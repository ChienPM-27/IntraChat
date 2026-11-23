# US5 — Two real Windows LAN computers (acceptance blocker)

**Status:** **NOT RUN / BLOCKED** as of 2026-11-21.

The available tools operated **one** Windows 11 desktop only. Two distinct IntraChat Java processes running over `127.0.0.1` successfully negotiated consent and exchanged synthetic diagnostics. **This is not evidence of two physical Windows PCs** on an actual LAN.

## Acceptance procedure to execute with second PC

1. On Windows PC A and B, verify JDK 21, Maven/JAR version, and actual IPv4 LAN interfaces; do not expose any public Internet service.
2. On A, user starts listener bound to real LAN adapter IP:47321 (or agreed port) and obtains authorized Private-profile firewall configuration. On B, user manually enters A's IPv4:port.
3. Confirm no Internet access is required **while LAN still works**; document test environment without disconnecting unrelated user devices.
4. B clicks Connect; A gets incoming dialog with observed endpoint and **untrusted** UUID/name, and must click **Chấp nhận**. Only after HELLO_READY may both peers show connected.
5. Both sides send synthetic Ping and observe Pong, then reverse listener/connect roles. Run user Reject, no-response timeout, sudden peer close, manual reconnect and port block.
6. Record OS/JDK versions, command exit/status, sanitized endpoint metadata, logs, and normal socket/worker cleanup from **both** PCs.

## No fabricated evidence

- **AC-02-A/I:** BLOCKED / NOT RUN.
- `SC-001` and real LAN elements of `SC-009`: NOT VERIFIED.
- Do not mark tasks T050–T053 or M1 Gate 1 complete without physical two-PC test.
- Device pairing/authentication and History/File remain out of scope.
