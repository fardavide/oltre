package dev.fardavide.oltre.client.design.component

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.sp
import dev.fardavide.oltre.client.design.core.OltreColors
import dev.fardavide.oltre.client.design.core.oltreMono
import dev.fardavide.oltre.core.ResourceKind

// What one resource of a price reads as. Colour is the affordability channel — red is the one you
// are short of — which is why `short` is a property of the chip rather than of the row: a price
// the player cannot meet is usually only short in one of its three terms, and saying which is the
// whole point.
data class CostChipUiState(
    val kind: ResourceKind,
    val amount: String,
    val short: Boolean,
)

// Shared by the Colony's facility rows and Research's technology rows. It was duplicated between
// them until 0.0.14 under the rule that two callers do not justify a shared module — the cost of
// keeping it duplicated was that the two screens could disagree about what a price looks like,
// which is exactly the kind of drift a player reads as a bug in the game rather than in the UI.
@Composable
fun CostChip(chip: CostChipUiState) {
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
