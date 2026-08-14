package dev.fardavide.oltre.client.colony.ui

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import dev.fardavide.oltre.client.design.component.RowSheet
import dev.fardavide.oltre.client.design.component.SectionLabel
import dev.fardavide.oltre.client.design.core.OltreLayout
import dev.fardavide.oltre.core.BuildingType

@Composable
fun ColonyScreen(
    uiState: ColonyUiState,
    onUpgrade: (BuildingType) -> Unit,
    onToggleWatch: (BuildingType) -> Unit,
    // Hoisted since the Sky pass, because the starfield behind this screen shifts with it and the
    // frame that draws the field is the shell's. Defaulted so that the screenshot fixtures and the
    // layout assertions, none of which scroll, still read as a screen and not as a wiring exercise.
    scrollState: ScrollState = rememberScrollState(),
    modifier: Modifier = Modifier,
) {
    // Which row has its arithmetic open, held here rather than in the shell. The sheet is a second
    // rendering of a row this screen already has, so nothing about it crosses the module boundary
    // and `App` keeps the parameter list it has. The row is named rather than captured, so a sheet
    // left open keeps counting down with the card behind it.
    var open by remember { mutableStateOf<BuildingType?>(null) }
    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        // A width decision, not a change of voice, and the same one Research has made since 0.3:
        // below this the square stacks under the ghost time and one facility name shortens.
        // Measured on the window rather than on the capped column, because it is the window that is
        // a Slide Over pane.
        val compact = maxWidth < OltreLayout.compactWidth
        // The window can be any width — iPad, Split View, Stage Manager, a desktop window — so
        // the colony caps its content and centres it instead of stretching the cards.
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Column(
                modifier = Modifier
                    .widthIn(max = OltreLayout.maxContentWidth)
                    .fillMaxWidth()
                    // Ahead of the padding: a tag placed after it marks the padded interior, so
                    // the bounds a test reads would be 32dp narrower than the column itself.
                    .testTag(ColonyTestTags.CONTENT)
                    .padding(16.dp),
            ) {
                uiState.returningFleet?.let { fleet ->
                    FleetStrip(uiState = fleet, modifier = Modifier.padding(bottom = 22.dp))
                }
                // First in the column, so the reading order is the state of the colony and then
                // the things that produce it.
                PowerIndicator(uiState = uiState.energy, modifier = Modifier.padding(bottom = 8.dp))
                // The label's trailing slot carries the watch, which is what makes one slot shared
                // across three ladders legible: it names the watched row even when that row is on
                // the Research tab, so moving the watch there is never a thing that happened
                // somewhere the player was not looking.
                SectionLabel(text = "FACILITIES", rule = uiState.watching)
                FacilityList(
                    facilities = uiState.facilities,
                    compact = compact,
                    onUpgrade = onUpgrade,
                    onToggleWatch = onToggleWatch,
                    onOpenDetail = { open = it },
                )
            }
        }
        open?.let { building ->
            uiState.facilities.firstOrNull { it.building == building }?.let { row ->
                RowSheet(
                    uiState = row.toRowSheetUiState(),
                    // Acting from the sheet is acting on the row, and by then the sheet has said
                    // everything it had to say — leaving it up over a row that is now building
                    // would be leaving up an argument for a decision already taken.
                    onAct = {
                        open = null
                        onUpgrade(building)
                    },
                    onDismiss = { open = null },
                    contentModifier = Modifier.testTag(ColonyTestTags.SHEET),
                    actionModifier = Modifier.testTag(ColonyTestTags.SHEET_ACTION),
                )
            }
        }
    }
}
