# Tasks 007 — Image / Video / Sound (đồ án)

**Status (22/11/2026): 0/10 checked — chưa code Live Media.** Phụ thuộc FileTransfer 006 để chia sẻ bytes thật.

- [ ] T001 Test/define loại media + metadata, phân loại ảnh/video/audio đúng.
- [ ] T002 Mở API sendImage/sendVideo/sendSound bằng FileTransferService; không tự tạo protocol mới.
- [ ] T003 Nối nút chọn ảnh trong Messenger Live, nhận file và render thumbnail/viewer.
- [ ] T004 Test ảnh JPEG/PNG qua 2 JVM, byte integrity, ảnh lỗi và size limit.
- [ ] T005 Nối video bubble Live với lưu/mở trình phát mặc định và fallback.
- [ ] T006 Test video qua 2 JVM, không có player, cancel giữa chừng.
- [ ] T007 Nối `VoiceRecorder` hoặc chọn WAV thành transfer audio Live.
- [ ] T008 Nối audio bubble/phát WAV khi nhận xong; test thiếu mic/loa/định dạng.
- [ ] T009 Test sender/time/state và progress/hủy media trên Messenger không khóa EDT.
- [ ] T010 Cập nhật README/feature matrix F06–F08 và smoke demo 2 JVM/PC.

**Scope:** chia sẻ tệp và preview/playback, không cuộc gọi thời gian thực. **Không check** từ Demo media hay prototype UI.

[Spec](spec.md) · [Plan](plan.md) · [ROADMAP](../../ROADMAP.md).
