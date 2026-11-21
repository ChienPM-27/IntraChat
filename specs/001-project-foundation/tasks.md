# Tasks: IntraChat — Giai đoạn 01 (Project Foundation)

**Feature:** `001-project-foundation`
**Date:** 2026-11-21
**Status:** In Progress — 38/41 tasks đã thực hiện và có bằng chứng; M0 chưa Verified
**Input:** [spec.md](spec.md) · [plan.md](plan.md) · [Constitution](../../.specify/memory/constitution.md)
**Goal:** Đạt **M0 — Foundation Ready**: dự án Java desktop Windows có thể build/run/test, ứng dụng khung hoạt động, có kiến trúc và hợp đồng thông điệp làm nền cho LAN P2P.

## Quy tắc thực hiện

- Dạng bắt buộc: `- [ ] Tnnn [P?] [USn?] Hành động cụ thể tại đường dẫn`.
- `[P]`: có thể làm song song **khi các điều kiện tiên quyết đã thỏa**, không cùng sửa một tệp.
- `[US1]` đến `[US4]`: liên kết với 4 user stories trong `spec.md`; Setup/Foundation/Polish không cần nhãn story.
- Phân biệt trạng thái **chưa làm** (`[ ]`) với **đã hoàn thành và có bằng chứng** (`[x]`). Không đánh dấu hoàn thành chỉ vì một tệp đã được dự kiến trong `plan.md`.
- Theo quy định test-first của dự án: viết test trước phần hành vi tương ứng; quan sát fail trước khi sửa code cho pass. Nếu chưa thể chạy test, ghi blocker thay vì coi đã đạt.
- Mọi đường dẫn là **relative từ gốc repository** `IntraChat/`. Thư mục `src/`, `docs/` và `scripts/` là **đường dẫn sẽ tạo**, không phải khẳng định đã tồn tại.
- **Không triển khai** socket/discovery, gửi tin thật, Group, lưu/đồng bộ History thật, truyền File hay phát Video/Sound ở giai đoạn này.
- Thỏa thuận làm nền: **P2P trực tiếp**, **Windows**, **media dạng tệp**, **History đồng bộ nhiều thiết bị qua P2P**. Phần sync chỉ thiết kế; việc cho phép chia sẻ lịch sử thực tế đòi hỏi authentication + authorization ở giai đoạn sau.

## Task Index

| Phase | Phạm vi | Task IDs | Điều kiện hoàn thành |
| --- | --- | --- | --- |
| 1 | Setup | T001–T006 | Công cụ và skeleton build được định nghĩa, có wrapper và hướng dẫn dự thảo |
| 2 | Foundational design (blocking) | T007–T012 | Có mô hình/architecture/protocol/sync draft và Constitution Gate 0 |
| 3 | US1 — Setup & run | T013–T019 | Checkout mới build, chạy, test cơ sở được |
| 4 | US2 — App shell | T020–T025 | Swing shell minh bạch, khởi động/đóng không phụ thuộc peer |
| 5 | US3 — Shared contracts | T026–T030 | Data/metadata validators và contract review phù hợp giai đoạn 02–07 |
| 6 | US4 — Verification & handoff | T031–T035 | Báo cáo kiểm thử, xử lý kết quả fail và gate đủ điều kiện M0 |
| 7 | Polish / cross-cutting | T036–T041 | README, docs, kiểm tra chéo và quyết định nghiệm thu M0 |

---

## Phase 1: Setup — Shared Infrastructure

**Mục đích:** Chuẩn bị project Maven Java 21 trên Windows 11, test framework và quy ước repository.

- [x] **T001** Nghiên cứu và ghi quyết định D-01…D-09 (JDK 21 Temurin, Swing, Maven Wrapper, JUnit Jupiter, TCP/JSON draft, P2P, SQLite/sync dự kiến), xác minh tính khả dụng của phiên bản được chọn và trade-off tại `specs/001-project-foundation/research.md`. **Xong khi:** nêu rõ cái nào được chốt ở M0, cái nào chỉ là hướng tương lai; không có version được tuyên bố “đã kiểm chứng” nếu chưa xác minh.
- [x] **T002 [P]** Tạo `.gitignore` để bỏ qua `target/`, file IDE, logs, tệp tạm, DB/history thực và file được nhận; giữ tài liệu, source, test, wrapper trong Git. **Xong khi:** `git status` không đưa dữ liệu runtime vào staging và không vô tình ignore source.
- [x] **T003** Tạo `pom.xml`: Maven single-module Java 21, `maven.compiler.release=21`, UTF-8, JUnit Jupiter (bản patch đã xác minh), Surefire và các Maven plugin được pin; xác định entrypoint để chạy ứng dụng ở US1. **Phụ thuộc:** T001. **Xong khi:** dependency/plugin declaration hợp lệ và không kéo GUI/network runtime library ngoài yêu cầu.
- [x] **T004** Thiết lập `mvnw.cmd` và `.mvn/wrapper/maven-wrapper.properties` cùng những thành phần wrapper cần thiết theo Maven 3.9.16; không commit file thực thi/URL không tin cậy. **Phụ thuộc:** T003. **Xong khi:** `mvnw.cmd -version` chạy trên máy Windows được hỗ trợ, version khớp hướng dẫn.
- [x] **T005 [P]** Tạo bản nháp `specs/001-project-foundation/quickstart.md` ghi điều kiện tiên quyết (Windows 11 x64, JDK 21, Maven Wrapper), cách kiểm tra môi trường và kế hoạch lệnh build/run/test; **đánh dấu chưa kiểm chứng** cho tới T017/T033.
- [x] **T006 [P]** Tạo `scripts/check-environment.ps1` để báo thông tin Windows, `java -version`, `javac -version` và tình trạng Maven Wrapper; trả lỗi rõ khi thiếu điều kiện, không tự động thay đổi PATH/Windows Firewall. **Xong khi:** người dùng có thể kiểm tra điều kiện tiên quyết mà không cần IDE.

**Checkpoint Setup:** Có cấu hình dự án, wrapper và công cụ xác định môi trường; **chưa** coi ứng dụng đã hoạt động. T003 → T004 phải tuần tự; T002, T005, T006 có thể làm song song nếu khác file.

---

## Phase 2: Foundational — Design & Contracts (BLOCKING)

**Mục đích:** Hoàn thiện nền thiết kế có thể review trước khi triển khai theo User Story. **Phụ thuộc:** quyết định nghiên cứu ở T001. Những đầu việc dưới đây là tài liệu/contract, không tạo socket hay DB thật.

- [x] **T007 [P]** Viết `docs/architecture.md`: sơ đồ UI (Swing EDT), application, domain, protocol, transport (002) và persistence (005); hướng phụ thuộc; cách nhận event từ worker; vòng đời startup/shutdown; quy tắc không có socket I/O trên EDT. **Xong khi:** reviewer chỉ ra được nơi đặt từng chức năng và tầng nào không được import tầng nào (FR-006/007).
- [x] **T008 [P]** Viết `specs/001-project-foundation/data-model.md` về các khái niệm `Peer`, `Conversation`, `Message`, `Group`, `Attachment`, `Transfer`, `ConnectionState`, `HistoryEvent`, `SyncCursor`; phân biệt `deviceId`, `userId`, `displayName`, `messageId`, `eventId`; nêu rõ mô hình nào chưa được hiện thực. **Xong khi:** có thuộc tính, quan hệ và ví dụ trùng display name/khác thiết bị.
- [x] **T009 [P]** Soạn `specs/001-project-foundation/contracts/protocol-v1-draft.md`: ví dụ envelope JSON UTF-8 gồm `protocolVersion`, `messageId`, `type`, sender, destination, `sentAt`, payload; mô tả length-prefix, giới hạn **đề xuất** 1 MiB control frame, unknown type/version, malformed input, UTC và error semantics. **Xong khi:** có ví dụ hợp lệ/không hợp lệ và không dùng native Java deserialization.
- [x] **T010 [P]** Soạn `specs/001-project-foundation/contracts/history-sync-design.md`: CL-04, local-first SQLite dự kiến, exchange summary/request/batch, event IDs, dedup/idempotency, trạng thái offline, concurrent events, auth + quyền hội thoại và tombstone/chính sách xóa còn mở. **Xong khi:** giải thích được S1 offline, S2 duplicated event, S3 unauthorized peer; không yêu cầu server trung tâm hoặc tuyên bố đã có sync.
- [x] **T011 [P]** Tạo `docs/feature-matrix.md` ánh xạ **đủ 11 chức năng** (P2P, Group, File, Emoji, History, Image, Video, Sound, State, Time, Sender) tới layer, giai đoạn 02–07, ranh giới dữ liệu và tính năng ngoài phạm vi M0. **Xong khi:** reviewer đếm đủ 11 mục, Media không bị hiểu là real-time call.
- [x] **T012** Review T007–T011, ghi Constitution Check **I–VII ở mức thiết kế**, các quyết định bị hoãn, rủi ro của auth/history sync và liên kết bằng chứng tại `specs/001-project-foundation/checklists/constitution-check.md`. **Phụ thuộc:** T007–T011. **Xong khi:** không có `MUST` bị bỏ qua; vi phạm (nếu có) được đánh dấu blocked thay vì PASS.

**Checkpoint Foundational:** Design đủ ổn định để các story có thể triển khai độc lập theo contract. **Không** tự đánh dấu đạt M0 ở checkpoint này.

---

## Phase 3: User Story 1 — Thành viên mới build/run được dự án (P1)

**Story goal:** Người mới có thể build, chạy, test cơ sở từ checkout sạch trên Windows 11 x64, không phụ thuộc cấu hình IDE bí mật.

**Independent Test:** Một checkout sạch chạy theo quickstart, kiểm tra lệnh build tạo đầu ra và entrypoint IntraChat khởi động đúng.

### Tests first

- [x] **T013 [US1]** Viết test JUnit cho thông tin khởi chạy tối thiểu (tên ứng dụng và phiên bản/trạng thái nền tảng) ở `src/test/java/vn/intrachat/application/AppInfoTest.java`; quan sát test fail/red trước implementation. **Xong khi:** test có assertion có ý nghĩa, không phải `assertTrue(true)`, kết quả đỏ được ghi nhận.

### Implementation

- [x] **T014 [US1]** Triển khai value object/constant `AppInfo` trong `src/main/java/vn/intrachat/application/AppInfo.java` theo test T013; tên hiển thị là `IntraChat`, tránh hard-code nhiều nơi. **Xong khi:** test T013 chuyển green.
- [x] **T015 [US1]** Tạo entrypoint `src/main/java/vn/intrachat/IntraChatApplication.java` làm bộ khởi chạy tối thiểu, in trạng thái nền tảng có nghĩa khi chưa tích hợp Swing; không kết nối Internet/LAN. **Xong khi:** process start được và chưa thông báo online giả.
- [x] **T016 [US1]** Cấu hình entrypoint trong `pom.xml` để chạy **một cách đã được kiểm tra thực tế** (`exec:java` hoặc JAR có manifest phù hợp); không hứa `java -jar` chạy được nếu chưa có manifest/dependencies tương ứng. **Phụ thuộc:** T015.
- [x] **T017 [US1]** Trên Windows 11 x64 chạy `mvnw.cmd clean package`, lệnh chạy đã chốt và `mvnw.cmd test`; ghi đầy đủ command, exit code, Java/Maven version, kết quả vào `specs/001-project-foundation/quickstart.md`. **Phụ thuộc:** T013–T016; lần chưa chạy phải ghi NOT RUN.
- [x] **T018 [US1]** Cập nhật hướng dẫn setup/build/run/test và lỗi thường gặp thực tế trong `README.md` dựa trên T017; không gỡ cảnh báo “chưa có chat” và không tuyên bố socket/history đã triển khai.
- [x] **T019 [US1]** Làm lại luồng build/run/test từ checkout sạch hoặc một thư mục làm việc sạch theo `specs/001-project-foundation/quickstart.md`; ghi kết quả tái lập vào `specs/001-project-foundation/evidence/us1-reproducibility.md`. **Xong khi:** AC-01-A có bằng chứng, ghi blocker nếu chưa thể tái lập.

**Checkpoint US1:** Có bản chạy tối thiểu và build/test cơ sở; chưa đòi hỏi UI chat. T015 và T016 cùng thay đổi launcher/pom nên cần tuần tự. US2 sau này thay launcher thành Swing mà phải giữ được lệnh run từ US1.

---

## Phase 4: User Story 2 — Khung IntraChat trung thực, đóng an toàn (P1)

**Story goal:** Người dùng thấy một cửa sổ Swing trên Windows, có tên IntraChat và trạng thái chưa kết nối; không cần peer/Internet.

**Independent Test:** Mở/đóng trên Windows 11 x64, tối thiểu năm chu trình không peer; không có tiến trình còn chạy hoặc trạng thái online giả.

### Tests first

- [x] **T020 [US2]** Viết unit test cho trạng thái khởi động `DISCONNECTED` và việc không thể suy diễn trạng thái `READ`/`DELIVERED` chỉ từ trạng thái kết nối ở `src/test/java/vn/intrachat/domain/ConnectionStateTest.java`. **Xong khi:** test red trước khi có model và không giả lập socket.

### Implementation

- [x] **T021 [US2]** Tạo kiểu `ConnectionState` riêng tại `src/main/java/vn/intrachat/domain/ConnectionState.java`; tách khỏi message delivery state; làm T020 green.
- [x] **T022 [US2]** Tạo `src/main/java/vn/intrachat/ui/MainWindow.java` làm UI Swing tối thiểu: title `IntraChat`, nhãn “Chưa kết nối — Chat chưa sẵn sàng”, đóng cửa sổ theo vòng đời đúng; không hiển thị tin nhắn/peer giả.
- [x] **T023 [US2]** Cập nhật `src/main/java/vn/intrachat/IntraChatApplication.java` để khởi tạo UI trên Swing EDT bằng `SwingUtilities.invokeLater`, xử lý lỗi khởi chạy có kiểm soát và không tạo worker/socket ngầm. **Phụ thuộc:** T022; kiểm tra lại lệnh run của US1.
- [x] **T024 [US2]** Chạy kiểm tra thủ công 5 lần mở/đóng trên Windows GUI thật; theo dõi process, startup/exit, title và trạng thái; ghi kết quả **PASS/FAIL/NOT RUN** vào `specs/001-project-foundation/evidence/us2-lifecycle.md`. **Xong khi:** nếu chưa đủ 5/5 thì US2 chưa được verified.
- [ ] **T025 [US2]** Kiểm thử khởi động **không có peer và không cần Internet**, lỗi hiển thị, đóng ngay sau mở; cập nhật kết quả vào `specs/001-project-foundation/evidence/us2-lifecycle.md`. **Xong khi:** UI không thể hiện kết nối hay delivered/read khi chưa có tính năng.

**Checkpoint US2:** Có cửa sổ dùng để demo nền tảng và thoát ổn định; chưa có chat hoặc kết nối mạng. T020 → T021 → T022/T023 → T024/T025 là đường chính. Nếu muốn kiểm thử tự động GUI, phải bảo đảm test ổn định trên Windows; không thay thế kiểm tra thủ công thực tế.

---

## Phase 5: User Story 3 — Kiến trúc & hợp đồng chung (P1)

**Story goal:** Có mô hình identity/metadata tối thiểu đã được test và tài liệu kiến trúc, protocol, sync có thể review mà không cần socket hoạt động.

**Independent Test:** Đọc `docs/architecture.md`, `data-model.md`, `contracts/`; chạy test identity/metadata; mô tả một thông điệp P2P và Group cùng ba trường hợp sync, không chạy mạng thật.

### Tests first

- [x] **T026 [P] [US3]** Viết `src/test/java/vn/intrachat/domain/PeerIdentityTest.java` cho định danh ổn định, display name trùng/đổi, ID rỗng hoặc bất hợp lệ; xác nhận red trước implementation.
- [x] **T027 [P] [US3]** Viết `src/test/java/vn/intrachat/protocol/MessageMetadataTest.java` cho message ID, sender device ID, destination, timestamp UTC, type và lỗi thiếu metadata; thêm kiểm tra tiếng Việt/emoji Unicode; test phải fail trước implementation.

### Implementation

- [x] **T028 [US3]** Tạo `src/main/java/vn/intrachat/domain/PeerIdentity.java` (immutable value object), phân biệt ID với tên hiển thị, validation không dùng IP/display name làm identity; làm T026 green.
- [x] **T029 [US3]** Tạo `src/main/java/vn/intrachat/protocol/MessageMetadata.java` chứa metadata typed/immutable cơ bản phù hợp `contracts/protocol-v1-draft.md`, có kiểm tra null/invalid fields; làm T027 green. **Không** tạo socket, JSON parser hay History synchronization service trong M0.
- [x] **T030 [US3]** Chạy toàn bộ test domain/protocol và review `docs/architecture.md`, `specs/001-project-foundation/data-model.md`, `specs/001-project-foundation/contracts/protocol-v1-draft.md`, `specs/001-project-foundation/contracts/history-sync-design.md`: kiểm tra versioning, sender/time/state, 11 features, 4 CL, tối thiểu S1–S3, không vi phạm phân lớp. Ghi các điểm cần xử lý ở `specs/001-project-foundation/evidence/us3-contract-review.md`.

**Checkpoint US3:** Định danh và metadata có test; giao thức chỉ ở mức data contract/draft và **không tuyên bố đã truyền/đồng bộ**. T026/T027 chạy song song; T028 phụ thuộc T026; T029 phụ thuộc T027; T030 phụ thuộc cả hai.

---

## Phase 6: User Story 4 — Kiểm chứng và bàn giao mốc M0 (P2)

**Story goal:** Có bằng chứng rõ ràng cho build/run/test, phát hiện test thất bại thực sự, Constitution Check Gate 1 và giới hạn bàn giao; không nâng trạng thái khi chưa đủ điều kiện.

**Independent Test:** Từ một checkout sạch, chạy lại các lệnh đã công bố; đọc báo cáo M0, đối chiếu 9 tiêu chí AC-01-A…I và 7 nguyên tắc Constitution.

### Test infrastructure & evidence

- [x] **T031 [US4]** Viết `scripts/verify-m0.ps1` chạy các bước **không phá dữ liệu**: check environment, build, unit tests, kiểm tra artifact/report test và báo exit code. Không tự động sửa source hoặc công bố PASS nếu không có bài test được thực thi.
- [x] **T032 [US4]** Kiểm chứng tình huống **test-fail** trong một bản làm việc tạm (hoặc worktree/checkout tách biệt): cố ý đưa một assertion sai, chạy `mvnw.cmd test`, xác nhận exit code khác 0, hoàn nguyên mọi thay đổi tạm; ghi kết quả vào `specs/001-project-foundation/evidence/m0-test-results.md`. **Không tạo test fail cố ý trong source chính**.
- [x] **T033 [US4]** Thực hiện kiểm chứng checkout sạch trên Windows 11, build/run/test và thử offline sau khi cache dependency; bổ sung điều kiện máy, commands, thời gian/exit code và hạn chế trong `specs/001-project-foundation/evidence/m0-test-results.md` và `specs/001-project-foundation/quickstart.md`. **Phụ thuộc:** kết quả US1–US3. Không khẳng định build lần đầu không cần Internet.
- [x] **T034 [US4]** Rà soát nguồn/log/artifact và Git hygiene: không secret, không nội dung chat thật, không Java deserialization dữ liệu mạng, không socket/network trong M0, không giả `read/delivered`; ghi kết quả kiểm tra I–VII sau code tại `specs/001-project-foundation/checklists/constitution-check.md`. **Phụ thuộc:** mã nguồn US1–US3.
- [x] **T035 [US4]** Tạo `specs/001-project-foundation/evidence/m0-acceptance-report.md`: bảng **AC-01-A…I**, SC-001…009 với trạng thái **PASS/FAIL/NOT RUN/BLOCKED**, link bằng chứng V-01…V-10, vấn đề tồn đọng và kết luận M0 có sẵn sàng để bắt đầu 002 hay không. **Phụ thuộc:** T031–T034. **Chưa đủ điều kiện thì chưa được ghi `Verified`**.

**Checkpoint US4:** Có thể biết chính xác những gì đã được chứng minh. Test fail thử nghiệm và các báo cáo chỉ ghi kết quả **đã thật sự chạy**.

---

## Phase 7: Polish & Cross-Cutting Concerns

**Mục đích:** Khép kín giai đoạn 01 theo Spec Kit, bảo đảm tài liệu phản ánh code và test thực sự.

- [x] **T036 [P]** Đối chiếu `specs/001-project-foundation/spec.md`, `specs/001-project-foundation/plan.md`, `specs/001-project-foundation/data-model.md` và `docs/feature-matrix.md`; xác nhận đủ CL-01…04, FR-001…023, 11 chức năng và các ranh giới “thiết kế trước, hiện thực sau”. Sửa chỗ lệch nếu có, **không nới scope ngoài M0**.
- [x] **T037 [P]** Review `specs/001-project-foundation/contracts/protocol-v1-draft.md` và `specs/001-project-foundation/contracts/history-sync-design.md` cho security/permission, offline, dedup, timestamp, framing/limit và unknown version; ghi quyết định còn chờ 002/005, tránh dùng `displayName` hay IP làm authentication.
- [x] **T038** Hoàn thiện `README.md`: platform Windows 11, Java 21, lệnh build/run/test **đã kiểm chứng**, giới hạn offline build lần đầu, chưa có chat/socket/sync/file/media thật và dẫn tới `specs/001-project-foundation/quickstart.md`; đồng bộ `ROADMAP.md` nếu thực trạng thay đổi. **Phụ thuộc:** T033 và báo cáo test.
- [x] **T039** Chạy `scripts/verify-m0.ps1`, `git diff --check`, kiểm tra artifacts và `git status`; xác nhận tệp runtime không bị theo dõi, JUnit test count lớn hơn 0, không có report giả. Ghi kết quả tại `specs/001-project-foundation/evidence/m0-test-results.md`. **Phụ thuộc:** T035/T038.
- [ ] **T040** Review Constitution Gate 1 lần cuối và đối chiếu **AC-01-A…I** với `specs/001-project-foundation/evidence/m0-acceptance-report.md`; chỉ thay trạng thái M0 thành `Verified` nếu từng điều kiện có bằng chứng và không còn vi phạm MUST chưa xử lý. **Phụ thuộc:** T036–T039.
- [ ] **T041** Bàn giao `specs/001-project-foundation/evidence/m0-acceptance-report.md` cùng quyết định/chỗ còn mở cho `002-lan-networking`: topology P2P, địa chỉ/port, handshake/pairing, framing, auth boundary, sẵn sàng prototype 2 peer. Không viết code 002 tại task này. **Phụ thuộc:** T040.

**Checkpoint cuối:** M0 đạt khi mọi AC/SC theo spec và Constitution có bằng chứng; nếu có mục FAIL/NOT RUN/BLOCKED phải báo rõ. Chuyển 002 chỉ sau khi nghiệm thu.

---

## Dependencies & Execution Order

### Phase Dependencies

```text
Phase 1 Setup (T001–T006)
            |
Phase 2 Foundational (T007–T012)
            |
    +-------+-------------------+
    |           |               |
Phase 3 US1   Phase 5 US3    (document review)
(T013–T019)  (T026–T030)
    |
Phase 4 US2
(T020–T025)
    |           |
    +-----+-----+
          |
Phase 6 US4 (T031–T035)
          |
Phase 7 Polish (T036–T041)
          |
     M0 Verified? -- yes --> 002-lan-networking
          |
          +------ no --> Resolve blockers, rerun gates
```

- **Phase 1** không phụ thuộc vào các tính năng sau. T003 cần T001, T004 cần T003.
- **Phase 2** cần quyết định T001 và nguồn tham chiếu `spec.md`/`plan.md`. T007–T011 có thể làm song song; T012 cần tất cả.
- **US1** cần Maven + JUnit hoạt động (Phase 1) và Gate 0 (Phase 2); T013 test-first, T014–T016 code, T017–T019 xác minh.
- **US2** phụ thuộc entrypoint US1 (T015–T016), để không tạo hai launcher cạnh tranh; các bước của US2 kiểm chứng độc lập khung UI.
- **US3** phụ thuộc Foundational design (T007–T012) nhưng **không cần UI US2**; T026/T027 có thể song song, sau đó triển khai tương ứng.
- **US4** cần báo cáo chạy code từ US1/US2/US3; không khẳng định hoàn tất US4 chỉ dựa trên spec/plan.
- **Polish** bắt đầu sau US4; task T036/T037 có thể làm song song vì review nguồn khác nhau, nhưng mọi thay đổi trùng tệp phải được phối hợp.

### Parallel Opportunities

| Nhóm | Task có thể chạy song song | Điều kiện |
| --- | --- | --- |
| Setup | T002, T005, T006 | Khác tệp, không phụ thuộc cấu hình Maven đã chạy |
| Foundation | T007, T008, T009, T010, T011 | T001 đã chốt hướng công nghệ; tránh thay đổi chéo tệp |
| Domain tests | T026, T027 | Phase 2 hoàn thành; khác package/file |
| Stories | US3 song song với US1/US2 | Không cùng sửa entrypoint/`pom.xml`; review hợp nhất trước US4 |
| Polish | T036, T037 | Hai nhóm tài liệu tách biệt |

### Test-First & Independent Acceptance

| Story | Định nghĩa kiểm thử độc lập | Bằng chứng dự kiến |
| --- | --- | --- |
| **US1** | Checkout sạch, build/test/run thành công, exit code phù hợp | `evidence/us1-reproducibility.md`, quickstart |
| **US2** | 5/5 lần khởi động/thoát Swing trên Windows không peer, trạng thái đúng | `evidence/us2-lifecycle.md` |
| **US3** | Unit tests identity/metadata pass; protocol + sync review đủ trường và tình huống lỗi | `evidence/us3-contract-review.md` |
| **US4** | Test runner phản ánh pass/fail thật, đủ báo cáo AC/SC và Constitution Gate 1 | `evidence/m0-test-results.md`, `evidence/m0-acceptance-report.md` |

### Traceability: Deliverables ↔ Spec ↔ Tasks

| Tiêu chí | Requirement | Tasks quan trọng |
| --- | --- | --- |
| **AC-01-A** | FR-001–003 | T001–T006, T013–T019 |
| **AC-01-B** | FR-014 | T003, T013–T014, T026–T029, T031–T033 |
| **AC-01-C** | FR-006–007 | T007, T012, T022–T023, T030, T034 |
| **AC-01-D** | FR-004–005 | T020–T025 |
| **AC-01-E** | FR-008–012 | T008–T010, T026–T030 |
| **AC-01-F** | FR-013 | T011, T030, T036 |
| **AC-01-G** | FR-015–017 | T005, T017–T019, T033, T038 |
| **AC-01-H** | FR-018 | T012, T031–T035, T039–T041 |
| **AC-01-I** | FR-019–023 | T001, T008–T011, T030, T036–T037 |
| **SC-001…009** | Mục tiêu đo lường trong spec | T017, T019, T024–T025, T030–T035, T039–T040 |

## Implementation Strategy

### First usable deliverable

1. Hoàn thành **Setup + Foundational** → tài liệu nền và hệ thống build được chuẩn bị.
2. Hoàn thành **US1** → baseline Java có thể build/run/test; ghi bằng chứng.
3. Hoàn thành **US2** → màn hình IntraChat đúng trạng thái, vòng đời ổn định.
4. Hoàn thành **US3** → metadata & contract đủ cho phase networking; test độc lập.
5. Hoàn thành **US4 + Polish** → nghiệm thu M0 trước khi viết spec/plan cho `002-lan-networking`.

### Ranh giới giai đoạn (anti-scope-creep)

**Cho phép trong 001:** Maven, Swing shell, pure Java value objects, JUnit tests, architecture docs, draft JSON protocol và History sync design.

**Không cho phép trong 001:** socket listener/client thật, UDP discovery, gửi tin P2P/Group, SQLite runtime database, thực thi sync History, truyền file, media playback, gọi video/voice hay server Internet. Những phần này phải có spec và tasks riêng.

### Điều kiện đánh dấu task hoàn thành

- Có tệp/đầu ra đúng đường dẫn hoặc kết quả review rõ ràng.
- Hành vi có test phù hợp; test thực sự chạy trên Windows mục tiêu nếu nhiệm vụ yêu cầu.
- Có log PASS/FAIL/NOT RUN và minh chứng cho nghiệm thu; không phỏng đoán.
- Không vi phạm Constitution, không tạo secret/runtime user data trong Git.
- Không dùng `[x]` cho những hạng mục chưa làm; các mục kiến trúc chỉ chứng minh **thiết kế**.

**Current state (2026-11-21):** Nền tảng Maven/Java/Swing, JUnit tests, tài liệu thiết kế và evidence đã được tạo. **38/41 tasks hoàn thành** theo kiểm chứng Windows 11 + JDK 25 (Java release 21). **T025** cần xác nhận GUI trong điều kiện Internet bị ngắt hoàn toàn; **T040** chờ Gate 1/JDK 21 runtime và acceptance đầy đủ; **T041** chờ bàn giao chính thức sau Gate 1. Không coi M0 đã Verified. Xem [M0 acceptance report](evidence/m0-acceptance-report.md).
