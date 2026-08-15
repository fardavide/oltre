package dev.fardavide.oltre.client.galaxy.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.fardavide.oltre.client.design.component.oltreCardShape
import dev.fardavide.oltre.client.design.component.oltreCardSurface
import dev.fardavide.oltre.client.design.core.OltreColors
import dev.fardavide.oltre.client.design.core.oltreMono
import dev.fardavide.oltre.core.GalaxyCoordinate
import dev.fardavide.oltre.core.ResourceKind

// **One card, six verdicts, two screens.** The system view and the ledger draw this row and nothing
// else, which is what makes the ledger cheap and what makes a world look like itself wherever you
// meet it. *What* it holds, and why the order never changes, is `GalaxyRowUiState.World`'s comment;
// what this file adds is where each block sits and which of them is allowed to shrink.
//
// ── Three things here are easy to get wrong and cheap to get right ───────────────────────────────
//
// 1. **The address has two homes and exactly one is ever used.** The epithet decides: with one it
//    sits on the subtitle, without one there is no subtitle at all and the address trails the name.
//    Printing both is the natural mistake, and it doubles the one string on the row that is the
//    row's key.
// 2. **Only the name and the epithet shrink.** The verdict word, the address and the trailing figure
//    never truncate, so a 320dp pane wraps the deposit and requirement lines and grows the card
//    rather than losing a reading. There is no compact flag on this row and no branch that drops
//    anything — the narrow width is a wrap, not a state.
// 3. **The accent border is `HOME` and nothing else.** Settleable is the verdict a player is hunting
//    and it still does not get it: the border says *yours*, not *good*.
//
// Nothing asks for tabular figures anywhere below. JetBrains Mono advances every digit the same
// width already, so the design's per-element `tabular-nums` is satisfied by the family — and a font
// feature spelled out on five of the eight Texts would read as a rule where there is none.
@Composable
internal fun WorldRow(
    row: GalaxyRowUiState.World,
    onOpenResearch: () -> Unit,
    onOpenWorld: (GalaxyCoordinate) -> Unit,
    modifier: Modifier = Modifier,
) {
    val home = row.verdict == WorldVerdictUiState.HOME
    Column(
        modifier = modifier
            .fillMaxWidth()
            .border(
                1.dp,
                if (home) OltreColors.accent.copy(alpha = 0.45f) else Color.White.copy(alpha = 0.09f),
                oltreCardShape,
            )
            .background(oltreCardSurface, oltreCardShape)
            .testTag(GalaxyTestTags.row(row.at))
            .clickable(enabled = row.verdict.isRunnable()) { onOpenWorld(row.at) }
            .padding(11.dp),
        // The one gap between every top-level block: header, deposits, requirements, note. A block
        // with nothing to say is omitted and nothing takes its place, so this spacing is also what
        // makes an unsurveyed row the shortest card in the app at ~50dp — which clears the 44dp
        // touch minimum without the row having to claim it.
        verticalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        Header(row = row)
        // Guarded rather than left to the FlowRow: a child of zero height still spends the Column's
        // 7dp on both sides of itself, so an empty deposit line reads as a 14dp hole under the name.
        row.deposits?.takeIf { it.metal != null || it.crystal != null }?.let { DepositLine(deposits = it) }
        if (row.requirements.isNotEmpty()) {
            Column(verticalArrangement = Arrangement.spacedBy(5.dp), modifier = Modifier.fillMaxWidth()) {
                row.requirements.forEach { requirement ->
                    RequirementLine(at = row.at, requirement = requirement, onOpenResearch = onOpenResearch)
                }
            }
        }
        row.note?.let { Note(text = it) }
    }
}

// **A hairline and no fill, because it is not a card and not a target.** It states its effect and
// stops: no holding mechanic exists until multiplayer, and a relay has no hold for a fleet to fill.
//
// V5 does not draw a relay — the design pass is about worlds — so this is 0.9's row carried into the
// new shape with the two things that no longer exist removed: there is no portrait, because a relay
// is not a world, and no orbit tag, because the disc retired it everywhere.
//
// **`RELAY` is no longer accent, and that is the one considered change.** Accent means "go tap this"
// and nothing else, this row is deliberately not tappable, and an accent string that is not a target
// is a worse violation than a demoted one — settled at 0.0.18 and restated on `RowVerdict`. Bold and
// the label's tracking are what separate the word from the address now, not a hue.
@Composable
internal fun RelayRow(row: GalaxyRowUiState.Relay, modifier: Modifier = Modifier) {
    val mono = oltreMono()
    Column(
        modifier = modifier
            .fillMaxWidth()
            .border(1.dp, Color.White.copy(alpha = 0.09f), oltreCardShape)
            .testTag(GalaxyTestTags.row(row.at))
            .padding(11.dp),
        verticalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            Text(
                text = row.coordinate,
                color = OltreColors.textSecondary,
                fontFamily = mono,
                fontSize = 13.5.sp,
                lineHeight = 18.sp,
                maxLines = 1,
                softWrap = false,
                modifier = Modifier.weight(1f).alignByBaseline(),
            )
            Text(
                text = "RELAY",
                color = OltreColors.textSecondary,
                fontFamily = mono,
                fontSize = 10.5.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.4.sp,
                maxLines = 1,
                softWrap = false,
                modifier = Modifier.alignByBaseline(),
            )
        }
        Text(
            text = row.effect,
            color = OltreColors.textTertiary,
            fontFamily = mono,
            fontSize = 10.5.sp,
            lineHeight = 16.sp,
        )
    }
}

// **The disc is centred against the whole text column, and the lines inside it are not.** The header
// aligns the portrait on the box's centre while the headline and subtitle align their own children on
// the baseline — get that pair the wrong way round and a 26dp disc floats against a two-line column.
//
// 26dp and the 7dp beside it are 33dp of the 339dp a card gets at 393dp, which is what leaves the
// headline the 306dp that fits a fifteen-character name and the widest verdict word with room over.
@Composable
private fun Header(row: GalaxyRowUiState.World) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(7.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        WorldPortrait(uiState = row.portrait, box = 26.dp)
        // 2dp, and it is a literal rather than the 7dp every other gap on this card spends: the two
        // lines are one paragraph about one world, and at the block gap they read as two blocks.
        Column(verticalArrangement = Arrangement.spacedBy(2.dp), modifier = Modifier.weight(1f)) {
            Headline(row = row)
            row.epithet?.let { epithet ->
                Subtitle(epithet = epithet, coordinate = row.coordinate, trailing = row.trailing)
            }
        }
    }
}

// Name, verdict word, and the address only when there is no subtitle to hold it.
@Composable
private fun Headline(row: GalaxyRowUiState.World) {
    val mono = oltreMono()
    // Baseline rather than `Alignment.Bottom`, which is what the rest of the app spends: those two
    // agree only while every child is the same size, and this is the one line in the app that mixes
    // 13.5sp with 10.5sp. Bottom-aligned, the small type sits visibly under the name.
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
        Text(
            text = row.name,
            color = OltreColors.text,
            fontFamily = mono,
            fontSize = 13.5.sp,
            lineHeight = 18.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            // The only thing on the headline that gives way, and it grows to fill so that the word
            // and the address sit at the right edge on a short name too.
            modifier = Modifier.weight(1f).alignByBaseline(),
        )
        // **Uppercased here and capitalised in the model**, so the string stays a word a sentence
        // could contain. Null on `UNSURVEYED`, which is the design's one subtraction: an empty socket
        // beside a drawn disc is the state, and it buys back the right end of 98% of rows.
        row.verdict.word?.let { word ->
            Text(
                text = word.uppercase(),
                color = row.verdict.hue(),
                fontFamily = mono,
                fontSize = 10.5.sp,
                fontWeight = FontWeight.Bold,
                // 0.133em, and it is load-bearing: it is what makes SETTLEABLE 79dp rather than 63,
                // which is the width the headline budget was measured against.
                letterSpacing = 1.4.sp,
                maxLines = 1,
                softWrap = false,
                modifier = Modifier.alignByBaseline(),
            )
        }
        // The address's second home. See note 1 at the top of the file — there is no branch anywhere
        // else that can print it twice, because the subtitle only exists when the epithet does.
        if (row.epithet == null) {
            Text(
                text = row.coordinate,
                color = OltreColors.textTertiary,
                fontFamily = mono,
                fontSize = 10.5.sp,
                maxLines = 1,
                softWrap = false,
                modifier = Modifier.alignByBaseline(),
            )
        }
    }
}

// Epithet, address, and the round trip flush right. Every child is 10.5sp, so `Alignment.Bottom` and
// the baseline are the same line here and the cheaper of the two is enough.
@Composable
private fun Subtitle(epithet: String, coordinate: String, trailing: String?) {
    val mono = oltreMono()
    Row(verticalAlignment = Alignment.Bottom, modifier = Modifier.fillMaxWidth()) {
        // The pair is grouped and the group takes the width, which is what pushes the trailing figure
        // to the far edge. Inside it only the epithet gives way: the address is the row's key and a
        // truncated one is a wrong one.
        Row(
            horizontalArrangement = Arrangement.spacedBy(7.dp),
            verticalAlignment = Alignment.Bottom,
            modifier = Modifier.weight(1f),
        ) {
            Text(
                text = epithet,
                color = OltreColors.textSecondary,
                fontFamily = mono,
                fontSize = 10.5.sp,
                lineHeight = 15.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                // `fill = false`: it takes what it needs up to its share and hands the rest back, so
                // a two-word epithet does not shove the address to the right of a gap.
                modifier = Modifier.weight(1f, fill = false),
            )
            Text(
                text = coordinate,
                color = OltreColors.textTertiary,
                fontFamily = mono,
                fontSize = 10.5.sp,
                lineHeight = 15.sp,
                maxLines = 1,
                softWrap = false,
            )
        }
        // Only in the ledger, and only where a subtitle exists to carry it — the system view states
        // the round trip once, under its header, because it is identical for all fifteen slots.
        trailing?.let {
            Text(
                text = it,
                color = OltreColors.textTertiary,
                fontFamily = mono,
                fontSize = 10.5.sp,
                lineHeight = 15.sp,
                maxLines = 1,
                softWrap = false,
                modifier = Modifier.padding(start = 7.dp),
            )
        }
    }
}

// Metal then crystal, rail order, and **never deuterium** — a run cannot lift it, so a figure for it
// would be an offer the verb refuses.
//
// It wraps rather than dropping its second item, which is the whole 320dp rule on this row: what a
// narrow pane costs is a line of height, never a reading.
@Composable
private fun DepositLine(deposits: DepositReadingUiState) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        // Tighter than the 7dp between blocks, so a wrapped deposit reads as the same line continued
        // rather than as a block of its own.
        verticalArrangement = Arrangement.spacedBy(5.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        deposits.metal?.let { DepositItem(item = it) }
        deposits.crystal?.let { DepositItem(item = it) }
    }
}

// **One Text, two colours, one unbreakable run.** `metal` and `full` are a single reading and must
// never wrap apart or become two items the flow can separate — which is why the colour change is a
// span inside an `AnnotatedString` and the wrap happens between items, not inside one.
@Composable
private fun DepositItem(item: DepositItemUiState) {
    Text(
        text = buildAnnotatedString {
            withStyle(SpanStyle(color = item.resource.hue())) { append(item.resource.word()) }
            withStyle(SpanStyle(color = item.tone.hue())) { append(" ${item.reading}") }
        },
        fontFamily = oltreMono(),
        fontSize = 10.5.sp,
        lineHeight = 15.sp,
        maxLines = 1,
        softWrap = false,
    )
}

// One line per failing axis: the arithmetic on the left, the ladder that lands it flush right.
//
// **`Blocked` naming its own remedy is the load-bearing detail of the whole verdict** — it is what
// turns the galaxy screen into a reason to research, and the only thing connecting two tabs that
// otherwise never speak. So the technology is a target, and the accent is legitimate for once.
@Composable
private fun RequirementLine(at: GalaxyCoordinate, requirement: BlockedAxisUiState, onOpenResearch: () -> Unit) {
    val mono = oltreMono()
    Row(verticalAlignment = Alignment.Top, modifier = Modifier.fillMaxWidth()) {
        Text(
            // The unit is written once, on the tolerance: both figures are the same axis and so the
            // same unit, and the four characters that saves are what keep the ladder on the line.
            text = "${requirement.axis} ${requirement.reading}, you tolerate ${requirement.tolerated}",
            color = OltreColors.textTertiary,
            fontFamily = mono,
            fontSize = 10.5.sp,
            lineHeight = 15.sp,
            // The clause is the only half that wraps; at 320dp it takes two lines and the ladder
            // stays whole beside it.
            modifier = Modifier.weight(1f),
        )
        Text(
            text = requirement.label,
            color = OltreColors.accent,
            fontFamily = mono,
            fontSize = 10.5.sp,
            lineHeight = 15.sp,
            maxLines = 1,
            softWrap = false,
            // **The padding is inside the clickable, and the order is the whole point.** A 10.5sp
            // line box is 15dp tall and every other target in the app is 30–32dp: put the padding
            // after `clickable` and the hit rectangle is the glyphs alone, a third of the iOS
            // minimum, and a near miss lands on the card — which opens a sheet about a different
            // decision entirely. 6dp rather than the button's 7dp so that three stacked axes still
            // clear each other by the 5dp above rather than visibly loosening.
            modifier = Modifier
                .clickable(onClick = onOpenResearch)
                .testTag(GalaxyTestTags.adaptation(at, requirement.technology))
                .padding(start = 10.dp, top = 6.dp, bottom = 6.dp),
        )
    }
}

// The one block allowed to run to several lines, and the one at 1.5 rather than 1.45: it is a
// sentence rather than a reading, and a sentence set at a reading's leading reads as a table cell.
@Composable
private fun Note(text: String) {
    Text(
        text = text,
        color = OltreColors.textSecondary,
        fontFamily = oltreMono(),
        fontSize = 10.5.sp,
        lineHeight = 16.sp,
    )
}

// `startRun`'s outright refusals, restated as the rule for whether the card raises a sheet: your own
// world and a world somebody holds refuse at the verb, so offering a sheet for either would be the
// screen promising something thrown away on the first tap. Everything else is a target — **including
// `BLOCKED`, which is the mechanic**: hostility gates settling and never gathering, so the commonest
// verdict on the map is an ordinary destination. A relay is not a `World` and never reaches here.
private fun WorldVerdictUiState.isRunnable(): Boolean = when (this) {
    WorldVerdictUiState.UNSURVEYED,
    WorldVerdictUiState.BLOCKED,
    WorldVerdictUiState.BARREN,
    WorldVerdictUiState.SETTLEABLE,
    -> true
    WorldVerdictUiState.HOME,
    WorldVerdictUiState.OCCUPIED,
    -> false
}

// **The row's only colour**, and three of the six are grey on purpose: another empire holding a world
// is not a warning, and a world that pays badly is not one either. `UNSURVEYED` keeps an entry it
// never spends — its word is null — so that restoring the word would be a one-line change rather than
// an argument reopened from scratch.
private fun WorldVerdictUiState.hue(): Color = when (this) {
    WorldVerdictUiState.HOME -> OltreColors.accent
    WorldVerdictUiState.OCCUPIED -> OltreColors.textSecondary
    WorldVerdictUiState.UNSURVEYED -> OltreColors.textTertiary
    WorldVerdictUiState.BLOCKED -> OltreColors.danger
    WorldVerdictUiState.BARREN -> OltreColors.textSecondary
    WorldVerdictUiState.SETTLEABLE -> OltreColors.ok
}

// The resource rail's hues, so a deposit is the same colour here as the stock it will become.
// Deuterium has no place on this line and the mapper emits none — the branch exists because the row
// prints what it is handed rather than deciding what it may be handed.
private fun ResourceKind.hue(): Color = when (this) {
    ResourceKind.METAL -> OltreColors.metal
    ResourceKind.CRYSTAL -> OltreColors.crystal
    ResourceKind.DEUTERIUM -> OltreColors.deuterium
}

private fun ResourceKind.word(): String = when (this) {
    ResourceKind.METAL -> "metal"
    ResourceKind.CRYSTAL -> "crystal"
    ResourceKind.DEUTERIUM -> "deuterium"
}

// A word at each end and a working fraction between them. **Only `EMPTY` takes a status hue**: a full
// vein is the ordinary case on 98% of worlds and colouring it would light up a galaxy nobody has
// touched, while a fraction is a figure to read rather than a state to react to.
private fun DepositTone.hue(): Color = when (this) {
    DepositTone.FULL -> OltreColors.textSecondary
    DepositTone.EMPTY -> OltreColors.danger
    DepositTone.PARTIAL -> OltreColors.text
}
