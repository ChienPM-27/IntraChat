# Tasks 005 — History (đồ án)

**Status (22/11/2026): 0/10 checked — chưa code.** Mọi task chỉ check sau khi source/test thực hiện; không lấy Demo hoặc transcript RAM làm bằng chứng History bền vững.

- [ ] T001 Viết test tạo/lưu/tải/restart trực tiếp và nhóm, đúng UUID Sender/Time; ghi RED.
- [ ] T002 Tạo repository/schema SQLite local, index theo conversation/time/messageId.
- [ ] T003 Kiểm tra insert idempotent theo messageId; test không có 2 bubble sau load + live event.
- [ ] T004 Tạo HistoryService worker async và API load/delete theo conversation; không chạy DB trên EDT.
- [ ] T005 Hook tin `LiveChatService` gửi/nhận thật vào HistoryRepository (không ghi mock).
- [ ] T006 Cập nhật `LiveChatAdapter.loadHistory` và `MainChatFrame` để đọc History bền vững.
- [ ] T007 Thêm thao tác xóa lịch sử trong Messenger và test không xóa nhầm peer khác.
- [ ] T008 Test restart, file DB hỏng/khóa, đóng app và không nhầm Demo/Live.
- [ ] T009 Smoke hai JVM gửi tin rồi khởi động lại, chứng minh F05 và metadata; ghi evidence.
- [ ] T010 Cập nhật README, ma trận F05 và checklist demo, không nhận local History là P2P Sync.

**Phần mở rộng nếu rubric yêu cầu:** đồng bộ History delta sau reconnect (H-06); lập task bổ sung khi được xác nhận, không tự check.

[Spec](spec.md) · [Plan](plan.md) · [ROADMAP](../../ROADMAP.md).
