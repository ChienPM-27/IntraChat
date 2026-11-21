# M2 Confirmed Clarifications — Implementation Review

**Date:** 2026-11-21 · **Status:** Product mapping documented, security Gate 0/1 incomplete.

| Decision | Implementation trace | Current gap |
| --- | --- | --- |
| **CL-03-01** fingerprint first pair, remember device | ECDSA device cert, DPAPI identity and SPKI store, two-sided human SAS GUI and PAIR_CONFIRM/READY over TLS1.3; remembered pin across process restart, still manual Accept inbound | Custom TLS first-contact trust handling independent review, compromised-key UX, Windows multi-account tests |
| **CL-03-02** draft only RAM / resend manual | FE composer keeps draft while UI open; coordinator `connect` is explicit, no reconnect timer or offline send queue | App-level uncertain-send manual resend UI/duplication warning not fully completed |
| **CL-03-03** READ on real UI view event | Distinct app ACK vs READ, foreground Windows HWND guard, selected conversation, visible bubble/viewport ≥250ms, authenticated receipt | Preview/occlusion/multi-chat/replay negative suite still incomplete |
| **CL-03-04** only session chat persisted, trust separate | Chat/attempt/receipt in RAM-only `LiveChatService`; DPAPI store holds device key/pin, no transcript | Full disk forensic/log/privacy audit still pending |
| **CL-03-05** Messenger FE Live + separate Demo | `ui.LiveIntraChat` with `LiveChatAdapter` and real M2 transport, `ui.IntraChat` still `MockChatService`; UI visually labels modes | Full FE uncertainty/error bubble, accessibility and all future feature controls audits |

Constitution and test release blockers tracked in [constitution-check](../checklists/constitution-check.md) and [acceptance](m2-acceptance-report.md). M1 Gate 1 remains open. This mapping is **not** proof of production-grade cryptography or M2 Verified.
