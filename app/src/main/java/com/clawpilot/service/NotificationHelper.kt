package com.clawpilot.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.clawpilot.MainActivity
import com.clawpilot.R

/**
 * Helper para gestionar canales de notificación y mostrar notificaciones.
 * Canales:
 *   - "approvals": alta importancia, para solicitudes de aprobación de ejecución
 *   - "alerts": importancia por defecto, para desconexiones y fallos de cron
 *   - "service": baja importancia, para la notificación persistente del servicio
 */
object NotificationHelper {

    const val CHANNEL_APPROVALS = "approvals"
    const val CHANNEL_ALERTS = "alerts"
    const val CHANNEL_SERVICE = "service"

    // IDs de notificación base (approvals usan hash del approvalId)
    const val NOTIFICATION_ID_SERVICE = 1
    private const val NOTIFICATION_ID_ALERT_BASE = 1000

    private var alertCounter = 0

    /**
     * Crea los canales de notificación requeridos.
     * Debe llamarse en Application.onCreate().
     */
    fun createNotificationChannels(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return

        val approvalChannel = NotificationChannel(
            CHANNEL_APPROVALS,
            "Exec Approvals",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "Solicitudes de aprobación de ejecución de agentes"
        }

        val alertChannel = NotificationChannel(
            CHANNEL_ALERTS,
            "Alerts",
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = "Alertas de desconexión y fallos de cron"
        }

        val serviceChannel = NotificationChannel(
            CHANNEL_SERVICE,
            "ClawPilot Service",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Notificación persistente del servicio de conexión"
        }

        manager.createNotificationChannels(
            listOf(approvalChannel, alertChannel, serviceChannel)
        )
    }

    /**
     * Construye la notificación persistente para el foreground service.
     */
    fun buildServiceNotification(
        context: Context,
        statusText: String = "Connected"
    ): android.app.Notification {
        // Intent para abrir la app al tocar la notificación
        val tapIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pendingTap = PendingIntent.getActivity(
            context, 0, tapIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        return NotificationCompat.Builder(context, CHANNEL_SERVICE)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("ClawPilot")
            .setContentText(statusText)
            .setOngoing(true)
            .setContentIntent(pendingTap)
            .build()
    }

    /**
     * Muestra una notificación de aprobación con botones Approve/Reject.
     * @param approvalId ID de la solicitud (se usa como requestId en el RPC)
     */
    fun showApprovalNotification(
        context: Context,
        title: String,
        message: String,
        approvalId: String
    ) {
        // Intent para abrir la app al tocar la notificación
        val tapIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pendingTap = PendingIntent.getActivity(
            context, approvalId.hashCode(), tapIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        // Botón Approve
        val approveIntent = Intent(context, ApprovalReceiver::class.java).apply {
            action = ApprovalReceiver.ACTION_APPROVE
            putExtra(ApprovalReceiver.EXTRA_APPROVAL_ID, approvalId)
        }
        val pendingApprove = PendingIntent.getBroadcast(
            context,
            "approve_$approvalId".hashCode(),
            approveIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        // Botón Reject
        val rejectIntent = Intent(context, ApprovalReceiver::class.java).apply {
            action = ApprovalReceiver.ACTION_REJECT
            putExtra(ApprovalReceiver.EXTRA_APPROVAL_ID, approvalId)
        }
        val pendingReject = PendingIntent.getBroadcast(
            context,
            "reject_$approvalId".hashCode(),
            rejectIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_APPROVALS)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(title)
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pendingTap)
            .addAction(0, "Approve", pendingApprove)
            .addAction(0, "Reject", pendingReject)
            .build()

        try {
            NotificationManagerCompat.from(context)
                .notify(approvalId.hashCode(), notification)
        } catch (_: SecurityException) {
            // Permiso POST_NOTIFICATIONS no concedido; se ignora silenciosamente
        }
    }

    /**
     * Muestra una notificación de alerta genérica (desconexiones, fallos de cron, etc.).
     */
    fun showAlertNotification(context: Context, title: String, message: String) {
        val tapIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pendingTap = PendingIntent.getActivity(
            context, 0, tapIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val notificationId = NOTIFICATION_ID_ALERT_BASE + (alertCounter++ % 50)

        val notification = NotificationCompat.Builder(context, CHANNEL_ALERTS)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(title)
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .setContentIntent(pendingTap)
            .build()

        try {
            NotificationManagerCompat.from(context).notify(notificationId, notification)
        } catch (_: SecurityException) {
            // Permiso POST_NOTIFICATIONS no concedido
        }
    }
}
