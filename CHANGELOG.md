# Changelog

All notable changes to Sync360 will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/). Semantic versioning will begin when public releases begin.

## [Unreleased]

### Added

- Android-first manual rebuild with shared Compose Multiplatform Send and Receive UI.
- Android DNS-SD/mDNS discovery and registration through `NsdManager`.
- Windows DNS-SD/mDNS discovery and registration through the operating system `dnsapi.dll` API on all interfaces.
- Current macOS/Linux DNS-SD/mDNS discovery and registration through JmDNS on eligible IPv4 and IPv6 LAN addresses.
- Separate discovery and registration lifecycle states shared by Android, Desktop, the controller, and UI.
- Stable per-install device identity and advertised dynamic HTTP/file-transfer ports.
- Text offers, receiver Accept/Decline, text transfer, Copy, and Clear.
- Android and Desktop multiple-file selection and metadata offers.
- Raw TCP file transfer using one persistent connection per accepted batch.
- Sequential file framing with index/size validation and one final batch result containing receiver success and the completed-file count.
- Android Downloads writing through pending `MediaStore` entries.
- Desktop Downloads writing through temporary `.part` files and collision-safe final names.
- Unified send operation states and best-effort cancellation.
- Shared transfer buffer/timeout constants, currently using a 512 KiB payload buffer.
- Compose Desktop startup, platform DI implementations, native file dialog, clipboard, and Downloads actions.
- Navigation 3 adaptive 50/50 Send/Receive scene for wider windows.
- Application-lifetime network startup and state-driven connection repair.
- Public architecture, development, roadmap, security, privacy, and contribution documentation.

### Changed

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
- Desktop support needs broader operating-system, adapter, firewall, and router validation.
- Automated transfer coverage is minimal; iOS is inactive.
