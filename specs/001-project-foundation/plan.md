# Implementation Plan: IntraChat — 001 Project Foundation

**Branch:** `001-project-foundation` *(tên đề xuất; hiện không tự đổi nhánh Git)*
**Date:** 2026-11-21
**Spec:** [spec.md](spec.md)
**Status:** Implementation in progress — Swing foundation built/tested; full M0 gate still open
**Scope:** Giai đoạn 01, mốc M0 — Foundation Ready
**Authority:** [.specify/memory/constitution.md](../../.specify/memory/constitution.md)

## Summary

Thiết lập nền tảng **ứng dụng desktop Java cho Windows**, không cần dịch vụ Internet để chạy; khung giao diện IntraChat có thể mở/đóng, build/test tái lập và có ranh giới UI–Service–Domain–Protocol–Transport–Persistence. Hoàn thành tài liệu kiến trúc, mô hình dữ liệu, protocol envelope bản đầu và ma trận 11 chức năng. Chưa triển khai socket hay History sync thực tế trong giai đoạn này.

**Bốn quyết định đã xác nhận trong spec:** (CL-01) kết nối **P2P trực tiếp**, không server tập trung; (CL-02) **Windows**; (CL-03) Video/Sound là **chia sẻ và phát file**, không phải cuộc gọi; (CL-04) **History đồng bộ nhiều máy** qua LAN giữa các peer có quyền, có khả năng đối soát khi kết nối lại. Về sau mỗi máy giữ bản sao cục bộ; tính nhất quán là **eventual**, không có cam kết đồng bộ tức thời khi offline.

## Technical Context

| Hạng mục | Lựa chọn trong kế hoạch | Lý do / giới hạn |
| --- | --- | --- |
| Language/Version | **Java 21 (LTS), Eclipse Temurin JDK 21**, UTF-8 | LTS ổn định, API tiêu chuẩn đầy đủ, tránh phụ thuộc Oracle JDK license; khóa Java release 21 |
| Target Platform | **Windows 11 x64**; Windows 10 chỉ hỗ trợ nếu kiểm chứng riêng | Đúng CL-02; Windows 11 là tiêu chí nghiệm thu chính; không mặc định xác nhận hỗ trợ Windows 10 |
| Project Type | **Desktop app, một Maven module** | Dự án học tập/đồ án gọn, dễ build; tách package thay vì multi-module sớm |
| Desktop UI | **Swing** (JDK tích hợp), UI thread qua EDT | Ít dependency hơn JavaFX, dễ tạo shell và đóng gọn; JavaFX có lợi hơn với media nhưng tăng dependency, media chưa làm ở M0 |
| Build | **Apache Maven 3.9.16** + Maven Wrapper cấu hình phiên bản | `pom.xml` thống nhất; `mvnw.cmd` giảm khác biệt môi trường; không yêu cầu IDE riêng |
| Testing | **JUnit Jupiter 5.14.4** (đề xuất pin trong `pom.xml`) + Maven Surefire | Unit test đơn giản, chạy được trên Java 21; phiên bản plugin kiểm tra tương thích trước khi cố định |
| Logging | `java.util.logging` cho nền tảng | Giảm thư viện, không log dữ liệu chat/bí mật |
| Main Dependencies | **Không có thư viện runtime bên ngoài** ở M0 | Chỉ Swing/JDK; có dependency JUnit phục vụ test |
| History Storage | **Chưa triển khai** tại M0; định hướng **SQLite cục bộ trên mỗi thiết bị** ở giai đoạn 05 | Phù hợp offline-first, có khóa duy nhất và truy vấn; lúc lập spec 005 sẽ chốt schema, migration, backup, retention, quyền truy cập |
| LAN Topology | **P2P trực tiếp**. Gợi ý: **TCP** hai chiều cho tin nhắn; máy nào cũng có thể là listener/client | TCP có thứ tự byte stream, dễ framing và kiểm soát lỗi; không cần server trung tâm; hiện thực ở giai đoạn 02 |
| Peer Discovery | Ở giai đoạn 02: **nhập IP/port thủ công** làm baseline; UDP LAN discovery tùy chọn, không phụ thuộc duy nhất broadcast | Windows Firewall hoặc router có thể chặn discovery; nhập IP vẫn sử dụng được |
| Protocol | **Length-prefixed framed messages** với nội dung **JSON UTF-8 có version**; binary stream/chunk độc lập về sau | Đơn giản để debug, không dùng Java native serialization; chính thức hóa ở giai đoạn 02 |
| History Sync | Giai đoạn 05: trao đổi **summary/delta** trực tiếp qua các peer được phép, lưu local, dedup theo event ID | Không có server trung tâm, hỗ trợ peer offline; cần cơ chế xác thực/ủy quyền trước khi bật |
| Media | Giai đoạn 06: stream file; giai đoạn 07: preview/open/play file Image/Video/Sound | Không gọi thời gian thực; ưu tiên dùng player hệ điều hành khi định dạng không được Swing hỗ trợ |
| Performance Goals | Đề xuất đo trên máy Windows 11, SSD, RAM ≥8 GB: khởi động shell ≤3s và thoát ≤2s (mỗi phép đo 5 lần) | Ngân sách thiết kế để đo, không tuyên bố kết quả cho đến khi kiểm chứng |
| Constraints | Không Internet runtime; không socket I/O trên EDT; không secrets trong Git; không public Internet endpoint | Ràng buộc Constitution I–VII |
| Scale/Scope | Một desktop app; 11 chức năng định hướng, **M0 chỉ foundation** | Không đưa Group, File hoặc sync thật vào code của M0 |

**Tình trạng nguồn (sau triển khai):** đã có `pom.xml`, Maven Wrapper, Swing entrypoint, typed domain/protocol metadata và 9 JUnit tests. Build/package/tests/diagnostics chạy được trên **Windows 11 + Oracle JDK 25.0.4**, bytecode target 21; **chưa xác minh JDK 21 runtime** và chưa có chức năng LAN/History/File/Media. [Báo cáo M0](evidence/m0-acceptance-report.md).

**Phân biệt Internet:** ứng dụng chạy và giao tiếp LAN không phụ thuộc Internet. Lần đầu Maven tải dependency/plugin có thể cần Internet hoặc Maven repository nội bộ; sau khi cache sẵn có thể kiểm thử offline bằng `mvnw.cmd -o test`. Không dùng điều này để tuyên bố checkout sạch hoàn toàn offline có thể build nếu chưa có cache.

## Constitution Check (Gate 0 — Trước triển khai)

| Nguyên tắc | Đánh giá thiết kế | Bằng chứng dự kiến / chốt chặn |
| --- | --- | --- |
| **I. LAN-First & Explicit Topology** | **PASS — design** | CL-01 chốt P2P trực tiếp; TCP dự kiến, manual IP fallback, không cần cloud; socket/discovery để 002 |
| **II. Versioned Message & Identity Contract** | **PASS — design** | Phần Protocol nêu version, type, ID, peer/group destination, timestamp, schema, lỗi; `deviceId` và `displayName` khác nhau |
| **III. Non-Blocking UI & Bounded Resources** | **PASS — design** | EDT chỉ cập nhật UI; I/O sau này qua worker/executor có timeout và cleanup; max frame size; chưa truyền file |
| **IV. Security & Privacy** | **PASS có điều kiện trước khi có sync** | Không dùng object deserialization; chưa bật sync; trước giai đoạn 05 phải có peer authentication + conversation authorization, dữ liệu nhận không tin cậy, không log content |
| **V. Automated Tests** | **PASS — test strategy** | JUnit baseline, negative test, thử mở/đóng, không cần peer; phải có log chạy thật trước khi nghiệm thu M0 |
| **VI. User-Visible Truth** | **PASS — design** | Shell chỉ hiển thị “Chưa kết nối / Chức năng chat chưa sẵn sàng”, không hiển thị delivered/read giả; Unicode |
| **VII. Spec-Driven & Honest Delivery** | **PASS — process** | Spec có FR/AC, plan có trade-off, tasks làm bước kế tiếp; mọi chức năng chưa triển khai được đánh dấu planned |

**Gate 0:** Không phát hiện vi phạm `MUST` ngay trong **thiết kế**. **Gate 1 (sau code)** vẫn **chưa kiểm chứng**; trạng thái PASS ở bảng này không đồng nghĩa với đã nghiệm thu chương trình. Nếu chưa chứng minh bất kỳ điều kiện MUST nào bằng code/test và tài liệu, M0 không được đánh dấu Verified.

**Bảo mật sync:** “LAN” không đồng nghĩa “đáng tin”. Phương án trao đổi History chỉ được bật khi có cơ chế nhận diện và xác thực thiết bị, kiểm tra quyền tham gia cuộc hội thoại và chính sách xóa/thu hồi quyền. Chưa cam kết mã hóa đầu-cuối hay mã hóa khi truyền cho đến khi đặc tả/kiểm thử xác nhận.

## Project Structure

### Documentation (giai đoạn 01)

```text
specs/001-project-foundation/
├── spec.md                       # đã có, gồm CL-01..CL-04
├── checklists/requirements.md    # đã có, checklist chất lượng đặc tả
├── plan.md                       # file kế hoạch này
├── research.md                   # đề xuất: bảng ADR/trade-off, tạo ở bước thiết kế
├── data-model.md                 # đề xuất: mô hình khái niệm chi tiết
├── contracts/
│   └── protocol-v1-draft.md      # đề xuất: hợp đồng thông điệp chưa có wire implementation
├── quickstart.md                 # đề xuất: hướng dẫn kiểm chứng M0
└── tasks.md                      # đã tạo T001–T041; chưa task nào được thực thi
```

### Source Code — dự kiến sau khi triển khai M0

```text
IntraChat/
├── pom.xml
├── mvnw.cmd
├── .mvn/wrapper/...
├── src/
│   ├── main/
│   │   ├── java/vn/intrachat/
│   │   │   ├── IntraChatApplication.java
│   │   │   ├── ui/               # application shell, Swing components
│   │   │   ├── application/      # use cases, interaction contracts
│   │   │   ├── domain/           # immutable data, identifiers, validation
│   │   │   ├── protocol/         # schema/contracts; network parser later
│   │   │   ├── transport/        # empty until giai đoạn 02
│   │   │   └── persistence/      # empty until giai đoạn 05
│   │   └── resources/
│   └── test/java/vn/intrachat/
│       ├── domain/
│       └── application/
├── specs/001-project-foundation/
├── README.md
├── ROADMAP.md
├── AGENTS.md
└── .specify/memory/constitution.md
```

**Structure Decision:** single-module Maven, chuẩn `src/main/java` và `src/test/java`; package `vn.intrachat` chỉ là namespace đề xuất cho mã nội bộ, không khẳng định dự án đã có package. Không tạo package rỗng hoặc module phức tạp khi chưa cần. Trong M0 chỉ cần entry point, UI shell, một số value object có test và tài liệu contract; `transport`/`persistence` có thể là **mô tả thiết kế, chưa tạo source**.

## Architecture & Dependency Direction

```text
Swing UI (EDT)  ──commands/events──>  Application services
                                         │
                                         v
                                  Domain / Models
                                         ^
                                         │
         Protocol contracts ------> Application ports/interfaces
                 ^                       ^
                 |                       |
        Transport adapters          Persistence adapters
         (giai đoạn 02)              (giai đoạn 05)
```

- UI **chỉ** nhận input, phát command và render state; không xử lý socket trong handler.
- Application/service điều phối use case, phụ thuộc vào interface/port thay vì implementation kết nối/lưu trữ.
- Domain lưu giá trị, identity và các quy tắc hợp lệ; **không import Swing, socket hoặc JDBC**.
- Protocol mô tả message envelope và versioning; chuyển đổi giữa bytes và domain phải có validation.
- Transport dùng TCP/UDP/discovery theo trách nhiệm, asynchronous, có timeout; được thêm ở giai đoạn 02.
- Persistence lưu History cục bộ theo conversation/device; sync adapter ở giai đoạn 05, không nhúng SQL vào UI.
- Để tuân thủ EDT: callback từ worker chuyển về `SwingUtilities.invokeLater` hoặc cơ chế phù hợp; close lifecycle yêu cầu hủy worker và đóng đúng tài nguyên.

### Khả năng mở rộng 11 chức năng

| Feature | Trách nhiệm chính | Giai đoạn |
| --- | --- | --- |
| P2P | Transport + Application + Protocol + UI | 02–03 |
| Group | Group service + protocol + participant model | 04 |
| File | Transfer service + stream transport + UI | 06 |
| Emoji | Unicode rendering + message model | 03 |
| History | Local repository + sync service + protocol | 05 |
| Image | Attachment + preview + file transfer | 07 |
| Video | Attachment + file transfer + player/open fallback | 07 |
| Sound | Attachment + file transfer + player/open fallback | 07 |
| State | Connection service / delivery-state model độc lập | 02–03 |
| Time | Protocol timestamp UTC + UI localization | 03 |
| Sender | Peer identity + message envelope + UI attribution | 03 |

## Technical Decisions & Trade-offs (ADR-lite)

| ID | Quyết định | Lựa chọn thay thế | Vì sao chọn / trade-off |
| --- | --- | --- | --- |
| D-01 | Java **21 LTS Temurin** | Java 25 LTS, Java 17 | Cân bằng tương thích và tính hiện đại, runtime LTS; không mặc định dùng tính năng mới Java 25 |
| D-02 | **Swing** | JavaFX | Không cần external UI dependencies để làm M0; UI hiện đại/media player cần bổ sung khi 007 |
| D-03 | **Maven 3.9.16 + Wrapper** | Gradle | Cấu hình đơn giản cho project một module, quen thuộc với Java; Gradle linh hoạt hơn cho project lớn |
| D-04 | **JUnit Jupiter 5.14.4** | TestNG / JUnit 6 | Dễ tổ chức unit test cho Java 21, quy trình Maven phổ biến; xác minh Maven Surefire tương thích khi tạo pom |
| D-05 | **TCP + JSON framed** (triển khai ở 002) | UDP thuần / Java serialization / Protobuf | TCP giúp reliable byte stream; JSON dễ quan sát; kích thước overhead cao, phải giới hạn và dùng binary chunk cho tệp |
| D-06 | **Manual IP trước**, UDP discovery phụ | Phụ thuộc broadcast/multicast duy nhất | Không bị khóa bởi cấu hình Windows Firewall/router; discovery là tiện ích, không phải yêu cầu sống còn |
| D-07 | **SQLite mỗi peer** ở giai đoạn 05 | Máy chủ SQL tập trung / flat files | Hỗ trợ offline, unique index và đối soát; phải xử lý migration, consistency và privacy |
| D-08 | **History sync theo event/delta** khi được phép | Central sync server / sao chép cả DB | Phù hợp CL-01+CL-04, tránh server; phức tạp hơn ở authorization, conflict và offline reconciliation |
| D-09 | **Media qua file transfer** | RTP/WebRTC streaming | Đúng CL-03, phạm vi kiểm thử phù hợp; không đáp ứng nhu cầu call thời gian thực (không yêu cầu) |

**Ràng buộc phiên bản:** khóa bản JDK major, wrapper Maven và plugin/dependencies trong `pom.xml`; chỉ cập nhật khi có kiểm thử regression. Đường dẫn tham khảo: [GitHub Spec Kit plan template](https://github.com/github/spec-kit/blob/main/templates/plan-template.md), [Apache Maven releases](https://maven.apache.org/docs/history.html), [Java SE support roadmap](https://www.oracle.com/java/technologies/java-se-support-roadmap.html).

## Protocol Draft v1 — thiết kế, chưa hiện thực

### 1. Message envelope

```json
{
  "protocolVersion": 1,
  "messageId": "uuid-v4",
  "type": "CHAT_TEXT",
  "sender": { "deviceId": "stable-device-id", "displayName": "Máy A" },
  "destination": { "kind": "PEER", "id": "peer-device-id" },
  "sentAt": "2026-11-21T06:53:00Z",
  "payload": { "text": "Xin chào!" }
}
```

Đây là **ví dụ mô tả hợp đồng**, không phải message đã được ứng dụng gửi. `deviceId` là định danh **thiết bị/peer** ổn định, khác với `userId` (nếu sau này có mô hình nhiều thiết bị của cùng một người), và khác `displayName` có thể đổi. `senderId` trong spec tương đương `sender.deviceId` của bản envelope này, trừ khi schema cuối cùng tách cả user/device identity trong 002/005.

- **Required fields:** `protocolVersion`, `messageId`, `type`, `sender.deviceId`, `destination.kind/id`, `sentAt`, `payload`.
- **IDs:** UUID ngẫu nhiên cho message/event; không dùng IP hoặc tên hiển thị làm định danh vĩnh viễn.
- **Destination:** `PEER` hoặc `GROUP`; group ID không đồng nghĩa routing/ủy quyền đã có.
- **Clock:** timestamp UTC ISO-8601; hiển thị theo timezone địa phương. Không dùng giờ máy làm chứng cứ thứ tự nhân quả.
- **Text:** UTF-8, bảo toàn tiếng Việt và emoji Unicode. Không đặt binary lớn trực tiếp trong JSON.
- **Error handling:** thiếu trường bắt buộc, type lạ, version không hỗ trợ, JSON lỗi hoặc kích thước quá giới hạn phải bị từ chối có kiểm soát; không thực thi Java native deserialization.
- **Compatibility:** version major là bắt buộc, unknown fields được bỏ qua **chỉ nếu** không làm sai ý nghĩa an toàn; incompatible version phải từ chối/đàm phán, không parse mù.
- **Message classes tương lai:** `HELLO`, `CHAT_TEXT`, `GROUP_MESSAGE`, `ACK`, `SYNC_SUMMARY`, `SYNC_REQUEST`, `SYNC_BATCH`, `FILE_OFFER`, `FILE_CHUNK`, `FILE_STATUS`. Đây là **danh sách minh họa để review**, chưa phải enum cố định.

### 2. Framing & tài nguyên (định hướng cho giai đoạn 02)

- TCP truyền **byte stream**, không đảm bảo ranh giới từng lời gọi gửi; mỗi frame đề xuất **4 byte big-endian length** + nội dung frame.
- Giới hạn đề xuất cho **control message**: tối đa **1 MiB/frame** (1,048,576 byte). Kiểm tra độ dài trước khi cấp phát; độ dài âm, bằng 0 (nếu không hỗ trợ), quá hạn hoặc thiếu bytes phải tạo lỗi rõ.
- File/media về sau dùng luồng/chunk độc lập (gợi ý **256 KiB/chunk**, cần đo và chốt ở 006); không nhét toàn bộ video vào payload JSON hoặc RAM.
- Kết nối có timeout, cancellation, bounded queue và đóng socket/stream/worker đúng vòng đời. Tham số timeout cụ thể phải được benchmark/chốt ở 002, không tuyên bố đã được triển khai.
- Windows Firewall: listener chỉ lắng nghe trên LAN/private network và cổng cấu hình được; không tự động mở inbound public profile. Baseline nhập IP/port thủ công, discovery sau.
- **Safety rule:** mọi dữ liệu mạng là untrusted. Validate ở boundary, kiểm soát version/schema/size; không log nội dung/payload nguyên văn.

### 3. Trạng thái và thứ tự

- **ConnectionState**: `DISCONNECTED`, `CONNECTING`, `CONNECTED`, `FAILED` (tên chính thức có thể điều chỉnh).
- **MessageDeliveryState** riêng biệt: `QUEUED`, `SENT`, `DELIVERED`, `READ`, `FAILED`; chỉ hiển thị nếu từng trạng thái có sự kiện xác nhận tương ứng.
- TCP đảm bảo thứ tự bytes trên **một connection**, không đảm bảo thứ tự toàn cục khi gửi qua nhiều peer; UI phải có quy tắc sắp xếp xác định.
- ACK, retry, dedup phải ghi thành contract ở giai đoạn 02–03. Không mặc nhiên “exactly once” vì mất kết nối giữa ACK và nhận.

## History Sync Architecture — CL-04 (phác thảo để triển khai ở giai đoạn 05)

**Quyết định đã chốt:** không có máy chủ trung tâm; mỗi peer tham gia và được phép lưu hội thoại có một **local history store**. Đồng bộ chỉ xảy ra qua kết nối P2P giữa các peer/thiết bị **được phép** khi chúng cùng online. Mục tiêu eventual consistency trong phạm vi dữ liệu được chia sẻ; **không** đồng bộ toàn bộ dữ liệu riêng của một máy tới mọi máy trong LAN.

### Danh tính và quyền chia sẻ

- Mô hình hóa riêng **deviceId**, **userId** (nếu có nhiều thiết bị/cùng người dùng), **conversationId**, **groupId** và **membership**. Không xem cùng display name là cùng người.
- Một peer chỉ yêu cầu/nhận event của những conversation nó **được phép tham gia và truy cập lịch sử** theo chính sách được đặc tả.
- Khi thiết kế 002/005, phải chọn phương án nhận diện/xác thực thiết bị (ví dụ manual pairing + key fingerprint/trust) và chính sách cấp quyền/thu hồi quyền trước khi bật sync. ID/địa chỉ IP tự khai không chứng minh được quyền.
- **Không có authentication/authorization đã triển khai ở M0**. Việc tự động gửi lịch sử chỉ dựa vào IP, tên hoặc `conversationId` là không được phép.

### Mô hình dữ liệu dự kiến

```text
Device(deviceId, publicIdentity?, displayName)
Conversation(conversationId, kind, membershipPolicy)
HistoryEvent(eventId, conversationId, originDeviceId, sequence,
             eventType, messageId?, timestampUtc, payloadDigest, payload?)
SyncCursor(conversationId, remoteDeviceId, knownSequences/checkpoint)
```

- **Event là immutable** khi ghi; mỗi sự kiện có `eventId` duy nhất, `originDeviceId` và sequence tăng dần trong phạm vi nguồn (để đối soát theo nguồn).
- Khác biệt giữa `messageId` (đơn vị nhắn tin) và `eventId` (thao tác phát sinh với tin) tránh dùng lại một ID cho mọi lần sửa/xóa.
- Một `Conversation` có thể có event từ nhiều nguồn; cursor/summary theo nguồn giúp phát hiện thiếu. Dùng unique constraint để ngăn cùng event được ghi hai lần.
- Nếu có **edit/delete** ở phiên bản sau, mô hình event phải có mutation/tombstone và conflict policy rõ ràng; **không** xử lý bằng “last timestamp wins” khi chưa có quy tắc xác định.
- File/media bytes **không** tự động đồng bộ như History metadata; attachment policy dựa theo giai đoạn 06–07.

### Luồng đồng bộ dự kiến

1. Hai peer được xác thực kết nối trực tiếp và xác định tập hội thoại mà cả hai được phép đối soát.
2. Mỗi phía gửi `SYNC_SUMMARY` theo conversation (ví dụ highest contiguous sequence hoặc version vector theo nguồn).
3. So sánh summary, gửi `SYNC_REQUEST` cho các dải sự kiện còn thiếu và `SYNC_BATCH` theo từng lô có giới hạn.
4. Phía nhận kiểm tra quyền, schema, giới hạn, event ID và tính toàn vẹn; lưu theo kiểu idempotent, cập nhật cursor **sau khi ghi bền vững**.
5. Hai bên có thể lặp lại quá trình cho đến khi không còn sự kiện cần đổi hoặc một phía offline. Lần kết nối sau tiếp tục từ checkpoint.

**Tình huống phải kiểm tra trong thiết kế:**

- **S1 — Peer offline:** A tạo tin khi B mất mạng; khi B trở lại và được phép, B yêu cầu missing events và dần đồng bộ.
- **S2 — Dữ liệu trùng:** A nhận cùng event hai lần sau retry; local store chỉ có một bản, cursor không bị tăng sai.
- **S3 — Peer không có quyền:** C đoán conversation ID và xin lịch sử; hệ thống từ chối chia sẻ nội dung kể cả khi C ở cùng LAN.
- **S4 — Đồng thời:** A và B đều tạo event khi offline; sau reconnect hai bản sao hội tụ về cùng tập event theo ID/version, không dựa vào timestamp máy để kết luận nhân quả.
- **S5 — Xóa/sửa:** tombstone/retention, sự kiện xóa và phạm vi quyền phải có đặc tả trước khi có sync thực tế.

**Giới hạn đã nhận thức:** Không có nguồn nào online thì không tải được sự kiện từ xa; máy mất toàn bộ bản sao có thể không phục hồi được; trạng thái “đã đồng bộ” có nghĩa đã đối soát với các peer kết nối/được phép, **không** chứng minh mọi peer đã có dữ liệu. Các máy cùng một người chưa mặc nhiên chia sẻ identity hay được cấp quyền trùng nhau.

## Implementation Stages (Phase 0–2 của kế hoạch)

### Phase 0 — Research / chốt quyết định (chưa coding)

**Đầu ra đề xuất:** `research.md` (khi tạo theo Spec Kit), ADR/tổng kết D-01…D-09.

- Xác nhận Windows mục tiêu, khả năng cài Temurin 21, Maven Wrapper và JUnit.
- So sánh Swing và JavaFX theo ràng buộc M0 và tác động media giai đoạn 07.
- So sánh TCP vs UDP đối với nhắn tin; định nghĩa địa chỉ và firewall trên Windows.
- Xác định schema envelope, framing, giới hạn, xử lý unknown fields/type/version.
- Làm rõ mô hình danh tính device/user, quyền History trên từng conversation, delta sync và failure modes.
- Rà soát Constitution Check Gate 0; không thêm dependency không có lý do.

**Gate Phase 0:** quyết định kỹ thuật có trade-off; không còn lựa chọn chặn việc tạo skeleton ở giai đoạn 01.

### Phase 1 — Design & Contracts

**Đầu ra đề xuất:** `data-model.md`, `contracts/protocol-v1-draft.md`, `quickstart.md` (nếu dùng workflow đầy đủ).

- Vẽ sơ đồ 6 tầng, dependency direction, lifecycle startup/shutdown, UI thread handling.
- Mô tả entities: Peer, Conversation, Message, Group, Attachment, Transfer, ConnectionState, HistoryEvent/SyncCursor (concept).
- Định nghĩa protocol v1 draft bằng examples hợp lệ/sai và bảng lỗi; không triển khai TCP ở đây.
- Lập ma trận 11 tính năng ↔ thành phần ↔ giai đoạn ↔ rủi ro.
- Chuẩn bị kịch bản kiểm chứng M0: checkout sạch, build/run/test, 5 chu trình mở/thoát và test fail có chủ đích.
- Review Constitution Check **lần hai** sau thiết kế, giải quyết mọi vi phạm MUST.

**Gate Phase 1:** bản design đủ để tạo tasks mà không bị yêu cầu viết feature ngoài phạm vi.

### Phase 2 — Skeleton + Validation (thực thi sau khi có `tasks.md`)

**Thứ tự khuyến nghị (dành cho công đoạn tasks, chưa phải tasks đã tạo):**

1. Cấu hình `pom.xml` với `maven.compiler.release=21`, UTF-8, test dependency và plugin versions được pin.
2. Thiết lập Maven Wrapper `mvnw.cmd`, `.mvn/wrapper/`, `.gitignore` phù hợp.
3. Tạo entrypoint `IntraChatApplication` và một cửa sổ Swing tối thiểu hiển thị tên + “Chưa kết nối”; không fake chat.
4. Tạo cấu trúc các package thật sự dùng, một hoặc vài immutable value objects/validator thuần Java.
5. Thêm unit tests (ví dụ: immutable identifiers, validation) và smoke test mở/đóng GUI chạy thủ công có log.
6. Dọn resource on exit, đảm bảo không có worker/socket chạy ngầm; không tự khởi chạy listener.
7. Viết README với command đã **chạy thực tế**, OS/JDK/Maven version và giới hạn tính năng.
8. Ghi báo cáo test/acceptance checklist M0, review tuân thủ Constitution; chỉ cập nhật trạng thái Verified sau xác nhận.

**Chặn phạm vi:** Ở M0 không tạo socket, listener, UDP discovery, database SQLite thật hoặc SYNC messages chạy thực. Không thêm demo gửi file/nhắn tin để “cho đủ tính năng”.

## Verification Strategy & Quality Gates

| Test ID | Nội dung | Phương pháp | Liên quan |
| --- | --- | --- | --- |
| V-01 | Build từ checkout sạch | `mvnw.cmd clean package`, kiểm tra exit code / artifact | AC-01-A, SC-001 |
| V-02 | Chạy ứng dụng | Lệnh `java -jar` nếu được đóng gói executable; **hoặc** `mvnw.cmd exec:java` nếu cấu hình; chỉ ghi lệnh thực tế đã thử | AC-01-A, AC-01-D |
| V-03 | Không có Internet/peer | Khởi chạy/thoát mà không có dịch vụ mạng; trạng thái chưa kết nối | AC-01-D, SC-003 |
| V-04 | Lifecycle 5 lần | Mở/đóng liên tiếp tối thiểu 5 lượt, kiểm tra process cleanup | AC-01-D, SC-002 |
| V-05 | Unit test baseline | `mvnw.cmd test`, tối thiểu một test chạy thật, có báo cáo pass/fail | AC-01-B, SC-004 |
| V-06 | Negative test | Thay đổi assertion trong nhánh/bản làm việc tạm và xác nhận Maven fail, **hoàn nguyên** | AC-01-B |
| V-07 | Review kiến trúc | Kiểm tra UI không trực tiếp xử lý mạng; contracts không dựa displayName làm identity | AC-01-C, AC-01-E |
| V-08 | Coverage tài liệu | Kiểm tra danh mục 11 tính năng, 4 CL và 3 case sync; review protocol mandatory fields | AC-01-E, F, I |
| V-09 | Documentation | Thành viên khác làm theo README setup/build/run/test và ghi kết quả | AC-01-G |
| V-10 | Constitution gate | Review I–VII, ký nhận exceptions nếu có; không có MUST mở khi M0 Verified | AC-01-H |

**Điều kiện môi trường kiểm thử:** Windows 10/11 x64 + JDK 21 + Maven Wrapper, GUI thật (không headless) để test startup/exit; unit tests có thể chạy không có GUI. Ghi nhận CPU/RAM/Windows/JDK và thời gian khởi động/đóng; ngân sách ≤3s/≤2s là **đề xuất để đo**, không công bố đạt nếu chưa có dữ liệu. Sau khi dependencies đã có trong cache, thử `mvnw.cmd -o test` để kiểm tra khả năng chạy test không dùng mạng.

**Quy tắc báo cáo:** “PASS” chỉ khi có command thật, kết quả và môi trường; “NOT RUN” nếu chưa thực hiện; “FAIL” nếu kiểm thử thất bại. Checklists chất lượng spec không thay thế kiểm thử ứng dụng.

## Traceability: Spec → Plan → Deliverables

| Spec | Thiết kế/đầu ra | Kiểm chứng |
| --- | --- | --- |
| FR-001–005 | JDK, Maven Wrapper, app shell, lifecycle | V-01…V-04 |
| FR-006–008 | Kiến trúc 6 tầng, domain/entity draft | V-07 |
| FR-009–012 | Protocol v1 envelope, framing/error model, sender/time/state | V-07, V-08 |
| FR-013 | Ma trận 11 chức năng | V-08 |
| FR-014–018 | JUnit, README, report và Constitution Gate | V-01, V-05, V-06, V-09, V-10 |
| FR-019–021 / CL-01…03 | P2P trực tiếp, Windows, media file-based | V-08 |
| FR-022–023 / CL-04 | Thiết kế History sync event/delta, quyền truy cập, offline/duplicate/unauthorized | V-08, V-10 |
| SC-001…009 | Các mục tiêu định lượng trước M0 | V-01…V-10 |

## Risks, Dependencies, Deferred Decisions

| Rủi ro | Mức | Biện pháp / thời điểm xử lý |
| --- | --- | --- |
| P2P Windows bị Firewall chặn | High | 002: cấu hình private network, manual IP fallback, test hai máy |
| Nhận diện peer chỉ dựa IP/tên dẫn đến giả mạo | High | 002/005: thiết kế xác thực peer/pairing, không bật History sync khi chưa có authorization |
| Sync History đồng thời/offline/xung đột | High | 005: append-only events + per-origin sequence + idempotent merge; conflict/tombstone policy trước khi implement |
| Phát tán history tới peer không thuộc hội thoại | Critical | 005: kiểm tra ACL/membership trước summary và mỗi batch; secure logging; test unauthorized |
| Đồng hồ mỗi máy khác nhau | Medium | UTC timestamp để hiển thị, event ID + causal info cho đồng bộ, không dùng wall clock làm nguồn thứ tự |
| JavaFX/media dependencies làm M0 phức tạp | Low | Swing shell không có media playback; 007 mới chọn player/fallback |
| Maven lần đầu cần Internet | Medium | Ghi prereq tải dependency, dùng wrapper & local cache/mirror; runtime LAN không cần Internet |
| Vi phạm scope do lập trình Group/Sync sớm | Medium | Gate 01 chỉ skeleton/contract; 002/004/005/006/007 có specs riêng |
| Tên package và plugin versions bị lựa chọn thiếu căn cứ | Low | Pin trong `pom.xml` khi implement, thực chạy trên Windows, ghi kết quả |

**Decisions deferred nhưng phải làm trước feature tương ứng:** Group mesh membership + late join (004); rule chia sẻ lịch sử nhiều máy và user identity/pairing/ACL/tombstone (005); file limits/checksum/path handling (006); media codecs/player formats (007). Không để những quyết định này được ngầm hiểu là đã triển khai ở M0.

## Complexity Tracking

Không có vi phạm Constitution được yêu cầu để biện minh. Một Maven module + Swing + JUnit là lựa chọn đơn giản nhất đáp ứng M0; bảng exception chỉ cần khi review phát hiện vi phạm và được chấp thuận bằng văn bản.

## M0 Exit Criteria (chưa đạt — chờ code/test)

- [ ] AC-01-A: checkout sạch build/run thành công trên Windows được hỗ trợ.
- [x] AC-01-B: unit tests đã chạy 9/9; kiểm chứng cố ý fail trong bản tạm (1/9 fail như mong đợi).
- [x] AC-01-C: kiến trúc phân tầng được mô tả và UI M0 không làm socket I/O.
- [ ] AC-01-D: 5/5 lần mở/thoát, hiển thị đúng “chưa kết nối”.
- [x] AC-01-E: model + protocol draft, error/identity/time/state đã được review mức thiết kế.
- [x] AC-01-F: đủ 11 chức năng đã được ánh xạ trong feature matrix.
- [x] AC-01-G: README có build/run/test đã chạy trên Windows 11 + JDK 25, kèm giới hạn chưa test JDK 21.
- [ ] AC-01-H: Constitution Check sau thiết kế và sau implementation, report kết quả thực.
- [x] AC-01-I: CL-01…04 có mặt; sync P2P có offline/trùng/không quyền ở mức thiết kế.

**Task breakdown:** [tasks.md](tasks.md) đã được tạo với các mục T001–T041 theo thứ tự phụ thuộc, đều ở trạng thái chưa thực hiện. Các file `research.md`, `data-model.md`, `contracts/protocol-v1-draft.md` và `quickstart.md` vẫn là **đầu ra dự kiến của tasks**, chưa được tạo chỉ nhờ việc có plan/tasks. Không tự động gọi Spec Kit CLI hoặc coi các dự định là kết quả test.

## References

- [GitHub Spec Kit Implementation Plan Template](https://github.com/github/spec-kit/blob/main/templates/plan-template.md)
- [GitHub Spec Kit Quickstart](https://github.github.com/spec-kit/quickstart)
- [Java SE Support Roadmap](https://www.oracle.com/java/technologies/java-se-support-roadmap.html)
- [Apache Maven Release History](https://maven.apache.org/docs/history.html)
- [JUnit Jupiter artifact](https://central.sonatype.com/artifact/org.junit.jupiter/junit-jupiter)
