package com.clawpilot

import android.app.Application
import com.clawpilot.di.appModule
import com.clawpilot.di.networkModule
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin

/**
 * Application class de ClawPilot.
 * Inicializa Koin DI al arrancar la app.
 */
class ClawPilotApp : Application() {

    override fun onCreate() {
        super.onCreate()

        startKoin {
            androidContext(this@ClawPilotApp)
            modules(appModule, networkModule)
        }
    }
}
