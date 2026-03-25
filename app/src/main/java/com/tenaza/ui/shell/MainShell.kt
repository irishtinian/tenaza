package com.tenaza.ui.shell

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.Forum
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.ui.NavDisplay
import com.tenaza.R
import com.tenaza.data.remote.ws.ConnectionState
import com.tenaza.ui.connection.ConnectionStatusBar
import com.tenaza.ui.connection.ConnectionViewModel
import com.tenaza.ui.chat.ChatScreen
import com.tenaza.ui.crons.CronsScreen
import com.tenaza.ui.agent.AgentDetailScreen
import com.tenaza.ui.arena.ArenaScreen
import com.tenaza.ui.dashboard.DashboardScreen
import com.tenaza.ui.navigation.AppRoute
import com.tenaza.ui.settings.SettingsScreen

/**
 * Shell principal de la app con navegación inferior de 4 pestañas
 * y barra de estado de conexión.
 */
@Composable
fun MainShell(connectionViewModel: ConnectionViewModel) {
    val connectionState by connectionViewModel.connectionState.collectAsStateWithLifecycle()

    val tabs: List<AppRoute> = listOf(
        AppRoute.Chat,
        AppRoute.Dashboard,
        AppRoute.Crons,
        AppRoute.Settings
    )

    var selectedTab by remember { mutableStateOf<AppRoute>(AppRoute.Dashboard) }

    // Estado para el atajo "Chat with agent" desde AgentDetail
    // Par (agentId, agentName), null si no hay pendiente
    var pendingChatAgent by remember { mutableStateOf<Pair<String, String>?>(null) }

    val chatBackstack = rememberNavBackStack(AppRoute.Chat)
    val dashboardBackstack = rememberNavBackStack(AppRoute.Dashboard)
    val cronsBackstack = rememberNavBackStack(AppRoute.Crons)
    val settingsBackstack = rememberNavBackStack(AppRoute.Settings)

    val currentBackstack = when (selectedTab) {
        AppRoute.Chat -> chatBackstack
        AppRoute.Dashboard -> dashboardBackstack
        AppRoute.Crons -> cronsBackstack
        AppRoute.Settings -> settingsBackstack
        else -> dashboardBackstack
    }

    Scaffold(
        topBar = {
            // Solo mostrar barra de estado cuando NO está Connected
            if (connectionState !is ConnectionState.Connected) {
                ConnectionStatusBar(connectionState = connectionState)
            }
        },
        bottomBar = {
            NavigationBar {
                tabs.forEach { tab ->
                    NavigationBarItem(
                        selected = selectedTab == tab,
                        onClick = { selectedTab = tab },
                        icon = {
                            when (tab) {
                                AppRoute.Chat -> Icon(
                                    imageVector = Icons.Default.Forum,
                                    contentDescription = stringResource(R.string.tab_chat)
                                )
                                AppRoute.Dashboard -> Icon(
                                    imageVector = Icons.Default.Dashboard,
                                    contentDescription = stringResource(R.string.tab_dashboard)
                                )
                                AppRoute.Crons -> Icon(
                                    imageVector = Icons.Default.Schedule,
                                    contentDescription = stringResource(R.string.tab_crons)
                                )
                                AppRoute.Settings -> Icon(
                                    imageVector = Icons.Default.Settings,
                                    contentDescription = stringResource(R.string.tab_settings)
                                )
                                else -> {}
                            }
                        },
                        label = {
                            when (tab) {
                                AppRoute.Chat -> Text(stringResource(R.string.tab_chat))
                                AppRoute.Dashboard -> Text(stringResource(R.string.tab_dashboard))
                                AppRoute.Crons -> Text(stringResource(R.string.tab_crons))
                                AppRoute.Settings -> Text(stringResource(R.string.tab_settings))
                                else -> {}
                            }
                        }
                    )
                }
            }
        }
    ) { paddingValues ->
        NavDisplay(
            backStack = currentBackstack,
            modifier = Modifier.padding(paddingValues),
            entryProvider = { route ->
                when (route) {
                    AppRoute.Chat -> NavEntry(route) {
                        // Consumir agente pendiente si hay uno (atajo desde AgentDetail)
                        val pending = pendingChatAgent
                        ChatScreen(
                            onOpenArena = {
                                chatBackstack.add(AppRoute.Arena)
                            },
                            pendingAgentId = pending?.first,
                            pendingAgentName = pending?.second,
                            onPendingConsumed = { pendingChatAgent = null }
                        )
                    }
                    AppRoute.Arena -> NavEntry(route) {
                        ArenaScreen(
                            onBack = { chatBackstack.removeLastOrNull() }
                        )
                    }
                    AppRoute.Dashboard -> NavEntry(route) {
                        DashboardScreen(
                            onAgentTap = { agentId, agentName ->
                                dashboardBackstack.add(
                                    AppRoute.AgentDetail(agentId, agentName)
                                )
                            }
                        )
                    }
                    is AppRoute.AgentDetail -> NavEntry(route) {
                        val detail = route as AppRoute.AgentDetail
                        AgentDetailScreen(
                            agentId = detail.agentId,
                            agentName = detail.agentName,
                            onBack = { dashboardBackstack.removeLastOrNull() },
                            onChatWithAgent = { agentId, agentName ->
                                // Volver al dashboard, guardar agente pendiente y cambiar a Chat
                                dashboardBackstack.removeLastOrNull()
                                pendingChatAgent = Pair(agentId, agentName)
                                selectedTab = AppRoute.Chat
                            }
                        )
                    }
                    AppRoute.Crons -> NavEntry(route) {
                        CronsScreen()
                    }
                    AppRoute.Settings -> NavEntry(route) {
                        SettingsScreen(connectionViewModel = connectionViewModel)
                    }
                    else -> NavEntry(route) {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text("Unknown")
                        }
                    }
                }
            }
        )
    }
}
