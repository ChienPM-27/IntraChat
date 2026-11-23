# Spec 004 requirements & test readiness checklist

**Created:** 2026-11-22 · **Status:** DESIGN READY FOR REVIEW; **all runtime checks NOT RUN**
**References:** [Spec 004](../spec.md) · [Plan](../plan.md) · [Tasks](../tasks.md) · [Wire contract](../contracts/group-v1.md) · [Data model](../data-model.md).

## Requirements coverage (document review, NOT test PASS)

| Area | Spec references | Planned tasks | Design status |
| --- | --- | --- | --- |
| Identity / invite / owner / revision | FR-04-001…009, US1/3 | T011–018, T041–054, T075–078 | SPECIFIED; source absent |
| Group protected text / Unicode / sender / time | FR-04-010…017, US2 | T019–032, T055–064 | SPECIFIED; source absent |
| Per-recipient ACK / READ and retry | FR-04-015,018–020, US4 | T063–072, T079–080, T089 | SPECIFIED; Group receipt absent |
| Live UI/Demo isolation | FR-04-021…023,027, US6 | T073–080, T086 | SPECIFIED; Group Live disabled |
| Windows-protected metadata; RAM-only chat | FR-04-024,028–029, US5 | T033–040, T071–072, T085 | SPECIFIED; Group store absent |
| Bounds, EDT / fault and release | FR-04-025–026,030, NFR-04-001…010 | T029–032, T081–092 | DESIGN TARGET; unmeasured |
| Accepted clarifications | CL-04-01…06 | T002, T010 and per-phase DoDs | CONFIRMED by user; security ADRs not signed |
| Acceptance A…L | AC-04-A…L, SC-04-001…010 | T081–092 | ALL NOT RUN / gated |

## Constitution I–VII: readiness, not a release checklist

- [ ] I — demonstrate LAN no Internet with **two real physical Windows 11 devices**, ≥3 distinct authorized test identities and manual IP/port; no content relay.
- [ ] II — peer identity (pinned key, not alias), strict GROUP_V1 schema and negotiated profile; test legacy v1/v2 and downgrade.
- [ ] III — measured bounds under 5-member mesh, queue/pending quotas, no UI EDT hangs or leaked workers.
- [ ] IV — independent reviewer signed owner grant revocation race, TLS mutual pin, DPAPI CurrentUser/ACL, forged events, no secrets in logs.
- [ ] V — RED→GREEN test evidence with actual JDK21 command/exits; 3-process real TLS and >=2-host LAN evidence.
- [ ] VI — actual Messenger Group Live UI with keyboard/focus, per-recipient truth and visible foreground-only READ.
- [ ] VII — updated docs/tracker with only evidence-supported implementation claims, approved ADRs, signed gates.

## Acceptance experiment matrix (all NOT RUN)

- [ ] AC-04-A: 3 real verified A/B/C each send/receive Unicode/emoji text.
- [ ] AC-04-B: D outsider/unverified/forged owner/group never obtains authorization.
- [ ] AC-04-C: invite Accept/Reject/Expire; owner signed N+1; member self-leave and owner remove.
- [ ] AC-04-D: owner offline and other peer disconnect/partial delivery without hang.
- [ ] AC-04-E: Group vs P2P contexts, duplicate names, sender/time/Unicode, no Demo leak.
- [ ] AC-04-F: distinct per-recipient app ACK and true foreground READ.
- [ ] AC-04-G: replay/duplicate/forged ACK/signature/wrong revision/session negative matrix.
- [ ] AC-04-H: metadata persists securely, user content/drafts/receipts RAM only, manual retry.
- [ ] AC-04-I: one Messenger, Group Live gated, unsupported File/History/Media remain gated.
- [ ] AC-04-J: ≥2 Windows 11 physical hosts, ≥3 identities/processes, LAN works without public Internet.
- [ ] AC-04-K: Java21 automation, privacy/stress/ACL/independent security approval.
- [ ] AC-04-L: upstream M1/M2 Gate 1 Verified and independent Constitution 004 Gate 1 signoff.

**Known blockers:** second physical Windows PC; second authorized Windows account for cross-user DPAPI; independent qualified security/dependency reviewer and reviewer-approved Gate 0 before Group code. See [M2 blocker](../../003-p2p-messaging/evidence/execution-blockers-2026-11-22.md). This checklist intentionally marks no test PASS.