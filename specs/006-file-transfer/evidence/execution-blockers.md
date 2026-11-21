# Verified execution blockers — Stage 06 File Transfer

**Date:** 2026-11-21 · **Branch:** `006-file-transfer` · **Starting SHA:** `d01fcc0`. **Status: partial domain/read-only source TDD, production FILE_V1/Win11 destination/Group release BLOCKED.** Missing evidence is not proof a reviewer or second PC cannot exist.

## B1 — Independent File V1 canonical protocol review (T004, T011)

**Observed:** [FILE V1 contract](../contracts/file-v1.md) is explicitly **DRAFT / not independently reviewed**. `protocolVersion=5` is a candidate only, with no approved raw-preface negotiation bytes, mutually protected transcript/nonces/SPKI/session golden vectors, replay/downgrade corpus or attributable cryptographic signoff. Before changes the repository had no `protocol/file` or `security/file` runtime; `TlsChatSession` only sends/receives strict CHAT_V2.

**Blocked:** T004, T011, production File wire/negotiation T033–T050 and real network T091–T108. **Unblock:** independent named protocol/security reviewer approves versioned exact canonical bytes/transcript, identity and golden negative tests. **No unsafe fallback:** no FILE over M1 raw v1, CHAT_TEXT v2, Group v3, History v4 or new unaudited file socket.

## B2 — Independent Win11 Downloads filesystem review (T005, T011)

**Observed:** no reviewed `ApprovedDownloadsRoot`, `storage/file` or `NoClobberFinalizer` source; no independently verified Windows no-follow/reparse/junction/ADS/ACL stable handle and atomic **no-overwrite** algorithm, safe FSYNC/rename or safe orphan identification. User **CL-06-03 B** approves a Downloads root once, but **CL-06-02 A** still requires Accept for each offer. Basic lexical path normalization cannot establish filesystem race safety.

**Blocked:** T005, T011, receiver staging/durable no-clobber T051–T070, full File E2E and AC-D/G/L. **Unblock:** attributable Windows 11/JDK21 filesystem review and actual adversarial race/ACL/reparse/collision/cross-volume/crash tests; fail-closed write semantics. The isolated `SourceFileReader`/`SourcePrehashService` inspect only user-selected local sources and **do not** write Downloads or satisfy T005.

## B3 — Signed Group 004 runtime not verified

**Observed:** `specs/004-group-chat/tasks.md` states **18/124** pure-domain tasks; initial 006 source had no signed origin Group relay/owner epoch/roster/JOIN/REMOVE/owner handoff transport implementation. [CL-06-07 A](../clarifications.md) makes owner-mediated per-member Group File mandatory after 004 Verified.

**Blocked:** T123–T136, AC-M and full 006 acceptance. A signed member B origin cannot be inferred from current owner A TLS hop. **Unblock:** real independently verified Group 004 signed network/owner runtime plus ≥3-JVM positive/negative cases. Pure `GroupFileEligibility` policy does not authorize Group wire.

## B4 — Two distinct physical Win11 LAN peers and independent release review not evidenced

**Observed:** one Windows build `26100` environment accessible in this execution, no **second distinct physical Win11** peer exercised, no manual two-PC inbound Accept/SAS File transfer without Internet, no attributable File/M2/privacy/security Gate 1 signoff. This is unavailable evidence **during this execution**, not a categorical statement that no other PC exists.

**Blocked:** T143–T144, AC-O/P, release 006/M4. **Unblock:** real two Windows 11 x64 machines, verified pinned peer IDs, human inbound Accept/SAS and negative File tests, independent attributable security/privacy report. Two JVMs on a single host are not the physical LAN gate.

## Stop and continuation

**Executed:** T001–T003, T006–T010, T012 design/evidence where sourced; T013–T032 pure-domain RED→GREEN; T071–T076 read-only source RED→GREEN; T083 RED timeout test only (T084 pure helper is GREEN-tested but task remains unchecked pending T082). **Maven evidence:** baseline 138 PASS; two RED commands exit 1 with missing symbols; 20+7 focused GREEN tests; 165 full tests PASS. See [trace/test report](trace-and-tdd.md).

**Do not mark blocked IDs done or enable File capability/receiver finalizer/Group File/Live real File.** No on-wire File or Win11 destination write E2E was executed. 006 **NOT VERIFIED**, M4 **NOT VERIFIED** (also requires 007).
