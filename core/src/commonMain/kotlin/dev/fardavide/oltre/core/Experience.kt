package dev.fardavide.oltre.core

import kotlinx.serialization.Serializable
import kotlin.jvm.JvmInline

// **The player's standing: seeded from the log once, then carried on `GameState` and paid into.**
//
// Two of Davide's sentences shaped this file and they pull in opposite directions.
//
// 2026-08-22: *"make it so next time I start the game it gives me experience for everything I did
// before."* Only the log can answer that. `GameState.eventLog` has recorded every completed build,
// project, ladder, hull, survey and run since the format's first version, and schema 1 is the only
// one this build refuses — so every save a player can still open carries its whole history, and a
// fold over it is the honest opening balance for a colony that was played before the level existed.
//
// 2026-08-23, on the first cut, which folded that log on every read: *"this is bad, because the more
// the player progresses, the more it will be intensive to infer the level!"* Exactly right, and it
// is the worst shape of that mistake — free on day one, and paid by the players who played the most,
// months later, on a reading the chrome recomputes above every screen.
//
// So the fold happens **once per save, in the 15 → 16 migration**, and from then on `experience` is a
// field the verbs add to. `experienceOf` below is not dead with the migration written: it is what
// that hop calls, and it is what `ExperienceTest` compares the stored field against on every state
// the verbs produce — the standing proof that the cheap number and the expensive one agree.
//
// **Nothing may append to `eventLog` except `GameState.logging`**, which is where the two are kept in
// step. That is the whole of the invariant, and it is why the field is safe to trust.

// One number, and deliberately not a `Long` in the open. The wrapper is what stops an experience
// being passed where a threshold was wanted, and both are Longs.
@Serializable
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

// The fold, and **it is not what the game reads.** Two callers and both are deliberate: the 15 → 16
// migration, which uses it once per save to state an opening balance no field could have held, and
// the tests, which use it to check that `GameState.experience` still equals it. A third caller on a
// path a player waits for would be the thing this design exists to avoid — see the header.
//
// Order-independent and total: every member of `Event` is priced in `ExperienceBalance`, including
// the six that are worth nothing.
fun experienceOf(eventLog: List<Event>): Experience =
    eventLog.fold(Experience.NONE) { total, event -> total + ExperienceBalance.awardFor(event) }

// The reading the strip draws, off the stored total. Constant time whatever the colony has done.
fun GameState.playerProgress(): PlayerProgress = ExperienceBalance.progressFor(experience)
