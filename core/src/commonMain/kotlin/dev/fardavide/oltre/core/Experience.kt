package dev.fardavide.oltre.core

import kotlin.jvm.JvmInline

// **The player's standing, derived from the log rather than stored beside it.**
//
// Davide, 2026-08-22: *"make it so next time I start the game it gives me experience for everything
// I did before."* That sentence is a design constraint before it is a feature, and it decides the
// whole shape of this file: an experience *field* could only ever hold what a migration invented for
// it, and a migration has no honest answer — an existing colony's experience is neither zero, which
// confiscates a fortnight of play, nor a number picked at the keyboard. See
// `.claude/docs/player-strip-sheet.md` §3, which named the fold as the follow-up and is now closed
// by it.
//
// A fold has no such problem. `GameState.eventLog` has recorded every completed build, project,
// ladder, hull, survey and run since the format's first version, and schema 1 is the only one this
// build refuses — so **every save a player can still open carries its whole history**, and reading
// the level off it is retroactive by construction. Nothing migrates, `GameSave.SCHEMA_VERSION` does
// not move, and a save written by 0.16 opens in 0.17 on the level it had already earned.
//
// The cost is that the answer is recomputed rather than read. It is a sum over a list that grows by
// a few hundred entries a month — the sim's thirty-day player logs about 2,100 — so the arithmetic
// is not worth caching. **The day the log is ever trimmed, this stops being true**, and the answer
// then is a checkpoint in the envelope rather than a field in the state: what would be stored is
// "the experience of the entries that were dropped", which a migration *can* answer honestly.

// One number, and deliberately not a `Long` in the open. The wrapper is what stops an experience
// being passed where a threshold was wanted, and both are Longs.
//
// **Not `@Serializable`, and that is the point of the file**: nothing here goes on disk.
@JvmInline
value class Experience(val points: Long) : Comparable<Experience> {

    init {
        require(points >= 0) { "experience must be non-negative, was $points" }
    }

    operator fun plus(other: Experience): Experience = Experience(points + other.points)

    override fun compareTo(other: Experience): Int = points.compareTo(other.points)

    companion object {
        val NONE: Experience = Experience(0)
    }
}

// The number in the badge. Its own type rather than `BuildingLevel` or a bare `Int`, because this
// game already has two other things called a level — a facility's and a technology's — and the one
// bug this wrapper exists to prevent is a mine's level reaching the player's badge.
@JvmInline
value class PlayerLevel(val value: Int) {

    init {
        require(value >= 0) { "player level must be non-negative, was $value" }
    }
}

// What the strip draws, in the units it draws them in. `earned` is the whole history and the other
// three are this level alone — the gauge is a share of the level being served, not of the game, so a
// player who has just levelled sees an empty track rather than a nearly-full one.
data class PlayerProgress(
    val level: PlayerLevel,
    val earned: Experience,
    val intoLevel: Experience,
    val span: Experience,
) {

    // 0..99 while a level is being served. It cannot read 100: reaching the span *is* the next
    // level, so the only way to draw a full track would be to be standing on a level you have
    // already left.
    val percent: Int
        get() = (checkedTimes(intoLevel.points, PERCENT) { "gauge of $intoLevel" } / span.points).toInt()
}

private const val PERCENT: Long = 100

// The fold. Order-independent and total: every member of `Event` is priced in `ExperienceBalance`,
// including the six that are worth nothing.
fun experienceOf(eventLog: List<Event>): Experience =
    eventLog.fold(Experience.NONE) { total, event -> total + ExperienceBalance.awardFor(event) }

fun GameState.playerProgress(): PlayerProgress = ExperienceBalance.progressFor(experienceOf(eventLog))
