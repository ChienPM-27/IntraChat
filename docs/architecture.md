# IntraChat — Kiến trúc hiện hành và đích Messenger của đồ án

**Cập nhật:** 22/11/2026 · [ROADMAP 11 chức năng](../ROADMAP.md) · [Phạm vi đồ án](course-project-scope.md) · [Kế hoạch UI](messenger-integration-plan.md).

## 1. Runtime hiện tại (đã xác nhận từ source)

```text
ui.IntraChat (MAIN CLASS duy nhất trong JAR)
    |
    v
ui.frames.LoginFrame
    | Demo                       | Chat trong LAN
    v                            v
MockChatService             LanConnectionDialog
    |                        |            |
    |                        | M1 v1      | M2 v2 TLS
    |                        v            v
    |                  LanDiagnosticsDialog SecureChatCoordinator
    |                       (PING/PONG)    |
    |                                     LiveChatService
    |                                           |
    |                                    LiveChatAdapter
    v                                           v
    +------------------- MainChatFrame ---------+
                             |
                   ComposerPanel / timeline
                 Sender / Time / State / Emoji
```

- **UI chỉ có một Messenger:** `ui.IntraChat`, `ui.frames.LoginFrame`, `ui.frames.MainChatFrame` và dialog/phần tử trong `ui`.
- **Demo:** `model.mock.MockChatService`; có tin nhắn, nhóm, file/media mock. Không truyền LAN.
- **Live hiện có:** `listener.LiveChatAdapter` ánh xạ `LiveChatService` → `ChatActionHandler/ChatClientListener`. Text/emoji, ACK/READ, sender/time/peer UUID và retry cục bộ qua TLS.
- **Networking:** M1 TCP v1 chỉ diagnostics `NetworkCoordinator`; M2 dùng `SecureChatCoordinator`, `TlsChatSession`, `ChatV2Codec`. Dispatcher chung phân luồng v1/v2 ngay từ first frame. Group v3 hiện **không bật**.
- **Group core prototype:** `GroupRoster`, `GroupOwnerGrantService`, `GroupV1Codec`, `GroupInboundMailbox`, `GroupRecipientLedger`, `GroupRestoreGuard`, `ProtectedGroupStore`. Chưa có Group Live fanout/GUI.
- **History:** `LiveChatService.transcript` chỉ giữ trong RAM phiên; chưa có repository bền vững.
- **File/Media:** `ComposerPanel`, `MediaFiles`, `MessageBubblePanel`, `VoiceRecorder` có UI Demo; `LiveChatAdapter.sendMessage` từ chối loại khác `TEXT`.

## 2. Đích tối thiểu để hoàn thành 11 chức năng

```text
                       MainChatFrame (Messenger Live)
                           |
                ChatActionHandler / ChatClientListener
                           |
                       LiveChatAdapter
               /            |             \
      LiveChatService   GroupService   FileTransferService
           |                |             |
      P2P session     roster + fanout   chunks + checksum
           |                |             |
           +------------- TCP/TLS shared -------------+
                           |
                    Verified LAN peers
                           |
                   HistoryRepository (local)
                           |
                    SQLite / persistence

MessageContent => TEXT | FILE | IMAGE | VIDEO | AUDIO
MessageBubble  => Sender | Time | State | Content/Progress
```

Tên `GroupService`, `FileTransferService`, `HistoryRepository` là **thành phần cần viết**, không phải khẳng định class hiện đã tồn tại. Dùng `MessageContent`/FE model hiện tại, mở rộng type/metadata khi test yêu cầu, tránh tái xây UI riêng.

## 3. Ranh giới phải giữ

- **Không khôi phục UI cũ:** `vn.intrachat.IntraChatApplication`, `vn.intrachat.ui.MainWindow/NetworkPanel/InboundRequestDialog`, `ui.LiveIntraChat`, `ui.frames.LiveConnectionFrame` đã bị loại khỏi build. M1 diagnostics có dialog phụ trong Messenger.
- **Không dùng Demo làm backend Live:** mock timers/seeded messages không được phát vào `LiveChatAdapter`.
- **Không đưa socket/file/SQLite I/O vào Swing EDT:** worker xử lý, FE nhận events qua EDT.
- **Không giả State/Time/Sender:** ACK ứng dụng khác READ từ viewport; sender là peer/group participant thực, giờ UTC lưu và local hiển thị.
- **Không phá an toàn cơ bản:** M1 plaintext chỉ PING/PONG; Live tiếp tục dùng M2 TLS hiện tại; dữ liệu file chunk giới hạn, không traversal hoặc ghi đè bừa, không log dữ liệu riêng.

## 4. Giảm độ phức tạp bảo mật đúng phạm vi lớp học

Kiến trúc cũ đã có `DeviceIdentityStore`, `PinnedPeerStore`, `TlsContextFactory`, `PeerTrustService`, owner signing và `ProtectedGroupStore`. **Không xóa những phần đang chạy ổn định**. Nếu cần thiết có thể thiết kế Group fanout dựa trên session P2P sẵn có thay vì bắt buộc dựng thêm một giao thức mã hóa/group ticket mới; việc này vẫn cần validation recipient và test qua nhiều peer.

Audit độc lập, penetration test, multi-user DPAPI, SBOM/provenance chuyên sâu và Gate review chuyên nghiệp **không phải điều kiện để bắt đầu code các chức năng còn thiếu trong đồ án**. Chúng vẫn nằm trong hồ sơ spec/evidence cũ với trạng thái chưa thực hiện; không suy ra hệ thống được chứng nhận.

## 5. Build, mức bằng chứng

- JDK 21, Maven Wrapper, Swing/FlatLaf, Jackson; network TCP/TLS theo source hiện hữu.
- Lệnh build: `.\mvnw.cmd clean package`; launcher: `java -jar target/intrachat-0.1.0-SNAPSHOT.jar`.
- Test gần nhất ghi trong repo 22/11/2026: `scripts/verify-m2.ps1 -Offline` **246/246 JUnit PASS** và executable JAR/diagnostics PASS. Đây là regression toàn dự án, không phải nghiệm thu 11 Live.
- M0 **41/41**; M1 **53/60**; M2 **65/90**; Group **28/92**; History/File/Media **chưa có code Live**. Hai máy Windows vật lý vẫn chưa kiểm thử.

## 6. Tài liệu dẫn đường

- [Scope 11 chức năng](course-project-scope.md)
- [UI Messenger](messenger-integration-plan.md) và [migration 21/11](ui-migration-2026-11-21.md)
- [Feature matrix](feature-matrix.md)
- [Spec 004 Group](../specs/004-group-chat/spec.md) · [005 History](../specs/005-chat-history/spec.md) · [006 File](../specs/006-file-transfer/spec.md) · [007 Media](../specs/007-multimedia/spec.md)

Các báo cáo acceptance/Gate cũ vẫn là lịch sử kỹ thuật; **roadmap hiện hành** dùng tiêu chí chạy thật/đúng mục đích trên Messenger.
