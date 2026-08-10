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
    // own event: the two branches share one slot and are still not the same kind of thing.
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

// **One watch in the whole game**, shared by the facilities, the technologies and the ladders — so
// there is no verb for adding a second. Tapping another row's square moves it; tapping the watched
// row's takes it back. That is also why taking it back asks nothing: the undo is the same tap.
fun toggleWatch(state: GameState, target: WatchTarget): GameState =
    state.copy(watching = target.takeIf { it != state.watching })

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

// A watch is spent the instant the colony can pay for what it points at: it exists to name one
// moment, and past that moment it has nothing left to say. Applied by `advance` rather than written
// to the event log, because nothing *happened* — a store crossed a line the watch was only reading,
// and a log entry for it would be the one entry a player could not have caused.
//
// Checked at the end of the span rather than at each boundary inside it, which is exact for the
// same reason the alert can only fire early and never late: nothing inside `advance` spends, so
// stores only rise, and a colony that could pay at any instant of the span can still pay at its end.
internal fun GameState.withoutSpentWatch(): GameState {
    val purchase = watchedPurchase() ?: return this
    return if (resources.covers(purchase.cost)) copy(watching = null) else this
}
