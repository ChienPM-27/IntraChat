# Data Model — LAN Networking M1

**Status:** Proposed entities/contracts for phase 002, not persisted schema or existing classes.
**Inputs:** [Spec](spec.md) · [Plan](plan.md) · [Wire contract](contracts/network-v1.md).

## Value entities

| Entity | Fields (proposed) | Invariants |
| --- | --- | --- |
| `LocalPeerProfile` | `deviceId:UUID`, `displayName:String` | UUID stable within local config, separate from display name and IP; config stores no private messages or secret |
| `RemotePeerClaim` | `deviceId:UUID`, `displayName:String`, `observedEndpoint:IPv4:port` | ID/name are UNVERIFIED claims; endpoint is observed socket address, not remote promise |
| `NetworkConfig` | `bindAddress:IPv4`, `listenerPort:int`, `remoteAddress:IPv4`, `remotePort:int`, timeouts/caps | IP address and port manual; no discovery; program uses 1..65535, test bind may use port 0 |
| `ConnectionSession` | `sessionId:UUID`, `remoteDeviceId`, `local/remoteEndpoint`, `version:int`, `state`, `lastError`, `createdAt` | Only active after accept + negotiated handshake; session NOT authentication |
| `InboundRequest` | `candidateId`, `observedEndpoint`, `peerClaim`, `deadline` | Human Accept/Reject; no autoapproval; timeouts/cancel close underlying socket |
| `ApprovalDecision` | `ACCEPT` / `REJECT` | Default on expiry/error/window close = REJECT; no trust persisted |
| `HandshakeMessage` | `requestId`, protocol version(s), `deviceId`, optional session/nonce | Missing/unknown version rejected; message correlation required |
| `NetworkEnvelope` | `protocolVersion`, `messageId`, `type`, `sender`, `destination`, `sentAt`, `payload` | Strict typed JSON, UTF-8, unknown type rejected, no user chat payload in M1 |
| `Frame` | `length:uint32 big-endian`, `utf8JsonBytes` | Length checked BEFORE allocation; 16KiB preapproval, 1MiB max control after approval |
| `DiagnosticProbe` | `probeId:UUID`, synthetic nonce, `sentAt:Instant` | Max 4KiB, no user-supplied chat text, echo only for approved session |
| `ConnectionEvent` | `sessionId`, `eventKind`, `endpoint`, error code, timestamp | Asynchronous observer; UI text never infers delivered/read/authenticated |
| `SessionRegistry` | active peer ID map, pending candidates, quotas | ≤4 active, ≤4 pending; duplicate policy operational only, no security claims |

## Session lifecycle

```text
Local Listener: STOPPED → STARTING → LISTENING → STOPPING → STOPPED
                         \→ FAILED

Outbound: DISCONNECTED → CONNECTING → AWAITING_REMOTE_APPROVAL
                               → NEGOTIATING → CONNECTED
                      \→ FAILED or DISCONNECTED on cancel/close

Inbound: TCP_PENDING → HELLO_VALIDATED → WAITING_USER_APPROVAL
                                                   ├─ REJECT/TIMEOUT → CLOSED
                                                   └─ ACCEPT → NEGOTIATING → CONNECTED/CLOSED
```

**Never** represent TCP socket accept as `CONNECTED`. The accepting user only approves **synthetic diagnostics** and does **not** verify peer identity. The initiator knows the endpoint IP:port but cannot trust the remote UUID without future pairing/auth.

## Relationship / future boundaries

- A single local device may have multiple sessions to **different peer IDs**, up to the registry quota. One active session per *declared* remote ID; deterministic tie-break for concurrent two-way connections.
- A `MessageMetadata` from M0 remains transport-independent. HELLO uses destination NIL UUID sentinel (unknown peer) **only during bootstrap**; every active diagnostic has explicit remote destination UUID.
- Reject future `CHAT_TEXT/GROUP_MESSAGE/SYNC_*/FILE_*` while in M1, including after successful consent/handshake.
- `Conversation`, `HistoryEvent`, `SyncCursor` and Group ACL are **not** M1 data models/runtime dependencies. History Sync in 005 must authenticate device identities and check per-conversation authorization **before** summaries/content.
- The local profile is nonsecret application config with clear invalid/corrupt handling; no SQLite, attachment, chat retention or migration in M1.

## Validation examples

1. Two peers named `Hoài` with different device UUIDs are distinct. Display name change does not rotate UUID.
2. Peer claims local UUID from remote endpoint: reject as self/ID collision operationally; not a cryptographic spoof detector.
3. Inbound HELLO claims sender A from endpoint B: prompt shows actual B endpoint and `A (tự khai, chưa xác thực)`; no trust.
4. Timeout waiting for approval: candidate closed, no session entry remains, any diagnostic received before approval rejected.
5. Duplicate request ID/session ID or unexpected HELLO_CONFIRM/READY: reject/close and no `CONNECTED` state.
