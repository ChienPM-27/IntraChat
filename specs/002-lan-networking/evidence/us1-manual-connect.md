# US1 — Manual IPv4:port transport & listener

**Executed:** 2026-11-21
**Platform:** Windows 11 x64 build 26100, Eclipse Temurin Java 21.0.13.1+2, Maven Wrapper 3.9.16.

## Automated

- `TcpListenerTest` — start listener on 127.0.0.1/test port, accept candidate socket, close, bind-conflict error.
- `TcpConnectorTest` — manual 127.0.0.1:port connect; refused port produces `IOException`.
- `NetworkConfigTest` — IPv4 literal/port validation, rejects hostname, invalid addresses, multicast/broadcast/unspecified IP.
- These test cases are included in JDK21 `.\mvnw.cmd -q clean package` (see `m1-test-results.md` for aggregate result).
- **Known test fix:** An initial clean package hit Windows `BindException: Address already in use` when a test attempted immediate **rebind of the same recently accepted TCP port** after close. The test was corrected to verify listener behavior and normal socket ownership without assuming instantaneous rebind (TIME_WAIT dependent). Subsequent clean package PASS.

## Native GUI / two-process loopback

- Started two independent `javaw.exe -jar target/intrachat-0.1.0-SNAPSHOT.jar` on the same Windows desktop, with separate `-Dintrachat.profile` paths to keep device UUIDs distinct.
- Process A displayed local listen form at 127.0.0.1:47321; clicked `Bắt đầu lắng nghe` and observed status `Đang lắng nghe 127.0.0.1:47321`.
- Process B manually entered destination 127.0.0.1:47321 (default form), clicked `Kết nối`, observed status `Đang chờ người nhận Chấp nhận/Từ chối` until human-operated local consent popup on A.
- No UDP/discovery or external server was used.
- After approval and correct handshake, both windows displayed diagnostic connection. See [US2](us2-approval-handshake.md) and [US3](us3-diagnostics.md).

**PASS:** Manual IPv4/port loopback and UI controls. **NOT RUN:** Two different physical LAN PCs; not proof of AC-02-A/I.
