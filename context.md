# Sync360 Project Context

This file is the current project handoff for developers and AI assistants. It replaces the old generated-code era context. If this file conflicts with the repository, trust the repository and update this file.

## Project Identity

Sync360 is a Kotlin Multiplatform / Compose Multiplatform app for nearby device sharing over a local network.

The product direction is closer to Quick Share than a chat app, clipboard history app, or permanent device manager. The intended flow is:

```text
open app -> discover nearby devices -> choose target -> receiver approves -> send files/text directly
```

The current rebuild is Android-first. Desktop and iOS folders exist, but they are not the active implementation target yet.

## Why The Rebuild Exists

An older Sync360 implementation grew too large through AI-generated code. It contained many ideas, layers, and transport decisions before the project owner fully understood the underlying concepts.

The current project is being rebuilt manually from a smaller baseline so the owner can learn and own every part of the system:

- Android NSD / mDNS discovery.
- Host addresses and ports.
- Local Ktor HTTP servers.
- Ktor HTTP clients.
- JSON DTOs and serialization.
- Coroutines and request/response waiting.
- StateFlow propagation from lower layers to UI.
- Dependency injection with Koin.
- Later: file bytes, storage, progress, and security.

The goal is not to create perfect architecture first. The goal is to build one understandable working slice at a time, then extract structure only when the need is real.

## Current Implementation Status

As of this context update, the current rebuild has:

- Android app startup through `Sync360Application`.
- Shared Compose root through `Sync360Root`.
- Navigation 3 Send and Receive screens.
- Koin DI setup.
- Stable Android install UUID through `LocalDeviceIdentityStore`.
- Android NSD advertisement and discovery through `AndroidNetworkServices`.
- `NearbyDevice` with `hostAddresses` and `port`.
- Local Ktor server through `Sync360HttpServer`.
- Dynamic OS-assigned HTTP server port advertised through NSD.
- Ktor HTTP client through `Sync360HttpClient`.
- First request route: `GET /sync360/ping`.
- Route-specific `PingRequestResponse` with Accepted/Declined variants.
- Incoming server request state surfaced to Receive UI.
- Receiver Accept/Decline decision sent back to the suspended server route.
- Basic outgoing ping request controller.

This is a technical milestone, not a finished MVP. It proves:

```text
NSD discovery -> discovered route -> HTTP request -> receiver UI decision -> HTTP response
```

## Current Product Flow Under Construction

Near-term target flow:

1. Both Android devices open the app.
2. Each device starts a local HTTP server.
3. Each device advertises itself through Android NSD with the real HTTP server port.
4. Send screen shows nearby devices.
5. User taps a nearby device.
6. Sender sends a request to the receiver using `hostAddresses + port`.
7. Receiver screen shows the incoming request.
8. Receiver accepts or declines.
9. Sender sees accepted or declined.

File transfer is not part of this slice yet.

## Current Runtime Flow

Approximate current startup/request flow:

```text
Sync360Application
  -> initKoin(androidModule)

MainActivity
  -> Sync360Root()

Sync360Root
  -> Navigation 3 shell
  -> observes ReceiveScreenViewModel.serverState
  -> navigates to Receive when server state becomes Busy

SendScreenViewModel
  -> NetworkServicesController.startNetworkServices()

NetworkServicesController
  -> Sync360HttpServer.start()
  -> NetworkServices.startNetworkServices()
  -> waits scan window
  -> stops discovery scan

Sync360HttpServer
  -> starts Ktor CIO server on port 0
  -> reads real assigned port
  -> registers /sync360/ping

AndroidNetworkServices
  -> creates NsdServiceInfo after server has a real port
  -> advertises _sync360._tcp. with device UUID/name/type/protocol
  -> discovers and resolves nearby services
  -> maps resolved services to NearbyDevice

Sync360HttpClient
  -> builds URL from NearbyDevice hostAddresses + port
  -> calls /sync360/ping

IncomingServerRequestsController
  -> exposes ServerState as StateFlow
  -> holds CompletableDeferred<UserDecision> for pending request
  -> lets Receive UI complete Accept/Decline
```

## Key Files

Android entry:

- `androidApp/src/main/kotlin/com/liftley/sync360/Sync360Application.kt`
- `androidApp/src/main/kotlin/com/liftley/sync360/MainActivity.kt`
- `androidApp/src/main/AndroidManifest.xml`

Dependency injection:

- `shared/src/commonMain/kotlin/com/liftley/sync360/core/di/Koin.kt`
- `shared/src/androidMain/kotlin/com/liftley/sync360/core/di/koin.android.kt`

App shell and UI:

- `shared/src/commonMain/kotlin/com/liftley/sync360/Sync360Root.kt`
- `shared/src/commonMain/kotlin/com/liftley/sync360/presentation/featureSend/SendScreen.kt`
- `shared/src/commonMain/kotlin/com/liftley/sync360/presentation/featureReceive/RecieveScreen.kt`
- `shared/src/commonMain/kotlin/com/liftley/sync360/presentation/viewmodel/SendScreenViewModel.kt`
- `shared/src/commonMain/kotlin/com/liftley/sync360/presentation/viewmodel/ReceiveScreenViewModel.kt`
- `shared/src/commonMain/kotlin/com/liftley/sync360/presentation/viewmodel/NavigationViewModel.kt`

Discovery and networking:

- `shared/src/commonMain/kotlin/com/liftley/sync360/data/NetworkServicesController.kt`
- `shared/src/androidMain/kotlin/com/liftley/sync360/data/AndroidNetworkServices.kt`
- `shared/src/androidMain/kotlin/com/liftley/sync360/domain/NsdServiceInfoToNearbyDevice.android.kt`
- `shared/src/commonMain/kotlin/com/liftley/sync360/data/remote/Sync360HttpServer.kt`
- `shared/src/commonMain/kotlin/com/liftley/sync360/data/remote/Sync360HttpClient.kt`
- `shared/src/commonMain/kotlin/com/liftley/sync360/data/remote/IncomingServerRequestsController.kt`
- `shared/src/commonMain/kotlin/com/liftley/sync360/data/remote/OutgoingRequestsController.kt`

Domain models:

- `shared/src/commonMain/kotlin/com/liftley/sync360/domain/model/NearbyDevice.kt`
- `shared/src/commonMain/kotlin/com/liftley/sync360/domain/model/DiscoveryStatus.kt`
- `shared/src/commonMain/kotlin/com/liftley/sync360/domain/model/ServerState.kt`
- `shared/src/commonMain/kotlin/com/liftley/sync360/domain/remote/response/PingResponse.kt`
- `shared/src/commonMain/kotlin/com/liftley/sync360/domain/repository/NetworkServices.kt`

## Current Concepts And Ownership

### Discovery

`NetworkServices` is the current common contract for nearby discovery. Android implementation is `AndroidNetworkServices`.

It currently advertises and discovers Sync360 services. It maps resolved Android `NsdServiceInfo` into common `NearbyDevice` data.

Current known rough edge: discovery currently receives the HTTP server dependency so it can advertise the real server port. This works for learning, but a later cleaner shape may let a runtime controller start the server first and pass only the port to discovery.

### HTTP Server

`Sync360HttpServer` starts an embedded Ktor CIO server inside the app.

It uses:

```text
host = 0.0.0.0
port = 0
```

Meaning:

- listen on network interfaces, not only localhost
- let the OS choose an available port

The route currently implemented is:

```text
GET /sync360/ping
```

This route intentionally goes through receiver decision state for learning. Later, ping may become immediate and real send offers may use the Accept/Decline flow.

### HTTP Client

`Sync360HttpClient` sends HTTP requests to nearby devices.

It currently calls the first `hostAddresses` entry from `NearbyDevice`. This is good enough for the current Android-to-Android proof, but later should prefer IPv4 first or format IPv6 addresses correctly.

### Incoming Request Coordination

`IncomingServerRequestsController` is the middle object between the HTTP server and Receive UI.

The server does not talk to the ViewModel directly. The ViewModel does not own the server route. Both communicate through this controller.

Pattern:

```text
server receives request
  -> controller sets ServerState.Busy
  -> ReceiveScreen observes Busy
  -> user accepts/declines
  -> controller completes deferred
  -> server route resumes and responds
```

This is the core bridge for incoming local requests.

### Outgoing Requests

`OutgoingRequestsController` wraps outgoing HTTP client calls. It converts low-level `Result` failures into route-specific declined responses so UI-facing code does not directly deal with Ktor exceptions.

### ViewModels

ViewModels should stay UI-facing:

- `SendScreenViewModel`: nearby devices, discovery controls, outgoing send/request actions.
- `ReceiveScreenViewModel`: incoming request state and receiver decision actions.
- `NavigationViewModel`: Navigation 3 back stack.

Do not turn ViewModels into HTTP servers or clients. Long-lived networking objects should be Koin singletons; ViewModels observe or call them.

## Current Architecture Direction

Preferred direction for this rebuild:

```text
Screen
  renders state
  calls ViewModel functions

ViewModel
  launches coroutines
  exposes UI state
  calls controllers/services

Controllers
  coordinate app flows
  bridge multiple lower-level pieces

Data/remote
  owns Ktor client/server work

Data/platform
  owns Android NSD and platform APIs

Domain models
  describe shared app data and request/response contracts
```

Avoid introducing many abstract layers just because architecture diagrams say so. Add a boundary when it makes ownership clearer.

## Important Development Rules

- Android first.
- Desktop later.
- Keep code small and explainable.
- Do not add security before the simple request/response and file-transfer flow is understood.
- Do not add a database until there is a real need.
- Do not reintroduce old generated sync backend code.
- Do not treat direct text snippets as `.txt` files.
- Discovery should find reachable devices; it should not own transfer logic long-term.
- UI should render state and call ViewModel actions.
- Business/networking logic should stay out of composables.
- Use route-specific DTOs while learning. Avoid generic polymorphic wrappers unless the serialization shape is fully understood.

## Known Current Rough Edges

These are expected in the learning-stage code:

- `RecieveScreen.kt` is misspelled and can be renamed later.
- Some naming may still be provisional.
- Release minification may require Ktor/serialization keep or dontwarn rules as dependencies evolve.
- `Sync360HttpClient` currently uses the first discovered host address.
- `waitForUserDecision()` can suspend forever if the user never chooses; later add timeout/cancel behavior.
- Request state currently supports only `Ping`.
- There is no security yet.
- There is no file transfer yet.
- There is no polished user-facing error model yet.

## Build And Verification

The project owner prefers to run builds manually. Do not run Gradle builds/tests unless explicitly asked.

Useful commands when requested:

```powershell
./gradlew.bat :androidApp:assembleDebug
./gradlew.bat :androidApp:assembleRelease
./gradlew.bat :desktopApp:run
```

## Roadmap

Near-term:

1. Clean current ping request/response wiring.
2. Decide whether ping should stay approval-based or become immediate.
3. Add real send-offer request/response DTOs.
4. Show incoming send offer on Receive screen.
5. Let sender see accepted/declined result.
6. Add direct text object sending.
7. Add one-file transfer.
8. Add progress and result UI.
9. Add multiple selected items.
10. Add desktop support.
11. Add security as a separate learning milestone.

## AI Assistant Instructions

When helping on this project:

1. Read `_CODEX_CONTEXT_DO_NOT_DELETE/memory.md` first if available.
2. Inspect the current repository before answering because this project changes quickly.
3. Do not assume old README/context sections are true if the code says otherwise.
4. Do not run Gradle builds/tests unless the user explicitly asks.
5. Explain exact code paths simply.
6. Clearly separate actual breakage from style improvement.
7. Prefer small steps and learning-oriented explanations.
8. Do not generate large architectures ahead of the user's understanding.
9. If editing docs, keep them aligned with the manual Android-first rebuild.
