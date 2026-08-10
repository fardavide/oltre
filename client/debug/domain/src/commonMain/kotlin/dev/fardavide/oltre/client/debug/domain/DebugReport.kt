package dev.fardavide.oltre.client.debug.domain

import dev.fardavide.oltre.core.FutureEvent
import dev.fardavide.oltre.core.GameSave
import dev.fardavide.oltre.core.GameState
import kotlin.time.Duration
import kotlin.time.Instant

// What the colony looks like from underneath. Every field here answers a question that is otherwise
// only answerable by reading a file off a device — which is the whole reason the inspector exists:
// `status.md` lists five things whose first real test is an install, and until this shipped there
// was no way to see any of them from inside the running app.
data class DebugReport(
    // The two clocks, side by side, because the interesting number is the gap between them and a
    // reader who is shown one of them cannot compute it.
    val gameTime: Instant,
    val wallTime: Instant,
    val skippedBy: Duration,
    // Whether this colony's clock has ever been moved by hand. The flag that travels in the save;
    // shown here so it is visible before it matters rather than after.
    val debugUsed: Boolean,
    val schemaVersion: Int,
    val galaxySeed: Long,
    // The size of the append-only log, which is the one number that says how much has happened to
    // this colony — and, since a save is the log, roughly how big the file on disk is.
    val eventLogSize: Int,
    // What is in flight, split the way the model splits it: builds run one per facility, probes run
    // in parallel with no cap, and research is the single empire-wide slot either branch may hold.
    val buildsInFlight: Int,
    val surveysInFlight: Int,
    val researchSlotBusy: Boolean,
    val fleetInbound: Boolean,
    // Where the next skip would land, and what it would land on. Null when nothing is in flight,
    // which is exactly when `skipAhead` falls back to a flat hour.
    val nextEvent: FutureEvent?,
)

// Pure, and takes both clocks as parameters for the reason everything here does. `debugUsed` comes
// from the save envelope rather than from the state, because that is where it lives — see
// `GameSnapshot`.
fun debugReport(
    state: GameState,
    gameTime: Instant,
    wallTime: Instant,
    debugUsed: Boolean,
): DebugReport = DebugReport(
    gameTime = gameTime,
    wallTime = wallTime,
    // Read off the two clocks rather than passed in, so the number shown can never disagree with
    // the two instants shown beside it. Never negative: game time is wall time plus a non-negative
    // offset, and a device clock that jumped forward is clamped here rather than displayed as a
    // colony running backwards.
    skippedBy = maxOf(gameTime - wallTime, Duration.ZERO),
    debugUsed = debugUsed,
    schemaVersion = GameSave.SCHEMA_VERSION,
    galaxySeed = state.galaxy.seed.value,
    eventLogSize = state.eventLog.size,
    buildsInFlight = state.builds.size,
    surveysInFlight = state.surveys.size,
    researchSlotBusy = state.researchSlotFreesAt != null,
    fleetInbound = state.runs.isNotEmpty(),
    nextEvent = (skipAhead(state, gameTime) as? SkipAhead.ToEvent)?.event,
)
