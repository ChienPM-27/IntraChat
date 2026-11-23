# C6 — Final Verification (24/11/2026)

**Kết luận: C6 PARTIAL — MVP PARTIAL ACCEPTANCE. C5 PARTIAL.** Đây là kết quả chạy thật, không phải checklist đã hoàn thành.

## A. Git protection và backup
- Repository: `D:\Documents\Lap_trinh_ung_dung_mang\Tuan08\IntraChat`.
- Branch: `main`; HEAD: `f9b415af9dad8e312cac4a9d7c099411f7d50390`.
- Trước C6: working tree không sạch do thay đổi C0–C5 (tracked modified/deleted và 5 untracked reports); giữ nguyên, không reset/stash/clean/commit.
- Backup: `D:\Documents\Lap_trinh_ung_dung_mang\Tuan08\IntraChat-C6-backup-20261124-020757`.
- Backup 106 file (không gồm `.git` hoặc `target`), gồm source/tests/scripts/docs/Maven config và 5 untracked reports; Git status/branch/HEAD/staged + unstaged binary patches/untracked list lưu tại `_c6_git_evidence`.
- Manifest SHA-256: **106/106 khớp**, 0 mismatch; restore thử `pom.xml`, `ui/IntraChat.java`, `docs/c5-detailed-audit.md`: **3/3 khớp**.
- Không truy cập dữ liệu LocalAppData người dùng. GUI dùng `%TEMP%\IntraChat-C6-GUI-A` và `...-B`.

## B. Confirmed defect / code fix
**HistoryRepository close-vs-append boundary:** Trước sửa, `close()` gọi `flush()` khi `closed=false`, sau đó mới đặt `closed=true`. Một producer có thể enqueue thêm event sau flush barrier, khiến ranh giới shutdown không được định nghĩa chặt. Sửa `close()` thành `synchronized`, đặt `closed=true` trước `flush()`, cùng monitor với các public mutation methods đã synchronized. Các event được chấp nhận trước ranh giới close sẽ nằm trước barrier; gọi append sau close ném `IllegalStateException`. Không thay JSONL hoặc TCP ACK semantics. Đây là lỗi thứ tự được chứng minh bằng source review; **chưa có test fail-before-fix thu thập được**, không tuyên bố crash/loss đã tái hiện trên bản cũ.

**New test:** `HistoryShutdownC6Test.acceptedEventsSurviveConcurrentCloseAndRestart` — bốn producer, latch khởi động đồng thời, close ở luồng thứ năm, deadline cho futures, tập UUID đã được chấp nhận đối chiếu sau restart. PASS trong Maven. Test này không thay thế kiểm chứng crash, I/O fault hoặc nhiều vòng stress dài.

## C. Concurrency tests
| Nhóm | Kết quả | Bằng chứng / giới hạn |
|---|---|---|
| Normal Accept | PASS (GUI một lượt) | Hai JVM và hai cửa sổ riêng, localhost, người dùng nhấn Accept |
| Accept sát timeout / EDT delay / Expire | NOT RUN | Chưa có deterministic scheduler/EDT-delay test |
| Reject | NOT RUN | Chưa thao tác |
| Recorder start/stop/cancel blocked/read/shutdown | NOT RUN | C5 risk vẫn mở; chưa có fake line/hardware test |
| History concurrent close + restart | PASS | C6 JUnit test mới |
| History concurrent writes không shutdown | PARTIAL | Test producer C6 có overlap với close; chưa stress độc lập |
| History crash / I/O failure | NOT RUN | Không fault injection |
| File cancel/chunk/disconnect/complete races | NOT RUN | Chưa có race harness |
| Duplicate offer/chunk + concurrent text during media | NOT RUN | P5 smoke cũ không chứng minh các race mới |
| Normal GUI shutdown hai instance | PASS (một lượt) | Đóng cả hai cửa sổ, hai PID Java app đã thoát |

## D. Media và extended P6
- P5 media process smoke: **PASS** trong `verify-all.ps1`; không suy diễn thành toàn bộ M01–M14.
- Extended P6 M01–M14: **NOT RUN / chưa triển khai**.
- MP4 playback thực, WAV phát qua loa, microphone thật: **NOT RUN**.
- Media SHA-256 và transfer byte chỉ được ghi nhận ở mức regression P5 cũ, không suy ra playback.

## E. GUI Acceptance (hai JVM thật trên cùng máy)
- Java 21 JAR, pid A `24472`, B `23760`; dataDir tạm độc lập.
- **GUI-P01 PASS:** A Listen 127.0.0.1:47321; B Connect; A thấy modaless Accept/Reject; nhấn Accept; cả hai xuất hiện `IntraChat — Live LAN TCP`, peer online. Không có timeout giả trong lượt này.
- **GUI-P02/P03 PASS:** A gửi `Chào bạn, kiểm thử C6 tiếng Việt 😀`, B nhận; B gửi `Phản hồi từ B: đã nhận tin, C6 OK ✅`, A nhận.
- **GUI-P04 PASS (Unicode/emoji transmission):** Unicode và emoji hiển thị trên hai phía; glyph emoji phụ thuộc font.
- **GUI-P05/P06 PARTIAL:** hai bubble có hướng đúng, tên peer và timestamp `02:12` hiển thị; chưa đối chiếu UUID/timestamp sâu từ history.
- **GUI-P07 PARTIAL:** sender xuất hiện Đã nhận và sau khi receiver foreground chuyển Đã xem; chưa test background guard.
- **GUI-P08/P09 NOT RUN:** chưa test reconnect/reject/expire.
- **GUI Group 3 JVM, file/image/video/sound/history restart: NOT RUN**.
- **Shutdown bình thường PASS:** sau khi gửi close cho hai cửa sổ, không còn Java app PID A/B (Java khác của VS Code vẫn chạy).
- Không có bằng chứng để tuyên bố `C6 GUI VERIFIED` toàn bộ.

## F. Physical LAN
**PHYSICAL LAN — NOT RUN**: chỉ có hai JVM trên localhost, chưa truy cập điều khiển hai máy vật lý độc lập. Không suy diễn từ localhost.

## G. Regression
- Baseline JDK 21 `mvnw.cmd -o test`: **96/96 PASS**.
- Sau sửa JDK 21 `mvnw.cmd -o clean test package`: **BUILD SUCCESS**, **97 JUnit / 0 failures**.
- `scripts/verify-all.ps1 -JavaHome C:\Users\XuanHoai\.jdks\jdk-21.0.12.1+1`: **exit 0**; P1/P2/P3/P4/P5 PASS, `git diff --check` PASS.
- Log: `%TEMP%\intrachat-p6-verify-36588b9d3921493daa9677917b10f205`.
- P2 Group 3 JVM: ba lượt bổ sung **PASS/PASS/PASS** (exit 0 mỗi lượt).
- P6 extended standalone: **NOT RUN** vì chưa triển khai.
- Executable JAR: GUI startup/connection tested PASS; `--diagnostics` không chạy riêng trong C6.
- Shade `META-INF` overlap/module-info warning còn tồn tại, không chặn build.

## H. Feature acceptance matrix
| ID | Chức năng | Implemented | JUnit/backend P1–P5 | GUI thực tế C6 | Physical LAN | Persistence/playback | Kết luận |
|---|---|---|---|---|---|---|---|
| F01 | P2P Chat | YES | PASS | PASS hai chiều | NOT RUN | Chưa restart | PARTIAL |
| F02 | Group Chat | YES | PASS P2 | NOT RUN | NOT RUN | NOT RUN GUI | PARTIAL |
| F03 | File Transfer | YES | PASS P3 | NOT RUN | NOT RUN | Race NOT RUN | PARTIAL |
| F04 | Emoji | YES | PASS | PASS trong P2P GUI | NOT RUN | N/A | PARTIAL |
| F05 | Chat History | YES | PASS P4 + C6 close/restart JUnit | NOT RUN restart GUI | NOT RUN | PARTIAL | PARTIAL |
| F06 | Image | YES | PASS P5 | NOT RUN | NOT RUN | Preview GUI NOT RUN | PARTIAL |
| F07 | Video | YES | PASS P5 | NOT RUN | NOT RUN | Playback NOT RUN | PARTIAL |
| F08 | Sound | YES | PASS P5 | NOT RUN | NOT RUN | Playback/recorder NOT RUN | PARTIAL |
| F09 | State | YES | PASS | PARTIAL ACK/Seen | NOT RUN | Foreground negative test NOT RUN | PARTIAL |
| F10 | Time | YES | PASS | PARTIAL timestamp visible | NOT RUN | Full metadata correlation NOT RUN | PARTIAL |
| F11 | Sender | YES | PASS | PARTIAL peer/bubble shown | NOT RUN | UUID correlation GUI NOT RUN | PARTIAL |

Backend result is not a full acceptance verdict for a feature that requires additional GUI/LAN/playback evidence.

## I. Remaining debt
1. Accept-vs-timeout, EDT-delay and expired dialog race deterministic tests.
2. Recorder blocking TargetDataLine, close and repeat cycles.
3. FileTransferService cancel/chunk/disconnect/completion/duplicate races, extended two-JVM P6 harness M01–M14.
4. History writer I/O fault/crash recovery tests.
5. GUI 3 JVM Group, file/media, history process restart, Reject/Expire/Reconnect.
6. Physical LAN with independent machines, media playback hardware.
7. C5 incomplete per-member call-graph coverage. **C5 PARTIAL**; no speculative deletions.

## J. Conclusion
**C6 PARTIAL — MVP PARTIAL ACCEPTANCE; C5 PARTIAL; P6 PARTIAL.** Có tiến triển kiểm chứng: backup hợp lệ, một lỗi shutdown ordering được harden và test mới PASS, JUnit 97/97, P1–P5 PASS, P2 stability 3/3, GUI P2P hai chiều PASS và shutdown sạch một lượt. Không đủ chứng cứ cho C6 BACKEND VERIFIED toàn bộ, GUI VERIFIED toàn bộ, LAN VERIFIED hoặc MVP FULLY ACCEPTED.
