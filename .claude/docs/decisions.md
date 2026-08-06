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
