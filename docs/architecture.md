# Kiến trúc IntraChat MVP hiện hành (28/11/2026)

Tài liệu này mô tả code hiện có, không phải kiến trúc TLS/M1/M2 lịch sử. Xem [README](../README.md), [feature matrix](feature-matrix.md), [P6 report](p6-final-report.md).

```text
ui.IntraChat
  └─ LocalProfile (UUID/displayName/dataDir)
      └─ LiveChatService (worker, ngoài EDT)
          ├─ LanConnectionDialog (Listen / Connect / Accept / Reject)
          │   └─ PeerReconnect (saved endpoints, retry)
          └─ MainChatFrame (Swing EDT) ↔ LiveChatAdapter
                ├─ LiveChatService (TEXT, Emoji, ACK/READ)
                ├─ GroupService → GroupMedia (owner relay, invite, membership)
                ├─ FileTransferService (file/image/video/sound)
                └─ HistoryRepository (JSONL, restore)
                      ↕
                 LanCoordinator (listener / peer lifecycle)
                      ↕ LanSession (socket, workers)
                 WireMessage / WireCodec / FrameCodec
                      ↕
                 TCP LAN plaintext
```

Sơ đồ là luồng chức năng, không khẳng định mọi cạnh là dependency trực tiếp. Các service Live sử dụng những session do coordinator quản lý; adapter đồng bộ model/UI. C4 đã loại bỏ MockChatService và LoginFrame. Profile mới mở dialog kết nối trước; profile cũ mở MainChatFrame ngay cả khi offline. LanConnectionDialog quản lý Listen/reconnect và mở lại khi người dùng chọn biểu tượng bánh răng Cài đặt kết nối LAN bên phải profile ở cuối sidebar, không tạo Messenger thứ hai. Nút Nhóm chỉ hiển thị trong hội thoại nhóm.

## Profile và dữ liệu cục bộ

`LocalProfile` nhớ user gần nhất trong data/last-user.txt; tạo UUID và display name được lưu giữa các lần chạy trong data directory (mặc định `data/<tên user>` trong thư mục project); JVM có thể chỉ định thư mục tách biệt bằng `-Dintrachat.dataDir=...`. UUID/display name do peer tự khai, **không xác thực danh tính**. `HistoryRepository` lưu các bản ghi JSONL local, khôi phục transcript và group metadata/timeline khi khởi động lại; không đồng bộ lịch sử giữa máy. File nhận tự lưu dưới media/; data/ được Git ignore. Metadata file/media được ghi lại, nhưng nội dung file nhận vẫn phụ thuộc đường dẫn và vòng đời file thực tế. Offline có thể xem history đã lưu; không có offline network delivery.

## Kết nối TCP và giao thức

`LanCoordinator` nhận kết nối Listen/Connect, yêu cầu Accept/Reject cho peer mới, kiểm tra UUID đích khi reconnect và quản lý session. `LanSession` sử dụng TCP socket, vòng đọc và ghi được phối hợp, timeout và cleanup khi ngắt. `FrameCodec` dùng độ dài int32, giới hạn frame **1 MiB**; `WireCodec` mã hóa envelope JSON/UTF-8 của `WireMessage` với messageId, conversationId, senderId, recipientId, timestamp và payload. Không dùng Java object deserialization, TLS, certificate, fingerprint, SAS, DPAPI hoặc signed group protocol. Dữ liệu **TCP và JSONL plaintext, không mã hóa**, chỉ dùng mạng LAN tin cậy và dữ liệu thử; không đưa cổng ra Internet.

## Tự kết nối lại

History JSONL lưu thêm LOCAL_LISTEN, PEER_ADDRESS và PEER_ENDPOINT. HELLO/WELCOME quảng bá cổng Listen; lấy IP từ socket đã kết nối, không lấy source port làm cổng đích. PeerReconnect retry nền có giới hạn session, tối đa 32 endpoint private/loopback, kiểm tra UUID đích trong handshake; phía nhận đối chiếu UUID và IP đã lưu. Peer mới vẫn cần phê duyệt thủ công; không scan mạng hay xác thực mật mã. Profile cũ thiếu endpoint cần kết nối một lần. Disconnect thủ công tạm dừng retry; không resend tin/file. GROUP_SYNC giúp đồng bộ membership với chủ nhóm sau reconnect, loại bỏ membership đã bị owner hủy trong lúc offline.

## F01/F04/F09/F10/F11 — P2P và metadata

`LiveChatService` chuyển TEXT Unicode/emoji qua socket; identity theo UUID, timestamp UTC và state SENT/DELIVERED/SEEN dựa sự kiện thật. ACK xác nhận xử lý phía nhận, READ xuất phát từ điều kiện cuộc hội thoại hiển thị/foreground do adapter quan sát; READ không chứng minh người dùng đã đọc bằng mắt. Dedup messageId hạn chế bubble trùng; disconnect/timeout không được suy diễn thành delivered. Tin được ghi history cục bộ sau P4; ACK không đồng nghĩa fsync hoặc đồng bộ remote disk.

## F02 — Group Owner Relay

`GroupService` quản lý group ID, owner, invitation accept/reject, roster, join/leave/remove, relay tin qua owner và ACK theo recipient. Ba JVM process smoke đã kiểm chứng backend, không phải GUI/physical LAN. Owner offline làm mất tuyến relay đang chạy; không có leader election, tự chuyển chủ hay guaranteed offline delivery. Group metadata và history được khôi phục từ local JSONL sau P4. Group Media dùng GroupService/GroupMedia để điều phối cùng FileTransferService: chủ nhóm fan-out, thành viên upload tới chủ nhóm rồi chủ nhóm relay sau khi CRC32/size hợp lệ. Mỗi chặng có transferId riêng; groupMessageId, origin và originalSentAt giữ nguyên. Offer nhóm phải đến từ thành viên gốc tại chủ nhóm hoặc từ chủ nhóm tại thành viên, và phải có roster hiện hành. Chỉ FILE_RESULT sau lưu thành công được tính là đã nhận; GROUP_MEDIA_STATUS trả kết quả từng người về origin. Một tin logic được ghi GROUP JSONL. Tối đa 5 thành viên, 1 truyền/peer, 16 job nhóm active; quá hạn tổng 10 phút, không resume/offline queue, hủy best-effort.

## F03 — File Transfer

`FileTransferService` dùng cùng TCP session: OFFER → ACCEPT/REJECT → CHUNK, có cancel, progress, CRC32/đối chiếu kích thước và xử lý file tạm khi lỗi. Chunk thường **32 KiB**, nội dung gửi qua codec bounded; receiver tự lưu vào thư mục media của profile, tránh traversal và overwrite. P2P dùng tên gốc hoặc hậu tố số; nhóm dùng thư mục groupId/transferId. Không resume, không coi thao tác gửi file thành công là xác nhận phần mềm mở được file.

## F05 — History

`HistoryRepository` ghi JSONL và phục hồi khi restart, lưu sender/time/state, conversation/group và file/media metadata. Lịch sử thuộc từng profile/dataDir, không đồng bộ giữa máy; kiểm thử process restart đã PASS. Chưa nghiệm thu lỗi disk full/ACL/crash; cách hiển thị/index và đọc JSONL có giới hạn bộ nhớ, không bảo đảm fsync trên mọi ACK.

## F06–F08 — Media

Image PNG/JPEG tối đa **16 MiB / 32 MP**, có thumbnail/preview GUI. Video MP4/AVI/MKV tối đa **128 MiB** phát trong `VideoPlayerPanel` với JavaCV/FFmpeg (Windows x64), giải mã trên worker và cập nhật Swing trên EDT. Sound WAV/MP3/M4A tối đa **8 MiB**, WAV PCM có Java Sound Play/Stop, định dạng còn lại dùng ứng dụng ngoài/fallback. Media dùng chung `FileTransferService` và lưu metadata vào history. Backend byte/type/restart đã PASS; GUI localhost đã gửi/nhận media tự động, kiểm tra thumbnail PNG và WAV controls. VideoPlayerPanelTest kiểm tra giải mã MP4 mẫu trong Swing; AVI/MKV, âm thanh và playback thực tế trên thiết bị người dùng chưa được nghiệm thu.

## GUI, giới hạn và bằng chứng

Swing/FlatLaf hiển thị một `MainChatFrame` cho Live; callback cập nhật UI trên EDT, network/disk I/O không được chặn EDT. Không còn LoginFrame/Demo; GUI startup và hộp Accept/Reject trên localhost hai JVM đã quan sát được, GUI Messenger P2P/Group/Media có smoke localhost; xem ma trận delta. P1–P5 socket/process smoke và JUnit chỉ xác minh backend. **LAN nhiều máy vật lý, video/sound playback, extended concurrency/disk full/crash đều NOT RUN**; xem [ma trận](feature-matrix.md) và [P6](p6-final-report.md).

## Bằng chứng lịch sử C6 — lifecycle clarification (24/11/2026)

`HistoryRepository.close()` now synchronizes with its existing synchronized mutation APIs, sets `closed=true` before its flush barrier, then shuts down the single writer. Newly accepted events cannot enter behind the barrier; post-close mutation attempts reject. This preserves JSONL and does not turn TCP ACK into a durable fsync acknowledgment. The C6 concurrent-close/restart JUnit test passes. GUI approval vẫn có timeout 30 giây; bản hiện hành kiểm tra decision.isDone() trước khi tạo dialog EDT. Stress timeout-vs-approval và VoiceRecorder blocked-read cancellation vẫn chưa được nghiệm thu. See [C6 report](c6-final-verification-report.md).

Kiểm chứng hiện hành và giới hạn theo lớp: [current-verification](current-verification.md). Thiết kế GUI: [DESIGN](../DESIGN.md).

## Mở rộng upstream — P2P Call

CallService điều phối một cuộc gọi VOICE/VIDEO, ring timeout 45 giây, trạng thái mute/camera/screen share. LiveChatService chuyển CALL_* signaling qua TCP hiện có. CallMediaTransport mở UDP cổng động, audio PCM 16 kHz mono 16-bit và JPEG phân mảnh (chunk tối đa 1350 bytes, frame tối đa 256 KiB). AudioCallEngine dùng Java Sound; VideoCallEngine dùng videoInput Windows DirectShow và Robot cho screen share. CallDialog cập nhật qua callback EDT. Media state được gửi tuần tự để tránh trạng thái camera/mute cũ đến sau trạng thái mới. Không mã hóa, không gọi nhóm; hardware và GUI call thật NOT RUN.
