<!--
Sync Impact Report
- Constitution version: 1.0.0 (initial ratification)
- Added principles: I. LAN-First; II. Protocol Contract; III. Concurrency & Reliability;
  IV. Security & Privacy; V. Test-Backed Delivery; VI. Honest UX; VII. Spec-Driven Changes.
- Added sections: Product & Architecture Constraints; Development Workflow & Quality Gates.
- Templates/scripts reviewed: none installed in this repository at ratification.
- Follow-up: after initializing Spec Kit, check generated templates and align
  their Constitution Check with this document; preserve this constitution.
-->

# IntraChat Constitution

## Core Principles

### I. LAN-First & Explicit Topology

- IntraChat **MUST** support chat between devices in a local-area network without requiring a public Internet service.
- Every feature specification **MUST** state whether traffic is peer-to-peer, relayed by a LAN coordinator, or distributed within a group. “P2P” **MUST NOT** be used to claim a fully decentralized architecture without a design.
- Peer discovery, connection initiation, ports, transport (TCP/UDP or other), reconnection and LAN firewall assumptions **MUST** be documented in a plan before implementation.
- The system **MUST** handle a peer disconnecting without hanging other conversations or the application.
- Cloud authentication, Internet synchronization and public endpoints **MUST NOT** become implicit dependencies; they require separate approved specs.

**Rationale:** Local connectivity and predictable behavior on ordinary classroom/office LANs are the primary product constraints.

### II. Versioned Message & Identity Contract

- Network message types **MUST** have explicit schema/contracts with type, message ID, sender identity, destination (peer/group as applicable), timestamp and payload semantics.
- Any incompatible schema change **MUST** introduce a versioning or negotiation strategy; silent incompatibilities are prohibited.
- Sender identity and mutable display name **MUST** be represented separately. The UI **MUST NOT** infer a message's sender solely from the active screen or an untrusted label.
- Peer presence/connection states and message delivery/read states **MUST** be modeled independently and named unambiguously.
- Timestamp storage/transport **MUST** use an agreed representation (prefer UTC/ISO 8601 or epoch); local rendering **MUST** clearly apply the user's locale/timezone.
- Protocol specs **MUST** define framing, size limits, failure responses and handling of malformed/unknown types.
- Group message delivery guarantees, membership changes, ordering and duplicate behavior **MUST** be explicit rather than assumed.

**Rationale:** P2P, Group, Sender, Time, State and History all depend on one consistent protocol contract.

### III. Non-Blocking UI, Bounded Resources & Resilience

- Socket operations, file/media transfer and history persistence **MUST NOT** block the UI thread.
- Every network operation **MUST** have an explicit lifecycle, timeout/cancellation policy and cleanup path for streams, sockets and workers.
- Incoming payloads **MUST** have size and resource bounds. File and media transfers **MUST** stream incrementally rather than require full in-memory buffering.
- The app **MUST** distinguish recoverable disconnects, invalid data and fatal errors; failures **MUST** be visible through appropriate status/error feedback.
- Retry, acknowledgement, deduplication and ordering guarantees **MUST** be specified before being promised to users.
- An interrupted transfer **MUST NOT** be displayed as complete.

**Rationale:** LAN connections are transient, and large media files must not freeze the client or exhaust its memory.

### IV. Security, Privacy & Safe File Handling (NON-NEGOTIABLE)

- All bytes, file paths, filenames and metadata received from other LAN devices **MUST** be treated as untrusted input.
- Receivers **MUST** prevent directory traversal, writing outside the approved destination, and unintended overwrite; downloading/saving behavior **MUST** be visible to the user.
- Inbound protocol data **MUST NOT** be processed through unsafe native Java object deserialization.
- Secrets, credentials, private chat content and personal file contents **MUST NOT** be committed to Git or written to routine logs.
- Any local history storage **MUST** specify who can access it, how deletion works and which data persists after exit.
- Transport encryption, peer authentication and access control **MUST NOT** be claimed as implemented unless actually designed, implemented and verified. If omitted, README/UI **MUST** disclose that security limitation before release.
- User-supplied file/media types **MUST** be validated appropriately; security tests **MUST** include malformed inputs and unsafe paths.

**Rationale:** LAN peers cannot be assumed trustworthy; file-sharing mistakes can damage local user data.

### V. Automated Tests & Verifiable Behavior (NON-NEGOTIABLE)

- Every behavioral change **MUST** include suitable automated tests, or clearly document why tests are temporarily blocked and track the gap.
- Network features **MUST** be tested with at least two local peers/processes or deterministic loopback equivalents; tests **MUST NOT** require a public Internet service.
- Tests **MUST** cover a successful exchange, invalid input, timeout/disconnect and cleanup for affected network functionality.
- File/media changes **MUST** test byte integrity, size boundaries, cancellation and path validation.
- Group changes **MUST** test multiple peers, membership transitions and intended recipients.
- History, Time, State and Sender changes **MUST** test metadata correctness and edge cases (including timezone and duplicate display names when relevant).
- Agents and maintainers **MUST** report actual command results; a test **MUST NOT** be marked as passing unless executed.

**Rationale:** Requirements are meaningful only when reproducibly verifiable across multiple LAN participants.

### VI. User-Visible Truth & Consistent Experience

- The interface **MUST** make the sender, readable time and message context visible for each displayed chat message.
- UI indicators such as `online`, `sent`, `delivered`, `read` and `completed` **MUST** correspond to defined, observable states; optimistic UI may be used only if it is clearly distinguished.
- Text and emoji **MUST** preserve Unicode content; unsupported media formats **MUST** receive a clear fallback/error message.
- Long-running operations (connection and file/media transfer) **MUST** show progress or state and offer cancellation where feasible.
- Failures **MUST NOT** silently discard messages or misrepresent unsuccessful transfers as successful.
- Feature names such as Video and Sound **MUST** be scoped explicitly; sending media **MUST NOT** imply real-time calling functionality.

**Rationale:** Accurate sender/time/state information is part of the core chat product, not optional decoration.

### VII. Spec-Driven, Incremental & Honest Delivery

- Before a new feature is implemented, maintainers **MUST** document its user-facing requirements and acceptance criteria in a feature specification.
- The technical plan **MUST** record Java/JDK/build/UI/storage/transport decisions and their trade-offs before committing to dependencies or architectural boundaries.
- Implementation **MUST** be divided into reviewable tasks; each completed task **MUST** be validated against its acceptance criteria and relevant tests.
- Documentation **MUST** distinguish `planned`, `in progress`, `implemented` and `verified`; absent source code **MUST NOT** be portrayed as a working application.
- Architecture changes and new dependencies **MUST** be justified in the plan. Scope increases such as real-time calls, cloud accounts or remote Internet support **MUST** receive separate specification and approval.

**Rationale:** Spec Kit is used to control scope and prevent undocumented assumptions from becoming protocol or UX defects.

## Product & Architecture Constraints

- **Language:** Java. The required JDK version, desktop UI toolkit, dependency manager and persistence technology are open decisions until adopted in a reviewed technical plan.
- **Required functional scope:** P2P, Group, File, Emoji, History, Image, Video, Sound, State, Time and Sender. Each **MUST** receive clear acceptance criteria before its implementation is marked complete.
- **Separation of concerns:** UI, transport, protocol, domain/services and persistence **SHOULD** remain independently testable; merging layers requires a documented reason.
- **Media semantics:** Image, Video and Sound refer to media handling/sharing until a separate specification authorizes streaming calls or live conferencing.
- **Data and privacy:** The product **MUST** be usable on LAN. Any storage design **MUST** state local data location, retention/deletion policy and failure handling.
- **Compatibility:** Messages **MUST** be robust against unknown types, mismatched versions and peers that disconnect mid-operation.
- **Performance budgets:** Concrete concurrency, file-size, transfer-speed and response-time targets **MUST** be defined in the relevant plan; do not invent universal numeric thresholds without measurement.

## Development Workflow & Quality Gates

1. **Constitution check:** Every feature plan **MUST** include a Constitution Check against Principles I–VII and note justified exceptions.
2. **Specify:** Capture scenarios, acceptance tests, non-functional requirements and edge cases, especially offline LAN behavior and network failure.
3. **Clarify/plan:** Decide transport, discovery, protocol framing, group semantics, history storage, UI toolkit, JDK and build tool for the relevant scope. Record alternatives where needed.
4. **Tasks/implementation:** Break work into independently verifiable changes. Keep networking and file safety constraints visible in code review.
5. **Verification:** Run available automated tests; for LAN functionality record local multi-peer test evidence. Documentation-only changes can use review/lint checks.
6. **Release gate:** A release **MUST NOT** claim a capability, security property or test result that has not been verified; known limitations **MUST** be documented.
7. **Repository hygiene:** No credentials, genuine user conversations or received private files in commits. Update README and specs for user-visible changes.

The official GitHub Spec Kit CLI/templates have **not** yet been initialized in this repository. When initialized, maintainers **MUST** review generated files for consistency with this constitution rather than blindly replacing its project-specific rules.

## Governance

- **Authority:** This constitution is the project's governing engineering agreement. Feature specs, plans, task lists, code reviews and agent instructions **MUST** comply; exceptions require written rationale and maintainer approval.
- **Amendments:** Changes **MUST** be reviewed, explain motivation/impact, update affected guidance, and record a version bump. Material changes **MUST** be reflected in the Sync Impact Report.
- **Semantic versioning:** MAJOR = incompatible removal/redefinition of a binding principle; MINOR = new principle or materially expanded rule; PATCH = editorial clarification without changed obligations.
- **Review and enforcement:** Each feature plan **MUST** contain a Constitution Check, and unresolved violations of **MUST** requirements block feature completion.
- **Initial adoption:** The first version is ratified on 2026-11-21. Future amendments retain the original Ratified date and change Last Amended.

**Version: 1.0.0 | Ratified: 2026-11-21 | Last Amended: 2026-11-21**
