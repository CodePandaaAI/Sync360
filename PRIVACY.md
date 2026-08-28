# Sync360 Privacy

Last updated: August 28, 2026

Sync360 sends text and files directly between nearby devices on the same reachable local network. It does not use a Sync360 account, cloud-storage service, analytics service, advertising service, or Sync360 transfer backend.

## Data Handling

- No account is required.
- No cloud service is used for discovery or transfer.
- No analytics, advertising, tracking, or telemetry is included.
- A random installation identifier is stored locally so devices can identify each other.
- Nearby-device discovery information is exchanged only with devices on the reachable local network and is kept as runtime state.
- Text is sent directly to the chosen receiver. Files are sent after the sender enters the receiver's temporary four-digit code.
- Received files remain on the receiving device in its platform Downloads location.
- Shared text and transfer state are temporary runtime state; Sync360 does not maintain chat or clipboard history.
- Sync360 does not send shared content to the developer.

## Network Security

Sync360 currently uses cleartext local HTTP for offers and text and raw TCP for file bytes. Sender authentication, session validation, request signing, replay protection, encryption, and cryptographic integrity verification are not implemented yet. The temporary four-digit file receive code is a convenience check, not authentication, and is transmitted over cleartext HTTP.

Do not treat the current code as production-secure file-transfer software. Use it only on private networks you control while testing.

## Permissions

Sync360 uses network access for local discovery and direct transfer. Android uses system file pickers and `MediaStore` for selected and received files. iOS source declares local-network and Bonjour usage and exposes its app Documents directory through Files. Future lifecycle work may require notification, foreground-service, wake-lock, or other platform permissions.

## Retention

Sync360 stores a local installation identifier. The file receive code, discovery, offer, text, and transfer state are runtime state. The receive code is not persisted and a fresh application session generates another one. Files successfully received remain in Downloads until the user removes them through the operating system. Incomplete current files are removed after receive failure or cancellation where the platform implementation supports it.

## Contact

For privacy questions, contact the maintainer through the GitHub profile linked in `README.md`. Report security-sensitive findings through the private-contact guidance in `SECURITY.md`.
