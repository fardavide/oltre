# Session roles

Three kinds of session work on Oltre. They are not interchangeable, and each one's limits come
from something real rather than from policy.

| Session | May work on | May not |
|---|---|---|
| **Cloud** — Claude Code on the web | infrastructure, `core` / domain logic, build and CI, docs, planning and decision sheets | **anything a player sees.** No Compose, no `presentation` module, no screenshot baselines, no design-system components |
| **Local** — Claude Code on Davide's machine | everything, UI included | ship UI with no design behind it |
| **Claude Design** | screen and component design, decision sheets with a visual half | write code |

## Why the cloud session cannot touch UI

Two hard facts, not preferences:

1. **A cloud session cannot build anything that needs AGP.** The remote environment's egress
   policy answers 403 to `dl.google.com`, and every route to the Android Gradle Plugin ends there
   — `maven.google.com` redirects to it, the Gradle Plugin Portal redirects to Maven Central, and
   Google does not publish AGP to Maven Central (see `status.md`). Every `client/*` module applies
   AGP, so UI written there is unverified by definition.
2. **A cloud session cannot reach Claude Design.** `DesignSync` needs an interactive
   `/design-login`, which needs a terminal. So the design it would be implementing is not
   readable from inside the session.

Compose it cannot compile, screenshots it cannot record, against a design it cannot see. Domain
code has none of those problems: `core` is pure, its tests are the specification, and CI is the
gate.

### The one exception: a platform entry point is not UI

`MainActivity` was written by a cloud session at 0.2.0, with Davide's call, and it contains a
Compose call — `setContent { App() }`. The line the rule draws is between **making a visual
decision** and **hosting one that already exists**: an entry point attaches a save directory, goes
edge to edge and hands the platform the `App()` the design already settled. There is no design to
read, nothing to record a baseline of, and CI's Build job compiles the APK on every pull request,
so it is not unverified in the way a Compose screen would be.

The same session then drew `ic_notification.xml`, which is unambiguously a visual asset and the
only one in the repository a cloud session authored. It is defensible for one reason — Android
masks a status-bar icon to a flat silhouette, so the choice was between a reduction of the
existing mark and shipping the launcher artwork as a white blob — and it is flagged in
`decisions.md` and `status.md` as overrulable rather than settled. **A second one would not be
defensible.** If a platform needs artwork again, that is a prompt for Claude Design.

This does not widen anything else. A screen, a component, a baseline or a `presentation` module is
still off limits to a cloud session, and the reasons in this section are unchanged. And note what
the exception could not cover: **nobody has run the Android build on a device**, which is a local
session's job — see the *Pending* entries in `status.md`, which now list five things the first
install is the first test of.

### It *can* build and run `:core` and `:sim` — use it

"A cloud session cannot build" was the flat claim here until 0.1.1, and it is too strong. `:sim`
depends only on `:core` and consumes its **JVM** target; AGP is in `:core` only to publish an
Android target the sim never looks at. Restricted to those two modules with the Android target
dropped, Gradle resolves everything it needs from Maven Central and the real harness runs
unmodified:

```
.claude/tools/gradle-without-agp.sh :sim:run
.claude/tools/gradle-without-agp.sh :core:jvmTest :sim:test
```

What it cannot run is `client/*`, and **CI runs that** — see "What is *not* a reason to hand off"
below before treating an uncompilable client file as somebody else's problem.

The script swaps in a minimal overlay for the three build files, runs Gradle, and always restores
the real ones — it refuses to start if they have uncommitted changes, because the restore is a
hard `git checkout --`. Nothing it writes is ever committed.

This is what round 7 of the balance log was measured with, and the 0.0.12 greedy week reproduced
byte for byte through it. **So a cloud session doing balance or domain work should run the tests
and the sim rather than reasoning about the numbers** — rounds 2 and 3 wrote their tables by hand
against this same blockage, and hand arithmetic is not a measurement. It changes nothing about
UI: `client/*` still cannot be compiled, and screenshot baselines still go through the manual
Record job.

### What is *not* a reason to hand off (Davide, 2026-08-09)

Both of these were used as hand-off items by the round 11 session and both were rejected. They are
written down because the rule above reads as though they follow from it, and they do not.

1. **"I changed a file I cannot compile here."** *"Tests can run on CI, we don't need local."*
   CI builds every module including `client/*`, runs the unit tests, and runs
   `verifyRoborazziDesktop`. So a cloud session that touches a client **test** — an assertion that
   moved because a `core` balance number moved, say — pushes it and lets CI be the verifier. That
   is what CI is. Not being able to run it *locally* is not a reason to stop, and it is not a
   reason to make someone else finish a two-line edit.
2. **"I bumped the version but could not run `xcodegen`."** *"Bump is fine."* The `versioning`
   skill already says to bump `iosApp/project.yml` anyway and note it, and
   `iosApp/ci_scripts/ci_pre_xcodebuild.sh` rewrites `MARKETING_VERSION` from the catalogue on
   every Xcode Cloud build, so nothing ships mislabelled. An unregenerated project is a tidy-up the
   next local session does in passing, not a blocker to name.

The corollary, and the point of the section: **a cloud session finishes its own work.** Hand off
only for what a cloud session genuinely cannot do — write UI against a design it cannot read. Do
not hand off verification, tidy-up, or anything CI already covers, and do not turn an observation
into someone else's task. If a cloud session notices something outside its lane, it *says so in a
sentence* and carries on; a hand-off prompt is for work that has been decided on, not for a
suspicion. Round 11 also wrote a UI item into a hand-off before Davide had decided he wanted it,
which is the same mistake in the other direction — **the scope of a session is the scope of the
request.** A balancing session stays a balancing session.

## Hand-offs are prompts, in a code block

- **Cloud needs Local** → the cloud session's final output is a prompt for the local session, in
  a code block, ready to paste. A cloud session **ends** with that prompt — but only when there is
  genuinely local-only work to hand over. When there is not, it ends with the work done, and a
  hand-off prompt written anyway is noise.
- **Local needs Design** → the local session emits a prompt for Claude Design, in a code block,
  ready to paste. It does **not** end there — it waits for the design to come back, then
  continues with it.

Note the asymmetry: cloud→local is a hand-off, local→design is a **round trip**.

A hand-off prompt names: the branch to work on, the docs to read first, what is in scope, what is
explicitly out of scope, and what "done" means. Never a summary of the work — a specification of
it. The prompt-writing rule from the Notion page applies: *a spec plus context, not a leash.*
