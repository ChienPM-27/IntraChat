# T003 — History trust and threat model (2026-11-21)

**Scope:** protected local Windows user History of opted-in text+emoji and optional manually reconciled authenticated LAN peer copies. This document is design threat mapping, **not independent security approval**.

| Threat | Boundary | Mitigation | Mandatory negative test | Severity |
| --- | --- | --- | --- | --- |
| LAN MITM strips v2 optional History capability | Untrusted TCP preface → TLS authenticated protocol | Bind both raw offers/nonce/IDs/session after M2 PAIR_READY; fail closed | T065/T066; v2-only and downgrades | Critical |
| Pinned outsider C requests A↔B | Authenticated TLS peer → conversation authorization | exact two-device IDs+independent SPKIs+opt-in before summary | T029/030/091/092; 0 count/metadata leak | Critical |
| Group A relays forged B origin | Verified hop A → claimed sender B | B origin detached signature + C independent B pin, 004 signed roster | T027/028/031/032/106/107 | Critical |
| M2 old RAM message lacks durable seal | TLS hop receipt → later sync provenance | future sender seal only; no fabricated legacy signatures or offline drafts | T027/T071/T072 | Critical |
| Group new D requests prejoin history | Group member → owner committed chronology | ownerSequence/JOIN revision cutoff; no clock-only ACL | T019/020/098/099 | Critical |
| Group removed C uses in-flight cursor | Owner durable revoke → outstanding Sync queues | recheck membership before every new frame, drop queues | T102/103/128 | Critical |
| Group handoff split brain | Owner epoch and signed continuity | dual signed handoff, no stale A/auto-election | T033/034/104/105 | Critical |
| Normal SQLite DB/WAL/temp is plaintext | DPAPI-wrapped key → native SQLite pages | reviewed **encrypted SQLite** backend or BLOCK; separate DPAPI/ACL; wrong user and synthetic on-disk scan | T005/006/039–042/047/048/131 | Critical |
| Symlink/junction swaps protected DB | Local filesystem untrusted path | canonical owner dir/read/write ACL, reject reparse | T047/048/131 | High |
| Corrupt DB/driver/key/migration | Durable store → recovered UI | fail closed, ACID rollback, no auto reset or backup | T043–046/124 | High |
| Deleted local event reimported | Anti-entropy → local deletion fence | tombstone/conversation generation persists across compaction | T059/060/085/086/129 | High |
| Same ID signer/body changed | Imported peer copy → local timeline | scope-specific hash and signer match or quarantine | T015/016/049/050 | High |
| History APPLY_ACK treated as chat ACK/READ | Local copy state → live recipient state | copy proof separate from live viewer receipt, receipt not archived | T021/022/089/090/130 | High |
| Malformed/flooding peer freezes Swing | TLS reader → codec/DB/UI | bounded 65536 frame, budgets, workers off EDT and cancellation | T069–080/132 | High |
| Privacy leakage logs/export | Transient decrypted content → disk/log/Git | no content logs, no backup/export, no plaintext side files | T063/064/131/135 | High |

**Implementation workstream owners (roles, not assigned people):** M2 transport/capability owner handles MITM/session/downgrade and resource quotas; independent identity/protocol cryptographer reviews signer/canonical bytes; 004 Group owner handles origin/membership/handoff/revoke; encrypted SQLite/native security owner handles DB/DPAPI/ACL/WAL/temp/migration; History domain/store owner handles tombstones/conflicts; Swing Live owner handles receipt/status/EDT/privacy. Shared files have one editor at a time per [ownership register](shared-file-ownership.md). Each must supply executable negative evidence from the table before production release.

**Independent review owner:** independent authorized security reviewer **NOT PRESENT / NOT SIGNED**. This agent cannot sign off its own native cryptography or dependency selection. Mark production crypto/storage and release **BLOCKED** until real review evidence.
