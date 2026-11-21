# Contract — CHAT_V2 secure direct P2P (prototype + incomplete release review)

**Date:** 2026-11-21
**Status:** Partially implemented locally in the M2 prototype, **not a released/interoperability-approved contract**. The shared v1/v2 port dispatcher and dual-physical-PC acceptance remain NOT RUN.
**References:** [spec](../spec.md), [plan](../plan.md), [data model](../data-model.md).
**Safety:** M1 v1 remains diagnostic-only and plaintext. **Never transmit CHAT_TEXT/DELIVERY_ACK/READ_RECEIPT over existing raw TCP v1.**

## 1. Two distinct negotiated paths

**Existing M1 v1:** four-byte length + strict UTF-8 JSON, HELLO*/DIAGNOSTIC_PING/PONG/PROTOCOL_ERROR; maximum 16 KiB preapproval, 1 MiB postapproval. The v1 decoder continues to reject chat/History/File/Group.

**New CHAT_V2 path:** TCP manual IPv4:port, receiver explicitly accepts each incoming connection. First frame is a small **unencrypted, data-free capability preface** (maximum 16,384 encoded bytes). Once the user accepts, both endpoints upgrade **the same socket** to JSSE TLSv1.3 with mutually presented device X.509 certificates. **No CHAT_TEXT or identity-trust authorization until TLS and SAS/pinning completed.** Inside TLS, use 4-byte unsigned big-endian JSON byte length with maximum **65,536 bytes**. Invalid length zero/oversize/overflow/truncated frame: fail closed before allocating unbounded memory.

**Version enforcement:** The bounded first preface explicitly states CHAT_V2 and protocol version 2. Dispatcher routes v1 diagnostic only or v2 secure, not automatically one after the other. A remote version mismatch produces a bounded error/close with **no fallback to plaintext chat**. Do not reuse current JsonEnvelopeCodec as a v2 decoder; it strictly handles version 1.

## 2. Small first preface (UNTRUSTED; no chat)

Fields in one JSON object with strict schema (example values are illustrative, not actual credentials):

| Field | Type | Meaning |
| --- | --- | --- |
| prefaceVersion | integer 2 | Route selector, never downgrade for chat |
| type | string CHAT_HELLO | No diagnostic or user payload |
| requestId | UUID string | Correlate approval/reject |
| sender.deviceId | UUID string | **Untrusted until paired TLS** |
| sender.displayName | bounded text ≤128 chars | UI-only, never trust |
| targetDeviceId | UUID or all-zero sentinel for unknown | Bootstrap only |
| senderNonce | 32 random bytes, Base64 | Replay resistance and SAS transcript binding |
| capabilities | array of known strings | Must contain CHAT_TLS13_V2; reject unknown critical flag |

Receiver reads HELLO under **5s**. Show observed SocketAddress + claimed metadata + warning; human decision timeout **30s**. It replies **CHAT_ACCEPT** (matching requestId and receiverNonce) only on explicit Accept, or **CHAT_REJECT** with reason code. Reject/timeout → close, never begin chat. Bounded errors do not echo untrusted input.

**Warning:** The preface and CHAT_ACCEPT are **unencrypted/unverified**. They carry only routing, random nonces and self-claims, not text, pairing secrets or trusted data. TLS handshake begins **only after human approval**. This is a transport dispatch pattern, not the M1 diagnostic authorization.

## 3. TLS identity and pairing checks

- Require TLSv1.3, server requires client certificate, both endpoints present locally generated self-signed **ECDSA P-256 X.509** identity certificate. JSSE verifies key possession within TLS handshake. Reject weak/unsupported key, invalid DER, invalid time window, incorrect key usage, or inability to validate a self-signed leaf signature.
- **Pinned peer:** require exact SPKI SHA-256 match for the claimed deviceId and claimed peer public key. A self-claimed UUID/endpoint/displayName alone is NEVER accepted. Key change/revocation → stop.
- **Unknown peer:** cryptographically valid but unknown identity cert may finish TLS only in a **quarantined, unauthorized** state. No CHAT_TEXT or receipts accepted/sent. An unconditional trust-all manager must **not** be used for actual chat authorization. An independent security review of first-contact verifier is a blocking prerequisite.
- Shared **pairing SAS** (both displays MUST calculate the same value): SHA-256 with literal domain separator "IntraChat-PAIR-V2" over **canonical binary encoding** of two identity tuples (deviceId UUID bytes, DER-SPKI SHA256), sorted lexicographically by fixed binary tuple bytes, plus **both preface nonces in the corresponding sorted tuple order**; display first 64 bits as four groups of four uppercase hex characters. Full SHA-256 SPKI fingerprints must be available in UI details. Neither endpoint may accept a peer-supplied SAS string as proof.
- Users verify the matching code through an **independent trusted channel** (same room, direct voice conversation) and **each clicks Confirm locally**. Conflicting/incorrect/no match, timeout (target 60s) or canceled consent closes the attempt. A new/changed pin is never approved merely by remote PAIR_CONFIRM.
- Inside TLS, use PAIR_CONFIRM from each endpoint only after its **local human approval**, binding the requestId, TLS session ID/peer keys, identity pair and nonce transcript; require matching confirmation from peer. Exchange PAIR_READY after validating both confirmations and user-local pin write. **Enable chat only after both PAIR_READY observed in the active secure session.** A crash after one peer wrote a pin is not a distributed atomic transaction; the next connection must reconcile by re-verifying pin and possibly repeat pairing, never send before session READY.
- Known pinned key: no first-pair SAS prompt needed unless user requests inspection, but new incoming socket still needs explicit human Accept. Always validate the pinned key again; a resumed TLS session must never bypass it.

## 4. Protected application envelope

Once verified, the following metadata is mandatory for every v2 JSON envelope inside TLS:

| Field | Type | Rule |
| --- | --- | --- |
| protocolVersion | integer | Exactly 2 |
| messageId | UUID string | Unique for this envelope (ACK and receipt have separate envelope IDs) |
| type | enum | PAIR_CONFIRM, PAIR_READY, CHAT_TEXT, DELIVERY_ACK, READ_RECEIPT, CHAT_ERROR only |
| sessionId | UUID string | Current authenticated chat session; reject old-session replay |
| sender.deviceId | UUID string | Must equal device UUID bound to authenticated TLS peer SPKI pin |
| sender.displayName | string | **Design-only; not emitted in current v2 protected envelope.** Current prototype exchanges untrusted displayName in pre-TLS HELLO/ACCEPT and binds messages to authenticated sender.deviceId instead. Never authorize by name. |
| destination.kind | string | PEER only for M2 |
| destination.id | UUID string | Must equal local verified peer identity |
| conversationId | UUID string | Derived deterministically from verified (deviceId, pinned SPKI) pair using SHA-256 domain separation and 128-bit UUID version/variant formatting, **not** from displayName or IP; authorization still requires TLS pin check |
| sentAt | ISO-8601 UTC | Display time only, not causal order |
| payload | strict JSON object | Type-specific schema, no additional unknown fields in v2 |

Strict UTF-8 decoder (REPORT for malformed), JSON max nesting depth 32, duplicate keys and extra tokens rejected, UUID canonical format validated; cap text/bytes, no native Java object deserialization. For PAIR_CONFIRM/READY, conversationId may be a special bootstrap value only as defined by a dedicated v2 validator; never treat that sentinel as a real chat conversation.

## 5. Allowed message payloads and semantics

| Type | Required payload | Allowed when | Result |
| --- | --- | --- | --- |
| **PAIR_CONFIRM** | requestId, local/peer key hashes, SAS-transcript hash, confirmationId | TLS established/quarantine; **local user confirmed** | Validate pairing transcript and peer confirmation; no chat yet |
| **PAIR_READY** | requestId, sessionId, transcriptHash | Both confirmations validated; pin written safely | After both READY: verified secure session |
| **CHAT_TEXT** | text (1…8,000 code points AND ≤32 KiB UTF-8) | Verified + protected + consent + v2 READY | Add once to RAM correct peer/conversation; send DELIVERY_ACK |
| **DELIVERY_ACK** | originalMessageId, acceptedAtUTC, result=ACCEPTED | Verified session after receiver accepted original into RAM | Sender may mark APP_ACKNOWLEDGED only on valid correlation |
| **READ_RECEIPT** | originalMessageId, readEventId UUID, viewedAtUTC | Receiver has **actual foreground visible message event**, never simply receipt | Sender marks READ for matching message if peer/session/conversation verified |
| **CHAT_ERROR** | errorCode, optional relatedMessageId | Safe bounded error from protected app | Fail/close according to code, no raw peer payload in error |

**Do not allow** GROUP_MESSAGE, SYNC_*, FILE_*, media types, unknown version, unexpected pre-READY CHAT_TEXT, or raw M1 diagnostic frames inside v2 application stream.

## 6. ACK vs READ, out-of-order and ambiguity

- Sender states DRAFT → SENDING → LOCAL_SENT → APP_ACKNOWLEDGED → READ. FAILED/UNCERTAIN preserve truthful outcome; READ is **never** inferred from TCP write or ACK.
- Receiver sends DELIVERY_ACK only after validating/enqueuing a unique message in RAM. An ACK for wrong peer, conversation, session, missing messageId or expired attempt must not mark any outbound message delivered.
- Read event only after the recipient **Live** UI renders a particular incoming bubble within the viewport of selected, active window for ≥250ms; notification/preview, other conversation, inactive window, offscreen bubble and Demo do **not** emit read. Receiver sends only **one receipt per message/session** with bounded dedup. Offline view → no durable queued read receipt.
- If read receipt arrives before delivery ACK on the same protected verified session: it is stronger evidence of app processing and view, so sender may transition directly to READ after full correlation; late ACK cannot reduce state. Duplicate/replayed/out-of-order read receipt must not create extra side effects.
- A receiver can deliberately lie about showing content; proof of *human actual reading* is outside protocol guarantees. UI should say **Đã xem (hiển thị trên thiết bị nhận)** if explanation is needed.
- On ACK loss, sending side marks UNCERTAIN after bounded deadline (initial target 30s); manual retry only. In-session retry uses same original messageId within dedup window; a fresh session after app restart has no persisted dedup so cannot guarantee exactly once.

## 7. Failure codes and fail-closed behavior

| Code | Meaning | Handling |
| --- | --- | --- |
| INVALID_ENDPOINT / CONNECT_TIMEOUT / CONNECT_REFUSED | Bad/manual LAN endpoint or unreachable | Visible UI error, manual retry |
| USER_REJECTED / APPROVAL_TIMEOUT | Receiver did not consent | Close candidate, no TLS/chat |
| CHAT_VERSION_UNSUPPORTED / DOWNGRADE_DETECTED | Only raw M1 or incompatible capabilities | Close chat attempt, never plaintext fallback |
| TLS_HANDSHAKE_FAILED / CERT_INVALID / CERT_EXPIRED | Cannot establish mutual TLS | Close, no chat payload |
| PEER_NOT_PAIRED / SAS_MISMATCH / PAIRING_TIMEOUT | Incomplete two-user fingerprint verification | Quarantine/close, no CHAT_TEXT |
| PEER_KEY_CHANGED / PEER_REVOKED / PIN_MISMATCH | Previously trusted identity changed | Block, require explicit re-pair |
| TRUST_STORE_LOCKED / DPAPI_FAILED | Credential access/commit unavailable | Fail closed, do not silently regenerate identity |
| FRAME_INVALID / UTF8_INVALID / JSON_INVALID / TYPE_UNSUPPORTED | Bad length/JSON/malformed or future type | Reject safely and close if fatal |
| IDENTITY_MISMATCH / CONVERSATION_MISMATCH / SESSION_MISMATCH | Spoofing, misrouting, stale metadata | Reject, never display as correct sender |
| ACK_INVALID / READ_RECEIPT_INVALID | Forged/incorrect/duplicate/stale reference | Ignore/reject; do not elevate state |
| SEND_CAPACITY / ACK_TIMEOUT / REMOTE_CLOSED | Backpressure or disconnection | Truthful FAILED/UNCERTAIN; explicit user retry |
| SHUTDOWN_CANCELLED | User closes app | Clear ephemeral RAM/session; persistent identity pin retained |

No private messages/keys/raw JSON in user logs; messages in errors are sanitized codes only. A network attacker may learn local IP, existence of listener and plaintext preface metadata, so don't misrepresent metadata secrecy.

## 8. Deterministic canonical binary vectors and wire-error matrix (T005)

All vector inputs below are synthetic bytes, **not actual device SPKI hashes, keys or user credentials**. Independently calculated using Windows PowerShell `SHA256.ComputeHash`, then verified in JDK21 JUnit `FingerprintVerifierTest` and `P2PConversationTest`.

| Field | A | B |
| --- | --- | --- |
| UUID | `11111111-1111-4111-8111-111111111111` | `22222222-2222-4222-8222-222222222222` |
| Synthetic 32-byte fingerprint (hex) | `000102030405060708090a0b0c0d0e0f101112131415161718191a1b1c1d1e1f` | `202122232425262728292a2b2c2d2e2f303132333435363738393a3b3c3d3e3f` |
| 32-byte nonce (hex) | `404142434445464748494a4b4c4d4e4f505152535455565758595a5b5c5d5e5f` | `606162636465666768696a6b6c6d6e6f707172737475767778797a7b7c7d7e7f` |

**Exact SAS transcript bytes:** ASCII `IntraChat-PAIR-V2` prefixed by unsigned 32-bit big-endian length (17); then each sorted tuple prefixed with 32-bit big-endian length (80); a tuple = 16 UUID RFC4122 network-order bytes + 32 fingerprint bytes + 32 nonce bytes. Order tuples using unsigned lexicographic comparison of their entire bytes; each side MUST reverse inputs identically. `SHA-256` of these bytes:

```text
ac818b2d88b58cf914e943963bd4921e307769494b75c427f68f89b0374789dc
```

The **first 8 bytes** yield SAS `AC81-8B2D-88B5-8CF9`, uppercase group-of-4 hex. Altering any nonce/key/ID changes SAS (test includes mutation). SAS does not itself grant trust without **each human confirmation**.

**Conversation ID** input differs from SAS: ASCII `IntraChat-CONVERSATION-V2` **without length prefix**, followed by sorted 48-byte tuples (16 UUID network-order bytes + 32 fingerprint bytes, **no nonce**). SHA-256 first 16 bytes; set UUID version to 5 and RFC variant to 2. Expected ID: `5bc726bf-5e70-5d11-8b49-774e77637482`, independent of input ordering. Conversation ID identifies a pair but is **not** authentication.

**Session ID test vector:** the protected `sessionId` equals the exact pre-TLS HELLO `requestId` (not a fingerprint hash), for example `33333333-3333-4333-8333-333333333333` represented as 16 UUID RFC4122 network-order bytes `33333333333343338333333333333333`. It is repeated in every PAIR_CONFIRM/PAIR_READY/CHAT_TEXT/ACK/READ on that one TLS session. A different `requestId` is a different session and must fail receipt correlation; no reuse/replay across reconnect.

**Handshake / version states (current prototype):**

| State | Allowed wire | Rejection |
| --- | --- | --- |
| Raw socket, untrusted, max 16,384 bytes | single `CHAT_HELLO` v2; `CHAT_ACCEPT` or `CHAT_REJECT` | invalid UUID (including all-zero request/device ID), unknown fields/capabilities, malformed UTF-8, duplicate keys, version mismatch, chat text → fail closed |
| TLS established, not yet paired/READY | `PAIR_CONFIRM`, `PAIR_READY` with matching session and transcript | `CHAT_TEXT`, ACK/READ or v1 diagnostic → reject |
| TLS 1.3 plus both READY, max 65,536 bytes per protected frame | `CHAT_TEXT`, `DELIVERY_ACK`, `READ_RECEIPT` (CHAT_ERROR is schema-known but not yet fully handled) | other types, wrong sender/destination/session/conversation, duplicate JSON keys, oversized frame → reject |
| Raw M1 v1 listener, diagnostics-only | v1 ping/pong and consent | v2 user chat, v1 CHAT_TEXT or silent downgrade → prohibited |

**Prototype/release gap:** v1 and v2 currently use different listener ownership rather than a completed version dispatcher on one port; no automatic v1 fallback is allowed. Pairing control currently correlates `requestId`, `transcriptHash` and conversationId and does not send redundant standalone `localKeyHash`/`remoteKeyHash` fields because the transcript already binds both. `CHAT_ERROR` is defined in the codec but not fully supported as a LiveChatService incoming event. Those mismatches are tracked, not claimed interoperable.

## 9. Test matrix (partially executed)

1. M1 v1 diagnostics still work; v1 CHAT_TEXT rejected; v1-only peer does not silently receive v2 content; no downgrade on mismatch.
2. First pair: valid self-signed certificates, TLS mutual possession, equal SAS on both ends, both humans confirm; one rejects or uses wrong code; no pre-pair content.
3. Stored pin survives process restart, message text/ACK/read not persisted; key changed/expired/revoked, certificate with same SPKI renewed, corrupt DPAPI and another Windows user.
4. Two authenticated JVMs exchange Unicode/emoji A↔B, ACK and READ for exact message, wrong peer and duplicate display names; timestamps render with local timezone.
5. Read negative cases: preview/background/offscreen/other chat/no 250ms visible event, bad readEventId/sessionId, unknown message, duplicate/out-of-order, read receipt before ACK; only valid view event marks READ.
6. Invalid frames, UTF-8, duplicate JSON, oversized text, malicious pre-TLS payload, close midframe, slowloris/half-open and queue flood; no UI EDT stall.
7. Manual offline draft/send/retry, close app no messages/draft/receipt restored, no auto reconnect, no offline read-receipt replay.
8. FE Live uses real transport and Demo uses MockChatService only; login password not peer auth.
9. **Two distinct Windows 11 x64 LAN computers with no Internet**, real IPv4 + authorized firewall, human Accept, SAS, bidirectional chat and view receipts. Record proof on both machines.

**Gate:** AC-03-A…L and Constitution I–VII not complete until all critical tests, real two-PC evidence and M1 Gate 1 proof. The deterministic vectors verify representation only; this is **not proof of production interoperability or security**.
