# State: ClawPilot

## Project Reference

See: .planning/PROJECT.md (updated 2026-03-25)

**Core value:** Chat with your AI agents and get instant push notifications when they need you — from anywhere, on your phone.
**Current focus:** Phase 1 — Project Setup & Connection

## Milestone: v1.0 — MVP Release

| Phase | Name | Status | Plans |
|-------|------|--------|-------|
| 1 | Project Setup & Connection | ○ Pending | 0/? |
| 2 | Dashboard | ○ Pending | 0/? |
| 3 | Chat | ○ Pending | 0/? |
| 4 | Notifications & Approvals | ○ Pending | 0/? |
| 5 | Cron Management | ○ Pending | 0/? |
| 6 | Polish & Release | ○ Pending | 0/? |

Progress: ░░░░░░░░░░ 0%

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

---
*Last updated: 2026-03-25 after project initialization*
