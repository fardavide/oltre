package dev.fardavide.oltre.core

// PLACEHOLDER balance numbers — every value here is provisional until decided on the Notion
// page or by Davide. This object is the single place placeholders live; never scatter literals.
object PlaceholderBalance {
    const val METAL_PRODUCTION_PER_HOUR: Long = 3_600
    const val CRYSTAL_PRODUCTION_PER_HOUR: Long = 1_800
    const val DEUTERIUM_PRODUCTION_PER_HOUR: Long = 900

    // Linear placeholder curve; the real curve comes from Notion or sim tuning.
    fun metalProductionPerHour(level: BuildingLevel): Long =
        METAL_PRODUCTION_PER_HOUR * level.value

    fun crystalProductionPerHour(level: BuildingLevel): Long =
        CRYSTAL_PRODUCTION_PER_HOUR * level.value

    fun deuteriumProductionPerHour(level: BuildingLevel): Long =
        DEUTERIUM_PRODUCTION_PER_HOUR * level.value
}
