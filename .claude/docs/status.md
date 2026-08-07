# Status

Updated: 2026-08-07 (0.0.15)

## Landed

- **0.0.1 scaffold** — KMP monorepo (`core`, `sim`, `client/design`, `client/shell`, `server`),
  full local build green including iOS framework link.
- **CI** — jobs "Build (JVM & Android)", "Unit tests", "Screenshot tests", "iOS framework";
  first run green on `main`. All are required checks — keep the ruleset payload in sync when
  jobs change.
- **`protect-main` ruleset** — active, verified with `gh ruleset check main`.
- **AI harness** — CLAUDE.md, `.claude/docs/`, project skills, permissions.
- **0.0.2 walking skeleton** — `core` advance with metal accrual (fine-unit stock, composability
  property test), `:client:colony:presentation` with the resource rail + first Roborazzi
  baseline, shell ticking the clock at the boundary, sim fast-forward scenario.
- **iOS delivery proven** — `iosApp/` xcodegen wrapper; runs on the iPhone 17 Pro simulator and
  installed+launched on Davide's physical iPhone 16 Pro (signing, provisioning, Developer Mode
  all exercised).
- **0.0.3 core economy (M2)** — six buildings with exponential costs, one-slot build queue
  completing via `advance` as logged events, energy scaling (proportional on deficit), storage
  caps, robotics build-speed, nanite gate at Robotics 10. Composability property extended
  across completion boundaries and caps.
- **0.0.5 Colony screen (M3)** — resource rail, facility rows with per-resource costs and
  affordability by colour, tap-to-upgrade.
- **0.0.6 returning-fleet strip**, **0.0.7 persistence** — JSON snapshot save, offline
  progression credited on the way in.
- **0.0.8 balance + parallel builds** — play-test feedback from Davide: upgrades run in parallel
  (one job per facility, progress on the facility row, hero card gone) and the placeholder
  curves are human-scale (60/30/15 per hour at level 1, +25% output per level, ×1.5 cost per
  level, 500 metal / 300 crystal starting stock). See `decisions.md`.
- **0.0.9 iPad** — the app fills the screen at any size, Split View and Stage Manager work, and
  content caps and centres past a phone's width (Davide, #11).
- **0.0.10 local notifications (the rest of M8)** — `core.futureEvents` derives what is still
  coming, `:client:notifications:data` books it, the shell reschedules on the same trigger as
  the save. iOS schedules for real, desktop prints, Android waits for an app module. Also fixed
  the `MARKETING_VERSION` drift that shipped 0.0.8 to TestFlight labelled 0.0.7, and a latent
  build defect the new module exposed: every project shared one Gradle group, so two modules
  named `data` had identical coordinates and one silently left the compile classpath. The group
  now carries the project path — see `decisions.md`, and expect the same for `presentation` and
  `domain` layers as they arrive.
- **0.0.11 tab bar (slice 1)** — the mockup's five-destination bottom bar in `:client:shell`,
  with Research, Shipyard, Galaxy and Fleets as honest empty states saying what will be there.
  Navigation is the shell's because a tab set names every feature; the glyphs are drawn from the
  mockup's own SVG paths on a `Canvas`; safe-area insets moved from `ColonyScreen` to the
  scaffold. Each feature that lands takes a parameter on `MainScaffold`, so its signature is the
  list of what is really built. See `decisions.md`.

- **0.0.12 research (slices 2 and 3)** — the whole branch, built to the 0.1 research decision sheet
  Davide approved. `core` gains `Technology` / `Research` / `ResearchJob`, a decided
  `ResearchBalance` (three technologies, compounding multipliers, ×1.5 costs, duration riding
  Robotics), `startResearch` with one empire-wide slot, completions applied by `advance` as logged
  events with a pinned tie-break, and effects that reach production in a fixed order (building
  curve → research multiplier → energy deficit). `futureEvents` carries research, so the alerts do.
  Save schema 3 **migrates** version 2 rather than retiring it. `:client:research:presentation`
  renders the three rows in four states with six baselines at 393dp and 320dp, and the first
  behaviour test in the repo that drives a real game interaction — through a Robot, as the taxonomy
  asks. The resource rail moved to `:client:shell`, because the design draws it on Research too and
  a feature module cannot own what another feature needs. See `decisions.md` and `balance-log.md`.

- **Test taxonomy + per-kind coverage reporting** — tests declare their kind by class-name
  suffix (`…Test` / `…IntegrationTest` / `…ScreenshotTest` / `…BehaviourTest`),
  `-Poltre.testCategory` filters the build to one kind, and the new **Coverage** CI job reports
  line/branch coverage *per kind* with a delta against the last `main` run, as one rewritten-in-
  place PR comment. Reporting only — no thresholds, not a required check. See `decisions.md` and
  the `test-coverage` skill.

- **0.0.14 the design system becomes a family of modules** — `:client:design` stops being one
  module and becomes a *directory* of layer modules, split the way Compose splits itself:
  `:core` (tokens, theme, font), `:icon` (`PowerMark`), `:component` (`CostChip` + its ui-state,
  `ProgressBar`, `SectionLabel`), `:format` (`toChipLabel` / `toCountdown` / `pad2` /
  `groupedByThousands`, with no Compose in it) and `:testing` (`oltreRoborazziOptions`, in the main
  source set). Davide's call — the rejected options are in `decisions.md`. What forced it was not
  the duplication count but the loss of ownership: the rail moving to the shell at 0.0.12 left
  `PowerMark` and the cost chip with no owning feature. The tab glyphs deliberately stayed in the
  shell (one caller). **Nothing a player can see changed, and no screenshot baseline moved** — the
  baselines were recorded before the extraction commit, so the check had to verify rather than
  re-record. Also repaired `main`, which had been red since 0.0.13 (below).

- **0.0.14 repair — `main` was red from 0.0.13 and the ruleset did not catch it.** `#16` merged
  while its checks were still running, and those runs were attached to a `workflow_dispatch` run,
  which `protect-main` does not count as the required checks. Two things were broken, both in test
  code only: `TestResourceRailUiState` never got the `throttled` argument (so
  `:client:shell:compileTestKotlinDesktop` failed, taking down Unit tests, Screenshot tests *and*
  Coverage), and the six research baselines had never been recorded at all. The shipped 0.0.13
  build was unaffected — Xcode Cloud runs no test actions — so the repair carried no version bump.
  See `decisions.md`: **a dispatched run is not a required check**, so read conclusions from
  `repos/.../commits/<sha>/check-runs` before merging.

- **0.0.15 galaxy procgen (slice 4)** — `core` gains the whole map, built to the galaxy decision
  sheet. `GalaxyCoordinate` / `StarClass` / `World` / `WorldTraits` / `Hazard` / `Tolerance` /
  `WorldVerdict`, a decided `GalaxyBalance` beside `ResearchBalance`, and
  `worldAt(seed, coordinate)` — O(1), reads no neighbours, draws every axis from its own named
  sub-stream so a later slice adding a trait cannot reroll anyone's map. Save **schema 4 migrates
  3**, storing the seed, the home coordinate, the surveyed set and who holds what, and never a
  world. `advance()` is untouched and deliberately has no hook. `:sim:run` prints the distribution
  against the sheet's §9 targets; `GalaxyDistributionTest` pins it.
  **One §9 target is unmet and is Davide's to settle** — see below and `balance-log.md` round 5.
  Also repaired `./gradlew build`, which was failing on `main` before this slice started.

- **0.0.15 galaxy screen (slice 5)** — `:client:galaxy:presentation`, built to the Claude Design
  page's recommended direction: **the orbit page**. One system fills the screen, its fifteen orbits
  drawn once on a `Canvas` — hot to cold, empty slots included — with the worlds it holds listed
  under it. Six baselines at 393dp and 320dp cover every verdict; `GalaxyScreenBehaviourTest` drives
  the real interactions through `GalaxyRobot`. Galaxy left `OltreTab.pendingWork`, so the shell's
  empty-state baseline moved to Shipyard. See `decisions.md` for why the system selector is the
  feature's state rather than the shell's, and for the three design calls still open.

## Roadmap — v1 in vertical slices

The v1 feature set from Notion is *3 resources, 6 buildings, 4 ship types, one research branch,
a large procedurally generated galaxy, 3 AI empires, local notifications, JSON snapshot save*.
Four of the eight are done. What is left, decomposed into slices that each end playable —
**sequencing is the agent's (per the Notion hand-off), the content is Davide's**:

| # | Slice | Ends with | Needs a design call first |
|---|---|---|---|
| ~~1~~ | ~~**Tab bar**~~ | Landed at 0.0.11 | — |
| ~~2~~ | ~~**Research: core**~~ | Landed at 0.0.12 | — |
| ~~3~~ | ~~**Research: screen**~~ | Landed at 0.0.12 | — |
| ~~4~~ | ~~**Galaxy: procgen**~~ | Landed at 0.0.15 | — |
| ~~5~~ | ~~**Galaxy: screen**~~ | Landed at 0.0.15 | — |
| 6 | **Shipyard: core + screen** | The 4 v1 ship types, built from the shipyard, held in one empire-wide pool | **Yes** — the ship set (today's `CARGO/FIGHTER/CRUISER/COLONY_SHIP` are placeholders) |
| 7 | **Fleets: outbound** | Sending a fleet: distance as travel time, an outbound leg, the Fleets tab. The return leg already exists | **Yes** — travel-time formula, fuel |
| 8 | **Combat** | Seeded `resolve(a, b, seed)` and a battle report in the event log | **Yes** — the combat model |
| 9 | **AI empires** | 3 scripted empires that grow and raid, driven from `advance` | **Yes** — how visible, how aggressive |
| 10 | **Colonisation** | Settling a second world; the outpost → settlement → self-sufficient lifecycle | **Yes** — the pillar's rules |

Five of the eight v1 features are done. **Slices 4 and 5 are unblocked** as of 2026-08-07:
Davide asked the build to settle the galaxy's open questions, and
[`galaxy-sheet.md`](galaxy-sheet.md) is the result — trait axes, coordinate space, the two
visibility tiers, generation constants and the target distribution. It is the design, in the same
shape as the 0.1 research sheet, and every line in it is Davide's to overrule.

Sequencing after that is still his: #6 (the real ship set) and #8 (the combat model) each need a
call before they can start, and #7/#9/#10 sit behind them.

Why the galaxy went first, recorded so it is not re-litigated: #7 needs destinations, #9 needs
somewhere to put three empires and #10 needs a second world to settle, so all three are dead
letters until a map exists. #8 is the only remaining slice that is genuinely independent — and
combat with nothing to fight over is a system without a reason.

The research slice was copied as a shape: a decision sheet that answers the design questions *and*
argues the alternatives it rejected turned two slices into an implementation with no invented
numbers in it.

Colonisation (#10) is called a **core pillar** on Notion but is not in the eight-item v1 list;
carried here because the pressures that replace hard caps (upkeep, logistics, distance decay,
real failure) have nothing to act on without it. Whether it is v1 or v1.1 is Davide's call.

## Pending / not yet set up

- ~~**The sheet's §9 `fails exactly one axis` target of 35–45% cannot be reached**~~ — settled
  2026-08-07, Davide delegating the call: the three comparable axes were kept and the target
  corrected to 12–18%, which is what that shape produces. Gravity and pressure were tightened to
  meet temperature (all three now gate ~25%) and the worth-it threshold went to 0.92. Every §9 row
  is now met. See `balance-log.md` round 5 for what to move if it plays wrong.
- **Watch next balance round:** 17 settleable worlds galaxy-wide, ~4 per galaxy — inside the ≤0.5%
  bound but stricter than the sheet's illustrative "~24". The lever if the first settleable world
  takes too long to find is the **worth-it threshold**, not the tolerance bands, which now carry
  the axis balance.
- **Three smaller calls the sheet did not make** are assumed and marked as such in the code, listed
  in `balance-log.md` round 5: the star class distribution (equal thirds), where home is, and what
  `Settleable` carries.
- **`GameState.initial` now requires a galaxy seed**, and the five client test modules each declare
  their own one-line `freshState()` helper as a result. That is the duplication `:core-testing`
  (named in `architecture.md`, never built) exists to remove — the threshold the repo uses is "a
  third caller justifies a module", and this is five. Deliberately not built in this slice: it is a
  build-layout change with nothing to do with the galaxy.
- **`Coordinates` and `GalaxyCoordinate` are now twins.** The old one carries
  `ReturningFleet.origin` and is unbounded; the new one is bounded to the real coordinate space.
  Folding them together is a fleets change, so slice #7 owns it.
- **Three open calls from the Galaxy Design sheet**, none of which block anything:
  1. **Does a near miss look different from a hopeless one?** 1.78 g against a 1.45 g band and
     2.62 g against the same band read identically today except for the digits. A "close" treatment
     would be useful and would also be the interface recommending a purchase, which nothing else in
     the app does. The design left it out and said it was the omission it was least sure of.
  2. **Should a relay state an effect it cannot confer?** "+18% range while held" is true of a
     mechanic that does not exist and cannot until multiplayer. It ships as **placeholder copy**,
     marked as such in `GalaxyUiState.kt`, like the notification copy and the unbuilt tabs'.
  3. **Who holds an `Occupied` world?** Nothing generates one — the three scripted empires are
     slice #9 — so it is the only verdict a player cannot reach today. The verdict is built and
     covered by a hand-written frame; the naming, and whether a holder carries a date, are open.
     (`Barren` and `Settleable` *are* reachable: the seed is minted per colony, and 2–3% of worlds
     pass every band, so roughly one colony in a dozen sees one in its own home system.)
- **Every `Blocked` row points at a Research tab that cannot sell what it names.** All three
  adaptation ladders are unbuilt, so the sentence is true and the purchase does not exist. The
  connection the design is after does not close until the adaptation technologies land — which is
  its own slice, and Davide's call per the galaxy sheet's own open list.

- Android app entry point (thin `androidApp`-style module) — when Android delivery matters. Two
  stubs are waiting on it: `AndroidSaveLocation.directory` and the no-op notification scheduler.
- **Open design question for Davide:** what raises the storage cap? (flat 10M placeholder now;
  candidates: a storage building, mine-level scaling.) With human-scale production the flat cap
  is far out of reach — it binds nothing until very deep levels.
- **Open design question for Davide:** should anything cap how many facilities build at once?
  Nothing does today (resources are the only limiter), while Notion's expansion pressures call
  for "limited simultaneous projects". Research answered half of it at 0.0.12 — one project at a
  time, empire-wide — so the remaining question is only about *construction*.
- **Open calls left by the research sheet**, recorded in `balance-log.md` and costing nothing
  until answered: compounding versus linear effects; whether Automation joins as a fourth
  technology in 0.2; and whether the two Robotics divisors (÷ 1 + 0.08 × Robotics for research,
  ÷ 1 + Robotics for construction) should ever be made to agree — which would be a rebalance of
  the colony, not of research.
- ~~**`oltreRoborazziOptions` is now in three modules**~~ — done at 0.0.14: it lives in
  `:client:design:screenshot-testing`, along with the rest of the design-system extraction. (It
  landed as `:client:design:testing` and was renamed when the module rules landed — rule 5 matches
  on the `-testing` suffix, so the original name left nothing stopping a `commonMain` from
  depending on it.)
- **Open design question for Davide:** what a notification *says* is player-facing content. The
  copy in `GameNotifications` is a placeholder that says what happened and that a decision is
  waiting. The same applies to the unbuilt tabs' one-liners in `OltreTab.pendingWork`, which say
  only what will be there.
- No linter (detekt) configured yet — decide when code volume justifies it.
- **Nothing drives the colony's upgrade tap.** Research fixed this for *its* screen at 0.0.12 —
  `ResearchScreenBehaviourTest` really taps Research and asserts the technology reaches the
  callback — but the colony's Upgrade button is still covered by `core` unit tests and by nothing
  that renders: every colony test passes `onUpgrade = {}` to nothing. `ResearchRobot` is now the
  worked example to copy; the prompt in `.claude/prompts/robot-behaviour-tests.md` predates it.
- **`MainScaffoldBehaviourTest` still queries nodes directly** in its test bodies, which the
  taxonomy asks it not to. It predates the convention and is the migration target; the research
  module shows the shape.
- **The `protect-main` ruleset payload is unchanged.** The new Coverage job is deliberately not
  required; if that ever changes, the ruleset has to change with it.
- **Agent sessions cannot build.** The remote environment's egress policy blocks
  `dl.google.com`, so Gradle cannot resolve AGP and `./gradlew build` fails before compiling
  anything; `maven.google.com` only redirects there. CI is the gate for agent-written code, and
  screenshot baselines go through the manual Record job. Not a repo problem — do not "fix" it in
  the build files.
