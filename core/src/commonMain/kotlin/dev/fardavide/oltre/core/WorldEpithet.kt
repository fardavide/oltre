package dev.fardavide.oltre.core

// **Three numbers become something you can say out loud.** "iron giant", "frozen waste", "scorched
// shroud" — and the point of it is the list you *scan*: a column of coordinates and verdicts reads
// as uniform however varied the worlds behind it are, which is most of what "the map has no
// identity" means. `galaxy-identity-sheet.md` §3.2.
//
// **Derived, never rolled**, in the same spirit as richness: an epithet is a function of the three
// axes and of nothing else, so it cannot disagree with the readings printed beside it. That is what
// makes it safe to put a *word* on a row where the app otherwise only puts measurements.
//
// **Two words rather than a sentence, and the pair is not ordered here.** English says "veiled
// furnace" and Italian says "fornace velata" — noun first, adjective agreeing with it — so which
// word comes first is the catalogue's decision and not this file's. What `core` knows is *which*
// adjective and *which* noun, which is why both are enums since 0.14.0 and were strings before it.
//
// Two decisions worth naming:
//
// 1. **It is measured against the level-0 tolerance bands**, not the player's current ones, so a
//    world does not stop being an iron giant because its owner bought a Gravitic level. What a
//    world *is* cannot depend on who is looking at it.
// 2. **Hazards are not in it.** They already carry their own line, with their own arithmetic, and
//    an epithet that folded them in would be a second place the same fact is stated — on the row
//    that can least afford one.
data class WorldEpithet(val adjective: EpithetAdjective, val noun: EpithetNoun)

// What a world *is*, in one word: the axis it is most extreme on, and which end of that axis.
//
// **PROPOSED VOCABULARY, NOT DECIDED** — the mechanism is settled and these are the build's words;
// `galaxy-identity-sheet.md` §9 carries the vocabulary as Davide's open call, because it is content
// rather than balance. The English *word* now lives in `:client:design:text` with every other word
// the game says; what survives here is the *distinction*, which is what the balance depends on and
// what a language cannot change. Renaming a constant here changes no behaviour and breaks no save.
enum class EpithetNoun {
    FURNACE,
    FROST,
    GIANT,
    HUSK,
    SHROUD,
    WASTE,

    // The world inside every band, which has no extreme to be named for. It is 1.5% of the map and
    // the only kind a settler can take.
    WORLD,
}

// The second word, from the axis a world is second most extreme on — or, when there is no second
// axis, from that axis's own reserve list. See `soleAdjectiveFor` for why the reserve exists.
enum class EpithetAdjective {
    SCORCHED,
    FROZEN,
    IRON,
    HOLLOW,
    VEILED,
    AIRLESS,
    ASHEN,
    DEEP,
    BRITTLE,
    DROWNED,
    BARE,
    TEMPERATE,
}

private fun nounFor(axis: HostilityAxis, above: Boolean): EpithetNoun = when (axis) {
    HostilityAxis.TEMPERATURE -> if (above) EpithetNoun.FURNACE else EpithetNoun.FROST
    HostilityAxis.GRAVITY -> if (above) EpithetNoun.GIANT else EpithetNoun.HUSK
    HostilityAxis.PRESSURE -> if (above) EpithetNoun.SHROUD else EpithetNoun.WASTE
}

private fun adjectiveFor(axis: HostilityAxis, above: Boolean): EpithetAdjective = when (axis) {
    HostilityAxis.TEMPERATURE -> if (above) EpithetAdjective.SCORCHED else EpithetAdjective.FROZEN
    HostilityAxis.GRAVITY -> if (above) EpithetAdjective.IRON else EpithetAdjective.HOLLOW
    HostilityAxis.PRESSURE -> if (above) EpithetAdjective.VEILED else EpithetAdjective.AIRLESS
}

// **A second adjective per axis, for the world that is extreme on that axis and nothing else.**
// Reusing the list above for both words was the first cut and it stuttered on 10% of the galaxy —
// `frozen frost`, `hollow husk`, `veiled shroud`. The two lists are disjoint as *constants*, which
// is what that draft checked, and identical in *sense*, which is what a reader sees.
private fun soleAdjectiveFor(axis: HostilityAxis, above: Boolean): EpithetAdjective = when (axis) {
    HostilityAxis.TEMPERATURE -> if (above) EpithetAdjective.ASHEN else EpithetAdjective.DEEP
    HostilityAxis.GRAVITY -> if (above) EpithetAdjective.IRON else EpithetAdjective.BRITTLE
    HostilityAxis.PRESSURE -> if (above) EpithetAdjective.DROWNED else EpithetAdjective.BARE
}

// How far outside its band a world sits on one axis, in hundredths of that band's own width — which
// is what makes three axes in three different units comparable at all. Zero when the world is
// inside the band, so "how many axes is this world extreme on" is just a count of the non-zero ones.
private data class AxisExtremity(val axis: HostilityAxis, val above: Boolean, val magnitude: Int)

private fun extremityOf(traits: WorldTraits, axis: HostilityAxis): AxisExtremity {
    val band = GalaxyBalance.tolerance(AdaptationLevels.NONE).bandOf(axis)
    val value = traits.axisValue(axis)
    val width = band.max - band.min
    return when {
        value < band.min -> AxisExtremity(axis, above = false, magnitude = (band.min - value) * 100 / width)
        value > band.max -> AxisExtremity(axis, above = true, magnitude = (value - band.max) * 100 / width)
        else -> AxisExtremity(axis, above = false, magnitude = 0)
    }
}

fun epithetFor(traits: WorldTraits): WorldEpithet {
    // Ties break in `HostilityAxis` order, which is the order every other three-axis reading in the
    // app already uses — so two worlds equally extreme on two axes are described the same way round.
    val extremes = HostilityAxis.entries
        .map { extremityOf(traits, it) }
        .filter { it.magnitude > 0 }
        .sortedWith(compareByDescending<AxisExtremity> { it.magnitude }.thenBy { it.axis.ordinal })

    // Inside every band: about 1.5% of the map, and the only worlds a settler can take. Naming them
    // for what they are beats naming them for an extreme they do not have — and it is the one
    // epithet a player should be glad to read.
    val dominant = extremes.firstOrNull()
        ?: return WorldEpithet(adjective = EpithetAdjective.TEMPERATE, noun = EpithetNoun.WORLD)

    // With nothing to borrow an adjective from, the dominant axis supplies both words — from the
    // second table, which exists precisely so that it does not say the same thing twice.
    val secondary = extremes.getOrNull(1)

    return WorldEpithet(
        adjective = when (secondary) {
            null -> soleAdjectiveFor(dominant.axis, dominant.above)
            else -> adjectiveFor(secondary.axis, secondary.above)
        },
        noun = nounFor(dominant.axis, dominant.above),
    )
}
