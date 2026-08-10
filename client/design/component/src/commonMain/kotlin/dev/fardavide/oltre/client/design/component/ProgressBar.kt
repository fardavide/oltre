package dev.fardavide.oltre.client.design.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import dev.fardavide.oltre.client.design.core.OltreColors
import dev.fardavide.oltre.client.design.core.rememberOneShotFill

// How long a thing has left, on the card that is doing it. The two row-shaped callers — a facility
// upgrading and a technology being researched — drew this from byte-identical private copies until
// 0.0.14, shared it until the Sky pass, and now draw a `LevelDial` instead. **What is left is the
// probe**, whose card is not a row: it has a flight rather than a level, so there is nothing to put
// in the middle of a ring and a length is still the honest shape for it.
//
// A percentage rather than a fraction, because the caller computes it from two instants and a
// rounded integer is what the row also prints; deriving the bar and the label from one number is
// what keeps them from ever disagreeing by a point.
@Composable
fun ProgressBar(percent: Int) {
    // Arrives once on the way in and then holds, in step with every dial and meter on the other
    // screens — see `rememberOneShotFill`.
    val fill = rememberOneShotFill()
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 10.dp)
            .height(3.dp)
            .background(Color.White.copy(alpha = 0.09f), RoundedCornerShape(2.dp)),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(percent / 100f * fill)
                .fillMaxHeight()
                .background(OltreColors.accent, RoundedCornerShape(2.dp)),
        )
    }
}
