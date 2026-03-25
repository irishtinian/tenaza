package com.tenaza.ui.pairing

import android.os.Build
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tenaza.data.local.crypto.KeyStoreManager
import com.tenaza.data.local.prefs.CredentialStore
import com.tenaza.data.remote.ws.ConnectionState
import com.tenaza.data.repository.ConnectionRepository
import com.tenaza.domain.model.GatewayCredentials
import com.tenaza.domain.model.PairingPayload
import com.tenaza.domain.model.PairingState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import java.net.URL

/**
 * ViewModel que gestiona la máquina de estados del emparejamiento.
 * El handshake ECDSA real ocurre en WebSocketManager automáticamente.
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

    fun onQrDetected(payload: PairingPayload) {
        if (payload.isExpired()) {
            _state.value = PairingState.Error("QR code expired. Generate a new one from the gateway.")
            return
        }
        _state.value = PairingState.Connecting(payload.u)
        viewModelScope.launch {
            connectAndStore(payload.toWebSocketUrl(), payload.t)
        }
    }

    fun onManualUrlSubmitted(url: String, token: String = "") {
        val trimmed = url.trim()
        if (!isValidUrl(trimmed)) {
            _manualUrlError.value = "Invalid URL format. Use http(s)://host:port or ws(s)://host:port"
            return
        }
        _manualUrlError.value = null
        _state.value = PairingState.Connecting(trimmed)
        val wsUrl = trimmed.replace("https://", "wss://").replace("http://", "ws://")
        viewModelScope.launch {
            connectAndStore(wsUrl, token.trim())
        }
    }

    fun onRetry() {
        _state.value = PairingState.Unpaired
    }

    /**
     * Conecta al gateway y almacena credenciales si tiene éxito.
     * El handshake ECDSA (challenge → sign → connect) lo maneja WebSocketManager.
     */
    private suspend fun connectAndStore(wsUrl: String, token: String) {
        try {
            Log.w("PairingVM", "connectAndStore: $wsUrl")
            connectionRepository.connectForPairing(wsUrl, token)

            // Esperar Connected o Error (20s — incluye handshake)
            val connState = withTimeout(20_000L) {
                connectionRepository.connectionState
                    .filter { it is ConnectionState.Connected || it is ConnectionState.Error }
                    .first()
            }
            Log.w("PairingVM", "connectAndStore: state=$connState")

            if (connState is ConnectionState.Error) {
                throw Exception(connState.reason)
            }

            // Almacenar credenciales para auto-connect
            val credentials = GatewayCredentials(
                gatewayUrl = wsUrl,
                token = token,
                deviceName = "${Build.MANUFACTURER} ${Build.MODEL}",
                scopes = listOf("operator.admin", "operator.read", "operator.write", "operator.approvals")
            )
            credentialStore.storeCredentials(credentials)
            _state.value = PairingState.Paired(credentials)

        } catch (e: kotlinx.coroutines.TimeoutCancellationException) {
            Log.w("PairingVM", "connectAndStore: TIMEOUT")
            connectionRepository.disconnect()
            _state.value = PairingState.Error("Connection timed out. Check the gateway URL and try again.")
        } catch (e: Exception) {
            Log.w("PairingVM", "connectAndStore: ERROR ${e.message}", e)
            connectionRepository.disconnect()
            _state.value = PairingState.Error(e.message ?: "Connection failed")
        }
    }

    private fun isValidUrl(url: String): Boolean {
        val validSchemes = listOf("http://", "https://", "ws://", "wss://")
        if (validSchemes.none { url.startsWith(it, ignoreCase = true) }) return false
        // Verificar que tiene host después del scheme
        val afterScheme = url.substringAfter("://")
        return afterScheme.isNotEmpty() && afterScheme.contains(".")  || afterScheme.startsWith("localhost") || afterScheme.startsWith("127.") || afterScheme.startsWith("10.") || afterScheme.startsWith("192.168.")
    }
}
