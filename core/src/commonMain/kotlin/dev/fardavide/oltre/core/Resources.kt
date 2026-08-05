package dev.fardavide.oltre.core

// Stock is stored in fine units (1 metal = FINE_PER_UNIT fine) so that accruing
// `hourlyRate × elapsedMilliseconds` is exact integer arithmetic. Anything coarser truncates,
// and truncation breaks the advance-composability property the whole simulation rests on.
data class Resources internal constructor(
    internal val metalFine: Long,
) {
    val metal: Long get() = metalFine / FINE_PER_UNIT

    internal companion object {
        // Milliseconds per hour: fine units accrued per metal-per-hour of production per ms.
        internal const val FINE_PER_UNIT: Long = 3_600_000

        internal fun of(metal: Long): Resources = Resources(metalFine = metal * FINE_PER_UNIT)
    }
}
