# Thiết kế IntraChat Desktop — 28/11/2026

Giao diện sáng, hai cột, Swing + FlatLaf, icon nét mảnh do ChatIcon tự vẽ. Không có Demo/Login/Register; tên profile là danh tính cục bộ, không phải tài khoản xác thực.

| Token | Giá trị | Vai trò |
|---|---|---|
| Blue | `#0084FF` | Gửi, thao tác chính, bong bóng của bạn |
| Ink | `#050505` | Nội dung và tên |
| Muted | `#65676B` | Preview, giờ, trạng thái, icon Settings |
| White | `#FFFFFF` | Sidebar, header, nền hội thoại |
| Field | `#F0F2F5` | Tìm kiếm, nhập tin, nền nút Settings |
| Divider | `#E4E6EB` | Đường phân cách |
| Presence | `#31A24C` | Chấm online |
| Error | `#B32237` | Thao tác/truyền thất bại |

Segoe UI; avatar chữ cái; sidebar 300 px; cửa sổ mặc định 1100×720, tối thiểu 760×520; header 60 px. Layout Swing co giãn. Icon có tooltip và accessible name; Settings dùng icon 19 px trên nút 35×35 px.

## Bố cục hiện hành

```text
+--------------------------+----------------------------------------+
| Đoạn chat          [+]   | Avatar | Tên peer/nhóm    [Nhóm]*     |
| Tìm kiếm                 | Online/offline hoặc số thành viên      |
|--------------------------+----------------------------------------|
| Alice                    |             Lịch sử tin nhắn           |
| Nhóm LAN                 |                                        |
| Bob                      |        Bubble text / media / file      |
|                          |        Trạng thái, thời gian, sender   |
|--------------------------+----------------------------------------|
| Avatar | Profile | [⚙]   | Ảnh Video Tệp Nốt nhạc Micro           |
|        | LAN TCP         | Nhập tin nhắn / emoji / Gửi            |
+--------------------------+----------------------------------------+
* Nhóm chỉ hiện khi chọn hội thoại nhóm.
```

- Sidebar: tạo nhóm mới (+), tìm kiếm, danh sách hội thoại; Profile ở cuối. Bánh răng nằm ngoài cùng bên phải Profile, tooltip/tên truy cập **Cài đặt kết nối LAN**, mở dialog LAN đang có. Không còn nút chữ Kết nối LAN trên header.
- Header: chat riêng không có nút Nhóm; chat nhóm có quản lý thành viên/rời nhóm tùy vai trò. Không có hội thoại thì ẩn nút.
- Composer: ảnh, video, file, nốt nhạc chọn WAV/MP3/M4A, một micro ghi WAV, emoji màu Twemoji và gửi. Media chỉ khả dụng khi kết nối P2P hoặc nhóm sẵn sàng relay. Draft gắn với hội thoại; đổi hội thoại dừng ghi âm/playback.
- Bubble: text, ảnh thumbnail/preview, WAV controls, video phát trực tiếp trong bong bóng chat (phát/tạm dừng/dừng/phát lại), MP3/M4A mở ứng dụng mặc định, file mở đường dẫn. Media nhận tự lưu; sau khi truyền xong có nút Lưu về máy để chọn vị trí và tên bản sao, không di chuyển bản gốc. Cancel và trạng thái tiến trình/lỗi theo transfer; nhóm hiển thị số thành viên đã nhận/lỗi.
- Profile cũ mở thẳng Messenger, history offline; Listen và reconnect ngầm. Profile mới mở dialog kết nối trước. Cài đặt LAN cho phép chọn IPv4/port, Connect/Accept/Reject và Disconnect; Disconnect tạm dừng reconnect trong phiên. Không tự gửi lại tin.

## Kiểm chứng thiết kế

MediaGuiSmoke đã kiểm chứng ba cửa sổ Swing localhost: nút Nhóm ẩn/hiện theo hội thoại, Settings ở vùng EAST của profile và callback, một micro, chọn tệp gửi/nhận tự động, thumbnail PNG và WAV controls. Kiểm thử reconnect GUI độc lập xem [báo cáo hiện hành](docs/current-verification.md). Không suy diễn từ smoke sang nghiệm thu toàn bộ UI, physical LAN, thu âm hoặc playback thực.

## Giao diện gọi P2P từ upstream

Header có icon điện thoại/video, chỉ enable với peer cá nhân online. Dialog CallDialog xử lý lời mời, chấp nhận/từ chối, mute, camera, chia sẻ màn hình và kết thúc. Smoke contextual controls kiểm tra Settings/Nhóm/media, chưa kiểm thử thao tác cuộc gọi trên desktop.
