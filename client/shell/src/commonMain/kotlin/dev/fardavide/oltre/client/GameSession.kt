package dev.fardavide.oltre.client

import dev.fardavide.oltre.client.debug.domain.DebugClock
import dev.fardavide.oltre.client.debug.domain.skipping
import dev.fardavide.oltre.client.notifications.data.GameNotifications
import dev.fardavide.oltre.client.save.data.GameStore
import dev.fardavide.oltre.core.GalaxySeed
import dev.fardavide.oltre.core.GameSnapshot
import dev.fardavide.oltre.core.GameState
import dev.fardavide.oltre.core.ShipType
import dev.fardavide.oltre.core.WatchTarget
import dev.fardavide.oltre.core.advance
import dev.fardavide.oltre.core.cycleHullAlert
import dev.fardavide.oltre.core.toggleAlert
import dev.fardavide.oltre.core.toggleFlightAlerts
import kotlin.time.Instant

// What the shell holds between ticks: the simulation and the instant it is accurate as of. The
// pair is also exactly what a save contains, because everything in between is recomputed.
//
// Plus one flag that is not part of the simulation at all — whether the debug menu has ever acted
// on this colony. It rides here because it rides in the save, and `toSnapshot` is where the two
// meet; nothing in the game reads it. Defaulted, so the fifty existing constructions of this type
// in the tests still say what they meant.
internal data class GameSession(
    val state: GameState,
    val lastUpdatedAt: Instant,
    val debugUsed: Boolean = false,
    // **The instant this launch advanced *from*, which is the only way to know what happened while
    // the app was closed.** `lastUpdatedAt` is where the advance ended, so by the time anything is
    // composed it is already the present and every event the launch produced looks old.
    //
    // The Galaxy tab's discovery section is measured from here: a world surveyed inside the span
    // that just ran is new, and one surveyed before it is not. Defaults to `lastUpdatedAt` — an
    // empty span, so nothing is new — which is right for a colony that has not been resumed and is
    // what keeps the fifty existing constructions of this type saying what they meant.
    val resumedFrom: Instant = lastUpdatedAt,
) {

    fun toSnapshot(): GameSnapshot =
        GameSnapshot(lastUpdatedAt = lastUpdatedAt, debugUsed = debugUsed, state = state)
}

// Opening the app is one operation whether or not there is a save: resuming is "advance the
// saved colony from the instant it was saved to now", and starting fresh is the same thing over
// zero elapsed time. A snapshot from the future — the device clock moved backwards, or the save
// travelled from another machine — is clamped rather than rejected, because core's advance
// refuses to run backwards and losing a colony over a clock skew would be absurd.
//
// Since 0.2.5 a save can be stamped in the future *on purpose*: skipping ahead writes the colony
// down at the instant it was skipped to. The clamp still handles it correctly, but on its own it
// would freeze the colony there until the wall clock caught up — so the caller derives a
// `DebugClock` from the same saved instant and passes `now` already offset. See `DebugClock.resuming`.
internal fun resume(saved: GameSnapshot?, now: Instant): GameSession {
    // A new colony needs a galaxy, and a galaxy needs a seed that core cannot mint for itself —
    // it reads no clock and no random source. The composition root is where the clock already
    // is, so the instant the colony was founded becomes the seed of the map it was founded in.
    // Derived rather than drawn, deliberately: `resume` stays a pure function of its arguments,
    // which is what keeps it testable and what stops a retry handing back a different galaxy.
    if (saved == null) return GameSession(GameState.initial(GalaxySeed(now.toEpochMilliseconds())), now)
    val to = maxOf(now, saved.lastUpdatedAt)
    return GameSession(
        state = advance(saved.state, from = saved.lastUpdatedAt, to = to),
        lastUpdatedAt = to,
        // Where the advance started. A probe that landed overnight completed inside `[from, to]`,
        // and this is the `from`.
        resumedFrom = saved.lastUpdatedAt,
        // Carried across, never re-derived: the flag is one-way, and a colony that was debugged
        // three launches ago is still a colony that was debugged.
        debugUsed = saved.debugUsed,
    )
}

// One tick: bring the colony up to now and leave everything else alone.
//
// The clamp is the load-bearing part. A wall clock can step backwards — NTP, or the player changing
// the device time — and `advance` requires `to >= from`, so without it a colony crashes on a clock
// correction. Extracted from the tick loop for the reason the debug actions were: inside a
// `LaunchedEffect` this is four lines nothing can execute, and the clamp is exactly the kind of edge
// that deserves a test rather than a comment.
internal fun GameSession.ticked(clock: DebugClock, wallClock: Instant): GameSession {
    val now = maxOf(clock.now(wallClock), lastUpdatedAt)
    return copy(state = advance(state, from = lastUpdatedAt, to = now), lastUpdatedAt = now)
}

// One player action, and the only safe order for it: bring the simulation up to the instant the
// player acted, then ask core to apply the action *at that instant*. Acting on a stale state would
// spend resources the colony has not accrued yet — which is the bug this shape exists to prevent,
// and which nothing could have caught while it lived inside a composable.
internal fun GameSession.acting(
    clock: DebugClock,
    wallClock: Instant,
    transition: (GameState, Instant) -> GameState,
): GameSession {
    val at = maxOf(clock.now(wallClock), lastUpdatedAt)
    val advanced = advance(state, from = lastUpdatedAt, to = at)
    return copy(state = transition(advanced, at), lastUpdatedAt = at)
}

// The square's action, and **the one verb in the app that transitions *before* it advances.**
//
// `acting`'s order — advance, then apply — exists to stop a player spending resources the colony has
// not accrued yet. Asking for an alert spends nothing, and the order actively hurts it: `toggleAlert`
// reads whether the row is running to decide which of the two things a tap means, and `advance` can
// finish that very job inside the span. A tap on the lit bell of a build that landed 400ms ago would
// then find it settled, fall through to the affordability branch, and quietly move the empire's one
// watch onto it — unbooking the alert the player had actually set, and persisting it, because this
// action commits unconditionally.
//
// Toggling first asks the question against the state the player was looking at, which is the state
// they answered. The advance that follows is not skipped and is what keeps the result honest:
// `withoutSpentWatch` drops a subscription whose job landed inside the span, so a tap on a row that
// finished mid-gesture ends where it would have ended anyway — with nothing subscribed.
internal fun GameSession.alerting(clock: DebugClock, wallClock: Instant, target: WatchTarget): GameSession {
    val at = maxOf(clock.now(wallClock), lastUpdatedAt)
    return copy(state = advance(toggleAlert(state, target), from = lastUpdatedAt, to = at), lastUpdatedAt = at)
}

// The Shipyard card's square, in `alerting`'s shape — cycle first, then advance.
//
// **It is the same shape and not for the same reason, and the difference is worth stating because
// the first draft of this comment got it backwards.** That one claimed the ordering rescued a tap on
// an order that landed 400ms ago. It does not: both orderings end with no entry, because
// `withoutFinishedHullAlerts` prunes exactly what `cycleHullAlert`'s guard would have refused. The
// ordering here is provably immaterial — `advance` only ever *removes* yard jobs, so a type present
// after the span was present before it, and a type absent after it ends unasked either way.
//
// **What makes `alerting` genuinely order-sensitive is a branch this verb does not have.**
// `toggleAlert` reads `isRunning(target)` and does one of two *different* things — subscribe, or
// move the empire's single watch — so advancing first sends the tap down the other branch and
// silently unbooks something. `cycleHullAlert` only ever decides between acting and not acting on
// one hull type, and the prune makes those two answers converge.
//
// So this is consistency rather than a fix, and it is kept for that: the two squares in the app
// should not be wired in opposite orders, and the day `cycleHullAlert` grows a second branch this is
// already on the safe side of it. `a tap at the instant an order lands settles the same way whichever
// end it is taken from` is the test that holds the claim above, rather than a comment asserting it.
internal fun GameSession.alertingHull(clock: DebugClock, wallClock: Instant, ship: ShipType): GameSession {
    val at = maxOf(clock.now(wallClock), lastUpdatedAt)
    return copy(state = advance(cycleHullAlert(state, ship), from = lastUpdatedAt, to = at), lastUpdatedAt = at)
}

// The dispatch sheet's bell, and the third square wired the same way — cycle first, then advance.
//
// **Here the ordering really is immaterial, and for a reason neither of the two above has.**
// `alerting` is order-sensitive because `toggleAlert` branches on whether the row is running, and
// `alertingHull` copies it for consistency because a queue can empty inside the span. This verb
// reads nothing about the colony at all: it flips one boolean that no job is keyed to and that
// `advance` never touches. It is written in the same shape as its two neighbours so that the three
// squares in the app are not wired three different ways — the day this grows a branch it is already
// on the safe side of it.
//
// It still has to *advance*, and that is not decoration: this action commits, and committing writes
// the save at `lastUpdatedAt`. A session that stamped a new instant without advancing to it would
// write a colony that had not caught up to its own clock.
internal fun GameSession.alertingFlights(clock: DebugClock, wallClock: Instant): GameSession =
    preferring(clock, wallClock) { toggleFlightAlerts(it) }

// **A standing answer being changed, which is a different kind of action from the three above.**
// Those point at something — a row, a hull type, the next flight — and each has to be careful about
// what the span may have done to the thing they point at. A preference points at nothing: it reads
// no job, no stock and no queue, so the ordering that `alerting` is delicate about cannot matter here
// and the transition takes a state rather than a state and an instant.
//
// It still advances, and that is not decoration: these actions commit, and committing writes the save
// at `lastUpdatedAt`. A session that stamped a new instant without advancing to it would write a
// colony that had not caught up to its own clock.
//
// **And it commits for a reason that has nothing to do with the save.** Nothing here writes an event,
// so `act` would decline — and the point of every one of these taps is the schedule, which is booked
// by the `notifications.sync` inside `commit`. A ladder that moved and told the platform nothing
// would be the whole sheet failing silently.
internal fun GameSession.preferring(
    clock: DebugClock,
    wallClock: Instant,
    transition: (GameState) -> GameState,
): GameSession {
    val at = maxOf(clock.now(wallClock), lastUpdatedAt)
    return copy(state = advance(transition(state), from = lastUpdatedAt, to = at), lastUpdatedAt = at)
}

// A session and the clock that goes with it. The two always move together — a session stamped in
// the future with a clock that does not know it is the frozen-game bug in another shape — so the
// two debug actions hand back both rather than leaving the caller to remember the second.
internal data class DebugOutcome(val session: GameSession, val clock: DebugClock)

// The debug menu's one time verb, as a function rather than as a body inside a composable.
//
// It is `act`'s shape with two differences, and both are the point: the instant is chosen by the
// simulation rather than by the clock, and the caller commits unconditionally — a skip that changed
// no event still moved the colony's clock, and the offset only survives a relaunch because the save
// records the instant it reached.
//
// The arithmetic itself is `:client:debug:domain`'s, tested there against a colony rather than
// against a screen. What is left here is the part that is genuinely about a `GameSession`: carrying
// the debug mark, which is the shell's to carry because the shell is what writes the save.
internal fun GameSession.skipped(clock: DebugClock, wallClock: Instant): DebugOutcome {
    val outcome = skipping(state, lastUpdatedAt = lastUpdatedAt, clock = clock, wallClock = wallClock)
    return DebugOutcome(
        session = GameSession(state = outcome.state, lastUpdatedAt = outcome.at, debugUsed = true),
        clock = outcome.clock,
    )
}

// A reset is a first launch: `resume` with nothing saved is the same path the app takes when it
// opens for the first time, so there is no second way of founding a colony to keep in step.
//
// The offset is dropped with it. A new colony is not the old one's future, and starting it hours
// ahead of the wall clock would be inheriting a debt it never ran up.
//
// **And the mark is dropped with it too** — Davide's call, 2026-08-09, reversing what 0.2.5 shipped.
// The flag answers "has this colony's clock been moved by hand", and the colony that comes back has
// no history at all: nothing has been skipped in it, and it is indistinguishable from one founded on
// a first launch. Carrying the mark across would have made it a fact about the *device* rather than
// about the save, which is not what it is for. So skipping is the only thing that sets it.
internal fun resetColony(wallClock: Instant): DebugOutcome = DebugOutcome(
    session = resume(saved = null, now = wallClock),
    clock = DebugClock(),
)

// Only discrete transitions are worth writing to disk. The state between two events is
// reproduced exactly by advance() from the last saved instant, so a tick that only accrued
// resources has nothing new to say — and on iPhone, a save per second would be a save per
// second of battery for nothing.
internal fun GameSession.hasNewEventsSince(previous: GameSession): Boolean =
    state.eventLog.size != previous.state.eventLog.size

// Writing the colony down and telling the player when to come back are one operation, because
// they answer to the same trigger and to the same instant. Split them and they drift: a save
// without a reschedule leaves an alert promising a build that has already finished, and a
// reschedule without a save promises something the next launch will not remember.
//
// `clock` is the third thing that has to agree with the other two. Every instant in `state` is in
// *game* time, and the operating system raises alarms in real time — the same clock until the
// debug menu skips the colony forward, and four hours apart the moment it does. Defaulted to an
// untouched clock, which is the identity, so every caller that has no debug menu in it is unchanged.
internal suspend fun GameSession.commit(
    store: GameStore,
    notifications: GameNotifications,
    clock: DebugClock = DebugClock(),
) {
    store.save(toSnapshot())
    notifications.sync(state, now = lastUpdatedAt, toRealTime = clock::toRealTime)
}
