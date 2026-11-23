# Implementation plan 005 — History

**Trạng thái:** kế hoạch, chưa code (22/11/2026). **Mục tiêu:** hoàn thành F05 trong Messenger không dựng UI mới.

## Kiến trúc gợi ý

- `application/ChatHistoryService`: bất đồng bộ, điều phối cache/message ID.
- `persistence/HistoryRepository`, `SqliteHistoryRepository`: DB local dưới thư mục ứng dụng. Tạo/migrate schema idempotent, đóng connection khi exit.
- `listener/LiveChatAdapter.loadHistory` kết hợp data từ DB và tin RAM hiện hành; nhận event inbound/outbound từ `LiveChatService` để lưu.
- `ui.frames.MainChatFrame` dùng logic `timeline.merge` sẵn có để chống vẽ lặp, cập nhật UI từ worker trên EDT.
- Phân tách `Demo/Live`: Mock không ghi DB Live.

**Schema tối thiểu:** conversations (id, kind, title, peer/group keys) và messages (message_id PK, conversation_id, sender_id, sent_at_utc, type, body/attachment metadata, state). Index (conversation_id, sent_at_utc, message_id). Chỉ bắt đầu với TEXT, sau đó thêm GROUP/FILE/MEDIA metadata.

## Các bước

1. Unit test ghi/đọc theo conversation và restart; test duplicate/corrupt DB.
2. Repo SQLite + test schema migration. Quy định vị trí file và thao tác xóa.
3. Hook sự kiện Live P2P vào repository; test sau tắt/mở hai service.
4. Chuyển `loadHistory` và xóa hội thoại sang service thật; giữ UI Messenger.
5. Smoke bằng 2 JVM trên một PC, ưu tiên 2 PC thật khi có điều kiện.

**Không chặn bởi:** chứng nhận mã hóa DB/audit SBOM độc lập. **Giữ tối thiểu:** đúng conversation/sender, có lỗi rõ, không log nội dung riêng, không block EDT.

[Acceptance](spec.md) · [Tasks](tasks.md) · [ROADMAP](../../ROADMAP.md).
