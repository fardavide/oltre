package dev.fardavide.oltre.client

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import dev.fardavide.oltre.client.design.core.OltreColors
import dev.fardavide.oltre.client.design.core.resolve
import dev.fardavide.oltre.client.design.core.settlingColor
import dev.fardavide.oltre.client.design.text.Strings

// The merged Ships destination's own navigation, one level down from the tab bar: which of the two
// subjects — the hull being built, the fleet that flies it — the destination is currently showing.
//
// The galaxy's `ModeSwitch`/`ModePill` (`client/galaxy/ui/…/LedgerHead.kt`) is the model, raised
// from its 22dp filter-row size to the tap minimum: here the switch *is* the destination's primary
// navigation rather than a row filtering a list already on screen, so it owes 44dp rather than 22.
// `ModeSwitch` itself is `internal` to `client.galaxy.ui` and out of reach across the feature
// boundary, so the ~15-line pattern is duplicated here rather than reached for.
//
// Glyph rather than text, unlike the galaxy's pills: `alliance-sheet.md` §7 draws each chip as the
// tab's own shipped glyph at 17dp inside a 40dp chip, so a player who already learned the two icons
// from the old bar reads the same shapes here. The words survive as each chip's content description.
@Composable
internal fun ShipsHead(mode: ShipsMode, onSelectMode: (ShipsMode) -> Unit, modifier: Modifier = Modifier) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        modifier = modifier
            .size(width = 90.dp, height = TRAY_HEIGHT)
            .background(TRAY_FILL, TRAY_SHAPE)
            .padding(2.dp),
    ) {
        ShipsChip(mode = ShipsMode.SHIPYARD, selected = mode, onClick = { onSelectMode(ShipsMode.SHIPYARD) })
        ShipsChip(mode = ShipsMode.FLEETS, selected = mode, onClick = { onSelectMode(ShipsMode.FLEETS) })
    }
}

@Composable
private fun ShipsChip(mode: ShipsMode, selected: ShipsMode, onClick: () -> Unit) {
    val on = mode == selected
    val tint = settlingColor(if (on) OltreColors.accent else OltreColors.textTertiary)
    // Resolved here, in the composable's own context, rather than inside `.semantics { }` — that
    // lambda is not a composable scope, and `resolve()` reads `LocalTranslations.current`.
    val label = when (mode) {
        ShipsMode.SHIPYARD -> Strings.tabShipyard()
        ShipsMode.FLEETS -> Strings.tabFleets()
    }.resolve()
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(CHIP_SIZE)
            .testTag(ShellTestTags.shipsMode(mode))
            .semantics { contentDescription = label }
            .background(settlingColor(if (on) CHIP_FILL else Color.Transparent), CHIP_SHAPE)
            .clip(CHIP_SHAPE)
            .clickable(onClick = onClick),
    ) {
        Canvas(Modifier.size(GLYPH_SIZE)) {
            val factor = size.width / GLYPH_VIEWPORT
            withTransform({ scale(factor, factor, pivot = Offset.Zero) }) {
                when (mode) {
                    ShipsMode.SHIPYARD -> drawShips(tint)
                    ShipsMode.FLEETS -> drawFleets(tint)
                }
            }
        }
    }
}

private val TRAY_HEIGHT = 44.dp
private val CHIP_SIZE = 40.dp
private val GLYPH_SIZE = 17.dp
private val TRAY_FILL = Color.White.copy(alpha = 0.09f)
private val CHIP_FILL = OltreColors.accent.copy(alpha = 0.22f)
private val TRAY_SHAPE = RoundedCornerShape(9.dp)
private val CHIP_SHAPE = RoundedCornerShape(7.dp)

// `drawShips`/`drawFleets` are written in the same 24-unit viewport every `TabIcon.kt` glyph uses.
private const val GLYPH_VIEWPORT = 24f
