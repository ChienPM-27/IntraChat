# Proposed wire contract: GROUP_V1 over explicit authenticated group profile

**Date:** 2026-11-22 · **Status:** DESIGN / NOT APPROVED / NOT IMPLEMENTED
**Tracks:** Spec 004 T006–T007, [research](../research.md), [data model](../data-model.md), [plan](../plan.md), CL-04-01…06.

## 1. Security and version boundary

**Legacy facts:** `ChatV2BootstrapCodec` accepts prefaceVersion exactly 2, capability array exactly one item `CHAT_TLS13_V2`; `ChatV2Codec` accepts protocolVersion 2/destination `PEER` and a strict list of P2P types. M1 v1 is plaintext diagnostics. **Neither receives Group user data.**

**Proposed new secure-only profile:** `CHAT_V3_GROUP` uses framed strict UTF-8 JSON prefaceVersion **3**, explicit bilateral capabilities `CHAT_TLS13_V2` and `GROUP_V1`, then the same mandatory human inbound consent, mutual TLS1.3 and two-sided SAS/peer pin + READY **with chosen profile and capability transcript included in the authenticated pairing hash**. Group application frames require that both sides positively negotiated `GROUP_V1`; old peers do not receive these frames.

**TCP listener impact:** A version-aware dispatcher on the manually bound LAN endpoint is required. Current `SecureChatCoordinator` v2-only listener cannot transparently share that bind with M1 v1. The dispatcher must route on *validated bounded preface only* and keep v1 diagnostics data-free. An old v2 peer accepts v2 only; new v3 is not sent to it unless user explicitly selected Group-capable pairing (or capability was otherwise verified). Unsupported versions/negotiation failure reject, **not automatic retry to v1 plaintext**.

**No pre-TLS user data:** v3 preface length <=16,384 bytes, no text/group membership/store data; only HELLO/ACCEPT/REJECT negotiation metadata. Before mutual TLS + verified fingerprint + READY, no Group or Chat user payload. No TOFU, trust-all TLS or storing unknown public keys as authoritative group owners.

### 1.1. CHAT_V3_GROUP preface (JSON, strict)

- Frame = existing `FrameCodec` 4-byte unsigned big-endian length + bytes; 1..16,384 byte cap for bootstrap.
- `CHAT_HELLO` required exact fields: `prefaceVersion:3`, `type:"CHAT_HELLO"`, `requestId:UUID`, `sender:{deviceId:UUID,displayName:string<=128}`, `targetDeviceId` (zero sentinel only on first contact), `senderNonce:base64(32 random bytes)`, `capabilities:["CHAT_TLS13_V2","GROUP_V1"]` in **fixed order**.
- `CHAT_ACCEPT` exact required: `prefaceVersion:3`, `type:"CHAT_ACCEPT"`, `requestId` echo, `sender` with receiver deviceId/displayName, `receiverNonce:base64(32 random bytes)`, `selectedCapabilities:["CHAT_TLS13_V2","GROUP_V1"]` in fixed order. `CHAT_REJECT` exact `prefaceVersion,type,requestId,errorCode`.
- Reject unknown/duplicate fields, repeated JSON keys, any other capability ordering, invalid UUID/nonce/UTF-8, unrecognized version, trailing bytes/tokens and oversized content. Reject without user text echo.
- **Authenticated binding after TLS:** New `PAIR_CONFIRM_V3` and `PAIR_READY_V3` messages MUST bind requestId, both nonces, both device IDs + SPKI fingerprints, selected `CHAT_V3_GROUP` profile and exact ordered capabilities into a canonical transcript SHA-256. Until both people confirm real GUI SAS/fingerprint and both sides READY, `GROUP_V1` is forbidden. These are **new types, never silently inserted into legacy v2 enum/decoder**.

**Proposed V3 transcript byte layout** (before protected `PAIR_CONFIRM_V3`): u32BE length + ASCII `IntraChat-PAIR-V3-GROUP`; u32BE `80` + sorted peer tuple #1; u32BE `80` + sorted peer tuple #2, where each tuple is exactly `deviceId:16B raw RFC-4122 UUID | SPKI-SHA256:32B | localNonce:32B`. Sort the two tuples ascending unsigned bytewise, **independent of initiator direction**. Append u16BE length + ASCII `CHAT_V3_GROUP`; append u8 `2`, then [u8 ASCII length + `CHAT_TLS13_V2`], then [u8 ASCII length + `GROUP_V1`] in that exact order; append `requestId:16B`. Compute SHA256 of all preceding bytes. SAS is first **64 bits** hex, shown in four 4-hex-digit groups; peer confirmation includes that digest (not caller-chosen SAS).

**Test-only reference vector:** peer A deviceId `aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa` with SPKI bytes 00..1F and nonce bytes 40..5F; peer B deviceId `bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb` with SPKI bytes 20..3F and nonce bytes 60..7F; requestId `dddddddd-dddd-4ddd-8ddd-dddddddddddd`; canonical v3 transcript **250 bytes**; SHA256 `3B4952931FF836E84EA4F21BF167BFC4DB78B8077BC114F931D3632C51388959`; displayed SAS `3B49-5293-1FF8-36E8`. Calculated with Windows PowerShell explicit network-order UUIDs, big-endian integer prefix and SHA256; a future independent Java21 test must reproduce it. Incorrect profile/capability/nonces/order gives mismatched digest, **never downgrade automatically**.

**Protected confirmation contract:** `PAIR_CONFIRM_V3` carries exactly `requestId`, `transcriptHash` (32B uppercase/lowercase hex canonicalized by strict decoder), `confirmationId` unique UUID; `PAIR_READY_V3` carries exactly `requestId`, `transcriptHash`. The enclosing secure session must bind `sessionId=requestId`, actual peer sender and destination. All proof types require mutual TLS, verified certificate and correct pairing state; `GROUP_V1` is enabled only after two-sided READY. **The v2 pair confirmation format remains unchanged for old v2 peers.**
- **Interop expectation:** v1 listener rejects Group; legacy v2 decoder rejects v3 (not send group); new v3 correctly routes; no silent fallback or auto reconnect. Define transcript independent vectors before source changes; reviewer must approve v3 negotiation's replay/downgrade safety.

## 2. Post-READY application Group V1 envelope

New group frame on mutually verified v3 session only, 4-byte frame length + strict UTF-8 JSON 1..65,536 bytes. Required root fields (no additional root keys):

```json
{
  "protocolVersion": 3,
  "feature": "GROUP_V1",
  "messageId": "dddddddd-dddd-4ddd-8ddd-dddddddddddd",
  "type": "GROUP_TEXT",
  "sessionId": "12345678-1234-4234-8234-123456789abc",
  "sender": {"deviceId": "aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa"},
  "destination": {"kind": "PEER", "id": "bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb"},
  "groupId": "11111111-1111-4111-8111-111111111111",
  "membershipRevision": 1,
  "sentAtUTC": "2026-11-22T12:00:00Z",
  "payload": {
    "text": "Xin chao, nhom!",
    "ownerGrant": "<base64 owner-signed canonical ticket + signature>"
  }
}
```

**Meaning:** Even GROUP_TEXT is sent to **one PEER destination on one direct TLS link**, not to a multicast address. Root `sender.deviceId`, `destination.id` and `sessionId` MUST exactly match verified TLS session identities and pairing state. Root `groupId` is a separate UUID namespace/context from `conversationId` P2P. Zero IDs reserved except unknown target preface sentinel. `membershipRevision` is integer 0 only in INVITE negotiation, positive 1..2^63-1 for committed group content/control. `sentAtUTC` must be a valid UTC ISO-8601 Z date; remote time is presentation metadata, **not** a source of authorization.

### 2.1. Message types and exact payload keys (conceptual schema)

| Type | Required `payload` keys | Authorization behavior |
| --- | --- | --- |
| `GROUP_INVITE` | `inviteId`, `ownerId`, `ownerSpkiSha256`, `title`, `expiresAtUTC`, `proposalHash` | Only authenticated owner to verified intended peer; recipient consent UI Accept required |
| `GROUP_INVITE_DECISION` | `inviteId`, `decision` (ACCEPT/REJECT), `proposalHash` | Authenticated invitee→owner; correlate exact invite session/peer/timeout |
| `GROUP_ROSTER_COMMIT` | `ownerId`, `rosterCanonicalBase64`, `ownerSignatureBase64` | Verify signed bytes, owner pin, groupId/revision and previousHash before DPAPI apply |
| `GROUP_ROSTER_ACK` | `rosterHash`, `result` (APPLIED/REJECTED), `commitEventId` | Only member who applied signed committed roster via secure store |
| `GROUP_LEAVE_REQUEST` | `requestId`, `baseRevision` | Authenticated active member→owner; member locally suspends own sends |
| `GROUP_GRANT_REQUEST` | `originalMessageId`, `senderId`, `rosterHash`, `recipientIds` | Sender→owner over protected link, **no message text**; owner checks exact current membership |
| `GROUP_SEND_GRANT` | `grantCanonicalBase64`, `ownerSignatureBase64` | Owner→requesting sender, signed exact recipients/message ID/revision/expiry |
| `GROUP_TEXT` | `text`, `grantCanonicalBase64`, `ownerSignatureBase64` | Receiver checks owner ticket + TLS sender/session + local roster before inserting RAM/ACK |
| `GROUP_DELIVERY_ACK` | `originalMessageId`, `acceptedAtUTC`, `result:ACCEPTED` | Recipient app only after valid RAM receipt; cannot mark another recipient delivered |
| `GROUP_READ_RECEIPT` | `originalMessageId`, `readEventId`, `viewedAtUTC` | Viewer app only after foreground selected-group row visible; exact group/peer/session |
| `GROUP_ERROR` | `originalMessageId`, `errorCode` | Bounded sanitized error; no echo original content |

Fields inside payload not listed for that type MUST reject, with explicit bounded values/arrays; no permissive `Map<String,Object>` trusted downstream without schema validation. Invites and roster may be at revision 0/1 respectively, as defined in canonical types; **no unsigned invite establishes membership**. GROUP_CAPABILITIES belongs to preface/pair transcript, not an unauthenticated post-READY text message.

**String/size bounds:** max user text **8,000 Unicode code points and 32,768 UTF-8 bytes**, reject invalid surrogate/UTF-8. Group title 1..60 Unicode code points, NFC, max 240 UTF-8 bytes, reject control characters/blank. Frame ≤65,536 serialized bytes including base64 grant; unknown types/array/object nesting beyond strict schema rejected. Max 5 unique verified members including owner; 1..4 recipients per fanout (for 2–5 total). Signature DER Base64 decoded length must be bounded (e.g. 64..128 bytes), owner SPKI fingerprint exact 32 bytes; owner key algorithm P-256 ECDSA.

## 3. Canonical owner-signing bytes — big-endian binary

**Not raw JSON canonicalization.** Signed bytes have a fixed-order, domain-separated network binary layout; all UUIDs are 16 bytes in RFC-4122 big-endian/network order (**NOT** .NET `Guid.ToByteArray()` mixed-endian). Longs are signed 64-bit big-endian positive; no negative values, no overflow. Fingerprints SHA-256 DER SubjectPublicKeyInfo = 32 raw bytes. Sign canonical bytes using JCA `SHA256withECDSA` P-256 and verify against **pinned owner** public key. The resulting DER ECDSA signature is not deterministic, but the canonical bytes and their SHA256 digest ARE deterministic.

### 3.1. `ROSTER_V1` signed bytes in exact order

1. ASCII bytes `INTRACHAT_GROUP_ROSTER_V1` followed by NUL byte 00 (domain separation).
2. `groupId`: 16B raw UUID; `ownerDeviceId`: 16B; `ownerSpkiSha256`:32B.
3. `revision`: int64 BE, >=1; `previousRosterHash`:32B (all zero for first commit).
4. `titleLen`: uint16 BE; `titleUtf8Nfc`: titleLen bytes.
5. `memberCount`: uint8 (2..5); each member sorted **ascending lexicographic unsigned raw 16B deviceId**: `deviceId:16B`, `spkiFingerprint:32B`, `role:uint8` (1 OWNER, 2 MEMBER).
6. `commitEventId`: 16B UUID; `committedAtEpochMillis`: int64 BE, >=0.
7. No trailing fields/bytes. `rosterHash=SHA-256(entire canonical roster bytes)`. Owner-signed `rosterSignature` stored/transported separately.

Verify exactly one owner entry == ownerDeviceId and fingerprint == ownerSpki; no duplicates by deviceId **or** SPKI; owner key changed/revoked requires new explicit authorization path, not auto transfer.

### 3.2. `GRANT_V1` signed bytes in exact order

1. ASCII bytes `INTRACHAT_GROUP_GRANT_V1` followed by NUL.
2. `groupId:16B`, `messageId:16B`, `ownerDeviceId:16B`, `ownerSpkiSha256:32B`.
3. `revision:int64BE` >=1, `rosterHash:32B`.
4. `senderId:16B`, `recipientCount:uint8` (1..4), `recipientIds` each 16B sorted ascending, no duplicates, sender excluded.
5. `issuedAtEpochMillis:int64BE`, `expiresAtEpochMillis:int64BE`, delta 1..10,000ms; `nonce:16B` cryptographically random.
6. No trailing bytes. `grantHash=SHA-256(entire canonical grant bytes)`; owner ECDSA signature transmitted separately.

Owner verifies recipients equal **the entire current roster minus sender** (not a requested arbitrary subset), with exactly one owner and active Accept+commit; network availability determines which direct send attempts can complete, not the ticket recipient set. Owner never receives text in grant request. Receiver validates its ID in ticket and its local committed revision/hash; an out-of-date local roster cannot accept a newer message until resync. A grant issued before remove may still be in flight, and **no instant global deletion/rollback guarantee** is made.

### 3.3. Reproducible digest vectors (generated using local PowerShell, not code implementation)

Fixture values for test-only contract:
- groupId `11111111-1111-4111-8111-111111111111`
- ownerDeviceId `aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa`; owner SPKI fingerprint raw bytes 00,01,…,1F
- memberDeviceId `bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb`; member SPKI fingerprint raw bytes 20,21,…,3F
- roster revision 1, previous hash = 32 zero bytes, title NFC `Do an` (5 UTF-8 bytes), member records OWNER then MEMBER, roles 01 and 02.
- commitEventId `cccccccc-cccc-4ccc-8ccc-cccccccccccc`; committedAt UTC `2026-11-22T12:00:00Z` = **1795348800000 epoch milliseconds**.
- messageId `dddddddd-dddd-4ddd-8ddd-dddddddddddd`, sender=owner, recipient=member, grant issuedAt=1795348800000, expiresAt=1795348810000, nonce bytes 01,02,…,10.

| Fixture | Canonical length | SHA-256 canonical bytes |
| --- | ---: | --- |
| ROSTER_V1 | **260 bytes** | `FE5AA540B53CE02A64457F0A95BEAEE6274828EC743ACD3598F310C085DF915D` |
| GRANT_V1 (rosterHash above) | **210 bytes** | `0C31739C8BD77B2E44B21BF5054FB343116C48449EAA1BAD3D533B78323BA869` |

**How calculated:** Windows PowerShell built byte lists: ASCII domain + NUL, explicit network-order GUID hex, explicit BE integers and 32B fingerprints; `System.Security.Cryptography.SHA256.Create().ComputeHash()` on the raw canonical byte arrays. **These are draft digest fixtures only**; a separate Java21 test must reproduce both bytes and hashes and a reviewer must validate canonicalization, signatures and transcript vectors before secure Group rollout.

## 4. Version, authorization and negative matrix

| Input | Expected |
| --- | --- |
| Incoming GROUP_TEXT on M1 plaintext v1 | REJECT, no content parsed, no downgrade |
| Group V1 message sent to legacy CHAT_V2-only session | Sender MUST NOT emit; negotiation failure disables Group |
| Unknown v3 preface field/version/capability mismatch | REJECT before protected user bytes |
| Group frame before TLS+pin/SAS two-sided READY | REJECT, no RAM/disk content write |
| Correct verified TLS but sender NOT_MEMBER | REJECT even if groupId guessed |
| Signed roster by wrong SPKI, revision gap/rollback/hash mismatch | REJECT and mark STALE/RESYNC_REQUIRED |
| GROUP_TEXT ticket signed by wrong owner/expired/wrong recipient/nonce reused | REJECT before app ACK |
| Grant valid but owner key revoked locally / owner offline and no new grant | REJECT or Group PAUSED; no optimistic message |
| ACK/READ by another recipient or wrong group/session/revision | REJECT; do not elevate anyone |
| Oversized frame, invalid UTF-8, duplicate keys, trailing tokens, unknown nested field | REJECT bounded; optionally close malicious session safely |
| 6th member attempted, 5th remote TLS beyond global cap, queue exhausted | REJECT explicitly without enabling membership/leak |
| Duplicate valid in-session Group message | Deduplicate in RAM; may ACK same accepted ID without duplicate bubble; no cross-restart exactly-once promise |
| Owner died after grant issuance, remove in progress | No new grant while owner unavailable; already authorized in-flight bytes are residual risk documented, not erased retroactively |

Error code allowlist (not sent with secrets): `GROUP_UNSUPPORTED`, `NOT_VERIFIED`, `NOT_MEMBER`, `OWNER_REQUIRED`, `INVITE_EXPIRED`, `REVISION_STALE`, `SIGNATURE_INVALID`, `GRANT_EXPIRED`, `RECIPIENT_MISMATCH`, `GROUP_CAPACITY`, `QUEUE_CAPACITY`, `STORE_CORRUPT`, `FRAME_INVALID`, `UNCERTAIN`.

## 5. Gate-0 review blockers, not solved by writing contract

1. **Owner revocation race:** Approval of whether short-lived grants and local signed-roster check sufficiently meet confidentiality/security requirement under network partition. Cannot guarantee immediate revoke of bytes already in flight.
2. **Actual v3 pairing transcript and capability downgrade safety:** integrate with strict existing CHAT_V2 bootstrap and M1 dispatcher; independently review exact binary hash input and mock/real TLS identity binding before coding.
3. **Signature canonicalization:** test vectors must be reimplemented in Java21 and independently checked; no private-key leak or signature trust based on claimed ownerId.
4. **Windows storage/rollback:** group metadata persist only with DPAPI owner-only ACL/atomic; cross-user testing requires authorized second user; backup rollback always restarts STALE.
5. **License/CVE/SBOM and privacy:** independent reviewer required before approving new security behavior.

**Contract status: PROPOSED ONLY**, no byte-level Group implementation or interoperability test has run. Gate 0 remains CONDITIONAL/BLOCKED without reviewer signoff.