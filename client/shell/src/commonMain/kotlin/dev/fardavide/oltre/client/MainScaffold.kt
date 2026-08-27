package dev.fardavide.oltre.client

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.IntOffset
import dev.fardavide.oltre.client.design.core.OltreMotion
import dev.fardavide.oltre.client.player.ui.PlayerStrip
import dev.fardavide.oltre.client.player.ui.PlayerStripUiState
import dev.fardavide.oltre.client.tilt.domain.Tilt

// The app's frame: the selected destination over the tab bar. Which destination is showing is the
// scaffold's own state — this is the composition root's navigation, and nothing above it has an
// opinion about it.
//
// Deliberately not remembered across launches. The colony is recomputed from the save every time
// the app opens, so opening on the Colony tab is what the player actually wants to see; restoring
// "you were on Fleets" would restore a screen that is not built yet.
//
// Each feature that lands takes a parameter here, so this signature is the honest list of what
// exists — and since 0.8.0 the list is complete: five destinations, five parameters, and nothing
// left that the frame has to apologise for. `resources` is the exception that proves it: the rail is
// chrome rather than a feature, framing every destination exactly as the tab bar does.
// `galaxy` takes a parameter the other two do not: the way to the Research tab. A blocked world
// names the ladder that would land it, and since 0.0.18 that string is a tap target — but which
// destination is showing is the scaffold's state, so the galaxy cannot select a tab and must be
// handed the ability to ask. It stays a lambda rather than becoming a hoisted `selected` because
// the feature has no opinion about tabs beyond "take me to that one".
// Each destination takes the `ScrollState` it must scroll with rather than remembering one of its
// own, and that is the Sky pass's one structural change to this file. The starfield behind the
// destinations is the scaffold's, and it shifts with the list in front of it — so the scaffold has
// to be able to read how far that list has got. Hoisting the state is the only way to say it out
// loud; the alternative is a screen writing its offset into somewhere the frame happens to read,
// which is the same coupling with none of it visible in a signature.
//
// One state per destination rather than one shared, because they are three different lists: coming
// back to Colony from Research should find Colony where it was left, and a single hoisted state
// would make every tab scroll every other one.
@Composable
fun MainScaffold(
    // Ahead of `resources` because it is drawn ahead of it, and a parameter here for the reason the
    // rail is one: this signature is the honest list of what the frame carries. Two of its three
    // readings are folded off the save since 0.17, and it arrives as a parameter for the reason it
    // did when all three were constants: the frame draws what it is handed and asks no `GameState`
    // anything.
    player: PlayerStripUiState,
    resources: ResourceRailUiState,
    colony: @Composable (ScrollState) -> Unit,
    research: @Composable (ScrollState) -> Unit,
    galaxy: @Composable (ScrollState, onOpenResearch: () -> Unit) -> Unit,
    shipyard: @Composable (ScrollState) -> Unit,
    fleets: @Composable (ScrollState) -> Unit,
    // The second thing the field behind the destinations moves on, after the scroll above. A lambda
    // and not a value — `Starfield` argues both reasons, and the second one (Compose would infer a
    // `Tilt` parameter unstable and stop skipping this whole scaffold) is the load-bearing one.
    //
    // **Required rather than defaulted, deliberately.** The obvious default is `{ Tilt.NONE }`, and
    // it is also exactly the value that means *the feature is switched off* — so a composition root
    // that forgot to pass one would compile, ship, and quietly do nothing, with no test able to tell
    // the difference. `Starfield` keeps the default, where "no lean" is a real thing a screenshot
    // wants to ask for; here it would only ever be a way of not noticing.
    tilt: () -> Tilt,
    // **The one piece of chrome that is usually absent**, which is why it is nullable rather than a
    // flag: a colony with signal has no line at all and the 22dp goes back to the destination. See
    // `OfflineLine`, and see `GalaxyRobot.DESTINATION_HEIGHT`, which is the arithmetic this moves.
    offline: OfflineLineUiState?,
    // **The gear, forwarded rather than answered here** — and that is `debugOpen`'s precedent rather
    // than a new arrangement. Both modals in this app are raised by `App`: it already holds whether
    // the debug sheet is up, and a settings sheet held *here* instead would put the app's two
    // modals in two different places for no reason either could state.
    //
    // The frame keeps the *destination*, which is navigation and is genuinely this file's. A sheet
    // is not a destination — it is a thing raised over one, and it covers the tab bar this file owns.
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var selected by remember { mutableStateOf(OltreTab.COLONY) }
    val colonyScroll = rememberScrollState()
    val researchScroll = rememberScrollState()
    val galaxyScroll = rememberScrollState()
    val shipyardScroll = rememberScrollState()
    val fleetsScroll = rememberScrollState()
    Column(
        // Insets are the frame's job, not a screen's: every tab sits inside the same safe area,
        // and the bar has to clear the home indicator whatever is above it.
        modifier = modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing),
    ) {
        // Inside the safe-area padding and above the rail. Outside it, the notch would eat the mark
        // on every notched phone and nothing in the desktop suite could see that happen — insets are
        // the frame's job, never a screen's, which is the whole reason this Column has exactly one.
        PlayerStrip(uiState = player, onOpenSettings = onOpenSettings)
        ResourceRail(uiState = resources)
        // **Under the rail and above the destination**, which is where the design put it: it is a fact
        // about the connection the whole frame is on, so it belongs with the chrome rather than on any
        // one screen — and every destination loses the same 22dp rather than one of them losing more.
        offline?.let { OfflineLine(uiState = it) }
        Box(modifier = Modifier.fillMaxWidth().weight(1f)) {
            Destination(
                selected = selected,
                colony = colony,
                research = research,
                galaxy = galaxy,
                shipyard = shipyard,
                fleets = fleets,
                colonyScroll = colonyScroll,
                researchScroll = researchScroll,
                galaxyScroll = galaxyScroll,
                shipyardScroll = shipyardScroll,
                fleetsScroll = fleetsScroll,
                tilt = tilt,
                onOpenResearch = { selected = OltreTab.RESEARCH },
            )
        }
        OltreTabBar(selected = selected, onSelect = { selected = it })
    }
}

@Composable
private fun Destination(
    selected: OltreTab,
    colony: @Composable (ScrollState) -> Unit,
    research: @Composable (ScrollState) -> Unit,
    galaxy: @Composable (ScrollState, onOpenResearch: () -> Unit) -> Unit,
    shipyard: @Composable (ScrollState) -> Unit,
    fleets: @Composable (ScrollState) -> Unit,
    colonyScroll: ScrollState,
    researchScroll: ScrollState,
    galaxyScroll: ScrollState,
    shipyardScroll: ScrollState,
    fleetsScroll: ScrollState,
    tilt: () -> Tilt,
    onOpenResearch: () -> Unit,
) {
    // Every destination scrolls now, so the field behind every one of them moves. The nullable this
    // used to be — "the two tabs with no screen have nothing to scroll" — went with the two tabs
    // that had no screen; a `null` branch kept for a case that cannot occur is a case a reader has
    // to rule out on every pass.
    val scroll = when (selected) {
        OltreTab.COLONY -> colonyScroll
        OltreTab.RESEARCH -> researchScroll
        OltreTab.GALAXY -> galaxyScroll
        OltreTab.SHIPYARD -> shipyardScroll
        OltreTab.FLEETS -> fleetsScroll
    }
    Box(modifier = Modifier.fillMaxSize()) {
        // Inside the destination box and first in it, so it sits under every screen and under none
        // of the chrome: the rail and the tab bar are surfaces, and space does not show through a
        // surface.
        //
        // The three planes are a function of the scroll offset and of how the device is being held,
        // and of nothing else — **nothing here starts on its own.** A frame that has been closed for
        // two days opens drawn exactly as it was left, and a phone put down leaves the sky where it
        // is. Until 0.4.3 that last clause was false — a lean went on settling back to level for
        // about ten seconds after the hand stopped — and `Starfield` carries what removing it cost
        // and bought. What it is not, on any reading, is the game telling you something is happening.
        //
        // Both go in as lambdas so that a drag or a lean is a redraw rather than a recomposition of
        // the whole destination.
        Starfield(scrollOffset = { scroll.value.toFloat() }, tilt = tilt)
        // **The one place the app navigates, so the one place a navigation can be drawn.** Until
        // 0.13.1 this was a bare `when` and a tab change was a hard cut: five screens swapped between
        // two frames with nothing between them, which is the single thing that made the app read as
        // a set of screens rather than as one.
        //
        // It travels in the direction the bar does. Tapping Galaxy from Colony moves left-to-right in
        // the tab bar, so the arriving screen enters from the right and the leaving one goes left —
        // the destinations keep the order the player can see at the bottom of the window, and a tab
        // two along does not travel twice as far for it.
        //
        // The starfield above is deliberately outside this: it is the frame's, not a destination's,
        // and a sky that slid with the content would be a sky attached to the screen instead of
        // behind it.
        AnimatedContent(
            targetState = selected,
            transitionSpec = {
                val forward = targetState.ordinal > initialState.ordinal
                val travel = { width: Int -> (width * OltreMotion.SWITCH_TRAVEL).toInt() }
                val spec = tween<IntOffset>(OltreMotion.SWITCH_MILLIS, easing = OltreMotion.Settle)
                val fade = tween<Float>(OltreMotion.SWITCH_MILLIS, easing = OltreMotion.Settle)
                val enter = slideInHorizontally(spec) { if (forward) travel(it) else -travel(it) } +
                    fadeIn(fade)
                val exit = slideOutHorizontally(spec) { if (forward) -travel(it) else travel(it) } +
                    fadeOut(fade)
                // No size animation between the two: every destination fills the same box, so the
                // default `SizeTransform` would be animating a measurement that never changes and
                // clipping the pair while it did it.
                enter togetherWith exit using null
            },
            // Names the transition for the Compose animation inspector and for nothing else — it is
            // not a testTag, and this scaffold has none.
            label = "destination",
        ) { destination ->
            // Exhaustive on purpose, and it is what a sixth destination will have to answer to:
            // every branch names a screen now, so a tab added without one cannot compile rather
            // than falling through to an apology.
            //
            // Driven by the animation's own state rather than by `selected`, which is what keeps the
            // outgoing screen showing the screen it *is* for the 210ms it spends leaving.
            when (destination) {
                OltreTab.COLONY -> colony(colonyScroll)
                OltreTab.RESEARCH -> research(researchScroll)
                OltreTab.GALAXY -> galaxy(galaxyScroll, onOpenResearch)
                OltreTab.SHIPYARD -> shipyard(shipyardScroll)
                OltreTab.FLEETS -> fleets(fleetsScroll)
            }
        }
    }
}
