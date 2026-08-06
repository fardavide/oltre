# Balance log

Every tuning round, newest last: what the numbers were, what playing them felt like, what
changed and why. The point is the **feedback loop** — a later session should be able to read
what was already tried and rejected instead of re-deriving it, and should be able to tell
whether a complaint is new or a repeat.

**Every balance number in the game is a placeholder** until Davide says otherwise. They all live
in `core/.../PlaceholderBalance.kt`; the shapes are asserted in `BalanceCurveTest`. This file is
the history, that file is the truth — if the two disagree, the code wins and this file is stale.

## How to update this

When a round of tuning lands:

1. Add a section at the bottom: date, version, the feedback in Davide's own words, what changed,
   and what is still open. His words verbatim — a paraphrase loses the thing that made it
   feedback.
2. Regenerate the current-curve table below with `./gradlew :sim:run`, which prints it.
3. Say what the change is expected to *feel* like, so the next round can check whether it did.

Do not delete a superseded round. A number that was tried and rejected is the most useful thing
in this file.

## Current curves (0.0.8)

Level-1 output: **60 metal / 30 crystal / 15 deuterium per hour**. Output compounds **+25% per
level**, cost compounds **+50% per level**, both floored to whole units at every step.

| Level | metal/h | crystal/h | deut/h | metal mine cost (m/c) | payback of the next level |
|---|---|---|---|---|---|
| 1 | 60 | 30 | 15 | 60 / 15 | 6h |
| 2 | 75 | 37 | 18 | 90 / 22 | 7h |
| 3 | 93 | 46 | 22 | 135 / 33 | 8h |
| 5 | 145 | 71 | 33 | 303 / 73 | 12h |
| 8 | 282 | 137 | 63 | 1,021 / 244 | 21h |
| 10 | 440 | 213 | 97 | 2,296 / 549 | 31h |
| 12 | 687 | 332 | 151 | 5,166 / 1,234 | 45h |
| 15 | 1,340 | 647 | 293 | 17,434 / 4,164 | 78h |
| 18 | 2,616 | 1,262 | 571 | 58,839 / 14,053 | 134h |
| 20 | 4,087 | 1,971 | 891 | 132,387 / 31,618 | 194h |

Daily metal: 1,440 at level 1, 3,480 at level 5, 10,560 at level 10, 32,160 at level 15.

Other levers as of 0.0.8: starting stock 500 metal / 300 crystal (no deuterium); build duration
is base-minutes × level, divided by 1 + robotics level; storage cap a flat 10M per resource;
energy scales all mine output by produced/consumed on a deficit.

> Computed from the curve definitions rather than from a `:sim:run`, which could not be run in
> the session that wrote this (the Gradle build could not resolve AGP — `dl.google.com` was
> blocked by the environment's egress policy). Re-run the sim and correct anything that differs.

## Round 1 — 0.0.3, the first placeholders (2026-08-05)

Invented to get the economy moving, never played: level-1 output 3,600 / 1,800 / 900 per hour,
output scaling **linearly with level** (`rate × level`), cost **doubling** per level, Nanite
Factory based at 1M/500k/100k, no starting stock.

## Round 2 — 0.0.8, human numbers and parallel builds (2026-08-06)

Davide, after playing it:

> "doubling the production on upgrade is ridicolous"
> "the facilities produce too many resource, and upgrades cost too much: as I said, I want human
> numbers, not having 10k mineral on 1h, when the game already started"
> "I think i prefer to allow parallel upgrades, so the facility item itself will show the upgrade
> progress, instead of having a separate item at top"

What changed:

- Output no longer scales linearly with level (`rate × level` doubled output on the *first*
  upgrade, which is what read as ridiculous). It compounds **+25%** instead: an upgrade is a
  raise, level 10 out-produces level 1 by ~7× rather than 10×.
- Level-1 output cut 60× to 60 / 30 / 15 per hour.
- Cost compounds ×1.5 instead of ×2. Nanite base cut to 20k/10k/4k so it sits just past
  Robotics 10 instead of in a different economy.
- Starting stock of 500 metal / 300 crystal added, because at the new rates a cold start meant
  waiting 1.5 hours before the first upgrade was affordable. Deuterium is never granted.
- Upgrades run in parallel, one job per facility; resources are the only limiter.
- Build durations were **not** touched.

Expected feel, to check next round: the first mine upgrade pays back in ~6 hours and level 11 in
~31, so early check-ins should offer a real choice between several affordable upgrades, and deep
levels should start to feel like commitments. Stocks should stay four-digit for the first days.

Watch for:

- **Durations may now be the wrong shape.** Cost grows ×1.5 per level while duration grows
  linearly, so deep levels are gated almost entirely by resources — a level-20 mine costs 132k
  metal but takes only 3h20m. If deep upgrades feel like waiting for money rather than building
  something, the lever is tying duration to cost, OGame-style.
- **The storage cap (10M) now binds nothing** until very deep levels. Whatever eventually raises
  it is still an open design question.
- **Nothing caps simultaneous projects.** Notion's expansion pressures call for "limited
  simultaneous *projects*"; today only resources limit them.
- Saves from before this round are retired rather than migrated — see `decisions.md`.

## Round 3 — 0.0.12, the research branch (2026-08-06)

The first numbers in the game that are **not placeholders**. They come from the 0.1 research
decision sheet Davide approved, they live in `ResearchBalance` rather than `PlaceholderBalance`,
and `ResearchBalanceTest` pins all three published tables value by value.

Shape: `cost(level) = base × 1.5^(level−1)` · `duration(level) = base minutes × level ÷ (1 + 0.08 ×
Robotics)` · `effect(level) = rate^level`, compounding.

| Technology | Effect | Bases (m/c/d) | Minutes × level | Requires |
|---|---|---|---|---|
| Photovoltaics | Solar Plant output × 1.10^level | 300 / 150 / 100 | 60 | Robotics 1 |
| Extraction | Metal and Crystal Mine output × 1.08^level | 600 / 400 / 200 | 90 | Robotics 1 |
| Enrichment | Deuterium Synthesizer output × 1.14^level | 500 / 700 / 200 | 150 | Extraction 3 |

Reference colony for every payback figure below: Metal 12 / Crystal 10 / Deuterium 8 / Solar 12 /
Robotics 4, producing 698 / 224 / 72 raw per hour, pricing resources against metal at 1 : 2 : 3.

| LV | Photovoltaics | Extraction | Extraction payback | Enrichment | Enrichment payback |
|---|---|---|---|---|---|
| 1 | +10% | +8% | 21h 49m | +14% | 3.5d |
| 3 | +33% | +26% | 42h 06m | +48% | 6.0d |
| 6 | +77% | +59% | 4.7d | +119% | 13.7d |
| 10 | +159% | +116% | 17.5d | +271% | 41.0d |

Cumulative to Extraction 6: 12,469 metal / 8,313 crystal / **4,157 deuterium** — 2.4 days of the
reference colony's deuterium income against thirteen hours of its metal income. Deuterium is the
price; metal and crystal are there to make the cost chips mean something.

Regenerate this with `./gradlew :sim:run`, which now prints the research tables alongside the
building curves.

What the sheet expected it to feel like, to check next round:

- Research should **beat the marginal mine upgrade early and lose to it late** — Extraction crosses
  over around level 6, against a Metal Mine 12→13 that pays back in 2.8 days.
- A deficit should now have **two answers with different shapes**: build the plant (metal, now) or
  research it (deuterium, over hours).
- **Enrichment's payback is the worst of the three on purpose.** Deuterium cannot be traded for
  progress, so any exchange rate understates it, and the branch is more interesting when the
  efficient buy and the unblocking buy are different rows. If it plays as a trap, the sheet says the
  lever is the Deuterium Synthesizer's base rate, not Enrichment's multiplier — not the obvious one.

Still open, deliberately:

- **Compounding or linear effects.** Compounding was chosen to keep late levels alive; it is one
  constant per technology to switch. Watch it if the horizon ever goes past a month.
- **Three technologies or four.** Automation — build duration ÷ 1.06^level, bases 1000 / 600 / 500,
  180 min × level, requires Robotics 5 — is fully specced in the sheet and deferred: in a game where
  you only act at check-in, shaving 30% off a build changes nothing you can use until the next
  session, and its deuterium cost to level 6 is six days of income against a benefit you cannot
  feel. Kept here so 0.2 can add it as a fourth row without re-deriving anything.
- **Storage stays a flat 10M cap.** The sheet rejected a storage technology for 0.1: at these rates
  the cap is 438 days away, so it would be a row that buys nothing sitting next to two that buy
  something. When it does start to bite, the sheet's proposal is that the *mine* raises its own
  resource's cap — no UI, no new decision. That reopens the closed set of six only if Davide wants
  storage to be a decision rather than a consequence, and that is a bigger call than this branch.
- **The two Robotics divisors disagree by design** (research ÷ 1 + 0.08 × Robotics, construction
  ÷ 1 + Robotics). See `decisions.md`; unifying them is a colony rebalance, not a research one.

Durations were **not** touched for buildings, so the 0.0.8 watch item stands: deep building levels
are still gated almost entirely by resources rather than by clock.
