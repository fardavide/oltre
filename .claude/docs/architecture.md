# Architecture

## Module map

```
core          KMP: jvm, iosArm64, iosSimulatorArm64, android. Pure model + rules.
sim           JVM CLI. Headless balancing harness; fast-forwards weeks in ms. Never ships.
client/       Directory of KMP + Compose Multiplatform modules (desktop, iOS, Android):
  :client:shell    Composition root + entry points (desktop main, iOS framework)
  :client:design/  The design system, as a directory of layer modules — split the way Compose
                   splits itself, by dependency direction and rate of change:
    :core          Tokens: palette (from docs/ui-mockup.html), theme, bundled font, layout caps
    :icon          Drawn glyphs (Canvas paths, never an icon font — see screenshot-testing)
    :component     Styled widgets with no single feature owner (cost chip, progress bar, …)
    :format        How numbers and durations are written. No Compose reaches it
    :testing       Test helpers for the modules above (Roborazzi options); main source set
  :client:<feature>:<layer>  One directory per feature, holding layer modules (presentation,
                             plus domain / data only where the feature requires them) — never
                             a monolithic feature module
  :client:save:data          Reads/writes the JSON snapshot; the only client module that
                             touches a filesystem. No presentation layer — saving has no UI.
  :client:notifications:data Books the platform's local alerts at the instants core computes.
                             No presentation layer either, for the same reason: the UI it has
                             is the operating system's.
server        JVM + Ktor. Compiling stub until multiplayer starts.
iosApp/       Xcode wrapper around the client framework (pending, arrives with the iOS slice)
androidApp    Thin Android app module wrapping :client:shell (pending, when Android delivery matters)
```

## Dependency rule

Dependencies point inward to `core`; `core` depends on **nothing** but `kotlinx-serialization`
(justified in at 0.0.6 — the save format is a rule client and server must agree on; see
[decisions.md](decisions.md)). `client/*`, `server` and `sim` depend on `core`; feature modules
depend on `core` + whichever `:client:design:*` layers they actually use; `:client:shell` composes
the features. The module graph *is* the enforcement — a violating import fails to compile because
the dependency simply is not declared.

A feature declares the design layers it uses and no more, so its build file says what kind of UI it
is: Research declares no `:icon` because it draws no glyph, and the shell declares no `:component`
because it draws chrome rather than rows. `:client:design:component` is the one design module that
depends on `core`, for `ResourceKind` alone — see [decisions.md](decisions.md).

## Core purity (the load-bearing invariants)

The canonical, full statement lives in [brief.md](brief.md) — this is a faithful summary, not a
second authority:

- No clock reads, no I/O, no logging, no platform APIs, no framework types.
- Time enters as parameters (an advance function over a state and two instants — exact
  signatures live in the code once it exists, brief.md records the design intent). This makes
  the simulation deterministic, testable, fast-forwardable, and reusable unchanged on the
  server. `Instant` is the stdlib `kotlin.time.Instant` — it requires no third-party dependency.
- Randomness enters as an explicit seed; same inputs, same outputs.
- Game state changes are an append-only event log, not in-place mutation. Offline reports,
  combat reports and replay debugging all fall out of this, and it is the server persistence
  model.
- The advance-composability property (advancing in one span equals advancing in any two
  sub-spans; equation in brief.md) is a required test; everything downstream depends on it.

## Test doubles (repo-wide, not client-only)

Handwritten fakes via per-module Gradle test fixtures where the module type supports them; a
KMP module that cannot host fixtures gets a sibling `:<module>:testing` module owned by the same
layer. Never one repo-wide doubles module.

**Backtick test names in `commonTest` may not contain `, . ; : / \ < > [ ]`.** Kotlin/Native
rejects them (`Name contains illegal characters`), so a comma in a test name compiles on the JVM,
passes locally, and fails CI on the iOS targets only — learned the slow way, 2026-08-06. Write
`only the building facility shows progress while the rest stay actionable`, not
`… progress, the rest stay actionable`. `desktopTest` source sets are JVM-only and unaffected.

## Client rules

- The UI computes state from the last-updated instant when the app comes to the foreground —
  never from a running timer. Local notifications are scheduled at computed completion
  timestamps (this is the entire iOS check-in loop; the platform forbids background execution).
- The shell persists **only on discrete transitions** (an event appended to the log), never per
  tick. Everything between two events is reproduced exactly by `advance` from the saved instant,
  so an accrual-only tick has nothing new to write.
- **Saving and rescheduling notifications are one operation** (`GameSession.commit`), on that
  same trigger and against that same instant. Split, they drift: a save without a reschedule
  leaves an alert promising a build that has already finished.
- Galaxy map is a Compose `Canvas`. No game engine, settled.
