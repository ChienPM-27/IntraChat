# Kế hoạch Frontend chat desktop

- **JDK/UI:** Swing và Java 17 theo yêu cầu. FlatLaf cung cấp `FlatLightLaf`, focus, màu và độ bo góc. Dùng Java2D cho icon, avatar, bong bóng và preview.
- **Build/test:** Maven theo bố cục Java tiêu chuẩn; Maven Shade đóng gói FlatLaf vào JAR. JUnit kiểm tra DTO, mock, xử lý media và tương tác Swing.
- **Tích hợp:** `ChatActionHandler` nhận thao tác xác thực, tải lịch sử, gửi và tạo nhóm, trả `CompletionStage` không chặn. `ChatClientListener` nhận dữ liệu cập nhật; UI chuyển callback sang EDT.
- **Dữ liệu:** DTO bất biến, ID độc lập với tên, `Instant` và ID tin để sắp xếp ổn định. Gộp lịch sử/sự kiện theo ID; biên nhận chỉ tăng. Trạng thái nhóm tổng hợp mọi người nhận. Demo không tự nâng trạng thái khi còn thành viên offline.
- **Media:** Đường dẫn local đến từ file chooser hoặc adapter đã kiểm tra và lưu xong. Giới hạn demo: ảnh 16 MiB và 32 megapixel; tệp/video 128 MiB; WAV PCM 8 MiB, mỗi lần ghi tối đa 120 giây. Lưu theo luồng qua tệp tạm, không ghi đè. Mở video theo liên kết hệ điều hành.
- **Vòng đời:** Lịch sử demo chỉ trong RAM. Media mẫu và bản ghi dùng thư mục tạm, xóa khi JVM thoát bình thường. Tệp người dùng chọn lưu giữ nguyên ở đích.

## Constitution Check

| Nguyên tắc | Áp dụng |
| --- | --- |
| I | Demo offline; topology, discovery, cổng và reconnect thuộc kế hoạch Backend LAN. |
| II | ID, loại, thời gian và biên nhận tách biệt. DTO là hợp đồng Java, không phải wire protocol. |
| III | UI trên EDT; I/O media qua worker, có lỗi và đóng thiết bị khi rời phiên. |
| IV | Xác thực tên, định dạng và giới hạn media. Không gửi lịch sử hay mật khẩu ra mạng. |
| V | Kiểm thử thứ tự/trùng tin, hội thoại, trạng thái, định dạng, byte lưu, giới hạn và UI. Kiểm thử nhiều peer thuộc Backend. |
| VI | UI ghi rõ demo; biên nhận dựa trên dữ liệu mẫu/adapter, lỗi media được báo. |
| VII | Đặc tả, tasks và README đi kèm triển khai. |
