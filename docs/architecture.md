# Kiến trúc thực tế IntraChat — P1 TCP + P2 Group + P3 File (23/11/2026)

Xem [README](../README.md), [ma trận tính năng](feature-matrix.md) và [báo cáo P1](p1-report.md).

```text
ui.IntraChat → LoginFrame (Demo tách biệt / nút Live TCP)
                     └→ LanConnectionDialog (Listen / Connect / Accept / Reject / Disconnect)
                           └→ LanCoordinator (một TCP listener, giới hạn peer)
                                  └→ LanSession (Socket + worker đọc / ghi có khóa)
                                       ↕ WireCodec (bounded UTF-8 JSON)
                                       ↕ FrameCodec (int32 length prefix, tối đa 1 MiB)
                             LiveChatService (text/emoji, message UUID, ACK/READ)
                                       ↓ events
                         LiveChatAdapter → MainChatFrame / ChatViewMapper
                                  (EDT, sender/time/state/foreground read)
        LocalProfile → %LOCALAPPDATA%\IntraChat\data\profile.json
```

## LocalProfile và định danh

`LocalProfile.loadOrCreate()` tạo UUID ngẫu nhiên lần đầu và giữ qua restart cùng `displayName`; override qua JVM property `intrachat.dataDir`. Trùng displayName không trùng thiết bị. P2P conversation UUID tạo xác định bằng `UUID.nameUUIDFromBytes("intrachat:dm:" + hai UUID sắp xếp)`, không dùng fingerprint hoặc alias. Cả A/B luôn tính cùng ID; không cần lưu mapping. UUID do bên kia **tự khai**, không xác thực.

## Wire protocol / TCP

Một TCP listener; `HELLO {senderId, payload.displayName}`, peer nhận xem tên/UUID/endpoint và Accept hoặc Reject, trả `WELCOME` hoặc `REJECT`. Sau đó một `LanSession` chỉ nhận/gửi frame đúng senderId/recipientId và conversationId. Một message envelope JSON có `type, messageId, conversationId, senderId, recipientId, sentAtUtc, payload`. Loại đang chạy: `TEXT`, `ACK`, `READ`; `ERROR` và tên loại GROUP/FILE mới được dự trù trong enum, **không xử lý trong Live P1**. Payload UTF-8 Unicode, giới hạn frame 1 MiB; không dùng Java Object Serialization. P3 dùng FILE_CHUNK Base64 theo JSON có giới hạn, không tạo framing hay socket thứ hai.

`LanSession`: một reader virtual thread/phiên, synchronized write, timeout đọc 15 giây (idle tiếp tục), timeouts handshake và connect, đóng socket idempotent. `LanCoordinator` giới hạn 4 peer hoạt động / 4 ứng viên chờ, bỏ phiên UUID trùng và dọn phiên đã đóng trước reconnect. Không tự reconnect, không tự resend.

## Tin nhắn và trạng thái

`LiveChatService` tạo messageId UUID và UTC sentAt; kiểm tra conversation, sender/recipient theo session. Inbound TEXT được xử lý và dedup trước khi ACK; duplicate same ID chỉ ACK lại, không tạo bubble mới. ACK hợp lệ chuyển Delivered; READ chỉ từ `LiveChatAdapter.onMessageVisible` do `MainChatFrame` quan sát bubble đang hiện khi cửa sổ foreground. Khi ACK không xác định, trạng thái UNCERTAIN; retry thủ công chỉ cùng session còn mở. Event UI được đưa qua Swing EDT; không thao tác socket trên EDT.

Transcript Live giữ trong RAM có giới hạn; khi mất kết nối vẫn xem được trong tiến trình, nhưng **chưa có HistoryRepository trên ổ đĩa**, restart mất tin nhắn. Demo/mock và Live tách backend.

## Mã đã gỡ / ranh giới P2–P6

P1 đã gỡ TLS session, certificate/key/fingerprint, DPAPI/pin/trust stores, signed Group prototype, M1 diagnostics cùng các codec/protocol cũ, BouncyCastle và JNA. Java AWT focus thay phụ thuộc JNA để tránh READ khi ứng dụng không active. File security cũ trong LocalAppData không bị xóa. P2 đã bổ sung GroupService/Owner Relay mới (xem phần phía dưới). Việc gỡ Group prototype P1 không được nhầm với nghiệm thu GUI/LAN P2 hoàn chỉnh. HistoryRepository/Media Live chưa triển khai; xem ROADMAP.

**Mô hình tin cậy:** TCP plaintext LAN thử nghiệm; UUID không chứng minh người gửi thật. Không gửi dữ liệu nhạy cảm hoặc mở cổng ra Internet.

## Group Owner Relay — P2 (23/11/2026)

```text
MainChatFrame (CreateGroupDialog, sidebar, bubbles, menu Nhóm)
      |  ChatActionHandler / ChatClientListener (Swing EDT)
LiveChatAdapter  ──── GroupService ── ChatGroup + RAM timelines + ACK ledger
      |                    |
      +── LiveChatService ←+── group-type dispatcher
                 |
       LanCoordinator → LanSession (1 TCP session/peer, 1 reader)
                 |
                 +──── A owner ↔ B
                 +──── A owner ↔ C
```

Không có Group TCP listener riêng. A mời B/C bằng GROUP_INVITE. B/C hiển thị popup async trên Swing EDT, trả GROUP_ACCEPT hoặc GROUP_REJECT; owner bổ sung **chỉ các peer đã chấp nhận** và phát GROUP_UPDATE gồm roster + revision tăng dần. Owner serialize xử lý các frame nhận đồng thời để không mất cập nhật roster. GROUP_UPDATE kèm alias hiển thị từng UUID để B và C xem đúng tên người gửi dù không trực tiếp nối nhau; alias không dùng làm khóa định tuyến.

Một frame GROUP_TEXT giữ envelope sender/recipient theo TCP session để LanSession xác thực tính nhất quán. Payload chứa `origin` (UUID tác giả gốc), `originalMessageId`, `sentAt` UTC, `text`. B → A → C giữ nguyên metadata gốc trong payload; C chỉ nhận Group text từ session A; A chỉ nhận Group text từ session B/C khi người đó còn trong roster. `groupId` là `conversationId`, distinct UUID cho mỗi nhóm. B/C không cần kết nối trực tiếp.

Với ACK: người nhận gửi GROUP_ACK gồm groupId, originalMessageId, `recipient` và `origin`. Owner gửi ACK nhận tin của chính mình về B/C, và forward ACK C về B (hoặc B về C); adapter chỉ hiển thị DELIVERED sau khi mọi recipient trong snapshot lúc gửi đã xác nhận, không giả ALL READ. Tin trùng messageId cùng nội dung được loại bằng RAM dedup; thay đổi cùng ID bị từ chối. GROUP_LEAVE và GROUP_REMOVE cập nhật roster (rev), sau đó owner fanout chỉ tới roster mới. Offline không xóa nhóm; owner offline chặn gửi mới từ member. Giới hạn group 2–5 người, text 32 KiB UTF-8, frame tối đa 1 MiB.

Nhóm, timeline và receipt ledger hiện lưu trong RAM; chưa sync history/persist (P4). TCP plaintext và peer UUID self-declared không có xác thực mật mã. Không khôi phục giao thức signed grant/GroupV1/V3 hoặc TLS. GUI/physical LAN acceptance không suy từ process smoke; xem [báo cáo P2](p2-report.md).
## P3 — File Transfer P2P (23/11/2026)

```text
MainChatFrame / ComposerPanel / File bubble
    → LiveChatAdapter (ChatActionHandler + ChatClientListener trên Swing EDT)
    → LiveChatService.files() → FileTransferService
    → LanSession.send(WireMessage) → WireCodec JSON → FrameCodec 1 MiB → TCP
```

Tái sử dụng **duy nhất** kết nối P1, không TCP listener mới. Một file/peer tại một thời điểm, giới hạn 128 MiB. `FILE_OFFER` kèm UUID transfer/conversation, sender/recipient trong envelope, fileName và fileSize (không có sourcePath), tiếp theo `FILE_ACCEPT` hoặc `FILE_REJECT`. A đọc file bằng InputStream 32 KiB/chunk, mã hóa **chỉ từng chunk** thành Base64 cho `FILE_CHUNK {transferId, offset, data}` (khoảng 44 KiB JSON/frame), B đối chiếu offset tuần tự và giới hạn 32 KiB rồi ghi OutputStream vào tệp tạm `.intrachat-*.part` trong thư mục đích đã chọn. A gửi `FILE_DONE {size, crc32}` sau EOF; B so byte count/CRC32, đóng stream, `Files.move` **không REPLACE_EXISTING**, sau đó mới gửi `FILE_RESULT`. A chỉ chuyển `COMPLETED` khi nhận `FILE_RESULT` hợp lệ, không phải sau write cuối. CRC32 chỉ phát hiện sai dữ liệu, không có xác thực mật mã.

State tối thiểu: OFFERED → WAITING_ACCEPT → TRANSFERRING → COMPLETED, hoặc REJECTED / CANCELLED / FAILED (terminal). `FILE_CANCEL` cho cả A/B, `FILE_ERROR` cho lỗi I/O/protocol. Close/disconnect/timeout 60s dọn worker/stream/file tạm; không xóa file nguồn hay file đích tồn tại trước. Peer/session/conversation được xác minh bởi `LanSession` + file service. Worker virtual thread truyền file, mỗi chunk khóa ghi TCP độc lập để text/control nhóm không bị chặn suốt file; phát tiến độ theo byte thật và gộp UI mỗi ~256 KiB. Event cập nhật bubble qua Swing EDT, file lưu/đọc ngoài EDT. Sender chỉ lưu sourcePath nội bộ; recipient chỉ lưu targetPath nội bộ. File group chưa hỗ trợ. RAM là buffer 32 KiB + Base64 chunk; không giữ toàn bộ file. Không hỗ trợ truyền lại sau restart hay file >128 MiB.

Hai JVM socket thật đã PASS (SHA-256 file đầu-cuối) nhưng thao tác GUI Messenger và LAN vật lý vẫn NOT RUN; không dùng kết quả này để gán GUI VERIFIED. Chi tiết: [P3 report](p3-report.md).


## Bổ sung chất lượng P2 — timeout lời mời và hiển thị ACK (23/11/2026)

```text
Owner A -- GROUP_INVITE --> B/C
        <-- GROUP_ACCEPT / GROUP_REJECT --
        -- GROUP_UPDATE (accepted roster + revision) --> accepted members
        -- GROUP_REMOVE (invite expires) --> unanswered invitees

GROUP_TEXT (original sender/messageId/time) --> owner relay --> recipients
GROUP_ACK (original messageId, recipient)  --> owner forwards to origin
         GroupService recipient counts --> LiveChatAdapter
         --> ChatClientListener.onGroupReceiptChanged --> MainChatFrame bubble
             0/N ... 1/N ... N/N; unknown send remains UNCERTAIN
```

Lời mời không được trả lời hết hạn theo mặc định sau 30 giây. Owner giữ nhóm đã có thành viên Accept; nếu chưa có ai Accept, thao tác tạo nhóm hoàn tất bằng lỗi timeout và không để lại nhóm chỉ có owner. Trạng thái ACK là bằng chứng nhận **từng recipient** trên phiên TCP; không cung cấp READ nhóm hoặc cam kết bảo mật danh tính. Không tạo TCP listener khác, scheduler riêng, Group coordinator phân tán hay persistence mới. GUI thật/LAN vật lý chưa nghiệm thu.
## P4 — HistoryRepository / startup restore

`LocalProfile.loadOrCreate` xác định `dataDir` trước khi `LiveChatService` tạo `HistoryRepository`. `HistoryRepository` tải `history/messages.jsonl` từ dataDir riêng của profile (không phải `target/`). `LiveChatService` sử dụng kho này khi gửi/nhận TEXT, ACK/READ và khi nhận sự kiện FileTransfer; `GroupService` lưu message/roster và nạp roster trong chế độ **chưa xác minh** sau restart; `LiveChatAdapter` khởi tạo sidebar từ metadata và load timeline trên worker ngoài Swing EDT; `MainChatFrame` giữ hội thoại dù peer offline. Khi đóng bình thường, `LiveChatService.close()` gọi `HistoryRepository.close()`, drain writer và flush.

Dữ liệu **JSONL append-only** gồm `MESSAGE` (messageId, conversationId UUID ổn định, DIRECT/GROUP, sender/recipient UUID, timestamp UTC, TEXT/FILE, state, file metadata), `STATE`, `TRANSFER_STATE`, `GROUP` (groupId, ownerId, tên, member UUID, revision) và `PEER` (UUID + alias). Một bounded single writer (queue 1024) ghi tuần tự và `FileChannel.force`; update chỉ thêm event, reader gộp theo message ID và bỏ qua các dòng lỗi. Tin FILE chỉ ghi metadata, không ghi bytes/chunks và không dùng đường dẫn từ peer làm nơi lưu history. Tin FILE đang dở khi load được hiển thị INTERRUPTED/FAILED, không resume. Group roster cũ không tự trở thành quyền gửi mới.

Đọc có giới hạn: index RAM giữ tối đa 10.000 message và API `loadMessages(conversation,limit,offset)` tối đa 500 mỗi trang, thứ tự `sentAtUtc` rồi `messageId`. Dedup qua persisted message ID, kết hợp timeline RAM và disk; ACK/READ cập nhật state, không tạo bubble riêng. `HistoryRepository` chạy I/O trên worker trong đường ghi; phần đọc JSONL khi khởi tạo service xảy ra lúc startup nên launcher không được gọi khởi tạo nặng trực tiếp trên EDT. Cần lưu ý: ACK TCP hiện có nghĩa **đã xử lý ở phía ứng dụng**, chưa phải cam kết fsync trước ACK vì writer bất đồng bộ. Không có mã hóa history và không có mạng sync; dữ liệu nhạy cảm không phù hợp môi trường này.

Giới hạn hiện tại: chưa có thao tác UI để load trang cũ hơn 500; việc đọc file lúc startup vẫn duyệt toàn bộ JSONL (một dòng quá lớn vẫn có thể tốn bộ nhớ trước khi bị loại). Các trường hợp I/O/ACL lỗi phải được xem như rủi ro, không suy ra bền vững tuyệt đối từ ACK. Chi tiết chứng cứ: [P4 report](p4-report.md).
## P5 — Media có type trên một đường FileTransfer (23/11/2026)

`ComposerPanel → LiveChatAdapter.sendMessage(MessageContent.media(type, Attachment)) → LiveChatService.files() → FileTransferService.offer(peer,conversation,path,type) → LanSession → TCP`. `FILE_OFFER` bổ sung metadata `messageType=FILE|IMAGE|VIDEO|SOUND`; offer P3 không có trường mới được mặc định `FILE`. Mọi nội dung vẫn dùng `FILE_CHUNK` 32 KiB, CRC32, `FILE_DONE` và `FILE_RESULT`, không có listener/protocol riêng. Giới hạn IMAGE 16 MiB, SOUND 8 MiB, FILE/VIDEO 128 MiB. Transfer ID cũng là message ID, riêng ACK/READ text không thay đổi.

`LiveChatService.persistTransfer` lưu loại media và trạng thái vào `HistoryRepository` JSONL, chỉ metadata + localPath **của máy hiện tại**, không truyền sourcePath từ A sang B. `LiveChatAdapter` khôi phục type và bubble cùng ID/time/sender sau restart, đổi transfer đang dở thành FAILED/INTERRUPTED. `MessageBubblePanel` hiện trạng thái/accept/reject/cancel/progress trong lúc truyền; khi COMPLETED, `MessageMedia` render ImageIO thumbnail async, nút Desktop.open video, Java Sound WAV Clip + fallback mở audio bên ngoài. Không decode ảnh/video/checksum trên EDT, không tự mở/phát; Clip đóng khi Stop/chuyển hội thoại/đóng app/kết thúc phát. Video không decode trong backend; ảnh có giới hạn 32MP và co kích thước.

GUI/LAN nghiệm thu vật lý NOT RUN. Thông tin giới hạn và bằng chứng test: [p5-report.md](p5-report.md).
