# Spec 004 implementation evidence — core Group prototype, 2026-11-22

**Execution authorization:** User's exact follow-up was “Phê duyệt, tiếp tục thực hiện” after Gate-0 documentation blocker. This authorizes continuing work on source in the existing workspace. **It is NOT an independent crypto/security reviewer signature** and does not certify Gate 0/Gate 1. New Group code is **not wired into Messenger Live/actual TCP**, deliberately retaining existing secure P2P behavior.

**Git baseline:** branch `003-p2p-messaging`, HEAD `b8a8fd5`, dirty working tree before Group. No reset/stash/commit, no overwriting preexisting modified Java files; new source/test files only.

## RED→GREEN changes actually made

| Source area | New files created (not pre-existing) | Test cases | Evidence/status |
| --- | --- | ---: | --- |
| Group typed domain + validation | `domain/GroupIdentity.java`, `GroupMember.java`, `GroupRoster.java`, `GroupMetadata.java`, `GroupGrant.java` | 6 | `GroupIdentityTest` 3/3, `GroupMembershipCapacityTest` 3/3. Group limit 2–5, one owner and unique verified IDs, title Unicode/control checks, immutable fingerprint copies |
| Canonical owner signing and revision | `security/GroupCanonicalBytes.java`, `GroupOwnerSignature.java` | 6 | `GroupCanonicalBytesTest` 3/3, `GroupOwnerSignatureTest` 3/3; fixed ROSTER hash 260B, GRANT hash 210B, forged owner/pin/signature and stale/previous hash negative checks |
| Group V1 strict JSON envelope | `protocol/GroupV1Type.java`, `GroupV1Message.java`, `GroupV1Codec.java` | 5 | `GroupV1CodecTest` 5/5; version 3 / feature GROUP_V1, strict UTF-8/unknown fields/duplicate JSON keys/65KiB cap, exact session ID check, text, ACK and READ. **Codec parsing is NOT verified TLS authorization.** |
| Group metadata DPAPI | `security/ProtectedGroupStore.java` | 3 | `ProtectedGroupStoreTest` 3/3 on same Windows user; CurrentUser DPAPI+ACL+atomic write via existing ProtectedLocalFile, signed roster load always STALE, wrong owner/corruption/rollback and 16-group limit. **Different Windows user token NOT tested.** |
| Owner-side grant control | `application/GroupOwnerGrantService.java` | 3 | `GroupOwnerGrantServiceTest` 3/3: full exact authorized recipient set, owner-only signed ticket, stale/forged owner, removed participant rejected. **No real TLS owner request/response or Group text transport.** |
| Explicit V3 negotiation primitives | `protocol/GroupV3Transcript.java`, `GroupV3BootstrapCodec.java` | 5 | `GroupV3TranscriptTest` 2/2, `GroupV3BootstrapCodecTest` 3/3; v3 transcript digest and SAS; legacy v2 decoder rejects v3; v3 decoder rejects v2; malformed/duplicate/capability mismatch rejected. **No shared dispatcher or TLS V3 transport implemented.** |

**Total Group tests:** 28 JUnit tests, failures=0, errors=0, skipped=0, across **9** test classes.

### Actually executed commands / results

Selected JDK: `C:\Users\XuanHoai\.jdks\jdk-21.0.12.1+1` (Temurin Java **21.0.12.1+1**).

```powershell
$env:JAVA_HOME='C:\Users\XuanHoai\.jdks\jdk-21.0.12.1+1'
$env:PATH=$env:JAVA_HOME+'\bin;'+$env:PATH
.\mvnw.cmd -o -q '-Dtest=GroupIdentityTest,GroupMembershipCapacityTest' test
.\mvnw.cmd -o -q '-Dtest=GroupCanonicalBytesTest,GroupOwnerSignatureTest' test
.\mvnw.cmd -o -q '-Dtest=GroupV1CodecTest' test
.\mvnw.cmd -o -q '-Dtest=ProtectedGroupStoreTest' test
.\mvnw.cmd -o -q '-Dtest=GroupOwnerGrantServiceTest' test
.\mvnw.cmd -o -q '-Dtest=GroupV3TranscriptTest,GroupV3BootstrapCodecTest' test
powershell.exe -NoProfile -ExecutionPolicy Bypass -File scripts/verify-m2.ps1 -Offline
```

- Before each new implementation slice, the corresponding test files were written and Maven compile was executed: **RED compilation failure because new class symbols did not exist** (observed missing GroupIdentity/GroupRoster, GroupCanonicalBytes/GroupGrant, GroupV1Codec, ProtectedGroupStore, GroupOwnerGrantService, GroupV3Transcript/Bootstrap).
- Subsequent focused test commands above: **all exit 0**. One intermediate GroupIdentity GREEN exposed control-character stripping and failed **1/6**; fixed validation, reran and achieved 6/6 PASS. No test was weakened to make it pass.
- Final full offline `scripts/verify-m2.ps1 -Offline` **exit 0**, **141/141 JUnit PASS**, failures/errors=0; shaded JAR and one Messenger launcher diagnostics PASS. Previously baseline was **113/113**, so +28 Group tests. 9 additional XML suites were verified with exact tests/failures from `target/surefire-reports/TEST-*.xml`.
- **No real Group network send and no Group UI acceptance run**. Two-host LAN, peer SAS Group V3 flow, independent review and second-user DPAPI remain NOT TESTED.

## Security scope / unresolved work

1. Group V1 codec and V3 transcript are **standalone, not enabled in `TlsChatSession`/SecureChatCoordinator**; legacy v1/v2 remain unchanged. This prevents an unreviewed Group feature from sending user data over M1 plaintext or unnegotiated v2 sessions.
2. GroupOwnerGrantService accepts an `authenticatedSender` argument which **must** be supplied only by a future handler that has validated TLS peer identity, owner roster, invite consent and committed revision. Do not expose it as a public unauthenticated network endpoint.
3. ProtectedGroupStore accepts a map of **trusted owner keys** which the future caller must derive from actual M2 pinned peers or local DeviceIdentityStore — NEVER from JSON fields in Group messages. Store reload is STALE and never alone enables Group sends.
4. No independent crypto/security signoff: fixed contract vectors were reproduced by local Java21 tests, but not externally audited. The user authorized development but **did not provide independent reviewer**. Task T004/T010 remain open for independent security and license/privacy gate; Gate 1 not Verified.
5. Main blockers for Live implementation: M2 shared version dispatcher and pairing-profile-negotiated group TLS (open upstream tasks), Group authority network invitation/roster fanout, real per-recipient UI visibility, and integration tests. Final release also needs a second physical Windows 11 LAN host and an authorized second Windows account for DPAPI cross-user.

**Summary:** Real domain/crypto/codec/DPAPI/control primitives were implemented/tested. **Group Live end-to-end feature NOT IMPLEMENTED and M3 NOT VERIFIED.** Do not infer that new `GROUP_V1` classes are active just because the shaded JAR compiled.