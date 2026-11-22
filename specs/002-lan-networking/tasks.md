> **Ghi chú phạm vi đồ án (22/11/2026):** [ROADMAP hiện hành](../../ROADMAP.md) · [11 chức năng và nghiệm thu lớp học](../../docs/course-project-scope.md) · [Tích hợp Messenger](../../docs/messenger-integration-plan.md). **M1 53/60**; TCP v1 chẩn đoán đã có dialog bên trong Messenger; chưa cần đợi review Gate1 kiểu production để code các chức năng 11 tính năng trên M2. **Các yêu cầu Gate/audit bảo mật chuyên sâu ở phần lịch sử bên dưới chuyển thành nâng cao, không phải điều kiện để bắt đầu code**. Giữ an toàn tối thiểu, không tắt TLS M2, không giả mạo trạng thái hoặc ghi đè file ngoài ý muốn. **Task checkbox/biên bản Gate cũ không bị đổi thành PASS**; khi có tính năng Live thật hãy ghi test và trạng thái riêng trong [ma trận 11 chức năng](../../docs/feature-matrix.md).

# Tasks: IntraChat — Giai đoạn 02 (LAN Networking / M1)

**Feature:** `002-lan-networking`
**Created:** 2026-11-21
**Status (2026-11-22):** **In Progress — 53/60 tasks checked, 7 externally blocked**; 16 local code/test tasks RED→GREEN complete, Temurin21 **172/172 JUnit PASS**, real M1 Swing diagnostics within the unified Messenger, and two separate local JVMs tested. **M1 Gate 1 NOT VERIFIED** until two physical Windows LAN hosts, downstream security/review and acceptance evidence. [Implementation evidence](evidence/m1-local-completion-2026-11-22.md).
**Inputs:** [spec.md](spec.md) · [plan.md](plan.md) · [requirements checklist](checklists/requirements.md) · [Constitution](../../.specify/memory/constitution.md)
**Goal:** Hai máy Windows trong cùng LAN kết nối **TCP P2P trực tiếp**, trao đổi **payload chẩn đoán hai chiều** qua handshake v1, nhập IPv4/port thủ công; inbound phải được người dùng Chấp nhận/Từ chối; không xác thực/mã hóa (cảnh báo); chỉ reconnect thủ công; có negative tests, cleanup và bằng chứng hai máy thật.

## Task Format & Rules

- Mỗi task dùng định dạng chuẩn **`- [ ] Tnnn [P?] [USn?] ...`**. Khi thực thi thành công và có bằng chứng mới chuyển thành `[x]`; **không** thay task ID bằng chữ đậm `**Tnnn**` để máy/Spec Kit parser dễ đọc.
- `[P]` nghĩa là có thể làm song song **khi phụ thuộc đã giải quyết**, tránh hai người cùng sửa một file. `[US1]`…`[US5]` liên kết tới 5 user stories của [spec](spec.md). Phase Setup/Foundational/Polish không bắt buộc story label.
- Mọi đường dẫn trong task là relative từ repository `IntraChat/`; **file chưa tồn tại được ghi là đầu ra cần tạo**, không được hiểu là đã có.
- **Test-first:** viết unit/integration test cho từng hành vi mới, thực thi để thấy fail (RED), sau đó viết code tối thiểu làm test pass (GREEN), refactor và chạy regression. Ghi lệnh/exit code/bằng chứng trong `specs/002-lan-networking/evidence/`. Không đánh dấu PASS nếu chưa chạy.
- **CL-02-01:** chỉ nhập **IPv4/port thủ công**, không có auto-discovery. **CL-02-02:** inbound **phải được người dùng Chấp nhận/Từ chối**. **CL-02-03:** chỉ **diagnostics chưa auth/chưa mã hóa**, cảnh báo rõ; không History/File/Group/Chat. **CL-02-04:** **reconnect do người dùng chủ động**, không tự retry.
- **M0 đã Verified — 41/41 tasks** sau kiểm thử JDK21 và GUI trong JVM network-denied, có [bằng chứng](../001-project-foundation/evidence/m0-jdk21-offline.md). T001 xác nhận [M0 dependency gate](evidence/m0-dependency-gate.md); sau Gate 0 design review mới bắt đầu code test-first của M1. M1 **chưa** Verified.
- Security gate: `CONNECTED` là phiên **transport sau consent + handshake**, **không** có nghĩa peer đã xác thực/mã hóa, không phải `sent/delivered/read/synced`. Không mở API xử lý dữ liệu riêng; no unsafe Java deserialization; không sửa Windows Firewall tự động.
- Không tự tạo commit/push từ việc đánh dấu task; tách các commit theo protocol, transport, UI, tests, docs khi thực hiện; không commit private data, network secrets hoặc file thu thập từ người dùng.

## Task Index & Dependencies

| Phase | Focus | IDs | Gate / deliverable |
| --- | --- | --- | --- |
| 1 | Setup, M0 prerequisite & research | T001–T008 | M0 Gate 1 rõ ràng, ADR/contract/model/quickstart + Constitution Gate 0 |
| 2 | Foundational tests, codec & state | T009–T017 | Parser/frame/DTO/config/session primitives test-first, kiểm thử âm tính |
| 3 | US1 — Manual LAN connection | T018–T024 | Listener/connect IP:port thủ công, UI commands, 2-peer loopback |
| 4 | US2 — Explicit approval & handshake | T025–T031 | Inbound Accept/Reject/timeout, v1 negotiation, duplicate protection |
| 5 | US3 — Diagnostic exchange | T032–T037 | Synthetic bidirectional Ping/Pong, Unicode, whitelist M1 |
| 6 | US4 — Reliability & manual reconnect | T038–T046 | Async lifecycle, manual retry, heartbeat, caps, shutdown |
| 7 | US5 — Windows LAN deployment | T047–T053 | Private-network warning, Windows guide, two real PCs, no Internet |
| 8 | Integration, evidence & release gate | T054–T060 | verify-m1, tests/results, Constitution Gate 1, M1 acceptance, handoff |

**Dependency spine:** T001 + T008 → T009–T017 → US1 (T018–T024) → US2 (T025–T031) → US3 (T032–T037); US4 tests/state start after foundational/session primitives, then integrate with US1–3; US5 manual/two-device acceptance follows stable US1–4; T054–T060 close M1. Documents with `[P]` may run independently during Gate 0, **not** code/network tasks blocked by M0.

**Suggested MVP work order:** T001, T002–T008, T009–T017, T018–T031, T032–T037, T038–T046, T047–T060. Run `.\mvnw.cmd clean test` after each relevant code task and preserve M0's existing 9 tests.

---

## Phase 1 — Setup, Research & Gate 0 (BLOCKING FOR CODE)

**Goal:** Xử lý phụ thuộc M0 và chốt lựa chọn khả thi trước khi đưa TCP/JSON/networking vào project. Những task tài liệu có thể hoàn thành khi T001 còn blocked, nhưng không đánh dấu code tests/behavior là đã thực hiện.

- [x] T001 Xác minh trạng thái `specs/001-project-foundation/tasks.md` và thực hiện/ghi rõ kết quả **T025 (offline GUI)**, **T040 (Constitution Gate 1 + JDK21)**, **T041 (M0 handoff)**; cập nhật `specs/001-project-foundation/evidence/m0-acceptance-report.md` và dẫn nguồn tại `specs/002-lan-networking/evidence/m0-dependency-gate.md`. **Done:** M0 thực sự Verified trên Windows + JDK21 hoặc có quyết định ngoại lệ **được phê duyệt rõ theo Constitution**, không giả định gate đã đóng.
- [x] T002 [P] Kiểm tra ADR-01…ADR-14 của [plan](plan.md), so sánh TCP/UDP, Java 21 virtual threads/executor, manual IPv4, prompt accept, diagnostics-only và manual reconnect; ghi trade-offs, giới hạn và nguồn tại `specs/002-lan-networking/research.md`. **Done:** tách rõ lựa chọn đã được user xác nhận với giả định HOW và chưa kiểm chứng.
- [x] T003 Thực nghiệm/kiểm tra **Jackson databind 2.22.4** và các transitive dependencies trên Maven + Java 21, review CVE/licensing/strict parser support; ghi ở `specs/002-lan-networking/research.md`, chỉ sau khi có bằng chứng mới cập nhật dependency phiên bản pin vào `pom.xml`. **Depends:** T002 để không ghi đồng thời vào `research.md`. **Done:** command/resolution exit code được lưu; version không được tuyên bố verified nếu chưa chạy.
- [x] T004 [P] Soạn `specs/002-lan-networking/contracts/network-v1.md`: 4-byte big-endian length, UTF-8 JSON, HELLO sentinel cho destination bootstrap, HELLO/ACCEPT/REJECT/CONFIRM/READY, DIAGNOSTIC_PING/PONG, ERROR, field validation + mã lỗi. **Done:** có JSON hợp lệ/không hợp lệ, frame > limit, unknown version/type và định nghĩa **không có payload trước Accept**; không cho CHAT/SYNC/FILE.
- [x] T005 [P] Soạn `specs/002-lan-networking/data-model.md`: Local/RemotePeer, `ConnectionSession`, `PendingApproval`, `NetworkConfig`, `NetworkEnvelope`, Frame, diagnostic probe, error/result và state transitions; ghi rõ self-declared UUID ≠ authenticated identity. **Done:** rà soát identity/consent/session/destination của spec với contract.
- [x] T006 [P] Viết `specs/002-lan-networking/quickstart.md` bản dự thảo: JDK21/Windows, IPv4/port manual, listen/connect, Accept/Reject, security warning, `loopback` và **2 máy LAN**; đánh dấu lệnh/chỉ dẫn **chưa test** cho tới T050/T056. **Done:** không khẳng định Internet/firewall tests đã pass.
- [x] T007 [P] Lập `specs/002-lan-networking/checklists/constitution-check.md`, tái kiểm tra nguyên tắc **I–VII** và bốn CL-02-01…04, đặc biệt trust boundary + không khai thác chat/History khi chưa auth. **Done:** Gate 0 design-only có pass/fail/open item, gate code chưa được tự động pass.
- [x] T008 Review chéo `specs/002-lan-networking/{spec.md,plan.md,research.md,data-model.md,contracts/network-v1.md,quickstart.md}` và `specs/002-lan-networking/checklists/constitution-check.md`; cập nhật rủi ro handshake/capping và traceability. **Depends:** T002–T007. **Done:** Gate 0 đạt mức thiết kế, ghi quyết định về port/timeouts/UUID/session trùng, ghi blocker chưa test. **Code gate:** T001 cũng phải pass mới chuyển sang Phase 2.

**Checkpoint Phase 1:** Có đường truy vết M0, hợp đồng v1 và Constitution Gate 0; **không một task code nào được làm trước gate**.

---

## Phase 2 — Foundational Codec / State (BLOCKING FOR USER STORIES)

**Goal:** Tạo nền giao thức/validation **test-first**, chống payload không tin cậy và giữ tách lớp protocol/domain/transport. **Depends:** T001 + T008.

- [x] T009 [P] Viết `src/test/java/vn/intrachat/protocol/FrameCodecTest.java` bao phủ prefix 4 byte unsigned big-endian, 1-byte fragments, 2 frames coalesced, size=0, >1MiB, unsigned overflow, EOF mid-prefix/body, preapproval 16KiB; chạy RED. **Done:** các assertion fail đúng lý do khi chưa có codec, ghi test command/result.
- [x] T010 Triển khai `src/main/java/vn/intrachat/protocol/FrameCodec.java` với cap trước cấp phát, strict length, bounded read/timeout và write, không dùng `ObjectInputStream`; chạy GREEN. **Depends:** T009. **Done:** FrameCodecTest PASS, không phân tách sai frame; không nạp frame > cap vào RAM.
- [x] T011 [P] Viết `src/test/java/vn/intrachat/protocol/JsonEnvelopeCodecTest.java` với valid/invalid/missing/null/duplicate key/trailing token/depth/UTF-8 lỗi/unknown type/version và Unicode; chạy RED. **Done:** test chứng minh parser không ngầm chấp nhận unknown chat/sync/file trong M1.
- [x] T012 Triển khai `src/main/java/vn/intrachat/protocol/JsonEnvelopeCodec.java`, `src/main/java/vn/intrachat/protocol/NetworkEnvelope.java`, `src/main/java/vn/intrachat/protocol/ProtocolException.java`, whitelist v1 M1, strict Jackson parser, chống duplicate/unknown fields, limit nesting, validate UUID/UTC; mở rộng `src/main/java/vn/intrachat/protocol/MessageType.java`. **Depends:** T011/T003/T004. **Done:** codec tests GREEN, 9 test M0 vẫn pass; dependency pin có bằng chứng.
- [x] T013 [P] Viết `src/test/java/vn/intrachat/protocol/HandshakeContractTest.java` cho HELLO destination sentinel duy nhất, claimed identity khác display name, supported versions, request/session correlation, forbidden diagnostic before approval; RED. **Done:** tests bắt đầu fail khi chưa có handshake DTO/validator.
- [x] T014 Triển khai `src/main/java/vn/intrachat/protocol/HandshakeMessage.java` và `src/main/java/vn/intrachat/protocol/HandshakeValidator.java` cho HELLO/ACK/correlation và message header theo `specs/002-lan-networking/contracts/network-v1.md`, giữ tương thích `MessageMetadata` M0. **Depends:** T012/T013. **Done:** HandshakeContractTest GREEN; không xem ID tự khai là authentication.
- [x] T015 [P] Viết `src/test/java/vn/intrachat/transport/NetworkConfigTest.java` về IPv4 literal, port 1..65535 (test port 0 nội bộ), bind-interface, self-connect/invalid multicast/broadcast, giới hạn 4 sessions/4 prompts/queue 16 frames & 2MiB; RED. **Done:** contract config thể hiện cả giới hạn và tên lỗi.
- [x] T016 Triển khai `src/main/java/vn/intrachat/transport/NetworkConfig.java` với validators và resource settings immutable, không chạm Swing; **Depends:** T015. **Done:** NetworkConfigTest GREEN, địa chỉ/cổng hợp lệ và giới hạn tài nguyên được kiểm tra trước khi tạo listener/connector.
- [x] T017 Viết **test-first** `src/test/java/vn/intrachat/domain/ConnectionSessionTest.java`, chạy RED, rồi tạo `src/main/java/vn/intrachat/domain/ConnectionSession.java` cho lifecycle/session state; `ConnectionState` M0 vẫn phân biệt `MessageDeliveryState`. **Depends:** T016. **Done:** GREEN; chỉ sau approval+HELLO_READY mới CONNECTED, failure/rejected/shutdown không hồi sinh session.

**Checkpoint Phase 2:** T009–T017 GREEN + M0 regression; đã có validation+state nhưng **chưa** kết nối TCP P2P thật.

## Phase 3 — User Story 1: Manual Direct LAN Connection (P1)

**Goal:** Mỗi máy có thể bắt đầu listener TCP và chủ động kết nối qua **IPv4:port nhập tay**, không auto-discovery hay server/cloud. Socket connect **chưa** đồng nghĩa session được phê duyệt/CONNECTED; US2 bổ sung approval/handshake.

**Independent test:** Hai tiến trình/loopback với cổng khác nhau và device ID khác nhau; A connect tới B qua IPv4:port, B thấy TCP candidate; port sai/refused/bind-conflict cho lỗi rõ. Khi chưa có approval/handshake, mọi candidate chỉ ở trạng thái pending, không nhận diagnostics.

- [x] T018 [P] [US1] Viết `src/test/java/vn/intrachat/transport/TcpListenerTest.java` kiểm tra bind `127.0.0.1` port test, accept candidate, stop listener, bind conflict, listener không nhận khi STOPPED, no inbound after shutdown; chạy RED. **Depends:** T016/T017. **Done:** lỗi được phân loại `BIND_FAILED`/listener-stopped thay vì treo hoặc lặng lẽ đổi cổng.
- [x] T019 [P] [US1] Viết `src/test/java/vn/intrachat/transport/TcpConnectorTest.java` kiểm tra IPv4:port hợp lệ, sai, refused, unreachable/timeout, cancellation, close socket, không retry tự động; chạy RED. **Depends:** T016/T017. **Done:** kết quả connect/cancel có timeout và không chạy EDT.
- [x] T020 [US1] Triển khai `src/main/java/vn/intrachat/transport/TcpListener.java` và `src/main/java/vn/intrachat/transport/TcpConnector.java`: direct TCP, bind selected IPv4 chỉ khi user bắt đầu; background accept/connect, timeout 5s, candidate socket chưa active. **Depends:** T018/T019. **Done:** hai test GREEN, listener/connector API dễ inject, không auto-discovery/port scan.
- [x] T021 [P] [US1] Viết `src/test/java/vn/intrachat/domain/PeerIdentityPersistenceTest.java` RED rồi tạo `src/main/java/vn/intrachat/application/LocalPeerProfile.java` lưu/tải UUID thiết bị ổn định + display name **không nhạy cảm** theo cấu hình người dùng, không lấy tên/IP làm khóa. **Depends:** T016. **Done:** cùng profile khởi động lại giữ ID; hai profile khác nhau có ID khác; invalid/corrupted config báo lỗi, không ghi thông tin chat/file/log riêng.
- [x] T022 [US1] Viết `src/test/java/vn/intrachat/application/NetworkCoordinatorTest.java` RED cho StartListen/StopListen/Connect/Cancel và trạng thái/event; tạo `src/main/java/vn/intrachat/application/NetworkCoordinator.java` và `src/main/java/vn/intrachat/application/NetworkEvents.java` để tách UI khỏi socket. **Depends:** T020/T021. **Done:** commands không block EDT, event có endpoint thật, chưa phát CONNECTED khi chưa có handshake.
- [x] T023 [US1] Viết UI smoke test/acceptance plan tại `specs/002-lan-networking/evidence/us1-manual-connect.md`; bổ sung `src/main/java/vn/intrachat/ui/NetworkPanel.java`, nối với `MainWindow.java` cho chọn local IPv4, Start/Stop listener, nhập peer IPv4:port, Connect/Cancel, các label state và lỗi. **Depends:** T022. **Done:** có thể thao tác bằng bàn phím trên Windows; UI không chứa `Socket` hoặc synchronous network calls; chưa bật UI diagnostics.
- [x] T024 [US1] Chạy `src/test/java/vn/intrachat/transport/TcpLoopbackIntegrationTest.java` (tạo trước khi kiểm thử); xác minh ít nhất **2 process/ports**, vai trò listener/initiator đổi chiều, loopback và bind-conflict; ghi `specs/002-lan-networking/evidence/us1-manual-connect.md`. **Depends:** T020/T022/T023. **Done:** có command/PID/exit code, lỗi connect dự kiến; test chưa có approval không được nhầm là session CONNECTED.

**Checkpoint US1:** Manual IPv4/port & socket candidate hoạt động ở mức transport và có giao diện; trước US2 chưa công bố M1 đạt AC-02-A/C.

---

## Phase 4 — User Story 2: Inbound Approval, Handshake & Identity (P1)

**Goal:** Mọi inbound candidate phải qua **Chấp nhận/Từ chối do người dùng thật quyết định**; version/UUID/nonce/handshake không giả mạo thành `authenticated`. Chỉ sau approval và `HELLO_READY` mới phát ACTIVE/CONNECTED; timeout, Reject và phiên trùng có cleanup.

**Independent test:** 2 process loopback + `InboundApprovalPort` do test điều khiển rõ ràng: accept, reject, 30s timeout (fake clock), malformed HELLO, wrong version, duplicate session, same display name/different ID. Trên UI thật người dùng phải bấm Accept trước khi thấy `CONNECTED`.

- [x] T025 [P] [US2] Viết `src/test/java/vn/intrachat/transport/HandshakeTest.java` cho HELLO→pending→ACCEPT/CONFIRM/READY, Reject, timeout, invalid/version mismatch/correlation, unsolicited diagnostic before approval và sender ID tự khai; chạy RED. **Depends:** T014/T020. **Done:** không có `CONNECTED` trước approval + handshake, mọi nhánh lỗi đóng candidate.
- [x] T026 [P] [US2] Viết `src/test/java/vn/intrachat/application/InboundApprovalPortTest.java` RED và tạo `src/main/java/vn/intrachat/application/InboundApprovalPort.java` (async decision Accept/Reject) + `InboundRequest.java` gồm endpoint quan sát được, identity tự khai và deadline. **Depends:** T022. **Done:** fake port cho tests trả quyết định *tường minh*; không có production default auto-accept.
- [x] T027 [US2] Triển khai `src/main/java/vn/intrachat/transport/TcpPeerSession.java` handshake state machine HELLO→request approval→HELLO_ACCEPT/REJECT→CONFIRM→READY, preapproval 16 KiB + 5s initial HELLO + 30s approval. **Depends:** T025/T026/T020/T014. **Done:** `HandshakeTest` GREEN, cancellation/invalid peer/timeout đóng stream+socket, không gửi diagnostics trước Accept.
- [x] T028 [US2] Bổ sung `src/main/java/vn/intrachat/ui/InboundRequestDialog.java` và wiring với `NetworkCoordinator.java`: prompt **không modal blocking EDT**, endpoint IP:port, UUID/tên tự khai, cảnh báo **chưa xác thực/chưa mã hóa**, Chấp nhận/Từ chối, thời gian còn lại, đóng/hết hạn là Reject. **Depends:** T026/T027. **Done:** user click thật quyết định, không lưu danh sách “trusted peers”; keyboard/cancel thao tác được.
- [x] T029 [P] [US2] Viết `src/test/java/vn/intrachat/transport/SessionRegistryTest.java` RED cho 1 active/remote self-declared UUID, simultaneous cross-connect, UUID colliding/spoofed, same displayName different ID, self-connect, reject duplicate pending, capacity 4. **Depends:** T016/T017. **Done:** các kết quả deterministic, không xem UUID trùng là proof auth, không leak session.
- [x] T030 [US2] Triển khai `src/main/java/vn/intrachat/transport/SessionRegistry.java` và giao tiếp với `TcpPeerSession.java` theo chính sách deterministic tie-break trong plan; mỗi session được đóng đúng một lần. **Depends:** T027/T029. **Done:** registry tests GREEN, session cạnh tranh không phát connected giả hay tăng worker vô hạn.
- [x] T031 [US2] Tạo/chạy `src/test/java/vn/intrachat/transport/InboundApprovalIntegrationTest.java` với hai **process** test-controlled approval (Accept, Reject, timeout); kiểm chứng UI thật thủ công trên Windows ở `specs/002-lan-networking/evidence/us2-approval-handshake.md`. **Depends:** T027/T028/T030. **Done:** log thời điểm pending/accept/READY và không có diagnostics/CONNECTED trước khi Accept, không mô tả fake test approval là production auto-accept.

**Checkpoint US2:** Approval+negotiation/identity/version verified tại loopback & GUI smoke; **chưa** chuyển thông tin chat hoặc History; security warning vẫn bắt buộc.

---

## Phase 5 — User Story 3: Versioned Diagnostic Exchange (P1)

**Goal:** Sau phiên được duyệt, hai peer trao đổi **synthetic PING/PONG** hai chiều với metadata chuẩn, Unicode và framing. Runtime **chỉ cho whitelist loại M1**, không gửi user chat/Group/History/File/Media.

**Independent test:** Hai process có handshake + Accept, mỗi phía gửi DIAGNOSTIC_PING, nhận đúng PONG/correlation và Unicode; unknown type/CHAT_TEXT/SYNC_BATCH/FILE_OFFER bị reject, không gọi bất kỳ application chat/storage service nào.

- [x] T032 [P] [US3] Tạo `src/test/java/vn/intrachat/transport/DiagnosticExchangeTest.java` RED: ping/pong hai chiều, UUID correlation, timestamp UTC, emoji/tiếng Việt UTF-8, duplicate probe ID behavior, not-connected send refused. **Depends:** T012/T027. **Done:** chưa có code diagnostics thì tests fail đúng nguyên nhân.
- [x] T033 [US3] Triển khai `src/main/java/vn/intrachat/application/DiagnosticService.java` và handler PING/PONG trong `TcpPeerSession.java`; chỉ nhận dữ liệu **synthetic ≤4 KiB**, không cho text chat tự do qua M1; **Depends:** T032/T031. **Done:** exchange tests GREEN, không suy ra `delivered/read` từ socket write.
- [x] T034 [US3] Tích hợp nút `Ping kiểm tra`, kết quả Pong/latency và trạng thái kỹ thuật trong `src/main/java/vn/intrachat/ui/NetworkPanel.java`; test UI state transitions trong `src/test/java/vn/intrachat/application/DiagnosticServiceTest.java`. **Depends:** T033. **Done:** nút chỉ enabled sau ACCEPT+READY; network I/O không chạm EDT; không có composer chat.
- [x] T035 [US3] Viết `src/test/java/vn/intrachat/protocol/M1TypeWhitelistTest.java` RED cho CHAT_TEXT, GROUP_MESSAGE, SYNC_*, FILE_* trước/sau handshake; enforce whitelist trong `JsonEnvelopeCodec.java` và service router. **Depends:** T012/T033. **Done:** unknown/future types trả `UNSUPPORTED_TYPE_FOR_PHASE`, không có side effect/history queries.
- [x] T036 [P] [US3] Bổ sung `src/test/java/vn/intrachat/protocol/FrameCodecTest.java` và `JsonEnvelopeCodecTest.java` về split UTF-8 multibyte qua reads, 2 frames coalesced, 1 MiB/+1, invalid surrogate/malformed JSON/extra trailing tokens và 16KiB preapproval. **Depends:** T010/T012. **Done:** luôn tách đúng frame, reject malformed trước allocation, M0 metadata tests không hỏng.
- [x] T037 [US3] Chạy automated **2-process end-to-end** approved sessions + PING/PONG cả hai chiều; ghi `specs/002-lan-networking/evidence/us3-diagnostics.md`, bao gồm command/exit code/Unicode probe và trường hợp unknown type. **Depends:** T033–T036. **Done:** đủ V-05/V-06/V-13 ở môi trường loopback, chưa coi là thực nghiệm hai máy LAN thật.

**Checkpoint US3:** AC-02-D/E mới có thể được báo PASS khi tests thật đã chạy; không áp dụng cho text chat/History.

## Phase 6 — User Story 4: Async Lifecycle, Bounded Resources & Manual Reconnect (P1)

**Goal:** Không chặn EDT; connect/accept/approval/diagnostics hủy và đóng sạch; chỉ **reconnect thủ công**. Có timeout, heartbeat, session/queue caps; không ngầm báo delivered/read/authenticated/synced.

**Independent test:** Hai peer loopback tạo/ngắt session, giả lập từ chối, peer crash, partial frame/silent socket, người dùng bấm Reconnect ba lần, click nhanh hai lần, shutdown trong lúc pending; quan sát state và cleanup.

- [x] T038 [P] [US4] Viết `src/test/java/vn/intrachat/application/ConnectionLifecycleTest.java` RED cho DISCONNECTED→CONNECTING→WAITING_REMOTE_APPROVAL→CONNECTED/FAILED/closed, user Cancel/Disconnect, manual Reconnect only, duplicate click, no auto reconnect. **Depends:** T017/T022. **Done:** events không hiển thị CONNECTED trước approve/READY hoặc sau khi socket đã đóng.
- [x] T039 [US4] Mở rộng `src/main/java/vn/intrachat/application/NetworkCoordinator.java` và `NetworkEvents.java` để chạy network tasks trong **Java 21 virtual threads**, dispatch EDT-safe events, hủy pending/socket, `Reconnect` chỉ qua button; **Depends:** T038/T031/T037. **Done:** lifecycle test GREEN; không chứa retry scheduler tự động, không block event handlers.
- [x] T040 [P] [US4] Viết `src/test/java/vn/intrachat/transport/ResourceLimitsTest.java` RED cho 4 active sessions, 4 pending approvals, 16 outbound frames AND 2MiB queued bytes, 16KiB HELLO, 1MiB frame và flooding pending; stress/chờ socket không gây unbounded workers. **Depends:** T030/T010. **Done:** kiểm tra chính xác giới hạn +1, rejection/recovery và số socket đóng.
- [x] T041 [US4] Thực thi caps/backpressure trong `src/main/java/vn/intrachat/transport/SessionRegistry.java`, `src/main/java/vn/intrachat/transport/TcpPeerSession.java` và `src/main/java/vn/intrachat/transport/NetworkConfig.java`, dùng semaphore/byte-count quotas, bounded waits, error codes; **Depends:** T040. **Done:** ResourceLimitsTest GREEN, không giữ queue/socket unbounded hoặc silent-drop diagnostics.
- [x] T042 [P] [US4] Viết `src/test/java/vn/intrachat/transport/HeartbeatTest.java` RED: ping every 10s/PONG within 30s, silent remote, session stop cancels timer, no stray PING after close, no auto reconnect. **Depends:** T033/T027. **Done:** fake clock/injected scheduler thay vì sleep dài trong unit tests, phát hiện half-open trong policy.
- [x] T043 [US4] Triển khai heartbeat/liveness và cleanup trong `TcpPeerSession.java`, `SessionRegistry.java` và coordinator: timeout → FAILED/DISCONNECTED + explicit `Kết nối lại`. **Depends:** T042/T039. **Done:** HeartbeatTest GREEN, không tạo ghost sessions/timer threads, không tự reconnect.
- [x] T044 [US4] Viết/ chạy test UI responsiveness và cancellation `src/test/java/vn/intrachat/application/NetworkCoordinatorTest.java`, bổ sung màn hình `NetworkPanel.java` cho Hủy/Ngắt/Kết nối lại, tránh blocking Swing EDT. **Depends:** T039/T043. **Done:** UI vẫn nhận click/timer trong connect/timeout; không dùng `Future.get`/socket I/O từ EDT; bằng chứng trong `specs/002-lan-networking/evidence/us4-lifecycle.md`.
- [x] T045 [US4] Bổ sung lifecycle shutdown `src/main/java/vn/intrachat/IntraChatApplication.java` và `MainWindow.java`: khi close, cancel approval, stop listener, close sessions/sockets, drain bounded workers rồi exit; viết `src/test/java/vn/intrachat/application/ShutdownTest.java` **RED trước code**, sau đó GREEN. **Depends:** T028/T039/T043. **Done:** 5 lần mở/đóng không có java process/socket/worker sót lại, ghi thời gian shutdown.
- [x] T046 [US4] Chạy test fault injection `src/test/java/vn/intrachat/transport/NetworkFaultTest.java` (thiết kế RED trước handler): TCP disconnect mid-frame, refused, slow HELLO, no user reply, cancel, port conflict, queue full, double reconnect click; ghi `specs/002-lan-networking/evidence/us4-lifecycle.md`. **Depends:** T041/T043–T045. **Done:** V-07/V-08/V-09/V-12 PASS có exit code/cleanup; không thay đổi kết quả chỉ vì retry tình cờ.

**Checkpoint US4:** Liveness/timeout/cancellation và giới hạn được kiểm chứng; **reconnect không xảy ra khi không có thao tác của người dùng**.

---

## Phase 7 — User Story 5: Real Windows LAN & Truthful Security Warnings (P2)

**Goal:** Chạy thật trên **hai máy Windows 11 x64**, không Internet, cùng LAN có kết nối trực tiếp; người dùng thấy IP/port, nhập thủ công, duyệt kết nối và đọc cảnh báo **chưa auth/chưa TLS**. Loopback **không thay thế** AC-02-A/I.

**Independent test:** Hai máy với IP Private LAN khác nhau, hai chương trình, bắt đầu listener thủ công, nhập IPv4/port, thấy inbound request, bấm Accept, trao đổi synthetic Ping/Pong cả hai chiều; kiểm chứng từ chối/timeout/firewall rule là các trường hợp lỗi có thể hiểu.

- [x] T047 [P] [US5] Viết `docs/windows-lan-setup.md` hướng dẫn xem IPv4/`ipconfig`, chọn Private network profile, bind interface/cổng **47321/TCP gợi ý**, Windows Defender Firewall inbound rule trên **Private** khi được phép, cách thu hồi rule; không yêu cầu quyền admin khi không có. **Done:** có hướng dẫn lỗi `BIND_FAILED/CONNECT_REFUSED`, client isolation, Wi-Fi/router, không Internet; **không tự sửa firewall**.
- [x] T048 [US5] Bổ sung cảnh báo trong `NetworkPanel.java` và `InboundRequestDialog.java`: “Chưa xác thực danh tính, kết nối chưa mã hóa — chỉ dùng chẩn đoán”, remote endpoint quan sát được, ID/tên **tự khai**; test `src/test/java/vn/intrachat/application/SecurityBoundaryTest.java` viết RED trước khi hoàn thiện UI/handler. **Depends:** T028/T034. **Done:** cảnh báo xuất hiện kể cả sau Accept; không thể chuyển tự phát qua History/File/Chat.
- [x] T049 [P] [US5] Chuẩn bị test script/hướng dẫn hai máy trong `specs/002-lan-networking/quickstart.md`: phiên bản Windows/JDK, cấu hình local IPv4 và port, hai thiết bị riêng, phương án LAN vẫn hoạt động khi Internet bị tắt, yêu cầu người dùng bấm Accept, checklist evidence và cách sanitize địa chỉ. **Depends:** T006/T047. **Done:** trình tự tái lập, **ghi NOT RUN** cho tới khi thật sự có hai máy.
- [ ] T050 [US5] Chạy hai **máy Windows LAN thật** kết nối trực tiếp với Internet không khả dụng, từ máy A nhập IPv4:port của B, B bấm **Chấp nhận**, kiểm tra handshake/version và Ping/Pong **hai chiều**, rồi đổi vai trò. Ghi ảnh/chứng cứ/log đã làm sạch và kết quả tại `specs/002-lan-networking/evidence/us5-real-lan.md`. **Depends:** T031/T037/T046/T048/T049. **Done:** AC-02-A/I có evidence thật từ cả hai máy; nếu thiếu máy, đánh dấu **BLOCKED/NOT RUN**, không gọi loopback là “2 máy LAN”.
- [ ] T051 [US5] Chạy trường hợp B bấm **Từ chối**, để prompt hết hạn, B tắt đột ngột, Windows firewall chặn/port conflict, A bấm reconnect **thủ công**; ghi ảnh/lỗi/cleanup đúng thực tế vào `specs/002-lan-networking/evidence/us5-real-lan.md`. **Depends:** T050. **Done:** nhập/approval/state/error không giả connected; khi không có test có chứng cứ phải ghi NOT RUN.
- [ ] T052 [US5] Kiểm chứng không sử dụng Internet/runtime public endpoint và không auto-discovery: bật LAN nội bộ nhưng ngắt đường Internet ở môi trường thử nghiệm có kiểm soát (không làm hỏng mạng chung), quan sát app chỉ dùng endpoint IP đã nhập, không broadcast/scan; ghi `specs/002-lan-networking/evidence/us5-offline-network.md`. **Depends:** T050. **Done:** kiểm tra LAN còn hoạt động, không đổi firewall hệ thống nếu không được phép; nếu không thể test, ghi blocker.
- [ ] T053 [US5] Review UI/thực thi bảo mật tại `specs/002-lan-networking/evidence/us5-security-review.md`: không auth/TLS/pairing, không stream chat/History/File, không quyền từ self-reported UUID, không nhận inbound tự động, không log dữ liệu riêng; cập nhật `README.md` **chỉ theo bằng chứng thực**. **Depends:** T048/T050–T052. **Done:** reviewer xác định rõ ranh giới và mọi cảnh báo chưa implement hoặc test chưa chạy được liệt kê trung thực.

**Checkpoint US5:** 2 máy thật + consent/diagnostics/Firewall/privacy evidence trước khi xem xét M1 gate; không có test thật thì **BLOCKED**.

---

## Phase 8 — Integration, Regression, Quality & M1 Handoff

**Goal:** Consolidate code/docs/tests, script không phá môi trường, rà soát Constitution Gate 1 và báo cáo từng AC/SC/V thực tế. **Depends:** US1–5, M0 gate được xử lý theo T001.

- [x] T054 [P] Tạo `scripts/verify-m1.ps1` dùng Maven Wrapper và `scripts/check-environment.ps1`: build/test với lệnh có exit code, kiểm tra JDK21 strict gate theo plan, chạy parser/session/2-process loopback tests và diagnostics, **không** tự cấu hình Firewall; **Done:** fail-fast nếu test fail, không nói real 2-PC PASS khi script chỉ chạy trên một PC.
- [x] T055 [P] Bổ sung regression tests và test-isolation cho `src/test/java/vn/intrachat/`: chạy `.\mvnw.cmd clean test` và `.\mvnw.cmd -o test` khi cache sẵn, kiểm tra ≥9 M0 tests tiếp tục PASS, không có flaky socket/port leaked giữa suites. **Done:** actual test totals, exit codes và môi trường tại `specs/002-lan-networking/evidence/m1-test-results.md`.
- [x] T056 Chạy `scripts/verify-m1.ps1`, một lần **clean source checkout hoặc isolated copy** có cache/Maven wrapper, và deliberately broken test trong **temporary checkout only** (phải fail, rồi restore/cleanup); ghi `specs/002-lan-networking/evidence/m1-test-results.md`. **Depends:** T054/T055. **Done:** có test totals, fail-on-error/negative proof, JDK21 runtime actual; không đổi assertion trong working tree chính.
- [x] T057 [P] Rà soát code `src/main/java/vn/intrachat/` và source Git: không native deserialization, network I/O trên EDT, auto-discovery, auto-reconnect, autoaccept, dữ liệu riêng trong logs, unsanitized input, JSON+frame allocation vượt cap, auth/sync claims; ghi `specs/002-lan-networking/evidence/m1-security-review.md`. **Done:** rủi ro/khắc phục và chứng cứ static/test được nêu cụ thể, **không** thay cho penetration test.
- [ ] T058 Cập nhật `specs/002-lan-networking/quickstart.md`, `docs/windows-lan-setup.md`, `README.md`, `ROADMAP.md` và `specs/002-lan-networking/contracts/network-v1.md` để phản ánh lệnh thực sự đã chạy, port/firewall, bốn CL, security warning, scope M1; **Depends:** T050–T057. **Done:** mọi câu “works/verified” có evidence; không gắn M2 chat hoặc M0 Verified nếu chưa đạt.
- [ ] T059 Kiểm tra `specs/002-lan-networking/checklists/constitution-check.md` **Gate 1 I–VII** và tạo `specs/002-lan-networking/evidence/m1-acceptance-report.md` đối chiếu **AC-02-A…K**, **SC-001…010**, **V-01…15**, M0 T025/T040/T041, gắn `PASS/FAIL/BLOCKED/NOT RUN`; **Depends:** T001/T050–T058. **Done:** release gate chỉ PASS khi không có vi phạm MUST/acceptance và test 2 PC thật.
- [ ] T060 Đối chiếu source/plan/spec/tasks, chạy `git diff --check` và `scripts/verify-m1.ps1` lần cuối; lập handoff `specs/002-lan-networking/evidence/m1-handoff.md` cho **003-p2p-messaging**, ghi API/network semantics, known security gaps, auth+ACL bắt buộc trước History 005. **Depends:** T059. **Done:** chỉ đánh dấu M1 `Verified` khi report thực sự đầy đủ; nếu chưa, giữ `In Progress/Blocked` và danh sách task tồn đọng.

**Checkpoint M1:** M1 hoàn thành khi các điều kiện nghiệm thu được chứng minh **thực tế**, không chỉ khi 60 task được tạo hay build loopback pass.

---

## Acceptance / Verification Traceability

| M1 acceptance | User story | Implementation tasks | Planned test evidence |
| --- | --- | --- | --- |
| **AC-02-A** Direct 2-PC P2P & diagnostics | US1 + US3 + US5 | T018–T024, T032–T037, T050 | V-06, V-10, **V-11** |
| **AC-02-B** Manual IPv4/port only | US1 + US5 | T015–T016, T020, T023, T047, T050 | V-02, V-11 |
| **AC-02-C** Consent, version & identity | US2 | T013–T014, T025–T031 | V-03, V-04, V-09 |
| **AC-02-D** Framing, Unicode & metadata | US3 | T009–T014, T032, T036 | V-05, V-06 |
| **AC-02-E** Malformed/unknown types rejected | US2 + US3 | T009–T014, T025, T035–T036 | V-04, V-05, V-13 |
| **AC-02-F** Timeout/cancel/manual retry & cleanup | US4 | T038–T046, T051 | V-07, V-08, V-09, V-12 |
| **AC-02-G** Honest nonblocking UI states | US2 + US4 | T022–T023, T028, T034, T038–T045 | V-03, V-12 |
| **AC-02-H** Windows private firewall guide | US5 | T047–T049, T051, T058 | V-02, V-11 |
| **AC-02-I** 2 process + 2 real Windows devices | US1–5 | T024, T031, T037, T050–T052 | V-10, **V-11**, V-14 |
| **AC-02-J** Diagnostic-only untrusted transport | US2 + US5 | T028, T035, T048, T053, T057 | V-13, V-15 |
| **AC-02-K** M0 gate & Constitution evidence | Cross-cutting | T001, T007–T008, T054–T060 | V-01, V-14, V-15 |

**Plan verification inventory:** V-01 M0/JDK21; V-02 endpoint/bind; V-03 consent; V-04 protocol negotiation; V-05 frames; V-06 Ping/Pong; V-07 timeout/cancel; V-08 retry/heartbeat; V-09 capacity/duplicates; V-10 2-process loopback; V-11 2-device LAN; V-12 UI/cleanup; V-13 whitelist/security; V-14 full build/regression; V-15 Constitution Gate 1. Các mã kiểm thử là kế hoạch, **không là kết quả đã chạy**.

## Parallel / Suggested Commit Boundaries

- **Docs/research:** T002/T004/T005/T006/T007 có thể chạy song song sau đọc spec/plan; **T003 đợi T002** vì cùng sửa `research.md`. T008 chỉ review sau tất cả.
- **Test-first protocol:** T009, T011, T013, T015 có thể độc lập viết RED nếu contract đã chốt; T010, T012, T014, T016/T017 nối theo dependencies. T036 có thể bổ sung sau khi codecs tồn tại.
- **Transport:** T018/T019 có thể viết tests song song; T020 thực hiện sau cả hai. T025/T026 và T029 có thể viết test độc lập; T027/T030 hội tụ handshake và session registry.
- **Application/UX:** test state T038 có thể bắt đầu sau domain/session model; UI T023/T028/T034 cần giao diện coordinator và flow event ổn định. T047 docs có thể làm trước chạy máy thật.
- **Integration:** T054 script, T055 regression và T057 static review có thể song song sau code cơ bản. T059 và T060 phải đi **tuần tự** sau evidence thật.
- **Reviewable commits (gợi ý, không tự commit):** `docs(network): finalize protocol/Constitution contract`; `test(protocol): add malformed-frame and handshake RED cases`; `feat(protocol): implement strict framed codec`; `feat(network): add manual TCP endpoints and user approval`; `feat(network): diagnostics and bounded lifecycle`; `test(network): real LAN evidence and M1 verification`; `docs: acceptance and Windows guide`. Không push private logs, secrets hoặc ảnh chứa thông tin cá nhân.

## Final Status Reminder

**As of 2026-11-21:** **37/60 tasks completed**, local network verification **45 JUnit tests PASS** on actual Temurin21 and two separate Java GUI processes exchanged Ping/Pong with human Accept. **M0 VERIFIED (41/41)**. Two physical Windows LAN PCs, extended resource/heartbeat testing and M1 Gate 1 remain **NOT RUN/BLOCKED**. Only check items with executable evidence; see [M1 acceptance report](evidence/m1-acceptance-report.md).
**Execution update — 2026-11-22:** Finished **T027, T029–T031, T033–T034, T038–T046 and T056** with actual Java21 RED→GREEN tests; [per-task evidence and cross-connect race fix](evidence/m1-local-completion-2026-11-22.md). Old task paths `vn.intrachat.ui.NetworkPanel`, `MainWindow`, `IntraChatApplication` are retired in the unified Messenger: equivalent technical-only UI/lifecycle are implemented in `ui.dialogs.LanDiagnosticsDialog`, reachable from `LanConnectionDialog`, without reintroducing another launcher. **M1 remains In Progress/NOT VERIFIED.** The seven still unchecked tasks **T050–T053/T058–T060** require two real physical Windows PCs or their dependent acceptance/review. Historical 2026-11-21 counts above are retained as dated evidence.