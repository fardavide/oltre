package dev.fardavide.oltre.core

import kotlin.time.Duration
import kotlin.time.Instant

sealed interface StartRunResult {
    data class Started(val state: GameState) : StartRunResult

    // You cannot price a hold you cannot see. This is what chains the two verbs: a probe used to buy
    // a verdict and stop, and now it buys the right to send a ship — so surveying acquires a
    // second-order payoff that does not run out the way verdicts do.
    data object Unsurveyed : StartRunResult

    // Home, an empty slot, or a world somebody else holds. Note what is deliberately **not** here:
    // failing your tolerance bands. Hostility gates settling, not gathering — that is the whole
    // mechanic, and it is why 98% of the map stops being a wall.
    data object NotAValidTarget : StartRunResult

    data object NoSuchShips : StartRunResult

    // The window does not leave `MINIMUM_STATION` on the surface after the round trip. Unreachable
    // with skiffs from a home in galaxy 2 or 3 — the longest trip on the map is two galaxy hops at
    // 18h 20m out and back, which the 24h rung still covers — so it first occurs with the hauler.
    // Built now because the rule is the rule; the screen shows it by *omitting* the rung.
    data object WindowTooShort : StartRunResult
}

// The fifth verb. Same `(state, subject, at) -> sealed Result` shape as `startUpgrade`,
// `startResearch`, `startAdaptation` and `startSurvey`, and the same order of checks — validity,
// then requirements, then cost, then construct the job, then append the `Started` event.
//
// **It takes three subjects where every other verb takes one, and that is owned rather than hidden.**
// The target, the resource and the window are three facets of one commitment rather than three
// decisions about different things, and a verb that took them one at a time would need a
// partial-commitment state that nothing in this game has.
//
// Nothing gates it — no Robotics requirement, no research, no building. `startSurvey`'s reason
// transfers with more force: the verb whose whole job is to exist at hour zero cannot sit behind a
// building, and this one exists specifically to fix an opening that is empty 95.83% of its first 48
// hours. Gating the cure behind Robotics 1 at hour 6 would put it behind the disease.
//
// **Several runs may target one world.** No `distinctBy` rule, unlike `surveys`, and the reason is
// not convenience: a one-per-target rule would make the size of your surveyed map the fleet's
// ceiling, and then every probe would deliver ~4.75 guaranteed dispatch slots for 150 metal.
// Surveying would become strictly efficient and could never disappoint, which the galaxy sheet
// forbids in as many words. With repeats allowed a survey buys **better** targets, never **more** of
// them.
fun startRun(
    state: GameState,
    target: GalaxyCoordinate,
    gathering: ResourceKind,
    ships: Ships,
    window: Duration,
    at: Instant,
): StartRunResult {
    if (ships.isEmpty || !state.ships.covers(ships)) return StartRunResult.NoSuchShips
    if (target == state.galaxy.home) return StartRunResult.NotAValidTarget
    if (gathering == ResourceKind.DEUTERIUM) return StartRunResult.NotAValidTarget
    val holder = state.galaxy.holderOf(target)
    if (holder != null) return StartRunResult.NotAValidTarget
    val world = worldAt(state.galaxy.seed, target) ?: return StartRunResult.NotAValidTarget
    if (target !in state.galaxy.surveyed) return StartRunResult.Unsurveyed

    val home = state.galaxy.home
    val station = FleetBalance.stationFor(from = home, to = target, window = window)
    if (station < FleetBalance.MINIMUM_STATION) return StartRunResult.WindowTooShort

    val run = FleetRun(
        target = target,
        ships = ships,
        gathering = gathering,
        // Fixed at dispatch, and this is the rule one step past every other verb's. A mine level
        // completing mid-flight must not retroactively enrich a run already out, exactly as a
        // Robotics Factory finishing mid-build must not retroactively shorten it.
        cargo = FleetBalance.cargo(
            world = world,
            gathering = gathering,
            ships = ships,
            station = station,
            danger = FleetBalance.danger(from = home, world = world),
        ),
        dispatchedAt = at,
        returnsAt = at + window,
    )
    return StartRunResult.Started(
        state.copy(
            // The pool is the *idle* count, so dispatched hulls leave it and arrivals return them —
            // the same shape `resources` has, where the cost is spent at dispatch and a `BuildJob`
            // holds no money.
            ships = state.ships - ships,
            runs = state.runs + run,
            eventLog = state.eventLog + Event.FleetDispatched(
                target = target,
                gathering = gathering,
                ships = ships,
                at = at,
            ),
        ),
    )
}
