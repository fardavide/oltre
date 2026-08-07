package dev.fardavide.oltre.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant

class StartAdaptationTest {

    @Test
    fun `starting a ladder fills the slot and charges the stock`() {
        // given
        val ready = GameState.initial().readyToAdapt(AdaptationTechnology.GRAVITIC)

        // when
        val started = assertIs<StartAdaptationResult.Started>(
            startAdaptation(ready, AdaptationTechnology.GRAVITIC, at = EPOCH),
        ).state

        // then
        assertEquals(AdaptationTechnology.GRAVITIC, started.ladder().technology)
        assertEquals(TechLevel(1), started.ladder().toLevel)
        assertEquals(0L, started.resources.metal)
        assertEquals(0L, started.resources.crystal)
        assertEquals(0L, started.resources.deuterium)
    }

    @Test
    fun `starting a ladder is recorded in the event log`() {
        val started = GameState.initial().adapting(AdaptationTechnology.THERMAL, at = EPOCH)

        assertEquals(
            listOf(
                Event.AdaptationStarted(
                    technology = AdaptationTechnology.THERMAL,
                    toLevel = TechLevel(1),
                    at = EPOCH,
                ),
            ),
            started.eventLog,
        )
    }

    @Test
    fun `the project ends at the duration the balance prices`() {
        // given a colony whose Robotics Factory is level 4 — the gate, and the divisor
        val started = GameState.initial().adapting(AdaptationTechnology.THERMAL, at = EPOCH)

        assertEquals(
            EPOCH + AdaptationBalance.adaptationDuration(AdaptationTechnology.THERMAL, TechLevel(1), BuildingLevel(4)),
            started.ladder().completesAt,
        )
    }

    @Test
    fun `climbing costs a production level — a running technology refuses a ladder`() {
        // The single slot is shared with the applied branch, and that sharing *is* the mechanic:
        // every adaptation level is paid for in production levels the player did not buy. Give the
        // branch its own slot and the answer is always "run both".
        val busy = GameState.initial().researching(Technology.EXTRACTION, at = EPOCH)
        val funded = busy.readyToAdapt(AdaptationTechnology.GRAVITIC)

        assertEquals(
            StartAdaptationResult.SlotBusy,
            startAdaptation(funded, AdaptationTechnology.GRAVITIC, at = EPOCH),
        )
    }

    @Test
    fun `and the other way round — a running ladder refuses a technology`() {
        val busy = GameState.initial().adapting(AdaptationTechnology.GRAVITIC, at = EPOCH)
        val funded = busy.readyToResearch(Technology.EXTRACTION)

        assertEquals(StartResearchResult.SlotBusy, startResearch(funded, Technology.EXTRACTION, at = EPOCH))
    }

    @Test
    fun `a busy slot refuses even the ladder that is already climbing`() {
        val busy = GameState.initial().adapting(AdaptationTechnology.GRAVITIC, at = EPOCH)
        val funded = busy.readyToAdapt(AdaptationTechnology.GRAVITIC)

        assertEquals(
            StartAdaptationResult.SlotBusy,
            startAdaptation(funded, AdaptationTechnology.GRAVITIC, at = EPOCH),
        )
    }

    @Test
    fun `the gate refuses a colony whose Robotics Factory is one level short`() {
        val cost = AdaptationBalance.adaptationCost(AdaptationTechnology.ATMOSPHERIC, TechLevel(1))
        val short = GameState.initial().copy(
            buildings = Buildings.initial()
                .withLevel(BuildingType.ROBOTICS_FACTORY, BuildingLevel(AdaptationBalance.GATE.value - 1)),
            resources = Resources.of(metal = cost.metal, crystal = cost.crystal, deuterium = cost.deuterium),
        )

        assertEquals(
            StartAdaptationResult.RequirementsNotMet,
            startAdaptation(short, AdaptationTechnology.ATMOSPHERIC, at = EPOCH),
        )
    }

    @Test
    fun `a colony one unit short of the price cannot start`() {
        val ready = GameState.initial().readyToAdapt(AdaptationTechnology.THERMAL)
        val poorer = ready.copy(
            resources = Resources.of(
                metal = ready.resources.metal - 1,
                crystal = ready.resources.crystal,
                deuterium = ready.resources.deuterium,
            ),
        )

        assertEquals(
            StartAdaptationResult.InsufficientResources,
            startAdaptation(poorer, AdaptationTechnology.THERMAL, at = EPOCH),
        )
    }

    @Test
    fun `a refused start changes nothing at all`() {
        val ready = GameState.initial().readyToAdapt(AdaptationTechnology.THERMAL)
        val poorer = ready.copy(resources = Resources.of())

        val result = startAdaptation(poorer, AdaptationTechnology.THERMAL, at = EPOCH)

        assertIs<StartAdaptationResult.InsufficientResources>(result)
        assertNull(poorer.activeAdaptation)
        assertTrue(poorer.eventLog.isEmpty())
    }

    @Test
    fun `the next level is priced from the level already held, not from one`() {
        val climbed = GameState.initial().climbed(AdaptationTechnology.GRAVITIC, to = 3)
        val ready = climbed.readyToAdapt(AdaptationTechnology.GRAVITIC)

        val started = assertIs<StartAdaptationResult.Started>(
            startAdaptation(ready, AdaptationTechnology.GRAVITIC, at = EPOCH),
        ).state

        assertEquals(TechLevel(4), started.ladder().toLevel)
        assertEquals(
            AdaptationBalance.adaptationCost(AdaptationTechnology.GRAVITIC, TechLevel(4)),
            Resources.of(
                metal = ready.resources.metal,
                crystal = ready.resources.crystal,
                deuterium = ready.resources.deuterium,
            ),
        )
    }

    private companion object {
        val EPOCH = Instant.fromEpochMilliseconds(0)
    }
}
