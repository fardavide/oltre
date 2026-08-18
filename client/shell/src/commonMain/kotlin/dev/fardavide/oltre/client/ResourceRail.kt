package dev.fardavide.oltre.client

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.fardavide.oltre.client.design.core.OltreColors
import dev.fardavide.oltre.client.design.core.OltreLayout
import dev.fardavide.oltre.client.design.core.oltreMono
import dev.fardavide.oltre.client.design.core.resolve
import dev.fardavide.oltre.client.design.text.Strings
import dev.fardavide.oltre.core.ResourceKind
import dev.fardavide.oltre.client.design.core.rememberOneShotFill
import dev.fardavide.oltre.client.design.format.groupedByThousands
import dev.fardavide.oltre.client.design.icon.PowerMark

// Chrome, like the tab bar below it, and here for the same reason: what it shows is the empire's,
// not one screen's, and it frames every destination. It moved out of :client:colony:presentation
// when Research landed as a second screen that shows it — a feature module cannot own a component
// another feature needs.
//
// It stays the shell's after 0.0.14 split the design system into layer modules. What went into
// :client:design is what has no owner at all; the rail has one, and it is this module. Only the
// bolt it draws was shared out, to :client:design:icon, because the *glyph* is owned by neither
// this rail nor the colony's facility cards.
@Composable
internal fun ResourceRail(uiState: ResourceRailUiState, modifier: Modifier = Modifier) {
    // The bar itself is full-bleed — it reads as the top edge of the window — but its cells
    // stay on the same centred column as the content below, whatever the window's width.
    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .background(OltreColors.surface)
            // The rail's own bottom edge. The bar is a surface the destinations pass under, and a
            // surface with no edge on the side things move past it reads as a gap in the window
            // rather than as a thing in front of one — which is exactly what it became once the
            // starfield started sliding underneath.
            .drawBehind {
                val line = HAIRLINE_WIDTH.toPx()
                drawRect(
                    color = HAIRLINE,
                    topLeft = Offset(x = 0f, y = size.height - line),
                    size = Size(width = size.width, height = line),
                )
            },
        contentAlignment = Alignment.TopCenter,
    ) {
        // **Measured on the window rather than on the capped column**, like every other compact
        // decision in the app: the rail is full-bleed, so what decides whether a cell has room is
        // the pane it is in. Below the threshold every cell stacks its rate under its stock — all
        // three, not the two that happened to overflow, because a bar where one cell is a line
        // taller than its neighbours is ragged rather than compact.
        val compact = maxWidth < OltreLayout.compactWidth
        Row(
            modifier = Modifier
                .widthIn(max = OltreLayout.maxContentWidth)
                .fillMaxWidth()
                .testTag(ShellTestTags.RESOURCE_RAIL_CONTENT)
                // Drawn rather than laid out, and deliberately: the three cells are the one place in
                // the app with no width to spare, and a 1dp element between them is 2dp taken off
                // the figures. Every cell carries `weight(1f)`, so the boundaries are exactly at a
                // third and two thirds of the row — this is not an approximation of where the cells
                // divide, it is where they divide.
                .drawBehind {
                    val line = HAIRLINE_WIDTH.toPx()
                    val inset = DIVIDER_INSET.toPx()
                    listOf(1f / 3f, 2f / 3f).forEach { fraction ->
                        drawRect(
                            color = HAIRLINE,
                            topLeft = Offset(x = size.width * fraction - line / 2f, y = inset),
                            size = Size(width = line, height = size.height - inset * 2),
                        )
                    }
                },
        ) {
            // The rates are already the throttled figures. What misled the player was not the
            // number but the absence of any mark saying it was being held down — a true rate
            // presented as an untroubled one. Recolouring costs no width, which matters in the
            // one component with none to spare.
            val throttled = uiState.throttled
            ResourceCell(
                kind = ResourceKind.METAL,
                stock = uiState.metal,
                orb = OltreColors.metal,
                throttled = throttled,
                compact = compact,
            )
            ResourceCell(
                kind = ResourceKind.CRYSTAL,
                stock = uiState.crystal,
                orb = OltreColors.crystal,
                throttled = throttled,
                compact = compact,
            )
            ResourceCell(
                kind = ResourceKind.DEUTERIUM,
                stock = uiState.deuterium,
                orb = OltreColors.deuterium,
                throttled = throttled,
                compact = compact,
            )
        }
    }
}

// Two lines rather than three: the hue that used to be carried only by the stock's own column
// arrives as an orb beside the caption, and the rate comes up onto the stock's baseline. The cell
// loses 12dp, and so does every screen under the rail.
@Composable
private fun RowScope.ResourceCell(
    kind: ResourceKind,
    stock: ResourceStockUiState,
    orb: Color,
    throttled: Boolean,
    compact: Boolean,
) {
    val mono = oltreMono()
    // The one thing on the frame that says what happened while the app was closed. It counts from
    // the figure the player was last looking at to the one the colony has accrued to, once, over
    // 900ms — and then it is a number again, tracking the tick instantly like every other reading.
    //
    // The font is monospaced, so the count is a stable width rather than a reflow: the cell does not
    // grow and shrink under a rolling figure, and the three cells stay on their columns throughout.
    val fill = rememberOneShotFill()
    val rolled = stock.lastSeenStock + ((stock.stock - stock.lastSeenStock) * fill).toLong()
    // Padded to the width of the figure it is heading for, and that is what "a stable-width count,
    // not a reflow" actually takes. Tabular numerals fix the width of a *digit*; they do nothing
    // about a count that grows from "900" to "1,400" and takes two characters with it. At 320dp the
    // stock and its rate share a wrapping row, so those two characters are enough to throw the rate
    // onto a second line halfway through the roll and pull it back at the end. Trailing spaces in a
    // monospaced face reserve the final width without moving a single digit.
    // Padded against the *resolved* target, because how wide a grouped figure is is a fact about
    // the language that groups it — an Italian "1.400" and an English "1,400" are the same width
    // and a language that groups in fours would not be.
    val counted = rolled.groupedByThousands().resolve()
        .padEnd(stock.stock.groupedByThousands().resolve().length)
    Column(
        modifier = Modifier
            .weight(1f)
            // Ahead of the padding, like every other tagged column in the app: a tag after it
            // marks the padded interior, and what the rate has to fit inside is the cell.
            .testTag(ShellTestTags.resourceCell(kind.name))
            .padding(horizontal = 11.dp, vertical = 9.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(modifier = Modifier.size(7.dp).background(orb, CircleShape))
            Text(
                text = Strings.resourceRailLabel(kind).resolve(),
                color = OltreColors.textTertiary,
                fontFamily = mono,
                fontSize = 9.5.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp,
                modifier = Modifier.padding(start = 5.dp),
            )
        }
        // **A row above the compact width, a column below it, and the two are not the same thing
        // with a flag.** Wide, the rate sits on the stock's own baseline and the pair is one line;
        // that is the whole of what the rail's second version bought, and it is what saves the bar
        // twelve pixels. Narrow, the pair does not fit — and which cells overflow depends on the
        // figures they happen to hold, so leaving it to the measurement wraps two of the three and
        // leaves the bar ragged. Below the threshold every cell stacks, so the three stay a set.
        //
        // `alignByBaseline` is why this is two branches rather than one with a direction: it is a
        // row's alignment and has no meaning in a column, where the two lines have their own.
        val figure: @Composable (Modifier) -> Unit = { modifier ->
            Text(
                text = counted,
                color = OltreColors.text,
                fontFamily = mono,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = modifier,
            )
        }
        val rate: @Composable (Modifier) -> Unit = { modifier ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = modifier.testTag(ShellTestTags.resourceRate(kind.name)),
            ) {
                if (throttled) {
                    PowerMark(color = OltreColors.warn, width = 7.dp, height = 10.dp)
                }
                Text(
                    text = stock.ratePerHour.resolve(),
                    color = if (throttled) OltreColors.warn else OltreColors.ok,
                    fontFamily = mono,
                    fontSize = 10.sp,
                    maxLines = 1,
                    softWrap = false,
                    modifier = Modifier.padding(start = if (throttled) 2.dp else 0.dp),
                )
            }
        }
        if (compact) {
            // 2dp: enough that the rate reads as its own line rather than as the figure's descender.
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                figure(Modifier)
                rate(Modifier)
            }
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                figure(Modifier.alignByBaseline())
                rate(Modifier.alignByBaseline())
            }
        }
    }
}

// The same white 9% the cards use for their hairlines, so the rail's edge and a row's edge are one
// decision rather than two that happen to match.
private val HAIRLINE = Color.White.copy(alpha = 0.09f)
private val HAIRLINE_WIDTH = 1.dp

// The divider stops short of the cell's own top and bottom padding, so it reads as a rule between
// two columns rather than as the bar being cut into three boxes.
private val DIVIDER_INSET = 7.dp
