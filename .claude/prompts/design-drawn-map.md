# Claude Design prompt — a drawn map: system, galaxy, universe

Ready to paste into Claude Design. Written 2026-08-15 by the local session, against
[issue #69](https://github.com/fardavide/oltre/issues/69) and `.claude/docs/galaxy-identity-sheet.md`.
Local→Design is a **round trip**: the local session waits for the sheet to come back and then builds
it.

Issue #69 carries a first draft of this prompt, written at 0.11.0. **This file supersedes it.** Four
things were added after checking the draft's premises against the code, and the first of them is the
one that changes what Design may draw:

1. **The galaxy has no geometry at all** — a system is an index in 1…250 and both travel metrics are
   functions of `|Δsystem|` alone. Davide's call, 2026-08-15: the drawn layout must agree with the
   metric rather than the metric moving to meet the drawing. §"one-dimensional" below.
2. **The draw cost is measured** rather than guessed — three numbers, taken on this machine.
3. **The strip already rejected region names on itself, with a measurement.** Design should not
   have to rediscover that at the same width.
4. **Thirty-one screenshot baselines cover the tab today.** A redraw is a cost with a number on it.

---

```
Oltre is an asynchronous space-colonisation strategy game in the OGame lineage — Kotlin Multiplatform,
Compose Multiplatform, iPhone is the delivery target. Check-ins are 5–10 minutes; everything
progresses while the app is closed. It is at 0.11.2.

You designed the 0.11.0 identity slice: regions, names, epithets, planet discs, the discovery card,
and the ledger with its filters, sort, search and pins. It shipped as you drew it. Since then 0.11.1
made a ledger row open the world it names, and 0.11.2 was balance only.

THE FEEDBACK, IN THE OWNER'S WORDS (Davide, 2026-08-15, having played it)

  "we're still not there with the Galaxy map: the filters and search are useless, and I want a real
   map! I want to be able to visualise my system (and we kinda have that), and also visualise the
   galaxy and the universe. I think this would improve a lot the identity thingy"

Read that as completion, not contradiction, of the identity sheet's own first finding: "Names,
portraits and a ledger all make the map nicer to use. Only spatial structure makes it KNOWABLE. A map
with no spatial structure has addresses, not places." 0.11.0 built the structure — ten named regions
per galaxy, genuinely biasing the stars. What does not exist is a surface where a player can SEE that
structure. The regions live as nine hairline breaks on a strip and an index. Davide is asking for the
place.

WHAT THE TAB IS TODAY — three views, so you know what you are moving

  LEDGER   the landing screen. Your surveyed worlds as rows, with search, filter chips, a sort and
           pins. This is what he calls useless.
  SYSTEM   the orbit page — fifteen slots drawn once left to right, hot to cold, with the reach
           strip (250 ticks, an hour ruler measured from your own star, five named cells) under it.
           This is the "we kinda have that".
  REGIONS  an index of the galaxy's ten regions, reached by tapping the region name in the system
           header. A chooser you pass through, not a level you stay in.

WHAT WE NEED FROM YOU — a drawn map at two new scales, and the relationships between all three

  1. THE GALAXY VIEW. 250 systems in ten named regions. This is the heart of the ask. Questions it
     has to answer: what is a system glyph (star class is real data — DIM/STANDARD/BRIGHT, biased per
     region); how do regions read as places (names on the map? tinted fields? boundaries?); what does
     the player's own knowledge look like on it (surveyed systems, pins, home, worlds with runs in
     flight); what does a tap do (jump the strip? open the system page? peek?); and how does a player
     aim a probe or a run from it, if at all.

  2. THE UNIVERSE VIEW. Four galaxies. Today it is honestly mostly identity — three AI empires are a
     later slice and multiplayer later still. Say what four discs can mean NOW (travel cost between
     galaxies is real and severe — a hop is 9h20m round trip), and what the frame leaves room for
     when empires arrive. If your answer is that the universe view is one gesture up from the galaxy
     view and not a screen of its own, say so.

  3. THE SYSTEM VIEW EXISTS — the orbit page. Davide: "we kinda have that". Treat it as settled
     unless integrating the scales demands a change, in which case name the change and its cost.

  4. THE NAVIGATION MODEL ACROSS SCALES. Zoom? Push/pop? The reach strip and the region index
     shipped at 0.11.0 — do they survive, fold into the map, or go? And the ledger with its filters,
     search and pins: Davide judged the filters and search "useless". Diagnose why before replacing
     them — our reading is that they filter a LIST when the question is a WHERE question, and a list
     cannot answer where. Decide what the map absorbs and what remains a list job (the ledger's
     history and pinned sections may well stay — finding a KNOWN world by name is a list job; finding
     a NEW place to go is a map job).

  5. WHAT THE TAB LANDS ON. 0.11.0 made the ledger the landing screen. The map is the other
     candidate. Pick one and argue it. This one is explicitly yours — Davide has a preference and
     has ruled that it enters as input rather than as an instruction.

HARD CONSTRAINTS FROM THE BUILD

- Compose Canvas, no game engine — settled at kickoff, the orbit page and the 250-tick strip are the
  precedents. Both are already single Canvases rather than hundreds of composables.

- THE GALAXY IS ONE-DIMENSIONAL TODAY, AND THE DRAWING MUST AGREE WITH THAT. A system is an index
  1…250 and has no position. Every travel cost in the game is a function of the difference of two
  indices and nothing else: a probe prices a hop at |Δsystem|, a run at 95 + 5·|Δsystem| units. So a
  layout whose visual neighbours are index-distant would draw a lie — two stars a thumb apart, one
  four hours away and one forty minutes.

  Davide's call, 2026-08-15: THE LAYOUT MOVES, NOT THE METRIC. You may draw any shape whose path
  order IS the index order — a spiral, an arc, a ribbon, a folded serpentine, ten region cells laid
  out in a ring — so that "looks near" and "is near" agree. The alternative (a free 2-D scatter, and
  the build re-derives distance from drawn position) was considered and declined: it re-quotes every
  new dispatch, moves the reach ruler's hour marks and splits the legs of runs already in flight
  differently, and lands back on him as a balance round.

  Two consequences worth designing WITH rather than around. A region is a contiguous run of 25
  indices, so on any index-monotone layout a region is automatically a connected stretch — regions
  come out as places for free. And the hour ruler the strip already carries is a monotone function of
  position along the path, so distance-from-home can be drawn as a field on the map rather than as a
  separate ruler.

- EVERYTHING MUST BE DERIVABLE FROM THE SEED PLUS THE SAVE. Star classes are one hash per system,
  regions are arithmetic, names regenerate. Nothing new may be STORED per system — but note what that
  does and does not forbid: adding a new generated quantity is free and rerolls nobody's existing
  galaxy, so if your layout wants a per-system jitter, a spur or an arm index, the build can generate
  it deterministically. Storing it is what is barred, not inventing it.

- MEASURED DRAW COST, on a desktop JVM, one galaxy, per pass:
    star class for all 250 systems ............  55 µs
    generated name for all 250 systems .......  144 µs
    every world in the galaxy (250 × 15) ......  777 µs
  The phone number is unmeasured and will be larger. Read it as: STARS, NAMES AND REGIONS ARE FREE
  TO REDRAW EVERY FRAME, including under a pinch. "How many worlds does this system hold" is 14×
  the cost of the whole charted tier and is the one thing that would need a cache — so if a system
  glyph wants to carry a world count, say so deliberately and we will cache it; if it carries star
  class, region and name, nothing has to be cached at all.

- KNOWLEDGE TIERS BOUND WHAT ANY SCALE MAY SHOW. Names, star classes and regions are charted from
  first launch. Traits, portraits, deposits and epithets exist only after a survey. A galaxy view
  must not leak an unsurveyed world's nature — the portrait is the survey's reward and that is
  load-bearing. About 98% of the map is unsurveyed on any real save, so the unsurveyed state is the
  DEFAULT case the map must look good in, not the edge case.

- iPhone first: 393dp, and 320dp is baselined and must work. 250 systems on a 393dp canvas is the
  layout problem — say how it reads at phone size, not just at desktop size.

  ONE MEASUREMENT FROM 0.11.0 YOU SHOULD NOT HAVE TO REPEAT: ten region names could not go on the
  strip. A region is 33dp of strip at 393dp and its name is 68–90dp of type at the 9.5sp floor, so
  ten of them overlap two-and-a-half times over before one is legible. Any galaxy view that names all
  ten regions at once has to buy that space from somewhere — a second dimension is the obvious
  somewhere, which is part of why a real map is the right answer.

- A check-in is 5–10 minutes. The map must make finding a place FASTER, not become a place to dwell.
- No timers; anything that moves is computed from a stored instant on foreground.
- Accent colour means "go tap this" and nothing else. A value and its unit are joined by a
  non-breaking space. Never reward tapping, reward deciding.
- The design system is lifted from the repo: a token in your frame maps to a token in the code.

WHAT IT COSTS US, SO YOU CAN SPEND IT DELIBERATELY

Thirty-one committed screenshot baselines cover this tab — 24 of the Galaxy screen at 393dp and
320dp, 7 of the world portrait. Every one your redraw touches is re-recorded by hand. That is not an
argument against changing things; it is the reason to say plainly which of them you expect to die,
so the build does not discover it one baseline at a time.

WHAT WE ARE NOT ASKING FOR

A redraw of the orbit page, the world row, the dispatch sheet, or the ledger rows. Combat, AI
empires, colonisation. Player-drawn annotations (renaming is deferred to colonisation, recorded in
the identity sheet's rejections).

WHAT TO SEND BACK

The shape your sheets take: name the call, give the recommendation, argue what you rejected, and say
what it should FEEL like so the next round can tell whether it worked. Where a premise of ours does
not survive contact with a real screen, say so — every sheet so far has had at least two premises
that did not, and finding them early is most of the value.
```

## Closed, 2026-08-15 — the sheet came back and shipped as 0.12.0

Claude Design answered with **Looks Near Is Near**, archived at
[`../docs/design/looks-near-is-near.dc.html`](../docs/design/looks-near-is-near.dc.html) and recorded
in the repository's own voice at [`../docs/drawn-map-sheet.md`](../docs/drawn-map-sheet.md). The
answer to the metric question was a serpentine fold: ten bands of twenty-five, each band one region,
path order the index order — so the layout moved and the metric did not, exactly as the call asked.

**Three of the prompt's premises did not survive**, which is what the last paragraph asks for and
what the round trip was worth:

1. **"Adding a generated quantity is free" was true, and it was needed three times rather than
   never.** The drift, the size wobble and the halo hue are all seeded now.
2. **The measured draw cost changed the design rather than confirming it.** 55 µs against 777 µs is
   why a system glyph carries class, region and position and deliberately not a world count.
3. **The strip's own retired measurement turned out to be the whole argument for the shape.** Ten
   names not fitting on 393dp *of one dimension* is what the second dimension was bought for.

## What the build must do when the sheet lands

- Write `.claude/docs/drawn-map-sheet.md` — the prose record of every call, in the repo's own voice,
  the way `galaxy-identity-sheet.md` §12 holds the fleet sheet's. Archive the returned `.dc.html`
  under `.claude/docs/design/` and add its row to that folder's README.
- Any layout the sheet settles on is **index-monotone or it goes back to Davide**, per the call
  above. A `PositionAt` generator takes a new `GenerationAxis` tag; adding one is free, reordering
  or reusing one is a save-format change.
- Measure the phone-side draw cost before promising per-frame regeneration. The desktop numbers
  above are the floor, not the answer.
