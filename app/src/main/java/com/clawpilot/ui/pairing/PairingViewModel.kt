package com.clawpilot.ui.pairing

import android.os.Build
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.clawpilot.data.local.crypto.KeyStoreManager
import com.clawpilot.data.local.prefs.CredentialStore
import com.clawpilot.data.remote.ws.ConnectionState
import com.clawpilot.data.remote.ws.GatewayFrame
import com.clawpilot.data.remote.ws.RequestFrame
import com.clawpilot.data.repository.ConnectionRepository
import com.clawpilot.domain.model.GatewayCredentials
import com.clawpilot.domain.model.PairingPayload
import com.clawpilot.domain.model.PairingState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import java.net.URL
import java.util.UUID

/**
 * ViewModel que gestiona la máquina de estados del emparejamiento.
 * Flujo: Unpaired -> Scanning -> Connecting -> WaitingForApproval -> Paired/Error
 */
class PairingViewModel(
    private val credentialStore: CredentialStore,
    private val keyStoreManager: KeyStoreManager,
    private val connectionRepository: ConnectionRepository
) : ViewModel() {

    private val _state = MutableStateFlow<PairingState>(PairingState.Unpaired)
    val state: StateFlow<PairingState> = _state.asStateFlow()

    private val _manualUrlError = MutableStateFlow<String?>(null)
    val manualUrlError: StateFlow<String?> = _manualUrlError.asStateFlow()

    fun onStartScanning() {
        _state.value = PairingState.Scanning
    }

    /**
     * Callback cuando el escáner QR detecta un payload válido.
     */
    fun onQrDetected(payload: PairingPayload) {
        if (payload.isExpired()) {
            _state.value = PairingState.Error("QR code expired. Generate a new one from the gateway.")
            return
        }
        _state.value = PairingState.Connecting(payload.u)
        viewModelScope.launch {
            initiatePairing(payload.toWebSocketUrl(), payload.t)
        }
    }

    /**
     * Valida la URL ingresada manualmente e intenta conexión directa.
     */
    fun onManualUrlSubmitted(url: String) {
        val trimmed = url.trim()
        if (!isValidUrl(trimmed)) {
            _manualUrlError.value = "Invalid URL format. Use http(s)://host:port or ws(s)://host:port"
            return
        }
        _manualUrlError.value = null
        _state.value = PairingState.Connecting(trimmed)
        val wsUrl = trimmed.replace("https://", "wss://").replace("http://", "ws://")
        viewModelScope.launch {
            directConnect(wsUrl)
        }
    }

    fun onRetry() {
        _state.value = PairingState.Unpaired
    }

    // --- Conexión directa (manual URL, sin QR pairing handshake) ---

    private suspend fun directConnect(wsUrl: String) {
        try {
            Log.w("PairingVM", "directConnect: attempting $wsUrl")
            // Conectar al gateway
            connectionRepository.connectForPairing(wsUrl, "")
            Log.w("PairingVM", "directConnect: connectForPairing called, waiting for state...")

            // Esperar Connected o Error (15s timeout)
            val connState = withTimeout(15_000L) {
                connectionRepository.connectionState
                    .filter { it is ConnectionState.Connected || it is ConnectionState.Error }
                    .first()
            }
            Log.w("PairingVM", "directConnect: got state=$connState")
            if (connState is ConnectionState.Error) {
                throw Exception("Connection failed: ${connState.reason}")
            }

            // Conexión exitosa — almacenar credenciales para auto-connect
            val deviceName = "${Build.MANUFACTURER} ${Build.MODEL}"
            val credentials = GatewayCredentials(
                gatewayUrl = wsUrl,
                token = "", // El gateway no requiere token en modo directo
                deviceName = deviceName,
                scopes = emptyList()
            )
            credentialStore.storeCredentials(credentials)
            _state.value = PairingState.Paired(credentials)
        } catch (e: kotlinx.coroutines.TimeoutCancellationException) {
            Log.w("PairingVM", "directConnect: TIMEOUT")
            connectionRepository.disconnect()
            _state.value = PairingState.Error("Connection timed out. Check the gateway URL and try again.")
        } catch (e: Exception) {
            Log.w("PairingVM", "directConnect: ERROR ${e.message}", e)
            connectionRepository.disconnect()
            _state.value = PairingState.Error(e.message ?: "Connection failed")
        }
    }

    // --- Pairing handshake (via QR) ---

    private suspend fun initiatePairing(wsUrl: String, pairingToken: String) {
        try {
            // 1. Asegurar par de claves ECDSA
            if (!keyStoreManager.hasKeyPair()) {
                keyStoreManager.generateEcdsaKeyPair()
            }
            val publicKey = keyStoreManager.getPublicKeyBase64()

            // 2. Conectar al gateway con token de emparejamiento
            connectionRepository.connectForPairing(wsUrl, pairingToken)
            _state.value = PairingState.WaitingForApproval(wsUrl)

            // 3. Esperar estado Connected o Error (con timeout de 15s)
            val connState = withTimeout(15_000L) {
                connectionRepository.connectionState
                    .filter { it is ConnectionState.Connected || it is ConnectionState.Error }
                    .first()
            }
            if (connState is ConnectionState.Error) {
                throw Exception("Connection failed: ${connState.reason}")
            }

            // 4. Enviar frame connect.challenge con clave pública
            val deviceName = "${Build.MANUFACTURER} ${Build.MODEL}"
            val challengeFrame = RequestFrame(
                method = "connect.challenge",
                params = buildJsonObject {
                    put("publicKey", publicKey)
                    put("deviceName", deviceName)
                    put("deviceId", generateDeviceId())
                }
            )
            connectionRepository.send(challengeFrame)

            // 5. Esperar evento device.pair.resolved (timeout 60s — usuario puede tardar en aprobar)
            val resolvedEvent = withTimeout(60_000L) {
                connectionRepository.frames
                    .filterIsInstance<GatewayFrame.Event>()
                    .filter { it.event == "device.pair.resolved" }
                    .first()
            }

            // 6. Extraer token permanente de la respuesta
            val data = resolvedEvent.data?.jsonObject
            val scopedToken = data?.get("token")?.jsonPrimitive?.content
                ?: throw Exception("No token in pair response")
            val scopes = data["scopes"]?.jsonArray?.map { it.jsonPrimitive.content }
                ?: emptyList()

            // 7. Almacenar credenciales cifradas
            val credentials = GatewayCredentials(
                gatewayUrl = wsUrl,
                token = scopedToken,
                deviceName = deviceName,
                scopes = scopes
            )
            credentialStore.storeCredentials(credentials)

            // 8. Reconectar con token permanente
            connectionRepository.disconnect()
            connectionRepository.connectWithStoredCredentials()

            _state.value = PairingState.Paired(credentials)
        } catch (e: kotlinx.coroutines.TimeoutCancellationException) {
            connectionRepository.disconnect()
            _state.value = PairingState.Error("Connection timed out. Check the gateway URL and try again.")
        } catch (e: Exception) {
            connectionRepository.disconnect()
            _state.value = PairingState.Error(e.message ?: "Pairing failed")
        }
    }

    private fun generateDeviceId(): String {
        return UUID.nameUUIDFromBytes(
            "${Build.MANUFACTURER}|${Build.MODEL}|${Build.FINGERPRINT}".toByteArray()
        ).toString()
    }

    private fun isValidUrl(url: String): Boolean {
        val validSchemes = listOf("http://", "https://", "ws://", "wss://")
        if (validSchemes.none { url.startsWith(it, ignoreCase = true) }) return false
        return try {
            val httpUrl = url.replace("wss://", "https://").replace("ws://", "http://")
            URL(httpUrl)
            true
        } catch (_: Exception) {
            false
        }
    }
}
