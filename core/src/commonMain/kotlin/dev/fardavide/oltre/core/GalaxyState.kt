package dev.fardavide.oltre.core

import kotlinx.serialization.Serializable
import kotlin.jvm.JvmInline

// Who holds a world. In 0.2 the only empire is the player, because the three scripted empires are
// slice #9 — but `Occupied` has to carry a holder for the verdict to mean anything, and a holder
// that is a real identity now is one slice #9 populates rather than replaces.
@Serializable
@JvmInline
value class EmpireId(val value: String) {
    companion object {
        val PLAYER: EmpireId = EmpireId("player")
    }
}

// One world and who holds it. A record rather than a map entry because a `GalaxyCoordinate` is a
// structured key, which JSON cannot use as an object key at all — the alternative was to turn on
// `allowStructuredMapKeys` and have every ownership entry written as a flat [key, value] array,
// which changes how the *whole* save format encodes maps to buy an unreadable save.
@Serializable
data class WorldOwnership(val at: GalaxyCoordinate, val holder: EmpireId)

// **The galaxy is never serialised.** 4,700 worlds of traits would dwarf the entire rest of the
// snapshot, so what the save carries is the seed plus what the player has changed: which worlds
// they have surveyed, and who holds what. Every trait is regenerated from the seed on demand — see
// `GalaxyGeneration.kt`, and the locality property that makes it affordable.
@Serializable
data class GalaxyState(
    val seed: GalaxySeed,
    val home: GalaxyCoordinate,
    // Per world rather than per system, because surveying is a per-world fleet action from slice #7
    // onwards. At genesis this holds the worlds of the home system and nothing else, so almost
    // everything on the screen reads `Unsurveyed` — the honest state, not a placeholder.
    val surveyed: Set<GalaxyCoordinate>,
    val ownership: List<WorldOwnership>,
) {
    init {
        require(ownership.distinctBy { it.at }.size == ownership.size) {
            "a world cannot have two holders, was $ownership"
        }
        require(holderOf(home) == EmpireId.PLAYER) {
            "the home world must be held by the player, was ${holderOf(home)}"
        }
    }

    // Linear over a list that holds one entry in 0.2 and a handful once slice #9 lands three
    // scripted empires. If it ever holds enough to matter, the fix is an index built here, not a
    // map on disk.
    fun holderOf(at: GalaxyCoordinate): EmpireId? = ownership.firstOrNull { it.at == at }?.holder

    // Whether a probe would learn anything there. Asked of the *occupied* slots only, because an
    // empty slot has nothing to know — so a system whose worlds are all known is complete even
    // though eleven of its fifteen slots are absent from the set.
    fun hasSurveyed(system: SystemAddress): Boolean =
        occupiedWorldsIn(seed, system).all { it in surveyed }

    companion object {

        // Genesis. The seed comes from outside core — the shell mints it, because core cannot read
        // a clock or a random source — and everything else about the map follows from it.
        fun initial(seed: GalaxySeed): GalaxyState {
            val home = homeFor(seed)
            return GalaxyState(
                seed = seed,
                home = home,
                surveyed = occupiedWorldsIn(seed, SystemAddress.of(home)),
                ownership = listOf(WorldOwnership(at = home, holder = EmpireId.PLAYER)),
            )
        }

        // Every slot of a system that actually holds a world. Genesis surveys the home system with
        // it — you can see your own neighbours, and nothing else — and a landing probe surveys its
        // target with the same call. That those two are one function is not a tidy-up: the set a
        // survey writes has to be exactly the set the player already has for their own system, or
        // "surveyed" would quietly mean two different things depending on how it got there.
        //
        // Only occupied slots are recorded, so the set stays the handful of entries a system really
        // has rather than a fixed fifteen.
        internal fun occupiedWorldsIn(seed: GalaxySeed, system: SystemAddress): Set<GalaxyCoordinate> =
            (1..GalaxyBalance.SLOTS_PER_SYSTEM)
                .map { slot -> GalaxyCoordinate(galaxy = system.galaxy, system = system.system, slot = slot) }
                .filter { at -> worldAt(seed, at) != null }
                .toSet()
    }
}
