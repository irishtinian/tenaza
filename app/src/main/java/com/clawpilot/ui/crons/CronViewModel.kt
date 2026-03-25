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
                        CronJob(
                            id = obj["id"]?.jsonPrimitive?.content ?: return@mapNotNull null,
                            agentId = obj["agentId"]?.jsonPrimitive?.content
                                ?: obj["agent_id"]?.jsonPrimitive?.content
                                ?: obj["agent"]?.jsonPrimitive?.content
                                ?: "",
                            label = obj["label"]?.jsonPrimitive?.content
                                ?: obj["name"]?.jsonPrimitive?.content
                                ?: "",
                            schedule = obj["schedule"]?.jsonPrimitive?.content
                                ?: obj["cron"]?.jsonPrimitive?.content
                                ?: obj["interval"]?.jsonPrimitive?.content
                                ?: "?",
                            enabled = obj["enabled"]?.jsonPrimitive?.booleanOrNull ?: true,
                            lastRunAt = obj["lastRunAt"]?.jsonPrimitive?.longOrNull
                                ?: obj["last_run_at"]?.jsonPrimitive?.longOrNull,
                            lastStatus = obj["lastStatus"]?.jsonPrimitive?.content
                                ?: obj["last_status"]?.jsonPrimitive?.content,
                            nextRunAt = obj["nextRunAt"]?.jsonPrimitive?.longOrNull
                                ?: obj["next_run_at"]?.jsonPrimitive?.longOrNull
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
