# Kế hoạch thực thi Spec 003 — P2P Chat MVP

**Mục tiêu:** hoàn thành các tình huống P2P-01…07 trong [spec.md](spec.md) bằng **mã nguồn hiện có**, không viết lại networking.

## 1. Tận dụng các phần đã có

- Launcher `ui.IntraChat`, `LoginFrame`, `LanConnectionDialog`, `MainChatFrame`, `ComposerPanel`.
- `LiveChatAdapter`, `LiveChatService`, `NetworkCoordinator`, `SecureChatCoordinator`.
- TCP/v2, TLS 1.3, xác nhận peer hiện tại, codec JSON, ACK/READ; `MockChatService` chỉ dành Demo.
- Giữ nguyên M1 diagnostic; không thêm kênh plaintext để gửi nội dung.

## 2. Trình tự làm việc

1. **Smoke GUI:** chạy hai JVM với hai thư mục danh tính thử nghiệm khác nhau, mở Live ở cả hai, Listen → Connect → Accept → hoàn tất xác nhận hiện có.
2. **Gửi/nhận:** gửi và trả lời tin Unicode; kiểm tra đúng conversation, sender, timestamp, không nhân đôi bubble.
3. **State:** xác minh các chuyển trạng thái Đang gửi → Đã gửi → Đã nhận theo ACK; Đã xem chỉ khi receipt thật, không phải mock; thất bại không bị biến thành thành công.
4. **Lỗi và reconnect:** Reject, sai cổng, ngắt khi gửi, Connect lại thủ công, gửi tin mới; UI không đứng, không auto-send draft.
5. **Demo isolation:** gửi/nhận Live không xuất hiện trong Demo; tính năng Group/File/Media chưa hỗ trợ Live không được giả vờ gửi thành công.
6. **Test & chứng cứ:** chạy Java 21 Maven offline test, ghi số test/exit code; cập nhật [evidence/m2-mvp-acceptance.md](evidence/m2-mvp-acceptance.md) theo từng P2P-01…07.
7. **LAN hai máy:** nếu có PC thứ hai, làm lại smoke theo địa chỉ LAN thật, không dùng 127.0.0.1 làm địa chỉ máy bên kia.

## 3. Chỉ sửa khi có lỗi tái hiện

Ưu tiên sửa mapping UI ↔ adapter ↔ service, lifecycle, trạng thái và timeout. Thêm test gần lỗi, tránh refactor TLS, protocol hay storage không cần thiết. Không làm security audit, review gate, benchmark lớn hoặc thêm server trong Spec 003 MVP.

## 4. Điều kiện merge/bàn giao

- P2P-01…07 PASS ở hai tiến trình, regression test PASS.
- UI Live gửi/nhận thật; không có lỗi chặn trình diễn, không giữ socket/worker treo sau Close.
- Ghi rõ phần chưa thử trên LAN hai máy, không tự chứng nhận Gate 1 hoặc toàn bộ 90 task lịch sử.

**Phụ thuộc sau MVP:** Spec 004 Group dùng P2P đã ổn định; Spec 006 File sử dụng cùng kết nối; Spec 005 History và 007 Media triển khai riêng.
