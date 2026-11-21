# Requirements Quality Checklist — Stage 06: Secure LAN File Transfer

**Feature:** `006-file-transfer` · **Date:** 2026-11-21
**Status:** **10/10 CL-06 USER CONFIRMED (9 A, CL-06-03 B); 36/160 tasks executed, 27 isolated File JUnit PASS, 165/165 full regression PASS. FILE_V1 wire/Win11 filesystem Gate 0 BLOCKED; 006/M4 NOT VERIFIED.**
**Documents:** [Spec](../spec.md) · [Confirmed CL log](../clarifications.md) · [Implementation plan](../plan.md) · [160 tasks](../tasks.md) · [ADRs](../research.md) · [Data model](../data-model.md) · [FILE_V1 draft](../contracts/file-v1.md) · [Quickstart](../quickstart.md) · [Architecture impact](architecture-impact-review.md) · [ROADMAP](../../../ROADMAP.md) · [Constitution](../../../.specify/memory/constitution.md).

## 1. Feature and requirements quality — DOCUMENTATION DONE

- [x] Phase06 scope: single P2P File stream; Group owner-mediated fanout after actual Group004 Verified; Multimedia 007 depends on File but its preview/playback remain excluded.
- [x] Eight user stories cover sender, per-file consent, bounded chunk progress, SHA256 durable commit, cancel/restart, Windows path security, Group authority and Live/Demo isolation.
- [x] 56 numbered FR-06 use explicit MUST/MUST NOT, traceable to source/consent/wire/staging/security/Group/Live.
- [x] 14 SC-06 are measurable future outcomes with no fabricated PASS.
- [x] 16 AC-06-A…P preserve ROADMAP A–D and add privacy/path/adversarial/crash/protocol/resource/Group/release cases.
- [x] 06.01…06.08 Roadmap mapped in [spec](../spec.md) and [plan](../plan.md); **no Roadmap implementation checkbox changed to done**.
- [x] M1 plaintext v1 diagnostics, M2 authenticated TLS/pinned SPKI/human inbound Accept/SAS, v2-v4 profile isolation, no new File port or auto reconnect explicit.
- [x] M2 `NetworkConfig` shared max 4 sessions/16 frames/2MiB queued per peer and TLS frame 65,536 bytes treated as existing caps; future chunk/timeout concurrency values marked proposals and review targets.
- [x] Group 004 18/124 pure domain and History 005 22/136 partial, M1/M2 2 physical PCs Gate 1 and independent review remain **NOT VERIFIED**.
- [x] Current local Demo `Attachment.sizeBytes>0` and `MediaFiles` not misrepresented as verified File LAN runtime.

## 2. Clarification decisions — **10/10 USER CONFIRMED**

- [x] **CL-06-01 A** — ≤256 MiB/file (268435456 bytes), **0-byte supported**, SHA-256 empty.
- [x] **CL-06-02 A** — **Accept/Reject each offer** even after pinned M2 identity.
- [x] **CL-06-03 B** — **User-preauthorized Downloads root**. Per-file Save As **not** required, but per-file Accept/Reject **still mandatory**. Changing/revoking root supported; unsafe/missing root blocks acceptance.
- [x] **CL-06-04 A** — **No overwrite**; safe collision-free filename or ask user for different name, atomic no-clobber.
- [x] **CL-06-05 A** — Cancel/fault delete partial; **no resume**; manual retry new transferId, app-owned orphan cleanup after crash.
- [x] **CL-06-06 A** — **Fail/cancel if source mutated**, prehash/stream-hash/identity verification; **no snapshot**.
- [x] **CL-06-07 A** — **Current owner-mediated Group fanout**, per-recipient outcomes; only after 004 Verified, signed source/roster/epoch/JOIN→REMOVE.
- [x] **CL-06-08 A** — **Separate FILE_V1 profile on same verified M2 TLS socket/session**, mutual protected capability and shared quotas, no new listener.
- [x] **CL-06-09 A** — **RAM-only transfer metadata and state**, no History 005 binary/transfer metadata archive; only user saved final file durable.
- [x] **CL-06-10 A** — **Generic binary allowed with warning**, no auto-open/execute/unzip; manual OS Open only.

**Guard:** Product A/B choices are binding, but selected technical protocolVersion 5, ≤32KiB chunk target, Win11 no-clobber API, signed Group original member canonical vectors and performance numbers **NOT independently approved**.

## 3. Documentation/architecture alignment — DONE AS DRAFT ONLY

- [x] [plan.md](../plan.md) includes Constitution I–VII review, trace/critical path, separate FILE_V1 negotiation on M2 TLS, typed binary chunk, integrity hash, state machine and Java21 RED→GREEN test strategy.
- [x] Downloads root permission vs per-file offer consent are **separate**; staging/commit inside approved Downloads root, no peer pathname, collision-safe no-clobber/Windows reparse/ACL/TOCTOU review.
- [x] 256MiB + empty file: zero chunks but full Accept/END/empty SHA-256/durable commit; no Demo positive-size restriction.
- [x] No persistent transfer index/resume; crash can strand partial until **guarded** next-start app-owned orphan sweep; cannot delete arbitrary `*.part`.
- [x] Full-file SHA-256 prehash+sent bytes+receiver digest, final receiver proof; 100% sender progress != completed and never creates fake M2 CHAT_ACK/READ.
- [x] Group 004 signed owner relay/handoff, JOIN/REMOVE per-recipient rights and ≤5 devices; Group File stays disabled before real 004 Gate.
- [x] Separate File strict draft contract, positive allowlist and mutual protected capability; no raw M1/v2 CHAT_TEXT transfer/auto-open.
- [x] Research, conceptual data model, File V1 wire and quickstart artifacts exist and all explicitly state **DRAFT / NOT RUN**.

## 4. Independent technical security reviews — OPEN / BLOCKED

- [ ] Obtain independent freeze/approval of FILE_V1 app version, exact canonical binary codec and mutual protected preface-capability transcript, replay/downgrade and cross-v2/v3/v4 compatibility golden vectors.
- [ ] Obtain independent Win11 Downloads root settings/ACL/reparse/junction/ADS/TOCTOU, safe staging and atomic no-clobber/FSYNC/crash-orphan cleanup implementation/security review.
- [ ] Benchmark 256MiB transfers, per-chunk encoded size, M2 4 sessions/16 frames/2MiB queue fairness, worker/EDT and socket half-open under real Java21 Windows load.
- [ ] Verify 004 Group runtime owner signed origination/epoch/roster/JOIN/REMOVE/owner handoff and trusted per-member fanout. **Currently 004 only 18/124 pure domain**, no proven Group network.
- [ ] Obtain attributable independent privacy/protocol/filesystem review; product decisions alone do **not** satisfy it.

## 5. Future implementation and release tasks — NOT DONE

- [x] Create [160-task plan](../tasks.md) test-first RED→GREEN with user CL trace, shared-file ownership, review and Group gates. **Planning checklist satisfied; 36/160 tasks actually completed with [Java21 RED→GREEN evidence](../evidence/trace-and-tdd.md).**
- [ ] Implement FILE_V1 domain/codec/service/Win11 secure Downloads receiving, consent/stream/integrity/UI in separate clean worktree; protect dirty 003 main checkout.
- [ ] Run actual Java21 File suite, 2-peer verified M2 session, malformed/corrupt/path attack/cancel/stress tests, log AC-06-A…P evidence.
- [ ] Test **≥2 actual physical Windows 11 x64 PCs** in a LAN without Internet with manual IPv4 connection and inbound Accept/SAS, never claim local loopback equals physical Gate 1.
- [ ] Mark 006 VERIFIED only after all mandatory P2P and Group (selected CL-06-07 A) gates/security/AC pass; **M4 VERIFIED only if 006 AND 007 Verified**.

**Conclusion:** Clarify **10/10** locked, [160 tasks](../tasks.md) tracked (**36 completed**) and plan/wire/impact aligned; **27 new File unit JUnit plus 165 full tests PASS**, but no on-wire FILE_V1, Windows receiver, 2-PC transfer, independent security approval, or 006/M4 release validation.
