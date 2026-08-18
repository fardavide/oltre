package dev.fardavide.oltre.client.galaxy.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import dev.fardavide.oltre.client.design.core.settlingColor

// **The whole bar is the 44dp target**, which is what lets the stars be 3dp across and still cost
// nothing to miss: you scrub with a thumb anywhere on the drawing and act down here, where there is
// room for a finger. Tapping it opens the system the map has selected — the one real push in the
// tab, because a system is a different kind of object and it is where you act.
//
// The trailing element is the only other thing that can be tapped, and only when there is a probe to
// send. A probe is aimed at a **star**, so the map may aim one; a run is aimed at a **world**, and
// worlds are what a survey pays for, so the map quotes the run's clock and sends you to the orbit
// page to choose. One rule, straight out of the knowledge tiers.
@Composable
internal fun MapCaption(
    uiState: MapCaptionUiState,
    compact: Boolean,
    onOpen: () -> Unit,
    onDispatchProbe: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        modifier = modifier
            .fillMaxWidth()
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
                    text = uiState.system,
                    color = OltreColors.text,
                    fontFamily = oltreMono(),
                    fontSize = 13.5.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                if (uiState.coordinate.isNotEmpty()) {
                    Text(
                        text = uiState.coordinate,
                        color = OltreColors.textTertiary,
                        fontFamily = oltreMono(),
                        fontSize = 10.5.sp,
                        maxLines = 1,
                        softWrap = false,
                    )
                }
            }
            Text(
                text = if (compact) uiState.compactMeta else uiState.meta,
                color = OltreColors.textSecondary,
                fontFamily = oltreMono(),
                fontSize = 10.5.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        when (val trailing = uiState.trailing) {
            // `PressableFace`, because this claims 44dp and draws about 30: the ripple belongs on
            // the ghost rather than on the whole height the button asks the row for.
            is MapCaptionTrailingUiState.Dispatch -> PressableFace(
                onClick = onDispatchProbe,
                shape = oltreActionShape,
                modifier = Modifier
                    .heightIn(min = TOUCH_MINIMUM)
                    .testTag(GalaxyTestTags.CAPTION_ACTION),
                faceModifier = Modifier
                    .border(1.dp, OltreColors.accent.copy(alpha = 0.45f), oltreActionShape),
            ) {
                // A ghost rather than a filled button, and the difference is load-bearing: the
                // filled accent verb belongs to the orbit page's footer, where the whole screen is
                // about one system. Here it sits beside a name that is already accented by being
                // selected, and two solid accents in one bar is the screen shouting at itself.
                Text(
                    text = trailing.label,
                    color = OltreColors.accent,
                    fontFamily = oltreMono(),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    softWrap = false,
                    modifier = Modifier.padding(horizontal = 11.dp, vertical = 7.dp),
                )
            }

            is MapCaptionTrailingUiState.Note -> Text(
                text = trailing.label,
                color = OltreColors.textTertiary,
                fontFamily = oltreMono(),
                fontSize = 10.5.sp,
                maxLines = 1,
                softWrap = false,
            )

        }
    }
}

private val TOUCH_MINIMUM = 44.dp
private val SHAPE = RoundedCornerShape(14.dp)

// The opaque card fill, not white at 4.5%: the starfield sits behind the content column on the
// worlds list, and an alpha fill would let stars through the card so they read as dust on it.
private val CARD_FILL = Color(0xFF101218)
