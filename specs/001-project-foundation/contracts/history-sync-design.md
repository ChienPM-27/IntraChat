# History Synchronization (P2P) — Future Design

**Clarification CL-04:** Multi-device History synchronization over LAN peer-to-peer.
**Implementation:** strictly **Phase 005**, not part of M0.
**Trust:** Only authenticated and authorized participants may exchange events; same LAN alone grants no access.

## Desired behavior

Each allowed device retains a local history database for conversations it may access. After disconnect/reconnect, peers converge toward a common set of authorized events (**eventual consistency**). No central server is required. If no authorized device holding missing history is online, full catch-up is temporarily impossible.

## Draft event model

```text
HistoryEvent(
  eventId: UUID, conversationId: UUID, originDeviceId: UUID,
  originSequence: long, type: EventKind,
  messageId?: UUID, payloadHash?: bytes, sentAtUtc: Instant, payload?: ...
)
SyncCursor(conversationId, remoteDeviceId, knownOriginSequences)
```

- Events are immutable. `eventId` globally unique, per-origin `originSequence` monotonic; missing ranges detected by summary/requests.
- Use unique constraints `eventId`, optionally `originDeviceId + originSequence` to enforce idempotency.
- Cursor advances only after durable validation and persistence.
- Concurrent messages from distinct devices must both survive merge even when clocks are skewed.
- Edit/delete require explicitly defined tombstone and conflict semantics; automatic timestamp-based overwrite is **not approved**.

## Authorization model (must be specified before implementation)

1. Device identity cannot be proven by IP, displayName, self-declared UUID or LAN membership.
2. Pick secure device pairing/authentication and bind channel to validated identity.
3. Verify conversation ACL/membership **before revealing** summary metadata or event content; recheck permissions per batch.
4. Revocation, history of removed group members, deletion propagation and local data retention must be specified in `005-chat-history/spec.md`.
5. No claim of end-to-end or transport encryption until implemented and tested; sensitive deployment needs explicit risk assessment.
6. Do not sync binary media bytes automatically; add policy when 006/007 exist.

## Proposed protocol

1. Connect peer and establish a verified session.
2. Determine authorized conversations; send scoped per-conversation `SYNC_SUMMARY`.
3. Request missing per-origin ranges via `SYNC_REQUEST`.
4. Respond in small bounded `SYNC_BATCH` events.
5. Validate schema, size, event identity, origin and rights; merge idempotently and commit transaction.
6. Persist cursor/checkpoint only after successful commit.
7. Repeat after reconnection until no missing events or peer disconnects.

## Required future acceptance scenarios

- **S1 Offline:** B disconnects, A receives messages, B reconnects and obtains authorized missing events.
- **S2 Duplicate:** same event received twice after retries; exactly one durable row and no accidental duplicate UI message.
- **S3 Unauthorized:** C guesses conversation ID; cannot retrieve summary, content or event counts.
- **S4 Concurrent:** A and B create events while offline; both exist after authorized reconciliation regardless of wall-clock order.
- **S5 Delete/revoke:** defined tombstone and access revocation preserve privacy and consistency under replays.
- **S6 Fail midway:** stream drops mid-batch; cursor does not advance for unpersisted events; retry is safe.
- **S7 No peer online:** application honestly displays sync pending, never all-devices-synced without evidence.

## Deferred

Encryption/key handling, peer certificate/trust store, membership protocol, precise SQLite schema, events for edits/deletes, retention, compaction/checkpoints, conflict resolution, batch limits, storage quota, schema migrations, supported multi-user/device account semantics.
