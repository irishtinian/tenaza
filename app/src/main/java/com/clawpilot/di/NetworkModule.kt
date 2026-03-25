package com.clawpilot.di

import com.clawpilot.data.remote.ws.WebSocketManager
import com.clawpilot.data.repository.ConnectionRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import okhttp3.OkHttpClient
import org.koin.dsl.module
import java.util.concurrent.TimeUnit

/**
 * Módulo Koin para la capa de red.
 *
 * OkHttpClient con pingInterval de 25s para mantener la conexión viva (Pitfall 7 — battery drain
 * por keepalive demasiado agresivo evitado con 25s, valor recomendado para proxies típicos).
 */
val networkModule = module {
    single {
        OkHttpClient.Builder()
            .pingInterval(25, TimeUnit.SECONDS) // Heartbeat a nivel de aplicación
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build()
    }

    single {
        WebSocketManager(
            okHttpClient = get(),
            scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        )
    }

    single {
        ConnectionRepository(
            webSocketManager = get(),
            credentialStore = get()
        )
    }
}
