package dev.fardavide.oltre.core

import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.hours
import kotlin.time.Instant

// **The colony past the opening.** `OpeningBalanceTest` pins day one and day two, which is where
// every balance round so far has looked, because that is where the complaints come from. Nothing
// looks at week two — and a cost curve that compounds at +50% against production compounding at
// +25% is a curve that *will* stall eventually, by construction. The only question is whether it
// stalls inside the fortnight a player is still around for.
//
// Round 11 is the standing decision these bands restate: duration was cut from the **root** of the
// cost precisely so that the wait would stop outrunning the earning, and `BalanceCurveTest` bounds
// that ratio at the level of the curve. This file asks the same question of a colony rather than of
// a formula — the two can disagree, because a colony also has six build slots, one research slot
// and a finite number of hours in which to use them.
//
// The bands are floors, not targets. A round that makes the late game faster passes every one of
// them; what fails is a change that quietly takes the *slope* out of week two.
class ProgressionBalanceTest {

    // The colony has to be visibly bigger after a fortnight than after a week, or the second week
    // is the game asking the player to watch a number that no longer moves.
    @Test
    fun `the colony is still growing in its second week`() {
        val week = levelsAt(7)
        val fortnight = levelsAt(14)

        assertTrue(
            fortnight >= week + 8,
            "day 14 added only ${fortnight - week} levels to day 7's $week, was 19",
        )
    }

    // Growth in levels can be bought by a curve that got cheaper without anything getting better,
    // so this is the same question in the unit that actually matters. Income compounds because
    // every level compounds; a change that flattens it has changed what the game is.
    @Test
    fun `income keeps compounding across the fortnight`() {
        val week = income(7)
        val fortnight = income(14)

        assertTrue(
            fortnight >= week * 2,
            "day 14 income is ${fortnight}/h against day 7's ${week}/h, was 3.19x",
        )
    }

    // Round 11's own subject, as a colony rather than as a formula: the middle of the game must not
    // become a wait. Measured over the whole fortnight and deliberately loose — the ceiling is the
    // shape of the failure, not a target, and round 16 measured 95.83% at 0.2.6 and called it the
    // biggest open item in the balance log.
    @Test
    fun `the colony is rarely left with nothing running`() {
        val states = fortnight()
        val idle = states.count { it.builds.isEmpty() && it.researchSlotFreesAt == null }

        assertTrue(
            idle * 100 / states.size <= 45,
            "the colony had nothing running for ${idle * 100 / states.size}% of the fortnight, was 14%",
        )
    }

    // A floor on the middle, so a change cannot buy week two by flattening week one. The three
    // readings together say the curve has slope at every scale a player sees, which is what neither
    // a table test nor a curve test can say.
    @Test
    fun `the colony does not stall between its third and seventh day`() {
        val third = levelsAt(3)
        val seventh = levelsAt(7)

        assertTrue(
            seventh >= third + 8,
            "day 7 added only ${seventh - third} levels to day 3's $third, was 15",
        )
    }

    // ── the instruments ──────────────────────────────────────────────────────────────────────

    private fun levelsAt(day: Int): Int =
        BuildingType.entries.sumOf { fortnight()[day * 24].buildings.levelOf(it).value }

    private fun income(day: Int): Long = fortnight()[day * 24].let { state ->
        PlaceholderBalance.effectiveMetalProductionPerHour(state.buildings, state.research) +
            2 * PlaceholderBalance.effectiveCrystalProductionPerHour(state.buildings, state.research) +
            3 * PlaceholderBalance.effectiveDeuteriumProductionPerHour(state.buildings, state.research)
    }

    // Memoised for the class rather than recomputed per test: 337 hourly steps is the most expensive
    // thing in this file, and on Kotlin/Native — where these also run — four copies of it is the
    // difference between a unit test and a nuisance.
    private fun fortnight(): List<GameState> = FORTNIGHT

    private companion object {

        val EPOCH: Instant = Instant.fromEpochMilliseconds(0)

        val PLAN = listOf(
            BuildingType.METAL_MINE,
            BuildingType.CRYSTAL_MINE,
            BuildingType.DEUTERIUM_SYNTHESIZER,
            BuildingType.SOLAR_PLANT,
            BuildingType.ROBOTICS_FACTORY,
        )

        // The same fixed player every balance file here uses: hourly, cheapest first, both branches.
        val FORTNIGHT: List<GameState> by lazy {
            var state = GameState.initial(TEST_GALAXY_SEED)
            var now = EPOCH
            buildList {
                for (hour in 0..(14 * 24)) {
                    val at = EPOCH + hour.hours
                    state = advance(state, from = now, to = at)
                    now = at
                    for (building in PLAN.sortedBy { priced(PlaceholderBalance.upgradeCost(it, next(state, it))) }) {
                        val cost = PlaceholderBalance.upgradeCost(building, next(state, building))
                        if (!state.resources.covers(cost)) continue
                        (startUpgrade(state, building, at = now) as? StartUpgradeResult.Started)?.let { state = it.state }
                    }
                    if (state.researchSlotFreesAt == null) {
                        for (technology in Technology.entries) {
                            val started = (startResearch(state, technology, at = now) as? StartResearchResult.Started)?.state
                            if (started != null) {
                                state = started
                                break
                            }
                        }
                    }
                    add(state)
                }
            }
        }

        fun next(state: GameState, building: BuildingType): BuildingLevel =
            BuildingLevel(state.buildings.levelOf(building).value + 1)

        fun priced(resources: Resources): Long =
            resources.metal + 2 * resources.crystal + 3 * resources.deuterium
    }
}
