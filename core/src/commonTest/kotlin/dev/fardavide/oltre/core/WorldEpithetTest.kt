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
        assertEquals(EpithetNoun.GIANT, epithetFor(traits(gravityMilliG = 2_600)).noun)
        assertEquals(EpithetNoun.HUSK, epithetFor(traits(gravityMilliG = 200)).noun)
        assertEquals(EpithetNoun.FROST, epithetFor(traits(celsius = -180)).noun)
        assertEquals(EpithetNoun.FURNACE, epithetFor(traits(celsius = 210)).noun)
        assertEquals(EpithetNoun.SHROUD, epithetFor(traits(pressureMilliAtm = 9_000)).noun)
        assertEquals(EpithetNoun.WASTE, epithetFor(traits(pressureMilliAtm = 10)).noun)
    }

    @Test
    fun `the adjective comes from the axis it is second most extreme on`() {
        // A heavy world that is also very cold is a frozen giant; the same world with the two
        // magnitudes swapped is an iron frost. Same two facts, and the word order says which one is
        // the headline.
        assertEquals(
            WorldEpithet(EpithetAdjective.FROZEN, EpithetNoun.GIANT),
            epithetFor(traits(gravityMilliG = 2_700, celsius = -120)),
        )
        assertEquals(
            WorldEpithet(EpithetAdjective.IRON, EpithetNoun.FROST),
            epithetFor(traits(gravityMilliG = 1_600, celsius = -250)),
        )
    }

    @Test
    fun `a world extreme on one axis alone takes that axis for both words without stuttering`() {
        // There is no second axis to borrow from, so the dominant axis supplies its own adjective —
        // **from a second table**. Reusing the first one shipped `frozen frost`, `hollow husk` and
        // `veiled shroud` across 10% of the galaxy: two word lists can be disjoint as strings and
        // identical in sense, and only the second of those is what a reader sees.
        assertEquals(WorldEpithet(EpithetAdjective.IRON, EpithetNoun.GIANT), epithetFor(traits(gravityMilliG = 2_700)))
        assertEquals(WorldEpithet(EpithetAdjective.DEEP, EpithetNoun.FROST), epithetFor(traits(celsius = -250)))
        assertEquals(WorldEpithet(EpithetAdjective.BRITTLE, EpithetNoun.HUSK), epithetFor(traits(gravityMilliG = 200)))
        assertEquals(
            WorldEpithet(EpithetAdjective.DROWNED, EpithetNoun.SHROUD),
            epithetFor(traits(pressureMilliAtm = 9_000)),
        )
        assertEquals(WorldEpithet(EpithetAdjective.BARE, EpithetNoun.WASTE), epithetFor(traits(pressureMilliAtm = 10)))
        assertEquals(WorldEpithet(EpithetAdjective.ASHEN, EpithetNoun.FURNACE), epithetFor(traits(celsius = 210)))
    }

    @Test
    fun `a world inside every band is temperate and says so`() {
        // 1.5% of the map, and the only worlds a settler can take. Naming them for what they are
        // rather than for an extreme they do not have is the honest answer — and it is the one
        // epithet a player should be pleased to read.
        assertEquals(WorldEpithet(EpithetAdjective.TEMPERATE, EpithetNoun.WORLD), epithetFor(traits()))
    }

    @Test
    fun `the temperate pair is reserved for worlds that are actually temperate`() {
        // **What "every world gets an epithet" became once the words were typed.** The old assertion
        // was that neither string was blank, which a pair of enums cannot be — so what is left to
        // check is the thing it was really guarding: `world` and `temperate` are the fallback, and a
        // world outside any band reaching them would be an extreme one described as mild.
        val unaided = GalaxyBalance.tolerance(AdaptationLevels.NONE)
        for (system in 1..GalaxyBalance.SYSTEMS_PER_GALAXY) {
            for (slot in 1..GalaxyBalance.SLOTS_PER_SYSTEM) {
                val world = worldAt(TEST_GALAXY_SEED, GalaxyCoordinate(1, system, slot)) ?: continue
                val traits = world.traits
                val inside = HostilityAxis.entries.all {
                    traits.axisValue(it) in unaided.bandOf(it).min..unaided.bandOf(it).max
                }

                assertEquals(inside, epithetFor(traits).noun == EpithetNoun.WORLD, "$world")
                assertEquals(inside, epithetFor(traits).adjective == EpithetAdjective.TEMPERATE, "$world")
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

                // Exhaustive now that the noun is an enum, which is the second thing typing it
                // bought: a seventh noun would fail to compile here rather than pass unchecked.
                when (epithetFor(traits).noun) {
                    EpithetNoun.FROST -> assertTrue(traits.temperature.celsius < unaided.temperature.min, "$world")
                    EpithetNoun.FURNACE -> assertTrue(traits.temperature.celsius > unaided.temperature.max, "$world")
                    EpithetNoun.GIANT -> assertTrue(traits.gravity.milliG > unaided.gravity.max, "$world")
                    EpithetNoun.HUSK -> assertTrue(traits.gravity.milliG < unaided.gravity.min, "$world")
                    EpithetNoun.SHROUD -> assertTrue(traits.pressure.milliAtm > unaided.pressure.max, "$world")
                    EpithetNoun.WASTE -> assertTrue(traits.pressure.milliAtm < unaided.pressure.min, "$world")
                    EpithetNoun.WORLD -> Unit
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
