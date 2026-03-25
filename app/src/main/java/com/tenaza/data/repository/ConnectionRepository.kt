package com.tenaza.data.repository

import android.os.Build
import com.tenaza.data.local.crypto.Ed25519KeyManager
import com.tenaza.data.local.prefs.CredentialStore
import com.tenaza.data.remote.ws.ConnectionState
import com.tenaza.data.remote.ws.GatewayConnectParams
import com.tenaza.data.remote.ws.GatewayFrame
import com.tenaza.data.remote.ws.RequestFrame
import com.tenaza.data.remote.ws.WebSocketManager
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first

/**
 * Orquesta el ciclo de vida de la conexión al gateway.
 * Usa Ed25519 device identity para obtener scopes completas.
 */
class ConnectionRepository(
    private val webSocketManager: WebSocketManager,
    private val credentialStore: CredentialStore,
    private val ed25519KeyManager: Ed25519KeyManager
) {

    val connectionState: StateFlow<ConnectionState> = webSocketManager.connectionState
    val frames: SharedFlow<GatewayFrame> = webSocketManager.frames
    val isPaired: Flow<Boolean> = credentialStore.isPaired()

    /** Versión del gateway obtenida durante el handshake WebSocket */
    val gatewayVersion: StateFlow<String?> = webSocketManager.gatewayVersion

    suspend fun connectWithStoredCredentials() {
        val credentials = credentialStore.getCredentials().first() ?: return
        val params = buildConnectParams(credentials.gatewayUrl, credentials.token)
        webSocketManager.connect(params)
    }

    suspend fun connectForPairing(url: String, token: String) {
        val params = buildConnectParams(url, token)
        webSocketManager.connect(params)
    }

    fun disconnect() {
        webSocketManager.disconnect()
    }

    fun send(frame: RequestFrame): Boolean {
        return webSocketManager.send(frame)
    }

    private fun buildConnectParams(url: String, token: String): GatewayConnectParams {
        if (!ed25519KeyManager.hasKeyPair()) {
            ed25519KeyManager.generateKeyPair()
        }
        return GatewayConnectParams(
            url = url,
            token = token,
            deviceId = ed25519KeyManager.getDeviceId(),
            publicKeyBase64 = ed25519KeyManager.getPublicKeyBase64Url(),
            platform = "android",
            deviceFamily = "${Build.MANUFACTURER} ${Build.MODEL}",
            signFunc = { data -> ed25519KeyManager.sign(data) }
        )
    }
}
