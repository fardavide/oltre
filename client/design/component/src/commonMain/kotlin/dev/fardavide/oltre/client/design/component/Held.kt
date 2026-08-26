package dev.fardavide.oltre.client.design.component

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.fardavide.oltre.client.design.core.OltreColors
import dev.fardavide.oltre.client.design.core.oltreMono
import dev.fardavide.oltre.client.design.core.resolve
import dev.fardavide.oltre.client.design.text.Strings
import dev.fardavide.oltre.client.design.text.TextRes

// **What a card is waiting on the server for**, and the three carriers of held in one record because
// a card can be waiting on two things at once. A shipyard card holds a build and the alert that goes
// with it, and the design draws that pair as *one* sentence rather than two lines — which is only
// possible if the thing that writes the sentence can see both.
//
// It is here rather than in each feature for `WatchUiState`'s reason: four screens draw a card with an
// action and a square, and a fifth copy of this record is a fifth chance for one of them to answer the
// question differently.
data class HeldUiState(
    // The action button has been tapped and nothing has answered. The button becomes an amber ghost
    // reading `Held` — still a target, and pressing it withdraws the request.
    val action: Boolean,
    // The square has been tapped and nothing has answered. Which *way* is not here: the square cannot
    // say it and the line below can, so it is said there once rather than carried twice.
    val watch: Boolean,
    // **What the card says about it, and it displaces the verdict line rather than adding a line.**
    // A held card has no countdown and no bar, so the slot is free — and the design's own fix for the
    // map card's bell is exactly this move, which makes it the rule rather than an exception.
    //
    // Null when nothing on the card is held, which is every card on a colony with signal.
    val line: TextRes?,
) {

    companion object {

        // A colony with signal. Every frame that is not about the queue is drawn with this, and it is
        // what a mapper hands back when the outbox is empty.
        val NONE = HeldUiState(action = false, watch = false, line = null)
    }
}

// **The card's foot when something on it is held**, in the note voice the card already uses for its
// verdict — muted, never amber. The surface and the face are already amber; a third amber thing would
// be the card shouting rather than stating.
//
// **It wraps where `RowVerdict` truncates**, and that is the one difference between the two. A verdict
// is authored to a width and drops its second clause in a Slide Over pane; a held line is a fact about
// the network with nothing optional in it, and the longest of them — *"Build held, and the alert held
// off with it. Both land together."* — is past one line at 393dp before it is translated. Dropping
// half of that would leave a card saying a build is held and not saying the alert is.
@Composable
fun HeldNote(text: TextRes, modifier: Modifier = Modifier) {
    Text(
        text = text.resolve(),
        color = OltreColors.textSecondary,
        fontFamily = oltreMono(),
        fontSize = 10.5.sp,
        lineHeight = 15.sp,
        modifier = modifier.padding(top = 4.dp),
    )
}

// **The action button while the request is outstanding, and it is never greyed and never removed.**
// There is no disabled state in this product: held turns the button into an amber ghost reading
// `Held`, which is still a target, and tapping it withdraws the request. Nothing has been sent, so
// there is nothing to countermand and nobody to tell.
//
// One component rather than a branch inside four cards, because the four would drift: this is the
// same object on a facility, a project, an adaptation and a hull, and the day the withdrawal grows a
// confirmation it grows one in four places at once or in none.
@Composable
fun HeldAction(onClick: () -> Unit, modifier: Modifier = Modifier) {
    Text(
        text = Strings.heldButton().resolve(),
        color = OltreColors.warn,
        fontFamily = oltreMono(),
        fontSize = 11.sp,
        fontWeight = FontWeight.SemiBold,
        // `pressable` ahead of the border for the reason every other action in the app declares it
        // first: it scales what is drawn inside it, and chrome declared before it is drawn outside.
        modifier = modifier
            .pressable(shape = oltreActionShape, onClick = onClick)
            .border(1.dp, OltreColors.warn.copy(alpha = 0.45f), oltreActionShape)
            .padding(horizontal = 11.dp, vertical = 7.dp),
    )
}

// **A refusal as two strings**, because it is drawn two ways and both need the same pair: as a block
// above a button, where the button holds its place and the sheet grows, and as a row's verdict line
// turned red, where there is no room for a block and the row already has a line.
//
// One record rather than two, so the sheet and the row cannot end up saying different things about
// the same fact — which is the whole reason the design writes *"same words, one line shorter"*.
data class RefusalUiState(val lead: TextRes, val body: TextRes)

// **A refusal, in the two places one is a block rather than a line.** Red lead over a muted body: the
// same grammar as the short cost chip — *this cannot happen, and here is the fact that stops it* —
// with the network in the noun slot.
//
// It is not amber, because nothing was accepted, and it is not accent, because there is nothing here
// to tap. The control that produced it is untouched: it keeps full strength and goes on answering,
// which is what lets the sentence name the target and what stops this becoming a dead control.
@Composable
fun RefusalBlock(lead: TextRes, body: TextRes, modifier: Modifier = Modifier) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = lead.resolve(),
            color = OltreColors.danger,
            fontFamily = oltreMono(),
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            lineHeight = 17.sp,
        )
        Text(
            text = body.resolve(),
            color = OltreColors.textSecondary,
            fontFamily = oltreMono(),
            fontSize = 11.sp,
            lineHeight = 17.sp,
        )
    }
}
