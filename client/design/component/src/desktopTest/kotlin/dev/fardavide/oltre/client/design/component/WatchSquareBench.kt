package dev.fardavide.oltre.client.design.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.fardavide.oltre.client.design.core.OltreColors
import dev.fardavide.oltre.client.design.icon.WatchBell
import dev.fardavide.oltre.client.design.icon.WatchBellStack

// The square in all three of its states, and then the two glyphs on their own at five times the size
// they ship at.
//
// **Both rows are the point, and neither would do alone.** The top row is the control as a player
// meets it — 29dp of chrome around a 17dp mark — which is the only size at which "can these two be
// told apart" is a real question. The bottom row is the same two marks big enough for a human to
// check the drawing: where the second bell's stroke stops, how much daylight is left between the two
// contours, whether the rim stub reads as a rim. A defect in either is invisible in the other.
@Composable
internal fun WatchSquareBench() {
    Column(
        verticalArrangement = Arrangement.spacedBy(BENCH_GAP),
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.padding(BENCH_GAP),
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(BENCH_GAP),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Stacked, so the square is its own 29dp rather than a 29x44 claim with air above and
            // below it — the bench is about the mark, and the taller form's extra height is a touch
            // target that draws nothing.
            // `FACES` rather than the `entries` this used to walk: since 0.21 the state is a product
            // of two facts rather than an enum, so nothing derives the list and a seventh face has to
            // be added there by hand — see `WatchSquareUiState.FACES`, which says so.
            WatchSquareUiState.FACES.forEach { state ->
                WatchSquare(state = state, onClick = {}, stacked = true)
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(BENCH_GAP)) {
            WatchBell(color = OltreColors.accent, size = ENLARGED)
            WatchBellStack(color = OltreColors.accent, size = ENLARGED)
        }
    }
}

// Eight times 17dp. Big enough that a 1.7-unit stroke is 10dp and the gap the occlusion depends on
// is about 8dp, which is a thing eyes can measure on a screenshot — the point of the row is that a
// human can review a drawing no query can read, so it is sized to be reviewed rather than to be
// small.
private val ENLARGED = 136.dp
private val BENCH_GAP = 14.dp

internal const val WATCH_BENCH_WIDTH = 330
internal const val WATCH_BENCH_HEIGHT = 230
