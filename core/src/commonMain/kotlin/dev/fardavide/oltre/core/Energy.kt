package dev.fardavide.oltre.core

// The colony's power situation at a given set of building levels.
//
// Energy is deliberately not a resource: it never accumulates, so it is never stocked, never
// enters the event log, and is never advanced over time. It is derived fresh from the buildings
// wherever it is needed — which is why it is a value here rather than a field on GameState.
//
// It exists as a named type because a deficit silently scales every mine's output, and a rule
// that invisible has to be something the UI can hold and show rather than a ratio each caller
// re-derives.
data class EnergyBalance(val produced: Long, val consumed: Long) {

    val isDeficit: Boolean get() = produced < consumed

    val surplus: Long get() = produced - consumed

    // The headline percentage of full output the mines run at. A colony consuming nothing runs
    // at 100 by definition, which is also what keeps the division safe. Individual rates floor
    // independently of this number (whole-unit accrual has to stay exact), so a mine can land a
    // unit either side of it — this is the figure to show, not to compute an accrual from.
    val outputPercent: Int get() = if (!isDeficit) 100 else (produced * 100 / consumed).toInt()
}
