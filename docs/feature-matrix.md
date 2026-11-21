# IntraChat — Feature Responsibility Matrix

**Scope:** This is a coverage map, **not** a feature-completion report.

| Capability | Ownership/layers | Planned feature | Important boundary |
| --- | --- | --- | --- |
| P2P | transport, protocol, application, UI | 002/003 | Peer-to-peer directly in LAN, not cloud relay |
| Group | domain, group service, membership, protocol | 004 | Fanout & group permission definition required |
| File | transfer service, streaming transport, UI | 006 | Never trust remote filenames or path |
| Emoji | Unicode text model & UI rendering | 003 | Preserve UTF-8, allow complex emoji sequences |
| History | local repository, sync service, conversation ACL | 005 | Sync authorized peers only; eventual consistency |
| Image | attachments, transfer, image preview | 007 | Size/format validation, fallback for unsupported |
| Video | attachments, file transfer, external player fallback | 007 | No realtime video call |
| Sound | attachments, file transfer, media player fallback | 007 | No realtime voice call |
| State | peer session, connection state, delivery ack | 002/003 | Presence ≠ sent/delivered/read |
| Time | UTC event metadata, locale rendering | 003/005 | No total order by remote device wall clock |
| Sender | stable peer/device ID and display label | 003 | Display name ≠ authenticated identity |

M0 includes only **application shell + typed metadata foundations**; all networking, group, history and media behavior above remains planned.

## Clarification coverage

- CL-01: P2P without obligatory central server.
- CL-02: Windows 11 x64 primary.
- CL-03: Video & Sound are media files, not live calls.
- CL-04: Multi-device history delta sync after reconnection; **not** full replication to every nearby peer.
