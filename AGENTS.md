# AGENTS — Quy tắc duy trì IntraChat MVP

- Dùng **Java 21**, Maven Wrapper, Swing/FlatLaf và Jackson. Build bằng `mvnw.cmd`; chạy `scripts/verify-all.ps1 -JavaHome <JDK21>`.
- Chỉ triển khai **11 chức năng F01–F11** theo [feature matrix](docs/feature-matrix.md). Không tự thêm cloud, video call hay chức năng thứ 12.
- Duy trì một TCP plaintext transport (`LanCoordinator/LanSession/WireCodec`), một `GroupService` (gồm `GroupMedia` điều phối relay), một `FileTransferService` dùng chung file/media và một `HistoryRepository` JSONL.
- Không khôi phục TLS, certificate, DPAPI, fingerprint, SAS, signed grants hay security gates cũ; không thêm framework nặng hoặc thay stack không cần thiết. Không dùng dữ liệu nhạy cảm qua TCP/JSONL không mã hóa.
- Bảo toàn UUID sender, timestamp, trạng thái ACK/READ thực, bounds/chunk/file integrity, giới hạn memory, cleanup, và dữ liệu local. Không chạy I/O mạng/đĩa nặng trên Swing EDT.
- **Live-only** — `ui.IntraChat → LocalProfile → LiveChatService → LanConnectionDialog / MainChatFrame ↔ LiveChatAdapter`. Profile cũ mở Messenger ngay, `PeerReconnect` tự nối lại endpoint private/loopback đã lưu; profile mới cần kết nối lần đầu. Không Demo/Login/Mock, không authentication server. Một Messenger, một TCP stack; giữ headless `--diagnostics`.
- Giữ lưu tự động media trong profile, kiểm tra CRC/size, UUID tin nhóm và kết quả từng thành viên. Không tự mở/chạy tệp nhận; không scan LAN, tự resend, hoặc coi UUID/IP là xác thực mật mã.
- Phân biệt backend PASS, GUI localhost PASS/PARTIAL/NOT RUN, physical LAN NOT RUN và playback NOT RUN. Không đổi NOT RUN thành PASS khi chưa chạy.
- Kiểm tra `git status`, backup toàn bộ modified/untracked trước khi xóa; không tự commit/reset/stash/clean hoặc đụng LocalAppData thực.
- Tài liệu chính: [README](README.md), [ROADMAP](ROADMAP.md), [architecture](docs/architecture.md), [matrix](docs/feature-matrix.md), [P6 report](docs/p6-final-report.md). Tài liệu hiện hành là nguồn chuẩn; báo cáo C0–C6 là snapshot lịch sử và không được sửa kết quả kiểm thử quá khứ. Xem [hướng dẫn GUI](DESIGN.md) và [báo cáo hiện tại](docs/current-verification.md). Tài liệu specs/evidence đã archive không phải tài liệu bắt buộc cho agent.
