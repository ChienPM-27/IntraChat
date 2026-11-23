# IntraChat — ma trận 11 chức năng MVP (23/11/2026)

Backend PASS là kiểm thử JVM/socket, **không** là nghiệm thu GUI hay LAN vật lý. `NOT RUN` giữ nguyên khi chưa có bằng chứng.

| ID | Chức năng | Implementation / backend verification | GUI | LAN vật lý | Persistence / Playback / giới hạn |
|---|---|---|---|---|---|
| F01 | P2P Chat | LiveChatService/TCP hai JVM, TEXT hai chiều PASS | NOT RUN | NOT RUN | History JSONL; TCP plaintext |
| F02 | Group Chat | GroupService Owner Relay ba JVM, invite/roster/ACK PASS | NOT RUN | NOT RUN | Group metadata/history local; owner offline không relay |
| F03 | File Transfer | FileTransferService offer/accept/reject/chunk/CRC/cancel PASS | NOT RUN | NOT RUN | Metadata history; 128 MiB; no resume; P2P |
| F04 | Emoji | Unicode TEXT qua TCP PASS | NOT RUN | NOT RUN | JSONL text |
| F05 | Chat History | HistoryRepository JSONL, restart hai/ba JVM PASS | NOT RUN | NOT RUN | Local-only, không sync; disk failures NOT RUN |
| F06 | Image | PNG/JPEG byte/type/restore, component test PASS | NOT RUN | NOT RUN | 16 MiB / 32 MP; thumbnail GUI NOT RUN |
| F07 | Video | Transfer/type/restore backend PASS | NOT RUN | NOT RUN | 128 MiB; Desktop.open/codec playback NOT RUN |
| F08 | Sound | Transfer/type/restore, WAV component PASS | NOT RUN | NOT RUN | 8 MiB; WAV Java Sound/device + external playback NOT RUN |
| F09 | State | SENT/ACK/READ from socket events PASS | NOT RUN | NOT RUN | State JSONL; foreground UI observation NOT RUN |
| F10 | Time | Timestamp UTC via socket PASS | NOT RUN | NOT RUN | Timestamp JSONL; GUI formatting NOT RUN |
| F11 | Sender | UUID through P2P/group socket PASS | NOT RUN | NOT RUN | Sender metadata JSONL; unauthenticated UUID |

**Chứng cứ:** P1–P5 process smoke/JUnit trong [P6 report](p6-final-report.md) và [C4 report](c4-cleanup-report.md). C4 chỉ kiểm chứng GUI startup, chưa nghiệm thu GUI Messenger P2P/Group/Media. C0–C3 có [báo cáo riêng](c0-c3-cleanup-report.md). Không dùng evidence M0/M1/M2/TLS lưu trữ để chứng minh backend P1–P5.

**Chưa nghiệm thu:** thao tác nhiều cửa sổ Messenger, LAN hai/ba PC thật, MP4 player/codec, WAV trên thiết bị thật, video cancel/disconnect/concurrency/duplicate offer, disk full/permission/crash. Không coi transfer bytes là playback.

## C6 acceptance delta (24/11/2026)

Across F01–F11 existing P1–P5 automated backend regression remains PASS (97 JUnit after C6). **F01 GUI P2P two-way localhost PASS**, **F04 GUI Unicode/emoji PASS**; **F09 GUI delivered/seen PARTIAL**, **F10/F11 GUI time/sender display PARTIAL**. GUI Group F02, GUI file/media F03/F06/F07/F08, GUI history restart F05 NOT RUN. Physical LAN for every function NOT RUN; audio/video device playback NOT RUN. No overall feature is FULLY ACCEPTED against all acceptance layers. Full [F01–F11 C6 matrix](c6-final-verification-report.md#h-feature-acceptance-matrix).
