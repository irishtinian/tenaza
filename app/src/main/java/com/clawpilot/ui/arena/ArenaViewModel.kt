package com.clawpilot.ui.arena

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.clawpilot.data.remote.GatewayRpcClient
import com.clawpilot.data.remote.loadAgentsFromGateway
import com.clawpilot.data.remote.ws.GatewayFrame
import com.clawpilot.data.repository.ConnectionRepository
import com.clawpilot.domain.model.ArenaMessage
import com.clawpilot.domain.model.ArenaParticipant
import com.clawpilot.ui.dashboard.AgentInfo
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import java.util.UUID

private const val TAG = "ArenaVM"
private const val MAX_PARTICIPANTS = 6
private const val MIN_PARTICIPANTS = 2

/**
 * ViewModel del Arena: chat grupal multi-agente.
 *
 * Cada agente participante tiene su propia sesión. Cuando el usuario
 * envía un mensaje, se reenvía a todas las sesiones en paralelo.
 * Los eventos de streaming se recogen y muestran en una línea de
 * tiempo compartida.
 */
class ArenaViewModel(
    private val rpcClient: GatewayRpcClient,
    private val connectionRepository: ConnectionRepository
) : ViewModel() {

    private val _participants = MutableStateFlow<List<ArenaParticipant>>(emptyList())
    val participants: StateFlow<List<ArenaParticipant>> = _participants.asStateFlow()

    private val _messages = MutableStateFlow<List<ArenaMessage>>(emptyList())
    val messages: StateFlow<List<ArenaMessage>> = _messages.asStateFlow()

    private val _isGenerating = MutableStateFlow(false)
    val isGenerating: StateFlow<Boolean> = _isGenerating.asStateFlow()

    private val _availableAgents = MutableStateFlow<List<AgentInfo>>(emptyList())
    val availableAgents: StateFlow<List<AgentInfo>> = _availableAgents.asStateFlow()

    private val _isSettingUp = MutableStateFlow(true)
    val isSettingUp: StateFlow<Boolean> = _isSettingUp.asStateFlow()

    // Mapa de sessionKey -> messageId del placeholder de streaming activo
    private val activeStreamingMessages = mutableMapOf<String, String>()

    init {
        loadAgents()
        collectChatEvents()
    }

    /** Carga la lista de agentes disponibles del gateway */
    private fun loadAgents() {
        viewModelScope.launch {
            _availableAgents.value = loadAgentsFromGateway(rpcClient)
        }
    }

    /**
     * Colecta eventos "chat" del gateway para actualizar los mensajes de streaming.
     * El texto en "delta" es ACUMULADO, no incremental.
     */
    private fun collectChatEvents() {
        viewModelScope.launch {
            connectionRepository.frames.collect { frame ->
                if (frame !is GatewayFrame.Event || frame.event != "chat") return@collect
                val payload = frame.payload?.jsonObject ?: return@collect
                val sessionKey = payload["sessionKey"]?.jsonPrimitive?.content ?: return@collect
                val state = payload["state"]?.jsonPrimitive?.content ?: return@collect

                // Solo procesar eventos de sesiones que pertenezcan a nuestros participantes
                val participant = _participants.value.find { it.sessionKey == sessionKey }
                    ?: return@collect

                val messageId = activeStreamingMessages[sessionKey] ?: return@collect

                when (state) {
                    "delta" -> {
                        val text = extractMessageText(payload)
                        if (text != null) {
                            updateMessage(messageId, text, isStreaming = true)
                        }
                    }
                    "final" -> {
                        val text = extractMessageText(payload)
                        if (text != null) {
                            updateMessage(messageId, text, isStreaming = false)
                        } else {
                            finalizeMessage(messageId)
                        }
                        activeStreamingMessages.remove(sessionKey)
                        checkIfStillGenerating()
                    }
                    "error" -> {
                        val errorMsg = payload["errorMessage"]?.jsonPrimitive?.content ?: "Error del agente"
                        updateMessage(messageId, "Error: $errorMsg", isStreaming = false)
                        activeStreamingMessages.remove(sessionKey)
                        checkIfStillGenerating()
                    }
                    "aborted" -> {
                        finalizeMessage(messageId)
                        activeStreamingMessages.remove(sessionKey)
                        checkIfStillGenerating()
                    }
                }
            }
        }
    }

    /** Extrae texto de payload.message.content[0].text */
    private fun extractMessageText(payload: kotlinx.serialization.json.JsonObject): String? {
        return try {
            payload["message"]?.jsonObject
                ?.get("content")?.jsonArray
                ?.firstOrNull()?.jsonObject
                ?.get("text")?.jsonPrimitive?.content
        } catch (_: Exception) {
            null
        }
    }

    /** Agrega un agente a la lista de participantes */
    fun addAgent(agent: AgentInfo) {
        if (_participants.value.size >= MAX_PARTICIPANTS) return
        if (_participants.value.any { it.agentId == agent.id }) return

        _participants.value = _participants.value + ArenaParticipant(
            agentId = agent.id,
            agentName = agent.displayName,
            emoji = agent.emoji.ifBlank { "\uD83E\uDD16" } // Robot por defecto
        )
    }

    /** Quita un agente de la lista de participantes */
    fun removeAgent(agentId: String) {
        _participants.value = _participants.value.filter { it.agentId != agentId }
    }

    /** Crea sesiones para todos los participantes y cambia al modo chat */
    fun startArena() {
        if (_participants.value.size < MIN_PARTICIPANTS) return

        viewModelScope.launch {
            try {
                // Crear sesiones en paralelo para todos los participantes
                val results = _participants.value.map { participant ->
                    async {
                        try {
                            val response = rpcClient.request("sessions.create", buildJsonObject {
                                put("agentId", participant.agentId)
                            })
                            if (response.ok) {
                                val sessionKey = response.payload?.jsonObject
                                    ?.get("key")?.jsonPrimitive?.content
                                Log.d(TAG, "Sesión creada para ${participant.agentName}: $sessionKey")
                                participant.copy(sessionKey = sessionKey)
                            } else {
                                Log.w(TAG, "Error creando sesión para ${participant.agentName}: ${response.error?.message}")
                                participant
                            }
                        } catch (e: Exception) {
                            Log.w(TAG, "Error creando sesión para ${participant.agentName}: ${e.message}")
                            participant
                        }
                    }
                }.awaitAll()

                _participants.value = results
                _isSettingUp.value = false
                Log.d(TAG, "Arena iniciada con ${results.count { it.sessionKey != null }} sesiones activas")
            } catch (e: Exception) {
                Log.e(TAG, "Error iniciando arena: ${e.message}")
            }
        }
    }

    /**
     * Parsea @menciones al inicio del mensaje.
     * @todos o sin menciones = enviar a todos.
     * @nombre = enviar solo a ese agente.
     * Múltiples @menciones = enviar a los mencionados.
     */
    private fun parseMentions(text: String): Pair<List<ArenaParticipant>, List<String>> {
        val mentionRegex = Regex("""@([\w-]+)""")
        val mentions = mentionRegex.findAll(text).map { it.groupValues[1].lowercase() }.toList()

        val allParticipants = _participants.value.filter { it.sessionKey != null }

        // Sin menciones o @todos = todos los participantes
        if (mentions.isEmpty() || mentions.any { it == "todos" || it == "all" }) {
            return allParticipants to emptyList()
        }

        // Filtrar por nombre del agente (coincidencia parcial insensible a mayúsculas)
        val targeted = allParticipants.filter { participant ->
            mentions.any { mention ->
                participant.agentName.lowercase().contains(mention) ||
                    participant.agentId.lowercase().contains(mention)
            }
        }

        val targetNames = targeted.map { it.agentName }

        // Si no matcheó nadie, enviar a todos como fallback
        return if (targeted.isEmpty()) {
            allParticipants to emptyList()
        } else {
            targeted to targetNames
        }
    }

    /**
     * Envía un mensaje a las sesiones de agentes correspondientes.
     * Soporta @menciones: @nombre envía solo a ese agente, sin mención o @todos envía a todos.
     */
    fun sendMessage(text: String) {
        if (text.isBlank()) return
        val trimmed = text.trim()

        // Parsear menciones para determinar destinatarios
        val (targets, targetNames) = parseMentions(trimmed)

        // Agregar mensaje del usuario a la línea de tiempo
        _messages.value = _messages.value + ArenaMessage(
            id = UUID.randomUUID().toString(),
            agentId = null,
            agentName = "Tú",
            agentEmoji = "",
            role = "user",
            text = trimmed,
            targetAgents = targetNames
        )

        if (targets.isEmpty()) return

        _isGenerating.value = true

        // Para cada participante target, crear placeholder y enviar
        targets.forEach { participant ->
            val messageId = UUID.randomUUID().toString()
            activeStreamingMessages[participant.sessionKey!!] = messageId

            // Agregar placeholder de streaming
            _messages.value = _messages.value + ArenaMessage(
                id = messageId,
                agentId = participant.agentId,
                agentName = participant.agentName,
                agentEmoji = participant.emoji,
                role = "assistant",
                text = "",
                isStreaming = true
            )

            // Enviar mensaje al agente (incluye @menciones para contexto)
            viewModelScope.launch {
                try {
                    val idempotencyKey = UUID.randomUUID().toString()
                    val response = rpcClient.request(
                        method = "chat.send",
                        params = buildJsonObject {
                            put("sessionKey", participant.sessionKey)
                            put("message", trimmed)
                            put("idempotencyKey", idempotencyKey)
                        },
                        timeoutMs = 15_000L
                    )

                    if (!response.ok) {
                        val errorMsg = response.error?.message ?: "Error enviando mensaje"
                        updateMessage(messageId, "Error: $errorMsg", isStreaming = false)
                        activeStreamingMessages.remove(participant.sessionKey)
                        checkIfStillGenerating()
                    } else {
                        Log.d(TAG, "chat.send ok para ${participant.agentName}")
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Error enviando a ${participant.agentName}: ${e.message}")
                    updateMessage(messageId, "Error: ${e.message}", isStreaming = false)
                    activeStreamingMessages.remove(participant.sessionKey)
                    checkIfStillGenerating()
                }
            }
        }
    }

    /** Aborta todas las sesiones activas */
    fun abort() {
        if (!_isGenerating.value) return

        _participants.value.filter { it.sessionKey != null }.forEach { participant ->
            viewModelScope.launch {
                try {
                    rpcClient.request("chat.abort", buildJsonObject {
                        put("sessionKey", participant.sessionKey!!)
                    })
                } catch (e: Exception) {
                    Log.w(TAG, "Error abortando ${participant.agentName}: ${e.message}")
                }
            }
        }
        // Los eventos "aborted" finalizarán los mensajes
    }

    /** Volver al modo de selección */
    fun backToSetup() {
        abort()
        _isSettingUp.value = true
        _messages.value = emptyList()
        _isGenerating.value = false
        activeStreamingMessages.clear()
        // Limpiar sessionKeys
        _participants.value = _participants.value.map { it.copy(sessionKey = null) }
    }

    // --- Helpers ---

    private fun updateMessage(messageId: String, text: String, isStreaming: Boolean) {
        val current = _messages.value.toMutableList()
        val index = current.indexOfFirst { it.id == messageId }
        if (index >= 0) {
            current[index] = current[index].copy(text = text, isStreaming = isStreaming)
            _messages.value = current
        }
    }

    private fun finalizeMessage(messageId: String) {
        val current = _messages.value.toMutableList()
        val index = current.indexOfFirst { it.id == messageId }
        if (index >= 0) {
            current[index] = current[index].copy(isStreaming = false)
            _messages.value = current
        }
    }

    private fun checkIfStillGenerating() {
        _isGenerating.value = activeStreamingMessages.isNotEmpty()
    }
}
