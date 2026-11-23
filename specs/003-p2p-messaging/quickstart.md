# Spec 003 — Chạy thử P2P Live nhanh

## Chuẩn bị

- Windows, Java 21 và Maven Wrapper; dùng **hai tiến trình app với hai identity/test profile khác nhau**.
- Dùng cùng mạng LAN nếu có hai PC. Nếu chạy hai JVM trên một PC, dùng `127.0.0.1` và hai thư mục `secureRoot` riêng; đây **không phải** bằng chứng hai máy LAN thật.

## Build và chạy

Mở PowerShell trong thư mục gốc IntraChat:

```powershell
.\mvnw.cmd -o test
.\mvnw.cmd -o package -DskipTests
```

Mở **hai PowerShell** cho hai cửa sổ thử nghiệm:

```powershell
# Cửa sổ A
java '-Dintrachat.secureRoot=C:\Temp\intrachat-mvp-A' -jar target\intrachat-0.1.0-SNAPSHOT.jar

# Cửa sổ B
java '-Dintrachat.secureRoot=C:\Temp\intrachat-mvp-B' -jar target\intrachat-0.1.0-SNAPSHOT.jar
```

Các thư mục ví dụ cần có quyền ghi; không dùng chung `secureRoot` cho A/B.

## Kịch bản 7 bước

1. A và B chọn **Chat trong LAN** (không chọn Demo).
2. A **Listen** tại một cổng chưa dùng (ví dụ 47321); B **Connect** tới `127.0.0.1:47321` hoặc IPv4 LAN thật của A.
3. A **Accept**; nếu dialog TLS/SAS hiện ra, xác nhận theo giao diện hiện tại ở hai đầu. Không tắt/bỏ qua bằng sửa mã.
4. A gửi **Xin chào Việt Nam 👋**; B nhận và trả lời bằng emoji/tiếng Việt; xem đúng Sender/Time.
5. Kiểm tra trạng thái gửi/nhận; `Đã nhận` phải có ACK thật, `Đã xem` phải có receipt thật sau khi mở hội thoại.
6. Ngắt B rồi thử gửi, kiểm tra báo lỗi; kết nối lại thủ công và gửi tin mới; UI không treo.
7. Mở Demo và xác minh tin Live không trộn với dữ liệu mô phỏng.

Ghi kết quả vào `evidence/m2-mvp-acceptance.md` với cột **PASS / FAIL / NOT RUN** và số liệu test thực tế. Với hai PC, nhập IPv4 của A thay cho localhost và thực hiện tương tự. Không cần Internet hay server trung tâm.

**Không thuộc bản demo P2P:** Group, File, History sau restart và Multimedia. Không xóa hoặc thay thế lớp TLS đang hoạt động chỉ để rút ngắn màn hình xác nhận.
