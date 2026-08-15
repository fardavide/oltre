package dev.fardavide.oltre.core

import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

// **The check-in, pinned.** The other half of what `OpeningBalanceTest` started.
//
// Oltre is a game about opening an app for five minutes, and almost every round in
// `balance-log.md` was called by a complaint about *that* rather than about a curve: round 8 found
// check-ins with nothing on them, round 11 found the wait outgrowing the earning, round 12 swept
// every lever at "nothing to do" and moved none of them, and round 16 was Davide asking in as many
// words for *"some adrenaline"* in the first session. Every one of those was argued from a reading
// in `:sim` that nothing asserted.
//
// The readings here are the ones those rounds turned on, as bands. They are deliberately floors and
// ceilings on the *experience* rather than on any constant: a change to the discount, the cost
// curve, the duration curve, the gate or the opening stock all land here, which is the point —
// `BalanceCurveTest` already pins the shapes, and the shapes being right is not the same as the
// game being playable.
//
// Measured with `:sim:run` before being written down — `printFirstSitting` and `printOpeningReport`
// are the two instruments. **If one fails, do not widen it to go green**: read the report, decide
// whether the opening genuinely got better, and move the band in a balance-log round that says what
// it bought.
class CheckInBalanceTest {

    // Round 8's finding, and the one that has never been allowed to regress since: a player who
    // opens the game must find *something* — either work that finished while they were away or
    // something they can afford now. A dead check-in is the only outcome this game cannot survive,
    // because there is nothing else to do in it.
    @Test
    fun `no check-in in the first two days is dead`() {
        val dead = fourADay(days = 2).count { visit -> visit.finished == 0 && visit.affordable == 0 }

        assertTrue(dead == 0, "$dead of the first two days' check-ins offered nothing at all")
    }

    // Round 16, in one row. Davide: *"we need to give some adrenaline to users"* — and the reading
    // that answered it was completions watched inside the first ten minutes, which went from **0**
    // at 0.2.6 to **7**. A floor of 3 keeps the session alive without forbidding a round that
    // lengthens the early builds again for a reason.
    @Test
    fun `the first ten minutes of a new colony land something to watch`() {
        val landed = firstSitting().count { it.atMinute <= 10 }

        assertTrue(landed >= 3, "only $landed things finished in the first ten minutes, was 7")
    }

    // The same session from the other side: not how much lands, but how long the player is asked to
    // stare at nothing.
    //
    // **Scoped to the first quarter of an hour on purpose.** Completions thin out later in the hour
    // by design — the curve is exponential and the second Solar Plant is not meant to land while
    // you watch — so a bound over the whole hour would be pinning the shape of the curve rather
    // than the density of the session. The sitting is the window Davide asked about.
    @Test
    fun `the first sitting never leaves the player staring at nothing`() {
        val landings = firstSitting().map { it.atMinute }.filter { it <= 15 }
        val first = landings.minOrNull() ?: 16
        val longest = (listOf(0) + landings).zipWithNext { a, b -> b - a }.maxOrNull() ?: 15

        assertTrue(first <= 5, "the first thing landed at minute $first, was 2")
        assertTrue(longest <= 8, "the longest silence in the first quarter hour is ${longest}m, was 6m")
    }

    // Round 12's finding, which no number in `PlaceholderBalance` could move: a colony that only
    // ever offers *building* is a colony with one verb. The second kind of decision has to arrive
    // while the player is still in their first day, or the opening is a single loop.
    @Test
    fun `a second kind of decision arrives on the first day`() {
        val visit = fourADay(days = 2).firstOrNull { it.kinds >= 2 }

        assertTrue(
            visit != null && visit.atHour <= 24,
            "a second kind of decision first appeared at hour ${visit?.atHour}, was hour 11",
        )
    }

    // The colony has to be *doing* something between visits or the game is a spreadsheet that
    // charges rent. This is the weakest of the four and is here as a floor rather than a target —
    // round 16 measured 95.83% of the first 48 hours with nothing in flight and called it the
    // biggest open item in the file, so the bar is deliberately low and the number is the thing to
    // watch rather than the assertion.
    @Test
    fun `a check-in leaves the colony with work booked`() {
        val visits = fourADay(days = 2)
        val booked = visits.count { it.leftRunning > 0 }

        assertTrue(
            booked * 100 / visits.size >= 60,
            "only ${booked * 100 / visits.size}% of check-ins left anything running, was 100%",
        )
    }

    // ── the instruments ──────────────────────────────────────────────────────────────────────

    private class Visit(
        val atHour: Int,
        val finished: Int,
        val affordable: Int,
        val kinds: Int,
        val leftRunning: Int,
    )

    private class Landing(val atMinute: Int)

    // A player who opens the game four times a day and buys what they can afford. The same shape
    // `printOpeningReport` uses; written out here rather than called into `:sim`, because `core`
    // cannot see the harness and a bound that depended on the harness's strategy would be pinning
    // the strategy rather than the balance.
    private fun fourADay(days: Int): List<Visit> {
        var state = GameState.initial(TEST_GALAXY_SEED)
        var now = EPOCH
        var seen = 0
        return buildList {
            for (day in 0 until days) {
                for (hour in CHECK_IN_HOURS) {
                    val at = EPOCH + (day * 24 + hour).hours
                    if (at < now) continue
                    state = advance(state, from = now, to = at)
                    now = at

                    val finished = state.eventLog.drop(seen).count { it.isCompletion() }
                    seen = state.eventLog.size

                    val buildings = PLAN.count { state.resources.covers(costOf(state, it)) }
                    val projects = offeredProjects(state)
                    val kinds = listOf(buildings > 0, projects > 0).count { it }

                    for (building in PLAN.sortedBy { priced(costOf(state, it)) }) {
                        if (!state.resources.covers(costOf(state, building))) continue
                        (startUpgrade(state, building, at = now) as? StartUpgradeResult.Started)
                            ?.let { state = it.state }
                    }
                    add(
                        Visit(
                            atHour = day * 24 + hour,
                            finished = finished,
                            affordable = buildings + projects,
                            kinds = kinds,
                            leftRunning = state.builds.size,
                        ),
                    )
                }
            }
        }
    }

    // One hour from genesis at one-minute resolution — the only cadence that can tell a two-minute
    // build from a fifty-minute one, which is why round 16 had to build it before it could measure
    // what it had changed.
    private fun firstSitting(): List<Landing> {
        var state = GameState.initial(TEST_GALAXY_SEED)
        var now = EPOCH
        var seen = 0
        return buildList {
            for (minute in 0..60) {
                val at = EPOCH + minute.minutes
                state = advance(state, from = now, to = at)
                now = at
                val completions = state.eventLog.drop(seen).count { it.isCompletion() }
                repeat(completions) { add(Landing(atMinute = minute)) }
                seen = state.eventLog.size
                for (building in PLAN.sortedBy { priced(costOf(state, it)) }) {
                    if (!state.resources.covers(costOf(state, building))) continue
                    (startUpgrade(state, building, at = now) as? StartUpgradeResult.Started)
                        ?.let { state = it.state }
                }
            }
        }
    }

    // How many research projects the colony could start, both branches, counting only what each
    // branch's own slot could actually take — two slots since 0.12.1, so a running project no longer
    // zeroes the ladders as well.
    private fun offeredProjects(state: GameState): Int {
        val applied = if (state.activeResearch != null) {
            0
        } else {
            Technology.entries.count { technology ->
                ResearchBalance.requirementFor(technology).isMetBy(state) &&
                    state.resources.covers(
                        ResearchBalance.researchCost(
                            technology,
                            TechLevel(state.research.levelOf(technology).value + 1),
                        ),
                    )
            }
        }
        val ladders = if (state.activeAdaptation != null) {
            0
        } else {
            AdaptationTechnology.entries.count { ladder ->
                AdaptationBalance.requirementFor(ladder).isMetBy(state) &&
                    state.resources.covers(
                        AdaptationBalance.adaptationCost(
                            ladder,
                            TechLevel(state.research.levelOf(ladder).value + 1),
                        ),
                    )
            }
        }
        return applied + ladders
    }

    // A *completion* is what a player watches; a start is what they did. The event log holds both,
    // and counting the pair would double every reading here and make a change that only moved starts
    // look like a change to the session.
    private fun Event.isCompletion(): Boolean = when (this) {
        is Event.BuildCompleted, is Event.ResearchCompleted, is Event.AdaptationCompleted,
        is Event.SurveyCompleted, is Event.FleetReturned,
        -> true
        else -> false
    }

    private fun costOf(state: GameState, building: BuildingType): Resources =
        PlaceholderBalance.upgradeCost(building, BuildingLevel(state.buildings.levelOf(building).value + 1))

    private fun priced(cost: Resources): Long = cost.metal + 2 * cost.crystal + 3 * cost.deuterium

    private companion object {

        val EPOCH: Instant = Instant.fromEpochMilliseconds(0)

        // Davide's own cadence, from `printOpeningReport`: morning, lunch, evening, bedtime.
        val CHECK_IN_HOURS = listOf(8, 13, 19, 23)

        val PLAN = listOf(
            BuildingType.METAL_MINE,
            BuildingType.CRYSTAL_MINE,
            BuildingType.DEUTERIUM_SYNTHESIZER,
            BuildingType.SOLAR_PLANT,
            BuildingType.ROBOTICS_FACTORY,
        )
    }
}
