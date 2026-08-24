---
name: architecture
description: Oltre's module map and dependency rule — core purity, the client module family, where new code goes, and how to add a feature module.
when_to_use: >
  Consult before creating any new file outside an existing module's established structure,
  before adding a dependency to any build.gradle.kts, and whenever deciding where a new
  feature, rule, or adapter belongs.
---

# Oltre architecture rules

- **`core` accepts no dependencies.** Only `kotlinx-datetime` and `kotlinx-serialization` may
  ever be added, each justified to Davide first. No clock reads, no I/O, no logging, no
  platform APIs, no framework types — time and randomness enter as parameters (an advance
  function over a state and two instants; explicit seeds). Canonical full invariant list:
  `.claude/docs/brief.md`.
- **Game rules go in `core`; orchestration goes in the consumer.** If a behaviour must agree
  between client and server, it is a `core` rule by definition.
- **The wire goes in `:protocol`, which is a sibling of `core` and takes `core` and nothing else.**
  The verbs as data, the envelope, the sync request/response, the rejection taxonomy, the API
  version. It is not in `core` because `core`'s charter is *model + rules* and none of those is a
  game rule; it is not on the client side because rule 8 forbids `server` from reaching there. It
  holds **no I/O** — Ktor, routes and sockets are the server's and `:client:net:data`'s. And it
  states the *shape* of a request, never the rules: a verb that `core` will refuse constructs
  happily, because a refusal is a result the player can be shown and an exception is not.
- **One directory per client feature, layer modules inside.** New feature = a directory under
  `client/` holding layer modules: `:client:<feature>:ui` always, `:presentation` / `:domain` /
  `:data` only when the feature actually needs them — no empty placeholder layers. `ui` holds the
  composables and the models they render and depends on the `:client:design:*` layers it uses (+
  `core` or the feature's own domain where a model genuinely needs one). `presentation` holds the
  mapping from state into those models, depends on `ui` — never the reverse — and is **absent**
  where there is nothing to decide, as on `:client:debug`.
  `:client:shell` is the only module that sees all features; features never depend on each other —
  shared needs go down into `core` or the design family.
- **`:client:design` is a directory, not a module.** Its layers are `:core` (tokens, theme, font,
  and `LocalTranslations`), `:icon` (drawn glyphs), `:component` (styled widgets with no single
  feature owner), `:format` (which numbers and durations to show — no Compose in it), `:text`
  (what the game says: `TextRes`, `Strings`, `Translations` — no Compose either) and `:testing`
  (test helpers, in the *main* source set). Declare only the ones you use. A component moves here when it has **no single
  owner**, which is not the same as being used twice — the threshold is still two callers do not
  justify sharing, a third does, and a component whose one owner is obvious stays with it.
- **New module checklist:** add to `settings.gradle.kts`; copy the target set from
  `:client:design:core`; namespace `dev.fardavide.oltre.client.<feature>.<layer>`; wire into
  `:client:shell`; **add it to the root `build.gradle.kts` `kover {}` list** — a module missing
  there is silently absent from the coverage report.
- **No bare strings anywhere a player can read.** Every word the game says is a `TextRes` built
  through `Strings`, resolved once at the leaf by `Translations`. A `UiState` field is a `TextRes`
  and never a `String`; `TextRes(value)` is for text that came from outside the catalogue and
  therefore cannot be translated — a generated world name, a value from a server. A test asserts
  `Strings.hullsInFleet(3)`, not `"3 hulls"`.
- **Placeholder balance numbers live in one marked place in `core`** — never scattered
  literals. Decided values come from Notion or Davide.
- **State changes are events appended to a log**, not mutations. If a change can't be expressed
  as an event, question the change.
- Fakes: per-module test fixtures. KMP modules can't host fixtures, so sharing a fake across a
  module boundary needs a module — a **sibling of the module it doubles, named for it**:
  `client/save/data-testing` beside `client/save/data`, `core-testing/` beside `core/`. Never
  `:<module>:testing`, which is a child and breaks rule 1. A testing module inherits the layer it
  doubles, restrictions included. Never a repo-wide doubles module.
- **Eight module rules are enforced by the build**, and break the IDE sync rather than review: a
  module cannot contain a module; `domain` cannot depend on `data`, `presentation` or `ui`;
  `presentation` cannot depend on `data`; `data` cannot depend on `presentation` or `ui`; `ui`
  cannot depend on `data` or `presentation`; only a test source set may reach a `-testing` module;
  `core` depends on no module; nothing depends on `:client:shell`; `sim` and `server` never reach
  into `client/*`. Layer is the last segment of the
  Gradle path, so `:core`, `:protocol`, `:client:shell` and `:client:design` are not layers and are
  not constrained by 2–4 — the shell may see every layer precisely because rule 7 stops anything
  seeing it. Read the `module-rules` skill before adding a module or a project dependency.
