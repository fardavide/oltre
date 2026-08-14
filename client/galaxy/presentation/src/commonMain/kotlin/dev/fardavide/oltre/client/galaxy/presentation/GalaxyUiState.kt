package dev.fardavide.oltre.client.galaxy.presentation

import dev.fardavide.oltre.client.design.format.groupedByThousands
import dev.fardavide.oltre.client.design.format.toChipLabel
import dev.fardavide.oltre.client.galaxy.ui.GalaxyBodyUiState
import dev.fardavide.oltre.client.galaxy.ui.GalaxyRowUiState
import dev.fardavide.oltre.client.galaxy.ui.GalaxyTabUiState
import dev.fardavide.oltre.client.galaxy.ui.GalaxyUiState
import dev.fardavide.oltre.client.galaxy.ui.MapBodyUiState
import dev.fardavide.oltre.client.galaxy.ui.MapMark
import dev.fardavide.oltre.client.galaxy.ui.MapTrajectoryUiState
import dev.fardavide.oltre.client.galaxy.ui.SystemHeadUiState
import dev.fardavide.oltre.client.galaxy.ui.SystemMapUiState
import dev.fardavide.oltre.core.FleetBalance
import dev.fardavide.oltre.core.GalaxyBalance
import dev.fardavide.oltre.core.GalaxyCoordinate
import dev.fardavide.oltre.core.GameState
import dev.fardavide.oltre.core.StarClass
import dev.fardavide.oltre.core.World
import dev.fardavide.oltre.core.WorldTraits
import dev.fardavide.oltre.core.WorldVerdict
import dev.fardavide.oltre.core.verdictFor
import dev.fardavide.oltre.core.regionNameAt
import dev.fardavide.oltre.core.regionOf
import dev.fardavide.oltre.core.relayAt
import dev.fardavide.oltre.core.starClassAt
import dev.fardavide.oltre.core.systemNameAt
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
): GalaxyUiState {
    val at = nav.at
    // Filtered and sorted once, then handed to both halves: the head prints the count of exactly
    // what the body lists, so deriving it twice is one list walked twice to agree with itself.
    val matching = knownWorldsFor(nav, now)
    return GalaxyUiState(
        head = toLedgerHeadUiState(nav = nav, matching = matching),
        body = when (nav.view) {
            GalaxyView.LEDGER -> GalaxyBodyUiState.Ledger(
                toLedgerBodyUiState(nav = nav, matching = matching, now = now),
            )
            GalaxyView.REGIONS -> GalaxyBodyUiState.Regions(
                galaxy = "Galaxy ${at.galaxy}",
                scope = "${GalaxyBalance.REGIONS_PER_GALAXY} regions · " +
                    "${GalaxyBalance.SYSTEMS_PER_GALAXY} systems",
                rows = toRegionRows(galaxy = at.galaxy),
            )
            // The probe footer is built here rather than above because it is the system view's
            // own furniture: it walks all fifteen slots and prices a flight, and the ledger and the
            // region index would have paid for that and thrown it away.
            GalaxyView.SYSTEM -> GalaxyBodyUiState.System(
                strip = toRegionStripUiState(at = at),
                header = toSystemHeadUiState(at = at),
                map = toSystemMapUiState(at = at, now = now),
                probe = toProbeActionUiState(at = at, worlds = worldsOf(at), now = now, timeZone = timeZone),
                rows = toSystemRows(at = at, now = now),
            )
        },
        dispatch = dispatch?.let {
            toDispatchUiState(
                at = at,
                selection = it,
                // Hoisted rather than restated: the card's footer already decides whether a probe
                // can be sent, and a second copy of that decision inside the sheet is a second place
                // for the two to disagree about one flight.
                probe = toProbeActionUiState(at = at, worlds = worldsOf(at), now = now, timeZone = timeZone),
                now = now,
            )
        },
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
                slot = slot,
                coordinate = coordinate.label(),
                effect = RELAY_EFFECT,
            )
            else -> null
        }
    }
}

private fun GameState.toSystemHeadUiState(at: SystemSelection): SystemHeadUiState {
    val worlds = worldsOf(at)
    val starClass = starClassAt(galaxy.seed, at.galaxy, at.system)
    return SystemHeadUiState(
        galaxies = (1..GalaxyBalance.GALAXIES).map { index ->
            GalaxyTabUiState(label = "G$index", galaxy = index, selected = index == at.galaxy)
        },
        scope = "${GalaxyBalance.SYSTEMS_PER_GALAXY} systems",
        system = systemNameAt(galaxy.seed, at.galaxy, at.system),
        coordinate = "${at.galaxy}:${at.system}",
        region = regionNameAt(galaxy.seed, at.galaxy, regionOf(at.system)),
        detail = detailFor(starClass, worlds.size, compact = false),
        astronomy = astronomyFor(at = at, worlds = worlds),
        isHome = at.galaxy == galaxy.home.galaxy && at.system == galaxy.home.system,
    )
}

private fun GameState.toSystemMapUiState(at: SystemSelection, now: Instant): SystemMapUiState {
    val relay = relayAt(galaxy.seed, at.galaxy, at.system)
    val occupied = (1..GalaxyBalance.SLOTS_PER_SYSTEM).mapNotNull { slot ->
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
                    label = "[${job.target.galaxy}:${job.target.system}]" +
                        " · ${(job.completesAt - now).coerceAtLeast(Duration.ZERO).toChipLabel()}",
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
private fun GameState.astronomyFor(at: SystemSelection, worlds: List<World>): String {
    val home = galaxy.home
    // Any slot of the system will do and slot 1 is the one that always exists: the band and the unit
    // count both ignore the slot the moment the system differs, which is the whole reason this line
    // can be stated once. Asking it of a *world* would make an empty system unanswerable.
    val anywhereInIt = GalaxyCoordinate(galaxy = at.galaxy, system = at.system, slot = 1)
    val band = FleetBalance.distanceBand(from = home, to = anywhereInIt)
    val where = if (band == 0) {
        "Your own system"
    } else {
        "${FleetBalance.distanceUnits(from = home, to = anywhereInIt).toLong().groupedByThousands()} units out"
    }
    val trips = worlds.map { it.at }
        .filter { it != home }
        .map { FleetBalance.roundTrip(from = home, to = it) }
        .sorted()
    val reach = trips.reachLabel()
    // **"from here" goes when the line will not fit, and the budget is a measurement rather than a
    // taste.** Two cases overflow and both are ordinary: the home system, which states a *range* of
    // round trips rather than one, and any target in another galaxy, whose distance is four digits
    // and whose flight is hours. The home system is the screen every player opens on.
    //
    // What goes is a noun and never a figure — the rule the header and the world row already follow
    // — and it is the least load-bearing clause here, because the first clause has already said what
    // the band is measured from.
    val full = listOfNotNull(where, "danger $band from here", reach).joinToString(SEPARATOR)
    if (full.length <= ASTRONOMY_BUDGET_CHARS) return full
    return listOfNotNull(where, "danger $band", reach).joinToString(SEPARATOR)
}

// What one line of this column holds. The content column is capped at `maxContentWidth` and padded
// 16dp a side, so at a phone's 393dp it is 361dp wide; JetBrains Mono advances 0.6em, which at the
// 10.5sp this line is set in makes 57 characters exactly 359dp. That is inside 361 on paper and
// wrapped in practice, so the budget is the measured figure with the rounding taken off rather than
// the arithmetic one.
private const val ASTRONOMY_BUDGET_CHARS = 54

// Null on a system with nothing in it: there is no round trip to nowhere, and the probe footer above
// is already saying the system is empty.
private fun List<Duration>.reachLabel(): String? {
    val shortest = firstOrNull() ?: return null
    val longest = last()
    if (shortest == longest) return "${shortest.toChipLabel()} out and back"
    val from = shortest.toChipLabel()
    val to = longest.toChipLabel()
    // "20–26m" rather than "20m–26m", but only when both ends are minutes: at the hour scale the
    // label already carries an "h" and dropping the "m" off the near end would leave "1h 04–2h 12m".
    val collapsed = if ('h' in from || 'h' in to) "$from–$to" else "${from.removeSuffix("m")}–$to"
    return "$collapsed out and back"
}

private fun detailFor(starClass: StarClass, worlds: Int, compact: Boolean): String {
    if (compact) return "${starClass.name} · $worlds"
    val plural = if (worlds == 1) "world" else "worlds"
    return "${starClass.name} · $worlds $plural"
}

// Evenly across the frame, whatever the system holds. A lone body sits midway rather than at
// either edge — an orbit pinned to the inner limit would say "hot" about a world that might be the
// coldest slot in the system, and the map has no second body to say it against.
private fun orbitOf(index: Int, of: Int): Float =
    if (of <= 1) 0.5f else index.toFloat() / (of - 1).toFloat()

// Internal since the dispatch sheet: the sheet heads itself with the coordinate the row it was
// raised from prints, and two copies of this would be two ways of writing one address.
internal fun GalaxyCoordinate.label(): String = "[$galaxy:$system:$slot]"

// Internal because three files in this module now join a list of facts with it — the row, the sheet
// and the astronomy line — and one screen writing "·" three different ways is the screen reading as
// three screens.
internal const val SEPARATOR = " · "

// The relay's one sentence. It states its effect and stops: no holding mechanic exists until
// multiplayer, and a relay has no hold for a fleet to fill either.
internal const val RELAY_EFFECT = "Relay · contested · +18% range while held"

// Between a value and its unit, so a line that has to wrap never leaves "atm" alone on one.
private const val NBSP = ' '
