# Requirements: ClawPilot

**Defined:** 2026-03-25
**Core Value:** Chat with your AI agents and get instant push notifications when they need you — from anywhere, on your phone.

## v1 Requirements

### Connection & Pairing

- [ ] **CONN-01**: User can pair device with gateway by scanning QR code displayed on gateway Control UI
- [x] **CONN-02**: Pairing uses ECDSA keypair generated on device, stored in Android Keystore
- [x] **CONN-03**: App stores gateway URL + scoped token securely (DataStore + Tink)
- [x] **CONN-04**: App shows connection status (connected/disconnected/reconnecting) at all times
- [ ] **CONN-05**: App auto-reconnects with exponential backoff when connection drops
- [ ] **CONN-06**: User can manually enter gateway URL as fallback to QR scanning
- [ ] **CONN-07**: User can unpair/disconnect from gateway

### Dashboard

- [ ] **DASH-01**: Home screen shows gateway health status (online/degraded/offline)
- [ ] **DASH-02**: Home screen shows list of all agents with name, emoji, model, and active session count
- [ ] **DASH-03**: Home screen shows channel status (Telegram, Discord, etc. — connected/disconnected)
- [ ] **DASH-04**: Dashboard data refreshes via WebSocket events in real-time
- [ ] **DASH-05**: Tapping an agent navigates to chat with that agent

### Chat

- [ ] **CHAT-01**: User can send text messages to any agent
- [ ] **CHAT-02**: Agent responses stream in real-time (token by token)
- [ ] **CHAT-03**: User can abort a running agent response
- [ ] **CHAT-04**: Chat displays conversation history (loaded from gateway)
- [ ] **CHAT-05**: Chat history is cached locally in Room for offline viewing
- [ ] **CHAT-06**: User can switch between agent conversations
- [ ] **CHAT-07**: Chat shows agent thinking/tool execution status indicators

### Notifications

- [ ] **NOTF-01**: App runs foreground service to maintain WebSocket connection for notifications
- [ ] **NOTF-02**: User receives notification when an agent requests exec approval
- [ ] **NOTF-03**: User can approve or reject exec approval directly from the notification action buttons
- [ ] **NOTF-04**: User receives notification when a cron job fails
- [ ] **NOTF-05**: User receives notification when a channel disconnects
- [ ] **NOTF-06**: Tapping a notification deep-links to the relevant screen (agent chat, cron, etc.)
- [ ] **NOTF-07**: User can configure which notification types are enabled

### Cron Management

- [ ] **CRON-01**: User can view list of all cron jobs with status (enabled/disabled, next run, last status)
- [ ] **CRON-02**: User can enable/disable a cron job
- [ ] **CRON-03**: User can trigger a cron job to run immediately
- [ ] **CRON-04**: User can view run history for a cron job
- [ ] **CRON-05**: User can create a new cron job with schedule picker (cron expression, interval, or one-shot)
- [ ] **CRON-06**: User can edit an existing cron job
- [ ] **CRON-07**: User can delete a cron job

### Security

- [ ] **SECR-01**: All gateway communication uses TLS (wss://)
- [x] **SECR-02**: Auth token stored encrypted (DataStore + Tink)
- [x] **SECR-03**: Only one device can be paired per gateway (enforced app-side)
- [ ] **SECR-04**: App supports optional biometric lock (fingerprint/face) to open

## v2 Requirements

### Usage & Costs

- **COST-01**: Dashboard shows total token usage across all agents
- **COST-02**: Per-agent token usage breakdown
- **COST-03**: Cost tracking with timeseries chart (daily/weekly/monthly)
- **COST-04**: Budget alerts when spend exceeds threshold

### Session Management

- **SESS-01**: User can browse sessions per agent
- **SESS-02**: User can preview session content
- **SESS-03**: User can delete or reset sessions
- **SESS-04**: Session token usage displayed

### Agent Management

- **AGNT-01**: User can view agent workspace files (SOUL.md, MEMORY.md) read-only
- **AGNT-02**: User can trigger agent heartbeat manually
- **AGNT-03**: User can change agent model
- **AGNT-04**: Agent activity timeline

### Advanced Features

- **ADVN-01**: Real-time log streaming viewer
- **ADVN-02**: Home screen widget showing agent status
- **ADVN-03**: FCM push via proxy daemon (replace foreground service)
- **ADVN-04**: Device management (approve/reject other devices)
- **ADVN-05**: Themes and customization
- **ADVN-06**: TTS playback for agent responses

## Out of Scope

| Feature | Reason |
|---------|--------|
| Backend/relay cloud service | v3+, users expose their own gateway |
| iOS version | Android first, evaluate after traction |
| Running OpenClaw on phone | andClaw already does this, different product |
| Web version | Covered by Mission Control |
| Multi-gateway in v1 | Over-engineering, single gateway sufficient |
| Agent creation from mobile | Too complex for mobile UI, use Control UI |
| Config JSON editor | Dangerous on mobile, easy to break gateway |
| Skill installation | Needs filesystem access, use Control UI |
| SOUL/file editing | Too risky on small screen, read-only in v2 |

## Traceability

| Requirement | Phase | Status |
|-------------|-------|--------|
| CONN-01 | Phase 1 | Pending |
| CONN-02 | Phase 1 | Complete |
| CONN-03 | Phase 1 | Complete |
| CONN-04 | Phase 1 | Complete |
| CONN-05 | Phase 1 | Pending |
| CONN-06 | Phase 1 | Pending |
| CONN-07 | Phase 1 | Pending |
| DASH-01 | Phase 2 | Pending |
| DASH-02 | Phase 2 | Pending |
| DASH-03 | Phase 2 | Pending |
| DASH-04 | Phase 2 | Pending |
| DASH-05 | Phase 2 | Pending |
| CHAT-01 | Phase 3 | Pending |
| CHAT-02 | Phase 3 | Pending |
| CHAT-03 | Phase 3 | Pending |
| CHAT-04 | Phase 3 | Pending |
| CHAT-05 | Phase 3 | Pending |
| CHAT-06 | Phase 3 | Pending |
| CHAT-07 | Phase 3 | Pending |
| NOTF-01 | Phase 4 | Pending |
| NOTF-02 | Phase 4 | Pending |
| NOTF-03 | Phase 4 | Pending |
| NOTF-04 | Phase 4 | Pending |
| NOTF-05 | Phase 4 | Pending |
| NOTF-06 | Phase 4 | Pending |
| NOTF-07 | Phase 4 | Pending |
| CRON-01 | Phase 5 | Pending |
| CRON-02 | Phase 5 | Pending |
| CRON-03 | Phase 5 | Pending |
| CRON-04 | Phase 5 | Pending |
| CRON-05 | Phase 5 | Pending |
| CRON-06 | Phase 5 | Pending |
| CRON-07 | Phase 5 | Pending |
| SECR-01 | Phase 1 | Pending |
| SECR-02 | Phase 1 | Complete |
| SECR-03 | Phase 1 | Complete |
| SECR-04 | Phase 6 | Pending |

**Coverage:**
- v1 requirements: 32 total
- Mapped to phases: 32
- Unmapped: 0 ✓

---
*Requirements defined: 2026-03-25*
*Last updated: 2026-03-25 after research synthesis*
