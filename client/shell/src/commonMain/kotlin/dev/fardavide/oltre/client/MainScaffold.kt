package dev.fardavide.oltre.client

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
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
        ResourceRail(uiState = resources)
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
        OltreTabBar(selected = selected, onSelect = { selected = it })
    }
}

@Composable
private fun ColumnScope.Destination(
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
    Box(modifier = Modifier.fillMaxWidth().weight(1f)) {
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
        // Exhaustive on purpose, and it is what a sixth destination will have to answer to: every
        // branch names a screen now, so a tab added without one cannot compile rather than falling
        // through to an apology.
        when (selected) {
            OltreTab.COLONY -> colony(colonyScroll)
            OltreTab.RESEARCH -> research(researchScroll)
            OltreTab.GALAXY -> galaxy(galaxyScroll, onOpenResearch)
            OltreTab.SHIPYARD -> shipyard(shipyardScroll)
            OltreTab.FLEETS -> fleets(fleetsScroll)
        }
    }
}
