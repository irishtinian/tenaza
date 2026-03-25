package com.tenaza

import android.os.Bundle
import android.os.SystemClock
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.fragment.app.FragmentActivity
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tenaza.data.local.prefs.AppPreferences
import com.tenaza.ui.navigation.AppNavHost
import com.tenaza.ui.theme.TenazaTheme
import kotlinx.coroutines.flow.first
import org.koin.android.ext.android.inject

/**
 * Unica Activity de Tenaza.
 * Edge-to-edge habilitado. Compose gestiona todo el contenido visual.
 * Lee la preferencia de tema para aplicar dark/light/system.
 * Bloqueo biometrico opcional — se re-activa tras 30s en background.
 */
class MainActivity : FragmentActivity() {

    private val appPreferences: AppPreferences by inject()

    /** Indica si la app esta desbloqueada (biometrico aprobado o desactivado) */
    private var isUnlocked by mutableStateOf(false)

    /** true una vez que la primera lectura de preferencia se completo */
    private var biometricCheckDone by mutableStateOf(false)

    /** Timestamp (elapsedRealtime) de cuando la app fue a background */
    private var backgroundTimestamp: Long = 0L

    /** Umbral en ms para re-bloquear la app al volver del background */
    private val lockTimeoutMs = 30_000L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            // Leer preferencia de tema de forma reactiva
            val themeMode by appPreferences.getThemeMode()
                .collectAsStateWithLifecycle(initialValue = "system")

            // Leer preferencia de biometria de forma reactiva
            val biometricEnabled by appPreferences.getBiometricEnabled()
                .collectAsStateWithLifecycle(initialValue = false)

            // Al arrancar, determinar si hay que pedir biometria o desbloquear directo
            LaunchedEffect(Unit) {
                val enabled = appPreferences.getBiometricEnabled().first()
                if (enabled) {
                    // Arranque con biometria activada — mantener bloqueado y mostrar prompt
                    isUnlocked = false
                    biometricCheckDone = true
                    showBiometricPrompt()
                } else {
                    // Sin biometria — desbloquear directo
                    isUnlocked = true
                    biometricCheckDone = true
                }
            }

            val isDark = when (themeMode) {
                "dark" -> true
                "light" -> false
                else -> isSystemInDarkTheme()
            }

            TenazaTheme(darkTheme = isDark) {
                when {
                    // Todavia no sabemos si hay que bloquear — pantalla vacia (flash minimo)
                    !biometricCheckDone -> {
                        Surface(
                            modifier = Modifier.fillMaxSize(),
                            color = MaterialTheme.colorScheme.background
                        ) {}
                    }
                    // Biometria activa y no desbloqueado — pantalla de bloqueo
                    biometricEnabled && !isUnlocked -> {
                        LockScreen(onUnlockClick = { showBiometricPrompt() })
                    }
                    // Desbloqueado o biometria desactivada — app normal
                    else -> {
                        AppNavHost()
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // Verificar si hay que re-bloquear tras volver del background
        if (backgroundTimestamp > 0L) {
            val elapsed = SystemClock.elapsedRealtime() - backgroundTimestamp
            if (elapsed >= lockTimeoutMs) {
                // Re-bloquear — el compose reacciona al cambio de isUnlocked
                isUnlocked = false
            }
            backgroundTimestamp = 0L
        }
    }

    override fun onStop() {
        super.onStop()
        backgroundTimestamp = SystemClock.elapsedRealtime()
    }

    /**
     * Muestra el prompt biometrico del sistema.
     * Permite BIOMETRIC_STRONG y DEVICE_CREDENTIAL (PIN/patron) como fallback.
     */
    fun showBiometricPrompt() {
        val executor = ContextCompat.getMainExecutor(this)

        val callback = object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                isUnlocked = true
            }

            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                // El usuario cancelo o hubo error — no desbloqueamos
            }

            override fun onAuthenticationFailed() {
                // Intento fallido pero el sistema permite reintentar automaticamente
            }
        }

        val biometricPrompt = BiometricPrompt(this, executor, callback)

        // Cuando se usa DEVICE_CREDENTIAL, no se necesita negativeButtonText
        val promptInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle("Tenaza")
            .setSubtitle("Authenticate to access your agents")
            .setAllowedAuthenticators(
                BiometricManager.Authenticators.BIOMETRIC_STRONG
                    or BiometricManager.Authenticators.DEVICE_CREDENTIAL
            )
            .build()

        biometricPrompt.authenticate(promptInfo)
    }

    /**
     * Verifica si el dispositivo soporta autenticacion biometrica o credencial de dispositivo.
     * @return true si al menos un metodo esta disponible.
     */
    fun canAuthenticateBiometric(): Boolean {
        val biometricManager = BiometricManager.from(this)
        val result = biometricManager.canAuthenticate(
            BiometricManager.Authenticators.BIOMETRIC_STRONG
                or BiometricManager.Authenticators.DEVICE_CREDENTIAL
        )
        return result == BiometricManager.BIOMETRIC_SUCCESS
    }
}

/**
 * Pantalla de bloqueo con fondo oscuro y boton para desbloquear.
 */
@androidx.compose.runtime.Composable
private fun LockScreen(onUnlockClick: () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = "Tenaza",
                style = MaterialTheme.typography.headlineLarge,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(Modifier.height(16.dp))
            Text(
                text = "Unlock to continue",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(32.dp))
            Button(onClick = onUnlockClick) {
                Text("Unlock")
            }
        }
    }
}
