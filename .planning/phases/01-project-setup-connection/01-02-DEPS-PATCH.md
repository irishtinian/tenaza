# Dependency Patch — Plan 01-02

Plan 01-02 requires these dependencies added to `app/build.gradle.kts`.
Plan 01-01 owns the build.gradle.kts file. These must be merged in during integration.

## Add to `app/build.gradle.kts` implementation block

```kotlin
// Encrypted credential storage (Plan 01-02)
implementation(libs.datastore.preferences)
implementation(libs.tink)
```

## Verify in `gradle/libs.versions.toml`

The following must exist (Plan 01-01 owns this file):

```toml
[versions]
datastore = "1.2.1"
tink = "1.16.0"

[libraries]
datastore-preferences = { group = "androidx.datastore", name = "datastore-preferences", version.ref = "datastore" }
tink = { group = "com.google.crypto.tink", name = "tink-android", version.ref = "tink" }
```

## Files that use these dependencies

- `app/src/main/java/com/clawpilot/data/local/prefs/CredentialStore.kt`
  - imports: `com.google.crypto.tink.*`, `androidx.datastore.preferences.*`
- `app/src/main/java/com/clawpilot/data/local/prefs/AppPreferences.kt`
  - imports: `androidx.datastore.preferences.*`
