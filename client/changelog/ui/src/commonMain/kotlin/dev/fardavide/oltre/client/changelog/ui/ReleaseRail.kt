package dev.fardavide.oltre.client.changelog.ui

import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.fardavide.oltre.client.design.core.OltreColors
import kotlin.math.roundToInt

// **Position across sixty-five stops, and it is not sixty-five dots.** Claude Design's §3: a 2dp
// track the width of the sheet, one hairline per minor line, a cap on the release being read, and
// the run behind you filled in. Nothing on it is per-release except a hairline, which is why it
// scales where dots do not — at two hundred releases the ticks thicken into a texture and the cap is
// still 3dp.
//
// **Newest is the left end.** The pager's next page is always to the right and the run is
// newest-first, so this is the order of the pages rather than a timeline — calling it one would put
// the oldest release on the wrong side of it.
//
// **It is a control, because sixty-four swipes is not a way to reach the first week.** Tap snaps to
// the nearest release and a drag scrubs through them, which is the galaxy map's own caption gesture
// from 0.11.3 — a second use rather than a new idea. The row is 44dp so that 2dp of ink is a legal
// target.
@Composable
fun ReleaseRail(
    count: Int,
    // Fractional on purpose: it follows the pager's own offset so the cap moves with the swipe
    // rather than jumping when a page settles. Nothing here animates — the fling is the platform's.
    position: Float,
    stops: List<Int>,
    onPick: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .height(ROW_HEIGHT)
            .testTag(ChangelogTestTags.RAIL),
    ) {
        val width = maxWidth
        // A run of one would divide by zero and is not hypothetical: it is what the sheet looks like
        // on the day somebody clears the catalogue down to a single release for a test.
        val lastStop = (count - 1).coerceAtLeast(1)
        fun atStop(index: Float): Dp = width * (index / lastStop)
        fun stopAt(x: Float): Int =
            (x / width.value * lastStop).roundToInt().coerceIn(0, count - 1)

        // Everything is centred on the row rather than measured from its top, so the four heights
        // stay concentric whatever the row does.
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(TRACK)
                .align(Alignment.CenterStart)
                .background(TRACK_COLOUR, TRACK_SHAPE),
        )
        // The run behind you — which is to say everything newer than the page you are on, because the
        // newest release is the left end.
        Box(
            modifier = Modifier
                .width(atStop(position))
                .height(TRACK)
                .align(Alignment.CenterStart)
                .background(RUN_COLOUR, TRACK_SHAPE),
        )
        for (stop in stops) {
            Box(
                modifier = Modifier
                    .size(width = TICK_WIDTH, height = TICK_HEIGHT)
                    .align(Alignment.CenterStart)
                    .offset(x = atStop(stop.toFloat()) - TICK_WIDTH / 2)
                    .background(TICK_COLOUR),
            )
        }
        Box(
            modifier = Modifier
                .size(width = CAP_WIDTH, height = CAP_HEIGHT)
                .align(Alignment.CenterStart)
                .offset(x = atStop(position) - CAP_WIDTH / 2)
                .background(OltreColors.text, CAP_SHAPE),
        )
        // The whole row answers rather than the track: two separate detectors, exactly as the galaxy
        // map does it, because a drag that begins with a tap must not report twice.
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(ROW_HEIGHT)
                .pointerInput(count, width) {
                    detectDragGestures { change, _ -> onPick(stopAt(change.position.x / density)) }
                }
                .pointerInput(count, width) {
                    detectTapGestures { offset -> onPick(stopAt(offset.x / density)) }
                },
        )
    }
}

// A legal target around 2dp of ink.
private val ROW_HEIGHT = 44.dp

private val TRACK = 2.dp
private val TRACK_SHAPE = RoundedCornerShape(2.dp)
private val TICK_WIDTH = 1.dp
private val TICK_HEIGHT = 10.dp
private val CAP_WIDTH = 3.dp
private val CAP_HEIGHT = 12.dp
private val CAP_SHAPE = RoundedCornerShape(1.5.dp)

private val TRACK_COLOUR = Color.White.copy(alpha = 0.09f)
private val RUN_COLOUR = Color.White.copy(alpha = 0.28f)
private val TICK_COLOUR = Color.White.copy(alpha = 0.16f)
