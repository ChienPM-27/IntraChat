# IntraChat — Kiểm chứng hiện hành (28/11/2026)

**MVP PARTIAL ACCEPTANCE / P6 PARTIAL.** 11 chức năng được triển khai; không suy diễn backend hoặc smoke localhost thành nghiệm thu LAN vật lý hay playback. Baseline C0–C6 được bảo toàn trong các báo cáo lịch sử.

## Phạm vi bản hiện hành

- Windows launcher JDK 21, Maven Wrapper, executable shaded JAR, diagnostics headless.
- P2P/group text và media, Owner Relay, tự lưu theo user, kết quả từng thành viên, history JSONL.
- Profile cũ mở Messenger offline; auto Listen/reconnect endpoint đã lưu và đồng bộ roster.
- Settings icon cuối bên phải Profile; nút Nhóm chỉ hiện trong hội thoại nhóm; một micro, chọn âm thanh bằng nốt nhạc.

## Kiểm thử tái lập

Chạy từ root repository với JDK 21 và profile thử nghiệm, không dùng dữ liệu người dùng thật:

```powershell
.\run.cmd -Diagnostics
powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\scripts\verify-all.ps1 -JavaHome 'C:\Path\To\jdk-21'
powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\scripts\verify-reconnect-gui.ps1 -JavaHome 'C:\Path\To\jdk-21'
java -cp target\intrachat-0.1.0-SNAPSHOT.jar scripts\MediaGuiSmoke.java "$env:TEMP\IntraChat-MediaGui-$([guid]::NewGuid().ToString('N'))"
```

Hai smoke GUI cần desktop Windows, JAR đã build; không chạy trong headless CI. Nếu JAR đang bị khóa, đóng app hoặc build bản sao tạm độc lập. Không dùng -SkipBuild sau khi đổi source. Script verify-all kiểm tra backend và không bao gồm GUI smoke.

## Evidence hiện hành

Kết quả JUnit và GUI media trên bản hợp nhất: 113/113 PASS, 0 failures/errors/skipped; BUILD SUCCESS. MediaGuiSmoke in ra PASS_GUI_CONTEXTUAL_GROUP_AND_PROFILE_SETTINGS và PASS_GUI_MEDIA_END_TO_END. Smoke sử dụng ba cửa sổ Swing trên một JVM với TCP thật. Kiểm thử callback Settings xác nhận đúng vị trí và action, không phải kiểm thử toàn bộ dialog LAN.

Bản sau merge call đã chạy lại verify-all P1–P5 PASS và hai JVM GUI độc lập PASS_GUI_MAIN_WHILE_PEER_OFFLINE / PASS_GUI_TWO_JVM_AUTO_RECONNECT. JUnit PeerReconnectTest kiểm tra peer bật muộn, UUID sai, pause, group text/file sau restart. Không tuyên bố đã kiểm tra hardware hoặc IP thay đổi thực tế.

| Kịch bản | Kết quả |
|---|---|
| JUnit Java 21 | 113/113 PASS |
| P1–P5 backend regression | PASS trên bản hiện hành |
| P2P/group media, auto-save, CRC/bytes, duplicate filename | PASS |
| Owner/member relay, partial failure, origin sai | PASS |
| Lỗi không tạo thư mục nhận | PASS |
| History JSONL restart | PASS |
| GUI PNG thumbnail, WAV controls, mẫu MP4 và file | PASS smoke |
| GUI contextual group/settings, một micro | PASS smoke |
| GUI reconnect, offline startup | PASS smoke hai JVM |
| LAN vật lý | NOT RUN |
| Microphone capture, audio/video playback | NOT RUN |
| GUI restore toàn bộ media sau restart, stress, disk full/ACL/crash | NOT RUN |

## Giới hạn hiện hành

Tối đa 4 TCP sessions, 5 thành viên/nhóm, 1 transfer/peer, 32 endpoint đã nhớ, 16 group media jobs active. Chunk 32 KiB; ảnh 16 MiB/32 MP, audio 8 MiB, video/file 128 MiB, ghi âm 120 giây. Owner phải online và roster sẵn sàng; không tự resend/resume/offline queue/owner failover. Reconnect thử lại khoảng 15 giây, không scan IP mới. UUID/IP tự khai/quan sát không phải xác thực mật mã. Chỉ LAN tin cậy; tự nhận không tự thực thi file. Lịch sử cũ chưa có endpoint cần kết nối một lần.

Tài liệu sử dụng: [README](../README.md), [DESIGN](../DESIGN.md), [architecture](architecture.md), [feature matrix](feature-matrix.md), [ROADMAP](../ROADMAP.md). Bằng chứng lịch sử: [P6](p6-final-report.md), [C6](c6-final-verification-report.md).

## Tích hợp upstream P2P Call

Merge c2a3c2d / fad2ea5 bổ sung calling. Build đầu tiên thất bại 1/113 test do trạng thái mute/camera gửi bất đồng bộ có thể đảo thứ tự; chuyển sang executor tuần tự cho CALL_MEDIA_STATE. Build lại 113/113 PASS. CallServiceTest kiểm tra signaling bằng callback trực tiếp và JPEG qua UDP; CallMediaTransportTest kiểm tra UDP. Không xác nhận thiết bị thực, GUI call hoặc LAN vật lý.
