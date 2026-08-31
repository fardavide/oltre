package dev.fardavide.oltre.client.player.ui

import androidx.compose.runtime.Immutable
import dev.fardavide.oltre.client.design.text.TextRes
import dev.fardavide.oltre.protocol.MarkPreset

// What the identity face draws: six silhouettes, the word for whichever is worn, a door to the
// composer, and the name.
//
// **`draft` and `committed` are the two `String`s in the whole UI half that are not `TextRes`, and
// that is the type saying what they are rather than a lapse.** A `TextRes` is what the catalogue can
// say; a commander's name is what a keyboard produced, so it is a `String` on the way in — the field's
// `value` — and it is a `String` on the way out. Wrapping it in `TextRes.Raw` would buy a resolve per
// keystroke and no translation, because there is no language in which somebody's name is a different
// name.
//
// **The pair is what the save button is, and neither half of it is a `Boolean`.** *A Name You Chose*:
// the button is present only when the draft differs from what is committed, absent and never disabled
// — so a `saveable` flag beside the two strings it is computed from would be a third fact that can
// disagree with the first two.
//
// `Immutable` for `PlayerStripUiState`'s reason, and it is a promise here rather than an inference:
// `cells` is a `List`, which Compose cannot prove stable on its own.
@Immutable
data class IdentityFaceUiState(
    // **The picker's set rather than `MarkPreset.entries`**, because the two move on different days:
    // a preset retired from the grid is one accounts still wear and the wire still serves. Which six
    // are offered is `:client:profile:presentation`'s call, exactly as which words name them is.
    val cells: List<MarkCellUiState>,
    // The line under the grid: a preset's own noun, or — when the mark is composed — the tuple spelled
    // out through `Strings.clauses`. One field for both, because it is one line saying one thing, and
    // the face has no business knowing which of the two kinds it was handed.
    val markName: TextRes,
    // The account wears a composed mark, so the compose row takes the lit face and no cell is lit.
    // Not derivable from `cells` — every cell being unchosen is also what a mark this build has no
    // drawing for would look like, and those must not read the same.
    val composed: Boolean,
    val committed: String,
    val draft: String,
    // "No network since 09:41" — the held card's lead, and null when there is signal. Nullable rather
    // than a `held` flag beside it for `AlertSheetUiState.categories`' reason: the card is the state,
    // so a state with no card to draw is a state with nothing to say.
    val requirement: TextRes?,
)

// One cell of the grid. Two fields and no name: what the chosen mark is called is one line under the
// whole grid rather than a caption per cell, so a name here would be five strings nothing draws.
@Immutable
data class MarkCellUiState(val preset: MarkPreset, val chosen: Boolean)
