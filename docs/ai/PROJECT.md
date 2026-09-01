# Project history and collaboration context

Read this file only when a request concerns Sync360's history, product philosophy, maintainability direction, or how AI should collaborate with the owner. It is not required for normal implementation work.

## Why the project was rebuilt

Sync360 originally grew quickly through broad AI-generated changes. It reached a point where the owner could not comfortably explain or modify important discovery, networking, transfer, and state paths. Bugs were difficult to isolate because too much code and architecture existed before the underlying mental model was understood.

The owner chose to rebuild the app manually from a smaller baseline. The rebuilt version delivered similar product behavior in substantially less and more understandable code, but line count was never the main goal. The supported benefit is clearer ownership and maintenance—not an unsupported claim that fewer lines automatically made the app faster.

The old implementation still provided useful lessons about product shape, UI flow, transport choices, naming, cancellation, progress, and failure handling. Its value is historical guidance, not authority over current source.

## Product philosophy

Sync360 should remain a focused nearby-sharing app:

```text
open app -> discover nearby device -> choose text or files -> send locally
```

It is closer to a local drop tool than a chat app, cloud-sync product, clipboard-history service, or permanent device manager. Features should be added only when their benefit justifies the mental and maintenance cost.

The project has intentionally changed direction when a simpler product model removed unnecessary machinery. A notable example was separating text from files: small text can follow a direct delivery path, while files retain the coordination required for streamed transfer. The important lesson is to question whether complexity is necessary before trying to hide it behind abstractions.

## Owner's working preferences

- The owner is learning networking, coroutines, state, KMP boundaries, and native APIs through this project.
- Android behavior is easiest for them to understand and normally acts as the reference flow.
- They want execution explained from the first function through each state transition, callback, socket, and result.
- They prefer direct classes, clear names, explicit ownership, and sometimes longer code over clever compression.
- They dislike speculative layers, vague models, strange filenames, and refactors whose only benefit is fewer lines.
- They want real failure risks separated clearly from taste and style.
- They may manually reimplement an important path to learn it and gain confidence.
- Public claims must reflect actual evidence and manual validation limits.
- Major commits and PRs should explain the reasoning and product direction in accessible language, not only list technical changes.

## How AI should collaborate

Use AI as a focused teacher, reviewer, investigator, and implementation partner—not as the unexamined owner of the architecture.

Good behavior:

- Start from the user's specific question.
- Read the smallest complete relevant code path.
- Explain what exists before proposing replacement architecture.
- Make narrow changes that the owner can review and understand.
- Point out when a platform detail must remain more complex for lifecycle or memory safety.
- Admit uncertainty and identify what requires runtime or platform validation.

Avoid:

- Reading the whole repository by default.
- Applying generic Android architecture rules mechanically to a KMP networking app.
- Adding repositories, use cases, event systems, or modules without a demonstrated need.
- Reconstructing current behavior from this history file.
- Treating old plans, chat summaries, or previous Git status as current truth.

For present behavior, read the relevant source. For the current high-level design, use `docs/ARCHITECTURE.md`. For planned work, use `docs/ROADMAP.md`.
