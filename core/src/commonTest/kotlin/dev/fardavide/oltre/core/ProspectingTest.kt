package dev.fardavide.oltre.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.hours
import kotlin.time.Instant
import kotlin.time.Duration.Companion.minutes

// The fourth applied technology: the one whose payoff is measured in a run rather than in an hourly
// rate. It is what Davide asked for when he asked to *"power up my ship so it can gather more
// resources in the same time"* — and the sheet's §5 is careful about what it does and does not buy.
class ProspectingTest {

    private fun at(galaxy: Int, system: Int, slot: Int): GalaxyCoordinate =
        GalaxyCoordinate(galaxy = galaxy, system = system, slot = slot)

    @Test
    fun `a fresh empire has prospected nothing`() {
        assertEquals(TechLevel(0), Research.initial().levelOf(Technology.PROSPECTING))
    }

    @Test
    fun `it opens behind the extraction it is the away half of`() {
        // Not a third row live at Robotics 1: the branch is meant to open on one decision rather
        // than three, which is the reason Enrichment carries a second gate too. Learning to pull
        // more out of your own ground first is the chain a player can say out loud.
        assertEquals(
            ResearchRequirement.Tech(Technology.EXTRACTION, TechLevel(1)),
            ResearchBalance.requirementFor(Technology.PROSPECTING),
        )
    }

    @Test
    fun `a level lifts what every hull pulls out of a world`() {
        val world = world(at(2, 125, 8))
        val ships = Ships.of(ShipType.SKIFF, 4)
        val station = 6.hours

        val plain = FleetBalance.cargo(world, ResourceKind.METAL, ships, station, danger = 0, research = Research.initial())
        val researched = FleetBalance.cargo(
            world,
            ResourceKind.METAL,
            ships,
            station,
            danger = 0,
            research = Research.initial().withLevel(Technology.PROSPECTING, TechLevel(1)),
        )

        // +10% a level, compounding in the same curve family the other three sit in
        assertEquals(plain.metal * 11 / 10, researched.metal)
    }

    @Test
    fun `it makes the same window worth more rather than the window longer`() {
        // The want, stated as a test: more resources in the same time.
        val world = world(at(2, 125, 8))
        val ships = Ships.of(ShipType.SKIFF, 1)

        val five = Research.initial().withLevel(Technology.PROSPECTING, TechLevel(5))
        val plain = FleetBalance.cargo(world, ResourceKind.METAL, ships, 3.hours, danger = 0, research = Research.initial())
        val better = FleetBalance.cargo(world, ResourceKind.METAL, ships, 3.hours, danger = 0, research = five)

        assertTrue(better.metal > plain.metal, "${better.metal} against ${plain.metal}")
    }

    @Test
    fun `a better fleet empties a world faster rather than finding more in it`() {
        // The correction the sheet's §5 makes to its own first framing: this is not relief from
        // depletion. Where the binding constraint is stock, a faster hull drains the same vein
        // sooner — the cap does not move, only the time to reach it.
        val world = world(at(2, 125, 8))
        val ships = Ships.of(ShipType.SKIFF, 1)
        val researched = Research.initial().withLevel(Technology.PROSPECTING, TechLevel(1))

        val cap = DepositBalance.cap(world, ResourceKind.METAL, danger = 0)
        val plain = DepositBalance.workingTime(
            world = world,
            gathering = ResourceKind.METAL,
            ships = ships,
            danger = 0,
            remaining = cap,
            research = Research.initial(),
        )
        val faster = DepositBalance.workingTime(
            world = world,
            gathering = ResourceKind.METAL,
            ships = ships,
            danger = 0,
            remaining = cap,
            research = researched,
        )

        assertEquals(1_450.minutes, plain)
        assertTrue(faster < plain, "$faster against $plain")
    }

    @Test
    fun `working time still reads the same wherever the world is`() {
        // The invariant survives research: it is uniform across the map at any one level, which is
        // what lets the legs line teach it. What moves is the number, not its evenness.
        val researched = Research.initial().withLevel(Technology.PROSPECTING, TechLevel(3))
        val cases = listOf(
            world(at(2, 125, 8)) to 0,
            world(at(2, 200, 8), hazards = setOf(Hazard.ION_STORMS)) to 2,
            world(at(1, 125, 8), hazards = setOf(Hazard.ION_STORMS, Hazard.THIN_CRUST)) to 5,
        )

        val times = cases.map { (world, danger) ->
            DepositBalance.workingTime(
                world = world,
                gathering = ResourceKind.METAL,
                ships = Ships.of(ShipType.SKIFF, 1),
                danger = danger,
                remaining = DepositBalance.cap(world, ResourceKind.METAL, danger),
                research = researched,
            )
        }

        assertEquals(1, times.distinct().size, "working times differ across the map: $times")
    }

    @Test
    fun `a run started with the technology carries what the technology promised`() {
        val state = GameState.initial()
            .let { it.copy(ships = Ships.of(ShipType.SKIFF, 1)) }
        val target = state.galaxy.surveyed.first { it != state.galaxy.home }
        val researched = state.copy(research = state.research.withLevel(Technology.PROSPECTING, TechLevel(2)))

        val plain = started(state, target).runs.single().cargo.metal
        val better = started(researched, target).runs.single().cargo.metal

        assertTrue(better > plain, "$better against $plain")
    }

    @Test
    fun `it shares the one research slot rather than adding a second`() {
        // `AdaptationBalance`'s rule, unchanged by a fourth claimant: give a branch its own slot and
        // the answer is always "run both".
        val state = GameState.initial().copy(
            buildings = Buildings.initial().withLevel(BuildingType.ROBOTICS_FACTORY, BuildingLevel(4)),
            research = Research.initial().withLevel(Technology.EXTRACTION, TechLevel(1)),
            resources = Resources.of(metal = 1_000_000, crystal = 1_000_000, deuterium = 1_000_000),
        )
        val busy = startResearch(state, Technology.PROSPECTING, at = EPOCH)

        assertTrue(busy is StartResearchResult.Started)
        val second = startResearch(busy.state, Technology.PHOTOVOLTAICS, at = EPOCH)
        assertTrue(second is StartResearchResult.SlotBusy, "was $second")
    }

    private fun started(state: GameState, target: GalaxyCoordinate): GameState {
        val result = startRun(
            state = state,
            target = target,
            gathering = ResourceKind.METAL,
            ships = Ships.of(ShipType.SKIFF, 1),
            window = 3.hours,
            at = EPOCH,
        )
        return (result as StartRunResult.Started).state
    }

    private val EPOCH: Instant = Instant.fromEpochMilliseconds(0)

    private fun world(at: GalaxyCoordinate, hazards: Set<Hazard> = emptySet()): World = World(
        at = at,
        starClass = StarClass.STANDARD,
        traits = WorldTraits(
            temperature = Temperature(0),
            gravity = Gravity(1_000),
            pressure = Pressure(1_000),
            metalRichness = Richness(1_000_000),
            crystalRichness = Richness(1_000_000),
            deuteriumRichness = Richness(1_000_000),
            hazards = hazards,
            fields = 150,
        ),
        hasRing = false,
    )
}
