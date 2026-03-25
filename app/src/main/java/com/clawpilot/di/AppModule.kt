package com.clawpilot.di

import com.clawpilot.data.local.crypto.KeyStoreManager
import com.clawpilot.data.local.prefs.AppPreferences
import com.clawpilot.data.local.prefs.CredentialStore
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module

/**
 * Módulo Koin principal.
 * Registra almacenamiento local: CredentialStore (cifrado), AppPreferences, KeyStoreManager.
 */
val appModule = module {
    single { CredentialStore(androidContext()) }
    single { AppPreferences(androidContext()) }
    single { KeyStoreManager() }
}
