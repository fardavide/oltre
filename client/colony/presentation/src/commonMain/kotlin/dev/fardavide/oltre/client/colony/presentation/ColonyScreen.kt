package dev.fardavide.oltre.client.colony.presentation

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.font.FontWeight
import dev.fardavide.oltre.core.BuildingType
import dev.fardavide.oltre.client.design.OltreColors

@Composable
fun ColonyScreen(
    uiState: ColonyUiState,
    onUpgrade: (BuildingType) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxSize()) {
        ResourceRail(uiState = uiState)
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
        ) {
            uiState.inProgress?.let { card ->
                SectionLabel(text = "IN PROGRESS")
                InProgressCard(uiState = card, modifier = Modifier.padding(bottom = 22.dp))
            }
            SectionLabel(text = "FACILITIES")
            FacilityList(facilities = uiState.facilities, onUpgrade = onUpgrade)
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text,
        color = OltreColors.textTertiary,
        fontSize = 10.5.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 1.4.sp,
        modifier = Modifier.padding(bottom = 9.dp, start = 2.dp),
    )
}
