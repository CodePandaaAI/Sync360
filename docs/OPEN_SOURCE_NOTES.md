# Open Source Notes

Sync360 is an open-source project and a build-in-public learning record.

## Why open source

Local device sharing touches many interesting areas:

- Android local networking
- Ktor client/server behavior
- mDNS/NSD discovery
- Kotlin Multiplatform boundaries
- Compose UI state
- file transfer design
- security tradeoffs

Opening the project makes the learning visible and gives other Android/KMP developers a place to discuss real implementation details.

## Project philosophy

- Build one small working slice at a time.
- Prefer understanding over generated volume.
- Keep public documentation honest.
- Do not claim features that are not implemented.
- Let architecture emerge from real behavior.
- Discuss large changes before coding them.

## What kind of feedback helps

Helpful feedback:

- "This naming hides ownership."
- "This Android NSD call can fail in this lifecycle state."
- "This Ktor route can hang without timeout."
- "This Desktop interface-selection rule will fail when multiple adapters are active."
- "Here is a clearer DTO shape."

Less helpful feedback:

- broad rewrites without explanation
- adding enterprise layers before the flow is understood
- pretending incomplete features are complete

## Good public milestones

Good moments to share publicly:

- two devices discover each other
- first local HTTP response
- receiver approval flow
- first text send
- first file send
- progress UI
- broader Android/Desktop validation
- first security pass

## Current message

Sync360 is still early, but the useful local flow is real:

```text
local discovery -> receiver approval -> direct text or file transfer
```

Android is the most-tested platform. Desktop/JVM now implements the same shared flow and has initial Desktop-to-Android validation, but broader operating-system, adapter, firewall, and router testing is still needed. That is the story to tell clearly without presenting the app as a finished or secure release.
