package dev.fardavide.oltre.client.shipyard.presentation

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
import dev.fardavide.oltre.client.design.component.oltreCard
import dev.fardavide.oltre.client.design.component.pressable
import dev.fardavide.oltre.client.design.core.OltreColors
import dev.fardavide.oltre.client.design.core.oltreMono
import dev.fardavide.oltre.core.ShipType

// A price list. The card is the app's own card, in the same three states and at the same padding as
// a facility row — a shop that invented a surface of its own would be a second design of the one
// thing the app draws everywhere.
//
// **What it is not is a row.** There is no level, so there is no dial and no badge; there is no job,
// so there is no countdown and no progress. What is left is a name, a pool, what the hull is for,
// and a price — which is the whole of what a purchase decision needs and the reason this reads as a
// shop rather than as a fourth list of rows.
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
            .oltreCard(hull.action.cardState())
            .testTag(ShipyardTestTags.card(hull.type))
            .padding(11.dp),
        verticalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                text = hull.name,
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
                text = hull.pool,
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
            text = hull.purpose,
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
                    text = "Build",
                    color = Color.White,
                    fontFamily = mono,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    // `pressable` ahead of the fill, as everywhere else: a background declared
                    // first is drawn outside the scaling layer.
                    modifier = Modifier
                        .pressable { onBuild() }
                        .background(OltreColors.accent, RoundedCornerShape(9.dp))
                        .testTag(ShipyardTestTags.action(hull.type))
                        .padding(horizontal = 11.dp, vertical = 7.dp),
                )
                // No disabled state, here or anywhere: the chip that reddened already said which
                // resource is short, and this says when it stops being short.
                is BuildActionUiState.AvailableIn -> Text(
                    text = action.label,
                    color = OltreColors.textTertiary,
                    fontFamily = mono,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier
                        .border(1.dp, Color.White.copy(alpha = 0.16f), RoundedCornerShape(9.dp))
                        .testTag(ShipyardTestTags.action(hull.type))
                        .padding(horizontal = 11.dp, vertical = 7.dp),
                )
            }
        }
    }
}

// The hull a slice has not reached, drawn rather than hidden — a shop that only lists what is on
// sale cannot teach the axis it is about to sell along. Two lines and no price, at the same 42% dim
// a locked Research row takes, because the two mean the same thing: this exists and you cannot have
// it yet.
@Composable
internal fun ComingHullList(hulls: List<ComingHullUiState>) {
    val mono = oltreMono()
    Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        hulls.forEach { hull ->
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .oltreCard(OltreCardState.WAITING)
                    .testTag(ShipyardTestTags.card(hull.type))
                    // After the card and not before it: an alpha ahead of the fill dims the card
                    // itself, which turns the one opaque thing on the screen translucent again and
                    // lets the starfield through it. See the same ordering on a locked Research row.
                    .alpha(0.42f)
                    .padding(11.dp),
                verticalArrangement = Arrangement.spacedBy(7.dp),
            ) {
                Text(
                    text = hull.name,
                    color = OltreColors.text,
                    fontFamily = mono,
                    fontSize = 13.5.sp,
                    fontWeight = FontWeight.Medium,
                )
                Text(
                    text = hull.purpose,
                    color = OltreColors.textSecondary,
                    fontFamily = mono,
                    fontSize = 10.5.sp,
                )
            }
        }
    }
}

// What the card is made of is the design system's; which of its three states a hull is in is this
// feature's. There is no RUNNING branch and there never will be: `buildShips` charges and delivers
// in the same call, so a hull is never in flight.
private fun BuildActionUiState.cardState(): OltreCardState = when (this) {
    BuildActionUiState.Build -> OltreCardState.ACTIONABLE
    is BuildActionUiState.AvailableIn -> OltreCardState.WAITING
}
