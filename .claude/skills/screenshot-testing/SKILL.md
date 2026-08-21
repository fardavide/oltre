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
- **Or dispatch the "Record screenshots" workflow** with the PR number
  (`gh workflow run record-screenshots.yml -f pr=<number>`, or the Run workflow button). It
  records on the runner, pushes a `chore: re-record screenshot baselines` commit to the PR
  branch, comments with before/after images, and dispatches CI so the new commit gets its
  required checks. Use it when the machine at hand cannot build — a remote agent session, or no
  Mac to hand.
- **Dispatch it yourself; do not leave the check red for Davide to clear** (his correction,
  2026-08-06, after a session read the rule below as forbidding that and stalled). A slice that
  adds or changes baselines dispatches the job against its own PR as part of finishing the slice.
- **Never re-record automatically, and never to make a red build green without looking at the
  diff first** — that converts the test into a recorder of whatever the code does. This is why
  the workflow is `workflow_dispatch` only and posts the images: the reviewable artefact is the
  **comment**, and reading those images before merging is where "this visual change is intended"
  actually gets decided. Say in the PR that the job ran and what it recorded.
- **A baseline recorded by the workflow is Linux-rendered**, where a locally recorded one is
  macOS-rendered. That direction is friendlier to CI (the recorder and the verifier are then the
  same renderer) but harsher locally: if `verifyRoborazziDesktop` starts failing on Davide's Mac
  for a baseline nobody touched, cross-OS drift is the first suspect, not a real regression.
- On CI failure, the diff images upload as the `roborazzi-diffs` artifact — read the diff, don't
  guess.
- **Never use platform font families** (`FontFamily.Monospace`, default sans) in any composable
  a screenshot covers: macOS and Linux resolve them to different typefaces, and baselines
  recorded locally fail on CI with glyph-level diffs no honest threshold absorbs (learned on the
  very first baseline, 2026-08-05). Use the bundled family from `:client:design:core`
  (`oltreMono()`,
  JetBrains Mono, OFL) — bundle further weights/families there when needed.
- Cross-OS drift between macOS recording and Linux CI comes in two shapes, measured from the
  CI diff artifacts of runs 31072340252 and 31075759250 (2026-08-06): gradient/dither noise
  of ±1/255 spread across nearly every filled pixel (87% of the in-progress card), and glyph
  anti-aliasing drift of ≥10/255 on 2.4–5.6% of pixels — the ratio rises as the screenshot
  shrinks, because text dominates a small canvas (the 393×72 fleet strip measured 5.6%).
  Both are absorbed by the shared `oltreRoborazziOptions()`, which lives in
  **`:client:design:screenshot-testing`** (in its *main* source set, because KMP source sets cannot
  host test fixtures — it was copied into three modules before 0.0.14 extracted it). Its Kotlin
  package is `dev.fardavide.oltre.client.design.testing`, deliberately not matching the module name:
  a dash is not a legal package segment. Consume it from a **test** source set only — rule 5 in the
  `module-rules` skill, and the reason for the `-testing` suffix:
  `SimpleImageComparator(maxDistance = 0.007f)` ignores the sub-perceptual noise,
  `ThresholdValidator(0.08f)` budgets the glyph edges on the smallest text-dense component.
  Use it in every screenshot test; raise either constant only with a new CI diff image as
  evidence, never speculatively — and note that there is now one copy, so loosening it loosens
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
