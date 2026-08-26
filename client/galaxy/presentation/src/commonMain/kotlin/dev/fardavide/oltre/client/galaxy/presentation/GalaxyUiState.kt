package dev.fardavide.oltre.client.galaxy.presentation

import dev.fardavide.oltre.client.design.format.groupedByThousands
import dev.fardavide.oltre.client.design.format.toChipLabel
import dev.fardavide.oltre.client.design.text.Strings
import dev.fardavide.oltre.client.design.text.TextRes
import dev.fardavide.oltre.client.dispatch.presentation.DispatchProbeOffer
import dev.fardavide.oltre.client.dispatch.presentation.DispatchSelection
import dev.fardavide.oltre.client.dispatch.presentation.toDispatchUiState
import dev.fardavide.oltre.client.galaxy.ui.GalaxyBodyUiState
import dev.fardavide.oltre.client.galaxy.ui.GalaxyHeadUiState
import dev.fardavide.oltre.client.galaxy.ui.GalaxyHeadsUiState
import dev.fardavide.oltre.client.galaxy.ui.GalaxyRowUiState
import dev.fardavide.oltre.client.galaxy.ui.GalaxyScale
import dev.fardavide.oltre.client.galaxy.ui.GalaxyTabUiState
import dev.fardavide.oltre.client.galaxy.ui.GalaxyUiState
import dev.fardavide.oltre.client.galaxy.ui.LedgerMode
import dev.fardavide.oltre.client.galaxy.ui.MapBodyUiState
import dev.fardavide.oltre.client.galaxy.ui.MapMark
import dev.fardavide.oltre.client.galaxy.ui.MapTrajectoryUiState
import dev.fardavide.oltre.client.galaxy.ui.ProbeActionUiState
import dev.fardavide.oltre.client.galaxy.ui.SystemHeadUiState
import dev.fardavide.oltre.client.galaxy.ui.SystemMapUiState
import dev.fardavide.oltre.core.FleetBalance
import dev.fardavide.oltre.core.GalaxyBalance
import dev.fardavide.oltre.core.GalaxyCoordinate
import dev.fardavide.oltre.client.design.component.RefusalUiState
import dev.fardavide.oltre.client.net.domain.HeldActions
import dev.fardavide.oltre.core.GameState
import dev.fardavide.oltre.core.StarClass
import dev.fardavide.oltre.core.SurveyBalance
import dev.fardavide.oltre.core.SystemAddress
import dev.fardavide.oltre.core.World
import dev.fardavide.oltre.core.WorldTraits
import dev.fardavide.oltre.core.WorldVerdict
import dev.fardavide.oltre.core.regionNameAt
import dev.fardavide.oltre.core.regionOf
import dev.fardavide.oltre.core.relayAt
import dev.fardavide.oltre.core.starClassAt
import dev.fardavide.oltre.core.systemNameAt
import dev.fardavide.oltre.core.verdictFor
import dev.fardavide.oltre.core.worldAt
import kotlin.time.Duration
import kotlin.time.Instant
import kotlinx.datetime.TimeZone

// **Everything the Galaxy tab decides.** The types it produces live in `:client:galaxy:ui`, which
// knows nothing about a seed or a `GameState` — this is where a slot becomes a verdict, a world
// becomes a sentence and a system becomes a page.

// Which system is on screen. The galaxy and the system, never the slot: the page *is* a system, and
// the slot is what the rows below the map are for.
data class SystemSelection(val galaxy: Int, val system: Int)

internal fun GameState.toGalaxyUiState(
    nav: GalaxyNavigation,
    now: Instant,
    timeZone: TimeZone,
    dispatch: DispatchSelection? = null,
    held: HeldActions = HeldActions.NONE,
    // What the last tap on a probe produced, or null. A fact about a tap rather than about the map.
    refusal: RefusalUiState? = null,
): GalaxyUiState {
    val at = nav.at
    // Searched once, then handed to both halves: the head prints the count of exactly what the body
    // lists, so deriving it twice is one list walked twice to agree with itself.
    val matching = knownWorldsFor(nav, now)
    return GalaxyUiState(
        head = when (nav.view) {
            GalaxyView.MAP, GalaxyView.UNIVERSE -> GalaxyHeadsUiState.Map(toGalaxyHeadUiState(nav = nav))
            GalaxyView.WORLDS, GalaxyView.SYSTEM ->
                GalaxyHeadsUiState.Worlds(toLedgerHeadUiState(nav = nav, matching = matching))
        },
        body = when (nav.view) {
            GalaxyView.MAP -> GalaxyBodyUiState.Map(
                map = toGalaxyMapUiState(at = at),
                caption = toMapCaptionUiState(at = at, now = now),
            )
            GalaxyView.UNIVERSE -> GalaxyBodyUiState.Universe(
                universe = toUniverseUiState(at = at),
                caption = toUniverseCaptionUiState(at = at),
            )
            GalaxyView.WORLDS -> GalaxyBodyUiState.Ledger(
                toLedgerBodyUiState(nav = nav, matching = matching, now = now),
            )
            // The probe footer is built here rather than above because it is the system view's
            // own furniture: it walks all fifteen slots and prices a flight, and the other three
            // views would have paid for that and thrown it away.
            // **The orbit page obeys the third tier or the third tier does not exist.** The caption
            // under the fold is a 44dp tap target on its whole width, so a player can scrub to any
            // grain star and open this page — and every fact on it, the name and the region and the
            // class and the world count and the drawn bodies and the relay, is charted-tier. Handing
            // it an empty world list is what applies the tier: eight surfaces read `worlds`, and one
            // decision at the top is cheaper than eight guards further down.
            GalaxyView.SYSTEM -> {
                val charted = galaxy.hasCharted(SystemAddress(galaxy = at.galaxy, system = at.system))
                val worlds = if (charted) worldsOf(at) else emptyList()
                GalaxyBodyUiState.System(
                    header = toSystemHeadUiState(at = at, charted = charted, worlds = worlds),
                    map = toSystemMapUiState(at = at, charted = charted, now = now),
                    probe = toProbeActionUiState(
                        at = at,
                        worlds = worlds,
                        now = now,
                        timeZone = timeZone,
                        held = held,
                        refusal = refusal,
                    ),
                    rows = if (charted) toSystemRows(at = at, now = now) else emptyList(),
                )
            }
        },
        dispatch = dispatch?.let { selection ->
            // **The sheet's own system, which is not necessarily the page's.** A ledger row belongs
            // to whatever system it came from, so the probe the refusal may hand back has to be
            // priced for the target's star rather than for the one the map is parked on.
            val its = SystemSelection(galaxy = selection.at.galaxy, system = selection.at.system)
            toDispatchUiState(
                selection = selection,
                // Hoisted rather than restated: the card's footer already decides whether a probe
                // can be sent, and a second copy of that decision inside the sheet is a second place
                // for the two to disagree about one flight. The sheet's own module cannot price a
                // survey and must not learn to — see `DispatchProbeOffer`.
                probe = toProbeActionUiState(
                    at = its,
                    worlds = worldsOf(its),
                    now = now,
                    timeZone = timeZone,
                    held = held,
                    refusal = refusal,
                )
                    .asDispatchProbeOffer(),
                now = now,
                held = held,
                refusal = refusal,
            )
        },
    )
}

// The head above the two map scales. **The count is the same idiom the worlds list uses one scale
// down** — a length and what you know of it — which is what makes the two heads read as two states of
// one tab rather than as two screens.
private fun GameState.toGalaxyHeadUiState(nav: GalaxyNavigation): GalaxyHeadUiState {
    val universe = nav.view == GalaxyView.UNIVERSE
    val known = galaxy.surveyed
        .filter { universe || it.galaxy == nav.at.galaxy }
        .distinctBy { it.galaxy to it.system }
        .size
    val pinned = galaxy.pinned.count { universe || it.galaxy == nav.at.galaxy }
    val systems = if (universe) {
        GalaxyBalance.GALAXIES * GalaxyBalance.SYSTEMS_PER_GALAXY
    } else {
        GalaxyBalance.SYSTEMS_PER_GALAXY
    }
    val charted = if (universe) {
        (1..GalaxyBalance.GALAXIES).sumOf { galaxy.chartedCountIn(it) }
    } else {
        galaxy.chartedCountIn(nav.at.galaxy)
    }
    return GalaxyHeadUiState(
        mode = LedgerMode.MAP,
        scale = if (universe) GalaxyScale.UNIVERSE else GalaxyScale.GALAXY,
        chip = if (universe) {
            Strings.galaxiesCount(GalaxyBalance.GALAXIES)
        } else {
            Strings.galaxyLabel(nav.at.galaxy)
        },
        count = Strings.clauses(
            listOfNotNull(
                // **"61 of 250 charted" is fog's whole readout**, and it replaces the bare length
                // rather than sitting beside it: a length nobody has walked any of was the honest
                // line while the map was free, and it stopped being the interesting number the day
                // the map had to be earned. It is deliberately not a second progression gauge —
                // the strip 8dp above counts what you *are*, this counts what you have looked at.
                Strings.chartedOfSystems(
                    charted = charted.toLong().groupedByThousands(),
                    systems = systems.toLong().groupedByThousands(),
                ),
                Strings.surveyedCount(known),
                // Absent rather than zero, so a save with nothing pinned does not print a control
                // it does not have. The same rule the ledger's own emptiness follows.
                Strings.pinnedCount(pinned).takeIf { pinned > 0 },
            ),
        ),
    )
}

internal fun GameState.worldsOf(at: SystemSelection): List<World> =
    (1..GalaxyBalance.SLOTS_PER_SYSTEM).mapNotNull { slot ->
        worldAt(galaxy.seed, GalaxyCoordinate(galaxy = at.galaxy, system = at.system, slot = slot))
    }

// The system view's rows: every slot that holds something, in slot order — a world, or the system's
// one relay. **No round trip travels with them**, because the astronomy line above has already said
// it for all fifteen.
private fun GameState.toSystemRows(at: SystemSelection, now: Instant): List<GalaxyRowUiState> {
    val relay = relayAt(galaxy.seed, at.galaxy, at.system)
    return (1..GalaxyBalance.SLOTS_PER_SYSTEM).mapNotNull { slot ->
        val coordinate = GalaxyCoordinate(galaxy = at.galaxy, system = at.system, slot = slot)
        val world = worldAt(galaxy.seed, coordinate)
        when {
            world != null -> toWorldRow(world = world, now = now, withTrailing = false)
            coordinate == relay -> GalaxyRowUiState.Relay(
                at = coordinate,
                coordinate = coordinate.label(),
                effect = Strings.relayEffect(),
            )
            else -> null
        }
    }
}

private fun GameState.toSystemHeadUiState(
    at: SystemSelection,
    charted: Boolean,
    worlds: List<World>,
): SystemHeadUiState {
    val target = SystemAddress(galaxy = at.galaxy, system = at.system)
    return SystemHeadUiState(
        galaxies = (1..GalaxyBalance.GALAXIES).map { index ->
            GalaxyTabUiState(label = Strings.galaxyLabel(index), galaxy = index, selected = index == at.galaxy)
        },
        scope = Strings.systemsCount(Strings.plainNumber(GalaxyBalance.SYSTEMS_PER_GALAXY)),
        // Generated names, so `TextRes.Raw` by construction: a star and a region are named from
        // the seed, and there is no language they could be translated into.
        //
        // **Uncharted takes the caption's own trade**, so the two surfaces say the same thing in the
        // same order: the address is the name because it is the only one there is, and the figure
        // beside it becomes the distance rather than an address repeated.
        system = when {
            charted -> TextRes(systemNameAt(galaxy.seed, at.galaxy, at.system))
            else -> Strings.systemAddress(galaxy = at.galaxy, system = at.system)
        },
        coordinate = when {
            charted -> Strings.systemAddressBare(galaxy = at.galaxy, system = at.system)
            else -> distanceFromHome(at)
        },
        // The region keeps its tap back out to the fold either way — a control that still works is
        // what lets this word be swapped rather than the row removed.
        region = when {
            charted -> TextRes(regionNameAt(galaxy.seed, at.galaxy, regionOf(at.system)))
            else -> Strings.unchartedWord()
        },
        detail = when {
            charted -> detailFor(starClassAt(galaxy.seed, at.galaxy, at.system), worlds.size, compact = false)
            else -> Strings.chartsSystems(galaxy.wouldChart(target))
        },
        // Both readings are computed from an empty world list when the light has not reached here, so
        // what survives is distance and danger — position facts, which fog never takes.
        astronomy = astronomyFor(at = at, worlds = worlds),
        shortAstronomy = astronomyFor(at = at, worlds = worlds, dropFromHere = true),
        isHome = at.galaxy == galaxy.home.galaxy && at.system == galaxy.home.system,
    )
}

private fun GameState.toSystemMapUiState(at: SystemSelection, charted: Boolean, now: Instant): SystemMapUiState {
    val relay = relayAt(galaxy.seed, at.galaxy, at.system)
    // **A drawn orbit is a world count you can read off the picture**, and the relay is a generated
    // point of interest — so an uncharted system draws neither. What is left is the star, which is
    // the same thing the fold shows as grain.
    val occupied = if (!charted) emptyList() else (1..GalaxyBalance.SLOTS_PER_SYSTEM).mapNotNull { slot ->
        val coordinate = GalaxyCoordinate(galaxy = at.galaxy, system = at.system, slot = slot)
        val world = worldAt(galaxy.seed, coordinate)
        when {
            world != null -> slot to markFor(world)
            coordinate == relay -> slot to MapMark.RELAY
            else -> null
        }
    }
    return SystemMapUiState(
        // `occupied` is exactly the set the list below draws, so the map and the rows can never
        // disagree about what is there.
        bodies = occupied.mapIndexed { index, (slot, mark) ->
            MapBodyUiState(slot = slot, mark = mark, orbit = orbitOf(index, of = occupied.size))
        },
        trajectory = if (at.galaxy == galaxy.home.galaxy && at.system == galaxy.home.system) {
            // The one landing soonest rather than whichever the list happens to hold first: nothing
            // caps simultaneous probes, and the arc can only carry one of them.
            surveys.minByOrNull { it.completesAt }?.let { job ->
                MapTrajectoryUiState(
                    label = Strings.clauses(
                        listOf(
                            Strings.systemAddress(job.target.galaxy, job.target.system),
                            (job.completesAt - now).coerceAtLeast(Duration.ZERO).toChipLabel(),
                        ),
                    ),
                )
            }
        } else {
            null
        },
    )
}

// Straight off the verdict rather than off a rendered row: a mark needs no deposits, no name and no
// clock, and asking for a row to get one would make the map depend on an instant it does not read.
private fun GameState.markFor(world: World): MapMark = when (verdictFor(world, this)) {
    WorldVerdict.Home -> MapMark.HOME
    is WorldVerdict.Occupied -> MapMark.OCCUPIED
    WorldVerdict.Unsurveyed -> MapMark.UNSURVEYED
    is WorldVerdict.Blocked -> MapMark.BLOCKED
    WorldVerdict.Barren -> MapMark.BARREN
    is WorldVerdict.Settleable -> MapMark.SETTLEABLE
}

// "195 units out · danger 1 from here · 58m out and back", and on your own doorstep "Your own system
// · danger 0 from here · 20–26m out and back". Three facts, all of them free: none needs a survey,
// and all three are the same for every slot of the system — which is exactly why this is one line
// under the header rather than a column on fifteen rows.
//
// **The range is only ever your own system's**, and it is not a rounding of anything: a run's
// distance metric is world-to-world, so within one system it is the *slot* gap that varies, where a
// hop to any other system is priced identically for all fifteen. So one number everywhere else, and
// a spread at home — where the player is choosing between neighbours and the spread is the choice.
private fun GameState.astronomyFor(
    at: SystemSelection,
    worlds: List<World>,
    dropFromHere: Boolean = false,
): TextRes {
    val home = galaxy.home
    // Any slot of the system will do and slot 1 is the one that always exists: the band and the unit
    // count both ignore the slot the moment the system differs, which is the whole reason this line
    // can be stated once. Asking it of a *world* would make an empty system unanswerable.
    val anywhereInIt = GalaxyCoordinate(galaxy = at.galaxy, system = at.system, slot = 1)
    val band = FleetBalance.distanceBand(from = home, to = anywhereInIt)
    val where = if (band == 0) {
        Strings.yourOwnSystemCapitalised()
    } else {
        Strings.unitsOut(FleetBalance.distanceUnits(from = home, to = anywhereInIt).toLong().groupedByThousands())
    }
    val trips = worlds.map { it.at }
        .filter { it != home }
        .map { FleetBalance.roundTrip(from = home, to = it, research = research, ships = FleetBalance.FASTEST_HULL) }
        .sorted()
    val reach = trips.reachLabel()
    // **"from here" goes when the line will not fit** — two cases overflow and both are ordinary:
    // the home system, which states a *range* of round trips rather than one, and any target in
    // another galaxy, whose distance is four digits and whose flight is hours. What goes is a noun
    // and never a figure, the rule the header and the world row already follow, and it is the least
    // load-bearing clause here because the first one has already said what the band is measured from.
    //
    // **Which of the two the screen uses is `SystemHead`'s call since #86, and it has to be.** The
    // choice was a `length` check on the built string, which is a measurement of *English*: a
    // translated line is a different length, so the decision cannot be made before the language is
    // known. So the mapper states both readings and the composable measures the one it is about to
    // draw — the same shape every other compact/full pair on this screen already has.
    val danger = if (dropFromHere) Strings.dangerLevel(band) else Strings.dangerFromHere(band)
    return Strings.clauses(listOfNotNull(where, danger, reach))
}



// Null on a system with nothing in it: there is no round trip to nowhere, and the probe footer above
// is already saying the system is empty.
private fun List<Duration>.reachLabel(): TextRes? {
    val shortest = firstOrNull() ?: return null
    val longest = last()
    if (shortest == longest) return Strings.reachSingle(shortest.toChipLabel())
    // **Whether the near end may drop its unit is the language's call, not this file's.** It used to
    // be decided by looking for an 'h' in the rendered label, which is a fact about English; what the
    // mapper actually knows is whether both ends are under an hour, so that is what it says.
    if (longest.inWholeHours >= 1) {
        return Strings.reachRange(from = shortest.toChipLabel(), to = longest.toChipLabel())
    }
    // Ceiled by the same rule `toChipLabel` rounds by, so the collapsed near end names the minute
    // the full form would have printed.
    val minutes = (shortest.inWholeSeconds + SECONDS_PER_MINUTE - 1) / SECONDS_PER_MINUTE
    return Strings.reachRangeMinutes(fromMinutes = minutes, to = longest.toChipLabel())
}

private const val SECONDS_PER_MINUTE: Int = 60

private fun detailFor(starClass: StarClass, worlds: Int, compact: Boolean): TextRes =
    if (compact) Strings.starDetailCompact(starClass, worlds) else Strings.starDetail(starClass, worlds)

// Evenly across the frame, whatever the system holds. A lone body sits midway rather than at
// either edge — an orbit pinned to the inner limit would say "hot" about a world that might be the
// coldest slot in the system, and the map has no second body to say it against.
private fun orbitOf(index: Int, of: Int): Float =
    if (of <= 1) 0.5f else index.toFloat() / (of - 1).toFloat()

// **The one place the probe footer and the dispatch sheet meet**, and it is a projection rather than
// a decision: `toProbeActionUiState` has already worked out whether a flight would be honoured, and
// this hands the three strings the sheet's refusal needs to say so. The five states that are not an
// offer — unaffordable, in flight, landed, charted, nothing to survey — all become null, which is the
// sheet showing a refusal with no verb under it.
//
// It lives here rather than in `:client:dispatch:presentation` because that module may not see this
// one and should not: a sheet raised from a landing has no probe footer above it at all.
internal fun ProbeActionUiState.asDispatchProbeOffer(): DispatchProbeOffer? =
    (this as? ProbeActionUiState.Dispatch)?.let {
        DispatchProbeOffer(label = it.label, cost = it.offer.cost.amount, flight = it.offer.flight)
    }

// Internal since the dispatch sheet: the sheet heads itself with the coordinate the row it was
// raised from prints, and two copies of this would be two ways of writing one address.
internal fun GalaxyCoordinate.label(): TextRes = Strings.coordinate(galaxy, system, slot)
