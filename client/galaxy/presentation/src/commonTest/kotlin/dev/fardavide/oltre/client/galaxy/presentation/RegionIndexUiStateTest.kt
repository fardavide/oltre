package dev.fardavide.oltre.client.galaxy.presentation

import dev.fardavide.oltre.client.galaxy.ui.GalaxyBodyUiState
import dev.fardavide.oltre.client.galaxy.ui.LedgerSort
import dev.fardavide.oltre.client.galaxy.ui.RegionRowUiState
import dev.fardavide.oltre.core.FleetBalance
import dev.fardavide.oltre.core.GalaxyBalance
import dev.fardavide.oltre.core.GalaxyCoordinate
import dev.fardavide.oltre.core.GalaxySeed
import dev.fardavide.oltre.core.GameState
import dev.fardavide.oltre.core.RegionTemperament
import dev.fardavide.oltre.core.StarClass
import dev.fardavide.oltre.core.regionOf
import dev.fardavide.oltre.core.temperamentOf
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue
import kotlin.time.Instant
import kotlinx.datetime.TimeZone

// **Ten rows against a thousand pages.** Where you decide where to probe next — the one decision the
// map exists for and the one it had never helped with, because 250 numbered systems are not a set of
// choices.
//
// What makes the index worth opening is that **every reading on it is free**: star class is charted,
// so a region announces its bias before a probe has ever been sent. That is the claim these tests
// exist to hold the mapper to.
class RegionIndexUiStateTest {

    @Test
    fun `the index is ten rows and covers the galaxy exactly once`() {
        // given
        val rows = state().regionRows()

        // then — ten names a galaxy is what a player can learn in a week, and the whole point is
        // that the list ends. A row per region and no region twice.
        assertEquals(GalaxyBalance.REGIONS_PER_GALAXY, rows.size)
        assertEquals((1..GalaxyBalance.REGIONS_PER_GALAXY).toSet(), rows.map { it.region }.toSet())
    }

    @Test
    fun `a row draws its whole region rather than a sample of it`() {
        // Twenty-five ticks: the bias is already visible in the histogram and needs no copy, which
        // only works if the run is complete. Twenty of twenty-five would read as a lighter region.
        val rows = state().regionRows()

        for (row in rows) {
            assertEquals(GalaxyBalance.SYSTEMS_PER_REGION, row.histogram.size, "region ${row.region}")
        }
    }

    @Test
    fun `a range reads as a range rather than as a subtraction`() {
        // Galaxy 3 and region 7 so the expectation is a literal rather than an interpolation: a test
        // that rebuilt the string the way the mapper does would still pass on a mapper that dropped
        // the colon or slid the boundary by one.
        val rows = state().regionRows(galaxy = 3)

        assertEquals("3:151–175", rows.first { it.region == 7 }.range)
        // An en dash, U+2013, and not the hyphen a keyboard reaches first — 151-175 is an arithmetic
        // expression and this is a span of systems. The lookalike is invisible in a diff, so it is
        // worth an assertion of its own rather than trusting the equality above to be read.
        assertTrue(rows.all { '-' !in it.range }, "was ${rows.map { it.range }}")
    }

    @Test
    fun `the region you are standing in is the only one flagged home`() {
        // given
        val state = state()
        val home = state.galaxy.home

        // when
        val rows = state.regionRows()

        // then — one active border on the list and one accent tick inside it. Two would be two
        // homes, and none would lose the only fixed point the index has.
        assertEquals(listOf(regionOf(home.system)), rows.filter { it.isHome }.map { it.region })
        assertEquals(
            listOf(home.system),
            rows.flatMap { it.histogram }.filter { it.isHome }.map { it.system },
        )
    }

    @Test
    fun `the rows are ordered by how far away they are and never by their number`() {
        // given
        val state = state()
        val home = state.galaxy.home

        // when
        val rows = state.regionRows()

        // then — the index is a chooser, and the first thing to choose from is where you already
        // are. Coordinate order is what the strip is for.
        assertEquals(regionOf(home.system), rows.first().region)
        assertNotEquals((1..GalaxyBalance.REGIONS_PER_GALAXY).toList(), rows.map { it.region })
        // **"Nearest" is the shortest round trip into the region**, so restating that metric is
        // stating the spec rather than copying the mapper — what is under test is the ordering, and
        // a run that read the flight off the *first* system of a region rather than the closest one
        // would fail here while every other assertion on this screen still passed.
        val flights = rows.map { row ->
            row.histogram.minOf { tick ->
                FleetBalance.roundTrip(
                    from = home,
                    to = GalaxyCoordinate(galaxy = home.galaxy, system = tick.system, slot = 1),
                )
            }
        }
        assertEquals(flights.sorted(), flights, "was ${rows.map { it.region }}")
    }

    @Test
    fun `a region says what its stars are before a probe has been sent`() {
        // **The one reading on this screen that is free**, and the reason the index is worth opening
        // rather than being a table of contents. A Deep is 60% dim by construction, and a Settled is
        // the mix the map had before regions existed — so it says "even" rather than a number that
        // would read as a bias.
        val state = state()
        val rows = state.regionRows()

        for (row in rows) {
            val expected = when (temperamentOf(state.galaxy.seed, state.galaxy.home.galaxy, row.region)) {
                RegionTemperament.DEEP -> "60% dim"
                RegionTemperament.SETTLED -> "even mix"
                RegionTemperament.BURNING -> "60% bright"
            }
            assertEquals(expected, row.bias, "region ${row.region}")
        }
    }

    @Test
    fun `the fact is the strategy the temperament implies and not a restatement of it`() {
        // **Five words that are true from the first launch.** A dim star is −40 °C against a fall of
        // 28 °C per orbit, so the tolerable orbits really do move about three slots between a Deep
        // and a Blaze — and the deuterium goes with them, because richness is derived from
        // temperature. The line is what a player can act on before surveying anything.
        val state = state()
        val rows = state.regionRows()

        for (row in rows) {
            val expected = when (temperamentOf(state.galaxy.seed, state.galaxy.home.galaxy, row.region)) {
                RegionTemperament.DEEP -> "settle close in · deuterium good"
                RegionTemperament.SETTLED -> "no orbit bias"
                RegionTemperament.BURNING -> "settle far out · deuterium poor"
            }
            assertEquals(expected, row.fact, "region ${row.region}")
        }
    }

    @Test
    fun `a Deep really does hold more dim stars than a Blaze`() {
        // **The whole claim the index makes and the one a player can check by counting ticks.** The
        // bias line above is copy; this is the histogram it is copy about, drawn from the same star
        // classes the strip draws. If a Deep did not visibly run darker than a Blaze the index would
        // be decoration, and every seed has four of each — the temperaments are a permutation of a
        // fixed list rather than ten draws, which is exactly what makes this assertable at all.
        val state = state()
        val rows = state.regionRows()
        val deep = rows.filter { it.temperamentIn(state) == RegionTemperament.DEEP }
        val blaze = rows.filter { it.temperamentIn(state) == RegionTemperament.BURNING }

        assertEquals(4, deep.size)
        assertEquals(4, blaze.size)
        assertTrue(
            deep.minOf { it.dimStars() } > blaze.maxOf { it.dimStars() },
            "deep ${deep.map { it.dimStars() }} against blaze ${blaze.map { it.dimStars() }}",
        )
    }

    // ── fixtures ────────────────────────────────────────────────────────────────────────────

    private fun GameState.regionRows(galaxy: Int = this.galaxy.home.galaxy): List<RegionRowUiState> =
        assertIs<GalaxyBodyUiState.Regions>(
            toGalaxyUiState(
                nav = navIn(galaxy),
                now = EPOCH,
                timeZone = TimeZone.UTC,
            ).body,
        ).rows

    // The index is reached by tapping the region name in the system header, so the view is the only
    // thing that moves: the query, the chips and the sort belong to the ledger and the head drops
    // them the moment it is not showing one.
    private fun GameState.navIn(galaxy: Int): GalaxyNavigation = GalaxyNavigation(
        view = GalaxyView.REGIONS,
        at = SystemSelection(galaxy = galaxy, system = this.galaxy.home.system),
        query = "",
        filters = emptySet(),
        sort = LedgerSort.NEAREST,
        seenAt = EPOCH,
        availableFilters = emptyList(),
    )

    private fun RegionRowUiState.temperamentIn(state: GameState): RegionTemperament =
        temperamentOf(state.galaxy.seed, state.galaxy.home.galaxy, region)

    private fun RegionRowUiState.dimStars(): Int = histogram.count { it.starClass == StarClass.DIM }

    private fun state(): GameState = GameState.initial(GalaxySeed(20_260_807))

    private companion object {
        val EPOCH: Instant = Instant.fromEpochMilliseconds(0)
    }
}
