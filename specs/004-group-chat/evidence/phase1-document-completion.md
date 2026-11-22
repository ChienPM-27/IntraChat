# Phase 1 documentation DoD verification — 004 Group

**Recorded:** 2026-11-22. **Scope:** T001–T009 author-deliverables, not Constitution Gate 0 security approval.
**Environment:** one Windows 11 workstation, Git branch `003-p2p-messaging`, HEAD `b8a8fd5`, worktree dirty. No unrelated source changes were made in this activity.

## Locally completed documentation deliverables

| Task | Concrete output / verification | Verdict |
| --- | --- | --- |
| T001 | [Prerequisite status](prerequisite-status.md) verifies source, Git branch/dirty worktree, M0 41/41, M1 37/60, M2 34/90 and external blockers; real Java21 baseline command/exit. | DOC DONE |
| T002 | [Clarification review](clarification-review.md) compares all six confirmed CL with Spec/Plan/source; zero Demo-as-Live claims. | DOC DONE |
| T003 | [Research threat model](../research.md) defines assets, trust boundaries, severity, attack tests, stop rules and independent reviewer required (unassigned). **No security signoff implied.** | DOC DONE |
| T004 | [Dependency inventory](dependency-security-review.md) has actual local hashes and POM license declarations but **no current independent CVE/SBOM/provenance/legal approval**. | **OPEN / BLOCKED** |
| T005 | [Source feasibility research](../research.md) traces NetworkConfig/LiveChatService/TlsChatSession caps and v1/v2 dispatch incompatibility; identifies five-peer resource risks. | DOC DONE |
| T006 | [Research](../research.md) + [contract](../contracts/group-v1.md) define deterministic owner-signed roster/grant bytes and revocation/race/replay/clock risk; local SHA256 length/hash fixtures for roster **260B** and grant **210B**. Reviewer still required **at T010**. | DOC DONE (NOT APPROVED) |
| T007 | [GROUP_V1 draft contract](../contracts/group-v1.md) describes v3 preface/negotiation, v3 authenticated transcript **250B**, pairing proof, message types, strict input limits and legacy v1/v2 negative matrix. **Reviewer signoff remains T010.** | DOC DONE (NOT APPROVED) |
| T008 | [Data model](../data-model.md) specifies owner/key/roster and current revision, Windows protected metadata only, RAM-only chat and safe restart STALE. | DOC DONE |
| T009 | [Requirements checklist](../checklists/requirements.md) and [NOT RUN quickstart](../quickstart.md) explicitly cover physical 2-host/3-peer, DPAPI other-user, five GUI cycles, no fictional test PASS. | DOC DONE |
| T010 | [Constitution check](../checklists/constitution-check.md) shows Gate 0 CONDITIONAL/BLOCKED and demands independent review/signoff. | **OPEN / BLOCKED** |

**Documentation QA actually run:** Windows PowerShell inspected all Markdown files under `specs/004-group-chat/` using relative link resolution; **0 broken links, 0 trailing whitespace lines**. `git diff --check` **exit 0**. Only doc evidence was added; tests were not run for Group code (it does not exist).

**Existing Java21 baseline actually run in this session:** `powershell.exe -NoProfile -ExecutionPolicy Bypass -File scripts/verify-m2.ps1 -Offline` after setting `JAVA_HOME` to Temurin 21.0.12.1+1. **Exit 0, JUnit 113/113**, shaded JAR diagnostics PASS. `.\mvnw.cmd -o dependency:tree -DoutputType=text` **exit 0** and read cached Maven dependency tree. This confirms M2 baseline, **not 004 Group acceptance**.

**Scope of review completion:** This is the *document authoring* DoD only. Reviewer not assigned; owner signature security/rollback/protocol handshake and license/CVE findings are **unapproved**. Gate 0 remains blocked and MUST be completed before source T011 or later.