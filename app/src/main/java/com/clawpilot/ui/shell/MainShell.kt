package com.clawpilot.ui.shell

import androidx.compose.foundation.layout.Box
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
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.ui.NavDisplay
import com.clawpilot.R
import com.clawpilot.ui.navigation.AppRoute

/**
 * Shell principal de la app con navegación inferior de 4 pestañas.
 * Cada pestaña mantiene su propio backstack independiente.
 */
@Composable
fun MainShell() {
    val tabs: List<AppRoute> = listOf(
        AppRoute.Chat,
        AppRoute.Dashboard,
        AppRoute.Crons,
        AppRoute.Settings
    )

    var selectedTab by remember { mutableStateOf<AppRoute>(AppRoute.Dashboard) }

    // Backstack independiente por pestaña — preserva estado al cambiar
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
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text("Chat")
                        }
                    }
                    AppRoute.Dashboard -> NavEntry(route) {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text("Dashboard")
                        }
                    }
                    AppRoute.Crons -> NavEntry(route) {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text("Crons")
                        }
                    }
                    AppRoute.Settings -> NavEntry(route) {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text("Settings")
                        }
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
