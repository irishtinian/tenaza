package com.clawpilot.data.remote.ws

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.util.UUID

/**
 * Frame de salida (app -> gateway): petición JSON-RPC.
 */
@Serializable
data class RequestFrame(
    val type: String = "request",
    val id: String = UUID.randomUUID().toString(),
    val method: String,
    val params: JsonObject = buildJsonObject {}
)

/**
 * Jerarquía de frames entrantes (gateway -> app).
 * Coincide con el protocolo JSON-RPC del gateway OpenClaw.
 */
@Serializable
sealed class GatewayFrame {

    /** Respuesta a una RequestFrame previa — lleva el id correlacionado. */
    @Serializable
    data class Response(
        val id: String,
        val result: JsonElement? = null,
        val error: GatewayError? = null
    ) : GatewayFrame()

    /** Evento push del gateway (sin id de correlación). */
    @Serializable
    data class Event(
        val event: String,
        val data: JsonElement? = null
    ) : GatewayFrame()
}

/**
 * Error estructurado devuelto en el campo `error` de una Response.
 */
@Serializable
data class GatewayError(
    val message: String,
    val code: String? = null
)

/**
 * Parsea un mensaje de texto WebSocket en el tipo de frame correspondiente.
 * Devuelve null si el mensaje no se puede parsear o tiene un tipo desconocido.
 */
fun parseGatewayFrame(text: String): GatewayFrame? {
    return try {
        val json = Json { ignoreUnknownKeys = true }.parseToJsonElement(text).jsonObject
        when (json["type"]?.jsonPrimitive?.content) {
            "response" -> Json.decodeFromJsonElement<GatewayFrame.Response>(json)
            "event" -> Json.decodeFromJsonElement<GatewayFrame.Event>(json)
            else -> null
        }
    } catch (e: Exception) {
        null
    }
}
