package dev.fardavide.oltre.client

import dev.fardavide.oltre.client.research.presentation.FinishedWhileAway
import dev.fardavide.oltre.core.AdaptationTechnology
import dev.fardavide.oltre.core.BuildingLevel
import dev.fardavide.oltre.core.BuildingType
import dev.fardavide.oltre.core.Event
import dev.fardavide.oltre.core.GalaxySeed
import dev.fardavide.oltre.core.GameState
import dev.fardavide.oltre.core.Resources
import dev.fardavide.oltre.core.SystemAddress
import dev.fardavide.oltre.core.TechLevel
import dev.fardavide.oltre.core.Technology
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.time.Duration.Companion.hours
import kotlin.time.Instant

// What one launch found, and nothing else. Every test here is about the difference between two
// states rather than about either of them.
class ArrivalTest {

    @Test
    fun `a first launch has nothing to announce`() {
        // given no save at all
        val resumed = freshState()

        // then there is no earlier reading to roll from and nothing finished while away
        assertNull(arrivalOf(saved = null, resumed = resumed))
    }

    @Test
    fun `the stocks the save was written with are what the rail rolls from`() {
        // given a colony closed holding 1_000 metal that has accrued to 1_400
        val saved = freshState().copy(resources = Resources.of(metal = 1_000))
        val resumed = saved.copy(resources = Resources.of(metal = 1_400))

        // when
        val arrival = checkNotNull(arrivalOf(saved = saved, resumed = resumed))

        // then
        assertEquals(1_000L, arrival.lastSeen.metal)
    }

    @Test
    fun `an upgrade that landed while the app was closed is what sweeps`() {
        // given
        val saved = freshState()
        val resumed = saved.copy(
            eventLog = listOf(
                Event.BuildCompleted(BuildingType.SOLAR_PLANT, BuildingLevel(9), at = EPOCH + 1.hours),
            ),
        )

        // when
        val arrival = checkNotNull(arrivalOf(saved = saved, resumed = resumed))

        // then
        assertEquals(AwayCompletion.Facility(BuildingType.SOLAR_PLANT), arrival.finished)
    }

    @Test
    fun `when several landed only the most recent sweeps`() {
        // given three completions in the order the log recorded them
        val saved = freshState()
        val resumed = saved.copy(
            eventLog = listOf(
                Event.BuildCompleted(BuildingType.METAL_MINE, BuildingLevel(4), at = EPOCH + 1.hours),
                Event.ResearchCompleted(Technology.EXTRACTION, TechLevel(2), at = EPOCH + 2.hours),
                Event.BuildCompleted(BuildingType.SOLAR_PLANT, BuildingLevel(9), at = EPOCH + 3.hours),
            ),
        )

        // when
        val arrival = checkNotNull(arrivalOf(saved = saved, resumed = resumed))

        // then
        assertEquals(AwayCompletion.Facility(BuildingType.SOLAR_PLANT), arrival.finished)
    }

    @Test
    fun `a completion the player already saw is not announced again`() {
        // given a save that was written *after* the build finished - the log entry is in both states
        val landed = Event.BuildCompleted(BuildingType.SOLAR_PLANT, BuildingLevel(9), at = EPOCH + 1.hours)
        val saved = freshState().copy(eventLog = listOf(landed))
        val resumed = saved

        // when
        val arrival = checkNotNull(arrivalOf(saved = saved, resumed = resumed))

        // then
        assertNull(arrival.finished)
    }

    @Test
    fun `a research project that landed while away sweeps on the research branch`() {
        // given
        val saved = freshState()
        val resumed = saved.copy(
            eventLog = listOf(Event.ResearchCompleted(Technology.ENRICHMENT, TechLevel(3), at = EPOCH + 1.hours)),
        )

        // then
        val arrival = checkNotNull(arrivalOf(saved = saved, resumed = resumed))
        assertEquals(AwayCompletion.Project(Technology.ENRICHMENT), arrival.finished)
    }

    @Test
    fun `an adaptation ladder that landed while away sweeps on its own branch`() {
        // given
        val saved = freshState()
        val resumed = saved.copy(
            eventLog = listOf(
                Event.AdaptationCompleted(AdaptationTechnology.GRAVITIC, TechLevel(2), at = EPOCH + 1.hours),
            ),
        )

        // then
        val arrival = checkNotNull(arrivalOf(saved = saved, resumed = resumed))
        assertEquals(AwayCompletion.Ladder(AdaptationTechnology.GRAVITIC), arrival.finished)
    }

    @Test
    fun `a probe landing is not a row and sweeps nothing`() {
        // given a survey that completed while away - its receipt is the probe card rather than a row
        val saved = freshState()
        val resumed = saved.copy(
            eventLog = listOf(
                Event.SurveyCompleted(SystemAddress(galaxy = 2, system = 118), worldsFound = 5, at = EPOCH + 1.hours),
            ),
        )

        // then
        val arrival = checkNotNull(arrivalOf(saved = saved, resumed = resumed))
        assertNull(arrival.finished)
    }

    @Test
    fun `a project that finished while away reaches the research screen as its own branch`() {
        // then each kind lands on the branch that draws it and on no other
        assertEquals(
            FinishedWhileAway.Project(Technology.EXTRACTION),
            AwayCompletion.Project(Technology.EXTRACTION).toResearchArrival(),
        )
        assertEquals(
            FinishedWhileAway.Ladder(AdaptationTechnology.THERMAL),
            AwayCompletion.Ladder(AdaptationTechnology.THERMAL).toResearchArrival(),
        )
        assertNull(AwayCompletion.Facility(BuildingType.METAL_MINE).toResearchArrival())
    }

    // `GameState.initial` takes a galaxy seed rather than defaulting one, so production cannot found
    // every colony in the same galaxy.
    private fun freshState(): GameState = GameState.initial(GalaxySeed(20_260_807))

    private companion object {
        val EPOCH = Instant.fromEpochMilliseconds(0)
    }
}
