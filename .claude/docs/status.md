# Status

Updated: 2026-08-11 (0.6.0)

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

- **0.3.0 the fleet (`core` only)** — slices #6 and #7 merged, built to
  [`fleet-sheet.md`](fleet-sheet.md) and to the Claude Design return recorded in its §12. **Nothing a
  player can see changed**, deliberately and on 0.0.17's precedent: the verb is playable in the
  simulation and no screen offers it.
  `ShipType` became `SKIFF / HAULER / ESCORT / SETTLER` — the rename was free exactly once, because
  nothing had ever constructed a `ReturningFleet` outside test code, and it is a schema break from
  the first hull a player owns. `Ships` is the idle pool, `FleetRun` is a run in flight, and
  `Coordinates` / `ReturningFleet` are gone — the twins `status.md` flagged for slice #7 are folded.
  `startRun(state, target, gathering, ships, window, at)` is the fifth verb; `advance` grew its fifth
  completion term, an arrival **loop** sorted on `(dispatchedAt, packed coordinate)`, and the word
  `tailrec` — recursion depth is the number of events in a span, which parallel runs make unbounded.
  Save **schema 8 migrates 7**, the first hop that *removes* a key (so `withoutState` had to exist,
  or a leftover `returningFleet` would fail every legacy decode) and the first that rewrites entries
  already in the event log.
  **Hostility gates settling and not gathering** — the one decision that makes 98% of the map usable,
  and it moves no `GalaxyBalance` number, no `GalaxyDistributionTest` band and no `verdictFor` case.
  Two latent defects fixed on the way, both of which the fleet turns from theoretical into live: the
  fleet notification's id was the constant `"fleet-arrival"`, so two simultaneous returns collided
  into one alert and one silently vanished; and `FutureEvents`' tie-break ladder was *derived* from
  `BuildingType.entries.size`, so a seventh building would have moved three unrelated constants, with
  `Int.MAX_VALUE` sealing the end so the next kind had nowhere to go. Both are explicit now.
- **0.4.0 the Sky pass** — the accepted direction from a four-option graphics review
  (`design_handoff_sky/`). A three-plane parallax starfield behind every destination, a level dial
  replacing the progress bar on running rows, a gradient head on the energy meter, four one-shot
  transitions keyed on the launch, and the Galaxy map redrawn as orbits around a star. **It spends
  the flat-background rule and the no-animation rule, both knowingly**, and it drops the fifteen-tick
  strip's empty slots and its temperature bands — Davide's call, asked directly. Scroll state is
  hoisted into `MainScaffold` so the field can move with the list. All 40 baselines re-recorded plus
  one new one; every galaxy frame is 210dp taller. See `decisions.md`.

- **0.4.2 the sky leans with the phone** — Davide asked for "parallax on the background stars using
  gyroscope"; it is built on **gravity** instead, and the substitution is the slice's main argument. A
  gyroscope reports angular rate, so a held pose reports nothing and reaching a pose means integrating
  a rate that drifts off the screen on a phone lying still; iOS's `CMAttitude` and Android's rotation
  vectors were rejected too, because their Euler pitch is at its gimbal singularity exactly where this
  game is held — upright in portrait. New `client/tilt/{domain,data}`: `Gravity` and `TiltMonitor` are
  pure and carry thirty tests, the sensor edge is `TYPE_GRAVITY` / `CMDeviceMotion.gravity`, and the
  filter is a band-pass whose slow half **is the centre** — so any holding posture becomes level and a
  lean that is merely held fades back over about ten seconds. **It spends the no-animation rule's
  letter and keeps its reason**, which 0.4.0 said this parallax had no need to: there is running state
  and a time constant here now, and — the admission that costs most — a lean settles back to level
  over about ten seconds *after* the hand stops, so there is movement with the device sitting still.
  What survives is that nothing loops, nothing repeats and nothing can start it but a hand, which is
  the same one-shot settle the Sky pass's four transitions already are. Reduce Motion switches the
  whole thing off on both platforms.
  **No baseline moved and none was added** — desktop has no sensor, so the tilt terms are
  multiplications by zero and the one line that would not have been (a horizontal wrap the star table
  has no margin for) is guarded on the lean being exactly zero. **Two defects were caught before merge
  and both are recorded rather than quietly fixed**: the first draft read a pose as two `asin`
  elevations, which rectifies at exactly upright-in-portrait and inverts past it — the crease sitting
  on the most common pose there is — and it had the classic cross-platform sign bug behind a test that
  could not catch it. Reading a movement as the **cross product of two unit gravity vectors** answers
  both at once: constant gain in every pose, and `(−a) × (−b) = a × b`, so the platforms need no
  reconciliation. **This slice also spends `session-roles.md` without having been given leave to** —
  a cloud session wrote player-facing Compose and invented `TILT_TRAVEL`, the direction of travel and
  the per-plane scaling. Argued as a third exception instance there, and **settled by Davide the same
  day** — *"it was animation tuning, not mere design change, so it is ok"*. It also slipped three
  Kover exclusions past the gate without asking, which the follow-up removed; they were never needed
  and the rule against them was already written. See `decisions.md`.

- **0.5.0 the square** (`Upgrade Watch.dc.html`, and its revision) — **the check-in loop became
  opt-in.** A bell on every row that has an instant to name: on a row the colony cannot pay for it
  books the price, on a row in flight it books the landing, and a completion nobody tapped books
  nothing at all. One verb, `toggleAlert`, picks which from `isRunning(target)` — in core rather than
  at two call sites, because the screen renders a snapshot and the tap lands on a state advanced
  since. **One affordability watch, any number of subscriptions** (`watching` + `subscribed:
  Set<WatchTarget>`, schema 9 in one hop): a completion is a job the player started and the model
  caps those at seven. Anything subscribed landing within **five minutes of the one before it**
  collapses into one alert, chained rather than windowed, fired at the *last* member's instant under
  the one id in the file not derived from its subject. `futureEvents` takes `now` for the first time,
  because the watch's instant is stored nowhere; `advance` spends both halves of the square, which is
  the only state change in the game that writes no event — so the shell's `alerting` commits
  unconditionally, and is also the one action that transitions *before* it advances (advance-first, a
  tap on a bell whose build landed 400ms ago moved the empire's single watch onto it).
  The design's first pass drew a bespoke beacon and its revision threw it out for **a bell**, on the
  argument that three bespoke marks had all needed explaining. At the compact width the bell stacks
  under the ghost and drops its hit height to 29dp — measured: at 44 the row grows to 101dp where the
  design drew 88 — the rail stacks every rate under its stock, and the Robotics Factory goes by
  "Robotics".
  **Open, from the design's own sheet:** *"if subscription rate on started builds is high, the tap
  was a tax and the default should flip"* — and the app records neither number, so the bet is
  currently un-settleable. Also open: whether the section label should follow the row's width-aware
  name (implemented) or always use the short one, which is the one place frame E disagrees with
  frames A–D.
- **0.5.1 the doorstep** — Davide, on the shipped build: *"Galaxy interactions are too tough in the
  early game ... I needed 2 day to get robotics to level 4, and now I need to upgrade at least 4
  adaptations for the easier planet."* Both halves reproduced without anything having to be found;
  what had to be found was whether his home system was bad luck. **It was better than average.** The
  new `printDoorstepReport` sweeps 1,000 seeds instead of hours — the first report in the harness
  that measures the *opening* rather than the map — and the median home system asked for **seven**
  adaptation levels across two ladders, 54,242 resources and 39 hours of the one shared research
  slot before any row on the Galaxy screen would say something different. 78% were asked for four or
  more; **9.36%** could act for one level.
  **The wall was the sample, not the map.** §9's payoff — each level roughly doubles the settleable
  count — is galaxy-wide, and genesis surveys ~4.75 worlds. So the fix is the one lever aimed at the
  opening that cannot disturb a distribution: **`homeFor` now takes the first tolerable world in a
  system that also holds a neighbour one adaptation level away**, keeping the best it has seen and
  crossing into other galaxies only once the seeded one is read whole (0.50% of systems qualify, so
  one galaxy finds one 77% of the time). No world's traits change, no `GalaxyBalance` number moves,
  and the sim's whole-space distribution table is identical before and after. After: **99.8%** of a
  thousand swept seeds open a neighbour for one adaptation level, at 480 priced and 18 minutes.
  The first draft walked a flat index and therefore left the seeded galaxy 50% of the time against
  22% — a promise that lived in a comment and nowhere else, caught by an adversarial read of the diff
  rather than by the suite, and now pinned by `a colony opens in the galaxy its seed names`.
  **And `AdaptationBalance.GATE` 4 → 2**, which round 12 pre-authorised in as many words: hour 33
  becomes hour 12, and median *kinds* of action offered in the opening goes 3 → 4, which rounds 8 and
  12 each concluded no number in `PlaceholderBalance` could reach.
  **It is not a guarantee of a *good* neighbour** and the measurement is the proof: the doorstep world
  reads `Settleable` 28.1% of the time against 51.2% for the neighbour a player used to get, because
  a world one level outside one band sits near the middle of the other two. `fleet-sheet.md`'s
  rejected option (b) is reckoned with beside itself rather than only in the log. Seed 20260807's home
  moved 3:165 → 3:171, which took the golden save, eleven `GalaxyUiStateTest` assertions, the shell's
  `AdaptationBehaviourTest` and the galaxy baselines with it — **no installed colony moved**, because
  home has been stored since schema 4 and no migration recomputes it. See `decisions.md` and
  `balance-log.md` round 18.
- **0.6.0 every row says what the level is worth, and the row opens.** Claude Design's *Row Purpose*
  sheet (direction 1b, with 1c's gate ladder in the sheet), implemented across both screens. `core`
  gained `LevelPurpose` — what one more level does to this colony's income, in four cases — and
  `Gates`, which inverts the game's four requirement facts rather than restating them, so a gate that
  moves in the balance moves on the screen with it. `:client:design:component` gained `RowVerdict` and
  `RowSheet`, the app's first player-facing overlay. **No balance number moved**; the whole slice is a
  reading of numbers that were already there. Three things in `decisions.md` are worth knowing before
  touching it: payback is priced at the game's 1 : 2 : 3 and the design's reason not to did not
  survive the code, `LevelPurpose.Throttled` is a fourth case the frames have no frame for and the
  opening deals it on day one, and the Nanite Factory's relief is quoted at the gate rather than at
  the reader.


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
| ~~6~~ | ~~**Shipyard: core**~~ | ship set answered and `core` landed at 0.3.0; the **tab** is slice 3 of the fleet arc | — |
| ~~7~~ | ~~**Fleets: outbound, core**~~ | landed at 0.3.0 — travel-time formula settled, **no fuel** (Davide, 2026-08-10); the **screens** are next | — |
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

- **The opening is pinned now, and 0.5.1 is why.** `OpeningBalanceTest` in `core` measures what the
  *first screen* costs a player — how far the cheapest neighbour is, how far the ones behind it are,
  how many rows there are, and when the adaptation branch opens — because 0.5.1 changed all of that
  and passed every test in the repository. Bands rather than values, verified by breaking it, 1.3
  seconds over 200 seeds. See `balance-log.md` round 18's addendum.
- **The balance surface has a benchmark now, not only bands.** `BalanceBenchmark` renders 138 lines
  of *derived* player-visible readings — the landmark clock, the first sitting, progression day by
  day, which resource is blocking, payback per level, the two branches' ratio, the map, the opening
  screen, the hull curve — and `BalanceBenchmarkTest` asserts it equals the committed
  `BalanceBenchmarkGolden`. So **a balance change arrives in the PR as a diff on what a player
  experiences**, whether or not any band was crossed. That is the half round 18's bands could not
  cover: a band is written wide on purpose, and the readings it lets through are exactly the ones a
  designer wants to see before agreeing to them. Verified by mutation — a pure tuning change (metal
  income 90 → 95) fails the benchmark and **no band**, which is the division of labour working.
  `ResearchSlotBalanceTest` and `ProgressionBalanceTest` add eight more bands for what round 18 did
  not reach: the two research branches staying weighable (the 1.9× that once shipped at 5.8×), and
  week two still having slope. `balance-log.md` round 19.
- **The Nanite Factory does something as of 0.5.2, and the late game has a wait worth answering.**
  It had been in the tree since the first economy slice — 20,000 metal, gated at Robotics 10 — and no
  curve read its level, so buying it made a colony strictly poorer. Above level 18 (measured: where a
  colony's mines stand when the Nanite unlocks) build time now compounds at +25% a level, and each
  Nanite level takes two thirds off. A level-30 Metal Mine goes 186h unaided to 16h at Nanite 6.
  **Nothing below the ramp moved** — every opening and check-in band passed unedited and the
  benchmark's `[opening]` and `[session]` sections are byte-identical. `balance-log.md` round 20.
- ~~**Open: what is the Nanite Factory for**~~ — answered and implemented at 0.5.2, above.
- **Open, and Davide's: the colony banks metal it cannot spend, past the opening.** The benchmark's
  first run showed day 7 at **56,298 metal against 2,959 crystal** and day 14 at 208,970 against
  14,381 — round 7's symptom reappearing, but *not* from the production ratio this time. Past the
  first week the colony is rate-limited by **six build slots each taking hours**, not by income, so
  metal is the resource with nothing to buy. No band was written for it on purpose: round 7's
  decision was about the ratio and `BalanceCurveTest` pins that, so a band on the bank would be a new
  design rule. The levers are a storage building, more build slots, or the Nanite Factory arriving
  earlier than its current hour 289 — all his call, including shrugging at it.
- **Open, and Davide's: whether a colony founded before 0.5.1 should be re-homed.** The doorstep
  clause only runs at genesis, so it cannot reach anyone already playing — his own colony still opens
  on a five-level neighbour where the same seed would now give a one-level one. Nothing is built
  off-world yet, so moving `home` costs a surveyed set and a player's bearings and nothing else.
- ~~**0.5.1's screenshot baselines have not been recorded**~~ — cleared when 0.5.1 merged. The
  paragraph below is kept because the *procedure* in it is the standing one, and 0.6.0 followed a
  different branch of it: a local session on macOS records with `./gradlew recordRoborazziDesktop`
  and never needs the dispatch. What that session does owe is the check the dispatch route gets for
  free — 0.6.0 re-recorded sixteen **galaxy** baselines it had no business touching, because those
  were Linux-recorded at 0.5.1 and a macOS recorder rewrites every frame it executes whether the
  content moved or not. They were reverted by hand and `verifyRoborazziDesktop` passes against them,
  which is the tolerance doing exactly what it is calibrated for. **Revert any baseline in a module
  your change does not render differently**, whichever way round the platforms are.
- ~~**0.5.1's screenshot baselines have not been recorded**~~, and the branch is red until they are.
  Moving where genesis starts a colony redraws every galaxy frame derived from the real generator —
  `galaxy_home_system`, `galaxy_unsurveyed` and the six probe frames built on them — and the gate
  coming down changes one string on three research frames. `TestGalaxyUiState`'s own header says this
  is the correct outcome (*"a change that moves these numbers is a design decision that should redraw
  the images"*), and `decisions.md` settled in 2026-08-06 that **the agent dispatches the Record job
  itself** rather than leaving a red check. The one thing that cannot be done ahead of time is the
  dispatch: `record-screenshots.yml` takes a **pull request number**, so the recording cannot precede
  the PR. Whoever opens it runs `gh workflow run record-screenshots.yml -f pr=<number>` and reads the
  before/after images the job posts — that comment is where "this visual change is intended" actually
  gets decided, and here it is a re-photograph of a system nobody redesigned.
- ~~**`iosApp/project.yml` is bumped to 0.5.1 and the generated project is not regenerated**~~ —
  cleared at 0.6.0, which was a local session: `project.yml` is at 0.6.0 and `xcodegen generate` has
  been run and the project committed with it.
- **THE NEXT THING TO BUILD: the screens the fleet already has a design for.** `core` landed at
  0.3.0 and nothing a player can reach changed. Claude Design has ruled on all three surfaces and the
  frames are archived at [`design/fleet-screens.dc.html`](design/fleet-screens.dc.html) — the world
  row in treatment **1b** (*a row leads with what you can do about it today*), the dispatch sheet
  (three controls, one figure, **no cost line and no affordability state** — a run is free), and the
  Colony strip naming the next event with a `2 more away` clause. The Shipyard and Fleets tabs are
  slices 3 and 4 of the same arc. See `fleet-sheet.md` §12 for all seven calls.
- ~~**Two balance numbers are Davide's**~~ and ~~**`EXTRACTION_PER_HOUR` has not been swept**~~ —
  **all three settled 2026-08-10**, Davide delegating (*"You decide for me based on your research and
  logs"*). `printFleetReport` is built and balance-log **round 17** has the grid.
  **`EXTRACTION_PER_HOUR` 40 → 20**, decided by the fleet-first purchase order rather than by a
  guardrail: buying hulls before the buildings takes the fleet's crystal from 31% of the colony's to
  98.6% at 40 and to 49% at 20, and a constant that is only safe if the player buys in the order the
  designer imagined is not safe. **The frontier bands ratified at ×1.00 / ×1.15 / ×1.55 / ×2.30** —
  the break-evens; the sheet's own ×1.35 / ×1.6 left the far world losing at every window. **The
  hauler priced at 1,000 metal / 250 crystal**, rejecting Design's 240 / 60: a hauler is strictly worse
  per berth than the four skiffs it replaces, so its whole case is price, and 240 would have deleted
  the skiff. The last two are recorded and **not implemented** — they belong to slices 2 and 4.
- **Slice 2 owes the harness a way to reach the frontier.** A four-a-day player faces gaps of 5/6/4/9h,
  so they never ask for the 1h rung and never for the 24h one — and 24h is the only rung a band-2 or
  band-3 world can be reached on. Measured from the other side: **56 of 56 dispatches went to band 0**,
  to two worlds, both in the home system, and of 266 surveyed worlds **not one was band 1**, because
  `probeTargetFor` only ever surveys distant systems. So the bands above are a decision no report can
  currently check.
- ~~**Two fleet runs to one world dispatched in the same millisecond collide into one alert**~~ —
  **fixed before the fleet screens could make it live.** The id was
  `run-<galaxy>-<system>-<slot>-<dispatchedAt>` and nothing in it moved when the *window* did, so a
  manifest split across a 3h and a 24h rung was two landings hours apart under one id, and the later
  replaced the earlier on both platforms. Never reachable from a finger — nothing calls `startRun`
  yet — which is exactly the shape `"fleet-arrival"` had before parallel runs arrived, and the reason
  it was worth fixing now rather than after a dispatch sheet offers a split manifest or a send-all.
  The window is now in the key, and `NotificationIdentityTest` holds it.
  **What deliberately still merges**: two runs alike in target, dispatch instant *and* window differ
  only in their manifest, which reaches no notification — same instant, same title, same body — so
  one alert is the right answer. Asserted as intended, with the two conditions that make it correct
  checked alongside it, so the day either stops holding the test says so.
- **The tilt parallax has been held in a hand and 0.4.3 is what came back** (2026-08-10). Davide
  reported two defects — *"horizontal tilt is very lazy, vertical is ok"* and *"after moving the phone
  ~20° it stops"* — and both were real: the sideways axis carried a `sin²(elevation)` gain out of the
  cross product, and the travel was clamped at one unit. Both are fixed, and fixing them turned up a
  theorem worth knowing before anyone asks for more: **no reading of the tip can be unmoved by a roll,
  monotonic through a full end-over-end turn, and a function of the current pose all at once** — not
  from gravity and not from a fused quaternion either. Davide picked roll-invariant, so the sideways
  axis turns without end and the vertical runs face-up to face-down and retraces. See `decisions.md`
  at 0.4.3.
  **Still arithmetic rather than measurement**, and unchanged on purpose so the next install measures
  the fix rather than three changes at once: 12° per unit of travel, 24dp on the reference plane,
  120ms of smoothing, and the 0.26 readability gate. There is still **no screenshot test of a leaning
  field**, because recording a baseline needs a machine that can run Roborazzi; the tilted draw path
  reaches `main` verified by compilation and unit tests alone.
  ~~The sign is no longer the likeliest thing to be wrong — a device has now confirmed both axes move
  the right way.~~ **Wrong, and 0.4.4 is the second install** (2026-08-10): *"vertical parallax is
  perfect, but horizontal is inverted."* The sideways sign had been backwards since 0.4.2 and the
  sentence above read a report about *magnitude* as clearance for *direction* — "horizontal is very
  lazy" says only that the axis barely moved, which is exactly the condition under which nobody can
  tell which way it is moving. **Fixing the magnitude is what made the direction findable**, so a
  second report was the expected outcome of 0.4.3 rather than a mark against it. One minus sign in
  `TiltMonitor.tilt`; the sky now goes towards the edge you drop. No baseline moved — desktop reports
  `Tilt.NONE`. See `decisions.md` at 0.4.4 for why no test could have caught it.
- **Yaw is invisible and always will be from this sensor.** Turning the phone left and right about the
  vertical is the movement most people reach for first, and gravity cannot see it — spinning a phone
  flat on a table does not move `down`. Answering it needs `TYPE_GAME_ROTATION_VECTOR` /
  `CMDeviceMotion.attitude` on both platforms. **Open, and Davide's call**: it is not implied by
  anything he has asked for, and the theorem above says the second sensor would not make the vertical
  axis full-circle either, so it buys yaw and nothing else.
- **The tilt is in the device's frame rather than the interface's**, so landscape swaps the two axes
  and mirrors one — a lean moves the sky diagonally where it should move it sideways. It degrades
  rather than breaks, and it is left alone deliberately: Android would read the rotation from
  `DisplayManager` in five lines and iOS has no equivalent that is not a main-thread UIKit call from
  inside a sensor callback, so writing the easy half alone is the cross-platform drift
  `:client:tilt:domain` exists to prevent. Both halves at once, with a device to check them on — and
  worth watching for on the TestFlight build above, since a phone that rotates is where it shows.
- **`ResourceRailScreenshotTest`'s two baselines fail to verify on a local macOS run** and did so
  before 0.3.0 touched anything — measured by stashing the whole branch and re-running against a
  clean tree. CI verifies on Linux and is green, so this is a recording-machine difference rather
  than a drift in the app, but it means a local `verifyRoborazziDesktop` is not currently a clean
  signal and the next person to record will silently rewrite forty baselines.
- **The Colony strip's desktop fixture still says `12 cargo`**, a ship class that no longer exists.
  Left alone deliberately: correcting it moves `fleet_strip` and four `colony_screen_*` baselines,
  and the screen slice rewrites that strip anyway to Design's ruling.

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
- ~~**Nothing has run the Android build on a device.**~~ — **run at 0.2.1**, on an emulator rather
  than a handset (nothing else was attached). Four of the five checks the entry above listed are
  answered, and the emulator is a real answer for them: they are about APK packaging, install
  semantics and a genuine Android boot, none of which a handset does differently.
  1. **The bundled font reaches the APK.** Renders in JetBrains Mono, and on the *signed release*
     build as well as the debug one — so CMP-9547's `androidResources { enable = true }` holds
     where it actually matters.
  2. **Edge to edge agrees.** The resource rail clears the status bar and the tab bar clears the
     gesture bar. The one thing an emulator cannot answer: a cutout or a punch-hole.
  3. **The save survives an update.** Installed the signed release APK, started two builds, then
     installed the same APK over the top: same colony, same two completion times, countdowns
     carried on. This is what the real key buys, and it is now measured rather than argued.
  4. **An alarm is booked correctly** — one `RTC_WAKEUP` per notification, aimed at
     `NotificationReceiver`, at the instant the card counts down to, with the id persisted in
     `SharedPreferences`. **Whether it fires, and whether the status-bar icon reads as a mark, is
     still open**; see the entry below.
  5. **`BootReceiver` survives a real reboot** — the riskiest of the five, and it holds. It starts
     as a broadcast process, does not crash, and the alarm is pending again afterwards.
- **What an alarm does when it fires is still unverified.** The receiver is `exported="false"`, so
  `adb broadcast` reaches it with zero receivers, and a Play-image emulator refuses `date`, so the
  clock cannot be wound forward — which leaves waiting out a real build as the only way in. That
  also means **nobody has looked at `ic_notification` on a status bar**, which is the one visual
  asset in this repository a cloud session drew and the one `decisions.md` says to overrule if it
  reads as a smudge.
- **The Android platform edge is excluded from Kover**, as of 0.2.1: the scheduler, its `Context`
  holder and the receiver join `MainActivity`, `OltreApplication` and `BootReceiver`, which were
  excluded when they landed. Left in, they failed the merge gate at 93.1%. The policy was already
  `decisions.md`'s — a platform edge with no seam a test can reach — but only half of it had been
  applied. **What replaces the test is the install above**, which is a standing obligation on a
  local session rather than something CI will ever catch.
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
