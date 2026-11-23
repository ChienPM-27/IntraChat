# Spec 003 — P2P MVP acceptance (new scope)

**Trạng thái:** NOT RUN theo checklist MVP mới. Tài liệu được tạo khi tinh gọn phạm vi ngày 23/11/2026; không suy diễn PASS từ báo cáo kỹ thuật cũ.

## Kết quả kiểm thử tự động

- Chạy `.\mvnw.cmd -o test -q` trên working tree sau chỉnh tài liệu: **exit code 0** (23/11/2026).
- Chưa chạy lại GUI smoke hai JVM hoặc LAN hai PC trong đợt thay đổi tài liệu này.
- JUnit thành công không tương đương P2P-01…07 đã được nghiệm thu.

## Checklist

| ID | Môi trường | Kết quả | Ghi chú |
| --- | --- | --- | --- |
| P2P-01 | Hai JVM / hai PC | NOT RUN | Listen/Connect/Accept |
| P2P-02 | Hai JVM / hai PC | NOT RUN | Text hai chiều |
| P2P-03 | Hai JVM / hai PC | NOT RUN | Emoji/Sender/Time |
| P2P-04 | Hai JVM / hai PC | NOT RUN | ACK/READ trung thực |
| P2P-05 | Hai JVM / hai PC | NOT RUN | Reject/IP lỗi/ngắt giữa chừng |
| P2P-06 | Hai JVM / hai PC | NOT RUN | Reconnect thủ công |
| P2P-07 | Hai JVM | NOT RUN | Demo và Live tách biệt |

## Cần ghi khi chạy

Ngày, commit/build, Windows/JDK, địa chỉ/port (không ghi secrets), số JVM/PC, tình huống, kết quả PASS/FAIL, lỗi quan sát và ảnh chụp không chứa dữ liệu riêng tư.

**Chú ý:** bằng chứng test/GUI lịch sử ở `evidence/` vẫn có giá trị tham khảo, không được tự điền PASS khi chưa đối chiếu điều kiện MVP mới.
