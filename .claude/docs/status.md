# Status

Updated: 2026-08-09 (0.2.1)

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
  place PR comment. Reporting only at first; it became a **required check** that fails when line
  coverage falls below `min(last main run, 95%)`. See `decisions.md` and the `test-coverage` skill.

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

- **0.0.16 the Blocked row explains itself** — presentation only, no balance number touched. The
  row states its yield as well as its cost, counts the bands it fails against the same 0.92 bar
  `Barren` quotes, drops the accent from a technology Research cannot sell, and the header carries
  a PLACEHOLDER line saying the adaptation ladders are not built. Both copy calls were Davide's,
  delegated to the build. See `decisions.md`.

- **0.0.17 the adaptation branch (`core` only)** — the open call the galaxy sheet left behind, and the
  thing that made every `Blocked` row a shopping list nobody could spend against. Written up as
  [`adaptation-sheet.md`](adaptation-sheet.md), the third decision sheet in the same shape. Thermal,
  Gravitic and Atmospheric Adaptation are **a second branch rather than rows four to six** — an
  adaptation level widens a band in °C / g / atm and the applied branch's whole row vocabulary is
  percentages of a per-hour rate — and they **share the one empire-wide research slot**, so climbing
  a ladder costs the production technology that was not researched instead. One shared gate
  (Robotics Factory 4), one cost curve (×1.5, as everything), and all three priced identically at
  1 : 2 : 3 **in three different currencies**, each in the resource its own axis makes rich. Save
  **schema 5 migrates 4** — and the hop *adds to* the research record rather than re-encoding it, or
  it would reset the levels the player earned. `verdictFor(world, state)` is the new one-call form
  the screen should use. See `decisions.md` and `balance-log.md` round 6.
  **Nothing a player can see changed** — see below.

- **0.0.18 the adaptation branch reaches the player (the screen half)** — the hand-off 0.0.17 wrote,
  built to a Claude Design sheet that answered the one open call and both secondary ones. **A second
  section on the Research tab**, not a segmented control and not a sixth destination: the argument is
  that a control which shows one branch at a time is a control whose job is to hide the thing you are
  giving up, and that with both on screen a running project explains itself — five rows read the same
  wait and the sixth counts it down, and the two numbers verify each other with nothing added. The
  adaptation row is the existing row with three different strings in it (band → band, unit), so both
  branches now render through **one** composable rather than two that promise to match. Galaxy took
  `verdictFor(world, state)`, so a verdict finally reads the levels the player bought; its blocked
  rows' remedies went back to accent **and** became tap targets — one decision, since an accent
  string that is not a target breaks the colour rule harder than the demotion did — and 0.0.16's
  PLACEHOLDER header line was deleted rather than replaced. `signed()`/`milli()` moved to
  `:client:design:format` so a band on Research and a reading on Galaxy cannot drift apart. Also
  repaired the branch, which did not build: Kotlin/Native rejects a comma in a backticked test name
  and ten of 0.0.17's had one. See `decisions.md`.

- **0.2.1 Android delivery** — the game runs on Android, and every version publishes itself. The
  wrapper `architecture.md` had anticipated since 0.0.1 finally landed, in the shape Davide chose:
  `androidApp/` is a manifest, a theme and the launcher icons with **no Kotlin in it**, and
  `MainActivity` sits in `client/shell/src/androidMain` beside the desktop `main()` and the iOS
  `MainViewController()`. **Rule 7 got its carve-out** — an allowlist of one name — because AGP 9
  will not let a KMP module apply `com.android.application`, because `iosApp/` has the identical
  edge and escapes only by not being a Gradle module, and because the shell declares every
  dependency as `implementation`, so the wrapper sees `App()` and no layer module at all.
  Publishing mirrors iOS: a merge that changes the version fires `release-android.yml`, which
  signs the APK with a real key from secrets, cuts the `v<version>` tag and attaches the APK to a
  GitHub Release whose body is the README changelog entry. Two runtime traps were caught on the
  way: Compose resources need `androidResources { enable = true }` or the fonts never reach the
  APK (CMP-9547), and the new entry point had to be excluded from Kover or it would have failed
  the coverage gate on its own PR. See `decisions.md`.

- **0.2.1 Android notifications, on Davide's call to stop holding them back** — the copy was
  already shared, so there was no design call to wait for, only engineering. `replaceAll` books
  one `AlarmManager` alarm per notification and persists the ids it scheduled, because Android
  cannot be asked what is pending the way `UNUserNotificationCenter` can. **Inexact alarms**
  (`setAndAllowWhileIdle`): an exact one needs a permission denied by default since API 33 and
  grantable only from system settings, and Doze holding an alert for minutes is affordable when
  builds run for hours. `BootReceiver` re-derives the schedule from the save after a reboot, which
  iOS needs no counterpart for. The permission is asked on the first frame, exactly as on iOS.
  `OltreApplication` fills the save directory and the Context before any component runs — Android
  is the only platform whose process can start with no screen. New status-bar icon in
  `:client:notifications:data`, reduced from the master's own arc. No test, matching the iOS and
  desktop schedulers: the seam is above the platform edge, and `GameNotificationsTest` already
  holds it. See `decisions.md`.

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

**The adaptation branch was never a numbered slice** and it is now finished: the galaxy sheet left
it as an open call rather than a row in this table, 0.0.17 settled it and built the `core` half, and
0.0.18 put it on screen. It sat between #5 and #6 in every practical sense — it is what makes slice
#5's screen mean something — and #6 is now the next thing with nothing in front of it but its own
design call.

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
- ~~**THE NEXT THING TO BUILD: the two screens that sell the adaptation branch.**~~ — **done at
  0.0.18.** The design call went to Claude Design and came back as *a second section on the same
  scrolling screen*; both screens landed with it, plus the deep link and the header deletion the
  same sheet answered. Two of the design's own premises did not survive contact with Compose and are
  recorded in `decisions.md`: the row is 106dp rather than 74dp, so six rows scroll a phone by about
  105dp instead of fitting it; and the pressure band has to drop its trailing zeros or the unit is
  ellipsised at 320dp. **What is still open from it:** whether a three-block world should list all
  three remedies now that all three are accent and tappable — flagged by the design itself as the
  loudest thing on the Galaxy screen, and tied to the still-open "does a near miss look different
  from a hopeless one".

- ~~**Every `Blocked` row points at a Research tab that cannot sell what it names.**~~ — the
  *purchase* landed at 0.0.17 (above); what is left is the screen that offers it. 0.0.16 had
  half-answered the copy side: the technology lost the accent that made it look tappable, the header
  said the ladders were not built, and the row states its yield and counts the bands it fails
  against the bar `Barren` names.

- **The adaptation band line overflows at 320dp from Atmospheric 8 upward**, and the unit is what
  gets ellipsised — "0.02 … 9.8 → −0.04 … 10.7 a…". Measured against the committed baseline, not
  inferred. Levels 8–11 are ones the design expects a player to reach (saturation is 11, and no cap
  was added). Three ways out, all of them Davide's call, in `decisions.md` under *Left open*. Nothing
  automated will catch it: a behaviour test reads the semantics string, which stays complete.
- **The blocked row's remedy grew 12dp taller** when its tap target was fixed from 15dp to 27dp, so
  a three-axis card is airier than the design drew it. Overrule if it reads loose.

- ~~Android app entry point (thin `androidApp`-style module)~~ — **done at 0.2.1**, and both stubs
  that were waiting on it are filled in: `AndroidSaveLocation.directory` and the notification
  scheduler.
- **Nothing has run the Android build on a device.** CI compiles the APK on every PR and the
  release job signs and publishes it, but no session in this project has installed one and looked
  at it. **This is the largest unverified surface in the repository** — everything Android was
  written by a cloud session that cannot compile a line of it. The first install is the first test
  of five things nothing else covers:
  1. the bundled font actually reaching the APK (CMP-9547, see `decisions.md`);
  2. edge-to-edge and `WindowInsets.safeDrawing` agreeing on a device with a gesture bar;
  3. the save surviving an update rather than only in theory;
  4. an alarm actually firing, and the status-bar icon reading as a mark rather than a smudge;
  5. `BootReceiver` surviving a real reboot — the riskiest of the five, because a receiver that
     throws at boot is a crash dialog on every start-up. It catches everything it can, but nothing
     has proven that on a device.
- **The notification copy is still PLACEHOLDER**, now on two platforms rather than one, and the
  notification channel's name and description in `NotificationReceiver` join it — those are shown
  in Android's own settings, so they are player-facing too.
- **Open design question for Davide:** what raises the storage cap? (flat 10M placeholder now;
  candidates: a storage building, mine-level scaling.) With human-scale production the flat cap
  is far out of reach — it binds nothing until very deep levels.
- ~~**Open design question for Davide:** should anything cap how many facilities build at once?~~ —
  **answered 2026-08-08: no.** Upgrades stay parallel and the stock stays the only limiter, because
  the decision the colony poses is what to spend on, and it is a real one because resources are
  finite rather than because a slot is. Davide's call, against a session that proposed a cap. Both
  candidates were measured first and both are worse on every axis — one slot halves progress, locks
  out Research entirely and *still* leaves 83% of the window empty. See `decisions.md` and
  `balance-log.md` round 8.

- **THE NEXT THING TO BUILD: the screen that dispatches a probe.** The `core` half landed at 0.1.2
  and **nothing a player can reach changed** — `startSurvey` exists, `advance` lands probes,
  `adaptationShortlist` derives what a ladder level would unlock, and no screen offers any of it.
  What the local session needs to build: a dispatch action on the Galaxy system page (plus a way to
  reach a distant system that is not 50 taps of a ±1 stepper — `GalaxyNav` is the risk to the
  5–10 minute rule), an in-flight countdown, a landed state, an "already surveyed" state, and the
  per-ladder shortlist line on Research. Round 8's **held** cost-proportional duration curve should
  ride along with it: it fixes the colony's own idleness, which the probe deliberately does not.
  See `balance-log.md` round 9 and `decisions.md`.

- ~~**THE NEXT THING TO DECIDE: a check-in has one verb in it.**~~ — **decided 2026-08-09**, three
  calls answered by Davide and the rest measured; the `core` half is built. Kept below because the
  measurement is the benchmark round 9 is judged against. Davide, 2026-08-08, playing 0.1.1:
  *"Ho poche cose da fare. Solo premere un tasto"* — and, crucially, *"non voglio rimuovere il senso
  di progressione, anzi!"* and *"I don't want the user to have nothing to do for hours, but I don't
  want it to be forced to keep logging it either"*. Measured by the new opening report in `:sim:run`:
  **6 of 8 check-ins over the first two days offer exactly one kind of decision**, the second kind
  does not exist until hour 29, the colony has nothing in flight for 42 of 48 hours, and the busiest
  session books 72 minutes of work. **No balance number fixes this** — four candidates were measured
  and all four rejected (see `balance-log.md` round 8). It is a content call: which existing system
  grows a second verb, and when. The cheapest candidate on the table is **surveying** —
  `GalaxyState.surveyed` already exists, holds the home system at genesis, and is written to by
  nothing, so the Galaxy tab shows four worlds forever and cannot be acted on.
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
- **The coverage gate has a known hole: a cache miss skips it silently.** With no `main` baseline
  restored, the verdict is `skipped` and the job goes green — the comment and log say so, but
  nothing blocks. A PR merged during a cache eviction is a PR nothing measured.
- **Agent sessions cannot build anything that needs AGP.** The remote environment's egress policy
  answers 403 to `dl.google.com`, so Gradle cannot resolve AGP and `./gradlew build` fails before
  compiling anything; `maven.google.com` only redirects there, the Gradle Plugin Portal redirects
  to Maven Central, and Google does not publish AGP to Maven Central. CI is the gate for
  agent-written code, and screenshot baselines go through the manual Record job. Not a repo
  problem — do not "fix" it in the build files.
  **They can, however, build and run `:core` and `:sim`**, which is most of what a domain or
  balance session needs: `.claude/tools/gradle-without-agp.sh :sim:run`. The sim consumes `:core`'s
  JVM target and AGP is in `:core` only for an Android target it never reads, so a two-module
  overlay compiles the same sources and runs the same harness. Added at 0.1.1, when round 7 of the
  balance log needed a measurement rather than arithmetic; the 0.0.12 greedy week reproduced
  through it byte for byte. See `.claude/rules/session-roles.md`.
