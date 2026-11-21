# Clarification Candidates — Stage 06: Secure LAN File Transfer

**Feature:** `006-file-transfer` · **Date:** 2026-11-21
**Status:** **10/10 PENDING USER CONFIRMATION**. These **A/B are recommendations for review**, **NOT** user decisions. They may be selected/customized before an implementation plan is approved. Security constraints from Constitution, M2 verified TLS, 004 signed Group ACL and Windows filesystem safety are binding regardless of choice.

[Spec and context](spec.md) · [Requirements checklist](checklists/requirements.md) · [ROADMAP 06.01–08](../../ROADMAP.md).

## Questions, suggested answers and architecture impact

### CL-06-01 — Size cap / zero-byte file

- **A (recommended):** Max single file **256 MiB**; **allow 0-byte files** with expected empty SHA-256. Sender/receiver validate cap, disk/quota before transfer. Existing FE `Attachment.sizeBytes > 0` is not authoritative for File V1.
- **B:** Max single file **1 GiB**; also allow 0-byte file, but requires disk/resource/stress test and concurrency capacity review.
- **Security invariant:** nonnegative int64, bounded chunk/bytes, reject >limit, no whole-file heap. An alternative custom limit must be explicit and tested on Win11.
- **Impact:** FR-06-011…013/020…022; AC-A/I/K.

### CL-06-02 — Incoming consent

- **A (recommended):** **Explicit Accept/Reject for every file offer**, including pinned peer; timeout rejects.
- **B:** Session-scoped “Allow files from this verified peer” until disconnect, with immediate cancel/individual Reject; requires tighter prompts and abuse controls.
- **Security invariant:** authorization by verified deviceId+SPKI, exact transferId/scope, receiver approval before *any content chunk*; no silent default auto-accept.
- **Impact:** FR-06-002/014/015/025; AC-E/F.

### CL-06-03 — Safe receiving directory

- **A (recommended):** Receiver **chooses target location per accepted transfer**; staging under same approved directory and safe finalization.
- **B:** Pre-approved user-specific Downloads folder for each future approved offer; filename/path must still be validated and each file must be accepted according to CL-06-02.
- **Security invariant:** remote path is never authoritative; no traversal, junction/reparse escape or auto-save to startup/system directories.
- **Impact:** FR-06-016…018/032/044/045; AC-D/G/L.

### CL-06-04 — Filename collision / overwrite

- **A (recommended):** **Never overwrite existing file**, select collision-safe new basename or ask user to pick. Atomic no-clobber final move.
- **B:** Allow replacement **only after separate explicit confirmation** and rollback/no data-loss guarantee, including Windows lock/race test.
- **Security invariant:** never overwrite because the peer picked an existing name; no TOCTOU/symlink escape.
- **Impact:** FR-06-017/018/032/044; AC-G/L.

### CL-06-05 — Partial data and resume policy

- **A (recommended):** V1 **no persistent resume**; cancel/fault deletes staging partial after safe cleanup, retry is a fresh user-triggered transferId/offer.
- **B:** Manual authenticated resume after interruption with trusted offset/digest/run binding and protected TTL partial storage; more disk/privacy and protocol complexity.
- **Security invariant:** never auto-reconnect/start transfer or accept self-claimed offset; partial never shown as complete.
- **Impact:** FR-06-037…043; AC-B/C/L.

### CL-06-06 — Mutating source while sending

- **A (recommended):** **Fail/cancel** when source size/identity changes or final digest disagrees; explain that file source changed.
- **B:** Create a consented private source snapshot before transfer, subject to quota/protected temp cleanup/privacy review.
- **Security invariant:** receiver still checks exact size/hash; no mixed-version successful commit.
- **Impact:** FR-06-011/030/042; AC-H/L.

### CL-06-07 — Group File topology and timing

- **A (recommended):** Implement Group owner-mediated fanout **only after 004 real runtime verified**, per-recipient Accept/Reject/progress, signed original origin proof, JOIN/REMOVE boundaries, partial Group outcome.
- **B:** **Defer Group File** explicitly to a later approved stage; release P2P File only, and mark ROADMAP 06.08 + Group acceptance deferred rather than “PASS”.
- **Security invariant:** no member mesh/owner spoof, no pre-JOIN content or new post-REMOVE chunks; owner offline PAUSED. Never claim 004 ready from pure-domain tests.
- **Impact:** FR-06-005/049…052; US7, AC-M/P.

### CL-06-08 — File transport profile / session architecture

- **A (recommended):** Negotiated strict **FILE_V1 profile on the existing mutual pinned M2 TLS session**, separate codec; shared session registry/quota/backpressure. Actual `protocolVersion` number to be assigned by reviewed plan, **not assumed in spec**.
- **B:** Separately approved TLS data socket bound to existing verified peers with explicit inbound consent/port/cert/session quota and independent security review.
- **Security invariant:** never M1 plaintext or overloaded `CHAT_TEXT` v2. Pre-TLS capability claim must be authenticated in TLS after `PAIR_READY`, no downgrade.
- **Impact:** FR-06-001…009/020…029; AC-J/K/O.

### CL-06-09 — Transfer metadata after restart

- **A (recommended):** Transfer state/offer/chunk metadata **RAM-only**; final saved file remains user-selected; no automatic History 005 archive of files or paths.
- **B:** User-consented, privacy-reviewed encrypted minimal metadata index for transfer listing/recovery (not file bytes), with DPAPI/ACL/retention/corruption tests.
- **Security invariant:** no automatic file bytes into History 005, no logging private paths/keys/SAS, no speculative resumable upload.
- **Impact:** FR-06-040/041/046/047; AC-C/L/N.

### CL-06-10 — Potentially dangerous file type handling

- **A (recommended):** Generic File transfer supports unknown binary types but **never auto-opens/executes**; clear Windows extension/MIME untrusted warning and manual Open decision.
- **B:** Denylist high-risk executable/script formats by default, override only per-file explicit user consent; requires defined/maintained policy/test.
- **Security invariant:** no claim of antivirus/malware safety from extension/MIME; do not unzip archives; preserve Windows safeguards/Mark-of-the-Web review as part of technical plan if suitable.
- **Impact:** FR-06-014/019/035/036; AC-D/N/O.

## Fixed behavior independent of A/B

- SHA-256 + byte-exact comparison, receiver-side safe durable finalization, fail closed on bad source/digest/size, no false COMPLETED.
- Manual M2 human-approved connection, pinned verified device identities and post-TLS mutually authenticated File capability, no raw plaintext v1/CHAT_TEXT tunneling.
- Filename/path is untrusted; reject Windows path traversal, ADS, junction/reparse/symlink escape and unauthorized overwrite.
- Streaming with bounded buffer/queue, non-blocking Swing EDT, explicit cancel and accurate progress.
- Group owner authority and temporal JOIN/REMOVE restrictions are inherited from 004; cannot bypass by choosing different Group fanout.
- 006 and 007 both required for M4, and tests on physical two-PC LAN/security review are release gates.

## Review / next state

Current selections remain **PENDING**, including the recommended A choices. The user should confirm A/B/custom (ideally all ten together). Only after user confirmation should a technical `plan.md` freeze exact frame/chunk limits, protocol version and signed control schema, filesystem hardening and file-size defaults, followed by `tasks.md` and Java21 RED→GREEN implementation.

**No File code, Java21 tests, 2-PC transfer experiments or independent security approval has been executed during this spec-authoring task.**
