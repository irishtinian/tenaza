# Roadmap: ClawPilot

**Created:** 2026-03-25
**Milestone:** v1.0 — MVP Release

## Phase Overview

| Phase | Name | Requirements | Goal |
|-------|------|-------------|------|
| 1 | Project Setup & Connection | CONN-01..07, SECR-01..03 | App connects and pairs with gateway securely |
| 2 | Dashboard | DASH-01..05 | Home screen shows system status at a glance |
| 3 | Chat | CHAT-01..07 | Talk to any agent with streaming responses |
| 4 | Notifications & Approvals | NOTF-01..07 | Never miss an exec approval or critical event |
| 5 | Cron Management | CRON-01..07 | Full control over scheduled jobs |
| 6 | Polish & Release | SECR-04 | Biometric lock, Play Store submission, GitHub release |

## Phase Details

### Phase 1: Project Setup & Connection
**Goal:** Scaffold the Android project and implement secure gateway pairing. User can scan QR, connect, and see "connected" status.

**Requirements:** CONN-01, CONN-02, CONN-03, CONN-04, CONN-05, CONN-06, CONN-07, SECR-01, SECR-02, SECR-03

**Plans:** 5 plans

Plans:
- [x] 01-01-PLAN.md — Android project scaffold (Gradle, Compose, Koin DI, Navigation 3 shell)
- [x] 01-02-PLAN.md — Secure storage (ECDSA Keystore, DataStore + Tink encrypted credentials)
- [ ] 01-03-PLAN.md — WebSocket connection manager (OkHttp, reconnect, TLS, frame parsing)
- [ ] 01-04-PLAN.md — QR scanner + manual URL entry (CameraX, ML Kit, PairingViewModel)
- [ ] 01-05-PLAN.md — Integration wiring (connection status bar, conditional nav, unpair flow)

**Scope:**
- Android project scaffold (Kotlin, Compose, Gradle version catalog)
- App architecture: modules, DI (Koin), navigation shell (Navigation 3)
- WebSocket client (OkHttp) with connection manager
- ECDSA keypair generation + Android Keystore storage
- QR scanner (CameraX + ML Kit) for pairing
- Manual gateway URL entry fallback
- Secure token storage (DataStore + Tink)
- Connection state management (connected/disconnected/reconnecting)
- Auto-reconnect with exponential backoff + jitter
- TLS enforcement (wss:// only for non-loopback)
- Bottom navigation shell (Chat, Dashboard, Crons, Settings)

**Success criteria:**
- App builds and runs on Android 8+
- User scans QR → device pairs with gateway
- Connection status visible and accurate
- Reconnects automatically after network drop
- Token stored encrypted, survives app restart

---

### Phase 2: Dashboard
**Goal:** Home screen that shows gateway health, all agents with status, and channel connectivity — all updating in real-time.

**Requirements:** DASH-01, DASH-02, DASH-03, DASH-04, DASH-05

**Scope:**
- Gateway health card (online/degraded/offline) via `health` RPC
- Agent list with cards (name, emoji, model, session count) via `agents.list`
- Channel status indicators via `channels.status`
- Real-time updates via WebSocket events
- Pull-to-refresh as manual fallback
- Tap agent → navigate to chat (stub until Phase 3)
- Room database for offline cache of agent/channel state

**Success criteria:**
- Dashboard loads and displays all agents from Brian's instance (21 agents)
- Health status updates when gateway state changes
- Channel status shows connected/disconnected for Telegram bots
- Data persists offline and refreshes on reconnect

---

### Phase 3: Chat
**Goal:** Full chat with any agent — send messages, see streaming responses, view history, abort responses.

**Requirements:** CHAT-01, CHAT-02, CHAT-03, CHAT-04, CHAT-05, CHAT-06, CHAT-07

**Scope:**
- Chat UI with message bubbles (user/agent), agent avatar/emoji
- Send message via `chat.send` RPC
- Streaming response display via `response.output_text.delta` events
- Abort button via `chat.abort` RPC
- Conversation history via `chat.history` RPC
- Room cache for conversations (offline viewing)
- Agent switcher (bottom sheet or drawer)
- Thinking/tool execution status indicators
- LazyColumn with stable keys, optimized for streaming text
- Keyboard handling, scroll-to-bottom on new messages

**Success criteria:**
- User can chat with Oris, Tech, or any agent
- Responses stream in token-by-token
- Abort stops the response immediately
- History loads on re-entering a chat
- Conversations cached for offline viewing
- No jank during streaming (60fps)

---

### Phase 4: Notifications & Approvals
**Goal:** Foreground service keeps WebSocket alive, surfaces exec approvals and critical events as push notifications with action buttons.

**Requirements:** NOTF-01, NOTF-02, NOTF-03, NOTF-04, NOTF-05, NOTF-06, NOTF-07

**Scope:**
- Android foreground service with persistent notification ("ClawPilot connected")
- Notification channels: Approvals (high priority), Alerts (default), Activity (low)
- Exec approval notifications with Approve/Reject action buttons
- `exec.approval.resolve` RPC from notification action
- Cron failure notifications via `cron` events
- Channel disconnect notifications via `health` events
- Deep-link from notification to relevant screen
- Notification preferences screen (toggle per type)
- Battery optimization: WebSocket only when service running, proper Doze handling

**Success criteria:**
- Service runs reliably in background
- Exec approval notification appears within seconds of request
- Approve/Reject from notification works without opening app
- Tapping notification opens correct screen
- Battery impact acceptable (< 5% per day)

---

### Phase 5: Cron Management
**Goal:** Full CRUD for cron jobs with native schedule picker — create, edit, enable/disable, run now, view history.

**Requirements:** CRON-01, CRON-02, CRON-03, CRON-04, CRON-05, CRON-06, CRON-07

**Scope:**
- Cron job list with status cards (enabled/disabled, next run, last status, error indicator)
- Enable/disable toggle via `cron.update`
- "Run now" button via `cron.run`
- Run history viewer via `cron.runs`
- Create job form: agent picker, message, schedule type (cron/interval/one-shot), timezone, delivery config
- Edit job form (same as create, pre-populated)
- Delete with confirmation via `cron.remove`
- Schedule picker: cron expression builder, interval selector, date-time picker for one-shots
- Delivery configuration: mode (none/last/announce), channel picker, target

**Success criteria:**
- All 10 cron jobs from Brian's instance display correctly
- User can create a new cron job entirely from the app
- Enable/disable and "run now" work immediately
- Run history shows past executions with status
- Schedule picker produces valid cron expressions

---

### Phase 6: Polish & Release
**Goal:** Final polish, biometric lock, Play Store compliance, and dual distribution (Play Store + GitHub).

**Requirements:** SECR-04

**Scope:**
- Biometric lock (BiometricPrompt API) — optional, configured in settings
- App icon and branding (ClawPilot logo, splash screen)
- Play Store listing (screenshots, description, AI disclosure)
- Play Store AI content policy compliance (disclosures, reporting)
- GitHub releases with APK (CI/CD via GitHub Actions)
- ProGuard/R8 minification
- Crash reporting (Firebase Crashlytics or Sentry)
- OEM battery optimization whitelist wizard (Xiaomi, Samsung, etc.)
- Edge cases: large agent lists, long conversations, poor connectivity
- Performance audit: startup time, memory, battery

**Success criteria:**
- Play Store submission accepted
- APK available on GitHub releases
- Biometric lock works on supported devices
- App handles 21+ agents without performance issues
- Crash-free rate > 99%

---

## Dependency Graph

```
Phase 1 (Connection)
  |---> Phase 2 (Dashboard)
  |       └---> Phase 3 (Chat)
  |               └---> Phase 4 (Notifications)
  |                       └---> Phase 5 (Crons)
  └---------------------------> Phase 6 (Polish)
```

Phases are sequential — each builds on the WebSocket/connection infrastructure from Phase 1.

---
*Roadmap created: 2026-03-25*
*Last updated: 2026-03-25 after Phase 1 planning*
