package dev.fardavide.oltre.client.design.component

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.fardavide.oltre.client.design.core.OltreColors
import dev.fardavide.oltre.client.design.core.oltreMono

// What a row opens on a tap. The chrome round it is `OltreBottomSheet`, which every sheet in the app
// shares — the dispatch sheet on the Galaxy tab is the second one a player meets.
//
// It earns its place because three things have nowhere else to live: the arithmetic behind a
// verdict that reads "nothing", the ladder of what a level gates, and the numbers the verdict
// displaced. The alternative — expanding the row in place — pushes the rest of the list down and
// grows the densest component in the app to five lines.
//
// It lives here rather than in a feature because Colony and Research open the identical sheet, and
// the two row composables next door are the standing reminder of what happens when that is left to
// two copies: they have stayed identical by luck, edited twice, for four releases.
//
// The chrome and the contents are separate for the reason `DebugSheet` states: every assertion
// about what the sheet *says* is written against `RowSheetContent`, so a behaviour test never
// depends on a popup being reachable or an enter animation settling.
@Composable
fun RowSheet(
    uiState: RowSheetUiState,
    onAct: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    contentModifier: Modifier = Modifier,
    actionModifier: Modifier = Modifier,
) {
    OltreBottomSheet(onDismiss = onDismiss, modifier = modifier) {
        RowSheetContent(
            uiState = uiState,
            onAct = onAct,
            modifier = contentModifier,
            actionModifier = actionModifier,
        )
    }
}

// Everything the sheet says, with no sheet around it.
@Composable
fun RowSheetContent(
    uiState: RowSheetUiState,
    onAct: () -> Unit,
    modifier: Modifier = Modifier,
    actionModifier: Modifier = Modifier,
) {
    Column(
        // 11dp between blocks is the card's own padding rhythm, which is what makes the sheet read
        // as the row it came from rather than as a second design.
        verticalArrangement = Arrangement.spacedBy(11.dp),
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            // No top padding: the drag handle is the space above the name.
            .padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
    ) {
        Heading(uiState)
        if (uiState.lines.isNotEmpty()) Prose(uiState.lines)
        if (uiState.ladder.isNotEmpty()) Ladder(uiState.ladder)
        uiState.pointer?.let { Pointer(it) }
        uiState.footer?.let { Footer(footer = it, onAct = onAct, actionModifier = actionModifier) }
    }
}

@Composable
private fun Heading(uiState: RowSheetUiState) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = uiState.name,
            color = OltreColors.text,
            fontFamily = oltreMono(),
            fontSize = 13.5.sp,
            fontWeight = FontWeight.Medium,
        )
        // The third copy of the row's level badge, and the last one that should be written by hand:
        // `FacilityList` and `TechnologyList` each carry their own, because theirs also swaps the
        // number behind a completion sweep and this one has nothing to announce.
        Text(
            text = "LV ${uiState.level}",
            color = OltreColors.textSecondary,
            fontFamily = oltreMono(),
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            softWrap = false,
            modifier = Modifier
                .padding(start = 7.dp)
                .background(Color.White.copy(alpha = 0.09f), RoundedCornerShape(4.dp))
                .padding(horizontal = 5.dp, vertical = 2.dp),
        )
        Spacer(modifier = Modifier.width(10.dp))
        // Pushed to the far end rather than set under the name: it is the sentence the player has
        // just read on the row, repeated so the sheet answers a question they still have in mind.
        Text(
            text = uiState.verdict,
            color = OltreColors.textSecondary,
            fontFamily = oltreMono(),
            fontSize = 10.5.sp,
            maxLines = 2,
            // Two lines and then an ellipsis rather than a silent cut: at 320dp beside a name as
            // long as "Deuterium Synth." this column is about sixteen characters wide, and a
            // payback dropped without a mark is a number the reader does not know they are missing.
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
    }
}

// The one place in the app that sets a paragraph rather than a line, so it is the one place with a
// line height worth stating: 12sp over 19sp, which is the ratio the design's prose was measured at.
@Composable
private fun Prose(lines: List<SheetLine>) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        for (line in lines) {
            Text(
                text = line.annotated(),
                color = OltreColors.textSecondary,
                fontFamily = oltreMono(),
                fontSize = 12.sp,
                lineHeight = 19.sp,
            )
        }
    }
}

// A figure is the same words in the body colour — no weight change, because a bold numeral in a
// tabular face changes the column width and the sheet is nothing but columns of numbers.
@Composable
private fun SheetLine.annotated(): AnnotatedString = buildAnnotatedString {
    for (part in parts) {
        when (part) {
            is SheetLinePart.Words -> append(part.text)
            is SheetLinePart.Figure -> withStyle(SpanStyle(color = OltreColors.text)) { append(part.text) }
        }
    }
}

// What each level opens, against the level the player holds. The one they already have is on the
// ladder too, greyed — it is how you learn that gating is a thing this building does at all.
@Composable
private fun Ladder(steps: List<SheetLadderStep>) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        for (step in steps) {
            Row {
                Text(
                    text = step.level,
                    color = if (step.held) OltreColors.textTertiary else OltreColors.accent,
                    fontFamily = oltreMono(),
                    fontSize = 10.5.sp,
                    modifier = Modifier.width(46.dp),
                )
                Text(
                    text = step.opens,
                    color = OltreColors.textSecondary,
                    fontFamily = oltreMono(),
                    fontSize = 10.5.sp,
                )
            }
        }
    }
}

// The row to look at instead, or the row that moves the gate. Not a link: nothing in the app
// navigates from a row yet, and an accent string that is not a target is a worse violation than a
// muted one. It names the row, and the player's thumb already knows where rows are.
@Composable
private fun Pointer(pointer: SheetPointer) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .background(Color.White.copy(alpha = 0.03f), RoundedCornerShape(4.dp))
            .padding(horizontal = 10.dp, vertical = 7.dp),
    ) {
        Text(
            text = pointer.name,
            color = OltreColors.text,
            fontFamily = oltreMono(),
            fontSize = 10.5.sp,
        )
        Text(
            text = pointer.detail,
            color = OltreColors.textSecondary,
            fontFamily = oltreMono(),
            fontSize = 10.5.sp,
        )
    }
}

// The row's own price line and its own action, so the sheet is somewhere a decision can be made
// rather than somewhere you read about one and then go back.
@Composable
private fun Footer(footer: SheetFooter, onAct: () -> Unit, actionModifier: Modifier) {
    Column {
        Spacer(
            modifier = Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(Color.White.copy(alpha = 0.16f)),
        )
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
        ) {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
                itemVerticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f),
            ) {
                for (chip in footer.costs) CostChip(chip = chip)
                Text(
                    text = footer.duration,
                    color = OltreColors.textSecondary,
                    fontFamily = oltreMono(),
                    fontSize = 10.5.sp,
                )
            }
            SheetActionButton(action = footer.action, onAct = onAct, modifier = actionModifier)
        }
    }
}

// The same two buttons the row carries, at the same size — a sheet that invented a third would be
// a second design of the one control the app has.
@Composable
private fun SheetActionButton(action: SheetAction, onAct: () -> Unit, modifier: Modifier) {
    when (action) {
        is SheetAction.Live -> Text(
            text = action.label,
            color = Color.White,
            fontFamily = oltreMono(),
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            // `pressable` ahead of the fill, as everywhere else: a background declared first is
            // drawn outside the scaling layer.
            modifier = modifier
                .pressable { onAct() }
                .background(OltreColors.accent, RoundedCornerShape(9.dp))
                .padding(horizontal = 11.dp, vertical = 7.dp),
        )
        // No disabled state, here or anywhere: a player who wants the level they cannot afford yet
        // is told when, not told no.
        is SheetAction.Ghost -> Text(
            text = action.label,
            color = OltreColors.textTertiary,
            fontFamily = oltreMono(),
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = modifier
                .border(1.dp, Color.White.copy(alpha = 0.16f), RoundedCornerShape(9.dp))
                .padding(horizontal = 11.dp, vertical = 7.dp),
        )
    }
}

// ── What a sheet is made of ──────────────────────────────────────────────────────────────────
//
// Plain data, and strings the feature has already worded: a design-system component that knew a
// building from a technology would be a feature in the wrong module. `CostChipUiState` next door
// is the precedent, down to carrying a `:core` enum and nothing else of the game.
data class RowSheetUiState(
    val name: String,
    val level: Int,
    val verdict: String,
    val lines: List<SheetLine>,
    val ladder: List<SheetLadderStep>,
    val pointer: SheetPointer?,
    // Null on a row with nothing to offer: a locked one, which has no price yet, and a running one,
    // which has already been paid for. Both end on what to do about it instead.
    val footer: SheetFooter?,
)

// A sentence with its figures picked out, rather than a string with markup in it. The parts are
// data, so a mapper's test can assert the numbers without parsing anything and the component owns
// what "picked out" looks like.
data class SheetLine(val parts: List<SheetLinePart>)

sealed interface SheetLinePart {

    data class Words(val text: String) : SheetLinePart

    data class Figure(val text: String) : SheetLinePart
}

// `held` is computed against the current level rather than written, so "you have this" cannot go
// stale the moment the level is bought.
data class SheetLadderStep(val level: String, val opens: String, val held: Boolean)

data class SheetPointer(val name: String, val detail: String)

data class SheetFooter(val costs: List<CostChipUiState>, val duration: String, val action: SheetAction)

sealed interface SheetAction {

    data class Live(val label: String) : SheetAction

    data class Ghost(val label: String) : SheetAction
}

fun words(text: String): SheetLinePart = SheetLinePart.Words(text)

fun figure(text: String): SheetLinePart = SheetLinePart.Figure(text)

fun sheetLine(vararg parts: SheetLinePart): SheetLine = SheetLine(parts.toList())
