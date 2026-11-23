# US4 — Real protected delivery ACK vs Live view-based READ

**Date:** 2026-11-21 · **Status:** Local GUI positive/negative foreground tests **PASS**, full negative/fault matrix and two physical Windows PCs **NOT RUN**.

**Automated:** `LiveChatServiceTest.genuineMessageDeliveryAckThenExplicitViewReceiptOverMutualTls` exercises real JSSE mutually authenticated TLS1.3 loopback. B sends text/emoji, A receives actual text, B becomes `APP_ACKNOWLEDGED` only after A accepted message into RAM and sent app ACK; calling `markViewed` with an unrelated conversation fails; only the correct incoming message/peer ID can produce a protected read receipt; B then becomes `READ`. `MessageAttemptStateTest` includes receipt-before-ACK and early-ACK/write race. `LiveMessageVisibilityTest` proves foreground/viewport/250ms logic with a fake clock.

**Actual human-operated Windows FE test (two independent JVMs, one physical PC):**
- Initial B→A text/emoji sent and received in real Live chat, but first implementation showed premature `Đã xem` when receiving GUI was background. **Observed defect**; NOT counted PASS.
- **Fix:** added native Windows HWND foreground guard to supplement Swing window active state. Rebuilt JAR, 72 then 73 Java21 tests passed.
- **Repeat B→A:** while A FE window was background, B showed **`Đã nhận`**, NOT `Đã xem`. After explicitly focusing A GUI, B changed to **`Đã xem`**.
- **Reverse A→B:** while B was background, A showed **`Đã nhận`**, NOT `Đã xem`. After explicitly focusing B GUI, A changed to **`Đã xem`**.
- Local timestamps and sender bubble direction were visually checked. No mock chat traffic was used.

**Meaning:** `Đã xem` means recipient **application reported a visible foreground message**, **not** verifiable human gaze/understanding. Authenticated receiver can choose to report misleading view events; no claim otherwise.

**Open:** preview/notification, occlusion with overlaid same-app windows, multiple chat switches, wrong-session forged receipts and replay flood, uncertain-delivery bubble rendering, manual resend/reconnect workflow, long-running resource soak, actual two Windows LAN PCs. No offline read-receipt outbox exists.
