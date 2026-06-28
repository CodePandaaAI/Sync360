<div align="center">
  <img src="shared/src/commonMain/composeResources/drawable/app_icon.png" alt="Sync360 logo" width="120" />

  # Sync360

  **Local device sharing, rebuilt from first principles.**

  Share files and text between nearby devices on the same network without uploading them to a cloud server.
</div>

---

## What Sync360 Is

Sync360 is a Kotlin Multiplatform and Compose Multiplatform app for nearby device-to-device sharing.

The goal is simple: open the app on two devices connected to the same local network, discover nearby Sync360 devices, choose a target, approve the request on the receiver, and send files or text directly over the local network.

This repository is currently being rebuilt from scratch, manually and publicly, after an earlier AI-generated implementation became too large to confidently own. The current code is intentionally small, Android-first, and learning-focused. The priority is understanding every networking and state-management step before adding more product surface.

## Current Status

Sync360 is not a finished file-sharing app yet. It is in an early rebuild phase.

Currently working:

- Android app startup with Koin.
- Compose Multiplatform UI shell.
- Navigation 3 Send and Receive screens.
- Android NSD service advertisement and discovery.
- Stable per-install local device UUID.
- Nearby device model with `hostAddresses` and dynamic `port`.
- Ktor local HTTP server started inside the app.
- OS-assigned dynamic server port advertised through NSD.
- Ktor HTTP client calling a discovered device.
- First local request/response route: `GET /sync360/ping`.
- Receiver-side Accept/Decline proof through the Receive screen.

Not built yet:

- Real send-offer DTOs.
- File picker and selected item list.
- Text snippet sending.
- File byte transfer.
- Progress UI.
- Storage/save handling.
- Security/session validation.
- Desktop support for the rebuilt flow.

## Why This Project Exists

Most file sharing workflows become awkward when files are large or when devices are already sitting on the same Wi-Fi network. Chat apps and cloud drives often upload data out to the internet only to download it back onto another nearby device.

Sync360 is being built around a local-first idea:

```text
same network -> discover nearby devices -> ask receiver -> send directly
```

The app is also a learning project in public. It is being rebuilt one small slice at a time to deeply understand Android local networking, Ktor servers and clients, serialization, coroutines, state flow, dependency injection, lifecycle, and eventually file bytes and security.

## Current Flow

The current prototype flow is:

```text
Device A starts local Ktor server
Device A advertises itself with Android NSD
Device B discovers Device A
Device B reads Device A hostAddresses + port
Device B calls http://host:port/sync360/ping
Device A shows incoming request on Receive screen
User accepts or declines
Device A responds
Device B receives Accepted or Declined
```

This is the first control-plane milestone. It proves that discovery can lead to a real local HTTP request and a structured response.

## Architecture Snapshot

The current code is deliberately small and direct.

```text
androidApp/
  Android entry point, Application, MainActivity

shared/
  commonMain/
    Compose UI
    ViewModels
    Koin common module
    shared domain models
    Ktor HTTP client/server prototype
    request coordination controllers

  androidMain/
    Android Koin module
    Android NSD implementation
    Android local device identity store
```

Important current pieces:

- `Sync360Root` owns the shared Compose app shell and Navigation 3 display.
- `SendScreenViewModel` exposes nearby devices and starts outgoing requests through a controller.
- `ReceiveScreenViewModel` exposes incoming request state and sends Accept/Decline decisions.
- `NetworkServicesController` starts the HTTP server, then starts discovery, then stops scanning after a short window.
- `AndroidNetworkServices` owns Android NSD advertisement/discovery.
- `Sync360HttpServer` owns the local Ktor server and `/sync360/ping` route.
- `Sync360HttpClient` calls nearby devices using discovered host/port data.
- `IncomingServerRequestsController` bridges incoming server requests to the Receive UI.
- `OutgoingRequestsController` wraps outgoing client requests.

The architecture is not final. The current goal is to keep responsibilities understandable while the networking fundamentals are being learned and proven.

## Tech Stack

- Kotlin Multiplatform
- Compose Multiplatform
- Android-first implementation
- Koin for dependency injection
- Ktor Client and Server with CIO
- kotlinx.serialization for JSON
- Android NSD / mDNS discovery
- Coroutines and StateFlow
- Navigation 3

## Roadmap

Short-term rebuild milestones:

1. Android discovery and dynamic server port advertisement.
2. Simple local HTTP request/response between two Android devices.
3. Real send offer request with receiver Accept/Decline.
4. Direct text snippet send after approval.
5. One-file transfer over local network.
6. Multiple files and text in one outgoing bundle.
7. Progress, result, and failure states.
8. Desktop support.
9. Security as a separate learning phase.

Security is intentionally not first. The project will add it after the simple local flow is understood and working.

## Running The Project

Android debug build:

```powershell
./gradlew.bat :androidApp:assembleDebug
```

Desktop target exists, but the current rebuild is Android-first and desktop behavior is not yet part of the active milestone.

## Build In Public

This project is being rebuilt openly as both a product and a learning record.

The philosophy:

- Make one small networking slice work.
- Understand every line.
- Keep names direct.
- Add structure only when it removes real confusion.
- Avoid generated architecture that cannot be explained.

## Contributing

The repository is being prepared for open source. Contributions, ideas, and debugging notes are welcome once the public workflow is ready.

For now, the best contributions are likely to be:

- Android local-network testing notes.
- Ktor client/server improvements.
- Clear naming and architecture feedback.
- Small, focused bug fixes.
- Documentation improvements.

## License

A license has not been added yet. One should be selected before relying on this repository as an open-source dependency.
