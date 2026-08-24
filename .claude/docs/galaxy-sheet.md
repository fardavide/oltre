# Galaxy decision sheet — 0.2

Written by the build, 2026-08-07, on Davide's instruction to settle the open questions for slices
4 and 5. **Every line here is his to overrule**; what it buys is that the slices can start without
a number being invented at the keyboard. Same shape as the 0.1 research sheet: answer the
question, name what was rejected, say what it should feel like so the next round can tell.

Nothing here contradicts the Notion page. Where the page already decided something it is quoted
and obeyed; where it named an option without choosing, this sheet chooses and says why.

> **Superseded in one place, 2026-08-10 (0.3.0).** The Sky graphics pass replaced the map's
> fifteen-tick strip with the orbit view, which draws one ellipse per *occupied* slot and no band
> strip. Two things this sheet treats as load-bearing therefore no longer appear on the map: the
> shape of a system — that four of fifteen slots are occupied and where the gaps fall — and the
> Hot / Temperate / Cold strip that taught the temperature axis by showing it. The bands survive as
> the world list's section headings. **Davide's call, asked directly**, with the alternatives on the
> table; the reasoning is in `decisions.md` under "The Sky pass". Everything else here — the
> verdicts, the thresholds, the balance — is untouched.

---

## The one-sentence version

**An easy world is a poor world.** Every axis that makes a world hostile is the same axis that
makes it rich, so the good ground is behind the tech you have not bought yet, and roughly one
world in two hundred is worth settling on day one.

---

## 1. The trait axes — four, not five

Notion names five candidates: *temperature, gravity, atmosphere, hazards, resources.* Settled:

- **Three hostility axes** — temperature, gravity, pressure. Each is a scalar; each is checked
  against an empire tolerance band; each gates settlement.
- **One yield consequence** — richness per resource, **derived** from the three axes rather than
  rolled independently.
- **Hazards are not an axis.** They are a short list of named flags on the world.

**Why hostility is three axes and not one.** One "habitability" number would read better on a
phone, but it makes adaptation tech a single ladder — and a ladder contains no decision, which is
exactly the argument the research sheet used to reject a tech chain. Three axes means three
adaptation ladders and *which one you push first* is a real choice that makes two empires differ
— Notion's "mutually exclusive tech branches" pillar, delivered by the map instead of by a tree.

**Why richness is derived, not rolled.** If richness were independent, the galaxy would contain
easy-and-rich worlds, and every other consideration would collapse into "take those". Deriving it
from the hostility axes is what makes the pillar work:

| Axis | Extreme | Rich in | Adaptation that unlocks it |
|---|---|---|---|
| Temperature | cold | **deuterium** | Thermal Adaptation |
| Gravity | heavy | **metal** | Gravitic Adaptation |
| Pressure | thick | **crystal** | Atmospheric Adaptation |

Deuterium is the resource the research branch already made scarce, and it sits behind the coldest
worlds. That is deliberate: the branch that gates research is gated by the map.

**Why hazards are flags, not a bar.** A hazard is about what happens *over time*; the other axes
are about what the world *is*. As a fifth bar it would be a number the player cannot act on until
colonisation exists. As words — `TidallyLocked`, `IonStorms`, `SeismicInstability`, `ThinCrust`,
`RadiationBelt` — it makes a world memorable in one line and gives slice #10 its content.

Rejected: five independent scalars (unreadable at 393dp, and it makes surveying a spreadsheet
exercise); a single habitability score (kills the three ladders); rolling richness independently
(collapses the decision).

## 2. What each axis does

**In 0.2, the hostility axes do exactly two things and no more:** they set the tolerance check,
and they set richness. They do **not** additionally modify production, cost or duration. Three
axes with three effects each is nine interactions nobody can hold in their head, and the energy
round already showed what an unexplained multiplier does to a player.

One exception, because it has an obvious home and no other: **gravity sets the world's field
count** — how many building levels the world can ever hold. Heavy worlds are big worlds. So
gravity is the cost and the reward twice over: rich in metal, roomy, and the hardest to stand on.

`fields` is **generated and stored, and nothing reads it in 0.2.** It is slice #10's input. Say so
in the code rather than wiring it to something to look busy.

## 3. How a world reads — one verdict, never a stat block

`core` computes the verdict; the screen renders a sentence. Sealed, in this precedence:

| Verdict | Means |
|---|---|
| `Home` | your world |
| `Occupied` | held by someone — carries the holder |
| `Unsurveyed` | charted, traits unknown (see §5) |
| `Blocked` | surveyed, fails one or more tolerance bands — carries **which axis, by how much, and the technology that would fix it** |
| `Barren` | surveyed, passes every band, yield below the worth-it threshold |
| `Settleable` | surveyed, passes, yield above it — carries the yield grade |

`Blocked` naming its own remedy is the design's load-bearing detail. "Blocked — gravity 2.4 g,
you tolerate 1.45 g. Gravitic Adaptation 3 would land it." That single sentence turns the galaxy
screen into a reason to research, which is the only thing connecting two tabs that otherwise never
speak. It is the same move the power card made for energy: state the ratio, then state the
consequence in a sentence.

**`Barren` must be the common answer.** Notion: *"Surveying should frequently return 'not worth
it'."* If a survey usually pays off, surveying is a tax rather than a decision.

Rejected: showing the three raw values as bars with no verdict (makes the player do arithmetic the
game can do, and 5–10 minute sessions cannot afford it); a single 0–100 score (hides *which* axis
blocks, which is the only actionable part).

## 4. The coordinate space

`galaxy : system : slot` — the mockup's `2 : 118` with a slot number under it.

- **4 galaxies × 250 systems × 15 slots** = 15,000 slots.
- A slot holds a world with probability **0.45 for slots 4–10** and **0.20 for slots 1–3 and
  11–15**, so a system averages ~4.75 worlds and the mockup's "4 / 15 occupied" is typical.
- **~4,700 worlds total**, ~1,180 per galaxy. That is Notion's "hundreds of systems", four times
  over.

**Temperature is a function of slot.** `slot 1` is the hottest orbit and `slot 15` the coldest,
offset by star class (`Dim −40 °C`, `Standard 0`, `Bright +40 °C`) plus jitter. This is why the
coordinate is worth having: position *is* a trait, so the map is readable before anything is
surveyed, and "the outer slots are where the deuterium is" is a thing a player learns rather than
a thing the UI tells them.

**Distance metric — shape settled, constants deferred.** The travel-time formula is slice #7's
call and stays open. What cannot stay open is the metric generation is built against, because
placement depends on it:

```
same system         →  |slotA − slotB| × 5
same galaxy         →  95 + |systemA − systemB| × 5
different galaxy    →  2700 × |galaxyA − galaxyB|
```

Distance units, not seconds. Slice #7 picks seconds-per-unit and whether fuel is a cost; nothing
here presumes either. Different-galaxy travel is priced to be a late-game undertaking, which is
Notion's distance decay expressed as geography.

## 5. What is known at the start — two tiers

**Charted** — free, whole galaxy, from the first launch: coordinates, star class, whether a slot
holds a world, and who holds it. It is astronomy; you can see stars from home.

**Surveyed** — per world, earned: the three axes, richness, hazards, fields, and therefore the
verdict.

### The doorstep — added 2026-08-11 (0.5.1), and it is a change to *this* section

**Genesis no longer takes the first tolerable world it walks past. It takes the first one in a
system that also holds a neighbour one adaptation level away.** Written up in `balance-log.md`
round 18; the short version is that §9's targets are galaxy-wide and this section hands the player
a sample of **4.75 worlds**, which is far too small for a 1.8%-and-doubling statistic to be visible
in. Measured over 1,000 seeds, the median home system asked for **seven** adaptation levels across
two ladders before any row on it would say something different, and 78% asked for four or more.

Three things about it belong here rather than only in the log:

- **It moves no number in §8 and no target in §9.** No world's traits change; the galaxy is the same
  galaxy and only the origin moved. This is the one lever aimed at the opening that cannot disturb
  the distribution, which is exactly why it was the one taken.
- **It is not a *good* neighbour, and the measurement is the proof.** The doorstep world reads
  `Settleable` **28.1%** of the time against **51.2%** for the pre-change cheapest neighbour: a world
  one level outside one band sits near the middle of the other two, and richness is derived from the
  axes. *"An easy world is a poor world"* is not bent by this — it is what makes the guarantee safe.
  What a player is given is somewhere to point at, not somewhere worth taking.
- **`fleet-sheet.md` §(b) rejected a guaranteed starter neighbourhood** and this is deliberately
  adjacent to it. See the reckoning recorded there: what (b) was rejected for was guaranteeing
  *richness or worth*, which would delete "the first settleable world should be underwhelming"; this
  guarantees *reachability* and makes that sentence true for the first time, because before it there
  was usually no nearby world at all — and "absent" is not "underwhelming".

At game start **your home system is surveyed and nothing else is.** Surveying is a fleet action,
so it genuinely arrives with slice #7 — until then the galaxy screen is a browsable map where
almost everything reads `Unsurveyed`, and that is the honest state rather than a placeholder.

Why two tiers rather than "nothing is known": a fully dark map is not explorable, it is empty —
there is nothing to *choose* to survey. Charting gives the player a reason to point at one system
rather than another, which is the decision surveying is supposed to reward. It also gives slice 5
a screen with content on the day it ships.

Rejected: everything known (deletes exploration, which Notion locks); nothing known (nothing to
decide between); fog that lifts by proximity (a timer that rewards waiting, and waiting is what
this game already asks too much of).

### A third tier arrived underneath at 0.20.0, and this section is what let it in

**Charted stopped being free.** Davide, 2026-08-16: *"perhaps even progressive disclosure? Eg you
see only nearby systems, and you see more the more you survey"* — ruled the same day as the strong
version, hide unexplored space. The settled design is `fog-sheet.md`; the short version is that
charted is now an interval per galaxy, `[lo, hi]`, and you see one hour of flight past the furthest
place a hull of yours has ever landed.

**The rejection above is not weakened and it is the reason the design has the shape it has.** What
was refused is fog that lifts by *time or proximity* — a timer wearing a map's clothes. This lifts by
the player's own action, which is the opposite. And the sentence two paragraphs up did more work than
any other line in this file:

> a fully dark map is not explorable, it is empty — there is nothing to *choose* to survey

That is a constraint on **how much may be dark**, and it is why nothing on the map is ever black.
Every uncharted star is still drawn, still selectable, still priced, and still offers a probe. What
it loses is its character, not its existence.

**Two sentences in this section are now half true and are left standing as the record:**

- *"Charted — free, whole galaxy, from the first launch"* is 0.2's rule and describes the tier's
  *content* correctly. It is no longer free and it is no longer whole-galaxy.
- *"At game start your home system is surveyed and nothing else is"* is unchanged. What is new is
  that genesis also **charts** 61 systems — the hour either side of home — and nothing in the other
  three galaxies.

## 6. Contested relay nodes — generated, inert

Notion locks *"PvP over contested neutral nodes"*, and the mockup draws one
(`RELAY · CONTESTED · +18% range while held`). PvP is multiplayer-era, so **no holding mechanic
is designed here.**

But they are **generated now**: one system in 40 carries a relay in an unoccupied slot. The
reason is not decoration, it is §7 — generation reads a dedicated sub-stream per feature, so a
relay added in two years' time would shift nothing, *provided the stream exists from the start*.
Adding the stream is one line now and a save-format problem later.

The screen may label a relay as a point of interest. It may not be tappable.

## 7. Generation is pure, O(1) per world, and the save stores a seed

**The galaxy is never serialised.** 4,700 worlds of traits would dwarf the entire rest of the
snapshot. The save stores the **galaxy seed** (one `Long`), plus what the player has changed:
which worlds are surveyed, and who holds what.

So generation must answer for **one coordinate without generating its neighbours**:

```kotlin
fun worldAt(seed: GalaxySeed, at: GalaxyCoordinate): World?
```

Derive a per-world seed by hashing `(seed, galaxy, system, slot)`, then derive a **per-axis
sub-seed** by hashing that with an axis tag. Two consequences, both required:

1. The Compose `Canvas` can render any viewport lazily — slice 5 never holds a galaxy in memory.
2. **Adding an axis later cannot shift the existing ones.** Draw every axis from a named stream
   and the galaxy generated by seed *S* today is the same galaxy under seed *S* after the next
   three slices add fields to `World`. Without this, every future addition silently rerolls
   everyone's map.

Save **schema 4, migrating 3** — 0.0.12's precedent: migrate rather than retire. A schema-3 save
has no galaxy, so migration mints a seed and marks the home system surveyed.

## 8. The numbers

**Revised 2026-08-07, after the constants were measured for the first time** — see §9 and
`balance-log.md` round 5. Gravity and pressure were tightened to meet temperature, which was
already the tightest axis; the worth-it threshold went from 0.90 to 0.92. Nothing else moved.

Tolerance bands at adaptation level 0 — what the species handles unaided:

| Axis | Tolerated at level 0 | Each level widens by | Passes |
|---|---|---|---|
| Temperature | −30 … +45 °C | ∓14 °C | 25.9% |
| Gravity | 0.65 … 1.40 g *(was 0.55 … 1.45)* | −0.05 / +0.12 g | 25.3% |
| Pressure | 0.5 … 2.6 atm *(was 0.4 … 3.0)* | −0.06 / +0.9 atm | 25.0% |

The three pass rates being within half a point of each other is the point, not a coincidence: §1's
argument for three ladders is that *which one you push first* is a real choice, and that stops
being true the moment one axis gates most of the galaxy.

Generation:

| Trait | Distribution | Range |
|---|---|---|
| Temperature | `220 − 28 × slot + starOffset + jitter(±20)` | ~ −260 … +250 °C |
| Gravity | `0.15 + 2.6 × u²`, u uniform | 0.15 … 2.75 g, median ~0.8 |
| Pressure | `12 × u³`, u uniform | 0 … 12 atm, median ~1.5 |
| Fields | `80 + 180 × (gravity / 2.75)` | 80 … 260 |
| Hazards | 35% one, 10% two | 0–2 flags |

Richness, each clamped to 0.6 … 1.6, where 1.0 is "as good as home":

```
metal      = 0.6 + 0.5 × (gravity / 1.4)
crystal    = 0.6 + 0.5 × (pressure / 3.0)
deuterium  = 0.6 + 0.5 × ((20 − temperature) / 60)
```

Yield score weights each richness by that resource's share of the reference colony's **priced**
output — the 698 / 224 / 72 per hour at 1 : 2 : 3 from `balance-log.md`, which is 51% / 33% / 16%:

```
yield = 0.51 × metal + 0.33 × crystal + 0.16 × deuterium − 0.05 × hazardCount
```

**Worth-it threshold: 0.92** *(was 0.90)*. The median world that passes every tolerance band scores
**0.85** — measured, and the sheet's own unrun guess was "~0.84" — so **the median settleable world
is Barren** by construction, because that is the design. The threshold was raised because it is the
one lever that thins the settleable share *without* changing which worlds pass, and so without
disturbing the three comparable pass rates above.

## 9. The distribution is the design, and the sim proves it

These are the targets. The constants in §8 exist to hit them; if the sim disagrees, **the
constants move, not the targets** — with one exception, recorded below, where a target turned out
to be unreachable by any constants.

Across the whole galaxy at adaptation level 0:

| Outcome | Target share of all worlds | Measured |
|---|---|---|
| Passes every band (`Settleable` or `Barren`) | **1 – 2%** | 1.81% |
| Fails exactly one axis — the "come back later" pile | **12 – 18%** *(was 35 – 45%)* | 13.88% |
| Fails two or three — effectively never | the rest | 84.3% |
| Passes *and* clears the threshold — genuinely worth taking | **≤ 0.5%** | 0.35% |

Which lands at **roughly one world in three hundred worth settling on day one**: 17 galaxy-wide,
4 in your home galaxy, one or two within early reach. Each adaptation level roughly **doubles** the
settleable count for the first few levels (17 → 40 → 105 → 218), so the tech has a visible payoff
on the map.

### The one target that had to change, and why

**`Fails exactly one axis` was 35 – 45%, and no choice of constants reaches it.** Davide's call,
delegated to the build on 2026-08-07 after the first measurement.

The first two rows constrain each other. With three independent axes passing at rates *a*, *b*, *c*,
row 1 is `abc` and row 2 is `ab + ac + bc − 3abc`. Holding `abc` inside 1 – 2% caps row 2 at about
**16%** whenever the three axes are near each other. The most *balanced* pass rates that reach 35%
at all are roughly **0.06 / 0.58 / 0.59** — one axis blocking 94% of worlds while the other two wave
nearly everything through.

That is a galaxy with one ladder that matters and two that do not: the single-habitability-score
design §1 rejected, arrived at from the other side. So the row was corrected to what a three-ladder
galaxy actually produces, and §1 stands. If the "come back later" pile ever needs to be bigger, the
lever is **not** this row — it is widening all three bands together, which raises row 1 with it.

`:sim:run` prints the actual distribution against this table. A `GalaxyDistributionTest` asserts a
large seeded sample stays inside the bands — the same treatment `ResearchBalanceTest` gives the
research tables. A property test that pins a distribution is unusual; it is warranted here because
the distribution *is* the mechanic, and a refactor that quietly makes the galaxy generous would
otherwise pass every other test in the repo.

## What it should feel like, to check next round

- **Surveying should usually disappoint**, and that should be fine rather than annoying — because
  the charted map made you choose where to look, so a `Barren` result is information you bought.
- **Being `Blocked` should read as a shopping list, not a wall.** The first time a player sees
  "Gravitic Adaptation 3 would land it" next to a metal-rich world, the research tab should stop
  being a place they visit out of duty.
- **The first settleable world should be underwhelming** — nearby, mediocre yield, and obviously
  worse than the one two galaxies away they cannot reach. That tension is the whole pillar.

## Left open, deliberately

- **The travel-time constants** (§4) — slice #7, unchanged.
- **The adaptation technologies themselves.** This sheet names three and specifies what they
  widen, but they are a research-branch change and the 0.1 branch is closed at three technologies.
  Whether they join as rows four to six, or become a second branch, is Davide's call and belongs
  to the slice that adds them. Until then every empire sits at level 0 and `Blocked` worlds stay
  blocked — which is honest, and is why slice 4 does not pretend to deliver colonisation.
- **Whether `Barren` should be permanent.** Terraforming is named on Notion as part of the
  adaptation-and-terraforming tree. If a world's traits can be *changed* rather than only
  tolerated, richness changes with them and the yield maths above becomes a moving target. Not a
  0.2 problem; flagged because it is the one thing here that a later decision could invalidate.
- **The five energy calls** from balance round 3 are still open and untouched by this sheet.
