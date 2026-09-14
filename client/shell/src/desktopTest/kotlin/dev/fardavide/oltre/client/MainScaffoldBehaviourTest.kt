package dev.fardavide.oltre.client

import dev.fardavide.oltre.client.design.text.English
import androidx.compose.foundation.ScrollState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.test.ComposeUiTest
import dev.fardavide.oltre.client.alliance.ui.AllianceActions
import dev.fardavide.oltre.client.alliance.ui.AllianceScreen
import dev.fardavide.oltre.client.alliance.ui.AllianceUiState
import dev.fardavide.oltre.client.design.text.Strings
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

    // **A real check-in, not five isolated ones.** Every test above opens a fresh scaffold per tab,
    // which proves each destination is reachable but never asks Compose to recompose `Destination`'s
    // own `when (selected)` across a real sequence — a fresh composition and a recomposition are not
    // the same event, and the difference is exactly what a player flipping through the whole bar in
    // one sitting does. One continuous session, every destination in bar order and back.
    @Test
    fun `a player can walk every destination in one sitting and land back on Colony`() {
        scaffold {
            OltreTab.entries.forEach { tab ->
                onNodeWithTag(ShellTestTags.tab(tab)).performClick()
                onNodeWithTag(ShellTestTags.tab(tab)).assertIsSelected()
            }
            onNodeWithTag(ShellTestTags.tab(OltreTab.COLONY)).performClick()
            onNodeWithText(COLONY_MARKER).assertIsDisplayed()
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

    // **And the cluster beside it asks for the other face**, which is the same claim about the
    // second control the strip grew: the frame forwards a press and decides nothing. Worth its own
    // test rather than folded into the gear's — the two sit 7dp apart on a 38dp bar, so a cluster
    // that had claimed the gear's target would pass every layout assertion in this file and open
    // the wrong face.
    @Test
    fun `the player cluster asks for the profile`() {
        var asked = 0

        scaffold(onOpenProfile = { asked++ }) {
            onNodeWithTag(PlayerTestTags.PROFILE, useUnmergedTree = true).performClick()
        }

        assertEquals(1, asked)
    }

    // The other half of that pair: pressing one control must not fire the other's callback.
    @Test
    fun `the gear does not ask for the profile`() {
        var asked = 0

        scaffold(onOpenProfile = { asked++ }) {
            onNodeWithTag(PlayerTestTags.SETTINGS, useUnmergedTree = true).performClick()
        }

        assertEquals(0, asked)
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
            // Shipyard, not a Ships-level marker: the default `ships` param composes the real
            // `ShipsScreen`, which opens on the Shipyard chip.
            OltreTab.SHIPS to SHIPYARD_MARKER,
            OltreTab.ALLIANCE to ALLIANCE_MARKER,
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
            onNodeWithTag(ShellTestTags.tab(OltreTab.SHIPS)).performClick()
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

    // **The Ships destination's own switch, one level under the tab bar.** Shipyard and Fleets are
    // no longer tabs of their own (0.23.0) — they are `ShipsHead`'s two chips — so this is the
    // test that used to be "the Shipyard tab shows its screen rather than Fleets", one level down.
    @Test
    fun `the Ships tab's head switches between Shipyard and Fleets`() {
        scaffold {
            onNodeWithTag(ShellTestTags.tab(OltreTab.SHIPS)).performClick()
            onNodeWithText(SHIPYARD_MARKER).assertIsDisplayed()
            onNodeWithText(FLEETS_MARKER).assertDoesNotExist()

            onNodeWithTag(ShellTestTags.shipsMode(ShipsMode.FLEETS)).performClick()
            onNodeWithText(FLEETS_MARKER).assertIsDisplayed()
            onNodeWithText(SHIPYARD_MARKER).assertDoesNotExist()

            onNodeWithTag(ShellTestTags.shipsMode(ShipsMode.SHIPYARD)).performClick()
            onNodeWithText(SHIPYARD_MARKER).assertIsDisplayed()
            onNodeWithText(FLEETS_MARKER).assertDoesNotExist()
        }
    }

    // **The bug Davide caught**: the chip used to be `remember`ed inside `ShipsScreen`, which
    // `AnimatedContent` tears down the moment another destination is selected — so leaving Ships on
    // Fleets and coming back always found Shipyard again. `MainScaffold` hoists the mode now, the
    // same way it already hoists `selected` and every `ScrollState`, and this is the regression test
    // for that: through Colony and back, not just a re-click of the same tab.
    @Test
    fun `the Ships chip is still Fleets after a trip through Colony`() {
        scaffold {
            onNodeWithTag(ShellTestTags.tab(OltreTab.SHIPS)).performClick()
            onNodeWithTag(ShellTestTags.shipsMode(ShipsMode.FLEETS)).performClick()
            onNodeWithText(FLEETS_MARKER).assertIsDisplayed()

            onNodeWithTag(ShellTestTags.tab(OltreTab.COLONY)).performClick()
            onNodeWithText(COLONY_MARKER).assertIsDisplayed()

            onNodeWithTag(ShellTestTags.tab(OltreTab.SHIPS)).performClick()
            onNodeWithText(FLEETS_MARKER).assertIsDisplayed()
            onNodeWithText(SHIPYARD_MARKER).assertDoesNotExist()
        }
    }

    // **The line a dropped connection draws, live rather than only at launch.** Every other test
    // hands `offline` a fixed `null` for the whole session — realistic for "the game opens with a
    // signal," but a player's connection actually drops and returns while a destination stays open,
    // which `AppBehaviourTest` only ever exercises at startup. This drives the transition itself, in
    // one continuous composition, and checks the destination underneath never moves for it.
    @Test
    fun `the offline line appears and disappears without losing the destination`() {
        lateinit var offline: MutableState<OfflineLineUiState?>
        runDesktopComposeUiTest(width = 393, height = 852) {
            setContent {
                offline = remember { mutableStateOf(null) }
                OltreTheme {
                    MainScaffold(
                        tilt = { Tilt.NONE },
                        player = testPlayerStripUiState,
                        resources = testResourceRailUiState,
                        colony = { Text(COLONY_MARKER) },
                        research = { Text(RESEARCH_MARKER) },
                        galaxy = { _, _ -> Text(GALAXY_MARKER) },
                        ships = { scroll, mode, onSelectMode ->
                            ShipsScreen(
                                scrollState = scroll,
                                mode = mode,
                                onSelectMode = onSelectMode,
                                shipyard = { Text(SHIPYARD_MARKER) },
                                fleets = { Text(FLEETS_MARKER) },
                            )
                        },
                        alliance = { Text(ALLIANCE_MARKER) },
                        offline = offline.value,
                        onOpenSettings = {},
                        onOpenProfile = {},
                    )
                }
            }
            onNodeWithTag(ShellTestTags.tab(OltreTab.RESEARCH)).performClick()
            onNodeWithText(RESEARCH_MARKER).assertIsDisplayed()

            val line = English.resolve(Strings.offlineSince(hour = 11, minute = 31, held = 3, compact = false))
            onNodeWithText(line).assertDoesNotExist()

            runOnIdle { offline.value = OfflineLineUiState(text = Strings.offlineSince(hour = 11, minute = 31, held = 3, compact = false)) }
            onNodeWithText(line).assertIsDisplayed()
            onNodeWithText(RESEARCH_MARKER).assertIsDisplayed()

            runOnIdle { offline.value = null }
            onNodeWithText(line).assertDoesNotExist()
            onNodeWithText(RESEARCH_MARKER).assertIsDisplayed()
        }
    }

    // **The Alliance tab's real screen, not a marker** — the one test in this file that drives the
    // actual composable rather than a stand-in, because the whole point of `AllianceScreen` is the
    // words it says, and a marker cannot be wrong about them.
    //
    // It read the coming-soon copy until the screens landed; what it asserts now is the face a tab
    // opened with no network draws, which is the one the shell can reach without a gateway.
    @Test
    fun `the Alliance tab shows the face it is handed`() {
        scaffold(
            alliance = { scroll ->
                AllianceScreen(
                    state = AllianceUiState.Held,
                    actions = AllianceActions(),
                    scrollState = scroll,
                )
            },
        ) {
            onNodeWithTag(ShellTestTags.tab(OltreTab.ALLIANCE)).performClick()
            onNodeWithText(English.resolve(Strings.allianceSearchHeld())).assertIsDisplayed()
        }
    }

    // A phone-sized window: the bar has to fit five destinations at the narrowest width the game
    // actually ships at.
    private fun scaffold(
        pauseTheClock: Boolean = false,
        onOpenSettings: () -> Unit = {},
        onOpenProfile: () -> Unit = {},
        // The real `ShipsScreen` by default, with markers standing in for its two halves — the
        // mode-persistence test needs it composed for real, since a flat marker has no chip to
        // switch and nothing to lose.
        ships: @Composable (ScrollState, ShipsMode, (ShipsMode) -> Unit) -> Unit = { scroll, mode, onSelectMode ->
            ShipsScreen(
                scrollState = scroll,
                mode = mode,
                onSelectMode = onSelectMode,
                shipyard = { Text(SHIPYARD_MARKER) },
                fleets = { Text(FLEETS_MARKER) },
            )
        },
        alliance: @Composable (ScrollState) -> Unit = { Text(ALLIANCE_MARKER) },
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
                        ships = ships,
                        alliance = alliance,
                        // Null: a colony with signal, which is what every test here that is not about
                        // the chrome line wants. The line has its own tests.
                        offline = null,
                        // Handed in rather than ignored, because one test here is about the gear:
                        // that pressing it asks for something. *What* it opens is `App`'s — both
                        // modals in this app are raised there — and `AlertSheetAppBehaviourTest` is
                        // where the sheet actually goes up and comes down.
                        onOpenSettings = onOpenSettings,
                        // The other control on the strip, handed in for the same reason and asserted
                        // the same way. `IdentityAppBehaviourTest` is where the face it opens arrives.
                        onOpenProfile = onOpenProfile,
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
        const val ALLIANCE_MARKER = "alliance-under-test"
        // Ships has no marker of its own — the default `ships` param composes the real
        // `ShipsScreen`, so its two halves stand in for it instead.
        const val SHIPYARD_MARKER = "shipyard-under-test"
        const val FLEETS_MARKER = "fleets-under-test"
    }
}
