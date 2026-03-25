package com.tenaza.service

import android.content.Context
import android.util.Log
import com.tenaza.data.local.prefs.AppPreferences
import com.tenaza.data.remote.ws.ConnectionState
import com.tenaza.data.remote.ws.GatewayFrame
import com.tenaza.data.repository.ConnectionRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

private const val TAG = "GatewayEventListener"

/**
 * Escucha eventos del gateway y genera notificaciones según el tipo de evento.
 * Eventos monitorizados:
 *   - agent con exec-approval → notificación de aprobación
 *   - health con cambios de canal → alerta de desconexión
 *   - cron con fallos → alerta de fallo
 */
class GatewayEventListener(
    private val context: Context,
    private val connectionRepository: ConnectionRepository,
    private val appPreferences: AppPreferences,
    private val scope: CoroutineScope
) {

    private var eventJob: Job? = null
    private var connectionJob: Job? = null

    /**
     * Inicia la escucha de eventos y estado de conexión.
     */
    fun start() {
        startEventListener()
        startConnectionMonitor()
    }

    /**
     * Detiene la escucha de eventos.
     */
    fun stop() {
        eventJob?.cancel()
        eventJob = null
        connectionJob?.cancel()
        connectionJob = null
    }

    private fun startEventListener() {
        eventJob?.cancel()
        eventJob = scope.launch {
            connectionRepository.frames.collect { frame ->
                if (frame is GatewayFrame.Event) {
                    handleEvent(frame)
                }
            }
        }
    }

    private fun startConnectionMonitor() {
        connectionJob?.cancel()
        connectionJob = scope.launch {
            // Variable para rastrear si estuvimos conectados previamente
            var wasConnected = false
            connectionRepository.connectionState.collectLatest { state ->
                when (state) {
                    is ConnectionState.Connected -> {
                        wasConnected = true
                    }
                    is ConnectionState.Disconnected,
                    is ConnectionState.Error -> {
                        // Solo alertar si estuvimos conectados antes (no en el arranque)
                        if (wasConnected && areNotificationsEnabled()) {
                            val reason = if (state is ConnectionState.Error) state.reason
                                else "Disconnected"
                            NotificationHelper.showAlertNotification(
                                context,
                                title = "Gateway Disconnected",
                                message = reason
                            )
                        }
                    }
                    else -> { /* Connecting, Reconnecting: no notificar */ }
                }
            }
        }
    }

    private suspend fun handleEvent(event: GatewayFrame.Event) {
        if (!areNotificationsEnabled()) return

        try {
            when (event.event) {
                "agent" -> handleAgentEvent(event)
                "health" -> handleHealthEvent(event)
                "cron" -> handleCronEvent(event)
                else -> {
                    Log.d(TAG, "Evento ignorado: ${event.event}")
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error procesando evento ${event.event}", e)
        }
    }

    /**
     * Maneja eventos de agente. Busca fase exec-approval para solicitar aprobación.
     */
    private fun handleAgentEvent(event: GatewayFrame.Event) {
        val payload = event.payload?.jsonObject ?: return
        val stream = (payload["stream"] as? JsonPrimitive)?.content
        val data = payload["data"]?.jsonObject

        // Detectar solicitud de aprobación de ejecución
        if (stream == "lifecycle") {
            val phase = (data?.get("phase") as? JsonPrimitive)?.content
            if (phase == "exec-approval") {
                val requestId = (data?.get("requestId") as? JsonPrimitive)?.content
                    ?: return
                val agentId = (payload["agentId"] as? JsonPrimitive)?.content
                    ?: (data?.get("agentId") as? JsonPrimitive)?.content
                    ?: "unknown"
                val command = (data?.get("command") as? JsonPrimitive)?.content
                    ?: (data?.get("description") as? JsonPrimitive)?.content
                    ?: "Exec request"

                Log.d(TAG, "Exec approval solicitado: agent=$agentId, requestId=$requestId")

                NotificationHelper.showApprovalNotification(
                    context = context,
                    title = "Approval: $agentId",
                    message = command,
                    approvalId = requestId
                )
            }
        }
    }

    /**
     * Maneja eventos de salud. Alerta si un canal se desconecta.
     */
    private fun handleHealthEvent(event: GatewayFrame.Event) {
        val payload = event.payload?.jsonObject ?: return
        val status = (payload["status"] as? JsonPrimitive)?.content
        val channel = (payload["channel"] as? JsonPrimitive)?.content

        if (status == "disconnected" && channel != null) {
            NotificationHelper.showAlertNotification(
                context = context,
                title = "Channel Disconnected",
                message = "Channel '$channel' lost connection"
            )
        }
    }

    /**
     * Maneja eventos de cron. Alerta si un cron falla.
     */
    private fun handleCronEvent(event: GatewayFrame.Event) {
        val payload = event.payload?.jsonObject ?: return
        val status = (payload["status"] as? JsonPrimitive)?.content
        val cronId = (payload["cronId"] as? JsonPrimitive)?.content
            ?: (payload["id"] as? JsonPrimitive)?.content
            ?: "unknown"
        val error = (payload["error"] as? JsonPrimitive)?.content

        if (status == "failed" || status == "error") {
            NotificationHelper.showAlertNotification(
                context = context,
                title = "Cron Failed: $cronId",
                message = error ?: "Cron execution failed"
            )
        }
    }

    private suspend fun areNotificationsEnabled(): Boolean {
        return appPreferences.getNotificationsEnabled().first()
    }
}
