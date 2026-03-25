package com.clawpilot.ui.crons

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Snackbar
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.koin.compose.viewmodel.koinViewModel

// Colores para indicadores de estado
private val ColorOk = Color(0xFF4CAF50)
private val ColorError = Color(0xFFF44336)
private val ColorNever = Color(0xFF9E9E9E)

/**
 * Pantalla de gestión de cron jobs.
 * Muestra lista con toggle, ejecución manual y eliminación.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CronsScreen(
    viewModel: CronViewModel = koinViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    // Diálogo de confirmación para eliminar
    var jobToDelete by remember { mutableStateOf<CronJob?>(null) }

    Box(Modifier.fillMaxSize()) {
        PullToRefreshBox(
            isRefreshing = state.isLoading,
            onRefresh = { viewModel.refresh() },
            modifier = Modifier.fillMaxSize()
        ) {
            when {
                // Carga inicial
                state.isLoading && state.jobs.isEmpty() -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }
                // Lista vacía
                !state.isLoading && state.jobs.isEmpty() && state.error == null -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                imageVector = Icons.Default.Schedule,
                                contentDescription = null,
                                modifier = Modifier.size(64.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                            )
                            Spacer(Modifier.height(16.dp))
                            Text(
                                text = "No hay cron jobs configurados",
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
                // Lista con contenido
                else -> {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        item { Spacer(Modifier.height(8.dp)) }

                        // Error si hay
                        state.error?.let { error ->
                            item { ErrorBanner(error, onDismiss = { viewModel.clearError() }) }
                        }

                        items(state.jobs, key = { it.id }) { job ->
                            CronJobCard(
                                job = job,
                                onToggleEnabled = { enabled ->
                                    viewModel.toggleEnabled(job.id, enabled)
                                },
                                onRunNow = { viewModel.runNow(job.id) },
                                onDelete = { jobToDelete = job }
                            )
                        }

                        item { Spacer(Modifier.height(8.dp)) }
                    }
                }
            }
        }

        // Snackbar de error cuando no hay jobs (estado vacío con error)
        if (state.error != null && state.jobs.isEmpty()) {
            Snackbar(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(16.dp),
                action = {
                    TextButton(onClick = { viewModel.clearError() }) {
                        Text("OK")
                    }
                }
            ) {
                Text(state.error ?: "")
            }
        }
    }

    // Diálogo de confirmación para eliminar
    jobToDelete?.let { job ->
        AlertDialog(
            onDismissRequest = { jobToDelete = null },
            title = { Text("Eliminar cron job") },
            text = {
                Text("Eliminar \"${job.label.ifEmpty { job.id }}\"?\nEsta acción no se puede deshacer.")
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteJob(job.id)
                    jobToDelete = null
                }) {
                    Text("Eliminar", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { jobToDelete = null }) {
                    Text("Cancelar")
                }
            }
        )
    }
}

/**
 * Card individual de un cron job con toggle, botón ejecutar y menú contextual.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun CronJobCard(
    job: CronJob,
    onToggleEnabled: (Boolean) -> Unit,
    onRunNow: () -> Unit,
    onDelete: () -> Unit
) {
    // Menú contextual por long press
    var showMenu by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = {},
                onLongClick = { showMenu = true }
            ),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp)
        ) {
            // Fila superior: indicador + label + toggle
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Indicador de último estado
                StatusDot(job.lastStatus)

                Spacer(Modifier.width(12.dp))

                // Label y schedule
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = job.label.ifEmpty { "Sin nombre" },
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = job.schedule,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1
                    )
                }

                // Toggle activar/desactivar
                Switch(
                    checked = job.enabled,
                    onCheckedChange = onToggleEnabled
                )
            }

            Spacer(Modifier.height(8.dp))

            // Fila inferior: agente + último run + botón ejecutar
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Agente
                if (job.agentId.isNotEmpty()) {
                    Text(
                        text = job.agentId,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    Spacer(Modifier.width(8.dp))
                }

                // Último run (tiempo relativo)
                Text(
                    text = formatRelativeTime(job.lastRunAt),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f)
                )

                // Botón ejecutar ahora
                IconButton(
                    onClick = onRunNow,
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = "Ejecutar ahora",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }

        // Menú contextual (long press)
        DropdownMenu(
            expanded = showMenu,
            onDismissRequest = { showMenu = false }
        ) {
            DropdownMenuItem(
                text = { Text("Ejecutar ahora") },
                onClick = {
                    showMenu = false
                    onRunNow()
                },
                leadingIcon = {
                    Icon(Icons.Default.PlayArrow, contentDescription = null)
                }
            )
            DropdownMenuItem(
                text = {
                    Text("Eliminar", color = MaterialTheme.colorScheme.error)
                },
                onClick = {
                    showMenu = false
                    onDelete()
                },
                leadingIcon = {
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error
                    )
                }
            )
        }
    }
}

/** Indicador circular de estado del último run. */
@Composable
private fun StatusDot(lastStatus: String?) {
    val color by animateColorAsState(
        targetValue = when (lastStatus) {
            "ok", "success" -> ColorOk
            "error", "fail", "failed" -> ColorError
            else -> ColorNever
        },
        label = "statusColor"
    )

    Surface(
        shape = CircleShape,
        color = color,
        modifier = Modifier.size(10.dp)
    ) {}
}

/** Banner de error dentro de la lista. */
@Composable
private fun ErrorBanner(message: String, onDismiss: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onErrorContainer,
                modifier = Modifier.weight(1f)
            )
            TextButton(onClick = onDismiss) {
                Text("OK")
            }
        }
    }
}

/**
 * Formatea un timestamp epoch (ms) a texto relativo en español.
 * Ej: "hace 2h", "ayer", "hace 5m", "nunca ejecutado".
 */
private fun formatRelativeTime(epochMs: Long?): String {
    if (epochMs == null || epochMs <= 0) return "nunca ejecutado"

    val now = System.currentTimeMillis()
    val diffMs = now - epochMs

    if (diffMs < 0) return "programado"

    val seconds = diffMs / 1000
    val minutes = seconds / 60
    val hours = minutes / 60
    val days = hours / 24

    return when {
        seconds < 60 -> "hace ${seconds}s"
        minutes < 60 -> "hace ${minutes}m"
        hours < 24 -> "hace ${hours}h"
        days == 1L -> "ayer"
        days < 7 -> "hace ${days}d"
        else -> "hace ${days / 7}sem"
    }
}
