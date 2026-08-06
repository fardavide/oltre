package dev.fardavide.oltre.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.time.Instant

class StartResearchTest {

    @Test
    fun `starting a project fills the slot and charges the stock`() {
        // given
        val ready = GameState.initial().readyToResearch(Technology.EXTRACTION)
        val cost = ResearchBalance.researchCost(Technology.EXTRACTION, TechLevel(1))

        // when
        val started = assertIs<StartResearchResult.Started>(
            startResearch(ready, Technology.EXTRACTION, at = EPOCH),
        ).state

        // then
        assertEquals(Technology.EXTRACTION, started.project().technology)
        assertEquals(TechLevel(1), started.project().toLevel)
        assertEquals(0L, started.resources.metal)
        assertEquals(0L, started.resources.crystal)
        assertEquals(0L, started.resources.deuterium)
        assertTrue(ready.resources.covers(cost), "the fixture must actually have been able to pay")
    }

    @Test
    fun `starting a project is recorded in the event log`() {
        // when
        val started = GameState.initial().researching(Technology.EXTRACTION, at = EPOCH)

        // then
        assertEquals(
            listOf(Event.ResearchStarted(technology = Technology.EXTRACTION, toLevel = TechLevel(1), at = EPOCH)),
            started.eventLog,
        )
    }

    @Test
    fun `the project ends at the duration the balance prices`() {
        // given a colony whose Robotics Factory is level 1 - the gate, and the divisor
        val started = GameState.initial().researching(Technology.EXTRACTION, at = EPOCH)

        // then
        assertEquals(
            EPOCH + ResearchBalance.researchDuration(Technology.EXTRACTION, TechLevel(1), BuildingLevel(1)),
            started.project().completesAt,
        )
    }

    @Test
    fun `the slot is empire-wide so a busy slot refuses a different technology`() {
        // given a colony already researching Extraction and able to pay for Photovoltaics
        val busy = GameState.initial().researching(Technology.EXTRACTION, at = EPOCH)
        val funded = busy.readyToResearch(Technology.PHOTOVOLTAICS)

        // when
        val result = startResearch(funded, Technology.PHOTOVOLTAICS, at = EPOCH)

        // then - this is research's only scarcity, and the whole reason the branch is a decision
        assertEquals(StartResearchResult.SlotBusy, result)
    }

    @Test
    fun `a busy slot refuses the technology already running`() {
        // given
        val busy = GameState.initial().researching(Technology.EXTRACTION, at = EPOCH)
        val funded = busy.copy(resources = Resources.of(metal = 100_000, crystal = 100_000, deuterium = 100_000))

        // when / then
        assertEquals(StartResearchResult.SlotBusy, startResearch(funded, Technology.EXTRACTION, at = EPOCH))
    }

    @Test
    fun `research is refused before the Robotics Factory exists`() {
        // given a colony with the money and no Robotics Factory
        val rich = GameState.initial().copy(
            resources = Resources.of(metal = 100_000, crystal = 100_000, deuterium = 100_000),
        )
        assertEquals(BuildingLevel(0), rich.buildings.roboticsFactory)

        // when / then - the branch opens behind the deuterium wall the game already has
        assertEquals(StartResearchResult.RequirementsNotMet, startResearch(rich, Technology.EXTRACTION, at = EPOCH))
        assertEquals(StartResearchResult.RequirementsNotMet, startResearch(rich, Technology.PHOTOVOLTAICS, at = EPOCH))
    }

    @Test
    fun `Enrichment is refused until Extraction has reached its third level`() {
        // given a colony past the first gate with the money, and Extraction one level short
        val funded = GameState.initial().copy(
            buildings = GameState.initial().buildings.copy(roboticsFactory = BuildingLevel(1)),
            research = Research.initial().withLevel(Technology.EXTRACTION, TechLevel(2)),
            resources = Resources.of(metal = 100_000, crystal = 100_000, deuterium = 100_000),
        )

        // when / then
        assertEquals(StartResearchResult.RequirementsNotMet, startResearch(funded, Technology.ENRICHMENT, at = EPOCH))
        assertIs<StartResearchResult.Started>(
            startResearch(
                funded.copy(research = funded.research.withLevel(Technology.EXTRACTION, TechLevel(3))),
                Technology.ENRICHMENT,
                at = EPOCH,
            ),
        )
    }

    @Test
    fun `research is refused when the stock is one unit short`() {
        // given
        val ready = GameState.initial().readyToResearch(Technology.EXTRACTION)
        val short = ready.copy(
            resources = Resources.of(
                metal = ready.resources.metal,
                crystal = ready.resources.crystal,
                deuterium = ready.resources.deuterium - 1,
            ),
        )

        // when / then - deuterium is the price, so it is the one that binds
        assertEquals(StartResearchResult.InsufficientResources, startResearch(short, Technology.EXTRACTION, at = EPOCH))
    }

    @Test
    fun `a refused start changes nothing at all`() {
        // given
        val rich = GameState.initial().copy(
            resources = Resources.of(metal = 100_000, crystal = 100_000, deuterium = 100_000),
        )

        // when
        val result = startResearch(rich, Technology.EXTRACTION, at = EPOCH)

        // then - a result carries a new state or it carries nothing; there is no half-applied one
        assertEquals(StartResearchResult.RequirementsNotMet, result)
        assertEquals(emptyList(), rich.eventLog)
        assertEquals(null, rich.activeResearch)
    }

    @Test
    fun `the next project costs the level after the one already reached`() {
        // given a colony that has already researched Extraction twice
        val done = GameState.initial().copy(research = Research.initial().withLevel(Technology.EXTRACTION, TechLevel(2)))

        // when
        val started = done.researching(Technology.EXTRACTION, at = EPOCH)

        // then
        assertEquals(TechLevel(3), started.project().toLevel)
    }

    @Test
    fun `starting research leaves the parallel build slots alone`() {
        // given a colony building a facility and able to research
        val building = GameState.initial()
            .fundedFor(BuildingType.METAL_MINE)
            .let { assertIs<StartUpgradeResult.Started>(startUpgrade(it, BuildingType.METAL_MINE, at = EPOCH)).state }

        // when
        val started = building.researching(Technology.EXTRACTION, at = EPOCH)

        // then - the two limiters are deliberately independent
        assertEquals(setOf(BuildingType.METAL_MINE), started.builds.keys)
        assertEquals(Technology.EXTRACTION, started.project().technology)
    }

    private companion object {
        val EPOCH = Instant.fromEpochMilliseconds(0)
    }
}
