# Sync360 Project Context

Sync360 is an Android-first Kotlin Multiplatform / Compose Multiplatform app for nearby sharing over a local network.

```text
open app -> discover nearby device -> choose content -> receiver approves -> send directly
```

The old AI-generated sync implementation was removed. The current app is being rebuilt manually so its maintainer can understand and own the complete discovery, request, transfer, and storage path.

## Current implementation

- Shared Compose Send/Receive UI, ViewModels, controllers, state, and Navigation 3.
- Compact single-pane navigation and a 50/50 Send/Receive scene on wider windows.
- Android discovery/registration through `NsdManager`.
- Desktop discovery/registration through JmDNS.
- Ktor HTTP offers, receiver decisions, metadata, and text payloads.
- Raw TCP streaming for file bytes.
- Multiple files sent sequentially over one accepted-batch connection.
- Android file access through `ContentResolver` and Downloads writing through `MediaStore`.
- Desktop native file selection, Java file streams, and safe Downloads writing through temporary `.part` files.
- Best-effort cancellation and batch-wide byte percentage.

Android-to-Android text and multiple-file flows have manual validation. The Desktop/JVM implementation is present, and Desktop-to-Android transfer has initial manual validation. The app is still development software, not a production-ready release.

## Architecture rule

```text
Compose screen -> ViewModel -> controller/service -> common contract -> platform implementation
```

- UI renders state and calls ViewModel actions.
- ViewModels own UI-facing operations and state.
- Controllers coordinate network and transfer services.
- Ktor DTOs remain at the HTTP boundary.
- Blocking file/socket work runs on `Dispatchers.IO`.
- Files are streamed; they are never loaded whole into memory.
- Platform APIs stay in Android/JVM source sets.

## Protocol summary

Ktor HTTP is the control plane:

```text
POST /sync360/text/offer
POST /sync360/text/transfer
POST /sync360/file/offer
```

Raw TCP is the file data plane:

```text
one connection per accepted batch
  -> repeat for each file:
       -> file index
       -> promised byte count
       -> exact file bytes
  -> sender flushes once
  -> receiver returns final success and completed-file count
```

Current shared transfer constants use a 512 KiB payload buffer, 5-second connect timeout, 60-second connected-socket timeout, and 10-second wait for the first file connection after acceptance.

## Current priorities

- Better receiver-side errors and per-file results.
- Android registration repair after network/address changes.
- Foreground/background lifecycle support.
- Broader Desktop adapter, firewall, router, and operating-system validation.
- Session validation, authentication, encryption, and integrity verification.

## Important limitations

Sync360 currently uses cleartext local HTTP and raw TCP. It has receiver approval but no authentication, encryption, transfer token, or checksum. Use development builds only on private networks you control.

For detailed and current information, read:

- [`README.md`](README.md)
- [`docs/ARCHITECTURE.md`](docs/ARCHITECTURE.md)
- [`docs/DEVELOPMENT.md`](docs/DEVELOPMENT.md)
- [`docs/ROADMAP.md`](docs/ROADMAP.md)
- [`SECURITY.md`](SECURITY.md)

Source code wins if documentation and implementation ever conflict.
