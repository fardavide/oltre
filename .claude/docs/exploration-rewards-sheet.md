# Exploration rewards decision sheet — 0.8

Written by the build, 2026-08-12, on Davide's report after playing 0.7.1:

> *"I feel like exploring other planets is way too little rewarding. I grinded to upgrade Thermal, to
> travel 3h, and 14 cristals lol… Also I would expect that travel towards far planes to be way more
> time consuming, and require upgraded fleets to get there faster."*

> *"We need to push users towards planets explorations, otherwise it is pointless, now it not
> rewarding AT ALL, like 1 to 10 → minus 50."*

**Every line here is his to overrule.** Same shape as the 0.1 research sheet, the 0.2 galaxy sheet,
the 0.3 adaptation sheet and the 0.4 fleet sheet: answer the question, name what was rejected, say
what it should feel like so the next round can tell.

This sheet **revises `fleet-sheet.md` §3.5 and §4** and closes fleet-arc **slice 2**, which was
supposed to deliver the frontier and has been outstanding since 0.3.0. It changes no `GalaxyBalance`
number, no `GalaxyDistributionTest` band, no `verdictFor` case and no generation constant.

---

## Davide's calls, 2026-08-12 — binding on this sheet

| Question | His answer |
|---|---|
| How far does the fleet's ceiling move? | *"Just adjust the rate, but I don't think a 20% is enough!"* |
| Should hostile worlds pay more? | *"I would expect that more challenging planets are even more rewarding."* |
| What is the goal? | *"We need to push users towards planets explorations."* |
| Drive technology, ship upgrades, or both? | **Asked back** — *"Do we need a drive tech, or it's enough to allow to upgrade ships? Maybe both?"* Answered in §3; it is still his call. |
| What was the Thermal complaint? | *"I had to upgrade stuff for two days, to update Termal to 1, so I can explore a planet…. to get 14 mineral"* |

---

## The one-sentence version

**Distance and hostility stop being a tax and become the payout**, and a drive technology is what
turns the frontier from a place you cannot afford to reach into the best thing in the game — so
"where do I send it" replaces "which of my two home-system rocks" as the question a dispatch asks.

---

## 0. What is broken, measured

Five readings, all from the repository's own committed instruments. None of them is a guess.

### You own one skiff, and there is no way to get a second — this is the finding that reframes the rest

**`buildShips` does not exist.** `FleetBalance.shipCost` has **no production caller at all** — only
`FleetBalanceTest`, `BalanceBenchmark` and the sim, which buys hulls with a raw `state.copy` and says
why at `Main.kt:1537`: *"the purchase is a `state.copy` rather than a verb, because `buildShips` is
slice 3."* `GameSave.kt:155` states it outright — *"in this slice there is no way to buy a hull."*

So the shipped game grants exactly one skiff at genesis (`GameState.kt:98`) and offers no way to add
to it. The hull curve, the Shipyard tab and the whole *"the natural fleet is three to four skiffs at
the opening"* argument describe a game that is not built.

**Two consequences, and both are large:**

1. **The one lever that could have scaled the fleet is not in the player's hands.** Every other term
   is bounded; hull count was the unbounded one, and it is unreachable. Davide's *"14 crystal"* is
   one skiff because one skiff is all there is.
2. **Round 17's sweep sized `EXTRACTION_PER_HOUR = 20` against a bot owning six to nine hulls, and
   the constraint that decided it cannot bind in the shipped game.** The binding row was *"a
   fleet-first player must not out-produce their own colony"* — 71% of colony crystal at rate 20 when
   hulls are bought first. **There is no fleet-first player**, because there is no purchase. The real
   figure for a real player is that same column divided by six to nine. **The number was measured
   honestly and it is guarding against something that cannot happen yet**, which is the strongest
   available argument that Davide's *"20% is not enough"* is right.

### The hold has no growth term at all

`:sim:run`'s own table — a single skiff, the 6h rung, best surveyed neighbour of the moment, against
the colony's income at that same moment:

| At | colony metal/h | one skiff, 6h, as metal | as crystal | = hours of **crystal** income |
|---|---|---|---|---|
| hour 0 | 90 | 127 | 90 | **2.5h** |
| hour 24 | 235 | 127 | 90 | 0.9h |
| hour 48 | 459 | 127 | 90 | 0.4h |
| hour 168 | 2,198 | 127 | 90 | **0.1h** |

**The middle columns never move.** 127 metal at hour 0 and 127 metal at hour 168. Every term in
`FleetBalance.cargo` is a constant or is bounded: the rate is a constant, station time is capped by
the 24h rung, crystal richness clamps at 1.6, hull count is linear against a cost that compounds
×1.5, and danger only ever subtracts. The colony compounds; the fleet does not. **That is the whole
of "not rewarding AT ALL"**, and it is structural rather than a number being low.

### Distance pays exactly nothing

`FleetBalance.FRONTIER_PERCENT` was ratified at 0.3.0 as `[100, 115, 155, 230]` and is **read by
nothing** — `FleetBalance.kt:160`, and `cargo` never references it. So a far world costs flight time
and −10% per danger point and returns precisely what the rock next door returns.

The sim's bot behaves exactly as that arithmetic dictates:

- **56 of 56 dispatches went to band 0**, to two worlds, both in the home system.
- Of 283 surveyed worlds, **band 0: 7 · band 1: 0 · band 2: 66 · band 3: 210**.

276 of 283 surveyed worlds were never worth sending a ship to. The map is a backdrop, which is the
thing `fleet-sheet.md` §3.5 predicted in as many words and deferred to the slice that never ran.

### Nothing anywhere scales fleet speed

`Technology` is `PHOTOVOLTAICS, EXTRACTION, ENRICHMENT` (`Research.kt:16-20`). There is no drive, no
engine, no hull level, and `FleetBalance.flight` takes no research parameter. **The mines have a
technology that multiplies their rate and the fleet has nothing at all** — which is the same
asymmetry as the first finding, seen from the research branch.

### Thermal never gated the dispatch — and that correction is the sharpest thing in this sheet

Davide upgraded Thermal to reach a world. **He did not need to.** Neither the rule nor the screen
consults a verdict:

- `StartRun.kt:56-66` checks hulls, home, deuterium, holder, existence, survey and window. There is
  **no verdict check** — and `StartRunResult.NotAValidTarget`'s own comment says so deliberately:
  *"Note what is deliberately not here: failing your tolerance bands."*
- `DispatchUiState.kt:119-201` mirrors it exactly. A `Blocked` world raises a full `Offer`.

So a `Blocked` world has always offered a dispatch, and two days of progression bought a badge on a
world that cannot be settled — because colonisation is slice #10 and is not built. Thermal's designed
prize is **deuterium richness**, and `cargo` hard-refuses deuterium (`FleetBalance.kt:226`).

**Two separate defects live in that one sentence**, and they need different fixes:

1. **A legibility failure.** If the app had said *"you can already send a ship here"*, the two days
   would not have been spent. §2.5 is the cheapest item in this sheet and probably the highest
   value-per-line.
2. **A payoff hole.** The adaptation ladders have no fleet-facing prize whatsoever before slice #10.
   §2.4 proposes one; it is Davide's call whether he wants it.

---

## 1. The principle this sheet turns on

**Today the game charges you for going far and pays you for staying home.** Flight time is
subtracted from the window, danger subtracts 10% a point, and nothing anywhere adds. Every one of
Davide's four sentences is the same complaint seen from a different angle, and the fix is one
inversion:

> **Distance costs time. Hostility pays cargo. Hulls are what combat will take.**

Three clean channels, one thing each — against today's arrangement, where distance costs time *and*
cargo, hostility costs cargo, and nothing pays.

---

## 2. The proposal

### 2.1 Danger stops subtracting and starts multiplying — the load-bearing change

```kotlin
// today
cargo = … × (100 − 10 × danger) / 100        // 1.00 → 0.50 at danger 5

// proposed
cargo = … × (100 + DANGER_BONUS_PERCENT × danger) / 100   // 1.00 → 2.75 at danger 5, at 35
```

`danger` is unchanged: `world.traits.hazards.size + distanceBand(from, world.at)`, 0…5. Only its
sign moves.

**This is Davide's *"more challenging planets are even more rewarding"* implemented in the one
number that already means "how challenging is this".** It needs no new generation, no new roll, no
new save field and no new noun — `danger` is already computed, already summed, and already printed
on the dispatch sheet.

**It also deletes `FRONTIER_PERCENT` rather than wiring it.** `danger` already contains
`distanceBand`, so a frontier multiplier *and* a danger bonus would pay for distance twice. One
mechanism, already on screen, is better than two that must be kept consistent — and it resolves the
double-count by subtraction. Worth stating plainly because it reverses a ratified decision: **the
0.3.0 break-even constants were derived against a danger *penalty*, and flipping the sign
invalidates the arithmetic that produced them.** They cannot be kept as-is regardless; the choice is
re-derive them or fold them in, and this folds them in.

**What is lost, honestly:** the word "danger" now names something purely good until combat lands, so
it is a promise the game does not yet keep. `fleet-sheet.md` already committed to that shape —
*"when `resolve(a, b, seed)` exists, danger stops taking cargo and starts taking hulls"* — and this
brings the reward half forward while the cost half still waits on slice #8. A rename is available
and is a content call (§8).

**`GalaxyBalance.HAZARD_PENALTY` is deliberately not touched, and the asymmetry is the point.** A
hazard stays −0.05 on `yieldScore` (`GalaxyBalance.kt:177`), which is what can drop a
band-passing world from `Settleable` to `Barren` — and it becomes +35% to a hold. So **a hazardous
world is worse to live on and better to raid**, which is the fleet/settlement split of
`fleet-sheet.md` §3 restated in numbers rather than contradicted. Moving the penalty would move a
committed golden and four `GalaxyDistributionTest` rows for no gain this sheet needs.

### 2.2 Travel gets longer, and a drive technology buys it back

Davide: *"travel towards far planets to be way more time consuming, and require upgraded fleets to
get there faster."* Both halves, and they are one change:

```kotlin
// today
flight = BASE_FLIGHT_MINUTES + units / 10

// proposed
flight = BASE_FLIGHT_MINUTES + units / unitsPerMinute(drive)
unitsPerMinute(drive) = UNITS_PER_MINUTE_BASE × (1 + drive)      // 5 at drive 0, 10 at 1, 30 at 5
```

At drive 0 a distant world is **half today's speed**; at drive 1 it is exactly today's; past that it
accelerates. `BASE_FLIGHT_MINUTES` is untouched, so the home system does not move at all — the drive
is worthless next door and transformative at the frontier, which is precisely the behaviour the
sheet wants to reward.

Skiff round trips, proposed:

| target | units | drive 0 | drive 1 *(=today)* | drive 3 | drive 5 |
|---|---|---|---|---|---|
| adjacent slot, own system | 5 | 22m | 20m | 20m | 20m |
| the next system | 100 | 1h 00m | 40m | 30m | 26m |
| a hundred systems out | 595 | 4h 18m | 2h 18m | 1h 18m | 58m |
| across your own galaxy | 1,340 | **9h 16m** | 4h 48m | 2h 34m | 1h 48m |
| the next galaxy | 2,700 | **18h 20m** | 9h 20m | 4h 50m | 3h 20m |

**The narrowing ladder does the teaching, and the mechanism already exists.** `windowsFor` offers
only rungs that leave `MINIMUM_STATION` after the round trip, so at drive 0 another galaxy is a
24h-rung-only proposition and across-your-own-galaxy loses everything below 12h. A player who buys a
drive level watches rungs *reappear*. No copy required.

### 2.3 The drive also multiplies the hold — which is the answer to "drive tech or ship upgrades?"

This is the one place the sheet adds a second effect to a single row, and it is deliberate. **The
same technology raises the hold**, on the game's own compounding shape:

```kotlin
hold = hulls × EXTRACTION_PER_HOUR × stationHours × holdMultiplier(drive)
holdMultiplier(drive) = compound(1.0, drive, 5, 4)     // ×1.25 a level: ×1.95 at 3, ×3.05 at 5
```

**Why one technology and not two rows, and not per-hull upgrades** — §3 argues it in full. Short
version: it is the growth term the fleet has never had, it costs one enum constant and no save
state, and per-hull levels would turn `Ships` from a count into a count-per-level and take the
manifest, the pool arithmetic, the save schema and the dispatch sheet with it.

### 2.4 A fleet-facing payoff for the adaptation ladders — DAVIDE'S CALL, and it is optional

The ladders pay nothing a ship can cash before slice #10. The cheapest honest fix reuses the number
this sheet has already inverted: **let the tolerance shortfall count into `danger`.**

```kotlin
danger = hazards.size + distanceBand + bandsFailed(world, research.adaptationLevels())   // 0…8
```

A world outside all three of your tolerance bands is *more challenging*, pays more, and each ladder
level you buy makes it pay less — so **the ladders and the fleet trade against each other instead of
ignoring each other**, and Thermal 1 changes a number a player can see on the dispatch sheet the same
afternoon.

**What it costs, and it is real.** `fleet-sheet.md` §3(d) split these two words on purpose —
*"hostility: can you settle. danger: what the trip costs you"* — and this re-couples them. It also
inverts the ladder's incentive in an odd direction: climbing Thermal makes a hot world pay *less*,
so a fleet-first player is rewarded for **not** researching. That is a real defect and it is why this
is presented as optional rather than recommended. The alternative that avoids it — hostility raises
the payout and the ladder is what lets you *reach* it at all — is a gate, and gating gathering behind
habitability is the thing §3 rejected outright as *"habitability-gated gathering **is**
colonisation."*

**Recommendation: take §2.5 instead, and leave the ladders to slice #10.** §2.5 fixes the actual
complaint (two days spent on something that was never required) at a fraction of the cost and
without touching a settled split.

### 2.5 The screen says a Blocked world can already be gathered from — the cheapest item here

No rule changes. The world row and the dispatch sheet gain one reading that says the trip is
available regardless of verdict. Treatment 1b already leads `Blocked` and `Barren` rows with metal
and crystal richness *because their verdict is not an offer* — the design's intent is right and the
sentence that closes it is missing.

**This is the item that would have saved two days of Davide's play**, and it is a Claude Design
round plus a string, not a mechanic.

---

## 3. Drive technology, ship upgrades, or both — the answer to the question asked back

**Recommendation: one drive technology, doing both jobs. Ship upgrades deferred to the Shipyard
slice, and probably never needed.**

| | What it buys | What it costs |
|---|---|---|
| **A drive technology** *(recommended)* | One `Technology` constant, one balance function, one research row. Speed **and** hold, so the fleet finally has a compounding term. Empire-wide, so it needs no per-hull state. | The research branch goes from three rows to four. `Research.kt`'s own argument *"every time the slot frees, the question is which of three"* becomes four, which strengthens rather than weakens it. |
| **Per-hull upgrades** | A Shipyard that sells something other than more hulls, and a reason to keep old hulls. | `Ships` stops being `Map<ShipType, Int>` and becomes level-aware — which takes `covers`, `minus`, `plus`, the manifest picker, the idle pool, `FleetRun`, the save schema and every dispatch string with it. A large save-format change for an effect the technology already delivers. |
| **Both** | Nothing the first does not, until there is a reason for two hulls to differ. | Two systems teaching the same lesson, in a 5–10 minute check-in on a 393dp screen. `fleet-sheet.md` §10 spends one new noun per slice for exactly this reason. |

**Where upgrades genuinely earn their place is slice #8**, when combat gives two hulls of the same
type a reason to differ. Until `resolve(a, b, seed)` exists, a hull level is a number with no
opponent.

**Naming.** `Technology.PROPULSION` reads as speed only, and this row also carries the hold — which
is a slight lie for a real gain in nouns. `LOGISTICS` covers both and is duller. **Davide's call**;
the enum constant is an on-disk identifier from the first merge, so it is free exactly once (§8).

---

## 4. The numbers — PROPOSED, NOT DECIDED, and none has been swept

Everything below has the standing `SurveyBalance` and `FleetBalance` have: build-authored against a
measured economy, with the *shape* the part worth defending. **§6 specifies the sweep that settles
them, and until that table exists these are the build's guesses.** Round 17's constants were all
moved by their sweep and these should be expected to move too.

```kotlin
const val DANGER_BONUS_PERCENT: Long = 35      // was −10 per point; 1.00 → 2.75 at danger 5
const val UNITS_PER_MINUTE_BASE: Int = 5       // was 10, flat; ×(1 + drive)
const val EXTRACTION_PER_HOUR: Long = 60       // was 20 — see below, this is the contested one
private const val HOLD_NUMERATOR: Long = 5     // ×1.25 a drive level
private const val HOLD_DENOMINATOR: Long = 4
```

### On the rate, and where this sheet disagrees with the instruction it was given

Davide asked for the rate to move and said 20% would not be enough. **He is right that it must move
and this sheet proposes tripling it — but a rate raise alone cannot fix what he is describing, and
that has to be said rather than quietly worked around.** A flat rate is a constant, and the table in
§0 decays because the colony compounds against a constant. Tripling 20 → 60 buys about a day and a
half of relevance and then the same curve resumes.

**What actually holds the fleet up over a fortnight is the multiplicative half** — danger up to
×2.75, richness up to ×1.6, the drive's hold up to ×3.05, and the drive's speed converting a 24h
window at the frontier from 5h 40m of station into 20h 40m. Compounded: one skiff on the 24h rung at
a maxed frontier target returns **16,640 metal at drive 5 against 586 today**, or **28×**. The rate
is 3× of that and **the other 9.5× is entirely "the player went somewhere"**, which is the behaviour
the whole sheet is trying to buy.

**The honest limit, stated once.** Even at 20×, a day-14 colony making 12,785 metal/h out-earns any
fleet a player can afford, because the hull curve compounds ×1.5 against a linear return. **The fleet
cannot be a fraction of income in the mid game without becoming the economy**, which is the exact
failure round 17 measured (142% of colony crystal at rate 40, fleet-first). What it can be is the
opening's pulse, the currency converter, and the reason the map is worth reading. If Davide wants
gathering to keep pace with a mature colony, that is colonisation's job (slice #10) and it should be
said now rather than discovered at the next round.

### Worked: the frontier crossover, at the 24h rung, richness held equal

Station time against a home-system world, times the danger multiplier. `1.00` is the near world.

| target | danger | drive 0 | drive 1 | drive 3 | drive 5 |
|---|---|---|---|---|---|
| own system, no hazards | 0 | 1.00 | 1.00 | 1.00 | 1.00 |
| across your galaxy, one hazard | 3 | 1.28 | 1.66 | 1.86 | 1.92 |
| another galaxy, no hazards | 3 | **0.49** | **1.27** | 1.66 | 1.79 |
| another galaxy, two hazards | 5 | 0.66 | 1.70 | 2.23 | 2.40 |

**Read the two bold cells: at drive 0 another galaxy returns half what the rock next door does, and a
single drive level turns it into a better buy than the rock.** That is the sheet's whole thesis in
two numbers — the frontier is not *given* to the player, it is *unlocked*, and the thing that unlocks
it is the technology Davide asked for. It also preserves `fleet-sheet.md`'s own finding that short
windows are for the neighbourhood and long windows are what the frontier is for.

---

## 5. The core changes, exact

All paths under `core/src/commonMain/kotlin/dev/fardavide/oltre/core/`.

| File | Change |
|---|---|
| `Research.kt` | `Technology` gains one constant. `Research` gains one field, one `levelOf` branch, one `withLevel` branch, one `initial()` argument. |
| `ResearchBalance.kt` | A cost curve and a duration curve for the new row, on the shapes the other three already use. |
| `FleetBalance.kt` | `DANGER_BONUS_PERCENT` replaces `DANGER_PERCENT_PER_POINT`; `FRONTIER_PERCENT` deleted; `flight`, `roundTrip`, `windowsFor`, `stationFor` gain a drive level; `cargo` gains the hold multiplier. |
| `StartRun.kt` | Passes `state.research` into `flight`/`stationFor`/`cargo`. **Cargo is still fixed at dispatch** — a drive level completing mid-flight must not retroactively enrich a run already out, which is the rule the file already states. |
| `GameSave.kt` | `SCHEMA_VERSION` → 9, plus a `8 to { … }` hop adding the new technology at level 0. Non-optional even though it is a one-key add: `migratedToCurrent` reads a missing step as *"this build cannot get there"*. |

**Three traps, all of which fail quietly rather than loudly:**

1. **`windowsFor` and `stationFor` must take the same drive level `cargo` does**, or the sheet offers
   a rung whose station time it then prices differently. They are three reads of one flight.
2. **Every existing `FleetBalance` call site is a compile error, and that is the good outcome** — the
   drive parameter has no default (the project bans them), so the compiler finds all of them.
   `DispatchUiState.kt` reads `flight`, `stationFor`, `danger`, `cargo` and `distanceBand`.
3. **`ResearchBalanceTest` and `BalanceBenchmarkGolden` both move.** The golden is the instrument that
   makes a balance change arrive as a diff on what a player experiences, so its `[fleet]` section
   should be **extended** to print the crossover table above, not merely re-recorded.

---

## 6. What the sim must print before any of this is believed

`printFleetReport` already prints the first two. The rest are new and the slice is not done without
them.

1. **The decay table of §0, re-run** — the middle columns must stop being constant.
2. **The band spread**, which today reads `band 0 ×56`. **The number to move is this one.** If a
   post-change bot still sends 56 of 56 dispatches to the home system, the sheet failed regardless of
   what any other table says.
3. **The crossover table of §4**, reproduced from the shipped formulas rather than from this
   document's arithmetic.
4. **The fleet-first purchase order re-run at the new rate** — round 17's binding row, and the one
   number that can veto the rate. A fleet-first player must still not out-produce their own colony in
   the currency they chose.
5. **The drive against the mine of the day** — a drive level competes for the research slot with
   Extraction and Enrichment, and it must not be strictly better than both on arrival.

---

## 7. What was rejected

- **Wiring `FRONTIER_PERCENT` as ratified.** Its constants are break-evens derived against a danger
  *penalty*; §2.1 removes the penalty, so they are arithmetic about a formula that no longer exists.
  Folding distance into the danger bonus is one mechanism instead of two.
- **Unclamping richness for the fleet.** `fleet-sheet.md` §3.5(ii) rejected it and the reason stands:
  it makes a single freak world the answer forever.
- **A per-run fuel cost to make distance matter.** Davide ruled out fuel in 2026-08-10, and
  `SurveyBalance`'s argument is unchanged: a cost that grew with distance *"would tax the player who
  is away longest, which is the one thing the check-in loop must never do."*
- **Letting a run gather deuterium.** Davide's clarification moved the complaint off deuterium and
  onto the investment-to-payoff ratio, so the load-bearing exclusion is left alone: 33 of 33 blocked
  check-ins were deuterium, and it is what the Robotics gate is priced in.
- **A hostility gate on gathering.** *"Habitability-gated gathering is colonisation"* — `fleet-sheet.md`
  §3, and §2.5 shows the real problem was that nobody was told the gate did not exist.
- **Raising only the rate.** It is what was asked for and §4 argues it is insufficient on its own. It
  is included, tripled, as one term of five.

---

## 8. Left open — Davide's calls

0. **Does the Shipyard go in first (§9 Slice A)?** This sheet says yes and it is the one place it
   reorders `fleet-sheet.md`'s own plan. The alternative — tune the rate for a one-skiff fleet — is
   coherent but means re-tuning it the day hulls go on sale.
1. **The rate.** Proposed 20 → 60. It is the number round 17 swept and the number this sheet is least
   confident in; §6.4 can veto it, and §0's first finding is the argument that round 17 measured a
   guardrail no shipped player can trip.
2. **Whether the ladders get a fleet payoff (§2.4).** Recommended *no*, with §2.5 instead.
3. **What the technology is called**, and whether one row carrying both speed and hold is acceptable
   or it should be two. Free exactly once — the enum constant is an on-disk identifier from the first
   merge.
4. **Whether "danger" is still the right word** once it is a payout. It reads as a warning and will
   be a promise for as long as slice #8 is unbuilt.
5. **Whether the frontier should be reachable at drive 0 at all.** §4's table makes another galaxy a
   bad trade until the first drive level. That is intended — it is what makes the technology feel
   like an unlock — but it means a new player's map is honestly smaller than it looks.
6. **Whether existing saves get a free drive level.** A colony mid-play wakes to a fleet that is
   half as fast as it was. The genesis-grant precedent says yes; the alternative is honest and means
   an existing player meets the slice at a research row.

---

## 9. The slicing plan

Three slices, and §0's first finding reorders them: **the Shipyard goes first.**

### Slice A — you can buy a hull

`buildShips`, the compounding curve that already exists in `FleetBalance`, and somewhere to tap it.
No new balance number — `shipCost` is written, tested and pinned in the benchmark, and it has simply
never been called.

**This is first because nothing else can be sized until it lands.** Every constant §4 proposes is a
per-hull rate, and a per-hull rate against a fleet of exactly one is not a balance decision, it is a
single multiplication. Round 17's guardrail — the one that produced the 20 this sheet wants to
triple — cannot even be evaluated until a player can buy a second skiff.

**Done means:** a player can turn metal into a hull; `printFleetReport`'s fleet-first purchase order
becomes a thing a real player can do; and the rate for Slice B is chosen against that measurement
rather than against the bot's `state.copy`.

### Slice B — the payout inverts

§2.1 (danger multiplies), §2.5 (the screen says a `Blocked` world is gatherable), and the rate. **No
new technology, no new research row, no save hop, no new noun** — a sign change, a constant, a
deletion and a sentence — and it lands the *"more challenging planets are even more rewarding"* half
on its own.

**Done means:** the sim's band spread stops reading `band 0 ×56`; the dispatch sheet states the bonus
where it used to state the penalty; the golden's `[fleet]` section carries the crossover table; and a
balance-log round 21 quotes it.

### Slice C — the drive

§2.2 and §2.3 — the technology, the research row, the flight curve, the hold multiplier, schema 9,
and the Research screen's fourth row. This is where *"far is way more time consuming"* lands, and it
lands *with* the thing that fixes it rather than before it.

**Done means:** the §4 crossover table reproduces from shipped formulas; a drive level visibly
returns rungs to a ladder that had narrowed; and §6.5 shows the drive competing with Extraction and
Enrichment rather than dominating them.

**A and B can be one PR.** A is a verb with a curve that already exists and B is four constants and a
string; separating them costs a release cycle for a player who is telling us the loop is dead now.
C is genuinely a slice of its own — it carries a schema hop and a screen.
