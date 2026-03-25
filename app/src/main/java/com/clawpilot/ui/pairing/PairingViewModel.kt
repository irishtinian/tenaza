package com.clawpilot.ui.pairing

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.clawpilot.data.local.crypto.KeyStoreManager
import com.clawpilot.data.local.prefs.CredentialStore
import com.clawpilot.domain.model.PairingPayload
import com.clawpilot.domain.model.PairingState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.net.URL

/**
 * ViewModel que gestiona la máquina de estados del emparejamiento.
 * Flujo: Unpaired -> Scanning -> Connecting -> WaitingForApproval -> Paired/Error
 */
class PairingViewModel(
    private val credentialStore: CredentialStore,
    private val keyStoreManager: KeyStoreManager
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
     * Verifica expiración y lanza el flujo de emparejamiento.
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
     * Valida la URL ingresada manualmente y actualiza el estado.
     */
    fun onManualUrlSubmitted(url: String) {
        val trimmed = url.trim()
        if (!isValidUrl(trimmed)) {
            _manualUrlError.value = "Invalid URL format. Use http(s)://host:port or ws(s)://host:port"
            return
        }
        _manualUrlError.value = null
        _state.value = PairingState.Connecting(trimmed)
        // TODO(plan-05): Wire actual connection via ConnectionRepository
    }

    fun onRetry() {
        _state.value = PairingState.Unpaired
    }

    // --- Privado ---

    private suspend fun initiatePairing(wsUrl: String, pairingToken: String) {
        // Asegurar que existe el par ECDSA
        if (!keyStoreManager.hasKeyPair()) {
            keyStoreManager.generateEcdsaKeyPair()
        }
        val pubKey = keyStoreManager.getPublicKeyBase64()
        _state.value = PairingState.WaitingForApproval(wsUrl)
        // TODO(plan-05): Use ConnectionRepository.connectForPairing(wsUrl, pairingToken)
        //                and send connect.challenge frame with pubKey
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
