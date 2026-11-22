# Spec 007 — Image, Video, Sound trong Messenger Live

**Trạng thái:** Draft đồ án, chưa truyền Media Live qua LAN (22/11/2026).  
**Phụ thuộc:** [Spec 006 FileTransfer](../006-file-transfer/spec.md) và frontend Messenger đã có media controls trong Demo.  
**Phạm vi:** Image/Video/Sound **là gửi, nhận, xem/phát tệp**, không phải cuộc gọi video/voice thời gian thực.

## Trải nghiệm người dùng

Trong `MainChatFrame`, người dùng có thể chọn ảnh, video, âm thanh hoặc ghi một clip WAV, gửi cho peer đang online. Người nhận nhận được file, xem/phát từ bubble với đúng Sender/Time/State và có thể lưu.

## Acceptance criteria

- **M-01 Image:** A gửi PNG/JPEG thực qua LAN; B thấy thumbnail phù hợp, mở ảnh đầy đủ, lưu đúng byte và không nhầm với ảnh mock.
- **M-02 Video:** A gửi MP4 hoặc định dạng hệ thống hỗ trợ; B thấy tên/kích thước/tiến độ, lưu/mở bằng trình phát mặc định; format không hỗ trợ được báo rõ.
- **M-03 Sound:** A ghi âm WAV trên máy có mic **hoặc** chọn WAV local rồi gửi; B phát lại từ tệp đã nhận; không cần gọi thoại realtime.
- **M-04 Metadata:** mỗi bubble media có tên/kích thước, người gửi, thời gian, trạng thái chuyển tệp chính xác và nút mở/lưu.
- **M-05 Failure:** file media sai loại, bị cắt, trống, bị hủy, không có trình phát hoặc thiếu mic/loa không làm treo Messenger; dùng fallback lưu/mở file.
- **M-06 Real data:** tất cả payload đi qua FileTransfer, không được gửi ảnh/video/audio demo URI như dữ liệu Live.

## Giới hạn đồ án

Không xây thêm media server hay streaming call, transcoding/video codec chuyên dụng, voice over IP hoặc cloud storage. Tái sử dụng tiện ích `MediaFiles`, `MessageMedia`, `VoiceRecorder` sẵn có **khi đúng**; không coi Demo media là Live.

## Giao diện

Mở các nút Image/Video/Sound trong chính `ComposerPanel` và bubble của `MainChatFrame` **sau khi** FileTransfer LAN thật hoạt động. Không tạo `MediaChatWindow` riêng.

[Plan](plan.md) · [Tasks](tasks.md).
