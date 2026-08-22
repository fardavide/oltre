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

    // A manifest carrying a hull that has no hold. **This is the one rule a fifth `ShipType` costs**,
    // and it is separate from `NoSuchShips` because the pool is not what is wrong: a colony can own
    // four scouts and still not be able to send one here.
    //
    // Refused whole rather than quietly stripped, the same shape `buildShips` uses for a hull with no
    // price — a run that silently left the scout behind would be a fleet the player did not choose,
    // and it would cost them a window to find out.
    data object NotAGatheringHull : StartRunResult

    // The window does not leave `MINIMUM_STATION` on the surface after the round trip. Unreachable
    // with skiffs from a home in galaxy 2 or 3 — the longest trip on the map is two galaxy hops at
    // 18h 20m out and back, which the 24h rung still covers — so it first occurs with the hauler.
    // Built now because the rule is the rule; the screen shows it by *omitting* the rung.
    data object WindowTooShort : StartRunResult

    // The world has nothing at all left of what was asked for. **Rare, and that is by construction
    // rather than by luck** — a stripped vein puts a whole unit back every twenty minutes, so exact
    // zero survives about a third of an hour and never outlives a check-in gap. The player who meets
    // it is the one who emptied the world seconds ago with the run before this one.
    //
    // A *partial* world never reaches here: it clamps, and the sheet says so before the tap. That
    // asymmetry is the design — see `.claude/docs/deposit-sheet.md` §4.
    data object Depleted : StartRunResult
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
    if (ships.counts.keys.any { it !in GATHERING_HULLS }) return StartRunResult.NotAGatheringHull
    if (target == state.galaxy.home) return StartRunResult.NotAValidTarget
    if (gathering == ResourceKind.DEUTERIUM) return StartRunResult.NotAValidTarget
    val holder = state.galaxy.holderOf(target)
    if (holder != null) return StartRunResult.NotAValidTarget
    val world = worldAt(state.galaxy.seed, target) ?: return StartRunResult.NotAValidTarget
    if (target !in state.galaxy.surveyed) return StartRunResult.Unsurveyed

    val home = state.galaxy.home
    val station = FleetBalance.stationFor(
        from = home,
        to = target,
        window = window,
        research = state.research,
        ships = ships,
    )
    if (station < FleetBalance.MINIMUM_STATION) return StartRunResult.WindowTooShort

    // What is actually in the ground, now. A world nobody has worked answers with its whole cap, so
    // this costs a generation and no state for the 98% of targets that have never been touched.
    val inTheGround = state.galaxy.remaining(target, gathering, at)
    if (inTheGround <= 0) return StartRunResult.Depleted

    // **Clamped, then debited, both at dispatch.** Debiting on arrival would let two runs ordered in
    // one check-in each see a full world and each take it, which is a duplication bug with a
    // narrative excuse. Clamping here is also what makes the sheet's promise true: the figure it
    // stated before the tap is the figure that lands, because the subtraction has already happened.
    val lifted = FleetBalance.cargo(
        world = world,
        gathering = gathering,
        ships = ships,
        station = station,
        danger = FleetBalance.danger(from = home, world = world),
        research = state.research,
    )
    val taken = minOf(lifted.of(gathering), inTheGround)
    val run = FleetRun(
        target = target,
        ships = ships,
        gathering = gathering,
        // Fixed at dispatch, and this is the rule one step past every other verb's. A mine level
        // completing mid-flight must not retroactively enrich a run already out, exactly as a
        // Robotics Factory finishing mid-build must not retroactively shorten it.
        cargo = hold(gathering, taken),
        dispatchedAt = at,
        returnsAt = at + window,
        // **Copied off the control rather than taken as a seventh argument**, and that is what makes
        // the promise checkable rather than merely intended. The bell the player was looking at *is*
        // `announceFlights`, so a run whose ask disagreed with it would need a caller that passed
        // something else — and there is no argument here to pass. See `toggleFlightAlerts`.
        announced = state.announceFlights,
    )
    return StartRunResult.Started(
        state.copy(
            // The pool is the *idle* count, so dispatched hulls leave it and arrivals return them —
            // the same shape `resources` has, where the cost is spent at dispatch and a `BuildJob`
            // holds no money.
            ships = state.ships - ships,
            runs = state.runs + run,
            galaxy = state.galaxy.withTaken(target = target, gathering = gathering, taken = taken, at = at),
            eventLog = state.eventLog + Event.FleetDispatched(
                target = target,
                gathering = gathering,
                ships = ships,
                at = at,
            ),
        ),
    )
}

// The hulls that have a hold, which is the whole of what a gathering run wants from a `ShipType`.
// A set rather than a `when`, mirroring `BuildShips.FOR_SALE` one file over, so the day the hauler
// gets a berth count this line is the only thing that moves.
//
// **It is a whitelist and not `!= SCOUT`, deliberately.** The escort and the settler are coming, and
// exactly one of them will have a hold; a blacklist would send the other two gathering on the day
// their constants land, silently and with a plausible number behind it.
private val GATHERING_HULLS: Set<ShipType> = setOf(ShipType.SKIFF, ShipType.HAULER)

private fun Resources.of(kind: ResourceKind): Long = when (kind) {
    ResourceKind.METAL -> metal
    ResourceKind.CRYSTAL -> crystal
    ResourceKind.DEUTERIUM -> deuterium
}

private fun hold(kind: ResourceKind, amount: Long): Resources = when (kind) {
    ResourceKind.METAL -> Resources.of(metal = amount)
    ResourceKind.CRYSTAL -> Resources.of(crystal = amount)
    ResourceKind.DEUTERIUM -> error("a run never gathers deuterium")
}
