# Phase 04 Group pure-domain — executed RED → GREEN evidence

**Date:** 2026-11-21 · **Branch:** `004-group-chat` isolated worktree, initial base commit `ff6aa96`; **Java:** Temurin 21.0.12.1+1; **Maven:** `.\mvnw.cmd -o ...`. No Group network traffic was enabled. Evidence consists of actual PowerShell command outputs in this execution, not staged screenshots or invented test passes.

## Batch 1: T011–T014 (Group ID, metadata, roster)
1. Created `GroupIdentityTest` and `GroupRosterTest` **before** domain classes. Command: `.\mvnw.cmd -o '-Dtest=GroupIdentityTest,GroupRosterTest' test`. **RED exit 1**, JDK21 testCompile errors: cannot find symbol `GroupIdentity`, `GroupMember` (new implementation absent).
2. Implemented `GroupIdentity.java`, `GroupMetadata.java`, `GroupMember.java`, `GroupRoster.java`. Fixed a test-only Java effectively-final lambda error during the first GREEN attempt. Reran same focused suite: **GREEN exit 0, 6/6 JUnit PASS**, zero failures/errors.
3. Verified unique nonzero groupId even with same name, Unicode emoji, invalid names/surrogates, positive owner epoch/revision; roster single owner, max **5 devices**, duplicate UUID/SPKI blocked, aliases are not ACL, immutable membership collection and defensive SPKI byte[] copy.

## Batch 2: T015–T018, T021–T022 (authorization, membership reducer, recipient statuses)
1. Created `GroupAuthorizationTest`, `MembershipReducerTest`, `GroupRecipientStatusTest` first. Command: `.\mvnw.cmd -o '-Dtest=GroupAuthorizationTest,MembershipReducerTest,GroupRecipientStatusTest' test`. **RED exit 1**, compile errors: missing `GroupSnapshot`, `GroupAuthorization`, `GroupRecipientStatus`.
2. Implemented `GroupAuthorization`, `MembershipEvent`, `GroupSnapshot`, `GroupMembershipReducer`, `GroupRecipientStatus`.
3. First GREEN attempt: tests compiled, **13 tests, 2 errors**. Root cause: calling `contains(null)` on Java immutable `Set.of` throws NPE. Fixed null validation by validating each element, without weakening the test.
4. Reran focused suite: **GREEN exit 0, 13/13 JUnit PASS**. Cases include member may **propose** invite but cannot approve/remove as owner; expected epoch/revision authorization; duplicate/same-ID altered event rejection; monotonic owner revision JOIN/REMOVE; immutable per-recipient denominator, owner accepted is not recipient app-ACK or READ; READ can legitimately precede ACK for exactly one recipient.

## Extra RED → GREEN: invalid member aliases
1. Added `GroupMemberValidationTest` with malformed surrogate/control codepoints, zero deviceId and malformed SPKI hash.
2. Command: `.\mvnw.cmd -o '-Dtest=GroupMemberValidationTest' test`. **RED exit 1**, `2 tests, 1 failure`: expected `IllegalArgumentException` for malformed Unicode but none thrown.
3. Hardened `GroupMember` validation for unpaired surrogate, ISO controls, alias bytes ≤512. Reran `.\mvnw.cmd -o '-Dtest=GroupMemberValidationTest,GroupRosterTest' test`: **GREEN exit 0, 5/5 PASS**.

## Full regression / package evidence

- Baseline before implementation: `.\mvnw.cmd -o test` **exit 0, 107/107 JUnit PASS**, BUILD SUCCESS.
- After first domain tests: `.\mvnw.cmd -o package` **exit 0, 120/120 JUnit PASS**, BUILD SUCCESS, shaded artifact replaced.
- After Unicode alias hardening: `.\mvnw.cmd -o package` **exit 0, 122/122 JUnit PASS**, **0 failures/errors/skipped**, BUILD SUCCESS, shaded artifact replaced.
- Therefore **15 Group JUnit test cases** were executed in 6 test classes, plus 107 existing M0–M2 regression cases. This is **pure-domain correctness only**, not Group 3-JVM, two-PC LAN, TLS signature or security acceptance.

## Scope / truthfulness

These Java classes intentionally have **no network transport, JSON Group V1 parser, TLS capability activation, DPAPI Group storage or UI integration**. `GroupAuthorization` and `GroupRecipientStatus` explicitly require upstream authenticated pinned signer and verified receipt before application calls their methods. They do **not** prove a cryptographic message origin, an actual user VIEWED, or a persisted owner REMOVE commit by themselves.

**Next code boundary:** Group canonical signature/protocol freeze (T006), independent trust signoff (T005) and Constitution release gates; until then no Group user data should be sent over M2 or raw M1.
