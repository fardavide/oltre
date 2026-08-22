package dev.fardavide.oltre.core

import kotlinx.serialization.Serializable
import kotlin.time.Instant

// A system, without a slot. `GalaxyCoordinate` names a world; a probe is sent to the star, and what
// comes back is every world around it. The two are deliberately different types rather than a
// coordinate with a nullable slot: a survey that could be aimed at slot 7 would raise the question
// of what the other fourteen slots then are, and the answer the design wants is "there is no such
// question".
//
// Bounded on construction like `GalaxyCoordinate`, so an address off the edge of the map cannot be
// built at all and `distanceTo` never has to answer for one.
@Serializable
data class SystemAddress(val galaxy: Int, val system: Int) {
    init {
        require(galaxy in 1..GalaxyBalance.GALAXIES) {
            "galaxy must be between 1 and ${GalaxyBalance.GALAXIES}, was $galaxy"
        }
        require(system in 1..GalaxyBalance.SYSTEMS_PER_GALAXY) {
            "system must be between 1 and ${GalaxyBalance.SYSTEMS_PER_GALAXY}, was $system"
        }
    }

    companion object {
        fun of(at: GalaxyCoordinate): SystemAddress = SystemAddress(galaxy = at.galaxy, system = at.system)
    }
}

// A probe in flight. The same shape as `BuildJob` and `ResearchJob` — subject, start, completion —
// because it is the same kind of thing and a fourth shape would be a fourth thing to remember.
//
// There is no `toLevel` equivalent: what a survey returns is not a number that goes up, it is the
// right to read verdicts that `verdictFor` could always compute. That asymmetry is the point of the
// verb, and it is why the completion branch in `advance` writes to `GalaxyState.surveyed` rather
// than to anything in the colony.
@Serializable
data class SurveyJob(
    val target: SystemAddress,
    val startedAt: Instant,
    val completesAt: Instant,
    // Whether the player asked to be told when it lands — stamped at dispatch, for `FleetRun`'s own
    // reason: a probe has no card to go back to either, so the ask rides on the job.
    //
    // **A probe is asked about even though its target is a subject that outlives it.** `surveys`
    // holds one job per system, so a `SystemAddress` would have been a legal pointer — and it would
    // have been the wrong one, because what the player is asking about is *this flight* rather than
    // that star. `galaxy.surveyed` is monotone, so a system is only ever flown to once, and a
    // pointer at it would be a pointer that can never be reused.
    val announced: Boolean,
)
