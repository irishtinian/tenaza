package com.clawpilot.data.repository

import android.os.Build
import com.clawpilot.data.local.crypto.Ed25519KeyManager
import com.clawpilot.data.local.prefs.CredentialStore
import com.clawpilot.data.remote.ws.ConnectionState
import com.clawpilot.data.remote.ws.GatewayConnectParams
import com.clawpilot.data.remote.ws.GatewayFrame
import com.clawpilot.data.remote.ws.RequestFrame
import com.clawpilot.data.remote.ws.WebSocketManager
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
