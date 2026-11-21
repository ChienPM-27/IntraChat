# IntraChat Architecture — Foundation

**Status:** Design reviewed for Giai đoạn 01 (M0). Runtime networking/history are not implemented.
**Links:** [Feature plan](../specs/001-project-foundation/plan.md) · [Constitution](../.specify/memory/constitution.md)

## Dependency boundaries

```text
                      +----------------------+
                      | Swing UI             |
                      | MainWindow (EDT)     |
                      +----------+-----------+
                                 | user actions, state presentation
                                 v
                      +----------------------+
                      | application          |
                      | AppInfo & use cases  |
                      +----------+-----------+
                                 | domain objects / ports
                                 v
                      +----------------------+
                      | domain               |
                      | PeerIdentity         |
                      | ConnectionState      |
                      +----------------------+
                           ^              ^
                           |              |
          +----------------+              +-----------------+
          |                                                  |
 +-------------------+                              +-------------------+
 | protocol          |                              | adapters (future) |
 | MessageMetadata   |                              | transport (002)   |
 | MessageType       |                              | history (005)     |
 +-------------------+                              +-------------------+
```

- UI may depend on application/domain state, **not on TCP/UDP/SQL implementation**.
- Application coordinates use cases and holds interfaces (ports) for future transport/history adapters.
- Domain must stay independent of Swing, Java sockets, database and JSON serialization libraries.
- Protocol represents typed immutable metadata; **no socket or parser exists yet**.
- Transport planned for 002 only; persistence/history sync planned for 005 only.

## M0 source map

| File | Responsibility |
| --- | --- |
| `src/main/java/vn/intrachat/IntraChatApplication.java` | Single entrypoint; diagnostics or open window on EDT |
| `src/main/java/vn/intrachat/ui/MainWindow.java` | Minimal title/status/error-free exit; no socket calls |
| `src/main/java/vn/intrachat/application/AppInfo.java` | Product identity and startup capability flag |
| `src/main/java/vn/intrachat/domain/PeerIdentity.java` | Immutable device UUID + mutable displayName copy |
| `src/main/java/vn/intrachat/domain/ConnectionState.java` | Device connection state, not read/delivered indicator |
| `src/main/java/vn/intrachat/domain/MessageDeliveryState.java` | Separate future message-delivery state vocabulary |
| `src/main/java/vn/intrachat/protocol/MessageMetadata.java` | Typed metadata only: no JSON parser or network transport |

## Startup/shutdown

1. Java invokes `IntraChatApplication.main`.
2. `--diagnostics` prints a truthful startup state then exits (useful for non-GUI CI).
3. Normal mode checks for a graphical session then schedules `MainWindow` using `SwingUtilities.invokeLater`.
4. All Swing creation/updates occur on the **Event Dispatch Thread** (EDT).
5. Closing the window releases Swing resources and exits. M0 does not create sockets, workers, databases or file transfers.
6. Future long-running jobs must use background worker(s), bounded queues, cancellation and timeouts; update UI via `SwingUtilities.invokeLater`.

## Evolution after M0

- **002:** TCP transport plus discovery/manual IP fallback; handshake and peer identification; no Internet server.
- **003:** chat command handlers, event reception and truthful State/Sender/Time/Emoji UI.
- **004:** decentralized Group membership and fanout; authorization of membership.
- **005:** SQLite local store and P2P History delta sync with authenticated peers and per-conversation permission checks.
- **006–007:** bounded streaming file transfer, Image/Video/Sound send/receive/play fallback.

## Security invariants

Network bytes and file names are untrusted. Do not deserialize inbound native Java objects. Restrict frame/file sizes before allocation, prevent path traversal and accidental overwrites. Avoid logging content, user secrets or private history. No feature may advertise `delivered`, `read` or `synced` without confirmed evidence.

## Tests

JUnit covers product identity, connection-state separation, immutable peer IDs and envelope metadata. A separate Windows GUI lifecycle check is required for M0; unit tests alone cannot demonstrate that all window close paths work.
