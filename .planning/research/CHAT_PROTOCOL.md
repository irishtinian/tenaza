# OpenClaw Gateway Chat Protocol

**Researched:** 2026-03-25 (live probed against gateway v2026.3.23-2)
**Gateway:** ws://localhost:18789 (protocol v3)
**Confidence:** HIGH (all schemas from TypeBox + live-verified)

---

## 1. Transport Layer

All communication is over a single WebSocket connection using JSON text frames.

### Frame Types

```
Client -> Server:  RequestFrame   { type: "req",   id: string, method: string, params?: any }
Server -> Client:  ResponseFrame  { type: "res",   id: string, ok: boolean, payload?: any, error?: ErrorShape }
Server -> Client:  EventFrame     { type: "event", event: string, payload?: any, seq?: int, stateVersion?: StateVersion }
```

### ErrorShape

```json
{
  "code": "string",         // e.g. "INVALID_REQUEST", "UNAVAILABLE"
  "message": "string",
  "details": any,           // optional
  "retryable": boolean,     // optional
  "retryAfterMs": int       // optional
}
```

### StateVersion (on events)

```json
{
  "presence": int,
  "health": int
}
```

---

## 2. Connection Handshake

### Step 1: Server sends challenge event (immediately on WS open)

```json
{
  "type": "event",
  "event": "connect.challenge",
  "payload": {
    "nonce": "64c09ba6-44f2-4d08-9b3b-7f776e94cb11",
    "ts": 1774465886098
  }
}
```

### Step 2: Client sends connect request

```json
{
  "type": "req",
  "id": "c1",
  "method": "connect",
  "params": {
    "minProtocol": 3,
    "maxProtocol": 3,
    "role": "operator",
    "scopes": ["operator.admin", "operator.read", "operator.write", "operator.approvals"],
    "client": {
      "id": "openclaw-android",        // enum: cli|webchat-ui|openclaw-control-ui|webchat|gateway-client|openclaw-macos|openclaw-ios|openclaw-android|node-host|test|fingerprint|openclaw-probe
      "displayName": "ClawPilot",
      "version": "0.1.0",
      "platform": "android",
      "deviceFamily": "samsung",        // optional
      "modelIdentifier": "SM-S928B",    // optional
      "mode": "ui",                     // enum: node|cli|webchat|test|ui|backend|probe
      "instanceId": "uuid"             // optional, for multi-instance dedup
    },
    "auth": {
      "token": "<gateway_token>",       // primary auth for local mode
      "deviceToken": "<device_token>",  // alternative: persisted after first pairing
      "bootstrapToken": "<string>",     // alternative: one-time bootstrap
      "password": "<string>"            // alternative: password auth
    },
    "device": {                         // optional, for device pairing
      "id": "<sha256_of_public_key>",
      "publicKey": "<base64url_ed25519_pubkey>",
      "signature": "<base64url_ed25519_sig>",
      "signedAt": 1774465886000,
      "nonce": "<from_challenge>"
    },
    "locale": "es",                     // optional
    "caps": ["push.fcm"],              // optional capability hints
    "permissions": {}                   // optional
  }
}
```

**Signature payload format:**
```
v3|{deviceId}|{clientId}|{mode}|{role}|{scopes_csv}|{signedAt}|{token}|{nonce}|{platform}|{deviceFamily}
```

### Step 3: Server responds with hello-ok

```json
{
  "type": "res",
  "id": "c1",
  "ok": true,
  "payload": {
    "type": "hello-ok",
    "protocol": 3,
    "server": {
      "version": "2026.3.23-2",
      "connId": "88121fb7-3150-4ec5-a1f8-cbd93e57a580"
    },
    "features": {
      "methods": ["sessions.list", "sessions.create", "chat.send", "chat.history", "chat.abort", ...],
      "events": ["session.message", "session.tool", "presence", "tick", "shutdown"]
    },
    "snapshot": {
      "presence": [...],
      "health": {...},
      "stateVersion": { "presence": 42, "health": 7 },
      "uptimeMs": 12345678,
      "configPath": "/home/casa/.openclaw/openclaw.json",
      "stateDir": "/home/casa/.openclaw/state",
      "sessionDefaults": {
        "defaultAgentId": "main",
        "mainKey": "main",
        "mainSessionKey": "agent:main:main",
        "scope": "..."
      },
      "authMode": "token"               // none|token|password|trusted-proxy
    },
    "auth": {                            // present when device pairing succeeds
      "deviceToken": "<persisted_token>",
      "role": "operator",
      "scopes": ["operator.admin", "operator.read", "operator.write", "operator.approvals"],
      "issuedAtMs": 1774465886000
    },
    "policy": {
      "maxPayload": 1048576,             // 1MB
      "maxBufferedBytes": 4194304,
      "tickIntervalMs": 30000
    }
  }
}
```

---

## 3. Session Lifecycle

### 3.1 sessions.list

List existing chat sessions.

**Params:**
```json
{
  "limit": 20,                    // optional, int (default varies)
  "activeMinutes": 60,            // optional, filter by recent activity
  "includeGlobal": false,         // optional
  "includeUnknown": false,        // optional
  "includeDerivedTitles": true,   // optional, reads first 8KB per session for title
  "includeLastMessage": true,     // optional, reads last 16KB per session for preview
  "label": "string",              // optional, filter by label
  "agentId": "main",              // optional, filter by agent
  "search": "keyword",            // optional, search filter
  "spawnedBy": "string"           // optional, filter by parent session
}
```

**Response payload:**
```json
{
  "ts": 1774465886169,
  "path": "(multiple)",
  "count": 5,
  "defaults": {
    "modelProvider": "openai-codex",
    "model": "gpt-5.4",
    "contextTokens": 272000
  },
  "sessions": [
    {
      "key": "agent:main:dashboard:e2ba0360-...",
      "kind": "direct",                    // direct|group
      "displayName": "ClawPilot probe",
      "derivedTitle": "...",               // if includeDerivedTitles
      "lastMessagePreview": "pong",        // if includeLastMessage
      "channel": "webchat",                // optional
      "chatType": "direct",
      "origin": {
        "label": "ClawPilot probe",
        "provider": "webchat",
        "surface": "webchat",
        "chatType": "direct"
      },
      "updatedAt": 1774465890986,          // epoch ms
      "sessionId": "df732b05-...",
      "systemSent": true,
      "abortedLastRun": false,
      "inputTokens": 3,
      "outputTokens": 5,
      "totalTokens": 33292,
      "totalTokensFresh": true,
      "estimatedCostUsd": 0.12491775,
      "status": "done",                    // done|running|error|idle
      "startedAt": 1774465887152,
      "endedAt": 1774465890882,
      "runtimeMs": 3730,
      "modelProvider": "anthropic",
      "model": "claude-sonnet-4-6",
      "contextTokens": 1000000,
      "deliveryContext": {
        "channel": "webchat",
        "to": "...",                       // optional
        "accountId": "..."                 // optional
      },
      "lastChannel": "webchat",
      "lastTo": "...",                     // optional
      "lastAccountId": "..."              // optional
    }
  ]
}
```

### 3.2 sessions.create

Create a new chat session.

**Params:**
```json
{
  "agentId": "main",                  // optional, defaults to config default agent
  "key": "agent:main:custom-key",     // optional, auto-generated if omitted (dashboard:uuid)
  "label": "My Chat",                 // optional, human label
  "model": "anthropic/claude-sonnet-4-6",  // optional, override model
  "parentSessionKey": "agent:main:...",    // optional, for sub-sessions
  "task": "...",                       // optional, initial task description
  "message": "Hello!"                 // optional, if set -> immediately calls chat.send
}
```

**Response payload:**
```json
{
  "ok": true,
  "key": "agent:main:dashboard:e2ba0360-a84e-44d5-96c3-7e9014ce07c6",
  "sessionId": "df732b05-d34c-4c91-b6ca-0229017ae09f",
  "entry": {
    "sessionId": "df732b05-...",
    "updatedAt": 1774465886367
  },
  "runStarted": false,                // true if `message` was provided and chat.send succeeded
  "runId": "...",                     // present if runStarted
  "status": "started"                 // present if runStarted
}
```

### 3.3 sessions.resolve

Resolve a session key from various identifiers.

**Params:**
```json
{
  "key": "string",                // optional
  "sessionId": "uuid",           // optional
  "label": "string",             // optional
  "agentId": "main",             // optional
  "spawnedBy": "string",         // optional
  "includeGlobal": false,        // optional
  "includeUnknown": false        // optional
}
```

**Response:** `{ "ok": true, "key": "agent:main:..." }`

### 3.4 sessions.patch

Update session configuration.

**Params:**
```json
{
  "key": "agent:main:dashboard:...",     // required
  "label": "New Label",                  // optional, null to clear
  "model": "anthropic/claude-opus-4-6",  // optional, null to clear
  "thinkingLevel": "high",              // optional
  "fastMode": true,                      // optional
  "verboseLevel": "full",               // optional: off|brief|full
  "reasoningLevel": "string",           // optional
  "sendPolicy": "allow"                 // optional: allow|deny
  // ... many more session-level overrides
}
```

### 3.5 sessions.reset

Clear conversation history but keep the session.

**Params:** `{ "key": "agent:main:...", "reason": "new" }`  (reason: "new"|"reset")

### 3.6 sessions.delete

Delete a session and optionally its transcript.

**Params:**
```json
{
  "key": "agent:main:dashboard:...",
  "deleteTranscript": true,            // optional, default varies
  "emitLifecycleHooks": true           // optional
}
```

**Response:**
```json
{
  "ok": true,
  "key": "agent:main:dashboard:...",
  "deleted": true,
  "archived": ["...path-to-deleted-transcript..."]
}
```

### 3.7 sessions.compact

Compact/summarize a long session to reduce context.

**Params:** `{ "key": "agent:main:...", "maxLines": 200 }`

---

## 4. Chat Operations (Primary for ClawPilot)

### 4.1 chat.send

Send a message and trigger an agent response. Returns immediately; response streams via events.

**Params:**
```json
{
  "sessionKey": "agent:main:dashboard:...",    // required
  "message": "Hello, Oris!",                  // required (or attachment)
  "idempotencyKey": "uuid-v4",                // required (used as client runId)
  "thinking": "high",                         // optional: thinking level override
  "deliver": true,                            // optional: route to channel
  "attachments": [],                          // optional: array of attachment objects
  "timeoutMs": 120000                         // optional: override agent timeout
}
```

**Immediate Response:**
```json
{
  "type": "res",
  "id": "r4",
  "ok": true,
  "payload": {
    "runId": "1acf474b-2fd7-4ce1-a984-531020b5168f",  // = idempotencyKey
    "status": "started"                                 // started|in_flight (if duplicate)
  }
}
```

### 4.2 chat.history

Get conversation history for a session.

**Params:**
```json
{
  "sessionKey": "agent:main:dashboard:...",    // required
  "limit": 200                                 // optional, max 1000, default 200
}
```

**Response:**
```json
{
  "sessionKey": "agent:main:dashboard:...",
  "sessionId": "df732b05-...",
  "messages": [
    {
      "role": "user",
      "content": [
        { "type": "text", "text": "hola, solo responde pong" }
      ],
      "timestamp": 1774465887149,
      "__openclaw": {
        "id": "a08189dc",
        "seq": 1
      },
      "senderLabel": "ClawPilot probe (openclaw-android)"
    },
    {
      "role": "assistant",
      "content": [
        { "type": "text", "text": "pong" }
      ],
      "api": "anthropic-messages",
      "provider": "anthropic",
      "model": "claude-sonnet-4-6",
      "usage": {
        "input": 3,
        "output": 5,
        "totalTokens": 33297,
        "cacheRead": 0,
        "cacheWrite": 33289,
        "cost": { "total": 0.12491775 }
      },
      "stopReason": "stop",
      "timestamp": 1774465887186,
      "responseId": "msg_019kA1Cf88PmphXeYNqcEG2Z",
      "__openclaw": {
        "id": "ea4eed88",
        "seq": 2
      }
    }
  ],
  "thinkingLevel": "adaptive",
  "fastMode": null,
  "verboseLevel": null
}
```

### 4.3 chat.abort

Abort a running agent response.

**Params:**
```json
{
  "sessionKey": "agent:main:dashboard:...",    // required
  "runId": "uuid"                              // optional: specific run, or all runs on session
}
```

**Response:**
```json
{
  "ok": true,
  "aborted": true,           // false if no active run
  "runIds": ["uuid"]          // list of aborted run IDs
}
```

### 4.4 chat.inject

Inject a system message into the conversation (not a user message).

**Params:**
```json
{
  "sessionKey": "agent:main:...",
  "message": "System note: ...",
  "label": "optional label"
}
```

---

## 5. Session-level Send (Alternative API)

### 5.1 sessions.send

Higher-level send that uses session `key` instead of `sessionKey`. Internally delegates to chat.send.

**Params:**
```json
{
  "key": "agent:main:dashboard:...",    // required (session key)
  "message": "Hello!",                  // required
  "thinking": "high",                   // optional
  "attachments": [],                    // optional
  "timeoutMs": 120000,                  // optional
  "idempotencyKey": "uuid"              // optional
}
```

### 5.2 sessions.abort

**Params:**
```json
{
  "key": "agent:main:...",              // required
  "runId": "uuid"                       // optional
}
```

**Response:**
```json
{
  "ok": true,
  "abortedRunId": "uuid",              // or null
  "status": "aborted"                   // or "no-active-run"
}
```

---

## 6. Subscription System

### 6.1 sessions.subscribe / sessions.unsubscribe

Subscribe to session-level lifecycle events (sessions.changed, session.tool). No params needed.

```json
// Request
{ "type": "req", "id": "s1", "method": "sessions.subscribe", "params": {} }

// Response
{ "type": "res", "id": "s1", "ok": true, "payload": { "subscribed": true } }
```

After subscribing, the client receives `sessions.changed` events when ANY session changes state.

### 6.2 sessions.messages.subscribe / sessions.messages.unsubscribe

Subscribe to real-time message events for a SPECIFIC session.

```json
// Request
{ "type": "req", "id": "s2", "method": "sessions.messages.subscribe", "params": { "key": "agent:main:dashboard:..." } }

// Response
{ "type": "res", "id": "s2", "ok": true, "payload": { "subscribed": true, "key": "agent:main:dashboard:..." } }
```

After subscribing, the client receives `session.message` events for that session.

---

## 7. WebSocket Event Types

### 7.1 `chat` — Streaming response (PRIMARY for ClawPilot chat UI)

**State: "delta" (streaming token)**
```json
{
  "type": "event",
  "event": "chat",
  "payload": {
    "runId": "1acf474b-...",
    "sessionKey": "agent:main:dashboard:...",
    "seq": 2,
    "state": "delta",
    "message": {
      "role": "assistant",
      "content": [{ "type": "text", "text": "accumulated text so far" }],
      "timestamp": 1774465890798
    }
  },
  "seq": 1014
}
```

**State: "final" (response complete)**
```json
{
  "type": "event",
  "event": "chat",
  "payload": {
    "runId": "1acf474b-...",
    "sessionKey": "agent:main:dashboard:...",
    "seq": 4,
    "state": "final",
    "stopReason": "stop",          // optional: "stop"|"rpc"|...
    "message": {
      "role": "assistant",
      "content": [{ "type": "text", "text": "pong" }],
      "timestamp": 1774465890883
    }
  },
  "seq": 1018
}
```

**State: "error" (agent error)**
```json
{
  "type": "event",
  "event": "chat",
  "payload": {
    "runId": "...",
    "sessionKey": "...",
    "seq": 5,
    "state": "error",
    "errorMessage": "timeout exceeded"
  }
}
```

**State: "aborted" (user or system abort)**
```json
{
  "type": "event",
  "event": "chat",
  "payload": {
    "runId": "...",
    "sessionKey": "...",
    "seq": 5,
    "state": "aborted"
  }
}
```

**IMPORTANT:** Delta text is ACCUMULATED (full text so far), not incremental.
The gateway throttles deltas to ~150ms intervals. Deltas may skip text — always use the latest `text` field as the full response so far.

### 7.2 `agent` — Low-level agent lifecycle events

```json
{
  "type": "event",
  "event": "agent",
  "payload": {
    "runId": "...",
    "stream": "lifecycle",          // lifecycle|assistant|tool|error
    "data": {
      "phase": "start",            // lifecycle: start|end|error
      "startedAt": 1774465887152
    },
    "sessionKey": "agent:main:...",
    "seq": 1,
    "ts": 1774465887152
  }
}
```

Lifecycle phases:
- `start` — agent run begins
- `end` — agent run complete (data may include `stopReason`)
- `error` — agent run failed (data may include `error`)

Stream types:
- `lifecycle` — start/end/error phases
- `assistant` — text delta streaming (data.text, data.delta)
- `tool` — tool call events (data.phase: "start"|"end", data.name, data.result)
- `error` — error details

### 7.3 `session.message` — Per-session message notifications

Sent to connections subscribed via `sessions.messages.subscribe`.

```json
{
  "type": "event",
  "event": "session.message",
  "payload": {
    "sessionKey": "agent:main:dashboard:...",
    "message": {
      "role": "user",                // or "assistant"
      "content": "hola, solo responde pong",
      "timestamp": 1774465886394,
      "__openclaw": { "seq": 0 }
    },
    "messageId": "...",              // optional (absent on first user message)
    "messageSeq": 0,
    "session": {
      "key": "agent:main:...",
      "kind": "direct",
      "displayName": "ClawPilot probe",
      "chatType": "direct",
      "status": "...",
      "modelProvider": "anthropic",
      "model": "claude-sonnet-4-6",
      "updatedAt": 1774465886591,
      "sessionId": "df732b05-..."
    },
    "updatedAt": 1774465886591,
    "sessionId": "df732b05-...",
    "kind": "direct",
    "displayName": "ClawPilot probe",
    "deliveryContext": { "channel": "webchat" }
  }
}
```

### 7.4 `session.tool` — Tool execution events (requires sessions.subscribe)

Same payload structure as `agent` events with `stream: "tool"`, but scoped to session subscribers.

### 7.5 `sessions.changed` — Session metadata changes (requires sessions.subscribe)

```json
{
  "type": "event",
  "event": "sessions.changed",
  "payload": {
    "sessionKey": "agent:main:...",
    "phase": "start",               // start|end|error|"" (for create/send/etc.)
    "reason": "send",               // create|send|steer|...
    "runId": "...",
    "ts": 1774465887152,
    "status": "running",
    "startedAt": 1774465887152,
    "endedAt": null,
    "runtimeMs": null,
    "updatedAt": 1774465887152,
    "totalTokens": 33292,
    "estimatedCostUsd": 0.12,
    "modelProvider": "anthropic",
    "model": "claude-sonnet-4-6",
    "session": { ... }              // full session row when available
  }
}
```

### 7.6 `health` — Periodic health snapshot

Broadcast periodically. Contains channel status, agent summaries, session stats.

### 7.7 `tick` — Heartbeat keepalive

```json
{ "type": "event", "event": "tick", "payload": { "ts": 1774465900000 } }
```

Default interval: 30s (from `policy.tickIntervalMs`). Use this as WebSocket keepalive.

### 7.8 `shutdown` — Gateway shutting down

```json
{ "type": "event", "event": "shutdown", "payload": { "reason": "restart", "restartExpectedMs": 5000 } }
```

### 7.9 `presence` — Presence changes

Broadcast when connected clients change. Contains full presence list.

---

## 8. Complete Chat Flow (for ClawPilot implementation)

```
1. Connect WebSocket
2. Receive connect.challenge
3. Send connect request (with token auth)
4. Receive hello-ok (extract features.methods, snapshot.sessionDefaults)
5. Send sessions.subscribe (to get sessions.changed events)
6. Send sessions.list (to populate session list)

--- User opens a chat session ---

7. Send sessions.messages.subscribe { key: sessionKey }
8. Send chat.history { sessionKey, limit: 50 }
9. Display history messages

--- User sends a message ---

10. Generate idempotencyKey (UUID v4)
11. Send chat.send { sessionKey, message, idempotencyKey }
12. Receive res { runId, status: "started" }
13. Show "thinking" indicator

14. Receive event chat { state: "delta", message.content[0].text: "partial..." }
    -> Update UI with accumulated text (NOT incremental diff)
15. ... more deltas (throttled ~150ms) ...
16. Receive event chat { state: "final", message.content[0].text: "complete response" }
    -> Finalize message, remove thinking indicator

--- User taps abort ---

17. Send chat.abort { sessionKey }
    OR sessions.abort { key: sessionKey }
18. Receive res { ok: true, aborted: true, runIds: [...] }
19. May receive chat event with state "aborted" or "error"

--- User switches session ---

20. Send sessions.messages.unsubscribe { key: oldSessionKey }
21. Send sessions.messages.subscribe { key: newSessionKey }
22. Send chat.history { sessionKey: newSessionKey }

--- User creates new session ---

23. Send sessions.create { agentId: "main" }
24. Receive res { ok: true, key: "agent:main:dashboard:uuid", sessionId: "..." }
25. Follow steps 7-9 for the new session
```

---

## 9. Session Key Format

Session keys follow the pattern: `agent:{agentId}:{scope}:{uuid?}`

Examples:
- `agent:main:main` — main persistent session for the "main" agent
- `agent:main:dashboard:e2ba0360-...` — ephemeral dashboard session (created via UI)
- `agent:cacho:main` — persistent session for Cacho agent
- `agent:main:cron:58cc741e-...` — cron-initiated session

For ClawPilot, use `sessions.create` which generates `agent:{agentId}:dashboard:{uuid}` keys automatically.

---

## 10. Available Agents (from openclaw.json)

| Agent ID | Name | Description |
|----------|------|-------------|
| main | Oris | Primary orchestrator |
| cacho | Cacho | Accounting/invoices |
| roxy | Roxy | Marketing/Instagram |
| tech | Tech | Technical/DevOps |
| hercules | Hercules | Gym/nutrition |
| legal-advisor | Legal Advisor HORECA | Legal advice |
| forge | Forge | Research/scouting |

List agents dynamically via `agents.list` RPC (no params required).

---

## 11. Method Scopes (Permission System)

Methods are grouped by required scopes:

| Scope | Methods |
|-------|---------|
| `operator.read` | sessions.list, sessions.preview, sessions.resolve, sessions.subscribe, sessions.unsubscribe, sessions.messages.subscribe/unsubscribe, sessions.usage, chat.history, agents.list, models.list, config.get, health, status |
| `operator.write` | chat.send, chat.abort, sessions.create, sessions.send, sessions.steer, sessions.abort, sessions.patch, sessions.reset, sessions.delete, sessions.compact, chat.inject |
| `operator.approvals` | exec.approval.resolve, device.pair.approve/reject |

---

## 12. Error Handling

Common error codes observed:
- `INVALID_REQUEST` — bad params, validation failure, unauthorized
- `UNAVAILABLE` — session/resource not found, gateway busy

Error responses always follow:
```json
{
  "type": "res",
  "id": "r4",
  "ok": false,
  "error": {
    "code": "INVALID_REQUEST",
    "message": "human-readable description",
    "retryable": false
  }
}
```

---

## 13. Implementation Notes for ClawPilot

### Delta text is accumulated
The `chat` event `delta` state sends the FULL accumulated text in `message.content[0].text`, not an incremental diff. Simply replace the displayed text each time. The gateway throttles to ~150ms.

### Idempotency
`chat.send` requires an `idempotencyKey` (UUID v4). If the same key is sent again:
- While running: returns `{ runId, status: "in_flight" }` with `cached: true`
- After completion: returns cached response

### Tick as keepalive
The gateway sends `tick` events every ~30s. If no tick arrives for >60s, the connection is likely dead. Reconnect.

### Session defaults
After hello-ok, check `snapshot.sessionDefaults.defaultAgentId` for the default agent. For ClawPilot, this is "main" (Oris).

### Subscriptions are per-connection
Both `sessions.subscribe` and `sessions.messages.subscribe` are tied to the WebSocket connection. They do not persist across reconnects.

### chat.send vs sessions.send
- `chat.send` uses `sessionKey` and requires `idempotencyKey`
- `sessions.send` uses `key` and auto-generates idempotency if omitted
- Both produce the same streaming behavior
- **Recommendation:** Use `chat.send` for ClawPilot (more explicit control, better dedup)
