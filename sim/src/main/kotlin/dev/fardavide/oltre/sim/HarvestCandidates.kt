package dev.fardavide.oltre.sim

import dev.fardavide.oltre.core.GameState
import dev.fardavide.oltre.core.World
import dev.fardavide.oltre.core.WorldVerdict
import dev.fardavide.oltre.core.verdictFor
import dev.fardavide.oltre.core.worldAt

internal fun harvestCandidates(state: GameState): List<World> = state.galaxy.surveyed
    .sortedWith(compareBy({ it.galaxy }, { it.system }, { it.slot }))
    .filter { it != state.galaxy.home && state.galaxy.holderOf(it) == null }
    .mapNotNull { worldAt(state.galaxy.seed, it) }
    .filter { verdictFor(it, state) !is WorldVerdict.Blocked }
