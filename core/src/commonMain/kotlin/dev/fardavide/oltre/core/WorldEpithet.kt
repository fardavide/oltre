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
// Two decisions worth naming:
//
// 1. **It is measured against the level-0 tolerance bands**, not the player's current ones, so a
//    world does not stop being an iron giant because its owner bought a Gravitic level. What a
//    world *is* cannot depend on who is looking at it.
// 2. **Hazards are not in it.** They already carry their own line, with their own arithmetic, and
//    an epithet that folded them in would be a second place the same fact is stated — on the row
//    that can least afford one.
data class WorldEpithet(val adjective: String, val noun: String) {
    override fun toString(): String = "$adjective $noun"
}

// PROPOSED VOCABULARY, NOT DECIDED. The mechanism is settled and these are the build's words;
// `galaxy-identity-sheet.md` §9 carries the vocabulary as Davide's open call, because it is content
// rather than balance. Changing a word here changes no behaviour and breaks no save.
private fun nounFor(axis: HostilityAxis, above: Boolean): String = when (axis) {
    HostilityAxis.TEMPERATURE -> if (above) "furnace" else "frost"
    HostilityAxis.GRAVITY -> if (above) "giant" else "husk"
    HostilityAxis.PRESSURE -> if (above) "shroud" else "waste"
}

private fun adjectiveFor(axis: HostilityAxis, above: Boolean): String = when (axis) {
    HostilityAxis.TEMPERATURE -> if (above) "scorched" else "frozen"
    HostilityAxis.GRAVITY -> if (above) "iron" else "hollow"
    HostilityAxis.PRESSURE -> if (above) "veiled" else "airless"
}

// **A second adjective per axis, for the world that is extreme on that axis and nothing else.**
// Reusing the list above for both words was the first cut and it stuttered on 10% of the galaxy —
// `frozen frost`, `hollow husk`, `veiled shroud`. The two lists are disjoint as *strings*, which is
// what that draft checked, and identical in *sense*, which is what a reader sees.
private fun soleAdjectiveFor(axis: HostilityAxis, above: Boolean): String = when (axis) {
    HostilityAxis.TEMPERATURE -> if (above) "ashen" else "deep"
    HostilityAxis.GRAVITY -> if (above) "iron" else "brittle"
    HostilityAxis.PRESSURE -> if (above) "drowned" else "bare"
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
    val dominant = extremes.firstOrNull() ?: return WorldEpithet(adjective = "temperate", noun = "world")

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
