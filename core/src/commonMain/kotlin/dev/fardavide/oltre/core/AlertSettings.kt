package dev.fardavide.oltre.core

import kotlinx.serialization.Serializable

// **Where the question is asked**, which is the one thing the game's three asks never said.
//
// `subscribed`, `hullAlerts` and `announceFlights` each point at something and each answers *tell me
// when this lands* for that one thing. This says whether that question belongs on the job at all, or
// one level up on the kind of job — and a colony that answers *the kind* stops carrying a square on
// any row, because the row has nothing left to ask.
//
// The design is *Ask Once*, accepted 2026-08-23.
@Serializable
enum class AlertMode {

    // The question is on the row. Every alert is asked for individually, which is what `subscribed`,
    // `hullAlerts` and `announceFlights` have meant since 0.5.0.
    PER_ITEM,

    // The question is on the kind. `categories` decides, the three per-thing asks are not consulted,
    // and the rows stop drawing the square — with one exception, `PRICE_REACHED`, which is about a row
    // the player has to name rather than a kind of thing that happens.
    BY_CATEGORY,
}

// **The seven kinds of news this game has**, and the order is the sheet's: the panel draws them by
// iterating this, so the declaration order is a design decision rather than a detail.
//
// Plural where the category is a stream of things. `PRICE_REACHED` is last because it is the odd one
// in three separate ways — it is the only one whose value expires, the only one that still needs a row
// named under `BY_CATEGORY`, and the only one whose switch turns a watch *off* rather than muting it.
@Serializable
enum class AlertCategory {
    FACILITIES,
    RESEARCH,
    ADAPTATIONS,
    HULLS,
    PROBES,
    FLEET_RETURNS,
    PRICE_REACHED,
}

// **How many notifications the answers arrive in**, which is a different question from which answers
// there are — a colony can ask about one row and still want the buzz held with everything else.
@Serializable
enum class AlertDelivery {

    // One per thing, which is what the game has always done — including the five-minute chain that
    // folds anything landing in the same breath into one sentence. Davide's call, 2026-08-23: the
    // chain is a dedupe rather than a delivery mode, so it lives here rather than being one of the
    // three stops.
    EACH,

    // One per kind, at the instant the last thing of that kind lands. `PRICE_REACHED` is exempt: it
    // announces a window that closes, so holding it back is worse than not sending it.
    PER_CATEGORY,

    // **One notification, brought up to date rather than joined by a second.** Davide, 2026-08-23:
    // *"Metal Mine upgraded at 12:04, and notification shows only that, then one ship is ready at
    // 12:37, and the notification updates to show Metal Mine + 1 ship."*
    //
    // So it is not one alert held until the last thing lands — the design's §6 measured that as five
    // and a half hours of silence on the reference colony. It fires at every instant, and every firing
    // replaces the one before it, so the tray never holds more than one. `PRICE_REACHED` folds into it
    // like everything else, which is Davide's overrule of the design's *never grouped*: there is
    // nothing to be late for when the notification is already on the lock screen.
    TOTAL,
}

// The two settings and the seven switches, together because the sheet is one surface and a colony
// cannot hold half of it.
//
// `categories` is a set rather than a map to `Boolean` for the reason `subscribed` is: *present means
// on* has one representation, and a map has two ways to say off.
@Serializable
data class AlertSettings(
    val mode: AlertMode,
    val categories: Set<AlertCategory>,
    val delivery: AlertDelivery,
) {

    companion object {

        // **What a colony opened after 0.18 starts on**, and it is a change of default rather than a
        // new option. Davide's call, 2026-08-23: everything announces itself, and it arrives as one
        // notification that keeps being updated. The two halves pay for each other — seven categories
        // on is the loudest this app can be under any other delivery, and under `TOTAL` it is one
        // entry in the tray.
        val NEW_COLONY: AlertSettings = AlertSettings(
            mode = AlertMode.BY_CATEGORY,
            categories = AlertCategory.entries.toSet(),
            delivery = AlertDelivery.TOTAL,
        )

        // **What a colony played before 0.18 lands on**, and the difference from `NEW_COLONY` is the
        // whole of Davide's call: *"use single notification only for new saves, previous ones keep the
        // current behavior"* (2026-08-23).
        //
        // Every earlier behavioural hop made a colony *quieter*, and each was defensible because the
        // thing being silenced had never been asked for. This one would go the other way — seven
        // categories nobody chose — so it does not happen to a save that already exists. The pair here
        // is exactly what 0.17 did: the question on the row, and the five-minute chain.
        //
        // The seven switches still arrive on. They are inert under `PER_ITEM` and the sheet draws no
        // panel at all, so this is not a promise about what the colony hears; it is what the player
        // finds the first time they choose the other mode, and seven off would look broken.
        val CARRIED_FORWARD: AlertSettings = AlertSettings(
            mode = AlertMode.PER_ITEM,
            categories = AlertCategory.entries.toSet(),
            delivery = AlertDelivery.EACH,
        )
    }
}

// **Whether a row still carries the square**, which is the sheet's call 1 and call 2 in one function.
//
// Under `PER_ITEM` every row does, which is what the app has done since 0.15.4. Under `BY_CATEGORY`
// almost none do: the question has been answered one level up, so a square beside a running job would
// be a control with nothing left to decide — and this app does not ship a control that does nothing.
//
// **`PRICE_REACHED` is the exception and it survives for a reason the other six do not have.** Every
// other category is a *kind of thing that happens* and the game knows which things those are; a price
// watch is a row the player had to point at, and *tell me when I can afford this one* has to be told
// which one. So the switch decides whether the watch exists at all, and where it does the square is
// still the only way to book it. The panel says so on the row that governs it.
//
// A `core` rule rather than four presentation modules agreeing, for `announcedEvents`' reason: the
// square is the control that books the alert, and a screen that drew one the scheduler would ignore
// would be the dead control this repository's rules are most emphatic about.
fun AlertSettings.asksOnRow(category: AlertCategory): Boolean = when (mode) {
    AlertMode.PER_ITEM -> true
    AlertMode.BY_CATEGORY -> category == AlertCategory.PRICE_REACHED && category in categories
}

// The two-chip ladder at the top of the sheet. A setter rather than a toggle, unlike the three asks
// below it: a ladder shows both stops at once, so a tap means *this one* rather than *the other one*,
// and a toggle would make a second tap on the lit chip do something.
fun setAlertMode(state: GameState, mode: AlertMode): GameState =
    state.copy(alerts = state.alerts.copy(mode = mode))

// One of the seven bells. A toggle, because a switch has one control and two positions — which is the
// square's own shape on every row in the colony, one level up.
fun toggleAlertCategory(state: GameState, category: AlertCategory): GameState = state.copy(
    alerts = state.alerts.copy(
        categories = if (category in state.alerts.categories) {
            state.alerts.categories - category
        } else {
            state.alerts.categories + category
        },
    ),
)

// The three-chip ladder. A setter, for the reason `setAlertMode` is one.
fun setAlertDelivery(state: GameState, delivery: AlertDelivery): GameState =
    state.copy(alerts = state.alerts.copy(delivery = delivery))
