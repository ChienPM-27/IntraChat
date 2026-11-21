# Execution blockers — Phase05 verified stop conditions (2026-11-21)

**Source checkout:** clean `005-chat-history` worktree at starting SHA `9acfb8214650fa88c932a81ae9589aa10af7dc23`; this report precedes the task execution commit. **User goal:** implement until goal or a **verified blocker**. The target full phase 005/M3 is **NOT achieved**. The following are observed technical/dependency gates that prevent sensitive follow-on tasks, not excuses to mark tasks done.

## B-005-01 — Full encrypted SQLite vendor + independent approval **BLOCKED**

- Directly inspected committed `pom.xml`: **no SQLite / SQLCipher dependency declared** (case-insensitive `sqlite|sqlcipher` search returned no hits). `storage/history/SqliteHistoryRepository.java` also absent.
- CL-05-02 **requires fully encrypted SQLite plus DPAPI CurrentUser key and owner-only ACL**, including protection of DB, WAL, journal and temp. A standard plaintext JDBC SQLite with a DPAPI-wrapped key would violate the confirmed requirement.
- This execution has **no externally attributed security/license/CVE/native reviewer signoff**, no approved artifact/version, and no tested Windows cross-user/WAL side-file evidence. This is a confirmed **missing approval/prerequisite**, **not** a claim that every encrypted SQLite candidate is unavailable.
- Blocks `T005/T006` until third-party approval, and `T037–T052` secure persistence and dependent T053–064/History Live sync. No unreviewed native dependency added or plaintext file created.

## B-005-02 — Origin signature and History capability protocol freeze **BLOCKED**

- `contracts/history-v1.md` is explicitly a **draft**, `protocol/history/HistoryV1Codec.java` absent. Existing M2 v2 app message does not provide a detachable, externally verified source signature for archived recipient copies.
- T004 requires *independently checked* canonical binary signing vectors, post-TLS transcript binding and protocol version compatibility. No independent signer/security reviewer or golden-vector approval exists in the committed evidence.
- Blocks production `T004` and dependent T025–T028, T065–080, and P2P real Sync T081–096. No “valid signature” is manufactured from TLS hop or replaced by a guess.

## B-005-03 — Real Group 004 owner/relay/handoff runtime **BLOCKED**

- `specs/004-group-chat/tasks.md` records **18/124** task completion (pure domain only), secure Group protocol/FE/owner handoff unimplemented.
- Direct file inspection: `protocol/group/GroupV1Codec.java`, `application/group/GroupOwnerHandoffService.java` and `application/group/GroupRelayService.java` are **absent**. Relevant Group T005/T006/T032/T034 in 004 remain unchecked.
- Thus Group History signed owner and per-member JOIN/REMOVE eligibility cannot be used as real authenticated authorization. Pure `GroupHistoryEligibility` only models a time-window policy and **does not** prove a signed owner or Group wire exists.
- Blocks T031–T034 and T097–T110 and AC-B/H/I/O until external 004 completion. **No member mesh or insecure owner-fallback** implemented.

## B-005-04 — Physical LAN/reviewer release evidence **NOT AVAILABLE / NOT VERIFIED**

- Windows inventory observed a single local endpoint: `XUANHOAISLECOOL`, model `Lecoo N155A`. **No second physical Windows 11 LAN endpoint was supplied, connected to or tested in this run**, and no independent security reviewer gave signoff.
- This establishes **missing acceptance evidence in this execution**, not a claim no other machine exists.
- Blocks `T134/T135/T136` and M1/M2 Gate 1; local 2/3-JVM unit or loopback tests never replace two physical PCs without Internet, manual inbound Accept/SAS, and independent review. Milestone M3 requires both 004 and 005 verified.

## Scope of work completed before stopping

- JDK21 baseline `.\mvnw.cmd -o test` PASS **122/122** at initial SHA.
- Pure History domain RED suite expected compilation failure (`exit 1, 168 "cannot find symbol" diagnostics`) before classes, GREEN `14/14` and full regression `136/136`.
- Exact pair P2P ACL RED expected compilation failure (`exit 1`), GREEN `2/2` and full regression `138/138`.
- New code is **pure domain/authorization policy only**, **not** encrypted SQL storage or authenticated network service. T001–T003/T007–T009 and T011–T024/T029–T030 may be checked after evidence review. `T010` remains **unchecked** because its dependency on T004–T006 reviews is unresolved; its conditional Constitution analysis is written, but that does not close technical Gate 0.
- **Release decision: BLOCKED / 005 NOT VERIFIED / M3 NOT VERIFIED.** This is a verified dependency stop as requested, not feature completion.

## Exact unblock requirements

1. Independently approve a specific encrypted SQLite JDBC/native binary (version/license/CVE/provenance/WAL/temp/DPAPI/ACL) and provide Windows second-account test environment; only then implement secure store.
2. Obtain independent canonical signature/capability/v2-compatibility review and synthetic golden vectors; only then implement signed History v4 wire.
3. Finish/verify 004 secure owner relay, member origin signatures, durable JOIN/REMOVE and owner handoff.
4. Provide at least two distinct physical Windows 11 LAN PCs and independent release reviewer for actual Gate 1 AC-05-A…P testing.

**Do not mark unchecked blockers as passed. No private chats, keys, SAS or DB artifacts stored in this report.**
