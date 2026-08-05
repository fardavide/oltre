package dev.fardavide.oltre.client.colony.presentation

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.fardavide.oltre.core.BuildingType
import dev.fardavide.oltre.client.design.OltreColors
import dev.fardavide.oltre.client.design.oltreMono

// Facility rows per the mockup: affordability shown by colour, locked rows dimmed with their
// requirement instead of a dead button.
@Composable
fun FacilityList(
    facilities: List<FacilityRowUiState>,
    onUpgrade: (BuildingType) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        facilities.forEach { row ->
            FacilityRow(row = row, onUpgrade = onUpgrade)
        }
    }
}

@Composable
private fun FacilityRow(row: FacilityRowUiState, onUpgrade: (BuildingType) -> Unit) {
    val mono = oltreMono()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .alpha(if (row.locked) 0.42f else 1f)
            .border(1.dp, Color.White.copy(alpha = 0.09f), RoundedCornerShape(14.dp))
            .background(Color.White.copy(alpha = 0.045f), RoundedCornerShape(14.dp))
            .padding(11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = row.name,
                    color = OltreColors.text,
                    fontSize = 13.5.sp,
                    fontWeight = FontWeight.Medium,
                )
                Text(
                    text = "LV ${row.level}",
                    color = OltreColors.textSecondary,
                    fontFamily = mono,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .padding(start = 7.dp)
                        .background(Color.White.copy(alpha = 0.09f), RoundedCornerShape(4.dp))
                        .padding(horizontal = 5.dp, vertical = 2.dp),
                )
            }
            if (row.locked && row.lockedReason != null) {
                Text(
                    text = row.lockedReason,
                    color = OltreColors.textSecondary,
                    fontFamily = mono,
                    fontSize = 10.5.sp,
                    modifier = Modifier.padding(top = 4.dp),
                )
            } else {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.padding(top = 4.dp),
                ) {
                    CostChip(amount = row.metalCost, tint = OltreColors.metal, short = !row.affordable)
                    CostChip(amount = row.crystalCost, tint = OltreColors.crystal, short = !row.affordable)
                }
            }
        }
        if (!row.locked) {
            Text(
                text = "Upgrade",
                color = if (row.affordable) Color.White else OltreColors.textTertiary,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier
                    .background(
                        if (row.affordable) OltreColors.accent else Color.Transparent,
                        RoundedCornerShape(9.dp),
                    )
                    .border(
                        1.dp,
                        if (row.affordable) Color.Transparent else Color.White.copy(alpha = 0.16f),
                        RoundedCornerShape(9.dp),
                    )
                    .clickable(enabled = row.affordable) { onUpgrade(row.building) }
                    .padding(horizontal = 11.dp, vertical = 7.dp),
            )
        }
    }
}

@Composable
private fun CostChip(amount: String, tint: Color, short: Boolean) {
    Text(
        text = amount,
        color = if (short) OltreColors.danger else tint,
        fontFamily = oltreMono(),
        fontSize = 10.5.sp,
    )
}
