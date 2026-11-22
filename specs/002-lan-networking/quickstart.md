# Spec 002 — Chẩn đoán TCP v1 trong Messenger (M1)

**Cập nhật:** 2026-11-22 · **M1: IN PROGRESS, Gate 1 NOT VERIFIED**
**Phiên bản giao diện:** Một launcher `ui.IntraChat` duy nhất; có **dialog M1 chẩn đoán kỹ thuật riêng** bên trong màn hình kết nối LAN. Không khôi phục launcher `vn.intrachat.IntraChatApplication`, `MainWindow` hay `NetworkPanel` lịch sử.

## 1. Cài đặt và kiểm thử trên Windows / Java 21

Mở PowerShell tại thư mục gốc repository. Ví dụ Java 21 trên máy phát triển hiện tại:

```powershell
$env:JAVA_HOME='C:\Users\XuanHoai\.jdks\jdk-21.0.12.1+1'
$env:PATH=$env:JAVA_HOME+'\bin;'+$env:PATH
powershell.exe -NoProfile -ExecutionPolicy Bypass -File scripts/verify-m1.ps1 -Offline
powershell.exe -NoProfile -ExecutionPolicy Bypass -File scripts/verify-m2.ps1 -Offline
java -jar target\intrachat-0.1.0-SNAPSHOT.jar
java -jar target\intrachat-0.1.0-SNAPSHOT.jar --diagnostics
```

**Kết quả có bằng chứng ngày 22/11/2026:** cả hai script `exit 0`, **172/172 JUnit PASS**, clean offline package + shaded JAR diagnostics PASS, GUI M1 `LanDiagnosticsDialogTest` **5/5 PASS**; bài hai JVM M1 Accept/Reject/timeout **3/3 PASS** trên **một máy**. [Báo cáo thực thi](evidence/m1-local-completion-2026-11-22.md).

## 2. Mở M1 TCP diagnostic trong Messenger

1. Khởi động Messenger bằng `java -jar ...`; mở chức năng **Chat trong LAN** để đến màn hình `LanConnectionDialog` (luồng TLS Chat Live M2).
2. Chọn nút **“Chẩn đoán TCP v1 (KHÔNG mã hóa)”** để mở `LanDiagnosticsDialog`. Đây là cửa sổ **diagnostics kỹ thuật M1**, không phải Chat Live.
3. Trên máy A, nhập IPv4 thuộc LAN được cấp phép và port chẩn đoán, mặc định **47322** (khác mặc định Chat Live TLS M2 **47321**), sau đó nhấn **Bắt đầu lắng nghe**.
4. Trên máy B, chỉ nhập **IPv4 và port thủ công** của A; nhấn **Kết nối kỹ thuật**. Máy A phải thấy popup với tên/UUID **tự khai, chưa tin cậy**, và **chủ động Chấp nhận kỹ thuật** hoặc **Từ chối**. Nếu không tương tác, yêu cầu hết hạn; không có auto-accept trong giao diện production.
5. Chỉ khi handshake HELLO → ACCEPT → CONFIRM → READY thành công, nút **Ping kiểm tra** mới bật ở cả hai phía; PONG chỉ báo hiệu phản hồi **kỹ thuật**/độ trễ, **không phải xác nhận gửi/đọc tin nhắn**.
6. Dùng **Hủy kết nối** nếu đang chờ, **Ngắt kết nối** sau READY, hoặc **Kết nối lại thủ công** khi thất bại/từ chối. Không có auto retry.
7. Đóng dialog để dừng listener M1 và các worker/sockets của cửa sổ đó. Kênh Chat Live M2 TLS/SAS là đường truyền **riêng**, không bị thay thế bởi v1 chẩn đoán.

**Cảnh báo bảo mật bắt buộc:** M1 v1 là **plaintext/unauthenticated**; IPv4, tên hiển thị và UUID do peer tự khai **không phải danh tính được xác minh**. M1 chỉ gửi PING/PONG synthetic; **không gửi text, Group, History, File, Media hoặc dữ liệu nhạy cảm** qua cửa sổ này. M2 Chat Live chỉ cho phép user text sau TLS 1.3 và xác minh SAS/fingerprint hai phía.

## 3. Phạm vi kiểm thử thực tế và nghiệm thu còn chặn

- **Đã chạy local:** Java 21 full 172/172; two-JVM Accept/Reject/timeout; 8 lượt cross-connect đồng thời GREEN sau race fix; 16 frames/2 MiB queue/4 active/4 pending; heartbeat fake clock 10s/30s; malformed HELLO/port conflict/truncated frame; 5 GUI listen/dispose; GUI Accept→PONG và Reject→manual reconnect.
- **CHƯA CHẠY:** hai **máy Windows vật lý riêng biệt** trên cùng Private LAN trong khi public Internet không khả dụng, IP/firewall/timeout/diagnostics hai hướng trên host thật, security review và Constitution Gate 1. Các task **T050–T053, T058–T060** vẫn mở; M1 **NOT VERIFIED**.
- Không tự sửa Windows Firewall Public hoặc ngắt Internet hệ thống nếu chưa được chủ máy và quản trị mạng cho phép. Khi có hai máy thật, làm theo [Windows LAN Setup](../../docs/windows-lan-setup.md), ghi kết quả từng máy vào [US5 real LAN](evidence/us5-real-lan.md), không nhầm localhost với hai PC.

**Lịch sử:** Các báo cáo [US1](evidence/us1-manual-connect.md), [US2](evidence/us2-approval-handshake.md), [US3](evidence/us3-diagnostics.md) ghi trạng thái trước khi giao diện M1 được tách thành dialog trong Messenger. Từ 22/11/2026 trở đi, dùng hướng dẫn và [bằng chứng local mới](evidence/m1-local-completion-2026-11-22.md).