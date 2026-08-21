package dev.fardavide.oltre.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration
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
        assertEquals(5_800, DepositBalance.cap(world, ResourceKind.METAL, danger = 0))
        assertEquals(2_900, DepositBalance.cap(world, ResourceKind.CRYSTAL, danger = 0))
    }

    @Test
    fun `richness scales the cap`() {
        val rich = world(at = at(2, 125, 8), hazards = emptySet(), metalPerMillion = 1_600_000)
        val poor = world(at = at(2, 125, 8), hazards = emptySet(), metalPerMillion = 600_000)

        assertEquals(9_280, DepositBalance.cap(rich, ResourceKind.METAL, danger = 0))
        assertEquals(3_480, DepositBalance.cap(poor, ResourceKind.METAL, danger = 0))
    }

    @Test
    fun `danger deepens the vein by the same third it sweetens the rate`() {
        val world = world(at = at(1, 125, 8), hazards = emptySet())

        // then — the whole point of the sheet's 2.2: the cap carries the multiplier the rate carries.
        assertEquals(5_800, DepositBalance.cap(world, ResourceKind.METAL, danger = 0))
        assertEquals(7_830, DepositBalance.cap(world, ResourceKind.METAL, danger = 1))
        assertEquals(15_950, DepositBalance.cap(world, ResourceKind.METAL, danger = 5))
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
    fun `a four skiff fleet strips any world in the same time wherever it is`() {
        // Davide's rule as a test, with its subject re-derived at issue #68 — *"a typical fleet takes
        // about two runs"*, where it used to read *"a regular ship"*. Four hulls is the manifest that
        // fixes the constant, and the invariant is untouched by the change: the cap and the rate carry
        // one multiplier, so this holds at every richness and every danger.
        val cases = listOf(
            world(at = at(2, 125, 8), hazards = emptySet()) to 0,
            world(at = at(2, 125, 8), hazards = emptySet(), metalPerMillion = 1_600_000) to 0,
            world(at = at(2, 125, 8), hazards = emptySet(), metalPerMillion = 600_000) to 0,
            world(at = at(2, 200, 8), hazards = setOf(Hazard.ION_STORMS)) to 2,
            world(at = at(1, 125, 8), hazards = setOf(Hazard.ION_STORMS, Hazard.THIN_CRUST)) to 5,
        )
        val fleet = Ships.of(ShipType.SKIFF, 4)

        for ((world, danger) in cases) {
            val cap = DepositBalance.cap(world, ResourceKind.METAL, danger = danger)
            assertEquals(
                1_450.minutes,
                DepositBalance.workingTime(
                    world = world,
                    gathering = ResourceKind.METAL,
                    ships = fleet,
                    danger = danger,
                    research = Research.initial(),
                    remaining = cap,
                ),
                "stripping ${world.at} at danger $danger",
            )
        }
    }

    @Test
    fun `a lone skiff now needs four days rather than one`() {
        // The cost of re-deriving the rule against a fleet, stated rather than discovered: the hull
        // that used to empty a doorstep world in a day is a quarter of the manifest the cap is now
        // sized for, so on its own it takes four times as long.
        val world = world(at = at(2, 125, 8), hazards = emptySet())
        val cap = DepositBalance.cap(world, ResourceKind.METAL, danger = 0)

        assertEquals(
            5_800.minutes,
            DepositBalance.workingTime(
                world = world,
                gathering = ResourceKind.METAL,
                ships = Ships.of(ShipType.SKIFF, 1),
                danger = 0,
                research = Research.initial(),
                remaining = cap,
            ),
        )
    }

    @Test
    fun `a partial minute of work is still a minute on the surface`() {
        val world = world(at = at(2, 125, 8), hazards = emptySet())

        // 1,000 priced units at three hulls is 333.3 minutes, and the third of a minute is worked
        assertEquals(
            334.minutes,
            DepositBalance.workingTime(
                world = world,
                gathering = ResourceKind.METAL,
                ships = Ships.of(ShipType.SKIFF, 3),
                danger = 0,
                research = Research.initial(),
                remaining = 1_000,
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
            research = Research.initial(),
            remaining = remaining,
        )

        val atThatMinute = FleetBalance.cargo(world, ResourceKind.METAL, ships, working, danger = 3, research = Research.initial()).metal
        val aMinuteEarlier =
            FleetBalance.cargo(world, ResourceKind.METAL, ships, working - 1.minutes, danger = 3, research = Research.initial()).metal
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
                research = Research.initial(),
                remaining = 0,
            ),
        )
    }

    // ── Refill ───────────────────────────────────────────────────────────────────────────────

    @Test
    fun `a world puts back five percent of its cap a day`() {
        val capFine = 5_800L * Resources.FINE_PER_UNIT

        // 5% of 5,800 is 290 units a day, and twenty of those days is the whole vein.
        assertEquals(1_044_000_000, DepositBalance.regenerated(0, capFine, 1.days))
        assertEquals(5_220_000_000, DepositBalance.regenerated(0, capFine, 5.days))
    }

    @Test
    fun `a world is full again after twenty days and never more than full`() {
        val capFine = 5_800L * Resources.FINE_PER_UNIT

        assertEquals(capFine, DepositBalance.regenerated(0, capFine, 20.days))
        assertEquals(capFine, DepositBalance.regenerated(0, capFine, 200.days))
        assertEquals(capFine, DepositBalance.regenerated(capFine, capFine, 1.days))
    }

    @Test
    fun `refill is measured from the stored instant rather than accumulated`() {
        // The deposit moves only when a run is dispatched — `advance` never writes it — so refill is
        // always one division from a single anchor to now. That is what keeps it exact: floors do not
        // telescope and a chained version would drift a fine unit per span.
        val capFine = 5_800L * Resources.FINE_PER_UNIT

        assertEquals(
            DepositBalance.regenerated(0, capFine, 11.hours),
            DepositBalance.regenerated(0, capFine, 4.hours + 7.hours),
        )
    }

    @Test
    fun `a stored stock above its cap is clamped rather than carried`() {
        // What keeps BASE_PRICED a number Davide can still move: lower the cap and every save is
        // consistent on the next read instead of needing a migration.
        val capFine = 5_800L * Resources.FINE_PER_UNIT

        assertEquals(capFine, DepositBalance.regenerated(capFine * 3, capFine, 0.hours))
    }

    @Test
    fun `a century of absence fills a world and overflows nothing`() {
        val capFine = 15_950L * Resources.FINE_PER_UNIT

        assertEquals(capFine, DepositBalance.regenerated(0, capFine, (365 * 100).days))
        assertEquals(0, DepositBalance.regenerated(0, capFine, 0.minutes))
    }

    // ── How long until a world is worth the ask ──────────────────────────────────────────────

    @Test
    fun `a world already holding what was asked for is worth visiting now`() {
        val capFine = 5_800L * Resources.FINE_PER_UNIT

        assertEquals(Duration.ZERO, DepositBalance.timeUntil(capFine, capFine, wanted = 900))
        assertEquals(Duration.ZERO, DepositBalance.timeUntil(capFine, capFine, wanted = 5_800))
    }

    @Test
    fun `an emptied world names when it will hold the ask again`() {
        val capFine = 5_800L * Resources.FINE_PER_UNIT

        // 5% of 5,800 is 290 a day, so 2,900 units is ten days and 5,800 is twenty.
        assertEquals(10.days, DepositBalance.timeUntil(0, capFine, wanted = 2_900))
        assertEquals(20.days, DepositBalance.timeUntil(0, capFine, wanted = 5_800))
    }

    @Test
    fun `an ask no world could ever hold is never`() {
        // Design's finding, and the reason the dispatch sheet's controls stay live in the waiting
        // state: a full fleet's lift is about the size of a vein, so the ask has to be able to shrink.
        val capFine = 5_800L * Resources.FINE_PER_UNIT

        assertNull(DepositBalance.timeUntil(0, capFine, wanted = 5_801))
    }

    @Test
    fun `a small ask of a nearly empty world is minutes rather than days`() {
        // The tier this state usually sits in: a whole unit comes back every five minutes.
        val capFine = 5_800L * Resources.FINE_PER_UNIT

        val wait = DepositBalance.timeUntil(0, capFine, wanted = 1)
        assertEquals(true, wait!! < 1.hours, "$wait")
    }

    // ── The guards, which are the model refusing rather than the balance answering ────────────

    @Test
    fun `a fleet that is not there works no time at all`() {
        val world = world(at = at(2, 125, 8), hazards = emptySet())

        assertEquals(
            Duration.ZERO,
            DepositBalance.workingTime(
                world = world,
                gathering = ResourceKind.METAL,
                ships = Ships.NONE,
                danger = 0,
                remaining = 1_000,
                research = Research.initial(),
            ),
        )
    }

    @Test
    fun `nothing about a vein is defined for deuterium or for a negative stock`() {
        val world = world(at = at(2, 125, 8), hazards = emptySet())

        assertFailsWith<IllegalArgumentException> {
            DepositBalance.workingTime(
                world = world,
                gathering = ResourceKind.DEUTERIUM,
                ships = Ships.of(ShipType.SKIFF, 1),
                danger = 0,
                remaining = 100,
                research = Research.initial(),
            )
        }
        assertFailsWith<IllegalArgumentException> {
            DepositBalance.workingTime(
                world = world,
                gathering = ResourceKind.METAL,
                ships = Ships.of(ShipType.SKIFF, 1),
                danger = 0,
                remaining = -1,
                research = Research.initial(),
            )
        }
    }

    @Test
    fun `a corrupt stock or cap is refused rather than carried`() {
        // A negative here can only come from a hand-edited save, and catching it at the point of
        // definition is what turns that into a clean failure instead of a wrong answer.
        assertFailsWith<IllegalArgumentException> { DepositBalance.regenerated(-1, 100, 1.days) }
        assertFailsWith<IllegalArgumentException> { DepositBalance.regenerated(0, -1, 1.days) }
        assertFailsWith<IllegalArgumentException> { DepositBalance.timeUntil(0, 100, wanted = -1) }
    }

    @Test
    fun `a world with no vein at all is never worth waiting for`() {
        // Reached through `prunedFull`, which asks about a coordinate that may hold no world.
        assertEquals(0, DepositBalance.regenerated(0, capFine = 0, elapsed = 5.days))
        assertNull(DepositBalance.timeUntil(0, capFine = 0, wanted = 1))
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
        hasRing = false,
    )

    @Test
    fun `working time and cargo are inverses of one expression in berths`() {
        // **The invariant that broke when the hauler landed.** `cargo` answers *how much a fleet
        // lifts in a given time* and `workingTime` answers *how long a fleet takes to lift a given
        // amount*; they are one expression read from two ends, so they must spend the same unit.
        //
        // `cargo` moved to berths with the hauler and this did not, so a manifest of one hauler and
        // two skiffs — three hulls, six berths — reported twice the working time it needs, and the
        // dispatch sheet's legs line read "on station 2h 24m · working 4h 48m" about a run that
        // cannot work longer than it stays. Asserted as the round trip rather than as a figure,
        // because a figure would be this fixture's arithmetic rather than the relationship.
        val target = GalaxyCoordinate(galaxy = 2, system = 125, slot = 6)
        val world = world(target, hazards = emptySet(), metalPerMillion = 1_240_000, crystalPerMillion = 900_000)
        val manifests = listOf(
            Ships.of(ShipType.SKIFF, 1),
            Ships.of(ShipType.SKIFF, 4),
            Ships.of(ShipType.HAULER, 1),
            Ships(mapOf(ShipType.HAULER to 1, ShipType.SKIFF to 2)),
        )

        for (ships in manifests) {
            val station = 160.minutes
            val lifts = FleetBalance.cargo(world, ResourceKind.METAL, ships, station, 0, Research.initial()).metal
            val takes = DepositBalance.workingTime(
                world = world,
                gathering = ResourceKind.METAL,
                ships = ships,
                danger = 0,
                remaining = lifts,
                research = Research.initial(),
            )

            // What it lifts in the whole station takes it the whole station — to the rounding the
            // ceiling adds, which is at most the minute a partial one is charged as.
            assertTrue(
                takes <= station && takes >= station - 1.minutes,
                "$ships lifts $lifts in $station but says it takes $takes",
            )
        }
    }

    @Test
    fun `one hauler works as fast as the four skiffs it replaces`() {
        // The berth is the unit, so the two manifests that carry four of them are indistinguishable
        // here — which is what makes the composition a trade about *speed* and nothing else.
        val target = GalaxyCoordinate(galaxy = 2, system = 125, slot = 6)
        val world = world(target, hazards = emptySet(), metalPerMillion = 1_240_000, crystalPerMillion = 900_000)
        fun takes(ships: Ships): Duration = DepositBalance.workingTime(
            world = world,
            gathering = ResourceKind.METAL,
            ships = ships,
            danger = 0,
            remaining = 2_000,
            research = Research.initial(),
        )

        assertEquals(takes(Ships.of(ShipType.SKIFF, 4)), takes(Ships.of(ShipType.HAULER, 1)))
    }
}
