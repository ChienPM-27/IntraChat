# IntraChat M1 — Windows LAN Setup & Troubleshooting

**Scope:** Only direct P2P TCP diagnostics; **no peer authentication, no encryption, no user Chat/History/File/Group**. Applies to Windows 11 x64 and Java 21. **Loopback tested**; **two physical Windows PCs NOT YET tested**.

## 1. Start the app

With Temurin JDK 21 and Maven Wrapper 3.9.16 from the project root:

```powershell
.\mvnw.cmd clean package
java -jar target\intrachat-0.1.0-SNAPSHOT.jar
```

Do not use this plaintext diagnostic channel for private data, even if you know the IP/device display name. A popup asking you to accept is **not** a security/authentication check.

## 2. Local IP, binding, and port

- On each computer open PowerShell and run `ipconfig`. Identify the **IPv4 Address** of the real Ethernet/Wi-Fi LAN adapter used between machines. Do not use `127.0.0.1` for two physical PCs (loopback addresses only the current PC), do not use a public/untrusted interface.
- In section **1. Máy của bạn**, type that exact local IPv4; port defaults to **47321/TCP**, editable 1–65535. Press **Bắt đầu lắng nghe**. The app does **not** start a listener or open a firewall by itself. The listener binds only the selected local IPv4.
- Section **2. Kết nối trực tiếp**, type the *other machine's* IPv4 and its **listening port**, then press **Kết nối**. There is **no auto-discovery**.
- The receiving PC sees an explicit popup showing actual observed incoming TCP endpoint and the sender's **unverified, self-declared** name/UUID. It must click **Chấp nhận**. **Từ chối**, closing the popup, or 30-second timeout denies the session.
- After compatible protocol v1 HELLO/ACCEPT/CONFIRM/READY and approval, both PCs display **connected for diagnostics only**. Use **Ping kiểm tra** from either side. The **PONG** result is an engineering connectivity probe, not a read receipt or authenticated proof.
- Disconnect with **Ngắt kết nối**; if a link drops, press **Kết nối lại** yourself. The app **never** reconnects automatically.

## 3. Windows Firewall / network policy

- **Never automatically change Windows Firewall** or weaken a Public profile. Confirm the LAN is a permitted **Private** or appropriately managed domain network. If this is a school/office domain, follow local IT policy.
- If incoming TCP is blocked, an authorized administrator can allow inbound **TCP port 47321** or your chosen port on the **Private profile only**, limited to trusted local subnet/hosts when appropriate. Example for an authorized administrator to review (do **not** run blindly):

```powershell
# Administrator-only example; requires approval and adaptation to LAN policy:
New-NetFirewallRule -DisplayName "IntraChat M1 Diagnostic (Private)" `
  -Direction Inbound -Action Allow -Protocol TCP -LocalPort 47321 -Profile Private
# Undo after testing:
Remove-NetFirewallRule -DisplayName "IntraChat M1 Diagnostic (Private)"
```

- Firewall rule permission and selected adapter do not guarantee reachability. Verify client isolation on Wi-Fi AP, guest VLAN segmentation, IP/subnet reachability and endpoint antivirus. The app does **not** traverse NAT/the Internet, use relays, multicast or broadcast.
- Safe reachability checks (not proof of IntraChat handshake):
  ```powershell
  Test-NetConnection -ComputerName 192.168.1.50 -Port 47321
  ```
  Substitute a **real authorized peer LAN IPv4**; don't scan random hosts. A failed check can indicate firewall, wrong IP/port, blocked routing, or peer not listening.

## 4. Two-process local loopback (tested on this Windows machine)

Two GUI processes can run under different device IDs by assigning separate local profile locations. This is useful for testing without a second computer:

```powershell
java -Dintrachat.profile="$env:TEMP\intrachat-A.properties" -jar target\intrachat-0.1.0-SNAPSHOT.jar
java -Dintrachat.profile="$env:TEMP\intrachat-B.properties" -jar target\intrachat-0.1.0-SNAPSHOT.jar
```

These are **Java system properties**, not passwords. In each process use loopback **127.0.0.1**, but only A should listen on 47321 (B may listen on a different port such as 47322 or not listen). B connects to A's `127.0.0.1:47321`; A explicitly Accepts. Both may Ping after approved handshake. A second process with the *same profile file* will claim the same UUID and be rejected operationally; choose unique paths.

**Actual controlled GUI test 2026-11-21:** two independent Java 21 JAR processes, A listened, B connected, A's real popup displayed/accepted, synthetic PING/PONG returned in both directions (B observed ~6 ms, A ~7 ms) and windows closed normally. [Evidence](../specs/002-lan-networking/evidence/us2-approval-handshake.md).

## 5. Troubleshooting

| UI symptom | Check / action |
| --- | --- |
| `Không thể mở cổng` / `BIND_FAILED` | Port already used or selected IPv4 not present; stop other listener, choose another port. |
| `CONNECT_REFUSED` | Peer not listening at target IP/port. Check section 1 on B. |
| `CONNECT_TIMEOUT` | Wrong IP/interface, firewall, LAN isolation, unreachable subnet; check authorized network. |
| `Đang chờ người nhận` | Wait for a **person** on the receiving PC to Accept or Reject. Pending expires after ~30s. |
| `USER_REJECTED` | Receiving person pressed Reject or prompt expired/closed; don't treat peer as authenticated. |
| `VERSION_UNSUPPORTED` | Run compatible IntraChat protocol versions; don't ignore handshake mismatch. |
| `FRAME_INVALID_LENGTH` or malformed protocol | Incoming peer data rejected; no private data should be sent at M1. |
| `Đã ngắt kết nối` | Link closed. Retry only by clicking `Kết nối lại`. |
| Unknown UUID | Device ID is **self-declared**; no cryptographic authenticity. Do not send private content. |

## 6. Unverified & release gate

- **NOT RUN:** Two physical Windows 11 PCs with LAN still available and Internet removed, including both directions and Windows Private-profile Firewall acceptance. Do not mark AC-02-A/I or M1 Verified from loopback tests.
- Authentication, TLS, secure pairing, History ACL, user chat, File/Group/Media remain **out of scope**.
- Java 21 local build/loopback is verified on one Windows machine; a second computer may have different firewall/network policies.
