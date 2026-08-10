package dev.fardavide.oltre.client.colony.presentation

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
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
    Column(modifier = modifier.fillMaxSize()) {
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
                    onUpgrade = onUpgrade,
                    onToggleWatch = onToggleWatch,
                )
            }
        }
    }
}
