package dev.fardavide.oltre.client.galaxy.presentation

import dev.fardavide.oltre.core.FleetBalance
import dev.fardavide.oltre.core.FleetRun
import dev.fardavide.oltre.core.GalaxyBalance
import dev.fardavide.oltre.core.GalaxyCoordinate
import dev.fardavide.oltre.core.GalaxySeed
import dev.fardavide.oltre.core.GalaxyState
import dev.fardavide.oltre.core.GameState
import dev.fardavide.oltre.core.ResourceKind
import dev.fardavide.oltre.core.Resources
import dev.fardavide.oltre.core.ShipType
import dev.fardavide.oltre.core.Ships
import dev.fardavide.oltre.core.World
import dev.fardavide.oltre.core.worldAt
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.hours
import kotlin.time.Instant
import kotlinx.datetime.TimeZone

// The arithmetic the sheet renders, with no sheet in front of it. Every claim here was reachable
// only through a Compose test until 0.7.1 — so the mapper that resolves three defaults, clamps the
// hull count to the pool and narrows the window ladder was asserted through a popup and nowhere
// else, which is a lot of pure arithmetic hiding behind an enter animation.
//
// Against a real generated galaxy rather than a fake one, for `GalaxyUiStateTest`'s reason: the
// screen exists to read what the seed produced, and a fixture would let the two drift.
class DispatchUiStateTest {

    @Test
    fun `the sheet opens on the resource the world is richer in`() {
        // The default that saves a tap on the commonest case: you came here because the row said
        // this world was good for something. Compared in the generator's own units rather than in
        // the priced basket — the player is choosing between two columns of the same number.
        val slot = runnableSlot()
        val world = worldAt(seed, homeSystemAt(slot))!!
        val richer = if (world.metalPerMillion >= world.crystalPerMillion) {
            ResourceKind.METAL
        } else {
            ResourceKind.CRYSTAL
        }

        val offer = assertIs<DispatchUiState.Offer>(dispatchAt(slot))

        assertEquals(richer, offer.gathering)
        // ...and the head line puts that same one first, so the eye lands on it before the control
        // below repeats it.
        assertTrue(offer.head.startsWith(if (richer == ResourceKind.METAL) "metal" else "crystal"), offer.head)
    }

    @Test
    fun `what the player touched is what the offer carries`() {
        val slot = runnableSlot()

        val offer = assertIs<DispatchUiState.Offer>(
            dispatchAt(slot, selection = selection(slot).copy(gathering = ResourceKind.CRYSTAL, window = 24.hours)),
        )

        assertEquals(ResourceKind.CRYSTAL, offer.gathering)
        assertEquals(24.hours, offer.window)
        assertEquals(24.hours, offer.windows.single { it.selected }.window)
    }

    @Test
    fun `the hull count is clamped to the pool rather than refused`() {
        // The pool shrinks on its own every time a run leaves, so a selection can outlive the fleet
        // that justified it. Clamping is what keeps the offer something `startRun` would honour.
        val slot = runnableSlot()
        val state = withSkiffs(3)

        val many = assertIs<DispatchUiState.Offer>(dispatchAt(slot, state, selection(slot).copy(ships = 99)))
        val none = assertIs<DispatchUiState.Offer>(dispatchAt(slot, state, selection(slot).copy(ships = 0)))

        assertEquals(3, many.shipCount)
        assertTrue(many.atMost)
        assertEquals("of 3 idle", many.pool)
        assertEquals(1, none.shipCount)
        assertTrue(none.atFewest)
    }

    @Test
    fun `a run defaults to the whole idle pool`() {
        val offer = assertIs<DispatchUiState.Offer>(dispatchAt(runnableSlot(), withSkiffs(4)))

        assertEquals(4, offer.shipCount)
        assertEquals("4 skiffs", offer.ships)
        assertTrue(offer.atMost)
    }

    @Test
    fun `the each line appears only when there is more than one hull to divide by`() {
        val slot = runnableSlot()

        val one = assertIs<DispatchUiState.Offer>(dispatchAt(slot, withSkiffs(1)))
        val several = assertIs<DispatchUiState.Offer>(dispatchAt(slot, withSkiffs(4)))

        // "132 each" beside "132 metal" is the same number printed twice.
        assertNull(one.perShip)
        assertEquals("1 skiff", one.ships)
        assertTrue(several.perShip.orEmpty().endsWith(" each"), several.perShip.orEmpty())
    }

    @Test
    fun `the ladder opens on three hours wherever three hours is offered`() {
        // 3h is the rhythm the measured cadence names. A default of the longest would send the first
        // skiff of a new colony away for a day on a tap nobody had thought about yet.
        val offer = assertIs<DispatchUiState.Offer>(dispatchAt(runnableSlot()))

        assertEquals(3.hours, offer.window)
        assertEquals(FleetBalance.WINDOWS, offer.windows.map { it.window })
        assertNull(offer.ladderNote, "a ladder that never narrowed has nothing to explain")
    }

    @Test
    fun `a window that will not fit is absent rather than dead and the sheet says why`() {
        // The only way to show "too far" without a control that refuses its own tap — and the rung
        // that vanishes is the copy: a ladder narrowing teaches distance before any sentence does.
        val far = SystemSelection(galaxy = home.galaxy + 1, system = home.system)
        val slot = firstWorldSlot(far)
        val target = GalaxyCoordinate(galaxy = far.galaxy, system = far.system, slot = slot)

        val offer = assertIs<DispatchUiState.Offer>(
            dispatchAt(slot, surveying(target), selection(slot), at = far),
        )

        assertTrue(offer.windows.size < FleetBalance.WINDOWS.size, offer.windows.toString())
        assertTrue(offer.ladderNote.orEmpty().endsWith("minutes on the surface."), offer.ladderNote.orEmpty())
        // The shortest rung that survived, never the longest — or the frontier would open on a day
        // away, which is not a tap anyone meant to make.
        assertEquals(offer.windows.first().window, offer.window)
    }

    @Test
    fun `a world nobody has looked at cannot be priced and hands back the flight that would fix it`() {
        val elsewhere = SystemSelection(galaxy = home.galaxy, system = home.system - 1)
        val slot = firstWorldSlot(elsewhere)

        val refusal = assertIs<DispatchUiState.Refuse>(dispatchAt(slot, at = elsewhere))

        assertTrue(refusal.title.endsWith("nobody has looked at."), refusal.title)
        // The one refusal in the app that hands back a verb — and only when the card above would
        // honour it, so the two can never disagree about whether a probe can be sent.
        assertIs<RefuseActionUiState.Probe>(refusal.action)
    }

    @Test
    fun `a fleet that is entirely away refuses and counts the first hull home`() {
        val slot = runnableSlot()
        val away = state.copy(ships = Ships.NONE, runs = listOf(runReturningIn(3.hours, slot)))

        val refusal = assertIs<DispatchUiState.Refuse>(dispatchAt(slot, away))

        assertEquals("Every skiff is away.", refusal.title)
        // A countdown rather than a dead button — the idiom the unaffordable probe already spends.
        assertEquals("in 03:00:00", assertIs<RefuseActionUiState.Waiting>(refusal.action).label)
    }

    @Test
    fun `a fleet that is away with nothing on its way back says so and offers nothing`() {
        val refusal = assertIs<DispatchUiState.Refuse>(dispatchAt(runnableSlot(), state.copy(ships = Ships.NONE)))

        assertEquals("Every skiff is away.", refusal.title)
        assertNull(refusal.action)
    }

    @Test
    fun `the world you are standing on is not a target`() {
        // `startRun` refuses your own world outright, so a row must never raise a sheet the verb
        // would throw away. The screen and the model agree rather than finding out afterwards.
        assertNull(dispatchAt(home.slot))
    }

    @Test
    fun `an empty slot is not a target`() {
        val empty = (1..GalaxyBalance.SLOTS_PER_SYSTEM).first { worldAt(seed, homeSystemAt(it)) == null }

        assertNull(dispatchAt(empty))
    }

    // ── The fixture ──────────────────────────────────────────────────────────────────────────

    private val galaxy: GalaxyState = GalaxyState.initial(GalaxySeed(SEED))
    private val seed: GalaxySeed = galaxy.seed
    private val home: GalaxyCoordinate = galaxy.home
    private val state: GameState = GameState.initial(seed).copy(galaxy = galaxy)
    private val homeSelection = SystemSelection(galaxy = home.galaxy, system = home.system)

    private fun withSkiffs(count: Int): GameState = state.copy(ships = Ships.of(ShipType.SKIFF, count))

    private fun surveying(target: GalaxyCoordinate): GameState =
        withSkiffs(1).let { it.copy(galaxy = it.galaxy.copy(surveyed = it.galaxy.surveyed + target)) }

    private fun homeSystemAt(slot: Int): GalaxyCoordinate =
        GalaxyCoordinate(galaxy = home.galaxy, system = home.system, slot = slot)

    private fun selection(slot: Int): DispatchSelection =
        DispatchSelection(slot = slot, gathering = null, ships = null, window = null)

    // The home system's first slot that holds a world the colony is not standing on. Read off the
    // seed rather than written down: the generator owns which slots hold something.
    private fun runnableSlot(): Int = (1..GalaxyBalance.SLOTS_PER_SYSTEM).first {
        it != home.slot && worldAt(seed, homeSystemAt(it)) != null
    }

    private fun firstWorldSlot(at: SystemSelection): Int = (1..GalaxyBalance.SLOTS_PER_SYSTEM).first {
        worldAt(seed, GalaxyCoordinate(galaxy = at.galaxy, system = at.system, slot = it)) != null
    }

    private fun runReturningIn(duration: kotlin.time.Duration, slot: Int): FleetRun = FleetRun(
        target = homeSystemAt(slot),
        ships = Ships.of(ShipType.SKIFF, 1),
        gathering = ResourceKind.METAL,
        cargo = Resources.of(),
        dispatchedAt = EPOCH,
        returnsAt = EPOCH + duration,
    )

    private fun worldsIn(at: SystemSelection): List<World> = (1..GalaxyBalance.SLOTS_PER_SYSTEM)
        .mapNotNull { worldAt(seed, GalaxyCoordinate(galaxy = at.galaxy, system = at.system, slot = it)) }

    private fun dispatchAt(
        slot: Int,
        state: GameState = withSkiffs(1),
        selection: DispatchSelection = selection(slot),
        at: SystemSelection = homeSelection,
    ): DispatchUiState? = state.toDispatchUiState(
        at = at,
        selection = selection,
        // The real one, never a stand-in: the refusal on an unsurveyed world offers a probe only
        // when the card above it would honour one, and a hand-made state here would be a second
        // copy of exactly the decision that pairing exists to keep single.
        probe = state.toProbeActionUiState(at = at, worlds = worldsIn(at), now = EPOCH, timeZone = TimeZone.UTC),
        now = EPOCH,
    )

    private val World.metalPerMillion: Int get() = traits.metalRichness.perMillion

    private val World.crystalPerMillion: Int get() = traits.crystalRichness.perMillion

    private companion object {
        const val SEED = 20_260_807L

        val EPOCH: Instant = Instant.fromEpochMilliseconds(0)
    }
}
