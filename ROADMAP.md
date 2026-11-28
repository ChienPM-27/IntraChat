# IntraChat — ROADMAP P0–P6 (28/11/2026)

Phạm vi vẫn là 11 chức năng F01–F11. Group Media, auto-save, reconnect và cải tiến GUI tích hợp các chức năng hiện có, không thêm chức năng F12.

| Giai đoạn | Trạng thái hiện tại |
|---|---|
| P0 — Scope | Chốt F01–F11 |
| P1 — TCP P2P | Backend hai JVM PASS; GUI text localhost và reconnect PASS |
| P2 — Group | Owner relay text/media, roster sync, partial failure; backend PASS, GUI ba cửa sổ smoke PASS |
| P3 — File | OFFER/ACCEPT/REJECT/CHUNK/CRC/cancel; auto-save P2P/nhóm, backend và GUI smoke PASS |
| P4 — History | JSONL restart, profile theo user, media metadata, endpoint; backend PASS, GUI startup/reconnect PASS |
| P5 — Media | Image/video/sound transfer backend PASS, GUI PNG/WAV controls và mẫu MP4/tệp PASS; playback thực NOT RUN |
| P6 — Cleanup/Acceptance | PARTIAL — chưa nghiệm thu toàn bộ LAN vật lý, thiết bị âm thanh/video và stress |

## Tiến độ C0–C6

- C0–C3 COMPLETE: backup, canonical docs, archive có hash; xem [báo cáo lịch sử](docs/c0-c3-cleanup-report.md).
- C4 IMPLEMENTED / BACKEND VERIFIED / CLEANUP COMPLETE: Live-only, không Demo/Login/Mock. GUI hiện đã có smoke P2P, Group/Media và reconnect; không phải full acceptance.
- C5 PARTIAL: đã xóa unused imports và kiểm kê; deep resolved call graph/lifecycle audit vẫn chưa hoàn thành.
- C6 PARTIAL: close boundary history được kiểm thử, JUnit và P1–P5 PASS. Các kiểm thử GUI mới bổ sung sau C6 được ghi ở [current-verification](docs/current-verification.md).

## Các mốc sau C6

| Mốc | Nội dung |
|---|---|
| Windows launcher / auto Listen | JDK21, Maven wrapper, run.cmd, chọn IPv4 private, tự Listen |
| Media GUI fixes | Một micro, nốt nhạc riêng, cập nhật bubble và đường dẫn sau khi nhận |
| Group Media / storage | Owner relay, UUID logic, kết quả từng thành viên, tự lưu theo profile |
| Auto reconnect | Ghi nhớ user, IP/cổng; mở Messenger offline; đồng bộ roster khi kết nối lại |
| Contextual GUI | Ẩn Nhóm trong chat riêng, Settings ở cuối Profile; regression smoke |

## Việc còn lại trước full acceptance

LAN 2–3 máy vật lý; thay đổi mạng/IP/firewall; microphone capture và blocked-read cancellation; WAV/MP3/M4A/video codec playback; GUI history media sau restart; extended file/media race/concurrency; disk full, ACL, crash; timeout-vs-approval stress và full deep member audit. Không tự động resend/offline queue, resume, owner failover hoặc discovery IP mới trong phạm vi hiện tại.

Tài liệu chuẩn: [README](README.md), [DESIGN](DESIGN.md), [architecture](docs/architecture.md), [feature matrix](docs/feature-matrix.md), [current verification](docs/current-verification.md). [P6 report](docs/p6-final-report.md) giữ bằng chứng lịch sử và dẫn tới trạng thái hiện hành.
