package dev.fardavide.oltre.core

import kotlinx.serialization.Serializable
import kotlin.jvm.JvmInline
import kotlin.time.Instant

// Enum names are on-disk identifiers in every save from schema 3 onwards; adding a constant is
// free, renaming one is a schema break.
//
// Three, flat, behind one shared gate — the 0.1 decision sheet's answer to "one research branch".
// A linear chain contains no decision (the player researches the next thing because it is the
// next thing); three rows behind a gate means every time the slot frees, the question is which of
// three, and that question has a different answer on day 4 than on day 11. Automation, the
// build-speed technology, is fully specced in the sheet and deliberately deferred past 0.1.
@Serializable
enum class Technology {
    PHOTOVOLTAICS,
    EXTRACTION,
    ENRICHMENT,
}

@Serializable
@JvmInline
value class TechLevel(val value: Int) {
    init {
        require(value in 0..MAX) { "tech level must be between 0 and $MAX, was $value" }
    }

    companion object {

        // The deepest level the cost curve is defined for. Enforced by the model rather than only
        // by the balance — unlike `BuildingLevel`, whose bound lives in `PlaceholderBalance` — so
        // a hand-edited save carrying an impossible level fails to decode instead of overflowing
        // inside `advance`. Both research curves are exponential in the level (the cost
        // multiplies by 3 per step, the effect by up to 57), and either wraps negative past Long;
        // a negative cost is one `covers()` reads as free.
        const val MAX: Int = 30
    }
}

// The mirror of `Buildings`, and deliberately shaped the same way: named fields rather than a map,
// so a technology that exists always has a level and the exhaustive `when`s below are the only
// place the set is enumerated.
@Serializable
data class Research(
    val photovoltaics: TechLevel,
    val extraction: TechLevel,
    val enrichment: TechLevel,
) {
    fun levelOf(technology: Technology): TechLevel = when (technology) {
        Technology.PHOTOVOLTAICS -> photovoltaics
        Technology.EXTRACTION -> extraction
        Technology.ENRICHMENT -> enrichment
    }

    fun withLevel(technology: Technology, level: TechLevel): Research = when (technology) {
        Technology.PHOTOVOLTAICS -> copy(photovoltaics = level)
        Technology.EXTRACTION -> copy(extraction = level)
        Technology.ENRICHMENT -> copy(enrichment = level)
    }

    companion object {
        fun initial(): Research = Research(
            photovoltaics = TechLevel(0),
            extraction = TechLevel(0),
            enrichment = TechLevel(0),
        )
    }
}

// One at a time, empire-wide — so unlike `builds` this is a single nullable record rather than a
// map. The slot is research's only scarcity: its resource costs are small next to a mine of the
// same era, so without it the answer would always be "start all three" and no decision is left.
@Serializable
data class ResearchJob(
    val technology: Technology,
    val toLevel: TechLevel,
    val startedAt: Instant,
    val completesAt: Instant,
)

// What a technology wants before it can be started at all. Two shapes, because the branch opens on
// a building the game already gates deuterium behind, and then opens up internally on itself.
sealed interface ResearchRequirement {

    data class Facility(val building: BuildingType, val level: BuildingLevel) : ResearchRequirement

    data class Tech(val technology: Technology, val level: TechLevel) : ResearchRequirement

    fun isMetBy(state: GameState): Boolean = when (this) {
        is Facility -> state.buildings.levelOf(building).value >= level.value
        is Tech -> state.research.levelOf(technology).value >= level.value
    }
}
