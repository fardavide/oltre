package dev.fardavide.oltre.client.player.ui

import androidx.compose.runtime.Immutable
import dev.fardavide.oltre.client.design.text.Strings
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

// **The whole of this slice's model, and it is a constant.** Nothing awards experience yet and
// nothing renames the player, so a field on `GameState` could only ever hold the value it was
// migrated in with — and the migration would have to answer what an existing colony's experience is,
// which has no honest answer. See `.claude/docs/player-strip-sheet.md` §3.
//
// A factory rather than defaults on the data class, per the house rule: a caller that builds one of
// these by hand — the day there is something to build it *from* — passes every value explicitly, so
// the compiler finds it when the shape grows.
fun playerStripUiState(): PlayerStripUiState = PlayerStripUiState(
    name = Strings.playerDefaultName(),
    level = Strings.levelBadge(STARTING_LEVEL),
    experiencePercent = STARTING_EXPERIENCE,
)

private const val STARTING_LEVEL = 0
private const val STARTING_EXPERIENCE = 0
