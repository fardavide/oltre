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

    // Flat, in metal. Affordable at genesis out of the 500 / 300 starting stock — at the real price
    // of Metal Mine 2 and 3, which is exactly the tension the verb should carry on day one: the
    // first dispatch costs levels, and it is still worth it.
    const val COST_METAL: Long = 500

    fun cost(): Resources = Resources.of(metal = COST_METAL)

    // Crossing to a neighbouring galaxy costs as much as crossing a whole one end to end, so the
    // four galaxies read as genuinely separate places rather than as one strip of a thousand
    // systems. Nothing in 0.2 makes the other three worth reaching; the constant is here so the
    // duration curve does not have to be redrawn the day something does.
    const val GALAXY_JUMP_UNITS: Int = SYSTEMS_PER_GALAXY_UNITS

    // Every probe carries this before distance is counted at all — a dispatch to the system next
    // door is still half an hour of flight. Without it the nearest targets would land inside the
    // check-in that ordered them, which is the failure the whole verb exists to fix.
    private const val BASE_MINUTES: Int = 30
    private const val MINUTES_PER_UNIT: Int = 1

    // Distance in systems, with a galaxy hop priced as a long haul. Pure integer arithmetic on
    // bounded inputs, so it is identical on every platform — the same rule the galaxy generator
    // follows and for the same reason.
    fun distanceUnits(from: SystemAddress, to: SystemAddress): Int =
        GALAXY_JUMP_UNITS * abs(from.galaxy - to.galaxy) + abs(from.system - to.system)

    // 30 minutes next door, ~4h40m to the far edge of your own galaxy, ~8h50m to the far corner of
    // another. That range is chosen against the measurement this verb answers: round 8's opening
    // report found gaps of four to nine hours between check-ins and a busiest session that booked
    // 72 minutes of work. A player who can reach any point in that range has a dispatch that covers
    // the night, and one that does not is the same 72 minutes with a different label.
    fun duration(from: SystemAddress, to: SystemAddress): Duration =
        (BASE_MINUTES + MINUTES_PER_UNIT * distanceUnits(from, to)).minutes
}

// Named separately only so `GALAXY_JUMP_UNITS` can reference it in an initializer without the
// forward reference reading as a coincidence.
private const val SYSTEMS_PER_GALAXY_UNITS: Int = GalaxyBalance.SYSTEMS_PER_GALAXY
