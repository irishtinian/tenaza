package com.clawpilot

import android.app.Application
import android.content.Intent
import android.os.Build
import com.clawpilot.di.appModule
import com.clawpilot.di.networkModule
import com.clawpilot.service.ClawPilotService
import com.clawpilot.service.NotificationHelper
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin

/**
 * Application class de ClawPilot.
 * Inicializa Koin DI, canales de notificación y el foreground service.
 */
class ClawPilotApp : Application() {

    override fun onCreate() {
        super.onCreate()

        startKoin {
            androidContext(this@ClawPilotApp)
            modules(appModule, networkModule)
        }

        // Crear canales de notificación (idempotente, seguro llamar siempre)
        NotificationHelper.createNotificationChannels(this)

        // Iniciar foreground service para mantener el proceso vivo
        startClawPilotService()
    }

    private fun startClawPilotService() {
        val intent = Intent(this, ClawPilotService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
    }
}
