package com.clawpilot.ui.agent

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.clawpilot.data.remote.GatewayRpcClient
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put

private const val TAG = "AgentDetailVM"

// --- Modelos de UI ---

data class AgentConfig(
    val id: String,
    val name: String,
    val emoji: String,
    val model: String,
    val heartbeatEvery: String?,
    val workspace: String?
)

data class AgentFile(
    val path: String,
    val name: String,
    val size: Long?
)

data class AgentSession(
    val key: String,
    val title: String?,
    val status: String?,
    val updatedAt: Long?,
    val model: String?
)

/**
 * ViewModel para la pantalla de detalle de un agente.
 * Carga config, archivos del workspace y sesiones recientes.
 */
class AgentDetailViewModel(
    private val rpcClient: GatewayRpcClient
) : ViewModel() {

    private val _agentConfig = MutableStateFlow<AgentConfig?>(null)
    val agentConfig: StateFlow<AgentConfig?> = _agentConfig.asStateFlow()

    private val _files = MutableStateFlow<List<AgentFile>>(emptyList())
    val files: StateFlow<List<AgentFile>> = _files.asStateFlow()

    private val _sessions = MutableStateFlow<List<AgentSession>>(emptyList())
    val sessions: StateFlow<List<AgentSession>> = _sessions.asStateFlow()

    private val _fileContent = MutableStateFlow<Pair<String, String>?>(null)
    /** Par (nombre del archivo, contenido). Null si no se está viendo ninguno. */
    val fileContent: StateFlow<Pair<String, String>?> = _fileContent.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    /**
     * Carga toda la información del agente: config, archivos y sesiones.
     */
    fun loadAgent(agentId: String) {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                // Lanzar peticiones en paralelo
                val configJob = launch { fetchConfig(agentId) }
                val filesJob = launch { fetchFiles(agentId) }
                val sessionsJob = launch { fetchSessions(agentId) }

                configJob.join()
                filesJob.join()
                sessionsJob.join()
            } catch (e: Exception) {
                Log.e(TAG, "Error cargando agente $agentId: ${e.message}", e)
            } finally {
                _isLoading.value = false
            }
        }
    }

    /**
     * Carga el contenido de un archivo del workspace.
     */
    fun viewFile(agentId: String, path: String) {
        viewModelScope.launch {
            try {
                val response = rpcClient.request(
                    "agents.files.get",
                    buildJsonObject {
                        put("agentId", agentId)
                        put("name", path)
                    },
                    timeoutMs = 15_000
                )
                if (response.ok) {
                    val content = response.payload?.jsonObject
                        ?.get("file")?.jsonObject
                        ?.get("content")?.jsonPrimitive?.content ?: ""
                    val fileName = path.substringAfterLast("/")
                    _fileContent.value = Pair(fileName, content)
                } else {
                    Log.w(TAG, "Error al leer archivo $path: ${response.error?.message}")
                    _fileContent.value = Pair(
                        path.substringAfterLast("/"),
                        "Error: ${response.error?.message ?: "No se pudo leer el archivo"}"
                    )
                }
            } catch (e: Exception) {
                Log.e(TAG, "viewFile error: ${e.message}", e)
                _fileContent.value = Pair(
                    path.substringAfterLast("/"),
                    "Error: ${e.message}"
                )
            }
        }
    }

    /**
     * Cierra el visor de archivos.
     */
    fun clearFileView() {
        _fileContent.value = null
    }

    // --- Fetchers privados ---

    private suspend fun fetchConfig(agentId: String) {
        try {
            val response = rpcClient.request(
                "config.get",
                buildJsonObject { put("path", "agents.list") }
            )
            if (!response.ok) {
                Log.w(TAG, "config.get failed: ${response.error?.message}")
                return
            }

            val agentsArray = response.payload?.jsonObject
                ?.get("value")?.jsonArray ?: return

            // Buscar el agente por id
            val agentObj = agentsArray.firstOrNull { element ->
                element.jsonObject["id"]?.jsonPrimitive?.content == agentId
            }?.jsonObject ?: return

            val name = agentObj["name"]?.jsonPrimitive?.content
                ?: agentObj["displayName"]?.jsonPrimitive?.content
                ?: agentId
            val emoji = agentObj["emoji"]?.jsonPrimitive?.content ?: ""
            val model = agentObj["model"]?.jsonObject
                ?.get("primary")?.jsonPrimitive?.content ?: ""

            // Heartbeat config
            val heartbeatEvery = agentObj["heartbeat"]?.jsonObject
                ?.get("every")?.jsonPrimitive?.content

            // Workspace path
            val workspace = agentObj["workspace"]?.jsonPrimitive?.content

            _agentConfig.value = AgentConfig(
                id = agentId,
                name = name,
                emoji = emoji,
                model = model.substringAfterLast("/"),
                heartbeatEvery = heartbeatEvery,
                workspace = workspace
            )
        } catch (e: Exception) {
            Log.e(TAG, "fetchConfig error: ${e.message}", e)
        }
    }

    private suspend fun fetchFiles(agentId: String) {
        try {
            val response = rpcClient.request(
                "agents.files.list",
                buildJsonObject { put("agentId", agentId) }
            )
            if (!response.ok) {
                Log.w(TAG, "agents.files.list failed: ${response.error?.message}")
                return
            }

            val filesArray = response.payload?.jsonObject
                ?.get("files")?.jsonArray ?: return

            // Archivos destacados primero
            val highlightedNames = setOf("SOUL.md", "MEMORY.md", "HEARTBEAT.md")

            val fileList = filesArray.map { element ->
                val obj = element.jsonObject
                val name = obj["name"]?.jsonPrimitive?.content ?: ""
                AgentFile(
                    path = name, // Usar name como path para files.get
                    name = name,
                    size = obj["size"]?.jsonPrimitive?.longOrNull
                )
            }.sortedWith(
                compareByDescending<AgentFile> { it.name in highlightedNames }
                    .thenBy { it.name }
            )

            _files.value = fileList
        } catch (e: Exception) {
            Log.e(TAG, "fetchFiles error: ${e.message}", e)
        }
    }

    private suspend fun fetchSessions(agentId: String) {
        try {
            val response = rpcClient.request(
                "sessions.list",
                buildJsonObject {
                    put("agentId", agentId)
                    put("limit", 10)
                    put("includeDerivedTitles", true)
                }
            )
            if (!response.ok) {
                Log.w(TAG, "sessions.list failed: ${response.error?.message}")
                return
            }

            val sessionsArray = response.payload?.jsonObject
                ?.get("sessions")?.jsonArray ?: return

            val sessionList = sessionsArray.map { element ->
                val obj = element.jsonObject
                AgentSession(
                    key = obj["key"]?.jsonPrimitive?.content
                        ?: obj["id"]?.jsonPrimitive?.content ?: "",
                    title = obj["title"]?.jsonPrimitive?.content
                        ?: obj["derivedTitle"]?.jsonPrimitive?.content,
                    status = obj["status"]?.jsonPrimitive?.content,
                    updatedAt = obj["updatedAt"]?.jsonPrimitive?.longOrNull,
                    model = obj["model"]?.jsonPrimitive?.content
                )
            }

            _sessions.value = sessionList
        } catch (e: Exception) {
            Log.e(TAG, "fetchSessions error: ${e.message}", e)
        }
    }
}
