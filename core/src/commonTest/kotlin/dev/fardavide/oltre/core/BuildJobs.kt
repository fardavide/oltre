package dev.fardavide.oltre.core

import kotlin.time.Instant

// Reading a single job out of the parallel build map, with a failure message that names the
// facility — every build test does this, and `builds[X]!!` says nothing when it breaks.
internal fun GameState.jobOf(building: BuildingType): BuildJob =
    checkNotNull(builds[building]) { "expected a job for $building, had ${builds.keys}" }

internal fun GameState.completionOf(building: BuildingType): Instant = jobOf(building).completesAt

internal fun GameState.fundedFor(vararg buildings: BuildingType): GameState {
    val total = buildings.fold(Resources.of()) { stock, building ->
        val cost = PlaceholderBalance.upgradeCost(building, BuildingLevel(this.buildings.levelOf(building).value + 1))
        Resources.of(
            metal = stock.metal + cost.metal,
            crystal = stock.crystal + cost.crystal,
            deuterium = stock.deuterium + cost.deuterium,
        )
    }
    return copy(resources = total)
}
