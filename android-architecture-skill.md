# Android Architecture Skill for AI IDE

This document distills the official Android architecture recommendations into concrete rules that an AI coding assistant can use to analyze and improve an existing Kotlin/Jetpack Compose Android codebase.[page:1][web:1][web:2]

The goal: you feed this file into your AI IDE as a "skill" so it can:
- Detect architectural smells in your current project.
- Suggest better layering, state handling, and test structure.
- Propose refactorings aligned with modern Android guidance.

---

## 1. High-level principles

An Android app should follow a layered architecture with clear separation of concerns, a single source of truth for data, and unidirectional data flow from data to UI.[page:1][web:1]

Key principles:
- UI reads state, renders it, and emits user events.
- ViewModels handle UI logic, transform data, and call the data (and optional domain) layer.
- The data layer owns business logic and data sources, and exposes data via repositories.
- Data flows down via Flows/StateFlows; user events flow up via method calls.

When analyzing a project, prefer changes that increase separation of concerns, move business logic downwards (toward data/domain), and make UI layer as dumb as possible.

---

## 2. Layered architecture rules

### 2.1 Layers and responsibilities

Rules for a modern layered Android architecture:[page:1][web:1][web:9]

- There must be a **data layer** that exposes application data and contains most business logic.
- There must be a **UI layer** that renders data and handles user interaction; it must not talk directly to low-level data sources.
- A **domain layer** is optional but recommended for larger/complex apps to host reusable business logic as use cases.
- Data should be exposed from the data layer via **repository interfaces**, not via raw APIs like Room DAOs, Retrofit services, or platform services.
- Communication between layers should use **Kotlin coroutines and Flow/StateFlow**.

### 2.2 Things to flag (smells)

The AI assistant should highlight these issues:[page:1][web:1]

- UI composables or Activities/Fragments directly accessing:
  - Retrofit/HTTP clients, Room DAOs, Firebase SDKs, DataStore/SharedPreferences.
  - GPS/location APIs, Bluetooth, network callbacks.
- Business rules (validation, filtering, authorization, pricing, etc.) implemented in composables instead of ViewModel/data/domain.
- No repository types at all, or using data sources directly from ViewModels.
- No dedicated data layer module/package (everything dumped into `ui` or `presentation`).
- Overuse of callbacks instead of coroutines/Flows for async data.

For each smell, suggest creating/using repositories, moving logic into data/domain, and exposing data as flows.

---

## 3. UI layer rules (Jetpack Compose)

The UI layer:
- Displays application data on screen.
- Is the primary point of user interaction.
- Should be implemented with Jetpack Compose for new apps.

### 3.1 Unidirectional Data Flow (UDF)

Rules:[page:1][web:1]

- ViewModels expose UI state (usually a `StateFlow<UiState>` or similar).
- Composables **collect** that state and render UI.
- Composables send user events upwards via function parameters (event handlers) to ViewModel.

Smells to detect:
- Composables updating shared mutable state directly (`var` globals, singleton objects) instead of via ViewModel.
- Two-way binding patterns that break unidirectional flow (for example, passing mutable state objects into lower-level components and letting them mutate them).
- ViewModel sending one-off events that the UI must "listen" for, instead of converting them into state.

### 3.2 ViewModels in the UI layer

Rules for UI–ViewModel interaction:[page:1][web:1]

- Use AAC `ViewModel` for each screen/destination.
- Do **not** inject Android-lifecycle objects into ViewModel (no `Activity`, `Context`, `Resources`, `Application`).
- Prefer a **single-activity** app with Navigation component for multi-screen flows.
- Collect flows in a **lifecycle-aware** way using `collectAsStateWithLifecycle` in composables.

Smells to detect:
- ViewModels that extend `AndroidViewModel` without a strong justification.
- ViewModels with `Context`, `Activity`, or `Fragment` fields.
- Multiple ViewModels per simple screen when one would do, or ViewModels used inside very small reusable composables.
- Collecting flows with `collectAsState` or `launchIn` tied to wrong lifecycle when `collectAsStateWithLifecycle` would be safer.

Suggested fixes:
- Replace `AndroidViewModel` with plain `ViewModel` and move context-dependent work into data layer or UI.
- Inject repositories and use cases into ViewModel via constructor injection (Hilt/manual DI).
- For reusable components, use simple state holder classes instead of ViewModels.

### 3.3 Screen structure example

Target pattern the AI should move code towards:[page:1]

- Each screen has:
  - A `@Composable` entry point: `MyScreen(...)`.
  - A `MyViewModel` with dependency-injected repositories/use cases.
  - A `UiState` data class (or sealed class) describing all data, loading, and error states for that screen.
- `MyScreen` gets `MyViewModel`, collects `uiState` via `collectAsStateWithLifecycle()`, and calls child composables with *plain* state and event lambdas.

---

## 4. ViewModel rules

### 4.1 General responsibilities

ViewModels must:
- Provide UI state to composables (usually as `StateFlow`/`Flow`).
- Interact with data/domain layers (repositories, use cases).
- Contain UI-specific logic (mapping domain/data models to UI models).

Rules:[page:1]

- No direct references to `Activity`, `Context`, `Resources`, or `Application`.
- Use coroutines and flows via `viewModelScope` and repository methods.
- Expose a **single `uiState` property** when possible (or a small number of clearly separated states).
- Use `stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), initial)` when adapting streams from data layer.

Smells to detect:
- ViewModels calling platform APIs that belong in repositories (e.g., Bluetooth, location, network listeners).
- Multiple mutable state properties representing overlapping pieces of UI state instead of a single `UiState` data class.
- Synchronous, blocking calls on main thread (network or disk).

### 4.2 UI state types

Recommended patterns for `UiState`:[page:1][web:1]

- Use a data class:
  - Fields for data, loading flags, and error messages.
- Or a sealed type if states are mutually exclusive (Loading, Success, Error, Empty, etc.).
- Expose as `StateFlow<UiState>` from ViewModel.

The AI assistant should:
- Suggest introducing a `UiState` type when multiple related `LiveData`/`StateFlow` fields exist.
- Consolidate error/loading flags into this state instead of keeping separate booleans everywhere.

---

## 5. Lifecycle rules

Instead of overriding Activity lifecycle callbacks for UI-related work, use lifecycle-aware effects in composables and lifecycle-aware collection of flows.[page:1]

Rules:[page:1]

- Prefer `LifecycleStartEffect` and `LifecycleResumeEffect` in composables for sync work tied to lifecycle.
- Use `repeatOnLifecycle` in lifecycle-aware scopes for async work in Activities/Fragments.
- Collect flows exposed by ViewModel with `collectAsStateWithLifecycle`.

Smells to detect:
- Heavy logic in `onCreate`, `onResume`, `onPause`, etc. directly manipulating UI state that could be moved into composables with effects.
- Manual lifecycle listeners where higher-level APIs would suffice.

---

## 6. Data layer rules

The data layer exposes application data and owns business logic tied to data retrieval, caching, and persistence.[page:1][web:1][web:9]

### 6.1 Repositories and data sources

Rules:
- Create repositories even when there is only one data source.
- Repositories hide details of data sources (network, database, disk, sensors, etc.).
- Data layer should use coroutines and Flows for asynchrony.

Smells:
- Direct use of DAOs, Retrofit interfaces, Firebase SDKs, etc. from ViewModels/UI.
- No clear repository abstractions.
- Network or database logic spread across multiple layers.

### 6.2 Models per layer

In complex apps, prefer separate models per layer:[page:1][web:1][web:9]

- DTO/network models in remote data sources.
- Database entities in local data sources.
- Repository models that expose only the fields needed by higher layers.
- UI models inside `UiState`/presentation.

Smells:
- Reusing network/database entities directly in UI.
- Leaking data source–specific concerns (pagination tokens, database primary keys) into UI.

The AI assistant should propose mapping functions (e.g., `toDomainModel()`, `toUiModel()`) and where to place them.

---

## 7. Optional domain layer rules

The domain layer is an optional layer between UI and data.[web:1][web:9]

Use it when:
- Business logic is complex and reused across multiple screens.
- You want richer test isolation and clear use case boundaries.

Rules:
- Represent each action as a **use case** class or function, ideally doing one thing (e.g., `FetchUserProfile`, `SubmitOrder`).[web:1]
- Keep use cases stateless; they should not hold mutable state.
- Use cases depend on repository interfaces, not concrete data sources.

Smells for the AI to detect:
- Repeated business logic in multiple ViewModels.
- Large ViewModels with many responsibilities and complex flows.

Suggested fixes:
- Extract repeated logic into use cases, move to `domain` module/package.
- Make ViewModels call use cases instead of repositories directly in complex flows.

---

## 8. Dependency injection rules

Proper dependency management is critical at scale.[page:1]

Rules:
- Prefer **constructor injection** for dependencies.
- Scope objects to the appropriate lifecycle (application, activity, navigation graph, etc.).
- Use Hilt or manual DI for simple apps; Hilt is recommended when you have multiple screens, WorkManager, or navigation-scoped ViewModels.[page:1]

Smells:
- Direct calls to `ServiceLocator`-style singletons from all over the app.
- Manual construction of dependencies inside Activities/Fragments/Composables instead of injection.
- Static singletons that hold mutable global state.

The AI assistant should:
- Suggest introducing DI if everything is wired manually.
- Promote constructor injection and removal of `object`-based global singletons when they carry app state.

---

## 9. Testing rules

Testing is required for anything beyond trivial apps.[page:1][web:3][web:11]

Minimum recommended test coverage:
- Unit tests for ViewModels (including flows/state).
- Unit tests for repositories and data sources.
- Basic UI navigation tests as regression tests.

Rules:
- Prefer **fakes** over mocks for most tests.
- For `StateFlow`:
  - Assert on the `value` when possible.
  - Use `SharingStarted.WhileSubscribed` in production code to avoid leaks and make tests deterministic.
- For Compose UI tests:
  - Use `ComposeTestRule` / `AndroidComposeTestRule` to launch composables.
  - Use semantics APIs to find elements and perform actions, not View IDs.

Smells:
- No tests for ViewModels or repositories.
- Heavy reliance on brittle UI tests for business rules that should be covered at unit level.
- Using Espresso only for Compose UI instead of Compose testing APIs.

The AI assistant should propose:
- Where to add ViewModel and repository tests.
- How to introduce basic Compose UI tests for key flows.

---

## 10. Modularization rules (bonus for larger apps)

For large apps, modularization improves build times, ownership, and separation of concerns.[web:2][web:4][web:7][web:10][web:13]

Recommended patterns:
- Separate by **layer** and **feature**:
  - Core modules: `core:model`, `core:network`, `core:database`, `core:ui`.
  - Feature modules: `feature:home`, `feature:settings`, etc.
  - Optional `domain` modules per bounded context.
- Keep feature modules independent; avoid feature-to-feature dependencies when possible.
- Introduce API/implementation split where necessary (`feature:payments-api` and `feature:payments-impl`).

Smells the AI should look for:
- Single massive `app` module with everything inside.
- Cycles between modules or many `implementation` dependencies where `api` boundaries would be better.
- Feature modules depending on each other for shared UI/components instead of depending on a shared `core:ui`.

Suggested refactorings:
- Extract common utilities/models into `core` modules.
- Move feature-specific code into feature modules.
- Introduce domain modules per business area when logic grows.

---

## 11. Naming conventions

Naming rules to improve clarity and consistency:[page:1]

- Methods: use verb phrases (e.g. `makePayment()`, `loadArticles()`).
- Properties: use noun phrases (e.g. `inProgressTopicSelection`).
- Streams of data: `get{Model}Stream()` / `get{Models}Stream()` for `Flow`-returning methods.
- Implementations of interfaces:
  - Prefer descriptive names: `OfflineFirstNewsRepository`, `InMemoryUserDataSource`.
  - Use `Default` prefix when there is no better name: `DefaultNewsRepository`.
  - Use `Fake` prefix for test implementations: `FakeNewsRepository`.

The AI assistant can:
- Suggest renames where names are misleading or violate these patterns.
- Propose consistent naming across repositories, data sources, and use cases.

---

## 12. How an AI IDE should apply this

When integrated into an AI IDE, this skill should:

1. **Detect layer violations**
   - Find direct access to data sources in UI.
   - Locate business logic in composables/Activities.

2. **Enforce ViewModel & UI rules**
   - Ensure each screen has a ViewModel.
   - Check ViewModels only expose state via flows and do not hold Android objects.

3. **Encourage proper data/domain layers**
   - Identify missing repositories or overgrown ViewModels.
   - Suggest extracting use cases for shared or complex logic.

4. **Promote testing**
   - Highlight untested ViewModels/repositories.
   - Suggest appropriate Compose UI tests.

5. **Guide modularization (when applicable)**
   - Recommend splitting a monolith module into core/feature modules.
   - Identify wrong module dependencies.

If multiple refactorings conflict, prioritize:
- Correctness and testability.
- Separation of concerns and UDF.
- Layered architecture and DI.

This prioritization keeps changes aligned with official Android architectural guidance while making your existing codebase more maintainable and scalable.
