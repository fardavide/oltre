package dev.fardavide.oltre.client.player.ui

import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.selection.LocalTextSelectionColors
import androidx.compose.foundation.text.selection.TextSelectionColors
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.NonRestartableComposable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.fardavide.oltre.client.design.component.PressableFace
import dev.fardavide.oltre.client.design.component.oltreActionShape
import dev.fardavide.oltre.client.design.component.oltreCardSurface
import dev.fardavide.oltre.client.design.core.OltreColors
import dev.fardavide.oltre.client.design.core.oltreMono
import dev.fardavide.oltre.client.design.core.resolve
import dev.fardavide.oltre.client.design.core.settlingColor
import dev.fardavide.oltre.client.design.text.Strings
import dev.fardavide.oltre.protocol.CommanderName

// **The app's first name field, and the second text input in it.** `SearchField` in
// `:client:galaxy:ui` is the first and the idiom is copied from it wholesale: a `BasicTextField` whose
// `decorationBox` draws the placeholder *behind* `field()` rather than instead of it, so the caret is
// never swapped out on the first keystroke. Material's `TextField` is used nowhere in this app and
// this is not where that changes — it brings a container, a label slot and an error state, and the
// design refuses all three by name.
//
// **The field is the card at the button's radius**, which is the frame's own sentence: 44dp of
// `#101218` inside a hairline at 9dp corners, with the strip's own 13.5sp SemiBold inside it — so what
// you type is set in the face you will read it in.
//
// **There is no error state and there cannot be one.** A name that cannot collide with anybody cannot
// be rejected: it is trimmed and bounded by the field, and the only other thing that can go wrong is
// the network, which is amber rather than red and is drawn by the face above rather than here.
@Composable
internal fun NameField(
    draft: String,
    // **Amber and still tappable rather than greyed**, because there is no disabled state in this
    // product. Held takes the caret and the clear away and leaves the field readable and focusable —
    // what it cannot do is accept a keystroke, and the note under it says so in words.
    held: Boolean,
    onDraftChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    // The one piece of state this file keeps, and it is not a fact about the player: whether the
    // platform has given this node focus. Three things read it — the line, the caret and the clear —
    // and none of them is anything a mapper could know.
    var focused by remember { mutableStateOf(false) }
    val holding = draft.isNotEmpty()
    val editable = focused && !held
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(NOTE_GAP)) {
        // Provided rather than themed: `TextSelectionColors` is Material's ambient and the palette this
        // app draws with is not Material's, so the one place a selection is ever drawn is the one place
        // that has to say what colour it is.
        CompositionLocalProvider(LocalTextSelectionColors provides SELECTION) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(FIELD_HEIGHT)
                    .background(if (held) HELD_FILL else oltreCardSurface, oltreActionShape)
                    // Settling, because focus is moved by a tap and by nothing else — which is exactly
                    // the set of colours `settlingColor` is for, and exactly the swap the active card
                    // already makes.
                    .border(
                        1.dp,
                        settlingColor(
                            when {
                                held -> HELD_EDGE
                                focused -> FOCUSED_EDGE
                                else -> RESTING_EDGE
                            },
                        ),
                        oltreActionShape,
                    )
                    .padding(start = FIELD_LEAD),
            ) {
                BasicTextField(
                    value = draft,
                    // **The bound is enforced here and stated on the wire**, and the two are the same
                    // number by construction: `CommanderName` refuses anything longer, and a field that
                    // could produce one would be shipping a bound as a lie.
                    //
                    // A value over the bound is dropped whole rather than truncated, which only shows on
                    // a paste: truncating would take characters off the end of what is *already* in the
                    // field whenever the caret is not at it, and silently deleting somebody's typing is
                    // worse than declining their paste. Nothing shakes and nothing turns red either way
                    // — the counter at the bound is the whole of the answer.
                    onValueChange = { typed -> if (typed.length <= CommanderName.MAX_LENGTH) onDraftChange(typed) },
                    readOnly = held,
                    singleLine = true,
                    textStyle = TextStyle(
                        color = OltreColors.text,
                        fontFamily = oltreMono(),
                        fontSize = NAME_SIZE,
                        fontWeight = FontWeight.SemiBold,
                    ),
                    // **Accent, and this contradicts `SearchField` on purpose.** That field argues its
                    // caret takes the ink it is inserting because "accent means *tap this* everywhere
                    // else and a caret is not a target", and it is a good argument. The frame overrules
                    // it here: a search caret sits in a 28dp filter and this one is the only moving
                    // thing on a face about who you are. Both are written down so the next reader finds
                    // the disagreement rather than assuming one of them is a slip.
                    //
                    // **The 1.5 × 19dp the frame gives is the platform's to draw and is not settable**:
                    // `BasicTextField` exposes the brush and nothing else, so the width and the height
                    // are whatever Compose paints at this line height. Only the hue landed.
                    cursorBrush = SolidColor(if (held) Color.Transparent else OltreColors.accent),
                    decorationBox = { field ->
                        Box(contentAlignment = Alignment.CenterStart) {
                            // **Behind the field rather than instead of it**, which is `SearchField`'s
                            // finding and the reason the caret survives the first keystroke.
                            //
                            // Always `Dead Reckoning`, and in the same size and weight as real text: it
                            // is not a hint, it is the value an empty field would save.
                            if (!holding) {
                                Text(
                                    text = Strings.playerDefaultName().resolve(),
                                    color = OltreColors.textTertiary,
                                    fontFamily = oltreMono(),
                                    fontSize = NAME_SIZE,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1,
                                    softWrap = false,
                                )
                            }
                            field()
                        }
                    },
                    modifier = Modifier
                        .weight(1f)
                        .testTag(IdentityTestTags.NAME)
                        .onFocusChanged { state -> focused = state.isFocused },
                )
                // **Silent to seventeen and showing from eighteen**, and it never changes hue: amber
                // means held and red means short, and running out of characters is neither. Full ink at
                // the bound is the only thing that moves.
                if (draft.length >= COUNTER_FROM) {
                    Text(
                        text = Strings
                            .profileNameCounter(length = draft.length, max = CommanderName.MAX_LENGTH)
                            .resolve(),
                        color = if (draft.length == CommanderName.MAX_LENGTH) {
                            OltreColors.text
                        } else {
                            OltreColors.textTertiary
                        },
                        fontFamily = oltreMono(),
                        fontSize = COUNTER_SIZE,
                        maxLines = 1,
                        softWrap = false,
                        modifier = Modifier.testTag(IdentityTestTags.COUNTER).padding(start = COUNTER_GAP),
                    )
                }
                // **Only while focused and holding something**, which is the frame's rule and the
                // reason the trailing space is a `Spacer` rather than padding: the target is a 44dp
                // square in a 44dp row, so it *is* the trailing end of the field, and padding around it
                // would push it out of the bounds the design drew it inside.
                if (editable && holding) {
                    ClearTarget(onClick = { onDraftChange("") })
                } else {
                    Spacer(modifier = Modifier.width(FIELD_LEAD))
                }
            }
        }
        // One line, and which one is a statement about what would happen rather than about what went
        // wrong. Held says a rename cannot wait; empty says what saving an empty field gives you. Held
        // outranks it, because a player who cannot save at all does not need to be told what saving
        // nothing would do.
        val note = when {
            held -> Strings.profileHeldFieldNote()
            holding -> null
            else -> Strings.profileEmptyName()
        }
        note?.let {
            Text(
                text = it.resolve(),
                color = OltreColors.textSecondary,
                fontFamily = oltreMono(),
                fontSize = NOTE_SIZE,
                lineHeight = NOTE_LINE,
            )
        }
    }
}

// **It clears to empty, which is not a delete.** An empty field is a preview of `Dead Reckoning`
// rather than an error, so this is the way *out* of a name somebody regrets and not a destructive
// control — which is why it is two strokes in the body ink rather than anything red.
//
// The 44dp square is the tap target and the 17dp cross is the face, which is `PressableFace`'s whole
// reason: the click and the indication sit on different nodes, so the ripple is the cross's own square
// rather than a smear across the end of the field.
@Composable
@NonRestartableComposable
private fun ClearTarget(onClick: () -> Unit) {
    PressableFace(
        onClick = onClick,
        shape = oltreActionShape,
        modifier = Modifier
            .size(FIELD_HEIGHT)
            .testTag(IdentityTestTags.CLEAR)
            // **It must not take focus, and this is a measured fix rather than a precaution.** A
            // `clickable` is focusable, and this control is drawn only while the *field* has focus — so
            // pressing it moved focus here, which took the control off screen between the finger going
            // down and coming up, and the tap it was cancelled by was its own. Nothing fired. It is
            // exactly the dead control the house rule forbids, and it was invisible until a robot
            // pressed it: the button was drawn correctly in every frame.
            .focusProperties { canFocus = false },
    ) {
        Canvas(modifier = Modifier.size(CLEAR_GLYPH)) {
            drawClearGlyph(unit = size.width / MARK_VIEWBOX, color = OltreColors.textSecondary)
        }
    }
}

// A plain `DrawScope` function for `drawIdentityMark`'s reason: a `Canvas { }` lambda is not
// `@Composable`, so the unit pass counts it and cannot execute it.
//
// **Ten units across where the gear beside it spends nearly sixteen**, and the difference is what the
// two glyphs have to do. A cog is six teeth around a rim and is illegible until it is nearly the width
// of its box; a cross is two strokes and reads at any size, so it takes the smaller span and stays
// quieter than the mark it sits along the row from. The frame gives the size and the stroke and not
// these coordinates.
internal fun DrawScope.drawClearGlyph(unit: Float, color: Color) {
    fun at(x: Float, y: Float) = Offset(x = x * unit, y = y * unit)

    drawLine(
        color = color,
        start = at(CLEAR_NEAR, CLEAR_NEAR),
        end = at(CLEAR_FAR, CLEAR_FAR),
        strokeWidth = MARK_STROKE_WIDTH * unit,
        cap = StrokeCap.Round,
    )
    drawLine(
        color = color,
        start = at(CLEAR_FAR, CLEAR_NEAR),
        end = at(CLEAR_NEAR, CLEAR_FAR),
        strokeWidth = MARK_STROKE_WIDTH * unit,
        cap = StrokeCap.Round,
    )
}

// The frame's numbers. The height is the button's, the radius is the button's, and the lead is the
// only measurement here that belongs to nothing else in the app.
internal val FIELD_HEIGHT = 44.dp
private val FIELD_LEAD = 11.dp

// The last length nothing is said about. "Silent to 17, showing from 18" is the whole of the rule.
private const val COUNTER_FROM = 18

private val COUNTER_SIZE = 10.5.sp

// Between the text and the counter. Seven, which is the gap this face spends everywhere a thing sits
// beside the thing it is about — the frame does not give a figure for this one.
private val COUNTER_GAP = 7.dp

private val CLEAR_GLYPH = 17.dp
private const val CLEAR_NEAR = 7f
private const val CLEAR_FAR = 17f

private val NOTE_GAP = 5.dp
private val NOTE_SIZE = 11.sp
private val NOTE_LINE = 16.sp

private val RESTING_EDGE = Color.White.copy(alpha = 0.09f)

// Accent 45%, the edge every lit control in this app spends — re-derived here because the system has
// twelve flat colours and no alpha tokens, so `LedgerHead`, `AlertSheet` and `DispatchSheet` all state
// this same value at their own call sites.
private val FOCUSED_EDGE = OltreColors.accent.copy(alpha = 0.45f)

// The fleet strip's pair, at the fleet strip's scale: amber 22% around the 6% that this app keeps
// baked opaque as `#141111` rather than as an alpha. A held field and a fleet in transit are the same
// claim about two different kinds of thing.
private val HELD_EDGE = OltreColors.warn.copy(alpha = 0.22f)
private val HELD_FILL = Color(0xFF141111)

// **The one alpha in this file that is not a border**, and the handle takes the flat accent because a
// platform selection handle is drawn by the platform and cannot be given an alpha that means anything.
private val SELECTION = TextSelectionColors(
    handleColor = OltreColors.accent,
    backgroundColor = OltreColors.accent.copy(alpha = 0.22f),
)
