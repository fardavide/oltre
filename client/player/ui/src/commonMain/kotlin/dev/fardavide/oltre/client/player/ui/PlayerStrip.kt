package dev.fardavide.oltre.client.player.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.runtime.NonRestartableComposable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import dev.fardavide.oltre.client.design.component.PressableFace
import dev.fardavide.oltre.client.design.core.OltreColors
import dev.fardavide.oltre.client.design.core.OltreLayout
import dev.fardavide.oltre.client.design.core.oltreMono
import dev.fardavide.oltre.client.design.core.rememberOneShotFill
import dev.fardavide.oltre.client.design.core.resolve

// **Who is playing, above the rail.** Chrome, like the rail and the tab bar below it, and here for
// the same reason they are: what it shows belongs to the empire rather than to one screen, and it
// frames every destination.
//
// One row of 38dp over a 2dp edge, and the total is the design's most consequential number rather
// than a consequence of its contents — every dp here comes off the destination below, and 0.12.0
// shipped a galaxy map whose only control was off the bottom of the screen from exactly this
// arithmetic.
//
// **The row's height is pinned rather than measured**, and that is not belt-and-braces. The gear's
// tap target is as tall as the row, so a `wrapContentHeight` row would take its height *from* the
// target and the number would be whatever the largest touch claim happened to be. Stated here, the
// target fits the strip instead of the strip fitting the target.
//
// **It decides nothing and holds nothing.** The gear reports its tap and the frame answers it — see
// `SettingsNotice` in `:client:shell`, which is where the answer is drawn because it sits above the
// tab bar and only the frame knows where that is.
@Composable
fun PlayerStrip(
    uiState: PlayerStripUiState,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // Full-bleed like the rail, and opaque like the rail: the starfield runs under every destination,
    // and an alpha fill over the window background would put stars inside the chrome.
    Column(modifier = modifier.fillMaxWidth().background(OltreColors.surface)) {
        Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
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
                    // **Everything left over, at every width the app supports**, which is what moving
                    // the gauge to the edge bought. Nothing on this row competes with the name for
                    // slack any more, so there is no compact rule here and nothing to shorten.
                    modifier = Modifier.weight(1f).padding(start = GAP),
                )
                LevelBadge(level = uiState.level.resolve())
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
        ExperienceEdge(percent = uiState.experiencePercent)
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

// **The gauge is the strip's own bottom edge**, and the argument for it is width rather than looks:
// it costs the row nothing, so the name keeps the whole line at 320dp as well as at 393. What it
// replaces is the 1dp hairline the rail draws along its own edge — same colour, one dp thicker — so
// at LV 0 the strip is drawn exactly as it was before this existed, and a fresh install sees a plain
// edge rather than a bar stuck at nothing.
//
// **Full bleed, deliberately.** Every other value on this strip sits on the capped 560dp column; the
// edge does not, because it is the boundary of the bar and a boundary that stopped short of the
// window would be a line under the contents instead of the bottom of the chrome. On a wide window
// that makes a long accent line, which is the one thing about this drawing a device has to confirm.
//
// The objection worth writing down rather than waving: **a line pinned under a bar reads as
// loading**. What answers it is that this one never moves — drawn once on foreground and held, like
// every other value on the screen. The one-shot fill is wired exactly as every dial and meter in the
// app wires it, and at 0 its target is 0, so the animation has zero amplitude and the strip is
// motionless on every launch that ships.
// Non-restartable for `LevelBadge`'s reason. It reads `rememberOneShotFill`, but that remember
// belongs to the caller's group either way — what a restart scope of its own would buy is the
// ability to recompose without its parent, and its parent is the only thing that ever changes it.
@Composable
@NonRestartableComposable
private fun ExperienceEdge(percent: Int) {
    val fill = rememberOneShotFill()
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(GAUGE_HEIGHT)
            .testTag(PlayerTestTags.EXPERIENCE)
            .background(TRACK),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(experienceFraction(percent) * fill)
                .fillMaxHeight()
                // Square, not rounded: it is an edge rather than a track, and a rounded cap on a 2dp
                // line that starts at the window's own left edge would only round one end of it.
                .background(OltreColors.accent),
        )
    }
}
