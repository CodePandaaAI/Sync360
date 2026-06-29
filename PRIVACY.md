# Sync360 Privacy

Last updated: June 29, 2026

Sync360 is currently an early rebuild prototype. The current Android-first milestone supports local discovery and a simple Ktor request/response proof between nearby devices. Real text transfer, file transfer, production security, and public releases are not implemented yet.

## Data Handling

- No account is required.
- No cloud service is used by the current prototype flow.
- No analytics, advertising, tracking, or telemetry is included.
- A random installation identifier is stored locally so devices can identify each other.
- Nearby-device discovery data stays on the local device while the app is running.
- The current prototype does not transfer or save user-selected files.
- The current prototype does not send user content to the developer.

## Network Security

The current prototype uses cleartext HTTP on the local network for learning and testing. Final authentication, session validation, request signing, replay protection, and encryption are not implemented yet.

Do not treat the current code as production-secure file-transfer software. Use it only on private networks you control while testing.

## Permissions

Sync360 uses network access for local discovery and request/response testing. Future versions may require additional Android permissions for reliable transfer sessions, notifications, foreground services, wake locks, Wi-Fi multicast behavior, and file access.

## Retention

The current prototype stores a local installation identifier. Discovery/request state is runtime state. Real transfer retention behavior will be documented when file transfer is implemented.

## Contact

Add a support email or website before publishing this policy.
