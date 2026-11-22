# IntraChat — Thiết lập Chat LAN trên Windows (Frontend thống nhất)

**Trạng thái (cập nhật 22/11/2026):** Một launcher `ui.IntraChat` và giao diện Messenger duy nhất. M1 diagnostics có dialog phụ `LanDiagnosticsDialog` (không phải cửa sổ chat); M2 gửi text/emoji TLS 1.3 trong `MainChatFrame`. Từng chạy cục bộ hai JVM một máy; chưa có bằng chứng hai PC vật lý. **Định hướng đồ án:** hoàn thiện 11 tính năng Live trên Messenger trước, bảo mật chuyên sâu không phải công việc ưu tiên. [ROADMAP](../ROADMAP.md) · [Kịch bản demo](course-project-scope.md).

## Yêu cầu

- Windows 11 x64 và **Temurin JDK 21** đang hoạt động; Maven Wrapper 3.9.16 để build.
- Hai máy trong cùng LAN có kết nối IPv4 trực tiếp; không cần cloud hay server Internet.
- Khi demo, **chỉ dùng dữ liệu thử**. TLS/SAS đang chạy được giữ; không sử dụng ứng dụng đồ án chưa hoàn thiện cho nội dung nhạy cảm. Không cần đợi audit độc lập mới được thử 11 tính năng bằng dữ liệu mẫu.

```powershell
java -version
.\mvnw.cmd clean package
java -jar target\intrachat-0.1.0-SNAPSHOT.jar
```

Lệnh `java -jar` mở **màn hình Messenger duy nhất**, không khởi chạy UI kỹ thuật M1.

## Chế độ Demo

Mở ứng dụng, nhập username và mật khẩu demo bất kỳ theo giới hạn trên màn hình để vào hội thoại mẫu. Demo sử dụng `MockChatService` trong RAM, **không** khởi động TCP, TLS hoặc lấy danh tính bảo vệ của máy cho đến khi chủ động chọn Chat LAN. Không dùng mật khẩu thật.

## Chế độ Chat LAN (M2)

1. Trong màn hình ban đầu, nhấn **Chat trong LAN (TLS 1.3)**. Hộp thoại Kết nối LAN là một phần của Frontend Messenger.
2. Trên máy A, nhập **IPv4 mạng LAN thực** của máy A và cổng lắng nghe, ví dụ `47321`, rồi chọn **Bắt đầu lắng nghe**.
3. Trên máy B, nhấn **Chat trong LAN**, nhập IPv4 và port của A, chọn **Kết nối & xác minh**. `127.0.0.1` chỉ dành cho thử trên cùng một máy, không dùng khi hai PC khác nhau.
4. A phải **Chấp nhận** yêu cầu đến; tên và UUID tự khai không phải xác thực danh tính. Từ chối hoặc hết hạn thì không có kênh chat.
5. Hai phía phải **độc lập so khớp cùng mã fingerprint/SAS** qua kênh đáng tin cậy và xác nhận. Mã không trùng thì Từ chối, không gửi nội dung chat.
6. Sau khi hai phía xác nhận và TLS 1.3/PAIR_READY hoàn tất, giao diện Messenger mở hội thoại thực. Gửi văn bản/emoji; `Đã nhận` cần application ACK và `Đã xem` ở đây chỉ có nghĩa tin đã thực sự hiển thị trong vùng chat đang foreground, **không chứng minh người nhận đã đọc bằng mắt**.
7. Nhấn **Kết nối LAN** trong header hội thoại để quản lý kết nối. Đóng hộp thoại này chỉ ẩn phần cài đặt, không ngắt Chat Live. Dùng nút **Ngắt kết nối** để ngắt phiên. Không tự động kết nối lại.
8. Tin nhắn Live và bản nháp chỉ ở RAM phiên hiện tại; danh tính thiết bị và peer pins được lưu bảo vệ qua Windows CurrentUser DPAPI.

## Kiểm thử hai JVM trên cùng Windows

Chỉ dùng dữ liệu thử nghiệm, hai thư mục danh tính riêng:

```powershell
java '-Dintrachat.secureRoot=C:\Temp\intrachat-test-A' -jar target\intrachat-0.1.0-SNAPSHOT.jar
java '-Dintrachat.secureRoot=C:\Temp\intrachat-test-B' -jar target\intrachat-0.1.0-SNAPSHOT.jar
```

Trên A Start Listening `127.0.0.1:47321`; trên B Connect cùng địa chỉ; hai người dùng thử bấm Accept, so khớp SAS rồi trao đổi văn bản. Điều này **không** thay thế kiểm chứng hai PC vật lý.

## Firewall / mạng

Chỉ khi được quyền quản trị mạng, thêm rule inbound TCP **Private profile**, giới hạn phạm vi máy/subnet nội bộ được phép và sau thử nghiệm gỡ rule. Không mở Public profile hoặc tự động mở firewall. Nếu thất bại, kiểm tra `ipconfig`, địa chỉ/port nhập, sự tồn tại của listener, chính sách Windows Firewall, AP client isolation và VLAN.

**Không dùng Internet công cộng hoặc peer không tin cậy để thử nghiệm.** Ngắt Internet chỉ khi có môi trường LAN thử nghiệm được phép, không làm ảnh hưởng mạng chung.

## Nghiệm thu và hạn chế

- **Đã quan sát:** hai GUI Java21 khác danh tính trên một Windows PC kết nối localhost; người nhận Accept; cùng SAS hai phía; TLS Live mở Messenger; gửi/nhận văn bản tiếng Việt/emoji hai chiều, application ACK; đóng hộp thoại quản lý kết nối không làm rớt phiên.
- **Chưa chạy:** hai PC Windows vật lý với LAN cô lập, không Internet, firewall thực, kiểm thử tải/half-open toàn diện, đánh giá bảo mật độc lập. Không gọi M1/M2 là Verified từ dữ liệu loopback.
- **M1 diagnostics:** launcher và `NetworkPanel` cũ đã gỡ. Dialog `LanDiagnosticsDialog` hiện mở từ `LanConnectionDialog` bên trong Messenger, chỉ cho PING/PONG kỹ thuật **không mã hóa**, không dùng gửi văn bản/file/media.

Xem [migration Messenger](ui-migration-2026-11-21.md), [kế hoạch tích hợp 11 chức năng](messenger-integration-plan.md), [Spec 003 quickstart](../specs/003-p2p-messaging/quickstart.md) và các báo cáo nghiệm thu lịch sử.
