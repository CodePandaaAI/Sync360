# Architecture

Sync360 is an Android-first Kotlin Multiplatform app for direct nearby sharing over a local network. Android, Desktop, and iOS reuse the common UI and transfer flow; platform source sets implement discovery, file access, storage, identity, clipboard, and raw socket I/O.

The architecture intentionally follows one readable path:

```text
Compose screen -> ViewModel -> controller/service -> common contract -> platform implementation
```

## Current end-to-end flow

```text
app starts
  -> Koin creates common services and platform implementations
  -> Android, Desktop, or iOS entry point starts NetworkServicesController once
  -> Sync360HttpServer opens an OS-assigned HTTP port
  -> FileTransferReceiver opens an OS-assigned TCP port
  -> NetworkServices advertises both ports through DNS-SD/mDNS
  -> nearby Sync360 devices are resolved into NearbyDevice
  -> sender delivers text directly or posts file metadata with a receive code
  -> idle receiver publishes the text, or checks the code and prepares file reception
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
  Windows system DNS-SD or macOS/Linux JmDNS discovery/registration
  AWT file selection and clipboard
  Java file/Downloads handling, identity, device info, TCP sender/receiver, and DI

shared/src/iosMain/
  Apple Bonjour discovery/registration
  native document selection and clipboard
  Files-visible storage, identity, device info, Ktor TCP sender/receiver, and DI
```

## Main responsibilities

### `Sync360Root`

Owns the single app `Scaffold`, compact bottom navigation, and one Navigation 3 `NavDisplay`. A small `TwoPaneSceneStrategy` renders Send and Receive in a fixed 50/50 split when the Material adaptive window size reaches the medium-width breakpoint. Compact windows use Navigation 3's normal single-pane fallback.

### ViewModels

- `SendScreenViewModel` owns nearby-device state, selected files/text, send operations, results, and cancellation.
- `ReceiveScreenViewModel` maps incoming server state and the session receive code to Receive UI, and handles Copy, Clear, and Open Downloads actions.
- `NavigationViewModel` keeps Send and Receive available as top-level entries and selects the active compact destination.

ViewModels launch UI-facing work. They do not implement platform APIs or socket protocols.

### Controllers

- `NetworkServicesController` owns application-lifetime HTTP/file listeners and serializes discovery intent separately. Platform callbacks report progress; the controller decides when to start or stop a discovery session. Discovery stop never closes transfer sockets.
- `OutgoingRequestsController` validates and delivers text, creates code-bearing file offers, and starts accepted file transfers.
- `IncomingServerRequestsController` generates one in-memory four-digit file receive code for its application session. Under one operation mutex it admits direct text only while idle, or atomically checks the file code, prepares the platform receiver, and publishes `ReceivingFiles`. Text follows `Idle -> TextReceived -> Idle`; files follow `Idle -> ReceivingFiles -> FilesReceived/Idle`.

### Discovery

`NetworkServices` is the common contract.

- Android uses `NsdManager` with `_sync360._tcp.`.
- Windows uses the operating system DNS-SD functions in `dnsapi.dll`.
- macOS and Linux currently use JmDNS with `_sync360._tcp.local.`.

Both advertise a stable device UUID, device name/type, protocol version, dynamic HTTP port, and dynamic file-transfer port. A device filters its own UUID from discovery results.

Discovery and registration expose independent `StateFlow` values (`Idle`, `Starting`, `Running`, `Stopping`); discovery also reports `CleanupFailed` when resources remain owned after cleanup fails. A retry must finish cleanup before a replacement session starts. There is no scan expiry timer. Start discovery and Stop discovery control both browsing and advertising; a manual Stop remains in effect across background/foreground transitions until Start is pressed or the process restarts. Registration success is not a guarantee of reachability from another device.

`AndroidNearbyDiscoveryObserver` observes only app visibility through `ProcessLifecycleOwner`. Foreground entry allows discovery immediately. Background entry schedules a stop after two seconds, in addition to the lifecycle owner's own delay. Returning before that stop cancels it and keeps the current session; returning after cleanup starts a new session unless the user manually stopped discovery. It does not monitor networks or automatically refresh after network changes; users can Stop and Start discovery themselves. Desktop and iOS retain process-lifetime availability; minimizing Desktop does not stop discovery. iOS visibility integration is not part of this change.

The controller waits for both operations to leave transitional states before issuing a replacement start. Users can Stop discovery and Start it again using the existing HTTP and TCP ports; there is no separate repair command. A failed start/stop is surfaced for explicit retry rather than automatically retried forever. Missing native completion callbacks are not treated as successful cleanup.

Android discovery commands and callbacks are serialized on the main dispatcher using asynchronous NSD APIs. Each scan owns a distinct listener, result map, and Android 14+ service-info callbacks; a replacement session waits for tracking callbacks to unregister. Old callbacks cannot publish into a newer session. Android 13 one-shot resolution is queued; an outstanding legacy resolve retains its slot until completion because that API level has no stop-resolution API. Its late result is discarded after its session ends or the service is lost and found again.

Windows cancels browse and pending resolves and deregisters through the native API. The macOS/Linux fallback closes JmDNS instances off the UI thread; instances that fail to close remain owned for a later cleanup attempt. Platform-native callback/memory ownership remains necessary.

Windows calls `DnsServiceBrowse`, `DnsServiceResolve`, `DnsServiceRegister`, and `DnsServiceDeRegister` through the JDK Foreign Function and Memory API. Browse and registration use interface index `0`, which delegates all-interface IPv4/IPv6 handling to Windows. Native registration and deregistration callbacks drive `RegistrationStatus`; browse cancellation drives the final transition back to `DiscoveryStatus.Idle`. Browse callbacks start resolution for added PTR records and remove devices reported with a zero TTL. Resolved TXT properties and IPv4/IPv6 addresses are converted into the same shared `NearbyDevice` model used by Android.

The Windows implementation keeps native request memory alive after terminal callbacks because a callback is still unwinding when Kotlin receives it. Those retired arenas are not yet closed later, so repeated discovery restart cycles can retain small native allocations. Resolved results are keyed by service name and interface, but a TTL-zero browse removal currently clears every interface result for that service name.

The macOS/Linux JmDNS fallback starts on eligible IPv4 and IPv6 addresses from every active, multicast-capable, non-loopback, non-virtual LAN interface. Windows DNS-SD and the fallback still need broader validation with VPN, WSL, Docker, virtual-machine, Ethernet, and Wi-Fi adapters.

## Control plane: Ktor HTTP

Ktor carries direct text plus immediate code-checked file offers and metadata:

```text
POST /sync360/text/deliver
POST /sync360/file/offer
POST /sync360/operation/cancel
```

Text is delivered in one request containing the sender device name and text. It has no offer, decision, operation ID, waiting state, or cancellation route. Text above 100,000 Kotlin `String.length` units is rejected, and the receiver atomically checks `Idle` and publishes `TextReceived` under the operation mutex.

A file offer contains the sender-entered four-digit receive code, operation identity, and file metadata. The receiver immediately reports accepted, invalid code, receiver busy, or preparation failed. A correct code is admitted only while `ClientServerState` is `Idle`; code checking, platform receiver preparation, and the `ReceivingFiles` state change happen under the operation mutex before acceptance is returned.

The receive code is generated once when the singleton incoming controller is created for a fresh application session. The Send and Receive ViewModels both read that same controller-owned value and render the same shared code card. It remains only in memory, is not advertised, and is not remembered by the sender. It is a convenience check rather than authentication because it has only 9,000 possible values and is sent over cleartext HTTP.

The added request field and structured response statuses changed the file-offer wire format in `0.4.0`. This implementation is not file-transfer compatible with `0.3.0` or older builds. Version `0.4.1` keeps the `0.4.0` wire format, so those two versions can transfer files with each other. Protocol metadata intentionally remains version `1` during the current preview stage, and discovery does not yet reject an incompatible older peer.

A random operation ID still correlates the accepted file offer, explicit cancellation, and TCP connection. Cancellation succeeds only when both the operation ID and sender device ID match the active file state. Every platform receiver retains its 30-second timeout waiting for the first TCP connection, so an accepted offer cannot leave the receiver busy forever if the sender disappears. File contents remain in platform file readers and are not placed in the HTTP request.

## File data plane: raw TCP

Accepted file bytes use a separate raw TCP connection:

```text
one connection for the accepted batch
  -> operation ID: 16 raw UUID bytes
  -> repeat for each accepted file:
       -> file index: Int
       -> promised file size: Long
       -> exactly promised-size bytes
  -> sender flushes after the complete batch
  -> receiver result: Boolean
  -> completed-file count: Int
```

Files remain sequential. The receiver first verifies that the socket operation ID matches the accepted offer, then verifies each index and size directly against the matching file before saving. It increments the completed-file count only after the platform Downloads writer returns successfully. After every file has been processed, the receiver sends one final success flag and completed count. If processing fails, it attempts to send `false` with the number of files that were fully saved.

`FileTransferConstants` currently provides:

- 512 KiB payload buffers
- 5-second connect timeout
- 60-second connected-socket timeout
- 30-second wait for the first file connection after acceptance

The sender and receiver do not need matching read boundaries because TCP is a byte stream; exact file sizes define the protocol framing. Flushing once after the batch makes any remaining buffered bytes available before the sender waits for the final result, but the flush does not define file boundaries.

## Platform storage

- Android writes into public Downloads with a pending `MediaStore` entry. It publishes the entry only after success and deletes the incomplete current entry on failure.
- Desktop writes to a temporary `.part` file in the user's Downloads folder, deletes it on failure, and moves it to a collision-safe final name after success.
- iOS writes to a temporary `.part` file in the app's Files-visible `Documents/Downloads` directory, deletes it on failure, and moves it to a collision-safe final name after success.

Previously completed files remain when a later file in the same batch fails.

## Current limitations

- No authentication, encryption, session token, or cryptographic integrity check. The four-digit code has no attempt throttling and must not be treated as a security boundary.
- No retry, pause/resume, or interrupted-transfer recovery.
- Android foreground discovery is implemented but requires device validation. Automatic network-change recovery is deferred. Background transfer guarantees, Desktop wake/network recovery, and iOS visibility handling remain outside this change.
- Android 17 local-network permission handling is not implemented even though the app targets SDK 37; Android 13 legacy NSD resolves are serialized, but failed resolves are not automatically retried.
- Receiver failures do not yet provide rich error details.
- HTTP and file-transfer senders retry distinct advertised addresses after connection failures; broader address preference and scoped IPv6 validation still need work.
- Desktop interface selection and firewall behavior need broader validation. Windows inbound transfers require an application allow rule or user-approved firewall prompt.
- Repeated Windows discovery restart cycles retain completed native callback arenas, and removing a service from one interface can temporarily clear the same service resolved through another interface.
- Automated transfer coverage is minimal.
- iOS source targets and implementations are enabled, but physical-device discovery and transfer remain unverified.
