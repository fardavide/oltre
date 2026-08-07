---
name: module-rules
description: Oltre's four module rules — a module cannot contain a module, and domain/presentation/data may not see each other's forbidden side. Enforced by the build, not by review.
when_to_use: >
  Consult before creating a module or a directory that will hold one, before adding any
  `implementation(projects.…)` / `api(projects.…)` line to a build.gradle.kts, and whenever a
  build or an IDE sync fails with "Module layout rule violated" or "Module dependency rules
  violated". Also when deciding where a shared component, a fake, or a test helper belongs.
---

# Module rules

Four rules. All four fail the build **and the IDE sync** — they are checked while Gradle
configures, not by a task you can forget to run and not by a reviewer who might not notice.

| # | Rule | Checked in |
|---|---|---|
| 1 | A module cannot contain another module | `settings.gradle.kts` |
| 2 | `domain` may not depend on `data` or `presentation` | root `build.gradle.kts` |
| 3 | `presentation` may not depend on `data` | root `build.gradle.kts` |
| 4 | `data` may not depend on `presentation` | root `build.gradle.kts` |

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

A module's **layer is the last segment of its Gradle path**: `:client:save:data` is data,
`:client:colony:presentation` is presentation. Only `domain`, `data` and `presentation` are
layers. Each forbidden edge is forbidden for its own reason, not for symmetry:

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

Davide's call (2026-08-07): the rule is real — it is what sent the tab bar and the resource rail
into the shell — but it has exceptions worth weighing one at a time, and a hard failure would
decide them in advance. The warning appears on the build that introduces the edge, because that
is the build whose script change invalidated the configuration cache.

## Worked examples — the fixes that came with the rules

**A testing module is a sibling of the module it doubles, never a child.** The convention read
"KMP modules that cannot host fixtures get a sibling `:<module>:testing`", which is not a sibling
at all — `:client:save:data:testing` is the directory `client/save/data/testing`, a module inside
a module. It is a peer layer beside `data`, in the feature folder:

```
client/save/data/testing    ->   client/save/testing        (:client:save:testing)
```

Nothing had been built on the old wording yet, so the fix was three documents, not a migration.
A module with no feature folder above it (`:core`) has no peer slot, so if it ever needs one it
takes a top-level sibling directory — `core-testing/` — rather than growing a child.

**Shared test config that belongs to nobody goes beside the features, not inside one.**
`oltreRoborazziOptions()` is in its third identical copy (`:client:shell`,
`:client:colony:presentation`, `:client:research:presentation`), which is the threshold
`.claude/docs/decisions.md` set for extracting it. It doubles nothing, so it belongs to no
feature: it lands as `:client:testing`, a sibling of `:client:design` and `:client:shell`.

**A module cannot grow a layer underneath it.** `PowerMark.kt` argues in its own comment for a
shared UI-components module — it is drawn in both `:client:shell` and
`:client:colony:presentation`, and a path is the kind of code where a typo compiles. That module
cannot be `:client:design:components`, because `client/design` is a module. Either it is a new
sibling under `client/`, or `client/design` becomes a folder holding
`client/design/tokens` + `client/design/components` — and that second shape renames `:client:design`
and every import of its package. Open; Davide's call when the slice lands.

## Adding a module

The `architecture` skill owns the checklist. The two things these rules add to it:

1. Put the new module where nothing above it is already a module. If the natural parent has a
   `build.gradle.kts`, you are about to break rule 1 — make the sibling instead.
2. Name it for its layer only if it *is* that layer. `presentation`, `domain` and `data` are
   promises the build will hold you to; a module that legitimately spans them (a composition
   root) must be named something else, and must be able to justify it.
