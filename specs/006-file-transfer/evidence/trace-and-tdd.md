# Phase 06 product trace, Java21 RED→GREEN test matrix and QA

**Date:** 2026-11-21 · **Task coverage:** T002, T009, T012 (source trace/checks) and test-phase evidence catalog. **CL-06-01…10 USER CONFIRMED: 9 A; only CL-06-03 B.** [Requirements checklist](../checklists/requirements.md) · [Tasks](../tasks.md).

## Product choices → implementation/test/AC

| Confirmed product choice | Task streams | Source tests / future gate | Acceptance |
| --- | --- | --- | --- |
| 06-01 A, 0..268435456 byte, SHA256 empty | T015–T016, T025–T026, T069–T070, T071–T074 | `FileOfferBoundsTest`, `FileEmptySha256Test`, `FileSenderPrehashTest`; no secure receiver yet | AC-A/I |
| 06-02 A, explicit consent each offer | T019–T020, T093–T094, T113–T114 | `FileOfferConsentTest` only pure predicate so far; real protected OFFER not present | AC-E |
| **06-03 B**, preapproved Downloads root | T051–T054, T067–T068, T109–T110 | Need independently reviewed Win11 root authorization/no-follow; root ≠ per-offer Accept | AC-D/E/G/L |
| 06-04 A, no overwrite | T055–T064, T069–T070 | Win11 atomic no-clobber + junction race review BLOCKED | AC-G/L |
| 06-05 A, no resume | T027–T028, T065–T066, T085–T090 | `FileRetryNoResumeTest` pure PASS; production staging cleanup NOT RUN | AC-B/C/L |
| 06-06 A, source change fails | T071–T076, T097–T098 | `FileSenderPrehashTest` and `SourceMutationStreamingTest` PASS; source handle/file relay security still unreviewed | AC-H |
| 06-07 A, owner-mediated Group | T029–T030, T123–T136 | `GroupFileEligibilityTest` pure PASS; **004 signed runtime BLOCKED** | AC-M/P |
| 06-08 A, distinct FILE_V1 same TLS | T033–T050, T091–T108 | Canonical file wire transcript/protocol version review BLOCKED | AC-J/K/F |
| 06-09 A, RAM-only | T023–T024, T027–T028, T031–T032, T121–T122 | `FileProgressSnapshotTest`, `FileRetryNoResumeTest`, `FileNoHistoryReceiptTest` pure PASS; no real persistence path | AC-C/N |
| 06-10 A, warn/manual Open | T017–T018, T119–T120 | `FileDisplayBasenameTest` pure PASS; real OS warning UI NOT RUN | AC-D/N/O |

**Coverage invariant:** spec has 8 US (US1…US8), **56 FR-06 (001…056)**, **14 SC-06 (001…014)**, **16 AC-06 (A…P)**, and ROADMAP 06.01–08 all unchecked. Task IDs 001…160 contiguous. All test work uses synthetic bytes, never private chats/actual user files. The model `Attachment.sizeBytes>0` in Demo is unchanged; the new File domain accepts zero bytes.

## RED→GREEN evidence (actual executions, not simulated)

All Maven commands were executed on Temurin **Java21.0.12.1+1** on Windows build 26100, from isolated `006-file-transfer` worktree (initial commit `d01fcc0`); `-o` uses local Maven dependencies. Maven exit codes observed:

| Step | Exact command or test list | Actual outcome |
| --- | --- | --- |
| Baseline | `.\mvnw.cmd -o test` before new File classes | **138 tests PASS**, exit **0**, BUILD SUCCESS |
| File-domain RED | `.\mvnw.cmd -o '-Dtest=VerifiedFileScopeTest,FileOfferBoundsTest,FileDisplayBasenameTest,FileOfferConsentTest,FileTransferStateTest,FileProgressSnapshotTest,FileEmptySha256Test,FileRetryNoResumeTest,GroupFileEligibilityTest,FileNoHistoryReceiptTest' test` | Maven exit **1**, test compilation **cannot find symbol** for missing 006 File domain classes. Tests were written before production classes |
| File-domain GREEN | Same focused `-Dtest=…` invocation after 12 new pure classes | **20/20 JUnit PASS**, Maven exit **0**, BUILD SUCCESS (10 classes × 2 tests) |
| Source/timeout RED | `.\mvnw.cmd -o '-Dtest=FileSourceReaderTest,FileSenderPrehashTest,SourceMutationStreamingTest,FileOfferIdleTimeoutTest' test` | Maven exit **1**, compilation **cannot find symbol** for missing application/source/deadline classes. Package imports aligned without changing assertion logic |
| Source/timeout GREEN | Same 4 focused `-Dtest=…` after 4 new isolated application helper classes | **7/7 JUnit PASS**, Maven exit **0** |
| Full regression | `.\mvnw.cmd -o test` after above features | **165/165 JUnit PASS**, Maven exit **0**, BUILD SUCCESS (138 baseline + 27 File) |
| Package verification | `.\mvnw.cmd -o package` | **165/165 JUnit PASS, shaded artifact built**, Maven exit **0**, BUILD SUCCESS (Java21, 2026-11-21) |

**New test classes**: `VerifiedFileScopeTest`, `FileOfferBoundsTest`, `FileDisplayBasenameTest`, `FileOfferConsentTest`, `FileTransferStateTest`, `FileProgressSnapshotTest`, `FileEmptySha256Test`, `FileRetryNoResumeTest`, `GroupFileEligibilityTest`, `FileNoHistoryReceiptTest`, `FileSourceReaderTest`, `FileSenderPrehashTest`, `SourceMutationStreamingTest`, `FileOfferIdleTimeoutTest`.

**Nature of GREEN:** `domain/file/` contains *predicate/value-state logic* (not authenticated session, file sender, receiver, source signature or Windows safe write). `application/file/` contains bounded **read-only local source inspection/prehash/source recheck and deadline predicates**, not a production transfer service or a write to Downloads. The synthetic tests are not File V1 on-wire E2E or signed Group/Win11 atomic commit tests.

**Task-mapping of actual code/tests:** T013–T032 (20 domain RED→GREEN); T071–T076 (6 read-only source tasks); T083 RED completed (T084 helper tested GREEN but remains unchecked pending T082). Other RED/GREEN pairs in `tasks.md` are UNCHECKED due dependency/security gates. No real File transfer or physical two-PC test has been executed.

## Dependency and QA stop criteria

- **T004/T011 independent protocol approval** is prerequisite for new File capability, binary wire and actual TLS session dispatcher. The candidate `protocolVersion=5` contract remains DRAFT, no canonical signed vectors.
- **T005/T011 independent Win11 FS review** is prerequisite for writing user Downloads, safe no-clobber, or orphan deletion. No code for that has been enabled.
- **Group 004 verified runtime** is prerequisite for T123–T136/AC-M; pure `GroupFileEligibility` predicate is not signed owner authority.
- T143–T144 physical Win11 LAN and independent security reviewer not evidenced; one Windows environment was observed, not a second distinct physical Windows peer. 2-JVM tests and unit tests cannot satisfy release.

**Audit:** Every implemented task must have a checked ID and actual evidence; blocked task remains unchecked. Do not claim Gate 0, Gate 1, AC-06-A…P, 006 or M4 Verified.
