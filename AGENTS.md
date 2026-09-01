# Sync360 agent guide

Use this file as the entry point for AI work in this repository. Do not preload the whole repository or every document. Load only the context required by the current request.

## Start every task

1. Inspect `git branch --show-current`, `git status --short --branch`, recent commits, and both staged and unstaged diffs relevant to the request.
2. Preserve every existing working-tree change. Never assume an unfamiliar change is disposable.
3. Locate relevant files with `rg` or `rg --files`, then read the smallest complete execution path needed for the task.
4. Treat current source and Git state as authoritative. Documentation gives orientation but may be stale.
5. State important source/documentation mismatches before acting on them.

Do not repeatedly reread unchanged files during one chat. After editing a file, inspect its current diff instead of reloading unrelated code.

## Non-negotiable working rules

- Do not run Gradle builds or tests unless the owner explicitly asks.
- Do not edit application source for an explanation, review, status report, commit message, PR description, or release-text-only request.
- Do not stage, commit, restore, delete, rename, or overwrite user work unless the request authorizes it.
- Separate genuine correctness problems from optional readability or style improvements.
- Prefer readable, explicit control flow over compact or clever abstractions.
- Do not introduce architecture merely to reduce line count.
- Explain exact execution paths in simple English: entry point, inputs, state changes, ownership, callbacks, and completion/failure.
- Preserve necessary native lifecycle, socket, stream, callback, coroutine, arena, and memory handling.
- Keep blocking I/O off the UI thread, networking out of composables, and platform APIs out of `commonMain`.
- Files must remain streamed; do not load complete files into memory.
- Keep README, changelog, Git, PR, release, security, and store text honest. Never turn assumptions or narrow manual checks into broad claims.
- Never expose signing files, credentials, passwords, or private keys.

## Load context by task

Read only the row that matches the request, plus directly connected source.

| Task                                                                         | Read first                                          | Then inspect                                                               |
|------------------------------------------------------------------------------|-----------------------------------------------------|----------------------------------------------------------------------------|
| HTTP, TCP, transfer state, cancellation, discovery, or platform networking   | `docs/ARCHITECTURE.md`                              | Relevant controller/contract and only the affected platform implementation |
| Android/Desktop/iOS build, packaging, version, release, or manual validation | `docs/DEVELOPMENT.md`                               | Relevant build file, workflow, version file, and `CHANGELOG.md`            |
| Product behavior, scope, or future priorities                                | `README.md` and `docs/ROADMAP.md`                   | Relevant state/ViewModel/controller when implementation matters            |
| Security or privacy                                                          | `SECURITY.md` or `PRIVACY.md`                       | Actual transport, storage, manifest, or platform code supporting the claim |
| UI or interaction                                                            | Relevant screen, its ViewModel, and its state model | Directly used components only; do not scan unrelated UI trees              |
| Project history, owner preferences, or architectural philosophy              | `docs/ai/PROJECT.md`                                | Current source only if the request also concerns implementation            |
| General code change                                                          | No broad document preload                           | Search for the symbol/behavior and follow its callers and dependencies     |

Do not read `CHANGELOG.md` as current architecture. Do not read historical documents to determine current behavior. Do not scan every source set when only one platform is in scope.

## Small source map

- Shared coordination: `shared/src/commonMain/kotlin/com/liftley/sync360/data/`
- HTTP client/server/DTOs: `shared/src/commonMain/kotlin/com/liftley/sync360/data/network/http/`
- TCP contracts/constants: `shared/src/commonMain/kotlin/com/liftley/sync360/data/network/tcp/`
- Receiver state: `shared/src/commonMain/kotlin/com/liftley/sync360/domain/model/ClientServerState.kt`
- Send/Receive presentation: `shared/src/commonMain/kotlin/com/liftley/sync360/presentation/send/` and `presentation/receive/`
- Platform implementations: `shared/src/androidMain/`, `shared/src/jvmMain/`, and `shared/src/iosMain/`
- Hosts and packaging: `androidApp/`, `desktopApp/`, and `iosApp/`

Follow one vertical path instead of reading an entire layer. For example, a text-delivery review normally needs the Send ViewModel/state, outgoing controller, HTTP DTO/client/server, incoming controller/state, and relevant Receive ViewModel—not every UI or discovery file.

## Project identity

Sync360 is an Android-first Kotlin Multiplatform/Compose Multiplatform app for direct text and file sharing between nearby devices on a reachable local network. It has no Sync360 backend or cloud storage. Android is the reference and most-tested platform; Desktop and iOS require equivalent behavior through platform-native implementations.

The owner rebuilt the app to understand and control it. Teachability and explicit ownership matter more than feature count. Review and explain important behavior before replacing it unless implementation was explicitly requested.

Current protocol and product details belong in source and `docs/ARCHITECTURE.md`; current release state belongs in build/version files and `CHANGELOG.md`. Do not copy volatile branch names, commit hashes, staged-file lists, dependency versions, or temporary work status into this guide.
