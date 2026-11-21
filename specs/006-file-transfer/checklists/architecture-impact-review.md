# Architecture Impact Review — Stage 06 confirmed CL decisions

**Feature:** `006-file-transfer` · **Date:** 2026-11-21
**Status:** **10/10 CL USER CONFIRMED (9 A, CL-06-03 B); design integrated into plan. Technical independent Gate 0 and Gate 1 still OPEN/BLOCKED.** [Spec](../spec.md) · [Choices](../clarifications.md) · [Plan](../plan.md) · [Wire draft](../contracts/file-v1.md) · [ROADMAP](../../../ROADMAP.md).

## 1. Decision → architecture → FR/AC trace

| CL | Confirmed | Architecture / cross-choice effect | FR / AC coverage |
| --- | --- | --- | --- |
| **06-01** | **A** | FileOffer size 0..268435456 bytes, SHA256 empty, 0-chunk END/COMMIT; never inherit Demo Attachment >0 | FR-011…013, 020…022, 030…033; AC-A/I/K |
| **06-02** | **A** | Prompt Accept/Reject **per file** even with verified pin and approved Downloads; timeout reject, no preconsent bytes | FR-014/015/025; AC-E/F |
| **06-03** | **B** | **Preapproved Downloads directory**, user can revoke/switch; no per-file Save dialog, but **each file still needs its own Accept**. Protected settings or reauthorize each launch | FR-016…018/032/044/045; AC-D/E/G/L |
| **06-04** | **A** | Never overwrite; safe basename collision suffix or user picks different nonexisting leaf, atomic no-clobber under root with Windows TOCTOU/reparse review | FR-017/018/032/044; AC-G/L |
| **06-05** | **A** | No persistent resume, explicit fresh transferId on retry; clean transient partial after cancel/error or guarded orphan sweep after crash | FR-037…043; AC-B/C/L |
| **06-06** | **A** | Source prehash, stream-hash, source identity/size/mtime recheck, fail/cancel if changed; no snapshot | FR-011/030/035/042; AC-H |
| **06-07** | **A** | Current Group owner mediates individual recipient transfer, signed original member proof, JOIN→REMOVE fence; **BLOCKED by 004** | FR-049…052; AC-M/P |
| **06-08** | **A** | FILE_V1 separate protected profile **on existing pinned M2 TLS**, mutual post-TLS capability, same 4-session/queue limits, no new socket | FR-001…009/020…029; AC-J/K/O |
| **06-09** | **A** | Transfer data/state/receipts **RAM-only**; final file retained by user and staging transient; no History bytes or metadata index | FR-040/041/046/047; AC-C/L/N |
| **06-10** | **A** | Untrusted extension/MIME warning, manual OS Open only, no automatic execute/preview/malware-free claim | FR-014/019/035/036; AC-D/N/O |

## 2. Preceding alternative designs now rejected without new user approval

- **CL-06-03 A NOT selected:** **do not** prompt for an arbitrary per-file Save location in every FILE_ACCEPT. User preauthorizes Downloads once; per-offer **permission to receive** remains mandatory. If root missing, prompt configure safe Downloads first, then Accept the individual file.
- CL-06-01 B 1GiB not selected; >256MiB rejected; 0-byte valid.
- CL-06-02 B session-scoped auto consent not selected. Never autoaccept a pinned peer.
- CL-06-04 B overwrite after confirmation **not selected**: no replace flow in V1.
- CL-06-05 B resume/retain partial not selected; no automatic or manual resume in this version.
- CL-06-06 B source snapshot not selected; a modified source aborts.
- CL-06-07 B Group deferral **not selected**; Group fanout is required full scope but **gated until 004 actually Verified**, so 006 cannot claim complete while Group disabled.
- CL-06-08 B new data TLS connection not selected; use shared M2 socket.
- CL-06-09 B encrypted transfer archive not selected. Protected user Downloads root *setting* is not an archive of file transfer attempts.
- CL-06-10 B default file type denylist not selected; warn and require manual OS Open rather than automatic execution.

## 3. Constitution Gate 0 review (documentation only)

| Gate | Actual status | Blocking evidence |
| --- | --- | --- |
| **G0.0 User choices** | **DONE 10/10** | Confirmed 9 A and CL-06-03 B |
| **G0.1 FILE_V1 version/codec/crypto** | **CONDITIONAL / NOT SIGNED** | Exact canonical transcript/frame/golden vectors/version need independent review; proposed `protocolVersion=5` is not frozen |
| **G0.2 Win11 Downloads security** | **CONDITIONAL / NOT SIGNED** | Protected local root authorization, symlink/junction/reparse/ACL and atomic no-clobber Windows semantic/security tests missing |
| **G0.3 Streaming and privacy** | **CONDITIONAL / NOT MEASURED** | Binary frame≤65536, shared 16 frames/2MiB M2 queue, RAM-only state, 256MiB stress/EDT yet to test |
| **G0.4 Group 004 signed authority** | **BLOCKED** | Branch 004 18/124 pure domain only, no signed owner relay/real handoff; cannot forge member proof |
| **G0.5 Independent security signoff** | **NOT EVIDENCED** | No attributable independent File protocol/filesystem reviewer has approved implementation |
| **G1 Physical LAN/AC acceptance** | **NOT RUN** | No File sender/receiver code/tests; 2 distinct physical Win11 LAN PCs, manual Accept/SAS and AC-A…P pending |
| **M4** | **NOT VERIFIED** | Both File 006 and Multimedia 007 must Verified, not merely spec complete |

**Constitution I–VII:** LAN-first DESIGN PASS; protocol II CONDITIONAL; bounded III CONDITIONAL; security/privacy IV BLOCKED; test V NOT RUN for File; Live honesty VI DESIGN PASS; spec-driven VII DESIGN PASS. None of these labels is production or security approval.

## 4. Document and workflow completeness

The [plan](../plan.md) traces **8 US, 56 FR, 14 SC, 16 AC** and eight ROADMAP 06.01–08 items. [Data model](../data-model.md) is conceptual RAM-only model + transient staging/final file, [FILE_V1 wire](../contracts/file-v1.md) is a strict **DRAFT**, [quickstart](../quickstart.md) is a test *plan*, not a test run. [160 planned implementation tasks](../tasks.md) now exist (0 completed); Java File transfer code is **not** generated or tested.

**Next:** independent File protocol+Win11 secure filesystem review and Group 004 runtime gap; then execute [160 tasks](../tasks.md) RED→GREEN under honest Gate0/Group blockers. Keep local dirty 003 main worktree untouched.
