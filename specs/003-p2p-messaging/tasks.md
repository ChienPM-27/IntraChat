# Tasks — IntraChat Giai đoạn 03: Secure P2P Messaging (M2)

**Feature:** 003-p2p-messaging
**Created:** 2026-11-21
**Status:** **Planned — 0/90 tasks completed; M2 NOT IMPLEMENTED, NOT VERIFIED**
**Branch:** 003-p2p-messaging
**Inputs:** [spec](spec.md) · [plan](plan.md) · [research](research.md) · [data model](data-model.md) · [chat v2 contract](contracts/chat-v2.md) · [quickstart](quickstart.md) · [requirements checklist](checklists/requirements.md) · [Constitution](../../.specify/memory/constitution.md)

## Task format, invariants and gates

- Format: **- [ ] Tnnn [P?] [USn?] action, path, dependency, Done condition.** Check an item only after the action and evidence have actually been performed. [P] means parallelizable **after dependencies**, with separate file ownership; [US1] through [US6] map to the six user stories. All file paths are relative to the IntraChat repository. Nonexistent paths are **proposed outputs**, not implemented files.
- **Path convention:** For abbreviated Java references, security/, domain/, protocol/, transport/, application/ refer to src/main/java/vn/intrachat/ for implementation and src/test/java/vn/intrachat/ for corresponding tests; ui/ and listener/ refer to src/main/java/ or src/test/java/ as appropriate. Confirm final path in the task PR; do not treat a suggested new file as existing.
- **Test-first RED → GREEN:** create a focused JUnit/integration/UI negative test; actually run it and record expected failure; implement code; rerun focused test and complete Maven/JDK21 regression; record command, exit code, test count and failures under future specs/003-p2p-messaging/evidence/. Do not mark any test PASS without execution, or weaken tests to pass.
- **Confirmed decisions:** **CL-03-01** first-pair **fingerprint/SAS verified by both people**, then persist protected device trust; **CL-03-02** offline **draft in session RAM** with **manual** retry; **CL-03-03** independent real UI view-based **Đã xem** read receipt; **CL-03-04** no durable chat/draft/receipt; **CL-03-05** Messenger FE **Live** uses real messaging, **Demo** remains isolated.
- **Inherited CL-02-01…04:** manual IPv4:port, manual listener, receiver Accept/Reject on **every** inbound connection even for pinned peer, M1 v1 unverified/plaintext **diagnostics only**, manual reconnect (never automatic). No cloud, Internet or relay.
- **Critical security gate:** Never enable CHAT_TEXT on existing plaintext M1 v1 or use an unconditional TLS trust-all verifier. CHAT_V2 text/ACK/read only after explicit inbound consent, valid TLS1.3 identity/key possession, two-sided first-pair user confirmation, pinned peer key and READY. Fail closed on changed key/corrupt DPAPI/version downgrade. No private chat content, keys, or SAS in logs/Git.
- **M1 dependency:** M0 verified (41/41). M1 In Progress (36/60), 45 historical automated tests and local two-JVM PING/PONG, but no **two distinct Windows LAN PCs** and thus M1 Gate 1 NOT VERIFIED. Work on M2 code may be prepared under documented risk; **M2 cannot be released/verified** until M1 is verified and M2 acceptance AC-03-A through AC-03-L, including two real Windows PCs offline from public Internet, have evidence.
- **Out of scope:** Group (004), durable History/Sync (005), File (006), Media (007), calls, public Internet rendezvous, auto-discovery, auto-reconnect, durable outbox, fake read receipts and demo password authentication.
- Do not assume a pre-existing M0/M1/FE class means its new M2 behavior is verified. **This document creates no Java code, tests, passwords, private data or test evidence.** Commit implementation in reviewable slices only after tests.

## Task index / dependencies

| Phase | Tasks | Outcome |
| --- | --- | --- |
| 1 — Setup and Constitution Gate 0 | T001–T010 | M1 blocker tracked; architecture, dependencies, FE audit and threat model reviewed |
| 2 — Device security foundation | T011–T022 | P-256 identity, certificate, Windows DPAPI, peer pinning and canonical SAS test-first |
| 3 — US1: Verified secure session | T023–T035 | v1/v2 router, manual consent, TLS1.3, both-sided pairing confirmation, no chat before READY |
| 4 — US2/US3: Text, Sender, Time, Emoji | T036–T048 | True CHAT_TEXT over TLS, UTF-8, identity binding, UTC and bidirectional loopback |
| 5 — US4: Delivery/read and manual retry | T049–T062 | ACK vs READ, real foreground viewport event, no fake read/offline auto send |
| 6 — US5: FE Live + Demo | T063–T075 | Messenger Live adapter, entry flow, accurate states; Demo separate |
| 7 — US6: Security and two-PC LAN | T076–T084 | Threat/fault/stress tests and actual two physical Windows PCs without Internet |
| 8 — Verification and release gate | T085–T090 | JDK21 regression, Constitution I–VII, AC traceability, docs, handoff |

**Critical sequence:** T001–T010 → T011–T022 → T023–T035 → T036–T048 → T049–T062 → T063–T075 → T076–T090. Tests tagged [P] can be written independently once their named prerequisites are available. Do not edit the shared pom.xml, TcpListener.java, NetworkCoordinator.java or MainChatFrame.java concurrently without an owner.

---

## Phase 1 — Setup, security review and Gate 0 (blocking for M2 code)

- [ ] T001 Kiểm tra trạng thái thực trong specs/002-lan-networking/tasks.md và evidence/m1-acceptance-report.md; tạo specs/003-p2p-messaging/evidence/m1-dependency-gate.md ghi M0 Verified, M1 còn thiếu hai-PC LAN và blockers chi tiết. **Done:** version/commit/evidence đúng, không tự đánh dấu M1 Verified.
- [ ] T002 [P] So sánh 5 Clarify trong specs/003-p2p-messaging/spec.md với plan.md, research.md, data-model.md và contracts/chat-v2.md; tạo evidence/m2-design-review.md. **Done:** kết nối đến vẫn phải Accept, trust persisted riêng, read event thật và FE Live/Demo không mâu thuẫn.
- [ ] T003 [P] Kiểm tra Bouncy Castle bcpkix/bcprov/bcutil 1.87, JNA/JNA-platform 5.19.1, Java21 JSSE, Maven artifact provenance, license/CVE/compatibility; ghi evidence/dependency-review.md. **Done:** có kết quả resolve thực và chấp thuận trước khi thay pom.xml; nếu chưa an toàn phải sửa ADR.
- [ ] T004 [P] Lập evidence/threat-model.md: LAN MITM, self-signed certificate, UUID/key spoof, SAS mismatch, pin substitution, TLS downgrade, DPAPI corruption, forged ACK/read, replay và DoS. **Done:** severity, trust boundary, attack tests, stop rules và reviewer được ghi rõ.
- [ ] T005 [P] Hoàn thiện contracts/chat-v2.md bằng canonical binary SAS/session/conversation ID test vectors, pre-TLS 16KiB HELLO, v2 JSON ≤64KiB, READY protocol và error/version matrices. **Done:** hai peers tính cùng code/ID, không có user payload trong raw preface.
- [ ] T006 [P] Rà soát data-model.md cho separate long-lived credential/pinned trust vs RAM-only ChatMessage/Draft/ACK/Read, key revoke/change, restart, dedup and proper message states. **Done:** không vô tình mở History Sync 005.
- [ ] T007 [P] Audit src/main/java/listener/{ChatActionHandler,ChatClientListener}.java, model/{ChatMessage,DeliveryState,MessageContent}.java, model/mock/MockChatService.java và ui/frames/{LoginFrame,MainChatFrame}.java; ghi evidence/fe-integration-audit.md. **Done:** nêu rõ fake login, timed mock read, group/media, text UTF-16 cap, loadHistory cần tách.
- [ ] T008 [P] Định nghĩa ViewEventPort và visible row criteria: FE Live foreground, đúng hội thoại đang chọn, bubble intersect viewport ≥250ms; viết UI negative test design tại evidence/view-receipt-design.md. **Done:** không READ từ preview, notification, background, Demo hoặc delivery ACK.
- [ ] T009 Chạy actual Windows Java21 Maven clean test/package và scripts/verify-m1.ps1 để lập evidence/m2-baseline.md với command, exit code, JUnit count. **Depends:** T001. **Done:** regression M0/M1/FE được đo thực, không chỉ lấy số từ báo cáo cũ.
- [ ] T010 Review Gate 0 Constitution **I–VII**, threat/dependency findings, tạo checklists/constitution-check.md và ghi blockers. **Depends:** T002–T009. **Done:** design PASS/CONDITIONAL/BLOCKED trung thực; Gate 1 và M1 two-PC vẫn chưa được coi là đã qua.

**Checkpoint:** Bảo mật và giao thức đủ rõ để chuyển sang test-first; nếu dependency/trust design không đạt, dừng trước việc mở user chat.

## Phase 2 — Cryptographic device identity, trust and pairing primitives

**Depends:** T010 and approved dependency review T003. These are security building blocks, **not yet a working secure chat**.

- [ ] T011 [P] Viết RED test src/test/java/vn/intrachat/security/DeviceIdentityTest.java cho P-256 ECDSA, key/identity lifetime, SHA-256 DER SPKI fingerprint, key rotation and malformed key. **Done:** actual RED command recorded; no private key in test logs.
- [ ] T012 [US1] Tạo DeviceIdentity.java + DeviceCertificateFactory.java bằng Java JCA/approved BC; pin dependencies in pom.xml only after T003; làm T011 GREEN. **Depends:** T011/T003. **Done:** Java21 validates self-signed X509 and private key separated.
- [ ] T013 [P] Viết RED DeviceCertificateValidatorTest.java (src/test/java/vn/intrachat/security/) cho expired/not-yet-valid, weak/wrong algorithm, invalid signature/key usage, changed SPKI, spoofed UUID. **Done:** invalid candidates rejected.
- [ ] T014 [US1] Triển khai src/main/java/vn/intrachat/security/DeviceCertificateValidator.java, exact key verification; GREEN T013. **Depends:** T012/T013/T004. **Done:** candidate self-signed TLS possession is **not** a trusted messaging peer.
- [ ] T015 [P] Viết RED DeviceIdentityStoreTest.java cho Windows CurrentUser DPAPI wrap/unprotect, per-user ACL, restart, corrupt blob, cross-user access, partial atomic write and no plaintext. **Done:** unavailable Windows second-user test recorded BLOCKED rather than fake PASS.
- [ ] T016 [US1] Triển khai DeviceIdentityStore.java dùng DPAPI/JNA, protected local key/cert storage, no plaintext fallback; GREEN T015. **Depends:** T012/T015/T003. **Done:** store corruption fails closed and never silently regenerates trusted identity.
- [ ] T017 [P] Viết RED PinnedPeerStoreTest.java cho remote deviceId/SPKI pair, restart, revoke/reset, duplicate display name, changed-key, collision and incomplete write. **Done:** peer pin is persistent but message data is not.
- [ ] T018 [US1] Triển khai PinnedPeerStore.java với atomic protected trust records and explicit revoke; GREEN T017. **Depends:** T016/T017. **Done:** pinned SPKI checked on reconnect, trust != automatic inbound approval.
- [ ] T019 [P] Viết RED FingerprintVerifierTest.java: canonical SAS includes both deviceId/key/nonces, independent endpoints derive same 64-bit code, changed nonce/key changes value, constant-time pin compare, never trust peer-provided SAS. **Done:** deterministic binary vectors documented.
- [ ] T020 [US1] Triển khai FingerprintVerifier.java và full SHA-256 SPKI view + grouped 64-bit SAS; GREEN T019. **Depends:** T019/T012. **Done:** both humans can compare same code through independent channel.
- [ ] T021 [P] Viết RED PairingStateMachineTest.java cho local+remote confirmation, timeout, rejection, mismatched SAS/nonces/session, one-sided confirm, changed/revoked pin and partial commit. **Done:** no unilateral VERIFIED.
- [ ] T022 [US1] Tạo application/PeerTrustService.java và domain/VerifiedPeerIdentity.java xử lý pairing state, pin decision/re-pair; GREEN T021 (domain only). **Depends:** T018/T020/T021. **Done:** human verification and ready gates cannot be bypassed.

**Checkpoint:** Cryptographic primitives locally tested; no socket app authorization yet.

## Phase 3 — US1: Verified, consented TLS v2 session

**Independent story test:** A manually listens, B enters IP:port, A explicitly Accepts, both check matching SAS, both Confirm, then TLS-protected READY. Subsequent reconnect validates pinned SPKI but **still** asks Accept. Changed key blocks.

- [ ] T023 [P] [US1] Viết RED protocol/ChatV2BootstrapCodecTest.java: HELLO/ACCEPT/REJECT correlation, 16KiB pre-TLS cap, invalid UUID/nonces, unsupported version, forbidden chat text. **Done:** malformed input rejected before consent.
- [ ] T024 [US1] Tạo protocol/ChatV2BootstrapCodec.java and strict DTO, bounded UTF-8 handling; GREEN T023. **Depends:** T023/T005. **Done:** no private payload before TLS.
- [ ] T025 [P] [US1] Viết RED transport/ProtocolDispatchTest.java: raw v1 diagnostics routed v1 only, v2 to secure candidate only, malformed/unknown versions and downgrade fail closed. **Done:** v1 CHAT_TEXT still denied.
- [ ] T026 [US1] Refactor transport/TcpListener.java and application/NetworkCoordinator.java to version dispatcher; GREEN T025 and existing M1 tests. **Depends:** T024/T025. **Done:** old TcpPeerSession diagnostic behavior preserved.
- [ ] T027 [P] [US1] Viết RED application/ChatV2InboundApprovalTest.java cho Accept/Reject/30s expiry and pinned peer still requiring human inbound approval. **Done:** scripted approval only in test; no production autoaccept.
- [ ] T028 [US1] Nối v2 candidate với InboundApprovalPort/InboundRequestDialog, then TLS worker only after Accept; GREEN T027. **Depends:** T026/T027. **Done:** Reject/timeout closes without chat.
- [ ] T029 [P] [US1] Viết RED transport/TlsChatHandshakeTest.java bằng real JSSE SSL socket, TLSv1.3 mutual cert, quarantined unknown cert vs pinned known peer, no pre-ready application data. **Done:** proof uses actual TLS, not Boolean mock.
- [ ] T030 [US1] Triển khai security/TlsContextFactory.java with TLSv1.3, mandatory mutual cert, candidate validation, pin check and no trust-all authorization; GREEN T029. **Depends:** T014/T018/T029. **Done:** verified binding to peer public key before chat.
- [ ] T031 [P] [US1] Viết RED transport/TlsChatSessionSecurityTest.java for expired/mismatched cert, fake UUID, TLS resumption bypass, pre-pair CHAT_TEXT, v1 downgrade attempts. **Done:** all private data blocked until READY.
- [ ] T032 [US1] Tạo transport/TlsChatSession.java with bounded reader/writer, handshake deadlines, cancel/close and authenticated peer binding; GREEN T031. **Depends:** T030/T031/T026. **Done:** no app payload if unverified/failed handshake.
- [ ] T033 [P] [US1] Viết RED application/PairConfirmIntegrationTest.java: both local decisions and protected PAIR_CONFIRM/PAIR_READY, wrong correlation, lost READY, partial durable store, replay/timeout. **Done:** no chat if either party not confirmed/ready.
- [ ] T034 [US1] Nối PeerTrustService, PinnedPeerStore, TlsChatSession and bounded ChatSessionRegistry for two-sided READY, key matching, safe re-pair; GREEN T033. **Depends:** T022/T028/T032/T033. **Done:** CONNECTED transport != PINNED_VERIFIED/PROTECTED.
- [ ] T035 [US1] Chạy 2-JVM secure loopback (separate device profiles and secure stores): first pair, both SAS confirmations, restart same key, key-change reject; ghi evidence/us1-fingerprint-pairing.md. **Depends:** T034. **Done:** record actual commands, exits, protected session; **not** called two-PC LAN test.

**Checkpoint US1:** Pair and protected transport local evidence, AC-03-F preliminary only.

## Phase 4 — US2/US3: Real text, sender, time and emoji

- [ ] T036 [P] [US2] Viết RED domain/P2PConversationTest.java for deterministic conversation UUID from verified deviceId/SPKI pair, same ID on both peers, changed key/new ID, same display names. **Done:** UUID alone does not authorize.
- [ ] T037 [US2] Triển khai domain/P2PConversation.java + in-RAM conversation registry, GREEN T036. **Depends:** T036/T022. **Done:** one verified pair maps to correct session conversation.
- [ ] T038 [P] [US2] Viết RED protocol/ChatV2CodecTest.java for CHAT_TEXT/DELIVERY_ACK/READ_RECEIPT/ERROR allowlist, v2 IDs/UTC/destination, UTF-8, duplicate JSON keys, trailing tokens, max 64KiB, reject Group/File/History. **Done:** v1 whitelist unchanged.
- [ ] T039 [US2] Tạo protocol/ChatV2Codec.java, ChatV2Types.java and immutable wire DTOs, GREEN T038. **Depends:** T038/T024. **Done:** only frames inside authenticated TLS are parsed as v2 chat.
- [ ] T040 [P] [US3] Viết RED domain/ChatTextValidationTest.java for Vietnamese accents, emoji ZWJ/surrogates, malformed UTF-8, blank text, 8,000 **code points** and 32KiB UTF-8 bytes caps, multiline. **Done:** no silent truncation.
- [ ] T041 [US3] Triển khai domain/ChatText.java validation and UTF-8 strict codec bridge, GREEN T040. **Depends:** T039/T040. **Done:** FE UTF-16 count mismatch handled explicitly.
- [ ] T042 [P] [US2] Viết RED application/LiveChatAuthorizationTest.java: disabled sending on merely connected, unverified key, missing inbound consent, TLS not READY, wrong conversation, offline draft. **Done:** zero plaintext user data emitted.
- [ ] T043 [US2] Tạo application/LiveChatService.java and RAM chat/attempt ledger; gate async CHAT_TEXT on verified protected v2 state; GREEN T042. **Depends:** T034/T037/T039/T041/T042. **Done:** local write not falsely DELIVERED/READ.
- [ ] T044 [P] [US2] Viết RED application/IncomingChatRoutingTest.java for verified sender/destination, wrong selected conversation, duplicate same-name peer, duplicate message IDs and session ordering. **Done:** invalid messages never appear in wrong chat.
- [ ] T045 [US2] Triển khai incoming chat handler, correct conversation routing and bounded in-session dedup; GREEN T044. **Depends:** T043/T044. **Done:** one unique message -> one bubble; no cross-restart exactly-once claim.
- [ ] T046 [P] [US3] Viết RED domain/MessageMetadataPresentationTest.java for UTC timestamp/timezone/locale, clock skew, display name change, verified sender vs mutable alias. **Done:** timestamp isn't global causal order.
- [ ] T047 [US3] Tạo application/ChatViewMapper.java (or equivalent typed mapper) for sender/time/emoji presentation, GREEN T046. **Depends:** T045/T046. **Done:** UI direction derived from verified local ID.
- [ ] T048 [US2] Tạo/chạy transport/SecureChatLoopbackTest.java for A↔B actual TLS CHAT_TEXT with UTF-8 Vietnamese/emoji, sender/conversation/time, two JVMs; write evidence/us2-text-emoji.md and us3-sender-time.md. **Depends:** T045/T047. **Done:** no mock-only echo counted.

**Checkpoint:** AC-03-A/B/C/H preliminary local evidence. Delivery/read depend on Phase 5.

## Phase 5 — US4: Delivery ACK, READ, retry and true state

- [ ] T049 [P] [US4] Viết RED domain/MessageAttemptStateTest.java for DRAFT/SENDING/LOCAL_SENT/APP_ACKNOWLEDGED/READ, FAILED/UNCERTAIN, monotonicity, READ-before-ACK. **Done:** no fake read/receipt from local socket write.
- [ ] T050 [US4] Tạo domain/MessageAttempt.java, DeliveryReceipt.java, ReadReceipt.java and strict state machine, GREEN T049. **Depends:** T049/T037. **Done:** delivery and view distinct.
- [ ] T051 [P] [US4] Viết RED application/DeliveryAckTest.java: ACK only after receiver accepts into RAM, wrong peer/session/conversation/messageId, duplicate/stale ACK, missing ACK. **Done:** no wrong \`Đã nhận\` state.
- [ ] T052 [US4] Implement authenticated DELIVERY_ACK wire/handler in LiveChatService and bounded pending attempts, GREEN T051. **Depends:** T050/T051/T045. **Done:** remote acceptance proof is application ACK, not read.
- [ ] T053 [P] [US4] Viết RED application/ManualRetryTest.java for offline RAM draft, no auto reconnect/outbox/send, lost ACK -> UNCERTAIN, manual resend and duplicate warning. **Done:** fake clock shows no hidden send scheduler.
- [ ] T054 [US4] Implement manual retry/reconnect commands and 30s ACK uncertain transition in LiveChatService/NetworkCoordinator, GREEN T053. **Depends:** T052/T053. **Done:** user explicitly initiates each retry.
- [ ] T055 [P] [US4] Viết RED ui/LiveMessageVisibilityTest.java for Live foreground active frame + selected conversation + row intersects viewport ≥250ms, excludes preview, other chat, offscreen, inactive, own outgoing and Demo. **Done:** deterministic clock/visibility tests.
- [ ] T056 [US4] Tạo listener/MessageViewEventPort.java and ui/components/LiveMessageVisibilityObserver.java; GREEN T055. **Depends:** T055/T008. **Done:** exactly one logical view event per in-RAM message/session, no mock events.
- [ ] T057 [P] [US4] Viết RED protocol/ReadReceiptContractTest.java with distinct READ_RECEIPT, original messageId, conversation/session/verified viewer, viewedAtUTC/readEventId, duplicate/invalid wrong type. **Done:** delivery ACK != READ.
- [ ] T058 [US4] Implement protected read receipt DTO/producer in ChatV2Codec/LiveChatService; accept only validated visibility event; GREEN T057. **Depends:** T056/T057/T039. **Done:** no offline receipt queue.
- [ ] T059 [P] [US4] Viết RED application/ReadReceiptCorrelationTest.java for real READ, forged/wrong peer/session/conversation/messageId, duplicate/out-of-order, READ before ACK, recipient offline. **Done:** no false status or downgrade of READ.
- [ ] T060 [US4] Implement authenticated read receipt correlation, dedup and monotonic state in LiveChatService, GREEN T059. **Depends:** T050/T052/T058/T059. **Done:** displayed on peer app only; not claimed human gaze.
- [ ] T061 [P] [US4] Viết RED application/SessionRamPrivacyTest.java for 128 receipt pending cap, 1,024 dedup ids, per-chat 500 messages/2MiB, total 8MiB, close/restart RAM loss and persisted trust only. **Done:** no chat/draft/receipt on disk/logs.
- [ ] T062 [US4] Enforce bounded ledger, cleanup, capacity failures, no offline read replay; GREEN T061; run 2-JVM ACK→viewport event→READ and error tests; write evidence/us4-delivery-read-receipt.md. **Depends:** T054/T060/T061. **Done:** actual tests, no fake read or History.

**Checkpoint:** AC-03-D/E/G preliminary local evidence; no read from ACK, preview or background.

## Phase 6 — US5: Messenger FE Live and independent Demo

- [ ] T063 [P] [US5] Viết/run RED ui/DemoIsolationTest.java: existing ui.IntraChat still MockChatService, seeded chat/demo credentials and timed fake DELIVERED/SEEN never appear in Live. **Done:** old demo tests preserved.
- [ ] T064 [P] [US5] Viết RED ui/LiveModeEntryTest.java for explicit Live vs Demo entry, labels and secure pairing startup (not demo LoginFrame/password). **Done:** separate mode indicators visible.
- [ ] T065 [US5] Add Live startup entry/screen in ui/IntraChat.java or ui/frames/LiveConnectionFrame.java, keep Demo route; GREEN T064. **Depends:** T064/T034. **Done:** user controls manual listen/connect/approval/SAS.
- [ ] T066 [P] [US5] Viết RED listener/LiveChatAdapterContractTest.java for real async directory, messages, send, receipt state, RAM transcript via existing FE ports; reject unsupported group/media actions. **Done:** no fake History/Sent/Seen.
- [ ] T067 [US5] Tạo listener/LiveChatAdapter.java mapping LiveChatService to ChatActionHandler/ChatClientListener, explicitly expose SENDING/FAILED/UNCERTAIN; GREEN T066. **Depends:** T066/T043/T060. **Done:** UI events marshaled to EDT and no fake demo auth.
- [ ] T068 [P] [US5] Viết RED ui/LivePeerDirectoryTest.java for duplicate name/changed alias, pinned/unverified key, message to other conversation, no pretend online. **Done:** peer identity key not mutable name.
- [ ] T069 [US5] Wire FE MainChatFrame.java directory/selection and real conversation mapping; GREEN T068. **Depends:** T067/T068. **Done:** correct recipient for all bubbles.
- [ ] T070 [P] [US5] Viết RED ui/LiveComposerTest.java for Enter/Send exactly once, multiline/emoji, UTF-16 vs code-point cap, offline draft, manual retry. **Done:** no silent Unicode truncation.
- [ ] T071 [US5] Wire ui/components/ComposerPanel.java to LiveChatAdapter async send, validation, state gating, draft loss warning; GREEN T070. **Depends:** T067/T070/T054. **Done:** disconnected/unverified cannot send.
- [ ] T072 [P] [US5] Viết RED ui/LiveDeliveryStateRenderingTest.java: Đang gửi/Đã gửi/Đã nhận/Đã xem/Thất bại/Chưa rõ, no premature status, Demo mock cannot influence Live. **Done:** exact event-to-state mapping.
- [ ] T073 [US5] Extend MainChatFrame.java/MessageBubblePanel.java with truthful live state and connect T056 viewport observer; GREEN T072/T055. **Depends:** T060/T067/T069/T072. **Done:** read only on displayed incoming row in foreground Live chat.
- [ ] T074 [P] [US5] Viết RED ui/LiveFutureFeatureIsolationTest.java: Group/File/Image/Video/Sound controls disabled/hidden in Live but may remain Demo, never emit unsupported packet. **Done:** scope 004/006/007 intact.
- [ ] T075 [US5] Hide/disable Live future-feature actions and run real Windows two-process GUI smoke for Live + standalone Demo; write evidence/us5-fe-live-demo.md. **Depends:** T063/T065/T071/T073/T074. **Done:** real app transport not simulated callbacks; UI actions and test commands recorded.

**Checkpoint:** AC-03-I preliminary UI evidence, Live FE genuinely backed by CHAT_V2 while Demo remains mock.

## Phase 7 — US6: Hardening, actual physical LAN and M1 dependency

- [ ] T076 [P] [US6] Create/run security/TlsAdversarialTest.java: MITM/substituted cert, unknown key, SAS mismatch, revoked pin, fake UUID, TLS resumption and forged read/ACK; write evidence/m2-security-tests.md. **Done:** failed trust means zero private app bytes, independent review.
- [ ] T077 [P] [US6] Create/run protocol/ChatV2NegativeTest.java for malformed 16KiB preface, 64KiB protected frame, invalid UTF-8, duplicate JSON, unknown version/type, wrong session and raw v1 chat downgrade; M1 tests still pass. **Done:** safely closed, bounded memory.
- [ ] T078 [P] [US6] Stress transport/SecureSessionResourceTest.java for 4 active/4 pending, 16 outbound frames/2MiB bytes, 128 pending receipts, half-open, cancellation and EDT latency; write evidence/m2-resource-tests.md. **Done:** no unbounded sockets/workers, budgets reported honestly.
- [ ] T079 [P] [US6] Update specs/003-p2p-messaging/quickstart.md and docs/windows-lan-setup.md for actual Live UI, Private IPv4/firewall admin guidance, two distinct physical PCs, isolated secure stores, SAS comparison and UI READ cases. **Done:** untested steps remain NOT RUN; no Public firewall rule added.
- [ ] T080 [US6] Execute on **two separate Windows 11 x64 PCs**, real LAN IPv4 with public Internet unavailable: human inbound Accept, first pair compare SAS at both ends, TLSv1.3 READY; write evidence/us6-real-lan-pairing.md. **Depends:** T035/T075/T079. **Done:** OS/JDK/commit/results on **both machines**; localhost two JVMs not accepted.
- [ ] T081 [US6] On those same physical PCs send A→B and B→A Vietnamese/emoji text; validate sender/time, app ACK then **real foreground viewport** view/read receipt; write evidence/us6-real-lan-messaging.md. **Depends:** T080. **Done:** AC-03-A/B/C/E/G/H/J with real machine observations.
- [ ] T082 [US6] On both PCs run Reject/timeout, changed key/revoke, background/preview/offscreen no-read, forced disconnect, manual reconnect/send and restart trust-preserved/chat-lost; write evidence/us6-real-lan-negatives.md. **Depends:** T081. **Done:** no downgrade/auto resend/fake read.
- [ ] T083 [US6] Independent source/privacy review on actual M2: authorized packet observation contains no plaintext CHAT_TEXT, logs/config contain no message/SAS/key, DPAPI ACL and corrupt store fail closed, Demo separate; write evidence/m2-security-review.md. **Depends:** T076–T082. **Done:** review records limitations, no claim human definitely read.
- [ ] T084 [US6] Verify independently that **M1 Gate 1 is actually Verified** via specs/002-lan-networking/evidence/m1-acceptance-report.md and two-PC evidence; otherwise write evidence/m1-blocker-at-m2.md and keep this task unchecked. **Depends:** T080–T083 for M2 readiness. **Done:** signed real M1 evidence exists; no circular assumption that M2 tests automatically close M1.

**Checkpoint:** AC-03-J needs **two physical Windows PCs**, not two local processes. If unavailable, M2 stays IN PROGRESS/BLOCKED.

## Phase 8 — Cross-cutting verification, Constitution Gate 1 and handoff

- [ ] T085 [P] Build scripts/verify-m2.ps1 with Java21 enforcement, clean package, all Surefire tests, shaded JAR and both Live/Demo smoke, nonzero exit on test failure; run and write evidence/m2-jdk21-test-results.md. **Done:** real commands, counts, exit codes; no PASS when skipped.
- [ ] T086 [P] Real Windows FE keyboard/focus/accessibility and ≥5 connect→pair→send→ACK→READ→disconnect cycles; inspect timers, socket close, viewport race, latency p95 and RAM-only restart; write evidence/m2-windows-ui-check.md. **Done:** measurements and failures disclosed.
- [ ] T087 Independently review **Constitution I–VII Gate 1** in checklists/constitution-check.md and evidence/m2-security-review.md; all critical trust/key/receipt/M1 gates must be satisfied. **Depends:** T083/T085/T086. **Done:** review records PASS/BLOCKED, no unapproved MUST exception.
- [ ] T088 Create evidence/m2-acceptance-report.md with AC-03-A…L, SC-03-001…010 and FR-001…040 mapped to actual tests/two PC evidence with PASS/PARTIAL/BLOCKED/NOT RUN. **Depends:** T080–T087. **Done:** no forged results and M1 prerequisite shown.
- [ ] T089 Update README.md, ROADMAP.md, AGENTS.md and quickstart.md to reflect **actual** Live/Demo launch, human SAS, read-display semantics, only-RAM messages, remaining risks; run link checker. **Depends:** T088. **Done:** docs never claim a capability/security guarantee before tested.
- [ ] T090 Official M2 release/handoff: verify M1 Gate 1, M2 AC-03-A…L, JDK21 regressions, 2 Windows PC offline LAN, security/privacy review, Constitution Gate 1 and signoff; **only then** mark M2 Verified. **Depends:** T084/T087/T088/T089. **Done:** evidence and reviewer/date recorded; otherwise keep checkbox open and mark BLOCKED.

## Story / acceptance traceability

| Spec story | Task ranges | Acceptance criteria |
| --- | --- | --- |
| **US1** Verified device/fingerprint | T011–T035, T076, T080 | AC-03-F, AC-03-J/K/L |
| **US2** Direct chat text | T036–T045, T048, T081 | AC-03-A/B/J |
| **US3** Sender, Time, Emoji | T040–T041, T046–T048, T081 | AC-03-C/H, AC-03-A |
| **US4** Delivery/Read/failure | T049–T062, T073, T082 | AC-03-D/E/G/K |
| **US5** FE Messenger Live + separate Demo | T063–T075, T086 | AC-03-I and UI portion of E/G |
| **US6** Real LAN two Windows PCs | T076–T084 | AC-03-J, AC-03-F/K |
| **Cross-cutting** Gate 0/1 and release | T001–T010, T085–T090 | AC-03-K/L, SC-03-001…010 |

**Final status:** 90 proposed tasks, **0 checked**, no new implementation or new test execution. Treat each checked item as an auditable assertion backed by actual evidence. Plan-stage documents are not executable functionality.
