# Roadmap

Sync360 is an active Android-first rebuild. The current MVP can discover nearby Sync360 devices, deliver text directly to an idle receiver, and admit file batches through a temporary four-digit receive code before streaming them over the local network. Android is the most-tested platform. Desktop-to-Android transfer has initial manual validation, and one Windows 11 Ethernet test confirmed prompt discovery and removal in both directions when the corresponding app opened or closed.

## Working now

- Android DNS-SD/mDNS discovery and registration through `NsdManager`.
- Windows DNS-SD/mDNS discovery and registration through the operating system `dnsapi.dll` API on all interfaces.
- Current macOS/Linux DNS-SD/mDNS discovery and registration through JmDNS on eligible IPv4 and IPv6 LAN addresses.
- Application-lifetime network startup with separate discovery and registration lifecycle states.
- A 60-second discovery window derived from the platform-reported running state.
- Manual discovery Reload while registration remains active, plus full connection repair when both lifecycle states are stable.
- Dynamic HTTP and file-transfer ports advertised with device metadata.
- One-request text delivery with sender name, a 100,000-character limit, Copy, and Clear.
- Android and Desktop multiple-file selection.
- A temporary four-digit file receive code generated for each fresh application session.
- The same receive code shown on both Send and Receive from one application-session source of truth.
- Immediate code and metadata checking before any file bytes are sent.
- One persistent raw TCP connection per accepted file batch.
- Sequential file framing, index/size validation, and one final success/completed-count result per batch.
- Android public Downloads writing with incomplete-entry cleanup.
- Desktop Downloads writing through temporary `.part` files and collision-safe final names.
- Operation-scoped sender cancellation that explicitly clears the matching active receiver transfer, with timeout fallbacks for lost communication.
- Batch-wide byte percentage on the sender and receiver.
- Shared Compose UI with compact navigation and a wider 50/50 Send/Receive scene.
- Enabled iOS device and Apple-silicon Simulator targets with initial Bonjour, selection, clipboard, storage, and TCP transfer implementations.

## Next

### Transfer feedback and reliability

- Improve receiver-side failure details and per-file results.
- Test cancellation and failure at more points in large multi-file batches.
- Validate correct, incorrect, busy, cancelled, and missing-TCP-sender code flows.
- Add focused protocol and storage tests.

### Discovery and lifecycle

- Detect network/address changes and repair registration automatically.
- Add the appropriate Android foreground/background service behavior.
- Add Android 17 `ACCESS_LOCAL_NETWORK` declaration, runtime request, denial handling, and permission-aware network startup.
- Queue Android 13 legacy NSD resolves and retry already-active failures.
- Replace the remaining macOS/Linux JmDNS fallback with Bonjour and Avahi after the Windows-native path is validated.
- Validate Desktop LAN-interface selection on more multi-adapter systems.
- Add clear Windows Firewall onboarding and decide whether packaging should install an inbound application rule.
- Retire Windows native callback arenas after a safe lifetime and preserve per-interface results when only one interface reports service removal.
- Test more routers, hotspots, firewalls, VPNs, and multicast-restricted networks.
- Improve IPv4/IPv6 host preference and scoped-address URL handling.

### Security

- Replace correctness-only operation IDs with authenticated session credentials.
- Authenticate nearby peers deliberately.
- Add cryptographic integrity verification.
- Evaluate encryption and replay protection.
- Add transfer size and resource limits.

## Later

- Retry or resume support if its protocol complexity is justified.
- Desktop packaging, update, and release workflow.
- Wider Windows, macOS, and Linux compatibility testing.
- iOS physical-device discovery, transfer, cancellation, storage, permission, signing, and distribution validation.
- Better onboarding and local-network troubleshooting UI.

## Not planned right now

- Cloud sync or cloud storage.
- Accounts.
- Permanent chat history.
- A database-backed device ledger.
- WebRTC or internet/NAT traversal.
- Background clipboard scraping.

The product direction remains focused:

```text
find nearby -> send text or enter a file receive code -> transfer directly
```
