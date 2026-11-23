# IntraChat Constitution — MVP 11 chức năng (hiệu lực 23/11/2026)

**Thay thế toàn bộ quy định constitution trước đây**, bao gồm security “NON-NEGOTIABLE” và legacy gates. Nếu các spec/plan/task/evidence cũ mâu thuẫn, ưu tiên [docs/feature-matrix.md](../../docs/feature-matrix.md), [ROADMAP.md](../../ROADMAP.md), [AGENTS.md](../../AGENTS.md) và [kiến trúc](../../docs/architecture.md). Không làm mất tài liệu legacy trong P0.

1. **Đúng phạm vi:** F01 P2P, F02 Group tối thiểu 3 người, F03 File, F04 Emoji, F05 History local sau restart, F06 Image, F07 Video, F08 Sound, F09 State, F10 Time, F11 Sender. Một entrypoint `ui.IntraChat`, một giao diện Messenger; mọi tính năng Live qua LAN thật. Mock/Demo không phải PASS.
2. **Thiết kế đích tối giản:** một stack TCP framed, UUID định tuyến (không xác thực mật mã), một file transfer cho bốn loại file/media, Group owner relay, History JSONL local. Không thêm login/cloud/video call, TLS, encryption, certificate, signature, fingerprint, SAS, pin, DPAPI, independent security Gate/audit.
3. **An toàn vận hành chức năng:** validate frame/type/length, bounded memory/chunk/file, timeout, cleanup, xử lý disconnect; phân biệt đúng recipient, group membership; chọn nơi lưu, chống traversal/overwrite, byte length/CRC32; không deserialization Java từ LAN. Không dùng dữ liệu nhạy cảm trên TCP không mã hóa; không mở ứng dụng ra Internet.
4. **UI/metadata trung thực:** network/disk I/O ngoài Swing EDT; update UI trên EDT; sender theo UUID, time UTC→local, delivered chỉ từ ACK thật, seen chỉ từ READ khi xem thật; tin lỗi không giả thành công; restart không mất History.
5. **Triển khai tăng dần:** P0 chỉ bảo vệ Git và tài liệu; **không xóa source/test/security**. P1 viết đường TCP thay thế và kiểm thử thành công trước khi tháo TLS/DPAPI/dependency. P2 Group, P3 File, P4 History, P5 Media, P6 test & cleanup.
6. **Bằng chứng:** test mới trên 2 JVM cho P2P/file/history/media, 3 JVM cho Group; LAN vật lý khi sẵn có. Ghi PASS/FAIL/NOT RUN và nguyên nhân; không suy từ docs/old tests/mock. Chỉ sửa những file phù hợp giai đoạn; không phá thay đổi chưa commit.

Đây là bộ quy tắc đang có hiệu lực thay cho phiên bản 1.0.1 trước P0. Tài liệu lịch sử có thể còn những từ TLS/security/Gate và chỉ dùng để đối chiếu, không làm tiêu chí nghiệm thu MVP.
