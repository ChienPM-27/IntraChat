# US5 — FE Messenger Live & separate Demo (partial)

**Date:** 2026-11-21 · **Status:** Interactive Windows local two-JVM smoke **PASS**, full FE feature audit NOT DONE.

**Live:** Explicit `ui.LiveIntraChat` opens manual listener/connect form and fingerprint approval; after both user confirmations it opens **real** `ui.frames.MainChatFrame` backed by `listener.LiveChatAdapter` and `vn.intrachat.application.LiveChatService`. TCP text/emoji flows in both directions, correct sender bubble and ACK/read state was observed. `MainChatFrame` headline shows **Live LAN (mã hóa, đã xác minh)**, footer labels `Đã xem = tin đã hiển thị`. Live mode hides new Group and Media/File/Voice controls. No Demo username/password involved. Status notifications use real event callbacks.

**Demo:** Independently launched original `java -cp target/intrachat-0.1.0-SNAPSHOT.jar ui.IntraChat` using Java21 javaw. Login screen opened with visible `Bản demo · Không kết nối máy chủ`, unchanged mock login/user seed semantics. No real Live TCP service created by Demo entrypoint. Window closed normally.

**Source isolation:** `listener.ChatActionHandler.liveMode()` defaults `false` for Demo, `LiveChatAdapter.liveMode()` returns true. `ui.IntraChat` always constructs `MockChatService`; Live constructs separate DPAPI identities and SecureChatCoordinator. FE's `model.MessageContent` now uses shared strict Unicode validation; legacy FE tests remain green.

**Open:** Dedicated Live UI automation tests of keyboard/accessibility, full copy/context/group/media hidden actions and large Unicode/viewport lists; explicit UNCERTAIN/FAILED bubble still represented with status notice, not full requested state model; no durable History by design.
