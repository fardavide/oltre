# Adaptation decision sheet — 0.3

Written by the build, 2026-08-07, to close the one open call that stands between the galaxy screen
and the player: *"the adaptation technologies themselves"*, which
[`galaxy-sheet.md`](galaxy-sheet.md) named, specified the effect of, and deliberately left to the
slice that adds them. **Every line here is Davide's to overrule**; what it buys is that the slice
can start without a number being invented at the keyboard. Same shape as the 0.1 research sheet and
the 0.2 galaxy sheet: answer the question, name what was rejected, say what it should feel like so
the next round can tell.

Nothing here changes a `GalaxyBalance` number. What each level *widens* was settled at 0.0.15 and
is already in the code; what was missing was any way to buy one.

---

## The one-sentence version

**The map is a shopping list and this is the shop.** Three ladders, ~~one shared research slot~~
**a research slot of their own** (§2, overruled at 0.12.2), each priced in the resource its own axis
will make you rich in — so the ladder you can afford first is the one your colony is already good
at, and the one that would fix your problem is the one you cannot yet pay for.

---

## 1. A second branch, not rows four to six

The galaxy sheet left both open. Settled: **`AdaptationTechnology` keeps its own enum, its own
balance object and its own job, and the applied branch's `Technology` stays at three.**

**Why not rows four to six.** The applied branch is three multipliers on a per-hour rate, and every
part of the row says so — `EffectUiState` carries a current percentage, a next percentage and a
subject that ends in the word *output*. An adaptation level does not multiply anything. It widens a
band, in °C, in g, in atm. Putting "+14 °C" and "+47%" in the same column of the same list makes two
different kinds of thing look like one, and the row that says `Thermal · +14 °C · cold tolerance`
has no honest way to fill in the column that says what your production is now.

The second argument is what the two branches are *bought against*. Applied research is bought
against a colony you can see: you know what a Solar Plant does because you watched it. Adaptation is
bought against a map — the reason to buy Gravitic 3 is a sentence on a world four systems away. One
list cannot be read both ways at once.

Rejected: **rows four to six** (above); **a tech *tree* with the two branches drawn as nodes** — a
picture of six nodes is still not worth a screen, which is the same argument 0.1 used to reject
drawing one for three; **folding the three ladders into one "Habitability" technology** — that is
§1 of the galaxy sheet arrived at from the other side, and it deletes the choice the three axes
exist to create.

## 2. ~~One research slot, shared between the branches~~ **A slot each**

> **OVERRULED BY DAVIDE, 2026-08-15, at 0.12.2**, on having played it: *"Technologies and
> Adaptations run on the same queue, making the game too slow. I want to have a queue each."*
>
> The section below is kept whole rather than rewritten, because it is the argument the ruling
> answers and reading only the answer would lose it. **What is true now**, in three lines:
>
> - `activeResearch` and `activeAdaptation` are two fields and **both may be set at once**. The
>   `require` in `GameState.init` is gone; there is no invariant here to check any more, so `core`'s
>   one checked-rather-than-unrepresentable rule about research has simply stopped existing.
> - Each branch is still **serial on its own**: one applied project, one ladder. `startResearch`
>   refuses on `activeResearch`, `startAdaptation` on `activeAdaptation`, and both still answer
>   `SlotBusy` — the word now means *this branch's slot*.
> - **The prices did not move with it.** Everything in §4 was priced against a trade that no longer
>   exists, and the first round after this lands is what says whether it should. That was Davide's
>   call too — measure before retuning, so the two changes can be told apart.
>
> **What the ruling costs, stated plainly rather than sanded off.** The section below is right that
> the sharing *was* the scarcity, and losing it is a real loss: an adaptation level is no longer
> paid for in production levels you did not buy, so the branch that changes the map is now cheap to
> push in the only currency that was ever making it expensive. `BalanceBenchmark` measured exactly
> that on the same fixed player — 28 readings moved, the fortnight lost seven building levels and
> never reached the Nanite Factory, because money the shared slot used to keep in mines now goes
> into ladders. What it bought is on the same page: income at day 14 rose from 274,291 to 302,878,
> because the applied branch stopped queueing behind a ladder, and the hours opening with nothing
> affordable fell from 46.3% to 32.9%. **Slower colony, richer colony, and two decisions a session
> instead of one.**
>
> The two *Left open* items below both close as a side effect: there is no shared slot left to turn
> into a sealed `ActiveProject`, and the Research screen renders both branches already.

`activeResearch` and `activeAdaptation` are two fields, and **at most one of them is ever set**.
The invariant is checked in `GameState.init`, so it holds on every construction including every
decode of a hand-edited save.

**Why shared and not a second slot.** The single slot is the only scarcity research has — 0.1 wrote
it down in as many words: *"its costs are small next to a mine of the same era, so without it the
answer would always be start all three"*. Give adaptation its own slot and the answer is always
"run both", and the branch that changes the map costs nothing to push. Sharing it means every
adaptation level is paid for in production levels you did not buy, which is the decision the branch
exists to create.

**Why two fields and not one sealed slot.** A sealed `ActiveProject` is the shape that makes the
invariant unrepresentable, and it is the shape this would take in a vacuum. It is not what landed,
for a stated reason: every existing reader of `activeResearch` — the Research screen's row mapper,
the notification set, `futureEvents` — would have to answer for a project it does not render, in a
slice whose whole point is that the *screen* work is a separate hand-off. Two fields plus one
`require` keeps the applied branch's readers compiling untouched. **This is the one place in `core`
where a rule is checked rather than made unrepresentable**, and it is worth revisiting the day the
Research screen learns to render both — see *Left open*.

Rejected: a second slot (no decision left); running in parallel at a duration penalty (a multiplier
nobody can hold in their head — the galaxy sheet's §2 argument, and the energy round's lesson).

> **Postscript on the rejection, because it is the part that aged worst.** "A second slot — no
> decision left" was wrong in a way worth naming: it treated *fewer waits* as *fewer decisions*.
> With a slot each the player picks a project **and** a ladder, which is two decisions rather than
> none; what disappeared is the wait between them. The lesson generalises past this sheet — scarcity
> that produces a queue is not the same thing as scarcity that produces a choice, and this sheet
> spent eleven versions assuming it was.

## 3. The gate: Robotics Factory ~~4~~ **2**, the same for all three

> **Lowered to 2 at 0.5.1** (`balance-log.md` round 18), on Davide having played it — *"I needed 2
> day to get robotics to level 4"* — and on round 12's own pre-authorisation, quoted below. The
> measured clock is hour 33 at gate 4 against **hour 12** at gate 2.
>
> **Everything in this section except the number survives, and the "why 4" paragraph survives with
> its argument intact rather than overruled.** It asked that the branch open *after* the player has
> met the Galaxy screen and read a `BLOCKED` row. Nothing gates the Galaxy tab — the home system is
> surveyed at genesis — so that has been true from the first frame at every gate level, at 4 and at
> 2 alike. What the clause really rules out is **1**, which is the applied branch's own gate: share
> it and five rows open at once and the locked row leaves normal play entirely. That is still the
> reason the gate is not 1, and it is the whole of what the paragraph below decides now.
>
> 3 was on the table and rejected on the measurement: Robotics 3 and 4 are six hours apart where 2
> and 3 are fifteen, so it costs half the price pressure for a fifth of the clock.

One shared gate, not three different ones.

**Why one.** Three gates that differ would decide the first ladder for the player. §1 of the galaxy
sheet argues that three ladders are worth having *because which one you push first is a real
choice*; a gate that opens Gravitic before Thermal makes that choice for them and the three ladders
collapse back into a chain.

**Why Robotics 4.** It adds no concept — Robotics 1 is already the applied branch's gate, and the
deuterium wall before it. It is a purchase the player wants anyway, because Robotics shortens every
project including these. And level 4 is far enough past level 1 that the branch opens *after* the
player has met the Galaxy screen and read a `BLOCKED` row, which is the order the sentence on that
row assumes: first you learn you need Gravitic 3, then you find you can buy it.

Rejected: **gating on having surveyed a `Blocked` world** — elegant on paper, and genuinely the
design's own logic, but it **gates nothing**: 98.2% of surveyed worlds are blocked, so a home system
of four worlds fails to contain one about once in thirty million, and the requirement would be met
at genesis before the player has done anything at all. It would also make a research rule depend on
world generation, and give `ResearchRequirement` a third shape that is not a level comparison, to
buy a sentence that is always already true; **gating each
ladder on its matching applied technology** (Enrichment → Thermal, and so on) — reads well and
decides the first ladder for them; **no gate at all** — the branch would open before the Galaxy tab
has anything to say.

## 4. The numbers

### Cost — the same ×1.5 curve, and all three cost the same in different currencies

Level 1, and ×1.5 per level after it, exactly as every building and every applied technology:

| Ladder | Widens | metal | crystal | deuterium | Priced at 1 : 2 : 3 |
|---|---|---|---|---|---|
| **Thermal** | temperature | 900 | 600 | 900 | **4,800** |
| **Gravitic** | gravity | 2,400 | 900 | 200 | **4,800** |
| **Atmospheric** | pressure | 850 | 1,600 | 250 | **4,800** |

**Each ladder is priced in the resource its own axis makes rich.** Gravity makes heavy worlds and
heavy worlds are rich in metal, so Gravitic costs metal. Pressure makes thick atmospheres and thick
atmospheres are rich in crystal, so Atmospheric costs crystal. Temperature makes cold worlds and
cold worlds are rich in deuterium — the resource the research branch already made scarce — so
Thermal costs deuterium, and is therefore the hardest of the three to start.

That is the whole design of this table. The ladder you can afford first is the one your colony is
already good at; the ladder that would fix the shortage you actually have is the one you cannot yet
pay for. **The identical priced total is what keeps that a preference rather than a right answer** —
strip the currencies away and there is nothing to choose between them, so the choice is entirely
about what your colony has in the bank.

Why ×1.5 and not a steeper curve for the expensive branch: `CLAUDE.md`'s standing rule is that the
game has *one cost curve rather than two*. The adaptation branch is the expensive one because its
base is nearly twice Enrichment's (4,800 priced against 2,500), not because its curve is different.

### Duration — 240 minutes × level, all three, on the research Robotics divisor

> **The tables below are still at Robotics 4 and 8 and are still correct**; what changed at 0.5.1 is
> that a player reaches the branch at Robotics 2, where the divisor is `25 / 29` rather than
> `25 / 33`. Level 1 is 21 minutes there against 18 at Robotics 4 — the gate coming down buys the
> clock, not the project.

The longest project in the game: 1.6× Enrichment's 150, and equal across the three ladders for the
same reason the priced costs are equal. It rides `25 / (25 + 2 × Robotics)`, the gentle divisor
0.1 settled for research — not construction's steeper one.

| Level | at Robotics 4 | at Robotics 8 |
|---|---|---|
| 1 | 3h 02m | 2h 26m |
| 3 | 9h 05m | 7h 19m |
| 5 | 15h 09m | 12h 12m |
| 8 | 24h 15m | 19h 31m |

Rounded to the nearest minute, the convention the other two sheets' tables use and the one
`:sim:run` prints. `AdaptationBalanceTest` pins these eight values the same way.

### What a level buys — unchanged, and already built

From `GalaxyBalance`, settled at 0.0.15 and not touched here:

| Ladder | Band at level 0 | Each level widens by |
|---|---|---|
| Thermal | −30 … +45 °C | ∓14 °C |
| Gravitic | 0.65 … 1.40 g | −0.05 / +0.12 g |
| Atmospheric | 0.5 … 2.6 atm | −0.06 / +0.9 atm |

And the payoff the galaxy sheet's §9 promised: each level roughly **doubles** the settleable count
for the first few levels — 17 → 40 → 105 → 218 galaxy-wide. `:sim:run` prints it; the numbers here
are what the ladders cost to reach it.

### Where the ladders saturate

Past a point a level buys nothing, because every world the generator can produce already passes:

| Ladder | Saturates at | Because the generator's extreme is |
|---|---|---|
| Thermal | **17** | −260 °C, at the coldest orbit of a dim star |
| Gravitic | **12** | 2.75 g |
| Atmospheric | **11** | 12 atm |

**No cap is added.** `TechLevel.MAX` stays 30 — its bound is about cost arithmetic overflowing a
`Long`, not about balance — and the ×1.5 curve already makes Gravitic 12 an undertaking: 4,800
priced at level 1 becomes 415,000 at level 12. A cap would be a new concept bought to prevent a
purchase nobody has a reason to make. Recorded as an open call rather than settled, because a
screen that shows a level with no effect is a screen telling a small lie.

## 5. The save carries three more integers and a nullable job

Schema **5, migrating 4** — the third time in a row the answer is migrate rather than retire, and
for the same reason both previous times: a colony saved before the branch existed has researched
nothing and has nothing running, which is exactly what a fresh `Research` and an empty slot say. No
number to invent, nothing to rescale.

## What it should feel like, to check next round

- **Reading a `BLOCKED` row should now end somewhere.** "Gravitic Adaptation 3 would land it"
  followed by a tab that sells Gravitic Adaptation is the connection the galaxy sheet said was the
  only thing joining two screens that otherwise never speak. Until 0.0.17 the sentence ended in a
  wall.
- **The first ladder should pick itself, and the second should hurt.** A metal-heavy colony buys
  Gravitic 1 almost without noticing and then finds that the world it actually wants is cold.
- ~~**Losing a production level to an adaptation level should sting**, and should still be worth it.
  If it never stings, the shared slot is not doing its job and the branch is too cheap; if it always
  loses, the base cost is too high. That is the number to move first — not the curve, and not the
  widening.~~ **Answered, and then deleted by §2's overruling.** It stung — that is what "making the
  game too slow" was — and the ruling was that the sting was the wrong instrument rather than the
  wrong size. **The replacement question for the next round: with the trade gone, is a ladder still
  a purchase you have to think about, or is it now just something you always buy?** The base cost is
  still the number to move first if the answer is the second one.
- **Nothing settles yet.** A world going from `BLOCKED` to `Barren` or `Settleable` is the whole
  payoff in 0.0.17, because colonisation is slice #10. The verdict changing on a screen the player
  is looking at is a real reward; it is not the pillar landing, and the next round should not read
  it as one.

## Left open, deliberately

- ~~**Whether the two branches share one screen or get two.**~~ **Closed:** one screen, two
  sections, shipped at 0.6.0. Each heading now carries its own rule, which is the 0.12.2 change
  showing on the surface.
- ~~**Whether the shared slot should become one sealed `ActiveProject`.**~~ **Closed by there being
  no shared slot.** The revisit was booked for "the day the Research screen renders both branches",
  and that day came and went without the shape mattering; what settled it instead was §2 being
  overruled. Two independent slots are two independent fields, and a sum type over them would now be
  actively wrong rather than merely unnecessary.
- **Whether a saturated ladder should be capped or labelled.** §4 declines to add a cap. Showing
  Thermal 18 as buyable when it changes nothing is the small lie a later slice may want to fix,
  probably by labelling rather than by capping.
- **Terraforming**, still — the galaxy sheet's own flag. If a world's traits can be *changed*
  rather than only tolerated, this branch is half of a bigger tree and the yield maths moves. Not a
  0.3 problem.
- **Whether adaptation should do anything but widen bands.** It is deliberately the only effect, per
  the galaxy sheet's §2 rule that an axis does exactly two things and no more. A ladder that also,
  say, cut colony upkeep on hostile worlds would be slice #10's call, not this one's.
