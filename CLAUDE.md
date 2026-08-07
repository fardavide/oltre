# Oltre

Asynchronous space colonisation strategy game in the OGame lineage. 5–10 minute check-in
sessions; everything progresses while the app is closed. iPhone is the delivery target, desktop
is the dev loop, multiplayer is the destination. v1 is local single-player vs scripted AI.

## Read first

- `.claude/docs/brief.md` — distilled brief; links the Notion design page (**source of truth**;
  agents read *and* write it — record what the build learned, never overwrite Davide's calls).
- `.claude/docs/architecture.md` + `.claude/docs/decisions.md` — before any non-trivial change.
- `.claude/docs/balance-log.md` — before touching any balance number: what was already tried,
  what playing it felt like, what is still open. Add a round there whenever tuning lands.
- `.claude/rules/session-roles.md` — **which session you are and what you may touch.** A cloud
  session may not write UI; hand-offs between sessions are ready-to-paste prompts.
- `.claude/docs/galaxy-sheet.md` — the settled design for slices 4 and 5 (the galaxy). Read it
  before writing a line of generation; it is the design, not a proposal.
- Design decisions (balance numbers, mechanics, scope) are Davide's. If Notion doesn't answer,
  ask — never invent.

| Project skill | Use for |
|---|---|
| `architecture` | Module map, dependency rule, adding a feature module |
| `module-rules` | The eight build-enforced module rules; what a layout/dependency failure means |
| `versioning` | Version bump + changelog, real file paths |
| `screenshot-testing` | Roborazzi record/verify workflow, baseline policy |
| `test-coverage` | The four test kinds, naming convention, per-kind coverage, the PR report and the merge gate |

## Stack (decided — do not substitute)

Kotlin Multiplatform + Compose Multiplatform. **No game engine** (KorGE evaluated and rejected;
galaxy map is a Compose `Canvas`). No DI framework. No mocking framework — handwritten fakes.
Versions live only in `gradle/libs.versions.toml`.

## Architecture

`core` (pure KMP model + rules) ← `client/*`, `server`, `sim`. Dependencies point inward; the
module graph enforces the direction (`core`'s build file declares nothing beyond the test
library and `kotlinx-serialization`, which carries the save format). Core *purity* — no clock
reads, no I/O — is enforced by review and the required property tests, not by compilation.
Invariants (raise, don't work around — full list in `.claude/docs/brief.md`):

1. `core` is pure: no I/O, no clock reads, no platform types. Time is a parameter:
   `advance(state, from, to)`. Randomness is explicitly seeded.
2. State is an append-only event log, not in-place mutation.
3. Never run a timer for game state; compute from the last-updated instant on foreground.
4. `client/` is a directory of modules, never a monolith: `:client:shell` (composition root),
   `:client:design` (a *directory* of design-system layer modules — `:core` tokens, `:icon`,
   `:component`, `:format`, `:testing`), and one *directory* per feature holding layer modules
   (`:client:<feature>:presentation`, plus `:domain` / `:data` only when the feature needs them).
5. A module cannot contain another module; `domain` cannot see `data` or `presentation`;
   `presentation` cannot see `data`; `data` cannot see `presentation`; only a test source set may
   reach a `-testing` module; `core` depends on nothing, nothing depends on `:client:shell`, and
   `sim`/`server` never reach into `client/*`. **Enforced by the build** — a violation fails the
   IDE sync, not just review. See the `module-rules` skill.

## Build & test

- Build everything: `./gradlew build` — Test: `./gradlew check`
- Desktop app (dev loop): `./gradlew :client:shell:run`
- Screenshots: `./gradlew verifyRoborazziDesktop` / `recordRoborazziDesktop`
- Sim harness: `./gradlew :sim:run`
- Coverage: `./gradlew koverHtmlReport` — one kind only: `-Poltre.testCategory=behaviour`

Tests come in four kinds and say which by class-name suffix — `…Test` (unit),
`…IntegrationTest`, `…ScreenshotTest`, `…BehaviourTest`. Behaviour tests drive Compose through
**Robots**, never raw node queries in the test body. CI reports coverage per kind on every PR and
**blocks the merge if line coverage falls** — a PR must clear `min(last main run, 95%)`.
See the `test-coverage` skill.

All work on branches → PR → all required checks green → squash merge (`protect-main` ruleset,
no bypass). PRs batch a coherent milestone of related slices — commits stay small, PRs don't. TDD per the global `tdd` skill: failing test first, always.

**Merging to `main` publishes.** Xcode Cloud archives every `main` commit and ships it to
TestFlight (internal testers); GitHub Actions is the only gate. The iOS project is generated —
edit `iosApp/project.yml`, run `xcodegen generate` in `iosApp/`, and commit the project *and*
its shared scheme. Never hand-edit `project.pbxproj`. See `.claude/docs/decisions.md`.

## Sanctioned tooling

Builds and tests go through the Gradle wrapper (`./gradlew`) only. If it fails, the failure is
the problem to solve — never bypass with direct `kotlinc`/`xcodebuild` invocations.
