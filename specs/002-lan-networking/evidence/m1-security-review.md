# M1 Source Security / Trust Review (Partial)

**Review date:** 2026-11-21. **Status:** Source-level and unit-test review, NOT a security certification or penetration test.

## Scope reviewed

- `JsonEnvelopeCodec`: allowlisted M1 types only (HELLO*, DIAGNOSTIC_PING/PONG, PROTOCOL_ERROR), rejects CHAT_TEXT/GROUP/SYNC/FILE variants. Strict UTF-8 decoding, strict duplicate JSON-key detection, root/sender/destination and type-specific payload field validation; max JSON nesting depth 32; byte caps.
- `FrameCodec`: 4-byte big-endian unsigned length validated before `readNBytes` allocation; preapproval max 16 KiB; after approval max 1 MiB. Empty/overflow/truncated frames fail, no Java native object deserialization.
- `NetworkCoordinator`: peer UUID and displayName are **untrusted claims**. No `CONNECTED` until receiver human approval and HELLO_CONFIRM/READY handshake; Reject/no answer fails; no automatic reconnect. Receiver's actual observed remote SocketAddress is shown in popup.
- `InboundRequestDialog` and `NetworkPanel`: prominent **CHƯA XÁC THỰC & CHƯA MÃ HÓA** warning. No UI action grants persistent peer trust, History/File/Group ACL.
- `TcpPeerSession`: synthetic diagnostic Ping/Pong whitelist, checks remote declared sender UUID and intended destination (NOT authentication), caps queued frames/bytes, closes socket and heartbeat on disconnect.
- `LocalPeerProfile`: stores only nonsecret local UUID and display name under user-local config; no chat logs/SQLite/secret content.
- `pom.xml`: pins Jackson databind 2.22.4; Maven Shade bundles codec runtime. Device transport is plain TCP.
- Automated `M1TypeWhitelistTest`, `NetworkFaultTest`, `SecurityBoundaryTest` verified M1-only boundary locally.

## Threats not mitigated in M1

**High risk:** LAN peer identity can be forged; TCP traffic **can be intercepted/modified** by on-path attackers. Human Accept is just an interaction decision and does not authenticate. Device ID operational dedup is spoofable. No confidentiality/integrity guarantee. Do not use for personal messages, History or File. M1 is a technical diagnostic milestone, **not a secure chat release**.

**DoS/robustness still incomplete:** listener candidate flood rate-limit not independently stressed, active sessions up to four, pending approvals four, heartbeat/queued-byte caps need extended tests; potential UI prompt pressure. No SSL/TLS. Logged errors should be further sanitized during security testing. Duplicate cross-connect deterministic policy not exhaustively proven.

**Input validation caveat:** Code rejects parse/unknown types but dedicated grammar fuzzing and adversarial IPv4 LAN load tests are not completed. Retest dependency CVEs, Jackson parser configs and Java21 runtime before production decisions.

## Conclusion

**PARTIAL:** source boundary matches approved CL-02-03 for *synthetic diagnostics only*. No general security or personal data assurance; pending stress tests and real 2-PC LAN verification remain blockers. History 005 requires separate verified device identity, authorization and group membership/ACL before ANY event summary/content is exposed.
