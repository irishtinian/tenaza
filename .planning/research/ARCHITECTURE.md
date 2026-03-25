# Architecture Patterns

**Domain:** Mobile remote control for multi-agent AI system
**Researched:** 2026-03-25
**Overall confidence:** HIGH

---

## System Overview

```
+------------------+          +-------------------+          +------------------+
|                  |  REST    |                   |  Local   |                  |
|   ClawPilot      |<-------->|  OpenClaw Gateway |<-------->|  Agent Runtimes  |
|   (Android App)  |  WS      |  (port 18789)     |          |  (Claude, etc.)  |
|                  |          |                   |          |                  |
+--------+---------+          +---------+---------+          +------------------+
         |                              |
         | FCM                          | Webhook
         v                              v
+------------------+          +-------------------+
|  Firebase Cloud  |<---------|  FCM Relay Module  |
|  Messaging       |          |  (gateway plugin)  |
+------------------+          +-------------------+
```

The app is a thin client. All intelligence lives on the gateway. ClawPilot's job is:
1. Present chat UI for agent conversations
2. Show real-time agent status
3. Relay push notifications for events requiring attention
4. Cache recent data for offline viewing

---

## 1. Chat App Architecture on Android (Compose)

### Message List Pattern

Use `LazyColumn` with `reverseLayout = true`. This is the standard pattern for chat UIs --
items stack from the bottom, newest messages appear at the bottom, and scrolling up
reveals history. Google's official Jetchat sample uses exactly this approach.

```
+-----------------------------------+
|  [Load more...]                   |  <- triggered by scroll to top
|  Agent: "Task completed"    10:01 |
|  Agent: "Running analysis"  10:00 |
|  You: "Analyze logs"        09:59 |
|  Agent: "Ready"             09:58 |
|  +---------+                      |
|  | Send    |  [________________]  |
+-----------------------------------+
     reverseLayout = true
     firstVisibleItemIndex 0 = bottom
```

**Key implementation details:**

```kotlin
// Core chat list structure
LazyColumn(
    state = listState,
    reverseLayout = true,
    contentPadding = PaddingValues(vertical = 8.dp)
) {
    items(
        items = messages,
        key = { it.id }  // CRITICAL: stable keys for efficient recomposition
    ) { message ->
        MessageBubble(message)
    }
}
```

**Stable keys are mandatory.** Without them, LazyColumn recomposes everything on each new
message. Use message ID (UUID or server-assigned) as key.

### Streaming Text (Typewriter Effect)

For AI agent responses that stream token-by-token via WebSocket:

```kotlin
@Composable
fun StreamingMessageBubble(tokens: StateFlow<String>) {
    val text by tokens.collectAsStateWithLifecycle()
    val breakIterator = remember(text) { BreakIterator.getCharacterInstance() }
    // For streaming, text updates come from the WebSocket flow.
    // No need for artificial delay -- tokens arrive at natural pace.
    // Just render the current accumulated text.
    Text(
        text = text,
        modifier = Modifier.animateContentSize() // smooth size transitions
    )
}
```

**Do NOT use artificial typewriter delay for streaming responses.** The tokens already
arrive incrementally from the agent. Simply accumulate and display. Use `animateContentSize()`
on the bubble to prevent jarring layout jumps as text grows.

For completed messages loaded from cache, display full text immediately -- no animation.

### Message Bubble Architecture

```
MessageBubble
  +-- AgentAvatar (left side, agent messages only)
  |     Uses agent's configured avatar/emoji from gateway
  +-- BubbleContent
  |     +-- AgentName (for multi-agent context)
  |     +-- MessageText (or streaming text)
  |     +-- Timestamp
  |     +-- StatusIndicator (sent/delivered/error)
  +-- ActionButtons (for approval-required messages)
        +-- Approve / Reject buttons inline
```

**Different bubble types needed:**
- `UserMessageBubble` -- right-aligned, primary color
- `AgentMessageBubble` -- left-aligned, with avatar, surface color
- `SystemMessageBubble` -- centered, muted, for status changes
- `ApprovalMessageBubble` -- agent bubble + action buttons
- `ErrorMessageBubble` -- agent bubble with error styling

### Performance Notes (Compose 2025+)

- **Pausable composition** (Compose BOM 2025.01+): LazyColumn can pause prefetch work
  mid-frame, eliminating jank when scrolling through long message lists
- Use `collectAsStateWithLifecycle()` (NOT `collectAsState()`) for all Flow collection
  to respect lifecycle and avoid background recompositions
- Split large state objects: separate `messagesState` from `connectionState` from
  `inputState` to minimize recomposition scope

**Confidence: HIGH** -- Based on official Android samples (Jetchat), Google I/O 2025
announcements, and Stream SDK patterns.

---

## 2. WebSocket Management on Android

### Recommendation: OkHttp WebSocket (NOT Scarlet, NOT Ktor)

**Why OkHttp directly:**
- Already a transitive dependency (Retrofit uses it)
- Scarlet is stuck at 0.1.12, no stable 1.0, questionable maintenance
- Ktor client adds a large dependency for just WebSocket
- OkHttp WebSocket API is simple enough to wrap in ~200 lines with coroutines

### Connection Architecture

```
+-------------------------------------------------------------+
|                    WebSocketManager                          |
|  (Singleton, injected by Hilt, survives config changes)     |
+-------------------------------------------------------------+
|                                                              |
|  +------------------+    +-------------------+               |
|  | ConnectionState  |    | ReconnectPolicy   |               |
|  | (StateFlow)      |    | (ExponentialBack  |               |
|  |  - Connected     |    |  off + Jitter)    |               |
|  |  - Connecting    |    +-------------------+               |
|  |  - Disconnected  |                                        |
|  |  - Error(reason) |    +-------------------+               |
|  +------------------+    | MessageChannel    |               |
|                          | (SharedFlow)      |               |
|  +------------------+    | Emits parsed      |               |
|  | PingPong         |    | gateway events    |               |
|  | (15s interval)   |    +-------------------+               |
|  +------------------+                                        |
+-------------------------------------------------------------+
          |                        |
          v                        v
   OkHttpClient              CoroutineScope
   .newWebSocket()           (SupervisorJob)
```

### Reconnection Strategy

Use exponential backoff with jitter. This is the industry standard.

```
Attempt 1: 1s  + random(0-500ms)
Attempt 2: 2s  + random(0-500ms)
Attempt 3: 4s  + random(0-500ms)
Attempt 4: 8s  + random(0-500ms)
Attempt 5: 16s + random(0-500ms)
Cap at:    30s + random(0-500ms)

Reset counter on successful connection.
```

**Network awareness:** Use `ConnectivityManager.registerNetworkCallback()` to detect
network changes. When network returns after being lost, trigger immediate reconnect
(skip backoff). When network is lost, stop reconnection attempts (save battery).

### Lifecycle Management

```
App in foreground  ->  WebSocket OPEN, 15s ping interval
App in background  ->  WebSocket CLOSE after 30s grace period
                       (rely on FCM for notifications)
App killed         ->  WebSocket dead, FCM only
Notification tap   ->  App opens, WebSocket reconnects
```

**Do NOT keep WebSocket alive in background.** Android will kill it anyway (Doze mode),
and it wastes battery. The correct pattern is:

1. Foreground: active WebSocket for real-time chat
2. Background: FCM push for important events (approval needed, errors, completions)
3. On return to foreground: reconnect WebSocket, sync missed messages via REST

### Message Protocol

Gateway should send JSON frames. Suggested message envelope:

```json
{
  "type": "agent_message|agent_status|approval_request|heartbeat|error",
  "agent_id": "oris",
  "payload": { ... },
  "timestamp": "2026-03-25T10:00:00Z",
  "sequence": 12345
}
```

Use `sequence` numbers to detect missed messages on reconnect. On reconnect,
send `{ "type": "sync", "last_sequence": 12340 }` to get missed events.

**Confidence: HIGH** -- OkHttp WebSocket is battle-tested. Reconnection patterns are
well-established across chat/real-time apps.

---

## 3. Push Notification Architecture

### FCM Relay Architecture

Since ClawPilot connects directly to a user's gateway (no backend), FCM requires a
relay component on the gateway side.

```
Agent Event
     |
     v
OpenClaw Gateway
     |
     +---> WebSocket (if app is foreground)
     |
     +---> FCM Relay Module
               |
               v
           Firebase Admin SDK (or HTTP v1 API)
               |
               v
           FCM Server
               |
               v
           ClawPilot (background/killed)
               |
               v
           Notification + optional deep link
```

### Gateway-Side FCM Component

The gateway needs a lightweight module that:
1. Stores the device's FCM token (received during pairing)
2. On qualifying events, sends FCM message via Firebase HTTP v1 API
3. Includes structured data payload for deep linking

**Qualifying events for push:**
- Agent requests approval (CRITICAL -- needs immediate action)
- Agent task completed
- Agent task failed/errored
- Agent went offline unexpectedly
- Cron job completed/failed (v2)

**NOT worth pushing:**
- Individual chat messages (too noisy)
- Status heartbeats
- Metric updates

### Notification Data Payload

```json
{
  "message": {
    "token": "<device_fcm_token>",
    "data": {
      "type": "approval_request",
      "agent_id": "oris",
      "agent_name": "Oris",
      "title": "Oris needs approval",
      "body": "Delete old log files? (3.2 GB)",
      "action_id": "act_abc123",
      "deep_link": "clawpilot://chat/oris?action=act_abc123"
    }
  }
}
```

Use **data-only messages** (not notification messages) to have full control over display
in both foreground and background. This lets the app:
- Show custom notification with action buttons
- Handle foreground notifications without duplicate display
- Include Approve/Reject actions directly in the notification

### Notification Actions (Approve/Reject)

```
+---------------------------------------------+
|  Oris needs approval                        |
|  Delete old log files? (3.2 GB)             |
|                                             |
|  [Approve]    [Reject]    [Open Chat]       |
+---------------------------------------------+
```

Implement with `NotificationCompat.Action` + `PendingIntent` pointing to a
`BroadcastReceiver` that calls the gateway REST API directly:

```
POST /api/agents/{agent_id}/actions/{action_id}/approve
POST /api/agents/{agent_id}/actions/{action_id}/reject
```

This lets users approve/reject without opening the app.

### Deep Linking with Navigation 3

Use custom URI scheme: `clawpilot://`

```
clawpilot://chat/{agent_id}          -> Opens chat with agent
clawpilot://chat/{agent_id}?action=X -> Opens chat, scrolls to action
clawpilot://dashboard                -> Opens dashboard
clawpilot://agents/{agent_id}        -> Opens agent detail
```

With Navigation 3's type-safe routes, deep links map to Kotlin sealed classes:

```kotlin
sealed class Route {
    data class Chat(val agentId: String, val actionId: String? = null) : Route()
    data class AgentDetail(val agentId: String) : Route()
    data object Dashboard : Route()
}
```

**Confidence: HIGH** -- FCM data messages + notification actions is the standard pattern
for approval workflows. Deep linking with Navigation 3 is well-documented.

---

## 4. Offline-First Patterns

### Architecture: Room as Single Source of Truth

```
+-----------+     +-----------+     +------------------+
|  Compose  |<----|  Room DB  |<----|  Gateway REST    |
|  UI       |     |  (truth)  |---->|  + WebSocket     |
+-----------+     +-----------+     +------------------+
     |                 ^
     |                 |
     +-- reads via --> Flow<List<T>>
         collectAsStateWithLifecycle
```

**The rule:** UI always reads from Room. Never directly from network.
Network responses are written to Room, which triggers Flow emissions to UI.

### Data Entities with Sync Metadata

```kotlin
@Entity(tableName = "messages")
data class MessageEntity(
    @PrimaryKey val id: String,
    val agentId: String,
    val content: String,
    val sender: MessageSender, // USER, AGENT, SYSTEM
    val timestamp: Long,
    val syncStatus: SyncStatus, // SYNCED, PENDING_SEND, FAILED
    val serverSequence: Long?  // for ordering/gap detection
)

enum class SyncStatus { SYNCED, PENDING_SEND, FAILED }

@Entity(tableName = "agents")
data class AgentEntity(
    @PrimaryKey val id: String,
    val name: String,
    val status: AgentStatus,
    val model: String?,
    val avatarEmoji: String?,
    val lastActivityAt: Long,
    val cachedAt: Long  // when this data was fetched
)
```

### Sync Strategy

**For messages (append-only):**
1. User sends message -> save to Room with `PENDING_SEND` -> show in UI immediately (optimistic)
2. Send via REST/WebSocket -> on success, update to `SYNCED`
3. On failure, mark `FAILED` -> show retry button in UI
4. On reconnect, fetch messages since last known `serverSequence`

**For agent status (mutable, server-authoritative):**
1. On WebSocket connect: full agent list fetch via REST
2. During session: incremental updates via WebSocket events
3. On reconnect: delta sync (send `If-Modified-Since` or last known timestamp)
4. Offline: show cached data with "Last updated X ago" indicator

**For conversations (historical, paginated):**
1. Cache last N messages per agent (N = 100)
2. Older messages: fetch on scroll, cache in Room with TTL
3. Prune messages older than 7 days on app start (configurable)

### WorkManager for Background Sync

Use `OneTimeWorkRequest` (not periodic) triggered by:
- App returning to foreground
- Network connectivity restored
- After sending a message that was queued offline

```kotlin
val syncWork = OneTimeWorkRequestBuilder<SyncWorker>()
    .setConstraints(
        Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()
    )
    .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
    .build()

WorkManager.getInstance(context).enqueueUniqueWork(
    "message_sync",
    ExistingWorkPolicy.REPLACE,
    syncWork
)
```

**No periodic sync needed.** WebSocket handles real-time. WorkManager handles "catch-up
on reconnect" and "retry failed sends."

### Conflict Resolution

Use **Last Write Wins** based on server timestamp. This is sufficient because:
- Messages are append-only (no conflicts)
- Agent status is server-authoritative (server always wins)
- User preferences are device-local (no sync needed)
- Only one device per gateway (no multi-device conflicts in v1)

**Confidence: HIGH** -- Room + Flow + WorkManager is the recommended Android architecture
per official docs and droidcon 2025 talks.

---

## 5. Device Pairing Flow (QR-Based Auth)

### Flow Diagram

```
Gateway (Desktop/Server)              ClawPilot (Phone)
========================              ==================

1. User opens gateway UI
   or runs CLI command
        |
2. Gateway generates:                 3. User opens app
   - temp pairing token (UUID)           "Scan QR to connect"
   - gateway URL (e.g. tailscale IP)         |
   - token expiry (5 minutes)           4. Scans QR code
   - device fingerprint challenge            |
        |                               5. Extracts:
3. Displays QR code encoding:              - gateway URL
   {                                       - pairing token
     "url": "https://my-gateway.ts.net",   |
     "token": "pair_abc123...",         6. POST /api/pair
     "expires": "2026-03-25T10:05:00Z"    {
   }                                        "pairing_token": "pair_abc123",
        |                                   "device_name": "Pixel 8",
        |                                   "fcm_token": "fcm_xyz...",
        |                                   "device_id": "fingerprint..."
7. Gateway validates:                    }
   - Token not expired                      |
   - Token not already used                 |
   - Marks as consumed                      |
        |                                   |
8. Returns:                             9. Stores in EncryptedSharedPrefs:
   {                                       - gateway URL
     "auth_token": "long_lived_jwt",       - auth token
     "gateway_name": "casa-gateway",       - gateway name
     "agents": [...]                       |
   }                                   10. Navigates to Dashboard
```

### QR Code Contents

Keep the QR payload minimal (QR codes have limited capacity):

```json
{
  "v": 1,
  "u": "https://gw.example.com:18789",
  "t": "pair_8f3a...",
  "e": 1711360200
}
```

- `v`: protocol version (for future compatibility)
- `u`: gateway URL
- `t`: pairing token (short, 16 chars sufficient)
- `e`: expiry as Unix timestamp

### Security Considerations

| Concern | Mitigation |
|---------|------------|
| QR interception | 5-minute expiry, single-use token |
| Token storage | EncryptedSharedPreferences (AES-256) |
| Network sniffing | HTTPS required (TLS), certificate pinning optional |
| Device theft | Biometric lock option for app access |
| Token revocation | Gateway can revoke token, returns 401, app shows re-pair screen |
| Replay attack | Pairing tokens are single-use, auth tokens have device fingerprint |

### Device Fingerprint

Generate a stable device identifier:

```kotlin
// Combine hardware identifiers for a stable fingerprint
val deviceId = UUID.nameUUIDFromBytes(
    "${Build.MANUFACTURER}|${Build.MODEL}|${Settings.Secure.ANDROID_ID}".toByteArray()
).toString()
```

This lets the gateway reject tokens used from a different device than originally paired.

### Re-pairing Flow

If auth token becomes invalid (401 response):
1. Clear stored credentials
2. Show "Connection lost" screen
3. Prompt to re-scan QR code
4. Gateway admin generates new QR

**Confidence: HIGH** -- Pattern is identical to WhatsApp Web, Telegram Desktop, Signal
Desktop linking. Well-established and understood.

---

## 6. Multi-Screen Architecture (Navigation)

### Use Navigation 3 (Stable since Dec 2025)

Navigation 3 is Compose-first, type-safe, and gives full control over the back stack.
It replaces the older Navigation Compose library.

### Screen Structure

```
+---------------------------------------------------+
|  ClawPilot            [connection indicator]   [!] |
+---------------------------------------------------+
|                                                    |
|              CONTENT AREA                          |
|       (varies by selected tab)                     |
|                                                    |
|                                                    |
|                                                    |
|                                                    |
|                                                    |
+---------------------------------------------------+
|  Chat  |  Dashboard  |  Agents  |  Settings        |
|  [ic]  |    [ic]     |   [ic]   |    [ic]          |
+---------------------------------------------------+
```

### Route Definitions (Type-Safe)

```kotlin
// Top-level tab routes
sealed interface TabRoute {
    data object ChatList : TabRoute
    data object Dashboard : TabRoute
    data object AgentList : TabRoute
    data object Settings : TabRoute
}

// Detail routes (pushed onto main back stack, above tabs)
sealed interface DetailRoute {
    data class ChatConversation(val agentId: String) : DetailRoute
    data class AgentDetail(val agentId: String) : DetailRoute
    data object PairDevice : DetailRoute
    data object About : DetailRoute
}
```

### Navigation Architecture with Nav 3

```
Main Back Stack (global)
  |
  +-- TabsScreen (contains bottom bar + tab content)
  |     |
  |     +-- chatBackStack     (per-tab, preserved on switch)
  |     +-- dashboardState    (no sub-navigation needed)
  |     +-- agentsBackStack   (per-tab, preserved on switch)
  |     +-- settingsState     (no sub-navigation needed)
  |
  +-- ChatConversation(agentId)   <- pushed on top of tabs
  +-- AgentDetail(agentId)        <- pushed on top of tabs
```

**Two NavDisplay instances:**
1. Main NavDisplay: handles global navigation (tabs screen + detail screens)
2. Tab NavDisplay: handles per-tab back stacks within the tabs screen

This ensures detail screens overlay the entire bottom bar, while tab switching
preserves each tab's internal state.

### Shared State Across Screens

Use Hilt-scoped ViewModels:

| State | Scope | Access |
|-------|-------|--------|
| Connection state | `@Singleton` | All screens via `ConnectionViewModel` |
| Current agent list | `@Singleton` | Injected `AgentRepository` |
| Chat messages | Per-conversation | `ChatViewModel` scoped to route |
| Dashboard metrics | Tab-scoped | `DashboardViewModel` |
| Settings | `@Singleton` | `DataStore<Preferences>` |

**Connection indicator** (top bar) reads from the singleton `ConnectionViewModel`
which exposes `StateFlow<ConnectionState>`. Every screen shows connection status
without duplicating logic.

**Confidence: HIGH** -- Navigation 3 is stable, well-documented, and purpose-built
for this exact use case.

---

## 7. Real-Time Dashboard Patterns

### Dashboard Layout

```
+---------------------------------------------------+
|  Dashboard                           [refresh ic]  |
+---------------------------------------------------+
|                                                    |
|  Connection: [green dot] Connected                 |
|  Gateway: casa-gateway | Uptime: 14d 3h           |
|                                                    |
|  +---------------------------------------------+  |
|  |  Active Agents       Pending Actions         |  |
|  |      5/14                  2                  |  |
|  +---------------------------------------------+  |
|                                                    |
|  Agents                                            |
|  +---+ +---+ +---+ +---+ +---+                    |
|  |Ori| |Cac| |Rox| |Her| |Joh|  <- horizontal     |
|  |[g]| |[g]| |[y]| |[g]| |[r]|     scroll chips   |
|  +---+ +---+ +---+ +---+ +---+     g=green/active  |
|                                     y=yellow/busy   |
|  Recent Activity                    r=red/error     |
|  +---------------------------------------------+  |
|  | 10:01 Oris completed: "Log analysis"        |  |
|  | 09:58 Cacho processed: Invoice #234         |  |
|  | 09:45 Roxy failed: Instagram post           |  |
|  +---------------------------------------------+  |
+---------------------------------------------------+
```

### Efficient Recomposition Strategy

The dashboard has multiple independently updating sections. **Do NOT use a single
god-state object.** Split into granular StateFlows:

```kotlin
class DashboardViewModel @Inject constructor(
    private val agentRepo: AgentRepository,
    private val metricsRepo: MetricsRepository
) : ViewModel() {

    // Each section has its own StateFlow -> independent recomposition
    val connectionState: StateFlow<ConnectionUiState>   // from singleton
    val agentSummary: StateFlow<AgentSummaryUiState>    // active count, total
    val agentChips: StateFlow<List<AgentChipUiState>>   // per-agent status
    val recentActivity: StateFlow<List<ActivityItem>>    // last N events
    val pendingActions: StateFlow<Int>                   // approval count
}
```

**Why this matters:** If metrics update every second but agent status changes every
minute, a single StateFlow would recompose the entire dashboard on every metric tick.
Split flows mean only the metrics section recomposes.

### Data Refresh Strategies

| Data | Update Method | Frequency |
|------|--------------|-----------|
| Connection status | Direct from WebSocketManager | Instant |
| Agent status | WebSocket events | As they happen |
| Pending action count | WebSocket events + REST on resume | As they happen |
| Recent activity | WebSocket events, cached in Room | As they happen |
| Metrics (v2: tokens, cost) | REST polling | Every 30s when visible |
| Uptime, system info | REST | On dashboard enter + pull-to-refresh |

### Compose Performance for Dashboard

1. **Use `derivedStateOf`** for computed values:
```kotlin
val activeAgentCount by remember {
    derivedStateOf { agents.count { it.status == AgentStatus.ACTIVE } }
}
```

2. **Use `key()` in LazyColumn** for recent activity list

3. **Use `Modifier.animateContentSize()`** for sections that change size

4. **Avoid recomposing static sections:** Extract the header, connection bar, etc.
   into separate composables with stable parameters

5. **Use `collectAsStateWithLifecycle()`** everywhere -- stops collection when
   dashboard tab is not visible

### Pull-to-Refresh

Use `PullToRefreshBox` (Material 3) to trigger a full data refresh:

```kotlin
PullToRefreshBox(
    isRefreshing = isRefreshing,
    onRefresh = { viewModel.refreshAll() }
) {
    LazyColumn { /* dashboard content */ }
}
```

**Confidence: HIGH** -- These are standard Compose performance patterns documented in
official Android developer guides and verified at droidcon 2025.

---

## Overall App Architecture

### Layer Diagram

```
+---------------------------------------------------+
|                   UI Layer                         |
|  Compose Screens + ViewModels                      |
|  (Chat, Dashboard, Agents, Settings, Pairing)     |
+---------------------------------------------------+
          |                    |
          v                    v
+-------------------+  +-------------------+
|   Domain Layer    |  |  Domain Layer     |
|   (Use Cases)     |  |  (Models)         |
|   - SendMessage   |  |  - Agent          |
|   - ApproveAction |  |  - Message        |
|   - SyncAgents    |  |  - Action         |
|   - PairDevice    |  |  - Metric         |
+-------------------+  +-------------------+
          |                    |
          v                    v
+---------------------------------------------------+
|                  Data Layer                        |
|  +---------------+  +---------------------------+ |
|  | Local Sources |  | Remote Sources            | |
|  | - Room DB     |  | - GatewayApiService (REST)| |
|  | - DataStore   |  | - WebSocketManager (WS)   | |
|  | - EncryptedSP |  | - FCM Service             | |
|  +---------------+  +---------------------------+ |
|                                                    |
|  +---------------------------------------------+  |
|  | Repositories (merge local + remote)          |  |
|  | - MessageRepository                          |  |
|  | - AgentRepository                            |  |
|  | - MetricsRepository                          |  |
|  | - AuthRepository                             |  |
|  +---------------------------------------------+  |
+---------------------------------------------------+
```

### Dependency Injection (Hilt)

```
@Singleton: OkHttpClient, Room Database, WebSocketManager,
            AuthRepository, DataStore
@ViewModelScoped: per-screen ViewModels
@ActivityRetainedScoped: (not needed -- singletons cover it)
```

### Module Organization

```
app/
  src/main/java/com/clawpilot/
    di/                     # Hilt modules
      NetworkModule.kt      # OkHttp, Retrofit, WebSocket
      DatabaseModule.kt     # Room
      RepositoryModule.kt   # Repository bindings
    data/
      local/
        db/                 # Room entities, DAOs, database
        prefs/              # DataStore, EncryptedSharedPrefs
      remote/
        api/                # Retrofit service interfaces
        ws/                 # WebSocket manager, message parser
        fcm/                # FCM service, notification builder
      repository/           # Repository implementations
    domain/
      model/                # Domain models (Agent, Message, etc.)
      usecase/              # Use cases (optional, use if logic is complex)
    ui/
      chat/                 # ChatListScreen, ChatConversationScreen
      dashboard/            # DashboardScreen
      agents/               # AgentListScreen, AgentDetailScreen
      settings/             # SettingsScreen
      pairing/              # PairDeviceScreen (QR scanner)
      components/           # Shared composables (bubbles, indicators)
      navigation/           # Routes, NavHost setup
      theme/                # Material 3 theme, colors, typography
    ClawPilotApp.kt         # Application class (@HiltAndroidApp)
    MainActivity.kt         # Single activity
```

---

## Anti-Patterns to Avoid

### Anti-Pattern 1: Keeping WebSocket Alive in Background
**What:** Using foreground service to maintain WebSocket 24/7
**Why bad:** Drains battery, Android kills it anyway, users will uninstall
**Instead:** Close WebSocket when backgrounded, use FCM for push events

### Anti-Pattern 2: Single God ViewModel
**What:** One ViewModel holding all app state
**Why bad:** Every state change recomposes everything
**Instead:** Scoped ViewModels per screen, shared state via singleton repositories

### Anti-Pattern 3: Direct Network Reads in UI
**What:** Calling REST API and displaying result directly
**Why bad:** No offline support, loading states on every screen entry, duplicate requests
**Instead:** Network -> Room -> UI (always read from Room)

### Anti-Pattern 4: Notification Messages (vs Data Messages)
**What:** Using FCM notification payload instead of data payload
**Why bad:** System handles display, no custom actions, duplicate notifications in foreground
**Instead:** Data-only messages, app builds notification with custom actions

### Anti-Pattern 5: Polling for Status Updates
**What:** Timer-based REST polling for agent status
**Why bad:** Wastes battery and bandwidth, adds latency
**Instead:** WebSocket for real-time events, REST only for initial load and catch-up

---

## Scalability Considerations

| Concern | 1-5 Agents (v1) | 10-20 Agents | 50+ Agents |
|---------|-----------------|--------------|------------|
| Chat history | Room cache, simple list | Room with pagination | Room + lazy loading + archive |
| WebSocket | Single connection, all events | Single connection, filter client-side | Single connection, server-side filtering |
| Dashboard | All agents visible | Scrollable grid, favorites | Search + filters, grouped by status |
| Notifications | All events pushed | Priority filtering | Agent-level notification settings |
| Storage | ~5MB | ~20MB | Implement aggressive TTL pruning |

For v1 (1-5 agents for most users), keep it simple. Over-engineering for 50+ agents
is premature.

---

## Sources

- [What's New in Jetpack Compose (Google I/O 2025)](https://android-developers.googleblog.com/2025/05/whats-new-in-jetpack-compose.html)
- [Compose December 2025 Release](https://android-developers.googleblog.com/2025/12/whats-new-in-jetpack-compose-december.html)
- [Announcing Jetpack Navigation 3](https://android-developers.googleblog.com/2025/05/announcing-jetpack-navigation-3-for-compose.html)
- [Navigation 3 Official Docs](https://developer.android.com/guide/navigation/navigation-3)
- [Navigation Compose 3 Stable (Dec 2025)](https://proandroiddev.com/navigation-compose-3-is-finally-stable-heres-why-it-matters-for-android-developers-6f2b59e4f022)
- [Jetchat Official Sample](https://github.com/android/compose-samples/blob/main/Jetchat/app/src/main/java/com/example/compose/jetchat/conversation/Conversation.kt)
- [Animate Text Character-by-Character (Official)](https://developer.android.com/develop/ui/compose/quick-guides/content/animate-text)
- [Compose Performance (Official)](https://developer.android.com/develop/ui/compose/performance)
- [Offline-First Architecture Guide (droidcon 2025)](https://www.droidcon.com/2025/12/16/the-complete-guide-to-offline-first-architecture-in-android/)
- [Android WebSockets with Kotlin (Bugfender)](https://bugfender.com/blog/android-websockets/)
- [OkHttp WebSocket Heartbeat Strategy](https://blog.birost.com/a?ID=01050-05041f61-1797-4329-8ffe-69e49f24da6e)
- [FCM with Jetpack Compose (Firebase Blog)](https://firebase.blog/posts/2023/08/adding-fcm-to-jetpack-compose-app/)
- [Deep Linking from Notifications (Compose)](https://medium.com/@mahbooberezaee68/deep-linking-composable-screen-route-from-notifications-using-jetpack-compose-navigation-ad4c0bd12f9e)
- [Single vs Multiple StateFlows in Compose](https://medium.com/@dnyaneshwar.patil/single-vs-multiple-stateflows-in-jetpack-compose-ece7bc399ec0)
- [SnapshotFlow vs collectAsState (droidcon 2025)](https://www.droidcon.com/2025/07/22/snapshotflow-or-collectasstate-how-to-pick-the-right-tool-for-jetpack-compose/)
- [Navigation 3 Bottom Tabs (Multiple Backstacks)](https://medium.com/@sunildhiman90/mastering-compose-navigation-3-a-deep-dive-into-navigation-3-part-2-bottom-tabs-navigation-2086c28b25fe)
- [QR Code Device Pairing Guide (2025)](https://www.imei.info/news/complete-2025-guide-secure-and-seamless-phone-pc-pairing-qrcode/)
- [Ktor WebSocket Client Docs](https://ktor.io/docs/client-websockets.html)
