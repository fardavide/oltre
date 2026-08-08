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

## Current curves (0.0.12)

Level-1 output: **90 metal / 30 crystal / 15 deuterium per hour**. Output compounds **+25% per
level**, cost compounds **+50% per level**, both floored to whole units at every step.

| Level | metal/h | crystal/h | deut/h | metal mine cost (m/c) | payback of the next level |
|---|---|---|---|---|---|
| 1 | 90 | 30 | 15 | 60 / 15 | 4h |
| 2 | 112 | 37 | 18 | 90 / 22 | 4h |
| 3 | 140 | 46 | 22 | 135 / 33 | 5h |
| 5 | 218 | 71 | 33 | 303 / 73 | 8h |
| 8 | 425 | 137 | 63 | 1,021 / 244 | 14h |
| 10 | 663 | 213 | 97 | 2,296 / 549 | 20h |
| 12 | 1,035 | 332 | 151 | 5,166 / 1,234 | 30h |
| 15 | 2,020 | 647 | 293 | 17,434 / 4,164 | 51h |
| 18 | 3,945 | 1,262 | 571 | 58,839 / 14,053 | 89h |
| 20 | 6,163 | 1,971 | 891 | 132,387 / 31,618 | 128h |

Daily metal: 2,160 at level 1, 5,232 at level 5, 15,912 at level 10, 48,480 at level 15.

Other levers as of 0.0.12: starting stock 500 metal / 300 crystal (no deuterium); build duration
is base-minutes × level, divided by 1 + robotics level; storage cap a flat 10M per resource;
energy scales all mine output by produced/consumed on a deficit — and that scaling is now on
screen rather than silent (round 3).

> **Regenerated from `./gradlew :sim:run` (2026-08-06), and it needed no correction.** Rounds 2
> and 3 both wrote this table by hand because `dl.google.com` was blocked in those sessions and
> Gradle could not resolve AGP. The first session that could build re-ran the sim and every
> figure above — all ten rows, both cost columns, the paybacks and the daily totals — came back
> identical. The hand arithmetic held; the table is now machine-generated and can be trusted.

A greedy week from a cold start, from the same run — upgrade anything affordable once an hour,
cheapest first, mines *and* plant:

```
after 7 days: metal=49,544 crystal=1,410 deuterium=2,520
buildings: metal 15 · crystal 14 · deuterium 1 · solar 14 · robotics 0 · nanite 0
energy: 700/310 (mines at 100%) — hours throttled by power: 0 of 168
events: 80 (starts + completions)
```

**Zero throttled hours is the finding, and it is about the strategy, not the curve.** A player who
treats the Solar Plant as just another cheap upgrade never meets the shortage at all — solar
reached 14 alongside metal 15. Davide's colony hit 55% precisely because he was buying mines and
not plant, which is the choice the mechanic exists to make visible. The sim as written cannot
reproduce his session; a variant that never builds solar would be the one that measures the pain.

Also worth noting from the same run: crystal ends at 1,410 against metal's 49,544, so after round
3's raise metal is now the resource that piles up unspent. That is the mirror image of the
complaint round 3 fixed, and it is the first evidence bearing on whether 90/h overshot.

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
