# Balance log

Every tuning round, newest last: what the numbers were, what playing them felt like, what
changed and why. The point is the **feedback loop** — a later session should be able to read
what was already tried and rejected instead of re-deriving it, and should be able to tell
whether a complaint is new or a repeat.

**Every balance number in the game is a placeholder** until Davide says otherwise. They all live
in `core/.../PlaceholderBalance.kt`; the shapes are asserted in `BalanceCurveTest`. This file is
the history, that file is the truth — if the two disagree, the code wins and this file is stale.

## How to update this

When a round of tuning lands:

1. Add a section at the bottom: date, version, the feedback in Davide's own words, what changed,
   and what is still open. His words verbatim — a paraphrase loses the thing that made it
   feedback.
2. Regenerate the current-curve table below with `./gradlew :sim:run`, which prints it.
3. Say what the change is expected to *feel* like, so the next round can check whether it did.

Do not delete a superseded round. A number that was tried and rejected is the most useful thing
in this file.

## Current curves (0.0.12)

Level-1 output: **90 metal / 30 crystal / 15 deuterium per hour**. Output compounds **+25% per
level**, cost compounds **+50% per level**, both floored to whole units at every step.

| Level | metal/h | crystal/h | deut/h | metal mine cost (m/c) | payback of the next level |
|---|---|---|---|---|---|
| 1 | 90 | 30 | 15 | 60 / 15 | 4h |
| 2 | 112 | 37 | 18 | 90 / 22 | 4h |
| 3 | 140 | 46 | 22 | 135 / 33 | 5h |
| 5 | 218 | 71 | 33 | 303 / 73 | 8h |
| 8 | 425 | 137 | 63 | 1,021 / 244 | 14h |
| 10 | 663 | 213 | 97 | 2,296 / 549 | 20h |
| 12 | 1,035 | 332 | 151 | 5,166 / 1,234 | 30h |
| 15 | 2,020 | 647 | 293 | 17,434 / 4,164 | 51h |
| 18 | 3,945 | 1,262 | 571 | 58,839 / 14,053 | 89h |
| 20 | 6,163 | 1,971 | 891 | 132,387 / 31,618 | 128h |

Daily metal: 2,160 at level 1, 5,232 at level 5, 15,912 at level 10, 48,480 at level 15.

Other levers as of 0.0.12: starting stock 500 metal / 300 crystal (no deuterium); build duration
is base-minutes × level, divided by 1 + robotics level; storage cap a flat 10M per resource;
energy scales all mine output by produced/consumed on a deficit — and that scaling is now on
screen rather than silent (round 3).

> **Still computed by hand, not from `:sim:run`.** The same egress block that stopped round 2
> stopped round 3: `dl.google.com` is denied by this environment's network policy, so Gradle
> cannot resolve AGP and no agent session yet has been able to run the sim or the tests. The
> table above was generated from the curve definitions and cross-checked against a standalone
> reimplementation of `compound()`; re-run `./gradlew :sim:run` on a machine that can build and
> correct anything that differs.

## Round 1 — 0.0.3, the first placeholders (2026-08-05)

Invented to get the economy moving, never played: level-1 output 3,600 / 1,800 / 900 per hour,
output scaling **linearly with level** (`rate × level`), cost **doubling** per level, Nanite
Factory based at 1M/500k/100k, no starting stock.

## Round 2 — 0.0.8, human numbers and parallel builds (2026-08-06)

Davide, after playing it:

> "doubling the production on upgrade is ridicolous"
> "the facilities produce too many resource, and upgrades cost too much: as I said, I want human
> numbers, not having 10k mineral on 1h, when the game already started"
> "I think i prefer to allow parallel upgrades, so the facility item itself will show the upgrade
> progress, instead of having a separate item at top"

What changed:

- Output no longer scales linearly with level (`rate × level` doubled output on the *first*
  upgrade, which is what read as ridiculous). It compounds **+25%** instead: an upgrade is a
  raise, level 10 out-produces level 1 by ~7× rather than 10×.
- Level-1 output cut 60× to 60 / 30 / 15 per hour.
- Cost compounds ×1.5 instead of ×2. Nanite base cut to 20k/10k/4k so it sits just past
  Robotics 10 instead of in a different economy.
- Starting stock of 500 metal / 300 crystal added, because at the new rates a cold start meant
  waiting 1.5 hours before the first upgrade was affordable. Deuterium is never granted.
- Upgrades run in parallel, one job per facility; resources are the only limiter.
- Build durations were **not** touched.

Expected feel, to check next round: the first mine upgrade pays back in ~6 hours and level 11 in
~31, so early check-ins should offer a real choice between several affordable upgrades, and deep
levels should start to feel like commitments. Stocks should stay four-digit for the first days.

Watch for:

- **Durations may now be the wrong shape.** Cost grows ×1.5 per level while duration grows
  linearly, so deep levels are gated almost entirely by resources — a level-20 mine costs 132k
  metal but takes only 3h20m. If deep upgrades feel like waiting for money rather than building
  something, the lever is tying duration to cost, OGame-style.
- **The storage cap (10M) now binds nothing** until very deep levels. Whatever eventually raises
  it is still an open design question.
- **Nothing caps simultaneous projects.** Notion's expansion pressures call for "limited
  simultaneous *projects*"; today only resources limit them.
- Saves from before this round are retired rather than migrated — see `decisions.md`.

## Round 3 — 0.0.12, the shortage nobody could see (2026-08-06)

Davide, with a screenshot of a colony at metal 3 / crystal 2 / deuterium 2 / solar 1:

> "I think I'm struggling way to much with Metal. I'm at the start of the game, and its feels
> too slow to gather some Metal. I dont want to be able to update things no stop, but this seems
> a bit too much, also considering its the more basic meterial, no?"

**The complaint was mostly not about the metal curve.** That colony produced 50 energy and
consumed 90, so `scaleByEnergy` was multiplying every mine by 50/90. The rail read `+51/h` from
a level-3 mine whose real rate was 93/h; crystal read 20 against 37, deuterium 10 against 18.
Nothing in the client mentioned energy — the string "energy" did not appear in a single Kotlin
file under `client/`. The fix he needed was a 16-minute Solar Plant upgrade he was 8 metal short
of, and the game had no way to tell him.

Two changes, and they are independent — keep them apart when judging what the next round felt
like:

1. **The shortage is now visible.** `EnergyBalance` gives the rule a name (produced, consumed,
   `outputPercent`, `surplus`); a power line under the rail shows it always, warn-coloured on a
   deficit; the throttled facilities are badged. No balance number moved for this — the same
   colony produces exactly what it produced before, it just says so.
2. **Metal base output 60 → 90/h.** The early tree (everything but Nanite) costs 808 metal to
   264 crystal, ~3:1, against production of 2:1. Metal was structurally the bottleneck however
   the colony was played. 90/h makes production 3:1 and `BalanceCurveTest` now ties the ratio to
   the cost curves so they cannot drift apart again.

**The cost of change 2, stated plainly: the early loop got faster.** First mine upgrade payback
goes 6h → 4h, level 10 → 11 goes 31h → 20h. That is in tension with "I dont want to be able to
update things no stop". It was accepted knowingly because the shortage fix already returns +82%
on its own — if the game now feels *too* fast, change 2 is the one to walk back, not change 1,
and the lever is `METAL_PRODUCTION_PER_HOUR` alone.

Watch for:

- **Whether the metal raise was needed at all.** The energy fix alone took that colony from
  51/h to 93/h. If a session after this feels loose, try 60/h again with the shortage visible
  before touching anything else — that combination has never actually been played.
- **Durations are still the wrong shape** (carried from round 2, untouched): cost grows ×1.5 per
  level while duration grows linearly, so deep levels are gated by resources, not by building.
- **Nothing still caps simultaneous projects**, and the 10M storage cap still binds nothing.
- **The energy curve itself is untested by play.** Solar is 50/level against mines at 10/10/20
  per level, so a solar plant covers roughly two mine levels; whether that cadence is right is
  now at least observable, via the sim's new "hours throttled" count.
