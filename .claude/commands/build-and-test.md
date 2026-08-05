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
