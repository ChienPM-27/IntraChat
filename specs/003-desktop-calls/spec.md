# Gọi thoại và gọi video 1–1

Người dùng có thể thử gọi thoại và gọi video với một người trong đoạn chat riêng. Giao diện dùng danh bạ mẫu và mô phỏng phản hồi của người bên kia.

| Tình huống | Tiêu chí chấp nhận |
| --- | --- |
| Gọi đi | Header chat riêng có nút Gọi thoại và Gọi video. Cửa sổ hiện tên người nhận và trạng thái Đang đổ chuông. Người gọi có thể hủy trước khi bên kia bắt máy. |
| Cuộc gọi đến | Thao tác Thử gọi đến cho phép chọn thoại hoặc video. Cửa sổ hiện người gọi, nút Chấp nhận và Từ chối. Lời gọi hết hạn sau 20 giây chờ. |
| Đang gọi | Hiện thời lượng, nút bật/tắt mic và nút kết thúc. Với video, hai khung hiển thị avatar mẫu và khung của mình phản ánh trạng thái nút camera. |
| Kết thúc | Hiện kết quả và thời lượng đã gọi. Sau khi kết thúc có thể bắt đầu cuộc gọi mới. Đóng cửa sổ khi đang gọi cũng kết thúc phiên. |
| Ngoại tuyến hoặc bận | Hiện phản hồi khi gọi người ngoại tuyến. Mỗi phiên chỉ có một cuộc gọi đang đổ chuông hoặc đã kết nối. |

Phạm vi demo là gọi 1–1. Mọi trạng thái cuộc gọi, mic và camera đều là mô phỏng cục bộ trong Frontend. Việc truyền media qua LAN cần đặc tả riêng.
