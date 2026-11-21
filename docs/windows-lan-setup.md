# IntraChat M1 diagnostics + M2 Live — Windows LAN Setup & Troubleshooting

**Scope:** M1 raw TCP diagnostics are **plaintext and not for chat**; M2 experimental **Live** chat separately uses TLS1.3 and human SAS pairing. Windows11 x64 / Java21. **Local two-JVM loopback tested; two distinct physical Windows LAN PCs NOT YET tested.** Do not treat M1 Accept as peer authentication.

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

## 6. M2 encrypted Live P2P chat on Windows 11 — prototype walkthrough (physical 2-PC NOT RUN)

**Never send private content using the M1 `java -jar ...` diagnostics entrypoint.** The M2 prototype has its own Live UI; peer authentication is based on a device TLS key and independently compared pairing code, not a nickname/IP/demo password.

1. Build with an actual **JDK 21**: `java -version`, `.\mvnw.cmd -o clean package`. Do not use the host JDK25 as an assertion of JDK21 runtime verification.
2. On **physical PC A**, start `java -cp target\intrachat-0.1.0-SNAPSHOT.jar ui.LiveIntraChat` and choose its **actual authorized private LAN IPv4:47321** as the bind address, then start listener **manually**. On **physical PC B**, launch the same Live entrypoint and manually enter **A's private IPv4:47321**. Do **not** enter `127.0.0.1` for another physical host.
3. A must **Accept** the inbound request (or Reject to test no-chat). On first contact the two humans visually compare the generated **same SAS on both devices** using an independent channel and **each clicks Confirm**. A matching unverified name or endpoint does not count.
4. Only after TLSv1.3 and **both PAIR_READY** should chat be enabled. Send Vietnamese/emoji in both directions; confirm distinct sender and local-rendered time. A TCP send is **not** `Đã nhận`; that requires a protected application ACK.
5. `Đã xem` requires recipient's Live conversation to be **foreground, selected, and actually show the message bubble in the viewport ≥250ms**. Test preview, background and offscreen: no read receipt. Read denotes app display, **not proof the human read it**.
6. Disconnect and reconnect **manually**; even a pinned known peer still requires inbound **Accept** on each connection. Messages/drafts/receipts are RAM-only; close/restart clears them but keeps **Windows CurrentUser DPAPI identity/trust pins**. Key replacement must fail closed and require deliberate revoke/re-pair.
7. If Windows Firewall blocks A's port, request authorized **Private profile TCP-only** inbound allowance scoped to approved LAN hosts/subnet. Never create a broad **Public** profile rule automatically. Check adapter/VLAN/AP client isolation, and undo test firewall rules when finished.
8. To test without the **public Internet**, disconnect Internet upstream or use an authorized isolated LAN while maintaining PC-to-PC routing. Do not disable unrelated shared corporate networks. Record sanitized evidence on **both** PCs: Windows build, actual JDK21, Git commit, private IPv4 (mask if needed), SAS match observation, approvals, TLS, chat/ACK/viewport READ, rejection/downgrade cases and disconnected Internet state.

**Local separate-profile smoke only (not two-PC proof):** two distinct secure roots prevent accidental sharing of the same local private key:
```powershell
java '-Dintrachat.secureRoot=C:\Temp\intrachat-live-A' -cp target\intrachat-0.1.0-SNAPSHOT.jar ui.LiveIntraChat
java '-Dintrachat.secureRoot=C:\Temp\intrachat-live-B' -cp target\intrachat-0.1.0-SNAPSHOT.jar ui.LiveIntraChat
```
These two processes use **one OS user/computer**, so they cannot satisfy the physical 2-PC requirement. Use a writable private test directory and delete stale synthetic credentials through the approved procedure when done. The normal secure directory is `%LOCALAPPDATA%\IntraChat\security\`.

**Known gaps (T079 documentation does not close them):** common-port v1/v2 dispatch, comprehensive red-team/TLS pin/DPAPI ACL review, 4-active/4-pending stress, accurate uncertain retry and real two-computer LAN acceptance all remain not verified. See [003 Quickstart](../specs/003-p2p-messaging/quickstart.md) and [threat model](../specs/003-p2p-messaging/evidence/threat-model.md).

## 7. Unverified & release gate

- **NOT RUN:** Two physical Windows 11 PCs with LAN still available and Internet removed, including both directions and Windows Private-profile Firewall acceptance. Do not mark AC-02-A/I or M1 Verified from loopback tests.
- **For the M1 diagnostic path only:** authentication, TLS, secure pairing, user chat, History/File/Group/Media remain out of scope. **M2 Live is a separate TLS chat prototype** and is not made Verified by M1 diagnostics or local loopback.
- Java 21 local build/loopback is verified on one Windows machine; a second computer may have different firewall/network policies.
