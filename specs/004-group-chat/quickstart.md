# Quickstart & Verification Plan — 004 Group Chat

**Status:** **PARTIAL DOMAIN IMPLEMENTATION — 15 Group Java21 unit tests PASS; no Group network/FE/3-JVM or physical acceptance, AC-04-A…L NOT RUN**
**Date:** 2026-11-21
**References:** [Spec](spec.md) · [Implementation plan](plan.md) · [Contract](contracts/group-v1.md) · [Group data model](data-model.md).

## 1. Preconditions

- Windows **11 x64**, actual **Temurin Java 21**, Maven Wrapper already in repo; ≥3 independent Java client processes (A owner, B/C members), at least **two distinct physical Windows LAN PCs** for inherited M1/M2 gate. A fourth client D tests unauthorized access. A 5-device run adds D and E as members, no fifth owner socket.
- No public Internet needed; manually input IPv4:port (default `47321/TCP`); each incoming TCP connection manually **Accept/Reject**. Do not silently enable public firewall profile, discovery or auto-reconnect.
- Each pair of Group members needs verified **pinned SPKI** to authenticate each other's Group source signatures; first-contact M2 TLS/SAS/human confirmation is necessary. Group invite acceptance alone is not sufficient cryptographic verification.
- M1 Gate 1 physical LAN and M2 Gate 1 TLS/peer authentication/privacy reviews **MUST** be completed before Group feature can be released. Current blockers: [M2 blocker memo](../003-p2p-messaging/evidence/m1-blocker-at-m2.md).
- **Group feature is not end-to-end implemented.** Pure Group domain Java classes and 15 JUnit tests now exist and are [recorded](evidence/domain-red-green.md), but there is **no** `verify-m3.ps1`, real Group network/FE service or Group acceptance run in this branch. Do not claim any unrun scenario passed.

## 2. Existing Java21 project preflight (not Group acceptance)

~~~powershell
# Execute from repository root on a Windows 11 test machine.
$env:JAVA_HOME = 'C:\Users\XuanHoai\.jdks\jdk-21.0.12.1+1'
$env:Path = "$env:JAVA_HOME\bin;$env:Path"
java -version
.\mvnw.cmd -o test
# Existing M2 verification script (only if M1/M2 prerequisites/environment match):
powershell -NoProfile -ExecutionPolicy Bypass -File scripts\verify-m2.ps1 -Offline
~~~

The path above is a **known development machine example**, not a universal Java installation. Use Java21 at the actual installed path of each test PC. Archive commands, exit codes, JUnit XML and commit hash; don't cite an unrun example as PASS. Existing M2 tests are regressions, **not Group tests**.

## 3. Suggested 3-client successful flow (after implementation)

1. On A/B/C: each user launches **Live**, starts listener, accepts incoming TCP manually and completes verified M2 TLS/pairing where necessary. Compare fingerprints/SAS over independent trusted channel. Pin each Group origin's public key on every intended recipient (may use temporary direct pair sessions; does not require B↔C active socket while Group chat runs).
2. A creates Group G, maximum 5 devices total. A invites B; B explicitly accepts; owner commits roster revision. A invites C; C explicitly accepts. Display groupId/owner/current revision and authenticated member roster.
3. B sends `Xin chào cả nhóm 👩🏽‍💻`. Confirm B→A→C uses authenticated encrypted hops, B is **origin** even though A is sender on A→C transport, A/C see one bubble each and D outsider receives **0** Group content.
4. Open G on C with foreground window, scroll bubble into viewport ≥250ms; C signs Group READ; A relays receipt to B; B UI marks **C viewed**. Repeat with C notification-only/inactive window: B MUST NOT receive READ. Show per-recipient delivery/READ evidence, not “everyone read” from owner ACK alone.
5. B proposes D; A owner explicitly **approves**; D separately **accepts**; only after owner commit D can receive **new** messages. Reject/timeout proposal or invite and verify no access.
6. A removes D and durably commits new revision. Send another Group message; verify **0** newly dispatched Group data to D. P2P A↔D remains independent.
7. A offers voluntary owner handoff to B. B explicitly accepts and commits epoch e+1 with signed A+B proof; after activation B relays, A cannot continue to act as owner e. Inject failures at each boundary and verify PAUSED rather than two owners.
8. Close all clients. Relaunch: **Group metadata** (ID/name/owner/roster/revisions) loads from DPAPI protection; Group initially PAUSED, chat/draft/receipt transcript **absent**. Manually reconnect and sync current signed owner state; no auto history/backfill.
9. Add E so roster reaches **5 devices** including owner; verify 4 owner peer sockets, quota/backpressure, no invisible 5th socket; if owner already uses one slot for unrelated P2P, report capacity rather than auto-evict.

## 4. Negative/security matrix

| Category | Injection | Expected |
| --- | --- | --- |
| Unauthorized outsider | D knows groupId or has authenticated P2P with A but is not Group member | No Group content/roster, no joined role |
| Member invitation spoof | B proposes D; B forges owner approval; D accepts replayed invitation | Owner must approve and commit; no membership otherwise |
| Invalid sender | A alters B's text, changes originId or advertises unknown B key to C | Signature/SPKI verification rejects, no fake B bubble |
| Forged READ | A forges C readEventId; C background; wrong session/recipient | No C VIEWED |
| Membership race | B SEND concurrent owner remove B; delayed frame to C | New sends after durable remove fail; previously transmitted bytes not falsely “recalled” |
| Handoff partial commit | Crash A/B before/after durable retire/commit, restore older file | Group PAUSED or one signed owner only; old A cannot resume |
| Key rotation | Group member or owner changes M2 identity public key | Block until new human re-pair; no TOFU silent key replacement |
| Protocol version | v2-only peer, invalid v3 JSON/frame/unknown field, raw v1 Group type | Group rejected; v1 diagnostics/P2P safe |
| RAM/queue | 32KiB text boundary, >65,536 frame, 5→6 members, 8→9 groups, slow C | Bounded rejection, other recipient delivery unaffected, EDT responsive |
| DPAPI/corrupt | Wrong Windows user, changed ACL, corrupt or missing protected file | Fail closed, no auto plaintext reset; current user metadata-only survives |
| Offline owner | Kill A before handoff, B tries Group SEND | PAUSED + draft RAM + manual connect, no B→C bypass |
| Demo isolation | Demo seeded Group/READ and Live real Group same labels | No mock traffic/receipts in Live |

## 5. Evidence template (fill **only** after real runs)

~~~text
Feature: 004-group-chat
Commit: <actual tested commit>
Machine hosts: <anonymized distinct physical IDs and number of JVMs>
Windows build / Java21 runtime / network profile: <actual observations>
Test command / exit code / JUnit count: <actual>
Peer trust/pinning & manual consent: <actual verified actions; NO secrets>
Topology / owner relay / 5-device capacity: <observed>
Invite proposal + owner approval + consent: <observed>
Handoff success + injected crash cases: <observed>
READ actual viewport + forged receipt rejected: <observed>
Group metadata after restart (no chat restore): <observed>
AC-04-A…L evidence references: <files/screenshots without private data>
Outstanding M1/M2 gates / independent security review: <review signatures>
Decision: NOT VERIFIED until every mandatory gate is met
~~~

**Safety:** redact real names/IPs if they identify users, never include private chat/keys/fingerprint SAS credentials in committed logs. Distinguish independent **JVM count** from independent **physical PC count**. localhost can help debugging but **cannot** satisfy physical-LAN evidence.

## 6. After-plan work

The [implementation task tracker](tasks.md) now contains **124 dependency-ordered RED→GREEN and gate tasks (0/124 completed)** for all eight confirmed CL-04, membership/owner handoff persistence, Group codec/canonical signing, per-recipient READ, multi-client integration, negative/privacy and release gates. This is documentation only; Group runtime tests remain **NOT RUN**. Keep Group release status separate from M3 (which additionally requires History 005).
