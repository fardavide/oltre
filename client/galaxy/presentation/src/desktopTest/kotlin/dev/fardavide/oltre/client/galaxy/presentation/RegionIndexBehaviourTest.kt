package dev.fardavide.oltre.client.galaxy.presentation

import androidx.compose.ui.test.ExperimentalTestApi
import dev.fardavide.oltre.client.galaxy.ui.GalaxyBodyUiState
import dev.fardavide.oltre.client.galaxy.ui.GalaxyUiState
import dev.fardavide.oltre.client.galaxy.ui.RegionRowUiState
import dev.fardavide.oltre.client.galaxy.ui.galaxyPage
import dev.fardavide.oltre.core.GalaxyBalance
import dev.fardavide.oltre.core.regionOf
import kotlin.test.assertEquals
import kotlin.test.assertIs
import org.junit.Test

// **Ten rows against a thousand pages**, driven with a finger rather than read off a mapper.
//
// `RegionIndexUiStateTest` next door already holds the arithmetic to account — ten rows, 25 ticks
// each, which one is flagged home, what a temperament composes — so nothing here restates any of it.
// What is left is the half no mapper test can reach, and it is two things:
//
// 1. **The index is a chooser rather than a level you pass through.** It is entered by tapping one
//    word in a header and left by tapping a card, and choosing a region has to hand back the *map* at
//    that region. Every claim about that is a claim about navigation, and navigation is state
//    `GalaxyScreen` holds.
// 2. **A card that maps correctly can still fail to draw.** Three steps of brightness inside 10.5sp
//    are the whole of the card's hierarchy, so the bias and the fact are exactly the readings a
//    tidy-up loses without changing a single string the mapper produces.
//
// Driven through the Robot, never through a raw node query — the shape `ResearchRobot` set.
@OptIn(ExperimentalTestApi::class)
class RegionIndexBehaviourTest {

    @Test
    fun `the region name in the system header is the way into the index`() {
        // The header's only accent string and the entire affordance — no chevron, no underline, no
        // button chrome — because accent already means *go tap this* everywhere else in the app.
        var opened = 0

        galaxyPage(uiState = homeSystemUiState, onOpenRegionIndex = { opened++ }) {
            openTheRegionIndex()
        }

        assertEquals(1, opened)

        // ...and the screen that holds the view answers it by swapping the body under the same head,
        // which is the half a handed frame cannot show.
        galaxyScreen(state = testGameState) {
            openTheMap()
            assertTheIndexIsAway()

            openTheRegionIndex()

            assertTheIndexIsUp()
        }
    }

    @Test
    fun `the index answers a galaxy of two hundred and fifty systems with ten cards`() {
        galaxyPage(uiState = regionsUiState) {
            // Both figures, in the heading's rule — the one place on the screen that says what the
            // ten cards are ten of, and the whole answer to the phone book.
            assertReads("${GalaxyBalance.REGIONS_PER_GALAXY} regions")
            assertReads("${GalaxyBalance.SYSTEMS_PER_GALAXY} systems")
            // Counted off the constant rather than off the frame: a loop over the rows the mapper
            // produced would pass just as happily over three of them, and how many rows there are is
            // `RegionIndexUiStateTest`'s claim. What this one says is that all ten reach the glass —
            // ten cards carrying a histogram and four lines each are three screens at 393x852.
            (1..GalaxyBalance.REGIONS_PER_GALAXY).forEach { assertTheIndexOffers(it) }
        }
    }

    @Test
    fun `every card draws its bias and the strategy that follows from it`() {
        // **The argument for the whole screen, on the glass.** Star class is charted, so these two
        // readings are true before a probe has ever been sent — and on the day this ships that is all
        // there is, because genesis surveys one system in 250. A card that dropped either would map
        // perfectly and still leave the index a table of contents.
        galaxyPage(uiState = regionsUiState) {
            regionRows.forEach { row ->
                assertTheRegionReads(row.region, row.bias)
                assertTheRegionReads(row.region, row.fact)
                // The appetite line under them, in the same ink and drawn just as easily lost: how
                // much of the region you have read is what the two above are an argument to change.
                assertTheRegionReads(row.region, row.known)
            }
        }
    }

    @Test
    fun `choosing a region puts you at its first system and hands back the map`() {
        // **A chooser rather than a level you pass through.** The strip still goes anywhere directly,
        // so an index you had to come back out of would be a hierarchy invented to hold one decision
        // — and the decision is *where to probe next*, which is taken on the map.
        val home = testGameState.galaxy.home
        // Somewhere you are not standing, so "it moved you" is a claim rather than a coincidence.
        val elsewhere = if (regionOf(home.system) == 1) GalaxyBalance.REGIONS_PER_GALAXY else 1
        val first = (elsewhere - 1) * GalaxyBalance.SYSTEMS_PER_REGION + 1

        galaxyScreen(state = testGameState) {
            openTheMap()
            openTheRegionIndex()
            assertTheIndexIsUp()

            openRegion(elsewhere)

            assertTheIndexIsAway()
            assertTheMapIsDrawn()
            // The head of the range the card printed rather than the nearest system in it: a region
            // is read from its first system, and landing anywhere else would make the card a label
            // for somewhere the tap did not go.
            assertTheHeaderNames("${home.galaxy}:$first")
        }
    }

    @Test
    fun `the active card carries the same region name the header was tapped on`() {
        // **The seam between two mappers, which is the one thing neither of their tests can see.**
        // `toSystemHeadUiState` composes the accent word and `toRegionRows` composes the card's name,
        // and both reach `regionNameAt` down their own path — so a player reads a word, taps it, and
        // has to find that word again on the card the accent border is around. Which card carries the
        // flag is `RegionIndexUiStateTest`'s claim; that the two agree is this one's.
        val header = assertIs<GalaxyBodyUiState.System>(homeSystemUiState.body).header
        val standingIn = regionRows.single { it.isHome }

        galaxyScreen(state = testGameState) {
            openTheMap()
            // Shouted in the header and title case on the card: the case is a rendering decision and
            // the name underneath it is the same string.
            assertReads(header.region.uppercase())

            openTheRegionIndex()

            assertTheRegionReads(standingIn.region, header.region)
        }
    }
}

// The index of the galaxy the colony is in, at genesis — which is the state 249 systems in 250 are
// in on the day the slice ships, and therefore the screen rather than a stage before the screen.
private val regionsUiState: GalaxyUiState = frame(view = GalaxyView.REGIONS)

private val regionRows: List<RegionRowUiState> =
    assertIs<GalaxyBodyUiState.Regions>(regionsUiState.body).rows
