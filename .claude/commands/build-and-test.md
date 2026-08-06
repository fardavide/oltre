---
description: Build everything and run all tests the way CI does, reporting failures verbatim.
---

Run the project's full verification, in this order, stopping at the first failure and reporting
its output verbatim (do not summarise an error away):

1. `./gradlew build` — assembles every module (JVM, Android, iOS klibs/framework on macOS) and
   runs `check` (all unit tests).
2. `./gradlew verifyRoborazziDesktop` — screenshot verification against committed baselines
   (skip silently if no baselines exist yet).

Both must pass before any PR is opened. These are the same gates CI enforces as required checks.

Coverage is reported, not gated: CI's **Coverage** job runs `.github/scripts/measure-coverage.sh`
and comments the per-test-kind table on the PR. Run it locally only when you want the numbers —
it is five Gradle passes. A single kind is cheap: `./gradlew check -Poltre.testCategory=behaviour`.
