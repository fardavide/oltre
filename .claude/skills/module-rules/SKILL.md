---
name: module-rules
description: Oltre's eight module rules — layout, the layer edges, fakes that must not ship, and the sealed ends of the dependency graph. Enforced by the build, not by review.
when_to_use: >
  Consult before creating a module or a directory that will hold one, before adding any
  `implementation(projects.…)` / `api(projects.…)` line to a build.gradle.kts, and whenever a
  build or an IDE sync fails with "Module layout rule violated" or "Module dependency rules
  violated". Also when deciding where a shared component, a fake, or a test helper belongs.
---

# Module rules

Eight rules. All eight fail the build **and the IDE sync** — they are checked while Gradle
configures, not by a task you can forget to run and not by a reviewer who might not notice.

| # | Rule | |
|---|---|---|
| 1 | A module cannot contain another module | layout |
| 2 | `domain` may not depend on `data` or `presentation` | layers |
| 3 | `presentation` may not depend on `data` | layers |
| 4 | `data` may not depend on `presentation` | layers |
| 5 | Only a test source set may reach a `-testing` module | fakes |
| 6 | `core` may not depend on any module | direction |
| 7 | Nothing may depend on `:client:shell` | direction |
| 8 | `sim` and `server` may not depend on a `client/*` module | direction |

Rule 1 is checked in `settings.gradle.kts`, rules 2–8 in the root `build.gradle.kts`.

## Rule 1 — a directory is either a folder or a module

A directory that holds a `build.gradle.kts` is a module, and **nothing beneath it may be one**.
When a module needs a second module beside it, the parent becomes a folder and both become
siblings inside it:

```
dir/moduleA                 dir/sub-dir/moduleA
dir/moduleA/moduleB   ->    dir/sub-dir/moduleB
```

The check reads the **disk**, not the `include` list, so a module directory that was created and
never included is caught too — an un-included module misleads every human and agent reading the
tree just as much as an included one. The root `build.gradle.kts` is exempt: it configures the
build and declares no sources of its own.

It is walked with explicit `listFiles()` calls rather than `File.walkTopDown()`. Only the former
registers as a configuration-cache input; with the walk, adding a nested module while no build
script changed reused the cache entry and sailed through. If you touch that code, re-check that
property — the failure mode is silent.

## Rules 2–4 — which layer may see which

A module's **layer is the last segment of its Gradle path**, minus a `-testing` suffix:
`:client:save:data` is data, `:client:colony:presentation` is presentation, and
`:client:save:data-testing` is data too. Only `domain`, `data` and `presentation` are layers.
Each forbidden edge is forbidden for its own reason, not for symmetry:

- **`domain` → `data` / `presentation`.** Domain is the feature's rules; it *defines* the
  interfaces data implements and knows nothing of a screen.
- **`presentation` → `data`.** A screen talks to domain, never to a store or a socket. The day a
  feature grows a domain layer, a presentation that reached past it has to be rewritten rather
  than rewired.
- **`data` → `presentation`.** Obvious, and cheap to keep obvious.

**Test source sets count.** A presentation module that reaches a data module only from
`commonTest` still compiles against it, still couples to it, and is exactly as expensive to
unpick later. The failure names the configuration, so it points at a line:

```
Module dependency rules violated:

  :client:research:presentation -> :client:save:data
    presentation may not depend on data
    declared in: testImplementation
```

**A testing module carries its subject's restrictions.** `:client:colony:presentation-testing`
holds fakes for a presentation module, so it may not reach data either — and neither may a
presentation module reach data *through* it. Without the suffix strip the rule holds on the direct
edge and leaks on the one hop through the fakes, which is the only hole this shape opens. It reads
the right way round too: a fake of a domain interface has no more business knowing about a store
than the domain does.

## Rule 5 — fakes do not ship

A `-testing` module may be reached **only from a test source set**. That is the one thing a plain
module cannot say for itself: `testFixtures(projects.x)` is on the test classpath by construction,
but `implementation(projects.saveDataTesting)` is on whatever classpath asked, and nothing about
the dependency line admits it is fakes. So the build says it instead.

```
Module dependency rules violated:

  :client:shell -> :core-testing
    a testing module may only be reached from a test source set — fakes must not ship
    declared in: commonMainImplementation
```

What stays legal, and must: **any** test source set reaching **any** testing module —
`commonTest`, `desktopTest`, `androidHostTest`, `testFixtures`, an iOS test target. Consuming a
testing module from a test is the entire reason one exists, since `commonTest` is invisible to
consumers and KMP cannot host a test-fixtures source set. A testing module may also depend on
another testing module from its *main* source set: it is already fakes, so there is nothing to
leak into.

Only the offending configurations are named. Declare an edge from both `api` and
`testImplementation` and the failure points at `api` alone.

A configuration counts as a test one if it starts with `test` or contains `Test` — matched on the
camel hump rather than on `contains("test")`, so a source set called `latest` does not quietly
become a place fakes are allowed.

## Rules 6–8 — the graph points inward, and both ends are sealed

Each was true the day it was written and held by nothing except nobody having typed the line.

**6. `core` may not depend on any module.** It is the centre: everything points at it, it points
at nothing. Absolute rather than main-source-only, unlike rule 5 — "core depends on nothing" is
the invariant as written, and core already hosts its own test helpers in `commonTest`.

**7. Nothing may depend on `:client:shell`.** This is what makes the shell's exemption from rules
2–4 *safe* rather than merely convenient: it may see every layer precisely because nothing sees
it, so the layers it mixes cannot travel. Take this rule away and the exemption becomes a hole.

> **Known collision.** `architecture.md` documents a pending `androidApp` module that wraps
> `:client:shell`. It will fail this rule the day it is added. That is deliberate — the rule was
> written literally rather than with a speculative carve-out, so the question "is a platform entry
> point the one thing allowed through?" gets asked when there is a real module to ask it about.

**8. `sim` and `server` may not depend on a `client/*` module.** The harness and the server run the
simulation, not the app; either would silently acquire a Compose dependency by reaching one.

The **root project is exempt from all three**, because it is the build rather than a module — and
it has to be: it holds a `kover(...)` dependency on every module including `:client:shell`, which
rule 7 would otherwise read as a violation.

## What is not a layer

`:core`, `:sim`, `:server`, `:client:design` and `:client:shell` end in none of the three layer
names, and are deliberately unconstrained by rules 2–4.

`:client:shell` is the one worth stating out loud, because it looks like an exception and is not.
It holds real Compose UI — `MainScaffold`, the tab bar, the tab icons, the resource rail — *and*
depends on `:client:save:data` and `:client:notifications:data`. That is the composition root's
whole job: it is the one module that may see every layer, which is also why nothing depends on
**it**. Davide's call (2026-08-07), taken over splitting the shell's UI into a
`:client:main:presentation`: the two decisions that put that UI there (0.0.11 navigation, 0.0.12
the resource rail) were made on their own merits and the rule was not written to overturn them.

The rules key off the layer *name*, so this exemption costs no allowlist and no annotation — a
module is constrained exactly when it calls itself a layer.

## Features never see each other — warned, not failed

`:client:<a>:*` depending on `:client:<b>:*` prints a warning and lets the build through:

```
Module rules: :client:research:presentation depends on :client:colony:presentation, so the
research feature sees the colony feature — features are meant not to. Worth a second look.
```

**`design` is not a feature**, and is excluded by name. Since 0.0.14 the design system is a
directory of layer modules, structurally identical to a feature, so the path alone reads it as one
— but it is the opposite: shared vocabulary every feature is *meant* to depend on. Left in, it
fired this warning nine times on a clean build, which is how a warning stops being read.

Davide's call (2026-08-07): the rule is real — it is what sent the tab bar and the resource rail
into the shell — but it has exceptions worth weighing one at a time, and a hard failure would
decide them in advance. The warning appears on the build that introduces the edge, because that
is the build whose script change invalidated the configuration cache.

## Worked examples — the fixes that came with the rules

**A module cannot grow a layer underneath it — so the module becomes a folder.** `:client:design`
was a single module, and the design system needed five: tokens, icons, components, formatting, and
the shared Roborazzi options. `:client:design:component` was impossible, because `client/design`
held a `build.gradle.kts`. The fix is rule 1's canonical shape, and 0.0.14 did exactly it —
`client/design` became a *folder* and all five landed as siblings inside it:

```
client/design                 ->  client/design/core        (:client:design:core)
client/design/component  ✗        client/design/icon        (:client:design:icon)
                                  client/design/component   (:client:design:component)
                                  client/design/format      (:client:design:format)
                                  client/design/screenshot-testing
```

The cost is the one the rule always charges for deferring: renaming `:client:design` meant moving
every import of its package. Cheaper the earlier it is paid.

**A testing module is a sibling named for what it doubles, never a child.** The convention read
"KMP modules that cannot host fixtures get a sibling `:<module>:testing`", which is not a sibling
at all — `:client:save:data:testing` is the directory `client/save/data/testing`, a module inside
a module. Davide's replacement (2026-08-07) is a true sibling, taking the name of what it doubles
plus `-testing`:

```
client/save/data/testing  ->  client/save/data-testing     (:client:save:data-testing)
                              client/featA/domain-testing  (:client:featA:domain-testing)
                              core-testing                 (:core-testing)
```

**And one module was built on the old wording before the rule landed.** 0.0.14 extracted the
thrice-copied `oltreRoborazziOptions` into `:client:design:testing`. That name is rule-1 legal —
`client/design` is a folder, so `testing` is a sibling of `core` and `icon`, not a child of
anything — but it is not `<module>-testing`, and rule 5 matches on that suffix, so **nothing
stopped a `commonMain` depending on it** and pulling Roborazzi into the shipped app. Davide's call
was one shape rather than two, so it was renamed:

```
client/design/testing  ->  client/design/screenshot-testing   (:client:design:screenshot-testing)
```

Which also reads better, because that module is the awkward case: it doubles nothing. It is shared
screenshot *config*, so "named for what it doubles" has no answer and the name has to say what it
is instead. Its package stays `dev.fardavide.oltre.client.design.testing` — a dash is not a legal
package segment, and renaming it would touch eight imports to no effect.

Note what a testing module is *not* for: inside a single module `commonTest` already shares a fake
and no module is involved — `FakeSaveFile` lives in `client/save/data/src/commonTest` and stays
there. One earns its existence only when a **second** module needs what it holds, because a test
source set is not visible to consumers and KMP cannot host a test-fixtures source set.

## Adding a module

The `architecture` skill owns the checklist. The two things these rules add to it:

1. Put the new module where nothing above it is already a module. If the natural parent has a
   `build.gradle.kts`, you are about to break rule 1 — make the sibling instead.
2. Name it for its layer only if it *is* that layer. `presentation`, `domain` and `data` are
   promises the build will hold you to; a module that legitimately spans them (a composition
   root) must be named something else, and must be able to justify it.
