# Tenaza

**Your AI agents, in your pocket.**

[![License](https://img.shields.io/badge/License-Apache_2.0-blue.svg)](https://opensource.org/licenses/Apache-2.0)
[![Android](https://img.shields.io/badge/Android-8.0%2B-green.svg)](https://developer.android.com)
[![Kotlin](https://img.shields.io/badge/Kotlin-2.1-purple.svg)](https://kotlinlang.org)
[![Jetpack Compose](https://img.shields.io/badge/Jetpack_Compose-Material_3-4285F4.svg)](https://developer.android.com/jetpack/compose)

Tenaza is a native Android app for remotely controlling [OpenClaw](https://github.com/openclaw) multi-agent AI systems from your phone. Connect to your self-hosted gateway over WebSocket, chat with agents, manage cron jobs, and approve actions on the go — all secured with Ed25519 device authentication and optional biometric lock.

---

## Screenshots

> Screenshots coming soon. Below is a description of the main screens.

| Screen | Description |
|--------|-------------|
| **Dashboard** | Gateway health indicator, agent grid with model info, Telegram channel status, cron count, cost/token summary |
| **Chat** | Real-time streaming conversation with any agent, message history, agent picker |
| **Arena** | Multi-agent group chat with 2-6 agents, @mention routing (@oris, @tech, @todos) |
| **Cron Manager** | List of cron jobs with schedule, toggle enable/disable, run now, delete |
| **Agent Detail** | Agent config (model, heartbeat, fallbacks), workspace file browser, recent sessions |
| **Settings** | Theme toggle, notification controls, biometric lock, gateway info, unpair |

---

## Features

### Dashboard
- Gateway health status (online / degraded / offline)
- 21+ agents displayed with name, emoji, model, and fallback info
- Telegram channel connection status
- Active cron job count
- Per-agent cost and token usage tracker

### Chat
- Send messages to any agent
- Real-time streaming responses (token by token)
- Conversation history with local caching
- Agent picker for quick switching

### Arena
- Multi-agent group chat — select 2 to 6 agents
- Route messages to specific agents with `@mentions` (e.g., `@oris`, `@tech`)
- Broadcast to all agents with `@todos`

### Cron Management
- View all cron jobs with schedule, status, and last run info
- Enable or disable jobs with a toggle
- Trigger immediate execution ("Run Now")
- Delete jobs with confirmation

### Agent Detail
- View agent configuration: model, heartbeat interval, fallback chain
- Browse workspace files read-only (SOUL.md, MEMORY.md, etc.)
- View recent sessions and activity

### Notifications
- Persistent WebSocket via Android foreground service
- Exec approval notifications with **Approve** / **Reject** action buttons
- Channel disconnect alerts
- Configurable notification categories

### Security
- Ed25519 device authentication (BouncyCastle)
- Biometric lock (fingerprint, face, or device PIN)
- TLS enforcement for remote connections
- Encrypted credential storage (DataStore + Tink)
- Auth tokens are never logged

---

## Quick Start

### Prerequisites

- Android phone running **Android 8.0+** (API 26)
- A self-hosted **OpenClaw gateway** (v2026.3 or later)
- Your gateway auth token

### Installation

1. Download the latest APK from [GitHub Releases](https://github.com/irishtinian/tenaza/releases) or install from Google Play (coming soon).
2. Open Tenaza.
3. Tap **"Enter URL manually"**.
4. Enter your gateway WebSocket URL (e.g., `ws://192.168.1.x:18789`) and auth token.
5. The app authenticates using an Ed25519 device identity generated on first launch.
6. Your Dashboard loads with agents, health status, cron jobs, and costs.

> **Tip:** For remote access outside your LAN, expose your gateway via Tailscale, Cloudflare Tunnel, or a VPS with TLS.

---

## Building from Source

### Requirements

- **JDK 17+** (Android Studio bundled JBR recommended)
- **Android SDK** with API 36
- **Git**

### Steps

```bash
# Clone the repository
git clone https://github.com/irishtinian/tenaza.git
cd tenaza

# Set JAVA_HOME (adjust path to your JDK installation)
export JAVA_HOME=/path/to/jdk-17

# Build debug APK
./gradlew assembleDebug

# The APK will be at:
# app/build/outputs/apk/debug/app-debug.apk
```

### Release Build

```bash
# Release builds require a signing key
./gradlew assembleRelease
```

> Release builds have R8 minification and resource shrinking enabled.

---

## Architecture

```
┌─────────────────────────────────────────────┐
│                   Tenaza App                │
├──────────┬──────────┬───────────┬───────────┤
│Dashboard │  Chat    │  Arena    │   Crons   │
│ Screen   │  Screen  │  Screen   │  Screen   │
├──────────┴──────────┴───────────┴───────────┤
│              ViewModels (Koin)               │
├─────────────────────────────────────────────┤
│          Repository / Use Cases              │
├──────────────┬──────────────────────────────┤
│  WebSocket   │   Local Storage              │
│  (OkHttp)    │   (DataStore + Tink)         │
├──────────────┴──────────────────────────────┤
│         OpenClaw Gateway (remote)            │
└─────────────────────────────────────────────┘
```

**Key layers:**

| Layer | Technology | Purpose |
|-------|-----------|---------|
| UI | Jetpack Compose + Material 3 | Declarative screens |
| Navigation | Navigation 3 | Screen routing and deep links |
| DI | Koin 4.1 | Dependency injection |
| Networking | OkHttp WebSocket | Real-time gateway communication |
| Auth | BouncyCastle Ed25519 | Device identity and signing |
| Storage | DataStore + Tink | Encrypted credentials and preferences |
| Camera | CameraX + ML Kit | QR code scanning for pairing |
| Biometric | BiometricPrompt | App lock (fingerprint/face/PIN) |

---

## Security

Tenaza takes security seriously. Your gateway credentials and agent data stay on your device.

| Aspect | Implementation |
|--------|---------------|
| Device authentication | Ed25519 keypair generated on-device, public key registered with gateway |
| Credential storage | Encrypted with Google Tink via Jetpack DataStore |
| Transport | TLS enforced for all remote (non-localhost) connections |
| Biometric lock | Optional fingerprint, face, or device PIN via AndroidX BiometricPrompt |
| Token hygiene | Auth tokens excluded from logs, crash reports, and backups |
| Network policy | Cleartext traffic blocked by default on Android 9+ |

---

## Contributing

Contributions are welcome! Here's how to get started:

1. **Fork** the repository.
2. **Create a branch** for your feature or fix: `git checkout -b feature/my-change`
3. **Make your changes** and ensure the project builds: `./gradlew assembleDebug`
4. **Submit a Pull Request** with a clear description of what changed and why.

### Guidelines

- Follow existing code style (Kotlin conventions, Compose best practices).
- Write meaningful commit messages.
- Keep PRs focused — one feature or fix per PR.
- Do not commit secrets, tokens, or personal data.

### Reporting Issues

Open an issue on [GitHub Issues](https://github.com/irishtinian/tenaza/issues) with:
- Device model and Android version
- Steps to reproduce
- Expected vs. actual behavior
- Logs (with tokens/URLs redacted)

---

## License

```
Copyright 2026 Tenaza Contributors

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

    http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
```

---

## Credits

- **[OpenClaw](https://github.com/openclaw)** — The multi-agent AI framework that Tenaza controls
- **[Jetpack Compose](https://developer.android.com/jetpack/compose)** — Modern Android UI toolkit
- **[OkHttp](https://square.github.io/okhttp/)** — HTTP and WebSocket client
- **[Koin](https://insert-koin.io/)** — Lightweight dependency injection for Kotlin
- **[BouncyCastle](https://www.bouncycastle.org/)** — Cryptography library (Ed25519)
- **[Google Tink](https://developers.google.com/tink)** — Secure encryption primitives
- **[ML Kit](https://developers.google.com/ml-kit)** — On-device barcode scanning

---

*Tenaza is not affiliated with or endorsed by Anthropic, Google, or OpenAI.*
