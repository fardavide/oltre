package dev.fardavide.oltre.core

import kotlinx.serialization.Serializable

// **What the player has said about being interrupted.** Not part of `GameState` and deliberately: it
// is a standing preference about a *device* rather than a fact about a colony, `advance` never reads
// it, and folding it into the snapshot would make every save on disk migrate for a field the
// simulation has no use for. It lives in the preferences file beside the galaxy landing —
// `:client:save:data`'s `Preferences` — and the whole record is replaced on every change.
//
// **It is in `core` all the same**, which is the one arrangement where no new edge appears anywhere
// in the module graph: the notification layer, the save layer and the settings screen all already
// depend on `core` and none of them may depend on each other. And it belongs here on the merits —
// `HullAlert` is already an enum in `core` about which of two ways the player wants to be told about
// hulls, and `NotificationCategory` below is a partition of `core`'s own `FutureEvent` hierarchy. A
// copy of that partition in the client is a copy that can drift from the thing it partitions.
//
// What is **not** here: the copy, the order the switches appear in, and what any of this is called on
// screen. Those are `:client:design:text`'s and the frame's.
@Serializable
data class NotificationSettings(
    val scope: NotificationScope,
    val grouping: NotificationGrouping,
    // Which categories are **on**, read only in `BY_CATEGORY`. A set of the ones that speak rather
    // than a map to booleans: a category is either announced or it is not, and the absent-means-off
    // shape is the one `hullAlerts` already uses for the same reason — there is nothing for a reader
    // to remember to ignore.
    val categories: Set<NotificationCategory>,
) {

    companion object {

        // A colony that has never opened the settings screen, and every colony on disk the day this
        // ships. **Nothing about an existing install changes**: `AD_HOC` plus `SINGLE` is exactly
        // what 0.17 does.
        //
        // `categories` is every category although `AD_HOC` never reads it, and that is the point of
        // it — the first switch into `BY_CATEGORY` lands on a working state rather than on silence,
        // which is the difference between a mode and a mode that looks broken.
        val DEFAULT: NotificationSettings = NotificationSettings(
            scope = NotificationScope.AD_HOC,
            grouping = NotificationGrouping.SINGLE,
            categories = NotificationCategory.entries.toSet(),
        )
    }
}

// **The two ways of answering "what am I told about".** Davide's own words for them are *ad-hoc* and
// *custom*; both are words about how the feature works rather than about what the player gets, and he
// handed the naming to Claude Design (2026-08-23). These constants are the model's names and are not
// what a screen says.
@Serializable
enum class NotificationScope {

    // Exactly what ships today, unchanged in every particular: nothing is announced that was not
    // asked for on the row it is about — a watch square, a hull card's bell, the bell beside
    // Dispatch. The default, and where every colony already is.
    AD_HOC,

    // The seven switches. A category that is on announces every job of that kind; one that is off
    // announces none, and the per-row controls are not drawn at all — Davide's call, 2026-08-23.
    //
    // **The per-job asks are ignored rather than deleted.** `subscribed`, `hullAlerts` and
    // `announceFlights` stay exactly as they were, so switching back restores every one of them. A
    // mode switch that emptied the set would be a destructive action behind a two-way control.
    BY_CATEGORY,
}

// **How what you are told is packaged**, and it applies in both scopes — the two settings are
// independent, which is what makes six combinations rather than five.
@Serializable
enum class NotificationGrouping {

    // One alert per piece of news, exactly as today — including today's two collapses: the
    // five-minute completion chain, and the hull card's *when all done* order.
    SINGLE,

    // One alert per category, for everything pending in it, fired at the instant the **last** of
    // them lands.
    GROUPED,

    // One alert for everything, fired at the instant the last pending event of any kind lands.
    SUMMARY,
}

// **One per kind of news the game can deliver**, mapping one-to-one onto the `FutureEvent` members
// `advance` will actually produce. That is the property worth having rather than a tidy list: nothing
// the game can say is ungovernable, and a new kind of news cannot ship without a switch, because
// `category()` below is an exhaustive `when` that will not compile without one.
@Serializable
enum class NotificationCategory {

    FACILITIES,

    // Separate from the ladders although the two rows read alike. They are separate slots and
    // separate events — Davide split the queues at 0.12.2 precisely because they are not the same
    // decision — and a settings screen that re-merged them would undo that in the one place a player
    // looks to say what they care about.
    RESEARCH,

    ADAPTATIONS,

    HULLS,

    PROBES,

    FLEET_RETURNS,

    // The affordability watch, and the one category that governs a control it cannot replace: it has
    // to be told *which row*, so the square survives on a stalled row in both scopes. Off means the
    // square is not drawn at all rather than drawn and inert — see the settings sheet §4.1.
    PRICE_REACHED,
}

// **Whether a job is answered for by its own control.** The one question every screen carrying a
// bell has to ask, and the reason it is a function rather than a comparison at six call sites: what
// "the categories are in charge" means is this file's to say, and a screen comparing against a
// constant would be six places to change the day there is a third scope.
fun NotificationSettings.asksPerJob(): Boolean = scope == NotificationScope.AD_HOC

// **Whether an alert of this kind can be booked at all.** In ad-hoc every kind can, because the ask
// is on the row and there is nothing standing over it; by category, only the ones switched on.
//
// The affordability watch is what this exists for. It is the one ask a category cannot replace — it
// has to be told *which row* — so its square survives in both scopes, and this is what stops it
// surviving into a state where tapping it would book an alert the switch has gated off. See the
// settings sheet §4.1.
fun NotificationSettings.canBook(category: NotificationCategory): Boolean =
    scope == NotificationScope.AD_HOC || category in categories

// Which switch governs a piece of news. Here rather than in the notification layer for the reason
// `FutureEvent.Completion.target()` is here: it is a statement about what `core`'s own events *are*,
// and a client that enumerated them by hand would silently stop covering a ninth.
fun FutureEvent.category(): NotificationCategory = when (this) {
    is FutureEvent.BuildCompletes -> NotificationCategory.FACILITIES
    is FutureEvent.ResearchCompletes -> NotificationCategory.RESEARCH
    is FutureEvent.AdaptationCompletes -> NotificationCategory.ADAPTATIONS
    is FutureEvent.ShipsComplete -> NotificationCategory.HULLS
    is FutureEvent.SurveyLands -> NotificationCategory.PROBES
    is FutureEvent.FleetReturns -> NotificationCategory.FLEET_RETURNS
    is FutureEvent.AffordableAt -> NotificationCategory.PRICE_REACHED
}
