# Confirmed Clarifications — Giai đoạn 05: Chat History & LAN P2P Sync

**Feature:** `005-chat-history` · **Confirmed by user:** 2026-11-21
**Status:** **10/10 CL-05-01…10 USER CONFIRMED — ALL OPTION A**. The binding product decisions below take precedence over the former recommended A/B candidates. **This is not a dependency, security, test or release approval.**

[Specification](spec.md) · [Implementation plan](plan.md) · [Architecture decision and risk review](checklists/architecture-impact-review.md) · [Requirements checklist](checklists/requirements.md).

| ID | Trạng thái | Quyết định đã xác nhận (phương án A) | Ảnh hưởng bắt buộc |
| --- | --- | --- | --- |
| **CL-05-01** | **CONFIRMED A** | **Opt-in lần đầu**, chỉ lưu **tin mới** từ lúc đồng ý | Ban đầu DISABLED; persist consent trong user-protected metadata; không âm thầm hồi sinh M2 RAM cũ hoặc mock |
| **CL-05-02** | **CONFIRMED A** | **SQLite mã hóa toàn bộ + DPAPI CurrentUser + Windows ACL, qua review** | Không dùng SQLite plaintext; random database key riêng được bọc DPAPI; DB/WAL/temp đều được bảo vệ; chọn, thẩm định dependency và kiểm thử Windows trước khi phát hành |
| **CL-05-03** | **CONFIRMED A** | Mặc định **90 ngày hoặc 500 MiB**, **cho phép điều chỉnh** | Hai ngưỡng tuổi và tổng dung lượng cùng được áp dụng; quá một ngưỡng phải xử lý theo policy/giới hạn an toàn đã ghi, có thông báo; tombstone không được mất làm dữ liệu hồi sinh |
| **CL-05-04** | **CONFIRMED A** | **Nhấn Đồng bộ thủ công theo từng hội thoại** | Chỉ sync sau manual M2 verified reconnect, protected History handshake và đúng quyền; không auto reconnect/auto sync |
| **CL-05-05** | **CONFIRMED A** | **Chỉ hai verified device IDs trong đúng hội thoại P2P** | Không multi-device account linking; displayName hoặc TLS pairing với peer ngoài cuộc không đủ quyền |
| **CL-05-06** | **CONFIRMED A** | **Chỉ owner Group hiện hành** phục vụ History, đúng cửa sổ quyền JOIN→REMOVE | Owner/epoch/revision/proof phải verified; không trước JOIN, không cấp thêm sau REMOVE; owner offline/handoff ambiguous → PAUSED |
| **CL-05-07** | **CONFIRMED A** | **Xóa cục bộ + tombstone chống reimport**, không remote delete | Durable tombstone/conversation deletion fence; peer khác có thể tiếp tục giữ bản sao cũ; không hứa remote wipe |
| **CL-05-08** | **CONFIRMED A** | **Không lưu hoặc đồng bộ application ACK/READ receipts** | Lưu text/emoji + signed provenance và trạng thái riêng SAVED/SYNCED; receipt M2/Group chỉ sống ở runtime và không được suy diễn qua History |
| **CL-05-09** | **CONFIRMED A** | **Immutable events; cùng ID khác content/signer thì quarantine**, không overwrite | Bảo toàn sender/time/content; không last-write-wins theo clock; local tombstone ngăn sự kiện tự hồi sinh |
| **CL-05-10** | **CONFIRMED A** | **Không backup/export tự động; chỉ migration schema an toàn** | Không sinh bản sao plaintext hay import/export workflow; migration cần rollback/atomicity và không tự xoá store lỗi |

## Ràng buộc kế thừa — không thay đổi

- M1 plaintext chỉ cho diagnostics. History chỉ dùng M2 TLS đã mutual verified/human consent, post-TLS negotiated History capability và exact-conversation ACL.
- Group 004: member mới không được backfill tin trước JOIN commit; member bị REMOVE không nhận thêm History sau REMOVE commit; owner/handoff phải chứng minh signed state.
- SAVED/SYNCED là trạng thái sao chép History, **không phải** DELIVERED/READ. Không lưu, không replay receipt; không tự gửi draft/outbox.
- Việc phê duyệt SQLite là **lựa chọn sản phẩm**, không phải bằng chứng native SQLCipher/JDBC an toàn: driver/version/license/CVE/provenance, encryption của WAL/temp, Windows cross-user DPAPI/ACL và threat model vẫn cần review và test độc lập. Nếu không có backend SQLite mã hóa đạt yêu cầu thì **BLOCKED, không fallback plaintext**.
- Giai đoạn 004, M1/M2 Gate 1 vẫn chưa VERIFIED; M3 chỉ đạt khi **004 và 005** đều VERIFIED.

## Phân biệt những gì đã được chốt và chưa thực hiện

**Đã chốt:** cả 10 CL, ranh giới quyền truy cập/lưu/xóa/đồng bộ, hành vi user-facing và architecture direction SQLite encrypted.

**Chưa phê duyệt kỹ thuật/chưa thực hiện:** native encrypted SQLite artifact/cryptographic vectors, history wire schema freeze và independent security signoff; Java History classes, DB, tests, 2 physical Windows LAN PCs và Group E2E. Các giới hạn batch/page/timeouts trong [plan](plan.md) là **design targets**, không phải benchmark đã PASS.
