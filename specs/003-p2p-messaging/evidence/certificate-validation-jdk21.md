# M2 certificate candidate validation — Java 21 regression

Date: 2026-11-21. Branch: `003-p2p-messaging`. Windows 11; JDK: `C:\Users\XuanHoai\.jdks\jdk-21.0.12.1+1`. Offline Maven Wrapper 3.9.16.

## RED evidence

New `DeviceCertificateValidatorTest` exercised generated X.509 candidates (Bouncy Castle):
- valid self-signed P-256 candidate and exact pinned-key comparison;
- expired and not-yet-valid;
- invalid certificate signature, P-384 instead of P-256, forbidden CA, missing digitalSignature, incomplete mutual-auth EKU;
- **unsupported critical X.509 extension**.

Command (with JAVA_HOME/PATH set to JDK 21):
```powershell
.\mvnw.cmd -q -o -Dtest=DeviceCertificateValidatorTest test
```
Actual initial exit: **1**. Tests: **4**, failures: **1**, errors: **0**. The unknown critical extension test failed because `DeviceCertificateValidator.validateCandidate` accepted that certificate.

## GREEN and regression

Changed `DeviceCertificateValidator.validateCandidate` to reject `certificate.hasUnsupportedCriticalExtension()` before signature/key checks. Updated P-384 negative test to use a valid self-signed P-384 pair instead of a mismatched signer.

Command:
```powershell
.\mvnw.cmd -q -o clean package
```
Actual exit: **0**, **78 tests**, **0 failures**, **0 errors** (42 Surefire XML reports). Package produced using actual JDK 21; `git diff --check` clean.

## Scope and remaining gates

This is a focused certificate-validation hardening slice, not evidence of trusted pairing or two physical Windows PCs. Unrecognized critical extensions are now rejected before network trust/pinning checks. `T013` negative-test scope has evidence; `T014` remains open because T004 threat-model review and broader algorithm/identity reviewer gate are not completed. M1 Gate 1 and M2 release remain blocked pending two-machine testing. No private keys, message contents, or real device fingerprints recorded.
