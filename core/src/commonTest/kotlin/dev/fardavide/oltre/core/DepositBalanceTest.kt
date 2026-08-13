package dev.fardavide.oltre.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes

// The vein's two curves — how deep a world is and how fast it comes back — pinned value by value.
// Worlds are **constructed** here for `FleetBalanceTest`'s reason: the generator's distributions are
// another test's subject and a balance test that read them would move every time one did.
class DepositBalanceTest {

    private fun at(galaxy: Int, system: Int, slot: Int): GalaxyCoordinate =
        GalaxyCoordinate(galaxy = galaxy, system = system, slot = slot)

    // ── The cap ──────────────────────────────────────────────────────────────────────────────

    @Test
    fun `a plain doorstep world holds the base cap in metal and half of it in crystal`() {
        val world = world(at = at(2, 125, 8), hazards = emptySet())

        // then — priced at the game's own 1 : 2 basket, so the two deposits are worth the same
        assertEquals(1_450, DepositBalance.cap(world, ResourceKind.METAL, danger = 0))
        assertEquals(725, DepositBalance.cap(world, ResourceKind.CRYSTAL, danger = 0))
    }

    @Test
    fun `richness scales the cap`() {
        val rich = world(at = at(2, 125, 8), hazards = emptySet(), metalPerMillion = 1_600_000)
        val poor = world(at = at(2, 125, 8), hazards = emptySet(), metalPerMillion = 600_000)

        assertEquals(2_320, DepositBalance.cap(rich, ResourceKind.METAL, danger = 0))
        assertEquals(870, DepositBalance.cap(poor, ResourceKind.METAL, danger = 0))
    }

    @Test
    fun `danger deepens the vein by the same third it sweetens the rate`() {
        val world = world(at = at(1, 125, 8), hazards = emptySet())

        // then — the whole point of the sheet's 2.2: the cap carries the multiplier the rate carries.
        assertEquals(1_450, DepositBalance.cap(world, ResourceKind.METAL, danger = 0))
        assertEquals(1_957, DepositBalance.cap(world, ResourceKind.METAL, danger = 1))
        assertEquals(3_987, DepositBalance.cap(world, ResourceKind.METAL, danger = 5))
    }

    @Test
    fun `a run never gathers deuterium so a world holds no deuterium deposit`() {
        val world = world(at = at(2, 125, 8), hazards = emptySet())

        assertFailsWith<IllegalArgumentException> {
            DepositBalance.cap(world, ResourceKind.DEUTERIUM, danger = 0)
        }
    }

    @Test
    fun `a negative danger cannot deepen a world`() {
        val world = world(at = at(2, 125, 8), hazards = emptySet())

        assertFailsWith<IllegalArgumentException> {
            DepositBalance.cap(world, ResourceKind.METAL, danger = -1)
        }
    }

    // ── The invariant the whole design leans on ──────────────────────────────────────────────

    @Test
    fun `one skiff strips any world in the same time wherever it is`() {
        // Davide's rule as a test — "a regular ship takes two rounds or a whole day to deplete a
        // planet" — and Design's reason for putting `working` on the legs line: the cap and the rate
        // carry one multiplier, so this holds at every richness and every danger.
        val cases = listOf(
            world(at = at(2, 125, 8), hazards = emptySet()) to 0,
            world(at = at(2, 125, 8), hazards = emptySet(), metalPerMillion = 1_600_000) to 0,
            world(at = at(2, 125, 8), hazards = emptySet(), metalPerMillion = 600_000) to 0,
            world(at = at(2, 200, 8), hazards = setOf(Hazard.ION_STORMS)) to 2,
            world(at = at(1, 125, 8), hazards = setOf(Hazard.ION_STORMS, Hazard.THIN_CRUST)) to 5,
        )
        val one = Ships.of(ShipType.SKIFF, 1)

        for ((world, danger) in cases) {
            val cap = DepositBalance.cap(world, ResourceKind.METAL, danger = danger)
            assertEquals(
                1_450.minutes,
                DepositBalance.workingTime(
                    world = world,
                    gathering = ResourceKind.METAL,
                    ships = one,
                    danger = danger,
                    remaining = cap,
                ),
                "stripping ${world.at} at danger $danger",
            )
        }
    }

    @Test
    fun `four skiffs strip a full world in a quarter of the time`() {
        val world = world(at = at(2, 125, 8), hazards = emptySet())
        val cap = DepositBalance.cap(world, ResourceKind.METAL, danger = 0)

        // 1,450 / 4 = 362.5 minutes, and a partial minute still has to be worked
        assertEquals(
            363.minutes,
            DepositBalance.workingTime(
                world = world,
                gathering = ResourceKind.METAL,
                ships = Ships.of(ShipType.SKIFF, 4),
                danger = 0,
                remaining = cap,
            ),
        )
    }

    @Test
    fun `working time agrees with the hold the same fleet would lift`() {
        // One rounding convention, not two: the minute this returns is the first at which `cargo`
        // covers what is there, so the legs line and the figure can never disagree.
        val world = world(at = at(2, 125, 8), hazards = setOf(Hazard.RADIATION_BELT), metalPerMillion = 1_234_567)
        val ships = Ships.of(ShipType.SKIFF, 3)
        val remaining = 900L

        val working = DepositBalance.workingTime(
            world = world,
            gathering = ResourceKind.METAL,
            ships = ships,
            danger = 3,
            remaining = remaining,
        )

        val atThatMinute = FleetBalance.cargo(world, ResourceKind.METAL, ships, working, danger = 3).metal
        val aMinuteEarlier =
            FleetBalance.cargo(world, ResourceKind.METAL, ships, working - 1.minutes, danger = 3).metal
        assertEquals(true, atThatMinute >= remaining, "at $working the fleet lifts $atThatMinute of $remaining")
        assertEquals(true, aMinuteEarlier < remaining, "a minute earlier it lifts $aMinuteEarlier of $remaining")
    }

    @Test
    fun `an empty world takes no time to strip`() {
        val world = world(at = at(2, 125, 8), hazards = emptySet())

        assertEquals(
            0.minutes,
            DepositBalance.workingTime(
                world = world,
                gathering = ResourceKind.METAL,
                ships = Ships.of(ShipType.SKIFF, 1),
                danger = 0,
                remaining = 0,
            ),
        )
    }

    // ── Refill ───────────────────────────────────────────────────────────────────────────────

    @Test
    fun `a world puts back five percent of its cap a day`() {
        val capFine = 1_450L * Resources.FINE_PER_UNIT

        // 5% of 1,450 is 72.5 units, which is exact in fine units and would not be in whole ones —
        // the reason a deposit is stored the way a stock is.
        assertEquals(261_000_000, DepositBalance.regenerated(0, capFine, 1.days))
        assertEquals(1_305_000_000, DepositBalance.regenerated(0, capFine, 5.days))
    }

    @Test
    fun `a world is full again after twenty days and never more than full`() {
        val capFine = 1_450L * Resources.FINE_PER_UNIT

        assertEquals(capFine, DepositBalance.regenerated(0, capFine, 20.days))
        assertEquals(capFine, DepositBalance.regenerated(0, capFine, 200.days))
        assertEquals(capFine, DepositBalance.regenerated(capFine, capFine, 1.days))
    }

    @Test
    fun `refill is measured from the stored instant rather than accumulated`() {
        // The deposit moves only when a run is dispatched — `advance` never writes it — so refill is
        // always one division from a single anchor to now. That is what keeps it exact: floors do not
        // telescope and a chained version would drift a fine unit per span.
        val capFine = 1_450L * Resources.FINE_PER_UNIT

        assertEquals(
            DepositBalance.regenerated(0, capFine, 11.hours),
            DepositBalance.regenerated(0, capFine, 4.hours + 7.hours),
        )
    }

    @Test
    fun `a stored stock above its cap is clamped rather than carried`() {
        // What keeps BASE_PRICED a number Davide can still move: lower the cap and every save is
        // consistent on the next read instead of needing a migration.
        val capFine = 1_450L * Resources.FINE_PER_UNIT

        assertEquals(capFine, DepositBalance.regenerated(capFine * 3, capFine, 0.hours))
    }

    @Test
    fun `a century of absence fills a world and overflows nothing`() {
        val capFine = 3_987L * Resources.FINE_PER_UNIT

        assertEquals(capFine, DepositBalance.regenerated(0, capFine, (365 * 100).days))
        assertEquals(0, DepositBalance.regenerated(0, capFine, 0.minutes))
    }

    private fun world(
        at: GalaxyCoordinate,
        hazards: Set<Hazard>,
        metalPerMillion: Int = 1_000_000,
        crystalPerMillion: Int = 1_000_000,
    ): World = World(
        at = at,
        starClass = StarClass.STANDARD,
        traits = WorldTraits(
            temperature = Temperature(0),
            gravity = Gravity(1_000),
            pressure = Pressure(1_000),
            metalRichness = Richness(metalPerMillion),
            crystalRichness = Richness(crystalPerMillion),
            deuteriumRichness = Richness(1_000_000),
            hazards = hazards,
            fields = 150,
        ),
    )
}
