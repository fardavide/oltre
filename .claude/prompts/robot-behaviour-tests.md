# Prompt — Robot harness + upgrade-tap behaviour tests (run on desktop)

Paste everything below the line into a Claude Code session **on the desktop machine**, with both
the Oltre repo and the BandLab project available. It has to run there because two things this
session needs are unreachable from a remote agent session: BandLab's Robot implementation, and a
working Gradle build (the remote egress policy blocks `dl.google.com`, so AGP will not resolve —
see `.claude/docs/status.md`).

Branch to continue: `claude/ui-test-coverage-4kcrak`, which already carries the test taxonomy and
the coverage reporting this builds on.

---

You are working in the Oltre repo (`~/…/oltre`, branch `claude/ui-test-coverage-4kcrak` — fetch
it, it exists on the remote). Read `CLAUDE.md` and the `architecture`, `test-coverage` and
`screenshot-testing` skills before you start. TDD per the global `tdd` skill: failing test first,
always.

## The gap you are closing

Oltre's only interaction is tap-to-upgrade on a facility row, and **nothing tests it through the
UI.** Every Compose test in the repo passes `onUpgrade = {}` — a callback wired to nothing — so
the suite would stay green if the button were deleted. `core` unit-tests `startUpgrade` heavily;
the shell's `App` composable, which is what actually turns a tap into a state change, has no test
at all.

A previous session added the machinery to make that visible: tests declare their kind by
class-name suffix, and CI reports coverage **per kind** with a delta on every PR. The
`behaviour` row currently reports one test class that asserts layout bounds. Your job is to make
that row mean something.

## What to do

### 1. Port the Robot pattern from BandLab

Read BandLab's Robot implementation properly before writing any Oltre code — the base class, one
representative screen Robot, and one test that uses it. Extract the *convention*, not the code:

- how a Robot is constructed and scoped to a Compose test
- how actions and assertions are named and chained (`fun tapUpgrade(...): ColonyRobot`, and
  whether assertions live on the Robot or on a separate `…Assertions`/`verify` receiver)
- how node handles are declared (test tags vs. text vs. semantics properties) and where they live
- how a test that spans screens hands off from one Robot to the next
- what the base class does about waiting, idling and `waitUntil`

Write down what you took and what you deliberately left, in the skill (step 2) — a convention
nobody can see the reasoning for gets re-litigated.

**Adapt, do not transplant.** BandLab is Android + Espresso/Compose-Android; Oltre is Compose
Multiplatform driven through `runDesktopComposeUiTest` on the JVM desktop target. Anything
resting on `ActivityScenario`, Hilt, Espresso, or an Android `Context` has no counterpart here
and must be redesigned rather than stubbed. Oltre also has **no mocking framework and no DI
framework** by decision — handwritten fakes only (`FakeSaveFile`, `FakeNotificationScheduler` are
the existing examples).

### 2. Add the Robot directives to the `kickstart` skill

The `kickstart` skill is where this convention has to land so it reaches the next project, not
just this one. Add directives covering:

- **When a behaviour test is required** — any composable that takes a callback, any state that
  changes on interaction. A screenshot baseline is not a substitute: it asserts what a state
  looks like, never what touching it does.
- **The Robot rule**: a behaviour test body names *what the player did and what they should see*;
  the Robot owns every `onNode…` query, every `performClick`, every `waitUntil`. A raw node query
  in a test body is a review failure, so a testTag rename touches one file.
- **Naming**: `…BehaviourTest` for the test, `…Robot` for the driver, one Robot per screen or
  per component with its own semantics.
- **Test tags** are declared in one object per feature next to the composables (Oltre's
  `ColonyTestTags`), never as string literals at the call site.
- **The trap this exists to catch**: a UI suite that passes with its callbacks wired to nothing.
  Cite Oltre as the worked example.

Mirror the Oltre-specific half into `.claude/skills/test-coverage/SKILL.md` under the behaviour
section — that file already states the Robot rule in one line and is the right home for the
detail.

### 3. Build the harness in `:client:colony:presentation`

Everything goes in `src/desktopTest`, which is where the Compose UI test dependencies already
are (`compose.desktop.uiTestJUnit4`, `compose.desktop.currentOs`).

- A small base that owns `runDesktopComposeUiTest` setup, `OltreTheme`, and the window size, so
  no test repeats them. `ColonyScreenLayoutBehaviourTest` deliberately varies window size — keep
  that possible.
- `ColonyRobot` driving the Colony screen: facility rows by building, the Upgrade button, the
  countdown, the locked reason, the resource rail values.
- Extend `ColonyTestTags` (`src/commonMain/…/ColonyTestTags.kt`, `internal`, visible to
  `desktopTest` in the same module) with what the Robot needs — the facility row and the upgrade
  action at minimum, keyed by `BuildingType` so a Robot can address a specific row.

What the UI gives you today, so you can plan the handles: `FacilityList.kt` renders one
`FacilityRow` per `FacilityRowUiState`, and the action slot is a `when` over
`FacilityActionUiState` — `Upgrade` (a clickable "Upgrade" label, the only interactive element in
the app), `AffordableIn` (an inert bordered label), `Upgrading` (a countdown plus a progress bar),
`Locked` (nothing, and the whole row at 42% alpha).

### 4. The behaviour tests — `ColonyUpgradeBehaviourTest`

Cover the tap path at the presentation level:

- tapping Upgrade on an affordable row calls back with **that row's** `BuildingType` — with more
  than one affordable row on screen, so a test cannot pass by picking the first
- a row in `AffordableIn` has no Upgrade button and tapping where it would be calls nothing
- a `Locked` row has no Upgrade button and its requirement text is shown
- an `Upgrading` row shows its countdown and offers no second upgrade
- the callback fires **once** per tap

### 5. Then the one that matters: the shell

The presentation tests prove the button reports the tap. They cannot prove the *game* responds —
that lives in `:client:shell`'s `App`, which owns the `advance` → `startUpgrade` → `commit`
sequence. Add a behaviour test there that drives `App` with a fake store and fake scheduler, taps
Upgrade, and asserts the row moves into the upgrading state and the save was written.

**You will hit a wall, and the decision is yours to make and record.** `App` takes `store` and
`notifications` as parameters (already injectable) but reads `Clock.System.now()` directly in
three places, and ticks on a 1-second `delay`. A test that renders it is therefore wall-clock
dependent and will be flaky. Two honest routes:

- **Inject the clock** — add a `now: () -> Instant = { Clock.System.now() }` parameter to `App`,
  making the impure boundary explicit at its own edge. Consistent with invariant 1 (time is a
  parameter), currently enforced only as far as `core`. This is an architecture change: append it
  to `.claude/docs/decisions.md` in the same PR, with the rejected alternative.
- **Stay at presentation level** and cover the shell's sequencing with unit tests on
  `GameSession`, saying plainly in the PR that the tap-to-state-change seam remains untested.

Pick one, say why, do not do half of each. If you take the first, `:client:shell` needs the
Compose UI test dependencies on `desktopTest` — it has none today.

## Constraints

- **Test kind by suffix.** `…BehaviourTest` or the coverage report miscounts it. `…Test` means
  unit. See the `test-coverage` skill.
- **No raw node queries in a test body.** That is the whole point of step 1.
- **Handwritten fakes only** — no mocking framework, no DI framework. Both are standing decisions.
- **`core` stays pure.** No test-only hooks in it.
- **Screenshot baselines**: do not re-record to make anything green. If a baseline moves, read the
  diff first — `screenshot-testing` skill. Note that adding a `testTag` does not change rendering,
  so it should not move any baseline; if one moves, that is a real finding.
- **Never use platform font families** in anything a screenshot covers (`oltreMono()` only).
- Bump the version and the README changelog per the `versioning` skill — this is a feature slice.
- Update `.claude/docs/status.md` (the "no behaviour test touches anything" entry under *Pending*
  is what you are removing).

## Verifying

```
./gradlew build                                     # everything
./gradlew verifyRoborazziDesktop                    # baselines unmoved
./gradlew check -Poltre.testCategory=behaviour      # your new tests alone
./gradlew koverHtmlReport -Poltre.testCategory=behaviour
```

That last pair is the check that matters: the behaviour category's coverage of
`:client:colony:presentation` (and `:client:shell`, if you took the injected-clock route) should
rise sharply. If it does not, the tests are not reaching the code you think they are. CI posts
the same table on the PR, with a delta against `main`.

Open a PR when green. Do not create it before the build passes locally — you have a machine that
can build, which is the whole reason this work was routed to you.
