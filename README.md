# IntraChat — Messenger LAN Java 21

Ứng dụng desktop **Java Swing + FlatLaf** dùng **Jackson JSON** cho chat trong mạng LAN bằng một giao thức **TCP plaintext**. Một Messenger Live dùng `LiveChatAdapter`, `GroupService`, `FileTransferService` và `HistoryRepository` JSONL. **Không có mã hóa hay xác thực danh tính mật mã**; chỉ dùng LAN tin cậy và dữ liệu thử, không mở cổng ra Internet.

## Đúng 11 chức năng MVP

| ID | Chức năng | ID | Chức năng |
|---|---|---|---|
| F01 | P2P Chat | F07 | Video |
| F02 | Group Chat | F08 | Sound |
| F03 | File Transfer | F09 | State |
| F04 | Emoji | F10 | Time |
| F05 | Chat History | F11 | Sender |
| F06 | Image | | |

Kết quả backend P1–P5 trước cleanup: 95 JUnit PASS và process smoke P1–P5 PASS. **GUI, LAN vật lý, MP4/WAV playback thực NOT RUN**; xem [feature matrix](docs/feature-matrix.md).

## Yêu cầu và build

Windows, JDK 21, Maven Wrapper (dependency lần đầu có thể cần Internet). Mở PowerShell ở root repository:

```powershell
$env:JAVA_HOME = 'C:\Path\To\jdk-21'
$env:Path = "$env:JAVA_HOME\bin;$env:Path"
.\mvnw.cmd clean test package
java -jar target\intrachat-0.1.0-SNAPSHOT.jar
```

JAR có Main-Class `ui.IntraChat`. **Luồng C4 Live-only**: `ui.IntraChat → LocalProfile → LiveChatService → LanConnectionDialog → MainChatFrame/LiveChatAdapter`. Không có Demo login, đăng ký hoặc mật khẩu. Dialog kết nối mở trước Messenger, Messenger xuất hiện khi kết nối được chấp nhận. Trong Messenger có nút **Kết nối LAN** để mở lại dialog. Đóng cửa sổ để dừng phiên và bảo toàn History. Có thể chạy `java -jar target\intrachat-0.1.0-SNAPSHOT.jar --diagnostics` mà không cần GUI.

## Chạy hai JVM độc lập (cùng PC)

Mở hai PowerShell trong repository với dataDir **khác nhau**, mỗi JVM có UUID/profile/history riêng:

```powershell
# Terminal A
java '-Dintrachat.dataDir=C:\Temp\IntraChat-A' -jar target\intrachat-0.1.0-SNAPSHOT.jar
# Terminal B
java '-Dintrachat.dataDir=C:\Temp\IntraChat-B' -jar target\intrachat-0.1.0-SNAPSHOT.jar
```

Trong cửa sổ kết nối Live, A **Listen** cổng `47321`; B **Connect** đến `127.0.0.1:47321`; A **Accept** hoặc thử **Reject**. Sau khi kết nối, gửi P2P text và emoji hai chiều, kiểm tra Sender/Time/State. SENT, DELIVERED (ACK), SEEN (READ khi hội thoại foreground) là các trạng thái khác nhau; SEEN không chứng minh đọc bằng mắt. Disconnect/Connect lại thủ công, không tự động resend.

## Group ba JVM

Mở thêm C với `-Dintrachat.dataDir=C:\Temp\IntraChat-C`. A Listen, B và C Connect, A Accept cả hai. Trong Messenger A chọn **+ Tạo nhóm**, mời B/C; ở B/C chấp nhận invite, gửi tin và kiểm tra roster, owner relay cùng per-recipient ACK. Owner offline thì relay không hoạt động; không có tự chuyển owner.

## Hai máy Windows cùng LAN

Trên máy A lấy IPv4 bằng `ipconfig`, ghi nhận IP card LAN/Wi-Fi và cho A Listen cổng TCP đã chọn (ví dụ `47321`). Trên B kết nối đến **IPv4 thực của A**, không dùng `127.0.0.1` giữa hai máy. Đảm bảo cùng mạng/subnet hoặc có route phù hợp, Windows Firewall cho phép inbound TCP tới cổng/application trên mạng Private; không mở Internet/public port. A Accept. Sử dụng `intrachat.dataDir` độc lập trên từng JVM/máy, chỉ thử với dữ liệu mẫu. GUI/LAN vật lý chưa được nghiệm thu tự động.

## File, Image, Video, Sound

Ở hội thoại P2P Live, chọn tệp/media qua composer, gửi **OFFER**; người nhận **Accept/Reject**, chọn nơi lưu; xem progress hoặc Cancel. Truyền theo CHUNK **32 KiB**, có CRC32/kiểm tra kích thước; không overwrite file có sẵn, không resume; file lỗi/cancel cần cleanup. File thường và video tối đa **128 MiB**. Image PNG/JPEG tối đa **16 MiB / 32 MP**, có thumbnail/preview. Video MP4/AVI/MKV tối đa **128 MiB**, mở bằng ứng dụng OS qua `Desktop.open`. Sound WAV/MP3/M4A tối đa **8 MiB**; WAV PCM dùng Java Sound Play/Stop, MP3/M4A mở player mặc định. Gửi/nhận bytes **không** chứng minh playback thực: MP4/codec và thiết bị âm thanh NOT RUN.

## History và dữ liệu

`LocalProfile` lưu UUID/displayName, `HistoryRepository` lưu transcript, sender/time/state, group và metadata file/media trong JSONL riêng từng profile. Mặc định trong `%LOCALAPPDATA%\IntraChat\data`; override bằng JVM property `-Dintrachat.dataDir=<thư mục>`. Gửi vài tin, đóng ứng dụng và chạy lại **cùng dataDir**, mở hội thoại để xem History được khôi phục, kể cả khi peer offline. Không có cloud sync; JSONL không mã hóa, không nên dùng nội dung nhạy cảm; file/media đã nhận phải còn tồn tại để mở lại.

## Kiểm chứng

```powershell
.\mvnw.cmd clean test package
powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\scripts\verify-all.ps1 -JavaHome 'C:\Path\To\jdk-21'
```

Script chạy P1–P5 smoke và Git diff check, log dưới `%TEMP%` (không trong `target/`). Kết quả mới và giới hạn được ghi trong [báo cáo C0–C3](docs/c0-c3-cleanup-report.md), [báo cáo P6](docs/p6-final-report.md), [architecture](docs/architecture.md) và [ma trận 11 tính năng](docs/feature-matrix.md).

### C6 verification note (24/11/2026)

JDK 21 clean test package: **97/97 JUnit PASS**; `scripts/verify-all.ps1` P1–P5 PASS. Two separate GUI JVMs using temporary `-Dintrachat.dataDir=...` directories passed localhost Listen/Connect/Accept, Unicode P2P two-way messages and normal GUI close. This is **not** physical LAN acceptance. GUI Group/media/history restart, extended media race cases, hardware playback and physical LAN still require tests. The history writer's synchronized close boundary now rejects new writes before flush. See [C6 final verification report](docs/c6-final-verification-report.md).
