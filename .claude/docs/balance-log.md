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

## Current curves (0.1.1)

Level-1 output: **90 metal / 36 crystal / 15 deuterium per hour**. Output compounds **+25% per
level**, cost compounds **+50% per level**, both floored to whole units at every step.

| Level | metal/h | crystal/h | deut/h | metal mine cost (m/c) | payback of the next level |
|---|---|---|---|---|---|
| 1 | 90 | 36 | 15 | 60 / 15 | 4h |
| 2 | 112 | 45 | 18 | 90 / 22 | 4h |
| 3 | 140 | 56 | 22 | 135 / 33 | 5h |
| 5 | 218 | 87 | 33 | 303 / 73 | 8h |
| 8 | 425 | 168 | 63 | 1,021 / 244 | 14h |
| 10 | 663 | 262 | 97 | 2,296 / 549 | 20h |
| 12 | 1,035 | 408 | 151 | 5,166 / 1,234 | 30h |
| 15 | 2,020 | 796 | 293 | 17,434 / 4,164 | 51h |
| 18 | 3,945 | 1,553 | 571 | 58,839 / 14,053 | 89h |
| 20 | 6,163 | 2,426 | 891 | 132,387 / 31,618 | 128h |

Daily metal: 2,160 at level 1, 5,232 at level 5, 15,912 at level 10, 48,480 at level 15.

Other levers as of 0.1.1: starting stock 500 metal / 300 crystal (no deuterium); build duration
is base-minutes × level, divided by 1 + robotics level; storage cap a flat 10M per resource;
energy scales all mine output by produced/consumed on a deficit — and that scaling is now on
screen rather than silent (round 3).

> **Regenerated from `./gradlew :sim:run` (2026-08-08).** The crystal column is the only thing
> round 7 moved; every metal figure, both cost columns, the paybacks and the daily totals are
> unchanged from the 2026-08-06 run.
>
> Rounds 2 and 3 wrote this table by hand because `dl.google.com` was blocked in those sessions and
> Gradle could not resolve AGP. It is blocked again for cloud sessions and round 7 hit it too — the
> way through is a build overlay that keeps `:core` and `:sim` and drops the Android target and the
> client modules, none of which the sim touches. The harness itself runs unmodified, so its output
> is still machine-generated rather than retyped.

A greedy week from a cold start, from the same run — upgrade anything affordable once an hour,
cheapest first, mines *and* plant:

```
after 7 days: metal=720 crystal=9,677 deuterium=2,520
buildings: metal 15 · crystal 15 · deuterium 1 · solar 14 · robotics 0 · nanite 0
energy: 700/320 (mines at 100%) — hours throttled by power: 0 of 168
events: 83 (starts + completions)
spent: 158,259 metal / 60,712 crystal — 2.6 : 1, against income at 2.5 : 1
```

**Zero throttled hours is a finding about the strategy, not the curve** (round 3, still true). A
player who treats the Solar Plant as just another cheap upgrade never meets the shortage at all —
solar reached 14 alongside metal 15. Davide's colony hit 55% precisely because he was buying mines
and not plant, which is the choice the mechanic exists to make visible. The sim as written cannot
reproduce his session; a variant that never builds solar would be the one that measures the pain.

The same week before round 7 closed on **49,544 metal against 1,410 crystal**, with all three
purchases on the table blocked by crystal and nothing else. That mountain of unspendable metal is
what round 7 removed, and the closing line above is what it looks like now.

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

## Round 3 — 0.0.12, the shortage nobody could see (2026-08-06)

Davide, with a screenshot of a colony at metal 3 / crystal 2 / deuterium 2 / solar 1:

> "I think I'm struggling way to much with Metal. I'm at the start of the game, and its feels
> too slow to gather some Metal. I dont want to be able to update things no stop, but this seems
> a bit too much, also considering its the more basic meterial, no?"

**The complaint was mostly not about the metal curve.** That colony produced 50 energy and
consumed 90, so `scaleByEnergy` was multiplying every mine by 50/90. The rail read `+51/h` from
a level-3 mine whose real rate was 93/h; crystal read 20 against 37, deuterium 10 against 18.
Nothing in the client mentioned energy — the string "energy" did not appear in a single Kotlin
file under `client/`. The fix he needed was a 16-minute Solar Plant upgrade he was 8 metal short
of, and the game had no way to tell him.

Two changes, and they are independent — keep them apart when judging what the next round felt
like:

1. **The shortage is now visible.** `EnergyBalance` gives the rule a name (produced, consumed,
   `outputPercent`, `surplus`); the colony screen opens with a power card that states the ratio
   as a bar and its consequence as a sentence, in both states rather than only the bad one; each
   facility carries its own signed energy figure while a shortage lasts; the rail's rates take
   the same mark; and the Solar Plant says when one level would end it. No balance number moved
   for this — the same colony produces exactly what it produced before, it just says so.
   (First drafted as a provisional strip under the rail, then replaced wholesale by the imported
   Claude Design treatment — direction 1a of the Energy Screen page.)
2. **Metal base output 60 → 90/h.** The early tree (everything but Nanite) costs 808 metal to
   264 crystal, ~3:1, against production of 2:1. Metal was structurally the bottleneck however
   the colony was played. 90/h makes production 3:1 and `BalanceCurveTest` now ties the ratio to
   the cost curves so they cannot drift apart again.

**The cost of change 2, stated plainly: the early loop got faster.** First mine upgrade payback
goes 6h → 4h, level 10 → 11 goes 31h → 20h. That is in tension with "I dont want to be able to
update things no stop". It was accepted knowingly because the shortage fix already returns +82%
on its own — if the game now feels *too* fast, change 2 is the one to walk back, not change 1,
and the lever is `METAL_PRODUCTION_PER_HOUR` alone.

Watch for:

- **Whether the metal raise was needed at all.** The energy fix alone took that colony from
  51/h to 93/h. If a session after this feels loose, try 60/h again with the shortage visible
  before touching anything else — that combination has never actually been played. The greedy
  week above adds a first data point against 90/h: metal ends at 49,544 and crystal at 1,410,
  so metal is now the resource with nothing to spend it on.
- **Durations are still the wrong shape** (carried from round 2, untouched): cost grows ×1.5 per
  level while duration grows linearly, so deep levels are gated by resources, not by building.
- **Nothing still caps simultaneous projects**, and the 10M storage cap still binds nothing.
- **The energy curve itself is untested by play.** Solar is 50/level against mines at 10/10/20
  per level, so a plant level covers five mine levels — both curves are linear, so that ratio
  holds at level 1 and at level 40 and the tension never escalates. The sim now counts throttled
  hours and reported zero, but only because its greedy strategy buys plant as readily as mines;
  the cadence is still unmeasured for a player who does not. A sim variant that starves solar
  deliberately is the cheapest way to put a number on how bad the shortage gets.
- **Five design calls are still open** on the energy work — direction, the wording of the
  headroom verdict, whether amber may mean "attenuated" as well as "in transit", and whether a
  deficit belongs in a notification. They are listed in the Energy Decision Sheet on the Claude
  Design page and are Davide's to make.

## Round 4 — 0.0.13, the research branch (2026-08-06)

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


## Round 5 — 0.0.15, the galaxy (2026-08-07)

No play-test feedback yet: nothing is playable until the screen lands in slice 5. This round exists
because the galaxy decision sheet published a table of constants (§8) *and* a table of targets (§9),
said the targets outrank the constants, and the constants had never been run. `:sim:run` now prints
the distribution, so this is a measurement rather than an expectation.

These numbers are **decided, not placeholders** — like `ResearchBalance` and unlike
`PlaceholderBalance` — so they live in `core/.../GalaxyBalance.kt` and `GalaxyBalanceTest` pins the
sheet's §8 tables value by value.

Shape: three hostility axes (temperature, gravity, pressure), each checked against a tolerance band
that its own adaptation ladder widens; richness **derived** from those same axes rather than rolled;
hazards as named flags; a yield score that weights each richness by 51 / 33 / 16 — the reference
colony's priced output from round 3 — minus 5% per hazard, against a worth-it threshold of 0.90.

### What the sheet's own constants actually produce

Seed 20260807, all 15,000 slots, 4,746 worlds. Regenerate with `./gradlew :sim:run`.

| Outcome | §9 target | Measured | Verdict |
|---|---|---|---|
| Passes every band | 1 – 2% | **2.63%** | over |
| Fails exactly one axis | 35 – 45% | **17.55%** | far under |
| Fails two or three | the rest | 79.81% | — |
| Passes and clears 0.90 | ≤ 0.5% | **0.71%** | over |

| Axis | Tolerated at level 0 | Passes | Rich in |
|---|---|---|---|
| Temperature | −30 … 45 °C | 25.85% | deuterium |
| Gravity | 0.55 … 1.45 g | 31.47% | metal |
| Pressure | 0.4 … 3.0 atm | 30.46% | crystal |

**Three of the sheet's claims land almost exactly.** The median world that passes every band scores
**0.84** against the 0.90 threshold — the sheet predicted "~0.84" without running it — so the median
settleable world really is `Barren` by construction. Each adaptation level roughly doubles the
settleable count for the first few (34 → 80 → 164 → 299). Hazards land on 45.6% of worlds. Home for
this seed is `[3:165:7]`, a world the unaided species tolerates.

### The one row that cannot be hit, and why it is not a tuning problem

`fails exactly one axis` is at 17.55% against a 35–45% target, and **no choice of constants reaches
it while three comparable axes are kept.** The two rows constrain each other. With independent pass
rates *a*, *b*, *c*, the first row is `abc` and the second is `ab + ac + bc − 3abc`. Holding `abc`
inside 1–2% puts each axis near 0.22–0.27, which caps the second row at about **16%**. The most
*balanced* pass rates that reach 35% at all are roughly **0.06 / 0.58 / 0.59** — one axis blocking
94% of worlds while the other two wave nearly everything through.

That is a galaxy with one ladder that matters and two that do not, which is the single-habitability-
score design §1 rejected, reached from the other direction. So the constants were **not** moved to
chase it: which target gives way is a design call, and moving a tolerance band to hit row 2 would
quietly overturn §1's argument for three axes. Recorded here, open below.

Also noticed while implementing, and worth a line because it is the design's showcase sentence: §3
illustrates `Blocked` with *"gravity 2.4 g, you tolerate 1.45 g. Gravitic Adaptation 3 would land
it."* Against §8's own widening of +0.12 g per level, 2.4 g needs **level 8**, not 3. Either the
sentence is illustrative or the widening should be nearer +0.32 g/level. The code computes the level
from the constants, so it currently says 8.

### Still open, and costing nothing until answered

- **The `fails exactly one axis` target.** Recommendation: keep the three comparable axes and correct
  §9's row to what that shape can produce (~15–20%), then tighten slightly to bring rows 1 and 4
  inside their bands. The alternative — one narrow axis and two wide ones — buys the 35–45% figure at
  the cost of the mechanic the axes exist for.
- **Rows 1 and 4 are modestly over** (2.63% vs 1–2%, 0.71% vs ≤0.5%). Both close with a small
  tightening of any one band, but tightening also pushes row 2 further down, which is why they are
  held until the row above is settled. The sheet expected ~24 settleable worlds galaxy-wide; there
  are 34.
- **Star class distribution.** The sheet gives each class its temperature offset but never how often
  each occurs. Equal thirds is assumed and marked as such in `GalaxyBalance.starClass`. Nearly free:
  because the habitable orbits shift with the offset, each class passes the temperature band on ~25%
  of its worlds either way.
- **Where home is.** The sheet does not say. Genesis walks systems from a seeded start and takes the
  first world the unaided species tolerates, which is close to a tautology for a homeworld but is
  still a rule nobody chose.
- **What `Settleable` carries.** §3 says "the yield grade"; grades are never defined, so it carries
  the raw score and the screen can band it. Bands are a design call for slice 5.
- **Whether `Barren` should say how close it was.** It carries nothing today, per §3. The screen may
  want the score, the way the power card states the ratio before the consequence.

### Resolved — the constants moved, and one target moved with them

Davide delegated the call to the build (2026-08-07): **keep three comparable axes and correct the
target.** What changed, and nothing else did:

| Constant | Was | Now | Why |
|---|---|---|---|
| Gravity band at level 0 | 0.55 … 1.45 g | **0.65 … 1.40 g** | to meet temperature's pass rate |
| Pressure band at level 0 | 0.4 … 3.0 atm | **0.5 … 2.6 atm** | same |
| Worth-it threshold | 0.90 | **0.92** | thins the settleable share without changing which worlds pass |
| §9 `fails exactly one axis` | 35 – 45% | **12 – 18%** | unreachable — see below |

**Temperature was left alone on purpose.** It was already the tightest axis at 25.9%, and its band
is the one tied to the slot formula that makes position a trait. Bringing the other two *down to
meet it* is what lands the distribution while leaving all three gating a near-identical share —
25.9 / 25.3 / 25.0. That last part is the whole point: §1's argument for three ladders is that
*which one you push first* is a real choice, which stops being true the moment one axis blocks
most of the galaxy.

**The yield model was not touched.** Its own prediction — a median passing world at ~0.84 — measured
0.85 before any change, so what was wrong was which worlds pass, not what they are worth. Raising
the threshold to 0.92 keeps the median passing world Barren with room to spare.

| Outcome | Target | Before | After |
|---|---|---|---|
| Passes every band | 1 – 2% | 2.63% | **1.81%** |
| Fails exactly one axis | 12 – 18% (was 35 – 45%) | 17.55% | **13.88%** |
| Passes and clears the threshold | ≤ 0.5% | 0.71% | **0.35%** |
| Each level doubles the settleable count | roughly | 34 → 80 → 164 | **17 → 40 → 105 → 218** |

**Why the middle target moved instead of the constants.** Rows 1 and 2 constrain each other: with
three independent axes passing at *a*, *b*, *c*, row 1 is `abc` and row 2 is `ab + ac + bc − 3abc`.
Holding `abc` inside 1 – 2% caps row 2 near **16%** for any three comparable axes. The most balanced
pass rates that reach 35% are ~0.06 / 0.58 / 0.59 — one axis blocking 94% of worlds. That is the
single-habitability-score design §1 rejected, from the other side, so the row was corrected rather
than chased.

### Watch next round

- **17 settleable worlds galaxy-wide, ~4 per galaxy.** Inside the ≤0.5% bound with margin, but
  stricter than the sheet's illustrative "~24 galaxy-wide, ~6 in your home galaxy". If the first
  settleable world takes too long to find, **the lever is the worth-it threshold** (0.92 → 0.91
  buys roughly a third more) and not the tolerance bands, which are now carrying the axis balance.
- **The "come back later" pile is 13.9%.** The sheet wanted it to be the bulk of the galaxy. It
  cannot be, with this shape — so if surveying feels like it returns "hopeless" too often, the
  honest fix is widening all three bands together and accepting more settleable worlds with it.
- **Blocked worlds now name higher technology levels** — the home system's slot 8 went from
  Gravitic 3 to Gravitic 4 — because the bands are tighter. Worth watching that the shopping list
  still reads as a purchase rather than as a wall.

## Round 6 — 0.0.17, what an adaptation level costs (2026-08-07)

**Not a rebalance.** No existing number moved: not a `GalaxyBalance` constant, not a
`ResearchBalance` table, not a `PlaceholderBalance` curve. Round 5's distribution is untouched and
its "watch next round" list is still open and still unanswered. What this round adds is a price for
something that already had an effect — the three adaptation ladders — and it is recorded here
because it is the first set of numbers in the game that a player pays without seeing a per-hour rate
change.

The numbers and the arguments are in [`adaptation-sheet.md`](adaptation-sheet.md), settled by the
build on Davide's "continue the development", in the same shape as the galaxy sheet's own delegated
round. Every line is his to overrule.

### What was chosen

| Ladder | metal | crystal | deuterium | priced 1 : 2 : 3 |
|---|---|---|---|---|
| Thermal | 900 | 600 | 900 | **4,800** |
| Gravitic | 2,400 | 900 | 200 | **4,800** |
| Atmospheric | 850 | 1,600 | 250 | **4,800** |

×1.5 per level, the game's one cost curve. Duration 240 minutes × level on research's gentle
Robotics divisor — 3h 02m for level 1 at Robotics 4, 15h 09m for level 5. Gate: Robotics Factory 4,
the same for all three. One shared research slot with the applied branch.

**The one idea in the table:** each ladder is priced in the resource its own axis makes rich, and
the three cost identically once priced at 1 : 2 : 3. So the choice of first ladder falls out of what
the colony already has in the bank rather than out of which is cheapest — and the ladder that would
fix the shortage a player actually has is the one they cannot yet pay for.

### Reference points it was set against

- Enrichment level 1, the priciest applied technology, is **2,500** priced. Adaptation level 1 is
  **4,800** — the branch that changes the map is the expensive one, by a factor of about two.
- Round 5's greedy week ends on 49,544 metal / 1,410 crystal / 2,520 deuterium. A metal-heavy colony
  of that shape can afford **Gravitic 1** almost without noticing and cannot afford Thermal 1 at
  all, which is the intended asymmetry landing on the only colony that has actually been measured.
- Gravitic 12 — the level past which the ladder buys nothing — costs **415,000** priced, roughly
  eight greedy weeks of metal. Saturation is priced out of reach rather than capped.

### What to watch, and what to move first

- **Does losing a production level to an adaptation level sting?** It should, and it should still be
  worth it. If it never stings the base cost is too low; if it always loses, too high. **The base
  cost is the lever, not the ×1.5 curve and not the widening** — the widening is carrying the
  galaxy's axis balance and moving it re-opens round 5.
- **Is Robotics 4 reachable at the moment a player first reads a blocked row?** Round 5's greedy
  sim reached **Robotics 0** in a week, because it only ever bought mines and plant. Nothing
  measures a player who buys Robotics. If the gate turns out to sit far past the first BLOCKED
  screen, lowering it to 2 or 3 is cheaper than re-pricing anything.
- **Nothing here has been played**, because no screen sells it yet. These numbers are arithmetic
  against measured reference points, not feedback. The first round that can say anything real is the
  one after the Research screen lands.

## Round 7 — 0.1.1, the resource everybody was waiting for (2026-08-08)

Davide, after playing it:

> "The cristal seems to be a bit too slow to farm. Don't take my work as right, run the sim and
> judge by yourself. You can simply tell me that I'm wrong."

He was right, and by more than "a bit". This is the first round where the sim was extended to
answer the complaint rather than only to regenerate the tables, so the numbers below are
measurements and not arithmetic.

### What the sim was taught to measure

Three additions to `:sim:run`, all of them things no previous round could see:

1. **A sole-blocker ledger.** Each simulated hour, for every purchase the strategy wants, which
   resources are short. An hour counts for a resource when some purchase is short of *that resource
   and nothing else* — the player has the rest of the price in the bank and is waiting on one mine.
2. **A spend ledger.** What a run actually paid, as it paid it. The ratio between those totals is
   what the income curve should be tuned against; every previous round tuned against a *basket of
   base costs* instead, which is a different number and, as it turns out, the wrong one.
3. **A second strategy — "everything the game sells".** The 0.0.12 greedy week buys mines and plant
   and nothing else, which are the two most metal-heavy things in the game. It structurally cannot
   see what applied research and the adaptation ladders ask of crystal, because it never buys
   either. The new run buys every building, and keeps the shared slot busy with whichever project
   of either branch is cheapest.

The 0.0.12 week is otherwise untouched, down to its sort key, so its closing line still reproduces
byte for byte and stays comparable with rounds 3 to 6.

### The finding

| Measured at 90 / 30 | Greedy week (7d) | Whole tree (14d) |
|---|---|---|
| Hours with a purchase blocked by **crystal alone** | **130 of 168** | **306 of 336** |
| Hours blocked by metal alone | 20 | 15 |
| Hours blocked by deuterium alone | 0 | 168 |
| Closing stock | 49,544 metal / **1,410 crystal** | 248,130 metal / **5,740 crystal** |
| Realised spend, metal : crystal | 2.8 : 1 | 2.2 : 1 |

Both runs end with *every* purchase on the table blocked by crystal and nothing else, next to a
mountain of metal with nothing to buy. That is Davide's sentence, measured.

**Why, when round 3 tied the ratio to the cost curves precisely so this could not happen?** Because
it tied it to the wrong basket. `BalanceCurveTest` averaged the level-1 cost of the whole early
tree — and that basket includes the Robotics Factory (3.3 : 1) and the Deuterium Synthesizer
(3 : 1), the two most metal-heavy rows in the game and the two a player buys a handful of times in
a game rather than a level of every session. Including them as equals pulled the target from the
2.65 : 1 the *repeating* basket costs up to 3.06 : 1, and production was set to match.

Two branches have shipped since, and neither costs anything like 3 : 1 — applied research is
1.1 : 1 and the adaptation ladders are 1.3 : 1. So the gap widened after round 3 rather than
holding.

A second, independent symptom of the same thing: priced at the game's own 1 : 2 : 3, **the Crystal
Mine paid back 1.6× slower than the Metal Mine at every single level** — the curves are the same
shape, so the factor is a constant. The answer to a crystal shortage was the worst purchase in the
game.

### What changed

**`CRYSTAL_PRODUCTION_PER_HOUR` 30 → 36, and nothing else.** Income goes from 3.0 : 1 to 2.5 : 1,
which is what the repeating basket costs (183 : 69) and the middle of every spend ratio measured
(2.0 – 2.8 : 1). Metal stays the plentiful basic material, which is how Davide described it in
round 3; it stops being the *only* plentiful one.

`BalanceCurveTest` now bounds the ratio **in both directions** and against the repeating basket.
The one-sided bound it replaces could only ever catch metal being too poor — the 0.0.12 failure —
and waved through metal being too rich, which is this one.

| After, at 90 / 36 | Greedy week (7d) | Whole tree (14d) |
|---|---|---|
| Blocked by crystal alone | 130 → **0** | 306 → **190** |
| Blocked by metal alone | 20 → 167 | 15 → 116 |
| Closing stock | **720 / 9,677** | 179,352 / 5,763 |
| Realised spend, metal : crystal | 2.6 : 1 | 2.4 : 1 |
| Crystal Mine payback penalty | 1.6× → **1.3×** | — |

The fortnight also gets materially further in the same 14 days — Robotics 8 → 10, Metal Mine
16 → 18, every ladder 4 → 5 — which is the idle metal finally being spent rather than new income.

### Watch next round, and what to move first

- **The lever is `CRYSTAL_PRODUCTION_PER_HOUR` alone, and 33 – 37 are all defensible.** The
  two-sided test admits 32 – 37; 36 was chosen as the midpoint of measured demand. If crystal now
  feels *loose*, 34 is the smaller step; the shape of the finding does not change anywhere in that
  band.
- **The greedy week now flips to metal-blocked (167 of 168 hours), and that is expected rather
  than a new bug.** A greedy strategy spends to zero every hour, so it is always blocked on
  *something*; the reading is which, and how much sits idle. Idle metal falling from 49,544 to 720
  is the result. The week is also lumpy — costs step ×1.5, so affordability is chunky and a single
  run swings hard on small changes. **The fortnight is the more reliable signal**; do not tune off
  the week alone.
- **Deuterium is now the second-worst blocker** — 168 of the fortnight's 336 hours, and the run
  ends unable to afford Robotics 11 for want of 6,422 of it. Round 4 made deuterium "the price" on
  purpose, so this may be working as designed, but nothing has ever measured it. It is the obvious
  candidate for round 8.
- **The Crystal Mine is still the slower buy (1.3×)** and probably should be — you buy it because
  you need crystal, not because it pays back. Recorded so the next session knows it was seen and
  left alone. The lever, if it ever matters, is its 48 / 24 base cost, not the production curve.
- **Round 3's standing question is now answered, and the answer is no.** It asked whether the
  metal raise to 90/h was needed at all, and proposed trying 60/h again. Dropping metal would fix
  the *ratio* without making crystal one unit faster — it answers a complaint about the game being
  too fast, which is not the complaint that was made. Measured for completeness: at 60 / 30 the
  fortnight is metal-blocked 322 hours of 336. Filed as tried-and-rejected.
- **Nothing here has been played yet.** These are sim measurements against a greedy strategy, not a
  session. The first round that can say anything about how it *feels* is the one after Davide plays
  0.1.1.

## Round 8 — 0.1.1, the check-in with one verb in it (2026-08-08)

**No balance number moved this round.** It is a diagnosis, a benchmark and four rejected
candidates. It is recorded at length because Davide asked for the feedback itself to be kept —
*"Let's save those feedbacks I give you, so that we keep a benchmark"* — and because the thing it
measures is the first one in this log that no amount of tuning fixes.

### The feedback, verbatim

> "Mi sembra che il gameplay sia un po lento e noioso in questi primi due giorni"

Asked whether "slow" meant an empty colony or a slow rate of progress, he rejected both framings:

> "Ho poche cose da fare. Solo premere un tasto"

> "So che mancano varie feature, ma ci vuole parecchio per sbloccare anche quelle che ci sono. Il
> che è buono, ma nel frattempo apri il gioco ogni 2/3 ore e premi solamente un tasto per
> l'upgrade. Sarebbe bello avere altro da fare e controllare"

> "Per essere chiari, non voglio rimuovere il senso di progressione, anzi! Ma vorrei avere qualcosa
> in più da fare, anziché premere un tasto ed aspettare 2/3/4 ore"

> "As said, I don't want the user to have nothing to do for hours, but I don't want it to be forced
> to keep logging it either, to avoid to fall behind. It must be a balance"

> "No. I don't wanna to remove parallel build! There's still a need to decide, as you will use
> resources to chose which to upgrade, you can upgrade them all"

**Read those five together before proposing anything**, because each one closes a door the others
leave open. The complaint is not the rates, not the unlock pace, not the progression curve and not
the absence of a construction cap. It is that a session contains **one verb**.

### What the sim was taught to measure

Every run in this harness before today is an hour-stepped greedy bot over 7 or 14 days. That shape
cannot answer a complaint about the opening: what is being complained about is what a *check-in*
offers, and a runner that acts 24 times a day never has one. It also cannot see idleness at all —
a bot that buys hourly keeps something running by construction.

`printOpeningReport` runs the same buying rule, restricted to four times a day (08:00 / 13:00 /
19:00 / 23:00). Two of its columns are new kinds of reading rather than new numbers:

- **Kinds, not count.** How many *sorts* of decision a check-in offers — a facility, a technology,
  an adaptation ladder. Five facility rows are one verb pressed five times, and the old "how many
  options" column called that five.
- **What a check-in booked.** How far ahead the session set the colony working. The brief calls
  local notifications *the entire check-in loop*; this is the number that loop lives on.

### The finding

| Reading | 0.1.1 |
|---|---|
| Dead check-ins (nothing finished, nothing affordable) | 0 of 8 |
| Median options on the table | 5 |
| **Check-ins offering one kind of thing only** | **6 of 8** |
| **A second kind of decision first exists** | **29 hours in** |
| Hours with nothing in flight | **42 of 48 (87.5%)** |
| Longest unbroken silence | 8h 33m |
| Work the busiest check-in booked | **72 min** |
| Median work a check-in booked | 48 min |

After 48 hours: metal 7 · crystal 7 · deuterium 3 · solar 6 · robotics 2 — 25 levels, Photovoltaics 1.

Three things fall out, and only the first was suspected:

1. **There is no shortage of things to buy.** Zero dead check-ins, five options at the median. The
   round 3 and round 7 levers — metal per hour, crystal per hour — are not what is wrong, and
   moving either would answer a complaint nobody made.
2. **There is one verb for the first 29 hours.** The Research tab is an empty room until the first
   Robotics Factory. The adaptation ladders need Robotics 4, which this run does not reach in two
   days — so the Galaxy screen is read-only for the whole opening, and every `Blocked` row on it
   is decoration. Of five tabs, one does anything on day one.
3. **The colony is idle 87.5% of the time and the busiest session books 72 minutes.** Every
   notification the game can send arrives while the player is still holding the phone; nothing at
   all fires across the gaps. This is the round 2 watch item — *durations are the wrong shape* —
   carried unactioned through rounds 3 and 4, which predicted it would bite at deep levels. It
   bites hardest at level 1.

### Four candidates, measured and rejected

Kept in full, because each is the obvious idea and each is now known to be wrong.

| Candidate | What it did | Verdict |
|---|---|---|
| **Cost-proportional durations** — `(metal + crystal) ÷ 3` minutes, OGame's shape | idle 87.5% → **64.6%**; busiest booking 72 → **224 min**; **identical 25 levels at 48h**; greedy week day 7 mines 15/15/14 → 12/12/11 | **Fixes the emptiness, not the complaint.** Same number of taps, fewer of them per session. Held. |
| **Robotics Factory cheaper** — 400/120/200 → 300/90/120, to open Research sooner | second verb 29h → **24h**; still **6 of 8** one-kind check-ins; idle 87.5% → 85.4% | **Rejected.** Five hours for a cheapened gate, and Davide likes the unlock pace: *"ci vuole parecchio per sbloccare anche quelle che ci sono. Il che è buono"*. |
| **One construction slot** | **11 levels at 48h** against 25; Research never opens at all; still 83% idle; 3,970 metal left unspent | **Rejected, and worse on every axis.** |
| **Two construction slots** | 18 levels at 48h; second verb slips 29h → **39h**; 7 of 8 one-kind; 2,564 metal unspent | **Rejected.** |

**Both caps are rejected by Davide directly, not only by the measurement:** *"I don't wanna to
remove parallel build! There's still a need to decide, as you will use resources to chose which to
upgrade, you can upgrade them all."* The scarcity that makes the colony a decision is the stock,
not a slot. See `decisions.md` — this also closes the "should anything cap simultaneous
construction" question that has been open since round 2.

The two caps also fail for a reason worth keeping: a cap does not fill the gaps it creates. Early
builds are short whatever the cap, so one slot spent 83% of the window empty *and* halved
progress — it removed actions without adding a single hour of cover.

### The constraint that any answer has to satisfy

Davide's fourth line is the hard one, and it rules out the cheap fixes in both directions:

> "I don't want the user to have nothing to do for hours, but I don't want it to be forced to keep
> logging it either, to avoid to fall behind. It must be a balance"

So whatever fills the gap must be **startable in a check-in and harmless to miss**. Anything that
rewards logging in at hour 3 rather than hour 9 fails the second half; anything that idles until
the player returns fails the first. That rules out timed pickups, decaying bonuses, and any
mechanic whose value depends on reaction speed — the whole standard idle-game toolkit, in fact,
which is built precisely to punish absence.

### Still open — and it is a content question, not a balance one

**No number in this file adds a second thing to do.** The honest reading of round 8 is that the
opening is thin because four of the game's eight v1 features are unbuilt, and the two verbs that
exist are gated behind a pace Davide wants kept. What is left to decide is which existing system
grows a second verb first, and that is his call.

The one this log can point at, because the save already carries it and nothing fills it:
**surveying**. `GalaxyState.surveyed` is a per-world set, holds the home system at genesis and is
never added to by anything — so the Galaxy tab shows four worlds forever and cannot be acted on.
Its own comment already says *"surveying is a per-world fleet action from slice #7 onwards"*. A
survey is startable in a check-in, lands hours later, is harmless to miss, gives the player
something to *check* rather than another rate to raise, and costs no progression — which is every
constraint above, met by a system that is half-built rather than by a new one.

Whether that is slice #6's job, slice #7's, or something before both is a sequencing call.

### Watch next round

- **Round 7's nomination still stands and is untouched:** deuterium is the fortnight's second-worst
  blocker, 180 hours of 336. This round did not look at it, because the complaint was not about a
  resource.
- **The duration curve is still the wrong shape**, now for the fourth round running, and now with a
  measurement attached: it costs nothing in the first two days to fix (identical levels at hour 48)
  and ~3 levels by day 7. It is held rather than rejected — it should ride along with whatever
  fills the gaps, not go in alone, because on its own it trades taps for cover.
- **The Robotics construction divisor is ÷(1 + level)** against research's ÷(1 + 0.08 × level): at
  Robotics 4 builds are 5× faster and at Robotics 10 they are 11×, so any duration curve is
  flattened by the building the player is buying anyway. Davide delegated this one to the sim; it
  is measured, unresolved, and pointless to move until the duration shape is settled.

## Round 9 — 0.1.2, the second verb (2026-08-09)

Round 8 ended with a diagnosis and no fix: *"No number in this file adds a second thing to do."*
This is the round that adds one. Davide answered three design calls on 2026-08-09 and the build
measured the rest; every number below came out of `:sim:run` rather than out of an argument.

### What he decided

| Call | Answer |
|---|---|
| Add exploration as a second thing to do? | **Yes — dispatch probes.** The alternative on the table was longer build durations alone, which fixes the emptiness without adding anything to do. |
| Should systems differ from one another? | **Yes — star class should matter**, so "where do I look" is a real question. |
| Should what you find be useful straight away? | **Yes — it guides research**, rather than being a bookmark for a colonisation slice that does not exist. |

### What was built (`core` only — no screen yet)

`startSurvey(state, target, at)`. The same `(state, subject, at) -> Result` shape as the other
three verbs, and different in every way that matters: the subject is a **`SystemAddress`** rather
than one of twelve enum rows, the payload is **knowledge** rather than a rate, and the **player
picks the completion instant** by choosing how far to aim.

Three shape decisions, each load-bearing rather than convenient:

- **Flat cost, distance only in the duration.** Verified against the generator: a system index
  enters *none* of `GalaxyBalance`'s trait functions and reaches `GalaxyGeneration` only as a hash
  salt. Expected payload is therefore identical galaxy-wide, so a distance-scaled cost would make
  far probes strictly dominated — more money, more time, the same information — **and would tax the
  player who is away longest**, which is precisely what Davide refused.
- **Metal only.** Deuterium buys the Robotics Factory, which opens Research at level 1 and the
  ladders at level 4. Pricing the new verb in that currency would add verb two by deleting verb
  three. Round 7 closed its fortnight on 179,352 unspent metal: this is what that metal is for.
- **No Robotics divisor.** Construction divides by (1 + Robotics), research by (1 + 0.08 × Robotics),
  a probe by nothing. Its duration is the one number in the game that is purely the player's own
  choice, and a divisor would let a building quietly shorten cover the player deliberately bought.

Probes run **in parallel**, limited by metal alone — the construction rule Davide settled on
2026-08-08, applied rather than re-litigated. **Nothing gates the verb**: one whose job is to exist
at hour zero cannot sit behind a building, and the unlock pace he likes is protected from the price
side instead.

Also `adaptationShortlist(state)`: per ladder, how many **surveyed** worlds the next level would
unlock and how many of those clear the worth-it bar. This is call 3, and it is what stops waiting
from being better than exploring — `surveyed` is monotone and `verdictFor` re-derives against
current levels, so without a consumer the optimal play is "not yet".

### The price, swept rather than chosen

One dispatch per check-in, aimed at the longest flight that still lands before the next one, bought
**before** the buildings so the levels it costs are visible rather than hidden behind a full queue.

| metal | levels at 48h | probes | what it costs |
|---|---|---|---|
| — | 25 | — | the round 8 baseline |
| 100 | 24 | 8 | one level |
| **150** | **23** | **8** | **two levels, Robotics 1 instead of 2** |
| 200 | 22 | 8 | three levels |
| 300 | 19 | 8 | six levels |
| 500 | 16 | 7 | nine levels, **and Research never opens at all** |

**Every reading the verb exists for is identical from 100 to 300** — eight dispatches, zero
one-kind check-ins, 540 minutes booked by the busiest session. So the price buys exactly one thing:
how much progression a dispatch costs. **150 is the midpoint of the defensible band (100 – 200).**
500 was the first guess and it is simply wrong.

### What it moves

| Reading | 0.1.1 | 0.1.2 at 150 metal |
|---|---|---|
| Check-ins offering one kind of decision only | 6 of 8 | **0 of 8** |
| A second kind of decision first exists | hour 29 | **hour 0** |
| Hours with nothing at all in flight | 42 of 48 (87.5%) | **1 of 48 (2.1%)** |
| Longest unbroken silence | 8h 33m | **0h 47m** |
| Work the busiest check-in booked | 72 min | **540 min** |
| Median work a check-in booked | 48 min | **360 min** |
| Building levels at 48h | 25 | 23 |
| Worlds known at 48h | 4 | **32** |

**Two of those rows are honest and one is a trap, so the report prints both.** A probe in flight
does not make a mine busier: the *colony's* own idleness is 85.4% against 87.5%, essentially
unchanged. What the probe covers is the **player's attention**, and collapsing the two into one
number would let the new verb take credit for a complaint it does not touch. The lever for the
colony standing still is still the held cost-proportional duration curve from round 8.

The greedy week and the fortnight are untouched and still reproduce byte for byte — 720 / 9,677 and
179,352 / 5,763 — so rounds 3 to 8 stay comparable.

### Call 2 answered itself, and the constants did not move

The recommendation was to widen the star class temperature offset to create a per-system gradient.
**Measuring first showed the ±40 °C offset already produces one**, so nothing in `GalaxyBalance`
was touched — no tolerance band, no §9 row, no re-pinned table.

| Star | Passes every band | Settleable | Mean metal | Mean crystal | Mean deuterium |
|---|---|---|---|---|---|
| DIM | 1.73% | **0.43%** | 0.95 | 1.00 | **1.06** |
| STANDARD | 2.10% | 0.40% | 0.97 | 1.00 | 0.93 |
| BRIGHT | 1.62% | **0.24%** | 0.95 | 1.00 | **0.82** |

A **29% swing in mean deuterium richness** from dim to bright and a settleable rate nearly double
at the dim end, both falling out of the offset the sheet already had: it moves orbit temperature,
temperature derives deuterium richness and gates one of the three bands. And `starClassAt` is O(1)
and needs no survey, so the prior is **already charted** — aiming a probe at a dim star because
deuterium is short is a decision the map can support today.

**The honest limit: metal and crystal are flat across all three classes**, because they derive from
gravity and pressure and neither reads the star. A player short of deuterium has a reason to prefer
a system; a player short of metal does not. Making all three axes vary per system is a real design
change and it is Davide's — it is not needed for the verb to work.

### Watch next round

- **Nothing here has been played.** These are sim measurements against a stated strategy, not a
  session. The first round that can say how it *feels* is the one after the screen lands.
- **The strategy is a claim, not a fact.** "One probe per check-in, aimed at the gap ahead" is how
  the mechanic is meant to be played. A player who dispatches greedily will pay more levels than
  the table above says, and one who never dispatches pays none and gets none.
- **The payload is thin for the first two days, by construction.** ~4.75 worlds per system, of which
  0.35% clear the worth-it bar galaxy-wide: roughly 14 dispatches to see one `Barren` worth
  remarking on and ~60 to see one `Settleable`. The shortlist is the consumer, and it is gated at
  Robotics 4, which round 8 measured as unreached at 48 hours. **In the exact window Davide
  complained about, this buys a second decision and a notification that is not about a mine — it
  does not buy a payoff.** That is the trade, stated rather than hidden.
- **`notificationsFor` has no cap and iOS keeps only the 64 soonest-firing requests.** The in-flight
  ceiling was 8 before this round and probes make it unbounded. Nothing is broken today at one
  dispatch per check-in; a player who dispatches thirty would start evicting the *latest* pending
  requests, which is where long build and research completions live. Engineering item, owned by the
  slice that puts the dispatch on screen.
- **Round 8's held change is still held.** Cost-proportional build durations fix the colony's own
  idleness at zero cost in the first two days. It should ride along with the screen rather than go
  in alone.
- **Round 7's nomination is still untouched:** deuterium is the fortnight's second-worst blocker,
  180 hours of 336.
