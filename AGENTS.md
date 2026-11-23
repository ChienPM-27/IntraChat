# AGENTS — IntraChat MVP

- Java 21, Maven Wrapper, Swing/FlatLaf, Jackson, TCP plaintext; chỉ 11 chức năng F01–F11 tại [feature matrix](docs/feature-matrix.md).
- Một ứng dụng Messenger Live, `LiveChatService`, `GroupService`, `FileTransferService`, `HistoryRepository`, `LanCoordinator/LanSession` và `WireMessage/WireCodec`. Không thêm TCP stack hay file service song song.
- Không triển khai TLS/DPAPI/certificate/fingerprint, cloud, login, group leader election, database server hoặc tính năng thứ 12.
- Giữ bất biến UUID sender, ID/timestamp, ACK/READ dựa trên sự kiện, integrity byte và lịch sử JSONL. Không thực hiện network/disk nặng trên Swing EDT.
- Chạy `scripts/verify-all.ps1 -JavaHome <JDK21>` trước khi bàn giao; phân biệt PASS, FAIL và NOT RUN; không dùng Demo/mock làm bằng chứng Live.
- Trước thao tác xóa/ghi đè: đọc `git status`, giữ file modified/untracked, tạo backup ngoài repo và kiểm tra SHA-256. Không tự commit/stash/reset/clean.
- Docs chính: README, ROADMAP, docs/architecture.md, docs/feature-matrix.md, docs/p6-final-report.md; specs/evidence cũ chỉ là historical reference, không phải yêu cầu hiện hành.
