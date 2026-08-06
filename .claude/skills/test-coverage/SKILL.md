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

The baseline is a GitHub Actions cache written only by `main` (`oltre-coverage-v1-<sha>`), read
by every branch. Consequences worth knowing:

- **A PR sees deltas against the last `main` run, not against its own merge base.** If `main`
  has moved on, the delta includes that drift. Read it as a trend, not as an audit.
- **No baseline, no delta** — the report says so rather than showing zeros.
- The job is **not a required check** and sets **no thresholds**. A coverage gate is a design
  decision with a number attached, and numbers are Davide's; the report exists so the trend is
  visible before anyone picks one. When thresholds arrive, `koverVerify` is where they go, and
  the `protect-main` ruleset payload has to be updated in the same change.
