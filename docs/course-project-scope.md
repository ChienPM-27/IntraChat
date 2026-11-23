# Quyết định phạm vi IntraChat — đồ án môn học

**Ngày quyết định:** 22/11/2026 · **Người đưa ra yêu cầu:** người thực hiện đồ án
**Ưu tiên:** **11 chức năng thật, hoạt động đúng mục đích, dùng chung Frontend Messenger hiện có.** Bảo mật nâng cao không phải mục tiêu chính của môn học.

## 1. Thứ tự ưu tiên mới

1. Người dùng nhìn thấy và thao tác được chức năng trên **cùng Messenger**.
2. Hai/nhiều tiến trình trao đổi **dữ liệu thật** qua LAN, không thay bằng dữ liệu mock.
3. Trạng thái và thời gian/sender đúng, truyền tin/file/media không hỏng; History còn sau restart.
4. Không treo UI, xử lý lỗi phổ biến, có test và kịch bản demo rõ ràng.
5. Phần mã bảo mật đã chạy được **giữ nguyên để tận dụng**; chỉ đơn giản hóa khi thực sự chặn chức năng hoặc UI người dùng.
6. Audit, penetration test, multi-user DPAPI, SBOM/provenance, chứng nhận Gate 0/Gate 1 theo chuẩn production là **ngoài phạm vi nghiệm thu bắt buộc của môn học** trừ khi giảng viên/rubric yêu cầu rõ.

**Không có thay đổi code bảo mật nào được thực hiện chỉ bằng quyết định tài liệu này.** M1 plaintext vẫn chỉ dành cho chẩn đoán; M2 TLS hiện có tiếp tục được dùng. Không chủ động gửi file/nội dung chat qua một kênh không phân biệt người nhận.

## 2. Tiêu chí chứng minh 11 chức năng

| ID | Chức năng | Phải trình diễn được trong Messenger Live |
| --- | --- | --- |
| F01 | **P2P** | Chọn peer, hai đầu gửi/nhận text qua LAN; kết nối/ngắt/kết nối lại |
| F02 | **Group** | Tạo nhóm, mời/chấp nhận, 3 người thấy tin cùng nhóm, sender đúng; thành viên rời nhóm |
| F03 | **File** | Chọn, gửi chunk, người nhận lưu, byte/checksum khớp, hiển thị tiến độ/hủy |
| F04 | **Emoji** | Soạn, gửi, nhận Unicode/ZWJ/tiếng Việt không lỗi |
| F05 | **History** | Gửi tin rồi tắt/mở lại app, xem lại đúng hội thoại, người gửi và thời gian |
| F06 | **Image** | Gửi ảnh qua LAN, thumbnail/preview và lưu file nhận |
| F07 | **Video** | Gửi tệp video qua LAN, lưu/mở bằng trình phát OS, có lỗi fallback |
| F08 | **Sound** | Ghi âm hoặc chọn tệp âm thanh, gửi, nhận, phát lại; không phải voice call |
| F09 | **State** | Online/offline và gửi/nhận/xem/thất bại/chưa rõ tương ứng sự kiện thật |
| F10 | **Time** | Mỗi tin có timestamp UTC nhất quán, hiển thị giờ địa phương, còn đúng sau History reload |
| F11 | **Sender** | Tên/định danh người gửi chính xác ở P2P/Group, kể cả khi trùng display name |

**Mức trạng thái:** `Chưa có` → `Có mã lõi` → `Có Live cục bộ` → `Đạt demo đồ án`. Chỉ chuyển sang **Đạt demo** sau khi thao tác thực sự trong Messenger và ghi kết quả; không suy từ tỷ lệ hoàn tất task cũ.

### Kịch bản demo liên tục cho giảng viên

**Chuẩn bị:** 2–3 cửa sổ ứng dụng hoặc 2–3 máy trong cùng Wi-Fi/LAN; không cần dịch vụ Internet công cộng. Mỗi cửa sổ có danh tính khác nhau; nếu trên một PC, dùng `intrachat.secureRoot` tách biệt như test.

1. Mở `ui.IntraChat`; chọn **Chat trong LAN**. A lắng nghe, B kết nối bằng IP/cổng; Accept và xác minh theo dialog hiện hữu.
2. A/B nhắn tiếng Việt + emoji; kiểm tra `Sender`, `Time`, `State`.
3. Thêm C, tạo nhóm A–B–C trong **Messenger Live**, gửi tin và xác nhận cả B/C nhận đúng nội dung.
4. A gửi file có tên Unicode; B lưu và đối chiếu SHA-256; thử Cancel một tệp khác.
5. A gửi ảnh/video/âm thanh qua cùng FileTransfer, B xem/phát/mở bằng Messenger/OS.
6. Tắt rồi mở lại A/B, chọn hội thoại và xem lại History (ít nhất lịch sử cục bộ); kiểm tra người gửi/giờ và không trộn nhóm.
7. Thử ngắt mạng/hủy kết nối khi đang gửi; UI không treo và không báo truyền xong khi chưa xong.

**Chưa chạy đủ:** các bước 3–6 Live LAN. Demo Group/File/Media không thay thế bằng chứng này. P2P/Emoji/State/Time/Sender có các test loopback cục bộ nhưng vẫn cần kịch bản trình diễn cuối.

## 3. Chuyển cách đo tiến độ

Các task tracker hiện có là **lịch sử công việc kỹ thuật**; không xóa và không tự ý check task chưa làm.

- `002-lan-networking`: **53/60**; phần còn mở chủ yếu là nghiệm thu LAN/security gate kiểu cũ.
- `003-p2p-messaging`: **65/90**; có P2P Live cục bộ, các task nâng cao và nghiệm thu vẫn mở.
- `004-group-chat`: **28/92**; Group core đã có, **Group Live chưa hoạt động**.
- `005-chat-history`, `006-file-transfer`, `007-multimedia`: đặc tả/checklist lớp học được tạo để triển khai tiếp; **chưa có code Live**.

**Không dùng** 53/60 hay 65/90 để suy ra F01/F09 đạt 90%. Chỉ có chức năng Live thì mới tính Fxx hoàn thành.

## 4. Phạm vi bảo mật điều chỉnh

**Bắt buộc tối thiểu cho chức năng đúng và không gây hại:**
- Chọn đúng peer/recipient; M1 diagnostic không được mang nội dung người dùng; M2 TLS hiện có không bị vô hiệu hóa.
- Kiểm tra framing/type/size/timeout; không khóa giao diện; đóng socket/file/worker sạch.
- File: không traversal, không ghi đè ngoài ý muốn, không ghi vào đường dẫn lạ; stream theo chunk, byte integrity.
- Không log key hoặc nội dung riêng, không lưu credentials thật trong Demo; không dùng `ObjectInputStream` trên dữ liệu mạng.
- Không giả mạo `Đã nhận/Đã xem`/transfer completed; Group fanout không đưa tin cho người ngoài nhóm.

**Để sau hoặc bỏ khỏi tiêu chí bàn giao môn học:** kiểm toán bảo mật bên thứ ba, vòng đời chữ ký grant cấp doanh nghiệp, cryptographic audit nhiều vòng, cross-user Windows DPAPI penetration testing, SBOM/CVE review độc lập, throughput/performance stress quy mô lớn, Gate reviewer độc lập và chứng nhận production-grade. Nếu dependency cũ vẫn có mã bảo mật phức tạp, giữ mã đó đến khi có thay thế **đã kiểm thử**; không cắt bỏ một cách phá hủy.

Các quy tắc nền tảng về hành vi an toàn trong [Constitution](../.specify/memory/constitution.md) vẫn có hiệu lực. Quyết định này **chỉ thay đổi phạm vi và tiêu chí ưu tiên để bàn giao môn học**, không ghi đè các yêu cầu an toàn cơ bản bằng câu chữ mơ hồ. Các biên bản Gate 0/Gate 1 lịch sử trong spec vẫn là hồ sơ lịch sử, chưa được biến thành PASS.

## 5. Những quyết định nhằm giảm độ phức tạp

- **Không UI mới độc lập:** mở rộng `MainChatFrame`, `ComposerPanel`, `MessageBubblePanel`, `CreateGroupDialog`, `LanConnectionDialog`.
- **Group:** mục tiêu tối thiểu là 3 thiết bị, một chủ nhóm, danh sách thành viên, fanout tới các kênh P2P; không buộc một owner-signed ticket mới mỗi message nếu kênh P2P đang có có thể cung cấp đúng định tuyến/nhận dạng.
- **History:** triển khai lưu đọc cục bộ sau restart trước; nếu yêu cầu `CL-04` trước đây về P2P sync vẫn thuộc rubric, thực hiện sync sau khi local History hoạt động. **Chưa coi local History đạt toàn bộ đồng bộ P2P**.
- **File:** một cơ chế chunk dùng chung cho File/Image/Video/Sound, có checksum và Cancel.
- **Media:** chia sẻ tệp và xem/phát, không làm video call/voice call.
- **Kiểm thử:** hai JVM trên một PC đủ cho RED→GREEN local; **bài demo LAN thật** vẫn rất nên thực hiện khi có máy để xác nhận đúng yêu cầu mạng.

## 6. Tài liệu điều phối

- [ROADMAP đồ án](../ROADMAP.md) — **lộ trình ưu tiên hiện hành**.
- [Messenger integration plan](messenger-integration-plan.md) — mapping UI, FE service/API và mốc bật nút.
- [Feature matrix](feature-matrix.md) — trạng thái thật của 11 chức năng.
- [Roadmap lịch sử](../ROADMAP-LEGACY-SECURITY-2026-11-22.md) — bản sao nguyên trạng, không xóa kiến thức kỹ thuật đã có.
- [Spec 004](../specs/004-group-chat/tasks.md) và [Spec 005](../specs/005-chat-history/spec.md), [006](../specs/006-file-transfer/spec.md), [007](../specs/007-multimedia/spec.md) — đầu vào triển khai tiếp.

**Hạn chế ghi rõ:** Chưa có bằng chứng cả 11 chức năng Live hoạt động; tài liệu không tự thêm chức năng mạng mới, không tự tạo kết quả test, không biến Gate lịch sử thành Verified.