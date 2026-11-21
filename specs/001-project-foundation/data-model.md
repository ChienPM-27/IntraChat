# Data Model — IntraChat Foundation

**Stage:** Conceptual design for M0. Only `PeerIdentity`, `ConnectionState` and `MessageMetadata`/typed support are coded.

## Entity catalog

| Entity | Identity / fields | Relations | Phase |
| --- | --- | --- | --- |
| Device/Peer | `deviceId: UUID`, `displayName` | Member in conversations; has connection state | 002/003 |
| User (possible) | `userId`, device bindings | Might own multiple devices; **not assumed** | 005 |
| Conversation | `conversationId`, kind P2P/GROUP, membership | Includes allowed participants, messages | 003/004 |
| Message | `messageId`, type, senderDeviceId, destination, sentAt UTC | Envelope, optional attachment, history events | 003 |
| Group | `groupId`, membership/roles | Conversation and delivery permissions | 004 |
| Attachment | `attachmentId`, name, size, media type, hash | Belongs to message; bytes transferred separately | 006/007 |
| Transfer | `transferId`, sender/recipients, bytes and state | Acknowledged, rejected, failed, cancelled | 006 |
| ConnectionState | DISCONNECTED, CONNECTING, CONNECTED, FAILED | **Not** message delivery status | 002 |
| MessageDeliveryState | QUEUED, SENT, DELIVERED, READ, FAILED | Must be backed by defined acknowledgments | 003 |
| HistoryEvent | `eventId`, `conversationId`, `originDeviceId`, sequence, eventType, optional messageId, UTC timestamp | Eventually consistent immutable event | 005 |
| SyncCursor | remote device, conversation, known events/sequences | Updated **after durable merge** | 005 |

## Invariants & relations

- UUID `deviceId` persists across display-name edits, and is not determined from IP or username.
- Two devices may have the same display name but distinct `deviceId`. Likewise, one person on two devices must not automatically receive both devices' conversation histories.
- Sender identity is always explicit on each message; a message destination has kind PEER or GROUP and stable ID.
- UTC timestamp is for representation and user display; use eventId and source sequence (plus causal metadata when needed) for sync instead of ordering events only by wall time.
- Attachment metadata may sync after authorization, but binary attachments do not automatically replicate merely because History sync exists.
- Event identity vs message identity: editing/deleting a message would create new event IDs; policy on tombstones, conflicts, retention and permission revocation is deferred to spec 005.

## Example: colliding display names

```text
Peer A: deviceId = 11111111-1111-4111-8111-111111111111, displayName = "Hòa"
Peer B: deviceId = 22222222-2222-4222-8222-222222222222, displayName = "Hòa"
=> Different senders, different rights, different storage keys.
```

## History Sync conceptual sequence

```text
Authenticated A -- SYNC_SUMMARY(conversation, per-origin ranges) --> Authenticated B
Authenticated A <-- SYNC_REQUEST(missing ranges) ------------------- Authenticated B
Authenticated A -- SYNC_BATCH(authorized, bounded events) ---------> Authenticated B
                B deduplicates, persists, then advances cursor
```

A disconnected B will reconcile when online, **only if authenticated and still authorized**. Invalid peers never receive summaries containing private message data. This is a future design, not a protocol implementation.

## Open items for spec 005

- User-device binding and pairing; authenticated identity and trust/revocation.
- Concrete SQLite schema, migrations, encryption at rest if needed, retention/delete.
- Conflict and tombstone semantics; membership changes and access to prior messages.
- Per-origin cursor and batching, idempotence, quota, causality and error recovery.
