package dev.fardavide.oltre.client.player.ui

import androidx.compose.runtime.Immutable
import dev.fardavide.oltre.client.design.text.TextRes
import dev.fardavide.oltre.protocol.MarkBody
import dev.fardavide.oltre.protocol.MarkPath
import dev.fardavide.oltre.protocol.MarkTerminus
import dev.fardavide.oltre.protocol.PlayerMark

// Forty marks from eleven drawings, as three ladders and the thing they make.
//
// **The mark itself is the state and the chips are read off it**, rather than each chip carrying a
// `selected` of its own. `PlayerMark.Composed` already refuses a terminus on a path that is `NONE`, so
// a second copy of *which parts are on* would be a second place that invariant could be got wrong —
// and the one place it shows on screen, the terminus ladder not being drawn at all, is then a reading
// of the wire contract rather than a rule this file restates.
//
// The three lists are the catalogue's vocabulary and nothing else: a part and the word for it. What a
// chip *draws* is the whole mark with that one slot swapped, which is geometry and belongs to the face.
@Immutable
data class MarkComposeFaceUiState(
    val mark: PlayerMark.Composed,
    // "Your mark · Limb · Rising · Dot" — `Strings.clauses` of the composed noun and the three parts,
    // joined by whoever knows both vocabularies. The same line the identity face draws under its grid.
    val markName: TextRes,
    val bodies: List<MarkBodyChoice>,
    val paths: List<MarkPathChoice>,
    // Always the three, even when the path is `NONE` and the ladder is not drawn: the words do not stop
    // existing because a row is off screen, and a list that emptied itself would be a second statement
    // of the same invariant.
    val termini: List<MarkTerminusChoice>,
    // "No network since 09:41" — the held card's lead, and null when the eleven chips can commit.
    // `IdentityFaceUiState.requirement` exactly, and it is here for the reason it is there: the card is
    // the state, so a state with no card to draw is a state with nothing to say.
    //
    // **This face had none of it, and eleven lit chips answered a tap with nothing.** The identity face
    // dimmed its grid and raised the card for the very same tap, which made the composer the one place
    // in the product where a control could be pressed and perceived to do nothing at all.
    val requirement: TextRes?,
)

// Three near-identical records rather than one over a type parameter, and the repetition is the point:
// a `MarkPartChoice<T>` would let the body ladder be handed the path words, where these three cannot be
// passed to each other at all.
@Immutable
data class MarkBodyChoice(val body: MarkBody, val name: TextRes)

@Immutable
data class MarkPathChoice(val path: MarkPath, val name: TextRes)

@Immutable
data class MarkTerminusChoice(val terminus: MarkTerminus, val name: TextRes)
