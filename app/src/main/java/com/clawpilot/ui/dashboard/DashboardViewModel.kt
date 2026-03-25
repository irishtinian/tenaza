package com.clawpilot.ui.dashboard

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.clawpilot.data.remote.GatewayRpcClient
import com.clawpilot.data.remote.loadAgentsFromGateway
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
            val agents = loadAgentsFromGateway(rpcClient)
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
