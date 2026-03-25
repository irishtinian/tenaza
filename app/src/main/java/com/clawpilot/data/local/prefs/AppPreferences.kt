package com.clawpilot.data.local.prefs

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

// DataStore para preferencias no sensibles de la app
private val Context.appDataStore by preferencesDataStore(name = "app_prefs")

/**
 * Preferencias no sensibles de la aplicación.
 * A diferencia de CredentialStore, estos datos no se cifran (no contienen secretos).
 */
class AppPreferences(private val context: Context) {

    companion object {
        val KEY_THEME_MODE = stringPreferencesKey("theme_mode")
        val KEY_LAST_GATEWAY_URL = stringPreferencesKey("last_gateway_url")
    }

    /**
     * Flujo reactivo del modo de tema ("system", "light", "dark").
     * Por defecto usa "system" para seguir la preferencia del SO.
     */
    fun getThemeMode(): Flow<String> =
        context.appDataStore.data.map { prefs ->
            prefs[KEY_THEME_MODE] ?: "system"
        }

    /**
     * Persiste el modo de tema seleccionado por el usuario.
     */
    suspend fun setThemeMode(mode: String) {
        context.appDataStore.edit { prefs ->
            prefs[KEY_THEME_MODE] = mode
        }
    }

    /**
     * Flujo reactivo de la última URL de gateway usada.
     * Se usa para pre-rellenar el campo de entrada manual de URL.
     */
    fun getLastGatewayUrl(): Flow<String?> =
        context.appDataStore.data.map { prefs ->
            prefs[KEY_LAST_GATEWAY_URL]
        }

    /**
     * Guarda la última URL de gateway para sugerirla en futuros intentos de conexión.
     */
    suspend fun setLastGatewayUrl(url: String) {
        context.appDataStore.edit { prefs ->
            prefs[KEY_LAST_GATEWAY_URL] = url
        }
    }
}
