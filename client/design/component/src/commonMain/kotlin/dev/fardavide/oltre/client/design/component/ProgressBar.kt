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

// How long a thing has left, on the row that is doing it. Both screens that run a job draw this —
// a facility upgrading and a technology being researched — and they drew it from byte-identical
// private copies until 0.0.14.
//
// A percentage rather than a fraction, because the caller computes it from two instants and a
// rounded integer is what the row also prints; deriving the bar and the label from one number is
// what keeps them from ever disagreeing by a point.
@Composable
fun ProgressBar(percent: Int) {
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
