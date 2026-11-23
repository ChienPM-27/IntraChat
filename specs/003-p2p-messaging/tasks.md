# Spec 003 — Task tracker P2P MVP

**Thay cho checklist triển khai 90 task cũ:** tập trung vào chức năng P2P đơn giản của đồ án. Tracker cũ **65/90** là *lịch sử tại 22/11/2026* và vẫn tra cứu được trong Git/evidence, không được chuyển task cũ thành PASS giả.

**Quy tắc:** [ ] nghĩa là **chưa xác nhận đủ tiêu chí của bản MVP hiện hành** (không khẳng định thiếu toàn bộ code). Chỉ check sau khi chạy thật và ghi bằng chứng. Có thể thực hiện T01–T04 trên một PC với hai JVM, rồi chạy T09 trên hai PC khi có thiết bị.

## A. Kết nối P2P và nhắn tin

- [ ] **M2-MVP-01** — Hai JVM Live khởi chạy độc lập, một Listen và một Connect bằng IPv4:port, Accept (và xác nhận hiện có) thành công; UI thể hiện đúng kết nối. **PASS:** P2P-01.
- [ ] **M2-MVP-02** — A gửi B, B trả lời A bằng text thật, đúng nội dung/hội thoại, không mock hay trùng bubble. **PASS:** P2P-02.
- [ ] **M2-MVP-03** — Unicode/emoji/tiếng Việt/xuống dòng, Sender và Time đúng. **PASS:** P2P-03.

## B. Trạng thái và lỗi

- [ ] **M2-MVP-04** — State phản ánh ACK/READ thật, không có delivered/read giả, hiển thị trạng thái chưa rõ hoặc thất bại khi cần. **PASS:** P2P-04.
- [ ] **M2-MVP-05** — Reject/sai IP:port/ngắt mạng được báo lỗi, không treo Swing EDT, không hiện gửi thành công giả. **PASS:** P2P-05.
- [ ] **M2-MVP-06** — Disconnect/reconnect thủ công, gửi tin mới thành công, không tự phát lại tin cũ. **PASS:** P2P-06.
- [ ] **M2-MVP-07** — Demo vẫn hoạt động riêng; không có dữ liệu mock hoặc nút tính năng Live chưa được nối backend mà báo gửi thành công. **PASS:** P2P-07.

## C. Kiểm thử, tài liệu và bàn giao

- [ ] **M2-MVP-08** — Chạy Java 21 Maven offline tests và smoke GUI hai JVM; ghi đầy đủ PASS/FAIL/NOT RUN trong `evidence/m2-mvp-acceptance.md`.
- [ ] **M2-MVP-09** — Thử hai máy Windows LAN thật, A/B gửi nhận hai chiều, reconnect và lỗi cơ bản; ghi máy/IP/phiên bản/kết quả. **Nếu chưa có máy:** để unchecked, ghi NOT RUN; không chặn code Spec 004.
- [ ] **M2-MVP-10** — Cập nhật README/feature-matrix và bàn giao hướng dẫn demo; chỉ đánh dấu MVP hoàn thành khi M2-MVP-01…08 thực sự PASS. Hai-PC LAN phải công bố trạng thái riêng.

**Không làm trong MVP:** chứng nhận TLS, pentest, CVE/SBOM, independent security review, multi-user DPAPI, Constitution Gate 0/1, M1 Gate 1, stress sản xuất. Không xóa code TLS/SAS hiện hành hoặc nhận chat trên v1 diagnostic.
