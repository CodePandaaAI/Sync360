# Architecture

Sync360 is an Android-first Kotlin Multiplatform app for direct nearby sharing over a local network. Android and Desktop reuse the common UI and transfer flow; platform source sets implement discovery, file access, storage, identity, clipboard, and raw socket I/O.

The architecture intentionally follows one readable path:

```text
Compose screen -> ViewModel -> controller/service -> common contract -> platform implementation
```

## Current end-to-end flow

```text
app starts
  -> Koin creates common services and platform implementations
  -> SendScreenViewModel starts NetworkServicesController
  -> FileTransferReceiver opens an OS-assigned TCP port
  -> Sync360HttpServer opens an OS-assigned HTTP port
  -> NetworkServices advertises both ports through DNS-SD/mDNS
  -> nearby Sync360 devices are resolved into NearbyDevice
  -> sender posts a text or file offer through Ktor HTTP
  -> receiver accepts or declines
  -> accepted text continues through HTTP
  -> accepted file bytes stream through one raw TCP connection
  -> platform DownloadsWriter saves the files
```

## Shared and platform code

```text
androidApp/
  Android application host and entry point

desktopApp/
  Compose Desktop entry point and native packaging configuration

shared/src/commonMain/
  shared Compose UI and adaptive Navigation 3 layout
  ViewModels and presentation state
  controllers and domain models
  Ktor HTTP client/server and DTOs
  discovery, transfer, storage, clipboard, and identity contracts

shared/src/androidMain/
  Android NsdManager discovery/registration
  ContentResolver file access and MediaStore Downloads writing
  Android clipboard, identity, device info, TCP sender/receiver, and DI

shared/src/jvmMain/
  JmDNS discovery/registration
  AWT file selection and clipboard
  Java file/Downloads handling, identity, device info, TCP sender/receiver, and DI
```

## Main responsibilities

### `Sync360Root`

Owns the single app `Scaffold`, compact bottom navigation, and one Navigation 3 `NavDisplay`. A small `TwoPaneSceneStrategy` renders Send and Receive in a fixed 50/50 split when the Material adaptive window size reaches the medium-width breakpoint. Compact windows use Navigation 3's normal single-pane fallback.

### ViewModels

- `SendScreenViewModel` owns nearby-device state, selected files/text, send operations, results, and cancellation.
- `ReceiveScreenViewModel` maps incoming server state to Receive UI and handles Accept, Decline, Copy, Clear, and Open Downloads actions.
- `NavigationViewModel` keeps Send and Receive available as top-level entries and selects the active compact destination.

ViewModels launch UI-facing work. They do not implement platform APIs or socket protocols.

### Controllers

- `NetworkServicesController` starts the file receiver, HTTP server, and discovery/registration in the required order.
- `OutgoingRequestsController` creates offers, calls the Ktor client, and starts accepted file transfers.
- `IncomingServerRequestsController` exposes incoming offers and receiver decisions to the HTTP server and Receive UI.

### Discovery

`NetworkServices` is the common contract.

- Android uses `NsdManager` with `_sync360._tcp.`.
- Desktop uses JmDNS with `_sync360._tcp.local.`.

Both advertise a stable device UUID, device name/type, protocol version, dynamic HTTP port, and dynamic file-transfer port. A device filters its own UUID from discovery results.

The current Desktop implementation selects the first active, non-loopback, non-virtual, site-local IPv4 interface. Machines with VPN, WSL, Docker, virtual-machine, Ethernet, and Wi-Fi adapters still need broader validation.

## Control plane: Ktor HTTP

Ktor carries offers, decisions, metadata, and text:

```text
POST /sync360/text/offer
POST /sync360/text/transfer
POST /sync360/file/offer
```

An offer waits up to 55 seconds for the receiver's decision. File metadata is converted from HTTP DTOs into the shared `FileTransferOffer` domain model at the Ktor boundary.

## File data plane: raw TCP

Accepted file bytes use a separate raw TCP connection:

```text
one connection for the accepted batch
  -> repeat for each accepted file:
       -> file index: Int
       -> promised file size: Long
       -> exactly promised-size bytes
  -> sender flushes after the complete batch
  -> receiver result: Boolean
  -> completed-file count: Int
```

Files remain sequential. The receiver verifies each index and size directly against the matching file in the accepted offer before saving. It increments the completed-file count only after the platform Downloads writer returns successfully. After every file has been processed, the receiver sends one final success flag and completed count. If processing fails, it attempts to send `false` with the number of files that were fully saved.

`FileTransferConstants` currently provides:

- 512 KiB payload buffers
- 5-second connect timeout
- 60-second connected-socket timeout
- 10-second wait for the first file connection after acceptance

The sender and receiver do not need matching read boundaries because TCP is a byte stream; exact file sizes define the protocol framing. Flushing once after the batch makes any remaining buffered bytes available before the sender waits for the final result, but the flush does not define file boundaries.

## Platform storage

- Android writes into public Downloads with a pending `MediaStore` entry. It publishes the entry only after success and deletes the incomplete current entry on failure.
- Desktop writes to a temporary `.part` file in the user's Downloads folder, deletes it on failure, and moves it to a collision-safe final name after success.

Previously completed files remain when a later file in the same batch fails.

## Current limitations

- No authentication, encryption, session token, or cryptographic integrity check.
- No retry, pause/resume, or interrupted-transfer recovery.
- Foreground/background and network-change lifecycle handling are not complete.
- Receiver failures do not yet provide rich error details.
- Host selection still uses the first resolved address.
- Desktop interface selection and firewall behavior need broader validation.
- Automated transfer coverage is minimal.
- iOS targets and implementations are inactive.
