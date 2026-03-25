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
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put

private const val TAG = "DashboardVM"

// --- Modelos de UI ---

data class AgentInfo(
    val id: String,
    val displayName: String,
    val emoji: String,
    val model: String,
    val fallbacks: List<String> = emptyList()
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

data class AgentCost(
    val agentId: String,
    val agentName: String,
    val tokens: Long,
    val costUsd: Double
)

data class CostSummary(
    val totalTokens: Long,
    val totalCostUsd: Double,
    val perAgent: List<AgentCost>
)

data class DashboardUiState(
    val isLoading: Boolean = true,
    val health: HealthInfo? = null,
    val agents: List<AgentInfo> = emptyList(),
    val cronCount: Int = 0,
    val costSummary: CostSummary? = null,
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
                val costsDeferred = launch { fetchCosts() }

                healthDeferred.join()
                agentsDeferred.join()
                cronsDeferred.join()
                costsDeferred.join()

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

    /**
     * Obtiene las sesiones del gateway y agrega tokens/costos por agente.
     */
    private suspend fun fetchCosts() {
        try {
            val response = rpcClient.request(
                "sessions.list",
                buildJsonObject {
                    put("limit", 100)
                    put("includeDerivedTitles", false)
                    put("includeLastMessage", false)
                }
            )
            if (!response.ok) {
                Log.w(TAG, "sessions.list failed: ${response.error?.message}")
                return
            }

            val payload = response.payload?.jsonObject ?: return
            val sessions = payload["sessions"]?.jsonArray ?: return

            // Mapa agentId → (tokens acumulados, costo acumulado)
            val tokensMap = mutableMapOf<String, Long>()
            val costMap = mutableMapOf<String, Double>()

            for (element in sessions) {
                val session = element.jsonObject
                val key = session["key"]?.jsonPrimitive?.content ?: continue
                val tokens = session["totalTokens"]?.jsonPrimitive?.longOrNull
                    ?: session["totalTokens"]?.jsonPrimitive?.intOrNull?.toLong()
                    ?: 0L
                val cost = session["estimatedCostUsd"]?.jsonPrimitive?.doubleOrNull ?: 0.0

                // Extraer agentId: "agent:main:dashboard:uuid" → "main"
                val parts = key.split(":")
                val agentId = if (parts.size >= 2) parts[1] else key

                tokensMap[agentId] = (tokensMap[agentId] ?: 0L) + tokens
                costMap[agentId] = (costMap[agentId] ?: 0.0) + cost
            }

            // Construir lista de costos por agente, resolver nombre desde agentes cargados
            val agentsList = _uiState.value.agents
            val perAgent = tokensMap.keys.map { id ->
                val name = agentsList.firstOrNull { it.id == id }?.displayName
                    ?: id.replaceFirstChar { it.uppercase() }
                AgentCost(
                    agentId = id,
                    agentName = name,
                    tokens = tokensMap[id] ?: 0L,
                    costUsd = costMap[id] ?: 0.0
                )
            }.sortedByDescending { it.costUsd }

            val totalTokens = tokensMap.values.sum()
            val totalCost = costMap.values.sum()

            _uiState.value = _uiState.value.copy(
                costSummary = CostSummary(
                    totalTokens = totalTokens,
                    totalCostUsd = totalCost,
                    perAgent = perAgent
                )
            )
        } catch (e: Exception) {
            Log.e(TAG, "fetchCosts error: ${e.message}", e)
        }
    }
}
