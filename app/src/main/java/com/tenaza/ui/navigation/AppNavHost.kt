package com.tenaza.ui.navigation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.ui.NavDisplay
import com.tenaza.domain.model.PairingState
import com.tenaza.ui.connection.ConnectionViewModel
import com.tenaza.ui.pairing.ManualUrlScreen
import com.tenaza.ui.pairing.PairingViewModel
import com.tenaza.ui.pairing.QrScanScreen
import com.tenaza.ui.shell.MainShell
import org.koin.androidx.compose.koinViewModel

/**
 * Navegación de nivel superior.
 * Muestra el flujo de emparejamiento o la shell principal según si hay credenciales.
 */
@Composable
fun AppNavHost() {
    val connectionViewModel: ConnectionViewModel = koinViewModel()
    val isPaired by connectionViewModel.isPaired.collectAsStateWithLifecycle()

    if (isPaired) {
        MainShell(connectionViewModel = connectionViewModel)
    } else {
        PairingFlow()
    }
}

/**
 * Sub-navegación del flujo de emparejamiento: escáner QR y entrada manual de URL.
 */
@Composable
private fun PairingFlow() {
    val pairingViewModel: PairingViewModel = koinViewModel()
    val pairingState by pairingViewModel.state.collectAsStateWithLifecycle()
    val backStack = rememberNavBackStack(AppRoute.Pairing)

    Box(modifier = Modifier.fillMaxSize()) {
        NavDisplay(
            backStack = backStack,
            entryProvider = { route ->
                when (route) {
                    AppRoute.Pairing -> NavEntry(route) {
                        QrScanScreen(
                            onQrDetected = { pairingViewModel.onQrDetected(it) },
                            onManualEntry = { backStack.add(AppRoute.ManualUrl) }
                        )
                    }
                    AppRoute.ManualUrl -> NavEntry(route) {
                        ManualUrlScreen(
                            viewModel = pairingViewModel,
                            onBack = { backStack.removeLastOrNull() }
                        )
                    }
                    else -> NavEntry(route) {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text("Unknown")
                        }
                    }
                }
            }
        )

        // Overlay de estado del emparejamiento
        PairingStateOverlay(
            state = pairingState,
            onRetry = { pairingViewModel.onRetry() }
        )
    }
}

/**
 * Overlay que muestra el estado del emparejamiento sobre el escáner/formulario.
 */
@Composable
private fun PairingStateOverlay(state: PairingState, onRetry: () -> Unit) {
    when (state) {
        is PairingState.Connecting, is PairingState.WaitingForApproval -> {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .background(
                            MaterialTheme.colorScheme.surface.copy(alpha = 0.9f),
                            RoundedCornerShape(16.dp)
                        )
                        .padding(32.dp)
                ) {
                    CircularProgressIndicator()
                    Text(
                        text = if (state is PairingState.WaitingForApproval)
                            "Waiting for gateway approval..."
                        else
                            "Connecting to gateway...",
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.padding(top = 16.dp)
                    )
                }
            }
        }
        is PairingState.Error -> {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .background(
                            MaterialTheme.colorScheme.errorContainer,
                            RoundedCornerShape(16.dp)
                        )
                        .padding(32.dp)
                        .fillMaxWidth(0.85f)
                ) {
                    Text(
                        text = state.message,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                    TextButton(
                        onClick = onRetry,
                        modifier = Modifier.padding(top = 16.dp)
                    ) {
                        Text("Try Again")
                    }
                }
            }
        }
        else -> {} // Unpaired, Scanning, Paired: no overlay
    }
}
