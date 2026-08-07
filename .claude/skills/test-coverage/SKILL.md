---
name: test-coverage
description: Oltre's four test kinds — unit, integration, screenshot, behaviour — how a test declares which one it is, how coverage is measured per kind, and how the PR report reads.
when_to_use: >
  Consult before naming a new test class, when deciding which kind of test a change needs,
  when the Coverage job's numbers move unexpectedly, and before adding any coverage threshold.
---

# Test kinds and coverage

## The four kinds

A test declares its kind through its **class-name suffix**. The suffix is the only piece of
metadata visible in the file tree, in a stack trace and in a CI log alike — an annotation is
none of those, and a source-set split would force a test to live away from the code it covers.

| Kind | Suffix | What it is | Example |
|---|---|---|---|
| Unit | `…Test` | In-process, no I/O, no clock, no Compose. The default — everything that is not one of the three below | `AdvanceTest`, `ColonyUiStateTest` |
| Integration | `…IntegrationTest` | Crosses one real boundary: the filesystem, a platform adapter, a real scheduler | `FileSaveFileIntegrationTest` |
| Screenshot | `…ScreenshotTest` | Roborazzi, verified against a committed baseline. See the `screenshot-testing` skill | `FacilityListScreenshotTest` |
| Behaviour | `…BehaviourTest` | Compose rendered and driven — taps, gestures, assertions on the node tree | `MainScaffoldBehaviourTest` |

Rules that follow from the table:

- **`…Test` is the residue, not a catch-all.** A test that touches disk and is still called
  `…Test` is reported as a unit test, which makes the unit number a lie. Rename it.
- **A fake is not a boundary.** `GameStoreTest` drives the same store as
  `FileSaveFileIntegrationTest` but against `FakeSaveFile`; it is a unit test. What makes the
  other one integration is the real `File`.
- **Behaviour tests go through Robots**, never through raw `onNodeWithTag` chains in the test
  body. A test says *what the player did and what they should see*; the Robot owns *how*. This
  keeps a testTag rename to one file. `MainScaffoldBehaviourTest` predates the rule and still
  queries directly — it is the migration target, not the example to copy.
- **Rendering Compose is enough to make it a behaviour test.**
  `ColonyScreenLayoutBehaviourTest` asserts layout bounds rather than tapping anything, and it
  still belongs here: it needs a composition and a node tree, which is what separates the kind.
- **Screenshot and behaviour are not alternatives.** A screenshot asserts what a state looks
  like; a behaviour test asserts what happens when you touch it. A tap that changes appearance
  wants both.

## Running one kind

`-Poltre.testCategory` filters every `Test` task in the build:

```
./gradlew check                                        # everything, the normal case
./gradlew check -Poltre.testCategory=behaviour         # behaviour tests alone
./gradlew koverXmlReport -Poltre.testCategory=unit     # …and what they cover
```

A module with no test of that kind matches nothing and passes — that is expected, not a
failure (`isFailOnNoMatchingTests = false` in the root build file).

## Coverage

Kover, aggregated at the root. `.github/scripts/measure-coverage.sh` runs **five passes** — one
per kind plus one unfiltered — because coverage is a property of the tests that ran: the only
way to say what the behaviour tests reach, as opposed to what the whole suite reaches, is to run
them alone and read the report.

- `./gradlew koverHtmlReport` → `build/reports/kover/html/index.html` for a local look.
- `.github/scripts/coverage.py` folds Kover XML + JUnit XML into `build/coverage/summary.json`
  and renders the Markdown report.
- Excluded from the numbers: Compose's generated `ComposableSingletons*`, serialization's
  `$$serializer` classes, compose-resources' `*.generated.resources` accessors, and the two
  `MainKt` entry points (`:sim`, `:server`) — generated code, or process entry points exercised
  by running them, so leaving them in only depresses the total with a number no test can move.
  Nothing else is excluded; if a number looks wrong, it is the tests that are wrong.
  **Adding an exclusion needs evidence from a real report**, not a guess that something is
  generated — every entry above was added after seeing it in one.

## The PR report

The **Coverage** job posts one comment per PR, rewritten in place, and writes the same table to
the run summary. Each row is a test kind; each cell carries the current value and the delta
against the last `main` run. The per-package section answers the question the totals cannot:
*which kind of test is actually reaching this code.*

**Read the behaviour and screenshot rows as "was this rendered", not "was this asserted".**
Rendering a screen executes every line that composes it, so both kinds score high on a UI module
the moment any test puts it on screen — the first report had behaviour at 47% and screenshot at
46% of the whole project while *nothing* drove the game's only interaction. Line coverage cannot
tell a rendered line from a driven one. What the split is good for is the comparison *between*
kinds on one package: `dev.fardavide.oltre.client` at 4% unit and 74% behaviour says the shell is
held up entirely by tests that render it, which is true and worth knowing. A high behaviour
number is never on its own evidence that an interaction is tested.

The baseline is a GitHub Actions cache written only by `main` (`oltre-coverage-v1-<sha>`), read
by every branch. **No baseline, no delta** — the report says so rather than showing zeros.

## The gate

The Coverage job is a **required check**, and it fails when line coverage falls. One comparison
says the whole rule:

```
pass  ⟺  current ≥ min(last main run, 95%)
```

Below 95% that is a plain ratchet — a PR may not leave the project worse than it found it. At or
above 95% there is slack down to 95%, because holding a high-nineties number to the decimal buys
nothing and turns every merge into a negotiation. The floor is `COVERAGE_FLOOR` in
`.github/scripts/coverage.py`; it is Davide's number, and changing it needs him.

What this means when you write code, and it is stricter than it sounds: with the project in the
mid-nineties, **new code has to be covered about as well as the project average** or it drags the
total down. A 200-line feature at 90% covered fails the gate even though 90% is a decent number.
Budget for the tests in the same slice, not the next one.

Details that matter when it fires:

- **It gates the `All tests` line number only.** Branch coverage moves for reasons that are not
  regressions, and a per-kind row moves when a test is renamed from one kind to another; neither
  should block a merge.
- **It judges to one decimal** (`GATE_EPSILON`), the precision the table prints, so the verdict
  can never contradict the `±0` in the row above it.
- **Pull requests only.** On a `main` push the merge has already happened, so a red `main` there
  would be a false alarm rather than a signal — and the baseline is stored *before* the gate runs,
  so `main` keeps tracking reality even on a run that would have failed.
- **The drift caveat is smaller than it looks.** The baseline is the last `main` run rather than
  the PR's merge base, but `protect-main` sets `strict_required_status_checks_policy`, so a branch
  is up to date with `main` before it can merge — at merge time the last `main` run *is* the merge
  base. Drift shows up in the deltas of an out-of-date branch, not in the verdict that gates it.
- **A cache miss disables the gate silently-ish.** With no baseline the verdict is `skipped` and
  the job passes; the comment and the log both say so, but nothing goes red. This is the known
  hole — a PR merged during a cache eviction is a PR nothing measured.
- `coverage.py`'s gate arithmetic is tested (`.github/scripts/test_coverage.py`, pytest), and the
  Coverage job runs those tests before it measures anything.
- The gate lives in `coverage.py`, **not** in `koverVerify` — it needs the baseline, and Kover's
  own verification rules only know about absolute numbers.
- The `protect-main` ruleset payload lists `Coverage` among the required contexts. Keep the
  committed payload and the applied ruleset in sync.
