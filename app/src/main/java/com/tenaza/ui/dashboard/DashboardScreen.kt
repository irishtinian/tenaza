package com.tenaza.ui.dashboard

import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.MonetizationOn
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tenaza.ui.connection.ConnectionViewModel
import org.koin.compose.viewmodel.koinViewModel

/**
 * Pantalla principal del Dashboard: salud del gateway, canales, agentes.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    onAgentTap: (agentId: String, agentName: String) -> Unit = { _, _ -> },
    viewModel: DashboardViewModel = koinViewModel(),
    connectionViewModel: ConnectionViewModel = koinViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val gatewayVersion by connectionViewModel.gatewayVersion.collectAsStateWithLifecycle()

    PullToRefreshBox(
        isRefreshing = state.isLoading,
        onRefresh = { viewModel.refresh() },
        modifier = Modifier.fillMaxSize()
    ) {
        if (state.isLoading && state.health == null) {
            // Carga inicial
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Espacio superior
                item { Spacer(Modifier.height(8.dp)) }

                // Error si hay
                state.error?.let { error ->
                    item { ErrorCard(error) }
                }

                // Card de salud
                state.health?.let { health ->
                    item { HealthCard(health, gatewayVersion) }
                }

                // Canales
                val channels = state.health?.channels ?: emptyList()
                if (channels.isNotEmpty()) {
                    item {
                        SectionTitle("Canales")
                    }
                    items(channels, key = { it.name }) { channel ->
                        ChannelCard(channel)
                    }
                }

                // Resumen crons
                if (state.cronCount > 0) {
                    item { CronSummaryCard(state.cronCount) }
                }

                // Costos / tokens
                state.costSummary?.let { costs ->
                    item { CostSummaryCard(costs) }
                }

                // Agentes
                if (state.agents.isNotEmpty()) {
                    item {
                        SectionTitle("Agentes (${state.agents.size})")
                    }
                    items(state.agents, key = { it.id }) { agent ->
                        AgentCard(
                            agent = agent,
                            onClick = { onAgentTap(agent.id, agent.displayName) }
                        )
                    }
                }

                // Espacio inferior
                item { Spacer(Modifier.height(8.dp)) }
            }
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(top = 4.dp)
    )
}

@Composable
private fun HealthCard(health: HealthInfo, gatewayVersion: String? = null) {
    val statusColor = if (health.ok) Color(0xFF4CAF50) else Color(0xFFF44336)
    // Mostrar versión del gateway junto al estado si está disponible
    val statusText = if (health.ok) {
        if (gatewayVersion != null) "Online \u00b7 v$gatewayVersion" else "Online"
    } else {
        "Offline"
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Indicador de estado
            Surface(
                shape = CircleShape,
                color = statusColor,
                modifier = Modifier.size(12.dp)
            ) {}

            Spacer(Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Gateway $statusText",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = formatUptime(health.uptimeMs),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Icon(
                imageVector = if (health.ok) Icons.Default.CheckCircle else Icons.Default.Error,
                contentDescription = statusText,
                tint = statusColor,
                modifier = Modifier.size(28.dp)
            )
        }
    }
}

@Composable
private fun ChannelCard(channel: ChannelInfo) {
    val statusColor = when {
        channel.running -> Color(0xFF4CAF50)
        channel.configured -> Color(0xFFFFC107)
        else -> Color(0xFF9E9E9E)
    }
    val statusLabel = when {
        channel.running -> "Activo"
        channel.configured -> "Configurado"
        else -> "Deshabilitado"
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = CircleShape,
                color = statusColor,
                modifier = Modifier.size(10.dp)
            ) {}

            Spacer(Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = channel.name.replaceFirstChar { it.uppercase() },
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium
                )
                if (channel.botUsername != null) {
                    Text(
                        text = "@${channel.botUsername}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Text(
                text = statusLabel,
                style = MaterialTheme.typography.labelMedium,
                color = statusColor
            )
        }
    }
}

@Composable
private fun CronSummaryCard(count: Int) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.Schedule,
                contentDescription = "Crons",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(24.dp)
            )
            Spacer(Modifier.width(12.dp))
            Text(
                text = "$count cron jobs programados",
                style = MaterialTheme.typography.bodyLarge
            )
        }
    }
}

@Composable
private fun AgentCard(agent: AgentInfo, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Emoji del agente
            if (agent.emoji.isNotEmpty()) {
                Text(
                    text = agent.emoji,
                    style = MaterialTheme.typography.headlineSmall
                )
            } else {
                Icon(
                    imageVector = Icons.Default.SmartToy,
                    contentDescription = null,
                    modifier = Modifier.size(28.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
            }

            Spacer(Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = agent.displayName,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (agent.model.isNotEmpty()) {
                    Text(
                        text = agent.model,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                if (agent.fallbacks.isNotEmpty()) {
                    Text(
                        text = "Fallbacks: ${agent.fallbacks.joinToString(", ")}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

@Composable
private fun CostSummaryCard(costs: CostSummary) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.MonetizationOn,
                    contentDescription = "Costos",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Costos",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                Text(
                    text = formatCostUsd(costs.totalCostUsd),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            Spacer(Modifier.height(4.dp))

            Text(
                text = formatTokens(costs.totalTokens),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 36.dp)
            )

            // Top 3 agentes por costo
            val topAgents = costs.perAgent.take(3)
            if (topAgents.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                topAgents.forEach { agent ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 36.dp, top = 2.dp, bottom = 2.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = agent.agentName,
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = formatCostUsd(agent.costUsd),
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }
        }
    }
}

/** Formatea un monto en USD (ej. "$12.45") */
private fun formatCostUsd(usd: Double): String {
    return "$${String.format("%.2f", usd)}"
}

/** Formatea tokens de forma legible (ej. "1.2M tokens", "450K tokens") */
private fun formatTokens(tokens: Long): String {
    return when {
        tokens >= 1_000_000 -> String.format("%.1fM tokens", tokens / 1_000_000.0)
        tokens >= 1_000 -> String.format("%.1fK tokens", tokens / 1_000.0)
        else -> "$tokens tokens"
    }
}

@Composable
private fun ErrorCard(message: String) {
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
            Icon(
                imageVector = Icons.Default.Error,
                contentDescription = "Error",
                tint = MaterialTheme.colorScheme.onErrorContainer
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onErrorContainer
            )
        }
    }
}

/**
 * Formatea milisegundos de uptime a texto legible.
 */
private fun formatUptime(ms: Long): String {
    if (ms <= 0) return "Uptime desconocido"
    val seconds = ms / 1000
    val minutes = seconds / 60
    val hours = minutes / 60
    val days = hours / 24

    return when {
        days > 0 -> "Uptime: ${days}d ${hours % 24}h ${minutes % 60}m"
        hours > 0 -> "Uptime: ${hours}h ${minutes % 60}m"
        minutes > 0 -> "Uptime: ${minutes}m"
        else -> "Uptime: ${seconds}s"
    }
}
