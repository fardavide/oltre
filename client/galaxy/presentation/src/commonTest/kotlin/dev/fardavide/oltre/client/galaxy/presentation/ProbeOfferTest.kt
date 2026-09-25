package dev.fardavide.oltre.client.galaxy.presentation

import dev.fardavide.oltre.client.design.text.English
import dev.fardavide.oltre.client.dispatch.presentation.DispatchProbeOffer
import dev.fardavide.oltre.core.GalaxyBalance
import dev.fardavide.oltre.core.GalaxySeed
import dev.fardavide.oltre.core.GameState
import dev.fardavide.oltre.core.Resources
import dev.fardavide.oltre.core.ShipType
import dev.fardavide.oltre.core.Ships
import dev.fardavide.oltre.core.StartSurveyResult
import dev.fardavide.oltre.core.SurveyBalance
import dev.fardavide.oltre.core.SystemAddress
import dev.fardavide.oltre.core.advance
import dev.fardavide.oltre.core.startSurvey
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.time.Duration.Companion.days
import kotlin.time.Instant

// The probe the dispatch sheet's refusal offers, and when it offers nothing. **Present exactly where
// `startSurvey` would accept**, which is the whole contract: the sheet is raised on a world nobody
// has surveyed, and the one verb it can put under that refusal has to be a verb that works.
class ProbeOfferTest {

    @Test
    fun `a system nobody has been to is offered with its price and its flight`() {
        // given
        val state = wealthy()

        // when
        val offer = state.probeOfferFor(awayFromHome(state, systemsAway = 52))

        // then — the price is the same everywhere and the flight is the only figure that moves
        val dispatch = assertNotNull(offer)
        assertEquals("Dispatch probe", English.resolve(dispatch.label))
        assertEquals("${SurveyBalance.COST_METAL}", English.resolve(dispatch.cost))
        assertEquals("flight 1h 22m", English.resolve(dispatch.flight))
    }

    @Test
    fun `the flight is the distance the player is actually buying`() {
        // given 30 minutes plus a minute a system, which is the whole of what a dispatch decides
        val state = wealthy()

        // then
        assertEquals("flight 31m", English.resolve(offerAt(state, systemsAway = 1).flight))
        assertEquals("flight 1h 00m", English.resolve(offerAt(state, systemsAway = 30).flight))
    }

    @Test
    fun `a colony that cannot pay is offered nothing`() {
        // given the genesis rail: at 84 metal every system in the galaxy is this for the first hour
        val state = fresh().copy(resources = Resources.of(metal = SurveyBalance.COST_METAL - 60))

        // then — a refusal with no verb under it, never a verb the tap would refuse
        assertNull(state.probeOfferFor(awayFromHome(state, systemsAway = 9)))
    }

    @Test
    fun `a colony with no idle scout is offered nothing`() {
        // `startSurvey` refuses without an idle `SCOUT` before it looks at the metal, so a full bank
        // and an empty pool is still no verb.
        val state = wealthy().copy(ships = Ships.NONE)

        assertNull(state.probeOfferFor(awayFromHome(state, systemsAway = 9)))
    }

    @Test
    fun `a probe already in flight to the system is not offered a second time`() {
        // given
        val state = wealthy().copy(ships = Ships.of(ShipType.SCOUT, 2))
        val target = awayFromHome(state, systemsAway = 40)
        val dispatched = assertIs<StartSurveyResult.Started>(startSurvey(state, target, at = EPOCH)).state

        // then — a second scout is idle, so this is the flight refusing, not the pool
        assertNull(dispatched.probeOfferFor(target))
    }

    @Test
    fun `a system the probe has landed on is not offered again`() {
        // given
        val state = wealthy()
        val target = awayFromHome(state, systemsAway = 12)
        val landed = advance(
            assertIs<StartSurveyResult.Started>(startSurvey(state, target, at = EPOCH)).state,
            from = EPOCH,
            to = EPOCH + 2.days,
        )

        // then — the scout is home and the bank is full; what refuses is that nothing is left to survey
        assertNull(landed.probeOfferFor(target))
    }

    @Test
    fun `home was never flown to and is not offered either`() {
        val state = wealthy()

        assertNull(state.probeOfferFor(SystemAddress.of(state.galaxy.home)))
    }

    @Test
    fun `a charted system whose fifteen slots are empty is nothing to survey`() {
        // given the one system in 390 with nothing around its star, **and the light reaching it** —
        // `hasSurveyed` is vacuously true of it, and `startSurvey` refuses on exactly that.
        val state = wealthy()
        val empty = firstWorldlessSystem(state.galaxy.seed)
        val charted = state.copy(galaxy = state.galaxy.withCharted(empty))

        assertNull(charted.probeOfferFor(empty))
    }

    @Test
    fun `an uncharted system whose slots are empty is offered the flight rather than refused`() {
        // The other half: until a hull has been there, you cannot know it is empty, and the offer
        // must not be the thing that tells you.
        val state = wealthy()
        val empty = firstWorldlessSystem(state.galaxy.seed)

        assertNotNull(state.probeOfferFor(empty))
    }

    // ── fixtures ────────────────────────────────────────────────────────────────────────────

    private fun offerAt(state: GameState, systemsAway: Int): DispatchProbeOffer =
        assertNotNull(state.probeOfferFor(awayFromHome(state, systemsAway)))

    private fun awayFromHome(state: GameState, systemsAway: Int): SystemAddress {
        val home = state.galaxy.home
        val up = home.system + systemsAway
        val down = home.system - systemsAway
        return SystemAddress(
            galaxy = home.galaxy,
            system = if (up <= GalaxyBalance.SYSTEMS_PER_GALAXY) up else down.coerceAtLeast(1),
        )
    }

    // Roughly one system in 390 — 0.55^7 x 0.80^8 against the two slot occupancy rates — so a
    // single galaxy of 250 is not guaranteed to hold one.
    private fun firstWorldlessSystem(seed: GalaxySeed): SystemAddress {
        for (galaxy in 1..GalaxyBalance.GALAXIES) {
            for (system in 1..GalaxyBalance.SYSTEMS_PER_GALAXY) {
                val address = SystemAddress(galaxy = galaxy, system = system)
                if (worldsIn(seed, address) == 0) return address
            }
        }
        error("seed $seed generated no empty system at all")
    }

    // **A scout, because the verb asks about the hull before the money.** Every test here that is
    // about a price needs the hull check to have passed first, or it measures the wrong refusal.
    private fun fresh(): GameState =
        GameState.initial(GalaxySeed(20_260_807)).copy(ships = Ships.of(ShipType.SCOUT, 1))

    private fun wealthy(): GameState = fresh().copy(resources = Resources.of(metal = 1_000_000))

    private companion object {
        val EPOCH: Instant = Instant.fromEpochMilliseconds(0)
    }
}
