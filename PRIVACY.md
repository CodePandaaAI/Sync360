# Sync360 Privacy

Last updated: June 15, 2026

Sync360 transfers user-selected text and files directly between approved devices on the same local network.

## Data Handling

- No account is required.
- No cloud service is used for transfers.
- No analytics, advertising, tracking, or telemetry is included.
- Device approvals, session tokens, and shared text remain in memory for the current app session.
- A random installation identifier is stored locally so devices can identify each other.
- Received files are saved only on the receiving device.
- Sync360 does not intentionally collect or upload user content to the developer.

## Network Security

Current transfers use authenticated HTTP on the local network. Session approval, random session tokens, HMAC signatures, timestamps, and nonce replay checks reject unauthorized or replayed requests.

Transfer content is not encrypted by Sync360. Anyone with sufficient access to the same network may be able to observe transferred content. Use Sync360 only on a private home network or personal hotspot controlled by you. Do not use it on public or shared networks.

## Permissions

Sync360 uses network permissions for local device discovery and direct transfer. Android may use notification, foreground-service, wake-lock, and Wi-Fi multicast permissions to keep an active session or file transfer reliable.

## Retention

Session approvals, tokens, and shared text are cleared when the runtime ends. Received files remain on the receiving device until the user deletes them.

## Contact

Add a support email or website before publishing this policy.
