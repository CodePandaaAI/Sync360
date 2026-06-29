# Development Guide

This guide explains how to set up and work on Sync360 locally.

## Requirements

- JDK 17
- Android Studio or IntelliJ IDEA with Kotlin support
- Android SDK Platform 37
- Android SDK Build Tools 36.0.0 or newer
- Gradle wrapper 9.4.1, already included in this repository
- Git
- Two Android devices on the same local network for real discovery testing

The project currently uses Android Gradle Plugin 9.2.1. Use a recent Android Studio version that supports AGP 9.2.x.

## Open the project

Open the repository root in Android Studio or IntelliJ IDEA.

Let Gradle sync finish before editing generated project configuration.

## Modules

- `androidApp` - Android application host.
- `shared` - KMP shared code, UI, domain models, Ktor prototype, Android source set.
- `desktopApp` - JVM desktop app shell.

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

Desktop shell:

```bash
./gradlew :desktopApp:run
```

## Local network testing

For Android-to-Android testing:

1. Install the app on two physical Android devices.
2. Connect both to the same Wi-Fi network.
3. Open the app on both devices.
4. Watch the Send screen for nearby devices.
5. Tap a discovered device to trigger the current ping/request experiment.
6. Use the Receive screen on the other device to accept or decline.

If discovery does not work:

- confirm both devices are on the same network
- try a different Wi-Fi network or hotspot
- check whether the router blocks client-to-client traffic
- inspect Android logs for NSD failures
- verify that the discovered device has host addresses and a non-zero port

## Debugging tips

Useful places to inspect:

- `AndroidNetworkServices` for NSD registration/discovery/resolve events.
- `Sync360HttpServer` for incoming route behavior.
- `Sync360HttpClient` for outgoing request URL and response parsing.
- `IncomingServerRequestsController` for receiver decision state.
- `NetworkServicesController` for startup order.

## Working style

This project values understanding over cleverness.

Prefer:

- small changes
- direct names
- clear ownership
- route-specific DTOs
- focused controllers
- simple state flows

Avoid:

- large rewrites without discussion
- generic abstractions too early
- hidden startup side effects in DI modules
- UI code that owns networking behavior
- platform APIs inside composables

## Tests

Test coverage is still minimal. When adding pure Kotlin logic, add focused tests if practical.

For networking and Android NSD changes, include manual test notes in pull requests.

## Release notes

There are no stable releases yet. Use `CHANGELOG.md` to document meaningful changes under `[Unreleased]`.
