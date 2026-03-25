---
phase: 01-project-setup-connection
plan: 02
subsystem: auth
tags: [android, kotlin, tink, aes256-gcm, datastore, android-keystore, ecdsa, p256, crypto]

# Dependency graph
requires: []
provides:
  - ECDSA P-256 keypair generation and management via Android Keystore (KeyStoreManager)
  - Encrypted credential storage for gateway URL and token (CredentialStore, Tink AES-256-GCM)
  - Domain model for gateway credentials and pairing state machine (GatewayCredentials, PairingState)
  - Non-sensitive preference storage (AppPreferences, DataStore)
affects:
  - 01-03 (WebSocket manager needs CredentialStore to get gateway URL and token)
  - 01-04 (Pairing flow uses KeyStoreManager for public key and CredentialStore to persist credentials)
  - 01-05 (Koin DI module needs to wire CredentialStore, AppPreferences, KeyStoreManager)

# Tech tracking
tech-stack:
  added:
    - "Tink 1.16.0 (tink-android) — AES-256-GCM encryption with Android Keystore master key"
    - "DataStore Preferences 1.2.1 — encrypted and plain preference storage"
    - "Android Keystore — hardware-backed ECDSA P-256 key storage"
  patterns:
    - "Encrypted-at-rest: sensitive values (token, URL) encrypted before DataStore write, decrypted on read"
    - "Reactive Flow API: getCredentials() and isPaired() return Flow<T> for ViewModel consumption"
    - "Single-key convention: alias 'clawpilot_device_key' enforces one device identity per install"
    - "Context extension: preferencesDataStore as top-level val for Android DataStore singleton"

key-files:
  created:
    - app/src/main/java/com/clawpilot/data/local/crypto/KeyStoreManager.kt
    - app/src/main/java/com/clawpilot/data/local/prefs/CredentialStore.kt
    - app/src/main/java/com/clawpilot/data/local/prefs/AppPreferences.kt
    - app/src/main/java/com/clawpilot/domain/model/GatewayCredentials.kt
  modified:
    - app/build.gradle.kts (added datastore-preferences and tink dependencies)
    - gradle/wrapper/gradle-wrapper.properties (updated to Gradle 9.3.1 for AGP 9.1.0 compatibility)

key-decisions:
  - "ECDSA P-256 with SHA256withECDSA: matches official OpenClaw Android client pairing handshake"
  - "Tink over EncryptedSharedPreferences: EncryptedSharedPreferences deprecated, Tink is current Google recommendation"
  - "userAuthenticationRequired=false for device keypair: pairing key should not require biometric on every use"
  - "Single set of credentials enforced: storeCredentials() overwrites, clearCredentials() wipes — one gateway per device"
  - "Gradle 9.3.1 required: AGP 9.1.0 requires minimum Gradle 9.3.1 (wrapper updated as blocking fix)"

patterns-established:
  - "Pattern: Tink AEAD lazy init — AeadConfig.register() in init{}, aead: Aead by lazy{} avoids blocking constructor"
  - "Pattern: encrypt-before-write — storeCredentials() encrypts token and URL, deviceName and scopes stored plain"
  - "Pattern: Flow<T?> returning null for unpaired state — callers check null to determine pairing status"

requirements-completed: [CONN-02, CONN-03, SECR-02, SECR-03]

# Metrics
duration: 28min
completed: 2026-03-25
---

# Phase 01 Plan 02: Secure Storage Layer Summary

**ECDSA P-256 Android Keystore keypair + AES-256-GCM encrypted DataStore credentials via Tink, with reactive Flow API for ViewModel consumption**

## Performance

- **Duration:** 28 min
- **Started:** 2026-03-25T15:10:25Z
- **Completed:** 2026-03-25T15:38:00Z
- **Tasks:** 2
- **Files modified:** 6

## Accomplishments
- Hardware-backed ECDSA P-256 keypair in Android Keystore — private key never leaves secure enclave
- AES-256-GCM encrypted credential storage: gateway URL and token encrypted before hitting disk
- Tink keyset stored in SharedPreferences with Android Keystore master key — defense in depth
- Flow-based reactive API ready for ViewModel direct consumption without boilerplate
- PairingState sealed class state machine covers all pairing lifecycle states

## Task Commits

Each task was committed atomically:

1. **Task 1: ECDSA KeyStoreManager and GatewayCredentials model** - `76cfd2b` (feat)
2. **Task 2: DataStore+Tink CredentialStore and AppPreferences** - `b939eb1` (feat)

**Plan metadata:** (final docs commit, see below)

## Files Created/Modified
- `app/src/main/java/com/clawpilot/data/local/crypto/KeyStoreManager.kt` - ECDSA P-256 keypair ops via Android Keystore
- `app/src/main/java/com/clawpilot/domain/model/GatewayCredentials.kt` - Gateway credentials data class + PairingState sealed class
- `app/src/main/java/com/clawpilot/data/local/prefs/CredentialStore.kt` - Encrypted credential storage (Tink AES-256-GCM + DataStore)
- `app/src/main/java/com/clawpilot/data/local/prefs/AppPreferences.kt` - Plain DataStore for theme mode and last gateway URL
- `app/build.gradle.kts` - Added datastore-preferences and tink dependencies
- `gradle/wrapper/gradle-wrapper.properties` - Updated to Gradle 9.3.1

## Decisions Made
- Tink AES-256-GCM selected over EncryptedSharedPreferences (deprecated in recent AndroidX)
- `userAuthenticationRequired = false` for ECDSA key: device key should not require biometric per-use
- `getPrimitive(Aead::class.java)` kept despite deprecation warning — functional in Tink 1.16.0, migration to new handle API is future work
- Single `ALIAS = "clawpilot_device_key"` constant enforces one identity per device install

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 3 - Blocking] Gradle wrapper updated to 9.3.1**
- **Found during:** Task 2 (compilation verification)
- **Issue:** `gradle-wrapper.properties` pointed to Gradle 8.13 which doesn't satisfy AGP 9.1.0's minimum requirement of Gradle 9.3.1
- **Fix:** Updated `distributionUrl` to `gradle-9.3.1-bin.zip` (already cached locally)
- **Files modified:** `gradle/wrapper/gradle-wrapper.properties`
- **Verification:** `./gradlew --version` shows Gradle 9.3.1; `compileDebugKotlin` succeeds
- **Committed in:** `b939eb1` (Task 2 commit)

**2. [Rule 3 - Blocking] Added tink and datastore deps to app/build.gradle.kts**
- **Found during:** Task 2 (verifying dependencies per plan instructions)
- **Issue:** Plan 01-01 correctly excluded these dependencies per its own instructions ("DO NOT add tink, datastore yet"); this plan needed to add them
- **Fix:** Added `implementation(libs.datastore.preferences)` and `implementation(libs.tink)` to app/build.gradle.kts
- **Files modified:** `app/build.gradle.kts`
- **Verification:** `compileDebugKotlin` resolves all imports in CredentialStore.kt and AppPreferences.kt
- **Committed in:** `b939eb1` (Task 2 commit)

---

**Total deviations:** 2 auto-fixed (2 blocking)
**Impact on plan:** Both fixes required for compilation. Gradle wrapper update is a shared-file change that Plan 01-01 must be aware of.

## Issues Encountered
- Parallel execution with Plan 01-01 created shared-file ownership question for `app/build.gradle.kts` and `gradle/wrapper/gradle-wrapper.properties`. Resolved by committing the complete working state — Plan 01-01 must rebase/merge.
- `getPrimitive(Aead::class.java)` shows deprecation warning in Tink 1.16.0. Kept for now as functional — note in deferred items for future migration to `keysetHandle.getPrimitive(Aead.class)` with non-deprecated overload.

## Next Phase Readiness
- KeyStoreManager ready for Plan 04 pairing flow: `generateEcdsaKeyPair()`, `getPublicKeyBase64()`, `sign()`
- CredentialStore ready for Plan 03 WebSocket manager: `getCredentials()` returns Flow with URL and token
- AppPreferences ready for Plan 05 Settings UI
- Koin DI module (Plan 05) needs to wire: `single { KeyStoreManager() }`, `single { CredentialStore(get()) }`, `single { AppPreferences(get()) }`

---
*Phase: 01-project-setup-connection*
*Completed: 2026-03-25*
