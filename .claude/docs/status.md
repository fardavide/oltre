# Status

Updated: 2026-08-06

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
- **0.0.9 local notifications (the rest of M8)** — `core.futureEvents` derives what is still
  coming, `:client:notifications:data` books it, the shell reschedules on the same trigger as
  the save. iOS schedules for real, desktop prints, Android waits for an app module. Also fixed
  the `MARKETING_VERSION` drift that shipped 0.0.8 to TestFlight labelled 0.0.7, and a latent
  build defect the new module exposed: every project shared one Gradle group, so two modules
  named `data` had identical coordinates and one silently left the compile classpath. The group
  now carries the project path — see `decisions.md`, and expect the same for `presentation` and
  `domain` layers as they arrive.

## Roadmap — v1 in vertical slices

The v1 feature set from Notion is *3 resources, 6 buildings, 4 ship types, one research branch,
a large procedurally generated galaxy, 3 AI empires, local notifications, JSON snapshot save*.
Four of the eight are done. What is left, decomposed into slices that each end playable —
**sequencing is the agent's (per the Notion hand-off), the content is Davide's**:

| # | Slice | Ends with | Needs a design call first |
|---|---|---|---|
| 1 | **Tab bar** | The mockup's 5-tab bottom bar over the Colony screen, with the four unbuilt tabs as honest empty states | No |
| 2 | **Research: core** | A shared tech tree in `core` — levels, costs, one lab-style build slot, effects applied through `advance` | **Yes** — which techs, what each does |
| 3 | **Research: screen** | The Research tab, built like the facility list | No, once #2 lands |
| 4 | **Galaxy: procgen** | Seeded generation of hundreds of systems with world traits, pure and reproducible from a seed in the save | **Yes** — trait axes and how they read |
| 5 | **Galaxy: screen** | Compose `Canvas` map over the tappable system list | No, once #4 lands |
| 6 | **Shipyard: core + screen** | The 4 v1 ship types, built from the shipyard, held in one empire-wide pool | **Yes** — the ship set (today's `CARGO/FIGHTER/CRUISER/COLONY_SHIP` are placeholders) |
| 7 | **Fleets: outbound** | Sending a fleet: distance as travel time, an outbound leg, the Fleets tab. The return leg already exists | **Yes** — travel-time formula, fuel |
| 8 | **Combat** | Seeded `resolve(a, b, seed)` and a battle report in the event log | **Yes** — the combat model |
| 9 | **AI empires** | 3 scripted empires that grow and raid, driven from `advance` | **Yes** — how visible, how aggressive |
| 10 | **Colonisation** | Settling a second world; the outpost → settlement → self-sufficient lifecycle | **Yes** — the pillar's rules |

Slice 1 is the one piece of navigation everything else hangs off and needs nothing decided, so it
is the natural next build. Slices 2, 4 and 6 are each blocked on content only Davide can supply
— worth asking for one of them early so the answer is ready when the slice comes up.

Colonisation (#10) is called a **core pillar** on Notion but is not in the eight-item v1 list;
carried here because the pressures that replace hard caps (upkeep, logistics, distance decay,
real failure) have nothing to act on without it. Whether it is v1 or v1.1 is Davide's call.

## Pending / not yet set up

- Android app entry point (thin `androidApp`-style module) — when Android delivery matters. Two
  stubs are waiting on it: `AndroidSaveLocation.directory` and the no-op notification scheduler.
- **Open design question for Davide:** what raises the storage cap? (flat 10M placeholder now;
  candidates: a storage building, mine-level scaling.) With human-scale production the flat cap
  is far out of reach — it binds nothing until very deep levels.
- **Open design question for Davide:** should anything cap how many facilities build at once?
  Nothing does today (resources are the only limiter), while Notion's expansion pressures call
  for "limited simultaneous projects".
- **Open design question for Davide:** what a notification *says* is player-facing content. The
  copy in `GameNotifications` is a placeholder that says what happened and that a decision is
  waiting.
- No linter (detekt) configured yet — decide when code volume justifies it.
- **Agent sessions cannot build.** The remote environment's egress policy blocks
  `dl.google.com`, so Gradle cannot resolve AGP and `./gradlew build` fails before compiling
  anything; `maven.google.com` only redirects there. CI is the gate for agent-written code, and
  screenshot baselines go through the manual Record job. Not a repo problem — do not "fix" it in
  the build files.
