package com.tenaza.di

import com.tenaza.data.local.crypto.Ed25519KeyManager
import com.tenaza.data.local.crypto.KeyStoreManager
import com.tenaza.data.local.prefs.AppPreferences
import com.tenaza.data.local.prefs.CredentialStore
import com.tenaza.ui.connection.ConnectionViewModel
import com.tenaza.ui.chat.ChatViewModel
import com.tenaza.ui.crons.CronViewModel
import com.tenaza.ui.agent.AgentDetailViewModel
import com.tenaza.ui.arena.ArenaViewModel
import com.tenaza.ui.dashboard.DashboardViewModel
import com.tenaza.ui.pairing.PairingViewModel
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
    viewModelOf(::CronViewModel)
    viewModelOf(::DashboardViewModel)
    viewModelOf(::AgentDetailViewModel)
    viewModelOf(::ChatViewModel)
    viewModelOf(::ArenaViewModel)
}
