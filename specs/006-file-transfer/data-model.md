# Conceptual Data Model — Phase 06 File Transfer (NO persistent transfer DB)

**Feature:** `006-file-transfer` · **Status:** 10/10 user CL confirmed (9 A, 06-03 B), **conceptual only**, no File Java classes/DDL/wire implementation/tests. [Plan](plan.md) · [Spec](spec.md) · [Wire draft](contracts/file-v1.md).

## 1. RAM-only application/domain values

| Type / record | Suggested fields | Invariant |
| --- | --- | --- |
| `TransferId` | UUID random per attempt, sessionNonce UUID/random | Nonzero, unique to attempt, never reused on retry/resume |
| `FileOffer` | senderDeviceId+SPKI verified reference, exact P2P scope or authorized Group ref, originalMessageId/transferId, basename display, size int64, sha256[32], offeredAtUtc, optional untrusted mime | Size **0..268435456**; basename never used as a destination path; profile capability required |
| `VerifiedFileScope` | actual current M2 TLS session, exact two deviceId/SPKI fingerprints, local peer identity, conversationId, capability transcript | User metadata hidden from wrong but TLS-pinned outsider |
| `FilePermission` | offerId, local decision Accept/Reject, scoped receipt nonce, expiry, approved Downloads root reference | Every offer requires its own Accept, distinct from root authorization and inbound TLS connection Accept |
| `TransferState` | stage, transferId, verified pair, expected size/hash, bytesSent, bytesAccepted, bytesVerified, committed bytes, optional bounded errorCode | RAM-only, state transition terminal/idempotent, receiver commit ACK separate from M2 app receipts |
| `TransferProgress` | bytesSent, bytesAccepted, total bytes, local/remote status and last event UTC | 0..size, no 100% sender progress = completed receiver |
| `FileChunk` | transferId/run/session, contiguous 64-bit offset, sequence, actual length, raw bytes buffer | Within reviewed encoded secure frame cap ≤65,536, bounded decode |
| `CommitResult` | transferId, authenticated receiver device, exact byte count+sha256, terminal success/failure and UTC | B can emit success only after safe durable no-clobber final file; sender must validate same scope |
| `GroupRecipientTransfer` | Group id/epoch/revision, verified original member signature, owner current proof, memberId, JOIN/REMOVE cutoff, consent/result | Per-member outcomes; only signed current owner may issue new chunks; Group disabled before 004 Gate |
| `FileSettings` | user-chosen/approved Downloads root, date/revocation permissions | Root authorization is local preference, **not** a grant for each incoming offer, and never network controlled |

**Not stored:** transferId/offer metadata/progress/receipts, sender absolute path, resume offsets, signed capability transcript, file indexes, file bytes in History 005/Group 004 stores. This is **CL-06-09 A RAM-only**. A protected local Downloads settings value may exist when independently reviewed; if not safe, reauthorize root on each launch rather than plaintext persistence.

## 2. Filesystem-only user data / ephemeral staging

| Artifact | Existence | Retention/cleanup |
| --- | --- | --- |
| **Approved Downloads root** | Explicit user selection/authorization (CL-06-03 **B**) | Change/revoke allowed, no remote control; if revoked during transfer, abort writes/commit |
| **Unique staging binary file** | Created only after per-file Accept in same securely resolved Downloads area, non-traversable | Temporary and *never* user-visible final; delete on cancel/fail, guarded orphan cleanup on restart after crash |
| **Completed final file** | After size+SHA256 and durable safe atomic **no-clobber** file creation | User owns and decides lifetime; app may not overwrite or delete as cleanup |
| **Existing user file with colliding name** | Already present before receiver | Unmodified, choose safe distinct basename or fail; never replace |

**Fail-closed filesystem requirement:** Windows junction/reparse/ACL, UNC and ADS, symlink swaps and case-insensitive name collisions require actual Win11 handle-safe tests. Do not assume lexical normalized path or `Files.move(ATOMIC_MOVE)` alone proves no-clobber on all supported filesystems; choose audited API/proof or block commit. Crash cannot always delete orphan instantly; only securely attributable app-owned files may be swept after restart.

## 3. State transition proposal

`SELECTED → PREHASHING → OFFERING → AWAITING_ACCEPT → STREAMING → WAITING_RECEIVER_COMMIT → COMPLETED`

Receiver: `OFFERED → AWAITING_USER → ACCEPTED → RECEIVING → VERIFYING → FSYNCING → NO_CLOBBER_COMMIT → COMPLETED`.

Exceptional: `REJECTED`, `CANCELLED`, `FAILED`, `UNCERTAIN` (sender may be UNCERTAIN when final receiver outcome is lost). No old ACK changes terminal state; no automatic resume on restart. Zero-byte accepted offer skips STREAMING chunks, but must still verify SHA-256 empty, create new zero-byte final file and await authenticated COMMIT_OK.

## 4. Migration/reuse constraints

- Do not extend `model.Attachment` Demo to silently accept empty file in unrelated media features; use separate Live `FileOffer` validation for 0..256 MiB. Multimedia 007 may consume a verified completed-file handle in a future separately approved adapter.
- Do not add SQL/JDBC or History 005 tables for File. Do not persist metadata for progress after restart. No archiving/backup or file preview added during this phase.
- Group 004 proof events are runtime prerequisites, not synthesized from Group pure-domain records. When 004 is not Verified, Group File is disabled regardless of UI mocks.

**This document is design, not a Java implementation, SQL schema, or tested Windows file operation.**
