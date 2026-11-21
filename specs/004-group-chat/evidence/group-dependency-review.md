# Group dependency and crypto review — T005 (INDEPENDENT APPROVAL BLOCKED)

**Date:** 2026-11-21. This is an assistant-performed source/cache inspection and a **request for independent review**, not a dependency security approval.

| Artifact | Observed pinned version | Check / caveat |
| --- | --- | --- |
| Java | Temurin **21.0.12.1+1** | `mvnw -o test`: 107/107 pass in current worktree; runtime API permits JSSE/JCA ECDSA P-256 |
| Maven Wrapper | Maven 3.9.16 in wrapper settings | Offline test passed; no new dependency proposed for pure domain |
| Jackson Databind | 2.22.4 | Versioned strict UTF-8 JSON needs separate Group parser/validation; advisories not independently verified |
| Bouncy Castle bcpkix | **1.87** | Local Maven cache has jar/pom/sha1; provenance, license/CVE/asset integrity signoff still needed |
| JNA-platform | **5.19.1** | Local Maven cache has jar/pom/sha1; Windows CurrentUser DPAPI integration; native access review outstanding |
| Swing/FlatLaf | 3.6.2 | Existing UI; no new UI dependency approved |

**Missing required checks:** up-to-date independent CVE/licensing/provenance assessment of direct and transitive artifacts, separate reviewer signature/authorization; this assistant cannot self-certify. No private source or secrets exposed. **T005 remains unchecked / BLOCKED for approval**, and Gate 1 cannot be signed.

**Stop rule:** Do not enable Group user payload or claim secure release based solely on Maven pass or checksum SHA1 files; request an independent qualified reviewer before any security-critical wire/signature implementation is approved for production.
