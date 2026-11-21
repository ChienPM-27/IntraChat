# US2 — Swing window and lifecycle evidence

**Executed:** 2026-11-21
**Environment:** Windows 11 x64 desktop, `javaw.exe` JDK 25.0.4, `target/intrachat-0.1.0-SNAPSHOT.jar`.

## Five open/close cycles

| Cycle | Process ID | Window title observed via Windows UI | Close requested | Process gone afterward |
| --- | ---: | --- | --- | --- |
| 1 | 3192 | IntraChat | Yes | Yes |
| 2 | 20380 | IntraChat | Yes | Yes |
| 3 | 22076 | IntraChat | Yes | Yes |
| 4 | 21896 | IntraChat | Yes | Yes |
| 5 | 21824 | IntraChat | Yes | Yes |

**Result:** 5/5 startup and normal window-close process-exit checks passed. Windows window listing identified `SunAwtFrame` title `IntraChat`, and subsequent process checks did not find each closed PID. No force-kill was required.

## UI and network assertions

- MainWindow source displays `Chưa kết nối`, `Chat chưa sẵn sàng` and explicit “no network/history” text; startup uses `SwingUtilities.invokeLater`.
- No socket/peer or database classes are used by this M0 application.
- The window title was observed through the Windows UI tool; internal JLabel text was verified by source review, **not** independently read via accessibility automation.
- The app was tested without requiring a peer. **Internet adapter was not physically disabled** for this check, so a physically offline GUI scenario is still unverified.
- Timed startup/exit performance budgets (≤3s/≤2s) were not measured; only successful completion and process exit were checked.

## Result

**PASS:** five lifecycle cycles and correct window identification.
**Pending:** physical offline UI check and Java 21 runtime test, if required before full M0 release readiness.
