package com.clawpilot.data.remote.ws

/**
 * Política de reconexión con backoff exponencial + jitter.
 * Evita el "thundering herd" cuando muchos clientes se reconectan al mismo tiempo.
 *
 * Secuencia de delays (sin jitter):
 *   attempt 1 → 1s, 2 → 2s, 3 → 4s, 4 → 8s, 5+ → 16s (cap: 30s)
 */
object ReconnectPolicy {
    private const val BASE_DELAY_MS = 1_000L
    private const val MAX_DELAY_MS = 30_000L
    private const val JITTER_MS = 500L

    /**
     * Calcula el tiempo de espera antes del intento [attempt] (1-indexed).
     * Limita el exponencial a 2^4 = 16x para no superar MAX_DELAY_MS.
     */
    fun delayFor(attempt: Int): Long {
        val exponential = BASE_DELAY_MS * (1L shl minOf(attempt, 4))
        val jitter = (Math.random() * JITTER_MS).toLong()
        return minOf(exponential, MAX_DELAY_MS) + jitter
    }
}
