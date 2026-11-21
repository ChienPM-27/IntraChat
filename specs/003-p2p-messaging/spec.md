# Feature Specification: Nhắn tin trực tiếp P2P — Giai đoạn 03 (M2)

**Feature Branch:** `003-p2p-messaging` (nhánh tài liệu, tạo từ `feat/m1-lan-networking`; không phải trạng thái đã triển khai)
**Created:** 2026-11-21
**Status:** **Draft / Specify — chưa implement, chưa plan, chưa tasks, chưa Verified**
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

**Đề xuất mặc định để có một MVP an toàn, CHƯA được coi là quyết định đã xác nhận của người dùng:**

- **P-03-01:** Trước tin chat thật, hai đầu phải có **xác minh peer bằng hành động rõ ràng** (ví dụ so khớp fingerprint/mã xác minh qua kênh độc lập) và **transport mã hóa có xác thực và bảo vệ toàn vẹn**. `plan.md` sẽ chốt phương án, vòng đời khóa, key rotation và việc ghép cặp; **không chọn thuật toán ngay trong spec**.
- **P-03-02:** Không có offline outbox tự gửi, không tự động resend, không đồng bộ lịch sử trong M2. Nếu peer offline, tin vẫn ở ô soạn/chưa gửi; người dùng được báo lỗi và tự quyết định thử lại sau khi chủ động kết nối lại.
- **P-03-03:** Có `Đang gửi`, `Đã gửi từ máy này`, `Đã nhận bởi ứng dụng bên kia`, `Gửi thất bại/Chưa rõ đã nhận` nếu có bằng chứng tương ứng; **không hiển thị `Đã xem` trong M2**. Nếu chưa triển khai ACK ứng dụng thì chỉ được hiển thị trạng thái gửi cục bộ và không thể ghi `Đã nhận`.
- **P-03-04:** Tin nhắn/hội thoại thực chỉ nằm trong **bộ nhớ phiên đang mở**, thoát app thì không còn lịch sử thực; cache mock của FE phải cách ly rõ. Lưu trữ dài hạn, History Sync, retention/deletion thuộc Giai đoạn 05.
- **P-03-05:** M2 chọn một **luồng chat thật** và một UI chat dễ dùng; có thể tái sử dụng FE FlatLaf, nhưng tuyệt đối không trộn tin/trạng thái mock với tin thật.

Các đề xuất trên là **giả định có thể sửa qua Clarify/plan**, không phải xác nhận ngầm. Mọi thay đổi làm giảm bảo vệ dữ liệu người dùng cần quyết định và đánh giá rủi ro công khai trước khi implement.

## 2. User Scenarios & Testing *(mandatory)*

### User Story 1 — Xác minh peer và mở đúng hội thoại (Priority: P1)

Là người dùng muốn nói chuyện với đồng nghiệp cùng LAN, tôi muốn biết mình đang chat với **thiết bị nào đã được xác minh**, thay vì chỉ nhìn tên/UUID tự khai, để tránh gửi nhầm hoặc lộ nội dung cho peer giả mạo.

**Why this priority:** M1 cố ý không xác thực và chỉ cho phép diagnostic; đây là **điều kiện bắt buộc trước user chat**.

**Independent Test:** Chạy hai peer đã thiết lập kết nối M1; chưa ghép cặp thì nút gửi thật không khả dụng. Thực hiện quy trình xác minh peer trên hai máy, gửi thử; sau đó đổi khóa danh tính/đưa peer giả và kiểm tra bị chặn.

**Acceptance Scenarios:**

1. **Given** A và B mới chỉ hoàn tất `HELLO_READY` của M1, **When** A chọn hội thoại B, **Then** UI ghi `Chưa xác minh — không thể gửi tin thật`, không sinh `CHAT_TEXT` trên mạng.
2. **Given** A và B xác minh danh tính bằng quy trình đã được `plan.md` phê duyệt, **When** hai người hoàn tất rõ ràng ở mỗi đầu và kênh bảo vệ nội dung hoạt động, **Then** hội thoại chỉ mở quyền gửi cho **peer identity đã được xác minh**.
3. **Given** người nhận từ chối kết nối hoặc người dùng không xác minh danh tính, **When** bên kia cố gửi, **Then** tin không được truyền; UI giữ nội dung nháp và báo lý do.
4. **Given** hai peer cùng `displayName` nhưng khác identity/key, **When** mở danh sách hội thoại, **Then** không gộp làm một; tên không được dùng làm khóa.
5. **Given** peer được ghép cặp trước đó xuất hiện với khóa nhận dạng **khác**, **When** tái kết nối, **Then** không tự tin tưởng khóa mới, không chuyển chat sang kênh mới cho đến khi người dùng xác minh lại; cảnh báo có thể hiểu.

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

**Independent Test:** Kiểm thử socket đóng trước gửi, đóng sau gửi nhưng trước ACK, ACK hợp lệ/ACK giả/ACK trùng, reconnect thủ công, retry cùng message ID, UI EDT responsiveness.

**Acceptance Scenarios:**

1. **Given** B không kết nối/không xác minh, **When** A nhấn gửi, **Then** không phát payload chat, hiển thị `Chưa kết nối` hoặc `Chưa xác minh`, giữ nháp để A sửa hoặc thử lại.
2. **Given** kết nối sụp trong lúc gửi, **When** hệ thống phát hiện thất bại, **Then** A không thấy `Đã nhận` hoặc `Đã xem`; phân biệt lỗi chắc chắn với tình huống **không biết bên kia đã nhận hay chưa**.
3. **Given** tin gửi qua kênh hợp lệ, **When** ứng dụng B đã validate/đưa vào luồng tin trong phiên và phát **ACK ứng dụng** đúng `messageId`, **Then** A mới được hiển thị `Đã nhận bởi ứng dụng bên kia`. ACK không mang nghĩa con người đã đọc.
4. **Given** ACK đến từ peer khác, sai phiên, sai ID, không có xác thực hoặc bị lặp, **When** xử lý, **Then** không được nâng trạng thái tin nhắn không liên quan.
5. **Given** người dùng bấm `Kết nối lại` sau rớt mạng, **When** kênh đã được xác minh lại theo chính sách, **Then** người dùng có thể **chủ động** gửi lại tin chưa xác nhận; bộ nhận loại tin trùng còn trong cửa sổ dedup được bảo đảm. Không tuyên bố exactly-once qua khởi động lại khi chưa lưu lâu dài.
6. **Given** UI đang chờ mạng hoặc ACK, **When** người dùng mở hội thoại khác/cuộn/nhập văn bản, **Then** giao diện vẫn phản hồi, không block Swing EDT.

### User Story 5 — Một giao diện Chat thật, tách biệt FE mock (Priority: P1)

Là người dùng, tôi muốn thấy danh sách peer, luồng tin, ô soạn, thời gian và trạng thái trong **một giao diện liên kết dịch vụ mạng thật**, không nhầm nội dung demo với tin đã gửi qua LAN.

**Why this priority:** Hiện tại FE demo và giao diện M1 là hai sản phẩm độc lập; chỉ đổi mock UI text không tạo thành chat MVP.

**Independent Test:** Khởi chạy bản M2, kết nối với peer thật, xác minh danh tính, gửi/nhận qua UI; tắt mạng/peer, kiểm tra trạng thái/nháp; mở FE demo độc lập và xác nhận không sinh traffic người dùng.

**Acceptance Scenarios:**

1. **Given** người dùng mở M2, **When** chưa có session xác minh, **Then** danh sách peer/chế độ chat hiển thị trạng thái thật (`Chưa kết nối`/`Chưa xác minh`), không giả online hoặc hiển thị tin mock như lịch sử thực.
2. **Given** người dùng chọn peer đã xác minh và gửi thành công, **When** ứng dụng B nhận tin, **Then** tin mới xuất hiện trong luồng chat đúng peer và UI cập nhật không chặn người dùng.
3. **Given** người dùng nhấn Enter hoặc nút Gửi trong trạng thái hợp lệ, **When** gửi, **Then** chỉ **một** message được sinh; xuống dòng có thao tác riêng được mô tả trong UI.
4. **Given** ứng dụng đóng và khởi động lại, **When** người dùng trở lại M2, **Then** không hiển thị nội dung phiên cũ như **History bền vững**; nếu chỉ có lưu định danh ghép cặp, giải thích đó không phải lưu tin nhắn.
5. **Given** FE mock vẫn tồn tại cho demo, **When** người dùng chạy demo, **Then** dữ liệu, password và các `Đã xem` mô phỏng **không** được hiểu/đẩy sang hệ thống mạng thật.

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
- Đồng hồ hai máy lệch nhiều phút/giờ, múi giờ đổi khi đang chạy, timestamp quá khứ/tương lai, DST và 12/24 giờ.
- B offline, B đóng app giữa lúc gửi, socket half-open, hệ điều hành sleep, đổi Wi-Fi/IP, kết nối lại thủ công chậm.
- User mở hai hội thoại cùng lúc; tin đến hội thoại khác, thay đổi lựa chọn trước khi callback bất đồng bộ về.
- Người dùng giữ bản nháp rồi đóng app: draft persistence không được tự hứa nếu chưa thiết kế rõ; phải có nhắc nhở phù hợp nếu mất nội dung.
- UI FE demo còn `sent/delivered/read` trạng thái mock: trong live mode các label phải bắt nguồn từ sự kiện/ACK thực, không reuse mock flag.
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
- **FR-007:** Người dùng **MUST** có cách kiểm tra/so khớp danh tính peer qua phương thức được `plan.md` xác định; chỉ bấm Accept M1, biết IP hoặc tin displayName/UUID **MUST NOT** tạo trust.
- **FR-008:** Session chat **MUST** liên kết peer identity đã xác minh với key/credential của kênh, và `senderId` của tin nhận **MUST** khớp peer đã xác minh; dữ liệu `sender` tự khai không được thay thế kết quả xác thực.
- **FR-009:** Khi peer đổi key, ghép cặp bị thu hồi hoặc xác minh thất bại, hệ thống **MUST** chặn tin thật và yêu cầu xác minh lại, không ghi nhận trạng thái `Trusted` hoặc `Secure` giả.
- **FR-010:** Secrets/keys, mật khẩu (nếu xuất hiện), chat content và nội dung private **MUST NOT** đi vào log thông thường, crash evidence hoặc Git. Việc lưu identity/key ở user-local nếu cần **MUST** được chốt quyền truy cập, rotation/revocation và cleanup trong `plan.md`.
- **FR-011:** Inbound data, ACK và version/peer metadata đều là untrusted cho tới khi kiểm tra schema, kênh và quyền truy cập; parser **MUST NOT** dùng Java native object deserialization.

**Hội thoại và dữ liệu tin nhắn**

- **FR-012:** Một P2P conversation **MUST** ánh xạ tới **đúng một peer identity đã xác minh**, không gộp theo display name hoặc địa chỉ IP.
- **FR-013:** `CHAT_TEXT` **MUST** có hợp đồng phiên bản rõ ràng gồm `messageId`, `senderId`, `destination peerId`, `sentAt` UTC, text UTF-8 và dữ liệu phiên/kiểm tra phù hợp; các field phải được validate trước khi gửi/nhận.
- **FR-014:** UI **MUST** cho người dùng soạn và gửi text/emoji Unicode qua kênh chat thật; dữ liệu phải được bảo toàn UTF-8. Không gửi rỗng/toàn whitespace; chiều dài bytes và số lượng request phải **có giới hạn hữu hạn** chốt trong `plan.md`.
- **FR-015:** Tin nhận **MUST** được gắn đúng sender đã xác minh và đúng conversation/destination; UI **MUST NOT** suy luận sender bằng bubble alignment hay active conversation.
- **FR-016:** Hệ thống **MUST** định nghĩa rõ thứ tự tin **trong một phiên kết nối** và xử lý dedup theo `messageId`; không ngầm cam kết exactly-once hoặc ordering xuyên restart khi M2 chưa có History durable.
- **FR-017:** Nếu một tin hợp lệ đến khi người dùng đang xem conversation khác, ứng dụng **MUST** cập nhật/đánh dấu đúng conversation đích và không chèn vào luồng đang chọn.
- **FR-018:** Ngoài `CHAT_TEXT`, giao thức **MUST** có quy tắc ACK/error có correlation ID, binding với session identity và trạng thái; không cho phép ACK đến từ peer/session khác hoặc không đúng message ID làm nâng trạng thái.
- **FR-019:** M2 **MUST** không tiếp nhận hoặc xử lý payload Group/File/History/Media bằng logic chat chỉ vì chung transport; mỗi loại cần spec/version/ủy quyền riêng.
- **FR-020:** Dữ liệu chat thật M2 **MUST** được biểu diễn rõ là **dữ liệu tạm trong phiên**; không lưu bền vững hoặc tự sync sau restart khi Giai đoạn 05 chưa triển khai. Nếu có cache/persistence bổ sung, bắt buộc tạo quyết định bảo mật/retention trước.

**Sender, Time, State, Emoji**

- **FR-021:** Mỗi tin hiển thị **MUST** có chiều gửi/nhận, định danh/tên sender đúng ngữ cảnh và thời gian dễ đọc; trùng display name **MUST NOT** gây nhầm identity.
- **FR-022:** Wire timestamp **MUST** là UTC theo hợp đồng; UI **MUST** chuyển đổi rõ ràng sang timezone/locale trên máy người dùng; timestamp remote **MUST NOT** được coi là bằng chứng chính xác tuyệt đối hay thứ tự nhân quả.
- **FR-023:** Trạng thái kết nối (disconnected/connecting/connected) **MUST** tách khỏi trạng thái xác minh peer (unverified/verified/changed-key) và trạng thái giao nhận tin (draft/pending/local-sent/acknowledged/failed/uncertain).
- **FR-024:** `Đã gửi từ máy này` chỉ được hiển thị nếu đã hoàn tất sự kiện gửi cục bộ được định nghĩa; `Đã nhận bởi ứng dụng bên kia` **MUST** có ACK ứng dụng hợp lệ sau khi xử lý tin; `Đã xem` **MUST NOT** hiển thị trong M2.
- **FR-025:** Nếu không biết tin đã tới vì mất kết nối sau gửi nhưng trước ACK, UI **MUST** biểu diễn trạng thái không chắc chắn, không chuyển thẳng sang thành công hay thất bại chắc chắn.
- **FR-026:** Unicode/emoji nhiều code point, tiếng Việt và ký tự newline hợp lệ **MUST** bảo toàn dữ liệu đầu vào qua encode/receive/render; trường hợp font thiếu glyph phải có fallback/hướng dẫn rõ.
- **FR-027:** Nếu có thao tác gửi lại sau reconnect, **MUST** do người dùng chủ động trong MVP; `messageId`/dedup/ACK phải tránh nhân đôi tin trong phạm vi bảo đảm đã chốt ở `plan.md`. Không tự tạo offline outbox hoặc background delivery.

**UI, hiệu năng, lỗi và lifecycle**

- **FR-028:** Giao diện M2 **MUST** có danh sách peer/conversation, khung hiển thị tin, ô soạn, nút gửi, trạng thái kết nối/xác minh/giao nhận và phản hồi lỗi rõ.
- **FR-029:** Luồng UI thật **MUST** dùng dữ liệu từ application/network service; mock FE và tài khoản demo **MUST** được phân tách/ghi nhãn, không đưa tin/trạng thái `read` giả vào live chat.
- **FR-030:** Không thao tác socket, crypto, parse lớn hoặc lưu dữ liệu trên Swing EDT; callback UI **MUST** chuyển về EDT, hủy/close **MUST** dọn socket, executor, timeout/ACK task.
- **FR-031:** Khi remote offline/refuse/timeout, user **MUST** thấy nguyên nhân/động tác tiếp theo; bản nháp chưa truyền **MUST NOT** bị coi là đã gửi và không mất do bấm gửi thất bại.
- **FR-032:** Hệ thống **MUST** có giới hạn kích thước tin, số thao tác, hàng đợi ACK và bộ nhớ/dedup; khi vượt giới hạn **MUST** báo lỗi có kiểm soát, không treo hoặc chiếm bộ nhớ vô hạn.
- **FR-033:** Việc kết nối lại **MUST** do người dùng kích hoạt; khi tái kết nối phải xác minh lại tính hợp lệ kênh/credential và thực hiện consent theo M1, không tự tin tưởng chỉ dựa theo IP.
- **FR-034:** Giao diện M2 **SHOULD** hỗ trợ keyboard navigation và thông báo trạng thái dễ đọc; Enter/nút Gửi không sinh 2 lần gửi, nội dung nhiều dòng có thao tác được ghi trong hướng dẫn.
- **FR-035:** Các trường hợp lỗi parser, version không tương thích, remote đóng giữa frame và ACK không hợp lệ **MUST** fail closed, không đưa dữ liệu hỏng vào hội thoại.

**Testing và bàn giao**

- **FR-036:** M2 **MUST** có unit tests cho schema, identity mapping, Unicode, timestamp/timezone, message state/ACK, invalid input, session cleanup và UI fake/mock separation.
- **FR-037:** M2 **MUST** có integration tests tối thiểu hai process/peer local và **hai máy Windows LAN thật** với A↔B text/emoji, verified peer, Internet không khả dụng, Reject và disconnect/retry.
- **FR-038:** Security tests **MUST** chứng minh không truyền plaintext chat trong M1, không gửi trước peer verification, không downgrade, giả mạo sender/key/ACK bị từ chối, dữ liệu nhạy cảm không xuất hiện trong log.
- **FR-039:** Bằng chứng nghiệm thu **MUST** ghi runtime JDK21, Maven Wrapper, exit codes, số test, tình trạng hai PC, thời gian và trạng thái quan sát thực; **MUST NOT** đánh dấu Verified khi chưa thực thi.
- **FR-040:** Các thay đổi protocol/crypto/integration FE **MUST** được mô tả trong `plan.md` (bao gồm Constitution Check I–VII), chia thành `tasks.md` test-first trước khi code.

### 4.2. Key Entities & State Model *(logical, not storage schema)*

| Entity | Trường logic tối thiểu | Bất biến |
| --- | --- | --- |
| `VerifiedPeerIdentity` | deviceId, displayName, verified credential/key reference, trust status | Tên/IP không phải bằng chứng identity; key change khóa chat |
| `PeerChatSession` | localId, remoteVerifiedId, transport state, security state, sessionId, negotiatedVersion | `CONNECTED` mạng ≠ `VERIFIED`; chỉ authenticated+protected mới cho chat |
| `P2PConversation` | conversationId, localPeerId, remotePeerId | Peer identity ổn định, không gộp theo tên |
| `ChatMessage` | messageId, conversationId, senderId, destinationId, sentAtUTC, text, direction | Nội dung là text Unicode; sender/destination phải khớp phiên xác thực |
| `MessageAttempt` | messageId, local state, outstanding ACK correlation, error/uncertainty reason | State giao nhận không lẫn presence; ACK không chứng minh human read |
| `MessageReceipt` | messageId, receiver verified ID, session correlation, acceptedAt | Chỉ có nghĩa ứng dụng đã xử lý tin; không phải `read` |
| `ConversationView` | selectedPeerId, transient messages, draft, scroll position | Live ≠ FE mock; không ngầm persist qua restart |

**Trạng thái đề xuất** (tên enum cuối cùng do plan chốt):

```text
Network:   DISCONNECTED → CONNECTING → CONNECTED → DISCONNECTED/FAILED
Consent:   PENDING → ACCEPTED / REJECTED / EXPIRED
Identity:  UNVERIFIED → VERIFYING → VERIFIED / CHANGED_KEY / REVOKED
Security:  UNPROTECTED → NEGOTIATING → PROTECTED / FAILED
Message:   DRAFT → SENDING → LOCAL_SENT → APP_ACKNOWLEDGED
                                  └──→ UNCERTAIN / FAILED
```

**Luật chặn:** `CHAT_TEXT` chỉ khi **network CONNECTED + explicit inbound approval + identity VERIFIED + security PROTECTED + agreed message protocol version**. `DIAGNOSTIC_PING/PONG` trong M1 không tự nâng cấp bất kỳ trạng thái nào thành xác minh người gửi.

### 4.3. Non-Functional Requirements

- **NFR-001 — Security first:** M2 không gửi dữ liệu người dùng trên plaintext M1; mã hóa/xác thực/khóa có test và review riêng; không ghi secret/content vào routine log; không dùng demo login làm auth.
- **NFR-002 — Responsiveness:** UI Swing không block chờ mạng/ACK/key verification; `plan.md` phải chốt các timeout, resource caps, cancel/cleanup và ngân sách responsiveness có thể đo.
- **NFR-003 — Data correctness:** UTF-8 lossless cho text hợp lệ và emoji; sender ID, timestamp UTC, đích và ACK đối chiếu đúng.
- **NFR-004 — Availability:** Hai Windows peer LAN giao tiếp khi public Internet không khả dụng; không có dependency runtime bắt buộc vào GitHub/cloud/remote auth.
- **NFR-005 — Bounded memory:** Số peer/session, pending messages, dedup window và message bytes đều hữu hạn; giảm tải/fail nhanh và có thông báo.
- **NFR-006 — Accessibility & discoverability:** Controls gửi/chuyển chat/kết nối/hủy có nhãn, focus/keyboard hợp lý và trạng thái không dựa hoàn toàn vào màu.
- **NFR-007 — Compatibility:** M1 diagnostic peers không hiểu chat extension phải từ chối có kiểm soát; không gửi `CHAT_TEXT` cho parser v1-only; plan phải xác định version negotiation/migration.
- **NFR-008 — Privacy of persistence:** Message History (durable) chưa nằm trong M2; nếu lưu pairing credential phải có cơ chế user-local bảo vệ, cách xóa/đổi và không đi vào Git.

## 5. Acceptance Criteria (AC-03-A…L)

| AC | Kết quả bắt buộc để nghiệm thu M2 | Stories / Requirements |
| --- | --- | --- |
| **AC-03-A** | A gửi tin thật cho B, B hiển thị **đúng text/sender/time** trong conversation tương ứng | US2–3; FR-012–017, FR-021–022 |
| **AC-03-B** | B trả lời cho A; hai phía đúng chiều gửi/nhận trên **hai máy LAN** | US2, US6; FR-001, FR-037 |
| **AC-03-C** | Vietnamese/emoji nhiều code point qua mạng giữ nguyên nội dung, đúng UTF-8 | US3; FR-014, FR-026 |
| **AC-03-D** | B offline/ngắt giữa gửi: A có trạng thái/lỗi hợp lý, nháp không mất, UI không treo | US4; FR-025, FR-030–033 |
| **AC-03-E** | Không ghi `Đã nhận/Đã xem` khi chưa có ACK hoặc human-read proof; **không có read receipts trong M2** | US4; FR-018, FR-023–025 |
| **AC-03-F** | Trước xác minh+protected channel, hoặc khi fake/mismatch key, **không có CHAT_TEXT thật ra mạng** | US1; FR-003, FR-006–011, FR-038 |
| **AC-03-G** | App-level ACK hợp lệ mới nâng `APP_ACKNOWLEDGED`; ACK sai/trùng/khác peer không làm thay đổi trạng thái; duplicate messageId bị xử lý theo phạm vi bảo đảm | US4; FR-016, FR-018, FR-024, FR-027 |
| **AC-03-H** | Hai peer trùng tên/đổi tên, tin đến khi đang xem hội thoại khác vẫn gắn đúng ID; timestamp hiển thị đúng timezone | US2–3; FR-005, FR-012, FR-015, FR-021–022 |
| **AC-03-I** | UI live nhận dữ liệu thật, không lẫn FE mock; không tuyên bố History/Read từ mock; đóng/mở lại không hiển thị giả dữ liệu bền vững | US5; FR-020, FR-028–029 |
| **AC-03-J** | Ít nhất **hai máy Windows 11 x64 thực**, không Internet nhưng LAN hoạt động, gửi/nhận hai chiều, consent/xác minh đúng; có bằng chứng trên cả hai | US6; FR-001–004, FR-037–039 |
| **AC-03-K** | Tests âm/timeout/Unicode/malformed/frame/key-downgrade/ACK/cleanup + regression M0/M1/FE đều pass; evidence ghi exit codes | US1–6; FR-035–040 |
| **AC-03-L** | M1 Gate 1 đã Verified; Constitution I–VII có review không còn vi phạm MUST; README/Quickstart mô tả đúng giới hạn | US1–6; FR-004, FR-039–040 |

### Success Criteria — measurable outcomes (SC-03-001…010)

- **SC-03-001:** Tối thiểu **1 luồng A→B và 1 luồng B→A** trên **hai Windows PC riêng biệt**, với Internet không khả dụng mà LAN vẫn hoạt động; nội dung, sender và time đều đúng (AC-A/B/J).
- **SC-03-002:** **100%** mẫu Unicode/emoji/tiếng Việt trong bộ test đã công bố round-trip nguyên vẹn; không có ký tự thay thế do lỗi codec (AC-C).
- **SC-03-003:** **0** tin chat được gửi khi peer chưa xác minh hoặc kênh chưa được bảo vệ trong test âm/downgrade; cùng lúc không có leak plaintext chat trong packet capture kiểm thử đã được phê duyệt (AC-F).
- **SC-03-004:** **0** lần hiển thị `Đã nhận` khi không có ACK hợp lệ và **0** `Đã xem` giả trong toàn bộ test M2 (AC-E/G).
- **SC-03-005:** **100%** trường hợp kết nối đến bị Reject/timeout/đóng giữa chừng trong suite không gửi message chat, không khóa UI và dọn socket/worker theo policy (AC-D/F/K).
- **SC-03-006:** Tin nhắn của hai peer **trùng displayName** và các trường hợp đổi tên trong suite vẫn gắn đúng `peerId`/conversation; **0** nhầm sender (AC-H).
- **SC-03-007:** Tối thiểu **5 chu kỳ** tạo session xác minh→gửi/ACK→ngắt/cleanup có kiểm thử lặp lại ổn định trên local loopback; ít nhất **một** chu kỳ tương đương được ghi trên **hai PC thật** (AC-J/K).
- **SC-03-008:** Tất cả test đã đánh dấu PASS có bằng chứng thực thi trên actual JDK21; command thất bại trả nonzero, không thay thế một PC hai process cho two-device acceptance (AC-K/L).
- **SC-03-009:** Ứng dụng không cần Internet hoặc central server tại runtime khi user chat; firewall vẫn do người dùng/IT điều khiển, không tự mở Public profile (AC-J).
- **SC-03-010:** README và UI phân biệt rõ `CONNECTED`, `VERIFIED`, `APP_ACKNOWLEDGED`, mock demo và History/Read chưa được hỗ trợ; không có tuyên bố bảo mật khi chưa kiểm thử (AC-I/L).

**Lưu ý đo lường:** Các số 100% và 0 áp dụng cho **tập ca test công bố**, không tuyên bố không thể có lỗi ngoài môi trường test. Timeout, giới hạn bytes, sức chứa queue, mục tiêu latency/EDT cụ thể phải được chốt theo đo đạc và đánh giá rủi ro trong `plan.md`.

## 6. Dependencies & out of scope

### Prerequisites / Gate

1. **M1 Gate 1** phải Verified trên hai Windows PC theo [acceptance M1](../002-lan-networking/evidence/m1-acceptance-report.md). Tạo spec/plan song song **không** cho phép tự đánh dấu M1 đã qua gate.
2. Phương thức xác minh identity, kênh mã hóa, trust/key rotation/revocation và protocol negotiation cho chat phải có **thiết kế và kiểm thử** trước khi chuyển dữ liệu người dùng.
3. FE demo là **mock data**; phải xác định ranh giới `MockChatService` với `NetworkCoordinator`/real messaging service, không mặc định dùng LoginFrame/password demo làm authentication.
4. `plan.md` và `tasks.md` của 003 chưa tồn tại tại thời điểm chỉ viết spec; không đánh dấu bắt đầu implement/code khi chưa có kế hoạch kỹ thuật và test-first tasks.

### Explicitly out of scope

- Nhóm nhiều người/membership/broadcast: **004**.
- Lưu/khôi phục lâu dài, đồng bộ lịch sử, multi-device offline merge và quyền conversation ACL: **005**; phải được thiết kế xác thực/ủy quyền riêng.
- Tệp và tải xuống: **006**; ảnh, video, âm thanh như nội dung file: **007**.
- Voice call, video call realtime, Cloud account, Internet relay/NAT traversal, auto-discovery, auto-reconnect, tự động đổi Firewall.
- Read receipts dựa trên người thực đã đọc, push notification ngoài phiên, multi-device history ordering, durable outbox và delivery offline/auto resend.
- M1 diagnostic plaintext không được trở thành đường gửi chat production; FE mock không phải backend chat.

## 7. Open Design Decisions for Clarify / Plan

| ID | Vấn đề cần quyết định trước khi implement | Ràng buộc không được phá |
| --- | --- | --- |
| **OD-03-01** | Cơ chế identity/key, xác minh lần đầu (so khớp fingerprint/SAS), pinning/revocation và UX khi key thay đổi | Không dùng tên/IP/UUID tự khai hoặc Accept M1 làm authentication |
| **OD-03-02** | Bảo vệ kênh và key lifecycle (ví dụ TLS mutual authentication hoặc cơ chế đã qua review); chống downgrade, ràng buộc session/ACK, compatibility v1→v2 | Không gửi chat plaintext/chưa xác thực, không lách whitelist M1 |
| **OD-03-03** | Giới hạn text UTF-8/frame/queue, thời hạn ACK/timeout, thứ tự và cửa sổ dedup có giới hạn | Không hứa exactly-once lâu dài, không treo UI/memory |
| **OD-03-04** | Chính sách thao tác retry khi ACK mất; `LOCAL_SENT` so với `UNCERTAIN`, giữ draft khi offline, cảnh báo khi không rõ B đã nhận | Không báo `Đã nhận` khi không có ACK; không tự reconnect trái CL-02-04 |
| **OD-03-05** | UI live dùng lại màn FE nào, có tách Demo/Live không; mapping `model.ChatMessage` ↔ domain M2, accessibility/keyboard behavior | Không trộn mock/dữ liệu thật; không dùng fake read/delivery |
| **OD-03-06** | Bộ nhớ phiên/draft, lưu credential user-local, xóa trust và cách hiển thị khi restart | Không tự dựng History Sync 005 hoặc lưu private chat trái policy |
| **OD-03-07** | Ngân sách thời gian phản hồi, timeout và khả năng nhiều peer; test security/soak và hai PC không Internet | Các số phải có căn cứ, gate thật không thể thay bằng loopback |

**Cần xác nhận trong Clarify:** Các P-03-01…05 ở §1.1 là đề xuất mặc định vì người dùng chưa chốt riêng cách xác minh/mã hóa chat, offline outbox, ACK và UI live của M2. Nếu đổi các đề xuất, phải cập nhật spec/plan/test trước khi implement; không được tự hạ thấp bảo mật hoặc gọi hai process localhost là hai máy LAN.

## 8. Traceability to ROADMAP

| Mục ROADMAP | Requirements / evidence cần có |
| --- | --- |
| 03.01 — Hội thoại P2P | US1–2; FR-005, FR-012, FR-015, FR-017; AC-H |
| 03.02 — Text | US2; FR-013–014, FR-018; AC-A/B |
| 03.03 — UI hội thoại | US5; FR-028–029, FR-034; AC-I |
| 03.04 — Sender | US3; FR-008, FR-015, FR-021; AC-A/H |
| 03.05 — Time | US3; FR-022; AC-A/H |
| 03.06 — State | US4; FR-018, FR-023–025; AC-D/E/G |
| 03.07 — Emoji | US3; FR-014, FR-026; AC-C |
| 03.08 — Gửi thất bại | US4; FR-025, FR-027, FR-031–033; AC-D/G |
| 03.09 — Automated tests | US1–6; FR-036–040; AC-J/K/L |

**M2 release gate:** Chỉ công bố `MVP Chat P2P — Verified` khi **M1 đã Verified**, protocol/security/UX đã qua review, **AC-03-A…L có bằng chứng thực thi**, và các nguyên tắc I–VII không có vi phạm MUST chưa được chấp thuận. File spec này **không** phải bằng chứng ứng dụng đã hỗ trợ gửi chat thật.
