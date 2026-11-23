# Verified application view event → READ_RECEIPT

**Date:** 2026-11-21 · **Status:** Local Java21 and real Windows two-process GUI verified for key foreground cases, not full a11y or two-device acceptance.

- `ui.components.LiveMessageVisibilityObserver` on Swing EDT checks Live mode, active selected conversation, **incoming** bubble is showing, `JViewport.getViewRect()` intersects row bounds and stable visible duration ≥250 ms, fires one logical event per message.
- Recipient sends distinct authenticated `READ_RECEIPT` only after above UI event (not TCP receive/ACK). `LiveChatService` validates peer/conversation/session/original message on sender.
- `Đã nhận` is application ACK, not a read; `Đã xem` is **the other application displayed the message**, not proof human eyes looked.
- **Actual GUI regression caught:** relying solely on Swing `isActive()` incorrectly emitted read while receiver Java window in background. Implemented `WindowsForegroundGuard`, comparing native `GetForegroundWindow()` HWND to owning Swing window. On OS check failure **do not** emit read.
- **Actual two-process re-test:** B→A sent while A FE window background. B observed **Đã nhận**, NOT Đã xem. After bringing A FE window to OS foreground, B advanced to **Đã xem**. Then A→B message while B background: A showed **Đã nhận**, then after foregrounding B, A advanced to **Đã xem**. Both messages visibly appeared on receiver, no fake Demo data.
- Unit tests `ui.LiveMessageVisibilityTest`, `ui.WindowsForegroundGuardTest` pass within Java21 JUnit suite.

**Remaining:** test multiple conversations and preview/notification, occlusion within same foreground app, screen readers, multiple physical monitors and OS focus corner cases. Receipt does not prove human cognition; no offline auto queued read.
