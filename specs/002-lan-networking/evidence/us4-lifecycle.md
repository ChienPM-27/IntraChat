# US4 — Asynchronous lifecycle and manual reconnect (partial)

**Date:** 2026-11-21
**Status:** PARTIAL (automated + local UI). Two-PC and stress/time-budget acceptance still outstanding.

## Evidence actually executed

- `NetworkCoordinatorTest.rejectThenExplicitManualRetryCanBeAccepted` uses direct TCP loopback, a test provider explicitly returning Reject then Accept. First future fails, no auto reconnect after a wait, second connection occurs **only** by calling `reconnect()`, and finally disconnects.
- `HandshakeTest` tests pending inbound async approval, no outbound future completion before Accept; explicit Reject results in no active sessions.
- `TcpListenerTest` checks listener lifecycle and port-conflict error, `SessionRegistryTest` checks pending cap and declared UUID duplicate reservation, `NetworkFaultTest` closes invalid preapproval input without displaying approval.
- Actual two Windows `javaw.exe` processes with distinct profiles: first incoming popup left unanswered until timeout and B displayed connection failure; B then clicked `Kết nối lại` manually, A accepted new request; both performed Ping then normal window-close requested.
- GUI status warning and inputs remained visible while B waited for consent. Network tasks are not executed by Swing EDT in source: `NetworkCoordinator` uses virtual-thread executor, `NetworkPanel` uses async listeners and `SwingUtilities.invokeLater` for updates.

## Not yet covered

- No controlled half-open/heartbeat scheduler test, 4 active & 4 concurrent pending saturation stress test, 16-frame/2MiB backpressure stress, duplicate simultaneous cross-connect test, remote crash while midframe, or 5 M1 window lifecycle timing samples.
- UI dialog is visually inspected; no automated focus/keyboard accessibility audit completed.
- Listener/network identity remains unauthenticated and plaintext; no security certification.

**Decision:** Keep unverified resource/heartbeat/large fault tasks open. M1 not Verified from this evidence.
