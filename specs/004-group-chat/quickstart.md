# Planned Group Chat verification runbook — Spec 004

**Created:** 2026-11-22 · **Status:** **NOT RUN / FUTURE PROCEDURE**, not instructions for a currently available Group Live feature.
**Scope:** [Spec](spec.md) · [Plan](plan.md) · [Tasks](tasks.md) · [Group V1 contract](contracts/group-v1.md) · [Windows LAN baseline](../../docs/windows-lan-setup.md).

## 1. Before attempting Group Live

- **Do not try to send Group packets today**: the repository supports Demo Group UI; `LiveChatAdapter.createGroup` remains unsupported. Core GroupIdentity/GroupRoster, canonical owner signatures, GroupV1Codec/V3 preface, DPAPI metadata and owner grant primitives now **exist and pass isolated tests**, but the Group V3 negotiated TCP transport and Live UI **are not wired/ready**. [Implementation evidence](evidence/group-core-implementation-2026-11-22.md).
- Finish/review Spec 004 Phase 1 Gate 0 (version negotiation, canonical owner roster/ticket, protected DPAPI group metadata, threat model/quotas); write actual tasks RED→GREEN and implement secure Group code behind capability gating.
- Maintain the existing verified **M2 protected TLS1.3 + both-human SAS + inbound Accept** security gate. Do **not** use M1 plaintext diagnostic channel for chat, or trust IP/name alone.
- For final Gate 1, verify M1 and M2 independently on two physical Windows hosts and obtain external security/privacy approvals. A successful Group demo cannot override upstream gate failures.

## 2. Baseline build that actually exists (not a Group test)

On the Windows development host, in repository root, after checking the current dirty working tree, one can rerun the existing M2-only regression:

```powershell
$env:JAVA_HOME='C:\Users\XuanHoai\.jdks\jdk-21.0.12.1+1'
$env:PATH=$env:JAVA_HOME+'\bin;'+$env:PATH
powershell.exe -NoProfile -ExecutionPolicy Bypass -File scripts/verify-m2.ps1 -Offline
```

**Recorded on 2026-11-22 after core implementation:** exit 0, **141/141 JUnit** including **28 Group core unit tests**, shaded JAR diagnostics PASS. **No Group LAN/Live end-to-end test**. Never describe this as three-peer Group acceptance. JDK21 path shown is this workstation's installation only; other hosts must use their own installed Java21 path.

## 3. Future 3-process/one-host negative and integration test (T081)

1. After Group code exists and RED→GREEN passes, prepare **three isolated, authorized Windows test profiles** (separate device identities and DPAPI stores), no reuse/export of real identities, chats or SAS.
2. Start 3 Java21 app processes A, B, C with separate trusted test config/stores; each peer starts its listener explicitly. Record OS, JDK version, process IDs, Git hash+dirty state and ports.
3. Each pair verifies mutual TLS/SAS through human Accept/confirm, then both sides explicitly negotiate **Group V1** capability; reject any attempt to send text before READY.
4. Owner A creates 3-member group, invites B and C, obtains their explicit Accept, commits owner-signed revision N+1.
5. Send A→B,C; B→A,C; C→A,B with Vietnamese combining marks and emoji. Check groupId/senderId/time and ensure P2P conversations are not mixed. Show exact per-recipient ACK; only recipient app foreground and correct Group row viewport creates READ.
6. Test owner offline → Group sends PAUSED, P2P independent; remove one member, stale revision/replay wrong owner/recipient and sixth-member rejection.
7. Close all three processes and relaunch. Protected group metadata should load as STALE; **message/draft/receipt content should not reappear**. No automatic reconnection/outbox or saved user text.
8. Write real commands, test case IDs, exit codes and pass/fail to `evidence/group-3peer-loopback.md`. If only one physical PC was used, label it **ONE-HOST ONLY**, not two-PC LAN.

## 4. Future physical LAN acceptance (T087–T089, AC-04-J)

**Hardware:** at least **two different Windows 11 x64 physical machines** in the **same authorized Private LAN**, with at least **three distinct test device identities/processes** (e.g. A+B on machine 1, C on machine 2). If assessing all three independent host-failures requires three physical hosts, add a third PC and record that choice explicitly.

- Record each physical host ID/OS/build/JDK21 version/LAN IPv4, process profile identity (nonsecret public fingerprint only), ports, firewall state and that **public Internet was disabled** while the LAN remained reachable.
- Never silently open Windows Public firewall or change user security identity. Any Private firewall rule must be explicitly authorized, scoped to a known interface/port and removed after the experiment if temporary.
- Repeat real inbound Accept and SAS fingerprint comparison on each independent peer. Distinct test profiles and verified trust are mandatory; no scripted acceptance in production.
- Create group A/B/C and explicitly Accept; send all directions, view per-recipient ACK/READ and observe UI for no false READ when background/offscreen.
- Reject forged/outsider D, replay roster/grant, wrong peer/session and changed-key. Remove member and inspect effect on **new** Group sends, separately document pre-removal in-flight bytes.
- Disconnect B, terminate owner A, restart C; ensure manual retry only, accurate PAUSED/FAILED/UNCERTAIN, no thread/socket leak, correct protected metadata STALE restore.
- Repeat ≥5 full GUI cycles and measure suggested EDT p95 ≤200ms / resource targets. Do **not** claim performance until measured.
- Document per-host test manifests, actual screenshots/log metadata (redact private message/SAS), test outcomes and independent reviewer signoff in `evidence/group-2host-lan.md`, `group-receipts-ui.md` and `group-acceptance-report.md`.

## 5. Security and recovery checks

- Windows CurrentUser DPAPI store: verify owner-only ACL/atomic persist/corrupt blob fail-closed; test **separate authorized Windows account token**, not a second app process using the same Windows user.
- Inspect configured group metadata path and routine logs for **absence of chat/draft/receipt/outbox**; do not expose raw keys, actual SAS or user conversations in screenshots/commits.
- Test owner offline, changed key, rollback of previously valid encrypted metadata; store load must remain STALE until online owner revalidation. No automatic owner transfer or self-elected relay.
- A Group recipient may copy/forward messages they have already received; remove is not retroactive deletion. No promise of group E2EE key protocol or Internet synchronization.
- Missing hardware/reviewer/second Windows user context ⇒ **BLOCKED / NOT RUN**, never substitute localhost/M2 JUnit for AC-04-J/K/L.

**Exit rule:** Only a reviewer-approved Group 004 implementation with actual automated, UI, multi-host, privacy and full Constitution/AC evidence may be called Verified. M3 milestone also includes History Spec 005.