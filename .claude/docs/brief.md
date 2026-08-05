# Oltre — project brief (distilled from the kickoff prompt, 2026-08-05)

## Source of truth

The design lives in Notion: **👾 Game Projects → "Oltre"**
`https://app.notion.com/p/3b383937c1be81af9036dce2ecaf7d57`

Everything there is decided unless it says otherwise. **Notion is read-only for agents** — never
write, update or create a Notion page. If something there is wrong, stale or missing, say so in
chat instead.

## What Oltre is

Persistent asynchronous space colonisation strategy in the OGame lineage. 5–10 minute check-in
sessions; everything progresses while the app is closed. Kotlin Multiplatform, iPhone is the
delivery target, desktop is the dev loop, multiplayer is the destination (v1 is local
single-player against 3 scripted AI empires).

v1 feature set (scoped on Notion): 3 resources, 6 buildings, 4 ship types, one research branch,
large procedurally generated galaxy, 3 AI empires, local notifications, JSON snapshot save.

## Architecture invariants (decisions, not preferences)

1. **`core` is pure.** No I/O, no logging, no platform APIs, no framework types. Only
   `kotlinx-datetime` and `kotlinx-serialization` may ever be added, each justified first.
2. **`core` never reads the clock.** Time enters as a parameter:
   `fun advance(state: GameState, from: Instant, to: Instant): GameState`.
   The most important rule in the codebase — it makes the simulation deterministic, testable,
   fast-forwardable, and reusable unchanged on the server.
3. **Randomness is explicitly seeded:** `fun resolve(a: Fleet, b: Fleet, seed: Long): BattleReport`.
   Same inputs, same output, always.
4. **State changes are an append-only event log**, not in-place mutation.
5. **No game engine.** Galaxy map is a Compose `Canvas`. Settled; do not revisit.
6. **`client` and `server` both depend on `core`. `core` depends on nothing.**

Required property test, everything downstream depends on it:

```kotlin
advance(s, t0, t2) == advance(advance(s, t0, t1), t1, t2)   // for any t1 between t0 and t2
```

## UI direction

[docs/ui-mockup.html](../../docs/ui-mockup.html) is the design brief for the client (Colony +
Galaxy at iPhone size): resource rail with rates, one hero "in progress" card with live
countdown, returning-fleet strip, affordability by colour (time-until-affordable instead of dead
buttons), 5-tab bar, canvas map over a tappable system list. Translate into idiomatic Compose,
do not port literally.

## iOS constraints

No background execution: **never run a timer** — compute state from `lastUpdatedAt` on
foreground. Local notifications (UNUserNotificationCenter) at computed completion/arrival
timestamps are the entire check-in loop.

## How Davide works

- TDD, tests first. Small commits, one logical change each, conventional messages.
- Senior Android/Kotlin engineer — skip explanations of basics.
- **Design decisions are his.** Balance numbers, mechanics and scope come from Notion or from
  him. Placeholder values live in one place, marked as placeholders. If Notion doesn't answer
  something, ask rather than invent.
- If instructions and Notion genuinely conflict, stop and ask.
