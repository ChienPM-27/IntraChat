# Protocol v1 Draft — DESIGN ONLY

**State:** Draft contract, not a wire implementation. No sockets, serializers or message delivery in M0.

## Framing proposal

TCP is a stream of bytes, **not** messages. Proposed layout:

```text
[4-byte unsigned big-endian JSON length][N bytes UTF-8 JSON envelope]
```

- Maximum control JSON payload: **1,048,576 bytes** (proposed; validate before allocating any buffer).
- Invalid length 0, length greater than limit, truncated frame or malformed UTF-8/JSON: reject safely and close/report protocol failure according to future transport spec.
- Binary files are not embedded in huge JSON strings. Use a separate bounded streaming/chunk transfer design in 006.
- Timeout, cancellation, rate/queue/resource limits and handshake will be finalized in 002.
- Neither Java `ObjectInputStream` nor Java native object serialization is acceptable for untrusted LAN input.

## Required envelope

```json
{
  "protocolVersion": 1,
  "messageId": "2a47055c-b4ac-47d5-8cf1-2813243c64f0",
  "type": "CHAT_TEXT",
  "sender": {
    "deviceId": "67b7b68a-8363-47f4-a37a-ce0f938cf462",
    "displayName": "Hòa"
  },
  "destination": {
    "kind": "PEER",
    "id": "98e29383-706c-4372-9de4-39df7ad5b067"
  },
  "sentAt": "2026-11-21T06:00:00Z",
  "payload": { "text": "Xin chào 👋" }
}
```

**Contract fields:** `protocolVersion` integer, `messageId` unique UUID, `type` from supported vocabulary, `sender.deviceId` stable UUID, `destination.kind` PEER/GROUP with `destination.id`, `sentAt` UTC/offset ISO-8601, `payload` typed per message type. The Java M0 `MessageMetadata` record represents **metadata only**.

**Not authenticated:** `sender.deviceId` is an **assertion**, not proof of device identity. Transport must bind verified sender to session when authentication exists.

## Type catalog (tentative)

| Type | Concept | Phase |
| --- | --- | --- |
| HELLO | Protocol/version and identity negotiation | 002 |
| CHAT_TEXT | Chat content | 003 |
| GROUP_MESSAGE | Group content | 004 |
| ACK | Defined acknowledgement type | 002/003 |
| SYNC_SUMMARY | Per-conversation/per-origin missing-event inventory | 005 |
| SYNC_REQUEST | Request events absent locally | 005 |
| SYNC_BATCH | Bounded event batch scoped by authorization | 005 |
| FILE_OFFER | File metadata proposal | 006 |
| FILE_STATUS | Pending/accepted/progress/completed/failure status | 006 |

## Invalid examples (rejected by future network parser)

1. `{"protocolVersion":1,"type":"CHAT_TEXT"}`: missing message ID, sender, destination and timestamp.
2. Envelope with `"protocolVersion":999`: incompatible version requires explicit refusal/negotiation.
3. Unknown `type` or destination kind: reject type not understood; do not route by guess.
4. Claimed sender.displayName matches a peer but sender.deviceId differs: **do not** infer authorization from display name.
5. Frame length > 1 MiB: reject **before** allocation.
6. Truncated JSON or unexpected binary: fail boundedly; do not deserialize.
7. A clock far ahead/behind: preserve timestamp for display but do not use it as causal ordering.

## Group & history caveats

- TCP preserves bytes in a connection, not global order across multiple peers.
- `sent/delivered/read` statuses need trustworthy ACK/receipt semantics, not mere socket writes.
- Group fanout, membership, duplicate message policy and event replay require separate specs.
- History sync cannot reveal private conversation metadata to nonmembers. Authenticate and authorize **before** summary or batch.
- Schema version changes must be negotiated; unknown optional fields may be skipped only when safe and backward compatible.
