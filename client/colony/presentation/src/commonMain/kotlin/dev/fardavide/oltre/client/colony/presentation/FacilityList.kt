package dev.fardavide.oltre.client.colony.presentation

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import dev.fardavide.oltre.client.design.OltreColors
import dev.fardavide.oltre.client.design.oltreMono
import dev.fardavide.oltre.core.BuildingType
import dev.fardavide.oltre.core.ResourceKind

// Facility rows per the mockup: per-resource affordability by colour, a duration chip on every
// unlockable row, time-until-affordable in the action slot instead of a dead button, and locked
// rows dimmed with their requirement. A facility that is building carries its own countdown and
// progress bar — upgrades run in parallel, so there is no single build to hoist into a card.
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
    val locked = row.action is FacilityActionUiState.Locked
    val upgrading = row.action as? FacilityActionUiState.Upgrading
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .alpha(if (locked) 0.42f else 1f)
            .border(
                1.dp,
                if (upgrading != null) OltreColors.accent.copy(alpha = 0.45f) else Color.White.copy(alpha = 0.09f),
                RoundedCornerShape(14.dp),
            )
            .background(Color.White.copy(alpha = 0.045f), RoundedCornerShape(14.dp))
            .padding(11.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = row.name,
                        color = OltreColors.text,
                        fontFamily = mono,
                        fontSize = 13.5.sp,
                        fontWeight = FontWeight.Medium,
                    )
                    Text(
                        text = "LV ${row.level.value}",
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
                when (val action = row.action) {
                    is FacilityActionUiState.Locked -> Text(
                        text = action.reason,
                        color = OltreColors.textSecondary,
                        fontFamily = mono,
                        fontSize = 10.5.sp,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                    is FacilityActionUiState.Upgrading -> Text(
                        text = "→ LV ${action.toLevel.value} · ${action.doneAt}",
                        color = OltreColors.accent,
                        fontFamily = mono,
                        fontSize = 10.5.sp,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                    FacilityActionUiState.Upgrade,
                    is FacilityActionUiState.AffordableIn,
                    -> Row(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.padding(top = 4.dp),
                    ) {
                        row.costs.forEach { chip -> CostChip(chip = chip) }
                        Text(
                            text = row.duration,
                            color = OltreColors.textSecondary,
                            fontFamily = mono,
                            fontSize = 10.5.sp,
                        )
                    }
                }
            }
            when (val action = row.action) {
                FacilityActionUiState.Upgrade -> Text(
                    text = "Upgrade",
                    color = Color.White,
                    fontFamily = mono,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier
                        .background(OltreColors.accent, RoundedCornerShape(9.dp))
                        .clickable { onUpgrade(row.building) }
                        .padding(horizontal = 11.dp, vertical = 7.dp),
                )
                is FacilityActionUiState.AffordableIn -> Text(
                    text = action.label,
                    color = OltreColors.textTertiary,
                    fontFamily = mono,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier
                        .border(1.dp, Color.White.copy(alpha = 0.16f), RoundedCornerShape(9.dp))
                        .padding(horizontal = 11.dp, vertical = 7.dp),
                )
                is FacilityActionUiState.Upgrading -> Text(
                    text = action.countdown,
                    color = OltreColors.text,
                    fontFamily = mono,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(start = 8.dp),
                )
                is FacilityActionUiState.Locked -> Unit
            }
        }
        if (upgrading != null) {
            ProgressBar(percent = upgrading.progressPercent)
        }
    }
}

@Composable
private fun ProgressBar(percent: Int) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 10.dp)
            .height(3.dp)
            .background(Color.White.copy(alpha = 0.09f), RoundedCornerShape(2.dp)),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(percent / 100f)
                .fillMaxHeight()
                .background(OltreColors.accent, RoundedCornerShape(2.dp)),
        )
    }
}

@Composable
private fun CostChip(chip: CostChipUiState) {
    val tint = when (chip.kind) {
        ResourceKind.METAL -> OltreColors.metal
        ResourceKind.CRYSTAL -> OltreColors.crystal
        ResourceKind.DEUTERIUM -> OltreColors.deuterium
    }
    Text(
        text = chip.amount,
        color = if (chip.short) OltreColors.danger else tint,
        fontFamily = oltreMono(),
        fontSize = 10.5.sp,
    )
}
