# IntraChat Desktop — Một Frontend Messenger thống nhất

> **Định hướng đồ án môn học, cập nhật 22/11/2026:** ưu tiên **11 chức năng hoạt động thật trong giao diện Messenger** (P2P, Group, File, Emoji, History, Image, Video, Sound, State, Time, Sender). Phần kiểm toán mật mã/Gate chuyên nghiệp trong tài liệu cũ **không còn là điều kiện ưu tiên để bắt đầu code**, nhưng các lớp TLS/DPAPI đã hoạt động và an toàn file cơ bản được giữ lại. [ROADMAP đồ án](ROADMAP.md) · [Phạm vi và tiêu chí demo](docs/course-project-scope.md) · [Kế hoạch chuyển mọi chức năng sang Messenger](docs/messenger-integration-plan.md) · [Bản roadmap kỹ thuật lịch sử](ROADMAP-LEGACY-SECURITY-2026-11-22.md).

IntraChat chỉ có **một giao diện ứng dụng chính**: Messenger Java Swing/FlatLaf tại `ui.IntraChat`. Giao diện TCP chẩn đoán M1 và các launcher/cửa sổ Live độc lập đã được thay thế. Các dịch vụ mạng, giao thức và bảo mật vẫn được giữ dưới tầng Backend.

- **Demo:** Đăng nhập/đăng ký mô phỏng, chat, nhóm và media với dữ liệu trong RAM; không khởi động kết nối mạng.
- **Chat LAN:** Từ màn hình đăng nhập chọn **Chat trong LAN (TLS 1.3)**; nhập IP/port, chấp nhận kết nối và xác minh fingerprint ngay trong hộp thoại thuộc Frontend Messenger; sau xác minh, tin văn bản/emoji được gửi bằng `MainChatFrame`. Không dùng mật khẩu demo để xác thực thiết bị.
- **Trạng thái:** M0 **41/41 Verified**; M1 **53/60**, M2 **65/90**, Group core **28/92** theo tracker kỹ thuật. Java21 full regression gần nhất **246/246 PASS**. P2P/Emoji/State/Time/Sender đã có Live cục bộ; **Group/File/History bền vững/Image/Video/Sound chưa chạy Live**. Tiêu chí bàn giao môn học là **11/11 chức năng trong Messenger**, không phụ thuộc chứng nhận bảo mật doanh nghiệp.

`java -jar` chạy Frontend Messenger; `--diagnostics` chỉ in trạng thái nền tảng không mở giao diện. Không còn lệnh khởi chạy M1 diagnostic UI hoặc M2 Live UI riêng.

## Chạy thử

**Yêu cầu build đã kiểm chứng:** Windows 11 x64, **Temurin JDK 21**, Maven Wrapper 3.9.16. Dependency chỉ cần tải lần đầu khi chưa được cache.

```powershell
.\mvnw.cmd clean package
# Khởi chạy Frontend Messenger duy nhất (Demo hoặc chọn Chat LAN):
java -jar target/intrachat-0.1.0-SNAPSHOT.jar
# Chẩn đoán CLI không mở giao diện:
java -jar target/intrachat-0.1.0-SNAPSHOT.jar --diagnostics
# Local Java 21 regression (M0/M1/M2/FE), không phải two-PC acceptance:
powershell -NoProfile -ExecutionPolicy Bypass -File scripts/verify-m2.ps1 -Offline
```

Với **FE demo**, nhập username bất kỳ (tối đa 32 ký tự) và mật khẩu demo không trống; chọn **Đăng ký** thì dùng tối thiểu 4 ký tự. **Không dùng mật khẩu thật.** FE chỉ là dữ liệu mô phỏng, không phải xác thực thực tế. Luồng Chat LAN yêu cầu Accept và so khớp fingerprint trên cả hai phía trước khi gửi tin thực.

## 1. Mục tiêu

- Hỗ trợ trao đổi thông tin giữa các máy trong cùng mạng LAN mà không bắt buộc có dịch vụ Internet.
- Cung cấp giao diện dễ sử dụng để trao đổi văn bản, emoji và tệp đa phương tiện.
- Hiển thị rõ người gửi, thời gian và trạng thái liên quan đến người dùng/tin nhắn.
- Ưu tiên tính ổn định khi nhiều máy kết nối, ngắt kết nối hoặc truyền tệp.
- Bảo vệ tệp và dữ liệu trò chuyện ở mức phù hợp với môi trường LAN.

**Ngoài phạm vi mặc định:** tài khoản đám mây, đồng bộ qua Internet, triển khai máy chủ công cộng và cuộc gọi video/âm thanh thời gian thực. Chỉ bổ sung khi có đặc tả tính năng riêng.

## 2. Danh sách tính năng

| Nhóm | Chức năng | Hành vi mục tiêu |
| --- | --- | --- |
| Giao tiếp | **P2P** | Nhắn tin trực tiếp giữa hai người dùng/thiết bị trong LAN. |
| Giao tiếp | **Group** | Tạo hoặc tham gia nhóm và trao đổi tin nhắn với nhiều thành viên. |
| Chia sẻ | **File** | Gửi, nhận, theo dõi tiến trình và lưu tệp an toàn. |
| Tin nhắn | **Emoji** | Soạn, gửi và hiển thị emoji Unicode. |
| Dữ liệu | **History** | Lưu và xem lịch sử cục bộ, hướng tới đồng bộ các hội thoại được phép giữa nhiều máy qua P2P khi kết nối lại. |
| Đa phương tiện | **Image** | Gửi/nhận và xem hình ảnh trong cuộc trò chuyện. |
| Đa phương tiện | **Video** | Gửi/nhận và mở nội dung video; **không mặc định** là gọi video. |
| Đa phương tiện | **Sound** | Gửi/nhận và phát nội dung âm thanh; **không mặc định** là gọi thoại. |
| Trạng thái | **State** | Biểu diễn trạng thái hiện diện/kết nối và, nếu đặc tả yêu cầu, trạng thái tin nhắn. |
| Thông tin | **Time** | Gắn và hiển thị mốc thời gian của tin nhắn/sự kiện theo quy ước rõ ràng. |
| Thông tin | **Sender** | Hiển thị đúng định danh/tên người gửi cho mỗi tin nhắn. |

**Quyết định hiện hành:** giữ P2P IPv4/port, consent/TLS 1.3/SAS và ACK/READ **đã triển khai**, nhưng **không xây thêm vòng kiểm toán bảo mật không cần thiết trước khi hoàn thành các tính năng còn thiếu**. Ưu tiên: Group Live qua Messenger, FileTransfer dùng chung, History local sau restart, Image/Video/Sound qua FileTransfer; giữ giới hạn size/path, checksum và không khóa UI. [Ma trận](docs/feature-matrix.md) · [Kế hoạch đồ án](ROADMAP.md) · [Spec Group](specs/004-group-chat/spec.md) · [005](specs/005-chat-history/spec.md) · [006](specs/006-file-transfer/spec.md) · [007](specs/007-multimedia/spec.md).

## 3. Công nghệ và kiến trúc

| Thành phần | Quyết định hiện tại |
| --- | --- |
| Ngôn ngữ | **Java** |
| Môi trường | **Windows** (bản đầu tiên), các máy trong cùng LAN |
| Kiểu truyền thông | **TCP P2P IPv4 thủ công**; M1 diagnostics v1 plaintext riêng; M2 secure CHAT_V2 qua TLS1.3 với fingerprint pairing, chưa đầy đủ nghiệm thu |
| Giao diện | **Swing + FlatLaf** — một Frontend Messenger duy nhất, có chế độ Demo và Live LAN |
| Build / dependency | **Maven Wrapper 3.9.16, Java21, JUnit5.14.4, Jackson2.22.4, FlatLaf3.6.2**, thêm Bouncy Castle1.87 (cert) và JNA5.19.1 (DPAPI/Windows) trong M2 |
| Lưu lịch sử | Dự kiến **SQLite tại mỗi peer + History sync P2P** ở giai đoạn 05; chưa triển khai |
| Định dạng thông điệp | **M1 v1 strict JSON chẩn đoán**, còn **M2 v2 strict framed JSON bên trong TLS1.3**; chỉ text/ACK/read, không Group/File/History; chưa audit security |

### Kiến trúc đang triển khai

```text
ui.IntraChat (launcher duy nhất)
  ├─ Demo: LoginFrame → MockChatService → MainChatFrame
  └─ Live: LoginFrame → LanConnectionDialog (Accept + SAS)
                             ↓
          MainChatFrame ↔ LiveChatAdapter ↔ LiveChatService (RAM)
                             ↓
              SecureChatCoordinator → TlsChatSession (TLS 1.3)
                             ↓
                 CHAT_V2 text / ACK / READ
```

M1 còn `TcpListener/TcpConnector/TcpPeerSession` và `JsonEnvelopeCodec` v1 dùng cho **diagnostics plaintext**, **không** cho chat; UI kỹ thuật M1 đã được loại khỏi bản build. Hai chế độ dùng cùng giao diện Messenger nhưng **không** dùng chung nguồn dữ liệu. Chi tiết tại [kiến trúc hiện tại](docs/architecture.md) và [báo cáo migration](docs/ui-migration-2026-11-21.md).

### Hợp đồng thông điệp hiện có và cần mở rộng

M1 v1 là framed JSON chỉ dành cho handshake/diagnostics. M2 `CHAT_V2` dùng framed JSON trong TLS 1.3 sau consent/ghép cặp; có identity/conversation/message ID, metadata thời gian UTC, text Unicode, ACK và read receipt. Các loại Group/File/History/Media **chưa** được bật cho Live. Khi mở rộng, các đặc tả tương ứng phải xác định:

- Phân biệt `senderId` ổn định với `displayName` có thể đổi.
- Quy ước timestamp và múi giờ (ưu tiên lưu UTC, hiển thị giờ địa phương).
- Giới hạn kích thước bản tin/tệp và cách truyền theo luồng.
- Thứ tự, xác nhận nhận, phát hiện tin trùng, lỗi và mất kết nối.
- Trạng thái hiện diện so với trạng thái gửi/nhận/đọc của tin nhắn.
- Cách phân biệt thông điệp nhắn tin và dữ liệu nhị phân.

Không sử dụng Java native serialization từ dữ liệu mạng không đáng tin cậy.

## 4. Hướng dẫn bắt đầu

### Yêu cầu hiện tại

- Windows 11 x64, giao diện desktop; Git và PowerShell.
- **JDK 21 LTS (Eclipse Temurin)**. `pom.xml` biên dịch với `--release 21`. M0 và M1 automated tests đã thực thi trên **actual Temurin JDK 21.0.13.1** tại Windows 11; Oracle JDK 25 cũng có trên máy nhưng không được dùng làm bằng chứng Java21 runtime.
- Maven Wrapper 3.9.16 được cung cấp bởi `mvnw.cmd` và sẽ tải Maven khi cần. Lần chạy đầu để tải Maven/plugins/dependencies cần Internet (hoặc cache/mirror đã chuẩn bị); **ứng dụng chạy không cần Internet**.
- M0 chỉ là nền tảng lịch sử; M1/M2 đã có TCP/TLS và loopback trên một PC. **Nghiệm thu LAN thật vẫn cần hai PC Windows riêng biệt, không có Internet công cộng.**

### Build, chạy và kiểm thử trên Windows 11 + Temurin JDK21

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File scripts/check-environment.ps1
.\mvnw.cmd -version
.\mvnw.cmd clean package
.\mvnw.cmd test
java -jar target/intrachat-0.1.0-SNAPSHOT.jar
# Chẩn đoán không cần màn hình đồ họa:
java -jar target/intrachat-0.1.0-SNAPSHOT.jar --diagnostics
# Kiểm chứng tự động build + test + diagnostics:
powershell -NoProfile -ExecutionPolicy Bypass -File scripts/verify-m0.ps1
```

Sau khi cache dependencies, `.\\mvnw.cmd -o test` có thể chạy offline. **Bằng chứng lịch sử:** M0 Verified 41/41 tasks; M1 từng có baseline 45/45; 21/11/2026 có kết quả 113/113 JUnit và GUI migration. **Kết quả thực thi mới nhất 22/11/2026:** `scripts/verify-m2.ps1 -Offline` trên Java21 **246/246 JUnit PASS, 0 failures/errors/skips**, JAR/launcher PASS; gồm cả Group core, **không** chứng minh 11 tính năng Live. Xem [M0 evidence](specs/001-project-foundation/evidence/m0-acceptance-report.md), [M1 evidence](specs/002-lan-networking/evidence/m1-acceptance-report.md), [M2 evidence](specs/003-p2p-messaging/evidence/m2-acceptance-report.md) và [UI migration](docs/ui-migration-2026-11-21.md).

### Tổ chức dự án

Cấu trúc đang dùng (rút gọn):

```text
IntraChat/
├── pom.xml, mvnw.cmd, .mvn/wrapper/
├── src/main/java/ui/               # launcher, Messenger, dialog LAN
├── src/main/java/listener/         # FE ports + LiveChatAdapter
├── src/main/java/model/            # FE models + MockChatService
├── src/main/java/vn/intrachat/     # application/domain/protocol/security/transport
├── src/test/java/                  # unit/integration/UI tests
├── docs/                           # kiến trúc, ma trận tính năng, LAN, migration
├── scripts/                        # verify-m0, verify-m1, verify-m2
├── specs/001-project-foundation/, 001-desktop-frontend/, 002-lan-networking/
├── specs/003-p2p-messaging/, 004-group-chat/, 005-chat-history/
├── specs/006-file-transfer/, 007-multimedia/
└── README.md, ROADMAP.md, AGENTS.md, .specify/memory/constitution.md
```

**Ranh giới hiện tại (22/11/2026):** **M0 41/41 Verified; M1 53/60, M2 65/90, Group core 28/92 In Progress**. Chỉ có launcher `ui.IntraChat`. Live P2P text/emoji/ACK/READ/Sender/Time/State đã chạy cục bộ, còn Group/File/Media qua LAN và History bền vững **chưa có Live**. M1 diagnostics có nút phụ trong `LanConnectionDialog`, không có cửa sổ chat kỹ thuật độc lập. **Hai máy LAN vật lý chưa kiểm thử**; tiêu chí đồ án dùng [feature matrix](docs/feature-matrix.md), không biến dữ liệu Demo thành tiến độ Live.

## 5. Quy trình phát triển theo GitHub Spec Kit

Dự án áp dụng **Spec-Driven Development**: thống nhất nguyên tắc → đặc tả hành vi → kế hoạch kỹ thuật → tác vụ → triển khai/kiểm thử.

1. Đọc [Constitution](.specify/memory/constitution.md) và [hướng dẫn cho agents](AGENTS.md).
2. Viết đặc tả cho **một tính năng có phạm vi rõ** (user stories, acceptance criteria, edge cases).
3. Làm rõ các điểm chưa quyết định như discovery, giao thức, persistence và UI.
4. Lập kế hoạch triển khai, thiết kế test và liệt kê tasks.
5. Triển khai theo tasks, chạy kiểm thử và cập nhật README khi hành vi thực tế thay đổi.

**Để cài đầy đủ công cụ Spec Kit (tùy chọn, chưa thực hiện):** từ thư mục repository, cài Specify CLI rồi khởi tạo integration phù hợp. Ví dụ với GitHub Copilot trên Windows:

```powershell
uv tool install specify-cli
specify init --here --force --integration copilot --script ps
```

> `--force` có thể ghi đè các đường dẫn do Spec Kit quản lý. **Commit/sao lưu ba tài liệu hiện có và kiểm tra Git diff trước/sau khi chạy.** Ba tài liệu trong repository **không đồng nghĩa** với việc Spec Kit CLI, templates hay slash commands đã được cài đặt.

Sau khi khởi tạo và mở agent hỗ trợ Spec Kit, workflow thường có các bước `/speckit-constitution`, `/speckit-specify`, `/speckit-plan`, `/speckit-tasks`, `/speckit-implement` (cú pháp thực tế tùy agent/integration).

Tham khảo: [GitHub Spec Kit](https://github.com/github/spec-kit) · [Hướng dẫn khởi tạo dự án hiện có](https://github.github.com/spec-kit/how-to/existing-projects.html).

## 6. Lộ trình đề xuất

- **Mốc 1 — Nền tảng (M0):** chốt JDK, UI/build, thiết kế protocol và identity; build, khởi chạy và test skeleton. Kiểm thử hai peer thuộc giai đoạn kết nối LAN tiếp theo.
- **Mốc 2 — Chat:** [Giai đoạn 03 M2](specs/003-p2p-messaging/spec.md) **IN PROGRESS** — đã có nền danh tính/ghép cặp fingerprint, TLS1.3, CHAT_V2, ACK và đọc từ foreground Live FE qua LAN loopback. [Task tracker](specs/003-p2p-messaging/tasks.md) và [M2 partial acceptance](specs/003-p2p-messaging/evidence/m2-acceptance-report.md). **Không phải bản đã bảo mật/Verified**; M1 chưa Gate 1 và two-PC LAN chưa thử.
- **Mốc 3 — Group Live trong Messenger:** nối `CreateGroupDialog` và timeline với Group fanout trên P2P session hiện có, test 3 JVM.
- **Mốc 4 — FileTransfer + History:** truyền file chunk/checksum/progress và lưu History local sau restart (có thể code song song).
- **Mốc 5 — Image/Video/Sound:** tái sử dụng FileTransfer để hiển thị media bubble, xem/phát tệp, gửi WAV.
- **Mốc 6 — Demo đủ 11 chức năng:** thử trực tiếp 2–3 peer qua Messenger, xử lý hủy/ngắt, kiểm tra Sender/Time/State và tài liệu hướng dẫn giảng viên. Audit bảo mật độc lập là tùy chọn, không chặn code.

Thứ tự có thể thay đổi sau khi viết spec; đây không phải tiến độ đã hoàn thành.

## 7. Chế độ Demo — chỉ mô phỏng

- **Mai Anh:** người dùng online, lịch sử có ảnh và biên nhận Đã xem.
- **Minh Khang:** người dùng offline; tin gửi trong demo dừng ở Đã gửi.
- **Nhóm dự án:** hiển thị người gửi, số thành viên, tệp, video, WAV và biên nhận Đã nhận. Nhấn **+** để tạo nhóm khác.
- **Thanh soạn:** Enter để gửi tin mock, bảng 30 emoji, chọn ảnh/video/tệp hoặc ghi âm tối đa 120 giây. Bấm ảnh để xem đầy đủ, video để mở bằng trình phát mặc định, biểu tượng tải để lưu tệp không ghi đè.

### Phạm vi Demo

Lịch sử, hội thoại, tài khoản và biên nhận chỉ là dữ liệu mô phỏng trong phiên; đóng ứng dụng sẽ mất thay đổi. Media mẫu và bản ghi sử dụng thư mục tạm; tệp lưu theo lựa chọn của bạn vẫn còn. Ghi và phát tin thoại phụ thuộc thiết bị/quyền âm thanh của hệ điều hành. Ứng dụng không có cuộc gọi, Story hoặc cài đặt nâng cao.

`listener.ChatActionHandler` nhận thao tác từ UI và `listener.ChatClientListener` nhận cập nhật. Bản demo chỉ dùng `model.mock.MockChatService`. Xem [đặc tả](specs/001-desktop-frontend/spec.md) và [kế hoạch](specs/001-desktop-frontend/plan.md) để biết giới hạn và hành vi mock.

## 8. Kiểm tra

`mvn test` chạy test lịch sử, kiểm tra/lưu media và luồng giao diện mock. Test Swing cần môi trường desktop; ảnh chụp layout nằm trong `target/`. Microphone, âm thanh phát ra loa và trình phát video mặc định cần kiểm tra thủ công trên máy sử dụng.