# Changelog

All notable changes to Sync360 will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/), and this project intends to follow semantic versioning once releases begin.

## [Unreleased]

### Added

- Android-first manual rebuild direction.
- Compose Multiplatform Send and Receive screens.
- Navigation 3 app shell.
- Koin startup and dependency graph.
- Stable per-install Android device UUID.
- Android NSD advertisement and discovery.
- Nearby device model with host addresses and port.
- Embedded Ktor HTTP server proof.
- Dynamic OS-assigned HTTP server port advertisement through NSD.
- Ktor HTTP client proof for nearby device requests.
- `GET /sync360/ping` request/response prototype.
- Receiver-side Accept/Decline proof for incoming requests.
- Initial open-source documentation set.

### Changed

- Replaced the old generated-code project presentation with the current manual rebuild story.
- Moved project positioning toward local-first nearby device sharing.
- Updated Gradle/dependency setup during the rebuild.

### Planned

- Real send-offer request model.
- Direct text sending.
- File selection.
- File byte transfer.
- Transfer progress and result UI.
- Desktop support for the rebuilt flow.
- Security/session validation.

### Notes

- There are no stable releases yet.
- The current app is a technical prototype and learning-stage rebuild.
