# IntraChat

**IntraChat** là dự án phần mềm nhắn tin trong mạng nội bộ (LAN) bằng **Java**, hướng tới giao tiếp trực tiếp giữa các thiết bị cùng mạng: trò chuyện cá nhân (P2P), trò chuyện nhóm, chia sẻ tệp và nội dung đa phương tiện.

> **Trạng thái dự án (21/11/2026):** Repository mới được khởi tạo, chưa có mã nguồn ứng dụng, cấu hình build hoặc bản phát hành. Các tính năng dưới đây là **phạm vi yêu cầu/dự kiến**, không phải cam kết đã triển khai.

## 1. Mục tiêu và phạm vi

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
| Dữ liệu | **History** | Xem lại lịch sử hội thoại theo chính sách lưu trữ được đặc tả. |
| Đa phương tiện | **Image** | Gửi/nhận và xem hình ảnh trong cuộc trò chuyện. |
| Đa phương tiện | **Video** | Gửi/nhận và mở nội dung video; **không mặc định** là gọi video. |
| Đa phương tiện | **Sound** | Gửi/nhận và phát nội dung âm thanh; **không mặc định** là gọi thoại. |
| Trạng thái | **State** | Biểu diễn trạng thái hiện diện/kết nối và, nếu đặc tả yêu cầu, trạng thái tin nhắn. |
| Thông tin | **Time** | Gắn và hiển thị mốc thời gian của tin nhắn/sự kiện theo quy ước rõ ràng. |
| Thông tin | **Sender** | Hiển thị đúng định danh/tên người gửi cho mỗi tin nhắn. |

Các tình huống chi tiết (ví dụ: nhóm có quản trị viên hay không, giới hạn dung lượng, lịch sử lưu tại đâu, trạng thái `đã xem`, cách khám phá thiết bị và loại codec hỗ trợ) **chưa được chốt**. Ghi các quyết định này trong feature spec trước khi lập trình.

## 3. Công nghệ và kiến trúc

| Thành phần | Quyết định hiện tại |
| --- | --- |
| Ngôn ngữ | **Java** |
| Môi trường | Các máy tính kết nối trong cùng LAN |
| Kiểu truyền thông | P2P là **yêu cầu chức năng**; cơ chế tìm peer, điều phối nhóm và giao thức truyền sẽ chọn ở giai đoạn thiết kế |
| Giao diện | Chưa quyết định (cân nhắc Swing hoặc JavaFX) |
| Build / dependency | Chưa quyết định (cân nhắc Maven hoặc Gradle) |
| Lưu lịch sử | Chưa quyết định (tệp cục bộ hoặc cơ sở dữ liệu nhúng) |
| Định dạng thông điệp | Chưa quyết định; cần định nghĩa version, loại tin và metadata |

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

- Git.
- JDK và công cụ build: **phiên bản/công cụ sẽ được xác định khi tạo kế hoạch kỹ thuật**.
- Hai máy cùng LAN (hoặc hai tiến trình loopback) để kiểm thử giao tiếp.

**Chưa có lệnh build hoặc chạy ứng dụng** vì repository chưa có mã nguồn. Khi bổ sung cấu hình thực tế, cập nhật mục này với lệnh chính xác (build, run, test) và phiên bản JDK đã kiểm chứng.

### Tổ chức dự án

Hiện tại repository chứa các tài liệu quản trị và đặc tả:

```text
IntraChat/
├── README.md
├── AGENTS.md
└── .specify/
    └── memory/
        └── constitution.md
```

Thư mục `src/`, `tests/`, cấu hình build và `specs/` chỉ được bổ sung khi có quyết định triển khai hoặc sau bước khởi tạo Spec Kit.

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

- **Mốc 1 — Nền tảng:** chốt JDK, UI/build, cơ chế tìm peer, định dạng thông điệp, định danh người dùng; kiểm thử hai peer.
- **Mốc 2 — Chat:** P2P, Sender, Time, State (kết nối), Emoji, lịch sử cơ bản.
- **Mốc 3 — Nhóm:** quản lý thành viên, gửi/nhận Group, xử lý rời nhóm và lỗi kết nối.
- **Mốc 4 — Chia sẻ:** File, Image, Video, Sound; kiểm thử dung lượng, đường dẫn và hủy truyền.
- **Mốc 5 — Hoàn thiện:** trạng thái tin nhắn (nếu được duyệt), phục hồi lỗi, test tích hợp, tài liệu build/chạy và đóng gói.

Thứ tự có thể thay đổi sau khi viết spec; đây không phải tiến độ đã hoàn thành.

## 7. Tiêu chí chấp nhận tối thiểu

- Hai peer trong LAN có thể trao đổi tin nhắn đúng người gửi, nội dung và thời gian.
- Nhóm gửi tới đúng thành viên theo quy tắc phân phối đã đặc tả.
- Truyền tệp không làm hỏng nội dung; có cách nhận biết lỗi/hủy và tránh ghi ra ngoài thư mục đích.
- Kết nối mất giữa chừng không làm treo UI hoặc làm mất kiểm soát tài nguyên.
- Các hành vi đã triển khai có kiểm thử tương ứng; kết quả kiểm thử phải được ghi nhận trung thực.

## 8. Đóng góp

Trước khi viết code, đọc `AGENTS.md` và `.specify/memory/constitution.md`. Mỗi thay đổi tính năng nên đi kèm spec hoặc cập nhật spec, test, và tài liệu liên quan. Không đưa dữ liệu riêng tư, khóa bí mật, tệp lịch sử chat thật hoặc tệp nhận được vào Git.

## 9. Giấy phép

**Chưa xác định.** Không mặc định dự án được cấp phép mã nguồn mở cho đến khi có tệp `LICENSE`.
