# Implementation Plan — Giai đoạn 03: Secure P2P Messaging (M2)

**Feature/branch:** 003-p2p-messaging
**Date:** 2026-11-21
**Status:** **In Progress — partial local Java21 implementation; Gate 1 NOT VERIFIED; see [acceptance](evidence/m2-acceptance-report.md)**
**Specification:** [spec.md](spec.md) (CL-03-01…05 confirmed) · [requirements checklist](checklists/requirements.md)
**Constitution:** [v1.0.0](../../.specify/memory/constitution.md)
**Design artifacts:** [research](research.md) · [data model](data-model.md) · [v2 protocol contract](contracts/chat-v2.md) · [quickstart/testing](quickstart.md)
**Dependencies:** M0 Verified (41/41); **M1 In Progress (36/60)**, loopback proven, **two physical Windows LAN peers NOT TESTED**. M1 Gate 1 and M2 Gate 1 remain open.
**Current repo reality (updated after implementation):** Java 21/Maven/FlatLaf/Jackson/JUnit remain; M1 raw TCP/JSON v1 diagnostic-only. **New** M2 branch contains JSSE TLS1.3, DPAPI device/pins, first human SAS dialogs, secure CHAT_V2 text/ACK/view-read and FE Live; Demo remains separate. Only local Windows loopback and JDK21 regression demonstrated; two physical PCs/security acceptance NOT VERIFIED.

## 1. Summary and binding Clarifications

Deliver direct P2P LAN text/emoji with correct Sender, Time, independent delivery ACK and **actual UI view-based Đã xem**, using the FE Messenger interface for **Live** while preserving **Demo** separately. Require a verified device identity and protected channel before any user content; do not silently lift M1's CHAT_TEXT restriction.

| Decision confirmed by user | Concrete M2 commitment |
| --- | --- |
| **CL-03-01** First pair compares code/fingerprint; remember device | Stable device public key, first-contact out-of-band code comparison confirmed by **both** users; store peer key fingerprint for future sessions. Changed key blocks messaging and demands explicit re-pair. Human inbound Accept/Reject **still occurs each connection**. |
| **CL-03-02** Offline draft, manual retry | Draft and uncertain message remain in **session RAM**; no automatic resend/outbox/reconnect. Only the user presses Connect/Reconnect and Send/Retry. |
| **CL-03-03** Đã xem from real view event | Distinct delivery ACK and READ_RECEIPT; READ only when recipient Live conversation is visible in active window and message bubble actually appears in viewport, and a correctly correlated protected read receipt arrives. Displayed does **not** prove human eyes read. |
| **CL-03-04** No chat history across app restart | Message text, drafts, ACK/read receipts, dedup and timelines live in RAM only, discarded on exit. **Exception:** device identity, protected private key and peer trust pin survive restart, containing **no chat text**. |
| **CL-03-05** Messenger FE for Live; keep Demo | Adapt existing FE UI to real chat service, without using demo password, seeded messages or simulated delivery/read states in Live. Keep standalone Demo clearly labeled. |

**Release rule:** Planning and isolated non-user-data engineering may proceed while M1's real LAN gate is pending; no M2 release or Verified claim until M1 Gate 1 and all M2 AC-03-A…L are proven. No personal chat over raw plaintext M1.

## 2. Technical Context

| Topic | Planned choice | Status and rationale |
| --- | --- | --- |
| OS / runtime | **Windows 11 x64, Java 21 LTS**, compiler release 21 | Established M0/M1 baseline; Windows-first |
| Build | Single Maven module, wrapper **3.9.16**, Shade, JUnit **5.14.4** | Reuse all current M0/M1/FE tests; do not create a new project |
| GUI | **Swing + FlatLaf 3.6.2**, existing FE MainChatFrame; no I/O/crypto on EDT | CL-03-05; UI callbacks marshaled back to EDT |
| TCP | Direct **manual IPv4:port**, suggested 47321/TCP; user-started listener | CL-02-01…04; no discovery, relay, Internet or firewall changes |
| Existing wire | Version 1 four-byte length + strict UTF-8 JSON; diagnostic allowlist only | Preserve M1 compatibility and safety |
| New wire | **Separate CHAT_V2 protocol** with bounded plaintext bootstrap for capability/consent then **TLS 1.3** and UTF-8 framed JSON inside TLS | Prevent downgrade; do not insert CHAT_TEXT into v1 codec |
| Crypto | Java 21 **JSSE TLSv1.3**, mandatory mutual self-signed X.509 device certificates, P-256 ECDSA keys, SHA-256 SPKI pins | Use vetted TLS, not hand-written ECDH/AES protocol |
| First-pair user check | **64-bit matching hex SAS** from a domain-separated SHA-256 transcript of sorted (deviceId, authenticated SPKI fingerprint, fresh nonce) pairs, shown at both endpoints; full SHA-256 fingerprint in details | Two users compare independently (in person/trusted voice channel) before both confirm |
| Trust at rest | **Windows DPAPI CurrentUser** wraps long-term private key/PKCS12; user-only ACL, protected local trust/peer pin, atomic write; separate from RAM chat | CL-03-01/04; corruption/inaccessible DPAPI fails closed |
| New dependencies proposed, **not added** | Bouncy Castle bcpkix-jdk18on **1.87** (+ bcprov/bcutil same family) for certificate creation; JNA-platform **5.19.1** (+ JNA) for Windows DPAPI | Maven Central versions researched; vulnerability/license/resolution and Java21 compatibility must be tested before pom change |
| FE integration | Existing listener.ChatActionHandler, ChatClientListener, MainChatFrame and model view DTOs; new LiveChatAdapter and new Live service | Demo MockChatService remains completely separate |
| Durable message storage | **None**: no SQL/History/database/draft/receipt files | History Sync belongs to phase 005 |
| Error/logging | Sanitized code/endpoint; no chat contents, crypto key, SAS, dump of raw JSON or passwords | Constitution IV |
| Security guarantee | Device-public-key pinning and encrypted/authenticated **direct pair session**, not verified real-person identities or multi-device E2EE | Must explicitly disclose trust and same-Windows-user compromise limits |
| Tests | JUnit protocol/security, two local JVMs, **two physical Windows LAN PCs without Internet**, FE Live/Demo negative tests | No new M2 test execution yet |

### 2.1 Initial measurable budgets (design targets, not measured)

- Message text: **at most 8,000 Unicode code points AND 32 KiB UTF-8 bytes**; reject empty/whitespace and malformed surrogate sequences. Current FE MAX_TEXT_LENGTH counts Java UTF-16 units: adapter must reconcile with this decision and never silently truncate.
- Protected v2 envelope **≤64 KiB** (4-byte big-endian length inside TLS); pre-TLS HELLO **≤16 KiB** with **no text/user data**. M1 v1 existing 1 MiB control and 16 KiB preapproval caps remain unchanged.
- Reuse M1 ceilings **4 active peers / 4 pending approvals**, at most **16 queued writes / 2 MiB bytes per session**. RAM-only message cache **500 messages or 2 MiB per conversation**, **8 MiB per process**, **128 outstanding ACK/receipt records per peer**; bounded dedup **1,024 IDs or 30 minutes**, whichever first. Show capacity errors, not unbounded allocation.
- Initial deadlines: TCP connect **5s**, preface **5s**, inbound human decision **30s**, TLS negotiation **10s**, fingerprint user decision **60s**, incomplete protected frame read **15s**, delivery ACK **30s** before marking **UNCERTAIN**, never false failure/success. Numbers require automated and Windows LAN validation.
- UI performance targets: input/scroll interactions p95 **≤200ms**, application status update p95 **≤500ms**, orderly close **≤2s** in bounded normal cases. Not measured or guaranteed yet.
- **Read visibility rule:** a real Live message bubble intersects the viewport while the correct conversation is selected, Swing window is foreground/active, message component is actually laid out and showing, and it remains visible **≥250ms**; debounce before read receipt. Re-evaluate after scroll, focus, selection, new item and resize. A partial viewport intersection counts as displayed, not as human reading. No receipt from preview, notifications, inactive frame or mock Demo.
- Resource/retry limits apply separately to TLS bootstrap, chat frames, outstanding operations and UI events. Under load always fail with truthful status; do not block EDT.

## 3. Constitution Check I–VII — Gate 0

| Principle | Design assessment | Gate 1 acceptance evidence needed |
| --- | --- | --- |
| **I. LAN-first / topology** | **PASS — design:** direct TCP P2P, manual IPv4/port/listener/approval/reconnect, offline LAN, no cloud/discovery | Two actual Win11 PCs, Internet unavailable, A↔B, disconnect |
| **II. Versioned message and identity** | **PASS — design:** v1 diagnostics separate from TLS v2 chat, UUID/type/peer/destination/UTC, independent delivery/read states | Unknown version/type, key mismatch, duplicate sender names, timestamp/timezone tests |
| **III. Bounded nonblocking** | **PASS — design:** TLS/DPAPI/network/parser off EDT, caps/timeouts/cancel/cleanup, manual reconnect | Stress queue/pending, half-open, kill-peer, EDT latency and resource cleanup |
| **IV. Security & privacy** | **PASS CONDITIONAL — design only:** JSSE TLS1.3 mutual keys, human-verified SAS and pinning, DPAPI trust, no plaintext chat/logs, no History | Security review and tests for MITM, cert/path validation, key changes, downgrade, forged ACK/read receipt, disk isolation |
| **V. Verifiable tests** | **PASS — test strategy only; NONE OF M2 RUN** | JDK21 JUnit, two-process loopback, two Windows PCs, failure/negative/regression evidence |
| **VI. Honest UI** | **PASS — design:** transport connection ≠ trust; received ACK ≠ viewed; read means UI display only; FE Live distinct from Demo | Foreground vs preview tests; no optimistic fake READ, clear errors |
| **VII. Spec-driven delivery** | **PASS — design:** confirmed 5/5 CLs; ADRs, contract, data model, later tasks with tests | Reviewed plan → task breakdown → implemented/tests → real acceptance; no fake Verified status |

**Gate 0 verdict:** Design is aligned **subject to independent security review** of custom certificate verification, key storage and dual-sided pairing confirmation. No Constitution MUST exception requested or granted. **Gate 1 NOT VERIFIED.** M1 physical-LAN blocker remains; no new feature/security properties are presently implemented.

## 4. Architecture, ownership and future source boundaries

### 4.1 Documents at plan stage

- specs/003-p2p-messaging/spec.md and checklists/requirements.md — **existing, Clarified**
- specs/003-p2p-messaging/plan.md — **this plan**
- specs/003-p2p-messaging/research.md — alternatives, dependency evidence and threat decisions
- specs/003-p2p-messaging/data-model.md — domain/session/receipt/trust schemas and lifetime
- specs/003-p2p-messaging/contracts/chat-v2.md — precise bootstrap, TLS and JSON contract
- specs/003-p2p-messaging/quickstart.md — future test procedure and blockers
- specs/003-p2p-messaging/tasks.md — **CREATED: T001–T090, all unchecked; implementation test-first work remains future**

### 4.2 Proposed layers (paths describe future code, not existing files)

| Layer | Planned classes / refactor | Responsibility |
| --- | --- | --- |
| UI | ui.frames.MainChatFrame; ui.components.MessageBubblePanel; new Live-mode entrance; keep ui.IntraChat | Render real directory/timeline, connect/pair, send, visibility events, status; no sockets or keys |
| FE ports | listener.ChatActionHandler, ChatClientListener; new LiveChatAdapter + ViewEventPort | Map FE view model to real typed domain and explicit view events; isolate MockChatService |
| Application | vn.intrachat.application.LiveChatService, PeerTrustService; existing NetworkCoordinator | User commands, pairing, routing, RAM conversation/attempt state, async events and lifecycle |
| Security | vn.intrachat.security.DeviceIdentityStore, PinnedPeerStore, FingerprintVerifier, TlsContextFactory | P-256 identity, Windows DPAPI, human-verified fingerprint trust, JSSE TLS |
| Domain | VerifiedPeerIdentity, P2PConversation, ChatMessage, MessageAttempt, DeliveryReceipt, ReadReceipt, SessionDraft | Pure immutable data/state transitions; no Swing, SQL or native APIs |
| Protocol | ChatV2BootstrapCodec, ChatV2Codec, ChatV2Types; existing FrameCodec | Strict v2 bootstrap/protected schema + length caps. **Existing JsonEnvelopeCodec v1 does not accept CHAT_TEXT** |
| Transport | TcpListener dispatch refactor, new TlsChatSession/ChatSessionRegistry; existing TcpPeerSession remains diagnostics | Route first preface v1/v2, enforce consent and TLS, bounded secure I/O and cleanup |
| Tests | security/, protocol/, application/, transport/, ui/ JUnit; scripts/verify-m2.ps1 future | Tests for security/session/read event, actual 2-process and 2-PC evidence |

**Dependency direction:** Swing FE → application ports/LiveChatAdapter → application services → domain, security, protocol, transport. UI must never own SSLSocket/cryptographic keys; protocol must never import Swing. Existing FE DeliveryState (SENT, DELIVERED, SEEN) is a **view DTO** and lacks SENDING/FAILED/UNCERTAIN; don't map those states to falsely completed statuses. Existing FE MockChatService schedules synthetic DELIVERED/SEEN: keep it **Demo only**.

**Existing FE caveat:** LoginFrame takes username/demo password and MainChatFrame calls loadHistory. Live must use a different secure **device pairing** startup flow and return only RAM-session messages from its similarly named UI data query; demo password is NEVER peer authentication. Hide/disable FE group/media/voice actions in Live until future phases.

## 5. Core design and transaction flows

### 5.1 First connection, human approval, TLS and first-pairing

1. User starts listener and peer types **manual IPv4:port**. Shared listener accepts bounded raw TCP and classifies one small first preface into **DIAGNOSTIC_V1** vs **CHAT_V2**. **No chat data in preface**; v1-only implementations must never silently receive v2 user content.
2. Incoming CHAT_V2 request triggers **explicit human Accept/Reject** with observed network endpoint and **self-declared (untrusted) display name/UUID**. Every inbound must ask even if key previously pinned; reject/timeout closes candidate.
3. Only after acceptance, both wrap the same socket in Java21 **JSSE TLS 1.3** with mutual client certificates (server requires client auth). Identity certs are locally self-signed. TLS authenticates **possession of each presented key**, not the associated human identity. A first-contact candidate may finish TLS under **quarantine** with strict self-signed certificate validity/algorithm/key-usage checks, but **no application chat bytes are allowed** before human verification.
4. Both ends calculate **matching SAS** from sorted (deviceId, TLS SPKI SHA-256 fingerprint, fresh per-peer nonce) tuples with a fixed domain separator, show grouped 16 hex chars (64 bits), local/remote full SHA-256 fingerprints in details, and instruct users to compare via a genuinely independent trusted channel. Both endpoints require an explicit **local Confirm** (not a remote-provided string).
5. Protected **PAIR_CONFIRM** from both sides correlates session, peer IDs, key hashes and verification transcript. After **both** local user confirmations and both peer confirmations, pin device UUID↔SPKI fingerprints in the protected user-local store. Only then transition to **VERIFIED + PROTECTED**, enabling chat. Reject, mismatch, timeout or partial failure → close and do not silently authorize a partial trust entry.
6. Later reconnect: inbound Accept again, TLS again, same pinned device ID/public key required; changed/expired/revoked credential requires warning and **manual re-pair**, no automatic TOFU, fallback or silent key migration.

**Security design stop rule:** Do not implement a trust manager that unconditionally trusts all certificates for chat. Design a checked first-contact certificate candidate that remains unauthorized until local and two-party human confirmations. Require independent review and negative tests for pin comparison, cert time/algorithm, certificate replacement and session resumption before exposing Live send.

### 5.2 Protected sending, delivery ACK and actual view receipt

1. FE Live adapter authorizes send only if connection **CONNECTED**, human inbound approval complete, TLS **PROTECTED**, peer **PINNED_VERIFIED**, CHAT_V2 version agreed. A failure keeps draft in RAM and does not generate CHAT_TEXT.
2. Validate unicode/UTF-8 and caps; mint UUID messageId; create RAM-only attempt; send **CHAT_TEXT inside TLS framed v2**. On local write completion mark LOCAL_SENT (not yet delivered).
3. Receiver validates TLS peer binding, claimed sender/destination, conversation, unique messageId, payload and cap; enqueue accepted unique message in correct **RAM conversation** and send authenticated **DELIVERY_ACK**. Duplicate id within dedup window must not produce duplicate bubble.
4. Only a correlated valid application ACK elevates to **APP_ACKNOWLEDGED / Đã nhận**. No ACK after timeout or lost connection → **UNCERTAIN**, not falsely delivered or definitely failed. Retry is manual; within session may reuse id for dedup, across restart exactly-once is not promised.
5. Receiver's view observer triggers only for correct Live chat, foreground active frame, actually laid-out bubble partly within JScrollPane viewport, visible for ≥250ms. Send a separate authenticated **READ_RECEIPT** once per id/session, not from arrival, ACK, scroll-offscreen, preview/notification, background or Demo. If offline, **do not queue the receipt** for reconnect.
6. Sender validates read receipt's **peer, session, conversation, messageId and original outbound attempt**; advance **READ / Đã xem** monotonically. Valid READ may arrive before ACK, so receipt of verified view implies delivery for this message; late ACK must not downgrade. Fake, duplicated, stale, wrong-peer or unknown-message receipt cannot modify state.
7. In RAM only: transcripts, drafts, attempt/ACK/read state, dedup. Closing app clears them; only separate protected device identity/trust data survives.

**Meaning of READ:** The authenticated receiver application reported a real visible view event. It **does not** prove a human was looking or understood; a compromised receiver can lie. Disclose this plainly in UI/help.

### 5.3 Failure, retry and FE integration

- No auto reconnect, auto send, offline queue, background read-receipt queue or durable retry. Show connection/verification/failed/uncertain states separately.
- If the protected link breaks, keep unsent draft and pending attempt **only in process memory**; user manually reconnects, verifies pin and chooses retry. Explicitly warn potential duplicate after ambiguous delivery.
- Live FE draws peer directory, sender/time/emoji and delivery/read from real service events, never demo model flags. **Demo FE** continues to run with independent MockChatService and visible Demo label; demo login is just a mock.
- Keep the M1 diagnostic NetworkPanel available for testing but **do not** use it as primary FE Live chat UI.
- On app close, stop socket, TLS, pending approvals, scheduled tasks, view observers and session RAM stores; warn if draft will be lost. Private key/trust remains as intended.

## 6. Message wire, security and privacy

See [contracts/chat-v2.md](contracts/chat-v2.md) for versioning, preface, TLS handshake guard, frame, schemas and failure classes.

- v1 raw transport remains **diagnostics only**. Dispatcher reads a small explicit preface and routes **CHAT_V2** to TLS with **no app data before secure pairing**; unknown/malformed/unsupported version closes, not downgrade. Don't just add CHAT_TEXT to M1 JsonEnvelopeCodec.
- TLS v1.3 only, mutual certs, key-usage/time/public-key validation. Self-signed keys first contact are **not trusted** until SAS comparison. Existing pins must match constant-time SPKI hashes and device identity; receipt authenticity relies on this session binding, not on self-claimed JSON sender alone.
- Use **P-256 ECDSA self-signed X.509** certificates with suggested initial validity **1 year**; renew cert with same key (if permitted by validation) without treating changed public key as same trusted peer. Rotate compromised keys only with explicit re-pair.
- **Windows DPAPI user scope** (via vetted native bridge), local folder **%LOCALAPPDATA%/IntraChat/security/**, user ACL, atomic writes, revocation/delete UI and tests for corruption/other Windows user access. No silent private-key fallback to plaintext. Credential metadata is allowed durable; **no chat/receipt/draft files**.
- **Attack surfaces:** pre-TLS HELLO, JSON length, UTF-8, certificate chain/key lengths, session ID and claimed UUID, ACK/receipt correlation, untrusted visible display name, remote clock, resource exhaustion, Windows file ACL. No Java ObjectInputStream or leakage of contents/private key/verification SAS to routine logs.
- **Threat scope:** first-time SAS must be checked through a genuine out-of-band channel to resist active MITM. TLS protects direct link; does not prove real-world identity, prevent malicious recipient from lying about a view event, or defend against compromised same-user Windows malware, swap or crash-dump exposure.

## 7. Logical state model

- **Transport:** DISCONNECTED → CONNECTING → CONNECTED → DISCONNECTED/FAILED.
- **Consent:** PENDING → ACCEPTED/REJECTED/EXPIRED, **per inbound connection**.
- **Identity:** UNVERIFIED → VERIFYING → PINNED_VERIFIED / CHANGED_KEY / REVOKED.
- **Security:** UNPROTECTED → TLS_NEGOTIATING → PROTECTED / FAILED.
- **Message:** DRAFT → SENDING → LOCAL_SENT → APP_ACKNOWLEDGED → READ; alternatively FAILED/UNCERTAIN.
- **Read event:** foreground Live row visible in selected conversation → local view event → protected READ_RECEIPT → sender READ.

**Invariant:** CONNECTED does **not** imply authenticated. No CHAT_TEXT unless consent, protected TLS, pinned verified identity and v2 contract. APP_ACKNOWLEDGED does not mean viewed; READ only means receiver-app visible event, not human comprehension. All transitions monotonic and correlated; no stale receipt may mark an unrelated message.

## 8. ADRs and tradeoffs

| ADR | Decision | Alternative rejected / rationale |
| --- | --- | --- |
| **ADR-03-01** | **Separate mutually authenticated TLSv1.3 chat v2**, leave v1 M1 diagnostics untouched | Do not invent AES/ECDH cryptosystem or silently enable plaintext v1 CHAT_TEXT. Requires listener refactor and interoperability tests. |
| **ADR-03-02** | Self-signed P-256 device certs, **human matching 64-bit SAS**, SPKI pin on subsequent connects | No unverified name/IP, no automatic TOFU, no Internet CA. More pairing UX, key-change/revoke complexity. |
| **ADR-03-03** | **DPAPI CurrentUser** wrapped local key/credential store, user-only ACL | Not hard-coded app password or plaintext PKCS12. Native dependency and Windows-specific tests required. |
| **ADR-03-04** | Java21 **JSSE**, **BC 1.87** only for X509 creation, **JNA-platform 5.19.1** only for DPAPI | Avoid custom TLS/native code; pin/review transitive dependencies, licenses and CVEs before implementing. |
| **ADR-03-05** | Distinct **DELIVERY_ACK** and **READ_RECEIPT** with session/peer/message binding | Generic ACK cannot prove view event; separate semantics are auditable/testable. |
| **ADR-03-06** | Foreground + viewport + 250ms stable visibility | Do not mark read from delivery/preview/background. Means displayed, not actual gaze. |
| **ADR-03-07** | **RAM-only** chat and attempts; durable **only identity/trust** | No SQLite or History/auto sync in M2; no message recovery or cross-restart exactly-once. |
| **ADR-03-08** | **Explicit** reconnect and send/retry only | Respect CL-02-04 and CL-03-02; warning needed for unknown delivery. |
| **ADR-03-09** | Reuse FE MainChatFrame for **Live** via adapter and retain standalone **Demo** | No reinvention in M1 NetworkPanel; mock login/group/media/read flags not allowed in Live. |

**Dependency validation status:** Published Maven metadata supports the proposed Bouncy Castle 1.87 and JNA-platform 5.19.1, and Java21 JSSE documents TLS 1.3; **none of the new M2 deps were downloaded, added to pom, compiled or audited in this plan**. See [research](research.md). Dependency version does not certify security.

## 9. Future implementation sequence (mapped to tasks.md)

1. **Gates and research:** M1 Gate 1 status tracked; source-level threat model, Java21 dependency/CVE audit, trust manager/security review, v1/v2 dispatch and FE adapters design.
2. **Device identity and trust:** P-256 identity/cert generation, DPAPI protected store, user-visible fingerprint/SAS, two-sided pair confirmation, restart/changed-key/revoke/failed store tests.
3. **Secure transport:** bounded v2 preface, per inbound consent, JSSE mutual TLS, quarantine unknown peer, pinned known peer, no v1 downgrade and cleanup tests.
4. **Chat protocol and state:** ChatV2 codec, message/ACK/read receipt correlation, Unicode, bounded dedup/queues, RAM-only timeline, negative/timeout/forgery tests.
5. **FE Live + Demo:** new adapter and real device startup flow, map sender/time states, offline drafts, view observer for read receipt, disable unsupported group/media in Live; preserve Demo tests.
6. **Reliability and acceptance:** half-open, capacity/flood, changed keys, privacy/disk/log audit, 5 loopback cycles, two physical Windows PCs without Internet, all M0/M1/FE regressions, Constitution Gate 1 and AC-03-A…L evidence.

Commit implementation in **small independently tested slices** following [tasks.md](tasks.md), which currently contains **15 checked / 75 open items**. Plan and task documents do **not** create implementation or test evidence.

## 10. Verification matrix and release boundary

| AC | Required future evidence / negative cases |
| --- | --- |
| **A/B/C** | Real A↔B text/emoji, sender, UTC/timezone on two JVMs **and two Windows PCs**; no mock exchange counted. |
| **D** | Offline and lost ACK: draft in RAM, explicit reconnect/retry, no UI freeze/no fake delivered. |
| **E/G** | Delivery ACK and **read receipt** separate; foreground selected visible row → READ; preview/hidden/inactive/Demo never READ; forged/duplicate/wrong peer or out-of-order receipts rejected. |
| **F** | Both-side SAS first pair, remembered key after restart, changed key/revoked block, TLS protection; deliberately attempted v1 downgrade must fail closed. |
| **H** | Two peers same displayName, changed name, wrong active conversation, timezone/clock drift correctly mapped. |
| **I** | Live receives from network; Demo isolated; app restart clears all chat/receipt/draft but remembers credential. |
| **J** | **Two distinct Win11 physical PCs**, private IPv4, human consent/SAS, A↔B chat and view event, Internet unavailable; single-machine loopback insufficient. |
| **K** | JDK21 unit/negative/crypto/identity/EDT/queue/cancel/security + M0/M1/FE regression, command exits, actual reports. |
| **L** | M1 Gate 1 Verified + Constitution I–VII Gate 1 + user documentation/read-receipt disclosure; otherwise remain M2 In Progress. |

**Stop rules:** Fail closed for unknown/version mismatch, TLS cert invalid/changed key, pairing disagreement, DPAPI failure or missing security review. Do not send private content over M1. No "Verified" claim from plan text, a compilation success or localhost-only testing.

**Definition of Ready for tasks:** approved spec/Clarify; security design, contract and data model reviewed; dependency audit and M1 gate gap explicitly tracked. **Definition of Done:** test-backed AC-03-A…L plus real two-Windows-PC LAN and security/constitution signoff.

**Current outcome (post-plan implementation): IN PROGRESS (15/90 checked).** [tasks.md](tasks.md) tracks verified subsets of 90 tasks. M2 source/tests, TLS dependency and local real GUI chat now exist; see [evidence](evidence/m2-acceptance-report.md). The original plan is design rationale, **not security certification**. Full AC-03 and real two-PC gates remain open.
