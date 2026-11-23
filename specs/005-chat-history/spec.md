# Spec 005 — History bền vững trong Messenger

**Status:** Draft cho đồ án môn học, **0 task code hoàn thành** (22/11/2026).  
**Phụ thuộc:** Live P2P text đã hoạt động cục bộ; Group/File sẽ bổ sung loại nội dung về sau.  
**Nguồn:** [ROADMAP](../../ROADMAP.md) · [Phạm vi 11 chức năng](../../docs/course-project-scope.md).

## Mục tiêu người dùng

Khi đóng và mở lại IntraChat Messenger, người dùng vẫn đọc được các tin **thật** đã gửi/nhận theo từng hội thoại, kèm đúng **Sender, Time, State**. Không trộn dữ liệu Demo vào lịch sử Live.

## Phạm vi tối thiểu phải chạy được

1. Lưu local theo `conversationId`, `messageId`, `senderId`, `sentAtUTC`, `contentType`, nội dung text và trạng thái biết được; dữ liệu Group và file metadata sẽ dùng chung mô hình này khi có Live.
2. Dùng SQLite hoặc repository local thay thế có kiểm thử; không lưu user payload vào log. Có đường dẫn data rõ ràng và thao tác xóa lịch sử.
3. Tự ghi khi tin thật được nhận/gửi, đọc theo trang/tìm theo conversation trong `LiveChatAdapter.loadHistory`, không chạy DB I/O trên Swing EDT.
4. Nếu tin đã thấy trong RAM rồi, khi tải từ DB không vẽ 2 bubble cùng `messageId`. Sắp xếp theo thời gian rồi ID để giao diện ổn định.
5. Sau restart, Messenger hiển thị lịch sử direct/group theo conversation ID. Khi offline có thể xem History, nhưng **không gửi tin khi peer đã ngắt**.
6. Không lưu/đồng bộ hóa dữ liệu Demo vào DB Live.

## User stories / Acceptance criteria

- **H-01:** A gửi tin có tiếng Việt/emoji cho B; cả hai thoát; mở lại: mỗi máy thấy lại các tin thuộc đúng hội thoại.
- **H-02:** Hai peer cùng tên khác UUID có lịch sử tách biệt; đúng người gửi, giờ UTC/local sau restart.
- **H-03:** Tin hiển thị lại cùng ID không bị lặp; tin Group phân biệt với direct.
- **H-04:** Người dùng xóa lịch sử của một hội thoại; tắt/mở lại vẫn trống đúng hội thoại đó, các hội thoại khác không mất.
- **H-05:** Lỗi DB bị khóa/hỏng/quyền truy cập được báo trên Messenger, không làm treo UI hoặc phá các hội thoại còn lại.

## History Sync P2P

Yêu cầu nền tảng CL-04 trước đây đề xuất đồng bộ History giữa các peer qua LAN. Trong **pha đầu đồ án**, nghiệm thu F05 tối thiểu là **lưu/đọc lịch sử cục bộ sau restart**. Nếu giảng viên yêu cầu đồng bộ giữa nhiều máy khi reconnect, bổ sung **H-06**: gửi delta theo messageId/conversationId cho đúng peer đã tham gia, chống trùng, không dùng server Internet. **Không đánh dấu H-06 hoàn thành từ một DB local**.

## Không thuộc phạm vi bắt buộc

Không cần xây toàn bộ event sourcing/tombstone/retention enterprise, kiểm toán CSDL mã hóa độc lập, cross-user DPAPI hoặc hệ ACL nhiều tầng trước khi History cơ bản chạy. Vẫn tránh lộ dữ liệu/log và sai conversation.

## Giao diện

**Giữ `MainChatFrame` hiện có** (danh sách hội thoại/timeline), sửa `LiveChatAdapter.loadHistory` và kết nối repository. Không tạo `HistoryFrame` hoặc launcher riêng.

[Task checklist](tasks.md) · [Plan](plan.md).
