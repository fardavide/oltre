package dev.fardavide.oltre.client.galaxy.presentation

import androidx.compose.ui.test.ExperimentalTestApi
import dev.fardavide.oltre.client.galaxy.ui.SkyDepth
import dev.fardavide.oltre.client.galaxy.ui.SkyGeometry
import dev.fardavide.oltre.client.galaxy.ui.SkySelection
import dev.fardavide.oltre.client.galaxy.ui.galaxyPage
import dev.fardavide.oltre.core.GalaxyBalance
import dev.fardavide.oltre.core.GalaxyCoordinate
import dev.fardavide.oltre.core.SystemAddress
import kotlin.test.assertEquals
import org.junit.Test

// **One sky, five zooms of it.** The tab used to be three pages and a switch; it is one drawing now,
// and the whole interface is where the eye is and what is under it. These are the page's own
// affordances — what a tap, a pinch, a step and the caption do to the eye — driven from a mapped
// frame, so the words never change under the test. Where the words *do* change with the eye,
// `GalaxyFromStateBehaviourTest` drives the stateful screen.
//
// Driven through the Robot, never through a raw node query — the shape `ResearchRobot` set.
@OptIn(ExperimentalTestApi::class)
class GalaxyPageBehaviourTest {

    // ── Where the tab opens ──────────────────────────────────────────────────────────────────

    @Test
    fun `the tab opens on the region about home with your star selected`() {
        // The landing, and it is the only fixed point in the whole tab: the region is the depth at
        // which the neighbours a genesis probe can reach are all on screen, and the star is selected
        // so the first caption a player reads is their own.
        galaxyPage(uiState = regionFrame.uiState, view = regionFrame.view) {
            assertTheSkyIsDrawn()
            assertTheDepthIs(SkyDepth.REGION)
            assertTheSelectionIs(SkySelection.System(HOME))
        }
    }

    @Test
    fun `the bar spells the address down to the selection and no further`() {
        // **The bar is the address, not a menu.** Four steps at the landing because the selection is
        // a system; the world step exists only once a world is selected, so a player is never shown
        // a step to somewhere they have not pointed at.
        galaxyPage(uiState = regionFrame.uiState, view = regionFrame.view) {
            assertTheBarReads(SkyDepth.UNIVERSE, "universe")
            assertTheBarReads(SkyDepth.GALAXY, "galaxy 6")
            assertTheBarReads(SkyDepth.REGION, "Torux Blaze")
            assertTheBarReads(SkyDepth.SYSTEM, "Teshezon")
            assertTheBarHasNoStep(SkyDepth.WORLD)
        }
        galaxyPage(uiState = universeFrame.uiState, view = universeFrame.view) {
            assertTheBarReads(SkyDepth.GALAXY, "galaxy 6")
            assertTheBarHasNoStep(SkyDepth.REGION)
            assertTheBarHasNoStep(SkyDepth.SYSTEM)
        }
    }

    @Test
    fun `the bar scrolls so the lit step is on screen`() {
        // Five steps do not fit a phone, and the one that would fall off the right edge is the world
        // — the step that is lit. The bar scrolls itself to it rather than leaving the address to
        // end on a separator.
        galaxyPage(uiState = worldFrame.uiState, view = worldFrame.view) {
            assertTheBarReads(SkyDepth.WORLD, "Teshezon IX")
            assertTheBarShows(SkyDepth.WORLD)
        }
    }

    @Test
    fun `the count under the bar says what the depth holds`() {
        galaxyPage(uiState = universeFrame.uiState, view = universeFrame.view) {
            assertTheCountReads("9 galaxies · yours is 6")
        }
        galaxyPage(uiState = regionFrame.uiState, view = regionFrame.view) {
            assertTheCountReads("61 of 250 charted · 1 surveyed")
        }
        galaxyPage(uiState = systemFrame.uiState, view = systemFrame.view) {
            assertTheCountReads("Teshezon · 5 worlds · your own system")
        }
    }

    // ── Tap: once to select, again to dive ───────────────────────────────────────────────────

    @Test
    fun `a tap on a star selects it and a second tap dives into it`() {
        // The one gesture rule at every depth: the first tap moves the selection and nothing else,
        // so the caption can say what a thing is before the eye commits to it; the second flies.
        galaxyPage(uiState = regionFrame.uiState, view = regionFrame.view) {
            tapStar(NEIGHBOUR)
            assertTheDepthIs(SkyDepth.REGION)
            assertTheSelectionIs(SkySelection.System(NEIGHBOUR))

            tapStar(NEIGHBOUR)
            assertTheDepthIs(SkyDepth.SYSTEM)
            assertTheSelectionIs(SkySelection.System(NEIGHBOUR))
        }
    }

    @Test
    fun `a tap on a galaxy selects it and a second tap dives into it`() {
        // The same rule at the top: the universe is nine discs, and diving into one lands on the
        // region the galaxy is entered by rather than on a star, because a galaxy is 250 stars wide
        // and no one of them is the answer.
        galaxyPage(uiState = universeFrame.uiState, view = universeFrame.view) {
            tapGalaxy(7)
            assertTheDepthIs(SkyDepth.UNIVERSE)
            assertTheSelectionIs(SkySelection.Galaxy(7))

            tapGalaxy(7)
            assertTheDepthIs(SkyDepth.GALAXY)
            // Somebody else's galaxy is entered by its middle region; yours by the one home is in.
            assertTheSelectionIs(SkySelection.Region(7, 5))
        }
    }

    @Test
    fun `a tap on a region selects it and a second tap dives into it`() {
        val next = SkyGeometry.regionOf(HOME.system) + 1

        galaxyPage(uiState = galaxyFrame.uiState, view = galaxyFrame.view) {
            tapRegion(HOME.galaxy, next)
            assertTheDepthIs(SkyDepth.GALAXY)
            assertTheSelectionIs(SkySelection.Region(HOME.galaxy, next))

            tapRegion(HOME.galaxy, next)
            assertTheDepthIs(SkyDepth.REGION)
        }
    }

    @Test
    fun `a tap on a world selects it and a second tap flies in front of it`() {
        galaxyPage(uiState = systemFrame.uiState, view = systemFrame.view) {
            tapWorld(RUNNABLE)
            assertTheDepthIs(SkyDepth.SYSTEM)
            assertTheSelectionIs(SkySelection.World(RUNNABLE))

            tapWorld(RUNNABLE)
            assertTheDepthIs(SkyDepth.WORLD)
            assertTheSelectionIs(SkySelection.World(RUNNABLE))
        }
    }

    @Test
    fun `the caption's open verb is the dive`() {
        // The caption is the one control, and "open" on it is the same flight the second tap makes —
        // for a player who would rather read what a thing is and then go, without finding it again
        // under a thumb.
        galaxyPage(uiState = regionFrame.uiState, view = regionFrame.view) {
            assertTheCaptionOffers("open")
            openTheSelection()
            assertTheDepthIs(SkyDepth.SYSTEM)
            assertTheSelectionIs(SkySelection.System(HOME))
        }
    }

    @Test
    fun `a galaxy and a region both open from their caption`() {
        galaxyPage(uiState = universeFrame.uiState, view = universeFrame.view) {
            assertTheCaptionReads("Galaxy 6")
            assertTheCaptionOffers("open")
            openTheSelection()
            assertTheDepthIs(SkyDepth.GALAXY)
        }
        galaxyPage(uiState = galaxyFrame.uiState, view = galaxyFrame.view) {
            assertTheCaptionReads("Torux Blaze")
            assertTheCaptionOffers("open")
            openTheSelection()
            assertTheDepthIs(SkyDepth.REGION)
        }
    }

    // ── The bar flies out ────────────────────────────────────────────────────────────────────

    @Test
    fun `a step on the bar flies the eye out to that depth and keeps the selection`() {
        // A step is a zoom, not a back button: the selection is where the player pointed and the
        // step only changes how far away they look at it from, so the bar still spells the whole
        // address after the flight.
        galaxyPage(uiState = systemFrame.uiState, view = systemFrame.view) {
            openStep(SkyDepth.GALAXY)
            assertTheDepthIs(SkyDepth.GALAXY)
            assertTheSelectionIs(SkySelection.System(HOME))
            assertTheBarReads(SkyDepth.SYSTEM, "Teshezon")

            openStep(SkyDepth.UNIVERSE)
            assertTheDepthIs(SkyDepth.UNIVERSE)
            assertTheSelectionIs(SkySelection.System(HOME))
        }
    }

    // ── Pinch and pan ────────────────────────────────────────────────────────────────────────

    @Test
    fun `a pinch in reaches the next depth and a pinch out the one before`() {
        // Depth is derived from the zoom and nothing else, which is what lets a pinch and a step be
        // the same thing: eight times the region is inside the system, half of it is the galaxy.
        galaxyPage(uiState = regionFrame.uiState, view = regionFrame.view) {
            pinch(factor = 8f)
            assertTheDepthIs(SkyDepth.SYSTEM)
            assertTheSelectionIs(SkySelection.System(HOME))
        }
        galaxyPage(uiState = regionFrame.uiState, view = regionFrame.view) {
            pinch(factor = 0.5f)
            assertTheDepthIs(SkyDepth.GALAXY)
        }
    }

    @Test
    fun `panning off the world at the world depth hands the selection back to its star`() {
        // **Past the galaxy the selection follows the eye.** There is no tap to make at 940 px a
        // unit — the world fills the screen — so what is in front of the player is what is selected,
        // and a pan that carries the world out of the middle leaves the star, and the system depth.
        galaxyPage(uiState = worldFrame.uiState, view = worldFrame.view) {
            pan(fractionOfWidth = 0.5f)
            assertTheSelectionIs(SkySelection.System(HOME))
            assertTheDepthIs(SkyDepth.SYSTEM)
        }
    }

    // ── What is not there to tap ─────────────────────────────────────────────────────────────

    @Test
    fun `a world is never drawn under a star nobody has charted`() {
        // Fog is grain at every depth and a world under an uncharted star is a rumour: the system
        // depth on a dark star draws nothing to tap, so the only verb in front of it is the probe.
        val dark = frame(state = wealthyState, selection = SkySelection.System(DARK_STAR), depth = SkyDepth.SYSTEM)

        galaxyPage(uiState = dark.uiState, view = dark.view) {
            (1..GalaxyBalance.SLOTS_PER_SYSTEM).forEach { slot ->
                assertNothingToTapAt(GalaxyCoordinate(DARK_STAR.galaxy, DARK_STAR.system, slot))
            }
        }
    }

    // ── The caption's verbs reach the screen ─────────────────────────────────────────────────

    @Test
    fun `the probe verb on an unsurveyed star asks for the probe`() {
        var asked = 0

        galaxyPage(uiState = unsurveyedStarFrame.uiState, view = unsurveyedStarFrame.view, onDispatchProbe = { asked++ }) {
            assertTheCaptionOffers("probe")
            takeTheCaptionsVerb()
        }

        assertEquals(1, asked)
    }

    private companion object {
        val HOME: SystemAddress = SystemAddress.of(frameState.galaxy.home)
        val NEIGHBOUR: SystemAddress = frameState.neighbour()
    }
}
