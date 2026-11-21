# US3 — Synthetic diagnostic PING/PONG exchange

**Executed:** 2026-11-21, same physical Windows 11 PC, Eclipse Temurin 21.

## Automated

- `DiagnosticExchangeTest`: two coordinators with different device UUIDs, *explicit test-controlled* acceptance provider, v1 HELLO→READY followed by PING/PONG in **both directions** and disconnect.
- `JsonEnvelopeCodecTest`: strict UTF-8 round-trip with Vietnamese/emoji, malformed UTF-8, duplicate JSON keys, trailing JSON, unknown/future CHAT_TEXT types, bootstrap sentinel restrictions.
- `FrameCodecTest`: one-byte-chunk reads, two frames in one TCP stream, max-bound and overflow/EOF handling.
- `NetworkFaultTest`: direct malicious loopback sockets test >16KiB frame before approval and PING before HELLO; receiver closes without prompting user.
- `NetworkCoordinatorTest`: explicit manual retry after Reject, no background reconnect.

## Windows UI two-process smoke

- After A clicked Accept, **B** clicked **Ping kiểm tra**: status `PONG chẩn đoán ... (6 ms)` in B window.
- **A** then clicked **Ping kiểm tra**: status `PONG chẩn đoán ... (7 ms)` in A window.
- Both statuses are engineering synthetic probes with unverified remote identity. **No user chat content, History, File or Group** was transmitted.

**PASS:** Two-way Ping/Pong on local Windows loopback, Java 21 runtime, real UI clicks; synthetic-only. **NOT RUN:** two separate LAN PCs, Internet-off across machines, throughput/latency benchmarks beyond these two spot samples.
