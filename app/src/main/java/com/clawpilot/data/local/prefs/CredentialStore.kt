package com.clawpilot.data.local.prefs

import android.content.Context
import android.util.Base64
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.clawpilot.domain.model.GatewayCredentials
import com.google.crypto.tink.Aead
import com.google.crypto.tink.KeyTemplates
import com.google.crypto.tink.aead.AeadConfig
import com.google.crypto.tink.integration.android.AndroidKeysetManager
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

// DataStore con nombre "credentials" — una sola instancia por contexto (extension property nivel top-level)
private val Context.credentialDataStore by preferencesDataStore(name = "credentials")

/**
 * Almacenamiento cifrado de credenciales del gateway.
 * El token y la URL se cifran con AES-256-GCM via Tink antes de escribir en DataStore.
 * La clave maestra de Tink se almacena en Android Keystore.
 */
class CredentialStore(private val context: Context) {

    companion object {
        val KEY_TOKEN = stringPreferencesKey("gateway_token")
        val KEY_URL = stringPreferencesKey("gateway_url")
        val KEY_DEVICE_NAME = stringPreferencesKey("device_name")
        val KEY_SCOPES = stringPreferencesKey("scopes")
        val KEY_PAIRED_AT = longPreferencesKey("paired_at")

        private const val TINK_KEYSET_NAME = "clawpilot_tink_keyset"
        private const val TINK_PREFS_NAME = "clawpilot_tink_prefs"
        private const val TINK_MASTER_KEY_URI = "android-keystore://clawpilot_tink_master"
    }

    // Inicializar Tink AeadConfig al crear la instancia
    init {
        AeadConfig.register()
    }

    // Primitiva Aead respaldada por Android Keystore (lazy para no bloquear el constructor)
    private val aead: Aead by lazy {
        AndroidKeysetManager.Builder()
            .withSharedPref(context, TINK_KEYSET_NAME, TINK_PREFS_NAME)
            .withKeyTemplate(KeyTemplates.get("AES256_GCM"))
            .withMasterKeyUri(TINK_MASTER_KEY_URI)
            .build()
            .keysetHandle
            .getPrimitive(Aead::class.java)
    }

    /**
     * Almacena las credenciales del gateway de forma cifrada.
     * El token y la URL se cifran; el resto se guarda en texto plano (no sensible).
     */
    suspend fun storeCredentials(credentials: GatewayCredentials) {
        context.credentialDataStore.edit { prefs ->
            prefs[KEY_TOKEN] = encrypt(credentials.token)
            prefs[KEY_URL] = encrypt(credentials.gatewayUrl)
            prefs[KEY_DEVICE_NAME] = credentials.deviceName
            prefs[KEY_SCOPES] = credentials.scopes.joinToString(",")
            prefs[KEY_PAIRED_AT] = credentials.pairedAt
        }
    }

    /**
     * Flujo reactivo de credenciales. Emite null si no está emparejado.
     */
    fun getCredentials(): Flow<GatewayCredentials?> =
        context.credentialDataStore.data.map { prefs ->
            val token = prefs[KEY_TOKEN]?.let { decrypt(it) } ?: return@map null
            val url = prefs[KEY_URL]?.let { decrypt(it) } ?: return@map null
            GatewayCredentials(
                gatewayUrl = url,
                token = token,
                deviceName = prefs[KEY_DEVICE_NAME] ?: "",
                scopes = prefs[KEY_SCOPES]?.split(",")?.filter { it.isNotEmpty() } ?: emptyList(),
                pairedAt = prefs[KEY_PAIRED_AT] ?: 0L
            )
        }

    /**
     * Borra todas las credenciales (desemparejamiento del dispositivo).
     */
    suspend fun clearCredentials() {
        context.credentialDataStore.edit { it.clear() }
    }

    /**
     * Flujo reactivo que emite true si el dispositivo está emparejado.
     */
    fun isPaired(): Flow<Boolean> =
        context.credentialDataStore.data.map { it[KEY_TOKEN] != null }

    // --- Helpers privados de cifrado/descifrado ---

    private fun encrypt(value: String): String {
        val encrypted = aead.encrypt(value.toByteArray(Charsets.UTF_8), null)
        return Base64.encodeToString(encrypted, Base64.NO_WRAP)
    }

    private fun decrypt(encrypted: String): String {
        val bytes = Base64.decode(encrypted, Base64.NO_WRAP)
        val decrypted = aead.decrypt(bytes, null)
        return String(decrypted, Charsets.UTF_8)
    }
}
