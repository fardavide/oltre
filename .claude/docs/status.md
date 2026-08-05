# Status

Updated: 2026-08-05

## Landed

- **0.0.1 scaffold** — KMP monorepo (`core`, `sim`, `client/design`, `client/shell`, `server`),
  full local build green including iOS framework link.
- **CI** — jobs "Build (JVM & Android)", "Unit tests", "Screenshot tests", "iOS framework";
  first run green on `main`. All are required checks — keep the ruleset payload in sync when
  jobs change.
- **`protect-main` ruleset** — active, verified with `gh ruleset check main`.
- **AI harness** — CLAUDE.md, `.claude/docs/`, project skills, permissions.

## Next

- Walking skeleton (Phase 6): one vertical slice end-to-end TDD — first `core` rules with the
  `advance` composability property test, thinnest Colony screen slice, first Roborazzi baseline.

## Pending / not yet set up

- `iosApp/` Xcode wrapper — arrives with the iOS wiring slice.
- Android app entry point (thin `androidApp`-style module) — when Android delivery matters.
- v1 slice breakdown (3 resources, 6 buildings, 4 ship types, 1 research branch, procgen galaxy,
  3 AI empires, notifications, JSON save) — to be decomposed and recorded **here** when feature
  work starts.
- No linter (detekt) configured yet — decide when code volume justifies it.
