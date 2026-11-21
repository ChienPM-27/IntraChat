# Tasks — IntraChat Giai đoạn 04: Group Chat (M3 / phần Group)

**Feature:** `004-group-chat` · **Branch:** `004-group-chat`
**Created:** 2026-11-21
**Status:** **PLANNED — 0/124 tasks completed; chưa có Group implementation, Group test hay acceptance evidence. M1/M2 Gate 1 còn BLOCKED.**
**Confirmed:** **CL-04-01…08 (8/8)**, [spec](spec.md) · [plan](plan.md) · [research](research.md) · [data model](data-model.md) · [Group v1 wire contract](contracts/group-v1.md) · [test quickstart](quickstart.md) · [checklist](checklists/requirements.md) · [Constitution](../../.specify/memory/constitution.md).

## 0. Cách đọc task, kiểm chứng và release gates

- **Định dạng:** `- [ ] Tnnn [P?] [USn?] ... **Depends:** Txxx/Tyyy. **Done:** điều kiện có thể kiểm thử.` Không đánh dấu `[x]` nếu chưa thực sự làm và lưu bằng chứng. **[P]** = có thể làm song song **sau khi dependency đã đạt**, miễn là không cùng sửa một file/hạ tầng. **[US1]–[US6]** là sáu user stories trong spec. Tất cả Java path nêu bên dưới là **dự kiến**, không tự nhận đã tồn tại.
- **Path aliases:** `domain/group/` → `src/main/java/vn/intrachat/domain/group/`; `application/group/` → `src/main/java/vn/intrachat/application/group/`; `protocol/group/` → `src/main/java/vn/intrachat/protocol/group/`; `security/group/` → `src/main/java/vn/intrachat/security/group/`; `transport/` → `src/main/java/vn/intrachat/transport/`; `listener/` và `ui/` → `src/main/java/listener/` và `src/main/java/ui/`. Các RED tests cùng package tương ứng trong `src/test/java/`; paths cụ thể của từng task vẫn được ghi để dễ review.
- **TDD RED → GREEN (bắt buộc khi có task cặp):** test RED phải được viết và **chạy thật trên Java21**, ghi nhận đúng hành vi lỗi ban đầu (`command`, `exit code`, tên test và failure reason). Task GREEN triển khai hành vi; chạy focused test + toàn bộ regression liên quan; ghi kết quả thật tại `specs/004-group-chat/evidence/`. Không sửa kỳ vọng test xuống để khiến GREEN giả. Với tài liệu/review/gate, Done = file kiểm chứng có người chấp thuận và bằng chứng có nguồn, không cần giả tạo RED.
- **Chọn định dạng Group:** M2 v1 plaintext **diagnostic only**; chỉ Group V1 ứng dụng (`protocolVersion=3`) **trong verified TLS 1.3/M2 READY + post-TLS negotiated GROUP_V1**. Không nới allowlist cũ, không gửi Group qua `CHAT_TEXT` v2 hay M1 v1. Hop TLS A→C **không chứng minh origin B**: Group content/recipient ACK/READ phải có chữ ký bên phát gốc từ **SPKI thành viên đã được xác minh độc lập**, không tin owner tự khai B/C.
- **8 CL binding:** CL-04-01 owner relay; 02 **member đề xuất** + owner duyệt + invitee đồng ý; 03 owner **handoff chủ động** không tự election; 04 **metadata Group mã hóa DPAPI qua restart**, tin/nháp/ACK/READ chỉ RAM; 05 **5 thiết bị** (owner + 4 member) dùng chung cap **4 peer sessions**; 06 per-recipient **READ thực** với receipt có chữ ký; 07 no backfill trước join; 08 REMOVE có hiệu lực tại **owner durable commit**. Không tự cắt scope vì khó.
- **Tài nguyên:** Plan `7 đưa **design targets chưa đo** (5 members, 8 Groups, TLS ≤65,536 bytes, text ≤8,000 codepoints/32KiB, 16 frames/2MiB session, 8MiB process RAM gồm M2+Group, 1,024 dedup IDs/30min, 30s recipient ACK); các task phải **implement/measure** trước khi coi là limit thật. Khi capacity vượt, báo rõ, không tự evict P2P/treo EDT.
- **Release B1/B2:** M1/M2 chưa có nghiệm thu **hai Win11 physical LAN PCs** và independent dependency/security/privacy review. Có thể chuẩn bị code Group với isolation/test dưới rủi ro được ghi; **không** bật Group trong bản phát hành hoặc ghi “Verified” cho tới M1/M2 Gate 1, các Group AC-04-A…L và Gate 1 bảo mật đều có bằng chứng. M3 còn cần History 005.
- **Git/worktree:** không tự chạm các thay đổi đang dở trên `003-p2p-messaging`; file chung như `pom.xml`, `TcpListener.java`, `SecureChatCoordinator.java`, `TlsChatSession.java`, `MainChatFrame.java`, `LiveChatAdapter.java` phải có **một owner chỉnh tại một thời điểm**, chạy toàn bộ M1/M2 regression khi đổi. **Không** commit mật khẩu/chat riêng/key/SAS/DPAPI secrets/test payload thật.
- **Các thư mục `evidence/` và test/class mới** chưa được tạo; task tạo chúng khi có kết quả thực. Task tracker chỉ là kế hoạch; **0 task được đánh dấu xong tại thời điểm viết**.

## Task index, story, phụ thuộc và checkpoint

| Giai đoạn | Tasks | Tập trung | Checkpoint/gate |
| --- | --- | --- | --- |
| **1 — Readiness / Constitution / threat** | **T001–T010** (10) | Review CL-04, M2 gap, security/profile freeze, Gate 0 | Không mở Group user payload trước trust/gate thiết kế |
| **2 — Group domain & trust ACL** | **T011–T022** (12) | Group ID/roster/ACL/state/per recipient | Pure domain RED→GREEN, không socket |
| **3 — Group protocol, signing, caps** | **T023–T036** (14) | Canonical proof, strict version v3, compatibility với v1/v2 | Secure typed Group route |
| **4 — Protected Group metadata** | **T037–T048** (12) | DPAPI/ACL/atomic/restart/corruption/tombstone | Metadata survives, content never persists |
| **5 — Invitation & membership** | **T049–T062** (14) | Member propose, owner approve, invitee opt-in, join/leave/remove | Owner durable revision, no outsider access |
| **6 — Voluntary owner handoff** | **T063–T074** (12) | Epoch, signed consent, retire/activate, crashes/recovery | At most one valid owner; else PAUSED |
| **7 — Group messages/fan-out** | **T075–T088** (14) | Signed origin, owner relay, dedup, order, resource caps | Real B→A→C logic with no forgery |
| **8 — Delivery ACK/READ/manual retry** | **T089–T100** (12) | Signed recipient ACK + viewport READ + honest statuses | No fake all-read, no offline outbox |
| **9 — Live FE + Demo isolation** | **T101–T110** (10) | Group navigation, roster, handoff UI, recipient states | Nonblocking real Live, mock separate |
| **10 — Multi-peer, security, release** | **T111–T124** (14) | ≥3 JVM, 5-device, LAN vật lý, security, Gate 1 | AC-04-A…L + M1/M2 gates real evidence |

**Dependency backbone:** T001–T010 → foundational domain/protocol/storage (T011–T048, some [P]) → membership (T049–T062) → handoff (T063–T074) + Group relay (T075–T088) → receipt (T089–T100) → FE (T101–T110) → evidence (T111–T124). **No fake implicit completion** when only source code existed from M2.

---

## Phase 1 — Setup, design freeze, threat model, Constitution Gate 0 (T001–T010)

- [ ] **T001** Audit commit/branch, current Constitution, 8 CL-04, M1/M2 status and source integration; create `specs/004-group-chat/evidence/phase4-baseline.md`. **Depends:** none. **Done:** exact commit, Java21/M2 status and missing real LAN/review stated honestly; do not overwrite M2 edits.
- [ ] **T002 [P]** Review `spec.md` vs `plan.md`, `research.md`, `data-model.md`, `contracts/group-v1.md` for all CL-04-01…08; write `evidence/clarification-trace-review.md`. **Depends:** T001. **Done:** owner relay, proposal approval, handoff, protected metadata, 5 cap, READ, join cutoff, remove commit all consistent.
- [ ] **T003 [P]** Audit M2 actual `NetworkConfig`, `TlsChatSession`, `SecureChatCoordinator`, `LiveChatService` and GUI entry, record `evidence/m2-integration-gap.md` including physical-LAN/security blockers. **Depends:** T001. **Done:** group doesn't promise unimplemented M2 queue/8MiB/capability features.
- [ ] **T004 [P]** Create `evidence/group-threat-model.md` trust boundaries: malicious LAN, owner observing/forging origin, viewer spoof, membership replay, DPAPI rollback, handoff split-brain, resource exhaustion. **Depends:** T001. **Done:** threat→test→owner→severity and stop rule for each.
- [ ] **T005 [P]** Review `pom.xml` dependency license/CVE/provenance and JDK21 JSSE/DPAPI/ECDSA support; `evidence/group-dependency-review.md`. **Depends:** T001. **Done:** reviewer approves/rejects actual artifacts; never assume M2 dependency review already signed.
- [ ] **T006 [P]** Freeze `contracts/group-v1.md` including exact canonical binary field order/type tags, preface+post-TLS capability binding, signer-key requirements, version downgrade policy and synthetic golden vectors. **Depends:** T002/T004. **Done:** no TBD field encoding, independently checked source→signature/receipt/handoff vectors; review signoff required before codec code.
- [ ] **T007 [P]** Document 5-device resource and owner 4-session coexistence design in `evidence/group-capacity-review.md` using `NetworkConfig.MAX_ACTIVE_SESSIONS` and aggregate RAM targets. **Depends:** T003. **Done:** single shared quota, no hidden Group sockets/automatic eviction and test metrics defined.
- [ ] **T008 [P]** Write `evidence/group-file-ownership.md` for domain/protocol/security/transport/FE test seams and shared files; reserve one owner per concurrent change. **Depends:** T003. **Done:** module boundaries, proposed filenames and merge/regression strategy explicit.
- [ ] **T009** Prepare Java21 Maven/JUnit test-first and independent multi-JVM harness plan in `evidence/test-matrix.md`; run baseline only if environment available. **Depends:** T001/T003. **Done:** command/exit/JUnit count captured if run, or explicit BLOCKED; no fabricated test PASS.
- [ ] **T010** Complete Constitution I–VII Gate 0 review in `checklists/constitution-gate0.md`, list unresolved M2/security design items and signoff criteria. **Depends:** T002–T009. **Done:** PASS/CONDITIONAL/BLOCKED per principle, **not** Gate 1 Verified.

**Checkpoint:** test-first foundations may start with documented unresolved external gates. Actual Group user-data release remains blocked until B1/B2 closure and secure implementations.

## Phase 2 — Pure Group domain, roster policy and member trust (T011–T022)

- [ ] **T011 [P] [US1]** Write RED `src/test/java/vn/intrachat/domain/group/GroupIdentityTest.java`: random nonzero UUID, separate P2P IDs, Unicode names, blank/oversized names and same-name groups. **Depends:** T010. **Done:** expected failures captured before new entity exists.
- [ ] **T012 [US1]** Implement `domain/group/GroupIdentity.java` / `GroupMetadata.java` primitives, GREEN T011. **Depends:** T011. **Done:** equality by groupId, independent displayName, UTC creation and strict validation.
- [ ] **T013 [P] [US1]** Write RED `src/test/java/vn/intrachat/domain/group/GroupRosterTest.java` for owner+4 verified devices cap, dup UUID/SPKI, same display names and removed members. **Depends:** T010. **Done:** 5 devices allowed, 6th rejected before allocation.
- [ ] **T014 [US1]** Implement `domain/group/GroupMember.java` + `GroupRoster.java` immutable snapshots, GREEN T013. **Depends:** T012/T013. **Done:** one OWNER, at most 5, identity not alias/IP.
- [ ] **T015 [P] [US2]** RED `src/test/java/vn/intrachat/domain/group/GroupAuthorizationTest.java` for propose vs owner approve, accept vs commit, remove/disband/handoff role checks, forged authority. **Depends:** T014. **Done:** no member can grant itself rights.
- [ ] **T016 [US2]** Implement `domain/group/GroupAuthorization.java` actor/role verification using verifiedId+ownerEpoch+revision, GREEN T015. **Depends:** T015. **Done:** fail closed for outsider and stale epoch.
- [ ] **T017 [P] [US4]** RED `src/test/java/vn/intrachat/domain/group/MembershipReducerTest.java` for monotonic revision, idempotent commit, stale/duplicate/conflicting events and remove cutoff. **Depends:** T014. **Done:** no replay can re-add removed member.
- [ ] **T018 [US4]** Implement `domain/group/MembershipEvent.java` / `GroupMembershipReducer.java`, GREEN T017. **Depends:** T016/T017. **Done:** deterministic roster revision transition under serialized owner commit.
- [ ] **T019 [P] [US3]** RED `src/test/java/vn/intrachat/security/group/GroupSignerTrustTest.java` for B origin signature verified by C's **independently pinned** SPKI, duplicate name, key change/revoke and fake owner-provided key. **Depends:** T014/T006. **Done:** owner cannot unilaterally vouch for B to C.
- [ ] **T020 [US3]** Implement `security/group/GroupMemberTrust.java` / verified signer lookup port backed by M2 pin/identity (no auto pin), GREEN T019. **Depends:** T019. **Done:** unknown signer key blocks trusted Group content until human M2-style verification.
- [ ] **T021 [P] [US5]** RED `src/test/java/vn/intrachat/domain/group/GroupRecipientStatusTest.java` for owner-accepted≠recipient-delivered, per-recipient READ monotonicity, independent connectivity state and immutable recipients. **Depends:** T014. **Done:** ACK/READ from one recipient cannot promote others.
- [ ] **T022 [US5]** Implement `domain/group/GroupRecipientStatus.java` / `GroupDeliveryReducer.java`, GREEN T021. **Depends:** T021. **Done:** explicit DRAFT/SENDING/OWNER_ACCEPTED/APP_ACK/VIEWED/UNCERTAIN/OFFLINE semantics without false all-read.

**Checkpoint:** typed pure-domain Group/membership/state with independent key-trust contract, not yet a networked Group.

## Phase 3 — Protected Group protocol, provenance signatures & compatibility (T023–T036)

- [ ] **T023 [P] [US3]** RED `src/test/java/vn/intrachat/protocol/group/GroupCanonicalizerTest.java` golden bytes for Group origin, sorted roster, owner event, ACK/READ/handoff, Unicode/UUID/int64 and changed field. **Depends:** T006/T012/T014. **Done:** executed RED shows missing canonical implementation, synthetic vectors independently checked.
- [ ] **T024 [US3]** Implement `protocol/group/GroupCanonicalizer.java` deterministic binary domain-separated serializer; GREEN T023. **Depends:** T023. **Done:** identical semantic data yields identical signature input regardless of JSON order; strict Unicode/field type rejects.
- [ ] **T025 [P] [US3]** RED `src/test/java/vn/intrachat/security/group/GroupSignatureServiceTest.java` JDK21 P-256 sign/verify, owner vs origin type confusion, invalid DER, changed SPKI and corrupt field. **Depends:** T024/T020. **Done:** forged owner/recipient proofs rejected before UI.
- [ ] **T026 [US3]** Implement `security/group/GroupSignatureService.java` using existing M2 identity `SHA256withECDSA`, pinned verifier and bounded Base64/DER; GREEN T025. **Depends:** T025. **Done:** signatures from origin and viewer cannot be minted by owner; signing never leaks key into logs.
- [ ] **T027 [P] [US3]** RED `src/test/java/vn/intrachat/protocol/group/GroupV1CodecTest.java` encode/decode all allowlisted Group V1 events, strict version=3/profile=1, authenticated hop IDs and recipient. **Depends:** T006/T024. **Done:** parsing a Group frame with legacy v2 decoder remains unsupported.
- [ ] **T028 [US3]** Implement `protocol/group/GroupV1Codec.java`, `GroupV1Types.java` and immutable wire DTOs, GREEN T027. **Depends:** T027. **Done:** 4-byte framing inside TLS, max 65,536 bytes, typed payload and authority context; no raw M1 support.
- [ ] **T029 [P] [US3]** RED `src/test/java/vn/intrachat/protocol/group/GroupV1NegativeTest.java`: invalid UTF-8, trailing JSON, duplicate keys, oversize frame, missing/excess fields, wrong version, UUID/time/int64 overflow, invalid proof. **Depends:** T028. **Done:** no unbounded allocations, no confusing default success.
- [ ] **T030 [US3]** Harden `GroupV1Codec` + `GroupCanonicalizer` for strict schema/size/nesting/signature bound; GREEN T029. **Depends:** T029. **Done:** malformed input returns sanitized codes; non-Group parsers unaffected.
- [ ] **T031 [P] [US3]** RED `src/test/java/vn/intrachat/application/group/GroupCapabilityNegotiationTest.java` for protected mutual offer confirm, untrusted pre-TLS stripping, v2-only peer, missing `PAIR_READY`, mismatched sessionId/keys/nonces. **Depends:** T006/T028. **Done:** Group cannot become READY from plaintext token alone.
- [ ] **T032 [US3]** Implement `application/group/GroupCapabilities.java` / protected negotiation service and optional v2 preface token, GREEN T031. **Depends:** T031. **Done:** explicit `GROUP_V1` both-ACK capability, no Group content before signed/pinned session READY.
- [ ] **T033 [P] [US3]** RED `src/test/java/vn/intrachat/transport/GroupProtocolDispatchTest.java` for v1 diagnostic, v2 private P2P and Group v3 only after caps; unknown protocol/version fails closed. **Depends:** T028/T032. **Done:** no M1 whitelist relaxation or Group tunneled as P2P CHAT_TEXT.
- [ ] **T034 [US3]** Extend `transport/TlsChatSession.java` / `application/SecureChatCoordinator.java` or typed `transport/GroupVerifiedSessionPort.java` for bounded Group profile dispatch, GREEN T033. **Depends:** T033; shared-file owner review T008. **Done:** verified hop authenticated sender/destination, no extra socket/duplicate per-peer registry.
- [ ] **T035 [P] [US3]** RED `src/test/java/vn/intrachat/security/group/GroupThirdPartyOriginTest.java` B→A→C verified signed origin, no direct active B↔C connection, C missing pin B, changed B key and malicious A forged B. **Depends:** T020/T026/T034. **Done:** independent B pin mandatory; receiver fails closed without it.
- [ ] **T036 [US3]** Integrate verified roster public-key lookup and provenance check in `application/group/GroupInboundVerifier.java`, GREEN T035. **Depends:** T035. **Done:** A's TLS hop identity not treated as original B; signed event/owner contexts checked before action.

**Checkpoint:** Only Group-capability-negotiated M2 verified TLS streams accept strict Group V1; third-party-origin and viewer authentication model is enforceable, but no membership UX or relay has been implemented yet.

## Phase 4 — Encrypted Group metadata persistence & restart (T037–T048)

- [ ] **T037 [P] [US1]** RED `src/test/java/vn/intrachat/security/group/GroupMetadataFormatTest.java`: versioned protected schema groupId/name/owner/roster/epoch/revision/proof, 8-group bound, no chat/draft/receipt/nonce fields. **Depends:** T012/T014/T018/T010. **Done:** malformed, unsupported future version and duplicate Group IDs fail closed.
- [ ] **T038 [US1]** Implement `security/group/GroupMetadataCodec.java` strict versioned snapshot serialization and validation, GREEN T037. **Depends:** T037. **Done:** ≤128KiB protected metadata file; never stores user message text or read receipts.
- [ ] **T039 [P] [US1]** RED `src/test/java/vn/intrachat/security/group/GroupMetadataStoreTest.java`: Windows DPAPI CurrentUser save/load, restart, corrupt ciphertext, inability to decrypt, no plaintext fallback. **Depends:** T038/T003. **Done:** actual Win11 test run; unsupported OS marked BLOCKED not fake PASS.
- [ ] **T040 [US1]** Implement `security/group/GroupMetadataStore.java` protected user-local `%LOCALAPPDATA%\\IntraChat\\groups.v1.dpapi` reuse reviewed `ProtectedLocalFile` primitives, GREEN T039. **Depends:** T039/T008. **Done:** encrypted at rest, owner-only ACL and no sensitive log/raw data.
- [ ] **T041 [P] [US1]** RED `src/test/java/vn/intrachat/security/group/GroupMetadataAtomicityTest.java` for partial write/power-loss injection, file symlink/reparse/alternate-user ACL, occupied parent, concurrent writers and bad ownership on load. **Depends:** T040. **Done:** no silently rewritten data or success before durable write; cross-user unavailable recorded BLOCKED.
- [ ] **T042 [US1]** Harden `GroupMetadataStore` for atomic same-directory replace, ACL ownership **read and write**, serialization locks, safe canonical path/file size; GREEN T041. **Depends:** T041. **Done:** corrupted/inaccessible protected store blocks Group; no new owner manufactured from old file.
- [ ] **T043 [P] [US4]** RED `src/test/java/vn/intrachat/security/group/GroupDeletionTombstoneTest.java` for local delete, member leave/remove/disband, revoked identity, restart, stale invitation and no unwanted P2P pin revoke. **Depends:** T042/T018. **Done:** local delete does not invent authoritative Group departure or auto rejoin.
- [ ] **T044 [US4]** Implement encrypted tombstone/deletion/revoke handling in `GroupMetadataStore` and Group state model, GREEN T043. **Depends:** T043. **Done:** explicit confirmation, correct owner event and no stale authorization after removal.
- [ ] **T045 [P] [US5]** RED `src/test/java/vn/intrachat/application/group/GroupRestartRecoveryTest.java`: metadata survives restart, volatile ACTIVE becomes PAUSED, transcript/receipt/draft/dedup absent, owner signed fresh snapshot required. **Depends:** T042/T026/T036. **Done:** no auto-connected Group or History backfill.
- [ ] **T046 [US5]** Implement `application/group/GroupMetadataRecoveryService.java` to load PAUSED and validate owner epoch/pin/run on user-triggered resync, GREEN T045. **Depends:** T045. **Done:** wrong owner/key/rollback/stale revision fail closed; no automatic reconnect.
- [ ] **T047 [P] [US5]** RED `src/test/java/vn/intrachat/security/group/GroupNoContentAtRestTest.java` including synthetic sensitive markers in draft/text/READ and routine logs/metadata, shutdown and 128KiB oversized file. **Depends:** T040/T046. **Done:** private bytes not persisted, DPAPI ciphertext not plaintext and resource bounds observed.
- [ ] **T048 [US5]** Fix Group storage/cleanup weaknesses found in T047; record `evidence/group-storage-review.md` with Win11 DPAPI/version/ACL/corrupt/cross-user results; GREEN T047. **Depends:** T047. **Done:** retention/deletion/corruption behaviors match CL-04-04, remaining OS-account blocker separately flagged.

**Checkpoint:** Only validated Group metadata survives restart, under Windows user-local protection; no Group text/receipts/History persisted. Group remains PAUSED until human reconnect/current owner sync.

## Phase 5 — Invite proposal, owner approval, JOIN/LEAVE/REMOVE (T049–T062)

- [ ] **T049 [P] [US2]** RED `src/test/java/vn/intrachat/application/group/InviteProposalTest.java`: B current member proposes D, outsider proposes D, duplicates/TTL, quota >8/group and wrong ownerEpoch. **Depends:** T016/T018/T036/T048. **Done:** a proposal never adds D or sends Group roster to D.
- [ ] **T050 [US2]** Implement `application/group/GroupInviteProposalService.java` for signed `GROUP_INVITE_PROPOSE` and bounded owner pending map, GREEN T049. **Depends:** T049. **Done:** only authenticated current members can propose, owner observes pending decisions; no automatic owner approval.
- [ ] **T051 [P] [US2]** RED `src/test/java/vn/intrachat/application/group/OwnerInviteApprovalTest.java`: owner explicit Approve/Reject, nonowner forged decision, expired proposal and changed pin. **Depends:** T050. **Done:** proposal cannot transition to invitation without local owner approval event.
- [ ] **T052 [US2]** Implement owner approval command in `application/group/GroupInvitationService.java` and signed `GROUP_PROPOSAL_DECISION`, GREEN T051. **Depends:** T051. **Done:** a verified owner, not GUI-selected name, authorizes offer; no unsolicited invite auto-send.
- [ ] **T053 [P] [US2]** RED `src/test/java/vn/intrachat/application/group/GroupInviteOfferTest.java` for exact invitee, signed nonce/deadline, 60s expiry, minimal roster disclosure, duplicate invitation and mismatched TLS session. **Depends:** T052/T026. **Done:** target must be verified/consented on current protected Group route.
- [ ] **T054 [US2]** Implement `GROUP_INVITE_OFFER` routing on `GroupInvitationService` with explicit invitee dialog port, GREEN T053. **Depends:** T053. **Done:** invitation not roster grant; candidate does not see full private roster or chat.
- [ ] **T055 [P] [US2]** RED `src/test/java/vn/intrachat/application/group/GroupInviteResponseTest.java` for explicit Accept/Reject/timeout/closed dialog, signed consent, replay and owner approval missing. **Depends:** T054. **Done:** rejected/expired invitee never joins, no silent retry.
- [ ] **T056 [US2]** Implement `GROUP_INVITE_RESPONSE` verified invitee consent and owner validation in `GroupInvitationService`, GREEN T055. **Depends:** T055. **Done:** only accepted offer by exact peer triggers candidate JOIN commit request.
- [ ] **T057 [P] [US2]** RED `src/test/java/vn/intrachat/application/group/GroupRosterSyncTest.java` for owner signed committed revision, candidate join no backfill, offline member stale, 5-device cap and all recipients verify keys. **Depends:** T056/T018/T048. **Done:** D receives no pre-join message, stale revision blocks sending.
- [ ] **T058 [US2]** Implement serialized `application/group/GroupMembershipService.java` JOIN **durable commit** + signed `GROUP_MEMBERSHIP_COMMIT/GROUP_ROSTER_SYNC`, GREEN T057. **Depends:** T057/T040/T036. **Done:** only commit grants rights; all online recipients get bounded snapshot/update; failure pauses Group.
- [ ] **T059 [P] [US4]** RED `src/test/java/vn/intrachat/application/group/GroupLeaveRemoveTest.java`: B self-leave request, owner remove C, unprivileged remove, disband/owner handoff or disband, revoked peer tries rejoin and P2P untouched. **Depends:** T058. **Done:** remove effective from owner durable revision.
- [ ] **T060 [US4]** Implement `GROUP_LEAVE_REQUEST/GROUP_REMOVE_COMMIT/GROUP_DISBAND_COMMIT` with owner-only authority/metadata tombstone, GREEN T059. **Depends:** T059/T044. **Done:** no new content to removed member; no automatic P2P key revocation.
- [ ] **T061 [P] [US4]** RED `src/test/java/vn/intrachat/application/group/GroupMembershipRaceTest.java` with JOIN/REMOVE/SEND collisions, old roster replay, owner store failure before commit and in-flight frames pre-commit. **Depends:** T060. **Done:** authoritative ordering and no new post-remove fan-out to revoked peer.
- [ ] **T062 [US4]** Implement per-Group serialized owner reducer/atomic roster selection in `application/group/GroupOwnerCoordinator.java`, GREEN T061. **Depends:** T061/T018. **Done:** one durable point of effect; frame sent pre-commit not falsely recalled.

**Checkpoint:** Member proposal → owner approval → invitee consent → durable JOIN; leave/remove/disband authorized by owner and no unintended History/P2P effects.

## Phase 6 — Owner handoff, epoch consistency & crash safety (T063–T074)

- [ ] **T063 [P] [US4]** RED `src/test/java/vn/intrachat/domain/group/OwnerHandoffStateMachineTest.java` for ACTIVE→TRANSFERRING→RETIRED→new ACTIVE, explicit B acceptance, abort before irreversible point and PAUSED after ambiguous commit. **Depends:** T018/T062. **Done:** two owners cannot both claim valid authority from one state machine.
- [ ] **T064 [US4]** Implement `domain/group/OwnerHandoffStateMachine.java` and immutable transferId/old/new epoch proof entities, GREEN T063. **Depends:** T063. **Done:** role/epoch/rosterDigest continuity and signed state transitions enforced.
- [ ] **T065 [P] [US4]** RED `src/test/java/vn/intrachat/application/group/OwnerHandoffPrepareTest.java` for owner UI consent, successor eligible and online, fresh transferId, B local owner-responsibility Accept/Reject/timeout, authenticated keys. **Depends:** T064/T058. **Done:** no auto election or silent successor consent.
- [ ] **T066 [US4]** Implement `application/group/GroupHandoffService.java` `PREPARE/ACCEPT` with Group quiesce and signed A/B identities, GREEN T065. **Depends:** T065/T026. **Done:** invalid/replay transfer cannot enter commit phase.
- [ ] **T067 [P] [US4]** RED `src/test/java/vn/intrachat/security/group/OwnerRetireDurabilityTest.java`: A storage failure before/after durable RETIRED and attempt to continue owner e or rollback. **Depends:** T066/T042. **Done:** A cannot fan-out after irrevocable durable retirement; failure leaves Group PAUSED.
- [ ] **T068 [US4]** Implement A RETIRED metadata write then signed `GROUP_HANDOFF_COMMIT` in `GroupHandoffService`, GREEN T067. **Depends:** T067. **Done:** commit proof references B's signed acceptance and A's persisted retire, send only afterward.
- [ ] **T069 [P] [US4]** RED `src/test/java/vn/intrachat/application/group/OwnerActivationTest.java`: B receives valid A+B proof, persists epoch+1 before serving, stale A event, offline other members and signed activation. **Depends:** T068. **Done:** B cannot relay as owner without committed proof.
- [ ] **T070 [US4]** Implement B activation and `GROUP_HANDOFF_ACTIVATED` + signed roster/run sync, GREEN T069. **Depends:** T069/T046. **Done:** other members switch only after A+B proof+pin checked, otherwise PAUSED.
- [ ] **T071 [P] [US4]** RED `src/test/java/vn/intrachat/application/group/OwnerHandoffCrashMatrixTest.java` fail at pre-prepare, B accepted, A before/after retire write, COMMIT in transit, B before/after activation persist, partial broadcast. **Depends:** T070. **Done:** every crash case one active owner or PAUSED, never two.
- [ ] **T072 [US4]** Add bounded timeout/manual retransmission of **same** committed proof and safe resume logic to `GroupHandoffService`; GREEN T071. **Depends:** T071. **Done:** no rollback of A after RETIRED; owner unavailable may block liveness, explicitly surfaced.
- [ ] **T073 [P] [US4]** RED `src/test/java/vn/intrachat/security/group/OwnerForkRollbackTest.java` for unsigned higher epoch, stale backup/current owner mismatch, changed A/B pin, conflicting handoff and replayed activation across run. **Depends:** T072. **Done:** corrupted/forked proof freezes Group; no largest-epoch-wins.
- [ ] **T074 [US4]** Harden owner proof validation `security/group/OwnerHandoffVerifier.java`, Group metadata recovery and UI error policy; GREEN T073 and record `evidence/handoff-threat-review.md`. **Depends:** T073/T036/T046. **Done:** split-brain/fail-closed behavior code reviewed with limitation of DPAPI rollback documented.

**Checkpoint:** Voluntary handoff can succeed without auto election; failure at any specified durable boundary never creates competing owners according to tested protocol state.

## Phase 7 — Signed text, owner relay, bounded fan-out, dedup & ordering (T075–T088)

- [ ] **T075 [P] [US3]** RED `src/test/java/vn/intrachat/application/group/GroupTextSubmitTest.java`: B signs valid UTF-8 text 1–8,000 codepoints/≤32KiB, authenticated origin/group/epoch/run and invalid text/wrong pin/offline. **Depends:** T036/T058. **Done:** no Group content before M2 READY+GROUP caps+active roster.
- [ ] **T076 [US3]** Implement `application/group/GroupService.java` submit command (async, RAM-only) and immutable origin signature, GREEN T075. **Depends:** T075/T022. **Done:** local UI SENDING is not owner accepted nor delivered.
- [ ] **T077 [P] [US3]** RED `src/test/java/vn/intrachat/application/group/OwnerGroupAcceptanceTest.java` for owner validation/serialized acceptance, current roster, signer, ownerSequence, duplicate and full pipeline rejection. **Depends:** T076/T062. **Done:** no owner ACK unless accepted into bounded RAM pipeline.
- [ ] **T078 [US3]** Implement serialized owner accept + signed `GROUP_OWNER_ACCEPT` in `GroupOwnerCoordinator.java`, GREEN T077. **Depends:** T077. **Done:** assigning monotonic ownerSequence does not claim all recipient ACKs.
- [ ] **T079 [P] [US3]** RED `src/test/java/vn/intrachat/application/group/GroupFanoutRecipientsTest.java`: A/B/C joined, D outsider, C offline, owner member sends, shared 4 active links/5-device quota. **Depends:** T078/T060. **Done:** zero Group frame to outsider, B→A→C correct.
- [ ] **T080 [US3]** Implement `application/group/GroupRelayService.java` per-recipient snapshot/signature fan-out, GREEN T079. **Depends:** T079/T034. **Done:** owner local receipt distinct from recipient, no direct B→C fallback.
- [ ] **T081 [P] [US3]** RED `src/test/java/vn/intrachat/application/group/GroupInboundProvenanceTest.java`: malicious owner changes text/origin/recipient snapshot, B+C duplicate nickname, unknown signer C and stale owner key. **Depends:** T080/T026. **Done:** C verifies B signature independently and owner relay signature.
- [ ] **T082 [US3]** Implement `application/group/GroupIncomingHandler.java` Group identity/recipient/signature gating and correct RAM conversation UI event, GREEN T081. **Depends:** T081/T036. **Done:** one valid message goes only to intended Group, no P2P contamination.
- [ ] **T083 [P] [US3]** RED `src/test/java/vn/intrachat/application/group/GroupDedupTest.java` for (group/run/origin/originalId) exact retry, conflicting content hash, duplicate owner fan-out, 1,024 IDs/30min and restart limit. **Depends:** T082. **Done:** no double bubble within bounded window.
- [ ] **T084 [US3]** Implement `application/group/GroupDedupWindow.java` + owner/recipient idempotence, GREEN T083. **Depends:** T083. **Done:** conflict rejection, receipt replay bounded, no exactly-once cross restart claim.
- [ ] **T085 [P] [US3]** RED `src/test/java/vn/intrachat/application/group/GroupOrderingTest.java` for B/C concurrent sends, ownerSequence/run rotation, clock skew/timezone, offline delayed frames. **Depends:** T084. **Done:** within-run display ordering stable; never rely on remote sentAt for authority.
- [ ] **T086 [US3]** Implement bounded Group timeline/order projection in `application/group/GroupTimeline.java`, GREEN T085. **Depends:** T085. **Done:** correct sender/time/group under out-of-order network events.
- [ ] **T087 [P] [US5]** RED `src/test/java/vn/intrachat/application/group/GroupBackpressureTest.java` for 16 frames/2MiB per shared M2 session, 200 messages/1MiB Group, global Group+P2P 8MiB, slow member, cancellation. **Depends:** T080/T084/T007. **Done:** capacity error before false owner acceptance, no resource runaway.
- [ ] **T088 [US5]** Implement `application/group/GroupResourceBudget.java` and shared session queue/backpressure limits, GREEN T087; keep M2 regression. **Depends:** T087/T034/T008. **Done:** atomic capacity reservations, per-recipient failure independent, no EDT blocking.

**Checkpoint:** Group content uses verified origin and owner-signed routing to correct roster with bounded resources; partial delivery truthful, no durable outbox.

## Phase 8 — Per-recipient application ACK, actual READ, offline/manual retry (T089–T100)

- [ ] **T089 [P] [US5]** RED `src/test/java/vn/intrachat/application/group/GroupSignedDeliveryAckTest.java`: C app accepted, signed receipt to B via A; owner ACK alone, wrong C identity/epoch/run/original ID and reused receipt. **Depends:** T082/T026/T022. **Done:** only C's authentic app ACK may mark C delivered.
- [ ] **T090 [US5]** Implement `application/group/GroupReceiptRouter.java` for signed `GROUP_DELIVERY_ACK` generation/owner-forward/verified correlation, GREEN T089. **Depends:** T089. **Done:** signed C ACK passes unchanged; B checks C pin and recipient snapshot.
- [ ] **T091 [P] [US5]** RED `src/test/java/vn/intrachat/application/group/GroupRecipientStateCorrelationTest.java`: A/B/C mixed outcomes, only one APP_ACK, duplicate/out-of-order receipt, immutable denominator and owner acceptance separately. **Depends:** T090/T022. **Done:** no false all-delivered/read.
- [ ] **T092 [US5]** Implement per-recipient ledger and monotonic state transitions in `application/group/GroupReceiptLedger.java`, GREEN T091. **Depends:** T091. **Done:** status queried by (groupId, originalId, recipientId, runId), 128 correlations/peer and bounded cleanup.
- [ ] **T093 [P] [US5]** RED `src/test/java/ui/GroupViewportReadEventTest.java`: correct Group foreground/active Swing viewport bubble shown ≥250ms; preview/background/offscreen/wrong Group/scroll/focus and Demo negative. **Depends:** T082/T092. **Done:** no READ just from arrival or owner ACK; deterministic EDT test if possible.
- [ ] **T094 [US5]** Implement `listener/GroupViewEventPort.java` + exact `ui/` Live viewport observer with EDT debounce, GREEN T093. **Depends:** T093/T008. **Done:** independent messageId/groupId/visible state, no timer race causing spurious read event.
- [ ] **T095 [P] [US5]** RED `src/test/java/vn/intrachat/application/group/GroupReadReceiptTest.java`: C signs READ after T094 view, A relays, B verifies signed viewer/content/epoch/run/readEventId; READ before app ACK. **Depends:** T094/T090/T026. **Done:** one recipient VIEWED only on valid receipt; late ACK cannot downgrade.
- [ ] **T096 [US5]** Implement `GroupReceiptRouter` `GROUP_READ_RECEIPT` with viewer signature and per-session idempotence; GREEN T095. **Depends:** T095/T092. **Done:** owner can't manufacture C's READ; offline viewer doesn't queue durable receipt.
- [ ] **T097 [P] [US5]** RED `src/test/java/vn/intrachat/application/group/GroupReceiptAdversarialTest.java` wrong owner/session/recipient/origin, spoofed viewer, duplicate readEventId, mutated content hash, changed key, C outside group. **Depends:** T096/T074. **Done:** all forged/late receipts fail closed without changing other recipients' statuses.
- [ ] **T098 [US5]** Harden receipt signature verification, timeout cleanup, ledger privacy and malformed Group ACK/READ codes in `GroupReceiptRouter`, GREEN T097. **Depends:** T097/T088. **Done:** no fake receipt/replay or unbounded pending map, errors sanitized.
- [ ] **T099 [P] [US5]** RED `src/test/java/vn/intrachat/application/group/GroupManualRetryTest.java` for owner offline, C offline, ACK lost, 30s UNCERTAIN, manual retry same signed original in same run, new run warning, no auto send. **Depends:** T098/T084. **Done:** no silent resend/history/outbox or false definite failure.
- [ ] **T100 [US5]** Implement manual retry + per-recipient deadlines/draft retention in `GroupService` with clean worker/session shutdown; GREEN T099. **Depends:** T099/T088. **Done:** timeout/close bounded, no auto reconnect, draft RAM only, owner paused and UI truthful.

**Checkpoint:** Owner-accepted≠C delivered. Verified C viewport READ is separately signed/correlated and may arrive before ACK, without false status to B/D or persistence.

## Phase 9 — Swing Messenger Live Group UI & Demo/P2P separation (T101–T110)

- [ ] **T101 [P] [US6]** RED `src/test/java/listener/GroupLiveAdapterTest.java` for Group ID vs P2P conversation, duplicate displayName, sender/time/timezone/emoji, per-recipient states, offline/uncertain and EDT dispatch. **Depends:** T086/T092. **Done:** mock group data never appears as verified Live.
- [ ] **T102 [US6]** Implement `listener/GroupLiveAdapter.java` mapping typed Group application events to existing FE view models (or new typed Group DTO); GREEN T101. **Depends:** T101. **Done:** no false map from UNCERTAIN to DELIVERED/READ.
- [ ] **T103 [P] [US6]** RED `src/test/java/ui/GroupConversationNavigationTest.java` for Group tab/list, groupId-based selection, same-name groups, unread Group vs P2P isolation and session PAUSED. **Depends:** T102. **Done:** background Group message never inserts into active P2P/H conversation.
- [ ] **T104 [US6]** Implement Group conversation/roster display in `ui/frames/MainChatFrame.java` and dedicated `ui/components/GroupPanel.java`; GREEN T103. **Depends:** T103/T008. **Done:** name/owner/member count, sender/time, connect/paused state and accessibility labels correct.
- [ ] **T105 [P] [US6]** RED `src/test/java/ui/GroupComposerInteractionTest.java`: real Group sends, disabled if unverified/owner paused/roster stale, Unicode boundary, draft preservation and manual retry/no hidden auto. **Depends:** T100/T104. **Done:** no packet from disabled controls or Demo.
- [ ] **T106 [US6]** Wire Group composer and message state controls to `GroupService` via `GroupLiveAdapter`, GREEN T105. **Depends:** T105. **Done:** network and crypto off Swing EDT, correct status across fast chat selection changes.
- [ ] **T107 [P] [US2]** RED `src/test/java/ui/GroupMembershipDialogsTest.java`: member propose, owner Approve/Reject, invitee Accept/Reject, remove/leave/disband confirmation, explicit successor owner-accept and timeout/cancel. **Depends:** T058/T074/T104. **Done:** independent owner/invitee consent and no accidental auto approval.
- [ ] **T108 [US2][US4]** Implement `ui/dialogs/GroupInviteDialog.java`, `ui/dialogs/GroupMemberDialog.java` and `ui/dialogs/OwnerHandoffDialog.java`, GREEN T107. **Depends:** T107. **Done:** shows verified peer identifiers, current owner/epoch and consequences of handing off or deleting; no UI blocking.
- [ ] **T109 [P] [US6]** RED `src/test/java/ui/GroupDemoIsolationTest.java`: Demo seeded Group/read status vs Live typed Group, mock login/password, P2P actions unaffected, Group File/Media/History buttons disabled. **Depends:** T102/T104/T108. **Done:** no accidental Demo→TLS traffic or UI claiming unsupported features.
- [ ] **T110 [US6]** Integrate Group Live entry/navigation across `ui/IntraChat.java` / `listener/ChatActionHandler.java` / `MainChatFrame.java` while preserving Demo/P2P; GREEN T109 and M0/M1/M2 UI regressions. **Depends:** T109/T106/T094/T008. **Done:** real Live Group supports create/invite/chat/handoff/status, Demo clearly separate and no extra entrypoint.

**Checkpoint:** Live Group is a real frontend to verified sessions, with per-recipient delivery/READ, explicit owner/admin actions, correct RAM/durable metadata boundary and responsive EDT.

## Phase 10 — Multi-client verification, real LAN, security & release gates (T111–T124)

- [ ] **T111 [P] [US3]** Write RED 3-JVM secure integration `src/test/java/vn/intrachat/group/GroupThreePeerIT.java` and harness for A(owner)↔B/C verified TLS, member proposal/owner approval, no Group before READY. **Depends:** T110/T100/T088. **Done:** tests truly use ≥3 independent JVM/process instances, not three fake objects.
- [ ] **T112 [US3]** Implement deterministic `scripts/verify-m3.ps1` or executable JUnit harness, run T111 GREEN for A→B/C and B→A→C text/emoji; save `evidence/group-3jvm-loopback.md`. **Depends:** T111. **Done:** exact Java21 command/exit/JUnit count and measured receipt states, **not** claimed two physical PCs.
- [ ] **T113 [P] [US3]** Add `src/test/java/vn/intrachat/group/GroupFiveDeviceIT.java`: owner +4 members (5 separate client identities), valid full roster, one outsider 6th rejected and unrelated P2P session capacity errors. **Depends:** T112/T088. **Done:** real 4 simultaneous verified member links within shared M2 cap; no hidden 5th owner socket.
- [ ] **T114 [P] [US2][US4]** Add `src/test/java/vn/intrachat/group/GroupMembershipAndHandoffIT.java` for member propose→owner approve→invitee accept, reject, join cutoff, remove race, A→B signed epoch handoff and crash matrix with resumed proof. **Depends:** T112/T074/T060. **Done:** full CL-04-02/03/07/08 across real TLS sessions with honest PAUSED failures.
- [ ] **T115 [US3]** Execute **real physical LAN** on ≥2 distinct Win11 x64 PCs without public Internet and ≥3 independent clients; test manual IPv4:port, per-inbound Accept/SAS/pin, B→A→C owner relay, outsider D and online/offline/disconnect. Save `evidence/group-windows-lan-acceptance.md`. **Depends:** T112/T113/T114 and verified inherited M1/M2 physical gates. **Done:** physical hostname/device counts, sanitized commands/screenshots and exact results; **BLOCKED** until actual PCs available.
- [ ] **T116 [P] [US5]** Execute Win11 cross-Windows-user DPAPI/ACL/atomic corrupt-storage/restart tests for `GroupMetadataStore`, including wrong account and no plaintext; `evidence/group-storage-windows-test.md`. **Depends:** T048. **Done:** actual distinct Windows account results and no Group chat content on disk; mark BLOCKED if unavailable.
- [ ] **T117 [P] [US5]** Run 5-device stress/soak + owner half-open/slow peer/queue/timeouts tests, measure global M2+Group RAM ≤8MiB target, 16 frames/2MiB per session, quota and EDT p95; `evidence/group-load-test.md`. **Depends:** T113/T100/T110. **Done:** actual measurements + remediation if target fails, not “target=PASS”.
- [ ] **T118 [P] [US3][US5]** Run malicious Group protocol/identity security suite: forged origin/viewer/owner, key/pin substitution, capability downgrade, unknown v3, invalid JSON/UTF-8/DER, replay, stale revision, DPAPI rollback. `evidence/group-adversarial-test.md`. **Depends:** T114/T098/T074. **Done:** each threat has run result and privacy/fail-closed assertion.
- [ ] **T119** Run full **actual JDK21** Maven/JUnit/packaging including M0/M1/M2 old tests, new Group tests, v1 diagnostic and v2 P2P Live/Demo regression; `evidence/m3-regression.md`. **Depends:** T112–T114/T117/T118. **Done:** command, Java version, exit, JUnit XML counts; no fabricated pass, repair any regressions.
- [ ] **T120** Obtain **independent security/dependency/privacy review** of P-256 canonical signatures, cross-member pinning, OwnerHandoff fail-closed/rollback, Group DPAPI, receipt spoofer, transport versioning and raw logs; `evidence/group-security-review.md`. **Depends:** T005/T006/T116–T119. **Done:** reviewer identity, findings, remediations and explicit signoff; **BLOCKED** without independent reviewer.
- [ ] **T121** Build `specs/004-group-chat/evidence/group-acceptance-matrix.md` mapping **AC-04-A…L**, **FR-04-001…040**, SC-04 and confirmed CLs to exact test result/log/screenshot/commit; mark PASS/FAIL/BLOCKED. **Depends:** T115–T120. **Done:** no self-certified pass without actual evidence.
- [ ] **T122** Review Constitution I–VII **Gate 1** and inherited M1/M2 release gates in `checklists/constitution-gate1.md` + `evidence/prerequisite-gates.md`. **Depends:** T120/T121. **Done:** fail release if two-PC M1/M2 or security review absent; no waiver implied.
- [ ] **T123** Update `README.md`, `ROADMAP.md`, `docs/architecture.md`, `specs/004-group-chat/{spec,plan,quickstart}.md` to reflect **actual** behavior and limitations; redact all chats, keys, SAS and user metadata from artifacts. **Depends:** T119/T121/T122. **Done:** clear Live vs Demo and protected per-hop relay (not Group E2EE), History 005 still out of scope.
- [ ] **T124** Final feature release review and `evidence/group-release-report.md`: if **all AC-04-A…L, all T001…T123, M1/M2 Gate 1, independent security review** have evidence, tag Group 004 **Verified**; otherwise leave **In Progress/BLOCKED** with owner and next actions. **Depends:** T121–T123. **Done:** decision defensible, no claim that Group completion also verifies History 005 / entire M3.

**Phase 10 stop rule:** Unavailable independent Windows LAN PCs or reviewer is a **clear external blocker**. Do not replace real physical evidence by localhost, bypass first-pair SAS, falsely check T115/T116/T120/T122/T124 or mark any Group/M3 Verified.

---

## User Story, Clarification, ROADMAP & acceptance traceability

| Source | Tasks implementing & testing | Acceptance |
| --- | --- | --- |
| **US1 — Tạo Group** | T011–T014, T037–T048, T101–T104, T112/T116 | AC-04-F/I/J |
| **US2 — Mời và join** | T015–T020, T049–T058, T107–T108, T114 | AC-04-B/C/E |
| **US3 — Gửi text/emoji nhóm** | T023–T036, T075–T088, T111–T113/T115 | AC-04-A/B/F/H/K |
| **US4 — Rời/loại/handoff** | T017–T018, T059–T074, T114/T118 | AC-04-C/D/E/H |
| **US5 — Offline, trạng thái/READ** | T021–T022, T089–T100, T093–T096, T117 | AC-04-D/G/J |
| **US6 — Live Group** | T101–T110, T112/T119 | AC-04-F/I/K |
| **CL-04-01 owner relay** | T079–T082, T111–T112, T115 | AC-04-A/B |
| **CL-04-02 member invites + owner approval** | T049–T058, T107–T108, T114 | AC-04-C/E |
| **CL-04-03 voluntary handoff** | T063–T074, T108, T114/T118 | AC-04-D/E/H |
| **CL-04-04 protected metadata** | T037–T048, T116, T118 | AC-04-J |
| **CL-04-05 5 devices** | T013–T014, T079/T087–T088, T113/T117 | AC-04-A/J/K |
| **CL-04-06 per-recipient READ** | T021–T022, T089–T100, T093–T096, T118 | AC-04-G/I |
| **CL-04-07 no backfill** | T057–T058, T083–T086, T114 | AC-04-C/H |
| **CL-04-08 remove at owner commit** | T017–T018, T059–T062, T079/T114 | AC-04-C/H |
| **ROADMAP 04.01** | T011–T022, T063–T074 | AC-04-C/D/H |
| **ROADMAP 04.02** | T049–T074 | AC-04-C/E/H |
| **ROADMAP 04.03** | T075–T088 | AC-04-A/B |
| **ROADMAP 04.04** | T101–T110 | AC-04-F/I |
| **ROADMAP 04.05** | T045–T046, T063–T074, T099–T100 | AC-04-D/G/J |
| **ROADMAP 04.06** | T083–T100 | AC-04-G/H/J |
| **ROADMAP 04.07** | T111–T124 | AC-04-A…L |

## AC-04-A…L acceptance map (required feature Gate 1)

| Criterion | Chứng minh cụ thể | Task lấy evidence |
| --- | --- | --- |
| **AC-04-A** | A/B/C trong Group; B→A→C Unicode, đúng origin, một bubble | T075–T086, T111–T112, T115 |
| **AC-04-B** | D outsider 0 Group content/roster, B origin phải được C independently pinned | T019–T020, T035–T036, T079–T082, T118 |
| **AC-04-C** | Member đề xuất lời mời, owner duyệt, invitee đồng ý; member remove/leave không động P2P | T049–T062, T107–T108, T114 |
| **AC-04-D** | Member/owner offline, voluntary A→B handoff an toàn, không split-brain/EDT treo | T063–T074, T099–T100, T114/T115 |
| **AC-04-E** | Fake owner/invite replay/origin/viewer/key changed/revoked blocked | T023–T036, T049–T060, T073–T074, T118 |
| **AC-04-F** | Tên trùng, alias đổi, UTC timezone và Sender đúng identity | T011–T014, T085–T086, T101–T104 |
| **AC-04-G** | Signed per-recipient app ACK/real foreground READ; wrong/duplicate/stale receipt không promote | T089–T100, T093–T096, T118 |
| **AC-04-H** | JOIN/REMOVE/SEND concurrent và revision/epoch stale; no new post-remove recipient | T017–T018, T059–T062, T079–T080, T114 |
| **AC-04-I** | Live Group vs P2P/Demo, media/history disabled; actual UI statuses | T101–T110, T119 |
| **AC-04-J** | Fuzz/size/queue/5-dev cap, owner crash, DPAPI corruption and restart, no content storage | T037–T048, T071–T074, T087–T088, T113/T116–T118 |
| **AC-04-K** | ≥3 independent JVM + physical Win11 LAN ≥2 PCs, no Internet, real consent/TLS | T111–T115, T119 |
| **AC-04-L** | Reviewed CL/Constitution/independent security + inherited M1/M2 Gate 1; release signoff | T001–T010, T120–T124 |

## Progress and evidence discipline

| Counter | Baseline at task creation |
| --- | --- |
| Total tasks | **124** (T001–T124 contiguous) |
| Completed | **0** |
| Remaining | **124** |
| Design CL | **8/8 CONFIRMED** (planning only) |
| Group automated tests | **NOT CREATED / NOT RUN** |
| Group Windows physical LAN | **NOT RUN** |
| Group independent security review | **NOT RUN** |
| M1/M2 physical LAN + security acceptance | **NOT VERIFIED** |
| Feature 004 / milestone M3 | **PLANNED / NOT VERIFIED** |

**Workflow after this document:** develop incrementally with narrow RED→GREEN commits, record actual results, keep shared M2 changes isolated, then run Group + inherited gates. Task list content alone **does not** complete any task, any Java feature or any runtime acceptance.
