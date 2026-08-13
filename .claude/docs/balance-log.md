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

## Current curves (0.2.4)

Level-1 output: **90 metal / 36 crystal / 15 deuterium per hour**. Output compounds **+25% per
level**, cost compounds **+50% per level**, both floored to whole units at every step — and from
round 14 **every cost table in the game** carries the opening discount: exactly a third of full
price at level 1, climbing in equal steps to full price at level 9 for buildings and level 4 for
applied research. The cost column below is what the player pays.

| Level | metal/h | crystal/h | deut/h | metal mine cost (m/c) | payback of the next level |
|---|---|---|---|---|---|
| 1 | 90 | 36 | 15 | 20 / 5 | 1h |
| 2 | 112 | 45 | 18 | 37 / 9 | 2h |
| 3 | 140 | 56 | 22 | 67 / 16 | 3h |
| 5 | 218 | 87 | 33 | 202 / 48 | 6h |
| 8 | 425 | 168 | 63 | 935 / 223 | 14h |
| 10 | 663 | 262 | 97 | 2,296 / 549 | 20h |
| 12 | 1,035 | 408 | 151 | 5,166 / 1,234 | 30h |
| 15 | 2,020 | 796 | 293 | 17,434 / 4,164 | 51h |
| 18 | 3,945 | 1,553 | 571 | 58,839 / 14,053 | 89h |
| 20 | 6,163 | 2,426 | 891 | 132,387 / 31,618 | 128h |

Daily metal: 2,160 at level 1, 5,232 at level 5, 15,912 at level 10, 48,480 at level 15.

Other levers as of 0.2.4: starting stock 500 metal / 300 crystal (no deuterium); **build duration
is 4 × √(metal + crystal) minutes, divided by 1 + robotics level, with a five-minute floor applied
last** (round 11 — base-minutes × level until round 10, then cost ÷ 3 for one release); storage cap
a flat 10M per resource; energy scales all mine output by produced/consumed on a deficit — and that
scaling is now on screen rather than silent (round 3).

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

## Round 10 — 0.2.0, the duration curve finally lands (2026-08-09)

Round 8 measured cost-proportional build durations, found them good, and **held** them on one
condition: *"it should ride along with whatever fills the gaps, not go in alone, because on its own
it trades taps for cover."* Round 9 built the thing that fills the gaps. This round lets the curve
in behind it, and the two are measured together for the first time.

### The change, in one line

`upgradeDuration` was per-building minutes × level. It is now **(metal + crystal) ÷ 3 minutes**,
still divided by (1 + Robotics), with a **five-minute floor applied after the divisor**.

Three things about that shape, each load-bearing:

- **Linear-in-level against cost compounding at +50% is why the old curve was wrong**, and it was
  wrong from level one rather than at depth — which is what rounds 2 through 4 kept predicting and
  round 8 finally caught. A Metal Mine 2 cost 112 metal-and-crystal and took 20 minutes; it now
  takes 37.
- **Deuterium is outside the sum**, as in OGame. It gates the Robotics Factory and therefore the
  whole research branch, and pricing time in it too would make one scarcity govern two trade-offs
  the player has to make separately.
- **The floor is applied last, to what the player actually waits.** At Robotics 10 a first mine
  level divides to under three minutes, which is a tap with a delay on it rather than a build. A
  floor placed ahead of the divisor would let the divisor cut through it.

### Round 8's numbers reproduce exactly

Measured through `:sim:run`, no-probe opening, against round 8's held row:

| Reading | 0.1.1 | round 8 predicted | measured now |
|---|---|---|---|
| Hours the colony had nothing in flight | 42 of 48 (87.5%) | 64.6% | **31 of 48 (64.58%)** |
| Work the busiest check-in booked | 72 min | 224 min | **224 min** |
| Building levels at 48h | 25 | 25 (identical) | **25** |
| Longest unbroken silence | 8h 33m | — | 7h 08m |
| Median work a check-in booked | 48 min | — | 125 min |
| Greedy week, day 7 mines | 15 / 15 / 14 | 12 / 12 / 11 | **12 / 12 / 11** |

Every figure round 8 published lands on the nose, including the one it only predicted at depth. The
five-minute floor is new since round 8 and changes none of them, because it does not bind anywhere
in the first 48 hours — the shortest build in the opening is 37 minutes at Robotics 0 and 12 at
Robotics 2. It binds from roughly Robotics 8 upward, which is a fortnight away.

### What the pair does together, which is the reading round 8 could not take

| Reading | 0.1.1 | 0.1.2, probe only | **0.2.0, probe + curve** |
|---|---|---|---|
| Check-ins offering one kind of decision only | 6 of 8 | 0 of 8 | **0 of 8** |
| A second kind of decision first exists | hour 29 | hour 0 | **hour 0** |
| Hours with nothing at all in flight | 42 of 48 (87.5%) | 1 of 48 (2.1%) | **1 of 48 (2.1%)** |
| **Hours the colony had nothing in flight** | 42 of 48 (87.5%) | 41 of 48 (85.4%) | **33 of 48 (68.75%)** |
| Longest unbroken silence | 8h 33m | 0h 47m | **0h 47m** |
| Building levels at 48h | 25 | 23 | **23** |

**The row that moves is the one round 9 was careful not to claim.** Round 9 printed the colony's own
idleness beside the probe's cover precisely so the new verb could not take credit for a complaint it
does not touch — 85.4% against 87.5%, essentially unchanged. The curve is what touches it: 68.75%.
The probe covers the player's attention, the curve covers the colony, and it took both to move both.

The probe's own price is unchanged at 150 metal and still costs two levels of twenty-five.

### One interaction worth writing down

The combined run reaches **Robotics 2 and Photovoltaics 0**, where the probe-only run reached
Robotics 1 and Photovoltaics 1. Same 23 building levels, differently shaped. Longer builds mean
fewer completions per check-in, so the greedy cheapest-first rule has more stock free at each visit
and spends it on the Robotics Factory rather than on the research it opens.

That is the strategy reacting, not a regression — and it is a reminder that the opening report
measures *one stated strategy*, not a player. Nothing here has been played.

### Watch next round

- **Deuterium is now the fortnight's worst blocker by a clear margin: 204 hours of 336**, up from
  180. Crystal fell from 190 to 143 and metal rose from 116 to 137. Round 7 nominated deuterium and
  this round makes the nomination harder to keep ignoring — but it also did not touch it, so the
  move is the curve redistributing waiting rather than anything aimed at the resource.
- **The greedy week is no longer resource-bound.** It closes with all three of its next purchases
  affordable and 47,517 metal in the bank, where 0.1.1 closed blocked on metal for 167 of 168 hours.
  Duration is the binding constraint at that depth now. That is what the change is *for*, and it is
  also the first time a run has ended with nothing to decide because everything is already running.
- **The floor is live in no run this harness measures, and "around Robotics 8" was the wrong way to
  say it.** Binding is a joint condition on cost *and* Robotics, not a Robotics threshold:
  `floor((metal + crystal) ÷ 3) ÷ (1 + Robotics) < 5` admits only purchases under ~150
  metal-and-crystal at Robotics 9 — which is exactly Metal Mine 1→2 (112) and Crystal Mine 1→2
  (108), and both are bought in the genesis check-in at Robotics 0. The fortnight ends on mines at
  17 and 16, whose next levels cost tens of thousands. So the floor guards a case none of the three
  runs reaches, and the honest reading is that it is **untested by measurement** rather than
  exercised once. It would start to bind on a colony that had climbed Robotics deep *and* still had
  level-1 rows to buy — a second colony, which is slice #10.
- **Still nothing here has been played.** Rounds 9 and 10 are both sim measurements against a stated
  strategy. The first round that can say how any of it *feels* is the one after the screen ships.

## Round 11 — 0.2.2, the wait that outgrew the earning (2026-08-09)

Round 10's "still nothing here has been played" lasted one day. Davide played 0.2.0 and the first
thing he said was about the curve it landed.

### The feedback, verbatim

> "Ok, I think I can tell that prices and build times, before have access to explorations, are way
> too long! I open the game, most of the thing are red (not enough resources), and after I tap the
> one or two thing I can upgrade I have to wait 2/3 hours!"

> "It's fine to have high cost and long build time when you have more things to do, but before then
> it's so frustrating"

And, asked to make the harness count what a check-in offers:

> "I'm not sure it's a good idea, but I was thinking we could count the possible interactions in the
> benchmarks, to make sure users have 'things to do'?"

**The second sentence is the specification.** He is not asking for a cheaper game — he asked twice
in round 8 for the opposite (*"I dont want to be able to update things no stop"*, *"ci vuole
parecchio per sbloccare anche quelle che ci sono. Il che è buono"*). He is asking for the weight to
arrive **after** there is something else to do, not before.

### What the sim was taught to measure

Three additions, and the first two exist because every reading in this harness before today counts
what a *strategy wanted to buy* — which is not what a player describes.

1. **The screen, as a colour.** `printCheckInPressureReport` reads the six Colony rows the way a
   person reads them at arm's length, **before** the check-in spends anything: how many are
   building, how many are tappable, how many are red, how many are locked. Locked is counted apart
   from red on purpose — the Nanite Factory below Robotics 10 draws as `Locked("Requires Robotics
   10")`, dimmed with its requirement, and folding it into the red count would manufacture a
   permanently-red row out of a row that is honestly saying "not yet".
2. **A three-hour cadence.** Every opening report until now ran at the brief's four a day. Davide
   has now twice described playing at two or three hours — *"apri il gioco ogni 2/3 ore"* in round
   8 and *"wait 2/3 hours"* here — and a colony visited twice as often has banked half as much
   each time. Also run against **a player who never buys the Robotics Factory**, which is not a
   straw man: it is the only facility that raises no rate, it is priced in the slowest resource,
   and nothing on the row says it is the building that halves every wait in the game.
3. **The interaction census**, which is Davide's idea and gets its own section below.

### The finding: the wait outgrew the earning, and it was always going to

Cost compounds at +50% a level. Production compounds at +25%. Round 10 read the duration straight
off the cost, so **the wait after a tap pulled away from the income that pays for it by ×1.2 a
level, from level one, without bound.** The two clocks, Metal Mine, Robotics 0:

| Level | cost (m+c) | 0.2.0 build | hours of income to afford it |
|---|---|---|---|
| 4 | 251 | 1h 23m | 1h 26m |
| 5 | 376 | 2h 05m | 1h 43m |
| 6 | 563 | **3h 07m** | 1h 50m |
| 7 | 844 | **4h 41m** | 2h 30m |
| 8 | 1,265 | **7h 01m** | 3h 00m |
| 20 | 164,005 | **911h** | 23h 51m |

**Level 4 is where they cross**, and after it the build is the binding wait for the rest of the
game. Davide's "2/3 hours" is levels 5 to 7, which is where a colony is on day two — measured, not
inferred. The Deuterium Synthesizer was worse still at 12h 36m for level 6, because its base cost
is four times the Metal Mine's and round 10's rule made that four times the clock.

The Robotics divisor was the only thing pushing back, and it decided the whole experience:

| Three-hour cadence, 0.2.0 | buys Robotics | never buys Robotics |
|---|---|---|
| Median wait a tap booked | 1h 33m | **2h 29m** |
| Longest wait a tap booked | 2h 53m | **6h 48m** |
| Taps booking over two hours | 8 of 23 | 13 of 22 |
| Building levels at 48h | 26 | **24** |

A balance that swings that far on whether the player has worked out which building is secretly the
clock is not a balance; it is a quiz.

### What changed: the duration is cut from the **root** of the cost

`upgradeDuration` was `(metal + crystal) ÷ 3` minutes. It is now **4 × √(metal + crystal)** minutes,
still divided by (1 + Robotics), still with the five-minute floor applied last, and deuterium still
outside the sum for round 10's reason.

**The arithmetic is why, rather than a coincidence.** Cost-over-income grows at 1.5 / 1.25 = ×1.2 a
level; the square root of a ×1.5 curve grows at ×1.2247. So a duration cut from the root tracks the
time it takes to earn the thing *at every depth*, with no help from any building — 0.75 of it at
level 3, 1.13 at level 20. `BalanceCurveTest` now bounds that ratio **on both sides**, which is a
check round 10's shape could not have passed at any constant.

Round 10's sentence was "a build takes as long as it costs". The correction is one word:
**a build takes about as long as *earning* it does.**

### The price, swept rather than chosen

Six candidates through the real harness. `÷5` and `÷6` keep round 10's shape and only shift it.

| Curve | 3h median | 3h longest | >2h | never-Robotics longest | its levels at 48h | colony idle |
|---|---|---|---|---|---|---|
| **÷3 — 0.2.0** | 1h 33m | 2h 53m | 8 of 23 | **6h 48m** | **24** | 68.75% |
| ÷5 | 1h 07m | 1h 44m | 0 of 24 | 4h 13m | **24** | 79.16% |
| ÷6 | 0h 56m | 1h 26m | 0 of 24 | 3h 30m | **24** | 83.33% |
| root ×3 | 0h 37m | 1h 06m | 0 of 24 | 1h 45m | 26 | 85.41% |
| **root ×4 — chosen** | **0h 50m** | **1h 28m** | **0 of 24** | **2h 20m** | **26** | **81.25%** |
| root ×5 | 1h 02m | 1h 50m | 0 of 24 | 2h 55m | 26 | 77.08% |

**Two things this table settles.** First, the constant was not the problem: doubling it to ÷6 still
leaves the uninformed player waiting 3h 30m and still costs them two levels, because the divergence
is exponential and a constant only moves where it bites. Second, the root **dominates** the
constant on both axes at once — root ×5 has *less* colony idleness than ÷5 (77.08% against 79.16%)
*and* a far better worst case. That is not a trade-off being taken; it is a better shape.

**Four rather than three or five.** Every value in 3–5 answers the complaint, so the constant buys
one thing: how much of round 10's cover survives. At 3 the colony idles 85.4% of its opening, which
is where it was *before* round 10 — the change undone. At 5 the deepest tap on day two is back to
2h 55m for a player at Robotics 0, which is the complaint. At 4 no repeating facility passes two
hours before level 8 and 81.25% of the opening still has the colony busy.

### What it moves

| Reading | 0.2.0 | 0.2.2 |
|---|---|---|
| Median wait a tap booked (3h cadence) | 1h 33m | **0h 50m** |
| Longest wait a tap booked | 2h 53m | **1h 28m** |
| Taps booking over two hours | 8 of 23 | **0 of 24** |
| Same, for a player who never buys Robotics | 13 of 22 | **4 of 23** |
| Their longest wait | 6h 48m | **2h 20m** |
| **Their building levels at 48h** | **24** | **26 — the same as the informed player's** |
| Metal Mine 6 / 7 / 8 at Robotics 0 | 3h07 / 4h41 / 7h01 | **1h32 / 1h56 / 2h20** |
| Deuterium Synthesizer 6 | 12h 36m | **3h 08m** |
| Levels at 48h, four-a-day, no probe | 25 | **25** |
| Hours with nothing **at all** in flight (with probe) | 2.08% | **2.08%** |
| Longest unbroken silence | 0h 47m | **0h 47m** |
| Work the busiest check-in booked (with probe) | 540 min | **540 min** |

**The honest cost, stated rather than buried: the colony's own idleness goes back up**, 68.75% →
81.25% at the four-a-day cadence, against 85.4% before round 10 ever ran. So round 11 hands back
roughly three quarters of what round 10 bought on that one row. It is defensible only because of
the row underneath it — *nothing at all in flight* is unchanged at 2.08% and the longest silence is
unchanged at 47 minutes, because **the probe is what covers the player's attention and always was**
(round 9 was careful not to let it claim otherwise; this is the same distinction paying off in the
other direction). What round 10 was buying on the colony row was cover the notification loop no
longer needs, at a price Davide could feel on every tap.

Deeper, the change gives back most of what round 10 took: the greedy week goes 12/12/1/11 →
**14/15/1/14** (0.1.1 was 15/15/1/14) and the fortnight 17/16/13/16/9 → **18/17/14/17/10**. The week
is also resource-bound again — 167 of 168 hours blocked on metal alone, against 81 — which reverses
round 10's note that it had become the first run to end "with nothing to decide because everything
is already running". In the fortnight, crystal as a sole blocker falls hard (143 → 58 hours) while
deuterium barely moves (204 → 192) and metal rises (137 → 180).

### The interaction census — Davide's idea, and the trap it had to avoid

He proposed counting the possible interactions. **The reason that is not trivially a good idea is
already in this file:** round 8's harness printed *"median options on the table: 5"* for the exact
opening he called boring, because five facility rows counted as five options when they were one verb
pressed five times. A raw count is not a safety net — it is a number that goes up when you add rows.

So `printInteractionCensus` enumerates every call `core` would accept at each check-in and reports
**kinds first, count second**, with a probe counted as *one* verb rather than as the ~1,000 systems
it could be aimed at. And for every call the game would refuse it records **why**, which is the part
that turned out to be worth building:

| Reading | over 2 days | over 7 days |
|---|---|---|
| Median actions offered | 6 | 5 |
| Median *kinds* offered | 2 | 3 |
| Check-ins offering one kind only | 0 of 12 | 2 of 42 |
| Check-ins offering nothing at all | 0 of 12 | 0 of 42 |
| **Median actions the stock actually stretched to** | **2** | **2** |
| Refused for the price | **5.12%** | 22.16% |
| Refused by a busy slot | **0.00%** | 5.12% |
| Refused by an unmet requirement | **47.43%** | 30.03% |

**The opening is gated, the week is priced.** Nearly half of every action the game has is refused in
the first two days by a requirement rather than by a cost: all three adaptation ladders behind
Robotics 4 for the whole window, all three applied technologies behind Robotics 1 for the first 27
hours, Enrichment behind Extraction 3 after that, the Nanite Factory behind Robotics 10 forever.
Price refuses 5%. Round 8 said this in words — *"the opening is thin because four of the game's
eight v1 features are unbuilt, and the two verbs that exist are gated behind a pace Davide wants
kept"* — and this is the first time it has a number.

Two smaller things fell out of it:

- **The shared research slot blocks nothing in the opening** (0.00%, rising to 5.12% over a week).
  `GameState` calls the single slot "research's only scarcity"; for the first two days it is not
  scarce, because projects are shorter than the gap between check-ins and the colony cannot afford
  to keep it busy anyway.
- **Two actions a check-in, all week.** "Offered" falls from 6 to 5 as costs outgrow the stock and
  "kinds" rises from 2 to 3 as gates open, but what the stock *stretches to* is 2 at both. That is
  Davide's "the one or two thing I can upgrade", and it is stable rather than decaying — which is
  the argument for **not** touching prices this round.

### Prices were not moved, and this is the evidence

His sentence names prices as well as durations, so it was measured rather than assumed. At the
three-hour cadence, before the check-in spends anything: **median 1 red row of 6**, worst case 2,
median 4 tappable, and **0 of 12 check-ins offering one row or none**. The screen is not mostly red
by count. What is true is that the stock stretches to about two of those rows — and that is the
scarcity Davide asked for by name on 2026-08-08: *"There's still a need to decide, as you will use
resources to chose which to upgrade."* Cutting prices would delete the decision to fix a perception
whose bigger half was rows sitting busy for three to seven hours, which is what this round removed.

### Watch next round, and what to move first

- **The gate share is the number to act on, and it is not a curve.** 47% of the opening's actions
  are refused by a requirement. The cheapest candidates, in order of how little they disturb: the
  adaptation ladders' Robotics 4 gate (round 6 already nominated dropping it to 2 or 3 as "cheaper
  than re-pricing anything"), and the Nanite Factory row, which is a permanently locked row on the
  main screen for weeks. Both are Davide's calls — round 8 recorded that he *likes* the unlock pace,
  so this is a note that the pace has a measurable cost, not an argument that it is wrong.
- **The colony's own idleness is back to 81.25%** and is now the row round 10 will be judged on.
  If it turns out to matter, **the lever is `MINUTES_PER_ROOT_COST`, and 5 is the one notch up** —
  it costs 2h 55m as the uninformed player's worst tap and buys back 4 points of idleness.
- ~~**"Before have access to explorations" may be a UI finding.**~~ Investigated during this round
  and **closed by Davide the same day: not a balance item, and no change wanted.** Recorded only so
  a later session does not re-derive it — dispatch is ungated in `core` and affordable from the
  starting 500 metal, but the Galaxy tab opens on the home system, surveyed at genesis, whose
  footer is the sentence "Surveyed at genesis" with no offer attached. Nothing in this file can or
  should move for it.
- ~~**Deuterium is still the fortnight's worst blocker** at 192 hours of 336 … crystal has fallen
  right back (143 → 58), so deuterium is now clear of the field.~~ **Corrected by round 12 the same
  day: do not quote these figures.** The sole-blocker ledger is unstable at this resolution — a
  one-unit change to deuterium income, touching crystal's curve not at all, moves crystal's count
  between 41 and 221. The 58 above is an outlier at the shipped constant, not a fall. Deuterium
  being the worst blocker survives the correction; "clear of the field" does not.
- **The floor is still untested by measurement** (round 10's note stands): at 4 × root it binds
  below ~150 metal-and-crystal at Robotics 9, which no run here reaches.

## Round 12 — the round that moved nothing, and why that is the finding (2026-08-09)

**No balance number moved.** Davide asked to continue balancing after round 11 shipped, and the
census he commissioned pointed at gates rather than curves. Three levers were swept against that,
and the sweep answered a different question than the one it was asked: **the reading rounds 7
through 11 were all tuned against is not stable at the resolution they read it at.**

Everything below is `:sim:run`. Nothing here is arithmetic.

### The chain the census pointed at

Round 11's census: 47.4% of the opening's actions refused by an unmet requirement against 5.1% by
price. `printGateClock` follows that to its cause, at the three-hour cadence over seven days.

| Robotics Factory level | Reached | Opens |
|---|---|---|
| 1 | **hour 27 (day 2)** | Photovoltaics, Extraction — the Research tab |
| 2 | hour 48 (day 3) | — |
| 3 | hour 75 (day 4) | — |
| 4 | **hour 99 (day 5)** | all three adaptation ladders — every `Blocked` world |
| 10 | **never in seven days** | the Nanite Factory |

**Every gate below Nanite is a Robotics Factory level, and the Robotics Factory is the only
repeating row priced in deuterium.** It was unaffordable at **35 of 42 check-ins**, and deuterium
was the shortage at **all 35** — metal at 2, crystal at 0. So the second and third verbs of a
five-verb game sit behind one resource.

That is a stable measurement: it moves smoothly and in the right direction under every lever
(Robotics at 150 deuterium → Robotics 4 at hour 81; income at 18/h → hour 84).

### What the sweep found instead

| Deuterium/h | sole-blocker m/c/d | short *at all* m/c/d |
|---|---|---|
| 12 | 180 / **41** / 222 | 334 / 252 / **336** |
| 13 | 139 / **149** / 207 | 241 / 250 / **335** |
| 14 | 118 / **183** / 184 | 188 / 252 / **334** |
| **15 — shipped** | 180 / **58** / 192 | 319 / 245 / **334** |
| 16 | 104 / **200** / 162 | 181 / 272 / **333** |
| 18 | 109 / 175 / 139 | 202 / 263 / **333** |
| 21 | 92 / 213 / 96 | 160 / 276 / **328** |

**Read the crystal column of the left-hand table.** Crystal's curve is not touched by any of these
runs, and its sole-blocker count goes 41, 149, 183, **58**, 200 — non-monotone, swinging by 142
hours of 336 on single-unit changes to an unrelated constant. That is not a curve responding; it is
a different trajectory. The cause is structural rather than a bug: *"short of this resource **and
nothing else**"* is a knife-edge on which purchase happens to be next, and a small income change
reorders the queue.

`Ledger` now records **`shortHours`** beside `soleBlockerHours` — the same question without the word
*alone*. It cannot say who to blame, which is what the sole ledger is for, but it does not flip on a
single unit. **Tune against the second; read the first afterwards.**

### Two things that survive the correction, and one that does not

- **Does not survive:** round 11's *"crystal has fallen right back (143 → 58), so deuterium is now
  clear of the field."* 58 is the outlier at the shipped constant; its neighbours give 149 to 200.
  That bullet is struck through in round 11 above. Round 7's crystal finding is unaffected — 130 of
  168 hours is far outside this noise band, which is why it was safe to act on and this was not.
- **Survives, and is stronger than before:** deuterium is short for *something* in **328 to 336 of
  336 hours at every income from 12 to 21/h**. Raising the rate by 75% buys eight hours. So
  **deuterium income is not the lever for deuterium being a blocker** — demand outruns any rate the
  mine can reach, because the Robotics Factory compounds at ×1.5 against a synthesizer at ×1.25.
  That is round 11's duration divergence again, on the resource axis, and there is no root to take.
- **Survives:** the gate clock. Day 5 for the adaptation ladders is the one number here worth a
  decision.

### Why nothing was moved

- **No lever touches the window that was complained about.** The two-day gate share is **47.43% in
  every single variant** — including both lowered ladder gates. Its floor is set by the ladders and
  Nanite being gated at all, plus research for the first 27 hours; nothing short of ungating a
  branch reaches it. **Median kinds offered in the opening is 2 under every candidate.** Round 8
  concluded "no number in this file adds a second thing to do"; this is the same conclusion arrived
  at by exhaustion rather than by argument, and round 9's answer — a verb gated by nothing — is
  still the only one that has ever worked.
- **Every deuterium lever overshoots into crystal.** Opening the ladders earlier raises crystal's
  robust count monotonically (245 → 264 at Robotics 3 → 291 at Robotics 2) because the ladders are
  the crystal-heaviest thing in the game. Round 7 set crystal income against the *repeating* basket;
  these levers change what the basket is.
- **Round 8 recorded that Davide likes the unlock pace**, in his own words. Nothing measured here
  contradicts him, so the calls below are his rather than the build's.

### On the table for Davide, with numbers rather than a recommendation

**The adaptation ladders' Robotics 4 gate.** Round 6 chose it "so the branch opens after the player
has met the Galaxy screen and read a `BLOCKED` row", and pre-authorised this exact review: *"If the
gate turns out to sit far past the first BLOCKED screen, lowering it to 2 or 3 is cheaper than
re-pricing anything."* It has: **hour 99, day 5.**

| Gate | Ladders open | Gate share over 7 days | Crystal short *at all* |
|---|---|---|---|
| **Robotics 4 — shipped** | day 5 | 30.03% | 245 of 336 |
| Robotics 3 | day 4 | 26.73% | 264 |
| Robotics 2 | day 3 | 22.89% | 291 |

None of the three changes the first two days. It is a mid-game call, not an opening one, and it is
in `AdaptationBalance` — a *decided* sheet, not a placeholder — so it is not the build's to move.

### Watch next round

- **The gate clock is the harness's stable instrument now; the sole-blocker ledger is not.** Any
  future round quoting sole-blocker hours should quote `shortHours` beside them, and should not read
  a difference of under ~50 hours as a signal at all.
- **Nanite is unreachable in a week** and its row is locked on the Colony screen throughout. Whether
  a facility nobody can reach for a fortnight should occupy a permanent row is a design question,
  not a balance one.
- **Round 11's held item stands:** the colony's own idleness is 81.25% and `MINUTES_PER_ROOT_COST`
  is the one notch either way.

## Round 13 — 0.2.3, the opening goes on a discount that runs out (2026-08-09)

> **Superseded within the hour by round 14, which shipped in the same unreleased 0.2.3.** The
> *shape* below stands and is the design; two things about it were wrong and are corrected there —
> it reached the buildings only, and its recovery was geometric with a convergence level chosen
> before Davide had named the landmark. Kept in full, because what it measured is still the
> measurement that justified the shape.

### The feedback, verbatim

> "I want the user to be able to gather resources and build quickly the first 2/3/4 days. And I'm
> talking about up to 300% quickly. Actually I don't want more resources, but cheaper upgrades at
> the start"

**Both halves rule something out.** *Not more resources* kills the income lever — round 3 raised
metal, round 7 raised crystal, and a third raise would inflate every payback in the game and break
the ratio `BalanceCurveTest` pins against the repeating basket. *Cheaper at the **start*** kills the
base-cost lever too: dividing `baseCost` discounts level 30 exactly as much as level 1 and hands
back the whole late game with it.

What is left is the shape nobody had tried in thirteen rounds: **a discount on the early levels that
decays to nothing.**

### The change

`upgradeCost` multiplies every resource by **(9/10) ^ (FULL_PRICE_LEVEL − level)** below level 11,
and by nothing at or above it. Level 1 is 0.35 of full price; level 10 is 0.9; level 11 and every
level after it is the same integer it was before this round existed.

Carried by `exactGeometric`, not `compound`, and that is not a preference: flooring a tenth off a
small number ten times over is catastrophic where flooring a half off a large one is not. The Metal
Mine's 15 crystal comes out at **5** carried exactly and **2** floored per step.

| Metal Mine | full price | now | |
|---|---|---|---|
| 1 | 60 / 15 | **21 / 5** | 2.87× cheaper |
| 3 | 135 / 33 | 58 / 14 | 2.33× |
| 5 | 303 / 73 | 161 / 39 | 1.88× |
| 8 | 1,021 / 244 | 744 / 178 | 1.37× |
| 11 and deeper | unchanged | unchanged | 1.00× |

**Two things came free, and both were the point.** Round 11 made duration a function of cost, so a
third of the price is 0.58 of the clock with no second constant touched. And every gate in the game
is a Robotics Factory level, so discounting the Robotics Factory moves round 12's gate clock without
moving a single gate — which round 12 had just measured as unreachable by all three levers aimed
straight at it.

### What it does, measured

| Reading | 0.2.2 | 0.2.3 |
|---|---|---|
| **Building levels at day 1** | 13 | **18** |
| day 2 | 22 | **30** |
| day 3 | 29 | **39** |
| day 4 | 36 | **44** |
| day 7 | 50 | **56** |
| **Research opens** | hour 27 (day 2) | **hour 12 (day 1)** |
| **Adaptation ladders open** | hour 99 (day 5) | **hour 51 (day 3)** |
| Median *kinds* offered in the opening | 2 | **3** |
| Median actions the stock stretched to | 2 | **3** |
| Opening actions refused by an unmet requirement | 47.4% | **43.6%** |
| Median wait a tap booked (3h cadence) | 0h 50m | **0h 30m** |
| Longest wait a tap booked | 1h 28m | **1h 08m** |
| Levels at 48h, 3h cadence | 26 (robotics 2) | **34 (robotics 4)** |

The colony that reaches day 4 now would have taken **until day 6** before. The Research tab opens
inside the first day rather than the second, and the ladders — with them every `Blocked` row on the
Galaxy screen — arrive on day 3 rather than day 5.

### "Up to 300%" — the honest reading, in both units

**As a discount it lands exactly: 2.87× at level 1**, under the stated ceiling. As a *pace* it does
not, and no setting of this lever reaches it:

| Ramp | level-1 discount | day 1 | day 2 | day 3 | day 4 | pace to day 4 |
|---|---|---|---|---|---|---|
| none — 0.2.2 | — | 13 | 22 | 29 | 36 | 1.0× |
| 9/10 to level 8 | 2.09× | 16 | 26 | 34 | 39 | ~1.2× |
| **9/10 to level 11 — shipped** | **2.87×** | **18** | **30** | **39** | **44** | **~1.5×** |
| 8/9 to level 11 | 3.25× | 19 | 33 | 40 | 44 | ~1.6× |
| 9/10 to level 14 | 3.93× | 21 | 35 | 43 | 49 | ~1.9× |
| 9/10 to level 16 | 4.86× | 23 | 38 | 46 | 53 | ~2.1× |

**Discounting alone tops out near 2× pace by day 4, and the ceiling is arithmetic rather than a
choice of constant.** Free upgrades would still leave the colony waiting on income and on one job
per facility, and income is the thing Davide ruled out in the same sentence. Doubling the discount
from 2.87× to 4.86× buys 9 levels on day 4; doubling it again would buy fewer. **The dial is
`FULL_PRICE_LEVEL` and the rows above are what each notch costs** — say the word and it moves.

### The bound that is load-bearing, and the bug it hid

`FULL_PRICE_LEVEL` cannot go past **16**. The discount is carried exactly, so the numerator is
`fullPrice × 9^(level−1)`; at 18 the Nanite Factory's deuterium overflows Long and prices at
**−70**, which `covers()` reads as *free*. `Resources.of` caught it — at the point of use, in a
running game, for the one row deep enough to overflow.

`BalanceCurveTest` now walks every building across all forty levels and asserts every cost is
positive and strictly rising, so the next session that reaches for that constant is told by CI
rather than by a crash.

### What it cost, stated rather than buried

- **Crystal is the fortnight's blocker again, and worse than before round 7:** 214 sole-blocker
  hours of 336 and **305 of 336 short-at-all** (against 245 in 0.2.2), closing on 245,573 metal
  against **521 crystal**, with all five next purchases short of crystal and nothing else. The
  cause is not the ramp's shape — realised spend is 2.4 : 1 against income at 2.5 : 1, which is
  round 7's target met — it is that the colony now gets far deeper into the two crystal-heaviest
  branches in the game (15 projects finished by day 7 against 12). **The ramp did not unbalance the
  economy; it accelerated arrival at a part of it that was already unbalanced.**
  This is a day-10-and-later problem, it was not touched, and round 7's lever is income — which is
  the thing this round was told not to move. Round 14's obvious subject.
- **Three tests changed shape rather than value**, and one of them was overdue: `AffordabilityTest`
  read its fixture off `upgradeCost` and had now failed in three consecutive rounds of tuning that
  had nothing to do with affordability. It states its own prices now.
- **The basket ratio is read at full price now.** The discount multiplies all three resources
  equally so it cannot change the ratio by design, but it rounds each independently, and at level 1
  the integers are small enough to drag the measured basket from 2.65 : 1 to 2.78 : 1. The opening's
  own skew is bounded separately at a fifth.

### Watch next round

- **Crystal at depth is now the biggest open item in this file**, ahead of deuterium. Both are
  downstream of the same thing: the applied and adaptation branches cost roughly 1.1 : 1 and
  1.3 : 1 where the mines cost 2.5 : 1, so every hour the player spends in the branches is an hour
  the income ratio is wrong for. That is a structural mismatch and probably not a one-constant fix.
- **Nothing here has been played.** Every figure is `:sim:run` against a stated strategy. Round 11
  was corrected within a day of shipping by one session with a phone.
- **`FULL_PRICE_LEVEL` is the dial and 16 is its hard ceiling.** Round 11's
  `MINUTES_PER_ROOT_COST` is still the duration dial and still at 4.

## Round 14 — 0.2.3, the discount reaches the whole game (2026-08-09)

Round 13 shipped the ramp on the buildings and Davide's correction arrived before it was merged.
Same unreleased version, so this is the shape that actually ships.

### The feedback, verbatim

> "Wait, did you just make Metal cheaper???"

> "Everything must be cheaper and quicker across the board, until first expedition. Lets say
> starting about 3x and the start of the game, and arrive to 1x at the moment you can have the first
> expedition"

**The first line is a question and the answer was no** — round 13 discounted all three resources on
all six buildings, and the Metal Mine was only the example row in the summary. **The second line is
the correction, and it was right:** `ResearchBalance`, `AdaptationBalance` and `SurveyBalance` are
separate objects with separate curves, and round 13 left all three at full price. Discounting a mine
while leaving a technology alone is not a cheaper opening — it is a changed ratio between the two.

Asked what "first expedition" meant, he chose **when the galaxy becomes actionable**: the adaptation
ladders at Robotics Factory 4, the point where a probe's findings can be bought against rather than
only read. (Worth recording, because it was offered as an option and rejected: the *first probe* is
already dispatchable at minute one — 150 metal against a 500-metal start — so that reading would
have left the ramp no room at all.)

### What changed from round 13

| | round 13 | round 14 |
|---|---|---|
| Recovery | geometric, ×10/9 a level | **linear, equal steps** |
| Level-1 discount | 2.87× | **exactly 3×** |
| Buildings reach full price | level 11 | **level 9** |
| Applied research | full price throughout | **discounted to level 4, cost *and* duration** |
| Adaptation ladders | full price | full price — see below |
| Lives in | `PlaceholderBalance` | **`Curves.kt`**, beside `compound` and `exactGeometric` |

**Linear rather than geometric** because geometric needs a fractional root between the two things
anyone wants to say — *how cheap at the start* and *where does it stop* — and `core` has no
fractional anything. Linear needs neither, and it cannot overflow: the multiplier is at most
`3 × (fullPriceLevel − 1)`, where round 13's carried power priced the Nanite Factory at **−70
deuterium** the moment the convergence level reached 18.

**Level 9 for buildings, because that is where the mines stand when the galaxy opens.** Measured,
not chosen: `:sim:run` puts the colony at metal 9 / crystal 9 on day 3 and Robotics 4 at hour 54.
The mines reach full price and the galaxy becomes actionable in the same session, which is
*"arrive to 1x at the moment you can have the first expedition"* turned into a level.

**Level 4 for applied research**, because the branch opens at Robotics 1 and the discount ends at
Robotics 4, and the colony gets through two or three technology levels between them.

**The adaptation ladders are not discounted, and that is the definition rather than an omission.**
The landmark *is* the moment they become buyable, so their level 1 sits exactly on the boundary
where the discount has already run out. The probe is not discounted either, for a duller reason: it
is a flat cost with no ladder to ramp along — every probe is the first probe.

### What ships

| Metal Mine | full price | now |
|---|---|---|
| 1 | 60 / 15 | **20 / 5** — exactly 3× |
| 3 | 135 / 33 | 67 / 16 |
| 5 | 303 / 73 | 202 / 48 |
| 8 | 1,021 / 244 | 935 / 223 |
| 9 and deeper | unchanged | unchanged |

| Applied research | full price | now |
|---|---|---|
| Photovoltaics 1 | 300 / 150 / 100, 60 min | **100 / 50 / 33, 20 min** |
| Extraction 1 | 600 / 400 / 200, 90 min | **200 / 133 / 66, 30 min** |
| Enrichment 1 | 500 / 700 / 200, 150 min | **166 / 233 / 66, 50 min** |
| level 4 and deeper | unchanged | unchanged |

Research needed telling twice — cost *and* duration — because a building got the second half for
nothing when round 11 made its duration a function of its cost, and this branch's duration is a
table times a level.

### Measured

| Reading | 0.2.2 (no ramp) | round 13 (buildings only) | **round 14 (across the board)** |
|---|---|---|---|
| Building levels, day 1 | 13 | 18 | **17** |
| day 2 | 22 | 30 | **28** |
| day 3 | 29 | 39 | **36** |
| day 4 | 36 | 44 | **41** |
| day 7 | 50 | 56 | **55** |
| **Projects finished by day 4** | ~4 | 4 | **9** |
| by day 7 | 12 | 15 | **17** |
| Research opens | hour 27 | hour 12 | **hour 12 (day 1)** |
| Ladders open | hour 99 | hour 51 | **hour 54 (day 3)** |
| Median *kinds* offered, opening | 2 | 3 | **3** |
| Median wait a tap booked | 0h 50m | 0h 30m | **0h 34m** |
| Levels at 48h, 3h cadence | 26 | 34 | **32 (robotics 4)** |

**Fewer building levels than round 13 and more than twice the projects.** That is the correction
doing its job: round 13's ramp was longer and building-only, so it bought levels; this one is
shorter and reaches the branch, so the same opening buys a wider game. Day 4 finishes 9 projects
against 4.

### What it cost

- **Three tests on two *decided* sheets changed.** `ResearchBalanceTest` says in as many words that
  its tables may only move if the sheet moved, *"which is Davide's call, not a refactor"* — he made
  it. The published tables stay in the fixture verbatim as the **full** price and the test applies
  the documented discount to them, so the sheet is still visible as the design.
- **The step into the adaptation branch is now the widest in the game**: Enrichment 1 is 830 priced
  and Thermal 1 is 4,800, a factor of 5.8 where it used to be 1.9. That is the training wheels
  coming off at exactly the landmark, and it is asserted rather than left to be discovered.
- **The `AdvanceResearchTest` fixture had to shrink its window** from an hour to twenty minutes,
  because Extraction 1 now lands in 27m 46s.
- **Crystal at depth is untouched and still the biggest open item** — 308 of 336 hours short-at-all
  in the fortnight, closing on 220,878 metal against 4,798 crystal. Realised spend is 2.5 : 1
  against income at 2.5 : 1, so this is not a ratio error; it is the two branches costing ~1.1 : 1
  and ~1.3 : 1 where the mines cost 2.5 : 1.

### Watch next round

- **Nothing here has been played**, and the last two rounds were both corrected within a day of a
  session with a phone.
- **The dials, in the order they are likely to be wanted:** `FULL_PRICE_LEVEL` in
  `PlaceholderBalance` (9) and in `ResearchBalance` (4), then `OPENING_DISCOUNT_DIVISOR` in
  `Curves.kt` (3 — the "3×"). All three are one-line changes with the sweep in round 13 for shape.
- **Discounting cannot buy much more pace.** Round 13 swept it: even a 4.86× opening discount only
  doubles day-4 progress, because free upgrades still leave the colony waiting on income and on one
  job per facility. If the first days should be faster still, the next lever is income — which is
  the one thing Davide has ruled out twice.

## Round 15 — 0.2.4, the cliff at the branch, and arithmetic that cannot wrap (2026-08-09)

Two things, one of which is not a balance change at all.

> "Adjust the Enrichment and Thermal matter."
> "Also lets find a solution to overflow, the game must be solid against large numbers for super
> lategame"

### The cliff

Round 14 gave the applied branch the opening discount and left the adaptation ladders at full price,
on the argument that the landmark *is* the moment the ladders become buyable, so their level 1 sits
exactly on the boundary. The argument is true and the result was a cliff: **Enrichment 1 at 830
priced against Thermal 1 at 4,800 — a step of 5.8× where the sheet designed 1.9×**, arriving exactly
where the player first meets the galaxy.

The fix is not a new number. The two branches **share one research slot** and are meant to be weighed
against each other, so they have to be on the same side of the discount at every level. Adaptation
now uses `ResearchBalance`'s own `FULL_PRICE_LEVEL` of 4, for cost and duration alike:

| Priced 1 : 2 : 3 | Enrichment | Thermal | ratio |
|---|---|---|---|
| level 1 | 830 | **1,600** | 1.93 |
| level 2 | 2,080 | **4,000** | 1.92 |
| level 3 | 4,375 | **8,400** | 1.92 |
| level 4 — full price | 8,439 | 16,202 | 1.92 |

The sheet's ratio now holds at **every** level rather than at the one depth a single pair of numbers
would have pinned, and `AdaptationBalanceTest` asserts it as a ratio for that reason.

**What it cost.** Exact equality between the three ladders was a property of the undiscounted
level-1 table — 4,800 each. A third of three differently-shaped baskets does not floor to three
equal totals, so it is now equality to within **two units in sixteen hundred**, asserted as a
proportion across the ramp and past it. And the discount runs a little past the landmark, since
these levels are bought from Robotics 4 onward — a soft edge instead of a cliff, which is what was
asked for.

Nothing else moved: Robotics 4 still lands at hour 54, and day 4 goes 41 building levels to 40 with
projects 9 to 10.

### Overflow

**Not a balance round.** No curve moved for it; it is the standing guarantee that none of them can
silently produce a free purchase.

Three real surfaces, found by looking rather than by guessing:

1. **The accrual, and this is the one that would have bitten a real save.**
   `stock + ratePerHour × elapsedMilliseconds` clamped to the store afterwards is correct arithmetic
   and unsafe storage: the clamp is 3.6e13 and the product it clamps is unbounded. A deep colony and
   a long absence — or a device clock that jumped, or a save whose `lastUpdatedAt` is far in the past
   — wraps the intermediate negative, and `Resources`' own non-negative guard turns that into a
   **crash on load**. `accrue` now works out how many milliseconds it would take to *fill* the
   store and clamps the **time**, so the product can never exceed the headroom plus an hour's
   production whatever the span is. Verified by reverting the fix and watching the new test fail.
2. **`exactGeometric`** carries `base × numerator^steps` and documented that every caller must bound
   `steps` — a comment, not a guarantee. It was safe only because `TechLevel.MAX` is 30, with three
   levels of margin nobody had measured.
3. **`openingDiscount`** was the one that already went wrong: carried as an exact power in round 13,
   a convergence level of 18 priced the Nanite Factory at **−70 deuterium**.

Every multiplication in a curve now goes through `checkedTimes`, which **throws rather than
saturates** — a cost of Long.MAX is not a cost anyone designed, it is a wrong answer wearing a
plausible face, and it would be spent against rather than crashed on. The error names the curve and
the level, so the next session to push a cap past what Long can hold finds out at the point of
definition instead of at the point of use.

`OverflowSafetyTest` is the standing proof: every building and every project walked to the deepest
level the game defines, asserting positive and computable; every duration likewise; the accrual
driven at a thousand years; and the composability property re-checked either side of the store
filling, because the clamp changed *how* the sum is reached.

**The real ceiling, now that it is known:** `Resources.of` refuses anything above 2.56e12 whole
units, which the Nanite Factory's metal reaches around level 46. `MAX_UPGRADE_LEVEL` is 40, so the
declared cap sits six levels inside the arithmetic one — and a test now pins the two together rather
than leaving the margin to be rediscovered.

### Watch next round

- **Still nothing played since 0.2.0.** Four rounds have shipped on one session's feedback.
- **Crystal at depth is untouched and still the biggest open balance item** — the two branches cost
  ~1.1 : 1 and ~1.3 : 1 where the mines cost 2.5 : 1, so every hour in the branches is an hour the
  income ratio is wrong for. Discounting both branches together makes them slightly more attractive
  early, which pulls that forward rather than pushing it back.
- **`TechLevel.MAX` at 30 is three levels from the arithmetic ceiling** for the dearest adaptation
  base. Fine today, and now asserted, but it is the number to check before anyone raises it.

## Round 16 — 0.2.7, the opening gets a pulse (2026-08-09)

Two instructions, an hour apart, and the second one is the round. The first moved a constant; the
second described a game and turned out not to be reachable by moving that constant at all.

### The feedback, verbatim

> "We did balancing recently, but I still feel the early game is WAAAY too slow! Let's try a 10x
> boost, instead of 3x as we did, especially constructions times"

> "I want a 2/3 min build time at the very first levels, then 30min should be ok when you can use
> Galaxy. Very long time in mid-late are ok, but now we need to give some adrenaline to users"

### What shipped, in three parts

**1. `OPENING_DISCOUNT_DIVISOR` 3 → 10.** The linear ramp is unchanged in shape and
`FULL_PRICE_LEVEL` is unchanged at 9 — measured rather than assumed, see below. Level 1 of the
Metal Mine is 6 metal against a full price of 60.

**2. The clock stopped riding the price's ramp by the square root, which was a bug wearing a
feature's clothes.** Round 13 got the early builds shortened "for free" because round 11 had made
duration a function of cost — and free is exactly what it was worth. The price fell by the ramp's
factor and the wait by its *root*, so inside the ramp a build no longer took as long as earning it
did: it took `1 / sqrt(discount)` times longer. At the shipped 3x that is **1.73x**, at 10x it would
have been **3.16x**. The stretch of the game round 13 set out to speed up was, in the only unit a
player waits in, the part furthest behind its own rule. The root is now taken of `fullPriceCost` and
a ramp applied to the minutes, which is what `ResearchBalance` and `AdaptationBalance` have always
done.

That is also the round's cheapest piece of evidence: `a build takes about as long as earning it
does` **fails at level 2** under the old arrangement at a 10x divisor, and passes under the new one.
The test caught the shape before the sim measured it.

**3. The clock's ramp is geometric where the price's is linear, and this is the part that was
asked for.** Davide named two anchors in minutes, and the first is outside the linear family's
reach — not by a choice of constant but arithmetically. A linear recovery hands back an equal share
per level, so at level 2 it charges `(span + D − 1) / (D · span)`, which **cannot fall below
`1 / span`** however large `D` grows. A first Metal Mine upgrade is 40 full-price minutes over a
span of 8, so 5 minutes is the floor of that entire family and the ask was 2. Widening the span
reaches the first anchor and loses the second: it is level 9 at Robotics 4 that makes "30 minutes at
the galaxy" true, and a longer ramp is still discounting there.

Geometric compounds, so the extra steepness sits where it is wanted: **two thirds per level below
`FULL_PRICE_LEVEL`**, same convergence level, so there is still one landmark rather than two.
`MINIMUM_UPGRADE_DURATION` went 5 minutes → **2**, because at 5 the floor would have been the answer
to Davide's question instead of the curve's.

Round 14 rejected geometric for the *price* and that stands unchanged — it wanted a fractional root
between "how cheap at the start" and "where does it stop", and `core` has no fractional anything.
This is not that problem: the two ends were named in minutes, 2/3 a level hits both, and the integer
curve carries it exactly.

### The build clock, before and after

Metal Mine, at Robotics 0 — and in the last column at the Robotics level a colony actually holds
when it buys that level, which is the number the player sees.

| Level | 0.2.6 | 0.2.7 | full price | felt, at the Robotics of the day |
|---|---|---|---|---|
| 1 | 20m | **2m** | 32m | 2m (R0) |
| 2 | 24m | **2m** | 40m | 2m (R0) |
| 3 | 36m | **4m** | 48m | 2m (R1) |
| 4 | 48m | **8m** | 60m | 4m (R1) |
| 5 | 1h 00m | **15m** | 76m | 5m (R2) |
| 6 | 1h 20m | **27m** | 92m | 6m (R3) |
| 7 | 1h 44m | **52m** | 116m | 10m (R4) |
| 8 | 2h 16m | **1h 33m** | 140m | 18m (R4) |
| 9 | 2h 52m | 2h 52m | 172m | **34m (R4)** |

Both anchors land: the first taps are 2–5 minutes across every facility (Metal 2, Crystal 2, Solar
3, Robotics 3, Deuterium 5), and the level the galaxy opens on is 34 minutes.

### The reading that justifies it, and the report that had to be written to get it

**Every existing report in `:sim:run` was blind to this change**, and that is worth recording rather
than glossing. They all check in every three hours, so a facility advances at most one level per
visit and a duration only matters if it exceeds the gap. Against that cadence a two-minute build and
a fifty-minute one are the same reading — which is why day-1 through day-7 progression is *identical*
before and after part 3 landed (20 / 32 / 39 / 43 / 57 building levels either way).

So `printFirstSitting` is new: one-minute resolution, everything affordable started on any free
facility, one hour from genesis. It measures the session a player stays inside, which is what
"adrenaline" is a property of.

| Reading | 0.2.6 | **0.2.7** |
|---|---|---|
| Completions watched inside 10 minutes | **0** | **7** |
| inside 30 minutes | 2 | 9 |
| inside the hour | 6 | 10 |
| Building levels after 10 minutes | 4 | **11** |
| after 30 minutes | 6 | 13 |
| Longest stretch with nothing landing | 24m | **8m** |

**A player's first ten minutes went from nothing at all to seven things finishing.** That is the
whole round in one row.

### What the cost half bought, which is much less

| Reading | 0.2.6 | 0.2.7 |
|---|---|---|
| Building levels, day 1 | 17 | 20 |
| day 2 | 28 | 32 |
| day 4 | 40 | 43 |
| day 7 | 55 | 57 |
| Research opens | hour 12 | **hour 6** |
| Robotics 4, so the ladders and every Blocked world | hour 54 (d3) | **hour 33 (d2)** |
| Projects finished by day 4 | 10 | 13 |

**A 10x discount bought about 8% more progression on day 4 than a 3x one did**, and the sweep says
that is not a tuning failure — it is saturation. `FULL_PRICE_LEVEL` swept at the 10x divisor:

| `FULL_PRICE_LEVEL` | day 1 | day 2 | day 3 | day 4 | day 7 | Robotics 4 |
|---|---|---|---|---|---|---|
| **9 — shipped** | 20 | 32 | 39 | 43 | 57 | hour 33 |
| 11 | 21 | 34 | 41 | 46 | 59 | hour 33 |
| 13 | 22 | 35 | 43 | 49 | 61 | hour 27 |
| 16 | 23 | 37 | 46 | 51 | 64 | hour 27 |

Nearly doubling the ramp's length buys 8 levels on day 4 and pushes the discount deep into levels
Davide said should be full price. **9 stays**, and it stays for the reason round 14 chose it rather
than by inertia: the landmark moved forward with the discount, so the mines are still at 8–9 when
Robotics 4 lands.

### Why cost saturates, measured — and the lever nobody has pulled

The colony is not short of money in the opening and it is not short of time. It is short of
**check-ins**. `startUpgrade` refuses a facility that is already building, so a visit can advance
each facility by exactly one level; six facilities × eight visits is the ceiling, and no discount
touches it. The opening report says the rest: **the colony has nothing in flight for 95.83% of the
first 48 hours** (87.50% at 0.2.6, 91.66% at the cost change alone), the longest unbroken silence is
8h 52m, and the median check-in books **9 minutes** of work.

Which is the honest shape of the thing: this round made the first *session* dense and the days
between them emptier. Both are consequences of the same change and only one of them was asked for.
**The lever that would raise progression per real-world day is a build queue** — letting one visit
book several levels of a facility — and that is a mechanic, so it is Davide's, not the build's. It
is named here because two rounds have now ended by concluding "the next lever is income", and this
measurement says it is not income.

### What it cost

- **Fifteen tests moved**, all of them numbers rather than shapes, plus three fixtures that were
  measuring something adjacent and broke on the timings: `FutureEventsTest` needed a pair of builds
  that no longer tie at 8 minutes, `StartUpgradeTest`'s Robotics divisor needed a level deep enough
  that a third of it clears the 2-minute floor, and `AdvanceResearchTest`'s window went from 20
  minutes to 4.
- **`BalanceCurveTest`'s earning-time rule is now two tests, not one.** Round 11's identity holds
  from level 9 up, and inside the ramp a new test asserts the divergence is *deliberate and closing*
  — every ramp level builds strictly faster than the income paying for it, a fifth of it at the
  first tap, past half by the last level of the ramp. One loop over both bands would have let a
  deliberate divergence and an accidental one wear the same bound.
- **`fullPriceCost` is `internal` rather than private**, so the duration rule can be stated against
  it. Same standard the test already held: it read the cost off production code and wrote the root
  out by hand; it now writes the ramp out by hand too.
- **The adaptation ladders' priced totals are exactly equal again at level 1** — 480 each, where a
  third of three differently-shaped baskets came out two units apart. That is luck, not design, so
  the assertion stays a proportion.
- **The ramp's first cost step is now ×3.17** where it was ×1.85, and the `BalanceCurveTest` bound
  widened to admit it. That is what a deeper linear ramp over a fixed span costs and there is no
  setting of the divisor that avoids it.

### Watch next round

- **Still nothing played.** Six rounds have now shipped on one session's feedback, and the last two
  were each corrected within the hour.
- **The idleness between check-ins is now the biggest open item in this file**, ahead of crystal at
  depth. 95.83% with nothing in flight is the highest this file has ever recorded, and it is the
  direct cost of the change that was asked for.
- **Crystal at depth is untouched** and still structural: the two branches cost ~1.1 : 1 and
  ~1.3 : 1 where the mines cost 2.5 : 1.
- **The dials, in the order they are likely to be wanted:** `OPENING_SPEEDUP_NUMERATOR`/
  `_DENOMINATOR` in `Curves.kt` (2/3 — the clock's steepness), `MINIMUM_UPGRADE_DURATION` in
  `PlaceholderBalance` (2 minutes — the floor of the whole game), then `FULL_PRICE_LEVEL` (9) with
  the sweep above, and `OPENING_DISCOUNT_DIVISOR` (10 — the price only).

## Round 17 — 0.3.0, the fleet constant gets measured and halved (2026-08-10)

**A measurement round, like round 12 — except this one moved something.**
`FleetBalance.EXTRACTION_PER_HOUR = 40` was written into `core` having never been through
`:sim:run`, and the fleet sheet's §9 says in as many words that it *"must not ship unswept"*. This
round builds `printFleetReport()`, runs the sweep, and **takes the constant to 20 before the slice
merges**. Davide delegated the call — *"You decide for me based on your research and logs"*,
2026-08-10 — so what follows is the evidence and then the decision, in that order.

**Two other calls were settled in the same breath and are written up at the end**: the frontier band
multipliers, which Claude Design corrected and this round ratifies, and the hauler's price, which
Design invented and this round rejects by about an order of magnitude.

### What was added to the harness

`printFleetReport()`, after `printOpeningReport`, and it is deliberately shaped by three mistakes
this file already made:

1. **The no-fleet column is in the same run**, probes on in both, so the fleet is the only variable.
2. **A third ledger.** `fleetBusy` joins `colonyBusy` and `probeBusy`, and what is printed is the
   share of covered time each one is the **only** thing covering.
3. **Kinds first, count second** — `gather` is one verb with 266 surveyed targets, not 266 actions.

Plus `FleetBalance.shipCost`, which the sheet's §4 specified and nobody had written. Everything else
in `:sim:run` is byte-identical to the pre-round output, checked by diff.

### The three findings that matter, in order

**1. The fleet buys back no idle time at all.** The third ledger, 48h, shipped tuning:

| Ledger | Covers | Is the **only** cover for |
|---|---|---|
| colony | 3.15% | **0.00%** |
| probes | 96.77% | **22.49%** (10h 27m) |
| fleet | 75.00% | **0.00%** (0h 0m) |

`Hours with nothing at all in flight` is **2.08% with and without the fleet**; the longest silence is
**0h 47m** either way; the median check-in books **360 minutes** either way. The probe already took
that ground at 0.2.x, and §0 of the sheet predicted exactly this — *"a slice justified by the
idleness number is a second probe"*. **Whatever this mechanic is for, it is not for the idleness
number, and no future round may claim it.**

**2. Distance buys nothing, measured — §3.5's worst case, confirmed.** Over a fortnight, **56 of 56
dispatches went to band 0**, to **two worlds**, both in the home system: `[3:165:8]` ×32 for metal and
`[3:165:13]` ×24 for crystal. Of 266 surveyed worlds the bands available were **4 / 0 / 66 / 196** —
there is **no band 1 at all**, because `probeTargetFor` aims at the longest flight that still lands
before the next check-in and therefore only ever surveys distant systems. And at a four-a-day cadence
the gaps are 5h, 6h, 4h and 9h, so the player **never asks for the 1h rung and never asks for the 24h
one** — which are the only rungs a far world can be reached on. The map is a backdrop, twice over.

**3. The crystal column cannot size the constant, and the reason is a feedback loop.** Crystal
short-at-all over the fortnight lands in **180–275 of 336 against a 292 control** across the whole
12-cell grid — every difference inside the ~50-hour band round 12 established as noise. The mechanism
is that the adaptive player gathers crystal *only while crystal is short*, so a higher rate buys
**fewer crystal runs** (68% → 43% of dispatches from rate 10 to 40) rather than less scarcity. The
sole-blocker ledger meanwhile flips to metal — 100 → 296 hours — because hulls are bought with metal.
**Crystal is relieved by about a quarter and stays the dominant shortage at every candidate rate.**

### The sweep

48h columns from the four-a-day player; fortnight columns from the hour-stepped colony, which is the
bot the numbers above are quoted against. Hulls are bought greedily out of what is left after the
buildings — the sheet's own account of why the fleet gets bought. Control: 32 levels @48h, Robotics 4
at hour 34, crystal short-at-all 292.

| `EXTRACTION_PER_HOUR` | levels @48h | hulls @48h | duty | fleet metal / colony **metal** | fleet crystal / colony **crystal** | crystal short-at-all | Robotics 4 |
|---|---|---|---|---|---|---|---|
| 10 | 33 | 5 | 75.0% | 10.8% | 7.6% | 246 | hour 34 |
| **20** | 34 | 6 | 74.6% | 25.9% | **15.5%** | 275 | hour 34 |
| 30 | 33 | 7 | 74.4% | 46.3% | 23.3% | 202 | hour 33 |
| **40 — shipped** | 33 | 7 | 74.1% | 63.8% | **31.1%** | 222 | hour 34 |

| hull base | levels @48h | hulls @48h | hulls @14d | fleet crystal / colony crystal |
|---|---|---|---|---|
| 40 metal | 33 | 9 | 18 | 65.0% |
| **80 — shipped** | 33 | 7 | 17 | 31.1% |
| 140 metal | 34 | 5 | 15 | 29.6% |

**Every guardrail is met at every candidate.** Levels at 48h never leave 32–34 against a control of
32; Robotics 4 never leaves hour 33–34 against a control of 34 — the deuterium exclusion does exactly
the job §1 gave it; the fleet is bought at every price and the mine out-paybacks every hull at every
rate by 4× or more, so §4's invariant holds and §6's proposed `BalanceCurveTest` would pass at 40.
**Nothing in the guardrails constrains the choice.** The only reading that moves monotonically with
the rate is the fleet's share of the opening colony's income in the currency it chose.

### The recommendation, for Davide

**`EXTRACTION_PER_HOUR` 40 → 20. Hull base unchanged at 80 metal / 20 crystal.** Three arguments:

1. **A fleet-first player must not out-produce their own colony.** The purchase order is worth
   measuring and it brackets the answer: buying hulls *before* the buildings rather than out of the
   residual takes the crystal share at rate 40 from 31.1% to **98.6%** — the fleet delivering as much
   crystal in 48 hours as the whole colony. At rate 20 the same aggressive player reaches 49.2%.
   **20 is the highest rate at which no purchase order makes the fleet the economy.**
2. **Per hull, against the colony that receives it.** At 40 one skiff on a 6h run brings home
   **3.4 hours** of a genesis colony's crystal income — ~55% of a crystal mine while it is out, which
   is the *"47%"* §4 flagged as the number most likely to be wrong. At 20 it is 1.7 hours, ~28%: three
   or four skiffs match your crystal mine, which is a fleet you build up to.
3. **§3.5's frontier band is not implemented and will multiply this.** Design's ratified multipliers
   are ×1.15 / ×1.55 / ×2.30 by band. A rate sized against today's flat hold is a rate that becomes
   up to 2.3× larger the day the band lands. 20 leaves the frontier at ~46 effective; 40 puts it at 92.

**30 is the upper bound defensible on this evidence** if the opening kick matters more than the
ceiling. **40 is not defensible.** **10 is too small**: §4's own hour-zero story — 132 metal from a 3h
run next door, *"the first thing that ever arrived from outside the colony"* — becomes 33 metal, which
is 22 minutes of the genesis colony's metal income and does not read as an event. At 20 it is 66 metal
and 44 minutes, which does. **Halving the rate halves that anchor, and that is the cost of the
recommendation, stated rather than buried.**

**The hull base is left alone because nothing measured argues with it** — the colony guardrail is met
at 40, 80 and 140, hulls are bought at all three, and the invariant holds at all three. The only thing
the base changes is fleet size, and there the sheet's stated intent is out by ~2× at every price:
§4 expects *"three to four skiffs at the opening and six or seven at depth"* and the measurement is
**7 at 48 hours and 17 at a fortnight** at the shipped 80. Landing the sheet's numbers needs a base
well above 140, and that is a design intent question rather than a measurement.

### Watch next round

- **Still nothing played.** Seven rounds on one session's feedback.
- **The window ladder is half-unmeasurable and will stay that way.** Every report but
  `printFirstSitting` checks in at three hours or more, so the 1h rung has never been simulated by
  anything, and a four-a-day player never wants the 24h one. If the frontier is meant to be reachable,
  something has to make a player want a window longer than their own absence.
- **`printFleetReport` measures a strategy, and two of its free choices move the answer.** The window
  policy is worth ~25 points of duty cycle; the purchase order is worth four to six building levels and
  roughly 3× the crystal income share. Both are printed rather than chosen, and both are the first
  thing to re-read if a later round disagrees with this one.
- **The dials, in order:** `EXTRACTION_PER_HOUR` (now 20, and the sweep above), then the §3.5 frontier
  band (decided below, not implemented), then `HULL_BASE_METAL` (80), then `DANGER_PERCENT_PER_POINT`
  (10), which this round never had a reason to touch because every dispatch went to band 0.

### The three calls, decided — Davide delegating, 2026-08-10

> "You decide for me based on your research and logs"

**1. `EXTRACTION_PER_HOUR` 40 → 20.** Not a guardrail decision: levels at 48h never left 32–34 and
Robotics 4 never left hour 33–34 at any candidate, so the guardrails constrain nothing here. Three
readings decided it, and the first is the one that matters.

**A fleet-first player must not out-produce their own colony.** The sweep's headline number is taken
from a player who buys hulls out of what is left after the buildings. Invert the purchase order — buy
hulls *first* — and at 40 the fleet's crystal goes from 31% of the colony's to **98.6%**: a fleet
delivering as much crystal in forty-eight hours as everything else put together. At 20 the same
aggressive player reaches 49%. **20 is the highest rate at which no purchase order makes the fleet the
economy**, and a constant that is only safe if the player buys in the order the designer imagined is
not a safe constant. Round 8's cap proposals died on the same principle from the other side.

Per hull it is also the legible number: at 20 a skiff on a 6h run brings home 1.7 hours of a genesis
colony's crystal income — about 28% of a Crystal Mine while it is away, so three or four skiffs match
the mine. At 40 it is 3.4 hours and ~55%, which is the "47%" the sheet's own reviewer had already
flagged.

And **§3.5's frontier band is not built yet and multiplies this by up to ×2.30.** Sizing at 20 lands
the frontier near an effective 46; sizing at 40 would put it at 92 the day slice 2 ships, which is a
rebalance arriving disguised as a feature.

**The cost, stated rather than buried:** the sheet's hour-zero anchor halves. A first run next door
brings 66 metal instead of 132 — and 30 is the upper bound this evidence defends, so if the opening
reads thin that is the move and it is one number. **10 was rejected**: 33 metal is twenty-two minutes
of income, and the first thing that ever arrives from outside the colony should not read as a
rounding error.

**2. The frontier bands: ×1.00 / ×1.15 / ×1.55 / ×2.30, ratified.** The sheet proposed ×1.35 and
×1.60 for bands 2 and 3 and Claude Design showed they create no crossover at all. Reproduced here
against the shipped formulas, at the 24h window, against a same-richness world at home:

| band | round trip | station | danger | sheet's | net vs near | break-even |
|---|---|---|---|---|---|---|
| 0 · own system | 20m | 23h 40m | 0 | ×1.00 | 1.00 | — |
| 1 · within 125 systems | 58m | 23h 02m | 1 | ×1.15 | 1.01 | ×1.14 |
| 2 · rest of galaxy | 4h 48m | 19h 12m | 2 | ×1.35 | **0.88** | **×1.54** |
| 3 · another galaxy | 9h 20m | 14h 40m | 3 | ×1.60 | **0.69** | **×2.31** |

The error is structural rather than a slip: **the flight is subtracted from the window while the band
is multiplied into the hold, and danger climbs with the same distance the band pays for.** Break-even
is the right target rather than a timid one — the band cancels the distance penalty exactly and then
*richness* decides, which is the only thing that makes a map worth reading, and there is no unpriced
risk left to compensate because danger is deterministic and already inside that arithmetic. Band 3
sits a hair under its own break-even deliberately, because the galaxy sheet prices a galaxy hop as a
late-game undertaking. Written into `FleetBalance.FRONTIER_PERCENT` and **read by nothing until slice
2.**

**3. The hauler is not 240 metal. It is ~1,000, and the reasoning was inverted.** Design drew
240 / 60 as "three times a skiff for four berths at half the speed". The comparison that decides it is
not hauler-against-skiff, it is **hauler against the four skiffs it replaces**, since it carries four
berths:

| hauler ÷ four skiffs | 3h | 6h | 12h | 24h |
|---|---|---|---|---|
| own system | 0.86 | 0.94 | 0.97 | 0.98 |
| twenty systems out | 0.51 | 0.80 | 0.91 | 0.96 |
| across your galaxy | — | — | 0.33 | 0.75 |

**A hauler is strictly worse than four skiffs per berth, at every target and every window.** Its
entire case is therefore price — which makes the price the whole design rather than a detail. At 240
it is 60 per berth against **243** per berth for the four skiffs it replaces at the earliest
opportunity, and against **548** once you own three. Four to nine times cheaper on arrival: the hauler
would dominate the moment it unlocked and the skiff would become a hull you buy once, which deletes
the composition decision the second hull exists to create.

**Anchor it to the four skiffs it replaces: base 1,000 metal / 250 crystal, its own ×1.5 curve.** That
is 250 per berth against 243 for four skiffs when you own one, and against 548 when you own three — so
**skiffs are the early buy and the hauler is the buy at scale**, a crossover in *ownership* laid on top
of the window-and-distance one. Marked for the slice-4 sweep the way `SurveyBalance.COST_METAL` was,
and not implemented here: the hauler is slice 4 and nothing should ship a constant a report cannot yet
read.

### What this round could not measure, and what slice 2 owes because of it

**The frontier is untestable by this harness as shaped**, and that is now a blocker rather than a
footnote. A four-a-day player faces gaps of 5h, 6h, 4h and 9h, so they never ask for the 1h rung and
never ask for the 24h one — and the 24h rung is the only one a band-2 or band-3 world can be reached
on. Confirmed from the other side by the run itself: **56 of 56 dispatches went to band 0, to two
worlds, both in the home system.** Of 266 surveyed worlds the bands available were 4 / **0** / 66 /
196 — not one band-1 world, because `probeTargetFor` only ever surveys distant systems.

So the bands ratified above are, at this moment, a decision no report can check. **Slice 2 owes a
once-a-day runner and a probe strategy that surveys near systems**, or §3.5 ships on arithmetic alone.

## Round 18 — 0.5.1, the wall was never the map, it was the sample (2026-08-11)

**The first round driven by someone playing the shipped build since round 16**, and the second time
a device has corrected paper this week. Two numbers moved, one of them in a place no previous round
had looked.

### The feedback, verbatim

> "Galaxy interactions are too tough in the early game! I would expect the user to be able to
> interact with neighbouring planets without too many challenges, with I needed 2 day to get
> robotics to level 4, and now I need to upgrade at least 4 adaptations for the easier planet"

Both halves reproduce exactly, and that is worth saying first because it is unusual: `printGateClock`
already put Robotics 4 at **hour 33** — two days — and the harness's own home system asked for
**Thermal 1 and Gravitic 4**, five levels across two ladders, for its cheapest neighbour. Nothing had
to be discovered to confirm the complaint. What had to be discovered was whether that home system was
bad luck.

### The reading that decided the round

`printDoorstepReport` is new, and it is the first report in this file that sweeps **seeds** rather
than hours. The reason it had to exist: every other galaxy report measures the map, and the map is
not what a player sees. Genesis surveys the home system and nothing else, so ~4.75 worlds are the
entire content of the Galaxy screen on day one — and *which* 4.75 is the one roll nobody re-rolls.
One seed can only ever say what one player saw.

1,000 seeds, the cheapest non-home world of each home system, counted in adaptation levels and in
what buying them from zero costs:

| Levels to the cheapest neighbour | Before | After |
|---|---|---|
| 0 — already tolerable | 3.22% | **34.00%** |
| 1 | 6.14% | **65.80%** |
| 2 | 6.14% | 0.20% |
| 3 | 6.44% | 0% |
| 4 | 7.65% | 0% |
| 5 | 9.06% | 0% |
| **6 or more** | **61.32%** | **0%** |

| Reading | Before | After |
|---|---|---|
| Median levels to the cheapest neighbour | **7** | **1** |
| Median bill, priced 1 : 2 : 3 | **54,242** | **480** |
| Median research time at Robotics 4 | **39h 03m** | **0h 18m** |
| Cheapest neighbour needs one ladder only | 31.31% | 99.90% |
| Can change a verdict for one level | **9.36%** | **99.80%** |

**Davide's five-level home system was in the better third.** The median colony was asked for seven
levels, 54,242 resources and thirty-nine hours of the one shared research slot — during which the
production branch researches nothing — to make one row on one screen say something different. 78%
were asked for four or more, which is his sentence arrived at from the other side.

### Why it is a sampling defect and not a balance one

The adaptation branch does what `galaxy-sheet.md` §9 designed: each level roughly doubles the
settleable count of the galaxy, 17 → 40 → 105 → 218. That statistic is **galaxy-wide**, and a player
looking at 4.75 worlds cannot see it. 1.81% of worlds pass every band, so a home system contains one
about 8% of the time; the other 92% of colonies open on a wall and stay there for a week.

So the levers aimed at the curve are all aimed at the wrong thing, and §9 says so about the biggest
of them in advance: *"If the 'come back later' pile ever needs to be bigger, the lever is **not**
this row — it is widening all three bands together, which raises row 1 with it."* Widening the bands
or the per-level widening makes adaptation stronger **everywhere** to fix a sampling problem in
**one system**, and moves every distribution target with it.

**Choosing which system you start in changes no world's traits at all.** The galaxy is the same
galaxy; only the origin moved. Every number in `GalaxyDistributionTest` and `GalaxyBalanceTest` is
untouched by construction, and the sim's whole-space distribution table is byte-identical before and
after.

### What shipped

**1. `homeFor` gains a clause: the system must have somewhere to go.** It was *"the first world,
walking systems forward from a seeded start, that the unaided species tolerates"*; it is now the
first such world in a system that also holds a neighbour **one adaptation level** away. The walk
keeps the best system it has seen and stops at the first that is good enough, so a seed with nothing
qualifying still gets the nearest thing there is rather than the first thing walked past.

Two constants, both measured rather than chosen:

- **One level, not two.** Two levels is two projects through the one shared slot and may be two
  different ladders, so it stops being true that *your first adaptation level opens a world you can
  see*.
- **The walk crosses galaxies, but walks the seeded one whole first.** A qualifying system is
  **0.50% of all systems**, so a walk bounded to the seeded galaxy's 250 finds one **77%** of the
  time — measured, against 71% predicted. Over the whole 1,000-system space it is **99.80%** of
  the thousand seeds swept, with the last two of a thousand needing a second level.

  **The first draft got the walk order wrong and no test could see it.** A flat index over the
  1,000 systems abandons the seeded galaxy the moment its *tail* runs out — a colony drawn at system
  200 sees fifty of its own systems and then a whole other galaxy — so **50%** of colonies opened
  somewhere their seed had not named, against **22%** when the seeded galaxy is walked whole first.
  Both measured. The promise *"you open where your seed says unless that galaxy has nothing"* was
  written in a comment and checked nowhere, which is `session-roles.md`'s tilt lesson in a different
  file: every existing test asks about the home *world*, and a home in the wrong galaxy passes all
  of them. `seededGalaxyOf` is now a named function precisely so the promise can be asserted, and
  `a colony opens in the galaxy its seed names` is the assertion. Caught by an adversarial read of
  the diff before merge rather than by the suite — the same way 0.4.3's two defects were.

**2. `AdaptationBalance.GATE` 4 → 2.** Round 12 pre-authorised exactly this — *"If the gate turns
out to sit far past the first BLOCKED screen, lowering it to 2 or 3 is cheaper than re-pricing
anything"* — and it had: hour 33. Robotics 2 is **hour 12**, the same day.

| Gate | Ladders open | Median kinds offered, 2d | Refused: requirement | Refused: price |
|---|---|---|---|---|
| **4 — was** | hour 33 (d2) | 3 | 35.25% | 5.12% |
| 3 | hour 27 (d2) | 3 | 31.41% | 9.61% |
| **2 — shipped** | **hour 12 (d1)** | **4** | **25.64%** | **14.10%** |

**Read the last two columns as one trade rather than as a gain and a loss.** Round 12's own reading
is that *"a price is a curve, a slot is a rule, a requirement is a gate — and only the first of those
is fixed by tuning a number"*, so converting nine points of gate refusal into nine points of price
refusal is the point of the change. And **median kinds offered in the opening goes 3 → 4**, which is
the reading rounds 8 and 12 each concluded no number in `PlaceholderBalance` could move.

Three rather than two was rejected on the table above: it costs half the price pressure for a fifth
of the clock, because Robotics 3 and 4 are six hours apart and 2 and 3 are fifteen.

**Robotics 1 was not on the table**, and that is the one thing round 6's §3 argument still decides:
Robotics 1 is the applied branch's gate, so sharing it opens five rows at once and deletes the locked
row from normal play. What round 6's clause does *not* decide any more is 4 over 2 — it asked that
the branch open after the player has met the Galaxy screen and read a `BLOCKED` row, and nothing
gates the Galaxy tab, so that has been true since the first frame at every gate level.

**Crystal, which round 12 warned this lever leans on**, is short at 331 hours of a fortnight's 336
against 320 before. An 11-hour move, well inside the ~50-hour band round 12 says not to read as a
signal at all.

### What it should feel like, to check next round

- **The first session should end with a world you are aiming at**, rather than with six rows that
  all say no. That is the whole round in one sentence and it is the one to check first.
- **The world you unlock should disappoint**, and by more than it used to. This is the reading that
  would say the guarantee had gone too far: the doorstep world is `Settleable` only **28.10%** of the
  time against **51.15%** for the pre-change cheapest neighbour, because a world one level outside a
  band sits near the middle of the other two and richness is derived from the axes. The guarantee
  makes your nearest world *easier and poorer at once*, which is `galaxy-sheet.md`'s *"an easy world
  is a poor world"* holding rather than bending.
- **The gate may now be too cheap rather than too far.** Hour 12 is inside day one, and the ladders
  are the crystal-heaviest thing in the game. If crystal starts reading as the only shortage in the
  opening, this is the first place to look — and the lever is the gate, not the crystal curve, which
  round 7 set against the repeating basket rather than against this.

### Watch next round

- **This round did not touch a single cost, duration or band**, which is worth recording because
  four of the last six did. The complaint was about a wall and the wall was in the generator's
  *sample*, not in any curve.
- **The doorstep report is the instrument the opening was missing**, and it should be read beside
  the gate clock from now on: the gate clock says when a player may act and the doorstep says
  whether acting changes anything.
- **The colonisation gap is now the loudest thing in the file.** `SETTLEABLE` is a label and not a
  button — the galaxy offers a probe, a remedy that changes tab, and nothing else. This round makes
  the reading worth having; it cannot make it worth *acting on*, because slice #10 does not exist.
  The fleet screens, which are designed and sitting finished in `core`, are the nearer half of that.
- **Nothing here was played.** The measurement is 1,000 seeds of arithmetic against a complaint from
  one device, and the thing that closed the last two rounds was an install.

### Round 18, addendum — the instrument that was missing, and who the round could not reach (2026-08-11)

Davide, an hour after 0.5.1 shipped: *"We need benchmarks/tests against balancing. Such regression is
NOT acceptable."* Two things came out of chasing that, and only one of them is a regression.

**His colony was never touched, and could not have been.** The seed in the debug menu is the instant
a colony was founded — `1786319875349` is **2026-08-09 23:57 UTC**, a day and a half before 0.5.1
merged. `homeFor` has exactly two callers, genesis and the schema 3 → 4 migration, and a schema 9
save runs neither. Regenerated from that seed, his home is `[2:173:6]` and his cheapest neighbour is
**Thermal 5** — five levels, exactly what it was on the build he complained about.

**So the real defect is the opposite of a regression: the fix cannot reach anybody who is already
playing.** Under the new rule the *same seed* opens at `[2:169:6]` with a **one-level** neighbour
(Thermal 1) and a three-level one behind it. The changelog line *"Existing colonies keep the home
they were founded on"* was written as a reassurance about save compatibility, and for the one player
who has an existing colony it means the round did nothing at all. **Whether a colony founded before
0.5.1 should be re-homed is Davide's call** and it is a real one: nothing is built off-world yet, so
moving `home` costs a player their surveyed set and their bearings and nothing else.

#### What no test could see, which is the part that generalises

0.5.1 passed every test in the repository. That was not luck and it was not a missing assertion on an
existing number — **it was a quantity nobody was measuring**. `GalaxyDistributionTest` pins the map
and could not move, by construction, because no world's traits changed. `BalanceCurveTest` pins the
curves. Neither knows what the *first screen* says, and the first screen is the only part of the map
a new colony can see.

`OpeningBalanceTest` is that quantity, in `core` beside the other balance pins, seven readings over
200 seeds in 1.3 seconds:

| Reading | Band | Now |
|---|---|---|
| Colonies that can open a neighbour for one level | ≥ 90% | 99% |
| Second cheapest neighbour | ≤ 10 levels | 8 |
| Third cheapest | ≤ 14 | 13 |
| Median across every neighbour | ≤ 13 | 12 |
| Non-home worlds on screen | ≥ 3 | 5 |
| Colonies opening with every neighbour blocked | 40 – 90% | 66% |
| The adaptation branch opens | ≤ hour 24 | hour 12 |

Three properties of it are the point, and a later round should keep them:

- **Every reading is a band, not a value.** A balance test that pins an exact number forbids tuning,
  which is the opposite of what these are for. Where a band has two sides it is because both sides
  are real: *"most colonies still open on a screen where every neighbour is blocked"* fails at 95%
  because that is the wall this round existed to leave, **and** at 20% because an easy world is a
  poor world and `Barren` must stay the common answer.
- **The ceilings sit between the old readings and the new**, never on either. On the old ones the
  test would pass a full regression; on the new ones it would forbid tuning.
- **It was verified by breaking it.** Neutralising `DOORSTEP_LEVELS` reproduces the pre-0.5.1
  opening, and three of the seven fail with the readings that describe it — 9% can act for one
  level, 95% open on a wall, the second neighbour is twelve levels out. A balance test nobody has
  watched fail is a balance test nobody should trust.

**And the reading that would have caught the fear this round could not answer**: `printWholeHomeSystem`
in `:sim`, measuring the whole screen rather than its cheapest row. Round 18 was argued entirely on
the cheapest neighbour, so *"the doorstep clause put me in a system whose other worlds are extreme"*
was unfalsifiable at the time it shipped. It is not: every rank improved, 12 → 8, 15 → 13, 14 → 12.


### Round 18, second addendum — the check-in gets bands too, and all of them were watched to fail

`OpeningBalanceTest` pinned the map side of the opening. This adds `CheckInBalanceTest`, which pins
the side almost every round in this file was actually called by: **not a curve, a session**. Round 8
found check-ins with nothing on them, round 11 found the wait outgrowing the earning, round 12 swept
every lever at *"nothing to do"* and moved none of them, and round 16 was Davide asking for
*"adrenaline"* in the first sitting. Each was argued from a `:sim` reading that nothing asserted.

| Reading | Band | Now | The round it comes from |
|---|---|---|---|
| Dead check-ins in the first two days | 0 | 0 | 8 |
| Completions inside the first ten minutes | ≥ 3 | 7 | 16 |
| First thing to land | ≤ minute 5 | minute 2 | 16 |
| Longest silence in the first quarter hour | ≤ 8m | 6m | 16 |
| A second *kind* of decision arrives | ≤ hour 24 | hour 11 | 12 |
| Check-ins that leave work booked | ≥ 60% | 100% | 11 |

Two decisions inside it are worth keeping when this is next touched. **A completion is what a player
watches; a start is what they did** — the event log holds both, and counting the pair doubles every
reading and makes a change that only moved starts look like a change to the session. And **the
silence band is scoped to the first quarter hour on purpose**: completions thin out later in the
hour by design, because the curve is exponential, so a bound over the whole hour would pin the shape
of the curve rather than the density of the sitting.

#### Every band was verified by watching it fail

A balance test nobody has seen fail is a balance test nobody should trust, so each was checked
against a mutation that reproduces a state this file has already been through:

| Mutation | Reproduces | Caught by |
|---|---|---|
| `MINIMUM_UPGRADE_DURATION` 2m → 25m | the 0.2.6 first sitting | *completions inside ten minutes* (**0**, floor 3) and *first thing to land* (**minute 16**, ceiling 5) |
| `AdaptationBalance.GATE` 2 → 4 | Davide's own 0.5.1 complaint | *the adaptation branch opens on the first day* (**hour 30**, ceiling 24) |
| `DOORSTEP_LEVELS` neutralised | the pre-0.5.1 opening | three of `OpeningBalanceTest` — 9% can act for one level, 95% open on a wall, second neighbour twelve levels out |
| `OPENING_DISCOUNT_DIVISOR` 10 → 3 | round 16's cost half, undone | **nothing, and that is correct** — round 16 measured it as worth ~8% of day-4 progression, which is tuning rather than shape, and a band that fired on it would forbid tuning |

That last row is the one to read twice. **A balance suite that catches everything is a suite that
forbids balancing.** The bands exist to fail on a change of *shape* — a session that goes dead, a
gate that leaves day one, an opening screen that becomes a wall — and to stay quiet through a round
that moves a number on purpose.

#### And one report was lying

Two tables in `:sim` wrote `Robotics Factory 4` as a literal, so when the gate moved to 2 they went
on attributing the adaptation ladders to a level reached at hour 33 when they had opened at hour 12.
Round 18's own *"hour 33"* framing came from one of them. Both read `AdaptationBalance.GATE` now.
Every round in this file is argued from these readings, so a report that quietly disagrees with the
game is worse than no report at all.

---

## Round 19 — the suite gets a second instrument, and the second one is a photograph (2026-08-11)

Davide, three times, on the 0.5.1 regression: *"We need benchmarks/tests against balancing. Such
regression is NOT acceptable."*

Round 18's addendum answered the *tests* half — `OpeningBalanceTest` and `CheckInBalanceTest`, twelve
bands on the opening and the session. This round is the **benchmarks** half, and the distinction
turns out to be real rather than a wording preference: a band and a benchmark fail on different
things, and 0.5.1 needed both.

### Why the bands alone were not enough

A band is a guardrail. It is written wide on purpose — narrow enough to fail on a change of shape,
wide enough that a deliberate round of tuning passes — and that width is exactly the problem when
what you want is a **review**. The readings a band lets through are the ones a designer most wants
to see before agreeing to them, and a band's whole job is to say nothing about them.

So 0.5.1 could have shipped with round 18's bands in place and still arrived at a review with prose
in front of it rather than numbers. The bands would have gone green — the opening *did* get better
by every one of them — and nothing would have shown that the doorstep rule made the second and third
neighbours worse at the same time.

### The benchmark

`BalanceBenchmark` renders the whole balance surface as one deterministic page of **derived**
readings — never a constant copied out — and `BalanceBenchmarkTest` asserts that page equals
`BalanceBenchmarkGolden`, a committed string in the file next door. One equality, not a band.

The consequence is the entire point:

> **A change to any balance number arrives in the pull request as a diff on a page of player-visible
> readings, whether or not any band was crossed.**

138 lines across nine sections — the landmark clock, the first sitting, progression day by day,
which resource is doing the blocking, cost/wait/payback per building level, the two research
branches, the map, the opening screen, the hull curve. A row that merely restated
`AdaptationBalance.GATE` would tell a reviewer nothing the diff had not already shown them; a row
that says *what hour the branch opens* moves when the gate moves **and** when the build curve, the
discount, the Robotics divisor or the opening stock move.

**When it fails, it is not a bug.** It means a balance number moved, which is what balance work is.
Read the diff, decide whether it is what was wanted, then paste the new page and write up the round.
What must not happen is the paste without the reading — which is why the bands stay: they are the
backstop for a golden approved without being looked at.

### Two more band files, for what round 18 did not reach

`ResearchSlotBalanceTest` — the six ladders competing for one empire-wide slot. Both published
tables were already pinned value by value, which records what a level costs and asserts nothing
about whether anyone would buy it. Two ways a branch stops being live: it stops paying back, or it
stops being *comparable*. The second has already broken in the wild — the opening discount shipped
reaching the applied branch and not the ladders, taking the step between them from the sheet's
**1.9×** to **5.8×** — and every table test passed, because both tables were still exactly what they
were designed to be. It was the relationship between them nobody was looking at.

`ProgressionBalanceTest` — week two. Every round in this file looks at day one and day two, because
that is where complaints come from, and a cost curve compounding at +50% against production
compounding at +25% *will* stall eventually. The only question is whether it stalls inside the
fortnight a player is still around for.

| Reading | Band | Now |
|---|---|---|
| Adaptation level ÷ priciest applied technology, levels 1–5 | 1.3 – 3.0 | 1.92 |
| Spread between the three ladders once priced | ≤ 1% | 0% |
| Best first applied level pays for itself | ≤ 24h | 1.69h |
| Some applied level still worth taking at fortnight depth | ≤ 168h | yes |
| Levels added in week two | ≥ +8 | +19 |
| Day-14 income against day-7 | ≥ 2× | 3.19× |
| Fortnight hours with nothing running | ≤ 45% | 14% |
| Levels added between day 3 and day 7 | ≥ +8 | +15 |

### The mutation battery, and the row that matters most

| Mutation | Bands that objected | Benchmark |
|---|---|---|
| Adaptation discount removed — **the real 5.8× regression** | `ResearchSlot` | ✓ |
| One ladder priced differently | `ResearchSlot` | ✓ |
| Production stops compounding | `Opening`, `ResearchSlot`, `Progression` | ✓ |
| Cost curve steepened ×1.5 → ×2 | `Opening`, `ResearchSlot`, `Progression` | ✓ |
| Research priced out of reach (×20) | `ResearchSlot`, `Progression` | ✓ |
| Adaptation gate back to Robotics 4 | `Opening` | ✓ |
| Minimum build 2m → 25m | `CheckIn` | ✓ |
| **Metal income 90 → 95 — pure tuning** | **none, and that is correct** | ✓ |

The last row is the design of the whole suite in one line. **A balance suite that catches everything
forbids balancing.** The bands stayed quiet through a deliberate tuning change and the benchmark
recorded it — which is precisely the division of labour, and neither instrument could have done both.

### One finding, unbanded on purpose — and it is Davide's call

The benchmark's `[progression]` section splits the bank three ways rather than pricing it into one
figure, because the lopsided bank is a failure a priced total hides perfectly. It immediately showed
this:

| | metal | crystal | deuterium |
|---|---|---|---|
| day 7 | **56,298** | 2,959 | 704 |
| day 14 | **208,970** | 14,381 | 7,035 |

That is round 7's symptom exactly — *"closed the week holding 49,544 metal it had nothing to spend
on"* — reappearing past the opening rather than inside it, and 19:1 at day 7. The cause is not the
production ratio this time. It is that the colony has **six build slots and each build takes hours**,
so past the first week spending is rate-limited by slots rather than by income, and metal is the
resource with nothing to buy. `[pressure]` agrees from the other side: deuterium is in the shortage
set on 80.59% of blocked hours, crystal on 33.22%, metal on 7.23%.

**No band was written for it**, deliberately. Round 7's decision was about the production *ratio*
and `BalanceCurveTest` already pins that; a band on the bank would be a new design rule, and design
rules are Davide's. The reading is on the benchmark page where it cannot be lost, and the options —
a storage building, more build slots, the Nanite Factory arriving earlier than Robotics 10 at hour
289 — are all his to pick between, or to shrug at.

### Addendum — the metal pile, measured properly (2026-08-11)

Davide, on the round above: *"56k on Metal in one week seems extreme, I would expect that number in
3/4 months."*

The first thing that reading needed was a correction to the **instrument**, not to the game. The
benchmark's player bought the five opening facilities and stopped, on the note that the Nanite
Factory sits behind Robotics 10 and is out of reach — true for a day and false for the rest of the
fortnight, since the colony reaches Robotics 10 on day 12. A player holding two hundred thousand
metal buys the thing that costs twenty thousand of it. With the sixth facility in the plan the tree
at day 14 reads 18 / 17 / 14 / 17 / 10 / **4**, and day 7 is untouched — so the 56,298 stands.

Then the reading that decides between the two possible diagnoses. Over the fortnight, per resource:

| | earned | spent | placed |
|---|---|---|---|
| metal | 1,022,626 | 779,089 | **76%** |
| crystal | 345,755 | 334,598 | 96% |
| deuterium | 81,337 | 74,452 | 91% |

**Only metal strands.** The 243,537 it never places is, to within five hundred units, the entire
day-14 bank of 244,037 — so the pile is not an economy running fast, it is one resource the game
does not ask for in the proportion it makes it. An economy that were simply too quick would pile up
all three together, and crystal and deuterium are consumed to 96% and 91%.

Nor is it the production ratio, which is the lever round 7 reached for. The colony *spends* metal
against crystal at **2.33 : 1** and the mines produce **2.5 : 1** — a 7% oversupply, against a 24%
strand. The rest is structural: there are six facilities, each takes hours, and `startUpgrade`
refuses a facility that is already building, so past the first week the colony is rate-limited by
**slots** rather than by income. Metal is what is left over when the thing you want to buy is
waiting on a crystal cost or a busy row.

And it **diverges** rather than sitting at a constant offset — 571, 1,074, 6,486, 56,298, 244,037 at
days 1, 2, 3, 7 and 14. Any sink that fixes it has to compound too.

`FleetBalance` already names the sink, in `SurveyBalance`'s words: *"metal is the resource with
nothing to buy, and this is the thing to buy with it."* The hull curve compounds at +50% from 80
metal, so the twentieth skiff is ~175,000 metal — a sink the right size and the right shape. **It is
not purchasable from `core` yet**: `shipCost` exists, and nothing spends against it.

So the open question this addendum hands back is which of two things the 56k means, because the two
have opposite costs:

- **the sink is missing** — no balance change at all, and the fleet slice closes it;
- **the whole arc is too fast** — a real re-scaling, which would run straight into rounds 13 and 16,
  where the opening was deliberately made quick and Davide asked for *"adrenaline"* in the first
  session.

Davide's call. Nothing here has been changed on the strength of it.

#### And then the horizon was measured, which changed the answer

The addendum above stopped at a fortnight and concluded *"the sink is missing"*. That was the right
reading of the data it had and the wrong answer to Davide's question, and the correction is worth
recording rather than quietly overwriting, because the mistake is a general one: **a diagnosis drawn
from the window you happen to have measured is a diagnosis about the window.**

`[horizon]` runs the same fixed player out to ninety days — the unit the question was actually asked
in.

| day | levels | mine | income/h | metal | placed |
|---|---|---|---|---|---|
| 7 | 59 | 14 | 4,856 | 56,298 | 74% |
| 14 | 80 | 18 | 12,785 | 244,037 | 76% |
| 30 | 106 | 23 | 54,615 | 2,090,612 | 72% |
| 60 | 131 | 27 | 224,908 | 9,081,491 | 79% |
| 90 | 149 | 30 | 574,224 | 7,210,828 | **94%** |

Two things fall out, and the second overrules the first.

**The sink problem does largely fix itself.** Placement climbs from 74% to 94% as the Nanite Factory
and the deeper levels arrive — so the fortnight's 76% was a reading of an early game with too few
rows to buy, not a permanent defect. A fleet a player could actually purchase would close most of
what is left.

**And it does not matter, because the scale is the finding.** At ninety days the colony places 94% of
its metal and still banks **7.2 million** of it, on an income of 574,224 priced units an hour.
Davide's expectation for three to four months was 56,000 — which this game reaches on **day 7**. That
is not a factor of two or three that a sink absorbs; the three-month figure is roughly **130x** what
he expected to see, and the day-7 figure is already the whole of it.

So the earlier framing — *"either the sink is missing or the arc is too fast"* — was a false choice
presented as an open one. Both are true, they are not comparable in size, and only the second is
worth a round. The arithmetic underneath it is the one relationship this file has never moved: cost
compounds at **+50%** a level and production at **+25%**, so income per level outruns nothing and the
*number of levels* is what the clock buys — 16 further mine levels between day 7 and day 90 multiply
income 118x. Stretching the arc means widening that gap, and round 11 tied build duration to the
**root of cost** precisely because of it, so nothing here can be moved without re-deriving that.

**Still Davide's call, and now with the real number attached rather than a fortnight's.** Nothing has
been changed.

#### Two things the horizon walked into: an inert building and a ceiling

Davide, reading the section above: *"What's the Nanite for?"*

**Nothing, yet — and the benchmark had just started buying it.** Every reference to
`buildings.naniteFactory` in `core` is storage (`Buildings`, `levelOf`, `withLevel`, `initial`), the
Robotics 10 gate in `startUpgrade`, the cost table, and an explicit **zero** in both energy
functions. No curve reads its level: `PlaceholderBalance.upgradeDuration` and
`ResearchBalance.researchDuration` each divide by the Robotics Factory alone. It costs 20,000 metal /
10,000 crystal / 4,000 deuterium at level 1, compounds at +50%, and buys nothing.

The addendum above had *added* it to the fixed player's plan, on the argument that a player holding
two hundred thousand metal buys the thing costing twenty thousand of it. That was wrong in both
directions at once, and the measurement says by how much:

| day 14 | levels | income/h | metal |
|---|---|---|---|
| buying the Nanite Factory | 80 | 12,785 | 244,037 |
| not buying it | 78 | **15,490** | 208,970 |

Four levels of a no-op cost the colony **2,705 priced units an hour** of income it would otherwise
have had, *and* left 35,067 more metal in the bank — so it flattered the very pile the page exists to
show, by spending metal on a row that buys nothing. At ninety days the distortion stops being
marginal: a tenth Nanite level is 1,999,032 priced units. The fixed player now buys what the game
actually sells, and the comment in `OPENING_PLAN` says to put it back when the building does
something.

**In OGame the Nanite Factory halves build time, multiplicatively with the Robotics Factory.** That
is presumably the intent — `NANITE_ROBOTICS_REQUIREMENT` lives in `PlaceholderBalance` next to the
other undecided numbers — but it is not written anywhere as a decision, so it is not implemented here
and will not be invented. Two ways out, both Davide's: give it an effect, or take it out of the tree
until the slice that needs it.

**And the quarter ends against a wall.** With the no-op purchase removed, the day-90 metal reads
exactly 10,000,000 — which is `PlaceholderBalance.STORAGE_CAPACITY`. The colony first touches the cap
at **hour 1,113, day 46**, and spends **386 of the quarter's 2,161 hours** resting on it.

That matters for reading every other row: once a stock is against the cap, `advance` stops accruing,
so income past it is not banked, not spent and not earned. Every "placed" percentage in the horizon
therefore *understates* the surplus, and the day-90 figure understates it most. `STORAGE_CAPACITY`'s
own comment already calls itself a placeholder and names the open question — *"the rule that raises
it (storage building? mine-level-scaled?)"*. The horizon says that question now has a date on it: day
46 of a colony's life, under the curves as they stand.

#### And the instrument's own diff was broken three ways, found by using it

Merged, then read back. The cap rows added above landed in **both** `pressure()` and `horizon()` —
a Python `str.replace` with no count, against an anchor that existed identically at the end of two
functions — so `[pressure]` carried *"hours resting on the metal storage cap: 0 of 337, first
reached: not within the run"*. Trivially true over a fortnight when the cap is first touched on day
46, and misleading in the one direction that matters: a reader of that section would conclude the cap
is a non-issue.

Worth stating plainly, because it is the point of the whole round: **the golden diff did show it.**
138 lines became 160 and those two rows were in the diff, reviewed and pushed. The instrument worked
and its reader did not.

Fixing it exposed that the failure message itself could not be reviewed, in three compounding ways —
each found only by breaking a balance number and reading what a reviewer would see:

1. **Positional.** It walked both pages by index, so removing two rows shifted everything below and
   it reported *105 of 160 lines differ*. Correct for a value moving inside a row that stayed put,
   useless for anything structural. Now keyed by row.
2. **Keyed by label alone, and the label was empty for every indented row** — the split took the
   first run of two spaces anywhere, which is the row's own indent. `associateBy` collapsed them all
   into one entry and the diff showed **11 of 48** changed rows.
3. **Keyed without the section**, so `day 7` and `day 14` — rows in both `[progression]` and
   `[horizon]`, with different columns — overwrote each other and one was thrown away.

Two and three are the same failure and it is the worst one a diff has: **it drops rows silently, and
a short diff reads as a small change rather than as a broken instrument.** The key is now
`section ▸ label`, and the report cap went 40 → 120 because the page is ordered by horizon, so the
truncated tail was exactly `[horizon]` — the late-game rows a re-scaling round exists to move.

What it reads like now, against the gate moved back to Robotics 4:

```
opening ▸ first adaptation level finished: hour 9 (day 0) -> hour 33 (day 1)
progression ▸ day 14: 78  15490  208970  14381  7035 -> 76  13665  254315  327  10507
horizon ▸ day 60: 124  28  286694  8311953  83.00% -> 124  27  267078  8372997  83.00%
horizon ▸ first reached: hour 1113 (day 46) -> hour 1116 (day 46)
```

---

## Round 20 — 0.5.2, the Nanite Factory gets a job and the late game gets a wait (2026-08-11)

Davide, reading round 19's page: *"What's the Nanite for?"* — and then, once the answer came back
*nothing*: *"I'd expect late game upgrade to be extremely slow, and expensive Nanite upgrades to make
them reasonable. I still think a late game upgrade could take various hours, even with Nanite.
Implement it as such, considering that Nanite gets unlocked a bit late, so let's not impact build
times before then. It's reasonable for build times to be long only when the user has many things to
do: manage ships, travels, and co, not when it has only a few things."*

Three sentences and each one is a constraint rather than a preference:

| The sentence | What it fixes |
|---|---|
| *"long only when the user has many things to do"* | nothing below the ramp may move |
| *"Nanite gets unlocked a bit late, so let's not impact build times before then"* | the ramp starts where the Nanite does |
| *"could take various hours, even with Nanite"* | the answer is partial, on purpose |

### The shape

**The ramp.** Above `LATE_GAME_FIRST_LEVEL` the wait compounds at **+25% a level**. That is a
deliberate break of round 11's identity — a build takes about as long as earning it does — which was
right for the mid-game and is precisely what is being overruled. `BalanceCurveTest`'s assertion of
that identity is now *scoped* to end at the ramp rather than deleted, and a new test owns the other
side, so neither half can drift without one of them failing.

**The threshold is measured, not picked**, the same way `FULL_PRICE_LEVEL = 9` was: a colony's mines
stand at **level 17** when Robotics reaches 10 and the Nanite Factory becomes buildable. So the ramp
opens at 18 — one level *after* the answer to it exists. `[opening]` prints that measurement, so if
the opening ever speeds up or slows down, the page says the constant is wrong rather than hiding it.

**The Nanite takes two thirds off per level** — `openingSpeedUp`'s own rational, deliberately. The
game now has two places where a building buys back time and they are the two ends of it; one shape
for both means a reader who has understood one has understood the other. Multiplicative rather than
another term in the Robotics divisor, because the divisor is linear and the thing it fights is not:
an additive Nanite worth three Robotics levels each buys a fifth off its first level and a twentieth
off its fifth, which is a building that stops mattering exactly as the player finishes paying for it.

### What it does, from `[late game]`

Metal Mine, at Robotics 15:

| level | nanite 0 | nanite 2 | nanite 4 | nanite 6 | 0 → 6 | vs income |
|---|---|---|---|---|---|---|
| 16 | 44m | 19m | 8m | 3m | 14.66× | 1.03× |
| 18 | 1h 07m | 29m | 13m | 5m | 13.40× | 1.08× |
| 20 | 2h 37m | 1h 10m | 31m | 13m | 12.07× | 1.76× |
| 25 | 22h 08m | 9h 50m | 4h 22m | 1h 56m | 11.44× | 5.96× |
| 30 | **186h 25m** | 82h 51m | 36h 49m | **16h 21m** | 11.40× | 20.17× |

Read the last column against the first two rows: at 16 and 18 a build still takes about as long as
earning it does, which is round 11 untouched. At 30 it takes twenty times as long, which is the
change.

### And nothing before it moved, which is the part that was checked hardest

Every band in `OpeningBalanceTest` and `CheckInBalanceTest` passed **unedited**, and the benchmark's
`[opening]` and `[session]` sections came out byte-identical — no row in either appears in the diff.
The first build still lands at minute 2, the research tab still opens at hour 5, the ladders at hour
9, and days 1, 2, 3 and 7 of `[progression]` are unchanged to the unit.

Day 14 moves, and only because the Nanite is worth buying now: the colony spends 4 levels on it and
carries 80 building levels instead of 78.

### Mutation

| Mutation | Caught by |
|---|---|
| Nanite back to nothing (2/3 → 3/3) | `BalanceCurveTest`, benchmark |
| Nanite made overwhelming (2/3 → 1/6) | `BalanceCurveTest`, benchmark |
| Late ramp flattened (5/4 → 4/4) | `BalanceCurveTest`, benchmark |
| **Ramp starts at level 4, inside the opening** | `BalanceCurveTest`, benchmark |
| Ramp starts at level 32 | `BalanceCurveTest`, benchmark |

The fourth is the one worth reading twice. It is caught by
`the opening builds faster than it earns and closes the gap by the landmark` — a test written in
round 16 for a different reason — because a ramp reaching down into levels 5–8 stops the opening
outrunning its own economy. *"Do not impact build times before then"* turns out to already have a
guard, written a round before anybody asked for it.

### What this round is not

**It does not touch the arc.** `[horizon]` still puts a colony at mine 30 and 10,000,000 metal by day
90, and Davide's *"56k in one week seems extreme"* is still open and still his. The Nanite question
was downstream of it and answerable on its own; the arc is not, because it runs straight into rounds
13 and 16.

### One process note, cheap to reproduce and expensive to learn twice

The mutation harness ends each case with `git checkout -- <file>`, and the implementation under test
was **uncommitted**. So the first two mutations ran correctly and the third reverted the entire
change — silently, reported only as *"PATTERN MISSING"* on the cases after it. Nothing was lost that
was not re-typed, but the rule is now: **commit, then mutate.** A harness that restores from git is
a harness that assumes git holds the thing you are testing.

---

## Round 21 — 0.7.2, exploring pays instead of costing (2026-08-12)

Davide, having played 0.7.1: *"I feel like exploring other planets is way too little rewarding. I
grinded to upgrade Thermal, to travel 3h, and 14 cristals lol… Also I would expect that travel
towards far planes to be way more time consuming, and require upgraded fleets to get there faster."*

And, asked how far the ceiling should move: *"Just adjust the rate, but I don't think a 20% is
enough! Also I would expect that more challenging planets are even more rewarding. We need to push
users towards planets explorations, otherwise it is pointless, now it not rewarding AT ALL, like 1
to 10 → minus 50."*

The design is [`exploration-rewards-sheet.md`](exploration-rewards-sheet.md); this round is the half
of it that shipped. **The drive technology and the Shipyard did not** — see the end.

### The finding that reframed the complaint

**`buildShips` does not exist, so a player owns one skiff and can never own two.**
`FleetBalance.shipCost` has no production caller anywhere — only `FleetBalanceTest`,
`BalanceBenchmark`, and the sim, which buys with a raw `state.copy` and says why at `Main.kt:1537`.
`GameSave.kt:155` states it outright.

That matters here because **round 17 sized `EXTRACTION_PER_HOUR = 20` against a guardrail no shipped
player can trip.** Its binding row was *"a fleet-first player must not out-produce their own
colony"* — 71% of colony crystal at rate 20, measured with a bot owning six to nine hulls. There is
no purchase, so there is no fleet-first player, and the real figure for a real player is that column
divided by six to nine. The number was measured honestly and it was guarding a door nobody can
reach. That is what unlocked tripling it rather than nudging it.

### What moved

| | 0.7.1 | 0.7.2 |
|---|---|---|
| `EXTRACTION_PER_HOUR` | 20 | **60** |
| danger, per point | **−10%** of the hold | **+35%** of the hold |
| `FRONTIER_PERCENT` | ratified 0.3.0, read by nothing | **deleted** |

**Danger's sign is the load-bearing change and the rate is not.** The rate is a constant, and the
benchmark's own decay table is why a constant cannot fix this: a 6h run brought 127 metal at hour 0
and *exactly* 127 at hour 168. Tripling it moves that curve up and does not bend it. What bends it
is that distance and hostility now multiply.

**`FRONTIER_PERCENT` is deleted rather than finally wired in.** Its four numbers are the break-even
points that cancel a −10%-per-point penalty, and there is no penalty left to cancel; keeping them
would pay for distance twice, since `danger` already contains `distanceBand`. One mechanism, already
computed, already on screen.

**`GalaxyBalance.HAZARD_PENALTY` is deliberately untouched.** A hazard is still −0.05 on
`yieldScore`, so it can still drop a band-passing world from `Settleable` to `Barren`. **A hazardous
world is now worse to live on and better to raid**, which is the fleet/settlement split restated in
numbers rather than contradicted.

### What it does — the benchmark's new `[frontier]` section

The 24h rung, one richness throughout, so the only things moving are the flight the window loses and
the danger it gains. **A row under 1.00 is distance still winning.**

| target | band | round trip | metal | vs the next slot |
|---|---|---|---|---|
| the next slot | 0 | 26m | 1,972 | 1.00x |
| 60 systems out | 1 | 1h 38m | 2,357 | **1.19x** |
| across your own galaxy | 2 | 3h 28m | 2,610 | **1.32x** |
| the next galaxy | 3 | 9h 20m | 2,182 | **1.10x** |

Before this round every one of those rows was below 1.00 by construction, and the sim measured the
consequence exactly: **56 of 56 dispatches to band 0**, to two worlds, both in the home system,
while 276 of 283 surveyed worlds sat further out.

The section is new and it is the point: this is the one reading that says whether the map is worth
opening, and nothing printed it before.

### What did not move, and it is most of the game

**Six rows of the benchmark differ and two of them are the fleet's.** The other four are the new
`[frontier]` block. `[opening]`, `[session]`, `[progression]`, `[research]`, `[galaxy]`, `[horizon]`
and `[late game]` are byte-identical — no landmark hour moved, no check-in changed, no build time
changed. A fleet change that touched the colony's curves would be a bug, and the golden is what says
it did not.

### The two things this round did not fix

1. **The Shipyard.** One skiff is still all there is, so the rate is tuned for a fleet of one and
   **must be re-swept the day `buildShips` lands.** Sheet §9 Slice A.
2. **Travel time and the drive technology.** Davide's *"way more time consuming… upgraded fleets to
   get there faster"* is Slice C and is untouched — a galaxy hop is still 9h 20m round trip at a
   speed nothing can improve. The frontier's 1.10x above is what it is worth *without* the
   technology that is supposed to make it worth crossing; the sheet's §4 puts it near 1.8x at drive
   5, and that is the round that has to follow this one.

### What to watch

**Whether the target actually changes.** The reading that decides this round is not the size of the
haul, it is the **band spread** — the sim's `band 0 x56`. If a post-change bot still sends every
dispatch to the home system, the multipliers are too small and the rate did the work, which is the
outcome this round was specifically trying not to buy.

---


## Round 22 — 0.8.0, the Shipyard lands and the guardrail is spent rather than met (2026-08-12)

The slice `exploration-rewards-sheet.md` §9 called **Slice A** and `fleet-sheet.md` §10 called
**slice 3**: `buildShips`, the Shipyard tab and the Fleets tab. It moves no balance number. What it
moves is a **constraint** — and that is worth a round of its own, because a guardrail that is
knowingly spent is a different thing from one that was never tested.

### The sweep round 21 was told to wait for

Round 21 tripled `EXTRACTION_PER_HOUR` to 60 on an argument with an expiry date written into it:
*"round 17 sized 20 against a guardrail no shipped player can trip"* — the guardrail being **a
fleet-first player must not out-produce their own colony**, measured against a bot owning six to
nine hulls when a real player owned one and could never own two. The sheet said so in as many words:
*"this must be re-swept the day `buildShips` lands."* It landed. It was re-swept.

`printFleetReport`'s purchase-order bracket, crystal-seeking, 48 hours, at the shipped hull base.
**A cell over 100% is a fleet that has become the economy.**

| rate | hulls from what is left | **hulls first** |
|---|---|---|
| 20 | 31.4% | **89.3%** |
| 30 | 47.2% | **134.0%** |
| 40 | 63.0% | **178.7%** |
| **60** *(shipped)* | 94.5% | **268.1%** |

Round 17's criterion is not met at 60 and is not close to met. **20 would still be the highest rate
that satisfies it**, and 30 is already over.

### Davide's ruling — the criterion moves, not the number

The build took the rate back to 20 on the strength of that table and was overruled on sight:
*"Why did you revert the rate? Bring it back."*

**So the reading stands and the constraint does not.** Recorded that way round, deliberately, because
the two are separable and only one of them was decided: the 268% is a measurement and it has not been
argued with; what has been rejected is round 17's rule that it violates. From here, *"the fleet must
never be the economy"* is no longer the thing that sizes this number, and a future round proposing to
lower the rate has to argue against Davide's bar rather than reinstate round 17's by default.

**The argument the build made and lost, kept because the next round will be tempted by it.** Hull
count is a growth term the fleet had never had, and this slice is what adds it: at 20 the same
four-a-day player owns six hulls at 48 hours and sixteen at a fortnight, so a dispatch would bring
home six to sixteen times the *"14 cristals"* without the constant moving. The build read that as the
rate raise having been compensation for a missing multiplier. Davide's call is that the multiplier and
the rate are both wanted. **The way to test which reading is right is a device session, not another
sweep** — see "what to watch".

### What it does to the game, at 60 with hulls on sale

| Reading, 48h, four a day | no fleet | with fleet |
|---|---|---|
| Hulls owned at 48h | 1 | **7** |
| Dispatches | 0 | **8** |
| Fleet duty cycle | — | **74.7%** |
| Fleet metal delivered | 0 | **12,662** |
| Fleet metal as a share of colony metal income | — | **91.8%** |
| Building levels at 48h | 32 | **33** |

**Levels went up rather than down**, which is the guardrail this slice was most likely to break: the
fleet is bought out of metal the colony had nowhere else to put, so it costs the build queue nothing.
The share, though, is the story — a fleet delivering 92% of what the mines do, at 48 hours, before
any frontier target is reachable.

### The danger inversion is doing its own work, and it is separable from the rate

The reading round 21 said would decide it, at both rates:

| | 0.7.1 | 0.7.2 (rate 60, no Shipyard) | **0.8.0 (rate 60, Shipyard)** |
|---|---|---|---|
| dispatches to band 0, of 56 | **56** | 42 | **42** |
| dispatches past the home system | **0** | 14 | **14** |
| distinct targets over a fortnight | 2 | 4 | **4** |

A quarter of dispatches leave the home system at either rate. **The multipliers moved the target and
the rate never did**, which is round 21's own thesis surviving contact with a fleet that can grow.

### The benchmark

Unmoved by this round: the rate did not change, so the six fleet rows read exactly what 0.7.2 signed
off. That is the honest outcome of a slice that adds a verb rather than a number, and it is worth
saying out loud — `[fleet]` and `[frontier]` are per-hull readings, and **the benchmark's fixed player
still owns one hull**, so nothing this slice built is visible there at all. The instrument that sees
it is `printFleetReport`, not the golden.

### Two instrument repairs, and the first one is embarrassing

1. **`:sim:run` had been dead since 0.7.2 and nobody noticed.** The harness carries a replica of
   `FleetBalance.cargo` and checks it against `core` on every dispatch; round 21 inverted the danger
   term in `core` and left the replica subtracting, so the `check` fired the first time the bot chose
   any target with a hazard or outside the home system — which is to say, immediately — and the whole
   report died on it. The discipline worked exactly as designed (a loud failure rather than a quiet
   disagreement) and then nobody ran it. **A balance round that ships without running the harness is
   how that happens.**
2. **Every sweep in the file was missing the shipped rate.** Round 17 swept {10, 20, 30, 40} and
   round 21 shipped 60 without widening the candidate lists, so four separate tables printed a grid
   that did not contain the constant the game was running on — including the bracket above, which is
   the one table that could have caught this. They are one named list now, and the list `check`s that
   it contains `FleetBalance.EXTRACTION_PER_HOUR`.

### What to watch

- **Whether the fleet-first player is a real player.** The 268% assumes somebody buys hulls before
  the buildings at *every* check-in. If nobody plays that way the honest column is 94.5%, which is a
  fleet matching the colony rather than tripling it. This is the single reading that decides whether
  the ruling above was right, and only a device session can produce it.
- **Whether the mines start feeling optional.** That is the shape the failure takes if it takes one —
  not a number in a table, a player who stops tapping the Colony tab. `fleet-sheet.md`'s own words:
  *"if a round finds the mines feeling optional, `EXTRACTION_PER_HOUR` is too high."*
- **Whether six or seven hulls is the natural fleet.** §4 predicted *"three to four at the opening and
  six or seven at depth"*; the bot reaches seven at 48 hours and seventeen at a fortnight, which is
  more than the sheet expected. If a real player ends up with seventeen skiffs and nothing to do with
  them, **the hull curve is the dial**, not the rate.
- **The drive technology is still not built** (sheet §9 Slice C), so *"travel towards far planets to
  be way more time consuming, and require upgraded fleets to get there faster"* remains unanswered in
  both halves.
