# Constitution I–VII and File 006 technical Gate 0 — observed status

**Date:** 2026-11-21 · **User CL:** 10/10 confirmed (9 A, CL-06-03 B). **Production File Gate 0 NOT PASSED.** [Execution blockers](../evidence/execution-blockers.md) · [TDD evidence](../evidence/trace-and-tdd.md).

| Principle | Assessment | Evidence gap |
| --- | --- | --- |
| **I — LAN-first** | DESIGN PASS; release OPEN | no actual File P2P on two distinct physical Win11 peers without Internet |
| **II — Protocol-first** | **BLOCKED T004** | exact FILE_V1 version/canonical wire/protected capability transcript/replay golden vectors not independently reviewed |
| **III — Bounded/async** | PARTIAL | 256 MiB pure bound and 32KiB read-only prehash buffer tested; real shared TLS frame/queue/EDT not measured |
| **IV — Security/privacy** | **BLOCKED T004/T005/T011** | independently reviewed File authentication and Win11 Downloads atomic no-clobber/ACL/reparse missing |
| **V — Test-backed** | PARTIAL | actual 27 new Java21 File domain/read-only helper tests PASS, 165 full suite PASS; no secure File network or Windows finalizer tests |
| **VI — Honest Live UI** | DESIGN ONLY | per-offer consent with Downloads root and truthful completion; Live real File UI not implemented |
| **VII — Spec-driven** | PLANNING PASS, runtime OPEN | 10 CL, 160 tasks, 8 US, 56 FR, 14 SC, 16 AC traced; no release acceptance |

| Gate | Task | Status |
| --- | --- | --- |
| **G0.0** Product choices | User confirmed | **DONE 10/10** |
| **G0.1** File protocol/crypto | **T004** | **BLOCKED / independent review NOT EVIDENCED** |
| **G0.2** Windows FS/ACL/no-clobber | **T005** | **BLOCKED / independent review NOT EVIDENCED** |
| **G0.3** Group owner signature runtime | external 004 | **BLOCKED** (18/124 pure-domain tasks) |
| **G0.4** Source budget review | **T007** | **Design documented**, full measurements NOT RUN |
| **G0.5** Independent joint go/no-go | **T011** | **BLOCKED** due T004/T005 and absent signoff |
| **Gate 1** 2 physical Win11 LAN/security | **T143–T144** | **NOT RUN / NOT EVIDENCED** |

**Allowed evidence:** pure policy/identity and read-only local source checks. **Not allowed to claim:** protected FILE_V1 codec, encrypted file stream on LAN, durable safe Downloads write, Group signed fanout, independent release approval, 006 or M4 Verified.
