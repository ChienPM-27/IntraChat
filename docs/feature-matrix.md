# Ma trận 11 chức năng — IntraChat Messenger (đồ án môn học)

**Cập nhật:** 22/11/2026 · [Phạm vi bàn giao](course-project-scope.md) · [ROADMAP hiện hành](../ROADMAP.md) · [Kế hoạch UI Messenger](messenger-integration-plan.md).

**Mục tiêu:** cả 11 chức năng **thực sự hoạt động trong chế độ Live LAN** với **một giao diện Messenger**. **Demo** có thể hoàn chỉnh về UI nhưng **không** được tính là Live/đã hoàn thành chức năng mạng.

**Quy ước tiến độ:**
- **Demo:** hành vi mô phỏng trong `MockChatService`, không gửi dữ liệu qua LAN.
- **Core prototype:** có class/test lõi nhưng chưa nối network/UI Live.
- **Live cục bộ:** test/đã chạy qua localhost hoặc hai JVM cùng PC; chưa chắc đã demo hai máy vật lý.
- **Chưa có Live:** UI có thể đã có nhưng thao tác Live bị ẩn/từ chối.
- **Đạt đồ án:** chưa chức năng nào được đánh dấu mới chỉ từ việc cập nhật tài liệu; cần test chức năng và thực hành demo.

| ID | Chức năng | Demo Messenger | Mã Live hiện có | Mức hiện tại | Bước bắt buộc tiếp theo | Spec |
| --- | --- | --- | --- | --- | --- | --- |
| F01 | **P2P** | Direct mock | `LiveChatService`, `SecureChatCoordinator`, `LiveChatAdapter`; text TCP/TLS 1.3 hai chiều | **Live cục bộ** | Hoàn tất smoke 2 peer/2 PC khi có máy, lỗi mạng và Messenger UX | [002](../specs/002-lan-networking/tasks.md) · [003](../specs/003-p2p-messaging/tasks.md) |
| F02 | **Group** | Mock tạo nhóm và tin nhóm | `GroupRoster`, `GroupInboundMailbox`, `GroupRecipientLedger`, chữ ký owner/DPAPI | **Core prototype** | GroupService fanout LAN + invite/join + bật CreateGroupDialog và timeline Live | [004](../specs/004-group-chat/tasks.md) |
| F03 | **File** | Chọn/lưu file demo | **Chưa có** sender/receiver file LAN; `LiveChatAdapter` chỉ nhận `TEXT` | **Chưa có Live** | FileTransferService chunk/progress/checksum/save trong Messenger | [006](../specs/006-file-transfer/spec.md) |
| F04 | **Emoji** | Bảng emoji và mock | Unicode/ZWJ qua `ChatText`, `ChatV2Codec`, `ComposerPanel` | **Live cục bộ** | GUI test nhiều emoji/UTF-8 với hai peer, cùng trường tin P2P | [003](../specs/003-p2p-messaging/tasks.md) |
| F05 | **History** | Hội thoại giả trong phiên | `LiveChatService.transcript` **RAM**, `loadHistory` chỉ trả dữ liệu phiên/peer đang kết nối | **Chưa có Live bền vững** | Local repository SQLite, restart test, hiển thị timeline, xóa lịch sử; sync P2P nếu rubric yêu cầu | [005](../specs/005-chat-history/spec.md) |
| F06 | **Image** | Chọn/xem/lưu ảnh demo | Renderer/viewer UI có sẵn; **chưa truyền ảnh LAN** | **Chưa có Live** | Tái sử dụng FileTransfer, render thumbnail/preview sau nhận xong | [007](../specs/007-multimedia/spec.md) |
| F07 | **Video** | File video demo, mở OS player | UI có thể mở file local; **chưa truyền video LAN** | **Chưa có Live** | Chia sẻ video qua FileTransfer, lưu và mở bằng trình phát mặc định | [007](../specs/007-multimedia/spec.md) |
| F08 | **Sound** | Demo ghi/phát WAV | `VoiceRecorder`, media bubble đã có; **chưa truyền âm thanh LAN** | **Chưa có Live** | Gửi WAV/audio qua FileTransfer, phát từ tệp nhận; mic/loa test thủ công | [007](../specs/007-multimedia/spec.md) |
| F09 | **State** | `Đã nhận`/`Đã xem` mock theo timer | `MessageAttempt`, `DeliveryState`, authenticated ACK/READ, UNCERTAIN/retry thủ công | **Live cục bộ** | Kiểm tra trạng thái thật ở 2 PC, gắn tiến độ File và Group per recipient | [003](../specs/003-p2p-messaging/tasks.md) |
| F10 | **Time** | Timestamp demo | `ChatViewMapper` giữ `Instant` UTC, hiển thị `ZoneId` cục bộ | **Live cục bộ** | Test History reload và tin Group/Media có timestamp đúng | [003](../specs/003-p2p-messaging/tasks.md) · [005](../specs/005-chat-history/spec.md) |
| F11 | **Sender** | Tên người gửi mock | UUID trong TLS, tên chỉ là alias; test hai peer cùng tên | **Live cục bộ** | Gắn đúng sender trong bubble Group và History/Media sau restart | [003](../specs/003-p2p-messaging/tasks.md) · [004](../specs/004-group-chat/tasks.md) |

## Điểm cần khắc phục để đạt 11/11

**Đã có nền tương đối tốt:** P2P, Emoji, State, Time, Sender; vẫn phải kiểm tra thực hành qua Messenger và LAN vật lý (nếu có).

**Chưa đủ để trình diễn như tính năng Live:** Group (có core nhưng chưa transport/UI), File, History bền vững, Image, Video và Sound. Thứ tự hiệu quả là: **Group → FileTransfer → Local History → Image/Video/Sound**; có thể triển khai History song song File.

## Không nhầm bảo mật với tiến độ chức năng

M1 diagnostics v1 không chứa chat; TLS v2 đã tồn tại và **không bị xóa** khi đổi scope đồ án. Những task Gate 0/1 độc lập, audit crypto, SBOM/DPAPI khác Windows user trong các spec cũ **không còn chặn tiến độ code 11 chức năng**, nhưng vẫn được giữ là **chưa thực hiện**, không sửa thành PASS.

Dữ liệu thật cần giữ **file path an toàn, giới hạn dữ liệu, phản hồi lỗi, không khóa EDT**. Các tình huống nâng cao như owner grant signed mỗi message, stress quy mô sản phẩm và penetration test không phải mốc để giảng viên kiểm tra **một tính năng đã chạy đúng**.

## Bằng chứng hiện có (không chạy test mới khi cập nhật ma trận)

- **M0:** 41/41 Verified.
- **M1:** 53/60 task (lịch sử M1); `LanDiagnosticsDialog` đã nằm trong Messenger.
- **M2:** 65/90 task, test P2P TLS/emoji/ACK/READ, sender/time/GUI theo mã nguồn.
- **Group core:** 28/92 task; bản Java core chưa nối Messenger Live.
- **Java21 full regression gần nhất:** **246/246 JUnit PASS** (22/11/2026), gồm M0/M1/FE/M2/Group, **không** đồng nghĩa 246 test cho 11 chức năng Live.
- **Không có bằng chứng:** File/History bền vững/Image/Video/Sound Live hoặc Group Live nhiều máy.
