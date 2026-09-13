package dev.fardavide.oltre.client

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

// The destination bought by merging Shipyard and Fleets, `alliance-sheet.md` §7: one subject, a
// hull built then flown, so one tab rather than two. `ShipyardScreen` and `FleetsScreen` are
// untouched — this composes them rather than replacing either, and each feature stays unaware the
// other exists, exactly as the module-rules skill asks of two features composed only by the shell.
//
// **One `ScrollState` shared by both halves, not one each.** Every other destination gets one
// `ScrollState` because it is one screen; Ships is now one destination too; and the starfield
// behind it reads whichever scroll `MainScaffold` hands this tab. The cost is real and stated
// rather than hidden: switching chips does not preserve each half's own scroll position the way
// switching tabs used to. Narrow scope, on the record — restoring per-mode scroll is a follow-up
// if it turns out to matter, not a thing this slice silently dropped.
@Composable
internal fun ShipsScreen(
    scrollState: ScrollState,
    shipyard: @Composable (ScrollState) -> Unit,
    fleets: @Composable (ScrollState) -> Unit,
    modifier: Modifier = Modifier,
) {
    var mode by remember { mutableStateOf(ShipsMode.SHIPYARD) }
    Column(modifier = modifier) {
        ShipsHead(
            mode = mode,
            onSelectMode = { mode = it },
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 9.dp),
        )
        when (mode) {
            ShipsMode.SHIPYARD -> shipyard(scrollState)
            ShipsMode.FLEETS -> fleets(scrollState)
        }
    }
}
