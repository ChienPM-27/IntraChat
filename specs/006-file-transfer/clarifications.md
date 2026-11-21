# Confirmed Clarifications — Stage 06: Secure LAN File Transfer

**Feature:** `006-file-transfer` · **Date:** 2026-11-21
**Status:** **10/10 USER CONFIRMED — 9 × A and 1 × B (CL-06-03 B)**. Binding user product decisions; alternatives are no longer pending.
**Source:** Explicit user confirmation on 2026-11-21 · [Spec](spec.md) · [Implementation plan](plan.md) · [160 tasks](tasks.md) · [Architecture impact](checklists/architecture-impact-review.md) · [ROADMAP Stage 06](../../ROADMAP.md).

## Binding decisions (canonical)

| ID | User-confirmed answer | Implementation impact |
| --- | --- | --- |
| **CL-06-01** | **CONFIRMED A** — **256 MiB (268435456 bytes) max/file**; **0-byte file accepted** with SHA-256 `e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855`. | Preflight capacity/disk; 0/1/max/max+1 and overflow tests; existing Demo `Attachment.sizeBytes > 0` is not File protocol policy |
| **CL-06-02** | **CONFIRMED A** — explicit per-file **Accept/Reject**, even for pinned peer. Timeout → Reject. | No chunk pre-consent, no remembered auto-Accept; session M2 inbound connection Accept remains separate |
| **CL-06-03** | **CONFIRMED B** — **user-preauthorized Downloads directory** (Windows Downloads or a user-approved alternative); select/change/revoke through user settings. No per-file save dialog. | Offer still requires explicit Accept/Reject; no root permission => do not Accept or write; only safe staging/final within approved root |
| **CL-06-04** | **CONFIRMED A** — **no overwrite**, safe collision-free name or user selects alternative; atomic no-clobber. | Never replace existing user file (even if race/junction); no separately confirmed overwrite mode |
| **CL-06-05** | **CONFIRMED A** — delete partial staging after cancel/fail, **no resume**, manual retry means new transferId and explicit fresh offer. | Orphan partial after forced crash cleaned on next startup only if proven app-owned; cannot promise instantaneous cleanup after power loss |
| **CL-06-06** | **CONFIRMED A** — source changes cause **fail/cancel**, no pre-transfer snapshot. | Compare original manifest digest, stream digest and source identity/size/mtime; detect same-size mutations via hash, receiver verifies own SHA-256 |
| **CL-06-07** | **CONFIRMED A** — **owner-mediated Group fanout per eligible member**, individual Accept/progress/result, **only after Group 004 Verified**. | Signed original source, current owner/epoch, JOIN/REMOVE cutoff, stop new chunk after removal, no member mesh; Group disabled until 004 real gate |
| **CL-06-08** | **CONFIRMED A** — distinct negotiated **FILE_V1** application profile **over existing M2 pinned TLS session**, shared queues/session caps. | Separate strict codec + post-TLS mutual capability; no new socket, no M1 or CHAT_TEXT tunnel; exact app protocol version requires technical signoff |
| **CL-06-09** | **CONFIRMED A** — transfer/progress/receipt metadata **RAM-only**, no persisted transfer history or automatic backup; only user saved completed file remains. | Staging file temporary while active, delete on failure/crash recovery; never archive file bytes or file metadata to History 005 |
| **CL-06-10** | **CONFIRMED A** — generic binary types permitted with visible untrusted MIME/extension warning; **never auto-open, run or unzip**. | User may choose manual OS Open; digest is not malware attestation, no false antivirus guarantee |

## Interactions and invariants

- **CL-06-03 B + CL-06-02 A:** Preauthorized Downloads directory is not per-offer transfer consent. Every inbound file still requires explicit Accept/Reject. If root is revoked/missing/unwritable/reparse-unsafe, disable Accept and do not write content.
- **CL-06-04 A + CL-06-03 B:** Remote filename is display metadata only, never a path. Sanitize Windows reserved names, UNC/ADS/colon, trailing dot/space and Unicode; secure same-volume staging + atomic no-clobber within **approved real root**, robust against junction/symlink race.
- **CL-06-01 A:** 0-byte file carries no content chunk, but does require verified Offer/Accept/end/finalize; empty SHA-256 and durable zero-length final file; exactly 256 MiB allowed, limit+1 denied.
- **CL-06-05 A + CL-06-09 A:** No persisted transfer index or offset; every retry new transferId; staging is a transient binary artifact not a remembered transfer. On unexpected termination, guarded orphan cleanup only removes proven app-created temporary files, never a user file.
- **CL-06-06 A:** Precompute stable source length/hash and rehash transmitted bytes, check OS source identity/metadata, fail if mutated; do not silently send mixed file. Receiver independently validates length/hash.
- **CL-06-07 A + CL-06-08 A:** Group flows are current-owner mediated **using the same authenticated M2 sessions** and bounded queues; original member signer must be independently verified at recipient; owner offline/handoff uncertain → PAUSED, no reissued chunks after REMOVE.
- **CL-06-10 A:** A successful content checksum does not establish that a file is safe to run. Never auto-open/preview/execute; warn and delegate manual OS handling.

## Remaining technical review gates (not product ambiguity)

Independent review **not yet granted** for: (i) exact File V1 version and canonical wire/capability transcript/golden vectors, (ii) Windows Downloads root/ACL/reparse/atomic no-clobber/orphan cleanup, (iii) M2 fair scheduling and bounded binary frames/quotas, (iv) original Group author signatures/current owner provenance, and (v) security/privacy release. **Group 004 remains 18/124 domain-only; M1/M2 physical two-Windows-PC release gate remains open.** No File Java implementation, File unit test or actual transfer acceptance is claimed.

**Next:** approved product choices are reflected in [plan](plan.md) and [draft file wire contract](contracts/file-v1.md). [160-task implementation list](tasks.md) is now prepared (0 done), but implementing Java code and signing security Gate 1 are **not** part of task authoring.
