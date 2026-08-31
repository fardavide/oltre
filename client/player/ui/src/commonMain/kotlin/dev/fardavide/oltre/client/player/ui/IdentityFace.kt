package dev.fardavide.oltre.client.player.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.NonRestartableComposable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.fardavide.oltre.client.design.component.PressableFace
import dev.fardavide.oltre.client.design.component.SectionLabel
import dev.fardavide.oltre.client.design.component.oltreActionShape
import dev.fardavide.oltre.client.design.component.oltreCardShape
import dev.fardavide.oltre.client.design.component.oltreCardSurface
import dev.fardavide.oltre.client.design.component.pressable
import dev.fardavide.oltre.client.design.core.OltreColors
import dev.fardavide.oltre.client.design.core.oltreMono
import dev.fardavide.oltre.client.design.core.resolve
import dev.fardavide.oltre.client.design.core.settlingColor
import dev.fardavide.oltre.client.design.text.Strings
import dev.fardavide.oltre.client.design.text.TextRes
import dev.fardavide.oltre.protocol.MarkPreset
import dev.fardavide.oltre.protocol.PlayerMark

// **A name you chose, and a mark you picked** — the face the strip's left cluster opens.
//
// **Header, mark, name, and the order is mechanical rather than aesthetic.** The field is last so that
// when the keyboard rises the sheet takes what is left and the field lands on the keys, with the save
// button in the 44dp between. Reordering this for looks would put the one control that needs the
// keyboard behind it.
//
// Chrome and contents are split for `AlertSheetContent`'s reason, and it is the harder half of that
// reason here: a `ModalBottomSheet` renders into a scene root of its own, so `onRoot()` cannot reach it
// and no camera can photograph it. The composition root raises the one sheet and swaps what is in it.
//
// **`@NonRestartableComposable`, and it changes nothing rather than costing something.** This takes
// four lambdas and a `UiState` holding a `List`, none of which Compose can compare, so it is never
// skipped anyway — the generated skipping machinery is dead by construction, and dead machinery is
// still branches against a coverage gate with no slack.
@Composable
@NonRestartableComposable
fun IdentityFaceContent(
    uiState: IdentityFaceUiState,
    compact: Boolean,
    onChooseMark: (MarkPreset) -> Unit,
    onComposeMark: () -> Unit,
    onNameChange: (String) -> Unit,
    onSaveName: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val held = uiState.requirement != null
    Column(
        modifier = modifier
            .fillMaxWidth()
            .testTag(IdentityTestTags.FACE)
            // It scrolls for `AlertSheetContent`'s reason and one more that is this face's alone: with
            // a keyboard up the sheet is shorter than the face by design, and the thing that must never
            // be clipped is the button that saves what was typed.
            .verticalScroll(rememberScrollState())
            // **9dp of top padding where every other face in this app has none.** The others let the
            // sheet's drag handle be the space above the title; the frame gives a figure here, so the
            // figure is what is drawn. The handle is still above it — this is nine dp more air than the
            // settings face has, not nine dp instead of a handle.
            .padding(start = SIDE, top = TOP, end = SIDE, bottom = SIDE),
        verticalArrangement = Arrangement.spacedBy(BLOCK_GAP),
    ) {
        Text(
            text = Strings.profileTitle().resolve(),
            color = OltreColors.text,
            fontFamily = oltreMono(),
            fontSize = TITLE_SIZE,
            fontWeight = FontWeight.SemiBold,
        )

        // **Above the two things it is about, at full strength.** The sheet still opens with no signal
        // — "a player who taps their own name deserves to see what they would be choosing" — so what
        // held changes is that the controls below go quiet and this says why.
        uiState.requirement?.let { requirement -> RequirementCard(requirement = requirement) }

        Column(verticalArrangement = Arrangement.spacedBy(ITEM_GAP)) {
            SectionLabel(text = Strings.profileMarkLabel())
            // **The locked card's own 42%, applied to the two controls and not to the label.** A label
            // is structure; the grid and the row are the things that cannot commit, and the card above
            // is at full strength saying so. Nothing is red and nothing is greyed-and-unexplained,
            // which is the one state this product does not have.
            Column(
                verticalArrangement = Arrangement.spacedBy(ITEM_GAP),
                modifier = if (held) Modifier.alpha(HELD_DIM) else Modifier,
            ) {
                MarkGrid(cells = uiState.cells, compact = compact, held = held, onChooseMark = onChooseMark)
                Text(
                    text = uiState.markName.resolve(),
                    color = OltreColors.textSecondary,
                    fontFamily = oltreMono(),
                    fontSize = MARK_NAME_SIZE,
                    lineHeight = MARK_NAME_LINE,
                )
                ComposeRow(lit = uiState.composed, held = held, onClick = onComposeMark)
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(ITEM_GAP)) {
            SectionLabel(text = Strings.profileNameLabel())
            NameField(draft = uiState.draft, held = held, onDraftChange = onNameChange)
            // **Present only when there is something to save, and absent rather than disabled.** A
            // greyed `Save name` would be the first disabled control in this product; held takes it
            // away for the same reason nothing-to-save does, because in both cases pressing it could
            // not do anything and a control that cannot act does not ship.
            if (!held && uiState.draft != uiState.committed) {
                SaveButton(onClick = onSaveName)
            }
        }
    }
}

// Six silhouettes at 393dp and three by two at 320, which is the dispatch ladder's move rather than a
// new one: nothing is dropped and nothing is shortened, the row wraps.
@Composable
@NonRestartableComposable
private fun MarkGrid(
    cells: List<MarkCellUiState>,
    compact: Boolean,
    held: Boolean,
    onChooseMark: (MarkPreset) -> Unit,
) {
    val columns = if (compact) COMPACT_COLUMNS else COLUMNS
    Column(verticalArrangement = Arrangement.spacedBy(CELL_GAP)) {
        cells.chunked(columns).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(CELL_GAP), modifier = Modifier.fillMaxWidth()) {
                row.forEach { cell ->
                    MarkCell(
                        cell = cell,
                        held = held,
                        onClick = { onChooseMark(cell.preset) },
                        modifier = Modifier.weight(1f),
                    )
                }
                // A short last row keeps the column pitch instead of stretching to fill it. Six into
                // six and six into three both come out even today; which presets the picker offers is
                // the mapper's to change, and the day it offers five this is what stops the grid
                // silently re-measuring itself.
                repeat(columns - row.size) { Spacer(modifier = Modifier.weight(1f)) }
            }
        }
    }
}

// **Every tap commits**, like every other control in this app — there is no confirm on a picture.
//
// Held draws the cell and takes the press away entirely rather than leaving a target that answers
// nothing: a control that presses and does nothing is the one failure worse than a control that is
// missing, and the card above has already said why this one is quiet.
@Composable
@NonRestartableComposable
private fun MarkCell(cell: MarkCellUiState, held: Boolean, onClick: () -> Unit, modifier: Modifier) {
    val face = Modifier
        .fillMaxWidth()
        .height(CELL_HEIGHT)
        .background(settlingColor(if (cell.chosen) CHOSEN_FILL else oltreCardSurface), oltreActionShape)
        .border(1.dp, settlingColor(if (cell.chosen) CHOSEN_EDGE else CELL_EDGE), oltreActionShape)
    val mark: @Composable () -> Unit = {
        IdentityMark(
            mark = PlayerMark.Preset(cell.preset),
            // Accent on the one you wear and muted on the five you do not, which is how every choice in
            // this app reads. The frame settles the two faces and not the ink inside them.
            color = settlingColor(if (cell.chosen) OltreColors.accent else OltreColors.textSecondary),
            size = CELL_MARK,
        )
    }
    if (held) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = modifier.testTag(IdentityTestTags.cell(cell.preset)).then(face),
            content = { mark() },
        )
    } else {
        PressableFace(
            onClick = onClick,
            shape = oltreActionShape,
            modifier = modifier.testTag(IdentityTestTags.cell(cell.preset)),
            faceModifier = face,
            content = mark,
        )
    }
}

// **A ghost until the account wears a composed mark, and then it is the lit face**, because that is
// where the mark came from and no cell in the grid above can be lit for it. It is the one row on this
// face that opens something rather than committing something, which the settings sheet's own account
// row already establishes as a legible exception.
@Composable
@NonRestartableComposable
private fun ComposeRow(lit: Boolean, held: Boolean, onClick: () -> Unit) {
    val label: @Composable () -> Unit = {
        Text(
            text = Strings.markComposeRow().resolve(),
            color = settlingColor(if (lit) OltreColors.accent else OltreColors.text),
            fontFamily = oltreMono(),
            fontSize = ACTION_SIZE,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            textAlign = TextAlign.Center,
        )
    }
    val face = Modifier
        .fillMaxWidth()
        .height(ROW_HEIGHT)
        .background(settlingColor(if (lit) CHOSEN_FILL else Color.Transparent), oltreActionShape)
        .border(1.dp, settlingColor(if (lit) CHOSEN_EDGE else GHOST_EDGE), oltreActionShape)
    if (held) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.testTag(IdentityTestTags.COMPOSE_ROW).then(face),
            content = { label() },
        )
    } else {
        PressableFace(
            onClick = onClick,
            shape = oltreActionShape,
            modifier = Modifier.fillMaxWidth().testTag(IdentityTestTags.COMPOSE_ROW),
            faceModifier = face,
            content = label,
        )
    }
}

// The app's one verb, at the app's one radius, in the app's one filled treatment. It is the only
// control on this face that does not commit the moment it is touched — a mark is a tap and a name has
// no keystroke that means *done*.
@Composable
@NonRestartableComposable
private fun SaveButton(onClick: () -> Unit) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .fillMaxWidth()
            .testTag(IdentityTestTags.SAVE)
            // Ahead of the fill, which is the ordering every action in this app declares: the layer
            // scales what is drawn inside it, and a background stated first is drawn outside it.
            .pressable(shape = oltreActionShape, onClick = onClick)
            // **The field's own 44dp, and the same number by argument rather than by coincidence**:
            // the frame puts this button in the 44dp between the field and the keys, so a second
            // constant here would be the same measurement written twice.
            .height(FIELD_HEIGHT)
            .background(OltreColors.accent, oltreActionShape),
    ) {
        Text(
            text = Strings.profileSaveName().resolve(),
            // The background colour on a filled accent face, which is what the delete face's own
            // filled button already spends: body ink on a saturated fill is a low-contrast label
            // rather than a button.
            color = OltreColors.background,
            fontFamily = oltreMono(),
            fontSize = ACTION_SIZE,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center,
            maxLines = 1,
        )
    }
}

// **The fleet strip's surface, at the card's radius** — amber 22% around the 6% this app bakes opaque
// as `#141111`. Red is not on offer here and would be wrong twice over: nothing has failed, and the
// thing that is waiting is the network rather than the player.
//
// Two lines in `RefusalBlock`'s grammar with the hue changed: the lead is the fact, the body is what
// follows from it. A refusal is red because nothing was accepted; this is amber because nothing was
// even asked yet.
//
// **Internal because the composer draws the same card**, and the same one rather than a second like
// it: the two faces are one editor with its contents swapped, so a card that drifted on one of them
// would be the sheet saying two things about one fact. The lead is the only part that varies — the
// body is what is true in every state that raises this — and the shell chooses between the two leads
// in `profileRequirement`.
@Composable
@NonRestartableComposable
internal fun RequirementCard(requirement: TextRes) {
    Column(
        verticalArrangement = Arrangement.spacedBy(CARD_LINE_GAP),
        modifier = Modifier
            .fillMaxWidth()
            .testTag(IdentityTestTags.REQUIREMENT)
            .border(1.dp, HELD_CARD_EDGE, oltreCardShape)
            .background(HELD_CARD_FILL, oltreCardShape)
            .padding(CARD_PAD),
    ) {
        Text(
            text = requirement.resolve(),
            color = OltreColors.warn,
            fontFamily = oltreMono(),
            fontSize = CARD_LEAD_SIZE,
            fontWeight = FontWeight.SemiBold,
            lineHeight = CARD_LEAD_LINE,
        )
        Text(
            text = Strings.profileHeldBody().resolve(),
            color = OltreColors.textSecondary,
            fontFamily = oltreMono(),
            fontSize = CARD_BODY_SIZE,
            lineHeight = CARD_BODY_LINE,
        )
    }
}

// The frame's numbers, and the two that are not are marked as such.
private val SIDE = 16.dp
private val TOP = 9.dp
private val BLOCK_GAP = 13.dp

// What separates a control from the thing that belongs to it, and the figure comes out of the frame's
// own arithmetic rather than out of a table: the compose row is given as costing "34dp + a 7dp gap",
// and the save button as the difference between a 307dp face and a 358dp one, which is 44 and this.
private val ITEM_GAP = 7.dp

private val TITLE_SIZE = 15.sp

private val CELL_HEIGHT = 54.dp
private val CELL_GAP = 7.dp
private val CELL_MARK = 24.dp

// Six across at 393dp is a 54.3dp cell; at 320 that would be 41.8 and the grid stacks instead, which
// is three across at 91.3. Both figures are the frame's own and both fall out of the two constants
// above, so neither is written down.
private const val COLUMNS = 6
private const val COMPACT_COLUMNS = 3

private val MARK_NAME_SIZE = 10.5.sp
private val MARK_NAME_LINE = 15.sp

private val ROW_HEIGHT = 34.dp

// 11sp SemiBold is the frame's figure for the save button, and the compose row takes it too: they are
// the same kind of thing said twice on one face, and a ghost that shouted at a different size from the
// filled button below it would read as a different family of control. The frame gives no size for the
// row's label.
private val ACTION_SIZE = 11.sp

private val CARD_PAD = 11.dp
private val CARD_LINE_GAP = 4.dp
private val CARD_LEAD_SIZE = 12.sp
private val CARD_LEAD_LINE = 17.sp
private val CARD_BODY_SIZE = 11.sp
private val CARD_BODY_LINE = 17.sp

// The locked-facility idiom's own dim, which the dispatch sheet's rungs already spend and the frame
// asks for by number. Internal for `RequirementCard`'s reason: the composer is the same editor and
// goes quiet by the same figure.
internal const val HELD_DIM = 0.42f

// Accent 45% over accent 12%, re-derived here because this system has twelve flat colours and no alpha
// tokens at all — `AlertSheet`, `DispatchSheet` and `LedgerHead` each state the same pair at their own
// call sites, and this is the fourth.
private val CHOSEN_EDGE = OltreColors.accent.copy(alpha = 0.45f)
private val CHOSEN_FILL = OltreColors.accent.copy(alpha = 0.12f)

// White 9% around an opaque `#101218`: the card's own resting pair, because an unchosen cell is a card
// with a picture on it. Deliberately not the 16% the ghost row below takes — a row that opens something
// has to read as a control at rest, where a cell in a grid of six reads from the one that is lit.
private val CELL_EDGE = Color.White.copy(alpha = 0.09f)
private val GHOST_EDGE = Color.White.copy(alpha = 0.16f)

private val HELD_CARD_EDGE = OltreColors.warn.copy(alpha = 0.22f)
private val HELD_CARD_FILL = Color(0xFF141111)
