# M1 prerequisite gate for M2

**Checked:** 2026-11-21 · **Status:** BLOCKED for M2 release.

- M0: **Verified (41/41 tasks)** from earlier JDK21 and offline GUI acceptance.
- M1: **In Progress (36/60 tasks)** as recorded in `specs/002-lan-networking/tasks.md`.
- M1: local JDK21 diagnostic protocol, two independently launched GUI JVMs on one Windows machine tested, but **real two physical Windows PCs in a LAN without public Internet NOT RUN**.
- M1 missing full resource/heartbeat/peer fault review and Constitution Gate 1 signoff. **Do not infer M1 Verified** just because M2 local secure prototype exchanges text.
- M2 software implementation may be developed in test branch, but M2 release **cannot be marked Verified** until M1 Gate 1 is explicitly closed from authentic two-PC evidence.
- M2 data/user-content stays separated from M1 v1: `JsonEnvelopeCodec` still rejects v1 CHAT_TEXT; v2 uses separate TLS stack.
