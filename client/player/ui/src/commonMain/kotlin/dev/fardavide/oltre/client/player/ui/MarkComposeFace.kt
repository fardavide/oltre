package dev.fardavide.oltre.client.player.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.fardavide.oltre.client.design.component.PressableFace
import dev.fardavide.oltre.client.design.component.SectionLabel
import dev.fardavide.oltre.client.design.component.oltreActionShape
import dev.fardavide.oltre.client.design.component.oltreCardShape
import dev.fardavide.oltre.client.design.component.oltreCardSurface
import dev.fardavide.oltre.client.design.core.OltreColors
import dev.fardavide.oltre.client.design.core.oltreMono
import dev.fardavide.oltre.client.design.core.resolve
import dev.fardavide.oltre.client.design.core.settlingColor
import dev.fardavide.oltre.client.design.text.Strings
import dev.fardavide.oltre.client.design.text.TextRes
import dev.fardavide.oltre.protocol.MarkBody
import dev.fardavide.oltre.protocol.MarkPath
import dev.fardavide.oltre.protocol.MarkTerminus
import dev.fardavide.oltre.protocol.PlayerMark

// **Forty marks out of eleven drawings**, as three ladders and the thing they make. Reached from the
// identity face's compose row, and it replaces that face in the sheet rather than sitting over it —
// the composition root swaps what is in the one sheet, which is *A Sky Per Build* §4's refusal of a
// sheet over a sheet.
//
// **There is no way back to the name field, and that is the design rather than an omission.** The way
// out is the handle or the frame, like every other face in this app: no Done, no back, and no control
// whose only job is to undo having tapped something.
//
// **Every chip draws the whole mark with its own slot swapped in**, at the same 24dp the grid uses.
// The reason is the frame's: a path on its own is four pixels of stroke and says nothing, so a ladder
// of bare parts would be four chips a player cannot tell apart. What each chip *is* called is carried
// as its spoken description — the words themselves are on the card above, where the mark they make is.
//
// **No `compact`.** Nothing on this face is measured differently at 320dp: the ladders stay four and
// three across, because stacking them would break the one-row-per-slot reading that makes three
// ladders legible at all, and the only thing that changes is the tuple line wrapping. A `compact`
// parameter here would be a knob nothing turns.
//
// **Held is the identity face's, mirrored rather than reinvented.** Every chip here commits on tap
// exactly as a cell in the grid does, so the state where a tap cannot leave the phone has to read the
// same on both: the requirement card above at full strength, the card and the three ladders at 42%,
// and the presses taken away rather than left to answer nothing. This face shipped without any of it,
// which made eleven controls that a player could press and perceive no answer from at all.
@Composable
@NonRestartableComposable
fun MarkComposeFaceContent(
    uiState: MarkComposeFaceUiState,
    onChooseBody: (MarkBody) -> Unit,
    onChoosePath: (MarkPath) -> Unit,
    onChooseTerminus: (MarkTerminus) -> Unit,
    modifier: Modifier = Modifier,
) {
    val held = uiState.requirement != null
    Column(
        modifier = modifier
            .fillMaxWidth()
            .testTag(IdentityTestTags.COMPOSE_FACE)
            // For the identity face's reason, minus the keyboard: there is no field here, so what this
            // protects against is a short window and a long translation rather than the IME.
            .verticalScroll(rememberScrollState())
            .padding(start = SIDE, top = TOP, end = SIDE, bottom = SIDE),
        verticalArrangement = Arrangement.spacedBy(BLOCK_GAP),
    ) {
        // **Above the things it is about, at full strength**, which is the identity face's arrangement
        // and its reason: the face still opens, and what held changes is that the chips go quiet and
        // this says why.
        uiState.requirement?.let { requirement -> RequirementCard(requirement = requirement) }

        PreviewCard(mark = uiState.mark, markName = uiState.markName, held = held)

        Ladder(label = Strings.markSlotBody(), held = held) {
            for (choice in uiState.bodies) {
                PartChip(
                    glyph = uiState.mark.copy(body = choice.body),
                    name = choice.name,
                    selected = choice.body == uiState.mark.body,
                    held = held,
                    tag = IdentityTestTags.body(choice.body),
                    onClick = { onChooseBody(choice.body) },
                    modifier = Modifier.weight(1f),
                )
            }
        }

        Ladder(label = Strings.markSlotPath(), held = held) {
            for (choice in uiState.paths) {
                PartChip(
                    glyph = uiState.mark.withPath(choice.path),
                    name = choice.name,
                    selected = choice.path == uiState.mark.path,
                    held = held,
                    tag = IdentityTestTags.path(choice.path),
                    onClick = { onChoosePath(choice.path) },
                    modifier = Modifier.weight(1f),
                )
            }
        }

        // **Not drawn rather than disabled**, and read off the mark rather than off a flag: a terminus
        // is the end of a path, which `PlayerMark.Composed` already refuses to let go wrong, so the one
        // place it shows on screen is a reading of that invariant instead of a second copy of it.
        if (uiState.mark.path != MarkPath.NONE) {
            Ladder(label = Strings.markSlotTerminus(), held = held, tag = IdentityTestTags.TERMINUS_LADDER) {
                for (choice in uiState.termini) {
                    PartChip(
                        glyph = uiState.mark.copy(terminus = choice.terminus),
                        name = choice.name,
                        selected = choice.terminus == uiState.mark.terminus,
                        held = held,
                        tag = IdentityTestTags.terminus(choice.terminus),
                        onClick = { onChooseTerminus(choice.terminus) },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }

        // The two facts nothing on the face can say for itself: that a tap is already the whole of the
        // commitment, and why the third ladder comes and goes.
        Text(
            text = Strings.markComposeFoot().resolve(),
            color = OltreColors.textSecondary,
            fontFamily = oltreMono(),
            fontSize = FOOT_SIZE,
            lineHeight = FOOT_LINE,
        )
    }
}

// **Swapping the path is the one slot change that can make an illegal mark**, so it is the one that is
// written out rather than copied: a mark with no path has no terminus, and `PlayerMark.Composed`
// throws rather than quietly allowing the pair. Choosing `None` therefore clears the terminus with it,
// which is exactly what the ladder disappearing means.
private fun PlayerMark.Composed.withPath(path: MarkPath): PlayerMark.Composed = if (path == MarkPath.NONE) {
    PlayerMark.Composed(body = body, path = MarkPath.NONE, terminus = MarkTerminus.NONE)
} else {
    copy(path = path)
}

// **The mark at the size it is chosen and again at the size it ships**, which is the whole argument for
// the card: 72dp is where a player can see what the three parts did, and 20dp is where they will
// actually meet it on the strip. A composer that only showed the large one would be letting somebody
// pick a mark that turns to mush on the bar above every screen.
@Composable
@NonRestartableComposable
private fun PreviewCard(mark: PlayerMark.Composed, markName: TextRes, held: Boolean) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(CARD_PAD),
        modifier = Modifier
            .fillMaxWidth()
            // **The mark-name line's own 42%**, which is the identity face's reading of the frame: what
            // goes quiet is what cannot be committed, and this card is a preview of the very thing the
            // chips below it can no longer choose.
            .then(if (held) Modifier.alpha(HELD_DIM) else Modifier)
            .border(1.dp, CARD_EDGE, oltreCardShape)
            .background(oltreCardSurface, oltreCardShape)
            .padding(CARD_PAD),
    ) {
        IdentityMark(mark = mark, color = OltreColors.accent, size = PREVIEW_MARK)
        Column(verticalArrangement = Arrangement.spacedBy(ECHO_GAP)) {
            IdentityMark(mark = mark, color = OltreColors.accent, size = STRIP_MARK)
            Text(
                text = markName.resolve(),
                color = OltreColors.textSecondary,
                fontFamily = oltreMono(),
                fontSize = TUPLE_SIZE,
                lineHeight = TUPLE_LINE,
            )
        }
    }
}

// One slot, one row, and the row never wraps: three ladders read as three questions only while each of
// them is one line. The chips take equal weight, so the four across at 393dp and the four across at
// 320dp are the same code measuring a narrower window.
//
// **The dim falls on the chips and not on the label**, which is the identity face's own split: a label
// is structure, and what goes quiet is the row of things that cannot commit.
@Composable
@NonRestartableComposable
private fun Ladder(
    label: TextRes,
    held: Boolean,
    tag: String? = null,
    chips: @Composable RowScope.() -> Unit,
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(ITEM_GAP),
        modifier = if (tag == null) Modifier else Modifier.testTag(tag),
    ) {
        SectionLabel(text = label)
        Row(
            horizontalArrangement = Arrangement.spacedBy(CHIP_GAP),
            modifier = if (held) Modifier.alpha(HELD_DIM) else Modifier,
            content = chips,
        )
    }
}

// **Every tap commits**, like every other control in this app, and the chip is a drawing rather than a
// word — so the word is what it says when it is read aloud. That is the settings sheet's own move for a
// row whose state is a glyph, borrowed here because there is nowhere on a 44dp chip to put a label that
// `Trasferimento` would fit inside at 320dp.
//
// **Held draws the chip and takes the press away entirely**, which is `MarkCell`'s own arrangement and
// its reason: a target that presses and does nothing is the one failure worse than a control that is
// missing, and the card above has already said why this one is quiet.
@Composable
@NonRestartableComposable
private fun PartChip(
    glyph: PlayerMark.Composed,
    name: TextRes,
    selected: Boolean,
    held: Boolean,
    tag: String,
    onClick: () -> Unit,
    modifier: Modifier,
) {
    val spoken = name.resolve()
    val face = Modifier
        .fillMaxWidth()
        .height(CHIP_HEIGHT)
        .background(settlingColor(if (selected) CHOSEN_FILL else oltreCardSurface), oltreActionShape)
        .border(1.dp, settlingColor(if (selected) CHOSEN_EDGE else CHIP_EDGE), oltreActionShape)
    val mark: @Composable () -> Unit = {
        IdentityMark(
            mark = glyph,
            color = settlingColor(if (selected) OltreColors.accent else OltreColors.textSecondary),
            size = CHIP_MARK,
        )
    }
    if (held) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = modifier.testTag(tag).semantics { contentDescription = spoken }.then(face),
            content = { mark() },
        )
    } else {
        PressableFace(
            onClick = onClick,
            shape = oltreActionShape,
            modifier = modifier.testTag(tag).semantics { contentDescription = spoken },
            faceModifier = face,
            content = mark,
        )
    }
}

// The frame's numbers, and this face shares the identity face's page geometry because it is the same
// sheet with different contents in it.
private val SIDE = 16.dp
private val TOP = 9.dp
private val BLOCK_GAP = 13.dp
private val ITEM_GAP = 7.dp

private val CARD_PAD = 11.dp
private val PREVIEW_MARK = 72.dp

// The size it ships at, on the strip. Not a preview convention — it is the same number `PlayerStrip`
// draws, said here because seeing it is the point of the echo.
private val STRIP_MARK = 20.dp
private val ECHO_GAP = 7.dp

private val TUPLE_SIZE = 10.5.sp
private val TUPLE_LINE = 15.sp

private val CHIP_HEIGHT = 44.dp
private val CHIP_GAP = 7.dp
private val CHIP_MARK = 24.dp

private val FOOT_SIZE = 11.sp
private val FOOT_LINE = 17.sp

// The identity face's pair, stated again here rather than shared: this system has twelve flat colours
// and no alpha tokens, so every lit control in the app re-derives accent 45% and accent 12% at its own
// call site. The resting pair is the cell's too — a chip and a cell are the same kind of choice drawn
// at two sizes, and the frame gives a face for one of them.
private val CHOSEN_EDGE = OltreColors.accent.copy(alpha = 0.45f)
private val CHOSEN_FILL = OltreColors.accent.copy(alpha = 0.12f)
private val CHIP_EDGE = Color.White.copy(alpha = 0.09f)
private val CARD_EDGE = Color.White.copy(alpha = 0.09f)
