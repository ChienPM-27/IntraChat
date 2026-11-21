# IntraChat Desktop

Bản Frontend demo phong cách Messenger, viết bằng Java Swing và FlatLaf. Chạy độc lập với ba người dùng mẫu, hai đoạn chat cá nhân và một nhóm; không có Server, Socket, Database hay xác thực thật.

## Chạy thử
> **Trạng thái dự án (21/11/2026):** Giai đoạn 01 đã có **Java/Swing foundation**, Maven Wrapper, mô hình metadata và JUnit tests. **Chưa** triển khai kết nối LAN, Chat P2P/Group, History Sync, File hoặc Media. M0 mới được kiểm tra một phần trên JDK 25 bằng bytecode target 21; runtime JDK 21 còn cần xác minh.

Yêu cầu JDK 17 trở lên và Maven để build. Maven tải dependency khi cần; file JAR đã đóng gói không cần mạng để chạy.

```shell
mvn clean package
java -jar target/intrachat-0.1.0.jar
```

Nhập một username bất kỳ (tối đa 32 ký tự) và mật khẩu demo không trống. Nếu chọn **Đăng ký**, nhập mật khẩu demo ít nhất 4 ký tự. Không dùng mật khẩu thật.
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
| Kiểu truyền thông | **P2P trực tiếp**, không bắt buộc server trung tâm; plan đề xuất TCP, cơ chế tìm peer triển khai ở giai đoạn 02 |
| Giao diện | **Swing** — đã triển khai khung cửa sổ M0; chưa có giao diện chat |
| Build / dependency | **Maven 3.9.16 + Wrapper** — đã triển khai và kiểm chứng build/test trên JDK 25, Java target 21 |
| Lưu lịch sử | Dự kiến **SQLite tại mỗi peer + History sync P2P** ở giai đoạn 05; chưa triển khai |
| Định dạng thông điệp | Draft **JSON UTF-8 có version, TCP length-prefix framing** trong plan; chưa triển khai |

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
- **JDK 21 LTS (khuyến nghị Eclipse Temurin)**. `pom.xml` biên dịch với `--release 21`. Kiểm thử hiện tại đã chạy trên **Oracle JDK 25.0.4** chứ chưa phải runtime JDK 21.
- Maven Wrapper 3.9.16 được cung cấp bởi `mvnw.cmd` và sẽ tải Maven khi cần. Lần chạy đầu để tải Maven/plugins/dependencies cần Internet (hoặc cache/mirror đã chuẩn bị); **ứng dụng chạy không cần Internet**.
- Hai máy cùng LAN chỉ cần cho giai đoạn 02 trở đi; M0 chưa mở socket.

### Build, chạy và kiểm thử đã xác minh trên Windows 11 + JDK 25 (Java 21 target)

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

Sau khi cache các dependencies, có thể dùng `.\mvnw.cmd -o test` để test offline. **Đã chạy 9/9 JUnit tests pass và build/package/diagnostics thành công** trên môi trường nêu trên; chưa kiểm thử runtime Java 21. Chi tiết lệnh và bằng chứng: [Quickstart](specs/001-project-foundation/quickstart.md) · [M0 test report](specs/001-project-foundation/evidence/m0-test-results.md).

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

**Ranh giới M0:** chưa có server, socket connection, database History Sync hay tính năng truyền media. Các thư mục transport/persistence sẽ được bổ sung ở giai đoạn sau.

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

### Thử kết bạn qua LAN

Mở **Kết bạn** từ sidebar. Nhập IP mẫu `192.168.1.23` hoặc `fd00::23` rồi bấm **Gửi lời mời**. Trong **Đã gửi**, lời mời ở trạng thái **Chờ chấp nhận**. Khánh Linh phản hồi trong bản mô phỏng sau khoảng 6 giây và đoạn chat riêng xuất hiện.

Tab **Đã nhận** có lời mời mẫu từ Hải Nam (`192.168.1.24`). Chọn **Chấp nhận** rồi **Nhắn tin** để mở đoạn chat, hoặc chọn **Từ chối** để đóng lời mời. Lời mời và bạn mới chỉ tồn tại trong phiên demo. IP nhập vào chỉ dùng để tra dữ liệu mẫu.

### Thử gọi thoại và gọi video

Mở đoạn chat với **Mai Anh** rồi bấm biểu tượng điện thoại hoặc camera ở góc trên. Người nhận mô phỏng bắt máy sau khoảng 2 giây. Trong cuộc gọi có bộ đếm thời lượng, nút bật/tắt mic và nút kết thúc. Cuộc gọi video hiển thị hai khung avatar mẫu và cho phép bật/tắt trạng thái camera.

Chọn **Thử gọi đến** trong cùng đoạn chat để mở cuộc gọi thoại hoặc video đến, rồi **Chấp nhận** hoặc **Từ chối**. Lời gọi đến hết hạn sau 20 giây. Xem [đặc tả Frontend cuộc gọi](specs/003-desktop-calls/spec.md).

## Phạm vi

Lịch sử, hội thoại, tài khoản, biên nhận và cuộc gọi dùng dữ liệu mô phỏng trong phiên. Media mẫu và bản ghi sử dụng thư mục tạm. Tệp lưu theo lựa chọn của bạn vẫn còn. Ghi và phát tin nhắn thoại phụ thuộc thiết bị và quyền âm thanh của hệ điều hành.

`listener.ChatActionHandler` nhận thao tác từ UI và `listener.ChatClientListener` nhận cập nhật. Bản demo chỉ dùng `model.mock.MockChatService`. Xem [đặc tả](specs/001-desktop-frontend/spec.md) và [kế hoạch](specs/001-desktop-frontend/plan.md) để biết giới hạn và hành vi mock.

## Kiểm tra

`mvn test` chạy test lịch sử, kiểm tra/lưu media và luồng giao diện mock. Test Swing cần môi trường desktop; ảnh chụp layout nằm trong `target/`. Microphone, âm thanh phát ra loa và trình phát video mặc định cần kiểm tra thủ công trên máy sử dụng.
