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
