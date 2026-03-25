package com.tenaza.domain.model

/**
 * Modelos del dominio de chat.
 */

/** Mensaje individual en una conversación. */
data class ChatMessage(
    val id: String,
    val role: String, // "user" o "assistant"
    val text: String,
    val timestamp: Long = System.currentTimeMillis(),
    val isStreaming: Boolean = false // true mientras el asistente sigue generando
)

/** Sesión de chat activa con un agente. */
data class ChatSession(
    val agentId: String,
    val agentName: String,
    val sessionKey: String? = null
)
