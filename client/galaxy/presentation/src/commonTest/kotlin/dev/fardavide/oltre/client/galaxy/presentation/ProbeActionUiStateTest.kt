package dev.fardavide.oltre.client.galaxy.presentation

import dev.fardavide.oltre.client.design.component.WatchSquareUiState
import dev.fardavide.oltre.client.design.text.Strings
import dev.fardavide.oltre.client.design.text.English
import dev.fardavide.oltre.client.design.text.TextRes
import dev.fardavide.oltre.client.galaxy.ui.GalaxyUiState
import dev.fardavide.oltre.client.galaxy.ui.GalaxyBodyUiState
import dev.fardavide.oltre.client.galaxy.ui.ProbeActionUiState
import dev.fardavide.oltre.client.galaxy.ui.ProbeFindKind
import dev.fardavide.oltre.client.galaxy.ui.ProbeOfferUiState
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
import dev.fardavide.oltre.core.YardJob
import dev.fardavide.oltre.client.design.format.toChipLabel
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant
import kotlinx.datetime.TimeZone

// The footer of the system card: the one place the fourth verb is bought, and the one place it
// reports back. Six states, and which one a system is in is entirely derived — nothing about the
// probe is stored outside `surveys` and the event log.
class ProbeActionUiStateTest {

    @Test
    fun `a system nobody has been to offers the flight and its price`() {
        // given
        val state = wealthy()

        // when
        val action = state.probeActionAt(awayFromHome(state, systemsAway = 52))

        // then — the price is the same everywhere and the flight is the only figure that moves,
        // which is why they are drawn in that order and only one of them is worth reading twice
        val dispatch = assertIs<ProbeActionUiState.Dispatch>(action)
        assertEquals("${SurveyBalance.COST_METAL}", English.resolve(dispatch.offer.cost.amount))
        assertTrue(English.resolve(dispatch.offer.flight).startsWith("flight "), "was '${dispatch.offer.flight}'")
        assertEquals("1h 22m", English.resolve(dispatch.offer.compactFlight))
        assertEquals("flight 1h 22m", English.resolve(dispatch.offer.flight))
    }

    @Test
    fun `the flight is the distance the player is actually buying`() {
        // given 30 minutes plus a minute a system, which is the whole of what a dispatch decides
        val state = wealthy()

        // then
        assertEquals("31m", English.resolve(offerAt(state, systemsAway = 1).compactFlight))
        assertEquals("1h 00m", English.resolve(offerAt(state, systemsAway = 30).compactFlight))
    }

    @Test
    fun `a colony that cannot pay is told when it could`() {
        // given the genesis rail the design flagged: at 84 metal every system in the galaxy is
        // this state for the first hour, and the ghost is the only thing the screen says about
        // probes
        val state = fresh().copy(resources = Resources.of(metal = SurveyBalance.COST_METAL - 60))

        // when
        val action = state.probeActionAt(awayFromHome(state, systemsAway = 9))

        // then — the committed idiom: the chip reddens, the verb becomes a ghost carrying the wait
        val short = assertIs<ProbeActionUiState.Unaffordable>(action)
        assertTrue(short.offer.cost.short, "the one resource you are short of is what reddens")
        assertTrue(English.resolve(short.availableIn).startsWith("in "), "was '${short.availableIn}'")
    }

    @Test
    fun `two durations share a row and are told apart by the word in`() {
        // The tightest reading on the sheet: "flight 39m" is how long the probe takes and
        // "in 1h 06m" is how long until you can send it. Only one of them has a preposition.
        val state = fresh().copy(resources = Resources.of(metal = SurveyBalance.COST_METAL - 60))
        val short = assertIs<ProbeActionUiState.Unaffordable>(
            state.probeActionAt(awayFromHome(state, systemsAway = 9)),
        )

        assertTrue("in " !in English.resolve(short.offer.flight), "the flight must not read as a wait")
        assertTrue(English.resolve(short.availableIn).startsWith("in "))
    }

    // ── The hull, which is asked about before the money ─────────────────────────────────────

    @Test
    fun `a colony with no scout is told what it needs rather than when`() {
        // **The dead control this whole layer exists to prevent.** `startSurvey` refuses without an
        // idle `SCOUT`, so the footer must not draw a verb — and the note has to name the *Shipyard*
        // rather than a wait, because unlike every other unaffordable state in the game this one is
        // not answered by standing still.
        val state = wealthy().copy(ships = Ships.NONE)

        val action = state.probeActionAt(awayFromHome(state, systemsAway = 9))

        val short = assertIs<ProbeActionUiState.Unaffordable>(action)
        assertEquals("needs a scout", English.resolve(short.availableIn))
        assertTrue(!short.offer.cost.short, "the metal is not what is short here")
    }

    @Test
    fun `a scout on its way home is a wait and the footer gives the date`() {
        // The other half: when a hull genuinely is coming back, a countdown is the honest answer and
        // the Shipyard is not the advice. One scout, sent, so the pool is empty and the probe that
        // emptied it is the thing being waited on.
        val state = wealthy()
        val out = assertIs<StartSurveyResult.Started>(
            startSurvey(state, awayFromHome(state, systemsAway = 40), at = EPOCH),
        ).state
        assertEquals(Ships.NONE, out.ships)

        val short = assertIs<ProbeActionUiState.Unaffordable>(
            out.probeActionAt(awayFromHome(out, systemsAway = 9)),
        )

        assertTrue(English.resolve(short.availableIn).startsWith("in "), "was '${short.availableIn}'")
    }

    @Test
    fun `a scout on the slipway beats a probe that lands later`() {
        // **The elvis consulted the yard only when nothing was out**, and the two are alternative
        // sources of the same hull. Own one scout, send it far, then buy another: the footer quoted
        // the probe's landing while the purchase was hours nearer. Always in the overstating
        // direction, on the screen whose whole job is an honest countdown.
        val sent = assertIs<StartSurveyResult.Started>(
            startSurvey(wealthy(), awayFromHome(wealthy(), systemsAway = 200), at = EPOCH),
        ).state
        val landing = sent.surveys.single().completesAt
        val buying = sent.copy(
            yard = listOf(YardJob(ship = ShipType.SCOUT, startedAt = EPOCH, completesAt = EPOCH + 30.minutes)),
        )
        assertTrue(landing > EPOCH + 30.minutes, "the fixture's probe lands before the purchase")

        val short = assertIs<ProbeActionUiState.Unaffordable>(
            buying.probeActionAt(awayFromHome(buying, systemsAway = 9)),
        )

        assertEquals("in 30m", English.resolve(short.availableIn))
    }

    @Test
    fun `a probe that lands before the slipway finishes is still the answer`() {
        // The other side of the minimum, so neither source wins by default.
        val sent = assertIs<StartSurveyResult.Started>(
            startSurvey(wealthy(), awayFromHome(wealthy(), systemsAway = 1), at = EPOCH),
        ).state
        val landing = sent.surveys.single().completesAt
        val buying = sent.copy(
            yard = listOf(YardJob(ship = ShipType.SCOUT, startedAt = EPOCH, completesAt = EPOCH + 20.days)),
        )
        assertTrue(landing < EPOCH + 20.days)

        val short = assertIs<ProbeActionUiState.Unaffordable>(
            buying.probeActionAt(awayFromHome(buying, systemsAway = 9)),
        )

        assertEquals(
            English.resolve(Strings.availableIn((landing - EPOCH).toChipLabel())),
            English.resolve(short.availableIn),
        )
    }

    @Test
    fun `a probe in flight counts down inside the system it is aimed at`() {
        // given
        val state = wealthy()
        val target = awayFromHome(state, systemsAway = 40)
        val dispatched = assertIs<StartSurveyResult.Started>(startSurvey(state, target, at = EPOCH)).state

        // when — halfway through a 70-minute flight
        val action = dispatched.probeActionAt(target, now = EPOCH + 35.minutes)

        // then the three parts a running build already draws, in that order
        val flight = assertIs<ProbeActionUiState.InFlight>(action)
        assertEquals("00:35:00", English.resolve(flight.countdown))
        assertEquals(50, flight.progressPercent)
        assertTrue(English.resolve(flight.lands).startsWith("lands "), "was '${flight.lands}'")
    }

    @Test
    fun `a landing is a receipt for a flight that was paid for`() {
        // given
        val state = wealthy()
        val target = awayFromHome(state, systemsAway = 12)
        val landed = advance(
            assertIs<StartSurveyResult.Started>(startSurvey(state, target, at = EPOCH)).state,
            from = EPOCH,
            to = EPOCH + 2.days,
        )

        // when
        val action = landed.probeActionAt(target, now = EPOCH + 2.days)

        // then
        val receipt = assertIs<ProbeActionUiState.Landed>(action)
        assertTrue(English.resolve(receipt.landedAt).startsWith("Probe landed "), "was '${receipt.landedAt}'")
        assertTrue(
            "worlds surveyed" in English.resolve(receipt.summary) ||
                "world surveyed" in English.resolve(receipt.summary),
        )
        assertTrue(English.resolve(receipt.find).isNotEmpty())
    }

    @Test
    fun `the usual landing says none settleable in the same breath as the count`() {
        // ~59 dispatches in 60. Saying it beside the count is what keeps a run of these reading
        // as calibration rather than as bad luck — the same job the Barren row's threshold does.
        val state = wealthy()
        val landing = firstLandingWhere(state) { it.findKind == ProbeFindKind.NONE }

        assertEquals("none settleable", English.resolve(landing.find))
    }

    @Test
    fun `a system whose fifteen slots are empty refuses the sale and says why`() {
        // given the one system in 390 with nothing around its star
        val state = wealthy()
        val empty = firstWorldlessSystem(state.galaxy.seed)

        // when
        val action = state.probeActionAt(empty)

        // then — never "already surveyed", because nothing was
        val note = assertIs<ProbeActionUiState.NothingToSurvey>(action)
        assertEquals("${GalaxyBalance.SLOTS_PER_SYSTEM} empty slots · nothing to survey", English.resolve(note.note))
    }

    @Test
    fun `home was never flown to and the card does not imply it was`() {
        // given
        val state = wealthy()

        // when
        val action = state.probeActionAt(SystemAddress.of(state.galaxy.home))

        // then — one tertiary line, no receipt, no verb
        assertEquals(ProbeActionUiState.Charted(Strings.surveyedAtGenesis()), action)
    }

    @Test
    fun `nothing on this footer is a verb once the system is known`() {
        // The screen never offers a flight it would refuse, so every non-buyable state is a
        // sentence rather than a control. Pinned across all three of them at once.
        val state = wealthy()
        val landed = advance(
            assertIs<StartSurveyResult.Started>(
                startSurvey(state, awayFromHome(state, 12), at = EPOCH),
            ).state,
            from = EPOCH,
            to = EPOCH + 2.days,
        )

        for (action in listOf(
            state.probeActionAt(SystemAddress.of(state.galaxy.home)),
            state.probeActionAt(firstWorldlessSystem(state.galaxy.seed)),
            landed.probeActionAt(awayFromHome(state, 12), now = EPOCH + 2.days),
        )) {
            assertTrue(
                action !is ProbeActionUiState.Dispatch && action !is ProbeActionUiState.Unaffordable,
                "$action still offers a flight",
            )
        }
    }

    // ── fixtures ────────────────────────────────────────────────────────────────────────────

    private fun GameState.probeActionAt(
        target: SystemAddress,
        now: Instant = EPOCH,
        // The footer is the *system* view's furniture, so the frame has to be asked for that view:
        // neither map scale nor the worlds list prices a flight, and building one for them would be
        // paying for a footer nothing draws.
    ): ProbeActionUiState = assertIs<GalaxyBodyUiState.System>(
        toGalaxyUiState(
            nav = navigationAt(SystemSelection(galaxy = target.galaxy, system = target.system)),
            now = now,
            timeZone = TimeZone.UTC,
        ).body,
    ).probe

    // Everything a navigation still carries is furniture for the *other* three views — the query and
    // the discovery boundary belong to the worlds list — so the footer's frame is the selection and
    // three inert fields. It stopped needing a `GameState` at 0.12, when the chips it used to derive
    // from one went.
    private fun navigationAt(at: SystemSelection): GalaxyNavigation = GalaxyNavigation(
        view = GalaxyView.SYSTEM,
        at = at,
        query = "",
        seenAt = EPOCH,
    )

    private fun offerAt(state: GameState, systemsAway: Int): ProbeOfferUiState =
        assertIs<ProbeActionUiState.Dispatch>(
            state.probeActionAt(awayFromHome(state, systemsAway)),
        ).offer

    // The nearest landing whose result matches, found by asking the mapper rather than by
    // hardcoding a coordinate — the seed decides which systems hold what, and a fixture that
    // asserted one would be asserting the seed.
    @Test
    fun `the bell shows the answer the next flight would be sent with`() {
        // The square is a rendering of `announceFlights` and of nothing about this star: the ask is
        // written onto the job by `startSurvey`, so what the control shows before the tap is exactly
        // what the tap would send. A square derived from the *system* would be a different control
        // wearing the same glyph.
        val state = wealthy()
        val target = awayFromHome(state, systemsAway = 52)

        assertEquals(
            WatchSquareUiState.UNASKED,
            assertIs<ProbeActionUiState.Dispatch>(state.probeActionAt(target)).announce,
        )
        assertEquals(
            WatchSquareUiState.ASKED,
            assertIs<ProbeActionUiState.Dispatch>(
                state.copy(announceFlights = true).probeActionAt(target),
            ).announce,
        )
    }

    private fun firstLandingWhere(
        state: GameState,
        predicate: (ProbeActionUiState.Landed) -> Boolean,
    ): ProbeActionUiState.Landed {
        for (away in 1..GalaxyBalance.SYSTEMS_PER_GALAXY) {
            val target = awayFromHome(state, away)
            val started = startSurvey(state, target, at = EPOCH)
            if (started !is StartSurveyResult.Started) continue
            val landed = advance(started.state, from = EPOCH, to = EPOCH + 30.days)
            val action = landed.probeActionAt(target, now = EPOCH + 30.days)
            if (action is ProbeActionUiState.Landed && predicate(action)) return action
        }
        error("no landing within a galaxy of home matched")
    }

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
    // single galaxy of 250 is not guaranteed to hold one, and scanning only galaxy 1 was a fixture
    // that happened to work under some seeds and not this one.
    private fun firstWorldlessSystem(seed: GalaxySeed): SystemAddress {
        for (galaxy in 1..GalaxyBalance.GALAXIES) {
            for (system in 1..GalaxyBalance.SYSTEMS_PER_GALAXY) {
                val address = SystemAddress(galaxy = galaxy, system = system)
                if (worldsIn(seed, address) == 0) return address
            }
        }
        error("seed $seed generated no empty system at all")
    }

    // **A scout, because the footer asks about the hull before the money.** Every test in this file
    // that is about a *price* needs the hull check to have passed first, or it measures the wrong
    // refusal — so the pool carries one here and the tests that are about the pool empty it.
    private fun fresh(): GameState =
        GameState.initial(GalaxySeed(20_260_807)).copy(ships = Ships.of(ShipType.SCOUT, 1))

    private fun wealthy(): GameState = fresh().copy(resources = Resources.of(metal = 1_000_000))

    private companion object {
        val EPOCH: Instant = Instant.fromEpochMilliseconds(0)
    }
}
