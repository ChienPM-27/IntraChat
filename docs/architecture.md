# Kiến trúc IntraChat MVP hiện hành (23/11/2026)

Tài liệu này mô tả code hiện có, không phải kiến trúc TLS/M1/M2 lịch sử. Xem [README](../README.md), [feature matrix](feature-matrix.md), [P6 report](p6-final-report.md).

```text
ui.IntraChat
  └─ LocalProfile (UUID/displayName/dataDir)
      └─ LiveChatService (worker, ngoài EDT)
          └─ LanConnectionDialog (Listen / Connect / Accept / Reject)
              └─ MainChatFrame (Swing EDT) ↔ LiveChatAdapter
                ├─ LiveChatService (TEXT, Emoji, ACK/READ)
                ├─ GroupService (owner relay, invite, membership)
                ├─ FileTransferService (file/image/video/sound)
                └─ HistoryRepository (JSONL, restore)
                      ↕
                 LanCoordinator (listener / peer lifecycle)
                      ↕ LanSession (socket, workers)
                 WireMessage / WireCodec / FrameCodec
                      ↕
                 TCP LAN plaintext
```

Sơ đồ là luồng chức năng, không khẳng định mọi cạnh là dependency trực tiếp. Các service Live sử dụng những session do coordinator quản lý; adapter đồng bộ model/UI. C4 đã loại bỏ MockChatService và LoginFrame. Dialog kết nối là cửa sổ đầu tiên; sau kết nối thành công mở MainChatFrame. Nút Kết nối LAN mở lại dialog đang có, không tạo Messenger thứ hai.

## Profile và dữ liệu cục bộ

`LocalProfile` tạo UUID và display name được lưu giữa các lần chạy trong data directory (mặc định dưới `%LOCALAPPDATA%\IntraChat\data`); JVM có thể chỉ định thư mục tách biệt bằng `-Dintrachat.dataDir=...`. UUID/display name do peer tự khai, **không xác thực danh tính**. `HistoryRepository` lưu các bản ghi JSONL local, khôi phục transcript và group metadata/timeline khi khởi động lại; không đồng bộ lịch sử giữa máy. Metadata file/media được ghi lại, nhưng nội dung file nhận vẫn phụ thuộc đường dẫn và vòng đời file thực tế. Offline có thể xem history đã lưu; không có offline network delivery.

## Kết nối TCP và giao thức

`LanCoordinator` nhận kết nối Listen/Connect, yêu cầu Accept/Reject và quản lý session. `LanSession` sử dụng TCP socket, vòng đọc và ghi được phối hợp, timeout và cleanup khi ngắt. `FrameCodec` dùng độ dài int32, giới hạn frame **1 MiB**; `WireCodec` mã hóa envelope JSON/UTF-8 của `WireMessage` với messageId, conversationId, senderId, recipientId, timestamp và payload. Không dùng Java object deserialization, TLS, certificate, fingerprint, SAS, DPAPI hoặc signed group protocol. Dữ liệu **TCP và JSONL plaintext, không mã hóa**, chỉ dùng mạng LAN tin cậy và dữ liệu thử; không đưa cổng ra Internet.

## F01/F04/F09/F10/F11 — P2P và metadata

`LiveChatService` chuyển TEXT Unicode/emoji qua socket; identity theo UUID, timestamp UTC và state SENT/DELIVERED/SEEN dựa sự kiện thật. ACK xác nhận xử lý phía nhận, READ xuất phát từ điều kiện cuộc hội thoại hiển thị/foreground do adapter quan sát; READ không chứng minh người dùng đã đọc bằng mắt. Dedup messageId hạn chế bubble trùng; disconnect/timeout không được suy diễn thành delivered. Tin được ghi history cục bộ sau P4; ACK không đồng nghĩa fsync hoặc đồng bộ remote disk.

## F02 — Group Owner Relay

`GroupService` quản lý group ID, owner, invitation accept/reject, roster, join/leave/remove, relay tin qua owner và ACK theo recipient. Ba JVM process smoke đã kiểm chứng backend, không phải GUI/physical LAN. Owner offline làm mất tuyến relay đang chạy; không có leader election, tự chuyển chủ hay guaranteed offline delivery. Group metadata và history được khôi phục từ local JSONL sau P4.

## F03 — File Transfer

`FileTransferService` dùng cùng TCP session: OFFER → ACCEPT/REJECT → CHUNK, có cancel, progress, CRC32/đối chiếu kích thước và xử lý file tạm khi lỗi. Chunk thường **32 KiB**, nội dung gửi qua codec bounded; receiver quyết định nơi nhận, tránh traversal và overwrite. Chỉ hỗ trợ transfer P2P, không resume, không coi thao tác gửi file thành công là xác nhận phần mềm mở được file.

## F05 — History

`HistoryRepository` ghi JSONL và phục hồi khi restart, lưu sender/time/state, conversation/group và file/media metadata. Lịch sử thuộc từng profile/dataDir, không đồng bộ giữa máy; kiểm thử process restart đã PASS. Chưa nghiệm thu lỗi disk full/ACL/crash; cách hiển thị/index và đọc JSONL có giới hạn bộ nhớ, không bảo đảm fsync trên mọi ACK.

## F06–F08 — Media

Image PNG/JPEG tối đa **16 MiB / 32 MP**, có thumbnail/preview GUI. Video MP4/AVI/MKV tối đa **128 MiB** dùng `Desktop.open` qua ứng dụng hệ điều hành. Sound WAV/MP3/M4A tối đa **8 MiB**, WAV PCM có Java Sound Play/Stop, định dạng còn lại dùng ứng dụng ngoài/fallback. Media dùng chung `FileTransferService` và lưu metadata vào history. Backend byte/type/restart đã PASS; **GUI thumbnail, video player/codec và playback âm thanh trên thiết bị thực NOT RUN**.

## GUI, giới hạn và bằng chứng

Swing/FlatLaf hiển thị một `MainChatFrame` cho Live; callback cập nhật UI trên EDT, network/disk I/O không được chặn EDT. Không còn LoginFrame/Demo; GUI startup và hộp Accept/Reject trên localhost hai JVM đã quan sát được, nhưng GUI Messenger P2P/Group/Media chưa nghiệm thu. P1–P5 socket/process smoke và JUnit chỉ xác minh backend. **GUI thao tác thật, LAN nhiều máy vật lý, video/sound playback, extended concurrency/disk fault đều NOT RUN**; xem [ma trận](feature-matrix.md) và [P6](p6-final-report.md).

## C6 verified lifecycle clarification (24/11/2026)

`HistoryRepository.close()` now synchronizes with its existing synchronized mutation APIs, sets `closed=true` before its flush barrier, then shuts down the single writer. Newly accepted events cannot enter behind the barrier; post-close mutation attempts reject. This preserves JSONL and does not turn TCP ACK into a durable fsync acknowledgment. The C6 concurrent-close/restart JUnit test passes. GUI `LanConnectionDialog` still starts the bounded 30-second approval future before dispatching dialog creation to EDT; EDT-delay race and VoiceRecorder blocked-read cancellation remain unverified, not claimed fixed. See [C6 report](c6-final-verification-report.md).
