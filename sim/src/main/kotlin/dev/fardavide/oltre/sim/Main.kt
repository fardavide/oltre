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
// once per simulated hour, starts the mine-or-plant upgrade with the lowest combined
// metal+crystal cost among those it can afford right now.
fun main() {
    val start = Instant.fromEpochMilliseconds(0)
    var state = GameState.initial()
    var now = start

    repeat(7 * 24) {
        val next = now + 1.hours
        state = advance(state, from = now, to = next)
        now = next

        if (state.buildQueue == null) {
            val candidates = listOf(BuildingType.METAL_MINE, BuildingType.CRYSTAL_MINE, BuildingType.SOLAR_PLANT)
            val affordable = candidates
                .map { it to PlaceholderBalance.upgradeCost(it, BuildingLevel(state.buildings.levelOf(it).value + 1)) }
                .filter { (_, cost) -> state.resources.covers(cost) }
                .minByOrNull { (_, cost) -> cost.metal + cost.crystal }
            if (affordable != null) {
                when (val result = startUpgrade(state, affordable.first, at = now)) {
                    is StartUpgradeResult.Started -> state = result.state
                    StartUpgradeResult.QueueBusy,
                    StartUpgradeResult.InsufficientResources,
                    StartUpgradeResult.RequirementsNotMet,
                    -> Unit // race between the affordability check and start; skip this hour
                }
            }
        }
    }

    println("after 7 days:")
    println("  metal=${state.resources.metal} crystal=${state.resources.crystal} deuterium=${state.resources.deuterium}")
    println("  buildings=${state.buildings}")
    println("  events=${state.eventLog.size} (starts + completions)")
}
