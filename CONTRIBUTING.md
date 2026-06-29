# Contributing to Sync360

Thanks for wanting to help. Sync360 is early, Android-first, and being rebuilt in public from small understandable slices.

The project is not looking for giant rewrites right now. The most useful contributions are focused, explainable, and easy to review.

## Current project status

Current working slice:

- Android NSD discovery.
- Dynamic Ktor server port advertisement.
- Ktor client/server ping request.
- Experimental receiver Accept/Decline state.

Not built yet:

- Real file transfer.
- Direct text sending.
- Desktop rebuilt networking flow.
- Security/session validation.
- Production-ready UX.

Please keep that status in mind when opening issues or PRs.

## Local setup

Prerequisites:

- JDK 17
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

Desktop shell:

```bash
./gradlew :desktopApp:run
```

The rebuilt networking flow is currently Android-first, so desktop behavior may lag behind Android.

## Before you start

For anything small, open a PR directly.

For anything large, open an issue first. Examples:

- changing architecture boundaries
- changing discovery behavior
- adding file transfer
- adding security
- changing Gradle/KMP target setup
- adding persistence/database code

## Good first issues

Good early contributions:

- Improve documentation.
- Add screenshots or demo GIFs.
- Improve error messages and logs.
- Test Android discovery on different devices/routers.
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
- Android hotspot if relevant
- what happened on sender
- what happened on receiver

Do not claim broad reliability unless it was tested.

## Security-related contributions

If a change touches local networking, file transfer, device identity, or future security/session validation, keep the threat model clear. Security-sensitive issues should follow [SECURITY.md](SECURITY.md), not public issue discussion.

## Project philosophy

Sync360 is being built in public, but not rushed. The goal is to learn and build a serious local-network sharing app one understandable layer at a time.
