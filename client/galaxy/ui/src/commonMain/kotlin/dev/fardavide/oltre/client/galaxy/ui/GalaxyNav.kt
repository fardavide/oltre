package dev.fardavide.oltre.client.galaxy.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import dev.fardavide.oltre.client.design.core.OltreColors
import dev.fardavide.oltre.client.design.core.oltreMono

// What is left of the header once the reach band took navigation off it: the galaxy, which is four
// fixed choices and therefore a segmented control; where you are; and Home, because on a map of
// 15,000 slots the one place you always want back is where you came from.
//
// **The ±1 steppers are gone as of 0.2.0**, and not because they were bad — because they were the
// *only* way across, and 249 taps to cross a galaxy is not navigation, it is a counter. Keeping
// them alongside the band would be two controls for the same one-system move, one 32dp and one
// 47dp and already on screen. The lens cell beside the lit one is the stepper now, and it tells you
// what you are stepping onto before you step.
@Composable
internal fun GalaxyNav(
    uiState: GalaxyUiState,
    compact: Boolean,
    onSelectGalaxy: (Int) -> Unit,
    onGoHome: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(9.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            GalaxyTabs(
                galaxies = uiState.galaxies,
                onSelectGalaxy = onSelectGalaxy,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = uiState.scope.uppercase(),
                color = OltreColors.textTertiary,
                fontFamily = oltreMono(),
                fontSize = 9.5.sp,
                letterSpacing = 1.sp,
                maxLines = 1,
                softWrap = false,
                modifier = Modifier.padding(start = 8.dp),
            )
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            CoordinateField(uiState = uiState, compact = compact, modifier = Modifier.weight(1f))
            HomeButton(isHome = uiState.isHome, onGoHome = onGoHome)
        }
        // 0.0.16's third line — "Adaptation research lands later. You are at level 0." — is gone
        // rather than replaced. It was PLACEHOLDER copy accounting for an absence, and Research
        // now sells the three ladders it said were not built. Nothing takes the slot: see the note
        // in `GalaxyUiState` for why a standing "Thermal 2 · Gravitic 0" total was rejected. The
        // header is back to the galaxy tabs and the coordinate field, which is what it was before.
    }
}

@Composable
private fun GalaxyTabs(galaxies: List<GalaxyTabUiState>, onSelectGalaxy: (Int) -> Unit, modifier: Modifier) {
    val mono = oltreMono()
    Row(
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        modifier = modifier
            .background(Color.White.copy(alpha = 0.06f), RoundedCornerShape(6.dp))
            .padding(2.dp),
    ) {
        galaxies.forEach { galaxy ->
            Text(
                text = galaxy.label,
                color = if (galaxy.selected) OltreColors.accent else OltreColors.textTertiary,
                fontFamily = mono,
                fontSize = 9.5.sp,
                fontWeight = if (galaxy.selected) FontWeight.Bold else FontWeight.Normal,
                letterSpacing = 1.sp,
                textAlign = TextAlign.Center,
                maxLines = 1,
                modifier = Modifier
                    .weight(1f)
                    .background(
                        if (galaxy.selected) OltreColors.accent.copy(alpha = 0.22f) else Color.Transparent,
                        RoundedCornerShape(4.dp),
                    )
                    .clickable { onSelectGalaxy(galaxy.galaxy) }
                    .testTag(GalaxyTestTags.galaxy(galaxy.galaxy))
                    .padding(vertical = 4.dp),
            )
        }
    }
}

@Composable
private fun CoordinateField(uiState: GalaxyUiState, compact: Boolean, modifier: Modifier) {
    val mono = oltreMono()
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .height(32.dp)
            .border(1.dp, Color.White.copy(alpha = 0.16f), RoundedCornerShape(9.dp))
            .testTag(GalaxyTestTags.COORDINATE)
            .padding(horizontal = 11.dp),
    ) {
        Text(
            text = uiState.coordinate,
            color = OltreColors.text,
            fontFamily = mono,
            fontSize = 13.5.sp,
            maxLines = 1,
            softWrap = false,
        )
        Text(
            text = if (compact) uiState.compactDetail.uppercase() else uiState.detail.uppercase(),
            color = OltreColors.textTertiary,
            fontFamily = mono,
            fontSize = 9.5.sp,
            letterSpacing = 1.sp,
            maxLines = 1,
            // The ellipsis stays as the last resort it is — `compactDetail` is what stops it being
            // reached at the one width the app actually has to survive.
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f).padding(start = 8.dp),
        )
    }
}


// Reads as the current place rather than as an action once you are already there, which is what
// stops it being a button that does nothing on the screen you open the tab to.
@Composable
private fun HomeButton(isHome: Boolean, onGoHome: () -> Unit) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .height(32.dp)
            .border(
                1.dp,
                if (isHome) OltreColors.accent.copy(alpha = 0.45f) else Color.White.copy(alpha = 0.16f),
                RoundedCornerShape(9.dp),
            )
            .then(if (isHome) Modifier else Modifier.clickable(onClick = onGoHome))
            .testTag(GalaxyTestTags.HOME)
            .padding(horizontal = 10.dp),
    ) {
        Text(
            text = "HOME",
            color = if (isHome) OltreColors.accent else OltreColors.textSecondary,
            fontFamily = oltreMono(),
            fontSize = 9.5.sp,
            letterSpacing = 1.sp,
            maxLines = 1,
            softWrap = false,
        )
    }
}
