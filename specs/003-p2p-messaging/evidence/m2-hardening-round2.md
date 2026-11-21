# M2 test-first security and protocol hardening — implementation evidence

**Date:** 2026-11-21 · Branch: `003-p2p-messaging` · Starting HEAD `733eae5` · Windows 11 · Java runtime **Eclipse Temurin 21.0.12.1+1** at `C:\Users\XuanHoai\.jdks\jdk-21.0.12.1+1`. Maven Wrapper 3.9.16, **offline** dependencies cached.

## Reproducible RED → GREEN observations

All focused commands used `JAVA_HOME` and `PATH` explicitly pointed to Temurin 21, then `.\mvnw.cmd -q -o '-Dtest=<TestClass>' test`:

| Test and changed surface | Actual RED result, before code change | GREEN outcome |
| --- | --- | --- |
| `DeviceCertificateValidatorTest.foreignIssuerCannotImpersonateSelfIssuedDeviceCertificate` → `DeviceCertificateValidator` | 5 tests: **1 failure**; foreign-issuer self-signed key was accepted | Require X509 **subject == issuer** before signature and critical-extension check |
| `ChatV2BootstrapCodecTest.allZeroUuidIsReservedOnlyForUnknownTargetNotDeviceOrRequest` → `ChatV2BootstrapCodec` | 3 tests: **1 failure**, all-zero `requestId` decoded | Reject zero request/sender/receiver UUID in parser and typed records; zero allowed **only** in unknown-target sentinel |
| `DeviceIdentityStoreBindingTest.certificateCnMustEqualStoredUuidNotMerelyContainIt` → `DeviceIdentityStore` | 1 test: **1 failure**; local DPAPI blob with certificate `CN=shadow-<UUID>` accepted | Require subject X500Principal name exactly `CN=<storedUUID>` (case-insensitive) |
| `ChatV2CodecTest.reservedUuidCannotBeUsedAsProtectedIdentityOrConversation` → `ChatV2Codec` | 6 tests: **1 failure**, zero UUID accepted in protected identity/session/conversation | Reject all-zero message/session/sender/destination/conversation IDs |
| `ReadReceiptContractTest.readEventTimestampMustBeUtcZNotOffsetOrGarbage` and `.zeroOrMalformedReceiptIdentifiersCannotBeAccepted` → `ChatV2Codec` | 4 tests: **2 failures**, non-UTC timestamp and zero receipt IDs accepted | Require strictly `Z` timestamp for ACK/read, nonzero original/readEvent/PAIR-control IDs |
| `FingerprintVerifierTest.deterministicCanonicalBinaryTestVector`, `P2PConversationTest.independentCanonicalConversationUuidVector` | Independent synthetic SHA-256 inputs calculated with Windows PowerShell and asserted in Java21 | Exact **SAS** `AC81-8B2D-88B5-8CF9` and **conversation UUID** `5bc726bf-5e70-5d11-8b49-774e77637482` match; ordering swapped also passes |

Additional `ChatV2BootstrapCodecTest` checks reject/accept schema, 16KiB preface, unsupported version, malformed UTF-8/JSON, duplicate keys and injected plaintext; `ChatV2CodecTest` checks 64KiB, strict unknown types and distinct ACK/read payloads. No private real device identity or user chat data placed in test resources/repo.

## Full actual verification

Executed:
```powershell
$env:JAVA_HOME='C:\Users\XuanHoai\.jdks\jdk-21.0.12.1+1'
$env:Path="$env:JAVA_HOME\bin;$env:Path"
powershell -NoProfile -ExecutionPolicy Bypass -File scripts\verify-m2.ps1 -Offline
```

**Actual exit 0 — 93 / 93 JUnit tests PASS; 0 failures, 0 errors.** `clean package` and runnable shaded JAR `--diagnostics` PASS. Java21 version printed by script. Actual JAR contains both FE Demo and M2 Live entrypoint classes; M1 plaintext diagnostic path still separate.

**Dependency resolution command** `.\mvnw.cmd -o 'org.apache.maven.plugins:maven-dependency-plugin:3.10.0:tree' '-Dincludes=org.bouncycastle:*,net.java.dev.jna:*' '-Dverbose=true'` exited 0, confirming Bouncy Castle 1.87 transitives and JNA 5.19.1 (see [review](dependency-review.md)).

## Task evidence and honest exclusions

Prepared [threat matrix](threat-model.md), [data-model audit](../data-model.md), [canonical protocol/response matrix](../contracts/chat-v2.md), [Gate 0 review](../checklists/constitution-check.md), and [two-machine Windows guide](../../../docs/windows-lan-setup.md). These support **T004/T005/T006/T014/T019/T020/T023/T024/T036/T038/T057/T058/T079**, with prior local proof of read producer/viewport in [US4 evidence](us4-delivery-read-receipt.md). T003 formal supply-chain approval and T010 Gate 0 signoff remain OPEN.

**NOT RUN, do not infer PASS:** two distinct Windows LAN PCs with Internet unavailable, other Windows OS user attempting to open DPAPI blob, shared M1/v2 port dispatcher, full MITM/forged ACK/replay stress, total RAM/queue limits, explicit uncertain-send manual retry, independent security source audit, 5 GUI cycle performance/accessibility and M1 Gate 1. **M2 remains PARTIAL / NOT VERIFIED**.
