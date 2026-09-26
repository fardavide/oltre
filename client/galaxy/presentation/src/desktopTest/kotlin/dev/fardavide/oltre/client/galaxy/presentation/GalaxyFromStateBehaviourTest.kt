package dev.fardavide.oltre.client.galaxy.presentation

import androidx.compose.ui.test.ExperimentalTestApi
import dev.fardavide.oltre.client.design.format.groupedByThousands
import dev.fardavide.oltre.client.design.text.English
import dev.fardavide.oltre.client.design.text.Strings
import dev.fardavide.oltre.client.dispatch.presentation.DispatchSelection
import dev.fardavide.oltre.client.dispatch.presentation.toDispatchUiState
import dev.fardavide.oltre.client.dispatch.ui.DispatchUiState
import dev.fardavide.oltre.client.galaxy.ui.GalaxyRobot
import dev.fardavide.oltre.client.galaxy.ui.SkyDepth
import dev.fardavide.oltre.client.galaxy.ui.SkyGeometry
import dev.fardavide.oltre.core.FleetBalance
import dev.fardavide.oltre.core.GalaxyBalance
import dev.fardavide.oltre.core.GalaxyCoordinate
import dev.fardavide.oltre.core.Research
import dev.fardavide.oltre.core.ResourceKind
import dev.fardavide.oltre.core.ShipType
import dev.fardavide.oltre.core.Ships
import dev.fardavide.oltre.core.StartRunResult
import dev.fardavide.oltre.core.StartSurveyResult
import dev.fardavide.oltre.core.SystemAddress
import dev.fardavide.oltre.core.WorldVerdict
import dev.fardavide.oltre.core.advance
import dev.fardavide.oltre.core.startRun
import dev.fardavide.oltre.core.startSurvey
import dev.fardavide.oltre.core.verdictFor
import dev.fardavide.oltre.core.worldAt
import dev.fardavide.oltre.core.systemNameAt
import dev.fardavide.oltre.core.worldNameAt
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import org.junit.Test

// **The screen that owns the eye, driven from a save.** A page harness holds one frame and the
// words on it never change; here the selection re-derives the caption, the bar and the count from
// the `GameState` under it, so what a tap *says* is what these assert. The gestures themselves are
// `GalaxyPageBehaviourTest`'s.
//
// Driven through the Robot, never through a raw node query — the shape `ResearchRobot` set.
@OptIn(ExperimentalTestApi::class)
class GalaxyFromStateBehaviourTest {

    @Test
    fun `a barren world within tolerance can still be harvested without adaptation`() {
        val target = (1..GalaxyBalance.SYSTEMS_PER_GALAXY).asSequence()
            .flatMap { system ->
                (1..GalaxyBalance.SLOTS_PER_SYSTEM).asSequence().map { slot ->
                    GalaxyCoordinate(testGameState.galaxy.home.galaxy, system, slot)
                }
            }
            .first { at ->
                val world = worldAt(testGameState.galaxy.seed, at)
                val surveyed = testGameState.copy(galaxy = testGameState.galaxy.copy(
                    surveyed = testGameState.galaxy.surveyed + at,
                ))
                world != null && verdictFor(world, surveyed) == WorldVerdict.Barren
            }
        val state = testGameState.copy(galaxy = testGameState.galaxy.copy(
            surveyed = testGameState.galaxy.surveyed + target,
        ))
        assertEquals(Research.initial(), state.research)
        val dispatched = mutableListOf<StartRunResult>()

        galaxyScreen(
            state = state,
            onDispatchRun = { at, resource, ships, window ->
                val result = startRun(state, at, resource, ships, window, FIXTURE_NOW)
                dispatched += result
                result is StartRunResult.Started
            },
        ) {
            walkTo(SystemAddress.of(target))
            tapStar(SystemAddress.of(target))
            tapWorld(target)
            tapWorld(target)
            assertTheCountReads("Barren · Yield")
            assertTheCaptionOffers("run")
            takeTheCaptionsVerb()
            bringBack(ResourceKind.METAL)
            send()
            assertNoSheet()
        }

        val run = assertIs<StartRunResult.Started>(dispatched.single()).state.runs.single()
        assertEquals(target, run.target)
        assertEquals(ResourceKind.METAL, run.gathering)
        assertTrue(run.cargo.metal > 0)
    }

    @Test
    fun `an adapted world beyond fleet range explains the propulsion requirement`() {
        val home = testGameState.galaxy.home
        val farGalaxy = (1..GalaxyBalance.GALAXIES).maxBy { galaxy ->
            FleetBalance.distanceUnits(home, GalaxyCoordinate(galaxy, 1, 1))
        }
        val target = (1..GalaxyBalance.SLOTS_PER_SYSTEM)
            .map { slot -> GalaxyCoordinate(farGalaxy, 1, slot) }
            .first { worldAt(testGameState.galaxy.seed, it) != null }
        val state = testGameState.copy(galaxy = testGameState.galaxy.copy(
            surveyed = testGameState.galaxy.surveyed + target,
        )).adaptedTo(target)
        assertTrue(FleetBalance.windowsFor(home, target, state.research, state.ships).isEmpty())
        var dispatches = 0

        galaxyScreen(state = state, onDispatchRun = { _, _, _, _ ->
            dispatches++
            true
        }) {
            openStep(SkyDepth.UNIVERSE)
            tapGalaxy(farGalaxy)
            tapGalaxy(farGalaxy)
            walkTo(SystemAddress.of(target))
            assertTheCaptionReads("units out")
            tapStar(SystemAddress.of(target))
            tapWorld(target)
            tapWorld(target)
            takeTheCaptionsVerb()
            assertTheSheetReads("Too far for any window.")
            assertTheSheetReads("Propulsion is what shortens the trip.")
            assertOffersNoRun()
            assertTheSheetHasNoBell()
        }

        assertEquals(0, dispatches)
    }

    @Test
    fun `a blocked planet explains its adaptation requirements without offering a harvesting run`() {
        val target = testGameState.galaxy.surveyed.first { at ->
            verdictFor(checkNotNull(worldAt(testGameState.galaxy.seed, at)), testGameState) is WorldVerdict.Blocked
        }
        val blocked = assertIs<WorldVerdict.Blocked>(verdictFor(checkNotNull(worldAt(testGameState.galaxy.seed, target)), testGameState))

        galaxyScreen(state = testGameState) {
            openStep(SkyDepth.SYSTEM)
            tapWorld(target)
            tapWorld(target)
            assertTheCountReads("Blocked")
            assertTheCaptionOffersNoVerb()
            assertTheCaptionFullyDisplays("Requires")
            listOf(ResourceKind.METAL, ResourceKind.CRYSTAL).forEach { resource ->
                val cap = requireNotNull(testGameState.galaxy.depositCap(target, resource))
                val reading = Strings.resourceReading(resource, Strings.depositFraction(cap.groupedByThousands(), cap.groupedByThousands()))
                assertTheCaptionFullyDisplays(English.resolve(reading))
            }
            blocked.failures.forEach { failure ->
                assertTheCaptionReads(English.resolve(Strings.namedLevel(Strings.adaptationName(failure.axis.adaptation), failure.closedAtLevel)))
            }
        }
    }

    @Test
    fun `the caption sends the probe to the star under the selection and not to home`() {
        // The defect this idiom exists to prevent: the verb is on the caption and the caption is
        // about the selection, so the target has to be read off the eye at the moment of the tap.
        val sent = mutableListOf<SystemAddress>()

        galaxyScreen(state = testGameState, onDispatchProbe = { sent += it }) {
            tapStar(NEIGHBOUR)
            assertTheCaptionOffers("probe")
            takeTheCaptionsVerb()
        }

        assertEquals(listOf(NEIGHBOUR), sent)
    }

    @Test
    fun `a star past the light is named by its address and priced by what the flight would chart`() {
        // **Fog's third tier, from the screen.** A dark star asks the generator nothing: no name, no
        // class, no world count. What the caption says is where it is and what a probe there buys,
        // because those are the only two facts that still make a choice.
        galaxyScreen(state = wealthyState) {
            walkTo(DARK_STAR)
            assertTheBarReads(SkyDepth.REGION, "226–250")
            assertTheCaptionReads("[6:240]")
            assertTheCaptionReads("uncharted")
            assertTheCaptionReads("charts")
            assertTheCaptionOffers("probe")
            assertTheCaptionDoesNotRead(systemNameAt(wealthyState.galaxy.seed, DARK_STAR.galaxy, DARK_STAR.system))
        }
    }

    @Test
    fun `diving into a dark star withholds the name the generator would give it`() {
        // The tier holds at every depth, not just from a distance: the system view of an uncharted
        // star is a socket-less orbit under an address, and the bar and the count say the address
        // too. A name that appeared on the dive would be the light reaching one star ahead of itself.
        val name = systemNameAt(wealthyState.galaxy.seed, DARK_STAR.galaxy, DARK_STAR.system)

        galaxyScreen(state = wealthyState) {
            walkTo(DARK_STAR)
            tapStar(DARK_STAR)
            assertTheBarReads(SkyDepth.SYSTEM, "[6:240]")
            assertTheCountReads("[6:240] · uncharted · charts")
            assertNothingReads(name)
        }
    }

    @Test
    fun `a probe that lands past the light widens it`() {
        // **What a probe into the dark is for.** The count under the bar is fog's whole readout, and
        // a landing moves it: the charted span grows to take the star in, and the number says by
        // how much. Read off the save rather than typed, because how far the light reaches is
        // `GalaxyState`'s arithmetic and not this file's.
        val flight = assertIs<StartSurveyResult.Started>(startSurvey(wealthyState, DARK_STAR, at = FIXTURE_NOW)).state
        val landed = advance(flight, from = FIXTURE_NOW, to = flight.surveys.single().completesAt)
        val charted = landed.galaxy.chartedCountIn(DARK_STAR.galaxy)
        assertTrue(charted > 61, "the light reaches $charted systems")

        galaxyScreen(state = landed) {
            assertTheCountReads("$charted of 250 charted")
        }
    }

    @Test
    fun `a probe that lands inside the light buys a survey and hardly any map`() {
        // The other half: the neighbour is already charted, so surveying it counts one more survey
        // and widens the light by the one system its own reach adds — against the eighty-odd a
        // star in the dark charts. The number is the widen's own, read off the save.
        val flight = assertIs<StartSurveyResult.Started>(startSurvey(wealthyState, NEIGHBOUR, at = FIXTURE_NOW)).state
        val landed = advance(flight, from = FIXTURE_NOW, to = flight.surveys.single().completesAt)
        val charted = landed.galaxy.chartedCountIn(NEIGHBOUR.galaxy)
        assertTrue(
            wealthyState.galaxy.wouldChart(NEIGHBOUR) < wealthyState.galaxy.wouldChart(DARK_STAR),
            "the neighbour charts ${wealthyState.galaxy.wouldChart(NEIGHBOUR)} to the dark star's ${wealthyState.galaxy.wouldChart(DARK_STAR)}",
        )

        galaxyScreen(state = landed) {
            assertTheCountReads("$charted of 250 charted · 2 surveyed")
        }
    }

    @Test
    fun `a star with a probe on the way counts down and offers nothing`() {
        // One flight per star, and the caption has room to say when it lands or to offer it, never
        // both: a second verb here would be a second probe to a star the first is already buying.
        galaxyScreen(state = probeInFlightState) {
            tapStar(NEIGHBOUR)
            assertTheCaptionReads("lands in")
            assertTheCaptionOffersNoVerb()
        }
    }

    @Test
    fun `a colony with no scout is shown the price and not sold the flight`() {
        // A probe flies a `SCOUT`, so a caption that read the stores alone would offer a verb
        // `startSurvey` refuses. The price stays — it is the reading that says what to build — and
        // the verb goes.
        galaxyScreen(state = testGameState.copy(ships = Ships.of(ShipType.SKIFF, 1))) {
            tapStar(NEIGHBOUR)
            assertTheCaptionReads("probe")
            assertTheCaptionOffersNoVerb()
        }
    }

    @Test
    fun `a world nobody has looked at is a socket that offers the probe rather than the run`() {
        // **A world is never drawn under an unsurveyed star**, but a charted one has sockets — a slot
        // and a name each — and the caption on a socket prices the flight that would fill it. The
        // run is not offered because there is nothing to price it against.
        val socket = wealthyState.worldsOf(SOCKET_SYSTEM).first().at

        galaxyScreen(state = wealthyState) {
            walkTo(SOCKET_SYSTEM)
            tapStar(SOCKET_SYSTEM)
            tapWorld(socket)
            assertTheCaptionReads("unsurveyed · slot ${socket.slot}")
            assertTheCaptionOffers("probe")
            assertTheCaptionDoesNotRead("run")
        }
    }

    @Test
    fun `in front of a socket the count line says what the caption said`() {
        // The count follows the depth: at the world depth it is the world's own sentence, and a
        // socket's sentence is the slot and the probe's price rather than a verdict — there is no
        // world under it to judge. The same words as the caption's, because a socket has no others.
        val socket = wealthyState.worldsOf(SOCKET_SYSTEM).first().at

        galaxyScreen(state = wealthyState) {
            walkTo(SOCKET_SYSTEM)
            tapStar(SOCKET_SYSTEM)
            tapWorld(socket)
            tapWorld(socket)
            assertTheBarReads(SkyDepth.WORLD, worldNameAt(wealthyState.galaxy.seed, socket))
            assertTheCountReads("unsurveyed · slot ${socket.slot} · probe")
        }
    }

    @Test
    fun `the run's figure reprices when the sheet is asked for the other resource`() {
        // The sheet is the stateful screen's, so the reprice is asserted here: a chip is a change of
        // ask, and the figure under it is `FleetBalance.cargo` for that ask — read off the mapper
        // rather than typed, so this is a claim about the screen and not about the balance.
        val onCrystal = assertIs<DispatchUiState.Offer>(
            testGameState.adaptedTo(RUNNABLE).toDispatchUiState(
                selection = DispatchSelection(at = RUNNABLE, gathering = ResourceKind.CRYSTAL, ships = null, window = null),
                probe = null,
                now = FIXTURE_NOW,
            ),
        )

        galaxyScreen(state = testGameState.adaptedTo(RUNNABLE)) {
            openStep(SkyDepth.SYSTEM)
            tapWorld(RUNNABLE)
            tapWorld(RUNNABLE)
            takeTheCaptionsVerb()
            bringBack(ResourceKind.CRYSTAL)
            assertTheSheetReads(English.resolve(onCrystal.figure))
        }
    }

    @Test
    fun `another galaxy is priced in units out rather than in systems`() {
        // A hop is 250 units whichever star you leave from, so the caption on a galaxy counts units
        // and never systems — the figure a system caption uses would be a lie one galaxy over.
        galaxyScreen(state = testGameState) {
            openStep(SkyDepth.UNIVERSE)
            tapGalaxy(7)
            assertTheCaptionReads("Galaxy 7")
            assertTheCaptionReads("units out")
            assertTheCaptionReads("uncharted")
            assertTheCaptionOffers("open")
        }
    }

    // The walk from the landing to a star that is not on it: out to the galaxy, into the region the
    // star is in, and onto the star. The bar spells the region as a span until a star is selected.
    private fun GalaxyRobot.walkTo(star: SystemAddress) {
        val region = SkyGeometry.regionOf(star.system)
        openStep(SkyDepth.GALAXY)
        tapRegion(star.galaxy, region)
        tapRegion(star.galaxy, region)
        tapStar(star)
    }

    private companion object {
        val NEIGHBOUR: SystemAddress = testGameState.neighbour()
    }
}
