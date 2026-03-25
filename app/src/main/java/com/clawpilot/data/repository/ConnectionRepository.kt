package com.clawpilot.data.repository

import com.clawpilot.data.local.prefs.CredentialStore
import com.clawpilot.data.remote.ws.ConnectionState
import com.clawpilot.data.remote.ws.GatewayFrame
import com.clawpilot.data.remote.ws.RequestFrame
import com.clawpilot.data.remote.ws.WebSocketManager
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first

/**
 * Orquesta el ciclo de vida de la conexión al gateway.
 *
 * Responsabilidades:
 * - Carga credenciales del CredentialStore y llama a WebSocketManager.connect()
 * - Expone el estado y los frames del WebSocketManager para que la UI los observe
 * - Permite iniciar una conexión temporal durante el flujo de emparejamiento QR
 */
class ConnectionRepository(
    private val webSocketManager: WebSocketManager,
    private val credentialStore: CredentialStore
) {

    // --- Observables públicos (delegan al WebSocketManager) ---

    val connectionState: StateFlow<ConnectionState> = webSocketManager.connectionState

    val frames: SharedFlow<GatewayFrame> = webSocketManager.frames

    val isPaired: Flow<Boolean> = credentialStore.isPaired()

    // --- Acciones de conexión ---

    /**
     * Lee las credenciales almacenadas y conecta al gateway.
     * No hace nada si el dispositivo no está emparejado.
     */
    suspend fun connectWithStoredCredentials() {
        val credentials = credentialStore.getCredentials().first() ?: return
        webSocketManager.connect(credentials.gatewayUrl, credentials.token)
    }

    /**
     * Conecta usando una URL y token de emparejamiento temporal (flujo QR).
     * El token definitivo se almacenará tras completar el emparejamiento.
     */
    suspend fun connectForPairing(url: String, pairingToken: String) {
        webSocketManager.connect(url, pairingToken)
    }

    /** Desconecta por iniciativa del usuario. No dispara reconexión. */
    fun disconnect() {
        webSocketManager.disconnect()
    }

    /** Envía un frame JSON-RPC al gateway. Devuelve false si la conexión no está activa. */
    fun send(frame: RequestFrame): Boolean {
        return webSocketManager.send(frame)
    }
}
