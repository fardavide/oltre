package dev.fardavide.oltre.client.galaxy.presentation

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

// What replaces the mockup's `◀ 2:118 ▶`. That stepper is 250 taps to cross a galaxy and 1,000 to
// cross the map, which is not navigation, it is a counter.
//
// Three things instead, in the order they are reached for: the galaxy, which is four fixed choices
// and therefore a segmented control rather than anything that scrolls; the neighbouring system,
// which is a real thing to want and stays one tap; and Home, because on a map of 15,000 slots the
// one place you always want back is where you came from. The ± steps survive because stepping to
// the next system is genuinely useful — what they stop being is the *only* way across.
@Composable
internal fun GalaxyNav(
    uiState: GalaxyUiState,
    compact: Boolean,
    onSelectGalaxy: (Int) -> Unit,
    onStepSystem: (Int) -> Unit,
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
            StepButton(
                label = "−",
                enabled = !uiState.atFirstSystem,
                testTag = GalaxyTestTags.STEP_BACK,
                onClick = { onStepSystem(-1) },
            )
            CoordinateField(uiState = uiState, compact = compact, modifier = Modifier.weight(1f))
            StepButton(
                label = "+",
                enabled = !uiState.atLastSystem,
                testTag = GalaxyTestTags.STEP_FORWARD,
                onClick = { onStepSystem(1) },
            )
            HomeButton(isHome = uiState.isHome, onGoHome = onGoHome)
        }
        // Sentence case and tertiary, which is the voice the unbuilt tabs use for the same job:
        // saying what is not built yet without dressing it as a thing to do. It sits under the
        // coordinate rather than on the rows because every blocked row would otherwise repeat it,
        // and it wraps rather than abbreviating — a caveat that lost half its words would be worse
        // than no caveat.
        // 9.5sp is the nav's own tertiary size — the one the scope and the star class already use —
        // and it is also what keeps both sentences on one line at 393dp. At 320dp it wraps, which
        // is the same thing the blocked rows do at that width.
        Text(
            text = uiState.adaptationState,
            color = OltreColors.textTertiary,
            fontFamily = oltreMono(),
            fontSize = 9.5.sp,
            lineHeight = 14.sp,
        )
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

@Composable
private fun StepButton(label: String, enabled: Boolean, testTag: String, onClick: () -> Unit) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(32.dp)
            .alpha(if (enabled) 1f else 0.32f)
            .border(1.dp, Color.White.copy(alpha = 0.16f), RoundedCornerShape(9.dp))
            .then(if (enabled) Modifier.clickable(onClick = onClick) else Modifier)
            .testTag(testTag),
    ) {
        Text(text = label, color = OltreColors.textSecondary, fontFamily = oltreMono(), fontSize = 11.sp)
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
