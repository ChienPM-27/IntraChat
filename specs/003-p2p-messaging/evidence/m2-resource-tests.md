# M2 — Partial local secure resource and RAM-bound test evidence, 2026-11-22

**Status:** **PARTIAL. T078 remains OPEN**, because 4 pending TLS approvals / 16 frames+2MiB actual TLS queued writes / half-open / full EDT p95 matrix and independent audit have NOT all been run.

**Observed test proofs on actual Temurin Java21:**

| Test | Result | Scope |
| --- | --- | --- |
| `SecureSessionResourceTest` | **1/1 PASS** | 4 simultaneous protected TLS peers allowed; fifth denied; closing a peer reclaims the slot; fifth then admitted. Both ends' asynchronous READY/attach awaited before assertions. |
| `SessionRamPrivacyTest` | **2/2 PASS** | 160 concurrent sends over real TLS with withheld ACK, **128 admitted, 32 rejected**, 128 pending slots accounted, all released on close with transcript/attempt erased. |
| `ConversationRamBudgetTest` | **3/3 PASS** | Exactly 500 messages accepted, 501st rejected; 2MiB exactly accepted, extra byte rejected; concurrent reservation maintains bounds. |
| `RecentMessageIdsTest` | **2/2 PASS** | 1024 IDs maximum, oldest evicted, duplicate inside window suppressed, session close resets cache. |
| `AckDeadlineTest` | **2/2 PASS** | Monotonic 30s fake-clock uncertainty without auto resend and without downgrading true authenticated ACK/READ. |
| `LiveReceiptVisibilityTest` | **2/2 PASS** | Earlier UNCERTAIN outgoing Live messages retain manual retry UI; Demo behavior unchanged. |
| `LiveChatAdapterContractTest` | **2/2 PASS** | Actual TLS callbacks and initial directory on Swing EDT; ACK does not fabricate foreground VIEW/READ. |
| `OutboundBudgetTest` from M1 | **3/3 PASS** | 16 frames / 2MiB quota for untrusted v1 diagnostic TCP, **NOT proof** that M2 TLS has the same writer queue. |

The M2 active protected-text upper bound follows from 4 active peers times 2MiB RAM text each = **8MiB**. Atomic `LiveChatService.attach`, per-peer `ConversationRamBudget`, 128 `pendingReceiptCount` semaphore, and 1024 `RecentMessageIds` bound the core service. This is **not** a full TLS socket/queue/GUI/real LAN overload audit.

**Unresolved:** M2 T078 adversarial stress on full transport, half-open saturation/EDT latency, real two-PC LAN, independent security/resource reviewer, Constitution Gate1. [Code/test evidence](m2-local-code-2026-11-22.md).