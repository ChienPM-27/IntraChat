# Specification Quality Checklist — 003 P2P Messaging (M2)

**Date:** 2026-11-21
**Scope:** [spec.md](../spec.md) — Giai đoạn 03 / M2 Chat P2P
**Review status:** **CLARIFIED — 5/5 product decisions CONFIRMED; ready for planning, NOT for implementation**
**Rule:** Checklist reviews **the specification document**, not implementation evidence. Checked items below mean the text is specified or stakeholder decision confirmed, **not that code or tests have run**.

## Confirmed Clarifications — CL-03-01…05

- [x] **CL-03-01 — First-time fingerprint pairing:** Hai đầu **so khớp mã/fingerprint lần đầu**, chỉ sau xác minh mới cho phép chat trên kênh bảo vệ; **ghi nhớ fingerprint/credential peer qua restart** trong kho bảo vệ. Key changed/revoked → block and reverify. Human inbound Accept/Reject theo M1 **vẫn bắt buộc mỗi lần**; trusted peer không được tự auto-accept.
- [x] **CL-03-02 — Offline behavior:** Giữ **bản nháp chỉ trong RAM phiên**, không offline outbox, không auto resend; sau khi chủ động reconnect thì **người dùng phải bấm Gửi/Gửi lại**. Đóng ứng dụng thì mất nháp, UI cảnh báo khi cần.
- [x] **CL-03-03 — Actual view-based `Đã xem` (expanded scope):** Chỉ sau khi tin được **thực sự hiển thị ở viewport của hội thoại Live đang mở/foreground** và peer phát **read receipt riêng, có xác thực và correlation đúng** mới được nâng trạng thái. **Application delivery ACK ≠ read receipt**; preview, notification, background, demo mock, receipt giả/sai/trùng không tạo `Đã xem`. Receipt là bằng chứng *ứng dụng hiển thị*, không chứng minh con người đọc bằng mắt.
- [x] **CL-03-04 — Ephemeral chat:** Nội dung hội thoại, bản nháp, trạng thái tin, delivery ACK/read receipt chỉ trong **bộ nhớ phiên**; thoát app không giữ History. **Ngoại lệ riêng:** credential/fingerprint đã ghép cặp vẫn ghi nhớ qua restart theo CL-03-01; tuyệt đối không đưa nội dung tin vào trust store.
- [x] **CL-03-05 — FE Live + separate Demo:** Dùng **Messenger FE** làm UI Chat thật với network service; giữ **Demo riêng**, có nhãn rõ. Không trộn/mock-send dữ liệu, demo password, `Đã nhận/Đã xem` giả vào Live.
- [x] Năm quyết định đã được đưa vào user stories, edge cases, FR/NFR, entities/state model, AC-03-A…L, SC-03-001…010 và bảng ranh giới/traceability. **Không còn câu hỏi product Clarify nào trong CL-03-01…05.**

## Content and Requirement Completeness

- [x] Spec maps to ROADMAP 03.01–03.09: P2P, Sender, Time, State (**có Đã xem thật**), Emoji
- [x] Six prioritized user stories contain independent tests and Given/When/Then acceptance cases; US1 gồm **pinning/restart/key change**, US4 gồm **foreground/view event/read receipt**, US5 gồm **FE Live/Demo**
- [x] FR-001…FR-040 remain individually labeled with explicit MUST/SHOULD; five confirmed decisions are directly traceable to relevant FR
- [x] AC-03-A…L specify observable outcomes; **AC-03-E/G** distinguish delivery ACK and read receipt, **AC-03-F** enforces pairing, **AC-03-I** verifies FE Live/Demo and RAM-only messages
- [x] SC-03-001…010 measurable within the declared suites; read receipt positive and negative cases are explicitly measured
- [x] Unicode/emoji, timezone/clock skew, duplicate names/IDs, ACK/receipt replay/out-of-order and disconnect handling are addressed
- [x] Transport state, consent, verified identity, protected channel, delivery ACK and **READ** are separate concepts
- [x] **No durable History**, background outbox, auto reconnect, auto send or fake read receipt is promised
- [x] Peer trust/fingerprint persistence is explicitly distinct from chat/message/draft/receipt persistence
- [x] User chat is **blocked on M1 plaintext/unverified diagnostic transport**, and spoofed/mismatched keys, ACK and read receipts are rejected
- [x] Inbound manual consent and manual IP/port decisions CL-02-01…04 remain unchanged
- [x] FE mock demo and live service identities/read states are not conflated; demo login is not authentication
- [x] Out-of-scope Group (004), History Sync (005), File (006), Media (007), cloud/calls remain clear; **actual view-based read receipts are IN scope**
- [x] Constitution I–VII and M1 Gate 1 dependency remain explicit. M1 In Progress (36/60), two real Windows LAN PCs not yet verified
- [x] No M2 network code, executed tests, `plan.md`, `tasks.md` or Verified status is asserted

## Open Technical Planning / Execution Gates (NOT completed by Clarify)

- [ ] **Technical design (plan only):** chọn thuật toán và cơ chế authenticated encryption/integrity; fingerprint/SAS presentation, first-time comparison, device-key lifecycle, protected user-local trust storage, revocation/change-key, downgrade resistance
- [ ] **Technical design (plan only):** versioned schema/types for `CHAT_TEXT`, delivery ACK và **read receipt độc lập**, authentic peer/session/conversation/message binding, replay/dedup, out-of-order, view-event criteria and timeout/resource caps
- [ ] **Technical design (plan only):** FE Live service adapter, Demo isolation, chat RAM-only lifecycle, protection of trust metadata and deletion flows; no hidden persistence of text/drafts/read receipts
- [ ] **Constitution Gate 0 review:** I–VII tradeoffs/security assessment must be reviewed in future `plan.md`
- [ ] **M1 Gate 1:** real dual-Windows LAN and remaining M1 acceptance evidence must be Verified before M2 release
- [ ] **M2 tasks/test-first implementation:** write `tasks.md` after plan; RED→GREEN implementation and test evidence are not part of this request
- [ ] **M2 acceptance:** AC-03-A…L including real two-PC chat/read receipts executed and independently verified (NOT RUN)

**Conclusion:** **CLARIFIED — 5/5 confirmed.** The M2 product behavior can proceed to **Plan**, but M2 remains **not implemented, not Verified** and M1 Gate 1 remains open. **No `plan.md`, `tasks.md` or implementation changes are created as part of this Clarify update.**
