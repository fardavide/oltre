package dev.fardavide.oltre.client

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import dev.fardavide.oltre.client.design.component.SegmentedSwitch
import dev.fardavide.oltre.client.design.core.OltreColors
import dev.fardavide.oltre.client.design.core.oltreMono
import dev.fardavide.oltre.client.design.core.resolve
import dev.fardavide.oltre.client.design.core.settlingColor
import dev.fardavide.oltre.client.design.text.Strings
import dev.fardavide.oltre.client.design.text.TextRes

// The merged Ships destination's own navigation, one level down from the tab bar: which of the two
// subjects — the hull being built, the fleet that flies it — the destination is currently showing.
//
// **The galaxy's `worlds · map` switch is the model, and since 0.24.0 it is the same component
// rather than a duplicate of it** (Davide, 2026-09-13): `:client:design:component`'s
// `SegmentedSwitch` carries the trough, the selection fill and the click for both, and this is text
// on both, not a glyph on one — the uniform design he asked for rather than a size or a content type
// this feature invented for itself.
@Composable
internal fun ShipsHead(mode: ShipsMode, onSelectMode: (ShipsMode) -> Unit, modifier: Modifier = Modifier) {
    SegmentedSwitch(
        options = listOf(ShipsMode.SHIPYARD, ShipsMode.FLEETS),
        selected = mode,
        onSelect = onSelectMode,
        testTag = { ShellTestTags.shipsMode(it) },
        modifier = modifier,
    ) { option, on ->
        ShipsModeLabel(text = labelFor(option), on = on)
    }
}

private fun labelFor(mode: ShipsMode): TextRes = when (mode) {
    ShipsMode.SHIPYARD -> Strings.tabShipyard()
    ShipsMode.FLEETS -> Strings.tabFleets()
}

// The galaxy's own `ModeLabel`, letter for letter: uppercase is a style rather than a spelling, and
// both channels — the ink and the fill the switch draws behind it — turn together.
@Composable
private fun ShipsModeLabel(text: TextRes, on: Boolean) {
    Text(
        text = text.resolve().uppercase(),
        color = settlingColor(if (on) OltreColors.accent else OltreColors.textTertiary),
        fontFamily = oltreMono(),
        fontSize = 9.5.sp,
        fontWeight = if (on) FontWeight.Bold else FontWeight.Normal,
        letterSpacing = 1.sp,
        maxLines = 1,
        softWrap = false,
    )
}
