# Requirements Quality Checklist — Giai đoạn 05 Chat History & LAN P2P Sync

**Feature:** `005-chat-history` · **Date:** 2026-11-21
**Status:** **SPEC DRAFT; 10/10 Clarify questions OPEN, no decisions user-confirmed yet. No plan/tasks/implementation/test/Verified.**
**Source:** [spec](../spec.md) · [ROADMAP Giai đoạn 05](../../../ROADMAP.md) · [Constitution](../../../.specify/memory/constitution.md).

## 1. Requirements scope, language and consistency

- [x] Tính năng được định nghĩa là **lưu History qua restart và sync giữa verified, authorized peers trong LAN**, không phải “lưu local-only”.
- [x] Có P2P **và** Group History; Group gating phụ thuộc owner/roster/epoch và **004 Group hiện chưa triển khai mạng**.
- [x] Khác biệt giữa **M2/004 RAM-only trước History** và **persist content 005 sau khi được bật** được ghi rõ, không hứa khôi phục tin đã mất.
- [x] Kế thừa CL-01-04, M1 manual IPv4:port/Accept cho inbound, M2 TLS pinned/SAS, M2 verified READ/ACK, Group 004 owner relay, join cutoff và owner remove commit.
- [x] Cấm leak private History qua M1 plaintext, v2 P2P CHAT_TEXT, public endpoint, unverified sender hoặc outsider chỉ biết conversationId.
- [x] Ranh giới ngoài phạm vi tách biệt: cloud/multi-device account, draft/outbox, media, Group E2EE, remote wipe guaranteed, automatic reconnect/election, Demo mock.
- [x] Không tự đánh dấu Group 004/M1/M2/M3 VERIFIED chỉ vì đã viết spec.

## 2. Clarifications — PENDING (user has NOT approved)

- [ ] **CL-05-01** — History opt-in/opt-out mặc định và điểm bắt đầu lưu.
- [ ] **CL-05-02** — Storage engine/mã hóa at rest/DPAPI/wal-temp/ACL và dependency approval.
- [ ] **CL-05-03** — Retention/quota mặc định và dọn dữ liệu theo ngày/dung lượng.
- [ ] **CL-05-04** — Sync thủ công hay tự động sau verified reconnect; xác nhận một/hai đầu.
- [ ] **CL-05-05** — P2P History chỉ 2 verified device IDs hay multi-device account linking.
- [ ] **CL-05-06** — Group sync nguồn owner hiện hành hay authorized member-to-member; mốc JOIN/REMOVE không được hạ.
- [ ] **CL-05-07** — Xóa local vs propagate remote; tombstone và giới hạn “remote wipe”.
- [ ] **CL-05-08** — Chính sách persist/sync delivery/read evidence (không tạo READ giả).
- [ ] **CL-05-09** — Conflict/tombstone policy, immutable event vs edit/delete.
- [ ] **CL-05-10** — Backup/export/migration scope và key recovery.

**Rule:** Cần người dùng xác nhận các lựa chọn trước khi chuyển spec thành implementation plan; “gợi ý baseline” trong spec **không** phải quyết định đã phê duyệt.

## 3. Functional and acceptance coverage

- [x] Có **7 user stories** độc lập với positive/negative Given/When/Then gồm local restore, paging, P2P delta sync, Group ACL, retention/delete, crash/conflict và Live UI truth.
- [x] **48 numbered FR-05** với MUST/MUST NOT, định danh tin/Group, quyền truy cập, persistence/crash, replay, ACL, deletion/privacy, faults/EDT.
- [x] **12 numbered SC-05** có thể đo/đối chiếu với test evidence.
- [x] **16 AC-05-A…P**; A–F giữ yêu cầu ROADMAP, G–P bổ sung Group membership/owner handoff, privacy và release.
- [x] Truy vết đủ **ROADMAP 05.01…05.09** tới FR và AC, không bỏ Sync hoặc quyền theo hội thoại.
- [x] Không gán số mặc định retention/quota/timer hoặc thuật toán storage/crypto chưa được người dùng chốt thành yêu cầu bắt buộc.
- [x] Tính eventual consistency giới hạn theo authorized and reachable peers; không claim global ordering, forever exactly-once hoặc remote erase.
- [x] Group mới join không nhận pre-JOIN events, removed member không nhận future delta, owner handoff verified proof.
- [x] Duplicate/replay same-ID khác payload/signer phải quarantine/reject, không last-write-wins dựa timestamp.
- [x] Không đồng nhất saved/synced với delivered/read; Live/Demo hoàn toàn biệt lập.

## 4. Security, resilience and non-functional test plan quality

- [x] History at rest protected for Windows user; không plaintext leakage ở WAL/temp/log/backup; DPAPI wrong-user corruption fail closed.
- [x] Sync only after verified M2 TLS + explicit History protocol capability và conversation-specific auth; no M1 transport fallback.
- [x] Group History authority cần current Group owner epoch/roster và signed origin/member proof.
- [x] Local delete/tombstone không tự hồi sinh; remote-copy revocation limits được nói đúng.
- [x] Page/queue/frame/disk/memory bounds, cancellation and EDT responsiveness được yêu cầu; giá trị cụ thể chờ plan/performance measurement.
- [x] Error/crash/migration/duplicate/oversized payload/unknown type tests và signed event security tests có trong scenarios.
- [x] 2-peer P2P sync, ≥3 process Group và **2 physical Win11 LAN PCs** là release evidence bắt buộc, không lấy localhost để giả hai PCs.
- [x] Independent dependency, crypto, storage and privacy security review vẫn là release blocker, Constitution Gate 1 không được tự sign off.

## 5. Before implementation — intentionally incomplete

- [ ] Người dùng xác nhận `CL-05-01…10` (có thể thay baseline; sửa spec tương ứng).
- [ ] Lập `plan.md` có Constitution Check I–VII, ADRs, at-rest schema/encryption/migration, versioned History wire contract, event model, sync/replay/ACL, deletion/retention, error and resource budgets.
- [ ] Lập `tasks.md` với RED→GREEN tests và gate dependencies, tránh sửa mã cùng file với công việc M2/004 đang dở.
- [ ] Implement History local store, Live history UI, P2P sync và Group History integration theo đúng các quyết định đã được duyệt.
- [ ] Chạy actual Java21 tests + 2-peer/3-client encrypted LAN, corruption/permissions/negative tests, record AC-05-A…P.
- [ ] Khép lại inherited M1/M2/004 release gates và independent security/privacy review trước khi tuyên bố 005 Verified.
- [ ] M3 Verified **chỉ khi cả 004 Group lẫn 005 History đã Verified**.

**Conclusion:** Spec draft đủ để tiếp tục phiên Clarify; không phải release report, design freeze, code hay test evidence.
