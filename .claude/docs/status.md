# Status

Updated: 2026-08-25 (0.20.1, plus `:protocol`, `:server` and `:client:net:data` — issues #107, #108,
#109 and #112, no bump)

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
  coverage falls below `min(last main run, 95%)` — and at 2026-08-12 that ceiling came off and the
  gate widened to *every* line and branch number in the table, so no value may go down. See
  `decisions.md` and the `test-coverage` skill.

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

- **0.7.0 the fleet reaches a finger (fleet arc, slice 1)** — the three client surfaces
  `fleet-sheet.md` §12 ruled on, built to the archived frames. **`startRun` had been in `core` since
  0.3.0 and nothing called it**: the balance existed, the save carried it, `advance` landed the
  cargo, and a player tapping a world got nothing at all. What was missing was the tap.
  `:client:galaxy:presentation` gains `DispatchUiState` and `DispatchSheet` — three controls in
  order of decreasing permanence (bring back, send, home in), one figure that is the only thing that
  moves when a control is touched, and the three lines that explain it. Two refusals, both reachable
  on a first check-in and neither an error: an unsurveyed world hands back a probe, and a fleet
  entirely away hands back a countdown. **No cost line and no affordability state** — a run is free,
  the hull was the price, and "cannot afford" is a Shipyard state.
  The world row moves to treatment **1b**: `Blocked` and `Barren` lead with metal and crystal
  richness because their verdict is not an offer, and the badge steps down into the sentence below as
  a `Blocked · ` clause keeping its axis, its band, its accent technology and its tap into Research.
  The distance band is stated **once** under the system header because it is astronomy — identical
  for all fifteen slots — while hazards stay on the rows carrying their own arithmetic, and nothing
  but the sheet ever prints the sum. The Colony strip names the next **event** rather than the next
  return, so an outbound skiff reads `On station at [3:185:4]`.
  Sixteen behaviour tests, five new baselines, and every existing galaxy baseline moved. What it did
  *not* touch: `core`, which needed nothing, and the frontier band, which belongs to slice 2.

- **0.7.1 the sheet is a sheet** — the one thing 0.7.0 got wrong, found on the first install: the
  dispatch sheet was a panel drawn inside the Galaxy tab, so it stopped above the tab bar, swallowed
  no drag and could not be swiped away. Every sheet in the app now shares one chrome,
  `OltreBottomSheet` in `:client:design:component`, and `RowSheet` and `DebugSheet` are its other two
  callers — the four lines of `ModalBottomSheet` configuration had been copied rather than shared,
  which is why a third sheet could imitate the drawing and miss the behaviour. `DispatchSheetContent`
  is split out on the shape `RowSheetContent` has, so the behaviour tests and the five baselines
  render the contents rather than the popup. See `decisions.md`; the regression test is a swipe that
  asserts the page underneath did not move.

- **0.9.0 the yard gets a clock and the hull gets a price** — Davide, having played 0.8.0: *"I
  think we need to add time to build ships, it shouldn't be instantaneous. Also I think ships are WAY
  to cheap, considered the benefits they bring back."* Two of 0.8.0's stated design decisions
  overruled in one message, and both are marked as overruled in `fleet-sheet.md` rather than edited
  away.
  **The yard is the sixth kind of job and the only serial one** — `GameState.yard` is a chained list
  rather than a slot, Davide's call, so a check-in can commit everything it can pay for and the yard
  works through it one hull at a time. One entry per hull rather than per order, because the price
  walks the curve hull by hull and the wait is taken from the price. The four costs 0.8.0 refused to
  pay were all real and are all paid: a sixth term in `advance`, `FutureEvent.ShipsComplete`, a rung
  at 250 in the tie-break ladder, and a **third unbounded kind** in the notification budget.
  `Event` gets the `ShipsOrdered` partner `ShipsBuilt` was the one member of the taxonomy to lack.
  Save schema **10**, one additive key.
  **The wait is `4 × √(metal + crystal) ÷ (1 + robotics)`**, both halves borrowed rather than
  invented — `PlaceholderBalance.MINUTES_PER_ROOT_COST` is the colony's own rate, so a hull and a
  facility that cost the same take the same time to make, and `integerRoot` moved to `Curves.kt`
  because two balance objects take a wait from the root of a price now. Nothing is gated; Robotics
  divides the wait without being a requirement.
  **The hull base went from 80/20 to 800/200** — tenfold, Davide's floor, the base and not the
  exponent on his call. The reading that was missing: at 80/20 the second skiff repaid itself in
  **three station-hours**, half of one six-hour run, for an asset that pays forever. It is thirty
  now, and the benchmark prints that ratio for the first time. **This gives round 17's guardrail
  back at the rate Davide insisted on**: the fleet-first player falls from 268% of their colony's
  crystal to **63.3%**, with `EXTRACTION_PER_HOUR` untouched. `balance-log.md` round 23.
  The Shipyard card takes `OltreCardState.RUNNING` and the probe's in-flight footer — an accent
  countdown, the wall-clock instant, `2 queued`, and the bar — **with the verb still live under it**,
  which is the one thing here that is not the Colony row's treatment: a busy facility cannot be
  started again and a serial yard can always take another order.

- **0.8.0 the fleet has a size (fleet arc, slice 3 — and the exploration sheet's Slice A)** —
  `buildShips`, the sixth verb, and the two tabs that stopped saying "nothing here yet".
  **`FleetBalance.shipCost` had been priced, tested and pinned in the benchmark since 0.3.0 with no
  production caller at all**, so a colony had the one skiff genesis granted and could never own two;
  the whole *"the natural fleet is three to four skiffs"* argument described a game that was not
  built. The verb charges and delivers in the same call — no yard job, because a fifth job kind would
  add a `FutureEvent` member, a tie-break slot and a notification id to put a second wait in front of
  the wait the mechanic is about, and the compounding price already does the bounding.
  `:client:shipyard:presentation` is a price list rather than a hero panel, per Design's sixth call:
  the pool on the card (`6 owned · 1 idle · 5 away`), one sentence naming what the hull is *for* so
  slice 4's "four berths at half the speed" lands as a trade, the Hauler as a dimmed card, and **the
  cannot-afford state this tab owns** — the metal chip reddens and the verb becomes a ghost carrying
  the wait, which is why the dispatch sheet has none.
  `:client:fleets:presentation` is one card per run with **three phases on one bar** and two hairline
  ticks, the phase derived in presentation from `dispatchedAt + flight` so `core` keeps storing one
  instant rather than three; under it a `Landed` ledger that is a fold over `Event.FleetReturned` and
  costs no state at all — the first player-facing use the event log has ever had.
  **`OltreTab.pendingWork` and `UnbuiltTabScreen` are deleted rather than nulled**: with five
  destinations built the column could only ever say "no". Twenty-eight unit tests, fourteen behaviour
  tests, nine new baselines and one retired.
  **It moves no balance number and spends a guardrail instead.** The sweep this slice owed found
  0.7.2's rate failing round 17's fleet-first test at 268%; the build lowered the rate, Davide
  overruled it, and 60 stands with the criterion retired rather than met — `balance-log.md` round 22
  and the Pending entry below.
- **0.12.0 a drawn map** — the Galaxy tab lands on the galaxy, drawn: 250 stars in ten banded regions,
  folded so path order is index order, on one 531dp Canvas that fits 393dp and 320dp alike. A caption
  under it is the map's one readout and its one control; a chip swaps four galaxy discs into the same
  frame; the orbit page is the tab's one real push. **The reach strip, the region index, the ledger's
  filter chips and the ledger's sort are all deleted** — see `drawn-map-sheet.md` for the diagnosis,
  which is that they narrowed a list of *worlds* when the question is about *systems*. `core` gains a
  `LAYOUT` generation axis (drift, size wobble, halo) so the sky is a property of the seed rather than
  of the renderer, and `:client:save:data` gains a second file: the one thing this tab persists is
  which of its two lists it lands on, which is Davide's amendment to Claude Design's call and
  deliberately breaks Design's own rule about the save.

- **0.15.0 the scout and the drive (issue #71, slices 1 and 2)** — `ShipType` grows to five and the
  research branch to five rows. **`SCOUT`** is the first hull that is not a fleet asset: no cargo,
  one verb, 200 metal / 50 crystal, and a probe now consumes one for its flight and gets it back at
  the landing. That is #83's ruling landing here, and the price is what keeps its severe companion
  concern from biting — the genesis stock covers the hull *and* the first probe. A skiff cannot
  survey and a scout cannot gather (`StartRunResult.NotAGatheringHull`). **`PROPULSION`** halves base
  flight speed and sells it back a level at a time, calibrated so drive 0 is half of 0.14's speed and
  drive 1 is 0.14 exactly; two galaxy hops leave the window ladder *empty* until it is bought, which
  is the frontier being bought rather than given. Speed only, not the hold — see the sheet's status
  block for why §2.3 expired. Schema 13, granting one scout per probe already in the air and no free
  drive level. The **hauler's price** is re-taken at 2,400 / 600 and the hold is counted in berths,
  but it is **not on sale**: see the pending note below. Balance-log round 30.
- **0.16.0 the player strip** — a fourth tier of chrome above the rail in a new `:client:player:ui`:
  a drawn mark, `Dead Reckoning`, a `LV 0` badge, a 72dp gauge and a settings gear that answers
  `Coming soon` for two seconds and clears itself. 38dp, one row, no `presentation` module and no
  `core` change — the name, the level and the experience are constants, and the sheet argues why a
  migration for a constant is the wrong trade. `DESTINATION_HEIGHT` 650 → 612 in the same commit;
  eight galaxy baselines and `main_scaffold` re-recorded. See
  [`player-strip-sheet.md`](player-strip-sheet.md).
- **0.17.0 the gauge fills** — experience and a level. **Inferred once, then stored**: the 15 → 16 hop
  folds a save's own `eventLog` into an opening balance, so a colony carried forward from 0.16 opens
  on the level it had already earned, and from there `GameState.experience` is a running total that
  `GameState.logging` — the one place anything may append to the log — pays into. Davide's call over
  the first cut, which folded on every read: *"the more the player progresses, the more it will be
  intensive to infer the level."* Completions pay and starts do not; a hull pays
  per hull and small; nothing reads a cost, a cargo or a stock. The ladder is a straight line —
  `1,100 + 360 × level` — because experience accrues linearly in time while Davide's marks are a
  power law, which the sim's new thirty-day experience report is what says. Lands on Lv 3 / 11 / 16
  / 25 against his 3 / 10 / 15 / 25. New `:client:player:presentation` (the module 0.16's build file
  said it would grow), an `[experience]` section on the balance benchmark, and **no baseline moved**
  — a new colony still reads `LV 0` on an empty track. It also fixed three of `:sim`'s four bots,
  which had silently stopped surveying at 0.15. See [`experience-sheet.md`](experience-sheet.md) and
  balance-log round 32.
- **0.17.1 a name above the rail** — the strip, revised by a Claude Design round trip. The gauge is
  no longer a 72dp inline track but the strip's own 2dp full-bleed bottom edge, which costs the row
  nothing and gives the name the whole line at 320dp as well as 393; and `Coming soon` is no longer
  printed over the badge but a card above the tab bar for four seconds, keyed on a tap count so a
  second tap restarts the window. The strip is 40dp now (38 of row over 2 of edge) and
  `DESTINATION_HEIGHT` does **not** move — 612 was already what the arithmetic gives with the edge in
  it, and `PlayerStripGeometryTest` states the whole sum. Five player baselines and `main_scaffold`
  re-recorded, two deleted. See [`decisions.md`](decisions.md) and the note at the top of
  [`player-strip-sheet.md`](player-strip-sheet.md).
- **0.18.0 ask once** — the first settings screen, built to the Claude Design sheet *Ask Once*
  (accepted 2026-08-23). The gear opens a modal bottom sheet with two controls on it: **Alerts**
  moves the question *tell me when this lands* from the job to the kind of job, and **Delivery** says
  how many notifications the answers arrive in. Under `By category` the seven bells replace every
  square in the app — except the price watch, which names a row rather than a kind. `One in total` is
  **one notification kept up to date** rather than one held back, which is Davide's replacement for
  the rule the design drew and measured as five and a half hours of silence. New colonies open on
  `By category · One in total`; a save from 0.17 keeps exactly what it does today, which is what the
  16 → 17 hop writes. The gate moved into `core` as `announcedEvents` because the sheet and the
  scheduler must not be able to disagree. `Coming soon` left the catalogue with `SettingsNotice`, its
  six measurements, its four-second window and three baselines. Two modules
  (`:client:settings:{ui,presentation}`), four new baselines. See
  [`ask-once-sheet.md`](ask-once-sheet.md) for the design and [`decisions.md`](decisions.md) for what
  implementation decided on top of it.
- **0.19.0 the in-game changelog** — built to Claude Design's *A Sky Per Build* (accepted
  2026-08-24). The settings sheet grew a second face: sixty-six releases, one page each, newest
  first, paged sideways, with the version and the date on every page and **a mark drawn from the
  version number itself** — `minor + patch` bodies on a golden-angle spiral over a world's limb,
  filled for the minor lines reached and hollow for the patches on the current one. It raises itself
  on the first launch of a new build and not again; a fresh install records the version without ever
  being shown it. Position is a rail with one tick per minor line, and it scrubs. The door from
  settings is a `BUILD` row that swaps the sheet's contents in 210ms rather than stacking a second
  sheet, which is why `AlertSheet`'s chrome wrapper is gone and the composition root raises the one
  sheet. Copy is two documents rather than 260 catalogue ids, in English and Italian, with three
  tests standing where the compiler cannot: the budget (40/90 characters), the translation pairing,
  and the catalogue against the README *and* `libs.versions.oltre`. Three modules
  (`:client:changelog:{domain,presentation,ui}`), five new baselines, and none of the existing 100-odd
  moved. See [`changelog-sheet.md`](changelog-sheet.md) and [`decisions.md`](decisions.md).
- **0.20.0 an hour ahead** — the third knowledge tier, built to the Claude Design sheet *An Hour
  Ahead* (accepted 2026-08-24, issue #84). **Charted stopped being free**: it is an interval per
  galaxy, `[lo, hi]`, widened by every landing to an hour of flight either side — thirty systems,
  derived from `SurveyBalance` rather than written down. Outside it a star is drawn as **grain**: one
  size, one flat value, no halo, no spike, no class, still selectable and still offering the probe.
  Genesis charts 61 of 250 in the home galaxy and nothing in the other three. The band label row
  carries an index range until the light touches the band; the region field is clipped to the charted
  stretch; the hour marks are deliberately not fogged. **Keyed on where a hull landed, never on what
  it found** — `surveyed` records findings and fog is about journeys, and the two disagree exactly
  where a system holds nothing. `MapStarInk` is sealed so an uncharted star cannot carry a class at
  all. Schema 17 → 18 folds the save's own contents, so a colony carried forward keeps the map it
  earned. Nine baselines (eight moved, `galaxy_map_uncharted` new). See
  [`fog-sheet.md`](fog-sheet.md).
- **`:protocol`, the wire before the server** — slice 0 of the online migration (issue #107 under
  epic #106), and the first module in the build that is neither `core` nor a consumer of it in the
  usual sense: a sibling of `core`, taking `core` and nothing else, carrying `core`'s target set
  because the JVM end is the server's and the other three are the client's. It holds the verbs as
  data, `VerbEnvelope`, `SyncRequest`/`SyncResponse`, `VerbRejection`, `RejectionReason`,
  `VerbRefusal`, `ApiError` and `ApiVersion` — **no I/O, no Ktor, and no knowledge that a network
  exists.** Nothing depends on it yet and nothing a player sees moves, so there is no version bump.
  **The verb count came out at twelve rather than the nine #106 names**: the epic was written at
  0.17.1 and 0.18's settings sheet added three more mutating functions to `core`, so the ticket's
  *"that is the complete list"* had gone stale in five days. `ClientVerbTest`'s hand-written
  registry and `offlineRule`'s `else`-less `when` are what replace trusting a count in a ticket.
  CI's `iOS framework` job gained `:protocol:iosSimulatorArm64Test`, because the module is in no
  dependency closure and its Apple half would otherwise have been compiled by nothing. See
  [`decisions.md`](decisions.md).
- **`:server` stops being a stub — the engine answers over HTTP** — slice 1 of the online migration
  (issue #108 under epic #106). `POST /v1/colony` founds a colony and **mints the galaxy seed**,
  which is the one responsibility that moves off the client at this slice (`GameSession.kt:60` until
  now); `POST /v1/sync` is everything else — the queued envelopes go up, the authoritative colony
  comes back, and what became of each verb comes back with it. Both take a `SyncRequest`, because
  founding a colony *is* a sync against a colony that does not exist yet. **The engine is not written
  here**: `advance` and the twelve verbs are `core`'s, unmodified, which is the bet `Main.kt` has
  been holding open since 0.0.1. The replay is the only real logic — check the key, clamp the claim
  into `[lastAcceptedAt, serverNow]`, advance, apply, keep it **only if `core` accepted it**, then
  advance to now and persist — and a refusal keeps nothing, including the advance it was judged
  against. Storage is a map behind a `ColonyRepository` whose four methods are shaped by the SQL
  #109 will answer them with; auth is a `X-Oltre-Player` header until #110. **`FRESH_WINDOW` is an
  invented number** (five minutes) and Davide's to move: #106 §3 says a galaxy-touching verb is
  look-don't-act and does not say how a server tells one sent live from one queued, and the clamped
  instant is the only evidence there is. `./gradlew :server:run` serves a colony playable end to end
  with `curl`. No version bump — nothing a player can do changes. See [`decisions.md`](decisions.md).
- **A colony survives a restart — three tables and a compare-and-set** — slice 2 of the online
  migration (issue #109 under epic #106). `PostgresColonyRepository` puts `players`, `colonies` and
  `applied_verbs` behind the interface slice 1 shaped, with `snapshot_json` holding
  `GameSave.encode` verbatim as `jsonb`; `schema.sql` is applied at startup and every statement in
  it is `IF NOT EXISTS`, because Cloud Run starts this process again on every scale-up from zero.
  **`ApiError.StaleColony` finally has something that produces it**: the interface widened so the
  read hands back a version, the write asserts it, and a `served()` that loses replays the whole
  attempt against the colony that won — up to three times, then `409`. That widening is a change to
  slice 1 rather than the drop-in its own comment predicted, and the comment was right about the
  other three methods. **Testcontainers is out and Zonky `embedded-postgres` is in** (Davide,
  2026-08-25): there is no container runtime on Davide's machine, an unqualified `./gradlew check`
  runs every category, and a suite that cannot run locally stops being run. The whole thing starts
  two real PostgreSQL 17.10 instances and finishes in about three seconds, on this machine and on
  `ubuntu-latest` alike, so `ci.yml` does not change. **#106 §6's "the same code modulo a driver" is
  corrected here** rather than preserved by writing portable SQL — the exit to SQLite is a second
  implementation of `ColonyRepository`, a class rather than a line. `./gradlew :server:run` with no
  `DATABASE_URL` still serves an in-memory colony and now says so in the log; **#111 is what sets
  that variable**, and a deployed server that fell back silently would lose every colony it was
  handed. No version bump — nothing a player can do changes. See [`decisions.md`](decisions.md).
- **The client learns to ask — the network layer, the outbox and the fake that keeps the suite
  green** — slice 5 of the online migration (issue #112 under epic #106). `:client:net:data` holds
  `OltreApi` and its Ktor implementation, an outbox that writes every queued verb to a file before
  the call is made, idempotency keys minted at the edge as 128 random bits, and `ColonySync`, which
  is the only thing above the transport that decides anything. **The queue-or-refuse split is read
  off `ClientVerb.offlineRule` in exactly one place** and never re-derived, which is why the twelve
  verbs are twelve rather than the nine #112's own table lists. `act` asks once and `sync` retries
  with a bounded backoff — the outbox has already taken the verb, so a second attempt buys a colony
  four seconds later and nothing else, with the screen waiting. **`ApiError.StaleColony` is answered
  by asking again and never by saying anything**, which is what #109 gave it a producer for; every
  other error in the taxonomy is terminal and returns at once. A `5xx` whose body is not an
  `ApiError` reads as `Unreachable` rather than `Malformed`, because Cloud Run scales to zero and the
  first request after an idle spell can be answered by a load balancer that never saw the colony.
  `:client:net:data-testing` lands here rather than at #113 and that is load-bearing: `App()` is
  about to require a network and the whole behaviour and screenshot suite runs on the desktop target.
  **`Protocol.PLAYER_HEADER` moves into `:protocol`** — a wire string spelled out at both ends is one
  that can differ at both ends, and a header the server does not recognise reads exactly like a
  player who never signed in. **`.claude/tools/gradle-without-agp.sh` had drifted from two real build
  files and was fixed first**, because a compile error in a module it claims to cover cannot be told
  from a session's own breakage. Coverage: the integration half of the new-module drop was answered
  with a real-socket `…IntegrationTest` over a JDK `HttpServer` rather than a filter, and the
  behaviour half with an exclusion Davide approved on the report, which comes out at #113 with
  `:protocol`'s. No version bump — nothing a player can do changes. See
  [`decisions.md`](decisions.md).


## Roadmap — v1 in vertical slices

The v1 feature set from Notion is *3 resources, 6 buildings, 4 ship types, one research branch,
a large procedurally generated galaxy, 3 AI empires, local notifications, JSON snapshot save*.
**Read as scope, never as a ceiling** — Davide, 2026-08-16: *"Notion stuff is now very ancient."*
A fifth, survey-only ship type was called that day; see `brief.md`'s supersession note.
Four of the eight are done. What is left, decomposed into slices that each end playable —
**sequencing is the agent's (per the Notion hand-off), the content is Davide's**:

| # | Slice | Ends with | Needs a design call first |
|---|---|---|---|
| ~~1~~ | ~~**Tab bar**~~ | Landed at 0.0.11 | — |
| ~~2~~ | ~~**Research: core**~~ | Landed at 0.0.12 | — |
| ~~3~~ | ~~**Research: screen**~~ | Landed at 0.0.12 | — |
| ~~4~~ | ~~**Galaxy: procgen**~~ | Landed at 0.0.15 | — |
| ~~5~~ | ~~**Galaxy: screen**~~ | Landed at 0.0.15 | — |
| ~~6~~ | ~~**Shipyard**~~ | ship set answered and `core` landed at 0.3.0; the **tab and `buildShips`** landed at 0.8.0 | — |
| ~~7~~ | ~~**Fleets: outbound**~~ | `core` landed at 0.3.0 — travel-time formula settled, **no fuel** (Davide, 2026-08-10); the dispatch sheet at 0.7.0 and the **Fleets tab** at 0.8.0 | — |
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

- **DAVIDE'S CALL, RULED: the rate stays at 60 and round 17's guardrail is spent.** 0.8.0 built the
  Shipyard and ran the sweep `exploration-rewards-sheet.md` §6.4 said could veto the rate. It vetoed
  it — **at 60 a fleet-first player's fleet delivers 268% of their colony's own crystal**, against
  89% at 20, and 30 is already 134% — the build took the rate back to 20, and Davide overruled it:
  *"Why did you revert the rate? Bring it back."*
  **The number is not the decision; the criterion is.** Nobody disputes the 268%; what has been
  rejected is round 17's rule that *"the fleet must never be the economy"* is what sizes this
  constant. A later round wanting the rate lower argues against Davide's bar, not round 17's — and
  should reach for the **hull curve** first, since that decides how many hulls the 268% is spread
  over. `balance-log.md` round 22 has the bracket and the argument the build lost.
  **What settles it is an install, not another sweep**: the 268% assumes a player who buys hulls
  before buildings at *every* check-in, and if nobody plays that way the honest reading is 94.5%. The
  failure shape to watch for is not a number — it is the mines starting to feel optional.
  **RESOLVED AT 0.9.0, and by the dial this entry pointed at.** *"A later round wanting the rate
  lower … should reach for the hull curve first, since that decides how many hulls the 268% is spread
  over."* That is what happened: Davide's tenfold base raise took the fleet-first player to **63.3%**
  and the from-what-is-left player to **36.4%**, both under round 17's bar, with the rate untouched.
  Round 22's trade — a guardrail for a rate — turns out not to have been the trade on offer. Davide's
  ruling still stands as the thing that *sizes* the rate; what has changed is that nothing violates
  round 17's rule any more, so a future round has nothing to reinstate. The install this entry asks
  for is still owed, and the question it settles is now the opposite one: whether the opening feels
  **slow** rather than empty, since a new colony can no longer buy its second skiff on day one.

- **`:sim:run` was dead from 0.7.2 to 0.8.0 and nobody noticed.** Round 21 inverted the danger term
  in `core` and left the harness's replica subtracting; the `check` between them fired on the first
  dispatch and killed the whole report. The discipline worked — a loud failure rather than a quiet
  disagreement — and then the round shipped without running it. **A balance round that does not run
  the harness is how a broken harness survives a release.** Fixed at 0.8.0, along with four sweep
  tables that had been printing grids not containing the shipped rate since round 21.

- **0.7.2 shipped the sheet's Slice B, 0.8.0 shipped Slice A, and Slice C is still owed.**
  `EXTRACTION_PER_HOUR` 20 → 60 and staying there, danger inverted from −10% a point to **+35%**,
  `FRONTIER_PERCENT` deleted rather than wired, and the Shipyard built. **What is still not built is
  the drive technology, so Davide's *"travel towards far planes to be way more time consuming, and
  require upgraded fleets to get there faster"* is untouched in both halves** — a galaxy hop is still
  9h 20m round trip at a speed nothing can improve. The benchmark's `[frontier]` section is the
  reading that says whether the inversion worked, and it survives the rate coming back down: four
  rows that were all below 1.00 by construction read 1.00 / 1.19 / 1.32 / 1.10, and the sim's band
  spread holds at 13 of 56 dispatches past the home system against **0 of 56** before 0.7.2.

- **THE SHEET IS STILL THE PLAN, and it is waiting on Davide's calls:
  [`exploration-rewards-sheet.md`](exploration-rewards-sheet.md).** Davide played 0.7.1 and reported
  the loop dead — *"exploring other planets is way too little rewarding… I grinded to upgrade
  Thermal, to travel 3h, and 14 cristals lol"*, and *"now it not rewarding AT ALL, like 1 to 10 →
  minus 50."* The sheet is the 0.8 design answer: **danger stops subtracting and starts multiplying**,
  travel to the frontier gets longer, and a **drive technology** buys it back — the fleet's first
  growth term of any kind. Six open calls in its §8, none of them answered yet.

  **Three findings from writing it are worth knowing even if the sheet is overruled entirely:**

  1. ~~**`buildShips` does not exist, so a player owns exactly one skiff forever.**~~ — **built at
     0.8.0**, which is the sheet's own Slice A and `fleet-sheet.md`'s slice 3. `shipCost` had been
     priced and pinned since 0.3.0 with no production caller, so the hull curve, the Shipyard tab and
     *"the natural fleet is three to four skiffs"* all described a game that was not built. **The
     sheet reordered `fleet-sheet.md`'s plan to put the Shipyard first** because every constant it
     proposes is a *per-hull* rate, and that ordering is what let round 22 size the rate against a
     fleet rather than against a single ship.
  2. ~~**Round 17's guardrail cannot be tripped by any shipped player.**~~ — **it can now, and it
     is.** `EXTRACTION_PER_HOUR = 20` was sized on *"a fleet-first player must not out-produce their
     own colony"*, measured against a bot owning six to nine hulls; 0.7.2 tripled the rate on the
     grounds that no such player existed. 0.8.0 made one exist and the guardrail bit immediately —
     268% at rate 60. See the open call at the top of this section.
  3. **Hostility has never gated a dispatch, and nothing on screen says so.** `StartRun.kt:56-66` and
     `DispatchUiState.kt:119-201` both check survey, hulls and window and neither reads a verdict — a
     `Blocked` world has always raised a full `Offer`. Davide spent two days on Thermal 1 to reach a
     world he could already reach. That is a legibility failure, and it is the cheapest item in the
     sheet (§2.5).

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
- ~~**THE NEXT THING TO BUILD: the screens the fleet already has a design for.**~~ — **done at
  0.7.0**, all three surfaces, to the archived frames. `startRun` had been in `core` since 0.3.0 with
  nothing calling it from a finger; the tap that was missing was the world row's. The Shipyard and
  Fleets tabs are slices 3 and 4 of the same arc and are still unbuilt.

  **Three things that treatment 1b subtracted, listed because they are content that shipped and
  they are Davide's to put back:** `Blocked` lost its `yield 1.06` and its `Fails 2 of 3 bands,
  worth it at 0.92` calibration line (both added at 0.0.18); `Home` lost its yield and its hazards;
  `Settleable` lost its deuterium richness. The design's argument in each case is that the slot went
  to something the player can act on *today* — and for the third, that a run may never carry
  deuterium, so on a screen whose other rows are now priced for a fleet it was the one richness with
  nothing to compare against. All three are pinned as absences in the tests, so a row that quietly
  grew one back would fail rather than drift.

  **The frontier band is still not wired in**, deliberately: `FleetBalance.FRONTIER_PERCENT` was
  decided at 0.3.0 and belongs to slice 2, so `cargo` is still flat and the dispatch sheet omits the
  `frontier ×1.15` line the design draws. A sheet that printed a multiplier nothing applies would be
  the screen lying about the arithmetic it is there to explain.

  **Two abbreviations were authored that the design did not specify**, both measured rather than
  guessed and both flagged here as overrulable. The astronomy line drops `from here` when the whole
  line would exceed 54 monospace characters — which is the home system, where a *range* of round
  trips is stated, and any target in another galaxy, where the distance is four digits and the flight
  is hours; without it the screen every player opens on wrapped to two lines by two dp. And at 320dp
  the world row's header drops the *lesser* of the two richnesses rather than ellipsising a figure,
  which is the rule 0.5.0 already applied to the yield.
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

- **0.10.0 worlds run out (the deposit milestone)** — every world holds a finite metal deposit and a
  finite crystal deposit, drained at dispatch and refilling at 5% of cap a day. `DepositBalance` is a
  new object rather than a section of `FleetBalance`, because another session was editing that file;
  `GalaxyState` gains a sparse `deposits` list where **an absent entry is a full world**, which is
  what answers `fleet-sheet.md` §8's objection that a counter per world is a save without bound —
  `advance` prunes an entry the moment it refills, and the sim measures 50 entries after a fortnight.
  `startRun` clamps and debits at dispatch. Schema 11. The row lost its richness pair to a deposit
  line and the verdict badge came back on all six verdicts; the dispatch sheet gained the clamp
  marker, two earned notes, a `working` leg and a `waiting` mode. `Technology.PROSPECTING` is the
  fourth applied technology, and `LevelPurpose.Haul` exists because the generic purpose would have
  told a player the level "does nothing while you are in surplus". See `.claude/docs/deposit-sheet.md`
  and `balance-log.md` round 24.

- **0.10.1 the hull price goes flat** — Davide, having played 0.10.0: *"Why is skiff pricing
  increasing at every buy? This is wrong."* Offered four shapes he took a flat price, with the
  consequence named in the option: it deletes the game's only bound on fleet size. A skiff is 800
  metal / 200 crystal at every depth; `alreadyOwned` is **removed from the signatures** rather than
  kept and ignored, because a live parameter is how a curve comes back without a decision, and
  `GameState.committedShips()` goes with it since pricing was all that read it. The wait went flat
  too — it is taken from the price — so every hull is 2h 04m at Robotics 0 and **the serial queue is
  the ceiling**. What broke the curve was 0.9.0's tenfold base: that call was about which end was too
  cheap, but a base x10 multiplies every rung, so a bite meant for the eighth hull arrived at the
  second. Measured cost of the change, `:sim:run`: the greedy bot reaches **300 hulls in a fortnight**
  against ten, delivering 2% of the colony's metal — the fleet becomes a metal sink rather than the
  economy, and the colony pays ten building levels for it. See `balance-log.md` round 25 and
  `decisions.md`.

- **0.11.0 the map gains a name** — Davide, having played 0.10.1: *"I'm so unhappy with the map. It
  is huge, but terrible to navigate! Finding a planet feels like searching a phone number on pagine
  gialle in the 90s"*, and *"the map should gain 'an identity'"*. The whole of
  `galaxy-identity-sheet.md` plus Claude Design's return.
  **`core`**: ten named regions a galaxy, each biasing its stars, drawn as a permutation of a fixed
  multiset so the pooled mix is identical for every seed; generated names for every system, world and
  region, unique inside a galaxy by construction; derived two-word epithets; a decorative ring on one
  world in two hundred; pins on `GalaxyState` at schema 12. Nothing else stored — names, epithets,
  portraits and regions are all regenerated from the seed.
  **The screen**: the tab now opens on a ledger of what you know, with filters, a sort, a search and
  a pinned section; a row leads with the name and a drawn planet disc and the word `Unsurveyed` is
  gone; the strip gained nine region breaks and five named cells; there is a region index; and a
  survey lands as a card at the top of the ledger.
  **The reroll**: every existing map changed. Seeds and home coordinates are kept, so a colony stays
  where it is and its surveys survive; what moved is what the stars are. Announced in the changelog.
  See `decisions.md` and `balance-log.md` round 26.

- **0.11.1 a ledger row opens the world it names** — Davide, on 0.11.0, twice: *"it says full, but if
  I tap it says deposit is empty"*. Two different worlds. A row handed its tap only a slot number and
  the sheet completed the address from whichever system the *map* was on — which in the ledger, the
  view the tab opens on, has nothing to do with the row. Tapping `[3:174:6]` priced `[3:177:6]`, the
  same slot of home, and `Dispatch` would have sent the run there. The whole `GalaxyCoordinate` now
  travels from the row through `DispatchSelection` to the offer, and `toDispatchUiState` no longer
  takes a `SystemSelection` at all — there is nothing left for a page to complete. Two tests were
  agreeing with the defect: one asserted `run.at.slot`, the one third of the address that was never
  wrong, and one asserted an absent rung against a sheet that was refusing outright, where every rung
  is absent. Issue #74, PR #73.

- **0.11.2 the vein was sized for one ship** — Davide, having played 0.11.0: *"I'm so much out of
  planets to gather resources from, I think we set the resource limit way too low!"*
  `DepositBalance.BASE_PRICED` goes **1,450 → 5,800**; refill stays at 5%/day; nothing else moves.
  **The defect is a composition of two earlier calls and neither was wrong on its own.** Round 24
  derived the cap exactly from a rule about *one basic ship*, and 0.10.1 then deleted the only ceiling
  on hull count — so the constant kept a premise the game no longer had. Davide's ruling was dial 1 of
  three: re-derive against a fleet, *"a typical fleet takes about two runs"*, band 4–6×, and sweep
  before shipping a number. 5,800 is the same arithmetic with a four-hull manifest substituted for the
  ship — 1.02 days, 2.07 runs at the 12h window — so `workingTime` for four hulls is still 1,450
  minutes everywhere and a lone skiff now takes four days. **4× rather than 6× because the decided
  reading saturates and a second veto does not**: worth-it worlds standing at hour 48 hits 6 of 6 by
  3×, while the share of dispatches the *vein* stops falls 48.8% → 12.1% at 4× and 7.7% at 6× — and a
  cap that drives that to zero deletes 0.10.0's mechanic rather than tuning it. It also lifted fleet
  income from **15.3% to 29.1%** of colony metal, clearing a §9 veto that had been failing unread since
  0.10.1. `:sim:run` grew `printStandingTable` for the deciding reading. See `deposit-sheet.md` §2.5.1
  and `balance-log.md` round 27.

- **0.11.3 genesis stops granting a skiff** — Davide, 2026-08-12 (issue #55): *"We should remove the
  default ship also, and allow the user to build them instead."* `GameState.initial` goes
  `Ships.of(SKIFF, 1)` -> `Ships.NONE`, and **no balance constant moves.** **The issue's own safety
  argument was two releases stale and that is the finding**: it priced the first hull at 80 metal /
  20 crystal and concluded the opening stock buys it *"with 420 metal to spare"*, but 0.9.0 raised
  the base tenfold the day after the issue was written — 800 metal against a genesis 500 — so the
  promised *buy, then send* is not available in the first sitting. Davide's call on being shown the
  arithmetic, 2026-08-15: **land it as-is, the hull is earned**, rather than raise the opening stock
  and undo 0.9.0 at the one place 0.9.0 was aimed. The cost, measured: the fleet-second player's
  first 48 hours go from 8 dispatches, 75% duty cycle and 17.0% of metal income to **zero of all
  three** — a colony that buys facilities first never has 800 spare in two days — and they get two
  building levels back for it. The fortnight barely moves (56 -> 46 dispatches, duty cycle and income
  share identical), so this is **entirely an opening change**. The 7 -> 8 migration hop still grants
  a skiff and is now the only place in the game that does, deliberately: it records what a save was
  carried through, and rewriting it would confiscate a hull from every colony already migrated past
  8. Closes `fleet-sheet.md` §9's open call, in both directions at once. See `balance-log.md` round
  28.

## Pending, from 0.20.0

- **Nobody has held it, and the whole design is a claim about how a dark map feels in a hand.** The
  test `fog-sheet.md` §7 sets is whether a report comes back naming a **heading** — *"I have been
  pushing up-galaxy"*, *"I went the wrong way and there is nothing over there"*. A report that the map
  got smaller is the failure, and the first lever then is the hour of grace: thirty systems is the one
  number in this design that is a matter of taste. Same shape as the tilt loop in `session-roles.md`.
- **The design's empty-system frequency was wrong by about forty times and two of its arguments leaned
  on it.** *"About one system in eight is empty"* — measured, it is **18 of 6,000, one in 333**, which
  `Galaxies.kt` already documented as one in 390. Both arguments survive on their other legs; the
  reckoning is in `fog-sheet.md` §3. Worth carrying forward as a habit rather than as a correction: a
  frequency in a design sheet is cheap to check and nobody checked this one.
- **The distance-scaled probe price is ruled and not built** (#83). Design's call is fog first, the
  curve immediately after and not in the same release, because fog changes what the curve is pricing —
  a far probe now buys a survey *and* a stretch of map, so the curve's job becomes keeping
  metal-per-system-charted roughly flat.
- **Nothing has measured what fog does to probe-spam.** Design's estimate is that it *reduces* it — the
  fog-motivated probe is always the longest flight you can afford, so it parks your only hull for
  hours and blocks the short ones — but `:sim:run` has not been run against this build, and surveys
  were 36% of a simulated month before it.
- **A charted star past your furthest landing also extends the light and its caption does not say so.**
  Left silent deliberately. If players stop pushing once the names run out, that clause is the first
  thing to try.


## Pending, from 0.19.0

- **The self-raising sheet covers the arrival, on the one launch per release where it fires.** The
  colony composes behind the scrim, so the resource roll and the completion sweep for anything that
  landed while the app was closed play out and are consumed while nobody can see them — both are
  latched by *composition* rather than by visibility. Nothing is lost or misstated (the settled
  numbers and levels are all correct when the sheet is dismissed) and no state is left stuck, so this
  is a manner-of-arrival cost rather than a defect; it is here because it is exactly the collision
  the design sheet never considers, and because the fix — holding the arrival until the sheet is
  gone — is a design call rather than a patch.
- **Nobody has swiped the sheet on a phone.** Sixty-six pages of `HorizontalPager` with a Canvas on
  every one of them is the first thing in this app whose *cost* is a question — desktop composes
  three at a time and never flings with a finger. The rail's scrub is the other half: it is the
  galaxy caption's gesture on a 2dp track, and 44dp of row is arithmetic rather than a measurement.
- **The mark has never been seen at 29dp on a real screen.** At page size every body is separable by
  a wide margin; the build row's mark is texture by design, and whether it reads as a mark or as
  grit is a thing an eye decides.
- **A player who skips three builds still sees only the newest.** Design raised it and did not decide
  it; the integer it would need is already stored. See `decisions.md`.
- **The 0.17 settings frames are stale by ~170dp** — drawn at 573dp of content against a full-height
  sheet — and the build row lands in exactly that space. The next redraw of that sheet is one change,
  not two.

## Pending, from 0.18.0

- **`One in total` is one stack on iPhone, not one notification**, and this is the first thing to
  look at on a device. Android's tray id genuinely replaces what is showing; iOS runs nothing in the
  background, so nothing can retract a delivered notification and the closest available is a
  `threadIdentifier` that collapses the run into one group in Notification Centre. The delivery
  target is the platform that cannot do it. See `decisions.md`.
- **Nobody has opened the sheet on a phone.** It is 573dp at 393 and 648 of the 652 a Slide Over pane
  has, both of them the design's arithmetic rather than a measurement — and the sheet scrolls now, so
  what a short window costs is a scroll rather than a clip.
- **Nothing on the sheet reflects a muted system.** A player can switch all seven on and hear
  nothing, which is the one state this screen lies about. The permission slice is the design's
  deliberate next one.
- **The title's `+n` compacts by kind rather than by character**, because a character budget cannot
  be spent on an unresolved `TextRes`. Two kinds reproduces both of the design's drawn examples; what
  a lock screen actually holds is a device measurement nobody has taken.

## Pending, from 0.17.1

- **The edge gauge on a wide window is undrawn and unlooked-at.** It is full-bleed like the hairline
  it replaced, so on a 1024dp iPad a level 62% through is a 635dp accent line and the only ink of
  that colour on screen. Capping it at the 560dp content column is the alternative and contradicts
  every other bar in the app. One device session settles it.
- **A line pinned under a bar reads as loading**, and three things answer that on paper — it never
  moves, LV 0 draws no fill at all, and it is the chrome's own boundary. Paper is what they are.
- ~~**The notice's position over an open bottom sheet is undrawn.**~~ — **closed at 0.18.0, by
  deletion.** The notice is gone: the gear opens a sheet now, so there is nothing left to position
  over one.

## Pending, from 0.17.0

- **Nobody has held it, and the whole curve is fitted to a bot.** What a device answers is whether
  Lv 3 on the first evening reads as *earned* or as *given*, and whether 16 → 25 across the back half
  of the month reads as a plateau. The dial for the middle is `LEVEL_STEP`. Same shape as the tilt
  loop — see `session-roles.md`.
- **The level does nothing, deliberately.** Nothing is gated on it and nothing is unlocked by it.
  Whether it should stay a record is Davide's; `experience-sheet.md` §5 names the two shapes worth
  putting to him and which this game's own evidence prefers.
- **A level-up is not announced.** The badge changes and the gauge resets, and there is no notice, no
  sound and no sweep. That has a visual half, so it is a Claude Design question rather than one a
  session should answer at the keyboard.
- **Probe-spam is the one grind vector.** A survey pays the dearest base in the table, scouts come
  home, and a player who buys ten runs ten concurrent probes. Surveys are already 36% of the sim's
  month even capped at one scout. The dial is `SURVEY_BASE`.

## Pending, from 0.16.0

- ~~**Nothing fills the gauge.**~~ — **closed at 0.17.0.** A fold over the event log, which is what
  this entry guessed it would be, and it needed no schema hop at all.
- **The gear has nothing behind it**, by design this slice. What a settings screen holds is undecided;
  the language is not in it (`TranslationsFor`).
- **Nobody has held it.** The strip is 38dp of chrome that every screen now pays for, and whether
  that reads as a frame or as clutter is a question a phone answers. Same shape as the tilt loop.

## Pending, from 0.15.0

- **The hauler ships, and slice 4 is closed** — Claude Design's *Twice the Flight* came back and is
  built: one stepper on berths, a two-cell hull row, and a rung the mix removed drawn at 42% with its
  requirement rather than absent. See `decisions.md`. Three things it raised and did **not** decide
  are open: the two clocks in the system header's astronomy line, a run card that needs to print a
  mixed manifest, and whether `:sim:run` agrees that the hauler pays at only one rung on a doorstep
  world.
- **Nobody has installed 0.15.** The drive's whole case is that a level feels like a door opening,
  and that is a claim about a hand holding a phone. Balance-log round 30 lists what to watch; the
  sharpest is whether a real player buys Propulsion at all — the benchmark's bot does not, because it
  buys cheapest-first and this is the dearest row on the screen.

## Pending, from 0.13.1

- **The four hold-to-repeat timings on the dispatch stepper are invented and marked as such** —
  350ms before a held stepper starts running, then 120ms ramping by 15 to 25, in
  `:client:dispatch:domain`'s `StepperHold`. They are arithmetic and not measurement, and the
  motion-tuning precedent in `session-roles.md` says plainly that a hand is what decides them.
  **The first install is the test.** If the rest is too long the control feels dead under a thumb; if
  the ramp is too eager the number runs past what you were aiming at, which is the failure mode that
  costs a tap back. `StepperHoldTest` pins the *shape* rather than the values, so a tuning pass may
  move all four and leave every assertion standing.
  **The ramp is 15 rather than 10 because a test said so**: at 10 it bought 47 steps in two seconds
  against the 52 that 55-down-to-3 needs. That is what the module is for — see `decisions.md`.
- ~~**The dispatch stepper's gesture is covered by behaviour tests and by nothing else.**~~ —
  **closed 2026-08-17.** A screenshot renders, it does not press, so the gesture cannot be reached by
  that kind of test at all. Davide widened the screenshot pass's filter to name `StepperGesture.kt`,
  which exists as a file for that reason: one `Modifier` extension and nothing that draws. With the
  entry in place the screenshot pass reads identically to `main` in every digit, which is the
  evidence that it excludes what this branch added and nothing else. See `decisions.md` for what the
  next entry has to demonstrate.
- **Nothing has yet felt the suggested manifest on a device.** The arithmetic is pinned to the hull
  and the frames agree, but what nobody can check from here is whether opening on *3 skiffs* out of
  55 reads as the app being helpful or as the app having lost your fleet. The pool line beside the
  label — `of 55 idle` — is the whole of the answer to that, and it is one small grey string.

## Pending, from 0.12.0

- **0.12.1 put the caption back on the screen, and the way it was lost is the finding.** The fold
  claimed a fixed 531dp; a phone leaves a destination about 650 once the 55dp resource rail, the 52dp
  tab bar and two safe-area insets are paid for; and 531 + 22 + 58 does not fit in 650. So the bar
  that is the map's *only* control went off the bottom — Davide, on the TestFlight build: *"I'm
  tapping on the systems, but nothing happens."*
  **The suite already asserted the thing that broke.** `the fold and the bar under it are on one
  screen at both widths` was written with the slice and passed all the way through review, because
  the harness handed the page the whole 852dp window. Nothing was wrong with the assertion; the
  *harness was describing a device that does not exist*, which is worse, because it makes every frame
  in the suite agree with it. `DESTINATION_HEIGHT` is a measured figure now and the fold folds into
  whatever height it is given. **The general lesson: a screenshot taken without the chrome the screen
  actually lives inside is not a photograph of that screen.**
- ~~**Nobody has seen the fold on a device.**~~ — **held, 2026-08-16, and it works.** It is measured
  at both widths and it is inside the geometry by construction, which is not the same as being
  readable: the pitch is 14.0dp at 393dp and 11.0dp at 320dp, and a star is 2.6–5.2dp across. The test
  the design set is whether a player can say four things out loud after five seconds on the day-one
  frame — *I live in a dark region; the bright places are three bands up and three bands down; I am
  near one end; that one is an hour away.* If what comes back instead is "it looks like a spreadsheet
  of dots", **the lever is the band gap before it is a different shape.**
  It did not come back as a spreadsheet of dots. Davide, unprompted, on the TestFlight build:
  *"The last map rework is working great by the way! I really feel like a sense of progression, and
  the named system is also very nice!"* — and, as the two things he could say about it: *"I can see
  'oh, this is the area I unlocked with my own actions'"* and *"'ah yes, [system A] is the last one I
  gathered resources from! Let me go there again and see whether it still has resources'."*
  **What came back is not the axis the test was written on, and that is the finding.** §8 predicted
  the win would be *spatial legibility* — where the bright places are, how far away one is. What
  actually arrived is **ownership and memory**: the map as a record of what he did, and as a prompt to
  go back. Neither of his sentences is one of the four. So the fold succeeded by carrying **0.11.0's
  identity layer** — names, regions, a surveyed set that reads as territory — rather than by the
  legibility claim the drawing was argued on. The band gap is not the lever it was expected to be, and
  §8's four-things test should be read as unproven rather than as passed: nobody has yet reported
  reading a *distance* off the fold.
  Recorded because it points a live decision: **#84 (hide unexplored space) is judged against
  ownership-and-memory, not against legibility.** Fog serves the axis that actually landed — more of
  the map becomes something you unlocked — where it would have threatened the one §8 predicted.
- **The caption's ghost dispatches a probe on one tap, for 150 metal.** That is the same verb the
  orbit page's footer has always had, but the footer is reached deliberately where the caption sits
  under a surface you scrub with a thumb. Davide's call whether it wants a confirmation.
- **The worlds list is nearest-first and has no other order.** Right for a list of places you already
  hold; possibly wrong at forty worlds, which is the size the ledger was designed for.
- **The coverage table is what caught the two worst defects in this slice, and neither was a test
  failure.** Thirty-four lines of `SystemMap` stopped being executed by anything at all — the probe
  arc on the home orbit page was covered *incidentally*, because the behaviour suite used to dispatch
  from that page's own footer and stay there, and the map is where a probe is aimed from now. And two
  ledger frames were silently photographing the map, because `frame(...)`'s default view changed
  under them. Both are now frames of their own. **A green suite said nothing about either.**
- **`galaxy_ledger_empty` was retired rather than re-recorded.** It was built from a filter set that
  no longer exists, and the emptiness it photographed — *"no world matches all three"* — is a state
  the screen can no longer reach.

## Pending, from 0.11.3

- **Nobody has held this opening.** The whole change is a first-sitting change and the two readings
  that bracket it are 30 hours apart: a colony that saves for a hull can order one at **hour 4**, and
  the benchmark's greedy player does not afford one until **hour 34**. Which end a real player lands
  on is a preference no bot in this repository has, and it decides whether the opening reads as a
  decision or as three hours of nothing. **The dial if it reads wrong is the opening stock, not the
  hull price** — raising the stock keeps 0.9.0's ratio everywhere except the sitting this release
  emptied, and it is the option Davide declined on paper and can take back after a device says so.
- **The Fleets tab now opens on `0 of 0 away` and the Shipyard on `0 hulls`.** Both fall out of rules
  that already existed and neither needed new copy, but they are states no player could reach before
  and no design was drawn for them. If the empty Shipyard wants more than a price list, that is a
  Claude Design prompt.

## Pending, from 0.11.2

- **Reach is untouched and is the next complaint if this one was really about the map.** A player on
  the 3h rung still reaches **six worlds** at every cap in the swept grid; a deeper vein multiplies
  what those six hold (480 → 1,955 metal a day) and cannot make a seventh exist. That is the drive
  technology, issue #71, named as dial 3 in issue #68 and unbuilt.
- **The dispatch sheet's legs line now wraps on a full-fleet ask**, because `working` reads in hours
  where it read in minutes. Nothing clips and it is legible, but it is a design-owned line that a
  balance change moved — Davide's or Claude Design's call whether to leave it.

## Pending, from 0.11.1

- **The dispatch sheet throws on a world three galaxies out** — issue #75, found while reviewing this
  fix and left open with Davide's call. `windowsFor` narrows the ladder to nothing at a 27h round
  trip and `defaultRung()` calls `last()` on it. Reachable only from a home in G1 or G4, so not on
  the current seed. What it needs is not a guard but an answer: whether the far galaxy is
  reachable-but-not-worth-it or out of a skiff's range at all.

## Pending, from 0.10.1

- **Nobody has held a flat-priced fleet.** The 300-hull reading is a bot that buys while it can pay,
  which no person does; the `hulls from what is left` column is unchanged from 0.10.0 at every rate.
  Whether the mines start feeling optional is a device question, and if they do the lever is the yard
  or the base — **not a restored curve**, which is the shape Davide ruled on.

## Pending, from 0.10.0

- **The two placeholder strings on the Prospecting row** — its subject and its verdict. Every other
  row on that screen quotes a rate the colony produces; this is the first measured in a run, and
  Claude Design left the wording to Davide.
- **The deposit is measured from your home**, because the cap carries `danger` and `danger` contains
  `distanceBand`. That is what makes the time to strip a world identical everywhere, which is what
  makes the rule teachable — and it is observer-relative, so multiplayer will have to revisit it. The
  fix is named in the sheet's §11. **Do not "tidy" it to hazards alone.**
- **The absent player is paid about fifty times over**, measured, and accepted rather than fixed: the
  window rung decides reach and reach decides how many veins you can spread across, so a player on
  the 3h rung reaches six worlds where a once-a-day player reaches thirty-nine. The dial that touches
  it is the drive technology, not the cap. **The thing to watch on a device**: if the six-hourly
  player feels poor, the answer is a faster hull rather than a shallower world.
- ~~**Two `error("unreachable")` arms stay uncovered** in the `when`s over `ResourceKind`, because the
  enum is three-valued where the fleet only handles two. The fix is a type rather than a test.~~
  **Done at 0.10.1, and by the fix this entry named.** `FleetBalance` carries a private two-valued
  `Gathered`, and `cargo` maps into it once at the door instead of guarding with a `require` and then
  carrying three dead arms downstream. The public signature, `Run.gathering` and the save format are
  untouched — the narrowing is entirely inside the object. It was the coverage gate that finally
  forced it: flattening the hull price deleted six *covered* branches, so unit branch coverage fell
  0.06pp with nothing newly uncovered, and the honest way back up was to stop counting branches no
  test can reach.

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
