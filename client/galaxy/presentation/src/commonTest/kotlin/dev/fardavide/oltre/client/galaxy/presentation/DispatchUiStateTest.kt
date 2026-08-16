package dev.fardavide.oltre.client.galaxy.presentation

import dev.fardavide.oltre.client.dispatch.presentation.DispatchSelection
import dev.fardavide.oltre.client.dispatch.presentation.toDispatchUiState
import dev.fardavide.oltre.client.dispatch.ui.DispatchUiState
import dev.fardavide.oltre.client.dispatch.ui.RefuseActionUiState
import dev.fardavide.oltre.client.galaxy.ui.GalaxyUiState
import dev.fardavide.oltre.client.galaxy.ui.ProbeActionUiState
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
import dev.fardavide.oltre.core.worldNameAt
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
        val target = runnable()
        val world = worldAt(seed, target)!!
        val richer = if (world.metalPerMillion >= world.crystalPerMillion) {
            ResourceKind.METAL
        } else {
            ResourceKind.CRYSTAL
        }

        val offer = assertIs<DispatchUiState.Offer>(dispatchAt(target))

        assertEquals(richer, offer.gathering)
        // **The head stopped carrying the richness at 0.13** and the chips carry it alone — the head
        // is the address and the hazards now, because the address had to go somewhere when the name
        // took the title slot and the chips were already printing both readings. So what the head
        // still has to lead with is the world's address.
        assertTrue(offer.head.startsWith(target.label()), offer.head)
    }

    @Test
    fun `what the player touched is what the offer carries`() {
        val target = runnable()

        val offer = assertIs<DispatchUiState.Offer>(
            dispatchAt(
                target,
                selection = selection(target).copy(gathering = ResourceKind.CRYSTAL, window = 24.hours),
            ),
        )

        assertEquals(ResourceKind.CRYSTAL, offer.gathering)
        assertEquals(24.hours, offer.window)
        assertEquals(24.hours, offer.windows.single { it.selected }.window)
    }

    @Test
    fun `the hull count is clamped to the pool rather than refused`() {
        // The pool shrinks on its own every time a run leaves, so a selection can outlive the fleet
        // that justified it. Clamping is what keeps the offer something `startRun` would honour.
        val target = runnable()
        val state = withSkiffs(3)

        val many = assertIs<DispatchUiState.Offer>(dispatchAt(target, state, selection(target).copy(ships = 99)))
        val none = assertIs<DispatchUiState.Offer>(dispatchAt(target, state, selection(target).copy(ships = 0)))

        assertEquals(3, many.shipCount)
        assertTrue(many.atMost)
        assertEquals("of 3 idle", many.pool)
        assertEquals(1, none.shipCount)
        assertTrue(none.atFewest)
    }

    @Test
    fun `a run defaults to the whole idle pool`() {
        val offer = assertIs<DispatchUiState.Offer>(dispatchAt(runnable(), withSkiffs(4)))

        assertEquals(4, offer.shipCount)
        assertEquals("4 skiffs", offer.ships)
        assertTrue(offer.atMost)
    }

    @Test
    fun `the each line appears only when there is more than one hull to divide by`() {
        val target = runnable()

        val one = assertIs<DispatchUiState.Offer>(dispatchAt(target, withSkiffs(1)))
        val several = assertIs<DispatchUiState.Offer>(dispatchAt(target, withSkiffs(4)))

        // "132 each" beside "132 metal" is the same number printed twice.
        assertNull(one.perShip)
        assertEquals("1 skiff", one.ships)
        assertTrue(several.perShip.orEmpty().endsWith(" each"), several.perShip.orEmpty())
    }

    @Test
    fun `the ladder opens on three hours wherever three hours is offered`() {
        // 3h is the rhythm the measured cadence names. A default of the longest would send the first
        // skiff of a new colony away for a day on a tap nobody had thought about yet.
        val offer = assertIs<DispatchUiState.Offer>(dispatchAt(runnable()))

        assertEquals(3.hours, offer.window)
        assertEquals(FleetBalance.WINDOWS, offer.windows.map { it.window })
        assertNull(offer.ladderNote, "a ladder that never narrowed has nothing to explain")
    }

    @Test
    fun `a window that will not fit is absent rather than dead and the sheet says why`() {
        // The only way to show "too far" without a control that refuses its own tap — and the rung
        // that vanishes is the copy: a ladder narrowing teaches distance before any sentence does.
        val far = SystemSelection(galaxy = home.galaxy + 1, system = home.system)
        val target = firstWorld(far)

        val offer = assertIs<DispatchUiState.Offer>(dispatchAt(target, surveying(target)))

        assertTrue(offer.windows.size < FleetBalance.WINDOWS.size, offer.windows.toString())
        assertTrue(offer.ladderNote.orEmpty().endsWith("minutes on the surface."), offer.ladderNote.orEmpty())
        // The shortest rung that survived, never the longest — or the frontier would open on a day
        // away, which is not a tap anyone meant to make.
        assertEquals(offer.windows.first().window, offer.window)
    }

    @Test
    fun `a world nobody has looked at cannot be priced and hands back the flight that would fix it`() {
        val elsewhere = SystemSelection(galaxy = home.galaxy, system = home.system - 1)

        val refusal = assertIs<DispatchUiState.Refuse>(dispatchAt(firstWorld(elsewhere)))

        assertTrue(refusal.title.endsWith("nobody has looked at."), refusal.title)
        // The one refusal in the app that hands back a verb — and only when the card above would
        // honour it, so the two can never disagree about whether a probe can be sent.
        assertIs<RefuseActionUiState.Probe>(refusal.action)
    }

    @Test
    fun `a refusal with no probe behind it offers nothing and prices nothing`() {
        // **`DispatchProbeOffer`'s whole contract**, and the state the Fleets tab is always in: it
        // has no map card above the sheet, so it passes null. The refusal still says why the world
        // cannot be priced; what it must not do is invent a flight, or quote a cost it was not
        // given.
        val elsewhere = SystemSelection(galaxy = home.galaxy, system = home.system - 1)
        val target = firstWorld(elsewhere)

        val refusal = assertIs<DispatchUiState.Refuse>(
            withSkiffs(1).toDispatchUiState(selection = selection(target), probe = null, now = EPOCH),
        )

        assertTrue(refusal.title.endsWith("nobody has looked at."), refusal.title)
        assertNull(refusal.action)
        // The sentence keeps its own half — how many worlds a probe would survey — and drops the
        // clause that quotes a price nobody quoted.
        assertTrue("A probe surveys all" in refusal.note, refusal.note)
        assertTrue("metal ·" !in refusal.note, refusal.note)
    }

    @Test
    fun `a fleet that is entirely away refuses and counts the first hull home`() {
        val target = runnable()
        val away = state.copy(ships = Ships.NONE, runs = listOf(runReturningIn(3.hours, target)))

        val refusal = assertIs<DispatchUiState.Refuse>(dispatchAt(target, away))

        assertEquals("Every skiff is away.", refusal.title)
        // A countdown rather than a dead button — the idiom the unaffordable probe already spends.
        assertEquals("in 03:00:00", assertIs<RefuseActionUiState.Waiting>(refusal.action).label)
    }

    @Test
    fun `a fleet that is away with nothing on its way back says so and offers nothing`() {
        val refusal = assertIs<DispatchUiState.Refuse>(dispatchAt(runnable(), state.copy(ships = Ships.NONE)))

        assertEquals("Every skiff is away.", refusal.title)
        assertNull(refusal.action)
    }

    @Test
    fun `a sheet is priced for the world the row named rather than for the system on screen`() {
        // **The ledger lists worlds from everywhere**, so which system the map happens to be parked
        // on says nothing about which world a row is. A selection that carried a slot alone read the
        // page's system for the other two thirds of the address and priced a different world — the
        // same slot of wherever the player last looked, which on the ledger's own screen is home.
        val elsewhere = SystemSelection(galaxy = home.galaxy, system = home.system - 1)
        val target = firstWorld(elsewhere)

        val sheet = assertNotNull(dispatchAt(target, state = surveying(target)))

        // **The name leads and the address is in the head** since 0.13 — Claude Design, so that a
        // tap from a list of named worlds lands on a sheet that looks like the row it came from.
        assertEquals(worldNameAt(seed, target), sheet.name)
        assertTrue(sheet.head.startsWith(target.label()), sheet.head)
    }

    @Test
    fun `the world you are standing on is not a target`() {
        // `startRun` refuses your own world outright, so a row must never raise a sheet the verb
        // would throw away. The screen and the model agree rather than finding out afterwards.
        assertNull(dispatchAt(home))
    }

    @Test
    fun `an empty slot is not a target`() {
        val empty = (1..GalaxyBalance.SLOTS_PER_SYSTEM).first { worldAt(seed, homeSystemAt(it)) == null }

        assertNull(dispatchAt(homeSystemAt(empty)))
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
        val offer = assertIs<DispatchUiState.Offer>(dispatchAt(runnable()))

        // Two strings rather than one: the card owns the word "richness" and prints it above this,
        // so a chip string that carried the word too rendered "richness richness 1.15".
        assertTrue(offer.metalRichness.first().isDigit(), offer.metalRichness)
        assertEquals("deposit full", offer.metalDeposit)
        assertEquals("deposit full", offer.crystalDeposit)
    }

    @Test
    fun `a worked world states what is left on the chip rather than the word`() {
        val target = runnable()
        val cap = state.galaxy.depositCap(target, ResourceKind.METAL)!!
        val worked = withSkiffs(1).let {
            it.copy(galaxy = it.galaxy.withTaken(target, ResourceKind.METAL, cap / 4, at = EPOCH))
        }

        val offer = assertIs<DispatchUiState.Offer>(dispatchAt(target, state = worked))

        assertTrue(offer.metalDeposit.contains("/"), offer.metalDeposit)
        assertEquals("deposit full", offer.crystalDeposit)
    }

    @Test
    fun `the legs line says how long the fleet is actually working`() {
        // The invariant made visible with no copy at all — `working` reads the same everywhere on the
        // map, because the vein and the rate carry one multiplier.
        val offer = assertIs<DispatchUiState.Offer>(dispatchAt(runnable()))

        assertTrue(offer.legs.contains("· working "), offer.legs)
        assertTrue(offer.compactLegs.contains("· working "), offer.compactLegs)
    }

    @Test
    fun `a clamped run says the whole deposit rather than printing the figure twice`() {
        val target = runnable()
        val offer = assertIs<DispatchUiState.Offer>(
            dispatchAt(target, state = withSkiffs(8), selection = selection(target).copy(window = 24.hours)),
        )

        assertEquals("the whole deposit", offer.perShip)
        // The headline figure already *is* the deposit, so nothing restates it.
        assertTrue(!offer.figure.contains("deposit"), offer.figure)
    }

    @Test
    fun `a clamped run names the hulls that bring nothing`() {
        val target = runnable()
        val offer = assertIs<DispatchUiState.Offer>(
            dispatchAt(target, state = withSkiffs(8), selection = selection(target).copy(window = 24.hours)),
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
        val target = runnable()
        val unclamped = assertIs<DispatchUiState.Offer>(dispatchAt(target))
        assertNull(unclamped.clampNote)

        val oneHull = assertIs<DispatchUiState.Offer>(
            dispatchAt(target, state = withSkiffs(1), selection = selection(target).copy(window = 24.hours)),
        )
        assertNull(oneHull.clampNote)
    }

    @Test
    fun `a window with hours to spare names the rung that brings the same`() {
        // Thirty-two hulls rather than eight: issue #68 made a vein four times as deep, so eight is
        // now the manifest a 24h rung is *right* for and the note it would print is no note at all.
        val target = runnable()
        val offer = assertIs<DispatchUiState.Offer>(
            dispatchAt(target, state = withSkiffs(32), selection = selection(target).copy(window = 24.hours)),
        )

        val note = assertNotNull(offer.rungNote)
        assertTrue(note.endsWith("window brings the same."), note)
    }

    @Test
    fun `an emptied world keeps its whole sheet and counts down to the ask`() {
        // Design's mode rather than a refusal: the controls stay live because the wait is a function
        // of the ask, so shrinking the ask is the remedy.
        val target = runnable()
        val cap = state.galaxy.depositCap(target, ResourceKind.METAL)!!
        val stripped = withSkiffs(1).let {
            it.copy(galaxy = it.galaxy.withTaken(target, ResourceKind.METAL, cap, at = EPOCH))
        }

        // The currency is named rather than defaulted: the sheet opens on whichever resource the
        // world is richer in, and this test is about the one that was emptied.
        val waiting = assertIs<DispatchUiState.Waiting>(
            dispatchAt(target, state = stripped, selection = selection(target).copy(gathering = ResourceKind.METAL)),
        )

        assertEquals("This deposit is empty.", waiting.title)
        assertTrue(waiting.windows.isNotEmpty(), "the ladder is still live")
        assertTrue(waiting.note.endsWith("Fewer skiffs, or a shorter window, is sooner."), waiting.note)
        assertEquals("deposit empty", waiting.metalDeposit)
    }

    @Test
    fun `the countdown moves when the ask does`() {
        // The finding the waiting state exists for: a big ask is often one no world can ever hold, so
        // the same world reads "never" to a fleet and a date to a single hull.
        val target = runnable()
        val cap = state.galaxy.depositCap(target, ResourceKind.METAL)!!
        val stripped = state.copy(
            ships = Ships.of(ShipType.SKIFF, 8),
            galaxy = state.galaxy.withTaken(target, ResourceKind.METAL, cap, at = EPOCH),
        )

        val big = assertIs<DispatchUiState.Waiting>(
            dispatchAt(
                target,
                state = stripped,
                selection = selection(target).copy(gathering = ResourceKind.METAL, window = 24.hours),
            ),
        )
        val small = assertIs<DispatchUiState.Waiting>(
            dispatchAt(
                target,
                state = stripped,
                selection = selection(target).copy(gathering = ResourceKind.METAL, ships = 1, window = 3.hours),
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
        val target = runnable()
        val cap = state.galaxy.depositCap(target, ResourceKind.METAL)!!
        // A world holding just over what one hull lifts on this window, so the second is the spare.
        val nearlyEmptied = state.copy(
            ships = Ships.of(ShipType.SKIFF, 2),
            galaxy = state.galaxy.withTaken(target, ResourceKind.METAL, cap - cap / 8, at = EPOCH),
        )

        val offer = assertIs<DispatchUiState.Offer>(
            dispatchAt(
                target,
                state = nearlyEmptied,
                selection = selection(target).copy(gathering = ResourceKind.METAL, ships = 2, window = 24.hours),
            ),
        )

        assertEquals("1 skiff empties it. The 2nd brings nothing.", offer.clampNote)
    }

    @Test
    fun `a world stripped of both resources says so in the plural`() {
        // The other half of the waiting title, and the state a player reaches by working one world
        // twice in a check-in rather than by anything exotic.
        val target = runnable()
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
            dispatchAt(target, state = stripped, selection = selection(target).copy(gathering = ResourceKind.METAL)),
        )

        assertEquals("Both deposits are empty.", waiting.title)
        assertEquals("deposit empty", waiting.crystalDeposit)
    }

    @Test
    fun `a rung that is already the shortest that empties the vein says nothing`() {
        // Earned rather than standing, the same rule the clamp clause follows: on the shortest rung
        // there is no shorter one to name, and a note on every dispatch would be furniture.
        val target = runnable()
        val shortest = FleetBalance.windowsFor(from = state.galaxy.home, to = target).first()

        val offer = assertIs<DispatchUiState.Offer>(
            dispatchAt(target, state = withSkiffs(8), selection = selection(target).copy(window = shortest)),
        )

        assertNull(offer.rungNote)
    }

    // ── The four readings the screenshot fixtures used to carry ─────────────────────────────
    //
    // **These are not new behaviour and they are not padding.** Until 0.9.1 `TestGalaxyUiState`
    // built its frames by calling this mapper, so a fixture aimed at another galaxy, or at a colony
    // with runs out, was quietly the only thing exercising these branches. The frames are stated by
    // hand now — a ui module cannot see a mapper — so what the images used to reach incidentally is
    // asserted here on purpose, which is where it should always have been.

    @Test
    fun `a target in another galaxy is priced as a whole galaxy away`() {
        val far = SystemSelection(galaxy = home.galaxy % GalaxyBalance.GALAXIES + 1, system = 1)
        val target = firstWorld(far)

        val offer = assertIs<DispatchUiState.Offer>(dispatchAt(target, state = surveying(target)))

        // The danger line names the crossing rather than a number of systems: from here, "how far"
        // stops being a count and becomes a different galaxy.
        assertTrue("another galaxy" in offer.danger, offer.danger)
        // And the ladder narrows rather than greying out — a round trip this long has no short
        // rungs to offer, which is the thing that teaches distance before any copy does.
        assertNotNull(offer.ladderNote)
        assertTrue(offer.windows.isNotEmpty())
    }

    @Test
    fun `the sheet says how much of the fleet is already out in the singular and the plural`() {
        val target = runnable()
        val other = (1..GalaxyBalance.SLOTS_PER_SYSTEM)
            .map { homeSystemAt(it) }
            .first { it != target && it != home && worldAt(seed, it) != null }

        // Every hull away, because the note only exists where the pool is empty: with something
        // idle the sheet prices a run instead of explaining why it cannot.
        val one = withSkiffs(1).copy(ships = Ships.NONE, runs = listOf(runReturningIn(2.hours, other)))
        val several = withSkiffs(1).copy(
            ships = Ships.NONE,
            runs = listOf(runReturningIn(2.hours, other), runReturningIn(5.hours, other)),
        )

        // A refusal rather than an offer, and the refusal is the subject: with nothing idle the
        // sheet explains where the fleet is instead of pricing a run it cannot send.
        val withOne = assertIs<DispatchUiState.Refuse>(dispatchAt(target, state = one))
        val withSeveral = assertIs<DispatchUiState.Refuse>(dispatchAt(target, state = several))

        // One run is a sentence about a run; two is a sentence about a queue, and the second one
        // has to say how much is behind the first or the count reads as the whole fleet.
        assertTrue("run is" in withOne.note, withOne.note)
        assertTrue("runs are" in withSeveral.note, withSeveral.note)
        assertTrue("more behind it" in withSeveral.note, withSeveral.note)
    }

    @Test
    fun `a world richer in crystal opens on crystal and says so on the chip rather than the head`() {
        // Searched rather than written down, for the reason every other figure here is read off the
        // generator: which slot happens to be crystal-heavy is the seed's business, and a hardcoded
        // one would be this test asserting the map.
        val slot = (1..GalaxyBalance.SLOTS_PER_SYSTEM).first { candidate ->
            val world = worldAt(seed, homeSystemAt(candidate))
            candidate != home.slot && world != null && world.crystalPerMillion > world.metalPerMillion
        }

        val offer = assertIs<DispatchUiState.Offer>(dispatchAt(homeSystemAt(slot)))

        assertEquals(ResourceKind.CRYSTAL, offer.gathering)
        // The chip is where both readings live, and it is the one place they sit together — which is
        // what makes the currency choice a comparison rather than a memory test.
        assertTrue(offer.crystalRichness.first().isDigit(), offer.crystalRichness)
        // **Neither richness is in the head at either width.** The head is the address and the
        // hazards, so there is nothing left for 320dp to drop.
        assertTrue("metal" !in offer.head, offer.head)
        assertEquals(offer.head, offer.compactHead)
    }

    @Test
    fun `a clean world says so rather than going quiet about its hazards`() {
        val clean = (1..GalaxyBalance.SLOTS_PER_SYSTEM).firstOrNull { candidate ->
            val world = worldAt(seed, homeSystemAt(candidate))
            candidate != home.slot && world != null && world.traits.hazards.isEmpty()
        }
        val hazardous = (1..GalaxyBalance.SLOTS_PER_SYSTEM).first { candidate ->
            val world = worldAt(seed, homeSystemAt(candidate))
            candidate != home.slot && world != null && world.traits.hazards.isNotEmpty()
        }

        // A hazard is named in words, because it is memorable that way and a count is not.
        val risky = assertIs<DispatchUiState.Offer>(dispatchAt(homeSystemAt(hazardous)))
        assertTrue(risky.danger.isNotEmpty())
        // "no hazards" is a fact worth printing rather than an absence worth hiding: a clean world
        // is the one you want to find, and silence would read as missing information.
        clean?.let {
            val safe = assertIs<DispatchUiState.Offer>(dispatchAt(homeSystemAt(it)))
            assertTrue("no hazards" in safe.danger, safe.danger)
        }
    }

    private fun withSkiffs(count: Int): GameState = state.copy(ships = Ships.of(ShipType.SKIFF, count))

    private fun surveying(target: GalaxyCoordinate): GameState =
        withSkiffs(1).let { it.copy(galaxy = it.galaxy.copy(surveyed = it.galaxy.surveyed + target)) }

    private fun homeSystemAt(slot: Int): GalaxyCoordinate =
        GalaxyCoordinate(galaxy = home.galaxy, system = home.system, slot = slot)

    private fun selection(at: GalaxyCoordinate): DispatchSelection =
        DispatchSelection(at = at, gathering = null, ships = null, window = null)

    // The home system's first world the colony is not standing on. Read off the seed rather than
    // written down: the generator owns which slots hold something.
    private fun runnable(): GalaxyCoordinate = homeSystemAt(
        (1..GalaxyBalance.SLOTS_PER_SYSTEM).first { it != home.slot && worldAt(seed, homeSystemAt(it)) != null },
    )

    private fun firstWorld(at: SystemSelection): GalaxyCoordinate = (1..GalaxyBalance.SLOTS_PER_SYSTEM)
        .map { GalaxyCoordinate(galaxy = at.galaxy, system = at.system, slot = it) }
        .first { worldAt(seed, it) != null }

    private fun runReturningIn(duration: kotlin.time.Duration, at: GalaxyCoordinate): FleetRun = FleetRun(
        target = at,
        ships = Ships.of(ShipType.SKIFF, 1),
        gathering = ResourceKind.METAL,
        cargo = Resources.of(),
        dispatchedAt = EPOCH,
        returnsAt = EPOCH + duration,
    )

    private fun worldsIn(at: SystemSelection): List<World> = (1..GalaxyBalance.SLOTS_PER_SYSTEM)
        .mapNotNull { worldAt(seed, GalaxyCoordinate(galaxy = at.galaxy, system = at.system, slot = it)) }

    private fun dispatchAt(
        target: GalaxyCoordinate,
        state: GameState = withSkiffs(1),
        selection: DispatchSelection = selection(target),
    ): DispatchUiState? {
        // The target's own system, which is the only one the sheet knows about: a ledger row belongs
        // to wherever it came from, and nothing here may stand in for the page's.
        val its = SystemSelection(galaxy = target.galaxy, system = target.system)
        return state.toDispatchUiState(
            selection = selection,
            // The real one, never a stand-in: the refusal on an unsurveyed world offers a probe only
            // when the card above it would honour one, and a hand-made state here would be a second
            // copy of exactly the decision that pairing exists to keep single. **That pairing is why
            // this test stayed in this module when the mapper left it** — `:client:dispatch` cannot
            // see `toProbeActionUiState` and should not, because a sheet raised from a landing has no
            // probe footer above it at all.
            probe = state.toProbeActionUiState(at = its, worlds = worldsIn(its), now = EPOCH, timeZone = TimeZone.UTC)
                .asDispatchProbeOffer(),
            now = EPOCH,
        )
    }

    private val World.metalPerMillion: Int get() = traits.metalRichness.perMillion

    private val World.crystalPerMillion: Int get() = traits.crystalRichness.perMillion

    private companion object {
        const val SEED = 20_260_807L

        val EPOCH: Instant = Instant.fromEpochMilliseconds(0)
    }
}
