package com.clawpilot

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.clawpilot.data.local.prefs.AppPreferences
import com.clawpilot.ui.navigation.AppNavHost
import com.clawpilot.ui.theme.ClawPilotTheme
import org.koin.android.ext.android.inject

/**
 * Única Activity de ClawPilot.
 * Edge-to-edge habilitado. Compose gestiona todo el contenido visual.
 * Lee la preferencia de tema para aplicar dark/light/system.
 */
class MainActivity : ComponentActivity() {

    private val appPreferences: AppPreferences by inject()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            // Leer preferencia de tema de forma reactiva
            val themeMode by appPreferences.getThemeMode()
                .collectAsStateWithLifecycle(initialValue = "system")

            val isDark = when (themeMode) {
                "dark" -> true
                "light" -> false
                else -> isSystemInDarkTheme()
            }

            ClawPilotTheme(darkTheme = isDark) {
                AppNavHost()
            }
        }
    }
}
