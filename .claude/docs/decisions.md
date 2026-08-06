# Decisions

Key choices and why (ADR-style, newest last). Several were settled with Davide during design —
**check here before re-litigating.**

## Kotlin Multiplatform + Compose Multiplatform, no game engine

Locked on the Notion page (2026-08-05). The genre is a UI application — lists, timers, trees,
numbers — with no render loop, so an engine (KorGE was evaluated) would cost layout, scrolling,
text input and accessibility for nothing; KorGE is also effectively single-maintainer. A SwiftUI
client lost because this game needs zero platform-native media, and KMP lets the server run the
exact simulation code. Galaxy map is a Compose `Canvas`; if it ever outgrows that, an engine view
may be embedded as a leaf, never as the foundation.

## Monorepo with server included from day one

Locked on the Notion page. `core` / `sim` / `client` / `server` (+ `iosApp` wrapper). Multiplayer
is confirmed as the destination, so the server consumes `core` from the start and the module
wiring never rots — even while `server` is a compiling stub. The alternative (client-only repo,
server extracted later) loses the guarantee that client and server agree on the simulation.

## Public repository, no license, on github.com/fardavide/oltre

Davide's explicit choice (kickstart, 2026-08-05) over private: on a Free plan only public repos
get branch rulesets, so public buys real enforcement of the PR flow. No LICENSE file — all
rights reserved, code visible but not reusable — chosen over MIT/Apache because the game may
ship commercially.

## Version lives in `gradle/libs.versions.toml` as `oltre`

Single source for all versions including the project's own, per the brief's "version catalog,
single source" rule. Semantic from 0.0.1; the root build script propagates it to every module.
Rejected: per-module version fields (drift) and gradle.properties (a second versions file).

## Latest stable toolchain, verified by build, not template-pinned

Kotlin 2.4.10, Compose Multiplatform 1.11.1, AGP 9.3.1 (new `com.android.kotlin.multiplatform.library`
plugin), Gradle 9.6.1, JDK toolchain 21. Chosen over pinning to the JetBrains template's slightly
older combo; the full local build plus CI is the compatibility proof. All plugins are declared
`apply false` in the root build script — per-module-only declaration caused KGP BuildService
classloader clashes.

## `client/` is a directory of modules, never a monolith module

Davide's correction during kickstart (2026-08-05), applying the `fardavide/Aura` shape: a locked
top-level layout does not license a monolith. `:client:shell` (composition root + entry points),
`:client:design` (theme), and one *directory* per feature holding layer modules —
`:client:<feature>:presentation`, plus `:domain` / `:data` only where required (second
correction, same day: a feature is a directory of layer modules, not a single module).
Rejected: a single `:client` module refactored "later", and single-module features.

## Test doubles via per-module Gradle test fixtures, not a doubles module

Davide's correction during kickstart (2026-08-05), BandLab-style: fakes travel with the module
that owns the interface (`java-test-fixtures` / AGP `testFixtures`), consumed as
`testFixtures(projects.x)`. A monolithic TestDoubles module is correct only under SwiftPM (Aura),
which lacks a fixtures concept. KMP modules that cannot host fixtures get a sibling
`:<module>:testing` module — still per-owner.

## Screenshot tests: Roborazzi against the desktop target

Davide's choice at kickstart. JVM-based, runs on the desktop target (the dev loop) and on ubuntu
CI without an emulator, and supports Compose Multiplatform. Paparazzi lost (effectively
Android-only); "none for now" lost (the UI is a first-class deliverable — the mockup is the
brief). Strategy: record locally, verify on CI (see the `screenshot-testing` skill).

## `.claude/` is the single agent-config root

Kickstart default: this is a Claude-only personal project today, so the `.ai/` + symlink layout
(used when several agent tools must read the same config) buys nothing yet. Switching later is a
real migration recorded here. The kickoff prompt file was deleted from `docs/` in favour of the
distilled `.claude/docs/brief.md` — the Notion page remains the design source of truth.

## v1 buildings are the mockup six; energy is a simple scaling mechanic

Settled with Davide at M2 start (2026-08-05). Buildings: Metal Mine, Crystal Mine, Deuterium
Synthesizer, Solar Plant, Robotics Factory, Nanite Factory — exactly the six the UI mockup
shows. Rejected: swapping Nanite Factory for an upgradeable Storage Depot. Energy exists in v1:
mines consume it, Solar Plant produces it, and on a deficit production scales down
proportionally. It is not a fourth resource on the rail — a warning state instead, per the
anti-overwhelm principle. Rejected: no-energy v1 (loses the mine-vs-plant build tension that
makes early upgrade ordering a real decision).

## `protect-main` ruleset, active, no bypass

Standard kickstart shape (as `fardavide/Aura`): PRs only, all four CI checks required, squash
merges only, linear history, no deletion/force-push, `bypass_actors: []` — nobody, owner
included, can route around a red check. `required_approving_review_count` is 0 because a solo
owner cannot approve their own PR. Payload committed at `.github/rulesets/protect-main.json`,
applied as ruleset id 20464541.

## iOS delivery: Xcode Cloud archive-only → TestFlight internal testing

Same shape as `fardavide/Aura`, adapted for KMP. A **branch-change start condition on `main`**
runs an **archive action with Deployment Preparation = "TestFlight (Internal Testing Only)"**;
every squash merge lands a build on TestFlight. Xcode Cloud runs **no test actions** — GitHub
Actions is the gate and `main` is PR-protected, so archives only ever see verified commits.

Xcode Cloud beat GitHub Actions + fastlane because it **manages signing itself**: no
distribution certificate, no provisioning profile, no App Store Connect API key, nothing in
GitHub secrets and no yearly cert-expiry chore. The cost is that a workflow is **server-side
state** — it lives on the App Store Connect app record, not in this repo, so it cannot be
code-reviewed or restored from git (only the ASC API `/v1/ciWorkflows` can read or edit it).

**Internal testing only, deliberately.** Apple requires a *clean* build for any workflow with an
external-tester post-action, which discards the cached derived data a Kotlin/Native build most
depends on. Internal testing has no such requirement and also skips Beta App Review.

What this repo must therefore carry, and why each would otherwise be silent:

- `iosApp/iosApp.xcodeproj` **and its shared scheme**, both committed. Xcode Cloud reads the
  product list from shared schemes (`xcodebuild -describeAllArchivableProducts`) and requires a
  project that is "continuously present" — a project generated at build time is unsupported.
  XcodeGen emits a scheme *only* when `project.yml` declares a `schemes:` block; it declared
  none, so the project exposed zero products.
- A **1024px opaque app icon** in an asset catalog. Missing icon or an alpha channel is a hard
  upload reject (ITMS-90022 / ITMS-90713 / ITMS-90717), not a warning.
- **`ITSAppUsesNonExemptEncryption`**, or every build sits in "Missing Compliance" and reaches
  no tester until answered by hand.
- **`iosApp/ci_scripts/`** (Xcode Cloud looks for it next to the `.xcodeproj`, not at the repo
  root). Xcode Cloud's environment ships macOS + Xcode + Homebrew and **no JDK**, so the
  "Compile Kotlin Framework" phase would fail with *Unable to locate a Java Runtime* (exit 65).
  `ci_post_clone.sh` installs `openjdk@21` via Homebrew — Apple's documented mechanism, and
  `sudo` is unavailable so the keg-only JDK can never be registered with `/usr/libexec/java_home`.
  Nothing a build script exports survives into the `xcodebuild` phase, so the build phase in
  `project.yml` resolves `JAVA_HOME` itself rather than relying on a workflow environment
  variable set in the App Store Connect UI (which would be one more piece of invisible
  server-side state).

Rejected: installing the JDK into `$CI_DERIVED_DATA_PATH` with a marker file to ride Xcode
Cloud's cache — the common KMP recipe. Apple documents unconditionally that "Xcode Cloud deletes
any files a custom build script creates", and the trick survives only through an undocumented
interaction with derived-data caching. Paying ~1 minute of `brew install` per build buys a
mechanism Apple actually supports.

**Cost watch.** 25 compute hours/month come with the developer membership. A Kotlin/Native
release link plus the archive is the expensive part, and `~/.konan` (~1.8 GB) is re-downloaded on
every cold machine. If hours run short, the first lever is pointing `KONAN_DATA_DIR` at derived
data — accepting the same undocumented caching the JDK install deliberately avoids.

## Persistence: a JSON snapshot owned by `core`, written only on discrete transitions

The v1 scope on Notion says "JSON snapshot save", which is what landed at 0.0.6.

**The format lives in `core`, not in a client adapter.** `kotlinx-serialization` is the one
dependency `core` now carries — the brief always allowed it, justified first, and this is the
justification: a save is a statement about the simulation, so client and server must read the
same bytes once multiplayer arrives. `GameSnapshot` is the whole `GameState` plus the instant it
is accurate as of, and `GameSave.encode/decode` are pure string functions, so core still does no
I/O. Rejected: a DTO layer in the client (`Resources` keeps its fine-unit fields internal, so the
mapping would have had to be exported from core anyway — the same coupling with a translation
step to drift).

**Only discrete transitions are saved.** The append-only event log plus the advance-composability
property means the state between two events is reproduced exactly from the last saved instant, so
a per-tick save would write a file every second to record nothing. The shell writes on load and
whenever the event log grows. A player who is killed mid-session loses nothing — not "loses
little": the reload recomputes it.

**A save that cannot be read is no save.** Corrupt, truncated and future-schema files all decode
to `DecodeResult.Failure` and the client starts a fresh colony. `SCHEMA_VERSION` is checked rather
than guessed at, and `GameSaveTest` pins the exact on-disk string — changing it requires a version
bump and a migration, and cannot happen by accident.

**Pre-release, a format change re-pins the test instead of bumping `SCHEMA_VERSION`.** The
returning-fleet field landed on `GameState` between this work being written and merged, which
changed the on-disk shape and failed the pinned-string test exactly as intended. The string was
re-pinned at version 1: no build carrying a save has shipped to TestFlight, so there is no
installed format to migrate *from*, and spending schema version 1 on a shape no player ever had
would burn the mechanism for nothing. **This stops being true the moment a TestFlight build
containing persistence reaches a tester** — from then on, every change to that string is a
`SCHEMA_VERSION` bump plus a migration, no exceptions.

**`:client:save:data` has no presentation layer**, the one deliberate exception to the
one-directory-per-feature-with-a-presentation-module rule: saving is infrastructure with no UI,
and `core` (which cannot do I/O) is the only place below it. If a save/restore screen ever
exists, `:client:save:presentation` joins it.

Davide settled the shape on review (2026-08-06), so **do not re-open it**. Two alternatives were
put to him and rejected: flattening to `:client:save` (a shared module in the shape of
`:client:design`, since the directory is named for a technical concern rather than a feature),
and folding it into `:client:colony:data` (strict feature purity). The second is the more
tempting mistake — the colony is the whole game today, so it looks like the natural owner, but
the snapshot is *whole-game* state and galaxy, fleets and research all land in it.

Save locations: macOS `~/Library/Application Support/Oltre/`, Windows `%APPDATA%\Oltre\`, Linux
`$XDG_DATA_HOME/Oltre/`, iOS the app's `Documents` directory. Android's actual needs a `Context`
and there is no Android app module yet, so `AndroidSaveLocation.directory` is set by the
application at startup; the two identical JVM `FileSaveFile` copies collapse into one shared
source set when that module lands.

## Parallel upgrades; build progress lives on the facility row

Davide's play-test feedback (2026-08-06). One build slot is replaced by one job **per facility**:
`GameState.builds` is a `Map<BuildingType, BuildJob>`, `advance` applies completions earliest
first (ties broken by building order, so the event log is deterministic), and `startUpgrade`
refuses only a second job on the *same* facility. Resources are meant to be the limiter, and now
they are the only one.

The UI follows the mechanic: the hero "in progress" card is gone and each facility row shows its
own target level, countdown, finish time and progress bar. **This supersedes the Notion UI
direction line "one hero in-progress card with a live countdown as the focal point"** — with
parallel builds there is no single build to hoist. The Notion page records the supersession
(written 2026-08-06 under the read/write rule below); the principle survives, the countdown just
lives on the row.

Open, deliberately not decided here: whether a later pressure (logistics, upkeep) caps how many
projects can run at once — Notion's "unlimited mature colonies, limited simultaneous projects"
suggests it eventually should.

## Placeholder curves: human numbers, +25% output per level, ×1.5 cost

Davide's play-test feedback (2026-08-06): production doubling on upgrade is absurd, facilities
produce too much, upgrades cost too much. Notion carries no balance numbers, so these remain
placeholders in `PlaceholderBalance` — but the *shape* is now deliberate:

- Level-1 output is 60/30/15 metal/crystal/deuterium per hour (was 3,600/1,800/900). A check-in
  reads as a number, not a wall of digits.
- Output compounds **+25% per level** instead of scaling linearly with it (`rate × level`
  doubled output on the very first upgrade). Level 10 out-produces level 1 by ~7×.
- Cost compounds **×1.5 per level** instead of ×2, from the same OGame-shaped bases; the Nanite
  Factory's base drops from 1M/500k/100k to 20k/10k/4k so it sits just past Robotics 10 instead
  of in another economy entirely.
- Cost outgrowing output is the point: the first mine upgrade pays back in ~6 hours, level 11 in
  ~31, so depth stays a decision. Asserted in `BalanceCurveTest`, not left to arithmetic.
- A new colony starts with 500 metal / 300 crystal so the first session opens on a decision
  rather than a wait. Deuterium is never granted — it is what gates the Robotics Factory.

Build durations were left alone (base minutes × level), so deep levels are gated by resources
rather than by clock. If that ever feels wrong the lever is tying duration to cost, OGame-style.

Each round of tuning is recorded in [balance-log.md](balance-log.md), with Davide's feedback in
his own words and what the change was expected to feel like — so the next session can tell a
repeat complaint from a new one, and can see what was already tried and rejected. `:sim:run`
prints the curve table that file carries, so its numbers are regenerated rather than retyped.

## Notion is read/write for agents

Davide, 2026-08-06, superseding the kickstart "never write to Notion" rule: the game's plan is
still forming, so what the build learns should land where the design lives instead of only in
chat. The guard rails, spelled out in [brief.md](brief.md): record rather than decide (design
calls stay his), append and annotate rather than overwrite, date every entry and say it came
from the build, and stay inside the Oltre page.

What the old rule was protecting against — an agent quietly rewriting a decision — is now
covered by *how* to write rather than by not writing at all. The first entries under it are the
hero-card supersession and the placeholder-curve shapes above.

## Screenshot baselines can be recorded by a manual CI job

Davide's proposal (2026-08-06), after a remote agent session could not record a baseline: a
`workflow_dispatch` job that runs `recordRoborazziDesktop`, pushes the result to the PR branch and
comments with the images for visual validation.

This narrows the earlier "never record on CI" rule rather than dropping it. What that rule
protects against is a job that re-records on every red build, which turns the assertion into a
recorder of whatever the code draws. The protection now lives in the shape of the job: it is
**manual only**, it posts before/after images into the PR, and the recording lands as its own
reviewable commit. Dispatching it is the human statement "this visual change is intended", which
is exactly the judgement the old rule was reserving for a person.

Two mechanics worth knowing before touching the workflow:

- **A push made with `GITHUB_TOKEN` starts no workflow run.** The new head commit would otherwise
  carry no checks at all, which the `protect-main` ruleset reads as unmergeable rather than as
  green — so the job dispatches `ci.yml` explicitly afterwards. Any future job that pushes to a
  PR branch needs the same step.
- **Baselines it records are Linux-rendered**, where locally recorded ones are macOS-rendered.
  Better for CI (recorder and verifier become the same renderer), worse locally: a Mac
  `verifyRoborazziDesktop` failure on an untouched baseline is cross-OS drift, not a regression.
  Rejected: making the job run on `macos-latest` to preserve provenance — it pays macOS runner
  minutes to keep the *verifier* mismatched, which is backwards.

Rejected: posting the images as an artifact link only (an artifact is a zip nobody opens during
review) and committing diff renders to the repo (build output does not belong in git). Raw URLs
on a public repo render inline in a comment, which is what makes the validation actually happen.

## Save schema 2: version 1 is retired, not migrated

0.0.7 shipped persistence to TestFlight, so the `buildQueue` → `builds` change is a
`SCHEMA_VERSION` bump rather than a re-pinned test string — that much the persistence entry
above already required. What to do with the version-1 saves already on installed builds was
Davide's call (2026-08-06): **reset them.**

A shape-only migration was written first and rejected on review. It worked — a queued job names
its own facility, so the map key was in the data — but it preserved the wrong thing. A colony
grown at the old rates keeps stocks the new curves would take weeks to earn, so converting its
shape hands back a colony that is no longer playable rather than preserving one. Rescaling the
stocks by the ratio between the curves was rejected too: it invents a number nobody decided.

`DecodeResult` therefore gains **`Obsolete`**, distinct from `Failure`. Both start a fresh
colony, so the distinction buys nothing today — it exists because a corrupt save is an accident
and a retired one is a decision, and only one of them is worth explaining to the player. The
reason string travels with the result, so a "your colony was reset, here is why" notice can be
built on it without touching core or the store. `OBSOLETE_SCHEMAS` is the list; adding to it is
how a future rebalance retires a format, and migrating stays the default for changes that are
only shape.

The version-1 test fixtures are frozen captures of what 0.0.7 wrote, asserted byte-for-byte
against the string that build pinned (git `ecbe518`). A reset test is only as good as the save
that triggers it: a fixture written from memory would prove that made-up JSON resets.

## Local notifications: the whole set, derived from state, replaced on every transition

Notion locks both the feature and the mechanism — "schedule local notifications
(UNUserNotificationCenter) at computed completion and arrival timestamps; that is the entire
check-in loop" — so 0.0.9 is an implementation, not a design call. It is the other half of what
0.0.7 started: persistence made the colony survive being closed, and this is what tells the
player it did something while it was.

**`core` owns what is coming, not just what happened.** `futureEvents(state)` is the mirror of
the event log: builds still running, a fleet still in flight, earliest first, ties broken exactly
the way `advance` applies them (completions in building order, then the arrival). Putting it in
core rather than in the client is the point — an alert, a future "while you were away" summary
and the server's eventual push scheduling must all agree with what the simulation will really do,
and reading them off the same state with the same ordering rule is the only way to guarantee it.
It stays clock-free: the caller knows what "now" is and drops what has passed.

**The pending set is replaced, never amended.** `NotificationScheduler.replaceAll` is the whole
interface. The schedule is *derived* from state exactly as the save is, so recomputing it whole
is what keeps it truthful: a build that completed, a fleet that landed, a colony reloaded from a
different save all disappear by not being in the new list. Rejected: an add/cancel pair, which
needs a record of what was scheduled last time — a second source of truth, in the one place
where being wrong means lying to the player about their own colony.

**Rescheduling rides the save's trigger.** `GameSession.commit` writes the snapshot and syncs the
alerts together, on an event appended to the log, against the session's `lastUpdatedAt`. Two
operations on one trigger because they answer the same question; separately they drift.

**Only iOS schedules anything today.** Desktop prints the schedule instead: the dev loop has the
app open, so an alert about the countdown you are watching is noise, and the checkable thing is
that the right alerts are being derived at all. Rejected there: `java.awt.SystemTray`, which
would need a timer held for the whole wait — the exact mechanism this game is built to avoid —
to buy a toast on the one platform that does not need one. Android does nothing until an
`androidApp` module exists to hold a `Context` and the API-33 `POST_NOTIFICATIONS` permission;
a stub that compiles and silently schedules nothing would look finished.

**Permission is asked on the first sync**, which is the first frame, and never again (iOS shows
the prompt once whatever you do). Not deferred to a "better moment": on iPhone the alerts *are*
the game's check-in loop, so a player who declines has declined something they can see the
shape of. A refusal is unreported and unrecoverable-in-app by design — there is no surface for
it and the game is fully playable without it.

Notification copy is a **placeholder**, marked as such in `GameNotifications`: what an alert says
is player-facing content and therefore Davide's. It says the two things a check-in alert must —
what happened, and that a decision is waiting — and the facility names are written out in full
rather than reusing the Colony row's abbreviations ("Deuterium Synth."), which exist to fit a
width a lock screen does not have.

## The shipped iOS version comes from the catalogue, not from the generated project

0.0.8 reached TestFlight labelled **0.0.7**. The bump did edit `iosApp/project.yml`, but the
label Xcode reads lives in the generated `project.pbxproj`, and regenerating that needs
`xcodegen` — macOS-only, and absent from every agent session so far. Hand-editing the generated
file is precisely what the iOS-delivery entry above forbids, so nothing could close the gap and
the drift shipped.

`ci_pre_xcodebuild.sh` already rewrote `CURRENT_PROJECT_VERSION` in that file at build time for
the same class of reason. It now also rewrites `MARKETING_VERSION` from the `oltre` version in
`gradle/libs.versions.toml`, and asserts both landed. The generated project's copies are
therefore placeholders like the build number: real for a local build, overwritten for a shipped
one.

This does **not** retire `project.yml` — it is the source the next `xcodegen generate` reads, so
a bump still edits it. What it retires is the failure mode where the only machine that can make
the repo honest is one the session does not have.

## No type-safe project accessors — they collide with `:client:<feature>:<layer>`

Adding `:client:notifications:data` next to `:client:save:data` at 0.0.9 silently broke the
shell: `implementation(projects.client.save.data)` stopped resolving to the save module, the
save jar never reached the compile classpath, and the build failed on `Unresolved reference
'save'` in a file nobody had touched.

The accessor is generated from a project's **name**, not its path, so two projects named `data`
generate one accessor and one of them wins. The module architecture — one directory per feature
holding `presentation` / `domain` / `data` layer modules — *guarantees* duplicate leaf names, so
this was never a one-off: every future feature with a `data` layer would have hit it, and
`:client:colony:presentation` had simply been the only `presentation` so far.

`enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")` is therefore gone and dependencies are
declared as `project(":client:save:data")`. Between an incubating Gradle convenience and a
module layout Davide decided, the layout wins.

What makes this worth an entry rather than a one-line fix: **it fails silently and wrongly**,
not loudly. Here the two modules had no overlapping API so it surfaced as a compile error, but
the same mis-resolution between two modules that happened to satisfy each other's imports would
have produced a *building* project wired to the wrong dependency. Rejected: renaming the layer
modules to be globally unique (`save-data`, `notifications-data`), which fixes the accessor by
disfiguring the naming convention that is the actual decision.
