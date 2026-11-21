# US1 — Reproducibility evidence

**Executed:** 2026-11-21
**Environment:** Windows 11 x64 (build 26100), Java 25.0.4 Oracle JDK, Maven Wrapper 3.9.16, Java `--release 21` target.
**Status:** PASS for tested environment; **not tested on JDK 21 runtime**.

## Main project

- `.\mvnw.cmd -version` → exit 0, Maven 3.9.16, Java 25.0.4.
- `.\mvnw.cmd -q clean package` → exit 0, generated `target/intrachat-0.1.0-SNAPSHOT.jar`.
- `java -jar target/intrachat-0.1.0-SNAPSHOT.jar --diagnostics` → exit 0, `IntraChat 0.1.0-SNAPSHOT — Chưa kết nối`.
- `.\mvnw.cmd -q test` → exit 0, 9 tests; no failures/errors.
- `.\mvnw.cmd -q -o test` → exit 0 with previously cached Maven dependencies.

## Isolated copy

A temporary directory under Windows %TEMP% received only `pom.xml`, `.mvn/`, `mvnw.cmd` and `src/` from the project; no `target/` or compiled classes were copied.

- `.\mvnw.cmd -q -o clean package` in temporary directory → **exit 0**.
- `java -jar target/intrachat-0.1.0-SNAPSHOT.jar --diagnostics` → **exit 0**.
- Temporary directory was cleaned up afterward.
- This is reproducible **with cached dependencies**, not proof of first-time zero-network bootstrapping.

## Limitations

- JDK 21 installation/runtime on a separate machine has **not** been tested; this machine has JDK 25. The `--release 21` flag restricts API/classfile compatibility but does not substitute for a JDK 21 runtime test.
- First wrapper/plugin/dependency downloads may require Internet.
- No peer-to-peer network or History Sync implementation was part of this verification.
