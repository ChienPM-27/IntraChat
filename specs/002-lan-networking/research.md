# Research & ADR Verification — 002 LAN Networking

**Status:** Design research; toolchain/runtime proof and security dependency checks still pending.
**Date:** 2026-11-21
**Authority:** [Spec](spec.md), [Plan](plan.md), [Constitution](../../.specify/memory/constitution.md).

## User-confirmed decisions (not subject to technical reinterpretation)

| ID | Accepted by user | Impact |
| --- | --- | --- |
| CL-02-01 | Manual IP/port only | No mDNS/broadcast/scan/UDP discovery. |
| CL-02-02 | Inbound human Accept/Reject | TCP accepted by OS is only a pending candidate; no diagnostic data or `CONNECTED` before user consent plus completed handshake. |
| CL-02-03 | Synthetic diagnostics only, no authentication/encryption; warn | Do not claim privacy, trust, pairing, secure traffic, or History ACL. Not suitable for sensitive payloads. |
| CL-02-04 | User reconnects manually | No background retry or auto-reconnect after peer drop. |

## Decision log: applied versus deferred

| Plan ADR | Selected for M1 | Trade-offs / requirement |
| --- | --- | --- |
| 01 TCP | Ordered duplex byte stream, Java sockets | Needs explicit framing and cancellation/timeout to avoid indefinite block; no Internet relay. |
| 02 Manual IPv4 literal | User enters peer IP + port | Less convenient; does not work via hostnames/IPv6 yet; explicit network assumptions. |
| 03 Listener OFF until user starts | Bind selected local IPv4 only | Avoid accidentally exposing service on all adapters. |
| 04 Every incoming connection prompts | Async approve/reject, pending cap/deadline | No persisted whitelist/autoapproval; spam possible but bounded. |
| 05 No auth/TLS, diagnostics only | Untrusted identity and wire, conspicuous warning | Can be spoofed/sniffed/tampered; must never carry private chat/history/files. |
| 06 Manual retry only | Explicit Reconnect command | Recovery requires user action, avoids hidden loops. |
| 07 TCP 4-byte big-endian length + JSON UTF-8 | Strict frame parser, size caps | Easily inspected but larger than binary; malformed lengths must be rejected before allocation. |
| 08 Jackson databind pinned | Proposed `2.22.4`, **validated on actual Temurin21 with Maven Wrapper 3.9.16; package resolution exit 0; parser behavior and complete dependency audit still require tests** | Easy DTO validation, but dependency and strict-parser settings need Maven+JDK21 proof and CVE review in T003. |
| 09 Java 21 virtual threads + explicit limits | Off-EDT blocking I/O code | Virtual threads are *not* capacity control; semaphore+bounded queues always required. |
| 10 Operational duplicate ID detection | 1 active session per declared ID, deterministic tie break | UUID can be spoofed, not authentication; reject/close duplicate without losing state. |
| 11 No firewall manipulation | Document Private-profile optional rule | Needs Windows user/IT action; no hidden privilege request. |
| 12 PING/PONG liveness | 10s probe/30s deadline | Heartbeat can produce false positives on suspended PC; manual retry after disconnect. |
| 13 Synthetic diagnostic whitelist | HELLO*/PING/PONG/ERROR only | No chat editor or History APIs; future versions extend protocol explicitly. |
| 14 Java 21 actual runtime | M0 Gate 1 prerequisite | Windows machine initially had Oracle JDK25; `--release 21` is not a JDK21 runtime test. |

## Java compatibility and build

- Existing `pom.xml` specifies Java release 21, Maven Wrapper 3.9.16 and JUnit Jupiter 5.14.4.
- Existing M0 evidence reports 9/9 tests passed with Oracle Java 25.0.4. It does **not** verify Java 21 runtime compatibility.
- Temurin 21 x64 zip is available from Eclipse Adoptium release API; status and checksum must be checked and version printed **on the target machine** prior to closing M0. Portable toolchains must stay outside Git working tree.
- Do not silently edit `JAVA_HOME` globally; use command-scoped `JAVA_HOME` when exercising target runtime.
- Jackson Jackson 2.22.4 was selected as a newer patched release than the original plan 2.21.5. Maven Wrapper 3.9.16 resolved 2.22.4 transitive dependencies on Temurin JDK21 with exit 0; strict parser behavior and vulnerability review remain test/review obligations.
- Syntactic schema validation, duplicate JSON keys, trailing tokens, maximum nesting, invalid UTF-8 must be explicitly tested even when using a mainstream JSON library. Do not interpret a parsed JSON field as proof of remote identity.

## Protocol decisions to review during implementation

- HELLO destination is unknown before the remote device ID is learned. The reserved NIL UUID sentinel exists **only** for bootstrap HELLO; never use it for post-handshake message routing.
- Approval request must show actual remote socket endpoint, not a peer-supplied arbitrary IP label.
- Receiver sends HELLO_ACCEPT **only after explicit human Accept**; initiator confirms, receiver replies HELLO_READY, and only then is diagnostic exchange enabled. Receiver may show local connected after sending READY; close/error must reconcile races.
- One peer can listen and initiate; no one becomes a central server. For two independent processes on loopback select different ephemeral ports; self-identical declared UUID is a configuration/conflict signal, not reliable spoof detection.

## Threat model boundary

| Threat | Required mitigation in M1 | Still unprotected |
| --- | --- | --- |
| Unsolicited connection attempts | Human approval for every inbound; prompt deadline and max 4 pending | Malicious peer can spam at bounded rate; approval not authentication |
| Unsafe length/JSON | Preapproval cap 16KiB, control max 1MiB, strict UTF-8 and schema, reject unknown type/version | No cryptographic integrity |
| IP or UUID spoofing | Actual socket endpoint shown; ID marked self-declared; no private data | Attacker can claim another ID; peer identity unverified |
| Network eavesdropping/tampering | Explicit plaintext warning, synthetic-only payloads | All network bytes visible/modifiable to on-path attacker |
| Resource exhaustion | 4 active/4 pending, 16 frames & 2MiB queue, timeout, bounded errors | DoS still possible at LAN/network layer |
| Accidental feature creep | Diagnostic type whitelist, no storage/file/chat routes | Future phases require separate approved authentication/ACL design |

## Further proof required

1. T003: artifact 2.22.4 resolved successfully under Temurin21; verify strict Jackson parser flags with automated tests and review dependency vulnerabilities before each release.
2. T001: complete or formally resolve M0 offline GUI acceptance, JDK21 runtime, and Gate 1/hand-off; documentation here alone does not resolve gate.
3. T008: review this research against protocol contract, model, Constitution Gate 0 before code.
4. T050–T052: two **real** Windows LAN peers with Internet disconnected but LAN reachable. Loopback-only evidence cannot close M1.
