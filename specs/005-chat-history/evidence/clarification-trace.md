# T002 — Confirmed CL-05 trace (2026-11-21)

All ten CL-05-01…10 **USER CONFIRMED option A** in [decision record](../clarifications.md). No new product clarification is pending. Source [spec](../spec.md) 48 FR/12 SC/16 AC and [plan](../plan.md) cover nine ROADMAP 05.01–05.09 entries.

| CL | Binding design | Architecture / test task chain |
| --- | --- | --- |
| 01 | First-use opt-in, future content only | T017/018/051/052/111/112; AC-A/N |
| 02 | Fully encrypted SQLite, independent DB key DPAPI CurrentUser, owner ACL, reviewed driver | T005/006/037–048/131; AC-L/D; **driver review OPEN** |
| 03 | 90 days and 500 MiB *both enforced* by default; configurable | T023/024/057/058/117/118/129; AC-J/N |
| 04 | Manual Sync after verified M2 reconnect, selected conversation | T065/066/087–096/115/116; AC-E |
| 05 | Exactly two verified deviceId/SPKI P2P participants | T011/012/029/030/083/084/091/092; AC-F |
| 06 | Group owner only, after JOIN and before REMOVE; stop issuance after REMOVE | T019/020/031–034/097–110/128; AC-H/I; **004 blocked** |
| 07 | Local-only deletion, durable tombstone; no remote wipe | T017/018/059–062/129; AC-J |
| 08 | Never persist/sync application APP_ACK or READ | T021/022/063/064/089/090/130; AC-K |
| 09 | Immutable event; same ID altered origin/body quarantined | T015/016/049/050/085/086; AC-C |
| 10 | No backup/export, only safe schema migration | T045/046/063/064/129; AC-D/N |

**Design assumption vs actual approval:** The 90d/500MiB defaults and other ten choices are binding. Proposed 48KiB batch, 50-page, crypto wire v4 and native driver version are still **technical design targets requiring review/tests**, not approval. Full product scope requires Group 004 actual runtime.

**T002 Done:** Decision trace complete; no mistaken signed-off Gate 0.
