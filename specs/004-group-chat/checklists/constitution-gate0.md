# Phase 04 Constitution I–VII — Gate 0 design assessment (T010)

**Date:** 2026-11-21 · **Outcome:** **CONDITIONAL / BLOCKED for Group wire enablement and release**. Task T010 is the act of **documenting this assessment**, not proof that external security prerequisites passed. Initial pure-Java, non-networked Group domain unit tests are permitted as preparatory engineering only. **Gate 1 NOT VERIFIED.**

| Constitution | Design / code assessment | Required release evidence |
| --- | --- | --- |
| I. LAN-first | **Design PASS, implementation OPEN:** owner relay on manual LAN M2 verified peer sockets, no Internet or fallback. Current Group domain has no networking. | ≥3 JVM/≥2 physical Win11 PCs, owner offline/handoff |
| II. Versioned messages | **CONDITIONAL:** Group V1/v3 proposal with independently signed origin/receipt; current M2 preface capability strict single entry and v2 strict codec. | T006 exact canonical vectors reviewed; T023–T036, legacy v1/v2 negative |
| III. Bounded/nonblocking | **CONDITIONAL:** code enforces immutable max 5 roster; message/queue/RAM caps and worker/EDT behavior not implemented for Group. | T087/T088/T117 and actual UI/resource measurements |
| IV. Security/privacy | **BLOCKED:** per-member signer pin and encrypted Group metadata DPAPI require actual code, independent review, cross-user tests; threat model completed. | T005 independent signoff; signatures, handoff, DPAPI, log privacy |
| V. Test-backed | **PARTIAL:** actual Java21 Maven 107 baseline then 122 JUnit PASS after new pure-domain tests, RED→GREEN recorded. No Group secure network tests. | ≥3 JVM Group tests, negative/timeout, 2-PC and full AC matrix |
| VI. Honest UX | **Design PASS / code OPEN:** receipt truth per recipient, real foreground viewport ≥250ms planned; Live Group absent. | viewer-signed READ and UI interaction tests, Demo isolation |
| VII. Spec-driven | **PASS for documents / CONDITIONAL for implementation:** 8/8 CL, spec/plan/tasks, tests, evidence and declared blockers; never claim Group Verified. | all T001–T124, AC-04-A…L and security signoff |

## Dependency review / justified partial execution

- T001–T004, T007–T009 have directly observed evidence.
- **T005 remains unchecked:** an independent, authorized security/dependency reviewer cannot be supplied by this agent. Source/cache checks recorded, but no approval fabricated.
- **T006 remains unchecked:** canonical binary signing vectors and independent interoperability review are not frozen; before security-critical Group protocol coding this is a **hard stop**.
- **T010 assessment is completed as CONDITIONAL with unresolved blockers**; it does **not** waive unfinished T005/T006, and **does not authorize Group wire/secure UI**. This limited preparatory exception is explicitly permitted by the existing Phase 04 tasks release note allowing isolated code/testing while M1/M2 acceptance is pending. T011–T018/T021–T022 implement no I/O, keys, metadata persistence or Group protocol; they can be reviewed/tested in isolation without altering existing P2P behavior.

## Release blockers / resolution

1. Independent dependency/CVE/license, crypto canonical/capability and security/privacy reviewer (T005/T006; see [blocker register](../evidence/execution-blockers.md)).
2. Two real Win11 x64 LAN hosts, actual per-inbound Accept/pairing and offline Internet validation for inherited M1/M2 and Group tests.
3. Alternate Windows user for DPAPI cross-account denial test and protected Group metadata implementation.
4. Remaining Group network, owner handoff, per-recipient signed VIEWED, frontend and 5-device tests.

**Decision:** Gate 0 design **reviewed conditional**. Isolated domain has actual JUnit evidence; secure Group wire progression is **BLOCKED** until reviewer/vector approval. No Constitution exception granted. Gate 1 and Group 004/M3 remain **NOT VERIFIED**.
