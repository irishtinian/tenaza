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
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener

private const val TAG = "WebSocketManager"
private const val MAX_RECONNECT_ATTEMPTS = 30

/**
 * Gestor de conexión WebSocket con el gateway OpenClaw.
 *
 * Flujo de conexión:
 * 1. Abrir WebSocket al gateway
 * 2. Recibir evento connect.challenge con nonce
 * 3. Firmar payload con ECDSA (clave del Android Keystore)
 * 4. Enviar request "connect" con auth token + device identity + firma
 * 5. Recibir respuesta ok → Connected con scopes
 *
 * Reconexión automática con backoff exponencial + jitter.
 * Enforcement TLS para hosts remotos.
 */
class WebSocketManager(
    private val okHttpClient: OkHttpClient,
    private val scope: CoroutineScope
) {

    private val _connectionState = MutableStateFlow<ConnectionState>(ConnectionState.Disconnected)
    val connectionState: StateFlow<ConnectionState> = _connectionState

    private val _frames = MutableSharedFlow<GatewayFrame>(replay = 0, extraBufferCapacity = 64)
    val frames: SharedFlow<GatewayFrame> = _frames

    private var webSocket: WebSocket? = null
    private var reconnectJob: Job? = null
    private var connectParams: GatewayConnectParams? = null
    private var reconnectAttempt: Int = 0
    private var userDisconnected: Boolean = false
    private var handshakeComplete: Boolean = false

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    // --- API pública ---

    fun connect(params: GatewayConnectParams) {
        reconnectJob?.cancel()
        connectParams = params
        userDisconnected = false
        handshakeComplete = false
        reconnectAttempt = 0 // Reset al conectar manualmente
        _connectionState.value = ConnectionState.Connecting

        val safeUrl = enforceTls(params.url)
        Log.w(TAG, "connect(): url=$safeUrl")
        val request = Request.Builder().url(safeUrl).build()
        webSocket = okHttpClient.newWebSocket(request, webSocketListener)
    }

    fun disconnect() {
        userDisconnected = true
        reconnectJob?.cancel()
        reconnectJob = null
        webSocket?.close(1000, "User disconnect")
        webSocket = null
        handshakeComplete = false
        _connectionState.value = ConnectionState.Disconnected
    }

    fun send(frame: RequestFrame): Boolean {
        val text = json.encodeToString(frame)
        // No loguear el contenido completo — puede contener tokens
        Log.w(TAG, "send(): method=${frame.method} id=${frame.id}")
        return webSocket?.send(text) ?: false
    }

    // --- Listener ---

    private val webSocketListener = object : WebSocketListener() {

        override fun onOpen(webSocket: WebSocket, response: Response) {
            Log.w(TAG, "WebSocket abierto, esperando connect.challenge...")
            // No seteamos Connected aún — esperamos el handshake
        }

        override fun onMessage(webSocket: WebSocket, text: String) {
            val frame = parseGatewayFrame(text)
            if (frame == null) {
                Log.w(TAG, "Frame no reconocido: ${text.take(120)}")
                return
            }

            if (!handshakeComplete) {
                handleHandshake(frame)
                return
            }

            // Post-handshake: emitir frames a los observers
            _frames.tryEmit(frame)
        }

        override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
            webSocket.close(1000, "bye")
        }

        override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
            Log.w(TAG, "WebSocket cerrado: code=$code reason=$reason")
            handshakeComplete = false
            if (userDisconnected) {
                _connectionState.value = ConnectionState.Disconnected
            } else {
                scheduleReconnect()
            }
        }

        override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
            Log.w(TAG, "Fallo WebSocket: ${t.message}")
            handshakeComplete = false
            _connectionState.value = ConnectionState.Error(t.message ?: "Error desconocido")
            if (!userDisconnected) {
                scheduleReconnect()
            }
        }
    }

    // --- Handshake ---

    private fun handleHandshake(frame: GatewayFrame) {
        when (frame) {
            is GatewayFrame.Event -> {
                if (frame.event == "connect.challenge") {
                    val nonce = frame.payload?.jsonObject
                        ?.get("nonce")?.jsonPrimitive?.content
                    if (nonce != null) {
                        Log.w(TAG, "Challenge recibido, enviando connect con device auth...")
                        sendConnectRequest(nonce)
                    } else {
                        Log.w(TAG, "Challenge sin nonce")
                        _connectionState.value = ConnectionState.Error("Invalid challenge from gateway")
                    }
                }
            }
            is GatewayFrame.Response -> {
                // Respuesta al connect request
                if (frame.ok) {
                    handshakeComplete = true
                    reconnectAttempt = 0
                    _connectionState.value = ConnectionState.Connected
                    Log.w(TAG, "Handshake completado — Connected con scopes")

                    // Emitir el connect response por si alguien necesita el snapshot
                    _frames.tryEmit(frame)
                } else {
                    val errorMsg = frame.error?.message ?: "Authentication failed"
                    Log.w(TAG, "Connect rechazado: $errorMsg")
                    _connectionState.value = ConnectionState.Error(errorMsg)
                    webSocket?.close(1000, "Auth failed")
                }
            }
        }
    }

    private fun sendConnectRequest(nonce: String) {
        val params = connectParams ?: return

        val signedAtMs = System.currentTimeMillis()
        val signaturePayload = params.buildSignaturePayload(nonce, signedAtMs)
        val signature = try {
            params.signFunc?.invoke(signaturePayload.toByteArray(Charsets.UTF_8))
                ?: throw IllegalStateException("No sign function provided")
        } catch (e: Exception) {
            Log.w(TAG, "Error firmando: ${e.message}")
            _connectionState.value = ConnectionState.Error("Device signing failed: ${e.message}")
            return
        }

        val connectFrame = RequestFrame(
            method = "connect",
            params = buildJsonObject {
                put("minProtocol", 3)
                put("maxProtocol", 3)
                put("role", "operator")
                put("scopes", buildJsonArray {
                    params.scopes.forEach { add(JsonPrimitive(it)) }
                })
                put("client", buildJsonObject {
                    put("id", "openclaw-android")
                    put("displayName", "ClawPilot")
                    put("version", params.clientVersion)
                    put("platform", params.platform)
                    put("deviceFamily", params.deviceFamily)
                    put("mode", "ui")
                })
                put("auth", buildJsonObject {
                    if (params.token.isNotEmpty()) {
                        put("token", params.token)
                    }
                })
                put("device", buildJsonObject {
                    put("id", params.deviceId)
                    put("publicKey", params.publicKeyBase64)
                    put("signature", signature)
                    put("signedAt", signedAtMs)
                    put("nonce", nonce)
                })
            }
        )

        send(connectFrame)
    }


    // --- Reconexión ---

    private fun scheduleReconnect() {
        if (userDisconnected) return
        reconnectAttempt++

        // Límite máximo de intentos de reconexión
        if (reconnectAttempt > MAX_RECONNECT_ATTEMPTS) {
            Log.w(TAG, "Máximo de intentos de reconexión alcanzado ($MAX_RECONNECT_ATTEMPTS)")
            _connectionState.value = ConnectionState.Error("Max reconnect attempts reached")
            return
        }

        val delayMs = ReconnectPolicy.delayFor(reconnectAttempt)
        _connectionState.value = ConnectionState.Reconnecting(reconnectAttempt, delayMs)
        Log.w(TAG, "Reconexión en ${delayMs}ms (intento $reconnectAttempt)")

        reconnectJob = scope.launch {
            delay(delayMs)
            val p = connectParams ?: return@launch
            // No resetear reconnectAttempt aquí — solo en connect() manual
            userDisconnected = false
            handshakeComplete = false
            _connectionState.value = ConnectionState.Connecting

            val safeUrl = enforceTls(p.url)
            val request = Request.Builder().url(safeUrl).build()
            webSocket = okHttpClient.newWebSocket(request, webSocketListener)
        }
    }

    // --- TLS enforcement ---

    private fun enforceTls(url: String): String {
        if (!url.startsWith("ws://", ignoreCase = true)) return url
        val host = extractHost(url)
        if (isPrivateOrLoopback(host)) return url
        val upgraded = "wss://" + url.removePrefix("ws://")
        Log.w(TAG, "URL upgradeada a TLS: $url -> $upgraded")
        return upgraded
    }

    private fun extractHost(url: String): String {
        val withoutScheme = url.removePrefix("ws://").removePrefix("wss://")
        return withoutScheme.substringBefore("/").substringBefore(":")
    }

    private fun isPrivateOrLoopback(host: String): Boolean {
        if (host == "localhost" || host == "127.0.0.1" || host == "::1") return true
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
