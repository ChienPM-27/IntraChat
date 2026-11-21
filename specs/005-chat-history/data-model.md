# History 005 — logical data model and state transitions

**Status:** CL-05-01…10 **USER CONFIRMED (all A)**; logical design only, concrete SQL schema/encrypted driver **NOT FROZEN** · [spec](spec.md) · [plan](plan.md) · [contract](contracts/history-v1.md). **No SQL schema/migration or Java History code created yet.**

## 1. Entities and invariants

| Entity | Core fields (logical names, not final SQL types) | Invariants |
| --- | --- | --- |
| `HistoryProfile` | windowsUserScope, dataStoreVersion, consentState, activatedAt, localGeneration, retentionPolicy, keyRef | One Windows user, encrypted store; opt-in required; key != M2 identity key |
| `HistoryConversation` | scopeKind=P2P/GROUP, scopeId, verifiedParticipantRefs, createdAt, contentPolicy, localDeletionFence | Exact ID not display name or IP; wrong scope denies |
| `HistoryEvent` | scopeKind, scopeId, stable eventId, originalMessageId, sourceDeviceId, signerSpkiDigest, originalSentAtUtc, kind=TEXT/EMOJI, canonicalText, contentHash, originSignature, sourceProtocolVersion, groupEpoch?, groupRevision?, ownerSequence? | Primary unique (kind, scopeId, eventId); immutable after durable commit; signed origin checked before insert |
| `HistoryLocalCommit` | scope/event reference, storedAtUtc, durableCommitVersion, contentEligibility | SAVED means committed local only; not chat ACK/READ |
| `HistoryTombstone` | scope, eventId or conversationDeletionGeneration, protectedDeletionEpoch, deletedAtUtc | Reject silent import; tombstones/fence remain until safe retention resolution |
| `HistoryConflict` | scope, eventId, expectedHash/signerDigest, offeredHash/signerDigest, detectedAtUtc, quarantineCode | No plaintext content in routine diagnostics; never auto overwrite |
| `HistorySyncSession` | verifiedSessionId, peerId/spki, scope, requestId, capabilityTranscriptHash, runNonce, state, deadline | RAM only; stale cursor not ACL, no auto reconnect |
| `HistoryManifestPage` | scope, partition or bounded ordered eventIds+digests, version, opaqueCursor | Bounded window, sent only after auth, no timestamp-only completeness |
| `HistoryDeltaPage` | scope, requestId, bounded event seals, pageIndex, pageDigest, ownerEligibilityProof? | Per-event verified before durable apply; network frame ≤65,536 |
| `HistorySyncCheckpoint` | scope, peerId, authenticatedRun, durableAppliedIDs or safe digest cursor, lastSyncAttempt/status | Advance only after durable apply; reload requires new permission check |
| `GroupHistoryEligibility` | groupId, memberId, verifiedSpki, joinCommitRevision/sequence, removeCommitRevision/sequence?, ownerEpoch/proof | Only events AFTER JOIN, none newly issued after REMOVE; handoff continuity required |

## 2. SQLite encrypted database schema sketch — **technical DDL NOT FROZEN**

~~~sql
-- CL-05-02 CONFIRMED: SQLite MUST be fully encrypted at rest; select approved cipher driver first.
-- No literal user chat/keys in application logs or plaintext DB/WAL/temp.
CREATE TABLE history_profile(
  profile_id INTEGER PRIMARY KEY,
  version INTEGER NOT NULL,
  consent_state TEXT NOT NULL,
  activated_at_utc TEXT,
  deletion_generation INTEGER NOT NULL
);
CREATE TABLE history_conversation(
  scope_kind TEXT NOT NULL CHECK(scope_kind IN ('P2P','GROUP')),
  scope_id TEXT NOT NULL,
  local_generation INTEGER NOT NULL,
  PRIMARY KEY(scope_kind, scope_id)
);
CREATE TABLE history_event(
  scope_kind TEXT NOT NULL,
  scope_id TEXT NOT NULL,
  event_id TEXT NOT NULL,
  source_message_id TEXT NOT NULL,
  origin_device_id TEXT NOT NULL,
  signer_spki_sha256 BLOB NOT NULL,
  source_sent_at_utc TEXT NOT NULL,
  payload_kind TEXT NOT NULL CHECK(payload_kind IN ('TEXT','EMOJI')),
  canonical_utf8 BLOB NOT NULL,
  content_sha256 BLOB NOT NULL,
  origin_signature BLOB NOT NULL,
  source_protocol_version INTEGER NOT NULL,
  group_epoch INTEGER,
  group_revision INTEGER,
  owner_sequence INTEGER,
  stored_at_utc TEXT NOT NULL,
  PRIMARY KEY(scope_kind, scope_id, event_id)
);
CREATE INDEX history_page_idx
 ON history_event(scope_kind, scope_id, source_sent_at_utc, event_id);
CREATE TABLE history_tombstone(
  scope_kind TEXT NOT NULL,
  scope_id TEXT NOT NULL,
  event_id TEXT NOT NULL,
  deleted_at_utc TEXT NOT NULL,
  local_generation INTEGER NOT NULL,
  PRIMARY KEY(scope_kind, scope_id, event_id)
);
CREATE TABLE history_conflict(
  scope_kind TEXT NOT NULL, scope_id TEXT NOT NULL, event_id TEXT NOT NULL,
  signer_hash BLOB NOT NULL, offered_content_hash BLOB NOT NULL,
  detected_at_utc TEXT NOT NULL, reason_code TEXT NOT NULL
);
CREATE TABLE history_checkpoint(
  scope_kind TEXT NOT NULL, scope_id TEXT NOT NULL, peer_id TEXT NOT NULL,
  verified_partition_digest BLOB, committed_version INTEGER NOT NULL,
  PRIMARY KEY(scope_kind, scope_id, peer_id)
);
~~~

**User-confirmed choices fixed:** opt-in, encrypted SQLite/DPAPI/ACL, 90d+500MiB user-adjustable, manual sync, 2-device P2P, current owner Group, local tombstone deletion, no ACK/READ receipt persistence, immutable conflict quarantine, no backup/export. **Not yet DDL-approved:** SQL timestamps versus integer epoch, canonical payload length, Group owner routing proof tables, foreign keys, key rotation and metadata checksum, encrypted index/WAL behavior and delete fence compaction need actual ADR/security review and tests. Do **not** execute this as unreviewed production migration.

## 3. State machines

~~~text
History local consent: DISABLED → (user opt-in) LOCAL_ONLY
LOCAL_ONLY → (manual verified sync) SYNC_PENDING → (both scoped proof) SYNCED
SYNC_PENDING → (cancel/link failure) PARTIAL/PAUSED → (user retry) SYNC_PENDING
any state → (key/cipher/storage failure) ERROR (fail closed)

Per event: CANDIDATE → (verified source + ACL + durable transaction) SAVED
CANDIDATE → (invalid signature/ACL) REJECTED
CANDIDATE → (same ID changed digest) QUARANTINED
SAVED → (local deletion commit) TOMBSTONED (never silent reimport)
SAVED → (peer copy verified) COPY_SYNCED (not chat DELIVERED/READ)

Group authorization: MEMBER_NOT_JOINED → (owner durable JOIN) ELIGIBLE
ELIGIBLE → (owner durable REMOVE) REVOKED, no NEW History copies
owner retired/ambiguous handoff → PAUSED until successor proof accepted
~~~

**Ordering:** Group uses verified ownerSequence and owner run/epoch where available; P2P stable source timestamp + event ID tie-break and optional local commit order, not global causal time. Repeated event append is a no-op only when original signer and content digest match. Event signed provenance cannot be manufactured by a relay or copied from a TLS hop.

## 4. Transactional operations and security gates

1. `appendImmutable`: check consent → validate source / exact scope ACL → check deletion fence → existing duplicate/conflict → bounded encrypted transaction commit → only then return SAVED.
2. `getHistoryPage`: check local Windows account+scope, cursor/version, max page, run background executor; deliver immutable view projection on Swing EDT.
3. `authorizeSync`: verified TLS session/History capability and exact P2P pair or Group owner/eligible roster → only then share any manifest/count.
4. `applyDeltaBatch`: per-event signature/hash/source and join/remove authorization → atomic bounded insert, quarantine conflicts → durable checkpoint → apply-ACK.
5. `deleteLocal`: capture exact user selection, atomically persist deletion intent/tombstone and remove content; maintain non-resurrection generation on restart; no remote-wipe claim.
6. `expire/compact`: apply confirmed retention, protect tombstone fences, verify encrypted side files, leave trust/group metadata unaffected.

**Privacy:** receipt ACK/READ tables/columns must NOT be introduced (CL-05-08); History local saved/sync state is separate. No backup/export jobs (CL-05-10). Retention by age/total size 90d+500MiB default with durable tombstone fences (CL-05-03/07). All data shown here is conceptual. Never log content or dump real SQL rows in evidence. DPAPI-wrapped key doesn't encrypt the SQLite engine unless an encrypted driver actually implements page protection.
