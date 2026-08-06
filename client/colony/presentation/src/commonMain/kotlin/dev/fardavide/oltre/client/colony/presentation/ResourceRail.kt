package dev.fardavide.oltre.client.colony.presentation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.fardavide.oltre.client.design.OltreColors
import dev.fardavide.oltre.client.design.oltreMono

@Composable
fun ResourceRail(uiState: ColonyUiState, modifier: Modifier = Modifier) {
    Row(modifier = modifier.fillMaxWidth().background(OltreColors.surface)) {
        ResourceCell(name = "METAL", value = uiState.metal, rate = uiState.metalRatePerHour)
        ResourceCell(name = "CRYSTAL", value = uiState.crystal, rate = uiState.crystalRatePerHour)
        ResourceCell(name = "DEUTERIUM", value = uiState.deuterium, rate = uiState.deuteriumRatePerHour)
    }
}

@Composable
private fun RowScope.ResourceCell(name: String, value: String, rate: String) {
    val mono = oltreMono()
    Column(modifier = Modifier.weight(1f).padding(horizontal = 11.dp, vertical = 9.dp)) {
        Text(
            text = name,
            color = OltreColors.textTertiary,
            fontFamily = mono,
            fontSize = 9.5.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.sp,
        )
        Text(
            text = value,
            color = OltreColors.text,
            fontFamily = mono,
            fontSize = 15.sp,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            text = rate,
            color = OltreColors.ok,
            fontFamily = mono,
            fontSize = 10.sp,
        )
    }
}
