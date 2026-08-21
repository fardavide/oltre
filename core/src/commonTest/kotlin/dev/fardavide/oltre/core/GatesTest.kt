package dev.fardavide.oltre.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class GatesTest {

    @Test
    fun `the robotics factory opens every gate the game has behind a building`() {
        // then in level order — the applied branch first, then the ladders, then the factory
        assertEquals(
            listOf(
                Gate(level = 1, opens = GateSubject.Project(Technology.PHOTOVOLTAICS)),
                Gate(level = 1, opens = GateSubject.Project(Technology.EXTRACTION)),
                Gate(level = 2, opens = GateSubject.Ladder(AdaptationTechnology.THERMAL)),
                Gate(level = 2, opens = GateSubject.Ladder(AdaptationTechnology.GRAVITIC)),
                Gate(level = 2, opens = GateSubject.Ladder(AdaptationTechnology.ATMOSPHERIC)),
                Gate(level = 10, opens = GateSubject.Facility(BuildingType.NANITE_FACTORY)),
            ),
            gatesOf(BuildingType.ROBOTICS_FACTORY),
        )
    }

    @Test
    fun `every other building opens nothing at all`() {
        // then five of the six rows on the colony screen gate nothing — which is what makes the
        // sixth worth stating
        for (building in BuildingType.entries - BuildingType.ROBOTICS_FACTORY) {
            assertEquals(emptyList(), gatesOf(building), "$building")
        }
    }

    @Test
    fun `extraction opens three rows and they are listed in the order they unlock`() {
        // Extraction became a fork at 0.9: level 1 opens the away half of the same idea, level 3 the
        // deuterium one. The order is the level order rather than the enum's, which is what makes the
        // row readable as a ladder.
        //
        // **Level 1 opens two rows since 0.15**, and they are the pair the fleet is made of — what a
        // hull pulls out of a world, and how far it can go to find one. They share a gate on purpose:
        // a chain would have put three research levels between a player and the row that answers
        // *"navigating distance takes way more time"*.
        assertEquals(
            listOf(
                Gate(level = 1, opens = GateSubject.Project(Technology.PROSPECTING)),
                Gate(level = 1, opens = GateSubject.Project(Technology.PROPULSION)),
                Gate(level = 3, opens = GateSubject.Project(Technology.ENRICHMENT)),
            ),
            gatesOf(Technology.EXTRACTION),
        )
    }

    @Test
    fun `a technology nothing waits on opens nothing`() {
        assertEquals(emptyList(), gatesOf(Technology.PHOTOVOLTAICS))
        assertEquals(emptyList(), gatesOf(Technology.ENRICHMENT))
        assertEquals(emptyList(), gatesOf(Technology.PROSPECTING))
    }

    // The point of deriving the index rather than writing it out: a gate that moves in the balance
    // moves here with it. This is the assertion that would fail if someone re-typed a level.
    @Test
    fun `every requirement in the game is stated as a gate on the thing that satisfies it`() {
        val stated = BuildingType.entries.flatMap { building ->
            gatesOf(building).map { it.opens to it.level }
        } + Technology.entries.flatMap { technology ->
            gatesOf(technology).map { it.opens to it.level }
        }

        for (technology in Technology.entries) {
            val required = ResearchBalance.requirementFor(technology)
            val level = when (required) {
                is ResearchRequirement.Facility -> required.level.value
                is ResearchRequirement.Tech -> required.level.value
            }
            assertTrue(
                GateSubject.Project(technology) to level in stated,
                "$technology is required at $level and no row says so",
            )
        }
        for (technology in AdaptationTechnology.entries) {
            val required = AdaptationBalance.requirementFor(technology) as ResearchRequirement.Facility
            assertTrue(
                GateSubject.Ladder(technology) to required.level.value in stated,
                "$technology is required at ${required.level} and no row says so",
            )
        }
        assertTrue(
            GateSubject.Facility(BuildingType.NANITE_FACTORY) to
                PlaceholderBalance.NANITE_ROBOTICS_REQUIREMENT in stated,
        )
    }

    @Test
    fun `gates arrive in the order the player reaches them`() {
        for (building in BuildingType.entries) {
            assertEquals(gatesOf(building).sortedBy { it.level }, gatesOf(building), "$building")
        }
    }
}
