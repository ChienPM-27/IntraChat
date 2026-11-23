# Implementation plan 006 — File Transfer LAN

**Trạng thái:** kế hoạch, chưa triển khai (22/11/2026).

## Ranh giới lớp

- `protocol/FileTransferFrame`: `OFFER/ACCEPT/CHUNK/COMPLETE/CANCEL/ERROR`, request/recipient/conversation ID, index, data, size/checksum.
- `application/FileTransferService`: quản lý mỗi transfer (trạng thái, bytes, deadline, cancel), stream file bằng buffer cố định và gửi qua kênh P2P đang kết nối; không tự mở public Internet.
- `transport`: tái dùng verified M2 session/codec với frame type mới hoặc kênh file phụ gắn cùng peer; không thay bằng plaintext M1 diagnostics.
- `listener/LiveChatAdapter`: ánh xạ progress/complete/error sang `ChatClientListener`.
- `ui.components.ComposerPanel/MessageBubblePanel/MediaFiles`: chọn file, offer confirmation và bubble có trạng thái. Bật nút File trong Messenger sau khi service và receive đã có.

## Trình tự code

1. RED unit test state machine + path/size/chunk.
2. Xây file sender/receiver loopback với stream và SHA-256; cancel và cleanup.
3. Nối verified peer, không tự ghi đè file, chọn đích của receiver.
4. Nối FE adapter, bubble/file progress, test EDT.
5. 2 JVM test gửi file bất kỳ/Unicode, so hash; test offline/timeout/cancel.
6. Tận dụng service này cho Spec 007.

**Giới hạn tham khảo:** chunk ≤64KiB, size cap cấu hình và số transfer đồng thời nhỏ (cần test); không viết file nguyên khối vào RAM. Báo rõ lỗi/overrun, không fake transfer completed. Chi tiết ngưỡng chỉnh theo thực nghiệm.

[Spec](spec.md) · [Tasks](tasks.md) · [ROADMAP](../../ROADMAP.md).
