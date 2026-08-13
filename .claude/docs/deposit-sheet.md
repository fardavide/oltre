# Deposit decision sheet — 0.9

Written by the build, 2026-08-13, on Davide's report after playing 0.8.0:

> *"I noticed planets never exhaust resources, and that seems like OP, and makes the game boring, as
> I can just build many ships, and keep sending them to the same planet. I think each planet should
> have a resources cap, and, when collecting resources from it, its resources should be exhausted.
> E.g. got 1000 metal (cap), I collect 800, and then it has 200 left. The replenishment should be
> slow, I would expect perhaps 5% of the total per day, so I don't have a good reason to go back to
> the same planet anytime soon, but I'm forced to explore others, requiring me also to go further and
> so requiring me to upgrade my stuff."*

**Every line here is his to overrule.** Same shape as the 0.1 research sheet, the 0.2 galaxy sheet,
the 0.3 adaptation sheet, the 0.4 fleet sheet and the 0.8 exploration-rewards sheet: answer the
question, name what was rejected, say what it should feel like so the next round can tell.

This sheet **reverses one ruling in `fleet-sheet.md` §8** — per-world depletion counters, rejected
there — and **declines one proposal of Davide's own**, the ship hold ceiling, on the strength of his
own absence rule. Both reversals are argued in full below rather than asserted.

---

## Davide's calls, 2026-08-13 — binding on this sheet

| Question | His answer |
|---|---|
| Do planets run out? | *"each planet should have a resources cap … its resources should be exhausted"* |
| How fast do they refill? | *"perhaps 5% of the total per day"* — confirmed against the measured steady state |
| How long should the doorstep last? | *"a couple of days, but we should consider only a few planets are reachable early game. We should involve the user to explore further"* |
| Cliff or taper? | *"Decide for me"* — the build chose the cliff; §2.3 |
| What sizes the deposit? | Richness × **danger** — his first answer, withdrawn on the build's advice and then **reinstated**, because his own rule below is only satisfiable with it. §2.2 |
| How big is a deposit, in play? | *"I would expect a regular ship to take two rounds or a whole day to deplete a planet, the ship capacity (a basic one) is never more than the planet resources"* — **this rule fixes the constant**, §2.5 |
| Which worlds show what is left? | Every surveyed world, whether or not you have sent a ship |
| Should ships have a hold ceiling? | Raised by him, **withdrawn on the measurement** — *"Drop the ceiling, upgrade the rate instead"* |
| Does the rate become upgradeable? | Yes, and in this milestone — *"just mind another session is working on ships prices"* |
| The steady state | *"I tend towards the first option, but please don't allow me to screw up"* — §9 is that guardrail |

---

## The one-sentence version

**A world is a finite vein rather than a tap**, so the question a dispatch asks stops being *how long
will I be gone* and becomes *which of my worlds still has anything in it* — and because a vein refills
at 5% a day, how much of the galaxy you have surveyed becomes a permanent income stat rather than a
one-off errand.

---

## 0. What is broken, measured

Every number below is computed from `FleetBalance`'s own integer arithmetic, in the same order of
operations, and is reproducible from `.claude/tools/` — see §10 for the harness this owes the sim.

**A world is infinite today.** `FleetBalance.cargo` reads `richness`, `station`, `hulls` and `danger`
and nothing about history: the ten-thousandth run to a world is worth exactly the first. So the
optimal play at 0.8.0 is to find the single best world inside your window and never look at the map
again — which is Davide's complaint, stated as arithmetic.

**And the fleet is now the economy.** Round 22 measured a fleet-first player delivering **268%** of
their own colony's crystal. That was accepted as a cost at 0.8.0, on the grounds that the hull curve
bounds the fleet — but the hull curve bounds *how many hulls*, and with an infinite world it does not
bound *how much a hull is worth*. The deposit is the missing bound, and it binds where the complaint
is rather than where the wallet is.

---

## 1. Ship capacity as a hold ceiling — raised, measured, dropped

Davide's follow-up: *"How many resources I can get depends on my ship/s. If my ships can carry 500, I
can take 500 from any planet, as long as it has enough resources. If my ships can carry only 50, I
will be able to get only 50 also from the most hazardous planet."*

Genre-standard, and it does not survive contact with this game's rules. Two measurements.

### 1.1 A ceiling flattens the frontier into the doorstep

Four skiffs, richness 1.00, metal landed at home:

| target | 1h | 3h | 6h | 12h | 24h |
|---|---|---|---|---|---|
| **today** doorstep (own system) | 152 | 632 | 1,352 | 2,792 | 5,672 |
| **today** neighbour (1 system, 1 hazard) | 136 | 952 | 2,176 | 4,624 | 9,520 |
| **today** frontier (next galaxy, 2 hazards) | — | — | — | 1,760 | 9,680 |
| **ceiling 500/skiff** doorstep | 152 | 632 | 1,352 | **2,000** | **2,000** |
| **ceiling 500/skiff** neighbour | 136 | 952 | **2,000** | **2,000** | **2,000** |
| **ceiling 500/skiff** frontier | — | — | — | 1,760 | **2,000** |

Every target and every window at or above six hours converges on one number. The frontier premium
bought at 0.7.2 — danger paying +35% a point — stops existing the moment the ceiling binds, because a
ceiling is indifferent to *why* the hold filled. It would undo the previous sheet inside one release.

### 1.2 And it taxes the player who is away longest

Metal per day, four skiffs, always dispatching to the best legal target:

| cadence | today | ceiling 500/skiff |
|---|---|---|
| every 6h | 8,704 (0.88×) | 8,000 (**4.00×**) |
| every 12h | 9,248 (0.94×) | 4,000 (**2.00×**) |
| every 24h | 9,856 (1.00×) | 2,000 (1.00×) |

`SurveyBalance` states the rule this breaks in as many words — *"it would tax the player who is away
longest, which is the one thing the check-in loop must never do."* This is not a tuning problem that a
larger capacity fixes: **any binding ceiling is arithmetically a tax on absence**, because the frequent
player collects it several times a day and the absent player collects it once.

### 1.3 What the want actually is, and where it goes instead

Strip the mechanism from the want and it reads: *my fleet's capability should be what determines the
haul, and I should be able to improve it.* Half of that is already true — hull count is a linear
multiplier on `cargo`. The missing half is that there is **no way to make a hull better, only to buy
another one**. So the want lands as an upgradeable extraction rate (§5), not as a ceiling.

**Davide's call, on being shown 1.1 and 1.2: drop the ceiling.**

---

## 2. The deposit

### 2.1 Two stocks per world, in resource units

A world carries a **metal deposit** and a **crystal deposit**, independently drained. No deuterium
deposit: `Fleet.kt` refuses to pay a run out in deuterium and gives the reason — deuterium is the
Robotics gate's currency, and a third of all refused actions in the interaction census sit behind that
gate. A stock nothing can draw from would be a field that exists to look symmetrical.

### 2.2 The cap carries the same multiplier the rate does

```
cap(kind) = BASE_PRICED × richness(kind) × (1 + 0.35 × danger) / price(kind)
BASE_PRICED = 1,450        price: metal 1, crystal 2        danger = hazards + distanceBand
```

so a plain doorstep world holds **1,450 metal** or **725 crystal** — equal in the game's own 1 : 2 : 3
basket, exactly as `FleetBalance.cargo` already prices a hold — climbing to 3,988 metal on a
two-hazard world in another galaxy at maximum richness.

**This reverses a correction the build made and Davide accepted, and the reversal is the load-bearing
argument of the sheet.** His first answer was richness × danger; the build talked him out of it on two
objections; the second is sound and the first is not.

1. ~~**Distance would be paid twice.**~~ **Wrong, and it matters that it is wrong.** That argument is
   `FleetBalance`'s, about deleting `FRONTIER_PERCENT` at 0.7.1 — a *second multiplier on an unbounded
   rate*, where the two compound. A stock is not a rate. Whether what lands is `rate × time × danger`
   or `cap × danger`, the danger factor appears **exactly once** in what the player receives.
2. **A home-relative stock does not survive multiplayer.** This one stands. `distanceBand` is measured
   from *your* home, so two players sharing a world would disagree about how much is in it. Accepted
   as debt, named in §11, and the fix if it ever bites is to freeze the band against the world's
   galaxy rather than the observer's home.

And there is a third argument, which is the one that actually decides it: **the cap and the rate must
carry the same multiplier, or the mechanic is not uniform.** Time-to-strip is
`cap / rate`; give the two different multipliers and that ratio varies by target, so *how long a
planet lasts* becomes a function of where it is. Davide's rule — one basic ship, two rounds or a whole
day, never more than the planet holds — is a statement about time-to-strip, and it is only true
everywhere when the multipliers match. Measured, one skiff on a 24h run:

| | smallest base cap that never overflows | doorstep takes | "two rounds" |
|---|---|---|---|
| **cap × danger** | **1,418** | 0.99 days | **2.0 runs at 12h** ✓ |
| cap × hazards only | 2,043 | 1.45 days | 2.9 runs at 12h ✗ |

So the sentence the whole mechanic can be taught in — **one ship strips one planet a day** — exists
only in the top row. A far hazardous world is deeper *and* faster in the same proportion, which means
it yields more per run and takes the same time to exhaust, and travel time is still what it costs.

### 2.3 The cliff, not the taper — and why the reason is not taste

A run extracts at the full rate and is clamped by what remains. 1,000 in the ground, a run that would
lift 800, 200 left. Davide's own model, and the build was asked to choose. Three reasons it is right
here rather than merely simpler:

1. **A continuous taper needs an exponential.** `core` is integer-only so that `advance` gives the
   identical answer on the JVM, on Kotlin/Native and on the server — a floating-point yield curve is
   a different galaxy on the phone than on the desktop.
2. **The sheet states the figure before the tap.** Every verb in this game prices itself exactly
   before it is committed; the cliff is the only one of the three candidates that stays exact.
3. **The dial that matters is the cap, not the curve.** The savagery of the drop-off is set by
   `BASE_PRICED` and the regen; a taper spends a constant to soften something two existing constants
   already control.

**The cliff's real defect is named rather than denied:** a run sent to a nearly-dry world spends its
whole window lifting almost nothing. That is what §6's row reading exists to prevent, and it is the
reason the remaining stock is shown on *every* surveyed world rather than only on ones you have
touched. If the map still ends up littered with worlds nobody will ever visit again, the first thing
to try is a **dispatch-time taper** — the rate scaled by how drained the world already is, fixed at
dispatch like everything else — which stays integer and, unlike a continuous taper, happens to reward
the absent player rather than tax them.

### 2.4 Refill: 5% of cap per day, linear

Twenty days from empty to full. Applied lazily rather than ticked (§3.2), which is what makes it free.

**It is not a rate you notice; it is a floor you settle onto.** Measured — four skiffs, twelve
doorstep-grade worlds, spreading one hull per world:

| | day 1 | day 7 | day 14 |
|---|---|---|---|
| metal per day | 5,408 | 864 | 864 |

That 864 is `0.05 × total mapped stock`, and it is the whole mechanic in one number: **once your
worlds are stripped, your fleet earns five percent of everything you have ever surveyed, per day.**
Which is the point — exploration stops being an errand and becomes an income stat — and it is also the
thing most likely to be wrong, which is why §9 exists.

### 2.5 Sizing: `BASE_PRICED = 1,450`, derived rather than chosen

Davide, asked for a number, gave a rule instead: *"I would expect a regular ship to take two rounds or
a whole day to deplete a planet, the ship capacity (a basic one) is never more than the planet
resources."* **The rule fixes the constant.** One skiff on a 24h run to a doorstep world spends 1,418
minutes on the surface and lifts exactly 1,418 priced units, so:

- **never more than the planet holds** → base ≥ 1,418, at every target and every window (§2.2's table)
- **two rounds or a whole day** → at 1,450 the doorstep takes **0.99 days**, or **2.0 runs at the 12h
  window**. Both halves, to two decimal places.

One skiff, the three worlds genesis surveys, 6h cadence:

| cap | d1 | d2 | d3 | d4 | d5 | d6 | steady |
|---|---|---|---|---|---|---|---|
| 1,000 | 1,352 | 1,352 | 596 | 150 | 150 | 150 | 150/day |
| **1,450** | 1,352 | 1,352 | 1,352 | 942 | 216 | 216 | 216/day |
| 2,500 | 1,352 | 1,352 | 1,352 | 1,352 | 1,352 | 1,352 | 375/day |

**1,000 was Davide's first answer and is rejected on measurement**, which is the clearest case in this
sheet for why a number gets swept before it ships. At 1,000 the deposit is smaller than a single
skiff's day, so it binds on essentially every dispatch a real player makes — and when the deposit
binds, *nothing else does*. Cargo as a multiple of a full deposit, four skiffs, doorstep world:

| base | 1h | 3h | 6h | 12h | 24h |
|---|---|---|---|---|---|
| 1,000 | 0.15 | 0.63 | **1.35** | **2.79** | **5.67** |
| **1,450** | 0.10 | 0.44 | 0.93 | **1.93** | **3.91** |

Every bolded cell returns the identical figure. At 1,000 that is three of five rungs, so **the window
ladder and the hull stepper stop changing the answer** — two controls the last two slices were spent
building, made ornamental by a constant. At 1,450 the clamp starts exactly where Davide's rule puts
it: past a day, or past one hull's worth of day.

What §9 must still watch: a single hull needs **20 metal deposits — about four systems — surveyed to
run indefinitely**. Four hulls need roughly sixteen systems. That is affordable (a probe is 150 metal,
flat, and probes fly in parallel), but it is a lot of *taps*, and the 5–10 minute check-in rule is
what it would be spent against.

---

## 3. Where the state lives — and the objection this reverses

`fleet-sheet.md` §8 rejected exactly this feature: *"Per-system depletion counters. OGame's ~10-per-day
regeneration needs mutable per-system state, and the galaxy is a seed plus what the player changed; a
counter for every world ever visited is a save that grows without bound."*

**The first clause is conceded and the second is answerable.** Mutable per-world state is genuinely
what this costs, and `GalaxyState` already carries two such sets — `surveyed` and `ownership` — for the
same reason: they are what the player changed. The unbounded save is the part that turns out not to
follow.

### 3.1 Sparse, and pruned at full

```kotlin
@Serializable
data class WorldDeposit(
    val at: GalaxyCoordinate,
    val metalFine: Long,
    val crystalFine: Long,
    val asOf: Instant,
)
```

held as `GalaxyState.deposits: List<WorldDeposit>` — a list of records rather than a map, for the
reason `ownership` is already a list: JSON cannot use a structured object as a map key without
`allowStructuredMapKeys`, which changes how the *whole* save encodes every map to buy an unreadable one.

**An absent entry means a full world.** So an entry exists only for a world that is not yet back to
full, and once it is, it is dropped. A full refill takes at most twenty days, which bounds the list at
*worlds harvested in the last twenty days* — a player dispatching eight runs a day to distinct worlds
holds at most 160 entries, about 16 KB. Bounded, and bounded by a rule the player can feel rather than
by a cap somebody chose.

Pruning is safe to do inside `advance` **because it is monotone**: a world that is full at `t₁` is full
at `t₂`, so pruning at the end of a span gives the identical result whether or not the span was
subdivided. It therefore composes, and it belongs exactly where `withoutSpentWatch()` already sits —
*"the one thing settled after the span rather than inside it"* — as a second such settlement.

### 3.2 Regeneration is computed, never ticked

`remaining(kind, now) = min(cap, storedFine + cap × 5 × elapsedMs / (100 × 86_400_000))`

Fine units for the reason `Resources` gives — `1 unit = 3,600,000 fine`, so accrual is exact integer
arithmetic rather than truncation — and at a 1,450 cap that is 3.02 fine per millisecond, comfortably
inside the integer floor. `advance` reads nothing here and writes nothing except the prune: a deposit
moves only when a run is dispatched, so the mechanic costs the simulation nothing per span.

**Stored remaining is clamped to the current cap on read.** Not hygiene — it is what keeps
`BASE_PRICED` a number Davide can still move after this ships. Lower the cap and every save is
instantly consistent; without the clamp a rebalance would need a migration.

---

## 4. When the deposit is debited: at dispatch

`startRun` computes the cargo the ships could lift, clamps it to what the world holds *at that
instant*, debits the world and fixes the clamped figure on the run.

This is the rule every other verb already follows, one step further. `FleetRun` states it: *"a mine
level completing mid-flight must not retroactively enrich a run already out."* Debiting on arrival
would mean two runs dispatched in one check-in both see a full world and both take it — a duplication
bug with a narrative excuse.

Three consequences, all deliberate:

- **The sheet's figure is true.** What it says before the tap is what lands, because the clamp has
  already happened.
- **The second run of a check-in sees the first one's hole.** Correct, and legible.
- **Refill during the stay is not collected.** At 5% a day a 24h window regenerates 72 units on a
  1,450 cap, below the noise of any real run. Named as a simplification rather than discovered later.

A world with **nothing at all** in the chosen resource refuses the dispatch — a new
`StartRunResult.Depleted` beside `Unsurveyed` and `WindowTooShort`, which the screen renders as a
refusal with a countdown, in the idiom the unsurveyed refusal already spends. A *partial* world never
refuses; it clamps, and says so.

**Note how narrow that makes the refusal.** A 1,450 deposit regenerates a whole unit every twenty
minutes, so exact zero survives about a third of an hour. `Depleted` is a real state and worth
building — a second run in one check-in reaches it — but it is not where this mechanic lives. **The
clamped offer is.** A build that spends its care on the dry screen and treats the clamp as an edge
case has it exactly backwards.

### 4.1 The absence tax the deposit reintroduces, and its answer

The deposit is a per-run ceiling for anyone who concentrates. Four skiffs, twelve worlds, 1,450 cap:

| cadence | spread 1 hull per world | whole fleet on one world |
|---|---|---|
| every 6h | 5,408 | 5,408 |
| every 12h | 5,584 | 2,900 |
| every 24h | **5,672** | **1,450** |

Spreading is absence-neutral — the once-a-day player is fractionally *ahead*, which is where 0.4's
measurement left it. Concentrating hands the once-a-day player 26% of what they should get.

**So the mechanic creates a real skill and a real trap in the same stroke.** The skill is worth having:
splitting a fleet across worlds is a decision the game does not currently contain, and `startRun`
already permits it — several runs, several targets, no one-per-target rule. The trap is not acceptable
undiscovered, and §6 is where it is defused: the sheet must say, before the tap, that this world cannot
fill these ships.

---

## 5. The rate ladder — and the correction that comes with it

Davide approved shipping the upgradeable rate in the same milestone, on the build's framing that it is
the counter-move to depletion. **That framing was wrong and is corrected here.**

A rate multiplier makes a hull lift more per hour. Where the binding constraint is *stock*, it buys
nothing at all — it drains the same vein faster and makes §2.5's six-systems-per-hull worse, not
better. It is relief only where the binding constraint is *time*:

- **the frontier at shorter windows.** A 12h run to the next galaxy is 9h20 of travel and 2h40 on the
  surface — hard time-bound, nowhere near any deposit. Doubling the rate takes that run from 1,760 to
  3,520 metal and turns the frontier from a 24h-only proposition into something a 12h absence can use.
- **the short rungs generally**, which is where a player who checks in often lives.

So the honest statement of what it buys: **it does not soften the wall, it widens the map you can reach
before you hit it.** That is still worth shipping here, and it is still the answer to *"power up my ship
so it can gather more resources in the same time"* — but the real relief valve for depletion is
**survey throughput**, and §9 names it as the dial to reach for first if the wall lands too hard.

**Shape: a fourth applied technology, not a new verb.** It reuses `startResearch`, the single
empire-wide slot, the ×1.5 cost curve, the opening discount, the shortlist and the Research screen. One
enum constant — free on disk, per `Research.kt`'s own note — and one field on `Research`, in a schema
hop this milestone is taking anyway. A Shipyard-side hull tier was the alternative and is rejected in
§8: it needs new state, a new verb and a second progression axis to balance, to deliver the same
multiplication.

It competes for the one research slot, which is the point. `AdaptationBalance`'s rule holds: *"give
adaptation its own slot and the answer is always run both."*

Name and magnitude are open — §11. `PROSPECTING` is the build's placeholder; the curve should be the
same shape as `EXTRACTION`'s with the magnitude swept, not chosen.

---

## 6. What the screens must say

Design's brief, and the whole of it is *make the clamp visible before the tap*.

> ### ANSWERED by Claude Design, 2026-08-13 — "A World Runs Out", second pass
>
> Both decisions came back, and the second one is larger than the question that asked it.
>
> **Decision 1 — a new line, *and the richness pair leaves the header*.** Design took the argument
> behind option (b) and rejected its slot: two labelled fractions are 33 characters into a
> 30-character slot that cannot wrap, and the case that overflows is *both deposits drained*, which is
> the case the reading exists for. So the stocks **replace** the richnesses rather than joining them —
> *"richness on a row was added for the fleet in 0.4, and the fleet no longer reads it: the cap is
> richness times the danger multiplier, and strip time is now the same everywhere, so what a run
> brings home is the stock and nothing else."* Three consequences, all simplifications: the header's
> middle slot returns to **the verdict word on all six verdicts**, so there is one row shape instead
> of two and `Settleable` stops being a special case; the `Blocked ·` lead retires and the block line
> becomes purely the requirement; and the card grows by exactly one line, which is the growth
> `GalaxyScreenBehaviourTest.kt:292` already pins. Richness survives on the sheet's chip.
>
> **Decision 2 — `full · 620/1,798 · empty`.** A fraction, reusing the app's own `84/163 fields`
> idiom, with a *word* at each end: **roughly 98% of rows have never been touched**, and `full` keeps
> an untouched galaxy reading as a shape the eye skips rather than thirty figures it must compare. The
> denominator is also the only place the cap is ever visible on the map. A fill bar was drawn and
> dropped — it says what `full` says, and it would be the first element on a world row that is not a
> number or a word.
>
> **The deposit line is present exactly where a run is legal** — absent on `Unsurveyed` (a hold cannot
> be priced from a world nobody has looked at) and on `Home`/`Occupied` (a run there is refused).
>
> **The clamp does not restate the figure.** The headline figure already *is* the deposit; what marks
> the state is the slot beside it, reading `the whole deposit` where an unclamped run reads
> `449 each` — one token, in a slot that already exists. Then the clause under the stepper, in the
> shape this sheet asked for: *"3 skiffs empty it. The 4th brings nothing."* Both notes are **earned
> rather than standing**: a clamp with no remedy — the fewest hulls that fit inside the shortest rung
> — shows the figure and stops.
>
> **The legs line gains a fourth segment**, and it is the invariant made visible without a word of
> copy: `out 10m · on station 11h 40m · working 6h 03m · home 10m`. Because the vein and the rate
> carry one multiplier, `working` reads the same on the doorstep as in the next galaxy — *"the
> teachable sentence is never written down."*
>
> **The dry world keeps its whole sheet** — chips, stepper, ladder — and loses only the figure, which
> becomes a countdown to the hold *this* offer would lift. Not a refusal: a new `waiting` mode. Design
> found the reason the controls must stay live, and it is the strongest thing in the handoff: because
> the vein and the rate carry one multiplier, a full fleet's ask is about the size of the vein, so
> "four skiffs at 6h" is 18d 13h away — *"exactly the `full again` you ruled out"* — while the same
> world is worth visiting in 2d 04h for one skiff at 3h. **The countdown is only honest because the
> offer above it can move.**
>
> **The duration format**, which is the work item question 3 was hiding, in three tiers of which only
> the top is new: `18d 13h` at a day and up (hours zero-padded, days never), `4h 20m` from 1h to 24h
> (`toPaybackLabel` unchanged), `00:19:41` under an hour (`toCountdown` unchanged). Two units, never
> three.
>
> **Vocabulary, closed:** `full`, `empty` and a fraction are the row's whole vocabulary, and `deposit`
> is the one noun, used only where there is prose room. Never written — *exhausted, depleted,
> stripped, mined out, reserve, vein, left, yield*, or any rate of refill.
>
> **One disagreement, logged and not resolved:** §6.5 says the refill rate never appears, and Design
> accepts that on the row and on the sheet — but notes the dry sheet carries the cap only implicitly,
> through the fraction on the chip, and *"if a player is meant to learn that a hazardous world is a
> deeper world, the deposit's cap is the only place that is legible."* Davide's call; it costs nothing
> until the dry sheet is built.

1. **Every surveyed world row carries its remaining stock, and where it goes is genuinely open.**
   Davide's call that it appears at all; the placement is Design's, and the build got it wrong once
   already. **The row has no universal reading line.** `WorldList.kt:371-375` gives the richness pair
   and the hazards/reach line to `Blocked` and `Barren` only; `Settleable`, `Unsurveyed`, `Home` and
   `Occupied` show a verdict *word* in that slot, and a `Settleable` row carries nothing but a prose
   note — while `isRunnable()` (`WorldList.kt:379-389`) makes `Settleable` a legal dispatch target.
   So the worlds a player most wants to strip are the ones with nowhere to put the figure. Two shapes
   are on the table and neither is the build's to pick:
   - **a new line**, which wraps and therefore costs the compact width nothing —
     `GalaxyScreenBehaviourTest.kt:292` already pins *"the blocked card grows by a line rather than
     dropping one, and no string changes"*;
   - **the header slot the richness pair holds**, on the argument that after depletion the two numbers
     that price a hold *are* the stocks. The header's children are all `maxLines = 1, softWrap =
     false`, so this one has a hard budget of roughly thirty characters against today's twenty-two.

   Whatever the shape, the figure must be **remaining against cap** and not a bare number. Two worlds
   both reading "120 metal" can be a fifth and a twentieth of their veins, and a bare figure makes an
   untouched world indistinguishable from a stripped one — which is precisely the comparison the whole
   reading exists to support.
2. **The clamped offer is the common state, and the marginal hull brings exactly zero.** Under the
   cliff there is no sharing: at 1,240 left on a doorstep world at 6h, two skiffs lift 838, three lift
   1,240, and the fourth lifts **nothing at all** while being locked away for the whole window. That
   is deterministic arithmetic stated before the tap — the app's own voice — rather than a scold, and
   it is computable as `ceil(remaining / perShipCargo)`. The sheet already prints the haul, so the
   missing fact is not the number but that the number is a **ceiling** rather than a yield.
3. **The window ladder becomes a judgement.** Four skiffs at danger 2 drain a 1,450 deposit in 3.6
   hours, so a 24h window on that world wastes twenty of them. §6.2's remedy — send fewer — does not
   exist at one hull, where `atFewest` is already true; there the shorter rung is the *only* remedy,
   which is why this cannot be folded into the sentence above. The ladder needs no new control, only
   the rung that just empties the vein made legible.
4. **A dry world is a refusal with a countdown — and it is a rare screen.** At 1,450 the world
   regenerates one whole unit every 20 minutes, so `Depleted` is reachable for about a third of an
   hour after a strip and essentially never survives a check-in gap: the ladder's own default rung is
   3h. The player who meets it is the one mid-tap who has just emptied the world with a previous run.
   The countdown should therefore point at **the first hold worth sending** — computable with no new
   constant, because the threshold is the sheet's own already-resolved offer — and rendered in the
   existing `RefuseActionUiState.Waiting` shape, which is one of exactly two payloads a refusal in
   this app has ever had.
5. **The refill rate never appears as a rate.** What appears is stock and time. It is the row's
   standing rule — *"the only place the number is spent"* — and a "+50/day" is the countdown handed
   over undivided.
6. **Nothing is rolled.** Every figure here is deterministic and stated before commitment, which is
   the standing promise of this app and the thing the copy must not undercut.

---

## 7. Save format — schema 10

Additive, and migrates rather than retires. A colony saved before deposits existed has taken nothing
out of anything, which is exactly what an empty `deposits` list says — so there is no number to invent
and nothing to rescale, the same argument schema 3 and schema 4 were carried on.

The hop also adds the fourth technology's level at zero. **One hop for two fields**, on schema 9's own
precedent: they ship together, so no save can ever hold one without the other.

---

## 8. Rejected

**A hold ceiling on ships.** §1. Flattens the frontier and taxes absence by construction.

**A continuous taper.** §2.3. Needs an exponential; `core` is integer-only across three platforms.

**Depletion per *system* rather than per world.** `fleet-sheet.md` already answers it for targeting:
*"slot 7 is where the metal is, and the other fourteen are the worlds you could have gone to instead.
Averaging richness over a system throws away the only thing that makes one target differ from
another."* Averaging the *stock* throws away the same thing.

**A cap that scales with distance from home.** §2.2. Pays for distance twice and makes a world's
contents a property of the observer.

**Regeneration as an event in `advance`.** It would put up to 160 boundaries into every span to move
numbers nobody is looking at. Computing on read is exact, free, and composes without an argument.

**A "world exhausted" notification.** The notification budget is already two multi-instance kinds
competing for iOS's 64 pending requests, and this one would fire for something the player did to
themselves and can see on the row.

**Making the deposit a `Resources`.** Tempting for the fine-unit arithmetic and the non-negative guard,
but it carries a deuterium field that must be zero forever, and a third `require(kind != DEUTERIUM)` in
the codebase is worse than twenty lines of a type that cannot express it.

**A Shipyard hull tier instead of a technology.** §5. New state, new verb, a second progression axis,
to deliver a multiplication the research branch already knows how to deliver.

**Auto-repeat, or a "re-send to the last target" affordance.** Rejected in `fleet-sheet.md` and more
firmly now: the whole point is that the last target is worse than it was.

---

## 9. The guardrail — *"please don't allow me to screw up"*

Davide asked for a brake — *"please don't allow me to screw up"* — and it has already caught one
thing: his first cap, 1,000, would have made the window ladder and the hull stepper ornamental (§2.5).
The brake is this section, and it is a merge condition rather than a promise.

**The sim owes a depletion report before any of this ships**, in the shape `printFleetReport` already
has: a fourteen-day run at each of {1,200, 1,450, 2,000} × {5%, 10%} per day, for a bot that probes at
a realistic rate, printing metal and crystal per day, deposits held in the save, the number of systems
the fleet needs surveyed to stay fed, **and the share of dispatches whose haul is deposit-limited
rather than fleet-limited** — that last column is what caught 1,000 and nothing else would have. Hand
arithmetic is not a measurement — rounds 2 and 3 wrote their tables by hand and the log says so.

Four readings would veto the numbers, and each names the dial to move:

1. **Fleet income falls below ~25% of colony income for a player probing once a day.** The fleet stops
   being worth owning; the hull curve then bounds nothing because nobody buys a second hull. → raise
   the cap.
2. **The check-in grows a fourth kind of tap.** If keeping a fleet fed means firing six probes a
   session, the 5–10 minute rule is spent on bookkeeping. → probe throughput: a cheaper probe, or one
   that reaches wider, is the first dial. Not the cap.
3. **A once-a-day player earns materially less per day than a six-hourly one** on the same fleet, with
   the *spread* strategy. That is the absence rule breaking, and it is a design failure rather than a
   tuning one. → the deposit is too small relative to a 24h single-hull run.
4. **The wall never arrives for an engaged player.** The inverse risk, and the more likely one: banked
   map is permanent and cheap, so after a few weeks of casual probing depletion may be irrelevant to
   the committed player and punishing only to the casual one. → probe cost, not the deposit.

**And the numbers must stay movable after ship.** The clamp-on-read in §3.2 is what buys that: cap and
regen are two constants in one new `DepositBalance` object, and changing either is a balance round
rather than a migration.

---

## 10. Build order

TDD throughout, failing test first, and the `core` half is entirely testable in a cloud session (§
`session-roles.md`) because it is pure integer arithmetic over a seed.

1. **`DepositBalance`** — `capFor(world, kind)`, `remainingAt(stored, cap, elapsed)`. Its own object,
   **not a growth of `FleetBalance`**: another session is working on hull prices in that file, and a
   new object is the cheap way for the two not to meet. Property tests: cap is monotone in richness and
   hazards; remaining never exceeds cap; refill is exactly twenty days.
2. **`WorldDeposit` + `GalaxyState.deposits`** — construction guards, one entry per world, and the
   prune. Property test: pruning is monotone, so a span and its subdivisions agree.
3. **`advance`** — the prune at the end of the span, beside `withoutSpentWatch()`. The composability
   property test extends across it unchanged, which is the assertion that matters.
4. **`startRun`** — the clamp, the debit, `StartRunResult.Depleted`. Tests: two runs in one check-in
   see one hole; a partial world clamps and does not refuse; an empty one refuses.
5. **Schema 10** — the migration, and `GameSaveTest`'s standing assertion that no generated trait ever
   reaches the file.
6. **The fourth technology** — enum constant, `Research` field, `ResearchBalance` rows, the multiplier
   reaching `FleetBalance.cargo`. Effects apply in a fixed order and this one joins it.
7. **The sim's depletion report** — §9. Nothing merges before this runs.
8. **The screens** — Design's round trip first; §6.
9. **`balance-log.md` round 23**, with what it is expected to *feel* like, so round 24 can check.

**Before pushing anything that adds a test to a `commonTest` source set**, run the comma check —
`grep -rn 'fun \`[^\`]*,[^\`]*\`(' core/src/commonTest/` — which has taken out five CI jobs twice.

---

## 11. Left open, deliberately

- ~~**Whether the absent player is taxed.**~~ — **measured, and it is the reverse: they are paid about
  fifty times over**, because the window rung decides reach and reach decides how many veins you can
  spread across. Six worlds against thirty-nine. **Accepted rather than fixed**, the build's call on
  Davide's *"decide for me"*, 2026-08-13: the cap and the refill both move every cadence together, so
  neither dial touches it, and the thing that does is making the frontier reachable at a shorter
  window — which is what the drive technology has been for since `exploration-rewards-sheet.md` §9.
  Watch it on a device: if the six-hourly player feels poor, the answer is a faster hull rather than a
  shallower world. `balance-log.md` round 23 has the table.
- ~~**Whether crystal deposits should be half the size of metal ones.**~~ — **kept**, measured at ten
  points more clamping than metal (42.6% against 33.0%). The basket is the game's single pricing
  convention, and a crystal vein worth double a metal one in it would collapse the currency choice
  into "always crystal", which `fleet-sheet.md` calls the whole reason the payout is one currency.
- **The deposit is measured from your home, and multiplayer will have to take that back.** §2.2's
  surviving objection, accepted knowingly: `distanceBand` is observer-relative, so two players sharing
  a world would disagree about how much is in it. The fix, when it is needed, is to freeze the band
  against something intrinsic — the world's own galaxy index rather than the distance from a home —
  which changes the numbers but not the shape. **Do not let a later session "tidy" this into hazards
  only without re-reading §2.2**: uniformity of time-to-strip is what pays for it.
- **The fourth technology's name and magnitude.** `PROSPECTING` is a placeholder; the curve wants a
  sweep, not a choice. Naming is Davide's or Design's.
- **Where the remaining figure lives on a row**, and whether it displaces the richness pair. §6.1.
  Design's, with the thirty-character header budget attached.
- **What a stripped world does to `WorldVerdict`.** A `Settleable` world that has been mined flat is
  still settleable, and `fleet-sheet.md` §9 already asks whether a run may strip a world you would
  rather colonise. Depletion makes that question sharper and it is still unanswered.
- **Scripted empires (slice #9) draining worlds too.** Nothing here stops them; nothing here builds it.
  The `deposits` list is holder-agnostic, so it costs nothing to defer.
- **Whether the hauler's berths interact with the deposit at all.** They should not — berths are rate,
  and rate is §5 — but the hauler is unbuilt and this is the sheet that would have said otherwise.
