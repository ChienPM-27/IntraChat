# M2 Data Model — Secure P2P Messaging (design with runtime audit)

**Status:** Prototype domain/transport/security classes exist; this document defines required behavior and records unclosed implementation gaps. **No chat database**; M2 not release Verified.
**Source:** [spec.md](spec.md) (CL-03-01…05 confirmed) · [plan.md](plan.md) · [chat-v2 contract](contracts/chat-v2.md).

## 1. Ownership and lifetimes

| Model | Owner | Persistence | Invariants |
| --- | --- | --- | --- |
| **LocalDeviceCredential** | Security/DeviceIdentityStore | **YES: DPAPI CurrentUser**, in local protected security directory | Persistent P-256 private key, X.509 certificate, local stable UUID; secret cannot be plaintext/logged/exported |
| **TrustedPeerRecord** | Security/PinnedPeerStore | **YES: protected user-local trust metadata** | Remote deviceId ↔ SHA-256 SPKI fingerprint; verifiedAt, trust status, re-pair/revoke audit metadata; no chat contents; active pin matched on every TLS session |
| **ProvisionalPeerCertificate** | Security/TlsContextFactory | **RAM until trust decision** | Unknown self-signed TLS cert may only enable quarantined candidate; cannot authorize CHAT_TEXT/ACK/READ |
| **PairingAttempt** | PeerTrustService | **RAM only** | Local and remote key SPKI, device IDs, fresh nonces, computed canonical SAS, both users' confirmations, deadline, TLS session binding; no unilateral trust grant |
| **PeerChatSession** | LiveChatService/ChatSessionRegistry | RAM only | network state, consent, TLS state, verified identity/key, protocol v2, session UUID; all gates required for chat |
| **P2PConversation** | LiveChatService | RAM only | conversation UUID + local deviceId + pinned remote deviceId; not based on name/IP/active FE row |
| **ChatMessage** | LiveChatService | **RAM only** | messageId, conversationId, authenticated senderId and destinationId, UTC sentAt, text, direction; no message persisted |
| **MessageAttempt** | LiveChatService | RAM only | messageId + sent session ID, delivery state, retry count, timeout, reason; uncertainty preserved when ACK lost |
| **DeliveryReceipt** | LiveChatService | RAM only | received message ID, conversation, authenticated peer, session and app-accepted-at; is **not read** |
| **MessageViewEvent** | FE view observation | RAM only | messageId/selected conversation/window active/viewport intersects/visible ≥250ms; emits once per in-memory message |
| **ReadReceipt** | LiveChatService | RAM only | messageId, conversationId, authenticated viewer peerId/session, viewedAtUTC; created only from valid view event |
| **SessionDraft** | FE Live composer | RAM only | conversationId, unsent text, updatedAt, dirty flag; loss on exit must be disclosed |
| **DedupWindow** | Per authenticated session | RAM only | bounded recent incoming messageId/ACK/read IDs; no cross-restart exactly-once |

**Persistence rule CL-03-01/04:** ONLY local device private identity and pinned peer trust can outlive process exit. Never write ChatMessage, SessionDraft, MessageAttempt, DeliveryReceipt, ReadReceipt or dedup IDs into properties/SQLite/log/backup/cloud. Fail closed if DPAPI key/trust cannot be read; do not generate a fresh identity silently. Provide explicit local reset/revoke action.

## 2. Stable identity and pairing

LocalDeviceCredential fields: localDeviceId (UUID, self-claimed until cryptographically bound), KeyPair reference (ECDSA P-256), selfSignedX509 DER, fingerprintSPKI = SHA256(DER SubjectPublicKeyInfo), createdAt, certExpiry and rotation epoch.

TrustedPeerRecord: remoteDeviceId, pinnedSPKIhash256, pairedAtUTC, aliasForDisplay (not an authenticator), revokedAtUTC nullable, lastVerifiedAt; unique key is **remoteDeviceId + pinned fingerprint**. A new SPKI for an existing remote ID is **KEY_CHANGED**; same SPKI with a different claimed remote ID is **MISMATCH** and requires independent comparison/re-pair, not silent migration. A duplicate displayName is permitted.

PairingAttempt: requestId and proposed session UUID; localPeerId, remoteClaimedId, localSPKIhash, remoteSPKIhash, random nonce from each endpoint, SAS (deterministic shared 64-bit display code), localDecision, remoteProofOfApproval, deadline and explicit failure reason. SAS encoding must bind **both UUID ↔ SPKI pairs**, **both session nonces**, sorted canonically with a fixed domain separator. Reject mismatched peerId, swapped keys, changed nonce or missing peer confirmation. Quarantined TLS is not chat authorization.

State:
- UNVERIFIED → VERIFYING → PINNED_VERIFIED
- VERIFYING → REJECTED / EXPIRED
- PINNED_VERIFIED → KEY_CHANGED / REVOKED (fail closed)
- Verified pin on disk by itself does not mean active session; still require inbound human Accept and valid TLS session on reconnect.

## 3. Chat and message identity

P2PConversation unique in RAM: **conversationId is deterministic** per verified pair and current trust key material: first 128 bits of SHA-256(domain separator, sorted binary tuples of verified device UUID and SHA-256 SPKI), with UUID version/variant bits set consistently on both peers. It is an identifier, **not an authorization secret or signature**. Both endpoints compute exactly the same UUID; test vectors must verify it. ConversationId can recur after restart but **no transcript or receipt is persisted**. A new trust public key changes the ID, and a session UUID separately binds every ACK/read receipt. Do not send ChatMessage without verified conversation pairing.

ChatMessage:
- messageId: UUID generated by sender, unique per session and conversation.
- senderId and destinationId: stable, TLS verified device UUIDs, **not** FE displayName.
- conversationId: agreed, bound to exactly this pair.
- sentAtUTC: ISO-8601 UTC Instant; do not use remote time as canonical receive order.
- body: text Unicode validated against **≤8,000 code points AND ≤32 KiB UTF-8**, no empty/whitespace, invalid Unicode rejected; Emoji including ZWJ, multi-code-point preserved.
- localDirection: OUTBOUND or INBOUND, derived from local verified identity; not a trusted field from network.
- transport protocol version: 2 only.

Ordering: TCP stream preserves bytes per secure session; app enqueues accepted messages in per-session receive order (monotonic local index). UI may show sender timestamps but remote clock cannot establish global order across sessions. Dedup by (remote verified peer, session, messageId) and cap/time window; no exactly-once after restart.

## 4. Message status and receipt correlation

Per outgoing message:
- DRAFT: unsent composer content, no wire message.
- SENDING: asynchronous send underway.
- LOCAL_SENT: protected write succeeded, no receiver proof.
- APP_ACKNOWLEDGED: valid DELIVERY_ACK after receiver accepted into RAM view model.
- READ: valid READ_RECEIPT emitted after viewer Live foreground bubble intersects viewport and is stable at least 250 ms; indicates displayed, **not human cognition**.
- FAILED: definitive local error before send.
- UNCERTAIN: sent/possibly sent but ACK absent or connection lost before proof; user decides retry.

State progression **monotonic**: DRAFT → SENDING → LOCAL_SENT → APP_ACKNOWLEDGED → READ. A valid READ can arrive before ACK; allow transition directly from LOCAL_SENT/UNCERTAIN to READ only when receipt is cryptographically authenticated and correct; this logically proves receiver accepted that exact message. Late ACK never downgrades READ. Unknown IDs, wrong recipient, old session, replayed ACK/read receipts must be ignored/rejected with bounded counters; do not call them success.

DeliveryReceipt fields: messageId, conversationId, sessionId, receiverVerifiedId, acceptedAtUTC, optional accepted status code; sender verifies receipt is from intended peer on protected v2 session.

ReadReceipt: messageId, conversationId, sessionId, viewerVerifiedId, viewedAtUTC, readEventId (UUID, dedup), origin=UI_VIEW_EVENT. The wire origin flag is informative only; **the receiving sender cannot cryptographically prove human attention**. Sender verifies session/peer/message correlation; recipient FE only emits after real view event. No queued read receipt when transport disappears.

**Lost ACK/retry:** A may have delivered without knowing it. The UI shows UNCERTAIN and warns of possible duplicates. User manually reconnects/resends after appropriate consent and key pin check. Within session reuse same messageId to dedup; across a new session without persistent dedup, no exactly-once claim. No offline outbox.

## 5. FE Live and Demo isolation

Existing FE view components: model.ChatMessage and model.DeliveryState.SENT/DELIVERED/SEEN, listener.ChatActionHandler, listener.ChatClientListener, ui.frames.MainChatFrame, model.mock.MockChatService.

- LiveChatAdapter maps typed real M2 messages and receipt states to UI. Existing 3-state enum is **insufficient** for SENDING, FAILED and UNCERTAIN; add explicit Live view status type or extend safely with tests, not treat FAILED as SENT.
- Live data directory uses only verified/pending peers; group/media and seeded Demo users are **not** live conversations.
- MainChatFrame loadHistory in Live must be a **RAM transcript snapshot**, never disk/network History Sync.
- View-event observer lives at FE row/scroll/focus boundary and reports an authenticated peer-bound messageId only for a real received Live message (not own outgoing bubble). Must check window active, selected conversation and visible viewport **after layout/paint** with stable visibility ≥250 ms.
- Demo stays on MockChatService, may continue mock read/delivery behavior only if visibly labeled Demo. Demo password never authenticates Live.
- GUI shutdown disposes observers, clears Live RAM state, closes sessions and warnings for unsent draft; trust store remains.

## 6. Validation and concurrency boundaries

- Domain constructors reject null/invalid UUID, time, text, destination, peer binding and impossible state transitions.
- Protocol parser enforces one strict UTF-8 JSON object, mandatory known fields, duplicate-key/trailing-token rejection, bounded depth and type allowlist; never native deserialization.
- SessionRegistry bounded 4 active/4 pending, frame cap 64 KiB TLS, 16 KiB preface, 16 queued writes/2 MiB, 128 receipts and bounded dedup; close resources on failure/timeouts.
- All socket/TLS/DPAPI/JSON work off EDT; UI events use SwingUtilities.invokeLater; close must cancel timers/listeners.
- Test with mismatched SPKI, same display names, expired/replaced cert, read without actual view event, wrong-session receipt, invalid UTF-8, Thai/Vietnamese + emoji, timezone difference, app restart with preserved trust but empty transcript/draft/receipt.

## 7. Runtime consistency audit (T006, 2026-11-21)

| Design requirement | Actual current code | Gap/action |
| --- | --- | --- |
| Durable local identity separate from chat | `DeviceIdentityStore` writes protected P-256 PKCS8/cert blob with Windows CurrentUser DPAPI, fail closed on damaged blob | Cross-user ACL and crash atomicity need independent Windows test (T015/016) |
| Durable remote trust only | `PinnedPeerStore` maps UUID→32-byte SPKI in DPAPI atomic file; explicit `revoke` | No trusted-peer revocation UI/audit metadata (T017/018/022); no silent replacement |
| Ephemeral pair state | `PairingStateMachine` owns local/remote confirmation and READY flags per request UUID | Confirmation/ready correlation is done by `SecureChatCoordinator`; timeout and replay matrix incomplete (T021/033) |
| Transcripts RAM-only | `LiveChatService.PeerSession.messages` is synchronized RAM list ≤500 messages and 2MiB text **per connection** | **8MiB total application cap and cross-session retained-attempt cap not enforced** (T061/062) |
| Draft RAM-only, manual resend | Live composer/adapter tracks UI draft | Uncertain resend semantics, same-ID within session and rejoin after disconnect not fully tested (T053/054/071) |
| Status distinct from presence | `MessageAttempt` guards DRAFT/SENDING/LOCAL_SENT/APP_ACKNOWLEDGED/READ/FAILED/UNCERTAIN; Live uses separate event kind | Outbound attempt and receipt maps currently can grow across reconnections; bounded cleanup required (T050/052/061) |
| Verified sender/destination | `TlsChatSession.send/receive` binds remote UUID and session ID to TLS peer; `LiveChatService` verifies conversation ID | Duplicate-name, clock skew and cross-session collision tests remain (T044/046/059) |
| Read only from real visible bubble | `LiveMessageVisibilityObserver` with foreground guard sends `markViewed`; protected v2 READ_RECEIPT | Direct service `markViewed` assumes trusted FE caller; negative/GUI review pending (T059/072/086) |
| In-session dedup | `PeerSession.seen` LinkedHashSet capped 1,024 incoming IDs; `viewed` set has max implicit 500 inbound | Different restart/session not exactly-once; duplicate-message ACK path needs explicit contract test (T044/061) |
| True sender/time and Unicode | `ChatText`, `P2PConversation`, protected `ChatV2Message`/UUID, `Instant` | UTC time is display metadata; no globally ordered history or 005 synchronization |

**Observed lifetime and fail-closed rules:** A peer pin can survive restart; `Message`, `MessageAttempt`, receipt events, draft, dedup and transcripts MUST NOT. Explicit revoke requires a new human verified pairing. A corrupted DPAPI identity/trust blob MUST throw rather than be silently regenerated. `LiveChatService.close()` clears its maps and shuts down workers; UI destruction must dispose references to prevent retaining strings beyond process lifetime.

**No future scope added:** this review does not authorize History Sync/SQLite (005), Group (004), File (006) or media (007). Corrections to missing capacity/retry/security tests belong to tasks T053/T061/T062/T076–T083, not to a new persistent conversation store.
