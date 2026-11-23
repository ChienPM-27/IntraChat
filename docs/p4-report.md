# P4 — Chat History Persistence — Báo cáo kiểm chứng 23/11/2026

## A. Trạng thái

**P4 IMPLEMENTED + BACKEND VERIFIED — P4 PARTIAL ACCEPTANCE.** F05 có lưu/đọc local JSONL, khôi phục P2P/Group/file metadata khi restart hai/ba JVM độc lập. **GUI VERIFIED: NOT RUN. PHYSICAL LAN VERIFIED: NOT RUN.** Không tuyên bố nghiệm thu đầy đủ F05. F06/F07/F08 chưa triển khai.

Branch `feat/messenger-live-integration`, HEAD `ef539c125aa08b96b65774317d032acf636441af`. Working tree đã có thay đổi chưa commit P0–P3 và các tệp P4 untracked ngay trước lần kiểm chứng này; **không** reset/stash/commit/clean. Đã tạo thêm backup riêng bên ngoài repository tại `D:\Documents\Lap_trinh_ung_dung_mang\Tuan08\IntraChat-P4-backup-20261123-session2`, gồm Git status/branch/HEAD/diffs và snapshot source/docs/scripts (trừ .git/target); so SHA-256 của các file quan trọng thành công. Một backup P4 khác cùng ngày đã tồn tại, được giữ nguyên.

## B. Source và dữ liệu

**Trong trạng thái ban đầu của lượt làm việc này**, bốn tệp P4 đã tồn tại nhưng chưa được Git track: `src/main/java/vn/intrachat/persistence/HistoryRepository.java`, `src/test/java/vn/intrachat/persistence/HistoryRepositoryTest.java`, `scripts/P4HistoryProcessSmoke.java`, `scripts/verify-p4.ps1`. Đã kiểm tra trực tiếp code đã tích hợp vào `LiveChatService`, `GroupService`, `FileTransferService` và `LiveChatAdapter`/Messenger.

**Thay đổi thực hiện trong lượt kiểm chứng:** 1 file mới (`docs/p4-report.md`); 7 file sửa (`HistoryRepository.java`, `HistoryRepositoryTest.java`, `README.md`, `docs/architecture.md`, `docs/feature-matrix.md`, `docs/cleanup-inventory.md`, `ROADMAP.md`); **0 file xóa**. Tệp P4 hiện hữu trước lượt này được giữ nguyên nội dung trừ bản sửa phòng vệ state và một JUnit test mới. Không được diễn giải số file của lượt này là toàn bộ thay đổi P4 so với HEAD chưa commit P0–P3.

Repository API: `appendMessage(Entry)`, `updateState(id,state)`, `updateTransfer(id,state,transfer,path)`, `saveGroup(group)`, `savePeer(id,alias)`, `loadMessages(conversation,limit,offset)`, `conversations()`, `groups()`, `peers()`, `find(id)`, `flush()`, `close()`. Dữ liệu tại `<LocalProfile.dataDir>/history/messages.jsonl` gần `profile.json` trong từng profile độc lập, mặc định `%LOCALAPPDATA%\IntraChat\data`. Các loại record: `MESSAGE`, `STATE`, `TRANSFER_STATE`, `GROUP`, `PEER`. JSONL append-only không mã hóa, không nhúng binary/FILE_CHUNK. Group ID, owner, name, roster/revision lưu bằng GROUP events; Group restore không đánh dấu roster đã xác minh để gửi mới.

Writer có 1 worker, queue bounded 1024, tuần tự, `FileChannel.force(false)` mỗi event; close chờ flush có timeout. Reader bỏ qua dòng JSON lỗi và giữ dòng hợp lệ, merge theo ID; tải tối đa 10.000 entries vào index RAM, 500 per request. File dở được biểu diễn INTERRUPTED/FAILED trên load; terminal transfer không thể đổi thành terminal state khác bởi event trễ (bổ sung test). Sender/time/messageId không sinh lại từ history.

## C. Kiểm thử

Thực thi trên Windows, JDK21, Maven Wrapper offline. Các lệnh thực sự đã chạy: `mvnw.cmd -o clean test package`, `verify-mvp.ps1`, `verify-p2.ps1`, `verify-p3.ps1`, `verify-p4.ps1 -SkipBuild`. Script P1 vẫn tự build trong lần chạy này. Sau bản vá chuyển trạng thái file, đã chạy lại build và smoke để xác nhận ở phần cuối báo cáo.

| Hạng mục | Kết quả | Phạm vi |
| --- | --- | --- |
| JUnit | PASS | 87 tests, 0 failure, 0 error, 0 skipped sau thêm 1 regression |
| Maven clean test package | PASS | shaded executable JAR |
| P1 regression | PASS | hai JVM TCP text Unicode, ACK/READ |
| P2 regression | PASS | ba JVM A/B/C Owner Relay |
| P3 regression | PASS | hai JVM file, CRC/SHA256, reject/cancel/disconnect |
| P4 hai JVM + restart | PASS | profile A/B được khởi động lại trong JVM riêng với cùng dataDir |
| P2P history | PASS | hai message emoji/tiếng Việt, sender/time và ID gốc |
| Group history | PASS | ba JVM Group và mỗi JVM được restart để kiểm tra roster/timeline |
| File metadata history | PASS | completed, tên/kích thước/path trên từng profile, không có Base64 payload |
| ACK/READ persistence | PASS | ACK/READ thật và restart bằng process |
| Deduplication | PASS | gửi lại message ID gốc sau restart, không thêm bubble |
| Corrupted JSONL | PASS | bản sao profile test có record lỗi sau record hợp lệ |
| Unexpected shutdown | NOT RUN | chưa có test kill giữa khi writer đang ghi |
| DataDir isolation | PASS | UUID khác, file history khác |
| Missing attachment | PASS | di chuyển tệp test rồi restore, không crash |
| Interrupted transfer process kill | NOT RUN | recovery khi restart được JUnit kiểm thử, chưa kill process giữa chunk |
| Large history stress | PARTIAL | JUnit thử paging 25 entries; chưa stress rất lớn hoặc UI scrolling |
| GUI History | NOT RUN | chưa thao tác desktop Messenger đóng/mở |
| LAN vật lý | NOT RUN | chưa chạy nhiều PC |

`verify-p4.ps1` khởi tạo hai tiến trình mạng, ghi JSONL khác nhau, restart A và B bằng tiến trình JVM khác, đọc khi peer offline, reconnect tiếp tục cùng conversationId, replay tin trùng, kiểm tra file metadata và file bị di chuyển. Script kiểm tra JSONL lỗi trên bản copy test. Nó còn chạy **ba lượt** P2 Group process A/B/C, sau đó tạo ba JVM restore độc lập từ dữ liệu lượt đầu. Cả ba lượt P2 smoke trong P4 PASS; chưa quan sát timeout tái diễn ở những lượt này. Lịch sử P3 ghi nhận một timeout P2 trước đây, không xóa rủi ro đó chỉ dựa trên ba PASS.

**Bằng chứng:** build log `%TEMP%\intrachat-p4-session2-build.log`, script logs `%TEMP%\intrachat-session2-verify-*.ps1.log`; P4 process artifact `%TEMP%\intrachat-p4-fd3690fe47f9422aa67fef999e32e837`. Script JVM process không phải GUI acceptance.

## D. Giới hạn / rủi ro

- Không cloud, cross-machine sync, đa thiết bị, mã hóa, auto reconnect, resend mọi history, resume file.
- P4 chỉ lưu dữ liệu kể từ khi persistence chạy; không cứu được transcript P1/P2/P3 chỉ từng tồn tại trong RAM của tiến trình đã đóng.
- ACK TCP hiện xác nhận ứng dụng đã xử lý; history write queue bất đồng bộ nên ACK **không** đồng nghĩa record đã fsync. Nếu crash trước khi flush, message mới có thể mất; không mô tả ACK như durable receipt.
- Khởi tạo HistoryRepository duyệt JSONL lúc startup; giới hạn 10.000 message trong index RAM, tối đa 500 tin trên API và chưa có nút UI lấy trang cũ. Hàng JSONL quá dài bị bỏ qua **sau** khi BufferedReader tạo line trong RAM. Test stress lớn, low-disk, ACL, force kill chưa được xác nhận.
- Khi file local đã mất, metadata/bubble vẫn tồn tại nhưng không thể mở. Binary file không nằm trong JSONL; không khôi phục file từ máy khác.
- Dòng JSONL lỗi được log và bỏ qua, không truncation toàn bộ file. Record cuối ghi dở không làm mất record hợp lệ trước đó; không cam kết record chưa flush. Không tự xóa history khi lỗi.
- Java IO writer lỗi được log và các lời gọi sau có thể báo lỗi; chưa kiểm thử tất cả đường báo lỗi lên UI. Group roster cũ chỉ để xem lịch sử, không tự cho gửi tới khi chủ nhóm xác minh lại. GUI thực tế chưa kiểm chứng.
- Maven Shade cảnh báo các resource META-INF chồng lấp trong Jackson/FlatLaf, không làm build thất bại.

## E. Công việc tiếp theo

P4 đủ bằng chứng để tiếp tục P5 **về backend**, tái sử dụng metadata FILE và FileTransferService cho Image, Video, Sound thay vì tạo stack mới. Trước nghiệm thu đầy đủ F05 cần GUI restart thật, LAN 2–3 thiết bị, test force kill/interrupted file, ACL/full disk, stress rất lớn và phân trang UI. P6 cần xem lại các mô tả lịch sử P1–P3, cleanup docs/spec legacy sau backup và nghiệm thu toàn bộ F01–F11. Không tự đánh dấu GUI/LAN PASS.
