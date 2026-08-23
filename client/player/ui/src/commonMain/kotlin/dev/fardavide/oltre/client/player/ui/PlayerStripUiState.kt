package dev.fardavide.oltre.client.player.ui

import androidx.compose.runtime.Immutable
import dev.fardavide.oltre.client.design.text.TextRes

// What the strip draws. Four readings and no verbs — the one thing in it that acts is the gear, and
// what the gear does is this module's own business rather than something a caller decides.
//
// **`Immutable` because it is passed by value into chrome that recomposes on every tick.** The
// scaffold below it is skippable only if every parameter it takes is stable, and a `data class` of
// `TextRes` and `Int` is — but the annotation says so out loud rather than leaving it to the
// compiler's inference across a module boundary.
@Immutable
data class PlayerStripUiState(
    val name: TextRes,
    val level: TextRes,
    // 0..100, and coerced where it is drawn rather than here: a state is a reading, and refusing an
    // out-of-range one at construction would put a rule in the model this slice does not have.
    val experiencePercent: Int,
)

// **There is no factory here any more, and the absence is the slice.** 0.16 shipped one returning
// three constants, because nothing awarded experience and there was nothing to map. 0.17 folds the
// event log for the level and the gauge, so the state is built from a `GameState` in
// `:client:player:presentation` — which is what this module's build file said would happen the day
// the numbers became real.
//
// Nothing replaces it in `commonMain`. A "the strip before anything happened" constant would be a
// second place the name is decided, and the name is the mapper's to say.
