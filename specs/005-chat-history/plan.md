# Implementation Plan — Giai đoạn 05: Protected Chat History & LAN P2P Sync

**Feature/branch:** `005-chat-history`
**Date:** 2026-11-21
**Status:** **PROVISIONAL PLAN / DESIGN-ONLY — 10/10 CL-05 PENDING user confirmation; NO History implementation, NO History test executions, NOT VERIFIED**
**Spec:** [spec.md](spec.md) · [10 proposed decisions](clarifications.md) · [requirements checklist](checklists/requirements.md)
**Design artifacts:** [research/ADRs](research.md) · [logical data model](data-model.md) · [proposed wire contract](contracts/history-v1.md) · [test quickstart](quickstart.md)
**Governing:** [Constitution I–VII](../../.specify/memory/constitution.md) · [ROADMAP 05.01–05.09](../../ROADMAP.md) · [M2 protocol](../003-p2p-messaging/contracts/chat-v2.md) · [Group plan](../004-group-chat/plan.md)

**Planning guard:** All assumptions marked `A` below are **recommendations**, not decisions made by the user. This plan is a **candidate for review**, not an approved implementation baseline. After the user confirms CL-05-01…10, update ADRs, numeric limits, wire fields and relevant FR/AC before generating `tasks.md` or enabling sensitive storage/network code. Immutable/security requirements inherited from M1/M2/004 are not optional.

## 1. Summary and prerequisite state

A Windows 11 x64 Java 21/Swing IntraChat Live client keeps an encrypted, per-Windows-user **History event store** of opted-in text/emoji from verified P2P and (when 004 exists) Group conversations. After restarting, users page through committed events without a peer online. On an **already manually established, human-approved, verified TLS1.3 M2 session**, authorized peers negotiate an optional `HISTORY_V1` capability, scope History to exactly one eligible conversation, compare stable event IDs/digests, exchange bounded deltas, validate origin/provenance, and apply idempotently under durable transactions. No cloud, new listening port, auto-connect, M1 plaintext fallback, auto outbox or synthetic receipts.

**Current actual baseline:** branch `005-chat-history` was created from commit `9188f04` of 004 (pure Group domain). M0 previously Verified. M1/M2 two-physical-Windows-LAN Gate 1 and independent security review remain **NOT VERIFIED**. Group 004 has **18/124 tasks** with 15 pure-domain JUnit Group tests; no real Group wire/FE implementation. Committed 004 evidence recorded Java21 **122/122 M0–M2 + pure Group JUnit PASS**; those results are historical **not** History runs. No History classes, SQLite driver, encryption integration, sync protocol or tests are implemented on this planning branch. Treat those facts as release blockers, not a reason to claim a Group sync path exists.

### 1.1 Binding user/product requirements vs conditional design

| Authority | Binding | Provisional A design if approved |
| --- | --- | --- |
| M0 CL-01-04 | Per-authorized-device local copies, LAN P2P eventual sync; no server | Two-party direct reconciliation, source-authenticated immutable events |
| M1 CL-02 | Manual IPv4:port/inbound Accept every time, v1 diagnostic only | Reuse same verified M2 socket, no new listener |
| M2 CL-03 | TLS1.3 pinned human pairing, M2 RAM-only baseline, real READ only on foreground view | 005 writes **future opted-in** messages, does not backfill lost RAM; no saved/synced→READ |
| Group CL-04 | Owner relay+handoff, 5 devices, protected metadata, join-cutoff/no prejoin history, REMOVE at durable owner commit | Owner-only Group sync, member eligibility interval, owner proof; Group sync disabled before 004 Gate 1 |
| **CL-05-01…10** | **PENDING** | Opt-in; encrypted SQLite/DPAPI; 90d/500MiB; manual sync; 2-device P2P; owner Group sync; local delete+tombstones; no persisted receipts; immutable events; no auto backup |

**Spec-driven precedence:** user-confirmed CL (once received) and Constitution → `spec.md` WHAT/AC → plan ADRs → data-model/contract → tests/code. An A baseline must not silently become CONFIRMED. Any decision requiring group pre-JOIN backfill, user-account merging or plaintext persistence conflicts with approved earlier behavior and requires explicit scope review.

## 2. Technical context — repository-backed integration

| Layer | Committed source / reality | Phase 05 approach |
| --- | --- | --- |
| Runtime & frontend | Java21, Maven Wrapper, JUnit5, Windows 11 x64 target, Swing/FlatLaf; `ui/frames/MainChatFrame` and Live/Demo adapters | Reuse; disk/crypto/network off EDT, cancellation; no alternate cloud frontend |
| M1 transport | Manual IPv4:port; `JsonEnvelopeCodec` raw v1 strictly diagnostics | No History bytes on M1; retain legacy tests |
| Secure M2 | `SecureChatCoordinator`, `TlsChatSession`, `ChatV2BootstrapCodec`, `ChatV2Codec`, `LiveChatService` | Explicit protected HISTORY capability after full pairing/READY, separate History decoder; do not broaden CHAT_TEXT v2 |
| Verified identity | `DeviceIdentityStore`, `PinnedPeerStore`, `ProtectedLocalFile` DPAPI CurrentUser | Reuse pinned SPKI and device identity; **do not reuse** device private key as a database key or expose it to SQLite |
| Session constraints | `NetworkConfig` max **4 active**, 16 outbound frames and 2MiB queued per peer, max secure frame 65,536 bytes | Shared M2 registry; no extra/history sockets, cap History queues within global quotas; targets measured before release |
| P2P message | `P2PConversation` binds two verified deviceIds+SPKI; `LiveChatService` RAM-only events | History opt-in event port records only authenticated/source-verifiable events starting at enablement |
| Group 004 | Pure domain Java + JUnit, Group secure wire/owner handoff/FE not implemented | Group History behind typed Group service/owner proof adapter; **disabled** until 004 runtime + Gate 1 established |
| Storage | Existing DPAPI `ProtectedLocalFile` for small identity files, no durable message DB | **New** encrypted, transactional local History store (A: reviewed SQLite cipher driver + DPAPI-wrapped independent database key); never dump whole DB in DPAPI credential helper |
| Third-party dependencies | Existing Jackson/BC/JNA; no SQLite/SQLCipher artifact pinned yet | Security/CVE/license/native distribution review REQUIRED before adding DB dependency; no plaintext SQLite fallback |

**Storage decision caveat:** Standard SQLite/JDBC is **not encrypted by default**. A request for “SQLite + DPAPI” does not make plaintext DB/WAL safe. The implementation may use a reviewed SQLCipher-compatible JDBC/native build with full-page encryption and encrypted journal/WAL, or, after explicit ADR/user acceptance, a bounded encrypted event-page store. Until verified on the actual Windows binary, **never claim History at rest protected**.

## 3. Constitution Check — Gate 0 (design-level, not release signoff)

| Principle | Assessment now | Design obligations and remaining gates |
| --- | --- | --- |
| **I. LAN-first** | **DESIGN PASS; RUNTIME OPEN** | Direct verified LAN peers, manual connection and explicit consent; Group sync only through owner 004; physical Win11 LAN evidence missing |
| **II. Versioned contract** | **CONDITIONAL** | Distinct HISTORY_V1 on TLS, immutable eventId/source, signed provenance, bounded frames and error matrix; independent canonical vectors/contract review pending |
| **III. Bounded, nonblocking** | **CONDITIONAL** | 50 event/page candidate; History 48KiB chunks/queue; no EDT disk/crypto/network; actual stress/timeout/queue measurement required |
| **IV. Security/privacy (MUST)** | **BLOCKED FOR IMPLEMENTATION/RELEASE** | No plaintext DB/WAL/temp/backup, DPAPI user key, ACL, verified conversation ACL, no Group prejoin/backfill; independent dependency+crypto review needed |
| **V. Test-backed** | **CONDITIONAL** | RED→GREEN unit/integration with JDK21; actual two-peer TLS, ≥3 Group JVM and two physical Windows LAN PCs; History tests not run |
| **VI. Honest UX** | **DESIGN PASS; RUNTIME OPEN** | Saved/synced separate from delivered/read, truthful partial/errors, opt-in/disable/delete, Demo isolation, no fake READ on scroll |
| **VII. Spec-driven** | **PASS for draft; CONFIRMATION OPEN** | 48 FR, 12 SC, 16 AC A–P, nine ROADMAP items; 10 CL pending, plan provisional, no release claim |

**Gate 0 exit criteria:** user confirms 10 CLs; plan/ADR/wire/contract validated and reviewed, approved dependency/protected storage implementation strategy and deterministic security test vectors. **Gate 1** additionally requires executed History AC-05-A…P, inherited M1/M2/004 external gates and independent security review. Design-level PASS does **not** authorize enabling History.

## 4. Architecture and dependency direction

~~~text
Windows 11 · IntraChat Swing Live (Demo separate)
              |
     History UI + consent/delete | live chat events (M2 / 004)
              v
      application/history/
 HistoryService · HistorySyncCoordinator · HistoryAccessPolicy
     |                 |                    |
     v                 v                    v
 domain/history/   protocol/history/    security/history/
 ImmutableEvent    HistoryV1Codec       HistorySignerVerifier
 HistoryCursor     Summary/Delta        VerifiedConversationACL
 Tombstone         Exact caps/errors    GroupOwnerProofVerifier
     |                 |                    |
     v                 v                    v
 storage/history/   verified TLS M2     existing M2 pinned identities
 HistoryRepository  guarded capability  Group 004 current roster
 ProtectedStore     single per-peer socket
     |
     v
Windows user-local encrypted database + protected DB-key reference (DPAPI)
~~~

**Strict ports:**

- `HistoryCapturePort.recordObserved(source, event)`: Live chat event adapter emits only after provenance verified and History opt-in; never on Demo or untrusted M1; explicit success/failure `DurableCommitResult`.
- `HistoryRepository`: `appendImmutable`, `getPage(cursor, limit)`, `lookupDigests`, `applyValidatedBatch`, `markTombstones`, `loadCheckpoint`, `compactUnderPolicy`. No socket/UI access.
- `HistoryAuthorizationPolicy`: validates local user consent, verified session identity, exact P2P pair or Group owner/epoch/member eligibility interval, allowed event type, deletion fence.
- `HistoryPeerTransport`: sends/receives typed bounded History envelopes only over same M2 authenticated TLS session **after protected capability confirm**. No direct Java serialization or v2 CHAT_TEXT tunneling.
- `HistorySyncCoordinator`: one request → explicit authorization → summary → bounded manifest/deltas → signature/hash/ACL verify → transaction commit → apply ACK and resume token. Controlled cancellation/timeout.
- `HistoryViewPort`: read-only paging mapped onto Live Swing EDT; source metadata and persisted status labels; no read receipt creation from archived text or background.

**Single-writer scheduling:** one short transaction writer per local History store, bounded worker queue; immutable inserts and tombstones serialized for a given conversation. Never hold EDT or TLS reader lock while executing SQLite transactions. Prevent M2+Group+History head-of-line blocking via shared session quota and bounded fairness.


## 5. Proposed CL-05 architecture decisions (A baseline; PENDING)

| CL | Candidate A consequences for implementation | Alternate B forces explicit redesign |
| --- | --- | --- |
| **01 Opt-in** | Default DISABLED; first-time consent shows local storage, sync sharing, retention. `HistoryConsent` persisted protected. Capture starts after committed ENABLE event; disable stops new capture immediately | Auto-enabled upgrade must define disclosure, legacy RAM behavior and revocation/rollout |
| **02 Encrypted SQLite** | Reviewed native SQLCipher-compatible implementation, all DB pages/WAL/temp encrypted, new random 256-bit DB key DPAPI-wrapped CurrentUser, ACL; transactions and indexed paging | Encrypted append-only pages need custom crash consistency, indexing, compaction, backup review |
| **03 90d/500MiB** | Suggested quota with configurable cap, warning before compaction; no silent evict where tombstone policy would cause resurrection | Keep-until-delete requires different disk cap/retention/migration and stress matrix |
| **04 Manual sync** | Only explicit Live Sync button after verified READY/capability; user selects scope; no background socket | Auto-sync after opt-in changes consent, throttling, battery/privacy behavior |
| **05 Two device IDs** | P2P ACL is exactly verified A↔B deviceId/SPKI identities. No derived “same user” identity | Additional user/device trust enrollment, key recovery, multi-device ACL |
| **06 Owner Group** | History Group served by **current signed owner**, no member-to-member relay, same owner handoff/membership boundary as 004 | Distributed Group source requires additional independent proof/revocation/forwarding graph |
| **07 Local delete** | User deletes at current Windows account, persist non-resurrection local tombstone/fence, no remote-wipe claim | Remote propagation requires signatures, per-peer consent, offline deletion guarantees |
| **08 No receipt archive** | Store immutable text/emoji + identity/provenance, separate SAVED/SYNCED; prior ephemeral ACK/READ are **not** restored or inferred | Persist original receipt proof with signed origin and strict audience/privacy/retention |
| **09 Immutable conflicts** | Same event ID + different hash/signature → quarantine, never last-write-wins; tombstone dominates local reimport | Edits and delete events need causal version/vector clock semantics and UI |
| **10 No backup** | No automatic backup/export; only reviewed transactional schema migration; importing copies from another Windows user is not implicit | Explicit encrypted export/import, key recovery, file safety and consent |

**Decision freeze rule:** No schema, SQLCipher/native Maven dependency, full signature vectors or History capability code should be treated production-approved until the corresponding CLs are confirmed and the independent security review signs off. Document all deviations from A as amended ADRs.

## 6. Local protected History store lifecycle

### 6.1 Storage layout / key handling (candidate, not tested)

- Per Windows user private directory, for example `%LOCALAPPDATA%\IntraChat\history\`. Separate `history.v1.db` (full encrypted SQLite database), `history.key.dpapi` (wrapped random database key) and protected settings/tombstone state. No message content in path names. Never store DB key beside database as raw bytes or derive it from a chat password, key alias, UUID or display name.
- Create independent random 256-bit database key with JCA `SecureRandom`. Protect key using **DPAPI CurrentUser**, with Windows owner-only ACL on directory and each file, atomic creation/replace, strict path/canonicalization/ACL checks on **reads and writes**; deny reparse point/symlink substitution. Reuse only approved M2 DPAPI helper concepts, not its small 1MiB credential file for whole transcript.
- **SQLCipher driver/native binary must be security-reviewed** (version, signature, license, provenance, CVE, Windows 11 x64 and JVM21 compatibility, encryption algorithm, KDF, WAL/journal/temp files). Plain `org.xerial:sqlite-jdbc` with a DPAPI-wrapped secret does **not** encrypt SQLite pages. If vetted encrypted driver is unavailable: **BLOCKED**, never fall back to plaintext SQLite or log content.
- Verify DB open cipher state, key mismatch, page integrity, WAL/journal/temp/spill policy, connection locking and native failure. Required testing includes strings extracted from on-disk DB/WAL/temp and attempted open by wrong Windows user; never commit raw files/ciphertext/real chat in evidence.
- A successful local write requires durable SQLite transaction commit, clear error if fsync/commit fails, and no `SAVED` before success; configure and test suitable `synchronous` and journal/transaction settings. DB migrations use schema version + explicit transactional migration/backup decision; no auto-delete of unknown/corrupt DB.
- Java process holds decrypted content while rendering/validating; DPAPI/encrypted DB protect **at rest**, not an unlocked account/process, memory dump, clipboard or malicious local admin. Document this threat boundary rather than claiming full device compromise protection.
- History DB, M2 pinned trust and 004 GroupMetadataStore are **different** protected data sets with independent retention/deletion. Deleting History does not destroy device identity or Group roster. Never leak History into app diagnostic logs.

### 6.2 Store interface, transactions and uniqueness

- `HistoryRepository.appendImmutable` enforces unique index `(scopeKind, scopeId, eventId)`; also index `(scope, immutableOrderKey, eventId)` for bounded cursor pagination. Compare canonical digest and verified signer key digest before treating duplicate as idempotent.
- `LocalCapture` must verify the original Live event is eligible (not mock, unsent draft, unauthorized group, invalid UTF-8, no user opt-in); writing an event is not proof it was delivered/read. Locally authored event should carry **origin-signed immutable seal** at capture time; a verified recipient may keep a direct-TLS-observed event but must not transmute it into evidence another signer authorized.
- Per event: preserve `originalMessageId, senderId, sourceSentAt UTC, content, canonical digest, origin signature/proof and source protocol version`. Track separate `storedAtUTC` and local metadata for sorting; do not overwrite source fields on import.
- Single local durable writer serializes `INSERT event`, `INSERT tombstone`, `SET deletion fence` and `UPDATE validated sync checkpoint`. Batch apply one bounded transaction; a checkpoint may only advance after every included event was committed or properly rejected with explicit status. On crash choose safe replay/reconcile instead of inventing committed progress.
- `schemaVersion` table and migration journal must be inside encrypted storage; record a sanitized failure code and stop rather than rewriting incompatible versions.
- Pagination via keyset cursor (not shifting offset) with immutable sort `(source chronology/order bucket, stable source tie-break, eventId)`. For Group use committed ownerSequence where verified; for P2P use stable bounded local order + UTC/UI tie-break, **not** timestamp-only global causality. Sort key must remain stable once committed.

### 6.3 Delete / retention / consent state machines

~~~text
HistoryConsent: DISABLED --explicit OPT_IN--> LOCAL_ONLY
             LOCAL_ONLY --disable--> DISABLED (old data retained/deleted by chosen policy)
             LOCAL_ONLY --verified READY + user Sync--> SYNC_PENDING
             SYNC_PENDING --durably reconciled--> SYNCED
             SYNC_PENDING --lost link/cancel--> PARTIAL or PAUSED
             any active --storage/auth error--> ERROR (no plaintext fallback)
~~~

- Opt-in is per Windows user, display exact retention/storage/sync sharing implications, and starts capture **from an explicit local activation boundary**. No “scan RAM to reconstruct previous chat” unless user explicitly agrees and every source event is still authentically evidenced.
- Local delete is a durable operation with two logical results: remove protected content and persist a **non-resurrection tombstone/fence** in the encrypted store. For whole conversation deletion, assign local deletion generation and prohibit old-generation imports; require fresh user opt-in to resume receiving new eligible event ranges.
- Tombstone retention and compaction cannot silently erase anti-reimport evidence while source peers may still offer old events. If tombstone capacity exhausted, **block reimport** or require user-directed reset with clear loss/reimport risk, not automatic acceptance.
- Quotas/90d/500MiB are only baseline A. Cleanup must protect user privacy and maintain coherent deletion fences, avoid deleting message content while falsely claiming archive complete. Never auto delete trust keys/membership when cleaning History.
- No remote wipe guarantee. Revoking Group membership terminates **new information issuance** to former member; already held local copies remain according to that device's policy.

## 7. History wire protocol and authentication boundary

### 7.1 Capability negotiation and version isolation

**Proposed separate profile:** `HISTORY_V1` inside already authenticated `CHAT_TLS13_V2`, with standalone app envelope `protocolVersion=4, historyProtocolVersion=1`. This uses a distinct `HistoryV1Codec`, **not** existing `ChatV2Codec` (strict protocolVersion=2) and **not** Group V1 profile (v3). Proposed version is draft pending protocol review; treat as app profile, not evidence of a new global CHAT_V4 transport.

1. Manual IPv4:port → human inbound Accept/Reject on every connection → TLS 1.3 mutual identity → pinned key/SAS first-pair → two-sided `PAIR_READY` as M2 specifies.
2. Optionally include `HISTORY_V1` in **data-free untrusted pre-TLS capability offer** after modifying current `ChatV2BootstrapCodec` (currently accepts exactly one CHAT capability). This offer **is not authorization**, can be stripped by LAN MITM.
3. Both participants exchange protected `HISTORY_CAPS_OFFER/CONFIRM` inside verified TLS, binding **both raw preface offers, request/session nonces, verified deviceId/SPKI identities, pair transcript, requested profile/version**; each verifies matched transcript before enabling only History parser. **Do not downgrade to History over M1/v2 CHAT_TEXT** if stripping/mismatch.
4. Select exact conversation scope, check both sides' current opt-in/sync preference, enforce M2 verified pair authorization (or 004 current owner+eligible roster interval). No summary, event count or existence leak before auth.
5. Typed bounded History frames travel through the **existing four-byte length-prefixed TLS M2 channel** (≤65,536 bytes), through distinct strict dispatcher; reject wrong profile, unknown version/type, invalid UTF-8, duplicate JSON keys, unknown fields, oversized nested lists and unverified signatures.

**Source provenance security:** For P2P, **direct verified peer may offer only its own origin-authenticated immutable history events**. A peer may store an observed original from its other peer, but cannot re-sign it as originating from that peer. To safely reshare a third-party-origin event, require its original signer seal attached at creation; otherwise **not eligible for sync**. M2 v2 currently does not include detached event signatures, so an optional signed-seal event may be necessary for **new** 005 content and must be version-negotiated; do not claim old unsigned RAM M2 content can be safely backfilled. For Group, rely on Group 004 independently verified **original member signature + owner routing proof + valid roster interval** and never accept owner-forged sender/viewer events.

### 7.2 History V1 message types (proposed)

| Event | Purpose / hard checks |
| --- | --- |
| `HISTORY_SYNC_OPEN` | requestId, group or pair scope, negotiated version, verified initiator, membership/epoch proof, current opt-in decision; **no unauthorized disclosure** |
| `HISTORY_SYNC_GRANT/DENY` | receiver explicit authorization/consent; denial sanitized and non-enumerating |
| `HISTORY_SUMMARY` | bounded manifest/partition digests or ID ranges for eligible events only, current deletion fence and run scope; no raw private count to outsiders |
| `HISTORY_HAVE/NEED` | bounded exact IDs/digest comparison for a specific scope and stage, not clock-only max cursor |
| `HISTORY_DELTA` | up to bounded event count/bytes containing immutable source event seal, source time, signed origin and eligibility proof |
| `HISTORY_APPLY_ACK` | durable transaction result only; event digest/cursor validated, distinct from **chat delivery ACK** |
| `HISTORY_SYNC_DONE` | complete scoped reconciliation proof only after both sides verify durable checkpoint, no false SYNCED |
| `HISTORY_CANCEL/ERROR` | bounded cancellation/retry-after/error code, never leak roster or private message content in logs |

**Non-goals:** The `HISTORY_APPLY_ACK` confirms a **history copy**, **not** application delivery/read of the original chat message; never re-inject into M2 `READ_RECEIPT` or Group member receipts. No old message re-send as live chat, no offline draft/outbox auto-send.

See [contract draft](contracts/history-v1.md) for proposed field schema, size limits, negative cases and canonical signature inputs. No implemented codec or tested golden vectors exist yet.

## 8. Sync data flow, ACL, ordering, retry and revoke

### 8.1 P2P A↔B reconciliation

~~~text
User A: manual M2 connect → human Accept → TLS pin/SAS → both PAIR_READY
A/B: protected HISTORY_V1 capability CONFIRMED
User A: explicit Sync for A↔B conversation
A→B: HISTORY_SYNC_OPEN(scope, requestId, peer identities)
B: verifies exact A↔B ACL + History permission BEFORE count/manifest
B→A: HISTORY_SYNC_GRANT + bounded SUMMARY
A↔B: HAVE/NEED compare stable event IDs + content digests
B→A: bounded DELTA only of eligible, original B-signed events
A: verify B signature/pin, scope, content, delete fence and quotas
A: durable transaction inserts valid entries + scoped checkpoint
A→B: HISTORY_APPLY_ACK (history copy only, not chat delivered)
A/B: reverse eligible direction if permitted; DONE only after scoped digest proof
~~~

**Eligibility boundary:** A/B represent two verified devices, not an inferred cloud/multi-device account. Every offered event must have originally belonged to their P2P conversation and be **eligible for history capture**. M2 offline drafts/unsent attempts do **not** become chat content through sync. Existing M2 v2 messages lack detached origin signatures, therefore old remote copies cannot be safely asserted as signed without a new negotiated source seal. Design a new sender-authenticated `HISTORY_EVENT_SEAL` (or equivalent), created at source capture for **future** 005 events; otherwise the peer must offer only its own newly signed eligible events. There is **no automatic replay of unsent messages** when A was offline.

**Reconciliation:** compare bounded partitions/sets of stable event IDs and digests, not the maximum source timestamp. Unknown IDs can be requested, but verified data apply is idempotent and scoped. Same ID/digest/source is a no-op; same ID with different digest or signer is quarantined. Never assume distributed total order from wall clocks.

**Resumption:** a scoped run/request ID and validated checkpoint are local hints, not an authorization token. After disconnect, new verified reconnect + permission check + summary reconciliation; no automatic network reconnect and no stale long-lived session capability. `HISTORY_APPLY_ACK` means durable stored copy, **not** M2 DELIVERED or Group READ.

**Unauthorized peer:** return generic refusal without event count/existence/roster detail even when peer is pinned for a different conversation. Bind request/cursor to authenticated peer identity, scope and current session to prevent cross-conversation replay.

### 8.2 Group owner-mediated sync and time-bounded authorization

- **Prerequisite:** 004 secure Group wire, owner relay, origin signed events and voluntary handoff are implemented and Verified. Until then Group History is **disabled**.
- Current verified owner is the only source under CL-05-06 option A. Owner B after handoff must prove dual A+B signed authority, persisted owner activation, epoch and roster before any summary.
- For joiner D, filter by owner serialized **JOIN commit sequence/revision**, not `sentAt`. No pre-JOIN content, including when user previously had a P2P session with the owner.
- On REMOVE durable commit, immediately stop issuing **all new History data** to removed C. Recheck roster before each queued chunk. Already delivered bytes cannot be revoked.
- Group event B→owner A→C keeps original signed sender B proof; owner signature alone does not attest B. C independently pins/validates B's signer key.
- If owner offline, handoff ambiguous or roster out of sync, Group Sync **PAUSED**; no member-to-member fallback or auto-election.

### 8.3 Truth and conflict matrix

| Input/outcome | Durable effect | Honest UI |
| --- | --- | --- |
| Exact duplicate same signer/digest | No-op (idempotent) | One bubble, no artificial receipt |
| Same ID, different signer/content | Quarantine, no overwrite | Conflict alert, sync scope partial |
| Eligible event with valid proof | Insert+commit before apply ACK | Local SAVED only |
| In-flight disconnection | Atomic batch rollback or retain committed checkpoint | PARTIAL/UNCERTAIN, not SYNCED |
| Local deleted event offered again | Tombstone/deletion generation rejects | Local delete preserved |
| Removed Group member requests delta | Refuse before summary/chunk | No new content after removal |
| Stale owner epoch/handoff | PAUSED | No sync by old authority |
| Corrupt DB/key, quota, disk full | Fail closed, no plaintext fallback | Clear error, no fake saved |

**Distributed limitation:** eventual consistency covers only peers online and authorized for the same eligible events, with compatible opt-in/quota. Remote deletion, globally exact-once or recovery of lost pre-History RAM are not promised.

## 9. Resource budgets — provisional targets, NOT MEASURED

| Dimension | Suggested candidate | Required measurement |
| --- | --- | --- |
| TLS frame | Existing ≤65,536 bytes incl framing | boundary/UTF-8/malformed length |
| Delta | ≤20 events **and** ≤48 KiB encoded/frame | Unicode, oversize, fuzz |
| Manifest | ≤256 event IDs/digests/window | large sets, forged cursor |
| Page load | 50 events/page, bounded two concurrent loads | 10k events, EDT p95 target ≤200ms |
| Sync concurrency | one per conversation, two local active sessions total | cancel/parallel Group/P2P |
| Memory | History cache/workers ≤4 MiB **additional** to M2/Group budget, subject to benchmark | heap/GC, slow peer |
| Writer queue | shared existing M2 max 16 frames/2MiB per peer, no separate History socket | fair scheduling/backpressure |
| Local retention | candidate A: 90 days or 500 MiB user-configurable | cleanup/tombstone correctness |
| Timeouts | candidate OPEN 10s, idle 20s, overall 120s/user run | half-open/suspend/cancel |
| Log/privacy | 0 plaintext message bytes, keys or SAS in routine logs/Git | file/log scan and reviewer |

**Important:** these numbers are targets, not actual performance test results. M2/Group aggregate memory and queue policies need implementation/measurement before treating the budget as a passed gate. Priority scheduling must prevent a History transfer from starving Live P2P/Group messages or the Swing EDT.

## 10. Threat review and release-blocking tests

| Threat | Severity | Prevention and test |
| --- | --- | --- |
| LAN MITM/preface strip/downgrade | Critical | Mutual verified TLS, re-confirm both offers after PAIR_READY; reject History on mismatch |
| TLS peer from unrelated conversation guesses ID | Critical | Conversation-specific ACL **before** summary/count or roster |
| Group owner forges sender B | Critical | B immutable origin signature + C independent SPKI pin |
| Late-join member requests old messages | Critical | Owner commit sequence JOIN cutoff; no timestamp shortcut |
| Removed member reuses old cursor | Critical | Re-check current roster revision before every chunk |
| Handoff split-brain, stale key | Critical | Dual signed transfer proof + durable activated owner; else PAUSED |
| Plaintext SQLite/WAL/native temp | Critical | Reviewed encrypted driver, DPAPI-wrapped random key, ACL, on-disk inspection |
| Local deletion resurrected by sync | High | Tombstone/generation survives compaction/restart |
| Forged P2P source history seal | Critical | Original signer bound to source event; don't endorse legacy unsigned copies |
| Replay/ID conflict/cross-scope import | High | Signed immutable digest, unique scoped IDs, quarantine |
| Malformed wire/UTF-8/oversize/disk flood | High | Bounded strict decoder, quotas, rate limit and no EDT block |
| Crash midwrite/migration/ACK loss | High | ACID transaction and checkpoint, safe reconcile |
| Saved/synced mistaken as READ | High | Receipt evidence separate; no READ generation from paging |
| Private content leaks via log/backup | High | No automatic export, content redaction, disk side-file checks |

**External approval gates:** independent security/dependency review of encrypted SQL driver/native binary and crypto protocol, DPAPI/ACL cross-Windows-user check, two **physical** Windows 11 LAN hosts, M1/M2 Gate 1 and Group 004 acceptance. An assistant cannot provide independent signoff. Security design itself is **not** a test or a certification.

## 11. Test-first implementation and acceptance matrix

**Sequence:** confirm CLs and Gate 0 → domain/event/ACL/immutability tests → protected store and corruption/Windows user tests → signed origin and strict History codec → 2-JVM M2 verified P2P sync → Live UI with paging/consent → Group owner-bound sync only after 004 runtime → 3-JVM Group/5 devices + adversarial/load tests → physical two-PC LAN and independent reviewer → final release decision.

- RED→GREEN each behavior: actual Java21 command, exit, expected RED failure, focused GREEN and full M0/M1/M2/004 regression; log sanitized evidence under future `evidence/`.
- **AC-05-A/D/L:** consent, encrypted store restart, wrong user, corrupted DB/key, WAL/temp/ACL, atomic crash/migration, rollback.
- **AC-05-B/H/I:** Group P2P isolation, signed original source, JOIN cutoff, REMOVE new-delta block, owner handoff.
- **AC-05-C/E/F/M:** two actual authenticated peers, bounded summary/delta, conflict/idempotence, replay/unauthorized peer, cancel/retry, quota and negative fuzz.
- **AC-05-G/K/N:** Unicode/timezone pagination, EDT and Demo isolation, saved vs delivery/read, delete/tombstone, retention choice.
- **AC-05-O/P:** ≥3 independent JVM for Group, ≥2 distinct Windows LAN physical PCs without Internet, actual human inbound Accept/SAS, full release trace and independent review.

Run an actual `mvnw.cmd -o test` and `mvnw.cmd -o package` on Java21 only during implementation/QA; none were executed for History planning. Previous 122/122 Group branch tests are **not** evidence that 005 code exists.

## 12. Risks, ADRs and release Definition of Done

Open ADRs: verified encrypted SQLite distribution and license; database key rotation/DPAPI migration; exact canonical signed event vectors; optional v2 preface/profile negotiation with legacy peers; whether original P2P event seal is transmitted at live capture; tombstone compaction and retention; Group epoch and ownerSequence eligible time windows; measured quotas.

**Planning deliverable definition:** This provisional plan includes Constitution I–VII, technical dependencies, architecture, encrypted storage/key handling, versioned wire, authenticated event provenance, ACL, dedup/order/retry/restore, Group JOIN/REMOVE/handoff, time/resource budgets and acceptance test/release gates. It is ready for user clarification review **but not approved**.

**Hard release rule:** No History code/acceptance is claimed in this document. Feature 005 remains **DRAFT / NOT VERIFIED** and milestone M3 requires **both** verified 004 Group and verified 005 History. No `tasks.md` or History Java code is generated in this plan step.
