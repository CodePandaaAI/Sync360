# Security Policy

Sync360 is an early local-network sharing app. It is not production-secure yet.

The current rebuild intentionally focuses on understanding local discovery and request/response before adding the final security model. Security work is planned, especially before real file transfer is treated as user-ready.

## Supported versions

There are no stable supported releases yet.

| Version | Supported |
| ------- | --------- |
| Unreleased / main | Best effort |

## Reporting a vulnerability

Please do not open a public issue for security-sensitive reports.

Send private reports to:

```text
TODO: add security contact email
```

Until a contact email is added, please contact the maintainer privately through their GitHub/LinkedIn profile once available.

## What to report privately

Please report privately if you find issues involving:

- unintended local network access
- unsafe file transfer behavior
- path traversal or unsafe file saving
- request spoofing
- missing sender validation
- denial-of-service risks
- sensitive logs
- dependency vulnerabilities that affect local networking or file handling

## What can be public

General bugs, crashes, UI issues, documentation problems, and non-sensitive architecture suggestions can be opened as normal GitHub issues.

## Current security status

Current implementation is a learning-stage prototype:

- Android NSD discovery works.
- Ktor request/response proof exists.
- Receiver Accept/Decline proof exists.
- Real file transfer is not implemented yet.
- Final authentication/session validation is not implemented yet.
- Encryption is not implemented yet.

Do not use the current code as a security model for production file transfer.

## Planned security work

Future security work may include:

- explicit sender identity validation
- session tokens
- request signing
- nonce/timestamp replay protection
- transfer token validation
- file name/path validation
- transfer size limits
- integrity checks
- optional encryption if the product requires it
