# Wire Contract Draft — HISTORY_V1 on verified M2 TLS

**Phase:** 005-chat-history · **Product decisions:** **CL-05-01…10 USER CONFIRMED (all A)** · **Wire format/proof vectors:** **DRAFT, NOT CRYPTO-APPROVED/IMPLEMENTED**
**Source:** [spec.md](../spec.md) · [plan.md](../plan.md) · [data-model.md](../data-model.md) · [Group V1](../../004-group-chat/contracts/group-v1.md) · [M2 CHAT_V2](../../003-p2p-messaging/contracts/chat-v2.md).

## 1. Separation, authentication and compatibility

**Transport:** reuse existing human-approved, mutually verified TLS 1.3 M2 session with verified local and remote deviceId and independently pinned SPKI. No cloud, new port, raw diagnostic v1 or auto-connect. All History frames use existing 4-byte big-endian length prefix **max 65,536 bytes**, inside TLS. The strict M2 `ChatV2Codec` remains v2 only; the strict Group app profile v3 remains separate. Proposed `HISTORY_V1` has `protocolVersion=4`, `historyProtocolVersion=1` and **separate decoder**; version numbering is a **draft proposal** and must be frozen before code.

**Capabilities:** current M2 preface allows just `CHAT_TLS13_V2`. Extend optional data-free `HISTORY_V1` offer only after compatibility review. Preface can be altered by a MITM and is **never an authorization grant**. After M2 both `PAIR_READY`, both peers exchange protected nonce-bound `HISTORY_CAPS_OFFER` + `HISTORY_CAPS_CONFIRM` binding raw preface offers, verified device IDs and SPKI hashes, pairing sessionId/requestId and transcript. Only exact mutual confirmation sets `historyProfileReady=true`. A v2-only peer continues private v2 chat but cannot receive History V1. Unknown type/version/prepared capability mismatch **fails closed**, not downgrade to plaintext M1.

**Binding confirmed CL-05-04/05/06/08:** Sync is **manual per conversation**, only two verified device IDs for P2P, only current owner for Group, and **no ACK/READ receipt persistence or propagation**. The History APPLY_ACK confirms the *copy*, not application delivery. **Other gates:** before any `SYNC_OPEN`/manifest, verify opted-in local policy and exact conversation/group scope; M2 pin alone does not authorize all histories. On Group, only current signed owner proof may serve the user-confirmed CL-05-06 A. If Group 004 runtime unavailable, all Group History types are disabled.

## 2. Proposed JSON envelope (inside TLS only)

~~~json
{
  "protocolVersion": 4,
  "historyProtocolVersion": 1,
  "type": "HISTORY_SYNC_OPEN",
  "messageId": "11111111-1111-4111-8111-111111111111",
  "requestId": "22222222-2222-4222-8222-222222222222",
  "senderDeviceId": "33333333-3333-4333-8333-333333333333",
  "destinationDeviceId": "44444444-4444-4444-8444-444444444444",
  "sessionBinding": "base64url-authenticated-session-transcript-hash",
  "sentAtUtc": "2026-11-21T14:00:00Z",
  "scope": {
    "kind": "P2P",
    "scopeId": "55555555-5555-4555-8555-555555555555",
    "membershipEpoch": null,
    "membershipRevision": null
  },
  "body": {
    "syncRunId": "66666666-6666-4666-8666-666666666666",
    "requestedMaxEvents": 20,
    "capabilityHash": "base64url-protected-confirmation-hash"
  }
}
~~~

**This is an illustrative example, not a tested golden vector.** All 10 user-level CL decisions are CONFIRMED; numeric wire syntax/version and crypto canonicalization still need independent technical freeze and tests. A Group scope sets `kind=GROUP`, `scopeId=groupId` and signed current-owner epoch/revision proof. Encode/null field policy, binary canonical signature input, base64url padding and strict JSON schema must be frozen in TDD wire tests. No unrestricted nested maps or peer-supplied disk path.

### Envelope invariants

- Sender/destination exactly match authenticated hop verified device IDs and actual intended recipient; never authorize by UUID/self-declared alias alone.
- `requestId` and `syncRunId` nonzero UUIDs; sessionBinding includes verified pairing transcript and current protected History capability handshake, not a static untrusted string.
- `scope.kind` P2P or GROUP, with scope-specific authorization and bounded membership revision; no cross-scope reuse of cursor/event ID.
- All timestamps strict UTC ISO-8601 for source display; cannot decide authorization/causal order on timestamp alone.
- Max encoded frame **65,536** bytes. Suggested History `DELTA` cap **20 events AND 48 KiB** encoded, manifest window **256 IDs**, max UTF-8 content follows original M2/004 text limits (8,000 code points AND ≤32KiB per text event), plus the tighter frame bound.
- Full JSON parse must reject duplicate property names, trailing tokens, invalid UTF-8/unpaired surrogate, NaN/overflow/invalid enum/unknown fields/oversized arrays, noncanonical ID/time and extra envelope types.
- Strict allowlist; no generic `payload` raw deserialization or Java `ObjectInputStream`.

## 3. History V1 type allowlist

| Type | Actor → receiver | Minimum body | Precondition/effect |
| --- | --- | --- | --- |
| `HISTORY_CAPS_OFFER` | each verified M2 peer | own/raw preface offer digest, verified IDs/SPKI, session nonces, version | only protected post-PAIR_READY, no private History |
| `HISTORY_CAPS_CONFIRM` | each verified M2 peer | symmetric hash of both authenticated offers and intended profile | mutual exact match before allowing sync |
| `HISTORY_SYNC_OPEN` | authorized requester→source | runId, one scope, requested bounds, signed Group owner proof where relevant | fail closed before revealing any data |
| `HISTORY_SYNC_GRANT` | source→requester | scoped run nonce, approved max, eligibility fence, ephemeral cursor binding | explicit consent; cannot grant disallowed scope |
| `HISTORY_SYNC_DENY` | source→requester | sanitized generic code | same response shape regardless of guessed conversation existence |
| `HISTORY_SUMMARY` | source→requester | bounded partition digest/ID manifest (≤256 IDs), next scoped cursor | share only eligible events, only after grant |
| `HISTORY_HAVE_NEED` | both ways | requested bounded event IDs + expected canonical digests | no clock-only max timestamp |
| `HISTORY_DELTA` | owner/original signer→requester | ≤20 signed events, ≤48KiB encoded, batch digest, owner permission proof if Group | validate source+member eligibility+local tombstones BEFORE commit |
| `HISTORY_APPLY_ACK` | durable receiver→source | applied IDs/hash and verified committed batch position | confirms **stored copy only**, not chat delivery/read |
| `HISTORY_SYNC_DONE` | both | verified scoped reconciliation partition digest/proof | SYNCED only for exactly that scope/run |
| `HISTORY_CANCEL` | either | runId and sanitized reason code | stop, release bounded buffers/workers |
| `HISTORY_SYNC_ERROR` | either | generic error category/retry policy | no private chat/roster or key info |

**Additional source-seal mechanism:** `HISTORY_EVENT_SEAL` is a proposed separate protected correlation message emitted by **original sender** around live message capture. It creates an immutable detached origin proof for future sync; because M2 v2 messages currently lack this signature, code must **not** claim legacy unsigned copies have durable origin signatures. Exact seal timing, original message delivery eligibility, signature canonical bytes and compatibility require independent review. No offline draft is made sendable by a seal.

## 4. Signed HistoryEvent payload (candidate)

| Field | Type/rule | Purpose |
| --- | --- | --- |
| `scopeKind/scopeId` | verified exact pair or group UUID | prevent confused-deputy export into other conversations |
| `eventId` | nonzero UUID stable, never reused across scopes | dedup and scope-level uniqueness |
| `originalMessageId` | nonzero UUID from real M2/004 message | correlate actual Live event without inventing delivery |
| `originDeviceId + signerSpkiHash` | verified peer identity and SHA-256 DER-SPKI pin | cryptographic source, not displayName/IP |
| `sourceSentAtUtc` | UTC timestamp as captured, immutable | display/time metadata, no causal authority |
| `contentKind + canonical UTF-8 payload/hash` | TEXT or EMOJI, bounded, strict Unicode | signed source content |
| `originSignature` | canonical immutable fields with version/domain separation; algorithm approved by review | reject forged transcript and altered body |
| `groupEpoch/revision/ownerSequence` | required only for Group + signed Group owner proof | JOIN/REMOVE cutoff and owner handoff |
| `sourceProtocolVersion` | typed value e.g. M2 v2 / Group V1 | reject unsupported legacy data |
| `sourceEligibilityEvidence` | approved local observed/owner-accepted state, **not** READ inference | prevent treating unsent drafts as History |

**Canonical signing bytes must be frozen before crypto implementation.** Proposed deterministic binary layout: ASCII `IntraChat-HISTORY-EVENT-V1` domain tag + length-prefixed scope/IDs/type/UTF-8/content digest, strictly ordered unsigned integers, big-endian UUIDs and signed UTC milliseconds. Reject alternate semantic JSON encodings, duplicate fields and unknown future critical flags. Publish independently checked synthetic golden vectors for positive/negative source and Group proof; this draft **contains no fabricated vectors**.

## 5. State & ordering guarantees

~~~text
TLS/PAIR_READY → protected HISTORY_CAPS_CONFIRMED
→ exact scope authorization → SYNC_OPEN/GRANT
→ SUMMARY/HAVE_NEED → bounded signed DELTA
→ local durable transaction → APPLY_ACK
→ repeat until both scoped digests reconcile → SYNC_DONE

Any timeout/cancel/corruption/revoke → PARTIAL/ERROR/PAUSED (no fake SYNCED)
~~~

- A successful delta receipt is **not** a durable history copy until transaction commit; after lost ACK, resend same batch ID, idempotent apply.
- Reconnect creates a **new** authenticated History authorization and safe reconciliation; never trust stale cursor independent of session/peer.
- Store conflict (same ID changed signer/digest) quarantines affected event, does not overwrite source. Tombstone/local deletion fence wins over re-import attempts under confirmed CL-05-07.
- Group owner rechecks current roster/epoch **before each** manifest/delta chunk. New joiner gets no pre-join events. Removed member gets no new History frames after durable remove commit; precommit bytes cannot be revoked.
- A revoked/unknown verified signer is not authenticated by the owner’s TLS hop. The receiver must independently verify original origin and relevant Group owner proof.
- No exactly-once over all restarts, no global causal ordering by timestamp, no guaranteed remote wipe, no synthetic M2 delivery/READ.

## 6. Error matrix and must-fail negative cases

| Condition | Proposed error behavior |
| --- | --- |
| No M2 READY / no HISTORY mutually confirmed | `HISTORY_NOT_NEGOTIATED`; no History parser/payload |
| v1 plaintext or ChatV2 strict parser sees v4 | reject unknown version/type without side effects |
| Peer outside exact conversation or Group | generic `HISTORY_UNAUTHORIZED`, no count/existence leak |
| Stale group owner epoch, wrong member cutoff | `HISTORY_SCOPE_STALE`; pause pending verified owner state |
| Forged original signature/changed key | reject event, no durable/UI insertion |
| Frame >65,536, delta >48KiB, malformed UTF-8/JSON | strict `HISTORY_MALFORMED`, bounded close/reject |
| Same eventId different content/signer | `HISTORY_CONFLICT`, quarantine, no overwrite |
| Key unavailable/corrupt DB/wrong Windows user | `HISTORY_STORE_UNAVAILABLE`, fail closed, no plaintext fallback |
| Disk quota/queue overflow/timeouts | `HISTORY_RESOURCE_LIMIT` or `HISTORY_PARTIAL`, no fake SYNCED |
| Deleted event offered again | reject using local deletion fence; do not rehydrate silently |
| Read receipt suggested by History import | **never emit**; History has no authority to make a viewer-signed READ |

**Verification (NOT RUN):** deterministic codec/schema/golden-vector, M2 v1/v2/Group compatibility and downgrade regression, 2-JVM actual TLS P2P, ≥3 independent Group JVM after Group 004, malformed/race/revoke/owner handoff, two physical Win11 LAN hosts and independent cryptographic review. **All are NOT RUN in this document-only plan.**
