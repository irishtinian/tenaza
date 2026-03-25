package com.clawpilot.service

import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log
import com.clawpilot.data.local.prefs.AppPreferences
import com.clawpilot.data.remote.ws.ConnectionState
import com.clawpilot.data.repository.ConnectionRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import org.koin.android.ext.android.inject

private const val TAG = "ClawPilotService"

/**
 * Foreground service que mantiene vivo el proceso para la conexión WebSocket.
 * No gestiona el WebSocket directamente (eso lo hace ConnectionRepository singleton),
 * solo evita que Android mate el proceso y muestra la notificación persistente.
 */
class ClawPilotService : Service() {

    private val connectionRepository: ConnectionRepository by inject()
    private val appPreferences: AppPreferences by inject()
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private var eventListener: GatewayEventListener? = null

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "Servicio creado")

        // Iniciar como foreground inmediatamente para evitar crash en Android 12+
        val notification = NotificationHelper.buildServiceNotification(this, "Starting...")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(
                NotificationHelper.NOTIFICATION_ID_SERVICE,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            )
        } else {
            startForeground(NotificationHelper.NOTIFICATION_ID_SERVICE, notification)
        }

        // Iniciar listener de eventos del gateway
        eventListener = GatewayEventListener(
            context = this,
            connectionRepository = connectionRepository,
            appPreferences = appPreferences,
            scope = serviceScope
        ).also { it.start() }

        // Observar cambios de conexión para actualizar la notificación
        serviceScope.launch {
            connectionRepository.connectionState.collect { state ->
                val statusText = when (state) {
                    is ConnectionState.Connected -> "Connected"
                    is ConnectionState.Connecting -> "Connecting..."
                    is ConnectionState.Reconnecting -> "Reconnecting (attempt ${state.attempt})..."
                    is ConnectionState.Disconnected -> "Disconnected"
                    is ConnectionState.Error -> "Error: ${state.reason}"
                }
                val updated = NotificationHelper.buildServiceNotification(this@ClawPilotService, statusText)
                val manager = getSystemService(android.app.NotificationManager::class.java)
                manager?.notify(NotificationHelper.NOTIFICATION_ID_SERVICE, updated)
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "onStartCommand")
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "Servicio destruido")
        eventListener?.stop()
        eventListener = null
        serviceScope.cancel()
    }
}
