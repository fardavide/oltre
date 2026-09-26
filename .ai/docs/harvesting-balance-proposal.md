# Adaptation harvesting: verification and proposed balance

2026-09-26. Davide approved the numerical proposal with "lets try". The gating correction and
the tuning below are implemented on the branch based on 0.29.1. Measured results and remaining
play questions are recorded in balance-log.md, round 36.

Final verification after tuning, 2026-09-26: `./gradlew build --quiet`,
`./gradlew verifyRoborazziDesktop --quiet` and `./gradlew :sim:run --quiet` all passed.
All 40 numerical-tuning screenshot differences (37 Galaxy, three Fleets) were visually reviewed
before recording. Larger amounts fit the reviewed narrow and Italian frames. Twelve core gating/save
regressions and the balance tests cover the adaptation axes, exact thresholds, legacy flights and
deposits, rewards, probe clocks, refill and large-fleet arithmetic. Additional arithmetic tests exercise
Long-limit quotients and exact fractional rounding. The simulation results remain those in round 36.

Before numerical tuning, verification on the branch based on `d7a93c2a` (main, 0.29.1): `./gradlew build --quiet` and
`./gradlew verifyRoborazziDesktop --quiet` both passed. Ten focused core regressions cover
missing axes, exact thresholds, zero-adaptation planets, refused-state preservation and legacy
saved runs. Client and HTTP tests cover enforcement at both dispatch entry points and the server.
The galaxy's 25 changed and two new screenshot baselines were visually reviewed before recording;
resource figures and controls were unchanged, while verdicts and caption layout changed as intended.

## Confirmed rule and release history

Davide's intended rule: every thermal, gravitic and atmospheric requirement must be met before
harvesting a planet. A zero-requirement planet needs no adaptation research.

The 0.28 galaxy displayed blocked verdicts, but its row still opened the dispatch sheet and
`core.startRun` accepted the run. The same core exception remained in 0.29; the 0.29 map rewrite
also offered a run for a blocked verdict. This was not a new core regression introduced by 0.29.
The old implementation dated to 0.3.0 (`54d1c3b4`, "You cannot live there, but you can send a ship").

The correction enforces the rule in core, the galaxy caption and the shared dispatch sheet.
Previously dispatched runs retain their cargo and return normally after save/load. Server
requests and the Fleets tab cannot bypass the rule.

## Why the existing rewards are weak

Deposit caps use richness and danger, with a base of 5,800 priced units. Adaptation requirements
do not enter either the deposit cap or the harvesting rate. Research cost grows 50% per level,
while difficult targets can offer only a small improvement in resources.

Increasing only the cap would let a planet last longer without necessarily improving the haul
from a trip. The proposal increases both cap and extraction at that planet, using the same
multiplier so fleet-size and working-time calculations remain consistent.

## Approved first round

For each world, let T, G and A be the absolute minimum thermal, gravitic and atmospheric levels
required to tolerate it. Multiply its current deposit cap and harvesting rate by:

`1 + (T*T + G*G + A*A) / 12`

The calculation uses the world's requirements, never the player's current or missing research.
Over-researching does not inflate an easy planet, and unlocking a planet does not shrink its reward.
Richness and danger continue to differentiate planets.

| Required level on one axis; other axes zero | Reward multiplier |
|---|---:|
| 0 | 1x |
| 3 | 1.75x |
| 6 | 4x |
| 9 | 7.75x |
| 12 | 13x |

A current 9,000-unit deposit requiring Gravitic 12 alone would become about 117,000 units;
one requiring no adaptation would retain its current value. Additional required axes add a
further reward. These are arithmetic examples, not results of a strategy simulation.

Double probe duration from `30 minutes + 1 minute per distance unit` to
`60 minutes + 2 minutes per distance unit`:

| Target | Current | Proposed |
|---|---:|---:|
| Next system | 31m | 1h 02m |
| 30 systems away | 1h | 2h |
| Same system number in the next galaxy | 4h 40m | 9h 20m |

Keep the existing 30-system charted radius. It currently derives from a one-hour probe and
would collapse to zero if the new duration were substituted without separating those concepts.
Distance rings must continue to show actual flight times. Probe cost and hull requirement stay
unchanged.

## Validation for the approved round

- Pin zero-requirement, single-axis and multi-axis rewards, and prove the player's research does
  not alter an existing world's cap.
- Check the cap/rate/working-time agreement, refill, checked arithmetic and existing saved deposits.
- Show numeric resource quantities for locked planets so the player can compare the reward before
  buying adaptation; a "full" reading alone cannot distinguish a 4,000-unit world from a 117,000-unit one.
- Check probe durations, charted radius, distance rings and in-flight probes saved before tuning.
- Update simulation target selection to choose planets the bot can legally harvest, then compare
  adaptation investment against repeated easy-world farming. An old bot that targets blocked
  planets and silently receives refusals is not a valid balance measurement.
- Re-run full build, screenshot verification and the simulation; record accepted tuning and measured
  outcomes in the balance log.

Opening progression needs explicit measurement: genesis guarantees a neighbour needing at most
one adaptation level, not a neighbour needing zero. Enforcing the gate therefore changes the
opening fleet opportunities on some seeds. Do not silently change generation to hide that effect.
