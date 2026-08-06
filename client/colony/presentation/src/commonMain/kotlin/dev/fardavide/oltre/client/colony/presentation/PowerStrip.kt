package dev.fardavide.oltre.client.colony.presentation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.fardavide.oltre.client.design.OltreColors
import dev.fardavide.oltre.client.design.OltreLayout
import dev.fardavide.oltre.client.design.oltreMono

// A power shortage silently scales every mine's output, which made the early game read as "metal
// is too slow" when it was really "you are running at 55%". It gets its own line under the rail
// rather than a fourth rail cell: energy is not a stock, and a fourth cell would both claim it is
// one and squeeze the rail below the 320dp Slide Over width.
//
// The line is always present, so the healthy case teaches what the number means before the
// shortage arrives — and so its appearance is never itself the layout change.
//
// PROVISIONAL VISUAL. The state this reads (EnergyUiState) is the settled part; the treatment is
// a placeholder until the Claude Design system lands, so that the shortage is at least visible
// in the meantime rather than costing the player 45% in silence.
@Composable
fun PowerStrip(uiState: EnergyUiState, modifier: Modifier = Modifier) {
    val mono = oltreMono()
    val tint = if (uiState.deficit) OltreColors.warn else OltreColors.textSecondary
    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(if (uiState.deficit) OltreColors.warn.copy(alpha = 0.10f) else OltreColors.surface),
        contentAlignment = Alignment.TopCenter,
    ) {
        Row(
            modifier = Modifier
                .widthIn(max = OltreLayout.maxContentWidth)
                .fillMaxWidth()
                // Ahead of the padding, matching the rail and the content column: a tag placed
                // after it would mark the padded interior rather than the column itself.
                .testTag(ColonyTestTags.POWER_STRIP_CONTENT)
                .padding(horizontal = 11.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = "POWER",
                color = OltreColors.textTertiary,
                fontFamily = mono,
                fontSize = 9.5.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp,
            )
            Text(
                text = uiState.reading,
                color = tint,
                fontFamily = mono,
                fontSize = 10.5.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                softWrap = false,
            )
            // The consequence is what the player acts on, so it yields last when the window is
            // narrow rather than the reading it explains.
            Text(
                text = uiState.consequence,
                color = tint,
                fontFamily = mono,
                fontSize = 10.5.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false),
            )
        }
    }
}
