# US3 — Sender ID and UTC/local timezone evidence, 2026-11-22

**Code:** `src/main/java/vn/intrachat/application/ChatViewMapper.java`; integrated into `listener/LiveChatAdapter.map`. Immutable sender and destination are bound to verified TLS device UUIDs; mutable display alias, UTF-8 emoji and system timezone affect presentation only.

**Tests (Java 21, offline):** `MessageMetadataPresentationTest` **3/3 PASS** after RED missing mapper; `SecureChatLoopbackTest` **1/1 PASS** uses two actual Java 21 OS processes and real mutual TLS to validate both directions' sender, conversation, UTF-8 and timestamps.

- The same `Instant.parse("2026-11-22T06:45:30Z")` renders as **13:45:30** on Asia/Ho_Chi_Minh and **01:45:30** in America/New_York, while stored/display-mapped UTC Instant is unchanged.
- Changing the untrusted alias from `Alice` to `Bob` does NOT change verified sender UUID or direction of incoming/outgoing message.
- Unexpected sender/destination UUID is rejected by the presentation guard; remote clock skew exceeding five minutes generates a warning, not a causal ordering/trust proof.
- No private user content, SAS codes, keys or persisted chat history is written by this test.

**NOT VERIFIED:** separate physical Windows peers, real GUI foreground viewport READ/SAS acceptance, human observation of timezone UI in a release build, independent security signoff. [Full evidence](m2-local-code-2026-11-22.md).