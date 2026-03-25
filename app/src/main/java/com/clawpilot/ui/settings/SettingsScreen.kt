package com.clawpilot.ui.settings

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.clawpilot.data.local.crypto.KeyStoreManager
import com.clawpilot.data.local.prefs.CredentialStore
import com.clawpilot.data.remote.ws.ConnectionState
import com.clawpilot.ui.connection.ConnectionViewModel
import kotlinx.coroutines.launch
import org.koin.androidx.compose.koinViewModel
import org.koin.compose.koinInject

/**
 * Pantalla de ajustes con estado de conexión, desemparejamiento y versión.
 */
@Composable
fun SettingsScreen(
    connectionViewModel: ConnectionViewModel = koinViewModel()
) {
    val connectionState by connectionViewModel.connectionState.collectAsStateWithLifecycle()
    val credentialStore: CredentialStore = koinInject()
    val keyStoreManager: KeyStoreManager = koinInject()
    val scope = rememberCoroutineScope()

    var showUnpairDialog by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        item {
            Text("Settings", style = MaterialTheme.typography.headlineMedium)
            Spacer(Modifier.height(24.dp))
        }

        // Sección conexión
        item {
            Text("Connection", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                val (color, label) = when (connectionState) {
                    is ConnectionState.Connected -> Color(0xFF4CAF50) to "Connected"
                    is ConnectionState.Connecting -> Color(0xFFFFC107) to "Connecting..."
                    is ConnectionState.Reconnecting -> Color(0xFFFF9800) to "Reconnecting..."
                    is ConnectionState.Disconnected -> Color(0xFFF44336) to "Disconnected"
                    is ConnectionState.Error -> Color(0xFFF44336) to "Error"
                }
                Canvas(Modifier.size(12.dp)) { drawCircle(color) }
                Spacer(Modifier.width(8.dp))
                Text(label)
            }
            Spacer(Modifier.height(16.dp))
        }

        // Sección desemparejamiento
        item {
            Text("Device", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))
            OutlinedButton(
                onClick = { showUnpairDialog = true },
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = MaterialTheme.colorScheme.error
                )
            ) {
                Text("Unpair from Gateway")
            }
        }

        // Info de la app
        item {
            Spacer(Modifier.height(24.dp))
            Text("About", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))
            Text("ClawPilot v0.1.0", style = MaterialTheme.typography.bodyMedium)
        }
    }

    // Diálogo de confirmación de desemparejamiento
    if (showUnpairDialog) {
        AlertDialog(
            onDismissRequest = { showUnpairDialog = false },
            title = { Text("Unpair Device") },
            text = {
                Text(
                    "This will disconnect from the gateway and remove all stored credentials. " +
                            "You will need to scan the QR code again to reconnect."
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch {
                        connectionViewModel.disconnect()
                        credentialStore.clearCredentials()
                        keyStoreManager.deleteKeyPair()
                    }
                    showUnpairDialog = false
                }) {
                    Text("Unpair", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showUnpairDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}
