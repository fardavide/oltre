package dev.fardavide.oltre.core

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// The one thing the game is asked to say about something that has *not* happened: a row the colony
// cannot afford already prints when it will be able to, and a watch books an alert for that instant.
//
// **A pointer at a row, never at a purchase**, and that is the whole of why it stays true. Both
// halves of a purchase move while the watch is set — spend the stores on something else and the
// instant slides later, finish a Solar Plant that lifts a throttle and it slides earlier — so a
// watch that had recorded a price or an instant would be stale by the next check-in. Nothing is
// stored except which row, and the instant is projected afresh every time anything asks.
@Serializable
sealed interface WatchTarget {

    // The @SerialName values are on-disk identifiers in every save from schema 9 onwards; renaming
    // a class is free, changing these is a schema break.
    @Serializable
    @SerialName("Facility")
    data class Facility(val building: BuildingType) : WatchTarget

    @Serializable
    @SerialName("Project")
    data class Project(val technology: Technology) : WatchTarget

    // Its own member rather than a widened `Project`, for the reason `AdaptationCompleted` is its
    // own event: the two branches are not the same kind of thing. That was true while they shared a
    // slot and it is what made splitting them at 0.12.1 cost nothing here.
    @Serializable
    @SerialName("Ladder")
    data class Ladder(val technology: AdaptationTechnology) : WatchTarget
}

// The watched row resolved against a colony: which level the next tap would buy, and what it costs.
// Derived on every read rather than stored — see `WatchTarget`.
//
// Three members carrying three subjects and two level types, rather than one carrying an `Int`:
// `BuildingLevel` and `TechLevel` are not the same number, and the alert that reads this is the one
// place the two could be confused without the compiler noticing.
sealed interface WatchedPurchase {

    val cost: Resources

    data class Facility(
        val building: BuildingType,
        val toLevel: BuildingLevel,
        override val cost: Resources,
    ) : WatchedPurchase

    data class Project(
        val technology: Technology,
        val toLevel: TechLevel,
        override val cost: Resources,
    ) : WatchedPurchase

    data class Ladder(
        val technology: AdaptationTechnology,
        val toLevel: TechLevel,
        override val cost: Resources,
    ) : WatchedPurchase
}

// **The square is one control with two meanings, and the row decides which** — so there is one verb
// and the state picks. A row that is building can only be asked about its completion; a row that is
// not can only be asked about its price. Deciding here rather than at the two call sites is not
// tidiness: the screen renders a snapshot and the tap is applied to a state that has been advanced
// since, so a build that finished in between would otherwise be "subscribed" after it had landed.
//
// The two halves count differently. **One affordability watch in the whole game**, shared by the
// facilities, the technologies and the ladders, so pointing it at another row moves it; **any number
// of subscriptions**, because each is a job the player started and the model caps those at eight.
// Either way the undo is the same tap, which is why neither asks for confirmation.
fun toggleAlert(state: GameState, target: WatchTarget): GameState = when {
    state.isRunning(target) -> state.copy(
        subscribed = if (target in state.subscribed) state.subscribed - target else state.subscribed + target,
    )
    else -> state.copy(watching = target.takeIf { it != state.watching })
}

// Which row a completion belongs to, so a caller can ask whether it was subscribed. The map is here
// rather than in the client because it is the same correspondence `isRunning` reads in the other
// direction, and the two must not be able to disagree about what a target points at.
fun FutureEvent.Completion.target(): WatchTarget = when (this) {
    is FutureEvent.BuildCompletes -> WatchTarget.Facility(building)
    is FutureEvent.ResearchCompletes -> WatchTarget.Project(technology)
    is FutureEvent.AdaptationCompletes -> WatchTarget.Ladder(technology)
}

// Whether the thing this target points at is in flight right now. Each branch has a slot and each
// slot holds one job, so a technology is running only when *that* technology holds its branch's.
fun GameState.isRunning(target: WatchTarget): Boolean = when (target) {
    is WatchTarget.Facility -> target.building in builds
    is WatchTarget.Project -> activeResearch?.technology == target.technology
    is WatchTarget.Ladder -> activeAdaptation?.technology == target.technology
}

// Null when nothing is watched. Total for every other case: a locked row and a row already building
// both have a next level and a price, and it is the screen that decides which rows offer a square
// rather than the model — core has no opinion about what a player can see.
fun GameState.watchedPurchase(): WatchedPurchase? = when (val target = watching) {
    null -> null
    is WatchTarget.Facility -> {
        val toLevel = BuildingLevel(buildings.levelOf(target.building).value + 1)
        WatchedPurchase.Facility(
            building = target.building,
            toLevel = toLevel,
            cost = PlaceholderBalance.upgradeCost(target.building, toLevel),
        )
    }
    is WatchTarget.Project -> {
        val toLevel = TechLevel(research.levelOf(target.technology).value + 1)
        WatchedPurchase.Project(
            technology = target.technology,
            toLevel = toLevel,
            cost = ResearchBalance.researchCost(target.technology, toLevel),
        )
    }
    is WatchTarget.Ladder -> {
        val toLevel = TechLevel(research.levelOf(target.technology).value + 1)
        WatchedPurchase.Ladder(
            technology = target.technology,
            toLevel = toLevel,
            cost = AdaptationBalance.adaptationCost(target.technology, toLevel),
        )
    }
}

// Both halves of the square are spent by the thing they were waiting for, and `advance` is where
// that is noticed. Neither is written to the event log, because in neither case did anything
// *happen* — a store crossed a line the watch was only reading, and a job the subscription was
// about has a completion of its own already in the log.
//
// **The watch** is checked at the end of the span rather than at each boundary inside it, which is
// exact for the same reason the alert can only fire early and never late: nothing inside `advance`
// spends, so stores only rise, and a colony that could pay at any instant of the span can still pay
// at its end.
//
// **A subscription** is dropped as soon as its job is no longer in flight, which after a span means
// it completed inside it. That is what makes a subscription about the job the player started rather
// than a standing preference about the row: start the same facility again and the square is unlit,
// because the second build is a second decision.
internal fun GameState.withoutSpentWatch(): GameState {
    val purchase = watchedPurchase()
    val stillWatching = watching?.takeIf { purchase == null || !resources.covers(purchase.cost) }
    val stillSubscribed = subscribed.filterTo(mutableSetOf()) { isRunning(it) }
    return if (stillWatching == watching && stillSubscribed.size == subscribed.size) {
        this
    } else {
        copy(watching = stillWatching, subscribed = stillSubscribed)
    }
}
