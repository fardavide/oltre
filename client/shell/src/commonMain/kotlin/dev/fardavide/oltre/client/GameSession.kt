package dev.fardavide.oltre.client

import dev.fardavide.oltre.core.GameSnapshot
import dev.fardavide.oltre.core.GameState
import dev.fardavide.oltre.core.advance
import kotlin.time.Instant

// What the shell holds between ticks: the simulation and the instant it is accurate as of. The
// pair is also exactly what a save contains, because everything in between is recomputed.
internal data class GameSession(val state: GameState, val lastUpdatedAt: Instant) {

    fun toSnapshot(): GameSnapshot = GameSnapshot(lastUpdatedAt = lastUpdatedAt, state = state)
}

// Opening the app is one operation whether or not there is a save: resuming is "advance the
// saved colony from the instant it was saved to now", and starting fresh is the same thing over
// zero elapsed time. A snapshot from the future — the device clock moved backwards, or the save
// travelled from another machine — is clamped rather than rejected, because core's advance
// refuses to run backwards and losing a colony over a clock skew would be absurd.
internal fun resume(saved: GameSnapshot?, now: Instant): GameSession {
    if (saved == null) return GameSession(GameState.initial(), now)
    val to = maxOf(now, saved.lastUpdatedAt)
    return GameSession(advance(saved.state, from = saved.lastUpdatedAt, to = to), to)
}

// Only discrete transitions are worth writing to disk. The state between two events is
// reproduced exactly by advance() from the last saved instant, so a tick that only accrued
// resources has nothing new to say — and on iPhone, a save per second would be a save per
// second of battery for nothing.
internal fun GameSession.hasNewEventsSince(previous: GameSession): Boolean =
    state.eventLog.size != previous.state.eventLog.size
