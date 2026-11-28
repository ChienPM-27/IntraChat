# P6 — Báo cáo hiện hành sau C0–C3 (23/11/2026)

> **Ghi chú 28/11/2026:** Đây là snapshot bằng chứng lịch sử tại ngày ghi trên báo cáo; số file, commit, kết quả PASS/NOT RUN và phạm vi audit bên dưới không phải hiện trạng mới nhất. Xem [kiểm chứng hiện hành](current-verification.md), [ma trận](feature-matrix.md) và [ROADMAP](../ROADMAP.md) cho các thay đổi sau C6 (Group Media, auto-save, auto reconnect và GUI Settings).

**P6 PARTIAL / MVP PARTIAL ACCEPTANCE.** C0–C3 hoàn thành, C4 Live-only backend/cleanup đã kiểm chứng nhưng GUI acceptance pending; C5 đã thực hiện một phần (cleanup an toàn và backend regression PASS), C6 chưa thực hiện. Không tuyên bố `P6 COMPLETE` hoặc `MVP FULLY ACCEPTED`.

## Trạng thái Git và archive

- Branch thực tế: `main`; HEAD `f9b415af9dad8e312cac4a9d7c099411f7d50390`. Khác branch/HEAD baseline P6 trước đây; trước C0–C3 chỉ có một untracked `docs/p6-cleanup-plan.md`.
- Backup riêng C0–C3: `D:\Documents\Lap_trinh_ung_dung_mang\Tuan08\IntraChat-C0C3-backup-20261123`; **220 files**, SHA-256 0 mismatch, restore 5/5 PASS.
- Archive/xóa **114 Markdown**: 3 root, 13 docs, 97 specs (47 evidence subset), 1 constitution; số Markdown trước **121**, sau **8** (bao gồm báo cáo C0–C3 mới). Source Java/tests/scripts không xóa/sửa trong C0–C3.
- Spec 003 gồm `plan.md`, `quickstart.md`, `spec.md`, `tasks.md`, `evidence/m2-mvp-acceptance.md` được backup/archive/hash đúng; trên `main` trước cleanup là tracked/clean. Manifest đầy đủ ngoài repo: `archive-manifest.csv`.
- C0 backup COMPLETE; C1 canonical docs COMPLETE; C2 root/docs archive COMPLETE; C3 specs/evidence/.specify archive COMPLETE. **0 broken internal Markdown links** sau khi tạo báo cáo.

## 11 chức năng MVP và regression

- F01 P2P, F02 Group, F03 File, F04 Emoji, F05 History, F06 Image, F07 Video, F08 Sound, F09 State, F10 Time, F11 Sender: implementation/backend P1–P5 có bằng chứng PASS, theo [feature matrix](feature-matrix.md).
- Baseline C0–C3: JUnit **95 PASS / 30 suites**. Sau C4, `mvnw.cmd clean test package`: **BUILD SUCCESS** (JDK 21), executable JAR có `Main-Class: ui.IntraChat`; JUnit **96 PASS / 30 suites, 0 failures/errors**.
- `scripts/verify-all.ps1` **exit 0**, P1/P2/P3/P4/P5 PASS, `git diff --check` PASS; log lưu ngoài `target/` tại `%TEMP%`.
- **NOT RUN**: GUI nhiều cửa sổ, LAN 2–3 máy vật lý, MP4/codec playback, WAV/thiết bị âm thanh, extended VIDEO and disk-failure tests. Không suy luận playback từ chuyển bytes hoặc physical LAN từ localhost.

## C4 hoàn tất cleanup, C5 PARTIAL, C6 còn mở

- C4: **IMPLEMENTED / BACKEND VERIFIED / CLEANUP COMPLETE — GUI ACCEPTANCE PENDING.** Đã xóa LoginFrame, MockChatService, DemoMedia, 2 assets. Maven/JUnit 96 PASS, P1–P5 PASS, JAR diagnostics exit 0, GUI startup dialog không Demo login, hai JVM localhost hiện Accept/Reject dialog (chưa xác minh Messenger kết nối), và đóng dialog JVM thoát. Xem [báo cáo C4](c4-cleanup-report.md).
- C5: **PARTIAL**; backup 103 file, SHA-256 0 mismatch, restore 3/3 PASS. Inventory đủ 46 production Java files; xóa 4 unused imports, không xóa class thiếu bằng chứng. 96/96 JUnit PASS, P1–P5 PASS, P2 stability 3/3 PASS, JAR diagnostics PASS. Chưa hoàn tất deep per-method/field/lifecycle audit và GUI Accept timeout; xem [báo cáo C5](c5-cleanup-report.md) và [inventory](c5-code-cleanup-inventory.md).
- C6: extended regression, physical GUI/LAN/playback acceptance và kết luận P6.

Xem [báo cáo C0–C3](c0-c3-cleanup-report.md), [README](../README.md), [architecture](architecture.md), [feature matrix](feature-matrix.md), [ROADMAP](../ROADMAP.md).

## C6 verification update — 24/11/2026

**C6 PARTIAL — MVP PARTIAL ACCEPTANCE; C5 PARTIAL; P6 PARTIAL.** C6 backup 106/106 SHA-256 matched and 3/3 restore verified. Added one HistoryRepository close-boundary fix and one concurrency JUnit test; post-change JDK 21 clean test package **97/97 JUnit PASS**. P1–P5 process regression **PASS**, P2 3 additional rounds **PASS**, `git diff --check` **PASS**. Real two-JVM GUI localhost Connect/Accept and Unicode P2P messages in both directions **PASS**; delivery→Seen visible and both app JVMs exit on GUI close. GUI Group three JVM, GUI media/history restart, timed Accept/Expire races, VoiceRecorder blocked read, FileTransfer race tests, extended P6 M01–M14, real media playback and physical LAN remain **NOT RUN**. Do not mark C6 backend/gui/lan fully verified or P6 cleanup complete. See [C6 final verification report](c6-final-verification-report.md).
