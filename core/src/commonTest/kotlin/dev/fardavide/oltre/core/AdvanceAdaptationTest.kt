package dev.fardavide.oltre.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

// Adaptation reaches the simulation exactly the way research and construction do: as an event
// applied by `advance` at a computed instant, never as a mutation and never as a number the UI
// reads on the side. That is what keeps offline progression, the sim harness and the screen
// telling the same story — and here it is also what makes a world's verdict change while the app
// was closed.
class AdvanceAdaptationTest {

    @Test
    fun `a ladder completes at its instant and raises the level`() {
        // given
        val started = GameState.initial().adapting(AdaptationTechnology.GRAVITIC, at = EPOCH)
        val completesAt = started.ladder().completesAt

        // when
        val before = advance(started, from = EPOCH, to = completesAt - 1.minutes)
        val after = advance(started, from = EPOCH, to = completesAt)

        // then
        assertEquals(TechLevel(0), before.research.gravitic)
        assertEquals(TechLevel(1), after.research.gravitic)
    }

    @Test
    fun `a completed ladder frees the slot and is appended to the log`() {
        val started = GameState.initial().adapting(AdaptationTechnology.GRAVITIC, at = EPOCH)
        val completesAt = started.ladder().completesAt

        val after = advance(started, from = EPOCH, to = completesAt + 1.minutes)

        assertNull(after.activeAdaptation)
        assertNull(after.researchSlotFreesAt)
        assertEquals(
            Event.AdaptationCompleted(
                technology = AdaptationTechnology.GRAVITIC,
                newLevel = TechLevel(1),
                at = completesAt,
            ),
            after.eventLog.last(),
        )
    }

    @Test
    fun `a ladder finished while the app was closed is applied on the way back in`() {
        val started = GameState.initial().adapting(AdaptationTechnology.ATMOSPHERIC, at = EPOCH)

        val after = advance(started, from = EPOCH, to = EPOCH + 7.days)

        assertEquals(TechLevel(1), after.research.atmospheric)
        assertNull(after.activeAdaptation)
    }

    @Test
    fun `a ladder raises only its own axis`() {
        val started = GameState.initial().adapting(AdaptationTechnology.ATMOSPHERIC, at = EPOCH)

        val after = advance(started, from = EPOCH, to = EPOCH + 7.days)

        // Each level widens exactly its own axis. That separation is the mechanic — an empire that
        // pushed Thermal and one that pushed Gravitic are looking at two different maps.
        assertEquals(AdaptationLevels(thermal = 0, gravitic = 0, atmospheric = 1), after.research.adaptationLevels())
    }

    @Test
    fun `climbing changes nothing about production`() {
        // The sheet's section 2 rule, held to: an adaptation level widens a band and does nothing
        // else. A ladder that quietly moved a per-hour rate would be the ninth interaction nobody
        // can hold in their head.
        val base = GameState.initial()
        val climbed = base.climbed(AdaptationTechnology.GRAVITIC, to = 6)

        assertEquals(
            PlaceholderBalance.effectiveMetalProductionPerHour(base.buildings, base.research),
            PlaceholderBalance.effectiveMetalProductionPerHour(climbed.buildings, climbed.research),
        )
        assertEquals(
            PlaceholderBalance.effectiveDeuteriumProductionPerHour(base.buildings, base.research),
            PlaceholderBalance.effectiveDeuteriumProductionPerHour(climbed.buildings, climbed.research),
        )
    }

    @Test
    fun `the span is composable across the completion boundary`() {
        // The property every event in `advance` has to keep: any span gives the same state as any
        // chain of sub-spans, or offline progression and a foregrounded app disagree.
        val started = GameState.initial().adapting(AdaptationTechnology.THERMAL, at = EPOCH)
        val end = EPOCH + 2.days

        val whole = advance(started, from = EPOCH, to = end)
        val halves = advance(
            advance(started, from = EPOCH, to = started.ladder().completesAt + 13.minutes),
            from = started.ladder().completesAt + 13.minutes,
            to = end,
        )

        assertEquals(whole, halves)
    }

    @Test
    fun `a build and a ladder landing on the same instant are applied in a fixed order`() {
        // Only one of the two research branches can be due at once, but a build can land beside
        // either — and the log has to be reproducible, so the colony is applied before the empire.
        val started = GameState.initial().adapting(AdaptationTechnology.THERMAL, at = EPOCH)
        val completesAt = started.ladder().completesAt
        val withBuild = started.copy(
            builds = mapOf(
                BuildingType.METAL_MINE to BuildJob(
                    building = BuildingType.METAL_MINE,
                    toLevel = BuildingLevel(2),
                    startedAt = EPOCH,
                    completesAt = completesAt,
                ),
            ),
        )

        val after = advance(withBuild, from = EPOCH, to = completesAt)

        assertIs<Event.BuildCompleted>(after.eventLog[after.eventLog.size - 2])
        assertIs<Event.AdaptationCompleted>(after.eventLog.last())
    }

    @Test
    fun `futureEvents predicts the ladder and predicts it in the log's order`() {
        val started = GameState.initial().adapting(AdaptationTechnology.THERMAL, at = EPOCH)
        val completesAt = started.ladder().completesAt

        val predicted = futureEvents(started, now = EPOCH)

        assertEquals(
            listOf(
                FutureEvent.AdaptationCompletes(
                    technology = AdaptationTechnology.THERMAL,
                    toLevel = TechLevel(1),
                    at = completesAt,
                ),
            ),
            predicted,
        )
    }

    @Test
    fun `buying the level a blocked world named actually unblocks it`() {
        // The whole point of the branch, end to end and through the real purchase: a world just
        // outside the gravity band, one level of Gravitic bought and waited out, and the same world
        // reads differently afterwards. Nothing in between knows the other exists — `startAdaptation`
        // moves a number in `Research` and `verdictFor` re-reads it.
        val state = GameState.initial().withSurveyedNeighbour()
        val world = worldWith(state.neighbour(), gravity = 1_450)

        val failure = assertIs<WorldVerdict.Blocked>(verdictFor(world, state)).failures.single()
        assertEquals(HostilityAxis.GRAVITY, failure.axis)
        assertEquals(1, failure.closedAtLevel, "one level must be enough, or the purchase below is not the one named")

        // when — the level the row named is actually bought and waited out
        val started = state.adapting(AdaptationTechnology.GRAVITIC, at = EPOCH)
        val landed = advance(started, from = EPOCH, to = started.ladder().completesAt)

        // then
        assertEquals(TechLevel(1), landed.research.gravitic)
        assertTrue(
            verdictFor(world, landed) !is WorldVerdict.Blocked,
            "the level the row named must actually land the world",
        )
    }

    // A coordinate in the home system that is not home, marked surveyed — so a hand-built world can
    // be judged against the real state. Hand-built rather than generated because what this file is
    // about is the ladder, and a generated world's traits are `GalaxyGenerationTest`'s business.
    private fun GameState.withSurveyedNeighbour(): GameState =
        copy(galaxy = galaxy.copy(surveyed = galaxy.surveyed + neighbour()))

    private fun GameState.neighbour(): GalaxyCoordinate =
        galaxy.home.copy(slot = if (galaxy.home.slot == 1) 2 else galaxy.home.slot - 1)

    // Temperature and pressure sit inside their bands, so gravity is the only axis that can fail.
    private fun worldWith(at: GalaxyCoordinate, gravity: Int): World = World(
        at = at,
        starClass = StarClass.STANDARD,
        traits = WorldTraits(
            temperature = Temperature(0),
            gravity = Gravity(gravity),
            pressure = Pressure(1_000),
            metalRichness = GalaxyBalance.metalRichness(Gravity(gravity)),
            crystalRichness = GalaxyBalance.crystalRichness(Pressure(1_000)),
            deuteriumRichness = GalaxyBalance.deuteriumRichness(Temperature(0)),
            hazards = emptySet(),
            fields = GalaxyBalance.fields(Gravity(gravity)),
        ),
        hasRing = false,
    )

    @Test
    fun `verdicts read the empire's real levels and never a hard-coded zero`() {
        // The failure this exists to catch is a caller passing `AdaptationLevels.NONE` by hand:
        // every world would stay as blocked as it was at genesis however deep the empire climbed.
        val state = GameState.initial().climbed(AdaptationTechnology.THERMAL, to = 9)
        // Anything but home, or both sides answer `Home` and the test proves nothing.
        val world = state.galaxy.surveyed
            .filter { it != state.galaxy.home }
            .firstNotNullOf { worldAt(state.galaxy.seed, it) }

        assertEquals(
            verdictFor(world, state.galaxy, AdaptationLevels(thermal = 9, gravitic = 0, atmospheric = 0)),
            verdictFor(world, state),
        )
    }

    private companion object {
        val EPOCH = Instant.fromEpochMilliseconds(0)
    }
}
