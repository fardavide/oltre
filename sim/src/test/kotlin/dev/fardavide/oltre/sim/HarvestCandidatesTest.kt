package dev.fardavide.oltre.sim

import dev.fardavide.oltre.core.GalaxyBalance
import dev.fardavide.oltre.core.GalaxyCoordinate
import dev.fardavide.oltre.core.GalaxySeed
import dev.fardavide.oltre.core.GameState
import dev.fardavide.oltre.core.WorldVerdict
import dev.fardavide.oltre.core.verdictFor
import dev.fardavide.oltre.core.worldAt
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class HarvestCandidatesTest {
    @Test
    fun `bots never choose a planet whose adaptation requirements are unmet`() {
        val initial = GameState.initial(GalaxySeed(20_260_807))
        val surveyed = (1..GalaxyBalance.SYSTEMS_PER_GALAXY).flatMap { system ->
            (1..GalaxyBalance.SLOTS_PER_SYSTEM).mapNotNull { slot ->
                worldAt(initial.galaxy.seed, GalaxyCoordinate(initial.galaxy.home.galaxy, system, slot))?.at
            }
        }
        val state = initial.copy(galaxy = initial.galaxy.copy(surveyed = initial.galaxy.surveyed + surveyed))
        assertTrue(surveyed.mapNotNull { worldAt(state.galaxy.seed, it) }
            .any { verdictFor(it, state) is WorldVerdict.Blocked })
        val candidates = harvestCandidates(state)
        assertTrue(candidates.isNotEmpty())
        assertFalse(candidates.any { verdictFor(it, state) is WorldVerdict.Blocked })
    }
}
