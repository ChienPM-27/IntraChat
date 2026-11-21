# Quickstart — File 006 Test-first Verification Plan (NOT EXECUTED)

**Feature:** `006-file-transfer` · **Status:** User choices **10/10 confirmed**, **160 implementation tasks / 36 completed**; 27 isolated File domain/source tests PASS, full 165 regression PASS; real File wire/Windows destination/physical tests NOT RUN. [Plan](plan.md) · [160 planned tasks](tasks.md) · [Spec](spec.md) · [Draft wire](contracts/file-v1.md) · [Gate assessment](checklists/architecture-impact-review.md).

## 1. Before running File tests

- Verify clean `006-file-transfer` worktree separate from user's dirty `003-p2p-messaging`; protect prior Group/History work.
- Use **Temurin JDK 21** on Windows 11 x64 and Maven Wrapper; `JAVA_HOME` must point to Java 21, not system Java 25.
- Check M1 plaintext v1 diagnostics only; M2 TLS1.3 verified `PAIR_READY`/deviceId+SPKI and manual inbound Accept/SAS. If not present do not send real files. FILE_V1 codec/streaming **not yet implemented**.
- User confirmed **≤256 MiB (0-byte allowed)**, **each file Accept**, **preauthorized Downloads root CL-06-03 B**, **no overwrite**, no resume, source mutation fail, Group owner only after 004 Verified, File profile on existing M2, RAM-only state and no auto-open.
- Before production wire/filesystem code, obtain independent reviewed exact FILE_V1 canonical vectors/protocol mapping, Windows Downloads ACL/junction/atomic no-clobber proof and security Gate 0; mark BLOCKED if not available.

## 2. Planned RED→GREEN commands (examples, NOT RUN)

From clean worktree in PowerShell, set approved JDK 21, then run targeted RED test before implementation (expected failure) and GREEN after implementation, documenting actual exit and test count. Test names are **proposals**, not current classes:

```powershell
$env:JAVA_HOME = Join-Path $env:USERPROFILE '.jdks\jdk-21.0.12.1+1'
$env:Path = (Join-Path $env:JAVA_HOME 'bin') + ';' + $env:Path
.\mvnw.cmd -o '-Dtest=FileOfferBoundsTest,ZeroByteFileTest' test
.\mvnw.cmd -o test
.\mvnw.cmd -o package
```

**IMPORTANT:** This is an instruction-only snippet, not evidence. Do not call File tests PASS based on existing 138 unrelated M0–M2/History domain baseline tests. Targeted RED must prove missing behavior, GREEN must run independently and full regression must remain PASS.

## 3. Planned acceptance walkthrough (AC-06-A…P)

| Scenario | Setup | Observe/result required |
| --- | --- | --- |
| **A/I** Normal/boundary bytes | A↔B verified TLS; synthetic 0,1, 268435456, 268435457 sizes; user preauthorizes Downloads and Accepts each offer | 0-byte sha256 empty, exact size/hash of valid files, >cap reject; final no overwrite |
| **B/C/L** Cancel and fault | Inject Cancel/half-close/disk full/crash before/after commit/late ACK | No false completed, app-owned staging cleanup only, no auto resume; retry new transferId |
| **D/G** Windows path attacks | UNC, `..\`, ADS, junction/reparse race, reserved device name, existing file same name | No path escape or overwrite, user existing file remains byte identical |
| **E** Consent | Pinned A sends 3 offers, B authorized Downloads root but Rejects 2 | 0 pre-Accept file chunks, no autoAccept, third only after explicit Accept |
| **F** Scope privacy | C pinned to A but not A↔B participant, knows transferId/conversationId | 0 count/name/size/content disclosure |
| **H** Source/integrity mutation | Change source length/mtime, then same-size content during transmission; alter one chunk | Sender and receiver report failure, no final corrupted file |
| **J/K** Protocol/fairness | M1/v2/v3/v4 FILE injection, capability-strip, two verified chat sessions, slow receiver | Strict denial/no downgrade, bounded frame/queues/heap, Live EDT responsive |
| **M** Group owner fanout | **Only after 004 Verified**: owner A, member B/C/D, late JOIN/REMOVE and handoff | Signed B original proof, each member consent/results, no pre-JOIN/new post-REMOVE chunk |
| **N** Live/Demo/History | Live file while Demo and History 005 present | No Demo traffic, no new History File record, no M2 faux READ/APP_ACK, no auto-open |
| **O/P** Physical release | ≥2 different physical Windows 11 PCs LAN offline Internet, manual IPv4:port inbound Accept and SAS, reviewed report | Actual logs/screenshots/hosts, AC-A…P and Constitution I–VII independent signoff; M4 needs 007 |

## 4. Evidence rules and blockers

Each executed test must record SHA, JDK, Windows build, machine/peer count, exact command, exit code, duration, result and synthetic test data; redact file content, PIN/SAS, Windows username/home/private paths. 27 File domain/read-only source JUnit tests have now been run on Java21 with RED→GREEN; see [evidence](evidence/trace-and-tdd.md). No actual FILE_V1 socket/Win11 receiver File E2E tests have been run.

**Blocked for release:** 004 owner signed Group runtime, exact FILE_V1 protocol and Win11 no-clobber independent review, inherited M1/M2 two real Windows-PC Gate 1 and full File security/privacy audit. Group File cannot be silently omitted because user confirmed CL-06-07 **A**, not defer B.

**Result of this document:** verification instructions only; **File 006 PARTIAL DOMAIN/SOURCE ONLY / NOT VERIFIED**, **M4 NOT VERIFIED**.
