# Status

Updated: 2026-08-05

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

## Next

- Decompose v1 into vertical slices and record the roadmap here: 3 resources, 6 buildings,
  4 ship types, 1 research branch, procgen galaxy, 3 AI empires, notifications, JSON save.

## Pending / not yet set up

- Android app entry point (thin `androidApp`-style module) — when Android delivery matters.
- Milestone roadmap: ~~M1 iOS wiring~~ ✓, ~~M2 core economy~~ ✓, M3 Colony screen vs mockup,
  M4 research branch + tab, M5 procgen galaxy + Galaxy screen, M6 ships/shipyard/fleets,
  M7 combat + 3 AI empires, M8 JSON save + offline progression + notifications.
- **Open design question for Davide:** what raises the storage cap? (flat 250k placeholder now;
  candidates: a storage building, mine-level scaling.)
- No linter (detekt) configured yet — decide when code volume justifies it.
