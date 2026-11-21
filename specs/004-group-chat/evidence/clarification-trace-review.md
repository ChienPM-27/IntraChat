# Confirmed CL-04 trace — T002

**Date:** 2026-11-21. Crosschecked `spec.md`, `plan.md`, `research.md`, `data-model.md`, `contracts/group-v1.md` and `tasks.md`. This is a document consistency check, not an external approval.

| Decision | Confirmed requirement and design evidence |
| --- | --- |
| CL-04-01 | Owner relay over verified TLS, old Group owner offline => PAUSED; plan §§1/5/6 |
| CL-04-02 | Active member **proposes**, owner **approves**, invitee separately **accepts**, then owner durable join commit; plan §5.2 |
| CL-04-03 | Voluntary dual-signed owner handoff with irreversible old-owner retire, successor durable activation; plan §5.4 |
| CL-04-04 | DPAPI CurrentUser protected metadata only; no persisted text/receipt/draft; plan §5.5 |
| CL-04-05 | Owner + maximum four member sessions = five devices; M2 MAX_ACTIVE=4 shared, not additive; plan §7 |
| CL-04-06 | Viewer-signed per-recipient READ after Live foreground viewport ≥250ms; plan §6.1 |
| CL-04-07 | New joiner sees no prejoin transcript/backfill; History 005 separate; plan §5.2 |
| CL-04-08 | REMOVE effective on **owner durable revision commit**; cannot retract precommit bytes; plan §5.3 |

**Review note:** independent member SPKI pinning before believing signatures introduces additional M2 first-pair sessions. Existing M2 can pin only current peers and has no Group key distribution; plan correctly labels this as implementation work. The Group v3 signature canonical binary format and capability negotiation are **design-draft, NOT interoperability-reviewed**. Gate 0 may remain CONDITIONAL pending those reviews.
