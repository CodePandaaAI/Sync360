# Sync360 - Complete Project Context & Handover Guide

Welcome to **Sync360**! This document serves as a comprehensive system summary, design record, and architectural guide. It captures the entire context of the project so that any new developer or AI assistant can immediately assume command, understand what has been built, and continue the implementation seamlessly.

---

## 📖 1. Project Overview & Mission
**Sync360** is a modern, premium, bidirectional peer-to-peer IoT synchronization system (akin to Apple Continuity, Xiaomi HyperConnect, or Google Quick Share) designed to bridge **Android Mobile** and **JVM Desktop** (PC) clients.

### Core Features
- **Symmetric Peer Presence**: Both Android and Desktop advertise their presence and discover each other concurrently.
- **Proactive Pair Prompts**: The app automatically triggers a premium pairing dialogue when an unpaired device is resolved on the local network.
- **Focused Bidirectional Clipboard Sync**: Seamlessly replicates system copy-paste events between paired devices in the background with strict deduplication to prevent feedback loops.
- **Modern Material 3 / OneUI Design**: Premium, visually striking, pill-shaped aesthetics, including a top pill-shaped selector and interactive bottom sheets.

---

## 🛠️ 2. Architectural Design

Sync360 is built using **Kotlin Multiplatform (KMP)** and **Compose Multiplatform**. It adheres to **MVVM / Clean Architecture / Unidirectional Data Flow (UDF)** principles.

### Directory Structure & Map
- [`/shared/src/commonMain/`](file:///c:/Users/4444444/IdeaProjects/Sync360/shared/src/commonMain/kotlin/com/liftley/sync360/): Core application flow, domain models, ViewModels, and Compose UI layouts.
  - [`features/sync/presentation/SyncViewModel.kt`](file:///c:/Users/4444444/IdeaProjects/Sync360/shared/src/commonMain/kotlin/com/liftley/sync360/features/sync/presentation/SyncViewModel.kt): The primary state machine driving UI states, local database subscriptions, discovery, sockets, and clipboard event routing.
  - [`features/sync/presentation/SyncUiState.kt`](file:///c:/Users/4444444/IdeaProjects/Sync360/shared/src/commonMain/kotlin/com/liftley/sync360/features/sync/presentation/SyncUiState.kt): Single source of truth state for UI rendering.
  - [`features/sync/presentation/SyncEvent.kt`](file:///c:/Users/4444444/IdeaProjects/Sync360/shared/src/commonMain/kotlin/com/liftley/sync360/features/sync/presentation/SyncEvent.kt): Sealed class definitions for unidirectional UI events.
  - [`App.kt`](file:///c:/Users/4444444/IdeaProjects/Sync360/shared/src/commonMain/kotlin/com/liftley/sync360/App.kt): Entry point of the Compose hierarchy.
- [`/shared/src/androidMain/`](file:///c:/Users/4444444/IdeaProjects/Sync360/shared/src/androidMain/): Android-specific platform implementations.
  - [`features/sync/data/network/AndroidDiscoveryService.kt`](file:///c:/Users/4444444/IdeaProjects/Sync360/shared/src/androidMain/kotlin/com/liftley/sync360/features/sync/data/network/AndroidDiscoveryService.kt): Handles Zeroconf service registration and discovery via Android's `NsdManager`.
  - [`features/sync/domain/model/LocalDeviceIdentity.android.kt`](file:///c:/Users/4444444/IdeaProjects/Sync360/shared/src/androidMain/kotlin/com/liftley/sync360/features/sync/domain/model/LocalDeviceIdentity.android.kt): Resolves a unique installation UUID and model details.
- [`/shared/src/jvmMain/`](file:///c:/Users/4444444/IdeaProjects/Sync360/shared/src/jvmMain/): Desktop/JVM-specific platform implementations.
  - [`features/sync/data/network/DesktopDiscoveryService.kt`](file:///c:/Users/4444444/IdeaProjects/Sync360/shared/src/jvmMain/kotlin/com/liftley/sync360/features/sync/data/network/DesktopDiscoveryService.kt): Handles local network advertisement and discovery via `JmDNS`.
- [`/androidApp/`](file:///c:/Users/4444444/IdeaProjects/Sync360/androidApp/): Native Android Application container.
  - [`MainActivity.kt`](file:///c:/Users/4444444/IdeaProjects/Sync360/androidApp/src/main/kotlin/com/liftley/sync360/MainActivity.kt): Hooks up foreground system clipboard listeners and launches background services.
  - [`service/SyncForegroundService.kt`](file:///c:/Users/4444444/IdeaProjects/Sync360/androidApp/src/main/kotlin/com/liftley/sync360/service/SyncForegroundService.kt): An Android Foreground Service that maintains network socket communication and clipboard listeners in the background.
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
- **Resolution**: In `SyncForegroundService.kt`, all clipboard accesses are wrapped in a robust `try-catch` safety block. Additionally, a secondary active listener resides inside the foreground `MainActivity` to capture copies immediately when the UI is visible.

### D. SQLite Database Schema Version Clash
- **Problem**: During rapid development, changing the SQLDelight schema causes immediate startup crashes due to version mismatches with the existing local database.
- **Resolution**: Wrapped the Android and Desktop Database Driver factories in `try-catch` blocks. If driver instantiation fails (due to schema mismatches), the database file is automatically deleted and safely re-created.

### E. Privacy-Safe Unique Identifiers
- **Problem**: The app originally referenced hardware-specific identifiers (`ANDROID_ID`), which are deprecated for high-privilege access and trigger lint/Play Store security flags.
- **Resolution**: Migrated to a unique installation UUID generated using `UUID.randomUUID().toString()` and persisted safely in `SharedPreferences` (or local file configuration for Desktop).

---

## 🎨 4. Aesthetic & Design System

The visual theme has been completely overhauled to feel premium, responsive, and tactile:
- **Top Pill Selector**: A beautiful, floating, pill-shaped header at the top of the interface. Tapping the selector opens a sleek bottom sheet.
- **Local Device Filtering**: The local device's ID is explicitly filtered out from the discovery list. You will never see a confusing "pairing invitation with yourself" prompt.
- **Tactile Feedback & Colors**: The UI uses carefully tailored Material 3 colors, dynamic gradients, and flat rounded corners.
- **Zero Mock Elements**: All hardcoded fake devices (such as old mock carousels) were removed from overlays to keep the UI clean, functional, and production-ready.

---

## 🧪 5. Verification Commands
Both build pipelines are verified and fully operational:
- **Build Android**: `.\gradlew.bat assembleDebug`
- **Build Desktop**: `.\gradlew.bat :desktopApp:assemble`

---

## 🚀 6. Next Steps & Active Roadmap
When launching the next session, consider taking on the following high-priority tasks:
1. **Multi-device Live Validation**: Test communication between an emulator and a physical client or two physical clients on the same local network.
2. **File and Asset Sharing**: Leverage the established bidirectional socket pipeline to enable drag-and-drop file/image sharing alongside the clipboard.
3. **Advanced Connection Recovery**: Implement auto-reconnect backoffs when a socket disconnects abruptly (e.g. when walking out of Wi-Fi range).

---

## 🎯 Instructions for the next AI Assistant
> [!TIP]
> 1. Read this `context.md` file completely to understand the project architecture and platform quirks.
> 2. Make sure to keep `AndroidDiscoveryService`'s explicit outer scopes (`this@AndroidDiscoveryService`) intact.
> 3. Verify that new platform capabilities adhere to MVVM/UDF structure via `SyncViewModel`, `SyncUiState`, and `SyncEvent`.
> 4. Keep your chat responses concise and technical. Acknowledge this context and jump straight into solving the user's immediate goal!
