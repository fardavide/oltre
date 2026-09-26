package dev.fardavide.oltre.sim

import dev.fardavide.oltre.core.AdaptationLevels
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class HarvestStrategyTest {
    @Test
    fun `easy farming comparison never buys adaptation while its comparison policy invests`() {
        val outcomes = harvestingStrategyComparison()
        val easy = outcomes.single { !it.withAdaptation }
        val investment = outcomes.single { it.withAdaptation }
        assertTrue(investment.adaptationSpendPriced > 0, "the comparison must exercise adaptation investment")
        assertEquals(AdaptationLevels.NONE, easy.adaptationLevels)
        assertEquals(0L, easy.adaptationSpendPriced)
    }
}
