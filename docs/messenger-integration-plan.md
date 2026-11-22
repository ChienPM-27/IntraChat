# Kế hoạch chuyển toàn bộ 11 chức năng Live vào Messenger

**Đồng bộ giao diện đã thực hiện 22/11/2026:** [Báo cáo code/test UI LAN, diagnostic, popup và viewer](messenger-ui-style-update-2026-11-22.md). Kết nối và xác minh vẫn chạy qua backend cũ; không mở launcher độc lập.


**Mục tiêu:** mở rộng giao diện Messenger Swing/FlatLaf hiện tại, **không tạo launcher/chat window thứ hai**.
**Nguồn quyết định:** [Phạm vi đồ án](course-project-scope.md) · [ROADMAP mới](../ROADMAP.md) · [Migration đã thực hiện 21/11](ui-migration-2026-11-21.md).

## 1. Hiện trạng mã nguồn — không nhầm Demo với Live

```text
ui.IntraChat
└── ui.frames.LoginFrame
    ├── Demo → model.mock.MockChatService
    └── Chat trong LAN → ui.dialogs.LanConnectionDialog
         ├── M1 diagnostics → LanDiagnosticsDialog (PING/PONG; không user chat)
         └── Live P2P → LiveChatAdapter → LiveChatService → SecureChatCoordinator
                              └── ui.frames.MainChatFrame (Messenger chung)
```

**Đã bỏ khỏi Java build**: `vn.intrachat.IntraChatApplication`, `vn.intrachat.ui.MainWindow`, `vn.intrachat.ui.NetworkPanel`, `vn.intrachat.ui.InboundRequestDialog`, `ui.LiveIntraChat`, `ui.frames.LiveConnectionFrame`. Những tên này chỉ còn trong tài liệu lịch sử. **Không khôi phục chúng**.

| Phần giao diện hiện có | Demo | Live hiện tại | Việc nối tiếp cho đủ 11 chức năng |
| --- | --- | --- | --- |
| `LoginFrame` | Tài khoản Demo giả lập | Nút Chat LAN + dialog kết nối | Giữ một điểm vào; tên/thuật ngữ thân thiện; không thêm trang đăng nhập khác |
| `LanConnectionDialog` | Không tham gia mạng | Listen/Connect, Accept/SAS, Disconnect | Giữ, gom thông tin kỹ thuật ở popup tùy chọn, không tạo màn hình riêng |
| `MainChatFrame` directory | Mock direct/group | Danh sách peer thực (trùng tên thì dùng UUID), offline RAM | Hiển thị nhóm thật từ GroupService; badge participant/online |
| `CreateGroupDialog` | Có thể tạo nhóm mock | Nút nhóm ẩn | Chuyển `ChatActionHandler.createGroup` sang backend Group; chỉ bật khi chạy thật |
| `ComposerPanel` | Text, emoji, đính kèm, mic | Text Unicode/emoji và nút attachment/mic ẩn | Bật attachment sau FileTransfer, mic/media sau Media handler |
| `MessageBubblePanel` | Text/file/media/receipt mock | Text, State/Time/Sender, retry thủ công | Dùng `MessageContent` type file/image/video/audio từ Live, không mock URI |
| `MainChatFrame` timeline/history | Lịch sử giả lập trong phiên | Transcript RAM TLS phiên | Kết nối HistoryRepository để xem sau restart |
| `LiveMessageVisibilityObserver` | Không sinh receipt thật | READ theo sự kiện foreground | Áp dụng trong Group; đọc message thật sau khi hiển thị |

## 2. Giao diện dùng chung, service provider riêng

- `listener.ChatActionHandler`: **điểm ra của UI** cho đăng nhập Demo, tạo nhóm, gửi tin, load history, retry và điều khiển kết nối. Có thể bổ sung `sendAttachment`, `cancelTransfer`, `leaveGroup`, `deleteHistory`/các phương thức mới bằng implementation Demo/Live riêng; đừng gọi socket trực tiếp trong panel.
- `listener.ChatClientListener`: **điểm vào của UI** cho message mới, cập nhật conversation/directory, trạng thái tin, transfer progress và lỗi. Callback Live chuyển về Swing EDT.
- `listener.LiveChatAdapter`: adapter thực **không tự tạo tin/ACK theo timer**, gắn conversation ID với peer/nhóm đang kết nối. Hiện `createGroup` cố ý trả lỗi; khi service Group hoàn chỉnh hãy implement thay vì tạo GroupFrame.
- `model.mock.MockChatService`: giữ nguyên làm Demo; đừng cho bất kỳ nội dung mock nào lẫn vào Live, bao gồm "Đã nhận"/"Đã xem" giả.
- `model.ChatMessage`, `MessageContent`, `Conversation`, `DeliveryState`: mô hình FE chung; mở rộng metadata tệp/progress có kiểm soát, không tạo phiên bản UI riêng.

## 3. Những nút được phép bật và thời điểm

| Nút/tính năng | Có thể hiển thị Live ngay? | Điều kiện mới được bật thực sự |
| --- | --- | --- |
| Chat cá nhân và Emoji | **Có** | Peer đã kết nối, composer hoạt động, `sendText` qua Live |
| State/Time/Sender | **Có** | Sử dụng message/event thật; không fake READ/ACK |
| Tạo nhóm | **Chưa** | `createGroup` thật, roster + ít nhất một recipient, fanout, Group events vào directory/timeline |
| Chọn/tải tệp | **Chưa** | FileTransfer có chunk, receive/save, SHA-256, Cancel/progress |
| Image/Video/Mic | **Chưa** | FileTransfer thật và renderer/recorder xử lý dữ liệu nhận qua LAN |
| History sau restart | **Chưa** | Persistence local, `loadHistory` thật và test restart |
| Bảng chẩn đoán kỹ thuật M1 | **Có, nằm trong dialog** | Giữ ở mục phụ LAN; không phải chế độ chat P2P |

Không bật nút chỉ để **trình diễn layout**: thao tác rỗng, "coming soon" không tính là tính năng hoàn thành. Khi service chưa có, giữ ẩn/vô hiệu hóa hoặc báo rõ trạng thái.

## 4. Thứ tự nối service vào FE

### A. P2P baseline (đã có mã Live, tiếp tục QA)

- `LoginFrame`/LAN dialog → `SecureChatCoordinator` → `LiveChatService` → `LiveChatAdapter` → `MainChatFrame`.
- Test 2 JVM: text + emoji, đúng sender/timestamp, ACK/READ và ngắt kết nối.
- Không viết lại `TcpListener`, TLS hay hệ ghép cặp đã chạy; chỉ cải tiến UX nếu cản demo.

### B. Group Live

- Tận dụng `ui.dialogs.CreateGroupDialog` và `Conversation.group()` sẵn có.
- Tạo `GroupService` quản lý conversation/groupId, owner/member, invite/accept và fanout qua các P2P session hợp lệ. Không cần một giao diện nhóm khác.
- Sự kiện join/leave/group message cập nhật directory/selector/timeline/preview qua cùng `ChatClientListener`.
- Group ACK phải gắn từng recipient; không nhầm ACK của B với C. Test 3 JVM.
- Chữ ký owner/DPAPI Group prototype có sẵn có thể tái sử dụng; **không để quy trình cryptographic audit làm chặn code UI**.

### C. File Transfer

- Tách `FileTransferService` khỏi `ComposerPanel`: UI chọn file → service gửi dữ liệu qua network → progress events → bubble/tải/lưu.
- `MediaFiles` có tiện ích Demo, không tự biến `File` Live thành mock; không gán đường dẫn của sender làm đường dẫn local của receiver.
- Mỗi transfer gắn `transferId`, peer/conversation ID, tên an toàn, size, byte count, checksum và trạng thái. Tất cả I/O chạy worker.

### D. Local History

- Dùng kho theo `conversationId` gồm tin direct/group/file metadata, thứ tự timestamp/messageId.
- Không coi RAM `LiveChatService.transcript` là SQLite; cập nhật `LiveChatAdapter.loadHistory` để đọc repository.
- Khi tải history, `MainChatFrame` merge theo ID không nhân đôi bubble, UI không treo.

### E. Media theo FileTransfer

- Image: preview từ file nhận đã hoàn tất.
- Video: bubble với nút mở bằng default player.
- Sound: recorder WAV + phát từ file nhận, không cần voice call.
- `MessageContent` và `MessageBubblePanel` giữ Sender/Time/State cho tất cả media.

## 5. Quy ước phát triển dễ bảo trì

- **UI Swing EDT:** UI chỉ update qua `SwingUtilities.invokeLater`; không `Future.get()` hoặc file/socket read trên EDT.
- **Không chặn tiến độ do mã hóa không cần thiết:** Tách phần cấp quyền Group vào một service, dùng P2P transport đã ổn định nếu đáp ứng demo. Không tự tắt TLS hoặc cho v1 plaintext chuyên chở dữ liệu file.
- **Không gây hại máy nhận:** người dùng chọn nơi lưu hoặc đồng ý file; kiểm tra tên/chunk/kích thước; không overwrite/traversal.
- **Thật vs giả:** `MockChatService` chạy Demo; `LiveChatAdapter` không gọi `MockChatService`, không tự tạo online/ACK/READ.
- **Mỗi bước cần test:** service test + GUI integration test và manual checklist; chỉ check milestone khi thao tác được trên Messenger.

## 6. Định nghĩa xong migration

- Một lệnh `java -jar target/intrachat-0.1.0-SNAPSHOT.jar`, không launcher riêng cho Group/File/Media.
- Chọn Demo hoặc LAN Live; tất cả **11 chức năng** có đường thao tác trong `MainChatFrame` Live.
- Không còn Class UI cũ được sử dụng/đóng gói; không có màn hình chat kỹ thuật thứ hai.
- UI không lừa người dùng rằng file đã nhận hoặc tin đã xem khi chỉ vừa gửi.
- P2P/Group/File/Emoji/History/Image/Video/Sound/State/Time/Sender có bằng chứng chạy thật và cập nhật [feature matrix](feature-matrix.md).