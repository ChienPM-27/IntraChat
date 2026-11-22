> **Rà soát source ngày 22/11/2026:** Lộ trình đối chiếu các lớp Java thực tế, `pom.xml`, test, working tree gồm thay đổi chưa commit và evidence. Ứng dụng dùng **một launcher `ui.IntraChat`** cho Demo/Live. M1 v1 là diagnostics plaintext; M2 v2 là prototype Live TLS. Mã đã tồn tại hoặc test localhost **không** đồng nghĩa nghiệm thu trên hai PC vật lý hay đạt security review. Xem [kiến trúc](docs/architecture.md) và [UI migration](docs/ui-migration-2026-11-21.md).

# IntraChat — Lộ trình triển khai chi tiết

> **Dự án:** IntraChat — Phần mềm Chat mạng LAN bằng Java
> **Phiên bản lộ trình:** 1.5.0 (đồng bộ theo source)
> **Ngày lập:** 21/11/2026 · **Cập nhật tiến độ:** 22/11/2026
> **Trạng thái:** **M0 Verified (41/41)**; **M1 In Progress (53/60)**; **M2 In Progress (65/90)**. **Temurin 21 đã chạy lại ngày 22/11/2026** với `scripts/verify-m2.ps1 -Offline`: **246/246 JUnit pass, 0 lỗi, exit 0**; baseline M2 commit độc lập trước đó có **107/107**. Đây là các mức kiểm thử cục bộ, **chưa** có hai PC Windows LAN thực hay audit bảo mật độc lập; **M1/M2 NOT VERIFIED**.
> **Nguồn yêu cầu:** P2P, Group, File, Emoji, History, Image, Video, Sound, State, Time, Sender
> **Tài liệu liên quan:** [README](README.md) · [AGENTS](AGENTS.md) · [Constitution](.specify/memory/constitution.md) · [Spec 001](specs/001-project-foundation/spec.md) · [Spec 002](specs/002-lan-networking/spec.md) · [Spec 003](specs/003-p2p-messaging/spec.md) · [Báo cáo blocker M2](specs/003-p2p-messaging/evidence/execution-blockers-2026-11-22.md)

## 1. Mục tiêu và nguyên tắc triển khai

Xây dựng ứng dụng Java cho phép các máy trong **cùng mạng LAN** trao đổi tin nhắn cá nhân, tin nhắn nhóm và tệp đa phương tiện; quản lý lịch sử hội thoại; hiển thị người gửi, thời gian và trạng thái một cách chính xác.

Nguyên tắc:

1. **LAN-first:** các chức năng lõi không bắt buộc Internet hay dịch vụ cloud.
2. **Chốt giao thức trước khi mở rộng:** mọi tin nhắn và sự kiện mạng đi qua hợp đồng dữ liệu nhất quán.
3. **Làm bản chạy được sớm:** hoàn thành P2P cơ bản trước Group, File và Media.
4. **Tái sử dụng:** Image/Video/Sound sử dụng cơ chế File Transfer khi có thể.
5. **Tách trách nhiệm:** UI, networking, protocol, service/domain và persistence không phụ thuộc chéo tùy tiện.
6. **Test theo từng giai đoạn:** không đợi đến cuối mới kiểm thử.
7. **Spec-driven:** từng nhóm tính năng có spec, kế hoạch, tasks và tiêu chí nghiệm thu rõ ràng.
8. **Không thổi phồng tiến độ:** chưa được test thì không đánh dấu verified; các lựa chọn kỹ thuật chưa chốt được ghi là TBD.

### 1.1. Phạm vi tính năng và mốc dự kiến

| Tính năng | Giai đoạn chính | Ưu tiên | Trạng thái từ source hiện tại |
| --- | --- | --- | --- |
| **P2P** | 2–3 | P0 | M1 v1: TCP diagnostic PING/PONG; M2 Live: text TLS1.3, xác minh fingerprint/SAS, mới thử localhost |
| **Group** | 4 | P1 | Có nhóm **Demo** trong `MockChatService`; **chưa có Group Live LAN** |
| **File** | 6 | P2 | FE `ComposerPanel`/`MediaFiles` cho **Demo**; **chưa có gửi/nhận File Live LAN** |
| **Emoji** | 3 | P0 | Unicode/emoji trong `ChatText` và `ChatV2Codec`, đã có test local |
| **History** | 5 | P1 | `LiveChatService.transcript` chỉ lưu **RAM phiên**, chưa có SQLite/History Sync |
| **Image** | 7 | P2 | Chọn/xem ảnh **Demo**; chưa có Image Live |
| **Video** | 7 | P2 | Media và trình phát **Demo**; chưa truyền Video Live, không phải gọi video |
| **Sound** | 7 | P2 | `VoiceRecorder` trong **Demo**; chưa truyền âm thanh Live, không phải gọi thoại |
| **State** | 2–3, 8 | P0 | M2 có `MessageAttempt`, app ACK và foreground READ; FAILED/UNCERTAIN và manual retry UI chưa nghiệm thu đủ |
| **Time** | 3 | P0 | Metadata UTC của `CHAT_V2`, FE hiển thị giờ; chưa đủ test lệch timezone/clock |
| **Sender** | 3 | P0 | M2 gắn sender/device ID với key/certificate, tách display name; test đổi alias/trùng tên còn mở |

**Phân biệt:** *Demo* = dữ liệu giả lập trên máy; *Live cục bộ* = code và một số kiểm chứng localhost; *Verified* = đủ acceptance/gates. Không suy ra Live Group/File/History/Media từ sự tồn tại của UI Demo. [Feature matrix](docs/feature-matrix.md) là chi tiết theo từng chức năng.

**Giới hạn phạm vi:** Video và Sound ở đây là **chia sẻ/phát nội dung**, chưa bao gồm gọi video hoặc gọi thoại thời gian thực. Những phần như Internet chat, cloud account và gọi điện trực tiếp chỉ thực hiện khi có yêu cầu và spec riêng. **History đồng bộ nhiều máy trong LAN** đã được xác nhận tại [CL-04](specs/001-project-foundation/spec.md), không yêu cầu server trung tâm; triển khai thuộc giai đoạn 05.

### 1.2. Công nghệ, quyết định và giới hạn đã xác nhận từ source

- **Build/runtime:** `pom.xml` dùng Java `--release 21`, Maven Wrapper, JUnit, Jackson, FlatLaf, Bouncy Castle cho X.509 và JNA/JNA-platform cho DPAPI. Windows 11 + **Temurin 21** là môi trường kiểm thử cục bộ. Dependency/license/CVE/provenance **chưa được phê duyệt độc lập**.
- **Một Frontend:** `ui.IntraChat` → `LoginFrame`; **Demo** dùng `MockChatService`; nút **Chat trong LAN (TLS 1.3)** mở `LanConnectionDialog`. `MainChatFrame` được dùng chung nhưng dữ liệu Demo/Live không trộn; Live qua `LiveChatAdapter`. Launcher `LiveIntraChat` và các UI M1 standalone cũ đã xóa; nút **Chẩn đoán TCP v1 (KHÔNG mã hóa)** trong `LanConnectionDialog` nay mở `LanDiagnosticsDialog` riêng bên trong Messenger, chỉ PING/PONG technical.
- **M1 v1:** `NetworkCoordinator`, `TcpListener`, `TcpPeerSession`, `JsonEnvelopeCodec` có TCP P2P IPv4/port thủ công, consent và PING/PONG. Đây là **diagnostics plaintext/chưa xác thực**, không có quyền chat/History/File/Group.
- **M2 v2:** `SecureChatCoordinator`, `TlsChatSession`, `ChatV2Codec`, `LiveChatService` cho pairing qua Accept + SAS/fingerprint, TLS1.3 và text/emoji/ACK/READ; `DeviceIdentityStore`/`PinnedPeerStore` quản lý thông tin thiết bị/trust bảo vệ theo Windows. **Prototype local, chưa security-certified**.
- **State/privacy:** application ACK khác sự kiện READ trong `LiveMessageVisibilityObserver` (foreground, viewport). **Đã xem = hiển thị trong ứng dụng**, không chứng minh mắt người nhận đã đọc. Chat, draft, receipt Live chỉ trong RAM phiên; chưa có durable History/Sync hoặc auto retry.
- **Khoảng trống kỹ thuật (cập nhật 22/11):** **Đã tích hợp shared v1/v2 dispatcher trên một cổng TCP**, v1 chỉ kỹ thuật plaintext với consent riêng, v2 chat TLS/SAS; **manual retry UNCERTAIN và 4-peer/128-pending/500-message/2MiB/1024-dedup caps đã có code/test**. Còn thiếu full pending/TLS queued-write/half-open/EDT adversarial stress, kiểm toán độc lập và DPAPI khác tài khoản — [M2 evidence](specs/003-p2p-messaging/evidence/m2-local-code-2026-11-22.md). Nhóm, History ACL/retention và giới hạn File/Media phải được đặc tả trước khi triển khai.
- **Ranh giới sản phẩm:** không Internet/cloud bắt buộc, không gọi video/voice thời gian thực, không tự mở public Firewall. Mốc M1/M2 chưa được release.

### 1.3. Bản đồ mã nguồn và bằng chứng

| Phần hệ thống | Nguồn hiện có | Có thể khẳng định |
| --- | --- | --- |
| Launcher & GUI | `src/main/java/ui/IntraChat.java`, `ui/dialogs/LanConnectionDialog.java`, `ui/frames/MainChatFrame.java` | Một FE với đường Demo và đường Live có consent/SAS |
| Demo | `src/main/java/model/mock/MockChatService.java`, `ui/components/ComposerPanel.java`, `ui/components/MediaFiles.java` | Nhóm/media demo có xử lý trong phiên, không truyền LAN |
| M1 | `src/main/java/vn/intrachat/application/NetworkCoordinator.java`, `transport/TcpPeerSession.java`, `protocol/JsonEnvelopeCodec.java` | v1 TCP chẩn đoán, local PING/PONG, không phải user chat |
| M2 | `application/SecureChatCoordinator.java`, `application/LiveChatService.java`, `transport/TlsChatSession.java`, `protocol/ChatV2Codec.java` | Text Unicode/ACK/view-READ qua TLS local, chưa two-PC/independent review |
| Identity | `security/DeviceIdentityStore.java`, `security/PinnedPeerStore.java`, `security/TlsContextFactory.java` | Code danh tính/trust có, external security approval chưa có |
| Test/build | `src/test/java/`, `scripts/verify-m2.ps1`, `target/surefire-reports/` | Local Java21 **246/246 JUnit**, clean package/JAR diagnostics PASS ngày 22/11 |

**Cấp độ evidence:** có Java source ≠ test qua từng luồng ≠ nghiệm thu. Test localhost/JUnit không thay thế hai PC Windows LAN thực, reviewer độc lập hay M1 Gate 1. Xem [M1 acceptance](specs/002-lan-networking/evidence/m1-acceptance-report.md), [M2 acceptance](specs/003-p2p-messaging/evidence/m2-acceptance-report.md) và [báo cáo blocker 22/11](specs/003-p2p-messaging/evidence/execution-blockers-2026-11-22.md).

## 2. Sơ đồ phụ thuộc và mốc phát hành

```text
01. Khởi tạo & kiến trúc
              |
02. Nền tảng kết nối LAN
              |
03. P2P + Sender + Time + State + Emoji  ---> MVP: chat cá nhân dùng được
           /           \
04. Group Chat      05. History
           \           /
              |
06. File Transfer
              |
07. Image + Video + Sound
              |
08. Integration + Testing + Release
```

Giai đoạn 4 và 5 có thể làm song song **sau khi** mô hình thông điệp và P2P ổn định. Trong tất cả giai đoạn, kiểm thử và cập nhật tài liệu được thực hiện liên tục; giai đoạn 8 là **kiểm thử tổng hợp/đóng gói**, không thay cho test của giai đoạn trước.

**Mốc bàn giao:**

- **M0 — Foundation ready (VERIFIED):** Java21 Windows 11, Maven/Swing foundation, 9/9 JUnit baseline, 5/5 GUI cycles với Java networking bị chặn trong JVM; M0 41/41 tasks.
- **M1 — LAN connected (IN PROGRESS):** **53/60 tasks**, Java21 full **172/172 JUnit PASS**, TCP v1 diagnostics nhập IP/port thủ công, real Swing `LanDiagnosticsDialog` trong Messenger với Accept/Reject, PING/PONG, Cancel/Disconnect/Manual Reconnect và 5 GUI cycles PASS. Đã kiểm thử Accept/Reject/timeout với **hai JVM trên một PC**, cross-connect và queue/heartbeat negative tests. **Hai PC LAN thật NOT RUN; M1 Gate 1 NOT VERIFIED** — [M1 evidence](specs/002-lan-networking/evidence/m1-local-completion-2026-11-22.md).
- **M2 — MVP (IN PROGRESS):** **65/90 tasks**; actual Messenger shared-port v1 diagnostics/v2 TLS verified routing, first-pair SAS, encrypted two-JVM text/emoji+ACK, UTC/sender, typed READ, manual UNCERTAIN retry UI and bounded RAM. Java21 **246/246 JUnit PASS**, nhưng **hai PC thật, độc lập security review và Gate 1 còn mở** — [test evidence](specs/003-p2p-messaging/evidence/m2-local-code-2026-11-22.md).
- **M3 — Core collaboration:** có Group và History đã nghiệm thu.
- **M4 — Media-capable:** File, Image, Video, Sound hoạt động theo đặc tả.
- **M5 — Release candidate:** bản đóng gói qua test tích hợp và có tài liệu sử dụng.

---

## 3. Giai đoạn 01 — Khởi tạo dự án và thiết kế kiến trúc

**Ưu tiên:** P0
**Spec Kit feature:** `001-project-foundation`
**Phụ thuộc:** không có
**Mục tiêu:** tạo nền tảng phát triển Java có thể build, run và test, thống nhất các ranh giới kiến trúc.

**Tiến độ thực tế (21/11/2026):** **M0 Verified, 41/41 tasks**; actual Temurin JDK21, 9/9 baseline JUnit, isolated cached build, 5/5 Windows GUI network-denied cycles. **M1 In Progress 37/60**, 45/45 JUnit pass sau merge FE, human-accepted PING/PONG hai GUI local. Xem [M0](specs/001-project-foundation/evidence/m0-acceptance-report.md) và [M1](specs/002-lan-networking/evidence/m1-acceptance-report.md).

### Công việc chi tiết

- [x] **01.01 — Chốt môi trường:** JDK, hệ điều hành mục tiêu, IDE gợi ý, Maven/Gradle và phiên bản thư viện.
- [x] **01.02 — Chọn UI:** phân tích Swing/JavaFX, xác định lý do, cách cập nhật UI thread và cơ chế đóng app.
- [x] **01.03 — Phác họa kiến trúc:** chia UI / service / domain / protocol / transport / persistence; xác định dependency direction.
- [x] **01.04 — Định nghĩa loại dữ liệu chính:** `Peer`, `Conversation`, `Message`, `Group`, `Attachment`, `Transfer`, `ConnectionState` (tên lớp cuối cùng có thể thay đổi).
- [x] **01.05 — Thiết kế hợp đồng thông điệp bản đầu:** message ID, version, type, sender ID, destination, timestamp, payload; quy tắc validation và giới hạn kích thước.
- [x] **01.06 — Thiết lập project skeleton:** cấu hình build, thư mục `src/main`, `src/test`, entry point, logging và cấu hình chạy.
- [x] **01.07 — Kiểm thử ban đầu:** unit-test mẫu, lệnh test/build có thể chạy được; CI nếu có môi trường phù hợp.
- [x] **01.08 — Cập nhật README:** ghi lệnh build/run/test thực tế sau khi đã xác minh.

**Lưu ý:** Dấu [x] 01.01–01.08 là tổng hợp đầu ra của roadmap; **M0 đã Verified 41/41** theo evidence. Ghi chú cũ về ba task M0 cuối chưa đóng chỉ đúng trước khi M0 được nghiệm thu.

### Kết quả bàn giao

- Source skeleton có thể khởi chạy giao diện/CLI tối thiểu và đóng sạch tài nguyên.
- Bản thiết kế kiến trúc và hợp đồng thông điệp sơ bộ.
- Có test framework và lệnh build/test chạy thành công trên máy phát triển.
- Mọi quyết định công nghệ đã được lưu trong kế hoạch, không chỉ truyền miệng.

### Tiêu chí nghiệm thu

- **AC-01-A:** clone dự án trên máy phù hợp, làm theo README thì build/run được.
- **AC-01-B:** unit test mẫu chạy và cho kết quả có thể lặp lại.
- **AC-01-C:** các tầng chính có ranh giới rõ, không buộc UI gọi trực tiếp socket I/O.

---

## 4. Giai đoạn 02 — Nền tảng kết nối mạng LAN

**Ưu tiên:** P0
**Spec Kit feature:** `002-lan-networking`
**Đặc tả:** [specs/002-lan-networking/spec.md](specs/002-lan-networking/spec.md)
**Trạng thái:** **In Progress — 53/60 tasks**; TCP v1 diagnostics, approval, cross-connect, heartbeat, bounded queue và dialog M1 riêng trong Messenger đã có source/test. Java21 **172/172 JUnit PASS**; **hai Windows PC LAN vật lý chưa thử, M1 Gate 1 NOT VERIFIED** — [local evidence](specs/002-lan-networking/evidence/m1-local-completion-2026-11-22.md).
**Phụ thuộc:** Giai đoạn 01 đã **VERIFIED (41/41)**. M1 còn thiếu stress/heartbeat/resource/GUI và kiểm thử 2 máy LAN thật.
**Mục tiêu M1:** hai máy Windows trong cùng LAN **kết nối trực tiếp P2P**, hoàn tất handshake/version negotiation, trao đổi **payload kỹ thuật** hai chiều, nhận biết trạng thái/mất kết nối, xử lý input không tin cậy và dọn tài nguyên. **Chưa có chat người dùng**; chức năng đó thuộc 03.

### Phạm vi & quyết định đã được xác nhận

- **Topology:** P2P trực tiếp, không cần server trung tâm hoặc cloud runtime. Máy có thể chủ động kết nối và chấp nhận kết nối đến; **không** tự động cho quyền đọc lịch sử.
- **Nền tảng:** Windows 11 x64, **Temurin Java 21 đã test thực tế** và M0 đã Verified. Tận dụng Swing, Maven Wrapper và typed metadata.
- **CL-02-01 — Discovery:** chỉ nhập **IP/port thủ công**; không auto-discovery/broadcast/scan trong M1.
- **CL-02-02 — Kết nối đến:** người dùng phải **Chấp nhận/Từ chối** trước khi session có hiệu lực; từ chối/hết hạn phải đóng socket và không chuyển dữ liệu chẩn đoán.
- **CL-02-03 — Security:** chỉ kết nối **chẩn đoán chưa xác thực/chưa mã hóa**, cảnh báo rõ; đồng ý kết nối không phải pairing/trust/History ACL.
- **CL-02-04 — Reconnect:** **người dùng chủ động Kết nối lại**, không có auto retry.
- **Giao thức M1 v1 đã triển khai cục bộ:** [plan 002](specs/002-lan-networking/plan.md) chọn TCP + framed JSON UTF-8, giới hạn frame và handshake/session/timeout; các kiểm chứng adversarial/stress/full two-PC còn mở.
- **Security:** thiết bị tự khai `deviceId` **không** mặc nhiên được xác thực; chưa có quyền với History/File/Group; phải công bố giới hạn mã hóa/xác thực nếu chưa có kiểm chứng.
- **Ngoài phạm vi:** P2P text chat, delivered/read, Group, History Sync, File, Image/Video/Sound; không gọi thời gian thực hoặc mở server Internet.

### Tình trạng từng nhóm việc M1 theo source (không thay 60 task của spec)

| Nhóm việc | Bằng chứng mã nguồn/test cục bộ | Phần chưa đủ nghiệm thu |
| --- | --- | --- |
| 02.01–02.02 Topology/IPv4 | `NetworkConfig`, `TcpListener`, `TcpConnector`; IP/port thủ công, Windows LAN guide | Chưa hai PC và firewall xác nhận trên máy B |
| 02.03 Handshake/consent | `NetworkCoordinator`, `InboundApprovalPort`, `HandshakeValidator`, `SessionRegistry` | Duplicate/timeout/resource negative matrix còn mở |
| 02.04 Framing | `FrameCodec`, `JsonEnvelopeCodec`; có test fragmentation/invalid type/size | Stress/fuzz/interop two-PC chưa đủ |
| 02.05 Diagnostic | `TcpPeerSession`, PING/PONG localhost hai process | **Chưa hai Windows PC vật lý** |
| 02.06–02.07 Async/lifecycle | Worker/timeout/cancel/close/reconnect thủ công; unit/loopback một số luồng | Heartbeat/soak, repeated retry, full EDT/resource measurements |
| 02.08–02.09 Untrusted/security | Parser nghiêm ngặt; v1 cấm user chat/History/File/Group | Audit và cảnh báo trên cấu hình LAN thật |
| 02.10 Automated tests | M1/FE/Group local regression **172/172 JUnit PASS** (gồm các test M1 mới); 2-host LAN chưa chạy | Full stress/physical LAN chưa chạy |
| 02.11 LAN thật | Hướng dẫn cấu hình đã có | **BLOCKED: cần hai PC Windows cùng LAN không Internet** |
| 02.12 Gate | [M1 acceptance](specs/002-lan-networking/evidence/m1-acceptance-report.md) ghi PASS/PARTIAL/BLOCKED | **M1 Gate 1 NOT VERIFIED** |

**Nguồn tiến độ chính thức:** [`specs/002-lan-networking/tasks.md`](specs/002-lan-networking/tasks.md): **53/60 checked, 7 mở**. “Có source” không tự làm task đạt DoD.

### Kết quả bàn giao

- Giao diện/luồng cấu hình kết nối P2P thủ công tới peer LAN; người dùng nhận biết `connecting/connected/disconnected/failed` đúng thực tế.
- Handshake có phiên bản và peer ID; payload thử nghiệm hai chiều theo hợp đồng protocol (không phải chat).
- API/event về connection lifecycle cho tầng application và UI, không để socket I/O trên EDT.
- Bộ test 2-process và **hai máy LAN thật**, bao gồm timeout, malformed input, cleanup; tài liệu IP/port/Firewall và hạn chế bảo mật.

### Tiêu chí nghiệm thu M1

- **AC-02-A:** Hai máy cùng LAN kết nối trực tiếp và trao đổi payload **kỹ thuật** hai chiều, không cần Internet/server.
- **AC-02-B:** **Chỉ** nhập IP/port thủ công và kết nối được, không có auto-discovery.
- **AC-02-C:** B **Chấp nhận** trước khi handshake hợp lệ; version và `deviceId` đúng cú pháp, không đánh đồng tên hiển thị/ID với identity xác thực.
- **AC-02-D:** Framing xử lý dữ liệu phân đoạn/gộp, UTF-8 và giới hạn kích thước.
- **AC-02-E:** Version/type không hỗ trợ, malformed/oversized/truncated frame bị từ chối an toàn.
- **AC-02-F:** Timeout/Từ chối/disconnect/cancel/**reconnect thủ công** không treo UI và không rò rỉ socket/worker.
- **AC-02-G:** UI thể hiện connection state thật; không báo delivered/read/authenticated/synced khi chưa có bằng chứng.
- **AC-02-H:** Có hướng dẫn Windows network profile/port/firewall/discovery và không tự mở public firewall.
- **AC-02-I:** Có kết quả chạy **hai process** và **hai máy LAN thật**, thành công/lỗi/cleanup được ghi lại.
- **AC-02-J:** Không tự động cấp quyền History/File/Group cho peer chưa xác thực; **chưa mã hóa, chưa auth; chỉ chẩn đoán**, công bố giới hạn rõ.
- **AC-02-K:** Gate M0 liên quan được giải quyết, M1 có Constitution Check và test thực thi trước khi Verified.

**Mốc M1 chưa nghiệm thu:** code v1 diagnostic và test localhost đã tồn tại, nhưng AC-02-A/I cùng Gate 1 thiếu thử hai máy thật và stress/negative evidence. Không dùng kết quả M2 TLS để tự động đóng M1.

---

## 5. Giai đoạn 03 — Chat P2P (MVP)

**Ưu tiên:** P0
**Spec Kit feature:** `003-p2p-messaging`
**Trạng thái:** **In Progress — 65/90** theo [M2 tracker](specs/003-p2p-messaging/tasks.md). Live text/emoji/ACK/READ đã có evidence localhost, nhưng Gate M1, security review và hai PC còn BLOCKED. Các ô 03.01–03.09 dưới đây là scope cấp roadmap, **không phải** trạng thái task chi tiết.
**Phụ thuộc:** giai đoạn 02
**Phạm vi tính năng:** **P2P, Sender, Time, State, Emoji**
**Mục tiêu:** hoàn thành phiên bản chat cá nhân tối thiểu có thể sử dụng.

### Tình trạng từng nhóm việc M2 theo source (không thay 90 task của spec)

| Nhóm việc | Source/test đã có | Khoảng trống còn lại |
| --- | --- | --- |
| 03.01 Identity/conversation | `P2PConversation`, `LiveChatService.conversationId`, binding peer/device key | Re-pair, tên trùng/đổi alias cần full acceptance |
| 03.02 Text/Unicode | `ChatText`, `ChatV2Codec`, `LiveChatService.sendText` và TLS transport; đã có GUI localhost | Cross-session/malformed/two-PC cases chưa đủ |
| 03.03 Messenger UI | `MainChatFrame`, `ComposerPanel`, `LiveChatAdapter`; một frontend Demo/Live | Keyboard/focus và >=5 GUI life cycles chưa đủ |
| 03.04 Sender | Sender ID, key/certificate tách displayName | Alias/clock/identity negative cases chưa nghiệm thu |
| 03.05 Time | `ChatV2Message` lưu thời gian UTC, FE trình bày local | Clock-skew/timezone two-peer coverage chưa đủ |
| 03.06 State | `MessageAttempt`, `DELIVERY_ACK`, `READ_RECEIPT`, `LiveMessageVisibilityObserver` | FAILED/UNCERTAIN bubble, forged receipt và read edge cases |
| 03.07 Emoji | Unicode code point validation và FE, JUnit Unicode | Biên Unicode trên hai host thật |
| 03.08 Offline/manual retry | UI khôi phục draft sau send fail; reconnect thủ công | **Retry/UNCERTAIN end-to-end UI chưa hoàn chỉnh** |
| 03.09 Test | **246/246 JUnit** và real two-JVM TLS text/emoji, app ACK, verified pin restart; Group/M1 baseline được tính trong tổng | RED→GREEN task còn mở, stress/security independent/two-PC |

**Bảo mật prototype ngoài checklist 03.01–03.09:** `SecureChatCoordinator`, `TlsChatSession`, `TlsContextFactory`, `DeviceIdentityStore`, `PinnedPeerStore` đã có implementation ghép cặp và bảo vệ trust local. **Chưa chứng nhận bảo mật; v1/v2 shared dispatcher đã được nối vào Messenger và có strict/negative tests**, M1 Gate 1 mở.

**Tracker thật:** [`specs/003-p2p-messaging/tasks.md`](specs/003-p2p-messaging/tasks.md): **65/90 checked, 25 mở**. [latest M2 coding and RED→GREEN evidence](specs/003-p2p-messaging/evidence/m2-continued-code-2026-11-22.md). Đã có shared v1/v2, manual UNCERTAIN retry và UI failure states, real two-JVM secure text, bounded RAM; backlog local còn secure TLS queue/EDT/full pairing & GUI stress. Blocker ngoài môi trường: T080–T084 two-PC; T003/T010/T083/T087/T090 review/security/handoff — [evidence](specs/003-p2p-messaging/evidence/m2-local-code-2026-11-22.md).

### Kết quả bàn giao

- Bản **MVP IntraChat** chat trực tiếp được giữa hai máy LAN.
- Tin nhắn hiển thị nội dung, người gửi, thời gian và trạng thái phù hợp.
- Emoji không bị mất hoặc biến dạng vì lỗi mã hóa.

### Tiêu chí nghiệm thu M2 theo Spec 003

- **AC-03-A/C và các luồng READ foreground cục bộ:** có test/UI evidence cho những trường hợp đã đo trên một Windows PC.
- **AC-03-D/F/G/H/I/K:** **PARTIAL** do retry/UNCERTAIN, security/identity/receipt negative matrix, UX/ressource/independent review chưa đủ.
- **AC-03-B/J:** **BLOCKED** — chưa chạy hai Windows PC vật lý trong LAN không có Internet công cộng.
- **AC-03-L:** **BLOCKED** — phụ thuộc M1 Gate 1, Constitution I–VII, security/dependency approval.
- Bảng AC-03-A…L chuẩn ở [M2 acceptance report](specs/003-p2p-messaging/evidence/m2-acceptance-report.md); không dùng 5 tiêu chí tóm tắt cũ thay cho 12 AC của Spec 003.

**Mốc M2 chưa Verified:** cần hoàn thành task có evidence, đầy đủ AC, two-PC LAN, M1 Gate 1 và độc lập review/signoff. Code local và JUnit pass **không** đồng nghĩa hoàn thành MVP production.

---

## 6. Giai đoạn 04 — Group Chat

**Ưu tiên:** P1
**Spec Kit feature:** [`004-group-chat`](specs/004-group-chat/spec.md) — **spec v0.2.0 đã xác nhận CL-04-01…06, có [technical plan](specs/004-group-chat/plan.md) và [92 tasks](specs/004-group-chat/tasks.md) (28/92, 22/11/2026); 28 Group JUnit mới, Gate 0 chưa có independent signoff; Group Live LAN chưa được nối vào UI/network**
**Phụ thuộc:** giai đoạn 03
**Phạm vi tính năng:** **Group**, tái sử dụng Sender/Time/State/Emoji
**Mục tiêu:** hỗ trợ trao đổi tin nhắn giữa nhiều thành viên trong LAN.

**Đối chiếu source — GROUP CORE PROTOTYPE ĐÃ CÓ, LIVE NETWORK CHƯA ĐƯỢC BẬT:** `model.mock.MockChatService.createGroup`, `ui.dialogs.CreateGroupDialog` và `MainChatFrame` chứng minh **nhóm Demo** hiện hữu. Ở chế độ Live, `LiveChatAdapter.createGroup` trả lỗi `UnsupportedOperationException` và `MainChatFrame` ẩn nút tạo nhóm. Hiện đã có prototype **GroupIdentity/GroupRoster, owner ECDSA signing, GroupV1Codec, GroupV3Bootstrap/Transcript, ProtectedGroupStore (DPAPI) và GroupOwnerGrantService** nhưng **chưa có Group v3 TLS dispatcher, Group send/receive LAN hoặc UI Live**. [Spec 004](specs/004-group-chat/spec.md) **đã Clarified** (CL-04-01…06 được xác nhận ngày 22/11) và có [Plan 004](specs/004-group-chat/plan.md) với topology direct P2P fanout TLS, một owner, revision monotonic, receipt theo từng member, metadata DPAPI persist/chat RAM-only, tối đa 5. **Gate 0 BLOCKED do T004/T010 cần review độc lập**; [tasks 004](specs/004-group-chat/tasks.md) **28/92 (64 mở)**, có **51 Group JUnit cases (23 bổ sung), full Java21 246/246 PASS**. Người dùng đã cho phép code prototype, nhưng **T004/T010 independent Gate 0 signoff vẫn mở**. Group Live LAN và acceptance chưa có. [Latest Group core code/test evidence](specs/004-group-chat/evidence/group-core-continued-2026-11-22.md) · [Implementation evidence](specs/004-group-chat/evidence/group-core-implementation-2026-11-22.md).

### Công việc chi tiết

- [ ] **04.01 — Chốt mô hình Group:** group ID, người tạo, thành viên, quyền, mô hình điều phối/phân phối.
- [ ] **04.02 — Luồng tạo/join/leave nhóm:** xác định đồng bộ membership giữa các peer.
- [ ] **04.03 — Gửi tin nhóm:** xác định danh sách nhận theo membership; tránh gửi nhầm người ngoài nhóm.
- [ ] **04.04 — Hiển thị nhóm:** tên nhóm, thành viên, tên người gửi ở mỗi tin, danh sách hội thoại.
- [ ] **04.05 — Xử lý bất đồng bộ:** thành viên offline, join muộn, rời nhóm, group host/peer điều phối rời mạng.
- [ ] **04.06 — Semantics tin nhóm:** duplicate, ordering, acknowledgment, consistency được định nghĩa rõ.
- [ ] **04.07 — Test ≥3 client:** broadcast, join/leave, kết nối gián đoạn và tên trùng.

### Kết quả bàn giao

- Người dùng có thể tạo hoặc tham gia nhóm theo quy tắc được đặc tả.
- Các tin gửi vào nhóm được phân phối tới đúng thành viên.
- Group hoạt động độc lập với hội thoại P2P đang mở.

### Tiêu chí nghiệm thu

- **AC-04-A:** ba client A/B/C trong cùng nhóm đều nhận được tin nhắn từ A theo chính sách phân phối.
- **AC-04-B:** người ngoài nhóm không tự động nhận nội dung nhóm.
- **AC-04-C:** thay đổi membership được phản ánh đúng; không làm hỏng hội thoại P2P.
- **AC-04-D:** ngắt một client không khiến các client còn lại treo hoặc bị lỗi dây chuyền.

---

## 7. Giai đoạn 05 — Chat History

**Ưu tiên:** P1
**Spec Kit feature:** `005-chat-history`
**Phụ thuộc:** mô hình message giai đoạn 03; tích hợp group với giai đoạn 04
**Phạm vi tính năng:** **History**, liên kết Sender/Time/State
**Mục tiêu:** xem lại lịch sử sau khi đóng/mở ứng dụng và đồng bộ lịch sử hội thoại được phép giữa các máy qua P2P khi kết nối lại.

**Đối chiếu source — CHƯA CÓ HISTORY BỀN VỮNG:** `LiveChatService` giữ `PeerSession.messages` trong RAM; `LiveChatAdapter.loadHistory` chỉ truy xuất transcript của phiên đang kết nối dù tên API là `loadHistory`. `MockChatService` có hội thoại/lịch sử **mô phỏng**. Source **chưa** có SQLite repository, migrations, delta sync, ACL/retention/erasure. Spec `005-chat-history` chưa có thư mục triển khai; muốn làm phải duyệt riêng quyền truy cập và chính sách dữ liệu trước.

### Công việc chi tiết

- [ ] **05.01 — Chọn storage:** file/DB nhúng, schema, migration, khóa hội thoại và vị trí lưu.
- [ ] **05.02 — Lưu message:** unique ID, conversation/group ID, sender ID, timestamp, loại nội dung, metadata.
- [ ] **05.03 — Load lịch sử:** phân trang/giới hạn để không tải toàn bộ dữ liệu lớn lên UI.
- [ ] **05.04 — UI lịch sử:** hiển thị theo hội thoại, thứ tự xác định rõ, phân biệt ngày và người gửi.
- [ ] **05.05 — Chính sách dữ liệu:** retention, xóa lịch sử, dữ liệu nhạy cảm, lỗi ghi và backup nếu cần.
- [ ] **05.06 — Chống dữ liệu trùng:** xử lý message ID trùng, replay/retry; tránh đổi sender/time gốc.
- [ ] **05.07 — Test persistence:** đóng/mở lại, corrupted storage, nhiều hội thoại và dữ liệu Unicode.
- [ ] **05.08 — Sync P2P:** trao đổi summary/delta, phát hiện event trùng/thiếu, tiếp tục đồng bộ khi online trở lại, giới hạn theo hội thoại được phép.
- [ ] **05.09 — Bảo vệ History:** đặc tả xác thực thiết bị, quyền tham gia, xóa/thu hồi quyền, giải quyết xung đột và kiểm thử peer không có quyền.

### Kết quả bàn giao

- Tin đã lưu xuất hiện lại sau khi khởi động ứng dụng.
- Lịch sử P2P và Group không lẫn vào nhau.
- Dữ liệu hiển thị đúng sender, timestamp và loại tin.

### Tiêu chí nghiệm thu

- **AC-05-A:** khởi động lại ứng dụng vẫn xem được lịch sử đã lưu của P2P.
- **AC-05-B:** sau khi Group tích hợp, tin nhóm xuất hiện đúng conversation/group.
- **AC-05-C:** tin lặp không làm xuất hiện bản sao ngoài chính sách đã định.
- **AC-05-D:** khi lỗi storage, app báo lỗi hợp lý và không sập/không che giấu mất dữ liệu.
- **AC-05-E:** hai peer được phép tham gia cùng hội thoại có thể đồng bộ những sự kiện còn thiếu sau khi một peer offline rồi kết nối lại; không tạo bản sao trùng sau retry.
- **AC-05-F:** peer không có quyền tham gia hội thoại không thể yêu cầu/nhận lịch sử, kể cả khi biết conversation ID hoặc nằm trong cùng LAN.

---

## 8. Giai đoạn 06 — File Transfer

**Ưu tiên:** P2
**Spec Kit feature:** `006-file-transfer`
**Phụ thuộc:** kết nối/giao thức giai đoạn 02–03; mục tiêu tích hợp Group ở giai đoạn 04
**Phạm vi tính năng:** **File**
**Mục tiêu:** truyền tệp giữa các peer có tiến độ, hủy truyền và kiểm soát an toàn.

**Đối chiếu source — CHƯA CÓ FILE LIVE:** `ComposerPanel.choose`, `MediaFiles.inspect` và `MessageContent.media` dùng cho **Demo**; `MainChatFrame` gọi `composer.setTextOnly(true)` ở Live, còn `LiveChatAdapter.sendMessage` từ chối mọi loại ngoài TEXT. Không thấy transport streaming chunk/ACK/checksum hoặc file receiver Live. Spec `006-file-transfer` chưa được khởi tạo; không tính UI demo là File Transfer đã hoàn thành.

### Công việc chi tiết

- [ ] **06.01 — Đặc tả file protocol:** transfer ID, file metadata, size, streaming/chunking, ACK, lỗi/hủy.
- [ ] **06.02 — Chính sách nhận file:** vị trí lưu, yêu cầu chấp thuận, kiểm tra đường dẫn/tên, overwrite.
- [ ] **06.03 — Gửi/nhận theo luồng:** giới hạn buffer, không nạp toàn bộ tệp vào bộ nhớ.
- [ ] **06.04 — UI tiến độ:** tiến trình byte đã gửi/nhận, completed/failed/cancelled.
- [ ] **06.05 — Integrity:** đối chiếu bytes/checksum và chỉ đánh dấu complete khi hợp lệ.
- [ ] **06.06 — Cleanup:** timeout, hủy giữa chừng, xóa/đánh dấu partial file theo chính sách.
- [ ] **06.07 — Kiểm thử an toàn:** file tên lạ, đường dẫn traversal, file rỗng, file lớn/quá giới hạn.
- [ ] **06.08 — Tích hợp Group:** nếu hỗ trợ tệp nhóm, xác định rõ cơ chế gửi tới từng thành viên và trường hợp nhận thất bại một phần.

### Kết quả bàn giao

- Người dùng gửi/nhận một tệp qua LAN với trạng thái minh bạch.
- Tệp hoàn tất trùng nội dung tệp gốc.
- Không cho phép dữ liệu mạng ghi ra ngoài thư mục đích an toàn.

### Tiêu chí nghiệm thu

- **AC-06-A:** A gửi file, B nhận được file cùng số byte và checksum (nếu quy định).
- **AC-06-B:** đang truyền có thể thấy tiến độ và hủy an toàn.
- **AC-06-C:** đứt mạng giữa chừng không bị đánh dấu là đã hoàn tất.
- **AC-06-D:** đường dẫn không hợp lệ, file quá giới hạn và tên tệp độc hại bị xử lý an toàn.

---

## 9. Giai đoạn 07 — Multimedia: Image, Video, Sound

**Ưu tiên:** P2
**Spec Kit feature:** `007-multimedia`
**Phụ thuộc:** giai đoạn 06
**Phạm vi tính năng:** **Image, Video, Sound**
**Mục tiêu:** hỗ trợ nội dung media dựa trên nền truyền tệp đã có.

**Đối chiếu source — CHƯA CÓ MEDIA LIVE:** `MessageMedia`, `VoiceRecorder`, `MediaFiles`, `DemoMedia` và các widget ảnh/video/file chỉ xử lý nội dung **Demo**; Live chặn các action Image/Video/Sound và không có network media packet. Không suy ra video/voice call từ khả năng chọn/phát tệp local. Spec `007-multimedia` chưa có thư mục triển khai; giai đoạn này phụ thuộc 006 streaming và kiểm tra file an toàn.

### Công việc chi tiết

- [ ] **07.01 — Media metadata:** MIME type, tên, kích thước, thumbnail/preview policy nếu cần.
- [ ] **07.02 — Image:** gửi/nhận ảnh, preview, kích thước UI hợp lý, xử lý ảnh lỗi.
- [ ] **07.03 — Video:** gửi/nhận file video, mở bằng trình phát phù hợp hoặc phát in-app nếu có giải pháp được chọn.
- [ ] **07.04 — Sound:** gửi/nhận file audio, phát/dừng bằng player phù hợp nếu được hỗ trợ.
- [ ] **07.05 — Giới hạn và fallback:** codec không hỗ trợ, dung lượng vượt hạn mức, thiếu player và dữ liệu không hợp lệ.
- [ ] **07.06 — UI hội thoại:** hiển thị item media với sender, time, transfer state và thao tác mở/lưu.
- [ ] **07.07 — Test:** đa dạng loại file, media lỗi, tên Unicode, file lớn, hủy và kiểm tra byte integrity.

### Kết quả bàn giao

- Người dùng gửi/nhận ảnh, video, âm thanh trong chat.
- Media được preview/phát khi nền tảng hỗ trợ; ngược lại có chỉ dẫn mở file an toàn.
- Không tự tuyên bố hỗ trợ gọi thoại/video thời gian thực.

### Tiêu chí nghiệm thu

- **AC-07-A:** gửi/nhận ảnh, video, audio theo tập định dạng hỗ trợ đã công bố.
- **AC-07-B:** file nhận không bị thay đổi nội dung; preview/playback không làm treo UI.
- **AC-07-C:** codec/định dạng không hỗ trợ được báo rõ, không crash.
- **AC-07-D:** mọi media item hiển thị đúng sender, time và trạng thái truyền.

---

## 10. Giai đoạn 08 — Tích hợp, kiểm thử và đóng gói

**Ưu tiên:** P0 (bắt buộc cho phát hành)
**Spec Kit feature:** `008-integration-release`
**Phụ thuộc:** các giai đoạn thuộc phạm vi bản phát hành; M1/M2 Gate 1 và security approval chưa xong
**Mục tiêu:** xác nhận ứng dụng hoạt động ổn định trên LAN thực tế, hoàn thiện trải nghiệm và phát hành có thể tái lập.

**Đối chiếu source — CÓ NỀN TẢNG BUILD/REGRESSION, CHƯA CÓ RELEASE GATE:** `pom.xml` cấu hình `ui.IntraChat` cho manifest/shade/exec và build shaded JAR; `scripts/verify-m0.ps1`, `verify-m1.ps1`, `verify-m2.ps1` có quy trình local. Lần chạy `verify-m2.ps1 -Offline` ngày 22/11/2026 trên Temurin21: **246/246 JUnit, clean package/JAR diagnostics PASS**. Chưa có two-PC LAN, full stress/security/UX audit, installer/release signoff. Spec `008-integration-release` chưa khởi tạo; giai đoạn phát hành **không Verified**.

### Công việc chi tiết

- [ ] **08.01 — Full regression:** chạy lại test P2P, Group, History, File và Media.
- [ ] **08.02 — LAN thực tế:** kiểm thử 2–3 máy, các địa chỉ IP khác nhau cùng LAN, policy firewall và reconnect.
- [ ] **08.03 — Stress/soak:** gửi nhiều tin, nhiều transfer, file lớn và chạy kéo dài theo budget đã thống nhất.
- [ ] **08.04 — Failure injection:** peer tắt đột ngột, packet/frame không hợp lệ, lỗi lưu, lỗi quyền thư mục, mất kết nối khi tải.
- [ ] **08.05 — Review bảo mật:** path traversal, unsafe deserialization, thiếu size limit, lộ nội dung chat trong log, ghi đè file.
- [ ] **08.06 — Review UX:** hiển thị Sender/Time/State, lỗi dễ hiểu, không chặn UI, đóng ứng dụng sạch.
- [ ] **08.07 — Đóng gói:** chọn JAR/installer phù hợp, dependency/runtime, cách chạy, yêu cầu máy đích.
- [ ] **08.08 — Tài liệu:** README cập nhật, hướng dẫn cài đặt, cấu hình LAN/firewall, screenshots (khi có), known limitations.
- [ ] **08.09 — Gắn phiên bản và phát hành:** changelog, release notes, kết quả test xác minh, tag Git khi được duyệt.

### Kết quả bàn giao

- Gói chạy ứng dụng trên máy đích theo hướng dẫn.
- Test report gồm test pass/fail, môi trường, lỗi chưa giải quyết và giới hạn đã biết.
- Tài liệu vận hành và release notes phản ánh đúng chức năng thực tế.

### Tiêu chí nghiệm thu

- **AC-08-A:** bản build được đóng gói và chạy được trong môi trường LAN mục tiêu.
- **AC-08-B:** tất cả các acceptance criteria của chức năng nằm trong phạm vi phát hành được xác nhận.
- **AC-08-C:** không còn lỗi nghiêm trọng chưa có quyết định xử lý; các giới hạn được công bố.
- **AC-08-D:** README có lệnh build, test, run, setup LAN và xử lý sự cố đã được kiểm chứng.

---

## 11. Cách tổ chức thư mục đặc tả theo GitHub Spec Kit

Thư mục dưới đây là **cấu trúc mục tiêu** theo Spec Kit, không phải toàn bộ tệp đã tạo. Hiện source có `001-project-foundation`, `001-desktop-frontend`, `002-lan-networking`, `003-p2p-messaging`; **đã có** `004-group-chat/spec.md` v0.2.0 Clarified, `plan.md` (design only) và `tasks.md` **28/92** gồm code/test lõi Group đã implement; **chưa có** kết nối Group Live TCP/UI hoặc spec 005–008. Các thư mục dưới đây không được coi là đã thực hiện chỉ vì xuất hiện trong lộ trình.

```text
IntraChat/
├── README.md
├── AGENTS.md
├── ROADMAP.md
├── .specify/
│   └── memory/
│       └── constitution.md
└── specs/
    ├── 001-project-foundation/
    │   ├── spec.md
    │   ├── plan.md
    │   └── tasks.md
    ├── 002-lan-networking/
    ├── 003-p2p-messaging/
    ├── 004-group-chat/
    ├── 005-chat-history/
    ├── 006-file-transfer/
    ├── 007-multimedia/
    └── 008-integration-release/
```

Mỗi feature folder hướng tới ba tài liệu cơ bản:

1. **`spec.md` (WHAT/WHY):** mục tiêu, phạm vi, user stories, yêu cầu chức năng, edge cases, acceptance criteria; không khóa cứng framework khi không cần.
2. **`plan.md` (HOW):** công nghệ, topology, kiến trúc, data model, contract, chiến lược test, Constitution Check, trade-off.
3. **`tasks.md` (EXECUTION):** các đầu việc nhỏ, thứ tự phụ thuộc, vị trí thay đổi, bài test, trạng thái.

Tùy tính năng, có thể có `research.md`, `data-model.md`, `contracts/`, `quickstart.md` hoặc checklist được sinh theo workflow Spec Kit thực tế.

### 11.1. Quy trình cho từng giai đoạn

1. Đọc `.specify/memory/constitution.md` và `AGENTS.md`.
2. Xây dựng `spec.md` từ phạm vi, tác nhân, luồng thành công/thất bại và tiêu chí nghiệm thu trong lộ trình.
3. Làm rõ các câu hỏi TBD; tránh bỏ mặc quyết định ảnh hưởng kiến trúc.
4. Lập `plan.md`, kiểm tra tuân thủ 7 nguyên tắc Constitution, xác định rủi ro.
5. Tạo `tasks.md` với test và định nghĩa hoàn thành cho từng task.
6. Thực hiện theo tasks, ghi lại kết quả build/test thực tế.
7. Review acceptance criteria, cập nhật tài liệu và chỉ sau đó chuyển giai đoạn.

> **Lưu ý:** repository có thể chứa `constitution.md` và `AGENTS.md` nhưng chưa chắc đã cài CLI/integration của GitHub Spec Kit. Những lệnh kiểu `/speckit-specify`, `/speckit-plan` và `/speckit-tasks` chỉ hoạt động khi agent tích hợp đã được khởi tạo đúng cách. Không chạy lệnh init ghi đè mà không kiểm tra/sao lưu tài liệu hiện có.

### 11.2. Mẫu thông tin bắt buộc trong spec

- **Actors:** người gửi, người nhận, thành viên nhóm, peer mất kết nối.
- **Preconditions:** cùng LAN, peer reachable, quyền nhận tệp hoặc nhóm hợp lệ.
- **Happy paths:** gửi/nhận P2P, nhắn nhóm, mở lịch sử, truyền tệp, xem media.
- **Failure paths:** peer offline, trùng/thiếu metadata, timeout, hủy truyền, lỗi lưu, file độc hại.
- **Acceptance tests:** đánh số để có thể trace đến unit/integration test.
- **Non-functional:** độ phản hồi UI, giới hạn file, timeout, an toàn path, memory usage và bảo mật.

---

## 12. Ma trận kiểm thử xuyên suốt

| Hạng mục | Test cần có | Giai đoạn |
| --- | --- | --- |
| LAN | Hai Windows peer P2P trực tiếp; manual IP/port fallback; handshake/version; payload chẩn đoán 2 chiều; fragmentation/coalescing; timeout; disconnect; malformed/oversized frame; cleanup; cảnh báo peer chưa xác thực | 2, 8 |
| P2P | Gửi/nhận hai chiều; đúng conversation; Unicode; message ID trùng | 3, 8 |
| Sender | Trùng display name; sender ID không đổi; không gán nhầm người | 3–5, 8 |
| Time | Hiển thị thời gian; sai múi giờ; đồng hồ peer lệch; reload history | 3, 5, 8 |
| State | Online/offline; chuyển trạng thái khi disconnect; ACK nếu có | 2–3, 8 |
| Emoji | Unicode đa byte; tin tiếng Việt + emoji; lưu đọc lại | 3, 5 |
| Group | ≥3 client; join/leave; thành viên ngoài nhóm; member offline | 4, 8 |
| History | Restart; phân tách conversation; message trùng; lỗi storage; P2P sync sau offline; denied unauthorized peer; concurrent events, tombstone/revocation | 5, 8 |
| File | File rỗng/lớn; checksum; hủy; disconnect; traversal; overwrite | 6, 8 |
| Image | Nhận/xem ảnh; định dạng lỗi; file lớn; sender/time | 7, 8 |
| Video | Nhận/mở; unsupported codec; hủy transfer; không block UI | 7, 8 |
| Sound | Nhận/phát; unsupported codec; lỗi playback; không block UI | 7, 8 |

**Tối thiểu cho thay đổi mạng/file:** một test luồng thành công, một test đầu vào sai, một test mất kết nối/timeout và một test dọn tài nguyên.

## 13. Rủi ro chính và biện pháp

| Rủi ro | Tác động | Hướng xử lý |
| --- | --- | --- |
| Đã chọn direct P2P nhưng discovery/port/firewall cụ thể chưa chốt | Hai Windows peer có thể không kết nối LAN thật | Plan 002 phải chốt listener/connect, cổng, Windows Private profile, manual IP fallback và test hai máy |
| Framing và schema thông điệp thiếu nhất quán | Sai sender/time, mất hoặc trùng message | Contract versioned, parser test, message ID |
| UI xử lý I/O đồng bộ | Đơ khi mạng yếu hoặc truyền file | Background worker, state event, timeout |
| Group không thống nhất thành viên | Gửi sai người hoặc thiếu người | Membership contract, multi-client tests |
| History trộn hội thoại hoặc mất dữ liệu | Sai thông tin, giảm độ tin cậy | Key theo conversation/group ID; persistence tests |
| File path traversal/ghi đè | Mất dữ liệu hoặc vi phạm an toàn | Validate và chuẩn hóa đường dẫn; giới hạn quyền ghi |
| Media quá lớn/codec không hỗ trợ | Tăng RAM, treo UI hoặc lỗi phát | Streaming, giới hạn, player fallback |
| LAN chưa xác thực/mã hóa nhưng nhầm peer ID tự khai là tin cậy | Dễ bị giả mạo và có thể rò rỉ History về sau | M1 nói rõ transport session không đồng nghĩa authenticated; không truyền History/File; chốt authentication + ACL trước sync 005 |
| Không có test trên LAN thực | Bản demo chỉ chạy loopback | Test tối thiểu trên hai máy và ghi bằng chứng |

## 14. Quy tắc theo dõi tiến độ

Trạng thái thống nhất cho từng task: **Not Started → In Progress → In Review → Verified**; thêm **Blocked** khi có phụ thuộc hoặc quyết định chưa chốt. Mỗi trạng thái ở bảng dưới dựa vào **source, checklist và evidence**; số JUnit không phải % hoàn tất task.

| Giai đoạn | Trạng thái ban đầu | Mốc |
| --- | --- | --- |
| 01 — Project foundation | **Verified — 41/41**; Temurin JDK21, 9 JUnit baseline + 5 GUI network-denied cycles | M0 |
| 02 — LAN networking | **In Progress — 53/60 (7 mở)**; [local code/test evidence](specs/002-lan-networking/evidence/m1-local-completion-2026-11-22.md), Java21 172/172 full JUnit, M1 technical diagnostics dialog inside Messenger, 2 JVM/1 PC and real Swing consent/ping tests PASS; **2 physical PC NOT RUN, Gate 1 OPEN** | M1 |
| 03 — P2P MVP | **In Progress — 65/90 (25 mở)**; [latest M2 coding and RED→GREEN evidence](specs/003-p2p-messaging/evidence/m2-continued-code-2026-11-22.md), **246/246 Java21 JUnit PASS**; shared v1/v2 same-port TCP, verified TLS two-JVM protected Vietnamese/emoji+ACK, explicit UNCERTAIN retry/READ UI, bounded RAM. **Full stress, reviewer, 2 physical PCs, M1 Gate1 chưa đạt** — [tasks](specs/003-p2p-messaging/tasks.md) | M2 |
| 04 — Group chat | **Core prototype In Progress — 28/92 (64 mở)** — [new Group security/DPAPI/receipt evidence](specs/004-group-chat/evidence/group-core-continued-2026-11-22.md) — [Spec](specs/004-group-chat/spec.md) · [Plan](specs/004-group-chat/plan.md) · [Tasks](specs/004-group-chat/tasks.md) · [Java21 evidence](specs/004-group-chat/evidence/group-core-implementation-2026-11-22.md). **51 Group JUnit cases (23 bổ sung); full 246/246 PASS**, nhưng **chưa Group Live networking/GUI**. Independent Gate 0 và Gate 1 chưa đạt | M3 |
| 05 — Chat history | **Not Started cho persistence/sync**; Live transcript RAM phiên, chưa SQLite/delta-sync/ACL | M3 |
| 06 — File transfer | **Not Started cho Live LAN**; FE Demo chọn/lưu file, Live từ chối media | M4 |
| 07 — Multimedia | **Not Started cho Live LAN**; Image/Video/Sound chỉ có Demo | M4 |
| 08 — Integration & release | **Nền tảng local đã có** (shaded JAR, CLI, 113-test regression); **full release/acceptance chưa thực hiện** | M5 |

**Định nghĩa hoàn thành (Definition of Done) cho từng giai đoạn:**

- [ ] Acceptance criteria đã được xác minh bằng thao tác/test cụ thể.
- [ ] Build/test chạy thật và kết quả được lưu lại; không ghi `pass` nếu chưa chạy.
- [ ] Các lỗi mạng, dữ liệu không hợp lệ và cleanup phù hợp đã được xử lý.
- [ ] Không có vi phạm `MUST` của Constitution chưa được giải quyết.
- [ ] `spec.md`, `plan.md`, `tasks.md` và README được cập nhật theo phần thực sự triển khai.
- [ ] Quyết định còn mở, rủi ro và giới hạn được ghi nhận trước khi chuyển bước.

## 15. Bắt đầu từ đâu? — ưu tiên theo source và blocker

**Kế hoạch từ ngày 22/11/2026 (không tự coi là task đã xong):**

1. **P0 — Bảo toàn working tree:** hiện source có thay đổi chưa commit, đặc biệt di chuyển UI; tránh reset/rebase/ghi đè. Duy trì launcher `ui.IntraChat` và backend M1 diagnostics/v2 Live riêng, không khôi phục UI cũ.
2. **P0 — Khép các phần có thể làm trên máy hiện tại:** theo [tasks M1](specs/002-lan-networking/tasks.md) và [tasks M2](specs/003-p2p-messaging/tasks.md), bổ sung RED→GREEN/evidence cho shared v1/v2 version dispatch, retry/UNCERTAIN, FAILED bubble, peer identity negative tests, resource/session caps, timeout/cleanup, keyboard/focus và GUI 5-cycle. Chỉ check task khi thỏa đúng DoD.
3. **P0 — Giải blocker bắt buộc:** chuẩn bị **hai Windows 11 vật lý** trên cùng LAN Private không cần Internet, riêng key/trust profiles và người dùng đồng ý hai phía; test M1 diagnostic và M2 Accept/SAS/TLS/text/ACK/READ/từ chối/mất kết nối/restart. Ghi lại bằng chứng ở AC-02 và AC-03 (T080–T084) cho **từng máy**, không thay bằng hai JVM localhost.
4. **P0 — Security/supply-chain approval:** reviewer độc lập kiểm tra dependency/SBOM/license/provenance, X.509/TLS pin/key change, DPAPI khác tài khoản, forged ACK/READ/replay/DoS, packet/log privacy; lưu quyết định và xử lý các findings trước Gate 1 (T003/T083/T087/T090).
5. **P0 — Chạy lại gate và cập nhật acceptance:** dùng actual Java21 + `scripts/verify-m2.ps1 -Offline`, điều chỉnh checklist M1/M2 theo kết quả; **không** đánh dấu M1 hoặc M2 Verified khi còn blocked.
6. **P1 — Sau khi M2 ổn định:** bổ sung **independent review T004/T010** để gỡ **Gate 0 của [Plan Group 004](specs/004-group-chat/plan.md)** (CL-04-01…06 đã confirmed); sau đó tiếp tục [64 task còn mở](specs/004-group-chat/tasks.md) tập trung vào shared v1/v2/v3 dispatcher, verified Group TLS transport, owner invitation, fanout và UI, sau đó đặc tả History + ACL/retention/sync (005), File streaming (006), Media (007), rồi mới mở nghiệm thu tích hợp/phát hành (008). Demo hiện có không được tính thay Live implementation.

**Điều chỉnh lộ trình:** Có thể thay ưu tiên Group/History/File theo yêu cầu bài học, nhưng không bỏ qua các ranh giới trust, contract version, kiểm chứng đầu vào không tin cậy và test ở mức phù hợp. [Ma trận tính năng](docs/feature-matrix.md) · [Kiến trúc](docs/architecture.md) · [Spec 003 blocker](specs/003-p2p-messaging/evidence/execution-blockers-2026-11-22.md).

---

*Cập nhật **22/11/2026** theo code/test hiện tại: **M0 41/41 Verified; M1 53/60 In Progress; M2 65/90 In Progress; Group 004 28/92 prototype**. `ui.IntraChat` vẫn là launcher duy nhất; listener chung v1 M1 diagnostics/v2 M2 TLS đã hoạt động và không chấp nhận Group v3. Trên Temurin21, **verify-m2.ps1 -Offline 246/246 PASS, exit 0**, shaded JAR/launcher diagnostics PASS; baseline M1 isolated source copy và deliberately failing test PASS trước đó. **Chưa có 2 Windows PC vật lý, đầy đủ TLS stress, security reviewer, M1/M2 Gate1 signoff** — [M1 evidence](specs/002-lan-networking/evidence/m1-local-completion-2026-11-22.md), [M2 evidence](specs/003-p2p-messaging/evidence/m2-local-code-2026-11-22.md).*