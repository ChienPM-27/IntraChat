# Feature Specification: Nền tảng kết nối LAN P2P (Giai đoạn 02)

**Feature Branch:** `002-lan-networking` (tên đề xuất; chưa tạo nhánh Git)
**Created:** 2026-11-21
**Status:** Clarified / Planned — 4 quyết định CL-02-01…04 đã chốt và [plan.md](plan.md) đã được tạo; chưa triển khai chức năng mạng
**Input:** [ROADMAP — Giai đoạn 02](../../ROADMAP.md), [nền tảng Giai đoạn 01](../001-project-foundation/spec.md)
**Governing:** [IntraChat Constitution](../../.specify/memory/constitution.md) · [AGENTS](../../AGENTS.md)
**Baseline:** [Protocol v1 draft](../001-project-foundation/contracts/protocol-v1-draft.md) · [Architecture](../../docs/architecture.md)

## Overview

**Mục tiêu M1 — LAN Connected:** Hai phiên IntraChat trên hai máy Windows trong cùng mạng LAN **kết nối trực tiếp**, xác định phiên/peer, trao đổi được **thông điệp thử nghiệm có phiên bản** theo hai chiều, nhận biết mất kết nối và phục hồi hoặc báo lỗi một cách có kiểm soát. Toàn bộ thao tác kết nối, chờ đợi và đóng phiên không làm treo giao diện.

**Topology được xác nhận:** **direct peer-to-peer (P2P) trong LAN**. Không yêu cầu server trung tâm, dịch vụ cloud hoặc Internet. Trong phạm vi Giai đoạn 02, mỗi máy vừa có thể nhận yêu cầu kết nối vừa có thể chủ động kết nối; đây **không** phải tuyên bố hệ thống đã phi tập trung hoàn toàn ở mức Group/History. Dự án nhắm tới **Windows 11 x64**, sử dụng bộ khung Java/Swing/Maven có từ Giai đoạn 01.

**Trong phạm vi:** listener kết nối trực tiếp; **nhập IP/port thủ công, không tự khám phá peer**; **hỏi Chấp nhận/Từ chối mỗi kết nối đến trước khi lập session**; danh tính thiết bị chỉ tự công bố, **chưa xác thực/mã hóa** và có cảnh báo; handshake/version negotiation; trạng thái phiên; payload chẩn đoán hai chiều; framing/giới hạn; I/O bất đồng bộ, timeout, ngắt kết nối, **kết nối lại do người dùng chủ động**, cleanup và hướng dẫn firewall; kiểm thử loopback và hai máy LAN thật.

**Ngoài phạm vi:** **auto-discovery/broadcast/multicast, tự kết nối lại, auto-accept kết nối đến, xác thực/ghép cặp thiết bị, mã hóa transport**; tin nhắn chat người dùng, danh sách hội thoại và `sent/delivered/read` (03); Group (04); lưu/đồng bộ History (05); File (06); Image/Video/Sound và gọi trực tiếp (07 hoặc ngoài phạm vi); Internet relay/server công cộng; tự động mở cổng firewall hay tài khoản cloud. Thông điệp trong M1 chỉ là **chẩn đoán kỹ thuật**, không phải chat MVP.

**Phân định trạng thái:** `CONNECTED` nghĩa là đã lập một **transport session đáp ứng handshake** theo hợp đồng, **không** chứng minh peer là người/thiết bị đáng tin, không có nghĩa “đã đọc tin” hoặc “đã đồng bộ lịch sử”.

## Confirmed Constraints & Carry-over

| ID | Nguồn | Ràng buộc |
| --- | --- | --- |
| CL-01 | [001 clarification](../001-project-foundation/spec.md#clarifications-2026-11-21) | P2P **trực tiếp** qua LAN, không bắt buộc server điều phối/relay |
| CL-02 | 001 | Windows là hệ điều hành mục tiêu; Windows 11 x64 là nền tảng nghiệm thu đầu tiên |
| CL-03 | 001 | Video/Sound là gửi/nhận/phát file, không có cuộc gọi trong M1 |
| CL-04 | 001 | History về sau **đồng bộ giữa máy được phép**; M1 không truyền, đọc hoặc lưu History |
| M0 gate | [Acceptance report](../001-project-foundation/evidence/m0-acceptance-report.md) | Giai đoạn 01 **38/41 tasks**; build/test đã chạy trên JDK 25 với `--release 21`, nhưng Java 21 runtime và offline UI test chưa đủ bằng chứng. Viết spec/plan 02 được phép, song **không coi M0 đã Verified hoặc M1 đã triển khai**. |

**Yêu cầu định hướng có sẵn, không phải công nghệ đã triển khai:** `plan.md` Giai đoạn 01 đề xuất TCP + JSON UTF-8, 4-byte length prefix và giới hạn control frame 1 MiB. **Clarify 02 chốt chỉ nhập IP/port thủ công; discovery không triển khai trong M1**. Cổng mặc định, timeout, parser, handshake, quy tắc chấp nhận và giới hạn tài nguyên do **plan Giai đoạn 02** lựa chọn có lý do trước khi code.

## Clarifications (2026-11-21) — user-confirmed behavior

| Mã | Câu hỏi đã làm rõ | Quyết định đã xác nhận | Hệ quả nghiệm thu M1 |
| --- | --- | --- | --- |
| **CL-02-01** | Discovery peer bằng cách nào? | **Chỉ nhập IP/port thủ công** | Không broadcast/multicast/scan/tự tìm thiết bị; GUI phải có Connect theo endpoint |
| **CL-02-02** | Kết nối đến có tự nhận không? | **Người nhận phải bấm Chấp nhận/Từ chối** | Inbound TCP có thể tồn tại tạm để đọc HELLO tối thiểu và hiển thị prompt, nhưng chưa được lập session/trao đổi payload chẩn đoán cho tới khi người dùng chấp nhận; từ chối/timeout/đóng cửa sổ phải giải phóng socket |
| **CL-02-03** | Xác thực và bảo mật phiên M1? | **Chỉ kết nối chẩn đoán, chưa xác thực peer, chưa mã hóa transport; phải cảnh báo bảo mật** | Chấp nhận thủ công là đồng ý với endpoint tự khai, **không** là pairing/trust/authorization. Không truyền dữ liệu thật, History, File/Group; không hiển thị “đã xác thực” |
| **CL-02-04** | Sau khi mất kết nối có retry tự động? | **Người dùng chủ động bấm Kết nối lại** | Không auto-reconnect hay backoff chạy ngầm; cho phép thử lại thủ công, chống bấm lặp tạo session/worker trùng |

**Ràng buộc an toàn:** Vì transport M1 là **chưa xác thực và chưa mã hóa**, người dùng không được xem nó là kênh bảo mật; prompt Chấp nhận phải hiển thị **endpoint IP:port** và tên/ID peer **tự khai**, kèm cảnh báo rõ. Chỉ sau quyết định Chấp nhận và handshake hợp lệ mới chuyển trạng thái session thành CONNECTED. Một kết nối bị từ chối hoặc hết hạn phải đóng, không đưa payload chẩn đoán lên UI. Thời gian/giới hạn cụ thể là quyết định implementation của `plan.md`.

**Tác động các giai đoạn sau:** Các yêu cầu History sync nhiều máy của CL-04 vẫn giữ nguyên, nhưng trước khi triển khai 005 phải có cơ chế xác thực peer và kiểm tra quyền hội thoại độc lập với quyền Chấp nhận thủ công ở M1. M1 **không** gửi/nhận History.

## User Scenarios & Testing *(mandatory)*

### User Story 1 — Kết nối trực tiếp với một máy trong LAN (Priority: P1)

Là người dùng đang sử dụng IntraChat trên Windows, tôi muốn nhập địa chỉ của một máy trong cùng LAN và chủ động kết nối mà không cần Internet hay server trung gian để biết hai phiên đã có thể giao tiếp.

**Why this priority:** Đây là năng lực nền tảng để xây dựng mọi tính năng P2P ở giai đoạn sau.

**Independent Test:** Dùng hai phiên trên hai máy LAN (và test loopback hai process) để xác nhận kết nối trực tiếp, đổi chiều kết nối, không phụ thuộc dịch vụ cloud.

**Acceptance Scenarios:**

1. **Given** peer B đang lắng nghe và A biết IP/cổng của B, **When** A yêu cầu kết nối và **B bấm Chấp nhận**, **Then** hai phía hoàn tất handshake và thấy session đã thiết lập đúng endpoint; nếu B chưa Chấp nhận thì chưa được xem là connected.
2. **Given** hai máy cùng mạng LAN nhưng không có Internet, **When** A nhập IP/port của B và B bấm Chấp nhận yêu cầu đến, **Then** kết nối P2P LAN hoạt động; không cần tự khám phá thiết bị.
3. **Given** người dùng nhập địa chỉ hoặc cổng không hợp lệ, **When** yêu cầu kết nối, **Then** dữ liệu bị từ chối với thông báo hữu ích; UI vẫn phản hồi và không tạo phiên “connected” giả.
4. **Given** B không chạy hoặc không thể tiếp cận, **When** A thử kết nối, **Then** A nhận kết quả thất bại/timeout trong một khoảng được chặn và có thể thử lại.
5. **Given** B đang lắng nghe, **When** B kết nối ngược sang A sau khi A sẵn sàng nhận, **Then** luồng tương tự hoạt động, không buộc một máy duy nhất đóng vai trò server cố định.

### User Story 2 — Nhận diện peer và phiên bản tương thích (Priority: P1)

Là người sử dụng hoặc lập trình viên, tôi cần biết một kết nối thực sự thuộc phiên nào, peer khai báo danh tính gì và có dùng giao thức phù hợp hay không để tránh gán nhầm người hoặc xử lý message không tương thích.

**Why this priority:** Một socket mở thành công chưa đủ để định nghĩa một phiên giao tiếp an toàn và đúng hợp đồng.

**Independent Test:** Dùng hai tiến trình với cùng/khác version, ID trùng tên hiển thị, handshake bị thiếu/sai; kiểm tra sự kiện phiên hoặc lỗi trả về mà không gửi tin chat.

**Acceptance Scenarios:**

1. **Given** hai phiên khai báo cùng version hỗ trợ, **When** B xem thông tin peer tự khai và bấm Chấp nhận, sau đó handshake hoàn tất, **Then** session mới chuyển sang kết nối hợp lệ với version và peer/device ID đúng cú pháp; việc bấm Chấp nhận **không phải xác thực**.
2. **Given** hai peer có cùng `displayName` nhưng `deviceId` khác, **When** thiết lập phiên, **Then** hệ thống lưu/phân biệt theo ID, không dùng tên hiển thị làm khóa danh tính.
3. **Given** peer có version không tương thích hoặc handshake thiếu trường, **When** thương lượng kết nối, **Then** từ chối session có kiểm soát và báo lý do; không tiếp tục trao đổi message với schema không tương thích.
4. **Given** một peer tự khai tên/ID và B đã bấm Chấp nhận kết nối đến, **When** hoàn tất handshake ở mức transport, **Then** UI/API vẫn cảnh báo **chưa xác thực, chưa mã hóa**, không coi peer là **đáng tin** hoặc **được quyền đọc History**.
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
4. **Given** kết nối mất và LAN khả dụng trở lại, **When** người dùng **bấm Kết nối lại** với IP/port đã cung cấp, **Then** ứng dụng thử tạo session mới; **không tự động reconnect** hoặc tạo session trùng.
5. **Given** nhiều phiên kết nối liên tiếp hoặc đầu vào sai, **When** số lượng thao tác đạt ngưỡng được quy định, **Then** số tài nguyên và hàng đợi vẫn có giới hạn; lỗi của một peer không kéo sập peer khác.

### User Story 5 — Dùng được trong điều kiện LAN thực tế (Priority: P2)

Là người cài đặt IntraChat trong phòng máy, tôi cần biết địa chỉ/cổng của máy mình, hướng dẫn firewall Windows và giới hạn của cơ chế tìm peer để xác định nguyên nhân kết nối thất bại.

**Why this priority:** Bản chạy trên loopback chưa chứng minh được hoạt động trên hai máy LAN thật.

**Independent Test:** Hai máy Windows trên cùng subnet/phạm vi LAN được hỗ trợ; không Internet; thử địa chỉ thủ công, cấu hình mạng phù hợp và trường hợp Firewall chặn.

**Acceptance Scenarios:**

1. **Given** kết nối đang lắng nghe, **When** người dùng xem thông tin kết nối, **Then** có thể biết cổng lắng nghe và địa chỉ cần cung cấp cho peer theo giao diện/hướng dẫn.
2. **Given** Giai đoạn 02 **không có auto-discovery**, **When** người dùng nhập IP/port của peer, **Then** việc kết nối hoàn toàn qua địa chỉ thủ công; giao diện không hiển thị danh sách peer tự tìm thấy giả.
3. **Given** Firewall/Private network chặn kết nối, **When** người dùng thử kết nối, **Then** ứng dụng hướng dẫn kiểm tra mạng/cổng an toàn, không lặng lẽ thay đổi firewall hoặc mở truy cập public Internet.
4. **Given** hai máy LAN thật, **When** kiểm thử chức năng kết nối/payload hai chiều, **Then** ghi lại môi trường, địa chỉ không nhạy cảm, kết quả và hạn chế để nghiệm thu M1; không coi loopback là tương đương test hai máy.
5. **Given** người dùng xem trạng thái bảo mật, **When** transport chưa có xác thực/mã hóa đã được kiểm chứng, **Then** cảnh báo rõ hạn chế và không gợi ý gửi dữ liệu nhạy cảm.

### Edge Cases

- Một máy nhập **chính địa chỉ của mình**; ứng dụng không ghi nhận thành peer từ xa ngoài ý muốn.
- Port đang được ứng dụng khác sử dụng; listener phải báo lỗi và không thể hiện trạng thái “đang nghe” giả.
- Hai peer cùng tên hiển thị, một peer đổi tên, peer khai báo `deviceId` bất thường hoặc trùng ID trên hai kết nối.
- Người dùng **Từ chối** kết nối đến, **không trả lời hoặc hết hạn hộp thoại**, đóng app khi đang chờ quyết định; handshake đến chậm/mất dở, sai version, unknown message type, field null/thiếu, JSON/UTF-8 hỏng. Kết nối chưa được chấp nhận không có quyền gửi payload chẩn đoán.
- TCP đọc một frame bị chia nhỏ; hai frame gộp; khung không hoàn chỉnh đến khi timeout; length âm/zero/over limit.
- Dữ liệu đến ngay trước hoặc sau disconnect; gửi khi session đã đóng; queue đầy; ngoại lệ I/O; người dùng bấm Kết nối lại liên tục; **không tự retry**.
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
- **FR-003:** Người dùng **MUST** nhập/thiết lập **IP và cổng thủ công** để kết nối; đây là cơ chế duy nhất của M1, không phụ thuộc discovery.
- **FR-004:** Hệ thống **MUST** kiểm tra định dạng địa chỉ và cổng, từ chối giá trị sai và hiển thị kết quả kết nối, bao gồm đích không tồn tại hoặc không tiếp cận được.
- **FR-005:** Hệ thống **MUST** cho người dùng xem hoặc tra cứu địa chỉ/cổng đang lắng nghe, và giải thích trường hợp không mở được listener (port conflict, permission, firewall).
- **FR-006:** M1 **MUST** chỉ dùng **địa chỉ IP và cổng nhập thủ công**, **MUST NOT** chạy auto-discovery, tự quét/broadcast thiết bị hoặc hiển thị peer được tìm tự động. Không có discovery **MUST NOT** cản trở luồng kết nối thủ công.

**Phiên, danh tính và versioning**

- **FR-007:** Thiết lập kết nối **MUST** có handshake/version negotiation; với kết nối đến, **MUST** đợi người dùng **Chấp nhận** trước khi thông báo connected hoặc trao đổi payload chẩn đoán; Từ chối, hết thời gian hoặc đóng app khi chờ **MUST** hủy phiên và giải phóng tài nguyên.
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
- **FR-020:** Hệ thống **MUST** hiển thị Connecting/Connected/Failed/Disconnected và **yêu cầu đến đang chờ người dùng duyệt**, có Chấp nhận/Từ chối, endpoint và identity **tự khai không đáng tin**; mọi thao tác chờ kéo dài có timeout/hủy.
- **FR-021:** Mỗi session/operation mạng **MUST** có timeout, cách hủy và cleanup tương ứng; đóng cửa sổ, người dùng ngắt hoặc peer disconnect phải thu hồi sockets/streams/workers có liên quan.
- **FR-022:** Sau disconnect/timeout, M1 **MUST NOT** tự động reconnect. Người dùng **MUST** có nút/thao tác **Kết nối lại** theo IP/port đã biết, không phải khởi động lại app; nhiều lần bấm liên tục **MUST NOT** làm tạo session/worker trùng hoặc không giới hạn.
- **FR-023:** Hệ thống **MUST** giới hạn số phiên đồng thời, **yêu cầu đang chờ người dùng chấp nhận**, tác vụ đang chờ và bộ đệm/hàng đợi; peer chưa được duyệt hoặc gây lỗi **MUST NOT** kéo theo treo toàn ứng dụng.
- **FR-024:** Các trạng thái kết nối **MUST** phản ánh sự kiện đã xác nhận; không hiển thị `connected` cho socket chưa handshake hoặc socket đã được phát hiện là đóng.

**Security, Windows và minh bạch**

- **FR-025:** Mọi inbound data và identity claim **MUST** được coi là untrusted; mỗi yêu cầu đến **MUST** được người dùng quyết định Chấp nhận/Từ chối trong giới hạn chờ, không có auto-accept; không tự động cho peer mới quyền với History, File, Group hoặc dữ liệu người dùng.
- **FR-026:** M1 **chỉ kết nối chẩn đoán, chưa xác thực danh tính và chưa mã hóa transport**; UI/README **MUST** cảnh báo rõ trước khi sử dụng, kể cả với peer đã được người dùng Chấp nhận. M1 **MUST NOT** tuyên bố session “đã bảo mật”, “đã xác thực”, “đã ghép cặp” hoặc “đã được cấp quyền”.
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
- **SC-005:** **3/3 lần** người dùng **chủ động bấm Kết nối lại** sau lỗi/peer tái khởi động có kết quả đúng, không phải khởi động lại app và **0 lần reconnect tự động**.
- **SC-006:** **1/1 luồng kết nối chỉ nhập IP/port thủ công** thành công với peer hợp lệ; **0 tiến trình/quét/broadcast auto-discovery** trong M1.
- **SC-007:** Có tài liệu tái lập **100%** bước cần thiết cho Windows: xem IP/port, cấu hình firewall private network, phân biệt lỗi firewall và lỗi peer, ranh giới không Internet.
- **SC-008:** Kiểm thử xác nhận **0 lần** hiển thị `connected` trước khi người nhận Chấp nhận và handshake hợp lệ, hoặc `delivered/read/authenticated/encrypted/synced` khi chưa có bằng chứng tương ứng.
- **SC-009:** Automated tests giai đoạn 02 **thực thi và pass** cùng các test M0; có bằng chứng cho exchange hợp lệ, input sai, timeout/disconnect và resource cleanup, không dựa vào public Internet.
- **SC-010:** Mọi lỗi/ngắt kết nối trong bộ test chuẩn đều có exit/cleanup kiểm chứng, không để lại socket/worker sử dụng tài nguyên sau kết thúc theo policy đo trong `plan.md`.

## Assumptions & Dependencies

- Dự án đã có Java/Swing foundation, Maven Wrapper và 9 test M0; **M0 vẫn In Progress** đến khi các gate Java 21 runtime/offline GUI/acceptance được giải quyết hoặc có quyết định chính thức.
- Mạng LAN cho phép hai máy tiếp cận trực tiếp bằng địa chỉ/cổng trên network profile được cấu hình đúng; không giả định kết nối xuyên Internet, NAT hoặc Wi-Fi client isolation.
- **Chỉ nhập IP/port thủ công** trong M1; không auto-discovery. Mọi kết nối đến cần người dùng Chấp nhận/Từ chối; không trả lời trong giới hạn phải đóng yêu cầu. Không auto-reconnect; người dùng chủ động thử lại.
- Kết nối chỉ dùng payload kỹ thuật/synthetic, không yêu cầu gửi dữ liệu người dùng thật hoặc tài khoản cloud. Không lưu History hay file người dùng.
- **Không có xác thực peer, pairing hay mã hóa transport trong M1** theo CL-02-03. Kết nối chỉ ở mức transport/diagnostics với cảnh báo bảo mật; nút Chấp nhận không phải là xác thực thiết bị.
- Tên class Java, API, cổng mặc định, socket timeouts, loại serializer và các ngân sách tài nguyên cụ thể thuộc **technical planning**, không quyết định bằng cách giả định trong spec.

## Acceptance Traceability — M1

| ID | Điều kiện nghiệm thu | Truy vết |
| --- | --- | --- |
| AC-02-A | Hai máy LAN kết nối P2P trực tiếp, trao đổi payload kỹ thuật hai chiều không Internet/server | US1, US3; FR-001–003, FR-012–014, FR-029; SC-001 |
| AC-02-B | Chỉ nhập IP/port thủ công, không auto-discovery, có lỗi có nghĩa khi peer không tìm thấy | US1, US5; FR-003–006, SC-006 |
| AC-02-C | Phải được người nhận Chấp nhận kết nối đến trước handshake hoàn tất; version/identity hợp lệ, display name không phải ID hoặc authentication | US2; FR-007–011, SC-008 |
| AC-02-D | Parser xử lý frame chia nhỏ/gộp, Unicode, metadata và giới hạn bytes | US3; FR-013–017; SC-004 |
| AC-02-E | Unknown version/type, JSON sai, frame quá cỡ và dữ liệu thiếu bị từ chối có kiểm soát | US2, US3; FR-010, FR-015–016, SC-004 |
| AC-02-F | Timeout, Từ chối/cancel, đóng peer và kết nối lại **thủ công** có trạng thái đúng, worker/socket cleanup | US1, US4; FR-019–024, SC-003, SC-005, SC-010 |
| AC-02-G | GUI không chặn, hiển thị rõ trạng thái/lỗi; không báo delivered/read/authenticated giả | US2, US4; FR-009, FR-018, FR-020, FR-024–026, SC-008 |
| AC-02-H | Có tài liệu Windows IP/port/Firewall/discovery limitations, không tự mở public firewall | US5; FR-005–006, FR-027, SC-007 |
| AC-02-I | Test loopback tối thiểu hai peer/process, kiểm thử LAN thật hai máy, không cần cloud, có log kết quả | US1, US3–5; FR-029; SC-001–002, SC-009 |
| AC-02-J | M1 chỉ chẩn đoán chưa xác thực/mã hóa (kể cả peer được Chấp nhận); không History/File/Media, không cấp quyền qua ID tự khai; cảnh báo bảo mật | US2, US5; FR-025–026, FR-028, FR-030; SC-008 |
| AC-02-K | Giai đoạn 01 đạt gate yêu cầu, kết quả M1 và Constitution Check I–VII có bằng chứng trước khi trạng thái Verified | Tất cả stories; SC-001–010, NFR-001–010 |

## Planning Handoff — Nội dung bắt buộc trong `plan.md` Giai đoạn 02

`plan.md` phải có **Constitution Check I–VII** và quyết định kỹ thuật có lý do/trade-off cho các nhóm sau:

1. **Windows/JDK baseline & M0 exit:** xác minh/giải quyết các gate chưa xong của 001; xác định môi trường test hai máy, version và kết quả có thể lặp lại.
2. **Topology & addressing:** listener/connect roles, kết nối trực tiếp, IP/port nhập **thủ công duy nhất** theo CL-02-01, IPv4/IPv6 phạm vi hỗ trợ, bind adapter, port mặc định/cấu hình, thay IP và Windows Private firewall. **Không thiết kế auto-discovery cho M1.**
3. **Transport & lifecycle:** TCP/UDP choice, ownership, shutdown/cancellation/timeout, bounded workers/queues và session, **chỉ reconnect thủ công** theo CL-02-04 (không tự backoff/retry), duplicate/simultaneous connection policy.
4. **Handshake & approval/trust:** schema handshake, **Chấp nhận/Từ chối kết nối đến trước session** theo CL-02-02, version negotiation, device ID/display name, session ID, **chưa auth/chưa mã hóa có cảnh báo** theo CL-02-03; History Sync chỉ sau identity verification + conversation ACL.
5. **Protocol wire contract:** framing, payload schema/UTF-8, hard size bounds, malformed/unknown frames, valid/invalid sample payloads, fail/close behavior, test harness.
6. **Application/UI:** các trạng thái mà UI được phép hiển thị, event callbacks/ports, cách báo lỗi, kiểm thử không block EDT, giữ architecture boundaries từ M0.
7. **Verification:** unit parser tests, test 2-process loopback, 2-device real LAN, disconnect mid-frame, port conflict, timeout, identity mismatch, cleanup, security limitations, JDK baseline; mục tiêu hiệu năng được đo thay vì khẳng định tùy tiện.
8. **Dependencies & out-of-scope:** chỉ rõ feature 03 P2P chat, 04 Group, 05 History sync, 06/07 File/Media chưa triển khai; các dependency mới cần biện minh.

**Clarify đã chốt (2026-11-21):** chỉ IP/port thủ công, người dùng Chấp nhận/Từ chối kết nối đến, chỉ chẩn đoán chưa xác thực/mã hóa (có cảnh báo), người dùng chủ động kết nối lại. **Không mở lại bốn lựa chọn này trong plan**. Những quyết định còn lại thuộc HOW: TCP/framing/port, chính sách phiên trùng, timeout, giới hạn chờ và test. Nếu muốn thay đổi hành vi đã xác nhận, cập nhật spec trước.

## Definition of Ready & Done

**Ready for `plan.md` 002:** CL-01…04 và **CL-02-01…04** đã xác nhận; scope/AC và edge cases của M1 được mô tả; tồn đọng M0 không được bỏ qua. Không cần M0 Verified chỉ để **viết tài liệu** plan/tasks.

**Ready to implement 002:** M0 đạt điều kiện bàn giao theo Constitution hoặc có quyết định chính thức ghi rõ blocker/ngoại lệ hợp lệ; `plan.md` đã ghi Constitution Check, port/discovery/transport, trust model, performance bounds và test strategy; `tasks.md` được chia nhỏ.

**M1 Verified chỉ khi:** AC-02-A…K đều có bằng chứng thực thi hoặc được ghi tình trạng cần xử lý; bắt buộc có kết quả hai máy thật, negative tests và resource cleanup; không có vi phạm MUST của Constitution, không tuyên bố peer authentication/encryption nếu chưa chứng minh. **Không được coi việc tạo tài liệu này là đã triển khai hoặc kiểm thử kết nối mạng.**

**Current status:** Clarified; đã có [plan.md](plan.md) ở mức thiết kế; chưa có `tasks.md`, Java networking code hoặc network tests thực thi.
