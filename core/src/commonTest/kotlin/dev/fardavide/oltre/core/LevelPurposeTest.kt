package dev.fardavide.oltre.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes

class LevelPurposeTest {

    // ── What a level hands you ───────────────────────────────────────────────────────────────

    @Test
    fun `a mine hands you the resource it mines`() {
        // given a colony with power to spare for any of the three
        val state = powered()

        // then each mine names its own resource and no other
        assertEquals(ResourceKind.METAL, output(state, BuildingType.METAL_MINE).kind)
        assertEquals(ResourceKind.CRYSTAL, output(state, BuildingType.CRYSTAL_MINE).kind)
        assertEquals(ResourceKind.DEUTERIUM, output(state, BuildingType.DEUTERIUM_SYNTHESIZER).kind)
    }

    @Test
    fun `a level the plant cannot carry says so instead of selling itself`() {
        // given genesis exactly as the game deals it: one plant supplying 50 against 40 drawn, and
        // a synthesizer level that would draw 20 more
        val state = surplus()

        // then the level is not a small gain — it is a loss, because the deficit it opens scales
        // every mine on the colony
        val purpose = assertIs<LevelPurpose.Throttled>(
            state.purposeOfNextLevel(BuildingType.DEUTERIUM_SYNTHESIZER),
        )

        // and it names the plant level that would carry the new draw
        assertTrue(
            PlaceholderBalance.energySupply(
                BuildingType.SOLAR_PLANT,
                BuildingLevel(purpose.coveredAtPlantLevel),
                state.research,
            ) >= PlaceholderBalance.energyConsumption(
                state.buildings.withLevel(
                    BuildingType.DEUTERIUM_SYNTHESIZER,
                    BuildingLevel(state.buildings.deuteriumSynthesizer.value + 1),
                ),
            ),
            "plant ${purpose.coveredAtPlantLevel} does not carry it",
        )
    }

    @Test
    fun `one more plant level turns that same row back into an income row`() {
        // given the colony above with the plant the previous test asked for
        val state = surplus().let { it.copy(buildings = it.buildings.withLevel(BuildingType.SOLAR_PLANT, BuildingLevel(2))) }

        // then
        assertEquals(ResourceKind.DEUTERIUM, output(state, BuildingType.DEUTERIUM_SYNTHESIZER).kind)
    }

    @Test
    fun `the rate it hands you is the rate the simulation will actually accrue`() {
        // given
        val state = surplus()
        val building = BuildingType.METAL_MINE
        val raised = state.buildings.withLevel(building, BuildingLevel(state.buildings.metalMine.value + 1))

        // then the delta is computed against the same function `advance` accrues with, not against
        // the raw mine curve — which is what makes a throttled colony state a throttled number
        assertEquals(
            PlaceholderBalance.effectiveMetalProductionPerHour(raised, state.research) -
                PlaceholderBalance.effectiveMetalProductionPerHour(state.buildings, state.research),
            output(state, building).perHour,
        )
    }

    @Test
    fun `payback is the priced cost over the priced gain`() {
        // given
        val state = surplus()
        val building = BuildingType.CRYSTAL_MINE
        val toLevel = BuildingLevel(state.buildings.crystalMine.value + 1)

        // when
        val purpose = output(state, building)

        // then recomputed independently, in the game's own 1 : 2 : 3
        val raised = state.buildings.withLevel(building, toLevel)
        val gain = PlaceholderBalance.effectiveCrystalProductionPerHour(raised, state.research) -
            PlaceholderBalance.effectiveCrystalProductionPerHour(state.buildings, state.research)
        assertEquals(
            (PlaceholderBalance.upgradeCost(building, toLevel).priced() * 60 / (2 * gain)).minutes,
            purpose.payback,
        )
    }

    @Test
    fun `a deeper level takes longer to pay for itself than a shallow one`() {
        // given the same colony twice, with one mine six levels deeper on the second
        val shallow = surplus()
        val deep = shallow.copy(
            buildings = shallow.buildings.withLevel(BuildingType.METAL_MINE, BuildingLevel(18)),
        )

        // then cost compounds faster than output does, which is the whole shape of the curve
        assertTrue(
            output(deep, BuildingType.METAL_MINE).payback > output(shallow, BuildingType.METAL_MINE).payback,
        )
    }

    @Test
    fun `the first applied technology pays for itself inside a day`() {
        // given a colony that has just opened the research tab
        val state = surplus()

        // then — the balance sheet's own promise, restated where the screen will read it
        assertTrue(
            output(state, Technology.EXTRACTION).payback < 24.hours,
            "extraction pays back in ${output(state, Technology.EXTRACTION).payback}",
        )
    }

    // ── What buys nothing ────────────────────────────────────────────────────────────────────

    @Test
    fun `the solar plant buys nothing while the colony has power to spare`() {
        // given a colony in surplus
        val state = surplus()

        // then supply is not what is limiting it — the level raises a term nothing divides by
        val purpose = assertIs<LevelPurpose.Inert>(state.purposeOfNextLevel(BuildingType.SOLAR_PLANT))
        assertTrue(purpose.suppliesMore > 0, "a plant level still supplies something")
        assertEquals(
            PlaceholderBalance.energyHeadroomLevels(state.buildings, state.research),
            purpose.mineLevelsSpare,
        )
    }

    @Test
    fun `photovoltaics buys nothing while the colony has power to spare`() {
        // given
        val state = surplus()

        // then the same reading as the plant beside it: a colony in surplus is told twice that
        // supply is not its problem
        assertIs<LevelPurpose.Inert>(state.purposeOfNextLevel(Technology.PHOTOVOLTAICS))
    }

    @Test
    fun `the solar plant raises every rate once the colony is throttled`() {
        // given a colony drawing more than it makes
        val state = throttled()

        // then a plant level is an income row like any other, because the deficit scales all three
        val purpose = output(state, BuildingType.SOLAR_PLANT)
        assertTrue(purpose.perHour > 0)
        assertTrue(purpose.payback > Duration.ZERO)
    }

    @Test
    fun `photovoltaics raises every rate once the colony is throttled`() {
        // given
        val state = throttled()

        // then it multiplies supply, and supply is what the mines are being divided by
        assertTrue(output(state, Technology.PHOTOVOLTAICS).perHour > 0)
    }

    // ── What is worth time rather than income ────────────────────────────────────────────────

    @Test
    fun `the robotics factory raises no rate and shortens the longest build instead`() {
        // given a colony deep enough to have builds worth shortening
        val state = surplus().let { it.copy(buildings = it.buildings.withLevel(BuildingType.METAL_MINE, BuildingLevel(12))) }

        // when
        val purpose = assertIs<LevelPurpose.Sooner>(state.purposeOfNextLevel(BuildingType.ROBOTICS_FACTORY))

        // then it names a build and takes time off it
        assertTrue(purpose.after < purpose.before, "${purpose.on}: ${purpose.before} then ${purpose.after}")
        assertEquals(
            PlaceholderBalance.upgradeDuration(
                purpose.on,
                BuildingLevel(state.buildings.levelOf(purpose.on).value + 1),
                state.buildings.roboticsFactory,
                state.buildings.naniteFactory,
            ),
            purpose.before,
        )
    }

    @Test
    fun `the build it names is the longest one this colony could start`() {
        // given
        val state = surplus().let { it.copy(buildings = it.buildings.withLevel(BuildingType.METAL_MINE, BuildingLevel(12))) }

        // when
        val purpose = assertIs<LevelPurpose.Sooner>(state.purposeOfNextLevel(BuildingType.ROBOTICS_FACTORY))

        // then nothing the colony can start right now takes longer than the one it named
        val startable = BuildingType.entries.filter { it != BuildingType.NANITE_FACTORY }
        for (building in startable) {
            val next = BuildingLevel(state.buildings.levelOf(building).value + 1)
            assertTrue(
                PlaceholderBalance.upgradeDuration(
                    building,
                    next,
                    state.buildings.roboticsFactory,
                    state.buildings.naniteFactory,
                ) <= purpose.before,
                "$building is longer than the ${purpose.on} the row named",
            )
        }
    }

    @Test
    fun `the nanite factory is a build saving too once it can be built at all`() {
        // given a colony that has cleared the gate
        val state = surplus().let {
            it.copy(
                buildings = it.buildings
                    .withLevel(BuildingType.ROBOTICS_FACTORY, BuildingLevel(PlaceholderBalance.NANITE_ROBOTICS_REQUIREMENT))
                    .withLevel(BuildingType.METAL_MINE, BuildingLevel(20)),
            )
        }

        // then
        val purpose = assertIs<LevelPurpose.Sooner>(state.purposeOfNextLevel(BuildingType.NANITE_FACTORY))
        assertTrue(purpose.after < purpose.before)
    }

    @Test
    fun `a locked nanite factory still says what it would be worth`() {
        // when — asked with no colony at all, which is the point: this is a fact about the building
        val relief = deepBuildRelief()

        // then the late-game wait and the answer to it are both stated
        assertTrue(relief.unaided > relief.helped)
        assertTrue(relief.helped > Duration.ZERO)
    }

    @Test
    fun `the relief it promises is the one the balance actually gives`() {
        // when
        val relief = deepBuildRelief()

        // then recomputed against the balance rather than restated
        val atTheGate = BuildingLevel(PlaceholderBalance.NANITE_ROBOTICS_REQUIREMENT)
        assertEquals(
            PlaceholderBalance.upgradeDuration(
                BuildingType.METAL_MINE,
                BuildingLevel(relief.level),
                atTheGate,
                BuildingLevel(0),
            ),
            relief.unaided,
        )
        assertEquals(
            PlaceholderBalance.upgradeDuration(
                BuildingType.METAL_MINE,
                BuildingLevel(relief.level),
                atTheGate,
                BuildingLevel(relief.naniteLevel),
            ),
            relief.helped,
        )
    }

    // The reason it is quoted at the gate rather than at the colony: nobody builds a Nanite Factory
    // at Robotics 3, and a claim that halved every time an unrelated building went up would be a
    // headline about the reader rather than about the building.
    @Test
    fun `the relief does not move when the colony does`() {
        val early = surplus()
        val late = surplus().let {
            it.copy(buildings = it.buildings.withLevel(BuildingType.ROBOTICS_FACTORY, BuildingLevel(14)))
        }

        assertEquals(deepBuildRelief(), deepBuildRelief())
        assertTrue(early.buildings.roboticsFactory != late.buildings.roboticsFactory)
    }

    // ── The edges ────────────────────────────────────────────────────────────────────────────

    @Test
    fun `a technology at its ceiling has no next level to be worth anything`() {
        // given an empire that has taken a technology as far as the model allows
        val state = surplus().let {
            it.copy(research = it.research.withLevel(Technology.EXTRACTION, TechLevel(TechLevel.MAX)))
        }

        // then nothing is computed rather than a level that cannot be constructed
        assertEquals(LevelPurpose.Unmeasured, state.purposeOfNextLevel(Technology.EXTRACTION))
    }

    @Test
    fun `a facility at its ceiling has no next level either`() {
        // given
        val state = surplus().let {
            it.copy(
                buildings = it.buildings.withLevel(
                    BuildingType.METAL_MINE,
                    BuildingLevel(PlaceholderBalance.MAX_UPGRADE_LEVEL),
                ),
            )
        }

        // then
        assertEquals(LevelPurpose.Unmeasured, state.purposeOfNextLevel(BuildingType.METAL_MINE))
    }

    @Test
    fun `a colony with nothing standing is never divided by`() {
        // given no mines and no plant — every rate is zero and so is every gain
        val state = razed()

        // then the first mine level is not an income row either: with no plant at all it would run
        // at nothing. The row says what to build first rather than dividing by a zero.
        assertIs<LevelPurpose.Throttled>(state.purposeOfNextLevel(BuildingType.METAL_MINE))

        // and the plant that fixes it is the one row that is not throttled
        assertIs<LevelPurpose.Inert>(state.purposeOfNextLevel(BuildingType.SOLAR_PLANT))
    }

    @Test
    fun `every facility and every technology answers in every state the game can reach`() {
        // then no row on either screen is ever left with nothing to say — the exhaustiveness the
        // screens rely on, asserted rather than assumed
        for (state in listOf(surplus(), powered(), throttled(), razed())) {
            for (building in BuildingType.entries) {
                assertTrue(state.purposeOfNextLevel(building) != LevelPurpose.Unmeasured, "$building")
            }
            for (technology in Technology.entries) {
                assertTrue(state.purposeOfNextLevel(technology) != LevelPurpose.Unmeasured, "$technology")
            }
        }
    }

    // ── Fixtures ─────────────────────────────────────────────────────────────────────────────

    private fun output(state: GameState, building: BuildingType): LevelPurpose.Output =
        assertIs(state.purposeOfNextLevel(building), "$building")

    private fun output(state: GameState, technology: Technology): LevelPurpose.Output =
        assertIs(state.purposeOfNextLevel(technology), "$technology")

    // Genesis has one plant against three level-1 mines: 50 supplied against 40 drawn. Ten spare,
    // which carries one more mine level and not one more synthesizer level.
    private fun surplus(): GameState = GameState.initial()

    // The same colony with room for any of the three, which is what makes it the control.
    private fun powered(): GameState = GameState.initial().let {
        it.copy(buildings = it.buildings.withLevel(BuildingType.SOLAR_PLANT, BuildingLevel(4)))
    }

    // Two more mine levels than the plant can carry.
    private fun throttled(): GameState = GameState.initial().let {
        it.copy(
            buildings = it.buildings
                .withLevel(BuildingType.METAL_MINE, BuildingLevel(4))
                .withLevel(BuildingType.CRYSTAL_MINE, BuildingLevel(3)),
        )
    }

    private fun razed(): GameState = GameState.initial().let {
        it.copy(
            buildings = Buildings(
                metalMine = BuildingLevel(0),
                crystalMine = BuildingLevel(0),
                deuteriumSynthesizer = BuildingLevel(0),
                solarPlant = BuildingLevel(0),
                roboticsFactory = BuildingLevel(0),
                naniteFactory = BuildingLevel(0),
            ),
        )
    }
}
