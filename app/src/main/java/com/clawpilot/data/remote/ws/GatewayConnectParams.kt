package com.clawpilot.data.remote.ws

/**
 * Parámetros necesarios para el handshake de conexión al gateway.
 */
data class GatewayConnectParams(
    val url: String,
    val token: String,
    val deviceId: String,
    val publicKeyBase64: String,
    val platform: String = "android",
    val deviceFamily: String = "",
    val clientVersion: String = "0.1.0",
    val scopes: List<String> = listOf(
        "operator.admin",
        "operator.read",
        "operator.write",
        "operator.approvals"
    )
) {
    /**
     * Construye el payload V3 para firma ECDSA.
     * Formato: v3|deviceId|clientId|clientMode|role|scopes|signedAtMs|token|nonce|platform|deviceFamily
     */
    fun buildSignaturePayload(nonce: String, signedAtMs: Long): String {
        val scopesStr = scopes.joinToString(",")
        val normalizedPlatform = platform.lowercase().take(64)
        val normalizedFamily = deviceFamily.lowercase().take(64)
        return listOf(
            "v3",
            deviceId,
            "openclaw-android",
            "ui",
            "operator",
            scopesStr,
            signedAtMs.toString(),
            token,
            nonce,
            normalizedPlatform,
            normalizedFamily
        ).joinToString("|")
    }
}
