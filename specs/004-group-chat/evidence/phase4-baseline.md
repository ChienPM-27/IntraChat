# Phase 04 baseline — T001

**Date:** 2026-11-21 · **Base commit:** ff6aa96 (004-group-chat from origin). Isolated Git worktree under Windows TEMP, original 003-p2p-messaging dirty working tree left unchanged.

## Direct observations

- Host: one accessible Windows desktop; tested Temurin Java **21.0.12.1+1** via scoped JAVA_HOME. The global java.exe defaults to Java 25, so all test commands must explicitly scope Java21.
- Command: `$env:JAVA_HOME='C:\Users\XuanHoai\.jdks\jdk-21.0.12.1+1'; $env:Path="$env:JAVA_HOME\bin;$env:Path"; .\mvnw.cmd -o test`.
- **Executed on this exact isolated 004 commit:** exit **0**, Maven **107 tests, 0 failures, 0 errors, 0 skipped; BUILD SUCCESS** (2026-11-21 20:04 +07:00, ~43.970s).
- M0 foundation Verified per repo evidence; M1 diagnostics-only plaintext remains IN PROGRESS; M2 supports local protected P2P but **NOT VERIFIED for physical LAN/security release**.
- Actual committed M2 classes include `SecureChatCoordinator`, `TlsChatSession`, `LiveChatService`, `ChatV2Codec`, `ProtectedLocalFile`. Group v3 classes and real Group tests **absent** before this execution.
- **Not run:** two independent physical Win11 LAN hosts disconnected from public Internet; independent security reviewer; cross-Windows-user DPAPI; ≥3 Group clients. Local M2 tests cannot certify Group behavior.

**Gate outcome:** baseline/test environment established. User's eight confirmed Group CL-04 decisions remain binding. Group release blocked by inherited evidence and no implementation.
