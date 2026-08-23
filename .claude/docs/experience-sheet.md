# Experience decision sheet — 0.17

Opened by Davide, 2026-08-22, as the follow-up
[`player-strip-sheet.md`](player-strip-sheet.md) §3 named:

> *"In the last PR we added design for levels. Let's imagine a 1-day player must be around Lv 3,
> 1-week lv 10, 2 weeks lv 15, 1 month lv 25. To give a very rough estimate. I imagine most of the
> actions give exp: upgrade, survey, travels, build ship, etc. […] make it so next time I start the
> game it gives me experience for everything I did before."*

Three things are in that: a **pacing target**, a **list of sources**, and a **retroactive
requirement**. The third is the one that decides the architecture, so it is §1.

---

## 1. Nothing is stored. The level is a fold over the event log

`GameState.eventLog` has recorded every completed build, project, ladder, hull, survey and run since
the format's first version, and schema 1 is the only one this build refuses — so **every save a
player can still open carries its whole history**. The experience is `experienceOf(eventLog)` and the
level is a threshold lookup on it.

That answers *"give me experience for everything I did before"* by construction rather than by
migration, and it is the only answer that could: a stored field would need the 15 → 16 hop to state
what an existing colony's experience is, and there is no honest number — zero confiscates a fortnight
of play and anything else is invented at the keyboard. `player-strip-sheet.md` §3 made exactly this
argument in the other direction, to refuse a field; the fold is what it was holding out for.

**Consequences, both worth stating:**

- `GameSave.SCHEMA_VERSION` does not move. 0.17 reads a 0.16 save unchanged and the player opens on
  the level they had already earned.
- **The day the log is ever trimmed, this breaks.** Nothing trims it today and nothing plans to. If
  something does, the answer is a checkpoint on the envelope — *"the experience of the entries that
  were dropped"* — which is a question a migration can answer honestly.

The cost is that the sum is recomputed rather than read. The sim's thirty-day player logs ~2,100
entries; the arithmetic is not worth caching.

---

## 2. Completions pay. Starts do not — Davide's call, 2026-08-22

Put to him against "on the tap" and "both, split". He took completions.

It is also the answer that belongs to *this* game rather than to a game with a progress bar in it:
everything here happens while the app is closed, so a bar that only moved under a finger would be the
one reading on the screen with nothing to do with being away. A player who checks in after a night
finds the mines up, the probe home **and** the level moved, on one clock.

Six of the twelve members of `Event` are therefore worth nothing, and `ExperienceBalance.awardFor`
names all six rather than defaulting them — a thirteenth event has to be priced by whoever adds it.

---

## 3. What you did, not what you own

No award reads a cost, a cargo or a stock.

A run home pays the same whether it lands 200 units or 200,000, because the run is the decision and
the cargo is the economy's answer to it. An award that scaled with cargo would compound with the
mines, and the level would quietly become a second resource counter — the thing the badge is
least useful as, given there is a resource rail directly beneath it.

What *does* scale is **depth**. A level-20 mine is worth more than a level-2 one, because it is a day
of waiting rather than four minutes of it, and that is the one honest difference between two
otherwise identical taps.

### The hull, and why it is small — Davide's call, 2026-08-22

Put to him as three options: one award per *order*, per hull but small, per hull at full price. He
took **per hull, small**.

The measurement behind the question: hull purchases are paid out of income and income compounds, so
`:sim:run`'s thirty-day player owns **1,721 skiffs against 79 finished facilities**. At a facility's
price they would have been four fifths of every point in the game by the end of the first month, and
more than that by the second. At `HULL = 15` — an eighth of the shallowest facility level — they are
17.8%. `ExperienceTest` pins the *ratio* rather than the constant, so a later round can move both
without losing the call that set them apart.

### The award table, as shipped

| Completion | Experience |
|---|---|
| a facility level | `100 + 20 × level` |
| an applied technology or an adaptation rung | `150 + 30 × level` |
| a probe landing | `200 + 50 × worlds found` |
| a fleet coming home | `150` |
| a hull off the slipway | `15` |

Applied research and the ladders are priced the same because they are the same kind of thing from the
level's point of view — a project that holds a slot for hours — and `AdaptationBalance` already
prices its three ladders deliberately equal for the same reason. A probe is the dearest base because
it is the only verb whose payoff is *information*: everything else adds to a colony that already
existed, and this adds to the map.

---

## 4. The ladder is a straight line, and that is a measurement rather than a convention

Most games make a level cost a geometric step. This one costs
`1,100 + 360 × level`, so the total is a quadratic. The reason is one reading:

> **Experience accrues almost exactly linearly in time.** The sim's player earns 4,685 points on day
> one and 4,818 a day averaged over thirty — within 4% across the whole month, while the colony's
> income grows by two orders of magnitude.

That is the five-minute check-in loop working: a player has roughly the same amount of *deciding* to
do in the first week as in the fifth, even though what they are deciding about has grown enormously.
Davide's four marks, meanwhile, are a power law — 3, 10, 15 and 25 sit on `3 × days^0.62` to within a
level at every one. Linear experience against a power-law level means experience per level has to
grow like `level^0.6`, and a straight line is the integer curve that tracks that over the range
anybody will play. A geometric ladder is the right shape for a game whose *income* is the score, and
in this one the income is not.

**No straight-line ladder hits all four marks exactly, and that is arithmetic rather than a failure
to try.** Fitting day 1 and day 7 forces a step below 381; fitting day 14 and day 30 forces one above
385. The shipped constants land:

| At | measured | Davide's mark |
|---|---|---|
| day 1 | **Lv 3** | Lv 3 |
| day 7 | **Lv 11** | Lv 10 |
| day 14 | **Lv 16** | Lv 15 |
| day 30 | **Lv 25** | Lv 25 |

Chosen over the alternative that lands 3 / 10 / 15 / 24 because the two ends are the marks anybody
will actually check, and because the bot over-buys hulls — a realistic month-thirty fleet puts a real
player nearer 23 either way, so the generous setting is the one that lands closest for a person. Both
were within *"a very rough estimate"*; if the middle reads too fast on a device, the dial is
`LEVEL_STEP`.

---

## 5. What the level does: nothing, deliberately, for now

It is a record. Nothing is gated on it, nothing is unlocked by it, no rate reads it. That was not
asked for and inventing it would be inventing a mechanic.

**It is the obvious next question and it is Davide's.** The two shapes worth naming when he asks:
levels as *gates* (a verb opens at Lv 10) would put the progression system in front of the content,
which the Robotics-Factory gates already do and the gate clock says is the slowest part of the
opening; levels as *rewards* (a level pays a one-off grant) is additive and cannot lock anybody out.
The second is the safer of the two on this game's own evidence.

---

## 6. Open, and what would move each dial

- **Nobody has held it.** The whole of §4 is a curve fitted to a bot. What a device answers is whether
  Lv 3 on the first evening reads as *earned* or as *given*, and whether the gap from 16 to 25 across
  the back half of the month reads as a plateau. Same shape as the tilt loop — see
  `session-roles.md`.
- **Probe-spam is the one grind vector, and it is buyable.** A survey pays the dearest base in the
  table and a probe costs 150 metal plus a scout that comes home, so a player who buys ten scouts can
  run ten concurrent probes and level faster than one who plays the colony. The bot is capped at one
  scout and reaches 36% of its points from surveys even so. Not obviously wrong — a player who
  explores hard *is* playing — but it is the first thing to look at if levelling feels degenerate.
  The dial is `SURVEY_BASE`.
- **A level-up is not announced.** The badge changes and the gauge resets; there is no notice, no
  sound and no sweep. That is a design question with a visual half, so it belongs to Claude Design
  rather than to a session inventing one.
- **The name is still a constant.** `player-strip-sheet.md` §3's *"a player-chosen name genuinely
  needs `core`"* is untouched by this slice.

---

## 7. What this slice also fixed, because it could not be measured otherwise

**Three of `:sim`'s four bots had stopped surveying at 0.15 and nothing said so.** A probe consumes a
`SCOUT` from that version; `openingReport` was taught to buy one and `fleetRun`, `depositRun`, the
interaction census and the milestone table were not — so `startSurvey` refused them silently and every
one of those reports printed a probe column that was structurally zero. It surfaced here only because
a survey is one of the four sources Davide named and the first run of the experience report showed
**zero surveys in thirty days**.

The rule now lives in one place (`boughtScoutIfNeeded`) rather than four. Balance-log round 32 lists
what moved in the reports that had been running blind.
