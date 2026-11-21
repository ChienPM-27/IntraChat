# Thiết kế IntraChat Desktop

Giao diện sáng, hai cột với điểm nhấn xanh cho thao tác gửi và bong bóng của bạn. Biểu tượng thoại IntraChat tự vẽ, đi cùng avatar chữ cái và các icon nét mảnh cùng độ dày.

| Token | Giá trị | Vai trò |
| --- | --- | --- |
| Blue | `#0084FF` | Gửi, thao tác chính, bong bóng của bạn |
| Ink | `#050505` | Nội dung và tên |
| Muted | `#65676B` | Preview, giờ, người gửi và trạng thái phụ |
| White | `#FFFFFF` | Sidebar, header, nền hội thoại |
| Field | `#F0F2F5` | Ô tìm kiếm và nhập tin |
| Divider / incoming | `#E4E6EB` | Đường phân cách, bong bóng nhận |
| Presence | `#31A24C` | Chấm online có viền trắng |

Segoe UI là font giao diện, Segoe UI Emoji dùng cho cụm emoji trong nội dung và bảng chọn. Sidebar rộng 300px. Header và thanh nhập cao 60px. Mỗi hàng tin nhắn có giờ gửi, tin nhóm nhận có nhãn người gửi. Trạng thái gửi nằm dưới tin mới nhất của bạn. Icon thao tác có tooltip và tên truy cập bằng bàn phím.
