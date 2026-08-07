package dev.fardavide.oltre.client.galaxy.presentation

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import dev.fardavide.oltre.client.design.core.OltreLayout
import dev.fardavide.oltre.core.GalaxyBalance
import dev.fardavide.oltre.core.GalaxyState

// The Galaxy tab. One system fills the screen: the map is its fifteen orbits drawn once, left to
// right, hot to cold, and the list below carries only the slots that hold something.
//
// **Which system is showing is this feature's own state, not the shell's.** The tab set names every
// feature, which is why navigation between tabs lives in the composition root — but a system
// selector names only the galaxy, and nothing outside this module has an opinion about it. So this
// composable is the one screen in the app that holds state, and `GalaxyPage` below is the stateless
// half the screenshots and the robot drive.
@Composable
fun GalaxyScreen(galaxy: GalaxyState, modifier: Modifier = Modifier) {
    var at by remember(galaxy.seed) {
        mutableStateOf(SystemSelection(galaxy = galaxy.home.galaxy, system = galaxy.home.system))
    }
    GalaxyPage(
        uiState = galaxy.toGalaxyUiState(at = at),
        onSelectGalaxy = { selected -> at = at.copy(galaxy = selected) },
        // Clamped rather than wrapped: 250 is the edge of a galaxy, and a stepper that jumps from
        // the last system to the first would be a different move than the one it looks like.
        onStepSystem = { step ->
            at = at.copy(system = (at.system + step).coerceIn(1, GalaxyBalance.SYSTEMS_PER_GALAXY))
        },
        onGoHome = { at = SystemSelection(galaxy = galaxy.home.galaxy, system = galaxy.home.system) },
        modifier = modifier,
    )
}

@Composable
internal fun GalaxyPage(
    uiState: GalaxyUiState,
    onSelectGalaxy: (Int) -> Unit,
    onStepSystem: (Int) -> Unit,
    onGoHome: () -> Unit,
    modifier: Modifier = Modifier,
) {
    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        // A width decision, not a change of voice: below this the header drops the word "worlds".
        // Measured on the window rather than on the capped column, because it is the window that is
        // a Slide Over pane.
        val compact = maxWidth < OltreLayout.compactWidth
        Column(
            modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Column(
                modifier = Modifier
                    .widthIn(max = OltreLayout.maxContentWidth)
                    .fillMaxWidth()
                    // Ahead of the padding, so the bounds a layout test reads are the column's own
                    // rather than its padded interior.
                    .testTag(GalaxyTestTags.CONTENT)
                    .padding(16.dp),
            ) {
                GalaxyNav(
                    uiState = uiState,
                    compact = compact,
                    onSelectGalaxy = onSelectGalaxy,
                    onStepSystem = onStepSystem,
                    onGoHome = onGoHome,
                    modifier = Modifier.padding(bottom = 13.dp),
                )
                // The map sits on the same card surface every row does, so the screen reads as one
                // stack rather than as a picture with a list under it.
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, Color.White.copy(alpha = 0.09f), RoundedCornerShape(14.dp))
                        .background(Color.White.copy(alpha = 0.045f), RoundedCornerShape(14.dp))
                        .testTag(GalaxyTestTags.MAP)
                        .padding(11.dp),
                ) {
                    SystemMap(map = uiState.map)
                }
                WorldList(
                    bands = uiState.bands,
                    compact = compact,
                    modifier = Modifier.padding(top = 13.dp),
                )
            }
        }
    }
}
