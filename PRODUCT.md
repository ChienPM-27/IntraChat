# IntraChat — sản phẩm đồ án môn học

**Mục tiêu chính:** một phần mềm Messenger Java Swing/FlatLaf sử dụng trong cùng mạng LAN, có **11 chức năng thật**: **P2P, Group, File, Emoji, History, Image, Video, Sound, State, Time, Sender**. Không bắt buộc Internet/cloud, không có gọi video hoặc thoại thời gian thực.

## Trải nghiệm duy nhất

Khởi chạy `ui.IntraChat`. Người dùng chọn **Demo** (mô phỏng để xem UI) hoặc **Chat trong LAN** (dữ liệu thật). Kết nối bằng `LanConnectionDialog`, sau đó dùng chung `MainChatFrame` cho cả hội thoại cá nhân, nhóm, lịch sử và file/media. Có thể bổ sung panel/dialog trong Messenger, **không khôi phục launcher/cửa sổ kỹ thuật cũ**.

Người dùng mở hội thoại, xem người gửi/thời gian/trạng thái, soạn văn bản/emoji, tạo nhóm, đính kèm file/ảnh/video hoặc ghi/chọn âm thanh, gửi/nhận qua LAN, xem/phát/lưu file và đọc lại History sau khi khởi động lại.

## Ưu tiên khi triển khai

1. 11 chức năng **chạy thật và đúng kết quả trong Messenger**, không chỉ Demo/mock.
2. Kết nối/định tuyến đúng peer, Group fanout chính xác, dữ liệu file/ảnh/video/âm thanh nhận nguyên vẹn, History bền vững.
3. UI rõ ràng, không treo khi mạng/file chậm; trạng thái `Đã nhận/Đã xem` không giả.
4. Kiểm thử hai/nhiều JVM và hai máy LAN khi có thiết bị; tài liệu hướng dẫn giảng viên trình diễn.
5. Giữ các lớp TCP/TLS/DPAPI **đã hoạt động** và an toàn file cơ bản, nhưng không đặt kiểm toán bảo mật độc lập/cổng chứng nhận sản phẩm lên trước việc code Group/File/Media.

## Trạng thái từ source hiện tại (22/11/2026)

- **Có Live cục bộ:** P2P text, Emoji, State, Time, Sender.
- **Có core, chưa Live:** Group (roster/owner/codec/DPAPI/receipt; chưa có UI/network Group).
- **Chưa có Live:** File Transfer, History bền vững, Image, Video, Sound. Demo của các chức năng này không tương đương giao tiếp LAN.
- **UI đã hợp nhất:** `ui.IntraChat` và `MainChatFrame` là điểm vào duy nhất; những UI kỹ thuật cũ đã được loại khỏi build.
- **Bằng chứng kỹ thuật gần nhất:** Java21 246/246 JUnit PASS; **không** có nghĩa 11/11 Live đạt nghiệm thu.

**Nguồn thực thi hiện hành:** [ROADMAP](ROADMAP.md) · [Ma trận 11 chức năng](docs/feature-matrix.md) · [Phạm vi đồ án](docs/course-project-scope.md) · [Messenger integration](docs/messenger-integration-plan.md).
