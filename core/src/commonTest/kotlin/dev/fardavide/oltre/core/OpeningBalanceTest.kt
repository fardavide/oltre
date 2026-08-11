package dev.fardavide.oltre.core

import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.hours
import kotlin.time.Instant

// **The opening, pinned the way a player meets it.**
//
// This file exists because 0.5.1 shipped a balance change that every test in the repository
// passed. `GalaxyDistributionTest` pins the *map* — how many worlds pass a band galaxy-wide — and
// it could not move, by construction, because the change altered no world's traits. `BalanceCurveTest`
// pins the *curves*. Neither of them knows what the first screen says, and the first screen is the
// only part of the map a new colony can see: genesis surveys the home system and nothing else.
//
// So the gap was not a missing assertion on an existing number. It was a whole quantity nobody was
// measuring: **what the opening costs a player**. The readings below are that quantity, and each
// one is a band rather than a value — a balance test that pins an exact number is a test that
// forbids tuning, which is the opposite of what this file is for. The bands are wide enough that a
// deliberate round of tuning passes and narrow enough that a change of *shape* fails.
//
// Every number here was measured with `:sim:run` before it was written down; the reports are
// `printDoorstepReport` and `printWholeHomeSystem`, and `balance-log.md` round 18 is the write-up.
// **If one of these fails, do not widen it to get green.** Read the sim report, decide whether the
// opening genuinely changed for the better, and move the band with a balance-log round that says
// what it bought.
class OpeningBalanceTest {

    // ── What the first screen says ───────────────────────────────────────────────────────────

    // The doorstep guarantee, from the player's side rather than the generator's: whatever else is
    // true of the map, a new colony can change a verdict in its own system for one adaptation level.
    @Test
    fun `almost every colony can open a neighbour with a single adaptation level`() {
        val within = openings().count { it.cheapest <= 1 }

        assertTrue(
            within * 100 / SEEDS >= 90,
            "expected >=90% of colonies to open a neighbour for one level, got ${within * 100 / SEEDS}%",
        )
    }

    // **The reading 0.5.1 shipped without.** The doorstep says how far the *cheapest* neighbour is;
    // a player reads the whole list. A rule that guaranteed a close neighbour by starting colonies
    // in systems whose other worlds were extreme would pass the test above and make the screen
    // worse, which is exactly what a player would report and no test would have caught.
    @Test
    fun `the neighbours that are not the doorstep do not get further away`() {
        val openings = openings()
        val second = openings.mapNotNull { it.others.getOrNull(1) }.median()
        val third = openings.mapNotNull { it.others.getOrNull(2) }.median()
        val overall = openings.flatMap { it.others }.median()

        // Measured at 8 / 13 / 12 under the rule that ships, against 12 / 15 / 14 under the one
        // before it. The ceilings sit *between* the two rather than on either: on the old readings
        // this test would pass a full regression, and on the new ones it would forbid tuning. Both
        // are ways of being useless, so the band is the gap between them.
        assertTrue(second <= 10, "the second cheapest neighbour is $second levels away, was 8")
        assertTrue(third <= 14, "the third cheapest neighbour is $third levels away, was 13")
        assertTrue(overall <= 13, "the median neighbour is $overall levels away, was 12")
    }

    // The screen has to hold something. A rule that met every distance target by putting the player
    // in a system with one neighbour would satisfy the two tests above and leave a Galaxy tab with
    // two rows on it.
    @Test
    fun `the home system holds enough worlds to be worth looking at`() {
        val worlds = openings().map { it.others.size }.median()

        assertTrue(worlds >= 3, "the median home system shows only $worlds non-home worlds, was 5")
    }

    // A band with a floor as well as a ceiling, and it is the only two-sided reading here.
    // `galaxy-sheet.md` is explicit that an easy world is a poor world and that `Barren` must be
    // the common answer, so an opening where most colonies wake up beside somewhere worth standing
    // has stopped being this game — that is the ceiling. The floor is the wall Davide reported: at
    // 96% (the reading before 0.5.1) all but one colony in twenty-five opens on six rows of BLOCKED
    // and no way to change any of them, which is the state this whole round existed to leave.
    @Test
    fun `most colonies still open on a screen where every neighbour is blocked`() {
        val walled = openings().count { it.others.isNotEmpty() && it.others.all { levels -> levels > 0 } }

        assertTrue(
            walled * 100 / SEEDS in 40..90,
            "expected 40-90% of colonies to open with every neighbour blocked, got ${walled * 100 / SEEDS}%",
        )
    }

    // ── What the opening costs in time ───────────────────────────────────────────────────────

    // The other half of Davide's 0.5.1 report was a clock — *"I needed 2 day to get robotics to
    // level 4"* — and nothing pinned it either. This is the gate as a wall-clock reading rather
    // than as a constant, so a change to the build curve, the discount or the gate itself that
    // pushes the branch back out of day one fails here rather than on a device.
    @Test
    fun `the adaptation branch opens on the first day`() {
        val opened = checkNotNull(hourReaching(AdaptationBalance.GATE)) {
            "a colony never reached the adaptation gate in $DAYS days"
        }

        assertTrue(opened <= 24, "the adaptation branch opened at hour $opened, was hour 12")
    }

    // The applied branch is the first thing a colony unlocks and the whole of the research tab
    // until the gate. If it slips out of the first session the opening has no second verb in it.
    @Test
    fun `the research tab opens within the first few hours`() {
        val opened = checkNotNull(hourReaching(BuildingLevel(1))) { "a colony never reached Robotics 1" }

        assertTrue(opened <= 12, "the research tab opened at hour $opened, was hour 6")
    }

    // Progression, coarsely. Not a target — a floor, so a change that quietly halves the rate of
    // the opening fails even if every curve still has the right shape.
    @Test
    fun `a colony is meaningfully further along on day two than on day one`() {
        val day1 = buildingLevelsAfter(days = 1)
        val day2 = buildingLevelsAfter(days = 2)

        assertTrue(day1 >= 14, "a colony had only $day1 building levels after a day, was 20")
        assertTrue(day2 >= day1 + 6, "day two added only ${day2 - day1} levels to day one's $day1, was 12")
    }

    // ── the instruments ──────────────────────────────────────────────────────────────────────

    // What one colony's opening screen holds: the levels every non-home world of its home system
    // is away from tolerable, cheapest first. Zero means a world the unaided species already
    // stands on, so it reads as a distance rather than as a verdict.
    private class Opening(val others: List<Int>) {
        val cheapest: Int get() = others.firstOrNull() ?: Int.MAX_VALUE
    }

    // Computed once for the whole class rather than per test. Genesis walks its galaxy for each
    // seed, so four tests asking the same question separately is four times the most expensive
    // thing in this file — and on Kotlin/Native, where these also run, that is the difference
    // between a unit test and a nuisance.
    private fun openings(): List<Opening> = OPENINGS

    // The check-in loop the sim's reports use, in miniature: a player who opens the game every
    // three hours and buys the cheapest thing they can afford. Deliberately its own loop rather
    // than a call into `:sim` — `core` cannot see the harness, and a bound that depended on the
    // harness's strategy would be pinning the strategy rather than the balance.
    private fun colonyEveryThreeHours(days: Int): Sequence<Pair<Int, GameState>> = sequence {
        var state = GameState.initial(TEST_GALAXY_SEED)
        var now = EPOCH
        for (hour in 0..(days * 24) step 3) {
            val at = EPOCH + hour.hours
            state = advance(state, from = now, to = at)
            now = at
            // Cheapest first, so the colony spreads rather than pouring everything into one row.
            for (building in PLAN.sortedBy { priced(PlaceholderBalance.upgradeCost(it, next(state, it))) }) {
                val cost = PlaceholderBalance.upgradeCost(building, next(state, it = building))
                if (!state.resources.covers(cost)) continue
                (startUpgrade(state, building, at = now) as? StartUpgradeResult.Started)?.let { state = it.state }
            }
            yield(hour to state)
        }
    }

    // The first hour at which the Robotics Factory stands at `level`, which is what every gate in
    // the game is expressed against.
    private fun hourReaching(level: BuildingLevel): Int? =
        colonyEveryThreeHours(DAYS).firstOrNull { (_, state) -> state.buildings.roboticsFactory.value >= level.value }?.first

    private fun buildingLevelsAfter(days: Int): Int =
        colonyEveryThreeHours(days).last().second.let { state ->
            BuildingType.entries.sumOf { state.buildings.levelOf(it).value }
        }

    private fun next(state: GameState, it: BuildingType): BuildingLevel =
        BuildingLevel(state.buildings.levelOf(it).value + 1)

    private fun priced(cost: Resources): Long = cost.metal + 2 * cost.crystal + 3 * cost.deuterium

    private fun List<Int>.median(): Int = if (isEmpty()) 0 else sorted()[size / 2]

    private companion object {

        val EPOCH: Instant = Instant.fromEpochMilliseconds(0)

        val OPENINGS: List<Opening> by lazy {
            (0 until SEEDS).map { offset ->
                val seed = GalaxySeed(TEST_GALAXY_SEED.value + offset)
                val galaxy = GalaxyState.initial(seed)
                Opening(
                    galaxy.surveyed
                        .filter { it != galaxy.home }
                        .mapNotNull { at -> worldAt(seed, at) }
                        .map { GalaxyBalance.levelsToTolerate(it.traits) }
                        .sorted(),
                )
            }
        }

        // Enough for a share to be stable to about a point, few enough that this stays a unit test
        // on every target including Kotlin/Native — genesis walks its galaxy once per seed.
        const val SEEDS: Int = 200

        const val DAYS: Int = 7

        // The five a player actually buys in the opening; the Nanite Factory is behind Robotics 10
        // and out of reach of every reading here.
        val PLAN = listOf(
            BuildingType.METAL_MINE,
            BuildingType.CRYSTAL_MINE,
            BuildingType.DEUTERIUM_SYNTHESIZER,
            BuildingType.SOLAR_PLANT,
            BuildingType.ROBOTICS_FACTORY,
        )
    }
}
