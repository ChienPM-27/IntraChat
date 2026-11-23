# FE Messenger Live versus Demo integration audit

**Date:** 2026-11-21 · **Status:** Real local GUI integration PARTIAL (not final acceptance).

Previous `ui.IntraChat` starts `ui.frames.LoginFrame(new MockChatService())`; demo login/password, seeded conversations, timed fake `SENT/DELIVERED/SEEN`, future Group/Media controls and `loadHistory` name were all **mock**, not real peer authentication.

M2 additions:
- `ui.LiveIntraChat` new **separate explicit Live entrypoint**, no demo LoginFrame credentials; Windows local identity/trust via DPAPI.
- `ui.frames.LiveConnectionFrame` offers manual IPv4:port listener/connect, inbound person Accept/Reject per connection and out-of-band matching SAS fingerprint confirmation at both ends.
- `listener.LiveChatAdapter` binds **real `LiveChatService`** events (protected sender/time/text/APP_ACKNOWLEDGED/READ) to existing FE `ChatActionHandler/ChatClientListener` and RAM snapshot; mock service never backs Live.
- `ui.frames.MainChatFrame` recognizes `actions.liveMode()`, labels Live, hides Group creation; `ComposerPanel.setTextOnly` hides File/Image/Video/Sound controls in Live. `ui.IntraChat` Demo remains separate.
- `ui.components.LiveMessageVisibilityObserver` plus `WindowsForegroundGuard`: real foreground-window + selected conversation + inbound bubble within viewport ≥250ms; event mapped to protected read receipt. No read from Demo.
- `model.MessageContent` text now validates codepoints/bytes consistently with `ChatText`.

Actual Windows GUI: **two independent Live javaw processes** with separate `-Dintrachat.secureRoot=...` directories, human Accept, initial matching SAS and explicit Confirm on both, Messenger FE Chat Live launched, real bidirectional text and correct delivery/read observed; original Demo standalone GUI launched separately and still displayed `Bản demo · Không kết nối máy chủ`.

**Open:** Full FE keyboard/a11y, race/cleanup, manual resend after disconnect and explicit per-message `UNCERTAIN/FAILED` bubble state not fully implemented; continued development needed. Demo password must never be used for Live auth.
