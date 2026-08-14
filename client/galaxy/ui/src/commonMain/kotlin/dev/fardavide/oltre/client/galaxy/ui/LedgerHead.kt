package dev.fardavide.oltre.client.galaxy.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.fardavide.oltre.client.design.core.OltreColors
import dev.fardavide.oltre.client.design.core.oltreMono

// **The head of the Galaxy tab, above all three of its views.** The switch, the search and the
// filters — everything that says *which worlds*, where the body under it says *this world*.
//
// Three rows, and **the last two are gated by data rather than by the mode**: the switch-and-search
// row is unconditional, the chips row appears when there are chips, the count-and-sort line appears
// when there is a count. `MAP` reads as a bare switch only because a map has nothing to count and
// nothing to filter — the field itself never goes away. It is the literal answer to *"finding a
// planet feels like searching a phone number on pagine gialle in the 90s"*, and a `mode == WORLDS`
// wrapped around it would take the answer back.
//
// The 9dp gap belongs to the pair of rows either side of it, which is what `spacedBy` over an `if`
// already gives: a row that does not render takes its gap with it, and a one-row head has none.
//
// **Nothing here is tagged.** `GalaxyTestTags` has no constant for the switch, the field, a chip or
// the sort, and this file does not invent one — naming the four targets is the integrator's call.
//
// Tabular figures are asked for on the count and the chips and are not set anywhere below: JetBrains
// Mono is monospaced in all three weights, so `41 worlds` and `5 worlds` already advance alike.
//
// **Everything tappable here paints well under the 44pt minimum** — a pill is ~20dp, a chip ~24dp,
// the sort string ~14dp — and all three heights are pinned by the design, so growing them is not on
// offer. That is deliberate rather than unfinished: Compose expands the *hit* area of a pointer-input
// node to the platform minimum on a touch that lands near but outside it, and prefers any node the
// touch actually landed in, so the targets are a finger wide and the paint has not moved. The reflex
// that breaks it is wrapping a target in a taller Box, which would push the 28dp row to 44dp.
@Composable
internal fun LedgerHead(
    uiState: LedgerHeadUiState,
    onSelectMode: (LedgerMode) -> Unit,
    onQueryChange: (String) -> Unit,
    onToggleChip: (LedgerFilter) -> Unit,
    onCycleSort: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(9.dp)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            // The switch keeps its own width at every screen size and the field absorbs the rest:
            // at 320dp nothing drops and nothing wraps, because the only elastic thing in the row
            // is the one part that can afford to be shorter.
            ModeSwitch(mode = uiState.mode, onSelectMode = onSelectMode)
            SearchField(query = uiState.query, onQueryChange = onQueryChange, modifier = Modifier.weight(1f))
        }
        if (uiState.chips.isNotEmpty()) {
            // One scrolling row rather than a wrapping block: a second line of chips would push the
            // first world row off a 393dp screen, and the filters are worth less than the list.
            Row(
                horizontalArrangement = Arrangement.spacedBy(5.dp),
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            ) {
                uiState.chips.forEach { chip ->
                    // Keyed by the filter rather than by the label it prints, so a copy change
                    // cannot silently stop a chip working.
                    FilterChip(chip = chip, onToggle = { onToggleChip(chip.filter) })
                }
            }
        }
        uiState.count?.let { count -> Meta(count = count, sort = uiState.sort, onCycleSort = onCycleSort) }
    }
}

// Two pills in a 2dp trough, and **the group never reflows**: JetBrains Mono is monospaced in all
// three weights, so dropping bold onto the selected half cannot change either pill's width. Nothing
// animates the flip for the same reason nothing else in the app does.
@Composable
private fun ModeSwitch(mode: LedgerMode, onSelectMode: (LedgerMode) -> Unit) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        modifier = Modifier.background(GROUP_FILL, BADGE_SHAPE).padding(2.dp),
    ) {
        ModePill(
            label = "worlds",
            mode = LedgerMode.WORLDS,
            on = mode == LedgerMode.WORLDS,
            onClick = { onSelectMode(LedgerMode.WORLDS) },
        )
        ModePill(
            label = "map",
            mode = LedgerMode.MAP,
            on = mode == LedgerMode.MAP,
            onClick = { onSelectMode(LedgerMode.MAP) },
        )
    }
}

// Uppercase is a style and not a spelling — the word is lowercase in the source here as it is in the
// count and the sort, per the system's casing rule.
@Composable
private fun ModePill(label: String, mode: LedgerMode, on: Boolean, onClick: () -> Unit) {
    Text(
        text = label.uppercase(),
        color = if (on) OltreColors.accent else OltreColors.textTertiary,
        fontFamily = oltreMono(),
        fontSize = 9.5.sp,
        fontWeight = if (on) FontWeight.Bold else FontWeight.Normal,
        letterSpacing = 1.sp,
        maxLines = 1,
        softWrap = false,
        modifier = Modifier
            .testTag(GalaxyTestTags.mode(mode))
            .background(if (on) PILL_FILL else Color.Transparent, PILL_SHAPE)
            .clickable(onClick = onClick)
            .padding(horizontal = 9.dp, vertical = 4.dp),
    )
}

// **Always visible, never a mode**, and one boolean drives three channels at once: the border, the
// glyph and the ink all turn over the moment there is something typed. The field paints no fill of
// its own, so what shows through it is the starfield rather than a surface.
@Composable
private fun SearchField(query: String, onQueryChange: (String) -> Unit, modifier: Modifier = Modifier) {
    val typed = query.isNotEmpty()
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(7.dp),
        modifier = modifier
            .height(28.dp)
            .border(1.dp, if (typed) ON_EDGE else EDGE, RoundedCornerShape(9.dp))
            .padding(horizontal = 7.dp),
    ) {
        SearchGlyph(ink = if (typed) OltreColors.text else OltreColors.textTertiary)
        BasicTextField(
            value = query,
            onValueChange = onQueryChange,
            textStyle = TextStyle(color = OltreColors.text, fontFamily = oltreMono(), fontSize = 10.5.sp),
            singleLine = true,
            // The design specifies no caret, and a field a player types into has to have one — so it
            // takes the ink it is inserting. Not the accent: accent means *tap this* everywhere else
            // in the app, and a caret is not a target.
            cursorBrush = SolidColor(OltreColors.text),
            decorationBox = { field ->
                Box(contentAlignment = Alignment.CenterStart) {
                    // Behind the field rather than instead of it: the field is what owns the caret,
                    // and swapping it out on the first keystroke would take the cursor with it.
                    //
                    // The literal lowercase word — not "Search", not "Search worlds". A placeholder
                    // is the query in the faint ink, which is why it carries no style of its own.
                    if (!typed) {
                        Text(
                            text = "name",
                            color = OltreColors.textTertiary,
                            fontFamily = oltreMono(),
                            fontSize = 10.5.sp,
                            maxLines = 1,
                            softWrap = false,
                        )
                    }
                    field()
                }
            },
            modifier = Modifier.testTag(GalaxyTestTags.LEDGER_SEARCH).weight(1f),
        )
    }
}

// **Drawn here rather than in `:client:design:icon`**: one call site, and it is a Galaxy action mark
// at stroke 1.8 in a 24-unit box where that module carries the tab marks at 1.4–1.7.
//
// Two primitives, and two asymmetries that are the whole character of it. The ring is centred on
// (10.5, 10.5) of 24 while the handle runs out to (20.5, 20.5), so the mark sits bottom-right-heavy
// in its slot — centring the ring would be tidier and would not be this glyph. And the handle starts
// at 15.4 where the ring's own 45° point is 15.096, so there is a deliberate third of a unit of air
// between them that the round caps then eat back into. Do not snap the handle onto the ring.
@Composable
private fun SearchGlyph(ink: Color) {
    Canvas(modifier = Modifier.size(GLYPH)) {
        val k = size.minDimension / 24f
        val stroke = 1.8f * k
        drawCircle(
            color = ink,
            radius = 6.5f * k,
            center = Offset(10.5f * k, 10.5f * k),
            style = Stroke(width = stroke, cap = StrokeCap.Round),
        )
        drawLine(
            color = ink,
            start = Offset(15.4f * k, 15.4f * k),
            end = Offset(20.5f * k, 20.5f * k),
            strokeWidth = stroke,
            cap = StrokeCap.Round,
        )
    }
}

// **The one text in the head that keeps its case**, and the only one with no tracking: a filter is a
// phrase the player reads — "reachable 6h", "one level away" — where a pill and the count line are
// labels. An unset chip paints nothing at all, so a row of them reads as an offer rather than as a
// set of controls already spent.
@Composable
private fun FilterChip(chip: LedgerChipUiState, onToggle: () -> Unit) {
    Text(
        text = chip.label,
        color = if (chip.on) OltreColors.accent else OltreColors.textSecondary,
        fontFamily = oltreMono(),
        fontSize = 10.5.sp,
        maxLines = 1,
        softWrap = false,
        modifier = Modifier
            .border(1.dp, if (chip.on) ON_EDGE else EDGE, BADGE_SHAPE)
            .background(if (chip.on) ON_FILL else Color.Transparent, BADGE_SHAPE)
            .clickable(onClick = onToggle)
            .padding(horizontal = 7.dp, vertical = 4.dp),
    )
}

// **The count is the gate on the whole line** — a sort of nothing is not a control, so the order can
// never be shown without the number it orders. Bottom rather than the design's baseline alignment
// because the two strings are one size in one family, which puts them on the same line for free and
// costs no per-child modifier.
@Composable
private fun Meta(count: String, sort: LedgerSort, onCycleSort: () -> Unit) {
    val mono = oltreMono()
    Row(
        verticalAlignment = Alignment.Bottom,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(
            text = count.uppercase(),
            color = OltreColors.textTertiary,
            fontFamily = mono,
            fontSize = 10.5.sp,
            letterSpacing = 1.4.sp,
            maxLines = 1,
            // Takes the whole line so the sort is pushed to the far end and stays there whatever
            // either string says.
            modifier = Modifier.weight(1f),
        )
        // Accent on a string that is not selected and not a state, and it earns it by being a
        // target: a tap cycles the order. No chevron and no glyph — `→` and `·` are the only
        // non-letter marks the system allows anywhere, and a chevron is neither.
        Text(
            text = sort.label.uppercase(),
            color = OltreColors.accent,
            fontFamily = mono,
            fontSize = 10.5.sp,
            letterSpacing = 1.4.sp,
            maxLines = 1,
            softWrap = false,
            modifier = Modifier.testTag(GalaxyTestTags.LEDGER_SORT).clickable(onClick = onCycleSort),
        )
    }
}

// Accent at 45% and accent at 10%, the pair `DispatchSheet` already spends on the one lit control on
// a screen. Here it is a chip that is on — and the border alone on a field with something in it,
// which is the same claim made by an edge with nothing behind it.
private val ON_EDGE = OltreColors.accent.copy(alpha = 0.45f)
private val ON_FILL = OltreColors.accent.copy(alpha = 0.10f)

// A third accent alpha, and not interchangeable with the other two: a chip is one of several filters
// that can be on at once, where the selected pill is one of exactly two and always the only one — so
// it is a fill that reads as a position rather than as a state.
private val PILL_FILL = OltreColors.accent.copy(alpha = 0.22f)

private val GROUP_FILL = Color.White.copy(alpha = 0.09f)
private val EDGE = Color.White.copy(alpha = 0.16f)

// 3dp inside the group's 4dp with 2dp of padding between them: the inner radius that keeps the pair
// concentric, and the reason the pill is the one shape here that is not the badge radius.
private val PILL_SHAPE = RoundedCornerShape(3.dp)
private val BADGE_SHAPE = RoundedCornerShape(4.dp)

private val GLYPH = 13.dp
