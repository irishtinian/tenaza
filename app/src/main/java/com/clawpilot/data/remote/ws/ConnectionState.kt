package com.clawpilot.data.remote.ws

/**
 * Estado de la conexión WebSocket con el gateway.
 * Expuesto como StateFlow para que la UI reaccione en tiempo real.
 */
sealed class ConnectionState {
    data object Disconnected : ConnectionState()
    data object Connecting : ConnectionState()
    data object Connected : ConnectionState()
    data class Reconnecting(val attempt: Int, val nextRetryMs: Long) : ConnectionState()
    data class Error(val reason: String) : ConnectionState()
}
