package dev.fardavide.oltre.client.colony.presentation

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.fardavide.oltre.client.design.core.OltreColors
import dev.fardavide.oltre.client.design.core.oltreMono

// The returning-fleet strip per the mockup: a warn-tinted row between the hero card and the
// facility list — dot, mission title over origin and composition, arrival countdown.
@Composable
fun FleetStrip(uiState: ReturningFleetUiState, modifier: Modifier = Modifier) {
    val mono = oltreMono()
    Row(
        modifier = modifier
            .fillMaxWidth()
            .border(1.dp, OltreColors.warn.copy(alpha = 0.22f), RoundedCornerShape(14.dp))
            // Amber 6% over the background, opaque, for the same reason every other card fill went
            // opaque: the starfield draws under this strip and an alpha would let it through. The
            // strip keeps its own tint rather than taking `oltreCardSurface` — it is the one card
            // that is warm on purpose, and the handoff cites it as the precedent for the running
            // row's accent fill.
            .background(FLEET_STRIP_FILL, RoundedCornerShape(14.dp))
            .padding(horizontal = 13.dp, vertical = 11.dp),
        horizontalArrangement = Arrangement.spacedBy(11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(7.dp)
                .background(OltreColors.warn, CircleShape),
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = uiState.title,
                color = OltreColors.text,
                fontFamily = mono,
                fontSize = 12.5.sp,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = uiState.subtitle,
                color = OltreColors.textSecondary,
                fontFamily = mono,
                fontSize = 11.sp,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
        Text(
            text = uiState.countdown,
            color = OltreColors.warn,
            fontFamily = mono,
            fontSize = 13.5.sp,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

// `OltreColors.warn` at 6% composited over `OltreColors.background`, by the same arithmetic that
// produced the three card fills in `OltreCard`. Written out rather than computed so it is a value
// a screenshot diff can be reasoned about, and so nothing recomposes a colour every frame.
private val FLEET_STRIP_FILL = Color(0xFF141111)
