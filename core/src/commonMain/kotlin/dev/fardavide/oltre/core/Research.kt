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

    // **The fourth, and the first whose payoff is not an hourly rate.** The other three multiply
    // something the colony produces while you are away; this one multiplies what a fleet pulls out of
    // a world it is standing on, so it is the only row on that screen a player cannot read off their
    // own production. It is what Davide asked for at 0.9 — *"power up my ship so it can gather more
    // resources in the same time"* — after the hold ceiling he first proposed was measured and
    // dropped.
    //
    // Note what it deliberately does **not** buy, because the sheet's §5 got this wrong once: it is
    // not relief from depletion. Where the binding constraint is a vein rather than a clock, a faster
    // hull drains the same vein sooner. What it buys is reach — the frontier at a shorter window.
    PROSPECTING,
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
//
// **Two branches, one record.** The three applied technologies multiply a per-hour rate; the three
// adaptation ladders widen a tolerance band and change nothing about production at all. They are
// separate enums, separate balance objects and — since 0.12.2 — separate slots on `GameState`, but
// they share this record, because what the empire knows is one thing however it was learned. That
// is the half of the sharing that survived: the levels are one fact, the queues are two.
@Serializable
data class Research(
    val photovoltaics: TechLevel,
    val extraction: TechLevel,
    val enrichment: TechLevel,
    val prospecting: TechLevel,
    val thermal: TechLevel,
    val gravitic: TechLevel,
    val atmospheric: TechLevel,
) {
    fun levelOf(technology: Technology): TechLevel = when (technology) {
        Technology.PHOTOVOLTAICS -> photovoltaics
        Technology.EXTRACTION -> extraction
        Technology.ENRICHMENT -> enrichment
        Technology.PROSPECTING -> prospecting
    }

    fun levelOf(technology: AdaptationTechnology): TechLevel = when (technology) {
        AdaptationTechnology.THERMAL -> thermal
        AdaptationTechnology.GRAVITIC -> gravitic
        AdaptationTechnology.ATMOSPHERIC -> atmospheric
    }

    fun withLevel(technology: Technology, level: TechLevel): Research = when (technology) {
        Technology.PHOTOVOLTAICS -> copy(photovoltaics = level)
        Technology.EXTRACTION -> copy(extraction = level)
        Technology.ENRICHMENT -> copy(enrichment = level)
        Technology.PROSPECTING -> copy(prospecting = level)
    }

    fun withLevel(technology: AdaptationTechnology, level: TechLevel): Research = when (technology) {
        AdaptationTechnology.THERMAL -> copy(thermal = level)
        AdaptationTechnology.GRAVITIC -> copy(gravitic = level)
        AdaptationTechnology.ATMOSPHERIC -> copy(atmospheric = level)
    }

    // The one bridge between the research branch and the map. `verdictFor` asks for this and for
    // nothing else about the empire, so a world's verdict is a function of what has been researched
    // and never of anything else the colony happens to be doing.
    fun adaptationLevels(): AdaptationLevels = AdaptationLevels(
        thermal = thermal.value,
        gravitic = gravitic.value,
        atmospheric = atmospheric.value,
    )

    companion object {
        fun initial(): Research = Research(
            photovoltaics = TechLevel(0),
            extraction = TechLevel(0),
            enrichment = TechLevel(0),
            prospecting = TechLevel(0),
            thermal = TechLevel(0),
            gravitic = TechLevel(0),
            atmospheric = TechLevel(0),
        )
    }
}

// One at a time, empire-wide — so unlike `builds` this is a single nullable record rather than a
// map. The slot is the applied branch's only scarcity: its resource costs are small next to a mine
// of the same era, so without it the answer would always be "start all four" and no decision is
// left. That argument is untouched by 0.12.2, which split the *branches* apart and neither branch
// within itself.
@Serializable
data class ResearchJob(
    val technology: Technology,
    val toLevel: TechLevel,
    val startedAt: Instant,
    val completesAt: Instant,
)

// The adaptation branch's job, and deliberately a separate type rather than a `ResearchJob` with a
// wider subject: the two branches are bought against different things — a colony you can watch,
// and a map you cannot — and a sealed subject would make every existing reader of `activeResearch`
// answer for a project it does not render. Two types for two slots since 0.12.2, which is the
// shape this always had; what it no longer needs is the `require` on `GameState` that used to make
// the second slot a fiction. See the 0.3 adaptation sheet, §2, and what overruled it.
@Serializable
data class AdaptationJob(
    val technology: AdaptationTechnology,
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
