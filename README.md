# IntraChat Desktop

IntraChat gồm **M1 LAN diagnostics**, **M2 Chat Live bảo vệ bằng TLS 1.3 (đang triển khai và kiểm thử local)** và **Messenger Demo** bằng Swing/FlatLaf với dữ liệu giả lập. Demo không kết nối TCP hoặc sử dụng xác thực thật.

> **Trạng thái dự án (21/11/2026):** **M0 Verified (41/41 tasks)** trên Windows 11 + Temurin JDK 21. **M1 LAN Networking đang triển khai (36/60 tasks, đã hợp nhất FE)**: TCP P2P, nhập IPv4/port thủ công, Chấp nhận/Từ chối, PING/PONG chẩn đoán đã kiểm thử local; **M1 chưa Verified** vì thiếu hai PC thật. **M2 đang triển khai (15/90 tasks checked)**: Live Messenger có TLS1.3, ghép cặp fingerprint và text/ACK/READ trong hai GUI process local; **chưa qua độc lập security audit hoặc two-PC LAN**, không phải bản chat được chứng nhận bảo mật. M1 **không có auth/TLS** và luôn chặn Chat/History/File/Group.

Dự án hiện có **ba entrypoint**: **M1 diagnostics** tại `vn.intrachat.IntraChatApplication`, **M2 Chat Live** tại `ui.LiveIntraChat` (kết nối TCP thủ công, TLS1.3, fingerprint và FE Messenger thật), và **Demo độc lập** tại `ui.IntraChat` (mock data, không gửi mạng). **M2 chưa Verified**: mới chạy loopback trên một Windows PC với hai tiến trình; còn thiếu kiểm thử hai Windows PC vật lý, review bảo mật và các case failure/stress. Không gửi dữ liệu riêng tư khi chưa hoàn tất nghiệm thu bảo mật.

## Chạy thử

**Yêu cầu build đã kiểm chứng:** Windows 11 x64, **Temurin JDK 21**, Maven Wrapper 3.9.16. Dependency chỉ cần tải lần đầu khi chưa được cache.

```powershell
.\mvnw.cmd clean package
# M1: Giao diện kết nối LAN P2P, chỉ dữ liệu chẩn đoán:
java -jar target/intrachat-0.1.0-SNAPSHOT.jar
# M2: Live Chat LAN (bản đang phát triển, chưa nghiệm thu bảo mật):
java -cp target/intrachat-0.1.0-SNAPSHOT.jar ui.LiveIntraChat
# FE Demo: dữ liệu giả lập, không kết nối mạng:
java -cp target/intrachat-0.1.0-SNAPSHOT.jar ui.IntraChat
# Local Java 21 regression (M0/M1/M2/FE), không phải two-PC acceptance:
powershell -NoProfile -ExecutionPolicy Bypass -File scripts/verify-m2.ps1 -Offline
```

Với **FE demo**, nhập username bất kỳ (tối đa 32 ký tự) và mật khẩu demo không trống; chọn **Đăng ký** thì dùng tối thiểu 4 ký tự. **Không dùng mật khẩu thật.** FE chỉ là dữ liệu mô phỏng, không phải xác thực thực tế. Giao diện M1 riêng chỉ có bước Chấp nhận/Từ chối **không xác thực thiết bị**.
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

Những tình huống chi tiết (ví dụ: nhóm có quản trị viên hay không, giới hạn dung lượng, chính sách giữ/xóa lịch sử, cơ chế xác thực peer, trạng thái `đã xem`, cách khám phá thiết bị và codec hỗ trợ) **chưa được chốt**; các quyết định nền tảng đã có trong [spec](specs/001-project-foundation/spec.md) và [plan](specs/001-project-foundation/plan.md). Ghi các quyết định này trong feature spec trước khi lập trình.

## 3. Công nghệ và kiến trúc

| Thành phần | Quyết định hiện tại |
| --- | --- |
| Ngôn ngữ | **Java** |
| Môi trường | **Windows** (bản đầu tiên), các máy trong cùng LAN |
| Kiểu truyền thông | **TCP P2P IPv4 thủ công**; M1 diagnostics v1 plaintext riêng; M2 secure CHAT_V2 qua TLS1.3 với fingerprint pairing, chưa đầy đủ nghiệm thu |
| Giao diện | **Swing + FlatLaf** — M1 diagnostics, **M2 Messenger FE Live** (đang kiểm thử local), FE Mock Demo tách riêng |
| Build / dependency | **Maven Wrapper 3.9.16, Java21, JUnit5.14.4, Jackson2.22.4, FlatLaf3.6.2**, thêm Bouncy Castle1.87 (cert) và JNA5.19.1 (DPAPI/Windows) trong M2 |
| Lưu lịch sử | Dự kiến **SQLite tại mỗi peer + History sync P2P** ở giai đoạn 05; chưa triển khai |
| Định dạng thông điệp | **M1 v1 strict JSON chẩn đoán**, còn **M2 v2 strict framed JSON bên trong TLS1.3**; chỉ text/ACK/read, không Group/File/History; chưa audit security |

### Phân lớp kiến trúc đề xuất

```text
Giao diện người dùng (UI)
        |
Dịch vụ ứng dụng (Chat / Group / Transfer / Presence / History)
        |
Mô hình dữ liệu + giao thức thông điệp
        |
Kết nối LAN (discovery / connection / transport)
        |
Lưu trữ cục bộ (nếu được chọn)
```

Đây là **sơ đồ định hướng**, chưa phải cấu trúc thư mục hay kiến trúc được triển khai.

### Hợp đồng thông điệp cần thiết kế

Mỗi thông điệp cần có định danh duy nhất và loại thông điệp (ví dụ: văn bản, emoji, yêu cầu chuyển tệp, trạng thái); thông tin người gửi; người nhận hoặc nhóm đích; thời điểm; và dữ liệu nội dung hoặc tham chiếu tệp. Đặc tả phải xác định:

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
- Hai máy cùng LAN chỉ cần cho giai đoạn 02 trở đi; M0 chưa mở socket.

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

Sau khi cache dependencies, `.\mvnw.cmd -o test` chạy offline. **M0: 9/9 JUnit pass trên actual Temurin JDK21**, cùng 5/5 GUI cycles dưới kiểm soát Java-network-denied; [M0 acceptance](specs/001-project-foundation/evidence/m0-acceptance-report.md). **M1 + FE: 45 JUnit cases pass**, hai tiến trình GUI độc lập trên cùng máy xác nhận Accept và PING/PONG hai chiều; [M1 test evidence](specs/002-lan-networking/evidence/m1-test-results.md). **Chưa có test hai máy LAN thật**, nên M1 chưa Verified. Xem [M1 quickstart](specs/002-lan-networking/quickstart.md) và [Windows LAN guide](docs/windows-lan-setup.md).

### Tổ chức dự án

Cấu trúc nền tảng đã được triển khai:

```text
IntraChat/
├── pom.xml, mvnw.cmd, .mvn/wrapper/
├── src/main/java/vn/intrachat/  # entrypoint, UI, domain, protocol metadata
├── src/test/java/vn/intrachat/  # JUnit tests
├── docs/                          # architecture + feature matrix
├── scripts/                       # environment + M0 verification
├── specs/001-project-foundation/  # spec, plan, tasks, contracts, evidence
├── README.md, ROADMAP.md, AGENTS.md
└── .specify/memory/constitution.md
```

**Ranh giới hiện tại:** M0 đã Verified. M1 đã có **TCP listener/connector, handshake và PING/PONG chẩn đoán**, không phải chat người dùng. Vẫn **chưa có** authentication/TLS, History Sync, File/Media qua mạng; FE Messenger là mock demo độc lập, chưa nối với transport M1.

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
- **Mốc 3 — Nhóm và lịch sử:** quản lý thành viên Group; History lưu cục bộ và đồng bộ giữa các máy được phép qua P2P khi kết nối lại.
- **Mốc 4 — Chia sẻ:** File, Image, Video, Sound; kiểm thử dung lượng, đường dẫn và hủy truyền.
- **Mốc 5 — Hoàn thiện:** hoàn thành kiểm thử lỗi mạng, an toàn danh tính và read receipts, tài liệu đóng gói, kiểm thử hai Windows PC thật; chỉ ghi Verified khi toàn bộ acceptance đạt.

Thứ tự có thể thay đổi sau khi viết spec; đây không phải tiến độ đã hoàn thành.

- **Mai Anh:** người dùng online, lịch sử có ảnh và biên nhận Đã xem.
- **Minh Khang:** người dùng offline; tin gửi trong demo dừng ở Đã gửi.
- **Nhóm dự án:** hiển thị người gửi, số thành viên, tệp, video, WAV và biên nhận Đã nhận. Nhấn **+** để tạo nhóm khác.
- **Thanh soạn:** Enter để gửi tin mock, bảng 30 emoji, chọn ảnh/video/tệp hoặc ghi âm tối đa 120 giây. Bấm ảnh để xem đầy đủ, video để mở bằng trình phát mặc định, biểu tượng tải để lưu tệp không ghi đè.

## Phạm vi

Lịch sử, hội thoại, tài khoản và biên nhận chỉ là dữ liệu mô phỏng trong phiên; đóng ứng dụng sẽ mất thay đổi. Media mẫu và bản ghi sử dụng thư mục tạm; tệp lưu theo lựa chọn của bạn vẫn còn. Ghi và phát tin thoại phụ thuộc thiết bị/quyền âm thanh của hệ điều hành. Ứng dụng không có cuộc gọi, Story hoặc cài đặt nâng cao.

`listener.ChatActionHandler` nhận thao tác từ UI và `listener.ChatClientListener` nhận cập nhật. Bản demo chỉ dùng `model.mock.MockChatService`. Xem [đặc tả](specs/001-desktop-frontend/spec.md) và [kế hoạch](specs/001-desktop-frontend/plan.md) để biết giới hạn và hành vi mock.

## Kiểm tra

`mvn test` chạy test lịch sử, kiểm tra/lưu media và luồng giao diện mock. Test Swing cần môi trường desktop; ảnh chụp layout nằm trong `target/`. Microphone, âm thanh phát ra loa và trình phát video mặc định cần kiểm tra thủ công trên máy sử dụng.
