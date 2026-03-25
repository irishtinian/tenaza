package com.tenaza.domain.model

import kotlinx.serialization.Serializable

/**
 * Payload del código QR generado por el gateway para el emparejamiento.
 * El gateway serializa este objeto a JSON y lo codifica como QR.
 * El app escanea el QR, parsea el JSON y extrae los datos de conexión.
 *
 * Ejemplo de JSON:
 * {"v":1,"u":"https://gw.example.com:18789","t":"abc123","e":1774453060}
 */
@Serializable
data class PairingPayload(
    val v: Int,      // versión del protocolo (se espera 1)
    val u: String,   // URL del gateway (ej. "https://gw.example.com:18789")
    val t: String,   // token de emparejamiento (corto, de un solo uso)
    val e: Long      // expiración como timestamp Unix (segundos)
) {
    /**
     * Comprueba si el QR ha expirado comparando con el tiempo actual.
     */
    fun isExpired(): Boolean = System.currentTimeMillis() / 1000 > e

    /**
     * Convierte la URL HTTP/HTTPS a WebSocket (WS/WSS) para la conexión.
     */
    fun toWebSocketUrl(): String {
        return u.replace("https://", "wss://").replace("http://", "ws://")
    }
}
