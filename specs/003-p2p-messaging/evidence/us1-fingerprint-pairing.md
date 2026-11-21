# US1 — Real human pairing, secure TLS v2, trust on restart

**Date:** 2026-11-21 · **Platform:** one Windows 11 x64 PC, Temurin Java21, two independent javaw GUI processes. **Not two physical PCs.**

**Exact entrypoint:** `java -Dintrachat.secureRoot=<distinct-user-test-dir> -cp target/intrachat-0.1.0-SNAPSHOT.jar ui.LiveIntraChat`. Processes A and B each used a separate temporary, user-protected DPAPI identity/trust directory. Synthetic test keys only, no private data.

Actual Windows GUI sequence:
1. On A, manually clicked **Bắt đầu lắng nghe** for `127.0.0.1:47321`; status changed to listening.
2. On B, clicked **Kết nối & xác minh** to that IP:port. A received **real** Swing inbound approval popup with the observed TCP endpoint and warning that self-declared UUID/displayName are not trusted.
3. A explicitly clicked **Chấp nhận**. A and B each displayed a first-time **matching 64-bit SAS**, local/remote SPKI fingerprints in opposite order. Test operator compared codes from both windows, then clicked **Mã trùng — Xác minh** **on each** (not scripted GUI autoaccept).
4. Both processed protected `PAIR_CONFIRM` / `PAIR_READY` on **real TLS1.3 socket** and launched **Messenger FE Live**; user chat was initially disabled until this succeeded.
5. Later closed/relaunched two Live JAR processes with the **same separate DPAPI secureRoot** folders. A still received **new human inbound approval**. Pinned peer keys were recognized without first-time SAS popup. Both Messenger Live windows reopened.

Complementary JUnit: `DeviceIdentityTest`, `FingerprintVerifierTest`, `DeviceIdentityStoreTest`, `PinnedPeerStoreTest`, `TlsContextFactoryTest`, `TlsChatSessionSecurityTest`, `SecureChatCoordinatorTest`. In `SecureChatCoordinatorTest.changedKeyForPreviouslyPinnedDeviceIsRejected`, an impostor with the same claimed UUID and different P-256 key **failed** to connect and the original SPKI remained pinned. Another test verifies inbound Reject creates **no pairing pins**.

**Limitations:** First-contact candidate cert verification and custom JSSE trust-manager require independent security review; one Windows host with two processes, not physical LAN. Key-rotation UI, revoked-key after actual GUI restart, Win11 different-user DPAPI tests and full MITM/DoS still open. Fingerprints/keys/SAS are **not included** in this evidence.
