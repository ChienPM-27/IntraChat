# Feature Specification — Giai đoạn 06: Secure LAN File Transfer

**Feature:** `006-file-transfer` · **Branch:** `006-file-transfer`
**Created:** 2026-11-21 · **Priority:** P2 · **Milestone:** M4 = File 006 + Multimedia 007
**Status:** **PARTIAL EXECUTION — 36/160 tasks completed, 27 isolated File JUnit tests PASS and 165/165 full regression PASS; 10/10 CL USER CONFIRMED (9 A, CL-06-03 B). FILE_V1 network/Win11 receiver/Group/physical security Gate 0/Gate 1 BLOCKED; 006/M4 NOT VERIFIED.**
**Source:** [ROADMAP Stage 06](../../ROADMAP.md) · [IntraChat Constitution I–VII](../../.specify/memory/constitution.md) · [M1 LAN](../002-lan-networking/spec.md) · [M2 verified private chat](../003-p2p-messaging/spec.md) · [M2 CHAT_V2 contract](../003-p2p-messaging/contracts/chat-v2.md) · [Group 004](../004-group-chat/spec.md) · [Group V1 contract](../004-group-chat/contracts/group-v1.md) · [History 005](../005-chat-history/spec.md) · [History execution blockers](../005-chat-history/evidence/execution-blockers.md).
**Design:** [Confirmed CL decisions](clarifications.md) · [Implementation plan](plan.md) · [Research](research.md) · [Data model](data-model.md) · [FILE_V1 wire draft](contracts/file-v1.md) · [Quickstart](quickstart.md) · [Requirements checklist](checklists/requirements.md) · [Architecture impact](checklists/architecture-impact-review.md). [160-task RED→GREEN implementation list](tasks.md) has **36/160 completed**; [real Java21 RED→GREEN evidence](evidence/trace-and-tdd.md) and [verified blockers](evidence/execution-blockers.md) are documented. Only isolated domain/read-only source code exists; no File wire/Windows receiving/security signoff.

## 1. Tổng quan, ranh giới và hợp đồng kế thừa

### 1.1 Mục tiêu sản phẩm

Người dùng **IntraChat Live** có thể chủ động chọn **một tệp hiện có**, gửi **trực tiếp qua LAN đến đúng thiết bị P2P đã xác minh**, bên nhận **xem nguồn/tên/kích thước và quyết định nhận**, thấy tiến độ tính theo byte, hủy truyền, và chỉ thấy **COMPLETED** khi dữ liệu nhận thực sự được ghi an toàn, bằng số byte và có hash toàn tệp trùng bản gốc. Không Internet/cloud, không tự gửi tệp nền. Group File theo **CL-06-07 A** bắt buộc owner-mediated cho từng member hợp lệ, **chỉ bật sau 004 runtime thật sự Verified**; không coi Group File đã được nghiệm thu chỉ vì có spec.

**Nguyên tắc:** “Đã gửi chunk vào TLS” ≠ “peer đã ghi đủ” ≠ “tệp an toàn/được người dùng mở”. Tệp được gửi là **nội dung nhị phân tùy ý**, không phải JSON string, không tải toàn bộ vào RAM, không truyền tên/đường dẫn local tùy ý như destination path từ peer. Tính toàn vẹn hash không đảm bảo file không độc hại: nhận ≠ thực thi/mở tự động.

### 1.2 Quyết định kế thừa bắt buộc và trạng thái hiện tại

| Nguồn | Cam kết đã có | Ràng buộc Giai đoạn 06 |
| --- | --- | --- |
| **M1 CL-02-01…04** | Kết nối thủ công IPv4:port, inbound human Accept/Reject, không discovery/auto-reconnect. Raw diagnostic `protocolVersion=1` không xác thực và không mã hóa | **MUST NOT** phát bất kỳ `FILE_*`, tên/tệp/metadata nhạy cảm hoặc chunk qua M1 v1. Không mở listener/port file mới một cách âm thầm |
| **M2 CL-03-01** | Mutual TLS 1.3 + verified deviceId/pinned SPKI và first-pair SAS hai phía; verified `PAIR_READY` | Chỉ xử lý file offer/chunk khi peer TLS đã xác minh, local permission đúng phiên và profile File được xác nhận bảo vệ **bên trong** TLS; biết IP/name/conversationId không đủ |
| **M2 CL-03-02/03/04** | Text/emoji/draft/ACK/READ RAM-only ở M2; ACK/READ có semantics theo người nhận/xem thật, retry thủ công | File transfer **không** được giả `DELIVERY_ACK`/`READ_RECEIPT`; trạng thái file/receiver write/verified finalize tách riêng; không auto outbox/retry |
| **M2 CL-03-05** | Live thật tách Demo mock, single verified peer session registry; v2 codec allowlist nghiêm | `FILE_V1` (nếu chốt) phải dùng **codec và capability riêng**, không chui vào `CHAT_TEXT` v2, không lấy Demo attachment làm nội dung live; chia sẻ session/buffer cap M2 |
| **Group 004 CL-04-01…08** | Current owner relay, max 5 members, max 4 peer sessions, signed original source, JOIN/REMOVE cutoff, handoff signed, không tự owner election | Không phát tệp cho outsider/members trước JOIN; sau REMOVE không khởi động/tiếp tục **chunk mới** theo quyền cũ. Group fanout và từng recipient receipt chỉ được chấp nhận sau verified 004 runtime và CL-06-07 |
| **History 005 CL-05-01…10** | Chỉ lưu nội dung text/emoji opt-in, History receipts không persist/sync; feature 005 hiện partial | File bytes **MUST NOT** được đưa vào History SQLite/delta, không coi file như text, không tự backup/export file qua History; History chưa Verified không được lấy làm resume service |
| **Java / OS** | Java 21, Swing, Win11 x64, Maven. `NetworkConfig` M2 có max 4 active sessions, outbound 16 frames/2 MiB, TLS frame 65,536 bytes | Streaming phải off-EDT; mọi frame/chunk có kiểm tra giới hạn, dùng cửa sổ/queue có backpressure, không phá ưu tiên chat/group |

**Sự thật baseline tại thời điểm viết:** Giai đoạn 05 có **22/136 task** và 16 unit History domain/policy test, **không có verified History wire/DB/Group runtime**. Giai đoạn 04 mới pure Group domain (**18/124 task**) và còn thiếu signed relay/owner handoff. M1/M2 release Gate 1 chưa được nghiệm thu với **hai Windows 11 PC LAN vật lý** và independent security signoff. `model.Attachment` / `ui.components.MediaFiles` hiện là mô hình/tiện ích FE media local, **không phải một file sender/receiver TLS đã được nghiệm thu**. Vì vậy Giai đoạn 06 **DRAFT/NOT VERIFIED**, không thể suy ra File transfer khả dụng từ nút đính kèm Demo.

### 1.3 Trong phạm vi

1. **P2P 1→1**: chọn tệp local bằng thao tác chủ động, validate regular file/size/name/availability; tạo offer với transferId riêng, gửi đến đúng conversation giữa hai verified device IDs.
2. **Receive consent và safe destination (CL-06-02 A + CL-06-03 B)**: người dùng cho phép Downloads root trước (có thể đổi/thu hồi), vẫn Accept/Reject **từng file**, xem verified sender/basename/size; peer không bao giờ chọn pathname, không ghi đè file.
3. **Binary streaming bounded** qua authenticated file profile: framed control + các chunk bounded, sequence/offset, backpressure, ACK/progress, resource fairness, không nạp whole file vào heap.
4. **Byte-perfect integrity**: kiểm tổng bytes và SHA-256 toàn tệp trên nội dung nhận từ offset 0 đến end, chỉ complete sau durable write/close + verified digest + an toàn khi commit path; handshake final receiver outcome phân biệt sender progress.
5. **Fault/cancel/cleanup**: disconnect, EOF nửa chừng, timeout, disk full, duplicate/replay/late chunks, local cancellation/receiver Reject; tệp chưa hoàn chỉnh không được hiện như final.
6. **Live UI và test**: Send File / Offer / Accept / Reject / Cancel, byte progress, lỗi & trạng thái thật, isolate Demo; Java21 test-first with two peers/malformed/large/zero length/symlink/traversal/corruption/cancel.
7. **Group File (CL-06-07 A, gated 004)**: current owner thực hiện fanout per-recipient, mỗi member Accept/Reject, Partial Success/Failure minh bạch, signed provenance/JOIN/REMOVE/handoff; không bật khi 004 runtime chưa Verified.
8. **Giao diện tái sử dụng cho 007 Multimedia**: cơ chế an toàn cho binary được dùng lại; việc preview/giải mã/phát/codec Image/Video/Sound thuộc 007, **không nằm trong 006**.

### 1.4 Ngoài phạm vi

- Cloud drive, Internet upload/public link, NAT traversal, auto peer discovery, server đồng bộ, upload tự động, auto-reconnect, offline store-and-forward cho thiết bị chưa nhận.
- Streaming voice/video call, screen sharing, browser file download, remote shared folder/SMB, tự động mở/thực thi tệp, chạy script/plugin/installer, giải nén archive sau tải, quét malware được bảo đảm “an toàn tuyệt đối”.
- Đồng bộ binary attachments bằng History 005, remote wipe, account đa thiết bị, group member mesh ngầm, tự bầu owner, gửi tệp cho Group member đã REMOVE hay chưa JOIN.
- Chia sẻ thư mục/multiple files hàng loạt, preview video/audio/ảnh trong chat; **không hỗ trợ resume** sau cancel/mất mạng/restart theo CL-06-05 A; retry thủ công tạo transferId mới.
- Áp dụng `Attachment.sizeBytes > 0` của Demo làm giới hạn mạng: **CL-06-01 A xác nhận file 0 byte hợp lệ**, file >256 MiB bị từ chối; không tự sửa logic Demo để giả vờ đã có File runtime.

## 2. Clarifications — **10/10 USER CONFIRMED: 9 A + CL-06-03 B**

Người dùng đã xác nhận chính thức cả 10 quyết định ngày 2026-11-21. Đây là **ràng buộc sản phẩm**, không phải gợi ý. Chi tiết và tác động nằm trong [decision register](clarifications.md); bản [plan](plan.md) đã đồng bộ. Phê duyệt sản phẩm **không** thay thế review kỹ thuật bảo mật/giao thức/Windows filesystem.

| ID | Quyết định người dùng | Hệ quả bắt buộc |
| --- | --- | --- |
| **CL-06-01** | **A — ≤256 MiB (268435456 bytes); file rỗng 0 byte được phép** | SHA-256 empty, giới hạn inclusive, không dùng `Attachment.sizeBytes > 0` của Demo |
| **CL-06-02** | **A — Accept/Reject từng file, kể cả peer đã pinned** | Không có chunk trước Accept, timeout Reject; khác inbound connection consent |
| **CL-06-03** | **B — Dùng thư mục Downloads đã được user cấp quyền trước** | Có thể chọn/đổi/thu hồi Downloads root; không hỏi Save As mỗi file, **vẫn phải Accept/Reject từng offer** |
| **CL-06-04** | **A — Không overwrite; đặt tên khác hoặc yêu cầu chọn tên** | Atomic no-clobber và chống collision/junction/race; tuyệt đối không ghi đè tự động |
| **CL-06-05** | **A — Xóa partial khi hủy/lỗi, không resume, retry tạo transferId mới** | Sau crash dọn an toàn chỉ các file staging do app tạo; không xóa user files |
| **CL-06-06** | **A — Source thay đổi thì fail/cancel** | Kiểm source identity/size/mtime và digest trước/sau streaming; không snapshot |
| **CL-06-07** | **A — Owner-mediated Group per-recipient sau Group 004 Verified** | Signed origin/owner proof, JOIN/REMOVE, member states riêng, Group route disabled trước Gate 004 |
| **CL-06-08** | **A — FILE_V1 riêng trên TLS M2 hiện có** | Post-TLS mutual capability + strict codec, cùng session quotas, không mở socket mới |
| **CL-06-09** | **A — Trạng thái transfer RAM-only, không lưu archive** | Chỉ final file user saved và staging transient; không sync bytes/metadata vào History 005 |
| **CL-06-10** | **A — File binary có cảnh báo loại không tin cậy; không auto-open/execute** | User mở thủ công qua OS; không hứa malware-free |

**Technical Gate 0 vẫn OPEN/BLOCKED:** FILE_V1 exact profile/golden vectors, Win11 Downloads root/atomic no-clobber/reparse review, M2 shared quota benchmark, signed Group 004 runtime và independent security/privacy review. Tất cả AC-06-A…P vẫn **NOT RUN**.

## 3. User Scenarios & Testing (mandatory)

### US1 — Người gửi chọn tệp và tạo P2P offer hợp lệ (P1)

**As** người dùng A đang ở phiên Live A↔B đã verified, **I want** chọn một tệp từ máy mình và gửi đề nghị rõ tên/kích thước, **so that** B quyết định có nhận mà không có dữ liệu tệp tự chảy ra ngoài khi B chưa đồng ý.

**Independent test:** hai peer M2 giả lập verified, chọn file Unicode hợp lệ, gửi một FILE_OFFER, B chưa Accept thì payload 0 byte; nguồn không có/quá limit bị chặn trước khi gửi.

1. **Given** A chưa verified M2 READY hoặc chỉ kết nối M1, **When** nhấn Send File, **Then** UI từ chối gửi và không phát metadata/chunk nhạy cảm.
2. **Given** A verified B nhưng B chưa Accept, **When** A chọn tệp, **Then** B chỉ nhận metadata bounded trong protected channel, chưa có chunk.
3. **Given** tên tệp Unicode, emoji, dấu chấm hoặc đường dẫn local dài, **When** tạo offer, **Then** chỉ truyền basename hiển thị đã validate, không truyền source absolute path, key, tài khoản Windows.
4. **Given** tệp nguồn mất quyền đọc, là thư mục/symlink ngoài chính sách, hoặc vượt file cap, **When** gửi, **Then** chuyển REJECTED/FAILED rõ ràng và không cấp stream.

### US2 — Người nhận chấp thuận, từ chối và kiểm soát vị trí lưu (P1)

**As** B, **I want** tự cấp/thu hồi quyền sử dụng Downloads trong cài đặt và vẫn Accept/Reject từng file từ A đã xác minh, **so that** A không thể ép lưu hay ghi đè file trên máy tôi.

**Independent test:** B đã ủy quyền Downloads nhưng Reject → 0 chunk; chưa ủy quyền Downloads thì Accept bị chặn; Accept từng file → chỉ tạo staging an toàn trong Downloads; traversal/junction/symlink/collision fail closed.

1. **Given** offer hợp lệ từ verified A, **When** B chưa chọn Accept, **Then** transfer ở OFFERED/PENDING và không tạo final filename.
2. **Given** B Reject hoặc quá timeout chờ consent, **When** A tiếp tục gửi chunk, **Then** receiver từ chối và không ghi disk.
3. **Given** Downloads root chưa được cấp quyền, đã bị thu hồi, hoặc là unsafe symlink/junction/reparse, **When** B thử Accept, **Then** fail closed, không tạo staging hoặc sửa file cũ.
4. **Given** Downloads có tệp cùng tên, **When** B Accept, **Then** tạo tên collision-safe mới hoặc yêu cầu user chọn tên khác, **atomic no-clobber** (CL-06-04 A).

### US3 — Truyền tệp theo luồng với tiến độ thật và UI không treo (P1)

**As** A/B, **I want** thấy số byte đã gửi/nhận và tốc độ/trạng thái theo transfer, **so that** biết truyền đang chạy hay chậm mà vẫn chat bình thường.

**Independent test:** file synthetic lớn hơn nhiều lần bounded buffer, 2 JVMs LAN loopback M2 TLS READY, verify memory max, queue fairness, EDT responsive và counters riêng sender/receiver.

1. **Given** file lớn hơn một chunk, **When** gửi, **Then** chỉ từng chunk bounded được nạp/ghi theo offset, không `readAllBytes` nguyên tệp.
2. **Given** receiver chậm/đĩa chậm, **When** queue đầy, **Then** backpressure hoặc PAUSED không làm tăng RAM vô hạn, không chiếm hết shared M2 sessions.
3. **Given** đang truyền, **When** user tương tác Live P2P/chat, **Then** Swing EDT không block vì file I/O/hash/socket.
4. **Given** A đã ghi chunk vào TLS nhưng B chưa durable write, **When** hiển thị progress, **Then** phân biệt `sentBytes` và `receiverAcceptedBytes`; không gắn hoàn tất cho cả hai.

### US4 — Nhận nguyên vẹn, finalize atomically và mở file có kiểm soát (P1)

**As** B, **I want** file sau khi lưu giống hệt nguồn, **so that** không nhầm tệp hỏng với tệp hợp lệ.

**Independent test:** nhận tệp synthetic bất kỳ bytes, 0/1/N bytes theo policy, verify SHA-256+exact length và atomic finalization; chỉnh một chunk hoặc cắt đuôi làm FAILED và không lộ final file sai nội dung.

1. **Given** đủ mọi chunk đúng offset, **When** B kiểm đủ `sizeBytes` và SHA-256 khớp manifest, **Then** B đóng/fsync staging, chuyển sang final target an toàn không overwrite, gửi terminal verified outcome.
2. **Given** nhận đúng số byte nhưng checksum sai, **When** finalize, **Then** FAILED_INTEGRITY, tệp không được hiện là COMPLETED hoặc tự mở.
3. **Given** A đóng socket ngay sau gửi EOF, **When** B chưa xác nhận durable finalize, **Then** A hiển thị UNCERTAIN/PARTIAL, không SUCCESS.
4. **Given** file định dạng/extension có khả năng thực thi, **When** tải xong, **Then** không tự mở/chạy; warning policy phải áp dụng trước khi thao tác Open thủ công.

### US5 — Hủy, mất mạng, timeout, restart và dọn tệp tạm (P1)

**As** A/B, **I want** hủy hoặc xử lý mất kết nối an toàn, **so that** không mất tệp cũ, không để lại tệp hoàn chỉnh giả hoặc retry ngầm.

**Independent test:** cancel từ mỗi phía sau 0/1/N chunks, half-close, timeout, disk full và app crash đúng commit boundary; inspect staging/actual final file và trạng thái sau restart.

1. **Given** transfer IN_PROGRESS, **When** A hoặc B nhấn Cancel, **Then** gửi FILE_CANCEL nếu phiên còn sống, đóng resources và hiển thị CANCELLED thật.
2. **Given** B hết dung lượng hoặc mất quyền ghi, **When** write chunk lỗi, **Then** FAILED/ERROR và không finalize, sender không nhận fake COMPLETE.
3. **Given** network đứt hoặc app bị kill, **When** mở lại, **Then** final file cũ vẫn nguyên vẹn; partial được xử lý theo CL-06-05/09 và không tự resume qua socket mới.
4. **Given** duplicate/late CANCEL/FINAL_ACK, **When** nhận, **Then** state machine idempotent, không biến CANCELLED/FAILED thành COMPLETED.

### US6 — Chặn tấn công đường dẫn, metadata và payload từ LAN (P1)

**As** người nhận, **I want** tệp/metadata do peer gửi được xem là untrusted, **so that** chúng không ghi đè/đọc trộm/đặt file vào vị trí khác hoặc gây OOM.

**Independent test:** fuzz filename with `../`, `..\`, absolute path, ADS `:`, NUL, reserved Windows devices, trailing dot/space, mixed Unicode, invalid UTF-8, oversized integer/hash/chunk, directory junction/symlink, permission/collision races.

1. **Given** malicious filename `..\..\Windows\System32\...`, **When** nhận offer, **Then** không dùng path này làm filesystem destination; reject/sanitize and leave approved root unchanged.
2. **Given** symlink/junction/reparse-point swap xảy ra giữa Accept và finalize, **When** ghi/rename, **Then** app không thoát target root và không đè tệp có sẵn.
3. **Given** malicious chunk offset sai, size âm/overflow, overlap hoặc payload > frame limit, **When** giải mã, **Then** reject before allocation/disk write và báo lỗi sanitized.
4. **Given** peer gửi tệp executable/đa extension giả MIME, **When** nhận, **Then** không auto-open/execute, không tin MIME claim để cấp quyền.

### US7 — Gửi tệp trong Group theo từng thành viên hợp lệ (P2 — **CONDITIONAL on 004**)

**As** một người trong Group, **I want** biết rõ ai nhận được tệp và ai từ chối/offline, **so that** trạng thái nhóm không nói sai “mọi người đã nhận”.

**Independent test:** sau khi 004 runtime VERIFIED theo CL-06-07 A, ≥3 JVM owner A, member B/C và member D JOIN muộn, thử per-recipient offer/accept/failure, REMOVE giữa transfer, owner handoff và Group owner offline.

1. **Given** B gửi Group file, **When** offer chạy qua owner A, **Then** C xác minh B gốc độc lập; hop TLS A không đủ để tự nhận là B.
2. **Given** C Accept, D Reject, E offline, **When** fanout, **Then** UI có recipient outcomes độc lập, không có universal Group COMPLETED/READ.
3. **Given** D JOIN sau khi offer/event được owner commit hoặc C bị REMOVE, **When** đến delta/chunk mới, **Then** không phát nội dung vi phạm mốc quyền 004.
4. **Given** owner A handoff sang B, **When** chưa có signed authority continuity hoặc A offline, **Then** Group transfer PAUSED/blocked; không tự member-mesh/fallback M1.

### US8 — Tích hợp Live và nền truyền binary cho Multimedia 007 (P2)

**As** người dùng, **I want** thông tin File transfer nằm đúng hội thoại, sender/time và trạng thái thật, **so that** có thể dùng cùng nền này cho Image/Video/Sound sau này nhưng không bị nhầm với Demo.

**Independent test:** cùng lúc A↔B Live file, Group mock/Demo, P2P text/emoji, History view; verify file item không xuất hiện ở hội thoại khác, không tạo text READ/ACK, không ghi binary vào History, correct EDT.

1. **Given** user đang ở Demo, **When** click mock attachment, **Then** không tạo Live TLS/file-transfer side effect.
2. **Given** người dùng chuyển conversation giữa lúc nhận, **When** progress cập nhật, **Then** event keyed by transferId+verified scope và không gán sang active chat khác.
3. **Given** file transfer complete, **When** UI render item, **Then** hiển thị verified sender/source time và nơi lưu, nhưng không biến nó thành viewer-signed READ receipt M2.
4. **Given** feature 007 chưa triển khai, **When** người dùng nhận video/audio/image thông qua File, **Then** hiển thị là generic file và chỉ cung cấp Open thủ công theo chính sách; không hứa in-app decode/call.

### 3.1 Edge cases bắt buộc

- Tệp 0 byte, 1 byte, đúng giới hạn, vượt 1 byte; size int64 overflow, sparse file, số chunk quá giới hạn, quota/disk preflight, file nguồn bị xóa/truncate/modify.
- Unicode NFC/NFD, emoji/surrogate, tên rất dài, tên giống nhau khác chữ hoa/thường Windows, reserved filenames (`CON`, `NUL`, `COM1`), colon/ADS, slash/backslash, UNC, drive-letter, `\\?\` extended paths, trailing dot/space, hidden file, reparse points, file stream ngoài user chọn.
- Thư mục đích read-only, file đang mở/locked, duplicate Accept, Reject after Accept, late/duplicate/out-of-order chunks, conflict transferId, bad digest, EOF early, network half-open, idle timeout.
- Concurrency 2/4 sessions, queued frames/backpressure, multiple file offers, cancellation races, Group owner handoff/revoke while frames in queue; **negative ACL before disclosing file name/size** to outsider.
- Terminal evidence ordering: sender can reach 100% local send, receiver can be still VERIFYING/COMMITTING; no fake COMPLETED, APP_ACK, READ, auto-open.
- Crash before/after staging fsync/atomic no-clobber move; partial deletion/TTL policy; no newly created file outside approved directory and no silent overwrite.
- New File profile vs legacy v1/v2, draft Group v3/History v4, unknown critical feature, strip/corrupt capability, wrong SPKI, key rotation and replay across TLS sessions.

## 4. Functional Requirements (MUST / MUST NOT)

### A. Authenticated transport, scope và versioning

- **FR-06-001:** File metadata/control/chunk **MUST NOT** truyền bằng M1 raw plaintext diagnostics `protocolVersion=1`, `CHAT_TEXT` M2 v2, mock Demo, cloud relay hoặc HTTP/file URL đến Internet.
- **FR-06-002:** Mọi file activity **MUST** yêu cầu human-approved M2 TLS 1.3, exact verified deviceId/pinned SPKI, hai `PAIR_READY` của active session. Có pin từ phiên cũ hoặc biết IP/conversationId **MUST NOT** tự cấp quyền.
- **FR-06-003:** File app protocol **MUST** được version/capability-negotiated riêng và mutual confirmed **trong verified TLS**, binding raw capability offers, requestId/sessionId, SPKI, nonces; pre-TLS untrusted token **MUST NOT** tự cho phép file.
- **FR-06-004:** P2P File transfer **MUST** authorize đúng derived P2P conversation ID và hai identities; sender/recipient của mọi control/data frame phải khớp actual TLS hop, transferId và active session, trước khi tiết lộ metadata.
- **FR-06-005:** Unknown file profile/version/critical field, mismatch capability hoặc phiên `PAIR_READY` chưa đạt **MUST** reject bounded và **MUST NOT** downgrade sang M1 hoặc tự mở socket.
- **FR-06-006:** File protocol **MUST** có typed allowlist riêng với transferId, messageId/frame ID, direction, sender device ID, recipient, scope ID, UTC event timestamp, state/proof và payload bounded; không deserialization Java native.
- **FR-06-007:** Tất cả mạng peer input **MUST** được xem là untrusted: strict UTF-8 cho metadata, no duplicate/trailing JSON, bounded nesting/string/array, nonzero UUID, int64 range/overflow, frame length trước allocate, version/capability gating, không chấp nhận unknown type.
- **FR-06-008:** File-control ACK/COMMIT **MUST NOT** trở thành `DELIVERY_ACK`/`READ_RECEIPT` của tin nhắn M2/Group; transfer outcomes **MUST** độc lập với viewing/reading chat.
- **FR-06-009:** File share **MUST** chạy trên phiên M2 TLS đã verified theo CL-06-08 A, chia sẻ giới hạn session/quota/fairness; **MUST NOT** mở thêm socket/file listener.

### B. File offer, user consent và safe destination

- **FR-06-010:** Mỗi transfer **MUST** có transferId ngẫu nhiên không trùng + correlation với đúng verified conversation/group run và local send attempt; message IDs của control/data tách khỏi ID hội thoại/tin nhắn.
- **FR-06-011:** Sender **MUST** chọn tệp qua thao tác explicit; phải kiểm nguồn là regular file có thể đọc theo chính sách symlink/reparse, size hợp lệ, quota, khả dụng, trước Offer. Không quét folder/upload tùy ý.
- **FR-06-012:** Metadata gửi **MUST** là basename hiển thị đã validate, fileLength int64 không âm và nằm trong policy, optional MIME chỉ là claim, whole-file digest/proof scheme được định nghĩa ở contract; **MUST NOT** lộ full source path, Windows username, key hoặc secret.
- **FR-06-013:** System **MUST** áp dụng **0 ≤ sizeBytes ≤ 268435456 (256 MiB)** inclusive (CL-06-01 A), kiểm 0/1/max/max+1, disk quota và int64 overflow; 0 byte hợp lệ với SHA-256 empty; không dùng `Attachment` Demo limit >0.
- **FR-06-014:** Receiving offer **MUST** present independently verified peer identity, sanitized filename/size, warning about executable/untrusted data and exact local destination implications; nhận file không được tự xảy ra vì peer pinned.
- **FR-06-015:** FILE_ACCEPT/FILE_REJECT/FILE_CANCEL **MUST** gắn với một offer chưa hết hạn và đúng authorized session/scope; trước explicit Accept không được gửi hoặc ghi content chunks.
- **FR-06-016:** Receiver **MUST** dùng **Downloads root đã được user cấp quyền trước** (CL-06-03 B, có thể đổi/thu hồi), **không** hỏi Save As cho từng file, nhưng **MUST** Accept/Reject từng offer theo CL-06-02 A. Không gửi FILE_ACCEPT nếu root thiếu/không an toàn; peer-supplied path **MUST NOT** chọn filesystem path.
- **FR-06-017:** Receiver **MUST** check filename for traversal, absolute/UNC/drive/device paths, Windows reserved names, ADS colon, trailing dot/space, NUL/control, hostile Unicode, oversized name; validate after normalization, not merely replace `../`.
- **FR-06-018:** Overwrite/collision **MUST NOT** xảy ra (CL-06-04 A); tạo tên collision-safe mới hoặc hỏi user tên khác bên trong Downloads; atomic no-clobber/fail closed dưới race, **MUST NOT** thay file cũ.
- **FR-06-019:** Metadata name/MIME/extension **MUST NOT** làm app auto-execute/open/unzip; CL-06-10 A cho phép binary tùy ý với cảnh báo type/MIME không đáng tin, user chỉ mở thủ công qua OS; không tuyên bố malware-free.

### C. Bounded streaming, scheduling và progress

- **FR-06-020:** Sender/receiver **MUST** đọc/ghi tệp theo stream/chunk với fixed bounded buffers; **MUST NOT** load whole file vào heap, Base64 whole-file vào JSON, serialize Java objects hoặc tích lũy chunk không giới hạn.
- **FR-06-021:** Mỗi chunk **MUST** mang transferId/session/scope-bound sequence và absolute 64-bit offset, explicit valid data length; receiver **MUST** reject negative, overflow, overlap, skip/out-of-order ngoài protocol, repeated altered chunk, stale run, chunk after cancel/complete.
- **FR-06-022:** Từng wire frame **MUST** nằm trong M2 TLS max **65,536 bytes** *bao gồm* envelope, integrity metadata và chunk bytes; control payload và decoded byte buffers đều có hard cap.
- **FR-06-023:** Writer/reader **MUST** áp dụng bounded queue, shared peer/session quotas, fair scheduling and backpressure; file bulk không được làm starvation verified chat/Group control hoặc OOM.
- **FR-06-024:** Mọi file read/write, checksum, fsync, network I/O, compression nếu có **MUST NOT** chạy trên Swing EDT; UI updates được coalesce/throttle, worker cancellation và cleanup phải deterministic.
- **FR-06-025:** Sender **MUST NOT** phát content chunk trước authorized Offer+receiver Accept được kiểm chứng cùng TLS session; một receiver Reject/timeout/cancel phải chặn chunk tiếp theo.
- **FR-06-026:** Transfer service **MUST** có terminal/nonterminal state machines rõ cho sender và receiver: `SELECTING / OFFERING / AWAITING_ACCEPT / QUEUED / TRANSFERRING / VERIFYING / COMMITTING / COMPLETED / REJECTED / CANCELLED / FAILED / UNCERTAIN` (hoặc tương đương) và transition hợp lệ/replay-safe.
- **FR-06-027:** Progress UI **MUST** nêu `bytesSent`, `bytesReceived/accepted`, total bytes và stage đang thực hiện, không ép tỉ lệ 100%=COMPLETED; số byte không thể giảm sai hoặc vượt declared total.
- **FR-06-028:** Chunk receive ACK/window updates **MUST** chứng minh mức byte thực sự receiver đã xử lý theo protocol; mọi terminal receipt được correlate với sender/transferId/size/digest và không cho peer phát ACK thay thiết bị khác.
- **FR-06-029:** File transfer concurrency, queue/connection budgets, idle/overall timeouts **MUST** được chốt tại plan sau CL-06 và đo trên Windows Java21; không tự tạo unlimited worker/transfer queue hoặc phá M2 current active-session cap.

### D. Integrity, durable receiving và truthful completion

- **FR-06-030:** Sender/receiver **MUST** dùng **SHA-256 toàn file** và exact byte count: sender prehash trước Offer, rehash emitted bytes khi stream, kiểm source identity/size/mtime không đổi (CL-06-06 A), receiver hash staging độc lập; **không tạo source snapshot**. TLS integrity không thay end-to-end digest.
- **FR-06-031:** Receiver **MUST** kiểm offset/chunk order, exact final byte count và full SHA-256 trước COMMIT; mismatch/replay/extra/missing data **MUST** FAILED_INTEGRITY, không rename staging thành final.
- **FR-06-032:** Receiver **MUST** ghi vào unique staging file dưới đường đích an toàn (hoặc staging layout được review), flush/fsync + close và finalize **atomic no-clobber** khi filesystem hỗ trợ; khi không thể atomically finalize an toàn, **MUST** dừng/báo lỗi, không ghi đè file cũ.
- **FR-06-033:** `COMPLETED` ở receiver **MUST** chỉ sau validated digest+size và durable, safe final target creation; sender `COMPLETED` **MUST** đợi terminal receiver confirmation gắn đúng id/scope/digest/session.
- **FR-06-034:** TCP/TLS write success, sender `bytesSent=total`, chunk ACK tạm, hoặc peer disconnect ngay sau EOF **MUST NOT** làm sender báo recipient-complete; khi kết quả không rõ dùng `UNCERTAIN/PARTIAL` và explanation.
- **FR-06-035:** Wrong digest, source changes, disk full, permission denial, staging rename/finalization failure **MUST** có error code/state sanitized, không để user nhầm file partial với file final.
- **FR-06-036:** UI **MUST NOT** tự open/preview/execute tệp hoàn thành hoặc tải MIME/codec tự động; mở thủ công phải chịu chính sách CL-06-10/Stage 007, không tuyên bố malware scan nếu chưa có thực nghiệm.

### E. Cancel, disconnect, restart và partial-file lifecycle

- **FR-06-037:** Sender và receiver **MUST** cho explicit Cancel khi còn nonterminal; cancellation được propagate qua protected `FILE_CANCEL` nếu link còn sống, trạng thái terminal idempotent; close stream/worker và không tự tiếp tục.
- **FR-06-038:** Disconnect/half-open/truncation/restart giữa transfer **MUST** không hiển thị completed; cleanup state chính xác, no automatic retransmit or reconnect, v1 plaintext fallback bị cấm.
- **FR-06-039:** Timeout Offer, idle chunk, stalled disk, final confirmation và overall transfer **MUST** bounded, cancellation-aware; user biết lỗi/hủy và có thể chủ động thử lại.
- **FR-06-040:** Staging/partial chỉ được tạo dưới Downloads đã ủy quyền sau Accept và **MUST** xóa sau cancel/fail (CL-06-05 A); sau crash, startup chỉ dọn orphan **được xác minh do app tạo** mà không xóa user file; **MUST NOT** giữ để resume.
- **FR-06-041:** **Không hỗ trợ resume** (CL-06-05 A); retry **MUST** do user chủ động, tạo transferId/offer mới và receiver lại Accept; **MUST NOT** lưu resume offset, auto-connect, auto-retry hoặc tin offset peer tự khai.
- **FR-06-042:** Sender **MUST** phát hiện source sửa/truncate giữa prehash Offer→EOF bằng identity/size/mtime và SHA-256, **fail/cancel** (CL-06-06 A), không tạo source snapshot, không mixed-version success.
- **FR-06-043:** Transitions nhận duplicate, stale control, cancel after complete và new session replay **MUST** được xử lý idempotent/fail-closed; không biến `CANCELLED/FAILED` thành `COMPLETED` từ một ACK trễ.

### F. Filesystem/privacy and negative security boundaries

- **FR-06-044:** Safe destination path **MUST** dùng filesystem API, kiểm normalized/canonical approved root, owner permissions, symlink/junction/reparse point trên từng critical open/move, chống TOCTOU bằng secure handle/no-follow/no-clobber theo khả năng Windows; chỉ lexical `startsWith` **không đủ**.
- **FR-06-045:** Transfer code **MUST NOT** ghi đến path do mạng cung cấp, path bên ngoài user-approved root, thư mục system/startup hoặc file đã tồn tại không được phê duyệt; không cho directory traversal dù dùng slash/Unicode/ADS.
- **FR-06-046:** Logs/crash dumps/Git evidence **MUST NOT** ghi binary file bytes, private full paths, secret keys/SAS, unsanitized remote metadata; chỉ mã lỗi/counters an toàn; không tạo bản copy plaintext không được phép.
- **FR-06-047:** File content/hash/name/offer/receipt/transfer metadata **MUST NOT** persist/sync trong History 005/Group 004/Demo; CL-06-09 A yêu cầu **RAM-only attempt**. Chỉ completed file và (nếu bảo vệ an toàn) local Downloads root preference tồn tại qua restart; staging orphan cleanup.
- **FR-06-048:** Abuse test **MUST** gồm malformed UTF-8, duplicate/trailing JSON, path traversal/junction/collision, integer overflow, empty/max/oversize file, corrupt checksum, slow read/EOF, forged control/ID/peer, cancellation races, repeat/reorder chunks và OS ACL issues.

### G. Group integration, Live isolation và hand-off cho 007

- **FR-06-049:** Group File **MUST NOT** được bật khi thiếu Group 004 real verified owner/session/roster/signed original-sender provenance; owner TLS hop **MUST NOT** tự đại diện là original member sender.
- **FR-06-050:** Khi Group được duyệt, per-recipient Offer/Accept/chunk/verify/outcome **MUST** dùng current owner authority và verified member rights, có bounded fanout/quota và trạng thái **per recipient**: accepted/declined/offline/failed/completed.
- **FR-06-051:** Member mới **MUST NOT** nhận Group file transfer được owner commit trước JOIN eligibility; sau durable REMOVE commit **MUST NOT** phát **new** file chunk/offer cho member bị loại, kể cả chunk đang chờ queue.
- **FR-06-052:** Owner handoff **MUST** kiểm signed continuity/epoch/revision; owner cũ mất quyền, owner offline/ambiguous → PAUSED. Không auto owner election, member-to-member mesh hay remote wipe bytes đã gửi.
- **FR-06-053:** Live UI **MUST** gắn mọi item/progress/status vào exact transferId + verified P2P conversation ID hoặc Group ID/epoch, hiển thị sender/time/status đúng, không đưa file vào thread đang active chỉ vì UI đổi màn hình.
- **FR-06-054:** Demo/mock attachment **MUST** tách khỏi Live transfer: click Demo không mở network/socket; receiver file completion không tự phát M2 CHAT_TEXT/read receipt hay Group per-recipient READ.
- **FR-06-055:** Stage 007 Image/Video/Sound **SHOULD** sử dụng cùng secure binary transfer API, nhưng 006 **MUST NOT** hứa preview/codec/real-time media hoặc tự mở file trước spec và test 007.
- **FR-06-056:** Feature/release UI **MUST** báo rõ khi P2P File chưa Verified hoặc Group File bị disable do 004; không mô tả Demo hiện tại hoặc pure-domain mock như đã gửi file LAN thực.

## 5. Measurable Success Criteria

Đây là **mục tiêu nghiệm thu tương lai, không phải kết quả test**. Mỗi tiêu chí cần command/log, máy/JDK/commit, synthetic bytes, actual PASS/FAIL/BLOCKED; không suy ra PASS từ spec.

- **SC-06-001:** **100%** file được receiver báo `COMPLETED` trong test synthetic có **đúng size và SHA-256 toàn nội dung** như nguồn bất biến đã prehash và kiểm lại trong lúc stream, kể cả UTF-8 filename.
- **SC-06-002:** **0** tệp failed/truncated/mismatched hash/partial được đặt tên hoặc hiển thị như final COMPLETED; receiver file cũ vẫn nguyên vẹn.
- **SC-06-003:** **0** chunk nội dung được ghi trước khi receiver Accept đúng offer + verified pair/session; explicit Reject/timeout không sinh final file.
- **SC-06-004:** **0** path traversal/absolute/UNC/ADS/reparse/junction/symlink swap/filename collision thành công ghi ngoài approved root hoặc overwrite ngoài chính sách.
- **SC-06-005:** **0** file payload/control qua M1 plaintext, M2 CHAT_TEXT v2 hoặc unknown/unconfirmed File capability; verified outsider được **0** private file name/size/content metadata.
- **SC-06-006:** Truyền file synthetic nhiều lần lớn hơn bounded buffer mà **không** tải toàn bộ vào RAM hoặc treo Swing EDT; đo queue/heap/latency trên Windows 11/Java21 thực.
- **SC-06-007:** Sender và receiver phân biệt **bytes sent / accepted / verified / committed**; **0** false completed sau TCP write, early EOF, lost final ACK hoặc cancel.
- **SC-06-008:** **100%** test explicit cancel, idle timeout, failed disk write và abrupt crash có terminal/fault state đúng; partial cleanup/retain theo CL-06-05/09 thực tế, không auto-reconnect.
- **SC-06-009:** Hash tamper, chunk reorder, overlapping offset, negative/overflow sizes, duplicate transferId, stale session và invalid Unicode đều fail closed, không crash/corrupt chat khác.
- **SC-06-010:** Live file UX gắn đúng sender/time/recipient; **0** mock Demo/network crossover và **0** file bytes xuất hiện ở History store/sync mặc định.
- **SC-06-011:** Group File tests (**CL-06-07 A đã xác nhận, chỉ sau 004 Verified**): **0** pre-JOIN content/new post-REMOVE chunk; per-recipient partial result, no forged original member signer; stale owner PAUSED.
- **SC-06-012:** M2/Group/History regressions không đổi semantics receipt; **0** fake `DELIVERY_ACK`/`READ_RECEIPT` do file progress hoặc receiver saved file.
- **SC-06-013:** Acceptance tối thiểu có **2 actual peer/processes** trên LAN/loopback cho logic; **Gate 1** còn yêu cầu **≥2 physical Win11 x64 LAN PCs** không cần Internet, manual inbound Accept/SAS, security review.
- **SC-06-014:** **M4 chỉ Verified khi File 006 và Multimedia 007** hoàn tất acceptance/independent security gates; 006 P2P PASS riêng không có nghĩa M4 PASS hoặc Group File đã chạy.

## 6. Acceptance Criteria — Stage 06 (A–P; **ALL NOT RUN**)

Các AC-06-A…D giữ nguyên ý định ROADMAP; E…P mở rộng để xác minh đầy đủ quyền riêng tư, atomicity, Group, Live và resource. **Không AC nào được đánh dấu PASS tại thời điểm viết spec.**

| ID | Test / Given → When | Expected Result / Then |
| --- | --- | --- |
| **AC-06-A** | Hai verified P2P LAN A/B, A chọn tệp bytes synthetic (0/1/N theo CL), B Accept, đủ chunks và finalize | B nhận **cùng số byte + SHA-256**, final file mới đúng vị trí; sender chỉ COMPLETED sau receiver outcome đã xác minh |
| **AC-06-B** | Streaming file > nhiều chunks, UI A/B đang chạy; Cancel ở sender và receiver tại 0/1/N chunks | Tiến độ bytes rõ, responsive EDT, cả hai xử lý CANCELLED thật, không có fake final |
| **AC-06-C** | Half-open/TLS disconnect/peer kill giữa file, mất final ACK hoặc B đĩa full | Sender không ghi success khi B chưa confirmed; final file cũ không bị hỏng, partial theo approved policy |
| **AC-06-D** | `..\`, UNC, `\\?\`, absolute path/ADS, Windows reserved/trailing dot, mixed Unicode, over-limit file, symlink/junction race | Không có write ngoài approved root, overwrite/traversal; rejected/sanitized errors; file khác nguyên vẹn |
| **AC-06-E** | A/B verified nhưng B chưa Accept, Reject hoặc hết timeout | 0 content chunk/0 final file, B có quyền từ chối mỗi file như CL |
| **AC-06-F** | C được A pin hợp lệ nhưng không tham gia conversation A↔B, đoán scope/transferId, yêu cầu metadata/chunk | C nhận **0** file metadata/count/content và denied generically, kể cả TLS session C hợp lệ |
| **AC-06-G** | Hai file trùng basename trong Downloads, receiver target đã có, symlink/junction swap giữa Accept và commit | **Không overwrite**; tên mới/atomic no-clobber an toàn, race không thoát Downloads root |
| **AC-06-H** | Tệp bị sửa/truncate trong lúc gửi, chỉnh 1 chunk dù giữ length, duplicate/overlap/missing offsets | Digest/size fail closed, không commit corrupted final, sender không false completed |
| **AC-06-I** | 0/1/268435456/268435457 bytes, sparse file, Unicode, int64 overflow, disk quota | 0-byte hợp lệ, ≤256 MiB nhận đúng SHA-256, 256 MiB+1 từ chối, bounded RAM |
| **AC-06-J** | Inbound unexpected FILE_* trên M1 v1, M2 v2, Group v3, History draft v4 hoặc pre-capability, stripped preface, mismatched SPKI | Profile isolation, post-TLS mutual capability proof, không rò payload và không downgrade |
| **AC-06-K** | 2 peer file bulk + Live P2P text/emoji, shared 4-session cap, slow receiver và cancel | Chat responsive, file queues bounded, không OOM/EDT stall, cleanup worker/stream tốt |
| **AC-06-L** | App crash before/after fsync/atomic rename, retry duplicate final outcome, no-clobber conflict | Tệp final chỉ có bản hoàn chỉnh đã verify, không bị đổi bởi duplicate/stale ACK; staging xử lý đúng policy |
| **AC-06-M** | E2E Group A(owner), B/C(member), D late JOIN, remove C midtransfer, old owner after handoff | Chỉ sau 004 actual verified; 0 post-REMOVE chunk mới, 0 pre-JOIN data, per-recipient outcomes và original sender proof |
| **AC-06-N** | Live P2P file running, đổi conversation, Demo mock, History 005 partial, unsupported Image/Video/Sound | Đúng scope/sender/time/progress, Demo không network, không History auto file persist, không auto-open/call |
| **AC-06-O** | Full malformed/error/fuzz, 2 physical Win11 LAN PCs không Internet, manual Accept/SAS, key change/replay/ACL/path traversal | Test Java21 actual negative/success log, physical host evidence và độc lập security/privacy review; không thay loopback bằng 2 physical PCs |
| **AC-06-P** | Release review M1/M2/004 dependency gates, Constitution I–VII, AC-06-A…O và Stage 007 | 006 chỉ Verified khi mọi AC bắt buộc thực tế PASS; **M4 chỉ khi cả 006 và 007 Verified**. Nếu thiếu Group 004 hoặc independent review → explicitly PARTIAL/BLOCKED |

## 7. Non-Functional Requirements & security boundaries

| Category | Requirement |
| --- | --- |
| **Platform** | Java 21; Windows 11 x64; Swing; Maven. JUnit 5 test-first; shared M2 verified transport, no undisclosed native file library or extra socket |
| **Confidentiality** | TLS1.3 authenticated and human-approved; no user file before verified and receiver accepts; filename/size sensitivity treated as private on unauthorized scopes |
| **Authorization** | Exactly verified identity/scope in each P2P/Group run, not alias/IP/string transferId; repeated check before content/control grant, Group owner+membership cutoff each batch |
| **Integrity** | Full SHA-256 of byte stream and exact byte count mandatory, atomic no-clobber durable receiver finalization; no false success/READ |
| **Filesystem** | User-approved target, absolute/canonical path/ACL reparse protection, check both staging and final handles, no remote-supplied destination, no overwrite unless separately approved |
| **Resources** | Bounded per-frame/per-chunk/per-file/concurrency/queue/worker/disk, fair priority to text/group, backpressure, cancellation, no EDT I/O; numeric file size from CL-06-01 |
| **Fault tolerance** | TCP/TLS disconnect, half-open, disk full, source mutation, stopped process, late ACK, repeated control, timeouts, unknown protocol; state truthful, cleanup deterministic |
| **Privacy** | No automatic file-index/cloud/History binary archive, private path in log/Git, media auto-open, silent attachment saving or malware-free claim |
| **Compatibility** | M1 diagnostic v1, M2 Chat v2, Group v3, History proposed v4 strictly separated; **File profile version is technical draft until plan review**, not a presumed deployed v5 |
| **Dependencies** | P2P release depends on M1/M2 real physical/security Gate 1; Group File depends on 004 actual signed owner runtime; 005 History status does not silently block 006 basic P2P file if no History integration is used |
| **Verification** | All 16 AC and negative corpus need actual command, JDK, OS, peer count, build SHA, pass/fail/blocker; independent security review and ≥2 physical Win11 LAN PCs before release |

## 8. Logical data model (conceptual, **not** frozen DDL/wire)

| Entity / state | Core fields (expected) | Authorization/lifecycle invariant |
| --- | --- | --- |
| **FileOffer** | transferId, senderVerifiedDeviceId, recipientVerifiedDeviceId or groupId, conversationId/groupId+epoch, basename, claimed type, expected size, expected SHA-256, offeredAt UTC | Metadata only over protected File capability and authorized scope; expected size/hash checked, not blindly trusted |
| **TransferPolicy** | selected source, approved target location, maxFileSize, partial/resume/overwrite settings, per-file consent state | Local authority only; paths are **not peer-chosen**; values from confirmed CL |
| **FileChunk** | transferId, currentRun/sessionId, sequence, offset int64, length bounded, binary payload, optional per-chunk proof | One recipient/scope and contiguous expected offset only, bounded frames |
| **FileTransferAttempt** | offer state, verified sender/receiver, bytesSent, bytesAccepted, bytesVerified, durableCompleted, cancellation/error code | Terminal states monotonic; never infer receiver complete from sender write |
| **ReceiverStaging** | user-selected approved root, private staging handle/path, expected total/digest, current offset, lifecycle | No final user-visible file until checksum+fsync+safe atomic no-clobber |
| **TransferOutcome** | terminal status, transferId, verified receiver context, byte count, SHA-256, committedAt UTC/error code | Receiver final proof correlation; does **not** produce M2 app ACK/READ |
| **GroupTransferRecipient** | groupId, verified current owner epoch/revision, original member signature, recipientId, JOIN/REMOVE eligibility, offer/accept/bytes/result | Per-recipient independent results; no new data to revoked member, owner handoff fails closed |
| **TransferCapability** | M2 verified session IDs/SPKIs and post-TLS offer/confirm transcript, negotiated File profile/features | Plaintext bootstrap claims don't authorize file content; profile can be disabled independently |

**Suggested wire shape for plan (NOT APPROVED):** `FILE_CAPS_CONFIRM`/`FILE_OFFER`/`FILE_ACCEPT`/`FILE_REJECT`/`FILE_CHUNK`/`FILE_CHUNK_ACK`/`FILE_END`/`FILE_COMMIT_OK`/`FILE_CANCEL`/`FILE_ERROR`. Choose strict versioned profile and test vectors only after CL-06-08. Preserve M2 TLS max 65,536-byte frame and current v1–v4 codecs. Binary chunk encoding must be bounded; no massive Base64-in-JSON. `FILE_COMMIT_OK` explicitly proves **copy completion**, not app chat READ.

**State progression (candidate semantics):**

`LOCAL_SELECTED → OFFERED → AWAITING_ACCEPT → TRANSFERRING → VERIFYING → COMMITTING → COMPLETED`

Independent terminal branches: `REJECTED`, `CANCELLED`, `FAILED`, `UNCERTAIN`. Transfer cannot return from terminal to TRANSFERRING with the same run without an explicitly approved authenticated resume protocol. A missing receiver final proof cannot advance to COMPLETED.

## 9. ROADMAP coverage & release gates

| ROADMAP | Binding requirements / scenarios | Acceptance |
| --- | --- | --- |
| **06.01** File protocol / chunk / ACK / cancel | FR-06-001…010, 020…029, 037…043 | AC-A/C/J/K |
| **06.02** Receive consent/path/overwrite | FR-06-011…019, 044…045 | AC-D/E/F/G |
| **06.03** Bounded streaming | FR-06-020…029 | AC-A/I/K |
| **06.04** Byte progress/status | FR-06-026…028, 033…036, 053…056 | AC-B/C/N |
| **06.05** Size/hash/integrity | FR-06-030…036 | AC-A/H/I/L |
| **06.06** Cancel/cleanup/crash | FR-06-037…043 | AC-B/C/L |
| **06.07** Traversal/malformed/security tests | FR-06-007, 017…019, 044…048 | AC-D/F/G/H/I/J/O |
| **06.08** Group fanout & partial success | FR-06-049…052 | AC-M/P |

**Release gates / Honest status:**

- **Gate 0 design:** CL-06-01…10 **USER CONFIRMED (9 A, 06-03 B)**; exact FILE_V1/capability/wire golden vectors và Win11 Downloads no-clobber/ACL/reparse/security review **STILL CONDITIONAL/BLOCKED**. User product approval không đồng nghĩa security signoff.
- **Gate 1 functional/security:** Java21 actual RED→GREEN tests, verified pinned TLS session, two real peers and **≥2 physical Windows 11 LAN PCs**, no Internet, real human inbound Accept/SAS; path/ACL/crash/fuzz/integrity/error UI evidence and independent security/privacy signoff.
- **Group gate:** Group 004 signed owner relay/member provenance/JOIN/REMOVE/handoff completed and security-reviewed, at least 3 JVM and actual role tests; until then **Group File disabled**, AC-M and Group part of AC-P blocked, not silently omitted.
- **M4:** File 006 + Multimedia 007 both reviewed and accepted; completing File P2P in isolation is **not** M4 Verified.
- **Current:** `CLARIFIED / PLAN DRAFT / NOT IMPLEMENTED / NOT VERIFIED`. **10/10 CL user CONFIRMED**, **36/160 tasks done** (isolated File domain/source), **16/16 AC NOT RUN**; no protected FILE_V1 implementation, Windows destination writer or independent security approval.

**Next step:** 10/10 decisions are aligned in [plan](plan.md), [FILE_V1 draft](contracts/file-v1.md), [ADRs](research.md), [model](data-model.md), [quickstart](quickstart.md) và [impact review](checklists/architecture-impact-review.md). [160 implementation tasks](tasks.md) now prepared with RED→GREEN, explicit independent protocol/Win11 Downloads/Group gates; next execute reviewer-gated tasks and gather real test evidence. **36/160 tasks done, 27 new File JUnit PASS, full 165/165 regression PASS; no network/receiver/physical File tests or 006/M4 Verified.**
