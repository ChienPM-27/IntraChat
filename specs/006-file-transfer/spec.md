# Spec 006 — File Transfer thực qua LAN trong Messenger

**Trạng thái:** Draft đồ án môn học, **chưa có code truyền File Live** (22/11/2026).  
**Phụ thuộc:** kết nối P2P có sẵn; Image/Video/Sound sẽ dùng FileTransfer này.  
**Nguồn:** [ROADMAP](../../ROADMAP.md) · [Phạm vi](../../docs/course-project-scope.md).

## Mục tiêu người dùng

Chọn tệp trong hội thoại Messenger, gửi cho một peer Live đang kết nối, theo dõi tiến độ, hủy nếu cần; người nhận đồng ý và chọn nơi lưu, tệp không thay đổi nội dung.

## Chức năng tối thiểu

1. `transferId`, `conversationId`, `senderId`, `recipientId`, tên an toàn, kích thước, loại file và checksum SHA-256.
2. Protocol `FILE_OFFER/ACCEPT/CHUNK/COMPLETE/CANCEL/ERROR` hoặc tương đương, có giới hạn chunk và transfer. Không nhét toàn bộ file vào JSON/heap.
3. Người nhận thấy lời mời file và chọn nơi lưu; không ghi đè tệp hiện có nếu chưa được đồng ý, không dùng đường dẫn sender như đích local.
4. Progress thật theo bytes; Cancel đóng stream/tệp tạm và thông báo trạng thái phù hợp. Chỉ hiện **Hoàn thành** khi checksum cuối khớp và tệp được lưu thành công.
5. File được thể hiện trong `MainChatFrame` bằng bubble file, nút xem/tải/lưu; không mở cửa sổ chat File riêng.
6. Đường dẫn xấu (`..\`, absolute, separator, tên bất thường), file quá lớn, mất mạng, thiếu dung lượng có lỗi rõ và không làm treo Swing EDT.

## Acceptance criteria

- **F-01:** Chuyển một tệp nhị phân/tên tiếng Việt từ A sang B qua hai JVM/PC. SHA-256 phía nhận = phía gửi, nội dung không mất/thêm byte.
- **F-02:** B từ chối offer/hủy giữa chừng; không có trạng thái hoàn tất giả hoặc file nhận hỏng được giữ như file cuối.
- **F-03:** A gửi tệp lớn hơn một chunk; heap không giữ toàn bộ payload; UI vẫn cuộn/đổi hội thoại được.
- **F-04:** Từ chối tên/path nguy hiểm và ghi đè ngoài ý muốn; người nhận được chọn nơi lưu.
- **F-05:** Bubble file hiển thị Sender/Time/State, tên/kích thước/tiến độ, mở tệp đã nhận được trong Messenger.
- **F-06:** Demo dùng mock và Live dùng bytes mạng thật; không tạo receipt giả.

## Giới hạn đồ án

Chỉ cần file direct P2P trước. Chia sẻ file nhóm có thể làm sau bằng fanout từng peer. Không yêu cầu resumable multi-GB, content scanning, cloud upload, E2EE audit chuyên sâu. **Vẫn bắt buộc** bounded chunks, destination consent, không traversal, checksum, timeout/cleanup.

[Plan](plan.md) · [Tasks](tasks.md).
