package com.clawpilot.ui.connection

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.clawpilot.data.remote.ws.ConnectionState
import com.clawpilot.data.repository.ConnectionRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * ViewModel singleton que expone el estado de conexión a todas las pantallas.
 * Auto-conecta al iniciar si hay credenciales almacenadas.
 */
class ConnectionViewModel(
    private val connectionRepository: ConnectionRepository
) : ViewModel() {

    val connectionState: StateFlow<ConnectionState> = connectionRepository.connectionState

    /** Versión del gateway obtenida del handshake */
    val gatewayVersion: StateFlow<String?> = connectionRepository.gatewayVersion

    val isPaired: StateFlow<Boolean> = connectionRepository.isPaired
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    init {
        connectIfPaired()
    }

    fun connectIfPaired() {
        viewModelScope.launch {
            connectionRepository.connectWithStoredCredentials()
        }
    }

    fun disconnect() {
        connectionRepository.disconnect()
    }
}
