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

// The sixth verb: it charges now and delivers later, which is the one thing about it that changed at
// 0.9.0.
//
// **There is a yard job, and it is Davide's call** — *"I think we need to add time to build ships, it
// shouldn't be instantaneous"*, 2026-08-13. The sheet's §4 argued the opposite and the argument is
// kept in `FleetBalance.buildDuration` rather than deleted, because half of it survives: the
// compounding price really is the ceiling, and it is now ten times taller. What did not survive is
// the sizing claim that the wait a hull costs you is only the flight — a hull bought and dispatched
// inside one check-in grows a fleet as fast as the stores allow, which is what 0.8.0's 268% bracket
// measured.
//
// **The queue is serial**, Davide's call in the same breath, and it is the only job list in the game
// that is: one hull at a time, each falling in behind the last. A check-in that can pay for four
// hulls is buying a commitment rather than a fleet.
//
// Same `(state, subject, at) -> sealed Result` shape as the other five and the same order of checks:
// validity, then requirements, then cost, then the state change, then the `Started` event. It is
// gated by nothing — no Robotics requirement, no research, no building — for `startRun`'s reason one
// step back: the fleet exists to fill an empty opening, and the hull is what the fleet is made of.
// The Robotics Factory divides the wait without gating the verb, which is a different thing.
//
// **It appends `Event.ShipsOrdered`, and the delivery appends `Event.ShipsBuilt`.** The taxonomy's
// own Started/Completed pair, which this verb was the one member of the log to lack — and it had to
// append *something* even when it was instant, because `GameSession` detects a discrete transition
// by `eventLog.size` changing and that is what triggers both the save write and the notification
// re-sync.
fun buildShips(state: GameState, ships: Ships, at: Instant): BuildShipsResult {
    if (ships.isEmpty) return BuildShipsResult.NothingToBuild
    if (ships.counts.keys.any { it !in FOR_SALE }) return BuildShipsResult.NotForSale
    val committed = state.committedShips()
    val cost = priceOf(ships, owned = committed)
    if (!state.resources.covers(cost)) return BuildShipsResult.InsufficientResources
    return BuildShipsResult.Started(
        state.copy(
            resources = state.resources - cost,
            yard = state.yard + laidDown(ships, committed, state.buildings, from = state.yardFreesAt(at)),
            eventLog = state.eventLog + Event.ShipsOrdered(ships = ships, at = at),
        ),
    )
}

// Everything the empire owns rather than everything it can send: `state.ships` is the **idle** pool,
// so a fleet that happens to be out would otherwise look like a fleet that was never bought.
//
// **This is the fleet that exists** — a hull on the slipway is not in it, because it cannot be sent,
// cannot be counted and does not yet exist. What the *price* answers to is `committedShips` below.
fun GameState.ownedShips(): Ships = runs.fold(ships) { total, run -> total + run.ships }

// What the next hull is priced and timed against: everything owned, plus everything paid for and not
// yet delivered. **Without the yard term a queue would be a way round the compounding price**, which
// is the whole ceiling — four taps in one check-in would each pay the second rung, and the curve
// would only bind a player patient enough to wait between them.
fun GameState.committedShips(): Ships =
    yard.fold(ownedShips()) { total, job -> total + Ships.of(job.ship, 1) }

// When the slipway is next empty: the tail of the queue, or now if there is no queue. `maxOf` rather
// than the tail outright, because a state carried forward from a stale span can hold a job whose
// instant has already passed — the same defence `advance` applies at its own boundary.
private fun GameState.yardFreesAt(at: Instant): Instant = maxOf(at, yard.lastOrNull()?.completesAt ?: at)

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

// The queue the manifest becomes, chained end to end from the moment the slipway frees.
//
// **It walks the same curve `priceOf` walked, in the same order**, which is what makes the third
// skiff of an order longer than the first rather than three copies of one job. The two loops are
// deliberately the same shape: a manifest is priced rung by rung and served rung by rung, and if one
// of them ever stops agreeing with the other, a hull will cost what the fourth costs and take what
// the second takes.
//
// The Robotics level is read once, here, and never again — the rule every other job follows.
private fun laidDown(ships: Ships, owned: Ships, buildings: Buildings, from: Instant): List<YardJob> {
    val robotics = buildings.levelOf(BuildingType.ROBOTICS_FACTORY)
    var startsAt = from
    val jobs = mutableListOf<YardJob>()
    for ((type, count) in ships.counts) {
        for (nth in 0 until count) {
            val completesAt = startsAt +
                FleetBalance.buildDuration(type, alreadyOwned = owned.countOf(type) + nth, roboticsFactory = robotics)
            jobs += YardJob(ship = type, startedAt = startsAt, completesAt = completesAt)
            startsAt = completesAt
        }
    }
    return jobs
}

// The hulls a slice has actually given a job to. A set rather than a `when` inside the loop, so the
// day the Hauler ships this file changes in one place and `FleetBalance.shipCost` in the other —
// and until then the two agree about which of the four constants is a product.
private val FOR_SALE: Set<ShipType> = setOf(ShipType.SKIFF)
