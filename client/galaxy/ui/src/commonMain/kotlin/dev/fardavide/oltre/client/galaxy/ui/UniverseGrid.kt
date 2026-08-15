package dev.fardavide.oltre.client.galaxy.ui

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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.fardavide.oltre.client.design.component.pressable
import dev.fardavide.oltre.client.design.core.OltreColors
import dev.fardavide.oltre.client.design.core.oltreMono

// **The universe, which is one gesture up rather than a screen.** Four cards in a two-by-two grid,
// each carrying that galaxy's own ten bands at a fifth of the size and the one fact that is true
// about it today: what it costs to get there.
//
// The four are not equidistant, and that is the whole content the view has. A neighbour is a 9h 20m
// round trip and the far one is 18h 20m, against 3h 22m to cross your own galaxy end to end — so a
// hop is nearly three times the longest journey you can make at home. Empires arrive later as a
// tinted disc and a holdings count on the line that reads "0 surveyed" now; neither needs a new
// surface, and neither is drawn here, because a frame showing an empire you cannot meet is
// decoration.
@Composable
internal fun UniverseGrid(
    uiState: UniverseUiState,
    onSelectGalaxy: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = modifier.fillMaxWidth().testTag(GalaxyTestTags.UNIVERSE),
    ) {
        uiState.discs.chunked(2).forEach { pair ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                pair.forEach { disc ->
                    Disc(disc = disc, onSelectGalaxy = onSelectGalaxy, modifier = Modifier.weight(1f))
                }
                // A galaxy count that is not a multiple of two would leave a ragged row rather than
                // a stretched card. `GalaxyBalance.GALAXIES` is four and has been since the sheet,
                // but the grid should not be the thing that breaks if it is ever five.
                repeat(2 - pair.size) { Column(modifier = Modifier.weight(1f)) {} }
            }
        }
    }
}

@Composable
private fun Disc(disc: UniverseDiscUiState, onSelectGalaxy: (Int) -> Unit, modifier: Modifier = Modifier) {
    Column(
        verticalArrangement = Arrangement.spacedBy(5.dp),
        modifier = modifier
            .border(
                width = 1.dp,
                color = if (disc.selected) OltreColors.accent.copy(alpha = 0.45f) else EDGE,
                shape = SHAPE,
            )
            .background(
                color = if (disc.selected) OltreColors.accent.copy(alpha = 0.10f) else CARD_FILL,
                shape = SHAPE,
            )
            .pressable(onClick = { onSelectGalaxy(disc.galaxy) })
            .testTag(GalaxyTestTags.disc(disc.galaxy))
            .padding(11.dp),
    ) {
        GalaxyDisc(uiState = disc.map, lane = DISC_LANE)
        // **Two rows where the design drew one, and the card is 148dp wide.** `G1`, `0 surveyed` and
        // `run 18h 20m` measure 158dp of type against 126dp of card at 393dp, so the count ellipsized
        // to `0 surve…` — and the count is the line three empires are meant to arrive on, which makes
        // it the wrong one to truncate. What the fold does for the region names, a second row does
        // here: the space was there, and one line was the constraint rather than the design.
        Row(
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.spacedBy(7.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(
                text = disc.label,
                color = OltreColors.text,
                fontFamily = oltreMono(),
                fontSize = 12.5.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                softWrap = false,
                modifier = Modifier.weight(1f),
            )
            // The galaxy you live in says "home" where the others say what a run costs. It is the
            // one card with nothing to price, and saying "run 0m" would be a number pretending to
            // be a decision.
            Text(
                text = disc.cost ?: HOME_WORD,
                color = if (disc.home) OltreColors.accent else OltreColors.textSecondary,
                fontFamily = oltreMono(),
                fontSize = 10.5.sp,
                letterSpacing = if (disc.home) 1.sp else 0.sp,
                maxLines = 1,
                softWrap = false,
            )
        }
        Text(
            text = disc.known,
            color = OltreColors.textTertiary,
            fontFamily = oltreMono(),
            fontSize = 9.5.sp,
            letterSpacing = 1.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

private const val HOME_WORD = "home"
private val DISC_LANE = 19.dp
private val EDGE = Color.White.copy(alpha = 0.16f)
private val SHAPE = RoundedCornerShape(14.dp)
private val CARD_FILL = Color(0xFF101218)
