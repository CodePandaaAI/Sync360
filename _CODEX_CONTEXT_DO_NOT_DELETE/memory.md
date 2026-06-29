# Project Memory

## Purpose of this file

This file preserves working context for future Codex chats when the previous chat becomes too long or the context window fills up. Future Codex chats should read this file before making changes, then inspect the current repository state because files may have changed after this memory was written.

This memory is context, not absolute truth. If the repository contradicts this file, trust the repository, mention the mismatch, and update this file.

## Project overview

Sync360 is a Kotlin Multiplatform / Compose Multiplatform app for local-network device-to-device sharing. The product direction is a nearby-device drop app, closer to Quick Share than a chat app or permanent device manager.

The current rebuild is Android-first and manual/learning-focused. The user deleted the large AI-generated sync implementation and is rebuilding the app from small understandable slices. The current live slice is Android local-network discovery using Android NSD.

Current intended product flow:

1. User opens on the Send screen.
2. User sees nearby devices on the same local network.
3. User later selects files and/or direct text snippets.
4. User taps a nearby device to send a request.
5. Receiver uses the Receive screen for approval, progress, and result.

Current active platform focus:

- Android first.
- Desktop/JVM later.
- iOS exists in the repo but is not active in the rebuild.

This project is not an Excel/document/PDF/data-entry workspace.

## User and project context

The user is the project owner/developer and is intentionally rebuilding manually to regain ownership of the code. They want to understand local network fundamentals by writing the app in small pieces instead of reading or refactoring a large AI-generated codebase.

Known user goals:

- Learn and own Android NSD/mDNS discovery, TCP/HTTP, host addresses, ports, JSON, local servers, file bytes, and later security.
- Keep the code understandable enough that the user can explain every line.
- Build behavior first, then extract structure only when needed.
- Use AI as teacher/reviewer/reference/debug helper, not as full project architect.
- Avoid over-architecture and vague class names.

The user prefers direct explanations. When they ask code-learning questions, answer concretely and avoid abstract architecture talk unless it directly helps.

## Current folder structure

Important root files/folders:

- `README.md`: old/current project handoff with many stale sections from the previous generated implementation. The top product direction still matters, but current source state is the manual rebuild.
- `_CODEX_CONTEXT_DO_NOT_DELETE/`: Codex context folder. Do not delete, rename, or move.
- `_CODEX_CONTEXT_DO_NOT_DELETE/sync360-rebuild-plan.md`: product and engineering brief for the manual rebuild. This is highly relevant.
- `androidApp/`: Android app entry module.
- `desktopApp/`: JVM desktop app module. Present but not actively rebuilt yet.
- `iosApp/`: iOS app folder exists; not active.
- `shared/`: KMP shared code, Compose UI, design system, domain contracts, and Android actual discovery implementation.
- `gradle/libs.versions.toml`, `settings.gradle.kts`, `build.gradle.kts`, module `build.gradle.kts` files: Gradle/KMP/Compose dependency setup.
- `android-architecture-skill.md`, `context.md`, `KMP GUIDE.md`, `PRIVACY.md`, `STORE_LISTING.md`: project docs from previous phases; may contain useful context but can be stale.

Important current source files:

- `androidApp/src/main/kotlin/com/liftley/sync360/Sync360Application.kt`: starts Koin once at app process startup with `androidContext`.
- `androidApp/src/main/kotlin/com/liftley/sync360/MainActivity.kt`: Android activity; only enables edge-to-edge and renders `Sync360Root()`.
- `androidApp/src/main/AndroidManifest.xml`: declares `Sync360Application`, `MainActivity`, and `INTERNET`.
- `shared/src/commonMain/kotlin/com/liftley/sync360/Sync360Root.kt`: shared Compose root with `Sync360Theme`, Navigation 3 `NavDisplay`, and bottom nav for Send/Receive.
- `shared/src/commonMain/kotlin/com/liftley/sync360/core/di/Koin.kt`: common Koin startup and common app module.
- `shared/src/androidMain/kotlin/com/liftley/sync360/core/di/koin.android.kt`: Android Koin bindings for `NetworkServices` and `LocalDeviceIdentityStore`.
- `shared/src/commonMain/kotlin/com/liftley/sync360/domain/local/DiscoveryStatus.kt`: `Idle`, `Starting`, `Running`, `Stopping`.
- `shared/src/commonMain/kotlin/com/liftley/sync360/domain/local/LocalDeviceIdentityStore.kt`: common interface for stable per-install device UUID.
- `shared/src/androidMain/kotlin/com/liftley/sync360/data/AndroidLocalDeviceIdentityStore.kt`: Android SharedPreferences-backed UUID store.
- `shared/src/commonMain/kotlin/com/liftley/sync360/domain/repository/NetworkServices.kt`: current discovery contract and `NearbyDevice` model.
- `shared/src/androidMain/kotlin/com/liftley/sync360/data/AndroidNetworkServices.kt`: Android NSD advertising/discovery implementation.
- `shared/src/commonMain/kotlin/com/liftley/sync360/presentation/featureSend/SendScreen.kt`: current Send UI showing nearby devices, reload/status card, and a custom loading arc.
- `shared/src/commonMain/kotlin/com/liftley/sync360/presentation/featureReceive/RecieveScreen.kt`: placeholder Receive screen. Note spelling is currently `RecieveScreen.kt`.
- `shared/src/commonMain/kotlin/com/liftley/sync360/presentation/viewmodel/SendScreenViewModel.kt`: starts timed discovery and exposes discovery state.
- `shared/src/commonMain/kotlin/com/liftley/sync360/presentation/viewmodel/NavigationViewModel.kt`: simple Navigation 3 back stack holder.
- `shared/src/commonMain/kotlin/com/liftley/sync360/core/designsystem/`: current theme/colors/type/shapes and Compose icon files.

## Existing scripts/tools/templates

Known tools:

- `gradlew` / `gradlew.bat`: Gradle wrapper.
- `recover.py`: present in root. Purpose not inspected in this update; needs confirmation before use.

Common project commands from docs:

- Android app build: `./gradlew.bat :androidApp:assembleDebug` on Windows.
- Desktop run: `./gradlew :desktopApp:run`.
- Desktop hot reload: `./gradlew :desktopApp:hotRun --auto`.

Important user preference: do not run Gradle builds/tests unless explicitly asked. The user prefers to run builds/tests manually.

## Generated outputs

This repo produces app artifacts:

- Android build outputs under Gradle/Android build directories.
- Desktop JVM build outputs under Gradle build directories.
- Received Sync360 files will eventually be saved by the running app, not generated by the repo itself.

No confirmed document/PDF/spreadsheet/report outputs exist for this project.

## Current workflow

Current app/rebuild startup flow:

1. Android process starts `Sync360Application`.
2. `Sync360Application` calls `initKoin(androidModule) { androidContext(applicationContext) }`.
3. `MainActivity` renders `Sync360Root()`.
4. `Sync360Root` shows Navigation 3 bottom navigation with Send and Receive.
5. `SendScreen` injects `SendScreenViewModel`.
6. `SendScreenViewModel` starts network services on init, waits about 15 seconds, then stops discovery.
7. `AndroidNetworkServices.startNetworkServices()` advertises this device with NSD and starts discovery.
8. Android NSD service registration advertises service type `_sync360._tcp.`, service name `${Build.MODEL} Sync360`, currently hardcoded port `8080`, and TXT attributes `deviceUuid`, `deviceName`, `deviceType`, `protocolVersion`.
9. Android discovery resolves nearby services: API 34+ uses `registerServiceInfoCallback`; older APIs use `resolveService`.
10. Resolved `NsdServiceInfo` maps to `NearbyDevice` only if required TXT attributes exist, host addresses exist, and port is greater than 0.
11. `NearbyDevice` currently includes `id`, `deviceName`, `deviceType`, `protocolVersion`, `hostAddresses: List<String>`, `port`, `serviceName`, and `serviceType`.
12. `SendScreen` renders device rows and discovery status/reload UI.

Discovery model:

- `DiscoveryStatus.Idle`: safe to start/reload.
- `Starting`: discovery was requested.
- `Running`: `onDiscoveryStarted` fired.
- `Stopping`: stop was requested.
- Failures are currently logged and status returns to `Idle`; there is no user-facing `Failed` state.

## Important project rules

Confirmed rules:

- Build Android first. Desktop/iOS later.
- Keep code small and understandable.
- Do not add security first.
- Do not add full clean architecture up front.
- Discovery should only provide reachable nearby devices; it should not own transfer/request/receive logic.
- UI should render state and call ViewModel actions; business/networking logic stays out of composables.
- Direct text snippets are not `.txt` files.
- Selected send items should eventually stay selected after sending until explicitly cleared.
- Do not run Gradle builds/tests unless the user asks.
- Do not hallucinate missing project facts. Use `Unknown` or `Needs confirmation`.
- Do not rewrite large systems without user request; the user is intentionally learning step by step.

Inferred rules:

- Prefer one small vertical slice at a time.
- Prefer explicit names and direct flow over clever abstractions.
- Keep debug UI acceptable during learning, but do not let it become permanent product logic without deciding.
- Use exact code-path explanations when teaching.

## Known preferences

Confirmed:

- The user wants short, direct explanations for code-learning questions.
- The user gets frustrated by vague or overly technical explanations when a simple answer would do.
- The user wants to know whether code is actually wrong/can break versus merely stylistically improvable.
- The user prefers manual coding for ownership, using AI as reviewer/teacher/reference.
- The user likes the current simple Material/Compose style with rounded surfaces, `surfaceContainer`, compact device cards, icon rows, and restrained UI.
- The user wants to preserve theme/fonts/icons/design identity while rebuilding behavior manually.

Inferred:

- Explain networking with practical mental models such as `deviceUuid = identity`, `hostAddresses + port = route`.
- When presenting options, label what is good enough now versus what should be improved later.
- Avoid pushing perfect production lifecycle handling before the current milestone needs it.

## Current status

As of 2026-06-25:

- The old generated sync implementation has been removed in prior commits/working sessions.
- The current rebuild has a working Android-first app shell with Koin, Navigation 3, Send/Receive screens, theme/icons, and Android NSD discovery.
- Android discovery can advertise a device, discover other devices on the same network, map resolved services to `NearbyDevice`, and show nearby devices in the Send screen.
- Discovery uses a timed scan window around 15 seconds and manual reload.
- Discovery status is exposed to UI with `Idle`, `Starting`, `Running`, `Stopping`.
- `NearbyDevice` now includes `hostAddresses: List<String>` and `port`, which are needed for the next request/response milestone.
- Current `AndroidNetworkServices` still has known learning-stage rough edges: port is hardcoded to `8080`; user wants dynamic OS-assigned port later; API 34+ callback map currently uses device id after resolve; `stopDiscoveryServices` stops scan/callbacks but does not unregister the advertised NSD service; repeated/restart lifecycle is simple learning-stage handling.
- Worktree at this update showed uncommitted changes in `AndroidNetworkServices.kt`, `NetworkServices.kt`, and a staged/deleted stale path `shared/src/commonMain/kotlin/com/liftley/sync360/presentation/features/SendScreen.kt`.

Latest intended next milestone:

- Move from discovery to simple request/response: tap nearby device, send a simple offer/request to its `hostAddresses + port`, receiver shows incoming request, receiver accepts/declines, sender sees accepted/declined.
- No file transfer yet.
- No security yet.
- Likely next learning topic: tiny local Ktor HTTP server/client, JSON DTOs, and dynamic port selection.

## Open questions / unknowns

- Unknown: exact final server/control-plane design for the rebuild.
- Needs confirmation: whether to use Ktor HTTP for the first request/response slice, though it is likely.
- Needs confirmation: how to choose primary host from `hostAddresses`; current plan is to store all and prefer IPv4 first later.
- Needs confirmation: dynamic port implementation details. The intended concept is to start the local server on port `0`, read the OS-assigned port, then advertise that port through NSD.
- Unknown: whether additional Android permissions such as `NEARBY_WIFI_DEVICES` are needed for target SDK/device behavior.
- Unknown: whether discovery is fully reliable across the user’s two Android devices after the latest host-address changes.
- Needs confirmation: when to add Desktop discovery and JVM network services. Current plan is after Android behavior is understood.
- Needs confirmation: whether `RecieveScreen.kt` spelling should be corrected now or later.

## Recent decisions

- Rebuild manually from a smaller baseline rather than continuing to refactor generated code.
- Keep AI as teacher/reviewer/reference, not app architect.
- Android-first; Desktop/iOS later.
- Discovery is Milestone 2 and is now nearly complete/good enough.
- Discovery should scan for a limited window instead of running forever to avoid unnecessary battery/network work.
- Advertising can stay on while the app is open; scanning can be manual/timed.
- Keep discovery errors developer/log-only for now; no global user-facing `Failed` state.
- Use `DiscoveryStatus.Idle`, `Starting`, `Running`, `Stopping`.
- Use `deviceUuid` for device identity.
- Use `hostAddresses + port` for communication route.
- Store host addresses as `List<String>` in common model rather than Android/JVM `InetAddress`.
- For Android 14+ use `NsdServiceInfo.hostAddresses`; for older Android use deprecated `host` wrapped as a list.
- If host address list is empty or port is invalid, do not map the service to `NearbyDevice`.

## How future Codex chats should use this file

- Always read `_CODEX_CONTEXT_DO_NOT_DELETE/memory.md` first.
- Then inspect the current folder state.
- Treat the memory file as context, not absolute truth if files have changed.
- If the folder contradicts the memory file, mention the mismatch and update the memory file.
- Keep the memory file updated after major changes.
- Never invent project facts that are not in memory, files, or user instructions.
- Use `Unknown` or `Needs confirmation` for uncertain details.
- Respect the user’s current request type. If they ask only for explanation, do not edit code.

## Change log

- 2026-06-22: Created `_CODEX_CONTEXT_DO_NOT_DELETE/memory.md` from earlier chat context and repository inspection.
- 2026-06-25: Updated memory from current chat and repository inspection. Replaced stale generated-code context with the current manual Android-first rebuild state, including Koin setup, Navigation 3 shell, Android NSD discovery, discovery status, host address mapping, user preferences, and next request/response milestone.
