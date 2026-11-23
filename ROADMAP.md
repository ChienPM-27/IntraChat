# IntraChat — ROADMAP đồ án môn học (11 chức năng trên Messenger)

**Cập nhật:** 22/11/2026 · **Phiên bản kế hoạch:** 2.0 — ưu tiên sản phẩm chạy được, demo trực tiếp được
**Nguồn phạm vi:** yêu cầu mới của người thực hiện đồ án: **P2P, Group, File, Emoji, History, Image, Video, Sound, State, Time, Sender** đều phải hoạt động **thực**, đúng mục đích, trong **giao diện Messenger hiện có**.
**Nguồn chi tiết:** [Phạm vi đồ án](docs/course-project-scope.md) · [Ma trận 11 chức năng](docs/feature-matrix.md) · [Kế hoạch tích hợp UI Messenger](docs/messenger-integration-plan.md) · [Kiến trúc](docs/architecture.md).
**Roadmap kỹ thuật trước đây:** [Bản lưu nguyên trạng 22/11/2026](ROADMAP-LEGACY-SECURITY-2026-11-22.md). Được giữ để đối chiếu code/spec và lịch sử quyết định, **không còn là thứ tự công việc ưu tiên cho đồ án**.

> **Nguyên tắc xác nhận:** Cột Demo có chức năng không đồng nghĩa Live LAN đã làm xong. Test trong một JVM/hai JVM một PC không đồng nghĩa thử nghiệm hai máy thật. Không thay đổi dấu [x] của những task chưa thực hiện; tách rõ **tiến độ task kỹ thuật lịch sử** và **tiến độ 11 hành vi thực tế**.

## 1. Quyết định phạm vi mới

1. **Một ứng dụng, một launcher:** `ui.IntraChat` → `LoginFrame` → `MainChatFrame` (Messenger). Demo dùng `MockChatService`; Live dùng các service LAN. Thêm dialog/panel vào Messenger khi cần; **không khôi phục** `MainWindow`, `NetworkPanel`, `IntraChatApplication`, `LiveIntraChat` hay `LiveConnectionFrame`.
2. **Hoàn thiện luồng người dùng trước:** kết nối → chọn người/nhóm → nhắn tin/emoji → gửi file/media → thấy State/Sender/Time → thoát/mở lại và đọc History. Mỗi tính năng phải đi qua **cùng danh sách hội thoại, timeline và composer của Messenger**.
3. **Tái sử dụng backend có sẵn:** không viết lại hệ TCP, TLS, codec hay DPAPI chỉ để đơn giản hóa tài liệu. Chỉ chỉnh đúng những phần thực sự cản trở chức năng. Có thể dùng phiên P2P hiện hữu cho Group/File/Media thay vì dựng thêm hệ bảo mật riêng nếu giao thức hỗ trợ.
4. **Giảm việc bảo mật ngoài yêu cầu môn học:** không đặt kiểm toán mật mã độc lập, audit SBOM/CVE chuyên sâu, kiểm thử đa tài khoản Windows, đánh giá xâm nhập, chứng nhận Gate 0/Gate 1 chuyên nghiệp hoặc stress cực hạn làm **điều kiện để bắt đầu code Group/File/Media**. Những mục này chuyển thành **nâng cao/tùy chọn**, không được đánh dấu là đã làm.
5. **Giữ an toàn tối thiểu:** không ghi đè tệp ngoài ý muốn, không chấp nhận đường dẫn `../`, giới hạn kích thước/chunk, không block Swing EDT, timeout/cleanup, không dùng Java deserialization dữ liệu mạng, không đưa khóa/mật khẩu/nội dung riêng vào log, và không nhận nhầm một peer. TLS 1.3 và ghép cặp hiện hữu **vẫn giữ nguyên** khi hoạt động; không tự mở cổng Internet hoặc hạ cấp truyền dữ liệu thật sang M1 plaintext.
6. **Chỉ báo hoàn thành bằng demo/kiểm thử chức năng:** cần các ca happy path, lỗi phổ biến, đóng/mở app và (khi có điều kiện) **hai máy trong cùng LAN**. Không đòi hỏi chứng nhận bảo mật doanh nghiệp cho đồ án.

Quyết định thay đổi phạm vi này **không xóa mã nguồn bảo mật đã viết** và **không sửa lịch sử kết quả nghiệm thu**. Các task của spec 002/003/004 còn mở vì tiêu chí cũ vẫn được giữ trong tracker. Chúng không còn tự động chặn việc triển khai chức năng khác. [Giải thích ngoại lệ/đối chiếu spec](docs/course-project-scope.md).

## 2. Tiến độ hiện tại (ảnh chụp thực tế 22/11/2026)

| Giai đoạn/spec | Tracker kỹ thuật hiện tại | Điều đã chạy | Chưa có chức năng Live hoặc chưa test đủ |
| --- | --- | --- | --- |
| **001 — Foundation** | **41/41 Verified** | Java 21, Maven, Swing/FlatLaf, launcher Messenger | Không phải tracker 11 chức năng |
| **001 — Desktop FE** | **4/5** | Giao diện Messenger, Demo chat/group/file/media | Thiết bị mic/loa/video thực tế cần kiểm tra thủ công |
| **002 — LAN** | **53/60** | M1 v1 diagnostic TCP; listener v1/v2 chung trong Messenger | Hai máy Windows LAN thật + nghiệm thu M1 lịch sử |
| **003 — P2P** | **65/90** | Text/emoji Live TLS, peer UUID, thời gian, ACK/READ/retry cục bộ | Một số GUI/lifecycle test và hai máy LAN thật; không cần audit độc lập để code 11 tính năng |
| **004 — Group** | **28/92** | Domain, roster, owner grant, codec, DPAPI metadata, mailbox/receipt **prototype** | Chưa có **Group Live transport, fanout và UI** |
| **005 — History** | **Chưa triển khai** | Demo + Live transcript RAM phiên | Chưa có lưu trữ lịch sử bền vững, mở app lại chưa đọc được |
| **006 — File** | **Chưa triển khai** | Chọn/lưu file trong Demo | Chưa gửi/nhận file LAN |
| **007 — Media** | **Chưa triển khai** | Ảnh, video, ghi/phát WAV trong Demo | Chưa chia sẻ Image/Video/Sound LAN |

Báo cáo kỹ thuật cuối đã có: Java 21 `verify-m2.ps1 -Offline` **246/246 JUnit PASS, exit 0** và JAR/launcher PASS vào 22/11/2026. **Đây là kết quả gần nhất được ghi trong repo**, không phải bằng chứng 11 tính năng Live hoàn thành. Xem [M2](specs/003-p2p-messaging/evidence/m2-continued-code-2026-11-22.md) và [Group prototype](specs/004-group-chat/evidence/group-core-continued-2026-11-22.md).

## 3. Ma trận nghiệm thu 11 chức năng — cùng một Messenger

| Chức năng | Trạng thái Live hiện tại | Bài kiểm thử tối thiểu để đánh dấu **Hoạt động** | UI Messenger |
| --- | --- | --- | --- |
| **P2P** | Cục bộ có chạy | Hai peer kết nối bằng IP/port, gửi/nhận text hai chiều, mất kết nối không treo app | Chọn hội thoại, header trạng thái, composer |
| **Group** | Chưa chạy Live | Tạo nhóm ≥3 thiết bị, thêm/chấp nhận, gửi text và **mỗi thành viên nhận đúng một bản**, hiển thị người gửi | `CreateGroupDialog`, danh sách hội thoại, timeline |
| **File** | Chưa chạy Live | Chọn tệp, người nhận đồng ý/chọn nơi lưu, truyền chunk, đối chiếu checksum/byte count, hủy giữa chừng | Nút đính kèm, bubble file, tiến độ/hủy |
| **Emoji** | Đã có cục bộ | Emoji Unicode/ZWJ/tiếng Việt không lỗi, gửi hai chiều và hiển thị đúng | Bảng emoji của `ComposerPanel` |
| **History** | RAM phiên; chưa bền vững | Gửi tin → đóng hẳn app → mở lại vẫn xem được tin theo hội thoại, đúng sender/time, không trộn nhóm/cá nhân | Danh sách hội thoại, timeline, tìm/tải lịch sử |
| **Image** | Chưa chạy Live | Gửi ảnh LAN, nhận và hiển thị thumbnail/preview đúng định dạng, lưu/mở được | Bubble ảnh, viewer Messenger |
| **Video** | Chưa chạy Live | Gửi video như tệp, người nhận lưu/mở bằng trình phát OS; có thông báo lỗi rõ khi không hỗ trợ | Bubble video, mở/lưu |
| **Sound** | Chưa chạy Live | Ghi/chọn âm thanh, gửi LAN, nhận/phát tệp WAV hoặc định dạng được hỗ trợ | Mic/đính kèm, bubble audio/play |
| **State** | Đã có cục bộ | `Đang gửi/Đã gửi/Đã nhận/Đã xem/Chưa rõ/Thất bại` phản ánh sự kiện thật; retry thủ công không gửi trùng vô ý | Header, bubble và trạng thái truyền |
| **Time** | Đã có cục bộ | Tin giữ UTC, UI hiển thị giờ địa phương; mở lại History vẫn đúng | Giờ trên bubble/timeline |
| **Sender** | Đã có cục bộ | Hai peer **trùng tên** vẫn hiện đúng người gửi/UUID; tin nhóm hiển thị người gửi từng dòng | Avatar, tên, bubble và header |

**Định nghĩa hoàn thành theo đồ án:** Có **source Live**, UI Messenger thao tác được, test 2 peer/3 peer cục bộ PASS, trình diễn trên LAN thật nếu có máy, xử lý lỗi cơ bản; ghi kết quả **PASS/FAIL/CHƯA CHẠY**. Không dùng tỷ lệ tracker 002/003/004 làm tỷ lệ hoàn thành 11 chức năng. [Checklist demo](docs/course-project-scope.md).

## 4. Lộ trình ưu tiên để triển khai ngay

### Chặng A — Chốt P2P + trải nghiệm Messenger (ưu tiên P0)

**Không viết lại giao thức mã hóa đã hoạt động.** Tận dụng `LiveChatAdapter`, `LiveChatService`, `SecureChatCoordinator`, `MainChatFrame` hiện có. Giữ mã IP/port và thao tác Connect ở `LanConnectionDialog`, tin nằm trong Messenger.

- [ ] Chạy smoke Windows GUI: 2 phiên Java độc lập, Connect/Accept, P2P text/emoji hai chiều, State, Time, Sender; thử cancel/close/reconnect.
- [ ] Đơn giản hóa **cách trình bày** kết nối (kết nối/ngắt/kết quả rõ ràng), không yêu cầu người dùng hiểu thuật ngữ chữ ký/grant/DPAPI. Xác minh SAS đang có được giữ nếu cần để nối vào backend, nhưng không mở cửa sổ riêng.
- [ ] Tất cả lỗi `UnsupportedOperationException` của tính năng chưa có phải được thông báo trung thực; không hiển thị nút Live thao tác giả.
- [ ] Nếu có thể dùng hai laptop trong cùng LAN, thử **thật** và ghi IP, cổng, kết quả; không coi hai JVM trên cùng PC là hai máy vật lý.

**Xong khi:** UI Messenger P2P/Emoji/State/Time/Sender chạy đúng với ít nhất hai peer; đây là nền tảng cho các bước tiếp theo. **Không chờ** audit TLS/DPAPI độc lập để bắt đầu Chặng B/C.

### Chặng B — Group thật trong Messenger (ưu tiên P0, `004-group-chat`)

**Checklist chức năng cần code:** [Group MVP 3 peer trong Messenger](specs/004-group-chat/course-mvp.md). Task 92 mục cũ vẫn là hồ sơ kỹ thuật; không đợi reviewer Gate0 để bắt đầu Group Live.

- [ ] Dùng các peer P2P đang kết nối để tạo nhóm, owner + danh sách tối đa khoảng 5 thiết bị; UI `CreateGroupDialog` hiện có **không cần viết cửa sổ chat mới**.
- [ ] Chốt một luồng invite/join/leave đơn giản có xác nhận người nhận; không bắt buộc ký grant cho mỗi tin nếu không cần để đáp ứng kịch bản môn học. Giữ phân tách đúng người nhận và revision/roster nhất quán.
- [ ] Fanout text trực tiếp đến **các thành viên khác** qua các kênh P2P; người gửi và thành viên đều thấy cùng conversation/group ID, sender/time.
- [ ] Map `GroupService`/backend vào `ChatActionHandler.createGroup`, `ChatClientListener` và `LiveChatAdapter`. Bật nút `Tạo nhóm mới` trong Live **chỉ khi backend gửi/nhận thực**.
- [ ] Test 3 JVM độc lập, nhận đủ tin, ngắt một thành viên, duplicate không tạo hai bubble.

**Xong khi:** nhóm Live thực được tạo/gửi/nhận trên Messenger; không còn chỉ là `GroupInboundMailbox` hoặc Demo mock. Tránh biến Group thành dự án kiểm toán chữ ký/secure metadata riêng.

### Chặng C — File Transfer chung (ưu tiên P0, `006-file-transfer`)

- [ ] Thiết kế `TRANSFER_START/CHUNK/FINISH/CANCEL` hoặc hợp đồng tương đương, gắn đúng conversation/recipient và ID.
- [ ] Chunk-stream thay vì `readAllBytes` toàn file; giới hạn kích thước và kiểm tra byte count/CRC/SHA-256 để bảo đảm tệp không hỏng.
- [ ] Hiện tiến độ, Cancel, lưu vào nơi **người dùng lựa chọn**, xử lý trùng tên và lỗi ổ đĩa; không ghi ra ngoài thư mục được chấp nhận.
- [ ] Tái sử dụng nút file và bubble trong Messenger, chuyển `ComposerPanel` Live từ text-only thành bật attachment khi service sẵn sàng.
- [ ] Test qua hai JVM với tệp nhị phân/Unicode; so sánh SHA-256 và kiểm tra hủy truyền.

**Xong khi:** có thể gửi và nhận **một file thật qua LAN** trong hội thoại Messenger.

### Chặng D — History bền vững, đọc lại (ưu tiên P0, `005-chat-history`)

- [ ] Ghi theo `conversationId/messageId/senderId/sentAt/type/status` vào SQLite hoặc lưu trữ cục bộ phù hợp; không dùng mock History như dữ liệu Live.
- [ ] `LiveChatAdapter.loadHistory` lấy từ kho thật kết hợp RAM, chống tin trùng, sắp xếp nhất quán, không treo EDT.
- [ ] Hiển thị lịch sử sau tắt và mở lại app, có xóa lịch sử cho một hội thoại. Không cần xây cơ chế đồng bộ sự kiện/mã hóa CSDL phức tạp **trước** khi Local History hoạt động.
- [ ] **History sync giữa các peer:** nếu tiêu chí môn học/CL-04 còn bắt buộc, triển khai đồng bộ các tin theo message ID sau reconnect như phần mở rộng của History. Không đánh dấu toàn bộ yêu cầu sync đã đạt chỉ từ bản lưu local.

**Xong khi:** khởi động lại Messenger mà vẫn xem được đoạn chat thật và metadata Sender/Time/State đúng.

### Chặng E — Image, Video, Sound (ưu tiên P0, `007-multimedia`)

- [ ] Tái sử dụng **File Transfer ở Chặng C** để gửi nội dung, không tạo 3 giao thức mạng riêng.
- [ ] Ảnh: bubble/thumbnail + xem ảnh + lưu file nhận.
- [ ] Video: bubble + mở trong trình phát mặc định, không yêu cầu video call.
- [ ] Sound: dùng `VoiceRecorder`/tệp WAV, bubble phát/nhận; kiểm tra mic/loa thực trên máy chạy.
- [ ] Test 2 JVM, ảnh/video/âm thanh nhận đủ byte, UI báo lỗi định dạng không hỗ trợ, không khóa EDT.

**Xong khi:** File, Image, Video và Sound đều gửi qua LAN và mở/phát được từ timeline Messenger.

### Chặng F — Tổng hợp và trình diễn môn học

- [ ] Test 11 chức năng theo **một kịch bản liên tục**, không chuyển sang launcher/cửa sổ kỹ thuật cũ.
- [ ] Test tối thiểu với 2 JVM; Group với 3 JVM; nếu có 2–3 PC LAN thì ghi thêm bằng chứng thật.
- [ ] Kiểm tra đóng/mở lại, kết nối lỗi, tệp hỏng/hủy, emoji Unicode và trạng thái receipt.
- [ ] Chạy Java 21 `mvnw.cmd -o test`, `clean package`, `java -jar ... --diagnostics`; cập nhật ảnh chụp/UI screenshots nếu cần.
- [ ] README hướng dẫn **người chấm** mở hai máy, khởi động listener, nhập IP, nhắn nhóm, truyền tệp và xem History; nếu thiết bị/hệ thống không hỗ trợ, ghi giới hạn.

**Xong khi:** người dùng thao tác được cả **11 chức năng Live** trên Messenger; phần Demo chỉ còn vai trò minh họa/offline.

## 5. Bản đồ UI: không khôi phục màn hình cũ

```text
java -jar intrachat-0.1.0-SNAPSHOT.jar
    └─ ui.IntraChat
        └─ ui.frames.LoginFrame
            ├─ Demo → MockChatService ───────────────────────┐
            └─ Chat trong LAN → ui.dialogs.LanConnectionDialog
                   └─ connect/listen/Accept (mạng chạy worker)
                      → LiveChatAdapter ────────────────────┤
                                                           ▼
                                           ui.frames.MainChatFrame
                                             ├─ danh sách P2P / Group
                                             ├─ timeline + Sender/Time/State/History
                                             ├─ Composer text + emoji
                                             ├─ nút Group / Attachment / Mic
                                             └─ bubble file/ảnh/video/âm thanh
```

| Luồng cũ hoặc thành phần có sẵn | Hướng thống nhất Messenger |
| --- | --- |
| `vn.intrachat.ui.MainWindow/NetworkPanel/InboundRequestDialog` | **Đã loại khỏi build**; thay bằng `LanDiagnosticsDialog` nằm trong `LanConnectionDialog`. Không khôi phục. |
| `ui.LiveIntraChat/LiveConnectionFrame` | **Đã loại khỏi build**; `LoginFrame` → `LanConnectionDialog` → `MainChatFrame`. |
| Demo Group / `CreateGroupDialog` | Giữ UI; đổi service provider sang Group Live khi có thật, không mở GroupFrame riêng. |
| Demo chọn/lưu tệp `ComposerPanel/MediaFiles` | Giữ UI/bubble; nối FileTransfer service để dữ liệu đi LAN; vẫn cho phép Demo mock. |
| Demo Image/Video/`VoiceRecorder` | Giữ viewer/player/recorder; nhận payload từ FileTransfer và render trong timeline. |
| Live `loadHistory` RAM-only | Đổi sang repository thật nhưng giữ giao diện danh sách hội thoại/timeline cũ. |

**Quy tắc UI:** chức năng chưa hỗ trợ Live không được âm thầm giả lập từ Demo. Khi service mới được nối, sử dụng listener event + `SwingUtilities.invokeLater` để cập nhật UI; không truyền `Socket` vào Swing widget. [Chi tiết giao diện](docs/messenger-integration-plan.md) · [lịch sử migration](docs/ui-migration-2026-11-21.md).

## 6. Bảo mật: giữ tối thiểu, bỏ việc dư thừa khỏi lộ trình bắt buộc

| Giữ lại vì giúp ứng dụng hoạt động đúng | Không còn là đầu việc bắt buộc để được chấm 11 chức năng |
| --- | --- |
| M1 diagnostic tách user chat; M2 TLS/peer identity **đang hoạt động** không bị xóa | Thiết kế lại TLS, tự xây thêm mật mã/session riêng khi không có yêu cầu |
| Kiểm tra format/framing, size limit, timeout, cleanup, lỗi mạng | Penetration test, chứng nhận mã hóa độc lập, đánh giá supply-chain doanh nghiệp |
| Sender và group membership đúng người; receipt không nói dối | Kịch bản tấn công mật mã chuyên sâu, rotating key/secret mỗi message |
| File path an toàn, không overwrite bừa, streaming và checksum | Cross-user Windows DPAPI acceptance nếu không nằm trong đề/rubric |
| Hiển thị yêu cầu cho phép nhận file; không log nội dung nhạy cảm | Các Gate 0/Gate 1 **nghiệp vụ bảo mật độc lập** trong tracker cũ làm điều kiện chặn Group/History/File/Media |
| UI không treo, đóng app sạch, không dùng giao thức public Internet | Thử tải quy mô sản xuất; cam kết compliance/production-grade ngoài phạm vi môn học |

**Lưu ý:** Các chức năng đã triển khai như `TlsContextFactory`, `PinnedPeerStore`, `PeerTrustService`, `ProtectedGroupStore` **không bị xóa hoặc tắt bởi việc sửa roadmap này**. Nếu chúng gây lỗi chức năng, đơn giản hóa từng lớp bằng test cụ thể thay vì xóa đồng loạt. Hiện các file/spec cũ vẫn giữ tiêu chí kỹ thuật cũ; chỉ **thước đo bàn giao môn học** đổi theo quyết định này.

## 7. Ưu tiên thực tế, quản lý task và tài liệu

- **Ưu tiên 1:** B/C — Group thật và File thật qua Messenger (hai khoảng trống lớn nhất có thể demo), song song D — History lưu cục bộ.
- **Ưu tiên 2:** E — Image/Video/Sound dùng chung FileTransfer, bao gồm tương thích audio/video với máy Windows.
- **Ưu tiên 3:** A/F — phủ test đầy đủ, sửa lỗi GUI/State/Sender/Time, chạy 2–3 tiến trình/thiết bị.
- **Không bắt buộc:** audit bảo mật độc lập, Gate review cũ, một hệ mã hóa Group v3 mới nếu P2P transport có thể mở rộng an toàn và đúng chức năng.

**Nguồn duy nhất của tiến độ chức năng:** [feature matrix](docs/feature-matrix.md), trạng thái PASS sau bài test mô tả ở mục 3. **Nguồn tiến độ kỹ thuật cũ:** [Spec 002 tasks](specs/002-lan-networking/tasks.md), [Spec 003 tasks](specs/003-p2p-messaging/tasks.md), [Spec 004 tasks](specs/004-group-chat/tasks.md) — giữ nguyên checklist/historical gates, không tự động đổi các task chưa xong thành DONE.

**Tài liệu triển khai bổ sung:** [Spec 005 — History](specs/005-chat-history/spec.md), [Spec 006 — File](specs/006-file-transfer/spec.md), [Spec 007 — Media](specs/007-multimedia/spec.md) (kế hoạch, **chưa có Live code**). Thực hiện code theo từng nhánh chức năng, không tạo launcher/screen mới.

**Bản bàn giao mong muốn:** một JAR Java 21, một giao diện Messenger, 11 chức năng Live cơ bản chạy được, ví dụ thử nghiệm và hướng dẫn chạy trên LAN. Không gọi bản đồ án này là sản phẩm bảo mật đã được chứng nhận.