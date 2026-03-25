package com.clawpilot.ui.chat

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.clawpilot.data.remote.GatewayRpcClient
import com.clawpilot.data.remote.loadAgentsFromGateway
import com.clawpilot.data.remote.ws.GatewayFrame
import com.clawpilot.data.repository.ConnectionRepository
import com.clawpilot.domain.model.ChatMessage
import com.clawpilot.domain.model.ChatSession
import com.clawpilot.ui.dashboard.AgentInfo
import kotlinx.coroutines.Job
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

private const val TAG = "ChatVM"

/**
 * ViewModel de Chat.
 *
 * Protocolo real del gateway:
 * - sessions.create { agentId } → sessionKey
 * - chat.send { sessionKey, message, idempotencyKey } → runId (streaming via eventos)
 * - evento "chat" con state: delta (texto acumulado), final, error, aborted
 * - chat.abort { sessionKey }
 * - chat.history { sessionKey }
 */
class ChatViewModel(
    private val rpcClient: GatewayRpcClient,
    private val connectionRepository: ConnectionRepository
) : ViewModel() {

    private val _messages = MutableStateFlow<List<ChatMessage>>(emptyList())
    val messages: StateFlow<List<ChatMessage>> = _messages.asStateFlow()

    private val _currentAgent = MutableStateFlow<ChatSession?>(null)
    val currentAgent: StateFlow<ChatSession?> = _currentAgent.asStateFlow()

    private val _isGenerating = MutableStateFlow(false)
    val isGenerating: StateFlow<Boolean> = _isGenerating.asStateFlow()

    private val _agents = MutableStateFlow<List<AgentInfo>>(emptyList())
    val agents: StateFlow<List<AgentInfo>> = _agents.asStateFlow()

    private var currentRunId: String? = null
    private var streamJob: Job? = null

    init {
        loadAgents()
        collectChatEvents()
    }

    private fun loadAgents() {
        viewModelScope.launch {
            _agents.value = loadAgentsFromGateway(rpcClient)
        }
    }

    /**
     * Colecta eventos "chat" del gateway para streaming de respuestas.
     * Delta text es ACUMULADO (no incremental) — reemplazamos el texto completo.
     */
    private fun collectChatEvents() {
        viewModelScope.launch {
            connectionRepository.frames.collect { frame ->
                if (frame !is GatewayFrame.Event || frame.event != "chat") return@collect
                val payload = frame.payload?.jsonObject ?: return@collect
                val runId = payload["runId"]?.jsonPrimitive?.content
                val sessionKey = payload["sessionKey"]?.jsonPrimitive?.content
                val state = payload["state"]?.jsonPrimitive?.content ?: return@collect

                // Solo procesar eventos de nuestra sesión activa
                val session = _currentAgent.value
                if (session?.sessionKey == null || session.sessionKey != sessionKey) return@collect

                when (state) {
                    "delta" -> {
                        val text = extractMessageText(payload)
                        if (text != null) {
                            replaceAssistantMessage(text)
                        }
                    }
                    "final" -> {
                        val text = extractMessageText(payload)
                        if (text != null) {
                            replaceAssistantMessage(text)
                        }
                        finalizeAssistantMessage()
                    }
                    "error" -> {
                        val errorMsg = payload["errorMessage"]?.jsonPrimitive?.content ?: "Agent error"
                        handleStreamError(errorMsg)
                    }
                    "aborted" -> {
                        finalizeAssistantMessage()
                    }
                }
            }
        }
    }

    /**
     * Extrae el texto del mensaje de un evento chat.
     * Formato: payload.message.content[0].text
     */
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

    /** Selecciona un agente y crea una sesión nueva */
    fun selectAgent(agentId: String, agentName: String) {
        _messages.value = emptyList()
        _isGenerating.value = false
        currentRunId = null
        streamJob?.cancel()

        _currentAgent.value = ChatSession(agentId = agentId, agentName = agentName)
        Log.w(TAG, "Agente seleccionado: $agentName ($agentId)")

        // Crear sesión en el gateway
        viewModelScope.launch {
            try {
                val response = rpcClient.request("sessions.create", buildJsonObject {
                    put("agentId", agentId)
                })
                if (response.ok) {
                    val sessionKey = response.payload?.jsonObject?.get("key")?.jsonPrimitive?.content
                    if (sessionKey != null) {
                        _currentAgent.value = ChatSession(
                            agentId = agentId,
                            agentName = agentName,
                            sessionKey = sessionKey
                        )
                        Log.w(TAG, "Sesión creada: $sessionKey")
                        // Cargar historial de conversación previo
                        loadHistory(sessionKey)
                    }
                } else {
                    Log.w(TAG, "Error creando sesión: ${response.error?.message}")
                }
            } catch (e: Exception) {
                Log.w(TAG, "Error creando sesión: ${e.message}")
            }
        }
    }

    /** Envía un mensaje al agente actual */
    fun sendMessage(text: String) {
        val session = _currentAgent.value ?: return
        val sessionKey = session.sessionKey ?: return
        if (text.isBlank()) return

        // Agregar mensaje del usuario
        _messages.value = _messages.value + ChatMessage(
            id = UUID.randomUUID().toString(),
            role = "user",
            text = text.trim()
        )

        // Placeholder del asistente para streaming
        _messages.value = _messages.value + ChatMessage(
            id = UUID.randomUUID().toString(),
            role = "assistant",
            text = "",
            isStreaming = true
        )
        _isGenerating.value = true

        val idempotencyKey = UUID.randomUUID().toString()
        currentRunId = idempotencyKey

        streamJob = viewModelScope.launch {
            try {
                val response = rpcClient.request(
                    method = "chat.send",
                    params = buildJsonObject {
                        put("sessionKey", sessionKey)
                        put("message", text.trim())
                        put("idempotencyKey", idempotencyKey)
                    },
                    timeoutMs = 15_000L
                )

                if (!response.ok) {
                    handleStreamError(response.error?.message ?: "Error enviando mensaje")
                } else {
                    Log.w(TAG, "chat.send ok, runId=${response.payload?.jsonObject?.get("runId")?.jsonPrimitive?.content}")
                    // Los eventos streaming llegarán via collectChatEvents()
                }
            } catch (e: Exception) {
                Log.w(TAG, "Error en sendMessage: ${e.message}")
                handleStreamError(e.message ?: "Error de conexión")
            }
        }
    }

    /** Aborta la generación actual */
    fun abort() {
        val session = _currentAgent.value ?: return
        val sessionKey = session.sessionKey ?: return
        if (!_isGenerating.value) return

        viewModelScope.launch {
            try {
                rpcClient.request("chat.abort", buildJsonObject {
                    put("sessionKey", sessionKey)
                })
            } catch (e: Exception) {
                Log.w(TAG, "Error en abort: ${e.message}")
            }
        }
        // El evento "aborted" finalizará el mensaje via collectChatEvents
    }

    /** Volver al selector de agentes */
    fun clearAgent() {
        _currentAgent.value = null
        _messages.value = emptyList()
        _isGenerating.value = false
        currentRunId = null
        streamJob?.cancel()
    }

    /**
     * Carga el historial de mensajes previos de una sesión.
     * Parsea la respuesta de chat.history y los agrega a la lista de mensajes.
     */
    private suspend fun loadHistory(sessionKey: String) {
        try {
            val response = rpcClient.request(
                "chat.history",
                buildJsonObject {
                    put("sessionKey", sessionKey)
                    put("limit", 50)
                }
            )
            if (!response.ok) {
                Log.w(TAG, "chat.history failed: ${response.error?.message}")
                return
            }

            val payload = response.payload?.jsonObject ?: return
            val messagesArray = payload["messages"]?.jsonArray ?: return

            val historyMessages = messagesArray.mapNotNull { element ->
                val msg = element.jsonObject
                val role = msg["role"]?.jsonPrimitive?.content ?: return@mapNotNull null
                // Solo mostrar mensajes de usuario y asistente
                if (role != "user" && role != "assistant") return@mapNotNull null

                // Extraer texto del contenido: content[0].text
                val text = try {
                    msg["content"]?.jsonArray
                        ?.firstOrNull()?.jsonObject
                        ?.get("text")?.jsonPrimitive?.content
                } catch (_: Exception) {
                    null
                } ?: return@mapNotNull null

                if (text.isBlank()) return@mapNotNull null

                ChatMessage(
                    id = UUID.randomUUID().toString(),
                    role = role,
                    text = text,
                    isStreaming = false
                )
            }

            if (historyMessages.isNotEmpty()) {
                _messages.value = historyMessages
                Log.w(TAG, "Historial cargado: ${historyMessages.size} mensajes")
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error cargando historial: ${e.message}")
        }
    }

    // --- Helpers ---

    private fun replaceAssistantMessage(text: String) {
        val current = _messages.value.toMutableList()
        val lastIndex = current.indexOfLast { it.role == "assistant" && it.isStreaming }
        if (lastIndex >= 0) {
            current[lastIndex] = current[lastIndex].copy(text = text)
            _messages.value = current
        }
    }

    private fun finalizeAssistantMessage() {
        _isGenerating.value = false
        currentRunId = null
        val current = _messages.value.toMutableList()
        val lastIndex = current.indexOfLast { it.role == "assistant" && it.isStreaming }
        if (lastIndex >= 0) {
            current[lastIndex] = current[lastIndex].copy(isStreaming = false)
            _messages.value = current
        }
    }

    private fun handleStreamError(errorMessage: String) {
        _isGenerating.value = false
        currentRunId = null
        val current = _messages.value.toMutableList()
        val lastIndex = current.indexOfLast { it.role == "assistant" && it.isStreaming }
        if (lastIndex >= 0) {
            val existing = current[lastIndex].text
            val errorText = if (existing.isBlank()) "Error: $errorMessage"
            else "$existing\n\n[Error: $errorMessage]"
            current[lastIndex] = current[lastIndex].copy(text = errorText, isStreaming = false)
            _messages.value = current
        }
    }
}
