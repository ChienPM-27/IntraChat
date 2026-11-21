# Kế hoạch Frontend cuộc gọi

- Dùng Swing, FlatLaf và Maven, đóng gói với Java target 21 theo cấu hình của nhánh `FE`. Cửa sổ cuộc gọi chạy modeless để người dùng vẫn xem được chat.
- Thêm hành động bắt đầu, trả lời và kết thúc vào `ChatActionHandler`. `ChatClientListener` nhận snapshot cuộc gọi có ID, peer, chiều gọi, loại cuộc gọi, trạng thái và mốc thời gian. Cập nhật Swing trên EDT.
- Adapter mock nối máy sau khoảng 2 giây với người online. Cuộc gọi đến được khởi tạo từ cửa sổ chat demo và hết hạn sau 20 giây. Worker chạy tuần tự, hủy timer khi đổi trạng thái và từ chối các thao tác dùng ID cũ.
- Trạng thái mic và camera nằm trong cửa sổ cuộc gọi. Avatar được vẽ bằng Java2D như trong chat. Bộ đếm dừng khi kết thúc hoặc đóng cửa sổ. Phiên cuộc gọi tồn tại trong RAM.

## Constitution Check

| Nguyên tắc | Áp dụng |
| --- | --- |
| I | Demo gọi 1–1 dùng mock tại chỗ. Topology, signaling, địa chỉ/cổng và truyền media LAN thuộc kế hoạch tích hợp riêng. |
| II | ID cuộc gọi và ID người tham gia tách khỏi tên và trạng thái tin nhắn. Mốc thời gian dùng `Instant`. |
| III | Worker mô phỏng chuyển trạng thái. EDT chỉ cập nhật giao diện và bộ đếm. |
| IV | Cửa sổ dùng avatar và trạng thái thiết bị mô phỏng trong phiên demo. |
| V | Kiểm thử gọi đi, nhận, từ chối, hủy, hết hạn, gọi trùng, người ngoại tuyến và chuyển trạng thái UI. |
| VI | Cửa sổ ghi rõ cuộc gọi mô phỏng và hiển thị kết quả từ adapter. |
| VII | Cập nhật đặc tả, tác vụ và README theo kết quả kiểm thử. |
