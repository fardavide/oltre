package dev.fardavide.oltre.client

import dev.fardavide.oltre.client.design.text.English
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
import dev.fardavide.oltre.client.player.ui.PlayerTestTags
import dev.fardavide.oltre.client.tilt.domain.Tilt
import kotlin.test.assertEquals
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

    // **The gear asks, and that is the whole of what this file can hold about it.** The frame draws
    // the control and forwards the press; what goes up is `App`'s, exactly as the debug sheet is —
    // so *that* the press is heard is here, and that the sheet arrives is
    // `AlertSheetAppBehaviourTest`, which drives the real composition root.
    @Test
    fun `the gear asks for the settings`() {
        var asked = 0

        scaffold(onOpenSettings = { asked++ }) {
            onNodeWithTag(PlayerTestTags.SETTINGS, useUnmergedTree = true).performClick()
        }

        assertEquals(1, asked)
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
                onNodeWithText(English.resolve(testResourceRailUiState.metal.stock.groupedByThousands())).assertIsDisplayed()
                onNodeWithText(English.resolve(testResourceRailUiState.deuterium.ratePerHour)).assertIsDisplayed()
            }
        }
    }

    // **The two halves of a switch, and the second one is the one worth having.** A transition that
    // brings the new screen in is easy to see and easy to get right; a transition that forgets to
    // take the old one away leaves two destinations composed on top of each other for the rest of the
    // session, which reads as a rendering bug rather than as a missing animation and is exactly what
    // `assertDoesNotExist` in the tests above would start failing on.
    //
    // Mid-crossing is asserted first because it is what makes the second assertion mean something:
    // without it, "the colony is gone" is satisfied by a switch that never drew the colony at all.
    @Test
    fun `the destination being left is still drawn while the one arriving crosses it`() {
        switching {
            tap(OltreTab.RESEARCH)
            halfwayThrough()
            assertDrawn(COLONY_MARKER)
            assertDrawn(RESEARCH_MARKER)
        }
    }

    @Test
    fun `the destination being left is gone once the switch is over`() {
        switching {
            tap(OltreTab.RESEARCH)
            afterTheSwitch()
            assertShowing(RESEARCH_MARKER)
            assertGone(COLONY_MARKER)
        }
    }

    // The clock stopped, so the switch can be read frame by frame rather than jumped over. Every
    // other test in this file wants the opposite — it asks what is on screen once everything has
    // settled — which is what the auto-advancing `scaffold` below gives it.
    //
    // Through a Robot, unlike its neighbours: the test-coverage skill requires one and names this
    // file as the thing not to copy. The older tests are left as they are — migrating them is worth
    // doing and is not this change.
    private fun switching(assertions: ScaffoldRobot.() -> Unit) {
        scaffold(pauseTheClock = true) { ScaffoldRobot(this).assertions() }
    }

    // A phone-sized window: the bar has to fit five destinations at the narrowest width the game
    // actually ships at.
    private fun scaffold(
        pauseTheClock: Boolean = false,
        onOpenSettings: () -> Unit = {},
        assertions: ComposeUiTest.() -> Unit,
    ) {
        runDesktopComposeUiTest(width = 393, height = 852) {
            if (pauseTheClock) mainClock.autoAdvance = false
            setContent {
                OltreTheme {
                    MainScaffold(
                        // Desktop has no motion sensor, so this is also what the app itself passes here.
                        tilt = { Tilt.NONE },
                        player = testPlayerStripUiState,
                        resources = testResourceRailUiState,
                        colony = { Text(COLONY_MARKER) },
                        research = { Text(RESEARCH_MARKER) },
                        galaxy = { _, _ -> Text(GALAXY_MARKER) },
                        shipyard = { Text(SHIPYARD_MARKER) },
                        fleets = { Text(FLEETS_MARKER) },
                        // Handed in rather than ignored, because one test here is about the gear:
                        // that pressing it asks for something. *What* it opens is `App`'s — both
                        // modals in this app are raised there — and `AlertSheetAppBehaviourTest` is
                        // where the sheet actually goes up and comes down.
                        onOpenSettings = onOpenSettings,
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
