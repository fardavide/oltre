package dev.fardavide.oltre.core

import kotlin.jvm.JvmInline
import kotlin.time.Duration
import kotlinx.serialization.Serializable

// **What an alliance takes off every build and every research its members start**, as a percentage
// and nothing else.
//
// **`core` does not learn that an alliance exists, and that is the point of the shape.** There is no
// alliance level here, no roster and no `:protocol` type: the server computes the percentage from
// the level and writes it into the snapshot when it advances that colony. `advance` stays a function
// of `GameState` alone, which is what makes the timing answer in `alliance-sheet.md` §4.2 work —
// the boon's effective instant *is* that member's next check-in, so nothing is backdated, nothing is
// recomputed, and `advance(s, t0, t2) == advance(advance(s, t0, t1), t1, t2)` is untouched.
//
// **The ceiling lives on the type rather than on the caller.** The design is 2% a level floored at
// 30%, which an alliance reaches at level 15; putting the bound here means no arithmetic anywhere —
// not the server's, not a migration's, not a test fixture's — can produce a boon past what was
// settled. A percentage that could be 80 is a percentage that will be, once.
//
// **Why a duration and not production**, recorded because it is the expensive half of the decision:
// a boon that divides a wait applies to what is started *next* and to nothing already running, since
// `BuildJob` and `ResearchJob` carry a `completesAt` fixed when the job starts. So *"it begins at
// your next check-in"* needs no explanation at all. Under a production multiplier the same rule is
// the confusing case — a player would ask why the three days they were away did not count — and it
// would compound with mines, the research branch and the energy throttle into the largest term in a
// month. `alliance-sheet.md` §4.3 is the full argument.
@Serializable
@JvmInline
value class AllianceSpeedup(val percent: Int) {

    init {
        require(percent in 0..MAX_PERCENT) {
            "an alliance takes off 0 to $MAX_PERCENT percent, not $percent"
        }
    }

    companion object {

        // Nineteen colonies in twenty, and every colony before this field existed.
        val NONE: AllianceSpeedup = AllianceSpeedup(0)

        // The floor the design settled, and the reason it exists: a divisor with no ceiling is how
        // membership stops being attractive and starts being mandatory.
        const val MAX_PERCENT: Int = 30
    }
}

// **Applied by multiplication rather than by division**, so a boon of zero is exactly the duration
// that was passed in and every balance figure measured before this existed is unchanged to the
// nanosecond. The two callers are `PlaceholderBalance.upgradeDuration` and
// `ResearchBalance.researchDuration`, and both apply it last.
fun Duration.shortenedBy(speedup: AllianceSpeedup): Duration =
    this * (100 - speedup.percent) / 100
