package dev.fardavide.oltre.client.colony.presentation

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.fardavide.oltre.client.design.OltreColors
import dev.fardavide.oltre.client.design.oltreMono

// The hero card: the single "in progress" build with its live countdown — per the design
// brief, the reason the app gets opened.
@Composable
fun InProgressCard(uiState: InProgressUiState, modifier: Modifier = Modifier) {
    val mono = oltreMono()
    Column(
        modifier = modifier
            .fillMaxWidth()
            .border(1.dp, Color.White.copy(alpha = 0.16f), RoundedCornerShape(18.dp))
            .background(
                Brush.linearGradient(
                    colors = listOf(
                        OltreColors.accent.copy(alpha = 0.13f),
                        OltreColors.accent.copy(alpha = 0.03f),
                        Color.White.copy(alpha = 0.02f),
                    ),
                ),
                RoundedCornerShape(18.dp),
            )
            .padding(15.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = uiState.title,
                    color = OltreColors.text,
                    fontFamily = mono,
                    fontSize = 14.5.sp,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            Text(
                text = uiState.countdown,
                color = OltreColors.text,
                fontFamily = mono,
                fontSize = 19.sp,
                fontWeight = FontWeight.SemiBold,
            )
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 13.dp)
                .height(4.dp)
                .background(Color.White.copy(alpha = 0.09f), RoundedCornerShape(3.dp)),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(uiState.progressPercent / 100f)
                    .fillMaxHeight()
                    .background(OltreColors.accent, RoundedCornerShape(3.dp)),
            )
        }
        Row(modifier = Modifier.fillMaxWidth().padding(top = 9.dp)) {
            Text(
                text = "${uiState.progressPercent}%",
                color = OltreColors.textTertiary,
                fontFamily = mono,
                fontSize = 11.sp,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = uiState.doneAt,
                color = OltreColors.textTertiary,
                fontFamily = mono,
                fontSize = 11.sp,
            )
        }
    }
}
