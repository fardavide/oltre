package dev.fardavide.oltre.core

import kotlin.jvm.JvmInline

@JvmInline
value class BuildingLevel(val value: Int)

data class Buildings(
    val metalMine: BuildingLevel,
    val crystalMine: BuildingLevel,
    val deuteriumSynthesizer: BuildingLevel,
) {
    companion object {
        fun initial(): Buildings = Buildings(
            metalMine = BuildingLevel(1),
            crystalMine = BuildingLevel(1),
            deuteriumSynthesizer = BuildingLevel(1),
        )
    }
}
