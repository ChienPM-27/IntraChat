# Draft Wire Contract — FILE_V1 over verified M2 TLS (NOT FROZEN)

**Contract ID:** `INTRACHAT-FILE-V1` · **Date:** 2026-11-21
**Status:** **DESIGN DRAFT / NOT IMPLEMENTED / NOT independently security-reviewed**. [Plan](../plan.md) · [Spec](../spec.md) · [CLs](../clarifications.md). Wire changes require frozen canonical/golden vectors; do not claim interoperability from this text.

## 1. Existing protocol preservation / no downgrade

- M1 `protocolVersion=1` plaintext **diagnostics-only**, rejects FILE. M2 `CHAT_V2` `protocolVersion=2` strict CHAT-only protected codec, rejects FILE. Group draft `protocolVersion=3` rejects File. History draft `protocolVersion=4` rejects File. FILE protocol needs a distinct **negotiated** protected application route on the **same** TLS socket.
- **Suggested** `protocolVersion=5` / `fileProtocolVersion=1`, pending independent version/profile review. Never silently modify M2 v2 enum to carry File type inside CHAT_TEXT. The protocol value **5 is proposal, not user approval or assigned wire standard**.
- Raw initial M2 HELLO/ACCEPT may advertise optional `FILE_V1` with no basename/length/content or private data. This raw preface **is untrusted**. After verified TLS/pin/SAS and both `PAIR_READY`, each endpoint must send authenticated File capability confirm and ack binding **exact** offered capabilities, both nonces, requestId, sessionId and two verified deviceId/SPKI tuples. Until both verified: FILE disabled and user metadata never transmitted. Absent/mismatched capability: optional File unsupported, v2 chat unaffected.
- Never make a separate file TCP listener, background connection or unauthenticated fallback. Always obey shared M2 max 4 sessions, 16 frames/peer, 2 MiB peer queued bytes and secure frame limit **65,536 bytes**. Use one authenticated session frame dispatcher.

## 2. Session binding and generic message envelope

Each file profile message **proposed** fields: `protocolVersion`, `fileProtocolVersion`, `messageId` (UUID distinct from transfer ID), `type` (strict enum), `sessionId` (current authenticated M2), `senderDeviceId` (actual verified TLS peer, not self-claim), `destinationDeviceId` (local verified endpoint), `conversationId` (derived exact two peer/SPKI scope), `transferId` (new cryptographically random UUID per attempt), `attemptNonce` (random replay context), `sentAtUtc` (for display, not auth) and typed `payload`. Authenticated Group extension must add current ownerEpoch/membershipRevision/owner run/signed original member proofs, **not** merely change conversationId to groupId.

**Actual identity source:** M2 TLS pinned authenticated peer identity, not unverified JSON fields. P2P only the two independently pinned device tuples. A TLS-pinned third C knowing A↔B scope must get zero offer existence/filename/size/count/chunk metadata. Session/nonce/transfer/run and receiver consent must match each frame.

**Validation:** strict canonical UUID, fixed positive version, ASCII allowlist for type; all integer lengths/offsets nonnegative 64-bit with overflow-safe addition; strict UTF-8 decoder REPORT, no trailing tokens/duplicate fields/unknown critical fields/Java object serialization. Proposed JSON max nesting depth 32, subject to test/freeze. Filename is bounded sanitized basename **for display only**, not target path; MIME/extension untrusted. SHA-256 as exactly 32 bytes encoded/canonical per final contract. Timestamp strict UTC ISO-8601.

## 3. Message types, payloads and user-consent gates

| FILE_V1 type | Proposed payload | Precondition and effect |
| --- | --- | --- |
| `FILE_CAPS_CONFIRM` | supported profile/version, canonical offer transcript digest, nonce/context | Only post verified M2 PAIR_READY; mutual confirm |
| `FILE_CAPS_ACK` | same digest/context+explicit acceptance of capability | Both sides mutually agree; no file data before complete |
| `FILE_OFFER` | new transferId, name display, size int64 **0..268435456**, sha256 full file, proposed MIME, source-sent time | User chose regular source and prehashed; only exact P2P/Group eligible session |
| `FILE_REJECT` | reasonCode, correlation, no raw remote payload | Receiver explicit Reject or timeout; **no chunks**, no final file |
| `FILE_ACCEPT` | transferId, receiver active session nonce, accepted size/hash/scope | User **explicitly Accepts this single offer**; Downloads root already preauthorized and safe |
| `FILE_CHUNK` | absolute offset int64, sequence int64, length, bounded raw binary | Only after accepted; contiguous write; must not overrun declared size, even on 0 file |
| `FILE_CHUNK_ACK` | contiguous receivedAcceptedBytes, transferId/run context | Indicates B accepted bytes, **not** fsync/file COMPLETED or M2 app ACK |
| `FILE_END` | expected count, sender stream digest, correlation | No more chunks, verify source unchanged + receiver exact count/hash |
| `FILE_COMMIT_OK` | transferId/session/scope, committed total bytes, sha256, outcome timestamp | **Only B** after SHA-256+length, durable staging close/fsync and safe atomic no-clobber final commit |
| `FILE_CANCEL` | reasonCode and correlation | User/fault cancel; terminal idempotent, clean partial |
| `FILE_ERROR` | bounded errorCode + optional relatedMessageId | Generic fail-safe error, no private path/name/bytes echoed |

**Empty file protocol:** expected size=0, digest SHA-256(empty) `e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855`; FILE_OFFER→explicit FILE_ACCEPT→zero FILE_CHUNK→FILE_END→safe zero-byte final commit→FILE_COMMIT_OK. Empty file must not bypass consent or no-clobber rule.

**Receiver directory policy:** CL-06-03 **B**, user has explicitly authorized a Downloads root **before** accepting files, not on each offer; every offer still individually approved per CL-06-02 A. Remote file name never selects disk path.

## 4. Binary frame design and resource caps

**Candidate encoding (pending freeze):** existing 4-byte length-delimited M2 secure TLS application frame; File-specific typed header (strict encoded metadata with profile tag/length and exact run IDs) followed by **raw binary bytes** for CHUNK. Control frames may be strict bounded JSON; no embedding full file in Base64. Binary file frame must be distinguishable from v2 CHAT_V2, v3 GROUP_V1, v4 HISTORY_V1, never dispatched to another codec. Byte order, field lengths, header exact canonical bytes, checksummed payload/proof and how encrypted frame lengths are accounted are **OPEN** and require golden test vectors.

- M2 max secure frame: **65,536 encoded bytes**. **Engineering candidate:** raw data ≤32,768 bytes per FILE_CHUNK, after header/auth overhead and actual framed-byte check. This is **not frozen** or measured.
- Same M2 shared 4 sessions/16 outbound frames/2MiB queued bytes per peer; proposed one active File stream per peer with a bounded worker, subject to performance review.
- No whole-file read/mmap/Base64 huge buffers or unlimited pending offers. No file/disk/network/crypto on Swing EDT.
- On chunk: if mismatched authenticated peer/session/run/transfer ID, negative/overflow offset, gap/overlap, repeated with altered bytes, beyond offered length or after termination → reject/cancel. ACK only accepted offset, never final success.

## 5. Consent, integrity, finalization, no-resume

- Sender checks regular readable source, **max 256MiB**, full SHA-256 BEFORE OFFER and recomputes while streaming; stop/fail on source identity, size, mtime or digest mutation. No snapshot (CL-06-06 A).
- Receiver sends per-file Accept **only** after active user click + approved safe Downloads root. Stage unique file in same approved real root; verify exact bytes and full SHA-256; apply FS handle/reparse checks and OS-reviewed **atomic no-clobber**, no overwrite. Only then send COMMIT_OK. If no proven safe finalize, FAIL; never create an apparently completed corrupt file.
- Cancel/disconnect/crash/timeout: terminal state, close/delete staging on live failure, and guarded app-owned orphan cleanup after restart; no resume and no metadata/offset index persists (CL-06-05 A, CL-06-09 A). A manual retry is a **fresh transferId** and separate explicit consent.
- No automatic open, execute, preview or decompress. Warn about unsafe extensions/MIME before opening externally. Successful SHA-256 does not provide malware protection (CL-06-10 A).
- Copy completion and File ACK **MUST NOT** fabricate M2 `DELIVERY_ACK` or `READ_RECEIPT`, Group viewer READ or History 005 durable events.

## 6. Conditional Group extension

Group file profile **disabled until 004 actually Verified**. Only owner-mediated B→A→C; B's original author proof must be independently verified by C (hop A TLS not proof), A's current ownerEpoch/roster/handoff independently checked. Member added after JOIN commit cannot receive pre-JOIN file; after REMOVE owner sends **zero new** FILE offer/data even if queued. Each member individually Accepts into their own Downloads root; C COMPLETE, D REJECT, E OFFLINE = PARTIAL Group, not all-complete/READ. Owner handoff ambiguous or offline → PAUSED; no member mesh/extra socket/auto resume.

## 7. Draft review/test vectors required before freeze

- Same preface token both sides → mutual protected readiness; stripped/altered capabilities, v2-only fallback (chat remains, FILE off), unknown v5, wrong SPKI/session → no File.
- Positive and negative canonical envelope/type encoding, UUID/UTF-8/int64 bounds; strict frame at 65,536/65,537 and proposed chunk 32KiB; replayed/altered chunk hash/offset.
- Empty SHA-256 above; 1-byte, 268435456 and 268435457 lengths; receiver SHA-256 mismatch and changed source.
- Windows path adversarial cases (UNC, `\\?\`, ADS, junction, symlink, reserved device name), target collision concurrent process and atomic no-clobber, crash after staging fsync.
- User preauthorizes Downloads once but individually rejects 3rd offer; no stale root/auto Accept. Outsider C receives 0 metadata/content. Group 004 owner/handoff/JOIN/REMOVE only after runtime verified.

**Approval state:** 10/10 **product** decisions confirmed, **protocol binary/crypto/OS technical freeze and independent security reviewer NOT YET APPROVED**. No File source/wire or test was implemented/executed in this drafting step.
