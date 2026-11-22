# Data model — 004 Group Chat Live (design only)

**Date:** 2026-11-22 · **Status:** PROPOSED / NOT IMPLEMENTED
**Sources:** [Spec](spec.md) · [Plan](plan.md) · [GROUP_V1 contract](contracts/group-v1.md) · [research](research.md).

## 1. Identity boundary

A Group has a random nonzero UUID separate from P2P conversationId, exactly **one owner** (verified creator device ID + pinned 32-byte SPKI hash), and **2–5 unique members** counting self. Nickname, UI title, IPv4 or self-claimed UUID never grants membership. TLS CONNECTED does not imply verified Group READY. Owner grants and signed roster must be independently validated after TLS READY and negotiated Group capability.

## 2. Typed records (proposed code, not existing)

| Entity | Required fields/invariants | Storage |
| --- | --- | --- |
| `GroupIdentity` | groupId, title NFC 1–60 Unicode codepoints / 240 UTF-8 bytes, ownerDeviceId, ownerFingerprint SHA256 32 bytes | Protected |
| `GroupMember` | nonzero deviceId, fingerprint 32B, OWNER or MEMBER role, active/removed; unique both ID and pin | Signed roster, protected |
| `GroupRoster` | owner ID+pin, groupId, ownerSignature ECDSA, revision N≥1, previousHash32, rosterHash32, commitEventId, UTC, exact sorted members (2–5, one OWNER) | Signed bytes encrypted by DPAPI |
| `GroupInvitation` | inviteId, exact verified invitee, groupId/owner, expiry UTC, proposal digest, PENDING/ACCEPTED/REJECTED/EXPIRED/CANCELED | RAM-only until commit; invitation itself grants no rights |
| `GroupRosterEvent` | base revision, next revision N+1, author verified owner, immutable updated members, signed canonical bytes | Event RAM, final committed roster durable |
| `GroupSendGrant` | groupId, messageId, senderId, sorted recipients, roster revision/hash, nonce16, issued/expires time, owner signature | RAM-only, single message and short lifetime |
| `GroupTextMessage` | messageId, groupId, sender verified identity, recipient exact peer, revision, UTC timestamp, Unicode text | RAM-only |
| `GroupRecipientAttempt` | groupId/messageId/recipientId/sessionId/revision, state and deadline | RAM-only |
| `GroupDeliveryAck` | original message, group, authenticated recipient, session/revision, acceptedAtUTC | RAM-only |
| `GroupReadReceipt` | original message, group, authenticated viewer, session/revision, readEventId, visibleAtUTC | RAM-only |
| `GroupUiSnapshot` | owner, member list, revision, freshness/connection state, per-recipient attempts; no key material | RAM-only display DTO |

**Immutability:** Copy byte arrays on boundary; `List.copyOf` for members, forbid duplicate IDs/SPKI and null/zero IDs. Never expose raw `PrivateKey` or DPAPI decrypted buffers to UI. Revoked or changed key always needs revalidation.

## 3. Protected metadata storage shape

Proposed file: `%LOCALAPPDATA%\IntraChat\security\groups.dpapi` in active Windows account, distinct from M2 identity and pins stores. New `security/ProtectedGroupStore` should use existing package-private `ProtectedLocalFile` safely (Windows CurrentUser DPAPI, owner-only ACL on temp file, atomic move), not export raw decryption outside security.

Versioned logical fields, in schema-validated order:

1. Magic `INTRACHAT_GROUPS_V1`, schemaVersion=1 and groupCount=0..16; group records sorted by raw UUID.
2. Per group: groupId, title, ownerDeviceId, owner 32B pin, committedRevision (1..Long.MAX_VALUE), previousRosterHash32, canonicalRoster bytes, owner DER ECDSA signature, committedHash32, optional sync timestamp.
3. No pending invite, grant, chat text, draft, attempt, delivery/read receipt, SAS, private key or offline outbox in this schema.
4. File ciphertext ≤1 MiB; reject unknown schema/role/duplicate group/member/owner, 6th member, invalid signature/hash and mismatched owner pin before publishing data.
5. Newly absent store → empty groups. Existing corrupt/wrong-user/ACL/decryption failure → **BLOCKED**, never unprotected fallback, silent reset or restore as trusted.
6. For owner commit: verify → sign → write DPAPI temp file → atomic replace → publish roster to peers. For member apply: verify pinned owner signature + strict hash chain + N+1 → atomic persist → ACK; on I/O failure keep prior committed state.
7. Loaded metadata always **STALE** until live verified owner/peer sessions and owner-authorized current revision are reestablished. Even a previously valid encrypted file from backup may be rolled back: DPAPI and a revision counter in the same file do NOT prevent rollback.

**Retention:** Group metadata persists until explicit local delete/leave or manually approved recovery. Content never persists or syncs before Spec 005. Removal cannot erase content already copied by another device.

## 4. State transitions

| Machine | Valid transitions | Fail-closed rule |
| --- | --- | --- |
| Connection | DISCONNECTED → CONNECTING → TLS_READY → GROUP_CAP_READY or FAILED/CLOSED | GROUP_CAP_READY requires mutual peer pin/SAS/consent and bilateral Group profile |
| Invite | PENDING → ACCEPTED/REJECTED/EXPIRED/CANCELED | ACCEPTED alone is NOT ACTIVE member |
| Membership | INVITED → OWNER_COMMIT_PENDING → ACTIVE → LEAVE_PENDING/REMOVED | Exactly one owner signs each revision, member may request own leave |
| Roster | N → owner-signed N+1 with correct previous hash; reconnect/restart → STALE | Reject stale/reordered/forged, do not skip revisions silently |
| Group send | DRAFT → WAIT_OWNER_GRANT → GRANTED → SENDING | Owner offline/new ticket unavailable → PAUSED, no Group packet |
| Recipient attempt | SENDING → LOCAL_SENT → APP_ACKNOWLEDGED → READ, or FAILED/UNCERTAIN | Wrong recipient/group/message/session cannot change status |
| Persistent metadata | ABSENT → EMPTY; VALID → LOADED_STALE; CORRUPT/ACCESS_DENIED → BLOCKED | Never use disk alone to authenticate Group |

**Receipt key:** tuple `(groupId, messageId, originalSenderId, recipientId, sessionId, revision)`. READ is only recipient application's real foreground viewport display event; it does not prove human attention. A TCP write is not an application ACK.

**Owner-in-flight caveat:** Owner signs per-message grant before sender transmits directly. Grants issued before a removal can still be in flight after commit; recipients with newer roster reject old revision, but instant retroactive erasure cannot be guaranteed on a partitioned LAN. Review at Gate 0.

## 5. Capacity and validation matrix

| Limit | Plan target, not verified | Test |
| --- | --- | --- |
| Members/group | 2–5 total incl local; sender up to 4 others | 6th member and duplicate ID/key rejected |
| Sessions | 4 verified remote TLS links total across Group/P2P | 5th remote connection capacity failure |
| Groups | ≤8 active, ≤16 metadata stored | Group directory admission tests |
| Pending invites | 4/group, 8/process | Flood, timeout and cancel |
| Text | ≤8,000 codepoints and ≤32,768 UTF-8 bytes | Vietnamese/emoji/invalid surrogate |
| Group frame | ≤65,536 serialized bytes | Oversized/invalid UTF8/strict JSON |
| In-RAM transcript | ≤500 messages/2 MiB each group and ≤8 MiB total | Group and P2P combined load |
| Pending receipts | ≤128 recipient attempts/process | Partial fanout and cleanup |
| Dedup | ≤1,024 IDs/group plus global cap | Duplicate and replay |
| DPAPI metadata | ≤1 MiB encrypted file | corrupt/other Windows user/backup rollback |

## 6. Class mapping and test handoff

**New planned implementation:** `vn/intrachat/domain/{GroupIdentity,GroupMember,GroupRoster,GroupText}.java`, `application/{GroupAuthority,GroupChatService}.java`, `protocol/{GroupV1Codec,GroupV1Message}.java`, `security/ProtectedGroupStore.java`, `transport/SecureSessionRouter.java`, `listener/GroupActionPort.java` plus LiveChatAdapter and MainChatFrame extensions. Existing Demo group must remain isolated.

**Test-first mapping:** T011–T022 domain/signature/codec; T023–T032 capability/transport; T033–T040 DPAPI/restore/RAM privacy; T041–T054 invite/roster; T055–T072 fanout and receipts; T073–T092 GUI/three peers/physical LAN/security.

**Pending approval:** Gate 0 must review canonical signing vectors, exact v3 capability transcript, removal race, quotas and DPAPI cross-user semantics. No source code or tests are asserted to exist merely because this data-model document exists.