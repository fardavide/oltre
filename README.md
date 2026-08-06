# Oltre

An asynchronous space colonisation strategy game in the OGame lineage. Short check-in sessions,
everything progresses while the app is closed: exponential cost curves, distance as travel time,
permanent fleet loss. v1 is local single-player against scripted AI empires; multiplayer is the
destination. iPhone is the delivery target, desktop is the dev loop.

All rights reserved. No license is granted for reuse of this code.

## Stack

Kotlin Multiplatform monorepo. Compose Multiplatform UI, no game engine.

| Module | What |
|---|---|
| `core` | KMP (jvm, iosArm64, iosSimulatorArm64, android). Pure model + rules, zero third-party deps. |
| `sim` | JVM. Headless balancing harness, fast-forwards weeks in milliseconds. Never ships. |
| `client/*` | KMP + Compose Multiplatform: desktop, iOS, Android. Directory of modules — `:client:shell` (composition root + entry points), `:client:design` (theme), one module per feature as features land. |
| `server` | JVM + Ktor. Compiling stub until multiplayer starts. |
| `iosApp` | Xcode wrapper around the client framework (pending). |

## Build

```bash
./gradlew build
```

## Test

```bash
./gradlew check
```

## Run

```bash
./gradlew :client:shell:run     # desktop client (dev loop)
./gradlew :sim:run        # balancing harness
./gradlew :server:run     # server stub
```

iOS: pending Xcode wrapper (`iosApp/`), arrives with the iOS wiring slice.

## Icon

The app icon is an SVG master; every platform asset is generated from it and committed.

```bash
python3 art/icon/generate.py
```

Edit `art/icon/*.svg`, rerun, commit the result — never hand-edit generated PNGs. See
[art/icon/README.md](art/icon/README.md).

## Docs

- [docs/ui-mockup.html](docs/ui-mockup.html) — UI design brief: Colony + Galaxy screens at iPhone size.
- [.claude/docs/brief.md](.claude/docs/brief.md) — distilled project brief; points to the Notion design page.
- `.claude/docs/` — architecture, decisions, status.

## Changelog

### 0.0.6 — 2026-08-06

- **Returning fleets are visible.** A fleet flying home sits in an amber strip under the hero
  card — where it's coming from, what it's made of, and a live countdown to arrival. When it
  lands, its cargo joins your stores (up to the storage cap) and the return is logged. Ship
  names are placeholders until the v1 ship set is decided.

### 0.0.5 — 2026-08-06

- **The Colony screen is playable.** The resource rail shows all three resources with live
  rates. Every facility lists its level, per-resource upgrade cost (deuterium included) and
  build duration — the exact resource you're short turns red, and instead of a dead button an
  unaffordable upgrade shows when you'll be able to afford it; the Nanite Factory stays locked
  until Robotics 10. Tapping Upgrade starts the build, and the one in-flight build sits in a
  hero card with a live countdown, progress bar and local finish time.

### 0.0.4 — 2026-08-06

- **Oltre has a face.** The app ships an icon — a planet's lit limb with a single trajectory
  rising past it into empty space — on iPhone, macOS, Windows and Linux. The artwork is an SVG
  master in `art/icon/`; every platform asset regenerates from it. Android's launcher assets are
  generated and staged, ready for the app module when it lands.

### 0.0.3 — 2026-08-05

- **The economy is real: six buildings, a build queue, and energy.** All three resources accrue
  from their mine levels; upgrades cost exponentially more per level, take time, and complete
  through the simulation as logged events; the Robotics Factory speeds construction and the
  Nanite Factory unlocks at Robotics 10; mines slow down proportionally when the Solar Plant
  can't feed them; storage caps at 10M per resource (placeholder). The sim harness
  fast-forwards a week of greedy play in milliseconds.

### 0.0.2 — 2026-08-05

- **The Colony resource rail is alive: metal accrues in real time while the app is open.**
  First vertical slice through every layer — the pure simulation core (guarded by the
  advance-composability property test), the Colony feature module rendering the rail, the
  desktop shell ticking the clock at the boundary, and the sim harness fast-forwarding a week
  in milliseconds. First screenshot baseline committed.

### 0.0.1 — 2026-08-05

- Initial project scaffold: KMP monorepo (core, sim, client, server), CI, branch ruleset.
