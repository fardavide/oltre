package dev.fardavide.oltre.client

import dev.fardavide.oltre.client.debug.domain.DebugClock
import dev.fardavide.oltre.client.notifications.data.GameNotifications
import dev.fardavide.oltre.client.save.data.GameStore
import dev.fardavide.oltre.core.GalaxySeed
import dev.fardavide.oltre.core.GameSnapshot
import dev.fardavide.oltre.core.GameState
import dev.fardavide.oltre.core.advance
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
        // Carried across, never re-derived: the flag is one-way, and a colony that was debugged
        // three launches ago is still a colony that was debugged.
        debugUsed = saved.debugUsed,
    )
}

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
