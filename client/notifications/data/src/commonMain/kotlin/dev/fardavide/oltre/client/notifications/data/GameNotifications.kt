package dev.fardavide.oltre.client.notifications.data

import dev.fardavide.oltre.client.design.text.Strings
import dev.fardavide.oltre.client.design.text.TextRes
import dev.fardavide.oltre.client.design.text.Translations
import dev.fardavide.oltre.core.AdaptationTechnology
import dev.fardavide.oltre.core.BuildingType
import dev.fardavide.oltre.core.GalaxyCoordinate
import dev.fardavide.oltre.core.FutureEvent
import dev.fardavide.oltre.core.GameState
import dev.fardavide.oltre.core.HullAlert
import dev.fardavide.oltre.core.NotificationCategory
import dev.fardavide.oltre.core.NotificationGrouping
import dev.fardavide.oltre.core.NotificationScope
import dev.fardavide.oltre.core.NotificationSettings
import dev.fardavide.oltre.core.ShipType
import dev.fardavide.oltre.core.category
import dev.fardavide.oltre.core.SystemAddress
import dev.fardavide.oltre.core.Technology
import dev.fardavide.oltre.core.WatchedPurchase
import dev.fardavide.oltre.core.futureEvents
import dev.fardavide.oltre.core.target
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
    // **`settings` is required rather than defaulted, deliberately.** The obvious default is
    // `NotificationSettings.DEFAULT`, which is also exactly the value that means *the player's choice
    // is being ignored* — so a caller that forgot to load the preferences file would compile, ship,
    // and quietly announce everything the old way with no test able to tell. `MainScaffold`'s `tilt`
    // is required for the same reason. The test source set keeps a two-argument helper, where a
    // default costs nothing because the assertion is what says which mode is under test.
    suspend fun sync(
        state: GameState,
        now: Instant,
        settings: NotificationSettings,
        toRealTime: (Instant) -> Instant = { it },
    ) {
        // Applied *after* `notificationsFor`, and it has to be. That function drops events already
        // due and trims the far landings to iOS's 64-request ceiling, both by comparing instants —
        // decisions that must be made in the clock the simulation computed them in. The translation
        // is monotone, so it moves every alert without reordering any of them, and the set that
        // reaches the platform is the same set with a different origin.
        scheduler.replaceAll(
            notificationsFor(state, now, settings).map {
                LocalNotification(
                    id = it.id,
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

// How many kinds of news a summary title still spells a verb for. Past it the clauses go bare —
// "3 fleets, 2 upgrades, 1 probe" — because four clauses with verbs is a paragraph and both
// platforms truncate a title hard. **The softest number in the slice**: it is a guess at what a lock
// screen holds, and Claude Design's to move.
private const val CLAUSES_WITH_VERBS: Int = 3

// How many subjects an alert names before it starts counting them instead. The last of the four is
// the tally itself once there are more — "Skiff, Skiff, Skiff and 3 more" — so a queue of twelve
// hulls writes one line rather than a paragraph.
private const val SUBJECTS_NAMED: Int = 4

internal fun notificationsFor(
    state: GameState,
    now: Instant,
    settings: NotificationSettings,
): List<PendingNotification> {
    // `now` reaches core as well as filtering its answer, and the two uses are not the same. One
    // member of that list is not a job with a stored instant — the watch is projected forward from
    // the moment these stocks are accurate as of, which is this one.
    val upcoming = futureEvents(state, now = now)
        // core hands back everything still in flight; an event at or before `now` is either
        // about to be applied by `advance` or already has been, and either way an alert for it
        // would fire in the past. The platforms reject that anyway — dropping it here means one
        // rule instead of one per platform.
        .filter { it.at > now }

    // **The gate, and the whole of what 0.5.0 changed about the check-in loop: a completion nobody
    // asked about is not booked at all.** Not trimmed — absent, so there is nothing for it to
    // weigh against the platform's 64.
    //
    // Here rather than inside `futureEvents`, on the design's own instruction and for a reason of
    // its own: that list is the mirror of what `advance` will write to the log, and a build completes
    // whether or not anybody asked to hear it. A core that dropped it would make the mirror lie, and
    // the debug menu's "skip to the next event" reads the very same list.
    //
    // **The second gate finishes the job on the one kind that was still firing on its own.** A
    // delivery was exempt because there was no control on a hull card to ask it with; there is one
    // now, so the same rule reaches it — see `announcedHulls`, which answers *which* deliveries as
    // well as *whether*, because a hull card asks two questions rather than one.
    // **The third and fourth gates close the loop: from 0.15.4 there is no alert in this game that
    // was not asked for.** A fleet return and a probe landing were the last two kinds firing on
    // their own, on the argument that a flight is not something you wait on a *row* for — which was
    // true and drew the wrong conclusion. What it described was the absence of a control, and the
    // answer to that is a control: the bell beside Dispatch, Davide's call of 2026-08-22.
    //
    // **Read off the event rather than off `state.announceFlights`, and that is the whole of the
    // per-flight promise.** The colony's flag is where the *bell* is; each job carries the answer it
    // was sent under. A gate that consulted the flag would announce a run the player had already
    // decided against and silence one they had asked for, both of them retroactively.
    // **The hull card's two answers are ad-hoc's business alone.** In `BY_CATEGORY` the card is not
    // drawn at all, so an entry left in `hullAlerts` from before the switch is neither obeyed nor
    // cleared — it is simply not consulted, exactly as `subscribed` is not.
    val orders = when (settings.scope) {
        NotificationScope.AD_HOC -> announcedHulls(state, upcoming)
        NotificationScope.BY_CATEGORY -> emptyMap()
    }
    val pending = when (settings.scope) {
        NotificationScope.AD_HOC -> upcoming
            .filterNot { it is FutureEvent.Completion && it.target() !in state.subscribed }
            .filterNot { it is FutureEvent.ShipsComplete && it !in orders }
            .filterNot { it is FutureEvent.FleetReturns && !it.announced }
            .filterNot { it is FutureEvent.SurveyLands && !it.announced }
        // **One line where the other branch is four**, and that is the shape of the decision rather
        // than a coincidence: ad-hoc asks four different questions of four different fields because
        // each control lives on the row it is about, and this asks one question of one setting.
        // Filtered rather than `filterNot`-ed for the same reason: a category that is off is a
        // category nobody has said yes to, and the default is every one of them on.
        NotificationScope.BY_CATEGORY -> upcoming.filter { it.category() in settings.categories }
    }

    return when (settings.grouping) {
        NotificationGrouping.SINGLE -> pending.asSingles(orders)
        NotificationGrouping.GROUPED -> pending.asCategories(orders)
        NotificationGrouping.SUMMARY -> pending.asSummary(orders)
    }
}

// What ships today, unchanged: one alert per piece of news, with the five-minute chain and the hull
// card's order collapse both intact. Lifted out of `notificationsFor` when the other two packagings
// arrived, and deliberately not otherwise touched — **it is the only branch the platform's ceiling
// can be reached from**, because grouping books at most seven requests and a summary books one.
private fun List<FutureEvent>.asSingles(
    orders: Map<FutureEvent.ShipsComplete, Int>,
): List<PendingNotification> {
    val pending = this
    // Everything the player asked about that lands close enough together to be one sentence. Only
    // completions group: a probe landing and a fleet coming home are different kinds of news, and the
    // group's sentence is about upgrades.
    val groups = pending.filterIsInstance<FutureEvent.Completion>().chainedWithin(GROUPING_WINDOW)
    // A group fires at its **last** member's instant — "Three upgrades are done" is not true until
    // the third one is — so it takes that member's place in the list and the surrounding order is
    // untouched by construction. The earlier members are absorbed.
    val groupBy = groups.associateBy { it.last() as FutureEvent }
    val absorbed = groups.flatMap { it.dropLast(1) }.toSet()

    // **Three kinds are now unbounded, not two.** Six facilities, two research slots and one watch
    // are bounded by the model — **nine** at the ceiling since 0.12.2 gave the adaptation branch a
    // slot of its own, and none of them can ever be the thing that overflows. Probes were the only
    // kind that ran in parallel with no cap; fleet runs are the second and the yard queue is the
    // third, so the partition has to name all three or `bounded.size` stops describing the protected
    // set and the trim arithmetic quietly under-counts.
    //
    // **The trim order is a content decision** and it is the sheet's proposal rather than a settled
    // one: protect the model-bounded nine, then returns, then probe landings, then hulls — because a
    // return carries resources that a full store can void, a probe carries information that does not
    // spoil, and a hull on the slipway loses nothing at all by being announced late. It is last for
    // that reason and not because it matters least; it is the only one of the three whose news keeps
    // indefinitely. Davide's to overrule.
    val (unbounded, bounded) = pending.filterNot { it in absorbed }.partition {
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
        .map { event -> groupBy[event]?.takeIf { it.size > 1 }?.toNotification() ?: event.toNotification(orders) }
}

// **One alert per category, at the instant the last thing in it lands** — Davide's call of
// 2026-08-23, and there is deliberately no window. "A single notification for the category" is
// literal: three builds landing an hour, six hours and a day apart are one alert a day from now, and
// the first two are not announced early, they are not announced at all.
//
// The cost is stated where it belongs — `.claude/docs/settings-sheet.md` §2.3, which records that the
// build recommended the five-minute chain and was overruled with the cost in front of it. What makes
// it survivable is that the whole set is re-derived on every discrete transition and every
// foreground, so a collapsed alert is never stale, only late.
private fun List<FutureEvent>.asCategories(
    orders: Map<FutureEvent.ShipsComplete, Int>,
): List<PendingNotification> = groupBy { it.category() }
    .flatMap { (category, events) -> events.asOneAlert(category) ?: events.map { it.toNotification(orders) } }
    // The groups come out in first-encounter order, which is the order of the *earliest* member of
    // each — and a group fires at its latest. Sorting puts the list back in firing order, which is
    // the order every other path here hands the platform.
    .sortedBy { it.at }

// **One alert for everything**, and the compaction ladder is the whole of what it adds: Davide's
// *"the more info we need to show, the more we compact to fit everything, otherwise we show more
// details"*. Four rungs, and the first two are not this function's — one thing pending is that
// thing's own alert and one category is that category's sentence, both of which `asOneAlert` already
// answers, so a summary of a quiet colony is indistinguishable from no summary at all.
private fun List<FutureEvent>.asSummary(
    orders: Map<FutureEvent.ShipsComplete, Int>,
): List<PendingNotification> {
    val byCategory = groupBy { it.category() }
    val single = byCategory.entries.singleOrNull()
    if (single != null) {
        return single.value.asOneAlert(single.key) ?: single.value.map { it.toNotification(orders) }
    }
    if (byCategory.isEmpty()) return emptyList()
    // Verbs while there are few enough kinds to read them — "3 fleets are home and 2 upgrades are
    // done" — and bare tallies past that. The fallback to a tally is not only for the crossing: the
    // affordability watch has no clause of this shape at all, so a summary that includes it takes
    // its noun phrase whatever the other counts are.
    val clauses = byCategory.map { (category, events) ->
        Strings.categoryClause(category, events.size)
            .takeIf { byCategory.size <= CLAUSES_WITH_VERBS }
            ?: Strings.categoryTally(category, events.size)
    }
    return listOf(
        PendingNotification(
            // There is one. Every other id in this file is derived from a subject so that replacing
            // the whole set is idempotent; this one has no subject smaller than the colony, and a
            // constant is exactly as stable.
            id = "summary",
            title = Strings.listed(clauses),
            body = Strings.subjectsBody(Strings.listed(subjectNames())),
            // The last thing to land, which is what the sentence is about: "2 facilities are done" is
            // not true until the second one is. The list arrives in firing order, so this is its
            // maximum.
            at = last().at,
        ),
    )
}

// A whole category as one sentence, or **null when it has no business being one** — which is two
// cases and they are the same case twice. A category holding one thing says the thing, because a
// count is only worth saying when there is more than one thing to count; and the affordability watch
// holds one thing by construction, since there is a single watch in the game.
private fun List<FutureEvent>.asOneAlert(category: NotificationCategory): List<PendingNotification>? {
    // Asked before the count, and that ordering is what keeps both refusals live rather than leaving
    // one of them a defensive branch nothing can reach: the affordability watch holds exactly one
    // event by construction, so a size check first would answer every case before this one was ever
    // consulted.
    val title = Strings.categoryClause(category, size) ?: return null
    if (size < 2) return null
    return listOf(
        PendingNotification(
            // **The most stable id in this file**, and stronger than the five-minute group's one deck
            // up — that one falls back on its instant because its subject is a *set* that changes the
            // moment one more row is subscribed. A category's membership changes the same way; its
            // name cannot, and there is at most one of these per category.
            id = "category-${category.name}",
            title = title,
            body = Strings.subjectsBody(Strings.listed(subjectNames())),
            at = last().at,
        ),
    )
}

// The names an alert is about, compacted once there are more than a lock screen holds:
// "Skiff, Skiff, Skiff and 3 more". The tally goes in as the **last part** of the list rather than
// after it, so the conjunction in front of it is `listed`'s and therefore the language's.
private fun List<FutureEvent>.subjectNames(): List<TextRes> = when {
    size <= SUBJECTS_NAMED -> map { it.subject() }
    else -> take(SUBJECTS_NAMED - 1).map { it.subject() } + Strings.moreBesides(size - SUBJECTS_NAMED + 1)
}

// What a piece of news is *about*, in one word or two — the same names the singleton alerts use, so a
// player told "3 facilities are done · Metal Mine, Solar Plant and Extraction" is being told about
// the same three things those alerts would have named one at a time.
private fun FutureEvent.subject(): TextRes = when (this) {
    is FutureEvent.BuildCompletes -> building.displayName()
    is FutureEvent.ResearchCompletes -> technology.displayName()
    is FutureEvent.AdaptationCompletes -> technology.displayName()
    is FutureEvent.ShipsComplete -> ship.displayName()
    is FutureEvent.SurveyLands -> target.label()
    is FutureEvent.FleetReturns -> target.label()
    is FutureEvent.AffordableAt -> purchase.displayName()
}

// One event as one alert, with the hull card's order collapse applied on the way past. Every path in
// this file that emits a lone event goes through here, so an order of five hulls says "5 hulls have
// left the yard" whichever packaging produced it.
//
// `orders` is empty in `BY_CATEGORY`, where the card is not drawn and its answer is not consulted, so
// this is the plain singleton there.
private fun FutureEvent.toNotification(orders: Map<FutureEvent.ShipsComplete, Int>): PendingNotification =
    (this as? FutureEvent.ShipsComplete)
        // An order of one is the singleton alert, exactly as a group of one is the thing itself: a
        // count is only worth saying when there is more than one thing to count.
        ?.let { hull -> orders[hull]?.takeIf { it > 1 }?.let { hull.toOrderNotification(hulls = it) } }
        ?: toNotification()

// **Which deliveries are announced, and what each announcement stands for.** A hull card's control
// has three states rather than two — see `HullAlert` — so this answers a question the completions'
// gate does not have to: not just *whether* the player asked, but *which* of the two ways.
//
// Keyed per hull type all the way through, which is Davide's call of 2026-08-22 and not a detail of
// this file. The yard is one serial queue holding several types at once, and the question is per
// type: a player waiting on a hauler is not waiting on the two skiffs ahead of it.
//
// The count is the hulls **still to come**, because `upcoming` has already dropped whatever is due
// or past. An order that counted the whole queue would promise five hulls at an instant three of
// them arrive at.
private fun announcedHulls(state: GameState, upcoming: List<FutureEvent>): Map<FutureEvent.ShipsComplete, Int> =
    upcoming.filterIsInstance<FutureEvent.ShipsComplete>()
        .groupBy { it.ship }
        .flatMap { (ship, hulls) ->
            when (state.hullAlerts[ship]) {
                // Absent is off, which is every card nobody has tapped.
                null -> emptyList()
                // One alert, taking the **last** hull's place in the list — "your five skiffs are
                // built" is not true until the fifth one is, and `upcoming` is already in instant
                // order, so the surrounding sequence is untouched by construction. The same rule the
                // upgrade group fires by, one deck down.
                HullAlert.WHEN_ALL_DONE -> listOf(hulls.last() to hulls.size)
                HullAlert.EACH_HULL -> hulls.map { it to 1 }
            }
        }
        .toMap()

// A whole order as one sentence. **The id is derived from the hull type**, unlike the upgrade group's
// — which has to fall back on its instant because a group's subject is a *set* that changes the
// moment one more row is subscribed. An order's subject is the type, which is fixed, and there is at
// most one of these per type, so it is unique by construction and stable across every sync.
private fun FutureEvent.ShipsComplete.toOrderNotification(hulls: Int): PendingNotification =
    PendingNotification(
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
private fun List<FutureEvent.Completion>.toNotification(): PendingNotification = PendingNotification(
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
    is FutureEvent.BuildCompletes -> PendingNotification(
        // Stable and derived from the thing it is about: the same colony always produces the
        // same alerts, which is what makes replacing the set idempotent.
        id = "build-${building.name}",
        title = Strings.reachedLevel(building.displayName(), toLevel.value),
        body = Strings.buildCompleteBody(),
        at = at,
    )
    is FutureEvent.ResearchCompletes -> PendingNotification(
        // Only one project runs at a time, so the technology is not needed to keep this unique —
        // it is here because an id derived from the thing it is about is what makes replacing the
        // whole set idempotent, and because a second slot would otherwise silently collide.
        id = "research-${technology.name}",
        title = Strings.reachedLevel(technology.displayName(), toLevel.value),
        body = Strings.labFreeBody(),
        at = at,
    )
    is FutureEvent.AdaptationCompletes -> PendingNotification(
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
    is FutureEvent.ShipsComplete -> PendingNotification(
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
    is FutureEvent.SurveyLands -> PendingNotification(
        // The one id that has to carry its subject to stay unique: probes run in parallel with no
        // cap, so a colony can hold thirty of these at once where it holds one research and at most
        // six builds. Derived from the target for the same reason all of them are — it is what
        // makes replacing the whole set idempotent.
        id = "survey-${target.galaxy}-${target.system}",
        title = Strings.probeReachedTitle(target.label()),
        body = charted(worldsFound = worldsFound, settleable = settleable),
        at = at,
    )
    is FutureEvent.FleetReturns -> PendingNotification(
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
    is FutureEvent.AffordableAt -> PendingNotification(
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
