package dev.fardavide.oltre.sim

import dev.fardavide.oltre.core.BuildingType
import dev.fardavide.oltre.core.GameState
import dev.fardavide.oltre.core.PlaceholderBalance
import dev.fardavide.oltre.core.StartUpgradeResult
import dev.fardavide.oltre.core.advance
import dev.fardavide.oltre.core.startUpgrade
import kotlin.time.Duration.Companion.hours
import kotlin.time.Instant

// Headless balancing harness. Never ships. Fast-forwards a week of a greedy strategy that
// always upgrades the cheapest available mine, hour by hour.
fun main() {
    val start = Instant.fromEpochMilliseconds(0)
    var state = GameState.initial()
    var now = start

    repeat(7 * 24) {
        val next = now + 1.hours
        state = advance(state, from = now, to = next)
        now = next

        if (state.buildQueue == null) {
            val cheapest = listOf(BuildingType.METAL_MINE, BuildingType.CRYSTAL_MINE, BuildingType.SOLAR_PLANT)
                .minBy { PlaceholderBalance.upgradeCost(it, nextLevelOf(state, it)).metal }
            when (val result = startUpgrade(state, cheapest, at = now)) {
                is StartUpgradeResult.Started -> state = result.state
                else -> Unit // save up and retry next hour
            }
        }
    }

    println("after 7 days:")
    println("  metal=${state.resources.metal} crystal=${state.resources.crystal} deuterium=${state.resources.deuterium}")
    println("  buildings=${state.buildings}")
    println("  events=${state.eventLog.size} completed builds")
}

private fun nextLevelOf(state: GameState, building: BuildingType) = when (building) {
    BuildingType.METAL_MINE -> state.buildings.metalMine
    BuildingType.CRYSTAL_MINE -> state.buildings.crystalMine
    BuildingType.DEUTERIUM_SYNTHESIZER -> state.buildings.deuteriumSynthesizer
    BuildingType.SOLAR_PLANT -> state.buildings.solarPlant
    BuildingType.ROBOTICS_FACTORY -> state.buildings.roboticsFactory
    BuildingType.NANITE_FACTORY -> state.buildings.naniteFactory
}.let { dev.fardavide.oltre.core.BuildingLevel(it.value + 1) }
