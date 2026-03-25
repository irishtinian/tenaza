# Tenaza User Manual

> Control your OpenClaw multi-agent AI system from your Android phone.

---

## Table of Contents

1. [Getting Started](#getting-started)
2. [Dashboard](#dashboard)
3. [Chat](#chat)
4. [Arena](#arena)
5. [Cron Management](#cron-management)
6. [Agent Detail](#agent-detail)
7. [Settings](#settings)
8. [Troubleshooting](#troubleshooting)
9. [FAQ](#faq)

---

## Getting Started

### Requirements

- Android phone running **Android 8.0** (API 26) or later
- A running **OpenClaw gateway** (v2026.3+) accessible from your phone
- Your gateway **auth token**

### Installation

**Option A: GitHub Releases**
1. Go to [Releases](https://github.com/irishtinian/tenaza/releases).
2. Download the latest `.apk` file.
3. Open the APK on your phone and follow the install prompts.
4. You may need to enable "Install from unknown sources" in your device settings.

**Option B: Google Play** (coming soon)

### First Connection

1. Open Tenaza.
2. You will see the pairing screen.
3. Tap **"Enter URL manually"**.
4. Enter your gateway WebSocket URL:
   - **Local network:** `ws://192.168.1.x:18789`
   - **Remote (TLS):** `wss://your-domain.com:18789`
5. Enter your gateway auth token.
6. Tap **Connect**.

On first launch, the app generates an **Ed25519 keypair** unique to your device. This identity is used for all subsequent authentication — no passwords required.

Once connected, you will be taken to the **Dashboard**.

### Remote Access

If you want to use Tenaza outside your local network, you need to expose your OpenClaw gateway. Common approaches:

| Method | Complexity | TLS |
|--------|-----------|-----|
| Tailscale | Low | Automatic |
| Cloudflare Tunnel | Medium | Automatic |
| Reverse proxy (nginx/Caddy) | Medium | Manual cert setup |
| VPS with port forwarding | High | Manual cert setup |

> **Important:** Always use `wss://` (TLS) for remote connections. Tenaza enforces TLS for non-localhost URLs.

---

## Dashboard

The Dashboard is your home screen. It provides a real-time overview of your entire OpenClaw system.

### Gateway Health

At the top of the Dashboard, a status indicator shows the gateway connection state:

| Status | Meaning |
|--------|---------|
| **Online** (green) | Gateway connected and responding normally |
| **Degraded** (yellow) | Gateway connected but some services are impaired |
| **Offline** (red) | Cannot reach gateway |

### Agents

The agent grid displays all configured agents. Each card shows:

- **Name and emoji** (e.g., Oris, Tech)
- **Model** currently assigned (e.g., Claude Sonnet 4)
- **Fallback model** if configured
- **Activity indicator** for agents with active sessions

Tap any agent card to open a **Chat** session with that agent.

### Channels

The channels section shows the status of external integrations:

- **Telegram** — connected or disconnected
- **Discord** — connected or disconnected (if configured)

A disconnected channel triggers a notification if notifications are enabled.

### Cron Jobs

A summary badge shows the total number of active cron jobs. Tap to navigate to the full **Cron Management** screen.

### Cost & Token Tracker

At the bottom of the Dashboard, a summary displays:

- Total tokens consumed (across all agents)
- Per-agent token breakdown (expandable)
- Estimated cost based on model pricing

---

## Chat

### Starting a Conversation

1. Tap an agent on the **Dashboard**, or
2. Open the **Chat** screen and use the agent picker at the top.

### Sending Messages

Type your message in the input field and tap **Send**. The agent will respond in real-time with streaming text (token by token).

### Conversation History

Previous messages are displayed in the chat view. Conversations are cached locally so you can review them even when offline.

- Scroll up to load older messages.
- Each message shows the sender and timestamp.

### Switching Agents

Use the agent picker dropdown at the top of the Chat screen to switch between agents. Your conversation history with each agent is preserved.

### Status Indicators

While an agent is processing your message, you may see:

- **Thinking...** — the agent is generating a response
- **Executing tool** — the agent is running a command or tool
- **Streaming** — response tokens are arriving

---

## Arena

Arena is a multi-agent group chat. It lets you have a conversation with multiple agents simultaneously.

### Creating a Group Chat

1. Navigate to the **Arena** screen.
2. Select **2 to 6 agents** from the agent list.
3. Tap **Start Arena**.

### Sending Messages

In Arena, you can direct messages to specific agents or broadcast to all:

| Syntax | Behavior |
|--------|----------|
| `@oris what do you think?` | Sends only to Oris |
| `@tech check the logs` | Sends only to Tech |
| `@todos summarize the plan` | Broadcasts to all agents in the Arena |
| (no @mention) | Broadcasts to all agents |

### How It Works

- Each agent receives your message (or the targeted message) independently.
- Responses stream in from each agent and are displayed with the agent's name and emoji.
- Agents do not see each other's responses unless you explicitly share them.

### Tips

- Use Arena for brainstorming, getting multiple perspectives, or coordinating tasks across agents.
- Keep the group to 2-3 agents for focused discussions; use up to 6 for broad ideation.

---

## Cron Management

Cron jobs are scheduled tasks that run on your OpenClaw gateway. Tenaza lets you view and manage them remotely.

### Viewing Cron Jobs

The Cron screen lists all jobs with:

| Column | Description |
|--------|-------------|
| **Name** | Job identifier |
| **Schedule** | Cron expression or interval (e.g., `*/30 * * * *`) |
| **Status** | Enabled or Disabled |
| **Last Run** | Timestamp and result (success/failure) |
| **Next Run** | When the job will execute next |

### Actions

- **Toggle Enable/Disable:** Tap the switch next to any job to enable or disable it. Disabled jobs will not run on schedule.
- **Run Now:** Tap the play button to trigger immediate execution regardless of schedule.
- **Delete:** Long-press a job (or tap the delete icon) and confirm to permanently remove it.

> **Note:** Changes take effect immediately on your gateway.

---

## Agent Detail

Tap any agent card on the Dashboard (or the info icon in Chat) to view the full agent detail screen.

### Configuration

- **Model:** The AI model the agent uses (e.g., Claude Sonnet 4, GPT-5)
- **Fallback Model:** Backup model if the primary is unavailable
- **Heartbeat:** Interval at which the agent checks in

### Workspace Files

Browse the agent's workspace files in read-only mode:

- **SOUL.md** — The agent's personality and instructions
- **MEMORY.md** — Persistent memory across conversations
- Other workspace files

> Files are displayed as-is from the gateway. Editing is not supported from mobile.

### Recent Sessions

View a list of the agent's recent conversation sessions, including timestamps and token usage.

---

## Settings

Access Settings from the gear icon in the top navigation.

### Theme

Choose your preferred appearance:

| Option | Behavior |
|--------|----------|
| **System** | Follows your device's light/dark mode setting |
| **Light** | Always light theme |
| **Dark** | Always dark theme |

### Notifications

Configure which notifications Tenaza sends:

- **Exec Approvals** — When an agent needs permission to run a command
- **Channel Alerts** — When a Telegram/Discord channel disconnects
- **Cron Failures** — When a scheduled job fails

Each category can be toggled independently.

### Biometric Lock

When enabled, Tenaza requires biometric authentication (fingerprint, face, or device PIN) to open the app.

1. Toggle **Biometric Lock** on.
2. Confirm with your biometric or PIN.
3. Next time you open the app, you will be prompted to authenticate.

### Gateway Info

Displays:
- Connected gateway URL
- Gateway version
- Device identity fingerprint (Ed25519 public key)

### Unpair Device

To disconnect from your gateway:

1. Tap **Unpair Device**.
2. Confirm the action.

This removes your gateway credentials and device pairing from the app. You will need to pair again to reconnect.

---

## Troubleshooting

### Connection Timeout

**Symptom:** The app shows "Connecting..." indefinitely or times out.

**Solutions:**
1. Verify your gateway is running and accessible from another device.
2. Check that the URL is correct (including port number).
3. Ensure your phone is on the same network as the gateway (for LAN connections).
4. If using a firewall, confirm port 18789 (or your custom port) is open.
5. Try opening the gateway URL in a browser to verify connectivity.

### Cleartext Traffic Error

**Symptom:** Connection fails with a cleartext/security error when using `ws://`.

**Solutions:**
- Android 9+ blocks unencrypted HTTP/WS by default.
- For **local development only**, this is handled automatically for localhost/LAN addresses.
- For **remote connections**, always use `wss://` with a valid TLS certificate.

### Reconnecting Loop

**Symptom:** The app repeatedly connects and disconnects.

**Solutions:**
1. Check your auth token is still valid on the gateway.
2. Verify no other device is already paired (single device limit).
3. Restart the gateway and try again.
4. In Settings, tap **Unpair Device**, then re-pair from scratch.

### Notifications Not Appearing

**Symptom:** You are not receiving push notifications.

**Solutions:**
1. Ensure the Tenaza foreground service is running (persistent notification in the notification shade).
2. Check that notification categories are enabled in Settings.
3. Check Android system settings: Settings > Apps > Tenaza > Notifications.
4. Disable battery optimization for Tenaza: Settings > Battery > Tenaza > Unrestricted.
5. Some manufacturers (Xiaomi, Huawei, Samsung) aggressively kill background services. Check [Don't Kill My App](https://dontkillmyapp.com/) for device-specific instructions.

### Biometric Lock Not Working

**Symptom:** Biometric prompt does not appear or fails.

**Solutions:**
1. Ensure you have a fingerprint, face, or PIN enrolled in your device settings.
2. Restart the app.
3. Toggle biometric lock off and on again in Settings.

### WebSocket Disconnects on Mobile Data

**Symptom:** Connection drops when switching from Wi-Fi to mobile data.

**Solutions:**
- Tenaza automatically reconnects with exponential backoff.
- If it does not reconnect within 30 seconds, open the app to trigger a manual reconnect.
- Ensure mobile data is not restricted for Tenaza in Android settings.

---

## FAQ

**Q: What is OpenClaw?**
A: OpenClaw is a self-hosted multi-agent AI framework that orchestrates multiple AI agents (e.g., coding assistants, automation bots) on your own infrastructure. Tenaza is the mobile remote control for it.

**Q: Do I need a cloud account or subscription?**
A: No. Tenaza connects directly to your self-hosted OpenClaw gateway. There is no cloud service, no account, and no subscription required.

**Q: Is my data sent to any third party?**
A: No. All communication is between your phone and your gateway. Tenaza does not phone home, collect analytics, or transmit data to external servers.

**Q: Can I connect to multiple gateways?**
A: Not in v1. Tenaza supports one gateway connection at a time. Multi-gateway support is planned for a future release.

**Q: What happens if I lose my phone?**
A: Your gateway auth token is encrypted on the device. To revoke access, regenerate the auth token on your gateway. The lost device will no longer be able to authenticate.

**Q: Can I use Tenaza on a tablet?**
A: Yes. Tenaza works on any Android device running Android 8.0 or later, including tablets.

**Q: How do I update the app?**
A: If installed from Google Play, updates arrive automatically. If sideloaded from GitHub, download the latest APK from the Releases page and install it over the existing version.

**Q: Can I edit agent SOUL.md or MEMORY.md files from Tenaza?**
A: No. Workspace files are read-only in Tenaza. Use the OpenClaw Control UI (web dashboard) on a desktop for editing.

**Q: What ports does Tenaza use?**
A: Tenaza connects to whatever port your OpenClaw gateway is running on (default: 18789). No additional ports are needed on the phone.

**Q: Is the source code available?**
A: Yes. Tenaza is open source under the Apache 2.0 license. See the [GitHub repository](https://github.com/irishtinian/tenaza).

---

*For additional help, open an issue on [GitHub](https://github.com/irishtinian/tenaza/issues).*
