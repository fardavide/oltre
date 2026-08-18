package dev.fardavide.oltre.client.dispatch.ui

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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.fardavide.oltre.client.design.component.OltreBottomSheet
import dev.fardavide.oltre.client.design.core.OltreColors
import dev.fardavide.oltre.client.design.core.oltreMono
import dev.fardavide.oltre.client.design.core.settlingColor
import dev.fardavide.oltre.core.ResourceKind
import kotlin.time.Duration

// **Raised from a world row, and the second of the two sheets a player meets.** Everything else in
// Oltre is a list you scroll: a sheet exists here because a run is the one action with three inputs,
// and three controls inside a 106dp row would be the row becoming a screen anyway.
//
// **It belongs to no tab, which is why it lives in a module of its own.** Galaxy raises it from a
// world row; Fleets raises it from a landing, because the ledger of what came back is where a player
// remembers which world was worth going to. Neither feature may see the other, so the sheet is
// public here rather than internal to whichever tab happened to draw it first.
//
// Bring back, send, home in — in that order, because it is the order of decreasing permanence. What
// your colony is short of changes over days, how many hulls you have changes over hours, and how
// long you will be away changes every check-in. The figure sits under a rule and above the verb, and
// is the only thing on the sheet that moves when a control is touched.
//
// **It was a hand-rolled panel at 0.7.0 and that is what shipped broken** — a Column at the bottom
// of the page's own layout, with a scrim of its own, a shape of its own and a drawn grabber. On a
// device it stopped above the tab bar instead of covering it, a drag on it scrolled the world list
// behind it, and the handle did not drag. `OltreBottomSheet` is the chrome now, exactly as Colony
// and Research have had since the row sheet landed; the sheet in `DebugSheet` made and fixed the
// same mistake at 0.2.6, which is why the shared component exists rather than a fourth copy.
@Composable
fun DispatchSheet(
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
    // There is no cancel button because there is nothing to cancel: the sheet costs nothing to open
    // and commits nothing until the verb is tapped, so an explicit dismiss would be a control whose
    // whole job is to undo an action that has not happened. Every way out — the drag, the scrim, the
    // back gesture — arrives here.
    //
    // **One composable, chrome and contents together**, where `RowSheet` and `DebugSheet` split the
    // two. Their split pays for itself — both are driven contents-first by a test, so the assertions
    // never wait on a popup — and this one's would not: every assertion here drives the real screen,
    // because the coverage table gates each test kind separately and a behaviour test that stops
    // composing `GalaxyPage` stops covering it. Eight parameters restated for no caller is eight
    // parameters to keep in step. Split it the day something needs to render the contents alone.
    OltreBottomSheet(onDismiss = onDismiss, modifier = modifier) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .testTag(DispatchTestTags.SHEET)
                // No top padding: the sheet's own drag handle is the space above the coordinate.
                .padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(13.dp),
        ) {
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
                is DispatchUiState.Waiting -> Waiting(
                    uiState = uiState,
                    compact = compact,
                    onSelectGathering = onSelectGathering,
                    onSelectShips = onSelectShips,
                    onSelectWindow = onSelectWindow,
                )
                is DispatchUiState.Refuse -> Refuse(uiState = uiState, onDispatchProbe = onDispatchProbe)
            }
        }
    }
}

// The name on the left at the size the resource rail spends on a stock, the world on the right in
// the faintest ink on the sheet. It is the sheet answering "which world is this" before it answers
// anything else, and it is **the same name the row that raised it is printing** — which is the whole
// of why the coordinate moved to the right at 0.13.
@Composable
private fun Head(uiState: DispatchUiState, compact: Boolean) {
    val mono = oltreMono()
    Row(verticalAlignment = Alignment.Bottom, modifier = Modifier.fillMaxWidth()) {
        Text(
            text = uiState.name,
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
                deposit = uiState.metalDeposit,
                hue = OltreColors.metal,
                selected = uiState.gathering == ResourceKind.METAL,
                onClick = { onSelectGathering(ResourceKind.METAL) },
                modifier = Modifier.weight(1f),
            )
            GatherCard(
                kind = ResourceKind.CRYSTAL,
                name = "Crystal",
                richness = uiState.crystalRichness,
                deposit = uiState.crystalDeposit,
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
                tag = DispatchTestTags.SHIPS_FEWER,
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
                tag = DispatchTestTags.SHIPS_MORE,
                onClick = { onSelectShips(uiState.shipCount + 1) },
            )
        }
        // "3 skiffs empty it. The 4th brings nothing." Present only when the vein is what stopped the
        // run *and* there is a smaller fleet to send: under the cliff the marginal hull is worth
        // exactly zero and is locked away for the whole window, which is arithmetic rather than a
        // scold. At one hull there is no remedy here and the ladder carries the only one there is.
        uiState.clampNote?.let { note -> Aside(text = note) }
    }
    Control(label = "Home in") {
        Row(horizontalArrangement = Arrangement.spacedBy(5.dp), modifier = Modifier.fillMaxWidth()) {
            uiState.windows.forEach { rung ->
                WindowRung(rung = rung, onClick = { onSelectWindow(rung.window) }, modifier = Modifier.weight(1f))
            }
        }
        // Only when the ladder has actually narrowed. The rung that vanished is the copy — this
        // sentence is for the player who never saw the full ladder and cannot know one is missing.
        uiState.ladderNote?.let { note -> Aside(text = note) }
        // "The 12h window brings the same." The rung is not locked and not greyed — inventing a state
        // for *not better* would be the first disabled control in the app — so the note is the whole
        // of the mechanism, and it is absent when the chosen rung is already the shortest that empties
        // the vein.
        uiState.rungNote?.let { note -> Aside(text = note) }
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
    Verb(label = "Dispatch", tag = DispatchTestTags.SEND, primary = true, onClick = onDispatch)
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
            Verb(label = action.label, tag = DispatchTestTags.SHEET_ACTION, primary = true, onClick = onDispatchProbe)
        // A reading, not a control — the idiom the unaffordable probe already spends. It carries the
        // test tag anyway, so a test can tap it and assert that tapping it does nothing.
        is RefuseActionUiState.Waiting ->
            Verb(label = action.label, tag = DispatchTestTags.SHEET_ACTION, primary = false, onClick = {})
        null -> Unit
    }
}

// **A mode, not a refusal.** The chips, the stepper and the ladder are all still live, and they have
// to be: the wait is a function of the ask, so shrinking the ask is the remedy and the player has to
// be able to reach it without backing out of the sheet. What changes is the block under the rule —
// the figure becomes a title, a sentence and a ghost carrying the time, which is
// `RefuseActionUiState.Waiting`'s shape without the refusal around it.
@Composable
private fun Waiting(
    uiState: DispatchUiState.Waiting,
    compact: Boolean,
    onSelectGathering: (ResourceKind) -> Unit,
    onSelectShips: (Int) -> Unit,
    onSelectWindow: (Duration) -> Unit,
) {
    Control(label = "Bring back") {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            GatherCard(
                kind = ResourceKind.METAL,
                name = "Metal",
                richness = uiState.metalRichness,
                deposit = uiState.metalDeposit,
                hue = OltreColors.metal,
                selected = uiState.gathering == ResourceKind.METAL,
                onClick = { onSelectGathering(ResourceKind.METAL) },
                modifier = Modifier.weight(1f),
            )
            GatherCard(
                kind = ResourceKind.CRYSTAL,
                name = "Crystal",
                richness = uiState.crystalRichness,
                deposit = uiState.crystalDeposit,
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
                tag = DispatchTestTags.SHIPS_FEWER,
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
                tag = DispatchTestTags.SHIPS_MORE,
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
        uiState.ladderNote?.let { note -> Aside(text = note) }
    }
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
    // A reading rather than a control, in the idiom the unaffordable probe already spends — and null
    // when no amount of waiting covers this ask, because a ghost carrying "never" would be worse than
    // the sentence above it, which says to ask for less.
    uiState.wait?.let { label ->
        Verb(label = label, tag = DispatchTestTags.SHEET_ACTION, primary = false, onClick = {})
    }
    Legs(legs = if (compact) uiState.compactLegs else uiState.legs)
    Legs(legs = if (compact) uiState.compactDanger else uiState.danger)
}

// One faint sentence under a control, which is the shape every earned note on this sheet takes —
// the narrowed ladder, the rung that brings the same, and the hull that brings nothing.
@Composable
private fun Aside(text: String) {
    Text(
        text = text,
        color = OltreColors.textTertiary,
        fontFamily = oltreMono(),
        fontSize = 10.5.sp,
        lineHeight = 15.sp,
        modifier = Modifier.padding(top = 2.dp),
    )
}

@Composable
private fun Legs(legs: String) {
    Text(
        text = legs,
        color = OltreColors.textTertiary,
        fontFamily = oltreMono(),
        fontSize = 10.5.sp,
        lineHeight = 15.sp,
    )
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
    deposit: String,
    hue: Color,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .border(
                1.dp,
                settlingColor(if (selected) SELECTED_EDGE else Color.White.copy(alpha = 0.09f)),
                CONTROL_SHAPE,
            )
            .background(settlingColor(if (selected) SELECTED_FILL else Color.Transparent), CONTROL_SHAPE)
            .testTag(DispatchTestTags.gather(kind))
            // Every control on this sheet clips before it clicks: an indication is clipped by the
            // layer declared ahead of it, and all four of these are drawn with `CONTROL_SHAPE`.
            .clip(CONTROL_SHAPE)
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
        // The stock under the richness rather than beside it: two readings of one world, and the
        // chip is the only place they sit together now that the row carries the stocks alone.
        Text(
            text = deposit,
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
//
// **A finger left on it keeps stepping** — Davide, 2026-08-17: *"going from 55 to 3 is a lot of
// taps"*. The suggested manifest is the other half of that call and makes the walk short in the
// common case; `steppingWhileHeld` is what stops the uncommon one being fifty taps, and it is a file
// of its own because nothing in it draws.
@Composable
private fun Stepper(glyph: String, enabled: Boolean, tag: String, onClick: () -> Unit) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(32.dp)
            .border(1.dp, Color.White.copy(alpha = 0.17f), CONTROL_SHAPE)
            .testTag(tag)
            // Ahead of the hold gesture rather than after it, because `steppingWhileHeld` ends in a
            // `clickable` of its own: an indication is clipped by the layer declared before it, so
            // this is what keeps a held repeat from painting a square across the stepper's corners.
            .clip(CONTROL_SHAPE)
            .steppingWhileHeld(enabled = enabled, onStep = onClick),
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
            .border(
                1.dp,
                settlingColor(if (rung.selected) SELECTED_EDGE else Color.White.copy(alpha = 0.09f)),
                CONTROL_SHAPE,
            )
            .background(settlingColor(if (rung.selected) SELECTED_FILL else Color.Transparent), CONTROL_SHAPE)
            .testTag(DispatchTestTags.window(rung.window.inWholeMinutes))
            .clip(CONTROL_SHAPE)
            .clickable(onClick = onClick),
    ) {
        Text(
            text = rung.label,
            color = settlingColor(if (rung.selected) OltreColors.accent else OltreColors.textSecondary),
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
            .border(
                1.dp,
                settlingColor(if (primary) SELECTED_EDGE else Color.White.copy(alpha = 0.09f)),
                CONTROL_SHAPE,
            )
            .background(settlingColor(if (primary) SELECTED_FILL else Color.Transparent), CONTROL_SHAPE)
            .testTag(tag)
            .clip(CONTROL_SHAPE)
            .clickable(enabled = primary, onClick = onClick)
            .padding(vertical = 11.dp),
    ) {
        Text(
            text = label,
            color = settlingColor(if (primary) OltreColors.accent else OltreColors.textSecondary),
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

// Accent at 45% and accent at 10%, which is the pair `OltreCardState.RUNNING` already spends on the
// one lit thing on a screen. A selected control is the same claim: this is the one that is on.
private val SELECTED_EDGE = OltreColors.accent.copy(alpha = 0.45f)
private val SELECTED_FILL = OltreColors.accent.copy(alpha = 0.10f)

private val CONTROL_SHAPE = RoundedCornerShape(10.dp)
