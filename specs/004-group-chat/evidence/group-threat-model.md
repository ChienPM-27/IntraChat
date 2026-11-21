# Group threat model — T004 (initial security analysis)

**Scope:** Group V1 on mutually verified CHAT_V2/TLS; Group origin and read receipts are separately signed. No Group payload may enter M1 diagnostics, insecure pairing or v2-only peers. **This document is not an independent security review.**

| Threat / trust boundary | Severity | Mitigation and must-fail test | Owner/Task |
| --- | --- | --- | --- |
| LAN MITM/preface downgrade | Critical | Authenticate TLS pinned peer; mutual capability transcript after READY; fail closed on strip/substitute (T031–T034) | Protocol/security |
| Malicious relay A claims B sent content | Critical | B signs canonical immutable content; C independently pins B; unknown/rotated B key blocks (T019–T020/T025–T026/T035) | Crypto |
| Owner forges viewer C READ | Critical | C signs READ after actual Group Live foreground viewport ≥250ms; B verifies C pin and full correlation (T089–T098) | Receipt/FE |
| Unapproved invite/outsider guessed groupId | High | Member proposal ≠ grant, A explicit approval, D explicit consent + durable JOIN (T049–T058) | Membership |
| Stale revision/rejoin revoked peer | High | Owner serialized durable commit, signed roster digest/epoch, remove before new sends (T059–T062) | Membership |
| Handoff split-brain after A crash | Critical | A irrevocably RETIRES on disk before B becomes active; A+B signed epoch proof; ambiguous => PAUSED (T063–T074) | Owner |
| Offline backup rollback/Windows user compromise | High | DPAPI/ACL/atomic and fresh owner signed state on resume, flag anti-rollback as unsolved under account compromise (T037–T048) | Persistence |
| Oversize JSON, Unicode, queue/resource DoS | High | Group v3 strict allowlist, bounded caps/queues/timeouts, one serialized owner, no EDT block (T027–T030/T087–T088) | Protocol |
| Partial ACK/READ + session replay | High | Recipient-only signature, group/run/session binding, bounded dedup, UNCERTAIN, no auto resend (T083–T100) | Receipt |
| Chat/roster/privacy in logs/temp | High | Metadata-only encrypted storage; no content/keys/tokens in logs or evidence (T037–T048/T118) | Security |

**Stop rules:** no Group user traffic until verified TLS READY + protected Group capability handshake; no trusted origin/READ without independent signature/pin verification; no Group release if two physical PCs, crypto review, DPAPI cross-account or Constitution MUST gates absent. Owner relay sees plaintext at A by design; not independent Group E2EE.
