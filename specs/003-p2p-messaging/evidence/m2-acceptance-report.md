# M2 Acceptance — Partial implementation, NOT VERIFIED

**Date:** 2026-11-21 · **Status:** **IN PROGRESS / PARTIAL; not a production secure chat release**.
**Environment:** one Windows 11 x64 desktop, Eclipse Temurin JDK21, two independent `javaw.exe` processes with distinct DPAPI test identities; **two separate physical Windows LAN hosts NOT RUN**.

| Spec AC | Executed evidence | Result |
| --- | --- | --- |
| **AC-03-A** text/sender/time | Protected v2 A→B and B→A actual GUI text; local timestamp and bubble direction verified | **PASS local** |
| **AC-03-B** two-way text on 2 physical PCs | Two GUI processes on **one PC only** | **BLOCKED physical LAN** |
| **AC-03-C** Vietnamese/emoji Unicode | Real GUI sample and `ChatTextValidationTest`/`ChatV2CodecTest` | **PASS tested cases** |
| **AC-03-D** offline draft/manual retry | No auto-reconnect; Live composer restores failed draft; full uncertain send/retry GUI not tested | **PARTIAL** |
| **AC-03-E** view-based READ | Initially false-positive when background, fixed using native foreground guard; local UI now shows DELIVERED until actual foreground, then READ | **PASS tested foreground cases; more negative tests needed** |
| **AC-03-F** first pin+TLS, changed key | Real first human SAS on both GUI windows, protected TLS1.3, remembered pin on relaunch, unit changed-key rejection | **PARTIAL security review** |
| **AC-03-G** ACK & READ correlation | Real app ACK vs actual view receipts, basic wrong-conversation/READ-before-ACK test; full forged/replay cross-session matrix not run | **PARTIAL** |
| **AC-03-H** ID/name/timezone | sender identity tracked by certificate/verified UUID, GUI time shown; full dual-name/timezone scenarios not run | **PARTIAL** |
| **AC-03-I** FE Live/Demo RAM-only | Live GUI real, Demo GUI separately opens, no mock credential in Live; message RAM, key/trust DPAPI | **PARTIAL** (restart/large-data audit) |
| **AC-03-J** two physical Win11 LAN peers, no Internet | **NOT RUN** | **BLOCKED** |
| **AC-03-K** all security/fault/GUI/regression tests | Java21 final `verify-m2.ps1 -Offline`: exit 0, **74/74 JUnit pass**, negative tests partial; no independent penetration/security assessment | **PARTIAL** |
| **AC-03-L** M1 gate + Constitution I–VII release | M1 Gate 1 still open; this is a work branch | **BLOCKED** |

**Verified local:** Real UI human inbound approval + matching SAS confirmed by two actual clicks; JDK21 JSSE socket messaging; READ negative OS background/positive foreground in both directions; original Demo entrypoint still works. This is genuine local functionality, not a test-only simulation of TLS.

**Unverified:** independent cryptographic security review, Windows DPAPI different-user access, corrupted-key workflow, v1/v2 shared-port dispatcher (M1 and M2 currently have separate listener lifecycles), full timeout/4 active capacity/ACL/flood/secret-log/perf tests, manual uncertain-send retry and accurate missing bubble states, and **two distinct Win11 LAN PCs without Internet**. No user Chat over M1 plaintext; `JsonEnvelopeCodec` v1 still diagnostic-only.

**Decision:** M2 stays **In Progress**, NOT Verified. M1 remains **In Progress**. This report is **not a security certification** or official milestone acceptance.

## 2026-11-21 later Java21 security-hardening addendum

The original baseline above measured **74 tests**. A subsequent local `scripts/verify-m2.ps1 -Offline` run on Temurin **21.0.12.1+1** completed **93/93 tests, 0 failures/errors, exit 0** after test-first fixes to X509 issuer/critical extensions, DPAPI stored UUID/CN exact binding, CHAT_V2 reserved-zero UUID and UTC/read receipt metadata. See [full RED→GREEN evidence](m2-hardening-round2.md). The task tracker is now **29/90 completed**. This improves AC-03-F/G/K local confidence but **does not change BLOCKED/PARTIAL** status for real two-PC AC-03-B/J, independent security evaluation, M1 Gate 1, stress/resource audits or release AC-03-L.
