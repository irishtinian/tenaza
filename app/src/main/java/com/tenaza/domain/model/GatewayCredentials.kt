package com.tenaza.domain.model

/**
 * Credenciales de conexión al gateway de OpenClaw.
 * Almacenadas cifradas via CredentialStore (AES-256-GCM + Tink).
 */
data class GatewayCredentials(
    val gatewayUrl: String,
    val token: String,
    val deviceName: String,
    val scopes: List<String> = emptyList(),
    val pairedAt: Long = System.currentTimeMillis()
)

/**
 * Máquina de estados del flujo de emparejamiento con el gateway.
 * La UI reacciona a este estado para mostrar la pantalla adecuada.
 */
sealed class PairingState {
    data object Unpaired : PairingState()
    data object Scanning : PairingState()
    data class Connecting(val gatewayUrl: String) : PairingState()
    data class WaitingForApproval(val gatewayUrl: String) : PairingState()
    data class Paired(val credentials: GatewayCredentials) : PairingState()
    data class Error(val message: String) : PairingState()
}
