# IntraChat MVP — ma trận 11 chức năng (P6, 23/11/2026)

**Nguồn:** source/JUnit 95 PASS, P1–P5 process smoke chạy lại trong P6. `PASS backend` không tương đương nghiệm thu GUI hoặc LAN vật lý. Các cột GUI và LAN được giữ **NOT RUN** vì chưa kiểm chứng.

| ID | Chức năng | Implemented | Backend | GUI | LAN vật lý | Persistence / giới hạn |
|---|---|---|---|---|---|---|
| F01 | P2P Chat | YES | PASS: TCP 2 JVM, hai chiều | NOT RUN | NOT RUN | History P4; TCP plaintext |
| F02 | Group Chat | YES | PASS: Owner Relay 3 JVM | NOT RUN | NOT RUN | Roster/tin P4; không leader election |
| F03 | File Transfer | YES | PASS: CRC/SHA-256, reject/cancel | NOT RUN | NOT RUN | Lưu metadata; không resume; P2P |
| F04 | Emoji | YES | PASS: Unicode socket | NOT RUN | NOT RUN | Lưu như text |
| F05 | Chat History | YES | PASS: JSONL/restart 2–3 JVM | NOT RUN | NOT RUN | Local-only, không sync; giới hạn index |
| F06 | Image | YES | PASS: PNG/JPEG transfer/type/restore, component | NOT RUN | NOT RUN | 16 MiB; 32 MP; preview GUI chưa nghiệm thu |
| F07 | Video | YES | PASS: byte/type/restore | NOT RUN | NOT RUN | 128 MiB; Desktop.open/codec **NOT RUN** |
| F08 | Sound | YES | PASS: byte/type/restore, WAV component | NOT RUN | NOT RUN | 8 MiB; WAV output và external player **NOT RUN** |
| F09 | State | YES | PASS: ACK/READ from events | NOT RUN | NOT RUN | State persisted; GUI focus chưa nghiệm thu |
| F10 | Time | YES | PASS: UTC timestamp via socket | NOT RUN | NOT RUN | Timestamp persisted; GUI format chưa nghiệm thu |
| F11 | Sender | YES | PASS: sender UUID socket/group | NOT RUN | NOT RUN | Sender metadata persisted; GUI chưa nghiệm thu |

**Chưa kiểm chứng riêng P6:** VIDEO cancel/disconnect/text concurrency/duplicate offer trên TCP, disk full/permissions/crash; playback MP4, thiết bị âm thanh WAV, thao tác hai/ba Messenger và hai/ba máy LAN vật lý. Không coi các trường hợp này là PASS.

Xem [README](../README.md), [kiến trúc](architecture.md), [báo cáo P6](p6-final-report.md).
