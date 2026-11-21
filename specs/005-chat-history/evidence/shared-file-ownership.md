# T009 — Phase05 edit ownership and isolation (2026-11-21)

**Current user main worktree:** `D:\Documents\Lap_trinh_ung_dung_mang\Tuan08\IntraChat` on `003-p2p-messaging`, numerous pre-existing unstaged modifications and untracked files; **do not stage, reset, modify or overwrite**. Execute History tasks in independent temporary `005-chat-history` worktree, push only the 005 branch.

| Shared file | Single-owner integration protocol | Required tests |
| --- | --- | --- |
| `pom.xml` | SQLite dependency owner only after T005/T006 actual review | Maven dependency tree/license/CVE, full Java21 regression |
| `protocol/ChatV2BootstrapCodec.java` | one protocol owner extends optional HISTORY_V1 but v2-only stays valid | M2 bootstrap downgrade/strict tests |
| `transport/TlsChatSession.java` | one shared TLS reader/dispatch owner | M1 diagnostics, M2 chat, 004 Group, new History profile |
| `application/SecureChatCoordinator.java` | one verified-session adapter owner | human Accept/SAS, pin/session/connection lifecycle |
| `listener/LiveChatAdapter.java` | one Live adapter/UI owner | P2P/Group mock isolation, no READ replay |
| `ui/frames/MainChatFrame.java` | one FE owner; changes only after framework protocol stable | Swing EDT, Unicode, no duplicate bubble |
| 004 `domain/group/` and its wire/metadata | 004 owner controls source authority; 005 uses typed Group port | signed original B/owner handoff/JOIN/REMOVE tests |

**Domain-only scope:** new `vn.intrachat.domain.history` and `src/test/java/vn/intrachat/domain/history` can be edited without touching shared M2/004 files and without SQLite/native driver. No concurrent worker assigned; one worktree is safe.

**T009 Done:** explicit ownership and merge/regression strategy documented, original M2 worktree untouched.
