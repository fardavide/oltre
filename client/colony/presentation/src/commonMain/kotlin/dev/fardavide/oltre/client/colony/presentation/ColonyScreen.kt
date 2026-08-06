package dev.fardavide.oltre.client.colony.presentation

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.font.FontWeight
import dev.fardavide.oltre.client.design.OltreColors
import dev.fardavide.oltre.client.design.OltreLayout
import dev.fardavide.oltre.client.design.oltreMono
import dev.fardavide.oltre.core.BuildingType

@Composable
fun ColonyScreen(
    uiState: ColonyUiState,
    onUpgrade: (BuildingType) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxSize()) {
        // The window can be any width — iPad, Split View, Stage Manager, a desktop window — so
        // the colony caps its content and centres it instead of stretching the cards.
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
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
                SectionLabel(text = "FACILITIES")
                FacilityList(facilities = uiState.facilities, onUpgrade = onUpgrade)
            }
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text,
        color = OltreColors.textTertiary,
        fontFamily = oltreMono(),
        fontSize = 10.5.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 1.4.sp,
        modifier = Modifier.padding(bottom = 9.dp, start = 2.dp),
    )
}
