package dev.fardavide.oltre.client.notifications.data

import dev.fardavide.oltre.client.design.text.Strings
import dev.fardavide.oltre.client.design.text.TextRes
import dev.fardavide.oltre.client.design.text.Translations
import dev.fardavide.oltre.core.AdaptationTechnology
import dev.fardavide.oltre.core.AlertCategory
import dev.fardavide.oltre.core.AlertDelivery
import dev.fardavide.oltre.core.AlertMode
import dev.fardavide.oltre.core.BuildingType
import dev.fardavide.oltre.core.GalaxyCoordinate
import dev.fardavide.oltre.core.FutureEvent
import dev.fardavide.oltre.core.GameState
import dev.fardavide.oltre.core.HullAlert
import dev.fardavide.oltre.core.ShipType
import dev.fardavide.oltre.core.SystemAddress
import dev.fardavide.oltre.core.Technology
import dev.fardavide.oltre.core.WatchedPurchase
import dev.fardavide.oltre.core.alertCategory
import dev.fardavide.oltre.core.announcedEvents
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

// The check-in loop, and on iPhone the only one there can be: iOS runs nothing in the
// background, so the game's single way of saying "something happened" is an alert booked in
// advance at an instant the simulation already knows.
//
// Kept honest by deriving the whole set from state on every discrete transition — the same rule
// that decides when to write the save. Nothing is ever amended, so a build that completed, a
// fleet that landed, or a colony reloaded from a different save can never leave a stale alert
// behind to fire about something that is no longer true.
// **The language arrives as a constructor parameter rather than as a `CompositionLocal`**, and that
// is the whole reason `TextRes` had to be buildable outside Compose. An alert is written into the
// OS's own database hours before anybody looks at it, with nothing composing — so the shell resolves
// the device's locale once and hands the table down here, exactly as it hands down the scheduler.
class GameNotifications(
    private val scheduler: NotificationScheduler,
    private val translations: Translations,
) {

    // `now` and everything `futureEvents` computes are in **game** time, which is not the same
    // clock the operating system raises alarms on the moment the debug menu skips the colony
    // forward. `toRealTime` is how the two are reconciled, and it defaults to the identity because
    // for an unskipped colony — every colony, until somebody shakes the phone — they are the same
    // clock. Without it a colony skipped four hours ahead books every alert four hours late, which
    // is the check-in loop, and on iPhone the check-in loop is the whole game.
    //
    // Passed per call rather than held, because the offset moves: the shell knows it at the instant
    // it commits, and a mapping captured at construction would be a stale one by the second skip.
    suspend fun sync(state: GameState, now: Instant, toRealTime: (Instant) -> Instant = { it }) {
        // Applied *after* `notificationsFor`, and it has to be. That function drops events already
        // due and trims the far landings to iOS's 64-request ceiling, both by comparing instants —
        // decisions that must be made in the clock the simulation computed them in. The translation
        // is monotone, so it moves every alert without reordering any of them, and the set that
        // reaches the platform is the same set with a different origin.
        scheduler.replaceAll(
            notificationsFor(state, now).map {
                LocalNotification(
                    id = it.id,
                    collapseId = it.collapseId,
                    title = translations.resolve(it.title),
                    body = translations.resolve(it.body),
                    at = toRealTime(it.at),
                )
            },
        )
    }
}

// **iOS keeps only the 64 soonest-firing pending requests and silently drops the rest.** That is
// the platform's number, not a choice made here, and it is why this file has a cap at all.
//
// The eviction rule is what makes it dangerous: iOS throws away the *furthest out*, which is
// precisely where long builds and research completions live. Uncapped, a player who dispatched
// thirty probes would lose the one alert they planned their evening around and keep thirty they
// did not. The fix is not to schedule fewer things, it is to make the choice **here**, where the
// game knows which alert is worth keeping, rather than at a boundary that only knows which is
// nearest.
internal const val IOS_PENDING_REQUEST_LIMIT: Int = 64

// **Anything subscribed that lands inside five minutes of the one before it is one piece of news.**
// The design's number, and it chains rather than windowing from the first of a run: three builds at
// 12:05, 12:09 and 12:13 collapse into one alert, because by the time the third lands the player has
// heard nothing about the first two either.
//
// It costs no runtime work. Every instant is known when the set is derived, so this is arithmetic
// done once at schedule time — and it hands the platform *fewer* pending requests than booking each
// one separately would.
private val GROUPING_WINDOW: Duration = 5.minutes

internal fun notificationsFor(state: GameState, now: Instant): List<PendingNotification> {
    // **The gate is `core`'s since 0.18, and the move is the settings sheet's doing rather than a
    // tidy-up.** It lived here from 0.5.0 on the design's own instruction, for a reason that is
    // untouched: `futureEvents` is the mirror of what `advance` will write to the log, and a build
    // completes whether or not anybody asked to hear it, so a core that dropped it would make the
    // mirror lie. `announcedEvents` is a second list derived from the first rather than a filter over
    // it, and the debug menu's "skip to the next event" still reads the unfiltered one.
    //
    // What moved it is that there are two readers now. This books the alerts; the settings sheet says
    // when the next one is due. One rule is the only thing that stops a preferences screen promising
    // a buzz the scheduler will not send.
    val pending = announcedEvents(state, now)

    // **The platform's ceiling is applied before the delivery rule and not after**, and the order is
    // load-bearing: `PER_CATEGORY` and `TOTAL` both fold the surviving events into fewer
    // notifications, so trimming afterwards would be counting sentences rather than alerts — and a
    // colony past the ceiling would schedule a summary claiming things it had just dropped.
    val kept = withinPlatformCeiling(pending)

    return when (state.alerts.delivery) {
        AlertDelivery.EACH -> oneEach(kept, state, now)
        AlertDelivery.PER_CATEGORY -> onePerCategory(kept, state, now)
        AlertDelivery.TOTAL -> oneInTotal(kept)
    }
}

// **One per thing, which is what the game has always done** — including the five-minute chain below.
// Davide's call, 2026-08-23: the chain stays here rather than becoming a fourth stop, because it is a
// dedupe for things landing in the same breath rather than a delivery rule.
private fun oneEach(kept: List<FutureEvent>, state: GameState, now: Instant): List<PendingNotification> {
    // Everything the player asked about that lands close enough together to be one sentence. Only
    // completions group: a probe landing and a fleet coming home are different kinds of news, and the
    // group's sentence is about upgrades.
    val groups = kept.filterIsInstance<FutureEvent.Completion>().chainedWithin(GROUPING_WINDOW)
    // A group fires at its **last** member's instant — "Three upgrades are done" is not true until
    // the third one is — so it takes that member's place in the list and the surrounding order is
    // untouched by construction. The earlier members are absorbed.
    val groupBy = groups.associateBy { it.last() as FutureEvent }
    val absorbed = groups.flatMap { it.dropLast(1) }.toSet()

    return kept.filterNot { it in absorbed }.map { event ->
        groupBy[event]?.takeIf { it.size > 1 }?.toNotification()
            ?: event.orderNotificationOrNull(state, now)
            ?: event.toNotification()
    }
}

// **A whole order as one sentence, or null if this is not one.** Shared by `oneEach` and
// `onePerCategory` because it is one rule and both of them got it wrong independently: the first
// applied it in a mode that must not consult the card, and the second did not apply it at all.
//
// Three conditions, and each is load-bearing:
//
// **`PER_ITEM`.** Under `BY_CATEGORY` the gate admits *every* queued hull — the card is not consulted,
// which is what the mode promises — so a card still holding `WHEN_ALL_DONE` from before the switch
// would collapse all of them onto one id. An order id is derived from the hull *type*, so two skiffs
// would book `order-SKIFF` twice, and neither platform will hold two pending requests under one
// identifier: the second replaces the first and an alert is silently lost.
//
// **`WHEN_ALL_DONE`.** `EACH_HULL` is one alert per hull by definition, and an absent entry is a card
// nobody tapped.
//
// **More than one hull still to come.** An order of one is the singleton alert, exactly as a group of
// one is the thing itself: a count is only worth saying when there is more than one thing to count.
private fun FutureEvent.orderNotificationOrNull(state: GameState, now: Instant): PendingNotification? =
    (this as? FutureEvent.ShipsComplete)
        ?.takeIf { state.alerts.mode == AlertMode.PER_ITEM }
        ?.takeIf { state.hullAlerts[it.ship] == HullAlert.WHEN_ALL_DONE }
        ?.let { hull ->
            orderSize(state, hull.ship, now).takeIf { it > 1 }?.let { hull.toOrderNotification(hulls = it) }
        }

// **One per kind, at the instant the last thing of that kind lands.**
//
// `PRICE_REACHED` is the exception and it is the design's: every other category announces something
// that has already happened and stays true, and this one announces a window that closes as soon as
// the resources go on something else. Held back, being told at 17:42 that a purchase was affordable
// at 14:50 is worse than not being told at all. It is also the one category whose count would be
// wrong in a summary — *1 price* is not a thing that finished — and it can only ever be one, because
// a colony watches one row.
private fun onePerCategory(kept: List<FutureEvent>, state: GameState, now: Instant): List<PendingNotification> = kept
    .groupBy { it.alertCategory }
    .flatMap { (category, events) ->
        if (category == AlertCategory.PRICE_REACHED || events.size == 1) {
            // A group of one is the thing itself, which is the rule the five-minute chain and the
            // hull order are both written to.
            //
            // **Except when the one is a whole order**, which is the second thing the first cut of
            // this stop got wrong. Under `PER_ITEM` a card on `WHEN_ALL_DONE` leaves exactly one
            // `ShipsComplete` standing — the gate has already dropped the rest — so the singleton
            // branch catches it and says *"A Skiff has left the yard"* to a player who asked to be
            // told when the whole order was done. Not a dropped alert: the wrong sentence, and the
            // count gone with it.
            events.map { it.orderNotificationOrNull(state, now) ?: it.toNotification() }
        } else {
            listOf(events.toGroupNotification(category))
        }
    }
    // **Reassembled and re-sorted, unlike the other two stops**, and it has to be: a group takes its
    // last member's instant, so the order the events arrived in is no longer the order the
    // notifications fire in.
    .sortedBy { it.at }

// **One notification, brought up to date rather than joined by a second.** Davide, 2026-08-23:
// *"Metal Mine upgraded at 12:04, and notification shows only that, then one ship is ready at 12:37,
// and the notification updates to show Metal Mine + 1 ship."*
//
// So this is emphatically **not** one alert held back until the last thing lands. §6 of
// `.claude/docs/ask-once-sheet.md`, which is the design this file implements, measured that on the
// reference colony and it came to five hours and thirty-eight minutes of
// silence — a mine finishing at 12:04 announced when a drive lands at 17:42, in a game played in
// five-minute check-ins. What ships instead fires at *every* instant and replaces what is already
// there, so the player is never told late and the tray never holds more than one.
//
// One booking per distinct instant, each summarising everything from `now` up to and including it.
// Several things landing on the same millisecond are one notification rather than two that would
// replace each other in the same frame.
private fun oneInTotal(kept: List<FutureEvent>): List<PendingNotification> = kept
    .map { it.at }
    .distinct()
    .sorted()
    .map { instant ->
        kept.filter { it.at <= instant }.toSummaryNotification(
            // **Distinct per booking even though they display as one**, because neither platform will
            // hold two *pending* requests under a single identifier — the second silently replaces
            // the first while it is still waiting, which would leave a colony holding only its last
            // alert. The instant separates them and never moves once a job has started.
            id = "$TOTAL_COLLAPSE_ID-${instant.toEpochMilliseconds()}",
            at = instant,
        )
    }

// The one tray identity in the game. Every other alert displays under its own id; these all display
// under this one, which is the whole of what the stop means. See `LocalNotification.collapseId` for
// what each platform can actually do with it.
private const val TOTAL_COLLAPSE_ID: String = "total"

// How many hulls of this type are still to come, which is what an order alert promises. Read off the
// yard rather than off the predictions, because the gate has already dropped all but the last of
// them — and an order that counted the whole queue would promise five hulls at an instant three of
// them have already arrived at.
private fun orderSize(state: GameState, ship: ShipType, now: Instant): Int =
    state.yard.count { it.ship == ship && it.completesAt > now }

// **Three kinds are unbounded, not two.** Six facilities, two research slots and one watch are
// bounded by the model — **nine** at the ceiling since 0.12.2 gave the adaptation branch a slot of
// its own, and none of them can ever be the thing that overflows. Probes were the only kind that ran
// in parallel with no cap; fleet runs are the second and the yard queue is the third, so the
// partition has to name all three or `bounded.size` stops describing the protected set and the trim
// arithmetic quietly under-counts.
//
// **The trim order is a content decision** and it is the sheet's proposal rather than a settled one:
// protect the model-bounded nine, then returns, then probe landings, then hulls — because a return
// carries resources that a full store can void, a probe carries information that does not spoil, and
// a hull on the slipway loses nothing at all by being announced late. It is last for that reason and
// not because it matters least; it is the only one of the three whose news keeps indefinitely.
// Davide's to overrule.
private fun withinPlatformCeiling(pending: List<FutureEvent>): List<FutureEvent> {
    val (unbounded, bounded) = pending.partition {
        it is FutureEvent.SurveyLands || it is FutureEvent.FleetReturns || it is FutureEvent.ShipsComplete
    }
    val (returns, rest) = unbounded.partition { it is FutureEvent.FleetReturns }
    val (landings, hulls) = rest.partition { it is FutureEvent.SurveyLands }

    // Trimmed from the far end, keeping the soonest. Two reasons, and the second is the one that
    // makes this safe: the near landings are the ones that will actually fire before the player
    // next opens the app, and every alert that fires causes a transition that re-derives this whole
    // set — so a far landing dropped today is re-booked long before it was due. Keeping the far
    // ones instead would drop alerts that nothing would ever come back for.
    val keptReturns = returns.take((IOS_PENDING_REQUEST_LIMIT - bounded.size).coerceAtLeast(0)).toSet()
    val keptLandings = landings
        .take((IOS_PENDING_REQUEST_LIMIT - bounded.size - keptReturns.size).coerceAtLeast(0))
        .toSet()
    val kept = keptReturns + keptLandings +
        hulls.take((IOS_PENDING_REQUEST_LIMIT - bounded.size - keptReturns.size - keptLandings.size).coerceAtLeast(0))

    // Filtered out of the original list rather than reassembled from the halves, so `futureEvents`'
    // ordering survives intact — including its tie-breaks, which say a landing sorts before a fleet
    // return at a shared instant. Concatenating the halves would put the return first and quietly
    // disagree with the log it is supposed to predict.
    return pending
        .filter { it in bounded || it in kept }
        // Belt and braces, and a no-op today: the bounded kinds top out at nine, so `kept` is
        // always sized to land exactly on the limit. It is here so the one promise this function
        // makes to the platform is enforced on the way out rather than inferred from the arithmetic
        // above.
        .take(IOS_PENDING_REQUEST_LIMIT)
}

// ── What a group of things says ─────────────────────────────────────────────────────────────

// Several things of one kind, as one sentence: a count-and-verb title and the kinds involved under
// it. **Never called with fewer than two** — one of anything is that thing's own alert.
//
// The body names the **distinct** subjects rather than every one of them, which is what makes one
// rule work for all seven categories. Three facilities are three different rows and read as a list;
// three skiffs are three identical objects, and "Skiff, Skiff and Skiff" is a sentence no player
// should ever be shown. The count is already in the title, so the body's job is which kinds.
private fun List<FutureEvent>.toGroupNotification(category: AlertCategory): PendingNotification {
    val at = maxOf { it.at }
    return pendingNotification(
        // Its kind and its instant, for the reason the upgrade group's id is its instant: a group's
        // subject is a *set* and one more switch changes it, where the instant it fires at does not.
        id = "group-${category.name}-${at.toEpochMilliseconds()}",
        title = Strings.alertGroupTitle(category, size),
        body = subjectList(),
        at = at,
    )
}

// Everything that has landed by one instant, as one sentence — the content of the single notification
// `AlertDelivery.TOTAL` keeps bringing up to date.
//
// Three shapes, and which one applies is a fact about the set rather than a setting:
// one thing is that thing's own alert, one kind is the group sentence above, and more than one kind
// is counts and nouns.
private fun List<FutureEvent>.toSummaryNotification(id: String, at: Instant): PendingNotification {
    val byCategory = groupBy { it.alertCategory }
    val single = singleOrNull()
    if (single != null) {
        // The singleton's own words, under the shared tray identity. A player whose colony has done
        // exactly one thing should read what happened, not a tally of one.
        val alone = single.toNotification()
        return alone.copy(id = id, collapseId = TOTAL_COLLAPSE_ID, at = at)
    }
    if (byCategory.size == 1) {
        val (category, events) = byCategory.entries.single()
        val group = events.toGroupNotification(category)
        return group.copy(id = id, collapseId = TOTAL_COLLAPSE_ID, at = at)
    }
    // **Counts and nouns, biggest first, no verb and no conjunction** — the design's rule, and the
    // reason for it is translation rather than brevity: a verb costs a plural agreement and *and*
    // costs a conjunction, and those are the two places English and Italian disagree in every string.
    val ranked = byCategory.entries.sortedWith(
        // Ties broken by the category's own declaration order, so the same colony always produces the
        // same sentence — `sortedByDescending` alone would leave two equal counts in map order, which
        // is insertion order, which is the order events happened to land in.
        compareByDescending<Map.Entry<AlertCategory, List<FutureEvent>>> { it.value.size }
            .thenBy { it.key.ordinal },
    )
    val shown = ranked.take(TITLE_CATEGORIES)
    val hidden = ranked.size - shown.size
    return PendingNotification(
        id = id,
        collapseId = TOTAL_COLLAPSE_ID,
        title = Strings.clauses(
            shown.map { Strings.alertCountClause(it.key, it.value.size) } +
                listOfNotNull(Strings.alertMoreCategories(hidden).takeIf { hidden > 0 }),
        ),
        // Written to be read from the left, because one line of it is all a lock screen shows.
        body = Strings.clauses(ranked.map { it.value.subjectList() }),
        at = at,
    )
}

// How many kinds the title carries before the rest become `+n`.
//
// **The design's rule is a character count — "take categories while they fit 28 characters" — and
// this is not that.** A character budget cannot be spent on a `TextRes`: the title is not a string
// until `Translations` resolves it, hours later and in whichever language the device is set to, and a
// rule that measured English would silently compact a different set of categories in Italian. Two is
// what reproduces both of the design's own drawn examples, and the sheet itself
// (`.claude/docs/ask-once-sheet.md` §4) says 28 is *"a measurement to take on a device."*
private const val TITLE_CATEGORIES: Int = 2

// The kinds involved, listed once each. See `toGroupNotification` for why it is distinct.
private fun List<FutureEvent>.subjectList(): TextRes {
    val names = map { it.subjectName() }.distinct()
    return names.singleOrNull() ?: Strings.listed(names)
}

// What one prediction is about, in the words the singleton alert for it would use — so a player told
// "3 facilities are done · Metal Mine and Solar Plant" and later told "Metal Mine reached level 4" is
// being told about one thing.
private fun FutureEvent.subjectName(): TextRes = when (this) {
    is FutureEvent.BuildCompletes -> building.displayName()
    is FutureEvent.ResearchCompletes -> technology.displayName()
    is FutureEvent.AdaptationCompletes -> technology.displayName()
    is FutureEvent.ShipsComplete -> ship.displayName()
    is FutureEvent.SurveyLands -> target.label()
    is FutureEvent.FleetReturns -> target.label()
    is FutureEvent.AffordableAt -> purchase.displayName()
}

// A whole order as one sentence. **The id is derived from the hull type**, unlike the upgrade group's
// — which has to fall back on its instant because a group's subject is a *set* that changes the
// moment one more row is subscribed. An order's subject is the type, which is fixed, and there is at
// most one of these per type, so it is unique by construction and stable across every sync.
private fun FutureEvent.ShipsComplete.toOrderNotification(hulls: Int): PendingNotification =
    pendingNotification(
        id = "order-${ship.name}",
        title = Strings.hullOrderDoneTitle(hulls),
        body = Strings.hullOrderDoneBody(ship.displayName()),
        at = at,
    )

// Runs of completions, each one within `window` of the one before it, earliest first and in the
// order `futureEvents` produced them. A run of one is still a run — the caller decides that a group
// of one is simply the thing itself.
//
// Chained rather than windowed from the head of each run, because what the rule is about is whether
// the player has been told anything yet: a fourth build landing four minutes after the third is not
// worth a second buzz even if it is a quarter of an hour after the first.
private fun List<FutureEvent.Completion>.chainedWithin(window: Duration): List<List<FutureEvent.Completion>> =
    fold(mutableListOf<MutableList<FutureEvent.Completion>>()) { runs, event ->
        val open = runs.lastOrNull()
        if (open != null && event.at - open.last().at <= window) open += event else runs += mutableListOf(event)
        runs
    }

// Several upgrades landing together, as one sentence. **The only alert in the game whose id is not
// derived from its subject** — and deliberately: a group's subject is a *set*, and subscribing to one
// more row a minute later would change it, where the instant it fires at never moves. A completion's
// instant is fixed the moment its job starts, so the same colony books the same group id every time,
// which is the property `replaceAll` rests on.
//
// No levels, unlike the singleton alerts. Seven "reached level N" clauses do not fit a lock screen,
// and what this one has to say is which things are done rather than what they became.
private fun List<FutureEvent.Completion>.toNotification(): PendingNotification = pendingNotification(
    id = "group-${last().at.toEpochMilliseconds()}",
    title = Strings.upgradesDoneTitle(size),
    // The second clause is the shipped `BuildCompletes` body's, word for word — the design's
    // instruction, and it is the right one even when a technology is in the list: the sentence is
    // about a decision waiting, and every one of these frees a slot to decide with.
    body = Strings.upgradesDoneBody(Strings.listed(map { it.displayName() })),
    at = last().at,
)

// Two through **eight**, which is every group this game can produce: six facilities build in
// parallel, one slot holds an applied project and — since 0.12.2 — a second holds a ladder beside
// it. Nine is unreachable. Spelled rather than printed as a digit — the game prints digits for
// levels, because a level is a number read off a row, and this is a count in a sentence.
//
// **The `else` is where the split would have gone wrong quietly.** Seven was the ceiling while the
// two research branches shared a slot, so seven lived in the fall-through; an eighth completion
// would have been announced as "Seven upgrades are done" with eight names listed under it, and
// nothing would have failed. Both counts have a branch now, and the `else` is unreachable rather
// than load-bearing.
private fun FutureEvent.Completion.displayName(): TextRes = when (this) {
    is FutureEvent.BuildCompletes -> building.displayName()
    is FutureEvent.ResearchCompletes -> technology.displayName()
    is FutureEvent.AdaptationCompletes -> technology.displayName()
}

private fun FutureEvent.toNotification(): PendingNotification = when (this) {
    is FutureEvent.BuildCompletes -> pendingNotification(
        // Stable and derived from the thing it is about: the same colony always produces the
        // same alerts, which is what makes replacing the set idempotent.
        id = "build-${building.name}",
        title = Strings.reachedLevel(building.displayName(), toLevel.value),
        body = Strings.buildCompleteBody(),
        at = at,
    )
    is FutureEvent.ResearchCompletes -> pendingNotification(
        // Only one project runs at a time, so the technology is not needed to keep this unique —
        // it is here because an id derived from the thing it is about is what makes replacing the
        // whole set idempotent, and because a second slot would otherwise silently collide.
        id = "research-${technology.name}",
        title = Strings.reachedLevel(technology.displayName(), toLevel.value),
        body = Strings.labFreeBody(),
        at = at,
    )
    is FutureEvent.AdaptationCompletes -> pendingNotification(
        // A separate id space from research even though the two share one slot, because the id is
        // derived from the thing it is about — and the two branches are not the same thing. Sharing
        // "research-…" would also collide the day a ladder and a technology are named alike.
        id = "adaptation-${technology.name}",
        title = Strings.reachedLevel(technology.displayName(), toLevel.value),
        // The only notification in the game that is about somewhere else. What changed is not the
        // colony but which worlds it could stand on, so the sentence points at the Galaxy tab.
        body = Strings.adaptationOpenedBody(),
        at = at,
    )
    is FutureEvent.ShipsComplete -> pendingNotification(
        // **The instant is the whole id, and it is the only one here derived from a time rather than
        // from a subject.** Every other alert names a thing there is one of — a facility, a
        // technology, a target system — and a hull names nothing: a queue of four skiffs is four
        // alerts about four objects that are identical in every respect except when they arrive. The
        // group id above has the same shape for the same reason, and it is safe for the same reason
        // too: the yard is serial, so no two of these can share an instant, and the instant of a
        // queued hull never moves once it is queued. Both halves are `GameState.init`'s serial rule,
        // which is checked on every decode.
        id = "hull-${at.toEpochMilliseconds()}",
        title = Strings.hullLeftYardTitle(ship.displayName()),
        body = Strings.hullLeftYardBody(),
        at = at,
    )
    is FutureEvent.SurveyLands -> pendingNotification(
        // The one id that has to carry its subject to stay unique: probes run in parallel with no
        // cap, so a colony can hold thirty of these at once where it holds one research and at most
        // six builds. Derived from the target for the same reason all of them are — it is what
        // makes replacing the whole set idempotent.
        id = "survey-${target.galaxy}-${target.system}",
        title = Strings.probeReachedTitle(target.label()),
        body = charted(worldsFound = worldsFound, settleable = settleable),
        at = at,
    )
    is FutureEvent.FleetReturns -> pendingNotification(
        // **This was the constant string `"fleet-arrival"`, and that was a latent defect that the
        // fleet slice turns into a live one.** A colony could only ever hold one returning fleet, so
        // one id was unique by construction; runs are parallel and uncapped, so two landing at once
        // would collide into a single alert and one would silently vanish. Derived from the run —
        // its target and the instant it left — for the same reason every other id here is derived
        // from its subject: that is what makes replacing the whole set idempotent.
        //
        // **The window joined it after the same defect was found a second time, one field along.**
        // `(target, dispatchedAt)` looks like it identifies a run and does not: `startRun` says in as
        // many words that *several runs may target one world*, with no `distinctBy` rule, and every
        // dispatch made inside one action carries one instant — so a manifest split across a 3h and a
        // 24h rung was two landings hours apart under one id, and the later one replaced the earlier
        // on both platforms. Not reachable from a finger today, because nothing calls `startRun` yet;
        // reachable the moment the dispatch sheet offers anything batched, which is exactly how
        // `"fleet-arrival"` waited for parallel runs.
        //
        // **With it, the id separates every pair of alerts that could differ**, which is the property
        // worth having rather than raw uniqueness. Two returns sharing all three parts share their
        // firing instant (`dispatchedAt + window`), their title, and their body — `target.label()` is
        // all the body names — so they are the same sentence at the same moment, and one alert is the
        // correct answer rather than a lost one. The manifest is deliberately not in the key: it
        // would split an id that nothing downstream could tell apart.
        //
        // In milliseconds because that is what the other instant here uses, and a window rounded to
        // minutes would quietly merge two rungs the day one of them stops being a whole hour.
        id = "run-${target.galaxy}-${target.system}-${target.slot}-" +
            "${dispatchedAt.toEpochMilliseconds()}-${(at - dispatchedAt).inWholeMilliseconds}",
        title = Strings.shipsHomeTitle(),
        body = Strings.shipsHomeBody(target.label()),
        at = at,
    )
    // **The only alert in the game that is not about something that happened** — it is about
    // something that became possible. It still obeys the rule the others do: it is a sentence a
    // player is happy to miss, and it asks for nothing.
    //
    // One id space across the three branches, unlike the research/adaptation pair above, and for a
    // reason that pair does not have: there is one watch in the whole game, so this set can never
    // hold two of these to collide.
    is FutureEvent.AffordableAt -> pendingNotification(
        id = "affordable-${purchase.subject()}",
        title = Strings.affordableTitle(purchase.displayName()),
        body = Strings.affordableBody(purchase.level()),
        at = at,
    )
}

// The enum constant, which is what every other id here is derived from and for the same reason: the
// same colony always produces the same alerts, which is what makes replacing the set idempotent.
private fun WatchedPurchase.subject(): String = when (this) {
    is WatchedPurchase.Facility -> building.name
    is WatchedPurchase.Project -> technology.name
    is WatchedPurchase.Ladder -> technology.name
}

// The same names the completion alerts use, so a player who is told they can afford a Deuterium
// Synthesizer and then told it reached level 8 is being told about one thing.
private fun WatchedPurchase.displayName(): TextRes = when (this) {
    is WatchedPurchase.Facility -> building.displayName()
    is WatchedPurchase.Project -> technology.displayName()
    is WatchedPurchase.Ladder -> technology.displayName()
}

// The two level types the branches carry, read for the one sentence that states a number. Written
// as a `when` rather than hidden behind a shared interface, because `BuildingLevel` and `TechLevel`
// staying different types is what stops one being passed where the other was meant.
private fun WatchedPurchase.level(): Int = when (this) {
    is WatchedPurchase.Facility -> toLevel.value
    is WatchedPurchase.Project -> toLevel.value
    is WatchedPurchase.Ladder -> toLevel.value
}

// PLACEHOLDER copy, and the two strings are the design rather than a formatting convenience.
//
// **The common one is the second.** Round 9 measured ~60 dispatches to see one settleable world, so
// an alert that only ever counted worlds would read as a payoff nearly every time it fired — and
// the one the verb exists for would look exactly like the fifty-nine that were not. Saying "none"
// plainly is what makes "1 settleable" mean anything when it finally arrives.
//
// **The words are the card's own, deliberately.** The Galaxy screen's landing footer says "none
// settleable" and this says "none settleable", off the same count — because the first version of
// this said "5 worth a look" about a landing whose card read "none settleable", and a game
// contradicting itself between the lock screen and the app is the worst failure a notification has
// available to it. See `FutureEvent.SurveyLands.settleable`.
//
// It also has to be a sentence a player is happy to *miss*, which is the constraint Davide set on
// this whole loop: nothing here asks them to open anything or implies that waiting cost them
// something. A probe that found nothing is a reading they bought, not a failure they slept through.
//
// Zero worlds is not a case: whether a slot holds a world is charted free and galaxy-wide, so
// `startSurvey` refuses a starless system outright rather than selling a flight to one.
private fun charted(worldsFound: Int, settleable: Int): TextRes = when (settleable) {
    0 -> Strings.chartedNoneSettleable(worldsFound)
    else -> Strings.chartedSettleable(worlds = worldsFound, settleable = settleable)
}

// PLACEHOLDER copy. What a notification says is player-facing content and therefore Davide's
// call; these say the one thing a check-in alert has to say — what happened, and that there is
// a decision waiting — and stay short enough to read on a lock screen.
//
// Written out in full rather than reusing the Colony screen's names, which abbreviate
// ("Deuterium Synth.") to fit a row that a notification does not have.
private fun BuildingType.displayName(): TextRes = Strings.buildingFullName(this)

// Capitalised, unlike the Colony screen's lower-case version of this — that one appears mid-sentence
// inside a strip ("your skiff is on station"), and this one opens a lock-screen title.
private fun ShipType.displayName(): TextRes = Strings.shipTitleName(this)

private fun Technology.displayName(): TextRes = Strings.technologyName(this)

// Spelled out in full, with the word the Galaxy screen's blocked rows drop to save eleven
// characters they do not have. A lock screen has the room, and "Gravitic reached level 3" on its
// own does not say what kind of thing climbed.
private fun AdaptationTechnology.displayName(): TextRes = Strings.adaptationFullName(this)

// A world, brackets and all — and now the bounded `GalaxyCoordinate` rather than the unbounded twin
// it replaced, so a label can no longer be written for an address that is off the map.
private fun GalaxyCoordinate.label(): TextRes = Strings.coordinate(galaxy, system, slot)

// No slot and no brackets: a probe is aimed at a star, not at a world, and the Galaxy screen's own
// header writes a system the same way — bare, because there is nothing for a bracket to separate it
// from.
private fun SystemAddress.label(): TextRes = Strings.systemAddressBare(galaxy, system)
