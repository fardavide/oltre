package dev.fardavide.oltre.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

// Research reaches the simulation the same way construction does: as an event applied by `advance`
// at a computed instant, never as a mutation and never as a number the UI reads on the side. That
// is what keeps offline progression, the sim harness and the screen telling the same story.
class AdvanceResearchTest {

    @Test
    fun `a project completes at its instant and raises the level`() {
        // given
        val started = GameState.initial().researching(Technology.EXTRACTION, at = EPOCH)
        val completesAt = started.project().completesAt

        // when
        val before = advance(started, from = EPOCH, to = completesAt - 1.minutes)
        val after = advance(started, from = EPOCH, to = completesAt)

        // then
        assertEquals(TechLevel(0), before.research.extraction)
        assertEquals(TechLevel(1), after.research.extraction)
    }

    @Test
    fun `a completed project frees the slot and is appended to the log`() {
        // given
        val started = GameState.initial().researching(Technology.EXTRACTION, at = EPOCH)
        val completesAt = started.project().completesAt

        // when
        val after = advance(started, from = EPOCH, to = completesAt + 1.minutes)

        // then
        assertNull(after.activeResearch)
        assertEquals(
            Event.ResearchCompleted(technology = Technology.EXTRACTION, newLevel = TechLevel(1), at = completesAt),
            after.eventLog.last(),
        )
    }

    @Test
    fun `research finished while the app was closed is applied on the way back in`() {
        // given a colony left running for a week with a project of a few hours in the slot
        val started = GameState.initial().researching(Technology.EXTRACTION, at = EPOCH)

        // when
        val reopened = advance(started, from = EPOCH, to = EPOCH + 7.days)

        // then
        assertEquals(TechLevel(1), reopened.research.extraction)
        assertTrue(reopened.eventLog.any { it is Event.ResearchCompleted })
    }

    @Test
    fun `Extraction raises what the mines actually produce`() {
        // given the same colony with and without two levels of Extraction
        val plain = GameState.initial()
        val researched = plain.copy(research = plain.research.withLevel(Technology.EXTRACTION, TechLevel(2)))

        // when
        val plainMetal = advance(plain, from = EPOCH, to = EPOCH + 1.hours).resources.metal - plain.resources.metal
        val researchedMetal = advance(researched, from = EPOCH, to = EPOCH + 1.hours).resources.metal -
            researched.resources.metal
        val researchedCrystal = advance(researched, from = EPOCH, to = EPOCH + 1.hours).resources.crystal -
            researched.resources.crystal

        // then - 60/h and 30/h at level 1, both times 1.08^2
        assertEquals(60L, plainMetal)
        assertEquals(69L, researchedMetal)
        assertEquals(34L, researchedCrystal)
    }

    @Test
    fun `Enrichment raises what the synthesizer produces and leaves the mines alone`() {
        // given
        val plain = GameState.initial()
        val researched = plain.copy(research = plain.research.withLevel(Technology.ENRICHMENT, TechLevel(2)))

        // when
        val after = advance(researched, from = EPOCH, to = EPOCH + 1.hours)

        // then - 15/h at level 1 times 1.14^2, and metal untouched
        assertEquals(19L, after.resources.deuterium - researched.resources.deuterium)
        assertEquals(60L, after.resources.metal - researched.resources.metal)
    }

    @Test
    fun `Photovoltaics buys energy headroom rather than output`() {
        // given a colony whose mines have outrun their plant - 50 produced against 120 consumed
        val starved = GameState.initial().copy(
            buildings = GameState.initial().buildings.copy(metalMine = BuildingLevel(9)),
        )
        val helped = starved.copy(research = starved.research.withLevel(Technology.PHOTOVOLTAICS, TechLevel(5)))
        assertTrue(
            PlaceholderBalance.energyProduction(helped.buildings, helped.research) >
                PlaceholderBalance.energyProduction(starved.buildings, starved.research),
            "Photovoltaics must raise supply",
        )

        // when
        val starvedMetal = advance(starved, from = EPOCH, to = EPOCH + 1.hours).resources.metal - starved.resources.metal
        val helpedMetal = advance(helped, from = EPOCH, to = EPOCH + 1.hours).resources.metal - helped.resources.metal

        // then - the deficit ratio moves from 50/120 to 80/120 against a 352/h mine
        assertEquals(146L, starvedMetal)
        assertEquals(234L, helpedMetal)
    }

    @Test
    fun `a deficit scales the research bonus exactly as it scales the mine`() {
        // The order of application is the rule: building curve, then research, then the deficit.
        // Applying the deficit first and the multiplier after would produce 294 here, not 295 -
        // which is what makes this a test of the order rather than of the arithmetic.
        val state = GameState.initial().copy(
            buildings = GameState.initial().buildings.copy(metalMine = BuildingLevel(9)),
            research = Research.initial()
                .withLevel(Technology.PHOTOVOLTAICS, TechLevel(5))
                .withLevel(Technology.EXTRACTION, TechLevel(3)),
        )

        // when
        val produced = advance(state, from = EPOCH, to = EPOCH + 1.hours).resources.metal - state.resources.metal

        // then
        assertEquals(295L, produced)
    }

    @Test
    fun `the level a project reaches changes production from its completion instant onwards`() {
        // given
        val started = GameState.initial().researching(Technology.EXTRACTION, at = EPOCH)
        val completesAt = started.project().completesAt

        // when an hour is accrued entirely before, and entirely after, the completion
        val beforeStart = advance(started, from = EPOCH, to = EPOCH + 1.hours)
        val beforeMetal = beforeStart.resources.metal - started.resources.metal
        val afterStart = advance(started, from = EPOCH, to = completesAt)
        val afterMetal = advance(afterStart, from = completesAt, to = completesAt + 1.hours).resources.metal -
            afterStart.resources.metal

        // then - the project has to finish before it produces anything
        assertTrue(completesAt > EPOCH + 1.hours, "the fixture must not complete inside the first hour")
        assertEquals(60L, beforeMetal)
        assertEquals(64L, afterMetal)
    }

    @Test
    fun `advancing in one span equals advancing through any instant while research is in flight`() {
        // given
        val started = GameState.initial().researching(Technology.EXTRACTION, at = EPOCH)
        val completesAt = started.project().completesAt
        val t2 = EPOCH + 7.days
        val oneShot = advance(started, from = EPOCH, to = t2)

        // when the span is split on both sides of the completion, and exactly on it
        val splits = listOf(
            EPOCH + 1.minutes,
            completesAt - 1.minutes,
            completesAt,
            completesAt + 1.minutes,
            t2 - 1.minutes,
        )

        // then
        for (t1 in splits) {
            assertEquals(
                oneShot,
                advance(advance(started, from = EPOCH, to = t1), from = t1, to = t2),
                "split at $t1 diverged",
            )
        }
    }

    @Test
    fun `a build and a research completing together are applied in a fixed order`() {
        // given both due at the same instant, with the research put in first
        val together = EPOCH + 2.hours
        val state = GameState.initial().copy(
            builds = mapOf(
                BuildingType.METAL_MINE to BuildJob(
                    building = BuildingType.METAL_MINE,
                    toLevel = BuildingLevel(2),
                    startedAt = EPOCH,
                    completesAt = together,
                ),
            ),
            activeResearch = ResearchJob(
                technology = Technology.EXTRACTION,
                toLevel = TechLevel(1),
                startedAt = EPOCH,
                completesAt = together,
            ),
        )

        // when
        val after = advance(state, from = EPOCH, to = together)

        // then - the colony first, then the empire; pinned so the log is reproducible
        assertEquals(
            listOf("BuildCompleted", "ResearchCompleted"),
            after.eventLog.map { it::class.simpleName },
        )
    }

    @Test
    fun `a research and a fleet landing together put the research first`() {
        // given
        val together = EPOCH + 2.hours
        val state = GameState.initial().copy(
            activeResearch = ResearchJob(
                technology = Technology.EXTRACTION,
                toLevel = TechLevel(1),
                startedAt = EPOCH,
                completesAt = together,
            ),
            returningFleet = ReturningFleet(
                ships = mapOf(ShipType.CARGO to 1),
                cargo = Resources.of(metal = 10),
                origin = Coordinates(galaxy = 1, system = 1, position = 1),
                arrivesAt = together,
            ),
        )

        // when
        val after = advance(state, from = EPOCH, to = together)

        // then
        assertEquals(
            listOf("ResearchCompleted", "FleetReturned"),
            after.eventLog.map { it::class.simpleName },
        )
    }

    @Test
    fun `two projects in a row compound rather than replace each other`() {
        // given Extraction taken to level 2 one project at a time, each through advance
        val first = GameState.initial().researching(Technology.EXTRACTION, at = EPOCH)
        val firstCompletesAt = first.project().completesAt
        val firstDone = advance(first, from = EPOCH, to = firstCompletesAt)
        val second = firstDone.researching(Technology.EXTRACTION, at = firstCompletesAt)

        // when
        val secondDone = advance(second, from = firstCompletesAt, to = second.project().completesAt)

        // then
        assertEquals(TechLevel(2), secondDone.research.extraction)
        assertEquals(
            2,
            secondDone.eventLog.count { it is Event.ResearchCompleted },
            "each level is its own event",
        )
    }

    private companion object {
        val EPOCH = Instant.fromEpochMilliseconds(0)
    }
}
