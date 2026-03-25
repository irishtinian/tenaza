package com.tenaza.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.app.NotificationManagerCompat
import com.tenaza.data.remote.GatewayRpcClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

private const val TAG = "ApprovalReceiver"

/**
 * BroadcastReceiver que maneja los botones Approve/Reject de las notificaciones.
 * Envía el RPC `exec.approval.resolve` al gateway con la decisión.
 */
class ApprovalReceiver : BroadcastReceiver(), KoinComponent {

    companion object {
        const val ACTION_APPROVE = "com.tenaza.ACTION_APPROVE"
        const val ACTION_REJECT = "com.tenaza.ACTION_REJECT"
        const val EXTRA_APPROVAL_ID = "approval_id"
    }

    private val rpcClient: GatewayRpcClient by inject()

    override fun onReceive(context: Context, intent: Intent) {
        val approvalId = intent.getStringExtra(EXTRA_APPROVAL_ID) ?: run {
            Log.w(TAG, "Recibido intent sin approval_id")
            return
        }

        val decision = when (intent.action) {
            ACTION_APPROVE -> "approve"
            ACTION_REJECT -> "reject"
            else -> {
                Log.w(TAG, "Acción desconocida: ${intent.action}")
                return
            }
        }

        Log.d(TAG, "Resolviendo approval $approvalId -> $decision")

        // Cancelar la notificación inmediatamente
        try {
            NotificationManagerCompat.from(context).cancel(approvalId.hashCode())
        } catch (_: SecurityException) {
            // Ignorar si no hay permiso
        }

        // Enviar RPC en coroutine (goAsync para que el receiver no muera antes)
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val params = buildJsonObject {
                    put("requestId", approvalId)
                    put("decision", decision)
                }
                val response = rpcClient.request(
                    method = "exec.approval.resolve",
                    params = params,
                    timeoutMs = 5_000L
                )
                if (response.ok) {
                    Log.d(TAG, "Approval $approvalId resuelto: $decision")
                } else {
                    Log.w(TAG, "Error al resolver approval: ${response.error?.message}")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Excepción al resolver approval $approvalId", e)
            } finally {
                pendingResult.finish()
            }
        }
    }
}
