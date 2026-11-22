# M2 Acceptance — Partial implementation, NOT VERIFIED

**Date:** 2026-11-21 · **Status:** **IN PROGRESS / PARTIAL; not a production secure chat release**.
**Environment:** one Windows 11 x64 desktop, Eclipse Temurin JDK21, two independent `javaw.exe` processes with distinct DPAPI test identities; **two separate physical Windows LAN hosts NOT RUN**.

| Spec AC | Executed evidence | Result |
| --- | --- | --- |
| **AC-03-A** text/sender/time | Protected v2 A→B and B→A actual GUI text; local timestamp and bubble direction verified | **PASS local** |
| **AC-03-B** two-way text on 2 physical PCs | Two GUI processes on **one PC only** | **BLOCKED physical LAN** |
| **AC-03-C** Vietnamese/emoji Unicode | Real GUI sample and `ChatTextValidationTest`/`ChatV2CodecTest` | **PASS tested cases** |
| **AC-03-D** offline draft/manual retry | No auto-reconnect; Live composer restores failed draft; full uncertain send/retry GUI not tested | **PARTIAL** |
| **AC-03-E** view-based READ | Initially false-positive when background, fixed using native foreground guard; local UI now shows DELIVERED until actual foreground, then READ | **PASS tested foreground cases; more negative tests needed** |
| **AC-03-F** first pin+TLS, changed key | Real first human SAS on both GUI windows, protected TLS1.3, remembered pin on relaunch, unit changed-key rejection | **PARTIAL security review** |
| **AC-03-G** ACK & READ correlation | Real app ACK vs actual view receipts, basic wrong-conversation/READ-before-ACK test; full forged/replay cross-session matrix not run | **PARTIAL** |
| **AC-03-H** ID/name/timezone | sender identity tracked by certificate/verified UUID, GUI time shown; full dual-name/timezone scenarios not run | **PARTIAL** |
| **AC-03-I** FE Live/Demo RAM-only | Live GUI real, Demo GUI separately opens, no mock credential in Live; message RAM, key/trust DPAPI | **PARTIAL** (restart/large-data audit) |
| **AC-03-J** two physical Win11 LAN peers, no Internet | **NOT RUN** | **BLOCKED** |
| **AC-03-K** all security/fault/GUI/regression tests | Java21 final `verify-m2.ps1 -Offline`: exit 0, **74/74 JUnit pass**, negative tests partial; no independent penetration/security assessment | **PARTIAL** |
| **AC-03-L** M1 gate + Constitution I–VII release | M1 Gate 1 still open; this is a work branch | **BLOCKED** |

**Verified local:** Real UI human inbound approval + matching SAS confirmed by two actual clicks; JDK21 JSSE socket messaging; READ negative OS background/positive foreground in both directions; original Demo entrypoint still works. This is genuine local functionality, not a test-only simulation of TLS.

**Unverified:** independent cryptographic security review, Windows DPAPI different-user access, corrupted-key workflow, v1/v2 shared-port dispatcher (M1 and M2 currently have separate listener lifecycles), full timeout/4 active capacity/ACL/flood/secret-log/perf tests, manual uncertain-send retry and accurate missing bubble states, and **two distinct Win11 LAN PCs without Internet**. No user Chat over M1 plaintext; `JsonEnvelopeCodec` v1 still diagnostic-only.

**Decision:** M2 stays **In Progress**, NOT Verified. M1 remains **In Progress**. This report is **not a security certification** or official milestone acceptance.

## 2026-11-21 later Java21 security-hardening addendum

The original baseline above measured **74 tests**. A subsequent local `scripts/verify-m2.ps1 -Offline` run on Temurin **21.0.12.1+1** completed **93/93 tests, 0 failures/errors, exit 0** after test-first fixes to X509 issuer/critical extensions, DPAPI stored UUID/CN exact binding, CHAT_V2 reserved-zero UUID and UTC/read receipt metadata. See [full RED→GREEN evidence](m2-hardening-round2.md). The task tracker reached **29/90 completed** at this revision. This improves AC-03-F/G/K local confidence but **does not change BLOCKED/PARTIAL** status for real two-PC AC-03-B/J, independent security evaluation, M1 Gate 1, stress/resource audits or release AC-03-L.

## Later Java21 network/receipt security addendum — 2026-11-21

[Round 3 RED→GREEN evidence](m2-hardening-round3.md) records real TLS duplicate READ/ACK events, retransmitted same-ID ACK loss, peer pin fingerprint collisions and disconnected-session RAM ledgers found and fixed. Negative `ChatV2NegativeTest` and focused ACK/authorization/identity tests were run. The shared-worktree `verify-m2.ps1 -Offline` exited **0: 111/111 JUnit PASS** (including concurrent uncommitted M1 tests), and the **isolated committed M2 source at `98658d0` exited 0: 107/107 JUnit PASS**, with no failures/errors on Temurin 21.0.12.1+1. Current M2 task tracker is **34/90**; the previously existing direct source tests and unrelated M1-in-progress work are **not independently verified security releases**. Code/test-first tasks whose exact RED gate was not met remain unchecked.

**Release blockers are explicit:** [M1 and M2 dependency blockers](m1-blocker-at-m2.md) include the **two inaccessible physical Windows LAN peers** for `T080–T082`, M1 Gate 1, independent security reviewer and dependency/supply-chain approval. Do **not** mark M2 Verified or infer real two-host behavior from these loopback results.

## Regression re-run and stop decision — 2026-11-22

On the single connected Windows 11 host, `scripts/verify-m2.ps1 -Offline` was **actually rerun** with command-scoped Temurin **21.0.12.1+1**; exit **0**, offline Maven clean/package PASS, `--diagnostics` PASS, **113/113 JUnit tests**, zero errors/failures, one Messenger launcher packaged. This is a new local regression result, not a new two-PC or independent security result.

No individual task was checked from this regression alone. **M2 remains 34/90 In Progress, NOT VERIFIED**; M1 Gate 1 remains blocked. Physical two-Windows-PC LAN checks (`T080–T082`), M1 prerequisite (`T084`), independent dependency/security review (`T003/T083/T087`) and official handoff (`T090`) remain open. See [dated execution/blocker report](execution-blockers-2026-11-22.md) for exact command, evidence, limitations and required external access.

## Execution addendum — 2026-11-22 (newer than historical results above)

- Spec 003 now 56/90 checked, 34 still open. See [actual implementation/test evidence](m2-local-code-2026-11-22.md), [encrypted two-JVM text](us2-text-emoji.md), [sender/UTC](us3-sender-time.md) and [partial resource tests](m2-resource-tests.md).
- Actual Temurin Java 21.0.12.1+1, scripts/verify-m2.ps1 -Offline **exit 0**, **206/206 JUnit PASS** across the M0/M1/FE/M2/Group test suite, zero failures/errors, shaded executable JAR diagnostics PASS. These are not 206 M2-only tests.
- Current unified Messenger listener routes the exact first M1 diagnostic v1 HELLO to plaintext Ping/Pong only, and M2 v2 HELLO to consent/TLS 1.3+SAS. Unknown versions, downgrade and Group v3 are NOT enabled. Same-port real loopback both routes PASS.
- Separate Java21 OS processes on **one Windows host** perform first SAS callback, DPAPI pinned key restart, changed-key rejection, two-way encrypted Vietnamese/emoji CHAT_TEXT, verified sender/timestamp and application ACK. Scripted test SAS callback is NOT a human GUI SAS event.
- Manual UNCERTAIN retry requires user action and repeats original ID/time/text with duplicate-risk warning; no offline queue or auto resend. Typed ACK/READ correlates to current verified TLS session and original message; wrong sender/session/conversation fails closed with no false DELIVERED.
- New 4 verified peers / fifth denied and recovery, atomic 128 pending receipt slots under 160 concurrent sends, 500-message/2MiB per chat and 1024 message-ID dedup capacity tests PASS. Full actual TLS queued-writer/4-pending/half-open/EDT stress still OPEN.
- Swing Live initial directory/receipt callbacks now run on EDT, and prior uncertain outgoing bubble retains explicit retry. Demo Group/Media remains isolated from Live. Real physical 2-PC, different-user DPAPI token, independent security reviewer and full actual GUI SAS/READ acceptance NOT RUN.

**Gate disposition unchanged: M2 IN PROGRESS / NOT VERIFIED**. Historical test counts and earlier limitations above remain dated snapshots; use linked 2026-11-22 evidence for current local status.

## Further local implementation evidence — 2026-11-22 (latest)

**Current M2 task tracker: 65/90 checked, 25 open; M2 NOT VERIFIED.**
[Full new security/GUI RED→GREEN evidence](m2-continued-code-2026-11-22.md) supersedes this report's earlier local test-count snapshots without changing any external acceptance outcome.

- **Actual Windows Temurin 21.0.12.1+1**, `scripts/verify-m2.ps1 -Offline`: **223/223 JUnit PASS, zero failures/errors/skipped, exit 0**, shaded executable/JAR diagnostics PASS. Total includes M0/M1/FE/M2/Group test suites.
- Added production `PeerTrustService` and `VerifiedPeerIdentity`; `SecureChatCoordinator` now performs actual TLS-bound two-sided PAIR_CONFIRM/PAIR_READY with strict request, conversation and SAS transcript correlation, and protected pin commit before authorization. Revoked pin cannot be silently restored by delayed READY. Tested both domain and real JSSE TLS protocol.
- Added `TlsChatHandshakeTest` (real mutual TLS1.3) and `PairConfirmIntegrationTest`; strengthened `TlsChatSession` so a caller-supplied UUID or manually toggled READY cannot bypass the authenticated peer certificate subject, and pairing control frames cannot be sent after READY.
- Live Messenger composer supports multiline `Enter` send / `Shift+Enter` newline and preserves full UTF-8 emoji while keeping Demo's original single-line editor. Verified Live peer directory uses UUID rather than alias, retains a RAM-only offline draft target, and clears disconnected session transcript/preview. Explicit Demo vs Live entry label and mock-data separation remain intact.
- An intermediate full suite surfaced an intermittent old M1 shutdown port-refusal assertion; it passed five isolated repetitions and the final full clean suite. Full soak/reliability certification still NOT performed. An intermediate debug run incorrectly placed a redirected log inside Maven's `target` directory, making `clean` fail to delete it; final verification moved logs to Windows TEMP and passed.

**Still BLOCKED/NOT RUN:** two physically separate Windows 11 LAN PCs, independent security and dependency review, a second Windows-user DPAPI token, complete TLS resumption/queued writer/half-open resource stress, repeat manual GUI SAS and real foreground viewport READ acceptance, and M1 Gate 1. No security certification, signed release gate or Group Live enablement is implied.
