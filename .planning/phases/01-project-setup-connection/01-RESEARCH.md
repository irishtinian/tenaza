# Phase 1: Project Setup & Connection - Research

**Researched:** 2026-03-25
**Domain:** Android project scaffold + secure WebSocket pairing with OpenClaw Gateway
**Confidence:** HIGH

---

<phase_requirements>
## Phase Requirements

| ID | Description | Research Support |
|----|-------------|------------------|
| CONN-01 | User can pair device with gateway by scanning QR code | CameraX + ML Kit section; pairing flow section |
| CONN-02 | Pairing uses ECDSA keypair generated on device, stored in Android Keystore | Android Keystore ECDSA section; keypair code example |
| CONN-03 | App stores gateway URL + scoped token securely (DataStore + Tink) | DataStore + Tink section; encrypted storage pattern |
| CONN-04 | App shows connection status at all times | ConnectionManager StateFlow section; UI indicator pattern |
| CONN-05 | App auto-reconnects with exponential backoff when connection drops | Reconnection strategy section; ExponentialBackoff code |
| CONN-06 | User can manually enter gateway URL as fallback to QR scanning | Manual URL entry UI section; validation pattern |
| CONN-07 | User can unpair/disconnect from gateway | Unpair flow section; credential clearing pattern |
| SECR-01 | All gateway communication uses TLS (wss://) | TLS enforcement section; URL validator pattern |
| SECR-02 | Auth token stored encrypted (DataStore + Tink) | Same as CONN-03 |
| SECR-03 | Only one device can be paired per gateway (enforced app-side) | Pairing state machine section; single-device lock |
</phase_requirements>

---

## Summary

Phase 1 is the foundation for the entire app. It establishes the Android project structure, the secure connection layer to the OpenClaw Gateway, and the pairing flow that exchanges ECDSA public keys for a scoped auth token. Every subsequent phase builds on this WebSocket connection manager and credential store.

The OpenClaw Gateway speaks a JSON-RPC-style WebSocket protocol. The pairing flow is ECDSA-based: the app sends a `connect.challenge` frame with its public key, the gateway owner approves, and the gateway issues a long-lived scoped token. That token is stored encrypted via DataStore + Tink (backed by Android Keystore), and used for all future WebSocket connections as a query param (`ws://host:18789/?token=<token>`).

The most critical architectural decision for this phase is the WebSocket connection manager. It must handle reconnection (exponential backoff with jitter), silent disconnects (application-level heartbeat), network changes (ConnectivityManager callbacks), and expose a clean `StateFlow<ConnectionState>` that every screen in the app can observe.

**Primary recommendation:** Scaffold with Kotlin 2.2, Compose BOM 2026.03, Navigation 3 for the bottom-tab shell. Use OkHttp directly (not Ktor) for WebSocket — it is battle-tested on Android and simpler to wrap in a ~200-line coroutines manager. Use DataStore + Tink for all credential storage from day one.

---

## Standard Stack

### Core

| Library | Version | Purpose | Why Standard |
|---------|---------|---------|--------------|
| Kotlin | 2.2.0 | Primary language | Latest stable; guard conditions promoted to Stable; excellent coroutines |
| AGP | 9.1.0 | Android Gradle Plugin | March 2026 release; supports API 36.1 |
| Gradle | 8.13 | Build system | Required by AGP 9.1; version catalogs standard |
| KSP | 2.2.0-1.0.29 | Symbol processing | Required by Room 3.0; KAPT is deprecated |
| Compose BOM | 2026.03.00 | UI toolkit | Current stable; maps to Compose 1.10.x |
| Material 3 | via BOM | Design system | Standard M3 with dynamic color |
| Navigation 3 | 1.0.1 | Screen navigation | Compose-native; backstack is a plain SnapshotStateList |

### Supporting

| Library | Version | Purpose | When to Use |
|---------|---------|---------|-------------|
| OkHttp | 4.12.x (transitive) | WebSocket transport | Core of the WebSocket manager |
| DataStore Preferences | 1.2.1 | Key-value preferences | Theme, notification settings, last URL |
| Tink Android | 1.16.0 | AES-GCM encryption | Encrypting token + URL before writing to DataStore |
| Koin Android | 4.1.1 | Dependency injection | DSL-based DI, no annotation processing |
| koin-androidx-compose | 4.1.1 | Compose + ViewModel DI | `koinViewModel()` in composables |
| CameraX Core | 1.5.0 | Camera abstraction | Lifecycle-aware camera for QR scanner |
| CameraX Camera2 | 1.5.0 | Camera2 backend | Required by CameraX |
| CameraX Lifecycle | 1.5.0 | Lifecycle integration | Binds camera to composable lifecycle |
| CameraX View | 1.5.0 | PreviewView composable | Camera preview in Compose |
| ML Kit Barcode (bundled) | 17.3.0 | QR code detection | On-device, no network; use bundled not unbundled |

### Alternatives Considered

| Instead of | Could Use | Tradeoff |
|------------|-----------|----------|
| OkHttp WebSocket | Ktor WebSocket | Ktor adds ~2MB, more complex setup; OkHttp already a transitive dep from many libs |
| OkHttp WebSocket | Scarlet | Scarlet stuck at 0.1.12, unmaintained — do not use |
| Koin | Hilt | Hilt requires KSP code generation, more ceremony; Koin sufficient for this scope |
| Navigation 3 | Compose Navigation (legacy) | Legacy nav is XML-era mental model wrapped in Compose; Nav3 is Compose-native |
| DataStore + Tink | EncryptedSharedPreferences | EncryptedSharedPrefs is deprecated as of late 2025 |
| ML Kit (bundled) | ZXing | ZXing is old Java library; ML Kit is faster and officially supported |

**Installation:**
```toml
# gradle/libs.versions.toml

[versions]
kotlin = "2.2.0"
agp = "9.1.0"
compose-bom = "2026.03.00"
navigation3 = "1.0.1"
koin = "4.1.1"
camerax = "1.5.0"
mlkit-barcode = "17.3.0"
datastore = "1.2.1"
tink = "1.16.0"
ksp = "2.2.0-1.0.29"

[libraries]
compose-bom = { group = "androidx.compose", name = "compose-bom", version.ref = "compose-bom" }
compose-material3 = { group = "androidx.compose.material3", name = "material3" }
compose-ui = { group = "androidx.compose.ui", name = "ui" }
compose-ui-tooling = { group = "androidx.compose.ui", name = "ui-tooling" }
compose-ui-tooling-preview = { group = "androidx.compose.ui", name = "ui-tooling-preview" }

navigation3-runtime = { group = "androidx.navigation3", name = "navigation3-runtime", version.ref = "navigation3" }
navigation3-ui = { group = "androidx.navigation3", name = "navigation3-ui", version.ref = "navigation3" }

koin-android = { group = "io.insert-koin", name = "koin-android", version.ref = "koin" }
koin-compose = { group = "io.insert-koin", name = "koin-androidx-compose", version.ref = "koin" }
koin-compose-navigation = { group = "io.insert-koin", name = "koin-androidx-compose-navigation", version.ref = "koin" }

camerax-core = { group = "androidx.camera", name = "camera-core", version.ref = "camerax" }
camerax-camera2 = { group = "androidx.camera", name = "camera-camera2", version.ref = "camerax" }
camerax-lifecycle = { group = "androidx.camera", name = "camera-lifecycle", version.ref = "camerax" }
camerax-view = { group = "androidx.camera", name = "camera-view", version.ref = "camerax" }
mlkit-barcode = { group = "com.google.mlkit", name = "barcode-scanning", version.ref = "mlkit-barcode" }

datastore-preferences = { group = "androidx.datastore", name = "datastore-preferences", version.ref = "datastore" }
tink = { group = "com.google.crypto.tink", name = "tink-android", version.ref = "tink" }

[plugins]
android-application = { id = "com.android.application", version.ref = "agp" }
kotlin-android = { id = "org.jetbrains.kotlin.android", version.ref = "kotlin" }
kotlin-compose = { id = "org.jetbrains.kotlin.plugin.compose", version.ref = "kotlin" }
kotlin-serialization = { id = "org.jetbrains.kotlin.plugin.serialization", version.ref = "kotlin" }
ksp = { id = "com.google.devtools.ksp", version.ref = "ksp" }
```

**Version verification note:** Versions above are confirmed from STACK.md research (2026-03-25). KSP version `2.2.0-1.0.29` — verify exact patch suffix against https://github.com/google/ksp/releases before pinning.

---

## Architecture Patterns

### Recommended Project Structure

```
app/src/main/java/com/clawpilot/
├── di/
│   ├── NetworkModule.kt        # OkHttpClient, WebSocketManager
│   ├── StorageModule.kt        # DataStore, Tink key, CredentialStore
│   └── AppModule.kt            # ViewModels, repositories
├── data/
│   ├── remote/
│   │   └── ws/
│   │       ├── WebSocketManager.kt      # Core WS manager singleton
│   │       ├── GatewayFrame.kt          # Sealed classes for frames
│   │       └── ConnectionState.kt       # Sealed class for connection states
│   ├── local/
│   │   └── prefs/
│   │       ├── CredentialStore.kt       # DataStore + Tink wrapper
│   │       └── AppPreferences.kt        # Non-sensitive settings
│   └── repository/
│       └── ConnectionRepository.kt     # Orchestrates pairing + reconnect
├── domain/
│   └── model/
│       ├── GatewayCredentials.kt       # URL + token + device metadata
│       └── PairingState.kt             # States during pairing flow
├── ui/
│   ├── navigation/
│   │   ├── AppRoutes.kt                # Sealed route hierarchy
│   │   └── AppNavHost.kt               # NavDisplay setup
│   ├── pairing/
│   │   ├── PairingViewModel.kt
│   │   ├── QrScanScreen.kt             # CameraX + ML Kit
│   │   └── ManualUrlScreen.kt          # Fallback manual entry
│   ├── connection/
│   │   ├── ConnectionViewModel.kt      # Singleton-scoped, all screens
│   │   └── ConnectionStatusBar.kt      # Shared composable (top bar)
│   ├── shell/
│   │   └── MainShell.kt               # Bottom nav + tab scaffold
│   └── theme/
│       ├── Theme.kt
│       ├── Color.kt
│       └── Typography.kt
├── ClawPilotApp.kt              # Application class, Koin init
└── MainActivity.kt              # Single activity, edge-to-edge
```

### Pattern 1: WebSocket Connection Manager

**What:** A singleton `WebSocketManager` wraps OkHttp's WebSocket API, exposes a `StateFlow<ConnectionState>`, a `SharedFlow<GatewayFrame>` for incoming events, and handles reconnection internally.

**When to use:** Always — every other feature in the app depends on this manager.

```kotlin
// Source: Based on OkHttp WebSocket API + coroutines wrapper pattern
sealed class ConnectionState {
    data object Disconnected : ConnectionState()
    data object Connecting : ConnectionState()
    data object Connected : ConnectionState()
    data class Reconnecting(val attempt: Int, val delayMs: Long) : ConnectionState()
    data class Error(val reason: String) : ConnectionState()
}

class WebSocketManager(
    private val okHttpClient: OkHttpClient,
    private val credentialStore: CredentialStore,
    private val scope: CoroutineScope // SupervisorJob + Dispatchers.IO
) {
    private val _connectionState = MutableStateFlow<ConnectionState>(ConnectionState.Disconnected)
    val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()

    // Incoming gateway frames (events + responses)
    private val _frames = MutableSharedFlow<GatewayFrame>(replay = 0, extraBufferCapacity = 64)
    val frames: SharedFlow<GatewayFrame> = _frames.asSharedFlow()

    private var webSocket: WebSocket? = null
    private var reconnectJob: Job? = null

    fun connect() { /* ... */ }
    fun disconnect() { /* ... */ }
    fun send(frame: RequestFrame) { /* serialize + webSocket.send() */ }
}
```

### Pattern 2: Exponential Backoff with Jitter

**What:** Standard reconnection policy used by all major WebSocket-dependent apps (Slack, Home Assistant, etc.).

**When to use:** Any time the WebSocket closes unexpectedly (not user-initiated).

```kotlin
// Source: Standard algorithm — verified across multiple WebSocket implementations
object ReconnectPolicy {
    private const val BASE_DELAY_MS = 1_000L
    private const val MAX_DELAY_MS = 30_000L
    private const val JITTER_MS = 500L

    fun delayFor(attempt: Int): Long {
        val exponential = BASE_DELAY_MS * (1L shl minOf(attempt, 4)) // cap at 16s base
        val jitter = (Math.random() * JITTER_MS).toLong()
        return minOf(exponential, MAX_DELAY_MS) + jitter
    }
}
// Attempt 0: 1s + jitter
// Attempt 1: 2s + jitter
// Attempt 2: 4s + jitter
// Attempt 3: 8s + jitter
// Attempt 4+: 16s + jitter (cap at 30s effective)
```

### Pattern 3: ECDSA Keypair in Android Keystore

**What:** Generate an ECDSA P-256 keypair stored in the hardware-backed Android Keystore. The private key never leaves the secure enclave. The public key is sent to the gateway during pairing.

**When to use:** First launch, before initiating the pairing flow (CONN-02).

```kotlin
// Source: Android Keystore official documentation
// https://developer.android.com/privacy-and-security/keystore
fun generateEcdsaKeyPair(alias: String = "clawpilot_device_key"): KeyPair {
    val kpg = KeyPairGenerator.getInstance(
        KeyProperties.KEY_ALGORITHM_EC,
        "AndroidKeyStore"
    )
    kpg.initialize(
        KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_SIGN or KeyProperties.PURPOSE_VERIFY)
            .setAlgorithmParameterSpec(ECGenParameterSpec("secp256r1"))
            .setDigests(KeyProperties.DIGEST_SHA256, KeyProperties.DIGEST_SHA512)
            .setUserAuthenticationRequired(false) // No biometric gate for pairing key
            .build()
    )
    return kpg.generateKeyPair()
}

fun getPublicKeyBase64(alias: String = "clawpilot_device_key"): String {
    val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
    val publicKey = ks.getCertificate(alias)?.publicKey
        ?: generateEcdsaKeyPair(alias).public
    return Base64.encodeToString(publicKey.encoded, Base64.NO_WRAP)
}
```

### Pattern 4: DataStore + Tink Credential Store

**What:** Encrypt the gateway URL and auth token with AES-GCM via Tink before writing to DataStore. The Tink keyset is stored in a separate DataStore file, with the master key in Android Keystore.

**When to use:** Any write/read of gateway URL, auth token, or device metadata (CONN-03, SECR-02).

```kotlin
// Source: https://github.com/Jaypatelbond/Android-DataStore-Tink-Migration
// Pattern: Proto DataStore + Tink StreamingAead
class CredentialStore(context: Context) {
    private val masterKeyAlias = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()

    // DataStore for the Tink keyset
    private val keysetDataStore = context.createDataStore("keyset.pb")

    // Tink Aead backed by Android Keystore master key
    private val aead: Aead by lazy {
        AndroidKeysetManager.Builder()
            .withSharedPref(context, "tink_keyset", "tink_keyset_prefs")
            .withKeyTemplate(AeadKeyTemplates.AES256_GCM)
            .withMasterKeyUri("android-keystore://clawpilot_tink_master")
            .build()
            .keysetHandle
            .getPrimitive(Aead::class.java)
    }

    // Preferences DataStore (plain bytes, but values are Tink-encrypted)
    private val dataStore: DataStore<Preferences> = context.createDataStore("credentials")

    suspend fun storeToken(token: String) {
        val encrypted = aead.encrypt(token.toByteArray(), null)
        dataStore.edit { it[PREF_TOKEN] = Base64.encodeToString(encrypted, Base64.NO_WRAP) }
    }

    suspend fun getToken(): String? {
        val encoded = dataStore.data.first()[PREF_TOKEN] ?: return null
        val decrypted = aead.decrypt(Base64.decode(encoded, Base64.NO_WRAP), null)
        return String(decrypted)
    }

    companion object {
        val PREF_TOKEN = stringPreferencesKey("gateway_token")
        val PREF_URL = stringPreferencesKey("gateway_url")
        val PREF_DEVICE_NAME = stringPreferencesKey("device_name")
    }
}
```

### Pattern 5: Navigation 3 Shell with Bottom Tabs

**What:** Navigation 3 gives full ownership of the backstack as a `SnapshotStateList`. For bottom tabs with independent backstacks, each tab gets its own `rememberNavBackStack()`.

**When to use:** App scaffold setup — the bottom nav with Chat, Dashboard, Crons, Settings tabs.

```kotlin
// Source: https://developer.android.com/guide/navigation/navigation-3/recipes/multiple-backstacks
// Navigation 3 stable 1.0.1

@Serializable sealed interface AppRoute {
    // Top-level tabs (no args)
    @Serializable data object Chat : AppRoute
    @Serializable data object Dashboard : AppRoute
    @Serializable data object Crons : AppRoute
    @Serializable data object Settings : AppRoute
    // Pre-auth screens
    @Serializable data object Pairing : AppRoute
    @Serializable data object ManualUrl : AppRoute
}

@Composable
fun MainShell(connectionViewModel: ConnectionViewModel = koinViewModel()) {
    val tabs = listOf(AppRoute.Chat, AppRoute.Dashboard, AppRoute.Crons, AppRoute.Settings)
    var selectedTab by remember { mutableStateOf<AppRoute>(AppRoute.Dashboard) }

    // Separate backstack per tab — state preserved on tab switch
    val backstacks = tabs.associateWith { rememberNavBackStack(it) }
    val currentBackstack = backstacks[selectedTab]!!

    Scaffold(
        bottomBar = {
            NavigationBar {
                tabs.forEach { tab ->
                    NavigationBarItem(
                        selected = selectedTab == tab,
                        onClick = { selectedTab = tab },
                        icon = { /* icon */ },
                        label = { /* label */ }
                    )
                }
            }
        }
    ) { paddingValues ->
        NavDisplay(
            backStack = currentBackstack,
            modifier = Modifier.padding(paddingValues),
            entryDecorators = listOf(rememberSceneSetupNavEntryDecorator()),
            entryProvider = entryProvider {
                entry<AppRoute.Chat> { ChatListScreen() }
                entry<AppRoute.Dashboard> { DashboardScreen() }
                entry<AppRoute.Crons> { CronsScreen() }
                entry<AppRoute.Settings> { SettingsScreen() }
            }
        )
    }
}
```

### Pattern 6: QR Pairing Flow with CameraX + ML Kit

**What:** Use `LifecycleCameraController` with `MlKitAnalyzer` to detect QR codes. Process the raw value (JSON payload with gateway URL + pairing token) and trigger the pairing handshake.

**When to use:** Primary pairing path (CONN-01).

```kotlin
// Source: https://developer.android.com/media/camera/camerax/mlkitanalyzer
@Composable
fun QrScanScreen(onQrDetected: (PairingPayload) -> Unit) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val cameraController = remember {
        LifecycleCameraController(context).also { ctrl ->
            ctrl.bindToLifecycle(lifecycleOwner)
        }
    }
    val barcodeScanner = remember {
        BarcodeScanning.getClient(
            BarcodeScannerOptions.Builder()
                .setBarcodeFormats(Barcode.FORMAT_QR_CODE)
                .build()
        )
    }

    // Set analyzer — fires on each frame
    LaunchedEffect(Unit) {
        cameraController.setImageAnalysisAnalyzer(
            ContextCompat.getMainExecutor(context),
            MlKitAnalyzer(
                listOf(barcodeScanner),
                COORDINATE_SYSTEM_VIEW_REFERENCED,
                ContextCompat.getMainExecutor(context)
            ) { result ->
                result.getValue(barcodeScanner)
                    ?.firstOrNull()
                    ?.rawValue
                    ?.let { raw ->
                        runCatching { Json.decodeFromString<PairingPayload>(raw) }
                            .getOrNull()
                            ?.let(onQrDetected)
                    }
            }
        )
    }

    AndroidView(
        factory = { PreviewView(it).apply { controller = cameraController } },
        modifier = Modifier.fillMaxSize()
    )
}

@Serializable
data class PairingPayload(
    val v: Int,           // protocol version
    val u: String,        // gateway URL
    val t: String,        // pairing token
    val e: Long           // expiry unix timestamp
)
```

### Pattern 7: OpenClaw Gateway Pairing Handshake

**What:** The actual ECDSA-based pairing flow. Send `connect.challenge` frame, wait for `device.pair.requested` (gateway waiting for approval), then wait for `device.pair.resolved` with the scoped token.

**When to use:** After QR scan delivers the gateway URL and pairing token (CONN-01, CONN-02).

```kotlin
// Source: FEATURES.md — OpenClaw Gateway RPC protocol analysis
// Gateway device pairing frames:

// 1. App connects to ws://<url>/?token=<pairing_token>
// 2. Sends:
val challengeFrame = """
{
  "type": "request",
  "id": "pair-${UUID.randomUUID()}",
  "method": "connect.challenge",
  "params": {
    "publicKey": "<base64_encoded_ecdsa_public_key>",
    "deviceName": "${Build.MANUFACTURER} ${Build.MODEL}",
    "deviceId": "<stable_device_fingerprint>"
  }
}
"""
// 3. Gateway emits event: { "type": "event", "event": "device.pair.requested", "data": { ... } }
//    → Owner approves in Control UI or CLI
// 4. Gateway emits event: { "type": "event", "event": "device.pair.resolved",
//                           "data": { "token": "<scoped_token>", "scopes": [...] } }
// 5. App stores token encrypted, reconnects with permanent token
```

### Anti-Patterns to Avoid

- **Ktor for WebSocket in this app:** STACK.md initially proposed Ktor, but STATE.md locks the decision to OkHttp. OkHttp is already a transitive dep (from many Jetpack libraries), simpler to wrap, and more battle-tested on Android. Do not introduce Ktor.
- **EncryptedSharedPreferences:** Deprecated since late 2025. Any use creates immediate migration debt. Use DataStore + Tink from the start.
- **Keeping WebSocket alive in background:** Do not use a foreground service in Phase 1. The connection should close when the app is backgrounded. Phase 4 adds the foreground service for notifications.
- **Single Activity + multiple Activities:** Use single-Activity architecture. Navigation 3 handles all screen transitions within MainActivity.
- **NavController from old Navigation Compose:** Navigation 3 uses `NavDisplay` + `NavBackStack` — not `NavController`. Do not mix the two.
- **Polling for connection state:** Use `ConnectivityManager.registerNetworkCallback()` + OkHttp's `onFailure`/`onClosed` callbacks. Never poll.

---

## Don't Hand-Roll

| Problem | Don't Build | Use Instead | Why |
|---------|-------------|-------------|-----|
| QR code detection | Custom ImageAnalysis pipeline | CameraX `MlKitAnalyzer` | Handles coordinate transforms, frame passing, lifecycle automatically |
| AES-GCM encryption | Custom cipher setup | Google Tink `Aead` | Tink handles key rotation, nonce management, and side-channel hardening |
| Hardware key storage | Manual JCA KeyStore operations | `MasterKey.Builder` + Tink `AndroidKeysetManager` | Handles Keystore provider quirks across OEMs |
| Camera permission flow | Custom permission dialogs | Accompanist Permissions or `rememberPermissionState` | Handles rationale dialog, settings redirect, lifecycle correctly |
| Barcode format parsing | JSON regex extraction | `Barcode.rawValue` + `kotlinx.serialization` | ML Kit returns structured barcode objects |
| JSON-RPC ID correlation | Manual map of pending requests | Request/response correlation map with `CompletableDeferred` | Race conditions in naive implementations |

**Key insight:** Android camera and cryptography APIs have numerous OEM-specific quirks. ML Kit and Tink exist precisely to hide these details. Custom implementations will fail on Xiaomi, Samsung, or older API levels in ways that are hard to debug.

---

## Common Pitfalls

### Pitfall 1: WebSocket Silent Disconnects on Mobile Networks
**What goes wrong:** Mobile network transitions (WiFi to 4G, tower handoffs) silently kill the TCP connection. The `onClose` callback never fires. The UI shows "Connected" while no data flows.
**Why it happens:** NAT gateways on cellular drop idle TCP mappings in as little as 30 seconds. The OS does not surface this to the application layer.
**How to avoid:** Implement an application-level heartbeat — send a ping frame every 20-25 seconds, expect a pong within 5 seconds. If 2 consecutive pongs are missed, declare the connection dead and reconnect.
**Warning signs:** Connection status stuck on "Connected" after switching networks. Messages not arriving after the phone wakes from sleep.

### Pitfall 2: EncryptedSharedPreferences (Deprecated)
**What goes wrong:** Using `EncryptedSharedPreferences` — still recommended in many tutorials — creates migration debt immediately. The API is deprecated as of late 2025.
**Why it happens:** The deprecation is recent (late 2025) and most tutorials predate it.
**How to avoid:** Use DataStore + Tink from day one. The migration guide is documented at ProAndroidDev (2026) but it is painful to do retroactively.
**Warning signs:** Any import of `androidx.security.crypto.EncryptedSharedPreferences`.

### Pitfall 3: Navigation 3 is NOT backwards compatible with old Navigation Compose
**What goes wrong:** Mixing `navigation3-runtime` with the old `navigation-compose` (NavController, NavHost) causes class conflicts and confusing state bugs.
**Why it happens:** Both libraries coexist in the dependency tree if a third-party library pulls in the old one.
**How to avoid:** Explicitly exclude the old navigation-compose dependency if it appears as a transitive dep. Use `NavDisplay` and `NavBackStack` exclusively.
**Warning signs:** `ClassCastException` involving `NavController` or duplicate backstack behavior.

### Pitfall 4: TLS Enforcement Bypass for Local Development
**What goes wrong:** Developers add a `ws://` exception for localhost during development, then ship with the exception still in place.
**Why it happens:** The gateway defaults to `http://localhost:18789` (no TLS), which forces developers to allow non-TLS for local testing.
**How to avoid:** In production code, validate that the URL is `wss://` or that the host is `localhost`/`127.0.0.1`/`::1` (loopback). Add a lint check or URL validator that rejects plain `ws://` for non-loopback hosts at connect time.
**Warning signs:** `if (BuildConfig.DEBUG) allowInsecure = true` code that survives to release.

### Pitfall 5: Pairing Token Expiry Not Checked Client-Side
**What goes wrong:** The QR code contains an expiry timestamp (`e` field). If the app does not check it before attempting to pair, the user gets a cryptic "invalid token" error from the gateway instead of a clear "QR code expired" message.
**Why it happens:** Easy to forget client-side validation when the server will also reject it.
**How to avoid:** After parsing the QR payload, check `payload.e * 1000 > System.currentTimeMillis()` before sending `connect.challenge`. Show "QR code expired — generate a new one" UI if expired.
**Warning signs:** Vague error messages on pairing failure.

### Pitfall 6: Android Keystore Unavailable on Some Emulators
**What goes wrong:** Android Keystore requires secure hardware or a software emulation of it. Some older API emulators (especially API < 28) may not emulate it correctly, causing `KeyStoreException` on keypair generation.
**Why it happens:** Emulators without Google Play services or with older system images may not have the Keystore provider.
**How to avoid:** Wrap keypair generation in a try-catch. For testing on emulators without Keystore support, fall back to generating the key in memory with a clear warning (never ship this fallback). Use API 28+ emulators with Google Play for development.
**Warning signs:** `java.security.KeyStoreException: AndroidKeyStore not initialized`.

---

## Code Examples

### Gateway WebSocket Frame Types

```kotlin
// Source: FEATURES.md — gateway source analysis
// All frames are JSON. Three types:

// CLIENT -> GATEWAY (Request)
@Serializable
data class RequestFrame(
    val type: String = "request",
    val id: String,          // correlates response
    val method: String,      // e.g. "connect.challenge"
    val params: JsonObject = buildJsonObject {}
)

// GATEWAY -> CLIENT (Response to a request)
@Serializable
data class ResponseFrame(
    val type: String,        // "response"
    val id: String,          // matches request id
    val result: JsonObject?,
    val error: ErrorPayload?
)

// GATEWAY -> CLIENT (Unsolicited event)
@Serializable
data class EventFrame(
    val type: String,        // "event"
    val event: String,       // e.g. "device.pair.resolved"
    val data: JsonObject
)

@Serializable
data class ErrorPayload(val message: String, val code: String?)
```

### Connection State Persistence Check

```kotlin
// On app start: check if already paired before showing pairing screen
class ConnectionRepository(
    private val credentialStore: CredentialStore,
    private val webSocketManager: WebSocketManager
) {
    // Returns true if credentials exist and the connection succeeds
    suspend fun isAlreadyPaired(): Boolean {
        val token = credentialStore.getToken() ?: return false
        val url = credentialStore.getUrl() ?: return false
        return token.isNotBlank() && url.isNotBlank()
    }

    suspend fun unpair() {
        webSocketManager.disconnect()
        credentialStore.clearAll()
    }
}
```

### TLS URL Validator (SECR-01)

```kotlin
// Enforce wss:// for non-loopback hosts
fun validateGatewayUrl(url: String): Result<String> {
    val parsed = runCatching { URL(url) }.getOrElse {
        return Result.failure(IllegalArgumentException("URL invalida"))
    }
    val isLoopback = parsed.host in listOf("localhost", "127.0.0.1", "::1")
    return when {
        parsed.protocol == "wss" -> Result.success(url)
        parsed.protocol == "ws" && isLoopback -> Result.success(url)
        parsed.protocol == "ws" && !isLoopback ->
            Result.failure(IllegalArgumentException("Se requiere wss:// para hosts remotos"))
        else -> Result.failure(IllegalArgumentException("Protocolo no soportado: ${parsed.protocol}"))
    }
}
```

### Stable Device Fingerprint

```kotlin
// Generates a stable, non-identifying device ID for gateway pairing
fun stableDeviceId(context: Context): String {
    val androidId = Settings.Secure.getString(
        context.contentResolver,
        Settings.Secure.ANDROID_ID
    )
    return UUID.nameUUIDFromBytes(
        "${Build.MANUFACTURER}|${Build.MODEL}|$androidId".toByteArray()
    ).toString()
}
```

---

## State of the Art

| Old Approach | Current Approach | When Changed | Impact |
|--------------|------------------|--------------|--------|
| EncryptedSharedPreferences | DataStore + Tink | Late 2025 | Must use new approach; migration is painful |
| KAPT for Room/DI | KSP only | 2024-2025 | KAPT deprecated; Room 3.0 and KSP are KSP-only |
| Navigation Compose (NavController) | Navigation 3 (NavBackStack) | Dec 2025 | Nav3 is Compose-native; old one is legacy |
| Scarlet for WebSocket | OkHttp direct wrapper | 2023+ | Scarlet abandoned at 0.1.12 |
| ZXing for QR | CameraX + ML Kit | 2021+ | ML Kit is faster, on-device, officially supported |
| Multiple Activities | Single Activity + Nav3 | 2020+ | All modern Android apps use single Activity |
| RxJava for async | Kotlin Coroutines + Flow | 2021+ | Coroutines are the Kotlin-idiomatic choice |

**Deprecated/outdated:**
- `EncryptedSharedPreferences`: deprecated late 2025, remove from consideration entirely
- KAPT: deprecated, use KSP for all annotation processing (Room, etc.)
- Scarlet WebSocket library: stuck at 0.1.12, last real commit 2022
- Old `androidx.navigation:navigation-compose`: legacy, do not use in new code
- `collectAsState()` without lifecycle: use `collectAsStateWithLifecycle()` instead

---

## Open Questions

1. **KSP exact patch version**
   - What we know: KSP 2.2.0 is the base version for Kotlin 2.2.0
   - What's unclear: The exact patch suffix (e.g., `2.2.0-1.0.29`) must be verified against https://github.com/google/ksp/releases at project creation time
   - Recommendation: Look up the latest `2.2.0-1.0.X` release before pinning in version catalog

2. **Gateway TLS certificate for local LAN access**
   - What we know: The gateway runs on localhost by default; users access it remotely via Tailscale or a reverse proxy
   - What's unclear: Does the Tailscale MagicDNS certificate work with Android's TLS verification, or does the user need to install a custom CA cert?
   - Recommendation: Test with a real Tailscale hostname during Phase 1. If cert issues arise, document the `wss://` + custom trust manager path for self-signed certs.

3. **connect.challenge exact frame schema**
   - What we know: The gateway expects a `connect.challenge` request with public key + device metadata based on FEATURES.md analysis
   - What's unclear: The exact field names in `params` — `publicKey`? `pub_key`? `deviceName`? Need to verify against the actual gateway source or test against a live instance.
   - Recommendation: Test against Brian's live OpenClaw instance at `localhost:18789` during Phase 1 implementation and adjust schema accordingly.

---

## Validation Architecture

### Test Framework

| Property | Value |
|----------|-------|
| Framework | JUnit 5 (5.11.x) + Turbine (1.2.x) + MockK (1.14.x) |
| Config file | None yet — Wave 0 creates `app/src/test/` structure |
| Quick run command | `./gradlew :app:testDebugUnitTest --tests "*.connection.*" -x lint` |
| Full suite command | `./gradlew :app:testDebugUnitTest -x lint` |

### Phase Requirements -> Test Map

| Req ID | Behavior | Test Type | Automated Command | File Exists? |
|--------|----------|-----------|-------------------|-------------|
| CONN-01 | QR payload parsed correctly, expiry checked | unit | `./gradlew :app:testDebugUnitTest --tests "*.PairingPayloadTest"` | Wave 0 |
| CONN-02 | ECDSA keypair generated, stored in KeyStore | unit (mock KeyStore) | `./gradlew :app:testDebugUnitTest --tests "*.KeystoreTest"` | Wave 0 |
| CONN-03 | Token + URL stored/retrieved encrypted | unit | `./gradlew :app:testDebugUnitTest --tests "*.CredentialStoreTest"` | Wave 0 |
| CONN-04 | ConnectionState transitions emitted correctly | unit (Turbine) | `./gradlew :app:testDebugUnitTest --tests "*.WebSocketManagerTest"` | Wave 0 |
| CONN-05 | Reconnection delay follows exponential backoff | unit | `./gradlew :app:testDebugUnitTest --tests "*.ReconnectPolicyTest"` | Wave 0 |
| CONN-06 | URL validator accepts wss://, rejects ws:// for non-loopback | unit | `./gradlew :app:testDebugUnitTest --tests "*.UrlValidatorTest"` | Wave 0 |
| CONN-07 | Unpair clears credentials from DataStore | unit | `./gradlew :app:testDebugUnitTest --tests "*.CredentialStoreTest"` | Wave 0 |
| SECR-01 | TLS enforced — ws:// to remote host rejected | unit | same as CONN-06 | Wave 0 |
| SECR-02 | Token never stored unencrypted | unit | same as CONN-03 | Wave 0 |
| SECR-03 | Second pairing attempt blocked if already paired | unit | `./gradlew :app:testDebugUnitTest --tests "*.ConnectionRepositoryTest"` | Wave 0 |

### Sampling Rate

- **Per task commit:** `./gradlew :app:testDebugUnitTest -x lint` (all unit tests, ~10 seconds)
- **Per wave merge:** Full unit suite + `./gradlew :app:connectedDebugAndroidTest` on emulator
- **Phase gate:** Full suite green before marking Phase 1 complete

### Wave 0 Gaps

- [ ] `app/src/test/java/com/clawpilot/` directory structure
- [ ] `app/src/test/java/com/clawpilot/data/remote/ws/WebSocketManagerTest.kt` — covers CONN-04, CONN-05
- [ ] `app/src/test/java/com/clawpilot/data/local/prefs/CredentialStoreTest.kt` — covers CONN-03, CONN-07, SECR-02
- [ ] `app/src/test/java/com/clawpilot/ui/pairing/PairingPayloadTest.kt` — covers CONN-01
- [ ] `app/src/test/java/com/clawpilot/data/remote/ws/ReconnectPolicyTest.kt` — covers CONN-05
- [ ] `app/src/test/java/com/clawpilot/util/UrlValidatorTest.kt` — covers CONN-06, SECR-01
- [ ] `app/src/test/java/com/clawpilot/data/repository/ConnectionRepositoryTest.kt` — covers SECR-03

---

## Sources

### Primary (HIGH confidence)

- FEATURES.md (2026-03-25) — OpenClaw Gateway WebSocket protocol, pairing flow, RPC catalog extracted from gateway source
- STACK.md (2026-03-25) — Full technology stack research with verified versions
- ARCHITECTURE.md (2026-03-25) — WebSocket patterns, pairing flow diagram, Navigation 3 shell patterns
- STATE.md (2026-03-25) — Final architectural decisions: OkHttp over Ktor, Koin over Hilt
- [Android Keystore official docs](https://developer.android.com/privacy-and-security/keystore) — ECDSA keypair generation
- [CameraX MlKitAnalyzer](https://developer.android.com/media/camera/camerax/mlkitanalyzer) — QR scanning integration
- [Navigation 3 official docs](https://developer.android.com/guide/navigation/navigation-3) — Multiple backstacks recipe
- [Navigation 3 multiple backstacks recipe](https://developer.android.com/guide/navigation/navigation-3/recipes/multiple-backstacks) — Bottom tab pattern

### Secondary (MEDIUM confidence)

- [DataStore + Tink migration reference](https://github.com/Jaypatelbond/Android-DataStore-Tink-Migration) — Verified pattern for encrypted credential storage
- [ProAndroidDev: EncryptedSharedPreferences deprecation guide (2026)](https://proandroiddev.com/goodbye-encryptedsharedpreferences-a-2026-migration-guide-4b819b4a537a) — Verified via droidcon
- [Navigation 3 bottom tabs deep-dive](https://medium.com/@sunildhiman90/mastering-compose-navigation-3-a-deep-dive-into-navigation-3-part-2-bottom-tabs-navigation-2086c28b25fe) — Multiple backstack implementation pattern
- [Koin + Navigation 3 integration (Jan 2026)](https://medium.com/@spparks_/koin-scaling-your-project-with-annotations-ksp-and-navigation-3-integration-273abe767a4b) — Koin 4.1 + Nav3 integration

### Tertiary (LOW confidence)

- WebSearch results on OkHttp WebSocket coroutines Flow wrapper — patterns cross-validated with official OkHttp docs
- KSP 2.2.0 patch version — requires verification at https://github.com/google/ksp/releases before pinning

---

## Metadata

**Confidence breakdown:**
- Standard stack: HIGH — verified versions from STACK.md research + project STATE.md decisions
- Architecture patterns: HIGH — WebSocket manager, ECDSA Keystore, DataStore+Tink all from official Android docs
- Pairing protocol: HIGH — extracted directly from OpenClaw gateway source files (FEATURES.md)
- Pitfalls: HIGH — most from official docs or real-world incident reports (Home Assistant, etc.)
- Test map: MEDIUM — standard Android testing patterns, but test file locations assumed from project structure not yet created

**Research date:** 2026-03-25
**Valid until:** 2026-06-25 (90 days — stable ecosystem, Navigation 3 1.0.x, no major churn expected)
