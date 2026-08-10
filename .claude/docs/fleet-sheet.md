# Fleet decision sheet — 0.4

Written by the build, 2026-08-10, on Davide's instruction: *"the game still feels empty… I think we
should allow users to manage some basic fleets, and explore neighbours planets. Close planets should
be less hostile, so the user can dispatch a ship to go gather some resource."* **Every line here is
his to overrule**; what it buys is that the slices can start without a number being invented at the
keyboard. Same shape as the 0.1 research sheet, the 0.2 galaxy sheet and the 0.3 adaptation sheet:
answer the question, name what was rejected, say what it should feel like so the next round can tell.

Nothing here changes a `GalaxyBalance` number, a `GalaxyDistributionTest` band, a `verdictFor` case or
a shipped `SurveyBalance` constant. Where the galaxy sheet already decided something it is quoted and
obeyed; where it deferred a constant to *"slice #7's call"* — the travel-time metric of its §4 — this
sheet chooses and says why.

This sheet closes roadmap slices **#6 (Shipyard)** and **#7 (Fleets, outbound)**, and it merges them,
because a shipyard that builds hulls with nowhere to send them is worse than the empty tab it
replaces.

---

## Davide's four calls, 2026-08-10 — asked before this sheet was drafted, and binding on it

The sheet was drafted against the request; these four answers arrived while it was being written and
**they are decisions, not preferences.** Where the draft disagreed with one, the draft moved.

| Question | His answer |
|---|---|
| How do near worlds become usable? | *"In a space game I assume it should be the baseline to scout a near planet and then gather some resources from it, while as of now this arrives like a week into the game. We really need to bring some 'space feel' into the game, as of now, in the early game, it is just any poorly made idle game."* |
| How does a gathering run repeat? | **Manual dispatch.** *"The only way to continuously gather from a planet, is to colonise it, which should come way later into the game I think."* |
| The v1 ship set | **Four fixed types now, hulls + modules later** — "each one existing because a slice needs it, so nothing is built before it has a job." |
| Does a fleet burn fuel? | **No fuel this slice.** Metal to build, time to fly, exactly as the probe. |

Three consequences the sheet is written to obey:

1. **Scout-then-gather is the baseline, not the endgame.** It has to work in the first check-in, which
   is why §3 rejects every option that gates gathering behind habitability — habitability-gated
   gathering *is* colonisation, and colonisation is explicitly late.
2. **No standing orders, no doctrines, no auto-repeat.** A run is one commitment. Notion's
   *"never reward tapping, reward deciding"* is satisfied by the dispatch being a real choice — which
   §3.5 says is exactly the thing not yet true, and is the one open call that matters.
3. **Continuous extraction is colonisation's**, so nothing here may creep toward a permanent income
   stream from a world you do not hold.

---

## The one-sentence version

**You cannot live there, but you can send a ship** — settling asks whether you survive the surface and
a gathering run asks only how far it went and what it met, so the 98% of the map that reads `BLOCKED`
stops being a wall and becomes the place you work.

---

## 0. What is actually broken, measured

Two sentences from `balance-log.md` round 16 set the whole brief, and neither is about money:

> **the colony has nothing in flight for 95.83% of the first 48 hours** (87.50% at 0.2.6, 91.66% at
> the cost change alone), the longest unbroken silence is 8h 52m, and the median check-in books
> **9 minutes** of work.

> The colony is not short of money and it is not short of time. It is short of **check-ins**.

And one sentence that rules out most of the answers, Davide's own, `balance-log.md:645-656`:

> *"I don't want the user to have nothing to do for hours, but I don't want it to be forced to keep
> logging it either, to avoid to fall behind. It must be a balance"*

So whatever fills the gap must be **startable in a check-in and harmless to miss.** That rules out
timed pickups, decaying bonuses and every mechanic whose value depends on reaction speed.

**One correction that must be carried forward before anything else.** Probes already took *"nothing at
all in flight"* from 95.83% to **2.08%**, and Davide played that build and still called the game an
empty idle. **So idleness is not the complaint, and a slice justified by the idleness number is a
second probe.** The reading that decides whether this works is the **fleet duty cycle** — ship-hours
committed against ship-hours owned — and the share of covered time the fleet is the *only* thing
covering. §7 and §6 say so again where it matters.

---

## 1. The mechanic — a run

**One verb.** You commit ships to a **surveyed world**, choose **which of metal or crystal** they
bring back, and choose **how long until they are home** from a fixed ladder of windows. The ships
leave the idle pool, fly out, work the surface for whatever is left of the window after the round
trip, and come home with cargo. `advance` lands the run at its instant, deposits the cargo, returns
the ships to the pool and logs it.

```
station = window − 2 × flight(target)
hold    = EXTRACTION_PER_HOUR × stationMinutes / 60                    — per hull, uncapped
cargo   = hold × richness(target, gathering) × (100 − 10 × danger) / 100
```

**There is no hold cap, and the draft that had one was wrong.** A cap at twelve station-hours was
proposed to stop the longest window dominating; the arithmetic says it does the opposite. Per skiff on
a home-system neighbour, priced units per day by cadence:

| cadence | capped at 480 | **uncapped** |
|---|---|---|
| four a day (4 × 6h) | 907 | 907 |
| twice a day (2 × 12h) | 933 | 933 |
| **once a day (1 × 24h)** | **480** | **947** |

Capped, the once-a-day player earns **49%** of what the twice-a-day player does. That is the check-in
loop taxing absence, which is the one thing Davide ruled out in as many words —
*"I don't want it to be forced to keep logging it either, to avoid to fall behind."* Uncapped, the
spread across every cadence is **4%**, and the small edge belongs to the player who checks in more,
which is the right direction and a rounding error in size. The 24h window was never dominant: it is
worth **1.3%** more per ship-hour than the 12h one, not more in total.

Everything on the right is known at dispatch. **The card states the exact figure before you commit,
to the unit.**

### What it costs

**Nothing per run.** The hull is the cost, bought once. There is no fuel and no dispatch fee.

Three reasons, in order of weight. `SurveyBalance.kt:11-20` already argues the third and the argument
transfers unchanged: a cost that grew with distance *"would tax the player who is away longest, which
is the one thing the check-in loop must never do."* Second, the measured colony spends to the floor on
metal every visit — closing stock at 48h is **38 metal** — so a per-trip price would make the verb
compete with the mines twice, once to build the hull and once to send it. Third, OGame's deuterium
bill is inseparable from fleet-save, which is one right answer, a savage punishment for forgetting,
and the most-botted action in the genre. **Transfer the bill; never transfer the chore.** The bill is
charged instead in the hull curve, §4.

### What comes back

**The world's richness in the resource you asked for, and never deuterium.**

The hold is measured in **priced units at the game's own 1 : 2 : 3** — the convention the adaptation
sheet's cost table and `:sim:run`'s `priced()` already use. A hold of 480 buys 480 metal or 240
crystal. The adaptation sheet's own argument for equal priced totals applies verbatim: *"the identical
priced total is what keeps that a preference rather than a right answer"* — strip the currency away
and there is nothing to choose between them, so the choice is entirely about what your colony is short
of. Metal buys hulls and mine levels; crystal buys research and the three ladders. Those are two
different strategies and the map is where you pick one.

**Never deuterium, and this is the load-bearing exclusion in the sheet.** Deuterium buys the Robotics
Factory, Robotics 1 opens research and Robotics 4 opens the ladders; the interaction census puts
**35.25%** of all refused actions behind an unmet requirement and the gate clock shows the Robotics
Factory unaffordable at 33 of 42 check-ins with deuterium the shortage at **33 of 33**.
`SurveyBalance` refused to *price* a verb in deuterium for exactly this; this refuses to *pay out* in
it, for the mirror reason. And it lands somewhere the design wanted anyway: cold worlds are deuterium
worlds, so **the fleet wants heavy and thick, and the colony wants cold.** Temperature is the one
axis the fleet can never undercut, which makes Thermal the ladder with an exclusive prize.

### The round trip, and why the window is the dial

`SurveyBalance` wrote the best sentence in the repository — *"the question a dispatch asks is 'how long
will I be gone'"* — and a run makes it literal by putting the window in the player's hand rather than
deriving it from the target.

**Flight eats the window; it does not extend it.** A far world delivers fewer station-hours out of the
same absence, and has to be richer to be worth it. How much richer depends on how long the absence is,
which changes every check-in. Worked at the constants of §4, uncapped — an adjacent slot in your own
system at richness 1.0 and danger 0, against a world a hundred systems out at richness 1.45 with one
hazard (danger 2):

| window | near, danger 0 | far, danger 2 | wins |
|---|---|---|---|
| 1h | 27 priced | *not offered* | — |
| 3h | 107 | 32 | **near, by 3.3×** |
| 6h | 227 | 172 | **near** |
| 12h | 467 | 450 | **near, barely** |
| 24h | 947 | 1,007 | **far, by 6%** |

**The crossover is real — and it only exists because the far world in that row was assumed richer.**
That assumption is the problem, and §3.5 is about it: nothing in the generator makes distant worlds
richer, so the honest reading of this table is *"a world 1.45 rich beats a world 1.0 rich once the
window is long enough to amortise the flight"*, which is a statement about richness with distance as a
tax. **Read §3.5 before treating the window as a strategic dial.**

**Guessing wrong is cheap in both directions, and that is the whole of "harmless to miss".** Guess
short and your ships idle in dock until you next look; guess long and the cargo lands while you are
away and waits for you. Neither is a loss, and the uncapped table above is what makes that true.

A window shorter than `roundTrip + MINIMUM_STATION` is **not offered at all**. A target where only the
24h chip survives has said *that is a long way* without a sentence.

### Deterministic. No roll, anywhere.

The haul is exactly what the card said. Three reasons and the first decides it:

1. **Composability forbids rolling at arrival.** `advance(s,t0,t2) == advance(advance(s,t0,t1),t1,t2)`
   means nothing may enter a transition that the two paths do not both share. A seed fixed at dispatch
   would satisfy the property — and at that point the game already knows the answer and is choosing to
   hide it, which is a lie of omission rather than a mechanic.
2. **At three dispatches a day, variance is not a distribution, it is an accident.** OGame's expedition
   tables work at hundreds of rolls per player per week; its **18.6% "nothing"** outcome at Oltre's
   cadence is a wasted day roughly every other day, spent on the one action the game offered.
3. **The galaxy sheet already settled the ethos.** §1: *"why richness is derived, not rolled."* A
   computable payoff is what makes the allocation a decision instead of a pull.

**If a roll is ever added, its seed must be derived at dispatch** — `hash(galaxySeed, target,
dispatchedAt, eventLog.size)`, where `eventLog.size` is monotone and is incremented by the dispatch
itself, so two dispatches at the same millisecond to the same target draw differently. Written down
here so the next slice does not have to rediscover it.

### The risk: danger takes cargo, not hulls

Every run comes home. There is no zero outcome and no total loss in this sheet. What a dangerous world
costs you is **a stated share of the hold**, shown before you commit — see §3 for what `danger` is and
§4 for the constant.

**Permanent hull loss is deferred to slice #8, deliberately and not timidly.** The pillar survives; the
delivery does not work yet. A hidden sub-1% catastrophe at three rolls a day is either invisible — so
it is not a pillar — or it fires once, costs a week of fleet, and ends the session. Signposted loss
needs something to lose the hulls *to*, and that is a combat model. The day `resolve(a, b, seed)`
exists, `danger` stops taking cargo and starts taking hulls, the `ESCORT` hull arrives to answer it,
and nothing in this sheet has to be unwritten.

### Several runs may target the same world

No one-per-target rule, unlike `surveys`. Two reasons and the second is the important one:

1. Staggering two runs to one world at different windows is a legitimate way to stagger income, and
   forbidding it would push the player onto worse targets to use their pool.
2. **A one-per-target rule would make the size of your surveyed map the fleet's ceiling** — and then
   every probe would deliver ~4.75 guaranteed dispatch slots for 150 metal, cheaper than the fourth
   hull. Probing would become strictly efficient and could never disappoint, and the galaxy sheet is
   explicit: *"`Barren` must be the common answer… If a survey usually pays off, surveying is a tax
   rather than a decision."*

With repeats allowed, a survey buys the fleet **better** targets, never **more** of them — its fleet
value is `max(new) − max(known)`, which diminishes to nothing exactly the way its settlement value
does. **Surveying still usually disappoints, and now it disappoints the fleet the same way.**

The consequence is stated rather than hidden: the best world in reach is farmable forever, and after a
week the player may have found their crystal world and stopped consulting the map. If play shows
parking, the lever is a cooldown **folded out of the append-only log** — *when did I last gather here*
— never per-world mutable state, because the galaxy is a seed plus what the player changed and a
counter for every world ever visited is a save that grows without bound.

---

## 2. The ship set — the concrete answer to slice #6

```kotlin
@Serializable enum class ShipType { SKIFF, HAULER, ESCORT, SETTLER }
```

**Four constants, matching Notion's v1 count. Two buildable, and not at the same time.**

| Hull | What it is FOR | Ships in |
|---|---|---|
| **SKIFF** | going far, and going soon. One berth of hold, full speed. | **slice 1** |
| **HAULER** | working the neighbourhood, and staying. Four berths of hold, **half speed**. | **slice 4** |
| `ESCORT` | surviving what a hauler cannot. Its whole behaviour is a combat model. | slice #8 |
| `SETTLER` | carrying a colony. | slice #10 |

**Why not four buildable now.** `FIGHTER` and `CRUISER` differ only *inside* a combat model, so today
they are two rows with different numbers and identical behaviour — a fake decision, on a new tab, which
is the "boring idle" complaint with a fresh coat of paint. `COLONY_SHIP` is a hull you can build and
cannot use, which is worse than an empty tab because an empty tab is honest. Each of the two reserved
constants is blocked by exactly one design call Davide has already been told he owes, and neither is
blocked by this sheet. Adding an enum constant is free; the tally is met.

**Why not one buildable, ever.** One hull makes "how many do I send" the only dial, and *send
everything, always* is its answer inside a day. The skiff/hauler axis is the only composition question
available with no combat model: they trade **speed against hold**, and the crossover lands inside the
range of gaps `:sim:run` actually measured. Per priced unit spent, over an absence *T*, the hauler wins
when the flight it saves is worth less than the hold it carries:

| target | skiff round trip | hauler round trip | hauler wins past |
|---|---|---|---|
| home system, adjacent slot | 20m | 42m | **~1h 40m** |
| the next system | 40m | 1h 20m | **~4h** |
| twenty systems out | 58m | 1h 58m | **~6h** |
| across your own galaxy | 4h 48m | 9h 36m | never inside a day |

So **haulers work the near rocks and the long stays; skiffs go prospecting.** Neither obsoletes the
other, which is the OGame composition failure mode avoided — and the mix is a bet on your own check-in
rhythm, the one question nothing else in the game asks.

**Why the hauler is a later slice.** This design introduces four nouns — window, danger, berth, hull —
against a 5–10 minute check-in on a 393dp screen. That is the top of what can be taught at once, so
§10 spends them one per slice. The hauler is the fourth noun and it arrives fourth.

**The rename, and its expiry.** `CARGO / FIGHTER / CRUISER / COLONY_SHIP` are flagged PLACEHOLDER at
`Fleet.kt:6-8`, which warns that *"these constant names are now on-disk identifiers in every save, so a
rename is a save-format change too."* That is true and it is cheaper than it looks: **nothing in the
repository has ever constructed a `ReturningFleet` outside test code**, so no save any player holds
contains a `ShipType` string. But the migration still has to be written and tested, because the frozen
fixtures `VERSION_1_FULL`, `VERSION_2_FULL`, `VERSION_3_FULL` and `VERSION_4_FULL` in `GameSaveTest`
each carry `"returningFleet":{"ships":{"CARGO":14},…}` and `GameSaveTest` asserts on the migrated
result. **A save is not guessed at.** See §5.

**The names are Davide's.** `SKIFF` and `HAULER` are function-names rather than OGame-lineage names,
because two of the four placeholders are what made the set feel decided when it was not. They are on
disk from the first merge, and free exactly once before it.

**There is no Shipyard building.** Notion's v1 list says six buildings and `BuildingType` stays at six.
See §4 for what bounds the fleet instead, and §8 for what a seventh facility would have cost.

---

## 3. The hostility question — DAVIDE'S CALL

Davide: *"Close planets should be less hostile, so the user can dispatch a ship to go gather some
resource."*

**What is true today, and it is not an oversight.** The galaxy sheet's §4 puts position into generation
in exactly one place — *"Temperature is a function of slot"* — and `SurveyBalance.kt:11-15` states the
consequence in one line: *"the generator has no per-system gradient — a system index enters
`GalaxyBalance` in exactly none of its trait functions and reaches `GalaxyGeneration` only as a hash
salt."* Gravity is `0.15 + 2.6u²` and pressure is `12u³`; neither knows where it is. `worldAt(seed, at)`
takes **no home**.

The four options, each with what it costs, what it breaks, and what moves downstream.

### (a) A real hostility gradient by distance from home

Scale the three axes toward 1.0 near home, or narrow the jitter, so near worlds genuinely pass more
bands.

- **Costs** — `worldAt` gains a home parameter, so generation becomes a function of player state. The
  galaxy sheet's §7 promise dies with it: *"the galaxy generated by seed S today is the same galaxy
  under seed S after the next three slices add fields to `World`."* Two saves on one seed with
  different homes see different maps, and the named-sub-stream argument that makes adding an axis
  later safe no longer holds, because the stream now depends on something outside the seed.
- **Breaks** — all four rows `GalaxyDistributionTest` pins (passes-every-band 1–2%, fails-exactly-one
  12–18%, the rest, settleable ≤0.5%), and the three near-identical per-axis pass rates that §8 calls
  *"the point, not a coincidence"*. §9 already proved the first two rows constrain each other so
  tightly that the only mixes reaching a large "come back later" pile are around **0.06 / 0.58 /
  0.59** — one axis blocking 94% of worlds while the other two wave nearly everything through. That
  is a galaxy with one ladder that matters, which is the single-habitability-score design §1 rejected,
  arrived at from the other side. A near-home softening is that same collapse, localised.
- **Downstream** — `SurveyBalance`'s flat cost is justified **by** payload being identical galaxy-wide;
  a gradient makes near probes strictly better and the flat price becomes a subsidy for far ones, so
  probe pricing has to be re-derived and re-swept. And because **richness is derived from hostility**,
  "near is gentler" means "near is poorer" by construction: the first ladder would land worlds that are
  settleable and worthless, and the whole first-week neighbourhood becomes something to leave.

### (b) A guaranteed starter neighbourhood

Carve out *n* good worlds near home rather than changing the generator.

- **Costs** — a rule with an edge, and the edge is the thing players learn to game. It also expires:
  once you have pushed past it, it has taught you nothing about the rest of the map.
- **Breaks** — the galaxy sheet's own *"what it should feel like"*: *"The first settleable world should
  be underwhelming — nearby, mediocre yield, and obviously worse than the one two galaxies away they
  cannot reach. That tension is the whole pillar."* A guaranteed good neighbourhood deletes that
  sentence.
- **Downstream** — probe pricing survives; the ladders' payoff is dented, because the first good world
  arrives free.
- **But half of it survives and should be taken.** See the recommendation.

### (c) Hostility gates settlement; gathering ignores it entirely

`verdictFor` unchanged, generator unchanged, and a run may target any surveyed world that is not home
and not held by someone else — `Blocked` and `Barren` included.

- **Costs** — nearly nothing in `core`.
- **Breaks** — nothing pinned. `GalaxyBalance` untouched, `GalaxyDistributionTest` untouched, `worldAt`
  still takes no home, `verdictFor` gains no case.
- **Downstream** — probe pricing stands, because payload really is identical galaxy-wide *for a probe*.
  The ladders are answered below.
- **What it does not deliver** — any felt gradient at all. Every world is equally safe. It answers the
  purpose clause of Davide's sentence and replaces the mechanism clause, and a player reading the
  Galaxy tab still sees the same `BLOCKED` wall on the world next door.

### (d) The recommendation — (c), plus a fleet-facing `danger` that carries the distance term, plus the surviving half of (b)

**Split the two words the request runs together.**

- **Hostility** — can you *settle*. Unchanged, position-free, still three axes and three ladders.
- **Danger** — what the trip *costs you*. Fleet-facing, new, and free to carry distance.

```kotlin
danger(from, world) = world.traits.hazards.size + distanceBand(from, world.at)     // 0 … 5
```

| where the target is | distance units | band |
|---|---|---|
| your own system | 5 – 70 | **0** |
| within 125 systems | 100 – 720 | **1** |
| the rest of your galaxy | 725 – 1,340 | **2** |
| another galaxy | 2,700 + | **3** |

Both inputs already exist. Hazards are generated on **45%** of worlds (35% one, 10% two) and are read
today by nothing except a −0.05 yield penalty — the galaxy sheet put them there for slice #10's content
and this is the first thing that consumes them. Distance is the sheet's own §4 metric. **No new
generation, no new roll, no new save state, no distribution moved.**

**Your own system with no hazards is danger 0: a completely safe, completely deterministic first run.**
The home system holds ~4.75 worlds and ~55% of worlds carry no hazard, so almost every colony gets one
on day one. That is Davide's sentence delivered in the only unit a fleet can feel it in.

**Plus the surviving half of (b):** constrain `homeFor` to a system holding **at least two other
worlds** — no richness guarantee, no hostility guarantee, nothing that touches "the first settleable
world should be underwhelming." Home is player state stored in the save, not a trait drawn from a
stream, so constraining it re-rolls nobody's map and moves no distribution. It is the cheapest possible
guarantee that the verb has something to point at in the first check-in. *(Checked: the test seed's
home system 3:165 holds worlds at slots 7, 8, 10 and 13, so `TEST_GALAXY_SEED`'s home does not move and
no core fixture changes.)*

### Does this devalue the adaptation ladders?

The honest worry, and it deserves a number rather than a reassurance: if a `Blocked` world is already
useful, then *"Gravitic Adaptation 3 would land it"* competes with *"or just keep sending skiffs."*

**It does not, and the reason is `GalaxyBalance`'s own arithmetic.** Richness is derived from
hostility, so the richest worlds are the most hostile ones, and a ladder only ever reaches the worlds
just outside your current band:

| | the best metal world you can **settle** | the best metal world you can **send a ship to** |
|---|---|---|
| Gravitic 0 | 1.40 g → richness **1.10** | 2.75 g → richness **1.58** |
| Gravitic 3 | 1.76 g → richness **1.23** | 2.75 g → richness **1.58** |
| Gravitic 6 | 2.12 g → richness **1.36** | 2.75 g → richness **1.58** |

**The fleet works the extremes and the ladders work the middle, and the gap between them never closes**
— because the fleet ignores the band entirely and the ladder's job is a different job. A ladder does
not make a world a better fleet target; it makes it a place you can stand. They do not compete for the
same ground.

What is genuinely true and should be said plainly: for the whole of 0.4 the *payoff side* of that
comparison does not exist, because colonisation is slice #10, so the player is being asked to take
"settling brings a colony, a run brings four hours of one mine" on faith. The adaptation sheet already
warned about this shape — *"Nothing settles yet… it is not the pillar landing, and the next round
should not read it as one"* — and this is the round that has to watch it.

### What the recommendation does not give him

**A near world still reads `BLOCKED` to a colonist.** "Close planets are less hostile" becomes true of
what the fleet does and false of what the settler does, and a world row now has to carry both readings
at once — `BLOCKED · gravity 2.4 g` and `metal 1.42 · danger 1` in one card. That is the hardest
design problem in the slice and it is handed to Claude Design as such.

**This is Davide's call.** If the literal version is what he wants, it is a galaxy-sheet revision and a
separate round, and it takes the four pinned distribution rows and the probe's price with it.

---

## 3.5. Distance buys nothing, and this is the one finding that decides whether the map is a strategy surface — DAVIDE'S CALL

Found by the adversarial pass over §3, verified against the generator. **It is the most important
paragraph in this sheet**, and it is the thing Davide's own instinct was pointing at when he asked for
a gradient.

**The generator has exactly one positional trait, and this design forbids gathering it.**
`GalaxyBalance.temperature(slot, starClass, jitter)` is the only trait that knows where it is, and it
knows only its *slot* — not its system, not its galaxy. Metal richness derives from gravity
(`0.15 + 2.6u²`) and crystal richness from pressure (`12u³`); both are plain uniform draws, and
`GalaxyGeneration` sees a system index only as a hash salt. The one richness with a positional term is
**deuterium**, from temperature — and §1 forbids deuterium as cargo, for the Robotics gate.

So metal and crystal richness are **identically distributed at every distance from home**, and the
consequences compound:

1. **At equal richness, the nearest world always wins**, at every window, because flight is pure
   overhead. The §1 table's crossover exists only because its far world was *stipulated* to be 45%
   richer, and nothing makes that more likely far away than next door.
2. **The search terminates fast, and for crystal it barely starts.** `crystalRichness` clamps at 1.6
   for any pressure ≥ 6.0 atm — `u ≥ 0.794` — and **19.3% of all worlds sit exactly at that clamp**
   (236 of 1,221 measured over galaxy 3 of the shipped seed). One probe surveys ~4.75 worlds, so it
   finds a clamped-maximum crystal world with **65–88% probability**. After one or two probes the
   player owns a target that cannot be beaten, only equalled — and every subsequent survey is worth
   nothing to the fleet.
3. **Therefore the player parks.** They find the best near world in the first day or two and re-dispatch
   to it forever. The Galaxy tab becomes a screen you visited twice, the window ladder collapses to
   "whatever matches my gap", and the mechanic is a timer with a fleet-shaped UI — **which is the
   complaint this whole sheet exists to answer.**

### The options

| | What it is | Cost | What it breaks |
|---|---|---|---|
| **(i) A fleet-side frontier term** *(recommended)* | `FleetBalance` scales the hold by distance band — e.g. ×1.0 / ×1.15 / ×1.35 / ×1.6 for own-system / near / far / another galaxy. Lives in `FleetBalance` only. | One constant table and one multiplication. | **Nothing.** `worldAt` stays pure and seed-stable, no generation constant moves, no `GalaxyDistributionTest` row moves, `verdictFor` gains no case. |
| **(ii) Unclamp richness for the fleet** | `FleetBalance.cargo` reads an uncapped richness so the tail keeps going and better worlds keep existing. | Re-derives the payout curve; the clamp exists to stop one extreme world outscoring every balanced one, and that reason is real. | Makes a single freak world the answer forever — the clamp's own failure mode, re-introduced. |
| **(iii) Accept it** | The neighbourhood is where you work; the map's job is settlement, later. | Free. | *"Explore neighbours planets"* becomes a one-time act, and the frontier never exists. Honest, and it is the status quo of this sheet. |

**Recommendation: (i).** It is the cheapest thing on the list, it breaks nothing pinned, and it makes
the dispatch a genuine three-way trade — **near, safe and small against far, dangerous and big, played
against how long you are going to be away.** That is a strategy decision rather than a schedule
report, and it is what "space strategy instead of a boring idle" actually means in a number.

Note what it does to Davide's original sentence, plainly: *close planets are less hostile* becomes
**true and load-bearing** — close is safe, cheap and modest; far is rich, dangerous and slow. The
gradient he asked for arrives, carried by the fleet rather than by the generator, so the galaxy sheet
is untouched and the adaptation ladders keep their prize.

**Not built into §4's numbers.** Every table below assumes a flat hold, so if (i) is taken the sweep in
§6 runs against the banded one and `EXTRACTION_PER_HOUR` moves with it.

---

## 4. The numbers

**Every number here is PROPOSED, NOT DECIDED.** It has the standing `SurveyBalance` has and not the
standing `GalaxyBalance` has: build-authored against a measured economy, with the *shape* the part
worth defending. Nothing here has been through `:sim:run` — §6 specifies the sweep that would settle
it, and until that table exists these are the build's guesses.

Sized against the 0.2.7 harness, re-measured 2026-08-10: level-1 income **90 / 36 / 15** per hour;
genesis **500 metal / 300 crystal**; the day-2 colony at 425 / 168 / 33; the fortnight closing on
**293,623 unspent metal against 999 crystal** with crystal the sole blocker in **273 of 336** hours and
deuterium short in **336 of 336**; check-in gaps at four a day of **5h, 6h, 4h and 9h**.

### Travel — the galaxy sheet's §4 metric, given a clock

The metric is the sheet's, verbatim, implemented for the first time. The sheet deferred exactly one
thing to this slice — *"Slice #7 picks seconds-per-unit and whether fuel is a cost"* — and this is it.

```
different galaxy →  2700 × |Δgalaxy|
same galaxy      →  95 + 5 × |Δsystem|
same system      →  5 × |Δslot|

flight(SKIFF)  = 10 + units / 10   minutes, one way
flight(HAULER) = 20 + units / 5    minutes, one way          (slice 4)
```

Pure integer arithmetic on bounded inputs, so it is identical on the JVM and on Kotlin/Native — the
same rule `GalaxyGeneration` and `SurveyBalance` already follow, and `core` compiles for Native.

| target | units | skiff round trip | hauler round trip |
|---|---|---|---|
| adjacent slot, your own system | 5 | **20m** | 42m |
| across your own system (7 → 15) | 40 | 28m | 1h 12m |
| the next system | 100 | 40m | 1h 20m |
| twenty systems out | 195 | 58m | 1h 58m |
| a hundred systems out | 595 | 2h 18m | 4h 38m |
| across your own galaxy | 1,340 | **4h 48m** | 9h 36m |
| the next galaxy | 2,700 | **9h 20m** | 18h 40m |

`BASE_FLIGHT_MINUTES = 10` is the number that makes a home-system target real rather than instant, and
it is the same failure `SurveyBalance.BASE_MINUTES` exists to prevent: *"without it the nearest targets
would land inside the check-in that ordered them."*

### The window ladder — 1h · 3h · 6h · 12h · 24h, minimum station 20 minutes

Read straight off the measured cadence. 3h is the *"ogni 2/3 ore"* rhythm Davide named; 6h covers the
5h and 6h daytime gaps; 12h covers the 9h overnight with room; 24h is the once-a-day player; 1h is the
*I am still holding the phone* run that gives the first sitting a fleet beat. A window is offered only
when `window ≥ roundTrip + 20 minutes`.

### The hold — 40 priced units per skiff station-hour, uncapped

`EXTRACTION_PER_HOUR = 40` priced units, per hull, per station-hour, at richness 1.0. **No cap** — see
§1 for the arithmetic that removed the one the draft had.

**`EXTRACTION_PER_HOUR = 40` is the number most likely to be wrong, and it must not ship unswept.**
Two independent corrections landed on it after the draft, and both push down:

1. **The denominator is wrong.** *"One skiff is 16% of a genesis colony"* divides by a colony 0.2.7
   deleted. `printFirstSitting` on this tree reaches **14 building levels inside the first hour**, and
   a run dispatched at genesis lands at hour 3 against a colony already at Metal Mine 5–6 making
   218–272 metal/h. The share has to be re-derived **against the colony that exists when the cargo
   lands**, not at genesis.
2. **The priced basket is the wrong unit for a mechanic whose selling point is that you pick the
   currency.** 34 priced/hour taken as *crystal* is 17 crystal/hour against a genesis colony's 36 —
   **47%**, not 16%. The binding row is the crystal one and nothing in the draft measured it.

So §6's sweep reads fleet income **as a share of metal income and of crystal income separately**, and
the constant is expected to land well below 40.

**Why one resource per run and not a share of all three.** A payout split by richness hands over a
little deuterium on every run, softening the gate; and it flattens the target choice into a 20–57% band
where a single-resource payout gives a **2.67×** spread.

### Danger — 10% of the hold per point

`danger = hazards.size + distanceBand`, 0 … 5, so a fully exposed run keeps half its hold. Sized for
legibility rather than for a curve: at 8% a two-hazard far world is a rounding error, and at 15% the
frontier is unreachable at every window. **Deterministic and stated before the tap** — the pillar is
signposted or it is a tax wearing a story.

### The hull — 80 metal / 20 crystal, compounding ×1.5 per hull already owned

`shipCost(SKIFF, owned) = compound(Resources.of(metal = 80, crystal = 20), owned, 3, 2)` — the game's
one cost curve, through `Curves.compound`, floored at every step.

| Nth skiff | metal | crystal | priced | cumulative priced |
|---|---|---|---|---|
| 1 *(granted)* | 80 | 20 | 120 | 120 |
| 2 | 120 | 30 | 180 | 300 |
| 3 | 180 | 45 | 270 | 570 |
| 4 | 270 | 67 | 404 | 974 |
| 5 | 405 | 101 | 607 | 1,581 |
| 6 | 607 | 151 | 909 | 2,490 |
| 8 | 1,366 | 341 | 2,048 | ~6,000 |

**This curve is the fleet's ceiling, and it is why there is no Shipyard building.** It proves
boundedness the way every ceiling in this game is proved — a compounding price against a linear return
— and it needs no seventh facility, no berth concept and no new noun. Measured against the alternative
buy at the same moment:

| | priced cost | priced return per hour | per priced spent | against a mine level |
|---|---|---|---|---|
| 3rd skiff | 270 | 17 | 0.063 | **52%** |
| 4th skiff | 404 | 17 | 0.042 | **34%** |
| 6th skiff | 909 | 17 | 0.019 | **15%** |
| Metal Mine 5 → 6 | 444 | 54 | 0.122 | — |

**The mine is the better rate buy, permanently and by construction — and the fleet is bought anyway.**
Two reasons, both measured. `startUpgrade` refuses a facility that is already building, so a check-in
that has tapped all six has nowhere left to put its metal; and the fleet pays in a currency you choose,
which no mine does. Round 16 named that lever and called it a mechanic. This is the mechanic.

**The natural fleet is three to four skiffs at the opening and six or seven at depth** — because the
hull price is flat in absolute terms while a mine level's is not, so the crossover drifts outward as
the colony grows. No cap is added, and none is needed: past the crossover the mine is simply the better
buy and the player can see it.

**Metal-led, for `SurveyBalance`'s own reason** — *"metal is the resource with nothing to buy, and this
is the thing to buy with it."* The 1 : 4 crystal component is there so the fleet is not entirely free
of the scarce resource, and it is small enough never to compete with a ladder.

### Purchase is instant, and that is a sizing decision

`buildShips(state, ships, at)` charges and delivers in the same call. **No yard job, no fifth job
kind** — which removes a term from `Advance`'s completion union, a member from `FutureEvent`, a slot
from the tie-break ladder and an id from the notification budget, all at once. What the timer was
protecting is already protected by the price curve, and the probe's own philosophy applies: **the wait a
hull costs you is the flight, not the yard.** A check-in must be able to buy and dispatch inside five
minutes.

It **must still append `Event.ShipsBuilt`** — `GameSession.kt:127` detects a discrete transition by
`eventLog.size` changing, and that is what triggers both the save write and the notification re-sync. A
verb that changes state without appending an event is invisible to both.

### Gated by nothing

No Robotics requirement, no research requirement, no building. `startSurvey`'s reason transfers with
more force: the verb whose whole job is to exist at hour zero cannot sit behind a building, and this one
exists specifically to fix an opening that is empty **95.83%** of its first 48 hours. Gating the cure
behind Robotics 1 at hour 6 would put it behind the disease. The hull price is the gate.

### Genesis: one skiff, granted

A new colony opens with **one skiff in the pool**, on the same argument the 500 metal is granted and the
mines start at level 1 — `BalanceCurveTest`'s own words, *"a new colony opens on a decision, not on a
wait."* One and not two, so the second hull is the first fleet purchase and the player learns the shop
exists by wanting something from it.

### The two stories the numbers have to tell

**Hour zero.** At the sim's own seed the home system is surveyed at genesis and holds three worlds
besides home: `[3:165:8]` (metal 1.24 · crystal 0.74), `[3:165:10]` (metal 1.21) and `[3:165:13]`
(metal 1.17 · crystal 1.13). Today all three read `Blocked` and there is nothing on any screen a player
can do about them.

- 3h window to `[3:165:8]`, round trip 20m, station 2h 40m → 107 priced × 1.24 = **132 metal**
- 3h window to `[3:165:13]` for crystal, round trip 26m, station 2h 34m → 103 × 1.13 = **58 crystal**

A colony making 90 metal and 36 crystal an hour. So a first run is **an hour and a half of one mine**,
and the first fleet decision in the game is which of your three neighbours is worth the trip — posed by
worlds the generator already put there, on a screen the player already has.

**Day two, overnight.** Four skiffs, the 12h window, a probed world twenty systems out at crystal 1.45
with one hazard (danger 2). Round trip 58m, station 11h 02m → 441 priced per skiff, × 1.45 × 0.80 →
`4 × 512 = 2,048` priced → **1,024 crystal.** A Crystal Mine 8 → 9 costs 615 crystal.

**The overnight run buys the level the colony could not afford**, in the resource the balance log has
called its biggest open item since round 13.

---

## 5. The core changes, exact

All paths under `core/src/commonMain/kotlin/dev/fardavide/oltre/core/`.

### New types — `Fleet.kt`, rewritten

`Coordinates` is **deleted**. `status.md:241-243` assigns the twins fold to this slice and this is where
it closes: the old type is unbounded (`> 0` only, so galaxy 9999 constructs) and `GalaxyCoordinate` is
bounded to the real space, so the generator never has to answer for a coordinate off the map. It touches
`FutureEvent.FleetArrives.origin` and `GameNotifications.kt:192`'s `Coordinates.label()`.

```kotlin
@Serializable enum class ShipType { SKIFF, HAULER, ESCORT, SETTLER }

// Mirrors `Resources` deliberately — an init guard, `covers`, `minus`, a companion — because it is
// the same kind of thing: a bundle you spend and get back. A map rather than a flat record because
// the ship set is scheduled to grow twice, and a flat record would be edited by slice #8, by slice
// #10 and by every save hop between; a map absorbs a constant for free.
@Serializable
data class Ships(val counts: Map<ShipType, Int>) {
    init { require(counts.values.all { it > 0 }) { "ship counts must be positive, were $counts" } }
    val isEmpty: Boolean get() = counts.isEmpty()
    fun covers(other: Ships): Boolean
    operator fun minus(other: Ships): Ships   // drops zeroed entries
    operator fun plus(other: Ships): Ships
    companion object { val NONE: Ships }
}

@Serializable
data class FleetRun(
    val target: GalaxyCoordinate,
    val ships: Ships,
    val gathering: ResourceKind,   // METAL or CRYSTAL, never DEUTERIUM
    val cargo: Resources,          // fixed at dispatch
    val dispatchedAt: Instant,
    val returnsAt: Instant,
) {
    init {
        require(!ships.isEmpty)
        require(gathering != ResourceKind.DEUTERIUM)
        require(returnsAt > dispatchedAt)
    }
}
```

**`require(counts.values.all { it > 0 })` is not hygiene, it is the composability property.** Both
`mapOf(SKIFF to 0)` and `emptyMap()` are reachable — a run that dispatches the last hull and later
returns it reaches both — and `advance`'s property is asserted with `assertEquals` on whole
`GameState`s, so a non-canonical representation makes one span and two spans produce equal games that
fail equality. The guard makes the representation canonical, and `minus` drops zeroed entries so it
can.

`FleetRun` is the fifth job kind and it is genuinely a different animal from the other four — it is the
only one that carries its own outcome — so it does not pretend to the `(subject, startedAt,
completesAt)` shape `Survey.kt:30-31` defends.

### `GameState`

Loses `returningFleet: ReturningFleet?`. Gains two fields:

```kotlin
val ships: Ships,            // the IDLE pool — dispatched hulls leave it, arrivals return them
val runs: List<FleetRun>,    // parallel, several at once, NO distinctBy guard
```

The pool is the *idle* count rather than the total, for the same reason `resources` is the available
stock and a `BuildJob` holds no money: the cost is spent at dispatch and returned at completion.

**No cross-field invariant in `init`**, and this is a departure worth stating because two of the three
proposals wanted one. A `require` relating the pool, the runs and a fleet ceiling would run on every
`copy` inside `advance`'s hot loop, would be safe only as long as the arrival branch happens to mutate
both fields in one `copy`, and would throw at runtime the day a later slice split it. `core` polices
*impossible*, not *cheating* — `GameState` does not require that `resources` be affordable for anything
either. The pool is bounded by `buildShips` and `startRun`, which are the only things that write it.

Three full-constructor call sites break (`initial()`, `ColonyUiStateTest`, `ResearchUiStateTest`),
because the project bans defaults in data-class primary constructors. Everything else builds by `.copy`.

### `Advance.kt` — four edits, and the first fails silently if it is missed

1. **The completion union at `:13-18` gains `state.runs.map { it.returnsAt }`.** There is no registry —
   four hard-coded terms become five. **A job kind absent from that expression never completes and
   `advance` accrues straight past it forever, with no test failing.**
2. **The arrival branch becomes a loop**, in the same position (last), sorted on a key that is
   **intrinsic to the job and never list order** — `Advance.kt:74-78` already writes the reason and it
   transfers unchanged: *"list order would be insertion order, and a log whose order depends on the
   sequence of taps that produced it is one a reloaded save reproduces only by accident."*

   **The key is `(dispatchedAt, target.galaxy, target.system, target.slot)`** — and the draft's
   `(target, cargo, ships)` was not implementable. `compareBy` needs `Comparable` selectors and none of
   those three are: `GalaxyCoordinate` (`Galaxy.kt:26`) and `Resources` (`Resources.kt:9`) are plain
   data classes and `Ships` wraps a `Map`. `dispatchedAt` is a `Comparable` intrinsic to the job and
   unique per run in practice; the packed coordinate breaks the residual tie. It also **fits in a single
   `Long`**, which is what `FutureEvents.secondaryTieBreak()` returns (`:134-137`) — so the two sides of
   the ordering can genuinely mirror each other, which the draft's key could not.

   Each arrival does `resources.deposit(cargo)`, `ships = ships + run.ships`, `runs = runs - run`, and
   appends `FleetReturned`.
3. **`advance` is marked `tailrec`.** The recursive call at `:23` is already in tail position;
   recursion depth is the number of events in the span, which was bounded at ~6 builds + 1 project +
   N probes + 1 arrival and which parallel runs across a week's absence or a debug skip makes
   unbounded. One word, and it belongs in this slice.
4. `deposit` stays private and stays clamped at `CAP_FINE`. See §9.

**The ordering premise survives, and it is checked rather than assumed.** `Advance.kt:27-36` argues
that the order of simultaneous completions changes only the event log, because *"none of these
transitions reads another's result."* An arrival now writes `resources` **and** `ships` — and nothing
else applied at a boundary reads either, because `advance` never spends; only the verbs do, and they
run between advances. So the premise holds. **A later slice that lets an arrival start something — a
re-dispatch, a queued build paid from the cargo — kills it, and the tie-break becomes a real semantics
question that day.**

### `Event.kt` — three members

| Member | `@SerialName` | Payload |
|---|---|---|
| `FleetDispatched` | `"FleetDispatched"` | `target: GalaxyCoordinate, gathering: ResourceKind, ships: Ships, at` |
| `FleetReturned` | `"FleetReturned"` *(unchanged)* | `from: GalaxyCoordinate?, ships: Ships, cargo: Resources, at` |
| `ShipsBuilt` | `"ShipsBuilt"` | `ships: Ships, at` |

**The discriminators are PascalCase, and the draft got this wrong in a way that would have shipped a
broken migration.** Every one of the nine existing members is PascalCase — `Event.kt:68` is
`@SerialName("FleetReturned")`, not `"fleet_returned"` — and `Event.kt:14` says these strings are
on-disk identifiers in every save. A migration written against `"fleet_returned"` matches nothing,
silently skips the `CARGO → SKIFF` rewrite, and the save then fails to decode on an unknown enum
constant, because the `Json` at `GameSave.kt:78-82` does not set `ignoreUnknownKeys`. The rewrite in
step 5 keys on `"type":"FleetReturned"`.

`FleetReturned` finally gets the `Started` partner it has been missing since 0.0.6, which is the
taxonomy's own rule (`Event.kt:48-50`) rather than an invention. Its `@SerialName` does not move —
renaming a class is free, changing a `@SerialName` is a schema break.

**`from` is nullable, and that is not a default value in the banned sense.** It is a real value the
domain has: a fleet folded forward by the migration came from a coordinate no old event ever recorded,
and *"we do not know"* is the truthful answer. Filling it from `galaxy.home` would be inventing a
number, which the 2 → 3 hop's standard forbids. The migration writes `"from":null` explicitly.

The rule `SurveyCompleted` set holds throughout: **an event may carry a coordinate and an amount, never
a world.**

### `FutureEvents.kt` — the prediction mirror, and two silent traps

`FleetArrives(origin: Coordinates, ships)` becomes
`FleetReturns(target: GalaxyCoordinate, ships: Ships, cargo: Resources, at)`. `FutureEvent` is not
`@Serializable`, so the rename is free; the two exhaustive `when` sites in Compose
(`GameNotifications.kt:88`, `DebugSheet.kt:324-331`) break either way and the compiler finds them.

**Trap one — the tie-break ladder is derived, and a seventh member shifts three constants at once.**
`tieBreak()` at `:124-126` reads `ResearchCompletes -> BuildingType.entries.size`,
`AdaptationCompletes -> + 1`, `SurveyLands -> + 2`, and `FleetArrives -> Int.MAX_VALUE`. **Replace the
derived ladder with explicit integers** — builds `building.ordinal` in 0…99, research 100, adaptation
200, surveys 300, returns 400. The relative order is unchanged, so this is a pure refactor with no
behaviour change and it should land as its own commit; what it buys is that the next kind has somewhere
to go, and that a building added later cannot silently move three constants it has nothing to do with.
It also unpins the end of the ladder, which `Int.MAX_VALUE` had sealed.

**Trap two — `secondaryTieBreak()` returns `0` in its `else` branch.** Runs are the second multi-instance
kind, and a member that falls through compiles cleanly and produces a **non-total order**: the log and
the prediction then disagree only when two land on the same millisecond, which is exactly what a
check-in dispatching three runs to one system produces. The branch is mandatory and it must mirror
`applyEventsDueAt`'s key exactly.

`futureEvents` and `applyEventsDueAt` are two hand-maintained copies of one ordering — the comment at
`:100-102` says so. Both change or they drift.

### New verbs — the fifth and sixth

```kotlin
sealed interface StartRunResult {
    data class Started(val state: GameState) : StartRunResult
    data object Unsurveyed : StartRunResult        // you cannot price a hold you cannot see
    data object NotAValidTarget : StartRunResult    // home, an empty slot, or held by someone else
    data object NoSuchShips : StartRunResult        // the manifest exceeds the idle pool
    data object WindowTooShort : StartRunResult     // window < roundTrip + MINIMUM_STATION
}

fun startRun(
    state: GameState,
    target: GalaxyCoordinate,
    gathering: ResourceKind,
    ships: Ships,
    window: Duration,
    at: Instant,
): StartRunResult

fun buildShips(state: GameState, ships: Ships, at: Instant): BuildShipsResult
```

`(state, subject, at) -> sealed Result`, checks in the settled order — validity → requirements → cost →
construct job → `state.copy(…, eventLog += Started)`, exactly as `startUpgrade`, `startResearch`,
`startAdaptation` and `startSurvey` do.

**`startRun` takes three subjects where every other verb takes one, and that is the sheet's only
deviation from the settled shape.** It is owned rather than hidden: the target, the resource and the
window are three facets of one commitment, not three decisions about different things, and a verb that
took them one at a time would need a partial-commitment state that nothing in this game has.

### New pure rules — `FleetBalance.kt`

Its own top-level file beside the other four balance objects, with the standing declared in the header
the way `SurveyBalance`'s is. `PlaceholderBalance.kt:7` forbids scattering literals; shared curve
machinery (`checkedTimes`, `compound`) stays in `Curves.kt`, and every cost and hold routes through
`checkedTimes` — it *"throws rather than saturating"*, because a cost of `Long.MAX` is *"a wrong answer
wearing a plausible face"*, and it has already happened once in this codebase.

```kotlin
object FleetBalance {
    fun distanceUnits(from: GalaxyCoordinate, to: GalaxyCoordinate): Int
    fun flight(type: ShipType, units: Int): Duration
    fun roundTrip(type: ShipType, from: GalaxyCoordinate, to: GalaxyCoordinate): Duration
    val WINDOWS: List<Duration>
    fun windowsFor(from: GalaxyCoordinate, to: GalaxyCoordinate, ships: Ships): List<Duration>
    fun danger(from: GalaxyCoordinate, world: World): Int
    fun cargo(world: World, gathering: ResourceKind, ships: Ships, station: Duration, danger: Int): Resources
    fun shipCost(type: ShipType, alreadyOwned: Int): Resources
}
```

**Two functions called "distance" will coexist and disagree, deliberately.**
`SurveyBalance.distanceUnits` is system-to-system with a galaxy hop at 250 units, because a probe is
aimed at a star; `FleetBalance.distanceUnits` is the galaxy sheet's world-to-world metric with a galaxy
hop at 2,700, because a hold is filled at a world. Folding them would re-time every shipped probe
flight and redraw the reach band's hour marks, which is a change to shipped work bought for tidiness.
The cost is real, it is named in the file, and it is an open call in §9.

### `homeFor` — one clause

`GalaxyGeneration.kt:160-183` walks systems forward from a seeded start and returns the first world the
unaided species tolerates. Add one clause to the acceptance test: **the system must hold at least two
other worlds.** The fallback at `:182` is unchanged, so genesis stays total. `worldAt` is untouched, no
distribution moves, and the test seed's home does not move.

### Save — schema 8, migrating 7

`SCHEMA_VERSION` → 8, plus a `7 to { … }` entry in `MIGRATIONS`. **Non-optional even where it does
nothing:** `migratedToCurrent` reads a missing step as *"this build cannot get there"* and refuses the
save, which is exactly why the `6 to { root -> root }` identity hop exists at `:137` — *"a hop that has
nothing to do still has to say so."* Plus a comment block in the numbered ledger at `:58-67`.

The hop does five things, and **the third one needs a helper that does not exist**:

1. add `"ships": {"counts": {"SKIFF": 1}}` — the genesis grant, the same value a colony that never had
   a fleet would have had. No number invented and nothing to rescale, which is the 2 → 3 hop's standard.
2. add `"runs": []` — one absent key, the shallowest kind of hop, exactly like the probe's
   `5 to { root -> root.withState("surveys" to JsonArray(emptyList())) }`.
3. **remove `"returningFleet"` — and `withState` cannot do it.** `GameSave.kt:204-207` is
   `JsonObject(state + entries)`, which only ever *adds*; and the `Json` instance at `:78-82` sets only
   `encodeDefaults = true`, so `ignoreUnknownKeys` is **false** and a leftover key makes every legacy
   save decode as `DecodeResult.Failure("malformed save")`. **A `withoutState(vararg keys: String)`
   sibling is required and is the single most load-bearing line in this hop.**
4. **fold any non-null `returningFleet` into `runs`, because the frozen fixtures have one.**
   `VERSION_1_FULL`, `VERSION_2_FULL`, `VERSION_3_FULL` and `VERSION_4_FULL` each carry
   `"returningFleet":{"ships":{"CARGO":14}, "cargo":{…}, "origin":{"galaxy":2,"system":117,"position":9},
   "arrivesAt":"…"}` and `GameSaveTest` asserts on the migrated result. The fold maps `CARGO → SKIFF`,
   `origin.position → slot`, `arrivesAt → returnsAt`, `dispatchedAt = lastUpdatedAt`,
   `gathering = "METAL"`. **The keep rule is a whole-shape guard, not a coordinate guard** — the hop
   keeps the folded run only if **the coordinate is in bounded range AND `arrivesAt > lastUpdatedAt`**,
   and otherwise credits the cargo to the stock and drops the run. Both halves are required:
   `Coordinates` accepted galaxy 9999 and `GalaxyCoordinate` does not, *and* `FleetRun.init` requires
   `returnsAt > dispatchedAt`, which a fleet already due at the save instant violates. **A migration
   must not be able to throw** — a `require` firing inside a hop turns a legacy save into
   `DecodeResult.Failure` with no way back, which is worse than dropping a fleet no production build
   ever created. Stated in the ledger rather than left to the code.
   *This is also why §5 declines the cross-field pool invariant: a folded fourteen-hull fleet is over
   any ceiling this design would have imposed, and it should play rather than fail to decode.*
5. rewrite every `eventLog` entry whose discriminator is `"FleetReturned"`: `"CARGO" → "SKIFF"`, and add
   `"from": null`. Note this is **defensive rather than load-bearing** — no production build has ever
   appended one — and it is kept for the reason the `6 → 6` identity hop is kept: a hop that has nothing
   to do still has to say so.

Migrations are pure functions — `seedFor(root)` hashes `lastUpdatedAt` rather than drawing a seed, so
decoding the same file twice hands back the same state. Nothing here needs randomness, which is what
keeps it one entry.

### Notifications — two things break the moment runs are parallel

Not `core`, but this slice owns them and no proposal may ship without them.

- **`GameNotifications.kt:128` uses the constant id `"fleet-arrival"`.** Two simultaneous returns
  collide into one alert and one silently vanishes. It must key on the run.
- **`:65` partitions on `SurveyLands` alone** to protect the model-bounded kinds from iOS's 64-request
  ceiling, and with runs also multi-instance `bounded.size` stops describing the protected set. The
  trim order is a **content decision, not a bug fix**, and the sheet's proposal is: protect the
  model-bounded seven (six facilities plus the project), then returns, then probe landings — because a
  return carries resources that a full store can void and a probe carries information that does not
  spoil. **Davide's call.**

### What composability demands, as a checklist

1. `returnsAt` is stored on the run and fixed at dispatch. Every existing verb does this and says why
   (`StartSurvey.kt:41`, `StartResearch.kt:33`, `StartAdaptation.kt:35`): a Robotics Factory finishing
   mid-flight must not retroactively shorten anything. ✓
2. **`cargo` is fixed at dispatch too** — the same rule one step further: a mine level completing
   mid-flight must not retroactively enrich a run already out. ✓
3. Nothing derived from `from`/`to` enters the transition; only the instant. ✓
4. Duration is strictly positive — window ≥ 1h, station ≥ 20 min. A zero-duration job completing at
   `boundary` re-enters `advance` at the same instant and recurses forever. ✓
5. `advance` is `tailrec`. ✓
6. The fleet contributes **no per-hour rate** — only discrete deposits — so `accrued()`'s cap-on-time
   shape and the fine-unit discipline it exists for are untouched. ✓
7. `AdvanceTest`'s *"everything in flight"* fixture at `:155` already carries a `ReturningFleet`. It
   becomes three `FleetRun`s at three different instants, split at each boundary ±1 ms and on it
   exactly. ✓

**One more, and it is not about `advance`:** no backticked test name in `core/commonTest` may contain a
comma. It compiles on the JVM and the Kotlin/Native compiler rejects it outright — measured at 0.2.7,
where one such name took out four of five CI jobs.

---

## 6. What the sim must print before any of this is believed

A `printFleetReport()` after `printOpeningReport()`, two days, at both cadences already in `Main.kt`,
each run **twice — without and with fleets, in the same run** — the way the probe report already prints
`withProbes = false` then `true`. Three rules it inherits from this file's own mistakes:

1. **Kinds first, count second.** Round 8's opening scored *"median options: 5"* for the opening Davide
   called boring. A dispatch is one verb with many targets. `gather` and `build ships` join `censusOf`
   with their `Barrier`, and whether a refusal is `PRICE` or `SLOT` (every hull is away) is the single
   most informative thing the census will say about this mechanic.
2. **A third ledger.** `colonyBusy` and `probeBusy` are kept apart *"so the new verb cannot take credit
   for fixing a complaint it does not touch."* Add `fleetBusy`, and print the share of covered time each
   ledger is the **only** thing covering — a fleet that only flies while a build is running has bought
   nothing back, and no total can show that.
3. **The no-fleet column in the same run.** A gathering fleet moves every other reading in the harness —
   levels at 48h, the gate clock, blocker hours — so a run that measures idleness while also getting
   richer cannot say which change did what.

The rows that decide it:

**Read each baseline off the right report.** `printOpeningReport` prints twice, without and with probes
(`Main.kt:855-858`), and the two columns are different games. Without probes both ledgers read 95.83%
because there is nothing but the colony; **with probes — the shipped game — the colony reads 93.75% and
nothing-at-all reads 2.08%.** Putting 95.83% in the nothing-at-all row would let the fleet claim credit
for 94 points that the probe already took.

| Reading | 0.2.7 today | what it must do |
|---|---|---|
| Hours the **colony** had nothing in flight | 93.75% *(with probes)* | **unchanged** — the honesty check. If it moves, the reading is contaminated. |
| Hours with **nothing at all** in flight | 2.08% *(with probes)* | barely moves, and **this is not the reading that decides** — probes already took it there and Davide still called the game empty |
| **Fleet duty cycle** — ship-hours committed ÷ owned | — | **≥ 70% at four a day, ≥ 50% once a day.** The reading that decides. |
| Share of covered time the fleet is the **only** cover | — | high. If low, the fleet is flying during builds and has bought nothing. |
| **Spread of chosen distances**, and of chosen windows | — | **wide, and it is the §3.5 verdict.** If every dispatch goes to the same world, distance buys nothing and the map is decoration. |
| Fleet income as a share of **crystal** income | — | the binding row — see §4. State it separately from metal; the priced basket hides it. |
| Median work a check-in booked | 9 min | rises, without the check-in growing past five minutes |
| Building levels at 48h | 32 | **within a level or two.** Falls → the hull price is eating the colony; rises far → the fleet is the economy. |
| Robotics 4 reached | hour 33 | **not much earlier.** The gate is the game's spine. |
| Crystal sole-blocker hours, fortnight | 273 of 336 | falls, and **not to zero** |

Then the sweeps, in the shape `SurveyBalance.COST_METAL`'s own comment table takes — one row per
candidate, columns *building levels at 48h · dispatches · duty cycle · hours with nothing in flight ·
crystal sole-blocker hours · Robotics 4 at*:

- **`EXTRACTION_PER_HOUR` ∈ {10, 20, 30, 40}** — the number that decides whether this is a strategy
  layer or an economy rewrite. **Swept downward from the draft's 40**, for the two reasons in §4: the
  share was computed against a genesis colony 0.2.7 deleted, and against the priced basket rather than
  the chosen currency, where it reads 47% of crystal income rather than 16% of everything.
- **`shipCost` base ∈ {40, 80, 140} metal** — the number that decides the natural fleet size.
- **The §3.5 frontier band ∈ {flat, ×1.15/band, ×1.25/band}** — the number that decides whether the
  galaxy map is a strategy surface or a backdrop. Read it off the *spread of chosen distances*, not off
  total income.

**`HOLD_CAP` is not swept, because it is gone.** §1 has the arithmetic: at 480 the once-a-day player
earns 49% of the twice-a-day player's fleet income, and uncapped every cadence lands within 4%.

Read `shortHours` and tune against it; read `soleBlockerHours` afterwards and **never treat a difference
under ~50 hours as a signal** — a one-unit deuterium change once swung crystal's count from 58 to 200.

**One instrument caveat that will bite.** Every report except `printFirstSitting` checks in every three
hours, so all of them are **structurally blind to anything shorter than the gap** — and the 1h and 3h
windows are shorter. The opening arc has to be read off the first sitting or it is not measured at all.

**And one property test, so the largest risk is not a thing to remember.** `BalanceCurveTest` gains:
*the fleet's priced return per priced unit spent stays below a mine level's at every depth.* That
converts §9's first risk from a sweep somebody has to re-run into a test that fails when they forget.

---

## What it should feel like, to check next round

- **Hour one should end with a ship in the sky.** The first check-in already books seven completions
  inside ten minutes; what it does not have is a reason to open the Galaxy tab. Three neighbours you
  have been looking at since genesis, each rich in something different, and one free skiff.
- **The first cargo should be small and should still land like something.** A hundred and thirty metal
  against a colony making ninety an hour. Not a windfall — the first thing that ever arrived from
  outside the colony.
- **A `BLOCKED` row should stop being a wall in a second way.** At 0.0.17 it became a shopping list.
  Now it becomes a destination: *gravity 2.4 g, you tolerate 1.45 g — and its metal is 1.44.* You
  cannot stand there. You can still take from it.
- **The right answer should change between two consecutive check-ins.** If it is always "send
  everything to the nearest world on the longest chip", the window is decoration and this is a probe
  with a cargo hold. The reading that catches it is the spread of chosen windows and the spread of
  chosen distances across a run, not the idleness number.
- **Being away nine hours should not cost you anything**, and being present should still be worth it.
  The player who checks in four times a day and the one who checks in once should be within a fifth of
  each other on fleet income. If a round finds itself opening the app to *avoid wasting a ship*, the
  cap or the window ladder is wrong.
- **The fleet should never be the economy.** If a round finds the mines feeling optional, or every
  purchase suddenly affordable, `EXTRACTION_PER_HOUR` is too high. If skiffs sit unbought,
  the hull base is too high. **That is the number to move first** — not the curve, not the flight
  times, not the danger constant.
- **The adaptation ladders should still hurt to skip.** A player who has been gathering from `BLOCKED`
  worlds for a week and has not bought a single ladder level is the failure mode, and §3's arithmetic
  says it should not happen — the ladder's worlds and the fleet's worlds are different worlds. If it
  happens anyway, the arithmetic is right and the *perception* is wrong, and that is a screen problem
  before it is a balance one.

---

## 8. What was rejected, and why

### About the shape

**A shipless expedition dispatched by the colony, like the probe.** The cheapest answer by an order of
magnitude and the honest competitor to the whole sheet: no hulls, no pool, no tab, one verb and one
balance object. Rejected because it does not answer what was asked — Davide said *"manage some basic
fleets"*, and a shipless expedition is a third probe with a cargo hold. It also leaves Shipyard and
Fleets as placeholders, and **two of five tabs saying "nothing here yet" is a real part of what "feels
empty" means.**

**Shipping #6 and #7 as two releases, as the roadmap has them.** A shipyard that builds hulls with
nowhere to send them is worse than the empty tab it replaces. `status.md` says *"sequencing is the
agent's"*; §10 merges them and then re-slices along a different seam.

**A build queue on the colony.** Round 16 named it *"the lever nobody has pulled"* and it is a real
competitor: letting one visit book several levels raises progression per real-world day directly. Not
rejected on merit — it fixes the **colony's** idleness, which is a different number from the player's
attention, and it adds no decision, because booking three levels is the same tap pressed harder. It
should be measured against this, not instead of it. **If this ships and the colony's own idleness is
still the complaint, the queue is next.**

**Raising income.** Ruled out by Davide twice (rounds 13 and 14) and measured exhausted in round 7: at
60/30 a fortnight is metal-blocked 322 of 336 hours, and raising deuterium 12 → 21/h buys eight hours
out of 336.

**Capping parallel builds.** Rejected by Davide directly — *"I don't wanna to remove parallel build!"* —
and measured worse on every axis: one slot halves progress, locks out Research entirely, and still
leaves 83% of the window empty.

### About the hostility gradient

**A spatial term in the trait formulas.** §3(a) in full. It makes `worldAt` a function of player state
and kills the galaxy sheet's §7 promise; it moves four rows `GalaxyDistributionTest` pins; and because
richness is derived from hostility it makes your neighbourhood settleable *and worthless*, which is the
pillar inverted.

**Distance-biased jitter that preserves the marginal distribution.** A worse version of the above: the
same purity break, plus a property no test in the repo can state and no reader can verify.

**Widening the tolerance bands near home.** All of the above, plus it makes `verdictFor` depend on the
player's home coordinate, so the same world reads differently for two empires — a colonisation-era idea
smuggled into this slice.

**A "safe zone" of *n* systems.** A rule with an edge, and the edge is what players learn to game.

**Requiring a world to pass your tolerance bands before a fleet may visit it.** The gradient by another
name. It would put **98.2%** of the map out of the fleet's reach, and at hour zero the average home
system contains zero passable worlds — so the verb bought to fix the empty opening would not exist in
the opening.

### About the payout

**A "nothing" outcome, at any probability.** Direct correction to OGame's table, which returns nothing
18.6% of the time. **Roll the magnitude, never the existence** — and this sheet rolls neither.

**Rolling the magnitude.** Weaker, and still rejected: the galaxy sheet derives richness rather than
rolling it, for a reason that transfers exactly. A computable payoff is what makes the allocation a
decision instead of a pull.

**A hidden low-probability catastrophe — OGame's 0.33% black hole.** At thousands of expeditions it is
folklore; at three a day the player either never sees it, so it is not a pillar, or sees it once, loses
a week of fleet to something they were never shown, and stops playing.

**Deuterium in the payout.** §1. Cold worlds are common in the outer slots and are surveyed in the home
system at genesis, so deuterium would be gatherable from day one and the Robotics gate — which a third
of the census's refusals sit behind — would go soft.

**A payout split across all three resources by richness share.** Hands over a little deuterium on every
run, and flattens the target choice from a 2.67× spread into a 20–57% band.

**A payout indexed to the colony's own hourly income.** It makes the fleet a permanently fixed fraction
of what you already produce — a percentage bonus arriving on a timer, which is the texture of the idle
game being complained about. It also means the fleet can never help a player who is behind, because the
payout is a multiple of what they already make.

**A fixed extraction window, with distance as pure travel on top.** It makes three three-hour runs worth
three times one nine-hour run, so it pays the player who checks in every two hours over the one who
checks in twice a day — precisely what Davide refused.

**No hold cap.** §1: the longest chip strictly dominates, and *harmless to miss* becomes *rewarded for
missing*.

### About the ships

**Four buildable hulls now.** §2. Two of them differ only inside a combat model and one has nowhere to
go.

**One buildable hull, forever.** *Send everything, always* solves it inside a day, and the composition
Davide asked for would be a single number.

**Escorts, now.** Their entire behaviour without a combat model is `cover = escorts`, and against a
`danger` the card already prints, *"send exactly `danger` escorts"* is a lookup rather than a judgement.
Shipping them now would either change nothing — a row that lies — or force a combat stub into this
slice, which is slice #8's design call taken by the build.

**A seventh `BuildingType` for the Shipyard.** Contradicts Notion's six; adds a `Buildings` field, a
save key, three `PlaceholderBalance` branches, a `startUpgrade` requirement and a seventh Colony row on
a screen where six rows at the **measured 106dp** already fill a 393×852 phone almost exactly. It would
also shift `FutureEvents.tieBreak()`'s three derived constants at once. And it does not earn it: a
facility on the ×1.5 cost curve whose output grows at ×1.25 cannot stay comparable to a mine unless the
hull carries most of the cost — at which point the yard is decoration. **The hull curve is the ceiling;
the Shipyard is a tab.**

**Ship construction as a timed job.** §4. A fifth job kind, a fifth `FutureEvent` member, a sixth
notification id and a fifth tie-break, bought to put a second wait in front of the wait the mechanic is
actually about.

**Ship construction sharing the research slot.** Deletes the adaptation branch's only scarcity. The
adaptation sheet is explicit: *"give adaptation its own slot and the answer is always run both."* A
third claimant is the same mistake from a third side.

**A flat `Ships(skiff, hauler)` record matching `Buildings`' house style.** The ship set is scheduled to
grow twice by design, and a flat record would be edited by slice #8, by slice #10 and by every save hop
between.

### About the loop

**One run per world.** §1. It converts a probe into fleet capacity and destroys survey disappointment,
which the galaxy sheet protects in as many words.

**Per-system depletion counters.** OGame's ~10-per-day regeneration needs mutable per-system state, and
the galaxy is a seed plus what the player changed; a counter for every world ever visited is a save that
grows without bound.

**A second travel-time formula.** The galaxy sheet's §4 metric was settled two slices ago and left
unimplemented. Implement it.

**Targeting a system, like the probe.** `Survey.kt:6-10` argues a system because *"a survey that could
be aimed at slot 7 would raise the question of what the other fourteen slots then are."* The argument
inverts cleanly for a hold: slot 7 is where the metal is, and the other fourteen are the worlds you
could have gone to instead. Averaging richness over a system throws away the only thing that makes one
target differ from another.

**Fleets performing surveys.** Not rejected here — settled on 2026-08-09 and obeyed:
*"A probe is dispatched by the colony, needs no ship, and slice #7's fleets will never survey."*

**A recall, or a cancel.** There is no cancel anywhere in this app, and the cargo is fixed at dispatch,
so a recall would have to re-derive from a new commitment.

**Auto-repeat, or "relaunch with last settings".** That is the OGame bot, shipped by the developer. It
converts the one verb that was supposed to add a decision into a subscription, and it is the most-botted
action in the genre for exactly that reason. What replaces it is that a re-dispatch is **two taps** —
a chip and the pill.

**A two-stage return — bring home a payload, then spend a second timer converting it.** Hades' Star's
artifacts and Ikariam's capture points, and the strongest idea not taken: it defers the economic effect,
creates a decision *at arrival* rather than only at dispatch, and gives the returning-fleet strip a
payload worth drawing. Rejected for this sheet because it is a fifth noun and a sixth job kind on top of
four new nouns, and because the hull curve already does the bounding the deferral was buying. **It is
the first thing to add if the arrival turns out to feel like nothing.**

---

## 9. Left open, deliberately

- **The whole of §3 — how "close planets should be less hostile" is delivered.** The sheet recommends a
  fleet-facing `danger` and leaves the tolerance bands alone. The literal version costs the galaxy
  sheet's seed-purity promise, four pinned distribution rows and the probe's price, and it is a
  galaxy-sheet revision rather than a slice.
- **`EXTRACTION_PER_HOUR = 40`, and it must not ship unswept.** It is the single number that decides
  whether this is a strategy layer or an economy rewrite, and the row to read is crystal sole-blocker
  hours over the fortnight against today's 273 of 336.
- **Whether crystal scarcity is load-bearing or a problem to be solved.** Rounds 13 through 16 all call
  it the biggest open item; this mechanic relieves it deliberately. If it is meant to stay a wall —
  because it is what makes both research branches feel expensive — the hold should be smaller and the
  fleet should lean on metal, which changes §4 and nothing else.
- **Two hulls or one, and their names.** `SKIFF` and `HAULER` are the build's invention and become
  on-disk identifiers on the first merge. The four constants are free to rename **today and once**,
  because nothing has ever written one to disk; after the first hull a real player owns, a rename is a
  schema break exactly as `Fleet.kt:6-8` warns.
- **Whether an existing save gets the granted skiff.** The sheet says yes, on the argument the 500 metal
  is granted. The alternative — an empty pool and buy your way in — is honest and means an existing
  player meets the slice at a purchase rather than at a dispatch.
- **Whether permanent hull loss ships at all before slice #8.** The sheet defers it and says why. If it
  is wanted sooner it needs a rule, and it must be signposted before dispatch rather than hidden.
- **The notification trim order.** Two multi-instance kinds now compete for iOS's 64 pending requests.
  The sheet proposes builds and the project first, then returns, then probe landings. That is a content
  call.
- **May a run gather at a `Settleable` world** — strip-mining the thing you would rather colonise?
  Today's proposal allows it; only `Home` and `Occupied` are refused. It becomes a real tension the day
  slice #10 lands and it is cheaper to decide now than to discover then.
- **Which distance metric moves.** `SurveyBalance` prices a galaxy hop at 250 units and the galaxy sheet
  at 2,700. Both will be live and both will be called "distance". The sheet is the design and the
  probe's constant is the placeholder, so eventually the probe's should move — at the cost of re-timing
  every shipped probe flight and redrawing the reach band's ruler, which is why not in the slice that is
  trying to fix the opening.
- **Whether `FULL_PRICE_LEVEL = 9` still means what its comment says.** `Curves.kt:46-48` converges the
  opening discount on a landmark Davide defined as *"the moment you can have the first expedition"* —
  Robotics Factory 4, at hour 33. After slice 1 the first run leaves at hour one. The landmark may still
  be right for a different reason, but the sentence is now literally false and somebody has to decide
  which half to keep.
- **What raises the storage cap** — open since 0.0.11 (`PlaceholderBalance.kt:10-11`) and now closer to
  mattering. `Advance.kt:160-164` clamps a deposit at `CAP_FINE` **silently, with no event and nothing
  renderable**, and `AdvanceArrivalTest:73` pins that behaviour. The cap is 10,000,000 and the fortnight
  closes under 300,000, so it binds nothing today — but a run is the first thing in the game that can
  deliver a lump larger than an hour's income, and if gathering is the point then a silently voided haul
  is the one place value can vanish unseen. Raise it or record it; do not work around it.
- **The stale yield weights.** `GalaxyBalance`'s 51 / 33 / 16 come from a 698 / 224 / 72 reference colony
  that today's curves produce as **1,035 / 262 / 63**, so `yieldScore` over-weights deuterium by about
  half. This design reads the three richnesses directly and never `yieldScore`, so it dodges the stale
  mix — **but the dispatch list must sort by what a run would actually bring home, never by
  `yieldScore`**, which is the obvious and wrong thing to sort by.
- **All player-facing copy.** Both `UnbuiltTabScreen` strings are marked PLACEHOLDER because *"what a
  screen says to the player is content, and content is Davide's"*, and this sheet writes a dozen more
  than it deletes: the danger line, the window chips, the empty states, and whatever a hull is called on
  screen.

---

## 10. The slicing plan

Four slices, ascending in nouns — **window, then danger, then fleet size, then hulls** — one new thing
to learn per slice, which is the check-in budget spent over time rather than all at once. Each ends
playable; each ends measured.

### Slice 1 — the run, on screens that already exist

The whole loop end to end with **one granted skiff and no way to buy a second**, so it introduces
exactly one noun: the window.

`Ships`, `FleetRun`, `startRun`, `FleetBalance`, the `Coordinates` fold, the `advance` branch and
`tailrec`, three events, the `FutureEvents` rewrite including the explicit tie-break ladder and the
mandatory `secondaryTieBreak` branch, save schema 8 with `withoutState` and the fixture fold, the
notification id fix, the `homeFor` clause. Client side: a **dispatch sheet raised from a Galaxy world
row**, and the existing `FleetStrip` on Colony showing the next return. No new tab, no new component
beyond the sheet.

**Done means:** a new colony can send its skiff to a neighbour in its first check-in and watch it come
back; `AdvanceTest`'s everything-in-flight fixture carries three runs and the property holds at every
boundary ±1 ms; every frozen `GameSaveTest` fixture migrates, including the four that carry a
`returningFleet`; and `printFleetReport` prints the with/without pair at both cadences with the third
ledger and the duty cycle. A balance-log round quotes it.

### Slice 2 — danger, and the frontier

The `danger` axis, the 10%-per-point penalty, and the world row's second reading. Still one skiff — so
this slice is **purely about where you send it**, which is the cleanest possible test of whether target
choice is a decision at all.

**Done means:** a world card states its danger and its haul before you commit; the near/far crossover in
§1's table reproduces in the sim; and the census can say whether the player's chosen distances actually
spread with the gap ahead or collapse onto one target.

### Slice 3 — the fleet has a size

`buildShips`, the compounding hull curve, and the **Shipyard tab** — hulls for sale, the pool, and what
is away. The **Fleets tab** — one card per run in flight, with the phase derived in presentation from
`dispatchedAt + flight` so `core` stores one instant rather than three. `MainScaffold` gains two
parameters and `UnbuiltTabScreen` loses its last caller.

**Done means:** both `pendingWork` strings are `null`, `unbuilt_tab_shipyard.png` is retired, several
runs can be in flight at once without an alert colliding, and the sweep in §6 has run and one number has
moved on the strength of it.

### Slice 4 — the hauler, and the composition

The second hull, speed against hold, and the manifest picker. Plus the returns ledger on the Fleets tab
— a fold over `Event.FleetReturned`, which is the first player-facing use of the event log and costs no
state at all.

**Done means:** the crossover table in §2 reproduces in the sim; the census shows the mix genuinely
moving with the window and the distance rather than settling on one composition inside a week; and the
balance-log round can answer the question this whole sheet exists to answer — **not whether the hours
are covered, but whether the right answer changed between two consecutive check-ins.**
