package dev.fardavide.oltre.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes

// The fleet's curves, pinned value by value. Every world here is **constructed**, never generated:
// the generator's own numbers are `GalaxyGenerationTest`'s subject, and a balance test that read them
// would move every time a distribution did.
class FleetBalanceTest {

    private fun at(galaxy: Int, system: Int, slot: Int): GalaxyCoordinate =
        GalaxyCoordinate(galaxy = galaxy, system = system, slot = slot)

    private val home = at(2, 125, 5)

    // ── Distance: three rules and not one metric ─────────────────────────────────────────────

    @Test
    fun `inside one system distance is five units per slot`() {
        // then — the only branch the slot number reaches at all
        assertEquals(0, FleetBalance.distanceUnits(home, home))
        assertEquals(5, FleetBalance.distanceUnits(home, at(2, 125, 6)))
        assertEquals(20, FleetBalance.distanceUnits(home, at(2, 125, 1)))
        assertEquals(50, FleetBalance.distanceUnits(home, at(2, 125, 15)))
    }

    @Test
    fun `inside one galaxy distance is ninety five plus five per system`() {
        assertEquals(100, FleetBalance.distanceUnits(home, at(2, 126, 5)))
        assertEquals(100, FleetBalance.distanceUnits(home, at(2, 124, 5)))
        assertEquals(120, FleetBalance.distanceUnits(home, at(2, 130, 5)))
        assertEquals(1_340, FleetBalance.distanceUnits(at(2, 1, 1), at(2, 250, 1)))
    }

    @Test
    fun `across galaxies distance is twenty seven hundred per galaxy`() {
        assertEquals(2_700, FleetBalance.distanceUnits(home, at(1, 125, 5)))
        assertEquals(2_700, FleetBalance.distanceUnits(home, at(3, 125, 5)))
        assertEquals(5_400, FleetBalance.distanceUnits(home, at(4, 125, 5)))
    }

    @Test
    fun `the three branches are different rules rather than one scale`() {
        // A rule that merely accumulated would make the slot term visible everywhere. It is not:
        // the moment the system differs the slot stops counting, and the moment the galaxy differs
        // the system stops counting too.
        assertEquals(
            FleetBalance.distanceUnits(home, at(2, 126, 1)),
            FleetBalance.distanceUnits(home, at(2, 126, 15)),
        )
        assertEquals(
            FleetBalance.distanceUnits(home, at(3, 1, 1)),
            FleetBalance.distanceUnits(home, at(3, 250, 15)),
        )

        // And each branch's floor is above the previous branch's ceiling — crossing your own system
        // end to end is cheaper than one step to the next star, and crossing a galaxy end to end is
        // cheaper than a single hop out of it.
        assertTrue(
            FleetBalance.distanceUnits(at(2, 125, 1), at(2, 125, 15)) <
                FleetBalance.distanceUnits(home, at(2, 126, 5)),
        )
        assertTrue(
            FleetBalance.distanceUnits(at(2, 1, 1), at(2, 250, 1)) <
                FleetBalance.distanceUnits(home, at(3, 125, 5)),
        )
    }

    @Test
    fun `distance is symmetric`() {
        assertEquals(
            FleetBalance.distanceUnits(home, at(3, 200, 12)),
            FleetBalance.distanceUnits(at(3, 200, 12), home),
        )
    }

    // ── The clock ───────────────────────────────────────────────────────────────────────────

    @Test
    fun `a flight is ten minutes plus one minute per ten units`() {
        // The nearest target in the sky is a flat ten minutes — five units buys no minute at all —
        // which is the base term doing the job it exists for.
        assertEquals(10.minutes, FleetBalance.flight(home, at(2, 125, 6)))
        assertEquals(17.minutes, FleetBalance.flight(at(2, 125, 1), at(2, 125, 15)))
        assertEquals(20.minutes, FleetBalance.flight(home, at(2, 126, 5)))
        assertEquals(29.minutes, FleetBalance.flight(home, at(2, 145, 5)))
        assertEquals(280.minutes, FleetBalance.flight(home, at(3, 125, 5)))
    }

    @Test
    fun `the minute is whole so a few units buy nothing`() {
        // 100 units and 105 units are the same flight: the division floors, and that is what keeps
        // a published table of minutes true to the unit.
        assertEquals(
            FleetBalance.flight(home, at(2, 126, 5)),
            FleetBalance.flight(home, at(2, 127, 5)),
        )
    }

    @Test
    fun `a round trip is exactly twice the flight`() {
        for (target in listOf(at(2, 125, 6), at(2, 145, 5), at(3, 125, 5), at(4, 1, 1))) {
            assertEquals(FleetBalance.flight(home, target) * 2, FleetBalance.roundTrip(home, target))
        }
    }

    @Test
    fun `the longest trip a mid galaxy home can order still fits inside a day`() {
        // Two galaxy hops out and back — the far end of the map from galaxy 2 — which is what makes
        // `WindowTooShort` unreachable with skiffs from here.
        assertEquals(1_100.minutes, FleetBalance.roundTrip(home, at(4, 1, 1)))
        assertTrue(FleetBalance.roundTrip(home, at(4, 1, 1)) < 24.hours)
    }

    // ── The window ladder ───────────────────────────────────────────────────────────────────

    @Test
    fun `the published ladder`() {
        assertEquals(listOf(1.hours, 3.hours, 6.hours, 12.hours, 24.hours), FleetBalance.WINDOWS)
        assertEquals(20.minutes, FleetBalance.MINIMUM_STATION)
    }

    @Test
    fun `a target next door offers every rung`() {
        assertEquals(FleetBalance.WINDOWS, FleetBalance.windowsFor(home, at(2, 125, 6)))
    }

    @Test
    fun `a rung is absent rather than disabled once the trip no longer fits`() {
        // The whole ladder minus its shortest rung. What the screen shows is four controls, not five
        // with one greyed out — which is how a narrowing ladder teaches distance before any copy does.
        val narrowed = FleetBalance.windowsFor(home, at(2, 128, 5))
        assertEquals(listOf(3.hours, 6.hours, 12.hours, 24.hours), narrowed)
        assertTrue(1.hours !in narrowed)
    }

    @Test
    fun `a rung that leaves exactly the minimum station survives`() {
        // 20m out and back plus 20m on the surface is exactly one hour, and the boundary is
        // inclusive — one system further pushes the round trip to 42m and the rung disappears.
        assertEquals(
            1.hours,
            FleetBalance.roundTrip(home, at(2, 126, 5)) + FleetBalance.MINIMUM_STATION,
        )
        assertTrue(1.hours in FleetBalance.windowsFor(home, at(2, 126, 5)))
    }

    @Test
    fun `a very distant target offers only the longest rung`() {
        assertEquals(listOf(12.hours, 24.hours), FleetBalance.windowsFor(home, at(3, 125, 5)))
        assertEquals(listOf(24.hours), FleetBalance.windowsFor(home, at(4, 1, 1)))
    }

    @Test
    fun `a target beyond every rung offers nothing at all`() {
        // Three galaxy hops is 27h20m out and back — past the longest window there is — so a home in
        // galaxy 1 simply cannot order the far corner with these hulls.
        assertTrue(FleetBalance.windowsFor(at(1, 1, 1), at(4, 250, 15)).isEmpty())
    }

    @Test
    fun `every rung the ladder offers leaves at least the minimum station`() {
        for (target in listOf(at(2, 125, 6), at(2, 128, 5), at(3, 125, 5), at(4, 1, 1))) {
            for (window in FleetBalance.windowsFor(home, target)) {
                assertTrue(
                    FleetBalance.stationFor(home, target, window) >= FleetBalance.MINIMUM_STATION,
                    "window $window to $target left too little station",
                )
            }
        }
    }

    @Test
    fun `flight eats the window rather than extending it`() {
        // The shape decision of the whole mechanic: a far world delivers fewer station-hours out of
        // the same absence, so it has to be richer to be worth it.
        assertEquals(160.minutes, FleetBalance.stationFor(home, at(2, 125, 6), 3.hours))
        assertEquals(140.minutes, FleetBalance.stationFor(home, at(2, 126, 5), 3.hours))
        assertTrue(
            FleetBalance.stationFor(home, at(3, 125, 5), 24.hours) <
                FleetBalance.stationFor(home, at(2, 125, 6), 24.hours),
        )
    }

    // ── Danger ──────────────────────────────────────────────────────────────────────────────

    @Test
    fun `the distance band is four steps and the galaxy check wins`() {
        assertEquals(0, FleetBalance.distanceBand(home, at(2, 125, 12)))
        assertEquals(1, FleetBalance.distanceBand(home, at(2, 126, 5)))
        assertEquals(1, FleetBalance.distanceBand(at(2, 100, 5), at(2, 225, 5)))
        assertEquals(2, FleetBalance.distanceBand(at(2, 100, 5), at(2, 226, 5)))
        // The same system number in another galaxy is the furthest band and never the nearest.
        assertEquals(3, FleetBalance.distanceBand(home, at(3, 125, 5)))
    }

    @Test
    fun `danger is the hazards plus the band`() {
        val safe = world(home.copy(slot = 12), metalPerMillion = 1_000_000, hazards = emptySet())
        val hazardous = world(
            home.copy(slot = 12),
            metalPerMillion = 1_000_000,
            hazards = setOf(Hazard.ION_STORMS, Hazard.THIN_CRUST),
        )
        val faraway = world(
            at(3, 125, 5),
            metalPerMillion = 1_000_000,
            hazards = setOf(Hazard.ION_STORMS, Hazard.THIN_CRUST),
        )

        // Your own system with no hazards is a completely safe first run.
        assertEquals(0, FleetBalance.danger(home, safe))
        assertEquals(2, FleetBalance.danger(home, hazardous))
        assertEquals(5, FleetBalance.danger(home, faraway))
    }

    // ── The hold ────────────────────────────────────────────────────────────────────────────

    @Test
    fun `the sheet's worked example reads one hundred and thirty two metal`() {
        // given the design's own row: metal richness 1.24 next door, one skiff, a 3h window
        val target = home.copy(slot = 6)
        val rich = world(target, metalPerMillion = 1_240_000, hazards = emptySet())
        val station = FleetBalance.stationFor(home, target, 3.hours)
        assertEquals(160.minutes, station)

        // when
        val cargo = FleetBalance.cargo(
            world = rich,
            gathering = ResourceKind.METAL,
            ships = Ships.of(ShipType.SKIFF, 1),
            station = station,
            danger = FleetBalance.danger(home, rich),
        )

        // then
        assertEquals(132L, cargo.metal)
        assertEquals(0L, cargo.crystal)
        assertEquals(0L, cargo.deuterium)
    }

    @Test
    fun `the arithmetic divides once at the end`() {
        // The two orderings the sheet rules out are computed here rather than described so the
        // comparison is arithmetic: flooring the priced hold before the richness lands a unit low
        // and rounding at each step lands a unit high. The shipped answer is neither.
        val holdMinutes = 1L * FleetBalance.EXTRACTION_PER_HOUR * 160
        val flooredEarly = holdMinutes / 60 * 1_240_000 / 1_000_000
        val roundedEachStep = ((holdMinutes + 30) / 60 * 1_240_000 + 500_000) / 1_000_000
        assertEquals(131L, flooredEarly)
        assertEquals(133L, roundedEachStep)

        val target = home.copy(slot = 6)
        val rich = world(target, metalPerMillion = 1_240_000, hazards = emptySet())
        val cargo = FleetBalance.cargo(
            world = rich,
            gathering = ResourceKind.METAL,
            ships = Ships.of(ShipType.SKIFF, 1),
            station = 160.minutes,
            danger = 0,
        )
        assertEquals(132L, cargo.metal)

        // Four hulls carry more than four times one hull's rounded answer — 529 against 528 — which
        // is the same property stated where it is impossible to fudge.
        val four = FleetBalance.cargo(
            world = rich,
            gathering = ResourceKind.METAL,
            ships = Ships.of(ShipType.SKIFF, 4),
            station = 160.minutes,
            danger = 0,
        )
        assertEquals(529L, four.metal)
        assertTrue(four.metal > 4 * cargo.metal)
    }

    @Test
    fun `crystal is priced at two to one against metal`() {
        // The same hold at the same richness buys half as much of it — the game's own 1 : 2 : 3, so
        // the choice is about what the colony is short of rather than about which number is bigger.
        val target = home.copy(slot = 6)
        val even = world(target, metalPerMillion = 1_240_000, crystalPerMillion = 1_240_000, hazards = emptySet())

        val metal = FleetBalance.cargo(even, ResourceKind.METAL, Ships.of(ShipType.SKIFF, 1), 160.minutes, 0)
        val crystal = FleetBalance.cargo(even, ResourceKind.CRYSTAL, Ships.of(ShipType.SKIFF, 1), 160.minutes, 0)

        assertEquals(132L, metal.metal)
        assertEquals(66L, crystal.crystal)
        assertEquals(0L, crystal.metal)
    }

    @Test
    fun `each point of danger takes a tenth of the hold`() {
        val target = home.copy(slot = 6)
        val rich = world(target, metalPerMillion = 1_240_000, hazards = emptySet())
        fun holdAt(danger: Int): Long =
            FleetBalance.cargo(rich, ResourceKind.METAL, Ships.of(ShipType.SKIFF, 1), 160.minutes, danger).metal

        assertEquals(132L, holdAt(0))
        assertEquals(119L, holdAt(1))
        // A fully exposed run keeps half of it, which is the ceiling the tenth was sized against.
        assertEquals(66L, holdAt(5))
    }

    @Test
    fun `danger past ten points empties the hold rather than inverting it`() {
        val target = home.copy(slot = 6)
        val rich = world(target, metalPerMillion = 1_240_000, hazards = emptySet())

        assertEquals(
            Resources.of(),
            FleetBalance.cargo(rich, ResourceKind.METAL, Ships.of(ShipType.SKIFF, 1), 160.minutes, 10),
        )
        // Past ten the kept share is coerced rather than allowed to go negative — a negative hold is
        // the shape of bug `checkedTimes` exists for, arriving by a different door.
        assertEquals(
            Resources.of(),
            FleetBalance.cargo(rich, ResourceKind.METAL, Ships.of(ShipType.SKIFF, 1), 160.minutes, 14),
        )
    }

    @Test
    fun `a run that never lands and a run with no hulls carry nothing`() {
        val target = home.copy(slot = 6)
        val rich = world(target, metalPerMillion = 1_240_000, hazards = emptySet())

        assertEquals(
            Resources.of(),
            FleetBalance.cargo(rich, ResourceKind.METAL, Ships.of(ShipType.SKIFF, 1), 0.minutes, 0),
        )
        assertEquals(
            Resources.of(),
            FleetBalance.cargo(rich, ResourceKind.METAL, Ships.of(ShipType.SKIFF, 1), (-30).minutes, 0),
        )
        assertEquals(
            Resources.of(),
            FleetBalance.cargo(rich, ResourceKind.METAL, Ships.NONE, 160.minutes, 0),
        )
    }

    @Test
    fun `a richer world fills the same hold further`() {
        val target = home.copy(slot = 6)
        val poor = world(target, metalPerMillion = 600_000, hazards = emptySet())
        val rich = world(target, metalPerMillion = 1_600_000, hazards = emptySet())

        assertTrue(
            FleetBalance.cargo(poor, ResourceKind.METAL, Ships.of(ShipType.SKIFF, 1), 160.minutes, 0).metal <
                FleetBalance.cargo(rich, ResourceKind.METAL, Ships.of(ShipType.SKIFF, 1), 160.minutes, 0).metal,
        )
    }

    @Test
    fun `a run can never gather deuterium`() {
        // The exclusion the whole research branch rests on — deuterium buys the Robotics Factory and
        // a fleet that could fetch it would undercut the one ladder with a prize of its own.
        val target = home.copy(slot = 6)
        val rich = world(target, metalPerMillion = 1_240_000, hazards = emptySet())

        assertFailsWith<IllegalArgumentException> {
            FleetBalance.cargo(rich, ResourceKind.DEUTERIUM, Ships.of(ShipType.SKIFF, 1), 160.minutes, 0)
        }
    }

    // Constructed rather than generated, deliberately: these curves have to be pinned against numbers
    // a reader can check by hand, and the generator's distributions are another test's subject.
    private fun world(
        at: GalaxyCoordinate,
        metalPerMillion: Int,
        hazards: Set<Hazard>,
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
