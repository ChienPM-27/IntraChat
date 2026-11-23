# C4 — Báo cáo loại bỏ Demo/Login/Mock (23/11/2026)

## A. Trạng thái

**C4 IMPLEMENTED / BACKEND VERIFIED / CLEANUP COMPLETE — GUI ACCEPTANCE PENDING.** Không kết luận GUI VERIFIED hoặc P6 COMPLETE.

- Repository: `D:\Documents\Lap_trinh_ung_dung_mang\Tuan08\IntraChat`
- Branch: `main`; HEAD: `f9b415af9dad8e312cac4a9d7c099411f7d50390`.
- Backup C4: `D:\Documents\Lap_trinh_ung_dung_mang\Tuan08\IntraChat-C4-backup-20261123`. Snapshot 107 file (không `.git`, `target`), chứa source/tests/scripts/docs/resources, modified/untracked, Git status, branch/HEAD, staged/unstaged diff, manifest SHA-256. **0 mismatch**, restore mẫu **3/3 PASS**.
- Bảo toàn thay đổi C0–C3 chưa commit; không commit/reset/stash/clean/rebase, không xóa dữ liệu LocalAppData.

## B. Startup

Trước C4: `ui.IntraChat → MockChatService → LoginFrame (Demo Login/Register) → nút Live → LocalProfile → LiveChatService → LanConnectionDialog → MainChatFrame`.

Sau C4:

```text
ui.IntraChat (entrypoint duy nhất; --diagnostics headless)
  → worker: LocalProfile.loadOrCreate → LiveChatService
  → Swing EDT: LanConnectionDialog (Listen / Connect / Accept / Reject)
  → TCP connected: LiveChatAdapter → MainChatFrame
  → P2P / Group / File / Image / Video / Sound / History
```

Dialog kết nối mở **trước Messenger** (phương án được C4 cho phép), không đòi account hay password. Messenger chỉ được tạo một lần từ dialog; nút Kết nối LAN mở lại dialog đã có. Đóng Messenger gọi `LiveChatAdapter.close → LiveChatService.close`, `LanCoordinator.close` và đóng dialog. Đóng dialog trước khi kết nối cũng dừng worker và service. Không tạo hồ sơ hay trạng thái ACK/READ giả. History và UUID dựa LocalProfile thật.

## C. Source thay đổi

| File | Hành động | Lý do |
|---|---|---|
| `src/main/java/ui/IntraChat.java` | MODIFY | Live-only entrypoint, khởi tạo off EDT |
| `src/main/java/ui/frames/LoginFrame.java` | DELETE | Xóa login/register Demo |
| `src/main/java/model/mock/MockChatService.java` | DELETE | Chỉ dùng Demo |
| `src/main/java/model/mock/DemoMedia.java` | DELETE | Chỉ được Mock gọi |
| `src/main/resources/demo/GhiChuThoai.wav` | DELETE | Chỉ dùng trong DemoMedia |
| `src/main/resources/demo/XemTruoc.mp4` | DELETE | Chỉ dùng trong DemoMedia |
| `src/main/java/ui/dialogs/LanConnectionDialog.java` | MODIFY | Bỏ phụ thuộc LoginFrame, giữ Accept/Reject |
| `src/main/java/ui/components/ChatIcon.java` | MODIFY | Chuyển icon khỏi LoginFrame |
| `src/main/java/ui/frames/MainChatFrame.java` | MODIFY | Bỏ điều kiện Demo, giữ foreground READ |
| `src/main/java/ui/components/ComposerPanel.java` | MODIFY | Một input Live, giữ emoji/file/image/video/sound và VoiceRecorder |
| `src/main/java/listener/ChatActionHandler.java` | MODIFY | Bỏ signIn/register/liveMode giả |
| `src/main/java/listener/LiveChatAdapter.java` | MODIFY | Bỏ Demo login methods |
| `src/test/java/ui/SingleFrontendEntrypointTest.java` | MODIFY | Kiểm tra không còn class Demo; diagnostics |
| `src/test/java/ui/LiveComposerTest.java` | MODIFY | Bỏ setTextOnly vì không còn Demo composer |
| `src/test/java/ui/frames/LiveReceiptVisibilityTest.java` | MODIFY | Bỏ nhánh kiểm thử receipt Demo; giữ retry UNCERTAIN Live |

**Số thay đổi C4 source/test/resources:** 11 file sửa, 5 file xóa (3 Java class và 2 binary demo); không thêm class Java mới. Các backend `LanCoordinator/LanSession`, `GroupService`, `FileTransferService`, `HistoryRepository` không thay đổi. Giữ `VoiceRecorder`, `WindowsForegroundGuard`, `ConversationRamBudget` và các tính năng F01–F11.

## D. Kiểm thử

| Hạng mục | Kết quả |
|---|---|
| JDK 21 `mvnw.cmd clean test package` | **PASS / BUILD SUCCESS** |
| JUnit | **96 tests / 30 suites / 0 failures / 0 errors** |
| P1 TCP/Unicode/ACK/READ | **PASS** |
| P2 Group Owner Relay | **PASS** |
| P3 File | **PASS** |
| P4 History/Restart | **PASS** |
| P5 Image/Video/Sound transfer | **PASS** |
| `scripts/verify-all.ps1` | **exit 0** |
| JAR `--diagnostics` | **exit 0**, không mở GUI |
| JAR manifest | **Main-Class: ui.IntraChat** |
| JAR classes/assets | **PASS**: Live classes present; LoginFrame/MockChatService/DemoMedia/demo binaries absent |
| GUI startup (JDK 21, temp dataDir) | **PASS giới hạn**: màn hình kết nối LAN mở, không Demo Login |
| GUI đóng dialog trước kết nối | **PASS**: cửa sổ đóng và javaw.exe thoát |
| GUI hai JVM trên localhost | **PARTIAL**: mở hai dialog Live, A Listen, B Connect, A xuất hiện hộp Accept/Reject; chưa chấp nhận để mở Messenger nên không kết luận P2P GUI PASS |
| GUI P2P Messenger hai JVM / Group ba JVM | **NOT VERIFIED / NOT RUN** |
| GUI media/history/foreground READ | **NOT RUN** |
| Physical LAN hai/ba máy | **NOT RUN** |
| MP4/WAV trên thiết bị thực | **NOT RUN** |
| `git diff --check` | **PASS** |

Log: `%TEMP%\intrachat-c4-maven.log`, `%TEMP%\intrachat-c4-verify-all.log`, `%TEMP%\intrachat-p6-verify-5a0d0eb3cf5f4f399d1d793e5ec68481`. GUI startup dùng `-Dintrachat.dataDir=C:\Users\XuanHoai\AppData\Local\Temp\IntraChat-C4-GUI-A`, không dùng profile mặc định.

## E. Phần chưa nghiệm thu và chuyển C5/C6

- C4: Đã khởi chạy hai JVM riêng với dataDir tạm, thao tác A Listen/B Connect và quan sát dialog Accept/Reject phía A; yêu cầu hết hạn trước khi chọn Accept, nên chưa xác nhận Messenger hai JVM P2P. Group ba JVM, media controls, offline history và đóng Messenger sau kết nối chưa kiểm tra. Đã xác nhận đóng các dialog và JVM thoát.
- C5: audit dead Java code, tests/scripts/resources ngoài phạm vi Demo/Login; tránh xóa component có caller hợp lệ.
- C6: extended VIDEO cancel/disconnect/concurrency/duplicate offer; lỗi disk/full/permissions, GUI full-flow, LAN vật lý và playback MP4/WAV.
- TCP/JSONL không mã hóa, chỉ phù hợp mạng LAN tin cậy và dữ liệu thử.

**Kết luận: C4 cleanup/backend hoàn tất với GUI acceptance pending; P6 và MVP vẫn partial acceptance.**
