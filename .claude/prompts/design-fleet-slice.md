# Claude Design prompt — the fleet slice

Ready to paste into Claude Design. Written 2026-08-10 by the local session, against
`.claude/docs/fleet-sheet.md`. Local→Design is a **round trip**: the local session waits for the sheet
to come back and then builds it.

---

```
Oltre is an asynchronous space-colonisation strategy game in the OGame lineage — Kotlin Multiplatform,
Compose Multiplatform, iPhone is the delivery target. Check-ins are 5–10 minutes; everything
progresses while the app is closed. It is at 0.2.7 and it has three built screens: Colony, Research,
Galaxy. Shipyard and Fleets are empty tabs that say what will be there.

THE PROBLEM, IN THE OWNER'S WORDS (Davide, 2026-08-10)

  "We did many early game balancing, but the game still feels like empty, it seems a boring idle,
   instead of a space strategy game. I think we should allow users to manage some basic fleets, and
   explore neighbours planets."

  "In a space game I assume it should be the baseline to scout a near planet and then gather some
   resources from it, while as of now this arrives like a week into the game. We really need to bring
   some 'space feel' into the game, as of now, in the early game, it is just any poorly made idle
   game."

Measured, so you know the shape of the hole: the colony has nothing in flight for 95.83% of the first
48 hours, the longest unbroken silence is 8h 52m, and the median check-in books 9 minutes of work.
A probe verb already exists and already took "nothing at all in flight" to 2.08% — and he played that
build and still called it an empty idle. So occupancy is NOT the complaint. The complaint is that
there is no decision with a map in it.

WHAT IS BEING BUILT — read `.claude/docs/fleet-sheet.md` in full; this is the summary

A "run": you commit ships to a world you have already surveyed, choose whether they bring back metal
or crystal, and choose how long until they are home from a ladder of windows (1h / 3h / 6h / 12h /
24h). They fly out, work the surface for whatever is left of the window after the round trip, and come
home with cargo. Deterministic — the exact haul is known and stated before you commit. Never
deuterium. Nothing is rolled, nothing is hidden, every run comes home.

The load-bearing idea, and the sentence the screens have to sell:

  YOU CANNOT LIVE THERE, BUT YOU CAN SEND A SHIP.

Hostility (temperature / gravity / pressure against your tolerance bands) gates SETTLING. It does not
gate gathering. That matters because richness is DERIVED from hostility in this game — heavy worlds
are metal-rich, thick-atmosphere worlds are crystal-rich, cold worlds are deuterium-rich — so the
richest worlds are by construction the ones you cannot stand on. 98% of the map currently reads
BLOCKED and does nothing. This slice turns it into the place you work.

WHAT WE NEED FROM YOU — three surfaces

1. THE WORLD ROW / WORLD CARD, on the existing Galaxy screen.
   This is the hardest problem in the slice and the reason we are asking.
   A world must now carry TWO READINGS AT ONCE that point in opposite directions:
     - the settler's reading, which exists today: "BLOCKED · gravity 2.4 g, you tolerate 1.45 g ·
       Gravitic Adaptation 3 would land it" (the technology name is an accent-coloured tap target
       that deep-links to Research)
     - the fleet's reading, which is new: "metal 1.42 · danger 1 · a 6h run brings 227 metal"
   The tension is the point — you cannot live there, you can still take from it — but a card that
   says BLOCKED and OPPORTUNITY in the same breath can very easily read as the app contradicting
   itself. Resolving that is the ask.
   Note the existing constraint: on a three-axis blocked world this row already wraps at 393dp.

2. THE DISPATCH SHEET — raised from a world row. What must appear BEFORE the tap:
     - which world, and its richness in the resource being gathered
     - metal or crystal (a two-way choice; deuterium is never offered)
     - the window ladder, with windows shorter than the round trip NOT OFFERED at all
     - danger, 0–5, and what it costs (10% of the hold per point) — stated, never hidden
     - the exact cargo figure, to the unit, for the current selection
     - how many ships, out of an idle pool
   States it must cover: no window available (the target is too far for any rung), nothing surveyed
   yet, no idle ships, and cannot afford.

3. THE FLEETS TAB and THE SHIPYARD TAB — both are currently empty states.
   Fleets: one card per run in flight — where it went, what it is carrying, phase (outbound /
   on station / inbound) and a countdown. Plus a returns ledger.
   Shipyard: hulls for sale on a compounding price curve, the idle pool, and what is away.
   The Colony screen already has a returning-fleet strip, shipped at 0.0.6 and never yet fed by
   anything real. Say whether it stays, and what it says when several runs are in flight.

THE ONE OPEN CALL THAT MAY CHANGE WHAT YOU DRAW

§3.5 of the sheet. Metal and crystal richness have NO positional term — they are uniform draws, and
the generator sees a system index only as a hash salt. So at equal richness the nearest world always
wins, distance is pure cost, and the player finds a good neighbour and parks there forever. The
recommended fix is a fleet-side frontier bonus (the hold scales with distance band, ×1.0 / ×1.15 /
×1.35 / ×1.6), which would make the dispatch a genuine three-way trade: near-safe-small against
far-dangerous-big, played against how long you are going to be away. Davide has not ruled on it.
If it lands, the dispatch sheet gains a frontier line and the map gains a reason to be looked at.
Design for both, or tell us which one makes the better screen — that argument is useful to us.

CONSTRAINTS THAT ARE NOT NEGOTIABLE

- iPhone first: 393dp, and 320dp is also baselined. Six facility rows at the MEASURED 106dp already
  fill a 393×852 phone almost exactly, so a seventh row on Colony is expensive — budget from 106dp,
  not from the 74dp an earlier sheet assumed.
- A check-in is 5–10 minutes. If a screen makes it longer, the screen is wrong.
- No timers ever run: everything is computed from a last-updated instant on foreground. A countdown
  is a rendering of a stored completion instant.
- Accent colour means "go tap this" and nothing else. An accent string that is not a target is a
  worse violation than a demoted one — this was settled at 0.0.18.
- A value and its unit are joined by a non-breaking space.
- Never reward tapping, reward deciding (locked on Notion). Dispatch is manual and there is
  deliberately NO auto-repeat and no "relaunch with last settings" — that is the OGame bot shipped
  by the developer. A re-dispatch should be about two taps, and no fewer.
- Continuous extraction from a world is COLONISATION, which is a much later slice. Nothing here may
  drift toward a permanent income stream from a world you do not hold.

WHAT WE ARE NOT ASKING FOR

Combat (no model yet, slice #8), colonisation (slice #10), ship design with hulls and modules
(locked on Notion as the eventual depth mechanic, not now), and permanent hull loss (deferred with
combat — danger currently takes cargo, not ships).

WHAT TO SEND BACK

The shape the last two design sheets took, which worked: name the call, give the recommendation,
argue what you rejected and why, and say what it should FEEL like so the next round can tell whether
it worked. Where a premise of ours does not survive contact with a real screen, say so — the last two
sheets each had two premises that did not, and finding them early is most of the value.
```
