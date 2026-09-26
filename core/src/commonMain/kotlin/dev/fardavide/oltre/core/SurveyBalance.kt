package dev.fardavide.oltre.core

import kotlin.math.abs
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes

// PLACEHOLDER balance, like `PlaceholderBalance` and unlike `GalaxyBalance` — these numbers come
// from the build against a measured target, not from a decision sheet Davide approved. The shape,
// however, is decided, and the shape is the part worth defending:
//
// **Flat cost, distance only in the duration.** The generator has no per-system gradient — a system
// index enters `GalaxyBalance` in exactly none of its trait functions and reaches
// `GalaxyGeneration` only as a hash salt — so the expected payload of a survey is identical
// galaxy-wide. Against that, a cost that grew with distance would make far probes strictly
// dominated: more money, more time, the same information. It would also tax the player who is away
// longest, which is the one thing the check-in loop must never do. Flat cost inverts both. Near and
// far cost the same and differ only in when they land, so **the question a dispatch asks is "how
// long will I be gone", and the player who is about to be gone nine hours has a better answer than
// the one who is not.** That is a decision the colony screen cannot pose, because a build's
// duration is fixed by its cost curve.
//
// **Metal only, never deuterium.** Deuterium buys the Robotics Factory, Robotics 1 opens the
// research branch and Robotics 4 opens the adaptation ladders — so pricing this verb in deuterium
// would buy a second thing to do by deleting the third. Round 7 measured the fortnight closing on
// 179,352 unspent metal against 5,763 crystal; metal is the resource with nothing to buy, and this
// is the thing to buy with it.
//
// **No Robotics divisor.** Construction divides by (1 + Robotics) and research by (1 + 0.08 x
// Robotics); a probe divides by nothing at all. Its duration is the one in the game that is purely
// the player's own choice of target, and a divisor would let a building quietly shorten the gap
// cover the player deliberately bought.
object SurveyBalance {

    // Flat, in metal, and **measured rather than chosen**. `:sim:run`'s opening report was swept
    // over five price points with one dispatch per check-in aimed at the gap ahead:
    //
    // | metal | building levels at 48h | probes | what it cost |
    // |---|---|---|---|
    // | — (no probes) | 25 | — | the 0.1.1 baseline |
    // | 100 | 24 | 8 | one level |
    // | **150** | **23** | **8** | **two levels, Robotics 1 instead of 2** |
    // | 200 | 22 | 8 | three levels |
    // | 300 | 19 | 8 | six levels |
    // | 500 | 16 | 7 | nine levels, and Research never opens at all |
    //
    // **Every reading the verb exists for is identical from 100 to 300** — eight dispatches, zero
    // check-ins with one kind of decision, 540 minutes booked by the busiest session. So the price
    // buys exactly one thing: how much progression a dispatch costs. 150 is the midpoint of what is
    // defensible, and 100 – 200 are all defensible; above 200 the verb starts eating the branch it
    // is supposed to sit beside, and at 500 it eats Robotics and takes Research with it.
    //
    // Two levels of twenty-five over two days is the tension stated as a number: **the first
    // dispatch costs levels, and it is still worth it.** It also leaves the opening intact — 500
    // starting metal covers a probe *and* the first level of all three mines, so the day-one
    // check-in gains a decision instead of trading one away.
    const val COST_METAL: Long = 150

    fun cost(): Resources = Resources.of(metal = COST_METAL)

    // **What a probe flies, and the constant that answers "way too easy".** Davide's call,
    // 2026-08-16: a survey consumes an idle hull for its flight, exactly as a gathering run does.
    //
    // One `SCOUT`, as a constant rather than as a field on `SurveyJob`. What a probe brings back is
    // *not a quantity* — the verb refuses a second probe to the same star for that reason — so a
    // manifest here would be a dial with one setting, and every reader of a save would have to
    // answer for the day somebody turned it. The day a probe wants two hulls, this is what moves.
    //
    // It rhymes with `COST_METAL` above and does a different job: metal is what the flight costs the
    // colony, and this is what it costs the fleet. **The second is the one that bit** — the price
    // was flat and parallel probes were unlimited, so ten of them landed together and the tenth cost
    // no wall-clock at all. A pool cannot be spent that way.
    val SHIPS: Ships = Ships.of(ShipType.SCOUT, 1)

    // Crossing to a neighbouring galaxy costs as much as crossing a whole one end to end, so the
    // galaxies read as genuinely separate places rather than as one strip of a few thousand
    // systems. Nothing in 0.2 makes the others worth reaching; the constant is here so the duration
    // curve does not have to be redrawn the day something does.
    //
    // **It survived the space going from four galaxies to nine at 0.29.0, and the day ceiling did
    // not.** Eight hops plus a galaxy's width is 38 hours, which broke the build's own rule that no
    // dispatch outlasts a day. Davide's call, 2026-09-25: *"Let's revisit the rules. I'm fine to
    // extend the probe time."* The rule that stands is about the target a player would pick — the
    // next galaxy over, at 8h 49m, which is the range check-in gaps were measured at — and the far
    // corner is simply far. Shrinking the hop to keep the ceiling would have made crossing to a
    // neighbour cheaper than crossing your own galaxy, which is the one thing this constant exists
    // to prevent.
    const val GALAXY_JUMP_UNITS: Int = SYSTEMS_PER_GALAXY_UNITS

    // Approved 2026-09-26: reward upgrading known worlds over opening more easy targets.
    // New probes take twice as long; charting keeps its existing thirty-system radius.
    private const val BASE_MINUTES: Int = 60
    private const val MINUTES_PER_UNIT: Int = 2

    // Visibility is geographical and stays fixed when the survey clock changes.
    const val GRACE_SYSTEMS: Int = 30

    // Distance in systems, with a galaxy hop priced as a long haul. Pure integer arithmetic on
    // bounded inputs, so it is identical on every platform — the same rule the galaxy generator
    // follows and for the same reason.
    fun distanceUnits(from: SystemAddress, to: SystemAddress): Int =
        GALAXY_JUMP_UNITS * abs(from.galaxy - to.galaxy) + abs(from.system - to.system)

    // 1h02m next door, 9h20m one galaxy over at the same system number.
    fun duration(from: SystemAddress, to: SystemAddress): Duration =
        (BASE_MINUTES + MINUTES_PER_UNIT * distanceUnits(from, to)).minutes
}

// Named separately only so `GALAXY_JUMP_UNITS` can reference it in an initializer without the
// forward reference reading as a coincidence.
private const val SYSTEMS_PER_GALAXY_UNITS: Int = GalaxyBalance.SYSTEMS_PER_GALAXY
