# Kế hoạch Frontend kết bạn qua LAN

- Giữ Java 17, Swing, FlatLaf và Maven. Dùng một nút Kết bạn trong sidebar và một hộp thoại có thể mở song song với chat, gồm ô nhập IP và hai tab Đã nhận, Đã gửi.
- Kiểm tra địa chỉ dạng số tại máy. IP dành cho định tuyến trong bản demo, còn định danh người dùng và lời mời dùng ID riêng.
- Thêm thao tác gửi và phản hồi vào `ChatActionHandler`. `ChatClientListener` chuyển một snapshot chứa lời mời và danh sách đoạn chat để UI đồng bộ sau khi chấp nhận.
- Adapter mẫu xử lý tuần tự trên worker hiện có. Chỉ phía nhận đổi được trạng thái đang chờ. Phần mô phỏng người nhận trả lời lời mời gửi đi sau khoảng 6 giây. Đoạn chat riêng chỉ được thêm khi chấp nhận.
- Lời mời nằm trong RAM của phiên đang mở. Topology, discovery, cổng, transport, firewall, timeout kết nối và chính sách lưu trữ dài hạn thuộc Backend LAN.

## Constitution Check

| Nguyên tắc | Áp dụng |
| --- | --- |
| I | Phiên mẫu hoạt động offline. Kết nối giữa các máy thuộc đặc tả Backend. |
| II | ID lời mời, ID peer, chiều và trạng thái riêng với biên nhận tin nhắn. Snapshot dùng cho UI Java. |
| III | Hành động trả `CompletionStage`, UI cập nhật trên EDT và worker đóng cùng phiên. |
| IV | Dùng địa chỉ IP mẫu trong RAM. Kiểm tra địa chỉ dạng số và giữ xác thực peer trong phạm vi Backend. |
| V | Kiểm thử IP hợp lệ và sai, gửi trùng, chấp nhận hoặc từ chối, đồng bộ sidebar và mở đúng đoạn chat. |
| VI | UI ghi rõ phiên demo và chỉ hiện chấp nhận sau cập nhật của adapter mẫu. |
| VII | Cập nhật đặc tả, tasks và hướng dẫn chạy thử. |
