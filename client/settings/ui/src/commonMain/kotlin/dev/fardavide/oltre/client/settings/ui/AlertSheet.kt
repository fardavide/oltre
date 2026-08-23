package dev.fardavide.oltre.client.settings.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.NonRestartableComposable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.fardavide.oltre.client.design.component.OltreBottomSheet
import dev.fardavide.oltre.client.design.component.PressableFace
import dev.fardavide.oltre.client.design.component.SectionLabel
import dev.fardavide.oltre.client.design.component.oltreCardShape
import dev.fardavide.oltre.client.design.component.oltreCardSurface
import dev.fardavide.oltre.client.design.core.OltreColors
import dev.fardavide.oltre.client.design.core.oltreMono
import dev.fardavide.oltre.client.design.core.resolve
import dev.fardavide.oltre.client.design.core.settlingColor
import dev.fardavide.oltre.client.design.icon.WatchBell
import dev.fardavide.oltre.client.design.text.TextRes
import dev.fardavide.oltre.core.AlertCategory
import dev.fardavide.oltre.core.AlertDelivery
import dev.fardavide.oltre.core.AlertMode

// **The app's third modal sheet, and the argument for it is that it is not a new surface.** The row
// sheet and the dispatch sheet are both `ModalBottomSheet`s and both shipped; a settings panel is the
// same shape, and a sheet is the only shape that needs no navigation stack — which this app does not
// have and would have to invent a back control for. The design weighs a full destination and a sixth
// tab in §1 and takes neither. *Ask Once*, accepted 2026-08-23; `.claude/docs/ask-once-sheet.md` is
// what every "the design" in this module refers to.
//
// **There is no Save, no Done and no X.** Every control here commits on tap, like every other control
// in the app, so the ways out are the handle, the frame above, the gear again, and the platform's own
// back — all four of which arrive at `onDismiss`.
//
// Chrome and contents are split, unlike the dispatch sheet: the contents are what a screenshot test
// renders and what the behaviour tests drive, so they must not depend on a popup being reachable from
// a test tree or on an enter animation having settled. `RowSheet` and `DebugSheet` are the precedent.
//
// **`@NonRestartableComposable`, and it pays for itself twice.** This function reads nothing and
// forwards six arguments, so a restart scope of its own could never do anything the caller's scope
// does not already do — `DispatchSheet`'s `Committing` makes the same argument, and without the
// annotation Compose generates the skipping machinery anyway. The second half is measured: a
// `ModalBottomSheet` renders into a scene root of its own, so no screenshot test can reach this
// through `onRoot()` at all, and twenty generated branches nothing can execute is twenty branches
// against the screenshot column of a gate that has no slack.
@Composable
@NonRestartableComposable
fun AlertSheet(
    uiState: AlertSheetUiState,
    compact: Boolean,
    onDismiss: () -> Unit,
    onSelectMode: (AlertMode) -> Unit,
    onToggleCategory: (AlertCategory) -> Unit,
    onSelectDelivery: (AlertDelivery) -> Unit,
    modifier: Modifier = Modifier,
) {
    OltreBottomSheet(onDismiss = onDismiss, modifier = modifier) {
        AlertSheetContent(
            uiState = uiState,
            compact = compact,
            onSelectMode = onSelectMode,
            onToggleCategory = onToggleCategory,
            onSelectDelivery = onSelectDelivery,
        )
    }
}

// Two settings, in the order they are decided: where the question is asked, then how many
// notifications the answers arrive in. The panel of seven belongs to the *ladder* rather than to the
// chip that owns it, so it sits under the ladder and the ladder never moves — choosing the other stop
// does not displace the chip you did not choose.
//
// **`@NonRestartableComposable`, and here it changes nothing rather than costing something.** This
// one is not trivial, unlike the four helpers below — but it takes three lambdas and a `UiState`
// whose fields are `List`s, none of which Compose can compare, so it is **never skipped anyway**. The
// generated skipping machinery is dead by construction, and dead machinery is still branches: twelve
// of them, against a coverage gate with no slack. `DispatchSheet`'s `Committing` makes the first half
// of this argument; the second half is what the gate measured.
@Composable
@NonRestartableComposable
fun AlertSheetContent(
    uiState: AlertSheetUiState,
    compact: Boolean,
    onSelectMode: (AlertMode) -> Unit,
    onToggleCategory: (AlertCategory) -> Unit,
    onSelectDelivery: (AlertDelivery) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .testTag(SettingsTestTags.SHEET)
            // **It scrolls, and the design asked for that decision to be taken now rather than when
            // it overflows.** By its own arithmetic the sheet fits at both widths it was drawn for —
            // 573dp at 393, clearing the resource rail by 94; and 648 of the 652 a Slide Over pane
            // has — so this does nothing today at either. What it stops is the thing four dp of
            // headroom cannot survive: a second section, a longer translation, a desktop window
            // dragged short. A `ModalBottomSheet` clips what does not fit, and the last control here
            // is the one that says how many notifications you get.
            .verticalScroll(rememberScrollState())
            // No top padding: the sheet's own drag handle is the space above the title.
            .padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(SECTION_GAP),
    ) {
        Text(
            text = uiState.title.resolve(),
            color = OltreColors.text,
            fontFamily = oltreMono(),
            fontSize = 15.sp,
            fontWeight = FontWeight.SemiBold,
        )

        Column(verticalArrangement = Arrangement.spacedBy(NOTE_GAP)) {
            SectionLabel(text = uiState.alertsLabel)
            // Two stops always fit, at either width: eleven characters each in English and thirteen
            // at the longest in Italian, against the 96dp a chip gets at 320dp. Only the three-stop
            // ladder below has to stack.
            Row(horizontalArrangement = Arrangement.spacedBy(CHIP_GAP)) {
                for (step in uiState.modes) {
                    LadderChip(
                        label = step.label,
                        selected = step.selected,
                        tag = SettingsTestTags.mode(step.mode),
                        onClick = { onSelectMode(step.mode) },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
            Note(text = uiState.modeNote)
        }

        // **Absent rather than collapsed.** The panel does not belong to a chip, it belongs to the
        // option — so when that option is not chosen there is nothing here at all, and the ladder
        // above has not moved.
        uiState.categories?.let { rows ->
            Column(
                modifier = Modifier
                    .testTag(SettingsTestTags.PANEL)
                    .border(1.dp, PANEL_EDGE, oltreCardShape)
                    .background(oltreCardSurface, oltreCardShape)
                    .padding(horizontal = 12.dp),
            ) {
                // **One card with hairlines, not seven cards.** A card per switch would read as seven
                // decisions; this is one decision taken seven times.
                rows.forEachIndexed { index, row ->
                    CategoryRow(
                        row = row,
                        first = index == 0,
                        onToggle = { onToggleCategory(row.category) },
                    )
                }
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(NOTE_GAP)) {
            SectionLabel(text = uiState.deliveryLabel)
            // **288dp of content will not hold three chips**, so at the narrow width the ladder
            // stacks — the same move the colony's watch square makes rather than clipping a name.
            // Nothing is shortened and nothing is dropped, which is the design's rule at 320dp.
            if (compact) {
                Column(verticalArrangement = Arrangement.spacedBy(CHIP_GAP)) {
                    for (step in uiState.deliveries) DeliveryChip(step = step, onSelectDelivery = onSelectDelivery)
                }
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(CHIP_GAP)) {
                    for (step in uiState.deliveries) {
                        DeliveryChip(
                            step = step,
                            onSelectDelivery = onSelectDelivery,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
            // Not a sentence explaining grouping, but the line the phone would actually print. The
            // names are parallel enough to carry the meaning by themselves; the example carries the
            // shape; the line under it carries the only thing nobody can guess, which is *when*.
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                WatchBell(color = OltreColors.textTertiary, size = 13.dp, modifier = Modifier.padding(top = 1.dp))
                Text(
                    text = uiState.example.resolve(),
                    color = OltreColors.text,
                    fontFamily = oltreMono(),
                    fontSize = NOTE_SIZE,
                    lineHeight = NOTE_LINE,
                    modifier = Modifier.testTag(SettingsTestTags.EXAMPLE),
                )
            }
            uiState.timing?.let { Note(text = it, tag = SettingsTestTags.TIMING) }
        }
    }
}

// The four helpers below are all `@NonRestartableComposable` for one reason, stated once here: each
// reads nothing of its own and forwards what it is handed, so a restart scope could never do anything
// its caller's does not already do — and Compose generates one regardless. That is `Committing`'s
// argument on the dispatch sheet, and it applies four times over on a sheet whose every value arrives
// from a mapper.
@Composable
@NonRestartableComposable
private fun DeliveryChip(
    step: AlertDeliveryStep,
    onSelectDelivery: (AlertDelivery) -> Unit,
    modifier: Modifier = Modifier,
) {
    LadderChip(
        label = step.label,
        selected = step.selected,
        tag = SettingsTestTags.delivery(step.delivery),
        onClick = { onSelectDelivery(step.delivery) },
        modifier = modifier,
    )
}

// **The dispatch sheet's `Home in` rung with the locked state removed**, which is the whole of what a
// two- or three-way ladder is here: accent border at 45% over a 12% fill and accent ink when chosen,
// a hairline and muted ink when not. Nothing on this sheet can be locked — every stop is always a
// legal answer — so the second line and the dim that rung carries have no case here.
//
// Not lifted into `:client:design:component` on the shared-surface test's own terms: two callers do
// not justify sharing, and the two are not the same control anyway. The day a third ladder lands,
// this and `WindowRung` go down together.
@Composable
@NonRestartableComposable
private fun LadderChip(
    label: TextRes,
    selected: Boolean,
    tag: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    PressableFace(
        onClick = onClick,
        shape = CHIP_SHAPE,
        modifier = modifier.testTag(tag).heightIn(min = CHIP_HEIGHT),
        faceModifier = Modifier
            .fillMaxWidth()
            .heightIn(min = CHIP_HEIGHT)
            .background(settlingColor(if (selected) SELECTED_FILL else Color.Transparent), CHIP_SHAPE)
            .border(1.dp, settlingColor(if (selected) SELECTED_EDGE else HAIRLINE), CHIP_SHAPE)
            // **3dp and 10sp rather than the rung's 6 and 11, and it is measured rather than
            // chosen** — the same finding `HullCell` records one sheet along. At 393dp a chip in the
            // three-stop ladder has about 115dp of room, and `One per category` is sixteen characters
            // that Compose lays out wider than the design's own HTML did: the first cut of this
            // shipped a chip reading `One per`, which is a different setting rather than a clipped
            // word. The copy is untouched, which is the part that matters — Design's rule is that
            // nothing is reduced and nothing invented.
            .padding(horizontal = CHIP_PADDING),
    ) {
        Text(
            text = label.resolve(),
            color = settlingColor(if (selected) OltreColors.accent else OltreColors.textSecondary),
            fontFamily = oltreMono(),
            fontSize = CHIP_SIZE,
            fontWeight = FontWeight.SemiBold,
            lineHeight = 14.sp,
            maxLines = 1,
            textAlign = TextAlign.Center,
        )
    }
}

// **One of the seven bells, and it is the colony's own 29dp square unchanged** — the same glyph, the
// same lit/unlit pair, the same meaning, asked once per category instead of once per row. A player
// who has ever tapped one has read the whole panel.
//
// The row is the target and it is 38dp tall, so the square never has to carry a 44dp hit area on its
// own. That is `WatchSquare`'s own argument in the case where the row is taller than the square, and
// it is why the tag and the press are on the row.
@Composable
@NonRestartableComposable
private fun CategoryRow(row: AlertCategoryRow, first: Boolean, onToggle: () -> Unit) {
    val spoken = row.spoken.resolve()
    PressableFace(
        onClick = onToggle,
        shape = ROW_SHAPE,
        modifier = Modifier
            .fillMaxWidth()
            .testTag(SettingsTestTags.category(row.category))
            .semantics { contentDescription = spoken },
        faceModifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            // A hairline between rows and none above the first — the panel's own edge is already
            // there, and a second line on top of it would read as a header nobody wrote.
            if (!first) Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(HAIRLINE))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxWidth().heightIn(min = ROW_HEIGHT).padding(vertical = 5.dp),
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    Text(
                        text = row.label.resolve(),
                        color = OltreColors.text,
                        fontFamily = oltreMono(),
                        fontSize = 12.sp,
                    )
                    row.note?.let { note ->
                        Text(
                            text = note.resolve(),
                            color = OltreColors.textTertiary,
                            fontFamily = oltreMono(),
                            fontSize = NOTE_SIZE,
                            lineHeight = NOTE_LINE,
                        )
                    }
                }
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                    modifier = Modifier
                        .size(SQUARE)
                        .background(
                            settlingColor(if (row.on) SELECTED_FILL else Color.Transparent),
                            SQUARE_SHAPE,
                        )
                        .border(1.dp, settlingColor(if (row.on) SELECTED_EDGE else HAIRLINE), SQUARE_SHAPE),
                ) {
                    WatchBell(color = settlingColor(if (row.on) OltreColors.accent else OltreColors.textTertiary))
                }
            }
        }
    }
}

// The muted line under a control, in the app's own note voice. Two callers with the same three
// properties, which is what makes it a function rather than a repeated `Text`.
@Composable
@NonRestartableComposable
private fun Note(text: TextRes, tag: String? = null) {
    Text(
        text = text.resolve(),
        color = OltreColors.textSecondary,
        fontFamily = oltreMono(),
        fontSize = NOTE_SIZE,
        lineHeight = NOTE_LINE,
        modifier = if (tag == null) Modifier else Modifier.testTag(tag),
    )
}

// The design's numbers. `SECTION_GAP` is what separates two settings, `NOTE_GAP` what separates a
// control from the line explaining it — the second is deliberately much tighter, so a note reads as
// belonging to the control above it rather than as a third thing.
private val SECTION_GAP = 18.dp
private val NOTE_GAP = 7.dp
private val CHIP_GAP = 8.dp
private val CHIP_HEIGHT = 34.dp
private val CHIP_PADDING = 3.dp
private val CHIP_SIZE = 10.sp
private val ROW_HEIGHT = 38.dp
private val SQUARE = 29.dp

private val CHIP_SHAPE = RoundedCornerShape(10.dp)
private val SQUARE_SHAPE = RoundedCornerShape(9.dp)

// A row has no corners of its own: it is a slice of the one card, separated from its neighbours by a
// hairline rather than by a gap. The shape is what the press clips to, and here that is the slice.
private val ROW_SHAPE = RectangleShape

private val SELECTED_EDGE = OltreColors.accent.copy(alpha = 0.45f)
private val SELECTED_FILL = OltreColors.accent.copy(alpha = 0.12f)

// The 16% white the watch square's unlit border already uses, so a bell in the panel and a bell on a
// row are the same drawing rather than two that happen to look alike.
private val HAIRLINE = Color.White.copy(alpha = 0.16f)

private val NOTE_SIZE = 11.sp
private val NOTE_LINE = 16.sp

private val PANEL_EDGE = Color.White.copy(alpha = 0.09f)
