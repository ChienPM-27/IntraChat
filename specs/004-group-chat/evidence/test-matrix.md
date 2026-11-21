# Group phase 04 test matrix and baseline — T009

**Date:** 2026-11-21 · **Environment:** Windows desktop, scoped `JAVA_HOME=C:\Users\XuanHoai\.jdks\jdk-21.0.12.1+1`, Maven Wrapper offline.

## Baseline executed on 004 at ff6aa96
Command (PowerShell in isolated worktree): `$env:JAVA_HOME='C:\Users\XuanHoai\.jdks\jdk-21.0.12.1+1'; $env:Path="$env:JAVA_HOME\bin;$env:Path"; .\mvnw.cmd -o test`

**Actual result:** exit **0**, 107/107 JUnit tests PASS, 0 failed/error/skipped, Maven BUILD SUCCESS, 43.970 seconds, 2026-11-21 20:04:23 +07:00. **This is a prior M0–M2 regression baseline, NOT Group verification.**

## Test-first execution
- RED: create Group JUnit tests; run `.\mvnw.cmd -o -Dtest=<TestClass> test` with Java21 and capture exact missing implementation/assertion failure, exit nonzero.
- GREEN: implement smallest tested behavior; run same focused suite and full `.\mvnw.cmd -o test`. Do not check task if RED was not actually run or Green fails.
- Security plan: deterministic canonical vector/signature tests; unsupported v1/v2 Group fail; forged relay/viewer, changed pin, wrong epoch; ACL/DPAPI user/account.
- Integration plan: ≥3 **separate** JVMs A/B/C on authenticated TLS; D outsider; 5 devices owner+4; true foreground READ and failover/handoff crash tests.
- Physical acceptance: ≥2 **distinct Windows 11 x64 LAN PCs** with no public Internet and per-inbound human consent/SAS; not available in current one-PC tool session.
- Full release: Group AC-04-A…L + M1/M2 Gate 1 + independent security signoff; no Group test counts invented.

**Capture:** Java version, commit SHA, command, exit, JUnit XML/test names, peer/PC count, sanitized screenshots; never commit real chat text, SAS/private keys, DPAPI files.
