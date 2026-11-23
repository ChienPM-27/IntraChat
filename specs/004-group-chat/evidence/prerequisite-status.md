# 004 prerequisite and source audit — 2026-11-22

**Scope:** Spec 004 T001, current Windows 11 workstation (one physical host). **Disposition:** BASELINE AUDITED, upstream Gate 1 BLOCKED; no Group Live implementation.

## Verified working-tree baseline

- Working directory: `D:\Documents\Lap_trinh_ung_dung_mang\Tuan08\IntraChat`.
- Git branch: `003-p2p-messaging`; HEAD: `b8a8fd5` (`docs(m2): record 34 tasks, isolated 107-test pass and release blockers`).
- **Dirty working tree** prior to this task: preexisting modified Java/UI/POM/test/docs, deleted obsolete frontends, untracked Live UI/test files and `specs/004-group-chat/`. Never stash/reset/rebase/overwrite unrelated work merely to run Group work.
- M0 Foundation: **41/41 Verified**, historically recorded.
- M1 Spec 002: **37/60** checked, Gate 1 **NOT VERIFIED**, real two-Windows-PC acceptance still missing.
- M2 Spec 003: **34/90** checked, Gate 1 **NOT VERIFIED**, 56 tasks remain; source contains working TLS P2P **local** but not Group Live.
- 004 Spec: CL-04-01…06 **confirmed**, `spec.md`, `plan.md`, `tasks.md` exist; new Group Live code/tests **do not exist** at baseline.

## Actual command and results in this session

Environment: `C:\Users\XuanHoai\.jdks\jdk-21.0.12.1+1`; Java reports **Temurin OpenJDK 21.0.12.1+1**. Ambient Java originally JDK25 and was overridden for this command only.

```powershell
$env:JAVA_HOME='C:\Users\XuanHoai\.jdks\jdk-21.0.12.1+1'
$env:PATH=$env:JAVA_HOME+'\bin;'+$env:PATH
powershell.exe -NoProfile -ExecutionPolicy Bypass -File scripts/verify-m2.ps1 -Offline
.\mvnw.cmd -o dependency:tree -DoutputType=text
```

Actual `verify-m2.ps1` **exit 0**, **113/113 JUnit pass**, failures/errors=0, shaded JAR diagnostics PASS, one Messenger launcher; dependency-tree **exit 0** (actual Maven plugin 3.7.0). Completed 2026-11-22, approx. 12:23 ICT. **No 004 tests were run**; the harness explicitly does **NOT** test two physical LAN PCs, independent review or full GUI SAS/view.

## Source observations and hard prerequisites

- `src/main/java/listener/LiveChatAdapter.java`: `createGroup` returns `UnsupportedOperationException`.
- `src/main/java/model/mock/MockChatService.java` and `ui/dialogs/CreateGroupDialog.java`: Group is **Demo**.
- `src/main/java/vn/intrachat/application/LiveChatService.java`: P2P text/ACK/READ RAM; four active peer sessions and 128 pending attempt limit.
- `src/main/java/vn/intrachat/protocol/ChatV2Codec.java`/`ChatV2BootstrapCodec.java`: strict v2 only, no Group extension negotiated; `SecureChatCoordinator` M2-only listener. M2 shared v1/v2 dispatch remains an upstream engineering prerequisite.
- `src/main/java/vn/intrachat/security/ProtectedLocalFile.java`: Windows CurrentUser DPAPI + per-user ACL + atomic move, package-private; not a group store API.

**External blockers:** B1 ≥2 physical Windows 11 LAN PCs with ≥3 independent test peer identities; B2 independent security/dependency/cryptographic review; B3 a second authorized Windows logon token for DPAPI cross-user. See [M2 blocker report](../../003-p2p-messaging/evidence/execution-blockers-2026-11-22.md). Gate 0 of 004 requires independent review of `GROUP_V1` compatibility, owner-signed grant and protected store. No user content must be sent by a Group prototype before that gate.

**T001 DoD evidence:** source/branch/status/task counts/real Java21 command/results/blockers captured, no false upstream gate claim.