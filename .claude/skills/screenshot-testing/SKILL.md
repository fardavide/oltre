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
  very first baseline, 2026-08-05). Use the bundled family from `:client:design` (`oltreMono()`,
  JetBrains Mono, OFL) — bundle further weights/families there when needed.
- Cross-OS drift between macOS recording and Linux CI comes in two shapes, measured from the
  CI diff artifacts of runs 31072340252 and 31075759250 (2026-08-06): gradient/dither noise
  of ±1/255 spread across nearly every filled pixel (87% of the in-progress card), and glyph
  anti-aliasing drift of ≥10/255 on 2.4–5.6% of pixels — the ratio rises as the screenshot
  shrinks, because text dominates a small canvas (the 393×72 fleet strip measured 5.6%).
  Both are absorbed by the shared `oltreRoborazziOptions()` (desktopTest):
  `SimpleImageComparator(maxDistance = 0.007f)` ignores the sub-perceptual noise,
  `ThresholdValidator(0.08f)` budgets the glyph edges on the smallest text-dense component.
  Use it in every screenshot test; raise either constant only with a new CI diff image as
  evidence, never speculatively.
