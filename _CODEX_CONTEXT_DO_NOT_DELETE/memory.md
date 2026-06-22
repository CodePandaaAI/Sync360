# Project Memory

## Purpose of this file

This file preserves working context for future Codex chats when the previous chat becomes too long or the context window fills up. Future Codex chats should read this file before making changes, then inspect the current repository state because files may have changed after this memory was written.

## Project overview

Sync360 is a Kotlin Multiplatform / Compose Multiplatform app for local-network device-to-device sharing. The current product direction is a nearby-device drop app, closer to Quick Share than a Bluetooth-style connected-device UX.

Current intended flow:

1. The app opens on the Send screen.
2. The user builds one outgoing bundle containing real files and/or direct text snippets.
3. Nearby devices on the same local network appear as send targets.
4. The user taps a target and confirms sending.
5. The receiver uses the Receive screen for approval, progress, and received results.

Current supported targets in this repo are Android and JVM Desktop. iOS folders exist, but the live implementation focus is Android + Desktop.

This project is not an Excel/document/PDF generation workspace. The pasted instruction mentioned the user's father, data entry, quality, health, safety, and generated Excel/doc/PDF outputs, but those facts are not supported by this Sync360 repository or this chat. Treat them as not applicable to this project unless the user later confirms otherwise.

## User and work context

The user is the project owner/developer. They are learning the networking/backend concepts while also improving the project by hand. They want the codebase to become understandable, predictable, and stable instead of AI-generated over-architecture.

Known user goals and concerns:

- Understand local network fundamentals: TCP, HTTP, JSON, NSD/mDNS, ports, raw bytes, file bytes, and security tokens.
- Stabilize backend/data/domain layers before relying on UI.
- Keep UI dumb: render current state and send events upward.
- Avoid unnecessary clean-architecture layers when the app flow is simple.
- Prefer simple, direct code whose class/file names say what they do.
- Avoid hallucinated explanations; explain exact code paths.
- User prefers short, direct answers when asking code-learning questions.

## Current folder structure

Important root files/folders:

- `README.md`: current handoff plus older historical product vision. The top sections are more authoritative than older roadmap sections.
- `context.md`: long project handoff and architecture notes. Contains both current and stale/historical sections. The `Current State First` section should win when there is conflict.
- `android-architecture-skill.md`: architecture guidance used by the project.
- `KMP GUIDE.md`: generic Kotlin Multiplatform project guide and run/test commands.
- `PRIVACY.md`: privacy policy draft for Sync360. States local network transfer, no account/cloud/analytics, local install identifier, session-scoped approvals/tokens, and no Sync360 encryption.
- `STORE_LISTING.md`: app store listing draft.
- `androidApp/`: Android app container, manifest, application, `MainActivity`, foreground service, FileProvider config.
- `desktopApp/`: JVM Desktop app container.
- `shared/`: shared KMP code, Compose UI, domain/data/repository/network/platform abstractions.
- `iosApp/`: iOS app folder exists but is not the current active implementation focus.
- `guide/`: project-local planning documents; ignored by git per `.gitignore`.
- `.agents/skills/caveman/` and `skills-main/`: project-local agent skill/tooling paths; ignored by git per `.gitignore`.
- `_CODEX_CONTEXT_DO_NOT_DELETE/`: Codex memory folder. Do not delete or move.

Important shared code areas:

- `shared/src/commonMain/kotlin/com/liftley/sync360/App.kt`: shared Compose app frame, mobile navigation, desktop/mobile split.
- `features/sync/presentation/`: Compose screens, ViewModel, UI state/effects/events.
- `features/sync/domain/`: common domain models, runtime controller, repository interface, transfer controller, local peer discovery interface.
- `features/sync/data/repository/`: repository integration, transfer engine/store, incoming offer decisions, incoming transfer receiver.
- `features/sync/data/network/`: HTTP client/server/control DTOs, raw TCP transport abstraction, outgoing transfer sender, transfer payload store, transfer grants.
- `shared/src/androidMain/...`: Android actual implementations for platform operations, local peer discovery via Android `NsdManager`, raw TCP transport, local device identity, theme, and Android root wiring.
- `shared/src/jvmMain/...`: Desktop actual implementations including JmDNS discovery and JVM raw TCP transport.

## Existing scripts/tools/templates

Known project tooling:

- `gradlew` / `gradlew.bat`: Gradle wrapper.
- `build.gradle.kts`, `settings.gradle.kts`, `gradle/libs.versions.toml`: Gradle/KMP/app dependency configuration.
- `recover.py`: present in root; purpose not inspected in detail during this memory update. Needs confirmation before use.

Common commands from project docs:

- Android app build: `./gradlew :androidApp:assembleDebug` or Windows `./gradlew.bat :androidApp:assembleDebug`.
- Desktop run: `./gradlew :desktopApp:run`.
- Desktop hot reload: `./gradlew :desktopApp:hotRun --auto`.
- Shared Android compile sometimes used during debugging: `./gradlew.bat :shared:compileAndroidMain`.
- JVM shared compile/test compile used during refactor checks: `./gradlew.bat :shared:compileKotlinJvm`, `./gradlew.bat :shared:compileTestKotlinJvm`.

Important user preference: README/context say the project owner prefers to run Gradle builds/tests manually unless explicitly requested. Future Codex chats should not run builds/tests by default.

## Generated outputs

This repo produces application artifacts, not office documents.

Known output types:

- Android APK/AAB outputs under Android/Gradle build directories.
- Desktop JVM application/build outputs under Gradle build directories.
- Received Sync360 files are saved by the running app, not generated by the repo itself:
  - Android: intended `Downloads/Sync360` through platform storage APIs.
  - Desktop: intended `Downloads/Sync360` in the user filesystem.

No confirmed Excel, Word, PDF, report, form, or data-entry generated outputs exist in this Sync360 repo.

## Current workflow

Typical current app workflow:

1. Koin is initialized.
2. `SyncRuntimeController.start()` starts the runtime.
3. Local HTTP server starts through `SyncRepositoryImpl.startSync()`.
4. Local peer discovery advertises the current device and scans for nearby peers.
5. `LocalPeerDiscovery` publishes `List<DeviceProfile>`.
6. `SyncRepositoryImpl` filters local/self addresses and exposes nearby devices.
7. `SyncViewModel` maps repository/runtime state into `SyncUiState`.
8. `SendScreen` displays selected files/text and nearby devices.
9. User selects target device and confirms send.
10. `TransferEngine` and `OutgoingTransferSender` send an HTTP offer/control messages and raw TCP file bytes.
11. Receiver handles offer through incoming offer decisions and `IncomingTransferReceiver`.
12. Raw TCP receive writes bytes through `TransferPayloadStore` and platform operations.
13. Receive UI shows approval/progress/result depending on quick-save and transfer state.

Current intended product rules:

- Nearby devices are send targets, not long-lived connected-device UI.
- Direct text snippets are `SendItem.Text`, not `.txt` files.
- Real `.txt` files remain normal files.
- Selected send items should remain selected after sending until the user explicitly clears/removes them.
- Quick Save OFF: incoming offers require Accept/Decline.
- Quick Save ON: valid offers are accepted automatically.
- Busy receiver states should reject/ignore new incoming offers until idle.
- Transfer errors should be one-time UI events/snackbars where possible instead of large persistent error surfaces.

## Important project rules

Confirmed rules from chat/files:

- UI should render state and emit events; business logic belongs above UI.
- Data/repository/network/platform layers own networking, auth, storage, and transfer orchestration.
- Keep Android APIs out of common composables.
- Use lifecycle-aware state collection at app/screen entry points where applicable.
- Do not reintroduce connection-first/Bluetooth-like UX as the main product flow.
- Do not reintroduce `selectedFiles` as the main send state; use selected `SendItem`s.
- Do not treat direct pasted text as a `.txt` file.
- Do not weaken session security, HMAC validation, session tokens, peer grants, raw TCP transfer token validation, header validation, byte-count validation, or SHA-256 validation without explicit instruction.
- Do not reintroduce WebSockets or base64/protobuf file-byte payloads unless product direction changes.
- Prefer raw TCP for file bytes; HTTP/Ktor is the control plane.
- Avoid unnecessary layers and vague class names.
- Keep code developer-readable before adding more abstraction.
- Do not run builds/tests unless user asks or grants approval.
- Never invent missing project facts. Use `Unknown` or `Needs confirmation`.

Inferred rules:

- Prefer small targeted refactors over sweeping rewrites.
- Prefer direct, simple explanations when the user is learning code.
- The user values exact path/flow explanations more than abstract architecture talk.

## Known preferences

Confirmed:

- The user wants straightforward, minimal explanations for learning questions.
- The user gets frustrated by vague naming and over-layered architecture.
- The user wants backend/domain/data flow stabilized first, then UI skin/state can be simplified.
- The user prefers app stories/boundaries such as discovery/presence vs transfer/request/receive instead of many tiny interfaces/classes with unclear responsibility.
- The user does not want caveman mode currently.
- The user often asks to understand code before changing it; obey when they say not to code.

Inferred:

- Prefer class names like `LocalPeerDiscovery`, `AndroidLocalPeerDiscovery`, `OutgoingTransferSender`, `IncomingTransferReceiver`, and `TransferPayloadStore` because they say what they do.
- Avoid adding diagnostic-only infrastructure unless it directly supports app behavior or a temporary debugging task.

## Current status

As of 2026-06-22:

- The repo currently contains refactored discovery and transfer names such as `LocalPeerDiscovery`, `AndroidLocalPeerDiscovery`, `DesktopLocalPeerDiscovery`, `OutgoingTransferSender`, `IncomingTransferReceiver`, and `TransferPayloadStore`.
- `SyncDiscoveryController`, `NetworkDiscoveryService`, `AndroidDiscoveryService`, `DesktopDiscoveryService`, `SyncDiagnosticLog`, and `TransferDiagnostics` appear to have been removed from the current source tree.
- Current active conversation is focused on reading and understanding `AndroidLocalPeerDiscovery` and the discovery flow, not making new code changes unless asked.
- `git status --short` at memory creation showed one modified file: `shared/src/androidMain/kotlin/com/liftley/sync360/features/sync/data/network/AndroidLocalPeerDiscovery.kt`. The user said they rolled back recent infinite-scanning changes, so future agents should inspect the actual diff before assuming what changed.
- The user is currently asking code-understanding questions around Android NSD discovery, registration, scanning, resolving, multicast locks, shutdown, and state/error modeling.

## Open questions / unknowns

- Unknown: whether Android discovery currently works reliably on two physical devices after the latest local rollback.
- Unknown: exact current desired final structure for discovery error state. User noticed one shared `failure` field is confusing when scan and advertisement can independently succeed/fail, but explicitly said not to change it yet.
- Unknown: whether port `8080` should remain fixed or later become dynamic. User has asked conceptually how to ask the OS for a free port.
- Unknown: whether the Android app should add `NEARBY_WIFI_DEVICES` permission for newer target SDK behavior. This has not been confirmed or implemented in this memory update.
- Needs confirmation: any future shift back to HTTP file-byte upload would conflict with current raw TCP direction.
- Needs confirmation: any Excel/document/PDF/data-entry workflow from the pasted instruction is not present in this repo.

## Recent decisions

- Current product model is nearby-device drop, not connection-first device pairing.
- Discovery/presence should be a clean independent component that outputs nearby `DeviceProfile`s.
- Transfer/request/receive should be separate from discovery.
- Direct text snippets and files share one send bundle UI/data surface, but text snippets are separate `SendItem.Text` objects rather than generated `.txt` files.
- User prefers manually learning the backend from the bottom up instead of asking AI to keep refactoring blindly.
- One universal discovery `failure` field is currently understood as simple but potentially confusing; splitting scan/advertisement errors is a possible later improvement, not a current task.
- `shutdown()` in discovery means permanently close this discovery instance, while `stopScan()` / `stopAdvertising()` are temporary lifecycle operations.

## How future Codex chats should use this file

- Always read `_CODEX_CONTEXT_DO_NOT_DELETE/memory.md` first.
- Then inspect the current folder state.
- Treat the memory file as context, not absolute truth if files have changed.
- If the folder contradicts the memory file, mention the mismatch and update the memory file.
- Keep the memory file updated after major changes.
- Never invent project facts that are not in memory, files, or user instructions.
- Use `Unknown` or `Needs confirmation` for uncertain details.
- Respect the user's current request type. If they ask only for explanation, do not edit code.

## Change log

- 2026-06-22: Created `_CODEX_CONTEXT_DO_NOT_DELETE/memory.md` from current chat context and repository inspection. Also noted that the pasted instruction's data-entry/Excel/PDF/father context is not supported by this Sync360 repository and should not be treated as true for this project without confirmation.
