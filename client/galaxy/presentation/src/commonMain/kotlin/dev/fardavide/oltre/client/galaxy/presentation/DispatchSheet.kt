package dev.fardavide.oltre.client.galaxy.presentation

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.fardavide.oltre.client.design.core.OltreColors
import dev.fardavide.oltre.client.design.core.OltreLayout
import dev.fardavide.oltre.client.design.core.oltreMono
import dev.fardavide.oltre.core.ResourceKind
import kotlin.time.Duration

// **Raised from a world row, and the only screen in the app that covers another one.** Everything
// else in Oltre is a list you scroll: a sheet exists here because a run is the one action with three
// inputs, and three controls inside a 106dp row would be the row becoming a screen anyway.
//
// Bring back, send, home in — in that order, because it is the order of decreasing permanence. What
// your colony is short of changes over days, how many hulls you have changes over hours, and how
// long you will be away changes every check-in. The figure sits under a rule and above the verb, and
// is the only thing on the sheet that moves when a control is touched.
@Composable
internal fun DispatchSheet(
    uiState: DispatchUiState,
    compact: Boolean,
    onDismiss: () -> Unit,
    onSelectGathering: (ResourceKind) -> Unit,
    onSelectShips: (Int) -> Unit,
    onSelectWindow: (Duration) -> Unit,
    onDispatch: () -> Unit,
    onDispatchProbe: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // A column rather than a stack, and that is not a detail: the scrim is **the part of the screen
    // the sheet is not covering**, so a tap on it is unambiguous — there is no overlap for a hit
    // test to resolve and no way to aim at the scrim and reach a control. The sheet is opaque, so
    // nothing is lost by not tinting behind it.
    Column(modifier = modifier.fillMaxSize()) {
        // The way out, and the only one: there is no cancel button because there is nothing to
        // cancel. The sheet costs nothing to open and commits nothing until the verb is tapped, so
        // an explicit dismiss would be a control whose whole job is to undo an action that has not
        // happened.
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .background(SCRIM)
                .testTag(GalaxyTestTags.SHEET_SCRIM)
                .clickable(onClick = onDismiss),
        )
        Column(
            modifier = Modifier
                .align(Alignment.CenterHorizontally)
                .widthIn(max = OltreLayout.maxContentWidth)
                .fillMaxWidth()
                .background(OltreColors.surface, SHEET_SHAPE)
                .border(1.dp, Color.White.copy(alpha = 0.09f), SHEET_SHAPE)
                .testTag(GalaxyTestTags.SHEET)
                .padding(start = 16.dp, end = 16.dp, top = 9.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(13.dp),
        ) {
            Grabber()
            Head(uiState = uiState, compact = compact)
            when (uiState) {
                is DispatchUiState.Offer -> Offer(
                    uiState = uiState,
                    compact = compact,
                    onSelectGathering = onSelectGathering,
                    onSelectShips = onSelectShips,
                    onSelectWindow = onSelectWindow,
                    onDispatch = onDispatch,
                )
                is DispatchUiState.Refuse -> Refuse(uiState = uiState, onDispatchProbe = onDispatchProbe)
            }
        }
    }
}

// The one affordance that says "this drags away", drawn rather than implemented: nothing in this app
// gestures yet, and a handle that looked draggable and was not would be a worse promise than the
// scrim it sits above. It is there because a sheet with no handle at all reads as a screen that
// arrived by mistake.
@Composable
private fun ColumnScope.Grabber() {
    Box(
        modifier = Modifier
            .align(Alignment.CenterHorizontally)
            .size(width = 36.dp, height = 4.dp)
            .background(Color.White.copy(alpha = 0.17f), RoundedCornerShape(2.dp)),
    )
}

// The coordinate on the left at the size the resource rail spends on a stock, the world on the
// right in the faintest ink on the sheet. It is the sheet answering "which world is this" before it
// answers anything else, and it is the same address the row underneath is printing.
@Composable
private fun Head(uiState: DispatchUiState, compact: Boolean) {
    val mono = oltreMono()
    Row(verticalAlignment = Alignment.Bottom, modifier = Modifier.fillMaxWidth()) {
        Text(
            text = uiState.coordinate,
            color = OltreColors.text,
            fontFamily = mono,
            fontSize = 15.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            softWrap = false,
        )
        Text(
            text = if (compact) uiState.compactHead else uiState.head,
            color = OltreColors.textTertiary,
            fontFamily = mono,
            fontSize = 10.5.sp,
            textAlign = TextAlign.End,
            maxLines = 1,
            modifier = Modifier.weight(1f).padding(start = 8.dp),
        )
    }
}

@Composable
private fun Offer(
    uiState: DispatchUiState.Offer,
    compact: Boolean,
    onSelectGathering: (ResourceKind) -> Unit,
    onSelectShips: (Int) -> Unit,
    onSelectWindow: (Duration) -> Unit,
    onDispatch: () -> Unit,
) {
    Control(label = "Bring back") {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            // Two, and never three. Deuterium is the Robotics gate's currency and a fleet that could
            // fetch it would undercut the one requirement the whole mid-game hangs on — so it is not
            // a disabled third card, it is not a card.
            GatherCard(
                kind = ResourceKind.METAL,
                name = "Metal",
                richness = uiState.metalRichness,
                hue = OltreColors.metal,
                selected = uiState.gathering == ResourceKind.METAL,
                onClick = { onSelectGathering(ResourceKind.METAL) },
                modifier = Modifier.weight(1f),
            )
            GatherCard(
                kind = ResourceKind.CRYSTAL,
                name = "Crystal",
                richness = uiState.crystalRichness,
                hue = OltreColors.crystal,
                selected = uiState.gathering == ResourceKind.CRYSTAL,
                onClick = { onSelectGathering(ResourceKind.CRYSTAL) },
                modifier = Modifier.weight(1f),
            )
        }
    }
    Control(label = "Send", trailing = uiState.pool) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Stepper(
                glyph = "−",
                enabled = !uiState.atFewest,
                tag = GalaxyTestTags.SHIPS_FEWER,
                onClick = { onSelectShips(uiState.shipCount - 1) },
            )
            Text(
                text = uiState.ships,
                color = OltreColors.text,
                fontFamily = oltreMono(),
                fontSize = 13.5.sp,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
                modifier = Modifier.weight(1f),
            )
            Stepper(
                glyph = "+",
                enabled = !uiState.atMost,
                tag = GalaxyTestTags.SHIPS_MORE,
                onClick = { onSelectShips(uiState.shipCount + 1) },
            )
        }
    }
    Control(label = "Home in") {
        Row(horizontalArrangement = Arrangement.spacedBy(5.dp), modifier = Modifier.fillMaxWidth()) {
            uiState.windows.forEach { rung ->
                WindowRung(rung = rung, onClick = { onSelectWindow(rung.window) }, modifier = Modifier.weight(1f))
            }
        }
        // Only when the ladder has actually narrowed. The rung that vanished is the copy — this
        // sentence is for the player who never saw the full ladder and cannot know one is missing.
        uiState.ladderNote?.let { note ->
            Text(
                text = note,
                color = OltreColors.textTertiary,
                fontFamily = oltreMono(),
                fontSize = 10.5.sp,
                lineHeight = 15.sp,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
    }
    Rule()
    Column(verticalArrangement = Arrangement.spacedBy(7.dp), modifier = Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.Bottom, modifier = Modifier.fillMaxWidth()) {
            Text(
                text = uiState.figure,
                color = if (uiState.gathering == ResourceKind.CRYSTAL) OltreColors.crystal else OltreColors.metal,
                fontFamily = oltreMono(),
                fontSize = 22.sp,
                lineHeight = 25.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f),
            )
            // Absent on a single hull, because "132 each" beside "132 metal" is the same number
            // printed twice.
            uiState.perShip?.let { each ->
                Text(
                    text = each,
                    color = OltreColors.textTertiary,
                    fontFamily = oltreMono(),
                    fontSize = 10.5.sp,
                    maxLines = 1,
                    softWrap = false,
                )
            }
        }
        // The three lines that explain the figure, and nothing else. Both are deterministic and both
        // are stated before the tap: nothing in this mechanic is rolled, so these are a
        // specification rather than a warning.
        Detail(text = if (compact) uiState.compactLegs else uiState.legs)
        Detail(text = if (compact) uiState.compactDanger else uiState.danger)
    }
    Verb(label = "Dispatch", tag = GalaxyTestTags.SEND, primary = true, onClick = onDispatch)
}

@Composable
private fun Refuse(uiState: DispatchUiState.Refuse, onDispatchProbe: () -> Unit) {
    Rule()
    Text(
        text = uiState.title,
        color = OltreColors.text,
        fontFamily = oltreMono(),
        fontSize = 13.5.sp,
        lineHeight = 18.sp,
        fontWeight = FontWeight.SemiBold,
    )
    Text(
        text = uiState.note,
        color = OltreColors.textSecondary,
        fontFamily = oltreMono(),
        fontSize = 10.5.sp,
        lineHeight = 17.sp,
    )
    when (val action = uiState.action) {
        // The one refusal in the app that hands back a verb rather than a wait.
        is RefuseActionUiState.Probe ->
            Verb(label = action.label, tag = GalaxyTestTags.SHEET_ACTION, primary = true, onClick = onDispatchProbe)
        // A reading, not a control — the idiom the unaffordable probe already spends. It carries the
        // test tag anyway, so a test can tap it and assert that tapping it does nothing.
        is RefuseActionUiState.Waiting ->
            Verb(label = action.label, tag = GalaxyTestTags.SHEET_ACTION, primary = false, onClick = {})
        null -> Unit
    }
}

@Composable
private fun Control(label: String, trailing: String? = null, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(7.dp), modifier = Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.Bottom, modifier = Modifier.fillMaxWidth()) {
            Text(
                text = label.uppercase(),
                color = OltreColors.textTertiary,
                fontFamily = oltreMono(),
                fontSize = 10.5.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.4.sp,
                modifier = Modifier.weight(1f),
            )
            trailing?.let {
                Text(
                    text = it,
                    color = OltreColors.textTertiary,
                    fontFamily = oltreMono(),
                    fontSize = 10.5.sp,
                    maxLines = 1,
                    softWrap = false,
                )
            }
        }
        content()
    }
}

@Composable
private fun GatherCard(
    kind: ResourceKind,
    name: String,
    richness: String,
    hue: Color,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .border(1.dp, if (selected) SELECTED_EDGE else Color.White.copy(alpha = 0.09f), CONTROL_SHAPE)
            .background(if (selected) SELECTED_FILL else Color.Transparent, CONTROL_SHAPE)
            .testTag(GalaxyTestTags.gather(kind))
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 7.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(
            text = name,
            color = hue,
            fontFamily = oltreMono(),
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            text = "richness $richness",
            color = OltreColors.textTertiary,
            fontFamily = oltreMono(),
            fontSize = 10.5.sp,
            maxLines = 1,
            softWrap = false,
        )
    }
}

// 32dp square, which is the size the colony's own steppers are and the size every target in this app
// that is not a full-width button is. Below that is the 44pt iOS minimum failing on contact.
@Composable
private fun Stepper(glyph: String, enabled: Boolean, tag: String, onClick: () -> Unit) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(32.dp)
            .border(1.dp, Color.White.copy(alpha = 0.17f), CONTROL_SHAPE)
            .testTag(tag)
            .clickable(enabled = enabled, onClick = onClick),
    ) {
        Text(
            text = glyph,
            color = if (enabled) OltreColors.text else OltreColors.textTertiary,
            fontFamily = oltreMono(),
            fontSize = 13.5.sp,
        )
    }
}

@Composable
private fun WindowRung(rung: WindowRungUiState, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .height(32.dp)
            .border(1.dp, if (rung.selected) SELECTED_EDGE else Color.White.copy(alpha = 0.09f), CONTROL_SHAPE)
            .background(if (rung.selected) SELECTED_FILL else Color.Transparent, CONTROL_SHAPE)
            .testTag(GalaxyTestTags.window(rung.window.inWholeMinutes))
            .clickable(onClick = onClick),
    ) {
        Text(
            text = rung.label,
            color = if (rung.selected) OltreColors.accent else OltreColors.textSecondary,
            fontFamily = oltreMono(),
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

// The committed idiom, unchanged: accent on a live verb, a hairline ghost on a reading. **There is
// no third state**, because a run costs nothing — see `DispatchUiState`.
@Composable
private fun Verb(label: String, tag: String, primary: Boolean, onClick: () -> Unit) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, if (primary) SELECTED_EDGE else Color.White.copy(alpha = 0.09f), CONTROL_SHAPE)
            .background(if (primary) SELECTED_FILL else Color.Transparent, CONTROL_SHAPE)
            .testTag(tag)
            .clickable(enabled = primary, onClick = onClick)
            .padding(vertical = 11.dp),
    ) {
        Text(
            text = label,
            color = if (primary) OltreColors.accent else OltreColors.textSecondary,
            fontFamily = oltreMono(),
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
private fun Rule() {
    Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(Color.White.copy(alpha = 0.09f)))
}

@Composable
private fun Detail(text: String) {
    Text(
        text = text,
        color = OltreColors.textSecondary,
        fontFamily = oltreMono(),
        fontSize = 10.5.sp,
        lineHeight = 16.sp,
    )
}

// Dark enough that the sheet reads as foreground and light enough that the world list stays legible
// behind it — the row you raised this from is the context, so hiding it entirely would be hiding
// what the sheet is about.
private val SCRIM = Color(0xFF05070D).copy(alpha = 0.72f)

// Accent at 45% and accent at 10%, which is the pair `OltreCardState.RUNNING` already spends on the
// one lit thing on a screen. A selected control is the same claim: this is the one that is on.
private val SELECTED_EDGE = OltreColors.accent.copy(alpha = 0.45f)
private val SELECTED_FILL = OltreColors.accent.copy(alpha = 0.10f)

private val CONTROL_SHAPE = RoundedCornerShape(10.dp)

// Rounded at the top only: the sheet is anchored to the bottom edge and a radius on a corner that is
// off-screen is a radius nobody sees.
private val SHEET_SHAPE = RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp)
