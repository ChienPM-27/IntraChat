# AGENTS.md — Hướng dẫn AI coding agents cho IntraChat

## 1. Vai trò và thứ tự ưu tiên

Bạn là coding agent hỗ trợ dự án **IntraChat**: ứng dụng chat mạng LAN bằng **Java**.

Đọc theo thứ tự trước khi thực hiện thay đổi:

1. `.specify/memory/constitution.md` — nguyên tắc có tính ràng buộc.
2. `README.md` — mục tiêu sản phẩm, tính năng và trạng thái thực tế.
3. Các tài liệu `specs/<feature>/spec.md`, `plan.md`, `tasks.md` **nếu đã có**.
4. Mã nguồn, cấu hình build và test hiện hữu **nếu đã có**.

Khi yêu cầu xung đột, tuân theo chỉ dẫn trực tiếp hợp lệ của người dùng và Constitution; làm rõ/ghi nhận xung đột trước khi thực thi. Không tự coi gợi ý trong README là quyết định kỹ thuật cuối cùng.

## 2. Hiện trạng repository

Tính đến bản nền tảng M0 (2026-11-21), repository **đã có** Java/Swing entrypoint, Maven Wrapper, metadata/domain types và bộ test JUnit. Chưa có kết nối LAN, nhắn tin, database hay History Sync thực tế. Bằng chứng M0 tại `specs/001-project-foundation/evidence/`; actual Temurin JDK21 runtime và network-denied GUI test đã xác minh M0 Gate 1 (xem `m0-jdk21-offline.md`). JDK25 vẫn có trên hệ thống nhưng kiểm thử M0 đã được chạy với command-scoped `JAVA_HOME` JDK21. **Không được tuyên bố tính năng đã được triển khai, đã chạy, đã kiểm thử hoặc đã bảo mật** nếu không có bằng chứng thực tế.

Phạm vi mục tiêu gồm 11 chức năng: **P2P, Group, File, Emoji, History, Image, Video, Sound, State, Time, Sender**.

Các công nghệ chưa được chọn: JDK version, Swing/JavaFX, Maven/Gradle, TCP/UDP, cơ chế peer discovery, server điều phối (nếu có), persistence, codec. Khi được giao phát triển, đề xuất lựa chọn cùng lý do/trade-off trong `plan.md`, không âm thầm giả định.

## 3. Cách làm việc theo Spec Kit

- **Feature mới:** tạo/cập nhật đặc tả bằng quy trình `specify → clarify (nếu cần) → plan → tasks → implement → validate`.
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
