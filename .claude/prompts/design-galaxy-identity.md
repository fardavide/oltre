# Claude Design prompt — the galaxy identity slice

Ready to paste into Claude Design. Written 2026-08-14 by the local session, against
`.claude/docs/galaxy-identity-sheet.md`. Local→Design is a **round trip**: the local session waits for
the sheet to come back and then builds it.

---

```
Oltre is an asynchronous space-colonisation strategy game in the OGame lineage — Kotlin Multiplatform,
Compose Multiplatform, iPhone is the delivery target. Check-ins are 5–10 minutes; everything progresses
while the app is closed. It is at 0.10.1. Colony, Research, Shipyard, Galaxy and Fleets are all built.
You designed the fleet slice at 0.4 (world row treatments 1a/1b/1c, the dispatch sheet, Fleets,
Shipyard) and this is the same Galaxy screen, one year of slices later.

THE PROBLEM, IN THE OWNER'S WORDS (Davide, 2026-08-14)

  "I'm so unhappy with the map. It is huge, but terrible to navigate! Finding a planet feels like
   searching a phone number on pagine gialle in the 90s."

  "I feel like the map should gain 'an identity'. Eg when I think about going to Siracusa, or
   specifically to Fontane Bianche, what comes to my mind is a map, some 'images' of the places; they
   have an identity. While here it's just numbers: like '2 pages before'. The user should feel excited
   about exploring new places and build a knowledge of the world."

WHAT WE FOUND, MEASURED — so you know the shape of the hole

1. The galaxy is statistically uniform BY CONSTRUCTION. Star class is the only system-level trait and
   it is drawn independently per system, so any two neighbourhoods of 250 systems are identical in
   distribution. There is nothing about any region of the map that CAN be learned. Not hidden by the
   UI — absent from the model.
2. You navigate in systems (a page of 15 slots) and you care about worlds. That is 1,000 pages, ~98%
   of which read UNSURVEYED. The reach band you have seen fixed the tap count and is a better index;
   it did not stop the only way of finding a world being to page a directory.
3. The player's own knowledge has NOWHERE TO LIVE. The set of surveyed worlds is read by exactly one
   function in the whole codebase — "would a probe learn anything here". No screen shows you what you
   know. Survey forty worlds over a fortnight and they are scattered across a thousand pages with no
   aggregate, no filter, no pin, and no way back except remembering "3:177:6".

WHAT IS BEING BUILT — read `.claude/docs/galaxy-identity-sheet.md` in full; this is the summary

The first three bullets are ALREADY BUILT AND GREEN in core — regions, names and epithets exist, are
tested, and produce the real strings quoted below. The last three are what your frames decide.

- REGIONS. Each galaxy's 250 systems are cut into 10 contiguous regions of 25. Each region has a
  generated name and a real star-class bias — a "Deep" runs 60% dim stars, a "Burning" 60% bright.
  Because a dim star is −40 °C and a bright one +40 °C against a fall of 28 °C per orbit, the
  HABITABLE ORBITS MOVE: in a Deep you settle close in and the deuterium is good, in a Burning you
  settle far out and it is poor. That is a fact a player can act on before surveying anything, because
  star class is free from the first launch. Every galaxy holds exactly 4 Deep, 2 Settled, 4 Burning.
  ALL OF THIS IS BUILT AND MEASURED — it is not a proposal you are designing against in the abstract.
- NAMES, generated, never stored, never renameable by the player (deferred to colonisation). Systems
  in a region rhyme in character. Real output, from the shipped generator, galaxy 1 of the test seed:
    a Deep   — Lumiaren · Almianova · Nerimira · Calelis · Velovae · Miraren
    a Reach  — Elyuvell · Bramura · Sorodun · Ostimar · Ardetis · Venavell
    a Blaze  — Kragith · Vokik · Teshux · Karodra · Torezon · Daxath
    regions  — "Almiaren Deep", "Bramuvell Reach", "Vokith Blaze"
  The world in slot 7 of Calianova is "Calianova VII" — the numeral is the SLOT, not the world's rank
  among its neighbours, so the map's existing slot labels and the name never disagree.
  Names are unique within a galaxy, so search by name returns exactly one place.
  The coordinate does not disappear: it is the address, and it becomes the subtitle.
- A PORTRAIT per world. A procedurally drawn disc where every channel is a trait the game already
  generates: hue from temperature, banding from pressure, size from gravity, plus a terminator for
  TIDALLY_LOCKED, a storm swirl for ION_STORMS, fracture lines for SEISMIC_INSTABILITY / THIN_CRUST, a
  halo for RADIATION_BELT, and a rare ring that means nothing at all.
- AN EPITHET per world — a derived two-word phrase, never rolled, so it cannot lie. BUILT; the noun
  comes from the axis a world is most extreme on and the adjective from the second, both measured
  against the level-0 tolerance bands. Real output, and the spread across a galaxy is wide — no single
  epithet is more than 8% of worlds:
    hollow shroud · frozen giant · iron frost · airless furnace · scorched shroud · brittle husk
    drowned shroud · deep frost · ashen furnace · bare waste · iron giant · temperate world
  "temperate world" is the ~1.5% that sit inside every band — the only worlds a settler can take, and
  the one epithet a player should be glad to read.
  The words are Davide's to overrule; the derivation is not.
- A LEDGER of everything you have surveyed, with filters, sort, pins and search by name.
- HISTORY per world: when you found it, how many runs you have sent, how much you have taken out.

WHAT WE NEED FROM YOU — four surfaces, and the first is the hard one

1. THE WORLD ROW. This is the ask, again, and for the same reason as last time: it is the tightest
   surface in the app and it is being asked to carry more.
   Today a row carries: the coordinate, the verdict (one of six), and — since 0.9 — a pair of deposit
   fractions ("84/163 metal · empty crystal"). A three-axis BLOCKED row ALREADY WRAPS at 393dp.
   It must now also carry a NAME, an EPITHET and a PORTRAIT, and the name has to be the headline
   because that is the entire point of the change.
   Both of the new strings are Davide's explicit call — he asked for the epithet on the row, not only
   on the sheet, because "the list you scan is the thing that reads as faceless".
   Something has to give and we would rather you chose it than us. Candidates, none preferred:
   demote the coordinate to a trailing monospace tag; let the epithet REPLACE the verdict clause on
   the rows where treatment 1b already decided the verdict is not the offer (Blocked and Barren);
   two-line rows with the portrait as the leading element; a wider row on iPad only.
   NOTE: the portrait and the epithet MUST NOT appear on an unsurveyed world — they are trait
   readouts, and drawing one would perform a survey nobody paid for. ~98% of rows are unsurveyed, so
   whatever you draw has to look deliberate when the whole list is blank discs.

2. THE LEDGER — and its placement is your call, which is why we are asking rather than telling.
   Three options, and the argument for each is in §4 of the sheet:
     (a) a mode of the Galaxy tab — a header toggle swaps map for list
     (b) a full-screen sheet raised over the map and dismissed back to it
     (c) the ledger becomes the Galaxy tab's DEFAULT view and the map is the mode you switch to
   (c) is the strongest answer to the phone-book complaint and the biggest change to what the tab is.
   We have no preference and we would like the argument as much as the frame.
   What it holds: every surveyed world; filters (reachable within N hours, verdict, "one adaptation
   level away", still holding stock, region); sort (distance, yield, stock, when you found it); pins,
   which also mark the reach strip; search by name.
   States to cover: at genesis it holds ONE SYSTEM — the home system, four or five rows — and that
   emptiness is honest and is meant to be an invitation rather than an error. Also: every filter
   excluding everything.
   The tab bar is five and fixed. A sixth tab is not on the table.

3. THE PORTRAIT's visual language, and the DISCOVERY CARD.
   We need the disc to be a READING, not decoration — a player should come to recognise a heavy cold
   world before reading a figure. Give us the palette against temperature, how pressure reads as
   banding, how the four hazard marks compose when a world has two of them, and what the UNSURVEYED
   blank looks like.
   Sizes: it appears at row scale (small) and on the dispatch sheet (large). Say what the small one
   drops.
   The discovery card is shown once ever, the first time a world is surveyed: name, portrait, epithet,
   the three axis readings, dated. It is the moment the sheet is betting the "excited about exploring"
   feeling on. It must not block a check-in — a player who surveys three worlds in one foreground must
   not be made to dismiss three cards.

4. THE REACH STRIP AND THE REGION AS A NAVIGATION LEVEL.
   The strip you have seen is 250 ticks wide, one per system, height and alpha by star class, with
   your star and your probe the only coloured marks, and hour marks under it.
   Regions now sit between the galaxy and the system. Do their names belong ON the strip — ten labels
   is a lot of ink at that width — or in the header, or somewhere else entirely? And does the region
   deserve its own view (the ten regions of a galaxy as a chooser) or is it purely a label that makes
   the strip legible?
   Note what the strip gains for free: a region's star bias is VISIBLE on it already, because tick
   height and alpha are star class. A Deep region should read as a visibly darker, shorter run of
   ticks without a word of copy. That may be the whole answer.

CONSTRAINTS THAT ARE NOT NEGOTIABLE

- iPhone first: 393dp, and 320dp is also baselined (a 320dp Slide Over pane drops a trailing NOUN,
  never a number or a name).
- A check-in is 5–10 minutes. If a screen makes it longer, the screen is wrong.
- No timers ever run: everything is computed from a last-updated instant on foreground.
- Accent colour means "go tap this" and nothing else — settled at 0.0.18. An accent string that is
  not a target is a worse violation than a demoted one.
- A value and its unit are joined by a non-breaking space.
- Nothing may leak a trait of an unsurveyed world. The astronomy line under the system header is the
  one thing that is free — distance, danger band, round trip — because it is identical for all fifteen
  slots and is known from the first launch.
- The galaxy is never serialised: 4,700 worlds are regenerated from one seed on demand. So a name, an
  epithet and a portrait cost the save nothing — but anything you invent that is NOT derivable from a
  world's traits would have to be stored, and that is a real cost. Say if you want one anyway.
- The map is a Compose Canvas. No game engine, ever (settled).

WHAT WE ARE NOT ASKING FOR

Player-authored names (Davide deferred them to colonisation — noted in the sheet as the strongest
identity lever we are choosing not to pull yet). Colonisation itself (slice #10). Combat (slice #8).
Any change to the tolerance bands, the richness formulas, the verdicts or the target distribution —
this slice moves no balance number.

WHAT TO SEND BACK

The shape the last sheets took, which worked: name the call, give the recommendation, argue what you
rejected and why, and say what it should FEEL like so the next round can tell whether it worked. Where
a premise of ours does not survive contact with a real screen, say so — the fleet sheet had four
premises that did not, and finding them early was most of the value.
```
