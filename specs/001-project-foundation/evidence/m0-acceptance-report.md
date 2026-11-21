# M0 Acceptance Report — IntraChat Foundation

**Date:** 2026-11-21
**Status:** **In Progress / Partial verification** — not yet `Verified`
**Required target:** Windows 11 x64 + Java 21 runtime. **Actual tests:** Windows 11 + JDK 25.0.4 compiling to release 21.
**Spec:** [spec.md](../spec.md) · **Plan:** [plan.md](../plan.md) · **Tasks:** [tasks.md](../tasks.md)
**Evidence:** [test results](m0-test-results.md), [US1](us1-reproducibility.md), [US2](us2-lifecycle.md), [US3](us3-contract-review.md).

## Acceptance criteria

| AC | Actual result | Decision |
| --- | --- | --- |
| AC-01-A — checkout/build/run | isolated clean-source copy packages and runs diagnostics offline with cached dependencies | **PARTIAL**: JDK 21 runtime not verified |
| AC-01-B — test baseline & negative test | 9/9 JUnit pass; purposely broken assertion causes expected failure | **PASS** |
| AC-01-C — layers / no socket in UI | architecture documentation + code review; Swing EDT & transport absent | **PASS — M0 scope** |
| AC-01-D — launch/exit, truthful state | 5/5 IntraChat windows normally close; label correctness from code review | **PARTIAL**: physical offline UI/label accessibility not independently verified |
| AC-01-E — entities / protocol | data-model + versioned JSON/framing contract reviewed | **PASS — design** |
| AC-01-F — coverage | 11/11 capabilities mapped to owners/stages | **PASS — design** |
| AC-01-G — README | prerequisite/build/run/test documented and commands executed | **PASS (JDK 25 evidence; JDK 21 caveat noted)** |
| AC-01-H — Constitution & evidence | design gate and code/test review; open JDK21 runtime and physical offline checks | **PARTIAL** |
| AC-01-I — clarifications & History design | CL-01…04 + S1 offline/S2 duplicate/S3 unauthorized and other cases documented | **PASS — design only** |

## Success criteria SC-001…SC-009

| SC | Outcome |
| --- | --- |
| SC-001 | **PARTIAL:** clean-source copy reproducible with cached dependencies on JDK25 |
| SC-002 | **PASS:** 5/5 GUI open/close, no residual process |
| SC-003 | **PARTIAL:** no peer and truthful window by code review; no forced physical Internet disconnect |
| SC-004 | **PASS:** 9/9 automated, intentional failure detected |
| SC-005 | **PASS:** 11/11 capabilities in feature matrix |
| SC-006 | **PASS — design:** required protocol draft fields/edge cases included |
| SC-007 | **PASS:** README has build/run/test and limitations |
| SC-008 | **PARTIAL:** no design violations observed; JDK21/offline release gate outstanding |
| SC-009 | **PASS — design:** 4 clarifications and ≥3 sync situations included |

## Constitution Check

- I LAN-first: PASS design, no cloud; networking not implemented.
- II Message/identity: PASS draft & value-object tests; no transport/parser.
- III Nonblocking/bounded: PASS M0 code scope (no network/file work); future limits designed.
- IV Security/privacy: PASS current source scope; **History sync disabled until peer authentication + ACL**.
- V Test-backed: PASS 9 automated tests, detected one intentional test failure.
- VI Truthful UX: PASS source and window/process title; physical offline UI check pending.
- VII Spec-driven: PASS docs/source alignment; M0 not claimed Verified.

No exception to Constitution MUST is approved. Future sync security is a deliberate non-implemented feature, **not** an exception permitting insecure data sharing.

## Outstanding before final `M0 Verified`

1. Validate the exact build/launcher/tests on **Temurin JDK 21** on target Windows (or explicitly amend approved toolchain requirements).
2. Perform physical offline/no-peer GUI acceptance and inspect displayed status text via GUI review; ensure 5 cycles still pass.
3. Optional but recommended: measure startup/exit timings vs planned performance budget and document target hardware.
4. Once all ACs are supported, rerun `scripts/verify-m0.ps1` and update this report/Constitution Gate 1; then mark eligible tasks and M0 Verified.

## Handoff to 002-lan-networking (preliminary)

- Direct P2P LAN; no mandatory central server.
- TCP + framed JSON v1 protocol is a **draft** requiring real parser and version negotiation, timeout, size bounds and malformed input tests.
- Manual IP/port fallback first, discover peers later; Windows Firewall private profile only.
- Device IDs are not cryptographic authentication. Add safe pairing/trust before disclosing private History.
- Event-based multi-device History sync occurs in 005 only; no SQLite runtime or SYNC messages in M0.
- Media transfer/playback occurs in 006/007 only.

**Decision:** Application foundation is implemented and basic tests pass. **M0 release gate remains OPEN** until the outstanding verification is completed.
