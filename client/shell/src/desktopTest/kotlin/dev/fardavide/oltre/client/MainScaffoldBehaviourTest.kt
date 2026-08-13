package dev.fardavide.oltre.client

import androidx.compose.material3.Text
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runDesktopComposeUiTest
import dev.fardavide.oltre.client.design.core.OltreTheme
import dev.fardavide.oltre.client.design.format.groupedByThousands
import dev.fardavide.oltre.client.tilt.domain.Tilt
import org.junit.Test

// The bar is the one piece of navigation every later screen hangs off, so what it has to get
// right is small and worth pinning: the game opens on the colony, all five destinations are
// reachable, each built one shows its own screen, and a tab with no screen behind it says so.
@OptIn(ExperimentalTestApi::class)
class MainScaffoldBehaviourTest {

    @Test
    fun `the game opens on the colony`() {
        scaffold {
            onNodeWithText(COLONY_MARKER).assertIsDisplayed()
            onNodeWithTag(ShellTestTags.tab(OltreTab.COLONY)).assertIsSelected()
        }
    }

    @Test
    fun `every tab in the bar is reachable`() {
        OltreTab.entries.forEach { tab ->
            scaffold {
                onNodeWithTag(ShellTestTags.tab(tab)).performClick()
                onNodeWithTag(ShellTestTags.tab(tab)).assertIsSelected()
                OltreTab.entries.filter { it != tab }.forEach { other ->
                    onNodeWithTag(ShellTestTags.tab(other)).assertIsNotSelected()
                }
            }
        }
    }

    // Each destination shows its own screen and only its own. A tab that quietly fell through to
    // a neighbour would read as a bug in the neighbour.
    @Test
    fun `the Research tab shows the research screen rather than the colony`() {
        scaffold {
            onNodeWithTag(ShellTestTags.tab(OltreTab.RESEARCH)).performClick()
            onNodeWithText(RESEARCH_MARKER).assertIsDisplayed()
            onNodeWithText(COLONY_MARKER).assertDoesNotExist()
        }
    }

    @Test
    fun `the Galaxy tab shows the galaxy screen rather than an empty state`() {
        scaffold {
            onNodeWithTag(ShellTestTags.tab(OltreTab.GALAXY)).performClick()
            onNodeWithText(GALAXY_MARKER).assertIsDisplayed()
            onNodeWithText(COLONY_MARKER).assertDoesNotExist()
            onNodeWithText(RESEARCH_MARKER).assertDoesNotExist()
        }
    }

    // **Every destination shows its own screen, and there is no longer an "unbuilt" case to
    // exempt.** This test used to iterate the tabs whose `pendingWork` was non-null and assert the
    // honest empty state; at 0.8.0 that filter matches nothing and the property it was protecting —
    // *a tab never quietly re-shows a neighbour* — is the one worth keeping. So it is stated over
    // all five rather than over the built three.
    @Test
    fun `no destination shows another destination's screen`() {
        val markers = mapOf(
            OltreTab.COLONY to COLONY_MARKER,
            OltreTab.RESEARCH to RESEARCH_MARKER,
            OltreTab.GALAXY to GALAXY_MARKER,
            OltreTab.SHIPYARD to SHIPYARD_MARKER,
            OltreTab.FLEETS to FLEETS_MARKER,
        )
        OltreTab.entries.forEach { tab ->
            scaffold {
                onNodeWithTag(ShellTestTags.tab(tab)).performClick()
                onNodeWithText(markers.getValue(tab)).assertIsDisplayed()
                markers.filterKeys { it != tab }.values.forEach { other ->
                    onNodeWithText(other).assertDoesNotExist()
                }
            }
        }
    }

    @Test
    fun `the colony comes back when its tab does`() {
        scaffold {
            onNodeWithTag(ShellTestTags.tab(OltreTab.FLEETS)).performClick()
            onNodeWithTag(ShellTestTags.tab(OltreTab.COLONY)).performClick()
            onNodeWithText(COLONY_MARKER).assertIsDisplayed()
        }
    }

    // The rail is chrome: it frames every destination, built or not.
    @Test
    fun `the resource rail stays put whichever destination is showing`() {
        OltreTab.entries.forEach { tab ->
            scaffold {
                onNodeWithTag(ShellTestTags.tab(tab)).performClick()
                onNodeWithText(testResourceRailUiState.metal.stock.groupedByThousands()).assertIsDisplayed()
                onNodeWithText(testResourceRailUiState.deuterium.ratePerHour).assertIsDisplayed()
            }
        }
    }

    // A phone-sized window: the bar has to fit five destinations at the narrowest width the game
    // actually ships at.
    private fun scaffold(assertions: ComposeUiTest.() -> Unit) {
        runDesktopComposeUiTest(width = 393, height = 852) {
            setContent {
                OltreTheme {
                    MainScaffold(
                        // Desktop has no motion sensor, so this is also what the app itself passes here.
                        tilt = { Tilt.NONE },
                        resources = testResourceRailUiState,
                        colony = { Text(COLONY_MARKER) },
                        research = { Text(RESEARCH_MARKER) },
                        galaxy = { _, _ -> Text(GALAXY_MARKER) },
                        shipyard = { Text(SHIPYARD_MARKER) },
                        fleets = { Text(FLEETS_MARKER) },
                    )
                }
            }
            assertions()
        }
    }

    private companion object {

        // Stand in for the real screens: the scaffold's job is to show them, not to know what is
        // in them, and the shell's tests should not need a colony to assert navigation.
        const val COLONY_MARKER = "colony-under-test"
        const val RESEARCH_MARKER = "research-under-test"
        const val GALAXY_MARKER = "galaxy-under-test"
        const val SHIPYARD_MARKER = "shipyard-under-test"
        const val FLEETS_MARKER = "fleets-under-test"
    }
}
