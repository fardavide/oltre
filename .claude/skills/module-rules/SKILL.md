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
| 2 | `domain` may not depend on `data`, `presentation` or `ui` | layers |
| 3 | `presentation` may not depend on `data` | layers |
| 4 | `data` may not depend on `presentation` or `ui`; `ui` may not depend on either | layers |
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
`:client:save:data` is data, `:client:colony:presentation` is presentation, `:client:colony:ui` is
ui, and `:client:save:data-testing` is data too. Only `domain`, `data`, `presentation` and `ui` are
layers. Each forbidden edge is forbidden for its own reason, not for symmetry:

- **`domain` → `data` / `presentation` / `ui`.** Domain is the feature's rules; it *defines* the
  interfaces data implements and knows nothing of a screen.
- **`presentation` → `data`.** A screen talks to domain, never to a store or a socket. The day a
  feature grows a domain layer, a presentation that reached past it has to be rewritten rather
  than rewired.
- **`data` → `presentation` / `ui`.** Obvious, and cheap to keep obvious.
- **`ui` → `data` / `presentation`.** A ui module draws and decides nothing, so it is a leaf. The
  mapping into what it renders belongs one layer up, and `presentation` depends on `ui` rather than
  the reverse — a leaf that could see its own mapper is not a leaf.

## `ui` and `presentation` — the split, and when the second one is not there

**`ui`** holds composables and the models they render. **`presentation`** holds the mapping from
`core` or domain state into those models, and anything else that decides rather than draws.

**A `presentation` module is optional and a `ui` module is not.** A feature with nothing to decide
is a `ui` module and no more — the same rule `domain` and `data` already follow, and a
`presentation` that only forwards its arguments is the placeholder layer this prevents.
`:client:debug` is the worked example: `debugReport(...)` in its `domain` already produces exactly
what `DebugSheet` draws, so there is nothing for a presentation module to do.

**A `ui` module should depend on the design system and little else.** It *may* take `core` or its
own feature's `domain` where a model genuinely needs them — a row keyed by a `BuildingType` is the
shape that earns it — but reaching for a *balance* or a *rule* is the signal that a mapping belongs
one layer up. In practice this is what strips `:client:design:format` and `kotlinx-datetime` out of
every ui module: by the time a duration reaches one it is a `String`.

**Where a screen holds its own navigation, the composable goes in `presentation`.**
`:client:galaxy:presentation` is the one presentation module in the build that applies the Compose
plugins, because *which system is on screen* is a decision: `GalaxyScreen` holds it, re-derives the
page whenever it changes, and hands the stateless `GalaxyPage` a frame. The test for a second one is
the same — does the module decide, or does it draw a frame it was handed.

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

**7. Nothing may depend on `:client:shell`, except `:androidApp`.** This is what makes the shell's
exemption from rules 2–4 *safe* rather than merely convenient: it may see every layer precisely
because nothing sees it, so the layers it mixes cannot travel. Take this rule away and the
exemption becomes a hole.

> **The one carve-out.** `:androidApp` is allowed through, by name, since 0.2.0. The edge is
> forced (AGP 9 will not let a KMP module apply `com.android.application`, so the shell cannot
> package itself for Android the way it already packages itself for desktop), it is not new
> (`iosApp/` links the same composition root and escapes only by being an Xcode project rather
> than a Gradle module), and it carries nothing (the shell declares every project dependency as
> `implementation`, so the wrapper sees `App()` and `MainActivity` and no layer module at all).
> The allowlist is `platformEntryPoints` in the root build script. It is a list of names rather
> than a rule about shapes — nothing can check that a module *stays* an entry point — so the next
> module that wants through has to make the argument again rather than inherit this one.

**8. `sim` and `server` may not depend on a `client/*` module.** The harness and the server run the
simulation, not the app; either would silently acquire a Compose dependency by reaching one.

The **root project is exempt from all three**, because it is the build rather than a module — and
it has to be: it holds a `kover(...)` dependency on every module including `:client:shell`, which
rule 7 would otherwise read as a violation.

**A module depending on itself is not a dependency.** Kover is applied to every subproject and
puts each one into its own `kover` configuration, so every module declares an edge to itself. Read
literally that is `:core` depending on a module and something depending on `:client:shell` — it
failed all five CI jobs before self-edges were dropped. If you extend this check, remember that
the graph contains plugin-injected edges as well as declared ones.

## What is not a layer

`:core`, `:sim`, `:server`, `:client:design` and `:client:shell` end in none of the four layer
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

**`dispatch` joined it at 0.13**, and it passes `design`'s test rather than a new one. The dispatch
sheet is one verb raised from two tabs — a world row on Galaxy, a landing on Fleets — so every
consumer of it is a cross-feature edge by construction and the warning would fire on each of them
forever. What makes the exclusion safe is that **nothing points out of it**: `:client:dispatch:*`
depends on `core` and the design system and on no feature at all, so it cannot become the back door
one tab reaches another through. That is the property to check before adding a third name — the list
is `sharedSurfaces` in the root build script, and a new entry has to demonstrate it rather than
inherit it.

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
