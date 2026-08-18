package dev.fardavide.oltre.client.galaxy.ui

import dev.fardavide.oltre.client.design.text.TextRes
import dev.fardavide.oltre.client.dispatch.ui.DispatchUiState

// **What the Galaxy tab draws, and nothing about how it is derived.** The mappers that produce these
// are `:client:galaxy:presentation`, which depends on this module rather than the other way round.
//
// Four views since 0.12 and two heads, and the pairing is the design rather than an accident of
// layout: the two map scales get a head with a galaxy chip and no search, the two lists get a head
// with a search and no chip. **A search field over 250 stars is a control that cannot answer its own
// question**, and a galaxy chip over a list of worlds names a scope the list does not have.
data class GalaxyUiState(
    val head: GalaxyHeadsUiState,
    val body: GalaxyBodyUiState,
    // Null until a world row is tapped. The sheet is the feature's own navigation exactly as `at`
    // is — nothing outside this module has an opinion about which world is open — so it arrives
    // here rather than being a second screen the shell would have to know about.
    val dispatch: DispatchUiState?,
)

// Which head is up. Two shapes rather than one with nullable halves, so a screen cannot be handed a
// search box it has no list for.
sealed interface GalaxyHeadsUiState {

    data class Map(val head: GalaxyHeadUiState) : GalaxyHeadsUiState

    data class Worlds(val head: LedgerHeadUiState) : GalaxyHeadsUiState
}

sealed interface GalaxyBodyUiState {

    // **The tab's default since 0.12.** The galaxy as a folded ribbon of ten banded regions: where
    // you go to find somewhere you have never been. Claude Design overruled its own 0.11.0 call to
    // land here, and the argument is that the galaxy exists nowhere else in the app while the worlds
    // you hold are already on Colony and on Fleets.
    data class Map(val map: GalaxyMapUiState, val caption: MapCaptionUiState) : GalaxyBodyUiState

    // One gesture up: four discs in the map's own frame. Not a push, so there is nothing to come
    // back from.
    data class Universe(val universe: UniverseUiState, val caption: MapCaptionUiState) : GalaxyBodyUiState

    // One system, filling the screen: where you go to acquire a reading you do not have. The one
    // real push in the tab, and it lost the reach strip at 0.12 — the strip's figure was already
    // printed one line above it in the astronomy line, which is a duplicate 0.11.0 found and did not
    // act on.
    data class System(
        val header: SystemHeadUiState,
        val map: SystemMapUiState,
        val probe: ProbeActionUiState,
        val rows: List<GalaxyRowUiState>,
    ) : GalaxyBodyUiState

    // Everything you have a reading on: where you go to spend a ship, and to find a world you
    // already know by name.
    data class Ledger(val body: LedgerBodyUiState) : GalaxyBodyUiState
}

// Which of the four a body is, and nothing about what is in it. It exists for the crossfade in
// `GalaxyPage`: a transition keyed on the body itself would replay every time a countdown ticked,
// because the body is a data class and every second gives it a new value. Keyed on this, the fade
// runs when the player changes view and at no other time.
internal val GalaxyBodyUiState.view: GalaxyView
    get() = when (this) {
        is GalaxyBodyUiState.Map -> GalaxyView.MAP
        is GalaxyBodyUiState.Universe -> GalaxyView.UNIVERSE
        is GalaxyBodyUiState.System -> GalaxyView.SYSTEM
        is GalaxyBodyUiState.Ledger -> GalaxyView.LEDGER
    }

internal enum class GalaxyView { MAP, UNIVERSE, SYSTEM, LEDGER }

// The system header. **The name is the headline and the coordinate is its subtitle** — the address
// survives, demoted, and the region is the only accent string here, which is what makes it read as
// the target it is.
data class SystemHeadUiState(
    // Four, always — the coordinate space is fixed, so this is a segmented control rather than a
    // list that grows. **Kept when the universe view arrived rather than deleted for it**: the
    // universe is where you *compare* galaxies, and this is where you step sideways between them
    // without leaving the system you are reading.
    val galaxies: List<GalaxyTabUiState>,
    val scope: TextRes,
    val system: TextRes,
    val coordinate: TextRes,
    val region: TextRes,
    val detail: TextRes,
    // "195 units out · danger 1 from here · 58m out and back". **Stated once, under the system
    // header, because it is astronomy** — free, known from the first launch, and identical for all
    // fifteen slots of any system but your own.
    val astronomy: TextRes,
    // The same line with its least load-bearing clause dropped. **Which of the two is drawn is
    // the composable's call since #86**: it used to be a `length` check inside the mapper, which
    // is a measurement of English — see `astronomyFor`.
    val shortAstronomy: TextRes,
    val isHome: Boolean,
)

data class GalaxyTabUiState(val label: TextRes, val galaxy: Int, val selected: Boolean)

// What the map draws: the system as orbits seen at a shallow angle, one ellipse per occupied slot.
//
// **The Sky pass's one real subtraction, and it is worth naming.** Until 0.3.0 the map was fifteen
// ticks on a horizontal line, and its whole argument was that it showed the *empty* slots — the
// shape of a system, and where its gaps fall — which the list underneath could never do. The orbit
// view cannot say that: eleven empty ellipses at 11dp apart read as noise rather than as absence.
// Davide took the trade knowingly (2026-08-10) for the picture the design is built around.
data class SystemMapUiState(
    val bodies: List<MapBodyUiState>,
    // Only on the home system's map, and only while a probe is out: a flight leaves from where it
    // was launched, and every probe in this game is launched from home. Null everywhere else.
    val trajectory: MapTrajectoryUiState?,
)

data class MapBodyUiState(
    val slot: Int,
    val mark: MapMark,
    // Where this body's orbit sits between the innermost the map draws and the outermost: 0 for the
    // first body in the system, 1 for the last, evenly spaced in between. A fraction rather than a
    // length, because how wide the widest orbit is drawn is the Canvas's business.
    //
    // **Spaced by rank rather than by slot, and that is a real choice with a real cost.** Linear in
    // the slot was tried first and does not survive contact with a real system: fifteen slots across
    // the frame puts neighbours 11dp apart, which is narrower than the number printed under them.
    // What is kept is the order; what is lost is the scale.
    val orbit: Float,
)

// "[2:118] · 4h 12m" — where the probe is going and how long it has left, printed at the faint end
// of the arc that carries it.
data class MapTrajectoryUiState(val label: TextRes)

// What a dot on the map means. `EMPTY` is a tick rather than a dot, and it is most of them.
enum class MapMark { EMPTY, HOME, OCCUPIED, UNSURVEYED, BLOCKED, BARREN, SETTLEABLE, RELAY }
