# P3 — File Transfer Live — Báo cáo thực thi 23/11/2026

## A. Trạng thái và phạm vi

**P3 IMPLEMENTED: YES — P3 BACKEND VERIFIED: YES — P3 PARTIAL ACCEPTANCE.** **P3 GUI VERIFIED: NOT RUN. P3 LAN VERIFIED: NOT RUN.** Chưa tuyên bố hoàn thành nghiệm thu toàn diện F03. Được phép bắt đầu P4 dù các mục GUI/LAN P1/P2/P3 chưa được thử; **P2 GUI và P2 LAN vẫn NOT RUN**, không sửa thành PASS.

- Branch: `feat/messenger-live-integration`; HEAD trước/sau: `ef539c125aa08b96b65774317d032acf636441af`. Git working tree ban đầu chứa thay đổi lớn chưa commit của P0/P1/P2; không stash/reset/clean/commit/rebase/force push.
- Backup trước sửa: `D:\Documents\Lap_trinh_ung_dung_mang\Tuan08\IntraChat-P3-backup-20261123`, gồm Git branch/HEAD/status, diff, staged diff, snapshot toàn bộ tệp project (kể cả untracked, trừ .git và target). Đã xác minh snapshot chứa docs/p2-report.md và LanCoordinator.java; thư mục này nằm ngoài working tree.
- P3 dùng một stack TCP cũ: LanCoordinator → LanSession → WireCodec → FrameCodec, không thêm socket listener hay thư viện. F03 File P2P có stream file thật 32 KiB/chunk, accept/reject/save, tiến độ byte, cancel cả hai đầu, cleanup, integrity CRC32, kết quả xác nhận từ receiver và UI Live file bubble. F06 Image, F07 Video, F08 Sound vẫn **NOT IMPLEMENTED** ở Live (P5); History vẫn RAM-only (P4).

## B. Thay đổi theo snapshot P3

**7 file tạo, 15 file sửa, 0 file xóa.** Khác với `git diff --stat` so với HEAD vì HEAD chưa bao gồm các thay đổi P0/P1/P2.

**Tạo (7):**
1. `src/main/java/vn/intrachat/application/FileTransferService.java`
2. `src/test/java/vn/intrachat/application/FileTransferServiceTest.java`
3. `src/test/java/vn/intrachat/protocol/WireFileCodecTest.java`
4. `scripts/P3FileProcessSmoke.java`
5. `scripts/verify-p3.ps1`
6. `src/test/java/ui/components/FileBubbleP3Test.java`
7. `docs/p3-report.md` (báo cáo này).

**Sửa (15):** `WireMessage.java`, `LiveChatService.java`, `LiveChatAdapter.java`, `ChatActionHandler.java`, `ChatClientListener.java`, `Attachment.java`, `ComposerPanel.java`, `MediaFiles.java`, `MessageBubblePanel.java`, `MainChatFrame.java`; `README.md`, `ROADMAP.md`, `docs/architecture.md`, `docs/feature-matrix.md`, `docs/cleanup-inventory.md`.

**Giao thức:** dùng các tên FILE_OFFER, FILE_ACCEPT, FILE_CHUNK, FILE_DONE, FILE_CANCEL đã có trong enum P1/P2; thêm **FILE_REJECT, FILE_RESULT, FILE_ERROR**. Payload `transferId` trong mọi frame; OFFER có fileName/fileSize; CHUNK có offset + Base64 32 KiB; DONE có size + CRC32; RESULT **chỉ receiver gửi sau move thành công**. UUID conversation/sender/recipient/time nằm trong envelope P1 và được đối chiếu với LanSession. Không truyền sourcePath của A tới B, không chuyển file thành text.

**API:** `FileTransferService.offer(peerId,conversationId,sourcePath)`, `accept(id,targetPath)`, `reject(id)`, `cancel(id)`, `handleIncoming(peer,frame)`, `disconnected(peer)`, `get(id)`, `close()`; `LiveChatService.files()/addFileListener()`; `ChatActionHandler.acceptFile/rejectFile/cancelFile`; `ChatClientListener.onFileTransfer()`. Transfer event gồm ID, peer, fileName/fileSize, transferredBytes, state, localPath, error, time. UI gọi JFileChooser nhận file và dùng worker thực hiện disk/network I/O.

## C. Kiểm thử thực tế

Môi trường: Windows 11, JDK Temurin **21.0.12.1**, Maven Wrapper offline. Thực thi:

```powershell
$env:JAVA_HOME='C:\Users\XuanHoai\.jdks\jdk-21.0.12.1+1'
.\mvnw.cmd -o clean test package
powershell.exe -ExecutionPolicy Bypass -File .\scripts\verify-mvp.ps1 -JavaHome $env:JAVA_HOME
powershell.exe -ExecutionPolicy Bypass -File .\scripts\verify-p2.ps1 -JavaHome $env:JAVA_HOME
powershell.exe -ExecutionPolicy Bypass -File .\scripts\verify-p3.ps1 -JavaHome $env:JAVA_HOME
git diff --check
```

| Hạng mục | Kết quả | Bằng chứng/giới hạn |
| --- | --- | --- |
| JUnit | **PASS** | **63 tests**, 0 failures, 0 errors, 0 skipped; bao gồm 10 FileTransferService + 3 WireFileCodec + 2 FileBubbleP3 tests |
| Maven clean test package | **PASS** | JDK21 build, Shade executable JAR thành công |
| P1 regression | **PASS** | verify-mvp.ps1 hai JVM, P2P Unicode, ACK/READ, JAR structural checks |
| P2 regression | **PASS ở lượt cuối** | verify-p2.ps1 ba JVM Group Owner Relay, per-recipient ACK, reject/leave/remove/offline |
| P3 hai JVM | **PASS** | A/B chạy hai tiến trình Java độc lập, TCP localhost, hai profile UUID và dataDir riêng |
| File nhỏ | **PASS** | 317 byte, SHA-256 đầu/cuối giống hệt |
| File nhiều chunk | **PASS** | 13 chunk + 31 byte, CRC32 và SHA-256 giống hệt |
| Tên Unicode | **PASS** | `Tiếng Việt có dấu 🇻🇳.bin`, giữ tên và nội dung chính xác |
| Reject | **PASS** | Receiver từ chối; sender REJECTED, không xuất bản file đích |
| Cancel sender | **PASS** | Hủy sau khi bắt đầu gửi, cả hai CANCELLED, không có file đích hoàn chỉnh |
| Cancel receiver | **PASS** | Hủy sau khi bắt đầu nhận, cả hai CANCELLED, dọn file tạm |
| Integrity | **PASS (JUnit)** | FILE_DONE CRC sai → FAILED, không xuất bản file; SHA-256 đối chiếu P3 process file đúng |
| Disconnect | **PASS** | Ngắt TCP giữa file đang truyền, FAILED, không xuất bản file, cleanup .part |
| File destination handling | **PASS (JUnit)** | Reject tên traversal, ký tự nguy hiểm/Windows reserved, không ghi đè đích đã tồn tại; chưa mô phỏng mọi ACL Windows |
| Text trong khi truyền | **PASS** | Tin Unicode/emoji P2P tới B trong khi file đang hoạt động, file hoàn tất |
| Concurrent transfers | **PASS (JUnit)** | Transfer thứ hai trên cùng peer bị từ chối rõ ràng khi transfer trước đang chờ |
| File GUI | **NOT RUN** | Máy Windows hiển thị LockScreenContentServer, chưa điều khiển thực tế hai cửa sổ Messenger |
| LAN vật lý | **NOT RUN** | Không có chứng cứ truyền trên hai thiết bị vật lý |
| Timeout thực 60 giây | **NOT RUN** | Đã có scheduler nhưng chưa có bài kiểm thử thời gian thực |
| `git diff --check` | **PASS** | Không có lỗi whitespace theo Git |
| JAR cấu trúc | **PASS** | `ui.IntraChat` Main-Class; có FileTransferService, GroupService, LanSession; không có security/TLS legacy |

**Lưu ý regression:** lượt kiểm tra P2 đầu tiên sau P3 có timeout ở `P2GroupProcessSmoke.java` (A chờ group ACK, B chờ owner offline). Chạy lại đúng script P2 sau đó **PASS** cả A/B/C, và lần cuối sau build P3 cũng PASS. Chưa xác định được nguyên nhân timeout không ổn định; giữ lại như rủi ro cần theo dõi, không sửa hoặc bỏ bài kiểm thử P2.

**Logs P3 cuối:** `C:\Users\XuanHoai\AppData\Local\Temp\intrachat-p3-ea37f72370324234adf9e4cbd738d958`. Có A.out/A.err, B.out/B.err, profile-A/profile-B, file nguồn và `received-*`. Script kiểm SHA-256 từng file nhận được. **Logs P2 cuối:** `C:\Users\XuanHoai\AppData\Local\Temp\intrachat-p2-6022d80b4dbb4f21ab7c8025587233f0`.

## D. Rà soát kỹ thuật

- **RAM/stream:** InputStream/OutputStream đọc và ghi liên tục; buffer nhị phân 32 KiB, Base64 43.7 KiB và JSON 1 frame tại một thời điểm; không có readAllBytes toàn bộ file production, không có queue frame file vô hạn. UI progress gộp sau ~256 KiB; mỗi frame có `LanSession` write lock riêng, cho phép TEXT/GROUP control xen giữa chunk.
- **Giới hạn:** file 0–128 MiB; 1 active transfer trên một peer; chunk 32 KiB; JSON frame <=1 MiB; 60s chờ Accept và 60s không có hoạt động. Terminal transfer history trên service giới hạn 256 bản gần nhất; không hỗ trợ resumable/parallel file trên cùng peer.
- **Lifecycle:** OFFERED → WAITING_ACCEPT → TRANSFERRING → COMPLETED, hoặc REJECTED/CANCELLED/FAILED terminal. Sender chỉ COMPLETED sau FILE_RESULT của receiver, receiver chỉ COMPLETED khi đã đối chiếu byte-count, CRC32, đóng file tạm và `Files.move` sang đích.
- **Storage:** B chọn đường dẫn, file tạm `.intrachat-*.part` cạnh destination, chuyển đích không `REPLACE_EXISTING`. Kiểm tra file đích tồn tại và tên filename trước ghi. Không xóa source, không dùng sourcePath của peer. Cleanup khi reject/cancel/error/disconnect/close; thiếu quyền ghi, hết dung lượng được báo thành lỗi I/O. Kiểm tra đầy đủ ACL/low-disk vật lý chưa thực hiện.
- **Lỗi/EDT:** Worker virtual thread gửi file; LanSession reader xử lý nhận/ghi file ngoài EDT; GUI callback được dispatch Swing EDT. Hộp thoại JFileChooser chạy trên EDT như thao tác Swing chuẩn; file I/O nằm ở worker. Không có TLS, checksum chỉ mang ý nghĩa integrity không phải bảo mật.
- **Routing:** file dùng conversationId P2P xác định từ cặp UUID, đúng senderId/recipientId theo session và transferId/offset; GROUP_* đi nhánh group riêng. Group không chấp nhận FILE_CHUNK.
- **Giới hạn acceptance:** File UI đã tích hợp nhưng chưa kiểm nghiệm thao tác thật; bộ đếm tiến độ/Cancel được kiểm chứng bằng service/process harness, chưa phải GUI performance proof. Không có tải nhiều GB, reboot recovery, media preview, file nhóm hay kiểm thử độ bền nhiều giờ. Shade cảnh báo overlap META-INF resources của Jackson/FlatLaf đã tồn tại, không chặn build.

## E. Công việc còn lại / điều kiện P4

1. **GUI NOT RUN:** mở khóa desktop, chạy hai JAR hai dataDir, chọn tệp, Accept/Reject, đường dẫn đích, Cancel A/B, Open sau Completed, kiểm tra bubble/progress/EDT và text xen kẽ. Không gán GUI PASS nếu chưa thao tác.
2. **Physical LAN NOT RUN:** chạy trên hai máy thật, kiểm tra port/firewall, ngắt mạng khi đang transfer, lỗi quyền ghi/ổ đĩa đầy. P2 Group GUI và physical LAN cũng vẫn NOT RUN.
3. **Rủi ro regression:** P2 smoke từng có một lượt timeout rồi chạy lại PASS; xem lại tính ổn định nếu xuất hiện lần nữa.
4. **Bổ sung kiểm thử:** timeout 60s, duplicate/out-of-order chunk trong một transfer đang mở, đổi file nguồn trong khi truyền, disk-full/ACL, đường dẫn symlink, stress 128 MiB, GUI automation.
5. **P5:** tái sử dụng FileTransferService và file events để triển khai F06/F07/F08, không thêm MediaTransferService hoặc TCP listener.
6. **P4 sẵn sàng bắt đầu:** backend P3 verified, regression lượt cuối PASS, JAR hợp lệ; P4 tập trung HistoryRepository JSONL/restart. Không phụ thuộc nghiệm thu GUI/LAN P2 để bắt đầu, nhưng phải ghi rõ các acceptance chưa hoàn thành. F03 chỉ **BACKEND VERIFIED / PARTIAL ACCEPTANCE**, chưa **FULL ACCEPTED**.

**Kết luận:** Có chứng cứ hai JVM TCP gửi/nhận file nhị phân đúng byte, trạng thái/tiến độ từ service thật, kiểm tra integrity và không phát Completed giả ở sender. P3 **IMPLEMENTED + BACKEND VERIFIED — PARTIAL ACCEPTANCE**; **GUI/LAN NOT RUN**.