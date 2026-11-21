# US2 — Inbound consent & v1 handshake evidence

**Executed:** 2026-11-21 on Windows 11 x64, Temurin JDK 21.0.13.1+2.

## Automated tests

- `HandshakeContractTest`: HELLO requires destination sentinel, supported v1 and valid request UUID. Bad/unsupported version and wrong type rejected.
- `HandshakeTest`: live TCP loopback with two coordinators (test-controlled explicit approval). Until the pending promise resolves, caller future is **not done**. After explicit ACCEPT and HELLO_ACCEPT→CONFIRM→READY, the session is connected and can exchange PING/PONG. Explicit REJECT produces failed future, zero active sessions.
- `InboundApprovalPortTest`: injected decision provider in tests returns explicit ACCEPT or REJECT; **not** a production autoaccept adapter.
- `SessionRegistryTest`: bounded pending count and operational duplicate self-declared UUID restrictions.

## Real Swing user actions, two independent Java processes (same PC)

| Check | Actual UI evidence |
| --- | --- |
| A listener | `Đang lắng nghe 127.0.0.1:47321` |
| B outgoing | `Đang chờ người nhận Chấp nhận/Từ chối (tối đa 30 giây)`, Connect disabled |
| A incoming | Real `Yêu cầu kết nối LAN đến` popup with actual observed peer TCP endpoint, **self-declared** remote device UUID/name |
| Security disclosure | Popup prominently: `CHƯA XÁC THỰC & CHƯA MÃ HÓA`, diagnostics only; Accept does not grant History/File |
| Expired/no decision | First UI attempt remained unanswered long enough to expire; B reported `Người nhận đã từ chối kết nối` rather than CONNECTED |
| Manual retry | User clicked `Kết nối lại` on B. A received **new** approval prompt; nothing was retried automatically |
| Human consent | User explicitly clicked `Chấp nhận` in A's popup (not scripted in UI) |
| Negotiation | Both sides subsequently enabled Ping and displayed a diagnostic connected status with `chưa xác thực/chưa mã hóa` |
| Shutdown | Windows were requested to close normally; no forced kill during the successful test |

**PASS:** Local UI approval and versioned handshake for two independent Windows processes, actual person/button action. **NOT RUN:** a *second physical Windows LAN computer*, invalid version via real GUI, hardening against forged IDs.

**Non-security claim:** A name, IP address, UUID and the human action Accept do **not** authenticate the device. No TLS, no History ACL, no secure channel.
