package dev.fardavide.oltre.client.galaxy.presentation

import dev.fardavide.oltre.core.GalaxyBalance
import dev.fardavide.oltre.core.GalaxyCoordinate
import dev.fardavide.oltre.core.GalaxySeed
import dev.fardavide.oltre.core.GameState
import dev.fardavide.oltre.core.StarClass
import dev.fardavide.oltre.core.SurveyBalance
import dev.fardavide.oltre.core.SystemAddress
import dev.fardavide.oltre.core.starClassAt
import dev.fardavide.oltre.core.worldAt

// **The stepper was a lens one system wide.** Widen it until it holds the galaxy and the browsing
// surface falls out — which is the answer to the question that actually threatened the 5–10 minute
// rule: ±1 is 249 taps to cross a galaxy and 999 to cross the map.
//
// The tap count was only the symptom. The disease was that the *charted* tier — free, galaxy-wide,
// known from the first launch — had no surface at all. A player short of deuterium has a real
// reason to prefer a dim star, and before this the only way to act on it was to step one system at
// a time reading the header. A jump-to-coordinate box would have fixed the taps and left that
// exactly as it was; it also presumes you already know the number, which is the one thing the
// charted tier does not give you.
//
// **The axis is hours, not coordinates, and that is the whole design.** A dispatch costs 150 metal
// wherever it goes, so the only thing the player is choosing is a duration — "what can I reach in
// the nine hours I am about to be asleep" is the only question the screen is asked, and a ruler is
// the only control that answers it.
data class ReachBandUiState(
    // All 250, never a window on them: the strip is the price list and its order is the price.
    val ticks: List<ReachTickUiState>,
    val marks: List<ReachMarkUiState>,
    val lens: ReachLensUiState,
    // Five cells rather than seven. The cell size holds and the count drops, so a target is never
    // under 44dp — the same rule that drops a word rather than shrinking a figure.
    val compactLens: ReachLensUiState,
)

data class ReachTickUiState(val system: Int, val mark: ReachTick)

// Star class in height and alpha, and the two things that are yours in colour. Nothing else on the
// strip is coloured, so your star and your probe are findable in a field of 250 without a legend.
enum class ReachTick {
    DIM,
    STANDARD,
    BRIGHT,

    // Your star.
    ORIGIN,

    // The index your flights are measured from while you are looking at another galaxy. It is not
    // your star, so it does not take the accent — a hop is priced at a whole galaxy plus the
    // distance from your own index, which is why the mark is at that index and not at your home.
    FOREIGN_ORIGIN,

    // A probe in flight, on its target. Amber rather than accent: inside its own system it is a job
    // running and the card is doing something; out here it is a thing of yours crossing space,
    // which is what the fleet strip's amber has always meant. This is the first object in the app
    // that is both at once — see `decisions.md`.
    PROBE,
}

// An hour of flight, at the system where a flight first costs it. Symmetric about the origin
// because distance is; the *galaxy* is not symmetric about you, and the ruler says so without a
// word of copy — from system 165 the left edge is 3h 14m out and the right edge 1h 55m.
data class ReachMarkUiState(val system: Int, val label: String)

data class ReachLensUiState(val firstSystem: Int, val cells: List<ReachCellUiState>)

// One system: its number, and a dot for what it holds. **No time.** Seven neighbours differ by one
// minute each, and printing "1h 21m" through "1h 27m" across the lens is seven near-identical
// figures where the ruler has already answered the question and the footer answers it exactly.
data class ReachCellUiState(
    val system: Int,
    val label: String,
    val dot: ReachDotUiState,
    val selected: Boolean,
)

sealed interface ReachDotUiState {

    data object Home : ReachDotUiState

    // A star with nothing around it. Drawn as the smallest mark there is rather than as no mark,
    // because "nothing there" is a fact the strip already knows for free and is worth one pixel.
    data object Empty : ReachDotUiState

    // Sized by the count and lit by the class. This is the one number on the band that costs
    // anything to know — fifteen world generations a system — which is why the lens counts seven
    // and the strip counts none. A whole-galaxy count would be 3,750 generations for a second
    // channel on a 1.4dp mark, encoding a figure the player cannot act on until after they have
    // paid for the survey.
    data class Worlds(val count: Int, val starClass: StarClass) : ReachDotUiState
}

internal const val LENS_CELLS: Int = 7
internal const val COMPACT_LENS_CELLS: Int = 5

internal fun GameState.toReachBandUiState(at: SystemSelection): ReachBandUiState {
    val seed = galaxy.seed
    val home = SystemAddress.of(galaxy.home)
    val isHomeGalaxy = at.galaxy == home.galaxy
    // Where flights into this galaxy are measured from. In your own galaxy that is your star; in
    // another it is the same *index*, because `SurveyBalance.distanceUnits` prices a hop as a whole
    // galaxy plus the difference of the two system numbers.
    val origin = home.system
    val inFlight = surveys.filter { it.target.galaxy == at.galaxy }.map { it.target.system }.toSet()

    return ReachBandUiState(
        ticks = (1..GalaxyBalance.SYSTEMS_PER_GALAXY).map { system ->
            ReachTickUiState(
                system = system,
                // Your marks win over the class underneath them, which is the point of having only
                // two of them: a tick you are looking for must not be a tick you have to find.
                //
                // **A probe outranks the foreign origin and not your own star**, and the asymmetry
                // is the whole reason the order is written out. Your star can never carry a probe —
                // `startSurvey` refuses a system you have already surveyed — so nothing is lost by
                // putting `ORIGIN` first. The foreign origin is not a star of yours at all, it is
                // the *index* a hop is measured from; letting it win would delete the amber tick of
                // a probe aimed at that index in another galaxy, and it is not a coincidence that
                // wants one system in 250: switching galaxy keeps the system number, so the very
                // first thing a player sees over there is the index their own home sits at.
                mark = when {
                    system == origin && isHomeGalaxy -> ReachTick.ORIGIN
                    system in inFlight -> ReachTick.PROBE
                    system == origin -> ReachTick.FOREIGN_ORIGIN
                    else -> starClassAt(seed, at.galaxy, system).toTick()
                },
            )
        },
        marks = marksFrom(origin = origin, home = home, galaxy = at.galaxy),
        lens = lensAt(seed = seed, at = at, home = home, cells = LENS_CELLS),
        compactLens = lensAt(seed = seed, at = at, home = home, cells = COMPACT_LENS_CELLS),
    )
}

// Derived from `SurveyBalance` rather than from a table, so the axis and the price the footer
// charges cannot disagree. A flight is `BASE + distance` minutes, so the hour mark `h` sits
// `h × 60 − base` systems out — and `base` already carries the galaxy hop, which is why crossing to
// a neighbour renumbers the ruler instead of breaking it: the first mark over there is 5h, and the
// cheapest flight into the next galaxy is longer than the longest flight inside your own.
private fun marksFrom(origin: Int, home: SystemAddress, galaxy: Int): List<ReachMarkUiState> {
    val here = SystemAddress(galaxy = galaxy, system = origin)
    val base = SurveyBalance.duration(from = home, to = here).inWholeMinutes.toInt()
    return (1..MAX_MARKED_HOURS).flatMap { hour ->
        val away = hour * MINUTES_PER_HOUR - base
        if (away < 0) return@flatMap emptyList()
        listOf(origin - away, origin + away)
            .distinct()
            .filter { it in 1..GalaxyBalance.SYSTEMS_PER_GALAXY }
            .map { ReachMarkUiState(system = it, label = "${hour}h") }
    }
}

// Slides rather than clipping at either edge, so the number of taps to reach a neighbour never
// changes with where you are standing.
private fun lensAt(seed: GalaxySeed, at: SystemSelection, home: SystemAddress, cells: Int): ReachLensUiState {
    val first = (at.system - cells / 2).coerceIn(1, GalaxyBalance.SYSTEMS_PER_GALAXY - cells + 1)
    return ReachLensUiState(
        firstSystem = first,
        cells = (first until first + cells).map { system ->
            ReachCellUiState(
                system = system,
                label = "$system",
                dot = dotFor(seed = seed, galaxy = at.galaxy, system = system, home = home),
                selected = system == at.system,
            )
        },
    )
}

private fun dotFor(seed: GalaxySeed, galaxy: Int, system: Int, home: SystemAddress): ReachDotUiState {
    if (galaxy == home.galaxy && system == home.system) return ReachDotUiState.Home
    val worlds = worldsIn(seed, SystemAddress(galaxy = galaxy, system = system))
    return if (worlds == 0) {
        ReachDotUiState.Empty
    } else {
        ReachDotUiState.Worlds(count = worlds, starClass = starClassAt(seed, galaxy, system))
    }
}

// How many of a system's fifteen slots hold a world. Regenerated from the seed like every other
// reader of the galaxy — core's own `occupiedWorldsIn` is internal to it, and duplicating the
// fifteen-slot scan here is cheaper than widening that surface for a count the screen wants and the
// model does not.
internal fun worldsIn(seed: GalaxySeed, system: SystemAddress): Int =
    (1..GalaxyBalance.SLOTS_PER_SYSTEM).count { slot ->
        worldAt(seed, GalaxyCoordinate(galaxy = system.galaxy, system = system.system, slot = slot)) != null
    }

// Dim is short and faint because that is what dim means. It makes the *desirable* class the least
// visible mark on the strip, which is a real cost and is kept deliberately: it is true, it costs
// nothing to learn, and inverting it would mean a bright tick means "dim star" — a lie the player
// has to hold in their head forever. Design call 4, and Davide's to overrule.
private fun StarClass.toTick(): ReachTick = when (this) {
    StarClass.DIM -> ReachTick.DIM
    StarClass.STANDARD -> ReachTick.STANDARD
    StarClass.BRIGHT -> ReachTick.BRIGHT
}

// Nine covers the longest flight the map can sell inside one galaxy (4h 39m) and the whole of a
// one-galaxy hop (8h 49m). Past that the marks would be off the strip at every origin.
private const val MAX_MARKED_HOURS: Int = 9
private const val MINUTES_PER_HOUR: Int = 60
