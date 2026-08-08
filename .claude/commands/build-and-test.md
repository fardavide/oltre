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

**From a session that cannot resolve AGP** (Claude Code on the web — `dl.google.com` is 403 there,
so both commands above fail during configuration), run what can still be verified rather than
nothing:

```
.claude/tools/gradle-without-agp.sh :core:jvmTest :sim:test
```

That covers `:core` — which is where domain and balance work lives — and leaves `client/*` to CI.
Say plainly in the PR which modules were verified locally and which were not. See
`.claude/rules/session-roles.md`.

Coverage is reported, not gated: CI's **Coverage** job runs `.github/scripts/measure-coverage.sh`
and comments the per-test-kind table on the PR. Run it locally only when you want the numbers —
it is five Gradle passes. A single kind is cheap: `./gradlew check -Poltre.testCategory=behaviour`.
