# Requirements & Plan Quality Checklist — 004 Group Chat

**Date:** 2026-11-21 · **Status:** **8/8 Clarifications CONFIRMED; planning documents written; Group feature NOT IMPLEMENTED/VERIFIED**
**Documents:** [spec](../spec.md) · [plan](../plan.md) · [research](../research.md) · [data model](../data-model.md) · [contract](../contracts/group-v1.md) · [verification quickstart](../quickstart.md).

## 1. Clarifications — user confirmed 2026-11-21

- [x] **CL-04-01:** Owner relay over verified TLS; Group owner is current coordinator.
- [x] **CL-04-02:** Any active member can **propose** invitation, but **owner must approve** and invitee separately accept.
- [x] **CL-04-03:** Owner can **voluntarily hand off** to willing verified member; no automatic election; crash-safe epoch commit.
- [x] **CL-04-04:** **Persist protected metadata** (groupId/name/roster/owner/epoch/revision), no persistent chat text/draft/receipts.
- [x] **CL-04-05:** **Max 5 devices** (owner + 4 members); shared M2 4 active peer sessions at owner.
- [x] **CL-04-06:** **Group READ per member** from real Live foreground viewport event + authenticated signed viewer receipt.
- [x] **CL-04-07:** New member can see **only messages after committed join**, no backfill/History 005.
- [x] **CL-04-08:** **Owner durable REMOVE commit** is cutoff for new Group fan-out; already sent bytes cannot be recalled.

## 2. Specification quality (WHAT/WHY)

- [x] Scope/out of scope distinguishes Group, History 005, File 006, Media 007, Demo and M2 protected P2P.
- [x] Identity, owner authority and Group privacy are explicit: knowing groupId/IP/name does **not** grant access.
- [x] Six user stories contain Given/When/Then with positive/negative and nonblocking/offline cases.
- [x] 40 numbered FR-04, 10 SC-04 and 12 AC-04, traceable to ROADMAP `04.01…07.
- [x] Member proposal, owner approval, successor handoff, protected metadata and viewer-signed READ are mandatory, not optional.
- [x] Updated rule for owner offline vs committed handoff; no automatic failover/reconnect/history.
- [x] Actual current status and M1/M2 release dependencies named; Group **NOT RUN** stated.
- [x] Threat cases: forged origin/viewer/owner, old revision, duplicate, wrong key, corrupt DPAPI, membership race, owner crash.

## 3. Implementation plan quality (HOW — design, not implemented)

- [x] Technical context matches inspected M2 files/constants (v2 strict codec, verified TLS, DPAPI CurrentUser, 4 active sessions).
- [x] Constitution I–VII Gate 0 assessment with CONDITIONAL items and explicit Gate 1 evidence.
- [x] Owner relay architecture, module boundaries, session/identity port and UI EDT separation.
- [x] [Research](../research.md) ADRs compare and justify architecture, handoff, metadata, READ, quota, signature.
- [x] [Data model](../data-model.md) defines logical entities, persisted vs RAM, ACL, group/member/handoff and recipient states.
- [x] [Protocol contract](../contracts/group-v1.md) has version/capability gate, types/fields, canonical signatures, receipts/ACK, errors, compatibility and negative matrix.
- [x] Security design handles owner as hop relay not origin; independently pinned signer proof required at recipient.
- [x] Handoff durable retire/commit and ambiguous crash **fail closed**; backup rollback limitation documented.
- [x] Metadata encrypted DPAPI/ACL/atomic, restart PAUSED and signed owner resync; no persistent message/history.
- [x] Concurrency/retry/dedup/ordering/resource budgets and max 5 devices; availability/status semantics truthful.
- [x] 3+ JVM test plan, inherited physical two-PC prerequisites, test-first and independent security review gate.
- [x] No fabricated Java Group code, test runs, crypto test vectors or physical LAN evidence.

## 4. Future execution/release — intentionally not checked

- [ ] Freeze canonical binary signature vectors, schema error matrix and review protocol against independent security expert.
- [ ] Write `tasks.md` with dependency-ordered RED→GREEN implementation/test tasks (not requested in this turn).
- [ ] Implement Group codec/capability/session routing; verify strict v1/v2 regression.
- [ ] Implement Group owner/member/ACL/epoch/transfer, bounded fan-out/ACK/READ and signed provenance.
- [ ] Implement protected metadata store; actual Windows DPAPI, cross-user/ACL/corruption tests.
- [ ] Implement Live Group UI with per-recipient real viewport receipts; preserve Demo/P2P isolation.
- [ ] Run Java21 JUnit/3+ JVM tests, stress/quota/negative/owner handoff crash injection and real 5-device case.
- [ ] Close inherited **M1/M2 Gate 1 on two physical Windows LAN PCs**, independent dependency/security/privacy reviews.
- [ ] Record Group AC-04-A…L results, security signoff, full regressions and release decision.
- [ ] Complete History 005 separately before calling milestone M3 Verified.

**Summary:** Document planning can be reviewed now; **not an implemented or verified Group product**. All actual Group functional acceptance scenarios remain **NOT RUN**.
