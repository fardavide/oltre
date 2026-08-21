package dev.fardavide.oltre.client.galaxy.presentation

import dev.fardavide.oltre.client.design.format.milli
import dev.fardavide.oltre.client.design.format.signed
import dev.fardavide.oltre.client.design.format.toChipLabel
import dev.fardavide.oltre.client.galaxy.ui.DiscoveryCardUiState
import dev.fardavide.oltre.client.galaxy.ui.GalaxyRowUiState
import dev.fardavide.oltre.client.galaxy.ui.LedgerBodyUiState
import dev.fardavide.oltre.client.galaxy.ui.LedgerEmptinessUiState
import dev.fardavide.oltre.client.design.text.Strings
import dev.fardavide.oltre.client.design.text.TextRes
import dev.fardavide.oltre.client.galaxy.ui.LedgerHeadUiState
import dev.fardavide.oltre.client.galaxy.ui.LedgerMode
import dev.fardavide.oltre.client.world.ui.WorldPortraitUiState
import dev.fardavide.oltre.core.Event
import dev.fardavide.oltre.core.FleetBalance
import dev.fardavide.oltre.core.GalaxyBalance
import dev.fardavide.oltre.core.GalaxyCoordinate
import dev.fardavide.oltre.core.GameState
import dev.fardavide.oltre.core.World
import dev.fardavide.oltre.core.epithetFor
import dev.fardavide.oltre.core.systemNameAt
import dev.fardavide.oltre.core.verdictFor
import dev.fardavide.oltre.core.worldAt
import dev.fardavide.oltre.core.worldNameAt
import kotlin.time.Duration
import kotlin.time.Instant

// **Where you go to find a world you already know.** Everything you have a reading on, in one place,
// in the same row the map draws — which is what makes the ledger cheap and what makes a world look
// like itself wherever you meet it.
//
// It stopped being the screen the tab opens on at 0.12, and lost its filters and its sort with the
// move. Both diagnoses are in `LedgerUiState` in the ui module; the short version is that they
// narrowed and ordered a list of *worlds* when the question they were reached for is about *systems*,
// and a probe is aimed at a star. What is left is the two jobs a list does better than a drawing:
// finding a place you have already been, by name, and keeping the ones you marked at the top.
//
// Nothing here is stored except the pins. The mode and the query are navigation: they sit on
// `GalaxyNavigation` and die with the check-in, because *a filter that outlives the check-in that set
// it is a screen lying about what it holds*.
// `matching` is passed in rather than recomputed: the head prints the count of exactly what the body
// lists, and searching the whole surveyed set twice per render to say so is work the screen can see
// on a long ledger.
internal fun GameState.toLedgerHeadUiState(
    nav: GalaxyNavigation,
    matching: List<World>,
): LedgerHeadUiState {
    return LedgerHeadUiState(
        mode = if (nav.view == GalaxyView.WORLDS) LedgerMode.WORLDS else LedgerMode.MAP,
        query = nav.query,
        // Absent on the orbit page: one system is a reading rather than a list with a length, and
        // the count line would be printing the size of a list that is not on screen.
        count = if (nav.view == GalaxyView.WORLDS) matching.size.worldCount() else null,
    )
}

internal fun GameState.knownWorldsFor(nav: GalaxyNavigation, now: Instant): List<World> =
    if (nav.view == GalaxyView.WORLDS) knownWorlds(nav, now) else emptyList()

internal fun GameState.toLedgerBodyUiState(
    nav: GalaxyNavigation,
    matching: List<World>,
    now: Instant,
): LedgerBodyUiState {
    val pinnedRows = matching.filter { it.at in galaxy.pinned }
    val rest = matching.filterNot { it.at in galaxy.pinned }
    return LedgerBodyUiState(
        discoveries = discoveriesIn(now = now, since = nav.seenAt),
        pinned = pinnedRows.map { toWorldRow(it, now = now, withTrailing = true) },
        rows = rest.map { toWorldRow(it, now = now, withTrailing = true) },
        emptiness = if (matching.isEmpty()) emptinessFor(nav) else null,
    )
}

// Every world the player has a reading on, narrowed by the query and put in the only order the list
// still has. **`surveyed` is the source** — it is the set the whole identity slice exists to make
// valuable, and until 0.11 exactly one function in the codebase read it.
//
// Nearest first, and it is a *fact about the list* now rather than one of four orders you could pick:
// the sort control went at 0.12 because "where next" is not a one-axis question, but a list of places
// you already hold still has one obvious reading, which is which of them you can reach soonest.
private fun GameState.knownWorlds(nav: GalaxyNavigation, now: Instant): List<World> = galaxy.surveyed
    .mapNotNull { worldAt(galaxy.seed, it) }
    .filter { matchesQuery(it, nav.query) }
    .sortedBy { FleetBalance.roundTrip(from = galaxy.home, to = it.at, research = research) }

// A full name returns one row and a system name returns its worlds — which is what unique names
// inside a galaxy buy, and the one place in the app where typing beats tapping.
//
// **An exact name wins outright, and a substring match alone would break the promise above.** Roman
// numerals are substrings of one another: `Calanova I` is inside `Calanova II`, `III`, `IV`, `VI`,
// `VII`, `VIII`, `IX`, `XI`…, and `Calanova X` is inside `XI` through `XV`. So typing a world's
// whole name would have returned twelve rows, which is the phone book again with extra steps.
private fun GameState.matchesQuery(world: World, query: String): Boolean {
    if (query.isBlank()) return true
    val trimmed = query.trim()
    val name = worldNameAt(galaxy.seed, world.at)
    if (name.equals(trimmed, ignoreCase = true)) return true
    // Only when nothing is an exact match does the query widen — otherwise a full name would drag
    // its numeral-suffixed neighbours along with it.
    return galaxy.surveyed.none { worldNameAt(galaxy.seed, it).equals(trimmed, ignoreCase = true) } &&
        name.contains(trimmed, ignoreCase = true)
}

// **Two cases where there were three**, and the third left with the chips: there is no longer a
// filter that can be doing the excluding, so the only ways to an empty list are having surveyed
// nothing and having typed a name nothing answers to. Both still end on the next thing to do rather
// than on an apology.
private fun emptinessFor(nav: GalaxyNavigation): LedgerEmptinessUiState = if (nav.query.isBlank()) {
    LedgerEmptinessUiState(
        headline = Strings.ledgerEmptyHeadline(),
        detail = Strings.ledgerEmptyDetail(),
    )
} else {
    LedgerEmptinessUiState(
        headline = Strings.ledgerNoMatchHeadline(),
        detail = Strings.ledgerNoMatchDetail(),
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
    val temperature = Strings.temperatureReading(traits.temperature.celsius.signed())
    val gravity = Strings.gravityReading(traits.gravity.milliG.milli())
    val pressure = Strings.pressureReading(traits.pressure.milliAtm.milli())
    return DiscoveryCardUiState(
        world = TextRes(worldNameAt(galaxy.seed, world.at)),
        coordinate = world.at.label(),
        epithet = Strings.worldEpithet(epithetFor(traits)),
        portrait = WorldPortraitUiState.Surveyed(
            temperature = traits.temperature,
            gravity = traits.gravity,
            pressure = traits.pressure,
            hazards = traits.hazards,
            hasRing = world.hasRing,
        ),
        temperature = temperature,
        gravity = gravity,
        pressure = pressure,
        readings = Strings.clauses(listOf(temperature, gravity, pressure)),
        note = verdictFor(world, this).discoveryNote(),
        // **"found 5 days ago", not "found day 9"** — nothing in `GameState` carries a genesis
        // instant, so a day number is not derivable where elapsed-since is.
        // Coerced, because a survey cannot have landed in the future and a label that says it did
        // reads as a defect rather than as a clock. Nothing should produce one now that the span is
        // measured from where the advance began, which is why this is a floor rather than a fix.
        found = Strings.foundAgo((now - foundAt).coerceAtLeast(Duration.ZERO).toChipLabel()),
    )
}

private fun dev.fardavide.oltre.core.WorldVerdict.discoveryNote(): TextRes = when (this) {
    is dev.fardavide.oltre.core.WorldVerdict.Settleable -> Strings.noteSettleable()
    dev.fardavide.oltre.core.WorldVerdict.Barren -> Strings.noteBarrenDiscovery()
    is dev.fardavide.oltre.core.WorldVerdict.Blocked -> failures.firstOrNull()
        ?.let { Strings.noteWouldLandIt(Strings.adaptationName(it.axis.adaptation), it.closedAtLevel) }
        ?: Strings.noteBlocked()
    dev.fardavide.oltre.core.WorldVerdict.Home -> Strings.noteHome()
    is dev.fardavide.oltre.core.WorldVerdict.Occupied -> Strings.noteOccupied(TextRes(holder.value))
    dev.fardavide.oltre.core.WorldVerdict.Unsurveyed -> Strings.noteSurveyed()
}

private fun Int.worldCount(): TextRes = Strings.worldCount(this)
