package com.clawpilot.domain.model

/**
 * Modelos del dominio de Arena (chat grupal multi-agente).
 */

/** Mensaje en la línea de tiempo compartida del Arena. */
data class ArenaMessage(
    val id: String,
    val agentId: String?, // null para mensajes del usuario
    val agentName: String,
    val agentEmoji: String,
    val role: String, // "user" o "assistant"
    val text: String,
    val timestamp: Long = System.currentTimeMillis(),
    val isStreaming: Boolean = false,
    val targetAgents: List<String> = emptyList() // nombres de agentes mencionados (solo para mensajes de usuario)
)

/** Participante activo en el Arena. */
data class ArenaParticipant(
    val agentId: String,
    val agentName: String,
    val emoji: String,
    val sessionKey: String? = null // se establece al crear la sesión
)
