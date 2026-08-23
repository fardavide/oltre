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
// shouldn't be instantaneous"*, 2026-08-13. The sheet's §4 argued the opposite, and what it argued
// was that the compounding price was already the ceiling a timer would be protecting. That half has
// since gone too: 0.10.1 made the hull price flat, so **the yard is the ceiling** rather than the
// second of two. What was true at 0.8.0 and is more true now is the sizing claim it replaced — a hull
// bought and dispatched inside one check-in grows a fleet as fast as the stores allow, which is what
// that release's 268% bracket measured.
//
// **The queue is serial**, Davide's call in the same breath, and it is the only job list in the game
// that is: one hull at a time, each falling in behind the last. A check-in that can pay for four
// hulls is buying a commitment rather than a fleet — and since the price stopped climbing, that
// sentence is the only thing standing between deep stores and a fleet bought in one tap.
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
    if (ships.counts.keys.any { it !in FleetBalance.FOR_SALE }) return BuildShipsResult.NotForSale
    val cost = state.priceOf(ships)
    if (!state.resources.covers(cost)) return BuildShipsResult.InsufficientResources
    return BuildShipsResult.Started(
        state.copy(
            resources = state.resources - cost,
            yard = state.yard + laidDown(ships, state.buildings, from = state.yardFreesAt(at)),
        ).logging(
            Event.ShipsOrdered(ships = ships, at = at),
        ),
    )
}

// Everything the empire owns rather than everything it can send: `state.ships` is the **idle** pool,
// so a fleet that happens to be out would otherwise look like a fleet that was never bought.
//
// **This is the fleet that exists** — a hull on the slipway is not in it, because it cannot be sent,
// cannot be counted and does not yet exist.
//
// It is a reading rather than an input now. Its sibling `committedShips()` — owned plus everything on
// the slipway — existed for one job, *"without the yard term a queue would be a way round the
// compounding price"*, and 0.10.1 deleted the compounding price, so it was deleted with it. Nothing is
// priced against a fleet any more; see `FleetBalance.shipCost`.
//
// **Probes count too, since a probe flies a scout.** A hull out on a survey is out in exactly the
// sense a hull out on a run is, so leaving it out of this fold would make a colony's fleet appear to
// shrink every time it looked at the map.
fun GameState.ownedShips(): Ships =
    surveys.fold(runs.fold(ships) { total, run -> total + run.ships }) { total, _ -> total + SurveyBalance.SHIPS }

// When the slipway is next empty: the tail of the queue, or now if there is no queue. `maxOf` rather
// than the tail outright, because a state carried forward from a stale span can hold a job whose
// instant has already passed — the same defence `advance` applies at its own boundary.
private fun GameState.yardFreesAt(at: Instant): Instant = maxOf(at, yard.lastOrNull()?.completesAt ?: at)

// **What a manifest costs, and the one place that answers it.** Public, and on `GameState` rather
// than free-standing, because a screen has to be able to ask — and until 0.10.1 it could not, so the
// Shipyard assembled the price itself out of `FleetBalance.shipCost` and a fleet count it derived to
// match this function. Two implementations of one rule, kept in agreement by a comment and a
// behaviour test, which is why flattening the price cost four files instead of one.
//
// **The receiver is unused today and it is the whole point.** A caller passes the state, which it
// already holds, rather than an ingredient of the pricing rule — so a price that starts reading the
// fleet again, or the research, or a hull-yard technology, changes this function and nothing that
// calls it. That is the distinction `PlaceholderBalance.upgradeCost(building, toLevel)` gets right
// by luck: `toLevel` is a fact about what is being bought. `alreadyOwned` was a fact about how it was
// priced, and a parameter like that outlives the rule that justified it by exactly one release.
//
// One price per hull, summed. It walked a compounding curve rung by rung until 0.10.1 and the
// property that walk existed to protect is the one still asserted: buying two together costs exactly
// what buying them one after the other costs.
//
// `checkedTimes` guards the multiplication: a manifest's count is whatever a caller passed, so a hull
// price times a count is the one place here that can leave a Long — and a cost that has wrapped is a
// cost `covers` reads as free.
//
// It raises for a hull with no price, exactly as `FleetBalance.shipCost` does. `buildShips` checks
// `FOR_SALE` first and the Shipyard draws those hulls as dimmed cards, so no caller reaches it — and
// a balance object that refuses to invent a number should not start inventing one here.
fun GameState.priceOf(ships: Ships): Resources {
    var metal = 0L
    var crystal = 0L
    var deuterium = 0L
    for ((type, count) in ships.counts) {
        val each = FleetBalance.shipCost(type)
        metal += checkedTimes(each.metal, count.toLong()) { "$type metal" }
        crystal += checkedTimes(each.crystal, count.toLong()) { "$type crystal" }
        deuterium += checkedTimes(each.deuterium, count.toLong()) { "$type deuterium" }
    }
    return Resources.of(metal = metal, crystal = crystal, deuterium = deuterium)
}

// The queue the manifest becomes, chained end to end from the moment the slipway frees.
//
// **Three copies of one job now, and that is the whole of what a deep order costs.** The wait is
// taken from the price and the price is flat, so the third skiff of an order is the first one again,
// laid behind it. With the compounding curve gone this chain is the only thing that makes buying four
// hulls different from buying one — which is why the loop stays a loop over individual jobs rather
// than becoming a count on one.
//
// The Robotics level is read once, here, and never again — the rule every other job follows.
private fun laidDown(ships: Ships, buildings: Buildings, from: Instant): List<YardJob> {
    val robotics = buildings.levelOf(BuildingType.ROBOTICS_FACTORY)
    var startsAt = from
    val jobs = mutableListOf<YardJob>()
    for ((type, count) in ships.counts) {
        val each = FleetBalance.buildDuration(type, roboticsFactory = robotics)
        repeat(count) {
            val completesAt = startsAt + each
            jobs += YardJob(ship = type, startedAt = startsAt, completesAt = completesAt)
            startsAt = completesAt
        }
    }
    return jobs
}
