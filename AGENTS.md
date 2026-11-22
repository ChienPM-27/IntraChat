> **Cập nhật phạm vi đồ án 22/11/2026:** Ưu tiên cao nhất là **11 chức năng Live thật, đúng mục đích, dùng giao diện Messenger hiện có**: P2P, Group, File, Emoji, History, Image, Video, Sound, State, Time, Sender. [ROADMAP](ROADMAP.md) · [Scope và checklist](docs/course-project-scope.md) · [UI integration](docs/messenger-integration-plan.md). **Không mở launcher/chương trình chat riêng cho Group/File/Media**; tái sử dụng `MainChatFrame`, `ComposerPanel`, `CreateGroupDialog` và backend P2P. Kiểm toán bảo mật doanh nghiệp/Gate reviewer độc lập không còn là công việc ưu tiên hoặc điều kiện bắt đầu code theo phạm vi môn học, nhưng file/path/data safety và TLS đang chạy phải giữ. Các task/evidence cũ là lịch sử; không tự check chưa làm.

> **Cập nhật kiến trúc 21/11/2026:** Ứng dụng chỉ còn **một launcher `ui.IntraChat` và một hệ Frontend Messenger** (Demo hoặc Live). Các lớp UI kỹ thuật M1 tại `vn.intrachat.ui` và launcher `ui.LiveIntraChat`/`LiveConnectionFrame` đã xóa. Không khôi phục các cửa sổ này; logic kết nối Live đặt trong `ui.dialogs.LanConnectionDialog` và các lớp service/controller. M2 vẫn chưa nghiệm thu LAN thực/bảo mật độc lập. Đọc `docs/ui-migration-2026-11-21.md` để biết trạng thái mới; các đoạn M0 bên dưới là lịch sử.

# AGENTS.md — Hướng dẫn AI coding agents cho IntraChat

## 1. Vai trò và thứ tự ưu tiên

Bạn là coding agent hỗ trợ dự án **IntraChat**: ứng dụng chat mạng LAN bằng **Java**.

Đọc theo thứ tự trước khi thực hiện thay đổi:

1. `.specify/memory/constitution.md` — nguyên tắc có tính ràng buộc.
2. `README.md` — mục tiêu sản phẩm, tính năng và trạng thái thực tế.
3. Các tài liệu `specs/<feature>/spec.md`, `plan.md`, `tasks.md` **nếu đã có**.
4. Mã nguồn, cấu hình build và test hiện hữu **nếu đã có**.

Khi yêu cầu xung đột, tuân theo chỉ dẫn trực tiếp hợp lệ của người dùng và Constitution; làm rõ/ghi nhận xung đột trước khi thực thi. Không tự coi gợi ý trong README là quyết định kỹ thuật cuối cùng.

## 2. Hiện trạng repository (đối chiếu 22/11/2026)

- **M0:** Verified **41/41 tasks** theo evidence nền tảng; M0 launcher cũ chỉ là lịch sử, đã được xóa khỏi bản build.
- **M1 / LAN diagnostics:** **In Progress 53/60 tasks**. Có `NetworkCoordinator`, TCP v1 framed JSON, listener/connector, Accept/Reject, PING/PONG; v1 **plaintext/chưa xác thực, chỉ diagnostic**, không gửi dữ liệu chat. Không còn UI kỹ thuật M1 độc lập.
- **M2 / Live P2P:** **In Progress 65/90 tasks**. Một Frontend Messenger với launcher `ui.IntraChat`; Demo chạy `MockChatService`, Live qua `LanConnectionDialog`, `LiveChatAdapter`, `LiveChatService`, `SecureChatCoordinator`, TLS 1.3 và xác minh SAS/fingerprint hai phía. Live đã có text/emoji, application ACK và foreground view-based READ ở mức thử nghiệm **hai JVM localhost trên một PC**; transcript/draft/receipt chỉ lưu RAM.
- **Bằng chứng gần nhất:** 22/11/2026, Temurin Java21 full **246/246 JUnit PASS, 0 lỗi/bỏ qua**, shaded JAR/launcher PASS. Đây là test toàn M0/M1/FE/M2/Group core, **không** phải bằng chứng Group/File/History/Media Live đã có. [Báo cáo Group](specs/004-group-chat/evidence/group-core-continued-2026-11-22.md).
- **Còn thiếu nghiệm thu hai Windows PC vật lý** và nhiều task bảo mật/reviewer cũ. Các mục này **không chặn triển khai** Group/History/File/Media trong phạm vi đồ án; không nói M1/M2/Gate đã Verified, và vẫn giữ test lỗi, file-path safety, đúng Sender/State.
- **Chưa có Live:** Group fanout/UI, File Transfer, Image, Video, Sound, History/SQLite sau restart và History Sync; Group core đã có **28/92 task** nhưng không thay thế chức năng nhóm thật. Các chức năng này ở Demo là mô phỏng, không phải transport đã triển khai.
- **Stack đã chọn:** Windows 11, actual Temurin JDK 21, Swing/FlatLaf, Maven Wrapper, Java TCP v1 diagnostic và TLS1.3 v2 secure chat. Xem `docs/architecture.md`, `docs/feature-matrix.md`, `specs/002-lan-networking/`, `specs/003-p2p-messaging/` để tránh dựa vào mô tả M0 đã cũ.

**Phạm vi mục tiêu không đổi (11 chức năng):** P2P, Group, File, Emoji, History, Image, Video, Sound, State, Time, Sender. **Đã có quyết định cho phép triển khai Group/History/File/Media theo scope môn học** trong `docs/course-project-scope.md`; áp dụng spec 004 và các spec 005–007 mới, tập trung acceptance cơ bản.

## 3. Cách làm việc theo Spec Kit

- **Feature mới:** cập nhật spec/plan/tasks đủ mô tả happy path, lỗi cơ bản, tích hợp Messenger và test; dùng quy trình rút gọn `spec → plan → tasks → implement → verify` khi đồ án nhỏ, tránh kéo dài tài liệu bảo mật ngoài rubric.
- Trước khi sửa hành vi, xác định user stories, tiêu chí chấp nhận, trường hợp biên và tác động tới tin nhắn/peer khác.
- `spec.md` mô tả **WHAT/WHY**, tránh nhồi framework và chi tiết code.
- `plan.md` mô tả **HOW**, kiến trúc, mô hình dữ liệu, giao thức, test và quyết định trade-off.
- `tasks.md` chia công việc nhỏ, có điểm kiểm chứng và thứ tự phụ thuộc.
- Không tạo tên file, command, kết quả test hoặc tính năng giả để điền chỗ trống.
- Chỉ dùng slash commands khi dự án thực sự đã khởi tạo integration Spec Kit. AGENTS.md tự nó không cài công cụ Spec Kit.

Nếu người dùng yêu cầu thay đổi nhỏ không cần đầy đủ quy trình, ghi lý do rút gọn và vẫn bảo đảm kiểm tra chất lượng cần thiết.

## 4. Quy ước kỹ thuật bắt buộc

### Java và kiến trúc

- Mỗi lớp có trách nhiệm rõ ràng; tách **UI**, **domain/model**, **protocol**, **network transport**, **service** và **persistence** khi các lớp này tồn tại.
- Tránh trộn đọc/ghi socket, xử lý business và cập nhật giao diện trong cùng một phương thức.
- Ưu tiên API tiêu chuẩn Java; thư viện ngoài phải có lý do và được ghi trong kế hoạch.
- Dùng tên lớp `PascalCase`, method/biến `camelCase`, hằng `UPPER_SNAKE_CASE`, package `lowercase`.
- Dùng UTF-8 và hỗ trợ nội dung tiếng Việt/emoji Unicode.
- Không chặn UI thread bằng socket I/O, truyền tệp hoặc thao tác lưu lịch sử.
- Quản lý vòng đời socket/stream/thread; có timeout và xử lý ngắt kết nối có kiểm soát.

### Giao thức và metadata

- Giao thức phải phân biệt rõ **loại thông điệp**, `messageId`, `senderId`, người nhận/nhóm, timestamp, payload và version khi cần.
- Không tin tên hiển thị do peer gửi là định danh xác thực.
- Không sử dụng `ObjectInputStream` / Java deserialization trực tiếp trên dữ liệu mạng không tin cậy.
- Không gộp trạng thái **peer online/offline** với trạng thái **tin nhắn sent/delivered/read**.
- Không giả định tin nhắn luôn đến đúng thứ tự, đúng một lần hoặc luôn có kết nối.
- Với nhóm, làm rõ semantics phân phối và quản lý thành viên trước khi code.

### File, image, video, sound

- Coi tên tệp, metadata, đường dẫn và payload từ peer là dữ liệu không tin cậy.
- Chuẩn hóa tên/đường dẫn nhận; từ chối traversal, tên bất hợp lệ, đường dẫn tuyệt đối và ghi đè không được đồng ý.
- Quy định giới hạn kích thước và truyền theo luồng; không nạp cả video/tệp lớn vào RAM.
- Kiểm tra byte count/checksum nếu đặc tả yêu cầu; bảo đảm cleanup khi timeout hoặc hủy.
- Không coi tính năng Video/Sound là gọi trực tiếp nếu chưa có spec yêu cầu.

### History, State, Time, Sender

- Không lưu dữ liệu nhạy cảm hoặc toàn bộ nội dung chat vào log thông thường.
- Quy định vòng đời lịch sử: nơi lưu, cách đọc, xóa, migration và phạm vi truy cập.
- Timestamp dùng quy ước nhất quán; không phụ thuộc giờ máy bên kia để suy ra thứ tự toàn cục.
- Trạng thái phải có định nghĩa và chuyển tiếp cụ thể; UI không được hiển thị `đã đọc` nếu chưa có xác nhận.
- Metadata `Sender` phải gắn theo từng thông điệp, không suy ra từ UI đang mở.

## 5. Kiểm thử tối thiểu cho thay đổi

- Bổ sung unit test cho logic/mapping/validation khi có test infrastructure.
- Với networking: test hai peer loopback, mất kết nối, timeout, payload lỗi và ngắt giữa chừng.
- Với Group: test nhiều thành viên, join/leave và không chuyển nhầm người nhận.
- Với File/Media: so sánh bytes trước/sau, thử tên tệp độc hại, giới hạn dung lượng và hủy giữa chừng.
- Với History: test thứ tự hiển thị, phân tách hội thoại và đọc lại sau khởi động.
- Với Time/State/Sender: test dữ liệu sai/thiếu, khác múi giờ, trùng tên hiển thị và chuyển trạng thái.
- Khi chưa có build/test framework, **không được báo “tests passed”**; mô tả rõ phần chưa thể kiểm thử.

## 6. Hạn chế phạm vi và báo cáo

- Không tự tạo server cloud, tài khoản Internet, tính năng gọi âm thanh/video hoặc cơ chế mã hóa như thể đã được phê duyệt.
- Không thay đổi cấu hình Git, xóa thư mục hoặc ghi đè file người dùng ngoài phạm vi được giao.
- Không thêm secrets, mật khẩu, lịch sử chat riêng, dữ liệu thử nghiệm nhạy cảm vào repository.
- Giữ PR/change set nhỏ; chỉ thay đổi các tệp liên quan.
- Kết thúc mỗi nhiệm vụ bằng tóm tắt **đã thay đổi**, **đã kiểm tra**, **chưa kiểm tra/rủi ro**, **bước tiếp theo**. Chỉ báo kết quả đã thực sự chạy.
- Nếu phát hiện tài liệu và mã nguồn không nhất quán, cập nhật tài liệu hoặc nêu rõ điểm lệch.

## 7. Định nghĩa hoàn thành (Definition of Done)

Một tính năng chỉ được xem là hoàn thành khi: acceptance criteria được đáp ứng; có test phù hợp và bằng chứng chạy test; xử lý các tình huống lỗi có liên quan; không vi phạm Constitution; và README/spec được cập nhật theo hành vi thực tế.

**Nguồn chuẩn:** [GitHub Spec Kit](https://github.com/github/spec-kit) · [IntraChat Constitution](.specify/memory/constitution.md).