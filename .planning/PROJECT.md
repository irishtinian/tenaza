# ClawPilot

## What This Is

Native Android app for controlling OpenClaw multi-agent systems remotely. A mobile command center that combines chat, notifications, and agent monitoring in one app — replacing Telegram/Discord as the primary mobile interface for OpenClaw users. Commercial product distributed via Play Store and GitHub releases.

## Core Value

Chat with your AI agents and get instant push notifications when they need you — from anywhere, on your phone.

## Requirements

### Validated

(None yet — ship to validate)

### Active

- [ ] Secure device pairing via QR code (one device per gateway)
- [ ] Chat with any OpenClaw agent (text, streaming responses)
- [ ] Push notifications for agent events (completion, failure, approval needed)
- [ ] Human-in-the-loop: approve/reject actions from notification
- [ ] Dashboard: agents list with status, last activity, model info
- [ ] Connection management: gateway URL, auth token, connection status
- [ ] Offline cache: recent conversations and agent states
- [ ] v2: Metrics and cost tracking (tokens, API spend, per-agent breakdown)
- [ ] v2: Cron management (view, create, edit, delete scheduled tasks)
- [ ] v2: Real-time log streaming
- [ ] v2: Agent lifecycle management (hibernate, activate, edit config)
- [ ] v2: Themes and customization

### Out of Scope

- Backend/relay cloud service — users expose their own gateway (v3+)
- iOS version — Android first, iOS later if traction
- Running OpenClaw gateway on the phone — this is a remote control, not a host
- Web version — already covered by Mission Control
- Multi-gateway management in v1 — one gateway per device for now
- Agent creation/SOUL editing from mobile — too complex for mobile UI

## Context

- OpenClaw Gateway exposes REST API on port 18789 with token auth
- WebSocket support for real-time streaming
- Existing ecosystem: ClawApp (React Native), ClawControl (cross-platform), Mission Control (web) — none combine chat + dashboard + push natively
- Market gap: zero native Android apps with full agent control + push notifications
- Brian has working OpenClaw setup on Ubuntu (14 agents, crons, watchers) — primary dogfood environment
- Mission Control web dashboard already running locally — reference for features
- Target audience: OpenClaw power users (technical, comfortable with self-hosting)

## Constraints

- **Tech stack**: Kotlin + Jetpack Compose — native Android, no cross-platform
- **Min SDK**: API 26 (Android 8.0) — covers 95%+ of active devices
- **Distribution**: Play Store + GitHub releases (APK sideload)
- **License**: Apache 2.0
- **Auth**: Device pairing via QR (gateway generates, app scans) — no accounts/passwords
- **Connectivity**: Requires user to expose gateway (Tailscale, Cloudflare Tunnel, VPS, LAN)
- **Push**: Firebase Cloud Messaging — requires gateway-side component to forward events
- **No backend**: App connects directly to user's gateway, no intermediary server

## Key Decisions

| Decision | Rationale | Outcome |
|----------|-----------|---------|
| App-only, no relay backend | Faster MVP, zero infra cost, OpenClaw users are technical | — Pending |
| Native Android (not cross-platform) | Best performance, Compose ecosystem, Brian's expertise from rockPOS | — Pending |
| One device per gateway | Security simplicity, prevents unauthorized access | — Pending |
| Apache 2.0 license | Android ecosystem standard, patent protection, allows commercial use | — Pending |
| Play Store + GitHub releases | Maximum reach: store for discoverability, GitHub for sideloaders | — Pending |
| v1 = chat + notifications + basic dashboard | Replace Telegram first, then add power features | — Pending |

---
*Last updated: 2026-03-25 after initial project definition*
