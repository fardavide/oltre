# Oltre

Asynchronous space colonisation strategy game in the OGame lineage. 5–10 minute check-in
sessions; everything progresses while the app is closed. iPhone is the delivery target, desktop
is the dev loop, multiplayer is the destination. v1 is local single-player vs scripted AI.

## Read first

- `.claude/docs/brief.md` — distilled brief; links the Notion design page. **Notion is the origin,
  not the authority** (Davide, 2026-08-16: *"Notion stuff is now very ancient"*) — the decision
  sheets in `.claude/docs/` and his live calls govern, and **no Notion number is a ceiling**. Never
  block work on one; if Notion is the only source for something, it is unanswered, so ask.
- `.claude/docs/architecture.md` + `.claude/docs/decisions.md` — before any non-trivial change.
- `.claude/docs/balance-log.md` — before touching any balance number: what was already tried,
  what playing it felt like, what is still open. Add a round there whenever tuning lands.
- `.claude/rules/session-roles.md` — **which session you are and what you may touch.** A cloud
  session may not write UI; hand-offs between sessions are ready-to-paste prompts.
- `.claude/docs/galaxy-sheet.md` — the settled design for slices 4 and 5 (the galaxy). Read it
  before writing a line of generation; it is the design, not a proposal.
- `.claude/docs/adaptation-sheet.md` — the settled design for the three adaptation ladders: why they
  are a second branch, what they cost, and why they share the one research slot. Read it before
  touching `AdaptationBalance` or putting the branch on a screen.
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
   `:component`, `:format`, `:text`, `:testing`), and one *directory* per feature holding layer modules
   (`:client:<feature>:ui`, plus `:presentation` / `:domain` / `:data` only when the feature needs
   them). **`ui` holds composables and the models they render and decides nothing; `presentation`
   holds the mapping from `core` or domain state into those models. `presentation` depends on `ui`,
   never the reverse, and a feature with nothing to decide has no `presentation` at all** — see
   `:client:debug`, whose logic is already its `domain`.
5. A module cannot contain another module; `domain` cannot see `data`, `presentation` or `ui`;
   `presentation` cannot see `data`; `data` cannot see `presentation` or `ui`; `ui` cannot see
   `data` or `presentation`; only a test source set may reach a `-testing` module; `core` depends on
   nothing, nothing depends on `:client:shell`, and `sim`/`server` never reach into `client/*`.
   **Enforced by the build** — a violation fails the IDE sync, not just review. See the
   `module-rules` skill.

## Build & test

- Build everything: `./gradlew build` — Test: `./gradlew check`
- Desktop app (dev loop): `./gradlew :client:shell:run`
- Screenshots: `./gradlew verifyRoborazziDesktop` / `recordRoborazziDesktop`
- Sim harness: `./gradlew :sim:run`
- Coverage: `./gradlew koverHtmlReport` — one kind only: `-Poltre.testCategory=behaviour`

Tests come in four kinds and say which by class-name suffix — `…Test` (unit),
`…IntegrationTest`, `…ScreenshotTest`, `…BehaviourTest`. Behaviour tests drive Compose through
**Robots**, never raw node queries in the test body. CI reports coverage per kind on every PR and
**blocks the merge if any number in that table falls** — line and branch, for each kind and for
the total, must hold at or above the last `main` run. No floor, no slack.
See the `test-coverage` skill.

Work that comes from an issue starts by **assigning that issue to `fardavide`** — `gh issue edit
<n> --repo fardavide/oltre --add-assignee "@me"`, before the branch — so the tracker shows it is
taken. The assignee means *picked up*, never work handed to Davide. Already assigned is nothing to
do; see the global `github-workflow` skill for the rest, including what to do when `gh` cannot
reach it.

**A ticket never buries a question** (Davide, 2026-08-15). When writing or updating an issue that
ends on something only Davide can answer, either **ask him in the session** (preferred when he is
present) or add the **`Needs info`** label, with the question stated plainly in the ticket. A ticket
blocked on a Claude Design round trip carries **`Needs Design`** instead, with the ready-to-paste
prompt in a `## Design prompt` section. The labels are the tracker's version of the reply rule that
anything needing Davide's action must be impossible to miss.

**Start from an up-to-date `main`, always** — `git fetch origin main` and branch from that, before
the first edit. A branch cut from a stale local `main` rebases later at the worst moment, and on a
repo that squash-merges it is the difference between a clean diff and a PR that re-litigates commits
that already shipped.

Then: all work on branches → PR → all required checks green → squash merge (`protect-main` ruleset,
no bypass). PRs batch a coherent milestone of related slices — commits stay small, PRs don't. TDD per the global `tdd` skill: failing test first, always.

**Merging to `main` publishes.** Xcode Cloud archives every `main` commit and ships it to
TestFlight (internal testers), and a merge that changes the version also publishes a signed APK as
a GitHub Release (`release-android.yml`, which cuts the `v<version>` tag too). GitHub Actions is
the only gate, and the README changelog entry *is* the Android release body — a version without
one fails the release. The iOS project is generated —
edit `iosApp/project.yml`, run `xcodegen generate` in `iosApp/`, and commit the project *and*
its shared scheme. Never hand-edit `project.pbxproj`. See `.claude/docs/decisions.md`.

## Sanctioned tooling

Builds and tests go through the Gradle wrapper (`./gradlew`) only. If it fails, the failure is
the problem to solve — never bypass with direct `kotlinc`/`xcodebuild` invocations.
