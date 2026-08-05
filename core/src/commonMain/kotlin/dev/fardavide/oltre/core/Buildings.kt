package dev.fardavide.oltre.core

import kotlin.jvm.JvmInline

@JvmInline
value class BuildingLevel(val value: Int) {
    init {
        require(value >= 0) { "building level must be non-negative, was $value" }
    }
}

data class Buildings(
    val metalMine: BuildingLevel,
    val crystalMine: BuildingLevel,
    val deuteriumSynthesizer: BuildingLevel,
    val solarPlant: BuildingLevel,
    val roboticsFactory: BuildingLevel,
    val naniteFactory: BuildingLevel,
) {
    fun levelOf(building: BuildingType): BuildingLevel = when (building) {
        BuildingType.METAL_MINE -> metalMine
        BuildingType.CRYSTAL_MINE -> crystalMine
        BuildingType.DEUTERIUM_SYNTHESIZER -> deuteriumSynthesizer
        BuildingType.SOLAR_PLANT -> solarPlant
        BuildingType.ROBOTICS_FACTORY -> roboticsFactory
        BuildingType.NANITE_FACTORY -> naniteFactory
    }

    fun withLevel(building: BuildingType, level: BuildingLevel): Buildings = when (building) {
        BuildingType.METAL_MINE -> copy(metalMine = level)
        BuildingType.CRYSTAL_MINE -> copy(crystalMine = level)
        BuildingType.DEUTERIUM_SYNTHESIZER -> copy(deuteriumSynthesizer = level)
        BuildingType.SOLAR_PLANT -> copy(solarPlant = level)
        BuildingType.ROBOTICS_FACTORY -> copy(roboticsFactory = level)
        BuildingType.NANITE_FACTORY -> copy(naniteFactory = level)
    }

    companion object {
        fun initial(): Buildings = Buildings(
            metalMine = BuildingLevel(1),
            crystalMine = BuildingLevel(1),
            deuteriumSynthesizer = BuildingLevel(1),
            solarPlant = BuildingLevel(1),
            roboticsFactory = BuildingLevel(0),
            naniteFactory = BuildingLevel(0),
        )
    }
}
