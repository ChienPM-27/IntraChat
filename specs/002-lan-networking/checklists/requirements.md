# Specification Quality Checklist — 002 LAN Networking

**Feature:** [002-lan-networking/spec.md](../spec.md)
**Created:** 2026-11-21
**Purpose:** Review specification completeness under GitHub Spec Kit conventions. This is a **documentation review**, not a claim of implemented networking.

## Scope & Content Quality

- [x] Có mục tiêu M1 rõ ràng và ranh giới trực tiếp P2P, không yêu cầu server/cloud.
- [x] Phân biệt M1 networking/diagnostics với M2 chat, Group, History Sync và Media.
- [x] Giữ 4 quyết định CL-01…04 của Giai đoạn 01 (P2P trực tiếp, Windows, media file, History sync P2P).
- [x] Công bố M0 chưa Verified, còn 3 task T025/T040/T041, không coi là đã sẵn sàng triển khai mạng.
- [x] Đặc tả tập trung WHAT/WHY; **chỉ IP/port thủ công** là quyết định của người dùng; TCP/cổng cụ thể, timeout, phiên bản schema và resource budgets được chọn có lý do trong plan.

## Clarification Decisions — Confirmed 2026-11-21

- [x] **CL-02-01 — Discovery:** chỉ nhập **IP/port thủ công**, **không auto-discovery** trong M1.
- [x] **CL-02-02 — Inbound consent:** mỗi yêu cầu kết nối đến cần **người dùng Chấp nhận/Từ chối**; trước đó chưa thành session hợp lệ; hết hạn/từ chối phải đóng và cleanup.
- [x] **CL-02-03 — Trust:** chỉ **diagnostics chưa xác thực và chưa mã hóa**, cảnh báo rõ; chấp nhận thủ công không phải là pairing/authentication/History authorization.
- [x] **CL-02-04 — Reconnect:** **người dùng chủ động bấm Kết nối lại**; không auto-reconnect, không tạo phiên trùng khi bấm nhiều lần.
- [x] Bốn quyết định đã phản ánh trong User Scenarios, FR, SC, AC và [plan.md](../plan.md). Không xem các nội dung này như lựa chọn thuần kỹ thuật còn chờ quyết định.

## Requirement Completeness

- [x] Có 5 user stories với mức ưu tiên, independent tests và Given/When/Then scenarios.
- [x] Có edge cases về địa chỉ sai, port conflict, peer offline, phiên trùng, frame lỗi, dữ liệu không tin cậy và cleanup.
- [x] Đủ nhóm Functional Requirements về topology, handshake/identity, protocol, state/lifecycle, resource bounds, security/Windows.
- [x] Success criteria có điều kiện đo/test và phân biệt môi trường loopback so với hai máy LAN thực.
- [x] Acceptance traceability AC-02-A…K liên kết tới requirement/scenario.
- [x] Mô hình entity mô tả peer/session/handshake/frame và ranh giới trust; chưa tuyên bố đã có source.
- [x] Không có yêu cầu cloud/public Internet hoặc gọi audio/video realtime.
- [x] Không mặc định `CONNECTED` đồng nghĩa `authenticated`, `delivered/read` hoặc `history synchronized`.
- [x] Đặc tả rủi ro peer chưa xác thực/chưa mã hóa; nhấn mạnh **Chấp nhận kết nối không phải là xác thực hay cấp quyền**; không cho History/File/Group quyền dựa vào ID tự khai.
- [x] Điều kiện kiểm thử nêu loopback hai process, LAN hai máy, invalid inputs, timeout/disconnect, resource cleanup.
- [x] Handoff cho `plan.md` đã được thực hiện; plan có Technical Context, Constitution Check **I–VII** (design-only), kiến trúc, protocol v1, trust boundary và test gates.

## Open Technical Decisions (để `plan.md`, không chặn chất lượng spec)

- [x] `plan.md` chọn TCP/JSON framed, port dự kiến, hard limits, timeouts, session trùng; **chưa triển khai hoặc kiểm thử**.
- [x] **CL-02-01 đã xác nhận:** M1 chỉ nhập IPv4/port thủ công; không auto-discovery.
- [x] **CL-02-02 và CL-02-03 đã xác nhận:** người nhận phải Chấp nhận/Từ chối, không auto-accept; M1 chỉ diagnostics **chưa xác thực/mã hóa** và cảnh báo. Authentication + per-conversation ACL vẫn **chưa có**, là gate riêng trước History Sync 005.
- [ ] **Còn mở (execution gates):** Java 21 runtime thật, nghiệm thu 2 máy LAN và benchmark/resource bounds thực tế, đóng T025/T040/T041 của M0. Plan đã xác định môi trường/tiêu chí, chưa chạy test.

## Status

**Spec quality:** **Clarified — ready for tasks**; 4 quyết định hành vi đã được người dùng xác nhận, [plan.md](../plan.md) có Constitution Check I–VII và ADR. Không còn câu hỏi Clarify bắt buộc trước `tasks.md`.

**Implementation:** NOT STARTED. **Đã có `plan.md`, chưa có `tasks.md`**, networking source hoặc test runtime 002; checklist này chỉ đánh giá chất lượng tài liệu, không xác minh socket/handshake/payload đã chạy.
