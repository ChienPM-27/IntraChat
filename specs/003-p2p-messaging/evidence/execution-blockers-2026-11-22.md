# Spec 003 — Execution stop report (2026-11-22)

**Project:** IntraChat · **Target:** `specs/003-p2p-messaging/tasks.md`
**Machine:** connected Windows 11 workstation (single physical host) · **Local JDK:** Eclipse Temurin OpenJDK **21.0.12.1+1** · **Mode:** Maven `-o` (offline).
**Disposition:** **BLOCKED for full M2 goal / NOT VERIFIED**. No checkbox may be completed by inference from a successful local build.

## Work actually executed in this session

1. Inspected Spec 003 task tracker, M1/M2 acceptance and constitution gates, dependency review, prior blockers, current Java source/test inventory and Git working-tree status.
2. Kept existing uncommitted work intact (including the single-Messenger UI migration). Did not stash, reset, commit, change firewall, or alter local identities.
3. Selected Temurin 21 explicitly for the command scope because ambient `JAVA_HOME` points to Oracle JDK 25. Set `JAVA_HOME=C:\Users\XuanHoai\.jdks\jdk-21.0.12.1+1` and prepended `%JAVA_HOME%\bin` to PATH.
4. Executed:

```powershell
$env:JAVA_HOME='C:\Users\XuanHoai\.jdks\jdk-21.0.12.1+1'
$env:PATH=$env:JAVA_HOME+'\bin;'+$env:PATH
powershell.exe -NoProfile -ExecutionPolicy Bypass -File scripts/verify-m2.ps1 -Offline
```

**Actual exit code: 0.** Output confirmed:
- Java Temurin 21.0.12.1+1;
- Maven offline `clean package` success;
- 113/113 JUnit cases passed, zero failures/errors;
- `java -jar target/intrachat-0.1.0-SNAPSHOT.jar --diagnostics` success;
- one `ui.IntraChat` launcher and `LanConnectionDialog` packaged, obsolete M1 and separate Live UI classes absent from shaded JAR;
- script explicitly reported **NOT TESTED**: two real Windows LAN PCs, independent security review, and full GUI SAS/view tests.

This invocation **did not** execute a fresh two-JVM GUI manual pairing cycle or external LAN validation. Historical manual GUI evidence is in `docs/ui-migration-2026-11-21.md` and remains distinct.

## Goal and status

- **M0:** 41/41 Verified (historical acceptance).
- **M1:** 37/60 checked, **IN PROGRESS / Gate 1 not Verified**.
- **M2 Spec 003:** 34/90 checked; **56 tasks open**. This run **does not advance any checkbox**, because the release gates and individual task Done prerequisites are not satisfied by the regression suite alone.

## Hard external blockers — stopping conditions

| ID | Tasks affected | Why this cannot be verified from the current Windows session | Required resolution |
| --- | --- | --- | --- |
| **B1 — two real PCs** | **T080, T081, T082, T084**; also T087–T090 downstream | Connected tooling reaches only one physical Windows 11 host. Two local JVMs with `127.0.0.1` cannot demonstrate two distinct Windows PCs on a LAN without public Internet, separate human SAS consent, realistic firewall/network failure, real ACK/view events | Grant authorized access to **two physical Windows 11 hosts on the same Private LAN**, with separate identities/operators and controlled offline-Internet test. Run [M2 quickstart](../quickstart.md) and [Windows LAN guide](../../../docs/windows-lan-setup.md); record OS/JDK/commit, IP, packet/GUI outcomes per host without exposing secrets |
| **B2 — independent approval** | **T003, T010, T076, T083, T087, T090** and T088/T089 dependencies | [Dependency review](dependency-review.md) documents BC/JNA graph and known advisories but formal current machine-readable CVE/SBOM/provenance, license/NOTICE and independent security review/signoff are not complete. This operator cannot be the independent approver of their own verification | Assign qualified independent security/supply-chain and maintainer/license reviewers; record signed, dated disposition, targeted MITM/pin/DPAPI/read-receipt review, reproduce negative tests, triage CVEs and issue approval or remediation |
| **B3 — second Windows user security token** | Cross-user DPAPI assertions in T015/T083, security Gate 1 | The current host exposes only the active user's testing context. A cross-user Windows DPAPI fail-closed test must actually run using a second authorized logon token | Provide approved second Windows account/test context; do not reuse or export real application key material |

## Still-open local engineering work (not misrepresented as external blockers)

The pending list also includes v1/v2 dispatch/T025–T034, robust manual retry and uncertain state/T053–T054, FE negative/keyboard and delivery status/T063–T075, stress/resource/T061–T062/T078, and multi-cycle GUI/T086. Some classes/tests already exist in source, but each checkbox has **specific test-first RED→GREEN, dependencies and evidence**. No new RED→GREEN or physical GUI acceptance was performed here, so no tasks were checked.

**Safety/release ruling:** M2 must remain **IN PROGRESS / NOT VERIFIED**. Re-running local tests cannot discharge T080–T084 or independent review gates. Do not claim secure release, two-PC readiness, or delivery/read correctness beyond measured local paths.

## Next action to unblock

Obtain an authorized second physical Windows PC and named independent reviewer; execute T080–T084 and independent T003/T083 checks with recorded evidence. Then close remaining local coding/tests, rerun `scripts/verify-m2.ps1 -Offline` under Java21, audit all AC-03-A…L and Constitution I–VII, and only thereafter consider T090 signoff.
