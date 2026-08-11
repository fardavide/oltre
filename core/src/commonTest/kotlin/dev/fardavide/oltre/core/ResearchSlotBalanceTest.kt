package dev.fardavide.oltre.core

import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.hours
import kotlin.time.Instant

// **The one research slot, and whether both branches are still worth putting in it.**
//
// Six ladders — three applied technologies and three adaptation ladders — compete for a single
// empire-wide slot. `ResearchBalanceTest` and `AdaptationBalanceTest` pin both published tables
// value by value, which records what a level costs and asserts nothing about whether anyone would
// ever buy it. That is the gap this file is for.
//
// It matters because the slot is the whole design. `AdaptationBalance`'s own header: the branches
// *"compete for the same empire-wide slot the applied branch uses — so every adaptation level is
// paid for in production levels you did not buy."* A choice between two things is only a choice
// while both are live, and there are two ways for one to stop being live:
//
// - **it stops paying back** — an exponential cost against a compounding effect has a depth past
//   which no level is worth its own wait, and `ResearchBalance` says so in as many words: *"with a
//   linear effect and an exponential cost, payback doubles every level and the branch is dead by
//   level 4."*
// - **it stops being comparable** — the adaptation sheet prices a ladder level at *about twice* the
//   priciest applied technology, and that ratio is what keeps the two weighable rather than one
//   being obviously right.
//
// The second one has already broken once, in the wild, and is the reason this file exists rather
// than being a nice idea: the opening discount shipped reaching the applied branch and not the
// adaptation ladders, which took the step between them from the sheet's **1.9x** to **5.8x**. Every
// table test passed — both tables were still exactly what they were designed to be. It was the
// *relationship* between them that had gone, and nothing was looking at it.
class ResearchSlotBalanceTest {

    // The sheet's ratio, at every level a player will actually buy. Wide enough that re-pricing
    // either branch on purpose passes, narrow enough that a discount reaching one and not the other
    // fails — which is the exact regression, and it lands at 5.8 against this ceiling of 3.
    @Test
    fun `an adaptation level stays about twice the priciest applied technology`() {
        for (level in 1..LEVELS) {
            val dearest = Technology.entries.maxOf { priced(ResearchBalance.researchCost(it, TechLevel(level))) }
            for (ladder in AdaptationTechnology.entries) {
                val ratio = priced(AdaptationBalance.adaptationCost(ladder, TechLevel(level))) * 100 / dearest

                assertTrue(
                    ratio in 130..300,
                    "$ladder $level costs ${ratio / 100.0}x the priciest applied technology, was 1.92x",
                )
            }
        }
    }

    // The three ladders are priced identically on purpose — the sheet's argument is that *which one
    // you push first* must be a preference rather than a right answer, and identical priced totals
    // are what make it one. They are paid for in different currencies, which is the actual mechanic;
    // strip the currencies away and there is nothing to choose between them.
    @Test
    fun `the three ladders cost the same once priced`() {
        for (level in 1..LEVELS) {
            val costs = AdaptationTechnology.entries.map { priced(AdaptationBalance.adaptationCost(it, TechLevel(level))) }
            val spread = costs.max() - costs.min()

            assertTrue(
                spread * 1_000 / costs.min() <= 10,
                "at level $level the ladders cost ${costs.min()}..${costs.max()}, which is not the same price",
            )
        }
    }

    // The applied branch has to be worth the slot at the moment a player first meets it, or the
    // slot only ever holds adaptation and the branch that raises rates is decoration. Measured
    // against a real colony's rates rather than a table, because a multiplier is worth exactly what
    // the rate it multiplies is worth.
    @Test
    fun `the first applied technology pays for itself inside a day`() {
        val colony = colonyAfter(days = 3)
        val paybacks = Technology.entries.mapNotNull { technology ->
            val gain = gainOf(colony, technology, level = 1)
            if (gain <= 0) null else priced(ResearchBalance.researchCost(technology, TechLevel(1))) / gain
        }

        assertTrue(paybacks.isNotEmpty(), "no applied technology raises this colony's income at all")
        assertTrue(
            paybacks.min() <= 24,
            "the best first level takes ${paybacks.min()}h to pay for itself, was 1h",
        )
    }

    // The other end of the same rule. `ResearchBalance` says a branch whose payback doubles every
    // level *"is dead by level 4"*, so this is that sentence as a reading: at the depth a player
    // reaches in the first fortnight, some level of some technology must still be worth buying.
    //
    // Deliberately weak, and deliberately about the *best* row rather than every row. A technology
    // that has stopped paying back is allowed — that is the branch having a shape. A slot with
    // nothing in it worth taking is not.
    @Test
    fun `some applied level is still worth taking at the depth a fortnight reaches`() {
        val colony = colonyAfter(days = 14)
        val worthwhile = Technology.entries.any { technology ->
            val level = colony.research.levelOf(technology).value + 1
            val gain = gainOf(colony, technology, level)
            gain > 0 && priced(ResearchBalance.researchCost(technology, TechLevel(level))) / gain <= 168
        }

        assertTrue(worthwhile, "no applied technology pays back inside a week at fortnight depth")
    }

    // ── the instruments ──────────────────────────────────────────────────────────────────────

    // What the level itself adds to this colony's priced income — level-1 against level, never
    // against whatever the colony happens to hold, so a technology it has already pushed past does
    // not read as a loss.
    private fun gainOf(state: GameState, technology: Technology, level: Int): Long {
        fun at(value: Int): Long = priced(
            incomeOf(state.copy(research = state.research.withLevel(technology, TechLevel(value)))),
        )
        return at(level) - at(level - 1)
    }

    private fun incomeOf(state: GameState): Resources = Resources.of(
        metal = PlaceholderBalance.effectiveMetalProductionPerHour(state.buildings, state.research),
        crystal = PlaceholderBalance.effectiveCrystalProductionPerHour(state.buildings, state.research),
        deuterium = PlaceholderBalance.effectiveDeuteriumProductionPerHour(state.buildings, state.research),
    )

    // The same fixed player the other balance files use: every hour, buy the cheapest facility you
    // can afford, then fill the slot with the cheapest project. Written out rather than called into
    // `:sim` because `core` cannot see the harness.
    private fun colonyAfter(days: Int): GameState {
        var state = GameState.initial(TEST_GALAXY_SEED)
        var now = EPOCH
        for (hour in 0..(days * 24)) {
            val at = EPOCH + hour.hours
            state = advance(state, from = now, to = at)
            now = at
            for (building in PLAN.sortedBy { priced(PlaceholderBalance.upgradeCost(it, next(state, it))) }) {
                val cost = PlaceholderBalance.upgradeCost(building, next(state, building))
                if (!state.resources.covers(cost)) continue
                (startUpgrade(state, building, at = now) as? StartUpgradeResult.Started)?.let { state = it.state }
            }
            if (state.researchSlotFreesAt != null) continue
            for (technology in Technology.entries.sortedBy { priced(ResearchBalance.researchCost(it, nextTech(state, it))) }) {
                val started = (startResearch(state, technology, at = now) as? StartResearchResult.Started)?.state
                if (started != null) {
                    state = started
                    break
                }
            }
        }
        return state
    }

    private fun next(state: GameState, building: BuildingType): BuildingLevel =
        BuildingLevel(state.buildings.levelOf(building).value + 1)

    private fun nextTech(state: GameState, technology: Technology): TechLevel =
        TechLevel(state.research.levelOf(technology).value + 1)

    private fun priced(resources: Resources): Long =
        resources.metal + 2 * resources.crystal + 3 * resources.deuterium

    private companion object {

        val EPOCH: Instant = Instant.fromEpochMilliseconds(0)

        // As deep as the first fortnight reaches on either branch; past that the table is a design
        // for a game nobody has played yet and a band on it would be a guess.
        const val LEVELS: Int = 5

        val PLAN = listOf(
            BuildingType.METAL_MINE,
            BuildingType.CRYSTAL_MINE,
            BuildingType.DEUTERIUM_SYNTHESIZER,
            BuildingType.SOLAR_PLANT,
            BuildingType.ROBOTICS_FACTORY,
        )
    }
}
