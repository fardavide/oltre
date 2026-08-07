package dev.fardavide.oltre.client.galaxy.presentation

import dev.fardavide.oltre.core.GalaxySeed
import dev.fardavide.oltre.core.GalaxyState

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

// The home system exactly as generated: Home in slot 7, then three Blocked worlds, all three of
// which out-yield the worth-it threshold. That is the pillar landing — the good ground is behind
// the technology nobody has bought yet.
internal val homeSystemUiState: GalaxyUiState = galaxy.toGalaxyUiState(
    at = SystemSelection(galaxy = galaxy.home.galaxy, system = galaxy.home.system),
)

// The neighbour, and every system but one at ship time.
internal val unsurveyedSystemUiState: GalaxyUiState = galaxy.toGalaxyUiState(
    at = SystemSelection(galaxy = galaxy.home.galaxy, system = galaxy.home.system - 1),
)

// The first system of the first galaxy: the one place the back step has nothing to step to.
internal val edgeOfTheGalaxyUiState: GalaxyUiState = galaxy.toGalaxyUiState(
    at = SystemSelection(galaxy = 1, system = 1),
)

// The precedence, top to bottom, including the four states this seed's home system cannot show.
internal val everyVerdictUiState = GalaxyUiState(
    galaxies = (1..4).map { GalaxyTabUiState(label = "G$it", galaxy = it, selected = it == 2) },
    scope = "250 systems",
    coordinate = "2:118",
    detail = "BRIGHT · 6 worlds",
    compactDetail = "BRIGHT · 6",
    adaptationState = "Adaptation research lands later. You are at level 0.",
    atFirstSystem = false,
    atLastSystem = false,
    isHome = false,
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
                        failures = listOf(blocked("gravity", "2.40", "1.40", "g", "Gravitic 9")),
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

// Borrowed from the real home world rather than retyped, so the one hand-written frame still shows
// the same axis line the app produces — non-breaking spaces included.
private fun homeAxes(): String {
    val home = homeSystemUiState.bands.flatMap { it.rows }.first { it.verdict is VerdictUiState.Home }
    return (home.verdict as VerdictUiState.Home).axes
}

// The unit is joined to its value by U+00A0 in the mapper, and a fixture that used an ordinary
// space would render a line that wraps differently from the real one.
private fun blocked(axis: String, reading: String, band: String, unit: String, technology: String) =
    BlockedAxisUiState(
        axis = axis,
        reading = reading,
        tolerated = band + ' ' + unit,
        technology = technology,
    )

private fun marks(vararg occupied: Pair<Int, MapMark>): List<MapSlotUiState> {
    val bySlot = occupied.toMap()
    return (1..15).map { slot -> MapSlotUiState(slot = slot, mark = bySlot[slot] ?: MapMark.EMPTY) }
}
