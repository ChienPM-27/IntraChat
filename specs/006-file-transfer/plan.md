# Implementation Plan — Giai đoạn 06: Secure LAN File Transfer

**Feature/branch:** `006-file-transfer` · **Date:** 2026-11-21 · **Priority:** P2 · **Milestone:** M4 = 006 File **AND** 007 Multimedia
**Status:** **160 IMPLEMENTATION TASKS PLANNED / 0 DONE; 10/10 CL-06 USER CONFIRMED (9 × A, CL-06-03 B); technical Gate 0 CONDITIONAL/BLOCKED, no File code/tests or release acceptance.**
**Sources:** [Spec](spec.md) · [Confirmed decisions](clarifications.md) · [Architecture impact](checklists/architecture-impact-review.md) · [Research/ADRs](research.md) · [Data model](data-model.md) · [FILE_V1 contract draft](contracts/file-v1.md) · [Quickstart](quickstart.md) · [160 RED→GREEN implementation tasks](tasks.md) · [Constitution I–VII](../../.specify/memory/constitution.md) · [ROADMAP 06.01–08](../../ROADMAP.md) · [M2 v2](../003-p2p-messaging/contracts/chat-v2.md) · [Group 004](../004-group-chat/spec.md) · [History 005](../005-chat-history/spec.md).

## 1. Decisions frozen by user

| CL | Confirmed choice | Binding implementation |
| --- | --- | --- |
| **06-01 A** | **≤256 MiB = 268435456 bytes**, zero-byte permitted | Exact bounds 0..268435456, empty file requires empty SHA-256 plus valid offer/consent/finalize |
| **06-02 A** | Per-file explicit Accept/Reject on every incoming offer | Pinned peer is NOT blanket file approval; 0 chunk before Accept, timeout Reject |
| **06-03 B** | **Pre-authorized Downloads directory** | User chooses/authorizes/changes/revokes a Downloads root ahead of receiving; no per-file Save dialog, but **every file still prompts** |
| **06-04 A** | Never overwrite existing files | Safe alternate basename or user chooses another nonexisting name; atomic no-clobber, fail closed under race |
| **06-05 A** | No resume; fault/cancel cleans partial; retry new transferId | Guarded orphan cleanup after crash; no resume across restart and no auto reconnect |
| **06-06 A** | Fail/cancel on source mutation | Prehash manifest, rehash stream + inspect file identity/size/mtime, no mixed-file success/snapshot |
| **06-07 A** | Owner-mediated Group fanout, each member separately, only after verified 004 | Current owner, JOIN/REMOVE, signed origin and per-recipient outcome; Group route disabled until 004 Gate |
| **06-08 A** | FILE_V1 distinct profile **on existing pinned M2 TLS session** | Mutual post-TLS capability, strict separate codec, shared queue/socket; **no new port** |
| **06-09 A** | RAM-only transfer/progress/receipt metadata | No transfer history/History binary export; only completed file durable; temporary partial disk staging allowed only while active |
| **06-10 A** | Allow generic binary, warn on unknown/dangerous type, no auto-open/run | Manual OS Open only; hash is integrity, never a malware safety claim |

**Frozen product policy ≠ approved security implementation.** Numeric frame/chunk/windows/worker limits beyond 256 MiB are **proposals**, not approved. Exact FILE_V1 application protocol version, capability canonicalization, Windows no-clobber API and Group original signature need independent technical review. No File implementation is claimed.

## 2. Baseline, scope and delivery slices

- M1 `protocolVersion=1` is manually connected plaintext **diagnostic only**, never File. M2 v2 = mutual TLS1.3, independently pinned deviceId/SPKI, first-pair SAS and both `PAIR_READY`; no background discovery/reconnection. File must be **only** on this verified M2 socket, not hidden inside `CHAT_TEXT`.
- Current `NetworkConfig` caps **4 shared active sessions**, **16 outgoing frames/peer**, **2 MiB queued bytes/peer**; M2 secure frame **65,536 bytes**. Streaming file must yield to P2P/Group control traffic and remain off Swing EDT.
- Group 004 is **18/124 pure-domain tasks**, no verified signed owner relay/handoff and no Group E2E. Group File remains disabled even after pure-domain prototypes until real 004 Gate 1.
- History 005 is **22/136 pure-domain/ACL tasks**, no verified encrypted persistence or sync. File bytes/metadata **MUST NOT** enter History 005; P2P File need not wait on History 005 runtime.
- `model.Attachment` disallows size 0 and `ui.components.MediaFiles` currently provide local Demo/media helper semantics, not a File TLS transfer. Introduce separate Live File DTO/ports and preserve Demo behavior.
- Suggested vertical slices: (A) typed pure domain state/bounds; (B) FILE_V1 codec and authenticated capability in M2; (C) secure Downloads+SHA256 streaming/atomic no-clobber; (D) Live UI and real 2-peer tests; (E) Group fanout **only after 004 Verified**; (F) physical-PC/security/release evidence. Plan first; tests/code not started.

## 3. Constitution I–VII (Gate 0 design assessment)

| Principle | Status | Required evidence |
| --- | --- | --- |
| I. LAN-first | **DESIGN PASS / physical Gate 1 OPEN** | No cloud/auto reconnect, actual two Windows LAN PCs without Internet not tested |
| II. Protocol-first | **CONDITIONAL** | Final FILE_V1 version/encoding, canonical capability transcript and v2-only downgrade vectors reviewed |
| III. Bounded/nonblocking | **CONDITIONAL** | Shared 4 sessions/16 frames/2MiB queue; File chunk/heap/fairness/EDT benchmark not measured |
| IV. Security/privacy | **BLOCKED** | Independent Win11 Downloads ACL/junction/atomic no-clobber/crash security review and M2 signature/capability review missing |
| V. Test-backed | **CONDITIONAL** | Prior Java21 138 tests are M0–M2/History domain baseline, **0 File tests**, no 2-PC File evidence |
| VI. Honest Live UI | **DESIGN PASS / runtime OPEN** | Per-offer Accept even with approved Downloads, no fake receiver complete/read/auto-open |
| VII. Spec-driven | **DESIGN PASS / implementation OPEN** | 10 CL confirmed; 8 US/56 FR/14 SC/16 AC; **tasks.md planned 0/160**, no File code or File QA |

**Gate 0.0: 10/10 user Clarify DONE.** G0.1 File wire/capability profile freeze **OPEN**. G0.2 Windows FS path/atomic no-clobber security review **OPEN**. G0.3 Group signed original-source/owner 004 runtime **BLOCKED**. G0.4 capacity/timeout fairness measurements **NOT RUN**. G0.5 independent security/privacy signoff **NOT EVIDENCED**. Product confirmation does not sign off these gates.

## 4. Architecture and owned seams

| Layer / planned ports | Responsibilities |
| --- | --- |
| `domain/file/`: FileOffer, FileTransferId, TransferPolicy, TransferStatus, ProgressSnapshot | Pure validated immutable size/name/scope/UTC/domain state; no I/O or Swing |
| `security/file/`: VerifiedFileAuthorization, FileCapabilityBinding, GroupFileEligibility | Exact M2 verified identity/SPKI and protected run/scope, Group owner/roster signed proof, outsider denied before filename/size |
| `protocol/file/`: FileV1Codec, FileControl, FileChunk | Separate typed profile, strict UTF-8 metadata, bounded binary chunk, no Java object serialization |
| `transport/`: FileVerifiedSessionPort, FileFrameScheduler | One shared M2 TLS reader/dispatcher, bounded peer queues; no new TCP listener |
| `application/file/`: FileTransferCoordinator, SendFileService, ReceiveFileService, TransferRegistry | RAM-only attempts, consent, cancel/retry/new transferId, source/receiver final correlation |
| `storage/file/`: ApprovedDownloadsRoot, SourceFileReader, SecureStagingWriter, NoClobberFinalizer, OrphanSweep | Filesystem **only** transient staging and final file, no transfer metadata DB, Windows race/ACL/reparse defenses |
| `ui/file/` and Live adapters | Settings: Downloads authorization; per-file Accept/Reject, warnings, honest byte progress; off-EDT tasks; Demo isolated |
| `application/group/file/` | Later current-owner bounded relay, original source proof, JOIN/REMOVE/handoff, per-recipient outcomes only after 004 verified |

**Dependency:** Swing UI → application+ports → domain; protocol/transport/storage implement ports. Security checks must read **M2 session authenticated identity** not JSON claimed identity. Shared files (`pom.xml`, `TlsChatSession`, `SecureChatCoordinator`, `ChatV2BootstrapCodec`, `MainChatFrame`, `LiveChatAdapter`) require single editor and regression ownership. Preserve dirty worktree `003-p2p-messaging`.

## 5. FILE_V1 over verified M2 — draft negotiation and wire

### 5.1 Transport and mutual capability proof

1. User connects manually via existing M2 IPv4:port; remote user Accepts inbound socket, mutual pinned TLS 1.3 handshake, human SAS for unknown peer, and **both** verified `PAIR_READY` required for the current session. Unknown or changed SPKI → no File.
2. **Optional untrusted pre-TLS** bounded HELLO/ACCEPT token `FILE_V1` contains no metadata/payload. v2-only peers can still use v2 chat but not File. Token alone never authenticates feature.
3. After M2 READY inside TLS, both endpoints mutually exchange and acknowledge **canonical File capability transcript**: exact raw offered capability sets, requestId, both nonces, sessionId, two ordered (deviceId, pinned SPKI) and File profile/version. Stripping/reordering an offer, wrong identity/nonce/session or missing ACK denies File; no silent downgrade/new socket.
4. File codec runs as **separate application profile** in the shared verified TLS dispatcher; proposed profile `protocolVersion=5` / `fileProtocolVersion=1` to avoid M1 v1/M2 v2/004 v3/005 v4 collision. **Version 5 is candidate, NOT security/freeze approval**. Existing `ChatV2Codec` must continue rejecting `FILE_*` if treated as CHAT_V2.
5. Every File offer/control/chunk binds active run/transferId and authenticated M2 hop sender/receiver, pinned SPKIs, derived P2P conversation ID and exact verified session. A pinned outsider C with known A↔B conversationId must receive **zero metadata/count/content** about A↔B.
6. In Group, owner A relay cannot falsely sign as original sender B; signed original author proof and current ownerEpoch/roster JOIN/REMOVE claims must be verified independently from A TLS hop at final recipient. **004 is not implemented**, so Group route remains disabled.

### 5.2 Typed messages (proposal, not frozen contract)

| Stage | Candidate message | Required authorization and result |
| --- | --- | --- |
| Negotiate | `FILE_CAPS_CONFIRM / FILE_CAPS_ACK` | M2 READY + canonical protected transcript both sides; no file info before ready |
| Offer | `FILE_OFFER` | New random transferId, exact scope/session, user-selected readable source, sanitized basename, SHA-256 digest, size 0..268435456 and UTC |
| Decline | `FILE_REJECT` | Explicit Reject or timeout; no data chunks sent; terminal REJECTED |
| Consent | `FILE_ACCEPT` | Receiver local click for **this offer**, healthy user-approved Downloads root, exact transfer/run context; Accept never cached |
| Stream | `FILE_CHUNK / FILE_CHUNK_ACK` | Raw bounded binary, consecutive 64-bit offset/sequence, receiver accepted progress, authenticated same session; ACK is not completion |
| Close | `FILE_END` | Sender original expected SHA-256 + streamed bytes digested, abort if source identity/digest changed |
| Verify+commit | `FILE_COMMIT_OK` | Receiver exact expected bytes and SHA-256, secure staging fsync/close and atomic no-clobber commit succeeded; recipient context proof |
| Fault | `FILE_CANCEL / FILE_ERROR` | Idempotent CANCELLED/FAILED/UNCERTAIN, no automatic reconnect/resume/new socket |

**Special zero-byte flow:** Offer size=0 and fixed empty digest → receiver still explicitly Accepts → zero chunk frames → FILE_END → secure create empty file in Downloads → FILE_COMMIT_OK. Zero byte cannot bypass consent or safe finalization.

**Sender integrity without snapshot (CL-06-06 A):** open validated regular file; before OFFER compute a complete bounded-memory SHA-256 + size/identity/metadata baseline. During stream from stable handle rehash the exact emitted bytes, monitor file identity/size/mtime; before END require stream digest and final metadata to match prehash. Same-size mutation can evade metadata alone, so digest is authoritative. If mutation detected after sending bytes, CANCEL/ERROR and receiver never commits. Receiver always recomputes hash, count and rejects any mismatch; malicious source sender can choose content but cannot forge receiver success.

### 5.3 Validation and frame/resource plan

- Existing secure TLS application frame **≤65,536 bytes**; count envelope/profile/binary header overhead before sending, enforce preallocation cap. **Proposed maximum raw data chunk ≤32 KiB** (engineering target pending wire design and measurement), not a user-confirmed limit. No full 256 MiB Base64 JSON, `Files.readAllBytes` or unbounded lists.
- Strict positive type+field allowlist; UTF-8 decoder REPORT on malformed, duplicate JSON keys/unknown critical fields/trailing tokens forbidden, nesting cap **candidate 32**; validate canonical nonzero UUID, 32-byte SHA-256, nonnegative int64/overflow-safe offset, valid UTF-8 basename and byte limits before disk.
- Sequence/offset must be exactly next expected contiguous offset, reject overlap/gap, late chunk after Cancel/END, changed chunk with repeated ID, wrong peer/SPKI/session/cursor/replayed run. Receiver byte count may equal 0 initially; must never exceed offered size.
- FILE_CHUNK_ACK may acknowledge only bytes B really received/accepted (not necessarily fsynced). Sender UI distinguishes `bytesSent`, `bytesAccepted`, `bytesVerified` and terminal `bytesDurablyCommitted`; neither an ACK nor 100% sent authorizes `COMPLETED`.
- Share **M2** 4 sessions, 16 outbound frames/peer and 2MiB queued/peer; one socket reader/dispatcher and bounded scheduler; live chat/control frames never starved. Proposed at most 1 active streaming file per peer, at most 2 globally **targets, not measured limits**; pin final cap in independent review.
- Proposed offer response 30 seconds, idle progress 20 seconds, overall deadline proportional to 256 MiB and LAN throughput **not frozen**. A fixed 120s maximum may incorrectly reject legitimate 256 MiB on slow LAN; benchmark and choose configurable bounded ceiling with liveness heartbeat/cancel, no infinite hang. Timeouts cannot auto reconnect.

## 6. Win11 Downloads authorization and secure filesystem lifecycle

### 6.1 CL-06-03 B (special case): Downloads root authorization

1. **User local settings:** user chooses or explicitly authorizes a Downloads directory (default offered from Windows Known Folder discovery as a *suggestion*, never silently auto-authorized). It may be their current Downloads or chosen alternative; app records local root choice/permission safely with existing user settings if reviewed. **No per-file Save As dialog.** If user revokes/changing root, new Accept and commits use only newly authorized root.
2. User has to click **Accept on every FILE_OFFER** as distinct act from authorizing Downloads once and from accepting incoming M2 socket. If no safe root configured, disable Accept and prompt settings; Reject/timeout must not create staging.
3. **CL-06-09 A RAM-only transfer metadata:** app may persist only the *user-managed Downloads setting* if needed for future permissions; transferId, sender filename/offer progress, receipt, raw binary, partial offsets, absolute source path and P2P metadata remain RAM-only. Existing protected settings storage must be reviewed; if not available, require reauthorization of Downloads every launch rather than insecure plaintext persistence.
4. Remote basename and MIME are untrusted *display* data. Sanitize Windows reserved device leaf (`CON`/`PRN`/`AUX`/`NUL`/`COM1`), case-insensitive collisions, trailing dot/space, Unicode normalization, `:\\`/ADS, slash/backslash/`..`, UNC, device namespace `\\?\`. Do not follow remote-supplied path even if visible filename contains one. Show original display basename only after escaping control/bidi tricks.
5. Preflight free space and quota for **0..256 MiB**, but preflight cannot guarantee future free space; handle disk full during write. Ensure Downloads is user authorized, owner-writable, real filesystem directory, not redirected to forbidden system/startup/symlink/junction/reparse destination, and stable across open→commit. **Never rely exclusively on lexical normalized `Path.startsWith`**.
6. Unique staging file is **app-owned** within the approved real Downloads root / private subdir on the same volume as final target. Use Windows filesystem-handle and no-follow/reparse protection where available, appropriately restrictive ACL, no leak on partial; if handle-safe atomic no-clobber cannot be proven on supported Win11 filesystem, **fail closed and block release** rather than guess using `Files.move` semantics.

### 6.2 No-clobber finalization and crash truth

- Compute exact offered length and SHA-256 against staging; verify bytes count before close. Flush file contents/metadata (e.g. Java `FileChannel.force(true)` subject to actual Windows semantics), close handle, then commit with an **audited atomic create-no-replace** under rechecked approved root. Explicitly test target appears during race, rename across volumes, ACL denial, locked file, junction swap and collision. No `REPLACE_EXISTING`.
- If name collides, choose safe nonexisting suffix or ask user to select **different filename** (still inside preapproved Downloads). Do not overwrite; verify no race between deciding name and committing.
- Only after verified safe final commit send FILE_COMMIT_OK with exact transferId/session/scope/digest/count. Sender after full TLS write but before receiver terminal ACK shows `UNCERTAIN / WAITING_COMMIT`, **never COMPLETED**.
- Cancel/fail/disconnect must close worker/handle and delete partial (CL-06-05 A); if process crashed, deletion cannot happen immediately. On next startup, guarded sweep may delete **only recognized app-owned orphan partials** within approved root, without following reparse, based on narrowly attributable naming/ownership and review. Do not delete arbitrary `*.part` or every old temp file belonging to user. If a staging file cannot be safely attributed, report/manual cleanup rather than erase. No resume.
- **CL-06-10 A:** allow generic binary downloads with clear warning extension/MIME can be spoofed; never auto-open/execute/decompress; user manually opens through OS only after explicit action. Mark-of-the-Web / Windows SmartScreen integration is security review consideration, not claimed implemented.
- Full path, bytes, PII, SAS, key material, untrusted raw name must not be logged, persisted in History or pushed into source control. SHA-256 checks **integrity not malware safety**.

## 7. Transfer lifecycle/state and truthfulness

**Proposed sender state:** `IDLE → SELECTED → PREHASHING → OFFERING → AWAITING_ACCEPT → STREAMING → WAITING_RECEIVER → COMPLETED`, with terminal `REJECTED/CANCELLED/FAILED/UNCERTAIN`. Source mutation at any later stage ⇒ terminal FAILED/ERROR and protected cancel; an ACK for old session cannot resurrect.

**Proposed receiver state:** `OFFERED → AWAITING_USER → ACCEPTED → RECEIVING → VERIFYING → FSYNCING → NO_CLOBBER_COMMITTING → COMPLETED` with terminal `REJECTED/CANCELLED/FAILED` and invalid/unconfirmed paths. Approval of Downloads root precedes any `ACCEPTED`; per-offer consent is required even for pinned peer. A zero-byte file goes from ACCEPTED directly to VERIFYING after END.

| Event | Sender semantics | Receiver semantics |
| --- | --- | --- |
| FILE_OFFER queued | OFFERING/AWAITING_ACCEPT | prompt verified sender, filename, 0..256MiB, warning; no disk content |
| FILE_ACCEPT | STREAMING after exact verified peer/scope/run | Create safe staging under authorized Downloads and RECEIVING |
| FILE_CHUNK_ACK | byte progress accepted by B, **not complete** | counter advances after bounded validated writes; persistent file durability not yet promised |
| FILE_END | stream hash/identity check, then WAITING_RECEIVER | check exact SHA-256/count, fsync, no-clobber commit |
| FILE_COMMIT_OK | COMPLETED only on authenticated receiver count+digest+run proof | COMPLETED after safe committed final file |
| Reject/Cancel/timeout | REJECTED/CANCELLED/UNCERTAIN as evidence permits | no final file, delete staging safely |
| Duplicate/late terminal frame | no terminal reversal | idempotent ignore/reject, never revive failed attempt |

**Monotonic terminal invariants:** Nothing may mutate COMPLETED into a different recipient or mark an incomplete file as completed. ACK of chat message is separate; File receiver terminal confirmation **does not** mean recipient opened/read/ran file. Transfer state is **RAM only**; crash means no automatic resume, user must retry a new transfer ID and explicit offer.

**Consent and pending queue:** per-file Accept/Reject prompt is local trusted UI with bounded timeout; duplicate offers same transfer/session do not create extra local approvals. If pending remote offer arrives without local Downloads authorization, the receiver may show a setup prompt but **does not send FILE_ACCEPT** until root is explicitly configured and revalidated. Changing/revoking root while transfer is in progress must cancel before any further local write/commit if destination is no longer approved. Multiple file offers need bounded pending approval count (source M2 max 4 pending connections is **not** a file-offer quota).

## 8. Group 004 owner-mediated file fanout (GATED)

**Dependency:** Group 004 must actually implement and pass its signed origin, invitation/membership JOIN/REMOVE, owner relay/handoff and verification. Current baseline **18/124 domain only**; secure relay/wire/UI/handoff and signed provenance **not verified**. Do not mark this phase as complete before that gate.

**Required message/auth topology:** B original member chooses file and explicitly sends a signed original FILE_OFFER to Group owner A over B↔A verified TLS. A verifies origin B/deviceId/SPKI, active signed ownerEpoch, membershipRevision and current roster before offering distinct per-recipient transfers to C/D. Each recipient verifies B's original source signature (not A TLS hop), current owner A authority and their own signed JOIN eligibility, then independently Accepts/Rejects into their preapproved Downloads. Owner relays chunks with bounded RAM/queue (no auto persistent owner file archive) and per-recipient response tracking. Exactly how bytes originate and owner buffers them is a **reviewed 004+006 contract detail**, not yet deployable.

**Membership changes:** no member who JOINed after owner commit of an offer/event gets retroactive file content. After signed durable REMOVE, owner must **stop issuing new** chunks/offer to removed member and purge queued frames; previously delivered bytes cannot be remotely wiped. If owner A hands off to B with signed continuity, A immediately loses issuing rights; new owner takes over **only** after proof and a freshly authorized transfer run, not silent resume (CL-06-05 no-resume). Owner offline/unverified/split brain → Group File PAUSED. No direct member mesh, no fallback to M1.

**Per-recipient outcomes:** C COMPLETED, D REJECTED, E OFFLINE gives partial Group result; the Group send action may be PARTIAL, never universal COMPLETED/READ. Shared M2 4-session max and 5-group-member bound remain; authorization repeated before **each** newly issued content chunk to avoid post-REMOVE leak. Group dual-signed original-source/epoch logic cannot be invented from hop TLS alone.

**P2P independence:** P2P File implementation and tests can proceed behind reviewed File Gate 0 even if Group 004 unavailable. Nevertheless **full scope 006 and AC-06-M remain BLOCKED** until Group 004 is verified, because user chose **CL-06-07 A** rather than defer Group File.

## 9. Security threat model with tests

| Attack / failure | Trust boundary | Control | Mandatory adversarial test / gate |
| --- | --- | --- | --- |
| Attacker strips File token in untrusted preface | Clear bootstrap → protected File enable | Mutual protected canonical transcript after M2 PAIR_READY, no downgrade | Mutated capability sets/nonces, v2-only peer, wrong TLS session; Gate protocol reviewer |
| Pinned outsider C knows A↔B ID | Verified TLS peer → private conversation grant | Exact two independently verified deviceId/SPKI, scope+transferId+session before offer metadata | C gets **zero** name/size/count/content (AC-F) |
| Unverified file metadata/chunk on M1/v2/v3/v4 | Old dispatcher → new profile | Strict positive per-profile codec, no CHAT_TEXT tunneling or extra socket | M1 raw/v2 CHAT_TEXT/004 v3/005 v4 inject FILE_* (AC-J) |
| Remote filename or encoded path escapes Downloads | Network string → Windows real file handle | Filename normalization/sanitization, approved root, no-follow/junction/ACL, same-volume staging | UNC/ADS/`\\?\`, device names, symlink/junction swap (AC-D/G) |
| Existing file overwritten during commit | Untrusted peer basename → final path | Separate staging + atomic Win11 no-clobber and collision name | Race target create/lock/no-clobber; OS reviewer must verify |
| Crash leaves partial disguised final | Transient staging → user-visible Downloads | Final only after SHA-256/size/fsync; cleanup app-owned orphan; no resume | kill process before/after commit, restart (AC-C/L) |
| Peer forges COMMIT_OK or sends after cancel | Peer TLS hop → transfer terminal state | Run/session/scope/digest/count proof and terminal idempotence | Stale/delayed/wrong peer/changed digest outcome (AC-H/L) |
| Source mutates in place | Local chosen path → manifest/stream | prehash, stream hash, file identity/size/mtime, receiver SHA-256 | Same-length mutation while sending (AC-H) |
| Memory/EDT starvation from large binary | TLS frames/buffer → heap/GUI | 65,536 frame cap, bounded chunk/window, fairness, workers off EDT | 256 MiB, slow receiver, other chat active (AC-K) |
| Group old owner/removed member receives bytes | Group owner/roster → member authorization | Signed original author + current owner epoch, recheck every chunk, purge queues | JOIN late/REMOVE during queued frames, handoff (AC-M) |
| Saved file auto-executes | Untrusted binary → OS | Warning, **never auto-open/run/decompress**, user manual action | Spoofed MIME/double extension/scripting file (AC-N/O) |
| Transfer metadata leaks into History/log | RAM/FS boundaries → permanent records | no transfer DB/history, redact logs, only final file durable | Inspect paths/logs/Demo/History independence (AC-N) |

**Review requirements:** written Windows filesystem threat assessment including ACL/TOCTOU/reparse/file IDs; independently reviewed File protocol canonical capability/wire and signer proof; provenance of any new dependency/native bindings; privacy and multi-PC network test. Product choice confirmation cannot replace a reviewer.

## 10. Test-first RED→GREEN implementation schedule

The [160-task implementation list](tasks.md) now exists; **0/160 tasks are implemented or checked**. This section is the RED→GREEN implementation strategy only, not a record of tests executed.

| Stream | RED tests (write and run first) | GREEN implementation and exit |
| --- | --- | --- |
| **S0 — Domain** | FileOfferBoundsTest, ZeroByteFileTest, TransferTerminalReducerTest, FileConversationScopeTest, OfferConsentTest | Immutable typed state with 0..256 MiB, per-file consent, terminal monotonic; pure JUnit |
| **S1 — Identity/capability** | FileCapabilityTranscriptTest, FileProfileIsolationTest, FileP2PAuthorizationTest, FileOutsiderNoLeakTest | Reviewed canonical protected FILE_V1 negotiation after M2 READY, no pre-TLS leak, strict v1–v4 compatibility |
| **S2 — Wire and resource limits** | FileV1CodecTest, FileChunkBoundaryTest, FileFrameFuzzTest, FileBackpressureTest | Binary chunk bounded within 65,536 secure frame, strict session/UUID/offset, shared 4 sessions/16 frames/2MiB queue |
| **S3 — Downloads/Windows** | ApprovedDownloadsRootTest, DownloadsRevocationTest, WindowsPathEscapeTest, JunctionSwapTest, NoClobberRaceTest | Only root approved once and per-file Accept; safe staging/finalize; **independent Win11 filesystem security review** |
| **S4 — Integrity** | FileSha256StreamingTest, ZeroLengthCommitTest, SourceMutationTest, FileIntegrityFaultTest | Bounded prehash/stream hash, receiver hash/count, no partial→COMPLETED, source-change fail |
| **S5 — Recovery and UI** | FileCancelCleanupTest, FileCrashOrphanTest, FileNoResumeTest, LiveFileProgressTest, FileNoAutoOpenTest, DemoIsolationTest | RAM-only attempts, no resume, safe orphan sweep, truthful UI, no auto-open/History archive |
| **S6 — Verified P2P E2E** | FileTwoPeerTlsTest, FileLiveConcurrentChatTest, FileOutsiderTest, FileM1DowngradeTest | Real two-JVM pinned TLS and user Accept/SAS, byte-perfect file and negative/failure regressions |
| **S7 — Group integration** | GroupOriginalSignerFileTest, GroupJoinRemoveFileTest, GroupOwnerHandoffFileTest, GroupRecipientPartialTest | **BLOCKED until 004 Verified**; owner-mediated per-recipient signed fanout, no post-REMOVE new chunks |
| **S8 — Release** | AC-06-A…P + two separate physical Win11 PCs, independent review, Constitution I–VII | 006 VERIFIED only with all mandatory gates; M4 only if **006 AND 007** verified |

**RED evidence:** actual Java21 compiler/test failure before implementation, with exact command/test class/exit, not invented sample. **GREEN evidence:** focused JUnit then full `.\mvnw.cmd -o test` and `.\mvnw.cmd -o package` with enabled tests; record SHA/JDK/OS/peer/process counts and real exit. Full Windows LAN physical acceptance cannot be replaced by localhost JVM tests. Never modify tests just to force green or mark a release check without evidence.

## 11. FR/US/AC and ROADMAP trace

| Workstream | Requirement coverage | Independent acceptance |
| --- | --- | --- |
| US1 Offer and sender source | FR-06-001…013, 030, 042 | AC-A/E/I/H/J |
| US2 Receiver consent + Downloads | FR-06-014…019, 032, 044…045 | AC-D/E/G/L |
| US3 Bounded streaming/UI progress | FR-06-020…029, 053…054 | AC-A/B/K/N |
| US4 Whole-file verified finalization | FR-06-030…036 | AC-A/H/I/L |
| US5 Cancel/no-resume/RAM-only | FR-06-037…043, 046…047 | AC-B/C/L/N |
| US6 Malicious filename/path/injection | FR-06-007/017…019/044…048 | AC-D/F/G/J/O |
| US7 Group owner-mediated fanout | FR-06-049…052 | AC-M/P; Group 004 gate |
| US8 Live + stage 007 binary seam | FR-06-053…056 | AC-N/P |
| ROADMAP `06.01–06.08` | All eight ROADMAP items, no implementation checkboxes advanced merely for docs | AC-06-A…P; [spec matrix](spec.md) |

## 12. Deliverables, release rules and next actions

**Documentation produced in this update:** confirmed `clarifications.md`; CL-aligned `spec.md`; `plan.md` (this document); supporting [architecture research](research.md), [conceptual model](data-model.md), [draft FILE_V1 wire](contracts/file-v1.md), [test plan](quickstart.md), and [impact review](checklists/architecture-impact-review.md); checklists/ROADMAP status updated. **160 tasks are now authored in [tasks.md](tasks.md) (0 completed); no Java File code or File tests were created/run by task authoring.**

**Gate 0 exit:** product CL now DONE; independent protocol version/handshake signed transcript review, Win11 safe root/no-clobber filesystem review and exact resource budgeting must be reviewed before production codec/disk/network changes. Group 004 missing real runtime remains a separate external blocker. No claim of approved native Win32 library, successful atomic rename on Windows, measured capacity or independent reviewer.

**Gate 1 exit:** real Java21 verified File E2E P2P/Group, full AC-06-A…P; ≥2 physical Windows 11 PCs LAN working without Internet, human inbound Accept and SAS, anti-traversal/ACL/symlink/junction/changed-source/crash/slow peer/adversarial tests and independent security/privacy signoff. No false READ/ACK, no Group 004 owner bypass.

**Milestone rule:** Stage 006 File NOT VERIFIED until **all mandatory acceptance including Group selected at CL-06-07 A** pass; M4 NOT VERIFIED until both Stage **006 AND 007** pass. Current plan and user choices alone meet **neither**. The [160-task RED→GREEN breakdown](tasks.md) is prepared with explicit BLOCKED technical Gate 0 dependencies; executing production wire/Win11 filesystem tasks awaits actual independent approvals.
