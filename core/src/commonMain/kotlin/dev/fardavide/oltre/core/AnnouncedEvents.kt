package dev.fardavide.oltre.core

import kotlin.time.Instant

// **Which kind of news each prediction is**, which is the question the settings sheet asks once
// instead of the colony asking it per row.
//
// A property of the event rather than a `when` in the client, because two clients now read it: the
// scheduler decides what to book, and the sheet decides what to say the next buzz will be. Those two
// must never disagree — a sheet promising an alert the scheduler will not send is the worst thing a
// preferences screen can do — and the only way to guarantee it is one rule.
val FutureEvent.alertCategory: AlertCategory
    get() = when (this) {
        is FutureEvent.BuildCompletes -> AlertCategory.FACILITIES
        is FutureEvent.ResearchCompletes -> AlertCategory.RESEARCH
        is FutureEvent.AdaptationCompletes -> AlertCategory.ADAPTATIONS
        is FutureEvent.ShipsComplete -> AlertCategory.HULLS
        is FutureEvent.SurveyLands -> AlertCategory.PROBES
        is FutureEvent.FleetReturns -> AlertCategory.FLEET_RETURNS
        is FutureEvent.AffordableAt -> AlertCategory.PRICE_REACHED
    }

// The same mapping for a row rather than for a prediction. A watched row can be asked two different
// questions — *tell me when this lands* and *tell me when I can afford it* — and only the first is
// keyed to the row's own kind; the second is `PRICE_REACHED` whatever the row is, which is why this
// answers the first and `AlertSettings.asksOnRow` is where the second is decided.
val WatchTarget.alertCategory: AlertCategory
    get() = when (this) {
        is WatchTarget.Facility -> AlertCategory.FACILITIES
        is WatchTarget.Project -> AlertCategory.RESEARCH
        is WatchTarget.Ladder -> AlertCategory.ADAPTATIONS
    }

// **Everything the player has asked to hear about, earliest first** — `futureEvents` with the gate
// applied and the past dropped.
//
// The gate lived in `:client:notifications:data` from 0.5.0 to 0.17, on the design's own instruction
// and for a good reason that has not changed: `futureEvents` is the mirror of what `advance` will
// write to the log, and a build completes whether or not anybody asked. That reasoning is about
// *`futureEvents`* rather than about `core`, and it survives intact — this is a second list derived
// from the first rather than a filter applied to it, and the debug menu's "skip to the next event"
// still reads the unfiltered one.
//
// What moved it here is that there are two readers now. The scheduler books these; the settings sheet
// says when the next one is due. A sheet that promised an alert the scheduler would not send would be
// a preferences screen lying about the only thing it does, and one rule is the only way to stop it.
//
// **`now` is a parameter and nothing here reads a clock**, like everything else in `core`. Events at
// or before it are dropped: an alert for them would fire in the past, and `advance` is about to apply
// them anyway.
fun announcedEvents(state: GameState, now: Instant): List<FutureEvent> {
    val upcoming = futureEvents(state, now = now).filter { it.at > now }
    return when (state.alerts.mode) {
        AlertMode.PER_ITEM -> upcoming.filter { it.askedOnItsOwnRow(state, upcoming) }
        // One lookup for all seven kinds, which is the whole of what the mode buys. The three
        // per-thing asks are not consulted and not cleared — choosing `PER_ITEM` again finds every
        // square exactly where it was left.
        AlertMode.BY_CATEGORY -> upcoming.filter { it.alertCategory in state.alerts.categories }
    }
}

// The three asks the game shipped between 0.5.0 and 0.15.4, read off whichever of them owns this
// kind of event. Unchanged in behaviour: this is the gate `notificationsFor` used to spell out, moved
// rather than rewritten.
private fun FutureEvent.askedOnItsOwnRow(state: GameState, upcoming: List<FutureEvent>): Boolean =
    when (this) {
        is FutureEvent.Completion -> target() in state.subscribed
        // **A hull card asks two questions where a row asks one**, so this is a lookup rather than a
        // membership test. `WHEN_ALL_DONE` keeps only the last of its type — "your five skiffs are
        // built" is not true until the fifth one is — and the list is already in instant order, so
        // the surrounding sequence is untouched by construction.
        is FutureEvent.ShipsComplete -> when (state.hullAlerts[ship]) {
            null -> false
            HullAlert.EACH_HULL -> true
            HullAlert.WHEN_ALL_DONE -> this == upcoming.last { it is FutureEvent.ShipsComplete && it.ship == ship }
        }
        // Read off the job rather than off `state.announceFlights`, and that is the whole of the
        // per-flight promise: the colony's flag is where the *bell* is, and each flight carries the
        // answer it was sent under.
        is FutureEvent.FleetReturns -> announced
        is FutureEvent.SurveyLands -> announced
        // There is at most one of these and it exists only because a row was pointed at, so the ask
        // is the watch itself. Under `BY_CATEGORY` the switch above can still remove it — which
        // removes the watch rather than muting it, and is the one thing the panel says twice.
        is FutureEvent.AffordableAt -> true
    }
