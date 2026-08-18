package dev.fardavide.oltre.client.design.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material3.Text
import dev.fardavide.oltre.client.design.core.OltreColors
import dev.fardavide.oltre.client.design.core.oltreMono

// The three shapes a press comes in, on one surface: a card, a filled verb, and a verb whose tap area
// is larger than its face. Shared by the screenshot test, which says what a held press *looks* like,
// and the behaviour test, which says where it is allowed to put ink.
//
// One bench rather than a fixture each, so that the untouched controls beside the pressed one are
// drawn identically in every frame and a diff is the press and never the layout.
@Composable
internal fun PressBench() {
    Column(
        verticalArrangement = Arrangement.spacedBy(14.dp),
        modifier = Modifier
            .background(OltreColors.background)
            .fillMaxWidth()
            .padding(14.dp),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .testTag(PressBenchTags.CARD)
                .pressable(shape = oltreCardShape, onClick = {})
                .oltreCard(OltreCardState.ACTIONABLE)
                .padding(20.dp),
        ) {
            Text(text = "card", color = OltreColors.text, fontFamily = oltreMono(), fontSize = 11.sp)
        }
        Text(
            text = "Upgrade",
            color = Color.White,
            fontFamily = oltreMono(),
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier
                .testTag(PressBenchTags.BUTTON)
                .pressable(shape = oltreActionShape, onClick = {})
                .background(OltreColors.accent, oltreActionShape)
                .padding(horizontal = 11.dp, vertical = 7.dp),
        )
        PressableFace(
            onClick = {},
            shape = oltreActionShape,
            modifier = Modifier.heightIn(min = 44.dp).testTag(PressBenchTags.FACE),
            faceModifier = Modifier.background(OltreColors.accent, oltreActionShape),
        ) {
            Text(
                text = "Dispatch",
                color = Color.White,
                fontFamily = oltreMono(),
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(horizontal = 11.dp, vertical = 7.dp),
            )
        }
    }
}

internal object PressBenchTags {
    const val CARD = "press-bench-card"
    const val BUTTON = "press-bench-button"
    const val FACE = "press-bench-face"
}

// The phone width the rest of the repository's baselines are taken at, so a corner here is the same
// number of pixels as the corner on the colony's own frame.
internal const val BENCH_WIDTH: Int = 393
internal const val BENCH_HEIGHT: Int = 230
