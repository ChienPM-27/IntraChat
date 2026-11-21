# Kết bạn qua IP LAN

Người dùng nhận địa chỉ IP từ người cùng mạng LAN, nhập vào IntraChat và gửi lời mời. Hai bên có thể bắt đầu chat riêng sau khi người nhận chấp nhận.

| Tình huống | Tiêu chí chấp nhận |
| --- | --- |
| Gửi lời mời | Mở Kết bạn từ sidebar và nhập địa chỉ IPv4 hoặc IPv6. Lời mời hiển thị ở Đã gửi với trạng thái Chờ chấp nhận. Chưa tạo đoạn chat ở trạng thái này. |
| Nhận lời mời | Sidebar cho biết số lời mời đến đang chờ. Đã nhận hiển thị tên và IP người gửi cùng nút Chấp nhận và Từ chối. |
| Chấp nhận | Lời mời đổi sang Đã chấp nhận và xuất hiện một đoạn chat riêng. Nút Nhắn tin mở đúng đoạn chat. |
| Từ chối | Lời mời đổi sang Đã từ chối. Danh sách đoạn chat giữ nguyên. |
| Xử lý lỗi | Địa chỉ sai, IP ngoài dữ liệu mẫu, IP của mình, bạn đã có và lời mời đang chờ đều có thông báo. Thao tác lặp giữ nguyên số lượng lời mời và đoạn chat. |

Demo có một lời mời đến có sẵn. Khi gửi đến IP mẫu, người nhận mô phỏng chấp nhận sau vài giây để thử cả hai phía của luồng. Mọi dữ liệu kết bạn chỉ tồn tại trong phiên Frontend. Lưu lượng LAN, xác thực thiết bị và giao thức gửi lời mời thuộc đặc tả Backend riêng.
