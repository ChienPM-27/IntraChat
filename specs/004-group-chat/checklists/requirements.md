# Requirements Quality Checklist — 004 Group Chat

**Date:** 2026-11-21 · **Status:** DRAFT (spec review, not runtime acceptance)
**Spec:** [spec.md](../spec.md) · **Parent:** [ROADMAP 04](../../../ROADMAP.md)

## Nội dung và tính kiểm thử được

- [x] Chỉ rõ Group thuộc giai đoạn 04, P1, phụ thuộc M1/M2; không đồng nghĩa với Group đã implement hoặc M3 Verified.
- [x] Kế thừa human Accept inbound, manual IPv4/port, TLS1.3/pinned identity, no auto-reconnect, RAM-only chat và phân tách Live/Demo.
- [x] Topology được nêu rõ là **owner relay trong LAN là đề xuất**, không giả định đã phê duyệt hay Group E2EE.
- [x] Có 6 User Stories và kịch bản Given/When/Then cho tạo nhóm, mời/join, gửi, leave/remove, offline, UI.
- [x] Ranh giới quyền membership phân biệt với consent TCP; biết groupId/alias không đủ quyền.
- [x] Định nghĩa sender/groupId/revision/UTC, authenticated routing, dedup và recipient-specific ACK/state.
- [x] Không suy đoán all-delivered/read từ TCP/owner ACK; Group READ per member cần yêu cầu riêng.
- [x] Member offline/owner offline, race JOIN/REMOVE/fan-out, retry thủ công, duplicate và cleanup đều có kịch bản.
- [x] Edge cases bao gồm UTF-8/emoji, version mismatch, unknown member, spoof owner, key changed, malicious/oversize frame.
- [x] Có 40 yêu cầu chức năng (FR-04-001…040), logical entities, 10 tiêu chí định lượng và 12 acceptance criteria (AC-04-A…L).
- [x] Có traceability 04.01…04.07 và Constitution I–VII; nói rõ **NOT RUN** khi chưa kiểm thử.
- [x] Group History (005), File (006), media (007), cloud, auto discovery/failover nằm ngoài scope.

## Clarifications cần người dùng xác nhận trước plan

- [ ] **CL-04-01:** Owner relay hay sender fan-out/full mesh?
- [ ] **CL-04-02:** Owner-only invite/remove hay nhiều vai trò quản trị?
- [ ] **CL-04-03:** Owner offline: paused/no failover hay handoff chủ động?
- [ ] **CL-04-04:** Group metadata/roster RAM-only hay lưu bảo vệ qua restart?
- [ ] **CL-04-05:** Giới hạn Group và thành viên phù hợp quota TLS/RAM?
- [ ] **CL-04-06:** Delivery/READ per-recipient và UX tổng hợp chính thức?
- [ ] **CL-04-07/08:** Backfill khi join muộn và điểm áp dụng revision khi remove?

## Implementation/release — chưa thực hiện

- [ ] `plan.md`: chọn topology, Group protocol contract, epoch/authorization, quotas, threat model, Constitution Gate 0.
- [ ] `tasks.md`: tasks độc lập, RED→GREEN, automated negative tests, review gates.
- [ ] Gate 1 M1 và M2 Verified với evidence thực hai Windows LAN PC và security review độc lập.
- [ ] Group implementation, ≥3 client TLS real tests, Windows physical LAN không Internet, AC-04-A…L verification.
- [ ] Security/privacy review của Group, no chat/roster leaks, release signoff; không coi chưa chạy là PASS.

**Kết luận:** Specification **đã soạn để review**, nhưng các lựa chọn CL-04 còn PENDING. Chưa có Group implementation, plan, tasks hoặc acceptance tests; không đánh dấu Group/M3 Verified.
