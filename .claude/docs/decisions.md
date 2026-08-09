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

**Desktop prints the schedule rather than raising it**: the dev loop has the app open, so an
alert about the countdown you are watching is noise, and the checkable thing is that the right
alerts are being derived at all. Rejected there: `java.awt.SystemTray`, which would need a timer
held for the whole wait — the exact mechanism this game is built to avoid — to buy a toast on the
one platform that does not need one. **Android did nothing until 0.2.0**, when the app module
arrived to hold a `Context` and the API-33 permission; see *Android books its alerts through
AlarmManager* below.

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

## `:client:design` is a family of layer modules, split the way Compose splits itself

Davide's call, 2026-08-07, and the thing 0.0.14 exists to do. The 0.0.11 rule came due:
`oltreRoborazziOptions` had reached three copies, and `PowerMark`, the cost chip and the ui-state
formatters had each reached two — not by anyone duplicating deliberately, but because the resource
rail moved to `:client:shell` at 0.0.12 and took half of each pair with it.

**What actually expired was not the rule, it was its premise.** The tab-bar entry above says
`:client:design` is a *token* module and "every visual component so far lives with the feature that
owns it". That held while every component had one owner. `PowerMark` now has none: the rail is the
shell's chrome and the facility card is the colony's, and the bolt belongs to neither. So the
question was never "should we relax the rule", it was "where does a component with no owner live".

Three options were put to Davide. He rejected **widening `:client:design` into one shared module**
(it makes the module a bag: tokens, glyphs, widgets and string helpers with nothing in common but
being shared) and **extracting only the test helper** (it fixes the copy that is at three and leaves
the drawn glyph — the one where a typo compiles — in two). He chose a **granular family**, asking for
the shape Compose uses on itself:

| Module | Holds | Why it is separate |
|---|---|---|
| `:client:design:core` | `OltreColors`, `OltreTheme`, `oltreMono` + the bundled font, `OltreLayout` | The tokens. Everything else in the family depends on this and on nothing else. |
| `:client:design:icon` | `PowerMark` | The `material-icons` seat: a corpus of hand-written vector paths that grows one entry at a time, and the one category where a typo compiles rather than failing. |
| `:client:design:component` | `CostChip` + `CostChipUiState`, `ProgressBar`, `SectionLabel` | The `material3` seat: styled widgets with no single feature owner. |
| `:client:design:format` | `toChipLabel`, `toCountdown`, `pad2`, `groupedByThousands` | **No Compose reaches it** — so it needs neither the plugin nor the compose compiler, and its tests are plain unit tests. A mechanical boundary, not a taxonomic one. |
| ~~`:client:design:testing`~~ `:client:design:screenshot-testing` | `oltreRoborazziOptions` | A testing module in the sense of the fixtures entry above, in the **main** source set — which is the whole reason the helper was copied three times. |

**The testing module was renamed hours later**, when the module rules landed and rule 5 turned out
to match on a `-testing` suffix this name did not have. See "One shape for testing modules" below —
the row above is what 0.0.14 shipped, not what the tree holds now.

The criterion is Compose's own, and it is worth stating because it is what decides the *next* one:
**dependency direction and rate of change, not subject matter.** That is why `format` is split off
over "it holds strings" and why `icon` is split off over "it holds pictures".

`:client:design` therefore stops being a module and becomes a *directory* of layer modules — the
same shape every feature directory already has, which is why the architecture needed no new concept
to absorb it.

**Categories deliberately not created**, because an empty category is how a design system starts
lying about itself: `:motion` (nothing animates), `:layout` (`OltreLayout` is two constants and
belongs in `core` until there are real layout composables), `:canvas` (the galaxy map is slice #5 —
inventing its shape now is inventing).

**The five `TabIcon` glyphs stayed in `:client:shell`.** Davide's call, against the consistency
argument: they have exactly one caller, and the rule is that a component lives with its owner until
a second module needs it. Being *the same kind of thing* as `PowerMark` is not the same as being
shared, and moving them would have widened a diff whose whole claim is that nothing moved.

**`SectionLabel` was shared because the two copies were two variants of one component** — identical
in every token, differing only in Research appending a rule to the heading — which is the test
Davide set for it. The shared version keeps the bare case out of the `Row` the two-part case needs:
a `Row` around a single `Text` almost certainly measures the same, and "almost certainly" is not a
claim an extraction that must not move a pixel is allowed to make.

**`:client:design:component` takes an inward edge to `core`, for `ResourceKind` alone.** The palette
in `:client:design:core` already names all three resources (`OltreColors.metal` / `.crystal` /
`.deuterium`), so the design system already carries this vocabulary as strings; taking the real enum
is strictly less duplication than that. Rejected: a design-owned tint enum, which buys independence
from `core` by making every caller translate `ResourceKind` into it — reintroducing per feature
exactly the drift the module exists to remove.

**The proof that nothing moved is the baselines, and it only works in that order.** The six research
baselines were recorded *before* the extraction commit (they had never been recorded at all — see
below), so the extraction had to verify green against images it did not produce. A screenshot check
that passes only because the baselines were re-recorded afterwards proves nothing; this one was set
up so that any pixel movement is a failure rather than a re-record.

## A merged PR is not a passed PR, and a dispatched run is not a required check

`main` was red from 0.0.13 until 0.0.14 repaired it, and the way it got there is worth an entry
because the mechanism is still live.

PR #16 was merged at 22:10:21 while its checks were still running. They completed at 22:11:3x with
**Unit tests, Screenshot tests and Coverage all failing**: `TestResourceRailUiState` never got the
`throttled` argument that `ResourceRailUiState` gained when the rail moved into the shell, so
`:client:shell:compileTestKotlinDesktop` did not compile — and all three of those jobs need the
desktop test classes.

**The `protect-main` ruleset did not stop it, and that is the part to remember.** The checks were
attached to a `workflow_dispatch` run, and a dispatched run's check runs are not associated with the
pull request for branch-protection purposes. The 0.0.12 entry above already recorded that
`gh pr checks` misreports dispatched runs — what it missed is that the ruleset misreads them the same
way, so the consequence is not a confusing display, it is **an open gate**. A PR whose checks were
dispatched by hand is a PR with no enforced checks: read the conclusions yourself, from
`repos/.../commits/<sha>/check-runs`, before merging.

The second half was quieter: **the six research baselines were never recorded at all.** Record
screenshots was never dispatched for #16, so `client/research/presentation/src/desktopTest/
screenshots/` did not exist and `verifyRoborazziDesktop` had six missing goldens on top of the
compile error. Both failures were invisible in the merged result because both live in test code —
Xcode Cloud runs no test actions, so 0.0.13 shipped to TestFlight as the build it would have been
either way. **A red suite and a broken game are not the same thing**, and the repair therefore
carried no version bump: the versioning skill reserves patch for corrections with user-visible
effect, and this had none.

## Module layout and layer dependencies are build rules, not review rules

Davide's call (2026-08-07). Eight rules, checked while Gradle configures so a violation breaks the
**IDE sync** rather than waiting for a reviewer: a module cannot contain another module; `domain`
cannot depend on `data` or `presentation`; `presentation` cannot depend on `data`; `data` cannot
depend on `presentation`; only a test source set may reach a `-testing` module; `core` depends on
no module; nothing depends on `:client:shell`; `sim` and `server` never reach into `client/*`.
Rule 1 lives in `settings.gradle.kts` — the earliest point Gradle evaluates anything — and rules
2–8 in the root `build.gradle.kts`. Full statement and failure
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

**The graph points inward, and both ends are sealed (rules 6–8).** Davide picked these three from
the *Dependency rule* section of `architecture.md`, which until now was enforced only by nobody
having typed the line. Rule 7 is the load-bearing one: it is what makes the shell's exemption from
rules 2–4 *safe* rather than merely convenient — the shell may mix layers precisely because
nothing depends on it, so what it mixes cannot travel. Remove rule 7 and the exemption is a hole.
The **feature-module allowlist was rejected** in the same pass, because it would have made
cross-feature a hard failure and reversed the case-by-case call above.

Rule 7 **will fail the pending `androidApp` wrapper**, which `architecture.md` documents as
depending on `:client:shell`. Written literally rather than with a speculative carve-out for
platform entry points: the module does not exist, so the argument is better had when there is
something real to have it about. The root project is exempt from 6–8 — it is the build, not a
module, and it holds a `kover(...)` dependency on every module including the shell.

**One shape for testing modules, so `:client:design:testing` was renamed.** 0.0.14 landed it under
the old wording, hours before this rule did. The name is rule-1 legal — `client/design` is a folder
now, so `testing` is a sibling of `core` and `icon` — but rule 5 matches on the `-testing` suffix,
so nothing stopped a `commonMain` depending on it and pulling Roborazzi into the shipped app.
Davide chose one shape over teaching the rule two, so it is `:client:design:screenshot-testing`:
it doubles nothing, so it names what it is. Rejected: widening the check to accept a bare
`testing` segment, which costs nothing in the build but leaves two shapes to choose between.
Its Kotlin package stays `dev.fardavide.oltre.client.design.testing` — a dash is not a legal
package segment, and renaming it would touch eight imports to no effect.

**A self-edge is not a dependency, and Kover creates one per module.** The first CI run failed
every job with `:core -> :core` and `:client:shell -> :client:shell`, both `declared in: kover`.
Kover is applied to every subproject and puts each into its own `kover` configuration, so every
module declares a dependency on itself; read literally that is core depending on a module and
something depending on the composition root. Self-edges are now dropped where the graph is
collected. **This is the failure the sandbox was structurally unable to catch** — it mirrored the
dependencies each build file *declares*, and this edge is injected by a plugin. The sandbox now
applies Kover for that reason, which reproduced the failure exactly before the fix.

**No unit tests, deliberately.** Build-script logic is not reachable from a test source set
without a `buildSrc`, which would add a compilation to every build to test forty lines. Verified
instead against a throwaway Gradle build carrying the identical rule code — every forbidden edge,
a test-only dependency, a nested module directory, the shell's allowed edges, the cross-feature
warning, and Oltre's real nine-module graph — plus `./gradlew help` on this repo to prove rule 1
runs before anything else does. If the rules grow past this, `buildSrc` + TestKit is the next
step, not more sandboxes.

## The galaxy is a seed, not a map: schema 4 stores what the player changed

The galaxy decision sheet (2026-08-07) is the design and 0.0.15 is an implementation of it, the way
0.0.12 implemented the research sheet. What is worth carrying forward is the handful of choices the
sheet left to the build, and the one place its own numbers disagree with each other.

**Nothing about the galaxy is serialised except the seed and the player's own edits.** 4,700 worlds
of traits would dwarf the entire rest of the snapshot, so `GalaxyState` is a seed, the home
coordinate, the surveyed set and a list of who holds what — and `worldAt(seed, coordinate)`
regenerates any world on demand. `GameSaveTest` asserts that no trait name ever appears in the
encoded save, because the day one does is the day the format stopped being affordable.

**Two generation properties are load-bearing and both are tested rather than trusted.** *Locality* —
a world is generated without touching its neighbours — is what lets slice 5's `Canvas` render a
viewport lazily instead of holding a galaxy in memory. *Sub-stream stability* — every axis is drawn
from its own named stream, hashed from the world's seed and an axis tag — is what stops the next
three slices rerolling everyone's map when they add a field to `World`. The obvious implementation,
a single sequential generator per world, silently breaks the second one; `GalaxySubStreamTest` exists
to fail loudly when someone writes it. The hash is SplitMix64's finalizer written out rather than
`kotlin.random.Random`, because the map is part of the save format and a stdlib generator whose
internals changed would reroll every installed player's galaxy.

**Every axis is an integer in a named unit — milli-g, milli-atm, whole °C — not a `Double`.** The
same reason the rest of core is integer: `advance` has to give the same answer on the JVM, on
Kotlin/Native and on the server, and a galaxy that differs by one unit between platforms is a
different galaxy. Rejected: doubles with a documented tolerance, which makes every pinned test a
question about the last bit.

**`GameState.initial` takes a galaxy seed rather than defaulting one.** A default is precisely how
every player ends up in the same galaxy, and core cannot mint one — it reads no clock and no random
source. So the composition root does: `resume` seeds the map from the instant the colony was founded,
which keeps it a pure function of its arguments. The ~60 existing tests that do not care which map
they get keep their no-argument call through an extension on the companion declared in `commonTest`,
so the terseness is available to tests and unavailable to production. Rejected: a default seed
(silently identical galaxies), and editing 60 call sites to pass one (a mechanical diff that would
have buried the change it was attached to).

**Schema 4 migrates 3, and the migration mints its seed from the save's own `lastUpdatedAt`.** The
galaxy is purely additive — a colony saved before the map existed has surveyed nothing and holds
nothing but its home world, which is what a fresh `GalaxyState` says — so this migrates rather than
retires, the default the persistence entry above sets for a change that is only shape. The seed has
to come from the file's contents because a migration is a pure function: a seed drawn at random would
hand the player a different galaxy every time the app reopened, until the first commit happened to
write one down. Two saves from different instants still get different maps.

**Ownership is a list of records, not a map keyed by coordinate.** JSON cannot use a structured
object as a key at all; the alternative was `allowStructuredMapKeys`, which changes how the *whole*
save format encodes maps — every entry becoming a flat `[key, value]` array — to buy an unreadable
save. Rejected for one field's convenience.

**`advance()` is untouched and no hook was left for it.** Nothing about the galaxy changes with time
in 0.2: it is fixed the instant the seed is minted, and surveying and ownership change through the
save's galaxy state rather than through the clock. Said in a comment in `GalaxyGeneration.kt` so the
next session does not add a speculative per-tick entry point.

### One §9 target was unreachable by any constants, so the target moved and the axes rebalanced

The sheet says its §9 targets outrank its §8 constants, so the constants should move when they miss.
They do miss — and one of the targets cannot be reached by moving them. Measured over all 15,000
slots: `passes every band` 2.63% against a 1–2% target, `fails exactly one axis` **17.55% against a
35–45% target**, `passes and clears 0.90` 0.71% against ≤0.5%.

The middle row is not a tuning miss. With three independent axes the first row is `abc` and the
middle one is `ab + ac + bc − 3abc`; holding `abc` inside 1–2% caps the middle row near **16%** for
any three comparable axes. Reaching 35% needs pass rates around 0.06 / 0.58 / 0.59 — one axis
blocking 94% of worlds and two waving everything through, which is the single-habitability-score
design §1 rejected, arrived at from the other side.

**Davide's call, delegated to the build (2026-08-07): keep the three comparable axes and correct the
target.** Gravity went 0.55…1.45 g → **0.65…1.40**, pressure 0.4…3.0 atm → **0.5…2.6**, and the
worth-it threshold 0.90 → **0.92**. Temperature was left alone — it was already the tightest axis,
and its band is the one tied to the slot formula that makes position a trait — so the other two were
brought *down to meet it*. All three now gate **25.9 / 25.3 / 25.0** per cent, which is the property
§1 actually needs and which hitting the old row 2 would have destroyed.

Result: `passes every band` **1.81%**, `fails exactly one` **13.88%** against a corrected 12–18%,
`settleable` **0.35%**, and each adaptation level still roughly doubles the settleable count
(17 → 40 → 105 → 218). [balance-log.md](balance-log.md) round 5 has the write-up and says which
lever to reach for if it plays wrong — the threshold for scarcity, all three bands together for the
"come back later" pile, never one band alone.

**The yield model was never touched, because it was right.** Its own unrun prediction of a median
passing world at "~0.84" measured 0.85. What was wrong was which worlds pass, not what they are
worth — which is why the last row moved by raising the threshold rather than by reweighting
richness. Hazards landed on 45.6% of worlds, as specified.

### Smaller calls the sheet did not make, all recorded in the balance log

Star class distribution (equal thirds assumed — nearly free, because each class's habitable orbits
shift with its offset), where home is (the first world walking from a seeded start that the unaided
species tolerates), and what `Settleable` carries (the raw yield score, because "the yield grade" is
named in §3 but its bands never are). Each is marked as assumed at the point in the code where it is
made, rather than left to look decided.

Also flagged rather than fixed: `Coordinates(galaxy, system, position)` already exists for
`ReturningFleet.origin` and is now a weaker twin of `GalaxyCoordinate`, which is bounded to the real
coordinate space. Folding the two together is a fleets change and slice #7 owns fleets.

## `./gradlew build` was failing on `main`, on an edge nobody declared

Found while building 0.0.15 and verified against a clean checkout: the module dependency rules
rejected `:core -> :client:*` for all nine client modules, declared in
`swiftPMDependenciesForLockFilesMetadataClasspathDependencies`. The Kotlin plugin's SwiftPM export
hangs that configuration on every module and fills it with every project in the build, so the rule
read it as core depending on the whole client.

This is the second artefact of exactly this kind — the first was Kover's self-edge, recorded above —
and the same lesson: **the rule is about what a build file declares, and a plugin-created
configuration declares nothing.** Filtering to declarable configurations does not help (this one is
declarable), so it is excluded by name beside the self-edge filter.

Worth knowing *why it went unnoticed*: the configuration is only realised once the iOS targets are,
so `./gradlew :core:jvmTest` and every narrower task stayed green, and CI's own jobs never provoked
it. A rule that runs at configuration time can therefore be broken for every developer running the
documented build command while every required check passes.

## The Galaxy screen is the orbit page, and the system selector is the feature's own

Claude Design returned two directions for slice 5 and recommended the first; 0.0.15 builds it. Both
replaced the mockup's `◀ 2:118 ▶` stepper, which is 250 taps to cross a galaxy and 1,000 to cross
the map. What separated them was **what the map is a map of.**

**1a, the orbit page — one system, its fifteen orbits drawn once, hot to cold.** Chosen because on
the day this ships 1b's galaxy field is 250 dots of which one is yours and six are relays, which is
a picture of how much you do not know. 1a's map has four dots and is still working: it shows the
eleven *empty* slots and therefore the shape of a system, it puts the hot end and the cold end on
screen together, and it is the only place a player can learn that slot 13 means cold without a
sentence telling them. The galaxy field becomes the better screen the week fleets ship and the map
starts filling in — which is an argument for building it then, as the zoom-out it is asking to be.

**Which system is on screen is the galaxy feature's state, not the shell's.** Navigation between
tabs lives in the composition root because a tab set names every feature and only the shell may see
them all. That argument does not reach a system selector, which names nothing outside this module —
so `GalaxyScreen` holds it, and `GalaxyPage` beneath it is the stateless half the screenshots and
the robot drive. It is the first screen in the app with state of its own, and the split is what
keeps it testable.

**The map is a Canvas, for the reason `PowerMark` is.** A circle from a shaped `Box` resolves
through the platform's shape renderer, and baselines are recorded on macOS and verified on Linux.
The star is the one gradient in the app and earns it by being a lit sphere.

**Fixtures for the two real systems are generated, not hand-written** — the opposite of the choice
`:client:research:presentation` made, and deliberately. Research freezes its fixtures so a baseline
moves only when the *screen* moves; here the generation constants are themselves pinned value by
value by `GalaxyBalanceTest` and `GalaxyDistributionTest`, so a change that moves these numbers is a
design decision that *should* redraw the images. The hand-written version drifted from the mapper's
own formatting within the hour and rendered numbers the app would never produce. `everyVerdictUiState`
stays hand-written because it has to: Barren, Settleable, Occupied and a relay have no real example
on the shipped seed.

**A value and its unit are joined by U+00A0.** The blocked line is the longest on the screen and
does wrap at 393dp on a three-axis world — the design expects that at 320dp and tolerates it here —
but breaking between a number and its unit leaves "atm" alone on a line, which reads as a defect
rather than as a wrap. The character is invisible in a diff, so the source says so where it is used.

**Galaxy left `OltreTab.pendingWork`,** so the shell's `unbuilt_tab_galaxy` baseline is gone and
Shipyard inherits the empty-state coverage. Two destinations are still unbuilt and the test that
covers them filters on `pendingWork` rather than naming tabs, so the next slice to land needs no
edit there.

### What the design asked for and what the build could answer

Three of its six calls were data the build already had, and the answers are in `balance-log.md`
round 5 and in `:sim:run`'s new home-system table. Two of those answers changed the screen:

- **The home system of the seed the sim uses is Home plus three Blocked** — not the Blocked /
  Barren / Blocked mix the design assumed. That is why the screenshot suite carries a hand-written
  every-verdict frame as well as two generated ones.
  **Corrected after review:** an earlier version of this entry said `Barren` and `Settleable`
  "never render at ship time on the shipped seed". There is no shipped seed — `GameSession.resume`
  mints one per colony from the founding instant, so every player's galaxy differs, and with 2–3%
  of worlds passing every band roughly one colony in a dozen opens the Galaxy tab on a Barren or
  Settleable row. The mapper's branches for both are covered against real generated worlds.
- **The committed tolerance bands are wider than the design guessed** on temperature and pressure
  (−30 … +45 °C and 0.4 … 3.0 atm against its −95 … +58 and 0.35 … 1.40), so every Blocked sentence
  on the screen reads differently from the mockup's.

Three remain open and are listed with the rest in `status.md`: whether a near miss should look
different from a hopeless one, whether a relay should state an effect no mechanic can confer, and
who holds an `Occupied` world before multiplayer exists to hold one.

## Coverage ratchets to 95%, then stops ratcheting

The Coverage job reported and gated nothing: a PR could drop line coverage and merge green, which
made the report a thing to admire rather than a thing to obey. It is now a required check, and the
whole rule is one comparison:

```
pass  ⟺  current ≥ min(last main run, 95%)
```

Below 95% that is a plain ratchet — you may not leave the project worse than you found it. At or
above 95% there is slack down to 95%. Davide's number, and the two halves answer different
failures: without the ratchet nothing improves, and without the ceiling a project in the
high nineties spends every PR arguing about a tenth of a point.

**Patch coverage was the alternative, and it was the wrong trade here.** Gating on "the lines this
PR touched must be ≥80% covered" is the industry-standard shape, it is drift-free by construction,
and it never punishes adding well-tested code below the project average. It is also *weaker* than
what we chose: at a mid-nineties baseline, "the total may not fall" demands roughly 95% on new
code, not 80%. The cost is that a genuinely good 200-line slice at 90% fails the gate. That is
understood and accepted — the slice brings its tests with it.

**The drift objection mostly dissolves against `strict_required_status_checks_policy`.** The
baseline is the last `main` run rather than the PR's own merge base, which sounds like it would
blame a branch for `main`'s movement. It cannot survive to merge time: strict required checks mean
a branch is up to date with `main` before it can merge, so at that moment the last `main` run *is*
the merge base. Drift shows up in the deltas of a stale branch, never in the verdict that gates it.

**Not `koverVerify`.** Kover's verification rules know absolute numbers, and this rule is a
comparison against a baseline Kover cannot see. The gate lives in `.github/scripts/coverage.py`,
which already holds the baseline for the delta.

**Render and enforce are separate steps, in that order, with the comment between them.** A gate
that fails before the report is posted tells you only that you are blocked. `render` writes
`verdict.json` and exits 0; the comment goes up; `enforce` reads the verdict and exits 1. On a
`main` push the gate does not run at all — the merge has happened, it can prevent nothing, and a
red `main` for an unfixable number is the false alarm 0.0.13 already taught us to avoid. The
baseline is stored before the gate for the same reason: `main` must keep tracking reality, or
every later PR is measured against a number `main` no longer has.

**The gate has tests, and the job that enforces it runs them first.** `.github/scripts/coverage.py`
is dependency-free by design, but its arithmetic can now cost someone a merge, so
`test_coverage.py` (pytest, 22 cases) covers the ratchet, the ceiling, the epsilon, the
no-baseline skip and the enforce exit codes. `coverage.py` is loaded by path there, because
`import coverage` finds the PyPI package of that name on any machine that has it.

**Known hole: a cache miss skips the gate.** No restored baseline means a `skipped` verdict and a
green job. The comment and the log both say so, but nothing blocks. Accepted for now — the
alternative is failing PRs for an infrastructure hiccup that has nothing to do with their tests.

## A `Blocked` row states its worth, and stops pretending it can be bought

0.0.16, presentation only. 98.2% of surveyed worlds are `Blocked` by design — `GalaxyDistributionTest`
pins "passes every band" at 1.81% — so a home system is ~93% likely to show nothing but BLOCKED.
**The verdict is correct and the screen did not explain it.** Four changes, none of which touches a
`GalaxyBalance` number: the distribution is a settled call, `balance-log.md` round 5.

**The row now says what the world is worth, not only what it costs.** `yieldLabel()` returned null
for `Blocked`, so the one verdict a player meets over and over was the one that never priced itself.
All three blocked worlds in the sim seed's home system out-yield the threshold, which is the pillar
landing — and the row was the only place that could say so.

**It counts the bands it fails against the bar `Barren` already names.** "Fails 2 of 3 bands, worth
it at 0.92", beside "Passes every band, worth it at 0.92". Barren's threshold sentence is what makes
a bad answer read as a scale rather than as bad luck, and `Blocked` is the verdict that needs it
more. The clause is written once in the mapper for both: two rows on one screen disagreeing about
the bar would be the screen contradicting itself. **No new number was invented** — the alternative
copy quoted the measured 1.81% pass rate, which no `core` constant carries and which the sheet pins
only as a 1–2% band.

**The technology dropped from `OltreColors.accent` to `textSecondary`.** Accent is the screen's only
"go tap this" signal, and Research sells PHOTOVOLTAICS, EXTRACTION and ENRICHMENT — never Thermal,
Gravitic or Atmospheric. Dressing an unbuyable ladder as a call to action sends a player to a tab
that cannot answer. 0.0.15's own comment argued the opposite ("the row is a promise, and the accent
is what makes it look like one"); a promise the app cannot keep is what accent must not mean.

**The header says which of the two it is:** *"Adaptation research lands later. You are at level 0."*
PLACEHOLDER copy, marked as such beside `RELAY_EFFECT`, in the unbuilt tabs' voice. It sits on the
header rather than on the rows because every blocked row would otherwise repeat it. Second person
because the rows are already in it — "you tolerate 1.40 g" — and because "Every empire is at level 0."
wrapped after "level" at 393dp, leaving "0." alone on a line.

**Both copy calls were Davide's and were delegated to the build**, 2026-08-07, in the same shape as
the balance round: the options were put with their costs and the answer was "decide for me". His to
overrule, like every line of the sheet.

Out of scope and still open: adding the three adaptation technologies to Research (its own slice,
his call per the galaxy sheet), and any survey action (slice #7). Until the first of those lands the
sentence on the row stays a shopping list nobody can spend against — which is now stated rather than
implied.

## The adaptation ladders become a second branch, sharing one slot

0.0.17, `core` only. The galaxy sheet named three adaptation technologies, specified exactly what
each level widens, and then deliberately left the technologies themselves to "the slice that adds
them". Every `Blocked` world has been pointing at a purchase that did not exist ever since. This is
that slice, written up as [`adaptation-sheet.md`](adaptation-sheet.md) in the same shape as the 0.1
research and 0.2 galaxy sheets — **the calls in it are Davide's to overrule**, and the two most
worth overruling are named at the end of this entry.

**A second branch, not rows four to six.** The applied branch is three multipliers on a per-hour
rate and every part of its row says so — a current percentage, a next percentage, a subject ending
in the word *output*. An adaptation level does not multiply anything; it widens a band, in °C, in g,
in atm. One list cannot carry both without making two kinds of thing look like one. The second
argument is what each is bought *against*: applied research against a colony you can watch,
adaptation against a map you cannot. So `AdaptationTechnology` keeps its own enum, its own
`AdaptationBalance` and its own `AdaptationJob`, and `Technology` stays at three.

**One slot, shared, and that sharing is the mechanic.** 0.1 wrote down that the single slot is
research's only scarcity. Give the adaptation branch its own and the answer is always "run both",
and the ladder that changes the map costs nothing to push. Sharing it means every adaptation level
is paid for in production levels the player did not buy. `startResearch` and `startAdaptation` both
refuse on `researchSlotFreesAt != null`.

**Two nullable fields with a `require`, not one sealed project.** This is the one place in `core`
where a rule is checked rather than made unrepresentable, and it is deliberate rather than lazy: a
sealed `ActiveProject` would make every existing reader of `activeResearch` — the Research screen's
row mapper, the notification set, `futureEvents` — answer for a project it does not render, in a
slice whose screen work is a separate hand-off to a local session. The invariant runs in
`GameState.init`, so it is checked on every construction including every decode; a hand-edited save
claiming both projects fails as a `DecodeResult.Failure` rather than being half-read. The sheet
records that this reason has a shelf life and says when to revisit it.

**The gate is Robotics Factory 4, the same for all three.** Three gates that differ would decide
the first ladder for the player, and the galaxy sheet's whole argument for three ladders is that
*which one you push first* is a real choice. Robotics adds no concept (it already gates the applied
branch at level 1) and is a purchase they want anyway, since it shortens every project including
these. Level 4 rather than 1 so the branch opens after the player has met the Galaxy screen.

Rejected, and worth not re-litigating: **gating on having surveyed a `Blocked` world**. It reads as
the design's own logic — the map teaches the branch — but it gates nothing. 98.2% of surveyed worlds
are blocked, so a home system of four fails to contain one about once in thirty million; the
requirement would be met at genesis, before the player had done anything.

**All three ladders cost the same, in three different currencies** — 4,800 at the game's 1 : 2 : 3,
compounding at the same ×1.5 every building and technology uses. Each is priced in the resource its
own axis makes rich: Gravitic in metal, Atmospheric in crystal, Thermal in the deuterium the
research branch already made scarce. So the ladder a player can afford first is the one their colony
is already good at, and the one that would fix the shortage they actually have is the one they
cannot yet pay for. The identical priced total is what keeps that a preference rather than a right
answer, and `AdaptationBalanceTest` pins it as a property rather than as three literals.

**Save schema 5 migrates 4**, the third hop in a row that migrates rather than retires. The one
thing this hop must not do is what the 2 → 3 hop does: encoding a fresh `Research` would carry the
three new ladders across *and reset the two levels the player earned*, because unlike at version 2
the `research` object already exists. The migration adds the missing keys instead, and a test says
so by name.

**Nothing a player can see changed, and no screenshot baseline moved.** A cloud session may not
write UI (`session-roles.md`), and this is a cloud session: the Research screen does not sell the
three ladders and the Galaxy screen still passes `AdaptationLevels.NONE`, so 0.0.16's PLACEHOLDER
header — "Adaptation research lands later. You are at level 0." — is still true of the shipped
build. `verdictFor(world, state)` exists as the one-argument-shorter call the screen should switch
to, and the hand-off prompt for the local session names it. Two compile-forced edits outside `core`
were unavoidable and are the whole of the diff outside it: a `when` branch in
`:client:notifications:data` for the new future event, and one `activeAdaptation = null` in each of
two presentation *test* fixtures.

Left open and stated in the sheet: whether the two branches share one screen or get two (a design
call before the local session can start); whether the shared slot should become a sealed
`ActiveProject` once the screen renders both; and whether a ladder past its saturation level — 17
Thermal, 12 Gravitic, 11 Atmospheric — should be capped or merely labelled. No cap was added: it
would be a new concept bought to prevent a purchase nobody has a reason to make.

## Both branches on one screen, and the two premises that did not survive Compose

0.0.18, presentation only, built to a Claude Design sheet that answered the open call 0.0.17 left
and both of the secondary questions with it. No balance number is touched.

**A second section on the same scrolling screen**, not a segmented control and not a sixth
destination. The design's reason is not the one the brief expected — it is not that a second section
puts the explanation one scroll away, it is that when a project is in flight five rows read the same
wait and the sixth counts it down, on one screen, so the number verifies itself with nothing added
to carry it. A control whose job is to show one branch at a time is a control whose job is to hide
the thing you are giving up, and the trade *is* the mechanic. A sixth tab was rejected harder: five
fixed tabs already overflow a 320dp pane, "Adaptation" abbreviates to nothing that exists elsewhere
on the screen to recover it from, and the bar is the honest list of what the game is — a destination
beside Colony and Galaxy would say adaptation is a place rather than three projects competing for
one slot.

**Four of six rows dimmed at Robotics 3 is the argument for it, not against.** The Research tab
already shipped that way — day one is three dimmed rows — and a second dimmed block does the same
job one branch further out: before the map has shown a single hostile world, the screen has said
that hostile worlds are something you buy your way past. Under a segmented control those rows are
behind a tap, so the player learns nothing until they go looking for something they do not know
exists.

**One row composable, not two that match.** `TechnologyList` and `AdaptationList` both call one
private `ProjectRow`, which takes the row's parts and its two tags rather than either row type. A
running ladder has to look *exactly* like a running technology, because from three rows away it is
the answer to why nothing else can start; two composables promising to stay identical is a promise,
and one implementation is a fact. The ui-states stay two types — the branches carry different enums,
and a sum type in the identity field would make every reader answer for a branch it does not render.

**The blocked row's remedy is accent and tappable, or neither.** 0.0.16 demoted it to
`textSecondary` for exactly one reason, that Research could not sell what it named, and this slice
ends that reason. Restoring the colour alone would have broken the rule harder than the demotion
did: accent means "go tap this" and nothing else, so an accent string that is not a target is worse
than a tertiary string that is not one. The target is the string rather than the row, because the
row belongs to the world — survey now, claim later. The link selects the Research tab and stops:
under a second section the ladders are already on screen when the player arrives, so there is no
scroll target, no highlighted row and no arrival state to design. `MainScaffold`'s `galaxy`
parameter therefore takes a lambda the other destinations do not, since navigation is the
composition root's.

**The Galaxy header line is deleted, not replaced.** It accounted for an absence, and the absence
ended. "Thermal 2 · Gravitic 0 · Atmospheric 1" was the honest candidate and it fails for one
reason worth keeping: a tolerance band means nothing except against a reading, and every place the
player needs one the reading is already beside it — on a blocked row, and on the left of every
adaptation row. A standing total would be the only header in Oltre stating empire state that is not
about what is on screen.

**`verdictFor(world, state)`, and the defect it closes.** The mapper defaulted an `AdaptationLevels`
to `NONE`, which was correct while nothing could raise it and would have become a screen quietly
refusing to show what the player had bought. Two tests pin it: a world's failing axes shrink as the
empire climbs, and a partly climbed ladder still names the level to *buy* rather than the one held.

**`signed()` and `milli()` moved to `:client:design:format`.** That module's absence from the galaxy
build file carried a comment saying the formatter had one caller. It has two now — a band on
Research is read against a reading on Galaxy — and two screens writing the same axis two ways would
be the app contradicting itself about a number the player is comparing.

### The two premises the sheet got wrong, both found by rendering it

Recorded because the sheet is the design and these are corrections to it, not to the build.

**The row is 106dp, not 74dp.** The sheet's central arithmetic — six rows, two labels and the 22dp
seam at ~610dp against ~708dp of content — used the mock's 74dp row hint. The real Compose row is
106dp, so the true figure is ~788dp and a 393x852 phone scrolls by about 105dp. **Direction A
survives**: five of the six rows and the countdown are still on screen together, which is the part
that had to be true. What does not survive is "there is no scroll", which was how the sheet
dismissed B and C — the honest version is that the scroll is small enough not to hide the
explanation. The sheet's own trigger to revisit ("the day a branch grows past what a phone holds")
is therefore closer than it thought. The same 74dp also made the first screenshot height guess clip
the sixth row out of a baseline, which is the failure mode that test's comment already warned about.

**The pressure band must drop its trailing zeros.** Padded to two decimals, "0.50 … 2.60 → 0.44 …
3.50 atm" is 29 monospace characters against about 26 of usable width beside a ghost button at
320dp, and what gets ellipsised is the unit: "3.50 a…". The sheet is internally inconsistent here —
its stated precision rule says "no padding 0.5 to 0.50" and its pressure strings are trimmed, while
its gravity strings are padded — and the width settles it in favour of what it drew. So temperature
prints whole degrees, gravity keeps both decimals so its two bands read as a column, and pressure
trims: its band spans an order of magnitude more than gravity's, so it carries a leading digit more
and has no character to spare. `milliTrimmed` exists for that one line and says so. **Davide's to
overrule** — the alternative is padding everywhere and letting the band line wrap at 320dp.

**The branch did not build when this slice started.** Kotlin/Native forbids a comma inside a
backticked declaration, and ten of 0.0.17's test names had one, so `:core:compileTestKotlinIosArm64`
failed. `FormattingTest` already carried a comment warning about exactly this. The cloud session
that wrote them cannot build — the egress policy blocks AGP — and no PR was opened, so nothing
caught it. **A cloud session's `core` work is not verified until a PR runs CI on it**, and opening
one is the cheapest way to find out.

### What an adversarial review of the slice found

Four dimensions, every finding put to an independent verifier told to refute it; 7 of 12 claims were
refuted and 5 survived. Four are fixed here; the fifth is a design call and is recorded rather than
guessed at.

**The remedy was a 15dp tap target, and it is the only way into the slice.** `.padding(...)` sat
*before* `.clickable(...)`, so the hit rectangle was the glyphs alone — a 10.5sp line box — where
every other target in the app is 30–32dp and iOS asks for 44pt. A near miss landed on a card that
is deliberately not clickable, so nothing happened at all: no navigation, no feedback. The padding
now sits inside the clickable, with 6dp above and below. It is the one interactive element in the
client that had the modifiers in that order, and "accent means go tap this" is exactly the promise
a target that size breaks. **The rows are 12dp taller as a result**, which is a visible change to
the density the design drew — Davide's to overrule if the airier card reads wrong.

**The gate-open frame showed a duration the balance never produces.** The design sheet's frame gave
Photovoltaics 3 as "2h 35m"; at Robotics 4 it is 2h 17m. Taking a design's numbers verbatim is right
for costs the sheet decided and wrong for durations the balance derives — and this one sat four rows
above three ladders reading 3h 02m, which is the cross-row comparison the shared slot exists to make.
Its two neighbours (Extraction 5 at 5h 41m, Enrichment 1 at 1h 54m) were correct and are unchanged.

**Nothing tested an adaptation row above level 0**, the one level at which reading `research` and
reading a hard-coded zero are indistinguishable. Replacing the mapper's level lookup with
`TechLevel(0)` left the whole suite green while a player at Gravitic 4 would have seen "LV 0",
the genesis band and level 1's price. Now pinned at level 4.

**`ResearchScreen`'s KDoc still repeated the premise this slice disproved** — six rows fitting a
phone — which is the arithmetic that already clipped a baseline once.

### Left open: the band line runs out of room at deep pressure levels

**Not fixed, because the fix is a design call.** `milliTrimmed` buys about two glyphs, and the
pressure band grows with the ladder: measured against the committed 320dp baseline (6.8px advance,
187px of usable column beside the ghost button), Atmospheric level 0 is 23 glyphs and fits, level 8
is 26 and does not — the trailing unit is the only element carrying `Ellipsis`, so it absorbs the
whole overflow and renders "a…". Levels 8–11 are levels the design expects a player chasing crystal
worlds to hold; §4 puts saturation at 11 and declined a cap.

So the 320dp fix this slice made is a fix for level 0, not for the ladder. The options, none of
which this slice may pick on its own: let the band line wrap to two lines at 320dp (preserves every
figure and the unit, costs the row's fixed height); drop the unit at 320dp for adaptation rows only
(contradicts "one unit, stated once" and the sheet's "identical at every width"); or abbreviate the
band itself past some level. The behaviour tests cannot catch it either way — `hasText` reads the
semantics string, which stays complete when the glyphs are ellipsised.

## A cloud session runs `:core` and `:sim` through a build overlay (2026-08-08, 0.1.1)

**"A cloud session cannot build" was written down as a hard fact and it was too strong.** It is
true of anything that applies AGP, which is every `client/*` module and `:core`'s Android target —
`dl.google.com` answers 403 to the remote environment, `maven.google.com` redirects there, the
Gradle Plugin Portal redirects to Maven Central, and Google does not publish AGP to Maven Central.
There is no way through for the client, and there never was.

But `:sim` depends only on `:core`, and consumes its **JVM** target. AGP is in `:core` purely to
publish an Android target the sim never reads. Restricted to those two modules with the Android
target dropped, every dependency resolves from Maven Central and the harness runs unmodified.
`.claude/tools/gradle-without-agp.sh` does that: it generates a minimal overlay for the three build
files, runs Gradle, and restores the real ones on every exit path.

**Why this is worth a decision entry rather than a script nobody mentions.** Rounds 2 and 3 of the
balance log wrote their tables *by hand* because of this blockage, and said so; a later session
that could build re-ran them and confirmed the arithmetic had held. That is a coin flip nobody
should be asked to make again. Round 7 needed measurements the arithmetic could not produce at all
— which resource blocks a purchase, in which hour, across 336 hours of two strategies — and got
them from this script. The 0.0.12 greedy week reproduced through it byte for byte, which is the
evidence that the overlay changes nothing about what runs.

Rejected alternatives:

- **Mirror AGP from a third-party host** (Aliyun, Huawei and others proxy Google's Maven). It would
  fix the whole build, and it means executing build plugins fetched from an unvetted mirror. Not
  for a balance measurement, and not silently.
- **Make the root `plugins {}` block conditional** so AGP loads only when resolvable. This is a
  permanent change to the real build to serve one environment, and it fights the reason every
  plugin is declared at the root in the first place — one classloader, no BuildService clashes.
- **Bypass Gradle with `kotlinc`.** `CLAUDE.md` forbids it, and rightly: the sim would then be
  compiled by something other than the toolchain that compiles it everywhere else.

The script is deliberately not wired into `./gradlew`, any CI job, or any default path. CI builds
the whole project normally and remains the gate. **The real build files are never modified in a
commit** — the script refuses to start if they carry uncommitted changes, because its restore is a
hard `git checkout --` that would discard them.

### The same PR found a hole in the coverage exclusions

`kover`'s excludes named `dev.fardavide.oltre.sim.MainKt` — the file's class — to keep a harness
that never ships from depressing the project total. Adding three top-level private types to
`Main.kt` (an enum, a ledger, an options holder) put them in *sibling* class files, outside that
name, and they arrived in the report as a new package at 0%: 14 uncovered lines, and a failed
coverage gate on a PR that had not touched a line of shipping code. Now excluded by package, which
is what the comment always meant. Worth knowing generally: **a class-name exclusion in Kover does
not cover a file, it covers a class**, and Kotlin puts top-level declarations wherever it likes.

## Android ships as a GitHub Release, and its wrapper is the one thing allowed to see the shell (2026-08-09, 0.2.0)

Davide asked for the quickest way to publish for Android and proposed a GitHub Release carrying
the APK. That is what landed, and it is right for a reason worth writing down: **a release asset
is a direct, unauthenticated `.apk` URL**. Tap it in a phone browser and the installer opens. The
obvious cheaper option — upload the APK as a CI artifact on every run — fails at exactly that
step: Actions artifacts are ZIPs behind a GitHub login, so a phone cannot install one without a
desktop and a cable in between. The repository is public, so the release link needs no token and
can be handed to anyone.

Rejected, and worth re-reading before anyone proposes them again:

- **Firebase App Distribution** — the true TestFlight analogue: testers get a push and install
  from an app. It costs a Firebase project, a service-account JSON in secrets and a tester list to
  keep. Worth it when there are testers who are not Davide; not worth it to install on your own
  phone, where a URL is the same two taps.
- **Play Console internal testing** — the eventual destination, and a heavier one: an account, a
  listing, and app signing to arrange. Nothing about this decision blocks it later; the release
  key below is the one Play would want.
- **An APK artifact per CI run** — see above. Kept in mind as the thing to add if a build ever
  needs sharing *before* it is a version.

### The trigger is a version change on `main`, and the job is idempotent

Merging to `main` already publishes on iOS. The Android half now matches: a push to `main` that
touches `gradle/libs.versions.toml` runs `release-android.yml`, which reads the `oltre` version,
asks whether `v<version>` is already released, and stops if it is. The path filter is exact about
the *file* and deliberately imprecise about the *reason* — bumping Ktor touches the same
catalogue — so the idempotence check, rather than the trigger, is what decides. It also creates
the tag, which the versioning convention used to ask a human to push by hand.

The release body is the README changelog entry for that version, extracted by
`.github/scripts/android_release.py` and tested in `test_android_release.py`. **A version with no
changelog entry raises rather than publishing an empty release** — the convention already
required the entry; this is the step that stops it being optional, and it fails before the tag
exists rather than after.

### Signing is a real key in secrets, because the alternative eats the save

CI's auto-generated debug keystore differs on every runner, so build N+1 will not install over
build N: the player uninstalls first, and `filesDir` — which is where the save lives — goes with
it. For a game whose entire proposition is progress accruing while it is closed, an update that
wipes the colony is not a rough edge, it is the product failing. So the release is signed with one
stable key, held as four secrets:

```
ANDROID_KEYSTORE_BASE64     base64 -w0 oltre-release.keystore
ANDROID_KEYSTORE_PASSWORD
ANDROID_KEY_ALIAS
ANDROID_KEY_PASSWORD
```

Generated once, and never in the repository:

```bash
keytool -genkeypair -v -keystore oltre-release.keystore -alias oltre \
  -keyalg RSA -keysize 4096 -validity 10000
```

**Losing this key means no future build can update an installed one.** Back it up somewhere that
survives the laptop. Rejected: committing a fixed debug keystore, which would give stable
signatures with no secrets to manage — but in a public repository it hands anyone the ability to
sign an APK that updates over the real one, and it is a dead end for Play.

`androidApp/build.gradle.kts` reads the four values through `providers.environmentVariable`, and
falls back to an unsigned release when they are absent. That is what keeps `./gradlew assemble`
working on a fork's pull request, where no secret is available. The workflow checks for the
secrets *before* it builds, and refuses to publish an APK whose filename carries AGP's
`-unsigned` suffix — the one thing this job must never do is ship a build that cannot install over
the last one.

### Rule 7 gets its carve-out, and it is an allowlist of one

`architecture.md` had anticipated an `androidApp` wrapper since 0.0.1 and rule 7 was written
literally so the question would be argued against a real module. Here it is, and the answer is
that `:androidApp` is allowed through by name.

The edge is **forced**. AGP 9 stopped the Kotlin Multiplatform plugin working alongside
`com.android.application`, so the shell cannot be the Android application the way it already *is*
the desktop application — `compose.desktop.application { }` sits in its own build file. The
wrapper has to be a sibling Gradle module, and it has to reach `App()`.

The edge is **not new**. `iosApp/` links `OltreClient.framework`, built by `:client:shell`, and
calls `MainViewController()`. That is precisely the relationship rule 7 forbids; it escapes only
because Xcode is not Gradle. Android is the first platform whose wrapper the module graph can
see, not the first one to have this shape.

The edge **carries nothing**. Every project dependency in the shell's build file is
`implementation`, not `api`, so nothing is re-exported: `:androidApp` sees `App()` and
`MainActivity`, and not one presentation, data or domain module — not even `:core`. Gradle already
enforces what rule 7 defends; the rule is the belt to that braces.

And the literal alternative is **worse**. To keep rule 7 as written, `:androidApp` would depend on
the nine feature and design modules directly and re-do the composition: `MainScaffold`, the
session, the save and notification wiring. That is a second composition root, mixing every layer,
protected by nothing, drifting from the first every time a feature lands. The rule would hold on
paper while the property it exists to protect was lost.

Also rejected: **widening the rule to "any Android application module"**, which reads as a
principle rather than an exception. Nothing can check that such a module stays an entry point, so
the allowlist is `platformEntryPoints` — a set of names in the root build script. The next module
that wants through has to make the argument again rather than inherit this one.

### `MainActivity` lives in the shell, so the wrapper holds no Kotlin

`androidApp/` is a build file, an `AndroidManifest.xml`, a theme and the launcher icons.
`MainActivity` is in `client/shell/src/androidMain`, beside the desktop `main()` and the iOS
`MainViewController()`, and the manifest names it across the module boundary as a string. Davide's
call, over the conventional Android layout (Activity, manifest and icons together in the app
module).

Two things follow. All three platform entry points stay in one place, and the carve-out permits a
module that *cannot* accumulate logic — the exception is narrow by construction rather than by
promise. And `AndroidSaveLocation.directory = filesDir`, which has to run before the first save or
`:client:save:data` throws, sits inside the composition root, where the rest of the save wiring
already is. The cost is that renaming `MainActivity` fails at manifest merge rather than at
compile time; there is no call site to break.

The notification scheduler is real, and is covered in its own section below.

`allowBackup` is left on. The save is a JSON snapshot in private storage, so Android's own backup
carries a colony to a new phone — the closest thing the game has to iCloud sync until a server
exists.

### Two traps this slice walked into, both invisible until runtime

**Compose resources are not packaged into an Android APK** by AGP 9's Kotlin Multiplatform library
plugin unless the resource pipeline is enabled explicitly (CMP-9547). `:client:design:core` bundles
the three JetBrains Mono files behind `Res`, and without `androidResources { enable = true }` they
compile, link and are then left out of the APK — a `MissingResourceException` on the first frame
that asks for the font, which is every frame, because the type scale is the theme's. Enabled on
both modules that declare `compose.components.resources`.

**A new entry point is uncovered lines, and the coverage gate blocks on those.** `MainActivity` is
excluded from Kover on exactly the grounds `sim` and the two `MainKt`s already are: a process
entry point is exercised by launching the app, and there is nothing in it for a test to hold. Left
in, it would have failed the merge gate on the PR that introduced it. `:androidApp` is absent from
the `kover(...)` aggregate for a different reason — it holds no Kotlin, so there is nothing to
measure.

## Android books its alerts through AlarmManager, inexactly and on purpose (2026-08-09, 0.2.0)

Davide's call, on being told the Android scheduler was a stub: *follow what you did for iOS.*
Right, and the reason given for holding it back was wrong — the copy already exists in
`GameNotifications` and is shared by every platform, so Android reuses it and invents nothing.
What was left was engineering, and it is recorded here.

**One alarm per notification, and the ids are written down.** iOS hands the whole set to
`UNUserNotificationCenter` and can later say "remove everything pending"; Android has no such
register, so `replaceAll` persists the ids it scheduled in `SharedPreferences` and cancels them on
the next sync. In memory would not do: the process that scheduled them is usually long dead by the
time the next sync runs.

Identity is the intent's **data URI**, `oltre://notification/<id>`, not the PendingIntent request
code. `Intent.filterEquals` — which is what the PendingIntent register compares — reads the data
URI and ignores extras, so a request code derived from the id would be a hash, and two colliding
hashes would silently overwrite one alert with another. `FLAG_UPDATE_CURRENT` is then required
precisely *because* extras are outside identity: without it a rescheduled alert keeps the title it
was first booked with.

**Inexact alarms — `setAndAllowWhileIdle`.** This is the one place Android is meaningfully worse
than iOS, and it is the right trade. An exact alarm needs `SCHEDULE_EXACT_ALARM`, denied by
default since API 33 and grantable only by the player walking into system settings; the permission
that avoids that walk, `USE_EXACT_ALARM`, is restricted by Play policy to alarm clocks and timers,
which this is not. Inexact means Doze can hold an alert for minutes. A game whose sessions are
five minutes long and whose builds run for hours can afford minutes; it cannot afford a permission
dialog nobody would grant. Overrule if a late alert ever reads as a broken one.

**A boot receiver, which iOS has no counterpart for.** Android drops every scheduled alarm on
reboot. Without `BootReceiver` the game goes quiet after a restart and stays quiet until the
player next opens it — which is exactly the player the alerts exist to reach. It reads the save
and recomputes rather than storing a schedule to restore, because the schedule is derived from
state everywhere else in this game and a stored copy is the one thing that could disagree with the
colony. That makes it composition — save plus notifications — so it lives in the shell rather than
in either module it uses. It catches everything: there is no UI at boot to report a failure to,
and a crash dialog on somebody's phone every time it starts up is the worst possible outcome for a
notification that will be rescheduled on the next launch anyway.

**The permission is asked on the first frame**, matching iOS exactly, and for the same reason: the
alerts *are* the check-in loop, so a player who declines has declined something they can see the
shape of. Android differs in one detail that makes this cheaper than it looks — the system stops
showing the dialog after two refusals and answers "denied" silently forever after, so asking on
every launch cannot nag.

**`OltreApplication` fills the two slots the platform cannot derive** — `AndroidSaveLocation
.directory` and `AndroidNotificationHost.context`. Not the Activity, which is where the save
directory was set for the few hours between this decision and the one above it: Android is the
only platform where the process can start with no screen at all, and `BootReceiver` running with
`AndroidSaveLocation.directory` still null would throw on the read. The Application is the one
component guaranteed to run before every other.

**The status-bar icon is a new asset**, in `:client:notifications:data` — the module that posts a
notification owns what it posts it with, and a non-transitive R class means the app module's
resources are not visible to it anyway. Android masks a small icon to a flat silhouette and
ignores its colours, so the launcher artwork cannot be reused: it would arrive as a white blob.
`ic_notification.xml` is the icon's one legible gesture instead — the trajectory and the light it
climbs towards, taking the curve from `threshold.svg`'s own arc and re-weighting it, because a
22/1024 stroke lands at half a pixel at 24dp. **It is the one visual asset in this repository a
cloud session drew**, it is a reduction of somebody else's mark, and it should be overruled if it
reads wrong on a device.

**No test, and the precedent is the point.** The iOS scheduler has none and the desktop one has
none: they are platform edges with no seam a test can reach without new infrastructure
(Robolectric, or an instrumented run) that this repository does not have and this slice is not the
place to introduce. What *is* tested is everything above the edge — `notificationsFor` derives the
set, `GameNotificationsTest` pins it against `FakeNotificationScheduler`, and that is where the
game logic lives. The platform half is verified by installing it, which is a local session's job.
