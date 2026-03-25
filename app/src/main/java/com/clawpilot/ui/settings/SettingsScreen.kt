package com.clawpilot.ui.settings

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.clawpilot.BuildConfig
import com.clawpilot.MainActivity
import com.clawpilot.data.local.crypto.Ed25519KeyManager
import com.clawpilot.data.local.crypto.KeyStoreManager
import com.clawpilot.data.local.prefs.AppPreferences
import com.clawpilot.data.local.prefs.CredentialStore
import com.clawpilot.data.remote.ws.ConnectionState
import com.clawpilot.ui.connection.ConnectionViewModel
import kotlinx.coroutines.launch
import org.koin.androidx.compose.koinViewModel
import org.koin.compose.koinInject

/**
 * Pantalla de ajustes con estado de conexión, notificaciones,
 * desemparejamiento y versión.
 */
@Composable
fun SettingsScreen(
    connectionViewModel: ConnectionViewModel = koinViewModel()
) {
    val connectionState by connectionViewModel.connectionState.collectAsStateWithLifecycle()
    val gatewayVersion by connectionViewModel.gatewayVersion.collectAsStateWithLifecycle()
    val credentialStore: CredentialStore = koinInject()
    val keyStoreManager: KeyStoreManager = koinInject()
    val ed25519KeyManager: Ed25519KeyManager = koinInject()
    val appPreferences: AppPreferences = koinInject()
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    var showUnpairDialog by remember { mutableStateOf(false) }

    // Estado de notificaciones desde DataStore
    val notificationsEnabled by appPreferences.getNotificationsEnabled()
        .collectAsStateWithLifecycle(initialValue = true)

    // Estado del modo de tema desde DataStore
    val themeMode by appPreferences.getThemeMode()
        .collectAsStateWithLifecycle(initialValue = "system")

    // Estado del bloqueo biométrico desde DataStore
    val biometricEnabled by appPreferences.getBiometricEnabled()
        .collectAsStateWithLifecycle(initialValue = false)

    // Launcher para solicitar permiso POST_NOTIFICATIONS (Android 13+)
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            scope.launch { appPreferences.setNotificationsEnabled(true) }
        }
    }

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

        // Sección tema (Dark / Light / System)
        item {
            Text("Theme", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                val options = listOf("system" to "System", "light" to "Light", "dark" to "Dark")
                options.forEach { (value, label) ->
                    FilterChip(
                        selected = themeMode == value,
                        onClick = {
                            scope.launch { appPreferences.setThemeMode(value) }
                        },
                        label = { Text(label) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                            selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    )
                }
            }
            Text(
                text = "Choose between system default, light, or dark appearance",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(16.dp))
        }

        // Sección notificaciones
        item {
            Text("Notifications", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Enable notifications",
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.bodyLarge
                )
                Switch(
                    checked = notificationsEnabled,
                    onCheckedChange = { enabled ->
                        scope.launch {
                            if (enabled && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                // Verificar si ya tenemos permiso
                                val hasPermission = ContextCompat.checkSelfPermission(
                                    context,
                                    Manifest.permission.POST_NOTIFICATIONS
                                ) == PackageManager.PERMISSION_GRANTED

                                if (!hasPermission) {
                                    // Solicitar permiso; el callback actualizará la preferencia
                                    notificationPermissionLauncher.launch(
                                        Manifest.permission.POST_NOTIFICATIONS
                                    )
                                    return@launch
                                }
                            }
                            appPreferences.setNotificationsEnabled(enabled)
                        }
                    }
                )
            }
            Text(
                text = "Receive approval requests, alerts, and cron failure notifications",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(16.dp))
        }

        // Sección seguridad (bloqueo biométrico)
        item {
            Text("Security", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Biometric Lock",
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.bodyLarge
                )
                Switch(
                    checked = biometricEnabled,
                    onCheckedChange = { enabled ->
                        scope.launch {
                            if (enabled) {
                                // Verificar que el dispositivo soporta biometría/PIN
                                val activity = context as? MainActivity
                                if (activity != null && activity.canAuthenticateBiometric()) {
                                    appPreferences.setBiometricEnabled(true)
                                } else {
                                    Toast.makeText(
                                        context,
                                        "No biometric or device credential available",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                }
                            } else {
                                appPreferences.setBiometricEnabled(false)
                            }
                        }
                    }
                )
            }
            Text(
                text = "Require fingerprint, face, or PIN to open the app",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
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

            // Versión dinámica desde BuildConfig
            Text(
                "ClawPilot v${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})",
                style = MaterialTheme.typography.bodyMedium
            )
            Spacer(Modifier.height(4.dp))

            // Versión del gateway (obtenida del handshake WebSocket)
            Text(
                text = if (gatewayVersion != null) "Gateway: v$gatewayVersion" else "Gateway: not connected",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(4.dp))

            // Device ID (hash de la clave pública Ed25519)
            val deviceId = remember {
                if (ed25519KeyManager.hasKeyPair()) {
                    ed25519KeyManager.getDeviceId().take(16) + "..."
                } else {
                    "Not paired"
                }
            }
            Text(
                "Device ID: $deviceId",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(4.dp))

            // Estado de conexión con info del gateway
            val gatewayInfo = when (val state = connectionState) {
                is ConnectionState.Connected -> "Gateway connected"
                is ConnectionState.Error -> "Error: ${state.reason}"
                is ConnectionState.Reconnecting -> "Reconnecting (attempt ${state.attempt})"
                else -> "Not connected"
            }
            Text(
                gatewayInfo,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
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
