# Implementation Plan: IntraChat — 002 LAN Networking (M1)

**Feature:** `002-lan-networking`
**Date:** 2026-11-21
**Status:** Planned / Design complete at Gate 0 — **no LAN networking implementation or tests yet**
**Spec:** [spec.md](spec.md) · **Checklist:** [checklists/requirements.md](checklists/requirements.md)
**Constitution:** [v1.0.0](../../.specify/memory/constitution.md)
**Base release:** M0 Java/Swing foundation; **not Verified** — 38/41 tasks, T025/T040/T041 remain open
**Git branch:** Current repository remains `main`; `002-lan-networking` is a proposed work branch, not created by this plan.

## Summary

Build the **M1 LAN transport foundation** for two or more Windows IntraChat peers with **direct TCP P2P**, **manual IPv4 address + port**, and no central server or Internet dependency. Each desktop can start/stop a local listener and initiate an outbound connection. The receiver **must manually accept/reject** every incoming request before a diagnostic session becomes active. Session handshake negotiates version and identifies the self-declared peer ID; it **does not authenticate or encrypt**. Only synthetic **diagnostic request/response** messages flow in M1, never user chat, Group, History, File or media data. On disconnect the application **does not reconnect automatically**; the user explicitly presses **Kết nối lại**. All I/O is off Swing EDT and resource bounded.

### User-confirmed clarifications — binding

| Clarification | Approved behavior | Engineering consequence |
| --- | --- | --- |
| **CL-02-01** | **Only manual IP/port; no discovery** | No UDP/mDNS/peer scan/background discovery; network config UI exposes IPv4 and port |
| **CL-02-02** | **Incoming request requires user approval** | Pending-approval state, Accept/Reject dialog, deadline and bounded pending sockets; no diagnostics or `CONNECTED` before approval and handshake |
| **CL-02-03** | **Diagnostics only, unauthenticated/unencrypted; warn clearly** | No pairing, TLS or auth claim in M1; no confidential payload; receiver approval is **not** device verification or History ACL |
| **CL-02-04** | **User reconnects manually** | `Kết nối lại` button; no automatic retry loop/reconnect scheduler; guard duplicate clicks and sessions |

**Carry-over:** CL-01 direct P2P without central server; CL-02 Windows target; CL-03 Video/Sound file sending/playing, not calls; CL-04 History later syncs **only authorized peers** over P2P (not in M1).

**Delivery boundary:** The code, dependencies, contracts, benchmarks and tests below are **proposed tasks**, not results. Making this `plan.md` does not close open M0 acceptance criteria and does not prove M1 can communicate over LAN.

## Technical Context

| Topic | Planned decision / tested baseline | Rationale & status |
| --- | --- | --- |
| Programming language | Java **21 LTS** source/API target (`maven.compiler.release=21`) | Existing `pom.xml`; machine currently has **Oracle JDK 25.0.4**, not Temurin 21 runtime — M0 gate remains open |
| Target system | Windows 11 x64 GUI, TCP over LAN, PowerShell troubleshooting | Product decision. Windows 10 is not claimed supported absent separate tests |
| Build & structure | Single-module Maven with `mvnw.cmd` **3.9.16**, existing `pom.xml` | Reuse working foundation; do not introduce unnecessary modules |
| UI | Swing, all UI operations on EDT, network commands asynchronous | Existing `MainWindow` and `IntraChatApplication`; no socket calls from listeners |
| Tests | JUnit Jupiter **5.14.4**, Surefire **3.5.6** | Existing baseline: **9 JUnit tests** passed on Windows 11/JDK25. M1 tests **NOT RUN** |
| Runtime JSON | **Jackson databind 2.21.5** (proposed pinned version, dependency compatibility must be confirmed at implementation) | Avoid writing a brittle custom JSON parser; adds one runtime library family; keep POJO/wire DTO validation explicit |
| Logging | `java.util.logging`, structured event codes, sanitized metadata | Existing JDK mechanism; never log HELLO raw data/private contents/secrets |
| Transport | Direct TCP sockets, one peer can listen and initiate | Framed, ordered byte stream; no Internet relay or UDP discovery |
| Addressing | **IPv4 literal entered manually**; bind selected local IPv4 interface + configurable port | Simplest testable Windows M1; IPv6/hostnames/discovery intentionally deferred |
| Listener default | **OFF** until user clicks `Bắt đầu lắng nghe` | Expose local address/port, avoid silently opening inbound socket |
| Port | **47321/TCP** suggested default; user can change 1–65535; tests can bind ephemeral 0 internally | Stable instructional port, no requirement for admin privileges; fallback on port conflict |
| Protocol | Frame: 4-byte unsigned big-endian length + **strict UTF-8 JSON**; version 1 | Aligns M0 draft; no native Java deserialization |
| Size bounds | **1 MiB** post-approval JSON control frame; **16 KiB** preapproval HELLO/handshake; **4 KiB** synthetic diagnostics payload | Limit worst-case memory use before/after approval |
| Session budget | Initial: **4** active transport sessions; **4** pending inbound approvals; max **16 queued outbound frames AND 2 MiB** queued bytes per session | Conservative student-project resource budget; validate under tests |
| Operation timeout | TCP connect **5s**, initial HELLO receive **5s**, approval **30s**, total negotiated handshake bounded; frame read **15s** when in expected active I/O | Design targets, not measured; timers and error codes to be pinned and tested in tasks |
| Liveness | Diagnostic `PING` every **10s**, require `PONG` within **30s** or close stale session | Helps identify silent disconnect; does not reconnect |
| Retry | **No auto reconnect**; user manually initiates fresh attempt, guarded against duplicates | CL-02-04 |
| Concurrency | Java 21 virtual threads for bounded socket tasks + scheduler; `Semaphore` on accepted sessions, explicit task cancellation/close | Blocking socket API remains readable and off EDT; virtual threads alone do **not** bound queues, so quotas mandatory |
| Security | **No authentication, no TLS in M1**; manual approval, warnings, diagnostic-only payloads; all inbound untrusted | Explicit product choice; private/trusted network testing only; no privacy/security claim |
| History | **No DB or history sync** in M1; identity/authorization hooks must remain extensible | CL-04 scheduled for 005 after verified peer identity + per-conversation ACL |
| Performance budgets | GUI interaction event dispatch target p95 ≤200 ms during connect/timeout; normal disconnect/close target ≤2 s after cancel signal; connect fails within configured timeout | Proposed measurable targets, not achieved/tested until evidence |
| Network scope | Local IPv4, Windows Private network with explicitly configured firewall rule if authorized | No auto firewall changes or listener on public Internet |

**Dependency caveat:** The chosen Jackson version is a **plan decision**, not a dependency already in `pom.xml`. Tasks must validate Maven Central artifact resolution/licensing, transitive versions, strict parser configuration and regression tests before committing; if unavailable, record ADR amendment rather than silently substituting another library. Maven bootstrap may use Internet; built peer-to-peer app must not.

## Constitution Check — Gate 0 (before code)

| Principle | Design-level assessment | Evidence / Gate 1 requirement |
| --- | --- | --- |
| **I. LAN-First & Explicit Topology** | **PASS — design** | Direct TCP P2P; manual IPv4/port; Windows firewall guidance; no cloud, relay, mDNS or discovery; tests on 2 Windows peers; disconnect handling |
| **II. Versioned Message & Identity Contract** | **PASS — design** | v1 framed JSON, explicit mandatory identity/type/ID/destination/timestamp, HELLO negotiation, strict malformed/unknown rejection; displayName distinct from self-declared deviceId; delivery state separate |
| **III. Non-Blocking, Bounded, Resilient** | **PASS — design** | Listener, pending approval, read/write jobs never run on EDT; frame/queue/session caps; timeout/cancel/cleanup; manual reconnect; heartbeat liveness |
| **IV. Security & Privacy (NON-NEGOTIABLE)** | **PASS WITH BOUNDARY — design** | Inbound bytes untrusted, approval is **not authentication**; no TLS/auth is **disclosed**; no private data sent; no history sharing, unsafe deserialization, secrets/log payloads. Insecure transport never silently upgraded to a secure capability |
| **V. Automated Tests (NON-NEGOTIABLE)** | **PASS — test plan only** | Parser tests; two-process loopback + actual two-machine LAN; valid/invalid/disconnect/cleanup; negative approval, malformed framing and queue caps; **none executed yet** |
| **VI. User-Visible Truth** | **PASS — design** | Connection progress/pending approval/diagnostics and warning visible; `CONNECTED` only after approval + handshake; no `authenticated/synced/read/delivered` claims |
| **VII. Spec-Driven & Honest Delivery** | **PASS — design** | CL-02-01…04 in spec, this plan records ADRs; tasks must follow; no tests/implementation assumed and M0 blockers remain visible |

**Gate 0 conclusion:** No proposed exception to Constitution MUST; all seven checks pass **design review only**. **Gate 1 unverified** and M1 cannot be marked Verified until running tests, code review, real LAN evidence and M0 handoff are complete.

**Why intentionally no TLS/auth?** This is a bounded *diagnostics-only* milestone per explicit CL-02-03, not permission to send private data over unprotected LAN. Users must consent to inbound connection and see a clear “chưa xác thực / chưa mã hóa; chỉ dữ liệu thử nghiệm” warning. A malicious LAN device can impersonate `deviceId` or observe/tamper traffic. No file, group, personal chat or history access is exposed. Actual authentication + authorization must precede History Sync 005, and product release risks must be reassessed before opening real user data.

## Project Structure

### Documents (002 scope)

```text
specs/002-lan-networking/
├── spec.md                       # clarified CL-02-01..04, already exists
├── checklists/requirements.md    # clarify/spec quality checklist, already exists
├── plan.md                       # THIS DOCUMENT
├── research.md                   # proposed ADR/tech spikes in plan workflow
├── data-model.md                 # proposed detailed session/handshake models
├── contracts/
│   └── network-v1.md            # proposed wire-level schemas/error samples
├── quickstart.md                 # proposed Windows/2-peer setup and test instructions
├── checklists/
│   └── constitution-check.md     # proposed Gate 0/1 review evidence
└── tasks.md                      # created: 60 implementation tasks, all unchecked
```

### Source tree — future implementation, not yet created

```text
src/main/java/vn/intrachat/
├── IntraChatApplication.java                  # update lifecycle, dispose resources
├── application/
│   ├── AppInfo.java                           # existing
│   ├── NetworkCoordinator.java                # commands, state, user-facing events
│   ├── NetworkEvents.java                     # app-facing callbacks/event types
│   └── InboundApprovalPort.java               # async user consent, injected for tests
├── domain/
│   ├── PeerIdentity.java                      # existing; ID self-claimed, not auth
│   ├── ConnectionState.java                   # existing; per session
│   └── ConnectionSession.java                 # ID, endpoint, states, close reason
├── protocol/
│   ├── MessageMetadata.java                   # existing
│   ├── MessageType.java                       # existing, extend technical types
│   ├── NetworkEnvelope.java                   # wire DTO and typed body
│   ├── JsonEnvelopeCodec.java                 # strict parsing/validation
│   ├── FrameCodec.java                        # bounded length-prefix frames
│   └── ProtocolException.java                 # categorized failures
├── transport/
│   ├── TcpListener.java                       # bind/accept lifecycle
│   ├── TcpConnector.java                      # outgoing connect/cancel/timeout
│   ├── TcpPeerSession.java                    # handshake/pending approval/read/write
│   ├── SessionRegistry.java                   # quotas, duplicates, shutdown
│   └── NetworkConfig.java                     # bind IP/port/caps
└── ui/
    ├── MainWindow.java                        # existing; add network panel/status
    ├── NetworkPanel.java                      # bind/connect/disconnect/reconnect
    └── InboundRequestDialog.java              # accept/reject, warning; no autoaccept

src/test/java/vn/intrachat/
├── protocol/FrameCodecTest.java
├── protocol/JsonEnvelopeCodecTest.java
├── transport/HandshakeTest.java
├── transport/SessionRegistryTest.java
├── transport/TcpLoopbackIntegrationTest.java
├── transport/NetworkFaultTest.java
└── application/NetworkCoordinatorTest.java

scripts/
├── check-environment.ps1                      # existing
├── verify-m0.ps1                              # existing
└── verify-m1.ps1                              # proposed; not yet implemented
```

**Structure decision:** Retain single Maven module and package architecture. UI knows only `NetworkCoordinator` & immutable view state, not `Socket`, `ServerSocket` or JSON parser. Domain models know nothing of Swing/TCP. Protocol validates bytes/DTO and maps to internal typed objects. Transport owns sockets. Service/application owns user approval and lifecycle. No persistent History/SQL code in M1.

## Architecture & Dependency Direction

```text
Swing UI / InboundRequestDialog (EDT)
  [Connect / Listen / Accept / Reject / Disconnect / Reconnect]
                      |
                      v
          NetworkCoordinator (application)
          | commands + session state/event streams
          | `InboundApprovalPort` async callback
          +--------+---------------+
                   |               |
                   v               v
         Transport ports        Protocol codec
         + bounded registry     [frame + JSON validation]
                   |
          TcpListener / TcpConnector
                   |
            TcpPeerSession
             direct TCP LAN
                   |
              remote peer

Future: Chat service (003), Group (004), History sync & ACL (005)
        attach ONLY after new authorized application interfaces.
```

- **Inbound on wire:** accept socket → read small HELLO under deadline/cap → validate syntactic fields/version → emit pending approval event → user **Accept/Reject** via async UI → on Accept finish handshake → activate bounded diagnostic session; Reject/timeout → close. A TCP socket accepted by the OS is **not** an approved peer session.
- **Outbound:** UI validates IP/port → coordinator submits connection task → TCP connect → send HELLO → await explicit remote Accept/Reject and compatible version → finish handshake → publish `CONNECTED`; no automatic fallback/retry.
- **EDT:** all view updates via `SwingUtilities.invokeLater`, UI handlers enqueue commands and immediately return. Never wait for network future on EDT.
- **Tests:** a mock `InboundApprovalPort` supplies explicit test-controlled Accept/Reject decisions (it is **not** the production UI autoaccept), allowing deterministic headless loopback tests.
- **Backpressure:** bounded session/semaphore and outbound queue bytes count. Full queue causes a visible recoverable error; no unbounded memory, no silent message loss.
- **Shutdown:** coordinator cancels pending approvals and connection tasks, closes sockets to unblock readers/writers, ends sessions and listener, awaits owned jobs with bounded timeout and reports failures.
- **Identity:** `deviceId` is a local stable UUID and remote **claim**. Local ID may be stored in user-specific app config (only nonsecret `deviceId`/displayName, not chat data); do not infer trust from same UUID, name or IP. Device identity persistence and ACL design in 005 remain separate.

## ADR — Technical Choices & Justification

| ID | Choice for M1 | Alternatives considered | Reason / negative trade-off |
| --- | --- | --- | --- |
| **ADR-01** | **TCP** direct, duplex stream | UDP messages, WebSocket relay | TCP ordering/framing and simple Java socket APIs; explicit framing and timeout needed |
| **ADR-02** | **Manual IPv4:port only** | mDNS, UDP broadcast, server directory | CL-02-01, predictable on Windows/firewall; manual address entry is less convenient |
| **ADR-03** | **Listener OFF at startup; local IPv4 binding**, user-started | Bind all `0.0.0.0` automatically | Minimizes exposed interfaces; user may need to select correct adapter |
| **ADR-04** | **Manual inbound approval on every incoming session** | Autoaccept, persistent trusted list | CL-02-02, visible consent; prompts can be abused, so cap pending and timeout |
| **ADR-05** | **No TLS/auth; synthetic diagnostics only, warning** | Mutual TLS, TOFU/pairing, encrypted chat | CL-02-03, keeps M1 transport teachable; not safe for private data; auth/ACL blocking requirement before 005 |
| **ADR-06** | **Manual reconnect only** | Backoff scheduler/auto reconnect | CL-02-04; user must click after failure, simpler state machine; less convenience |
| **ADR-07** | **Length-prefixed UTF-8 JSON 4-byte big-endian**, max 1 MiB | Newline JSON, Java serialization, protobuf | Works with split/merged TCP reads, inspectable, matches 001 draft; JSON overhead and need strict parser |
| **ADR-08** | **Jackson databind 2.21.5** for DTO parser | Build ad hoc parser, Gson, org.json | Strong JSON mapping and strict JSON constraints, testable; runtime dependency and strict validation configuration required |
| **ADR-09** | **Java 21 virtual threads + bounded semaphores/queues** | SwingWorker for sockets, fixed pool, async NIO selector | Clear blocking I/O without EDT blocking; virtual threads are cheap but **not resource bounds** |
| **ADR-10** | **One active session per declared remote device ID**, deterministic duplicate policy | Any number of sessions, indiscriminate close-first | Avoid confusing duplicate UI rows; remote ID can be spoofed, so this is only operational dedup, **not authentication** |
| **ADR-11** | **No auto Windows Firewall changes** | Add admin rule silently or accept public-profile inbound | Clear consent, avoids privilege escalation; requires user/IT setup |
| **ADR-12** | **Session liveness PING/PONG**; manual reconnect on failure | TCP keepalive only, no liveness probe | Detects silent broken links within bounded window; adds traffic and timer logic |
| **ADR-13** | **Only diagnostics request/response messages** | Early CHAT_TEXT or history exchange | Prevents accidental launch of insecure user chat/storage before M2/005 |
| **ADR-14** | **No JDK 25-only APIs; Java 21 runtime gate before M1 Verified** | Lift baseline to JDK25 silently | Honors original baseline and avoids unsupported compatibility claims |

**Open implementation-proof obligation:** confirm Jackson 2.21.5 artifact resolution, transitive modules and strict duplicate-key/unknown-property/trailing-token behavior under JDK21 during `tasks.md` implementation; same for virtual-thread shutdown. Do not claim the selected version/test suite is running merely because it appears in plan.

## Wire Contract & Handshake v1 — Proposed

**Wire status:** This section refines the [001 protocol draft](../001-project-foundation/contracts/protocol-v1-draft.md) into an M1 implementation target. Source codec and live network exchange are **not yet implemented**. Formal examples/error matrices will move to `contracts/network-v1.md` during `/speckit.tasks` and implementation.

### Frame and envelope

```text
4 bytes: unsigned big-endian N (1 <= N <= 1,048,576)
N bytes: strict UTF-8 JSON envelope (no Java object serialization)
```

- Length is measured in **bytes**, not UTF-16 Java chars or code points. In Java, read unsigned 32-bit length with overflow-safe conversion; reject 0, > cap and truncated frames **before allocating N bytes**. Preapproval reads obey tighter **16 KiB** cap. No resynchronizing after corrupted framing; close offending session.
- UTF-8 decoding must **REPORT** malformed/unmappable sequences, not silently replace them. JSON parser rejects duplicate keys, unknown required types, trailing tokens, missing required/null fields and excess nesting; cap nested depth in codec config (proposed 32).
- After approval, diagnostic request payload max 4 KiB; large unrelated JSON/control up to 1 MiB only with approved type-specific validation. No file, history or arbitrary user-origin data types accepted in M1.
- TCP has per-connection byte ordering only; message ID is not a guarantee of exactly-once delivery. Do not expose chat delivery statuses.

**M1 generic envelope (shape):**

```json
{
  "protocolVersion": 1,
  "messageId": "fc1c5e53-ded1-4705-ade3-4c997aeb46fb",
  "type": "DIAGNOSTIC_PING",
  "sender": {
    "deviceId": "1af45998-830f-440c-8d35-ac6a900dd1e4",
    "displayName": "May A"
  },
  "destination": {
    "kind": "PEER",
    "id": "71cd0a60-eb77-4103-938d-84466513aa44"
  },
  "sentAt": "2026-11-21T06:00:00Z",
  "payload": {
    "probeId": "bff1cda8-054b-4e69-8ff7-08ae04ad405a",
    "nonce": "demo-only"
  }
}
```

Destination `id` is the **declared remote device UUID** after handshake, not trusted authorization. On bootstrap `HELLO`, the sender does not yet know the remote ID. **Explicit exception:** `HELLO` must have `destination.kind=PEER` and `destination.id=00000000-0000-0000-0000-000000000000` as a *non-routable, reserved unknown-peer sentinel*. This sentinel is allowed **only** for the first `HELLO`; using it for other types, including diagnostics, is a protocol error. The recipient replies with actual local device ID in its sender fields; the caller verifies response correlation (nonce/message ID) and binds subsequent outbound envelopes to the reported ID. This is **not an authentication proof**. The protocol-v1 draft in 001 remains conceptual; this exception must be reconciled explicitly in the 002 wire contract and parser tests.

### Allowed M1 message types

| Type | When allowed | Required payload semantics | Privilege |
| --- | --- | --- | --- |
| `HELLO` | First bounded inbound/outbound handshake frame | `supportedVersions:[1]`, `requestId:UUID`, client app version; source declared identity | Untrusted; parse only minimal metadata to show prompt |
| `HELLO_ACCEPT` | Receiver **after user clicks Accept** | `requestId`, chosen `protocolVersion`, `sessionId`, receiver self-declared identity | Negotiation; no diagnostics until handshake completes |
| `HELLO_REJECT` | Receiver after user Reject / timeout / capacity / invalid version | `requestId` if known; generic reason code (no personal data) | Closes candidate socket after best-effort reply |
| `HELLO_CONFIRM` | Initiator confirms accepted negotiation | `sessionId`, echo selected version / request correlation | Negotiation confirmation |
| `HELLO_READY` | Receiver after valid confirmation | `sessionId` | Establishes active diagnostic session, not trust |
| `DIAGNOSTIC_PING` | Approved + negotiated active session | `probeId` + bounded synthetic nonce; no user-entered chat | Technical connectivity test |
| `DIAGNOSTIC_PONG` | Approved + negotiated active session | Same `probeId`, synthetic echo/status | Technical response only |
| `PROTOCOL_ERROR` | Bounded diagnostic error frame when safe to reply | Short machine-readable code, correlation if known, no raw payload | Does not grant access; terminate if fatal |

Names are the **M1 extension** to the draft enum currently containing `HELLO` and future chat/history/file types. `CHAT_TEXT`, `GROUP_MESSAGE`, `SYNC_*`, `FILE_*` **must be rejected** in M1 runtime with `UNSUPPORTED_TYPE_FOR_PHASE`; do not silently enable future capabilities.

### State and authorization flow

```text
A (initiator)                              B (receiver)
UI: choose IPv4:port                       UI: Start listening (explicit)
TCP connect -----------------------------> bounded accept (pending only)
HELLO, version, claim -------------------> validate format (not trusted)
                                           PENDING_APPROVAL + prompt
                                           /                       \
                                      Reject/timeout              Accept
                                           |                       |
socket close <------ HELLO_REJECT ---------+                       |
A FAILED, can manually retry                                      |
                 <------------------- HELLO_ACCEPT (B's claim, session)
HELLO_CONFIRM -------------------------> validate session/version
                 <------------------- HELLO_READY
A CONNECTED                                     B CONNECTED*
 [only diagnostic PING/PONG; no History/File/Chat]
 disconnect/timeout/cancel -----> close sockets; state DISCONNECTED/FAILED
 user clicks Reconnect ---------> new TCP attempt, new approval required
```

*B marks its own session CONNECTED after successfully sending HELLO_READY. A marks CONNECTED after receiving it. This is a **local observation**, not a distributed atomic commit; if the connection drops during final response, heartbeat/disconnect handling reconciles state without inventing a stronger delivery guarantee.

**Pending approval controls:**

- Listener starts only by explicit user command. Max **4 pending inbound sockets** (including awaiting HELLO/prompt); extra connections receive best-effort reject or are immediately closed, never queued unboundedly.
- Validate and show **actual remote TCP endpoint** and **untrusted declared device UUID/display name**; escape/filter control characters; label clearly: “Chưa xác thực danh tính, kết nối chưa mã hóa. Chỉ dùng thử nghiệm.”
- The prompt runs on Swing EDT but **does not block EDT** (`JDialog` use modeless callback or equivalent); returned decision is an async `CompletionStage<ApprovalDecision>`. Timeout after **30 seconds**, with cancel when app/window closes, treats pending as **rejected**.
- Approval grants only an M1 **diagnostic transport session**. No persisted trust list, pairing token, history access, file receipt or group membership. Accept is not identity proof.
- If a peer sends other frames while pending approval, reject/close; no application diagnostics run. Receive bounds apply even before prompt.
- Automated tests can inject a **scripted explicit decision** through `InboundApprovalPort`, while production must always obtain a human UI decision.

**Duplicate sessions, proposed deterministic policy:** one active session per declared remote `deviceId`. If simultaneous outbound/inbound sockets exist, the connection initiated by the **lexicographically smaller local/remote UUID** is preferred; if same-direction duplicates, keep the earliest successfully negotiated session and close the rest. Self-reported UUID equal to local UUID is refused (self-connect guard); this is *not* spoofing protection. Do not count a pending/unapproved inbound as active. Preserve the UI's honest state when a duplicate is refused; add tests for two peers connecting simultaneously and a forged-ID collision.

### Failure taxonomy and UI feedback

| Error/code (proposed) | Input/condition | System outcome | UI/user language |
| --- | --- | --- | --- |
| `INVALID_ENDPOINT` | Invalid IPv4 or port | No socket created | “IP hoặc cổng không hợp lệ” |
| `BIND_FAILED` | Selected interface/port unavailable | Listener stopped | “Không thể lắng nghe tại địa chỉ/cổng này” |
| `CONNECT_TIMEOUT` / `CONNECT_REFUSED` | Remote unavailable | Attempt FAILED, socket closed | “Không kết nối được; kiểm tra IP/cổng/firewall” |
| `PENDING_APPROVAL` | Inbound HELLO syntactically valid | Await user; **not CONNECTED** | “Yêu cầu kết nối mới — Chấp nhận / Từ chối” |
| `USER_REJECTED` / `APPROVAL_TIMEOUT` | Declined/not answered | Close socket; no active session | “Yêu cầu đã bị từ chối/hết thời gian” |
| `HANDSHAKE_INVALID` / `VERSION_UNSUPPORTED` | Missing/wrong version/handshake | Reject and close | “Phiên bản/handshake không tương thích” |
| `FRAME_INVALID_LENGTH` / `FRAME_TRUNCATED` | Negative/zero/oversized/partial | Reject before unsafe allocation, close | “Dữ liệu kết nối không hợp lệ” |
| `UTF8_INVALID` / `JSON_INVALID` | Bad encoding/syntax/schema/unknown type | Reject and close offending session | “Thông điệp không hợp lệ” |
| `UNSUPPORTED_TYPE_FOR_PHASE` | Chat/group/history/file traffic in M1 | Reject/close without side effects | “Loại dữ liệu chưa hỗ trợ” |
| `CAPACITY_EXCEEDED` | Too many sessions/prompts/queued bytes | Reject/close or fail send boundedly | “Đã đạt giới hạn kết nối” |
| `REMOTE_CLOSED` / `HEARTBEAT_TIMEOUT` | Peer close/silent failure | DISCONNECTED/FAILED; cleanup | “Đã mất kết nối; chọn Kết nối lại” |
| `USER_CANCELLED` / `SHUTTING_DOWN` | User disconnects/quits | Cancel tasks, close and reclaim | “Đã ngắt kết nối” |

Error frames are **best effort only**; never wait indefinitely to send errors to a malicious or blocked peer. No raw payload/stack trace leaks into UI or routine logs. Unexpected exception may be logged with code and sanitized summary.

## Lifecycle, Resource Boundaries & UX

### State machines

```text
Listener:
 STOPPED -> STARTING -> LISTENING -> STOPPING -> STOPPED
             \-> FAILED            \-> FAILED

Outbound:
 DISCONNECTED -> CONNECTING -> AWAITING_REMOTE_APPROVAL
              -> NEGOTIATING -> CONNECTED
                    |              |
                    +-> FAILED <---+ (I/O error, timeout)
                    +-> DISCONNECTED (user cancel / remote close)
   [manual "Kết nối lại" creates a new attempt, never an automatic retry]

Inbound:
 TCP_PENDING -> HELLO_VALIDATED -> WAITING_USER_APPROVAL
                                  |            |
                                 REJECT       ACCEPT
                                  |            |
                                 CLOSED     NEGOTIATING
                                               |
                                        CONNECTED / FAILED -> CLOSED
```

The existing domain `ConnectionState` enum may remain coarse (`DISCONNECTED`, `CONNECTING`, `CONNECTED`, `FAILED`) while the application exposes **more precise, temporary handshake/approval substate**. Avoid conflating network `CONNECTED` with recipient read acknowledgments.

### Budget proposal (must be benchmarked and tested)

| Resource or operation | Initial bound | Test |
| --- | --- | --- |
| TCP active sessions | 4 per app | Fifth candidate rejected without crash/queue leak |
| Inbound HELLO/approval pending | 4 sockets | Fifth pending rejected/closed |
| Preapproval HELLO frame | 16 KiB max | 16 KiB allowed only if valid; > cap rejected before allocation |
| Postapproval control frame | 1 MiB max | Exact limit, +1, 0, negative/overflow and truncated prefix |
| Diagnostic payload | 4 KiB max | Oversize/Unicode byte length tests |
| Per-session pending writes | ≤16 frames **and** ≤2 MiB | Backpressure, bounded failure with no message corruption |
| TCP connect | 5 seconds | Unreachable endpoint times out, cancel interrupts |
| Receive initial HELLO | 5 seconds | Silent inbound socket expires before prompt |
| Prompt approval | 30 seconds | No click → reject and close |
| Expected frame read while active | 15 seconds, with heartbeat policy | Partial frame cannot block forever |
| PING interval / PONG deadline | 10 / 30 seconds | Lost link transitions state; no auto reconnect |
| UI event dispatch during connection | target p95 ≤200 ms event latency on reference PC | UI timer/click smoke measurements |
| Normal shutdown after user cancel | target ≤2s (except OS/system exceptions) | 5 close cycles, no process/thread/socket leak |
| Retry policy | 0 automatic attempts | Only explicit click starts an attempt |

**Trade-off:** 1 MiB control frames are useful for later metadata but M1 diagnostics require only small samples. Never allow arbitrary 1 MiB inbound JSON before approval. Quotas must be enforced **per session and globally**, not merely by number of Java virtual threads. Test under flooded/slow/silent peers using synthetic data and bounded local resources.

### Windows user interface (M1 only)

- **Local network panel:** choose local IPv4 adapter/bind address, display or edit listener port (default 47321), `Bắt đầu lắng nghe` and `Dừng lắng nghe`, show actual bound endpoint. Warn if the machine is on Public profile; **do not** silently open firewall or expose on all interfaces.
- **Outbound panel:** text inputs IPv4 + port, `Kết nối`, `Hủy`, `Ngắt kết nối`, `Kết nối lại`; show spinner/text during connecting/waiting for remote approval. Disable duplicate submissions while an attempt is active.
- **Inbound prompt:** remote IP:port plus clearly **unverified** `deviceId` and display name, accept/reject buttons, 30s deadline, security warning. Reject if dialog closes/expires.
- **Diagnostics panel:** synthetic `Ping` action and Pong/latency result without user-entered arbitrary messages; only after session active. No contact list, chat composer, History or file UI in M1.
- **Accessibility:** readable labels separate from color; keyboard-accessible accept/reject and cancel; no network operations on EDT.
- **Security disclosure:** before starting listener/first connection, show notice that peers are **not authenticated** and traffic is **not encrypted**; do not recommend sensitive payloads. Acceptance of a peer must not be shown as persistent trust.

### Windows networking & firewall operational policy

- Bind the selected **local** IPv4 interface (default suggested port 47321); prefer Private LAN adapter. IPv4 `127.0.0.1` permitted for local two-process testing; user must select real LAN IPv4 for other PCs.
- Validate IP dotted-decimal, reject loopback as a **remote two-machine** test setup (loopback still valid for dev) and reject unspecified/multicast/broadcast addresses as peer destinations; explicit port range 1–65535.
- If listening fails with `Address already in use` or profile/firewall blocks traffic, show troubleshooting, never request administrator privileges silently and never modify Windows Firewall programmatically.
- Documentation may explain to users/admins how to allow the chosen TCP port on **Private** profile and how to remove the rule afterward, without assuming all campus routers or endpoint protections permit direct incoming TCP.
- No hardcoded public endpoint, no DHCP discovery, no NAT traversal and no IPv6 requirement in M1. Changes to support these need a spec/plan update.

## Testing & Verification Strategy (NOT RUN)

**Environment plan:** Windows 11 x64, JDK 21 actual runtime **required** for approved baseline; JDK25 compile-only pass remains M0 historical evidence. Use Maven Wrapper 3.9.16 and JUnit 5.14.4. Two separate processes (independent device IDs and ports) for loopback, then **two real Windows machines** on reachable LAN, with Internet disabled/absent but LAN still available. Test records must include exact commands, result codes, Windows/JDK/Maven versions and sanitised endpoint metadata; never actual private chat data.

| Verify ID | Scenario | Expected evidence | AC |
| --- | --- | --- | --- |
| **V-01** | Windows Java 21 + Maven baseline; remaining M0 T025/T040/T041 | M0 Gate 1 evidence and `mvnw.cmd clean test` PASS, no claims otherwise | AC-02-K |
| **V-02** | IPv4/port positive, malformed, port conflict, no listener | Valid entry progresses; invalid not connected; bind/connect errors visible | A, B, H |
| **V-03** | Listener manual start, inbound user Accept, inbound Reject, no click 30s | No `CONNECTED` or diagnostic bytes before explicit Accept; prompt deadline closes socket | C, F, G, J |
| **V-04** | HELLO with same/different version; bad/missing sender & unsupported kind | Only approved compatible session active; no identity trust inferred | C, E, J |
| **V-05** | One byte at a time, coalesced frames, truncated length/JSON, over cap, malformed UTF-8 | Exactly correct valid frames; malformed rejected before unbounded allocation | D, E |
| **V-06** | Synthetic PING/PONG bidirectional; Unicode/emoji test | Correct probe ID, message IDs, timestamps and content; no chat/read state | A, D, G |
| **V-07** | Outbound connect timeout, inbound HELLO silent, pending approval expiry, cancel midhandshake | Bounded failures, proper state, no hung EDT | F, G |
| **V-08** | Remote hard stop, half-open link, heartbeat timeout, user Disconnect, manual Reconnect (3 cycles) | Detect disconnect, explicit retry only, cleanup and no duplicate session | F |
| **V-09** | Session flooding/pending cap, queue overflow, simultaneous cross connects/self-connect | Hard quotas enforced, bounded resources, no inconsistent status | C, F, J |
| **V-10** | Two **processes** loopback, 5 connects, roles reversed | 5/5 connect/handshake/close, correct independent device ID and port | A, C, I |
| **V-11** | Two **real Windows devices** same LAN with no Internet | P2P diagnostics in both directions, remote consent and documented firewall setup | A, H, I |
| **V-12** | UI interactions during connection, 5 close cycles, lifecycle cleanup | EDT responsive, no socket/worker/process leak and timing samples | F, G, I |
| **V-13** | Unexpected `CHAT_TEXT`/`SYNC_BATCH`/`FILE_OFFER`, unapproved diagnostic, untrusted ID claim | Rejected/no private data/no History DB access; security warning present | E, J |
| **V-14** | Build & regression using `scripts/verify-m1.ps1` (to create) | Existing M0 tests continue PASS, new automated tests and real-LAN log referenced | I, K |
| **V-15** | Final Constitution Check I–VII, release evidence & documentation | No unaddressed MUST; all AC-02-A…K PASS or M1 remains NOT VERIFIED | K |

**Key negative tests:** reject without clicking Accept, fake device ID matches legitimate display name, invalid handshake, partial frame disconnect, 0/oversize length, multiple frames per TCP read, unsupported message type, queue full, port conflict, click Reconnect twice. At least one test for successful exchange, invalid input, timeout/disconnect and resource cleanup is **mandatory** by Constitution.

**Testing proof rule:** A mock approval provider returning `ACCEPT` in unit/integration tests is a synthetic **explicit decision**, not evidence that production UI auto-accept is allowed. Record that distinction. `M1 Verified` requires a real user clicking Accept on the other PC and evidence from both peers.

**Manual performance measurement:** Record reference Windows hardware, JVM settings, per-scenario sample count (recommend ≥5), p95 response and close timings. Targets above are hypotheses until measured; do not edit success criteria to match unproven numbers after a failed run without review.

## Traceability: Spec → Components → Tests

| Spec / User Story | Design deliverable | Evidence |
| --- | --- | --- |
| **US1 / FR-001–006** Direct P2P + manual IP | Listener, connector, NetworkConfig, network panel, Windows guide | V-02, V-10, V-11 |
| **US2 / FR-007–011** Handshake/identity/version | Protocol HELLO flow, session registry and pending approval | V-03, V-04, V-09 |
| **US3 / FR-012–018** Diagnostic exchange + framing | Codec, typed M1 messages, PING/PONG | V-05, V-06, V-13 |
| **US4 / FR-019–024** Nonblocking lifecycle | Coordinator, event stream, shutdown/reconnect/cancel | V-07, V-08, V-12 |
| **US5 / FR-025–030** Security/Windows/evidence | Trust boundary, UI warning, Firewall doc, tests | V-11, V-13–15 |
| **CL-02-01** Manual connection only | UI IPv4/port form; no discovery workers | V-02, V-11 |
| **CL-02-02** Inbound manual approval | InboundApprovalPort + prompt + timeout | V-03, V-10, V-11 |
| **CL-02-03** Untrusted/plaintext diagnostics only | Wire message whitelist, security notice, no History/File | V-03, V-13, V-15 |
| **CL-02-04** User reconnect only | Explicit `Kết nối lại`, no auto retry loop | V-08 |
| **SC-001–010 / AC-02-A…K** M1 milestone outcomes | Acceptance matrix with PASS/FAIL/NOT RUN and logs | V-01…V-15 |

## Implementation Phases (future tasks, not executed)

### Phase 0 — Close M0 blockers / Research

- Reconfirm JDK21 runtime compatibility and physically offline GUI acceptance from M0; close T025/T040/T041 or explicitly document any approved deviation.
- Validate Jackson 2.21.5 transitive dependency compatibility and strict JSON settings; create `research.md`, prototype only in dedicated test scope.
- Produce a small versioned `contracts/network-v1.md` with actual examples, error codes and acceptance/reject semantics. Reconcile HELLO destination bootstrap sentinel and `MessageType` addition against M0 metadata tests.

### Phase 1 — Protocol & Transport (test-first)

- Write parser/frame boundary tests, including malicious length/UTF-8/version/unsupported types.
- Implement DTO mappings, FrameCodec and strict JsonEnvelopeCodec; keep application models separate from wire DTOs.
- Write loopback tests and implement TcpListener/TcpConnector, session registry with quotas, acceptance deadline and safe socket shutdown.
- Implement HELLO request/response/confirmation, session version negotiation, duplicate policy, synthetic PING/PONG and heartbeat. All acceptance decisions come from injected `InboundApprovalPort` (real Swing implementation later).

### Phase 2 — Application, UI & Lifecycle

- Create NetworkCoordinator/session events and explicit `Connect`, `Cancel`, `Listen`, `Disconnect`, `Reconnect` commands.
- Extend Swing MainWindow with local bind endpoint/port, outbound IP/port, pending Accept/Reject, status and security warnings.
- Hook async approval and event updates without EDT blocking. Add cleanup on window exit/shutdown.
- Test manual retries, simultaneous connections and bounded queues; ensure feature 003/004/005/006/007 remains disabled.

### Phase 3 — Verification, Documentation, M1 Gate

- Two-process repeatable localhost tests, script `verify-m1.ps1`, fail-on-test-failure control.
- Actual two-device Windows Private LAN with Internet unavailable, mutual consent and bidirectional diagnostics; document firewall permission and connection failures.
- Re-run regression and Constitution Gate 1, create M1 acceptance report; update `README.md` and `ROADMAP.md` only according to **verified** implementation state.
- Mark M1 Verified only when all AC-02-A…K are supported by tests and M0 prerequisites are resolved.

## Risks, Deferrals & Complexity Tracking

| Risk | Severity | Mitigation / Gate |
| --- | --- | --- |
| **No auth/encryption**; malicious LAN device impersonates peer | **High** | Explicit diagnostic-only limitation + warning + consent; no personal data/chat/History; introduce verified identity and ACL before real History Sync |
| Listener exposed on wrong interface/Public firewall profile | High | User starts listener, chooses bind IPv4, no auto firewall edits; documented Private-profile rule |
| UI approval prompts abused by inbound flood | High | Max 4 pending, 16 KiB preapproval, 5s HELLO, 30s approval, rate-limit candidate attempts in plan-level resource controls |
| Session duplicates and forged device IDs | High | Deterministic operational dedup, source claims labeled untrusted; tests for concurrent connects; auth deferred |
| JSON parsing/length overflow memory exhaustion | High | Unsigned length checks before alloc, strict UTF-8/JSON, whitelist types, frame and nesting bounds, malicious tests |
| Silent disconnect not detected | Medium | Heartbeat timeout, close and honest UI state; manual reconnect |
| UI freeze due to join/lock/network blocking | High | All network on virtual threads; UI callback only, bounded shutdown and responsiveness tests |
| Wi-Fi client isolation / IPv4 interface changes | Medium | Manual IPv4 endpoint, clear diagnostic error, document network assumptions |
| Java 21 runtime M0 not tested | High gate | Keep T025/T040/T041 open until actual JDK21/Windows evidence and M0 handoff |
| Two real Windows machines not available | High acceptance | Loopback helps development only; **cannot** mark AC-02-A/I or M1 Verified |
| New Jackson runtime dependency mismatch | Medium | Pin after Maven validation, review transitive dependencies and security; do not pretend it was executed |
| Spec creep into chat/storage/media | Medium | Whitelist diagnostic types; no History DB, File, chat or Group APIs in M1 |

**Complexity exceptions:** No deviations from Constitution MUST proposed. A direct P2P listener has an accept loop but is **not** a mandatory centralized messaging server; each peer is a dual-role endpoint. Virtual threads and JSON dependency must be justified by bounded tests, not introduced solely for fashion. Authentication and encryption are **deliberately outside M1**, so the release cannot advertise a secure chat product yet.

## M1 Exit Criteria (all NOT VERIFIED)

- [ ] **AC-02-A:** Two real Windows peers exchange diagnostics two ways over direct LAN without Internet.
- [ ] **AC-02-B:** Manual IPv4/port only; no discovery behavior.
- [ ] **AC-02-C:** User accepts inbound request; version/identity syntax checked; no authentication inferred.
- [ ] **AC-02-D:** Framing/Unicode/bounds verified.
- [ ] **AC-02-E:** Unsupported/invalid/truncated/oversized inputs safely rejected.
- [ ] **AC-02-F:** Manual reconnect, timeouts, cancel, cleanup, no EDT freeze.
- [ ] **AC-02-G:** Honest connection/approval/error states and diagnostic-only UI.
- [ ] **AC-02-H:** Windows IPv4/port/Private Firewall documentation verified.
- [ ] **AC-02-I:** Loopback two-process + two real devices tested, outcomes documented.
- [ ] **AC-02-J:** Insecure-unverified notice + no History/File/Group access or sensitive payload.
- [ ] **AC-02-K:** M0 dependency gate resolved; Constitution I–VII Gate 1 evidence clear.

**Next step:** follow [tasks.md](tasks.md), now created with **60 unchecked tasks** over 8 phases and 5 user stories; **T001 M0 gate** precedes networking code. Do not create networking classes or mark M1 tests PASS until implementation and validation actually happen. Test-first protocol, inbound approval, real LAN evidence and M0 gate are required.

## References

- [Feature spec 002](spec.md)
- [Feature spec 001](../001-project-foundation/spec.md)
- [M0 acceptance evidence](../001-project-foundation/evidence/m0-acceptance-report.md)
- [M0 protocol draft](../001-project-foundation/contracts/protocol-v1-draft.md)
- [IntraChat Constitution](../../.specify/memory/constitution.md)
- [Java virtual threads (JEP 444)](https://openjdk.org/jeps/444)
- [Apache Maven Wrapper](https://maven.apache.org/tools/wrapper/)
- [Jackson Databind Maven artifact](https://central.sonatype.com/artifact/com.fasterxml.jackson.core/jackson-databind)
- [GitHub Spec Kit](https://github.com/github/spec-kit)
