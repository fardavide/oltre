package dev.fardavide.oltre.client.galaxy.ui

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.fardavide.oltre.client.design.component.PressableFace
import dev.fardavide.oltre.client.design.component.oltreActionShape
import dev.fardavide.oltre.client.design.component.pressable
import dev.fardavide.oltre.client.design.core.OltreColors
import dev.fardavide.oltre.client.design.core.oltreMono
import dev.fardavide.oltre.client.design.core.resolve
import dev.fardavide.oltre.client.design.core.settlingColor

// **The whole bar is the 44dp target**, which is what lets a star be two pixels across and still
// cost nothing to miss: you scrub with a thumb anywhere on the sky and act down here, where there
// is room for a finger. Tapping it dives one depth into what is selected — the same thing a second
// tap on the selection does, for the thumb that would rather not aim.
//
// The trailing pill is the only other thing that can be tapped, and it carries the one verb the
// selection affords beyond the dive. A probe is aimed at a **star**, so a star may send one; a run
// is aimed at a **world**, and worlds are what a survey pays for, so a surveyed world offers the
// run and the sheet prices it. One rule, straight out of the knowledge tiers.
@Composable
internal fun MapCaption(
    uiState: MapCaptionUiState,
    compact: Boolean,
    onOpen: () -> Unit,
    onDispatchProbe: () -> Unit,
    onRun: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        modifier = modifier
            .fillMaxWidth()
            .animateContentSize()
            .heightIn(min = TOUCH_MINIMUM)
            // Ahead of the border and the fill, as everywhere else: declared after them the press
            // scaled the caption's text and left the card it is written on standing still.
            .pressable(shape = SHAPE, onClick = onOpen)
            .border(
                width = 1.dp,
                color = settlingColor(
                    if (uiState.own) OltreColors.accent.copy(alpha = 0.45f) else Color.White.copy(alpha = 0.09f),
                ),
                shape = SHAPE,
            )
            .background(
                color = settlingColor(if (uiState.own) OltreColors.accent.copy(alpha = 0.10f) else CARD_FILL),
                shape = SHAPE,
            )
            .testTag(GalaxyTestTags.CAPTION)
            .padding(11.dp),
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(5.dp),
            modifier = Modifier.weight(1f),
        ) {
            Row(
                verticalAlignment = Alignment.Bottom,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    text = uiState.system.resolve(),
                    color = OltreColors.text,
                    fontFamily = oltreMono(),
                    fontSize = 13.5.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                // The universe has no address inside itself, so the resolved line is empty and there
                // is nothing to draw — checked on the resolved string, because whether a message is
                // empty is the language's answer rather than the model's.
                val coordinate = uiState.coordinate.resolve()
                if (coordinate.isNotEmpty()) {
                    Text(
                        text = coordinate,
                        color = OltreColors.textTertiary,
                        fontFamily = oltreMono(),
                        fontSize = 10.5.sp,
                        maxLines = 1,
                        softWrap = false,
                    )
                }
            }
            Text(
                text = (if (compact) uiState.compactMeta else uiState.meta).resolve(),
                color = OltreColors.textSecondary,
                fontFamily = oltreMono(),
                fontSize = 10.5.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            uiState.detail?.let { detail ->
                Text(
                    text = detail.resolve(),
                    color = OltreColors.textTertiary,
                    fontFamily = oltreMono(),
                    fontSize = 10.5.sp,
                )
            }
        }
        uiState.trailing?.let { trailing ->
            // A ghost rather than a filled button, and the difference is load-bearing: it sits
            // beside a name that is already accented by being selected, and two solid accents in
            // one bar is the screen shouting at itself. Amber for the run, because amber is your
            // fleet everywhere the sky draws one.
            val (onClick, colour) = when (trailing) {
                is MapCaptionTrailingUiState.Dispatch -> onDispatchProbe to OltreColors.accent
                is MapCaptionTrailingUiState.Run -> onRun to OltreColors.warn
                is MapCaptionTrailingUiState.Open -> onOpen to OltreColors.accent
            }
            // `PressableFace`, because this claims 44dp and draws about 30: the ripple belongs on
            // the ghost rather than on the whole height the button asks the row for.
            PressableFace(
                onClick = onClick,
                shape = oltreActionShape,
                modifier = Modifier
                    .heightIn(min = TOUCH_MINIMUM)
                    .testTag(GalaxyTestTags.CAPTION_ACTION),
                faceModifier = Modifier.border(1.dp, colour.copy(alpha = 0.45f), oltreActionShape),
            ) {
                Text(
                    text = trailing.label.resolve(),
                    color = colour,
                    fontFamily = oltreMono(),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    softWrap = false,
                    modifier = Modifier.padding(horizontal = 11.dp, vertical = 7.dp),
                )
            }
        }
    }
}

private val TOUCH_MINIMUM = 44.dp
private val SHAPE = RoundedCornerShape(14.dp)

// The opaque card fill, not white at 4.5%: it sits on the sky, and an alpha fill would let the
// stars through the card so they read as dust on it.
private val CARD_FILL = Color(0xFF101218)
