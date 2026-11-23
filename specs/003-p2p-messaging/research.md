# Research & ADR Evidence — M2 Secure P2P Chat (PLANNED)

**Status:** Research for plan, not tested software or certified security.
**Date:** 2026-11-21
**Binding product decisions:** [CL-03-01…05](spec.md) · [plan](plan.md).

## R-01. TLS 1.3 vs custom encrypted transport

**Chosen:** Java 21 JSSE TLSv1.3 with mandatory mutual X.509 device certificates on the CHAT_V2 socket. Keep existing M1 raw TCP v1 diagnostic-only; a bounded CHAT_V2 preface and manual inbound acceptance precede TLS. Every protected application message uses v2 allowlist inside TLS.

**Alternatives:** Implement our own ECDH/AES-GCM handshake (high security design risk), enable CHAT_TEXT on M1 plaintext (not allowed), mandatory central/cloud auth (breaks LAN-only), opportunistic STARTTLS with fallback (downgrade risk).

**Rationale:** Established JDK JSSE offers TLSv1.3 encryption/authenticated key exchange; adding explicit certificate identity and human pinning is still required. Mutual certificate possession alone does NOT verify the real person. [Java 21 JSSE reference](https://docs.oracle.com/en/java/javase/21/security/java-secure-socket-extension-jsse-reference-guide.html).

**Security gate:** A custom trust manager for unknown self-signed certificate candidates is sensitive: never unconditional trust-all for CHAT_TEXT. It may accept a **bounded, non-authorized first-contact candidate** for TLS cryptographic key proof and certificate inspection, then block all application data until both humans independently compare SAS and confirm; validate certificate signature/algorithms/validity/usage and peer ID↔SPKI pin. Independently review implementation and test MITM and downgrade. Resumed TLS sessions must still satisfy explicit identity pin and application consent. No TLS session is allowed to send user content before pair readiness.

## R-02. First-pair fingerprint and code

**Chosen:** Per-device ECDSA P-256 keypair and self-signed X.509 certificate, public key pin is SHA-256 DER SPKI fingerprint. Human-friendly same 64-bit SAS displayed by both peers: SHA-256 canonical, domain-separated transcript covering sorted pairs (device UUID, key hash, corresponding fresh nonce), then truncate 64 bits and group 4-hex characters. Show full 256-bit fingerprints in details. Both users compare via independent channel (in-person or trusted voice) and press Confirm separately.

**Alternatives:** Accept claimed displayName/UUID/IP (spoofable), TOFU with no human comparison (MITM vulnerable), remote server directory/CA (unwanted Internet requirement), certificate fingerprint of **remote only** shown on each side (the two peers see different strings, confusing for direct code comparison).

**Rationale:** A common SAS from both authenticated public keys and session/identity transcript provides a human-comparable first-contact check without requiring central infrastructure. 64-bit code is a usability/security trade-off, not proof of verified real-world identity. A new/different public key triggers re-pair. The exact binary transcript must have canonical serialization and test vectors so both peers derive identical strings.

**Uncertainty requiring proof:** The SAS confirmation flow under simultaneous cancellation, cross-connect, key rotation and partial durable writes requires implementation review, not a marketing security claim.

## R-03. Certificate generation

**Chosen:** Java JCA generates P-256 keypair; Bouncy Castle certificate APIs produce a valid short-lived self-signed X.509 leaf certificate for JSSE; TLS engine remains JDK JSSE, not Bouncy Castle TLS. Suggested cert lifetime one year with explicit expiry handling. Keep SPKI pin stable when renewing certificate on the same key only if validity/key usage are rechecked. New key always requires re-pair.

**Dependency proposal:** org.bouncycastle:bcpkix-jdk18on:1.87 and matching bcprov-jdk18on:1.87, plus required transitive bcutil family. Public Maven Central lists 1.87 in Nov 2026. [Maven artifact versions](https://central.sonatype.com/artifact/org.bouncycastle/bcpkix-jdk18on/versions) · [upstream download/release information](https://www.bouncycastle.org/download/bouncy-castle-java/).

**Rejected:** Internal sun.security.x509 APIs (strongly encapsulated; nonportable), silently launching keytool as a runtime subprocess (JRE embedding, process/error complexity), hand-written ASN.1 certificate writer.

**Security gate:** Confirm artifact signatures/license, vulnerability advisories, JDK21 compatibility, SHA256withECDSA support, cert key usage and DER encoding in tests. **No code/dependency has been added yet**.

## R-04. Windows identity at rest and trust persistence

**Chosen:** Windows CurrentUser DPAPI via JNA-platform (proposal 5.19.1) wraps the PKCS12 private-key material. Local user-only ACL under %LOCALAPPDATA%/IntraChat/security, atomic credential/trust writes, explicit user reset and revoke; never log/raw-dump keys or SAS. Pairing pin metadata contains remote UUID/SPKI and trust state, **never message text**. Validate other-Windows-user denial and store corruption.

**Dependency proposal:** net.java.dev.jna:jna-platform:5.19.1 (plus jna transitive). Maven Central lists 5.19.1. [Maven artifact versions](https://central.sonatype.com/artifact/net.java.dev.jna/jna-platform/versions).

**Alternatives:** plaintext PKCS12 with fixed password (unsafe), Java Preferences as secret vault (not confidentiality), cloud secrets vault (breaks offline LAN), Windows-MY only with brittle key creation APIs (more complex to implement in this Swing app). JNA adds native boundary/OS-specific risk, but project is explicitly Windows-first.

**Security limitations:** Same-Windows-user malware, memory dumps, compromised login and OS backup access are not solved by DPAPI. No silent insecure fallback if DPAPI fails. Prompt user for explicit reset/re-pair if key cannot be read. No cryptographic claim before Windows runtime tests.

## R-05. Separate v1 and v2 routing

**Chosen:** Existing v1 strict diagnostic parser retains its original whitelist. New inbound dispatcher consumes a bounded first preface, routes v1 diagnostic vs CHAT_V2 candidate, requires per-inbound human approval, and upgrades CHAT_V2 socket to TLS on a worker. Unknown/mismatch closes. Only dedicated v2 protected JSON codec understands CHAT_TEXT, DELIVERY_ACK, READ_RECEIPT.

**Alternative:** Change the existing v1 JsonEnvelopeCodec allowed enum set to include chat. **Rejected:** sending user content on unauthenticated/unprotected M1 would violate confirmed scope and Constitution IV.

**Edge to prove:** Existing M1 NetworkCoordinator currently itself reads first HELLO. The dispatcher refactor must preserve M1 connect/listen/Reject behavior, bounded pending slots, v1 diagnostics and existing 45 baseline tests. Two simultaneously connecting peers and cross-version failure must be predictable.

## R-06. Distinct application ACK and read receipt

**Chosen:** After unique message validates/enters recipient RAM app model, send DELIVERY_ACK. Only a real foreground visible bubble in selected FE Live conversation (viewport intersection, ≥250ms stable visibility) triggers READ_RECEIPT; both protected and correlated by verified peer/session/conversation/message. Never read from background/notification/preview/Demo.

**Alternative:** Treat ACK or opening conversation as read — **rejected:** would falsely display viewed state and violate CL-03-03. No proof of human eye movement is attempted, and UI should explain this limitation.

**UX risk:** Passive view receipts reveal when a chat is displayed on recipient device. Live UI must disclose this, with no unrelated user-content logging. Lost receipt when offline is **not queued** for later reconnect per CL-03-02/04.

**Out-of-order:** Authenticated READ can be processed before late ACK and is monotonic; wrong/duplicate session records ignored. Critical tests include same displayName and stale session/receipt.

## R-07. FE integration and RAM lifecycle

**Chosen:** Existing UI has listener.ChatActionHandler, ChatClientListener, model.ChatMessage, model.DeliveryState (SENT/DELIVERED/SEEN), MainChatFrame, scrolling HistoryPanel/JScrollPane and MockChatService that simulates ACK/read on scheduled timers. Implement LiveChatAdapter with distinct Live mode/service; retain MockChatService only for Demo. Add Live status model for SENDING/FAILED/UNCERTAIN and explicit view event port. Use RAM-only timelines and drafts, not FE persisted History semantics.

**Rejected:** Fake real status via MockChatService, treating demo login/password as peer authentication, rebuilding all UI in the diagnostics NetworkPanel, enabling Group/Media before their specs.

**Known conflict:** FE MessageContent limits 8,000 Java chars (UTF-16), while plan proposes 8,000 Unicode code points and 32 KiB UTF-8. The adapter/schema must align these limits deliberately, test emojis with supplementary code points and never truncate.

## R-08. Questions closed vs implementation-proof obligations

**Closed by user (do not re-clarify):** CL-03-01 first-pair fingerprint + remember, CL-03-02 manual resend/offline draft, CL-03-03 actual read receipt, CL-03-04 RAM-only messages, CL-03-05 Messenger FE Live + Demo separate.

**Technical choices proposed in plan (review before implementation):** JSSE TLS, BC certificate creation, Windows DPAPI/JNA, SAS transcript encoding, cert validity and read view debounce. These are **design choices**, not implemented or security verified. If an artifact is unavailable/vulnerable or Windows APIs are incompatible, record a new ADR and re-review the security gate rather than silently weakening authentication.

**M1 dependency:** Two-physical-PC offline-LAN test remains missing; localhost two processes do not close M1 Gate 1 nor M2 AC-03-J. No M2 test evidence exists.
