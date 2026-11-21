# M1 — Test Results (In Progress)

**Executed:** 2026-11-21 on Windows 11 x64 build 26100, actual Eclipse Temurin JDK **21.0.13.1+2**, Maven Wrapper **3.9.16**.
**Status:** **Partial M1 implementation + independent FE demo PASS on single Windows PC; two physical LAN PCs NOT RUN; M1 NOT VERIFIED.**

## Commands and outcomes

| Check | Result |
| --- | --- |
| Test-first `.\mvnw.cmd -q -o test` before M1 protocol classes | **Exit 1** expected (missing FrameCodec/NetworkConfig/JsonEnvelopeCodec) |
| Initial protocol/domain classes | **22/22 JUnit pass** on JDK21 |
| M1 direct TCP handshake/diagnostics added | **33/33 JUnit pass** on JDK21 |
| Added malformed preapproval, manual retry and 5-loopback-cycle cases | `powershell -NoProfile -ExecutionPolicy Bypass -File scripts/verify-m1.ps1 -Offline`: **exit 0, 45/45 tests**, **0 failures/errors** |
| `.\mvnw.cmd -q clean package` | **Exit 0**, shaded runnable JAR (~2.45 MB), includes Jackson 2.22.4 |
| `java -jar target/intrachat-0.1.0-SNAPSHOT.jar --diagnostics` | Exit 0, `IntraChat 0.1.0-SNAPSHOT — Chưa kết nối` |
| Two independent Windows Java21 GUI processes on `127.0.0.1:47321` | A started listener; B manually connected; A received real warning/prompt. Unanswered first attempt timed out; B clicked manual Reconnect; A clicked Accept; both connected for diagnostics |
| GUI synthetic Ping/Pong B→A and A→B | **PONG received both directions**, single observations ~**6 ms and 7 ms** |
| Two **different physical Windows LAN PCs**, no Internet | **NOT RUN** (no second accessible PC). AC-02-A/I BLOCKED |

## Automated test coverage

45 JUnit cases including 9 legacy M0 tests. Added ResourceLimitsTest: fifth preapproval TCP candidate is dropped while four candidate workers are active; probe timeout cleanup now removes pending probe records. New coverage: FrameCodec and strict JsonEnvelopeCodec (UTF-8/emoji, duplicate JSON, unknown types, split/coalesced/invalid length), HandshakeContract, NetworkConfig, ConnectionSession, TcpListener/Connector, SessionRegistry, InboundApprovalPort, LocalPeerProfile, Handshake, DiagnosticExchange, NetworkFault, NetworkCoordinator manual retry, TcpLoopbackIntegrationTest with **five** cycles, M1TypeWhitelistTest and SecurityBoundaryTest.

The scripted test approval port **does not autoapprove production requests**; GUI test explicitly clicked **Chấp nhận**.

## Not covered / limitations

- Two actual Windows PCs with IPv4 LAN access and public Internet unavailable: **NOT RUN**.
- Full request flood/queue saturation/heartbeat half-open tests, forced process crash and full cross-device shutdown & performance measurements: **NOT RUN**.
- Local Ping timings (6/7 ms) are two spot observations, not an SLA or real LAN latency.
- M1 is **unauthenticated and plaintext**; no user Chat, History, File or Group is transmitted or authorized.
- An initial Windows TIME_WAIT-dependent test expecting instantaneous rebind failed once; test corrected to avoid OS-specific assumption and subsequent Maven clean package/test passed.

**Local automated test gate: PASS. Full M1 acceptance: BLOCKED / PARTIAL.**
