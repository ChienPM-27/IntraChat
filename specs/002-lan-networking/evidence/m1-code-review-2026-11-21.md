# M1 focused verification — 2026-11-21

## Scope and working tree protection

- Operated on `003-p2p-messaging` working tree. Existing in-progress M2 modifications (LiveChatService, PinnedPeerStore, M2 RED tests) were left untouched.
- No firewall, network interface, or Windows system settings were changed.
- Tested with Eclipse Temurin JDK 21.0.12.1+1 via explicit per-command JAVA_HOME; Maven Wrapper offline.

## T036 protocol/frame test-first hardening — PASS

- `FrameCodecTest`: added exact 16 KiB HELLO and 1 MiB control-frame boundaries; oversize (+1) rejected before frame allocation. Existing split-byte and combined-frame UTF-8 test retained.
- `JsonEnvelopeCodecTest`: added malformed JSON Unicode escaped *unpaired surrogate* rejection. A focused test returned **exit 1** (RED), showing Jackson's parsed field was accepted before correction. Added recursive Unicode scalar check on parsed JSON tree and source envelope.
- Re-ran `.\\mvnw.cmd -q -o '-Dtest=FrameCodecTest,JsonEnvelopeCodecTest,ConnectionLifecycleTest' test` with actual Java 21: **exit 0**, 11/11 tests, 0 failures/errors: FrameCodecTest 4, JsonEnvelopeCodecTest 5, ConnectionLifecycleTest 2.
- Prior M1 targeted suite `-Dtest=FrameCodecTest,JsonEnvelopeCodecTest,HandshakeTest,NetworkCoordinatorTest,SessionRegistryTest,ResourceLimitsTest,NetworkFaultTest,DiagnosticExchangeTest,TcpLoopbackIntegrationTest` returned **exit 0** on Java 21 before adding new tests.
- Marked T036 done based on real executed coverage and RED/GREEN result.

## T038 / lifecycle test groundwork — PARTIAL, keep OPEN

- Added `ConnectionLifecycleTest`: pending remote approval, manual cancel/socket close, no false CONNECTED, Swing EDT still responsive, explicit Reject and no automatic retry.
- Two tests PASS. Missing explicit state-transition design for WAITING_REMOTE_APPROVAL, shutdown/heartbeat synthetic clock and GUI lifecycle acceptance: no completion claim for T038/T039/T044.

## Verified blocking condition for acceptance T050–T052

- The available Windows MCP controls only the local Windows 11 machine and has one reported hostname. A second independent physical Windows 11 host is **not attached** to the available control context; source evidence at `specs/002-lan-networking/evidence/us5-real-lan.md` already documents the same limitation.
- Requirement T050 explicitly demands a second *physical* Windows device, human Accept/Reject in both directions, no Internet and independent evidence from both endpoints. A localhost test with two JVMs or another local network interface cannot meet this requirement.
- No probing of nearby LAN devices, unsolicited remote control, change of firewall, disconnection of Internet, or assertion of cross-device PASS was performed.
- **T050/T051/T052 remain BLOCKED/NOT RUN.** Downstream tasks whose acceptance depends on them (T053, T058, T059, T060) cannot legitimately close their gates yet. This is an environment/access blocker, not a Java compilation failure.
