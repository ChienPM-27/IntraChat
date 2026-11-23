# Spec 002 — Local task implementation and RED→GREEN verification, 2026-11-22

**Scope:** M1 diagnostic-only LAN, 16 previously open local coding/test tasks T027, T029–T031, T033–T034, T038–T046 and T056. **Disposition:** Local technical tasks implemented/tested; **M1 Gate 1 remains NOT VERIFIED** because two separate physical Windows LAN hosts and dependent acceptance/review are unavailable. **No M1 chat text is authorized.**

**Windows working tree:** Branch `003-p2p-messaging`, HEAD `b8a8fd5`, dirty before intervention (M2/Group ongoing). Implemented directly in working tree; did not stash/reset/commit, modify firewall or identities or delete unrelated user files.

## Actual source changes

| Task(s) | Actual source/test coverage | Execution result |
| --- | --- | --- |
| T027 | Existing `TcpPeerSession`/HELLO–Accept–Confirm–Ready state + new `HandshakeNegativeTest`: self-connect rejected before consent; forged requestId and wrong chosenVersion rejected; real HELLO idle 5s truncated/oversize test and no preapproval diagnostic payload. | 3/3 new handshake negative tests PASS plus existing Handshake/NetworkFault |
| T029–T030 | Extended `SessionRegistry` with token-bound admission, 4 active/4 pending bounds, deterministic direction tie-break for simultaneous cross-connect, zero/self/duplicate rejection, idempotent pending accounting. `NetworkCoordinator` now reserves **atomically together with session map insertion** and removes only matching token on close. | `SessionRegistryTest` 6/6; `SimultaneousCrossConnectTest` real A↔B TCP 1/1. Cross-connect race initially failed under combined stress, traced to gap between registry and session-map insertion; after atomic repair **8 consecutive focused runs PASS**. |
| T031 | New `InboundApprovalIntegrationTest` spawns **TWO REAL Java 21 OS processes** via `M1PeerProcessHarness` on one Windows host, tests scripted Accept, Reject and short test-controlled decision expiry; Accept actually performs bidirectional PING/PONG. Prior [manual GUI evidence](us2-approval-handshake.md) records real human Accept on localhost. | 3/3 JUnit PASS; **two OS processes on ONE PC**, not second physical machine; test-only scripted consent is not production autoaccept |
| T033 | New `DiagnosticService` provides only synthetic `NetworkCoordinator.ping`, pre-READY returns failed future. Existing `TcpPeerSession` handles only DIAGNOSTIC_PING/PONG after READY, strict M1 codec rejects Chat/Group/File/Sync. | `DiagnosticServiceTest` 2/2 and `DiagnosticExchangeTest` PASS; no ACK/READ inferred from Pong |
| T034 | New `ui.dialogs.LanDiagnosticsDialog` reachable via explicit button in existing `LanConnectionDialog` (single Messenger launcher), technical-only controls and latency display, **no chat composer**, strongly labeled plaintext/unverified. M1 uses default 47322 vs separate TLS Live default 47321. | `LanDiagnosticsDialogTest` first GUI positive RED: incoming side Ping remained disabled; fixed CONNECTED UI event → 5/5 GUI tests PASS |
| T038–T039 | New `NetworkEvents.Kind.WAITING_REMOTE_APPROVAL`, cancellation flag for socket-opening race, explicit failure if cancelled before/after TCP handoff, duplicate-click rejection, no automatic retry; existing virtual-thread executor, EDT-safe Swing dispatch retained. | `ConnectionLifecycleTest` 4/4 (RED before state/cancel fix); `NetworkCoordinatorTest` manual reconnect PASS |
| T040–T041 | New `OutboundBudget` with synchronized **16-frame AND 2MiB** admission, scoped idempotent release, concurrency saturation tests; `TcpPeerSession` uses budget on actual writes. `SessionRegistry` enforces global peer limits and pending slots. Existing `ResourceLimitsTest` fifth incoming candidate drop + `FrameCodecTest` 16KiB/1MiB length bounds. | `OutboundBudgetTest` 3/3 (RED missing class then GREEN); `ResourceLimitsTest` PASS. A test barrier was corrected to wait for *all* producer attempts before releasing reservations; no production capacity assertion weakened |
| T042–T043 | New `DiagnosticHeartbeat` monotonic-clock state and fake-clock RED tests; real `TcpPeerSession` schedule every **10 seconds**, closes after **30 seconds** without validated inbound traffic, cancel heartbeat and outstanding probes on close; never auto reconnect. | `HeartbeatTest` 3/3 fake-clock RED→GREEN and real loopback regression PASS |
| T044 | M1 dedicated safe Swing diagnostic dialog on unified Messenger, explicit Connect/Cancel/Disconnect/Manual Reconnect/Ping, all socket jobs asynchronous and callbacks posted to EDT. UI tests check responsiveness during pending consent, true inbound READY, Reject then manual retry and cancellation. | `LanDiagnosticsDialogTest` 5/5, no blocking Swing EDT |
| T045 | `LanDiagnosticsDialog.dispose()` now **synchronously stops listener/sessions/workers**; startup/read sockets closed by `NetworkCoordinator.close`. New `ShutdownTest` real TCP 5 listen→connect→ping both ways→close cycles and pending-approval close. GUI test does 5 listen→dispose cycles with refused port probe. | `ShutdownTest` 2/2 and GUI 5/5 PASS. An isolated-source false-positive bind assertion was replaced by a stronger live-listener/refused-connect check because Windows TCP TIME_WAIT may prevent immediate port rebind despite correct close |
| T046 | `NetworkFaultTest` expanded to truncated midframe HELLO, refused endpoint, port conflict and actual **5-second slow HELLO**; remaining Reject/cancel/double click/queue full covered by `ConnectionLifecycleTest`, `NetworkCoordinatorTest`, `OutboundBudgetTest`. | `NetworkFaultTest` 5/5 PASS; timeouts and resource cleanup actually exercised. No 2-PC crash simulation claim |
| T056 | Actual Temurin21 `scripts/verify-m1.ps1 -Offline` and complete M2 regression; cloned current **dirty working-tree source** to fresh temporary directory excluding .git/target, Maven **offline clean package** and JAR diagnostics exit 0; inserted failing JUnit method **only inside temporary copy** and observed nonzero Maven exit plus `ISOLATED_NEGATIVE_PROOF`; removed temporary copy. | Script local exit 0, **172/172 JUnit**, 0 failures/errors; isolated clean exit 0, diagnostics exit 0, deliberate negative nonzero, temp removed. This is *isolated working-tree source copy*, **not pristine committed checkout** |

## Actual commands and results

```powershell
$env:JAVA_HOME='C:\Users\XuanHoai\.jdks\jdk-21.0.12.1+1'
$env:PATH=$env:JAVA_HOME+'\bin;'+$env:PATH
powershell.exe -NoProfile -ExecutionPolicy Bypass -File scripts/verify-m1.ps1 -Offline
powershell.exe -NoProfile -ExecutionPolicy Bypass -File scripts/verify-m2.ps1 -Offline
```

- **Temurin Java 21.0.12.1+1**; Maven offline clean/package success; shaded JAR `--diagnostics` prints `IntraChat 0.1.0-SNAPSHOT — Chưa kết nối`.
- `verify-m1.ps1` **exit 0, 172/172 JUnit PASS**, 0 failures/errors; `verify-m2.ps1` **exit 0, 172/172**, 0 failures/errors; unified Messenger launcher still packaged, obsolete M1 MainWindow and old Live UI absent.
- New **31** JUnit cases over the previously recorded 141/141 M0/M1/M2/Group local baseline; selected 11 suites: `SessionRegistryTest` 6; `SimultaneousCrossConnectTest` 1; `HeartbeatTest` 3; `OutboundBudgetTest` 3; `ConnectionLifecycleTest` 4; `DiagnosticServiceTest` 2; `InboundApprovalIntegrationTest` 3; `HandshakeNegativeTest` 3; `ShutdownTest` 2; `NetworkFaultTest` 5; `LanDiagnosticsDialogTest` 5, all zero errors/failures/skipped.
- **Isolated source copy**: `robocopy` exit 1 (**successful copied files**; Robocopy 0..7 nonfatal), `.\mvnw.cmd -o -q clean package` exit 0, `java -jar ... --diagnostics` exit 0, intentionally broken isolated `NetworkConfigTest` produced JUnit FAIL `ISOLATED_NEGATIVE_PROOF`, Maven exit **nonzero (-1 as exposed by PowerShell pipeline)**, temporary directory deleted.
- Initial combined run uncovered a real cross-connect race despite initially passing focused test, and clean-copy build uncovered Windows-specific premature rebind assertion. **Both fixed in source/tests and re-verified**; no failed test ignored.

## Tasks NOT completed: exactly 7 requiring real external acceptance

- **T050–T052:** two *different physical* Windows 11 PCs on same authorized Private LAN with public Internet inaccessible; mutual technical Ping/Pong and firewall/disconnect evidence. No such test happened.
- **T053:** security/UI implementation review and release evidence explicitly depends on T050–T052, so remains OPEN despite local warnings being tested.
- **T058:** final documentation acceptance depends on T050–T057; partial local instructions can be updated, but do not check release-level documentation yet.
- **T059–T060:** Constitution Gate 1, official M1 acceptance and handoff depend on two physical hosts and evidence. Not Verified without real external verification/review.

**Security boundary:** M1 is **plaintext/unauthenticated diagnostic-only**, despite user-facing UI. It accepts only synthetic Ping/Pong and technical handshake. Self-declared UUID/name never grants secured P2P or Group capability; Live Chat retains separate TLS+SAS verified path. The test harness uses explicitly scripted approval **only in tests**; no production auto-consent. No firewall, Internet adapter, machine identity or Git commits changed.

**Status:** Local M1 implementation tasks can be checked based on this record. **M1 remains In Progress / Gate 1 NOT VERIFIED**, release limited by physical 2-PC evidence; M2 and Group gates also remain open.
