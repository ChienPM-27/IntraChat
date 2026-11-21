# US3 — Domain and protocol contract review

**Executed:** 2026-11-21
**Status:** PASS at M0 design + unit test scope.

## Sources reviewed

- [Architecture](../../../docs/architecture.md): Swing EDT, application/domain, protocol, transport deferred 002, persistence deferred 005.
- [Data Model](../data-model.md): Peer/Device, Conversation, Message, Group, Attachment, Transfer, Connection/DeliveryState, HistoryEvent, SyncCursor.
- [Protocol Draft](../contracts/protocol-v1-draft.md): versioned JSON envelope, required identity/destination/time, 1 MiB control frame draft, malformed type/frame/version and UTC.
- [History Sync](../contracts/history-sync-design.md): S1 offline, S2 duplicate, S3 unauthorized, S4 concurrent, S5 delete/revoke, S6 partial failure, S7 no peer online.
- [Feature Matrix](../../../docs/feature-matrix.md): 11/11 P2P, Group, File, Emoji, History, Image, Video, Sound, State, Time, Sender.

## Tests

- `AppInfoTest` — 1 pass.
- `ConnectionStateTest` — 2 passes; connection state is separate from delivery/read.
- `PeerIdentityTest` — 3 passes; stable UUID, duplicate name, Unicode, invalid names.
- `MessageMetadataTest` — 3 passes; required metadata/version/UTC and UTF-8.
- **Total: 9 tests, 0 failures/errors.**

## Boundaries and unresolved design

- P2P direct architecture fixed; TCP/JSON framed draft, no parser/network yet.
- Windows 11 target; Java 21 compiler target, runtime tested on Java 25 only.
- Media means file transfer/playback, not calls.
- History uses allowed-peer P2P event/delta proposal, **not implemented**; identity authentication, access control, group membership, revocation, retention and conflict resolution must be specified/implemented before enabling sync.
- sender UUID supplied in protocol is not proof of identity; no security claims made for M0.

**Review conclusion:** Contracts are sufficient to proceed to spec 002 (networking), conditional on future security and protocol details at the corresponding feature stages.
