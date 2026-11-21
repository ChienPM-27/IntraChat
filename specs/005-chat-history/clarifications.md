# Clarification candidates — Phase 05 Chat History

**Feature:** `005-chat-history` · **Date:** 2026-11-21 · **Decision authority:** user
**Status:** **10/10 PENDING.** The defaults below are suggestions for review only, **not user-confirmed**, not implementation authorization. [Feature spec](spec.md) · [Provisional plan](plan.md).

| ID | Câu hỏi | Gợi ý A (đề xuất; chưa duyệt) | Phương án B | Hệ quả nếu đổi |
| --- | --- | --- | --- | --- |
| CL-05-01 | Bật History khi nào? | **A: Opt-in** lần đầu, lưu tin từ thời điểm đồng ý | B: Bật mặc định khi nâng cấp, có thông báo | Consent UI, privacy, migration |
| CL-05-02 | Lưu/mã hóa ra sao? | **A: SQLite được mã hóa toàn bộ**, DPAPI CurrentUser bọc key, ACL; SQLCipher-compatible driver **chỉ sau review** | B: Encrypted append-only page/event store | Index, atomicity, migration và ADR |
| CL-05-03 | Retention/quota? | **A: 90 ngày hoặc 500 MiB**, cho chỉnh và cảnh báo | B: Giữ đến lúc user xóa, có hard disk cap | Compaction, tombstone, quota tests |
| CL-05-04 | Sync sau reconnect? | **A: Nút Đồng bộ thủ công**, theo conversation, verified READY | B: Auto sync ở verified READY sau opt-in một lần | Consent, scheduling, throttling |
| CL-05-05 | Phạm vi P2P? | **A: Chỉ hai verified deviceId của cặp P2P** | B: Multi-device cùng user được liên kết danh tính | Account, ACL/revocation model |
| CL-05-06 | Nguồn Group History? | **A: Owner hiện hành**, signed epoch/roster, chỉ sự kiện trong JOIN→REMOVE | B: Authorized member-to-member replication | Trust, member proof, owner-offline behavior |
| CL-05-07 | Phạm vi xóa? | **A: Local delete + durable tombstone** chống reimport, không hứa remote wipe | B: Signed remote delete request | Remote consent/receipts/legal semantics |
| CL-05-08 | Lưu/sync ACK/READ? | **A: Không persist/sync receipt ACK/READ**, lưu content/provenance và trạng thái SAVED/SYNCED riêng | B: Lưu signed receipts có kiểm quyền | Privacy, evidence and replay policy |
| CL-05-09 | Conflict event ID? | **A: Immutable event, quarantine conflict**; tombstone chặn nhập lại | B: Versioned editing/deleting merge | Merge algorithm, causal authority |
| CL-05-10 | Backup/export? | **A: Không tự backup/export**, chỉ safe schema migration | B: User-initiated encrypted export/import | Key recovery, import conflicts, consent |

## Non-negotiable inherited guards

M1 plaintext is diagnostics-only; sync requires authenticated pinned TLS, protected History capability confirmation, per-conversation authorization and source provenance. CL-04-07 **no pre-JOIN backfill** and CL-04-08 **no new History sync to removed peers** are binding. Saved/synced never produces fabricated ACK/READ. Group 004 and M1/M2 Gate 1 are currently not verified.

The user should confirm A/B or a custom answer for each CL. Only then update `spec.md` to **CONFIRMED**, and resolve conditional ADRs in `plan.md`. The presence of these proposed defaults is **not** user consent.
