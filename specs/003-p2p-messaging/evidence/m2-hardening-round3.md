# M2 follow-up — TLS receipts, idempotent ACK, DPAPI pins, session RAM and negative wire tests

**Date:** 2026-11-21. Branch: `003-p2p-messaging`. Host: one Windows 11 x64 PC. Actual runtime **Eclipse Temurin JDK21 21.0.12.1+1** (`C:\Users\XuanHoai\.jdks\jdk-21.0.12.1+1`); offline Maven Wrapper 3.9.16. Tests use two locally approved **real JSSE TLS1.3** peers for protected application frames; human approval/fingerprint UI is scripted **only in JUnit**, not in Live production UI.

## Actually observed RED → GREEN defects

| RED command/test | Actual RED result (before production code fix) | Corresponding GREEN behavior |
| --- | --- | --- |
| `.\mvnw.cmd -q -o '-Dtest=ReadReceiptCorrelationTest' test` | 1 test, 1 failure: 3 `READ` UI update events instead of exactly 1 after repeated protected receipts and late ACK | `LiveChatService.changeStatus` publishes only **real** monotonic state transitions, not duplicate READ/ACK events |
| `-Dtest=PinnedPeerStoreTest` | 3 tests, 1 failure: same 32-byte pinned SPKI could be silently pinned to **another remote UUID** | `PinnedPeerStore.pinNew` rejects fingerprint collision across UUIDs; rejects altered/corrupt trust file without overwriting |
| `-Dtest=IncomingChatRoutingTest` | 1 test, 1 error: `SocketTimeoutException` waiting for second ACK after same-ID retransmission | A repeated **identical** authenticated message returns a **fresh ACK** but never creates second bubble. Reused message ID with different content/timestamp fails closed; wrong conversation closes session |
| `-Dtest=SessionRamPrivacyTest` | 1 test, 1 failure: after real TLS disconnect, outbound message status remained `LOCAL_SENT` in stale map | Disconnected authenticated session purges corresponding outbound/attempt ledger, avoiding across-session unbounded retention |

The RED tests use synthetic UUIDs, keys, text and fixture-generated ephemeral credentials only. No real conversation, private key or actual SAS is committed.

## Additional executed tests / coverage (without claiming fresh RED)

- `DeliveryAckTest`: protected valid ACK changes `LOCAL_SENT`→`APP_ACKNOWLEDGED`, unknown original ID ignored, duplicate ACK emits no extra UI status, ACK is never READ.
- `ReadReceiptCorrelationTest`: forged unknown original ignored; authenticated READ before ACK elevates only correct outgoing message; duplicate READ and late ACK do not repaint or downgrade.
- `IncomingChatRoutingTest`: exact authenticated peer UUID, duplicate UUID/text does not duplicate bubble, forged conversation fails closed.
- `LiveChatAuthorizationTest`: no socket/verified peer, wrong conversation and pre-READY transport never send user data; existing `TlsChatSessionSecurityTest` verifies actual JSSE quarantine.
- `DeviceIdentityStoreTest`: current-user Windows ACL on DPAPI file permits owner only, identity survives restart, corrupted blob fails closed, occupied parent blocks write; actual cross-user logon is **BLOCKED**.
- `PinnedPeerStoreTest`: protected persistent pins survive restart, revoke, changed-key rejection, UUID/public-key collision rejection and corrupt store failure.
- `ChatV2NegativeTest`: bounded 16,384 pre-TLS and 65,536 protected bytes, malformed UTF8, duplicate JSON, extra JSON data, unknown types/version, raw diagnostic v1 CHAT_TEXT downgrade rejected, protected wrong session/destination rejected.

`ChatV2NegativeTest` first execution had **a test-fixture error** (v1 PING missing required `probeId`, 4 tests: 1 error). Corrected the fixture to include valid `probeId` and then **all 4 passed**. This fixture error was **not a production vulnerability**.

## Full regression and limits

```powershell
$env:JAVA_HOME='C:\Users\XuanHoai\.jdks\jdk-21.0.12.1+1'
$env:Path="$env:JAVA_HOME\bin;$env:Path"
powershell -NoProfile -ExecutionPolicy Bypass -File scripts\verify-m2.ps1 -Offline
```

**Shared worktree execution:** exit **0, 111/111 JUnit PASS, 0 failures/errors**. Java21 clean package, shaded runnable diagnostics JAR, Live/Demo entrypoint presence and JAR metadata checks PASS. This shared worktree contained **independent, uncommitted M1 diagnostic tests/source** concurrently, which are **not part of the M2 implementation commit**.

**Clean isolated commit execution (reproducible):** Created a detached `git worktree` at **commit `98658d0`** (M2 source/test patch but before this documentation change), ran the *same* `scripts/verify-m2.ps1 -Offline` on JDK21. Actual **exit 0, 107/107 JUnit PASS, 0 failures/errors**, shaded runnable JAR diagnostics PASS, XML suite sum 107. That is the reliable test count for the **committed branch snapshot without the independently modified M1 working files**. The difference (**4 tests**) is from the concurrent, uncommitted M1 working changes; do not claim those as M2 changes or committed evidence.

**Open items not tested or not fully implemented:** real 2-PC LAN, independent security scan/signoff, actual second Windows OS account DPAPI failure, 4 active/4 pending secure stress, 8MiB global/16-frame writer budget, scheduled-deadline cancellation and potential in-flight send/disconnect races, manual uncertain retry command/UI, FE honest FAILED/UNCERTAIN state rendering, five OS GUI/latency cycles, end-to-end adversarial MITM. `T061/T062/T078/T086/T087/T090` remain unchecked even though some focused negative tests exist. See [external blockers](m1-blocker-at-m2.md).

**Meaning:** local regression passed on **one** desktop and does **not** mean production secure messaging is independently verified.
