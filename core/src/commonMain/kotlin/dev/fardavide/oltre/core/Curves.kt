package dev.fardavide.oltre.core

// The two integer growth rules the game's curves are built from. Both live here rather than in one
// balance object because the buildings and the research branch share them, and a second copy would
// be a second rounding convention nobody chose.

// Geometric growth floored at every step rather than once at the end. Per-step flooring is the
// rule, not an approximation of one: an hourly rate has to be a whole number of units for
// fine-unit accrual to stay exact. It is also the only form that survives an unbounded number of
// steps — `exactGeometric` below multiplies by the numerator every step and leaves Long quickly.
internal fun compound(base: Long, steps: Int, numerator: Long, denominator: Long): Long {
    var value = base
    repeat(steps) { value = value * numerator / denominator }
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
    return fullPrice * (span + (OPENING_DISCOUNT_DIVISOR - 1) * (level - 1)) / (OPENING_DISCOUNT_DIVISOR * span)
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
    repeat(steps) {
        top *= numerator
        bottom *= denominator
    }
    return (top + bottom / 2) / bottom
}
