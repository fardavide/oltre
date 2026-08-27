package dev.fardavide.oltre.client.design.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.fardavide.oltre.client.design.text.TextRes

// **The three carriers of *the network is not there*, on one bench.** Each of them is spent by four
// or five screens, so a change to one moves every card in the app and a per-screen baseline says only
// that *something* moved.
//
// The three are deliberately together rather than in three frames, because the thing most likely to
// go wrong about them is how they read *against each other*: the ghost is amber because it is still
// a target, the note beneath it is muted because a second amber thing would be the card shouting, and
// the refusal is red because nothing was accepted at all. Three colours carrying three different
// promises, and the frame is where a human can check that they still say those three things.
@Composable
internal fun HeldBench() {
    Column(
        verticalArrangement = Arrangement.spacedBy(BENCH_GAP),
        modifier = Modifier.padding(BENCH_GAP).width(HELD_BENCH_CONTENT),
    ) {
        // The ghost beside a note, which is the pair a facility card actually draws: the button that
        // was pressed, and the sentence saying what pressing it did.
        Row(
            horizontalArrangement = Arrangement.spacedBy(BENCH_GAP),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            HeldAction(onClick = {})
        }
        HeldNote(text = TextRes.Raw("Upgrade held. It starts when the network is back."))
        // **The longest line in the catalogue**, on purpose: it is past one line at 393dp before it
        // is translated, and this component wraps where a verdict truncates. A frame that only held a
        // short line would photograph the easy case and miss the one the rule exists for.
        HeldNote(text = TextRes.Raw("Build held, and the alert held off with it. Both land together."))
        RefusalBlock(
            lead = TextRes.Raw("This cannot be held."),
            body = TextRes.Raw("A run touches the galaxy, so the server has to answer."),
        )
    }
}

private val BENCH_GAP = 14.dp

// The narrowest pane the app runs in, less the card padding either side — because what these three
// are for is a card, and the wrap is the property the frame is holding.
private val HELD_BENCH_CONTENT = 288.dp

internal const val HELD_BENCH_WIDTH = 320
internal const val HELD_BENCH_HEIGHT = 240
