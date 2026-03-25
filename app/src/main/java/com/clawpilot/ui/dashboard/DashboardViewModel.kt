package com.clawpilot.ui.dashboard

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
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put

private const val TAG = "DashboardVM"

// --- Modelos de UI ---

data class AgentInfo(
    val id: String,
    val displayName: String,
    val emoji: String,
    val model: String
)

data class ChannelInfo(
    val name: String,
    val configured: Boolean,
    val running: Boolean,
    val botUsername: String?
)

data class HealthInfo(
    val ok: Boolean,
    val uptimeMs: Long,
    val channels: List<ChannelInfo>
)

data class DashboardUiState(
    val isLoading: Boolean = true,
    val health: HealthInfo? = null,
    val agents: List<AgentInfo> = emptyList(),
    val cronCount: Int = 0,
    val error: String? = null
)

/**
 * ViewModel del Dashboard: carga salud, agentes y crons del gateway.
 */
class DashboardViewModel(
    private val rpcClient: GatewayRpcClient
) : ViewModel() {

    private val _uiState = MutableStateFlow(DashboardUiState())
    val uiState: StateFlow<DashboardUiState> = _uiState.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)

            try {
                // Lanzar las 3 peticiones en paralelo
                val healthDeferred = launch { fetchHealth() }
                val agentsDeferred = launch { fetchAgents() }
                val cronsDeferred = launch { fetchCrons() }

                healthDeferred.join()
                agentsDeferred.join()
                cronsDeferred.join()

                _uiState.value = _uiState.value.copy(isLoading = false)
            } catch (e: Exception) {
                Log.e(TAG, "Error en refresh: ${e.message}", e)
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = e.message ?: "Error desconocido"
                )
            }
        }
    }

    private suspend fun fetchHealth() {
        try {
            val response = rpcClient.request("health")
            if (!response.ok) {
                _uiState.value = _uiState.value.copy(
                    health = HealthInfo(ok = false, uptimeMs = 0, channels = emptyList())
                )
                return
            }

            val payload = response.payload?.jsonObject ?: return
            val ok = payload["ok"]?.jsonPrimitive?.booleanOrNull ?: false
            val uptimeMs = payload["uptimeMs"]?.jsonPrimitive?.longOrNull ?: 0L

            // Parsear canales
            val channels = mutableListOf<ChannelInfo>()
            val channelsObj = payload["channels"]?.jsonObject
            channelsObj?.forEach { (name, value) ->
                val ch = value.jsonObject
                val configured = ch["configured"]?.jsonPrimitive?.booleanOrNull ?: false
                val running = ch["running"]?.jsonPrimitive?.booleanOrNull ?: false
                val botUsername = ch["probe"]?.jsonObject
                    ?.get("bot")?.jsonObject
                    ?.get("username")?.jsonPrimitive?.content
                channels.add(ChannelInfo(name, configured, running, botUsername))
            }

            _uiState.value = _uiState.value.copy(
                health = HealthInfo(ok = ok, uptimeMs = uptimeMs, channels = channels)
            )
        } catch (e: Exception) {
            Log.e(TAG, "fetchHealth error: ${e.message}", e)
            _uiState.value = _uiState.value.copy(
                health = HealthInfo(ok = false, uptimeMs = 0, channels = emptyList())
            )
        }
    }

    private suspend fun fetchAgents() {
        try {
            // agents.list solo devuelve {id, name}. Usamos config.get para la info completa.
            val response = rpcClient.request("config.get", buildJsonObject {
                put("path", "agents.list")
            })
            if (!response.ok) {
                // Fallback a agents.list básico
                val fallback = rpcClient.request("agents.list")
                if (fallback.ok) {
                    val agentsArray = fallback.payload?.jsonObject?.get("agents")?.jsonArray ?: return
                    val agents = agentsArray.map { element ->
                        val obj = element.jsonObject
                        AgentInfo(
                            id = obj["id"]?.jsonPrimitive?.content ?: "",
                            displayName = obj["name"]?.jsonPrimitive?.content ?: obj["id"]?.jsonPrimitive?.content ?: "?",
                            emoji = "",
                            model = ""
                        )
                    }
                    _uiState.value = _uiState.value.copy(agents = agents)
                }
                return
            }

            val agentsArray = response.payload?.jsonObject?.get("value")?.jsonArray ?: return

            val agents = agentsArray.map { element ->
                val obj = element.jsonObject
                val name = obj["name"]?.jsonPrimitive?.content
                    ?: obj["displayName"]?.jsonPrimitive?.content
                    ?: obj["id"]?.jsonPrimitive?.content
                    ?: "?"
                val emoji = obj["emoji"]?.jsonPrimitive?.content ?: ""
                val model = obj["model"]?.jsonObject
                    ?.get("primary")?.jsonPrimitive?.content ?: ""
                AgentInfo(
                    id = obj["id"]?.jsonPrimitive?.content ?: "",
                    displayName = name,
                    emoji = emoji,
                    model = model.substringAfterLast("/"), // "anthropic/claude-sonnet-4-6" -> "claude-sonnet-4-6"
                )
            }

            _uiState.value = _uiState.value.copy(agents = agents)
        } catch (e: Exception) {
            Log.e(TAG, "fetchAgents error: ${e.message}", e)
        }
    }

    private suspend fun fetchCrons() {
        try {
            val response = rpcClient.request("cron.list")
            if (!response.ok) {
                Log.w(TAG, "cron.list failed: ${response.error?.message}")
                return
            }

            val payload = response.payload?.jsonObject ?: return
            val jobs = payload["jobs"]?.jsonArray ?: return

            _uiState.value = _uiState.value.copy(cronCount = jobs.size)
        } catch (e: Exception) {
            Log.e(TAG, "fetchCrons error: ${e.message}", e)
        }
    }
}
