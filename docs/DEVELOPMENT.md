# Development Guide

This guide covers the current Android and Desktop/JVM development flow.

## Requirements

- JDK 17
- A recent Android Studio or IntelliJ IDEA version compatible with Kotlin 2.3.21 and Android Gradle Plugin 9.2.x
- Android SDK Platform 37 for Android development
- Git
- A local network or hotspot that allows device-to-device traffic
- Two Android 13+ devices for Android-to-Android testing, or Android plus Desktop for cross-platform testing

The repository includes the Gradle 9.4.1 wrapper.

## Modules

- `androidApp` — Android application host.
- `desktopApp` — Compose Desktop entry point and DMG/MSI/DEB packaging configuration.
- `shared` — shared UI, ViewModels, controllers, Ktor protocol, contracts, and Android/JVM implementations.

## Common commands

Android debug build:

```bash
./gradlew :androidApp:assembleDebug
```

Windows:

```powershell
./gradlew.bat :androidApp:assembleDebug
```

Android release build:

```bash
./gradlew :androidApp:assembleRelease
```

Desktop run:

```bash
./gradlew :desktopApp:run
```

Windows:

```powershell
./gradlew.bat :desktopApp:run
```

## Manual local-network testing

1. Connect both devices to the same trusted Wi-Fi network or hotspot.
2. Open Sync360 on both devices and keep it in the foreground during current testing.
3. Wait for the other device to appear on the Send screen.
4. Test a text offer: Accept, Decline, transfer, Copy, and Clear.
5. Test one file, multiple files, and cancellation.
6. Confirm completed files appear in Downloads.
7. Resize the Desktop window and verify compact single-pane navigation and the wider 50/50 Send/Receive layout.

For Desktop testing, also check systems with multiple adapters, VPNs, WSL, Docker, or virtual machines. The current JmDNS implementation selects one site-local IPv4 interface.

## If discovery or transfer fails

- Confirm both devices are on the same local network.
- Check whether the router enables client isolation.
- Try a trusted phone hotspot or another router.
- Keep both apps open; background/foreground lifecycle support is not complete.
- Check the OS firewall and local-network permissions.
- Verify that HTTP and file-transfer ports are non-zero in logs.
- Inspect whether the selected Desktop LAN adapter matches the active network.
- Remember that some networks block multicast even when ordinary internet access works.

Useful source locations:

- `NetworkServicesController` — startup order and discovery window.
- `AndroidNetworkServices` — Android NSD registration, discovery, and resolution.
- `JvmNetworkServices` — JmDNS registration, discovery, and LAN-interface selection.
- `Sync360HttpServer` / `Sync360HttpClient` — offer and text routes.
- `OutgoingRequestsController` / `IncomingServerRequestsController` — send/receive coordination.
- platform `FileTransferSender`, `FileTransferReceiver`, and `DownloadsWriter` implementations — file bytes and storage.

## Working style

Prefer small changes, direct names, explicit ownership, route-specific DTOs, streaming I/O, and platform implementations behind common contracts.

Avoid large speculative abstractions, networking inside composables, platform APIs in `commonMain`, loading whole files into memory, or claims that have not been manually verified.

## Tests and pull requests

Automated coverage is still minimal. Add focused tests for pure Kotlin logic where practical. For discovery, socket, storage, or lifecycle changes, include the exact devices, operating systems, network setup, scenarios, and results in the pull request.

There is no stable release yet. Treat current builds as development software.
