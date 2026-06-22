# Sync360 Rebuild Decision And Requirements

## Why this file exists

This file captures the decision to rebuild Sync360 from a smaller, hand-written baseline instead of continuing to fight the current AI-generated implementation. It is meant to preserve the reasoning, the product shape, and the minimum requirements for the next version.

This is not a code map of the current project. It is a product and engineering brief for rebuilding the app with more control.

## Decision

The current project should not be treated as the codebase to deeply refactor forever. It was useful as an experiment and learning path, but it grew too much through AI-assisted coding. Because of that, the user does not fully own the mental model of the code.

For learning, maintainability, and long-term confidence, the better path is:

1. Merge or preserve the current work safely if needed.
2. Start a new branch from a clean baseline.
3. Keep only reusable non-business assets such as theme, fonts, colors, icons, and maybe simple design-system pieces.
4. Delete or ignore the complex generated sync implementation.
5. Rebuild the app manually from the smallest working version.
6. Use AI only as a reference, reviewer, teacher, and helper for small focused pieces.

## My opinion on this decision

This is a good decision for your goal.

If the goal were only to ship something quickly, continuing to patch the current code might make sense. But your goal is different: you want to learn local networking, understand the code, and regain control of the project.

For that goal, rebuilding manually is better than reading a large codebase that you did not write. Reading unfamiliar generated code teaches slower than writing a small version yourself, especially for topics like HTTP servers, TCP sockets, NSD discovery, file bytes, lifecycle, and state flow.

The current project is not worthless. It gave you:

- product clarity
- mistakes to avoid
- working ideas
- naming lessons
- architectural lessons
- transport lessons
- UI flow lessons

But it should become a reference, not the foundation you are forced to understand line by line.

## What went wrong

The main problem was not AI itself. The problem was using AI to grow too much of the product before you fully understood the fundamentals.

What went wrong:

- Too much code existed before the mental model existed.
- The app changed philosophy multiple times: connection-first, WebSocket, HTTP, raw TCP, nearby-drop flow.
- Old architecture remained while new product rules replaced it.
- Layers became hard to reason about.
- Some classes existed because of earlier ideas, not because the current app needed them.
- Refactoring became harder than rebuilding because the system had too much history inside it.
- Learning from generated code became slower than writing the simpler thing by hand.

Lesson:

Use AI for small pieces, references, examples, reviews, and explanations. Do not let AI create the full product skeleton before you understand the domain.

## What to keep from the current project

Keep only things that do not hide business logic from you.

Good things to keep:

- app name and product idea
- Material theme
- color/theme tokens
- fonts
- simple reusable UI primitives if they are easy to understand
- app icon/assets if desired
- Gradle/KMP setup if it is not causing friction
- Navigation 3 knowledge
- Koin knowledge if you still want DI
- notes from README/context/memory files

Avoid keeping:

- current sync backend
- current discovery implementation as final code
- current transfer implementation as final code
- security/session/HMAC system at first
- over-abstracted layers
- diagnostic/logging systems that are not needed for MVP
- old connection-first concepts

## Rebuild philosophy

Build one small working slice at a time.

Do not build architecture first. Build behavior first, then shape the code around behavior.

The rule should be:

- make it work simply
- understand every line
- rename until it reads clearly
- only then extract interfaces or layers

Start Android-first. Once Android works and the boundaries are obvious, add desktop support behind small interfaces.

## Product vision

Sync360 is a local-network nearby-device sharing app.

It helps two devices on the same Wi-Fi discover each other and send selected files or direct text snippets without cloud storage.

It is not a chat app.
It is not a permanent device manager.
It is not Bluetooth-style pairing.
It is a simple local drop app.

## Core user flow

Two devices open the app.

Each device:

1. starts a local server or receiver
2. advertises itself on the local network
3. scans for other Sync360 devices
4. shows nearby devices in a list
5. can select files or add text
6. can tap a nearby device to send
7. can receive an incoming request
8. can accept or decline
9. receives bytes if accepted
10. shows progress/result

## Main screens

### Send screen

Purpose: choose what to send and who to send it to.

Required features:

- list of nearby devices
- manual scan button
- selected files list
- direct text input
- add text button
- paste button if useful
- remove selected item
- clear selected items
- tap device to propose/send selected items
- send confirmation dialog or bottom sheet
- sending progress state
- one-time snackbar/error messages

Important rule:

Selected files/text stay selected after sending. They are removed only when the user explicitly clears/removes them.

### Receive screen

Purpose: handle incoming offers and received content.

Required features:

- show incoming request when Quick Save is off
- accept button
- decline button
- receive progress
- received files result
- received text result / clipboard history
- busy state while requested/receiving/received if required
- one-time snackbar/error messages

### Settings or top controls

Required features:

- Quick Save toggle
- show local device name/IP/port if useful for debugging
- maybe restart sharing later

## Quick Save behavior

Quick Save OFF:

- incoming request is shown to user
- user accepts or declines
- if declined, sender sees a one-time message
- if accepted, transfer starts

Quick Save ON:

- valid incoming request is accepted automatically
- receive progress starts without manual approval

Initial rebuild simplification:

Do not implement security first. Treat requests as open local-network requests. Security can be learned and added later as a separate topic.

## Discovery requirements

The app needs a way to find nearby Sync360 devices on the same local network.

Functional requirements:

- advertise this device
- scan for other devices
- resolve discovered services into usable device info
- output a list of nearby devices
- stop scanning after a scan window, such as 10 seconds
- allow manual scan again
- do not show this device as a nearby target

The output needed by upper layers:

```kotlin
DeviceProfile(
    id,
    name,
    type,
    hostAddress,
    port,
    isOnline
)
```

Discovery should not own file transfer logic.
Discovery should not own receive request logic.
Discovery should just provide nearby reachable devices.

## Transfer requirements

The app needs to send an offer first, then bytes only if accepted.

Functional requirements:

- sender creates an outgoing bundle
- sender sends request/offer to target device
- receiver accepts or declines
- if accepted, sender streams selected file/text bytes
- receiver saves files
- receiver handles text separately from real files
- both sides show progress
- both sides handle failure without crashing

Initial rebuild simplification:

- no HMAC
- no session tokens
- no encryption
- no permanent pairing
- no database
- no complex retry system
- no background clipboard sync

Add those later only after the simple version works and is understood.

## Text vs file rule

A real `.txt` selected from file picker is a file.

A direct text snippet typed/pasted in the app is not a `.txt` file. It is a text object.

Receiver should know the difference:

- file item -> save as file
- text item -> add to received text/clipboard list

## Busy state requirement

A receiver should be able to receive from only one sender at a time.

Receiver states:

- idle: can accept new offer
- requested: offer is waiting for accept/decline
- receiving: bytes are being received
- received: result is being shown, if this blocks new sends in the chosen design

Only idle should accept a new request.

If receiver is busy, incoming requests should be rejected or ignored clearly.

## Functional requirements list

### Device/discovery

- local device identity
- advertise local device
- scan for nearby devices
- resolve host and port
- show device list
- manual scan
- self-filtering

### Send

- select files
- add direct text
- keep selected items until explicit clear
- remove individual selected item
- choose target device
- confirm send
- show sending progress
- show send success/failure snackbar

### Receive

- receive incoming offer
- quick save on/off
- accept/decline request
- show receiving progress
- save files
- store/show received text
- show received result
- reject while busy

### Transport

- simple local HTTP or TCP control request
- raw TCP or simple byte stream for file bytes
- clear message structure for file/text metadata
- length validation at minimum
- no security in first learning version

### Platform

- Android first
- Desktop later
- platform file picker
- platform file saving
- platform local IP/network info
- platform discovery implementation

## Non-functional requirements

- The code must be understandable by the user.
- Each class/file should have one obvious job.
- Avoid more layers until they solve a real problem.
- No hidden magic.
- No generated architecture that the user cannot explain.
- No security until the user learns and adds it intentionally.
- No persistent database in MVP unless needed.
- Errors should not crash the app.
- UI should not own business logic.
- Backend state should be predictable.
- App should work on a private local network.
- Performance should be reasonable, but first priority is clarity and correctness.

## Suggested rebuild milestones

### Milestone 1: Local device identity and static UI

- create Send screen
- create Receive screen
- add Navigation 3
- add Quick Save toggle
- show local device info
- no networking yet

### Milestone 2: Android discovery only

- advertise device with NSD
- scan for devices
- resolve service info
- convert resolved info to simple device profile
- show nearby list
- stop scan after 10 seconds
- manual scan button

### Milestone 3: Simple request/response

- target device sends a simple offer request
- receiver shows accept/decline
- sender sees accepted/declined result
- no file bytes yet

### Milestone 4: Send text object

- add direct text
- send text after accept
- receiver displays text
- no file saving yet

### Milestone 5: Send one file

- select one file
- send file metadata
- accept request
- stream bytes
- save file
- show progress

### Milestone 6: Send multiple items

- support multiple files
- support text + files in one bundle
- preserve selected list after send
- improve progress/result display

### Milestone 7: Desktop support

- define small interfaces based on already working Android behavior
- implement desktop discovery
- implement desktop file saving
- test Android-to-Desktop and Desktop-to-Android

### Milestone 8: Security as separate learning topic

Only after the simple app works:

- learn threat model
- add session token
- add HMAC/signatures
- add nonce/timestamp replay protection
- consider encryption only if needed

## What not to do in the rebuild

- Do not start with full clean architecture.
- Do not add security first.
- Do not add desktop first.
- Do not build all platforms at once.
- Do not preserve old backend code just because it exists.
- Do not ask AI to generate the full project.
- Do not keep code that the user cannot explain.
- Do not add abstractions before the shape is proven by working code.

## Role for AI going forward

AI should be used as:

- teacher
- reviewer
- reference provider
- small code example generator
- bug explainer
- API explainer
- design discussion partner

AI should not be used as:

- full project generator
- blind refactor engine
- owner of architecture
- source of truth without user understanding

Good AI prompts going forward:

- Explain this API.
- Review this file.
- Show the simplest example of NSD registration.
- What are the tradeoffs of this model?
- Is this class doing too many things?
- What should I test manually here?
- Help me understand this error.

Bad AI prompts going forward:

- Build the whole app.
- Refactor everything.
- Create complete architecture for me.
- Add all security now.

## Branch/reset idea

Possible practical git plan:

1. Preserve current work on its branch.
2. Merge or save any refactor branch only if it contains useful stable changes.
3. From `main`, create a new rebuild branch.
4. Keep theme/assets/fonts/simple UI primitives if useful.
5. Remove complex sync backend from the rebuild branch.
6. Start the new implementation manually.

Needs confirmation before any destructive action.

## Summary

Rebuilding is not failure. It is taking ownership.

The current project taught the product shape and the wrong turns. The next project should be smaller, slower, and fully understood.

The first goal is not maximum architecture. The first goal is one Android device discovering another Android device, sending a request, and transferring one simple thing in code the user can explain.
