package dev.fardavide.oltre.client.galaxy.presentation

import dev.fardavide.oltre.client.design.text.English
import dev.fardavide.oltre.client.design.text.Strings
import dev.fardavide.oltre.client.dispatch.presentation.DispatchSelection
import dev.fardavide.oltre.client.dispatch.presentation.toDispatchUiState
import dev.fardavide.oltre.client.dispatch.ui.DispatchUiState
import dev.fardavide.oltre.core.GameState
import dev.fardavide.oltre.core.GalaxySeed
import dev.fardavide.oltre.core.ShipType
import dev.fardavide.oltre.core.Ships
import dev.fardavide.oltre.core.WorldVerdict
import dev.fardavide.oltre.core.verdictFor
import dev.fardavide.oltre.core.worldAt
import kotlin.test.Test
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant

class DispatchAdaptationTest {

    @Test
    fun `opening a blocked target directly refuses harvesting and names every missing adaptation`() {
        val state = GameState.initial(GalaxySeed(20_260_807)).copy(ships = Ships.of(ShipType.SKIFF, 1))
        val world = state.galaxy.surveyed.map { checkNotNull(worldAt(state.galaxy.seed, it)) }
            .first { verdictFor(it, state) is WorldVerdict.Blocked }
        val blocked = assertIs<WorldVerdict.Blocked>(verdictFor(world, state))

        val refusal = assertIs<DispatchUiState.Refuse>(
            state.toDispatchUiState(
                selection = DispatchSelection(world.at, null, null, null),
                probe = null,
                now = Instant.fromEpochMilliseconds(0),
            ),
        )

        blocked.failures.forEach { failure ->
            val requirement = Strings.namedLevel(Strings.adaptationName(failure.axis.adaptation), failure.closedAtLevel)
            assertTrue(English.resolve(refusal.note).contains(English.resolve(requirement)))
        }
        assertNull(refusal.action)
    }
}
