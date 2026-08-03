# Security Policy

Sync360 is an early local-network sharing app. It is not secure for untrusted networks yet.

The current rebuild implements local discovery, receiver-approved text sharing, and streamed file transfer before adding the final security model. Security work remains required before untrusted-network use.

## Supported versions

There are no stable supported releases yet.

| Version | Supported |
| ------- | --------- |
| Unreleased / main | Best effort |

## Reporting a vulnerability

Please do not open a public issue for security-sensitive reports.

Until a dedicated security email is added, contact the maintainer privately through the GitHub or LinkedIn profile linked in `README.md`. Do not include exploit details in a public issue.

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

Current implementation:

- Android NSD, Windows system DNS-SD, macOS/Linux JmDNS, and an initial iOS Bonjour implementation exist.
- Ktor carries text/file offers, receiver decisions, metadata, and accepted text.
- Raw TCP streams accepted file batches to platform Downloads storage.
- File names and promised sizes are validated, but a file socket is not bound to its approved offer with a session token.
- Sender authentication, encryption, replay protection, and cryptographic integrity verification are not implemented.

Use current builds only on private local networks you control. Do not use the current code as a security model for production file transfer.

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
