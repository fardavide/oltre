package dev.fardavide.oltre.client.design.component

import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import dev.fardavide.oltre.client.design.core.OltreColors
import dev.fardavide.oltre.client.design.core.OltreLayout

// **The only way this app raises a panel over a screen.** Every sheet in Oltre goes through here, and
// that is the whole point of the file: what it holds is four lines of configuration, and the four
// lines were copied into three places before this existed.
//
// The rule it makes unavoidable is *be a `ModalBottomSheet`, do not resemble one*. A Box parked at
// the bottom of a page's own layout looks right in a screenshot and is wrong in a hand: it sits
// inside the destination's slot, so it stops at the tab bar instead of covering it; it has no
// pointer input, so a drag on it scrolls whatever is behind it; its handle is a drawing that does
// not drag; and it has no insets, no back gesture and no enter animation. The debug menu was that
// panel until 0.2.6 and the dispatch sheet was it again at 0.7.0 — the same four faults twice, from
// the same cause, which is a feature having to know how a sheet is built.
//
// Chrome only. Every sheet keeps its contents in a separate composable, so the assertions about what
// a sheet *says* are written against those and never depend on a popup being reachable from a test
// tree or on an enter animation having settled.
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OltreBottomSheet(
    // One callback for every way out — the drag, the scrim, the system back gesture — so a sheet
    // cannot be dismissed by a route its screen does not hear about.
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        modifier = modifier,
        // Straight to full height, never half: a partially expanded state is a size nothing in this
        // app has a design for, and what it would cut off is the arithmetic each sheet exists to
        // carry.
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        // The same cap the screens use, so a sheet on an iPad is the width of the column it was
        // raised from rather than of Material's own 640dp default.
        sheetMaxWidth = OltreLayout.maxContentWidth,
        // The theme populates Material's palette roles but not its container roles, so a sheet would
        // otherwise take stock `darkColorScheme()` grey. This is the rail's own surface.
        containerColor = OltreColors.surface,
        dragHandle = { BottomSheetDefaults.DragHandle(color = OltreColors.textTertiary) },
        content = { content() },
    )
}
