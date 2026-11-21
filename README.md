# IntraChat Desktop

Bản Frontend demo phong cách Messenger, viết bằng Java Swing và FlatLaf. Chạy độc lập với ba người dùng mẫu, hai đoạn chat cá nhân và một nhóm; không có Server, Socket, Database hay xác thực thật.

## Chạy thử

Yêu cầu JDK 17 trở lên và Maven để build. Maven tải dependency khi cần; file JAR đã đóng gói không cần mạng để chạy.

```shell
mvn clean package
java -jar target/intrachat-0.1.0.jar
```

Nhập một username bất kỳ (tối đa 32 ký tự) và mật khẩu demo không trống. Nếu chọn **Đăng ký**, nhập mật khẩu demo ít nhất 4 ký tự. Không dùng mật khẩu thật.

- **Mai Anh:** người dùng online, lịch sử có ảnh và biên nhận Đã xem.
- **Minh Khang:** người dùng offline; tin gửi trong demo dừng ở Đã gửi.
- **Nhóm dự án:** hiển thị người gửi, số thành viên, tệp, video, WAV và biên nhận Đã nhận. Nhấn **+** để tạo nhóm khác.
- **Thanh soạn:** Enter để gửi tin mock, bảng 30 emoji, chọn ảnh/video/tệp hoặc ghi âm tối đa 120 giây. Bấm ảnh để xem đầy đủ, video để mở bằng trình phát mặc định, biểu tượng tải để lưu tệp không ghi đè.

## Phạm vi

Lịch sử, hội thoại, tài khoản và biên nhận chỉ là dữ liệu mô phỏng trong phiên; đóng ứng dụng sẽ mất thay đổi. Media mẫu và bản ghi sử dụng thư mục tạm; tệp lưu theo lựa chọn của bạn vẫn còn. Ghi và phát tin thoại phụ thuộc thiết bị/quyền âm thanh của hệ điều hành. Ứng dụng không có cuộc gọi, Story hoặc cài đặt nâng cao.

`listener.ChatActionHandler` nhận thao tác từ UI và `listener.ChatClientListener` nhận cập nhật. Bản demo chỉ dùng `model.mock.MockChatService`. Xem [đặc tả](specs/001-desktop-frontend/spec.md) và [kế hoạch](specs/001-desktop-frontend/plan.md) để biết giới hạn và hành vi mock.

## Kiểm tra

`mvn test` chạy test lịch sử, kiểm tra/lưu media và luồng giao diện mock. Test Swing cần môi trường desktop; ảnh chụp layout nằm trong `target/`. Microphone, âm thanh phát ra loa và trình phát video mặc định cần kiểm tra thủ công trên máy sử dụng.
