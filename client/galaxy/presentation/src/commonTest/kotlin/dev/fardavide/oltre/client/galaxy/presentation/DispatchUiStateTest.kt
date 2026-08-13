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
import kotlin.test.assertNotNull
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

    // ── The vein, which is where this sheet's mechanic actually lives ────────────────────────

    @Test
    fun `a chip carries the richness and what is left of it`() {
        val offer = assertIs<DispatchUiState.Offer>(dispatchAt(runnableSlot()))

        assertTrue(offer.metalDeposit.startsWith("richness "), offer.metalDeposit)
        assertTrue(offer.metalDeposit.endsWith("· deposit full"), offer.metalDeposit)
        assertTrue(offer.crystalDeposit.endsWith("· deposit full"), offer.crystalDeposit)
    }

    @Test
    fun `a worked world states what is left on the chip rather than the word`() {
        val slot = runnableSlot()
        val target = homeSystemAt(slot)
        val cap = state.galaxy.depositCap(target, ResourceKind.METAL)!!
        val worked = withSkiffs(1).let {
            it.copy(galaxy = it.galaxy.withTaken(target, ResourceKind.METAL, cap / 4, at = EPOCH))
        }

        val offer = assertIs<DispatchUiState.Offer>(dispatchAt(slot, state = worked))

        assertTrue(offer.metalDeposit.contains("/"), offer.metalDeposit)
        assertTrue(offer.crystalDeposit.endsWith("· deposit full"), offer.crystalDeposit)
    }

    @Test
    fun `the legs line says how long the fleet is actually working`() {
        // The invariant made visible with no copy at all — `working` reads the same everywhere on the
        // map, because the vein and the rate carry one multiplier.
        val offer = assertIs<DispatchUiState.Offer>(dispatchAt(runnableSlot()))

        assertTrue(offer.legs.contains("· working "), offer.legs)
        assertTrue(offer.compactLegs.contains("· working "), offer.compactLegs)
    }

    @Test
    fun `a clamped run says the whole deposit rather than printing the figure twice`() {
        val slot = runnableSlot()
        val offer = assertIs<DispatchUiState.Offer>(
            dispatchAt(slot, state = withSkiffs(8), selection = selection(slot).copy(window = 24.hours)),
        )

        assertEquals("the whole deposit", offer.perShip)
        // The headline figure already *is* the deposit, so nothing restates it.
        assertTrue(!offer.figure.contains("deposit"), offer.figure)
    }

    @Test
    fun `a clamped run names the hulls that bring nothing`() {
        val slot = runnableSlot()
        val offer = assertIs<DispatchUiState.Offer>(
            dispatchAt(slot, state = withSkiffs(8), selection = selection(slot).copy(window = 24.hours)),
        )

        val note = assertNotNull(offer.clampNote)
        assertTrue(note.contains("empt"), note)
        assertTrue(note.endsWith("brings nothing.") || note.endsWith("bring nothing."), note)
    }

    @Test
    fun `a note nobody can act on is not printed`() {
        // Earned rather than standing. One hull has no smaller fleet to send, so the clause would be
        // an instruction with no verb — and a note on every dispatch is furniture, which is what stops
        // the other two being read as instructions.
        val slot = runnableSlot()
        val unclamped = assertIs<DispatchUiState.Offer>(dispatchAt(slot))
        assertNull(unclamped.clampNote)

        val oneHull = assertIs<DispatchUiState.Offer>(
            dispatchAt(slot, state = withSkiffs(1), selection = selection(slot).copy(window = 24.hours)),
        )
        assertNull(oneHull.clampNote)
    }

    @Test
    fun `a window with hours to spare names the rung that brings the same`() {
        val slot = runnableSlot()
        val offer = assertIs<DispatchUiState.Offer>(
            dispatchAt(slot, state = withSkiffs(8), selection = selection(slot).copy(window = 24.hours)),
        )

        val note = assertNotNull(offer.rungNote)
        assertTrue(note.endsWith("window brings the same."), note)
    }

    @Test
    fun `an emptied world keeps its whole sheet and counts down to the ask`() {
        // Design's mode rather than a refusal: the controls stay live because the wait is a function
        // of the ask, so shrinking the ask is the remedy.
        val slot = runnableSlot()
        val target = homeSystemAt(slot)
        val cap = state.galaxy.depositCap(target, ResourceKind.METAL)!!
        val stripped = withSkiffs(1).let {
            it.copy(galaxy = it.galaxy.withTaken(target, ResourceKind.METAL, cap, at = EPOCH))
        }

        // The currency is named rather than defaulted: the sheet opens on whichever resource the
        // world is richer in, and this test is about the one that was emptied.
        val waiting = assertIs<DispatchUiState.Waiting>(
            dispatchAt(slot, state = stripped, selection = selection(slot).copy(gathering = ResourceKind.METAL)),
        )

        assertEquals("This deposit is empty.", waiting.title)
        assertTrue(waiting.windows.isNotEmpty(), "the ladder is still live")
        assertTrue(waiting.note.endsWith("Fewer skiffs, or a shorter window, is sooner."), waiting.note)
        assertTrue(waiting.metalDeposit.endsWith("· deposit empty"), waiting.metalDeposit)
    }

    @Test
    fun `the countdown moves when the ask does`() {
        // The finding the waiting state exists for: a big ask is often one no world can ever hold, so
        // the same world reads "never" to a fleet and a date to a single hull.
        val slot = runnableSlot()
        val target = homeSystemAt(slot)
        val cap = state.galaxy.depositCap(target, ResourceKind.METAL)!!
        val stripped = state.copy(
            ships = Ships.of(ShipType.SKIFF, 8),
            galaxy = state.galaxy.withTaken(target, ResourceKind.METAL, cap, at = EPOCH),
        )

        val big = assertIs<DispatchUiState.Waiting>(
            dispatchAt(
                slot,
                state = stripped,
                selection = selection(slot).copy(gathering = ResourceKind.METAL, window = 24.hours),
            ),
        )
        val small = assertIs<DispatchUiState.Waiting>(
            dispatchAt(
                slot,
                state = stripped,
                selection = selection(slot).copy(gathering = ResourceKind.METAL, ships = 1, window = 3.hours),
            ),
        )

        assertNull(big.wait, "a fleet's ask is bigger than the world: ${big.note}")
        assertNotNull(small.wait, small.note)
    }

    @Test
    fun `the hull that brings nothing is named by its ordinal and never by an off-by-one`() {
        // The one-idle-hull case is Design's own copy — "The 4th brings nothing." — and it is the
        // only branch where an ordinal is printed at all, so it is the only one that can get the
        // suffix wrong. Two hulls at a world one can empty is the smallest state that reaches it.
        val slot = runnableSlot()
        val target = homeSystemAt(slot)
        val cap = state.galaxy.depositCap(target, ResourceKind.METAL)!!
        // A world holding just over what one hull lifts on this window, so the second is the spare.
        val nearlyEmptied = state.copy(
            ships = Ships.of(ShipType.SKIFF, 2),
            galaxy = state.galaxy.withTaken(target, ResourceKind.METAL, cap - cap / 8, at = EPOCH),
        )

        val offer = assertIs<DispatchUiState.Offer>(
            dispatchAt(
                slot,
                state = nearlyEmptied,
                selection = selection(slot).copy(gathering = ResourceKind.METAL, ships = 2, window = 24.hours),
            ),
        )

        assertEquals("1 skiff empties it. The 2nd brings nothing.", offer.clampNote)
    }

    @Test
    fun `a world stripped of both resources says so in the plural`() {
        // The other half of the waiting title, and the state a player reaches by working one world
        // twice in a check-in rather than by anything exotic.
        val slot = runnableSlot()
        val target = homeSystemAt(slot)
        val stripped = state.copy(
            ships = Ships.of(ShipType.SKIFF, 1),
            galaxy = state.galaxy
                .withTaken(target, ResourceKind.METAL, state.galaxy.depositCap(target, ResourceKind.METAL)!!, EPOCH)
                .withTaken(
                    target,
                    ResourceKind.CRYSTAL,
                    state.galaxy.depositCap(target, ResourceKind.CRYSTAL)!!,
                    EPOCH,
                ),
        )

        val waiting = assertIs<DispatchUiState.Waiting>(
            dispatchAt(slot, state = stripped, selection = selection(slot).copy(gathering = ResourceKind.METAL)),
        )

        assertEquals("Both deposits are empty.", waiting.title)
        assertTrue(waiting.crystalDeposit.endsWith("· deposit empty"), waiting.crystalDeposit)
    }

    @Test
    fun `a rung that is already the shortest that empties the vein says nothing`() {
        // Earned rather than standing, the same rule the clamp clause follows: on the shortest rung
        // there is no shorter one to name, and a note on every dispatch would be furniture.
        val slot = runnableSlot()
        val shortest = FleetBalance.windowsFor(
            from = state.galaxy.home,
            to = homeSystemAt(slot),
        ).first()

        val offer = assertIs<DispatchUiState.Offer>(
            dispatchAt(slot, state = withSkiffs(8), selection = selection(slot).copy(window = shortest)),
        )

        assertNull(offer.rungNote)
    }

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
