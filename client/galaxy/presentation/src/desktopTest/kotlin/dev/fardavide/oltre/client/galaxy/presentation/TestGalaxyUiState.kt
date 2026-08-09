package dev.fardavide.oltre.client.galaxy.presentation

import dev.fardavide.oltre.core.AdaptationTechnology
import dev.fardavide.oltre.core.GalaxySeed
import dev.fardavide.oltre.core.GalaxyState
import dev.fardavide.oltre.core.GameState
import kotlin.time.Instant
import kotlinx.datetime.TimeZone

// Two of these come from the real generator and one does not, and the split is deliberate.
//
// The research module freezes its fixtures by hand so that a baseline moves only when the *screen*
// moves. That reasoning does not carry here: the galaxy's constants are themselves pinned, value by
// value, by `GalaxyBalanceTest` and `GalaxyDistributionTest`, so a change that moves these numbers
// is a design decision that *should* redraw the images. Hand-writing them bought nothing and cost
// something real — the first version of this file drifted from the mapper's own formatting within
// an hour, and the screenshots quietly rendered numbers the app would never produce.
//
// `everyVerdictUiState` stays hand-written because it has to: under *this* seed the home system is
// Home plus three Blocked, so Barren, Settleable, Occupied and a relay have no example to draw from
// here. **There is no such thing as the shipped seed** — the shell mints one per colony from the
// instant it was founded, so every player's galaxy is different, and a suite that only covered what
// one seed happens to generate would be covering an accident. The mapper's own Barren and
// Settleable branches are exercised against real generated worlds in `GalaxyUiStateTest`, which is
// where that belongs; this fixture exists so the *screen* has all seven states in one frame.

private val galaxy: GalaxyState = GalaxyState.initial(GalaxySeed(20_260_807))

// A colony that has researched nothing, which is what the three generated frames below describe:
// the mapper reads the empire's adaptation levels now, so it needs the whole state rather than the
// map half of it.
private val state: GameState = GameState.initial(galaxy.seed)

// Frozen, because the footer runs a countdown now and a baseline recorded against a wall clock
// would differ from itself every second. Epoch in UTC, so a landing time on a frame is arithmetic
// rather than a fact about the machine that recorded it.
internal val FIXTURE_NOW: Instant = Instant.fromEpochMilliseconds(0)

// The home system exactly as generated: Home in slot 7, then three Blocked worlds, all three of
// which out-yield the worth-it threshold. That is the pillar landing — the good ground is behind
// the technology nobody has bought yet.
internal val homeSystemUiState: GalaxyUiState = state.toGalaxyUiState(
    at = SystemSelection(galaxy = galaxy.home.galaxy, system = galaxy.home.system),
    now = FIXTURE_NOW,
    timeZone = TimeZone.UTC,
)

// The neighbour, and every system but one at ship time.
internal val unsurveyedSystemUiState: GalaxyUiState = state.toGalaxyUiState(
    at = SystemSelection(galaxy = galaxy.home.galaxy, system = galaxy.home.system - 1),
    now = FIXTURE_NOW,
    timeZone = TimeZone.UTC,
)

// The first system of the first galaxy: the one place a centred lens has to slide rather than
// centre, and the one origin whose ruler is one-sided.
internal val edgeOfTheGalaxyUiState: GalaxyUiState = state.toGalaxyUiState(
    at = SystemSelection(galaxy = 1, system = 1),
    now = FIXTURE_NOW,
    timeZone = TimeZone.UTC,
)

// The precedence, top to bottom, including the four states this seed's home system cannot show.
internal val everyVerdictUiState = GalaxyUiState(
    galaxies = (1..4).map { GalaxyTabUiState(label = "G$it", galaxy = it, selected = it == 2) },
    scope = "250 systems",
    coordinate = "2:118",
    detail = "BRIGHT · 6 worlds",
    compactDetail = "BRIGHT · 6",
    isHome = false,
    // Borrowed from a real frame rather than hand-written. The band is 250 generated ticks and a
    // ruler derived from `SurveyBalance`; typing that out by hand is how a fixture starts asserting
    // a picture the app would never draw, which is the mistake this file's header warns about.
    reach = homeSystemUiState.reach,
    probe = ProbeActionUiState.Dispatch(
        offer = dispatchOffer(),
        label = "Dispatch probe",
        compactLabel = "Dispatch",
    ),
    map = SystemMapUiState(
        slots = marks(
            3 to MapMark.RELAY,
            4 to MapMark.HOME,
            5 to MapMark.OCCUPIED,
            6 to MapMark.UNSURVEYED,
            8 to MapMark.BLOCKED,
            9 to MapMark.BARREN,
            11 to MapMark.SETTLEABLE,
        ),
    ),
    bands = listOf(
        OrbitBandUiState(
            band = OrbitBand.HOT,
            rows = listOf(
                WorldRowUiState(
                    coordinate = "[2:118:3]",
                    slot = 3,
                    band = OrbitBand.HOT,
                    verdict = VerdictUiState.Relay(effect = "+18% range while held"),
                ),
            ),
        ),
        OrbitBandUiState(
            band = OrbitBand.TEMPERATE,
            rows = listOf(
                WorldRowUiState(
                    coordinate = "[2:118:4]",
                    slot = 4,
                    band = OrbitBand.TEMPERATE,
                    verdict = VerdictUiState.Home(
                        axes = homeAxes(),
                        detail = "142 fields · yield 0.87 · no hazards",
                    ),
                ),
                WorldRowUiState(
                    coordinate = "[2:118:5]",
                    slot = 5,
                    band = OrbitBand.TEMPERATE,
                    verdict = VerdictUiState.Occupied(holder = "Held by kepler"),
                ),
                WorldRowUiState(
                    coordinate = "[2:118:6]",
                    slot = 6,
                    band = OrbitBand.TEMPERATE,
                    verdict = VerdictUiState.Unsurveyed,
                ),
                WorldRowUiState(
                    coordinate = "[2:118:8]",
                    slot = 8,
                    band = OrbitBand.TEMPERATE,
                    verdict = VerdictUiState.Blocked(
                        failures = listOf(blocked("gravity", "2.40", "1.40", "g", AdaptationTechnology.GRAVITIC, 9)),
                        // Over the bar its own calibration line names, which is the pillar in one
                        // row: the good ground is behind the technology nobody has bought.
                        yieldLabel = "yield 1.04",
                        calibration = "Fails 1 of 3 bands, worth it at 0.92",
                        detail = "118 fields · ion storms",
                    ),
                ),
                WorldRowUiState(
                    coordinate = "[2:118:9]",
                    slot = 9,
                    band = OrbitBand.TEMPERATE,
                    verdict = VerdictUiState.Barren(
                        yieldLabel = "yield 0.81",
                        threshold = "Passes every band, worth it at 0.92",
                        detail = "96 fields · tidally locked",
                    ),
                ),
            ),
        ),
        OrbitBandUiState(
            band = OrbitBand.COLD,
            rows = listOf(
                WorldRowUiState(
                    coordinate = "[2:118:11]",
                    slot = 11,
                    band = OrbitBand.COLD,
                    verdict = VerdictUiState.Settleable(
                        yieldLabel = "yield 1.12",
                        richness = "metal 1.21 · crystal 0.88 · deut 0.64",
                        detail = "163 fields · seismic instability",
                    ),
                ),
            ),
        ),
    ),
)

// ── The six states of the card footer ────────────────────────────────────────────────────────
//
// One frame each, on the unsurveyed neighbour, because what the footer says is the only thing that
// changes between them and a baseline that also moved the world list would be asserting two things
// at once. `available` and `known` come from the real mapper — a fresh colony can afford its first
// probe and its home system was surveyed at genesis — and the other four are written out, because
// reaching them from a `GameState` means dispatching, advancing a clock and landing on a system the
// seed happens to have stocked the right way.

internal val probeUnaffordableUiState: GalaxyUiState = unsurveyedSystemUiState.copy(
    probe = ProbeActionUiState.Unaffordable(
        offer = dispatchOffer(short = true),
        // The tightest reading on the screen: two durations on one row, told apart by side, by
        // colour and by the preposition.
        availableIn = "in 1h 06m",
    ),
)

internal val probeInFlightUiState: GalaxyUiState = unsurveyedSystemUiState.copy(
    probe = ProbeActionUiState.InFlight(countdown = "00:47:12", lands = "lands 12:20", progressPercent = 43),
)

// The answer about fifty-nine dispatches in sixty, and the one this frame exists to make ordinary.
internal val probeLandedUiState: GalaxyUiState = homeSystemUiState.copy(
    probe = ProbeActionUiState.Landed(
        landedAt = "Probe landed 12:20",
        summary = "5 worlds surveyed",
        find = "none settleable",
        findKind = ProbeFindKind.NONE,
    ),
)

// Green once, and only on the count. The state the whole verb exists for.
internal val probeSettleableUiState: GalaxyUiState = homeSystemUiState.copy(
    probe = ProbeActionUiState.Landed(
        landedAt = "Probe landed 09:04",
        summary = "3 worlds surveyed",
        find = "1 settleable",
        findKind = ProbeFindKind.SETTLEABLE,
    ),
)

// The middle tier: neither green nor red, because it is worth reading and not worth acting on.
internal val probeNearMissUiState: GalaxyUiState = homeSystemUiState.copy(
    probe = ProbeActionUiState.Landed(
        landedAt = "Probe landed 21:47",
        summary = "4 worlds surveyed",
        find = "1 blocked at one axis",
        findKind = ProbeFindKind.NEAR_MISS,
    ),
)

// One system in 390: fifteen ticks, no dots, and a card that refuses the sale in the words of the
// thing above it. The world list under it is empty too, which is the frame's other half.
internal val probeNothingToSurveyUiState: GalaxyUiState = unsurveyedSystemUiState.copy(
    probe = ProbeActionUiState.NothingToSurvey(note = "15 empty slots · nothing to survey"),
    map = SystemMapUiState(slots = (1..15).map { MapSlotUiState(slot = it, mark = MapMark.EMPTY) }),
    bands = emptyList(),
)

// 150 metal, and the flight to the neighbour this fixture's frames are drawn on. Taken from the
// real mapper so the chip, the words and the duration are the ones the app produces.
private fun dispatchOffer(short: Boolean = false): ProbeOfferUiState {
    val offer = when (val probe = unsurveyedSystemUiState.probe) {
        is ProbeActionUiState.Dispatch -> probe.offer
        else -> error("the neighbour of a fresh colony's home must be a system it can be sent a probe")
    }
    return if (short) offer.copy(cost = offer.cost.copy(short = true)) else offer
}

// Borrowed from the real home world rather than retyped, so the one hand-written frame still shows
// the same axis line the app produces — non-breaking spaces included.
private fun homeAxes(): String {
    val home = homeSystemUiState.bands.flatMap { it.rows }.first { it.verdict is VerdictUiState.Home }
    return (home.verdict as VerdictUiState.Home).axes
}

// The unit is joined to its value by U+00A0 in the mapper, and a fixture that used an ordinary
// space would render a line that wraps differently from the real one.
private fun blocked(
    axis: String,
    reading: String,
    band: String,
    unit: String,
    technology: AdaptationTechnology,
    level: Int,
) =
    BlockedAxisUiState(
        axis = axis,
        reading = reading,
        tolerated = band + ' ' + unit,
        technology = technology,
        label = "${technology.name.lowercase().replaceFirstChar { it.uppercase() }} $level",
    )

private fun marks(vararg occupied: Pair<Int, MapMark>): List<MapSlotUiState> {
    val bySlot = occupied.toMap()
    return (1..15).map { slot -> MapSlotUiState(slot = slot, mark = bySlot[slot] ?: MapMark.EMPTY) }
}
