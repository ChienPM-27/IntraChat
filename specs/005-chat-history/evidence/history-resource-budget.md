# T007 — Shared M2/Group/History resource budget and measurement plan

**Observed source:** `NetworkConfig` declares `MAX_ACTIVE_SESSIONS=4`, `MAX_OUTBOUND_FRAMES=16`, `MAX_QUEUED_BYTES=2*1024*1024`, `CONNECT_TIMEOUT_MS=5000`, `APPROVAL_TIMEOUT_MS=30000`, `ACTIVE_READ_TIMEOUT_MS=15000`. The secure frame cap 65,536 bytes is described in M2/005 design. **Declared constants are not complete measurement/verification of aggregate runtime use.**

| Budget | Type | Expected engineering test |
| --- | --- | --- |
| 90 days and 500 MiB local History | **User-confirmed CL-05-03 default**; user-adjustable | Age boundary, total encrypted DB quota, warnings, compaction/tombstone, disk full |
| 4 active M2 sessions incl owner+4 Group members | Existing source cap/004 design | 4 valid and 5th denied, no new History sockets |
| 16 outbound frames/2MiB queued per peer | Existing declared shared queue cap | Slow peer, fair P2P/Group/History, priority and overflow |
| ≤65,536 bytes framing incl secure M2 envelope | Existing M2 contract | 65,535/65,536/65,537 malicious frame tests |
| History ≤20 events + ≤48 KiB encoded delta | **Unmeasured design target** | Emoji worst-case, UTF-8, repeated large events, bytes cap before allocation |
| ≤256 manifest IDs | **Unmeasured target** | 256/257/10k windows, opaque scoped cursor |
| 50 events per UI page | **Unmeasured target** | 1/50/51/10k, stable keyset cursor, p95 EDT <200ms target |
| Additional History decrypted RAM ≤4MiB | **Unmeasured target** | 2 syncs, many pages, malformed/flood input, heap peak/GC |
| One per conversation; two parallel History syncs | **Unmeasured target** | Concurrent P2P/Group/timeout/cancel |
| 10s OPEN, 20s idle, 120s overall sync | **Unmeasured design target** | Half-open/link loss/timeout/cancel/cleanup |

**Implementation ownership:** one shared M2 session registry and quota; History message writers must yield to live chat and 004 Group owner relay. No double-reading TLS. One bounded encrypted SQLite writer per profile, zero disk/net/crypto on Swing EDT. All tests need exact machine/commit/JDK and measured counters.

**T007 Done:** budget and measurement strategy documented. **No stress/performance PASS asserted.**
