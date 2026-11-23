# P6 — Final cleanup & acceptance report (23/11/2026)

## A. Tổng quan

**P6 PARTIAL / MVP PARTIAL ACCEPTANCE**, chưa đạt P6 CLEANUP COMPLETE hoặc MVP FULLY ACCEPTED.

- Branch: `feat/messenger-live-integration`
- HEAD: `ef539c125aa08b96b65774317d032acf636441af`
- Git: working tree đã có hàng loạt file modified/deleted/untracked P0–P5 từ trước P6; **không commit, reset, stash, clean**.
- Backup: `D:\Documents\Lap_trinh_ung_dung_mang\Tuan08\IntraChat-P6-backup-20261123`, chứa 217 files, bản sao source/tests/docs/scripts/specs và Git metadata/diff/untracked listing. SHA-256 5/5 file (README, pom, plan, LiveChatService, FileTransferService) giống source; thử restore README vào thư mục tạm PASS.
- Kiến trúc hiện hữu: `ui.IntraChat` → Messenger/`LiveChatAdapter` → `LiveChatService` + `GroupService` + `FileTransferService` + `HistoryRepository` → `LanCoordinator/LanSession` + `WireMessage/WireCodec` → plaintext TCP. JSONL local.

## B. Cleanup và thống kê thay đổi P6

- File **mới**: `scripts/verify-all.ps1`, `docs/p6-final-report.md`.
- File **sửa**: `README.md`, `ROADMAP.md`, `AGENTS.md`, `docs/feature-matrix.md`, `docs/cleanup-inventory.md` (bổ sung trạng thái P6).
- Source Java xóa **0**; test xóa/thay **0**; script xóa **0**; docs/evidence xóa **0**; dependency xóa **0** trong riêng P6.
- Những file security legacy được Git báo đã xóa là **thay đổi có từ P0–P5**, không được gộp vào thống kê P6.
- Có tài liệu `specs/` và `evidence/` lịch sử mô tả security/TLS/signed Group không còn hiện hành. Đã có backup ngoài repo nhưng **chưa đối chiếu toàn bộ phụ thuộc/link nội bộ và hợp nhất từng file**, nên giữ lại để không làm mất tài liệu modified/untracked. Do đó mục tiêu xóa toàn bộ historical evidence **chưa hoàn thành**.
- Không phát hiện tham chiếu TLS/DeviceCertificate/Fingerprint/BouncyCastle/DPAPI trong Java production bằng tìm kiếm từ khóa mục tiêu; script P1 kiểm tra JAR không có các class/dependency security cũ. Chưa thực hiện kiểm toán sâu toàn bộ code chết, dead listeners và EDT.

## C. Chất lượng code / kiến trúc

- `src/main/java`: **49** Java files; `src/test/java`: **30** Java files ở thời điểm kiểm kê.
- `pom.xml`: ba dependency trực tiếp (FlatLaf, Jackson Databind, JUnit Jupiter test). Không phát hiện dependency security trực tiếp. Maven Shade build executable JAR.
- Hệ thống có một đường TCP LAN cho Live và một FileTransferService dùng chung file/media theo mô tả/kiểm thử; **chưa chứng minh** mọi nhánh code không có listener, interface hoặc worker dư thừa.
- Entrypoint `ui.IntraChat` vẫn khởi tạo `MockChatService` cho **Demo** ngoài Live; không thấy bằng chứng mock đi vào Live TCP từ các process smoke, nhưng Demo chưa được loại khỏi launcher theo mục tiêu tinh gọn.
- Trước cleanup Git đã đánh dấu nhiều file legacy Java/tests `D`; không được coi là P6 deletion và đã được bảo vệ trong backup.

## D. Kiểm thử P6

| Kiểm thử | Kết quả | Ghi chú |
|---|---|---|
| Maven clean test package | **PASS** | JDK 21, executable Shade JAR |
| JUnit | **95 PASS, 0 FAIL/ERROR/SKIP** | 30 suite reports |
| P1 TCP socket 2 JVM | **PASS** | text, emoji, UUID, ACK/READ |
| P2 Group socket 3 JVM | **PASS** | Owner Relay, acceptance/reject/remove |
| P3 File socket 2 JVM | **PASS** | CRC/SHA-256, reject/cancel/disconnect/text |
| P4 History/restart | **PASS** | 2–3 JVM, JSONL and group |
| P5 Media TCP/restart | **PASS** | byte/type/metadata/restart |
| P6 extended VIDEO cancel/disconnect/concurrent text/duplicate offer | **NOT RUN** | Chưa tạo test chuyên biệt |
| Video MP4 playback | **NOT RUN** | Chưa điều khiển GUI/player với fixture MP4 hợp lệ |
| WAV device playback | **NOT RUN** | Chưa thử trên thiết bị âm thanh vật lý |
| Messenger GUI | **NOT RUN** | Chưa thực hiện kịch bản hai/ba cửa sổ và kiểm tra tương tác |
| Physical LAN | **NOT RUN** | Không kiểm tra hai/ba máy vật lý |
| Disk full/permission/crash | **NOT RUN** | Chưa thử fault injection |
| Git diff --check | **PASS** | Có cảnh báo chuyển LF/CRLF nhưng exit 0 |
| Markdown links tài liệu chính | **PASS** | Kiểm tra đường dẫn README/ROADMAP/AGENTS/docs; không đại diện toàn `specs/` |
| Executable JAR | **PASS** | JAR tồn tại, P1 xác nhận manifest/classes; chưa mở GUI JAR |

**Lưu ý về test harness:** log ban đầu của P1/P4 đặt trong `target/` khiến Maven Clean không xóa được file log đang mở (FAIL thao tác log, không phải regression Java). Đã chạy lại P1/P4 PASS với log ngoài `target/`. `verify-all.ps1` tạo log dưới `%TEMP%\intrachat-p6-verify-<UUID>`, tránh lỗi này. Lần chạy tổng hợp với `-SkipBuild` đạt P2/P3/P4/P5 + Git diff; lượt chạy trước đã có P1 build/JUnit + P2–P5 PASS nhưng phải sửa xử lý stderr warning của `git diff`.

## E. Ma trận 11 chức năng

| ID | Code thật | Backend verified | GUI verified | LAN verified | Persistence / giới hạn |
|---|---|---|---|---|---|
| F01 P2P | YES | PASS | NOT RUN | NOT RUN | JSONL P4, text |
| F02 Group | YES | PASS | NOT RUN | NOT RUN | Roster/text, owner relay |
| F03 File | YES | PASS | NOT RUN | NOT RUN | Metadata, không resume |
| F04 Emoji | YES | PASS | NOT RUN | NOT RUN | Text Unicode JSONL |
| F05 History | YES | PASS | NOT RUN | NOT RUN | JSONL restart, local-only |
| F06 Image | YES | PASS (transfer/component) | NOT RUN | NOT RUN | PNG/JPEG ≤16 MiB; render GUI NOT RUN |
| F07 Video | YES | PASS (transfer/type) | NOT RUN | NOT RUN | Desktop.open MP4 NOT RUN |
| F08 Sound | YES | PASS (transfer/component) | NOT RUN | NOT RUN | WAV hardware NOT RUN |
| F09 State | YES | PASS | NOT RUN | NOT RUN | Event/ACK persisted |
| F10 Time | YES | PASS | NOT RUN | NOT RUN | UTC timestamp persisted |
| F11 Sender | YES | PASS | NOT RUN | NOT RUN | UUID persisted |

## F. Những vấn đề còn lại

1. Nghiệm thu GUI Messenger P2P/Group/File/Media/history/state/time/sender trên hai/ba cửa sổ.
2. Nghiệm thu LAN vật lý 2–3 PC, firewall/port và disconnect/reconnect.
3. Chạy MP4 Desktop.open với video hợp lệ, WAV Play/Stop trên audio hardware, MP3/M4A fallback.
4. Thêm regression media VIDEO cancel/disconnect/text concurrency/duplicate FILE_OFFER; fault injection permission/disk-full/crash.
5. Kiểm toán chi tiết còn lại: Swing EDT, worker lifecycle, event/listener duplication và Demo UI; phân loại từng spec/evidence legacy và chỉ xóa sau kiểm tra dependency/link.
6. Kiểm tra shade warnings/resource duplicates bằng Maven output không rút gọn; tránh kết luận không warning từ build `-q`.
7. Rà soát liên kết trong toàn bộ specs/legacy documentation trước khi di chuyển/xóa.

## G. Kết luận

**P6 PARTIAL — MVP PARTIAL ACCEPTANCE.**

Đã bảo vệ dữ liệu, đồng bộ tài liệu hiện hành, có `verify-all.ps1`, build/JUnit và toàn bộ P1–P5 backend regression đã có bằng chứng PASS. **Chưa đạt P6 CLEANUP COMPLETE** vì cleanup historical evidence và một số kiểm tra kiến trúc/media extended chưa hoàn tất. **Không đạt MVP FULLY ACCEPTED** vì GUI, LAN vật lý và playback còn NOT RUN.

Không tự ý commit/reset/stash/clean, không xóa dữ liệu LocalAppData hay Git history.
