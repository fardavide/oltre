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

    // Nothing researched: every curve pinned here is the unresearched one, and the fourth
    // technology's effect is `ProspectingTest`'s subject rather than a term in these tables.
    private val NONE: Research = Research.initial()

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
    fun `the sheet's worked example reads one hundred and ninety eight metal`() {
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
            research = Research.initial(),
        )

        // then — 198 at the `EXTRACTION_PER_HOUR` of 60. This row has been published three times:
        // 132 against the draft's 40, 66 after round 17 halved it to 20, and 198 after round 21
        // tripled it on Davide's *"I don't think a 20% is enough"*. Round 22 swept it against a game
        // that can finally buy hulls, found a fleet-first player out-producing their colony 2.7 to 1,
        // and Davide kept 60 anyway — so the figure stands and the guardrail is the thing that moved.
        // Pinned as a literal on purpose, the way `GalaxyBalanceTest` pins its published tables: a
        // balance change should have to edit a test.
        assertEquals(198L, cargo.metal)
        assertEquals(0L, cargo.crystal)
        assertEquals(0L, cargo.deuterium)
    }

    @Test
    fun `the arithmetic divides once at the end`() {
        // The ordering the sheet rules out is computed here rather than described, so the comparison
        // is arithmetic rather than prose: flooring the priced hold *before* applying richness lands
        // a unit low, because it throws away the fraction of an hour twice.
        //
        // **Round 21 moved where the fraction lives, and the test had to move with it.** At
        // `EXTRACTION_PER_HOUR = 60` the priced hold is exactly the station in minutes — 60 per hour
        // is one per minute — so `hulls x RATE x minutes / 60` no longer has a fraction to lose and
        // the old flooring hazard is gone from that term entirely. It did not disappear; it moved
        // into the two terms that still divide, **richness and the danger bonus**, and that is what
        // this now measures. A test asserting a coincidence is worse than no test.
        val station = 170.minutes
        val target = home.copy(slot = 6)
        val rich = world(target, metalPerMillion = 1_240_000, hazards = emptySet())

        // Flooring richness before the bonus lands a unit low: floor(170 x 1.24) = 210, then +35%
        // is 283. The single division keeps the .8 and reaches 284.
        val flooredEarly = station.inWholeMinutes * 1_240_000 / 1_000_000 * 135 / 100
        assertEquals(283L, flooredEarly)

        val cargo = FleetBalance.cargo(
            world = rich,
            gathering = ResourceKind.METAL,
            ships = Ships.of(ShipType.SKIFF, 1),
            station = station,
            danger = 1,
            research = Research.initial(),
        )
        assertEquals(284L, cargo.metal)
        assertTrue(cargo.metal > flooredEarly)

        // Four hulls carry more than four times one hull's answer — 1138 against 1136 — which is the
        // same property stated where it is impossible to fudge.
        val four = FleetBalance.cargo(
            world = rich,
            gathering = ResourceKind.METAL,
            ships = Ships.of(ShipType.SKIFF, 4),
            station = station,
            danger = 1,
            research = Research.initial(),
        )
        assertEquals(1_138L, four.metal)
        assertTrue(four.metal > 4 * cargo.metal)
    }

    @Test
    fun `crystal is priced at two to one against metal`() {
        // The same hold at the same richness buys half as much of it — the game's own 1 : 2 : 3, so
        // the choice is about what the colony is short of rather than about which number is bigger.
        val target = home.copy(slot = 6)
        val even = world(target, metalPerMillion = 1_240_000, crystalPerMillion = 1_240_000, hazards = emptySet())

        val metal = FleetBalance.cargo(even, ResourceKind.METAL, Ships.of(ShipType.SKIFF, 1), 160.minutes, 0, NONE)
        val crystal = FleetBalance.cargo(even, ResourceKind.CRYSTAL, Ships.of(ShipType.SKIFF, 1), 160.minutes, 0, NONE)

        assertEquals(198L, metal.metal)
        assertEquals(99L, crystal.crystal)
        assertEquals(0L, crystal.metal)
    }

    @Test
    fun `each point of danger adds a third to the hold rather than taking a tenth`() {
        // **Round 21 inverted the sign** — Davide: *"I would expect that more challenging planets are
        // even more rewarding."* Danger is unchanged as a number (hazards + the distance band) and
        // only what it does to the hold moved, so a frontier world with two hazards now pays 2.75x
        // where it used to keep half.
        val target = home.copy(slot = 6)
        val rich = world(target, metalPerMillion = 1_240_000, hazards = emptySet())
        fun holdAt(danger: Int): Long =
            FleetBalance.cargo(rich, ResourceKind.METAL, Ships.of(ShipType.SKIFF, 1), 160.minutes, danger, NONE).metal

        assertEquals(198L, holdAt(0))
        assertEquals(267L, holdAt(1))
        // A fully exposed run is worth 2.75 holds, which is what makes the map worth reading.
        assertEquals(545L, holdAt(5))
    }

    @Test
    fun `the danger bonus rises without bound rather than saturating`() {
        // `danger` is 0…5 by construction — two hazards plus a band of three — so nothing in the game
        // reaches these. The property is that the arithmetic stays monotone and stays inside a Long
        // anyway: `checkedTimes` throws rather than saturating, because a hold of `Long.MAX` is *"a
        // wrong answer wearing a plausible face"*. The old test asserted the opposite end of the same
        // guard, where a tenth per point drove the kept share negative past ten.
        val target = home.copy(slot = 6)
        val rich = world(target, metalPerMillion = 1_240_000, hazards = emptySet())
        fun holdAt(danger: Int): Long =
            FleetBalance.cargo(rich, ResourceKind.METAL, Ships.of(ShipType.SKIFF, 1), 160.minutes, danger, NONE).metal

        assertTrue(holdAt(10) > holdAt(5))
        assertTrue(holdAt(14) > holdAt(10))
    }

    @Test
    fun `a run that never lands and a run with no hulls carry nothing`() {
        val target = home.copy(slot = 6)
        val rich = world(target, metalPerMillion = 1_240_000, hazards = emptySet())

        assertEquals(
            Resources.of(),
            FleetBalance.cargo(rich, ResourceKind.METAL, Ships.of(ShipType.SKIFF, 1), 0.minutes, 0, NONE),
        )
        assertEquals(
            Resources.of(),
            FleetBalance.cargo(rich, ResourceKind.METAL, Ships.of(ShipType.SKIFF, 1), (-30).minutes, 0, NONE),
        )
        assertEquals(
            Resources.of(),
            FleetBalance.cargo(rich, ResourceKind.METAL, Ships.NONE, 160.minutes, 0, NONE),
        )
    }

    @Test
    fun `a richer world fills the same hold further`() {
        val target = home.copy(slot = 6)
        val poor = world(target, metalPerMillion = 600_000, hazards = emptySet())
        val rich = world(target, metalPerMillion = 1_600_000, hazards = emptySet())

        assertTrue(
            FleetBalance.cargo(poor, ResourceKind.METAL, Ships.of(ShipType.SKIFF, 1), 160.minutes, 0, NONE).metal <
                FleetBalance.cargo(rich, ResourceKind.METAL, Ships.of(ShipType.SKIFF, 1), 160.minutes, 0, NONE).metal,
        )
    }

    @Test
    fun `a run can never gather deuterium`() {
        // The exclusion the whole research branch rests on — deuterium buys the Robotics Factory and
        // a fleet that could fetch it would undercut the one ladder with a prize of its own.
        val target = home.copy(slot = 6)
        val rich = world(target, metalPerMillion = 1_240_000, hazards = emptySet())

        assertFailsWith<IllegalArgumentException> {
            FleetBalance.cargo(rich, ResourceKind.DEUTERIUM, Ships.of(ShipType.SKIFF, 1), 160.minutes, 0, NONE)
        }
    }

    // ── The hull ────────────────────────────────────────────────────────────────────────────

    @Test
    fun `the hull curve compounds by half per hull already owned`() {
        // The metal column at the 0.9.0 base, value by value. This curve is the fleet's ceiling and
        // the reason there is no Shipyard building: a compounding price against a linear return is
        // how every bound in this game is proved. Only the base moved at 0.9.0 — the x1.5 is
        // untouched, which is Davide's call about *which end* was too cheap.
        val metal = (0..5).map { FleetBalance.shipCost(ShipType.SKIFF, it).metal }
        assertEquals(listOf(800L, 1_200L, 1_800L, 2_700L, 4_050L, 6_075L), metal)
    }

    @Test
    fun `the crystal component compounds on its own base rather than tracking the metal one`() {
        // Per-step flooring on each component separately, which is `Curves.compound`'s rule and the
        // one the whole game's costs already follow. The two agree for six rungs at this base and
        // then part — 2,277 against a quarter of 9,112 — so the quarter is a coincidence that lasts
        // longer than it used to rather than the rule.
        val crystal = (0..6).map { FleetBalance.shipCost(ShipType.SKIFF, it).crystal }
        assertEquals(listOf(200L, 300L, 450L, 675L, 1_012L, 1_518L, 2_277L), crystal)
        assertTrue(FleetBalance.shipCost(ShipType.SKIFF, 6).metal / 4 != crystal.last())
    }

    @Test
    fun `the first hull is the published base`() {
        assertEquals(Resources.of(metal = 800, crystal = 200), FleetBalance.shipCost(ShipType.SKIFF, 0))
        assertEquals(FleetBalance.HULL_BASE_METAL, FleetBalance.shipCost(ShipType.SKIFF, 0).metal)
        assertEquals(FleetBalance.HULL_BASE_CRYSTAL, FleetBalance.shipCost(ShipType.SKIFF, 0).crystal)
    }

    @Test
    fun `a hull is never priced in deuterium`() {
        // Metal-led for `SurveyBalance`'s own reason — metal is the resource with nothing to buy —
        // and the deuterium column stays empty because deuterium is the Robotics gate's currency.
        for (owned in 0..8) {
            assertEquals(0L, FleetBalance.shipCost(ShipType.SKIFF, owned).deuterium)
        }
    }

    @Test
    fun `a deep fleet is priced rather than overflowed`() {
        // The curve is the ceiling, so it has to stay arithmetic all the way out rather than wrap
        // into a cost `covers()` reads as free — the bug `checkedTimes` exists for.
        assertTrue(FleetBalance.shipCost(ShipType.SKIFF, 40).metal > FleetBalance.shipCost(ShipType.SKIFF, 39).metal)
    }

    @Test
    fun `only the skiff has a price this slice`() {
        // The other three hulls each wait on exactly one design call — the hauler on slice 4, the
        // escort on a combat model, the settler on colonisation — and a made-up price for one of
        // them would be a number nobody chose sitting in a balance object that forbids exactly that.
        for (type in listOf(ShipType.HAULER, ShipType.ESCORT, ShipType.SETTLER)) {
            assertFailsWith<IllegalStateException> { FleetBalance.shipCost(type, 0) }
        }
    }

    @Test
    fun `a negative fleet cannot be priced`() {
        assertFailsWith<IllegalArgumentException> { FleetBalance.shipCost(ShipType.SKIFF, -1) }
    }

    // ── The yard clock ───────────────────────────────────────────────────────────────────────

    @Test
    fun `a hull takes four minutes per root of its own price`() {
        // The colony's own rule, `PlaceholderBalance.MINUTES_PER_ROOT_COST`, applied to the hull's
        // price — so a hull and a facility that cost the same take the same time to make.
        val cost = FleetBalance.shipCost(ShipType.SKIFF, alreadyOwned = 1)
        val expected = 4 * integerRoot(cost.metal + cost.crystal)

        assertEquals(
            expected.minutes,
            FleetBalance.buildDuration(ShipType.SKIFF, alreadyOwned = 1, roboticsFactory = BuildingLevel(0)),
        )
    }

    @Test
    fun `the wait compounds with the price rather than staying flat`() {
        // The price compounds at x1.5, so its root compounds at x1.2247 — which is the whole reason
        // the clock is derived from the cost rather than fixed per hull. The tenth skiff is not the
        // second one again with a bigger number beside it.
        val waits = (0..9).map {
            FleetBalance.buildDuration(ShipType.SKIFF, alreadyOwned = it, roboticsFactory = BuildingLevel(0))
        }

        assertEquals(waits.sorted(), waits)
        assertTrue(waits.last() > waits.first() * 5, "the curve barely moved across ten hulls: $waits")
    }

    @Test
    fun `the Robotics Factory divides the yard's clock the way it divides the colony's`() {
        // Davide's call, 2026-08-13. The fleet is gated by nothing, so this is not a requirement —
        // it is the one building that answers a wait the player is already serving.
        val alone = FleetBalance.buildDuration(ShipType.SKIFF, alreadyOwned = 1, roboticsFactory = BuildingLevel(0))

        for (level in 1..8) {
            assertEquals(
                alone / (1 + level),
                FleetBalance.buildDuration(ShipType.SKIFF, alreadyOwned = 1, roboticsFactory = BuildingLevel(level)),
                "Robotics $level did not divide the wait",
            )
        }
    }

    @Test
    fun `no hull is ever instant however deep the factory goes`() {
        // A zero-duration job completes at its own boundary, re-enters `advance` at the same instant
        // and recurses forever — the reason `MINIMUM_STATION` exists one section up, for the reason
        // it exists here.
        for (level in 0..60) {
            assertTrue(
                FleetBalance.buildDuration(ShipType.SKIFF, alreadyOwned = 0, roboticsFactory = BuildingLevel(level)) >=
                    FleetBalance.MINIMUM_YARD_DURATION,
                "Robotics $level cut a hull below the floor",
            )
        }
    }

    @Test
    fun `the root of nothing is nothing rather than a loop that never converges`() {
        // Newton's method divides by its own estimate, so the zero case is a guard rather than a
        // rounding decision — and `integerRoot` is shared by two balance objects now, so the guard is
        // pinned here rather than left to whichever caller happens to reach it first.
        assertEquals(0L, integerRoot(0))
        assertEquals(0L, integerRoot(-1))
        assertEquals(1L, integerRoot(1))
        assertEquals(3L, integerRoot(15))
        assertEquals(4L, integerRoot(16))
    }

    @Test
    fun `a hull with no price has no wait either`() {
        for (type in listOf(ShipType.HAULER, ShipType.ESCORT, ShipType.SETTLER)) {
            assertFailsWith<IllegalStateException> {
                FleetBalance.buildDuration(type, alreadyOwned = 0, roboticsFactory = BuildingLevel(0))
            }
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
