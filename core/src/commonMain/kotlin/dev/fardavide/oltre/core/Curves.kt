package dev.fardavide.oltre.core

// The two integer growth rules the game's curves are built from. Both live here rather than in one
// balance object because the buildings and the research branch share them, and a second copy would
// be a second rounding convention nobody chose.

// ── Every multiplication in a curve goes through here ────────────────────────────────────────
//
// A wrapped Long is the worst bug this game can have and it is completely silent: `x * 3` past
// Long.MAX comes back **negative**, a negative cost is one `covers()` reads as *free*, and the
// player buys the Nanite Factory for nothing. It has already happened once — a convergence level of
// 18 on the opening discount priced it at −70 deuterium — and it was caught by `Resources.of`
// rejecting the negative, which is a crash in a running game rather than an answer.
//
// So no curve multiplies directly. Detection is the standard inverse check and it is exact on every
// target Kotlin compiles to, including the emulated Long on JS: if `a` is non-zero and dividing the
// product back does not return `b`, the product wrapped.
//
// **It throws rather than saturating.** Saturating would keep the game running while quietly
// changing the numbers, and a cost of Long.MAX is not a cost anybody designed — it is a wrong
// answer wearing a plausible face. A thrown error names the curve and the level, so the next person
// to push a cap past what Long can hold finds out at the point of definition.
internal fun checkedTimes(a: Long, b: Long, what: () -> String): Long {
    val product = a * b
    require(a == 0L || (product / a == b && !(a == -1L && b == Long.MIN_VALUE))) {
        "${what()}: $a x $b overflows a 64-bit integer"
    }
    return product
}

// Geometric growth floored at every step rather than once at the end. Per-step flooring is the
// rule, not an approximation of one: an hourly rate has to be a whole number of units for
// fine-unit accrual to stay exact. It is also the only form that survives an unbounded number of
// steps — `exactGeometric` below multiplies by the numerator every step and leaves Long quickly.
internal fun compound(base: Long, steps: Int, numerator: Long, denominator: Long): Long {
    var value = base
    repeat(steps) { step ->
        value = checkedTimes(value, numerator) { "compound($base, $steps) at step $step" } / denominator
    }
    return value
}

// ── The opening discount ─────────────────────────────────────────────────────────────────────
//
// Davide, 2026-08-09: *"Everything must be cheaper and quicker across the board, until first
// expedition. Lets say starting about 3x at the start of the game, and arrive to 1x at the moment
// you can have the first expedition."* He then named the moment: **when the galaxy becomes
// actionable** — the adaptation ladders at Robotics Factory 4, the point where a probe's findings
// can be bought against rather than only read.
//
// So this lives in `Curves.kt` beside the two growth rules rather than inside one balance object:
// three cost tables answer to it, and a second copy would be a second rounding convention nobody
// chose — the reason `compound` and `exactGeometric` are here in the first place.
//
// **Linear recovery, not geometric.** A third at level 1, climbing in equal steps, full price from
// `fullPriceLevel` on. Geometric would need a fractional root between the two things anyone
// actually wants to say — *how cheap at the start* and *where does it stop* — and `core` has no
// fractional anything. Linear needs neither: the multiplier is at most `3 × (fullPriceLevel − 1)`,
// so unlike a carried power this cannot leave Long however deep the caller goes.
internal const val OPENING_DISCOUNT_DIVISOR: Long = 3

internal fun openingDiscount(fullPrice: Long, level: Int, fullPriceLevel: Int): Long {
    if (fullPriceLevel <= 1 || level >= fullPriceLevel) return fullPrice
    val span = (fullPriceLevel - 1).toLong()
    // At level 1 this is `span / (3 × span)` — exactly a third — and at `fullPriceLevel − 1` it is
    // `(3 × span − 2) / (3 × span)`, a single step short of the full price the branch above returns.
    val share = span + (OPENING_DISCOUNT_DIVISOR - 1) * (level - 1)
    return checkedTimes(fullPrice, share) { "openingDiscount($fullPrice, level $level)" } /
        (OPENING_DISCOUNT_DIVISOR * span)
}

// Geometric growth carried exactly and rounded once, half up. Used for the research costs, whose
// published tables were computed this way — flooring at every step drifts a unit low by level 5
// and eight units low by level 10, which would put the game's cost chips out of step with the
// design sheet's numbers for no gain.
//
// The exact numerator is `base * numerator^steps`, which is why every caller has to bound `steps`:
// past that bound it wraps negative, and a negative cost is one `covers()` reads as free.
internal fun exactGeometric(base: Long, steps: Int, numerator: Long, denominator: Long): Long {
    var top = base
    var bottom = 1L
    repeat(steps) { step ->
        top = checkedTimes(top, numerator) { "exactGeometric($base, $steps) at step $step" }
        bottom = checkedTimes(bottom, denominator) { "exactGeometric($base, $steps) divisor at step $step" }
    }
    return (top + bottom / 2) / bottom
}
