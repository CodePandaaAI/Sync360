# Project Memory

## Purpose of this file

This file preserves working context for future Codex chats when the previous chat becomes too long or the context window fills up. Future Codex chats should read this file before making changes, then inspect the current repository state because files may have changed after this memory was written.

This memory is context, not absolute truth. If the repository contradicts this file, trust the repository, mention the mismatch, and update this file.

## Project overview

Sync360 is a Kotlin Multiplatform / Compose Multiplatform app for local-network device-to-device sharing. The product direction is a nearby-device drop app, closer to Quick Share than a chat app, clipboard history app, or permanent device manager.

The project is currently a manual Android-first rebuild. The user deleted/replaced the older large AI-generated sync implementation because they wanted to understand and own the networking, app state, dependency wiring, and platform behavior line by line.

Current intended product flow:

1. User opens the app on nearby devices.
2. Devices advertise/discover each other on the same local network.
3. Sender sees nearby devices on the Send screen.
4. Sender eventually selects files and/or direct text snippets.
5. Sender taps a target device.
6. Receiver sees an incoming request on the Receive screen.
7. Receiver accepts or declines.
8. Transfer/text send happens only after approval.

Current active platform focus:

- Android first.
- Desktop/JVM module exists but the rebuilt networking flow is not active there yet.
- iOS folder exists but is not active.

This repository is also being prepared for public/open-source presentation and build-in-public work.

## User and project context

The user is the project owner/developer. They are rebuilding Sync360 manually to learn the underlying concepts rather than continuing with a large generated codebase.

Confirmed user goals:

- Learn Android NSD/mDNS discovery, host addresses, ports, Ktor HTTP server/client, JSON DTOs, coroutines, StateFlow, Koin wiring, file bytes, storage, and later security.
- Keep code understandable enough that the user can explain every line.
- Build one small working slice at a time.
- Add structure only when it clarifies real ownership or removes real duplication.
- Use AI as teacher, reviewer, reference, and small helper, not as blind project architect.
- Make the repo public/open-source and present it as a serious but honest early-stage product.

Working style:

- The user prefers direct, concrete explanations.
- The user wants to know whether something can actually break versus being only style.
- The user often asks conceptual questions before coding.
- The user prefers to code manually and use Codex to explain, review, summarize diffs, write commit messages, and prepare docs.
- The user asked not to run Gradle builds/tests unless explicitly requested.

## Current folder structure

Important root files/folders:

- `README.md`: public-facing project README. It has been rewritten toward open-source/product presentation. Current uncommitted state references `screenshots/sync360-icon.png` and `screenshots/hero-demo.gif`.
- `context.md`: current project handoff for developers/AI; rewritten from older generated-code era context.
- `_CODEX_CONTEXT_DO_NOT_DELETE/`: Codex context folder. Do not delete, rename, or move.
- `_CODEX_CONTEXT_DO_NOT_DELETE/memory.md`: this memory file.
- `_CODEX_CONTEXT_DO_NOT_DELETE/README.md`: explains the context folder.
- `androidApp/`: Android app module with `Sync360Application`, `MainActivity`, manifest, Gradle config, and Android resources.
- `shared/`: KMP shared module containing Compose UI, ViewModels, design system, domain models, Koin common module, Ktor client/server prototype, and Android-specific discovery implementation in source sets.
- `desktopApp/`: JVM desktop module/shell. Present, but not active for the rebuilt networking milestone.
- `iosApp/`: iOS app folder exists; not active.
- `docs/`: open-source docs folder may exist depending on current branch/worktree. Recently created docs included `ARCHITECTURE.md`, `ROADMAP.md`, `DEVELOPMENT.md`, `OPEN_SOURCE_NOTES.md`.
- `screenshots/`: public visual assets folder. Current untracked assets include `hero-demo.gif` and `sync360-icon.png` in the latest observed worktree.
- `gradle/libs.versions.toml`: dependency/version catalog.
- `settings.gradle.kts`: includes `:androidApp`, `:desktopApp`, `:shared`.
- `gradlew`, `gradlew.bat`: Gradle wrapper.
- `android-architecture-skill.md`, `KMP GUIDE.md`, `PRIVACY.md`, `STORE_LISTING.md`: older/supporting docs; may be partially stale.

Important current source files:

- `androidApp/src/main/kotlin/com/liftley/sync360/Sync360Application.kt`: starts Koin once with Android context.
- `androidApp/src/main/kotlin/com/liftley/sync360/MainActivity.kt`: Android activity, enables edge-to-edge, renders `Sync360Root()`.
- `shared/src/commonMain/kotlin/com/liftley/sync360/Sync360Root.kt`: shared Compose root, Navigation 3 Send/Receive shell, observes receive server state and navigates to Receive when incoming request state becomes busy.
- `shared/src/commonMain/kotlin/com/liftley/sync360/core/di/Koin.kt`: common Koin module.
- `shared/src/androidMain/kotlin/com/liftley/sync360/core/di/koin.android.kt`: Android Koin module.
- `shared/src/commonMain/kotlin/com/liftley/sync360/data/NetworkServicesController.kt`: starts Ktor server, passes dynamic port to discovery, keeps discovery scan alive for about 30 seconds, and controls restart/stop flow.
- `shared/src/androidMain/kotlin/com/liftley/sync360/data/AndroidNetworkServices.kt`: Android NSD advertisement/discovery implementation.
- `shared/src/commonMain/kotlin/com/liftley/sync360/data/remote/Sync360HttpServer.kt`: embedded Ktor CIO server with `GET /sync360/ping`.
- `shared/src/commonMain/kotlin/com/liftley/sync360/data/remote/Sync360HttpClient.kt`: Ktor CIO client that calls nearby device `/sync360/ping`.
- `shared/src/commonMain/kotlin/com/liftley/sync360/data/remote/IncomingServerRequestsController.kt`: bridges incoming server request state to Receive UI and waits for user decision with `CompletableDeferred<UserDecision>`.
- `shared/src/commonMain/kotlin/com/liftley/sync360/data/remote/OutgoingRequestsController.kt`: wraps outgoing ping request and maps client failures to declined response.
- `shared/src/commonMain/kotlin/com/liftley/sync360/domain/repository/NetworkServices.kt`: common discovery contract. Current `startNetworkServices(httpServerPort: Int)` receives runtime port from controller.
- `shared/src/commonMain/kotlin/com/liftley/sync360/domain/model/NearbyDevice.kt`: nearby device model with `hostAddresses` and `port`.
- `shared/src/commonMain/kotlin/com/liftley/sync360/domain/model/DiscoveryStatus.kt`: `Idle`, `Starting`, `Running`, `Stopping`.
- `shared/src/commonMain/kotlin/com/liftley/sync360/domain/model/ServerState.kt`: `Idle`, `Busy(RequestType)`, `RequestType.Ping`, `UserDecision`.
- `shared/src/commonMain/kotlin/com/liftley/sync360/domain/remote/response/PingResponse.kt`: route-specific `PingRequestResponse` sealed response and `PingResponse` DTO.
- `shared/src/commonMain/kotlin/com/liftley/sync360/presentation/viewmodel/SendScreenViewModel.kt`: starts network services through controller, exposes nearby devices/discovery status, experimental send UI state, and outgoing ping action.
- `shared/src/commonMain/kotlin/com/liftley/sync360/presentation/viewmodel/ReceiveScreenViewModel.kt`: exposes incoming server state and completes user decision.
- `shared/src/commonMain/kotlin/com/liftley/sync360/presentation/featureSend/SendScreen.kt`: Send UI, nearby devices, reload, experimental request status card.
- `shared/src/commonMain/kotlin/com/liftley/sync360/presentation/featureReceive/RecieveScreen.kt`: Receive UI, idle state, Accept/Decline for busy server state. Note spelling: `RecieveScreen.kt` is currently misspelled.
- `shared/src/commonMain/kotlin/com/liftley/sync360/presentation/presentationComponents/Sync360Surface.kt`: shared rounded surface component. This was renamed from `brandComponents` in earlier work.
- `shared/src/commonMain/kotlin/com/liftley/sync360/core/designsystem/icons/`: custom Compose vector icons including Send, Receive, Reload, Android, Close, Emoji.

## Existing scripts/tools/templates

Known tools:

- `gradlew` / `gradlew.bat`: Gradle wrapper for builds/runs.
  - Android debug build: `./gradlew.bat :androidApp:assembleDebug` on Windows or `./gradlew :androidApp:assembleDebug` on macOS/Linux.
  - Android release build: `./gradlew.bat :androidApp:assembleRelease` when requested.
  - Desktop shell run: `./gradlew :desktopApp:run`.
- `recover.py`: present in root. Purpose has not been confirmed in current context; inspect before using.

Project docs/templates:

- `README.md`: public repository landing/documentation.
- `context.md`: developer/AI handoff.
- `docs/` files if present: architecture, roadmap, development, open-source notes.
- `screenshots/README.md` if present: screenshot/demo asset guidance.

No data-processing, office-document generation, or report-generation workflow is part of Sync360.

## Generated outputs

This project produces app/build artifacts and public repository assets:

- Android build outputs under `androidApp/build/`.
- Shared module build outputs under `shared/build/`.
- Desktop build/run outputs under `desktopApp/build/`.
- Open-source presentation assets under `screenshots/`, currently including/expecting:
  - `screenshots/sync360-icon.png`
  - `screenshots/hero-demo.gif`
  - planned screenshots such as `android-send.png`, `android-receive-request.png`, `architecture-preview.png`.
- Future received Sync360 files will eventually be saved by the running app, but file transfer is not implemented yet in the rebuilt flow.

Known current visual asset:

- A GIF from `C:\Users\4444444\Downloads\Local Device Sharing App.gif` was copied into `screenshots/hero-demo.gif` in the latest observed worktree.
- A rounded icon was generated from `shared/src/commonMain/composeResources/drawable/app_icon.png` into `screenshots/sync360-icon.png` in the latest observed worktree.

## Current workflow

Current app/rebuild runtime flow:

1. Android process starts `Sync360Application`.
2. Koin starts with common and Android modules.
3. `MainActivity` renders `Sync360Root()`.
4. `Sync360Root` shows Navigation 3 bottom navigation with Send and Receive.
5. `Sync360Root` observes `ReceiveScreenViewModel.serverState`; if state becomes `ServerState.Busy`, it navigates to Receive screen.
6. `SendScreenViewModel` starts `NetworkServicesController.startNetworkServices()` in `viewModelScope`.
7. `NetworkServicesController` starts `Sync360HttpServer`.
8. Ktor server binds `host = "0.0.0.0"`, `port = 0`; OS assigns real port.
9. `NetworkServicesController` passes that runtime port into `NetworkServices.startNetworkServices(port)`.
10. `AndroidNetworkServices` creates `NsdServiceInfo` with the supplied port, device UUID, cleaned device name, device type, and protocol version.
11. Android NSD advertises and discovers `_sync360._tcp.` services.
12. Discovered services are resolved into `NearbyDevice(hostAddresses, port, ...)` by `toNearbyDeviceAndroidImpl()`.
13. `NetworkServicesController` allows discovery scan for about 30 seconds, then stops discovery scan.
14. `SendScreen` shows nearby devices and experimental send status.
15. When user taps a nearby device, current experimental flow calls `OutgoingRequestsController.sendPingRequestToDevice(device)`.
16. `Sync360HttpClient` builds `http://{firstHostAddress}:{port}/sync360/ping` and parses `PingRequestResponse`.
17. Receiver `Sync360HttpServer` route sets `ServerState.Busy(RequestType.Ping)` and waits for user decision via `IncomingServerRequestsController`.
18. `ReceiveScreen` shows Accept/Decline; user action completes `UserDecision`.
19. Server responds with `PingRequestResponse.Accepted(PingResponse(...))` or `Declined(reason)`.
20. Sender updates experimental `SendScreenUiState` to `Sent` or `NotSent`.

## Important project rules

Confirmed rules:

- Do not run Gradle builds/tests unless the user explicitly asks.
- Android first; Desktop later.
- Keep code small and understandable.
- Do not reintroduce old generated sync backend code.
- Do not add security before the basic local request/response and file-transfer flow is understood.
- Do not add database/persistence until there is a real need.
- Discovery should find reachable nearby devices; it should not own transfer logic long-term.
- UI should render state and call ViewModel actions; business/networking logic stays out of composables.
- Koin should provide stable object dependencies, not runtime values such as OS-assigned ports. Runtime values should be passed as function parameters.
- Use route-specific DTOs while learning; avoid generic polymorphic response wrappers until serialization shape is fully understood.
- Direct text snippets are not `.txt` files.
- If information is missing, mark it `Unknown` or `Needs confirmation`; do not invent.
- Preserve `_CODEX_CONTEXT_DO_NOT_DELETE/` and keep it with the project.

Inferred rules:

- Prefer one small vertical slice at a time.
- Prefer explicit names and direct flow over clever abstractions.
- Keep debug/prototype UI acceptable during learning, but label it experimental if not final.
- Split actual product/foundation changes from experimental UI/state changes when committing.
- For public docs, be ambitious but honest; do not claim file transfer/security/desktop support as done.

## Known preferences

Confirmed:

- User wants short, direct explanations for learning questions.
- User wants exact code paths and mental models, not vague architecture talk.
- User wants to know whether code is actually wrong/can break versus only style.
- User prefers manual coding for ownership.
- User likes restrained Compose UI with `surfaceContainer`, rounded surfaces, compact cards, icons, and simple Material styling.
- User is open-sourcing/building in public and wants README/docs to feel polished, modern, honest, and serious.
- User wants commit messages and PR descriptions to be detailed, structured, and clean.

Inferred:

- Explain networking with practical mental models such as `deviceUuid = identity` and `hostAddresses + port = route`.
- Avoid pushing final architecture too early.
- Prefer names that describe ownership clearly, e.g. controllers/coordinators for bridge objects.
- Keep README/public claims aligned to current code.

## Current status

As of 2026-06-29:

Completed/working in the rebuild:

- Android app startup and Koin wiring.
- Navigation 3 Send/Receive shell.
- Android NSD advertisement/discovery.
- Stable Android install UUID.
- `NearbyDevice` includes `hostAddresses` and `port`.
- Ktor HTTP server starts on dynamic OS-assigned port.
- Dynamic server port is passed from `NetworkServicesController` into `AndroidNetworkServices`; discovery no longer needs the full HTTP server dependency.
- Ktor HTTP client can call discovered device `/sync360/ping`.
- Receiver can accept/decline incoming ping request in experimental flow.
- Sender has experimental `SendScreenUiState` for Idle/Sending/Sent/NotSent.
- Public README/open-source docs work has been started/updated.
- README currently references rounded icon and Android-to-Android demo GIF assets.

Not built yet:

- Real send-offer route/DTO separate from ping.
- File picker and selected item list.
- Direct text snippet sending.
- File byte transfer.
- Transfer progress/result UI.
- Android file save/storage flow.
- Desktop discovery/request support in the rebuilt flow.
- Security/session validation/encryption.
- Timeout/cancellation for pending receiver decisions.

Current worktree at latest inspection:

- `README.md` modified.
- `screenshots/hero-demo.gif` untracked.
- `screenshots/sync360-icon.png` untracked.
- Earlier code changes around icons/UI state may have been committed or may vary by branch; always inspect `git status`.

## Open questions / unknowns

- Unknown: final name/package boundaries for `NetworkServices`, `NetworkServicesController`, and request controllers.
- Unknown: whether ping should remain receiver-approved or become immediate once real send-offer route exists.
- Needs confirmation: final host address selection strategy; current client uses first host address, but IPv4-first/IPv6 formatting is likely needed.
- Needs confirmation: final Android discovery lifecycle, including whether/when advertisement should be unregistered.
- Needs confirmation: whether `RecieveScreen.kt` spelling should be fixed now or later.
- Needs confirmation: open-source contact links/email for README/SECURITY.
- Needs confirmation: whether license should remain Apache 2.0 or switch to MIT; user asked about both, no final change confirmed after Apache was added in docs work.
- Unknown: if current public documentation files (`LICENSE`, `CONTRIBUTING.md`, `CODE_OF_CONDUCT.md`, `SECURITY.md`, `CHANGELOG.md`, `docs/`) are committed in the current branch; inspect worktree.

## Recent decisions

- Rebuild branch/recreate branch started from main, so merging recreate into main is acceptable when ready because history is shared; it applies rebuild changes rather than combining unrelated roots.
- Old AI-generated implementation should be treated as history/reference, not active foundation.
- README and public docs should describe current Android-first rebuild honestly and not hallucinate file transfer/security as complete.
- Apache 2.0 license was selected for open-source docs unless user later changes to MIT.
- Koin should not provide runtime values like HTTP server port; controller should start server and pass port into discovery.
- `AndroidNetworkServices` should depend only on `LocalDeviceIdentityStore` and receive `httpServerPort` in `startNetworkServices(port)`.
- `NetworkServicesController` owns startup order: start HTTP server, get port, start discovery with port.
- Android NSD guards should protect invalid duplicate calls: start/restart only from `Idle`, stop only from `Running`.
- The Send request status UI is experimental and should be treated separately from permanent foundation changes.
- Public README should use repo-local visual assets rather than external image URLs.

## How future Codex chats should use this file

- Always read `_CODEX_CONTEXT_DO_NOT_DELETE/memory.md` first.
- Then inspect the current folder state.
- Treat the memory file as context, not absolute truth if files have changed.
- If the folder contradicts the memory file, mention the mismatch and update the memory file.
- Keep the memory file updated after major changes.
- Never invent project facts that are not in memory, files, or user instructions.
- Use `Unknown` or `Needs confirmation` for uncertain details.
- Respect the user's current request type. If they ask only for explanation/review/docs, do not edit app code.
- Do not run Gradle builds/tests unless the user explicitly asks.

## Change log

- 2026-06-22: Created `_CODEX_CONTEXT_DO_NOT_DELETE/memory.md` from earlier chat context and repository inspection.
- 2026-06-25: Updated memory from current chat and repository inspection. Replaced stale generated-code context with the manual Android-first rebuild state at that time.
- 2026-06-29: Updated memory from current chat context and repository inspection. Captured current Android-first rebuild state with Ktor ping request/response, dynamic port handoff, incoming/outgoing request controllers, experimental Send/Receive UI state, open-source docs/README direction, screenshot assets, user preferences, and current unknowns.
