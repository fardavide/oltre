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

**Amended 2026-08-07 (Davide):** `:<module>:testing` is not a sibling, it is a child —
`:client:save:data:testing` is the directory `client/save/data/testing`, a module inside a module,
which the layout rule now rejects. Read it as **a sibling named for what it doubles**:
`client/save/data-testing`, `client/featA/domain-testing`, `core-testing`. The shape was never
built, so nothing migrated. See *Module layout and layer dependencies are build rules* below for
why such a module inherits the layer it doubles.

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
reviewable commit. ~~Dispatching it is the human statement "this visual change is intended", which
is exactly the judgement the old rule was reserving for a person.~~

**Superseded by Davide, 2026-08-06: the agent dispatches the job itself.** Written as above, the
entry was read at 0.0.11 as "an agent must not dispatch", and the PR sat red waiting on a click.
That is not what the rule was protecting: the reviewable artefact is the **comment**, which puts
before/after images in the PR before anything merges, and it exists whoever pressed the button.
An agent that leaves a red check for a human to clear is not being careful, it is being slow. So:
a slice that adds or changes baselines dispatches Record screenshots against its own PR, and the
human judgement happens where it always was — reading the images the job posts, before merging.
The rest of the entry stands, "manual only" included: the job still never fires by itself.

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
check-in loop" — so 0.0.10 is an implementation, not a design call. It is the other half of what
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

## The Gradle group carries the project path, because layer names repeat

Adding `:client:notifications:data` next to `:client:save:data` at 0.0.10 silently broke the
shell: the save jar left the compile classpath and the build failed on `Unresolved reference
'save'` in files nobody had touched, while the *notifications* module — declared identically,
one line above — resolved fine.

The cause is coordinates, not code. `allprojects { group = "dev.fardavide.oltre" }` gave every
module the same group and the same version, and a Gradle module is identified by
`group:name:version`. Two projects both named `data` were therefore both
`dev.fardavide.oltre:data:0.0.10` — one component as far as resolution is concerned. Gradle
conflict-resolved the pair to a single winner and the loser simply was not there.

The module architecture — one directory per feature holding `presentation` / `domain` / `data`
layer modules — *guarantees* repeated leaf names, so this was never a one-off. It had gone
unnoticed only because `:client:save:data` was the first `data` and `:client:colony:presentation`
is still the only `presentation`. The group now carries the parent path
(`dev.fardavide.oltre.client.save:data`), which makes every module's coordinates unique by
construction, including every layer module not yet written.

What makes this worth an entry: **it fails silently and wrongly.** Here the two modules shared no
API, so it surfaced as a compile error. Two modules that happened to satisfy each other's imports
would have produced a *building* project wired to the wrong dependency.

Rejected: renaming the layer modules to be globally unique (`save-data`, `notifications-data`),
which fixes the coordinates by disfiguring the naming convention that is the actual decision.
Also rejected on the way — and recorded because it cost a CI cycle — **blaming type-safe project
accessors**. `projects.client.save.data` looked like the obvious suspect, and swapping every
dependency for an explicit `project(":path")` changed nothing: the accessor had been resolving to
the right project all along, and the component it pointed at was being dropped afterwards. That
change is reverted; `TYPESAFE_PROJECT_ACCESSORS` stays. The lesson is that a resolution symptom
is worth reading as resolution, and that "the declaration must be wrong" is a guess, not a
diagnosis.

## Navigation lives in `:client:shell`, and unbuilt tabs are screens rather than absences

The mockup's five-tab bar is the piece everything else hangs off, and it needs nothing decided,
so it went first of the remaining v1 slices (0.0.11).

**It is the shell's, not a feature's, and not `:client:design`'s.** A tab set names every feature
there is, and the module rule says features never see each other while the shell is the only
module that sees them all — so navigation can live nowhere else without breaking the direction of
the graph. `:client:design` was the other candidate and lost for a different reason: it is a
*token* module (colours, type, the content-width cap) and every visual component so far — the
resource rail, the facility rows, the fleet strip — lives with the feature that owns it. A tab bar
is chrome the shell owns, so it sits with the shell.

**A tab with no screen behind it shows an honest empty state, not nothing and not a hidden tab.**
`OltreTab.pendingWork` is the table: null once a tab has a real screen, otherwise a line saying
what will be there. Hiding the unbuilt four was rejected — the bar's job in this slice is to make
the shape of the game visible, and a bar that grows an item per release never gets to do that.
Showing a blank screen was rejected too: on a black theme it reads as a crash, not as a gap. The
copy is a **placeholder**, marked as such, for the same reason the notification copy is — what a
screen says to the player is content, and content is Davide's.

**Each feature that lands takes a parameter on `MainScaffold`.** The signature is therefore the
honest list of what exists: a tab with no parameter is a tab with no screen. Rejected: a
`content: @Composable (OltreTab) -> Unit` lambda that hands the decision back to `App`, which
would let a tab quietly fall through to the wrong screen and puts a `when` over every destination
in the one place already holding the clock, the save and the notifications.

**Selection is not restored across launches.** The colony is recomputed from the save every time
the app opens, so opening on the Colony is what the player wants to see; restoring "you were on
Fleets" restores a screen that is not built. Revisit when the other tabs are real.

**The glyphs are drawn, not imported.** They are bespoke — a ringed world, a lab ring, a rocket, a
galaxy, a fleet wedge — so no icon pack carries them; a Compose `Canvas` writing the mockup's own
24-unit paths keeps them exact and adds no dependency. That is also why they have screenshot
baselines: a path is the only kind of drawing where a typo compiles.

**Insets moved from the screen to the frame.** `windowInsetsPadding(safeDrawing)` was on
`ColonyScreen`; it belongs on the scaffold, because every tab sits in the same safe area and the
bar has to clear the home indicator whatever is above it. The tabs share the content column's
560dp cap for the same reason the resource rail does — an iPad would otherwise push them to the
screen edges — and take an equal share of the width rather than the mockup's fixed 66px, which
five of would overflow a 320dp Slide Over pane.

**`oltreRoborazziOptions` is now duplicated in two modules.** Sharing it needs a module (KMP
source sets cannot host test fixtures), which two callers do not justify and a third would. Both
copies carry the same comment saying so.

## A test says its kind in its name, and coverage is measured per kind

Coverage as one blended percentage answers no question anyone has. "78% covered" cannot
distinguish a codebase whose UI is exercised only by screenshot baselines from one where every
tap is driven, and those are very different codebases. This project is both at once: the tab
bar arrived with tests that really tap (0.0.11), while the colony's only interaction still
passes `onUpgrade = {}` to nothing. One blended number cannot show that split; four can.

So coverage is measured **once per kind of test**: unit, integration, screenshot, behaviour.
`-Poltre.testCategory` filters every `Test` task in the build, Kover reports on whatever ran,
and CI does five passes — the four kinds plus one unfiltered. Five Gradle passes is the slowest
job in CI, and it buys the only number that can say *the behaviour tests reach 12% of the
presentation module*.

The kind is carried by the **class-name suffix** (`…IntegrationTest`, `…ScreenshotTest`,
`…BehaviourTest`, plain `…Test` for unit). Rejected: a JUnit `@Tag` annotation, which is invisible
in a file tree, a stack trace and a CI log — the three places the question actually comes up;
and separate source sets, which would move a test away from the code it covers and multiply
build files. The cost of the suffix is that a misnamed test is silently miscounted, which is why
the taxonomy is a skill and not a comment.

The delta on a PR comes from a GitHub Actions cache written only by `main` and read by every
branch — no token, no external service, no third-party action. It compares against the last
`main` run rather than the PR's merge base, which is a trend and not an audit; the report says
so. Rejected: Codecov and friends (an account and a token for a single-developer repo), and
re-running the whole measurement on the merge base inside the PR job (ten Gradle passes to
sharpen a number nobody gates on).

**Nothing gates.** No thresholds, and the Coverage job is deliberately not a required check. A
coverage minimum is a design decision with a number attached, and numbers are Davide's; the
report exists so the trend is visible before anyone picks one.

## Research: three technologies behind one gate, one slot, effects as multipliers

The 0.1 research decision sheet (approved by Davide, 2026-08-06) is the design, and 0.0.12 is an
implementation of it rather than a set of calls. What is worth carrying forward is *why* the sheet
chose what it did, because those reasons constrain slices that have not been designed yet.

**One branch means a flat set behind a shared gate, not a chain.** A linear chain contains no
decision — the player researches the next thing because it is the next thing, and the only
variable is when they can pay, which the Colony screen already asks better. Three rows behind one
gate means every time the slot frees the question is *which of three*, and that question has a
different answer on day 4 than on day 11. Rejected: a chain (no decision in it), independent
parallel tracks (a tree in everything but name, unreadable at 393dp without a diagram), and more
than four technologies. Three rows also means **the flat list is the tech tree** — the whole branch
is legible on day 1 without a diagram, a graph view or a tap, which is the strongest argument
against drawing a tree in v1.

**Effects compound, and that overrules the obvious precedent.** With a linear effect (+3% per
level) against any exponential cost, payback doubles every level and the branch is dead by level 4.
Compounding keeps level 8 a live decision, and Davide's rule still holds because cost grows faster
than output. The cost: research multiplying a building curve that already compounds is
double-exponential in the long run — harmless inside 0.1 (level 6 Extraction is +59%) and the
number to watch when the horizon goes past a month.

**One slot, empire-wide, and this is where the pressure starts.** Buildings stay
unlimited-parallel; research does not. It gives the two screens different characters, which is
worth more than consistency here — the colony is limited by resources, research by time — and it
is the only scarcity research has: its costs are small next to a mine of the same era, so without
a slot the answer is always "start all three" and there is no decision left. Rejected: a queue,
which is a promise about a future the player cannot see and deletes the decision the check-in
exists to hold. This is also the first answer to Notion's "limited simultaneous projects", which
the parallel-builds entry above left deliberately open.

**Research has no building; its speed rides Robotics.** That answers "where does research happen"
without a seventh building, gives the Robotics Factory a second reason to exist, and places
research behind the deuterium wall using a gate already in the game. Rejected: a Research Lab (the
building set is closed, and a building whose only function is to permit research is a tax the
player pays for nothing visible).

**The Robotics divisor is deliberately not the one construction uses.** Research is
`base minutes × level ÷ (1 + 0.08 × Robotics)`; a build is `base minutes × level ÷ (1 + Robotics)`.
The sheet flagged the mismatch and Davide called it (2026-08-06): research keeps the gentle curve
its published tables were computed against, construction keeps the steep one the 0.0.8 round
settled. Making them agree is a rebalance of the *colony*, not of this branch, so it is not
smuggled in here. If it is ever unified, `ResearchBalance.researchDuration` and
`PlaceholderBalance.upgradeDuration` are the two places and the balance log is where it goes.

**Order of application is a rule, not an implementation detail:** building level curve, then the
research multiplier, then energy deficit scaling. So Photovoltaics raises supply *before* the
deficit ratio is computed, and Extraction's bonus is scaled down by a deficit exactly as the mine's
own output is. `AdvanceResearchTest` pins it with a case where the two orders differ by one unit —
295 against 294 — because an order that is only stated in a comment is an order that drifts.

**These numbers are decided, not placeholders**, which is why they live in `ResearchBalance` rather
than in `PlaceholderBalance` next door. `ResearchBalanceTest` pins all three published tables value
by value — 30 effect percentages, 90 costs and 30 durations. If one of them has to change, the
sheet changed, and that is Davide's call rather than a refactor.

Two rounding conventions live in `Curves.kt` and the split is deliberate. Costs use exact
arithmetic rounded once (`exactGeometric`) because the sheet's tables were computed that way, and
per-step flooring drifts a unit low by level 5 and eight units low by level 10 — a gap between the
published design and the game's own cost chips, for nothing. Effects use per-step flooring
(`compound`) because exact arithmetic there needs `1e6 × 27^level`, which leaves `Long` by level
10. The bound this forces on costs is `TechLevel.MAX = 30`, which is not a design cap in any
player-facing sense: level 30 Extraction costs 4.4 billion metal, centuries at reference rates. It
is the same kind of arithmetic guard `MAX_UPGRADE_LEVEL = 40` already is for buildings.

Two of the sheet's five open calls are **recorded rather than settled**, because nothing changes
until Davide answers them: whether effects should be linear instead of compounding, and whether
Automation — the deferred fourth technology, fully specced with numbers in the sheet and in
`balance-log.md` — joins in 0.2. The third, the Robotics divisor, is settled above. The fourth
(Enrichment's payback being the worst of the three, deliberately) and the fifth (whether research
completion notifies — it does, on the construction channel) are implemented as the sheet proposed.

## Save schema 3 migrates version 2 rather than retiring it

The opposite call from the one version 1 got, for the opposite reason. Version 1 was retired
because the 0.0.8 rebalance made a colony grown at the old rates unplayable at the new ones, so
converting its shape would have handed back something that is no longer a colony. Research is
**purely additive**: a save written before the branch existed has researched nothing and has
nothing running, which is exactly what a fresh `Research` says. There is no number to invent and
nothing to rescale, so migrating is what the persistence entry above already called the default for
a change that is only shape. Davide's call, 2026-08-06.

`MIGRATIONS` is keyed by the version being migrated *from* and applied one hop at a time, with the
envelope's version stamped by the loop rather than by each step — so a save several versions behind
is carried forward by composition instead of by a special case per starting point, and a step
cannot forget to bump. A version with no step, and any version from the future, still fail rather
than being guessed at.

The version 2 fixtures in `GameSaveTest` are frozen captures of what 0.0.11 wrote, byte-for-byte
against the string that build pinned. A migration test is only as good as the save that triggers
it: a fixture written from memory proves that made-up JSON migrates.

## The resource rail is chrome, and chrome lives in the shell

The approved Research design draws the rail on the Research screen, which made it the second
destination showing it — and the module rule says features never see each other. So the rail moved
out of `:client:colony:presentation` into `:client:shell` at 0.0.12, and `MainScaffold` draws it
above whichever destination is selected.

This is the tab bar's reasoning applied a second time, and the tab-bar entry above already ruled
out the alternative: `:client:design` is a *token* module, and every visual component so far lives
with the feature that owns it. What changed is which side of that line the rail is on. It was never
the colony's — it shows empire-wide stocks — it only looked like it while the colony was the only
screen. `ColonyUiState` lost its six stock-and-rate strings and the mapping became
`GameState.toResourceRailUiState` in the shell, still reading the *effective* rates so what the
rail says is what `advance` will really accrue.

`MainScaffold` also stopped branching on `pendingWork` and now dispatches over `OltreTab`
exhaustively. With one destination built, "null means show the colony" was the same statement; with
two it is the bug where a new tab silently shows its neighbour.

**Two duplications were left in deliberately, both under the rule 0.0.11 set** — two callers do not
justify a shared module, a third does. `oltreRoborazziOptions` is now in its **third** copy, so that
threshold is met and extracting it (KMP source sets cannot host test fixtures, so it needs a
module) is the next slice's to do, not this one's: it is a build-layout change with nothing to do
with research. The cost chip and its ui-state are duplicated between the colony and research
presentation modules at twelve lines, which is a cheaper price than reopening what `:client:design`
is for.

## Module layout and layer dependencies are build rules, not review rules

Davide's call (2026-08-07). Five rules, checked while Gradle configures so a violation breaks the
**IDE sync** rather than waiting for a reviewer: a module cannot contain another module; `domain`
cannot depend on `data` or `presentation`; `presentation` cannot depend on `data`; `data` cannot
depend on `presentation`; and only a test source set may reach a `-testing` module. Rule 1 lives in
`settings.gradle.kts` — the earliest point Gradle evaluates anything — and rules 2–5 in the root
`build.gradle.kts`. Full statement and failure
messages: the `module-rules` skill.

**The graph was already clean; what was broken was the writing.** The audit found no violating
dependency anywhere in the nine modules. It found the *convention* for testing modules mandating a
rule-1 violation in three places (this file, `architecture.md`, the `architecture` skill), and the
next module due to land — the shared `oltreRoborazziOptions` — being the one that would have hit
it. Nothing was built on the old wording, so the fix was three documents.

**Layer is the last path segment, which is what makes the shell exempt without an allowlist.**
Only `domain`, `data` and `presentation` are layers. `:client:shell` holds real Compose UI *and*
depends on `:client:save:data` and `:client:notifications:data` — and that is the composition
root's job, which is also why nothing depends on it. Rejected: splitting the shell's UI into a
`:client:main:presentation` (moves ~600 lines and four screenshot baselines to overturn two
decisions taken on their own merits — navigation at 0.0.11, the rail at 0.0.12), and inverting
`GameStore`/`GameNotifications` behind interfaces to make the shell obey rule 3. Naming the rules
after layers means the exemption costs no allowlist and no annotation: a module is constrained
exactly when it calls itself a layer.

**Features seeing each other warns rather than fails.** Davide's call in the same pass: the rule is
real — it is what sent the tab bar and the resource rail into the shell — but its exceptions are
worth weighing one at a time, and a hard failure decides them in advance. The warning surfaces on
the build that introduces the edge, because that is the build whose script change invalidates the
configuration cache.

**Rule 1 reads the disk, and how it reads it is load-bearing.** Scanning for `build.gradle.kts`
rather than walking the `include` list catches a module directory created and never included.
The first implementation used `File.walkTopDown()` and was wrong in a way that only showed up
under test: the walk is not tracked as a configuration-cache input, so adding a nested module
while no build script changed reused the cache entry and passed. Explicit `listFiles()` calls
from the script body *are* tracked. Verified both ways before landing.

**A testing module is a sibling named for what it doubles, and inherits its layer.**
`client/save/data-testing` beside `client/save/data`, `core-testing` beside `core` — Davide's
call, replacing `:<module>:testing`, which named a child and breaks rule 1. The layer check
strips the `-testing` suffix, so `presentation-testing` cannot reach data either: without that
the rule holds on the direct edge and leaks on the one hop through the fakes, which is the whole
of the hole. Nothing is built on this yet; it is the shape the next one takes.

**Only a test source set may reach a `-testing` module (rule 5).** Davide's call, and the thing a
plain module cannot say for itself: `testFixtures(projects.x)` is on the test classpath by
construction, but `implementation(projects.saveDataTesting)` is on whatever classpath asked, and
nothing about the line admits it is fakes. Read as *production* source sets, because the literal
reading — no non-testing module may depend on a testing one at all — forbids the only thing a
testing module is for. So `commonTest`, `desktopTest`, `androidHostTest`, `testFixtures` and the
iOS test targets stay legal; `commonMain` does not, and only the offending configurations are
named in the failure. A testing module may depend on another from `main`: it is already fakes.
A configuration counts as a test one if it starts with `test` or contains `Test` — matched on the
camel hump, so a source set called `latest` is not quietly a place fakes are allowed.

**No unit tests, deliberately.** Build-script logic is not reachable from a test source set
without a `buildSrc`, which would add a compilation to every build to test forty lines. Verified
instead against a throwaway Gradle build carrying the identical rule code — every forbidden edge,
a test-only dependency, a nested module directory, the shell's allowed edges, the cross-feature
warning, and Oltre's real nine-module graph — plus `./gradlew help` on this repo to prove rule 1
runs before anything else does. If the rules grow past this, `buildSrc` + TestKit is the next
step, not more sandboxes.
