---
name: versioning
description: Oltre's version-bump + changelog convention — patch for fixes, minor for feature slices, major only at Davide-called milestones, README changelog in lockstep, tag after merge.
when_to_use: >
  Consult before bumping the version or opening a PR that adds user-visible behaviour — editing
  the `oltre` version in gradle/libs.versions.toml, or when Davide says "bump the version" or
  "cut a release". Also whenever a change lands that warrants a changelog entry.
---

# Versioning

The version lives in **one place**: `oltre` under `[versions]` in `gradle/libs.versions.toml`.
The root `build.gradle.kts` propagates it to every module.

| Bump | When | Who initiates |
|---|---|---|
| Patch `0.0.X` | Bug fixes, corrections with user-visible effect | Agent, by default |
| Minor `0.X.0` | A feature slice lands | Agent, when the slice completes |
| Major `X.0.0` | A big milestone | **Davide only — never propose, never apply** |

- Every bump carries a `## Changelog` entry in `README.md`, same PR, newest first, heading
  exactly `### <version> — <YYYY-MM-DD>`. User-facing claims, not implementation notes.
  A bump with a stale changelog is a defect.
- After the squash merge: `git tag v<version> && git push origin v<version>`.
- iOS: `MARKETING_VERSION` and `CURRENT_PROJECT_VERSION` live in `iosApp/project.yml` (single
  `settings.base` block) — bump both there and run `xcodegen generate` in `iosApp/`; never edit
  the generated `project.pbxproj` by hand. `CURRENT_PROJECT_VERSION` is monotonic, +1 every
  bump. When an Android app module lands, also bump its `versionCode` and record the path here.
