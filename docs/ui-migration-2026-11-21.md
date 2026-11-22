# IntraChat — Biên bản hợp nhất Frontend và loại bỏ giao diện cũ

**Ngày thực thi:** 21/11/2026  
**Mục tiêu GOAL:** Chỉ còn một ứng dụng/entrypoint Swing/FlatLaf Messenger (`ui.IntraChat`), không còn UI kỹ thuật M1 hoặc launcher Live độc lập; Demo và Live dùng chung hệ thống Frontend. Các dịch vụ backend TCP/TLS/fingerprint được bảo toàn.

## Phạm vi thay đổi

**Đã xóa khỏi source và shaded JAR:**

- `src/main/java/vn/intrachat/ui/MainWindow.java`
- `src/main/java/vn/intrachat/ui/NetworkPanel.java`
- `src/main/java/vn/intrachat/ui/InboundRequestDialog.java`
- `src/main/java/vn/intrachat/IntraChatApplication.java`
- `src/main/java/ui/LiveIntraChat.java`
- `src/main/java/ui/frames/LiveConnectionFrame.java`

**Đã thay thế/tích hợp:**

- `ui.IntraChat`: launcher duy nhất; mở Demo login và cung cấp thao tác tự chọn Live LAN.
- `ui.frames.LoginFrame`: giữ nguyên đăng nhập/đăng ký Demo, thêm nút `Chat trong LAN (TLS 1.3)`.
- `ui.dialogs.LanConnectionDialog`: thay cửa sổ Live cũ bằng dialog thuộc Frontend; nhập IPv4/port, Listen/Stop/Connect/Disconnect, Accept/Reject, so SAS/fingerprint, xử lý session.
- `ui.frames.MainChatFrame`: tái sử dụng Messenger cho Live, có nút `Kết nối LAN` trong header; đóng dialog quản lý không ngắt phiên.
- `listener.ChatActionHandler`, `LiveChatAdapter`: điều khiển mở kết nối từ UI mà không đưa socket I/O lên EDT.
- `pom.xml`: Maven JAR Manifest, Shade Manifest và exec-maven-plugin đều trỏ về `ui.IntraChat`.
- `scripts/OfflineLaunchHarness.java`, `scripts/verify-m2.ps1`: chuyển sang launcher mới, tự động chặn class UI cũ trong JAR.
- README, hướng dẫn LAN và M1/M2 quickstarts: cập nhật lệnh chạy; evidence cũ giữ nguyên như lịch sử.

**Đã giữ nguyên:** `MockChatService`, FE chat/group/media, `LiveChatService`, `SecureChatCoordinator`, `TlsChatSession`, `ChatV2Codec`, TLS/DPAPI/certificate/fingerprint/pinning, M1 network/codec API dùng cho regression.

## Bằng chứng kiểm chứng

1. **Windows 11 + actual Temurin JDK 21.0.13.1+2**: `scripts/verify-m2.ps1 -Offline` chạy `clean package`, kiểm tra JAR/CLI, rồi đọc Surefire XML: **113/113 JUnit pass, 0 failures/errors, exit 0**.
2. Từ JAR mới, `java -jar target/intrachat-0.1.0-SNAPSHOT.jar` mở đúng `LoginFrame` Messenger, có nút `Chat trong LAN (TLS 1.3)`.
3. Nút Live mở **hộp thoại `IntraChat — Kết nối LAN`** thay vì một launcher thứ hai.
4. **Hai `javaw.exe` độc lập trên cùng máy**, mỗi bên dùng một `intrachat.secureRoot` tạm dưới `target/`: A Start Listening 127.0.0.1:47321, B Connect, A chấp nhận trong popup, cả hai nhận cùng mã SAS và tự xác nhận.
5. Hai cửa sổ **`IntraChat — Live LAN (mã hóa, đã xác minh)`** được mở, gửi/nhận văn bản tiếng Việt và emoji **hai chiều**. Ứng dụng hiển thị trạng thái `Đã nhận` theo ACK và `Đã xem` sau khi tin xuất hiện trong cửa sổ người nhận đang foreground.
6. Mở nút `Kết nối LAN` trong hội thoại, đóng dialog cài đặt và tiếp tục gửi tin thành công; xác nhận phiên Live không bị ngắt vì đóng dialog.
7. Đóng hai cửa sổ chat, hai Java GUI process thử nghiệm thoát; không thấy PID thử nghiệm còn hoạt động.
8. Script kiểm tra JAR bảo đảm không đóng gói class UI M1 và Live launcher cũ.

9. Kiểm thử chuyển chế độ: mở hộp thoại Chat LAN làm màn hình Demo tạm bị vô hiệu hóa; khi đóng hộp thoại trước khi kết nối, màn hình Demo được bật lại. Java GUI thử nghiệm thoát sạch sau khi đóng cửa sổ.
10. Cả ba script `verify-m0.ps1 -Offline`, `verify-m1.ps1 -Offline`, `verify-m2.ps1 -Offline` đã chạy thành công trên Temurin 21, tổng 113 JUnit không lỗi trong mỗi lần chạy.
11. `OfflineLaunchHarness` đã được biên dịch và chạy với `--diagnostics`: in `OFFLINE_DENIAL_CONFIRMED` và trạng thái nền tảng, exit 0.
12. `git diff --check` đạt. Sáu file có thay đổi chưa commit trước lúc chuyển đổi đã được so sánh SHA-256 với bản sao ban đầu, **không bị sửa đổi bởi tác vụ di chuyển UI**.

## Giới hạn còn mở (không thuộc nghiệm thu thay launcher)

- **Chưa kiểm thử hai máy Windows vật lý** trên LAN thực tế khi không có Internet.
- **Chưa đánh giá bảo mật độc lập/penetration test** hoặc toàn bộ lỗi/stress của M1/M2. Không được tuyên bố M1/M2 Verified từ lần di chuyển UI.
- Các tính năng Group, File, Image, Video, Sound trong Demo **không phải** dữ liệu Live được gửi qua TCP; Live hiện hỗ trợ văn bản/emoji, ACK và READ bằng UI.
- Báo cáo spec/evidence M0/M1/M2 lịch sử vẫn nhắc tới class/entrypoint cũ để truy vết; đó không phải class đang build.

## Bảo toàn công việc có sẵn

Trước khi sửa, repository đã có thay đổi chưa commit trong các file thuộc M1 protocol/test và `tasks.md`. Các bản sao được lưu trong `.git/ui-migration-preexisting-20261121/`, không stash/reset/overwrite những file đang có thay đổi này. Việc chuyển UI chưa tạo commit Git.

## Lệnh sử dụng hiện tại

```powershell
.\mvnw.cmd clean package
java -jar target\intrachat-0.1.0-SNAPSHOT.jar
java -jar target\intrachat-0.1.0-SNAPSHOT.jar --diagnostics
powershell -NoProfile -ExecutionPolicy Bypass -File scripts\verify-m2.ps1 -Offline
```

**Kết luận trong phạm vi di chuyển UI:** GOAL đạt qua build, regression, kiểm tra JAR, và smoke UI hai tiến trình cục bộ; các blocker nghiệm thu sản phẩm LAN/security vẫn được công bố riêng.

---

## Phụ lục phạm vi đồ án — 22/11/2026 (không sửa lại lịch sử 21/11)

**Giao diện cũ đã chuyển sang Messenger từ 21/11:** `MainWindow`, `NetworkPanel`, `InboundRequestDialog`, `IntraChatApplication`, `LiveIntraChat` và `LiveConnectionFrame` không còn trong Java build. `LanDiagnosticsDialog` mới là panel kỹ thuật phụ **bên trong Messenger**, không phải launcher khác. `ui.IntraChat` là entrypoint duy nhất.

**Yêu cầu đồ án mới:** đủ **P2P, Group, File, Emoji, History, Image, Video, Sound, State, Time, Sender**, tất cả phải **Live** qua cùng `ui.frames.MainChatFrame`. Mã bảo mật TCP/TLS/DPAPI đã hoạt động được giữ, không yêu cầu audit doanh nghiệp/Gate độc lập để **tiếp tục code** chức năng. Không truyền dữ liệu user qua M1 plaintext; không bỏ file safety, Sender/State đúng hay UI EDT.

| Nguồn/cửa sổ cũ hoặc Demo | Đường triển khai đồ án trong Messenger |
| --- | --- |
| M1 `MainWindow`/ `NetworkPanel` | **Đã loại**; chỉ `LanDiagnosticsDialog` trong `LanConnectionDialog` |
| M2 `LiveConnectionFrame`/ `LiveIntraChat` | **Đã loại**; `LoginFrame` → `LanConnectionDialog` → `MainChatFrame` |
| Group hiện là `MockChatService` + `CreateGroupDialog` | **Cần code:** Group service thực, fanout 3 peer, bật chính `CreateGroupDialog` cho Live |
| File/Image/Video/Sound chỉ ở Demo/ `MediaFiles`/ `VoiceRecorder` | **Cần code:** FileTransfer chung và nối attachment/bubble trong chính Messenger |
| History Live chỉ RAM phiên | **Cần code:** local repository, reload sau restart trên timeline Messenger |
| Text/Emoji/State/Time/Sender Live | **Đã có code cục bộ**; cần smoke 2 PC/điều kiện lỗi để đạt demo cuối |

**Chưa thực hiện trong phụ lục này:** Group/File/History/Media Live network và migration dữ liệu của chúng. Đây là **kế hoạch** chuyển đổi các feature còn thiếu, không phải xác nhận chúng đã hoạt động.

**Nguồn ưu tiên hiện hành:** [ROADMAP](../ROADMAP.md) · [Ma trận 11 tính năng](feature-matrix.md) · [Kế hoạch FE/Live integration](messenger-integration-plan.md) · [Phạm vi đồ án](course-project-scope.md).
