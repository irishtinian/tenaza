# Technology Stack

**Project:** ClawPilot — Native Android app for OpenClaw multi-agent control
**Researched:** 2026-03-25
**Overall Confidence:** HIGH

---

## Recommended Stack

### Language & Build Tools

| Technology | Version | Purpose | Why |
|------------|---------|---------|-----|
| Kotlin | 2.2.0 (stable) | Primary language | Latest stable, guard conditions promoted to Stable, excellent coroutines. Kotlin 2.3.20 exists but too bleeding-edge for app launch. |
| AGP | 9.1.0 | Android Gradle Plugin | March 2026 release, supports API 36.1, built-in Kotlin support. |
| Gradle | 8.13+ | Build system | Required by AGP 9.1, version catalogs standard. |
| KSP | 2.2.0-x | Symbol processing | Required by Room 3.0 and Hilt. KAPT is dead — KSP only. |

### Core UI Framework

| Technology | Version | Purpose | Why |
|------------|---------|---------|-----|
| Jetpack Compose BOM | 2026.03.00 | UI toolkit | Current stable BOM, maps to Compose 1.10.5. Declarative UI is the only sane choice for a new app in 2026. |
| Material 3 | via BOM | Design system | Default material design for Compose. Use dynamic color theming for personality. |
| Navigation 3 | 1.0.1 | Screen navigation | **Use this, not the old Navigation Compose.** Stable since Nov 2025, Compose-native, you own the backstack as a simple list. No XML, no NavGraph, no magic. |

**Navigation 3 rationale:** The old `androidx.navigation:navigation-compose` is legacy. Navigation 3 (`androidx.navigation3`) was built from scratch for Compose. You own the backstack as a `SnapshotStateList`, navigation is just add/remove from a list. Much simpler mental model. Deeplinks still maturing but not needed for ClawPilot.

**Rejected:** Voyager (third-party, unnecessary now that Nav3 is stable), Circuit (Square's library, overkill for this scope).

### Networking — REST

| Technology | Version | Purpose | Why |
|------------|---------|---------|-----|
| Ktor Client | 3.4.x | HTTP client | **Use Ktor, not Retrofit.** Kotlin-first, coroutines-native, lighter (~2MB vs ~3MB), built-in serialization with kotlinx.serialization, WebSocket support in the same library. No annotation processing, no reflection overhead. |
| kotlinx.serialization | 1.8.x | JSON parsing | Kotlin-native, compile-time, no reflection. Works seamlessly with Ktor. |

**Rejected:** Retrofit — still excellent but tied to Java patterns (annotations, reflection, converters). For a 2026 pure-Kotlin app with WebSocket needs, Ktor is the unified choice. Using one library for both REST and WebSocket simplifies the dependency tree significantly.

### Networking — WebSocket (Real-time Agent Streaming)

| Technology | Version | Purpose | Why |
|------------|---------|---------|-----|
| Ktor WebSocket Client | 3.4.x | Real-time comms | Same Ktor dependency as REST. Built-in WebSocket support with Kotlin Flows for reactive streaming. Handles ping/pong, reconnection logic composable with coroutines. |

**Rejected:**
- **Scarlet** — Last real release was 0.1.12, version 0.2.x still unreleased after years. Effectively unmaintained. Do not use.
- **OkHttp WebSocket** — Works but low-level. Would need manual Flow wrapping, reconnection logic, serialization. Ktor gives all this out of the box.

### Push Notifications

| Technology | Version | Purpose | Why |
|------------|---------|---------|-----|
| Firebase Cloud Messaging | latest (via Firebase BOM) | Push notifications | Industry standard, only real option for reliable Android push. Required for agent alerts, approval requests. |
| Firebase BOM | 33.x | Version management | Manages FCM + Analytics versions. |

**Implementation notes:**
- Create custom `FirebaseMessagingService` for foreground handling
- Notification channels: `agent-alerts` (high priority), `conversations` (default), `system` (low)
- Action buttons on notifications: `APPROVE` / `REJECT` intents for agent approval requests — use `PendingIntent` with `FLAG_IMMUTABLE`
- Data-only messages for maximum control (not notification messages)

### Local Storage

| Technology | Version | Purpose | Why |
|------------|---------|---------|-----|
| Room | 3.0.x | Offline database | New major version, KSP-only, all DAOs must be suspend or return Flow. Perfect for Kotlin-first. Cache conversations, agent states, command history. |
| DataStore (Preferences) | 1.2.1 | Key-value prefs | App settings, theme, last connected server. Replaces SharedPreferences entirely. |
| Tink | 1.x | Encryption layer | For encrypting sensitive DataStore content (tokens, server URLs). **EncryptedSharedPreferences is deprecated** — use DataStore + Tink instead. |

**What to cache in Room:**
- Agent list + states (offline viewing)
- Recent conversations (last 50 per agent)
- Command history (for re-execution)
- Server connection profiles

**What to store in DataStore:**
- Active server URL
- Theme preference
- Notification settings
- Last sync timestamp

### QR Code Scanning (Device Pairing)

| Technology | Version | Purpose | Why |
|------------|---------|---------|-----|
| CameraX | 1.5.x | Camera abstraction | Jetpack library, lifecycle-aware, works across API 21+. |
| ML Kit Barcode | 17.x (bundled) | QR detection | On-device, no network needed, supports all barcode formats. Use bundled model (not unbundled) for instant scanning without download. |

**Use `MlKitAnalyzer`** — the official bridge between CameraX and ML Kit. Handles coordinate transforms and frame passing automatically. Much cleaner than manual `ImageAnalysis.Analyzer` implementations.

**Rejected:** ZXing — old, Java-based, no longer the recommended approach. ML Kit is faster, more accurate, and officially supported by Google.

### Dependency Injection

| Technology | Version | Purpose | Why |
|------------|---------|---------|-----|
| Koin | 4.1.x | DI framework | **Use Koin, not Hilt.** Simpler setup, no annotation processing, no code generation, pure Kotlin DSL. `koinInject()` works naturally in Composables. For an app of ClawPilot's scope (not enterprise-scale), Koin's simplicity wins over Hilt's compile-time guarantees. |
| koin-androidx-compose | 4.1.x | Compose integration | Wraps koin-compose + koin-compose-viewmodel. |
| koin-androidx-compose-navigation | 4.1.x | Nav integration | ViewModel scoping with navigation. |

**Rationale:** Hilt requires KSP/KAPT, generates code, adds build time, needs `@HiltViewModel`, `@AndroidEntryPoint` annotations everywhere. Koin uses a DSL — `module { viewModelOf(::AgentListViewModel) }`. For a team of 1-2 developers on a focused app, Koin is dramatically less friction. The "runtime DI" performance concern is negligible — Koin's resolution is microseconds on modern devices.

**When to reconsider Hilt:** If ClawPilot grows to 50+ screens with complex scoping needs. Not expected.

### Security

| Technology | Version | Purpose | Why |
|------------|---------|---------|-----|
| DataStore + Tink | See above | Token storage | Encrypted Proto DataStore for auth tokens, server credentials. AES-GCM encryption via Tink. |
| BiometricPrompt | 1.4.x | App lock | `androidx.biometric` for fingerprint/face unlock. Gate app access behind biometric or PIN. |
| OkHttp CertificatePinner | via Ktor OkHttp engine | Cert pinning | Pin the OpenClaw gateway's TLS certificate. Prevents MITM on the control channel. |
| Android Keystore | System API | Key management | Hardware-backed key storage for biometric-bound crypto keys. |

**Security architecture:**
1. First launch: scan QR to get server URL + initial token
2. Token stored in encrypted DataStore (Tink + Keystore-backed key)
3. App access gated by BiometricPrompt on each open
4. All network calls over TLS with certificate pinning
5. Token refresh via Ktor interceptor

### Architecture Pattern

| Pattern | Purpose | Why |
|---------|---------|-----|
| **MVI** (Model-View-Intent) | Presentation layer | Unidirectional data flow maps perfectly to Compose's declarative model. State is a single immutable data class, intents are sealed classes, reducer is a pure function. Predictable, testable, debuggable. |
| Clean Architecture (3 layers) | Project structure | `data` / `domain` / `presentation` separation. Keeps business logic independent of Android framework. |

**MVI over MVVM because:**
- Compose already wants immutable state — MVI enforces this
- Single `UiState` data class per screen = single source of truth
- Side effects handled explicitly via `Channel` or `SharedFlow`
- Easier to debug: log every intent and state transition
- More boilerplate than MVVM, but worth it for a real-time app where state consistency matters

**Concrete pattern per screen:**
```kotlin
// Estado
data class AgentListState(
    val agents: List<Agent> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null
)

// Intenciones del usuario
sealed interface AgentListIntent {
    data object LoadAgents : AgentListIntent
    data class SelectAgent(val id: String) : AgentListIntent
    data class ApproveAction(val agentId: String, val actionId: String) : AgentListIntent
}

// ViewModel con reducer
class AgentListViewModel(...) : ViewModel() {
    private val _state = MutableStateFlow(AgentListState())
    val state: StateFlow<AgentListState> = _state.asStateFlow()

    fun onIntent(intent: AgentListIntent) { /* reduce */ }
}
```

### Testing

| Technology | Version | Purpose | Why |
|------------|---------|---------|-----|
| JUnit 5 | 5.11.x | Unit tests | Modern assertions, coroutine test support. |
| Turbine | 1.2.x | Flow testing | Test StateFlow/SharedFlow emissions cleanly. |
| MockK | 1.14.x | Mocking | Kotlin-native mocking, works with coroutines and suspend functions. |
| Compose UI Test | via BOM | UI tests | `ComposeTestRule` for Compose component tests. |

### Build & Distribution

| Technology | Purpose | Details |
|------------|---------|---------|
| Gradle Version Catalog | Dependency management | `gradle/libs.versions.toml` — single source of truth for all versions. |
| GitHub Actions | CI/CD | Build, test, sign, distribute. |
| GitHub Releases | Distribution | APK + changelog for each release. Private app, no Play Store needed initially. |
| Signing | Release builds | Keystore in GitHub Secrets, sign via Gradle. |

**CI/CD pipeline:**
1. Push to `main` -> build + test
2. Tag `v*` -> build release APK -> GitHub Release with APK attached
3. Optional: Firebase App Distribution for beta testing

---

## Alternatives Considered

| Category | Recommended | Alternative | Why Not |
|----------|-------------|-------------|---------|
| Navigation | Navigation 3 | Compose Navigation (old) | Legacy, XML-era mental model wrapped in Compose |
| Navigation | Navigation 3 | Voyager | Third-party, unnecessary now that official solution is stable |
| HTTP Client | Ktor Client | Retrofit | Java patterns, separate WebSocket library needed |
| WebSocket | Ktor WebSocket | Scarlet | Unmaintained (stuck at 0.1.12), abandoned 0.2.x branch |
| WebSocket | Ktor WebSocket | OkHttp raw | Too low-level, manual Flow/reconnection |
| DI | Koin | Hilt | Overkill for this scope, slower builds, more ceremony |
| QR Scan | CameraX + ML Kit | ZXing | Old Java library, less accurate, no official support |
| Storage | DataStore + Tink | EncryptedSharedPrefs | Deprecated, sync API, keyset corruption bugs |
| DB | Room 3.0 | SQLDelight | Room is Jetpack standard, better tooling, Google-backed |
| Architecture | MVI | MVVM | Less structured state management for real-time app |

---

## Installation

```toml
# gradle/libs.versions.toml

[versions]
kotlin = "2.2.0"
agp = "9.1.0"
compose-bom = "2026.03.00"
navigation3 = "1.0.1"
ktor = "3.4.0"
room = "3.0.0"
datastore = "1.2.1"
koin = "4.1.1"
camerax = "1.5.0"
mlkit-barcode = "17.3.0"
biometric = "1.4.0"
tink = "1.16.0"
firebase-bom = "33.9.0"
mockk = "1.14.0"
turbine = "1.2.0"

[libraries]
# Compose
compose-bom = { group = "androidx.compose", name = "compose-bom", version.ref = "compose-bom" }
compose-material3 = { group = "androidx.compose.material3", name = "material3" }
compose-ui = { group = "androidx.compose.ui", name = "ui" }
compose-ui-tooling = { group = "androidx.compose.ui", name = "ui-tooling" }

# Navigation 3
navigation3-runtime = { group = "androidx.navigation3", name = "navigation3-runtime", version.ref = "navigation3" }
navigation3-ui = { group = "androidx.navigation3", name = "navigation3-ui", version.ref = "navigation3" }

# Ktor
ktor-client-core = { group = "io.ktor", name = "ktor-client-core", version.ref = "ktor" }
ktor-client-okhttp = { group = "io.ktor", name = "ktor-client-okhttp", version.ref = "ktor" }
ktor-client-websockets = { group = "io.ktor", name = "ktor-client-websockets", version.ref = "ktor" }
ktor-client-content-negotiation = { group = "io.ktor", name = "ktor-client-content-negotiation", version.ref = "ktor" }
ktor-serialization-json = { group = "io.ktor", name = "ktor-serialization-kotlinx-json", version.ref = "ktor" }
ktor-client-logging = { group = "io.ktor", name = "ktor-client-logging", version.ref = "ktor" }

# Room 3.0
room-runtime = { group = "androidx.room", name = "room-runtime", version.ref = "room" }
room-compiler = { group = "androidx.room", name = "room-compiler", version.ref = "room" }
room-ktx = { group = "androidx.room", name = "room-ktx", version.ref = "room" }

# DataStore
datastore-preferences = { group = "androidx.datastore", name = "datastore-preferences", version.ref = "datastore" }

# Koin
koin-android = { group = "io.insert-koin", name = "koin-android", version.ref = "koin" }
koin-compose = { group = "io.insert-koin", name = "koin-androidx-compose", version.ref = "koin" }
koin-compose-navigation = { group = "io.insert-koin", name = "koin-androidx-compose-navigation", version.ref = "koin" }

# CameraX + ML Kit
camerax-core = { group = "androidx.camera", name = "camera-core", version.ref = "camerax" }
camerax-camera2 = { group = "androidx.camera", name = "camera-camera2", version.ref = "camerax" }
camerax-lifecycle = { group = "androidx.camera", name = "camera-lifecycle", version.ref = "camerax" }
camerax-view = { group = "androidx.camera", name = "camera-view", version.ref = "camerax" }
camerax-mlkit = { group = "androidx.camera", name = "camera-mlkit-vision", version.ref = "camerax" }
mlkit-barcode = { group = "com.google.mlkit", name = "barcode-scanning", version.ref = "mlkit-barcode" }

# Security
biometric = { group = "androidx.biometric", name = "biometric", version.ref = "biometric" }
tink = { group = "com.google.crypto.tink", name = "tink-android", version.ref = "tink" }

# Firebase
firebase-bom = { group = "com.google.firebase", name = "firebase-bom", version.ref = "firebase-bom" }
firebase-messaging = { group = "com.google.firebase", name = "firebase-messaging-ktx" }

# Testing
mockk = { group = "io.mockk", name = "mockk", version.ref = "mockk" }
turbine = { group = "app.cash.turbine", name = "turbine", version.ref = "turbine" }

[plugins]
android-application = { id = "com.android.application", version.ref = "agp" }
kotlin-android = { id = "org.jetbrains.kotlin.android", version.ref = "kotlin" }
kotlin-compose = { id = "org.jetbrains.kotlin.plugin.compose", version.ref = "kotlin" }
kotlin-serialization = { id = "org.jetbrains.kotlin.plugin.serialization", version.ref = "kotlin" }
ksp = { id = "com.google.devtools.ksp", version = "2.2.0-1.0.x" }
google-services = { id = "com.google.gms.google-services", version = "4.4.x" }
```

---

## Sources

- [Jetpack Compose BOM releases](https://developer.android.com/develop/ui/compose/bom) — Compose 2026.03.00 stable
- [Navigation 3 official docs](https://developer.android.com/guide/navigation/navigation-3) — Stable 1.0.1
- [Navigation 3 announcement](https://android-developers.googleblog.com/2025/05/announcing-jetpack-navigation-3-for-compose.html)
- [Ktor 3.4.0 release](https://blog.jetbrains.com/kotlin/2026/01/ktor-3-4-0-is-now-available/)
- [Room 3.0 announcement](https://android-developers.googleblog.com/2026/03/room-30-modernizing-room.html) — KSP-only, suspend DAOs
- [Kotlin 2.2.0 release](https://kotlinlang.org/docs/whatsnew22.html)
- [AGP 9.1.0 release notes](https://developer.android.com/build/releases/agp-9-1-0-release-notes)
- [Koin releases](https://github.com/InsertKoinIO/koin/releases) — 4.1.1 stable
- [EncryptedSharedPreferences deprecation guide](https://proandroiddev.com/goodbye-encryptedsharedpreferences-a-2026-migration-guide-4b819b4a537a) — Migrate to DataStore + Tink
- [ML Kit Barcode scanning](https://developers.google.com/ml-kit/vision/barcode-scanning/android)
- [CameraX ML Kit Analyzer](https://developer.android.com/training/camerax/mlkitanalyzer)
- [Hilt vs Koin analysis (droidcon)](https://www.droidcon.com/2025/11/26/hilt-vs-koin-the-hidden-cost-of-runtime-injection-and-why-compile-time-di-wins/)
- [Firebase Cloud Messaging setup](https://firebase.google.com/docs/cloud-messaging/android/get-started)
- [Android Security 2026 practices](https://medium.com/@ramadan123sayed/android-security-in-2026-10-practices-every-app-must-implement-with-code-for-each-8d32a896142d)
- [DataStore official docs](https://developer.android.com/topic/libraries/architecture/datastore)
