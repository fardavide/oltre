package dev.fardavide.oltre.core

import kotlin.time.Instant

sealed interface BuildShipsResult {
    data class Started(val state: GameState) : BuildShipsResult

    // An empty manifest. It is a refusal rather than a no-op that returns the state untouched,
    // because a `Started` here would append `ShipsBuilt` with nothing in it — and `GameSession`
    // reads a longer log as *something happened*, so the app would write a save and re-book every
    // pending alert for a transition that did not occur.
    data object NothingToBuild : BuildShipsResult

    // A hull whose slice has not landed. `FleetBalance.shipCost` raises for the other three by
    // design — *"each of them waits on exactly one design call, and a plausible number invented here
    // would be indistinguishable, to every later reader, from one somebody chose"* — and a verb
    // reachable from a finger may not throw. So the price's refusal to guess is carried back as a
    // result, which is exactly what the Shipyard's dimmed Hauler card means.
    data object NotForSale : BuildShipsResult

    // The whole manifest, priced together. Nothing is part-filled: a tap that bought two of the
    // three hulls asked for would spend money on a fleet the player did not choose.
    data object InsufficientResources : BuildShipsResult
}

// The sixth verb, and the only one that charges and delivers in the same call.
//
// **There is no yard job, and that is a sizing decision rather than an omission.** A fifth job kind
// would add a term to `Advance`'s completion union, a member to `FutureEvent`, a slot to the
// tie-break ladder and an id to the notification budget — all bought to put a second wait in front
// of the wait the mechanic is actually about. What a timer would have been protecting is already
// protected by the price: the hull curve compounds x1.5 against a linear return, which is how every
// ceiling in this game is proved. The probe's own philosophy applies unchanged — **the wait a hull
// costs you is the flight, not the yard** — and a check-in has to be able to buy and dispatch inside
// five minutes.
//
// Same `(state, subject, at) -> sealed Result` shape as the other five and the same order of checks:
// validity, then requirements, then cost, then the state change, then the `Started` event. It is
// gated by nothing — no Robotics requirement, no research, no building — for `startRun`'s reason one
// step back: the fleet exists to fill an empty opening, and the hull is what the fleet is made of.
//
// **It must append `Event.ShipsBuilt` even though nothing waits on it.** `GameSession` detects a
// discrete transition by `eventLog.size` changing, and that is what triggers both the save write and
// the notification re-sync. A verb that changed state without appending would be invisible to both,
// and the hull would vanish on the next launch.
fun buildShips(state: GameState, ships: Ships, at: Instant): BuildShipsResult {
    if (ships.isEmpty) return BuildShipsResult.NothingToBuild
    if (ships.counts.keys.any { it !in FOR_SALE }) return BuildShipsResult.NotForSale
    val cost = priceOf(ships, owned = state.ownedShips())
    if (!state.resources.covers(cost)) return BuildShipsResult.InsufficientResources
    return BuildShipsResult.Started(
        state.copy(
            resources = state.resources - cost,
            ships = state.ships + ships,
            eventLog = state.eventLog + Event.ShipsBuilt(ships = ships, at = at),
        ),
    )
}

// Everything the empire owns rather than everything it can send: `state.ships` is the **idle** pool,
// so a fleet that happens to be out would otherwise price the next hull as if it had never been
// bought — and the curve is the only ceiling this design has.
fun GameState.ownedShips(): Ships = runs.fold(ships) { total, run -> total + run.ships }

// The manifest walks the curve rather than multiplying one rung by the count, so buying the second
// and the third together costs exactly what buying them one after the other costs. A quantity
// discount here would be a way round the compounding price, which is the whole ceiling.
//
// Summed on whole units rather than through a `Resources` addition: every `shipCost` is built by
// `Resources.of`, so the whole-unit values are exact and `Resources.of` re-applies the same bound
// check `covers` depends on. `checkedTimes` is not reached because nothing here multiplies — the
// compounding happens inside `shipCost`, where it is already guarded.
private fun priceOf(ships: Ships, owned: Ships): Resources {
    var metal = 0L
    var crystal = 0L
    var deuterium = 0L
    for ((type, count) in ships.counts) {
        for (nth in 0 until count) {
            val rung = FleetBalance.shipCost(type, alreadyOwned = owned.countOf(type) + nth)
            metal += rung.metal
            crystal += rung.crystal
            deuterium += rung.deuterium
        }
    }
    return Resources.of(metal = metal, crystal = crystal, deuterium = deuterium)
}

// The hulls a slice has actually given a job to. A set rather than a `when` inside the loop, so the
// day the Hauler ships this file changes in one place and `FleetBalance.shipCost` in the other —
// and until then the two agree about which of the four constants is a product.
private val FOR_SALE: Set<ShipType> = setOf(ShipType.SKIFF)
