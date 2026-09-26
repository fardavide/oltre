package dev.fardavide.oltre.core

/** Satisfies only the target's adaptation requirements for tests of other dispatch behaviour. */
internal fun GameState.adaptedForHarvesting(target: GalaxyCoordinate): GameState {
    val world = checkNotNull(worldAt(galaxy.seed, target))
    val adapted = HostilityAxis.entries.fold(research) { levels, axis ->
        val required = GalaxyBalance.levelThatTolerates(axis, world.traits.axisValue(axis))
        levels.withLevel(axis.adaptation, TechLevel(maxOf(levels.levelOf(axis.adaptation).value, required)))
    }
    return copy(research = adapted)
}
