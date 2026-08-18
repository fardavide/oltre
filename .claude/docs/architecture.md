# Architecture

## Module map

```
core          KMP: jvm, iosArm64, iosSimulatorArm64, android. Pure model + rules.
sim           JVM CLI. Headless balancing harness; fast-forwards weeks in ms. Never ships.
client/       Directory of KMP + Compose Multiplatform modules (desktop, iOS, Android):
  :client:shell    Composition root + every platform's entry point (desktop main(), iOS
                   MainViewController(), Android MainActivity)
  :client:design/  The design system, as a directory of layer modules — split the way Compose
                   splits itself, by dependency direction and rate of change:
    :core          Tokens: palette (from docs/ui-mockup.html), theme, bundled font, layout caps
    :icon          Drawn glyphs (Canvas paths, never an icon font — see screenshot-testing)
    :component     Styled widgets with no single feature owner (cost chip, progress bar, …)
    :format        Which numbers and durations to show, and how to round them. No Compose
    :text          What the game *says*: `TextRes`, the `Strings` catalogue, `Translations` and
                   `English`. No Compose either, and for a stronger reason — a string is built in
                   a presentation module hours before it is drawn, and a notification's string is
                   built outside composition entirely
    :screenshot-testing  Roborazzi options, shared by every screenshot test; main source set
  :client:dispatch/  The dispatch sheet, which belongs to no tab: Galaxy raises it from a world
                     row and Fleets raises it from a landing, and features may not see each
                     other. Shaped like a feature and excluded from the cross-feature warning by
                     name, exactly as :client:design is — see below
    :ui            The sheet and the models it renders
    :presentation  What a world would give a fleet, what it refuses and why. No Compose
  :client:world/     What a world looks like: one Canvas drawing a face from four core traits.
    :ui              Two features draw it — the Galaxy row and the Fleets worked list. NOT in the
                     design system: "Design system should not contain such full-ui components"
                     (Davide, 2026-08-16)
  :client:<feature>:<layer>  One directory per feature, holding layer modules (presentation,
                             plus domain / data only where the feature requires them) — never
                             a monolithic feature module
  :client:save:data          Reads/writes the JSON snapshot; the only client module that
                             touches a filesystem. No presentation layer — saving has no UI.
  :client:notifications:data Books the platform's local alerts at the instants core computes.
                             No presentation layer either, for the same reason: the UI it has
                             is the operating system's.
  :client:debug:{domain,data,presentation}  The shake-to-open debug menu. The first feature to
                             hold all three layers: what a debug action decides is arithmetic,
                             the accelerometer is a device service, the sheet is a screen.
  :client:tilt:{domain,data} Which way the device is being held, for the parallax on the sky
                             behind every destination. No presentation layer — what it feeds is
                             a Canvas the shell already owns. `domain` depends on nothing at
                             all, not even core (see the dependency rule below).
server        JVM + Ktor. Compiling stub until multiplayer starts.
iosApp/       Xcode wrapper around the client framework. An Info.plist, an asset catalogue and
              a few lines of Swift hosting MainViewController(). Not a Gradle module
androidApp    Android packaging of :client:shell. A manifest, a theme and the launcher icons —
              no Kotlin at all; the manifest names MainActivity across the module boundary.
              The one module allowed to depend on the composition root (rule 7)
```

## Dependency rule

Dependencies point inward to `core`; `core` depends on **nothing** but `kotlinx-serialization`
(justified in at 0.0.6 — the save format is a rule client and server must agree on; see
[decisions.md](decisions.md)). `client/*`, `server` and `sim` depend on `core`; feature modules
depend on `core` + whichever `:client:design:*` layers they actually use; `:client:shell` composes
the features. **`:client:tilt:domain` is the one module that depends on nothing at all** — which way
a device is being held is geometry, and it has no more to do with a colony than it has with a
screen. Read the rule as a ceiling rather than a floor: a module that needs less than `core` takes
less. The module graph *is* the enforcement — a violating import fails to compile because
the dependency simply is not declared.

A feature declares the design layers it uses and no more, so its build file says what kind of UI it
is: Research declares no `:icon` because it draws no glyph, and the shell declares no `:component`
because it draws chrome rather than rows. `:client:design:component` is the one design module that
depends on `core`, for `ResourceKind` alone — see [decisions.md](decisions.md).

**Features never depend on each other, and two things are now shaped like a feature and are not
one.** `:client:dispatch` is the sheet; `:client:world` is the drawn face a row is identified by.

The sheet came first. One verb is raised from two tabs: a run starts from a world row on
Galaxy and, since #62, from a landing on Fleets. Davide ruled out both places it could otherwise
have gone — the shell (*"We absolutely do not put code in shell!"*, 2026-08-13) and
`:client:design:component`, whose one `core` edge is `ResourceKind` and which has no business
reading a `GameState`. So the sheet is its own directory of layer modules, and `featureOf` in the
root build script excludes it by name for `design`'s reason: every consumer is a cross-feature edge
by construction, and a warning that fires on every clean build is a warning nobody reads. **What
makes that safe is that nothing points out of it** — `:client:dispatch:*` reaches `core` and the
design system and no feature at all, so it cannot become the back door one tab reaches another
through. `:client:world:ui` was the first name added under that test and it passes it the same way.

**Why the portrait is not in the design system**, since it is the obvious place and was proposed
there first: Davide's call, 2026-08-16 — *"Design system should not contain such full-ui
components."* A cost chip or a section label is vocabulary. A procedural drawing of a planet from its
temperature, gravity, pressure and hazards is a feature's worth of decisions, and hosting it would
have widened `:client:design:component`'s single `core` edge — `ResourceKind`, argued for at length
in its own build file — to four more types on the way past. **The test for a shared surface is not
"do two features use it", it is that plus "is it vocabulary or is it a screen".**

## Module rules

Eight rules, checked while Gradle configures, so a violation fails the **IDE sync** and not only
the build. Full statement, failure messages and worked examples: the `module-rules` skill.

1. **A module cannot contain another module** — a directory is either a folder or a module. When
   a module needs a second beside it, the parent becomes a folder and both become siblings:
   `dir/moduleA` + `dir/moduleA/moduleB` → `dir/sub-dir/moduleA` + `dir/sub-dir/moduleB`.
   Checked in `settings.gradle.kts`, against the disk rather than the `include` list, so a module
   directory that was never included is caught too.
2. **`domain` may not depend on `data` or `presentation`.**
3. **`presentation` may not depend on `data`.**
4. **`data` may not depend on `presentation`.**
5. **Only a test source set may reach a `-testing` module** — `commonTest`, `desktopTest`,
   `androidHostTest`, `testFixtures` and the iOS test targets all qualify; `commonMain` does not.
   A plain module cannot say "tests only" the way `testFixtures(projects.x)` does, so the build
   says it. A testing module may depend on another testing module from `main`: it is already
   fakes, so there is nothing to leak into.
6. **`core` may not depend on any module.** It is the centre: everything points at it, it points
   at nothing. Absolute, unlike rule 5 — core already hosts its own test helpers in `commonTest`.
7. **Nothing may depend on `:client:shell` except `:androidApp`.** This is what makes the shell's
   exemption from rules 2–4 safe rather than merely convenient: it may see every layer precisely
   because nothing sees it. The carve-out is an allowlist of one name, settled at 0.2.0 against
   the real module rather than the hypothetical one this rule used to anticipate: AGP 9 will not
   let a KMP module apply `com.android.application`, so the Android wrapper cannot be the shell
   itself; `iosApp/` already has the same edge and escapes only by not being a Gradle module; and
   every dependency the shell declares is `implementation`, so the wrapper sees `App()` and not
   one layer module. The argument in full is in [decisions.md](decisions.md).
8. **`sim` and `server` may not depend on a `client/*` module.** Either would silently acquire a
   Compose dependency by reaching one.

The root project is exempt from 6–8: it is the build rather than a module, and it holds a
`kover(...)` dependency on every module including `:client:shell`.

Rules 2–8 are checked in the root `build.gradle.kts` and cover **test source sets too** — a
`commonTest` dependency couples the modules exactly as much as a `commonMain` one. A module's
layer is the last segment of its Gradle path, so only `domain`, `data` and `presentation` are
layers; `:core`, `:sim`, `:server`, `:client:design` and `:client:shell` are not, and are
unconstrained. That is deliberate for the shell: the composition root is the one module allowed
to see every layer, which is why nothing depends on it.

Separately, **a feature depending on another feature is warned about, not rejected** — the rule is
real, but its exceptions are worth weighing one at a time.

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

Handwritten fakes via per-module Gradle test fixtures where the module type supports them. Never
one repo-wide doubles module.

A test source set is not visible to consumers, so a fake that a **second** module needs has to
live somewhere publishable. On JVM/Android that is a test-fixtures source set; KMP cannot host
one, so there it is a module — a **sibling of the module it doubles, named for it**:

```
client/save/data       ->  client/save/data-testing      (:client:save:data-testing)
client/featA/domain    ->  client/featA/domain-testing   (:client:featA:domain-testing)
core                   ->  core-testing                  (:core-testing)
```

Davide's call (2026-08-07), replacing `:<module>:testing`, which named a *child* and breaks rule 1.
A testing module **inherits the layer it doubles** and its restrictions with it — the layer check
strips the `-testing` suffix — so `presentation-testing` cannot reach data either. Without that,
the rule holds on the direct edge and leaks on the one hop through the fakes.

**One shape, always `-testing`.** A module that doubles nothing — shared test *config* rather than
a fake — still takes the suffix, and names what it is instead of what it doubles:
`:client:design:screenshot-testing`. It landed at 0.0.14 as `:client:design:testing`, which rule 5
did not recognise, so nothing stopped a `commonMain` pulling Roborazzi into the shipped app.

Inside a single module none of this applies: `commonTest` is the answer and no module is involved.
`FakeSaveFile` lives in `client/save/data/src/commonTest` and always will.

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
