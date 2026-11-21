# T008 — Java21 RED→GREEN / release test matrix

**Baseline executed:** Temurin JDK 21.0.12.1, `.\mvnw.cmd -o test` at 005 commit `9acfb82`: **122 tests PASS, exit 0**. These are *old* baseline tests, not History tests.

| Test stream | Proposed test classes / required negative checks | Tasks | Actual status now |
| --- | --- | --- | --- |
| Scope, event, duplicate, consent, Group eligibility, state, retention | `HistoryConversationKeyTest`, `HistoryEventTest`, `HistoryEventDeduplicatorTest`, `HistoryConsentAndTombstoneTest`, `GroupHistoryEligibilityTest`, `HistoryCopyStateTest`, `HistoryRetentionPolicyTest` | T011–024 | NOT RUN initially |
| Canonical provenance and ACL | `HistoryCanonicalEventTest`, `HistoryOriginSignatureTest`, `P2PHistoryAuthorizationTest`, `GroupOriginProofTest` | T025–036 | BLOCKED by independent vectors for crypto, 004 for Group |
| Encrypted SQLite/DPAPI/ACL/migration | `HistoryDbKeyProtectionTest`, `EncryptedSideFileTest`, `HistoryWindowsAccessTest`, `HistorySchemaMigrationTest` | T037–052 | BLOCKED by approved cipher driver/native/security review |
| Paging/tombstone/retention/no receipts | `HistoryKeysetPaginationTest`, `HistoryTombstoneRestartTest`, `HistoryNoReceiptsOrBackupTest` | T053–064 | Requires encrypted DB, may test pure helpers separately |
| Strict profile and auth handshake | `HistoryCapabilityTranscriptTest`, `HistoryV1CodecTest`, `HistoryV1MalformedInputTest`, `HistoryFrameFuzzTest` | T065–080 | BLOCKED by signed wire vector/profile review |
| Real P2P M2 verified Sync | `HistoryTwoPeerTlsTest`, `HistoryOutsiderPrivacyTest`, `HistoryResumeTest` | T081–096 | Not executed; requires full source/seal, store and wire |
| Group owner integration | `GroupHistoryRemoveRaceTest`, `GroupHistoryOwnerHandoffTest`, `HistoryGroupNamespacesTest` | T097–110 | BLOCKED by 004 secure runtime/handoff |
| Swing Live and Demo | `HistoryPagedTimelineTest`, `HistoryManualSyncPanelTest`, `HistoryLiveTruthAndDemoTest` | T111–122 | Not run |
| Physical LAN and independent audit | AC-05-A…P, **two physical Win11 LAN PCs**, ≥3 Group JVM, no Internet, human inbound Accept/SAS, independent reviewer | T123–136 | BLOCKED / NOT VERIFIED |

**Command protocol:** use exact JDK21 (`JAVA_HOME`), worktree, `.\mvnw.cmd -o -Dtest=HistoryXxxTest test` for RED (expect missing implementation failure) and GREEN (expect exit 0), then `.\mvnw.cmd -o test`. Save actual stdout excerpts/exit/test counts and version. Do not mark one red compiler failure as green; not run means NOT RUN.

**Security of evidence:** synthetic markers only, no real chat/DPAPI key/cert/SAS; no user content in JUnit XML committed. **T008 Done:** test plan and stop conditions defined, not execution evidence for History yet.
