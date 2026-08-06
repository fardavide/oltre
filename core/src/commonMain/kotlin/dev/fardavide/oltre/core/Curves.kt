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
