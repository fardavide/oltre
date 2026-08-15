package dev.fardavide.oltre.client.galaxy.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.fardavide.oltre.client.design.component.pressable
import dev.fardavide.oltre.client.design.core.OltreColors
import dev.fardavide.oltre.client.design.core.oltreMono

// The head above the fold and above the four discs. Two rows, because it does two things: say which
// of the tab's two lists you are looking at, and say which galaxy — and the galaxy chip is the only
// way up, because **the universe swaps into this same frame rather than pushing a screen.** There is
// nothing to come back from, no back button, and the tab bar never changes under you.
//
// It carries no search field and no filter chips. Both stay on the worlds list, where the question
// they answer — *which of the places I already know* — is a list question. Neither can be asked of
// 250 stars.
@Composable
internal fun GalaxyHead(
    uiState: GalaxyHeadUiState,
    onSelectMode: (LedgerMode) -> Unit,
    onToggleScale: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            ModeSwitch(mode = uiState.mode, onSelectMode = onSelectMode)
            Box(modifier = Modifier.weight(1f))
            ScaleChip(uiState = uiState, onToggleScale = onToggleScale)
        }
        // Uppercased here rather than in the mapper, exactly as `LedgerHead`'s count line does it:
        // the casing is a style the design owns and the string is a sentence the mapper wrote, and a
        // model that shouted would make a copy change a two-file edit.
        Text(
            text = uiState.count.uppercase(),
            color = OltreColors.textTertiary,
            fontFamily = oltreMono(),
            fontSize = 10.5.sp,
            letterSpacing = 1.4.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

// "G3", or "4 galaxies" when the discs are up. It is a toggle between two states of one surface, the
// way `worlds · map` is — two toggles, no stack.
// It paints at the same ~20dp as the mode pills beside it and is not wrapped to reach 44dp, for the
// reason `LedgerHead`'s header sets out at length: Compose already expands the hit area of a
// pointer-input node to the platform minimum, and a Box that reached for it here would push the whole
// row half again as tall.
@Composable
private fun ScaleChip(uiState: GalaxyHeadUiState, onToggleScale: () -> Unit) {
    val up = uiState.scale == GalaxyScale.UNIVERSE
    Text(
        text = uiState.chip,
        color = if (up) OltreColors.accent else OltreColors.textSecondary,
        fontFamily = oltreMono(),
        fontSize = 9.5.sp,
        letterSpacing = 1.sp,
        maxLines = 1,
        softWrap = false,
        modifier = Modifier
            .testTag(GalaxyTestTags.SCALE_CHIP)
            .border(
                width = 1.dp,
                color = if (up) OltreColors.accent.copy(alpha = 0.45f) else EDGE,
                shape = BADGE_SHAPE,
            )
            .background(if (up) OltreColors.accent.copy(alpha = 0.10f) else Color.Transparent, BADGE_SHAPE)
            .pressable(onClick = onToggleScale)
            .padding(horizontal = 9.dp, vertical = 4.dp),
    )
}

private val EDGE = Color.White.copy(alpha = 0.16f)
private val BADGE_SHAPE = RoundedCornerShape(4.dp)
