# Feature Specification: Nhắn tin trực tiếp P2P — Giai đoạn 03 (M2)

**Feature Branch:** `003-p2p-messaging` (nhánh tài liệu, tạo từ `feat/m1-lan-networking`; không phải trạng thái đã triển khai)
**Created:** 2026-11-21
**Status:** **In Progress / Partial implementation — CL-03-01…05 confirmed; v2 TLS/Chat Live local loopback tested, full M2 not Verified; see [partial acceptance](evidence/m2-acceptance-report.md)**
**Priority:** P0 (ROADMAP) · **Milestone:** M2 — P2P Chat MVP
**Input:** [ROADMAP — Giai đoạn 03](../../ROADMAP.md) · [Spec M1](../002-lan-networking/spec.md) · [M1 acceptance](../002-lan-networking/evidence/m1-acceptance-report.md) · [FE mock demo](../001-desktop-frontend/spec.md)
**Governing:** [IntraChat Constitution](../../.specify/memory/constitution.md) · [Spec M0](../001-project-foundation/spec.md)
**Scope:** **P2P, Sender, Time, State, Emoji**. Group/History/File/Media thuộc giai đoạn sau.

## 1. Overview & mục tiêu sản phẩm

Người dùng trên **hai máy Windows 11 x64 trong cùng LAN** có thể chủ động kết nối theo **IPv4 và cổng nhập thủ công**, xác minh đúng peer và trao đổi **tin nhắn văn bản/emoji thật theo hai chiều**, không cần Internet, cloud hay server trung tâm. Giao diện thể hiện rõ **hội thoại/peer**, **ai gửi**, **thời gian**, **trạng thái kết nối và giao nhận có bằng chứng**, đồng thời cho biết lỗi khi peer offline hoặc mất kết nối.

**M2 là chuyển từ chẩn đoán sang dữ liệu người dùng.** M1 hiện chỉ cho phép `HELLO*` và `DIAGNOSTIC_PING/PONG` trên kênh **plaintext và chưa xác thực**. Vì vậy, **không được bật `CHAT_TEXT` bằng cách bỏ message whitelist** hoặc coi lần bấm `Chấp nhận` của M1 là xác minh danh tính. Trước khi truyền nội dung chat thật, M2 **MUST** có kênh bảo vệ nội dung và liên kết peer với danh tính được người dùng xác minh, theo thiết kế/kiểm thử ở `plan.md`. Trong giai đoạn chỉ đang có transport M1, nút gửi chat thực **phải bị khóa**; không được lén gửi qua kênh chẩn đoán.

**Trạng thái phụ thuộc thực tế tại thời điểm viết spec:** M0 **Verified (41/41)**; M1 **In Progress (36/60)**, 45 JUnit tests pass sau khi hợp nhất frontend demo, đã có hai tiến trình loopback và Ping/Pong hai chiều nhưng **chưa kiểm thử hai máy LAN thật**, do đó **M1 chưa Verified**. Có hai entrypoint Swing độc lập: `vn.intrachat.IntraChatApplication` (giao diện mạng kỹ thuật) và `ui.IntraChat` (Messenger **mock data**). Việc hợp nhất UI demo với dịch vụ gửi tin thật **là yêu cầu tương lai của M2, chưa hoàn thành**.

### 1.1. Ranh giới và các quyết định kế thừa

| Mã | Đã được xác nhận trong giai đoạn trước | Áp dụng trong M2 |
| --- | --- | --- |
| CL-01-01 | **Direct P2P LAN**, không bắt buộc central server | Chat A↔B đi trực tiếp; không dùng Internet relay hoặc cloud |
| CL-01-02 | Windows trước, mục tiêu Windows 11 x64 | Nghiệm thu trên 2 máy Windows thực |
| CL-01-03 | Video/Sound là file, không phải cuộc gọi | Không phát triển live call trong M2 |
| CL-01-04 | History cần sync giữa **peer được phép** sau này | Không mở History/Sync qua kênh M1; M2 chỉ giữ tin trong phiên |
| CL-02-01 | **IP/port nhập thủ công**; không auto-discovery | Không tìm peer bằng broadcast/mDNS/scan |
| CL-02-02 | Kết nối đến luôn cần **người dùng Chấp nhận/Từ chối** | Ghép cặp/xác minh danh tính **khác** với phê duyệt kết nối đến |
| CL-02-03 | **M1 chỉ chẩn đoán, không xác thực/mã hóa**, phải cảnh báo | Không dùng kênh M1 để gửi chat; M2 phải mở rộng giao thức có kiểm soát |
| CL-02-04 | Kết nối lại **do người dùng chủ động** | Không tự tạo socket/reconnect ngầm sau mất kết nối |

### 1.2. Clarification Decisions — CONFIRMED 2026-11-21

| ID | Quyết định được người dùng xác nhận | Hệ quả bắt buộc trong M2 |
| --- | --- | --- |
| **CL-03-01** | **So khớp mã/fingerprint lần đầu, sau đó ghi nhớ thiết bị** | Hai đầu phải chủ động xác minh cùng danh tính/khóa trước khi gửi chat; ghi nhớ **danh tính/credential đã xác minh** qua lần khởi động sau, không ghi nhớ nội dung tin. Kết nối đến vẫn phải được Chấp nhận/Từ chối mỗi lần theo CL-02-02; đổi khóa hoặc thu hồi trust phải cảnh báo và xác minh lại. |
| **CL-03-02** | **Giữ bản nháp trong phiên; người dùng gửi lại thủ công khi mất kết nối** | Không tự tạo offline outbox, không auto resend và không auto reconnect. Nháp chưa gửi không bị gắn trạng thái đã gửi. Nháp chỉ tồn tại trong bộ nhớ phiên, đóng ứng dụng có thể mất. |
| **CL-03-03** | **Có trạng thái Đã xem dựa trên sự kiện xem thực** *(mở rộng phạm vi)* | `Đã nhận` chỉ khi có application ACK hợp lệ; `Đã xem` chỉ khi phía nhận thực sự hiển thị tin ở hội thoại đang mở, trong vùng nội dung hiển thị, và gửi **read receipt riêng có xác thực, đúng messageId/conversation/peer/session**. Không suy ra từ ACK, nhận mạng, preview/notification, mở app nền hoặc dữ liệu mock; read receipt không chứng minh mắt người thật đã đọc. |
| **CL-03-04** | **Tin nhắn chỉ giữ trong phiên; thoát ứng dụng thì mất** | Không có History bền vững, offline sync hay persist nội dung/receipt. **Thông tin trust/khóa thiết bị được ghi nhớ theo CL-03-01 là riêng biệt** với lịch sử/nội dung chat và phải được bảo vệ theo chính sách kỹ thuật sau này. |
| **CL-03-05** | **Dùng giao diện Messenger FE cho Chat thật; giữ Demo riêng** | Tái sử dụng FE cho chế độ **Live** gắn với dịch vụ P2P, đồng thời giữ **Demo** hoạt động độc lập và được ghi nhãn. Không lẫn dữ liệu, login demo, read/delivery mock hoặc trạng thái ảo vào Live. |

**Ranh giới cần giữ:** M1 vẫn là diagnostic plaintext chưa xác thực; việc ghi nhớ fingerprint sau xác minh **không** có nghĩa tự chấp nhận inbound. Read receipt là sự kiện **UI hiển thị tin của phía nhận** được gửi qua kênh đã xác minh/bảo vệ, không là bằng chứng con người đã đọc hoặc báo nhận TCP. Người dùng đã chốt **hành vi sản phẩm**; các thuật toán, định dạng fingerprint/SAS, kho khóa, wire types cho application ACK/read receipt, timeout và tiêu chí “đã hiển thị” cụ thể để kiểm thử sẽ được lựa chọn có lý do ở **bước plan**, không cần mở lại năm Clarify.

**M2 đang triển khai.** Có [plan.md](plan.md), [tasks.md](tasks.md) và mã nguồn TLS/Chat Live test trên một Windows PC; trạng thái và số task có bằng chứng xem [task tracker](tasks.md) và [partial acceptance](evidence/m2-acceptance-report.md). Không tự đóng M1 Gate 1 hoặc coi loopback là hai máy LAN vật lý.

## 2. User Scenarios & Testing *(mandatory)*

### User Story 1 — Xác minh peer và mở đúng hội thoại (Priority: P1)

Là người dùng muốn nói chuyện với đồng nghiệp cùng LAN, tôi muốn biết mình đang chat với **thiết bị nào đã được xác minh**, thay vì chỉ nhìn tên/UUID tự khai, để tránh gửi nhầm hoặc lộ nội dung cho peer giả mạo.

**Why this priority:** M1 cố ý không xác thực và chỉ cho phép diagnostic; đây là **điều kiện bắt buộc trước user chat**.

**Independent Test:** Chạy hai peer đã thiết lập kết nối M1; chưa ghép cặp thì nút gửi thật không khả dụng. Thực hiện quy trình xác minh peer trên hai máy, gửi thử; sau đó đổi khóa danh tính/đưa peer giả và kiểm tra bị chặn.

**Acceptance Scenarios:**

1. **Given** A và B mới chỉ hoàn tất `HELLO_READY` của M1, **When** A chọn hội thoại B, **Then** UI ghi `Chưa xác minh — không thể gửi tin thật`, không sinh `CHAT_TEXT` trên mạng.
2. **Given** A và B chưa từng ghép cặp, **When** hai người so khớp mã/fingerprint ở lần đầu, xác nhận đúng danh tính và kênh bảo vệ hoạt động, **Then** hội thoại mới mở quyền gửi cho **peer identity đã được xác minh**, đồng thời ghi nhớ credential/fingerprint để đối chiếu về sau (CL-03-01).
3. **Given** người nhận từ chối kết nối hoặc người dùng không xác minh danh tính, **When** bên kia cố gửi, **Then** tin không được truyền; UI giữ nội dung nháp và báo lý do.
4. **Given** hai peer cùng `displayName` nhưng khác identity/key, **When** mở danh sách hội thoại, **Then** không gộp làm một; tên không được dùng làm khóa.
5. **Given** peer được ghép cặp trước đó xuất hiện với khóa nhận dạng **khác**, **When** tái kết nối, **Then** không tự tin tưởng khóa mới, không chuyển chat sang kênh mới cho đến khi người dùng xác minh lại; cảnh báo có thể hiểu.
6. **Given** A khởi động lại sau khi ghép cặp thành công, **When** B sử dụng lại đúng khóa đã xác minh và A chủ động kết nối/duyệt kết nối theo M1, **Then** danh tính được đối chiếu từ thông tin thiết bị đã ghi nhớ mà không cần so khớp fingerprint lần đầu nữa; A không tự động chấp nhận inbound và không khôi phục tin cũ.

### User Story 2 — Gửi và nhận văn bản trực tiếp (Priority: P1)

Là người dùng có phiên chat hợp lệ với peer trong LAN, tôi muốn soạn, gửi và nhận tin nhắn văn bản thật hai chiều để liên lạc trực tiếp mà không cần Internet.

**Why this priority:** Năng lực tối thiểu của P2P Chat MVP.

**Independent Test:** Hai peer **được xác minh** ở hai process; A gửi văn bản cho B, B phản hồi, kiểm tra bản tin thực trên TCP đã bảo vệ, đúng conversation, đúng nội dung và thứ tự trong một phiên.

**Acceptance Scenarios:**

1. **Given** A và B đã được xác minh, chấp nhận kết nối đến và có kênh bảo vệ còn hoạt động, **When** A nhập tin hợp lệ và bấm `Gửi`, **Then** B thấy đúng văn bản trong hội thoại với A, không phải tin mock hoặc echo chỉ trên A.
2. **Given** B đã nhận tin của A, **When** B phản hồi, **Then** A nhận tin trong cùng hội thoại P2P; UI phân biệt chiều gửi/nhận.
3. **Given** ô soạn rỗng/toàn khoảng trắng hoặc nội dung vượt giới hạn, **When** người dùng bấm gửi, **Then** không tạo wire message, có phản hồi phù hợp và không mất nháp ngoài ý muốn.
4. **Given** A đang xem hội thoại C, **When** tin đến từ B, **Then** tin được gắn vào **hội thoại B**, không chèn vào luồng C hoặc gán nhầm sender.
5. **Given** A gửi liên tiếp nhiều tin qua một kênh ổn định, **When** B nhận, **Then** các tin không bị nhân đôi do parser/ứng dụng; quy tắc thứ tự trong *một phiên* được giữ nhất quán. Không ngầm cam kết thứ tự toàn cục qua reconnect.

### User Story 3 — Hiển thị đúng Sender, Time và Emoji (Priority: P1)

Là người dùng, tôi muốn biết ai đã gửi, khi nào và đọc đúng tiếng Việt/emoji để hiểu ngữ cảnh hội thoại mà không bị nhầm người hoặc lỗi mã hóa.

**Why this priority:** Là 3 trong 5 năng lực chính của M2 và là nguyên tắc Constitution II, VI.

**Independent Test:** Hai peer trùng tên; một peer đổi display name; gửi chuỗi UTF-8 tiếng Việt dấu kết hợp, emoji nhiều code point và tin có timestamp UTC; đổi múi giờ máy hiển thị.

**Acceptance Scenarios:**

1. **Given** A/B có tên hiển thị giống nhau nhưng danh tính xác minh khác, **When** tin đến, **Then** sender/conversation dựa theo identity, không dựa vào tên hoặc màn hình đang mở.
2. **Given** tin có `messageId`, sender ID và `sentAt` UTC hợp lệ, **When** hiển thị, **Then** UI ghi mốc giờ theo timezone/locale hiện tại và vẫn gắn đúng tin, đúng người.
3. **Given** hai đồng hồ máy lệch nhau, **When** hiển thị tin, **Then** không mặc định coi timestamp máy gửi là bằng chứng thứ tự nhân quả/toàn cục; lỗi định dạng giờ được xử lý rõ.
4. **Given** nội dung gồm `Xin chào Việt Nam`, emoji đơn, emoji đa code point và ký tự xuống dòng hợp lệ, **When** truyền/hiển thị, **Then** nội dung không mất hoặc bị thay thế do decode/encode. Nếu font không hỗ trợ, hiển thị fallback thay vì âm thầm làm sai bytes.
5. **Given** một peer đổi `displayName`, **When** có tin tiếp theo, **Then** identity hội thoại không đổi; UI vẫn phân biệt được tên hiển thị và định danh peer.

### User Story 4 — Hiểu chính xác trạng thái gửi, lỗi và kết nối (Priority: P1)

Là người dùng, tôi muốn biết tin đang gửi, đã rời máy, đã được ứng dụng bên kia xác nhận hay chưa và nên làm gì khi kết nối đứt, thay vì thấy `Đã nhận/Đã xem` giả.

**Why this priority:** Dữ liệu thật có thể lỗi giữa chừng; thông tin trạng thái trung thực quan trọng hơn giao diện trông hoàn chỉnh.

**Independent Test:** Kiểm thử socket đóng trước gửi, đóng sau gửi nhưng trước ACK, ACK hợp lệ/ACK giả/ACK trùng, **read receipt hợp lệ/giả/trùng/đến trước ACK**, xem tin trong hội thoại foreground vs preview/background, reconnect thủ công, retry cùng message ID, UI EDT responsiveness.

**Acceptance Scenarios:**

1. **Given** B không kết nối/không xác minh, **When** A nhấn gửi, **Then** không phát payload chat, hiển thị `Chưa kết nối` hoặc `Chưa xác minh`, giữ nháp để A sửa hoặc thử lại.
2. **Given** kết nối sụp trong lúc gửi, **When** hệ thống phát hiện thất bại, **Then** A không thấy `Đã nhận` hoặc `Đã xem`; phân biệt lỗi chắc chắn với tình huống **không biết bên kia đã nhận hay chưa**.
3. **Given** tin gửi qua kênh hợp lệ, **When** ứng dụng B đã validate/đưa vào luồng tin trong phiên và phát **ACK ứng dụng** đúng `messageId`, **Then** A mới được hiển thị `Đã nhận bởi ứng dụng bên kia`. ACK **không** tự động tạo `Đã xem`.
4. **Given** ACK đến từ peer khác, sai phiên, sai ID, không có xác thực hoặc bị lặp, **When** xử lý, **Then** không được nâng trạng thái tin nhắn không liên quan.
5. **Given** người dùng bấm `Kết nối lại` sau rớt mạng, **When** kênh đã được xác minh lại theo chính sách, **Then** người dùng có thể **chủ động** gửi lại tin chưa xác nhận; bộ nhận loại tin trùng còn trong cửa sổ dedup được bảo đảm. Không tuyên bố exactly-once qua khởi động lại khi chưa lưu lâu dài.
6. **Given** UI đang chờ mạng, ACK hoặc read receipt, **When** người dùng mở hội thoại khác/cuộn/nhập văn bản, **Then** giao diện vẫn phản hồi, không block Swing EDT.
7. **Given** B nhận tin từ A nhưng chưa mở hội thoại B↔A, hoặc tin chỉ có trong notification/preview/khung hội thoại không được nhìn thấy, **When** B tiếp tục thao tác ở hội thoại khác hoặc app chạy nền, **Then** A **chưa** được thấy `Đã xem`.
8. **Given** B đang ở hội thoại B↔A trong cửa sổ hoạt động và tin đã thực sự được hiển thị trong vùng nội dung, **When** ứng dụng B phát sự kiện xem thực và gửi **read receipt có xác thực, đúng messageId/conversation/peer/session**, **Then** A được nâng trạng thái của **đúng tin đó** lên `Đã xem`; không nâng các tin khác do ACK nhận hoặc thứ tự arrival.
9. **Given** read receipt bị giả mạo, sai peer/conversation/session/messageId, đến lặp hoặc B chỉ nhận mà chưa xem, **When** A xử lý, **Then** không thay đổi trạng thái sai; khi receipt đến không theo thứ tự, không được hạ trạng thái tin đã xem.
10. **Given** B thực sự thấy tin nhưng kênh bảo vệ đã ngắt trước khi read receipt được gửi, **When** A không nhận được read receipt hợp lệ, **Then** A không được hiện `Đã xem`, B không tự xếp hàng/gửi read receipt sau reconnect; không có bảo đảm trạng thái xem vượt qua restart của M2.

### User Story 5 — Một giao diện Chat thật, tách biệt FE mock (Priority: P1)

Là người dùng, tôi muốn thấy danh sách peer, luồng tin, ô soạn, thời gian và trạng thái trong **một giao diện liên kết dịch vụ mạng thật**, không nhầm nội dung demo với tin đã gửi qua LAN.

**Why this priority:** CL-03-05 chốt **dùng UI Messenger FE cho chế độ Live**, vẫn giữ **Demo riêng biệt**. Hiện tại hai entrypoint còn độc lập; chỉ đổi nhãn mock không tạo thành chat MVP.

**Independent Test:** Khởi chạy bản M2, kết nối với peer thật, xác minh danh tính, gửi/nhận qua UI; tắt mạng/peer, kiểm tra trạng thái/nháp; mở FE demo độc lập và xác nhận không sinh traffic người dùng.

**Acceptance Scenarios:**

1. **Given** người dùng mở M2, **When** chưa có session xác minh, **Then** danh sách peer/chế độ chat hiển thị trạng thái thật (`Chưa kết nối`/`Chưa xác minh`), không giả online hoặc hiển thị tin mock như lịch sử thực.
2. **Given** người dùng chọn peer đã xác minh trong **Live mode sử dụng giao diện Messenger FE**, **When** gửi thành công và B nhận tin, **Then** tin mới xuất hiện trong luồng chat đúng peer và UI cập nhật từ network/service thật, không từ mock data.
3. **Given** người dùng nhấn Enter hoặc nút Gửi trong trạng thái hợp lệ, **When** gửi, **Then** chỉ **một** message được sinh; xuống dòng có thao tác riêng được mô tả trong UI.
4. **Given** ứng dụng đóng và khởi động lại, **When** người dùng trở lại M2, **Then** không hiển thị nội dung phiên cũ như **History bền vững**; nếu chỉ có lưu định danh ghép cặp, giải thích đó không phải lưu tin nhắn.
5. **Given** FE mock vẫn tồn tại cho demo, **When** người dùng mở chế độ **Demo** riêng, **Then** dữ liệu, password và trạng thái `Đã nhận/Đã xem` mô phỏng **không** được hiểu/đẩy sang **Live**, và người dùng nhìn thấy nhãn Demo rõ ràng.
6. **Given** người dùng đóng ứng dụng và mở lại, **When** xem **Live**, **Then** nội dung/nháp/read receipt trong phiên trước **không** được phục hồi, còn fingerprint/credential của peer đã xác minh trước đó vẫn được ghi nhớ an toàn theo CL-03-01 và có thể quản lý/thu hồi.

### User Story 6 — Chat trực tiếp được trên LAN không Internet (Priority: P2)

Là người triển khai IntraChat trong phòng máy, tôi muốn kiểm chứng trên hai máy Windows thực sự để biết MVP hoạt động ngoài loopback và không dựa vào dịch vụ cloud.

**Independent Test:** Hai máy Windows 11 x64 ở LAN được phép truy cập, Internet không khả dụng nhưng LAN còn kết nối; A↔B xác minh peer, gửi/nhận Unicode, mất kết nối/reconnect thủ công, kiểm chứng cleanup và evidence trên **cả hai** máy.

**Acceptance Scenarios:**

1. **Given** hai Windows PC trong LAN, không có đường truy cập Internet, **When** A/B kết nối thủ công, duyệt và xác minh lẫn nhau, **Then** tin chat thật đi trực tiếp A↔B hai chiều.
2. **Given** một PC bị tắt/ngắt LAN, **When** PC còn lại thao tác chat, **Then** lỗi và trạng thái mạng phản ánh đúng sự cố; UI không treo, không báo đã nhận giả.
3. **Given** firewall hoặc version/protection mismatch, **When** thiết lập session chat, **Then** từ chối an toàn với chẩn đoán hữu ích; không hạ cấp sang plaintext `CHAT_TEXT`.
4. **Given** phần mềm tắt bình thường, **When** đóng cửa sổ, **Then** các socket, tác vụ và dữ liệu nhạy cảm tạm được xử lý theo chính sách đã công bố, không còn tiến trình mồ côi.

## 3. Edge Cases

- Nhiều peer dùng **cùng display name**, một peer đổi tên/IP/cổng; một endpoint tự khai UUID của peer khác.
- Tên/ID được dùng trong session M1 nhưng **chưa xác minh key**; người dùng Accept mạng nhưng **không** chấp nhận ghép cặp.
- Peer giả/đổi khóa sau ghép cặp, key mismatch hoặc downgrade version; ngắt/đổi khóa ngay giữa lúc gửi.
- Peer A/B cùng chủ động mở kết nối; hai phiên trùng identity; một peer bị loại/đóng trong khi UI đang hiển thị hội thoại.
- Tin đi đến trước khi peer được xác minh, sau khi đã disconnect, sai destination hoặc trên session chưa hoàn thành xác thực; phải từ chối **trước khi xử lý nội dung**.
- Tin không có text, toàn whitespace, quá giới hạn, ký tự điều khiển bất hợp lệ, Unicode surrogate hỏng, văn bản đa dòng, emoji ZWJ/skin tone/combining.
- JSON type/version sai, frame 0 byte/quá dài/cắt đôi, duplicate JSON keys, invalid UTF-8, trường sender/time/ID bị thiếu hoặc bị giả mạo.
- B nhận tin nhưng A mất ACK; A gửi lại cùng `messageId`; ACK đến trước khi local UI chờ ACK, ACK lặp, ACK từ sai peer.
- B chỉ có notification/preview hoặc ứng dụng đang ở nền, tin ngoài viewport, tin chưa được mở ở hội thoại đúng: **không** được tạo sự kiện `Đã xem`.
- B thực sự mở hội thoại và thấy tin, nhưng read receipt mất hoặc đến A trước ACK nhận; receipt bị gửi lặp, không đúng peer/session/messageId, hoặc A tắt UI giữa lúc receipt tới. **Nếu link offline khi xem, không có outbox read receipt và A không được hiện `Đã xem` khi chưa nhận receipt.**
- B đã xem tin, nhưng A gửi lại cùng `messageId`; bộ nhận phải tránh phát sự kiện xem không giới hạn và không tự nâng nhầm trạng thái tin khác.
- Đồng hồ hai máy lệch nhiều phút/giờ, múi giờ đổi khi đang chạy, timestamp quá khứ/tương lai, DST và 12/24 giờ.
- B offline, B đóng app giữa lúc gửi, socket half-open, hệ điều hành sleep, đổi Wi-Fi/IP, kết nối lại thủ công chậm.
- User mở hai hội thoại cùng lúc; tin đến hội thoại khác, thay đổi lựa chọn trước khi callback bất đồng bộ về.
- CL-03-02/04: Người dùng giữ bản nháp khi peer offline, rồi đóng app: **nháp và nội dung tin của phiên mất**, không tự gửi ở lần khởi động kế tiếp; UI cảnh báo khi đóng nếu còn nháp chưa gửi.
- CL-03-01: khởi động lại phải nhận ra fingerprint/credential đã ghép cặp (không phục hồi tin cũ); khi khóa đổi/credential bị xóa hoặc thu hồi, yêu cầu so khớp mã lại, nhưng inbound vẫn cần người dùng Chấp nhận theo CL-02-02.
- CL-03-05: giao diện Messenger FE cung cấp **Live mode mạng thật** và **Demo riêng**; trạng thái `sent/delivered/read` của demo không lọt vào Live. Trong Live, `Đã nhận` bắt nguồn từ ACK ứng dụng, `Đã xem` bắt nguồn từ read receipt sau sự kiện hiển thị thật ở peer đã xác minh.
- Dùng hai máy thật nhưng không Internet; một máy bị Windows Firewall chặn TCP; không được tự mở firewall ra Public profile.
- Ghi lỗi/telemetry hoặc crash dump không được chứa nội dung chat/secret/key; người dùng không được dùng mật khẩu demo như real auth.

## 4. Requirements *(mandatory)*

### 4.1. Functional Requirements

**Topology, session và dependency**

- **FR-001:** M2 **MUST** cho phép hai Windows peer trao đổi chat văn bản/emoji **trực tiếp qua LAN** không cần public Internet/server.
- **FR-002:** M2 **MUST** kế thừa **IP/port thủ công, listener do người dùng bật, từng inbound có Chấp nhận/Từ chối, không auto-discovery, không auto-reconnect** từ CL-02-01…04.
- **FR-003:** Các phần chat thật **MUST NOT** được kích hoạt trên kết nối M1 chưa xác thực/chưa mã hóa; nâng cấp giao thức/kênh cho chat cần versioning và kiểm soát hợp lệ, không bỏ whitelist M1 để lách gate.
- **FR-004:** Bản M2 **MUST NOT** công bố Verified khi M1 Gate 1 chưa Verified hoặc khi không có bằng chứng hai PC thật, các test bảo mật và nhận/gửi theo AC-03.
- **FR-005:** Một thiết bị **MUST** được biểu diễn bằng peer identity ổn định, tách khỏi `displayName`, IP/cổng và thông tin màn hình đang mở.

**Xác minh danh tính và bảo vệ dữ liệu người dùng**

- **FR-006:** Trước khi gửi/đọc `CHAT_TEXT` thật, hệ thống **MUST** bảo đảm hai đầu dùng **kênh mã hóa có xác thực và kiểm tra toàn vẹn**, được kiểm thử, không có đường downgrade sang plaintext; M1 chỉ còn được dùng cho diagnostic/khởi tạo có giới hạn.
- **FR-007:** Khi **ghép cặp lần đầu**, người dùng ở hai phía **MUST** chủ động so khớp **mã xác minh/fingerprint** theo quy trình an toàn được `plan.md` xác định; sau khi xác minh thành công, ứng dụng **MUST** ghi nhớ fingerprint/credential liên kết với đúng peer identity ở user-local để đối chiếu khi chạy lại. Chỉ bấm Accept M1, biết IP hoặc tin displayName/UUID **MUST NOT** tạo trust; những lần inbound sau vẫn cần human Accept/Reject theo CL-02-02 (CL-03-01).
- **FR-008:** Session chat **MUST** liên kết peer identity đã xác minh với key/credential của kênh, và `senderId` của tin nhận **MUST** khớp peer đã xác minh; dữ liệu `sender` tự khai không được thay thế kết quả xác thực.
- **FR-009:** M2 **MUST** so khớp credential/fingerprint đã ghi nhớ khi tái kết nối; nếu peer đổi key, ghép cặp bị thu hồi hoặc xác minh thất bại, hệ thống **MUST** chặn tin thật, cảnh báo và yêu cầu người dùng chủ động so khớp lại mã trước khi cập nhật trust. Không tự chấp nhận key mới hoặc inbound, không hiển thị `Trusted`/`Secure` giả.
- **FR-010:** Secrets/keys, mật khẩu (nếu xuất hiện), chat content, bản nháp và nội dung private **MUST NOT** đi vào log thông thường, crash evidence hoặc Git. **Thông tin xác minh/trust đã ghép cặp MUST được ghi nhớ qua restart** theo CL-03-01, tách khỏi nội dung chat theo CL-03-04; `plan.md` phải chốt user-local protected storage/OS permissions, vòng đời khóa, xóa/thu hồi trust, lỗi lưu và test không rò rỉ.
- **FR-011:** Inbound data, ACK và version/peer metadata đều là untrusted cho tới khi kiểm tra schema, kênh và quyền truy cập; parser **MUST NOT** dùng Java native object deserialization.

**Hội thoại và dữ liệu tin nhắn**

- **FR-012:** Một P2P conversation **MUST** ánh xạ tới **đúng một peer identity đã xác minh**, không gộp theo display name hoặc địa chỉ IP.
- **FR-013:** `CHAT_TEXT` **MUST** có hợp đồng phiên bản rõ ràng gồm `messageId`, `senderId`, `destination peerId`, `sentAt` UTC, text UTF-8 và dữ liệu phiên/kiểm tra phù hợp; các field phải được validate trước khi gửi/nhận.
- **FR-014:** UI **MUST** cho người dùng soạn và gửi text/emoji Unicode qua kênh chat thật; dữ liệu phải được bảo toàn UTF-8. Không gửi rỗng/toàn whitespace; chiều dài bytes và số lượng request phải **có giới hạn hữu hạn** chốt trong `plan.md`.
- **FR-015:** Tin nhận **MUST** được gắn đúng sender đã xác minh và đúng conversation/destination; UI **MUST NOT** suy luận sender bằng bubble alignment hay active conversation.
- **FR-016:** Hệ thống **MUST** định nghĩa rõ thứ tự tin **trong một phiên kết nối** và xử lý dedup theo `messageId`; không ngầm cam kết exactly-once hoặc ordering xuyên restart khi M2 chưa có History durable.
- **FR-017:** Nếu một tin hợp lệ đến khi người dùng đang xem conversation khác, ứng dụng **MUST** cập nhật/đánh dấu đúng conversation đích và không chèn vào luồng đang chọn.
- **FR-018:** Ngoài `CHAT_TEXT`, giao thức M2 **MUST** phân biệt **application delivery ACK** và **read receipt** (hai loại sự kiện khác nhau), mỗi thông điệp có correlation `messageId`, `conversationId`, binding với verified peer, phiên/kênh được bảo vệ, thứ tự chuyển trạng thái và quy tắc replay/dedup; ACK/receipt từ peer/session khác hoặc sai message ID **MUST NOT** nâng trạng thái tin. Wire type/schema, thời hạn và xử lý out-of-order được chốt có test ở `plan.md` (CL-03-03).
- **FR-019:** M2 **MUST** không tiếp nhận hoặc xử lý payload Group/File/History/Media bằng logic chat chỉ vì chung transport; mỗi loại cần spec/version/ủy quyền riêng.
- **FR-020:** **CL-03-04:** ChatMessage, conversation transcript, bản nháp, delivery/read receipts và trạng thái gửi M2 **MUST** chỉ lưu **trong bộ nhớ của phiên ứng dụng**; đóng ứng dụng thì mất, không ghi bền vững, không khôi phục hoặc tự sync sau restart. **Ngoại lệ phân biệt rõ:** fingerprint/credential/trust metadata từ CL-03-01 **MUST** tồn tại qua restart trong kho được bảo vệ nhưng **không chứa nội dung chat**. History Sync vẫn thuộc 005.

**Sender, Time, State, Emoji**

- **FR-021:** Mỗi tin hiển thị **MUST** có chiều gửi/nhận, định danh/tên sender đúng ngữ cảnh và thời gian dễ đọc; trùng display name **MUST NOT** gây nhầm identity.
- **FR-022:** Wire timestamp **MUST** là UTC theo hợp đồng; UI **MUST** chuyển đổi rõ ràng sang timezone/locale trên máy người dùng; timestamp remote **MUST NOT** được coi là bằng chứng chính xác tuyệt đối hay thứ tự nhân quả.
- **FR-023:** Trạng thái kết nối (disconnected/connecting/connected) **MUST** tách khỏi trạng thái xác minh peer (unverified/verified/changed-key) và trạng thái tin (**draft/sending/local-sent/app-acknowledged/read/failed/uncertain**). `READ` chỉ nâng sau read receipt hợp lệ; delivery ACK/hiển thị tại phía gửi không phải đã xem (CL-03-03).
- **FR-024:** **CL-03-03:** `Đã gửi từ máy này` chỉ hiển thị sau sự kiện gửi cục bộ có bằng chứng; `Đã nhận bởi ứng dụng bên kia` **MUST** cần application ACK hợp lệ; **`Đã xem` MUST được hỗ trợ** nhưng **CHỈ** khi ứng dụng peer đã xác minh thực sự hiển thị **đúng tin trong vùng nhìn thấy của hội thoại đang mở/foreground**, rồi gửi **read receipt riêng** qua kênh bảo vệ, phía gửi kiểm tra đúng peer/conversation/message/session. UI Live **MUST** công khai rằng sự kiện hiển thị có thể gửi `Đã xem` cho peer. Không suy ra `Đã xem` từ TCP send, delivery ACK, tin tới nền, preview/notification, chọn nhầm conversation hoặc mock data; read receipt phản ánh *hiển thị trên UI*, không chứng minh người dùng đã đọc bằng mắt.
- **FR-025:** Nếu không biết tin đã tới vì mất kết nối sau gửi nhưng trước ACK, UI **MUST** biểu diễn trạng thái không chắc chắn, không chuyển thẳng sang thành công hay thất bại chắc chắn.
- **FR-026:** Unicode/emoji nhiều code point, tiếng Việt và ký tự newline hợp lệ **MUST** bảo toàn dữ liệu đầu vào qua encode/receive/render; trường hợp font thiếu glyph phải có fallback/hướng dẫn rõ.
- **FR-027:** **CL-03-02:** Khi offline, payload **chưa gửi** MUST ở dạng **nháp trong bộ nhớ phiên**, không được tự chuyển vào outbox hay tự gửi/resend. Sau khi người dùng chủ động reconnect và phiên đã được xác minh/bảo vệ, **người dùng phải chủ động bấm Gửi/Gửi lại**; `messageId`/dedup/delivery ACK/read receipt phải tránh nhân đôi tin hoặc nâng sai trạng thái trong cửa sổ bảo đảm của phiên. Đóng ứng dụng làm mất nháp; phải có cảnh báo phù hợp trước khi thoát nếu còn nháp.

**UI, hiệu năng, lỗi và lifecycle**

- **FR-028:** **CL-03-05:** Giao diện Messenger **FE** được dùng làm giao diện **Live Chat thật**: MUST có danh sách peer/conversation, luồng tin, composer/nút gửi, trạng thái kết nối/xác minh/`Đã gửi`/`Đã nhận`/`Đã xem` và lỗi rõ ràng. **Demo mode riêng** vẫn được duy trì và ghi nhãn; không thay thế FE bằng bảng diagnostics M1 làm UI chat chính.
- **FR-029:** **Live FE** MUST nhận tin, delivery ACK và read receipt **thực** qua application/network service; **Demo FE** vẫn dùng mock service riêng với nhãn rõ. Hai chế độ MUST cách ly nội dung/identity/login/state: demo password và `read` giả **không** được đưa vào Live, Live `Đã xem` chỉ theo FR-024, không dùng mock read flag.
- **FR-030:** Không thao tác socket, crypto, parse lớn hoặc lưu dữ liệu trên Swing EDT; callback UI **MUST** chuyển về EDT, hủy/close **MUST** dọn socket, executor, timeout/ACK task.
- **FR-031:** Khi remote offline/refuse/timeout, user **MUST** thấy nguyên nhân/động tác tiếp theo; nháp chưa truyền **MUST** được giữ trong bộ nhớ phiên và **MUST NOT** bị coi là đã gửi. Không tự chuyển nháp thành tin chờ gửi; người dùng tự gửi lại sau khi chủ động kết nối lại, và được cảnh báo rằng nháp mất khi đóng ứng dụng.
- **FR-032:** Hệ thống **MUST** có giới hạn kích thước tin, số thao tác, hàng đợi ACK và bộ nhớ/dedup; khi vượt giới hạn **MUST** báo lỗi có kiểm soát, không treo hoặc chiếm bộ nhớ vô hạn.
- **FR-033:** Việc kết nối lại **MUST** do người dùng kích hoạt; khi tái kết nối phải xác minh lại tính hợp lệ kênh/credential và thực hiện consent theo M1, không tự tin tưởng chỉ dựa theo IP.
- **FR-034:** Giao diện M2 **SHOULD** hỗ trợ keyboard navigation và thông báo trạng thái dễ đọc; Enter/nút Gửi không sinh 2 lần gửi, nội dung nhiều dòng có thao tác được ghi trong hướng dẫn.
- **FR-035:** Các trường hợp lỗi parser, version không tương thích, remote đóng giữa frame và ACK không hợp lệ **MUST** fail closed, không đưa dữ liệu hỏng vào hội thoại.

**Testing và bàn giao**

- **FR-036:** M2 **MUST** có unit tests cho schema, **first-time fingerprint matching/pinning + restart/changed-key/revoke**, Unicode, timestamp/timezone, delivery ACK **tách biệt read receipt**, không xem khi tin chỉ ở nền/preview/ngoài viewport, read receipt đúng/sai/trùng/out-of-order, nháp trong phiên, invalid input, cleanup và **FE Live/Demo isolation**.
- **FR-037:** M2 **MUST** có integration tests tối thiểu hai process/peer local và **hai máy Windows LAN thật** với A↔B text/emoji, xác minh fingerprint lần đầu và ghi nhớ sau restart, explicit Accept cho kết nối đến, **delivery ACK rồi read receipt sau UI view event thật**, offline draft/manual retry, Live/Demo FE tách biệt, Internet không khả dụng, Reject và disconnect/retry.
- **FR-038:** Security tests **MUST** chứng minh không truyền plaintext chat trong M1, không gửi trước peer verification, không downgrade, fingerprint/key thay đổi bị chặn, giả mạo sender/key/**ACK/read receipt** (khác peer/session/message, replay) bị từ chối; **trust/credential được bảo vệ nhưng nội dung chat/receipt không persist**, dữ liệu nhạy cảm không xuất hiện trong log.
- **FR-039:** Bằng chứng nghiệm thu **MUST** ghi runtime JDK21, Maven Wrapper, exit codes, số test, tình trạng hai PC, thời gian và trạng thái quan sát thực; **MUST NOT** đánh dấu Verified khi chưa thực thi.
- **FR-040:** Các thay đổi protocol/crypto/integration FE **MUST** được mô tả trong `plan.md` (bao gồm Constitution Check I–VII), chia thành `tasks.md` test-first trước khi code.

### 4.2. Key Entities & State Model *(logical, not storage schema)*

| Entity | Trường logic tối thiểu | Bất biến |
| --- | --- | --- |
| `VerifiedPeerIdentity` | deviceId, displayName, verified credential/key reference, fingerprint pin/trust status | So khớp lần đầu; lưu trust credential qua restart **không kèm nội dung chat**; key change khóa chat |
| `PeerChatSession` | localId, remoteVerifiedId, transport state, security state, sessionId, negotiatedVersion | `CONNECTED` mạng ≠ `VERIFIED`; chỉ authenticated+protected mới cho chat |
| `P2PConversation` | conversationId, localPeerId, remotePeerId | Peer identity ổn định, không gộp theo tên |
| `ChatMessage` | messageId, conversationId, senderId, destinationId, sentAtUTC, text, direction | Nội dung là text Unicode; sender/destination phải khớp phiên xác thực |
| `MessageAttempt` | messageId, local state, outstanding ACK correlation, error/uncertainty reason | State giao nhận không lẫn presence; ACK không chứng minh human read |
| `MessageReceipt` | messageId, conversationId, receiver verified ID, session correlation, acceptedAt | **Delivery ACK**: ứng dụng nhận đã xử lý; không phải `read` |
| `ReadReceipt` | messageId, conversationId, verified viewer identity, session binding, viewedAt | Chỉ sinh sau **UI view event** của tin đúng hội thoại trong foreground/viewport; phải kiểm tra authenticated binding, dedup/replay; **không** là bằng chứng người thật đã đọc |
| `ConversationView` | selectedPeerId, mode (FE Live/FE Demo), transient messages, draft, viewport/visibility and focus state | Live ≠ FE mock; sinh view event chỉ khi tin thực được hiển thị; nội dung/draft/receipt không persist |
| `TrustedPeerRecord` | verified peer identity, pinned fingerprint/key reference, trust/revocation metadata | **Ghi nhớ qua restart** để nhận diện đúng peer; được bảo vệ và tách biệt với History/ChatMessage |

**Trạng thái đề xuất** (tên enum cuối cùng do plan chốt):

```text
Network:   DISCONNECTED → CONNECTING → CONNECTED → DISCONNECTED/FAILED
Consent:   PENDING → ACCEPTED / REJECTED / EXPIRED
Identity:  UNVERIFIED → VERIFYING → VERIFIED / CHANGED_KEY / REVOKED
Security:  UNPROTECTED → NEGOTIATING → PROTECTED / FAILED
Message:   DRAFT → SENDING → LOCAL_SENT → APP_ACKNOWLEDGED → READ (valid view receipt)
                       └──→ UNCERTAIN / FAILED
Read event: peer displays message visibly in active conversation → authenticated READ_RECEIPT
            (not from background arrival, delivery ACK, preview or demo)
```

**Luật chặn:** `CHAT_TEXT` chỉ khi **network CONNECTED + explicit inbound approval + identity VERIFIED + security PROTECTED + agreed message protocol version**. `DIAGNOSTIC_PING/PONG` trong M1 không tự nâng cấp bất kỳ trạng thái nào thành xác minh người gửi.

### 4.3. Non-Functional Requirements

- **NFR-001 — Security first:** M2 không gửi dữ liệu người dùng trên plaintext M1; mã hóa/xác thực/khóa có test và review riêng; không ghi secret/content vào routine log; không dùng demo login làm auth.
- **NFR-002 — Responsiveness:** UI Swing không block chờ mạng/ACK/key verification; `plan.md` phải chốt các timeout, resource caps, cancel/cleanup và ngân sách responsiveness có thể đo.
- **NFR-003 — Data correctness:** UTF-8 lossless cho text hợp lệ và emoji; sender ID, timestamp UTC, đích, ACK nhận và **read receipt gắn đúng message/peer/conversation/session**. Không phát/suy đoán `Đã xem` từ nhận mạng hoặc read mock.
- **NFR-004 — Availability:** Hai Windows peer LAN giao tiếp khi public Internet không khả dụng; không có dependency runtime bắt buộc vào GitHub/cloud/remote auth.
- **NFR-005 — Bounded memory:** Số peer/session, pending messages, dedup window và message bytes đều hữu hạn; giảm tải/fail nhanh và có thông báo.
- **NFR-006 — Accessibility & discoverability:** Controls gửi/chuyển chat/kết nối/hủy có nhãn, focus/keyboard hợp lý và trạng thái không dựa hoàn toàn vào màu.
- **NFR-007 — Compatibility:** M1 diagnostic peers không hiểu chat extension phải từ chối có kiểm soát; không gửi `CHAT_TEXT` cho parser v1-only; plan phải xác định version negotiation/migration.
- **NFR-008 — Privacy of persistence:** **CL-03-01/04:** fingerprint/credential đã ghép cặp **được lưu an toàn qua restart** ở user-local với thao tác quản lý/thu hồi; **chat content, draft, delivery/read receipts chỉ trong RAM phiên**, không ghi bền vững/log/Git. History durable vẫn thuộc 005.

## 5. Acceptance Criteria (AC-03-A…L)

| AC | Kết quả bắt buộc để nghiệm thu M2 | Stories / Requirements |
| --- | --- | --- |
| **AC-03-A** | A gửi tin thật cho B, B hiển thị **đúng text/sender/time** trong conversation tương ứng | US2–3; FR-012–017, FR-021–022 |
| **AC-03-B** | B trả lời cho A; hai phía đúng chiều gửi/nhận trên **hai máy LAN** | US2, US6; FR-001, FR-037 |
| **AC-03-C** | Vietnamese/emoji nhiều code point qua mạng giữ nguyên nội dung, đúng UTF-8 | US3; FR-014, FR-026 |
| **AC-03-D** | B offline/ngắt giữa gửi: nháp ở A được giữ **trong phiên**; A phải chủ động reconnect rồi bấm Gửi lại; không có auto outbox/resend và UI không treo | US4; CL-03-02; FR-025, FR-027, FR-030–033 |
| **AC-03-E** | **Có `Đã xem` từ sự kiện UI xem thật (CL-03-03):** nhận tin/ACK không đủ; B phải hiển thị đúng tin trong vùng nhìn thấy của hội thoại Live đang hoạt động rồi gửi **authenticated read receipt riêng**; A chỉ hiển thị `Đã xem` cho đúng tin sau khi kiểm tra receipt. Chỉ preview/notification/background hoặc mock Demo không tạo `Đã xem`. Đây là bằng chứng **hiển thị trên ứng dụng**, không chứng minh người dùng đọc bằng mắt | US4–5; FR-018, FR-023–024, FR-028–029 |
| **AC-03-F** | **Ghép cặp lần đầu phải so khớp mã/fingerprint**, lưu trust credential bảo vệ để đối chiếu **sau restart**; peer đổi key/không khớp bị chặn và yêu cầu xác minh lại. Không `CHAT_TEXT` khi chưa xác minh/kênh chưa protected; mỗi inbound vẫn cần human Accept | US1; CL-03-01; FR-003, FR-006–011, FR-038 |
| **AC-03-G** | App ACK hợp lệ mới nâng `APP_ACKNOWLEDGED`; **read receipt hợp lệ và event xem thật** mới nâng `READ/Đã xem`. ACK/receipt sai, giả, trùng, khác peer/conversation/session, đến đảo thứ tự hoặc `messageId` không tương ứng không được nâng/hạ trạng thái sai; dedup trong phạm vi phiên | US4; CL-03-03; FR-016, FR-018, FR-023–024, FR-027 |
| **AC-03-H** | Hai peer trùng tên/đổi tên, tin đến khi đang xem hội thoại khác vẫn gắn đúng ID; timestamp hiển thị đúng timezone | US2–3; FR-005, FR-012, FR-015, FR-021–022 |
| **AC-03-I** | **Messenger FE là giao diện Live Chat thật, Demo giữ riêng có nhãn**; Live dùng event/network thực cho tin, trạng thái `Đã nhận/Đã xem`. Đóng mở lại không có nội dung/draft/receipt lịch sử cũ nhưng **credential/fingerprint đã ghép cặp vẫn được ghi nhớ an toàn**; Demo không gửi traffic chat thật | US1, US5; CL-03-01/04/05; FR-007, FR-020, FR-028–029 |
| **AC-03-J** | Ít nhất **hai máy Windows 11 x64 thực**, không Internet nhưng LAN hoạt động: so khớp fingerprint lúc ghép lần đầu, chat hai chiều, delivery ACK, read receipt sau UI view event, manual retry, đồng thời consent kết nối đến đúng; bằng chứng trên cả hai | US1–6; CL-03-01…05; FR-001–004, FR-024, FR-037–039 |
| **AC-03-K** | Tests âm/timeout/Unicode/malformed/frame/key-downgrade/ACK/**read receipt**, foreground/viewport vs preview, changed-key after restart, no offline outbox, Live/Demo isolation, RAM-only chat/receipt và cleanup + regression M0/M1/FE đều pass; evidence ghi exit codes | US1–6; FR-006–010, FR-018, FR-020, FR-024, FR-027–029, FR-035–040 |
| **AC-03-L** | M1 Gate 1 đã Verified; Constitution I–VII có review không còn vi phạm MUST; README/Quickstart mô tả đúng giới hạn | US1–6; FR-004, FR-039–040 |

### Success Criteria — measurable outcomes (SC-03-001…010)

- **SC-03-001:** Tối thiểu **1 luồng A→B và 1 luồng B→A** trên **hai Windows PC riêng biệt**, với Internet không khả dụng mà LAN vẫn hoạt động; nội dung, sender và time đều đúng (AC-A/B/J).
- **SC-03-002:** **100%** mẫu Unicode/emoji/tiếng Việt trong bộ test đã công bố round-trip nguyên vẹn; không có ký tự thay thế do lỗi codec (AC-C).
- **SC-03-003:** **0** tin chat được gửi khi peer chưa xác minh hoặc kênh chưa được bảo vệ trong test âm/downgrade; cùng lúc không có leak plaintext chat trong packet capture kiểm thử đã được phê duyệt (AC-F).
- **SC-03-004:** **100%** tình huống UI view event thật có read receipt hợp lệ trong test được nâng đúng tin lên `Đã xem`; **0** `Đã nhận` khi không có ACK và **0** `Đã xem` từ ACK/preview/background/Demo hoặc receipt giả/trùng/sai peer/session/message (AC-E/G).
- **SC-03-005:** **100%** trường hợp kết nối đến bị Reject/timeout/đóng giữa chừng trong suite không gửi message chat, không khóa UI và dọn socket/worker theo policy (AC-D/F/K).
- **SC-03-006:** Tin nhắn của hai peer **trùng displayName** và các trường hợp đổi tên trong suite vẫn gắn đúng `peerId`/conversation; **0** nhầm sender (AC-H).
- **SC-03-007:** Tối thiểu **5 chu kỳ** tạo session xác minh→gửi/ACK→**xem thật/read receipt**→ngắt/cleanup có kiểm thử lặp lại ổn định trên local loopback; ít nhất **một** chu kỳ tương đương được ghi trên **hai PC thật** (AC-J/K).
- **SC-03-008:** Tất cả test đã đánh dấu PASS có bằng chứng thực thi trên actual JDK21; command thất bại trả nonzero, không thay thế một PC hai process cho two-device acceptance (AC-K/L).
- **SC-03-009:** Ứng dụng không cần Internet hoặc central server tại runtime khi user chat; firewall vẫn do người dùng/IT điều khiển, không tự mở Public profile (AC-J).
- **SC-03-010:** README và UI phân biệt rõ `CONNECTED`, `VERIFIED`, `APP_ACKNOWLEDGED`, **`READ` từ read receipt đúng UI event**, **FE Live vs Demo**, fingerprint được ghi nhớ riêng, **History/draft/receipt không persist**; không tuyên bố bảo mật hoặc người đã thực sự đọc bằng mắt khi chưa có bằng chứng (AC-E/I/L).

**Lưu ý đo lường:** Các số 100% và 0 áp dụng cho **tập ca test công bố**, không tuyên bố không thể có lỗi ngoài môi trường test. Timeout, giới hạn bytes, sức chứa queue, mục tiêu latency/EDT cụ thể phải được chốt theo đo đạc và đánh giá rủi ro trong `plan.md`.

## 6. Dependencies & out of scope

### Prerequisites / Gate

1. **M1 Gate 1** phải Verified trên hai Windows PC theo [acceptance M1](../002-lan-networking/evidence/m1-acceptance-report.md). Tạo spec/plan song song **không** cho phép tự đánh dấu M1 đã qua gate.
2. Phương thức xác minh identity, kênh mã hóa, trust/key rotation/revocation và protocol negotiation cho chat phải có **thiết kế và kiểm thử** trước khi chuyển dữ liệu người dùng.
3. **CL-03-05:** M2 **bắt buộc dùng Messenger FE làm giao diện Live Chat thật** và **giữ chế độ Demo riêng**. Cần tách `MockChatService` và live messaging service với identity/ACK/read receipt thật; không dùng LoginFrame/password demo làm authentication.
4. [plan.md](plan.md) và [tasks.md](tasks.md) đã tạo; **một phần code M2 và kiểm thử local JDK21 đã chạy**. Cần review dependency/bảo mật độc lập, hoàn tất task còn lại và M1 Gate 1 trước khi M2 được phát hành; xem [acceptance](evidence/m2-acceptance-report.md).

### Explicitly out of scope

- Nhóm nhiều người/membership/broadcast: **004**.
- Lưu/khôi phục lâu dài, đồng bộ lịch sử, multi-device offline merge và quyền conversation ACL: **005**; phải được thiết kế xác thực/ủy quyền riêng.
- Tệp và tải xuống: **006**; ảnh, video, âm thanh như nội dung file: **007**.
- Voice call, video call realtime, Cloud account, Internet relay/NAT traversal, auto-discovery, auto-reconnect, tự động đổi Firewall.
- **Read receipts không nằm ngoài phạm vi:** M2 **có** read receipt sau **sự kiện tin thực sự hiển thị trong vùng nhìn thấy của hội thoại foreground**, không chứng minh người thật đọc bằng mắt. Ngoài phạm vi vẫn gồm: xác minh bằng theo dõi mắt người/human cognition, push notification ngoài phiên, multi-device history ordering, durable outbox và delivery offline/auto resend.
- M1 diagnostic plaintext không được trở thành đường gửi chat production; FE mock không phải backend chat.

## 7. Technical Design Questions for Plan (product Clarify CL-03-01…05 resolved)

| ID | Vấn đề cần quyết định trước khi implement | Ràng buộc không được phá |
| --- | --- | --- |
| **OD-03-01** | **Đã chốt behavior CL-03-01:** lần đầu so khớp mã/fingerprint, sau đó ghi nhớ peer. **Plan chỉ cần chốt HOW:** sinh/hiển thị mã, pinning bảo vệ ở user-local, khóa đổi/thu hồi, lỗi lưu và UX xác minh lại | Không dùng tên/IP/UUID tự khai hay Accept M1 làm authentication; không tự accept inbound |
| **OD-03-02** | Bảo vệ kênh và key lifecycle (ví dụ TLS mutual authentication hoặc cơ chế đã qua review); chống downgrade, ràng buộc session/ACK, compatibility v1→v2 | Không gửi chat plaintext/chưa xác thực, không lách whitelist M1 |
| **OD-03-03** | Giới hạn text UTF-8/frame/queue, thời hạn delivery ACK/**read receipt**, thứ tự/receipt đến trước ACK, cửa sổ dedup và sự kiện tin **thực sự hiển thị trong foreground/viewport** | Không hứa exactly-once lâu dài, không suy `Đã xem` từ ACK/preview/background, không treo EDT/memory |
| **OD-03-04** | **CL-03-02/03 đã chốt:** offline giữ draft trong RAM và gửi lại do người dùng bấm; `LOCAL_SENT/UNCERTAIN/APP_ACKNOWLEDGED/READ`, receipt out-of-order/expired, cảnh báo draft khi thoát | Không auto reconnect, outbox hay resend; chỉ `READ` nếu có view event + read receipt đúng |
| **OD-03-05** | **CL-03-05 đã chốt:** Messenger FE là UI **Live Chat**, **Demo riêng**. Plan chọn adapter/mapping `model.ChatMessage` ↔ M2 domain, cơ chế xác định viewport/foreground cho read event, navigation/accessibility | Không chuyển demo credentials/mock messages/read status vào Live |
| **OD-03-06** | **CL-03-01/04 đã chốt:** tin, nháp, delivery/read receipt chỉ trong RAM phiên; trust fingerprint/credential qua restart. Plan chọn protected storage, xóa/revoke trust, không khôi phục nội dung chat và cleanup RAM/secret | Không dựng History Sync 005 hoặc lưu riêng tư trái CL-03-04 |
| **OD-03-07** | Ngân sách thời gian phản hồi, timeout và khả năng nhiều peer; test security/soak và hai PC không Internet | Các số phải có căn cứ, gate thật không thể thay bằng loopback |

**Clarify đã hoàn tất:** Người dùng **xác nhận CL-03-01…05** vào 2026-11-21 tại §1.2. Không có quyết định hành vi nào trong năm mục này còn là phương án gợi ý. Các OD-03-* là câu hỏi **thiết kế kỹ thuật cho plan**, không mở lại pairing/manual resend/read receipt/RAM-only/FE Live + Demo. **M2 đang implement (15/90 tasks checked), chưa Verified; còn thiếu Gate1, bảo mật và 2 PC thật**; không hạ thấp bảo mật hoặc thay bằng loopback khi nghiệm thu hai PC thật.

## 8. Traceability to ROADMAP

| Mục ROADMAP | Requirements / evidence cần có |
| --- | --- |
| 03.01 — Hội thoại P2P | US1–2; FR-005, FR-012, FR-015, FR-017; AC-H |
| 03.02 — Text | US2; FR-013–014, FR-018; AC-A/B |
| 03.03 — UI hội thoại | **CL-03-05 FE Live + Demo riêng**; US5; FR-028–029, FR-034; AC-I |
| 03.04 — Sender | US3; FR-008, FR-015, FR-021; AC-A/H |
| 03.05 — Time | US3; FR-022; AC-A/H |
| 03.06 — State | **CL-03-03 `Đã xem` dựa trên read receipt sau view event**; US4; FR-018, FR-023–025; AC-D/E/G |
| 03.07 — Emoji | US3; FR-014, FR-026; AC-C |
| 03.08 — Gửi thất bại | **CL-03-02 draft trong phiên / gửi lại thủ công**, **CL-03-04 không lưu tin bền vững**; US4; FR-020, FR-025, FR-027, FR-031–033; AC-D/G |
| 03.09 — Automated tests | US1–6; FR-036–040; AC-J/K/L |

**M2 release gate:** Chỉ công bố `MVP Chat P2P — Verified` khi **M1 đã Verified**, protocol/security/UX đã qua review, **AC-03-A…L có bằng chứng thực thi**, và các nguyên tắc I–VII không có vi phạm MUST chưa được chấp thuận. File spec này **không** phải bằng chứng ứng dụng đã hỗ trợ gửi chat thật.
