# Đợt chỉnh UI — Đồng bộ dialog LAN với Messenger

**Ngày:** 22/11/2026 · **Trạng thái:** Đã sửa Java UI và chạy JUnit/regression.  
**Phạm vi người dùng yêu cầu:** chỉnh các giao diện lệch phong cách so với Messenger hiện có; **không đổi quy tắc TCP/TLS, Gate hoặc 11 chức năng**. [Kế hoạch Messenger](messenger-integration-plan.md) · [Ma trận tính năng](feature-matrix.md).

## Chuẩn tham chiếu thực tế

`ui.components.ChatStyle`: FlatLightLaf, Segoe UI, màu Messenger `BLUE #0084FF`, `INK #050505`, `MUTED #65676B`, `FIELD #F0F2F5`, `LINE #E4E6EB`, `SELECTED #EAF4FF`. Giao diện chính `ui.frames.MainChatFrame` và `LoginFrame` là chuẩn cho bố cục, thẻ, nút xanh, nút phụ và trạng thái.

## Thay đổi đã làm trong source

| File | Trước | Sau |
| --- | --- | --- |
| `ui/components/ChatStyle.java` | Chỉ `primary`, iconButton, field | Thêm `secondary`, `dialogField`, `dialogCard` cho dialog dùng chung token Messenger |
| `ui/dialogs/LanConnectionDialog.java` | Form kỹ thuật, nút Swing mặc định, nhiều mô tả HTML | Hai thẻ **Chờ bạn bè kết nối** / **Kết nối với bạn bè**, IP/cổng bo góc, nút hành động xanh–xám, trạng thái ở footer, chẩn đoán thành công cụ nâng cao; giữ nút Listen, Stop, Connect, Disconnect |
| `LanConnectionDialog` popup Accept/SAS | Khung mặc định, nút không theo style, mã SAS ở HTML `h2` | Modal kiểu Messenger có title, thẻ thông tin, nút primary/secondary; SAS 28pt màu xanh và hai fingerprint trong khung dễ đọc; Esc/đóng/timeout vẫn từ chối |
| `ui/dialogs/LanDiagnosticsDialog.java` | Nhiều hàng FlowLayout kỹ thuật | Ba thẻ Lắng nghe / Kết nối / Phiên và thử nghiệm; token/nút dùng chung; **cảnh báo KHÔNG MÃ HÓA** vẫn luôn hiện rõ ở đầu và test gọi được qua `m1-untrusted-warning` |
| `ui/frames/LoginFrame.java` | Nút vào Live mặc định | Nút Live secondary đồng bộ Messenger, vẫn tách mật khẩu Demo |
| `ui/frames/MainChatFrame.java` | Nút Kết nối LAN Swing mặc định trong header | Nút secondary cùng Messenger, không đổi callback |
| `ui/dialogs/CreateGroupDialog.java` | Nút Hủy mặc định | Nút Hủy secondary, Tạo nhóm primary, giữ luồng Demo |
| `ui/components/MessageMedia.java` | Image viewer chỉ là JDialog + JScrollPane | Image viewer có header, tên file, nút Đóng secondary, viền/spacing đồng bộ |

Các nút có tên test automation (`m1-listen`, `m1-connect`, `m1-approve`, `m1-reject`, `open-m1-diagnostics`) và hành vi network vẫn giữ nguyên. **Không khôi phục giao diện cũ** `MainWindow`/`NetworkPanel`/`LiveConnectionFrame` và không tạo launcher riêng.

## RED → GREEN và regression

- Mới: `src/test/java/ui/MessengerStyleConsistencyTest.java` **3/3 PASS**. RED ban đầu do chưa có `ChatStyle.secondary/dialogField/dialogCard`. Sau triển khai GREEN: kiểm tra token, màu, component tree và style của Login, Group, LAN và diagnostic.
- `LiveModeEntryTest`, `DesktopUiTest`, `LanDiagnosticsDialogTest` chạy tập trung **exit 0** trên Java 21 sau khi chỉnh.
- Lượt full regression đầu: **248/249 PASS, 1 fail** vì bố cục mới bỏ mất `m1-untrusted-warning` mà test và hành vi M1 yêu cầu. Sửa **trả cảnh báo đỏ lên header**, giữ `name` và đúng ý nghĩa `KHÔNG MÃ HÓA`. Focused GREEN, sau đó full regression **249/249 PASS, 0 failures/errors/skips**, `scripts/verify-m2.ps1 -Offline` **exit 0**, shaded JAR và launcher `--diagnostics` PASS.
- Java runtime: **Temurin 21.0.12.1+1** trên Windows. Marker cuối: `%TEMP%\intrachat-messenger-ui-clean-final-exit-20261122.txt` = **0**; output `%TEMP%\intrachat-messenger-ui-clean-final-out-20261122.log`. Sau dọn import/style đã chạy lại đầy đủ trên source cuối cùng với cùng kết quả **249/249 PASS, exit 0**. Các test không phát sinh mạng Internet.

## Giới hạn kiểm tra hình ảnh thực

Windows desktop đang ở **màn hình khóa**, nên screenshot giao diện Java trả về hình tối/đen: **không có bằng chứng QA so sánh screenshot Before/After**. Vì vậy chỉ xác nhận cấu trúc Swing/layout/style, JUnit GUI automation và launcher smoke; không tự nhận rằng đã kiểm tra trực quan chính xác khoảng cách/chiều cao mọi màn hình trên monitor người dùng.

**Cần QA bổ sung khi mở khóa Windows:** mở Messenger Demo → Live LAN, kiểm tra bố cục ở 800×560 và 850×600, thử popup Accept + SAS 2 JVM thật, xác nhận có thể đọc đầy đủ fingerprint, Esc/Từ chối, chẩn đoán PING/PONG, ảnh viewer đóng/mở, Tab/focus và khả năng hiển thị sau đổi DPI 125%/150%. Nếu có cắt chữ, chỉ chỉnh layout/font; **không nới lỏng xác minh hoặc đổi networking**.

**Không thay đổi:** M0/M1/M2/Group task checkboxes, bảo mật/codec/transport, 11 chức năng Live. Không commit/reset/stash worktree trước đó.