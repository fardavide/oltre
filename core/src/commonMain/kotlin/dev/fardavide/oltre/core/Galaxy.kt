package dev.fardavide.oltre.core

import kotlinx.serialization.Serializable
import kotlin.jvm.JvmInline

// The galaxy's model. Only the two types the save actually stores — `GalaxySeed` and
// `GalaxyCoordinate` — are `@Serializable`; a `World` deliberately is not, because the galaxy is
// never written to disk. 4,700 worlds of traits would dwarf the entire rest of the snapshot, so the
// save carries the seed and what the player changed, and every world is regenerated on demand. See
// `GalaxyGeneration.kt`.
//
// **Every scalar here is an integer in a named unit**, not a Double, for the reason the rest of
// core is integer: `advance` has to give the same answer on the JVM, on Kotlin/Native and on the
// server, and a generated galaxy that differs by one unit between platforms is a different map. The
// axes are scaled so the sheet's formulas stay exact — milli-g and milli-atm — and temperature
// needs no scale because the sheet's formula is already whole degrees.

@Serializable
@JvmInline
value class GalaxySeed(val value: Long)

// galaxy : system : slot — the mockup's `2 : 118` with the slot number under it. Bounded rather
// than merely positive, so a coordinate off the edge of the map cannot be constructed at all; the
// generator therefore never has to answer for one.
@Serializable
data class GalaxyCoordinate(
    val galaxy: Int,
    val system: Int,
    val slot: Int,
) {
    init {
        require(galaxy in 1..GalaxyBalance.GALAXIES) {
            "galaxy must be between 1 and ${GalaxyBalance.GALAXIES}, was $galaxy"
        }
        require(system in 1..GalaxyBalance.SYSTEMS_PER_GALAXY) {
            "system must be between 1 and ${GalaxyBalance.SYSTEMS_PER_GALAXY}, was $system"
        }
        require(slot in 1..GalaxyBalance.SLOTS_PER_SYSTEM) {
            "slot must be between 1 and ${GalaxyBalance.SLOTS_PER_SYSTEM}, was $slot"
        }
    }
}

// A property of the star, so every slot in a system shares it — which is why it is drawn from the
// system's stream rather than the world's.
enum class StarClass { DIM, STANDARD, BRIGHT }

// Not an axis, deliberately: a hazard is about what happens *over time* where the three axes are
// about what the world *is*. As a fourth bar it would be a number the player cannot act on until
// colonisation exists; as words it makes a world memorable in one line, and gives slice #10 its
// content.
enum class Hazard {
    TIDALLY_LOCKED,
    ION_STORMS,
    SEISMIC_INSTABILITY,
    THIN_CRUST,
    RADIATION_BELT,
}

// Enum names are on-disk identifiers in every save from schema 5 onwards — an `AdaptationJob` names
// one — so adding a constant is free and renaming one is a schema break, exactly as for `Technology`.
@Serializable
enum class AdaptationTechnology { THERMAL, GRAVITIC, ATMOSPHERIC }

// The sheet's central table, in the type system: every axis that makes a world hostile is the axis
// that makes it rich, and each one has its own adaptation ladder. Three ladders rather than one is
// what makes *which one you push first* a real choice — a single habitability score would collapse
// the three into a ladder, and a ladder contains no decision.
enum class HostilityAxis(val richResource: ResourceKind, val adaptation: AdaptationTechnology) {
    TEMPERATURE(ResourceKind.DEUTERIUM, AdaptationTechnology.THERMAL),
    GRAVITY(ResourceKind.METAL, AdaptationTechnology.GRAVITIC),
    PRESSURE(ResourceKind.CRYSTAL, AdaptationTechnology.ATMOSPHERIC),
}

@JvmInline
value class Temperature(val celsius: Int)

@JvmInline
value class Gravity(val milliG: Int)

@JvmInline
value class Pressure(val milliAtm: Int)

// How much of a resource a world yields against home, in parts of `GalaxyBalance.RICHNESS_BASIS`:
// 1_000_000 is "as good as home". Clamped by the balance to 0.6 … 1.6.
@JvmInline
value class Richness(val perMillion: Int)

// The three richnesses weighted by what the reference colony's output is actually worth, minus the
// hazard penalty. Same basis as `Richness`, and compared against `GalaxyBalance.WORTH_IT_THRESHOLD`.
@JvmInline
value class YieldScore(val perMillion: Int)

// What a survey reveals. Everything here is derived from the world's own seed and nothing else, so
// two sessions generating the same coordinate get the same traits without sharing any state.
data class WorldTraits(
    val temperature: Temperature,
    val gravity: Gravity,
    val pressure: Pressure,
    val metalRichness: Richness,
    val crystalRichness: Richness,
    val deuteriumRichness: Richness,
    val hazards: Set<Hazard>,
    // How many building levels this world could ever hold. GENERATED AND STORED, AND NOTHING READS
    // IT IN 0.2 — it is slice #10's input, and saying so here is better than wiring it to something
    // to look busy.
    val fields: Int,
)

data class World(
    val at: GalaxyCoordinate,
    val starClass: StarClass,
    val traits: WorldTraits,
)
