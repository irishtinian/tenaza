package com.clawpilot.data.repository

import android.os.Build
import com.clawpilot.data.local.crypto.KeyStoreManager
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
import java.util.UUID

/**
 * Orquesta el ciclo de vida de la conexión al gateway.
 * Construye GatewayConnectParams con credenciales + device identity.
 */
class ConnectionRepository(
    private val webSocketManager: WebSocketManager,
    private val credentialStore: CredentialStore,
    private val keyStoreManager: KeyStoreManager
) {

    val connectionState: StateFlow<ConnectionState> = webSocketManager.connectionState
    val frames: SharedFlow<GatewayFrame> = webSocketManager.frames
    val isPaired: Flow<Boolean> = credentialStore.isPaired()

    /**
     * Lee las credenciales almacenadas y conecta al gateway con device auth.
     */
    suspend fun connectWithStoredCredentials() {
        val credentials = credentialStore.getCredentials().first() ?: return
        val params = buildConnectParams(credentials.gatewayUrl, credentials.token)
        webSocketManager.connect(params)
    }

    /**
     * Conecta con URL y token dados (flujo de emparejamiento).
     */
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
        // Asegurar que existe el par ECDSA
        if (!keyStoreManager.hasKeyPair()) {
            keyStoreManager.generateEcdsaKeyPair()
        }
        val publicKey = keyStoreManager.getPublicKeyBase64()
        val deviceId = generateDeviceId()
        val deviceFamily = "${Build.MANUFACTURER} ${Build.MODEL}"

        return GatewayConnectParams(
            url = url,
            token = token,
            deviceId = deviceId,
            publicKeyBase64 = publicKey,
            platform = "android",
            deviceFamily = deviceFamily
        )
    }

    private fun generateDeviceId(): String {
        return UUID.nameUUIDFromBytes(
            "${Build.MANUFACTURER}|${Build.MODEL}|${Build.FINGERPRINT}".toByteArray()
        ).toString()
    }
}
