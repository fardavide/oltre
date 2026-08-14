# Galaxy identity decision sheet — 0.10

Written by the build, 2026-08-14, on Davide's report after playing 0.9.0:

> *"I'm so unhappy with the map. It is huge, but terrible to navigate! Finding a planet feels like
> searching a phone number on pagine gialle in the 90s."*

> *"I feel like the map should gain 'an identity'. Eg when I think about going to Siracusa, or
> specifically to Fontane Bianche, what comes to my mind is a map, some 'images' of the places; they
> have an identity. While here it's just numbers: like '2 pages before'. The user should feel excited
> about exploring new places and build a knowledge of the world."*

**Every line here is his to overrule.** Same shape as the 0.1 research sheet, the 0.2 galaxy sheet,
the 0.3 adaptation sheet, the 0.4 fleet sheet and the 0.8 exploration sheet: answer the question,
name what was rejected, say what it should feel like so the next round can tell.

This sheet **extends `galaxy-sheet.md` §4 and §5** and supersedes nothing in it. No tolerance band,
no richness formula, no `verdictFor` case and no `GalaxyDistributionTest` target moves — §2.4 is the
argument for why the one generation change it does make cannot move them.

## Davide's calls, 2026-08-14 — binding on this sheet

| Question | His answer |
|---|---|
| Which threads to develop | **All four** — names, geography, portraits and epithets, per-world history — plus the ledger they hang off |
| How worlds get names | **Generated only.** *"No rename, perhaps later on, when it will be colonised, but let's keep out of scope for now"* |
| How far the geography change may go | **Regions as real geography, accept the reroll** |
| What happens to his existing save | **Keep the seed and the home coordinate** — §1.4 |
| Region size | **10 regions of 25 systems** |
| How hard a region tilts | **Moderate — 60 / 30 / 10** |
| Where the epithet appears | **The world row *and* the dispatch sheet** |
| Where the ledger lives | *"Should we ask Design?"* — **yes**, and it went in the round trip with the portrait and the strip. `.claude/prompts/design-galaxy-identity.md` |

---

## The one-sentence version

**A place is something you can name, picture, find again and have a history with** — so the galaxy
gains regions that are genuinely different from one another, every world gets a name and a face
derived from what it already is, and the player's own accumulated knowledge finally gets a screen.

---

## 0. What is broken, measured

Three readings from the repository's own code. None is a guess.

### The galaxy is statistically uniform, by construction — and this is the finding that reframes the rest

`starClassAt` (`GalaxyGeneration.kt:84`) hashes each system's seed independently. Star class is the
**only** system-level trait in the game; every other trait is drawn per world. So any two
neighbourhoods of 250 systems are drawn from the same distribution, and therefore:

**There is nothing about any region of the map that can be learned, remembered, or told to another
player.** Not because the UI hides it — because it does not exist. A map with no spatial structure
has addresses, not places.

This has to be stated starkly, because it means the identity complaint is not a UI complaint. Names,
portraits and a ledger all make the map *nicer to use*. Only spatial structure makes it *knowable*,
and knowing the world is exactly what Davide asked for: *"build a knowledge of the world."*

Note what the design already gets right and what it stops one step short of. `galaxy-sheet.md` §4
makes temperature a function of the **slot**, precisely so that *"the outer slots are where the
deuterium is"* is something a player learns rather than is told. That instinct is correct and it is
the model for this sheet — it is simply applied to the one axis of the coordinate that has no
structure at all. Slot means something. System number means nothing.

### The unit of navigation is not the unit of interest

The screen navigates in **systems** — a page of fifteen slots — and the player is choosing a
**world**. That is 1,000 pages, of which roughly 98% read `Unsurveyed`, because genesis surveys the
home system and nothing else (`GalaxyState.initial`). The reach band replaced 249 taps with a lens,
which was the right fix for the tap count and is a better *index*; it did not change the fact that
the only way to find a world is to page through a directory. **That is the phone book, exactly.**

### The player's own knowledge has nowhere to live

`GalaxyState.surveyed` is a `Set<GalaxyCoordinate>` and it is read for exactly one thing: whether a
probe would learn anything there (`hasSurveyed`). **No screen in the app shows you what you know.**
Survey forty worlds over a fortnight and they are scattered across a thousand pages with no
aggregate, no filter, no pin, and no way back except remembering `3:177:6`.

Surveying is the game's exploration verb, it costs a probe and hours, and it accumulates **nothing
the player can look at**. Round 21 asked why exploring felt unrewarding and answered it in cargo;
this is the same question answered in knowledge.

Two smaller notes belonging here. `WorldDeposit` prunes itself back to nothing twenty days after a
world is worked — correctly, for a resource cap, but it means the map is *designed* to forget your
visits. And nothing anywhere records that *you* were the one who found a world.

---

## 1. Regions — the geography, and the reroll

### 1.1 What a region is

A galaxy's 250 systems are cut into **10 contiguous regions of 25 systems**, boundaries fixed rather
than generated (§7). A region carries two things:

- a **name** — "the Ostara Deep", "the Calanova Reach"
- a **temperament**, which biases the star classes of its 25 systems

```kotlin
fun regionOf(system: Int): Int = (system - 1) / GalaxyBalance.SYSTEMS_PER_REGION + 1
```

`starClassAt` gains one step: draw the region's temperament, then draw the system's class from the
distribution that temperament names.

| Temperament | Dim | Standard | Bright | What a player learns |
|---|---|---|---|---|
| **Deep** | 60% | 30% | 10% | cold stars — settle *close in*, and this is where the deuterium is |
| **Settled** | 20% | 60% | 20% | the middle, and the reference against which the other two read |
| **Burning** | 10% | 30% | 60% | hot stars — the habitable orbits are *out*, and deuterium is poor |

**The lesson each region teaches is real arithmetic, not flavour text.** A `DIM` star is −40 °C and a
`BRIGHT` one +40 °C on a fall of 28 °C per slot, so the tolerable orbits move by about three slots
between them, and deuterium richness — `0.6 + 0.5 × (20 − T) / 60`, clamped to 0.6…1.6 — swings by
two thirds of its whole range across an 80 °C offset. *"In the Deep you settle close in; in the
Burning you settle far out"* is a fact a player can act on before they have surveyed anything, which
is the same class of fact as *"the outer slots are where the deuterium is"* and the reason
`galaxy-sheet.md` §4 says the coordinate is worth having.

### 1.2 The temperaments are a permutation, not ten independent draws — and this is the load-bearing detail

Each galaxy holds a **fixed multiset** of ten temperaments — `4 × Deep, 2 × Settled, 4 × Burning`,
**shipped**, after `3 × Deep, 4 × Settled, 3 × Burning` was proposed here and measured — shuffled into
its ten regions by the seed. Not ten independent draws.

Two consequences, both required:

1. **The galaxy-wide star-class distribution is identical for every seed.** A permutation preserves a
   total; ten independent draws preserve it only in expectation, and a per-seed test would then have
   to widen its bands to admit the unlucky galaxy. `GalaxyDistributionTest` gets *tighter* from this
   change, not looser.
2. **Every galaxy is promised one of each.** "There is a Deep somewhere in your galaxy" is a sentence
   the game can say and always be right about — which matters because a player who cannot find one is
   a player for whom this entire section did nothing.

**What it is *not* is identical to today's mix, and an earlier draft of this sheet claimed it could
be.** It cannot: ten regions cannot average to thirds of 1,000. `4 / 2 / 4` pools to **32 / 36 / 32**,
which is what shipped and is near enough equal thirds to leave every target below alone; the proposed
`3 / 4 / 3` pooled to 29 / 42 / 29 and left four bland regions in ten instead of two. That is a
decision rather than a missed target, and it is a cheap one to take, because **equal thirds was never
decided either** —
`GalaxyBalance.kt:74` says so in as many words: *"ASSUMED, NOT DECIDED. The sheet gives each star
class its temperature offset but never says how often each one occurs, so this slice takes equal
thirds and says so"*, and it is carried as an open call in `balance-log.md`. So this section closes
that open call rather than disturbing a settled one, and the same paragraph is why it is safe to:
each class passes the temperature band on ~25% of its worlds either way, so the pooled *verdict*
distribution barely moves whatever the class mix is.

**The sim is still the arbiter.** `:sim:run` prints §9's four rows against the new generator before
the slice is believed, exactly as round 5 did for the original constants.

### 1.3 Why gravity and pressure stay i.i.d. — the one place the sheet declines what it was asked for

Regional bias on **gravity** ("iron country") and **pressure** ("thick country") is the obvious next
step and this sheet rejects it. Two reasons, and the first is decisive:

**It would move `GalaxyDistributionTest` and star class would not.** Temperature's pass rate is
robust to the star offset because *the habitable orbit moves with it* — `GalaxyBalance.kt:74` already
records this, measured: *"because the habitable orbits shift with the offset, each class ends up
passing the temperature band on ~25% of its worlds either way."* Gravity and pressure have no such
compensating coordinate: they are threshold crossings on a fixed skewed distribution, so biasing
their means by region pools into a mixture with strictly higher variance and fatter tails. §9's first
row is 1–2% and measures 1.81% — there is a fifth of a point of headroom at the top, and a mixture
spends it.

**And the identity it buys is unactionable anyway.** A player cannot see a world's gravity without
surveying it, so "this region runs heavy" is a statistic they can only confirm by paying for it
repeatedly. The star-class lesson is free: star class is *charted*, per `galaxy-sheet.md` §5, so the
region announces itself the moment you look at it.

If a later round wants heavier geography, the honest lever is the same one §9 names for the
"come back later" pile: change something that moves all three axes together, and re-derive the
targets deliberately rather than as a side effect.

### 1.4 The reroll, and what happens to a save that already exists

Changing `starClassAt` changes every world's temperature, therefore its deuterium richness, its
verdict, its `levelsToTolerate`, and therefore the walk in `homeFor`. **Every existing map changes
under its owner.** Three ways to meet that:

| | What it does | What it costs |
|---|---|---|
| **Keep the seed, keep the home coordinate** *(recommended)* | The colony stays where it is; the traits under it move | Home may end up a world the species does not tolerate, and its doorstep guarantee is not re-established |
| Re-mint `GalaxyState` from a fresh seed | Every genesis invariant holds again, including the doorstep | **Deletes the player's surveys** — the exact accumulated knowledge this sheet exists to make valuable |
| Version the generator per save | Nobody's map moves | Two generators in `core` forever, and the old one has no regions, which is the thing being fixed |

**Recommended: keep the seed and the home coordinate.** The argument is that the doorstep rule
(`GalaxyGeneration.kt:161`, added at 0.5.1) exists to make the *opening* legible — *"your first
adaptation level opens a world you can see"* — and a player with a colony already past the opening
is not the player it was written for. Re-minting would restore that guarantee by destroying a
fortnight of surveys, which is a worse trade at the exact moment surveys start being worth
something.

**Say it plainly in the changelog rather than quietly**: the map changed, on purpose, and it will not
change again.

---

## 2. Names — generated, never stored

Davide's call: generated only. Renaming is out of scope and revisits at colonisation.

- One new `GenerationAxis.NAMES` tag. **Free** — `GalaxyGeneration.kt:46` guarantees that adding a
  constant shifts nothing that already exists, which is the whole reason the sub-stream rule was
  written.
- **A system's name is generated from its seed; the region supplies the phonetic palette**, not a
  literal prefix. Systems in a region rhyme in *character* — the Deep's names long and soft, the
  Burning's short and hard — so a name places you without two systems reading as near-typos of each
  other.
- **A world's name is the system's name plus the Roman numeral of its slot.** `Calanova VII` is
  slot 7 of Calanova. One numbering on the screen, not two: the map already spaces bodies by rank and
  labels them by slot, and a second ordinal would make those two disagree.
- **Uniqueness inside a region is structural, not hoped for.** One syllable position is a pure
  function of the system's index within its region, so two of the 25 cannot collide. Collisions
  across regions are fine and arguably good — Italy has more than one Marina di Something.
- **The coordinate never goes away.** It is the address: the arithmetic, the eventual multiplayer
  chat, and the ledger's key all need it. The name becomes the headline and the coordinate the
  subtitle, which is the same demotion `WorldRowUiState` already performs on the verdict.
- Region names take the same grammar plus a common noun chosen by temperament — *Deep, Reach, Verge,
  Span, Drift* — so the word is true rather than decorative.

**Nothing here is stored.** A name is regenerated exactly like a trait, so 4,700 names cost the save
zero bytes, and the save-format argument of `galaxy-sheet.md` §7 is untouched.

---

## 3. A world gets a face

### 3.1 The portrait is the survey's reward, and it must not appear before one

A procedurally drawn disc, on `Canvas`, deterministic from the world's seed. Every channel is a
**fact already generated** — which is what makes it a reading rather than decoration:

| Channel | Driven by |
|---|---|
| hue | temperature — blue-white, grey, ochre, red |
| banding and cloud opacity | pressure |
| disc size | gravity |
| surface mottling | the world seed |
| terminator: one lit limb, one dark | `TIDALLY_LOCKED` |
| storm swirl | `ION_STORMS` |
| fracture lines | `SEISMIC_INSTABILITY`, `THIN_CRUST` |
| halo | `RADIATION_BELT` |
| ring | a new stream, rare, no mechanic — pure identity |

**An unsurveyed world gets a blank disc.** Not a compromise — the point. A portrait is a trait
readout, so drawing one on the 98% would perform a survey nobody paid for (the rule
`VerdictUiState.Unsurveyed` already states for hazards), *and* it would spend the best reward
surveying has. **The picture arriving is what makes a survey feel like a discovery**, which is the
literal answer to *"the user should feel excited about exploring new places."*

After a fortnight a player recognises a heavy cold world from its disc before reading a number. That
is the *"images of places"* in Davide's sentence, and it is the only part of this sheet that a
screenshot can carry.

### 3.2 An epithet — derived, never rolled

A short phrase from the axes, in the same spirit as richness being derived rather than rolled: it
**cannot lie**, because it is a function of the values it describes. The noun comes from the most
extreme axis, the adjective from the second — *iron giant, frost husk, storm shroud, bare furnace*.

Roughly a dozen nouns and eight adjectives is a hundred combinations, all true, and it turns three
numbers into something a player can say out loud. **The vocabulary is content and therefore Davide's**
— this sheet specifies the mechanism and proposes a table, not the words.

**It goes on the world row as well as the dispatch sheet** (Davide, 2026-08-14): the surface that
reads as faceless is the *list you scan*, so a phrase that only appears once you have already
committed to opening a world has arrived too late to do the job. That is a real cost on the tightest
surface in the app — a three-axis `Blocked` row already wraps at 393dp — and it is the first thing
§6's design round trip is asked to solve.

### 3.3 The discovery card

The first time a world is surveyed, once ever: name, portrait, epithet, the three readings, dated.
It converts a survey from a change of set membership into an **event**, which is what a memory
attaches to.

---

## 4. The ledger — the missing screen, and the largest single win

Everything in `surveyed`, in one place. This is the direct answer to the phone-book complaint: you do
not read a directory, you look something up.

- **Filters**: reachable within N hours (the reach band's own axis, reused), verdict, "one level away"
  per ladder, still holding stock, region.
- **Sort**: distance, yield, metal or crystal remaining, when you found it.
- **Pins** — and pinned worlds get their mark on the reach ruler, so the strip becomes a memory aid
  rather than an index.
- **Search by name**, which is what §2 was for.
- At genesis it holds your home system and nothing else. That emptiness is honest and it is the
  invitation.

**Not a sixth tab** — the bar is five and fixed (`decisions.md`, 0.0.11), and a ledger is a second way
of reading the map rather than a second subject. **Whether it is a mode of the Galaxy tab, a sheet
raised over the map, or the tab's new default view is Claude Design's call**, asked on 2026-08-14
(`.claude/prompts/design-galaxy-identity.md`). The third option is the strongest answer to the
phone-book complaint and the largest change to what the tab is, which is exactly the kind of trade
worth handing to a drawing rather than settling in prose.

Storage: `surveyed` already holds the set; **pins are the one new field** — a `Set<GalaxyCoordinate>`
on `GalaxyState`, and a schema hop.

---

## 5. History — the map remembers what you did

Per surveyed world: when you found it, how many runs you have sent, and how much you have taken out.

> *Calanova VII — found day 3 · 6 runs · 12,400 metal hauled*

That sentence is identity in a way no generated trait can be, because it is **yours**.

**The unbounded-growth objection has to be met head on**, because `fleet-sheet.md` raised it and
`WorldDeposit` is built around the answer: *"a counter for every world ever visited is a save that
grows without bound."* Three points:

1. It is bounded by worlds **surveyed**, not worlds visited — and a survey costs a probe and hours,
   so a heavy player reaches a few hundred in a month, not thousands.
2. `WorldDeposit` prunes because a deposit is *derived* state that returns to a known default. A
   history entry is neither derived nor returning; pruning it would be deleting the thing.
3. Four fields per entry at a few hundred entries is tens of kilobytes against a save that already
   carries an event log.

`discoveredAt` needs an `Instant` at the moment of survey. Survey lands inside `advance` on a probe's
arrival, which has the instant in hand — so `core` purity is untouched and no clock is read.

---

## 6. The slicing

| | What lands | Reroll? | Save hop? | Needs Design? |
|---|---|---|---|---|
| **A — Names** | `NAMES` stream, the grammar, name as the headline on every row, sheet and header | no | no | no |
| **B — The ledger** | Galaxy-tab mode, filters, sort, search, pins | no | **yes** (pins) | yes |
| **C — Regions** | Region stream, permuted temperaments, region names, region as a navigation level | **yes** | no | yes (the strip) |
| **D — Portraits** | The disc, the epithet, the discovery card, new baselines | no | no | **yes** |
| **E — History** | `discoveredAt` and the run tallies, shown on the row and in the ledger | no | **yes** | small |

**A then B is the order**, and the argument is that between them they are the whole of the
navigation complaint, they cost no reroll and no balance work, and B is the single largest win in the
sheet. C is the deepest change and the only one that touches generation, so it wants the sim and its
own PR. D needs a Claude Design round trip and can run in parallel with anything. E is small and can
ride with B, whose schema hop it shares.

**A and B can be one PR** — a generator plus a screen, and the screen is the reason the generator is
worth having. **C is its own.** D is its own because baselines move.

---

## 7. What was rejected

- **Player renaming.** Davide's call, deferred to colonisation. Recorded honestly: it is the single
  strongest identity lever available — a name you chose is the difference between an address and
  Fontane Bianche — and the reason to wait is that a name given to a world you cannot own yet is a
  name for a rock you visit.
- **A sixth tab for the ledger.** The bar is five and fixed.
- **Jump-to-coordinate as *the* fix.** `ReachBandUiState` already rejected it for the right reason —
  it presumes you know the number, which is what the charted tier does not give you. It survives as
  an accessory next to search-by-name, which does not presume that.
- **Generated region boundaries.** Regions of varying width per seed buy nothing a player can
  perceive and cost trivial arithmetic — `(system − 1) / 25` — and cross-galaxy comparability.
- **Ten independent temperament draws.** §1.2: a permutation keeps the galaxy-wide distribution
  exact for every seed instead of merely on average, and lets the game promise one Deep per galaxy.
- **Regional bias on gravity and pressure.** §1.3 — it moves `GalaxyDistributionTest` and buys
  identity a player cannot read without paying for it.
- **Regions as names with no bias behind them.** Explicitly ruled out by Davide, and correctly: a
  region that is only a label is a lie the map tells for a fortnight until somebody checks.
- **Portraits on unsurveyed worlds.** Leaks traits, and spends the survey's best reward.
- **Re-minting the galaxy on migration.** §1.4 — it restores a genesis guarantee by destroying the
  surveys this sheet exists to make valuable.

---

## 8. What it should feel like, to check next round

- You say *"I'm heading up into the Ostara Deep"* instead of *"systems 180 to 200"*.
- A world you surveyed a week ago is two taps away, found by name, not remembered as a number.
- You recognise a heavy cold world from its disc before you read a single figure.
- The first survey of a session is **a picture arriving**, not a set membership change.
- The regions are worth knowing, and you learned them rather than being told: *"in the Deep you
  settle close in"* is a sentence the player says first.
- **The map is smaller than it was**, in the only sense that matters — not fewer worlds, fewer worlds
  you have to walk past.

---

## 9. The calls, taken 2026-08-14

| | Call | Answer |
|---|---|---|
| 1 | Region count and width | **10 × 25.** A region is about two hours wide at drive 0 — a plausible night's dispatch — and ten names a galaxy is learnable in a week |
| 2 | How hard a region tilts | **Moderate, 60 / 30 / 10.** A Deep is clearly cold and a Settled still reads like today's map, so a region keeps some texture instead of becoming one fact |
| 3 | Migration for existing saves | **Keep the seed and the home coordinate** — §1.4, and the changelog says the map moved |
| 4 | The temperament multiset | **`4 × Deep, 2 × Settled, 4 × Burning`**, pooling to 32 / 36 / 32 — which **closes** `GalaxyBalance.kt:74`'s assumed equal thirds rather than disturbing a decided number. Measured: both §9 rows stay in band, and the row was re-pinned across six maps because one map cannot carry it. `balance-log.md` round 26 |
| 5 | Where the epithet appears | **The row and the dispatch sheet.** The list you scan is the thing that reads as faceless, so the row is where it has to earn its line — and the row is already the tightest surface in the app, which is why §6 sends it to Design |
| 6 | Where the ledger lives | **Design's call**, asked. With it went the portrait's visual language and whether region names belong on the reach strip |

### Still genuinely open

- **The epithet vocabulary** — the mechanism is §3.2, the words are content and Davide's.
- **Whether the ledger's filters are chips, a sheet, or a segmented row** — inside Design's remit.
