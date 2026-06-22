# Sync360 - Complete Project Context & Handover Guide

## Current State First - Read This Before Editing

## Authoritative Handoff - June 17, 2026

This section supersedes older notes below when there is any conflict.

Sync360 is now moving to a **nearby-device drop app** model, not a Bluetooth-style "connect to one device" model.

### Current Product Model

* The user starts on the **Send** screen.
* The app has two primary screens:
  * `SendScreen`: compose a bundle and choose a nearby target.
  * `ReceiveScreen`: approve/observe incoming receives and show received results.
* Navigation uses **Navigation 3**:
  * `SyncRoute.Send`
  * `SyncRoute.Receive`
  * `SyncNavigationViewModel` owns the state-backed back stack.
  * Mobile starts at Send and automatically navigates to Receive for incoming offer/progress/result state.
  * Desktop uses `DesktopDashboard` to show Send and Receive side by side.
* Nearby devices are **send targets**, not user-facing sessions.
* There should be no primary "connected device" UX, no disconnect button in the main flow, and no connect-first requirement before the user can choose a target.
* Internal peer grants/session tokens can still exist for authentication and route caching. They are implementation details, not product UX.

### Send Model

Outgoing content is a mixed bundle of `SendItem`s:

* `SendItem.File(PickedFile)` for real files.
* `SendItem.Text(...)` for direct text snippets.

Important: a direct text snippet is **not** a `.txt` file. It is sent as Sync360 text content using `SYNC360_TEXT_MIME_TYPE` and should be received into the app text/clipboard list. A real `.txt` file remains a normal file.

UI state now uses:

* `SyncUiState.selectedItems: List<SendItem>`
* `SyncEvent.AddSelectedFiles`
* `SyncEvent.AddCustomText`
* `SyncEvent.SendSelectedItems`
* `SyncEvent.SendSelectedItemsTo`
* `SyncEvent.ClearSelectedItems`

The old `selectedFiles` naming should not be reintroduced.

### Receive / Quick Save Model

Quick Save controls receive approval:

* Quick Save OFF: incoming file/text offers create a pending incoming offer and wait for Accept/Decline.
* Quick Save ON: valid offers from authenticated nearby peers are accepted automatically.
* Invalid unsigned offers must still be rejected.
* File receive starts raw TCP only after the offer is accepted.
* Incoming text accepted through Quick Save or approval is saved through `MessageEngine.saveReceivedText`.

### Current Network / Transport Model

Ktor HTTP remains the **control plane**:

* discovery-related control
* connect/grant request/accept/reject compatibility endpoints
* text offers/messages
* file offers
* file-complete/status/error signals

Raw TCP remains the **file byte plane**:

* dynamic receiver TCP port
* one-time raw transfer token
* `RawTransferGrantStore`
* `RawTcpFileTransport`
* transfer header validation
* content length and SHA-256 validation

Do **not** reintroduce HTTP file-byte upload routes or base64 file-byte payloads. HTTP file bodies mentioned in older sections are historical/stale.

### Security Rules

Do not weaken:

* HMAC request validation
* `sessionToken` validation
* peer grant / route / token lookup
* raw TCP transfer token validation
* raw TCP header, length, and hash validation

If a target has no valid grant/route/session token, target send must fail clearly instead of bypassing auth.

### Current Key Files

* `shared/src/commonMain/kotlin/com/liftley/sync360/App.kt`: Navigation 3 app shell and desktop split-pane decision.
* `features/sync/presentation/SendScreen.kt`: main send surface, text/file bundle composer, nearby target list.
* `features/sync/presentation/ReceiveScreen.kt`: receive approval/progress/result surface.
* `features/sync/presentation/SyncViewModel.kt`: UDF event handling and UI-state mapping.
* `features/sync/presentation/SyncUiState.kt`: single UI render state; includes `selectedItems`.
* `features/sync/domain/model/FileTransferModels.kt`: `SendItem`, `PickedFile`, transfer preview/result models.
* `features/sync/data/repository/TransferEngine.kt`: target/item-based transfer orchestration.
* `features/sync/data/network/OutgoingFileTransferCoordinator.kt`: file/text-item offer, auth, raw TCP send orchestration.
* `features/sync/data/network/FileTransferManager.kt`: hashing, incoming writes, text-content bridge, raw TCP upload helper.
* `features/sync/data/network/RawTcpFileTransport.kt` plus Android/JVM actuals: raw byte transport.
* `features/sync/data/repository/SyncRepositoryImpl.kt`: runtime integration and incoming offer decisions.

### Architecture Rules For Future AI

Follow `android-architecture-skill.md`:

* UI renders `SyncUiState` and emits `SyncEvent`.
* ViewModel handles UI logic and calls domain/repository/controller APIs.
* Data/repository layer owns networking, auth, storage, transfer orchestration, and business rules.
* Use `collectAsStateWithLifecycle()` in Compose app/screen entry points where available.
* Keep platform APIs behind platform/data abstractions; do not call Android APIs from composables.

### Working Preference

The project owner has explicitly asked the AI assistant **not to run Gradle tests or builds** unless later requested. The owner will run builds and paste errors. Prefer focused code edits plus static searches.

The older sections below still describe the product vision and many important platform quirks, but this section reflects the current implementation direction.

Last updated: June 15, 2026.

Sync360 is currently an Android + JVM Desktop KMP/CMP app. The current engineering goal is to preserve its simple connect-share-leave behavior while debugging, modularizing, removing duplicate logic, and separating responsibilities cleanly across presentation/domain/data/platform layers. The UI has now been deliberately aligned with the Habitrek project's Compose style: `surfaceContainer` backgrounds, reusable rounded surfaces, compact title/device pills, simpler hierarchy, fewer nested panels, and shared shape defaults.

### Known Issues

1. **File transport:** Measurements isolated Ktor CIO HTTP body streaming as the large-file bottleneck. Ktor HTTP now carries control messages only; raw TCP carries file bytes using a dynamic receiver port and one-time transfer grant.

Current transfer performance work already present:

* Raw HTTP file bodies remain the transport.
* Sender and receiver use reusable `1 MiB` buffers.
* Chunk callbacks use offset/length instead of copying each chunk.
* Receiver uses one idle-activity watchdog instead of recreating a timeout coroutine per chunk.
* Fixed completion delays were removed.
* SHA-256 integrity and byte-count verification remain enabled.

### Product Philosophy

Sync360 should behave closer to Quick Share than a chat or device-management app:

* Every app relaunch is a fresh start.
* Devices are approved for the current runtime session only.
* Text/file activity can live in memory while connected, but should not become persisted conversation history.
* The app should work hard to keep the current session alive while it remains in memory, especially during active file transfer.
* If the user removes/kills the app or explicitly leaves after work is done, the session can disappear.
* Transfer failures are session-only UI state. They should be visible to the user, but not persisted.
* Device approval happens once for the current session. Approved peers can then send text and files without a separate accept/decline dialog for each file.

### Current Network Model

The app currently uses an HTTP-only local LAN model:

* **Discovery:** Android uses `NsdManager`; Desktop uses `JmDNS`.
* **Manual IP fallback:** Users can manually enter a peer IP. It follows the same connect approval/session-token flow as discovered devices.
* **Control channel:** Ktor HTTP JSON endpoints carry connection request/accept/reject, explicit text messages, file offers, and transfer-complete signals.
* **File byte channel:** File bytes should stream over raw HTTP. The current implementation pushes files to the receiver's embedded Ktor route `POST /api/file/upload/{offerId}/{fileIndex}`.
* **Serialization:** Small control payloads use JSON. This is not where performance matters. The large file bytes must not be JSON/base64/protobuf payloads; they should be raw HTTP bytes.
* **Session approval model:** Approve stable device identity for the current runtime session, not permanent history. An IP address is only the current route for an approved device.
* **Session gate:** Connection requests are the only expected unauthenticated entry point. Text, file offer, file complete, and upload routes should only proceed for approved session devices/current transfer sessions. The current implementation uses an in-memory session token exchanged during connection approval and cleared with the app/session.
* **Request authentication:** Session control requests and file upload headers are HMAC-SHA256 signed with timestamp and nonce replay checks. This is session-scoped protection, not permanent account/device identity.
* **Android foreground reliability:** Active transfers call `PlatformOperations.startTransferService()`. Idle approved sessions do not own the foreground service or wake lock.
* **No WebSocket/protobuf transport:** JSON is used only for small control DTOs, while file bodies are raw HTTP bytes. Do not reintroduce base64, protobuf file envelopes, or WebSocket file chunks.

### Current File Transfer Flow

1. Sender builds a JSON `FileOfferDto` containing `offerId` and file metadata.
2. Sender posts the offer to the receiver's HTTP server.
3. If the sender belongs to the approved current session, the receiver automatically initializes receiving progress. There is no per-file confirmation UI.
4. Receiver opens platform storage handles as uploads arrive.
5. Sender streams each selected file as raw bytes to `POST /api/file/upload/{offerId}/{fileIndex}`.
6. Receiver writes bytes directly:
   * Android: `MediaStore.Downloads`, `Downloads/Sync360`, `IS_PENDING = 1` during write, `IS_PENDING = 0` after finalization.
   * Desktop: `Downloads/Sync360` in the user filesystem.
7. Receiver reports a saved batch only after every file is finalized and saved paths exist.
8. Sender sends an HTTP file-complete signal after upload success.

Progress must be byte-based for HTTP transfers: bytes successfully written divided by total bytes. Do not report `100%` just because all chunks/messages were seen; final storage write/finalization must succeed.

### Known Current Pitfalls

* Slow transfer throughput is issue #1. A completed transfer does not prove the byte pipeline is efficient. Add stage timing before changing buffer sizes, hashing, storage, or Ktor code again.
* If both sides stay at `1%`, the file offer likely arrived but the HTTP byte stream did not start or the receiver could not open/write storage. Check the reachable peer host, route `/api/file/upload/{offerId}/{fileIndex}`, and whether `beginFileWrite()` returned a handle.
* A device's self-reported local IP can be wrong or unreachable from the peer. Requests should prefer the peer host learned from discovery/connection approval.
* Android files may not appear in Downloads if `finishFileWrite()` fails or `IS_PENDING` is not cleared. Treat `finishFileWrite() == null` as transfer failure.
* Do not disconnect peers from `SyncViewModel.onCleared()`. Android can recreate UI under memory/config pressure; disconnect should be explicit user/app shutdown behavior.
* Avoid repeated Gradle/build runs during AI edits unless the user explicitly asks. The user prefers to run builds and paste errors.

### Current Preferred Architecture

```text
NSD/JmDNS discovery
HTTP JSON control endpoints
HTTP raw upload streaming for file bytes
Session-approved device identity with IP as a current route
Android MediaStore / Desktop filesystem for final storage
```

### Current Internal Structure

* `SyncRepositoryImpl` is the runtime/HTTP integration facade.
* `ConnectionEngine` owns incoming and outgoing connection orchestration, approval, session changes, manual endpoint parsing, and connection validation.
* `MessageEngine` owns session text sending, receiving, validation, notification, and temporary message state.
* `TransferEngine` owns transfer orchestration, transfer state, cancellation, idle timeout monitoring, and foreground-service lifetime.
* `DeviceSessionStore` owns active, incoming-pending, and outgoing-pending connection state.
* `DeviceRegistry` owns approved session devices, current peer hosts, and in-memory session tokens.
* `SessionAuthenticator` owns signing, verification, timestamp/nonce replay protection, and session-token generation.
* `SessionTextStore` holds temporary text for the current runtime. It is not a conversation database.
* `IncomingFileTransferCoordinator` owns receiver offer/upload/finalization orchestration.
* `OutgoingFileTransferCoordinator` owns sender offer/upload/complete orchestration.
* `FileTransferManager` owns byte progress, SHA-256 verification, and platform streaming handles.
* `SyncUiState` is durable render state; `SyncUiEffect` is a one-shot effect stream. Snackbars and desktop feedback are sent through a buffered `Channel`, not stored as `userMessage`.
* `SyncNavigationViewModel` owns a state-backed `List<SyncRoute>` back stack. This follows the Navigation 3 model where the app owns navigation state. Only `SyncRoute.Home` exists today, so `NavDisplay` is not yet needed.
* `Sync360Surfaces.kt` contains Habitrek-inspired shared surface, title-pill, and icon-button primitives. `AppShapes` is applied by both Android and JVM themes.

### Presentation Rules

* Keep screen-specific behavior in `SyncViewModel`; keep navigation state in `SyncNavigationViewModel`.
* Do not put transient snackbar messages in `SyncUiState`.
* Do not create separate mobile and desktop copies of the same message event. Both consume `SyncUiEffect`.
* Prefer shared `Sync360Surface`/theme shapes and simple full-width sections over deeply nested cards.
* Keep current connection, transfer, and storage behavior unchanged when adjusting UI.

Welcome to **Sync360**! This document serves as a comprehensive system summary, design record, and architectural guide. It captures the entire context of the project so that any new developer or AI assistant can immediately assume command, understand what has been built, and continue the implementation seamlessly.

> The sections below this point include historical design notes and resolved experiments. When they conflict with **Current State First**, the current-state section is authoritative.

---

## 📖 1. Project Overview & Mission
**Sync360** is a modern, premium, bidirectional peer-to-peer IoT synchronization system (akin to Apple Continuity, Xiaomi HyperConnect, or Google Quick Share) designed to bridge **Android Mobile** and **JVM Desktop** (PC) clients.

### Core Features
- **Symmetric Peer Presence**: Both Android and Desktop advertise their presence and discover each other concurrently.
- **Connection Approval**: Discovered or manually entered devices request approval once for the current runtime session.
- **Focused Text Sharing**: Text is explicitly sent between approved peers and retained only for the current session.
- **Habitrek-Inspired Material 3 Design**: Android and Desktop use shared rounded surfaces, compact selector/title pills, simple full-width sections, and restrained nesting.

---

## 🛠️ 2. Architectural Design

Sync360 is built using **Kotlin Multiplatform (KMP)** and **Compose Multiplatform**. It adheres to **MVVM / Clean Architecture / Unidirectional Data Flow (UDF)** principles.

### Directory Structure & Map
- [`/shared/src/commonMain/`](file:///c:/Users/4444444/IdeaProjects/Sync360/shared/src/commonMain/kotlin/com/liftley/sync360/): Core application flow, domain models, ViewModels, and Compose UI layouts.
  - [`features/sync/presentation/SyncViewModel.kt`](file:///c:/Users/4444444/IdeaProjects/Sync360/shared/src/commonMain/kotlin/com/liftley/sync360/features/sync/presentation/SyncViewModel.kt): Screen-specific state and event handling for discovery, connections, text sharing, file selection, progress, and one-shot UI effects.
  - [`features/sync/presentation/SyncUiState.kt`](file:///c:/Users/4444444/IdeaProjects/Sync360/shared/src/commonMain/kotlin/com/liftley/sync360/features/sync/presentation/SyncUiState.kt): Single source of truth state for UI rendering.
  - `features/sync/presentation/SyncUiEffect.kt`: One-shot snackbar/feedback effects backed by a buffered channel in `SyncViewModel`.
  - [`features/sync/presentation/SyncEvent.kt`](file:///c:/Users/4444444/IdeaProjects/Sync360/shared/src/commonMain/kotlin/com/liftley/sync360/features/sync/presentation/SyncEvent.kt): Sealed class definitions for unidirectional UI events.
  - `features/sync/presentation/navigation/`: `SyncRoute` and the separate `SyncNavigationViewModel` state-owned back stack.
  - `features/sync/presentation/components/Sync360Surfaces.kt`: Shared Habitrek-inspired surface, title-pill, and icon-button primitives.
  - `features/sync/data/repository/`: Session stores, authentication, repository integration, and incoming transfer coordination.
  - `features/sync/data/network/OutgoingFileTransferCoordinator.kt`: Sender-side offer/upload/complete orchestration.
  - [`App.kt`](file:///c:/Users/4444444/IdeaProjects/Sync360/shared/src/commonMain/kotlin/com/liftley/sync360/App.kt): Entry point of the Compose hierarchy.
- [`/shared/src/androidMain/`](file:///c:/Users/4444444/IdeaProjects/Sync360/shared/src/androidMain/): Android-specific platform implementations.
  - [`features/sync/data/network/AndroidDiscoveryService.kt`](file:///c:/Users/4444444/IdeaProjects/Sync360/shared/src/androidMain/kotlin/com/liftley/sync360/features/sync/data/network/AndroidDiscoveryService.kt): Handles Zeroconf service registration and discovery via Android's `NsdManager`.
  - [`features/sync/domain/model/LocalDeviceIdentity.android.kt`](file:///c:/Users/4444444/IdeaProjects/Sync360/shared/src/androidMain/kotlin/com/liftley/sync360/features/sync/domain/model/LocalDeviceIdentity.android.kt): Resolves a unique installation UUID and model details.
- [`/shared/src/jvmMain/`](file:///c:/Users/4444444/IdeaProjects/Sync360/shared/src/jvmMain/): Desktop/JVM-specific platform implementations.
  - [`features/sync/data/network/DesktopDiscoveryService.kt`](file:///c:/Users/4444444/IdeaProjects/Sync360/shared/src/jvmMain/kotlin/com/liftley/sync360/features/sync/data/network/DesktopDiscoveryService.kt): Handles local network advertisement and discovery via `JmDNS`.
- [`/androidApp/`](file:///c:/Users/4444444/IdeaProjects/Sync360/androidApp/): Native Android Application container.
  - [`MainActivity.kt`](file:///c:/Users/4444444/IdeaProjects/Sync360/androidApp/src/main/kotlin/com/liftley/sync360/MainActivity.kt): Hosts Compose, requests notification permission, and bridges Android file picking, opening, and legacy save callbacks into shared platform operations.
  - `service/SyncService.kt`: Mode-aware Android foreground service that keeps approved sessions and active transfers reliable with wake/multicast locks.
- [`/desktopApp/`](file:///c:/Users/4444444/IdeaProjects/Sync360/desktopApp/): Native Desktop application container launching Compose Multiplatform for JVM.

---

## ⚡ 3. Critical Technical Discoveries & Resolutions

Several highly specific, tricky bugs were resolved during development. Keep these in mind during future refactorings:

### A. Kotlin `apply` Scoping Collision (NsdManager Launch Crash)
- **Problem**: In `AndroidDiscoveryService.kt`, configuring `NsdServiceInfo` inside `.apply { ... }` using `this.serviceType = serviceType` resulted in a crash. The compiler resolved the RHS `serviceType` to the receiver's own property (initially `null`), setting the service type to null and throwing an `IllegalArgumentException`.
- **Resolution**: Explicitly qualify the outer property name using `this@AndroidDiscoveryService.serviceType`.

### B. NsdManager Service Type Trailing Dot
- **Problem**: Android's native `NsdManager` throws an immediate validation exception if the service type contains a trailing dot (e.g., `_sync360._tcp.`). Conversely, Java's `JmDNS` on Desktop appends a trailing dot by default (e.g., `_sync360._tcp.local.`).
- **Resolution**: Keep `serviceType` as `_sync360._tcp` on Android and matching is performed gracefully via `.contains(serviceType)` on resolved addresses rather than strict equality.

### C. Android Background Clipboard Restrictions (Android 10+)
- **Problem**: Android 10 (API 29) and above restricts background apps/services from reading `clipboard.primaryClip`. Direct access throws a `SecurityException`.
- **Current approach**: Sync360 no longer runs a background clipboard listener. Clipboard text is read explicitly through `AndroidPlatformOperations.readClipboard()` while the user is interacting with the foreground UI, which fits Android's privacy restrictions and the app's explicit text-sharing model.

### D. SQLite Database Schema Version Clash
- **Problem**: During rapid development, changing the SQLDelight schema causes immediate startup crashes due to version mismatches with the existing local database.
- **Resolution**: Wrapped the Android and Desktop Database Driver factories in `try-catch` blocks. If driver instantiation fails (due to schema mismatches), the database file is automatically deleted and safely re-created.

Current relevance: this is retained as historical debugging context. The current Sync360 session/device/text flow does not depend on persisted conversation or device history.

### E. Privacy-Safe Unique Identifiers
- **Problem**: The app originally referenced hardware-specific identifiers (`ANDROID_ID`), which are deprecated for high-privilege access and trigger lint/Play Store security flags.
- **Resolution**: Migrated to a unique installation UUID generated using `UUID.randomUUID().toString()` and persisted safely in `SharedPreferences` (or local file configuration for Desktop).

---

## 🎨 4. Aesthetic & Design System

The visual theme now follows the Habitrek reference while remaining specific to Sync360:
- **Compact Device Selector**: A title/device pill opens the device picker without turning the screen into a deeply nested card hierarchy.
- **Local Device Filtering**: The local device's ID is explicitly filtered out from the discovery list. You will never see a confusing "pairing invitation with yourself" prompt.
- **Shared Surfaces and Shapes**: Mobile and Desktop reuse `Sync360Surface`, Material 3 `surfaceContainer` colors, and `AppShapes` for consistent alignment and spacing.
- **Zero Mock Elements**: All hardcoded fake devices (such as old mock carousels) were removed from overlays to keep the UI clean, functional, and production-ready.

---

## 🧪 5. Verification Commands
Use these commands when verification is requested. The project owner normally runs builds after a batch of edits and reports any errors:
- **Build Android**: `.\gradlew.bat assembleDebug`
- **Build Desktop**: `.\gradlew.bat :desktopApp:assemble`

---

## 🚀 6. Next Steps & Active Roadmap
When launching the next session, consider taking on the following high-priority tasks:
1. **Diagnose Slow File Transfers**: Instrument sender preparation/read, HTTP upload, receiver network read, hashing/write, and finalization separately. Compare Android-to-Desktop and Desktop-to-Android using the same file and network.
2. **Multi-device Live Validation**: Test Android-to-Android, Android-to-Desktop, Desktop-to-Android, and Desktop-to-Desktop transfers with large files on the same LAN.
3. **Transfer Recovery**: Add carefully scoped retry/recovery behavior for interrupted HTTP uploads without persisting chat or device history.
4. **Navigation Growth**: Add Navigation 3 `NavDisplay` only when a genuine second screen exists; keep navigation state in `SyncNavigationViewModel`.

---

## 🎯 Instructions for the next AI Assistant
> [!TIP]
> 1. Read this `context.md` file completely to understand the project architecture and platform quirks.
> 2. Make sure to keep `AndroidDiscoveryService`'s explicit outer scopes (`this@AndroidDiscoveryService`) intact.
> 3. Verify that new platform capabilities adhere to MVVM/UDF structure via `SyncViewModel`, `SyncUiState`, and `SyncEvent`.
> 4. Keep your chat responses concise and technical. Acknowledge this context and jump straight into solving the user's immediate goal!
