package dev.fardavide.oltre.client

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier

// The app's frame: the selected destination over the tab bar. Which destination is showing is the
// scaffold's own state — this is the composition root's navigation, and nothing above it has an
// opinion about it.
//
// Deliberately not remembered across launches. The colony is recomputed from the save every time
// the app opens, so opening on the Colony tab is what the player actually wants to see; restoring
// "you were on Fleets" would restore a screen that is not built yet.
//
// Each feature that lands takes a parameter here, so this signature is the honest list of what
// exists — the tabs with no parameter are the ones that are not built. `resources` is the
// exception that proves it: the rail is chrome rather than a feature, framing every destination
// exactly as the tab bar does.
// `galaxy` takes a parameter the other two do not: the way to the Research tab. A blocked world
// names the ladder that would land it, and since 0.0.18 that string is a tap target — but which
// destination is showing is the scaffold's state, so the galaxy cannot select a tab and must be
// handed the ability to ask. It stays a lambda rather than becoming a hoisted `selected` because
// the feature has no opinion about tabs beyond "take me to that one".
@Composable
fun MainScaffold(
    resources: ResourceRailUiState,
    colony: @Composable () -> Unit,
    research: @Composable () -> Unit,
    galaxy: @Composable (onOpenResearch: () -> Unit) -> Unit,
    modifier: Modifier = Modifier,
) {
    var selected by remember { mutableStateOf(OltreTab.COLONY) }
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
            onOpenResearch = { selected = OltreTab.RESEARCH },
        )
        OltreTabBar(selected = selected, onSelect = { selected = it })
    }
}

@Composable
private fun ColumnScope.Destination(
    selected: OltreTab,
    colony: @Composable () -> Unit,
    research: @Composable () -> Unit,
    galaxy: @Composable (onOpenResearch: () -> Unit) -> Unit,
    onOpenResearch: () -> Unit,
) {
    Box(modifier = Modifier.fillMaxWidth().weight(1f)) {
        // Exhaustive on purpose: a `when` over the destinations is what makes a tab with no screen
        // impossible to reach by accident, and `pendingWork` is the table saying which those are.
        when (selected) {
            OltreTab.COLONY -> colony()
            OltreTab.RESEARCH -> research()
            OltreTab.GALAXY -> galaxy(onOpenResearch)
            OltreTab.SHIPYARD,
            OltreTab.FLEETS,
            -> UnbuiltTabScreen(
                tab = selected,
                pendingWork = checkNotNull(selected.pendingWork) {
                    "${selected.label} has no screen and no pending-work line"
                },
            )
        }
    }
}
