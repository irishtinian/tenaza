package com.clawpilot.data.remote.ws

import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener

private const val TAG = "WebSocketManager"

/**
 * Gestor de conexión WebSocket con el gateway OpenClaw.
 *
 * Características:
 * - Reconexión automática con backoff exponencial + jitter (ReconnectPolicy)
 * - Enforcement TLS: fuerza wss:// para hosts remotos (no-loopback)
 * - Parsea frames JSON-RPC entrantes y los emite como SharedFlow
 * - Expone el estado de conexión como StateFlow
 */
class WebSocketManager(
    private val okHttpClient: OkHttpClient,
    private val scope: CoroutineScope
) {

    // --- Estado público ---

    private val _connectionState = MutableStateFlow<ConnectionState>(ConnectionState.Disconnected)
    val connectionState: StateFlow<ConnectionState> = _connectionState

    private val _frames = MutableSharedFlow<GatewayFrame>(replay = 0, extraBufferCapacity = 64)
    val frames: SharedFlow<GatewayFrame> = _frames

    // --- Estado interno ---

    private var webSocket: WebSocket? = null
    private var reconnectJob: Job? = null
    private var currentUrl: String? = null
    private var currentToken: String? = null
    private var reconnectAttempt: Int = 0
    private var userDisconnected: Boolean = false

    // Instancia reutilizable de Json para no crear una nueva en cada llamada
    private val json = Json { ignoreUnknownKeys = true }

    // --- API pública ---

    /**
     * Inicia la conexión WebSocket.
     * Fuerza wss:// para hosts que no sean loopback o privados (SECR-01).
     */
    fun connect(url: String, token: String) {
        reconnectJob?.cancel()
        currentUrl = url
        currentToken = token
        userDisconnected = false
        _connectionState.value = ConnectionState.Connecting

        val safeUrl = enforceTls(url)
        val fullUrl = if (token.isNotEmpty()) "$safeUrl?token=$token" else safeUrl
        Log.w(TAG, "connect(): url=$fullUrl")
        val request = Request.Builder()
            .url(fullUrl)
            .build()

        webSocket = okHttpClient.newWebSocket(request, webSocketListener)
        Log.w(TAG, "connect(): WebSocket created, waiting for callback...")
    }

    /**
     * Desconecta limpiamente por iniciativa del usuario.
     * No dispara reconexión.
     */
    fun disconnect() {
        userDisconnected = true
        reconnectJob?.cancel()
        reconnectJob = null
        webSocket?.close(1000, "User disconnect")
        webSocket = null
        _connectionState.value = ConnectionState.Disconnected
    }

    /**
     * Envía un frame JSON-RPC serializado.
     * Devuelve false si el WebSocket no está abierto.
     */
    fun send(frame: RequestFrame): Boolean {
        val text = json.encodeToString(frame)
        return webSocket?.send(text) ?: false
    }

    // --- Listener interno ---

    private val webSocketListener = object : WebSocketListener() {

        override fun onOpen(webSocket: WebSocket, response: Response) {
            Log.w(TAG, "Conexión establecida")
            _connectionState.value = ConnectionState.Connected
            reconnectAttempt = 0
        }

        override fun onMessage(webSocket: WebSocket, text: String) {
            val frame = parseGatewayFrame(text)
            if (frame != null) {
                _frames.tryEmit(frame)
            } else {
                Log.w(TAG, "Frame no reconocido: ${text.take(120)}")
            }
        }

        override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
            webSocket.close(1000, "bye")
        }

        override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
            Log.w(TAG, "WebSocket cerrado: code=$code reason=$reason")
            if (userDisconnected) {
                _connectionState.value = ConnectionState.Disconnected
            } else {
                scheduleReconnect()
            }
        }

        override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
            Log.w(TAG, "Fallo WebSocket: ${t.message}")
            _connectionState.value = ConnectionState.Error(t.message ?: "Error desconocido")
            if (!userDisconnected) {
                scheduleReconnect()
            }
        }
    }

    // --- Reconexión ---

    private fun scheduleReconnect() {
        if (userDisconnected) return

        reconnectAttempt++
        val delayMs = ReconnectPolicy.delayFor(reconnectAttempt)
        _connectionState.value = ConnectionState.Reconnecting(reconnectAttempt, delayMs)

        Log.w(TAG, "Reconexión en ${delayMs}ms (intento $reconnectAttempt)")

        reconnectJob = scope.launch {
            delay(delayMs)
            val url = currentUrl ?: return@launch
            val token = currentToken ?: return@launch
            connect(url, token)
        }
    }

    // --- TLS enforcement (SECR-01) ---

    /**
     * Para hosts que no sean loopback (127.x, localhost) ni red privada (10.x, 192.168.x, 172.16-31.x),
     * reemplaza ws:// por wss:// para garantizar cifrado en tránsito.
     */
    private fun enforceTls(url: String): String {
        if (!url.startsWith("ws://", ignoreCase = true)) return url

        val host = extractHost(url)
        if (isPrivateOrLoopback(host)) return url

        val upgraded = "wss://" + url.removePrefix("ws://")
        Log.w(TAG, "URL upgradeada a TLS: $url -> $upgraded")
        return upgraded
    }

    private fun extractHost(url: String): String {
        // Formato: ws://host:port/path o ws://host/path
        val withoutScheme = url.removePrefix("ws://").removePrefix("wss://")
        return withoutScheme.substringBefore("/").substringBefore(":")
    }

    private fun isPrivateOrLoopback(host: String): Boolean {
        if (host == "localhost" || host == "127.0.0.1" || host == "::1") return true
        // Verificar rangos privados IPv4
        val parts = host.split(".").mapNotNull { it.toIntOrNull() }
        if (parts.size != 4) return false
        return when {
            parts[0] == 10 -> true
            parts[0] == 192 && parts[1] == 168 -> true
            parts[0] == 172 && parts[1] in 16..31 -> true
            else -> false
        }
    }
}
