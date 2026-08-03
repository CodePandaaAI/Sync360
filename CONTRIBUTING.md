# Contributing to Sync360

Thanks for wanting to help. Sync360 is early, Android-first, and being rebuilt in public from small understandable slices.

The project is not looking for giant rewrites right now. The most useful contributions are focused, explainable, and easy to review.

## Current project status

Implemented today:

- Android nearby discovery and registration through `NsdManager`.
- Windows nearby discovery and registration through the system DNS-SD API.
- Current macOS/Linux discovery and registration through JmDNS.
- Direct text and multi-file transfer between nearby devices.
- Shared Compose UI for Android and Desktop.
- Enabled iOS source implementation for Bonjour discovery, text/file transfer, selection, clipboard, and Files-visible storage.

Important current limitations:

- Local transfers are not authenticated or encrypted.
- Background and automatic network-change lifecycle handling is incomplete.
- Desktop networking has not been broadly validated across operating systems, adapters, VPNs, and routers.
- iOS physical-device discovery and transfer behavior is not yet validated.

Please keep that status in mind when opening issues or PRs.

## Local setup

Prerequisites:

- JDK 23
- Android Studio or IntelliJ IDEA
- Android SDK
- Gradle wrapper from this repository

Clone and open:

```bash
git clone <your-repo-url>
cd Sync360
```

Open the repository root in Android Studio or IntelliJ IDEA and let Gradle sync.

Build Android debug:

```bash
./gradlew :androidApp:assembleDebug
```

On Windows:

```powershell
./gradlew.bat :androidApp:assembleDebug
```

Run Desktop:

```bash
./gradlew :desktopApp:run
```

Android remains the primary reference implementation. Platform networking behavior can differ where operating-system APIs require it.

## Before you start

For anything small, open a PR directly.

For anything large, open an issue first. Examples:

- changing architecture boundaries
- changing discovery behavior
- changing the transfer protocol
- adding security
- changing Gradle/KMP target setup
- adding persistence/database code

## Good first issues

Good early contributions:

- Improve documentation.
- Add screenshots or demo GIFs.
- Improve error messages and logs.
- Test Android discovery on different devices/routers.
- Test Windows discovery across Ethernet, Wi-Fi, VPN, and virtual adapters.
- Improve host address selection, especially IPv4 vs IPv6.
- Clean up naming where the current intent is obvious.
- Add small tests around pure Kotlin models/controllers when useful.

## Issues

When reporting a bug, include:

- device model
- Android version
- app build type
- Wi-Fi/router/hotspot setup if relevant
- steps to reproduce
- expected behavior
- actual behavior
- logs/screenshots if available

For feature ideas, describe the user flow first. Implementation can come later.

## Pull requests

A good PR should:

- solve one clear problem
- keep changes scoped
- avoid unrelated formatting churn
- explain why the change is needed
- mention what was tested manually
- avoid broad architecture rewrites without discussion

Suggested branch names:

```text
feature/android-discovery-log
fix/ktor-ping-timeout
docs/readme-screenshots
refactor/network-controller-boundary
```

## Commit messages

Use clear, plain commit messages. Examples:

```text
Add receiver decision state for ping requests
Fix Android NSD duplicate stop guard
Document current Ktor request flow
```

For larger commits, include a short body explaining behavior and tradeoffs.

## Code style

Current preferences:

- Kotlin-first, direct names.
- Small classes with obvious jobs.
- UI renders state and calls ViewModel actions.
- ViewModels coordinate UI-facing state.
- Data/platform layers own networking/platform APIs.
- Do not put Android APIs directly in composables.
- Do not add abstractions before they clarify real duplication or ownership.

## Testing expectations

Tests are still light. If you add pure logic, add focused tests where practical.

For networking changes, manual validation notes are useful:

- one Android device
- two Android devices on same Wi-Fi
- Android and Desktop on the same network
- Desktop adapter and operating-system version
- Android hotspot if relevant
- what happened on sender
- what happened on receiver

Do not claim broad reliability unless it was tested.

## Security-related contributions

If a change touches local networking, file transfer, device identity, or future security/session validation, keep the threat model clear. Security-sensitive issues should follow [SECURITY.md](SECURITY.md), not public issue discussion.

## Project philosophy

Sync360 is being built in public, but not rushed. The goal is to learn and build a serious local-network sharing app one understandable layer at a time.
