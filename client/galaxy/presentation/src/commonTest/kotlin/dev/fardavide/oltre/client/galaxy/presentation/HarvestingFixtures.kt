package dev.fardavide.oltre.client.galaxy.presentation

import dev.fardavide.oltre.core.GalaxyBalance
import dev.fardavide.oltre.core.GalaxyCoordinate
import dev.fardavide.oltre.core.GameState
import dev.fardavide.oltre.core.HostilityAxis
import dev.fardavide.oltre.core.TechLevel
import dev.fardavide.oltre.core.axisValue
import dev.fardavide.oltre.core.worldAt

internal fun GameState.adaptedTo(target: GalaxyCoordinate): GameState {
    val world = checkNotNull(worldAt(galaxy.seed, target))
    return copy(research = HostilityAxis.entries.fold(research) { learned, axis ->
        learned.withLevel(axis.adaptation, TechLevel(maxOf(
            learned.levelOf(axis.adaptation).value,
            GalaxyBalance.levelThatTolerates(axis, world.traits.axisValue(axis)),
        )))
    })
}
