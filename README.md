# IntraChat Desktop

Bản Frontend demo phong cách Messenger, viết bằng Java Swing và FlatLaf. Chạy độc lập với ba người dùng mẫu, hai đoạn chat cá nhân và một nhóm; không có Server, Socket, Database hay xác thực thật.

> **Trạng thái dự án (21/11/2026):** **M0 Verified (41/41 tasks)** trên Windows 11 + Temurin JDK 21. **M1 LAN Networking đang triển khai (36/60 tasks, đã hợp nhất FE)**: TCP P2P, nhập IPv4/port thủ công, Chấp nhận/Từ chối, handshake và PING/PONG chẩn đoán đã chạy **45 JUnit tests (gồm FE) + hai GUI process loopback**, nhưng **chưa thử hai máy Windows LAN thật nên M1 chưa Verified**. Kết nối M1 **chưa xác thực, chưa mã hóa** và không cho phép Chat/History/File/Group.

Dự án chứa **hai giao diện Swing độc lập**: giao diện **M1 kết nối kỹ thuật P2P** tại `vn.intrachat.IntraChatApplication` và giao diện **FE demo Messenger với dữ liệu giả lập** tại `ui.IntraChat`. FE demo không dùng dữ liệu mạng thực. Cả hai được giữ lại sau khi hợp nhất nhánh FE, **chưa được liên kết thành một sản phẩm chat hoàn chỉnh**.

## Chạy thử

**Yêu cầu build đã kiểm chứng:** Windows 11 x64, **Temurin JDK 21**, Maven Wrapper 3.9.16. Dependency chỉ cần tải lần đầu khi chưa được cache.

```powershell
.\mvnw.cmd clean package
# M1: Giao diện kết nối LAN P2P, chỉ dữ liệu chẩn đoán:
java -jar target/intrachat-0.1.0-SNAPSHOT.jar
# FE: Giao diện Messenger demo độc lập, dùng dữ liệu giả lập:
java -cp target/intrachat-0.1.0-SNAPSHOT.jar ui.IntraChat
# Kiểm thử local 45 JUnit cases, không phải test hai máy LAN thật:
powershell -NoProfile -ExecutionPolicy Bypass -File scripts/verify-m1.ps1 -Offline
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
| Kiểu truyền thông | **TCP P2P trực tiếp**, chỉ IPv4/port thủ công, consent/HELLO/PING/PONG chẩn đoán ở M1; không có auto-discovery |
| Giao diện | **Swing** — M1 UI kỹ thuật và **FE FlatLaf mock demo** độc lập; chưa tích hợp thành chat thật |
| Build / dependency | **Maven 3.9.16 + Wrapper**, Java 21 + JUnit 5.14.4, Jackson 2.22.4 và FlatLaf 3.6.2 |
| Lưu lịch sử | Dự kiến **SQLite tại mỗi peer + History sync P2P** ở giai đoạn 05; chưa triển khai |
| Định dạng thông điệp | **JSON UTF-8 có version, TCP 4-byte length-prefixed frame** và validate strict, đã kiểm thử local; không hỗ trợ Chat/History/File trên M1 |

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
- **Mốc 2 — Chat:** kết nối LAN, P2P, Sender, Time, State (kết nối), Emoji.
- **Mốc 3 — Nhóm và lịch sử:** quản lý thành viên Group; History lưu cục bộ và đồng bộ giữa các máy được phép qua P2P khi kết nối lại.
- **Mốc 4 — Chia sẻ:** File, Image, Video, Sound; kiểm thử dung lượng, đường dẫn và hủy truyền.
- **Mốc 5 — Hoàn thiện:** trạng thái tin nhắn (nếu được duyệt), phục hồi lỗi, test tích hợp, tài liệu build/chạy và đóng gói.

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
