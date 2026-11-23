> **Ghi chú phạm vi đồ án (22/11/2026):** [ROADMAP hiện hành](../../ROADMAP.md) · [11 chức năng và nghiệm thu lớp học](../../docs/course-project-scope.md) · [Tích hợp Messenger](../../docs/messenger-integration-plan.md) · [Checklist Group MVP cho môn học](course-mvp.md). **Group 28/92** (core prototype), **chưa có Group Live**; ưu tiên tạo nhóm/invite/fanout 3 peer và nối `CreateGroupDialog` vào `MainChatFrame`, không để audit owner-grant/Gate0 cũ chặn phần code. **Các yêu cầu Gate/audit bảo mật chuyên sâu ở phần lịch sử bên dưới chuyển thành nâng cao, không phải điều kiện để bắt đầu code**. Giữ an toàn tối thiểu, không tắt TLS M2, không giả mạo trạng thái hoặc ghi đè file ngoài ý muốn. **Task checkbox/biên bản Gate cũ không bị đổi thành PASS**; khi có tính năng Live thật hãy ghi test và trạng thái riêng trong [ma trận 11 chức năng](../../docs/feature-matrix.md).

# Tasks — IntraChat Giai đoạn 04: Group Chat Live (M3)

**Feature:** 004-group-chat · **Created:** 2026-11-22
**Status (2026-11-22 latest):** **Core prototype In Progress — 28/92 checked; 64 open** · **8 new local core tasks** (Group authorization, STALE restore/owner revalidation, grant/replay RAM inbox, recipient ACK ledger), full offline Java21 **246/246 JUnit PASS, exit 0** · experimental code authorization **does not satisfy independent-review Gate 0 T004/T010 or Gate 1** · [latest RED→GREEN evidence](evidence/group-core-continued-2026-11-22.md) · [prior core evidence](evidence/group-core-implementation-2026-11-22.md)
**Branch:** Chưa tạo nhánh 004; working tree hiện ở nhánh 003-p2p-messaging và có thay đổi chưa commit; không được reset/commit thay cho người dùng.
**Inputs:** [Spec 004 v0.2.0](spec.md) · [Plan 004 v0.1.0](plan.md) · [ROADMAP](../../ROADMAP.md) · [Constitution](../../.specify/memory/constitution.md) · [M1 tasks](../002-lan-networking/tasks.md) · [M2 tasks](../003-p2p-messaging/tasks.md) · [M2 blockers](../003-p2p-messaging/evidence/execution-blockers-2026-11-22.md)

## Cách dùng task tracker và Definition of Done

- **Định dạng:** `- [ ] Tnnn [P?] [USn?] hành động; đường dẫn/đầu ra. Depends; Done.` `[P]` chỉ những task có thể song song **sau dependencies**, tránh ghi đè cùng file. `[US1]…[US6]` truy vết sáu user stories của [spec](spec.md).
- **Nguồn trạng thái:** **28/92 hoàn tất theo test/tài liệu thực**: 8 task thiết kế Phase 1, 12 task core trước (T011–T020, T033–T034), và 8 task core mới (T021–T022, T037–T038, T057–T058, T065–T066). **Group transport/FE chưa nối, không có Group Live**. Người dùng đã cho phép code prototype nhưng T004/T010 review độc lập vẫn mở; full offline regression mới nhất 246/246 PASS, không chứng minh M3 Verified. Các file còn chưa tồn tại được hiểu là đầu ra dự kiến.
- **Đường dẫn rút gọn:** `domain/`, `application/`, `protocol/`, `transport/`, `security/` bên dưới `src/main/java/vn/intrachat/` hoặc `src/test/java/vn/intrachat/` tùy implementation/test; `ui/`, `listener/`, `model/` nằm dưới `src/main/java/` hoặc `src/test/java/`. Đường dẫn `evidence/`, `contracts/`, `checklists/` tính từ `specs/004-group-chat/`.
- **RED → GREEN đúng nghĩa:** (1) viết test cho hành vi/negative case; (2) thực sự chạy lệnh Java21 và ghi RED expected failure; (3) implement/refactor; (4) chạy focused GREEN và regression thích hợp; (5) ghi command, exit code, số test, timestamps, môi trường, Git hash + dirty state và lỗi vào evidence. **Không check RED hoặc GREEN khi chưa có bằng chứng thực thi**; không làm test yếu đi để pass. Với docs tasks phải có review/lint/traceability.
- **CL đã xác nhận 22/11:** 04-01 direct verified TLS sender fanout, **no content relay**; 04-02 **một owner** thêm/xóa, member tự leave; 04-03 invite **Accept** + owner monotonic signed revision; 04-04 **ACK/READ per recipient**; 04-05 Windows-protected **group metadata persist**, nhưng chat/draft/receipt RAM-only; 04-06 **max 5 members INCLUDING local** (chưa được kiểm nghiệm).
- **Security invariants:** Không Group payload qua M1 v1 plaintext; Group không được bật khi peer chưa verified TLS READY/negotiated Group capability/owner signed roster+grant; không cấp quyền từ tên/IP/UUID tự khai, metadata saved hoặc UI Demo; roster owner signature và owner control ticket không được giả mạo. Owner offline **không cấp ticket mới** → Group send PAUSED; P2P riêng vẫn dùng được. Không hứa instant revocation cho bytes đã in-flight.
- **Release gates (current):** M0 Verified 41/41; M1 **53/60 In Progress**, M2 **65/90 In Progress**, cả hai **NOT VERIFIED**. Baseline khi soạn plan 004 là M1 37/60 và M2 34/90. Test Group local có thể phát triển theo từng slice sau Gate 0, nhưng **004 Gate 1 không Verified** nếu M1/M2 Gate 1 chưa Verified; **M3 tổng còn Spec 005 History**. **Blockers ngoài môi trường:** hai máy Windows vật lý cùng LAN, independent security/dependency reviewer và Windows test account thứ hai cho DPAPI cross-user.
- **Hoàn thành chung:** từng task phải có output thật, test/review đúng, fail-closed, không rò chat/secret, không phá Demo/P2P, không tăng scope Internet/History/File/Media. **Không** tự động đánh dấu hoàn thành task dependent sau khi task upstream PASS.

## Task Index và dependency gates

| Phase | Tasks | Milestone | Gate |
| --- | --- | --- | --- |
| 1 — Setup / research / Constitution Gate 0 | T001–T010 | Chốt protocol/canonical, resources, storage, owner send ticket; review Gate 0 | BLOCKING before source changes |
| 2 — Domain & strict group wire codec | T011–T022 | Typed model, deterministic owner signature, group contract RED→GREEN | After T010 |
| 3 — Secure capabilities / transport | T023–T032 | No legacy downgrade, secure group multiplex, 4-peer quota | After protocol and T010 |
| 4 — Protected group metadata | T033–T040 | Persist only group roster/owner/revision, safe restore | After domain/signature |
| 5 — Owner / invite / membership | T041–T054 | Accepted invitations, signed owner commits, revocation/leave | After secure session/store |
| 6 — Direct fanout & short owner grant | T055–T064 | Correct recipients only; fail-closed owner offline | After owner roster/transport |
| 7 — Per-recipient ACK, READ & RAM safety | T065–T072 | Real recipient-specific delivery/view events | After group send |
| 8 — Live Messenger integration | T073–T080 | Group UI real, Demo isolated, truthful states | After core service |
| 9 — Multi-peer validation / real LAN / release gates | T081–T092 | 3 peers, 2 physical hosts, security, AC-04-A…L | Gate 1 BLOCKED externally |

## Phase 1 — Baseline, research, version contract và Gate 0

- [x] T001 Audit Git worktree, M0/M1/M2 tasks, source và evidence trước 004; lập evidence/prerequisite-status.md. **Done:** chính xác M0 41/41, M1 37/60, M2 34/90 tại baseline; ghi blockers, branch/dirty state, không tự ký Gate 1.
- [x] T002 [P] Rà soát CL-04-01…06 trong spec.md và plan.md so với ROADMAP/Constitution/FE Demo; ghi evidence/clarification-review.md. **Done:** 6/6 đúng (fanout, owner, accept+revision, per-recipient receipts, DPAPI metadata/RAM content, max 5), không thay đổi ý nghĩa.
- [x] T003 [P] Viết research.md + threat model (owner forged/revoked, groupId spoof, stale revision under partition, forged grant/READ, replay, recipient leak). **Done:** trust boundary, asset, attack tree, controls, residual risk, reviewer và test hooks theo Constitution IV.
- [ ] T004 [P] Kiểm tra thực tế Java21 JSSE, ECDSA JCA/Bouncy Castle, DPAPI/JNA dependency, license/NOTICE/CVE/SBOM/provenance trước code crypto mới; evidence/dependency-security-review.md. **Done:** phiên bản, resolved graph và findings có quyết định reviewer, không tự cho dependency “approved”.
- [x] T005 [P] Phân tích source quota 4 verified peers, NetworkConfig, TlsChatSession/LiveChatService, queue/threads, hai mode v1/v2; research.md. **Done:** feasibility 5 members, 4 sockets shared P2P/Group, cleanup/capacity risks, không tạo hidden relay.
- [x] T006 [P] Định nghĩa deterministic owner ECDSA canonical signed bytes, groupId/key binding, roster revision hash chain và 10s per-message ticket với test vectors; research.md. **Done:** replay, rollback, clock skew, grant-vs-remove race và owner offline đều có phương án fail-closed và reviewer.
- [x] T007 [P] Viết contracts/group-v1.md: explicit new profile/capability, legacy v1/v2 negative interop, fields/sizes/signature, invite/commit/grant/text/ACK/READ/errors. **Done:** byte-level schema, strict rejects/version negotiation transcript, no group before TLS READY; chưa coi contract là triển khai.
- [x] T008 [P] Viết data-model.md: verified owner/key, group/roster/revision, per-recipient attempt, protected metadata schema, RAM-only chat, store recovery/deletion. **Done:** 2–5 members, atomic commit, changed-pin, restart STALE, schema version, field validation và code mapping có test plan.
- [x] T009 [P] Viết checklists/requirements.md và quickstart.md **dưới dạng NOT TESTED** cho test 3-process, 2 physical Windows, owner offline, DPAPI cross-user, 5-cycle GUI, p95/quotas. **Done:** không gắn PASS giả, không ghi credentials/messages thật, trace US1–6/AC-04-A…L.
- [ ] T010 Gate 0 Constitution I–VII: độc lập review threat model, ADR owner ticket/availability, version/canonical signatures, metadata DPAPI, numeric budgets; lập checklists/constitution-check.md. **Depends:** T001–T009. **Done:** reviewer/date/verdict PASS/CONDITIONAL/BLOCKED, các issue MUST được xử lý hoặc ghi blocker, **signoff được cho phép triển khai Group security code**; chưa nhầm với Gate 1.

**Phạm vi phê duyệt của người dùng (22/11/2026):** Cho phép tiếp tục phát triển và chạy test **prototype** trước khi reviewer độc lập sẵn có; quyết định này **không** đáp ứng T004/T010 hoặc cho phép enable Group Live/release. Các task T011–T020 và T033–T034 chỉ được check dựa trên mã/test Java21 thực đã chạy; phần dispatch M1/v2/v3, remote invitation, TLS group sender và UI vẫn chưa làm. [Bằng chứng](evidence/group-core-implementation-2026-11-22.md).

## Phase 2 — Immutable domain, canonical signing và strict wire contract

- [x] T011 [P] [US1] RED test domain/GroupIdentityTest.java: group ID khác P2P, owner verified ID/SPKI, title/Unicode/empty, duplicates, zero IDs. **Depends:** T010. **Done:** expected RED run/exit được ghi evidence/group-domain-tests.md.
- [x] T012 [US1] Implement domain/GroupIdentity.java, GroupMember.java, GroupMetadata.java immutable typed/validated; GREEN T011. **Depends:** T011/T008. **Done:** không dùng alias/IP làm identity, unit test Java21 pass.
- [x] T013 [P] [US1] RED domain/GroupMembershipCapacityTest.java: 2–5 unique members/owner one, sixth rejected, duplicate ID/key and invalid revision. **Depends:** T010. **Done:** có boundary RED và tiêu chí 5 gồm local.
- [x] T014 [US1] Implement domain/GroupMembership.java/GroupRoster.java enforce max 5, strict role/monotonic revision; GREEN T013. **Depends:** T012/T013. **Done:** one owner, immutable snapshot, không silently trim members.
- [x] T015 [P] [US3] RED security/GroupCanonicalBytesTest.java với fixed vectors cho ROSTER và GRANT, field order, unicode, hash chain, timestamp, purpose separation. **Depends:** T007/T010. **Done:** RED + known-good vectors lưu contracts/group-v1.md.
- [x] T016 [US3] Implement canonical serializer và owner ECDSA signing port trong protocol/security; GREEN T015. **Depends:** T015/T012/T014. **Done:** deterministic bytes, không export private key, signature không chỉ là JSON unsigned.
- [x] T017 [P] [US3] RED security/GroupOwnerSignatureTest.java: forged owner ID/SPKI, altered roster, wrong groupId/rev/previousHash, replay, wrong signature/algorithm. **Depends:** T016. **Done:** negative matrix RED có command/cases.
- [x] T018 [US3] Implement strict owner signature verification và revision/hash-chain acceptance, GREEN T017. **Depends:** T017/T016. **Done:** owner từ pinned cert, không tin ID tự khai; reject stale/forged trước state change.
- [x] T019 [P] [US2] RED protocol/GroupV1CodecTest.java: types, version, invalid UTF-8/JSON, duplicate keys, unknown fields, 65,536B cap, malformed UUID/time, 32KiB text/emoji. **Depends:** T007/T010. **Done:** RED artifacts và chặn frame trước allocation.
- [x] T020 [US2] Implement protocol/GroupV1Message.java + GroupV1Codec.java strict typed decoder/encoder, GREEN T019. **Depends:** T019/T012. **Done:** exact schema, no native deserialization, không thay ChatV2Codec accept unknown group.
- [x] T021 [P] [US6] RED protocol/GroupV1AuthorizationEnvelopeTest.java: sender/destination/session binding, no M1 plaintext, group-vs-P2P destination, reject codes safe. **Depends:** T020. **Done:** input xấu không thành GROUP_TEXT hoặc ACK.
- [x] T022 [US6] Implement validation guards/errors và deterministic group routing metadata, GREEN T021. **Depends:** T020/T021/T018. **Done:** signed owner context bắt buộc và semantic mismatch trả lỗi an toàn, không làm lộ payload.

## Phase 3 — Explicit capability, TLS routing, bounded transport

- [ ] T023 [P] [US6] RED transport/GroupLegacyCompatibilityTest.java: M1 v1 rejects group; old M2 v2 strict peer-chat receives zero unsolicited Group V1 frames; no downgrade. **Depends:** T010/T007. **Done:** RED negative/compat evidence và giữ test M1/M2.
- [ ] T024 [US6] Refactor version-aware bootstrap/dispatch path có negotiation profile rõ ràng, giữ M1 diagnostics và M2 v2; GREEN T023. **Depends:** T023/T020, M2 shared-v1-v2 dispatcher prerequisite. **Done:** v1 plaintext never group, M2 P2P không regress, unknown version fail closed.
- [ ] T025 [P] [US6] RED transport/GroupCapabilityNegotiationTest.java cho bilateral GROUP_V1 support, transcript/TLS READY, legacy v2 only, mismatched cap/session, changed-key. **Depends:** T024. **Done:** chỉ negotiated+verified mới nhận group.
- [ ] T026 [US6] Implement transport/SecureSessionRouter.java (hoặc tương đương) multiplexer Group V1/P2P theo negotiated capability và exact authenticated session; GREEN T025. **Depends:** T025/T024/T022. **Done:** no group on quarantined/unknown peer, P2P v2 vẫn chạy.
- [ ] T027 [P] [US2] RED transport/GroupSessionBindingTest.java: wrong sender/destination/session, duplicate simultaneous connect, TLS not READY, different pinned key, inbound reject. **Depends:** T026. **Done:** không authorized từ socket connected hoặc claimed UUID.
- [ ] T028 [US2] Implement secure group dispatch and lifecycle guard trên existing TlsChatSession/Coordinator (không thêm socket mỗi group); GREEN T027. **Depends:** T027/T026. **Done:** link identity là cert/TLS confirmed, Close/cancel thu hồi.
- [ ] T029 [P] [US5] RED transport/SharedGroupSessionQuotaTest.java: 4 remotes, 5th remote reject, 4 pending consent, shared queue 16 frames/2MiB; Group+P2P đồng thời. **Depends:** T028. **Done:** test over-budget không freeze/worker leak.
- [ ] T030 [US5] Implement global shared session/queue quotas & bounded task cancellation, GREEN T029. **Depends:** T029/T028. **Done:** virtual threads không trở thành unbounded producer, không starvation P2P.
- [ ] T031 [P] [US6] RED transport/GroupTlsNegativeTest.java: changed-key, forged SAS, plain frame, unexpected version, peer reset giữa negotiation, cross-session replay. **Depends:** T026/T028. **Done:** không payload group trước human READY.
- [ ] T032 [US6] Hardening secure session state/timeout, safe disconnect, manual reconnect only, evidence/group-transport-tests.md; GREEN T031. **Depends:** T031/T030. **Done:** run tests with real JSSE sockets, no trust-all, no accidental owner relay.

## Phase 4 — Windows-protected group metadata and RAM privacy

- [x] T033 [P] [US5] RED security/ProtectedGroupStoreTest.java: create, restart, owner/key/roster/revision save, Windows CurrentUser DPAPI owner-only ACL, no plaintext. **Depends:** T014/T018/T010. **Done:** RED with real Windows store path and isolated test profiles.
- [x] T034 [US5] Implement security/ProtectedGroupStore.java using narrow DPAPI+atomic write API, schema/key/owner verification; GREEN T033. **Depends:** T033/T016. **Done:** metadata survives restart; user content never stored.
- [ ] T035 [P] [US5] RED security/GroupStoreCorruptionTest.java: missing vs corrupt, truncated cipher, wrong schema, duplicate member/owner, >5, ACL failure, tamper signature, cross-user. **Depends:** T034. **Done:** second Windows account marked BLOCKED until authorized; other cases execute.
- [ ] T036 [US5] Implement fail-closed load, schema strict validation, atomic replace/rollback on I/O failure, bounded encrypted file (≤1MiB); GREEN T035 locally. **Depends:** T035/T034. **Done:** no plaintext fallback, no silent trusted recreation.
- [x] T037 [P] [US5] RED security/GroupRestoreStaleTest.java: backup of older ciphertext restored, owner offline, changed pin, stale highwater, wrong owner. **Depends:** T036. **Done:** stored metadata never alone authorizes send.
- [x] T038 [US5] Implement Group restore state STALE/PENDING_RESYNC and owner revalidation, no automatic reconnect or group grant from disk; GREEN T037. **Depends:** T037/T018/T032. **Done:** rollback cannot grant new send while owner unreachable.
- [ ] T039 [P] [US5] RED application/GroupRamPrivacyTest.java: after close/restart zero chat/draft/attempt/ACK/READ/grants files, metadata remains; clear local group/leave semantics. **Depends:** T036. **Done:** inspect expected storage and logs, not user private conversations.
- [ ] T040 [US5] Implement group lifecycle RAM cleanup, user-visible local delete and protected store error UX; GREEN T039; evidence/group-dpapi-privacy.md. **Depends:** T038/T039. **Done:** no Group History Sync, remote recipients may retain already seen data.

## Phase 5 — US1/US3: invitation, single-owner authority and committed roster

- [ ] T041 [P] [US1] RED application/GroupInviteTest.java: invite with verified owner, accept/reject/expire/cancel, wrong invitee/session, no autoaccept. **Depends:** T022/T032/T040. **Done:** invite pending never creates active member.
- [ ] T042 [US1] Implement GroupInvitation state/expiry, group invitation transport service & consent port; GREEN T041. **Depends:** T041. **Done:** explicit user Accept is bound to verified TLS session, no data before commit.
- [ ] T043 [P] [US3] RED application/GroupOwnerAuthorizationTest.java: only authenticated creator add/remove/rename, member may self-leave, owner offline, wrong owner key/role rejected. **Depends:** T018/T042. **Done:** no group admin by alias/IP/UUID alone.
- [ ] T044 [US3] Implement application/GroupAuthority.java owner actor/ACL, one-owner rule, bounded command queue; GREEN T043. **Depends:** T043/T042/T014. **Done:** owner control only, never relay chat text.
- [ ] T045 [P] [US3] RED application/GroupRosterCommitTest.java: Accept prerequisite, owner signed N→N+1, previous hash, two concurrent edits, persist failure, no rollback. **Depends:** T044/T040. **Done:** RED concurrency/atomic scenarios recorded.
- [ ] T046 [US3] Implement serial owner roster commit with sign→atomic persist→publish; GREEN T045. **Depends:** T045/T044/T036. **Done:** no commit on failed store, unique revision monotonic and exactly one owner.
- [ ] T047 [P] [US3] RED application/GroupRosterApplyTest.java: offline recipient, stale replay, out-of-order/hash gap, wrong owner, ACK only after protected commit. **Depends:** T046. **Done:** invalid updates never enable send.
- [ ] T048 [US3] Implement receiver validate/apply/resync and GROUP_ROSTER_ACK; GREEN T047. **Depends:** T047/T038/T046. **Done:** stale member must revalidate, no auto history sync.
- [ ] T049 [P] [US1] RED application/GroupJoinCapacityTest.java: max **5 including local**, reject 6th, duplicate deviceId/key/alias collision, rejected invite and late Accept. **Depends:** T048. **Done:** deterministic boundary and human-approval test.
- [ ] T050 [US1] Implement owner add/join after Accept and group capacity enforcement, GREEN T049. **Depends:** T049/T046/T048. **Done:** member authorized only at signed committed revision, title/displayName not ACL.
- [ ] T051 [P] [US3] RED application/GroupLeaveRemovalTest.java: member self-leave, owner remove, removed peer after commit blocked, in-flight prior grant, owner offline LEAVE_PENDING. **Depends:** T050. **Done:** no false guarantee retroactive erasure.
- [ ] T052 [US3] Implement leave/remove and revocation gates, owner-offline pending member state; GREEN T051. **Depends:** T051/T050. **Done:** no new grant to removed peer post-commit, state monotonic.
- [ ] T053 [P] [US3] RED application/GroupOwnerFailureTest.java: owner key changed/revoked, owner crash, rename authority, group with one remaining member, split-brain revision. **Depends:** T052. **Done:** no auto election/unauthorized send.
- [ ] T054 [US3] Implement rename, owner failure/readonly policy, group lifecycle edge state and safe errors; GREEN T053; evidence/group-owner-membership.md. **Depends:** T053/T052. **Done:** no content relay and no bypass to verified P2P without group authorization.

## Phase 6 — US2/US5: owner ticket, direct TLS fanout, partial failure

- [ ] T055 [P] [US2] Viết RED application/GroupGrantRequestTest.java: active sender, roster đúng revision, exact recipient set, owner reachability; GRANT_REQUEST không chứa chat text. **Depends:** T054/T020. **Done:** ghi RED cho outsider, stale roster, owner offline và recipient sai.
- [ ] T056 [US2] Implement owner cấp signed GROUP_SEND_GRANT gắn groupId/messageId/revision/senderId/recipients/nonce/expiry ≤10s; GREEN T055. **Depends:** T055/T016/T046. **Done:** owner chỉ thấy control metadata, không relay nội dung.
- [x] T057 [P] [US6] Viết RED application/GroupGrantValidationTest.java: forged signature/owner SPKI, expired/replayed ticket, wrong group/sender/recipient/session/revision, grant-vs-remove race. **Depends:** T056. **Done:** negative vectors và clock-skew được ghi.
- [x] T058 [US6] Implement receiver ticket validation + bounded nonce/message replay ledger, require owner pin and current signed roster; GREEN T057. **Depends:** T057/T048/T018. **Done:** invalid/stale grant không cho nhập RAM hoặc ACK.
- [ ] T059 [P] [US2] Viết RED application/GroupFanoutIntegrationTest.java: A/B/C distinct verified TLS peers, mỗi người gửi cho hai người còn lại text tiếng Việt/emoji; D không member nhận 0. **Depends:** T058/T032. **Done:** test sử dụng real JSSE sockets, không mock echo.
- [ ] T060 [US2] Implement GroupChatService.java phát từng GROUP_TEXT trực tiếp đến từng recipient, group RAM transcript và authenticated sender binding; GREEN T059. **Depends:** T059/T056/T058. **Done:** đúng tập recipient, không relay/no P2P conversation mixing.
- [ ] T061 [P] [US6] Viết RED application/GroupRoutingAuthorizationTest.java: forged sender, guessed groupId, removed member, wrong recipient, cross-P2P message, unknown type. **Depends:** T060. **Done:** unauthorized frame không tạo bubble hoặc ACK.
- [ ] T062 [US6] Implement inbound group demux, authorization before RAM acceptance và safe error codes; GREEN T061. **Depends:** T061/T026/T060. **Done:** v1 diagnostic và legacy v2 P2P vẫn strict.
- [ ] T063 [P] [US5] Viết RED application/GroupPartialFailureTest.java: B offline/C online, owner offline, lost ACK, no auto reconnect/outbox, manual retry duplicate warning. **Depends:** T060/T062. **Done:** trạng thái per-recipient trung thực, không ảnh hưởng P2P.
- [ ] T064 [US5] Implement bounded per-recipient fanout attempts, cancellation và manual send/retry, trạng thái PAUSED khi owner unavailable; GREEN T063. **Depends:** T063/T030/T062. **Done:** không optimistic everyone-delivered, không hidden autosend.

## Phase 7 — US4: per-recipient ACK, READ và RAM safety

- [x] T065 [P] [US4] Viết RED application/GroupDeliveryAckTest.java: ACK chỉ sau accepted-in-RAM, B ACK/C offline, wrong group/message/recipient/session/revision. **Depends:** T064. **Done:** no delivered on socket write, no fake aggregate.
- [x] T066 [US4] Implement GroupRecipientLedger.java + GROUP_DELIVERY_ACK keyed by messageId/recipientId/groupId/sessionId; GREEN T065. **Depends:** T065/T062. **Done:** mỗi recipient độc lập, pending bounded và RAM-only.
- [ ] T067 [P] [US4] Viết RED ui/GroupForegroundVisibilityTest.java: đúng group trong Live foreground/visible viewport/dwell, exclude offscreen, wrong conversation, preview, background, Demo, outgoing. **Depends:** T066. **Done:** RED deterministic clock và negative cases.
- [ ] T068 [US4] Implement group-aware LiveMessageVisibilityObserver / GROUP_READ_RECEIPT, GREEN T067. **Depends:** T067/T066. **Done:** READ chỉ chứng minh hiển thị ở ứng dụng người nhận.
- [ ] T069 [P] [US4] Viết RED application/GroupReceiptCorrelationTest.java: forged ACK/READ, duplicate/out-of-order, wrong sender/recipient/session/group/revision, READ-before-ACK. **Depends:** T068. **Done:** không nâng/hạ sai recipient status.
- [ ] T070 [US4] Implement exact correlation, monotonic per-recipient status và expiry/UNCERTAIN logic; GREEN T069. **Depends:** T069/T066/T068. **Done:** không suy ra everyone-read từ một receipt.
- [ ] T071 [P] [US4] Viết RED application/GroupRamCapacityTest.java: 500 messages/2MiB mỗi group, 8MiB toàn app, 128 pending recipient attempts, 1,024 dedup IDs/group, 8 active/16 stored groups, restart clears chat. **Depends:** T070/T040. **Done:** actual RED boundaries, no persistence test.
- [ ] T072 [US4] Enforce group/global memory and dedup bounds, timeout/cleanup, no offline receipt replay; GREEN T071, evidence/group-receipts-ui.md. **Depends:** T071/T070/T030. **Done:** no unbounded queues, no cross-restart exactly-once claim.

## Phase 8 — US1/US3/US6: Live Messenger Group UI, Demo isolation

- [ ] T073 [P] [US6] Viết RED ui/LiveGroupDemoIsolationTest.java: Demo seeded Group/timed fake receipts không xuất hiện Live; Group Live bị disable trước khi verified/capability; media/history vẫn unsupported. **Depends:** T072. **Done:** existing Demo & P2P FE tests unaffected.
- [ ] T074 [US6] Add GroupActionPort.java + extend LiveChatAdapter.java typed group snapshots/commands/events; GREEN T073. **Depends:** T073/T060/T070. **Done:** real member IDs, no mock delivery/read trong Live.
- [ ] T075 [P] [US1] Viết RED ui/LiveGroupCreateInviteTest.java: create chỉ với verified peers, max 5, duplicate names, invite Accept/Reject/Expire, cancel/focus keyboard. **Depends:** T074/T050. **Done:** không create active group từ một click khi chưa commit.
- [ ] T076 [US1] Extend MainChatFrame/CreateGroupDialog; add GroupInviteDialog consent flow cho Live; GREEN T075. **Depends:** T075/T074/T042. **Done:** human Accept, pending/rejected/expired visible, no EDT blocking.
- [ ] T077 [P] [US3] Viết RED ui/GroupRosterPermissionsTest.java: owner-only add/remove/rename, member self leave, stale restored metadata, removed member, owner offline, 5 cap. **Depends:** T076/T054. **Done:** online peer ≠ Group authorized.
- [ ] T078 [US3] Implement GroupMembersDialog và roster/owner/revision/STALE states trong Messenger, GREEN T077. **Depends:** T077/T038/T054. **Done:** ID chi tiết phân biệt cùng displayName; restart không restore messages.
- [ ] T079 [P] [US4] Viết RED ui/GroupBubbleReceiptPresentationTest.java: group ≠ P2P, verified sender/time/Unicode, per-recipient ACK/READ/FAILED/UNCERTAIN, partial fail, focus/viewport accessibility. **Depends:** T078/T072. **Done:** actual no-false-READ UI test cases.
- [ ] T080 [US4] Implement group bubbles, per-recipient delivery detail, owner-offline/manual retry warnings và keyboard/focus labels; GREEN T079. **Depends:** T079/T078/T068. **Done:** real group viewport triggers READ, Demo unchanged.

## Phase 9 — Multi-peer verification, LAN thật và Gate 1

- [ ] T081 [P] [US2] Chạy 3-process/3-identity Java21 loopback với TLS+SAS/Accept, A↔B↔C 3 hướng, group invite/text/emoji; evidence/group-3peer-loopback.md. **Depends:** T080/T032/T060. **Done:** actual commands/exits, isolated profiles/PIDs; localhost KHÔNG phải 2 PCs.
- [ ] T082 [P] [US3] Chạy negative outsider D/forged owner/replay/stale revision/changed key/6th member và packet authorization tests; evidence/group-owner-security.md. **Depends:** T081/T070/T054. **Done:** zero unauthorized new deliveries, evidence 100% required cases.
- [ ] T083 [US5] Thử owner offline/leave/remove/add race/partition/stale store/partial ACK/manual retry/restart; evidence/group-owner-failure.md. **Depends:** T081/T082. **Done:** Group send PAUSED nếu owner không cấp ticket; P2P tiếp tục, không resource leak.
- [ ] T084 [US5] Thử 5-member full mesh, 4 verified remotes, 8 active/16 persisted groups, queue/dedup/RAM caps, ≥5 GUI cycles, EDT p95≤200ms; evidence/group-resource-stress.md. **Depends:** T080/T083. **Done:** ghi số đo thật với môi trường/exit, không nhận target là achieved khi chưa đo.
- [ ] T085 [P] [US6] Audit log/disk/privacy, DPAPI ACL, corrupt blob và **second authorized Windows user** cross-user decrypt; evidence/group-dpapi-privacy.md. **Depends:** T040/T083. **Done:** cross-user chạy thật hoặc BLOCKED, không auto fallback/plaintext, group data chỉ metadata persist.
- [ ] T086 [P] [US6] Chạy regression M1 v1 + M2 v2 cùng Group V1 interoperability/negative tests và real TLS; evidence/group-compatibility.md. **Depends:** T080/T032. **Done:** no unsolicited group frames to old M2, zero M1 group plaintext, no silent downgrade.
- [ ] T087 [US1] Lập runbook quickstart.md cho LAN không Internet: ≥2 Windows 11 physical hosts và ≥3 process/verified identities, IP/port, private firewall có đồng ý, SAS hai phía, owner/member UX; ghi NOT RUN cho bước chưa test. **Depends:** T081/T086. **Done:** host inventory, steps và evidence placeholders, không tự thay Public Firewall.
- [ ] T088 [US2] **[EXTERNAL BLOCKER]** Thực thi acceptance trên ≥2 Windows 11 PC vật lý, ≥3 verified process identities (A/B/C), tắt public Internet, owner/invite/3-direction text, remove/leave/offline; evidence/group-2host-lan.md. **Depends:** T087/T083. **Done:** per-host manifest, IP/JDK/profiles, tests AC-04-A/B/C/D/J được quan sát; localhost không thay được.
- [ ] T089 [US4] Kiểm thử per-recipient ACK→actual group foreground READ trên LAN, wrong group/offscreen/background và partial delivery; evidence/group-receipts-ui.md. **Depends:** T088/T080. **Done:** chứng cứ recipient/message/group/session chính xác, không khẳng định human gaze.
- [ ] T090 **[EXTERNAL REVIEW]** Independent security/dependency/privacy review: owner grant/signature, capability TLS pin/SAS, revocation partition/race, DPAPI cross-user, replay/log/SBOM/CVE/license; evidence/group-security-review.md. **Depends:** T082/T085/T086/T089. **Done:** reviewer/date/findings/disposition, no author self-approval, violations MUST block.
- [ ] T091 Full Temurin21 clean package + M0/M1/M2/Group regression, shaded JAR diagnostics, docs README/ROADMAP/architecture/feature-matrix/quickstart sync theo **test thực**; evidence/group-regression.md. **Depends:** T084/T086/T088/T090. **Done:** exit/test counts, QA links, no fake Group History/File/Media or M3 Verified.
- [ ] T092 **[FINAL GATE / EXTERNAL BLOCKER]** Review all task DoDs + AC-04-A…L + Constitution I–VII Gate1 trong evidence/group-acceptance-report.md; kiểm tra M1/M2 Gate1 Verified và independent maintainer signoff. **Depends:** T001–T091 (mọi task/evidence bắt buộc). **Done:** 12/12 AC có evidence link, zero unmitigated MUST, named reviewers/date; chỉ sau đó 004 mới có thể Verified; **M3 tổng vẫn phụ thuộc Spec 005 History**.

## Traceability — AC-04-A…L → Task/evidence

| AC | Task covering requirement | Current |
| --- | --- | --- |
| AC-04-A — 3 verified peers Group text | T041–T050, T055–T062, T075–T076, T081, T088 | NOT RUN |
| AC-04-B — Nonmember, forged owner blocked | T017–T022, T041–T058, T061–T062, T082/T088 | NOT RUN |
| AC-04-C — Invite, revision, leave/remove | T041–T054, T075–T078, T083/T088 | NOT RUN |
| AC-04-D — Offline/partial/failure resilience | T029–T032, T051–T064, T083–T084/T088 | NOT RUN |
| AC-04-E — Correct sender/time/Unicode/Demo | T011–T022, T059–T062, T073–T080/T081 | NOT RUN |
| AC-04-F — per-recipient ACK/READ | T065–T070, T079–T080, T089 | NOT RUN |
| AC-04-G — replay/duplicate/forged receipt | T015–T022, T047–T058, T069–T072, T082 | NOT RUN |
| AC-04-H — RAM chat, persistent metadata, retry | T033–T040, T063–T064, T071–T072, T083/T085 | NOT RUN |
| AC-04-I — FE Live/Demo isolation | T073–T080, T086/T091 | NOT RUN |
| AC-04-J — >=2 real Windows hosts/3 identities | T081/T087/T088 | **BLOCKED**: host B |
| AC-04-K — Java21/security/stress review | T001–T010, T081–T090 | **BLOCKED**: reviewer/cross-user |
| AC-04-L — upstream M1/M2 & 004 Gate1 | T010, T090–T092 + [M1 acceptance](../002-lan-networking/evidence/m1-acceptance-report.md) + [M2 acceptance](../003-p2p-messaging/evidence/m2-acceptance-report.md) | **BLOCKED**: M1/M2 |

## External blockers / no false PASS

- **B1:** T087–T089/T092 require two **physical** Windows LAN hosts and three independent peer identities. A/B/C localhost only satisfies local integration, not AC-04-J.
- **B2:** T004/T010/T090/T092 require qualified independent reviewer for owner tickets/signatures, TLS pin, DPAPI, licenses, CVEs and Constitution.
- **B3:** T035/T085 cross-user DPAPI needs a second **authorized Windows logon token**; otherwise evidence must say BLOCKED.
- **B4:** M1 37/60 and M2 34/90 still **IN PROGRESS** and Gate1 NOT VERIFIED; M3 total also includes Spec 005 History. Code can progress after Gate 0 but **004 Verified** cannot.
- **STOP** if Group bytes sent via M1 plaintext; unverified peer/forged owner accepted; ticket after revoked revision permits unauthorized new content; false READ; chat/draft/receipt persisted; group queue unbounded; UI fakes Demo Live delivery. Preserve evidence, fix and rerun RED→GREEN before continuing.

**Creation baseline:** 0/92. **Earlier snapshot (2026-11-22):** 20/92 checked: 8 docs ([phase1 evidence](evidence/phase1-document-completion.md)) + 12 Group core RED→GREEN ([initial source/test evidence](evidence/group-core-implementation-2026-11-22.md)); then total test count 141/141 PASS. **Latest 2026-11-22: 28/92 checked, 64 open, 23 additional Group JUnit cases, full Java21 246/246 PASS**, [latest source/test evidence](evidence/group-core-continued-2026-11-22.md). T004/T010 independent review and Gate 1 remain BLOCKED; Group v3 TLS/Live UI still disabled; no Git commit.

## Local core implementation continuation — 2026-11-22

**Newly checked (8): T021, T022, T037, T038, T057, T058, T065, T066**; 28/92 checked, 64 open. [Actual RED→GREEN classes, test commands, security limits and full Java21 result](evidence/group-core-continued-2026-11-22.md).

- T021–T022: strict `GroupV1AuthorizationEnvelope` with canonical signed owner grant parsing; only explicitly verified READY TLS + Group capability and owner-signed fresh roster may pass `GROUP_TEXT` to the *core guard*. No Legacy v1/v2 routing enabled.
- T037–T038: `GroupRestoreGuard` starts STALE, explicitly resyncs with authenticated owner/key and N→N+1 signed chain, persists before ACTIVE; missing owner/new trust never restores send authority.
- T057–T058: `GroupInboundMailbox` validates signed ticket, exact member/recipient, bounded replay IDs/nonces and content caps; emits only an accepted-into-RAM typed ACK. No Group socket dispatch or owner content relay.
- T065–T066: `GroupRecipientLedger` uses per-recipient verified session and independent status; test revealed and fixed ACK→READ double-free affecting another recipient's pending quota. Exactly 128 pending slots; no synthetic READ or auto-send.
- **T035/T036 remain OPEN**: several new Windows DPAPI corruption, schema/ACL/atomic-safety tests already GREEN against existing implementation, plus explicit nonregular/cipher-size hardening. No invented RED or second Windows user test; NTFS symlink creation permission unavailable. **T023–T032, T039–T056, T059–T064, T067–T092 remain OPEN except the eight checked IDs**. Full network/router, group invitation and GUI acceptance cannot be inferred from local core.
- **Final actual verification:** `scripts/verify-m2.ps1 -Offline` on Temurin21, **246/246 JUnit PASS, 0 skipped/failures/errors, shaded JAR and unified launcher PASS, exit 0**; user worktree preserved (no commit/reset/stash).