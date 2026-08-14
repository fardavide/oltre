package dev.fardavide.oltre.client.galaxy.presentation

import dev.fardavide.oltre.client.design.format.toChipLabel
import dev.fardavide.oltre.client.galaxy.ui.RegionRowUiState
import dev.fardavide.oltre.client.galaxy.ui.RegionTickUiState
import dev.fardavide.oltre.core.FleetBalance
import dev.fardavide.oltre.core.GalaxyBalance
import dev.fardavide.oltre.core.GalaxyCoordinate
import dev.fardavide.oltre.core.GameState
import dev.fardavide.oltre.core.RegionTemperament
import dev.fardavide.oltre.core.regionNameAt
import dev.fardavide.oltre.core.regionOf
import dev.fardavide.oltre.core.starClassAt
import dev.fardavide.oltre.core.temperamentOf

// **Ten rows against a thousand pages.** Where you decide where to probe next — the one decision the
// map exists for and the one it has never helped with.
//
// Sorted nearest first rather than by coordinate: the index is a chooser, and coordinate order is
// what the strip is for.
internal fun GameState.toRegionRows(galaxy: Int): List<RegionRowUiState> =
    (1..GalaxyBalance.REGIONS_PER_GALAXY)
        .map { region -> toRegionRow(galaxy = galaxy, region = region) }
        .sortedBy { it.nearestMinutes }
        .map { it.row }

private data class SortableRegion(val row: RegionRowUiState, val nearestMinutes: Long)

private fun GameState.toRegionRow(galaxy: Int, region: Int): SortableRegion {
    val first = (region - 1) * GalaxyBalance.SYSTEMS_PER_REGION + 1
    val last = first + GalaxyBalance.SYSTEMS_PER_REGION - 1
    val temperament = temperamentOf(this.galaxy.seed, galaxy, region)
    val home = this.galaxy.home
    val systems = first..last

    val nearest = systems.minOf { system ->
        FleetBalance.roundTrip(
            from = home,
            to = GalaxyCoordinate(galaxy = galaxy, system = system, slot = 1),
        )
    }
    // **Systems, not worlds**, because every other reading on this row is per system — the 25-tick
    // histogram, the range, the nearest. Counting worlds made the home region read "4 surveyed" at
    // genesis, when exactly one of its twenty-five systems has ever been looked at.
    val surveyed = this.galaxy.surveyed
        .filter { it.galaxy == galaxy && it.system in systems }
        .distinctBy { it.system }
        .size
    val isHome = galaxy == home.galaxy && regionOf(home.system) == region

    return SortableRegion(
        nearestMinutes = nearest.inWholeMinutes,
        row = RegionRowUiState(
            region = region,
            name = regionNameAt(this.galaxy.seed, galaxy, region),
            // An en dash, because it is a range rather than a subtraction.
            range = "$galaxy:$first–$last",
            histogram = systems.map { system ->
                RegionTickUiState(
                    system = system,
                    starClass = starClassAt(this.galaxy.seed, galaxy, system),
                    isHome = isHome && system == home.system,
                )
            },
            bias = temperament.bias(),
            fact = temperament.fact(),
            known = if (surveyed == 0) "none surveyed" else "$surveyed of 25 surveyed",
            nearest = "nearest ${nearest.toChipLabel()}",
            isHome = isHome,
        ),
    )
}

// **The one reading on this screen that is free.** Star class is charted, so a region announces its
// bias before a probe has ever been sent — which is what makes the whole index worth opening.
private fun RegionTemperament.bias(): String {
    val mix = GalaxyBalance.starMix(this)
    return when (this) {
        RegionTemperament.DEEP -> "${mix.dimPercent}% dim"
        RegionTemperament.SETTLED -> "even mix"
        RegionTemperament.BURNING -> "${mix.brightPercent}% bright"
    }
}

// **Five words that are true from the first launch**, and the reason a region is a place rather than
// a range of numbers. A dim star is −40 °C and a bright one +40 °C against a fall of 28 °C per orbit,
// so the tolerable orbits really do move about three slots between a Deep and a Blaze — and the
// deuterium goes with them, because richness is derived from temperature.
private fun RegionTemperament.fact(): String = when (this) {
    RegionTemperament.DEEP -> "settle close in · deuterium good"
    RegionTemperament.SETTLED -> "no orbit bias"
    RegionTemperament.BURNING -> "settle far out · deuterium poor"
}
