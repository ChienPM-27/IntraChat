# M2 external verification blockers — explicit stop conditions

**Date:** 2026-11-21 · **Branch:** `003-p2p-messaging` · **State:** BLOCKED for M2 acceptance, **NOT** an M1/M2 release signoff.

## B1 — Two distinct Windows LAN PCs unavailable to this test run

Only **one Windows 11 desktop is accessible** through the connected Windows tooling in this session. Local two-process `127.0.0.1` TLS chat is not the same as two physical hosts. Required tasks:

- `T080` actual A/B first-pair, two human Accept/SAS confirmations, TLS1.3 READY, LAN available while Internet unavailable;
- `T081` actual A→B and B→A message/sender/time/emoji and foreground viewport READ/ACK;
- `T082` actual A/B disconnect/restart/changed key/inbound reject/offscreen/privacy/negative tests;
- `T084` M1 Gate 1 real two-machine evidence (M1 is **In Progress**, not Verified; independently verify the M1 acceptance report).

**Blocked facts:** no second independent Windows endpoint connected, no access to two authorized human operators for SAS, no evidence of a LAN-with-no-public-Internet setup on both. No synthetic localhost screenshots or one-host JVM logs can satisfy these tasks. **Resolution:** provide access to **two authorized Windows 11 hosts** in the same Private/approved LAN, separate application device profiles, safe Internet disconnection that preserves LAN; run procedures from [quickstart](../quickstart.md) and [Windows guide](../../../docs/windows-lan-setup.md) on **both** machines, record real results. Do not silently change Public Firewall rules.

## B2 — Independent dependency/security gate requires a reviewer

`T003` still needs formal up-to-date dependency CVE/SBOM/provenance and license signoff (Bouncy Castle 1.87 / JNA 5.19.1); `T010` Gate 0 depends on that independent approval. `T076/T083/T087/T090` require complete adversarial security/privacy code review and real two-PC evidence and/or independent reviewer signoff. The current implementation assistant cannot self-certify independent review.

**Resolution:** appoint a separate qualified maintainer/security reviewer authorized to approve dependency license/use and inspect key binding, JSSE trust, SAS, DPAPI ACL, replay/DoS, packet capture, and 2-PC results; record signed dated findings and fix critical issues. See [dependency review](dependency-review.md), [threat model](threat-model.md), and [Constitution gate](../checklists/constitution-check.md).

## B3 — Windows second-user DPAPI test unavailable

`T015` exercised ACL owner-only on the active account, encrypted persistence/restart/corruption and negative partial parent-write tests, but **cross-Windows-user DPAPI access was NOT RUN**. A second authorized local Windows account with a different logon security token and safely isolated test identity is needed before claiming that additional security property; never present the missing test as passed.

## Distinguish unblocked coding backlog

Remaining tasks also include independent code/tests for v1/v2 shared dispatcher, secure-session resource stress, manual uncertain-send retry, FE failure-state rendering, etc. These are **OPEN**, not falsely labelled as external-only blockers. B1/B2 block **release/90-of-90 completion** regardless of any local JUnit count; none of `T080–T084`, `T087` or `T090` may be checked until actual evidence exists.

**Decision:** stop the current 90/90 push at the first hard external verification gates rather than fabricating two-host or independent-review PASS. M2 remains IN PROGRESS; the local test script does not assert M1/M2 Verified.
