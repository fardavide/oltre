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

~~**Standing override (Davide, 2026-08-05): bump only PATCH for now**, even for feature
milestones — minor bumps resume when he says so.~~ **Lifted by Davide on 2026-08-08** ("let's bump
to 0.1.0"), which took 0.0.18 to 0.1.0 for the rail-v2 and depth-pass slice. The table above is
live again: a feature slice takes a minor bump, and the agent initiates it.

**"A feature slice" is narrower than "a feature", and 0.4.2 is the case that drew the line.** The
tilt parallax was bumped to 0.5.0 by the session that built it — new modules, a new sensor, a
changelog entry with three bullets in it — and Davide overruled it on sight: *"This doesn't deserve
a minor bump!!!"* (2026-08-10).

The test the table means is **whether a player can do something they could not do before**, not how
much code arrived. A slice is a row in `status.md`'s roadmap: the tab bar, research, the galaxy,
combat. The Sky pass took a minor because it was a whole graphics pass across every screen; the tilt
parallax is an addition *to* that pass, and a player who never picks the phone up cannot tell it
shipped. New modules are not evidence — `:client:tilt:{domain,data}` is two of them and the feature
is still a background moving a few dp.

So: **when in doubt, patch.** A patch that should have been a minor costs nothing; a minor that
should have been a patch spends a number that is meant to mean something, and only Davide can tell
you it was wrong after the fact.

- Every bump carries a `## Changelog` entry in `README.md`, same PR, newest first, heading
  exactly `### <version> — <YYYY-MM-DD>`. User-facing claims, not implementation notes.
  A bump with a stale changelog is a defect.
- **And a page in the game, in both languages** — since 0.19 the README is not the only place a
  release is written down. `EnglishChangelog` and `ItalianChangelog` in
  `:client:changelog:presentation` each need an entry with the same version, the same date and the
  same number of notes, inside a budget of 40 characters of headline and 90 of note. This is not a
  convention you have to remember: `ReleaseCatalogueIntegrationTest` fails the build when the head of
  the catalogue is not `libs.versions.oltre`, when a README heading has no page, and — since #118 —
  when a release that was *published* has no page at all, which is what catches an entry being
  deleted rather than never written.
- After the squash merge the tag creates itself: `release-android.yml` fires on the `main` push
  that changed the catalogue and creates `v<version>` along with the GitHub Release it hangs on.
  Tag by hand only if that job did not run or failed — and if it failed, fix that instead, because
  a missing tag means a missing Android release.
- iOS: bump `MARKETING_VERSION` in `iosApp/project.yml` (single `settings.base` block) to match
  the new `oltre` version, then run `xcodegen generate` in `iosApp/` and commit the regenerated
  project **and its shared scheme**; never edit `project.pbxproj` by hand.
  **If you cannot run xcodegen** (no macOS), bump `project.yml` anyway and say in the PR that the
  generated project is unregenerated. A *local* session on Davide's Mac can and should run it —
  the 0.1.0 bump did, so "every agent session so far" is no longer true; check for the binary
  before assuming you cannot.
  **Check `project.yml` even when you are not the one bumping**: 0.0.18 moved the catalogue and
  left `MARKETING_VERSION` at 0.0.17, so the two drifted for a whole release. The
  `ci_pre_xcodebuild.sh` net meant nothing shipped mislabelled, which is exactly why the drift went
  unnoticed — the net hides this class of mistake rather than preventing it. The *shipped* label is
  safe either way: `iosApp/ci_scripts/ci_pre_xcodebuild.sh` rewrites `MARKETING_VERSION` in the
  pbxproj from the `oltre` version in the catalogue on every Xcode Cloud build, added at 0.0.10
  after 0.0.8 shipped to TestFlight labelled 0.0.7. That safety net is not a licence to skip
  `project.yml` — it is the source the next `xcodegen generate` reads.
- **Do not touch `CURRENT_PROJECT_VERSION`.** It is a placeholder; the shipped build number is
  Xcode Cloud's own run number, written into the project by
  `iosApp/ci_scripts/ci_pre_xcodebuild.sh` at build time. Bumping it by hand achieves nothing and
  invites a duplicate — App Store Connect refuses a build number that repeats within a release
  train (all builds sharing one `MARKETING_VERSION`).
- **Android needs nothing bumped by hand.** `androidApp/build.gradle.kts` derives both from the
  catalogue: `versionName` is the `oltre` version verbatim, and `versionCode` is
  `major * 10_000 + minor * 100 + patch` (so 0.2.0 is 200). Every bump the table above allows
  moves it upward, which is what the package manager requires of an update. It follows that the
  version string must stay strict `X.Y.Z` — a suffix like `0.2.0-rc1` fails the build rather than
  shipping a wrong code.

## Merging to `main` publishes — on both platforms

Every squash merge to `main` triggers an Xcode Cloud archive that lands on TestFlight (internal
testers). Since 0.2.0 a merge that changes the version also triggers `release-android.yml`, which
builds a signed APK and publishes it as a GitHub Release — public, and the download link anyone is
given. Both are in `.claude/docs/decisions.md`.

So a merge is a release, not just a merge: the changelog entry and `MARKETING_VERSION` must be
correct **before** the PR goes green, because the build ships the moment it merges. The changelog
entry is doubly load-bearing now — it *is* the Android release body, and a version without one
fails the release job rather than publishing an empty page. A build cannot be un-published; the
fix is always a new build.
