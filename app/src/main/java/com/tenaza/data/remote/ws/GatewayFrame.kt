package com.tenaza.data.remote.ws

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.util.UUID

/**
 * Frame de salida (app -> gateway): petición JSON-RPC.
 * El gateway usa type "req" (no "request").
 */
@Serializable
data class RequestFrame(
    val type: String = "req",
    val id: String = UUID.randomUUID().toString(),
    val method: String,
    val params: JsonObject = buildJsonObject {}
)

/**
 * Jerarquía de frames entrantes (gateway -> app).
 * El gateway usa:
 *   - type "res" con ok/payload/error para respuestas
 *   - type "event" con event/payload para eventos push
 */
@Serializable
sealed class GatewayFrame {

    /** Respuesta a una RequestFrame previa. */
    @Serializable
    data class Response(
        val id: String,
        val ok: Boolean = false,
        val payload: JsonElement? = null,
        val error: GatewayError? = null
    ) : GatewayFrame()

    /** Evento push del gateway. */
    @Serializable
    data class Event(
        val event: String,
        val payload: JsonElement? = null
    ) : GatewayFrame()
}

@Serializable
data class GatewayError(
    val message: String,
    val code: String? = null
)

/**
 * Parsea un mensaje de texto WebSocket en el tipo de frame correspondiente.
 */
fun parseGatewayFrame(text: String): GatewayFrame? {
    return try {
        val json = Json { ignoreUnknownKeys = true }
        val obj = json.parseToJsonElement(text).jsonObject
        when (obj["type"]?.jsonPrimitive?.content) {
            "res" -> json.decodeFromJsonElement(GatewayFrame.Response.serializer(), obj)
            "event" -> json.decodeFromJsonElement(GatewayFrame.Event.serializer(), obj)
            else -> null
        }
    } catch (_: Exception) {
        null
    }
}
