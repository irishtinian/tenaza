package com.clawpilot.di

import com.clawpilot.data.local.crypto.Ed25519KeyManager
import com.clawpilot.data.local.crypto.KeyStoreManager
import com.clawpilot.data.local.prefs.AppPreferences
import com.clawpilot.data.local.prefs.CredentialStore
import com.clawpilot.ui.connection.ConnectionViewModel
import com.clawpilot.ui.chat.ChatViewModel
import com.clawpilot.ui.dashboard.DashboardViewModel
import com.clawpilot.ui.pairing.PairingViewModel
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

val appModule = module {
    single { CredentialStore(androidContext()) }
    single { AppPreferences(androidContext()) }
    single { KeyStoreManager() }
    single { Ed25519KeyManager(androidContext()) }
    viewModelOf(::PairingViewModel)
    viewModelOf(::ConnectionViewModel)
    viewModelOf(::DashboardViewModel)
    viewModelOf(::ChatViewModel)
}
