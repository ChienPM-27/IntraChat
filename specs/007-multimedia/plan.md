# Implementation plan 007 — Media over FileTransfer

**Status:** kế hoạch, chưa có Live Media code.

1. Dùng `FileTransferService` từ 006 làm **kênh dữ liệu duy nhất**. Metadata hiển thị phân loại `IMAGE/VIDEO/AUDIO/FILE`, mọi loại đi cùng file ID/bytes/hash.
2. Ảnh: khi nhận hoàn tất kiểm tra loại file thực, tạo thumbnail local với kích thước giới hạn, tải viewer khi người dùng click; làm trên worker, không khóa EDT.
3. Video: lưu file ở nơi user chọn, bubble hiển thị tên/dung lượng, nút `Mở video` gọi Desktop/OS player nếu hỗ trợ.
4. Sound: `VoiceRecorder` có thể tạo WAV như Demo; gửi WAV qua FileTransfer; nhận xong mới mở player local. Nếu không có mic, cho phép gửi file âm thanh có sẵn.
5. `MessageBubblePanel` hiển thị trạng thái truyền riêng từng media và Sender/Time. `ChatActionHandler`/adapter cung cấp progress events, không ghi "Đã nhận" trước khi có checksum.
6. Test một ảnh, video, WAV qua 2 JVM; byte integrity, Cancel, format lỗi và không có mic/OS player; manual UI check âm thanh thật.

**Không yêu cầu:** video call/voice call, codec transcoding hay cryptographic certification. **An toàn tối thiểu:** path hợp lệ, kích thước giới hạn, không tự chạy file nhận, báo fallback rõ ràng.

[Spec](spec.md) · [Tasks](tasks.md) · [ROADMAP](../../ROADMAP.md).
