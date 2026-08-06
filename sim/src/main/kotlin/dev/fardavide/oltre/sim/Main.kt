package dev.fardavide.oltre.sim

import dev.fardavide.oltre.core.BuildingLevel
import dev.fardavide.oltre.core.BuildingType
import dev.fardavide.oltre.core.GameState
import dev.fardavide.oltre.core.PlaceholderBalance
import dev.fardavide.oltre.core.StartUpgradeResult
import dev.fardavide.oltre.core.advance
import dev.fardavide.oltre.core.startUpgrade
import kotlin.time.Duration.Companion.hours
import kotlin.time.Instant

// Headless balancing harness. Never ships. Fast-forwards a week of a greedy strategy that,
// once per simulated hour, starts every mine-or-plant upgrade it can afford — cheapest first,
// since builds now run in parallel and each start eats into the same stock.
fun main() {
    val start = Instant.fromEpochMilliseconds(0)
    var state = GameState.initial()
    var now = start

    repeat(7 * 24) {
        val next = now + 1.hours
        state = advance(state, from = now, to = next)
        now = next

        val candidates = listOf(BuildingType.METAL_MINE, BuildingType.CRYSTAL_MINE, BuildingType.SOLAR_PLANT)
        candidates
            .map { it to PlaceholderBalance.upgradeCost(it, BuildingLevel(state.buildings.levelOf(it).value + 1)) }
            .sortedBy { (_, cost) -> cost.metal + cost.crystal }
            .forEach { (building, cost) ->
                if (!state.resources.covers(cost)) return@forEach
                when (val result = startUpgrade(state, building, at = now)) {
                    is StartUpgradeResult.Started -> state = result.state
                    StartUpgradeResult.AlreadyUpgrading,
                    StartUpgradeResult.InsufficientResources,
                    StartUpgradeResult.RequirementsNotMet,
                    -> Unit // already building this facility, or outbid by an earlier start
                }
            }
    }

    println("after 7 days (parallel builds):")
    println("  metal=${state.resources.metal} crystal=${state.resources.crystal} deuterium=${state.resources.deuterium}")
    println("  buildings=${state.buildings}")
    println("  events=${state.eventLog.size} (starts + completions)")
}
