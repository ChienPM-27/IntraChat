# Frontend chat desktop

| Người dùng cần | Kết quả quan sát được |
| --- | --- |
| Mở demo từ màn hình đăng nhập hoặc đăng ký | Form có tên người dùng, mật khẩu, lỗi nhập liệu và thao tác chuyển chế độ. Một phiên hợp lệ mở cửa sổ chat có dữ liệu mẫu. |
| Tìm và đọc đoạn chat cá nhân hoặc nhóm | Tìm theo tên trong sidebar. Chọn hàng để xem tên, trạng thái online/offline hoặc số thành viên và lịch sử theo thứ tự gửi. |
| Tạo nhóm | Nhập tên và chọn ít nhất một thành viên. Nhóm xuất hiện và được chọn, số thành viên gồm cả người tạo. |
| Gửi văn bản và emoji | Enter hoặc nút Gửi đưa nội dung vào đúng đoạn chat. Picker chèn emoji tại con trỏ hoặc thay phần đang chọn. Tin trống không được gửi. |
| Hiểu ngữ cảnh của từng tin | Phân biệt tin của mình/đối phương, tên người gửi trong nhóm và giờ địa phương. Tin mới cuộn xuống đáy. Tin cuối của mình hiện `Đã gửi`, `Đã nhận` hoặc `Đã xem`. |
| Chia sẻ media | Chọn ảnh, video hoặc tệp theo bộ lọc. Xem ảnh lớn, mở video bằng trình phát hệ thống và lưu tệp vào vị trí mình chọn. |
| Gửi và nghe tin thoại | Bắt đầu/dừng/hủy ghi âm với bộ đếm, gửi WAV hợp lệ. Tin thoại hiện Play/Pause, tiến trình và thời lượng. |

Ba người dùng mẫu, hai đoạn chat cá nhân và một nhóm có đủ loại nội dung. Trạng thái mẫu và thao tác gửi chỉ diễn ra trong bộ nhớ tiến trình. Lưu lượng P2P/nhóm, xác thực và lưu trữ dài hạn thuộc Backend LAN sau này. Call, Video Call, Voice Room, Story và cài đặt nâng cao nằm ngoài phạm vi.

## Trường hợp biên

- Hội thoại rỗng, tìm kiếm không có kết quả, tên dài, lỗi tải lịch sử hoặc đổi đoạn chat trong lúc tải.
- Tin trùng ID, lịch sử đến sau tin mới, biên nhận đến trước nội dung và hai người trùng tên.
- Người dùng hủy chọn tệp, đích lưu đã tồn tại, tệp mất, media hỏng, thiếu microphone hoặc trình phát mặc định.
