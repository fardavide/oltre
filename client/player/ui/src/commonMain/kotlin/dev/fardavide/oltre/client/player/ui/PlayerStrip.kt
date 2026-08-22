package dev.fardavide.oltre.client.player.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.NonRestartableComposable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import dev.fardavide.oltre.client.design.component.PressableFace
import dev.fardavide.oltre.client.design.core.OltreColors
import dev.fardavide.oltre.client.design.core.OltreLayout
import dev.fardavide.oltre.client.design.core.oltreMono
import dev.fardavide.oltre.client.design.core.rememberOneShotFill
import dev.fardavide.oltre.client.design.core.resolve
import dev.fardavide.oltre.client.design.text.Strings
import kotlinx.coroutines.delay

// **Who is playing, above the rail.** Chrome, like the rail and the tab bar below it, and here for
// the same reason they are: what it shows belongs to the empire rather than to one screen, and it
// frames every destination.
//
// One row, 38dp, and the height is the design's most consequential number rather than a consequence
// of its contents — every dp here comes off the destination below, and 0.12.0 shipped a galaxy map
// whose only control was off the bottom of the screen from exactly this arithmetic.
//
// **The height is pinned rather than measured**, and that is not belt-and-braces. The gear's tap
// target is as tall as the strip, so a `wrapContentHeight` row would take its height *from* the
// target and the number would be whatever the largest touch claim happened to be. Stated here, the
// target fits the strip instead of the strip fitting the target.
@Composable
fun PlayerStrip(uiState: PlayerStripUiState, modifier: Modifier = Modifier) {
    // **Transience as state with an explicit clearing rule**, which is the shape this app already
    // uses for the one other thing that appears and goes away — see the resource rail's arrival
    // roll, cleared by a `LaunchedEffect` in the shell. There is no snackbar, no toast and no host
    // anywhere in this app, and this deliberately does not become the first one: a notice that only
    // ever says one sentence, in one place, next to the control that raised it, is a piece of this
    // strip rather than a piece of infrastructure.
    var noticeShown by remember { mutableStateOf(false) }
    // Keyed on the flag, so a second tap restarts the window rather than adding a second notice.
    LaunchedEffect(noticeShown) {
        if (noticeShown) {
            delay(NOTICE_MILLIS)
            noticeShown = false
        }
    }
    PlayerStripContent(
        uiState = uiState,
        noticeShown = noticeShown,
        onOpenSettings = { noticeShown = true },
        modifier = modifier,
    )
}

// The drawing, with the notice as a parameter. **Split from the state above so a frame can be
// photographed in either state without tapping anything** — a `performClick` before a capture bakes
// the press indication into the baseline and pins it there forever.
@Composable
internal fun PlayerStripContent(
    uiState: PlayerStripUiState,
    noticeShown: Boolean,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // Full-bleed like the rail, and opaque like the rail: the starfield runs under every destination,
    // and an alpha fill over the window background would put stars inside the chrome.
    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .background(OltreColors.surface)
            // The same hairline the rail draws along its own bottom edge, so the two tiers are one
            // decision applied twice rather than two that happen to match.
            .drawBehind {
                val line = HAIRLINE_WIDTH.toPx()
                drawRect(
                    color = HAIRLINE,
                    topLeft = Offset(x = 0f, y = size.height - line),
                    size = Size(width = size.width, height = line),
                )
            },
        contentAlignment = Alignment.TopCenter,
    ) {
        // Measured on the window rather than on the capped column, exactly as the rail measures it:
        // what decides whether the gauge has room is the pane the strip is in.
        val compact = maxWidth < OltreLayout.compactWidth
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .height(STRIP_HEIGHT)
                .widthIn(max = OltreLayout.maxContentWidth)
                .fillMaxWidth()
                // Ahead of the padding, like every other tagged column in the app: a tag after it
                // marks the padded interior, and what the contents have to fit inside is the strip.
                .testTag(PlayerTestTags.CONTENT)
                // Asymmetric, and the end is not a mistake: the gear's 38dp target is 10dp wider
                // than its face on each side, so 2dp here puts the *glyph* 12dp from the edge while
                // the tappable area runs out to it.
                .padding(start = EDGE, end = GEAR_EDGE),
        ) {
            PlayerMark(color = OltreColors.accent)
            Text(
                text = uiState.name.resolve(),
                color = OltreColors.text,
                fontFamily = oltreMono(),
                fontSize = NAME_SIZE,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                // Deliberately not the rail's 15sp SemiBold: at the same size and weight as the
                // three figures below it the name reads as a fourth statistic rather than as whose
                // statistics they are.
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f).padding(start = GAP),
            )
            // **The notice displaces the readings rather than overlaying them**, and what it
            // displaces is the two things that are not real yet. It sits immediately left of the
            // gear, so the answer appears where the question was asked.
            if (noticeShown) {
                Text(
                    text = Strings.settingsComingSoon().resolve(),
                    color = OltreColors.textSecondary,
                    fontFamily = oltreMono(),
                    fontSize = NOTICE_SIZE,
                    maxLines = 1,
                    softWrap = false,
                    modifier = Modifier.testTag(PlayerTestTags.NOTICE).padding(start = GAP),
                )
            } else {
                LevelBadge(level = uiState.level.resolve())
                ExperienceGauge(percent = uiState.experiencePercent, compact = compact)
            }
            PressableFace(
                onClick = onOpenSettings,
                shape = RoundedCornerShape(GEAR_RADIUS),
                // **38dp square, not 44.** `WatchSquare` settled this: a child placed outside its
                // parent's bounds does not reliably receive touch, so a 44dp claim inside a 38dp
                // band either misses the tap or grows the band to 44dp — and the band's height is
                // the one number this design spends most carefully. Its own remedy applies: claim
                // the axis you can afford. 38dp is larger than the 29dp square that already ships
                // stacked on a colony row.
                modifier = Modifier.size(GEAR_TARGET).testTag(PlayerTestTags.SETTINGS),
            ) {
                SettingsGlyph(color = OltreColors.textSecondary)
            }
        }
    }
}

// The level, in the badge the facility rows already wear — same 10sp, same white 9% at 4dp, so the
// player's level and a mine's level are read the same way.
// `@NonRestartableComposable` because it is a leaf that draws its arguments and holds nothing: a
// restart scope of its own could do nothing its caller's cannot, and Compose generates one — with a
// skippability branch per parameter — unless told not to. See the `test-coverage` skill.
@Composable
@NonRestartableComposable
private fun LevelBadge(level: String) {
    Text(
        text = level,
        color = OltreColors.textSecondary,
        fontFamily = oltreMono(),
        fontSize = BADGE_SIZE,
        fontWeight = FontWeight.Bold,
        maxLines = 1,
        softWrap = false,
        modifier = Modifier
            .padding(start = GAP)
            .background(BADGE_FILL, RoundedCornerShape(BADGE_RADIUS))
            .padding(horizontal = BADGE_PAD_X, vertical = BADGE_PAD_Y),
    )
}

// **The measurements of the run bar, not the component.** `ProgressBar` bakes in `fillMaxWidth()`
// and a 10dp top padding and takes no width, so calling it here would be forking it rather than
// reusing it — which is the same call `RunCard` made for the same reason.
//
// Borrowed knowingly, though: a track that visibly *ends* is a scale, where one running to the edge
// of a card is a timer. This is the same instrument reading a standing quantity instead of a job.
//
// **At zero it is an empty track and nothing else is drawn.** The one-shot fill is wired exactly as
// every dial and meter in the app wires it, and at 0 its target is 0 — so the animation has zero
// amplitude and the strip is motionless on every launch that ships. The first frame that ever moves
// here is the first frame after something awards experience.
// Non-restartable for `LevelBadge`'s reason. It reads `rememberOneShotFill`, but that remember
// belongs to the caller's group either way — what a restart scope of its own would buy is the
// ability to recompose without its parent, and its parent is the only thing that ever changes it.
@Composable
@NonRestartableComposable
private fun ExperienceGauge(percent: Int, compact: Boolean) {
    val fill = rememberOneShotFill()
    Box(
        modifier = Modifier
            .padding(start = GAP)
            .testTag(PlayerTestTags.EXPERIENCE)
            .size(width = if (compact) GAUGE_WIDTH_COMPACT else GAUGE_WIDTH, height = GAUGE_HEIGHT)
            .background(TRACK, RoundedCornerShape(GAUGE_RADIUS)),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(experienceFraction(percent) * fill)
                .fillMaxHeight()
                .background(OltreColors.accent, RoundedCornerShape(GAUGE_RADIUS)),
        )
    }
}
