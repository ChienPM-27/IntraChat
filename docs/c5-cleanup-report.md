# C5 — Dead code audit / source cleanup report (23/11/2026)

## Detailed continuation (24/11/2026)

A separate evidence-oriented follow-up is available in [C5 detailed audit](c5-detailed-audit.md). It covers invocation routes of low-reference private methods (including Swing and `this::` callbacks), socket/worker/history/media ownership, a trace of the approval timer's ordering relative to EDT dispatch, and unresolved concurrency tests.

- Source change: removed one confirmed unused wildcard import `java.util.concurrent.atomic.*` from `GroupService.java`.
- No additional production class, method, field, listener, resource, script, or dependency removed. The private-member regex counts are *screening candidates only*, not definitive call-graph coverage.
- Post-change JDK 21 offline JUnit: **96/96 PASS**. Final `verify-all.ps1`: **exit 0**, P1–P5 PASS. Shaded JAR `--diagnostics`: **exit 0**; `git diff --check`: **exit 0**. Final logs: `%TEMP%\intrachat-p6-verify-0af6a2a0ceb248788d1f948a89cfeff6`.
- The GUI timer starts before its dialog's EDT creation callback; this is a specific scheduling risk, not a reproduced GUI defect. The finite 30-second timeout remains unchanged.
- C5 remains **PARTIAL**, since per-member resolved call-graph and device/GUI stress acceptance are incomplete.

## A. Trạng thái

**C5 PARTIAL — safe source cleanup implemented, backend verified; full static/lifecycle and GUI acceptance pending.**

- Repository: `D:\Documents\Lap_trinh_ung_dung_mang\Tuan08\IntraChat`.
- Branch: `main`; HEAD: `f9b415af9dad8e312cac4a9d7c099411f7d50390`.
- Backup: `D:\Documents\Lap_trinh_ung_dung_mang\Tuan08\IntraChat-C5-backup-20261123`.
- Backup includes **103 files** excluding `.git` and `target`, plus Git working-tree status, staged/unstaged diffs, untracked list, previous-deletion list, branch/HEAD and SHA-256 manifest. **0 mismatches**; temporary restore of `pom.xml`, `ui/IntraChat.java` and `docs/c4-cleanup-report.md` **3/3 PASS**.
- Pre-existing C0–C4 tracked deletions, modifications and untracked reports remain intact. No Git commit, reset, stash or clean.

## B. Inventory

| Hạng mục | Trước C5 | Sau C5 |
|---|---:|---:|
| Java production files | 46 | 46 |
| Java test files | 30 | 30 |
| Scripts (PS1 + Java smoke) | 13 | 13 |
| Main/test resources | 0 | 0 |
| Direct Maven dependencies | 3 | 3 |
| Java classes deleted | 0 | 0 |
| Test files deleted | 0 | 0 |
| Script/resource files deleted | 0 | 0 |

See [C5 code cleanup inventory](c5-code-cleanup-inventory.md), which identifies all 46 Java source files and their cross-file usage candidates. **No confirmed dead production class**: references exist to all 46; this alone does not prove absence of unused members.

## C. Changes made

- Removed **4 verifiably unused imports**: `model.ChatUser` in `ChatActionHandler`; `ui.components.ChatStyle` in `LanConnectionDialog`; `java.time.Instant` in `LanCoordinator`; `vn.intrachat.application.FileTransferService` in `HistoryRepository`.
- No behavior, wire protocol, JSONL format, UUID, status semantics, GUI layout, timeout values, dependency versions or script interfaces changed.
- No class deletion, method deletion, field deletion, listener removal or worker redesign without high-confidence caller/lifecycle proof.
- Tests and process smokes retained to protect F01–F11.
- Documents added: `docs/c5-code-cleanup-inventory.md`, `docs/c5-cleanup-report.md`; roadmap and P6 report updated.
- Historic C4 deletions of Demo/Login/mock and legacy docs are **not counted** as C5 deletions.

## D. Code audit and risks

- Production reference scan performed for 46 Java files against production, test and script sources. Source inspected across application, protocol/transport, persistence, UI/media and listeners with focus on legacy references, timeout and resource lifecycle. Limited single-occurrence method scan surfaced callbacks/method references but no deletion-safe evidence.
- `ConversationRamBudget` (RAM protection), `ChatViewMapper` (UI mapping), `AppInfo` (diagnostics), `LocalProfile` (stable UUID/data directory), `GroupService` (F02), `FileTransferService` (F03/F06–F08), `WindowsForegroundGuard` and `LiveMessageVisibilityObserver` (F09), `VoiceRecorder` and `MessageMedia` (F08/media) retained due to functional role.
- `LanConnectionDialog.approveIncoming` sets a 30-second finite decision timeout; `LanCoordinator.accept` separately awaits completion using `NetworkConfig.APPROVAL_TIMEOUT_MS`. The C4 GUI Accept timeout is not reproducibly explained by the static inspection. No value changed or automatic acceptance introduced. Potential EDT display delay and approval race require interactive test before changing semantics.
- No additional GUI listeners or resource leaks conclusively established; **full per-method call graph, deep resource lifecycle and interactive EDT checks not complete**. Therefore C5 cannot responsibly be labelled COMPLETE.
- Maven Shade warnings about `module-info` and overlapping `META-INF/NOTICE`, `LICENSE`, `MANIFEST.MF` remain. Runtime JAR works; warning not hidden or suppressed.

## E. Verification

| Check | Result |
|---|---|
| Baseline `mvnw.cmd -o clean test package` (JDK 21) | PASS |
| Post-cleanup `scripts/verify-all.ps1` (JDK 21) | PASS / exit 0 |
| JUnit | **96 tests / 30 suites / 0 fail / 0 error** |
| P1 TCP | PASS |
| P2 Group Owner Relay | PASS |
| P3 File | PASS |
| P4 History | PASS |
| P5 Media | PASS |
| P2 stability (3 consecutive independent runs) | PASS / PASS / PASS |
| Executable JAR `--diagnostics` | PASS / exit 0 |
| JAR includes live service/group/file/history classes | PASS |
| `git diff --check` (during verification) | PASS |
| GUI startup this C5 run | NOT RUN |
| GUI P2P Accept/Reject | NOT RUN |
| Physical LAN | NOT RUN |
| Real video/audio device playback | NOT RUN |

Verification logs: `%TEMP%\intrachat-p6-verify-d0d293a9d3f8469eb88621a92c5d34d3`. P2 stability logs: `%TEMP%\c5-p2-stability-1.log`, `-2.log`, `-3.log`.

## F. Outstanding / C6

- Complete full method/field/interface call-graph and lifecycle analysis before any additional deletion.
- Interactively verify Listen/Connect/Accept/Reject in two live GUI JVMs (including delay near timeout), EDT behavior, and correct cancellation/expiration UI.
- Physical LAN and extended real media playback acceptance remain C6 tasks.
- No evidence supports marking GUI P2P, physical LAN, media device playback or P6 as fully accepted.

## Follow-up verification — 24/11/2026

- Confirmed branch `main`, HEAD `f9b415af9dad8e312cac4a9d7c099411f7d50390`; pre-existing uncommitted C0-C4 changes remain untouched.
- Revalidated existing C5 backup SHA-256 manifest: **103/103 matches**, and restore checks for `pom.xml`, `src/main/java/ui/IntraChat.java`, `docs/c4-cleanup-report.md`: **3/3 matches**.
- Re-ran JDK 21 Maven offline tests: **96/96 PASS**, 0 failures/errors/skips.
- Re-ran `scripts/verify-all.ps1` with JDK 21: **exit 0**, P1/P2/P3/P4/P5 all PASS, `git diff --check` PASS. Logs: `%TEMP%\\intrachat-p6-verify-e9d1cf6a36934289a65f702858154ae0`.
- Re-ran shaded JAR `--diagnostics`: **exit 0**; JAR announces TCP plaintext. Manual `git diff --check`: **exit 0**.
- Re-inspected `LanConnectionDialog.approveIncoming` and `LanCoordinator.accept`: dialog starts its 30-second completion timer before `SwingUtilities.invokeLater` displays the dialog, while coordinator enforces a separate 30-second approval wait. This makes EDT scheduling delay a plausible timing contributor, **not a verified root cause**; changing either timer without a reproducible regression would be unsafe.
- No newly confirmed deletion-safe class, method, field, listener or dependency during this follow-up. No production code or timeout behavior changed. GUI P2P, physical LAN and real media playback remain NOT RUN.
- Status remains **C5 PARTIAL**: additional per-member call-graph and interactive EDT/lifecycle tests are required before claiming a complete audit.

**Verdict: C5 PARTIAL**, because the requested comprehensive per-member/lifecycle audit and GUI timing investigation have not reached a deletion-safe, exhaustive conclusion, despite passing backend regression and preserved working tree.
