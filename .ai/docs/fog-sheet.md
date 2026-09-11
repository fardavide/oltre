# An hour ahead — the fog sheet

**The settled design for the third knowledge tier**, from the Claude Design round trip of
2026-08-24 (`An Hour Ahead.dc.html`, project `aea4cd09-c111-4e9a-8b7d-c25cea371fd4`), built and
shipped at 0.20.0. Read it before touching `ChartedSpan`, `SurveyBalance.GRACE_SYSTEMS`, or anything
in `GalaxyMap.kt` that decides how a star is drawn.

Issue #84. It answers a question Davide asked on 2026-08-16 and ruled the same day.

## The one-sentence version

**Fog is one interval and two integers: you see one hour past the furthest place a hull of yours has
ever landed.** Inside it the map is exactly what shipped at 0.12.0. Outside it every star is still
drawn, as grain — a point you can see, at an address you can read, with a flight time on it, and
nothing else. Nothing is black, nothing refuses a tap, the fold stays 531dp, and the save gains eight
numbers in total.

## 0. What this had to not break, and it is the thing that was working

Davide, unprompted, on the TestFlight build of 0.12.2:

> The last map rework is working great by the way! I really feel like a sense of progression, and the
> named system is also very nice! … I can see *"oh, this is the area I unlocked with my own actions"*

That sentence is the sheet's own goal met, and fog subtracts from exactly the image producing it. The
two readings genuinely pull apart: what makes the map feel like **progress** is seeing the region you
opened against the ones you have not, which needs the unopened ones visible; what makes exploring
feel like **discovery** is not being able to see them at all.

**The resolution is that the win was never legibility.** `drawn-map-sheet.md` §8 predicted the report
would be *"I live in a dark region, the bright places are three bands up, that one is an hour away"*
— and no report has ever read a *distance* off the fold. What arrived instead was **ownership and
memory**. So fog runs *with* the grain of what actually landed rather than against it, provided the
unopened regions stay on screen. Which is why nothing is ever black.

## 1. Dark is grain, and it is drawn

An uncharted star is one size, one value, no halo, no spike, no class. You can see it is there and
where it is; you cannot see what it is.

**That is the third tier and it is also the *sensed* fourth the brief asked about, so there are three
states and not four.** There is no blacker tier underneath, because a tier you cannot choose to
survey is a tier with nothing in it — which is `galaxy-sheet.md` §5's own constraint, met rather
than bent.

| tier | what it says | what buys it |
|---|---|---|
| **uncharted** | position, address, flight time | — |
| **charted** | name, star class, region, world count | a hull landing within an hour |
| **surveyed** | traits, portrait, deposits, verdict | a probe on that system |

**Survey does not buy a star's existence. It buys its character** — and the map has been saying so
since 0.12.0, where texture was already the channel knowledge rode on.

## 2. The light is an interval, not a set of lit dots

The ribbon's path order is index order, so a rule written in indices is a **contiguous stretch of the
drawn line** — which is the thing a player can point at and call the area they opened. Charted is
`[lo, hi]` per galaxy and nothing else.

Said in one sentence to a player: **a probe charts everything nearer than where it went, and an hour
further.**

- `GRACE_SYSTEMS = 30`, derived rather than written down: `SurveyBalance` is 30 minutes of base plus
  a minute a system, so an hour of flight reaches exactly thirty. It is `(60 − BASE_MINUTES) /
  MINUTES_PER_UNIT`, computed inside `SurveyBalance` because both operands are private there.
- On a landing at system *n* in galaxy *g*: `lo = min(lo, n − 30)`, `hi = max(hi, n + 30)`, clamped
  to the galaxy's ends. Idempotent and monotone.
- Genesis writes `home ± 30` once, through the same function — so genesis is an instance of the rule
  rather than a special case of it.
- A galaxy no hull has landed in has **no interval at all**, which is different from an empty one:
  *never been* rather than *been and charted nothing*.

**Deriving the grace is the deliberate half.** The grace *means* an hour of flight, so a rebalance of
the probe's clock should move the map with it rather than leave a constant behind quietly meaning
something else. `GalaxyChartedTest.the grace is one hour of the probe's own clock` is what says so out
loud if it ever moves. ⚠️ `SurveyBalance` declares itself PLACEHOLDER balance in its own header, so
that is a live coupling and not a theoretical one.

### The frontier is never a wall you are standing on

Because the hour of grace travels with you, there is **always an hour of charted runway ahead of the
last place you reached**, with real names on it, to aim the next probe at. You never aim into
nothing: you aim at the last thing you know, and it moves.

## 3. Why the interval is keyed on the landing and not on the survey

**This is the load-bearing half and it is the one the build got wrong first.**

`galaxy.surveyed` records *findings*. Fog is about *journeys*. The two are the same object only when
every journey finds something — and a landing on a system whose fifteen slots are all empty writes
not one coordinate to `surveyed`. A span derived from that set would silently refuse to move on
exactly the flight the player most wants counted.

So the rule is: **a survey is a finding; a flight is a fact. Fog is built on the fact.**

⚠️ **Design's stated frequency for that case is wrong by a factor of about forty, and the number
matters because two of its arguments lean on it.** The sheet says *"about one system in eight is
empty"*. Measured over 6,000 systems across six seeds and four galaxies each:
**18 of 6,000 — 0.30%, one in 333.** The arithmetic agrees: `0.55⁷ × 0.80⁸ = 0.26%`, from
`GalaxyBalance`'s two slot-occupancy rates. `Galaxies.kt` already documented it as one in 390. A
median galaxy holds **zero or one** entirely-empty system; the worst of the twenty-four measured held
four.

**The call survives the correction and both arguments it supported get weaker rather than dying:**

- The rejection of per-region fog rested partly on *"the empty-system probe leaves a whole region
  dark, one time in eight"*. At one in 333 that is a rare event rather than a common one — but
  per-region fog was rejected for two further reasons that do not depend on frequency at all (the
  frontier jumps rather than moves, five times in a galaxy; and it makes the band grid load-bearing,
  which the fold spent 0.11.3 arguing against). Those carry it.
- The eight integers were justified partly by *"roughly one probe in eight reveals nothing"*. At one
  in 333 the zero-storage version is nearly always right. It is still worth the eight numbers: the
  failure is **silent and indistinguishable from a bug** to the player, it is likeliest to bite on the
  frontier probe (the record-holder, and the longest flight they paid for), and eight integers is not
  a cost worth arguing about.

**The general shape is round 27's lesson again** — a constant derived from a premise, where the
premise was never checked. The premise here was a *frequency*, which is cheaper to check than most.

## 4. Where the design's premises did not survive the code

Three, and the first is the sharpest.

### 4a. "An uncharted star still offers the probe" was false in three places

The design says every grain star answers and offers the same button every other star offers. The
shipped code refused a **worldless** system outright — `startSurvey` returned `AlreadySurveyed`,
because `hasSurveyed` is *vacuously true* when a system has no occupied slots — and the caption said
`no worlds`. Under fog that does two forbidden things at once: it **leaks that emptiness for free**,
which is precisely what the tier exists to stop, and it **withholds a control every other star has**.

Davide's call, 2026-08-24: **offer the probe; refuse only once charted.** So `startSurvey` now refuses
only where the star is *both* charted and fully surveyed —

> a probe is refused only where there is nothing left to learn, and on an uncharted star there is
> always the map itself to learn.

That overrules `AdvanceSurveyTest.a star with nothing around it cannot be dispatched to at all`
(2026-08-16), and note **why** it is fair to overrule rather than merely overruled: that test's own
comment justified itself with *"whether a slot holds a world is charted, free and galaxy-wide"*. Fog
is the thing that made that sentence untrue. It also makes the design's own justification in §3
*true* — a worldless landing is now reachable, so the case the rule was built for exists.

### 4a-bis. The orbit page was the whole tier's back door, and Design had ruled it out of scope

**The caption's entire 44dp bar is a tap target**, and the tap opens the orbit page for whatever the
map has selected. So a player could scrub to any grain star, tap once, and read the name, the region,
the star class, the world count, the drawn orbits and the relay that the bar two dp above had just
refused to say. Every one of those is charted-tier. A behaviour test found **eight nodes** naming the
star on that page.

Design's brief said *"what we are not asking for: a redraw of the orbit page"* — which is exactly how
this got missed on both sides. Davide's call, 2026-08-24: **render an uncharted orbit page.** It is
the same page with four fewer facts rather than a new screen, and every string it needs already
existed from the caption:

```
[3:240]                          69 systems out
UNCHARTED · CHARTS 49 SYSTEMS
440 units out · danger 1 from here
        ( the star, alone — no orbits, no rows )
150 metal · flight 1h 39m            [Dispatch probe]
```

**The tier is applied once, at the top, by handing the page an empty world list.** Eight surfaces
read `worlds`, and one decision where the body is built is cheaper and harder to forget than eight
guards further down. Two consequences worth stating:

- `toProbeActionUiState`'s two early branches — `worlds.isEmpty()` and `hasSurveyed` — both had to
  gain the same `hasCharted` clause `startSurvey` gained, because **this footer's whole job is to
  offer exactly what the verb would accept.** Without it an uncharted worldless star said *nothing to
  survey · 15 empty slots* on the page while the map two dp up offered the flight: the leak and a
  dead control in one row.
- The region row keeps its tap back out to the fold, reading `UNCHARTED`. A control that still works
  is what let the word be swapped rather than the row removed.

### 4b. The selection leaked a name, and no assertion caught it

`namesFor` names home, every pin and **the selection**. Home and pins are charted by construction —
a pin requires a survey, a survey requires a landing, and a landing is what set the span. The
selection is not: a thumb parks it anywhere.

So the first recorded frame of an uncharted selection drew the star's real generated name —
*Elyomar* — eight dp from a caption saying `[3:240]` precisely because there is not one. The tier
leaking through the loudest channel it has.

**Found by looking at the baseline, not by any test**, which is what the baselines are for. Design's
own component had the guard (`if (!a || litOf[l.n] <= 0) return`) and the implementation had dropped
it. There is a test now.

### 4c. "A first landing in a dark galaxy is the largest single reveal" is false, and the sheet says so twice

The sheet's §4 claims *"one probe charts 61 systems at a stroke — that is the largest single reveal
available anywhere"*. It is not, and the sheet's own §4 contradicts it three sentences later with
*"two long probes, to [3:250] and to [3:1], chart the entire galaxy"*.

The reason is the shape of the rule: the light is **one interval with two ends**, so a probe sent
*past* everything you hold back-fills the whole gap behind it as well as opening the hour in front.
From genesis at `[3:171]` with `[141, 201]` charted, a probe to `3:1` — 200 minutes, reachable on the
first evening — merges to `[1, 201]` and charts **140 systems**, against 61 for a first landing in a
fresh galaxy and 30 for stepping the frontier out an hour.

**So the optimal probe is the longest one you can afford, and that is the design working rather than
a hole in it** — §6's whole argument for fog reducing probe-spam is that the fog-motivated probe is
always the longest flight available. What was wrong was only the sentence naming a maximum. Caught by
an adversarial review of the diff, against a changelog line that had copied the claim.

### 4d. The universe view lost its only differentiator, and that is intended

The 0.12.0 sheet gave each disc real class texture on the grounds that texture is free. Under fog it
is a **leak** — three of the four discs would advertise which galaxy has the brightest stars before a
hull ever left home. Grain fixes it and costs the drawing nothing, but it means the four cards now
differ only by their fare and their charted count. Design flagged it in as many words:

> That is honest and it is thin. I would rather it were thin than lying, and I would rather you knew
> it was thin before you look at it.

A consequence rather than a defect, and it is what `galaxy_universe.png` now records.

## 5. What it cost the layout — nothing above the drawing

The premise that fog would need room was the third that did not survive: both surfaces it uses were
already on the screen and both were already saying less than they could.

| | |
|---|---|
| above the drawing | **0dp.** The fold is 531 at both widths, unchanged. Fog is drawn inside the map or it is not drawn. |
| the band label row | 13dp, already paid for by the ten region names. A dark band spends it on its index range — `1–25`, `226–250` — so **the row is never empty and the map never has holes in it**. |
| the count line | The head's second row already counted things. `61 of 250 charted · 1 surveyed` is fog's whole readout. |
| the grain | r 1.05dp against the dim class's 1.3, one flat value at 24% white, no halo and no spike. |
| the fifth ring | There is none. Fog cannot be a ring on a 2.6dp dot — so it is the dot itself, and the four overlays stack on top of it unchanged. |
| never taken | Position, address, and the hour marks. **Distance is the fact you choose a target with**, so fog does not touch it: the ticks run to the edge of the galaxy through the dark. |

**It is not a second progression gauge.** The player strip's own gauge is 8dp above the drawing. The
strip counts what you *are*; the count line counts what you have *looked at*.

### Two numbers that are the same colour by argument rather than by coincidence

- `GRAIN_STAR` is `Color(0xFFE2E8F5).copy(alpha = 0.24f)` — **`STANDARD_STAR`'s own hue at a third of
  its value**. Not a new colour: an uncharted star is a star nobody has looked at yet, not a
  different kind of object.
- The band's index-range label is `OltreColors.text.copy(alpha = 0.24f)`, and `OltreColors.text` is
  `0xFFE9EDF5` — the design's `rgba(233,237,245,.24)` exactly, with no new constant at all.

## 6. The shape in code

- **`ChartedSpan(galaxy, lo, hi)`** in `GalaxyState.kt`, and `GalaxyState.charted: List<ChartedSpan>`
  **last** in the primary constructor, because `GameSaveTest.the on-disk shape is pinned` asserts the
  encoded string and kotlinx writes fields in declaration order.
- `spanIn` / `hasCharted` / `chartedCountIn` / `withCharted` / `wouldChart` on `GalaxyState`.
  **`wouldChart` is defined as a difference of `withCharted`** rather than re-derived, so the number
  the caption quotes and the number the landing writes cannot drift.
- `hasCharted` is **never vacuously true**, unlike `hasSurveyed` directly above it. A span contains
  an index or it does not.
- One writer: `Advance.kt`'s `landed` loop, beside the line that writes `surveyed`. Two tiers, two
  different facts, one place.
- **`MapStarInk` is sealed** — `Grain` or `Charted(starClass, sizePermille, coolHalo)` — rather than
  a class beside a boolean, so an uncharted star does not *carry* a class every drawing pass then has
  to remember not to use. The leak is unrepresentable rather than merely untaken.
- `Fold.grain` is 1.05dp full / 0.62dp mini and does **not** go through `radiusOf(starClass,
  sizePermille)`, which multiplies by the wobble on every path. Grain takes no wobble.
- `drawRegionField` **clips** rather than narrowing: the vertical squash is derived from `radiusX`,
  so shrinking that would flatten the band as well as shorten it. And `fold.x` reverses on odd bands,
  so the ends of the *range* are not the ends of the *stretch* — five of the ten need min/max the
  other way round. A fully-charted band short-circuits and draws byte-identically to before.
- Schema **17 → 18** folds the save's own contents (schema 15's precedent): a colony carried forward
  wakes up charted around every system it ever surveyed, widened by the grace. It cannot see past
  landings that found nothing, so it comes back at worst an hour narrower than it truly was — which
  is the direction to be wrong in, since the span only ever widens from there.

## 7. What it should feel like, so the next round can tell

> It should feel like carrying a lamp, not like someone holding a curtain.

The galaxy is all there, all the time; the thing you are moving is how much of it you can read.
Nothing is taken from a player who already has it, and the only thing fog withholds is the character
of places nobody has been.

0.12.0 was reported back as **ownership** — *this is the area I unlocked*. If this one works, the
report gains a **direction**:

> *"I have been pushing up-galaxy"* · *"I went the wrong way and there is nothing over there"* ·
> *"I finally reached Daxath Blaze"*

**A report that names a heading is the test.** A report that says the map got smaller is the failure,
and the first lever if it comes back is the hour of grace — thirty systems is the one number in this
design that is a matter of taste.

## 8. Open, and Davide's

1. **Nobody has held it**, and the whole design is a claim about how a dark map *feels* on a phone.
   0.15, 0.16, 0.17 and 0.18 are also uninstalled, so the scarcity fog composes with is itself
   unfelt. Same shape as the tilt loop in `session-roles.md`.
2. **A charted star past your furthest landing also extends the light, and its caption does not say
   so.** Design left it silent deliberately, to keep the shipped caption untouched and the new clause
   rare — but it means the yield is discoverable on the dark side of the frontier and invisible on the
   near side. *"If the next round says players stop pushing once the names run out, that clause is the
   first thing to try."*
3. **The distance-scaled probe price is ruled and not built** (#83). Design's call is **fog first, the
   curve immediately after, not in the same release** — because fog changes what the curve is pricing:
   a far probe now buys a survey *and* a stretch of map, so the curve's real job becomes keeping
   **metal-per-system-charted roughly flat**. A curve tuned before fog would be tuned against a good
   that no longer exists.
4. **Whether fog pushes probe-spam down, and by how much.** Design's estimate is that fog adds between
   two and about nine surveys over a galaxy's whole life, and that the marginal fog value of a second
   probe at a nearby system is exactly zero — so the fog-motivated probe is always the *longest* one
   you can afford, which parks your only hull for hours. If that holds it is the first mechanic in the
   game that competes with spam for the same scout. `:sim:run` is what would measure it; it has not
   been run against this.
5. **The 320dp band label.** `226–250` is eleven characters at the 9.5sp floor and the baseline says
   it fits, which is not the same as a real pane.
