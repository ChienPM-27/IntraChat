# Feature Specification: Khởi tạo dự án IntraChat (Giai đoạn 01)

**Feature Branch:** `001-project-foundation` (dự kiến; chưa tạo nhánh Git)
**Created:** 2026-11-21
**Status:** Clarified — đã có plan và tasks; chưa triển khai hoặc kiểm thử ứng dụng
**Clarification Date:** 2026-11-21
**Input:** Yêu cầu xây dựng nền tảng cho ứng dụng Chat mạng LAN bằng Java; [ROADMAP — giai đoạn 01](../../ROADMAP.md)
**Governing:** [Constitution](../../.specify/memory/constitution.md) · [AGENTS](../../AGENTS.md)

## Overview

**Mục tiêu:** Đạt mốc **M0 — Foundation Ready**: một dự án Java có thể xây dựng, chạy và kiểm thử từ checkout sạch; khung ứng dụng hoạt động; kiến trúc và hợp đồng thông điệp sơ bộ đủ làm đầu vào cho giai đoạn 02 (kết nối LAN).

**Trong phạm vi:** hướng dẫn môi trường; quy trình build/run/test; điểm khởi chạy; màn hình hoặc giao diện trạng thái tối thiểu; kiến trúc phân trách nhiệm; mô hình dữ liệu khái niệm; bản đặc tả giao thức sơ bộ; kiểm thử cơ sở và tài liệu bàn giao.

**Ngoài phạm vi:** kết nối socket, peer discovery, nhắn tin P2P/Group thật; lưu/đồng bộ History, truyền File, hiển thị/phát Image/Video/Sound; trạng thái delivered/read thật; đăng nhập cloud, gọi video hoặc gọi thoại. Những việc này thuộc các giai đoạn 02–08.

## Clarifications (2026-11-21)

Bốn quyết định **đã được người yêu cầu xác nhận** và là ràng buộc cho `plan.md`:

| Mã | Câu hỏi | Quyết định | Tác động và phạm vi |
| --- | --- | --- | --- |
| **CL-01** | Mạng LAN kết nối theo mô hình nào? | **P2P trực tiếp** | Tin nhắn trao đổi trực tiếp giữa các máy; **không phụ thuộc server trung tâm**. Discovery, handshake, transport và Group được thiết kế chi tiết ở giai đoạn sau. |
| **CL-02** | Hệ điều hành mục tiêu? | **Windows** | Windows là nền tảng bắt buộc của bản đầu tiên; các hệ điều hành khác không thuộc nghiệm thu giai đoạn này. |
| **CL-03** | Video/Sound có nghĩa gì? | **Gửi/nhận và phát tệp media** | Không bao gồm gọi âm thanh/video thời gian thực. Media kế thừa file transfer ở giai đoạn 06–07. |
| **CL-04** | History lưu và đồng bộ ở đâu? | **Đồng bộ nhiều máy qua LAN** | Lịch sử phải có khả năng sao chép/đối soát giữa **các peer được phép tham gia hội thoại** khi kết nối; không phụ thuộc lưu trữ tập trung hoặc Internet. Thiết kế tiền đề ở giai đoạn 01, hiện thực ở giai đoạn 05. |

**Diễn giải CL-04 để giới hạn phạm vi:** mỗi thiết bị giữ bản sao cục bộ của các cuộc trò chuyện mà nó được phép lưu; khi các thiết bị tham gia liên lạc lại, chúng đối soát những sự kiện còn thiếu để tiến tới nhất quán. Không có bảo đảm tất cả thiết bị đồng bộ tức thì khi offline; không cho phép phát tán lịch sử tới peer ngoài cuộc hội thoại. Cơ chế xác minh quyền, quản lý thiết bị cùng người dùng, xung đột cập nhật/xóa và chính sách lưu dữ liệu phải được thiết kế rõ ở giai đoạn 05; chưa coi là đã có tính năng bảo mật. Không cần server trung tâm, nhưng khi không có peer giữ bản sao trực tuyến thì không thể truy hồi dữ liệu tức thì.

**Những vấn đề thuộc thiết kế kỹ thuật, không cần hỏi lại:** phiên bản JDK, Swing/JavaFX, Maven/Gradle, cách đóng gói, giao thức wire format và cấu trúc package sẽ được lựa chọn có lý do ở `plan.md`.

## User Scenarios & Testing *(mandatory)*

### User Story 1 — Thành viên mới chạy được dự án (Priority: P1)

Là người phát triển mới, tôi cần hướng dẫn đáng tin cậy để tải mã nguồn, cài đúng điều kiện tiên quyết, build, chạy và kiểm thử mà không cần biết các thiết lập cá nhân của người tạo dự án.

**Why this priority:** Các chức năng LAN không thể xây dựng ổn định nếu nền tảng không tái lập được.

**Independent Test:** Thực hiện đầy đủ hướng dẫn từ một checkout sạch trên môi trường đã công bố.

**Acceptance Scenarios:**

1. **Given** máy đã đáp ứng điều kiện tiên quyết và checkout sạch, **When** chạy bước build đã hướng dẫn, **Then** build thành công và tạo ra kết quả có thể chạy.
2. **Given** build thành công, **When** chạy lệnh khởi động, **Then** ứng dụng nhận diện là IntraChat xuất hiện hoặc công bố trạng thái chạy rõ ràng.
3. **Given** đang chạy ứng dụng, **When** người dùng thoát, **Then** tiến trình ứng dụng kết thúc bình thường.
4. **Given** thiếu một điều kiện tiên quyết, **When** thử làm theo hướng dẫn, **Then** có thông tin nhận biết được điều kiện còn thiếu; không cần đoán thiết lập IDE riêng.

### User Story 2 — Khung ứng dụng trung thực và dễ kiểm tra (Priority: P1)

Là người dùng thử nghiệm, tôi muốn mở được IntraChat ngay cả khi chưa có thiết bị khác, thấy rõ đây là bản chưa kết nối và đóng chương trình an toàn.

**Why this priority:** Xác nhận vòng đời khởi động/thoát mà không nhầm tính năng dự kiến với tính năng đã hoạt động.

**Independent Test:** Mở và đóng ứng dụng nhiều lần khi không có Internet hoặc LAN peer.

**Acceptance Scenarios:**

1. **Given** không có peer nào hoạt động, **When** mở ứng dụng, **Then** vẫn mở được và thể hiện trạng thái chưa kết nối/chưa hỗ trợ chat.
2. **Given** khung ứng dụng đang hiển thị, **When** người dùng thực hiện lệnh thoát, **Then** ứng dụng đóng mà không để lại tiến trình của nó.
3. **Given** chưa có kết nối thật, **When** xem trạng thái, **Then** không có chỉ báo online, delivered hoặc read gây hiểu nhầm.

### User Story 3 — Nhóm có cơ sở chung để triển khai LAN (Priority: P1)

Là lập trình viên tiếp nhận giai đoạn 02–07, tôi muốn biết các thành phần chịu trách nhiệm gì và những trường thông điệp nào được thống nhất để tránh thiết kế P2P, Group, Time, Sender, State và File không tương thích.

**Why this priority:** Giao thức và mô hình dữ liệu là nền chung cho toàn bộ 11 chức năng.

**Independent Test:** Review tài liệu kiến trúc, mô hình và protocol draft; mô tả được ví dụ tin nhắn P2P, tin nhóm và thông báo tệp **trên giấy**, không yêu cầu truyền mạng thật.

**Acceptance Scenarios:**

1. **Given** tài liệu kiến trúc, **When** người mới đọc, **Then** chỉ ra được trách nhiệm UI, service, model, protocol, transport và persistence cùng ranh giới tương tác.
2. **Given** bản hợp đồng thông điệp, **When** diễn giải một tin P2P và một tin Group, **Then** chỉ ra được loại, ID tin, người gửi, đích nhận, thời gian, nội dung/metadata và version.
3. **Given** hai người trùng tên hoặc người dùng đổi tên hiển thị, **When** xem định nghĩa sender, **Then** phân biệt được identity ổn định với display name.
4. **Given** dữ liệu/phiên bản không hợp lệ, **When** xem protocol draft, **Then** có quy tắc xử lý an toàn được mô tả, dù parser mạng chưa được triển khai.
5. **Given** hai peer P2P được phép chia sẻ một hội thoại nhưng một peer offline, **When** review thiết kế History, **Then** chỉ ra được cách biểu diễn sự kiện bền vững và định danh phục vụ đồng bộ sau kết nối lại, cùng giới hạn về quyền truy cập và tính nhất quán cuối cùng; không yêu cầu thực thi sync trong giai đoạn 01.

### User Story 4 — Có bằng chứng kiểm thử và điều kiện chuyển giai đoạn (Priority: P2)

Là người quản lý/kiểm thử dự án, tôi muốn có lệnh test, test mẫu thực thi được, tài liệu quyết định kỹ thuật và checklist nghiệm thu để xác định đúng khi nào chuyển từ giai đoạn 01 sang 02.

**Why this priority:** Tránh đánh dấu đã hoàn tất khi project chưa build hoặc chưa được kiểm chứng.

**Independent Test:** Chạy quy trình test, thử một phép kiểm tra cố ý thất bại trong bản làm việc tạm, rồi đối chiếu đủ các điều kiện nghiệm thu M0.

**Acceptance Scenarios:**

1. **Given** project đã có test cơ sở, **When** chạy lệnh test, **Then** có ít nhất một test thực sự được chạy và có kết quả pass/fail.
2. **Given** một test bị làm sai có chủ đích trong bản kiểm tra tạm, **When** chạy test, **Then** quy trình báo fail, không trả về pass giả.
3. **Given** một quyết định kiến trúc quan trọng còn mở hoặc AC chưa đạt, **When** đánh giá readiness, **Then** ghi rõ tồn đọng và không đánh dấu giai đoạn là Verified.

### Edge Cases

- Checkout sạch chưa có source/build file: trạng thái tiếp tục là Draft/Not Started cho tới khi triển khai và kiểm chứng.
- Thiếu JDK hoặc công cụ cần thiết: tài liệu phải chỉ cách kiểm tra điều kiện tiên quyết.
- Môi trường không có màn hình đồ họa: tài liệu phải chỉ rõ hỗ trợ/giới hạn, không ngầm cam kết chạy GUI ở nơi không phù hợp.
- Không có Internet, không có peer hoặc không có kết nối mạng: ứng dụng khung vẫn phải có khả năng khởi động và thoát.
- Mở rồi đóng liên tiếp: không được có tiến trình/worker bị bỏ lại.
- Display name trùng hoặc thay đổi; timestamp không hợp lệ; message type lạ hoặc version không tương thích: protocol draft phải giải thích hành vi mong đợi.
- Hai peer mất kết nối khi đang trao đổi; cùng sự kiện được nhận nhiều lần; máy có đồng hồ lệch; peer mới tham gia nhưng không có quyền đọc lịch sử: bản thiết kế đồng bộ phải có nguyên tắc dedup, kiểm soát phạm vi chia sẻ, khôi phục và sắp xếp hiển thị, chưa hiện thực ở giai đoạn 01.
- Văn bản tiếng Việt/emoji và metadata Unicode: không giả định chỉ có ký tự ASCII.
- Lệnh test không tìm thấy test hoặc test lỗi: báo thất bại rõ, không ghi “đã kiểm thử thành công”.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001:** Dự án **MUST** công bố điều kiện tiên quyết về môi trường, phiên bản Java và các công cụ cần thiết để build/run/test.
- **FR-002:** Dự án **MUST** cung cấp hướng dẫn build, run và test có thể tái lập từ checkout sạch mà không phụ thuộc cấu hình IDE riêng.
- **FR-003:** Dự án **MUST** có điểm khởi chạy rõ ràng và mở được một khung IntraChat tối thiểu.
- **FR-004:** Khung ứng dụng **MUST** có tên/nhận diện IntraChat và trạng thái chưa kết nối; **MUST NOT** thể hiện chat/peer đã online khi chưa triển khai.
- **FR-005:** Ứng dụng khung **MUST** đóng bình thường và không cần Internet hoặc peer ngoài để khởi động/thoát.
- **FR-006:** Tài liệu kiến trúc **MUST** xác định trách nhiệm và ranh giới giữa UI, domain/service, protocol, network và persistence; phần chưa triển khai phải được ghi rõ.
- **FR-007:** Tài liệu kiến trúc **MUST** nêu yêu cầu không chặn luồng giao diện bằng tác vụ mạng, truyền tệp hoặc lưu dữ liệu.
- **FR-008:** Mô hình khái niệm **MUST** bao quát Peer, Conversation, Message, Group, Attachment, Transfer, Connection State và mối quan hệ cốt lõi.
- **FR-009:** Protocol draft **MUST** nêu version, type, message ID, sender ID, đích peer/group, timestamp, payload/metadata.
- **FR-010:** Mô hình **MUST** phân biệt sender ID với tên hiển thị, peer/connection state với trạng thái sent/delivered/read.
- **FR-011:** Protocol draft **MUST** quy định ý nghĩa thời gian và hiển thị múi giờ; không giả định clock của mọi peer đồng bộ hoàn toàn.
- **FR-012:** Protocol draft **MUST** đề cập phân khung, giới hạn kích thước, loại/phiên bản không hỗ trợ, dữ liệu sai và chính sách phản hồi lỗi; cơ chế cụ thể chốt ở plan.
- **FR-013:** Tài liệu thiết kế **MUST** lập ma trận ánh xạ đầy đủ **11/11 chức năng** vào trách nhiệm hệ thống và giai đoạn tương ứng.
- **FR-014:** Dự án **MUST** có tối thiểu một test tự động có thể chạy thật, báo đúng pass/fail và không yêu cầu Internet hoặc peer bên ngoài.
- **FR-015:** README **MUST** phản ánh trạng thái triển khai thực tế, hướng dẫn đã kiểm chứng và giới hạn bản nền tảng.
- **FR-016:** Các quyết định công nghệ chưa được chọn **MUST** chuyển sang `plan.md` với lý do và trade-off, không coi lựa chọn gợi ý là đã phê duyệt.
- **FR-017:** Tài liệu/kiểm thử **MUST NOT** cần secrets, cloud account hay dữ liệu chat thật để chạy nền tảng.
- **FR-018:** Bàn giao M0 **MUST** có kết quả xác minh build/run/test, các điều kiện AC đạt/chưa đạt, Constitution Check và tồn đọng cần giải quyết trước giai đoạn 02.
- **FR-019:** Thiết kế tương lai **MUST** dùng P2P trực tiếp trong LAN; không được đặt một server tập trung thành điều kiện bắt buộc cho chức năng Chat hoặc History.
- **FR-020:** Thiết kế của bản đầu tiên **MUST** nhắm tới Windows, ghi rõ phạm vi môi trường được thử nghiệm.
- **FR-021:** Trong đặc tả Video/Sound, hệ thống **MUST** hướng đến gửi/nhận/phát tệp media; **MUST NOT** ngầm coi hai chức năng này là gọi thời gian thực.
- **FR-022:** Mô hình dữ liệu và protocol draft **MUST** dự phòng nhu cầu đồng bộ History giữa các máy được phép trong LAN, gồm định danh sự kiện ổn định, ngữ cảnh hội thoại, phát hiện thiếu/trùng, giới hạn chia sẻ và điều kiện đồng bộ sau offline; chỉ làm thiết kế, không triển khai sync giai đoạn 01.
- **FR-023:** Tài liệu **MUST** nêu giới hạn đồng bộ không tức thì khi peer offline, sự khác nhau giữa các thiết bị với danh tính người dùng, và các quyết định bảo mật/quyền truy cập/xung đột/xóa cần được chốt trước khi hiện thực History.

### Non-Functional Requirements

- **NFR-001 — Reproducibility:** Người khác có thể thực hiện lại các bước trên môi trường được hỗ trợ.
- **NFR-002 — Maintainability:** Ranh giới thiết kế phải cho phép bổ sung giao thức LAN mà không ép UI trực tiếp xử lý socket.
- **NFR-003 — Responsiveness:** Khởi động/thoát không phụ thuộc peer; ngưỡng thời gian mục tiêu sẽ được xác định trong kế hoạch theo thiết bị kiểm thử.
- **NFR-004 — Security:** Không lưu/commit secrets hoặc hội thoại thật; dữ liệu mạng tương lai phải được coi là không tin cậy; không sử dụng native Java deserialization trên đầu vào mạng không đáng tin.
- **NFR-005 — Unicode:** Dữ liệu text/metadata được thiết kế để hỗ trợ tiếng Việt và emoji Unicode.
- **NFR-006 — Honesty:** Không công bố tính năng hay test là đã hoàn thành nếu thiếu bằng chứng thực thi.
- **NFR-007 — Eventual Consistency:** Thiết kế History đồng bộ nhiều máy có thể tiến tới nhất quán khi các peer được phép kết nối lại; không giả định thứ tự tin nhắn toàn cục dựa trên đồng hồ máy hoặc yêu cầu server liên tục hoạt động.

### Key Entities

> Đây là các **khái niệm cần được thống nhất**, chưa phải Java classes hoặc bảng dữ liệu đã tồn tại.

| Khái niệm | Vai trò / quan hệ |
| --- | --- |
| Peer | Người/thiết bị tham gia; `peerId` ổn định và `displayName` có thể thay đổi |
| Conversation | Ngữ cảnh hội thoại P2P hoặc Group, gồm định danh và participants |
| Message | `messageId`, `type`, `senderId`, destination, timestamp, payload |
| Group | Hội thoại nhiều thành viên; quản lý membership ở giai đoạn 04 |
| Attachment | Thông tin tệp/media gắn với message hoặc transfer |
| Transfer | Vòng đời chuyển tệp; chưa thực thi trong giai đoạn 01 |
| Connection State | Trạng thái kết nối peer, độc lập với trạng thái giao nhận tin |
| Protocol Contract | Mô tả dữ liệu/sự kiện, version, giới hạn và tình huống lỗi |
| History Sync Event (khái niệm) | Định danh sự kiện duy nhất, conversation ID, nguồn/thiết bị, thông tin đối soát; hiện thực chi tiết ở giai đoạn 05 |

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001:** Có ít nhất **01 lần** build, run, test thành công từ checkout sạch theo tài liệu trên môi trường được công bố.
- **SC-002:** **5/5 lần** mở và thoát liên tiếp kết thúc bình thường, không để lại tiến trình ứng dụng.
- **SC-003:** **5/5 lần** chạy khi không có peer vẫn khởi động được và không hiển thị “online” giả.
- **SC-004:** Có tối thiểu **01 test tự động chạy thật**; quy trình báo fail khi cố ý tạo một trường hợp test thất bại tạm thời.
- **SC-005:** Có ánh xạ đầy đủ **11/11 chức năng** P2P, Group, File, Emoji, History, Image, Video, Sound, State, Time, Sender.
- **SC-006:** Review xác nhận **đủ 100% mục protocol draft bắt buộc** theo FR-009–012 (kể cả tình huống lỗi), không nhầm tài liệu với triển khai.
- **SC-007:** README có **đủ build/run/test**, điều kiện tiên quyết, hạn chế và kết quả kiểm chứng.
- **SC-008:** **0 vi phạm chưa xử lý** đối với các nguyên tắc MUST trong Constitution khi đánh dấu M0 là Verified.
- **SC-009:** Tài liệu review đủ **4/4 quyết định CL-01…CL-04** và có bản thiết kế mô tả ít nhất **3 tình huống sync** (peer offline rồi online lại; sự kiện trùng; peer không có quyền yêu cầu lịch sử).

## Assumptions

- Java là ngôn ngữ đã được yêu cầu; **Windows, P2P trực tiếp, media dạng tệp và History đồng bộ nhiều máy** đã chốt. JDK, Swing/JavaFX, build/test framework, cơ chế P2P/discovery và lưu trữ cụ thể do plan lựa chọn.
- Phần mềm hướng tới máy tính trong LAN. Giai đoạn 01 chỉ cần khung ứng dụng chạy được, không phải chat hoàn chỉnh.
- Không yêu cầu Internet cho hành vi cơ sở; xác thực/mã hóa LAN chưa được mặc định là đã triển khai.
- Video và Sound chỉ là gửi/nhận/phát media, **không** bao gồm gọi thời gian thực (CL-03).
- Mỗi máy tham gia lưu dữ liệu cục bộ của hội thoại được phép đồng bộ; các peer khác chỉ có thể nhận dữ liệu khi có kết nối và đủ quyền. Định nghĩa đồng nhất người dùng trên nhiều thiết bị sẽ đặc tả riêng, không mặc nhiên một `peerId` dùng chung mọi máy.
- Thử nghiệm chỉ dùng dữ liệu giả lập, không chứa thông tin cá nhân thật.

## Dependencies & Planning Handoff

- Nguồn chuẩn: [ROADMAP](../../ROADMAP.md), [Constitution](../../.specify/memory/constitution.md), [AGENTS](../../AGENTS.md), [README](../../README.md).
- Kết quả giai đoạn 01 là đầu vào cho `002-lan-networking`; không yêu cầu giao tiếp P2P thật để nghiệm thu giai đoạn này.
- Tại `plan.md`, phải chốt **JDK/Windows**, **UI toolkit**, **build/test**, hướng phân tách các thành phần, **P2P trực tiếp/discovery/transport** định hướng giai đoạn 02, cách biểu diễn/framing/versioning giao thức, phương án **History sync P2P** (thiết kế, không code), giới hạn tài nguyên và tiêu chí test; kèm **Constitution Check I–VII**.
- Không cài hoặc giả định Spec Kit CLI đã hoạt động chỉ vì có tài liệu `spec.md`.

## Acceptance Traceability — Điều kiện kết thúc M0

| Mã | Tiêu chí nghiệm thu | Truy vết |
| --- | --- | --- |
| AC-01-A | Checkout sạch build/run thành công theo README | US1, FR-001–003, SC-001 |
| AC-01-B | Test mẫu chạy được, phản ánh pass và fail đúng | US4, FR-014, SC-004 |
| AC-01-C | Ranh giới trách nhiệm rõ, UI không trực tiếp làm socket I/O | US3, FR-006–007 |
| AC-01-D | App mở/đóng ổn và không hiển thị online giả | US2, FR-004–005, SC-002–003 |
| AC-01-E | Có entity model và contract sơ bộ đủ trường/loại lỗi | US3, FR-008–012, SC-006 |
| AC-01-F | 11/11 chức năng được ánh xạ trách nhiệm/giai đoạn | US3, FR-013, SC-005 |
| AC-01-G | README có môi trường, build/run/test, giới hạn đã kiểm chứng | US1, FR-015–017, SC-007 |
| AC-01-H | Có báo cáo kiểm chứng, Constitution Check và danh sách tồn đọng | US4, FR-018, SC-008 |
| AC-01-I | Bản thiết kế ghi đủ 4 lựa chọn Clarify và mô tả kiến trúc sync History nhiều máy không cần server trung tâm | US3, FR-019–023, SC-009 |

**Definition of Ready for giai đoạn 02:** AC-01-A…I đều đạt và có bằng chứng; các quyết định kiến trúc/giao thức ảnh hưởng LAN được ghi lại; không có vi phạm bắt buộc của Constitution chưa được xử lý.

**Current implementation status:** Đã có Java/Swing foundation, Maven Wrapper, metadata models và unit tests. Build, diagnostics, 9/9 JUnit, isolated-source offline build và 5 GUI lifecycle cycles đã được kiểm tra trên Windows 11 + JDK 25 (target Java 21). **Chưa** có runtime JDK 21 hoặc physical-offline GUI verification nên M0 chưa `Verified`. Xem [acceptance report](evidence/m0-acceptance-report.md).
