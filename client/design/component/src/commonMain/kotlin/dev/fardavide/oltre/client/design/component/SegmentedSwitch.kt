package dev.fardavide.oltre.client.design.component

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import dev.fardavide.oltre.client.design.core.OltreColors
import dev.fardavide.oltre.client.design.core.settlingColor

// **The two-or-more-way text switch every "which of these am I looking at" control in this app is
// built from**, extracted rather than duplicated: the galaxy's `worlds · map` pills and the alliance
// epic's Ships pills are the same mechanics with different words, and until 0.24.0 the second one
// copied the first's ~15 lines by hand rather than reaching across a module boundary it could not
// cross. This is that boundary crossed the right way instead — the trough, the selection fill and
// the click live here; what a segment *says* stays with the caller.
//
// **Text only, deliberately** (Davide, 2026-09-13): every segment in the app is a word, so the
// component asks for one and draws one. A glyph variant is not on the shelf here — the day a segment
// needs one, that is a new parameter to design rather than a branch to guess at now.
//
// **Selection is one flat cross-fade, never a slide or a scale.** `settlingColor` is what every call
// site already used for this, so the switch itself asks nothing of the ink or the fill beyond it —
// the caller's `content` lambda receives `selected` and settles its own colour the same way.
@Composable
fun <T> SegmentedSwitch(
    options: List<T>,
    selected: T,
    onSelect: (T) -> Unit,
    testTag: (T) -> String,
    modifier: Modifier = Modifier,
    content: @Composable (option: T, selected: Boolean) -> Unit,
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        modifier = modifier
            .background(TROUGH_FILL, TROUGH_SHAPE)
            .padding(2.dp),
    ) {
        options.forEach { option ->
            val on = option == selected
            Segment(
                onClick = { onSelect(option) },
                selected = on,
                modifier = Modifier.testTag(testTag(option)),
            ) {
                content(option, on)
            }
        }
    }
}

// One segment: the fill and the click, sized by its own content plus the shared padding — every
// segment in the app agrees on this, since a text pill has never needed to match another's width.
// The shape and padding are not parameters: no caller has ever needed a different one, and a
// customization surface nothing calls is a branch Compose still has to generate and nothing can
// ever exercise.
@Composable
private fun Segment(
    onClick: () -> Unit,
    selected: Boolean,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .background(settlingColor(if (selected) SEGMENT_FILL else Color.Transparent), SEGMENT_SHAPE)
            // Between the fill and the click, which is the only place a clip works: an indication
            // is clipped by the layer declared before it, so a segment's ripple is a segment.
            .clip(SEGMENT_SHAPE)
            .clickable(onClick = onClick)
            .padding(horizontal = 9.dp, vertical = 4.dp),
    ) {
        content()
    }
}

// White 9% and the accent at 22%, unchanged from the two call sites this replaces — neither number
// moves with this refactor, so no baseline this component now feeds has to be re-recorded for it.
private val TROUGH_FILL = Color.White.copy(alpha = 0.09f)
private val SEGMENT_FILL = OltreColors.accent.copy(alpha = 0.22f)

// The galaxy's own radii, kept since it was here first — every caller agrees on them.
private val TROUGH_SHAPE = RoundedCornerShape(4.dp)
private val SEGMENT_SHAPE = RoundedCornerShape(3.dp)
