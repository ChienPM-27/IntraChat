# Network Contract — Group V1 over verified M2 TLS (design draft)

**Contract ID:** `INTRACHAT-GROUP-V1`
**Date:** 2026-11-21
**Status:** **PLANNED / NOT IMPLEMENTED — no frozen interoperability vectors or Group test results**
**Documents:** [Spec](../spec.md) · [Plan](../plan.md) · [Data model](../data-model.md) · [M2 CHAT_V2 contract](../../003-p2p-messaging/contracts/chat-v2.md).

## 1. Versioning, transport and protected capability negotiation

- **Existing M1 diagnostic v1 is unchanged.** Its raw TCP/JSON remains diagnostic-only; `GROUP_*` types are forbidden.
- **Existing CHAT_V2 v2 is unchanged as an independent strict codec.** It uses `protocolVersion=2`, PEER destination, strict type/payload allowlist and verified TLS 1.3 after human approval, SAS and pin. `ChatV2Codec` in current source rejects Group types and protocol v3.
- **Extension:** optional capability token `GROUP_V1` in the existing data-free CHAT_V2 HELLO/ACCEPT preface. Its plaintext form is untrusted; capability must be mutually re-confirmed **inside verified TLS after both PAIR_READY**. Confirmations bind both peers' exact offered capability sets, requestId, sessionId, device IDs/SPKI hashes, preface nonces and version. If peer is old v2/no Group or confirmation fails: **Group disabled**; P2P v2 may remain usable; no silent downgrade.
- **Group wire:** distinct application profile `protocolVersion=3`, `groupProtocolVersion=1`, framed JSON **inside the already verified TLS session** (4-byte unsigned big-endian length, `1..65,536 bytes`). Profile 3 does **not** create an unauthenticated transport or a new public port. Only a dedicated, negotiated `GroupV1Codec` receives Group v3. `destination.kind=PEER` identifies the **current verified hop**, not an invitation to deliver outside membership.
- **Exception:** protected `GROUP_CAPS_CONFIRM`/`GROUP_CAPS_ACK` are allowed only in the narrow capability-negotiating state after M2 READY, with **no** Group payload/roster/text. Other Group types require both protected capability ACKs and a valid current Group roster/run before processing.
- No Group data in pre-TLS HELLO or v1 diagnostics. No Group message in an M2 `CHAT_TEXT` payload. Unknown protocol version/capability, unexpected Group type or fallback attempt: reject with sanitized reason and close Group route, never expose data through legacy path.

### 1.1 Required parsing and validation

Strict UTF-8 decoder REPORT on invalid input, Jackson strict duplicate-key detection, max nesting depth **32**, no trailing tokens, no additional fields, canonical nonzero UUID strings, numeric integer-only epochs/revisions/sequence, UTC ISO-8601 timestamps ending `Z`, bounded collection count and strings. Network input untrusted even on TLS. All Group types are **positive allowlist**; unknown critical field/type/version rejected, unknown noncritical fields **also** rejected in v1 profile (no silent ignore). No Java native deserialization. Frame size checked **before** payload allocation, UTF-8 bytes checked after encode. Max member count **5**, text **≤8,000 Unicode code points AND ≤32,768 UTF-8 bytes** and nonblank, max Group metadata file **128KiB**. Signatures Base64/DER bounded; invalid key algorithm, invalid signature, weak RSA or changed pinned SPKI: reject before UI.

## 2. Group V1 protected envelope fields

Every non-capability Group envelope has the following fields; outer hop identity is authenticated by verified M2 TLS and is **not** itself proof of the message's original author.

| Field | Type | Required / meaning |
| --- | --- | --- |
| `protocolVersion` | integer | Exactly `3` for Group profile |
| `groupProtocolVersion` | integer | Exactly `1`; future incompatible profile requires explicit negotiation |
| `messageId` | canonical nonzero UUID | ID of **this envelope**; not interchangeable with original Group message ID |
| `type` | enum | Exact Group V1 type allowlist in `3 |
| `sessionId` | canonical UUID | Must equal current verified M2 hop sessionId, reject replay from earlier hop |
| `sender.deviceId` | canonical UUID | **Hop sender** bound to actual TLS/pin identity, e.g. A on A→C relay |
| `destination.kind` / `destination.id` | `PEER` / UUID | Recipient of **hop**; destination ID must equal local authenticated device |
| `sentAt` | strict ISO8601 UTC | Display/audit only, not identity or global ordering |
| `group.groupId` | UUID | Logical Group identity, unrelated to P2P conversationId |
| `group.ownerEpoch` | positive integer64 | Current verified owner epoch; monotonic and overflow-safe |
| `group.membershipRevision` | positive integer64 | Owner committed roster revision, not member self-claimed |
| `group.groupRunId` | UUID | Current owner-approved active run, ephemeral; old-run replay rejected |
| `body` | type-specific JSON object | Strict required/allowed fields; bounded |
| `proof` | type-specific signed provenance object | Verify authoritative signer, canonical bytes and group role; outer TLS alone insufficient |

**Capability exceptions:** `GROUP_CAPS_CONFIRM/ACK` omit `group`/`proof` and carry only strict capability fields and mutual authenticated TLS transcript; they never convey a Group roster or private content. Other exceptions (e.g. invitation to a not-yet-member peer) use owner-signed invitation context and Group ID/epoch/revision but no active runId until the invitee joins; decoder uses per-type explicit validation, never generic nullable fields.

**Group identity vs hop destination:** A sender B submitting Group G uses `sender.deviceId=B`, `destination.id=A`, `body.original.originId=B`. A forwarding to C uses `sender.deviceId=A`, `destination.id=C`, unchanged **original signed content B**, owner authenticated relay proof. C must **not** interpret outer hop sender A as origin of the text.

## 3. Type allowlist, gates and required payloads

| Type | Principal body fields | Who signs / gate |
| --- | --- | --- |
| `GROUP_CAPS_CONFIRM` | requestId, sessionId, bothOffersDigest, localSpkiHash, remoteSpkiHash, capabilityId=GROUP_V1, random nonce | Authenticated current M2 hop, after PAIR_READY; no Group content |
| `GROUP_CAPS_ACK` | confirmMessageId, bothOffersDigest, result=ACCEPTED | Same authenticated hop; must match both directions |
| `GROUP_INVITE_PROPOSE` | proposalId, groupId, proposerId, targetId, expiresAtUTC | Current member signature, to owner; not a grant |
| `GROUP_PROPOSAL_DECISION` | proposalId, ownerId, approved boolean, decisionId | Current owner signed; approval still not join |
| `GROUP_INVITE_OFFER` | invitationId, groupId/name, currentOwnerId, inviteeId, nonce, expiresAtUTC, group ownerEpoch/revision, minimal roster identity hints | Owner signed, to exact verified invitee |
| `GROUP_INVITE_RESPONSE` | invitationId, inviteeId, accepted boolean, ownerId, nonceEcho | Exact invitee signed; no rights until owner commit |
| `GROUP_MEMBERSHIP_COMMIT` | eventId, operation JOIN/LEAVE/REMOVE/RENAME/DISBAND, targetId, priorRevision, newRevision, previousDigest, rosterDigest, ownerId | Current owner signed **after durable commit** |
| `GROUP_ROSTER_SYNC` | groupId, ownerId, ownerEpoch, revision, activeRunId, signed canonical ordered roster with each member's deviceId/SPKI hash/role/joinRev | Current owner signed, only to authorized current member or minimized pending invitee |
| `GROUP_TEXT_SUBMIT` | original (immutable object below) and originSignature | Original sender signed; current member → owner |
| `GROUP_TEXT_RELAY` | original + same originSignature, ownerSequence, recipientSnapshotDigest, relayCommitId and ownerRelaySignature | Current owner signed routing proof → eligible recipients |
| `GROUP_OWNER_ACCEPT` | originalMessageId, originId, recipientSnapshotDigest, ownerSequence, acceptedAtUTC | Owner signed, means **only** owner pipeline accepted |
| `GROUP_DELIVERY_ACK` | originalMessageId, originalOriginId, recipientId, originalContentHash, recipientSessionId, acceptedAtUTC, receiptId | **Actual recipient** signed; owner relays unchanged signed receipt |
| `GROUP_READ_RECEIPT` | originalMessageId, originalOriginId, viewerId, originalContentHash, readEventId, viewerSessionId, viewedAtUTC | **Actual viewer** signed after foreground viewport event; owner relays unchanged |
| `GROUP_LEAVE_REQUEST` | eventId, departingMemberId, reasonCode | Exact member signed, owner must commit removal |
| `GROUP_REMOVE_COMMIT` | eventId, removedId, beforeRevision, afterRevision, rosterDigest | Current owner signed, after **durable** revision commit |
| `GROUP_DISBAND_COMMIT` | eventId, groupId, newRevision, ownerId, tombstoneDigest | Current owner signed, terminal |
| `GROUP_HANDOFF_PREPARE` | transferId, A, B, epoch e, proposedEpoch e+1, currentRevision, rosterDigest, expiresAtUTC | A current owner signed after Group quiesce |
| `GROUP_HANDOFF_ACCEPT` | transferId, A/B, e/e+1, rosterDigest, acceptedAtUTC | B verified member signed and locally PREPARED |
| `GROUP_HANDOFF_COMMIT` | transferId, A/B, e/e+1, rosterDigest, oldOwnerRetiredDigest, A signature + B accept signature | A signed **and retired durably** before send; no longer owner e |
| `GROUP_HANDOFF_ACTIVATED` | transferId, newOwnerId=B, newEpoch=e+1, newRunId, rosterDigest, commitDigest | B signed after durable active persistence |
| `GROUP_ERROR` | bounded errorCode, optional relatedEnvelopeId/groupId | Authenticated hop peer; sanitize; never raw content |

**Envelope size note:** JSON text/Base64 signatures can cause a valid 32KiB message to exceed 65,536-byte total (especially roster/proof overhead). Encoder **must** check final framed bytes and fail before sending; no silent truncation, no compression of untrusted variable text. Never fan-out partial signed content. Type-specific max fields/body and signer count need tests.

### 3.1 Immutable original Group text object

`original` MUST contain exactly: `originalMessageId` UUID; `groupId` UUID; `originId` UUID; `ownerEpoch` positive int64; `membershipRevision` positive int64; `groupRunId` UUID; `sentAtUTC` UTC; `text` strict Unicode text; `contentHash` SHA256 bytes represented as bounded lowercase hex or Base64 (freeze representation before code). Signed origin bytes include *all* these fields (including text hash and text body under canonical encoding) so relay cannot change content, ID, timestamp or sender. Same originalMessageId with differing immutable fields => `MESSAGE_CONFLICT`, close offending Group route; duplicate exact original in dedup window returns idempotent owner acceptance (if first actually accepted).

### 3.2 Owner relay proof

Relay proof MUST bind `originalMessageId`, owner ID and SPKI hash, groupId, epoch/revision/runId, ownerSequence, **immutable recipient snapshot digest**, current intended hop recipient and relayCommitId. An outsider cannot be added by changing outer `destination` without invalidating owner proof. Before every not-yet-started fan-out, owner re-checks **latest committed roster** and cancels removed recipients; messages already sent before commit cannot be recalled. Receiver checks it belongs to signed recipient snapshot/current permitted membership. Owner must not re-sign original text as another member.

### 3.3 Signed ACK and READ

- Each recipient uses its own pinned P-256 identity private key to sign per-recipient `GROUP_DELIVERY_ACK` and `GROUP_READ_RECEIPT` with original Group ID/origin/message/content hash, epoch/revision/runId, viewer/recipient ID, receiptId, active recipient hop sessionId and UTC. Group source receives receipt via owner but verifies **member signature independently**.
- A `GROUP_OWNER_ACCEPT` from A **never** upgrades B's status for C. A cannot claim C READ just by forwarding text or inserting a boolean.
- `READ` requires Live selected Group, foreground active Swing window, bubble actually showing/intersecting viewport for **≥250ms**, not preview, notification, inactive window or Demo. It is an application-view event, not proof a human cognitively read the text.
- Valid READ from C may precede C's delivery ACK at B, promoting **C only** directly to READ; late ACK cannot reduce. Duplicate receiptId/readEventId idempotent, wrong viewer/session/group/origin/content hash rejected.
- If viewed while offline or owner disappears before receipt, no durable READ outbox, so sender does not see READ until a valid receipt is **actually observed**. No replay of old receipts after restart.

## 4. Canonical signing and verification (DESIGN PROFILE — freeze vectors before implementation)

**Problem:** JSON key order, UTF-8 escaping, numeric render and Java map order are not canonical. **Never sign raw JSON string or `Map.toString()`.**

- **Keys:** reuse M2 device ECDSA **P-256** long-term private/public key, `SHA256withECDSA` signature. Bind key via deviceId→SPKI SHA-256 **independently verified by recipient**. A signed owner roster may distribute candidate public keys but **cannot replace local peer verification**. Signed identity keys and metadata are separate from TLS session secrecy.
- **Domain separators:** ASCII exact labels e.g. `IntraChat-GROUP-V1-ORIGIN`, `IntraChat-GROUP-V1-OWNER-EVENT`, `IntraChat-GROUP-V1-RELAY`, `IntraChat-GROUP-V1-ACK`, `IntraChat-GROUP-V1-READ`, `IntraChat-GROUP-V1-HANDOFF`. Each signed message uses a *different* domain and a known **type tag**; signatures cannot be replayed across types/roles.
- **Canonical binary encoder proposal:** signature input = 4-byte big-endian label byte length + ASCII label + 4-byte big-endian `groupProtocolVersion=1` + 4-byte type-tag ID + fixed field order specified per event class; each variable UTF-8 field represented as unsigned 4-byte length + exact UTF-8 (strict scalar validation, **no automatic normalization**); UUID as RFC4122 16 network-order bytes; SHA256 hashes as exactly 32 raw bytes; numbers as unsigned 64-bit big-endian, reject negative/overflow; ordered roster as sorted unsigned lexical member UUID bytes followed by pinned SPKI bytes/role/revisions. Missing field/value type/additional signed field fails canonicalization.
- A signature is DER ECDSA P-256 bytes encoded Base64 in bounded `proof` with mandatory `signerDeviceId`, `signerSpkiSha256`, `algorithm=ECDSA_P256_SHA256`, `signature`; verify algorithm, signer pin, role, group/epoch/revision/run and signed data before acceptance. Reject high/low-S malleability if deterministic signature equality is relied on; **dedup must rely on semantic identity/content hash**, not DER signature bytes (ECDSA signing is not deterministic by default).
- **Vectors are NOT YET GENERATED/VERIFIED.** Before implementation freeze exact byte layouts and publish golden examples for Unicode text, roster sorting, SEND/ACK/READ/HANDOFF and signature mutation. Use Java21 independent test calculations; no fabricated hash/sample signature in this design-only document.

## 5. Explicit state/authorization gates by phase

| Gate | Allowed messages | Forbidden behavior |
| --- | --- | --- |
| Raw v1 diagnostics | Existing v1 HELLO/DIAGNOSTIC only | All Group types, roster, text, receipts |
| v2 plaintext bootstrap (max 16KiB) | Existing CHAT_HELLO/CHAT_ACCEPT/REJECT with optional untrusted capability token | Group data/keys/history; claiming authorization |
| TLS established, M2 not READY | M2 PAIR_CONFIRM/PAIR_READY only as M2 contract allows | Group invitation, roster, text or receipts |
| M2 VERIFIED+READY, Group caps negotiating | `GROUP_CAPS_CONFIRM/ACK` only | Any Group ID/roster/content before both confirmations |
| Group caps accepted but no current Group owner/roster sync | Minimal signed Group invite to an exact candidate or signed roster sync for authorized current member | Text/receipt from unjoined/outdated/revoked member; roster leak to outsider |
| Group ACTIVE, stable epoch/revision/run | Allowed current Group types with signatures/ACL, limits and owner route | Unauthorized sender, stale event, wrong signer, wrong destination, absent key |
| Group TRANSFERRING | Handoff PREPARE/ACCEPT/COMMIT/ACTIVATED; bounded errors/receipt on already accepted frames according to safe cut-off | New text acceptance/fan-out after owner retire; dual active owner |
| Group PAUSED | Manual reconnect/resync/handoff-proof recovery, sanitized errors | Auto fallback/auto replay/auto owner election/READ from old run |
| Group DISBANDED / member removed | Error or explicit re-invitation through fresh authorized Group process | Any new Group text/READ/owner event based on stale membership |

**Invitation exception must not leak full roster:** A candidate who is not yet a member can see only owner identity, Group name/groupId, approximate member count and fingerprints necessary for consent, all over verified TLS; full roster or content only after join durable commit (CL-04-02 and privacy). If owner key/Group context cannot be verified at that moment, reject invitation.

### 5.1 Owner-hand-off signatures and commits

1. `HANDOFF_PREPARE` signed by old owner A binds transferId, groupId, A/B deviceIds+pinned hashes, epoch e→e+1, current revision, rosterDigest and expiry; A stops accepting new messages.
2. Successor B explicitly accepts via GUI and signs `HANDOFF_ACCEPT` after it persists PREPARED metadata. An old member's spoofed consent is invalid.
3. A **durably persists RETIRED at epoch e** together with signed e+1 COMMIT proof **before sending** `HANDOFF_COMMIT`. After this point A has **no authority to resume owner e**, even if B never receives COMMIT.
4. B verifies A signed COMMIT and its own accepted transfer, persists owner B/e+1/commit proof before signing `HANDOFF_ACTIVATED`. Other members trust B only after verifying **A+B pinned signatures**, epoch continuity and recording owner change; otherwise Group PAUSED.
5. Timeout/crash after step 3 is **ambiguous** and may cause availability loss. Recovery via **manual retransmission of the existing signed COMMIT** from A or reviewed manual repair; no re-elections. Durable store rollback/inconsistency requires current signed owner sync and fail-closed repair; DPAPI does not solve distributed consensus.

**Key owner-event authorization:** only owner at epoch e and its pinned key may sign ordinary roster commit at e; only a dual-authorized transfer to e+1 changes current owner. A numeric e+2 self-claim from nonowner or unauthenticated channel is rejected.

## 6. Routing, ack/read lifecycle and malformed payloads

### 6.1 Example flow A owner, B source, C recipient

~~~text
B (origin)         A (current owner/coordinator)            C (recipient)
   | GROUP_TEXT_SUBMIT + signed original |                      |
   |------------------------------------>| verify B + roster    |
   |<------ GROUP_OWNER_ACCEPT ----------|                      |
   |                                     | GROUP_TEXT_RELAY + A/B proofs
   |                                     |--------------------->| verify A and B signatures
   |                                     |<-- signed C APP ACK --| accepted into Group RAM
   |<--------- signed C ACK relay -------|                      |
   |                                     |<-- signed C READ -----| after REAL viewport event
   |<--------- signed C READ relay ------|                      |
~~~

If C offline, A marks C offline/uncertain, B sees per-recipient state without blocking A's local/other recipients. If A offline, Group PAUSED, B keeps draft RAM; no send via B↔C alternative or raw v1. No file/media or History frames in Group v1.

### 6.2 Dedup, ordering, out-of-order ACK and group rotation

- Owner acceptance serialized by Group for local `ownerSequence`; per-recipient delivery may be out of order due to different sessions/queuing. Sender timestamp UTC display only; no cross-run total order.
- Dedup key: `groupId + groupRunId + originDeviceId + originalMessageId` and **canonical contentHash**. In RAM cache max 1,024 IDs / 30 minutes; same key/hash accepted once, duplicate ACK only if previously app-accepted; same key with different immutable content = reject.
- Status store keyed `groupId + originalMessageId + recipientId + groupRunId`. Receipt must reference exact original origin/content/epoch/revision and actual signed viewer, and current owner route. Old run/session IDs rejected. ACK duplication does not double count.
- Recipient joins at revision r+1: **no** transcript backfill or read of messages sent at r. Member removed at owner durable commit r+2: no new Group content queued/sent to it afterwards; already left network frames are not retractable.
- On owner restart/handoff change `groupRunId`: old message content/receipt RAM is gone; message replay from prior run rejected; no auto resend/read queue. On peer reconnect without owner run rotation, dedup only within bounded window, so **not exactly once forever**.

## 7. Error codes, closure policy and privacy

| ErrorCode | Cause | Required behavior |
| --- | --- | --- |
| `GROUP_CAPABILITY_MISMATCH` / `GROUP_VERSION_UNSUPPORTED` | v2-only peer, protected transcript mismatch, unknown Group profile | Disable Group/close Group route; never fallback plaintext/P2P-as-Group |
| `GROUP_TLS_NOT_VERIFIED` / `GROUP_PIN_MISMATCH` | Not M2 READY/pinned/SAS or changed peer key | Fail closed and require first-pair/re-pair human confirmation |
| `GROUP_ORIGIN_KEY_UNKNOWN` | Recipient lacks independently pinned origin member key | Do not display as verified; verify/pair before Group send/receive |
| `GROUP_NOT_MEMBER` / `GROUP_NOT_OWNER` | Unauthorized actor or outsider knowing groupId | No roster/text leak; sanitize response, never echo private fields |
| `GROUP_INVITE_PENDING` / `GROUP_INVITE_REJECTED` / `GROUP_INVITE_EXPIRED` | Pending owner approval or invitee consent, rejection/expiry | No membership/revision commit, release pending quota |
| `GROUP_CAPACITY` / `GROUP_SESSION_CAPACITY` | >5 members or shared >4 M2 active peers, queue/pending exceed | Bounded rejection + UI status, no stealth eviction of P2P |
| `GROUP_EPOCH_STALE` / `GROUP_REVISION_MISMATCH` | Stale owner/member state, pending membership update | PAUSED until signed current owner sync; no unauthorized text |
| `GROUP_SIGNATURE_INVALID` / `GROUP_RELAY_INVALID` | Fake origin, viewer/owner or modified payload/route | Reject frame, close suspicious route if malicious; no UI content |
| `GROUP_MESSAGE_CONFLICT` / `GROUP_RECEIPT_INVALID` | Reused ID with different content, forged/wrong session ACK/READ | No status upgrade; reject/ignore duplicates safely |
| `GROUP_OWNER_OFFLINE` / `GROUP_HANDOFF_INCOMPLETE` | Coordinator missing or partial commit | PAUSED, manual reconnect/recovery only |
| `GROUP_STORE_LOCKED` / `GROUP_STORE_CORRUPT` | Wrong DPAPI Windows user, ACL/corrupt/atomic write unavailable | Fail closed; do not reset/overwrite metadata automatically |
| `GROUP_FRAME_INVALID` / `GROUP_JSON_INVALID` / `GROUP_UTF8_INVALID` | Length/JSON/UTF-8/UUID/type/field violation | Fail closed before unbounded memory; bounded sanitized error |
| `GROUP_SEND_UNCERTAIN` / `GROUP_ACK_TIMEOUT` | A/C disconnect after possible send, receipt missing | Truthful per-recipient UNCERTAIN, no auto retry |
| `GROUP_SHUTDOWN_CANCELLED` | App or group closing | Stop I/O, clear Group RAM and timers; metadata protected remains per policy |

Errors are **codes only**, not raw JSON payloads, roster, secrets, text, fingerprints or SAS; UI may show user-action guidance, not cryptographic material. Never log actual Group text/metadata private or private keys. Access to receiver file system limited to DPAPI-protected metadata under current Windows user; no network command can specify a local path.

## 8. Tests and required golden vectors before code approval

**Deterministic vector suite to produce (NOT YET PRODUCED):**

1. Canonical binary serialization of a synthetic Group UUID, sorted 3-member roster and signed JOIN revision, independent Java21 implementation; two orders of JSON keys yield **same canonical signed bytes**.
2. B→A→C origin immutable Unicode text (“Xin chào 👩🏽‍💻”), owner relay signature and C verification with B pinned key; one altered byte in text/groupId/epoch/revision/recipient snapshot fails.
3. C authenticated signed delivery ACK and foreground READ, B's per-recipient status correct; viewer spoof/owner-fabricated C receipt/wrong session rejected.
4. Handoff A→B e→e+1 with signed A PREPARE, B ACCEPT, A RETIRED/COMMIT and B ACTIVATED; mutation of epoch, signer, roster or transferId rejected.
5. v2-only peer sees Group disabled, v1 accepts diagnostic only, malformed Group v3/unknown critical flag rejected; under TLS no plaintext downgrade.
6. Group DPAPI metadata read after restart, ACL/corrupt file and wrong Windows user fail closed; no chat text/draft/receipt in file or log.
7. Three separate JVM processes + optional D outsider and real Windows LAN evidence, consent and group key verification recorded without personal sensitive data.
8. Max frame, max 5 members, 4 active links, 8 groups, owner/receipt quotas, slow link, memory 8MiB, repeated duplicates and EDT p95 under load.

**Status:** Every Group V1 network/cryptographic example above is *design*, not a working packet capture or test result. Before implementation freeze exact canonical byte layout/type tags, complete golden vectors and review them for cross-peer interoperability. Independent security review of signature/handoff/DPAPI is release-blocking.
