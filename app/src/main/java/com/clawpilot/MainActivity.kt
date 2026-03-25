package com.clawpilot

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.clawpilot.ui.navigation.AppNavHost
import com.clawpilot.ui.theme.ClawPilotTheme

/**
 * Única Activity de ClawPilot.
 * Edge-to-edge habilitado. Compose gestiona todo el contenido visual.
 */
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            ClawPilotTheme {
                AppNavHost()
            }
        }
    }
}
