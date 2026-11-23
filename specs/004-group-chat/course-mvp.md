# Spec 004 — Group Chat tối thiểu cho đồ án (Messenger Live)

**Cập nhật:** 22/11/2026 · **Trạng thái:** kế hoạch triển khai Group Live, chưa có Group send/receive qua LAN.  
**Mục tiêu:** ba peer (A, B, C) tạo/tham gia nhóm, mỗi người gửi text/emoji và tất cả người nhận phù hợp thấy đúng tin trong `MainChatFrame`. Đây là **lớp yêu cầu đồ án hiện hành**, đối chiếu với [spec 004 kỹ thuật cũ](spec.md), không xóa evidence và không check lại các task 92 mục.

## Phạm vi chức năng bắt buộc

- A tạo nhóm từ **`CreateGroupDialog` có sẵn trong Messenger**; tên nhóm, Group ID, thành viên có UUID, owner.
- A mời B và C là peer P2P đang kết nối; B/C nhận popup đồng ý/từ chối. Không thêm người chưa đồng ý.
- Sau khi nhận, cả A/B/C thấy một hội thoại nhóm trong danh sách Messenger. Tin nhóm có Group ID, message ID, Sender, Time, content Unicode và State theo recipient.
- Gửi tin nhóm bằng **fanout P2P trực tiếp** tới từng thành viên đang online (hoặc cách tương đương đúng nhiệm vụ); không gửi tới peer D ngoài nhóm, không tạo tin trùng khi retry.
- Người tham gia tự leave; owner có thể loại thành viên và cập nhật roster; người bị loại không tiếp tục nhận tin mới. Thành viên offline được đánh dấu offline, không có delivered giả.
- Nút `Tạo nhóm` trong Live chỉ được bật sau khi `LiveChatAdapter.createGroup` có backend thật; **không gọi `MockChatService`**.

## Đơn giản hóa so với plan bảo mật cũ

**Giữ:** dùng peer UUID/phiên Live P2P đã xác minh, consent invite, revision/roster nhất quán, giới hạn nhóm, recipient đúng và ACK không bị nhầm. Giữ những class owner-signing/DPAPI đã viết nếu tái sử dụng dễ.

**Không bắt buộc cho MVP môn học:** owner phát ticket ECDSA ngắn hạn **cho từng tin**, độc lập Group v3 TLS stack chuyên dụng nếu v2 P2P session mở rộng xử lý được, DPAPI cross-user audit, reviewer Gate0/Gate1 độc lập, resync metadata phức tạp trước khi tạo nhóm/gửi text cơ bản. Nếu đơn giản hóa giao thức, phải viết contract/version và test tránh nhầm frame M1/v2.

Không xóa hoặc bỏ qua an toàn đường truyền hiện hữu. Không bịa chứng minh bảo mật enterprise.

## Checklist để triển khai ngay

- [ ] **G01** Xác định GroupService API/event trên `ChatActionHandler`/Live adapter; không tạo GroupFrame.
- [ ] **G02** Tạo group ID, owner, roster 2–5 thành viên và lưu trong RAM phiên đủ demo; test lỗi/thành viên trùng.
- [ ] **G03** Mời/nhận invitation qua peer Live, popup Accept/Reject, test denial.
- [ ] **G04** Fanout text/emoji theo groupId đến B/C qua peer sessions thật, test 3 JVM.
- [ ] **G05** Đúng Sender/Time và ACK theo từng recipient; không đánh dấu tất cả Delivered khi mới có B.
- [ ] **G06** Leave/remove cập nhật danh sách và ngừng gửi cho peer bị loại; test tin cũ/new revision.
- [ ] **G07** Đưa group vào Messenger directory/timeline/preview/bubble; bật nút tạo nhóm Live sau khi backend sẵn sàng.
- [ ] **G08** Test hai nhóm song song, duplicate, một member offline, close/reconnect không treo UI.
- [ ] **G09** Update ma trận F02, README, hướng dẫn trình diễn và kết quả 3 JVM/PC.

**Bằng chứng đạt F02:** ba cửa sổ Messenger Live độc lập, A mời B/C, mỗi peer gửi text+emoji, B/C thấy đúng sender/time, người ngoài không nhận, leave/remove có tác dụng. **Group Core prototype 28/92 không tự đạt tiêu chí này**.

[ROADMAP](../../ROADMAP.md) · [UI Integration](../../docs/messenger-integration-plan.md) · [Task kỹ thuật lịch sử](tasks.md) · [Phạm vi đồ án](../../docs/course-project-scope.md).
