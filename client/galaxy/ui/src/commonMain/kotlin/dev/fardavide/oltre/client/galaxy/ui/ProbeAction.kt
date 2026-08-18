package dev.fardavide.oltre.client.galaxy.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.fardavide.oltre.client.design.component.CostChip
import dev.fardavide.oltre.client.design.component.PressableFace
import dev.fardavide.oltre.client.design.component.ProgressBar
import dev.fardavide.oltre.client.design.component.oltreActionShape
import dev.fardavide.oltre.client.design.core.OltreColors
import dev.fardavide.oltre.client.design.core.resolve
import dev.fardavide.oltre.client.design.text.Strings
import dev.fardavide.oltre.client.design.text.TextRes
import dev.fardavide.oltre.client.design.core.oltreMono

// The footer of the system card, under the orbits and behind a hairline. One probe affordance per
// screen, never in the world list.
@Composable
internal fun ProbeAction(
    uiState: ProbeActionUiState,
    compact: Boolean,
    onDispatch: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(9.dp)) {
        Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(Color.White.copy(alpha = 0.09f)))
        when (uiState) {
            is ProbeActionUiState.Dispatch -> Offer(
                offer = uiState.offer,
                compact = compact,
                action = {
                    // **Draws 30dp and claims 44dp**, which is two boxes rather than one: the
                    // minimum height and the click go on the *outer* Box, and the accent fill goes
                    // on the text inside it. Put `defaultMinSize` ahead of `background` on a single
                    // node and the fill grows to 44dp too — a button half again as tall as every
                    // other button in the app, which is what the first version of this shipped as.
                    //
                    // Nothing else in the footer is tappable, so there is nothing for the expanded
                    // area to collide with.
                    // `PressableFace` since 0.13.1, and it is what the two-box shape above was
                    // always asking for: the click stays on the outer 44dp box and the ripple moves
                    // to the filled text, so the indication is the size of the button rather than
                    // the size of the area the button claims.
                    PressableFace(
                        onClick = onDispatch,
                        shape = oltreActionShape,
                        modifier = Modifier
                            .heightIn(min = TOUCH_MINIMUM)
                            .testTag(GalaxyTestTags.DISPATCH),
                        faceModifier = Modifier.background(OltreColors.accent, oltreActionShape),
                    ) {
                        Text(
                            text = (if (compact) uiState.compactLabel else uiState.label).resolve(),
                            color = Color.White,
                            fontFamily = oltreMono(),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            softWrap = false,
                            modifier = Modifier.padding(horizontal = 11.dp, vertical = 7.dp),
                        )
                    }
                },
            )
            is ProbeActionUiState.Unaffordable -> Offer(
                offer = uiState.offer,
                compact = compact,
                action = {
                    Text(
                        text = uiState.availableIn.resolve(),
                        color = OltreColors.textTertiary,
                        fontFamily = oltreMono(),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        softWrap = false,
                        modifier = Modifier
                            .border(1.dp, Color.White.copy(alpha = 0.16f), oltreActionShape)
                            .testTag(GalaxyTestTags.DISPATCH)
                            .padding(horizontal = 11.dp, vertical = 7.dp),
                    )
                },
            )
            is ProbeActionUiState.InFlight -> Column(
                verticalArrangement = Arrangement.spacedBy(7.dp),
                modifier = Modifier.fillMaxWidth().testTag(GalaxyTestTags.PROBE_FOOTER),
            ) {
                Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    Text(
                        text = uiState.countdown.resolve(),
                        color = OltreColors.accent,
                        fontFamily = oltreMono(),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        softWrap = false,
                    )
                    Text(
                        text = Strings.middot().resolve(),
                        color = OltreColors.textTertiary,
                        fontFamily = oltreMono(),
                        fontSize = 10.5.sp,
                    )
                    Text(
                        text = uiState.lands.resolve(),
                        color = OltreColors.textTertiary,
                        fontFamily = oltreMono(),
                        fontSize = 10.5.sp,
                        maxLines = 1,
                        softWrap = false,
                    )
                }
                ProgressBar(percent = uiState.progressPercent)
            }
            is ProbeActionUiState.Landed -> Column(
                verticalArrangement = Arrangement.spacedBy(5.dp),
                modifier = Modifier.fillMaxWidth().testTag(GalaxyTestTags.PROBE_FOOTER),
            ) {
                Text(
                    text = uiState.landedAt.resolve(),
                    color = OltreColors.text,
                    fontFamily = oltreMono(),
                    fontSize = 12.5.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    softWrap = false,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(5.dp), verticalAlignment = Alignment.Bottom) {
                    Text(
                        text = uiState.summary.resolve(),
                        color = OltreColors.textSecondary,
                        fontFamily = oltreMono(),
                        fontSize = 10.5.sp,
                    )
                    Text(
                        text = Strings.middot().resolve(),
                        color = OltreColors.textTertiary,
                        fontFamily = oltreMono(),
                        fontSize = 10.5.sp,
                    )
                    // Green once, and only on the count. The row below carries the world and its
                    // yield; the summary does not repeat them.
                    Text(
                        text = uiState.find.resolve(),
                        color = uiState.findKind.hue(),
                        fontFamily = oltreMono(),
                        fontSize = 10.5.sp,
                    )
                }
            }
            is ProbeActionUiState.Charted -> Note(text = uiState.note)
            is ProbeActionUiState.NothingToSurvey -> Note(text = uiState.note)
        }
    }
}

// Cost then time, left to right: the cost never moves and the time is the whole purchase, so the
// eye learns where the changing number is and stops reading the other one.
@Composable
private fun Offer(offer: ProbeOfferUiState, compact: Boolean, action: @Composable () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier.fillMaxWidth().testTag(GalaxyTestTags.PROBE_FOOTER),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp),
            modifier = Modifier.weight(1f),
        ) {
            CostChip(chip = offer.cost)
            // What 320dp drops, along with the word "flight" beside it and "probe" on the button.
            // Two words and no figures: 150 stays, 1h 22m stays, and the chip still reddens — the
            // colour does the work the word did.
            if (!compact) {
                Text(
                    text = offer.costWord.resolve(),
                    color = OltreColors.textTertiary,
                    fontFamily = oltreMono(),
                    fontSize = 10.5.sp,
                )
            }
            Text(
                text = Strings.middot().resolve(),
                color = OltreColors.textTertiary,
                fontFamily = oltreMono(),
                fontSize = 10.5.sp,
            )
            Text(
                text = (if (compact) offer.compactFlight else offer.flight).resolve(),
                color = OltreColors.textSecondary,
                fontFamily = oltreMono(),
                fontSize = 10.5.sp,
                maxLines = 1,
                softWrap = false,
            )
        }
        action()
    }
}

// Wrapped rather than tagged directly, so `PROBE_FOOTER` means the same thing in all six states:
// a container whose *descendants* are what the card says. Tagged straight onto the Text, the node
// would carry the string itself and every assertion written against the other five states would
// quietly miss it.
@Composable
private fun Note(text: TextRes) {
    Column(modifier = Modifier.fillMaxWidth().testTag(GalaxyTestTags.PROBE_FOOTER)) {
        Text(
            text = text.resolve(),
            color = OltreColors.textTertiary,
            fontFamily = oltreMono(),
            fontSize = 10.5.sp,
            lineHeight = 15.sp,
        )
    }
}

// Green once. `NEAR_MISS` is neither green nor red: a fact that points at Research, in body weight
// because it is worth reading and not worth acting on today.
private fun ProbeFindKind.hue(): Color = when (this) {
    ProbeFindKind.NONE -> OltreColors.textSecondary
    ProbeFindKind.SETTLEABLE -> OltreColors.ok
    ProbeFindKind.NEAR_MISS -> OltreColors.text
}

// The iOS minimum, claimed by the button rather than drawn by it.
private val TOUCH_MINIMUM = 44.dp
