# M1 Acceptance Report — Giai đoạn 02 LAN Networking

**Date:** 2026-11-21
**Status:** **IN PROGRESS / PARTIAL — NOT VERIFIED**
**Scope:** Direct P2P TCP synthetic diagnostics, manual IPv4/port, manual inbound approval, no authentication/TLS, manual reconnect.
**Baseline:** M0 VERIFIED 41/41 on Temurin21 with process-level network denial. [M0 gate](m0-dependency-gate.md).

## AC-02-A … AC-02-K traceability

| AC | Actual evidence | Result |
| --- | --- | --- |
| A — two real Windows PCs, direct P2P, two-way diagnostic exchange without Internet | **Two GUI processes on one PC** succeed, real two-PC LAN not attempted | **BLOCKED — 2 PC** |
| B — manual IPv4:port only | NetworkConfig, UI input, actual 127.0.0.1:47321 local two-process test, no discovery code | **PASS (local)** |
| C — human incoming Accept/Reject + handshake/version | Real Swing Accept clicked, timeout/Reject in tests, v1 HELLO to READY, no persistent trust | **PASS (local)** |
| D — framing, Unicode, bounds | JUnit FrameCodec/JsonEnvelopeCodec tests on Java21, split and merged frames; UTF-8/emoji | **PASS (automated)** |
| E — malformed/unsupported/truncated/oversize safely rejected | Unit + real loopback bad preapproval input, unsupported CHAT/SYNC/FILE types | **PASS (tested cases)** |
| F — timeout, cancel, disconnect/manual reconnect & no leak | Real UI unanswered prompt timeout, manual retry, JUnit close and rejected cases; full heartbeat/queue stress not run | **PARTIAL** |
| G — UI truth, no blocking, no false claims | Real Swing window: connected only after human Accept, warnings and Ping; automated EDT performance/accessibility incomplete | **PARTIAL** |
| H — Windows Private firewall/IP/port guide | [Guide](../../../docs/windows-lan-setup.md) written, not validated on 2 PC firewall | **PARTIAL** |
| I — 2-process loopback + **two real Windows devices** | Five JUnit cycles + two GUI JVMs passed; physical dual-device absent | **BLOCKED — 2 PC** |
| J — diagnostic-only, untrusted identity warning, no Chat/History/File/Group | Visible warning + M1 type whitelist, negative tests; security stress not run | **PASS M1 scope / NONSECURE** |
| K — M0 Gate + Constitution I–VII Gate1 evidence | M0 Gate1 VERIFIED. M1 Constitution Gate1 not fully met (two PCs/stress) | **PARTIAL** |

## SC-001…010 summary

- **SC-001:** BLOCKED — two real LAN PCs and no-Internet requirement not checked.
- **SC-002:** PASS — five automated loopback connect/handshake/Ping/disconnect cycles.
- **SC-003:** PARTIAL — Reject, timeout, disconnect, manual reconnect tested; remote hard stop & full UI cancel cycle pending.
- **SC-004:** PARTIAL — invalid endpoint/unknown type/frame limits tested; extended adversarial cases pending.
- **SC-005:** PARTIAL — manual reconnect tested; 3 repeated real UI retry cycles not documented.
- **SC-006:** PASS (local) — manual IPv4 only, no auto-discovery.
- **SC-007:** PARTIAL — Windows guide written but not validated on real second PC.
- **SC-008:** PASS in tested local paths — CONNECTED after approval; no false auth/History/read state.
- **SC-009:** PARTIAL — 45 automated tests pass, real LAN and stress/cleanup still needed.
- **SC-010:** PARTIAL — basic close/cleanup tests, no full 2-PC soak/stress evidence.

## Automated and manual evidence

- [M1 Java21 test results](m1-test-results.md): **45/45 JUnit pass**, Maven shaded JAR runs.
- [US1](us1-manual-connect.md): manual IPv4/port, listen/connect.
- [US2](us2-approval-handshake.md): real Swing modal-less inbound approval dialog; first timeout; manual retry then human click Accept.
- [US3](us3-diagnostics.md): real UI synthetic PING/PONG **two directions**, 6 ms and 7 ms sample results.
- [US4](us4-lifecycle.md): lifecycle partial, nonblocking and cleanup tests.
- [US5 real LAN](us5-real-lan.md): **NOT RUN/BLOCKED** due to missing second accessible Windows PC.
- [Security boundary review](m1-security-review.md): no auth/encryption, type whitelist, outstanding hardening.

## Gate result

**Constitution Gate 0:** design passed. **Gate 1:** PARTIAL. MUST remain **In Progress** until AC-02-A/I and resource/timing/security acceptance are evidenced. Do not conflate 2 Java processes on one machine with 2 separate physical Windows PCs. No M1 History/File/Chat features or trusted peer authentication exist.

**Next:** secure authorized second PC for LAN testing, complete heartbeat/queue/duplicate stress, JDK21 UI negative tests, rerun regression+evidence and only then mark M1 Verified.
