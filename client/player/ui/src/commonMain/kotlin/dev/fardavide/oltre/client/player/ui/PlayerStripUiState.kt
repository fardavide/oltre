package dev.fardavide.oltre.client.player.ui

import androidx.compose.runtime.Immutable
import dev.fardavide.oltre.client.design.text.TextRes
import dev.fardavide.oltre.protocol.PlayerMark

// What the strip draws. Four readings and no verbs — the two things in it that act are the gear and
// the cluster beside it, and what either of them does is this module's own business rather than
// something a caller decides.
//
// **`Immutable` because it is passed by value into chrome that recomposes on every tick.** The
// scaffold below it is skippable only if every parameter it takes is stable, and a `data class` of
// `TextRes`, `Int` and a sealed pair of data classes is — but the annotation says so out loud rather
// than leaving it to the compiler's inference across a module boundary.
@Immutable
data class PlayerStripUiState(
    val name: TextRes,
    // **The mark the account holds, and there is no default here for the same reason the class has
    // none anywhere else.** An account that has chosen nothing wears `Preset(THRESHOLD)` and is
    // called `Dead Reckoning`, and neither is drawn differently from a chosen one — see
    // `PlayerProfile`, which argues why a default is a mark rather than an absence. A default
    // parameter would make that substitution happen silently in whichever construction site forgot,
    // instead of once in the mapper that reads the profile.
    val mark: PlayerMark,
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
