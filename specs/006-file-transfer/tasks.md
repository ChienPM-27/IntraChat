# Tasks 006 — File Transfer (đồ án)

**Status (22/11/2026): 0/11 checked — chưa code Live File.**

- [ ] T001 Viết test chuyển file >1 chunk, checksum và cancel (RED).
- [ ] T002 Định nghĩa protocol frame file và validate size/type/message ID.
- [ ] T003 Xây sender stream/chunk, buffer có giới hạn, timeout, cancel.
- [ ] T004 Xây receiver lưu file tạm rồi rename khi checksum OK.
- [ ] T005 Xây người nhận Accept/Reject và chọn đường dẫn; chặn traversal/overwrite.
- [ ] T006 Thêm transfer state/progress và trạng thái lỗi, không fake COMPLETE.
- [ ] T007 Nối FileTransferService vào M2 verified P2P transport/Live adapter.
- [ ] T008 Bật nút chọn file Live trong `ComposerPanel` và bubble file trong Messenger.
- [ ] T009 Test GUI EDT/Cancel và hai transfer đồng thời, cleanup khi disconnect.
- [ ] T010 Test hai JVM gửi file tên Unicode, so SHA-256, thử mất mạng/hủy.
- [ ] T011 Cập nhật README/feature matrix F03 và evidence demo đồ án.

**Không cần để đạt F03:** chứng nhận mã hóa bên thứ ba, cloud upload, resumable multi-GB, file group fanout. Vẫn giữ path safety/bounded buffers.

[Spec](spec.md) · [Plan](plan.md) · [ROADMAP](../../ROADMAP.md).
