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
- **No commas in a backticked test name in `commonTest`.** Kotlin/Native rejects them —
  `Name contains illegal characters: ","` — where the JVM accepts them happily, so
  `:core:jvmTest` goes green locally and `:core:compileTestKotlinIosArm64` fails on CI, taking
  the Build, Unit tests, Screenshot and Coverage jobs down with it. Learned the expensive way at
  0.2.4, on six names in one push. A `desktopTest` or `androidUnitTest` name may contain one,
  because no Native target ever compiles those source sets — which is why the two that exist in
  `client/*` have never failed. Semicolons and colons are out for the same reason; an em dash,
  an apostrophe and a full stop are all fine.
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
- Excluded from the numbers, in two groups. **Generated or unreachable code**: Compose's
  `ComposableSingletons*`, serialization's `$$serializer` classes, compose-resources'
  `*.generated.resources` accessors, and the `MainKt` entry points (`:sim` — the whole package —
  and `:server`). **Android platform edges the repo has no way to reach**: `MainActivity`,
  `OltreApplication`, `BootReceiver`, the notification scheduler and receiver, and the shake
  detector — a `SensorManager` or an `AlarmManager` behind a component the system instantiates,
  with no seam a test can take without Robolectric or an instrumented run, neither of which exists
  here. The full list is in the root `build.gradle.kts`, each entry with its own argument; this
  paragraph goes stale if that list moves, so read the file.

  If a number looks wrong, it is the tests that are wrong.

### An exclusion is Davide's call, and the bar is a failing report

**Two conditions, both required, and 0.4.2 is the case that proves they are not pedantry.**

1. **Evidence from a real report** — a Coverage run you can point at, showing the lines and what
   they cost. Not a guess that something is generated, not a pattern-match onto an entry already in
   the list.
2. **Davide's explicit permission**, asked for and given. An exclusion is permanent and silent: it
   does not lower the gate, it removes the gate's ability to *see*, and no future run can notice
   what is no longer being counted. That is not a call the build makes for itself.

The tilt parallax broke both. Three `classes(…)` lines went in during the same commit as the code
they hid, reasoning by analogy from the shake detector three lines above them, before any report
existed — the comment even said so out loud and shipped anyway. **The exclusion turned out to buy
nothing, measured rather than argued**: taking it back out moved the total from 96.9% to 96.3%
against the 95.0% floor of the day, because it had been hiding twenty-six lines. The gate passed
either way, then. **It would not today**: with the floor gone the bar is the baseline itself, and a
96.3% run against a 96.9% baseline is a red gate. Taking an exclusion back out now costs the tests
that were owed when it went in — which is the rule working, and one more reason not to add one. So a
rule was broken to purchase a margin that was already there, which is the worst version of this
mistake and the easy one to make.

Note what the report said while the exclusion was in place: `client.tilt.data` at **100.0%**. A
package can read fully covered because it is fully tested, or because the untested half of it has
been hidden, and the table cannot tell you which. That is the failure mode — not a number that looks
bad, a number that looks *good*.

Davide, on finding it: *"You excluded something from coverage check without my explicit permission.
This is very bad! We need a ROCK SOLID reason to exclude something from coverage report!"* (The
three lines came back out in the next PR, which carried no version bump — nothing a shipped build
does was ever affected.)

**The order to work in**: write the code, let the Coverage job measure it, read the number. Most of
the time there is nothing to ask for. If the gate does fail, the first question is whether a test
can reach the code — an exclusion is the last answer, never the first, and it is a request rather
than a decision.

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

The Coverage job is a **required check**, and it fails when *any* number in the per-kind table
falls. One comparison, applied ten times:

```
pass  ⟺  every gated value ≥ the same value on the last main run
```

That is a plain ratchet with **no floor and no slack** — nothing may go down, wherever the project
happens to sit. Davide's call, 2026-08-12, replacing `min(last main run, 95%)`: the floor bought
slack above 95% that the project no longer wants, and the single number it gated could rise while
a kind underneath it fell.

**Ten values are gated**: line and branch, for each of unit / integration / screenshot / behaviour
/ all. Not the test counts — a count is not a coverage value — and **not the per-package
breakdown**, which is a diagnostic: a package that is new, deleted or renamed moves cells with no
regression behind it, and the totals catch what those cells are for.

What this means when you write code, and it is stricter than it sounds: **new code has to be
covered about as well as the project average** or it drags the total down. A 200-line feature at
90% covered fails the gate even though 90% is a decent number. Budget for the tests in the same
slice, not the next one.

### Paying the debt somewhere else is the point, not a dodge

**A row that falls may be answered by covering something the PR never touched, and that is the
correct answer rather than a way round the gate.** Davide, 2026-08-22, on being told the alternative
was "screenshot frames unrelated to this change": *"They still increase the coverage, converting
previously uncovered screen. That's actually very good? And a reason why we have no negative delta
rule."*

The rule gates **totals**, deliberately — see the per-package note above. It is a ratchet on how well
the project is tested, not an audit of one diff, so a slice that costs three branches and pays for
them by photographing a screen nobody had photographed leaves the project better than a slice that
cost nothing. Refusing to do that on tidiness grounds is refusing the thing the ratchet is for.

What separates it from gaming is whether the new test is *worth having on its own*: a frame of a
screen with no baseline, a case a `when` never reached, a real boundary nothing crossed. A test
written to touch lines and assert nothing is not, and neither is one that forces a recomposition
purely so a skip branch is taken.

**Look for the never-covered thing before concluding a row is unfixable.** In the Kover XML a
composable that no test composes shows `covered=0` with a non-zero `missed`, which is the search:

```
python3 - <<'PY'
import xml.etree.ElementTree as ET
t = ET.parse("build/reports/kover/report.xml")
for pkg in t.getroot().findall("package"):
    for cl in pkg.findall("class"):
        for m in cl.findall("method"):
            for c in m.findall("counter"):
                if c.get("type") == "BRANCH" and int(c.get("covered")) == 0 < int(c.get("missed")):
                    print(c.get("missed"), pkg.get("name"), cl.get("name"), m.get("name"))
PY
```

0.15.4 is the case: the bell's four new callbacks cost 21 screenshot branches, and `RowSheet` — the
modal Colony and Research actually raise — turned out to be composed by no screenshot test at all,
because both screens photograph `RowSheetContent` instead. Two frames of the real sheet converted 39
branches and took the row from 54.5% to 56.2%. The gate found a four-release-old hole in the suite,
which is exactly what it is for.

### A parameter added to a composable costs screenshot branch coverage

Worth knowing before the row surprises you. Compose emits a skippability check per parameter —
`if (composer.changed(param))` — whose true arm runs on first composition and whose false arm runs
only on a **recomposition with the same value**. A screenshot test composes one state and photographs
it, so it takes the true arm and never the false one: every new callback lands at about 50% on
`screenshot branch`. The behaviour kind covers them, because tapping recomposes.

Two things follow. **A trivial wrapper composable should carry `@NonRestartableComposable`** — it has
no state and reads nothing, so a restart scope of its own can do nothing its caller's cannot, and
without the annotation Compose generates one anyway. 0.15.4's `Committing` was ten branches of
machinery over a `Row`. **And the cost is real for the rest**, so budget a frame for it.

Details that matter when it fires:

- **A rename between kinds is now a gate event.** Moving `FooTest` to `FooBehaviourTest` lowers
  the unit row, and the PR that does it has to leave that row where it found it. This was the
  stated reason the old gate judged the total alone; it is accepted rather than overlooked.
- **A value only one side has is not judged at all** — the first behaviour test the project ever
  had has nothing to be measured against, so it is left out rather than compared to a zero nobody
  measured. It joins the ratchet on the next `main` run.
- **A `—` is not a zero.** A counter with nothing to cover (`:client:design:core` has no branches)
  is unjudgeable on either side, the same as a missing one.
- **It judges to one decimal** (`GATE_EPSILON`), the precision the table prints, so the verdict
  can never contradict the `±0` in the row above it.
- **The comment names every value that fell**, with what it is and what it was, so the failing
  rows are readable without opening the job log. `enforce` prints the same list to stderr.
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

### Reaching for a production constant from a unit test can *lower* the unit row

Measured on 0.16.0, and it is the opposite of the obvious. A file whose top-level `val`s are only
ever read by composables shows up in the unit pass as a handful of **uncovered property
initialisers** — `PlayerStrip.kt` reported twenty missed lines and nothing else, because the
`@Composable` exclusion had removed every function and no unit test had ever loaded the file class.

The tempting fix is a unit test that asserts on one of those constants. **Do not.** Touching
`STRIP_HEIGHT` from a `…Test` loads `PlayerStripKt`, and the whole class arrives in the report:
twenty lines covered, **forty-one lines of composable body added to the denominator**. Measured
both ways on the same branch — 91.42% without the test, 91.31% with it.

The exclusion is `annotatedBy(Composable)`, which lands on annotated *functions*. Lambdas nested
inside them, and the bodies that reach the report once the class is loaded, are not annotated and
are not excluded. This is issue #100's shape reaching a second kind of code.

**The fix is structural and it is a better file anyway: put the numbers in their own file.**
`PlayerStripGeometry.kt` holds the strip's constants and the one piece of arithmetic that reads
them (`experienceFraction`); `PlayerStrip.kt` holds only drawing. The geometry file is fully
covered by a unit test, the drawing file is never loaded and contributes nothing, and the unit row
went 91.42% → 91.81%. It is the same move `:client:design:icon` made when it lifted the bell out of
its `Canvas { }` lambda so the ink could be measured.

**Rule of thumb.** Before adding a unit test that names something in a file full of composables,
ask what else loading that file drags in. If the answer is "a screen", move the thing being tested
instead.

### A defaulted parameter nothing overrides is untested surface

Each one emits a `$default` branch that only a call site omitting it can cover — so a composable
with one caller at one size carries branches no test can honestly reach. `PlayerMark` and
`SettingsGlyph` each shipped a `size: Dp = …` that nothing ever passed; deleting it removed the
branches rather than testing them, and read better. `WatchBell` keeps its `size` because it has two
callers at two sizes, which is what makes the parameter real.

## A test that reads the wall clock makes the gate a coin flip

**No behaviour test may call `Clock.System.now()`.** `App` takes a `wallClock` and `AppRobot` hands
it the fixed `TEST_NOW`; build every snapshot's `lastUpdatedAt` from that constant too, so a colony
"aged by two days" is aged from a known instant rather than from whenever the suite happened to run.

**What it costs when one does, measured 2026-08-21.** A launch with no save mints its galaxy from the
instant it happened — `resume` derives the seed from `now`, deliberately, so a new colony gets a new
map. `app(saved = null)` therefore generated a *different galaxy every run*, `homeFor` walked a
different set of systems, and `GalaxyGeneration.kt` line 342 flipped between covered and missed.
Behaviour branch coverage measured **66.826%, 66.849% and 66.894% on three runs of identical code** —
straddling the 66.85% rounding line, so the table printed 66.8% or 66.9% at random. Against a ratchet
with no slack, that fails roughly every other pull request for a reason no diff contains. Pinning the
clock took two consecutive passes to **identical counters across all 494 classes**.

**The tell is a red gate on a value the PR cannot explain**, especially a *branch* number moving while
lines and test counts hold at `±0`. Before touching a threshold, run the category twice on one commit
and diff the per-class counters out of `build/reports/kover/report.xml` — if they differ, the gate is
not what is broken.

**And the general rule the seams already encode:** everything `App` reaches outside itself is a
parameter — store, preferences, translations, notifications, shake detector, tilt source, and now the
clock — because *a behaviour test whose result depends on the machine or the moment it runs on is the
one failure mode those seams exist to prevent*. The clock was the last hole in that, and it was the
expensive one, because it failed as a statistic rather than as an assertion.

### A seam on `App` is charged to the screenshot row

**Every parameter added to `App` costs six uncoverable branches in the screenshot pass**, because the
Compose compiler emits `$changed` bookkeeping per parameter and no screenshot test calls `App` — the
shell's baselines render `MainScaffold`, `Starfield` and `TabBar` directly. Measured 2026-08-21:
`AppKt` went 0/106 → 0/112 missed and the row fell 51.6% → 51.4%, on the change that gave the shell a
clock. **Defaulted or required makes no difference** — both were measured, both cost six.

That row was charging a fee for the seams that make the suite honest, out of a number that was never
measuring the drawings, so `AppKt*` became the block's fourth screenshot-pass exclusion (Davide,
2026-08-21). The row stepped 51.4% → 54.4% when 124 branches left the denominator at once.

**So when a red gate names a row your change has no business touching, check the denominator before
the tests.** A per-category ratchet penalises adding a parameter to a function that category cannot
reach, and the fix is scoping the measurement, never weakening the seam.
