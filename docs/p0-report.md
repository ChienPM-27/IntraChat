# Báo cáo P0 — 23/11/2026

## Git trước P0 và bản sao lưu

- Branch: `feat/messenger-live-integration`; HEAD `ef539c125aa08b96b65774317d032acf636441af`.
- Trước P0: modified `specs/003-p2p-messaging/{plan.md,quickstart.md,spec.md,tasks.md}`; untracked `PLAN-SIMPLIFY-11-MVP.md` và `specs/003-p2p-messaging/evidence/m2-mvp-acceptance.md`.
- Backup ngoài repo: `D:\Documents\Lap_trinh_ung_dung_mang\Tuan08\IntraChat-P0-backup-20261123`. Có bản sao **đủ 6 file** giữ nguyên relative path, `pre-p0.patch`, `pre-p0-status.txt`, `pre-p0-head.txt`, `pre-p0-branch.txt`.
- Khôi phục an toàn: đóng editor, **sao lưu tiếp mọi thay đổi mới**; copy riêng file cần phục hồi từ thư mục backup vào repository sau khi kiểm tra diff. Nếu cần patch 4 tracked spec, kiểm tra bằng `git apply --check <backup>\pre-p0.patch` trên **working tree sạch phù hợp** trước khi `git apply`; không apply trực tiếp đè lên 4 file đã modified. Không dùng reset/clean/stash. File untracked khôi phục bằng copy, patch không bao gồm chúng.

## Phạm vi thực hiện

- P0 chỉ tài liệu: `README.md`, `ROADMAP.md`, `AGENTS.md`, `docs/architecture.md`, `docs/feature-matrix.md`, `docs/cleanup-inventory.md`, `.specify/memory/constitution.md`, báo cáo này. Không sửa Java/tests/scripts/pom, không xóa file, không đụng `.git`.
- Thống nhất đúng F01–F11 trong [ma trận](feature-matrix.md), có trạng thái code/tiêu chí nghiệm thu/phụ thuộc/giai đoạn; [roadmap](../ROADMAP.md) P0–P6; [inventory](cleanup-inventory.md) đầy đủ nhóm source, tests, Maven, scripts, UI, mock, spec/contracts/evidence.
- Constitution cũ được thay bằng quy tắc MVP; `PRODUCT.md`, `docs/course-project-scope.md`, specs và roadmap legacy vẫn giữ làm hồ sơ lịch sử khi mâu thuẫn. Không coi security gate legacy là ràng buộc.
- Mã TLS/DPAPI/security còn nguyên đến P1 và có thể tiếp tục phục vụ đường chạy Live hiện tại. TCP mục tiêu **chưa được triển khai**.

## QA P0

| Kiểm tra | Kết quả | Ghi chú |
| --- | --- | --- |
| Snapshot 6 file thay đổi ban đầu | PASS | 4 tracked + 2 untracked và patch/status/HEAD |
| Ma trận đúng 11 IDs F01–F11 | PASS | Mỗi ID đúng một dòng, chưa có PASS nghiệm thu |
| Mốc P0–P6 + dependencies rõ | PASS | ROADMAP và matrix |
| Nguyên tắc scope và constitution hiện hành | PASS | Legacy chỉ tham khảo, không dùng Gate bảo mật |
| Markdown internal links (path) | PASS | Kiểm tra lại sau khi tạo báo cáo; links anchors chưa xác thực tự động |
| `git diff --check` | PASS | exit 0 |
| Java 21 Maven wrapper `.\mvnw.cmd -o test package` | PASS | 249 tests, failures 0, errors 0, skipped 0; BUILD SUCCESS; chạy thực ngày 23/11/2026 |
| Maven shade warning | FAIL (cảnh báo cần theo dõi) | Overlapping classes/resources từ shaded dependencies, build vẫn thành công; P1/P6 rà dependency/shade |
| Nghiệm thu GUI 2–3 JVM và 2–3 PC LAN | NOT RUN | P0 chỉ chuẩn hóa; không đủ chứng cứ kết luận bất kỳ F01–F11 PASS |
| TCP/plaintext mới và bỏ security code | NOT RUN | Thuộc P1, không thực hiện trong P0 |

## Blockers/việc chuyển P1

1. **Trước khi sửa code**, dùng backup và Git diff để bảo đảm 4 spec sửa + 2 untracked vẫn còn; không mất dữ liệu.
2. Thiết kế/cài `LocalProfile` (UUID bền vững + `dataDir` override), P2P conversation ID từ cặp UUID, không giữ fingerprint-based ID.
3. Viết `WireCodec/WireMessage`, `LanSession` và `LanCoordinator` với size cap, timeout, cleanup, HELLO/WELCOME/REJECT và đúng recipient; không mở Internet.
4. Chuyển `ui.IntraChat`, `LanConnectionDialog`, `LiveChatService`, `LiveChatAdapter` và `MainChatFrame` sang **một** Live TCP backend; keep EDT safe.
5. Test thật hai JVM/profile: text/emoji Unicode hai chiều, duplicate name, sender/time, ACK/READ, disconnect/reconnect và lỗi frame. Thử hai máy LAN nếu có.
6. Chỉ sau khi P2P smoke chạy mới tháo TLS/DPAPI/fingerprint, M1 diagnostics, test/script/dependency tương ứng; chạy test/package mới và rà cảnh báo Shade.
7. Sau P1 đến P2 Group (3 peer), P3 File, P4 History local, P5 Media, P6 tổng nghiệm thu/cleanup. Không đưa mock/test quá khứ vào PASS.

**Kết luận:** P0 chuẩn hóa tài liệu và bảo vệ hiện trạng hoàn tất; sẵn sàng bắt đầu **P1 về mặt tài liệu**, nhưng chưa thể xác nhận ứng dụng đạt 11/11 Live. Không tự chuyển P1.
