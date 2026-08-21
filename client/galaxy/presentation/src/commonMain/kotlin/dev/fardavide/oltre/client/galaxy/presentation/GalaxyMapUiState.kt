package dev.fardavide.oltre.client.galaxy.presentation

import dev.fardavide.oltre.client.design.format.toChipLabel
import dev.fardavide.oltre.client.design.text.Strings
import dev.fardavide.oltre.client.design.text.TextRes
import dev.fardavide.oltre.client.galaxy.ui.GalaxyMapUiState
import dev.fardavide.oltre.client.galaxy.ui.MapBandUiState
import dev.fardavide.oltre.client.galaxy.ui.MapCaptionTrailingUiState
import dev.fardavide.oltre.client.galaxy.ui.MapCaptionUiState
import dev.fardavide.oltre.client.galaxy.ui.MapHourUiState
import dev.fardavide.oltre.client.galaxy.ui.MapNameTone
import dev.fardavide.oltre.client.galaxy.ui.MapNameUiState
import dev.fardavide.oltre.client.galaxy.ui.MapStarMark
import dev.fardavide.oltre.client.galaxy.ui.MapStarUiState
import dev.fardavide.oltre.client.galaxy.ui.UniverseDiscUiState
import dev.fardavide.oltre.client.galaxy.ui.UniverseUiState
import dev.fardavide.oltre.core.FleetBalance
import dev.fardavide.oltre.core.GalaxyBalance
import dev.fardavide.oltre.core.GalaxyCoordinate
import dev.fardavide.oltre.core.GalaxySeed
import dev.fardavide.oltre.core.GameState
import dev.fardavide.oltre.core.SurveyBalance
import dev.fardavide.oltre.core.SystemAddress
import dev.fardavide.oltre.core.layoutAt
import dev.fardavide.oltre.core.regionNameAt
import dev.fardavide.oltre.core.regionOf
import dev.fardavide.oltre.core.starClassAt
import dev.fardavide.oltre.core.systemNameAt
import dev.fardavide.oltre.core.temperamentsOf
import dev.fardavide.oltre.core.worldAt
import kotlin.time.Duration
import kotlin.time.Instant

// **The fold, derived.** Ten bands of twenty-five, every one of the galaxy's 250 stars, and the four
// or so hairlines where a probe's flight crosses an hour — all of it regenerated on every frame from
// the seed and the save, and none of it stored.
//
// The cost of that is measured rather than assumed: star class for 250 systems is 55 µs on a desktop
// JVM, names for 250 are 144 µs, and generating every *world* in the galaxy is 777 µs. So a glyph
// carries class, region and position — the free tier — and **deliberately not a world count**, which
// is the expensive one and a fourth channel on a 3dp dot besides. The caption pays that price for
// one system at a time, on demand, which is fifteen `worldAt` calls.

internal fun GameState.toGalaxyMapUiState(at: SystemSelection): GalaxyMapUiState {
    val selectedRegion = regionOf(at.system)
    return GalaxyMapUiState(
        bands = bandsOf(galaxy = at.galaxy, lit = selectedRegion),
        stars = starsOf(at = at, mini = false),
        hours = hourMarksFor(galaxy = at.galaxy),
        names = namesFor(at = at),
        mini = false,
    )
}

// The same fold at a fifth of the size for one of the universe's four cards. No hour marks and no
// names — a disc is a texture and a price, and a 148dp drawing has nowhere to put a word.
private fun GameState.toGalaxyDiscUiState(galaxyIndex: Int): GalaxyMapUiState = GalaxyMapUiState(
    bands = bandsOf(galaxy = galaxyIndex, lit = 0),
    stars = starsOf(at = SystemSelection(galaxy = galaxyIndex, system = 0), mini = true),
    hours = emptyList(),
    names = emptyList(),
    mini = true,
)

// One shuffle of the ten temperaments per galaxy, taken once and indexed — `temperamentAt` and
// `regionNameAt` each rebuild that permutation internally, so asking them ten times would shuffle
// the same list ten times over.
private fun GameState.bandsOf(galaxy: Int, lit: Int): List<MapBandUiState> {
    val temperaments = temperamentsOf(this.galaxy.seed, galaxy)
    return (1..GalaxyBalance.REGIONS_PER_GALAXY).map { region ->
        MapBandUiState(
            region = region,
            name = TextRes(regionNameAt(this.galaxy.seed, galaxy, region)),
            temperament = temperaments[region - 1],
            lit = region == lit,
        )
    }
}

// **The overlays come off the save, not off the generator**, which is what keeps the whole draw
// inside the free tier. "Have I been here" is a membership test against `galaxy.surveyed` — a set of
// world coordinates the save already holds — rather than `hasSurveyed`, which walks fifteen slots per
// system and would turn a 55 µs draw into a 777 µs one.
//
// That substitution also fixes something `hasSurveyed` would have got wrong here. `hasSurveyed` is
// *vacuously true* for a system with no worlds in it, because every one of its zero worlds is in the
// set — so a map built on it would ring hundreds of empty systems nobody has ever sent a probe to.
// A system has a ring when you know a world in it, which is what a player means by having been
// somewhere.
private fun GameState.starsOf(at: SystemSelection, mini: Boolean): List<MapStarUiState> {
    val seed = galaxy.seed
    val known = galaxy.surveyed.filter { it.galaxy == at.galaxy }.mapTo(mutableSetOf()) { it.system }
    val inFlight = surveys.filter { it.target.galaxy == at.galaxy }.mapTo(mutableSetOf()) { it.target.system }
    val home = galaxy.home.takeIf { it.galaxy == at.galaxy }?.system
    return (1..GalaxyBalance.SYSTEMS_PER_GALAXY).map { system ->
        val layout = layoutAt(seed, at.galaxy, system)
        MapStarUiState(
            system = system,
            starClass = starClassAt(seed, at.galaxy, system),
            driftPermille = layout.driftPermille,
            sizePermille = layout.sizePermille,
            coolHalo = layout.haloPermille < COOL_HALO_BELOW_PERMILLE,
            marks = buildSet {
                if (system in known) add(MapStarMark.SURVEYED)
                if (system in inFlight) add(MapStarMark.IN_FLIGHT)
                if (system == home) add(MapStarMark.HOME)
                // A disc has no selection: the universe view selects a *galaxy*, and the card's own
                // border is what says which.
                if (!mini && system == at.system) add(MapStarMark.SELECTED)
            },
        )
    }
}

// **The probe's clock, laid along the path.** `SurveyBalance` is thirty minutes plus one a system, so
// on an index-monotone drawing every whole hour is a vertical hairline — which is what retires the
// reach strip rather than reproducing it.
//
// Davide's call, 2026-08-15: the probe's and not the run's. Two rulers over one drawing is not
// survivable, and a probe is the only thing the map can aim.
//
// Nine hours rather than the four the design drew, and the difference only shows on a galaxy you do
// not live in: a hop already costs 4h 40m before a single system of travel, so `1h`…`4h` would leave
// a foreign galaxy with no ruler at all. At home the ninth is off the map and the four are what
// remain.
private fun GameState.hourMarksFor(galaxy: Int): List<MapHourUiState> {
    val home = SystemAddress.of(this.galaxy.home)
    // The index the flight is measured from *in the galaxy being looked at* — your own system number
    // projected across the hop, which is where the ruler is symmetric because distance is.
    val origin = home.system
    val base = SurveyBalance.duration(from = home, to = SystemAddress(galaxy = galaxy, system = origin))
        .inWholeMinutes
    return (1..MARKED_HOURS).flatMap { hour ->
        val away = (hour * MINUTES_PER_HOUR - base).toInt()
        if (away < 0) return@flatMap emptyList()
        listOf(origin - away, origin + away)
            .distinct()
            .filter { it in 1..GalaxyBalance.SYSTEMS_PER_GALAXY }
            .map { system -> MapHourUiState(system = system, label = Strings.durationHours(hour.toLong())) }
    }
}

// **A pin is what makes a name appear**, and that is the whole of search on a map. Home and the
// selection are named by the same mechanism, and the precedence is home first: your own star is
// still your own star while you are standing on it.
private fun GameState.namesFor(at: SystemSelection): List<MapNameUiState> {
    val home = galaxy.home.takeIf { it.galaxy == at.galaxy }?.system
    val pinned = galaxy.pinned.filter { it.galaxy == at.galaxy }.map { it.system }
    return (pinned + listOfNotNull(home, at.system)).distinct().sorted().map { system ->
        MapNameUiState(
            system = system,
            name = TextRes(systemNameAt(galaxy.seed, at.galaxy, system)),
            tone = when {
                system == home -> MapNameTone.HOME
                system == at.system -> MapNameTone.SELECTED
                else -> MapNameTone.PINNED
            },
        )
    }
}

// The bar under the fold. Two lines of what a system is before anybody has looked at it — every one
// of them charted, so nothing here can leak what a survey is the reward for — and one trailing
// element that is a control exactly when there is a probe to send.
internal fun GameState.toMapCaptionUiState(at: SystemSelection, now: Instant): MapCaptionUiState {
    val seed = galaxy.seed
    val target = SystemAddress(galaxy = at.galaxy, system = at.system)
    val worlds = worldsIn(seed, target)
    val starClass = Strings.starClassName(starClassAt(seed, at.galaxy, at.system))
    val region = TextRes(regionNameAt(seed, at.galaxy, regionOf(at.system)))
    return MapCaptionUiState(
        system = TextRes(systemNameAt(seed, at.galaxy, at.system)),
        coordinate = Strings.systemAddress(at.galaxy, at.system),
        // **The trailing noun goes and the number stays**, which is the abbreviation rule the system
        // header and the world row already follow. Claude Design's frame set all four words against
        // the run's clock on one line and showed them; at the real advance of JetBrains Mono the left
        // column is about 190dp at 393dp and `standard · Elyutis Reach · 7 worlds` is 214, so it
        // ellipsized inside the word `worlds`. Dropping the noun costs nothing a reader needs: the
        // bar is under a drawing of stars and the figure beside a system is its worlds.
        meta = Strings.clauses(listOf(starClass, region, Strings.plainNumber(worlds))),
        // At 320dp the region goes too, and it is the one exception the map earns to *"never a
        // name"*: the band the selection sits in is lit and named directly above this bar, so the
        // region is the single fact here the drawing has already stated for itself.
        compactMeta = Strings.clauses(listOf(starClass, Strings.plainNumber(worlds))),
        trailing = trailingFor(at = at, target = target, worlds = worlds, now = now),
        own = true,
    )
}

private fun GameState.trailingFor(
    at: SystemSelection,
    target: SystemAddress,
    worlds: Int,
    now: Instant,
): MapCaptionTrailingUiState {
    val flight = surveys.firstOrNull { it.target == target }
    val known = galaxy.surveyed.any { it.galaxy == at.galaxy && it.system == at.system }
    val trip = FleetBalance.roundTrip(
        from = galaxy.home,
        to = GalaxyCoordinate(galaxy = at.galaxy, system = at.system, slot = 1),
        research = research,
    )
    return when {
        // A system with nothing in it is the one case where neither verb applies, and saying so is
        // worth more than an empty corner: a probe sent there would come back with the same answer.
        worlds == 0 -> MapCaptionTrailingUiState.Note(Strings.noWorlds())
        flight != null -> MapCaptionTrailingUiState.Note(
            Strings.probeLandsIn((flight.completesAt - now).coerceAtLeast(Duration.ZERO).toChipLabel()),
        )
        // **Stars are probe targets; worlds are run targets.** On a system you already know, the map
        // has nothing left to aim — a run is chosen per world on the orbit page — so it quotes the
        // clock and the caption's own tap takes you there.
        known -> MapCaptionTrailingUiState.Note(Strings.reachSingle(trip.toChipLabel()))
        !resources.covers(SurveyBalance.cost()) -> MapCaptionTrailingUiState.Note(
            Strings.probeFlight(
                SurveyBalance.duration(from = SystemAddress.of(galaxy.home), to = target).toChipLabel(),
            ),
        )
        else -> MapCaptionTrailingUiState.Dispatch(
            Strings.probeFlight(
                SurveyBalance.duration(from = SystemAddress.of(galaxy.home), to = target).toChipLabel(),
            ),
        )
    }
}

// **What four discs can mean today is one thing, and it is real: what it costs to get there.** The
// four are not equidistant — two neighbours at a 9h 20m round trip and one far corner at 18h 20m,
// against 3h 22m to cross your own galaxy end to end — so a hop is nearly three times the longest
// journey you can make at home.
internal fun GameState.toUniverseUiState(at: SystemSelection): UniverseUiState = UniverseUiState(
    discs = (1..GalaxyBalance.GALAXIES).map { index ->
        val home = index == galaxy.home.galaxy
        UniverseDiscUiState(
            galaxy = index,
            label = Strings.galaxyLabel(index),
            map = toGalaxyDiscUiState(galaxyIndex = index),
            known = Strings.surveyedCount(
                galaxy.surveyed.filter { it.galaxy == index }.distinctBy { it.system }.size,
            ),
            cost = if (home) null else Strings.runFlight(galaxyHopFrom(index).toChipLabel()),
            home = home,
            selected = index == at.galaxy,
        )
    },
)

// The caption under the discs, which is a summary of one galaxy rather than a selection inside one —
// so it takes the plain card rather than the accent edge, and it has nothing to aim. **A probe is
// aimed at a star and a galaxy is not one**: Claude Design's frame put a `probe 4h 40m` button here,
// and there is no system for it to point at.
internal fun GameState.toUniverseCaptionUiState(at: SystemSelection): MapCaptionUiState {
    val known = galaxy.surveyed.filter { it.galaxy == at.galaxy }.distinctBy { it.system }.size
    val home = at.galaxy == galaxy.home.galaxy
    return MapCaptionUiState(
        system = Strings.galaxyNamed(at.galaxy),
        // Nothing to say rather than a message that is empty: a galaxy has no address inside
        // itself, which is the same reason its caption has nothing to aim.
        coordinate = TextRes(""),
        meta = Strings.clauses(
            listOf(
                Strings.systemsCount(Strings.plainNumber(GalaxyBalance.SYSTEMS_PER_GALAXY)),
                if (known == 0) Strings.nothingCharted() else Strings.surveyedCount(known),
            ),
        ),
        // A galaxy's summary is two short facts and fits either width, so there is nothing to drop.
        compactMeta = Strings.clauses(
            listOf(
                Strings.systemsCount(Strings.plainNumber(GalaxyBalance.SYSTEMS_PER_GALAXY)),
                if (known == 0) Strings.nothingCharted() else Strings.surveyedCount(known),
            ),
        ),
        trailing = if (home) {
            MapCaptionTrailingUiState.Note(Strings.homeNote())
        } else {
            MapCaptionTrailingUiState.Note(Strings.probeFlight(probeHopTo(at.galaxy).toChipLabel()))
        },
        own = false,
    )
}

private fun GameState.galaxyHopFrom(galaxyIndex: Int) = FleetBalance.roundTrip(
    from = galaxy.home,
    to = GalaxyCoordinate(galaxy = galaxyIndex, system = galaxy.home.system, slot = galaxy.home.slot),
    research = research,
)

private fun GameState.probeHopTo(galaxyIndex: Int) = SurveyBalance.duration(
    from = SystemAddress.of(galaxy.home),
    to = SystemAddress(galaxy = galaxyIndex, system = galaxy.home.system),
)

// The fifteen-slot scan, and the standing decision it carries: `core`'s own `occupiedWorldsIn` is
// internal to it, and duplicating the scan here is cheaper than widening that surface for a count
// the screen wants and the model does not. It moved here from the reach strip when the strip went.
internal fun worldsIn(seed: GalaxySeed, system: SystemAddress): Int =
    (1..GalaxyBalance.SLOTS_PER_SYSTEM).count { slot ->
        worldAt(seed, GalaxyCoordinate(galaxy = system.galaxy, system = system.system, slot = slot)) != null
    }

// About a third of the brights, which is what Claude Design asked for. A threshold on a draw rather
// than "every third one" for the reason the draw exists at all: iteration order is the renderer's
// business and the sky has to be the seed's.
private const val COOL_HALO_BELOW_PERMILLE: Int = 350

private const val MARKED_HOURS: Int = 9
private const val MINUTES_PER_HOUR: Int = 60
