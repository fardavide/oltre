package dev.fardavide.oltre.client

import dev.fardavide.oltre.core.GalaxyBalance
import dev.fardavide.oltre.core.GalaxyCoordinate
import dev.fardavide.oltre.core.GameState
import dev.fardavide.oltre.core.HostilityAxis
import dev.fardavide.oltre.core.TechLevel
import dev.fardavide.oltre.core.axisValue
import dev.fardavide.oltre.core.worldAt

/** Satisfies target adaptation for app tests of dispatch, alerts and connectivity. */
internal fun GameState.adaptedForHarvesting(target: GalaxyCoordinate): GameState {
    val world = checkNotNull(worldAt(galaxy.seed, target))
    val adapted = HostilityAxis.entries.fold(research) { levels, axis ->
        val required = GalaxyBalance.levelThatTolerates(axis, world.traits.axisValue(axis))
        levels.withLevel(axis.adaptation, TechLevel(maxOf(levels.levelOf(axis.adaptation).value, required)))
    }
    return copy(research = adapted)
}
