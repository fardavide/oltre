package dev.fardavide.oltre.core

// Stock is stored in fine units (1 resource = FINE_PER_UNIT fine) so that accruing
// `hourlyRate × elapsedMilliseconds` is exact integer arithmetic. Anything coarser truncates,
// and truncation breaks the advance-composability property the whole simulation rests on.
data class Resources internal constructor(
    internal val metalFine: Long,
    internal val crystalFine: Long,
    internal val deuteriumFine: Long,
) {
    fun covers(other: Resources): Boolean =
        metalFine >= other.metalFine && crystalFine >= other.crystalFine && deuteriumFine >= other.deuteriumFine

    operator fun minus(other: Resources): Resources = Resources(
        metalFine = metalFine - other.metalFine,
        crystalFine = crystalFine - other.crystalFine,
        deuteriumFine = deuteriumFine - other.deuteriumFine,
    )

    val metal: Long get() = metalFine / FINE_PER_UNIT
    val crystal: Long get() = crystalFine / FINE_PER_UNIT
    val deuterium: Long get() = deuteriumFine / FINE_PER_UNIT

    companion object {
        // Milliseconds per hour: fine units accrued per resource-per-hour of production per ms.
        internal const val FINE_PER_UNIT: Long = 3_600_000

        // Guards the fine-unit conversion: a Long overflow here would wrap into a negative
        // stock/cost, which covers() would then treat as free.
        private const val MAX_WHOLE_UNITS: Long = Long.MAX_VALUE / FINE_PER_UNIT

        fun of(metal: Long = 0, crystal: Long = 0, deuterium: Long = 0): Resources {
            require(metal in 0..MAX_WHOLE_UNITS) { "metal out of range: $metal" }
            require(crystal in 0..MAX_WHOLE_UNITS) { "crystal out of range: $crystal" }
            require(deuterium in 0..MAX_WHOLE_UNITS) { "deuterium out of range: $deuterium" }
            return Resources(
                metalFine = metal * FINE_PER_UNIT,
                crystalFine = crystal * FINE_PER_UNIT,
                deuteriumFine = deuterium * FINE_PER_UNIT,
            )
        }
    }
}
