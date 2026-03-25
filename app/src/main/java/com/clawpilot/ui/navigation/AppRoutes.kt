package com.clawpilot.ui.navigation

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

/**
 * Jerarquía de rutas type-safe para Navigation 3.
 *
 * Cada objeto implementa [NavKey] (requerido por Navigation 3) y es @Serializable
 * para persistencia del backstack en SavedState.
 */
@Serializable
sealed interface AppRoute : NavKey {
    @Serializable data object Chat : AppRoute
    @Serializable data object Dashboard : AppRoute
    @Serializable data object Crons : AppRoute
    @Serializable data object Settings : AppRoute
    @Serializable data object Pairing : AppRoute
    @Serializable data object ManualUrl : AppRoute
    @Serializable data class AgentDetail(val agentId: String, val agentName: String) : AppRoute
}
