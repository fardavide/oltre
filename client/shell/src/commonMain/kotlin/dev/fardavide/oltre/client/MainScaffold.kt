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
@Composable
fun MainScaffold(
    resources: ResourceRailUiState,
    modifier: Modifier = Modifier,
    colony: @Composable () -> Unit,
) {
    var selected by remember { mutableStateOf(OltreTab.COLONY) }
    Column(
        // Insets are the frame's job, not a screen's: every tab sits inside the same safe area,
        // and the bar has to clear the home indicator whatever is above it.
        modifier = modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing),
    ) {
        ResourceRail(uiState = resources)
        Destination(selected = selected, colony = colony)
        OltreTabBar(selected = selected, onSelect = { selected = it })
    }
}

@Composable
private fun ColumnScope.Destination(selected: OltreTab, colony: @Composable () -> Unit) {
    Box(modifier = Modifier.fillMaxWidth().weight(1f)) {
        val pendingWork = selected.pendingWork
        if (pendingWork == null) colony() else UnbuiltTabScreen(tab = selected, pendingWork = pendingWork)
    }
}
