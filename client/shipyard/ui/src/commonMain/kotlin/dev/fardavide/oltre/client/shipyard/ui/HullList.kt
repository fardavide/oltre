package dev.fardavide.oltre.client.shipyard.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.fardavide.oltre.client.design.component.CostChip
import dev.fardavide.oltre.client.design.component.OltreCardState
import dev.fardavide.oltre.client.design.component.ProgressBar
import dev.fardavide.oltre.client.design.component.oltreActionShape
import dev.fardavide.oltre.client.design.component.oltreCard
import dev.fardavide.oltre.client.design.component.pressable
import dev.fardavide.oltre.client.design.core.OltreColors
import dev.fardavide.oltre.client.design.core.oltreMono
import dev.fardavide.oltre.client.design.core.resolve
import dev.fardavide.oltre.client.design.text.Strings
import dev.fardavide.oltre.core.ShipType

// A price list. The card is the app's own card, in the same three states and at the same padding as
// a facility row — a shop that invented a surface of its own would be a second design of the one
// thing the app draws everywhere.
//
// **What it is not is a row.** There is no level, so there is no dial and no badge. What is left is
// a name, a pool, what the hull is for, and a price — which is the whole of what a purchase decision
// needs and the reason this reads as a shop rather than as a fourth list of rows.
//
// **It grew a countdown at 0.9.0 and the sentence above used to rule that out too** — *"there is no
// job, so there is no countdown and no progress"*. The yard has a clock now, and what it draws is
// the treatment a Colony row already has for exactly this: `OltreCardState.RUNNING`, a `ProgressBar`
// and a countdown. Nothing here is a new drawing; what is new is that this card can be in the third
// state the design system has always had for it.
//
// **The one thing that is not the facility row's treatment**: the verb stays live underneath it. A
// facility that is building cannot be started again and its action is *replaced* by the countdown; a
// serial yard can always be given another hull, and refusing one would turn a queue back into the
// single slot it deliberately is not.
@Composable
internal fun HullList(hulls: List<HullUiState>, onBuild: (ShipType) -> Unit) {
    Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        hulls.forEach { hull -> HullCard(hull = hull, onBuild = { onBuild(hull.type) }) }
    }
}

@Composable
private fun HullCard(hull: HullUiState, onBuild: () -> Unit) {
    val mono = oltreMono()
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .oltreCard(hull.cardState())
            .testTag(ShipyardTestTags.card(hull.type))
            .padding(11.dp),
        verticalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                text = hull.name.resolve(),
                color = OltreColors.text,
                fontFamily = mono,
                fontSize = 13.5.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            // The pool, pushed to the far end and set in the faintest grey on the card. It is the
            // one line here that is a **reading** rather than an offer, and putting it beside the
            // name is what stops it being read as part of the price.
            Text(
                text = hull.pool.resolve(),
                color = OltreColors.textTertiary,
                fontFamily = mono,
                fontSize = 10.5.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.End,
                modifier = Modifier.weight(1f).padding(start = 10.dp),
            )
        }
        Text(
            text = hull.purpose.resolve(),
            color = OltreColors.textSecondary,
            fontFamily = mono,
            fontSize = 10.5.sp,
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f),
            ) {
                hull.costs.forEach { chip -> CostChip(chip = chip) }
            }
            when (val action = hull.action) {
                BuildActionUiState.Build -> Text(
                    text = Strings.build().resolve(),
                    color = Color.White,
                    fontFamily = mono,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    // `pressable` ahead of the fill, as everywhere else: a background declared
                    // first is drawn outside the scaling layer.
                    modifier = Modifier
                        .pressable(shape = oltreActionShape) { onBuild() }
                        .background(OltreColors.accent, oltreActionShape)
                        .testTag(ShipyardTestTags.action(hull.type))
                        .padding(horizontal = 11.dp, vertical = 7.dp),
                )
                // No disabled state, here or anywhere: the chip that reddened already said which
                // resource is short, and this says when it stops being short.
                is BuildActionUiState.AvailableIn -> Text(
                    text = action.label.resolve(),
                    color = OltreColors.textTertiary,
                    fontFamily = mono,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier
                        .border(1.dp, Color.White.copy(alpha = 0.16f), oltreActionShape)
                        .testTag(ShipyardTestTags.action(hull.type))
                        .padding(horizontal = 11.dp, vertical = 7.dp),
                )
            }
        }
        // The slipway, under the price rather than in place of it — see the note at the top of this
        // file for why the verb stays live above it. The drawing is the probe's in-flight footer,
        // spent unchanged: an accent countdown, the wall-clock instant beside it in the faintest
        // grey, and the bar underneath. A probe and a hull are both a card with a job and no level,
        // which is the case that shape was drawn for.
        hull.yard?.let { yard -> YardFooter(yard = yard, type = hull.type) }
    }
}

@Composable
private fun YardFooter(yard: YardUiState, type: ShipType) {
    val mono = oltreMono()
    Column(modifier = Modifier.fillMaxWidth().testTag(ShipyardTestTags.yard(type))) {
        Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
            Text(
                text = yard.countdown.resolve(),
                color = OltreColors.accent,
                fontFamily = mono,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                softWrap = false,
            )
            // The two trailing clauses are one run of faint text with the app's own separator
            // between them, so "done 14:05 · 2 queued" reads as one aside rather than as two
            // competing readings. `listOfNotNull` because the queue count is absent at one hull.
            Text(
                text = yard.footer.resolve(),
                color = OltreColors.textTertiary,
                fontFamily = mono,
                fontSize = 10.5.sp,
                maxLines = 1,
                softWrap = false,
            )
        }
        ProgressBar(percent = yard.progressPercent)
    }
}


// What the card is made of is the design system's; which of its three states a hull is in is this
// feature's.
//
// **The yard wins over the price**, and that ordering is the reading rather than a tie-break: a card
// with a hull on the slipway is the lit one whatever the player can currently afford, because
// RUNNING's own definition is *"the only lit thing on the screen from four rows away"* and a busy
// yard is the thing on this screen that is happening. The price still speaks — through the chips,
// which redden on their own, and through the ghost, which stays where it was.
private fun HullUiState.cardState(): OltreCardState = when {
    yard != null -> OltreCardState.RUNNING
    action is BuildActionUiState.Build -> OltreCardState.ACTIONABLE
    else -> OltreCardState.WAITING
}
