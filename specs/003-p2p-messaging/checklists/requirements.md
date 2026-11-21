# Specification Quality Checklist — 003 P2P Messaging (M2)

**Date:** 2026-11-21
**Scope:** [spec.md](../spec.md) — Giai đoạn 03 / M2 Chat P2P
**Review status:** **DRAFT — ready for stakeholder clarification, NOT ready for implementation**
**Rule:** Checklist reviews **the quality of this spec**, not completed product behavior. Checked items below are text/document checks, **not tested software**.

## Content & consistency

- [x] Feature scope maps to ROADMAP 03.01–03.09: P2P, Sender, Time, State, Emoji
- [x] Six prioritized user stories contain independent-test descriptions and Given/When/Then acceptance scenarios
- [x] Out-of-scope Group (004), History Sync (005), File (006), Media (007), call/cloud is explicit
- [x] All previously confirmed CL-01-01…04 and CL-02-01…04 are preserved without silently changing M1
- [x] M1's actual partial status (36/60 tasks, local test vs no real two-PC LAN proof) is disclosed
- [x] FE `ui.IntraChat` mock demo distinguished from live `vn.intrachat.IntraChatApplication`; demo login/read receipts are NOT real
- [x] Existing Constitution Principles I–VII are referenced and mapped to explicit gates/requirements
- [x] No source code, implementation tests or completed M2 tasks claimed in spec

## Requirement quality

- [x] FR-001…FR-040 have explicit MUST/SHOULD statements and defined behaviors
- [x] AC-03-A…L map to observable outcomes and traceable FR/stories
- [x] SC-03-001…010 are measurable **within their stated test scope**
- [x] Unicode/emoji, timestamp timezone/clock skew, duplicated display names, mismatch/unknown peer, ACK/no-ACK and disconnect are included
- [x] There is a clear distinction among transport state, peer verification, channel protection and message delivery state
- [x] No `read` receipt, durable History, offline outbox or automatic retry is promised
- [x] No true chat is allowed over unauthenticated/plaintext M1 diagnostic channel
- [x] Rejected/invalid/oversized/malformed messages, cancellation, bounded workers and cleanup are testable requirements
- [x] Two real Windows PCs without Internet, plus loopback and security negative tests, are release gates

## Pending clarification / blockers (not checked)

- [ ] **Stakeholder confirmation** of P-03-01: human peer verification and authenticated encrypted chat before exposing real content
- [ ] **Stakeholder confirmation** of P-03-02/03: manual retry/no offline outbox, ACK semantics, no read receipts in MVP
- [ ] **Stakeholder confirmation** of P-03-04/05: ephemeral chat messages and how live chat is separated from/integrated with FE mock
- [ ] **Plan** selects and reviews concrete trust/credential lifecycle, cryptographic channel, protocol version/handshake, ACK correlation, failure policy, size/timing limits and secure key storage
- [ ] **Constitution Gate 0 plan review** completed, with I–VII trade-offs and security assessment
- [ ] **M1 Gate 1 Verified** on two physical Windows LAN computers
- [ ] **M2 tasks/test plan** and RED→GREEN implementation evidence (not part of this spec-writing task)
- [ ] **M2 acceptance** AC-03-A…L executed and independently verified (not part of this spec-writing task)

**Conclusion:** Feature specification is documented and traceable. **M2 remains not implemented, not Verified**. Its suggested product defaults are explicitly marked as proposals and require approval or revision during Clarify/plan. No premature move of M1 or M2 to Verified.
