# Báo cáo thực thi C0–C3 — IntraChat (23/11/2026)

> **Ghi chú 28/11/2026:** Đây là snapshot bằng chứng lịch sử tại ngày ghi trên báo cáo; số file, commit, kết quả PASS/NOT RUN và phạm vi audit bên dưới không phải hiện trạng mới nhất. Xem [kiểm chứng hiện hành](current-verification.md), [ma trận](feature-matrix.md) và [ROADMAP](../ROADMAP.md) cho các thay đổi sau C6 (Group Media, auto-save, auto reconnect và GUI Settings).

## A. Git và backup (C0 COMPLETE)

- Repository: `D:\Documents\Lap_trinh_ung_dung_mang\Tuan08\IntraChat`
- **Branch thực tế: `main`; HEAD: `f9b415af9dad8e312cac4a9d7c099411f7d50390`**. Khác baseline kế hoạch `feat/messenger-live-integration` / `ef539c...`: không tự chuyển branch hoặc tái tạo working tree lịch sử.
- Trước cleanup: Git clean ngoại trừ **1 untracked** `docs/p6-cleanup-plan.md`; không có modified/deleted/staged. Trong HEAD hiện hành, bốn spec 003 và evidence nêu trong kế hoạch đều **tracked và clean**, không phải modified/untracked như baseline cũ.
- Backup mới ngoài repo: `D:\Documents\Lap_trinh_ung_dung_mang\Tuan08\IntraChat-C0C3-backup-20261123`; snapshot **220 file** (không copy `.git/` và `target/`). Metadata Git gồm branch/HEAD/status/diff/staged/untracked/deleted.
- `sha256-manifest.csv`: 220 file snapshot so sánh source tại C0, **0 mismatch**. Restore 5/5 mẫu PASS: README, Java launcher, spec 003 plan, evidence 003 và untracked cleanup plan. `archive-manifest.csv`: **114 file** archive, relative path giữ nguyên, SHA-256 xác minh trước và sau xóa. Archive: `backup\archive\...`. Không commit/reset/stash/clean hoặc sửa Java.
- Git sau cleanup có các file docs sửa/xóa có chủ đích và báo cáo mới. Mọi Java source/test có trạng thái Git như trước; không có C0–C3 Java deletion.

## B. Thống kê Markdown

`evidence` là **tập con** của `specs`, không cộng thêm lần hai khi tính tổng.

| Khu vực | Trước | Sau | Archive/xóa |
|---|---:|---:|---:|
| Root Markdown | 7 | 4 | 3 |
| `docs/` Markdown | 16 | 4 (bao gồm báo cáo mới này) | 13 |
| `specs/` Markdown | 97 | 0 | 97 |
| └ `evidence/` (subset) | 47 | 0 | 47 |
| `.specify/` Markdown | 1 | 0 | 1 |
| **Tổng (không đếm evidence hai lần)** | **121** | **8** | **114** |

Số sau bao gồm 1 tài liệu mới; do đó 121 - 114 + 1 = 8. Giữ `DESIGN.md` vì UI color/font/sidebar/bubble tokens chưa dư thừa. Root còn README, ROADMAP, AGENTS, DESIGN; docs còn architecture, feature-matrix, p6-final-report và báo cáo này.

## C. C1 — Tài liệu chuẩn hóa (COMPLETE)

Viết lại `README.md`, `ROADMAP.md`, `AGENTS.md`, `docs/architecture.md`, `docs/feature-matrix.md`. Giữ đúng **F01–F11**; Java 21/Swing/FlatLaf/Jackson; TCP plaintext; UI `ui.IntraChat → LoginFrame → Chat trong LAN (TCP)`; P2P/Group/File/History/Media live backend; JSONL; các giới hạn; group owner relay; dataDir; 2–3 JVM; hai PC/firewall; và kiểm chứng. Không coi Demo/mock là Live hoặc GUI/LAN/playback NOT RUN thành PASS.

Thông tin hữu ích từ PRODUCT/DESIGN, windows-LAN, P0–P5 reports, specs và constitution được tóm tắt trong README/architecture/matrix/AGENTS/ROADMAP. `DESIGN.md` còn độc lập vì có token UI hữu ích.

## D. C2 — Root/docs archive (COMPLETE)

- Batch A: `PRODUCT.md`, `PLAN-SIMPLIFY-11-MVP.md`, `ROADMAP-LEGACY-SECURITY-2026-11-22.md` — 3.
- Batch B: docs course-project-scope, messenger-integration-plan, messenger-ui-style-update, ui-migration, windows-lan-setup — 5.
- Batch C: `docs/p0-report.md` đến `docs/p5-report.md` — 6.
- Batch I: `docs/cleanup-inventory.md`, `docs/p6-cleanup-plan.md` — 2.

Tổng root/docs archive/delete **16**. Mọi file có SHA-256 và đường dẫn đầy đủ trong `archive-manifest.csv`, archive ngoài repo. Các liên kết chính đến P1–P5 đã sửa sang tài liệu hiện hành.

## E. C3 — Specs/evidence/.specify (COMPLETE theo dependency hiện kiểm tra)

- Batch D: specs 001/002 — 40 file; E: specs 003 — 31; F: specs 004 — 17; G: specs 005–007 — 9. Tổng **97 Markdown** (trong đó **47 evidence**).
- Batch H: `.specify/memory/constitution.md` — 1.
- Đã tìm đường dẫn `specs/`, `.specify/`, `constitution.md` trong Java source/tests/scripts/pom/.mvn: không tìm thấy dependency runtime/tooling cụ thể; nội dung quy tắc MVP được chuyển vào `AGENTS.md`.
- **Bảo vệ Spec 003 đặc biệt:** `plan.md`, `quickstart.md`, `spec.md`, `tasks.md`, `evidence/m2-mvp-acceptance.md` đều nằm trong backup và archive cùng relative path, SHA-256 khớp; đã xóa khỏi repo sau archive. Ở branch main lúc C0 chúng là tracked/clean (khác trạng thái baseline P6 cũ). Bản archive lưu nguyên lịch sử TLS/M2 để đối chiếu, không dùng nó xác nhận P1–P5 hiện tại.
- Đã chạy `git diff --check` sau mỗi batch và bảo toàn archive. Không sửa hay loại bỏ Java/tests/scripts.

## F. Kiểm thử sau cleanup

| Kiểm tra | Kết quả |
|---|---|
| `mvnw.cmd clean test package` | **PASS — BUILD SUCCESS** (JDK 21) |
| JUnit | **95 tests / 30 suites, 0 failures, 0 errors** |
| `scripts/verify-all.ps1` đầy đủ | **PASS — exit 0** |
| P1 TCP/JUnit; P2 Group; P3 File; P4 History; P5 Media | **PASS từng nhóm** |
| Executable Shade JAR | **PRESENT**, P1 verification PASS |
| `git diff --check` | **PASS** |
| Internal Markdown links | **0 broken / 29 internal Markdown links** (kiểm tra sau khi tạo báo cáo) |
| Java source/tests chỉnh sửa bởi C0–C3 | **0** |
| GUI Messenger / physical LAN / MP4 and WAV device playback | **NOT RUN** |

Logs: `%TEMP%\intrachat-c0c3-maven.log`, `%TEMP%\intrachat-c0c3-verify-full.log`, và `%TEMP%\intrachat-p6-verify-11a948bb3f7e45cfacd8094fd019cd0f\`. Maven shade có warning resource trùng manifest, không làm build FAIL. Lượt Maven đầu có PowerShell NativeCommandError do thông báo stderr; lượt chạy qua cmd.exe đã **exit 0**.

## G. Việc chưa thực hiện — C4/C5/C6

- **C4:** bỏ Demo/`MockChatService`, `LoginFrame` và phụ thuộc launcher một cách có kiểm thử GUI.
- **C5:** audit dead source/tests/scripts/resources, VoiceRecorder/ComposerPanel, không xóa tùy tiện.
- **C6:** extended VIDEO cancel/disconnect/concurrent text/duplicate offer; disk-full/permissions/crash, GUI hai/ba JVM và LAN nhiều máy vật lý, MP4 player/codec, WAV âm thanh thực.

**Kết luận:** C0–C3 đã thực hiện archive, xóa và kiểm chứng theo phạm vi tài liệu; **P6 vẫn PARTIAL, MVP PARTIAL ACCEPTANCE**, vì C4–C6 chưa hoàn thành. Không có file bị BLOCKED trong batch tài liệu dựa trên dependency đã kiểm tra.
