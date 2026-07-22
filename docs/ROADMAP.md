# Roadmap

Sync360 is an active Android-first rebuild. The current MVP can discover nearby Sync360 devices, request receiver approval, transfer text, and stream multiple files over the local network. Android is the most-tested platform; the Desktop/JVM implementation now exists and has initial Desktop-to-Android manual validation.

## Working now

- Android DNS-SD/mDNS discovery and registration through `NsdManager`.
- Desktop DNS-SD/mDNS discovery and registration through JmDNS.
- Dynamic HTTP and file-transfer ports advertised with device metadata.
- Text offer, Accept/Decline, transfer, Copy, and Clear.
- Android and Desktop multiple-file selection.
- File metadata offer before any file bytes are sent.
- One persistent raw TCP connection per accepted file batch.
- Sequential file framing, index/size validation, and per-file save acknowledgements.
- Android public Downloads writing with incomplete-entry cleanup.
- Desktop Downloads writing through temporary `.part` files and collision-safe final names.
- Best-effort sender cancellation.
- Batch-wide byte percentage on the sender and receiver.
- Shared Compose UI with compact navigation and a wider 50/50 Send/Receive scene.

## Next

### Transfer feedback and reliability

- Improve receiver-side failure details and per-file results.
- Test cancellation and failure at more points in large multi-file batches.
- Add focused protocol and storage tests.

### Discovery and lifecycle

- Repair registration automatically after network/address changes.
- Add the appropriate Android foreground/background service behavior.
- Improve Desktop LAN-interface selection for multi-adapter systems.
- Test more routers, hotspots, firewalls, VPNs, and multicast-restricted networks.
- Improve IPv4/IPv6 host selection and URL handling.

### Security

- Bind accepted offers to file connections with a transfer/session token.
- Authenticate nearby peers deliberately.
- Add cryptographic integrity verification.
- Evaluate encryption and replay protection.
- Add transfer size and resource limits.

## Later

- Retry or resume support if its protocol complexity is justified.
- Desktop packaging, update, and release workflow.
- Wider Windows, macOS, and Linux compatibility testing.
- iOS discovery, transfer, storage, and permission investigation.
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
find nearby -> approve -> send directly
```
