package dev.fardavide.oltre.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration
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

    // The fifth technology's is a term in these tables, because it is the only one that moves a
    // clock — so the drive gets a fixture here rather than a file of its own.
    private fun drive(level: Int): Research = NONE.withLevel(Technology.PROPULSION, TechLevel(level))

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
    fun `a flight is ten minutes plus one minute per five units at drive zero`() {
        // The nearest target in the sky is a flat ten minutes — five units buys one minute now, and
        // nothing under five buys any — which is the base term doing the job it exists for.
        assertEquals(11.minutes, FleetBalance.flight(home, at(2, 125, 6), NONE, FleetBalance.FASTEST_HULL))
        assertEquals(
            24.minutes,
            FleetBalance.flight(at(2, 125, 1), at(2, 125, 15), NONE, FleetBalance.FASTEST_HULL),
        )
        assertEquals(30.minutes, FleetBalance.flight(home, at(2, 126, 5), NONE, FleetBalance.FASTEST_HULL))
        assertEquals(49.minutes, FleetBalance.flight(home, at(2, 145, 5), NONE, FleetBalance.FASTEST_HULL))
        assertEquals(550.minutes, FleetBalance.flight(home, at(3, 125, 5), NONE, FleetBalance.FASTEST_HULL))
    }

    @Test
    fun `the first drive level is exactly the speed the game shipped at`() {
        // **The calibration the whole change is anchored on**, and it is why `UNITS_PER_MINUTE_BASE`
        // is 5 rather than any other number: drive 0 is half of what 0.14 flew at and drive 1 is
        // 0.14 exactly. So the technology is not a bonus bolted onto today's game — it is the thing
        // that gives today's game back, which is what makes the frontier feel *unlocked* rather than
        // handed over.
        val shipped = mapOf(
            at(2, 125, 6) to 10.minutes,
            at(2, 126, 5) to 20.minutes,
            at(2, 145, 5) to 29.minutes,
            at(3, 125, 5) to 280.minutes,
        )
        for ((target, was) in shipped) {
            assertEquals(was, FleetBalance.flight(home, target, drive(1), FleetBalance.FASTEST_HULL), "drive 1 to $target")
        }
        assertEquals(17.minutes, FleetBalance.flight(at(2, 125, 1), at(2, 125, 15), drive(1), FleetBalance.FASTEST_HULL))
    }

    @Test
    fun `the base term is untouched by the drive so the neighbourhood does not move`() {
        // **The drive is worthless next door and transformative at the frontier**, which is the whole
        // of why it is a *reach* technology rather than a speed bonus. `BASE_FLIGHT_MINUTES` is
        // outside the division, so a target five units away is ten minutes at every level there is.
        val nextDoor = at(2, 125, 6)
        assertEquals(11.minutes, FleetBalance.flight(home, nextDoor, NONE, FleetBalance.FASTEST_HULL))
        for (level in 1..6) {
            assertEquals(10.minutes, FleetBalance.flight(home, nextDoor, drive(level), FleetBalance.FASTEST_HULL), "drive $level")
        }
    }

    @Test
    fun `each drive level adds one base of units per minute`() {
        // Linear, not compounding, and deliberately: `1 + level` is what makes level 1 exactly double
        // the base, which is the calibration above. Every other technology in the branch compounds
        // because it multiplies a rate that is already a stock's derivative; this one divides a
        // distance, and a compounding divisor would delete the map by level 8.
        for (level in 0..6) {
            assertEquals((5L * (1 + level)), FleetBalance.unitsPerMinute(drive(level)), "drive $level")
        }
    }

    @Test
    fun `a drive level shortens the frontier far more than the neighbourhood`() {
        // The property the sheet's crossover table turns on, asserted as a shape rather than as a
        // table of minutes: the further the target, the more the same level is worth.
        val near = at(2, 126, 5)
        val far = at(3, 125, 5)
        fun saved(target: GalaxyCoordinate): Duration =
            FleetBalance.flight(home, target, NONE, FleetBalance.FASTEST_HULL) - FleetBalance.flight(home, target, drive(1), FleetBalance.FASTEST_HULL)

        assertTrue(saved(far) > saved(near) * 10, "the drive bought the frontier no more than the next street")
    }

    @Test
    fun `the minute is whole so a few units buy nothing`() {
        // 100 units and 105 units are the same flight: the division floors, and that is what keeps
        // a published table of minutes true to the unit.
        assertEquals(
            FleetBalance.flight(home, at(2, 126, 5), drive(1), FleetBalance.FASTEST_HULL),
            FleetBalance.flight(home, at(2, 127, 5), drive(1), FleetBalance.FASTEST_HULL),
        )
    }

    @Test
    fun `a round trip is exactly twice the flight`() {
        for (target in listOf(at(2, 125, 6), at(2, 145, 5), at(3, 125, 5), at(4, 1, 1))) {
            for (level in 0..3) {
                assertEquals(
                    FleetBalance.flight(home, target, drive(level), FleetBalance.FASTEST_HULL) * 2,
                    FleetBalance.roundTrip(home, target, drive(level), FleetBalance.FASTEST_HULL),
                )
            }
        }
    }

    @Test
    fun `the far corner of the map is out of reach until the drive is bought`() {
        // **Davide's own ask, in the one number that carries it** — *"navigating distance takes way
        // more time, without powered up ships"*. Two galaxy hops out and back is 36h 20m at drive 0,
        // past the longest window there is, so a colony in galaxy 2 simply cannot order the far end
        // of the map until it has researched its way there. One level brings it back inside a day.
        //
        // This is what makes `WindowTooShort` reachable in ordinary play for the first time — the
        // result's own comment used to say it waited on the hauler.
        assertEquals(2_180.minutes, FleetBalance.roundTrip(home, at(4, 1, 1), NONE, FleetBalance.FASTEST_HULL))
        assertTrue(FleetBalance.roundTrip(home, at(4, 1, 1), NONE, FleetBalance.FASTEST_HULL) > 24.hours)

        assertEquals(1_100.minutes, FleetBalance.roundTrip(home, at(4, 1, 1), drive(1), FleetBalance.FASTEST_HULL))
        assertTrue(FleetBalance.roundTrip(home, at(4, 1, 1), drive(1), FleetBalance.FASTEST_HULL) < 24.hours)
    }

    // ── The window ladder ───────────────────────────────────────────────────────────────────

    @Test
    fun `the published ladder`() {
        assertEquals(listOf(1.hours, 3.hours, 6.hours, 12.hours, 24.hours), FleetBalance.WINDOWS)
        assertEquals(20.minutes, FleetBalance.MINIMUM_STATION)
    }

    @Test
    fun `a target next door offers every rung`() {
        assertEquals(FleetBalance.WINDOWS, FleetBalance.windowsFor(home, at(2, 125, 6), NONE, FleetBalance.FASTEST_HULL))
    }

    @Test
    fun `a rung is absent rather than disabled once the trip no longer fits`() {
        // The whole ladder minus its shortest rung. What the screen shows is four controls, not five
        // with one greyed out — which is how a narrowing ladder teaches distance before any copy does.
        val narrowed = FleetBalance.windowsFor(home, at(2, 126, 5), NONE, FleetBalance.FASTEST_HULL)
        assertEquals(listOf(3.hours, 6.hours, 12.hours, 24.hours), narrowed)
        assertTrue(1.hours !in narrowed)
    }

    @Test
    fun `a drive level hands rungs back to a ladder that had narrowed`() {
        // **The teaching moment the sheet is built around, and it needs no copy at all.** A player
        // who buys a drive level opens the same world they looked at yesterday and finds windows that
        // were not there. `windowsFor` already omits a rung it cannot fill rather than disabling it,
        // so the whole lesson is delivered by a control appearing.
        val target = at(2, 126, 5)

        assertEquals(listOf(3.hours, 6.hours, 12.hours, 24.hours), FleetBalance.windowsFor(home, target, NONE, FleetBalance.FASTEST_HULL))
        assertEquals(FleetBalance.WINDOWS, FleetBalance.windowsFor(home, target, drive(1), FleetBalance.FASTEST_HULL))
    }

    @Test
    fun `a ladder never narrows as the drive deepens`() {
        // The monotonicity a player's mental model depends on: research can only ever add rungs. A
        // level that took one away would be a technology that made a target harder to reach.
        for (target in listOf(at(2, 125, 6), at(2, 126, 5), at(3, 125, 5), at(4, 1, 1))) {
            for (level in 0..5) {
                val shallow = FleetBalance.windowsFor(home, target, drive(level), FleetBalance.FASTEST_HULL)
                val deeper = FleetBalance.windowsFor(home, target, drive(level + 1), FleetBalance.FASTEST_HULL)
                assertTrue(deeper.containsAll(shallow), "drive ${level + 1} to $target lost a rung: $shallow -> $deeper")
            }
        }
    }

    @Test
    fun `a rung that leaves exactly the minimum station survives`() {
        // 20m out and back plus 20m on the surface is exactly one hour, and the boundary is
        // inclusive — five units further pushes the round trip to 42m and the rung disappears.
        assertEquals(
            1.hours,
            FleetBalance.roundTrip(home, at(2, 125, 15), NONE, FleetBalance.FASTEST_HULL) + FleetBalance.MINIMUM_STATION,
        )
        assertTrue(1.hours in FleetBalance.windowsFor(home, at(2, 125, 15), NONE, FleetBalance.FASTEST_HULL))
    }

    @Test
    fun `a very distant target offers only the longest rung`() {
        assertEquals(listOf(24.hours), FleetBalance.windowsFor(home, at(3, 125, 5), NONE, FleetBalance.FASTEST_HULL))
        assertEquals(listOf(12.hours, 24.hours), FleetBalance.windowsFor(home, at(3, 125, 5), drive(1), FleetBalance.FASTEST_HULL))
    }

    @Test
    fun `a target beyond every rung offers nothing at all`() {
        // At drive 0 that is now two galaxy hops rather than three — the far end of the map from a
        // mid-galaxy home — which is the reach half of *"navigating distance takes way more time,
        // without powered up ships"* stated as an empty ladder.
        assertTrue(FleetBalance.windowsFor(home, at(4, 1, 1), NONE, FleetBalance.FASTEST_HULL).isEmpty())
        assertTrue(FleetBalance.windowsFor(at(1, 1, 1), at(4, 250, 15), NONE, FleetBalance.FASTEST_HULL).isEmpty())
    }

    @Test
    fun `every rung the ladder offers leaves at least the minimum station`() {
        for (target in listOf(at(2, 125, 6), at(2, 128, 5), at(3, 125, 5), at(4, 1, 1))) {
            for (level in 0..4) {
                for (window in FleetBalance.windowsFor(home, target, drive(level), FleetBalance.FASTEST_HULL)) {
                    assertTrue(
                        FleetBalance.stationFor(home, target, window, drive(level), FleetBalance.FASTEST_HULL) >= FleetBalance.MINIMUM_STATION,
                        "window $window to $target at drive $level left too little station",
                    )
                }
            }
        }
    }

    @Test
    fun `flight eats the window rather than extending it`() {
        // The shape decision of the whole mechanic: a far world delivers fewer station-hours out of
        // the same absence, so it has to be richer to be worth it.
        assertEquals(158.minutes, FleetBalance.stationFor(home, at(2, 125, 6), 3.hours, NONE, FleetBalance.FASTEST_HULL))
        assertEquals(120.minutes, FleetBalance.stationFor(home, at(2, 126, 5), 3.hours, NONE, FleetBalance.FASTEST_HULL))
        assertTrue(
            FleetBalance.stationFor(home, at(3, 125, 5), 24.hours, NONE, FleetBalance.FASTEST_HULL) <
                FleetBalance.stationFor(home, at(2, 125, 6), 24.hours, NONE, FleetBalance.FASTEST_HULL),
        )
    }

    @Test
    fun `what the drive really buys is station time and most of it at the frontier`() {
        // The pay-off the speed change converts into cargo, and the reason the sheet calls the drive
        // a *reach* technology: at the frontier a level does not shave minutes off a trip, it turns a
        // window that was almost all flight into one that is mostly work.
        val far = at(3, 125, 5)

        assertEquals(340.minutes, FleetBalance.stationFor(home, far, 24.hours, NONE, FleetBalance.FASTEST_HULL))
        assertEquals(880.minutes, FleetBalance.stationFor(home, far, 24.hours, drive(1), FleetBalance.FASTEST_HULL))
        assertEquals(1_060.minutes, FleetBalance.stationFor(home, far, 24.hours, drive(2), FleetBalance.FASTEST_HULL))
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
        //
        // **At drive 1, which is where the sheet's arithmetic lives.** This row was computed against
        // the flight curve the game shipped with, and drive 1 *is* that curve to the minute — so
        // holding the level here is what keeps the worked example a check on `cargo` rather than a
        // check on `flight`, which has its own section above.
        val target = home.copy(slot = 6)
        val rich = world(target, metalPerMillion = 1_240_000, hazards = emptySet())
        val station = FleetBalance.stationFor(home, target, 3.hours, drive(1), FleetBalance.FASTEST_HULL)
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

    // ── The fleet the deposit is worth sending ───────────────────────────────────────────────

    @Test
    fun `the fleet that empties a vein is the smallest one whose hold covers it`() {
        // **Derived from `cargo`'s own expression rather than from a second rate**, exactly as
        // `DepositBalance.workingTime` is — so the two can never disagree about a hull. The property
        // is the definition: this many hulls take everything there is and one fewer does not.
        val target = home.copy(slot = 6)
        val rich = world(target, metalPerMillion = 1_240_000, hazards = emptySet())
        fun lift(hulls: Int): Long =
            FleetBalance.cargo(rich, ResourceKind.METAL, Ships.of(ShipType.SKIFF, hulls), 160.minutes, 2, NONE).metal

        for (remaining in listOf(1L, 100L, 1_000L, 4_321L, 12_000L, 25_000L)) {
            val hulls = assertNotNull(
                FleetBalance.hullsToLift(rich, ResourceKind.METAL, remaining, 160.minutes, 2, NONE),
            )
            assertTrue(lift(hulls) >= remaining, "$hulls hulls lift ${lift(hulls)} of $remaining")
            assertTrue(hulls == 1 || lift(hulls - 1) < remaining, "${hulls - 1} hulls already lift $remaining")
        }
    }

    @Test
    fun `a vein with nothing left in it takes one hull rather than none`() {
        // One is the floor because a fleet of nothing is not an offer. The dispatch sheet opens on
        // this number on a stripped world — which is what makes its countdown the soonest one there
        // is rather than a date no world will ever reach.
        val target = home.copy(slot = 6)
        val rich = world(target, metalPerMillion = 1_240_000, hazards = emptySet())

        assertEquals(1, FleetBalance.hullsToLift(rich, ResourceKind.METAL, 0, 160.minutes, 0, NONE))
    }

    @Test
    fun `a window that leaves no time on the surface is one no fleet can empty`() {
        // Null rather than a number, because the answer is *none of them*: `cargo` is zero at every
        // fleet size when the station is, so any figure here would be a lie with a plausible face.
        val target = home.copy(slot = 6)
        val rich = world(target, metalPerMillion = 1_240_000, hazards = emptySet())

        assertNull(FleetBalance.hullsToLift(rich, ResourceKind.METAL, 500, 0.minutes, 0, NONE))
        assertNull(FleetBalance.hullsToLift(rich, ResourceKind.METAL, 500, (-30).minutes, 0, NONE))
    }

    @Test
    fun `a longer window and a richer world both want fewer hulls`() {
        // The two reasons the sheet re-derives the number when a rung is tapped: the same vein wants
        // a smaller fleet the longer the fleet is allowed to stay.
        val target = home.copy(slot = 6)
        val poor = world(target, metalPerMillion = 600_000, hazards = emptySet())
        val rich = world(target, metalPerMillion = 1_600_000, hazards = emptySet())
        fun hulls(on: World, station: Duration): Int? =
            FleetBalance.hullsToLift(on, ResourceKind.METAL, 8_000, station, 0, NONE)

        assertTrue(hulls(poor, 160.minutes)!! > hulls(poor, 700.minutes)!!)
        assertTrue(hulls(poor, 160.minutes)!! > hulls(rich, 160.minutes)!!)
    }

    @Test
    fun `no fleet is ever sent to gather deuterium`() {
        val target = home.copy(slot = 6)
        val rich = world(target, metalPerMillion = 1_240_000, hazards = emptySet())

        assertFailsWith<IllegalArgumentException> {
            FleetBalance.hullsToLift(rich, ResourceKind.DEUTERIUM, 500, 160.minutes, 0, NONE)
        }
    }

    // ── The hull ────────────────────────────────────────────────────────────────────────────

    @Test
    fun `the hull price is the published base and nothing else`() {
        // **Flat — Davide's call, 2026-08-14**, replacing the x1.5-per-hull curve this file pinned
        // rung by rung from 0.3.0 to 0.9.0. A hull costs what a hull costs; the fleet a player
        // already owns is not an input to it.
        assertEquals(Resources.of(metal = 800, crystal = 200), FleetBalance.shipCost(ShipType.SKIFF))
        assertEquals(FleetBalance.HULL_BASE_METAL, FleetBalance.shipCost(ShipType.SKIFF).metal)
        assertEquals(FleetBalance.HULL_BASE_CRYSTAL, FleetBalance.shipCost(ShipType.SKIFF).crystal)
    }

    @Test
    fun `a hull is never priced in deuterium`() {
        // Metal-led for `SurveyBalance`'s own reason — metal is the resource with nothing to buy —
        // and the deuterium column stays empty because deuterium is the Robotics gate's currency.
        assertEquals(0L, FleetBalance.shipCost(ShipType.SKIFF).deuterium)
    }

    @Test
    fun `the scout is the cheapest hull in the game and the first one a colony can afford`() {
        // **Davide's call, 2026-08-21: 200 metal / 50 crystal.** It is an *opening* constant rather
        // than a mid-game one, and what sizes it is the genesis stock — a colony owns no hulls and
        // buys this one first, so a price it cannot reach in the first check-in is an empty Galaxy
        // tab for as long as it takes to earn one.
        assertEquals(Resources.of(metal = 200, crystal = 50), FleetBalance.shipCost(ShipType.SCOUT))

        val opening = GameState.initial().resources
        assertTrue(
            opening.covers(FleetBalance.shipCost(ShipType.SCOUT)),
            "a genesis colony cannot afford the hull it has to buy first: $opening",
        )
    }

    @Test
    fun `a scout is a quarter of a skiff in priced units`() {
        // The ratio is the legible half of the number: 300 priced units against the skiff's 1,200, at
        // the 1 : 2 the hold is already paid in. Pinned because the two prices are one decision — a
        // scout that drifts towards a skiff stops being the thing you buy before anything else.
        fun priced(type: ShipType): Long = FleetBalance.shipCost(type).let { it.metal + 2 * it.crystal }

        assertEquals(300L, priced(ShipType.SCOUT))
        assertEquals(1_200L, priced(ShipType.SKIFF))
        assertEquals(priced(ShipType.SKIFF), 4 * priced(ShipType.SCOUT))
    }

    @Test
    fun `the hauler is three skiffs of price for four skiffs of hold`() {
        // **Davide's call, 2026-08-21, and it is a re-decision rather than a first one.** The
        // 2026-08-10 ruling priced it at 1,000 / 250 on its own x1.5 curve; 0.10.1 made hull prices
        // flat and 0.9.0 had already raised the skiff base tenfold, so against today's 800 / 200 that
        // number would be **1.25x a skiff for four berths** — which deletes the skiff outright.
        //
        // Three, and the ratio is the decision: four berths for the price of three hulls is a 25%
        // discount on hold, paid for in half speed and in putting everything in one basket. Four
        // would make the hauler strictly worse than the four skiffs it replaces — same hold, half
        // speed, no splitting across targets — so nobody would ever buy one.
        assertEquals(Resources.of(metal = 2_400, crystal = 600), FleetBalance.shipCost(ShipType.HAULER))

        fun priced(type: ShipType): Long = FleetBalance.shipCost(type).let { it.metal + 2 * it.crystal }
        assertEquals(3 * priced(ShipType.SKIFF), priced(ShipType.HAULER))
    }

    @Test
    fun `the yard takes longer over a hauler than over a skiff`() {
        // The wait is taken from the price and the price is flat, so this follows rather than being
        // chosen — 4 x root(3,000) against 4 x root(1,000). Pinned because it is the second half of
        // what a hauler costs and the only part of it that is not money.
        assertEquals(216.minutes, FleetBalance.buildDuration(ShipType.HAULER, roboticsFactory = BuildingLevel(0)))
        assertTrue(
            FleetBalance.buildDuration(ShipType.HAULER, BuildingLevel(0)) >
                FleetBalance.buildDuration(ShipType.SKIFF, BuildingLevel(0)),
        )
    }

    @Test
    fun `the hulls with no job yet have no price`() {
        // Each waits on exactly one design call — the escort on a combat model, the settler on
        // colonisation — and a made-up price for one of them would be a number nobody chose sitting
        // in a balance object that forbids exactly that.
        for (type in listOf(ShipType.ESCORT, ShipType.SETTLER)) {
            assertFailsWith<IllegalStateException> { FleetBalance.shipCost(type) }
        }
    }

    // ── The hauler's clock, and the manifest that sets it ────────────────────────────────────

    @Test
    fun `the design's own frames reproduce to the minute`() {
        // **Claude Design, *Twice the Flight*, checked against its published figures rather than its
        // prose.** Both frames are drawn at drive 1, which is the speed 0.14 shipped at and the
        // curve the design had — so the level is held here and the numbers are its own:
        //
        // | target | skiffs | with a hauler |
        // |---|---|---|
        // | the doorstep, 5 units | 20m out and back | 42m |
        // | Ashkur IX, 440 units | 1h 48m | 3h 36m |
        val skiffs = Ships.of(ShipType.SKIFF, 2)
        val mixed = Ships(mapOf(ShipType.HAULER to 1, ShipType.SKIFF to 2))
        val doorstep = home.copy(slot = 6)
        val ashkur = home.copy(system = home.system + 69, slot = 1)
        assertEquals(440, FleetBalance.distanceUnits(home, ashkur))

        assertEquals(20.minutes, FleetBalance.roundTrip(home, doorstep, drive(1), skiffs))
        assertEquals(42.minutes, FleetBalance.roundTrip(home, doorstep, drive(1), mixed))
        assertEquals(108.minutes, FleetBalance.roundTrip(home, ashkur, drive(1), skiffs))
        assertEquals(216.minutes, FleetBalance.roundTrip(home, ashkur, drive(1), mixed))
    }

    @Test
    fun `a manifest has one clock and the slowest hull sets it`() {
        // **The fact the whole picker is built on** — Design: *"the run has one clock with two
        // settings and never more, however many hulls go."* One hauler among ten skiffs flies the
        // hauler's clock, which is what lets a run store one `returnsAt`.
        val target = home.copy(system = home.system + 30, slot = 1)
        val skiffs = Ships.of(ShipType.SKIFF, 10)
        val one = Ships(mapOf(ShipType.HAULER to 1, ShipType.SKIFF to 10))
        val many = Ships(mapOf(ShipType.HAULER to 4, ShipType.SKIFF to 10))

        assertEquals(
            FleetBalance.flight(home, target, NONE, one),
            FleetBalance.flight(home, target, NONE, many),
            "a second hauler is not slower than the first",
        )
        assertTrue(FleetBalance.flight(home, target, NONE, one) > FleetBalance.flight(home, target, NONE, skiffs))
    }

    @Test
    fun `the hauler is about double rather than exactly double and the base term is why`() {
        // The design's prose says exactly and its own doorstep frame says 2.1x — 20m against 42m,
        // where the flat ten minutes doubles and the distance rounds away to nothing. Pinned as the
        // discrepancy it is, because a later reader will meet the sentence before the frame.
        val doorstep = home.copy(slot = 6)
        val far = home.copy(galaxy = home.galaxy + 1)
        fun ratio(to: GalaxyCoordinate): Double {
            val skiff = FleetBalance.flight(home, to, drive(1), Ships.of(ShipType.SKIFF, 1))
            val hauler = FleetBalance.flight(home, to, drive(1), Ships.of(ShipType.HAULER, 1))
            return hauler.inWholeMinutes.toDouble() / skiff.inWholeMinutes
        }

        assertEquals(2.1, ratio(doorstep))
        // ...and it converges on two as the distance term swamps the base.
        assertTrue(ratio(far) < 2.001, "a galaxy hop should be within a rounding error of double")
    }

    @Test
    fun `the drive speeds a hauler and a skiff by the same proportion`() {
        // The two changes are orthogonal by construction — the factor scales the whole flight and the
        // drive scales the distance term — so a level is worth the same *share* of a trip to either
        // hull. A drive that favoured one would be a second composition dial nobody designed.
        val target = home.copy(galaxy = home.galaxy + 1)
        val skiff = Ships.of(ShipType.SKIFF, 1)
        val hauler = Ships.of(ShipType.HAULER, 1)

        assertEquals(
            FleetBalance.flight(home, target, drive(1), skiff) * 2,
            FleetBalance.flight(home, target, drive(1), hauler),
        )
        assertEquals(
            FleetBalance.flight(home, target, drive(3), skiff) * 2,
            FleetBalance.flight(home, target, drive(3), hauler),
        )
    }

    @Test
    fun `a hauler narrows the ladder where a skiff still fits`() {
        // Design §2, and the state the whole locked-rung idiom exists for: at 69 systems out a
        // skiff's 1h 48m fits the 3h rung and a hauler's 3h 36m does not.
        val ashkur = home.copy(system = home.system + 69, slot = 1)
        val skiffs = FleetBalance.windowsFor(home, ashkur, drive(1), Ships.of(ShipType.SKIFF, 2))
        val mixed = FleetBalance.windowsFor(home, ashkur, drive(1), Ships(mapOf(ShipType.HAULER to 1)))

        assertTrue(3.hours in skiffs)
        assertTrue(3.hours !in mixed)
        assertTrue(6.hours in mixed, "the hauler's shortest rung there is 6h")
    }

    // ── The reachable manifests ──────────────────────────────────────────────────────────────

    @Test
    fun `the hold climbs one two four five six at one hauler and two skiffs`() {
        // **The design's own list**, and the gaps are the point: a hauler is four berths and it does
        // not divide, so there is no three-berth manifest to offer.
        val idle = Ships(mapOf(ShipType.HAULER to 1, ShipType.SKIFF to 2))

        assertEquals(listOf(1, 2, 4, 5, 6), FleetBalance.reachableManifests(idle).map { it.berths })
    }

    @Test
    fun `where two manifests carry the same hold the hauler-first one wins`() {
        // Four berths is one hauler or four skiffs, and the rule packs the hauler — which is what
        // keeps the skiffs at home for the second target the sheet cannot see.
        val idle = Ships(mapOf(ShipType.HAULER to 1, ShipType.SKIFF to 4))

        val atFour = FleetBalance.reachableManifests(idle).first { it.berths == 4 }
        assertEquals(1, atFour.ships.countOf(ShipType.HAULER))
        assertEquals(0, atFour.ships.countOf(ShipType.SKIFF))
    }

    @Test
    fun `every reachable manifest is one the pool can actually cover`() {
        val idle = Ships(mapOf(ShipType.HAULER to 2, ShipType.SKIFF to 3))

        for (manifest in FleetBalance.reachableManifests(idle)) {
            assertTrue(idle.covers(manifest.ships), "the pool cannot cover ${manifest.ships}")
            assertTrue(!manifest.ships.isEmpty, "an empty manifest is not an offer")
        }
    }

    @Test
    fun `a pool with nothing that gathers reaches no manifest at all`() {
        // The refusal the sheet draws is this emptiness rather than a count — a colony whose hulls
        // are all out, or all scouts, has nothing to offer and says so once.
        assertEquals(emptyList(), FleetBalance.reachableManifests(Ships.NONE))
        assertEquals(emptyList(), FleetBalance.reachableManifests(Ships.of(ShipType.SCOUT, 4)))
    }

    // ── Berths ───────────────────────────────────────────────────────────────────────────────

    @Test
    fun `a hold is counted in berths rather than in hulls`() {
        // The ship set's own table, as a number: a skiff is one berth, a hauler is four, and a scout
        // has no hold at all. Until the hauler this was `ships.total` and the two were the same
        // thing — which is exactly the kind of coincidence that stops being true without a test.
        assertEquals(1, FleetBalance.berths(Ships.of(ShipType.SKIFF, 1)))
        assertEquals(3, FleetBalance.berths(Ships.of(ShipType.SKIFF, 3)))
        assertEquals(4, FleetBalance.berths(Ships.of(ShipType.HAULER, 1)))
        assertEquals(0, FleetBalance.berths(Ships.of(ShipType.SCOUT, 9)))
        assertEquals(6, FleetBalance.berths(Ships(mapOf(ShipType.SKIFF to 2, ShipType.HAULER to 1))))
    }

    @Test
    fun `one hauler lifts exactly what four skiffs lift`() {
        // **The composition axis, in the one number it turns on.** The hauler trades speed for hold
        // and nothing else, so at a fixed station time the two manifests are worth the same — what
        // separates them is what the flight costs, which is the picker's slice.
        val target = home.copy(slot = 6)
        val rock = world(target, metalPerMillion = 1_000_000, hazards = emptySet())
        fun lift(ships: Ships): Long = FleetBalance.cargo(
            world = rock,
            gathering = ResourceKind.METAL,
            ships = ships,
            station = 6.hours,
            danger = 0,
            research = NONE,
        ).metal

        assertEquals(lift(Ships.of(ShipType.SKIFF, 4)), lift(Ships.of(ShipType.HAULER, 1)))
        assertTrue(lift(Ships.of(ShipType.HAULER, 1)) > lift(Ships.of(ShipType.SKIFF, 3)))
    }

    @Test
    fun `a scout in a manifest adds no hold`() {
        // It cannot reach `cargo` from `startRun`, which refuses the manifest at the door — but the
        // arithmetic must not be the thing standing between a scout and a berth it does not have.
        val target = home.copy(slot = 6)
        val rock = world(target, metalPerMillion = 1_000_000, hazards = emptySet())
        fun lift(ships: Ships): Long = FleetBalance.cargo(
            world = rock,
            gathering = ResourceKind.METAL,
            ships = ships,
            station = 6.hours,
            danger = 0,
            research = NONE,
        ).metal

        assertEquals(
            lift(Ships.of(ShipType.SKIFF, 2)),
            lift(Ships(mapOf(ShipType.SKIFF to 2, ShipType.SCOUT to 5))),
        )
    }

    // ── The yard clock ───────────────────────────────────────────────────────────────────────

    @Test
    fun `a hull takes four minutes per root of its own price`() {
        // The colony's own rule, `PlaceholderBalance.MINUTES_PER_ROOT_COST`, applied to the hull's
        // price — so a hull and a facility that cost the same take the same time to make. With a flat
        // price that is 2h 04m a hull, at every depth: 4 x root(1,000).
        val cost = FleetBalance.shipCost(ShipType.SKIFF)
        val expected = 4 * integerRoot(cost.metal + cost.crystal)

        assertEquals(expected.minutes, 124.minutes)
        assertEquals(
            expected.minutes,
            FleetBalance.buildDuration(ShipType.SKIFF, roboticsFactory = BuildingLevel(0)),
        )
    }

    @Test
    fun `the Robotics Factory divides the yard's clock the way it divides the colony's`() {
        // Davide's call, 2026-08-13. The fleet is gated by nothing, so this is not a requirement —
        // it is the one building that answers a wait the player is already serving. **And since the
        // price went flat it is the only thing in the game that answers the wait at all**, which is
        // what makes the factory the fleet's pace rather than a discount on it.
        val alone = FleetBalance.buildDuration(ShipType.SKIFF, roboticsFactory = BuildingLevel(0))

        for (level in 1..8) {
            assertEquals(
                alone / (1 + level),
                FleetBalance.buildDuration(ShipType.SKIFF, roboticsFactory = BuildingLevel(level)),
                "Robotics $level did not divide the wait",
            )
        }
    }

    @Test
    fun `no hull is ever instant however deep the factory goes`() {
        // A zero-duration job completes at its own boundary, re-enters `advance` at the same instant
        // and recurses forever — the reason `MINIMUM_STATION` exists one section up, for the reason
        // it exists here.
        //
        // **This is no longer unreachable in play, and that is the price of a flat clock.** At the
        // compounding wait a factory past level 30 was needed to touch this floor; at 2h 04m flat,
        // Robotics 25 reaches it, and every level past that buys nothing. The floor is what the yard
        // converges on rather than a guard nobody trips.
        for (level in 0..60) {
            assertTrue(
                FleetBalance.buildDuration(ShipType.SKIFF, roboticsFactory = BuildingLevel(level)) >=
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
        for (type in listOf(ShipType.ESCORT, ShipType.SETTLER)) {
            assertFailsWith<IllegalStateException> {
                FleetBalance.buildDuration(type, roboticsFactory = BuildingLevel(0))
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
        hasRing = false,
    )
}
