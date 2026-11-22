# Local dependency and supply-chain inventory — Spec 004 T004

**Recorded:** 2026-11-22, Windows 11 / Temurin 21.0.12.1+1
**Disposition:** **PARTIAL LOCAL INVENTORY — NOT APPROVED / T004 OPEN**
**Reviewer:** **UNASSIGNED** (qualified independent security, legal/license and supply-chain reviewer still required).

## Commands and real outcomes

- Maven offline regression: `scripts/verify-m2.ps1 -Offline` with `JAVA_HOME=C:\Users\XuanHoai\.jdks\jdk-21.0.12.1+1`: exit **0**, **113/113 JUnit PASS**, shaded-JAR diagnostics PASS (existing P2P/FE, **not** Group tests).
- Offline dependency graph: `.\mvnw.cmd -o dependency:tree -DoutputType=text`: exit **0**, Maven dependency plugin **3.7.0**, BUILD SUCCESS. This is **not** an independently reviewed SBOM or CVE scan.
- Inspected local Maven-repository artifacts via PowerShell `Get-FileHash -Algorithm SHA256` and license names in cached artifact POM XML. These hashes only attest to the **currently cached bytes**, not artifact provenance or publisher signature.

## Direct and security-relevant transitive inventory

| Artifact | Version | Local cached SHA-256 | License string observed in cached POM |
| --- | --- | --- | --- |
| com.formdev:flatlaf | 3.6.2 | `32C79DEE6D51341A3F8603598415841B4461FE535DE2C34BCC8EE40E91E72267` | Apache License 2.0 |
| com.fasterxml.jackson.core:jackson-databind | 2.22.4 | `D4CBF8947087F305869826801913ABDC50735A1FAB2074A1993E7BC67CA974A3` | Apache License 2.0 |
| com.fasterxml.jackson.core:jackson-core | 2.22.4 | `2D30C375CDDEA9657A1E1EF97547A2694B663B355FACB4DE53A097782B312D1A` | Apache License 2.0 |
| com.fasterxml.jackson.core:jackson-annotations | 2.22 | `21DDB598807D3A51A876704EB979D9296E1C6A6F47AB1826FF88C6D6A127A2D0` | Apache License 2.0 |
| org.bouncycastle:bcpkix-jdk18on | 1.87 | `A44BE2660D72ED88673372B8CC2D53A25A5AAF27122FB803ED93F07147536AA1` | Bouncy Castle Licence |
| org.bouncycastle:bcutil-jdk18on | 1.87 | `AF46DD381553FDC466CD0C18F8BF2144C7DBCB7337EEE3E16CAB15E96613DDF0` | Bouncy Castle Licence |
| org.bouncycastle:bcprov-jdk18on | 1.87 | `12D2D3DD82A9EC15E92EC7C7943EE64F6097C6DC12C07E984F1693B77565B9AA` | Bouncy Castle Licence |
| net.java.dev.jna:jna-platform | 5.19.1 | `3B3864F5B449E9C3C24B16861524B622B086563F44E0CD8384C8EFC5A6052F82` | LGPL-2.1-or-later; Apache-2.0 (POM string) |
| net.java.dev.jna:jna | 5.19.1 | `4FB141DD8EF6B0585FFCEEA4BC49602FBC6312FA977E2C488794EA3E6AAFECAE` | LGPL-2.1-or-later; Apache-2.0 (POM string) |

Test dependencies resolved as JUnit Jupiter 5.14.4, JUnit Platform 1.14.4, OpenTest4J 1.3.0 and API Guardian 1.1.2; see actual `dependency:tree` results in execution logs. All above versions were already in the source/POM before Group work; **no new dependency was added** for Spec 004.

## What remains unverified and requires a reviewer

1. Current upstream advisories, CVEs, exploitability, replacement versions, exact transitive BOM and risk disposition for BC 1.87, JNA 5.19.1, Jackson 2.22.4, FlatLaf and JDK21; **offline Maven cannot prove absence of CVEs**. A properly dated trusted vulnerability database scan and documented triage are still necessary.
2. Artifact POM declarations are **not** a substitute for reading actual LICENSE/NOTICE, choosing correct JNA dual-license obligations, verifying file redistribution requirements, certificates/signatures/checksums against the publisher and generating an independently reviewed SBOM.
3. Security code review for new Group ECDSA owner signing, canonical binary vectors, pinned TLS/profile transcript, per-message grant/rollback/removal race, unsafe serialization, and DPAPI CurrentUser/ACL.
4. DPAPI cross-user fail-closed test using **another authorized Windows account/token**, not merely two processes with same user.
5. Constitution IV, dependency/security/license signoff by a **qualified independent reviewer** with name/date/reproduced evidence/findings and remediation decision. An assistant/implementation author cannot self-certify independent approval.

**Blocker B2:** T004 independent approval is **not available** in the current connected session. Documentation and hashing can be completed, but T004 **must remain unchecked** until genuine review. This also blocks T010 Constitution Gate 0 signoff and, by the explicit task dependency graph, **all Group security source implementation tasks T011 onward**. See [004 checklist](../tasks.md) and [M2 blocker report](../../003-p2p-messaging/evidence/execution-blockers-2026-11-22.md).

**Status:** inventory performed; security/license/CVE provenance acceptance **OPEN**.