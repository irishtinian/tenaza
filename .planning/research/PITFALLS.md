# Domain Pitfalls

**Project:** ClawPilot (Android app for OpenClaw multi-agent control)
**Researched:** 2026-03-25
**Overall Confidence:** MEDIUM-HIGH

---

## Critical Pitfalls

Mistakes that cause Play Store rejection, security breaches, or fundamental architecture rewrites.

---

### Pitfall 1: Play Store Rejection for Missing AI Disclosure

**Risk Level:** HIGH
**What goes wrong:** Google Play's AI-Generated Content policy (updated July 2025) requires explicit disclosure when apps interact with AI models. ClawPilot controls AI agents that generate text responses -- this falls under the policy scope. Missing disclosures or lacking in-app reporting features leads to rejection or removal.
**Why it happens:** Developers treat "remote control" apps differently from "AI content" apps, but Google's policy covers any app that displays AI-generated text to users -- including chat interfaces with AI agents.
**Consequences:** App rejected during review, or worse, removed post-publication after user reports.
**Prevention:**
- Include clear in-app disclosure that responses come from AI agents (splash screen or first-run dialog)
- Implement in-app feedback/reporting mechanism for AI-generated content (mandatory per policy)
- Add a Data Safety section declaration covering AI-generated content
- Include content filtering or at minimum a user-facing flag/report button per conversation
- Document AI usage in the Play Console's AI-Generated Content declaration form
**Detection:** Review Google's AI-Generated Content policy checklist before each submission. Test with internal track first.
**Confidence:** HIGH (verified via official Google Play Console Help articles)

---

### Pitfall 2: EncryptedSharedPreferences Deprecated -- Token Storage Migration

**Risk Level:** HIGH
**What goes wrong:** EncryptedSharedPreferences (the standard secure storage for tokens on Android) is deprecated as of late 2025. Apps built with it today will need migration, and the API may be removed in future AndroidX releases. Storing gateway auth tokens insecurely (plain SharedPreferences or hardcoded) is a direct security vulnerability.
**Why it happens:** Many tutorials still recommend EncryptedSharedPreferences. The deprecation is recent and migration guidance is still sparse.
**Consequences:** Security vulnerability if tokens stored insecurely. Technical debt if built on deprecated API. Token leakage on rooted devices if not using hardware-backed keystore.
**Prevention:**
- Use DataStore + Google Tink for encrypted preferences (the official replacement path). Tink encrypts the entire data stream before disk write, superior to per-value encryption
- Back tokens with Android Keystore for hardware-level protection
- Implement short-lived tokens with refresh rotation -- even if a token leaks, damage window is limited
- Never store tokens in plain text, SQLite, or unencrypted files
- Plan the DataStore+Tink approach from day one to avoid migration pain
**Detection:** Static analysis with lint rules checking for plain SharedPreferences usage with sensitive keys.
**Confidence:** HIGH (verified via ProAndroidDev migration guide Dec 2025 and official Android docs)

---

### Pitfall 3: WebSocket Battery Drain Killing User Retention

**Risk Level:** HIGH
**What goes wrong:** Maintaining a persistent WebSocket connection to the OpenClaw gateway drains battery aggressively. Users notice the app in their battery stats, uninstall it, and leave 1-star reviews. The Home Assistant Android app had exactly this problem (GitHub issue #6208) -- WebSocket heartbeats at default frequency caused significant battery drain.
**Why it happens:** WebSocket connections are persistent TCP connections requiring regular heartbeats. Each heartbeat wakes the radio, preventing deep sleep. On cellular, this is catastrophic for battery.
**Consequences:** 2-3x higher battery consumption than necessary. Poor Play Store ratings. Users disabling the app.
**Prevention:**
- Use a hybrid architecture: FCM for push notifications (agent alerts, task completion) + on-demand WebSocket only when app is in foreground and user is actively chatting
- Never maintain WebSocket in background -- kill it in onStop(), reconnect in onStart()
- If background monitoring is needed, use FCM high-priority messages to wake the app, not persistent connections
- Make WebSocket heartbeat interval configurable (Home Assistant learned this the hard way)
- Use WorkManager for periodic status polling if FCM is not available, respecting Doze windows
**Detection:** Monitor battery usage in Android Profiler during development. Test with device unplugged for 8+ hours with app installed.
**Confidence:** HIGH (verified via real-world issues in Home Assistant, ntfy, and Android developer docs)

---

### Pitfall 4: Doze Mode Killing Background Connectivity

**Risk Level:** HIGH
**What goes wrong:** Android Doze mode (API 23+) suspends all network access during idle periods. Any background WebSocket connection will be killed. FCM high-priority messages are the ONLY reliable way to reach a Doze-sleeping device. Apps that try to work around Doze with wakelocks or battery optimization exemptions get flagged by Play Store review.
**Why it happens:** Developers test on devices plugged in via USB (Doze disabled) and never encounter the issue until production.
**Consequences:** Users miss critical agent alerts. The app appears unreliable. Attempting workarounds (REQUEST_IGNORE_BATTERY_OPTIMIZATIONS) triggers Play Store policy flags.
**Prevention:**
- Architecture must be FCM-first for any background notifications
- Test with `adb shell dumpsys deviceidle force-idle` to simulate Doze
- Do NOT request battery optimization exemption unless absolutely necessary -- Google reviews this and can reject apps that abuse it
- For foreground service (e.g., active chat session), declare proper foregroundServiceType (API 34+ mandatory): use `dataSync` or `connectedDevice` type
- On API 33+, request POST_NOTIFICATIONS runtime permission explicitly
**Detection:** Test on physical device with screen off for 30+ minutes, verify notifications still arrive via FCM.
**Confidence:** HIGH (verified via official Android Doze documentation)

---

### Pitfall 5: Man-in-the-Middle Attacks on Gateway Communication

**Risk Level:** HIGH
**What goes wrong:** ClawPilot communicates with OpenClaw gateway (currently on localhost:18789, but will need remote access). Without certificate pinning and TLS, anyone on the same WiFi can intercept gateway commands, steal auth tokens, and control the user's AI agents.
**Why it happens:** Development happens on localhost where TLS feels unnecessary. The jump to remote access often skips proper security.
**Consequences:** Full agent takeover. Attacker can send commands to all agents via stolen gateway token. Data exfiltration of agent conversations.
**Prevention:**
- Enforce TLS for all gateway communication, even on LAN (use self-signed certs with pinning for local deployments)
- Implement certificate pinning via OkHttp's CertificatePinner -- pin the gateway's public key, not the full certificate (easier rotation)
- Use OkHttp 4.x+ (older versions had pinning bypass vulnerabilities)
- Add mutual TLS (mTLS) for gateway auth as a premium feature
- Implement token binding -- tie tokens to device fingerprint so stolen tokens can't be replayed from other devices
**Detection:** Test with mitmproxy -- the app should refuse to connect when intercepted.
**Confidence:** HIGH (well-established security practice, verified via OkHttp docs and NowSecure guidance)

---

### Pitfall 6: FCM Delivery Failure on Chinese OEM Devices (30-45% delivery rate)

**Risk Level:** HIGH
**What goes wrong:** Xiaomi, Huawei, Oppo, Vivo, and OnePlus devices aggressively kill background services and suppress FCM delivery. Actual notification delivery rates on these devices range from 30-45% (vs. Google's claimed 99%). Users on these devices simply never receive agent alerts.
**Why it happens:** Chinese OEMs implement custom battery optimization that kills Google Play Services connections. This is by design from the OEM side and cannot be fixed by the app developer alone.
**Consequences:** App appears broken for a significant portion of Android users (Chinese OEMs represent 40-50% of global Android market). Users blame ClawPilot, not their phone.
**Prevention:**
- Implement fallback notification strategy: FCM high-priority as primary, with in-app polling as fallback when FCM fails
- Guide users to whitelist ClawPilot from battery optimization (show a setup wizard on first launch detecting OEM)
- Use FCM high-priority messages (not normal priority) -- these have better delivery on restricted devices
- Consider integrating Xiaomi MiPush and Huawei Push Kit for those specific OEMs (significant effort but dramatically improves delivery)
- Show a clear "notification troubleshooting" screen that detects the OEM and provides manufacturer-specific instructions (AutoStart, battery saver whitelist, etc.)
- Track delivery rates with acknowledgment pings -- if a device stops acknowledging, surface a warning in the web dashboard
**Detection:** Test on Xiaomi and Huawei physical devices with app swiped from recents. Verify notification delivery after 30 minutes idle.
**Confidence:** HIGH (verified via CleverTap research, Pushy.me documentation, and community reports)

---

## Moderate Pitfalls

Issues that degrade UX or create ongoing maintenance burden.

---

### Pitfall 7: WebSocket Silent Disconnects on Mobile Networks

**Risk Level:** MEDIUM-HIGH
**What goes wrong:** Mobile network transitions (WiFi to 4G, tower handoffs, tunnel entry/exit) silently kill WebSocket connections. The TCP stack reports the connection as OPEN, but no data flows. The onClose callback never fires. Users see a "connected" indicator while the app is actually dead.
**Why it happens:** Network transitions change the client's IP address, invalidating the TCP connection. Cellular NAT gateways also drop idle mappings in as little as 30 seconds.
**Prevention:**
- Implement application-level heartbeat: ping every 20-25 seconds, expect pong within 5 seconds
- If 2-3 consecutive heartbeats fail, declare connection dead and reconnect
- Use exponential backoff for reconnection (1s, 2s, 4s, 8s, max 30s)
- Register a ConnectivityManager.NetworkCallback to detect network changes and proactively reconnect
- Show connection state prominently in the UI -- never let the user think they're connected when they're not
- Design protocol for state recovery on reconnect: the gateway should send missed messages since last known sequence number
**Detection:** Test by toggling airplane mode, switching WiFi on/off, and walking through areas with poor signal.
**Confidence:** HIGH (verified via WebSocket.org guides and real-world issue reports from ntfy, Meteor, Home Assistant)

---

### Pitfall 8: LazyColumn Recomposition Jank During Streaming Chat

**Risk Level:** MEDIUM-HIGH
**What goes wrong:** As AI agents stream text responses, each character/chunk update triggers recomposition. Without proper optimization, the entire LazyColumn recomposes on every text update, causing visible jank (dropped frames). This is the #1 performance complaint in Compose chat apps.
**Why it happens:** Streaming text means rapid state changes. If the streaming text state is observed at the LazyColumn level (rather than isolated to the individual message composable), every update recomposes the entire list.
**Consequences:** Janky scrolling (37 FPS instead of 60 FPS). Poor perceived performance. Users compare unfavorably to ChatGPT/Claude native apps.
**Prevention:**
- Isolate streaming state: each message composable should observe its own state, not a list-level state
- Use `key()` on every LazyColumn item -- without keys, position-based diffing causes unnecessary recomposition on list changes
- Mark message data classes as `@Stable` or `@Immutable` to help Compose skip unchanged items
- Use `derivedStateOf` for computed values (e.g., message count, scroll position)
- Buffer streaming chunks -- append every 50-100ms instead of per-character to reduce recomposition frequency
- Profile with Compose Compiler metrics and Layout Inspector recomposition counts
- Consider using `Text` with `AnnotatedString.Builder` for incremental text building instead of replacing the entire string
**Detection:** Enable recomposition counts in Layout Inspector. Profile with systrace during active streaming.
**Confidence:** HIGH (verified via multiple Compose performance guides and developer experience reports)

---

### Pitfall 9: Large Conversation History Causing OOM

**Risk Level:** MEDIUM
**What goes wrong:** Long conversations with AI agents (especially those with code blocks, images, or formatted text) accumulate in memory. LazyColumn holds all items in its state even if virtualized, and complex AnnotatedString objects for rendered markdown are expensive. After 500+ messages, the app crashes with OutOfMemoryError.
**Why it happens:** Chat apps commonly load entire conversation history into a single list state. Unlike RecyclerView, Compose's LazyColumn has higher per-item overhead.
**Prevention:**
- Implement conversation pagination: load only the last 50-100 messages, load more on scroll-up
- Store conversations in a local database (Room) and use Paging 3 with LazyColumn for efficient loading
- Limit in-memory rendered markdown: only render visible messages, cache rendered AnnotatedStrings with size limit
- Implement conversation archiving: automatically archive conversations older than N days
- Monitor memory usage with Android Profiler and set a hard cap on loaded messages
**Detection:** Stress test with 1000+ messages including code blocks and formatted text. Monitor heap allocation.
**Confidence:** MEDIUM (extrapolated from general Compose memory patterns and chat app architecture experience)

---

### Pitfall 10: Stale Data Display After Reconnection

**Risk Level:** MEDIUM
**What goes wrong:** After a connection drop and reconnection, the app displays outdated agent status (showing "running" when an agent crashed 10 minutes ago, or showing old conversation messages). Users make decisions based on stale information.
**Why it happens:** The app caches the last known state and displays it immediately on reconnect before fresh data arrives. The "loading" state is too brief or nonexistent.
**Consequences:** User trusts stale data and takes wrong actions. Worse than showing nothing.
**Prevention:**
- Implement a freshness indicator: show how old the displayed data is ("updated 2 minutes ago", "updating...")
- On reconnect, mark ALL cached data as potentially stale (dim it, show overlay) until fresh data confirms or replaces it
- Gateway protocol should include a "full state sync" message on reconnect, not incremental updates only
- Cache invalidation strategy: agent status expires after 60 seconds, conversations after 5 minutes
- Optimistic UI for user actions, but pessimistic UI for agent status -- always show the last confirmed state, not assumed state
**Detection:** Simulate: disconnect for 5 minutes while agent status changes on server, reconnect, verify UI shows fresh data within 2 seconds.
**Confidence:** MEDIUM (based on general mobile architecture patterns)

---

### Pitfall 11: Foreground Service Type Declaration (API 34+ Mandatory)

**Risk Level:** MEDIUM
**What goes wrong:** Starting Android 14 (API 34), every foreground service MUST declare a `foregroundServiceType` in the manifest. Missing this causes a runtime exception. Additionally, API 33+ requires runtime POST_NOTIFICATIONS permission -- without it, no notifications appear and the user gets no error message.
**Why it happens:** Incremental API requirements that developers miss when targeting the latest SDK.
**Consequences:** App crashes on Android 14+ devices. Silent notification failure on Android 13+.
**Prevention:**
- Declare `android:foregroundServiceType="dataSync"` for the WebSocket service (or `connectedDevice` if controlling a gateway)
- Request `android.permission.FOREGROUND_SERVICE` and `android.permission.FOREGROUND_SERVICE_DATA_SYNC` in manifest
- Request `POST_NOTIFICATIONS` runtime permission on API 33+ at first launch, with clear rationale dialog
- Create notification channels in Application.onCreate() before any notification is posted
- Define at minimum 3 channels: "Agent Alerts" (high importance), "Connection Status" (low importance), "Chat Messages" (default importance)
**Detection:** Test on Android 14 and 15 emulators. Verify foreground service starts without exception. Verify notification permission flow.
**Confidence:** HIGH (verified via official Android developer documentation)

---

### Pitfall 12: Latency UX -- Making Remote Control Feel Responsive

**Risk Level:** MEDIUM
**What goes wrong:** Commands sent to agents have inherent network latency (100ms-2s depending on connection). If the UI waits for server confirmation before showing feedback, the app feels sluggish. If it shows optimistic updates, failed commands create confusion.
**Why it happens:** Remote control apps have a fundamental tension between responsiveness and accuracy.
**Prevention:**
- Implement optimistic UI for user commands: show "sending..." state immediately, transition to "sent" on server ack, show error if timeout (3s)
- Use visual feedback hierarchy: haptic on tap -> immediate UI change -> server confirmation -> final state
- For agent commands (start/stop/message), show a pending state with cancel option
- Never block the UI thread waiting for a response
- Show latency indicator somewhere accessible (e.g., connection quality icon: green/yellow/red)
- Timeout strategy: 3s for command ack, 30s for agent response start, 5min for task completion
**Detection:** Test on throttled connection (Android emulator network throttling: 3G profile). Verify UI remains responsive.
**Confidence:** MEDIUM (based on UX best practices for remote control apps)

---

## Minor Pitfalls

Issues that are annoying but recoverable.

---

### Pitfall 13: Apache 2.0 License Compliance with Dependencies

**Risk Level:** LOW-MEDIUM
**What goes wrong:** ClawPilot is Apache 2.0, but dependencies may have GPL, LGPL, or other copyleft licenses that conflict. Including a GPL library in an Apache-licensed app creates a license violation. Also, failing to display open source notices violates most licenses.
**Why it happens:** Transitive dependencies (dependency of a dependency) often include licenses developers don't check.
**Prevention:**
- Use the `oss-licenses-plugin` from Google Play Services to auto-generate and display license notices
- Run `./gradlew dependencies` and audit licenses before each release
- Consider using a license-checking Gradle plugin (e.g., `licensee` by Cash App) in CI
- Avoid GPL-licensed libraries entirely -- stick to Apache 2.0, MIT, BSD-compatible dependencies
- Include NOTICE file in the repository and in the APK assets
- The "OpenClaw" name in "ClawPilot" is fine -- you own the OpenClaw project. But register a trademark if going commercial to prevent clones
**Detection:** License audit in CI pipeline. Manual review of new dependencies.
**Confidence:** MEDIUM (well-established legal patterns, but specific license interactions vary)

---

### Pitfall 14: Play Integrity API -- Rooted Device Handling

**Risk Level:** LOW-MEDIUM
**What goes wrong:** Developers implement Play Integrity checks to block rooted devices, but the bypass ecosystem is mature (PlayIntegrityFix Magisk module passes even STRONG_INTEGRITY on some devices). Blocking rooted devices also alienates power users who are likely your target audience for an agent control app.
**Why it happens:** Security theater -- blocking rooted devices feels like security but provides minimal actual protection against determined attackers.
**Prevention:**
- Do NOT block rooted devices -- ClawPilot's target users (developers, power users) are disproportionately likely to have rooted devices
- Instead, focus on token security: use Android Keystore with hardware-backed keys (works even on rooted devices for key extraction prevention)
- Warn users on rooted devices that token security may be reduced, but don't block them
- Use Play Integrity only for optional features (e.g., premium license verification), not as a gate
**Detection:** Test on both rooted and unrooted devices. Verify core functionality works on both.
**Confidence:** MEDIUM (strategic decision more than technical -- the Play Integrity bypass arms race is well-documented)

---

### Pitfall 15: Notification Channel Misconfiguration Across Android Versions

**Risk Level:** LOW-MEDIUM
**What goes wrong:** Notification channels, once created, cannot be programmatically modified for importance level -- only the user can change it. If you create a channel with wrong importance at first install, you're stuck with it unless you delete and recreate with a new channel ID (which resets user preferences).
**Why it happens:** Developers don't plan channel strategy upfront and need to change it post-launch.
**Prevention:**
- Plan notification channels carefully before v1.0 -- changing them later is painful
- Use versioned channel IDs (e.g., "agent_alerts_v1") so you can migrate if needed
- Suggested channels for ClawPilot:
  - `agent_alerts_v2` (HIGH importance) -- agent errors, task completions
  - `chat_messages_v1` (DEFAULT importance) -- new messages from agents
  - `connection_status_v1` (LOW importance) -- connection lost/restored
  - `updates_v1` (MIN importance) -- app update available
- Test on Android 8, 12, 13, 14, and 15 to verify channel behavior across versions
**Detection:** Verify in Settings > Apps > ClawPilot > Notifications that all channels appear with correct defaults.
**Confidence:** HIGH (verified via official Android notification channel documentation)

---

### Pitfall 16: Google Play's Verified Developer Enforcement (2026)

**Risk Level:** LOW (for now, HIGH in future)
**What goes wrong:** Google announced in August 2025 that starting September 2026, ALL apps on certified Android devices (including sideloaded) must come from verified developers. This is rolling out initially in Brazil, Indonesia, Singapore, and Thailand, with likely expansion. For an open-source app that users might want to build and sideload, this could prevent self-built versions from running.
**Why it happens:** Google's response to app store bills and sideloading security concerns.
**Prevention:**
- Distribute via Play Store as primary channel (already planned)
- For open-source builds, provide clear instructions for users to register as developers
- Monitor the "Keep Android Open" movement and policy changes
- Consider F-Droid as an alternative distribution channel for open-source builds
- GitHub Releases with signed APKs remain viable outside affected regions for now
**Detection:** Monitor Google Play policy announcements quarterly.
**Confidence:** MEDIUM (policy announced but enforcement timeline and scope still evolving)

---

## Phase-Specific Warnings

| Phase Topic | Likely Pitfall | Mitigation |
|-------------|---------------|------------|
| Project setup & architecture | Choosing EncryptedSharedPreferences (deprecated) | Use DataStore + Tink from day one |
| WebSocket connection layer | Battery drain from persistent background connection | FCM-first architecture, foreground-only WebSocket |
| Chat UI implementation | LazyColumn recomposition jank during streaming | Isolate streaming state per message, use keys, buffer chunks |
| Push notifications | FCM failures on Chinese OEMs | OEM detection + whitelist wizard + fallback polling |
| Play Store submission | Missing AI content disclosure | Add disclosure dialog, feedback button, data safety declaration |
| Security hardening | Plain token storage | Android Keystore + DataStore+Tink, certificate pinning |
| Background services | Foreground service crashes on API 34+ | Declare foregroundServiceType, request all permissions |
| Offline/reconnection | Stale data after reconnect | Freshness indicators, full state sync protocol |
| Open source release | License conflicts in transitive deps | License audit plugin in CI, NOTICE file |
| Scale to production | Network handoff silent disconnects | App-level heartbeat, ConnectivityManager callback, reconnect with state recovery |

---

## Sources

### Google Play Policy
- [Understanding Google Play's AI-Generated Content policy](https://support.google.com/googleplay/android-developer/answer/14094294?hl=en)
- [AI-Generated Content Policy](https://support.google.com/googleplay/android-developer/answer/13985936?hl=en)
- [Policy announcement: July 10, 2025](https://support.google.com/googleplay/android-developer/answer/16296680)
- [Navigating AI Rejections in App & Play Store](https://appitventures.com/blog/navigating-ai-rejections-app-store-play-store-submissions)
- [Google's 2025 Android App Policy Overhaul](https://www.webpronews.com/googles-2025-android-app-policy-overhaul-safety-and-privacy-focus/)

### Battery & Background
- [Optimize for Doze and App Standby (official)](https://developer.android.com/training/monitoring-device-state/doze-standby)
- [WebSocket ping frequency battery issue - Home Assistant](https://github.com/home-assistant/android/issues/6208)
- [WebSockets and Android apps - Ably](https://ably.com/topic/websockets-android)
- [Build Real-Time Android Apps with WebSockets - Bugfender](https://bugfender.com/blog/android-websockets/)

### Security
- [Goodbye EncryptedSharedPreferences: 2026 Migration Guide](https://proandroiddev.com/goodbye-encryptedsharedpreferences-a-2026-migration-guide-4b819b4a537a)
- [EncryptedSharedPreferences Deprecated (droidcon)](https://www.droidcon.com/2025/12/16/goodbye-encryptedsharedpreferences-a-2026-migration-guide/)
- [Secure Token Storage Best Practices](https://capgo.app/blog/secure-token-storage-best-practices-for-mobile-developers/)
- [Certificate Pinning & MITM - NowSecure](https://www.nowsecure.com/blog/2017/06/15/certificate-pinning-for-android-and-ios-mobile-man-in-the-middle-attack-prevention/)
- [SSL Pinning with OkHttp](https://nextnative.dev/blog/android-ssl-certificate-pinning)
- [Play Integrity API Limitations](https://securityboulevard.com/2025/11/the-limitations-of-google-play-integrity-api-ex-safetynet-2/)

### Compose Performance
- [Compose Performance Best Practices (official)](https://developer.android.com/develop/ui/compose/performance/bestpractices)
- [Boost LazyColumn Performance](https://coldfusion-example.blogspot.com/2025/01/boost-lazycolumn-performance-in-jetpack.html)
- [Fix Compose Recomposition Issues: 2025 Guide](https://www.brahimoubbad.com/2025/05/fix-compose-recomposition-issues.html)
- [Stop LazyColumn Constant Recomposing](https://dev.to/theplebdev/the-refactors-i-did-to-stop-my-jetpack-compose-lazycolumn-from-constantly-recomposing-57l0)

### Push Notifications
- [Why Chinese Devices Fail to Receive Notifications - Pushy](https://support.pushy.me/hc/en-us/articles/360043864791)
- [Why Push Notifications Go Undelivered - CleverTap](https://clevertap.com/blog/why-push-notifications-go-undelivered-and-what-to-do-about-it/)
- [Future of Push: Beyond Mi Push Deprecation](https://clevertap.com/blog/the-future-of-push-notifications-navigating-beyond-mi-push-deprecation/)

### WebSocket Reliability
- [Fix WebSocket Timeout and Silent Drops](https://websocket.org/guides/troubleshooting/timeout/)
- [Troubleshooting Connection Issues - Socket.IO](https://socket.io/docs/v4/troubleshooting-connection-issues/)

### Android Notifications & Services
- [Foreground Service Types (official)](https://developer.android.com/develop/background-work/services/fgs/service-types)
- [Create Notification Channels (official)](https://developer.android.com/develop/ui/views/notifications/channels)
- [Foreground Services in Android 14](https://proandroiddev.com/foreground-services-in-android-14-whats-changing-dcd56ad72788)

### Licensing & Distribution
- [Include Open Source Notices - Google](https://developers.google.com/android/guides/opensource)
- [Keep Android Open: 2026 Rule Pushback](https://dev.to/maverickjkp/keep-android-open-developers-push-back-on-googles-2026-rule-p31)

### Latency & Offline UX
- [Minimize Effect of Regular Updates (official)](https://developer.android.com/develop/connectivity/minimize-effect-regular-updates)
- [Understanding Latency in Mobile & Android Systems](https://medium.com/@subhrajeetpandey2001/understanding-latency-in-mobile-android-systems-an-end-to-end-perspective-including-jetpack-9712af44d8aa)
