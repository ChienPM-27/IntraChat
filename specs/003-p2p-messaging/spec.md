# Spec 003 — P2P Chat MVP (tối giản)

**Cập nhật:** 23/11/2026 · **Trạng thái:** Đang hoàn thiện, chưa nghiệm thu hai máy LAN thật.
**Mục tiêu:** Hai người dùng mở **cùng ứng dụng Messenger IntraChat**, kết nối trực tiếp trong LAN qua IP:port, gửi và nhận tin văn bản/emoji **hai chiều**. Không cần Internet, server hoặc tài khoản.

> Đây là **phạm vi đồ án hiện hành**, thay thế checklist 90 task mang tính bảo mật/release cũ làm tiêu chí hoàn thành Spec 003. Trạng thái cũ **65/90** chỉ là dữ liệu lịch sử (xem Git và `evidence/`), không được coi là 90/90 PASS. Các hợp đồng bảo mật cũ trong `contracts/` vẫn là hồ sơ kỹ thuật, không phải yêu cầu triển khai mới của MVP.

## Phạm vi bắt buộc

1. **Một giao diện:** `ui.IntraChat` → chọn **Chat trong LAN** → `LanConnectionDialog` → `MainChatFrame`. Demo/mock phải tách khỏi Live.
2. **Kết nối P2P:** A chọn Listen; B nhập IPv4:port và Connect; A Accept hoặc Reject; UI báo rõ Đang kết nối / Đã kết nối / Mất kết nối / Không thể kết nối. Người dùng được phép Disconnect và Connect lại thủ công.
3. **Text hai chiều:** A gửi B, B gửi A qua transport thật; nhận đúng nội dung và đúng hội thoại, không mock/echo giả; tránh tin bị lặp trong một phiên.
4. **Emoji + tiếng Việt:** UTF-8/Unicode, emoji đa code point, dấu tiếng Việt và xuống dòng hiển thị đúng.
5. **Sender + Time:** Hiển thị đúng người gửi và giờ tin nhắn; không dùng tên hiển thị làm khóa định tuyến, tin nhắn không xuất hiện nhầm hội thoại.
6. **State tối thiểu, trung thực:** Hiển thị trạng thái kết nối và `Đang gửi / Đã gửi / Đã nhận / Thất bại / Chưa rõ` theo sự kiện thực. `Đã xem` chỉ hiện khi có read receipt hợp lệ từ sự kiện hiển thị thực; nếu chưa chắc chắn, không hiện `Đã xem`.
7. **Lỗi cơ bản:** Không gửi khi mất kết nối, báo lỗi có thể hiểu, giữ nháp trong phiên khi phù hợp, không tự tạo hàng đợi gửi offline. UI không treo khi timeout/ngắt mạng.

## Không thuộc Spec 003 MVP

- Group, History sau restart, File Transfer, Image, Video, Sound (Spec 004–007).
- Phát triển hoặc thay thế cơ chế mã hóa, chứng thực, lưu khóa đang có.
- Audit CVE/SBOM/provenance, penetration test, review độc lập, cross-user DPAPI, Gate 0/Gate 1, chứng nhận production, stress quy mô lớn.
- Auto-discovery, Internet relay, cloud, đăng nhập tài khoản, đồng bộ lịch sử, auto-reconnect.

**Giữ nguyên phần đang hoạt động:** mã TLS 1.3, pairing/SAS và phân tuyến v1 diagnostic/v2 chat hiện hữu; không tự chuyển user chat sang M1 plaintext. Giữ giới hạn frame, timeout/cleanup, chống gửi sai peer và xử lý bất đồng bộ để tránh treo Swing. Bỏ yêu cầu *kiểm toán bảo mật chuyên sâu*, không phải chủ trương cố ý làm kết nối kém an toàn.

## Kịch bản nghiệm thu đơn giản

| ID | Thao tác | Kết quả cần thấy |
| --- | --- | --- |
| P2P-01 | A Listen, B Connect theo IP:port, A Accept; hoàn tất bước xác nhận đang có nếu ứng dụng yêu cầu | Hai phía hiện Đã kết nối, có đúng hội thoại của peer |
| P2P-02 | A gửi "Xin chào Việt Nam 👋" cho B; B trả lời | Hai bên nhận đúng tin thật, đúng chiều, không duplicate |
| P2P-03 | Gửi emoji ZWJ, xuống dòng; kiểm tra Sender và Time | Nội dung không lỗi, sender và giờ đúng |
| P2P-04 | Theo dõi trạng thái một tin | Không hiện Đã nhận trước ACK; Đã xem chỉ khi thật sự có receipt hợp lệ |
| P2P-05 | B Reject / sai IP:port / ngắt giữa lúc gửi | Báo lỗi, không treo, không báo nhận giả |
| P2P-06 | Disconnect rồi kết nối lại, gửi tin mới | Tin mới đến được đúng peer, không tự gửi lại nháp |
| P2P-07 | Mở Demo riêng rồi quay lại Live | Không trộn mock message, trạng thái hoặc danh tính Demo |

## Điều kiện hoàn thành

- **Bắt buộc:** P2P-01…07 chạy PASS với **hai JVM độc lập** trên Windows, có ghi nhận kết quả thực và bài test tự động liên quan PASS.
- **Ưu tiên nghiệm thu cuối đồ án:** lặp lại P2P-01…06 trên **hai máy Windows trong cùng LAN**; chưa chạy phải ghi `NOT RUN`, không tự nhận đã test LAN thật.
- Không đánh dấu hoàn thành khi mới có unit test nhưng gửi/nhận qua GUI Live chưa chạy.

**Điểm bắt đầu:** [Kế hoạch](plan.md) · [Task MVP](tasks.md) · [Cách chạy](quickstart.md). Bằng chứng/gate lịch sử vẫn nằm trong `evidence/`; trạng thái kỹ thuật cũ không tự động đổi thành Verified.
