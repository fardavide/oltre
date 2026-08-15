# The drawn map decision sheet — 0.12

Written by the build, 2026-08-15, on Davide's report after playing 0.11.0:

> *"we're still not there with the Galaxy map: the filters and search are useless, and I want a real
> map! I want to be able to visualise my system (and we kinda have that), and also visualise the
> galaxy and the universe. I think this would improve a lot the identity thingy"*

Claude Design answered it as **Looks Near Is Near**, archived at
[`design/looks-near-is-near.dc.html`](design/looks-near-is-near.dc.html). This sheet is the prose
record of every call in it, in the repository's voice, plus the four the build had to take on its own
and the three of Design's premises that did not survive contact with the code.

**This sheet extends `galaxy-identity-sheet.md` and supersedes two of its conclusions** — the ledger
as the landing screen, and the region index as a screen. Both are named below with the argument that
retired them. Nothing in `galaxy-sheet.md` moves: no tolerance band, no richness formula, no
`verdictFor` case and no `GalaxyDistributionTest` target.

---

## The one-sentence version

**The galaxy is one-dimensional, so the drawing has to agree with it** — and the whole design is one
move: fold the line into ten bands and let each band be a region, so path order stays index order and
a distance on the drawing is a distance in the game.

---

## 0. The finding that framed the whole round trip

`galaxy-identity-sheet.md` §0 said it and 0.11.0 half-answered it:

> Names, portraits and a ledger all make the map *nicer to use*. **Only spatial structure makes it
> knowable.** … A map with no spatial structure has addresses, not places.

0.11.0 built the structure — ten named regions per galaxy, really biasing the stars, pooled
identically for every seed. What it did not build is a way to **see** it. The regions existed as nine
hairline breaks on a strip and a ten-row index. So Davide's report is not a contradiction of that
slice; it is the second half of it.

### What the build measured before asking Design anything

Three readings, none of them a guess, and the first one changed what Design was allowed to draw.

**The galaxy has no geometry.** A system is an index in 1…250. Every travel cost in the game is a
function of the difference of two indices and nothing else: `SurveyBalance.distanceUnits` prices a
probe hop at `|Δsystem|`, `FleetBalance.distanceUnits` prices a run at `95 + 5·|Δsystem|`. **There
was nothing to draw.** A drawn galaxy is therefore a choice between a layout that agrees with the
metric and a metric that agrees with the layout, and the two are not symmetrical — the second re-quotes
every new dispatch, moves the reach ruler's hour marks and re-splits the legs of runs already in
flight. Davide's call, before the prompt went out: **the layout moves.** Recorded in `decisions.md`.

**What a galaxy draw costs**, measured on a desktop JVM with a throwaway harness deleted the same
session:

| pass | cost |
|---|---|
| star class for all 250 systems | 55 µs |
| generated name for all 250 systems | 144 µs |
| every world in the galaxy — 250 × 15 `worldAt` | 777 µs |

The ratio is the finding rather than the absolutes: **the entire charted tier is free to redraw every
frame, and a per-system world count is fourteen times the cost of it** — which is convenient, because
the charted tier is exactly what the knowledge tiers permit a galaxy view to show.

**Ten region names do not fit on one dimension.** 0.11.0 drew them, measured them and rejected them:
a region is 33dp of a 393dp strip and its name is 68–100dp of type at the 9.5sp floor, so ten of them
overlap two and a half times over before one is legible. That measurement is what the second
dimension was bought for, and it is the whole argument for the fold.

---

## 1. The fold — ten bands, and each one is a region

A serpentine whose path order is index order. Twenty-five stars a band at a 14.0dp pitch at 393dp
(11.0dp at 320dp), odd bands running right to left, a drawn turn at each end so the ten read as one
folded line rather than as a grid, and a deterministic perpendicular drift so a band reads as sky
rather than as a table.

**A region falls out for free**, and that is the part worth stating plainly: a region *is* a
contiguous run of twenty-five indices, so on any index-monotone layout it is automatically a connected
stretch. The bands are not a grid imposed on the data. They are the regions.

The geometry, which is one measurement rather than two:

| | |
|---|---|
| a band | 13dp of label, a 32dp lane, 9dp of gap |
| ten of them | 531dp, less the last gap |
| the content area | 587dp at 393dp, 570dp at 320dp |

**So the same drawing fits a phone and a Slide Over pane**, and there is one geometry to keep when
either moves. Nothing scrolls, nothing pans and nothing pinches.

### The one lie the fold tells

Two stars stacked across a band gap are twenty-five systems apart and drawn 22dp apart; horizontally,
twenty-five systems is 337dp. **That is a fifteenfold understatement in one direction**, and it is the
price of folding — a straight line at true pitch is 3,500dp and fits on nothing. Three things pay it
down, and all three are in the drawing rather than in copy: the turn is *drawn*, so the eye is handed
the path; the labels alternate sides, which states the reading direction without an arrow; and
vertical neighbours are always in different named regions, which is the strongest "not next door"
signal the map has. **If it reads as a grid on a device, the next lever is the band gap and not the
shape.**

### What a glyph carries, and what it deliberately does not

Size and luminance are star class. Colour obeys the world portrait's rule — no status hue ever lands
on a celestial body — so heat is brightness, the cold end leans the deuterium violet, about a third of
the brights carry a crystal-leaning halo, and each band sits in a 5–10% tinted field of its
temperament.

It does **not** carry a world count. That is the 777 µs figure, and a fourth channel on a 3dp dot
besides. The caption carries it, for one system, on demand.

### Two things the seed had to start generating

`GenerationAxis.LAYOUT`, appended after `RING` — free, per the enum's own rule, and the test suite
passing unchanged is the evidence that it was. Three draws off the one tag:

- **drift**, ±500 permille of a pitch. Capped at half a pitch so it can never reorder two stars,
  which is the safety argument for the whole drawing in one number.
- **size wobble**, 820–1180 permille of the class radius, so two standards are siblings rather than
  clones and never a promotion.
- **halo**, which of the two hues a bright star wears — generated rather than picked at draw time,
  because "every third bright" chosen by the renderer would depend on iteration order and two players
  on one seed have to be looking at the same sky.

---

## 2. What the map knows about you

Four overlays, each on its own ring, none of them leaking anything a survey has not paid for. **A
glyph may say how bright a star is, which region it is in and whether you have been there. It may
never say what is orbiting it.**

| mark | means |
|---|---|
| a thin ring at the disc's edge | you know a world here |
| an amber ring | a probe is in flight to it — the fleet strip's amber, meaning what it means there |
| a wide white ring | home |
| an accent ring | the selection |

They stack rather than compete: your own star can be selected, and a system can be surveyed with a
probe still on its way back to it.

### The defect this design would have shipped, and did not

The obvious source for "have I been here" is `GalaxyState.hasSurveyed(system)`. **It is vacuously
true for a system with no worlds in it** — every one of its zero worlds is in the set — so a map built
on it would have ringed hundreds of empty systems nobody has ever sent a probe to. It is also the
expensive call, at fifteen `worldAt` per system.

The map tests membership of `galaxy.surveyed` instead, which is the save's own set of world
coordinates. Cheaper by a factor of fourteen, and *right*: a system has a ring when you know a world in
it, which is what a player means by having been somewhere. There is a test pinning an empty system as
unringed.

### A pin is what makes a name appear

You cannot print 250 names — 144 µs says the drawing could and the screen says it cannot. You can
print the four or five you have marked. So on the map **a pin does not sort anything: it is what
makes a name appear**, which is the whole of search on a drawing. It is exactly the state 0.11.0
already stored, and a system is labelled when it holds a pinned world, which is a derivation rather
than a new field.

This is why the search box is not missed on the map. Search asks *where is the place I named*; the map
has already written the answer next to the star.

---

## 3. The caption — the map's one readout and its one control

Never empty, and that is a design rather than a default: the map opens with home selected and a tap
can only move the selection, never clear it. So there is no "nothing selected" state to design, no
placeholder copy, and no dead bar at the foot of the screen — and the first thing the tab ever shows a
new player is their own star, named, with its own clock on it.

**The whole bar is the 44dp target**, which is what lets the stars be 3dp across and cost nothing to
miss: you scrub with a thumb anywhere on the drawing and act down here. A tap opens the system the map
has selected, which is the tab's one real push.

The trailing element is the only other thing that can be tapped, and the rule it obeys falls straight
out of the knowledge tiers:

> **Stars are probe targets; worlds are run targets.**

A probe is aimed at a star, so the map may aim one. A run is aimed at a world, and worlds are what a
survey pays for — so on a system you already know the map quotes the round trip in plain text and
sends you to the orbit page, where a run has always been chosen per world.

---

## 4. The universe — one gesture up, not a screen

Four galaxies swap into the map's own frame from the header chip. Nothing pushes and there is nothing
to come back from; the tab bar never changes under you. Each disc is the same fold at a fifth of the
size, drawn from that galaxy's own temperament permutation, so it is real texture rather than
decoration — and it costs one more sweep of the 55 µs.

**What four discs can mean today is one thing, and it is real: what it costs to get there.** The four
are not equidistant. A neighbour is a 9h 20m round trip and the far corner is 18h 20m, against 3h 22m
to cross your own galaxy end to end — so a hop is nearly three times the longest journey you can make
at home.

What it leaves room for: three empires arrive as three tinted discs and a holdings count on the line
that reads `0 surveyed` today; multiplayer arrives as the same line with a population. Neither needs a
new surface and neither is drawn now, because a frame showing an empire you cannot meet is the thing
the brief called identity-only.

---

## 5. What the worlds list keeps, and what it loses

Davide's verdict was *"the filters and search are useless"*. Design diagnosed it before replacing it,
and found a floor under the build's own reading:

> **The ledger's rows are worlds and the outbound question is about systems.** A probe is aimed at a
> star. So the ledger could not answer "where next" filtered, sorted or neither — wrong unit, before
> you reach the controls.

| control | call |
|---|---|
| the five filter chips | **gone.** They narrowed a list fourteen rows long on a day-21 save, and every axis they filtered on — distance, verdict, region — is now something you can see rather than request |
| the sort | **gone.** A sort ranks on one axis and "where next" is distance against class against region against what is still unknown. The map holds all four at once, which is the one thing a drawing does that an ordering cannot |
| the search | **stays**, on the worlds list only. It matches names you have learned, which is the definition of a list job. It was useless on the old landing screen for a reason that was never about search: you were being asked to type the name of a place you had not been to yet |
| the pins | **stay**, and gain a second job — the system holding a pinned world is the one the map writes a name against. One state, two surfaces, no new field |
| the count | stays, and loses the sort control that shared its line |

The list is now nearest-first without being asked. That is a fact about the list rather than one of
four orders you could pick: a list of places you already hold has one obvious reading, which is which
of them you can reach soonest.

---

## 6. Davide's calls

| Question | His answer |
|---|---|
| May the drawn layout move the travel metric? | **No — the layout moves.** A free 2-D scatter would re-open numbers three balance rounds have settled |
| Which clock owns the map's hour marks | **The probe's.** Two rulers over one drawing is not survivable, and a probe is the only thing the map can aim |
| Where the tab lands | **The map, and thereafter wherever you last were.** Design's call taken, with one amendment |
| What to do while the round trip was out | Measure the draw cost rather than build against a guess |

### The amendment, and what it cost

Design landed the tab on the map and argued it against its own 0.11.0 position:

> 0.11.0 argued the ledger, on the grounds that *"five honest rows beat 250 dots of which one is
> yours"*. That argument was against a dot field and it still defeats one. It does not survive a map
> that is named, banded, and prints the places you marked.

Davide took it and added that the tab should then follow whichever of the two lists was last used —
**which breaks Design's own rule that nothing this tab remembers reaches the save**, knowingly. It is
one field, in a `preferences.json` beside the colony rather than in it, for a reason worth keeping:
a preference must never be able to cost somebody a colony, and separate files mean a corrupt one of
either kind takes only its own down. The composition root is the only place that knows what the stored
name means, because `:client:save:data` may not see a `presentation` module.

Only the switch writes it. Not the scale chip and not the push: the universe is a state of the map and
the orbit page is somewhere you go *from* it, so neither is a place a tab could land — and a player
who ended a check-in inside a system should come back to the map they reached it from.

---

## 7. Where Design's premises did not survive the code

Design asked for these to be named. Three of its own, and two of the build's.

**A galaxy is not a probe target.** The universe frame put a `probe 4h 40m` button on its caption.
There is no system for it to point at — `SurveyBalance.duration` takes a `SystemAddress` — so the
universe caption quotes the hop as a *reading* and its tap enters the galaxy instead. The rule the map
already obeys is what settles it: a probe is aimed at a star.

**There is no probe sheet, and never has been.** Design described the caption's ghost as *"opening the
dispatch sheet already pointed"*. The game's probe verb is a one-tap dispatch in the orbit page's
footer, and the caption's ghost is that same verb with the same price on it.

**Four hour marks is a home-galaxy number.** Design drew `1h`…`4h`. A galaxy hop costs 4h 40m before a
single system of travel, so those four would leave a foreign galaxy with no ruler at all. The map
draws hours 1…9 and keeps whichever land on it — which is four at home, and the right ones abroad.

**The starfield could not simply be switched off.** Design is right that decorative stars and real
ones cannot share a screen, and at 320dp the shell's third parallax plane really does read as extra
dim systems. But the starfield is drawn by `MainScaffold` *inside the destination box*, under every
screen, and a feature cannot reach up and turn it off. The map paints its own opaque ground over it,
which is one rect and needs nothing hoisted. The worlds list keeps the sky.

**No zoom is Design's own retracted premise, and it is worth keeping retracted.** It proposed a pinch
and then argued itself out of one: a pinch buys names for unpinned systems and nothing else, because
class, region and position are already legible at a 14dp pitch — and the caption gives you those names
one at a time under your thumb with no camera to restore on a foreground. **No zoom is a feature of a
five-minute check-in.** If a device session says otherwise, the geometry does not change; only the
camera does.

---

## 7b. Two numbers the drawing got wrong, and what found them

Both were caught by tests that **execute the drawing** rather than photograph it —
`GalaxyMapDrawingTest`, which hands `drawFold` a `CanvasDrawScope` over an `ImageBitmap` and reads
back where a star landed. A recorded frame of 250 dots cannot tell a correct one from a wrong one.

- **The drift was half what the design asked for.** The permille is already a signed fraction of the
  cap, and halving it again put every star at a quarter pitch of travel where the design asked for a
  half — 3.5dp of wander instead of 7 at 393dp, which is a band that reads as a ruled line with a
  wobble rather than as sky.
- **The size wobble could promote a star.** At Claude Design's 820…1180 the widest standard is 2.24dp
  against a narrowest bright of 2.13 — a standard genuinely drawn larger than a bright, on a map
  whose entire legend is that size is class. `core` now draws 870…1130, the widest band that keeps
  both gaps open.

And a third, from the coverage table rather than from a test: **thirty-four lines of `SystemMap`
stopped being executed by anything.** The probe arc on the home orbit page was covered incidentally,
because the behaviour suite used to dispatch a probe from that page's own footer and stay there to
watch the countdown — and the map is where a probe is aimed from now. It has a frame of its own since.

## 8. What it should feel like, so the next round can tell

Opening the tab should feel like unfolding a chart, not opening a database. The test Design set, and
the build has kept, is whether somebody who has read none of this can say four things out loud after
five seconds on a day-one frame:

> *I live in a dark region. The bright places are three bands up and three bands down. I am near one
> end. That one is an hour away.*

If any of those comes back in Davide's own words, unprompted, the fold worked. **If the report is that
it looks like a spreadsheet of dots, the fold failed and the answer is a bigger band gap before it is
a different shape.**

The check-in test is separate and stricter: **finding a place to send the next probe should take one
drag and one tap**, and the map should be a worse place to loiter than the ledger ever was. Nothing on
it rewards being looked at twice.

---

## 9. Open, and Davide's

1. **Is the fold legible at 320dp on a real pane?** The pitch drops from 14.0dp to 11.0dp and a star
   is 2.6–5.2dp across. It is inside the 393dp geometry by construction, which is not the same as
   being readable.
2. **Does the caption's ghost want a confirmation?** It dispatches on one tap for 150 metal, which is
   what the orbit page's footer already does — but the footer is reached deliberately and the caption
   sits under a surface you scrub with a thumb.
3. **Is nearest-first the right and only order for the worlds list**, now that the sort is gone? A
   list of held worlds has one obvious reading; a list of forty might not.
