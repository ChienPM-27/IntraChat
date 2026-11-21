# Requirements Quality Checklist — Giai đoạn 05 Chat History & LAN P2P Sync

**Feature:** `005-chat-history` · **Date:** 2026-11-21
**Status:** **10/10 CL-05 CONFIRMED (all A); 22/136 tasks complete, Java21 138/138 tests PASS (16 new domain/ACL), encrypted SQLite/History wire/Group E2E and security release gates BLOCKED; 005 NOT VERIFIED.**
**Source:** [spec](../spec.md) · [choices](../clarifications.md) · [confirmed-decision plan](../plan.md) · [data model](../data-model.md) · [History V1 draft](../contracts/history-v1.md) · [ROADMAP Giai đoạn 05](../../../ROADMAP.md) · [Constitution](../../../.specify/memory/constitution.md).

## 1. Requirements scope, language and consistency

- [x] Tính năng được định nghĩa là **lưu History qua restart và sync giữa verified, authorized peers trong LAN**, không phải “lưu local-only”.
- [x] Có P2P **và** Group History; Group gating phụ thuộc owner/roster/epoch và **004 Group hiện chưa triển khai mạng**.
- [x] Khác biệt giữa **M2/004 RAM-only trước History** và **persist content 005 sau khi được bật** được ghi rõ, không hứa khôi phục tin đã mất.
- [x] Kế thừa CL-01-04, M1 manual IPv4:port/Accept cho inbound, M2 TLS pinned/SAS, M2 verified READ/ACK, Group 004 owner relay, join cutoff và owner remove commit.
- [x] Cấm leak private History qua M1 plaintext, v2 P2P CHAT_TEXT, public endpoint, unverified sender hoặc outsider chỉ biết conversationId.
- [x] Ranh giới ngoài phạm vi tách biệt: cloud/multi-device account, draft/outbox, media, Group E2EE, remote wipe guaranteed, automatic reconnect/election, Demo mock.
- [x] Không tự đánh dấu Group 004/M1/M2/M3 VERIFIED chỉ vì đã viết spec.

## 2. Clarifications — USER CONFIRMED 2026-11-21 (10/10 option A)

- [x] **CL-05-01** — Đã xác nhận A: mặc định opt-in, chỉ ghi tin mới sau consent.
- [x] **CL-05-02** — Đã xác nhận A: fully encrypted SQLite + DPAPI CurrentUser + ACL; security review driver **vẫn đang mở**.
- [x] **CL-05-03** — Đã xác nhận A: 90 ngày và 500 MiB tổng mặc định, áp dụng cả hai giới hạn, có thể điều chỉnh.
- [x] **CL-05-04** — Đã xác nhận A: user bấm Đồng bộ thủ công theo từng conversation.
- [x] **CL-05-05** — Đã xác nhận A: P2P History chỉ hai verified deviceId/SPKI của conversation.
- [x] **CL-05-06** — Đã xác nhận A: current Group owner, có epoch/roster proof và mốc JOIN→REMOVE.
- [x] **CL-05-07** — Đã xác nhận A: local delete + durable tombstone, không remote delete.
- [x] **CL-05-08** — Đã xác nhận A: không persist/restore/sync application ACK/READ receipts.
- [x] **CL-05-09** — Đã xác nhận A: immutable events, same ID different signer/body → quarantine.
- [x] **CL-05-10** — Đã xác nhận A: không tự backup/export; chỉ schema migration an toàn.

**Rule:** Người dùng **đã xác nhận 10/10 phương án A**. Chỉ có native encrypted SQLite driver, crypto/wire/canonical vectors, performance benchmarks và independent review chưa có chứng cứ phê duyệt. Không dùng các việc còn mở để phủ nhận các quyết định CL đã chốt.

## 3. Functional and acceptance coverage

- [x] Có **7 user stories** độc lập với positive/negative Given/When/Then gồm local restore, paging, P2P delta sync, Group ACL, retention/delete, crash/conflict và Live UI truth.
- [x] **48 numbered FR-05** với MUST/MUST NOT, định danh tin/Group, quyền truy cập, persistence/crash, replay, ACL, deletion/privacy, faults/EDT.
- [x] **12 numbered SC-05** có thể đo/đối chiếu với test evidence.
- [x] **16 AC-05-A…P**; A–F giữ yêu cầu ROADMAP, G–P bổ sung Group membership/owner handoff, privacy và release.
- [x] Truy vết đủ **ROADMAP 05.01…05.09** tới FR và AC, không bỏ Sync hoặc quyền theo hội thoại.
- [x] **Đã khóa** các ngưỡng retention/quota user-confirmed: 90 ngày và 500 MiB mặc định, có thể điều chỉnh. Các giá trị batch/timeout/RAM và exact native crypto còn là **technical targets chưa đo/chưa security-review**, không gán thành test PASS.
- [x] Tính eventual consistency giới hạn theo authorized and reachable peers; không claim global ordering, forever exactly-once hoặc remote erase.
- [x] Group mới join không nhận pre-JOIN events, removed member không nhận future delta, owner handoff verified proof.
- [x] Duplicate/replay same-ID khác payload/signer phải quarantine/reject, không last-write-wins dựa timestamp.
- [x] Không đồng nhất saved/synced với delivered/read; **không lưu/sync ACK/READ receipts (CL-05-08)**; Live/Demo hoàn toàn biệt lập.

## 4. Security, resilience and non-functional test plan quality

- [x] Đã chỉ rõ CL-05-02 **fully encrypted SQLite + DPAPI + ACL** và CL-05-10 **no backup/export** (ở mức yêu cầu/plan); kiểm thử WAL/temp/log/other-user còn OPEN, không coi review đã PASS.
- [x] Sync only after verified M2 TLS + explicit History protocol capability và conversation-specific auth; no M1 transport fallback.
- [x] Group History authority cần current Group owner epoch/roster và signed origin/member proof.
- [x] **Local-only delete** + durable tombstone không tự hồi sinh; **không remote delete**; remote-copy revocation limits được nói đúng.
- [x] Page/queue/frame/disk/memory bounds, cancellation and EDT responsiveness được yêu cầu; giá trị cụ thể chờ plan/performance measurement.
- [x] Error/crash/migration/duplicate/oversized payload/unknown type tests và signed event security tests có trong scenarios.
- [x] 2-peer P2P sync, ≥3 process Group và **2 physical Win11 LAN PCs** là release evidence bắt buộc, không lấy localhost để giả hai PCs.
- [x] Independent dependency, crypto, storage and privacy security review vẫn là release blocker, Constitution Gate 1 không được tự sign off.

## 5. Implementation/security gates — intentionally OPEN

- [x] Người dùng đã xác nhận `CL-05-01…10` = **10/10 A**, cập nhật [spec](../spec.md), [decisions](../clarifications.md) và [plan](../plan.md).
- [x] Đã đồng bộ [plan.md](../plan.md) theo **10/10 CL CONFIRMED**, có Constitution I–VII, encrypted SQLite+DPAPI+ACL, History V1 wire draft, event model/ACL/delete/resource gates. **Technical Gate 0 vẫn CONDITIONAL**, vì native driver, exact signature vectors và independent security review chưa được duyệt.
- [x] Đã xây dựng [tasks.md](../tasks.md) **136 tasks**, chia 10 phase/7 US, có RED→GREEN, explicit Depends/Done, file owner và security/physical release gates; **22/136 tasks checked after actual evidence**.
- [ ] Implement History local store, Live history UI, P2P sync và Group History integration theo đúng các quyết định đã được duyệt.
- [ ] Chạy actual Java21 tests + 2-peer/3-client encrypted LAN, corruption/permissions/negative tests, record AC-05-A…P.
- [ ] Khép lại inherited M1/M2/004 release gates và independent security/privacy review trước khi tuyên bố 005 Verified.
- [ ] M3 Verified **chỉ khi cả 004 Group lẫn 005 History đã Verified**.

**Conclusion:** Clarify 10/10 DONE; domain + P2P scope policy RED→GREEN [22/136 tasks](../tasks.md) and [138/138 Java21 JUnit PASS](../evidence/history-domain-red-green.md). Secure driver/crypto/004 dependencies are [verified BLOCKED](../evidence/execution-blockers.md); full History AC/physical LAN tests are NOT RUN; 005/M3 NOT VERIFIED.
