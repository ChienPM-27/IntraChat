# P2 — Group Chat Live Owner Relay — Báo cáo thực thi 23/11/2026

## A. Tổng quan và trạng thái

**Kết luận: P2 PARTIAL ACCEPTANCE** — **P2 IMPLEMENTED: YES**, **P2 BACKEND VERIFIED: YES**, **P2 GUI VERIFIED: NOT RUN**, **P2 LAN VERIFIED: NOT RUN**. Không tuyên bố **P2 COMPLETE** hoặc nghiệm thu toàn bộ F02 khi chưa thao tác thực tế ba cửa sổ Messenger và ba thiết bị LAN.

- Branch: `feat/messenger-live-integration`; HEAD trước/sau triển khai: `ef539c125aa08b96b65774317d032acf636441af`. Không commit/reset/stash/clean/rebase.
- Trước P2: working tree chứa nhiều thay đổi chưa commit từ P0/P1; baseline được chạy lại với Java 21, **44/44 JUnit PASS**.
- Backup độc lập: `D:\Documents\Lap_trinh_ung_dung_mang\Tuan08\IntraChat-P2-backup-20261123`. Có `pre-p2.diff`, `pre-p2-status.txt`, `head.txt`, `branch.txt`, `repository/` sao chép toàn bộ working tree trừ `.git` và `target`. Đã kiểm tra backup đủ 68/68 source files và lưu 1.039.300 bytes Git diff. Có thể phục hồi file riêng theo relative path sau khi đối chiếu, không ghi đè tự động những thay đổi mới.
- Triển khai `ChatGroup` immutable, `GroupService` RAM-only; `LiveChatService` phân luồng frame `GROUP_*` lên service này **trong chính LanSession**, không có TCP stack/listener thứ hai.
- Mô hình A owner nối TCP B/C; B gửi qua A đến C, C gửi qua A đến B, giữ nguyên `originalMessageId`, `origin`, UTC `sentAt`, `text`. Owner chỉ fanout tới thành viên đã Accept. B/C nhận roster cùng revision và nhãn tên hiển thị được owner chuyển tới; UUID vẫn là khóa định tuyến.
- Messenger `MainChatFrame` hiện có bật nút tạo nhóm và popup `CreateGroupDialog`, cập nhật sidebar, group bubbles, header menu xem roster/rời nhóm/loại thành viên, báo owner offline. Popup chấp nhận/từ chối được chạy trên EDT; TCP I/O chạy worker P1.
- Group ACK ghi theo từng `recipient` cho mỗi message ID, owner chuyển ACK C → B khi B là tác giả. Chỉ hiện DELIVERED khi tất cả recipient lúc gửi đã ACK, không tạo READ nhóm giả. Khi mất kết nối, gửi thất bại/chưa rõ được báo riêng; không tự retry vô hạn.
- Giới hạn: 2–5 thành viên gồm owner, tối đa 16 group RAM, 500 tin/group, 512 receipt ledgers gần nhất, text 32 KiB UTF-8; nhóm không tồn tại qua restart (P4). Owner không thể rời nhóm khi vẫn là chủ; có thể loại các thành viên khác. Không owner migration, không full mesh.

## B. Thay đổi mã nguồn

Đối chiếu với snapshot backup **ngay trước P2** (khác với Git HEAD vốn chứa rất nhiều thay đổi P0/P1 chưa commit): **7 file tạo, 10 file sửa, 0 file xóa**.

**File mới (7):**
- `src/main/java/vn/intrachat/domain/ChatGroup.java`
- `src/main/java/vn/intrachat/application/GroupService.java`
- `src/test/java/vn/intrachat/domain/ChatGroupTest.java`
- `src/test/java/vn/intrachat/protocol/WireGroupCodecTest.java`
- `scripts/P2GroupProcessSmoke.java`
- `scripts/verify-p2.ps1`
- `docs/p2-report.md` (báo cáo này).

**File sửa (10):**
- `src/main/java/vn/intrachat/protocol/WireMessage.java`
- `src/main/java/vn/intrachat/application/LiveChatService.java`
- `src/main/java/listener/ChatActionHandler.java`
- `src/main/java/listener/LiveChatAdapter.java`
- `src/main/java/ui/frames/MainChatFrame.java`
- `README.md`, `ROADMAP.md`, `docs/architecture.md`, `docs/feature-matrix.md`, `docs/cleanup-inventory.md`.

API nổi bật: `GroupService.createGroup`, `sendGroupText`, `leave`, `remove`, `groups`, `group`, `history`, `memberName`; `LiveChatService.onGroupFrame`, `sendGroup`; Live adapter triển khai `createGroup`, `leaveGroup`, `removeGroupMember`, `isGroupOwner`, `groupOwnerOnline`. Message types bổ sung vào `WireMessage.Type`: **GROUP_ACCEPT, GROUP_REJECT, GROUP_UPDATE, GROUP_REMOVE, GROUP_ACK**. Tái sử dụng `GROUP_INVITE, GROUP_TEXT, GROUP_LEAVE` đã khai báo trước ở P1; đã gỡ hai enum type dự trù không dùng `GROUP_CREATE/GROUP_JOIN`, không thêm codec/protocol thứ hai.

## C. Kết quả kiểm thử

Môi trường: Windows 11, Temurin JDK **21.0.12.1**, Maven Wrapper offline, real localhost TCP. Chạy:
```powershell
.\mvnw.cmd -o clean test package
powershell.exe -ExecutionPolicy Bypass -File .\scripts\verify-mvp.ps1 -JavaHome 'C:\Users\XuanHoai\.jdks\jdk-21.0.12.1+1'
powershell.exe -ExecutionPolicy Bypass -File .\scripts\verify-p2.ps1 -JavaHome 'C:\Users\XuanHoai\.jdks\jdk-21.0.12.1+1'
git diff --check
```

| Hạng mục | Kết quả | Bằng chứng/giới hạn |
| --- | --- | --- |
| JUnit | **PASS** | **48 tests, 0 failures, 0 errors, 0 skipped**, từ baseline 44 |
| Maven clean test package | **PASS** | Shade executable JAR `target/intrachat-0.1.0-SNAPSHOT.jar` |
| P1 regression | **PASS** | `verify-mvp.ps1`, 2 JVM TCP thực, Unicode, ACK/READ |
| 3 JVM Group TCP | **PASS** | `verify-p2.ps1`, tiến trình A/B/C riêng, 3 dataDirs/UUID khác |
| A → B,C | **PASS** | Cả B và C nhận text và cùng message ID/sender/time |
| B → A,C | **PASS** | Owner relay, giữ sender B; duplicate ID không tạo tin trùng |
| C → A,B | **PASS** | Unicode/emoji, giữ sender C; ACK theo recipient |
| Invite/Reject | **PASS** | B/C accept nhóm thứ nhất; C reject nhóm thứ hai và không vào roster |
| Leave/Remove | **PASS** | B rời nhóm thứ hai; owner loại C nhóm đầu, C không nhận tin sau loại |
| ACK theo recipient | **PASS** | 1/2 trước 2/2; trong ca C offline chỉ 1/2, không báo đủ |
| Member offline | **PASS backend** | C mất TCP khi còn trong nhóm thứ ba, B vẫn nhận, fanout báo partial failure |
| Owner offline | **PASS backend** | B không thể gửi tin Group khi TCP owner đóng |
| 2 nhóm độc lập | **PASS backend** | ID khác nhau, roster và nội dung tách biệt; thêm nhóm offline thứ ba |
| Sender name | **PASS backend** | `GROUP_UPDATE` chuyển displayName của roster; test ba JVM kiểm tra tên của mọi member |
| Group GUI | **NOT RUN** | Windows desktop vẫn có LockScreenContentServer, chưa thể thao tác 3 Messenger thực; không giả PASS |
| LAN vật lý | **NOT RUN** | Không có chứng cứ 2–3 máy vật lý trên LAN |
| JAR cấu trúc | **PASS** | `verify-mvp.ps1` kiểm tra manifest `Main-Class: ui.IntraChat`, không có TLS/security/BouncyCastle/JNA; Group classes được đóng gói qua Maven |
| `git diff --check` | **PASS** | Không còn trailing whitespace do P2 gây ra |

Nguồn log smoke ba JVM cuối: xem output `verify-p2.ps1` và thư mục evidence mà script in ra; mỗi lần chạy tạo thư mục `%TEMP%\intrachat-p2-<UUID>` chứa `A/B/C.out`, `A/B/C.err` và profile riêng. Smoke dùng **một JVM hệ điều hành cho mỗi vai A, B, C**, Java source launcher dùng project executable JAR làm classpath; không dùng MockChatService, không phải test UI. Lượt đầu phát hiện lỗi lost update khi A nhận hai ACCEPT đồng thời; đã sửa bằng tuần tự hóa receive và chạy lại PASS.

## D. Rà soát mã nguồn

- **PASS static/package:** không khôi phục `vn.intrachat.security`, TLS, DPAPI, certificate, signature, fingerprint, Signed Group Grant, GroupV1/V3 codec; không thêm dependency bảo mật hoặc TCP listener mới.
- **PASS source:** adapter Live gọi GroupService thật; Group không dùng MockChatService. Frame GROUP vẫn qua `LanSession` session binding (sender/recipient envelope luôn là hai đầu TCP), author gốc nằm ở `origin`.
- **PASS backend:** ACK owner và member phân biệt, không nhầm ACK A với C; dedup message ID; roster revision đồng bộ khi hai lời mời accept đồng thời, Leave/Remove và owner/member offline; P1 direct vẫn hoạt động.
- **PASS source:** socket send/worker không nằm trên Swing EDT; popup quyết định trên EDT. **GUI responsiveness actual: NOT RUN**.
- **Cảnh báo còn lại:** Maven Shade vẫn phát hiện resource overlaps của Jackson/FlatLaf (`META-INF/LICENSE/NOTICE/MANIFEST`...), không có trùng class ứng dụng được báo. Đây không làm build thất bại.
- **Giới hạn xác thực:** UUID/session checking bảo đảm nhất quán routing ứng dụng, **không bảo vệ chống giả mạo bằng mật mã**. TCP plaintext chỉ dùng LAN thử nghiệm.

## E. Công việc còn lại / điều kiện chuyển P3

1. **GUI ba cửa sổ NOT RUN**: mở khóa desktop, chạy ba `java -Dintrachat.dataDir=...` độc lập, tạo group bằng UI, click Accept/Reject từng người, thử A/B/C, preview và bubble sender/time/emoji, leave/remove, owner offline và responsiveness EDT. Cần quan sát nhãn ACK, không đánh dấu `P2 GUI VERIFIED` khi chưa chạy.
2. **LAN vật lý NOT RUN**: kiểm thử ba thiết bị thật trên mạng nội bộ, Windows Firewall/NAT địa phương, mất Wi-Fi, disconnect/reconnect, không dùng cloud.
3. READ nhóm theo từng thành viên **không hỗ trợ** (không hiển thị All Seen); không sync/persist group/history qua restart. P4 sẽ xử lý RAM → storage. Owner chuyển quyền/migration, File Group, media, TLS/security nằm ngoài P2.
4. Có thể tăng test GUI automation, negative malformed group payload, stress/timeout/đua trạng thái nhanh và tests reconnect vật lý trước khi tuyên bố F02 nghiệm thu đầy đủ.
5. **P3 File Transfer:** backend P1 và GroupService đủ để bắt đầu thiết kế **khi gate chấp nhận kỹ thuật đã thống nhất**, nhưng chưa nên nói P2 nghiệm thu hoàn toàn. P3 tái sử dụng session TCP và membership/owner relay, cần thiết kế chunk streaming, ack/progress và kiểm tra lại giới hạn 1 MiB/frame, không đưa file vào text payload. Không triển khai P3 trong đợt này.

**Trạng thái cuối:** **P2 PARTIAL ACCEPTANCE**, cụ thể **IMPLEMENTED + BACKEND VERIFIED**, **GUI NOT RUN**, **PHYSICAL LAN NOT RUN**. F02 chưa được đánh dấu FULL ACCEPTED.
Backup restore dry-run: PASS — copied pre-P2 LiveChatAdapter.java to temporary file and confirmed matching SHA-256; no repository file was overwritten.


## F. Rà soát chất lượng P2 (23/11/2026, sau khi P3 đã có trong tree)

Đây là **bản cập nhật hậu P2**, không thay số liệu lịch sử 48 JUnit ở đợt P2 đầu. Trước chỉnh sửa, Git vẫn ở `feat/messenger-live-integration` / `ef539c125aa08b96b65774317d032acf636441af`; có thay đổi P0/P1/P2/P3 chưa commit. Đã sao lưu các file dự kiến chỉnh sửa, Git diff (tracked), status, HEAD, branch ra `D:\Documents\Lap_trinh_ung_dung_mang\Tuan08\IntraChat-P2-quality-backup-20261123`, kiểm tra SHA-256 bản sao `GroupService.java`. Không commit, stash, reset, clean hay chỉnh Git history.

- **Timeout lời mời:** `GroupService` mặc định hết hạn sau 30 giây, có overload `Duration` dành cho test. Lời mời chưa phản hồi được hủy qua GROUP_REMOVE; khi không có thành viên Accept, CompletableFuture tạo nhóm thất bại và nhóm chờ bị dỡ. Thành viên đã Accept tiếp tục thuộc nhóm; lời mời quá hạn không được thêm vào roster.
- **ACK thực theo recipient trên Messenger:** `LiveChatAdapter` lưu số recipient đã ACK theo message ID, `ChatClientListener.onGroupReceiptChanged` phát sự kiện và `MainChatFrame` cập nhật từng bubble. Nhãn `Đã nhận 0/2`, `1/2`, `2/2` chỉ lấy từ sự kiện GROUP_ACK; khi gửi không rõ kết quả vẫn giữ nhãn `Chưa rõ · Đã nhận 1/2` thay vì báo cả nhóm đã nhận. Không giả READ nhóm.
- **Test mới:** `GroupInvitationTimeoutTest` kiểm tra TCP localhost thật với một lời mời không có phản hồi, hết hạn và Accept muộn; `LiveDeliveryStateRenderingTest` thêm kiểm tra nhãn ACK 0/2, 1/2, 2/2 và UNCERTAIN. Đây là test mức service/component, **không phải GUI ba cửa sổ**.
- **Tài liệu:** sửa trạng thái F02/F03 cũ trong README; bổ sung giới hạn timeout/receipt vào README, architecture, feature matrix, ROADMAP và cleanup inventory.

**Kết quả hậu chỉnh sửa:** Maven Java 21 `mvnw.cmd -o clean test package` **66/66 JUnit PASS**, 0 fail, 0 error, 0 skipped; executable JAR tạo thành công. **Regression chạy lại sau chỉnh sửa:** `verify-mvp.ps1` **PASS** (hai JVM P1, Unicode, ACK/READ); `verify-p2.ps1` **PASS** (ba JVM A/B/C owner relay, invite/reject/leave/remove, ACK, offline); `verify-p3.ps1` **PASS** (hai JVM truyền file, SHA-256 đúng byte, cancel/reject/disconnect); `git diff --check` **PASS**. F02 tiếp tục là **P2 PARTIAL ACCEPTANCE** vì GUI Messenger thật và LAN vật lý chưa có nghiệm thu. Lưu ý Maven Shade vẫn có resource-overlap warnings, không phải lỗi build. Phần P3 File Transfer chỉ được chạy regression, không thay đổi giao thức file.
