# Development Guide

This guide covers the current Android and Desktop/JVM development flow.

## Requirements

- JDK 23
- A recent Android Studio or IntelliJ IDEA version compatible with Kotlin 2.4.10 and Android Gradle Plugin 9.1.x
- Android SDK Platform 37 for Android development
- Git
- A local network or hotspot that allows device-to-device traffic
- Two Android 13+ devices for Android-to-Android testing, or Android plus Desktop for cross-platform testing

The repository includes the Gradle 9.3.1 wrapper.

Gradle and Desktop use JDK/JVM 23 because the Windows backend uses the finalized Foreign Function and Memory API. Android continues to emit Java 17 bytecode; Windows `jvmMain` sources are not part of the Android artifact, so raising Android's bytecode target would add no FFM capability.

## Modules

- `androidApp` — Android application host.
- `desktopApp` — Compose Desktop entry point and DMG/MSI/DEB packaging configuration.
- `iosApp` — SwiftUI iOS application host.
- `shared` — shared UI, ViewModels, controllers, Ktor protocol, contracts, and Android/JVM/iOS implementations.

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

## Preparing public packages

The current package version is `0.4.1`.

Android release APKs must use the maintainer's permanent private signing key. Copy `keystore.properties.example` to the ignored `keystore.properties` file and set:

```properties
storeFile=C:/absolute/path/to/keystore.jkis
storePassword=your-keystore-password
keyAlias=your-key-alias
keyPassword=your-key-password
```

Never commit the keystore, `keystore.properties`, passwords, or private keys. Keep secure backups of the signing key because future APK updates must use the same key.

Build the Android release APK:

```powershell
./gradlew.bat :androidApp:assembleRelease
```

Windows public packages currently use the normal Compose Desktop MSI task, not the ProGuard release-MSI task:

```powershell
./gradlew.bat :desktopApp:packageMsi
```

The Windows `upgradeUuid` must remain unchanged for the lifetime of Sync360, and `packageVersion` must increase for every public MSI so a newer installer can replace an older installed version. Windows packages are currently unsigned and may show an unknown-publisher or SmartScreen warning.

## Manual local-network testing

1. Connect both devices to the same trusted Wi-Fi network or hotspot.
2. Open Sync360 on both devices and keep it in the foreground during current testing.
3. Wait for the other device to appear on the Send screen.
4. Test direct text delivery while idle and busy, the 100,000/100,001 boundaries, sender name, Copy, and Clear.
5. Test the file receive-code bottom sheet with correct, incorrect, incomplete, and non-numeric input.
6. Confirm Send and Receive show the same code, and that a fresh application start creates a new code while navigation and recomposition do not change it.
7. Test one file, multiple files, receiver-busy behavior, the first-connection timeout, and cancellation.
8. Confirm completed files appear in Downloads.
9. Resize the Desktop window and verify compact single-pane navigation and the wider 50/50 Send/Receive layout.

For Windows testing, check IPv4 and IPv6 with Ethernet, Wi-Fi, VPN, WSL, Docker, Hyper-V, or virtual-machine adapters. Windows DNS-SD browses and registers with interface index `0`, so Windows selects the applicable interfaces. Confirm discovery and resolution, live removal when a nearby app closes, removal of Windows from the other device after the Desktop app closes, Reload, and full connection repair.

On first network use, allow Sync360 on the intended private network when Windows Firewall prompts. The current MSI does not install its own inbound firewall exception; a denied prompt or administrator policy can block incoming HTTP and file-transfer sockets.

Android currently targets SDK 37 but does not yet declare or request Android 17's `ACCESS_LOCAL_NETWORK` runtime permission. Android 17 LAN testing is therefore expected to fail until permission-aware startup is implemented. On Android 13, also test several discoverable devices appearing close together because the legacy resolver is not yet queued.

macOS and Linux currently retain JmDNS. Test those systems with multiple adapters as well because JmDNS starts separately on each eligible address.

## If discovery or transfer fails

- Confirm both devices are on the same local network.
- Check whether the router enables client isolation.
- Try a trusted phone hotspot or another router.
- Keep both apps open; background/foreground lifecycle support is not complete.
- Check the OS firewall and local-network permissions.
- On Windows, confirm an inbound allow rule exists for Sync360 if the first-run firewall prompt was dismissed or denied.
- Verify that HTTP and file-transfer ports are non-zero in logs.
- Inspect whether the selected Desktop LAN adapter matches the active network.
- Remember that some networks block multicast even when ordinary internet access works.

Useful source locations:

- Android `Sync360Application` and Desktop `main` — one-time application network startup after Koin initialization.
- `NetworkServicesController` — startup order, state-derived discovery window, restart, and repair coordination.
- `AndroidNetworkServices` — callback-driven Android NSD registration, discovery, resolution, and repair.
- `WindowsNetworkServices` — Windows DNS-SD registration, discovery, resolution, cancellation, and shared-state mapping.
- `WindowsDnsSdApi` — focused JDK Foreign Function and Memory bindings for `dnsapi.dll`.
- `JvmNetworkServices` — current macOS/Linux JmDNS registration, discovery, repair cleanup, and IPv4/IPv6 LAN-interface selection.
- `Sync360HttpServer` / `Sync360HttpClient` — direct text delivery and file control routes.
- `OutgoingRequestsController` / `IncomingServerRequestsController` — send/receive coordination.
- platform `FileTransmitter`, `FileTransferReceiver`, and `DownloadsWriter` implementations — file bytes and storage.

The Windows backend currently requires a 64-bit Desktop JVM, matching the project's Windows packaging target and the native ABI used by the binding.

## Working style

Prefer small changes, direct names, explicit ownership, route-specific DTOs, streaming I/O, and platform implementations behind common contracts.

Avoid large speculative abstractions, networking inside composables, platform APIs in `commonMain`, loading whole files into memory, or claims that have not been manually verified.

## Tests and pull requests

Automated coverage is still minimal. Add focused tests for pure Kotlin logic where practical. For discovery, socket, storage, or lifecycle changes, include the exact devices, operating systems, network setup, scenarios, and results in the pull request.

There is no stable release yet. Treat current builds as development software.
