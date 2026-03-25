package com.clawpilot.ui.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.clawpilot.ui.shell.MainShell

/**
 * Manejador de navegación de nivel superior.
 *
 * Decide si mostrar el flujo de emparejamiento (Pairing) o la shell principal
 * con tabs de navegación. En esta versión el emparejamiento se implementa
 * en el plan 01-02, por lo que por defecto se muestra MainShell.
 *
 * TODO plan 01-02: leer isPaired desde CredentialStore y redirigir a PairingScreen si es necesario
 */
@Composable
fun AppNavHost() {
    // Placeholder: en plan 01-02 se leerá el estado real de emparejamiento
    val isPaired = true

    if (isPaired) {
        MainShell()
    } else {
        // Pantalla de emparejamiento — implementada en plan 01-02
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("Pairing")
        }
    }
}
