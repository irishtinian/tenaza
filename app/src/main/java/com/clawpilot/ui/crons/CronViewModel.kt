package com.clawpilot.ui.crons

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

private const val TAG = "CronVM"

// --- Modelos de UI ---

data class CronJob(
    val id: String,
    val agentId: String,
    val label: String,
    val schedule: String,
    val enabled: Boolean,
    val lastRunAt: Long?,
    val lastStatus: String?,
    val nextRunAt: Long?
)

data class CronUiState(
    val isLoading: Boolean = true,
    val jobs: List<CronJob> = emptyList(),
    val error: String? = null
)

/**
 * ViewModel para la gestión de cron jobs del gateway.
 * Permite listar, activar/desactivar, ejecutar y eliminar crons.
 */
class CronViewModel(
    private val rpcClient: GatewayRpcClient
) : ViewModel() {

    private val _uiState = MutableStateFlow(CronUiState())
    val uiState: StateFlow<CronUiState> = _uiState.asStateFlow()

    init {
        refresh()
    }

    /** Recarga la lista de cron jobs desde el gateway. */
    fun refresh() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            try {
                val response = rpcClient.request("cron.list")
                if (!response.ok) {
                    val msg = response.error?.message ?: "Error desconocido en cron.list"
                    Log.w(TAG, "cron.list failed: $msg")
                    _uiState.value = _uiState.value.copy(isLoading = false, error = msg)
                    return@launch
                }

                val payload = response.payload?.jsonObject
                if (payload == null) {
                    Log.w(TAG, "cron.list: payload nulo o no es objeto")
                    _uiState.value = _uiState.value.copy(isLoading = false, jobs = emptyList())
                    return@launch
                }

                // Intentar parsear "jobs" array; si no existe, logear el payload completo
                val jobsArray = payload["jobs"]?.jsonArray
                if (jobsArray == null) {
                    Log.w(TAG, "cron.list: campo 'jobs' no encontrado. Payload: $payload")
                    _uiState.value = _uiState.value.copy(isLoading = false, jobs = emptyList())
                    return@launch
                }

                val jobs = jobsArray.mapNotNull { element ->
                    try {
                        val obj = element.jsonObject
                        // schedule es un JsonObject: {kind, expr/everyMs, tz}
                        val scheduleObj = obj["schedule"]?.jsonObject
                        val scheduleText = when (scheduleObj?.get("kind")?.jsonPrimitive?.content) {
                            "cron" -> scheduleObj["expr"]?.jsonPrimitive?.content ?: "cron"
                            "every" -> {
                                val ms = scheduleObj["everyMs"]?.jsonPrimitive?.longOrNull ?: 0
                                formatInterval(ms)
                            }
                            else -> scheduleObj?.toString()?.take(30) ?: "?"
                        }
                        // state está dentro de un sub-objeto "state"
                        val stateObj = obj["state"]?.jsonObject
                        CronJob(
                            id = obj["id"]?.jsonPrimitive?.content ?: return@mapNotNull null,
                            agentId = obj["agentId"]?.jsonPrimitive?.content ?: "",
                            label = obj["label"]?.jsonPrimitive?.content
                                ?: obj["name"]?.jsonPrimitive?.content
                                ?: "",
                            schedule = scheduleText,
                            enabled = obj["enabled"]?.jsonPrimitive?.booleanOrNull ?: true,
                            lastRunAt = stateObj?.get("lastRunAtMs")?.jsonPrimitive?.longOrNull,
                            lastStatus = stateObj?.get("lastStatus")?.jsonPrimitive?.content
                                ?: stateObj?.get("lastRunStatus")?.jsonPrimitive?.content,
                            nextRunAt = stateObj?.get("nextRunAtMs")?.jsonPrimitive?.longOrNull
                        )
                    } catch (e: Exception) {
                        Log.w(TAG, "Error parseando cron job: ${e.message}, element: $element")
                        null
                    }
                }

                _uiState.value = _uiState.value.copy(isLoading = false, jobs = jobs)
                Log.d(TAG, "Cargados ${jobs.size} cron jobs")
            } catch (e: Exception) {
                Log.e(TAG, "Error en refresh: ${e.message}", e)
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = e.message ?: "Error desconocido"
                )
            }
        }
    }

    /** Formatea milisegundos en intervalo legible */
    private fun formatInterval(ms: Long): String {
        val seconds = ms / 1000
        val minutes = seconds / 60
        val hours = minutes / 60
        return when {
            hours > 0 -> "cada ${hours}h"
            minutes > 0 -> "cada ${minutes}m"
            else -> "cada ${seconds}s"
        }
    }

    /** Activa o desactiva un cron job. */
    fun toggleEnabled(jobId: String, enabled: Boolean) {
        viewModelScope.launch {
            try {
                val response = rpcClient.request("cron.update", buildJsonObject {
                    put("id", jobId)
                    put("enabled", enabled)
                })

                if (response.ok) {
                    // Actualizar estado local inmediatamente
                    _uiState.value = _uiState.value.copy(
                        jobs = _uiState.value.jobs.map { job ->
                            if (job.id == jobId) job.copy(enabled = enabled) else job
                        }
                    )
                    Log.d(TAG, "Cron $jobId ${if (enabled) "activado" else "desactivado"}")
                } else {
                    Log.w(TAG, "cron.update falló: ${response.error?.message}")
                    _uiState.value = _uiState.value.copy(
                        error = "Error al actualizar: ${response.error?.message}"
                    )
                }
            } catch (e: Exception) {
                Log.e(TAG, "toggleEnabled error: ${e.message}", e)
                _uiState.value = _uiState.value.copy(error = e.message)
            }
        }
    }

    /** Ejecuta un cron job inmediatamente. */
    fun runNow(jobId: String) {
        viewModelScope.launch {
            try {
                val response = rpcClient.request("cron.run", buildJsonObject {
                    put("id", jobId)
                })

                if (response.ok) {
                    Log.d(TAG, "Cron $jobId ejecutado manualmente")
                    // Refrescar para ver el nuevo lastRunAt/lastStatus
                    refresh()
                } else {
                    Log.w(TAG, "cron.run falló: ${response.error?.message}")
                    _uiState.value = _uiState.value.copy(
                        error = "Error al ejecutar: ${response.error?.message}"
                    )
                }
            } catch (e: Exception) {
                Log.e(TAG, "runNow error: ${e.message}", e)
                _uiState.value = _uiState.value.copy(error = e.message)
            }
        }
    }

    /** Elimina un cron job. Llamar solo tras confirmación del usuario. */
    fun deleteJob(jobId: String) {
        viewModelScope.launch {
            try {
                val response = rpcClient.request("cron.remove", buildJsonObject {
                    put("id", jobId)
                })

                if (response.ok) {
                    // Quitar del estado local
                    _uiState.value = _uiState.value.copy(
                        jobs = _uiState.value.jobs.filter { it.id != jobId }
                    )
                    Log.d(TAG, "Cron $jobId eliminado")
                } else {
                    Log.w(TAG, "cron.remove falló: ${response.error?.message}")
                    _uiState.value = _uiState.value.copy(
                        error = "Error al eliminar: ${response.error?.message}"
                    )
                }
            } catch (e: Exception) {
                Log.e(TAG, "deleteJob error: ${e.message}", e)
                _uiState.value = _uiState.value.copy(error = e.message)
            }
        }
    }

    /** Limpia el mensaje de error actual. */
    fun clearError() {
        _uiState.value = _uiState.value.copy(error = null)
    }
}
