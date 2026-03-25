# Feature Landscape: ClawPilot

**Domain:** Native Android app for controlling OpenClaw multi-agent systems remotely
**Researched:** 2026-03-25
**OpenClaw Version:** 2026.3.13

## 1. OpenClaw Gateway REST/WebSocket API

The Gateway runs on port 18789 (configurable) and serves both an HTTP Control UI and a WebSocket RPC endpoint. All communication from clients (including mobile) uses WebSocket with JSON-RPC-style request/response frames.

### Authentication

| Mode | Method | ClawPilot Relevance |
|------|--------|---------------------|
| `none` | No auth (loopback only) | Not useful for remote |
| `token` | Shared static token in connect params | Current setup (token: `gateway.auth.token`) |
| `password` | Login prompt flow | Alternative for remote |
| `device` | ECDSA keypair + signature (device pairing) | **Best for mobile** -- already used by official Android node |

**Device pairing flow:**
1. App generates ECDSA keypair, stores in local keystore
2. Connects to Gateway WS, sends `connect.challenge` with public key + device metadata
3. Gateway creates pending pairing request (`device.pair.requested` event)
4. Owner approves via CLI (`openclaw devices approve <id>`) or Control UI
5. Gateway issues scoped token (`device.pair.resolved` event)
6. App stores token, uses for all future connections

**Scopes discovered in paired.json:**
- `operator.read` -- read-only access to status, health, sessions, agents
- `operator.write` -- send messages, manage cron, sessions
- `operator.admin` -- config changes, gateway reload
- `operator.approvals` -- approve/deny exec requests
- `operator.pairing` -- approve/deny other devices

**Roles:**
- `operator` -- full dashboard access
- `node` -- device peripheral (camera, canvas, sensors)

### Complete RPC Method Catalog (extracted from gateway source)

#### System & Health
| Method | Purpose | Priority for ClawPilot |
|--------|---------|----------------------|
| `health` | Gateway health + channel probes | HIGH -- dashboard home |
| `status.request` | Full system status (agents, sessions, tokens) | HIGH |
| `gateway.identity.get` | Gateway name, version | MEDIUM |
| `gateway.reload` | Reload config without restart | LOW (admin) |
| `update.available` | Check for OpenClaw updates | LOW |
| `update.run` | Run update | LOW |

#### Agent Management
| Method | Purpose | Priority |
|--------|---------|----------|
| `agents.list` | List all configured agents with models, heartbeat, sessions | HIGH |
| `agents.create` | Add new agent | LOW |
| `agents.update` | Update agent config | MEDIUM |
| `agents.delete` | Remove agent | LOW |
| `agent.identity.get` | Get agent name/emoji/avatar | HIGH |
| `agent.heartbeat` | Trigger heartbeat manually | MEDIUM |
| `agents.defaults.model` | Get/set default model | LOW |
| `agents.defaults.models` | List available models per agent | MEDIUM |
| `agents.files.list` | List workspace files (SOUL.md, AGENTS.md, etc.) | MEDIUM |
| `agents.files.get` | Read workspace file contents | MEDIUM |
| `agents.files.set` | Write workspace file | LOW |

#### Chat & Messaging
| Method | Purpose | Priority |
|--------|---------|----------|
| `chat.send` | Send message to agent (triggers streaming response) | HIGH |
| `chat.history` | Get conversation history for a session | HIGH |
| `chat.abort` | Stop a running agent response | HIGH |
| `chat.inject` | Inject a message into chat history | LOW |
| `chat.completion` | Direct model completion | LOW |
| `chat.completion.chunk` | Streaming chunk event | HIGH (receive) |

#### Session Management
| Method | Purpose | Priority |
|--------|---------|----------|
| `sessions.list` | List all sessions across agents | HIGH |
| `sessions.get` | Get session details (tokens, model, age) | HIGH |
| `sessions.delete` | Delete a session | MEDIUM |
| `sessions.reset` | Reset session history | MEDIUM |
| `sessions.compact` | Compact session to save tokens | LOW |
| `sessions.patch` | Update session overrides | LOW |
| `sessions.preview` | Preview session content | MEDIUM |
| `sessions.usage` | Token usage for session | HIGH |
| `sessions.usage.logs` | Usage log history | MEDIUM |
| `sessions.usage.timeseries` | Usage over time | MEDIUM |

#### Cron Job Management
| Method | Purpose | Priority |
|--------|---------|----------|
| `cron.list` | List all scheduled jobs | HIGH |
| `cron.add` | Create new cron job | HIGH |
| `cron.update` | Edit existing job | HIGH |
| `cron.remove` | Delete job | HIGH |
| `cron.run` | Trigger job immediately (returns `{ enqueued: true, runId }`) | HIGH |
| `cron.runs` | View run history (JSONL-backed) | MEDIUM |
| `cron.status` | Scheduler status | MEDIUM |

**Cron job fields (from jobs.json analysis):**
```json
{
  "id": "uuid",
  "agentId": "string",
  "name": "string",
  "description": "string (optional)",
  "enabled": "boolean",
  "deleteAfterRun": "boolean (optional, for one-shots)",
  "schedule": {
    "kind": "cron|every|at",
    "expr": "cron expression (when kind=cron)",
    "everyMs": "number (when kind=every)",
    "at": "ISO datetime (when kind=at)",
    "tz": "IANA timezone",
    "anchorMs": "number (for every)"
  },
  "sessionTarget": "isolated|last",
  "wakeMode": "now",
  "payload": {
    "kind": "agentTurn",
    "message": "string",
    "timeoutSeconds": "number"
  },
  "delivery": {
    "mode": "none|last|announce",
    "channel": "telegram|slack|last",
    "accountId": "string (optional)",
    "to": "string (optional, e.g. telegram:chatId)"
  },
  "state": {
    "nextRunAtMs": "number",
    "lastRunAtMs": "number",
    "lastRunStatus": "ok|error",
    "lastDurationMs": "number",
    "lastError": "string (optional)",
    "consecutiveErrors": "number"
  }
}
```

#### Execution Approvals
| Method | Purpose | Priority |
|--------|---------|----------|
| `exec.approval.request` | Request approval for command | N/A (agent-initiated) |
| `exec.approval.requested` | Event: approval needed | HIGH (notification) |
| `exec.approval.resolve` | Approve/deny execution | HIGH |
| `exec.approvals.get` | Get approval allowlists | MEDIUM |
| `exec.approvals.set` | Update allowlists | LOW |

#### Device & Node Management
| Method | Purpose | Priority |
|--------|---------|----------|
| `device.pair.list` | List paired + pending devices | MEDIUM |
| `device.pair.approve` | Approve pairing request | MEDIUM |
| `device.pair.reject` | Reject pairing request | MEDIUM |
| `device.pair.remove` | Remove paired device | LOW |
| `device.token.revoke` | Revoke device token | LOW |
| `device.token.rotate` | Rotate device token | LOW |
| `node.list` | List connected nodes | MEDIUM |
| `node.describe` | Node capabilities + permissions | LOW |
| `node.invoke` | Execute command on node | LOW |

#### Channel Management
| Method | Purpose | Priority |
|--------|---------|----------|
| `channels.status` | Status of all channels (Telegram, Discord, etc.) | HIGH |
| `channels.logout` | Logout from channel | LOW |

#### Configuration
| Method | Purpose | Priority |
|--------|---------|----------|
| `config.get` | Read current config | MEDIUM |
| `config.patch` | Partial config update + auto-restart | LOW (dangerous on mobile) |
| `config.schema` | JSON schema for config | LOW |
| `config.apply` | Apply and restart | LOW |

#### Models & TTS
| Method | Purpose | Priority |
|--------|---------|----------|
| `models.list` | List available AI models | MEDIUM |
| `tts.status` | TTS configuration status | LOW |
| `tts.convert` | Convert text to speech | MEDIUM |
| `tts.providers` | List TTS providers | LOW |

#### Skills & Plugins
| Method | Purpose | Priority |
|--------|---------|----------|
| `skills.status` | List installed skills | LOW |
| `skills.install` | Install new skill | LOW |
| `tools.catalog` | List available tools | LOW |

#### Logs
| Method | Purpose | Priority |
|--------|---------|----------|
| `logs.tail` | Stream gateway logs | MEDIUM |

#### Memory
| Method | Purpose | Priority |
|--------|---------|----------|
| `doctor.memory.status` | Memory search index status | LOW |

#### Usage & Costs
| Method | Purpose | Priority |
|--------|---------|----------|
| `usage.cost` | Token cost tracking | HIGH |
| `usage.status` | Usage limits/status | HIGH |


## 2. WebSocket Protocol

### Connection
```
ws://<host>:18789/?token=<auth_token>
```

### Frame Types

**RequestFrame** (client -> gateway):
```json
{
  "type": "request",
  "id": "unique-request-id",
  "method": "rpc.method.name",
  "params": { ... }
}
```

**ResponseFrame** (gateway -> client):
```json
{
  "type": "response",
  "id": "matching-request-id",
  "result": { ... },
  "error": { "message": "...", "code": "..." }
}
```

**EventFrame** (gateway -> client, unsolicited):
```json
{
  "type": "event",
  "event": "event.name",
  "data": { ... }
}
```

### Event Types (streaming/push)

| Event | Payload | ClawPilot Use |
|-------|---------|---------------|
| `chat` | Message updates: delta (streaming), final, aborted | Real-time chat display |
| `agent` | Tool execution, thinking, streaming text | Show agent activity |
| `presence` | Device connect/disconnect | Node status updates |
| `shutdown` | Gateway restart notification | Reconnect handling |
| `health` | Channel status changes | Dashboard updates |
| `cron` | Job completion results | Cron monitoring |
| `exec.approval.requested` | Command needs approval | Push notification trigger |
| `response.output_text.delta` | Streaming text chunks | Live typing display |
| `response.completed` | Agent turn finished | UI state update |
| `response.failed` | Agent turn failed | Error display |
| `device.pair.requested` | New device wants to pair | Approval notification |
| `node.event` | Node-initiated events | Peripheral updates |


## 3. Notification / Delivery System

### Current Delivery Architecture

OpenClaw delivers agent outputs through **channels** (Telegram, Discord, WhatsApp, etc.) configured in `openclaw.json`.

**Delivery modes for cron jobs:**
- `none` -- output stays internal
- `last` -- deliver to the last channel the agent used
- `announce` -- deliver to specified channel + target

**Current setup (Brian's instance):**
- 5 Telegram bot accounts bound to different agents
- Each agent has independent Telegram delivery
- Delivery target = Telegram chat ID (e.g., `telegram:1582581747`)

### Delivery Queue
- Located at `~/.openclaw/delivery-queue/`
- Has a `failed/` subdirectory for failed deliveries
- Jobs that fail delivery get logged (e.g., "requires target <chatId>")

### What ClawPilot Can Add
ClawPilot can register as a **delivery channel** or use the WebSocket event stream to capture all agent outputs in real-time. The app does NOT need to replace Telegram -- it complements it by providing:
1. Real-time event stream (WebSocket events)
2. Exec approval push notifications (critical -- currently only via Control UI)
3. Cron job failure alerts
4. System health degradation alerts


## 4. Existing Mobile Clients

### Official Android Node App (unreleased, buildable from source)
- **Role:** Node (peripheral device) -- NOT operator dashboard
- **Features:** Camera, canvas, chat, voice, device commands
- **Connection:** WebSocket with device pairing
- **Limitation:** Designed as a "node" that the gateway commands, not a dashboard to command the gateway
- **Build:** `./gradlew :app:assemblePlayDebug` from openclaw repo `apps/android/`

### andClaw (third-party, Google Play)
- **Package:** `com.coderred.andclaw`
- **Features:** Runs OpenClaw locally on Android, multi-provider support, channel integration
- **Approach:** Runs the full OpenClaw stack ON the phone (not remote control)
- **Limitation:** Not a remote dashboard -- it IS the gateway

### openclaw-android (AidanPark, GitHub)
- **Approach:** Run OpenClaw on Android via Termux-like environment
- **Same limitation:** Local gateway, not remote control

### openclaw-assistant (yuga-hashimoto, GitHub)
- **Features:** Voice assistant with wake word, system integration
- **Approach:** Voice-first interaction with local/remote gateway

### ClawPhone (marshallrichards, GitHub)
- **Approach:** Tweaks for running OpenClaw on Android smartphones

### Gap Analysis
**NO existing app provides a native Android operator dashboard for remote gateway management.** All existing solutions either:
1. Run OpenClaw locally on the phone (andClaw, openclaw-android)
2. Act as a passive node device (official Android app)
3. Provide voice-only interaction (openclaw-assistant)

**ClawPilot fills the gap:** A native Android app that acts as an **operator** (not node) to remotely monitor and control an existing OpenClaw Gateway.


## 5. OpenClaw Control UI (Mission Control) -- Features to Replicate

The Control UI is a Lit-based SPA served at `http://localhost:18789/`. Features grouped by mobile relevance:

### Must-Have on Mobile (P0)

| Feature | Control UI Implementation | ClawPilot Adaptation |
|---------|--------------------------|---------------------|
| **Dashboard/Health** | Overview tab with gateway health, channel status, agent list | Home screen with cards |
| **Chat** | Full chat with streaming, tool cards, file upload | Native chat UI with Compose |
| **Agent List** | All agents with session counts, model info | Agent cards with quick actions |
| **Cron Management** | Create/edit/run/disable jobs with delivery config | Full CRUD with schedule picker |
| **Exec Approvals** | Inline approval dialogs for command execution | Push notification + approve/deny |
| **Session Browser** | List sessions per agent, preview, delete | Expandable session list |

### Should-Have on Mobile (P1)

| Feature | Control UI Implementation | ClawPilot Adaptation |
|---------|--------------------------|---------------------|
| **Usage/Costs** | Token usage per session, cost tracking | Usage dashboard with charts |
| **Logs Tail** | Live log stream with level filtering | Scrollable log viewer |
| **Channel Status** | Per-account Telegram/Discord/etc status | Status indicators |
| **Agent Files** | Edit SOUL.md, AGENTS.md, MEMORY.md | Read-only viewer, basic editor |
| **Device Management** | Approve/reject pairing, list devices | Device list with actions |

### Nice-to-Have on Mobile (P2)

| Feature | Control UI Implementation | ClawPilot Adaptation |
|---------|--------------------------|---------------------|
| **Config Editor** | JSON schema-aware editor with diff preview | Read-only viewer |
| **Skills Management** | Install/enable/disable skills | Status view only |
| **Node Capabilities** | List nodes, invoke commands | View only |
| **Model Selection** | Per-session model override | Dropdown in chat |


## 6. Firebase Cloud Messaging Integration

### Current State
- **Official support:** NOT yet implemented
- **GitHub Issue:** [#30138](https://github.com/openclaw/openclaw/issues/30138) -- open, no assignees, 0 comments (filed Feb 28, 2026)
- **Gateway has `push.test` RPC method** -- exists in code, suggesting push infrastructure is partially built

### What the Gateway Supports Now
- WebSocket event streaming (only while connected)
- No native push when app is backgrounded
- The `node.pending.*` methods (`enqueue`, `pull`, `ack`, `drain`) suggest a queuing system for offline nodes

### ClawPilot Push Strategy

**Option A: Gateway-side FCM (ideal, not yet available)**
- Wait for #30138 to land
- Gateway sends FCM data messages when events need attention
- App wakes, shows notification, optionally reconnects WS

**Option B: Proxy service on the server (recommended for now)**
- Lightweight daemon alongside the gateway
- Listens to gateway WebSocket events
- Forwards critical events via FCM to registered ClawPilot instances
- Events to forward:
  - `exec.approval.requested` -- command needs approval
  - `cron` job failures
  - Channel disconnections
  - Agent errors
  - Heartbeat failures

**Option C: Persistent WebSocket with Android foreground service**
- Keep WS connected via foreground service
- Generate local notifications from events
- Battery cost is moderate but not ideal
- Works without any server-side changes

**Recommended:** Start with Option C (foreground service) for MVP, build Option B proxy as Phase 2, migrate to Option A when #30138 lands.


## 7. Brian's Specific Instance -- Current Agent Topology

Understanding the actual deployment helps prioritize features:

### Active Agents (21 total)

| Agent | Role | Model | Heartbeat | Sessions |
|-------|------|-------|-----------|----------|
| **Oris** (main) | Orchestrator | Claude Sonnet 4.6 | 30m to Telegram | 76 |
| **Cacho** | Accounting/invoices | Claude Sonnet 4.6 | 30m to Telegram | 182 |
| **Roxy** | Marketing/Instagram | Claude Sonnet 4.6 | disabled | 14 |
| **Tech** | DevOps/technical | Claude Opus 4.6 | disabled | 7 |
| **Forge** | Research coordinator | Claude Sonnet 4.6 | disabled | 6 |
| **Legal Advisor** | HORECA law | Claude Sonnet 4.6 | disabled | 3 |
| **Hercules** | Gym/nutrition | Claude Sonnet 4.6 | disabled | 6 |
| **Ink** | Blog writer | Claude Sonnet 4.6 | disabled | 1 |
| **Chat Analyzer** | Session analysis | Gemini 2.0 Flash | disabled | 3 |
| **Memory Manager** | Memory consolidation | Claude Sonnet 4.6 | disabled | 1 |
| **Arquitecto** | Architecture | Claude Sonnet 4.6 | disabled | 1 |
| 5x Scouts | Web/X/Social/GitHub/ClawHub | Gemini Flash | disabled | 0 |
| 5x Tech Subs | Doctor/Optimizer/Architect/SysAdmin/Tester | Various | disabled | 0 |

### Active Cron Jobs (10)
- Oris heartbeat (every 3h)
- Chat analysis daily
- Memory consolidation weekly
- System health daily (Doctor)
- Cost optimization weekly (Optimizer)
- Weekly research (Forge + 5 scouts)
- ClawHub midweek scan
- Ink blog publisher (4x/week)
- 2x one-shot reminders

### Channels
- 5 Telegram bot accounts (Oris, Cacho, Roxy, Legal, Hercules)
- All restricted to Brian's Telegram ID (1582581747)


## Feature Dependencies

```
Device Pairing (connect.challenge)
  --> Agent List (agents.list)
  --> Dashboard (health + status.request)
    --> Chat (chat.send + chat.history + events)
    --> Cron Manager (cron.*)
    --> Session Browser (sessions.*)
    --> Exec Approvals (exec.approval.*)
      --> Push Notifications (FCM/foreground service)
        --> Usage Dashboard (usage.* + sessions.usage.*)
        --> Log Viewer (logs.tail)
```

## Table Stakes

Features users expect in a gateway control app. Missing = feels incomplete.

| Feature | Why Expected | Complexity | Notes |
|---------|--------------|------------|-------|
| Gateway connection + auth | Can't do anything without it | Medium | Device pairing ECDSA |
| Agent dashboard | See what's running | Low | `agents.list` + `health` |
| Chat with any agent | Primary interaction | High | Streaming, tool cards |
| Cron job management | #1 daily task for Brian | Medium | Full CRUD + run now |
| Exec approval from phone | Currently stuck at desktop | Low | Push + approve/deny |
| Session browser | See conversation history | Medium | Per-agent, searchable |
| Channel status | Are bots connected? | Low | `channels.status` |

## Differentiators

Features that set ClawPilot apart from just using Telegram/browser.

| Feature | Value Proposition | Complexity | Notes |
|---------|-------------------|------------|-------|
| Unified multi-agent view | See ALL agents in one screen (vs 5 Telegram bots) | Medium | Agent cards with status |
| Push notifications for approvals | Never miss exec approval requests | Medium | FCM or foreground service |
| Cron visual scheduler | Create/edit cron jobs natively (no JSON) | High | Schedule picker, delivery config |
| Usage/cost dashboard | Track spend across all agents | Medium | Charts with timeseries |
| Offline queue | Queue messages when disconnected | Medium | Local DB + sync |
| Quick actions | One-tap heartbeat, restart job, abort agent | Low | FAB or action buttons |
| Widget | Home screen widget showing agent status | Medium | Android widget API |

## Anti-Features

Features to explicitly NOT build.

| Anti-Feature | Why Avoid | What to Do Instead |
|--------------|-----------|-------------------|
| Config JSON editor | Too dangerous on mobile, easy to break gateway | Read-only config viewer |
| Full node control | ClawPilot is operator, not node | Separate concern |
| Running OpenClaw locally | andClaw already does this | Focus on remote control |
| Agent creation wizard | Rare task, complex UI | Link to Control UI |
| Skill installation | Needs file system access | View status only |
| Multi-gateway management | Over-engineering for v1 | Single gateway, add later |


## MVP Recommendation

Prioritize for Phase 1:
1. **Connection + Device Pairing** -- ECDSA keypair, gateway discovery (manual + mDNS)
2. **Dashboard** -- Health, agents list, channel status
3. **Chat** -- Talk to any agent with streaming responses
4. **Cron Manager** -- List, enable/disable, run now, create simple jobs
5. **Exec Approvals** -- Push notification + approve/deny actions

Defer:
- Usage dashboard: needs UI charting library, not critical for daily use
- Log viewer: nice but not essential on phone
- Session browser with preview: complex, can use Control UI
- Agent file editor: use Control UI for this
- Widget: requires stable API layer first


## Sources

- Local OpenClaw installation v2026.3.13 analysis (openclaw.json, gateway health, CLI help, source extraction)
- [OpenClaw Docs](https://docs.openclaw.ai) -- features, multi-agent, Control UI, nodes, platforms
- [OpenClaw Docs: Features](https://docs.openclaw.ai/concepts/features) -- HIGH confidence
- [OpenClaw Docs: Platforms/Android](https://docs.openclaw.ai/platforms/android) -- HIGH confidence
- [OpenClaw Docs: Control UI](https://docs.openclaw.ai/web/control-ui) -- HIGH confidence
- [DeepWiki: Control UI](https://deepwiki.com/openclaw/openclaw/7-control-ui) -- MEDIUM confidence
- [GitHub Issue #30138: FCM/APNs push wake](https://github.com/openclaw/openclaw/issues/30138) -- HIGH confidence
- [andClaw on Google Play](https://play.google.com/store/apps/details?id=com.coderred.andclaw) -- MEDIUM confidence
- [GitHub: openclaw-android](https://github.com/AidanPark/openclaw-android) -- MEDIUM confidence
- [GitHub: openclaw-assistant](https://github.com/yuga-hashimoto/openclaw-assistant) -- MEDIUM confidence
- [GitHub: ClawPhone](https://github.com/marshallrichards/ClawPhone) -- LOW confidence
- Gateway RPC methods extracted from `/home/casa/.local/lib/node_modules/openclaw/dist/gateway-cli-*.js` -- HIGH confidence
