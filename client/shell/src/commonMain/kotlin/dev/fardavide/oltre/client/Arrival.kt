package dev.fardavide.oltre.client

import dev.fardavide.oltre.client.research.presentation.FinishedWhileAway
import dev.fardavide.oltre.core.AdaptationTechnology
import dev.fardavide.oltre.core.BuildingType
import dev.fardavide.oltre.core.Event
import dev.fardavide.oltre.core.GameState
import dev.fardavide.oltre.core.Resources
import dev.fardavide.oltre.core.Technology

// What opening the app found, which is not a fact about the colony and never enters the save.
//
// The whole premise of the game is that it progresses while it is closed, and until the Sky pass
// the app had no way of saying so: a player who came back to four hours of production met a screen
// of numbers that were simply *different*, with nothing to attribute the difference to. This is the
// difference itself, computed once on the way in and handed to the two screens that can show it.
//
// It is dropped a couple of seconds later — see `App`. That is deliberate rather than tidy: after
// the transitions have played there is nothing left to arrive, and a switch back to a tab must not
// replay an announcement about a launch that has already happened.
internal data class Arrival(
    // The stocks the save was written with, which are the last figures the player actually saw.
    val lastSeen: Resources,
    // The one job that landed while the app was closed, if any did.
    val finished: AwayCompletion?,
)

// A completion is either a facility's, a technology's or a ladder's, and the three land on two
// different screens. Sealed rather than a nullable triple because the shell has to be able to hand
// each one to exactly the screen that draws it, and a `when` is what makes a fourth kind of
// completion impossible to forget about.
internal sealed interface AwayCompletion {
    data class Facility(val building: BuildingType) : AwayCompletion
    data class Project(val technology: Technology) : AwayCompletion
    data class Ladder(val technology: AdaptationTechnology) : AwayCompletion
}

// The events appended between the saved state and the resumed one are exactly what happened while
// the app was closed — that is what an append-only log buys, and it is why this needs no timestamps
// and no comparison of levels. Taking the tail by the saved log's length is safe for the same
// reason: `advance` only ever appends.
//
// The **last** of them rather than all of them, because one band crossing one card is a statement
// and four bands crossing four cards at once is a light show. If several landed, the most recent is
// the one the player is most likely to be looking for.
internal fun arrivalOf(saved: GameState?, resumed: GameState): Arrival? {
    // A first launch founded the colony a moment ago. There is no earlier reading to roll from and
    // nothing has landed, so there is nothing to announce at all.
    if (saved == null) return null
    return Arrival(
        lastSeen = saved.resources,
        finished = resumed.eventLog
            .drop(saved.eventLog.size)
            .mapNotNull { it.toAwayCompletion() }
            .lastOrNull(),
    )
}

// Exhaustive on `Event`, so a ninth kind of event has to decide whether it is something a row can
// announce. Most are not: a build *starting* is something the player did rather than something they
// came back to, a fleet returning already has its own strip at the top of the colony, and a probe
// landing draws a receipt in the map card — which is not a row and has no level to change.
private fun Event.toAwayCompletion(): AwayCompletion? = when (this) {
    is Event.BuildCompleted -> AwayCompletion.Facility(building)
    is Event.ResearchCompleted -> AwayCompletion.Project(technology)
    is Event.AdaptationCompleted -> AwayCompletion.Ladder(technology)
    is Event.BuildStarted,
    is Event.ResearchStarted,
    is Event.AdaptationStarted,
    is Event.FleetReturned,
    is Event.SurveyStarted,
    is Event.SurveyCompleted,
    -> null
}

// The shell knows about all three kinds; the Research screen knows about the two that are its own.
// Translating here rather than widening the feature's type is what keeps `:client:research` from
// having to answer for a facility it does not draw.
internal fun AwayCompletion.toResearchArrival(): FinishedWhileAway? = when (this) {
    is AwayCompletion.Project -> FinishedWhileAway.Project(technology)
    is AwayCompletion.Ladder -> FinishedWhileAway.Ladder(technology)
    is AwayCompletion.Facility -> null
}
