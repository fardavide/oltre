package dev.fardavide.oltre.client.galaxy.presentation

import dev.fardavide.oltre.client.design.format.toChipLabel
import dev.fardavide.oltre.client.galaxy.ui.DiscoveryCardUiState
import dev.fardavide.oltre.client.galaxy.ui.GalaxyRowUiState
import dev.fardavide.oltre.client.galaxy.ui.LedgerBodyUiState
import dev.fardavide.oltre.client.galaxy.ui.LedgerChipUiState
import dev.fardavide.oltre.client.galaxy.ui.LedgerEmptinessUiState
import dev.fardavide.oltre.client.galaxy.ui.LedgerFilter
import dev.fardavide.oltre.client.galaxy.ui.LedgerHeadUiState
import dev.fardavide.oltre.client.galaxy.ui.LedgerMode
import dev.fardavide.oltre.client.galaxy.ui.LedgerSort
import dev.fardavide.oltre.client.galaxy.ui.WorldPortraitUiState
import dev.fardavide.oltre.client.galaxy.ui.WorldVerdictUiState
import dev.fardavide.oltre.client.design.format.milli
import dev.fardavide.oltre.client.design.format.signed
import dev.fardavide.oltre.core.AdaptationTechnology
import dev.fardavide.oltre.core.Event
import dev.fardavide.oltre.core.FleetBalance
import dev.fardavide.oltre.core.GalaxyBalance
import dev.fardavide.oltre.core.GalaxyCoordinate
import dev.fardavide.oltre.core.GameState
import dev.fardavide.oltre.core.HostilityAxis
import dev.fardavide.oltre.core.axisValue
import dev.fardavide.oltre.core.ResourceKind
import dev.fardavide.oltre.core.World
import dev.fardavide.oltre.core.epithetFor
import dev.fardavide.oltre.core.regionNameAt
import dev.fardavide.oltre.core.regionOf
import dev.fardavide.oltre.core.systemNameAt
import dev.fardavide.oltre.core.verdictFor
import dev.fardavide.oltre.core.worldAt
import dev.fardavide.oltre.core.worldNameAt
import kotlin.time.Duration
import kotlin.time.Instant

// **The screen the Galaxy tab opens on.** Everything you have a reading on, in one place, in the
// same row the map draws — which is what makes the ledger cheap and what makes a world look like
// itself wherever you meet it.
//
// Nothing here is stored except the pins. The mode, the query, the chips and the sort are
// navigation: they sit on `GalaxyNavigation` and die with the check-in, because *a filter that
// outlives the check-in that set it is a screen lying about what it holds*.
internal fun GameState.toLedgerHeadUiState(nav: GalaxyNavigation, now: Instant): LedgerHeadUiState {
    val worlds = if (nav.view == GalaxyView.LEDGER) knownWorlds(nav, now) else emptyList()
    return LedgerHeadUiState(
        mode = if (nav.view == GalaxyView.LEDGER) LedgerMode.WORLDS else LedgerMode.MAP,
        query = nav.query,
        // Absent outside the ledger: the map is not a query, so a filter row over it would be a
        // control with nothing to act on.
        chips = if (nav.view == GalaxyView.LEDGER) nav.chips() else emptyList(),
        count = if (nav.view == GalaxyView.LEDGER) worlds.size.worldCount() else null,
        sort = nav.sort,
    )
}

internal fun GameState.toLedgerBodyUiState(nav: GalaxyNavigation, now: Instant): LedgerBodyUiState {
    val matching = knownWorlds(nav, now)
    val pinnedRows = matching.filter { it.at in galaxy.pinned }
    val rest = matching.filterNot { it.at in galaxy.pinned }
    return LedgerBodyUiState(
        discoveries = discoveriesIn(now = now, since = nav.seenAt),
        pinned = pinnedRows.map { toWorldRow(it, now = now, withTrailing = true) },
        rows = rest.map { toWorldRow(it, now = now, withTrailing = true) },
        emptiness = if (matching.isEmpty()) emptinessFor(nav, now) else null,
    )
}

// Every world the player has a reading on, filtered by the chips and the query and put in the
// chosen order. **`surveyed` is the source** — it is the set the whole slice exists to make
// valuable, and until 0.11 exactly one function in the codebase read it.
private fun GameState.knownWorlds(nav: GalaxyNavigation, now: Instant): List<World> {
    val known = galaxy.surveyed.mapNotNull { worldAt(galaxy.seed, it) }
    return known
        .filter { world -> nav.filters.all { matches(it, world, now) } }
        .filter { matchesQuery(it, nav.query) }
        .sortedWith(nav.sort.comparator(this, now))
}

// A full name returns one row and a system name returns its worlds — which is what unique names
// inside a galaxy buy, and the one place in the app where typing beats tapping.
private fun GameState.matchesQuery(world: World, query: String): Boolean {
    if (query.isBlank()) return true
    return worldNameAt(galaxy.seed, world.at).contains(query.trim(), ignoreCase = true)
}

private fun GameState.matches(filter: LedgerFilter, world: World, now: Instant): Boolean = when (filter) {
    is LedgerFilter.ReachableWithin ->
        FleetBalance.roundTrip(from = galaxy.home, to = world.at).inWholeMinutes <= filter.hours * 60
    is LedgerFilter.Verdict -> toWorldRow(world, now, withTrailing = false).verdict == filter.verdict
    is LedgerFilter.OneLevelAway -> world.isOneLevelAway(filter.technology, this)
    LedgerFilter.StillHolding -> ResourceKind.entries
        .filter { it != ResourceKind.DEUTERIUM }
        .any { galaxy.remaining(world.at, it, now) > 0 }
    is LedgerFilter.Region -> regionOf(world.at.system) == filter.region &&
        world.at.galaxy == galaxy.home.galaxy
}

// A world one level of ONE ladder away from being tolerable — the chip that turns the research tab
// into a shopping list from the other direction.
private fun World.isOneLevelAway(technology: AdaptationTechnology, state: GameState): Boolean {
    val current = state.research.adaptationLevels()
    val axis = HostilityAxis.entries.first { it.adaptation == technology }
    val next = when (technology) {
        AdaptationTechnology.THERMAL -> current.copy(thermal = current.thermal + 1)
        AdaptationTechnology.GRAVITIC -> current.copy(gravitic = current.gravitic + 1)
        AdaptationTechnology.ATMOSPHERIC -> current.copy(atmospheric = current.atmospheric + 1)
    }
    val value = traits.axisValue(axis)
    return value !in GalaxyBalance.tolerance(current).bandOf(axis) &&
        value in GalaxyBalance.tolerance(next).bandOf(axis)
}

// **Never a dead end, always the next number** — the `time-until-affordable` pattern applied to a
// query. It names the filter that is doing the excluding and what dropping it would return, which is
// the difference between a screen that has failed and one that is still answering.
private fun GameState.emptinessFor(nav: GalaxyNavigation, now: Instant): LedgerEmptinessUiState {
    if (nav.filters.isEmpty() && nav.query.isBlank()) {
        return LedgerEmptinessUiState(
            headline = "Every world a probe reaches lands here.",
            detail = "You have surveyed nothing yet.",
        )
    }
    if (nav.query.isNotBlank()) {
        return LedgerEmptinessUiState(
            headline = "No world you know is called that.",
            detail = "Names are unique in a galaxy, so a full name finds one place.",
        )
    }
    // Which single chip, dropped, would give the most back. Stated as a count rather than as advice,
    // because the count is what the player is actually deciding against.
    val without = nav.filters.map { dropped ->
        dropped to knownWorlds(nav.copy(filters = nav.filters - dropped), now).size
    }.maxByOrNull { it.second }
    val detail = without
        ?.takeIf { it.second > 0 }
        ?.let { (dropped, count) -> "${count.worldCount()} match without ${dropped.label().lowercase()}." }
        ?: "Nothing you have surveyed matches."
    return LedgerEmptinessUiState(
        headline = "No world matches all ${nav.filters.size}.",
        detail = detail,
    )
}

// **The survey moment, and it costs the save nothing.** "New" is not a flag on a world — it is
// *surveyed since you last had this tab open*, which the event log already answers and which no
// seen-set has to remember. The worlds of a surveyed system are regenerable from the seed, so the
// event needs to name only the system.
//
// The design says *"surveyed inside the span the app just advanced through"*, and this is the
// nearest thing reachable without plumbing the previous save instant down from the shell: the
// boundary is the moment this screen was last composed rather than the moment the state last
// advanced. The two differ only for a player who opens the tab, leaves it, and comes back inside one
// advance — for whom the card is gone rather than shown twice, which is the right way to be wrong.
private fun GameState.discoveriesIn(now: Instant, since: Instant): List<DiscoveryCardUiState> = eventLog
    .filterIsInstance<Event.SurveyCompleted>()
    .filter { it.at > since }
    .flatMap { event ->
        (1..GalaxyBalance.SLOTS_PER_SYSTEM).mapNotNull { slot ->
            val at = GalaxyCoordinate(
                galaxy = event.target.galaxy,
                system = event.target.system,
                slot = slot,
            )
            worldAt(galaxy.seed, at)?.let { it to event.at }
        }
    }
    .map { (world, at) -> toDiscoveryCard(world, foundAt = at, now = now) }

private fun GameState.toDiscoveryCard(world: World, foundAt: Instant, now: Instant): DiscoveryCardUiState {
    val traits = world.traits
    return DiscoveryCardUiState(
        world = worldNameAt(galaxy.seed, world.at),
        coordinate = world.at.label(),
        epithet = epithetFor(traits).toString(),
        portrait = WorldPortraitUiState.Surveyed(
            temperature = traits.temperature,
            gravity = traits.gravity,
            pressure = traits.pressure,
            hazards = traits.hazards,
            hasRing = world.hasRing,
        ),
        temperature = "${traits.temperature.celsius.signed()} °C",
        gravity = "${traits.gravity.milliG.milli()} g",
        pressure = "${traits.pressure.milliAtm.milli()} atm",
        note = verdictFor(world, this).discoveryNote(),
        // **"found 5 days ago", not "found day 9"** — nothing in `GameState` carries a genesis
        // instant, so a day number is not derivable where elapsed-since is.
        found = "found ${(now - foundAt).toChipLabel()} ago",
    )
}

private fun dev.fardavide.oltre.core.WorldVerdict.discoveryNote(): String = when (this) {
    is dev.fardavide.oltre.core.WorldVerdict.Settleable -> "Nothing here blocks a colony."
    dev.fardavide.oltre.core.WorldVerdict.Barren -> "Passes every band, and not worth taking."
    is dev.fardavide.oltre.core.WorldVerdict.Blocked ->
        failures.firstOrNull()?.let { "${it.axis.adaptation.name.lowercase()
            .replaceFirstChar { first -> first.uppercase() }} ${it.closedAtLevel} would land it." }
            ?: "Blocked."
    dev.fardavide.oltre.core.WorldVerdict.Home -> "Your colony."
    is dev.fardavide.oltre.core.WorldVerdict.Occupied -> "Held by ${holder.value}."
    dev.fardavide.oltre.core.WorldVerdict.Unsurveyed -> "Surveyed."
}

private fun LedgerSort.comparator(state: GameState, now: Instant): Comparator<World> = when (this) {
    LedgerSort.NEAREST -> compareBy { FleetBalance.roundTrip(from = state.galaxy.home, to = it.at) }
    LedgerSort.RICHEST -> compareByDescending { GalaxyBalance.yieldScore(it.traits).perMillion }
    LedgerSort.MOST_LEFT -> compareByDescending {
        state.galaxy.remaining(it.at, ResourceKind.METAL, now) +
            state.galaxy.remaining(it.at, ResourceKind.CRYSTAL, now)
    }
    // Nothing records *when* a world was surveyed, so the closest honest reading is coordinate
    // order reversed — the newest thing a probe reached is the furthest one it was sent to. Flagged
    // rather than faked: a real "newest" wants the survey instant, which is a schema hop.
    LedgerSort.NEWEST -> compareByDescending { it.at.system * 100 + it.at.slot }
}

private fun GalaxyNavigation.chips(): List<LedgerChipUiState> = availableFilters.map { filter ->
    LedgerChipUiState(filter = filter, label = filter.label(), on = filter in filters)
}

private fun LedgerFilter.label(): String = when (this) {
    is LedgerFilter.ReachableWithin -> "reachable ${hours}h"
    is LedgerFilter.Verdict -> verdict.word?.lowercase() ?: "unsurveyed"
    is LedgerFilter.OneLevelAway -> "one level away"
    LedgerFilter.StillHolding -> "still holding"
    is LedgerFilter.Region -> name.substringBefore(' ')
}

private fun Int.worldCount(): String = when (this) {
    0 -> "no worlds"
    1 -> "1 world"
    else -> "$this worlds"
}

// The five the design settles, in its order. The region chip names the region the player is standing
// in, so it is the one filter whose label moves with where they are.
internal fun GameState.availableFiltersFor(at: SystemSelection): List<LedgerFilter> = listOf(
    LedgerFilter.ReachableWithin(hours = 6),
    LedgerFilter.Verdict(WorldVerdictUiState.SETTLEABLE),
    LedgerFilter.OneLevelAway(AdaptationTechnology.THERMAL),
    LedgerFilter.StillHolding,
    LedgerFilter.Region(
        region = regionOf(at.system),
        name = regionNameAt(galaxy.seed, at.galaxy, regionOf(at.system)),
    ),
)
