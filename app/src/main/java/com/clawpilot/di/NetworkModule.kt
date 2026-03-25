package com.clawpilot.di

import com.clawpilot.data.remote.ws.WebSocketManager
import com.clawpilot.data.repository.ConnectionRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import okhttp3.OkHttpClient
import org.koin.dsl.module
import java.util.concurrent.TimeUnit

val networkModule = module {
    single {
        OkHttpClient.Builder()
            .pingInterval(25, TimeUnit.SECONDS)
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
            credentialStore = get(),
            ed25519KeyManager = get()
        )
    }
}
