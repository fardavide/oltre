package dev.fardavide.oltre.client.galaxy.presentation

import dev.fardavide.oltre.client.dispatch.presentation.DispatchSelection
import dev.fardavide.oltre.client.galaxy.ui.SkyDepth
import dev.fardavide.oltre.client.galaxy.ui.SkyGeometry
import dev.fardavide.oltre.client.galaxy.ui.SkyScene
import dev.fardavide.oltre.client.galaxy.ui.SkySelection
import dev.fardavide.oltre.client.galaxy.ui.SkyUiState
import dev.fardavide.oltre.client.galaxy.ui.SkyView
import dev.fardavide.oltre.client.galaxy.ui.landing
import dev.fardavide.oltre.core.GalaxyBalance
import dev.fardavide.oltre.core.GalaxyCoordinate
import dev.fardavide.oltre.core.GameState
import dev.fardavide.oltre.core.Resources
import dev.fardavide.oltre.core.StartSurveyResult
import dev.fardavide.oltre.core.SystemAddress
import dev.fardavide.oltre.core.WorldVerdict
import dev.fardavide.oltre.core.startSurvey
import dev.fardavide.oltre.core.verdictFor
import dev.fardavide.oltre.core.worldAt
import kotlin.test.assertIs
import kotlin.time.Instant
import kotlinx.datetime.TimeZone

// **Every frame the Galaxy tab is photographed in, derived from a real `GameState` through the real
// mapper.** Since 0.11 a frame is the same call the app makes, so a mapper that re-words anything
// moves a baseline — which is what a baseline is for. See the header this file carried before One
// Sky for the three thousand lines of stated fixtures that rule replaced.
//
// **A frame is now a picture and an eye.** The old tab had three pages and a frame named one of
// them; the sky has one drawing and five zooms of it, so a frame carries the `SkyUiState` the mapper
// built for a selection at a depth *and* the `SkyView` that shows it — the zoom the step bar would
// fly to, centred where the bar would centre it. The harness holds the eye and the page draws it.

internal class SkyFrame(val uiState: SkyUiState, val view: SkyView)

// The colony every frame describes. One seed, so a coordinate means the same thing in all of them:
// home is 6:137, Teshezon, in Torux Blaze, with the light at genesis running 107…167.
internal val frameState: GameState = testGameState

internal fun frame(
    state: GameState = frameState,
    selection: SkySelection = SkySelection.System(SystemAddress.of(state.galaxy.home)),
    // The region about home, because that is what the tab lands on: a default that is the screen
    // a player actually opens on is the one worth having in every frame that does not say otherwise.
    depth: SkyDepth = SkyDepth.REGION,
    now: Instant = FIXTURE_NOW,
    dispatch: DispatchSelection? = null,
): SkyFrame {
    val uiState = state.toSkyUiState(
        selection = selection,
        depth = depth,
        now = now,
        timeZone = TimeZone.UTC,
        dispatch = dispatch,
    )
    // The landing frames the star itself; every other step of the bar frames what the step is
    // about, which for a region is its middle rather than one star in it.
    val landing = uiState.landing()
    val view = if (depth == landing.depth && selection == landing.selection) {
        landing
    } else {
        SkyScene(uiState).flightTo(landing, depth, selection)
    }
    return SkyFrame(uiState, view)
}

// A neighbour nobody has looked at: 249 systems in 250 are in this state on the day the slice
// ships, so it is the screen rather than a stage before the screen. One system out, so a genesis
// colony can afford the probe the caption offers.
internal fun GameState.neighbour(): SystemAddress = SystemAddress.of(galaxy.home).let {
    it.copy(system = (it.system + 1).coerceAtMost(GalaxyBalance.SYSTEMS_PER_GALAXY))
}

// ── the five depths, on the day the slice ships ────────────────────────────────────────────

internal val universeFrame: SkyFrame =
    frame(selection = SkySelection.Galaxy(frameState.galaxy.home.galaxy), depth = SkyDepth.UNIVERSE)

internal val galaxyFrame: SkyFrame = frame(
    selection = SkySelection.Region(frameState.galaxy.home.galaxy, SkyGeometry.regionOf(frameState.galaxy.home.system)),
    depth = SkyDepth.GALAXY,
)

// The landing: the region about home, framed on your star.
internal val regionFrame: SkyFrame = frame()

internal val systemFrame: SkyFrame = frame(depth = SkyDepth.SYSTEM)

internal val worldFrame: SkyFrame = frame(selection = SkySelection.World(frameState.galaxy.home), depth = SkyDepth.WORLD)

// ── the caption's other faces ──────────────────────────────────────────────────────────────

// The neighbour selected from the landing, which is the frame the probe verb ships on.
internal val unsurveyedStarFrame: SkyFrame = frame(selection = SkySelection.System(frameState.neighbour()))

// A colony rich enough to price the far side of its galaxy. Stated once, because a fixture that
// needs it needs the same one: forty thousand metal is a fortnight of mines and no more.
internal val wealthyState: GameState = frameState.copy(resources = Resources.of(metal = 40_000, crystal = 9_000))

// A star past the light: the caption prices the chart the flight would buy rather than the star.
internal val DARK_STAR: SystemAddress = SystemAddress(galaxy = frameState.galaxy.home.galaxy, system = 240)

internal val darkStarFrame: SkyFrame = frame(state = wealthyState, selection = SkySelection.System(DARK_STAR))

// A probe out to the neighbour, which is the one overlay no other frame carries: the flight path
// from home and a caption that reads a clock rather than offering a second flight.
internal val probeInFlightState: GameState = assertIs<StartSurveyResult.Started>(
    startSurvey(wealthyState, wealthyState.neighbour(), at = FIXTURE_NOW),
).state

internal val probeInFlightFrame: SkyFrame =
    frame(state = probeInFlightState, selection = SkySelection.System(probeInFlightState.neighbour()))

// A colony a fortnight in: it has surveyed a spread of systems, so the region has names and veins
// to count. Built by surveying rather than by hand-writing a set, so every world in it is one a
// probe could really have reached.
internal val wellTravelledState: GameState = frameState.surveying(systems = listOf(-7, -1, 2, 6, 13))

internal val wellTravelledFrame: SkyFrame = frame(state = wellTravelledState)

// The same fortnight, pinched in past the region's landing and short of the orbit view: the zoom
// where the worlds of every surveyed star are strung across its arm as grains. **A pinch and not a
// step**, so the view is the landing's eye at a zoom no step of the bar flies to; the depth read
// off it is still the region's, which is what the bar says.
internal val hoodFrame: SkyFrame = wellTravelledFrame.let { SkyFrame(it.uiState, it.view.copy(ppu = HOOD_PPU)) }

// Past the near edge of the neighbourhood's fade and well short of the orbit view's: the
// portraits at full strength, and the star's own name and the region's still drawn.
private const val HOOD_PPU: Float = 20f

// The orbit view of a star past the light: nothing sits about it but the fog's own word, because
// a socket is a charted fact and this star is not one.
internal val darkSystemFrame: SkyFrame = frame(state = wealthyState, selection = SkySelection.System(DARK_STAR), depth = SkyDepth.SYSTEM)

// A charted star no probe has been to, at the system depth: the orbit view draws sockets where the
// worlds would be, and the caption on one prices the probe that would fill it.
internal val SOCKET_SYSTEM: SystemAddress = SystemAddress(galaxy = frameState.galaxy.home.galaxy, system = 160)

internal val socketFrame: SkyFrame = frame(state = wealthyState, selection = SkySelection.System(SOCKET_SYSTEM), depth = SkyDepth.SYSTEM)

// A world in the home system a run may actually be sent to. Read off the seed rather than written
// down: a run's legality is `startRun`'s rule and not this file's guess — home is refused and so is
// a world somebody holds, so it is whichever world is neither.
internal val RUNNABLE: GalaxyCoordinate = frameState.let { state ->
    val home = state.galaxy.home
    (1..GalaxyBalance.SLOTS_PER_SYSTEM)
        .map { slot -> GalaxyCoordinate(galaxy = home.galaxy, system = home.system, slot = slot) }
        .first { at ->
            val world = worldAt(state.galaxy.seed, at)
            world != null && verdictFor(world, state).let { it !is WorldVerdict.Home && it !is WorldVerdict.Occupied }
        }
}

// In front of a world you may run to: the one frame whose caption carries the run verb.
internal val runnableWorldFrame: SkyFrame = frame(selection = SkySelection.World(RUNNABLE), depth = SkyDepth.WORLD)

// Surveys the systems at the given offsets from home, which is what a fortnight of probes buys.
private fun GameState.surveying(systems: List<Int>): GameState {
    val added = systems.flatMap { offset ->
        val system = (galaxy.home.system + offset).coerceIn(1, GalaxyBalance.SYSTEMS_PER_GALAXY)
        (1..GalaxyBalance.SLOTS_PER_SYSTEM)
            .map { slot -> GalaxyCoordinate(galaxy = galaxy.home.galaxy, system = system, slot = slot) }
            .filter { worldAt(galaxy.seed, it) != null }
    }
    return copy(galaxy = galaxy.copy(surveyed = galaxy.surveyed + added))
}
