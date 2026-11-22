# 004 confirmed clarification and Demo/Live boundary review — 2026-11-22

**Scope:** Spec 004 T002. Checked user-confirmed CL-04-01…06 against [Spec](../spec.md), [Plan](../plan.md), [ROADMAP](../../../ROADMAP.md) and actual source class responsibilities. **Design consistency review only**, not the external Gate-0 security signoff.

| CL | User-confirmed requirement | Spec/Plan and source audit | Verdict |
| --- | --- | --- | --- |
| 04-01 | Direct TLS peer-to-peer **message fanout**, no content relay | Plan §4 sends per recipient on VERIFIED_READY TLS; owner only signs control. Existing M2 is P2P; there is **no Group Live** yet. | CONSISTENT / NOT IMPLEMENTED |
| 04-02 | Exactly one creator owner adds/removes/renames, members self-leave | Plan owner-signed single authority; offline owner means control unavailable, no multi-admin. Demo group authorization not trusted. | CONSISTENT / NOT IMPLEMENTED |
| 04-03 | Invitee explicit Accept, owner monotonic commit | Plan revision chain/owner signature, receiver checks. Current M2 inbound consent **not a group invitation**. | CONSISTENT / NOT IMPLEMENTED |
| 04-04 | Delivery/Read **per recipient**, app ACK vs group foreground VIEW | Plan attempt key includes recipient/message/group/session; existing M2 READ is P2P; Group new receipt required. | CONSISTENT / NOT IMPLEMENTED |
| 04-05 | **Protected group metadata persistent; chat/drafts/receipts RAM-only** | Plan uses new Windows DPAPI group store and STALE restart state, never group History. M2 stores only device identity/trust; Group store not present yet. | CONSISTENT / NOT IMPLEMENTED |
| 04-06 | Max **5 total members** incl local; unverified capacity | Plan requires up to four other verified links, matching *nominal* M2 MAX_ACTIVE=4, not a measured full-mesh guarantee. | CONSISTENT / NOT IMPLEMENTED |

**Unresolved *technical* decisions (not user clarifications):** byte-accurate profile negotiation with legacy strict CHAT_V2, ECDSA signing canonical bytes, owner ticket expiry/removal concurrency, DPAPI cross-user and resource cap performance. Independent reviewer still needed at T004/T010. Do not treat this review as approval of security.

**T002 DoD:** all 6/6 confirmed decisions accounted for; no source or tests changed, Demo and Live identities not conflated.