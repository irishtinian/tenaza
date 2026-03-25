---
phase: 1
slug: project-setup-connection
status: draft
nyquist_compliant: true
wave_0_complete: false
created: 2026-03-25
---

# Phase 1 — Validation Strategy

> Per-phase validation contract for feedback sampling during execution.

---

## Test Infrastructure

| Property | Value |
|----------|-------|
| **Framework** | JUnit 5 (5.11.x) + Turbine (1.2.x) + MockK (1.14.x) |
| **Config file** | None yet — Wave 0 creates `app/src/test/` structure |
| **Quick run command** | `./gradlew :app:testDebugUnitTest -x lint` |
| **Full suite command** | `./gradlew :app:testDebugUnitTest -x lint` |
| **Estimated runtime** | ~10 seconds |

---

## Sampling Rate

- **After every task commit:** Run `./gradlew :app:testDebugUnitTest -x lint`
- **After every plan wave:** Run `./gradlew :app:testDebugUnitTest -x lint`
- **Before `/gsd:verify-work`:** Full suite must be green
- **Max feedback latency:** 10 seconds

---

## Per-Task Verification Map

| Task ID | Plan | Wave | Requirement | Test Type | Automated Command | File Exists | Status |
|---------|------|------|-------------|-----------|-------------------|-------------|--------|
| 01-01-01 | 01 | 1 | CONN-04 | build | `./gradlew assembleDebug` | N/A | ⬜ pending |
| 01-01-02 | 01 | 1 | CONN-04 | build | `./gradlew assembleDebug` | N/A | ⬜ pending |
| 01-02-01 | 02 | 1 | CONN-02, SECR-03 | unit | `./gradlew :app:testDebugUnitTest --tests "*.KeystoreTest"` | ❌ W0 | ⬜ pending |
| 01-02-02 | 02 | 1 | CONN-03, SECR-02 | unit | `./gradlew :app:testDebugUnitTest --tests "*.CredentialStoreTest"` | ❌ W0 | ⬜ pending |
| 01-03-01 | 03 | 2 | CONN-04, CONN-05, SECR-01 | unit | `./gradlew :app:testDebugUnitTest --tests "*.WebSocketManagerTest"` | ❌ W0 | ⬜ pending |
| 01-03-02 | 03 | 2 | CONN-06, SECR-01 | unit | `./gradlew :app:testDebugUnitTest --tests "*.UrlValidatorTest"` | ❌ W0 | ⬜ pending |
| 01-04-01 | 04 | 2 | CONN-01 | unit | `./gradlew :app:testDebugUnitTest --tests "*.PairingPayloadTest"` | ❌ W0 | ⬜ pending |
| 01-04-02 | 04 | 2 | CONN-06 | build | `./gradlew assembleDebug` | N/A | ⬜ pending |
| 01-05-01 | 05 | 3 | CONN-04 | unit | `./gradlew :app:testDebugUnitTest --tests "*.ConnectionRepositoryTest"` | ❌ W0 | ⬜ pending |
| 01-05-02 | 05 | 3 | CONN-07 | unit | `./gradlew :app:testDebugUnitTest --tests "*.CredentialStoreTest"` | ❌ W0 | ⬜ pending |
| 01-05-03 | 05 | 3 | SECR-03 | unit | `./gradlew :app:testDebugUnitTest --tests "*.ConnectionRepositoryTest"` | ❌ W0 | ⬜ pending |

*Status: ⬜ pending · ✅ green · ❌ red · ⚠️ flaky*

---

## Wave 0 Requirements

- [ ] `app/src/test/java/com/clawpilot/data/remote/ws/WebSocketManagerTest.kt` — covers CONN-04, CONN-05
- [ ] `app/src/test/java/com/clawpilot/data/remote/ws/ReconnectPolicyTest.kt` — covers CONN-05
- [ ] `app/src/test/java/com/clawpilot/data/local/prefs/CredentialStoreTest.kt` — covers CONN-03, CONN-07, SECR-02
- [ ] `app/src/test/java/com/clawpilot/data/local/crypto/KeystoreTest.kt` — covers CONN-02, SECR-03
- [ ] `app/src/test/java/com/clawpilot/ui/pairing/PairingPayloadTest.kt` — covers CONN-01
- [ ] `app/src/test/java/com/clawpilot/util/UrlValidatorTest.kt` — covers CONN-06, SECR-01
- [ ] `app/src/test/java/com/clawpilot/data/repository/ConnectionRepositoryTest.kt` — covers SECR-03

---

## Manual-Only Verifications

| Behavior | Requirement | Why Manual | Test Instructions |
|----------|-------------|------------|-------------------|
| QR camera scanning works | CONN-01 | Requires physical camera | Open pairing screen → point at QR → verify payload parsed |
| TLS handshake with remote gateway | SECR-01 | Requires real TLS endpoint | Connect to Tailscale-exposed gateway → verify wss:// works |
| Biometric prompt displays | SECR-04 | Phase 6 (not this phase) | N/A |

---

## Validation Sign-Off

- [x] All tasks have `<automated>` verify or Wave 0 dependencies
- [x] Sampling continuity: no 3 consecutive tasks without automated verify
- [x] Wave 0 covers all MISSING references
- [x] No watch-mode flags
- [x] Feedback latency < 10s
- [x] `nyquist_compliant: true` set in frontmatter

**Approval:** pending
