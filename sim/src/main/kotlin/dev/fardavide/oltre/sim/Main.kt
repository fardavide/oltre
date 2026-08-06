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

// Headless balancing harness. Never ships. Prints the curve table that `.claude/docs/balance-log.md`
// carries, then fast-forwards a week of a greedy strategy that, once per simulated hour, starts
// every mine-or-plant upgrade it can afford — cheapest first, since builds now run in parallel
// and each start eats into the same stock.
fun main() {
    printCurveTable()
    printGreedyWeek()
}

// The balance log is only useful if its numbers can be regenerated rather than retyped. Markdown
// rows, so a round of tuning is a copy-paste rather than an arithmetic exercise.
private fun printCurveTable() {
    println("## Current curves")
    println()
    println("| Level | metal/h | crystal/h | deut/h | metal mine cost (m/c) | payback of the next level |")
    println("|---|---|---|---|---|---|")
    for (level in listOf(1, 2, 3, 5, 8, 10, 12, 15, 18, 20)) {
        val here = BuildingLevel(level)
        val next = BuildingLevel(level + 1)
        val cost = PlaceholderBalance.upgradeCost(BuildingType.METAL_MINE, here)
        val gain = PlaceholderBalance.metalProductionPerHour(next) -
            PlaceholderBalance.metalProductionPerHour(here)
        val payback = PlaceholderBalance.upgradeCost(BuildingType.METAL_MINE, next).metal / gain
        println(
            "| $level | ${PlaceholderBalance.metalProductionPerHour(here).grouped()} " +
                "| ${PlaceholderBalance.crystalProductionPerHour(here).grouped()} " +
                "| ${PlaceholderBalance.deuteriumProductionPerHour(here).grouped()} " +
                "| ${cost.metal.grouped()} / ${cost.crystal.grouped()} | ${payback}h |",
        )
    }
    val daily = listOf(1, 5, 10, 15)
        .joinToString(", ") { (PlaceholderBalance.metalProductionPerHour(BuildingLevel(it)) * 24).grouped() }
    println()
    println("Daily metal at levels 1, 5, 10, 15: $daily.")
    val starting = GameState.initial().resources
    println("Starting stock: ${starting.metal.grouped()} metal, ${starting.crystal.grouped()} crystal.")
    println()
}

private fun Long.grouped(): String = toString().reversed().chunked(3).joinToString(",").reversed()

private fun printGreedyWeek() {
    val start = Instant.fromEpochMilliseconds(0)
    var state = GameState.initial()
    var now = start
    // A power shortage scales every mine silently, so a week that looks slow can be a week spent
    // throttled rather than a curve that is too flat. Counting it is what tells the two apart.
    var throttledHours = 0

    repeat(7 * 24) {
        val next = now + 1.hours
        state = advance(state, from = now, to = next)
        now = next
        if (PlaceholderBalance.energyBalance(state.buildings).isDeficit) throttledHours++

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

    val energy = PlaceholderBalance.energyBalance(state.buildings)
    println("after 7 days (parallel builds):")
    println("  metal=${state.resources.metal} crystal=${state.resources.crystal} deuterium=${state.resources.deuterium}")
    println("  buildings=${state.buildings}")
    println("  energy=${energy.produced}/${energy.consumed} (mines at ${energy.outputPercent}%)")
    println("  hours throttled by power: $throttledHours of ${7 * 24}")
    println("  events=${state.eventLog.size} (starts + completions)")
}
