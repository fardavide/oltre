package dev.fardavide.oltre.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

// One verdict, in the sheet's precedence, and a `Blocked` that names its own remedy. The screen
// renders a sentence from this and does no arithmetic of its own.
class WorldVerdictTest {

    @Test
    fun `your own world reads Home whatever its traits say`() {
        // Home outranks everything, so a homeworld is never reported as blocked by its own gravity.
        val world = worldWith(HOME, gravity = 2_700, temperature = 200, pressure = 11_000)

        assertEquals(WorldVerdict.Home, verdictFor(world, galaxy(), AdaptationLevels.NONE))
    }

    @Test
    fun `a world held by another empire reads Occupied and carries the holder`() {
        val world = worldWith(NEIGHBOUR)
        val galaxy = galaxy(
            surveyed = setOf(HOME, NEIGHBOUR),
            ownership = listOf(homeOwnership(), WorldOwnership(NEIGHBOUR, EmpireId("kepler"))),
        )

        assertEquals(WorldVerdict.Occupied(EmpireId("kepler")), verdictFor(world, galaxy, AdaptationLevels.NONE))
    }

    @Test
    fun `Occupied outranks the traits so a held world never advertises its yield`() {
        val rich = worldWith(NEIGHBOUR, gravity = 1_450, pressure = 3_000, temperature = -30)
        val galaxy = galaxy(
            surveyed = setOf(HOME, NEIGHBOUR),
            ownership = listOf(homeOwnership(), WorldOwnership(NEIGHBOUR, EmpireId("kepler"))),
        )

        assertIs<WorldVerdict.Occupied>(verdictFor(rich, galaxy, AdaptationLevels.NONE))
    }

    @Test
    fun `a charted but unsurveyed world reads Unsurveyed`() {
        // The honest default at ship time: charting is free and galaxy-wide, surveying is earned.
        val world = worldWith(NEIGHBOUR)

        assertEquals(WorldVerdict.Unsurveyed, verdictFor(world, galaxy(), AdaptationLevels.NONE))
    }

    @Test
    fun `an unsurveyed world gives nothing away about whether it was worth taking`() {
        // Two worlds that differ in every trait must read identically until they are surveyed,
        // otherwise the survey action has already happened.
        val poor = worldWith(NEIGHBOUR, gravity = 200, pressure = 100, temperature = 40)
        val rich = worldWith(NEIGHBOUR, gravity = 1_450, pressure = 3_000, temperature = -30)

        assertEquals(
            verdictFor(poor, galaxy(), AdaptationLevels.NONE),
            verdictFor(rich, galaxy(), AdaptationLevels.NONE),
        )
    }

    @Test
    fun `a surveyed world inside every band but under the threshold reads Barren`() {
        // The common answer, by construction — a mild world is a poor one.
        val world = worldWith(NEIGHBOUR, temperature = 0, gravity = 1_000, pressure = 1_000)

        assertEquals(WorldVerdict.Barren, verdictFor(world, surveyedGalaxy(), AdaptationLevels.NONE))
    }

    @Test
    fun `a surveyed world over the threshold reads Settleable and carries its score`() {
        // Hostile on every axis but still inside the bands, which is exactly where the good ground
        // is: at the edge of what the species can take.
        val world = worldWith(NEIGHBOUR, temperature = -30, gravity = 1_400, pressure = 2_600)

        val verdict = assertIs<WorldVerdict.Settleable>(verdictFor(world, surveyedGalaxy(), AdaptationLevels.NONE))
        assertTrue(
            verdict.score.perMillion >= GalaxyBalance.WORTH_IT_THRESHOLD.perMillion,
            "a settleable world must clear the threshold, scored ${verdict.score}",
        )
    }

    @Test
    fun `the mild world scores below the hostile one`() {
        // The pillar, asserted rather than asserted about: an easy world is a poor world.
        val mild = worldWith(NEIGHBOUR, temperature = 0, gravity = 1_000, pressure = 1_000)
        val harsh = worldWith(NEIGHBOUR, temperature = -30, gravity = 1_400, pressure = 2_600)

        assertTrue(GalaxyBalance.yieldScore(mild.traits).perMillion < GalaxyBalance.yieldScore(harsh.traits).perMillion)
    }

    @Test
    fun `Blocked names the axis the gap and the level that would close it`() {
        // The design's load-bearing detail: this is the sentence that turns the galaxy screen into a
        // reason to research.
        val world = worldWith(NEIGHBOUR, temperature = 0, gravity = 2_400, pressure = 1_000)

        val verdict = assertIs<WorldVerdict.Blocked>(verdictFor(world, surveyedGalaxy(), AdaptationLevels.NONE))
        val failure = verdict.failures.single()
        assertEquals(HostilityAxis.GRAVITY, failure.axis)
        assertEquals(2_400, failure.worldValue)
        assertEquals(1_400, failure.toleratedBound)
        // 1,000 milli-g over the band at 120 per level
        assertEquals(9, failure.closedAtLevel)
        assertEquals(AdaptationTechnology.GRAVITIC, failure.axis.adaptation)
    }

    @Test
    fun `a world short of the lower bound is blocked by the same axis`() {
        // Pressure runs to zero, so the thin end blocks as surely as the thick one.
        val world = worldWith(NEIGHBOUR, temperature = 0, gravity = 1_000, pressure = 100)

        val failure = assertIs<WorldVerdict.Blocked>(
            verdictFor(world, surveyedGalaxy(), AdaptationLevels.NONE),
        ).failures.single()
        assertEquals(HostilityAxis.PRESSURE, failure.axis)
        assertEquals(500, failure.toleratedBound)
        // 400 milli-atm under the band at 60 per level
        assertEquals(7, failure.closedAtLevel)
    }

    @Test
    fun `Blocked lists every axis that fails in axis order`() {
        val world = worldWith(NEIGHBOUR, temperature = 200, gravity = 2_400, pressure = 11_000)

        val verdict = assertIs<WorldVerdict.Blocked>(verdictFor(world, surveyedGalaxy(), AdaptationLevels.NONE))
        assertEquals(
            listOf(HostilityAxis.TEMPERATURE, HostilityAxis.GRAVITY, HostilityAxis.PRESSURE),
            verdict.failures.map { it.axis },
        )
    }

    @Test
    fun `the right ladder unblocks a world and the other two do not`() {
        // Three ladders, and which one you pushed is what makes two empires see different maps.
        val world = worldWith(NEIGHBOUR, temperature = 0, gravity = 2_400, pressure = 1_000)
        val galaxy = surveyedGalaxy()

        assertIs<WorldVerdict.Blocked>(verdictFor(world, galaxy, AdaptationLevels(thermal = 9, gravitic = 0, atmospheric = 9)))
        assertIs<WorldVerdict.Settleable>(
            verdictFor(world, galaxy, AdaptationLevels(thermal = 0, gravitic = 9, atmospheric = 0)),
        )
    }

    @Test
    fun `a level short of the named one still leaves the world blocked`() {
        // The level `Blocked` names has to be the level that actually works, or the sentence is a
        // lie the player pays deuterium to discover.
        val world = worldWith(NEIGHBOUR, temperature = 0, gravity = 2_400, pressure = 1_000)
        val galaxy = surveyedGalaxy()

        assertIs<WorldVerdict.Blocked>(verdictFor(world, galaxy, AdaptationLevels(0, gravitic = 8, atmospheric = 0)))
        assertIs<WorldVerdict.Settleable>(verdictFor(world, galaxy, AdaptationLevels(0, gravitic = 9, atmospheric = 0)))
    }

    @Test
    fun `every named level really lands its world`() {
        // The same claim across the whole range each axis can generate, rather than at one point.
        val galaxy = surveyedGalaxy()
        for (gravity in 150..2_750 step 50) {
            val world = worldWith(NEIGHBOUR, temperature = 0, gravity = gravity, pressure = 1_000)
            val verdict = verdictFor(world, galaxy, AdaptationLevels.NONE)
            val failure = (verdict as? WorldVerdict.Blocked)?.failures?.singleOrNull() ?: continue
            val landed = AdaptationLevels(thermal = 0, gravitic = failure.closedAtLevel, atmospheric = 0)
            assertTrue(
                verdictFor(world, galaxy, landed) !is WorldVerdict.Blocked,
                "gravity $gravity claimed level ${failure.closedAtLevel} would land it, and it did not",
            )
        }
    }

    private fun worldWith(
        at: GalaxyCoordinate,
        temperature: Int = 0,
        gravity: Int = 1_000,
        pressure: Int = 1_000,
    ): World = World(
        at = at,
        starClass = StarClass.STANDARD,
        traits = WorldTraits(
            temperature = Temperature(temperature),
            gravity = Gravity(gravity),
            pressure = Pressure(pressure),
            metalRichness = GalaxyBalance.metalRichness(Gravity(gravity)),
            crystalRichness = GalaxyBalance.crystalRichness(Pressure(pressure)),
            deuteriumRichness = GalaxyBalance.deuteriumRichness(Temperature(temperature)),
            hazards = emptySet(),
            fields = GalaxyBalance.fields(Gravity(gravity)),
        ),
    )

    private fun homeOwnership(): WorldOwnership = WorldOwnership(at = HOME, holder = EmpireId.PLAYER)

    private fun galaxy(
        surveyed: Set<GalaxyCoordinate> = setOf(HOME),
        ownership: List<WorldOwnership> = listOf(homeOwnership()),
    ): GalaxyState = GalaxyState(
        seed = TEST_GALAXY_SEED,
        home = HOME,
        surveyed = surveyed,
        ownership = ownership,
        deposits = emptyList(),
    )

    private fun surveyedGalaxy(): GalaxyState = galaxy(surveyed = setOf(HOME, NEIGHBOUR))

    private companion object {
        val HOME = GalaxyCoordinate(galaxy = 1, system = 1, slot = 7)
        val NEIGHBOUR = GalaxyCoordinate(galaxy = 1, system = 1, slot = 8)
    }
}
