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
one platform that does not need one. **Android did nothing until 0.2.1**, when the app module
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

**Superseded 2026-08-12 — see "Nothing in the coverage table may go down", at the foot of this
file. The ceiling is gone, and the gate now judges every value in the per-kind table rather than
the total alone. Kept because everything below except the ceiling is still why the gate is shaped
the way it is.**

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

## Nothing caps simultaneous construction: the stock is the scarcity (2026-08-08, 0.1.1)

Open since round 2 of the balance log, listed in `status.md` as a design question for Davide, and
answered by him on 2026-08-08 when a session proposed a construction cap as the fix for *"solo
premere un tasto"*:

> "No. I don't wanna to remove parallel build! There's still a need to decide, as you will use
> resources to chose which to upgrade, you can upgrade them all"

**Upgrades run in parallel, one job per facility, and resources are the only limiter.** The
decision a colony screen poses is *what to spend the stock on*, and it is a real decision because
the stock is finite — not because a slot is. A cap would replace a question the player answers
with their whole balance sheet by a question they answer with a queue.

Notion's expansion pressures call for "limited simultaneous *projects*", and research already
answers that half: one project at a time, empire-wide, shared between the applied branch and the
adaptation ladders. **Construction is deliberately the opposite**, and the contrast is the point —
the colony is limited by resources and research is limited by time, which is what gives the two
screens different characters (`GameState`'s own comment has said so since 0.0.12).

Measured before the ruling, and kept because both are the obvious idea:

| Candidate | Result over the first 48 hours |
|---|---|
| One construction slot | 11 building levels against 25; Research never opens at all; **still 83% of the window with nothing in flight**; 3,970 metal left unspent |
| Two construction slots | 18 levels; the second kind of decision slips from 29h to 39h; 2,564 metal unspent |

The measurement and the ruling agree, by different routes. A cap does not even buy what it was
proposed for: early builds are short whatever the cap, so restricting them removed actions without
adding a single hour of cover. See `balance-log.md` round 8.

**What this does not settle:** the Nanite Factory, ships and fleets all queue work too, and none of
them exists yet. This decision is about facility upgrades on the colony screen.

## The probe: surveying is a colony action, permanently shipless (2026-08-09, 0.1.2)

Davide asked for a second thing to do in a check-in (`balance-log.md` rounds 8 and 9). Surveying
won because `GalaxyState.surveyed` already existed, was written to by nothing, and made the whole
Galaxy tab unactionable. The question this entry settles is **what performs a survey**, because
three places in the repo said in writing that a fleet would.

**A probe is dispatched by the colony, needs no ship, and slice #7's fleets will never survey.**

Superseded, and kept so the reasoning survives:

- `GalaxyState.surveyed`'s own comment: *"surveying is a per-world fleet action from slice #7
  onwards."* Now: a per-**system** colony action from 0.1.2. The set is still per-world.
- `galaxy-sheet.md` §5 and the comment in `App.kt` said the same. Both are overruled here rather
  than left to drift.

Why now rather than with slice #7:

- **Slice #7 needs a design call Davide has not made** — the v1 ship set (`CARGO`/`FIGHTER`/
  `CRUISER`/`COLONY_SHIP` are placeholders) and the travel-time formula. Waiting for it would leave
  the opening with one verb for however long that takes.
- **A `ShipType` constant is an on-disk identifier in every save.** Inventing a probe ship now would
  pre-empt the ship-set call with a name nobody chose.
- **It avoids the `Coordinates` / `GalaxyCoordinate` reconciliation** that `status.md` assigns to
  slice #7. `SystemAddress` is a third type, deliberately: a survey is aimed at a star, and a
  coordinate with a nullable slot would raise the question of what the other fourteen slots are.
- OGame's own Discovery mission requires no ship either, and Oltre is in that lineage.

**Deciding it now rather than deferring is the point.** A shipless probe that "might later become a
fleet action" is a retcon waiting to happen; ruling that fleets never survey closes it.

### What follows from it, and what does not

- **Nothing gates the verb.** A second verb whose job is to exist at hour zero cannot sit behind a
  building. The slow unlock pace Davide likes is protected from the price side (150 metal, swept in
  round 9) rather than from the requirement side.
- **Probes run in parallel**, limited by metal alone — the construction rule settled on 2026-08-08,
  applied rather than re-litigated.
- **Flat cost; distance is only in the duration.** The generator has no per-system gradient in its
  *trait* functions, so a distance-scaled cost would make far probes strictly dominated and would
  tax the player who is away longest. Reversing that is what turns distance into a schedule the
  player chooses.
- **`advance` breaks simultaneous landings on the target coordinate**, not on list order. Durations
  are quantised in whole systems, so two probes sent to 117 and 119 from home 118 land on the same
  millisecond — a tie is ordinary here, unlike everywhere else in the log, and insertion order is
  extrinsic in exactly the way every other tie-break in this codebase avoids.
- **Save schema 6 migrates 5** additively: one absent key. What a survey writes to has existed
  since schema 4, so the hop adds the verb and not the record it fills.
- **`GalaxyState.surveyedHomeSystem` became `occupiedWorldsIn`** and is now called by both genesis
  and a landing. Not a tidy-up: the set a survey writes has to be exactly the set the player already
  has for their own system, or "surveyed" would mean two different things depending on how it got
  there.

## Android ships as a GitHub Release, and its wrapper is the one thing allowed to see the shell (2026-08-09, 0.2.1)

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

### And two more the first local build found, both of them `lintDebug` errors (2026-08-09, 0.2.1)

Neither is runtime, which is why the section above missed them: `:androidApp:lintDebug` is part of
`build`, so a cloud session that cannot run AGP cannot see them either. Both failed on the first
build anyone ran.

**A manifest can only name a class its own module can see at compile time.** `NotificationReceiver`
was declared in `androidApp/`'s manifest, and it is the one component that cannot be: `:androidApp`
depends on `:client:shell`, which depends on `:client:notifications:data` as `implementation`. That
edge puts the class on the *runtime* classpath — so the receiver really would have been in the APK
and really would have fired — but not on the app module's *compile* classpath, where lint looks.
`MissingClass`, on a name that was never wrong.

The fix is the one the module already argues for its own status-bar icon: the module that owns the
component declares it, in `client/notifications/data/src/androidMain/AndroidManifest.xml`, and
manifest merging folds it into the application. `MainActivity`, `OltreApplication` and
`BootReceiver` stay named in `androidApp/` — they come from `:client:shell`, which is a *direct*
dependency, so they resolve on the compile classpath and a rename still breaks the build. The rule
this leaves behind: **a component whose class arrives transitively is declared by its own module**,
and the distinction is the dependency graph, not taste.

**`android:windowLightNavigationBar` is API 27 and `minSdk` is 26.** `NewApi`, and the value was
`false` — which is also what API 26 falls back to, since a platform ignores a theme attribute it
does not know. So the line changes nothing anywhere it is not honoured, and it is annotated
`tools:targetApi="27"` rather than split into a `values-v27` copy of the whole style or deleted.
Deleting it would have been the same bytes and a worse comment: the theme states both bar
appearances on purpose, and the pair reads as a pair.

## Android books its alerts through AlarmManager, inexactly and on purpose (2026-08-09, 0.2.1)

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

## The Sky pass: four one-shot transitions, and the map stops being a strip (2026-08-10, 0.3.0)

The accepted direction out of a four-option graphics review — `1b` and `2b` in the design project's
`Graphics Pass.dc.html`, handed over as `design_handoff_sky/`. Everything the pass adds sits
**behind** the content: the cards, the rail, the tab bar and the type are untouched.

### It spends two written rules, both knowingly

1. **"Flat `#05070D`. No texture."** The starfield already broke this at the depth pass with 26
   static stars. It is now 101 across three planes that translate against each other with the
   scroll. The extension is larger than the original exception and is the same exception.
2. **"Animation. Effectively none, and that is a rule rather than an omission."** Four transitions
   are added. **The rule's reason survives intact and that is the whole argument**: nothing loops,
   nothing repeats, and nothing implies a live clock. Every one of them runs once when the thing it
   describes enters composition and then holds forever. A game whose premise is that it progresses
   while closed must never draw anything a player could read as "it is happening now" — a value
   settling into place on arrival is the opposite of that. See `OltreMotion`.

The parallax is deliberately not counted as an animation and it is not a dodge: it has no duration,
no clock and no running state. It is a function of the scroll offset, exactly as the position of the
list is.

*Superseded in part at 0.4.2: the tilt parallax added running state and two time constants, and a
lean settles back to level for about ten seconds after the hand stops. The paragraph above is still
true of the scroll term and of nothing else. See the 0.4.2 entry at the end of this file.*

### The starfield stayed in `:client:shell`, against the handoff's own path

The handoff asks for `client/design/.../design/sky/Starfield.kt`. There is no `:client:design`
module — it is a directory of layer modules — and the architecture skill's rule is that *a component
whose one owner is obvious stays with it*. The starfield's one owner is `MainScaffold`. Moving it
would have meant a new module or a new `:client:shell` → `:client:design:component` edge that the
shell's build file explicitly declines. It stays put; only `rememberOneShotFill` went down into
`:client:design:core`, beside `OltreMotion`, because four callers in three modules share it.

**`clipToBounds` on that Canvas is load-bearing.** Star `y` runs −0.08..1.08 so a translated plane
never exposes an empty edge, and Compose does not clip a child to its layout bounds — so the first
recorded baseline had stars drawn over the resource rail and the tab bar. Space showing through a
surface is the one thing the depth pass's opaque fills exist to prevent.

### Scroll state is hoisted into `MainScaffold`

The field is the frame's and the scroll is a screen's, so one of them has to reach the other. The
scaffold now owns one `ScrollState` per destination and hands each screen its own — which also means
returning to Colony from Research finds Colony where it was left. The alternative was a screen
writing its offset somewhere the frame happens to read: the same coupling with none of it visible in
a signature.

### The Galaxy map replaces the fifteen-tick strip, and that is a real subtraction

**Davide's call, 2026-08-10**, asked directly because the `1b` frame contains no strip, no
Hot/Temperate/Cold band, and `galaxy-sheet.md` calls both load-bearing. The options put to him were
"add the drawing above the strip", "the orbit view replaces it", "defer to a design round-trip" and
"take `1b` literally". He chose the second.

What went: the *shape of a system* — that four of fifteen slots are occupied and where the gaps fall
— and the band strip, which was the only place a player could learn that slot 13 means cold without
being told so in a sentence. The bands survive as the world list's section headings; the empty slots
do not survive at all. `GalaxyNav`, `ReachBand`, the probe footer and `WorldList` were **not**
touched, which is what separates the chosen option from taking the frame literally.

**Orbits are spaced by rank, not by slot, and that is the second-order finding.** Linear-in-slot was
built first and is what the coordinate deserves — slot 13 twice as far out as slot 7, to scale. It
does not survive a real system: fifteen slots across the frame puts neighbours 11dp apart, and with
every body on one phase an 11dp step is narrower than the number printed under it. The home system
drew worlds 7 and 8 on the same pixel under two overlapping labels. Rank spacing gives every system
the whole frame; the order and the slot numbers survive, the *scale* does not.

**Every body sits at the same phase (−33°)**, which is what the reference does and what makes the
map safe: with one angle both coordinates are monotone in the orbit's width, so no two bodies can
land on each other. Varying the phase per body — the obvious way to make it look less mechanical —
is exactly what caused the collision, because a wider orbit at a shallower angle reaches no further
across than a narrower one at a steeper.

The design file hand-placed its four worlds and no angle formula fits them (residuals 0.58–1.40 on
the ellipse equation), so there was no rule to copy. This is the rule the build chose.

### The energy card keeps its copy and takes only the drawing

**Davide's call, same session.** The handoff's Change 4 draws a section label "Energy · 1,240 of
1,480" over a card reading "Produced 1,240/h" / "Capacity 1,480/h", while claiming in its own prose
that "copy is unchanged from what ships — no string here is new". Both cannot be true. The shipped
`PowerIndicator` says POWER, a verdict sentence and three terms, and the verdict is what the colony
docs call the teaching move. It keeps all of that and gains the 4dp track and the 26dp gradient head
on the fill's leading edge. The handoff's "capacity" is also not the shipped model's "drawn" — it
warns when production *exceeds* it, which inverts the deficit rule — so adopting its copy would have
meant deciding a mechanic, not a drawing.

### What the arrival window is for

The two transitions that say "this changed while you were away" — the stock roll and the completion
sweep — are fed by an `Arrival` the shell computes once from the difference between the saved state
and the resumed one, and **drops two seconds later**. Without that, every return to a tab would
replay a sweep about a launch that happened an hour ago. The fills (dial, meter, probe bar) are not
gated that way and do replay when a destination is re-entered: they are one-shot per composition,
which is honest — a value arriving is a value arriving — and gating them would mean threading a flag
through every row on every screen.

`arrivalOf` reads the event log's tail rather than comparing levels, which is what an append-only
log buys. Only the **last** completion sweeps: one band crossing one card is a statement, four bands
crossing four cards at once is a light show.

### Screenshot baselines stop the clock

Every screenshot test now sets `mainClock.autoAdvance = false` before `setContent` and advances by
`SETTLED_MILLIS` (2,000) before capturing. A baseline caught mid-transition is the only way any of
this can flake — the fills take 900ms and the band leaves the card at 1,170ms. One baseline is
deliberately the exception: `facility_list_finished_while_away` winds to 795ms, because a settled
baseline of a swept row is a baseline of a row with nothing on it.

The 14 galaxy baselines each grew by exactly 210dp: the map block was a 76dp strip and is now the
286dp orbit view. Nothing else on that screen moved.

### The rail's dividers were skipped once, and that was wrong

The first pass read `dividers="true"` and the rail's `border-bottom` as prototype framing, on the
grounds that they appear in all six frames including the two rejected directions. Davide caught it.
The component's own default is `false` and every frame overrides it — that is a choice, not
scaffolding. Both are in: a 1dp white-9% rule between each pair of cells, inset 7dp top and bottom,
and one under the bar. Both are **drawn rather than laid out**, because the three cells are the one
place in the app with no width to spare and a 1dp element between them is 2dp off the figures; every
cell carries `weight(1f)`, so a third and two thirds is where the cells divide rather than an
approximation of it.

### Four defects an adversarial review found after the pass was green

All four survived `./gradlew build`, 41 verified baselines and 95.4% coverage, which is the point
worth keeping: none of them were things a test in the repo was shaped to notice.

1. **The completion sweep was dead on Research.** The arrival was dropped by a wall-clock timer two
   seconds after the save was read, but the sweep's own clock starts when the *row* composes — and
   `MainScaffold` opens on Colony and composes no other destination. A research project that landed
   while the app was closed could only be announced if the player found the Research tab inside
   0.83s; between 0.83s and 2s the band was cut off mid-crossing and the level badge snapped with
   nothing on screen to explain it. **The fix is two-part**: the announcement is no longer on a
   timer at all — it is consumed by whichever destination shows it, and by the first action the
   player takes — and `rememberCompletionSweep` latches `play` at its first composition, so
   withdrawing the announcement underneath a running crossing cannot cut it short.
   `CompletionSweepBehaviourTest` is the regression, and it fails against the unlatched version.
2. **The parallax emptied the sky.** Each plane translated by `-offset × parallax` with no wrap, and
   the star table's −0.08..1.08 bleed only covers a shift of 8% of the viewport. The near plane
   keeps 58% of the list's speed, so one screen of scroll left the bottom of the destination with no
   near stars in it at all. The planes now tile: the shift is taken modulo the height and every star
   is drawn twice, one height apart. `starfield_scrolled.png` is the witness — the first test in the
   repo that renders the field at a non-zero offset.
3. **Two Canvases painted outside their own bounds**, because Compose does not clip a child to its
   layout. The starfield's bleed rows landed on the resource rail and the tab bar; the system map's
   180dp ambient glow and its outermost orbit landed on the screen behind the card. Both take
   `clipToBounds`. The first was caught by looking at a baseline; the second was not caught by
   anything, because no galaxy baseline is recorded above 393dp.
4. **The rolling stock re-wrapped the rail.** Tabular numerals fix the width of a digit and do
   nothing about a count that grows from "900" to "1,400" — and at 320dp the stock shares a wrapping
   row with its rate, so two characters were enough to throw the rate onto a second line halfway
   through the roll and pull it back at the end. The rolled figure is now padded to the width of the
   figure it is heading for.

Two smaller ones from the same review: the map's geometry was absolute dp measured for a 361dp card
and is now fractions of whatever width it is given (it clipped at 320dp and floated in the left half
at the 560dp cap), and the trajectory follows the probe landing soonest rather than whichever the
list happened to hold first, since nothing caps simultaneous probes.

**One finding is accepted rather than fixed, and it is the honest limit of this pass.** The arrival
is computed on a cold start only — there is no foreground observer anywhere in this app, on any
platform. A player who backgrounds Oltre for four hours and brings it back without the process being
killed sees the new numbers with no roll and no sweep. The handoff calls for a "foreground epoch";
what the repo has is a launch. Wiring real lifecycle observation across three platforms is its own
slice, and this one should not invent a heuristic for it.

## The check-in loop becomes opt-in (2026-08-10, 0.5.0)

Claude Design's revision of the Upgrade Watch sheet, and it reverses the loop the game has run on
since 0.0.10: **an upgrade completing no longer books an alert unless the player tapped the bell on
its row while it was running.** Probes and fleet returns are untouched and still fire on their own.

**Both passes of the sheet ship as one version, Davide's call** — *"there's no reason for 2 bumps"*.
The first drew a square that books a price and left completions firing on their own; the revision
gave the same control a second question and took the automatic ones away. Neither reached `main`
separately, so the player meets one change rather than two, and the save format is one hop rather
than two: **schema 9 adds `watching` and `subscribed` together**, because no save has ever held one
without the other and a second version would be a migration nobody could ever run.

The sheet argues against itself out loud, which is why it is worth recording rather than merely
implementing: *"the completion alert is the check-in loop, and on iPhone it is the only way the game
can say a decision is ready; a player who never taps the square now hears nothing at all."* The case
for it is that every alert becomes one that was asked for — the stronger form of the rule the probe
copy already follows. **Its own stated exit condition is a measurement the app cannot currently
take**: *"if subscription rate on started builds is high, the tap was a tax and the default should
flip."* Nothing records started builds or subscriptions, so the bet is un-settleable as shipped, and
that is in `status.md` as an open item rather than quietly missing.

Four decisions inside it are ours rather than the sheet's, each because the sheet did not say:

1. **One verb, `toggleAlert`, deciding from `isRunning(target)`** rather than two verbs the screens
   pick between. The row knows whether it is building, so two verbs look equally correct — and they
   are not: the screen renders a snapshot and the tap is applied to a state that has been advanced
   since, so a build that finished in between would be "subscribed" after it had already landed.
   Deciding in core is the only place the question is asked of the state the action will act on.
2. **`Set<WatchTarget>` rather than a second sealed `CompletionTarget`.** One pointer type for one
   control. The argument for two — that `watching in subscribed` would then compile and mean nothing
   — is answered by a test rather than by a type: `advance never leaves a row both watched and
   subscribed`, which holds because starting a job requires covering the very cost the watch waits
   for and `advance` clears the watch the moment the stores do.
3. **The grouping lives in `notificationsFor`, not in `futureEvents`.** That list is the mirror of
   what `advance` will write to the log; a group mirrors nothing, and the debug menu's "skip to the
   next event" reads the same list. The sheet agrees — *"arithmetic done at schedule time"*.
4. **The stacked square is 29dp tall where the side-by-side one is 44dp.** Measured, not conceded:
   at 44 the compact action column is 28 + 7 + 44 = 79dp against a 56dp content column, which grows
   the row to 101dp where the design drew 88. Height is free beside the ghost and is not free under
   it, so the platform's minimum is bought only in the axis that costs nothing.

**The beacon is gone after one version.** The sheet's own reason is the best argument in it: three
bespoke marks were drawn for this control — a beacon, a body above a limb, a trajectory past a world
— and every one had to be explained before it could be read. *"A control nobody can read is worse
than a borrowed shape"*, and a bell is barely borrowed: it is the one mark a player already knows
means *tell me later*.
## The sky leans with the phone, and the parallax finally admits it has state (2026-08-10, 0.4.2)

Davide asked for "parallax effect on the background stars using gyroscope". The effect is built and
the field now moves on two inputs instead of one — but almost every decision below is about a word
in that sentence being the wrong instrument, or about what the second input costs a rule the Sky
pass had just spent.

### It is not the gyroscope, and that is not pedantry

A gyroscope reports angular **rate**. Holding a phone at a fixed lean reports nothing at all, so the
only route from a gyroscope to a pose is to integrate — and an integrated rate accumulates its own
bias until the sky has drifted off the screen on a phone lying still on a table. Minutes, not hours.

What the effect actually wants is *which way is down*, which is a question gravity answers directly
and absolutely, with no integration and so nothing to drift. Android publishes it as `TYPE_GRAVITY`
(falling back to `TYPE_ACCELEROMETER`, which is the same vector plus whatever the hand is doing) and
iOS as `CMDeviceMotion.gravity`. Both are fused from the gyroscope among other things, so the
gyroscope is in here — as an ingredient of a sensor that has already had its drift corrected, rather
than as the thing being read.

**Rejected on the way: iOS's `CMAttitude` and Android's rotation vectors**, which hand out Euler
pitch and roll and look like exactly what is wanted. They are worst precisely where this game is
held: a phone upright in portrait sits at the gimbal singularity, where roll goes degenerate and can
snap by half a turn on a millimetre of movement. The sky would flick. `TYPE_ROTATION_VECTOR` also
drags in the magnetometer to compute a heading nothing here uses, which means a lurch every time the
player sits near a speaker or a car dashboard.

### Two attempts at reading a pose, and the first one shipped a fold

The first version described a pose as **two elevations of a device axis above the horizon** — `asin`
of a gravity component — which is defined in every pose, needs no order, has no singularity, and is
wrong. It was replaced before merge because an adversarial review measured it, and the measurement is
worth keeping: `asin(y)` cannot distinguish a phone tipped 80° from flat from one tipped 100°, so the
response **rectifies at exactly upright-in-portrait** (leaning either way moved the sky the same way)
and **inverts past it** (reading lying down leaned the sky backwards). The crease sat on the most
common pose there is. Worse, the two axes were not independent — `sin²(pitch) + sin²(roll) ≤ 1` pins
them to a disc — so a *pure sideways lean* of an upright phone produced six degrees of spurious tip
and the field went diagonally.

What replaced it keeps no angles at all. Both readings are normalised to the unit vector pointing at
the ground, and a movement is the **cross product** of the slow direction with the fast one — the
axis of the turn, scaled by the sine of its angle. Every good property of the module comes from that
one change:

- **Constant gain in every pose.** A six-degree turn reads as `sin 6°` whether the phone was flat,
  upright, or tipped past vertical; measured at nine poses across the full half-turn. A rotation
  between two real directions cannot fold.
- **Axes that stay independent.** The cross-axis leak drops from 100% of the signal to at most 1.3%
  of full travel, and it is second order in the lean angle rather than first.
- **The platform sign difference cancels for free**, which is the section below.
- Two properties that are physics rather than shortfalls, pinned by tests so nobody "fixes" them: the
  sideways axis fades as `sin²(elevation)` — spinning a phone flat on a table does not move *down* at
  all — and the third component of the cross product is yaw, which gravity cannot observe, so it
  stays at zero and documents the instrument.

### The rule it spends, stated rather than smuggled

0.4.0 wrote, in this file and in `Starfield.kt`, that the parallax "is deliberately not counted as an
animation and it is not a dodge: it has no duration, no clock and no running state." Of the scroll
term that is still exactly true. **Of the tilt term it is not**, and the 0.4.0 entry was careful
enough about not making a dodge that pretending otherwise now would be one. `TiltMonitor` keeps two
exponential averages between samples; that is running state, and each average has a time constant.

**And the first draft of this entry then overclaimed, which is worth leaving in.** It said "putting
the phone down stops the sky dead", in five places including the changelog — and that is false by
this feature's own constants. Because the centre follows the pose, a lean that is over still settles
back to level across about ten seconds, so there is a stretch after the hand stops in which the sky
is moving with nobody touching the device. It is the only thing in this app a player can watch
happen with their hands in their lap, and it is the one part of this that genuinely tests the rule.

What survives is the *reason*, and it survives in a shape 0.4.0 already accepted rather than a new
one. The rule exists so a game whose premise is that it progresses while closed never draws anything
a player could read as "it is happening now". The four transitions that passed spend it as one-shot
settles — each runs once when the thing it describes enters composition, then holds forever. The
recentring is that shape with a different trigger: it runs once per movement the player makes, decays
to rest, and cannot start itself. Nothing loops, nothing repeats, and the only thing in the world
that can begin it is a hand.

**The alternative that would have made the tidy sentence true, and why it loses.** Gate the slow
average on the fast one actually moving, and the sky does stop dead the instant the phone does. It
also never comes back to level for anyone holding a lean still — which is the exact failure the
following centre exists to prevent, and the reason the two rejected centres below were rejected. It
trades the whole feature for a sentence.

**The flat-background rule is not spent again, and 0.4.0's paired accounting is the reason to say
so.** No star was added, no texture, no second exception: the same hundred and one circles that
0.4.0 argued for are moved a little further. The exception is exactly as wide as it was.

### The centre follows the pose, which is the one real design decision here

There is no correct pose to measure a lean from. The phone is flat on a desk, at forty degrees on a
sofa, or overhead in bed, and **any fixed zero point leaves two of those three pinned against the
stop for the whole session**. So the tilt is the gap between a fast average of the attitude (120ms,
which is the noise floor — a gravity sensor at rest still wanders a tenth of a degree, and a sky that
shimmers in a still hand is worse than no sky) and a slow one (4s, which is the centre). Two averages
of one signal, subtracted, is a band-pass filter, and naming it that is the clearest way to say what
it does: movement passes, sensor noise and holding posture do not.

**A centre captured once, at the first sample, was rejected for the case that breaks it** — the app
is opened flat on a table and then picked up, which is most launches, and the field spends the rest
of the session at full deflection. **Absolute tilt with no centre at all was rejected harder**, for
every launch that is not upright. The cost of the choice is that a lean which is simply held fades
back to level over about ten seconds; that reads as settling, and it is the price of working in every
posture rather than one.

**Two constants that came out of this and are not obvious.** A gap longer than a second is treated as
an *absence* and restarts both averages rather than being fed to them — feeding it does the opposite
of re-centring, because the fast average arrives instantly while the slow one crawls a fifth of the
way, and the sky slams against the stop on the first frame back from a backgrounded app. And every
reported value is snapped to a two-hundredth of full travel, which is a performance decision wearing
a visual one's clothes: without it the last digit of a gravity reading jitters forever, every sample
is a new value, and a hundred and one stars redraw fifty times a second to show nothing.

### `client/tilt/{domain,data}`, and a `domain` with no `:core` in it

The same split the debug menu has, for the same reason and with the same payoff: what a lean *means*
is arithmetic, so it lives where it can be tested without a phone in somebody's hand, and the thirty
tests behind it are the only reason any of this could be written by a session that cannot hold a
device — they are also what caught the fold above, once they drove real poses instead of hand-written
numbers. It is also the first `domain` in the build that does not depend on `:core` — how a device is
being held has nothing to do with a colony — and that absence is worth keeping.

### The trap that would have shipped twice, and what finally stopped it

**Android reports the reaction to gravity and iOS reports gravity, so the two vectors are exact
negations of each other.** Android's own documentation says a phone lying flat on a table reads
`z = +9.81`; iOS reads the same phone as `z = -1.0`, and the sign is the same story on the other two
axes. Read one as though it were the other and the whole effect flips, and the sky leans one way on
an iPhone and the other way on a Pixel — the class of defect nobody finds without owning both.

The first draft had exactly that bug **and a test claiming to guard it that could not**, built from a
vector no `SensorManager` on earth produces: it asserted the platforms differed only in `z`, when in
fact they differ in all three. The first fix was two named entry points, `fromGravity` and
`fromReactionToGravity`, on the theory that a name matching each platform's documentation survives
being tidied where a bare `(-x, -y, -z)` at a call site does not.

**The cross product then made the whole question disappear**, and that is the better answer: `(−a) ×
(−b) = a × b`, so negating both operands is invisible, and normalising discards the ten-times scale
difference on the way in. Neither platform file holds a correction, because neither needs one — which
is what the first version *claimed* and this one can prove. The named pair is gone with the angles.
`GravityTest` walks every pose twice, once as each device would actually report it, and
`TiltMonitorTest` does the same end to end through the filter.

### Nothing a player sees on desktop changes, and that is structural rather than lucky

Every screenshot baseline in this repository is recorded on desktop, which has no motion sensor, so
`defaultTiltSource()` there reports `Tilt.NONE` forever and the tilt terms are multiplications by
zero. The one place that is not automatic is the horizontal wrap: the star table has no margin across
(`x` runs 0.0004..0.9845, edge to edge, where `y` was given bleed on purpose), so a sideways lean has
to be taken modulo the width and each star drawn again one width over. Folding an unchanged `x`
through `mod` comes back a fraction of a pixel different — small enough to pass the verifier, quite
large enough to be a drift nobody could read off a diff — so the wrap is **guarded on the lean being
exactly zero**, and on desktop the branch is never taken. `DefaultTiltSourceTest` pins the promise
from the other end.

The consequence worth stating: **there is no screenshot test of a leaning field**, because recording
a baseline needs a machine that can run Roborazzi and this was written by a session that cannot. It
is the one visual check this slice does not carry.

### Reduce Motion is honoured, and the battery question mostly answers itself

A parallax driven by device motion is the textbook thing that setting exists to switch off — Apple's
own wallpaper stops doing this when it is set — so it is read at both edges (`ANIMATOR_DURATION_SCALE`
on Android, `UIAccessibilityIsReduceMotionEnabled` on iOS) and the sky simply holds still. Read once,
when collection starts: watching it properly needs an observer on each platform, and it is a setting
people change roughly never and always outside the app they are changing it for.

**On battery**, this repository has no foreground observer on any platform — the limitation the Sky
pass recorded — so a naive reading is that the sensor runs forever. It does not, and for once both
platforms do the right thing unasked. iOS suspends a backgrounded app, so the queue stops being
serviced. Android 9 cut off continuous-reporting sensors for apps that are not in the foreground, and
`targetSdk` is 36. The gap is API 26 and 27, which is the bottom two rungs of `minSdk` and where the
cost is a fused low-power sensor at 50 Hz feeding a filter that emits nothing while the phone is
still.

### Two things a device session owns, and they are not tidy-up

1. **Every feel constant is arithmetic rather than a measurement** — 12° to full deflection, 24dp of
   travel before each plane's factor, 120ms and 4s. They carry the caveat `ShakeMonitor`'s three
   carry, and for the same reason: nobody has held a phone running this. Expect the first real
   session to move them, and expect the **sign** to be the thing most likely to be wrong. The sky
   moves *against* the lean here, so that it reads as something seen past the cards rather than
   sitting on them; both axes are one subtraction from being the other way round, in one place.
2. **The tilt is in the device's frame, not the interface's.** Neither platform rotates its motion
   frame when the UI does, and this app ships both landscape orientations on iPhone and all four on
   iPad — so in landscape the axes are swapped and one is mirrored, and a lean moves the sky
   diagonally where it should move it sideways. It degrades rather than breaks. The fix is a rotation
   by the interface orientation; Android would read it from `DisplayManager` in five lines and iOS has
   no equivalent that is not a main-thread UIKit call from inside a sensor callback, so writing the
   easy half alone would be precisely the cross-platform drift `:client:tilt:domain` exists to
   prevent. It wants both halves at once and a device to check them on.

### And a rule this entry did not notice it was spending: who wrote it

`.claude/rules/session-roles.md` says a cloud session may not work on **"anything a player sees. No
Compose, no `presentation` module, no screenshot baselines, no design-system components."** This
slice was written by a cloud session and it edits `Starfield.kt`, `MainScaffold.kt` and `App.kt`.
The first draft of this entry argued the no-animation rule at length and never mentioned that one,
which is the more consequential omission of the two — in a repository whose whole culture is the
accounting.

**Settled by Davide, 2026-08-10, on being shown it:** *"It is true this was a cloud session, but it
was animation tuning, not mere design change, so it is ok."*

That is a sharper line than the one this entry was reaching for, and it is now the third exception in
`session-roles.md`. Claude Design returns frames; a frame can be authoritative about what a card
looks like, and cannot be authoritative about how far a field should travel per degree of wrist or
how long a lean should take to settle, because nobody knows that without holding a phone. So the
debug menu's test — *is there a design this code could be wrong about* — answers no here too, for a
different reason than it did there: not "nobody drew this screen" but "nobody can draw this quantity".

The condition that comes with it is the one this slice already met: **every invented number is marked
in the code as arithmetic rather than measurement**, and the check that replaces a design review is
an install. Davide took that himself — *"I will try the app from TestFlight, and open another session
should it need tuning."* What the exception does not reach is unchanged: a screen, a component, a
layout, a baseline, or motion that a handoff has already specified, as the Sky pass's four
transitions were.

## An exclusion from the coverage gate is Davide's call, and it needs a failing report (2026-08-10)

0.4.2 added three `classes(…)` lines to the root Kover filter, hiding `:client:tilt:data`'s Android
half — a `SensorManager`, a listener the platform calls, and the `Context` slot Android cannot
derive. Davide, on finding them: *"You excluded something from coverage check without my explicit
permission. This is very bad! We need a ROCK SOLID reason to exclude something from coverage
report!"* They are out.

**The rule was already written**, which is what makes this worth an entry rather than a fix. The
`test-coverage` skill said *"adding an exclusion needs evidence from a real report, not a guess —
every entry above was added after seeing it in one."* The three lines went in during the same commit
as the code they hid, by analogy with the shake-detector entry three lines above them, before any
report existed. The comment even said so out loud — *"listed at the same moment it was written rather
than after a Coverage job failed on it"* — and shipped anyway. Naming the deviation is not the same
as having permission for it, and a comment is not a review.

**And it bought nothing, which is the part worth remembering — and this is measured rather than
argued.** 0.4.2's Coverage run measured 96.9% against a 95.0% floor. Removing the three lines and
letting the job report what they had hidden gives **96.3%**: twenty-six lines, and `:client:tilt:data`
reading 3.7% where the exclusion had it reporting 100.0%. The gate passes either way, with one and a
third points to spare. A written rule was broken to buy a margin that was already there.

**The shape of the near-miss is worth keeping too.** While the exclusion stood, the per-package table
reported that package at 100.0% — not a suspicious number, an excellent one. An exclusion does not
make coverage look bad, it makes it look finished, which is why nothing downstream can catch it and
why the permission has to sit before the fact rather than after.

**Why an exclusion is not the same kind of thing as a threshold**, which is the argument behind
making it Davide's: a threshold that is set too low still measures, and the next run can tell you it
was wrong. An exclusion removes the gate's *sight*, permanently and silently — no future report can
report on lines nobody is counting, so nothing will ever surface it again. That asymmetry is why the
bar is a failing report you can point at plus an explicit yes, and why the first question when the
gate does fail is whether a test can reach the code instead.

The three Android entries that predate this stay: the entry points, the notification scheduler and
the shake detector are argued above, each was added after a real report, and the notification one was
added because the gate actually failed on it. The skill now carries both conditions, and its own list
of exclusions — which had drifted, still naming only the generated code and the two `MainKt`s — has
been corrected to match the build file.

## The lean loses its stop, its centre and its pose penalty (2026-08-10, 0.4.3)

The first device session — Davide, on TestFlight, which is exactly the check 0.4.2's entry said would
replace a design review — came back with two sentences: *"horizontal tilt is very lazy, vertical is
ok"* and *"the area is too narrow… after moving the phone ~20° it stops, I would like to be 360, or
almost."* Both are defects rather than taste, both were predicted by the 0.4.2 entry above without
its knowing it, and the second one turns out to be the cause of most of the first.

### The lazy axis was a gain where it should have been a precision

0.4.2 pinned `sin²(elevation)` as **physics**, in a test named so nobody would "fix" it, with the
argument that an in-plane lean of a phone lying flat is a spin about the vertical and moves `down`
not at all. The physics is right and the conclusion drawn from it was not. That factor is a statement
about **how well the in-plane angle can be read**, and the cross product folded it into **how far the
sky should move** — so a phone held at 45°, which is how a phone is held, answered a sideways lean at
half strength, and one at 30° at a quarter, while the tip axis kept full gain in every pose. The two
axes were built to be independent and were never built to be equal.

Reading the lean as an angle rather than as the sine of a turn removes the factor entirely: the
in-plane roll is `atan2(x, -y)`, which is the same number at every elevation. Where the pose is now
allowed to act is `Bearing.inPlane`, which fades the axis out only across the last stretch into poses
where the reading really is untrustworthy — 30° of elevation down to 15° — and **holds rather than
jumping** past the bottom of it, so a phone laid on a desk leaves the sky where it was.

### The narrow area was the clamp, and the clamp was holding up everything else

`FULL_DEFLECTION_DEGREES` was a stop as well as a scale, so every movement past a small wrist flick
arrived in the same place. It could not simply be widened: a wider clamp makes small movements do
less, and Davide asked for both.

**A cross product cannot answer this**, and that is worth recording because it was the right answer
to the previous question. It reports the **sine** of a turn, so it reads only the quadrant either side
of where it started and folds one quadrant further out than the `asin` formulation it replaced —
invisible inside a 12° clamp, fatal to a full turn. `atan2` of a *pair* of components knows its
quadrant. A running total of shortest steps between such readings is then unbounded **and does not
drift**: each step is measured from the device's own axes, so the total is the current angle plus a
whole number of turns, and the integer is the only part that accumulates. An integrated gyroscope —
the other route to an unbounded reading, and the one Davide's original prompt named — has no such
guarantee; see the entry above for why it was already refused.

The rendering side needed no change at all. `Starfield` already took both shifts modulo the box and
`StarfieldTest` already walked leans past a full turn of the phone, because the wrap existed for the
*scroll* term. One full roll is thirty units, which carries the near plane 418dp — a little over one
screen width. It does **not** put the field back where it started (418 into a 393dp box leaves 25dp,
and the other two planes land 216dp and 86dp out), and it does not need to: there is no landmark in a
tiling field of stars to measure the difference against, which is the same reason the zero point can
be anywhere.

### And the vertical axis cannot have it, which is a theorem rather than a budget

**The first draft of this entry shipped a defect here and the fix is the more interesting half of the
round.** It read the tip as `atan2(-y, -z)` — the elevation of the phone's long *edge* — which is
full-circle and is **not invariant under roll**, because rolling the phone sweeps that edge around a
cone. Under the old clamp the leak was capped at one unit and nobody could see it. Unbounded, a
measured 90° roll at a fifty-degree hold dragged the sky **4.17 units vertically** and a 180° roll
8.33 — more vertical than the sideways movement being asked for, from a purely sideways gesture. The
guard test could not catch it because every sideways case in the suite rolled six or eight degrees,
where the leak is second order.

Trying to fix it turned up the reason it was there. **No reading of the tip can be all three of:
(A) unmoved by a roll, (B) monotonic through a full end-over-end turn, (C) a function of where the
phone is now.** Proof, in this module's own pose model:

    g(elevation, lean + 180°) == g(-elevation, lean)

Roll the phone upside down and tip it *forward*, and gravity reports exactly what tipping it
*backward* the right way up reports. One of those must add and the other subtract, so a reading blind
to the roll cannot separate them. More generally (A)+(C) means the travel is a function of the screen
normal's direction in the world, and an end-over-end turn returns that direction to where it started
— so the travel returns to where *it* started, which is not (B). **This is not a gravity limitation.**
It holds for a fused attitude quaternion too, so `TYPE_GAME_ROTATION_VECTOR` and `CMDeviceMotion`
would buy nothing here; that is worth knowing before anyone reaches for them for a different reason.

The three corners are the three designs that have existed:

| | (A) roll-invariant | (B) full-circle | (C) conservative | |
|---|---|---|---|---|
| `atan2(-y, -z)` | ✗ | ✓ | ✓ | 0.4.3 draft — the defect |
| integrated `∫ω_x dt` | ✓ | ✓ | ✗ | a gyroscope, refused above |
| `atan2(√(x²+y²), -z)` | ✓ | ✗ | ✓ | **shipped** |

**Davide picked roll-invariant, 2026-08-10**, shown the trade. What ships is the elevation of the
*screen normal*: exactly the tip angle at every roll, identical to the old reading at zero roll (so
nothing changes in the pose the effect was tuned in), never degenerate — and running `0..π`, so
tipping on past face-down retraces rather than continuing. The range given up is the half turn in
which the screen points away from the player. The sideways axis keeps its whole turn, and it is the
axis the device session complained about.

It also fixed a pose the draft lost outright: that reading died completely in landscape held with the
long edge horizontal — measured as no vertical response at any roll — because its plane had emptied
out. The screen normal has no such pose, so the tip needs no companion measure of trust at all.

**One cost, and it is the first time this module has needed a sign convention.** Every earlier
formulation was blind to the platform difference for free — the cross product because
`(-a) × (-b) = a × b`, the pair of `atan2`s because negating all three components turned both
bearings by half a circle and every difference cancelled. `√(x² + y²)` does not care about the sign
and `z` does, so the two platforms now come out **reflected**: `tip(-g) = π - tip(g)`, and differences
negate instead of cancelling. A phone would have leaned the right way on an iPhone and upside down on
a Pixel. Since a convention is now unavoidable it is stated in the shared module as two named entry
points, `sampleGravity` and `sampleReactionToGravity`, rather than as a minus sign in a sensor
callback that only one of the two platform files would ever grow.

### Which retires the centre, and with it the rule this feature was spending

The slow average existed **only because of the stop**: with a clamp, any pose simply held would have
pinned the sky against it for the rest of the session, so the zero point had to chase the pose. That
is what left a finished lean settling back to level for ten seconds afterwards — the one thing in
this app a player could watch happen with their hands in their lap, and the admission the 0.4.2 entry
had to make twice.

Without a stop the question does not arise, and none of the three centres that entry weighed is
needed: not a following one, not one captured at the first sample, not "absolute with no centre at
all", which it *rejected hardest* and which is closest to what shipped. What makes it work now is the
thing that was missing then — with a clamp, a wrong zero point is a sky pinned against a stop; with a
wrapping field and no stop, a zero point is not observable at all, because there is no landmark in a
tiling field of stars to be offset from. **Put the phone down and the sky stops** is now simply true,
and what remains is the smoothing arriving over a tenth of a second, which is a response to a
movement that has just happened.

`MAX_GAP` survives and changed meaning: a gap longer than two seconds is still an absence, but it now
**keeps** the travel already made and re-anchors only the direction. Resetting it was harmless against
a clamped reading that was usually near zero anyway; against an unbounded one it would snap the field
back on the first frame after a resume — the same lurch that cut exists to prevent, arriving from the
other side.

### The pose weight had to become a gate, for the same reason

The draft also scaled every accumulated step by a weight that ramped in across a band of poses. That
is unsound and the unsoundness is the same shape as the one above: the weight is a function of the
**elevation** while the step it scales is a **roll**, so the total became a line integral of a form
that is not closed. Retracing a path cancelled — which is all the suite tested — and going round a
*loop* did not. Measured: the walk (50°,0) → (50°,40°) → (20°,40°) → (20°,0) → (50°,0), four ordinary
movements ending exactly where they began, left **2.2 units behind per lap and 11.0 after five**,
without bound.

No weight that varies with the pose can avoid this, so the weight does not vary: it is 1 inside the
readable range and 0 outside it. Inside, the steps telescope exactly and the loop returns to zero.
Outside, a zero step is a *re-anchor* rather than a discard — the reference bearing advances every
sample regardless — so a phone laid flat and spun leaves the sky exactly where it was and picks up
again from wherever the roll then is. Taking the smaller of the two ends keeps the gate symmetric in
time, and there is deliberately **no hysteresis**, since a sticky gate would be the same path
dependence rebuilt by hand.

What is left is bounded and forced: a loop that *crosses* the gate leaves the arc rolled below it
uncounted. Down there the roll is a spin about the vertical, so there is nothing to count.

### What did not move, deliberately

Neither feel constant. 12° per unit of travel and 24dp on the reference plane are unchanged, because
what the device session reported was not that a small movement moved too little — it was that a large
one moved no further, and that the two axes disagreed. Changing the scale in the same round would
have made the two reports impossible to tell apart on the next install. The sideways axis is roughly
two to three times livelier at a normal hold, and all of that comes from removing the `sin²` penalty
rather than from a bigger number.

### How the two defects got out, which is the transferable part

Both were introduced by *removing a clamp*, and both had existed underneath it. The clamp was not
only limiting the effect, it was hiding two errors that were smaller than it — so lifting a bound is
not a neutral act, and the things it was bounding have to be re-derived rather than assumed to have
been fine all along.

Both also survived a suite that was green and, on the face of it, thorough. What it never did was
**vary two coordinates at once**: `EVERY_POSE` walks elevation with `lean` left at its default, and
every sideways test rolled six or eight degrees. A lean sweep at a fixed elevation asserting the tip
does not move, and a closed pose loop asserting the total returns, are the two tests that would have
caught them the day they were written. Both are now in `TiltMonitorTest`.

### Still open, and it is the one thing gravity cannot be asked for

**Yaw — turning the phone left and right about the vertical — remains invisible**, and it is the
movement most people reach for first when told a background responds to the phone. Nothing in this
round changes that: spinning a phone flat on a table does not move `down`, so no reading off gravity
can see it. Answering it means the fused rotation vector on Android and `CMDeviceMotion.attitude` on
iOS, which the entry above rejected for the gimbal singularity at upright-in-portrait and for
dragging the magnetometer into a heading nothing here uses. Both objections still stand and both are
now avoidable — a quaternion has no singularity, and the game rotation vector omits the magnetometer
— so this is a scope call rather than a settled refusal. It is Davide's, and it is not implied by
either sentence he sent.

## The sideways lean was pointing the wrong way, and a sentence was hiding it (2026-08-10, 0.4.4)

Davide, off a TestFlight install: *"Vertical parallax is perfect, but horizontal is inverted."* Both
halves are correct and the fix is one minus sign in `TiltMonitor.tilt` — `travel(-reading.lean)`
becomes `travel(reading.lean)`, so dropping the right edge now carries the sky *towards* the edge you
dropped rather than away from it. Nothing else moved: not a constant, not a formulation, not the
vertical axis, and no screenshot baseline, since desktop reports `Tilt.NONE` and `Starfield`'s
`wraps = leanX != 0f` guard means a level field draws exactly what it always drew.

The one-line fix is not the entry. Three things around it are.

### The two axes never had one rule, and writing them as though they did is the defect

The comment those two lines carried, unchanged since 0.4.2, was:

> drop the right edge and your eye moves right of the screen, so what was hidden behind the right
> margin comes into view and the field slides left. Tip the top away and your eye moves above it, so
> the field slides up.

The second sentence is a derivation. The glass is a window on a sky far behind it, so tipping the
phone **aims that window somewhere else**: tip the top away and the line of sight through it swings
downwards, which is a camera panning down, and a camera panning down carries what it is looking at up
the frame. That is forced, and it is the axis a hand came back calling perfect.

The first sentence is the second one wearing its clothes. **An in-plane roll turns the phone about
the very line of sight the tip swings**, so it aims the window nowhere new — which is why the same
comment block already conceded, two paragraphs later, that mapping a roll to a horizontal slide is
"an artistic choice rather than a literal parallax". Taken literally a roll would *rotate* the field,
not translate it. So there was nothing for "your eye moves right" to be true of, the sentence
asserted a consequence anyway, and the consequence was backwards.

**The transferable form: a derivation that covers one case and a convention that covers the other,
written in one voice, is a convention nobody will audit.** It reads as settled. What replaces it in
the file is the two halves marked as what they are — the vertical one with its argument, the sideways
one with the date and the words of the person who chose it.

### Fixing the magnitude is what made the direction findable

The sign has been this one since 0.4.2, and 0.4.2 shipped the sideways axis at a quarter strength in
the hand because of the `sin²(elevation)` penalty. *"Horizontal tilt is very lazy"* was the report,
and **an axis that barely moves is an axis whose direction nobody can judge.** 0.4.3 removed the
penalty and the clamp; the very next install named the direction.

That ordering is worth expecting rather than being surprised by. A defect can *mask* a defect behind
it, so the first session after a fix lands is more informative than the fix's own reasoning, and the
right posture is to expect a second report rather than to treat one as a sign the first fix missed.
0.4.3's entry already recorded the version of this that bites in the other direction — lifting a
clamp exposed two errors the clamp had been hiding. This is the same fact from the useful side.

### Why no test catches it, and why that is not an omission

There is no test that could have caught this, and adding one is not the follow-up. **A convention is
not a property**: `a lean to the right pushes the sky left` was a true statement about the code for
two releases, and it was green for two releases. Renaming it to `…pushes the sky right` and flipping
its assertion changes what the suite records, not what it verifies.

So the test is explicitly labelled as *where the convention is written down, not where it is checked*
— and it now carries beside it the one part of the same test that **is** a property: the two edges
must answer oppositely and by the same amount, whichever way round the pair is. That half would catch
a broken formulation. Nothing will ever catch a wrong choice except a hand, which is the argument for
the loop in `session-roles.md` rather than an argument for more tests.

## The wall in the opening was the sample, not the map (2026-08-11, 0.5.1)

Davide, having played 0.4.4: *"Galaxy interactions are too tough in the early game! I would expect
the user to be able to interact with neighbouring planets without too many challenges, with I needed
2 day to get robotics to level 4, and now I need to upgrade at least 4 adaptations for the easier
planet."*

Both halves reproduced without anything having to be discovered — `printGateClock` already put
Robotics 4 at hour 33, and the harness's own home system asked five adaptation levels across two
ladders for its cheapest neighbour. The round is written up in `balance-log.md` **round 18**; what
belongs here is the two decisions and the one rule they bend.

### The diagnosis, which is the whole of the entry

Every galaxy report in the harness measured the **map**. A player does not see the map. Genesis
surveys the home system and nothing else, so ~4.75 worlds are the entire content of the Galaxy
screen on day one — and `galaxy-sheet.md` §9's payoff, *each adaptation level roughly doubles the
settleable count*, is a galaxy-wide statistic that a sample of five cannot show. 1.81% of worlds pass
every band, so **92% of colonies open on a wall and stay there**.

`printDoorstepReport` is the instrument that was missing, and it is the first report in the file that
sweeps seeds rather than hours. Over 1,000 seeds: the median home system asked for **seven** levels,
**54,242** resources priced at 1 : 2 : 3, and **39 hours** of the one shared research slot. Davide's
five-level system was in the better third. **9.36%** of colonies could change a verdict for one
adaptation level.

That reframes the request. The complaint sounds like a price complaint and is not one: the first
level of any ladder is 480 priced and 18 minutes, which a day-two colony pays without noticing. What
was expensive was the *distance to the nearest world worth pointing at*, and no cost curve, duration
or discount addresses a distance.

### Decision 1 — `homeFor` gains a clause, and the alternatives were all worse

**Genesis takes the first tolerable world in a system that also holds a neighbour one adaptation
level away**, keeping the best system it has seen and stopping at the first that qualifies. After:
**99.8%** of a thousand swept seeds open a neighbour within one level, median bill **480** and
median wait **18 minutes**.

Rejected, each for a reason the docs had already written down:

- **Widening the tolerance bands** — `galaxy-sheet.md` §9 names this in advance as the one thing not
  to do: *"the lever is not this row — it is widening all three bands together, which raises row 1
  with it."* It makes 4,746 worlds easier to fix a defect in 4.75.
- **Making a level widen further** — same objection with a different multiplier, plus it flattens the
  three ladders' differentiation.
- **Cheapening the ladder** — answers a question nobody asked. Four levels at a third of the price
  is still four projects through one slot.
- **`fleet-sheet.md` (b), a guaranteed *good* neighbourhood** — genuinely rejected, and the reckoning
  is recorded in that file beside the option rather than only here. The short of it: (b) guaranteed
  *worth*, this guarantees *reach*, and the measurement separates them — the doorstep world reads
  `Settleable` **28.1%** of the time against **51.2%** for the neighbour a player used to get. The
  guarantee makes your nearest world easier **and poorer at once**, which is *"an easy world is a poor
  world"* holding rather than bending.

**One level rather than two, and the walk crosses galaxies.** Two levels is two projects and possibly
two ladders, which stops the promise being *your first adaptation level opens a world you can see*. A
qualifying system is 0.50% of all systems, so a walk bounded to one galaxy finds one 77% of the time
and a walk over the whole space finds one for 99.8% of a thousand seeds.

**The walk order was wrong in the first draft and the suite could not see it.** A flat index over the
1,000 systems leaves the seeded galaxy when its *tail* runs out rather than when it has nothing to
offer, so **50%** of colonies opened outside the galaxy their seed named against **22%** for a walk
that reads the seeded galaxy whole first. The promise lived in a comment and nowhere else — every
other test asks about the home *world*, and a home in the wrong galaxy passes all of them. It is
`session-roles.md`'s tilt lesson in another file, and the fix is the same shape: `seededGalaxyOf` is
a named function now so the promise is assertable, and it is asserted.

### Decision 2 — `AdaptationBalance.GATE` 4 → 2, which round 12 pre-authorised

*"If the gate turns out to sit far past the first BLOCKED screen, lowering it to 2 or 3 is cheaper
than re-pricing anything."* It had: hour 33 against hour 12 at Robotics 2.

**Round 6's argument for 4 is not overruled, it is re-read.** It asked that the branch open *after*
the player has met the Galaxy screen and read a `BLOCKED` row — and nothing gates the Galaxy tab, so
that is true from the first frame at every gate level. What the clause actually rules out is **1**,
the applied branch's own gate, where five rows would open at once and the locked row would leave
normal play. Three was rejected on the measurement: Robotics 3 and 4 are six hours apart where 2 and
3 are fifteen.

The trade is nine points of gate refusal becoming nine points of price refusal (35.25% → 25.64% and
5.12% → 14.10% over the census's first two days), which is the point rather than a side effect —
round 12: *"a price is a curve, a slot is a rule, a requirement is a gate."* Median **kinds** of
action offered in the opening goes 3 → **4**, which rounds 8 and 12 each concluded no number in
`PlaceholderBalance` could reach.

### What it cost, including the part a cloud session cannot finish

- **The golden save moved and the schema did not.** `home` and `surveyed` are content genesis
  computes, not keys, so the pinned string in `GameSaveTest` is rewritten and nothing an installed
  build can read changes. **No player's map moves**: home has been stored since schema 4 and no
  migration recomputes it. The frozen `VERSION_*` fixtures still carry 3:165 and must never be
  rewritten to agree with the new pin — that divergence is now the thing they exist to prove.
- **Eleven assertions in `GalaxyUiStateTest` and the shell's `AdaptationBehaviourTest`** were pinned
  to 3:165's slots. `AdaptationBehaviourTest`'s fixture is now **derived** from the world it names
  rather than written out — four hand-typed numbers that all had to agree with each other and with a
  world none of them named is the shape that made this expensive, and it will not be expensive twice.
- **The galaxy screenshot baselines move**, and `TestGalaxyUiState` says in its own header that this
  is correct: *"a change that moves these numbers is a design decision that should redraw the
  images."* The hand-written every-verdict frame had one derived half and one typed half, and the
  typed half was quoting a world it no longer drew its axes from; both halves are derived now.
- **The research baselines move by one character** — "Requires Robotics 4" → "Requires Robotics 2" —
  and the frozen fixture's narrative moved from a colony at Robotics 2 to one at Robotics 1, which
  is the same frame (four of six rows dimmed) told about a legal colony.
- **A cloud session cannot record any of that**, and per the entry above — *"the agent dispatches the
  job itself"* — the answer is `record-screenshots.yml` against the slice's own PR, not a hand-off.
  It is named here because that job needs a PR number, so the recording cannot precede the PR.


## A record job that can be served from cache records nothing (2026-08-11, 0.5.1)

`record-screenshots.yml` ran `./gradlew recordRoborazziDesktop` with the build cache live, and at
0.5.1 that quietly produced a **wrong success**: sixteen galaxy baselines were re-recorded and
pushed, the job reported success, and three research baselines were left showing
`Requires Robotics 4` — a requirement string the app had stopped producing two commits earlier.

### Why it happens, which is not a Gradle bug

**Recording writes into `src/*/screenshots`, a source directory, not a task output.** So nothing
Gradle models about the task describes the thing the job exists to do. A `desktopTest` replayed
FROM-CACHE reports success and writes no PNG, `recordRoborazziDesktop` then reports UP-TO-DATE, and
the commit step finds a clean tree.

The sequence that exposed it is worth writing down because it is the *normal* one, not an exotic
one:

1. A dispatch fails in one module. Every other module's `desktopTest` has already run and **stored
   a cache entry**; their PNGs are written into a workspace that is then thrown away.
2. The failure is fixed and the job is dispatched again.
3. The failing module now runs and records. Every other module is served from step 1's cache and
   records nothing.

So the job is least reliable exactly when it is being used most — after a failure, which is when
anybody dispatches it twice.

### The fix, and why not a smaller one

`--rerun-tasks`. It ignores up-to-date checks and cache hits and re-executes everything, which is
the only setting under which "the job succeeded" means "every baseline on disk is what the code
draws". `--no-build-cache` alone was rejected: it stops the *read* but leaves the up-to-date check,
which is the other half of the same hole.

The cost is a few minutes on a job that is manual, rare, and already the slowest thing in the
repository. The alternative cost is a committed baseline asserting a screen the app cannot draw —
and since recording **replaces the assertion**, nothing downstream would ever catch it.

### What the fix widened, found immediately after

`--rerun-tasks` closed the hole and opened a smaller one in the other direction, and the pair is
the actual lesson. **The committed baselines are macOS-recorded** — `oltreRoborazziOptions` says so
in as many words, and the whole reason it carries a `maxDistance` and an 8% pixel budget is to
absorb what Linux does to them: *"±1/255 across gradient fills (dithering) and ≥10/255 on 2.4–5.6%
of pixels (glyph anti-aliasing)"*.

This job records on Linux. So every module it **executes** comes back re-rendered, whether its
content changed or not, and the cache was accidentally limiting the blast radius. The first
dispatch with `--rerun-tasks` rewrote **32** baselines where 26 had a reason to move; the other 22
belonged to `:client:colony:presentation` and `:client:shell`, which 0.5.1 does not touch at all.
Measured before reverting them: content identical frame for frame, differences confined to glyph
edges and gradient dither, exactly the profile the options file describes.

They were reverted by hand and the line drawn was *"which modules does this change render
differently"* rather than a pixel count — galaxy and research keep their new frames, colony and
shell keep the ones they had.

**The right fix is a commit step that drops any PNG whose diff sits inside the verifier's own
tolerance.** That would make the job self-limiting: it would record everything, and commit only
what a human would have to look at. It is not built — it is a change to how the repository decides
what a baseline *is*, and that is Davide's call rather than a session's.

**And one thing it turned up that is worth its own look:** a one-character text change is a few
hundred pixels on a 393dp frame, which is far inside the 8% budget. So the stale
`Requires Robotics 4` baselines would very likely have **passed** verification against the corrected
screen. The tolerance is calibrated for renderer drift and cannot tell a small genuine change from
it — which is the argument for reading the images rather than trusting the check, and is the reason
the job posts them.

### The part that generalises

A cached task is a claim about *outputs*. Any job whose real product is a change to **tracked
source** is outside that claim, and will be silently skipped the moment its inputs hash the same.
Recording baselines is the instance the repository has; a code generator writing into `src/` would
be the next one.

## Every row states what the level is worth, and the sheet says why (2026-08-11, 0.6.0)

The design is Claude Design's *Row Purpose* sheet, direction 1b with 1c's gate ladder folded into
the sheet. Its own argument is short: rows have always stated a price and a wait and never whether
the level was worth taking, and the one place the game already answered that — the adaptation
shortlist, counted against the worlds this player has surveyed — proved the harder version was
buildable. This slice is that sentence applied to the other twelve rows.

What follows is the calls the build made, not the design's. The design's own reasoning lives in its
decision sheet, which is the durable half; project `aea4cd09-c111-4e9a-8b7d-c25cea371fd4`,
`Purpose Decision Sheet.dc.html` and `Row Purpose.dc.html`.

### Payback is priced at the game's 1 : 2 : 3, and the sheet's own reason not to was wrong

The sheet left this open, and chose the optimistic form: *"A mine costs metal and crystal and repays
in metal; the number shown counts the metal. It is optimistic, and the honest alternative needs a
trade ratio the game does not have."*

**The game does have one.** `AdaptationBalance` prices its three deliberately-equal ladders at
"the game's 1 : 2 : 3" in as many words, `BalanceBenchmark` has divided every payback in the golden
report by exactly that since it was written, and `ResearchSlotBalanceTest` asserts the branch's
24-hour promise with it. The premise did not survive contact with the code.

And the optimistic form is not implementable as stated. **The Deuterium Synthesizer costs no
deuterium** — 225 metal and 75 crystal at level 1, nothing else — so "the cost in the resource it
repays in, over the gain in that resource" is zero over something, and the row would advertise a
free level. That is not a rounding problem; it is the rule having no answer for one of the six
facilities.

So payback is `priced(cost) ÷ priced(gain per hour)`, in minutes, with the gain measured over the
three `effective*ProductionPerHour` functions `advance` actually accrues with. The cost of the
choice is that the two halves of the sentence no longer divide into each other: a player who divides
12,458 by 122 will not get the hours the row prints. The benefit is that every row has an answer,
multi-resource gains are counted rather than dropped, and the number matches the one the balance
tests have always used. **This is a balance call and it is Davide's to overrule** — the whole of it
is `paybackOf` and `ResourceKind.weight` in `LevelPurpose.kt`.

### A level that costs you income is its own verdict, and the frames have no frame for it

The design has three ways for a row to read: an income row, an inert one ("nothing while you are in
surplus"), and a build-saving one. A test of the exhaustiveness — *every facility and every
technology answers in every state the game can reach* — failed on the first state the game deals.

**At genesis one Solar Plant supplies 50 against 40 drawn, and a second Deuterium Synthesizer level
draws 20 more.** Taking it tips the colony into deficit, `scaleByEnergy` divides all three mines by
`produced/consumed`, and the priced delta is **negative**: the level raises its own rate by 3 and
lowers the other two by more. Under the design's three cases that row falls through to nothing and
states no verdict at all, which is the one outcome the whole design exists to prevent.

`LevelPurpose.Throttled` is the fourth case, and the row reads *"throttles every mine · Solar Plant
2 covers it"*. It reuses the sentence the power indicator's fix line already writes, one row earlier
and before the money is spent. It is reachable on day one, and it is arguably the most useful thing
any row on the screen says.

The general form is worth keeping: **the frames were drawn against a colony a few days old, and the
states a design has no frame for are the ones the opening deals.**

And it has a second instance, found by an adversarial pass over the finished diff rather than by a
test. The Research screen's "worth nothing" copy was written as if only Photovoltaics could reach it
and only in surplus, on the argument that Extraction and Enrichment always move a rate. They do not:
`scaleByEnergy` floors `rate × produced / consumed`, so a colony at 40% can buy an Enrichment level
whose entire gain rounds away before it reaches the stores. The row would then have read *"nothing
while you are in surplus"* on a colony in deficit, and the sheet would have named Photovoltaics on
the Enrichment row.

**The discriminator is the colony's power, not the technology** — and the second reading is the
cheaper one to hold, because it has an answer for a case nobody enumerated. A project that is worth
nothing now reads *"nothing while your mines are throttled"*, and the sheet names the row it is on.

### The four cases and the one number, together

The four `LevelPurpose` members are what a row can be, and the fourth exists because of the two
paragraphs above. Stating them once, because the temptation on the next screen will be to add a
fifth: **Output** — the level raises income, and the priced payback says when you have it back.
**Inert** — it raises a supply nothing is limited by. **Throttled** — it raises a draw the colony
cannot power, so taking it lowers every other rate. **Sooner** — it raises no rate at all and
shortens a build instead. `Unmeasured` is not a fifth case; it is the ceiling, where there is no
next level to price and no upgrade to offer either.

### The Nanite Factory's relief is quoted at the gate, not at the reader

`upgradeDuration` divides by `1 + robotics` last, so "what does a level-30 mine cost unaided" has no
single answer — it is 2,982 hours at Robotics 0 and 271 at Robotics 10. Quoting the colony's own
level is the obvious reading and it is wrong twice over: on day one the row claims a 2,982-hour
build, which is true and unusable, and the claim then **halves every time an unrelated building goes
up**, so a headline about the building churns whenever the reader changes.

`deepBuildRelief()` takes no receiver at all and quotes both figures at
`NANITE_ROBOTICS_REQUIREMENT`, because there is no state in which somebody builds a Nanite Factory
at Robotics 3. The relief is a fact about the building; what it would take *you* is the pointer
underneath it. The published figures move from the brief's 186h → 16h to 271h → 23h 49m, which is
the same eleven-fold cut measured at a level the game actually enforces.

### The verdict replaces the effect line on Research, including the adaptation band

The sheet's call 1 states the exception — *"a research row's effect line, which the verdict replaces
rather than joins: two lines of numbers about the same level is where a dense row becomes an
unreadable one"* — and its frames show it applied to the adaptation row too: Thermal reads name,
verdict, costs, and no band. Its prose says "nothing was done to it", which is true of the
*shortlist* and not of the band above it.

So `EffectLine` is gone from both branches and `+21% → +33% Solar Plant output` and `−30 … +45 →
−44 … +59 °C` are the first sentence of the sheet each row opens. The adaptation row goes from four
lines to three; the tallest row in the app is now a watched Colony row at four, which is exactly the
height the adaptation row has been since the shortlist shipped. Nothing got taller.

### A locked row states the payoff — on both screens, which the frames only showed on one

The frames' second hard case is the locked Nanite Factory stating what it is worth twelve days
early, at 42% dim, under the requirement. Research's locked rows were left as they were, on this
module's older rule that *"a locked row is name, level and requirement"* — and that rule is exactly
what this design's second hard case argues against. **The only question a gate leaves open is
whether it is worth pushing for**, and a row that states the requirement and stops has withheld the
one fact that answers it. Both screens now put the verdict under the requirement.

### The sheet is in the design system, not in either feature

Colony and Research open the identical sheet, and the two row composables next door are the standing
argument: `FacilityRow` and `ProjectRow` have stayed identical by luck, edited twice, for four
releases. `RowSheet` / `RowSheetContent` and `RowVerdict` live in `:client:design:component` and
carry no knowledge of a building or a technology — the features word every string and hand over
plain data, which is `CostChipUiState`'s precedent down to it also carrying a `:core` enum.

The chrome and the contents are split for `DebugSheet`'s reason, and it is the reason a behaviour
test can exist at all: every assertion about what the sheet *says* renders `RowSheetContent`
directly, so nothing depends on a popup being reachable or an enter animation settling.

**The sheet's open state is local to the screen**, a `remember { mutableStateOf<…?>(null) }`, not
hoisted to `GameSession`. Which row is open is not game state — it does not survive a launch, it is
never saved, and it changes nothing the notification schedule reads. Hoisting it would have grown
`App.kt` by two callbacks to model something the composition root has no opinion about.

### A tap on the card body is the first one in the app, and it merges the semantics tree

Cards have never been tappable outside their action, and making one clickable sets
`mergeDescendants = true` — which folds every string on the card into one node and makes an
unscoped `onNodeWithText` match several rows and fail on ambiguity rather than on the claim. The fix
is in the Robots (`useUnmergedTree = true`, and a `card(…)` tag for the target next to the `row(…)`
tag for the text column), never in the assertions. Both modules did it that way; it is the shape to
copy the next time something becomes tappable.

### What is deliberately still open

- **The payback ratio**, above — a balance call, flagged rather than settled.
- **Nothing navigates.** The sheet's pointer names the row to look at instead and does not link to
  it: accent means "go tap this" and nothing else (settled 0.0.18), and a cross-tab tap would be the
  first in the app. The pointer is muted, and the player's thumb already knows where rows are.
- **The 320dp leak the design admits.** A verdict drops its second clause in a Slide Over pane, so
  `LV 10 → Nanite` is sheet-only there. The alternative is a "more" glyph on thirteen rows, which
  costs more than the leak.
- **The level badge is written by hand in three places now** — both row composables and the sheet
  heading. The two rows' copies also swap the number behind a completion sweep and the sheet's has
  nothing to announce, so extracting it is not a pure move; it is a small piece of work with a
  baseline risk, and it is worth doing on its own.

## Nothing in the coverage table may go down (2026-08-12)

Davide, replacing `min(last main run, 95%)`: *"remove 95% limit, but make it so no value can go
lower, for every single PR, any coverage value in the table must either be equal or higher, never
lower."* Two changes in one, and they pull in the same direction.

**The ceiling is gone.** Above 95% the old rule let coverage fall back to 95% — slack bought so
that a project in the high nineties would not spend every PR arguing about a tenth of a point.
The project has been in the mid-to-high nineties for a while without that argument happening, so
the slack was buying nothing and permitting a real regression: a PR could shed a point and a half
of coverage and merge green.

**And the gate now judges ten values, not one.** Line and branch, for each of unit / integration /
screenshot / behaviour / all. The single-number gate could pass a PR whose total rose on
well-tested new code while a behaviour test quietly stopped reaching a screen it used to drive —
the totals hide exactly the movement the per-kind split exists to show, and gating the split is
the only thing that makes the split load-bearing.

```
pass  ⟺  every gated value ≥ the same value on the last main run
```

**The cost is a rename between kinds, and it is accepted rather than overlooked.** The old
decision gated the total precisely *because* moving `FooTest` to `FooBehaviourTest` drops the unit
row through no fault of the tests. That is still true; it is now the PR's problem. The trade is
worth it because the failure it prevents is silent and the failure it causes is loud — a rename
that lowers a row shows up as a red gate naming the row, and the fix is a test, which is what the
gate is for.

**Three things are deliberately not gated.** *Test counts*, because a count is not a coverage
value and deleting a redundant test is not a regression. *The per-package breakdown*, because a
package that is new, deleted or renamed moves cells with no regression behind it — Davide, on
being asked: *"We don't need to check each package separately, so a new package without tests
would decrease the coverage"*, which is the totals doing the package table's job. And *any value
only one side of the comparison has a number for*: the first behaviour test the project ever runs
has nothing to be measured against, so it is left out rather than compared to a zero nobody
measured, and it joins the ratchet on the next `main` run. A `—` — a counter with nothing to
cover, like `:client:design:core`'s branches — is unjudgeable the same way.

**The comment names every value that fell**, with what it is and what it was, instead of one
sentence about one number. Ten gated values need a list or the report cannot be acted on without
opening the job log; `enforce` prints the same list to stderr.

Everything the previous decision settled still holds: the baseline is still the last `main` run
restored from an Actions cache, the drift objection still dissolves against
`strict_required_status_checks_policy`, `render` and `enforce` are still separate steps with the
comment between them, the gate still does not run on a `main` push, and a cache miss still skips
it silently. `test_coverage.py` grew from 22 cases to 43 and still runs before the measurement in
the job it gates.

## Every sheet in the app is one component, because two of them were not (2026-08-12, 0.7.1)

The dispatch sheet shipped at 0.7.0 as a `Column` at the bottom of `GalaxyPage`'s own `Box`, with a
scrim it drew, a shape it drew and a grabber it drew. Davide's report off TestFlight, in one
sentence: it opens *above* the tab bar, it cannot be swiped, and a scroll on it scrolls the screen
underneath — *"Why does it differ from the BS we have in Colony and Research?"*

**Because nothing made it the same.** `RowSheet` and `DebugSheet` were both real
`ModalBottomSheet`s, and the four lines of configuration that make one — dismiss callback, skip
the partially-expanded state, the surface colour, the drag handle — were copied between them. A
feature writing its third sheet had a pattern to imitate and nothing to reuse, and imitation gets
the drawing right and the behaviour wrong. All three faults are the same fault:

- a panel inside the destination's slot stops where the destination stops, which is above the tab
  bar rather than over it;
- a panel with no pointer input of its own does not consume a drag, so the list behind it scrolls;
- a grabber is a rectangle unless something is listening, and nothing was.

The tap-outside worked, which is what made it look like a sheet with three bugs rather than a
thing that was never a sheet.

So `OltreBottomSheet` is now the only chrome in the app, in `:client:design:component`, and
`RowSheet`, `DebugSheet` and `DispatchSheet` are its three callers. It is a small file on purpose:
what it holds is the four lines, and its value is that there is nowhere else to put them. Note this
is the *second* time this exact panel was written — `DebugSheet` was one until 0.2.6 — which is the
argument for a component rather than for a better comment.

**One thing changed for the other two sheets**: `sheetMaxWidth` is `OltreLayout.maxContentWidth`
(560dp) rather than Material's 640dp default, so a sheet on an iPad is the width of the column it
was raised from. The dispatch sheet already capped itself there; the row sheet never had.

### What the tests now say, and where they say it

- `DispatchSheetContent` is split out on the shape `RowSheetContent` and `DebugSheetContent` have,
  so a test *can* render the contents alone — but nothing here does, and that is deliberate. The
  fifteen behaviour assertions and the five baselines drive the real screen, because the coverage
  table gates each kind separately now and a test that stops composing `GalaxyPage` stops covering
  it. The split earns its keep the moment an assertion needs to be about wording rather than about
  the screen.
- **A baseline of a sheet is a baseline of the second root.** A popup is a root of its own, so
  `onRoot` finds two and refuses to choose — which is the fix stating itself, since 0.7.0's panel
  was part of the page and that is exactly what broke it. `captureSheet` composes the whole page and
  photographs the root holding the sheet tag, so the five frames now carry the scrim, the page dimmed
  behind it and the real drag handle. They are better pictures than the ones they replace.
- **The regression test is a drag, not a screenshot.** `a drag on the sheet leaves the screen behind
  it where it was` hoists the page's `ScrollState` into the harness, swipes up on the sheet and
  asserts the page did not move. It fails against 0.7.0's panel. The other two faults — the tab bar
  and the handle — are a scaffold and a gesture that desktop cannot see, so this is the one of the
  three a test can hold.
- The five `galaxy_dispatch*` baselines were pictures of the whole page with a panel on it; they are
  now pictures of the contents, and the scrim and shape they recorded are gone with the panel.
- **The gate found a real gap, which is the argument for it.** `DispatchUiState` — three resolved
  defaults, a hull count clamped to the idle pool, a ladder that narrows with distance — had *no*
  unit coverage at all: every one of those claims was asserted through a popup and nowhere else.
  `DispatchUiStateTest` is twelve tests against the real generated galaxy, and it is 0.7.0's debt
  rather than 0.7.1's. Two behaviour tests join it on the stateful `GalaxyScreen`, which nothing
  drove: a tap raises the sheet, and what leaves is the *rendered* offer rather than the selection.
- **`SHEET_SCRIM` is gone.** The scrim is Material's now, and the dismissal test that tapped it went
  with it: what dismissal does in this app is `onDismiss` from one component, and the platform's
  scrim is not ours to assert. What that test also claimed — that touching a control commits
  nothing — is `sending commits the run and nothing else does`, which is unchanged.

## The Shipyard sells hulls and takes back the rate that was raised for having none (2026-08-12, 0.8.0)

Two decisions in one slice, and the second one is only defensible because of the first.

### `buildShips` charges and delivers in the same call

No yard job, and it was proposed as one twice. A fifth job kind would have added a term to
`Advance`'s completion union, a member to `FutureEvent`, a slot to the tie-break ladder and an id to
the notification budget — all bought to put **a second wait in front of the wait the mechanic is
actually about**. What a timer would have been protecting is already protected by the price:
`shipCost` compounds ×1.5 against a linear return, which is how every ceiling in this game is proved.
The probe's own philosophy transfers unchanged — *the wait a hull costs you is the flight, not the
yard* — and a check-in has to be able to buy and dispatch inside five minutes.

It still appends `Event.ShipsBuilt`, and that is not decoration: `GameSession` detects a discrete
transition by `eventLog.size` changing, so a verb that changed state without appending would write no
save and re-sync no notifications, and the hull would vanish on the next launch.

**`BuildShipsResult.NotForSale` exists because `shipCost` raises.** The balance object refuses to
price the other three hulls on purpose — *"a plausible number invented here would be
indistinguishable, to every later reader, from one somebody chose"* — and a verb reachable from a
finger may not throw. So the price's refusal is carried back as a result, which is exactly what the
Shipyard's dimmed Hauler card means on screen.

### `OltreTab.pendingWork` and `UnbuiltTabScreen` are deleted rather than nulled

Every tab carried a nullable string saying what would be there one day, and a real screen drew it —
deliberately real, because *"an empty black rectangle reads as a bug in the game rather than as a gap
in it"*. Shipyard and Fleets were the last two holding one. Left in place the column would be five
nulls, and a field that can only ever say "no" is a field rather than a table.

If a sixth destination ever arrives ahead of its screen, **the honest empty state should come back as
that tab's own, in that tab's module.** What made the old one shell-shaped was that two tabs shared
one drawing, and that is the condition rather than the pattern.

### The rate stays at 60, and round 17's guardrail is spent rather than met

0.7.2 tripled `EXTRACTION_PER_HOUR` on Davide's *"Just adjust the rate, but I don't think a 20% is
enough!"* The argument that made tripling safe was that **round 17's guardrail could not be tripped
by any shipped player** — "a fleet-first player must not out-produce their own colony" was measured
against a bot owning six to nine hulls, and there was no way to buy a second. That argument had an
expiry date written into it, in `FleetBalance` and in the sheet: *"this must be re-swept the day
`buildShips` lands."*

It landed. The sweep says, crystal-seeking over 48 hours, hulls bought before the buildings:

| rate | hulls from what is left | **hulls first** |
|---|---|---|
| 20 | 31.4% | **89.3%** |
| 30 | 47.2% | **134.0%** |
| 40 | 63.0% | **178.7%** |
| **60** | 94.5% | **268.1%** |

The build took the rate back to 20 on the strength of that and was overruled: *"Why did you revert
the rate? Bring it back."*

**So the decision recorded here is not about the number, it is about the criterion.** The 268% is a
measurement and nobody has disputed it; what has been rejected is round 17's rule that it violates.
*"The fleet must never be the economy"* stops being the constraint that sizes this constant. A future
round that wants the rate lower has to make that case against Davide's bar rather than reinstate
round 17's by default — and a round that finds the rate too high should expect to reach for the
**hull curve** first, since that is the term that decides how many hulls the 268% is spread across.

**The argument the build made and lost is kept, because the next session will find it too.** Hull
count is a growth term the fleet never had, and this slice is what adds it: at rate 20 the same
four-a-day player owns six hulls at 48 hours and sixteen at a fortnight, so a dispatch brings home six
to sixteen times the *"14 cristals"* without the constant moving. The build read the 0.7.2 raise as
compensation for a multiplier that was missing and has now arrived. Davide's call is that both are
wanted. **What settles it is an install, not another sweep**: the 268% assumes a player who buys hulls
before buildings at every check-in, and if nobody plays that way the honest reading is 94.5%.

### And the harness had been dead since the round that raised it

`:sim:run` carries a replica of `FleetBalance.cargo` and `check`s it against `core` on every dispatch
of every sweep row. Round 21 inverted the danger term in `core` and left the replica subtracting, so
the check fired the first time the bot chose a target with a hazard or outside the home system —
immediately — and the whole report died on it. **The discipline worked exactly as designed and then
nobody ran it.** Four sweep tables had also been printing candidate grids that did not contain the
shipped rate ever since. Both are fixed, and the candidate list now `check`s that it contains
`FleetBalance.EXTRACTION_PER_HOUR`.

The rule this leaves behind: **a balance round that ships without running the harness is how a broken
harness survives a release.**

## The hull price is flat, and the yard is the ceiling (2026-08-14, 0.10.1)

Davide, having played 0.10.0: *"Why is skiff pricing increasing at every buy? This is wrong."*
Offered flatten-the-exponent, flat, keep-×1.5-and-lower-the-base, or leave it, he took **flat** — with
the consequence named in the option he chose: it deletes the game's only bound on fleet size.

`FleetBalance.shipCost(SKIFF)` is 800 metal / 200 crystal at every depth. The `alreadyOwned` parameter
is gone from `shipCost` and from `buildDuration` rather than kept and ignored, because a live
parameter is how a curve comes back without a decision being taken. `GameState.committedShips()` is
deleted with it: pricing was the only thing that read it, and its whole justification was *"without
the yard term a queue would be a way round the compounding price."*

### The wait went flat too, and that was not a separate call

`buildDuration` is four minutes per root of the hull's own price — the colony's rule, so a hull and a
facility that cost the same take the same time. A flat price therefore makes a flat wait: every skiff
is **2h 04m** at Robotics 0, where the root of a ×1.5 curve grew at ×1.2247 a hull. Giving the yard a
curve of its own would have been inventing a design number to replace one that had just been
withdrawn. Two consequences worth knowing: the serial queue in `buildShips` is now the only thing that
makes buying four hulls different from buying one, and `MINIMUM_YARD_DURATION` stops being unreachable
— Robotics 25 divides 124 minutes to five, and every level past that buys nothing.

### What it cost, measured rather than feared

`balance-log.md` round 25 has the sweep. The short version is that the failure mode is not the one the
curve was written against: the greedy bot reaches **300 hulls in a fortnight** against ten, and they
deliver **2%** of the colony's metal, because 0.10.0's finite deposits mean there is nothing for the
291st hull to lift. The fleet does not become the economy — it becomes a **metal sink**, and the
colony pays in levels (66 against 76 at a fortnight; 19 against 34 at 48 hours for a fleet-first
player). §4's own guardrail — *"building levels at 48h: falls → the hull price is eating the colony"*
— is the one that trips.

That is recorded rather than argued: the decision was taken with the trade named, and **a device
session is what says whether any player wants to buy three hundred hulls.** If it needs a ceiling
again, the lever is the yard or the base, not a restored curve.

### One string changed on a screen

The Shipyard's footnote opened with *"The next hull costs half again as much as the last"*, which was
the curve stated to the player. It now names what actually bounds a fleet — *"Every hull costs the
same, and the yard builds one at a time"* — and all seven shipyard baselines were re-recorded for it.
PLACEHOLDER copy like every string in the app; content is Davide's.

### A cost function's parameters name what is bought, never how it is priced

Davide, on reviewing the diff above: *"Why are you touching so many files to change the ships price…
it makes me question the architecture."* The layering was not what hurt — no build file moved, no
dependency changed, `core` stayed pure. The defect was one level down, in what `core` exposed.

`FleetBalance.shipCost(type, alreadyOwned)` published an **ingredient of the pricing rule** as a
parameter. Every caller therefore had to know that a hull's price depends on the fleet, and had to
derive that fleet the same way `buildShips` did — so `ShipyardUiState` carried a second
implementation of the rule, kept in agreement by a comment (*"a card that priced the next hull off the
fleet would offer a rung the verb will not sell"*) and by a behaviour test whose only job was to check
the two copies matched. A test that chaperones duplication is duplication.

Contrast `PlaceholderBalance.upgradeCost(building, toLevel)`, which would survive the identical change
untouched: `toLevel` is a **fact about the thing being bought**, stable under any pricing rule.
`alreadyOwned` was a fact about how the thing was priced, and a parameter like that outlives the rule
that justified it by exactly one release.

So `priceOf` is public and takes the state:

```kotlin
fun GameState.priceOf(ships: Ships): Resources
```

The receiver is unused at a flat price, which is the point — a caller passes what it already holds
rather than an ingredient, so a price that starts reading the fleet again, or the research, or a yard
technology, changes one function and reaches no screen. The idiom already existed in `Affordability.kt`
(`shortfallOf`, `timeUntilAffordable`); the Shipyard simply had not used it for price.

**What this would have saved, concretely**: the flat-price change touched `BuildShips.kt`,
`ShipyardUiState.kt`, the sim's call sites and four test files because of the parameter. With
`priceOf` in place it is `FleetBalance` plus one function body, and `client/` never moves.

Two things this does *not* indict. The sim's replica of every curve is a deliberate documented trade —
it sweeps candidate constants `core` cannot produce, and its `check` caught a real bug at round 24.
And balance tests pinning `800/1200/1800/2700` rung by rung is those tests doing their job.

### The coverage passes have to wipe Kover's state between them (2026-08-14, 0.10.1)

The gate blocked PR #65 on a number that had nothing to do with the branch: Screenshot line read
**83.6%** on one run and **84.6%** on a re-run of the *same commit*, against a `main` baseline of
85.6%. Davide: *"Why is it unstable and reports different percentages? It always been very stable
till now."*

**The per-category report filters are per category; Kover's per-module artifacts are not.** The root
`build.gradle.kts` drops composables from the unit pass and drops `core`, `*.presentation`,
`*.domain` and `*.data` from the screenshot pass, and those exclusions are what make the two rows
measure what they claim to. But `-Poltre.testCategory` is not an input to `koverGenerateArtifact`, so
an artifact built in an earlier pass is UP-TO-DATE in a later one and is reused **with the earlier
pass's filters baked in**. `measure-coverage.sh` runs the unfiltered `all` pass first, so what it
leaves on disk is exactly the artifact the filtered passes must not read.

Nothing fails when this happens. The report is produced, it looks plausible, and `core`'s 1,611
lines are quietly back in the screenshot denominator — 61% instead of 85.6%, with no indication
which of the two you are looking at. Whether it bites depends on incidental up-to-dateness, which is
why it differs between a fresh CI workspace and a warm local one, and between two runs of one commit.

The fix is one line in `measure-coverage.sh`: delete `**/build/kover` before each pass, so every
pass rebuilds its artifacts under its own filters. Verified by running the whole script twice and
reading all ten numbers, which now reproduce `main` exactly.

**Why it surfaced now.** #64 split every feature into `ui` + `presentation` and added the screenshot
pass's layer exclusions in the same release. Before that the filters barely moved the number, so
reusing a stale artifact cost a rounding error; after it, the same reuse is worth twenty-four points.

Two things worth keeping from how this was chased, because both were wrong turns taken confidently:

- **"It is flaky" is not a diagnosis.** Two CI runs disagreeing proved only that the input was not
  the code. The measurement that mattered was per-*task* state — `:core:koverGenerateArtifact
  UP-TO-DATE` in the screenshot pass — and it was in the log all along.
- **The build was read after the conclusion rather than before it.** This session proposed changing
  what a category *means* — counting the whole codebase, so screenshot would read 61% — and called
  85.6% an artifact. `build.gradle.kts` argues the exact opposite at length, and had done since #64:
  counting `core` in the screenshot pass measures *"what fraction of the repository is not
  drawable"*. The intended number was right; only the plumbing was broken.

## The map gains a name (0.11.0, 2026-08-14)

Davide, having played 0.10.1: *"I'm so unhappy with the map. It is huge, but terrible to navigate!
Finding a planet feels like searching a phone number on pagine gialle in the 90s."* And: *"the map
should gain 'an identity' … here it's just numbers."*

`galaxy-identity-sheet.md` is the design and `.claude/prompts/design-galaxy-identity.md` is the
Claude Design round trip that answered its visual half. What follows is only what is expensive to
reverse.

### The galaxy has geography now, and every existing map changed to get it

`starClassAt` hashed each system independently, so **nothing about any region of the map could be
learned** — not hidden by the UI, absent from the model. A galaxy is now ten contiguous regions of
25 systems, each with a temperament that biases its stars, and the ten are a **permutation of a
fixed multiset** (4 Deep, 2 Settled, 4 Burning) rather than ten independent draws — so the pooled
star mix is identical for every seed and every galaxy is promised one of each.

**Davide accepted the reroll**, and the migration keeps the seed and the home coordinate rather than
re-minting: the 0.5.1 doorstep guarantee exists to make the *opening* legible, and a player with a
fortnight behind them is not in the opening. Re-minting would have restored that guarantee by
deleting their surveys, at the exact moment surveys started being worth something.

The one thing that closed with it: `GalaxyBalance.starClass`'s *"ASSUMED, NOT DECIDED … equal
thirds"* is now decided, as a consequence of the multiset rather than as a number of its own.

### The Galaxy tab opens on the ledger, not the map

Claude Design's option (c), Davide's call. The argument is about what the tab is *for*: the map is
where you spend probes and the ledger is where you spend ships, and runs go out several times a day
where probes go once or twice — so the rarer errand was sitting in the commoner one's chair, and
reaching a world you already had a reading on cost four taps of paging.

The honest cost, stated by Design and accepted: a returning player's first sight of the tab is a
list, which is a weaker picture than a map. It is right at forty worlds and wrong at five, which is
why the genesis frame spends its empty half on the region rather than on an apology.

### The word `Unsurveyed` left the row

The design's one subtraction, and the one it most wanted argued with. An empty disc socket where
every surveyed row has a body is the state, stated where the state belongs — and it bought back a
colour, a ten-character reading and the row's whole right end on 98% of rows. `WorldVerdictUiState`
keeps the constant with a null word so the decision stays arguable rather than deleted.

### Pins are the only thing the slice writes to disk

Schema 12, a set inside `galaxy`. Names, epithets, portraits, regions and the ledger's own filters
and sort are all derived. **Filters and the sort deliberately do not persist** — Design's rule: *"a
filter that outlives the check-in that set it is a screen lying about what it holds."*

### The screenshot tests moved from `:client:galaxy:ui` to `:client:galaxy:presentation`

**This reverses the arrangement 0.9.1 accepted, and the reason is that its stated cost came due.**
`TestGalaxyUiState.kt` was three thousand lines of generator output, hand-stated because a ui module
is a leaf that cannot see a `GameState` — and its own header named what that bought: *"the drift the
old header warned about is now real again … a mapper that re-words a verdict leaves this file
asserting the old text, and the baselines will agree with it."*

This redesign changed every row, every header and the whole body shape at once, so the file had to be
regenerated wholesale regardless. What replaced it is not a smaller copy but **no copy**: the
screenshot tests now live in the presentation module, which owns the same feature and *can* build a
`GameState`, so a frame is `state.toGalaxyUiState(nav)` — the same call the app makes. The
`screenshot-testing` skill asks for the owning client module's `desktopTest`, and this is one. The
module rules are untouched: no ui module gained a dependency.

What is lost is the screenshot sitting beside the composable it photographs. What is gained is that
a mapper which re-words anything moves a baseline, which is what a baseline is for.

### The discovery card costs the save nothing

The design asked for a card shown "once ever", which needs a seen-flag per world. What shipped is
*surveyed since you last had this tab open*, answered from `Event.SurveyCompleted` — which already
carries an instant and a system, and whose worlds are regenerable from the seed. The weaker
guarantee is the one that stores nothing and cannot fire twice.

**The boundary that span is measured from was wrong in the first cut, and the way it was wrong is
worth keeping.** `seenAt` opened at `now`, and `now` is `lastUpdatedAt` — the instant the launch had
already advanced *to* before anything was composed. So every `SurveyCompleted` the launch itself
produced was `at <= seenAt` and excluded: **the section could never fire on the check-in it exists
for**, which is the only one that matters. It worked only for a player who left the tab composed
across a later foreground, and a comment three lines above it claimed the opposite.

Nothing caught it. Both mapper tests passed, because they set `seenAt` themselves and were therefore
testing the filter rather than the boundary; the screenshot frame passed by dating its landing an
*hour in the future*, which shipped a baseline reading `found -59m ago` — the defect printing itself
into a committed image that had already been looked at. A behaviour test driving the real
`GalaxyScreen` is what found it.

The fix is that `GameSession` carries `resumedFrom` — the instant `resume` advanced *from* — and the
shell hands it down. The general lesson is the one `advance` has taught before: **a span has two
ends, and a screen that is about what happened while you were away cannot derive the far one from
the near one.**

## The drawn map moves the layout, not the metric (2026-08-15, issue #69)

Davide asked for "a real map" — the galaxy and the universe drawn, not indexed — and the first thing
that had to be established before Claude Design could be asked anything is that **the galaxy has no
geometry to draw**. A system is an index in 1…250. Both travel metrics read the difference of two
indices and nothing else: `SurveyBalance.distanceUnits` prices a probe hop at `|Δsystem|`, and
`FleetBalance.distanceUnits` prices a run at `95 + 5·|Δsystem|`.

So a drawn galaxy is a choice between two things, and they are not symmetrical:

- **The layout agrees with the metric.** Any shape whose path order is the index order — spiral, arc,
  ribbon, serpentine — so that "looks near" and "is near" agree. Costs Design freedom of layout.
- **The metric agrees with the layout.** A free 2-D scatter, distance re-derived from drawn position.
  Costs a re-quote of every new dispatch, the reach ruler's hour marks, and the leg split of runs
  already in flight — `Run.inboundBeginsAt` recomputes `FleetBalance.flight`, so a run dispatched
  before the change would report a different outbound/inbound boundary after it. Stored arrival
  instants survive (`Run.returnsAt` and `SurveyJob.completesAt` are both persisted), so nothing in
  flight lands at a different time; what moves is every number the screens quote about it.

**Davide's call: the layout moves.** The rejected alternative is worth keeping in view because its
price is a balance round rather than a bug — it would not break anything, it would re-open numbers
that three rounds of the balance log have already settled.

Two things fall out of the call that are worth having said in advance rather than discovered. A
region is a contiguous run of 25 indices, so on any index-monotone layout a region is automatically a
connected stretch — regions become places for free rather than needing a boundary algorithm. And the
hour ruler the reach strip already draws is a monotone function of position along the path, so
distance-from-home can become a field on the map instead of a separate instrument.

### What a galaxy draw costs, measured

Taken on this machine against `:core:jvmTest`, one galaxy, per pass, with a throwaway harness that
was deleted in the same session — a wall-clock reading has no business in the suite:

| pass | cost |
|---|---|
| star class for all 250 systems | 55 µs |
| generated name for all 250 systems | 144 µs |
| every world in the galaxy — 250 × 15 `worldAt` | 777 µs |

The reading that matters is the ratio rather than the absolutes, which are desktop JVM and will be
larger on a phone: **the entire charted tier is free to redraw every frame, and "how many worlds does
this system hold" is fourteen times the cost of it.** Which is convenient, because the charted tier
is also exactly what the knowledge tiers permit a galaxy view to show. A system glyph carrying star
class, region and name needs no cache at all; one carrying a world count needs one, and the design
should say which it wants rather than have the build guess.

## The galaxy is a folded ribbon, and the tab lands on it (2026-08-15, 0.12.0)

Claude Design's *Looks Near Is Near*, built as drawn. The full record of every call is in
[`drawn-map-sheet.md`](drawn-map-sheet.md); what follows is the handful that would be expensive to
reverse, each with the alternative it beat.

### Ten bands, and each one is a region

The fold is the only layout tried that satisfies both constraints at once: path order is index order,
*and* a region gets a straight run long enough to carry its name. Three others were drawn and
rejected — a free 2-D scatter (needs the metric to move, already declined), an Archimedean spiral
(index-honest and the best-looking, but a region becomes a curved arc with no straight run and the
inner turns are 40dp at 393dp), and ten cells in a ring (plenty of room for labels, and it *closes* —
system 250 sits against system 1, four hours and thirty-nine minutes apart, at the one place the eye
insists they are neighbours).

The fold's own cost is stated rather than hidden: two stars stacked across a band gap are 25 systems
apart and drawn 22dp apart, where 25 systems horizontally is 337dp. A fifteenfold understatement in
one direction, bought back by drawing the turn, alternating the labels and putting vertical
neighbours in different named regions. **If a device says it reads as a grid, the lever is the band
gap, not the shape.**

### `GenerationAxis.LAYOUT`, and why the drawing generates anything at all

An even pitch on a straight line reads as a table, so a band needs a perpendicular drift — and every
player on one seed has to be looking at the same sky, which makes it generation rather than
rendering. One new tag, appended after `RING`, carrying three draws: drift (±half a pitch, capped so
it can never reorder two stars), a size wobble inside the class band, and which halo a bright star
wears. The whole pre-existing suite passing unchanged is the evidence that adding the tag was free,
which is the enum's own standing rule.

### The map reads `galaxy.surveyed`, never `hasSurveyed`

The obvious call for "have I been here" is `GalaxyState.hasSurveyed(system)`, and it is wrong here in
two ways at once. It walks fifteen slots per system, which turns a 55 µs draw into a 777 µs one; and
it is **vacuously true for a system with no worlds**, so a map built on it would ring hundreds of
places nobody has ever sent a probe to. Membership of `galaxy.surveyed` is cheaper by a factor of
fourteen and is the honest reading: a system has a ring when you know a world in it. Pinned by a test
naming an empty system.

### The tab lands on the map, and remembers which list you last used

Claude Design overruled its own 0.11.0 call — *"that argument was against a dot field and it still
defeats one; it does not survive a map that is named, banded, and prints the places you marked"* —
and Davide took it with one amendment: the tab should thereafter open on whichever of the two lists
he last used.

**That amendment knowingly breaks Design's own rule that nothing this tab remembers reaches the
save.** It is one field, and it lives in a `preferences.json` beside the colony rather than in it:
a preference must never be able to cost somebody a colony, and separate files mean a corrupt one of
either kind takes only its own down. `:client:save:data` stores the *name* of the landing as a
string, because the module rules forbid a `data` module from seeing the `presentation` one that owns
the enum — the composition root is the only place that knows what the name means, and an unknown
value degrades to the map, which is also what a first launch gets.

Only the mode switch writes it. Not the scale chip and not the push into a system: the universe is a
state of the map and the orbit page is somewhere you go *from* it, so neither is a place a tab could
land.

### The filters and the sort go, and the diagnosis is not the obvious one

Davide called them useless; the reason is one level below the controls. **The ledger's rows are
worlds and the outbound question is about systems** — a probe is aimed at a star — so the list could
not answer "where next" filtered, sorted or neither. Wrong unit, before you reach a control. What
stays is what a list does better than a drawing: finding a place you have already been, by name, and
keeping the ones you marked at the top. The list is nearest-first as a property rather than as one of
four choices.

The reach strip and the ten-row region index go with them. The strip's hour ruler becomes four
hairlines *on* the map, and its own figure was already printed one line above it in the astronomy
line — a duplicate 0.11.0 found and did not act on. The index existed because ten region names would
not fit on 393dp **of one dimension**; they fit trivially on ten bands, so the measurement that
justified it stands and its conclusion has expired.

### The map paints its own ground rather than switching the starfield off

Decorative stars and real ones cannot share a screen, and at 320dp the shell's third parallax plane
reads as extra dim systems. But `Starfield` is drawn by `MainScaffold` *inside* the destination box,
under every screen, and a feature cannot reach up and turn it off. Hoisting a flag through
`MainScaffold` for one substate of one tab was rejected in favour of the map painting one opaque rect
over it. The worlds list keeps the sky.

## Technologies and adaptations get a queue each (2026-08-15, 0.12.2)

Davide, on having played it: *"Technologies and Adaptations run on the same queue, making the game
too slow. I want to have a queue each."*

This **overrules `adaptation-sheet.md` §2**, which had settled the opposite at 0.3 and argued it at
length. The sheet keeps the argument in full with the ruling above it, because a decision sheet that
quietly rewrites itself loses the reason the call was ever close.

### What changed, in the model

`activeResearch` and `activeAdaptation` were always two fields; what went is the `require` in
`GameState.init` that kept at most one of them set. Each branch is **still serial on its own** —
`startResearch` refuses on `activeResearch` alone, `startAdaptation` on `activeAdaptation` alone, and
both still answer `SlotBusy` with the word now meaning *this branch's slot*. Picking which project
and which ladder are two decisions; picking one no longer forecloses the other.

**No save version.** The two fields have been on disk since schema 5 and the rule about which of them
could be set was never written there — only `GameState.init` knew it, so relaxing it is invisible to
the format. A save carrying both now decodes as an ordinary colony; `GameSaveTest` keeps the same
hand-edited fixture and asserts the reversal rather than deleting the case.

### The one place the change could have gone silently wrong

`advance` picks the next boundary from a list of instants, and that list carried **one** term for
both branches (`researchSlotFreesAt`, which read the applied job first). Left alone, a ladder due
*earlier* than the project beside it would never be offered a boundary: `advance` would accrue
straight past it forever, the level would never land, nothing would be logged, and no existing test
would fail. The file already said so in as many words — *"a job kind missing from this expression
never completes"* — and the property is now two terms with a test that arranges the losing order on
purpose.

The tie-break between the two branches needed no change at all, which is the return on `Advance.kt`
having written it down while only one of them could ever be due: *"a tie-break that depends on which
case happens to be reachable is one a later slice breaks."* This was that slice, and nothing moved.

### `core`'s one checked-rather-than-unrepresentable rule about research is gone

§2 called the `require` *"the one place in `core` where a rule is checked rather than made
unrepresentable"* and booked a revisit for the day the Research screen rendered both branches. The
revisit never happened and no longer can: two independent slots have no invariant to state, so the
sealed `ActiveProject` that would have made it unrepresentable would now be actively wrong.

### The prices did not move, deliberately

Everything the adaptation sheet's §4 priced was priced against a trade — a ladder level cost you a
production level — that no longer exists. Davide's call was to land the rule and measure before
touching a number, so the two changes can be told apart. `balance-log.md` round 29 is that
measurement: 28 readings moved, the fortnight lost seven building levels and stopped reaching the
Nanite Factory, and income at day 14 rose by 10%. The dial, if one is wanted, is the adaptation base
cost.

### The screen says it, and one string changed

The ADAPTATION heading read `"the same slot"` for eleven versions — copy that existed specifically to
stop a player reading the two headings as one rule each. That reading is now correct, so the heading
carries its own rule in the same shape as TECHNOLOGIES (`"one ladder at a time"`, `"one at a time"`
compact). Placeholder copy like every other string on that screen: the shape is the decision, the
wording is Davide's.

## The dispatch sheet belongs to no tab (2026-08-16, issue #62)

**Two doors, one verb.** A run is raised from a world row on Galaxy, and issue #62 adds a second
door: a landing in the Fleets ledger, so the list of what came back becomes a way back to a world
that was worth going to. Features are meant not to see each other, so a sheet raised from both
cannot live in either.

Davide settled where it goes on 2026-08-13, in as many words: *"We absolutely do not put code in
shell! I'd suggest `client/dispatch/ui` with its UI state."* Two homes were refused with it, and
both are worth recording so they are not re-proposed:

- **`:client:shell`.** The 0.0.12 precedent — where the resource rail moved to the shell because
  Research needed what Colony owned — and the root build script still describes that as what the
  cross-feature rule is *for*. **That remedy is now closed.** The shell is the composition root and
  stays chrome.
- **`:client:design:component`.** It already depends on `core` for `ResourceKind` and already hosts
  `OltreBottomSheet`. But `toDispatchUiState` reads `GameState`, `worldAt`, `FleetBalance` and
  `DepositBalance` — much the deepest `core` edge the design system would carry, and that module's
  own comment defends its single edge as *"strictly less duplication"* than the alternative. A game
  verb's ui-state is a different argument entirely.

So `:client:dispatch:{ui,presentation}` — shaped like a feature directory, and the second thing in
the build that is shaped like one and is not.

### `featureOf` excludes it by name, and the test it passes is `design`'s

Every consumer is a cross-feature edge by construction, so the warning would fire on
`:client:galaxy:ui`, `:client:galaxy:presentation`, `:client:galaxy:ui-testing` and both Fleets
modules, on every clean build — which is exactly how `design` earned its own exclusion ("it made the
cross-feature warning fire nine times on a clean build, which is how a warning stops being read").

**What makes it safe is that nothing points out of it.** `:client:dispatch:*` reaches `core` and the
design system and no feature at all, so it cannot become the back door one tab reaches another
through. The two names are `sharedSurfaces` in the root build script now rather than one literal, and
a third has to demonstrate that property rather than inherit it.

### The mapper stopped knowing what a probe is

`toDispatchUiState` took a `ProbeActionUiState` — Galaxy's map-card footer — because the unsurveyed
refusal hands back the flight that would lift it. That type cannot follow the sheet: it is the
*footer's* model, and a sheet raised from a landing has no footer above it at all.

What replaces it is `DispatchProbeOffer`: three strings, resolved by the caller. Galaxy projects its
footer into one with `asDispatchProbeOffer()`; Fleets will pass null, and null is the ordinary case
there — a world a fleet has already been sent to was surveyed in order to be dispatched to, and
`surveyed` is never removed, so that refusal is unreachable from the ledger. **The pairing the
parameter exists for is unchanged**: the sheet offers a probe exactly when the card above it would
honour one, because it is handed that decision rather than making it again.

`DispatchSelection` needed no reshaping. #62 predicted it would — the mapper used to split a target
into a page and a slot — but 0.11 had already fixed that for the Galaxy ledger's own sake, and a
selection has carried a whole `GalaxyCoordinate` since.

### The tests and the nine baselines stayed in `:client:galaxy:presentation`

#62 says they move, and that instruction is older than the code. Two facts have changed under it:

- **A dispatch baseline is a picture of the sheet *over a page*.** `captureSheet` composes the whole
  screen and photographs the popup root, so the five frames this file already argued for "carry the
  scrim, the page dimmed behind it and the real drag handle". Re-recording them against a bare
  `Surface` would record a screen no device produces, to no end.
- **`DispatchUiStateTest`'s subject is the probe pairing above**, which needs `toProbeActionUiState`
  — Galaxy's. A copy of that in the dispatch module would be a second copy of exactly the decision
  the pairing keeps single.

Kover aggregates by class rather than by module, so the moved code's coverage is measured wherever
its tests live. **Nothing was re-recorded and no baseline moved by a byte**, which is the check that
this was a move rather than a change: the nine `galaxy_dispatch*` images verify unchanged against a
sheet that now lives in another module. The day Fleets raises the same sheet, its own frames join
Galaxy's rather than replacing them.

### What is left, and it is the half that needs a drawing

This lands the plumbing and nothing a player can see. The Fleets ledger becoming a door is blocked
on Claude Design: #62 asks for a row that reads as a target without spending the quiet the ledger was
designed to have, for what a row should say now that it is a door, for the 320dp frame that has no
Landed section at all, and for whether the list should group by world rather than by run. The sheet
opens at **its own defaults** and never pre-filled from the run that was tapped — Davide,
2026-08-13 — because pre-filling is "relaunch with last settings" arriving through a side door, and
`fleet-sheet.md` §8 rejects that by name.

## The landed ledger becomes a list of worlds (2026-08-16, 0.13.0, issue #62)

Davide asked for one thing — *"in the Fleet tabs, under Landed, I want to be able to tap a previous
dispatch… but we need Design to improve the list of items"* — and Claude Design answered with one
move that decides everything else: **the list stops being made of runs and starts being made of
worlds.**

Eleven runs are five worlds. Folded, a row can carry what a single landing never had — how many
times you went, what the world has paid in total, and whether there is anything left in the ground —
and the two rows that used to read `[3:165:8]` twice stop asking the player to do the folding. It
also answers the affordance problem for free: **a row with a face and a name is an object, and
objects open.** Nothing gained a border, a fill or an accent.

Three options were drawn and two rejected, which is worth keeping:

- **1a, a landing made a target.** The literal reading of the ask: same rows, a rule between them, a
  44dp height. Honest, and it cannot answer *which world was worth it* — two of the four rows are one
  world, the amounts are per-run so neither is the total, and there is nowhere to say the crystal is
  finished. *"It is a receipt you can tap, and a receipt is a poor index."*
- **1c, a world in the card idiom.** The most consistent answer and wrong here: it costs 33dp a world,
  five worlds stop fitting under three run cards at 393dp, and it makes history the same weight as a
  run in flight — so the eye stops telling *happening* from *happened*. The quiet was carrying that
  distinction.

### What the row says, and what it deliberately does not

Identity is the name and the disc. Beyond identity: the run count, the lifetime total in its
resource's hue, and the deposit reading. Rejected by Design, each for its own reason — the manifest
that went (a fact about what was idle that day, and the sheet will not pre-fill it anyway), the time
on station (a property of the window you picked), and richness and the round trip (both fixed for the
life of the world, both printed by the sheet one tap later).

**The landing clock is the only conditional element**, and it is the discovery card's own derivation:
present when the last landing falls inside the span this launch advanced, so no seen-flag and no new
stored state. It carries the verb — `landed 11:04` — because a bare clock in Oltre is a countdown.

### The five-event cap retired, and it was a data call after all

The old ledger showed the last five landings and called the cap *"a layout decision rather than a
data one"*. Design spent it: a roll-up is its own cap, so the limit had nothing left to do — and the
totals **have** to read the whole log or they are wrong. Design flagged this as the one premise to
check before building, and it holds: `eventLog` is a `List<Event>` in memory, so the fold is a
`groupBy` and nothing is paged.

### The sheet leads with a name now, on both tabs

The row leads with a name and the sheet led with a coordinate, so the tap landed on a
different-looking object. `DispatchUiState.coordinate` became `name`, and the address joined the head
line — *"the same fix the Galaxy tab needs on the same sheet"*.

**One thing Design's frames were right about and its prose was not.** The prose said "two strings
swapped"; the frames dropped the richness from an offer's head. Implementing the prose clipped the
hazard clause off the end of a 393dp sheet, which the first recorded baseline showed. The frames are
right for a reason worth writing down: the two gather cards already print `richness 1.15` and
`richness 1.47`, so the head was saying it twice. **A refusal keeps its richness**, because a refusal
has no cards and the richness is the reading a player is being refused for.

### `:client:world:ui`, and the rule that comes with it

A row is identified by the world's drawn face, and `WorldPortrait` was `internal` to
`:client:galaxy:ui`. Three homes were weighed; Davide took it, 2026-08-16:

> **"I think world:ui. Design system should not contain such full-ui components."**

So the portrait is a third shared surface beside `:client:design` and `:client:dispatch`, and it is
the first name added under the test the dispatch module set — *nothing points out of it*. The reason
it is **not** `:client:design:component`, which is where it was first proposed, generalises: a cost
chip or a section label is vocabulary; a procedural drawing of a planet from its temperature,
gravity, pressure and hazards is a feature's worth of decisions. **The test for a shared surface is
two questions, not one**: do two features use it, *and* is it vocabulary or is it a screen.

### Two defects the tests found, both worth the line

- **A deuterium landing crashed the row.** `depositCap` and `remaining` *throw* for deuterium rather
  than answering null — a world holds no deuterium deposit — and the row's resource is read off what
  landed, which `Event.FleetReturned` allows to be deuterium even though `startRun` does not. The
  reading is `empty`, which is true of ground that never held any.
- **Two Galaxy behaviour assertions were passing for the wrong reason.** `assertTheSheetReads("metal")`
  claimed to check the figure and was matching the head's `metal 1.15`; the figure said *crystal*,
  because that world is richer in it. Dropping the richness from the head is what exposed them. They
  read the richer resource off the generator now.

### What Design raised and this did not build

- **The same tap on an in-flight run card.** Today it would mean *send more ships where I am already
  working*, which is a refusal on a world you are standing on — one gesture with two meanings on one
  screen. It becomes a real decision when a hauler exists and a vein can be emptied by two manifests
  at once. Raised, not designed.
- **Auto-pinning a world your fleet has worked.** Four runs is a stronger signal than a tap, and it
  would make the galaxy map label the places you actually use with no new state and no new gesture.
  It changes the map rather than this list.

### The coverage gate found a duplicated verb (2026-08-16, 0.13.0)

The merge gate failed 0.13.0's first push on six numbers, and chasing them found one real defect
rather than a threshold to argue with.

**`App.kt` had `startRun`'s five refusals pasted twice** — once for the Galaxy tab and once for the
Fleets tab, twenty identical lines that no test in the repository reached. The comment written with
them claimed the duplication was deliberate (*"a shared lambda would put the two tabs' error handling
in a place neither of them owns"*), which was rationalising: it is **one verb with two doors**, and
the refusals are the same five either way. Hoisted to one `dispatchRun` lambda, which is both the
better code and the thing that recovered the line number.

The rest of the gap was covered rather than excluded, and every test added is one that should have
existed:

- The Fleets sheet's **stepper had never moved**. The first test clicked `+` on a sheet that opens on
  the whole idle pool, where `+` is already at its stop — so it asserted a control that did nothing.
  `sendOneFewer` is the end that moves from a default.
- **Two sheet states had no 320dp baseline at all**, though 320 is a baselined width: the waiting
  state's compact legs and danger lines, and the refusal, whose head is the only thing that has to
  fit.
- The shared mapper's **`probe = null` contract** — the state the Fleets tab is always in — was
  asserted nowhere.

**One arm stayed uncovered on purpose.** `FleetsUiState`'s run-card `when` has a deuterium branch
Kotlin needs for exhaustiveness and `core` makes unreachable: `FleetRun`'s constructor throws *"a run
never gathers deuterium"*. A test for it would assert against a colony that cannot exist. Written
down rather than excluded, because the next reader will find it and wonder.

## The dispatch sheet suggests a manifest (2026-08-17, 0.13.1)

Davide, on a screenshot of the sheet reading *"3 skiffs empty it. The other 52 bring nothing."* above
a stepper parked at 55:

> **"Going from 55 to 3 is a lot of taps 😅"**

Three changes, and the first is the one that matters.

### The default is the fleet that empties the vein, not the pool

`hulls = selection.ships ?: idle` had been the rule since the sheet landed, and the clamp note has
been telling the player it was wrong since 0.10 — *this many empty it, the rest bring nothing*. The
note was right and the default was the thing being explained. Now `FleetBalance.hullsToLift` answers
the same question the note asks and the sheet opens on the answer.

**It is a suggestion, never a cap.** `of 55 idle` still sits beside the label, `+` still reaches every
hull, and a deep vein still opens on the whole fleet — which is the same rule rather than an
exception, because there nothing is wasted. What the change deletes is the walk, not the choice.

**The note's disappearance is the point rather than a loss.** `clampNote` is earned rather than
standing: at the suggestion there are no wasted hulls to name, so it now appears only when the player
has deliberately asked for more. It stopped being furniture and became a live warning.

**`hullsToLift` is derived from `cargo`'s own expression**, for the reason `DepositBalance.workingTime`
states: `cargo(n)` is `floor(n × K)`, so *n hulls empty it* is one ceiling division, and a second copy
of the rate here would be a second rounding convention. Null is *no fleet size lifts this in this
window*, which only a window with no surface time reaches and the ladder never offers.

### A rung and a currency both re-derive it — Davide's call, asked as an option

He asked for the window; the currency was offered beside it and he took both. The argument is the
same on either axis: a longer stay means a smaller fleet takes the same vein, and the two deposits
are different sizes, so a count chosen against the old ask is arithmetic about a run that no longer
exists. The **stepper is the one control that keeps what it was given** — a number a thumb put there
is the one thing on the sheet nothing should overrule.

The rule lives in `homingIn` / `bringingBack` on `DispatchSelection` rather than in the two screens,
because Galaxy and Fleets both raise this sheet and rule 5 stops either seeing the other: a `copy`
written twice is two doors that can start disagreeing about what a tap means.

### And the stepper repeats while it is held

The suggestion makes the walk short in the common case; the hold is what stops the uncommon one being
fifty taps. Three things it has to get right, each a defect if it does not: the step reads the count
that is **on screen now** (`rememberUpdatedState`, or a hold asks for one number fifty times); a hold
**does not add a step on the release**, since `clickable` fires on the up whatever the press was; and
a **disabled stepper stays disabled**, because `enabled` is read inside the loop rather than captured.

**The four timings are invented and marked as such** — 350ms before it starts, 120ms ramping to 25ms —
under the motion-tuning precedent in `session-roles.md`: nobody knows how long a thumb should rest
before a control starts running until they are holding a phone. The ramp walks 55 down to 3 in about
two seconds, which is the trip that was counted. Expect them to move on the first install.

### What the frames had to say out loud

Two screenshot fixtures were leaving the manifest blank and relying on it resolving to the pool:
`dispatchWholeDepositUiState` would have become a picture of the plain offer, and
`dispatchWaitingForeverUiState` — the one frame whose subject is *"no world this size ever holds that
much"* — would have opened on a single skiff and offered a date. Both state their count now. The same
edit was needed in four mapper tests, and it is the same lesson `dispatchClampedUiState` recorded at
0.9: **a fixture that leaves a default blank is a fixture that stops being about its own subject the
day the default moves.**

### The coverage gate asked what tests the gesture, and the answer was a module (2026-08-17, 0.13.1)

The gate blocked 0.13.1's first push on three numbers — unit line 87.4 → 87.3, screenshot line
87.1 → 86.9, screenshot branch 50.9 → 50.8 — and chasing them found the same thing 0.13.0's round
did: a real structural fault rather than a threshold to argue with.

**Every failing number was the hold-to-repeat gesture**, and the diagnosis is the interesting part.
Measured line by line off the two failing passes:

| what | unit pass | screenshot pass |
|---|---|---|
| `hullsToLift` in `core` | covered by `FleetBalanceTest` | excluded (`core.**`) |
| the mapper's suggestion | covered by `DispatchUiStateTest` | excluded (`*.presentation`) |
| `homingIn` / `bringingBack` | **uncovered — no test existed** | excluded |
| four invented timing constants | **uncovered** | covered (static init) |
| the gesture body | excluded (inside a `@Composable`) | **uncovered, permanently** |

Two of those three are things that should have been done anyway, and one is a genuine limit.

**`homingIn` / `bringingBack` had no unit test at all.** They are pure functions holding the one rule
Galaxy and Fleets share, and they were asserted only through two Compose screens. `DispatchSelectionTest`
is what the gate asked for and what should have been written with them.

**The four timing constants became `:client:dispatch:domain`.** What a held stepper *does* is a
cadence — a rest, a first repeat, a ramp to a floor — and that is arithmetic. `StepperHold.waits()`
is a lazy sequence of the waits, and the gesture reads `wait, step, wait, step` with no arithmetic
of its own. The module earns itself the way `:client:tilt:domain` did: *"push the logic of a feature
down into a module it can test, and leave the Compose layer as thin as it will go."*

**And the first test written there failed.** The changelog claimed the ramp walked 55 hulls down to
3 in about two seconds; at `GAIN = 10ms` it bought 47 steps against the 52 that trip needs — a fifth
short of the thing the number was chosen for. `GAIN` is 15ms because a test said so. **That is the
whole argument for the module in one line: the claim was in a comment, and the comment was wrong.**
A motion constant a session invents has to be flagged as invented; this one was, and it was still
wrong in a way only arithmetic could catch. The device session will still move all four, and
`StepperHoldTest` pins the *shape* — a rest, a ramp that only accelerates, a floor — so a tuning
pass may move every constant and leave every assertion standing.

**What is left is a real limit, and it is Davide's call.** Fourteen lines and six branches of pointer
handling — `awaitFirstDown`, the repeat `launch`, `waitForUpOrCancellation`, the tap's suppression
check — sit in a `*.ui` package and **cannot be reached by a screenshot test by construction**: a
screenshot renders, it does not press. Three behaviour tests cover them and the behaviour row went
up. Measured on one machine against `origin/main`:

| | main | branch |
|---|---|---|
| unit line | 84.26% | **84.29%** |
| screenshot line | 84.14% | 83.92% |
| screenshot branch | 50.88% | 50.74% |

The unit row is recovered and better than it was. The two screenshot rows are the gesture and
nothing else, and no test of that kind can move them.

**Davide's call, put to him with those numbers: widen the screenshot filter.** The filter's own
principle — *"what survives is what draws"* — reads on a gesture handler exactly as it reads on a
mapper; what it lacked was a layer boundary to express it with. So the gesture became
`StepperGesture.kt`, one `Modifier` extension and nothing that emits, and the screenshot pass names
that file beside the mappers and `core`.

**The measurement is the argument that it is narrow enough.** With the entry in place the screenshot
pass reads `4139/4919` lines and `1067/2097` branches — *identical to `main` in every digit*. The
excluded file holds exactly the lines this branch added to that pass and not one line that existed
before it, which is what "narrow" has to mean if it is to mean anything. The unit row ends at 84.32%
against main's 84.26%, and the behaviour row — the one that actually proves the gesture works — went
up.

**Two things rejected on the way.** A `…ScreenshotTest` that presses and holds before it captures
would execute the lines, and is a test written to move a number rather than to assert a drawing. A
global `excludes` entry, beside `MainActivity` and `AndroidShakeDetector`, would hide the gesture
from *every* pass including the behaviour one that covers it — which is the property those three
entries have (no seam any test can reach) and this one does not.

**What the next entry has to do.** Not "is it small" and not "is it hard to test", but: *is there a
kind of test that could reach this, and does the file contain anything that kind could catch.* A
pointer handler in a file with no drawing in it answers no and no. A composable that emits anything
does not, and belongs back in `DispatchSheet.kt`.
## Every word the game says is a `TextRes` (2026-08-18, issue #86)

Davide's requirements, 2026-08-16, verbatim: *"I want them typed"*, *"no bare strings across UI,
till DS components"*, *"the super type is a sealed interface so we can create from Strings as well,
like a name from BE for example"*, *"args and quantities are compile-time safe: we can't pass a
String if a number is required"*. And the API, his: the type is **`TextRes`** (because Compose's
`Text` is in scope in every file that would use it), a bare string is `TextRes(value)`, and every
translated string is `Strings.<id>(args)`.

**`Strings` is the factory and `Translations` is the table**, and keeping them apart is the whole
design rather than a tidiness. A `presentation` module builds its text long before anything knows
which language will draw it — and `GameNotifications` builds text the OS will draw hours later with
nothing composing at all, which is why `stringResource()` was never a candidate: it is a
`@Composable`. So `:client:design:text` has no Compose in it, exactly as `:client:design:format` has
none, and the seam to rendering is one line — `LocalTranslations` in `:client:design:core`.

Four things fall out, and they are what the framework was bought for:

- **A new `StringId` fails to compile in every language until it is translated.** `Translations`
  resolves an exhaustive `when` with no `else`, which is `core`'s own discipline applied to copy.
- **`Strings.hullsInFleet("two")` does not compile.** `Message`'s constructor is `internal` (and its
  `copy` with it), so the signature written in `Strings` *is* the contract rather than a convention.
- **A test asserts on meaning.** `assertEquals(Strings.hullsInFleet(3), head.rule)` survives a
  rewording and fails on a rewrite; the words are pinned once, in `EnglishTest`.
- **Equality is the `String`'s**, so every ui-state comparison, fixture and frame kept working.

**Stage 3 came with stage 1, on Davide's call** — the ticket staged the grammar helpers for later
and the alternative was worse: `:client:design:format` returning `TextRes` while two hundred call
sites still interpolated it into a `String` is a defect the compiler cannot see, and it bit twice in
the test tree before it was hunted out. So the split is now clean: **that module decides *which*
numbers to show and `Translations` writes them down.** Rounding a duration up so a chip never reads
0m is the same in every language; the unit letters, the thousands separator and the decimal point
are not.

Two places where the decision moved rather than just the strings:

- **A width decision cannot be made before the language is known.** The astronomy line dropped its
  "from here" clause when the built string passed 54 characters — a measurement of *English*. The
  mapper now states both readings and `SystemHead` measures the one it is about to draw.
- **Case is not a transformation a language shares.** Labels that were literals in a composable and
  then `uppercase()`d are catalogued in the case they are drawn in. Where the source is a *generated*
  name — a star, a region — the composable still uppercases the resolved string, because a name from
  the seed has no translation to be wrong about.

**No baseline moved and no version was bumped**, which is the promise the ticket made: every English
string is the one that was already there, character for character.

**The coverage gate needed one exclusion and one frame, and both are Davide's call recorded here.**
The catalogue is 309 one-line `Strings` entries and a `when` with a branch per `StringId`, so left in
the screenshot and behaviour denominators it makes those rows measure *what fraction of the table a
set of frames happens to quote* — the defect the two existing pass-scoped exclusions were already
written about. `*.design.text` is excluded from those two passes and fully covered in the unit and
unfiltered ones, by `CatalogueTest`, which resolves every entry the catalogue can produce.

What that left was a uniform tax of about forty branches: **one Compose skipping branch per two
`.resolve()` call sites**, spread across every composable that draws text. No frame can reach those —
a screenshot renders once, so the skip side of the branch is never taken — and the first diagnosis of
them here was wrong, which is worth recording because it sent one round of work at the wrong target.
It was recovered instead by photographing a state that had no baseline: the discovery card's *full*
form, three labelled axes in a column, which a system holding one world produces and which the
existing frame (two or more discoveries, so the compact form) never drew.

**One finding to hand back rather than fix.** Three files said in as many words that a U+00A0 binds
a value to its unit so a wrapping line never leaves "atm" alone on one — and all three shipped an
ordinary space. Only the Galaxy screen's `NBSP` constant held the real character, and nothing
referenced it. `English.UNIT_GAP` reproduces the space deliberately: changing it changes what four
screens render, which is Davide's call and a two-character diff once he has made it.

---

## The drive buys reach and never the hold (2026-08-21, 0.15.0, issue #71)

`exploration-rewards-sheet.md` §2.3 specified one technology carrying **speed and hold together**,
and argued it in one sentence: *"it is the growth term the fleet has never had."* That sentence was
true when the sheet was written on 2026-08-12 and false by the time anybody built it.

**`Technology.PROSPECTING` shipped at 0.10.0 and is a hold multiplier.** So the sheet's premise had
expired, and the consequence is not cosmetic: two rows multiplying one term against the same ×1.5
cost curve is not a choice a player gets to make. At ×1.25 against Prospecting's ×1.10 the drive wins
from some level on and never loses again, which turns the older row into a trap — a thing on the
Research screen that is always wrong to buy and says nothing about being so.

**Davide's call: speed only.** The drive is reach, Prospecting is yield, and neither can delete the
other. §6.5's merge condition — *"a drive level must not be strictly better than both on arrival"* —
is met by measurement rather than by argument: at the adjacent galaxy a drive level is worth 2,182
metal against Prospecting's 927, and next door the drive is worth **nothing at all** while
Prospecting is worth the same everywhere.

**This is round 27's lesson for the third time in this repository**, and it is worth naming as a
pattern rather than as an incident: *a constant derived from a rule carries the rule's premise, and a
later round can invalidate the premise without touching the constant.* Round 27 found it in the
deposit cap, #83 found it in the probe's flat 150 metal, and it is here in a decision sheet's §2.3.
**A sheet is not a plan that stays true.** The check that catches it is cheap and was not run twice:
before implementing a section, re-read what it assumes and ask which of those things shipped since.

### The effect is linear, and that follows from the split rather than softening it

Every other row in the branch compounds, because each multiplies a **rate** — a stock's derivative,
against an exponential cost, where a linear effect is dead by level 4. `PROPULSION` divides a
**distance**: `unitsPerMinute` is the denominator of a flight, so 1.25 a level puts another galaxy
inside twenty minutes by level eight and deletes the map rather than opening it.

`1 + level` is also the calibration, which is the part to keep hold of. It makes level 1 exactly
double the base, so `UNITS_PER_MINUTE_BASE` can be **half** what 0.14 flew at and drive 1 lands on
0.14 to the minute. The first level does not make a player faster than they have ever been — it gives
back the game they had. That is what makes the technology read as an *unlock* rather than as a bonus,
and it is why the constant is 5 and not 4 or 6.

### The frontier is not reachable at drive 0, and that is the answer to §8.5

Two galaxy hops is 36h 20m out and back at drive 0, past the longest window there is, so `windowsFor`
returns an **empty list** and the far end of the map cannot be ordered at all. The sheet raised this
as an open call and worried that *"a new player's map is honestly smaller than it looks."* It is, and
that is the point: the ladder narrowing and then widening again is the whole teaching device, and it
needs no copy — a rung reappears on a world the player already knows.

Existing saves get **no free level** (Davide's call). A colony mid-play wakes to a fleet at half the
speed it had and meets the slice at a research row, which is *"navigating distance takes way more
time, without powered up ships"* delivered where it was asked for. Schema 13 writes the truthful zero;
the 7 → 8 hop's granted skiff stays the one gift in that table.

---

## A probe flies a hull, and the hull is a fifth ship type (2026-08-21, 0.15.0, issues #71 and #83)

Davide, 2026-08-16, having played 0.12.2: *"Surveying other systems seems way too easy. A small bunch
of metal, a few minutes of waiting, and you can even survey 10 systems from another galaxy for 1500
metal in total, in less than one hour. Exploring the world must feel rewarding, not just a tap
away."*

**The price was never what made it a tap, and this is the finding worth keeping.** `startSurvey`
capped nothing but one-probe-per-target: probes ran in parallel with each other and with everything
else, limited by metal alone, so ten dispatched in one check-in **all landed together** and the
marginal wall-clock cost of the tenth was zero. No value of `COST_METAL` changes that. A finite pool
of hulls does — the tenth probe waits for the first to come home — which is why the answer is a
*noun* and not a number.

### `SCOUT` is a `ShipType`, and it is the first one that is not a fleet asset

It carries no cargo, cannot be escorted and has exactly one verb, so every other consumer of that
enum — all of which assume a hull is something you dispatch on a gathering run — is wrong about it.
The alternative was a concept beside `Ships`: a second pool, a second price path, a second yard and a
second save field, to say one word. **Davide's call: a fifth `ShipType`.** It reuses the Shipyard, the
queue, the price, the idle pool and the save format for nothing, and what it costs is one guard —
`StartRunResult.NotAGatheringHull` — placed where the assumption actually lives.

That guard is a **whitelist and not `!= SCOUT`**, deliberately. The escort and the settler are coming
and exactly one of them will have a hold; a blacklist would send the other two gathering on the day
their constants land, silently and with a plausible number behind it.

### Spent for the flight, not consumed by it

`advance` hands the scout back at the landing, exactly as a run's hulls come home. So what surveying
costs is a hull's **absence**, and the scarcity is the wall clock rather than attrition — one scout
surveys the galaxy given time. Consumption was the alternative and it makes the yard a permanent tax
on exploration, which is the opposite of a verb whose job is to exist at hour zero.

### 200 metal / 50 crystal is an *opening* constant

A colony owns no hulls since 0.11.3, so this is the first thing it buys, and #83 flagged the danger
in as many words: a fleet-second player who cannot afford a hull would have had **no exploration of
any kind for two days**, on the tab 0.12.0 had just made the landing screen. 200 for the hull plus
150 for the flight, against a genesis stock of 500 metal, is what keeps that from happening — and
`StartSurveyTest` pins it as arithmetic rather than as a hope.

**It is a fifth type against Notion's "4 ship types", and that objection is withdrawn rather than
overlooked** — Davide, 2026-08-16: *"Notion stuff is now very ancient."*

### The footer says so before the tap

The Galaxy tab's probe footer reuses its `Unaffordable` treatment rather than earning a seventh
state: that shape is already *"here is the offer, here is why you cannot take it, and there is no
verb."* What differs is the **note**, and it differs in the way that matters — every other
unaffordable state in this game is answered by standing still, and this one is answered at the
Shipyard. So a scout genuinely on its way home gets a countdown, and no scout at all gets *"needs a
scout"*, because a countdown to nothing is a lie however well it renders.

---

## Twice the Flight: one stepper on berths, and a rung that dims rather than vanishes (2026-08-21, 0.15.0, issue #71)

Claude Design's round trip for slice 4's manifest picker, which `fleet-sheet.md` §10 had flagged as
an open frame since the slice was written. Its three rulings, and what each is actually for.

### The fact the shape rests on

**A manifest has one clock, and the slowest hull sets it.** A hauler's flight is `20 + 2u/U` against
a skiff's `10 + u/U`, so with two hull types there are only ever *two* answers however many hulls go.
That is what makes the picker one stepper and a two-cell row rather than two steppers: **the stepper
says how much hold, the cells say which clock carries it**, and both move one cursor along one
ordered list of reachable manifests.

Two steppers was the obvious shape and Design rejected it on two grounds worth keeping: it puts the
packing arithmetic on the player, and it invites the strictly-dominated manifest — a hauler with one
skiff flies at the hauler's clock and lifts less than a hauler with two.

**The design says "exactly double" and its own frames say 2.1× at the doorstep**, because the flat
base term doubles while the distance rounds away. Nothing turns on it; the shape needs *one* clock,
not a particular ratio. Pinned as a test so the next reader meets the frame rather than the sentence.

**Its figures are drive-1 figures.** It was drawn against the curve 0.14 shipped and 0.15 halves base
speed. The shape is untouched — the drive scales the distance term, the hull factor scales the whole
flight, so a level is worth the same *share* of a trip to either hull — and the picker's frames are
held at Propulsion 1 so they are the frames Design published rather than the same shape doubled.

### Absent means never; dim means not with these hulls

The shipped ladder teaches distance by absence, **and that lesson only survives if absence keeps one
cause.** So the two causes get two renderings, and the difference between them is exactly whether the
player can do anything about it: a rung no manifest can fly is not drawn, and a rung *this* manifest
cannot fly is drawn at 42% with the hull that would fly it underneath.

**It is not a disabled control — this app has none — it is the undo.** One tap takes the hauler out
and selects the rung. The alternative, refusing the tap or silently dropping the hauler back out,
makes the control the player just touched the one that did nothing.

Up is the only direction when a mix removes the selected rung, and that is arithmetic rather than a
preference: legality is monotone, so a window too short for a flight is too short for every shorter
window. **Body weight is the whole announcement** — no animation, no toast, no highlight, because the
app has none of those and a moved selection does not earn the first.

### The default is a berth count, not a hull count

The fewest berths that empty the vein at the rung already selected, packed hauler-first, with one
constraint binding it: **it may never lock the rung it is defaulting to.** Expressed by filtering the
candidates to the manifests that fly that rung rather than by re-checking afterwards.

What it gives up, in Design's own words: *"the default will put the hauler in the manifest on the
first open and take the short rungs with it."* Both costs are paid for by the rung being dimmed
rather than gone — which makes the default's cost visible and one tap away.

### What the round cost, and it is the finding

**Four dead controls in one release, all the same shape**: `core` grew a requirement and everything
deriving *"can I?"* from the old inputs went on saying yes. The Shipyard sold no scout; the map's
caption offered a probe with no hull; `:sim:run` stopped surveying and reported plausible numbers;
and both screens rebuilt the dispatch manifest as `Ships.of(SKIFF, count)` from a number that had
become a *berth* count — six berths is not six skiffs, so `startRun` would have refused a fleet the
colony does not own while the button appeared to do nothing.

**The verb refuses correctly in every one of them**, which is why nothing crashed and nothing was
caught. Three were found by reading a screenshot or a report; none by a failing test. The guard that
now exists for the first is a test holding the Shipyard's card list against `FleetBalance.FOR_SALE`;
the guard for the last is that the offer carries its manifest rather than a number a screen
re-interprets.

### Raised by Design, not decided

- **Two clocks in one header.** The system header's astronomy line quotes one round trip and there
  are now two. Design's cheapest fix is to drop the reach from the header, since the sheet one tap
  away prints both. Left alone; every such reading now names `FleetBalance.FASTEST_HULL` explicitly,
  so the day it is decided they are one grep.
- **A run card needs a mixed manifest.** `RunCard` prints `3 skiffs`; it will need `1 hauler · 2
  skiffs`, nineteen characters into a slot sharing a line with a coordinate and a countdown.
- **One number to check before this ships.** Design measures the hauler as worth 630 metal at exactly
  one rung on a doorstep world and nothing at four of the five. If `:sim:run` agrees, the hull pays
  only on deep veins far out, and the Shipyard's copy is where that has to be said.

## The ask goes on the flight, and the bell goes beside Dispatch (2026-08-22, 0.15.4)

Davide, in as many words: *"I wanna set opt-in notifications also for flights, let's show usual bell
notification besides Dispatch button."* A fleet return and a probe landing were the last two alerts in
the game that fired whether or not anybody wanted them; from this version every alert Oltre raises is
one that was asked for.

**The reversal is the interesting part.** `GameNotificationsTest` carried a test called *"a probe
still lands without being asked about"*, on the argument that only *completions* went opt-in at 0.5.0
because a probe is not something you wait on a row for. The second half of that was true and the
conclusion drawn from it was backwards: what it described was the **absence of a control**, and the
answer to that is to add one rather than to keep announcing unasked. The same mistake had already
been made once and corrected once — 0.15.3's note about a delivery being *"exempt because there was
no control on a hull card to ask it with"* is the same sentence, one release earlier.

### Three calls, all Davide's

**The ask lives on the run, not in a standing preference.** `FleetRun.announced` and
`SurveyJob.announced`, stamped by `startRun` and `startSurvey` at the instant the verb is tapped.
That is the rule `cargo` and `returnsAt` already follow, for the reason they follow it: all three are
the promise the sheet made *before* the tap, and a promise a later sheet could withdraw would not be
one. So a player who lights the bell for one world and unlights it for the next is asking two
different questions, and the first answer survives the second.

**The bell remembers.** `GameState.announceFlights` is the *position of the control*, not the ask —
nothing empties it, because there is no job for it to be about, which is what makes it the odd one
out beside `subscribed` and `hullAlerts`. Both verbs copy it rather than taking it as a parameter,
and the absence of that parameter is the point: there is no argument a caller could pass that
disagrees with the bell the player was looking at.

**Dispatch is the only ask.** There is deliberately no square on a run card, so a flight in the air
cannot be re-asked about. That is the one irreversible ask in the game and it is defensible for the
same reason the run itself is: nothing about a flight is changeable once it has left.

One flag for both verbs rather than two. A probe landing and a fleet coming home are different news;
the *question* is the same one, and two memories for one word would be two settings a player has to
find separately. Overrulable the day the two want different answers.

### Where the two bells sit

Both are `WatchSquare` at its committed 29dp — no new component, no new visual language. The sheet's
verb is full-width, so the square is the trailing control beside it; the map card's footer already
ends in its verb, so the square goes to its **left** rather than pushing the one accent thing in the
card off the edge it is aligned to. Both keep the square adjacent to the verb it is about.

`stacked = true` at both sites, which is not about layout: it asks `WatchSquare` for a 29dp-tall
target rather than a 44dp one, and beside a ~38dp verb the taller hit box would overhang the row it
sits in — where Compose does not reliably deliver touch, so the extra pixels would be dead rather
than generous. **These metrics are the session's and not a frame's**; there was no Claude Design
round for this, because Davide named the control and its position himself and both are the system's
existing ones.

### Schema 15, and it is behavioural

`runs[].announced`, `surveys[].announced` and `announceFlights` in one hop — schema 9's precedent,
since no save can hold one without the others. **`false` everywhere, including on flights already in
the air**, which is the change rather than a cost of it: carrying an existing run forward as `true`
would invent an ask nobody made, and — because there is no control on a run card — one the player
could not take back. Nothing the colony holds moves.

The gate reads the *event* rather than the colony's flag, and that is the whole of the per-flight
promise: a gate consulting `announceFlights` would announce a run the player had already decided
against and silence one they had asked for, both retroactively.

## The frame gains a fourth tier: who is playing (0.16.0)

Davide's ask, 2026-08-22, and the design and the implementation are in
[`player-strip-sheet.md`](player-strip-sheet.md) rather than restated here. Four things from it are
decisions rather than drawings, and belong in this file:

**"Coming soon" is the copy, over the design system's own rule.** The Claude Design project lists it
under *Never written* beside "Under construction" and "Oops", and the app's idiom for an unbuilt
thing is a flat declarative sentence in the room where it would be. Put to Davide with
`Settings land here.` beside it; he took the literal reading. So the string ships and the design
system is what gets amended — recorded because the rule is still written down and the next session
will read it and hesitate.

**Nothing went into `core`.** Nothing awards experience and nothing renames the player, so a stored
field could only ever hold the value it was migrated in with — and the 14→15 hop would have had to
answer what an existing colony's experience is, which is neither zero nor a number worth inventing.
The name, the level and the gauge are constants in `:client:player:ui`.

**`:client:player:ui` and no `presentation`**, on `:client:debug`'s precedent — a feature with
nothing to decide has no presentation at all. There is no `GameState` to map, and a module that only
forwarded its arguments is the placeholder layer the rule exists to prevent. The slice that makes the
numbers real adds the layer then.

**`DESTINATION_HEIGHT` went 650 → 612 in the same commit**, because the strip is 38dp of chrome above
the rail and that constant is the only thing telling the suite what a destination actually gets.
0.12.0 shipped a map whose only control was off the bottom of the screen while every galaxy frame
stayed green, for exactly this reason. Eight galaxy baselines and `main_scaffold` were re-recorded.

### Raised and not decided

- **The gear has nothing behind it.** It answers, and that is all it does. What a settings screen
  holds is the follow-up task's question — and the language is not in it (see `TranslationsFor`).
- **The mark is not seeded.** One drawing for every save, deliberately: a mark that varied with the
  galaxy seed would assert an identity the save cannot back. The day identity earns variation it
  should be drawn for it rather than derived from a number nobody chose.

## The level is inferred once from the log, then stored (0.17.0)

Davide's ask, 2026-08-22, and the design is in [`experience-sheet.md`](experience-sheet.md). Six
things from it are decisions rather than numbers.

**The first cut folded the log on every read, and Davide overruled it**, 2026-08-23: *"this is bad,
because the more the player progresses, the more it will be intensive to infer the level! Let's infer
it for players that are coming from a previous version, then store it, and update it as it
progresses."*

Recorded because the rejected argument was not obviously wrong and the next session will reconstruct
it. The fold is a sum over a few hundred entries a month, so it is cheap *today*; what makes it the
wrong shape is that its cost is **unbounded in the one direction that matters** — free on the day it
ships, paid by the players who play the most, arriving months later, on a reading the chrome
recomputes above every screen. That cost appears in no review and no test. **Prefer a stored total to
a fold whenever the thing being folded is an append-only history.**

So: **infer once, store, maintain.** The 15 → 16 migration folds the save's own log into an opening
balance; `GameState.experience` carries it from there; `GameState.logging` is the only thing in
`core` that may append to `eventLog` and it adds the award in the same `copy`. The fold survives as
what the migration calls and as what `ExperienceTest` checks the field against — the expensive answer
is the specification, the cheap one ships.

**The hop computes where every other hop declares, and that is the interesting part.** Fifteen
migrations answer *what did a colony that predates this feature have?* with a truthful zero, because
it genuinely had none of the thing. A colony that predates *this* one had been earning since genesis
with nowhere to write the number down — so zero would confiscate a fortnight of play, and the honest
answer is what its own log is worth. That is exactly the question 0.16's entry above said had no
honest answer, and it was right at the time: the fold that answers it did not exist yet.

**The invariant has no compiler behind it and is held by construction.** `experience` must always
equal `experienceOf(eventLog)`. A `require` in `GameState.init` would fold on every construction
including every decode, reintroducing the cost this design removes — so instead there is exactly one
append site, and `ExperienceTest` drives a colony through every verb and every kind of completion
asserting the two agree at each step.

**A completion pays, a start does not** — Davide's call, over "on the tap" and over a split. It is
also what belongs to this game: everything here happens while the app is closed, so a bar that only
moved under a finger would be the one reading on screen with nothing to do with being away. Six of
the twelve `Event` members are worth nothing and `awardFor` names all six — there is no `else`, so a
thirteenth event has to be priced by whoever adds it rather than defaulting to zero unnoticed.

**A hull pays per hull and small** — Davide's call, over one-award-per-order. The smallness is the
measurement rather than a preference: hull purchases come out of income and income compounds, so the
sim's thirty-day player owns 1,721 skiffs against 79 finished facilities, and at a facility's price
they would be four fifths of the month's points. The award is an eighth of the shallowest facility
level, and `ExperienceTest` pins the *ratio* so a later round cannot move one without the other.

**No award reads a cost, a cargo or a stock.** A run home pays the same on 200 units as on 200,000.
The level is what you did rather than what you own — there is a resource rail directly beneath the
badge, and a second one wearing a different hat is not worth having. What does scale is depth: a
level-20 mine is a day of waiting where a level-2 one is four minutes.

**The ladder is a straight line, which is not the convention.** `1,100 + 360 × level`. The reading
that decided it: experience accrues **linearly in time** — 4,685 points on day one against 4,818 a
day over thirty, while income grows by two orders of magnitude — and Davide's marks are a power law.
A geometric ladder is right for a game whose income is the score; here it is not. Balance-log round
32 has the fit, including the proof that no straight line hits all four of his marks and which one
was let go.

### Raised and not decided

- **The level does nothing.** It gates nothing, unlocks nothing and no rate reads it. That was not
  asked for and inventing it would be inventing a mechanic. The sheet's §5 names the two shapes worth
  putting to Davide when he asks, and which of them this game's own evidence prefers.
- **A level-up is not announced.** The badge changes, the gauge resets, and there is no notice. That
  has a visual half, so it is Claude Design's rather than a session's to invent.
- **Probe-spam is the one grind vector and it is buyable.** A survey pays the dearest base in the
  table and scouts come home, so a player who buys ten can run ten concurrent probes. Not obviously
  wrong — a player who explores hard is playing — but it is the first thing to look at if levelling
  feels degenerate. The dial is `SURVEY_BASE`.

## Three of the sim's four bots had stopped surveying, and nothing said so (0.17.0)

Not a design decision — a note about the instrument, kept here because the next verb to grow a cost
will do this again.

A probe has consumed a `SCOUT` since 0.15. `openingReport` was taught to buy one; `fleetRun`,
`depositRun`, `interactionCensus` and `printProgressionMilestones` were not — so `startSurvey`
refused them silently and four reports printed a probe column that was structurally zero for two
releases. It surfaced only because the experience report needed a survey count and read **zero
surveys in thirty days**.

**The failure mode is that a bot which cannot afford an action simply does not take it.** No
exception, no divide-by-zero, no empty table — just a column of noughts that reads like a finding.
The rule now lives in one place, `boughtScoutIfNeeded`. Rounds 30 and 31's fleet and deposit tables
were measured without probes; their subjects were the drive, the hauler and the vein and the bot
works the home system either way, so the conclusions stand — but any reading in them about *reach* or
the frontier is about a colony that never left home.

## Designing inside a coding session was tried once and withdrawn (2026-08-23)

Mid-0.16 Davide said *"You have capability to use Design directly now"* and that session designed the
player strip itself — read the design system, built four candidate compositions, rendered them, and
implemented the synthesis. It is withdrawn: *"I think it's easier to manage having everything inside
Design, and I think you cannot do that, so let's revert that rule for now."*

**The reason is not that the frames were bad, it is where they live.** A canvas in the Claude Design
project can be reopened, forked, compared against the last four screens and handed to the next round;
a composition rendered inside a coding session exists for exactly as long as that session does. The
thing that would reopen the permission is a way for a session to *write into the project*, which is
the whole of what is missing.

The rule is therefore [`session-roles.md`](../rules/session-roles.md)'s as written: local → Design is
a **round trip**, the session emits the prompt in a code block, waits, and does not open a pull
request for the half it could build. What is not withdrawn: reading the design system, and rendering
something to look at while implementing a design that has already come back. `player-strip-sheet.md`
§4–§5 keep the record of the one round that ran the other way.

## The first settings screen, and what a category switch can and cannot replace (0.18)

Davide, 2026-08-23: *"I want to add settings screen, for now it would include only notifications."*
The argument, the seven categories and the compaction ladder are in
[`settings-sheet.md`](settings-sheet.md); what is here is the four calls that were expensive and the
one that went against the recommendation.

**The per-row bells are replaced, not layered over.** In by-category mode a running row draws no
square, a hull card draws no bell, and the dispatch sheet draws none either. The alternative — a
category as a *default* that a row could still override — was rejected because it is two sources of
truth for one question: a lit bell would mean either "this one, specifically" or "because the
category is on", and nothing on screen could say which. What made the chosen shape cheap is that
`WatchUiState` was already nullable and already documented null as *the absence of a control*, so
five presentation modules emit `null` into a state every row has always had to draw.

**The affordability watch is the one ask a category cannot express, and it survives in both modes.**
A switch cannot be told *which row*, and "tell me whenever I can afford anything" is not a setting.
So the square keeps its price half everywhere and loses its completion half — and disappears entirely
when the *Price reached* switch is off, because a control that would book an alert the switch has
gated off is exactly the dead control this repository does not ship.

**Grouped and summary have no window, against the recommendation.** The build proposed reusing the
existing five-minute chain; Davide took *"one alert per category, ever"* with the cost stated: an
upgrade finishing in ten minutes is not announced until the research finishing in six hours is.
Recorded as his call, with the reversal cost measured — the window is one `Duration` and one
`chainedWithin` call in `GameNotifications.kt`, and the first install is what should decide it.

**The settings are in the preferences file, not in `GameState`, and their types are in `core`.** They
are not a fact about a colony — `advance` never reads them, they do not travel to a server, and
folding them into the snapshot would migrate every save on disk for a field the simulation ignores.
The types are `core`'s all the same, and that is the arrangement in which **no new edge appears
anywhere in the module graph**: the notification layer, the save layer and the settings screen all
already depend on `core` and none of them may depend on each other. `HullAlert` is the precedent on
the merits — an enum in `core` about which of two ways the player wants to be told about hulls — and
`NotificationCategory` is a partition of `core`'s own `FutureEvent` hierarchy, which a copy in the
client could only drift from.

**A nullable field in `Preferences` is still a required field on decode, and that cost a measurement
rather than a review.** Adding `notifications: NotificationSettings?` made every preferences file
already on disk fail to decode with `MissingFieldException` — which is a `SerializationException`,
which `load` answers `Preferences.NONE` to — so a player would have silently lost their galaxy
landing to a settings screen they had not opened. The fix is `explicitNulls = false` on that store's
`Json`, which makes a missing nullable property decode as null with no constructor default. The save
format keeps the opposite line deliberately: `GameSave` leaves unknown keys fatal and migrates by
schema number, because silently misreading a colony is worse than admitting it is unreadable.

### Raised and not decided

- **Nothing on the screen says whether the OS is permitting notifications at all.** Android 13 and
  iOS both gate on a runtime grant, and a screen full of switches the system has muted is the one way
  this feature can look like it works and not. Deliberately the next slice.
- **Grouping is global rather than per category.** "Summary for probes, single for research" is not
  expressible. Nobody has asked for it.
- **The 2–3 / 4+ threshold in the compaction ladder is a guess** at what a lock screen holds, and it
  is in the Design prompt as one.
