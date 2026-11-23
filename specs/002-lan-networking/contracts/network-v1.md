# Network Protocol v1 — LAN P2P M1 (DESIGN CONTRACT)

**Status:** Design only; no M1 network implementation or execution proven by this file.
**Feature:** [Spec](../spec.md) · [Plan](../plan.md) · [data model](../data-model.md).
**Scope:** IPv4:port **manual only**, **human inbound approval**, **synthetic diagnostics only**, **plaintext/unverified**, **manual reconnect**.

## 1. Transport and frame

Two peers connect **direct TCP** to a user-started listener on selected local IPv4 and port (default suggestion `47321/TCP`). No cloud, NAT traversal, UDP discovery, mDNS, broadcast or auto-reconnect.

A frame is:

```text
Offset  Bytes      Meaning
0       4          unsigned big-endian length N of UTF-8 JSON bytes
4       N          UTF-8 JSON object (single envelope)
```

- `1 <= N <= 1,048,576` for an **approved session**. Initial HELLO and other preapproval negotiation frames: `N <= 16,384` (no user payload allowed). Diagnostic payload property itself must be at most **4,096 encoded bytes**.
- Reject `N=0`, negative Java int representation of lengths >2^31–1, `N>max`, truncated prefix, early EOF during body and malformed UTF-8 **before** unbounded allocation. Fatal framing error closes session; do not attempt to resynchronize by searching JSON delimiters.
- TCP can split a single frame over multiple `read()` calls or combine multiple frames in one `read()`. Decoder must read exactly `4+N` bytes per frame and preserve the remaining bytes for the next frame.
- UTF-8 decoder is strict (bad bytes report error, not replacement); JSON parsing rejects duplicate keys, extra tokens after object, excessive depth (design max 32) and missing/unknown mandatory schema. Unknown fields allowed **only** when explicitly classified as safe extensions in a future contract.
- All sockets use bounded connect/read deadlines; no `ObjectInputStream`/native Java deserialization. No binary file bytes or private History in this JSON framing.

## 2. Envelope (all M1 wire messages)

The envelope has these required fields:

| Field | Type | Rule |
| --- | --- | --- |
| `protocolVersion` | integer | Exactly **1** in this implementation; other values rejected |
| `messageId` | UUID string | Unique per envelope; do not treat as guaranteed dedup |
| `type` | enum string | M1 whitelist only; future chat/history/file types **rejected** |
| `sender.deviceId` | UUID string | **Self-reported**, not cryptographically authenticated |
| `sender.displayName` | 1–128 Unicode characters | Display label only; strip/reject control characters, never use for authorization |
| `destination.kind` | string | `PEER` only for M1 |
| `destination.id` | UUID string | Remote declared device ID after HELLO, special sentinel for bootstrap HELLO only |
| `sentAt` | ISO-8601 UTC string | UTC timestamp for display, NOT global causal ordering |
| `payload` | JSON object | Strict schema per type, small and bounded |

**Bootstrap exception:** When the sender does not know a remote device ID, the **first `HELLO` only** may use destination `00000000-0000-0000-0000-000000000000`. All other types MUST use actual UUID; misuse of sentinel is `HANDSHAKE_INVALID` or `JSON_INVALID` according to parser stage. This makes the M0 conceptual `MessageMetadata` compatible without guessing the remote ID; update M0 draft only with an explicit contract note, do not silently repurpose messages.

**Example HELLO (first message, before human approval):**

```json
{
  "protocolVersion": 1,
  "messageId": "dd949fe2-eaa2-4cd8-b333-827ee1989b9e",
  "type": "HELLO",
  "sender": {
    "deviceId": "86dc458d-ae44-4e8d-9e71-7ca7f2acd608",
    "displayName": "May A"
  },
  "destination": {
    "kind": "PEER",
    "id": "00000000-0000-0000-0000-000000000000"
  },
  "sentAt": "2026-11-21T07:00:00Z",
  "payload": {
    "requestId": "d2387368-624b-4d07-9ec6-f6d56dd7a272",
    "supportedVersions": [1],
    "clientVersion": "0.1.0-SNAPSHOT"
  }
}
```

**Valid diagnostic Ping (only after READY):**

```json
{
  "protocolVersion": 1,
  "messageId": "0cb196dc-d2f0-44a7-8cda-5584f9566b8a",
  "type": "DIAGNOSTIC_PING",
  "sender": {
    "deviceId": "86dc458d-ae44-4e8d-9e71-7ca7f2acd608",
    "displayName": "May A"
  },
  "destination": {
    "kind": "PEER",
    "id": "f8d6e537-949a-462f-b3cd-87fae9b2f5d1"
  },
  "sentAt": "2026-11-21T07:01:00Z",
  "payload": {
    "probeId": "565c599c-4a63-4397-a912-d343005c117e",
    "nonce": "diagnostic-1234"
  }
}
```

## 3. Message types / validity window

| Type | Allowed time | Required payload | Consequence |
| --- | --- | --- | --- |
| `HELLO` | First frame of new incoming/outgoing socket | `requestId:UUID`, supported versions containing 1, clientVersion | Open pending approval; no active session |
| `HELLO_ACCEPT` | **Only after human Accept** | requestId, sessionId, chosenVersion | Initiator validates and confirms |
| `HELLO_REJECT` | User Reject, expiry, malformed version or capacity | requestId if known, `reasonCode` | Close candidate; no session |
| `HELLO_CONFIRM` | After valid accept | requestId, sessionId, chosenVersion | Receiver checks correlation |
| `HELLO_READY` | Receiver after confirmed session | sessionId | Initiator can enter CONNECTED; only diagnostics from now on |
| `DIAGNOSTIC_PING` | Active approved session | probeId, small synthetic nonce | Return matching PONG |
| `DIAGNOSTIC_PONG` | Active approved session | probeId, status or synthetic echo | Correlate with outstanding probe |
| `PROTOCOL_ERROR` | If safe to send and bounded | errorCode, optional requestId; no raw input | Best-effort response, close if fatal |

`CHAT_TEXT`, `GROUP_MESSAGE`, `ACK` (unless purpose explicitly documented), `SYNC_SUMMARY`, `SYNC_REQUEST`, `SYNC_BATCH`, `FILE_OFFER`, `FILE_STATUS` and unknown types **MUST NOT** be routed or processed in M1, even after Accept. Use `UNSUPPORTED_TYPE_FOR_PHASE` and close on violation.

## 4. Receiver inbound approval semantics

```text
TCP accepted → read bounded HELLO within 5s → validate basic schema and version
→ show **real remote endpoint** + self-declared device name/UUID + security warning
→ WAITING_USER_APPROVAL (≤30s, pending max 4)
   Reject, close dialog, timeout or app shutdown → HELLO_REJECT best-effort → CLOSE
   Explicit human Accept → HELLO_ACCEPT with sessionId and chosenVersion
→ initiator HELLO_CONFIRM → receiver HELLO_READY → approved diagnostic session
```

- A socket accepted by the operating system **does not equal** an accepted IntraChat session. Mark `CONNECTED` only after explicit human Accept and successful handshake. Use nonblocking modeless Swing UI and async decision port.
- Approval should **not** store a trusted peer entry or treat the provided UUID/IP/name as authenticated.
- While pending, receiving data other than handshake (including diagnostics) is an error; user data MUST NOT be delivered to any application handler.
- HELLO_REJECT can be unavailable if connection closes; outbound must handle EOF/timeouts as a refused/failed attempt without falsely claiming consent.
- On concurrent cross-connection, registry duplicate rule retains one session per *declared* peer ID; ID collision detection is operational, not anti-spoofing.
- If peer closes between READY send and receipt, each UI reports its *local observed state*; disconnect/liveness reconciles, no distributed atomic-commit guarantees.

## 5. Failure codes / error boundary

| Code | Trigger | Handling |
| --- | --- | --- |
| `INVALID_ENDPOINT` | Invalid IPv4 literal/port | Reject before socket |
| `BIND_FAILED` | Port busy/interface invalid | Listener fails visibly |
| `CONNECT_TIMEOUT`, `CONNECT_REFUSED` | Remote unavailable | Bounded fail, manual retry available |
| `USER_REJECTED`, `APPROVAL_TIMEOUT` | Human decision/deadline | Close candidate, never CONNECTED |
| `VERSION_UNSUPPORTED`, `HANDSHAKE_INVALID` | Wrong/missing fields/correlation/ordering | Close candidate |
| `FRAME_INVALID_LENGTH`, `FRAME_TRUNCATED` | 0, >cap, overflow, EOF midframe | Reject without allocation, close |
| `UTF8_INVALID`, `JSON_INVALID` | Charset/JSON/schema/nesting/duplicate errors | Reject and close |
| `UNSUPPORTED_TYPE_FOR_PHASE` | Future chat/group/history/file or unknown type | Reject, no application side effect |
| `CAPACITY_EXCEEDED` | Pending/active/queue quota | Close or fail boundedly |
| `REMOTE_CLOSED`, `HEARTBEAT_TIMEOUT`, `USER_CANCELLED` | Connection/lifecycle | Close all owned resources, show accurate state |

**No leaked details:** `PROTOCOL_ERROR` may include code/requestId only; do not echo untrusted JSON, OS filesystem paths, private IP inventory, user chat or stack traces. Standard logs may include sanitized event code and locally observed endpoint to facilitate diagnosis, but must not contain secrets or private contents.

## 6. Limits and test matrix

**Initial design values:** 4 active sessions; 4 pending inbound approvals; 16 outbound queued frames and total ≤2 MiB per session; 16 KiB preapproval HELLO; 1 MiB postapproval control frame; 4 KiB synthetic diagnostics; TCP connect timeout 5s; initial HELLO receive 5s; inbound approval 30s; expected frame read 15s; PING every 10s, PONG deadline 30s. **These are plan targets, not verified benchmarks.**

Must test: 2-process loopback and 2 real Windows LAN hosts; Connect+Accept, Connect+Reject, prompt expiry, wrong versions, duplicate IDs, bad UTF-8 and malformed JSON, frame prefix split/coalesced/truncated/length 0 or > cap, listener bind conflict, queue saturation, remote crash, manual reconnect, thread/socket cleanup, Windows Private profile/IP/port guide and no Internet runtime dependency.

## 7. Trust boundary — non-negotiable

This is a **plaintext, unauthenticated technical diagnostics transport**. All HELLO identity fields are attacker-controlled. Clicking Accept permits a single candidate to exchange **synthetic diagnostic** frames, not private chat/History/Group/File; it does not establish peer authentication or storage/ACL authorization. M1 has no chat, storage/file transfer, group fanout, secure key exchange or TLS. History Sync in Giai đoạn 05 **must** use verified peer identity and per-conversation authorization before transferring summary/event data. Declare these limitations in GUI and README.

**Contract versioning:** Any incompatible field/type change needs explicit protocol version evolution and negative tests, not a silent parser behavior change.
