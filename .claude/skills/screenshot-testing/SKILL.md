---
name: screenshot-testing
description: Oltre's Roborazzi workflow — desktop-target screenshot tests, baselines recorded locally and committed, verified on CI with tolerance, never regenerated on CI.
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
- **Never record on CI** and never re-record to make a red build green without looking at the
  diff first — that converts the test into a recorder of whatever the code does. Re-record only
  when the visual change is intended, and say so in the PR.
- On CI failure, the diff images upload as the `roborazzi-diffs` artifact — read the diff, don't
  guess.
- **Never use platform font families** (`FontFamily.Monospace`, default sans) in any composable
  a screenshot covers: macOS and Linux resolve them to different typefaces, and baselines
  recorded locally fail on CI with glyph-level diffs no honest threshold absorbs (learned on the
  very first baseline, 2026-08-05). Use the bundled family from `:client:design` (`oltreMono()`,
  JetBrains Mono, OFL) — bundle further weights/families there when needed.
- Residual anti-aliasing drift between macOS recording and Linux CI is absorbed by
  `ThresholdValidator(0.01f)` in `RoborazziOptions.CompareOptions`, set per screenshot test.
  Raise it only with a CI diff image as evidence, never speculatively.
