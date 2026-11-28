# C5 — Detailed call-site, lifecycle and EDT audit (24/11/2026)

> **Ghi chú 28/11/2026:** Đây là snapshot bằng chứng lịch sử tại ngày ghi trên báo cáo; số file, commit, kết quả PASS/NOT RUN và phạm vi audit bên dưới không phải hiện trạng mới nhất. Xem [kiểm chứng hiện hành](current-verification.md), [ma trận](feature-matrix.md) và [ROADMAP](../ROADMAP.md) cho các thay đổi sau C6 (Group Media, auto-save, auto reconnect và GUI Settings).

## Scope, baseline and methodology

Repository `main` at `f9b415af9dad8e312cac4a9d7c099411f7d50390`. This audit continues the existing C5 inventory without changing C0–C4 uncommitted work. The pre-existing C5 backup has 103/103 SHA-256 manifest matches and 3/3 temporary file restore checks (see `c5-cleanup-report.md`).

Counted **46 production Java source files**, **30 Java test source files**, **13 scripts**, **0 resource files**, and **3 direct Maven dependencies**. A conservative source-regex pass found about **113 private-method declaration candidates** and **192 private-field declaration candidates**; these are *syntactic candidates, not resolved Java symbols or proof of reachability*. Inspected usages by direct calls, method references (`this::method`), Swing event callbacks, listener subscriptions, scheduled tasks, test references, entry point/JAR, resource-loading paths and lifecycle owners. The existing C5 per-class inventory remains the class-level catalogue.

## Evidence-driven private-method / callback triage

| Symbol / owner | Observed invocation route | Disposition |
|---|---|---|
| `LanConnectionDialog.approveIncoming` | `LanCoordinator(..., this::approveIncoming, ...)` | KEEP, handshake approval |
| `LanConnectionDialog.onConnected` | `LanCoordinator(..., ..., this::onConnected)` | KEEP, attaches TCP session |
| `LiveChatAdapter.onEvent` | `LiveChatService.addListener(this::onEvent)`, plus EDT redispatch | KEEP |
| `LiveChatAdapter.onFileEvent` | `LiveChatService.addFileListener(this::onFileEvent)` | KEEP |
| `LiveChatAdapter.onGroupEvent` | `GroupService.addListener(this::onGroupEvent)` and EDT redispatch | KEEP |
| `MainChatFrame.manuallyRetry` | `MessageBubblePanel(..., this::manuallyRetry, ...)` | KEEP, explicit uncertain-state retry |
| `MainChatFrame.fileAction` | `MessageBubblePanel(..., this::fileAction)` and `showFileTransfer(...)` | KEEP |
| `MainChatFrame.checkLiveVisibleRows` | 100 ms `javax.swing.Timer`, stopped in `dispose()` | KEEP, foreground READ |
| `FileTransferService.expire` | `timer.scheduleAtFixedRate(this::expire, ...)` | KEEP, finite transfer deadline |
| `GroupService.expireInvitations` | `CompletableFuture.delayedExecutor(...).execute(...)` | KEEP, invitation deadline |
| `MessageMedia.VoicePlayer.toggle/tick` | Swing button listeners and 85 ms timer | KEEP, playback |
| `ComposerPanel.showEmojis` | Swing action listener | KEEP |
| `CreateGroupDialog.confirm` | Swing action listener | KEEP |
| `GroupService.roster/parseRoster` | Group protocol serialization / inbound roster handling | KEEP |

**Important limitation:** text-regex hit counts alone cannot establish unused methods/fields, especially overloaded, constructor, `record`, `::`, reflection or framework-driven code. No extra method or field was deleted on that basis. Broad import scan did not find other definitive explicit-type unused imports. `GroupService` had one confirmed unused wildcard import `java.util.concurrent.atomic.*`, removed in this continuation.

## Lifecycle ownership and review

| Component | Evidence and safeguards | Open risk / status |
|---|---|---|
| `LanCoordinator` | Virtual-thread executor, bounded pending candidate semaphore, HELLO deadline, approval wait, socket closure in unsuccessful `accept`, disconnect and executor shutdown | Approval wait is finite but EDT scheduling can consume the UI's decision window; GUI interaction still pending |
| `LanSession` | Single virtual reader, bounded socket read timeout, synchronized writes, atomic idempotent close, callback on disconnect | No reproduced leak or duplicate reader |
| `LiveChatService` | Peer detach, pending send release, scheduled ACK handling, `FileTransferService` close, deadline/worker shutdown, `HistoryRepository` close | Concurrent shutdown vs in-flight sends requires a dedicated stress test |
| `GroupService` | Invitation timeout, owner relay, ACK ledgers, `close` clears maps and resolves creation futures | Delayed executor tasks are not individually cancelled, but check `closed`; no proven failure |
| `FileTransferService` | One active transfer per peer, 32 KiB bounded chunks, CRC32, stream try-with-resources, temp cleanup, deadline timer and worker shutdown | Cancel-vs-chunk and completion-vs-disconnect interleavings need stress test before changing concurrency |
| `HistoryRepository` | Single bounded writer (1024), append-only JSONL, force, flush, close, reader try-with-resources | Concurrent `close` vs queue/flush is a candidate for stress testing; do not modify format or history |
| `MainChatFrame` | EDT timer stopped, composer/media closed, background executor shut down, adapter closed on dispose | GUI acceptance not run |
| `MessageMedia` | Background image/audio work, EDT result rendering, playback clip closed in `stopPlayback/close`, pending clip closes when view no longer active | Hardware/device playback not verified |
| `VoiceRecorder` | Microphone line is in try-with-resources, bounded 120-second PCM capture, `stopped` and `cancelled` flags | `TargetDataLine.read` may block after cancellation until audio API wakes it; **REVIEW**, requires controllable audio fake/device repro |
| `LanConnectionDialog` | TCP Listen on worker, Connect asynchronous, Swing updates via `invokeLater`, close shuts down coordinator/live/workers | Approval dialog timer begins **before** its EDT display callback; static inspection cannot establish actual displayed time or timeout root cause |

## GUI Accept timeout: concrete ordering

1. TCP reader receives HELLO; `LanCoordinator.accept` requests an approval stage.
2. `LanConnectionDialog.approveIncoming` calls `decision.completeOnTimeout(false,30,SECONDS)`.
3. Only then does it enqueue UI creation with `SwingUtilities.invokeLater`.
4. `LanCoordinator.accept` separately waits `NetworkConfig.APPROVAL_TIMEOUT_MS = 30000` for the same future.
5. The Accept listener attempts `decision.complete(true)`. If a timeout already completed the future, this returns false and the Accept cannot reverse it.

**Impact:** elapsed time before dialog is displayed reduces the visible acceptance window. This is a code-level timing hazard, **not proof** that C4's observed timeout was caused by EDT stall. Neither timeout was made infinite or increased. C6 interactive two-JVM test should measure incoming HELLO, EDT dispatch, dialog visible timestamp, Accept callback and backend decision using bounded diagnostic logging or test hooks.

## Dependency / packaging / functional protection

- Maven uses Java 21, FlatLaf, Jackson, JUnit Jupiter (test scope) and Shade. No dependency removed without reference proof.
- JAR entry point remains `ui.IntraChat`. Media / READ classes and validation, historical JSONL compatibility, UUIDs, transfer integrity and group owner relay were retained.
- No new class removed; no test, resource or script removed. Removal counts for C0–C4 are not attributed to C5.
- Known Shade warnings about overlapping `META-INF` artifacts require runtime diagnosis before suppression.

## Verification for this continuation

- JDK 21 `mvnw.cmd -o test` **96 tests, 0 failure, 0 error, 0 skipped**, exit 0 after import cleanup.
- `git diff --check` exit 0.
- Previous same-day full `verify-all.ps1` returned exit 0 for P1–P5; re-run after the final documentation change to obtain current-session final evidence.
- Interactive GUI, physical LAN, actual speaker/microphone playback: **NOT RUN**.

## Follow-up classification

**C5 PARTIAL.** Confirmed dead import handled, all 46 production source files remain functionally referenced in the existing class inventory. A full symbol-resolved per-member call graph, hardware/media lifecycle stress tests and interactive GUI expiry testing were not completed. Candidates without evidence stay **REVIEW**, not DELETE. There is no justified mass class or method deletion.
