# Requirements Quality Checklist — Stage 06: Secure LAN File Transfer

**Feature:** `006-file-transfer` · **Date:** 2026-11-21
**Status:** **SPEC DRAFT + 10/10 CL-06 PENDING; 8 user stories, 56 FR, 14 SC, 16 AC documented. No implementation plan, tasks, File-code, File-transfer tests or release verification.**
**Sources:** [Spec](../spec.md) · [Clarifications](../clarifications.md) · [ROADMAP Stage 06](../../../ROADMAP.md) · [Constitution](../../../.specify/memory/constitution.md) · [M2 secure contract](../../003-p2p-messaging/contracts/chat-v2.md) · [004 Group](../../004-group-chat/spec.md) · [005 History](../../005-chat-history/spec.md).

## 1. Scope & requirements writing quality

- [x] ROADMAP **06.01–06.08** included in one `006-file-transfer` feature; P2P happy path independently testable.
- [x] 8 user stories with Given/When/Then positive and negative tests: offer, consent, streaming, integrity/finalize, cancel, malicious paths, Group conditional, Live/Media 007 seam.
- [x] **56 numbered FR-06** with MUST/MUST NOT and concrete network, filesystem, streaming, integrity and UI behaviors.
- [x] **14 measurable SC-06**, all formulated as future test metrics rather than fabricated PASS.
- [x] **16 AC-06-A…P** preserving ROADMAP A–D, adding privacy/path/ACL/replay/resource/Group/release gates.
- [x] Stage 006 is generic File transport; Image/Video/Sound preview and codec playback/calls excluded until 007. History 005 remains text/emoji only by its own approved scope.
- [x] Java 21, Swing, Maven, Windows 11, M1/M2 manual connection and M2 verified TLS identity/pin preconditions explicit.
- [x] Group 004 actual owner relay/provenance/JOIN/REMOVE/handoff prerequisite and max 5 members/shared M2 sessions documented; no false Group File claim.
- [x] Existing local FE `Attachment` / `MediaFiles` are not presented as a working LAN File transfer. Source metadata has local policy not necessarily compatible with File v1 0 byte.

## 2. Clarifications — **PENDING user approval (0/10 confirmed)**

- [ ] **CL-06-01** — Max file size and zero-byte handling; recommended 256 MiB/empty allowed.
- [ ] **CL-06-02** — Per-file receiver Accept/Reject vs session-scoped allow.
- [ ] **CL-06-03** — User-selected destination per file vs pre-approved Downloads folder.
- [ ] **CL-06-04** — No-clobber collision rename vs separately approved overwrite.
- [ ] **CL-06-05** — Delete partial/no resume vs manually authenticated resumable transfer.
- [ ] **CL-06-06** — Fail on source mutation vs consented private snapshot.
- [ ] **CL-06-07** — Group owner-mediated per-recipient transfer after 004 Verified vs explicit Group deferral.
- [ ] **CL-06-08** — Multiplex protected File profile in existing M2 TLS vs separately human-approved TLS data channel.
- [ ] **CL-06-09** — RAM-only transfer state vs encrypted retained metadata after restart.
- [ ] **CL-06-10** — Manual Open with warning vs high-risk format denylist/override.

**Rule:** These are proposed product choices, **not user-confirmed A defaults**. Safety requirements are binding regardless of choice: exact verified identities, explicit receiver permission, bounded streaming, SHA-256 integrity, durable safe finalization, no traversal/unapproved overwrite, no false completed, no automatic file execution. The specification does **not** bind numeric file caps/filename collision/resume/retention until user confirmation and technical plan.

## 3. Network, security and storage test coverage quality

- [x] File content/metadata only over mutually verified M2 TLS and authenticated File capability, never M1 plaintext or M2 v2 `CHAT_TEXT`.
- [x] Separate versioned File protocol/profile requirements, allowlist, framing/size, malformed JSON/UTF-8/UUID/int64/unknown type negative paths.
- [x] Scoped verified P2P 2-device authorization; pinned outsider knows conversation ID but may not read any metadata/content.
- [x] Offer/Accept/Reject consent precedes any bytes; no background socket, automatic upload, discovery or retry.
- [x] Safe target handling covers Windows absolute/UNC/device/ADS/reserved/trailing names, symlinks/junction/reparse and TOCTOU/collision cases.
- [x] Fixed bounded stream/chunks, backpressure and share M2 chat/Group resource cap; Swing EDT never blocked.
- [x] SHA-256 full file + length before durable/atomic finalization, sender only completes on authenticated receiver terminal outcome.
- [x] Cancel/timeout/half-close/failed disk/rename/crash/changed source and retry/replay cases documented with explicit cleanup policy to be approved.
- [x] No automatic file open/run/decompression or false malware-free assurance; future File type warning decision OPEN.
- [x] No File content in History 005 or Demo; no fake chat APP_ACK/READ from File transfer.
- [x] Group member-to-member bypass forbidden, current owner signed provenance, JOIN/REMOVE cutoff and per-recipient partial state if enabled.
- [x] Independent security/privacy review, real 2 physical Win11 LAN PCs and multi-process/Group tests required before verified release.

## 4. Planning and execution gates — intentionally OPEN

- [ ] User confirms **CL-06-01…10**; reflect decisions in `spec.md` and `clarifications.md` without bypassing inherited safety.
- [ ] Write reviewed `plan.md` with Constitution Check I–VII, exact File profile/version/capability schema, size/chunk/timeout/resource numbers, Windows secure path and atomic finalize choices.
- [ ] Produce strict typed File wire contract/test vectors and threat/dependency review, including Group signed original member proof design.
- [ ] Create `tasks.md`: Java21 RED→GREEN per US/FR/AC, exact dependency gates, M2 shared-file ownership and no overwrite of dirty M2 worktree.
- [ ] Implement & run actual secured 2-peer P2P file transfer and Group owner-mediated integration only after 004 readiness.
- [ ] Capture AC-06-A…P on Java21 tests and **two actual physical Windows PCs on LAN without Internet**, manual inbound Accept/SAS and independent review.
- [ ] Assert **006 Verified** only after all mandatory AC/security gates, **M4 only after 006 AND 007 Verified**.

**Conclusion:** The **specification and checklist are written and internally traceable**, but user Clarify, plan, code, file tests, 004 network dependencies and security/release verification remain **OPEN/BLOCKED** as applicable. Do not mark ROADMAP 06.01 implementation done simply because requirements mention protocol fields.
