# Feature Specification: Nền tảng kết nối LAN P2P (Giai đoạn 02)

**Feature Branch:** `002-lan-networking` (tên đề xuất; chưa tạo nhánh Git)
**Created:** 2026-11-21
**Status:** Draft — đặc tả mới; chưa lập `plan.md`, `tasks.md`, chưa triển khai chức năng mạng
**Input:** [ROADMAP — Giai đoạn 02](../../ROADMAP.md), [nền tảng Giai đoạn 01](../001-project-foundation/spec.md)
**Governing:** [IntraChat Constitution](../../.specify/memory/constitution.md) · [AGENTS](../../AGENTS.md)
**Baseline:** [Protocol v1 draft](../001-project-foundation/contracts/protocol-v1-draft.md) · [Architecture](../../docs/architecture.md)

## Overview

**Mục tiêu M1 — LAN Connected:** Hai phiên IntraChat trên hai máy Windows trong cùng mạng LAN **kết nối trực tiếp**, xác định phiên/peer, trao đổi được **thông điệp thử nghiệm có phiên bản** theo hai chiều, nhận biết mất kết nối và phục hồi hoặc báo lỗi một cách có kiểm soát. Toàn bộ thao tác kết nối, chờ đợi và đóng phiên không làm treo giao diện.

**Topology được xác nhận:** **direct peer-to-peer (P2P) trong LAN**. Không yêu cầu server trung tâm, dịch vụ cloud hoặc Internet. Trong phạm vi Giai đoạn 02, mỗi máy vừa có thể nhận yêu cầu kết nối vừa có thể chủ động kết nối; đây **không** phải tuyên bố hệ thống đã phi tập trung hoàn toàn ở mức Group/History. Dự án nhắm tới **Windows 11 x64**, sử dụng bộ khung Java/Swing/Maven có từ Giai đoạn 01.

**Trong phạm vi:** cấu hình và chấp nhận kết nối từ peer LAN; kết nối theo địa chỉ có thể nhập thủ công; danh tính thiết bị tự công bố và giới hạn niềm tin; handshake/version negotiation; session/connection state; giao thức thông điệp thử nghiệm hai chiều; framing và kiểm tra giới hạn; bất đồng bộ, timeout, disconnect, retry có kiểm soát, cleanup, lỗi dễ hiểu; hướng dẫn port/firewall; kiểm thử loopback và hai máy LAN thật.

**Ngoài phạm vi:** tin nhắn chat người dùng, danh sách hội thoại và trạng thái `sent/delivered/read` (Giai đoạn 03); Group (04); lưu/đồng bộ History (05); File (06); Image/Video/Sound và gọi trực tiếp (07 hoặc ngoài phạm vi); Internet relay/server công cộng; tự động mở cổng firewall; xác thực tài khoản cloud. Những thông điệp trong M1 là **kỹ thuật/diagnostics**, không phải bản chat MVP.

**Phân định trạng thái:** `CONNECTED` nghĩa là đã lập một **transport session đáp ứng handshake** theo hợp đồng, **không** chứng minh peer là người/thiết bị đáng tin, không có nghĩa “đã đọc tin” hoặc “đã đồng bộ lịch sử”.

## Confirmed Constraints & Carry-over

| ID | Nguồn | Ràng buộc |
| --- | --- | --- |
| CL-01 | [001 clarification](../001-project-foundation/spec.md#clarifications-2026-11-21) | P2P **trực tiếp** qua LAN, không bắt buộc server điều phối/relay |
| CL-02 | 001 | Windows là hệ điều hành mục tiêu; Windows 11 x64 là nền tảng nghiệm thu đầu tiên |
| CL-03 | 001 | Video/Sound là gửi/nhận/phát file, không có cuộc gọi trong M1 |
| CL-04 | 001 | History về sau **đồng bộ giữa máy được phép**; M1 không truyền, đọc hoặc lưu History |
| M0 gate | [Acceptance report](../001-project-foundation/evidence/m0-acceptance-report.md) | Giai đoạn 01 **38/41 tasks**; build/test đã chạy trên JDK 25 với `--release 21`, nhưng Java 21 runtime và offline UI test chưa đủ bằng chứng. Viết spec/plan 02 được phép, song **không coi M0 đã Verified hoặc M1 đã triển khai**. |

**Yêu cầu định hướng có sẵn, không phải công nghệ đã triển khai:** `plan.md` Giai đoạn 01 đề xuất TCP + JSON UTF-8, 4-byte length prefix, giới hạn control frame 1 MiB, nhập IP/port thủ công để fallback và khám phá peer như tùy chọn. Những nội dung chi tiết (cổng mặc định, timeout, cơ chế discovery, phiên bản parser, chính sách tin cậy) phải được chốt kèm trade-off trong **plan Giai đoạn 02** trước khi code.

## User Scenarios & Testing *(mandatory)*

### User Story 1 — Kết nối trực tiếp với một máy trong LAN (Priority: P1)

Là người dùng đang sử dụng IntraChat trên Windows, tôi muốn nhập địa chỉ của một máy trong cùng LAN và chủ động kết nối mà không cần Internet hay server trung gian để biết hai phiên đã có thể giao tiếp.

**Why this priority:** Đây là năng lực nền tảng để xây dựng mọi tính năng P2P ở giai đoạn sau.

**Independent Test:** Dùng hai phiên trên hai máy LAN (và test loopback hai process) để xác nhận kết nối trực tiếp, đổi chiều kết nối, không phụ thuộc dịch vụ cloud.

**Acceptance Scenarios:**

1. **Given** peer B đang lắng nghe trên cấu hình hợp lệ và A biết địa chỉ/cổng của B, **When** A yêu cầu kết nối, **Then** A và B nhìn thấy session đã thiết lập và đúng peer endpoint ở phía mình; không có server trung gian bắt buộc.
2. **Given** hai máy cùng mạng LAN nhưng không có Internet, **When** A kết nối đến B theo địa chỉ hợp lệ, **Then** chức năng kết nối LAN vẫn hoạt động.
3. **Given** người dùng nhập địa chỉ hoặc cổng không hợp lệ, **When** yêu cầu kết nối, **Then** dữ liệu bị từ chối với thông báo hữu ích; UI vẫn phản hồi và không tạo phiên “connected” giả.
4. **Given** B không chạy hoặc không thể tiếp cận, **When** A thử kết nối, **Then** A nhận kết quả thất bại/timeout trong một khoảng được chặn và có thể thử lại.
5. **Given** B đang lắng nghe, **When** B kết nối ngược sang A sau khi A sẵn sàng nhận, **Then** luồng tương tự hoạt động, không buộc một máy duy nhất đóng vai trò server cố định.

### User Story 2 — Nhận diện peer và phiên bản tương thích (Priority: P1)

Là người sử dụng hoặc lập trình viên, tôi cần biết một kết nối thực sự thuộc phiên nào, peer khai báo danh tính gì và có dùng giao thức phù hợp hay không để tránh gán nhầm người hoặc xử lý message không tương thích.

**Why this priority:** Một socket mở thành công chưa đủ để định nghĩa một phiên giao tiếp an toàn và đúng hợp đồng.

**Independent Test:** Dùng hai tiến trình với cùng/khác version, ID trùng tên hiển thị, handshake bị thiếu/sai; kiểm tra sự kiện phiên hoặc lỗi trả về mà không gửi tin chat.

**Acceptance Scenarios:**

1. **Given** hai phiên khai báo cùng version hỗ trợ, **When** hoàn tất handshake, **Then** session chỉ chuyển sang trạng thái kết nối hợp lệ sau khi xác định được version và peer/device ID hợp lệ về mặt cú pháp.
2. **Given** hai peer có cùng `displayName` nhưng `deviceId` khác, **When** thiết lập phiên, **Then** hệ thống lưu/phân biệt theo ID, không dùng tên hiển thị làm khóa danh tính.
3. **Given** peer có version không tương thích hoặc handshake thiếu trường, **When** thương lượng kết nối, **Then** từ chối session có kiểm soát và báo lý do; không tiếp tục trao đổi message với schema không tương thích.
4. **Given** một peer tự khai tên/ID, **When** kết nối thành công ở mức transport, **Then** giao diện và API không được gọi đó là **đã xác thực danh tính**, **được tin cậy** hoặc **được quyền đọc lịch sử** chỉ vì handshake thành công.
5. **Given** hai phiên khởi chạy trùng thời điểm, **When** vừa nhận vừa tạo kết nối trùng lặp, **Then** kết quả có chính sách xác định (giữ một phiên hoặc biểu diễn nhiều phiên rõ ràng); không rò rỉ tài nguyên hoặc hiển thị số phiên mâu thuẫn.

### User Story 3 — Gửi/nhận payload kiểm thử theo hợp đồng (Priority: P1)

Là người phát triển chức năng chat sau này, tôi cần một cơ chế gửi/nhận các bản tin kỹ thuật có version, sender, destination, message ID và timestamp, để có nền tảng kiểm thử độc lập với giao diện hội thoại.

**Why this priority:** Framing và parsing chính xác giúp giai đoạn 03–07 mở rộng mà không làm sai dữ liệu.

**Independent Test:** Hai tiến trình trao đổi một số payload nhỏ hai chiều; test phân mảnh/gộp frame, Unicode và bản tin không hợp lệ.

**Acceptance Scenarios:**

1. **Given** A và B có session hợp lệ, **When** A gửi một payload chẩn đoán và B phản hồi, **Then** cả hai chiều nhận đúng bytes/nội dung đã được đặc tả với thông tin loại, version và liên kết phiên.
2. **Given** khung message đến qua nhiều đoạn hoặc nhiều khung cùng một lần đọc, **When** bên nhận xử lý, **Then** mỗi message hợp lệ được phân tách đúng một lần ở mức parser và đúng thứ tự trên một kết nối.
3. **Given** payload thử nghiệm có Unicode tiếng Việt hoặc emoji, **When** gửi và nhận, **Then** nội dung còn nguyên mà không bị đổi ký tự.
4. **Given** bản tin thiếu trường, không đúng định dạng, có type không hỗ trợ hoặc vượt giới hạn, **When** nhận, **Then** được từ chối trước khi cấp phát không kiểm soát, không crash/treo UI và có mã lỗi hoặc lý do dễ kiểm tra.
5. **Given** message mang timestamp và sender ID, **When** được nhận, **Then** dữ liệu được diễn giải theo hợp đồng; bên nhận không suy ra trạng thái delivered/read và không dùng đồng hồ peer để tuyên bố thứ tự toàn cục.

### User Story 4 — Kết nối không làm treo ứng dụng và tự dọn dẹp (Priority: P1)

Là người sử dụng, tôi cần ứng dụng tiếp tục thao tác được khi kết nối yếu, đối phương tắt máy hoặc tôi hủy kết nối để không phải kết thúc chương trình bằng cách cưỡng bức.

**Why this priority:** Tính ổn định là điều kiện tối thiểu trước chat và file transfer.

**Independent Test:** Mở hai phiên, tạo thành công, ngắt đột ngột, timeout và thoát ứng dụng; quan sát UI, session state và tài nguyên.

**Acceptance Scenarios:**

1. **Given** đang kết nối/chờ kết quả, **When** người dùng tương tác với ứng dụng, **Then** UI vẫn phản hồi, có trạng thái đang kết nối và lựa chọn hủy nếu thao tác kéo dài.
2. **Given** B tắt đột ngột hoặc đóng socket, **When** A phát hiện mất liên lạc theo chính sách timeout, **Then** A chuyển sang trạng thái không còn connected, đưa ra thông báo có thể hiểu và không giữ worker/socket mồ côi.
3. **Given** A quyết định ngắt hoặc đóng cửa sổ, **When** thao tác kết thúc, **Then** socket/streams/worker liên quan được thu hồi và chương trình kết thúc theo chính sách shutdown.
4. **Given** mạng bị gián đoạn rồi khả dụng trở lại, **When** người dùng muốn thử kết nối lại, **Then** có cách thử lại có kiểm soát, không tự động retry vô hạn, không nhân bản session vô hạn.
5. **Given** nhiều phiên kết nối liên tiếp hoặc đầu vào sai, **When** số lượng thao tác đạt ngưỡng được quy định, **Then** số tài nguyên và hàng đợi vẫn có giới hạn; lỗi của một peer không kéo sập peer khác.

### User Story 5 — Dùng được trong điều kiện LAN thực tế (Priority: P2)

Là người cài đặt IntraChat trong phòng máy, tôi cần biết địa chỉ/cổng của máy mình, hướng dẫn firewall Windows và giới hạn của cơ chế tìm peer để xác định nguyên nhân kết nối thất bại.

**Why this priority:** Bản chạy trên loopback chưa chứng minh được hoạt động trên hai máy LAN thật.

**Independent Test:** Hai máy Windows trên cùng subnet/phạm vi LAN được hỗ trợ; không Internet; thử địa chỉ thủ công, cấu hình mạng phù hợp và trường hợp Firewall chặn.

**Acceptance Scenarios:**

1. **Given** kết nối đang lắng nghe, **When** người dùng xem thông tin kết nối, **Then** có thể biết cổng lắng nghe và địa chỉ cần cung cấp cho peer theo giao diện/hướng dẫn.
2. **Given** peer discovery tự động không hoạt động hoặc không được hỗ trợ, **When** người dùng nhập IP/port của peer, **Then** vẫn có đường kết nối thủ công độc lập với discovery.
3. **Given** Firewall/Private network chặn kết nối, **When** người dùng thử kết nối, **Then** ứng dụng hướng dẫn kiểm tra mạng/cổng an toàn, không lặng lẽ thay đổi firewall hoặc mở truy cập public Internet.
4. **Given** hai máy LAN thật, **When** kiểm thử chức năng kết nối/payload hai chiều, **Then** ghi lại môi trường, địa chỉ không nhạy cảm, kết quả và hạn chế để nghiệm thu M1; không coi loopback là tương đương test hai máy.
5. **Given** người dùng xem trạng thái bảo mật, **When** transport chưa có xác thực/mã hóa đã được kiểm chứng, **Then** cảnh báo rõ hạn chế và không gợi ý gửi dữ liệu nhạy cảm.

### Edge Cases

- Một máy nhập **chính địa chỉ của mình**; ứng dụng không ghi nhận thành peer từ xa ngoài ý muốn.
- Port đang được ứng dụng khác sử dụng; listener phải báo lỗi và không thể hiện trạng thái “đang nghe” giả.
- Hai peer cùng tên hiển thị, một peer đổi tên, peer khai báo `deviceId` bất thường hoặc trùng ID trên hai kết nối.
- Peer từ chối kết nối, handshake đến chậm/mất dở, sai version, unknown message type, field null/thiếu, JSON/UTF-8 hỏng.
- TCP đọc một frame bị chia nhỏ; hai frame gộp; khung không hoàn chỉnh đến khi timeout; length âm/zero/over limit.
- Dữ liệu đến ngay trước hoặc sau disconnect; gửi khi session đã đóng; queue đầy; ngoại lệ I/O; retry đồng thời.
- Máy B mất mạng, ngủ/khởi động lại, thay IP, hoặc mạng Wi-Fi bật client isolation; kết nối lại có thể cần cập nhật địa chỉ.
- Đồng hồ hai máy lệch; nội dung có ký tự Unicode đa byte/emoji; không tự suy luận thứ tự toàn hệ thống.
- Quét kết nối lạ vào cổng đang lắng nghe; dữ liệu không tin cậy trước khi handshake; chống cấp phát bộ nhớ không giới hạn.
- Hai máy nằm trong VLAN/subnet bị chặn định tuyến; không cam kết hoạt động xuyên mạng NAT/public Internet.
- Máy không có quyền quản trị Windows hoặc không được phép sửa firewall; ứng dụng chỉ hướng dẫn và cho lỗi có thể hiểu.
- Người dùng tắt phần mềm trong lúc connecting; không được bỏ lại luồng nền hoặc làm treo quá trình đóng cửa sổ.

## Requirements *(mandatory)*

### Functional Requirements

**Kết nối, địa chỉ và topology**

- **FR-001:** IntraChat **MUST** hỗ trợ kết nối trực tiếp giữa **hai peer** trong mạng LAN mà không cần server trung tâm, tài khoản cloud hoặc Internet khi runtime.
- **FR-002:** Mỗi phiên ứng dụng **MUST** có khả năng nhận yêu cầu kết nối từ peer và chủ động mở kết nối theo vai trò phù hợp; máy nào làm initiator/listener không trở thành server trung tâm bắt buộc cho tất cả người dùng.
- **FR-003:** Người dùng **MUST** có thể nhập/thiết lập địa chỉ peer và cổng hợp lệ để kết nối **thủ công**, kể cả khi cơ chế tự khám phá không có hoặc thất bại.
- **FR-004:** Hệ thống **MUST** kiểm tra định dạng địa chỉ và cổng, từ chối giá trị sai và hiển thị kết quả kết nối, bao gồm đích không tồn tại hoặc không tiếp cận được.
- **FR-005:** Hệ thống **MUST** cho người dùng xem hoặc tra cứu địa chỉ/cổng đang lắng nghe, và giải thích trường hợp không mở được listener (port conflict, permission, firewall).
- **FR-006:** Nếu có peer discovery tự động, kết quả **MUST** chỉ là gợi ý kết nối và **MUST NOT** là bằng chứng peer đã được xác thực; kết nối thủ công theo FR-003 vẫn dùng được.

**Phiên, danh tính và versioning**

- **FR-007:** Thiết lập kết nối **MUST** có handshake để xác nhận protocol version tương thích và metadata phiên cần thiết trước khi phiên được coi là hợp lệ.
- **FR-008:** Mỗi peer **MUST** được đại diện bằng định danh thiết bị ổn định độc lập với `displayName` hoặc IP. ID nhận từ mạng **MUST NOT** tự động được coi là danh tính đã xác thực.
- **FR-009:** Hệ thống **MUST** phân biệt trạng thái vận chuyển/kết nối (`DISCONNECTED`, `CONNECTING`, `CONNECTED`, `FAILED` hoặc tương đương) với trạng thái giao nhận/đọc của tin nhắn.
- **FR-010:** Handshake thiếu trường, sai phiên bản, bất thường hoặc hết thời gian **MUST** bị từ chối có kiểm soát, không chuyển sang connected.
- **FR-011:** Kết nối trùng lặp hoặc đồng thời giữa cùng các peer **MUST** được xử lý theo chính sách nhất quán được ghi ở `plan.md`, tránh tạo session/worker mồ côi.

**Thông điệp thử nghiệm và giao thức**

- **FR-012:** Sau handshake, hệ thống **MUST** cung cấp cơ chế gửi/nhận thông điệp **kỹ thuật để kiểm thử** theo cả hai chiều, tách khỏi màn hình chat/hội thoại người dùng.
- **FR-013:** Bản tin được chấp nhận **MUST** có contract phiên bản, loại, định danh bản tin, danh tính sender, destination, timestamp và ý nghĩa payload xác định; các trường có thể được dùng lại từ thiết kế M0 sau khi rà soát tương thích.
- **FR-014:** Bộ đọc/ghi **MUST** xử lý chính xác dữ liệu mạng bị chia thành nhiều phần hoặc ghép nhiều frame, không nhầm ranh giới và không tiêu thụ dữ liệu không thuộc frame.
- **FR-015:** Giao thức **MUST** có chính sách giới hạn kích thước control message, phân khung và xác thực cấu trúc dữ liệu; độ dài vượt ngưỡng **MUST** bị loại bỏ trước khi cấp phát bộ nhớ theo kích thước chưa kiểm chứng.
- **FR-016:** Loại bản tin không hỗ trợ, version không tương thích, UTF-8/JSON lỗi hoặc metadata không hợp lệ **MUST** được xử lý an toàn, phát ra lỗi quan sát được và không dùng Java native object deserialization trên bytes không tin cậy.
- **FR-017:** Bản tin kỹ thuật **MUST** bảo toàn Unicode tiếng Việt/emoji; timestamp phải có quy ước chuẩn và không được dùng thời gian peer để suy ra tính thứ tự toàn cục.
- **FR-018:** Hệ thống **MUST NOT** mô tả một lần ghi socket thành công là trạng thái chat `delivered`/`read`; đảm bảo về thứ tự, ACK, retry và dedup **MUST** được giải thích đúng theo phạm vi network session.

**Bất đồng bộ, lỗi và vòng đời**

- **FR-019:** Các hoạt động connect, accept, read, write, chờ handshake, retry và shutdown **MUST NOT** chặn Swing Event Dispatch Thread.
- **FR-020:** Hệ thống **MUST** cho người dùng thấy trạng thái đang kết nối, kết nối được, không kết nối được hoặc mất kết nối, cùng lỗi phù hợp; thao tác chờ kéo dài phải có chính sách hủy/thoát.
- **FR-021:** Mỗi session/operation mạng **MUST** có timeout, cách hủy và cleanup tương ứng; đóng cửa sổ, người dùng ngắt hoặc peer disconnect phải thu hồi sockets/streams/workers có liên quan.
- **FR-022:** Nếu có tự động reconnect, hệ thống **MUST** có giới hạn và chính sách backoff dừng được. Nếu không có auto-reconnect, người dùng **MUST** có cách thử lại thủ công mà không cần khởi động lại app.
- **FR-023:** Hệ thống **MUST** giới hạn số phiên đồng thời, tác vụ đang chờ và bộ đệm/hàng đợi; sự cố một peer **MUST NOT** kéo theo treo toàn ứng dụng.
- **FR-024:** Các trạng thái kết nối **MUST** phản ánh sự kiện đã xác nhận; không hiển thị `connected` cho socket chưa handshake hoặc socket đã được phát hiện là đóng.

**Security, Windows và minh bạch**

- **FR-025:** Mọi inbound data và identity claim **MUST** được coi là untrusted; không tự động cho peer mới có quyền với History, File, Group hoặc dữ liệu người dùng.
- **FR-026:** Nếu peer authentication/encryption chưa được triển khai và kiểm thử, giao diện/README **MUST** công bố giới hạn này; M1 **MUST NOT** tự quảng bá một session transport là “đã bảo mật”, “đã xác thực” hoặc “đã được cấp quyền”.
- **FR-027:** Hướng dẫn Windows **MUST** ghi cổng, network profile/Firewall và các giới hạn discovery thực tế; chương trình **MUST NOT** tự thay đổi cấu hình firewall hoặc mở dịch vụ public Internet không có yêu cầu/phê duyệt.
- **FR-028:** Logging chẩn đoán **MUST NOT** chứa secret, dữ liệu chat thật, file private hoặc payload không được làm sạch; lỗi mạng nên ghi mã/sự kiện và dữ liệu tối thiểu.
- **FR-029:** Kết quả M1 **MUST** được kiểm thử bằng ít nhất hai process/loopback và hai máy Windows cùng LAN thật, có log phiên bản, trạng thái, lỗi, cleanup và chỉ đánh dấu Verified sau khi thực hiện.
- **FR-030:** Thiết kế của giai đoạn 02 **MUST** giữ đường mở rộng cho Group, File và History sync P2P **có xác thực/ủy quyền sau này**, nhưng **MUST NOT** triển khai hoặc mở các chức năng đó chỉ dựa trên kết nối transport.

### Non-Functional Requirements

- **NFR-001 — LAN Independence:** Sau khi build/cài đặt đầy đủ, kết nối P2P trong LAN không cần truy cập Internet hoặc dịch vụ public. Việc tải dependency khi build lần đầu được phân biệt rõ.
- **NFR-002 — Responsiveness:** Thao tác network không được làm UI mất khả năng tương tác; ngưỡng thời gian phản hồi đo được phải được chốt trong `plan.md` dựa trên môi trường Windows mục tiêu.
- **NFR-003 — Reliability:** Kết nối timeout/disconnect hoặc dữ liệu sai phải có bounded failure, không crash/leak worker, và cho phép mở phiên mới theo chính sách.
- **NFR-004 — Resource Safety:** Plan phải nêu ngân sách cụ thể về kích thước frame, connection count, pending queue, socket timeout, retry và bộ nhớ; kiểm thử boundary tương ứng.
- **NFR-005 — Security:** LAN không được coi là đáng tin; mọi đầu vào mạng phải được validate và không được làm lộ dữ liệu riêng qua logs hoặc tự động cho quyền History.
- **NFR-006 — Protocol Compatibility:** Handshake, parser và contract version phải từ chối version/type không hỗ trợ rõ ràng thay vì silently parse như hợp lệ.
- **NFR-007 — Unicode & Identity:** Peer identity, timestamp, content test phải bảo toàn Unicode và tách stable device ID khỏi mutable display name; không tự suy ra authentication.
- **NFR-008 — Testability:** Giao thức và session lifecycle phải kiểm thử được qua ít nhất 2 peer/2 process hoặc deterministic loopback; acceptance M1 vẫn đòi kiểm thử hai máy LAN thật.
- **NFR-009 — Accessibility & Truth:** Connection state/error có mô tả văn bản dễ hiểu, không chỉ dùng màu; giao diện không giả trạng thái hiện diện hay chat đã gửi.
- **NFR-010 — Development Environment:** Tương thích baseline Windows/Java đã được duyệt trong dự án; chênh lệch Java 21 runtime tại M0 vẫn được theo dõi riêng, không tự miễn trừ.

## Key Entities

> Đây là **khái niệm/hành vi phải thống nhất**, không cam kết đã có class hoặc schema wire. `plan.md` sẽ chốt chi tiết kỹ thuật.

| Entity | Mô tả trách nhiệm |
| --- | --- |
| Local Peer | Định danh thiết bị của phiên hiện tại, display name, địa chỉ/cổng lắng nghe nếu có |
| Remote Peer | Thông tin peer tự khai, endpoint quan sát được, trạng thái xác minh/trust nếu có; không tự đồng nghĩa danh tính xác thực |
| Connection Session | ID phiên, endpoint local/remote, negotiated version, lifecycle status, thời điểm tạo/ngắt, lỗi gần nhất |
| Handshake | Điều kiện tương thích version, metadata peer và chính sách từ chối/timeout |
| Message Envelope | Version, type, message ID, sender/destination, timestamp, payload kỹ thuật |
| Frame | Đơn vị phân tách trên stream, gồm giới hạn độ dài, dữ liệu nội dung và lỗi validation |
| Connection Event | Sự kiện connect, connected, disconnected, failed, canceled; dùng cho UI/service |
| Network Configuration | Listener binding/port, địa chỉ kết nối, network assumptions/Firewall hints |
| Diagnostic Exchange | Payload kiểm thử và phản hồi kỹ thuật, không phải chat hoặc History |
| Trust Boundary | Quy định peer identity tự khai chỉ mang ý nghĩa nhận biết, không cấp quyền tải history/file |

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001:** **1/1 kịch bản** hai máy Windows cùng LAN kết nối trực tiếp và trao đổi payload kỹ thuật **hai chiều** thành công, có kết quả ghi nhận; không cần dịch vụ Internet.
- **SC-002:** Tối thiểu **5/5 lần** kết nối thành công qua hai tiến trình loopback liên tiếp; mỗi lần có handshake hợp lệ và cleanup sau đóng.
- **SC-003:** **5/5 kịch bản** peer chủ động ngắt/đóng đột ngột/timeout/cancel/đóng app được quan sát và chuyển trạng thái không connected theo policy; không treo UI hoặc để lại session lỗi.
- **SC-004:** **100% nhóm input xấu bắt buộc** được kiểm thử và từ chối đúng: địa chỉ/cổng sai, handshake thiếu trường, version sai, unknown type, malformed payload, frame quá giới hạn, frame thiếu bytes.
- **SC-005:** **3/3 lần** thử lại sau kết nối thất bại hoặc sau khi peer tái khởi động có kết quả đúng theo policy, không phải khởi động lại toàn app.
- **SC-006:** Trên môi trường không hỗ trợ auto-discovery, **1/1 luồng nhập IP/port thủ công** vẫn kết nối được tới peer hợp lệ.
- **SC-007:** Có tài liệu tái lập **100%** bước cần thiết cho Windows: xem IP/port, cấu hình firewall private network, phân biệt lỗi firewall và lỗi peer, ranh giới không Internet.
- **SC-008:** Kiểm thử xác nhận **0 lần** hiển thị `connected` trước handshake, hoặc `delivered/read/authenticated/synced` khi chưa có bằng chứng tương ứng.
- **SC-009:** Automated tests giai đoạn 02 **thực thi và pass** cùng các test M0; có bằng chứng cho exchange hợp lệ, input sai, timeout/disconnect và resource cleanup, không dựa vào public Internet.
- **SC-010:** Mọi lỗi/ngắt kết nối trong bộ test chuẩn đều có exit/cleanup kiểm chứng, không để lại socket/worker sử dụng tài nguyên sau kết thúc theo policy đo trong `plan.md`.

## Assumptions & Dependencies

- Dự án đã có Java/Swing foundation, Maven Wrapper và 9 test M0; **M0 vẫn In Progress** đến khi các gate Java 21 runtime/offline GUI/acceptance được giải quyết hoặc có quyết định chính thức.
- Mạng LAN cho phép hai máy tiếp cận trực tiếp bằng địa chỉ/cổng trên network profile được cấu hình đúng; không giả định kết nối xuyên Internet, NAT hoặc Wi-Fi client isolation.
- Tự khám phá peer là **khả năng tùy chọn**; nhập IP/port thủ công là baseline bắt buộc. Phương án discovery (nếu triển khai) được chọn ở `plan.md`.
- Kết nối chỉ dùng payload kỹ thuật/synthetic, không yêu cầu gửi dữ liệu người dùng thật hoặc tài khoản cloud. Không lưu History hay file người dùng.
- Không mặc nhiên có transport encryption hoặc peer authentication. Nếu chưa xây dựng tính năng này trong M1, kết nối chỉ được xác nhận ở mức transport và UI phải nói rõ hạn chế.
- Tên class Java, API, cổng mặc định, socket timeouts, loại serializer và các ngân sách tài nguyên cụ thể thuộc **technical planning**, không quyết định bằng cách giả định trong spec.

## Acceptance Traceability — M1

| ID | Điều kiện nghiệm thu | Truy vết |
| --- | --- | --- |
| AC-02-A | Hai máy LAN kết nối P2P trực tiếp, trao đổi payload kỹ thuật hai chiều không Internet/server | US1, US3; FR-001–003, FR-012–014, FR-029; SC-001 |
| AC-02-B | Dùng được nhập IP/port khi không có auto-discovery, có lỗi có nghĩa khi peer không tìm thấy | US1, US5; FR-003–006, SC-006 |
| AC-02-C | Handshake/version/peer identity được validate và phân biệt display name với device ID | US2; FR-007–011, SC-008 |
| AC-02-D | Parser xử lý frame chia nhỏ/gộp, Unicode, metadata và giới hạn bytes | US3; FR-013–017; SC-004 |
| AC-02-E | Unknown version/type, JSON sai, frame quá cỡ và dữ liệu thiếu bị từ chối có kiểm soát | US2, US3; FR-010, FR-015–016, SC-004 |
| AC-02-F | Timeout, cancel, đóng peer và reconnect có trạng thái đúng, worker/socket cleanup | US1, US4; FR-019–024, SC-003, SC-005, SC-010 |
| AC-02-G | GUI không chặn, hiển thị rõ trạng thái/lỗi; không báo delivered/read/authenticated giả | US2, US4; FR-009, FR-018, FR-020, FR-024–026, SC-008 |
| AC-02-H | Có tài liệu Windows IP/port/Firewall/discovery limitations, không tự mở public firewall | US5; FR-005–006, FR-027, SC-007 |
| AC-02-I | Test loopback tối thiểu hai peer/process, kiểm thử LAN thật hai máy, không cần cloud, có log kết quả | US1, US3–5; FR-029; SC-001–002, SC-009 |
| AC-02-J | Không đọc/ghi History, File/Media, không cấp quyền peer chỉ qua ID tự khai; cảnh báo bảo mật trung thực | US2, US5; FR-025–026, FR-028, FR-030; SC-008 |
| AC-02-K | Giai đoạn 01 đạt gate yêu cầu, kết quả M1 và Constitution Check I–VII có bằng chứng trước khi trạng thái Verified | Tất cả stories; SC-001–010, NFR-001–010 |

## Planning Handoff — Nội dung bắt buộc trong `plan.md` Giai đoạn 02

`plan.md` phải có **Constitution Check I–VII** và quyết định kỹ thuật có lý do/trade-off cho các nhóm sau:

1. **Windows/JDK baseline & M0 exit:** xác minh/giải quyết các gate chưa xong của 001; xác định môi trường test hai máy, version và kết quả có thể lặp lại.
2. **Topology & discovery:** listener/connect roles, kết nối trực tiếp, endpoint/address selection, IPv4/IPv6 hỗ trợ, port mặc định/cấu hình, thay IP, firewall private network; manual IP fallback; quyết định discovery có làm trong M1 hay hoãn.
3. **Transport & lifecycle:** TCP/UDP choice, connection ownership, shutdown, cancellation, timeout, bounded executor/queues, giới hạn session, retry/backoff, duplicate/simultaneous connection policy.
4. **Handshake & trust:** schema handshake, version negotiation, device ID và display name, session ID, ranh giới identity assertion với authenticated identity; giới hạn bảo mật và đường nâng cấp cho History Sync chỉ sau auth+authorization.
5. **Protocol wire contract:** framing, payload schema/UTF-8, hard size bounds, malformed/unknown frames, valid/invalid sample payloads, fail/close behavior, test harness.
6. **Application/UI:** các trạng thái mà UI được phép hiển thị, event callbacks/ports, cách báo lỗi, kiểm thử không block EDT, giữ architecture boundaries từ M0.
7. **Verification:** unit parser tests, test 2-process loopback, 2-device real LAN, disconnect mid-frame, port conflict, timeout, identity mismatch, cleanup, security limitations, JDK baseline; mục tiêu hiệu năng được đo thay vì khẳng định tùy tiện.
8. **Dependencies & out-of-scope:** chỉ rõ feature 03 P2P chat, 04 Group, 05 History sync, 06/07 File/Media chưa triển khai; các dependency mới cần biện minh.

**Nội dung có thể làm rõ trong planning, không nên ngầm giả định:** auto-discovery có bắt buộc cho mốc M1 không (spec hiện yêu cầu *manual IP baseline*, discovery tùy chọn); inbound connection có cần user consent/pairing trước khi cho phép diagnostics; chính sách kết nối trùng/đồng thời, timeout/session budget, cơ chế bảo vệ phù hợp môi trường LAN không tin cậy và phạm vi xác thực ở 002 so với 005. Nếu việc thay đổi những quyết định này làm thay đổi **hành vi người dùng hoặc acceptance criteria**, cập nhật `spec.md` và xin duyệt trước implementation.

## Definition of Ready & Done

**Ready for `plan.md` 002:** những ràng buộc CL-01…04 đã xác định; scope/AC và edge cases của M1 được mô tả; tồn đọng M0 được liệt kê và không được lờ đi. Không bắt buộc M0 Verified chỉ để **viết tài liệu plan/tasks**.

**Ready to implement 002:** M0 đạt điều kiện bàn giao theo Constitution hoặc có quyết định chính thức ghi rõ blocker/ngoại lệ hợp lệ; `plan.md` đã ghi Constitution Check, port/discovery/transport, trust model, performance bounds và test strategy; `tasks.md` được chia nhỏ.

**M1 Verified chỉ khi:** AC-02-A…K đều có bằng chứng thực thi hoặc được ghi tình trạng cần xử lý; bắt buộc có kết quả hai máy thật, negative tests và resource cleanup; không có vi phạm MUST của Constitution, không tuyên bố peer authentication/encryption nếu chưa chứng minh. **Không được coi việc tạo tài liệu này là đã triển khai hoặc kiểm thử kết nối mạng.**

**Current status:** Documentation only. Giai đoạn 02 chưa có `plan.md`, `tasks.md`, Java networking code hoặc test network thực thi.
