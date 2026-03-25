package com.tenaza.data.remote

import android.util.Log
import com.tenaza.data.remote.ws.GatewayFrame
import com.tenaza.data.remote.ws.RequestFrame
import com.tenaza.data.repository.ConnectionRepository
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import java.util.UUID

private const val TAG = "GatewayRpcClient"
private const val DEFAULT_TIMEOUT_MS = 10_000L

/**
 * Cliente RPC sobre el WebSocket del gateway.
 * Envía RequestFrame y espera la Response con el mismo ID.
 */
class GatewayRpcClient(
    private val connectionRepository: ConnectionRepository
) {

    /**
     * Envía una petición RPC y espera la respuesta correspondiente.
     * @param method Nombre del método RPC (ej: "agents.list", "health")
     * @param params Parámetros opcionales
     * @param timeoutMs Timeout en milisegundos
     * @return GatewayFrame.Response con ok/payload/error
     * @throws kotlinx.coroutines.TimeoutCancellationException si no llega respuesta
     */
    suspend fun request(
        method: String,
        params: JsonObject = buildJsonObject {},
        timeoutMs: Long = DEFAULT_TIMEOUT_MS
    ): GatewayFrame.Response {
        val requestId = UUID.randomUUID().toString()
        val frame = RequestFrame(
            id = requestId,
            method = method,
            params = params
        )

        Log.d(TAG, "RPC -> $method (id=$requestId)")

        val sent = connectionRepository.send(frame)
        if (!sent) {
            Log.w(TAG, "No se pudo enviar frame para $method")
            return GatewayFrame.Response(
                id = requestId,
                ok = false,
                error = com.tenaza.data.remote.ws.GatewayError(
                    message = "WebSocket not connected",
                    code = "SEND_FAILED"
                )
            )
        }

        // Esperar la respuesta con el mismo ID
        return withTimeout(timeoutMs) {
            connectionRepository.frames
                .filter { it is GatewayFrame.Response && it.id == requestId }
                .first() as GatewayFrame.Response
        }.also {
            Log.d(TAG, "RPC <- $method ok=${it.ok}")
        }
    }
}
