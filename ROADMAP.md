# IntraChat — ROADMAP P0–P6 (23/11/2026)

| Giai đoạn | Kết quả |
|---|---|
| P0 — Scope | Chốt đúng F01–F11 MVP |
| P1 — TCP P2P | Backend 2 JVM, text/emoji/ACK/READ PASS |
| P2 — Group | Owner relay backend 3 JVM PASS |
| P3 — File | Offer/accept/reject/chunk/CRC/cancel PASS |
| P4 — History | JSONL, restart, group metadata PASS |
| P5 — Media | Image/video/sound transfer/type/restore backend PASS |
| P6 — Cleanup/Acceptance | **PARTIAL**; C0–C3 tài liệu triển khai riêng, C4 đã triển khai, C5–C6 còn mở |

C0–C3 **COMPLETE**. C4 — Live-only, loại bỏ Demo/Login/Mock: **IMPLEMENTED / BACKEND VERIFIED / CLEANUP COMPLETE — GUI ACCEPTANCE PENDING**; xem [báo cáo C4](docs/c4-cleanup-report.md). C5 — audit dead Java code/tests/scripts/resources: **PARTIAL** (4 unused imports removed; 46 production Java files inventoried; 96 JUnit and P1–P5 PASS; deeper lifecycle/call-graph audit pending). See [C5 report](docs/c5-cleanup-report.md) and [inventory](docs/c5-code-cleanup-inventory.md). C6 — extended regression, GUI/physical LAN/media playback: **NOT DONE**. Không tuyên bố `P6 COMPLETE` hay `MVP FULLY ACCEPTED`.

Bằng chứng trước C4: JUnit 95 PASS; sau C4: JUnit 96 PASS, P1–P5 process smoke PASS; xem [báo cáo C4](docs/c4-cleanup-report.md). GUI Messenger, LAN 2–3 máy vật lý, MP4 Desktop.open và WAV trên thiết bị thực **NOT RUN**.

Tài liệu sử dụng: [README](README.md), [architecture](docs/architecture.md), [11-feature matrix](docs/feature-matrix.md), [P6 report](docs/p6-final-report.md).

## C6 status — 24/11/2026

- C0–C3 COMPLETE; C4 BACKEND VERIFIED / CLEANUP COMPLETE; **C5 PARTIAL** (deep member audit pending).
- **C6 PARTIAL / MVP PARTIAL ACCEPTANCE:** HistoryRepository close hardening and C6 stress test PASS, JUnit 97/97, P1–P5 PASS, P2 stability 3/3, GUI P2P on two live JVMs PASS.
- Remaining: deterministic Accept-vs-timeout, blocked audio cancellation, history crash/I/O faults, file/media concurrency, P6 extended 2-JVM smoke, GUI three-JVM Group/media/history, physical LAN, device playback. See [C6 report](docs/c6-final-verification-report.md).
