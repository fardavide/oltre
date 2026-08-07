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
  on `core` + `:client:design` (+ the feature's own domain/data). `:client:shell` is the only
  module that sees all features; features never depend on each other — shared needs go down
  into `core` or `:client:design`.
- **New module checklist:** add to `settings.gradle.kts`; copy the target set from
  `:client:design`; namespace `dev.fardavide.oltre.client.<feature>.<layer>`; wire into
  `:client:shell`.
- **Placeholder balance numbers live in one marked place in `core`** — never scattered
  literals. Decided values come from Notion or Davide.
- **State changes are events appended to a log**, not mutations. If a change can't be expressed
  as an event, question the change.
- Fakes: per-module test fixtures. KMP modules can't host fixtures, so sharing one across a module
  boundary needs a module of its own — **where that module goes is open** (`:<module>:testing` is
  ruled out: it is a child, not a sibling, and rule 1 rejects it). Nothing needs one yet; ask
  Davide when something does. Never a repo-wide doubles module.
- **Four module rules are enforced by the build**, and break the IDE sync rather than review: a
  module cannot contain a module; `domain` cannot depend on `data` or `presentation`;
  `presentation` cannot depend on `data`; `data` cannot depend on `presentation`. Layer is the
  last segment of the Gradle path, so `:client:shell` and `:client:design` are not layers and are
  not constrained. Read the `module-rules` skill before adding a module or a project dependency.
