# Constitution Check — 002 LAN Networking

**Status:** Gate 0 design PASS; **Gate 1 PARTIAL** (45 JUnit tests and two GUI loopback processes tested; real dual-PC LAN and stress tests NOT RUN).
**References:** [Constitution](../../../.specify/memory/constitution.md), [spec](../spec.md), [plan](../plan.md), [wire contract](../contracts/network-v1.md).

| Principle | Gate 0 design check | Gate 1 actual evidence required |
| --- | --- | --- |
| **I — LAN-first** | Direct TCP P2P, both listener and initiator, manual IPv4:port only; no Internet service/auto-discovery | 2-process + real two-Windows-PC LAN, no Internet, user consent and documented firewall |
| **II — Versioned contract** | Required envelope fields, 4-byte big-endian framing, v1 HELLO/approval, unique IDs, strict invalid/unknown rejection | FrameCodec + JsonEnvelopeCodec + HandshakeContract tests, bad version/type, Unicode |
| **III — EDT + bounded resource** | I/O not on EDT; 4 active/4 pending, 16 writes/2MiB, 16KiB HELLO, 1MiB control; cancel/timeouts + manual retry | UI responsiveness, timeout/flood, resource caps, process/socket/thread cleanup |
| **IV — Security/privacy** | Untrusted bytes/IDs; incoming requires human Accept; **no authentication or encryption** and only synthetic diagnostics with notice; never History/File/Group | UI warning before usage, reject no-consent diagnostics, forbid unsafe Java deserialization/logs/data leakage |
| **V — Automated tests** | Test-first RED→GREEN, loopback ≥2 independent peers, invalid/timeout/disconnect/cleanup, real 2 PCs | JUnit outputs/command exit, negative tests, clean build, 2-device evidence |
| **VI — User-visible truth** | Connected only after accept+READY, no delivered/read/authenticated/synced false labels, manual reconnect | UI state tests, real acceptance prompt, clear errors/Unicode, 5 close cycles |
| **VII — Spec-driven** | CL-02-01…04 preserved; ADR-01…14 & resource choices, traceability, release gate M0 dependency | Evidence-based task checkboxes, AC-02-A…K report, M0 Gate 1 closure/approved deviation |

**Gate 0 conclusion:** Design aligns with seven MUST principles. **Gate 1 PARTIAL (2026-11-21):** actual Temurin21, 45 JUnit tests, local 5-cycle loopback, real Swing inbound human Accept, PING/PONG both directions, strict frame/type validation and visible warning verified. **NOT VERIFIED:** two physical Windows LAN PCs, full quota/heartbeat/cleanup/stress and production risk review. No Constitution exception approved.

## Blocking gates

- **M0 dependency:** tasks T025/T040/T041 and Java21 runtime + offline GUI acceptance must be completed or explicitly approved deviation before M1 network code is considered ready under task T001.
- **Peer trust:** even after user clicks Accept, peer identity remains **self-declared** and wire is **plaintext**. Do not expose user chat, History or File or claim secure peer connection.
- **M1 real LAN:** loopback only is insufficient; two real Windows devices and Internet-free LAN evidence required.

## Required future artifacts

- `specs/002-lan-networking/evidence/m0-dependency-gate.md`
- `specs/002-lan-networking/evidence/us1-manual-connect.md`
- `specs/002-lan-networking/evidence/us2-approval-handshake.md`
- `specs/002-lan-networking/evidence/us3-diagnostics.md`
- `specs/002-lan-networking/evidence/us4-lifecycle.md`
- `specs/002-lan-networking/evidence/us5-real-lan.md`
- `specs/002-lan-networking/evidence/m1-test-results.md`
- `specs/002-lan-networking/evidence/m1-acceptance-report.md`

Each report may contain **PASS / FAIL / BLOCKED / NOT RUN**, never invent success. M1 stays unverified until all required gates are satisfied.
