# IntraChat — Messenger LAN Java 21

Ứng dụng desktop Swing + FlatLaf, Jackson JSON, **một transport TCP plaintext** cho nhắn tin LAN. Một Messenger Live, Owner Relay cho nhóm và FileTransferService dùng chung cho file/ảnh/video/âm thanh. Dữ liệu history lưu JSONL riêng từng profile.

## 11 chức năng MVP

F01 P2P Chat · F02 Group Chat · F03 File Transfer · F04 Emoji · F05 Chat History · F06 Image · F07 Video · F08 Sound · F09 State · F10 Time · F11 Sender.

Backend đã chạy regression P1–P5 (95 JUnit PASS, process smoke P1–P5 PASS). **GUI, LAN vật lý và playback thực vẫn NOT RUN**; xem [ma trận](docs/feature-matrix.md).

## Cài đặt và build

Yêu cầu Windows với **JDK 21** và Maven Wrapper. Dependency có thể cần Internet ở lần đầu; chỉ thêm `-o` khi Maven cache đã đủ.

```powershell
$env:JAVA_HOME = 'C:\Path\To\jdk-21'
$env:Path = "$env:JAVA_HOME\bin;$env:Path"
.\mvnw.cmd clean test package
java -jar target\intrachat-0.1.0-SNAPSHOT.jar
```

JAR có entrypoint `ui.IntraChat`. Có thể xem diagnostics không mở GUI bằng `java -jar target\intrachat-0.1.0-SNAPSHOT.jar --diagnostics`.

## Trình diễn hai JVM cùng máy

Mở hai terminal PowerShell sau khi build:

```powershell
# Terminal A
java '-Dintrachat.dataDir=C:\Temp\IntraChat-A' -jar target\intrachat-0.1.0-SNAPSHOT.jar

# Terminal B
java '-Dintrachat.dataDir=C:\Temp\IntraChat-B' -jar target\intrachat-0.1.0-SNAPSHOT.jar
```

Chọn **Chat trong LAN (TCP)** trên cả hai cửa sổ. Ở A chọn **Listen** tại `127.0.0.1`, port `47321`. Ở B chọn **Connect** đến `127.0.0.1:47321`; A chọn **Accept**. Chọn cuộc hội thoại và gửi text/emoji, quan sát sender/time và trạng thái SENT/DELIVERED/SEEN. Có thể thử Reject, Disconnect và Connect lại. Seen chỉ có nghĩa khi phía nhận hiển thị cuộc hội thoại theo điều kiện foreground.

## Trình diễn Group ba JVM

Mở terminal C với dataDir `C:\Temp\IntraChat-C`, dùng lệnh như trên. A **Listen**; B và C **Connect** vào A, A **Accept** cả hai. Trên A nhấn **+ Tạo nhóm**, chọn B/C và tạo nhóm; trên B/C chấp nhận lời mời. Gửi tin từ A/B/C, kiểm tra Owner Relay và per-recipient ACK. Chủ nhóm có thể loại thành viên; thành viên có thể rời nhóm. Không tự chọn chủ nhóm mới nếu A offline.

## File, ảnh, video, âm thanh

Trong cuộc hội thoại P2P Live, sử dụng composer để chọn tệp hoặc loại media, gửi yêu cầu và chọn **Accept/Reject** ở người nhận; kiểm tra progress, cancel, hoàn tất và mở tệp đã nhận. File dùng CRC32 và kiểm tra byte/size, chunk 32 KiB; không ghi đè file có sẵn, không resume. Image: PNG/JPEG tối đa **16 MiB, 32 MP**, xem thumbnail/phóng ảnh. Video: MP4/AVI/MKV tối đa **128 MiB**, mở bằng player mặc định của Windows qua `Desktop.open`. Sound: WAV/MP3/M4A tối đa **8 MiB**; WAV PCM dùng Java Sound Play/Stop; MP3/M4A mở ứng dụng mặc định. Playback trên thiết bị thực chưa nghiệm thu.

## History và dataDir

Thoát cả hai JVM rồi mở lại **với đúng dataDir cũ** để xem các cuộc hội thoại/tin nhắn và metadata đã lưu. `profile.json` lưu UUID/tên; dữ liệu JSONL đặt tại `history\messages.jsonl` trong dataDir. Mặc định dùng `%LOCALAPPDATA%\IntraChat\data`. History local-only: không đồng bộ sang máy khác, không ghi bytes tệp vào JSONL, không phục hồi transfer dang dở; dữ liệu lưu **không mã hóa**.

## Hai/ba máy LAN vật lý

Cấu hình mỗi máy với profile riêng; máy A **Listen** trên IPv4 LAN (không phải 127.0.0.1), B/C nhập IPv4 và cổng A rồi **Connect**. Kiểm tra firewall/cùng mạng và quyền truy cập cổng. LAN vật lý chưa được kiểm chứng P6; cần thực hiện nghiệm thu thủ công.

## Kiểm thử và bàn giao

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\scripts\verify-all.ps1 -JavaHome $env:JAVA_HOME
```

Script tổng hợp Maven/JUnit + P1–P5 process smoke + `git diff --check`; log riêng được tạo trong `%TEMP%` **ngoài** `target` để Maven Clean không xung đột. Chạy các scripts `verify-mvp.ps1`, `verify-p2.ps1`, `verify-p3.ps1`, `verify-p4.ps1`, `verify-p5.ps1` nếu cần kiểm tra từng giai đoạn.

**Giới hạn bảo mật:** TCP và dữ liệu JSONL đều plaintext; UUID không xác thực mật mã. Chỉ dùng trên LAN thử nghiệm tin cậy, không truyền dữ liệu nhạy cảm hoặc mở cổng ra Internet. Không hỗ trợ cloud, group media, gọi âm thanh/video, history sync, tìm kiếm nâng cao. Các test GUI 2–3 cửa sổ, LAN vật lý, MP4 thật và loa WAV vẫn **NOT RUN**.

Tham khảo [kiến trúc](docs/architecture.md), [ma trận](docs/feature-matrix.md), [roadmap](ROADMAP.md), [báo cáo P6](docs/p6-final-report.md).
