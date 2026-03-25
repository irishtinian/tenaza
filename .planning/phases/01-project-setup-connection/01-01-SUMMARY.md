---
phase: 01-project-setup-connection
plan: 01
subsystem: ui
tags: [android, kotlin, compose, navigation3, koin, material3, gradle, version-catalog]

# Dependency graph
requires: []
provides:
  - Android project with AGP 8.13.2 + Kotlin 2.1.21 + Compose BOM 2026.03.00
  - Gradle version catalog (gradle/libs.versions.toml) with all dependencies centralized
  - Koin 4.1.1 DI initialized in Application class
  - Navigation 3 bottom-tab shell with 4 tabs (Chat, Dashboard, Crons, Settings)
  - Material 3 theme with dynamic color support (API 31+)
  - Type-safe route hierarchy (sealed interface AppRoute implements NavKey)
  - Buildable APK via ./gradlew assembleDebug
affects: [02-dashboard, 03-chat, 04-notifications, 05-crons, 06-polish]

# Tech tracking
tech-stack:
  added:
    - "AGP 8.13.2 (was planning 9.1.0 — see deviations)"
    - "Kotlin 2.1.21 (was planning 2.2.0 — see deviations)"
    - "Compose BOM 2026.03.00"
    - "Navigation 3 1.0.1"
    - "Koin 4.1.1 (koin-android, koin-androidx-compose, koin-androidx-compose-navigation)"
    - "OkHttp 4.12.0"
    - "kotlinx-serialization-json 1.8.0"
    - "compose-material-icons-extended (via BOM)"
    - "Gradle 8.13 wrapper"
  patterns:
    - "Version catalog in gradle/libs.versions.toml — all versions in one place"
    - "Navigation 3 NavKey pattern: sealed interface implements NavKey + @Serializable"
    - "Navigation 3 entryProvider with NavEntry lambda (not DSL entry<T> — see deviations)"
    - "Per-tab NavBackStack for independent tab state preservation"
    - "Single Activity architecture (MainActivity) with Compose content"
    - "Koin module pattern: empty appModule, ViewModels added in subsequent plans"

key-files:
  created:
    - settings.gradle.kts
    - build.gradle.kts
    - gradle/libs.versions.toml
    - gradle/wrapper/gradle-wrapper.properties
    - app/build.gradle.kts
    - gradle.properties
    - app/src/main/AndroidManifest.xml
    - app/src/main/res/values/strings.xml
    - app/src/main/res/values/themes.xml
    - app/src/main/res/values/colors.xml
    - app/src/main/java/com/clawpilot/ClawPilotApp.kt
    - app/src/main/java/com/clawpilot/MainActivity.kt
    - app/src/main/java/com/clawpilot/di/AppModule.kt
    - app/src/main/java/com/clawpilot/ui/navigation/AppRoutes.kt
    - app/src/main/java/com/clawpilot/ui/navigation/AppNavHost.kt
    - app/src/main/java/com/clawpilot/ui/shell/MainShell.kt
    - app/src/main/java/com/clawpilot/ui/theme/Theme.kt
    - app/src/main/java/com/clawpilot/ui/theme/Color.kt
    - app/src/main/java/com/clawpilot/ui/theme/Typography.kt
  modified:
    - gradle/libs.versions.toml (added compose-material-icons-extended, fixed KSP version)

key-decisions:
  - "AGP 8.13.2 instead of 9.1.0: AGP 9.1.0 requires Gradle 9.3.1 but KGP 2.2.0/2.1.21 has no Gradle 9.x variant — incompatible combination"
  - "Kotlin 2.1.21 instead of 2.2.0: KGP 2.2.0 uses gradle813 variant only, fails with Gradle 9.x — downgraded to last stable"
  - "Gradle 8.13 instead of 8.11.1: used most recent 8.x compatible with both AGP 8.13.2 and Kotlin 2.1.21"
  - "KSP removed from plan 01-01: not needed until Room is added (plan 02+)"
  - "Navigation 3 entryProvider uses lambda approach (not DSL entry<T>): the reified inline function was not resolvable at import level; used NavEntry{} lambda directly"
  - "AppRoute implements NavKey interface: required by Navigation 3 for typed backstack"
  - "Per-tab rememberNavBackStack instead of shared: preserves each tab scroll/state independently"

patterns-established:
  - "NavKey pattern: every route sealed interface must implement NavKey + @Serializable"
  - "entryProvider lambda: when route -> NavEntry(route) { content } pattern"
  - "Koin empty module: register only what's needed, add in plan that owns the feature"

requirements-completed: [CONN-04]

# Metrics
duration: 23min
completed: 2026-03-25
---

# Phase 01 Plan 01: Android Project Scaffold Summary

**Gradle project from scratch with Compose BOM 2026.03, Navigation 3 four-tab shell, Koin DI initialization, and Material 3 dynamic-color theme — builds APK in 3min clean.**

## Performance

- **Duration:** 23 min
- **Started:** 2026-03-25T15:10:17Z
- **Completed:** 2026-03-25T15:33:20Z
- **Tasks:** 2
- **Files modified:** 22

## Accomplishments

- Android project scaffolded from zero — no Android Studio wizard, all files hand-crafted
- Gradle version catalog centralizes all dependencies (AGP, Kotlin, Compose BOM, Navigation 3, Koin, OkHttp)
- Bottom navigation shell with 4 independent backstacks (Chat, Dashboard, Crons, Settings)
- Koin DI initialized at app startup via ClawPilotApp.kt
- Material 3 theme with dynamic color on Android 12+ and custom purple fallback
- APK assembles successfully at ~22MB debug build

## Task Commits

1. **Task 1: Gradle scaffold, manifest, resources** - `859e6be` (feat)
2. **Task 2: Kotlin sources — Koin, Navigation 3, theme** - `7946f5c` (feat)

## Files Created/Modified

- `settings.gradle.kts` - Project name ClawPilot, plugin/dep repos
- `build.gradle.kts` - Root build with plugins apply false
- `gradle/libs.versions.toml` - Version catalog: all 15+ library versions
- `gradle/wrapper/gradle-wrapper.properties` - Gradle 8.13
- `app/build.gradle.kts` - App module: compileSdk 36, minSdk 26, Java 17, Compose enabled
- `gradle.properties` - AndroidX, parallel, JVM 2048m
- `app/src/main/AndroidManifest.xml` - ClawPilotApp, MainActivity, INTERNET permission
- `app/src/main/res/values/strings.xml` - App name + 4 tab labels
- `app/src/main/java/com/clawpilot/ClawPilotApp.kt` - Application class with startKoin
- `app/src/main/java/com/clawpilot/MainActivity.kt` - ComponentActivity with enableEdgeToEdge
- `app/src/main/java/com/clawpilot/di/AppModule.kt` - Empty Koin module
- `app/src/main/java/com/clawpilot/ui/navigation/AppRoutes.kt` - sealed interface AppRoute : NavKey
- `app/src/main/java/com/clawpilot/ui/navigation/AppNavHost.kt` - Top-level nav with isPaired placeholder
- `app/src/main/java/com/clawpilot/ui/shell/MainShell.kt` - Scaffold + NavigationBar + NavDisplay
- `app/src/main/java/com/clawpilot/ui/theme/Theme.kt` - ClawPilotTheme with dynamic/static color
- `app/src/main/java/com/clawpilot/ui/theme/Color.kt` - Purple deep color scheme
- `app/src/main/java/com/clawpilot/ui/theme/Typography.kt` - Default M3 typography

## Decisions Made

- AGP 8.13.2 instead of planned 9.1.0: AGP 9.1.0 requires Gradle 9.3.1+, but KGP (all versions including 2.2.0 and 2.1.21) has no Gradle 9.x variant yet — hard incompatibility
- Kotlin 2.1.21 instead of 2.2.0: both fail with Gradle 9.x, 2.1.21 is latest stable with Gradle 8.x support
- KSP removed from plan 01-01: no Room/annotation-processing needed until plan 02+
- Navigation 3 entryProvider as direct lambda (not DSL): `entry<T>{}` DSL function was not importable as standalone; used `when(route) -> NavEntry(route) {}` pattern instead

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 1 - Bug] AGP 9.1.0 requires Gradle 9.3.1 but KGP has no Gradle 9.x variant**
- **Found during:** Task 1 (initial build attempt)
- **Issue:** AGP 9.1.0 minimum Gradle is 9.3.1, but KGP 2.2.0/2.1.21 only has `gradle813` variant — fails with "Cannot add extension with name 'kotlin'" on Gradle 9.x
- **Fix:** Downgraded to AGP 8.13.2 + Gradle 8.13. Both are the latest in their respective 8.x series and fully compatible
- **Files modified:** gradle/libs.versions.toml (agp version), gradle/wrapper/gradle-wrapper.properties (distributionUrl)
- **Verification:** ./gradlew assembleDebug succeeds with BUILD SUCCESSFUL
- **Committed in:** 859e6be (Task 1 commit)

**2. [Rule 1 - Bug] KSP version 2.2.0-1.0.29 not found in any repository**
- **Found during:** Task 1 (initial Gradle configuration)
- **Issue:** The KSP version specified in RESEARCH.md does not exist in Google/Maven repositories
- **Fix:** Removed KSP from plan 01-01 entirely — it's only needed for Room (plan 02+)
- **Files modified:** build.gradle.kts (removed ksp alias), libs.versions.toml (kept version entry for future plans)
- **Committed in:** 859e6be (Task 1 commit)

**3. [Rule 1 - Bug] Navigation 3 entry<T> DSL not importable as standalone function**
- **Found during:** Task 2 (MainShell.kt compilation)
- **Issue:** `import androidx.navigation3.runtime.entry` unresolved — the reified inline `entry<T>` is a method on EntryProviderScope, not a standalone extension
- **Fix:** Used `entryProvider = { route -> when(route) { ... NavEntry(route) { content } } }` pattern instead
- **Files modified:** app/src/main/java/com/clawpilot/ui/shell/MainShell.kt
- **Verification:** compileDebugKotlin passes with no errors
- **Committed in:** 7946f5c (Task 2 commit)

**4. [Rule 2 - Missing Critical] Added compose-material-icons-extended dependency**
- **Found during:** Task 2 (MainShell.kt compilation)
- **Issue:** Icons.Default.Dashboard/Forum/Schedule/Settings not found — missing extended icons dependency
- **Fix:** Added `compose-material-icons-extended` to libs.versions.toml and app/build.gradle.kts
- **Files modified:** gradle/libs.versions.toml, app/build.gradle.kts
- **Committed in:** 7946f5c (Task 2 commit)

---

**Total deviations:** 4 auto-fixed (2 Rule 1 version bugs, 1 Rule 1 API mismatch, 1 Rule 2 missing dep)
**Impact on plan:** Version downgrade is the most significant change. AGP 8.13.2 is production-grade and fully supported. All functionality specified in the plan is delivered. The API mismatch with Navigation 3 DSL uses an equivalent approach that compiles and works correctly.

## Known Stubs

- `AppNavHost.kt`: `val isPaired = true` is hardcoded — plan 01-02 will wire this to CredentialStore to check real pairing state
- Tab screens (Chat, Dashboard, Crons, Settings) show placeholder Text — each replaced in plans 02-05

## Self-Check: PASSED

All files exist, all commits verified (859e6be, 7946f5c), APK generated at app/build/outputs/apk/debug/app-debug.apk.

## Issues Encountered

- AGP 9.1.0 + KGP incompatibility: required 3 build attempts to discover the correct version combination
- Navigation 3 API discovery: had to inspect compiled bytecode to understand actual API surface (no source jars available)

## Next Phase Readiness

- Plan 01-02 (secure storage + pairing) can start immediately — project builds, Koin initialized
- AppNavHost has isPaired placeholder ready for CredentialStore integration
- AppModule ready to receive CredentialStore and KeyStoreManager bindings

---
*Phase: 01-project-setup-connection*
*Completed: 2026-03-25*
