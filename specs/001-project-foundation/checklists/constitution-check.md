# Constitution Check — 001 Project Foundation

**Gate 0:** design review of M0. **Gate 1:** code/test validation to be updated with actual evidence.
**Authority:** [Project Constitution](../../../.specify/memory/constitution.md).

| Principle | Gate 0: design check | Gate 1: runtime evidence/status |
| --- | --- | --- |
| I. LAN-first | P2P without cloud/server; network deferred to 002 | PASS M0 source review: no socket/cloud dependencies |
| II. Protocol/identity | Message/version/destination/sender/time documented; stable IDs | PASS M0: PeerIdentity/MessageMetadata JUnit tests, no wire parser yet |
| III. Nonblocking & bounds | EDT UI, future bounded I/O; max frame draft 1 MiB | PASS M0: Swing runs on EDT, no I/O code; frame limits future design only |
| IV. Security/privacy | Treat all LAN data untrusted, no native Java deserialization, auth needed before sync | PASS M0 source scan; future sync blocked on authenticated peer + ACL |
| V. Test-backed | JUnit test-first, negative test and GUI lifecycle specified | PASS 9/9 green, initial red, intentional isolated negative red; evidence linked below |
| VI. User-visible truth | M0 UI shows disconnected; future state claims require ACK | PARTIAL: 5/5 GUI title/exit observed, label source checked; physical offline GUI not run |
| VII. Spec-driven | Spec, plan, tasks, docs with explicit future scope | PASS docs + tests and partial-status acceptance report; release remains blocked |

**Rules:** No unresolved MUST violation may pass Gate 1. No claim of implemented file transfer, chat or synced history in M0. The user-confirmed choices CL-01…04 remain binding.

**Gate 0 conclusion:** Architecture complies at design level. **Gate 1 VERIFIED on 2026-11-21:** actual Temurin21 Maven build/package/test (9/9), source-only offline-cache checkout, 5/5 Windows GUI launches and normal exits with **all Java networking denied at JVM boundary** (0 TCP/UDP), negative assertion detection, code/log/security review. Physical network adapter remained on; documented alternative is per-JVM network access denial, not physical shutdown. [Evidence](../evidence/m0-jdk21-offline.md), [M0 acceptance report](../evidence/m0-acceptance-report.md). History Sync remains disabled until authenticated peer and conversation ACL in stage 005.
