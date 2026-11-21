# M2 dependency and security review — partial

**Date:** 2026-11-21 · **Status:** Dependency resolution PASS; security/license audit **NOT COMPLETE**.

- Baseline `pom.xml`: Java21 release, Jackson databind 2.22.4, JUnit Jupiter 5.14.4, FlatLaf 3.6.2, Maven Shade 3.6.1.
- New `org.bouncycastle:bcpkix-jdk18on:1.87` (transitive BC provider/util), used only for local X.509 certificate creation. Runtime JSSE TLSv1.3 remains from JDK21.
- New `net.java.dev.jna:jna-platform:5.19.1` (with JNA core), used only for Windows CurrentUser DPAPI/OS foreground HWND.
- **Actually run on Java21 Maven Wrapper:** `org.apache.maven.plugins:maven-dependency-plugin:3.10.0:get -Dartifact=org.bouncycastle:bcpkix-jdk18on:1.87 -Dtransitive=true` → exit **0**; same for `net.java.dev.jna:jna-platform:5.19.1` → exit **0**. JARs in local `~/.m2` and combined shaded build compile/run.
- A first attempt without explicit full Maven plugin prefix accidentally failed plugin resolution (misparsed PowerShell `-Dartifact`); retried with fully qualified plugin goal and standalone quoted arguments successfully.
- **NOT DONE:** independent vulnerability scan, legal/license review and dependency supply-chain signoff; T003/T004/T010/Constitution security gate may not be checked solely from Maven resolution. Network test results are not penetration tests.
