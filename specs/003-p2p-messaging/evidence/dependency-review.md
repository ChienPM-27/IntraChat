# M2 dependency/security/license review (T003 — partial)

**Date:** 2026-11-21 · **Platform:** Windows 11 x64, JDK21 21.0.12.1+1, Maven Wrapper 3.9.16 · **Disposition:** version/provenance/license documented; **independent supply-chain CVE scan and maintainer signoff OPEN**. The dependencies were introduced in a previous M2 commit before this review; this retrospective evidence is **not** represented as prior approval.

## Resolved artifacts (actual offline Maven dependency tree)

```text
org.bouncycastle:bcpkix-jdk18on:1.87
  \- org.bouncycastle:bcutil-jdk18on:1.87
       \- org.bouncycastle:bcprov-jdk18on:1.87
net.java.dev.jna:jna-platform:5.19.1
  \- net.java.dev.jna:jna:5.19.1
```

**Executed (JDK21, actual exit 0):**

```powershell
.\mvnw.cmd -o 'org.apache.maven.plugins:maven-dependency-plugin:3.10.0:tree' '-Dincludes=org.bouncycastle:*,net.java.dev.jna:*' '-Dverbose=true'
```

Previously both dependency `get` goals succeeded and shaded JAR executed under Java21. Java's `SSLContext.getInstance("TLSv1.3")` and real `TlsChatSessionSecurityTest`/secure loopback use **JDK21 JSSE**, not a replaced custom crypto TLS engine. Bouncy Castle is limited to X509 creation; JNA does Windows DPAPI and foreground window handling.

## Publisher/license and known advisory checks

| Dependency | Publisher / known license | Version-specific screening result |
| --- | --- | --- |
| `bcpkix-jdk18on:1.87` | Bouncy Castle Java, Bouncy Castle Licence; [Central](https://central.sonatype.com/artifact/org.bouncycastle/bcpkix-jdk18on) | Exact 1.87 published in Maven Central; public advisory [CVE-2026-5588](https://github.com/advisories/ghsa-wg6q-6289-32hp) affects pre-1.84; [IBM Oct 2026 bulletin](https://www.ibm.com/support/pages/node/7294014) reports CVE-2026-17508 and -18036 for **before 1.86**. The direct version 1.87 is above these disclosed fixed bounds. **This is not a comprehensive vulnerability scan**. |
| `bcutil-jdk18on:1.87` and `bcprov-jdk18on:1.87` | Bouncy Castle Licence; [Central utility POM](https://central.sonatype.com/artifact/org.bouncycastle/bcutil-jdk18on) | Both resolved at 1.87; provider/KDF CVE details must be checked independently against precise module and exploit path before release |
| `jna-platform:5.19.1`, `jna:5.19.1` | Dual licensed **Apache-2.0 OR LGPL-2.1-or-later** per [publisher](https://github.com/java-native-access/jna) and [Central POM](https://central.sonatype.com/artifact/net.java.dev.jna/jna-platform/5.19.1); intended distribution choice: **Apache-2.0** subject to compliance review | Exact pair 5.19.1 resolved, native Windows DPAPI and foreground calls ran on actual Windows11/JDK21. A current formal CVE inventory remains OPEN. |

**Provenance/lock strategy:** Maven Central artifacts with exact pinned versions in `pom.xml` (not version ranges); fail offline if artifact absent. Local `~/.m2` files and one successful offline build do **not** prove signatures, reproducibility, or software supply-chain integrity. Shade filter removes signing *metadata from the assembled uber JAR* due to JAR reassembly; it does **not** provide a signed distributable or assert original upstream artifact signatures have been cryptographically verified.

## Review gate before production

- [x] Exact dependency graph and JDK21 compatibility verified locally.
- [x] Upstream authors/license texts and selected JNA licensing path documented.
- [x] Relevant **known, dated** BC advisories compared against pinned version (not an all-CVE scan).
- [ ] Run an independent up-to-date machine-readable vulnerability scan covering direct and **transitive** dependencies; triage relevant CVE exploit paths and produce an SBOM.
- [ ] Maintainer/legal signoff for packaging all Bouncy Castle + JNA license/NOTICE texts, and dependency version approval under project Constitution.
- [ ] Independently verify artifact SHA256/signatures/provenance from the publisher or a trusted mirror for release builds.

**Decision:** allow local development/testing only; T003 remains **OPEN** pending the above reviews. This document does not declare production dependency safety.
