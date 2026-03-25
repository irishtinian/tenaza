package com.tenaza.ui.arena

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.InputChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tenaza.domain.model.ArenaMessage
import com.tenaza.domain.model.ArenaParticipant
import com.tenaza.ui.dashboard.AgentInfo
import org.koin.compose.viewmodel.koinViewModel

/**
 * Colores sutiles para distinguir visualmente cada agente en el Arena.
 */
private val agentTints = listOf(
    Color(0xFF1E88E5).copy(alpha = 0.12f), // Azul
    Color(0xFF43A047).copy(alpha = 0.12f), // Verde
    Color(0xFFE53935).copy(alpha = 0.12f), // Rojo
    Color(0xFFFB8C00).copy(alpha = 0.12f), // Naranja
    Color(0xFF8E24AA).copy(alpha = 0.12f), // Púrpura
    Color(0xFF00ACC1).copy(alpha = 0.12f), // Cian
)

private val agentLabelColors = listOf(
    Color(0xFF1E88E5), // Azul
    Color(0xFF43A047), // Verde
    Color(0xFFE53935), // Rojo
    Color(0xFFFB8C00), // Naranja
    Color(0xFF8E24AA), // Púrpura
    Color(0xFF00ACC1), // Cian
)

/**
 * Pantalla principal del Arena (chat grupal multi-agente).
 * Tiene dos modos: setup (seleccionar agentes) y chat (conversación).
 */
@Composable
fun ArenaScreen(
    onBack: () -> Unit,
    viewModel: ArenaViewModel = koinViewModel()
) {
    val isSettingUp by viewModel.isSettingUp.collectAsStateWithLifecycle()
    val participants by viewModel.participants.collectAsStateWithLifecycle()
    val messages by viewModel.messages.collectAsStateWithLifecycle()
    val isGenerating by viewModel.isGenerating.collectAsStateWithLifecycle()
    val availableAgents by viewModel.availableAgents.collectAsStateWithLifecycle()

    if (isSettingUp) {
        ArenaSetupScreen(
            availableAgents = availableAgents,
            participants = participants,
            onAddAgent = viewModel::addAgent,
            onRemoveAgent = viewModel::removeAgent,
            onStart = viewModel::startArena,
            onBack = onBack
        )
    } else {
        ArenaChatScreen(
            participants = participants,
            messages = messages,
            isGenerating = isGenerating,
            onSend = viewModel::sendMessage,
            onAbort = viewModel::abort,
            onBack = viewModel::backToSetup
        )
    }
}

// =====================================================================
// Modo Setup: selección de agentes
// =====================================================================

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun ArenaSetupScreen(
    availableAgents: List<AgentInfo>,
    participants: List<ArenaParticipant>,
    onAddAgent: (AgentInfo) -> Unit,
    onRemoveAgent: (String) -> Unit,
    onStart: () -> Unit,
    onBack: () -> Unit
) {
    val selectedIds = participants.map { it.agentId }.toSet()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Crear Arena") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Volver"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp)
        ) {
            Text(
                text = "Selecciona agentes para invitar",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Chips de agentes seleccionados
            if (participants.isNotEmpty()) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    participants.forEachIndexed { index, participant ->
                        InputChip(
                            selected = true,
                            onClick = { onRemoveAgent(participant.agentId) },
                            label = {
                                Text("${participant.emoji} ${participant.agentName}")
                            },
                            trailingIcon = {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Quitar",
                                    modifier = Modifier.size(16.dp)
                                )
                            },
                            colors = InputChipDefaults.inputChipColors(
                                selectedContainerColor = agentTints.getOrElse(index) {
                                    agentTints[0]
                                }
                            )
                        )
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
            }

            // Lista de agentes disponibles
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(availableAgents, key = { it.id }) { agent ->
                    val isSelected = agent.id in selectedIds
                    AgentSelectionRow(
                        agent = agent,
                        isSelected = isSelected,
                        enabled = isSelected || participants.size < 6,
                        onToggle = {
                            if (isSelected) {
                                onRemoveAgent(agent.id)
                            } else {
                                onAddAgent(agent)
                            }
                        }
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Botón de iniciar
            Button(
                onClick = onStart,
                enabled = participants.size >= 2,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(
                    imageVector = Icons.Default.Groups,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = if (participants.size < 2)
                        "Selecciona al menos 2 agentes"
                    else
                        "Iniciar Arena (${participants.size} agentes)"
                )
            }
        }
    }
}

/** Fila de agente con checkbox para seleccionar/deseleccionar */
@Composable
private fun AgentSelectionRow(
    agent: AgentInfo,
    isSelected: Boolean,
    enabled: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable(enabled = enabled, onClick = onToggle),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected)
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
            else
                MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Checkbox(
                checked = isSelected,
                onCheckedChange = { onToggle() },
                enabled = enabled
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = agent.emoji.ifBlank { "\uD83E\uDD16" },
                style = MaterialTheme.typography.headlineSmall
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = agent.displayName,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (agent.model.isNotBlank()) {
                    Text(
                        text = agent.model,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

// =====================================================================
// Modo Chat: conversación grupal
// =====================================================================

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ArenaChatScreen(
    participants: List<ArenaParticipant>,
    messages: List<ArenaMessage>,
    isGenerating: Boolean,
    onSend: (String) -> Unit,
    onAbort: () -> Unit,
    onBack: () -> Unit
) {
    // Mapa de agentId -> índice de color
    val colorMap = remember(participants) {
        participants.mapIndexed { index, p -> p.agentId to index }.toMap()
    }

    val listState = rememberLazyListState()

    // Auto-scroll al fondo cuando llegan mensajes o se actualizan
    LaunchedEffect(messages.size, messages.lastOrNull()?.text) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size - 1)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Arena", maxLines = 1)
                        Text(
                            text = "${participants.size} agentes",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Volver a setup"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .imePadding()
        ) {
            // Lista de mensajes
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(messages, key = { it.id }) { message ->
                    ArenaMessageBubble(
                        message = message,
                        colorIndex = message.agentId?.let { colorMap[it] } ?: -1
                    )
                }
            }

            // Barra de entrada
            ArenaInputBar(
                isGenerating = isGenerating,
                onSend = onSend,
                onAbort = onAbort
            )
        }
    }
}

/**
 * Burbuja de mensaje del Arena.
 * - Mensajes del usuario: alineados a la derecha, primaryContainer.
 * - Mensajes de agentes: alineados a la izquierda, con etiqueta de color y nombre.
 */
@Composable
private fun ArenaMessageBubble(
    message: ArenaMessage,
    colorIndex: Int,
    modifier: Modifier = Modifier
) {
    val isUser = message.role == "user"
    val screenWidth = LocalConfiguration.current.screenWidthDp.dp

    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start
    ) {
        if (isUser) {
            // Mensaje del usuario
            Column(
                modifier = Modifier.widthIn(max = screenWidth * 0.80f),
                horizontalAlignment = Alignment.End
            ) {
                Box(
                    modifier = Modifier
                        .clip(
                            RoundedCornerShape(
                                topStart = 16.dp,
                                topEnd = 16.dp,
                                bottomStart = 16.dp,
                                bottomEnd = 4.dp
                            )
                        )
                        .background(MaterialTheme.colorScheme.primaryContainer)
                        .padding(12.dp)
                ) {
                    Text(
                        text = message.text,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
                // Etiqueta de agentes destinatarios si es una mención dirigida
                if (message.targetAgents.isNotEmpty()) {
                    Text(
                        text = "\u2192 ${message.targetAgents.joinToString(", ") { "@$it" }}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 2.dp, end = 4.dp)
                    )
                }
            }
        } else {
            // Mensaje de agente con etiqueta
            val tint = agentTints.getOrElse(colorIndex.coerceAtLeast(0)) { agentTints[0] }
            val labelColor = agentLabelColors.getOrElse(colorIndex.coerceAtLeast(0)) { agentLabelColors[0] }

            Column(
                modifier = Modifier.widthIn(max = screenWidth * 0.85f)
            ) {
                // Etiqueta del agente
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(start = 4.dp, bottom = 2.dp)
                ) {
                    Text(
                        text = message.agentEmoji,
                        style = MaterialTheme.typography.bodySmall
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = message.agentName,
                        style = MaterialTheme.typography.labelMedium,
                        color = labelColor,
                        fontWeight = FontWeight.Bold
                    )
                }

                // Burbuja del mensaje
                Box(
                    modifier = Modifier
                        .clip(
                            RoundedCornerShape(
                                topStart = 16.dp,
                                topEnd = 16.dp,
                                bottomStart = 4.dp,
                                bottomEnd = 16.dp
                            )
                        )
                        .background(tint)
                        .padding(12.dp)
                ) {
                    Column {
                        if (message.text.isNotBlank()) {
                            Text(
                                text = message.text,
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                        // Indicador de streaming
                        if (message.isStreaming) {
                            Spacer(modifier = Modifier.height(4.dp))
                            ArenaStreamingIndicator()
                        }
                    }
                }
            }
        }
    }
}

/** Indicador animado de streaming (3 puntos pulsantes) */
@Composable
private fun ArenaStreamingIndicator(modifier: Modifier = Modifier) {
    val infiniteTransition = rememberInfiniteTransition(label = "arena_streaming")

    Row(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        repeat(3) { index ->
            val alpha by infiniteTransition.animateFloat(
                initialValue = 0.3f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(
                    animation = tween(
                        durationMillis = 600,
                        delayMillis = index * 200,
                        easing = LinearEasing
                    ),
                    repeatMode = RepeatMode.Reverse
                ),
                label = "arena_dot_$index"
            )
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .alpha(alpha)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.onSurfaceVariant)
            )
        }
    }
}

/** Barra de entrada de texto con botón de enviar/abortar */
@Composable
private fun ArenaInputBar(
    isGenerating: Boolean,
    onSend: (String) -> Unit,
    onAbort: () -> Unit,
    modifier: Modifier = Modifier
) {
    var text by remember { mutableStateOf("") }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        OutlinedTextField(
            value = text,
            onValueChange = { text = it },
            modifier = Modifier.weight(1f),
            placeholder = {
                Text("Mensaje al Arena...")
            },
            maxLines = 4,
            shape = RoundedCornerShape(24.dp)
        )
        Spacer(modifier = Modifier.width(8.dp))

        if (isGenerating) {
            IconButton(onClick = onAbort) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Abortar todo",
                    tint = MaterialTheme.colorScheme.error
                )
            }
        } else {
            IconButton(
                onClick = {
                    if (text.isNotBlank()) {
                        onSend(text)
                        text = ""
                    }
                },
                enabled = text.isNotBlank()
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.Send,
                    contentDescription = "Enviar a todos",
                    tint = if (text.isNotBlank()) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
