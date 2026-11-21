# Architecture Impact Review — 10 confirmed CL-05 decisions

**Feature:** `005-chat-history` · **Date:** 2026-11-21
**Input authority:** User explicitly confirmed **CL-05-01…10 = option A (10/10)**.
**Outcome:** **PRODUCT DECISIONS CONFIRMED, TECHNICAL GATE 0 CONDITIONAL, RELEASE GATE 1 BLOCKED / NOT VERIFIED.** This is a design trace review, **not** an independent cryptographic/license/security approval or executed test report.

[Spec](../spec.md) · [Confirmed decisions](../clarifications.md) · [Plan](../plan.md) · [ADRs](../research.md) · [Data model](../data-model.md) · [Wire contract](../contracts/history-v1.md) · [Checklist](requirements.md).

## 1. CL → implementation impact → acceptance trace

| Confirmed CL | Product decision is binding | Architectural scope/implementation consequence | Verify in tests/AC |
| --- | --- | --- | --- |
| **CL-05-01 A** | First-use explicit opt-in, **only future** messages | Protected consent state, activation boundary, no legacy RAM/mock import; disable stops capture | FR-05-004/020; AC-05-A/N/K |
| **CL-05-02 A** | Fully encrypted SQLite + DPAPI CurrentUser + ACL and review | Separate random DB key wrapped DPAPI, security-reviewed full SQLite pages/WAL/temp, owner-only ACL and wrong Windows user check; **no plaintext SQLite/native fallback** | FR-05-010/011/012/041; AC-05-D/L |
| **CL-05-03 A** | Default **90 days or 500 MiB**, configurable | Enforce age threshold and total store-size budget, triggering policy whenever either threshold applies; warn on cleanup; never discard tombstone/fence and silently reimport | FR-05-019/040; AC-05-J/N/M |
| **CL-05-04 A** | Manual Sync **per selected conversation** | Explicit user action after verified READY and protected History cap, no auto connection/sync; correct request scope/checkpoint | FR-05-021/027/029; AC-05-E/F |
| **CL-05-05 A** | P2P only **two verified deviceId/SPKI identities** | No device/account linking, strict ACL before summary (not just valid TLS pin); fail non-enumerating for outsider | FR-05-001/023/031; AC-05-E/F |
| **CL-05-06 A** | Group **current owner only**, eligibility after JOIN and until REMOVE | Owner signed epoch/roster/handoff proof; Group disabled until 004 verified; no member mesh or pre-JOIN backfill; revoke before each chunk | FR-05-033–037; AC-05-B/H/I/O |
| **CL-05-07 A** | **Local-only delete** + tombstones | Durable local event tombstones and conversation deletion fences, compaction/quota safety; no remote deletion claims or outbound delete protocol | FR-05-038/039/040; AC-05-J/N |
| **CL-05-08 A** | **No ACK/READ receipt persist or sync** | No receipt tables/wire types/restore; SAVED and SYNCED are copy states, never upgrade M2/Group delivery/read; signed viewer receipt remains runtime-only | FR-05-006/007/046; AC-05-K |
| **CL-05-09 A** | **Immutable events**; different content/signer for same ID → quarantine | Scope+event unique index, canonical hash/signature validation, idempotent no-op for identical replay, tombstone wins reimport; no timestamp LWW | FR-05-009/025/026/043; AC-05-C/J |
| **CL-05-10 A** | **No backup/export**; safe migration only | Exclude user export/import and automatic backup, migration must be atomic/recoverable with no plaintext side-copy | FR-05-017/041/042; AC-05-D/L/N |

### 1.1 Explicit disallowlist after approval

Do **not** silently choose any former B alternative: default-on History, unencrypted SQLite, custom event-store substituted for SQLite, unlimited retention, automatic sync, third-device/multi-device identity linking, Group member-to-member history sync, remote wipe/delete propagation, receipt archive/replay, mutable LWW edits, or automatic/user export/backup. A change requires an explicit new product clarification and spec/plan update.

**Protected storage nuance:** The user confirmed SQLite **with actual encryption**; JNA DPAPI protecting only an encryption key does not make unencrypted SQLite safe. An appropriate SQLCipher-compatible/native distribution must pass independent cryptographic/license/CVE/Windows cross-account review, including encrypted WAL/temp, before secure storage can be implemented/released. If none is approved: **BLOCKER**; do not change storage type or security level to make the test pass.

**Retention nuance:** “90 ngày hoặc 500 MiB” is interpreted as **both active default thresholds**; the age limit applies to old events and the aggregate size limit applies to the local History store, so the first relevant limit may initiate retention/space handling. User can adjust within safe bounds selected during engineering. Retention must not destroy non-resurrection fences. Exact cleanup timing, user notification, and cryptographic erasure need tests/technical review before claims.

**Group temporal nuance:** Source timestamps are not authority. Signed owner commit order/revision and join/remove cutoffs determine visibility. Owner handoff requires signed A+B continuity; arbitrary member cannot serve Group History while owner offline.

**Privacy nuance:** History copy acknowledgements `HISTORY_APPLY_ACK` are allowed as protocol control but are **not** chat-delivery application ACK or viewer-signed READ receipts. Do not confuse these concepts or persist old per-recipient READ states.

## 2. Technical review and dependency blockers — still OPEN

| Gate | Current finding | Exit evidence required |
| --- | --- | --- |
| **G0.1 User Clarifications** | **DONE: 10/10 CONFIRMED** | This user confirmation and [decision log](../clarifications.md) |
| **G0.2 Encrypted SQLite distribution** | **OPEN / NOT APPROVED** | Reviewed version, binary provenance/signature, license/CVE/transitive artifacts, Win11 x64/Java21, encrypted DB+WAL+temp, no fallback |
| **G0.3 Key protection and ACL** | **OPEN / NOT TESTED** | DPAPI CurrentUser separate DB key; owner-only ACL directory/files, reparse protection, separate authorized Windows account negative tests |
| **G0.4 History source signature/profile** | **OPEN / NOT FROZEN** | Canonical signed event vectors reviewed independently, M2 origin proof limitations, protected HISTORY_V1 capability transcript with v2-only compatibility regression |
| **G0.5 History resource and deletion semantics** | **DESIGN ONLY** | Benchmark frame 65,536 bytes, proposed batches/page/concurrency, 90d/500MiB quota, tombstone retention, disk-full/crash outcomes |
| **G1.1 Inherited M1/M2 Gate 1** | **BLOCKED** | ≥2 distinct authorized Win11 LAN physical PCs, manual human inbound consent/SAS, no Internet and security approval |
| **G1.2 Group 004 dependency** | **BLOCKED** | Verified owner relay, member origin sign, durable owner handoff/JOIN/REMOVE and ≥3-JVM Group acceptance |
| **G1.3 History AC-05-A…P** | **NOT RUN / NO IMPLEMENTATION** | Java21 test-first source, two-peer P2P sync, 3-JVM Group, full negative/fault/privacy test evidence, independent signoff |

**Constitution I–VII:** Plan contains a design Gate 0 assessment. Confirming 10 product decisions resolves the former **clarification** blocker only; Principles II/III/IV/V and physical/security gates remain conditional/blocked. No Group 004/M3 release claim.

## 3. QA scope and next work

Reviewed documentation: `spec.md`, `clarifications.md`, `plan.md`, `research.md`, `data-model.md`, `contracts/history-v1.md`, `quickstart.md`, `checklists/requirements.md` and `ROADMAP.md`. Every CL-05 requirement maps to a concrete architecture impact and AC; all 48 FR, 12 SC, 16 AC and ROADMAP 05.01–05.09 remain in scope.

**Next:** independent encrypted-SQLite/cryptographic design review and canonical vectors → create task list (`tasks.md`) with RED→GREEN and gates → implementation → actually executed Java21/Maven/2-PC tests. Documentation QA is not a History functional test.
