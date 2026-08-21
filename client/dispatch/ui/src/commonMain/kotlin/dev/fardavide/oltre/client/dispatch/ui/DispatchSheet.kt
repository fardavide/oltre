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
import androidx.compose.ui.draw.alpha
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
import dev.fardavide.oltre.client.design.core.resolve
import dev.fardavide.oltre.client.design.core.settlingColor
import dev.fardavide.oltre.client.design.text.Strings
import dev.fardavide.oltre.client.design.text.TextRes
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
            text = uiState.name.resolve(),
            color = OltreColors.text,
            fontFamily = mono,
            fontSize = 15.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            softWrap = false,
        )
        Text(
            text = (if (compact) uiState.compactHead else uiState.head).resolve(),
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
    Control(label = Strings.controlBringBack()) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            // Two, and never three. Deuterium is the Robotics gate's currency and a fleet that could
            // fetch it would undercut the one requirement the whole mid-game hangs on — so it is not
            // a disabled third card, it is not a card.
            GatherCard(
                kind = ResourceKind.METAL,
                name = Strings.resourceTitle(ResourceKind.METAL),
                richness = uiState.metalRichness,
                deposit = uiState.metalDeposit,
                hue = OltreColors.metal,
                selected = uiState.gathering == ResourceKind.METAL,
                onClick = { onSelectGathering(ResourceKind.METAL) },
                modifier = Modifier.weight(1f),
            )
            GatherCard(
                kind = ResourceKind.CRYSTAL,
                name = Strings.resourceTitle(ResourceKind.CRYSTAL),
                richness = uiState.crystalRichness,
                deposit = uiState.crystalDeposit,
                hue = OltreColors.crystal,
                selected = uiState.gathering == ResourceKind.CRYSTAL,
                onClick = { onSelectGathering(ResourceKind.CRYSTAL) },
                modifier = Modifier.weight(1f),
            )
        }
    }
    Control(label = Strings.controlSend(), trailing = uiState.pool) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Stepper(
                glyph = Strings.stepperFewer(),
                enabled = uiState.fewer != null,
                tag = DispatchTestTags.SHIPS_FEWER,
                onClick = { uiState.fewer?.let(onSelectShips) },
            )
            Text(
                text = uiState.ships.resolve(),
                color = OltreColors.text,
                fontFamily = oltreMono(),
                fontSize = 13.5.sp,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
                modifier = Modifier.weight(1f),
            )
            Stepper(
                glyph = Strings.stepperMore(),
                enabled = uiState.more != null,
                tag = DispatchTestTags.SHIPS_MORE,
                onClick = { uiState.more?.let(onSelectShips) },
            )
        }
        // "3 skiffs empty it. The 4th brings nothing." Present only when the vein is what stopped the
        // run *and* there is a smaller fleet to send: under the cliff the marginal hull is worth
        // exactly zero and is locked away for the whole window, which is arithmetic rather than a
        // scold. At one hull there is no remedy here and the ladder carries the only one there is.
        // **Two cells, and they are absent rather than disabled when only one clock is available** —
        // a control with one option is not a control. That is 0.13.1 unchanged, and it is most
        // sheets: every one before the hauler is bought, and every one where it is already in the sky.
        if (uiState.hullCells.isNotEmpty()) {
            Row(horizontalArrangement = Arrangement.spacedBy(5.dp), modifier = Modifier.fillMaxWidth()) {
                uiState.hullCells.forEach { cell ->
                    HullCell(
                        cell = cell,
                        onClick = { onSelectShips(cell.berths) },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
        // One slot below the cells, one job: what the *other* cell would do. Where the clamp would
        // also be earned the clamp wins, because it is about the run being sent rather than one that
        // is not — so these two are one slot and never two.
        uiState.cellNote?.let { note -> Aside(text = note) }
        uiState.clampNote?.let { note -> Aside(text = note) }
    }
    Control(label = Strings.controlHomeIn()) {
        Row(horizontalArrangement = Arrangement.spacedBy(5.dp), modifier = Modifier.fillMaxWidth()) {
            uiState.windows.forEach { rung ->
                WindowRung(rung = rung, onClick = { onSelectWindow(rung.window) }, modifier = Modifier.weight(1f))
            }
        }
        // Only when the ladder has actually narrowed. The rung that vanished is the copy — this
        // sentence is for the player who never saw the full ladder and cannot know one is missing.
        uiState.ladderNote?.let { note -> Aside(text = note.label, emphasised = note.emphasised) }
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
                text = uiState.figure.resolve(),
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
                    text = each.resolve(),
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
    Verb(label = Strings.dispatchVerb(), tag = DispatchTestTags.SEND, primary = true, onClick = onDispatch)
}

@Composable
private fun Refuse(uiState: DispatchUiState.Refuse, onDispatchProbe: () -> Unit) {
    Rule()
    Text(
        text = uiState.title.resolve(),
        color = OltreColors.text,
        fontFamily = oltreMono(),
        fontSize = 13.5.sp,
        lineHeight = 18.sp,
        fontWeight = FontWeight.SemiBold,
    )
    Text(
        text = uiState.note.resolve(),
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
    Control(label = Strings.controlBringBack()) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            GatherCard(
                kind = ResourceKind.METAL,
                name = Strings.resourceTitle(ResourceKind.METAL),
                richness = uiState.metalRichness,
                deposit = uiState.metalDeposit,
                hue = OltreColors.metal,
                selected = uiState.gathering == ResourceKind.METAL,
                onClick = { onSelectGathering(ResourceKind.METAL) },
                modifier = Modifier.weight(1f),
            )
            GatherCard(
                kind = ResourceKind.CRYSTAL,
                name = Strings.resourceTitle(ResourceKind.CRYSTAL),
                richness = uiState.crystalRichness,
                deposit = uiState.crystalDeposit,
                hue = OltreColors.crystal,
                selected = uiState.gathering == ResourceKind.CRYSTAL,
                onClick = { onSelectGathering(ResourceKind.CRYSTAL) },
                modifier = Modifier.weight(1f),
            )
        }
    }
    Control(label = Strings.controlSend(), trailing = uiState.pool) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Stepper(
                glyph = Strings.stepperFewer(),
                enabled = uiState.fewer != null,
                tag = DispatchTestTags.SHIPS_FEWER,
                onClick = { uiState.fewer?.let(onSelectShips) },
            )
            Text(
                text = uiState.ships.resolve(),
                color = OltreColors.text,
                fontFamily = oltreMono(),
                fontSize = 13.5.sp,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
                modifier = Modifier.weight(1f),
            )
            Stepper(
                glyph = Strings.stepperMore(),
                enabled = uiState.more != null,
                tag = DispatchTestTags.SHIPS_MORE,
                onClick = { uiState.more?.let(onSelectShips) },
            )
        }
    }
    Control(label = Strings.controlHomeIn()) {
        Row(horizontalArrangement = Arrangement.spacedBy(5.dp), modifier = Modifier.fillMaxWidth()) {
            uiState.windows.forEach { rung ->
                WindowRung(rung = rung, onClick = { onSelectWindow(rung.window) }, modifier = Modifier.weight(1f))
            }
        }
        uiState.ladderNote?.let { note -> Aside(text = note.label, emphasised = note.emphasised) }
    }
    Rule()
    Text(
        text = uiState.title.resolve(),
        color = OltreColors.text,
        fontFamily = oltreMono(),
        fontSize = 13.5.sp,
        lineHeight = 18.sp,
        fontWeight = FontWeight.SemiBold,
    )
    Text(
        text = uiState.note.resolve(),
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
//
// **The weight is the whole announcement** — Design: *"muted states a rule that was already true,
// body states something that just changed."* No animation, no toast, no highlight: the app has none
// of those, and a moved selection does not earn the first.
@Composable
private fun Aside(text: TextRes, emphasised: Boolean = false) {
    Text(
        text = text.resolve(),
        color = if (emphasised) OltreColors.textSecondary else OltreColors.textTertiary,
        fontFamily = oltreMono(),
        fontSize = 10.5.sp,
        lineHeight = 15.sp,
        modifier = Modifier.padding(top = 2.dp),
    )
}

@Composable
private fun Legs(legs: TextRes) {
    Text(
        text = legs.resolve(),
        color = OltreColors.textTertiary,
        fontFamily = oltreMono(),
        fontSize = 10.5.sp,
        lineHeight = 15.sp,
    )
}

@Composable
private fun Control(label: TextRes, trailing: TextRes? = null, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(7.dp), modifier = Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.Bottom, modifier = Modifier.fillMaxWidth()) {
            Text(
                text = label.resolve(),
                color = OltreColors.textTertiary,
                fontFamily = oltreMono(),
                fontSize = 10.5.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.4.sp,
                modifier = Modifier.weight(1f),
            )
            trailing?.let {
                Text(
                    text = it.resolve(),
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
    name: TextRes,
    richness: TextRes,
    deposit: TextRes,
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
            text = name.resolve(),
            color = hue,
            fontFamily = oltreMono(),
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            text = Strings.richness(richness).resolve(),
            color = OltreColors.textTertiary,
            fontFamily = oltreMono(),
            fontSize = 10.5.sp,
            maxLines = 1,
            softWrap = false,
        )
        // The stock under the richness rather than beside it: two readings of one world, and the
        // chip is the only place they sit together now that the row carries the stocks alone.
        Text(
            text = deposit.resolve(),
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
private fun Stepper(glyph: TextRes, enabled: Boolean, tag: String, onClick: () -> Unit) {
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
            text = glyph.resolve(),
            color = if (enabled) OltreColors.text else OltreColors.textTertiary,
            fontFamily = oltreMono(),
            fontSize = 13.5.sp,
        )
    }
}

// **Four states, and the locked one is the only tap that puts a hull back in the hangar.** Open is a
// hairline; selected is accent at 45% over a 10% fill; locked is the whole cell at 42% with the hull
// that would fly it on a second line; absent is never rendered at all, which is the ladder's own way
// of teaching distance and is the mapper's business rather than this composable's.
//
// **The locked cell carries no colour.** Red is short of exactly that resource and amber is in
// transit, and a rung is neither — so it dims, exactly as a locked facility row does. It is 38dp
// where the others are 32dp, which is Design's number and is what the second line costs.
@Composable
private fun WindowRung(rung: WindowRungUiState, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val locked = rung.requirement != null
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = modifier
            .height(if (locked) 38.dp else 32.dp)
            .border(
                1.dp,
                settlingColor(if (rung.selected) SELECTED_EDGE else Color.White.copy(alpha = 0.09f)),
                CONTROL_SHAPE,
            )
            .background(settlingColor(if (rung.selected) SELECTED_FILL else Color.Transparent), CONTROL_SHAPE)
            .testTag(DispatchTestTags.window(rung.window.inWholeMinutes))
            .clip(CONTROL_SHAPE)
            // **Locked is clickable, and that is the point rather than an oversight.** It is the undo:
            // one tap takes the hauler out and selects the rung. A disabled control here would be the
            // first in the app and would strand the player on a mix they cannot back out of.
            .clickable(onClick = onClick)
            .alpha(if (locked) LOCKED_ALPHA else 1f),
    ) {
        Text(
            text = rung.label.resolve(),
            color = settlingColor(if (rung.selected) OltreColors.accent else OltreColors.textSecondary),
            fontFamily = oltreMono(),
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            lineHeight = 14.sp,
        )
        rung.requirement?.let { requirement ->
            Text(
                text = requirement.resolve(),
                color = OltreColors.textTertiary,
                fontFamily = oltreMono(),
                fontSize = 9.5.sp,
                // Explicit and tight, on both lines: the default leading on a 11sp and a 9.5sp line
                // overflows a 38dp box and clips the requirement to a sliver. Design's 38dp is the
                // number; this is what makes two lines fit inside it.
                lineHeight = 12.sp,
            )
        }
    }
}

// The locked-facility idiom's own dim, shared so a rung and a row cannot drift apart.
private const val LOCKED_ALPHA: Float = 0.42f

// One of the two clocks a manifest can fly on. The window ladder's idiom at two rungs wide, because
// it is the same kind of choice — and it names its hulls, which is why the row needs no label.
@Composable
private fun HullCell(cell: HullCellUiState, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = modifier
            .height(38.dp)
            .border(
                1.dp,
                settlingColor(if (cell.selected) SELECTED_EDGE else Color.White.copy(alpha = 0.09f)),
                CONTROL_SHAPE,
            )
            .background(settlingColor(if (cell.selected) SELECTED_FILL else Color.Transparent), CONTROL_SHAPE)
            .testTag(DispatchTestTags.hullCell(cell.label))
            .clip(CONTROL_SHAPE)
            .clickable(onClick = onClick)
            // **3dp rather than Design's 5dp, and 10sp rather than 11.** Measured rather than
            // chosen: at 320dp a cell has ~120dp of room and "1 hauler · 2 skiffs" is nineteen
            // characters, which Compose lays out wider than the design's own HTML did — its note
            // budgets *"131dp of room for 125dp of type"* and the real figure is over that. The copy
            // is untouched, which is the part that matters: Design's rule is that nothing is reduced
            // at 320dp and nothing invented, and a label that truncated to "1 hauler · 2" would read
            // as a different manifest.
            .padding(horizontal = 3.dp),
    ) {
        Text(
            text = cell.label.resolve(),
            color = settlingColor(if (cell.selected) OltreColors.accent else OltreColors.textSecondary),
            fontFamily = oltreMono(),
            fontSize = 10.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            lineHeight = 14.sp,
        )
        Text(
            text = cell.trip.resolve(),
            color = OltreColors.textTertiary,
            fontFamily = oltreMono(),
            fontSize = 9.5.sp,
            maxLines = 1,
            lineHeight = 12.sp,
        )
    }
}

// The committed idiom, unchanged: accent on a live verb, a hairline ghost on a reading. **There is
// no third state**, because a run costs nothing — see `DispatchUiState`.
@Composable
private fun Verb(label: TextRes, tag: String, primary: Boolean, onClick: () -> Unit) {
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
            text = label.resolve(),
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
private fun Detail(text: TextRes) {
    Text(
        text = text.resolve(),
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
