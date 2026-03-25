package com.tenaza

import android.app.Application
import android.content.Intent
import android.os.Build
import com.tenaza.di.appModule
import com.tenaza.di.networkModule
import com.tenaza.service.TenazaService
import com.tenaza.service.NotificationHelper
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin

/**
 * Application class de Tenaza.
 * Inicializa Koin DI, canales de notificación y el foreground service.
 */
class TenazaApp : Application() {

    override fun onCreate() {
        super.onCreate()

        startKoin {
            androidContext(this@TenazaApp)
            modules(appModule, networkModule)
        }

        // Crear canales de notificación (idempotente, seguro llamar siempre)
        NotificationHelper.createNotificationChannels(this)

        // Iniciar foreground service para mantener el proceso vivo
        startTenazaService()
    }

    private fun startTenazaService() {
        val intent = Intent(this, TenazaService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
    }
}
