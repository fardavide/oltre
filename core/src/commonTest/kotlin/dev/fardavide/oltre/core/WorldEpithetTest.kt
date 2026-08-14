package dev.fardavide.oltre.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

// **Derived, never rolled — so it cannot lie.** An epithet is a function of the three axes and of
// nothing else, exactly as richness is, which is what lets it be shown on a row without any risk of
// the words and the numbers under them disagreeing. `galaxy-identity-sheet.md` §3.2.
//
// It is measured against the **level-0** tolerance bands rather than against the player's current
// ones, so a world does not quietly stop being an iron giant because its owner bought a ladder
// level. What a world *is* does not depend on who is looking at it.
class WorldEpithetTest {

    @Test
    fun `the noun comes from the axis a world is most extreme on`() {
        assertEquals("giant", epithetFor(traits(gravityMilliG = 2_600)).noun)
        assertEquals("husk", epithetFor(traits(gravityMilliG = 200)).noun)
        assertEquals("frost", epithetFor(traits(celsius = -180)).noun)
        assertEquals("furnace", epithetFor(traits(celsius = 210)).noun)
        assertEquals("shroud", epithetFor(traits(pressureMilliAtm = 9_000)).noun)
        assertEquals("waste", epithetFor(traits(pressureMilliAtm = 10)).noun)
    }

    @Test
    fun `the adjective comes from the axis it is second most extreme on`() {
        // A heavy world that is also very cold is a frozen giant; the same world with the two
        // magnitudes swapped is an iron frost. Same two facts, and the word order says which one is
        // the headline.
        assertEquals("frozen giant", epithetFor(traits(gravityMilliG = 2_700, celsius = -120)).toString())
        assertEquals("iron frost", epithetFor(traits(gravityMilliG = 1_600, celsius = -250)).toString())
    }

    @Test
    fun `a world extreme on one axis alone takes that axis for both words without stuttering`() {
        // There is no second axis to borrow from, so the dominant axis supplies its own adjective —
        // **from a second table**. Reusing the first one shipped `frozen frost`, `hollow husk` and
        // `veiled shroud` across 10% of the galaxy: two word lists can be disjoint as strings and
        // identical in sense, and only the second of those is what a reader sees.
        assertEquals("iron giant", epithetFor(traits(gravityMilliG = 2_700)).toString())
        assertEquals("deep frost", epithetFor(traits(celsius = -250)).toString())
        assertEquals("brittle husk", epithetFor(traits(gravityMilliG = 200)).toString())
        assertEquals("drowned shroud", epithetFor(traits(pressureMilliAtm = 9_000)).toString())
        assertEquals("bare waste", epithetFor(traits(pressureMilliAtm = 10)).toString())
        assertEquals("ashen furnace", epithetFor(traits(celsius = 210)).toString())
    }

    @Test
    fun `a world inside every band is temperate and says so`() {
        // 1.5% of the map, and the only worlds a settler can take. Naming them for what they are
        // rather than for an extreme they do not have is the honest answer — and it is the one
        // epithet a player should be pleased to read.
        assertEquals("temperate world", epithetFor(traits()).toString())
    }

    @Test
    fun `every world in a galaxy gets an epithet`() {
        // No coordinate may render as an empty string on a row. Cheap to assert and impossible to
        // notice by hand across 4,700 worlds.
        for (system in 1..GalaxyBalance.SYSTEMS_PER_GALAXY) {
            for (slot in 1..GalaxyBalance.SLOTS_PER_SYSTEM) {
                val world = worldAt(TEST_GALAXY_SEED, GalaxyCoordinate(1, system, slot)) ?: continue
                val epithet = epithetFor(world.traits)

                assertTrue(epithet.adjective.isNotBlank() && epithet.noun.isNotBlank(), "$world has no epithet")
            }
        }
    }

    @Test
    fun `an epithet never contradicts the reading beside it`() {
        // The property the whole design rests on: a row shows the epithet *and* the axis values, so
        // a world called a frost must actually be colder than the band and a giant must actually be
        // heavier. Checked against the real galaxy rather than against hand-built traits.
        val unaided = GalaxyBalance.tolerance(AdaptationLevels.NONE)
        for (system in 1..GalaxyBalance.SYSTEMS_PER_GALAXY) {
            for (slot in 1..GalaxyBalance.SLOTS_PER_SYSTEM) {
                val world = worldAt(TEST_GALAXY_SEED, GalaxyCoordinate(1, system, slot)) ?: continue
                val traits = world.traits

                when (epithetFor(traits).noun) {
                    "frost" -> assertTrue(traits.temperature.celsius < unaided.temperature.min, "$world")
                    "furnace" -> assertTrue(traits.temperature.celsius > unaided.temperature.max, "$world")
                    "giant" -> assertTrue(traits.gravity.milliG > unaided.gravity.max, "$world")
                    "husk" -> assertTrue(traits.gravity.milliG < unaided.gravity.min, "$world")
                    "shroud" -> assertTrue(traits.pressure.milliAtm > unaided.pressure.max, "$world")
                    "waste" -> assertTrue(traits.pressure.milliAtm < unaided.pressure.min, "$world")
                }
            }
        }
    }
}

// Middling on every axis unless a test says otherwise, so each case names only the thing it is
// about. Hazards and richness are absent deliberately: an epithet reads the three axes and nothing
// else, and the hazards already carry their own line on the row.
private fun traits(
    celsius: Int = 10,
    gravityMilliG: Int = 1_000,
    pressureMilliAtm: Int = 1_500,
): WorldTraits = WorldTraits(
    temperature = Temperature(celsius),
    gravity = Gravity(gravityMilliG),
    pressure = Pressure(pressureMilliAtm),
    metalRichness = GalaxyBalance.metalRichness(Gravity(gravityMilliG)),
    crystalRichness = GalaxyBalance.crystalRichness(Pressure(pressureMilliAtm)),
    deuteriumRichness = GalaxyBalance.deuteriumRichness(Temperature(celsius)),
    hazards = emptySet(),
    fields = GalaxyBalance.fields(Gravity(gravityMilliG)),
)
