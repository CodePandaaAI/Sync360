# Changelog

All notable changes to Sync360 will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/). Versions remain preview releases, so compatibility can change before `1.0.0`.

## [Unreleased]

### Changed

- Remove the separate discovery repair command, its Settings screen, and Troubleshoot shortcuts; use Start/Stop or failure retry in Nearby devices.

- Tie Android nearby discovery to app visibility, with a cancellable two-second background grace period after the process lifecycle delay.
- Remove the 60-second scan expiry and add Start discovery / Stop discovery controls for browsing and advertising. Manual Stop survives background/foreground transitions within the same process.
- Centralize discovery-session start/stop coordination while preserving transfer listeners and content state. Desktop discovery remains active when minimized.
- Give Android scans separate callback ownership, wait for service-info callback cleanup, and queue Android 13 legacy resolution.
- Keep failed discovery cleanup retryable and ignore resolutions for services lost and found again.
- Replace Reload and the top-bar discovery action with Start/Stop and failure retry in the Nearby devices section.
- Present nearby devices in compact rows with a header Stop action and a centered Start discovery state when off.
- Shorten discovery messages and make lifecycle, callback, and platform-operation names more descriptive.

These changes have not yet been built or validated on devices.

## [0.4.1] - 2026-08-28

### Changed

- Replaced the centered file receive-code alert with a modal bottom sheet that shows the target device, four clear digit slots, and the selected file count.
- Kept file sending explicit: entering four digits enables the Send button, and the transfer starts only after the sender confirms.
- Simplified the idle Receive presentation and clarified the temporary file-code wording on Send and Receive.
- Renamed the outgoing raw file-stream abstraction to `FileTransmitter` and made the HTTP file-offer helper name more explicit. These are internal readability changes and do not change the `0.4.0` wire format.
- Prepared Android, Desktop, and iOS packages as `0.4.1`; Android and iOS build numbers are `5`. Preview protocol metadata remains version `1`.

## [0.4.0] - 2026-08-28

### Changed

- Replaced the file Accept/Decline screen and suspended HTTP decision with a temporary four-digit receive code generated once for each fresh application session.
- Displayed the same session receive code on both the Send and Receive screens so it is visible from the default screen.
- File offers now include the entered code and receive an immediate accepted, invalid-code, busy, or preparation-failed response.
- Removed file `UserDecision`, `CompletableDeferred`, incoming-offer state, decision timeout, and Accept/Cancel race while retaining operation IDs, TCP preparation timeout, cancellation, progress, framing, and cleanup.
- Changed the file-offer wire format, making `0.4.0` incompatible with `0.3.0` and older builds; preview protocol metadata intentionally remains version `1` for now.

### Security

- Documented the receive code as a short-lived convenience against accidental or casual unwanted sends, not authentication; it has no attempt throttling, and local HTTP and raw TCP remain cleartext.

## [0.3.0] - 2026-08-27

### Added

- Android-first manual rebuild with shared Compose Multiplatform Send and Receive UI.
- Android DNS-SD/mDNS discovery and registration through `NsdManager`.
- Windows DNS-SD/mDNS discovery and registration through the operating system `dnsapi.dll` API on all interfaces.
- Current macOS/Linux DNS-SD/mDNS discovery and registration through JmDNS on eligible IPv4 and IPv6 LAN addresses.
- Separate discovery and registration lifecycle states shared by Android, Desktop, the controller, and UI.
- Stable per-install device identity and advertised dynamic HTTP/file-transfer ports.
- One-request direct text delivery with sender name, a 100,000-character limit, Copy, and Clear.
- Android and Desktop multiple-file selection and metadata offers.
- Raw TCP file transfer using one persistent connection per accepted batch.
- Operation-bound file framing with operation ID, index, and size validation plus one final batch result containing receiver success and the completed-file count.
- Android Downloads writing through pending `MediaStore` entries.
- Desktop Downloads writing through temporary `.part` files and collision-safe final names.
- Unified send operation states and explicit operation-scoped cancellation, with timeout fallbacks for lost communication.
- Shared transfer buffer/timeout constants, currently using a 512 KiB payload buffer.
- Compose Desktop startup, platform DI implementations, native file dialog, clipboard, and Downloads actions.
- Navigation 3 adaptive 50/50 Send/Receive scene for wider windows.
- Application-lifetime network startup and state-driven connection repair.
- Enabled iOS device and Apple-silicon Simulator targets with native Bonjour discovery, document selection, clipboard, Files-visible storage, and streamed TCP transfer implementations.
- Added an iOS-only GitHub Actions workflow for an unsigned Simulator app and optional development-signed iPhone IPA.
- Prepared version `0.3.0` across Android, Desktop, and iOS; retained private Android release signing configuration and the permanent Windows MSI upgrade identity.
- Public architecture, development, roadmap, security, privacy, and contribution documentation.

### Changed

- Separated text from file operations: text now delivers directly while idle without an offer, decision, operation ID, waiting state, remote cancellation, or Cancel action.
- Kept file offers, receiver decisions, operation IDs, cancellation, timeouts, progress, and raw TCP streaming unchanged in purpose.
- Replaced the old generated sync implementation with a smaller, manually understood flow.
- Separated Ktor HTTP offer/control messages from raw TCP file bytes.
- Reused one TCP connection for the complete accepted multi-file batch instead of opening one connection per file.
- Removed per-file flush-and-acknowledgement waits so an accepted batch can stream continuously before one final receiver result.
- Moved network startup from the Send ViewModel to the Android and Desktop application entry points.
- Derived the 60-second discovery window from the platform-reported running state.
- Made Android repair advance through NSD callbacks and made JVM cleanup retain JmDNS instances that fail to close.
- Restricted discovery Reload and full connection repair to compatible discovery and registration states.
- Selected the Windows-native discovery backend at Desktop DI startup while retaining JmDNS for macOS and Linux.
- Used the JDK Foreign Function and Memory API for Windows interop without adding a third-party native bridge.
- Made Windows discovery process native add and TTL-zero removal notifications so the nearby-device list can update during an active browse.
- Moved Windows discovery out of its initial loading state as soon as the operating system accepts the asynchronous browse request.
- Confirmed in an initial Windows 11 Ethernet test that Android and Windows advertisements appeared promptly and were removed after the corresponding app closed.
- Aligned Kotlin 2.4.10, Android Gradle Plugin 9.1.1, and Gradle 9.3.1 within their documented compatibility ranges while retaining Android API 37.
- Positioned the project around direct local-network nearby sharing rather than chat or cloud sync.

### Known limitations

- No stable public release yet.
- No authentication, encryption, transfer/session token, or cryptographic integrity verification.
- No speed, ETA, retry, pause/resume, or interrupted-transfer recovery; transfer progress currently shows batch-wide whole-byte percentage.
- Foreground/background and network-change lifecycle handling are incomplete.
- The target-SDK-37 Android build does not yet declare or request Android 17's `ACCESS_LOCAL_NETWORK` runtime permission, so LAN discovery and transfer are blocked by default there.
- Android 13 starts legacy NSD resolves immediately; overlapping discoveries can fail with an already-active resolve and are not currently retried.
- A narrow Accept/Cancel race can let an offer response report acceptance after the matching receiver state was cancelled.
- Desktop support needs broader operating-system, adapter, firewall, and router validation. Windows inbound sharing also depends on the user or administrator allowing Sync360 through Windows Firewall.
- Windows retains completed native callback arenas for safety instead of closing them later, so repeated discovery repairs can slowly retain native memory; a service removal on one interface can also temporarily remove that service's results from other interfaces.
- Automated transfer coverage is minimal; iOS physical-device discovery and transfer are unverified.
