---
gsd_state_version: 1.0
milestone: v1.0
milestone_name: milestone
status: unknown
last_updated: "2026-03-25T15:35:19.449Z"
progress:
  total_phases: 6
  completed_phases: 0
  total_plans: 5
  completed_plans: 2
  percent: 20
---

# State: ClawPilot

## Project Reference

See: .planning/PROJECT.md (updated 2026-03-25)

**Core value:** Chat with your AI agents and get instant push notifications when they need you — from anywhere, on your phone.
**Current focus:** Phase 01 — Project Setup & Connection

## Milestone: v1.0 — MVP Release

| Phase | Name | Status | Plans |
|-------|------|--------|-------|
| 1 | Project Setup & Connection | ○ Pending | 0/? |
| 2 | Dashboard | ○ Pending | 0/? |
| 3 | Chat | ○ Pending | 0/? |
| 4 | Notifications & Approvals | ○ Pending | 0/? |
| 5 | Cron Management | ○ Pending | 0/? |
| 6 | Polish & Release | ○ Pending | 0/? |

Progress: [██░░░░░░░░] 20%

## Research Completed

- ✓ FEATURES.md — Gateway API (90+ RPC methods), existing clients, Control UI mapping
- ✓ STACK.md — Kotlin 2.2, Compose BOM 2026.03, Ktor 3.4, Navigation 3, Koin 4.1, Room 3.0
- ✓ ARCHITECTURE.md — Chat UI patterns, WebSocket lifecycle, pairing flow, offline-first
- ✓ PITFALLS.md — Play Store AI policy, battery drain, FCM on Chinese OEMs, security

## Key Technical Decisions

- WebSocket: OkHttp (not Ktor) — battle-tested on Android
- DI: Koin 4.1 (not Hilt) — less ceremony for this project scope
- Navigation: Navigation 3 (stable Dec 2025)
- Secure storage: DataStore + Tink (EncryptedSharedPreferences deprecated)
- Push strategy: Foreground service (v1) → FCM proxy daemon (v2)
- Auth: ECDSA device pairing (matches official OpenClaw Android app pattern)
- Tink over EncryptedSharedPreferences for credential storage (deprecated API)
- ECDSA P-256 keypair in Android Keystore with userAuthenticationRequired=false for device key
- Gradle 9.3.1 required for AGP 9.1.0 (wrapper updated)

## Completed Plans

- ✓ 01-01: Android scaffold — AGP 8.13.2 + Kotlin 2.1.21 + Compose BOM 2026.03 + Navigation 3 shell + Koin DI (commits: 859e6be, 7946f5c)
- ✓ 01-02: ECDSA KeyStoreManager + Tink CredentialStore + AppPreferences (commits: 76cfd2b, b939eb1)

## Key Technical Decisions (Updated)

- AGP 8.13.2 instead of 9.1.0: AGP 9.1.0 requires Gradle 9.3.1 but KGP has no Gradle 9.x variant
- Kotlin 2.1.21 instead of 2.2.0: KGP 2.2.0 incompatible with Gradle 9.x
- Gradle 8.13 as wrapper: latest 8.x series, compatible with both AGP and KGP

---
*Last updated: 2026-03-25 after Plan 01-01 completion*
