# Research & Architectural Decisions — 001 Project Foundation

**State:** Implemented foundation stack is documented below; deferred phases remain proposals.
**Reviewed:** 2026-11-21
**Refs:** [spec](spec.md), [plan](plan.md), [constitution](../../.specify/memory/constitution.md)

## Confirmed product clarifications

| CL | Fixed product requirement | Consequence |
| --- | --- | --- |
| CL-01 | Direct LAN P2P; no central messaging server required | Every peer can initiate/accept connections; routing details deferred to 002 |
| CL-02 | Windows desktop first | Validate Windows 11 x64; other operating systems optional |
| CL-03 | Video/Sound = file sharing and playback | No realtime audio/video calls; file streaming first in 006 |
| CL-04 | History synchronized across allowed peers | Each authorized device keeps local copy; eventual consistency and permissions required, code deferred to 005 |

## ADR-lite

| ADR | Decision | Considered | Rationale | Scope |
| --- | --- | --- | --- | --- |
| D-01 | Java 21 bytecode, Temurin 21 recommended | Java 17, Java 25 | Java 21 LTS baseline and wide tooling compatibility; current machine has JDK 25 which can compile `--release 21` but is **not** a JDK 21 runtime test | M0 |
| D-02 | Swing | JavaFX | Built into JDK, zero additional UI dependencies for simple shell; trade-off modern controls and media playback | M0 |
| D-03 | Maven 3.9.16 wrapper | Gradle, global Maven 3.9.14 | Reproducible wrapper version, normal Java conventions; first bootstrap may download Maven | M0 |
| D-04 | JUnit Jupiter 5.14.4, Surefire 3.5.6 | JUnit 6, TestNG | Modern JUnit 5 compatible with Java 21 source target and established Maven lifecycle | M0 |
| D-05 | P2P TCP, JSON length framing (draft) | UDP message stream, Java native serialization, binary protobuf | Stream ordering and diagnosable envelope; protocol overhead higher, 1 MiB frame bound proposed; no socket at M0 | 002 |
| D-06 | Manual IP/port baseline then optional discovery | Broadcast-only discovery | Avoid hard dependency on blocked multicast/broadcast/Firewall | 002 |
| D-07 | Local SQLite per peer (proposed) | Central DB, flat file | Offline use, unique event keys/indexes, durable sync checkpoints; requires migrations, retention & permissions | 005 |
| D-08 | P2P event delta sync (proposed) | Central sync service, file copies | Meets P2P + multi-device history, handles offline convergence; cost: identity, auth, ACL, conflict semantics | 005 |
| D-09 | Media through bounded file transfer (proposed) | WebRTC/RTP live streaming | Matches explicit file/send/play scope; codec fallback TBD | 006–007 |

## Versions and local evidence

- Maven **3.9.16** is a published Apache release ([Apache Maven download](https://maven.apache.org/download.cgi)).
- Maven wrapper **3.3.4** generated with official Maven Wrapper plugin, `only-script` type ([plugin docs](https://maven.apache.org/tools/wrapper/)).
- JUnit Jupiter **5.14.4** is published to Maven Central ([artifact](https://central.sonatype.com/artifact/org.junit.jupiter/junit-jupiter/5.14.4)).
- Windows 11 x64 environment found with **Oracle JDK 25.0.4** and pre-existing system Maven **3.9.14**. Java 21 is **not installed**; `--release 21` compiles Java 21-compatible classfile/API, but Windows/JDK21 runtime verification remains open.
- All dependency versions are pinned in `pom.xml`; `java.util.logging` comes with the JDK, no network, media or database runtime library yet.

## Deferred questions / blocking gates

1. **002 Networking:** manual pairing and trusted peer identity, TCP port, NAT/Wi-Fi isolation/firewall, framing compatibility, timeout and bounded buffers.
2. **004 Groups:** membership authority for decentralized groups, late join, rekey/revocation and group fanout.
3. **005 History:** exact device-vs-user identity, authentication + conversation ACL, SQLite migration, retention/deletion and tombstone propagation, event ordering/cursors, idempotent reconciliation, media link permissions.
4. **006/007:** file overwrite rules, path safety, size quotas, checksums, codec formats and media-opening fallback.

**Security caveat:** A UUID/host/IP/name is **not authentication**. History sync cannot be activated safely without per-conversation authorization and verified peer identity.

**No external network runtime dependency:** first Maven Wrapper/dependency downloads **can** require Internet; running the built application or cached unit tests does not.
