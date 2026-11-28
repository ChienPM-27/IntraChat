# IntraChat — Ma trận 11 chức năng MVP (28/11/2026)

Backend PASS là kiểm thử JVM/socket. GUI PASS (smoke) chỉ áp dụng cho kịch bản đã chạy trên localhost. PARTIAL là có bằng chứng nhưng chưa nghiệm thu toàn bộ. Không có chức năng nào được tuyên bố FULLY ACCEPTED trên mọi lớp; LAN vật lý vẫn NOT RUN.

| ID | Chức năng | Backend / implementation | GUI localhost | LAN vật lý | Persistence / Playback / giới hạn |
|---|---|---|---|---|---|
| F01 | P2P Chat | PASS — text hai JVM, ACK/READ, reconnect | PASS smoke — hai chiều, startup/reconnect | NOT RUN | History JSONL; 4 active sessions; TCP plaintext |
| F02 | Group Chat | PASS — owner relay, roster sync, text/media, partial failure | PASS smoke — tạo nhóm, owner/member media, nút Nhóm theo ngữ cảnh | NOT RUN | 5 thành viên; owner offline không relay |
| F03 | File Transfer | PASS — OFFER/ACCEPT/REJECT, CRC/chunk/cancel; tự lưu, tên trùng, lỗi folder | PASS smoke — gửi/nhận không hỏi nơi lưu, P2P/nhóm | NOT RUN | 128 MiB; 1 transfer/peer; không resume |
| F04 | Emoji | PASS — Unicode TEXT | PASS smoke C6; EmojiTextPaneTest kiểm tra emoji màu | NOT RUN | JSONL Unicode |
| F05 | History | PASS — restart JSONL, metadata, profile, endpoint | PARTIAL — startup profile cũ, offline, reconnect; chưa full media-history GUI restore | NOT RUN | Local-only; 10.000 message entries RAM, 500/view; disk full/crash NOT RUN |
| F06 | Image | PASS — PNG/JPEG validation, bytes/type/restore, group relay | PASS smoke — PNG thumbnail | NOT RUN | 16 MiB / 32 MP |
| F07 | Video | PASS — bytes/type/restore, group relay | PARTIAL — mẫu MP4 signature được gửi/nhận | NOT RUN | 128 MiB; FFmpeg nội bộ, MP4 mẫu PASS; AVI/MKV và playback âm thanh thực NOT RUN |
| F08 | Sound | PASS — WAV/MP3/M4A transfer, group relay | PARTIAL — WAV controls, một micro và nốt nhạc | NOT RUN | 8 MiB; ghi âm 120 giây; capture/playback NOT RUN |
| F09 | State | PASS — TEXT ACK/READ, file FILE_RESULT, group per-recipient outcome | PARTIAL — delivered/seen C6, media completion | NOT RUN | Không đồng nghĩa đọc bằng mắt; không tự resend |
| F10 | Time | PASS — UTC timestamp, group originalSentAt | PARTIAL — hiển thị giờ | NOT RUN | Timestamp JSONL |
| F11 | Sender | PASS — UUID P2P/group, từ chối group origin sai | PARTIAL — tên/nhãn người gửi | NOT RUN | UUID tự khai, không xác thực mật mã |

## Hành vi xuyên chức năng

- Profile cũ mở Messenger ngay cả offline, nhớ user gần nhất và cổng Listen; tự thử lại endpoint private/loopback đã lưu. Profile mới kết nối thủ công lần đầu; lịch sử cũ thiếu IP/port cần kết nối một lần để ghi nhớ.
- Settings bánh răng ở bên phải Profile; nút Nhóm chỉ hiện trong nhóm; + tạo nhóm vẫn ở sidebar.
- Media nhận tự lưu vào `data/<user>/media/` hoặc mediaDir của -DataDir. P2P tên trùng thêm hậu tố; nhóm dùng groupId/transferId. Không tự mở tệp.
- GroupMedia sử dụng một FileTransferService; owner forward sau CRC/size, một logical message UUID, kết quả theo thành viên, timeout tổng 10 phút, 16 active group jobs. Cancel best-effort.
- Auto reconnect không scan LAN, không tự resend, không resume, không tự chuyển owner. Mọi máy cần cùng phiên bản. Chỉ dùng LAN tin cậy.

## Chứng cứ và giới hạn

Xem [current-verification](current-verification.md) cho số test và script chạy thực tế; [C6 matrix lịch sử](c6-final-verification-report.md#h-feature-acceptance-matrix) cho baseline 24/11; [P6 report](p6-final-report.md) cho lịch sử cleanup. GUI media smoke dùng ba cửa sổ trên một JVM, reconnect smoke dùng hai JVM độc lập; không suy diễn thành LAN vật lý hoặc playback.

Chưa nghiệm thu: physical LAN, codec/hardware playback, microphone capture, blocked-read cancellation, stress concurrency/disk full/ACL/crash, GUI history media restart đầy đủ và audit lifecycle toàn diện.

## Mở rộng ngoài ma trận MVP gốc: P2P Call

Upstream bổ sung gọi thoại/video, mute/camera/screen share. CallServiceTest và CallMediaTransportTest kiểm tra signaling và UDP localhost (không hardware); GUI call thực, webcam/mic/screen capture, physical LAN NOT RUN. Không gọi nhóm; tối đa một cuộc gọi hoạt động.
