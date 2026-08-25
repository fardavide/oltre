package dev.fardavide.oltre.server

import dev.fardavide.oltre.core.Experience
import dev.fardavide.oltre.core.GameSave
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds

class GenesisTest {

    private val davide = PlayerId("davide")
    private val someoneElse = PlayerId("someone-else")

    @Test
    fun `the same player at the same instant is handed the same galaxy`() {
        // The property that makes founding safe to retry, and the reason the seed is derived rather
        // than drawn: a random one would hand a player a different map every time the request was
        // repeated, until one of them happened to be written down.
        assertEquals(galaxySeedFor(davide, TEST_NOW), galaxySeedFor(davide, TEST_NOW))
    }

    @Test
    fun `two players founding in the same millisecond open in different galaxies`() {
        assertNotEquals(galaxySeedFor(davide, TEST_NOW), galaxySeedFor(someoneElse, TEST_NOW))
    }

    @Test
    fun `one player founding twice a millisecond apart opens in different galaxies`() {
        assertNotEquals(galaxySeedFor(davide, TEST_NOW), galaxySeedFor(davide, TEST_NOW + 1.milliseconds))
    }

    @Test
    fun `a new colony is stamped at the instant it was founded`() {
        val colony = newColony(davide, TEST_NOW)

        assertEquals(TEST_NOW, colony.lastUpdatedAt)
        assertEquals(GameSave.SCHEMA_VERSION, colony.schemaVersion)
        // Nothing has moved this colony's clock by hand, and nothing but the debug menu ever can.
        assertEquals(false, colony.debugUsed)
    }

    @Test
    fun `a new colony opens on its own doorstep and nothing else`() {
        val colony = newColony(davide, TEST_NOW)

        val galaxy = colony.state.galaxy
        assertEquals(galaxySeedFor(davide, TEST_NOW), galaxy.seed)
        // A colony that came back with an unsurveyed home would be a colony with no map at all —
        // every world on the screen would read `Unsurveyed`, including the one it is standing on.
        assertTrue(galaxy.home in galaxy.surveyed)
        assertEquals(emptyList(), colony.state.eventLog)
        assertEquals(Experience.NONE, colony.state.experience)
    }
}
