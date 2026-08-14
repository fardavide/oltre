package dev.fardavide.oltre.client.galaxy.ui

// **What the Galaxy tab draws, and nothing about how it is derived.** The mappers that produce these
// are `:client:galaxy:presentation`, which depends on this module rather than the other way round.
//
// The tab has three views since 0.11 and one head above all of them. The head is the switch, the
// search and the filters; the body is whichever of the three you are looking at.
data class GalaxyUiState(
    val head: LedgerHeadUiState,
    val body: GalaxyBodyUiState,
    // Null until a world row is tapped. The sheet is the feature's own navigation exactly as `at`
    // is — nothing outside this module has an opinion about which world is open — so it arrives
    // here rather than being a second screen the shell would have to know about.
    val dispatch: DispatchUiState?,
)

sealed interface GalaxyBodyUiState {

    // One system, filling the screen: where you go to acquire a reading you do not have.
    data class System(
        val strip: RegionStripUiState,
        val header: SystemHeadUiState,
        val map: SystemMapUiState,
        val probe: ProbeActionUiState,
        val rows: List<GalaxyRowUiState>,
    ) : GalaxyBodyUiState

    // Everything you have a reading on: where you go to spend a ship. The tab's default.
    data class Ledger(val body: LedgerBodyUiState) : GalaxyBodyUiState

    // Ten rows against a thousand pages: where you decide where to probe next.
    data class Regions(val galaxy: String, val scope: String, val rows: List<RegionRowUiState>) :
        GalaxyBodyUiState
}

// The system header. **The name is the headline and the coordinate is its subtitle** — the address
// survives, demoted, and the region is the only accent string here, which is what makes it read as
// the target it is.
data class SystemHeadUiState(
    // Four, always — the coordinate space is fixed, so this is a segmented control rather than a
    // list that grows.
    val galaxies: List<GalaxyTabUiState>,
    val scope: String,
    val system: String,
    val coordinate: String,
    val region: String,
    val detail: String,
    // "195 units out · danger 1 from here · 58m out and back". **Stated once, under the system
    // header, because it is astronomy** — free, known from the first launch, and identical for all
    // fifteen slots of any system but your own.
    val astronomy: String,
    val isHome: Boolean,
)

data class GalaxyTabUiState(val label: String, val galaxy: Int, val selected: Boolean)

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
data class MapTrajectoryUiState(val label: String)

// What a dot on the map means. `EMPTY` is a tick rather than a dot, and it is most of them.
enum class MapMark { EMPTY, HOME, OCCUPIED, UNSURVEYED, BLOCKED, BARREN, SETTLEABLE, RELAY }
