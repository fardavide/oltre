---
name: screenshot-testing
description: Oltre's Roborazzi workflow — desktop-target screenshot tests, baselines recorded locally or by the manual Record screenshots job, committed, verified on CI with tolerance, never re-recorded automatically.
when_to_use: >
  Consult when writing or changing any Compose UI that a screenshot test covers, when a
  screenshot check fails on CI, or when the user asks to add/update UI baselines.
---

# Screenshot testing (Roborazzi, desktop target)

- Screenshot tests belong in the owning client module's `desktopTest` source set and run on the
  JVM — no emulator, no simulator. (None exist yet; the first lands with the walking skeleton.)
- **Record baselines locally**: `./gradlew recordRoborazziDesktop`. Baselines are committed —
  they are the assertion.
- **Verify** (what CI's "Screenshot tests" job runs): `./gradlew verifyRoborazziDesktop`.
- **The Mac is the recorder and CI verifies on a Mac.** One renderer at both ends, which is the
  only arrangement in which a screenshot test asserts anything: `ci.yml`'s screenshot job is the
  one job that does not run on Linux. So a local record is authoritative — record it, read the
  diff, push it. Nothing needs dispatching.
- **The "Record screenshots" workflow is the escape hatch, not the route.** It exists for a
  machine that cannot build — and since a cloud session is now rarely the one doing this
  (Davide, 2026-08-21), that is rarely. Dispatch it with
  `gh workflow run record-screenshots.yml -f pr=<number>`; it records on the runner, pushes a
  `chore: re-record screenshot baselines` commit, comments with before/after images, and
  dispatches CI. **It records on Linux, so what it commits now fails the gate** — a Linux render
  differs from a Mac one on 5–7.5% of pixels across nearly every frame, and the budget is 0.
  Dispatching it means committing to re-recording those frames on a Mac afterwards.
- **Never dispatch it to find out what changed.** That was the old loop and it cost PR #92
  **38 workflow runs in one day** (30 CI, 8 record dispatches), because a local record used to
  poison two frames and turn CI red. `recordRoborazziDesktop` takes about a minute and shows you
  the same diff on your own machine.
- **Never re-record automatically, and never to make a red build green without looking at the
  diff first** — that converts the test into a recorder of whatever the code does. This is why
  the workflow is `workflow_dispatch` only and posts the images: the reviewable artefact is the
  **comment**, and reading those images before merging is where "this visual change is intended"
  actually gets decided. Say in the PR that the job ran and what it recorded.
- **If `verifyRoborazziDesktop` fails on a baseline nobody touched, that is a finding now, not
  weather.** It used to be fair to suspect cross-OS drift first. Since the verifier moved to
  macOS there is one renderer, so a red frame means the drawing moved or the two Macs disagree —
  and the second is worth knowing about rather than absorbing.
- On CI failure, the diff images upload as the `roborazzi-diffs` artifact — read the diff, don't
  guess. Roborazzi also writes `build/test-results/roborazzi/desktop/results/*.json`, one per
  frame, carrying `diff_percentage` and the golden/actual paths; that is the machine-readable
  version of the same answer and it is what to parse when the log only says "there were failing
  tests".
- **Never use platform font families** (`FontFamily.Monospace`, default sans) in any composable
  a screenshot covers: macOS and Linux resolve them to different typefaces, and baselines
  recorded locally fail on CI with glyph-level diffs no honest threshold absorbs (learned on the
  very first baseline, 2026-08-05). Use the bundled family from `:client:design:core`
  (`oltreMono()`,
  JetBrains Mono, OFL) — bundle further weights/families there when needed.
- **Cross-OS drift is why the verifier is a Mac, and the numbers are worth knowing before anyone
  proposes moving it back.** Measured over the whole corpus on 2026-08-21: a Linux render differs
  from a Mac one on **5–7.5% of pixels on nearly every frame**, with the two most prose-heavy
  frames at 8.7% and 9.4%. The old `ThresholdValidator(0.08f)` sat just above the bulk of that and
  under those two — tight enough that a locally recorded corpus failed CI, which is what made the
  remote-record round trip feel compulsory. Same-machine rendering, by contrast, is exact: two
  consecutive records on one Mac produced byte-identical output for all 102 frames.
- The shared `oltreRoborazziOptions()` lives in
  **`:client:design:screenshot-testing`** (in its *main* source set, because KMP source sets cannot
  host test fixtures — it was copied into three modules before 0.0.14 extracted it). Its Kotlin
  package is `dev.fardavide.oltre.client.design.testing`, deliberately not matching the module name:
  a dash is not a legal package segment. Consume it from a **test** source set only — rule 5 in the
  `module-rules` skill, and the reason for the `-testing` suffix:
  `SimpleImageComparator(maxDistance = 0.007f)` ignores sub-perceptual dithering (at 0, every
  frame reports 0.5–33.5% of its pixels changed from ±1/255 gradient noise alone), and
  **`ThresholdValidator(0f)` allows no changed pixels at all** — there is no cross-renderer
  difference left to budget for once one Mac records and another verifies.
  Use it in every screenshot test; raise either constant only with a new diff image as
  evidence, never speculatively — and note that there is one copy, so loosening it loosens
  every baseline in the repo at once.
- **A refactor that only moves UI code must not move a baseline.** Verify, do not re-record: if
  `verifyRoborazziDesktop` fails after a pure move, the move changed the drawing, and that is a bug
  in the move rather than a baseline that needs updating. Record *before* such a change so it has
  something honest to verify against.

## A hand-built fixture cannot verify the mapper it stands in for

**A `Test…UiState` is a *drawing* of a screen.** It is written by hand, so it goes on rendering
whatever it was last told — including a screen that no longer exists. The mapper can grow a card, a
cell or a row and every screenshot will stay green, because the fixture was never asked.

**This bit three times in one release (0.15.0) and each one was a real defect:**

| | |
|---|---|
| `FleetBalance.FOR_SALE` gained `SCOUT`; the Shipyard's card list did not | the one hull that surveys was **unbuyable**, and the Galaxy tab dead for the whole game |
| The mapper then sold the `HAULER`; the frames still drew it as *coming* | two nodes with one test tag, and a frame of a screen nobody would see |
| `DispatchFrames` had no hauler in its pool | the picker's own frames photographed the sheet **without the picker** and passed |

**So when `core` grows a concept a mapper renders, three things move and only two of them shout:**

1. the mapper — the compiler finds it;
2. a test holding the mapper against `core` — write one, and it is the only automatic guard here;
3. **the hand-built fixture — nothing finds this but you.**

The check costs one question before recording: *does the fixture own the thing the mapper now
draws?* A pool with no hauler cannot photograph a hauler; a colony with no scout cannot photograph a
scout card.

**And it is why "read the diff" is a rule rather than advice.** All three were found by looking at a
picture, none by a red test — the frames were internally consistent, just of the wrong screen. A
re-record without reading banks the wrong screen as the assertion, which is exactly the failure the
`workflow_dispatch`-only job and its before/after comment exist to prevent.

## Baselines in a second language

- **Name a locale baseline `<frame>_it.png`, beside the English frame, and leave English
  unsuffixed.** Set with Italian (#87, 0.14.0), and it is the convention for every language after
  it: the language subtag appended to the name of the frame it is a translation of. A sibling
  directory was the alternative and it splits the one pair that is only ever read together — a
  locale frame means nothing except next to the frame it differs from, and the "Record screenshots"
  job's before/after comment is where that comparison actually happens.
- **A frame is *told* its language; it never reads the device's.** Pass `OltreTheme(Italian)`
  explicitly and leave the default `English`. `App` reads the system locale; a screenshot test that
  did the same would record on an Italian Mac and fail on an English runner, for a reason nothing in
  the diff would explain.
- **Baseline a handful at 320dp rather than the whole suite** (Davide, 2026-08-16) — the frames whose
  text is measured to the character, which is where a 15–30% longer language actually breaks. Every
  frame in a second language is also a frame to re-record whenever its English twin moves.
- **Only a frame built from the catalogue can see a language at all.** A fixture written as
  `TextRes("Metal Mine")` resolves to itself in every locale, so a locale baseline over it asserts
  that English is still English. Check the fixture first: the mapper-driven tests
  (`galaxy/presentation`, `fleets/presentation`) and any `Test…UiState` built through `Strings` —
  `TestShipyardUiState` is the one that is — are what is worth photographing twice.
- **A locale frame that truncates is a finding, not a baseline to fix by shortening the copy.**
  Record what happens, say so in the PR, and let the layout answer it. The alternative is worse
  words chosen to fit a width nobody measured on purpose.
