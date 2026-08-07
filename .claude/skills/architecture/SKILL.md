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
- **One directory per client feature, layer modules inside.** New feature = a directory under
  `client/` holding layer modules: `:client:<feature>:presentation` always, `:domain` / `:data`
  only when the feature actually needs them — no empty placeholder layers. Presentation depends
  on `core` + the `:client:design:*` layers it actually uses (+ the feature's own domain/data).
  `:client:shell` is the only module that sees all features; features never depend on each other —
  shared needs go down into `core` or the design family.
- **`:client:design` is a directory, not a module.** Its layers are `:core` (tokens, theme, font),
  `:icon` (drawn glyphs), `:component` (styled widgets with no single feature owner), `:format`
  (how numbers and durations are written — no Compose in it) and `:testing` (test helpers, in the
  *main* source set). Declare only the ones you use. A component moves here when it has **no single
  owner**, which is not the same as being used twice — the threshold is still two callers do not
  justify sharing, a third does, and a component whose one owner is obvious stays with it.
- **New module checklist:** add to `settings.gradle.kts`; copy the target set from
  `:client:design:core`; namespace `dev.fardavide.oltre.client.<feature>.<layer>`; wire into
  `:client:shell`; **add it to the root `build.gradle.kts` `kover {}` list** — a module missing
  there is silently absent from the coverage report.
- **Placeholder balance numbers live in one marked place in `core`** — never scattered
  literals. Decided values come from Notion or Davide.
- **State changes are events appended to a log**, not mutations. If a change can't be expressed
  as an event, question the change.
- Fakes: per-module test fixtures (or a sibling `:<module>:testing` for KMP modules that can't
  host fixtures). Never a repo-wide doubles module.
