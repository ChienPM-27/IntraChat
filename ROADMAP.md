# IntraChat — ROADMAP P0–P6 (23/11/2026)

| Giai đoạn | Trạng thái kỹ thuật đã xác minh |
|---|---|
| P0 | Chốt MVP 11 chức năng; tồn tại tài liệu/backup lịch sử |
| P1 | Backend TCP P2P PASS (hai JVM) |
| P2 | Backend Group Owner Relay PASS (ba JVM) |
| P3 | File P2P PASS (byte, CRC, SHA-256, cancel/reject) |
| P4 | History JSONL và restart PASS (hai/ba JVM) |
| P5 | Media transfer/type/history và component PASS; playback phần cứng NOT RUN |
| P6 | **PARTIAL** — backup/kiểm chứng, regression và tài liệu được cập nhật; cleanup legacy documentation chưa hoàn tất toàn diện |

**Maven/JUnit:** 95 PASS, 0 fail/skip ở lần chạy P6. **GUI Messenger:** NOT RUN. **LAN vật lý:** NOT RUN. **MP4 Desktop.open:** NOT RUN. **WAV trên thiết bị âm thanh:** NOT RUN. **Extended P6 media scenarios:** NOT RUN. Không tuyên bố nghiệm thu đầy đủ.

Sử dụng [README](README.md), [ma trận F01–F11](docs/feature-matrix.md) và [báo cáo P6](docs/p6-final-report.md) làm trạng thái hiện tại; các báo cáo P0–P5 chỉ là hồ sơ lịch sử.
