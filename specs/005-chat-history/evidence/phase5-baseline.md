# T001 — Phase05 clean baseline and status (2026-11-21)

**Source:** branch `005-chat-history` at `9acfb8214650fa88c932a81ae9589aa10af7dc23`, cloned via clean Git worktree (original `003-p2p-messaging` dirty branch was **not modified**).

- Platform: Windows 11 x64 target, local physical PC `XUANHOAISLECOOL` (Lecoo N155A), current user context, **only one physical Windows LAN endpoint observed here**.
- Java: `C:\Users\XuanHoai\.jdks\jdk-21.0.12.1+1`, Temurin OpenJDK 21.0.12.1 LTS; system default Java 25 was explicitly bypassed.
- Baseline command, worktree `005-chat-history`: set `JAVA_HOME` to above JDK 21 and invoke `.\mvnw.cmd -o test`. **Actual exit code 0, BUILD SUCCESS, 122 tests, 0 failures, 0 errors, 0 skipped, total time 44.928 s**. This is M0–M2 + 004 pure Group baseline; **zero new History tests in this baseline run**.
- Source inspection: existing M2 `P2PConversation.idFor`, M2 secure TLS/identity, Group pure domain classes; `pom.xml` Java 21/JUnit5/JNA/BouncyCastle/Jackson/FlatLaf. **No `sqlite`, `sqlcipher` or approved cipher-enabled JDBC dependency**, and no History source classes.
- 004: Group pure-domain 18/124 completed per committed 004 tasks; 004 secure Group transport/current owner handoff/origin signed E2E **NOT implemented/verified**.
- Required external gates: independent SQLite/native crypto/license/privacy review, M1/M2 physical two-Windows-PC LAN acceptance, Group 004, future History security review **NOT VERIFIED**. No other physical machine was contacted/tested.
- Domain-only RED→GREEN tests may proceed without choosing an unreviewed database vendor; **never open plain SQLite as a substitute**, or claim History authenticated sync.
- Evidence confidentiality: no user conversation content, private key, pin/SAS, database bytes or DPAPI secrets included. All statuses reflect observed commands/source state only.

**T001 Done:** Baseline and blockers recorded. **History feature NOT implemented or verified.**
