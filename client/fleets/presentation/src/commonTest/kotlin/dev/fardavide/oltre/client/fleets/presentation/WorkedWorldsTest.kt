package dev.fardavide.oltre.client.fleets.presentation

import dev.fardavide.oltre.client.fleets.ui.WorkedListUiState
import dev.fardavide.oltre.core.Event
import dev.fardavide.oltre.core.GalaxyCoordinate
import dev.fardavide.oltre.core.GalaxySeed
import dev.fardavide.oltre.core.GameState
import dev.fardavide.oltre.core.ResourceKind
import dev.fardavide.oltre.core.Resources
import dev.fardavide.oltre.core.ShipType
import dev.fardavide.oltre.core.Ships
import dev.fardavide.oltre.core.worldNameAt
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.hours
import kotlin.time.Instant
import kotlinx.datetime.TimeZone

// **The fold Claude Design's one move asks for**: the list stops being made of runs and starts being
// made of worlds. Everything here is a claim about that fold — which runs collapse into which row,
// what a row totals, and what happens to a landing that has no world to belong to.
class WorkedWorldsTest {

    @Test
    fun `runs to one world are one row that counts them`() {
        // The defect the fold exists to remove: the drawn ledger showed `[3:165:8]` twice across two
        // days and asked the player to add the two amounts up.
        val state = colony(
            landing(from = near, cargo = Resources.of(metal = 132), at = EPOCH + 1.hours),
            landing(from = near, cargo = Resources.of(metal = 149), at = EPOCH + 2.hours),
        )

        val row = state.worked().rows.single()

        assertEquals(near, row.at)
        assertEquals("2 runs", row.prefix.substringAfterLast(" · "))
        assertEquals("281 metal", row.total)
    }

    @Test
    fun `the total reads the whole log rather than a window of it`() {
        // **The cap retired with the fold**, and it had to: a roll-up is its own cap, so the five-event
        // limit had nothing left to do — and a total taken over the last five landings would be wrong
        // rather than merely short.
        val state = colony(*(1..9).map { landing(from = near, cargo = Resources.of(metal = 100), at = EPOCH + it.hours) }
            .toTypedArray())

        val row = state.worked().rows.single()

        assertEquals("9 runs", row.prefix.substringAfterLast(" · "))
        assertEquals("900 metal", row.total)
    }

    @Test
    fun `the newest landing is at the top`() {
        val state = colony(
            landing(from = near, cargo = Resources.of(metal = 132), at = EPOCH + 1.hours),
            landing(from = far, cargo = Resources.of(crystal = 588), at = EPOCH + 5.hours),
        )

        assertEquals(listOf(far, near), state.worked().rows.map { it.at })
    }

    @Test
    fun `a world is totalled in the resource it has actually paid the most of`() {
        // One resource per world, because that is the one you went back for. Read off what landed
        // rather than off the world's richness: a player may have worked the lesser vein on purpose.
        val state = colony(
            landing(from = near, cargo = Resources.of(metal = 40), at = EPOCH + 1.hours),
            landing(from = near, cargo = Resources.of(crystal = 300), at = EPOCH + 2.hours),
        )

        val row = state.worked().rows.single()

        assertEquals(ResourceKind.CRYSTAL, row.kind)
        assertEquals("300 crystal", row.total)
    }

    @Test
    fun `the row names the world and carries its face`() {
        val state = colony(landing(from = near, cargo = Resources.of(metal = 132), at = EPOCH))

        val row = state.worked().rows.single()

        assertEquals(worldNameAt(state.galaxy.seed, near), row.name)
        // The disc is the affordance — *"a face makes a row an object, and objects open"* — so a row
        // without one would be a door with nothing to say it is one.
        assertEquals(traitsOf(state, near), row.portrait.hazards)
    }

    @Test
    fun `a deposit nobody has touched reads full and one that is finished reads empty`() {
        val untouched = colony(landing(from = near, cargo = Resources.of(metal = 132), at = EPOCH))
        assertEquals("full", untouched.worked().rows.single().deposit)
        assertTrue(!untouched.worked().rows.single().depositIsEmpty)

        val cap = untouched.galaxy.depositCap(near, ResourceKind.METAL)!!
        val stripped = untouched.copy(
            galaxy = untouched.galaxy.withTaken(near, ResourceKind.METAL, cap, at = EPOCH),
        )

        // The one reading on the row that can say a door leads nowhere. **Read at the instant it was
        // emptied**, because a vein regenerates: twelve hours later this world holds 166 again, which
        // is the mechanic working rather than the reading being wrong.
        assertEquals("empty", stripped.worked(now = EPOCH).rows.single().deposit)
        assertTrue(stripped.worked(now = EPOCH).rows.single().depositIsEmpty)
    }

    @Test
    fun `a part worked deposit states the figure rather than a word`() {
        val state = colony(landing(from = near, cargo = Resources.of(metal = 132), at = EPOCH))
        val cap = state.galaxy.depositCap(near, ResourceKind.METAL)!!
        val worked = state.copy(galaxy = state.galaxy.withTaken(near, ResourceKind.METAL, cap / 4, at = EPOCH))

        val deposit = worked.worked().rows.single().deposit

        assertTrue(deposit.first().isDigit(), deposit)
        assertTrue(!worked.worked().rows.single().depositIsEmpty)
    }

    @Test
    fun `the landing clock appears only for a world that landed while you were away`() {
        val state = colony(
            landing(from = near, cargo = Resources.of(metal = 132), at = EPOCH + 1.hours),
            landing(from = far, cargo = Resources.of(crystal = 588), at = EPOCH + 5.hours),
        )

        // The span this launch advanced: the far world landed inside it, the near one before it.
        val rows = state.worked(since = EPOCH + 4.hours, now = EPOCH + 6.hours).rows.associateBy { it.at }

        assertEquals("landed 05:00", rows.getValue(far).landed)
        // A column that is sometimes empty is a column you stop reading — but this is the one
        // conditional element Design allows, and it carries the verb because a bare clock in Oltre
        // is a countdown.
        assertNull(rows.getValue(near).landed)
    }

    @Test
    fun `a landing with no world is a line at the foot rather than a row`() {
        // `Event.FleetReturned.from` is nullable because it is a real value the domain lacks: a
        // schema-8 fold came from a coordinate no old event ever recorded. **In a list of worlds it
        // cannot be a row**, and the missing disc is what says the rest.
        val state = colony(
            landing(from = near, cargo = Resources.of(metal = 132), at = EPOCH + 1.hours),
            Event.FleetReturned(from = null, ships = one, cargo = Resources.of(metal = 268), at = EPOCH),
            Event.FleetReturned(from = null, ships = one, cargo = Resources.of(metal = 134), at = EPOCH),
        )

        val worked = state.worked()

        assertEquals(listOf(near), worked.rows.map { it.at })
        assertEquals("2 earlier runs · 402 metal · no target recorded", worked.unrecorded)
    }

    @Test
    fun `nothing is said about runs that were all recorded`() {
        val state = colony(landing(from = near, cargo = Resources.of(metal = 132), at = EPOCH))

        assertNull(state.worked().unrecorded, "a note about an absence that is not there is furniture")
    }

    @Test
    fun `the trailing count is the runs the rows are made of`() {
        val state = colony(
            landing(from = near, cargo = Resources.of(metal = 132), at = EPOCH + 1.hours),
            landing(from = near, cargo = Resources.of(metal = 149), at = EPOCH + 2.hours),
            landing(from = far, cargo = Resources.of(crystal = 588), at = EPOCH + 3.hours),
        )

        val worked = state.worked()

        assertEquals("3 runs · newest first", worked.trailing)
        // 320dp keeps the count and drops the ordering: the rows are in an order the eye can see.
        assertEquals("3 runs", worked.compactTrailing)
    }

    @Test
    fun `the section is absent on a colony nothing has ever come back to`() {
        // Absent rather than empty, exactly as In flight is absent with no runs: a heading over
        // nothing is a section claiming there is a history when there is not.
        assertNull(colony().toFleetsUiState(now = EPOCH, since = EPOCH, timeZone = TimeZone.UTC).worked)
    }

    @Test
    fun `320dp drops the address and keeps the deposit`() {
        val state = colony(landing(from = near, cargo = Resources.of(metal = 132), at = EPOCH))

        val row = state.worked().rows.single()

        assertTrue(row.prefix.startsWith("[3:"), row.prefix)
        // The name is the identity now, and the address is in the sheet's own head one tap later.
        assertEquals("1 run", row.compactPrefix)
    }

    // ── The fixture ─────────────────────────────────────────────────────────────────────────

    private fun colony(vararg events: Event): GameState =
        GameState.initial(SEED).copy(ships = Ships.of(ShipType.SKIFF, 1), eventLog = events.toList())

    private fun GameState.worked(since: Instant = EPOCH, now: Instant = EPOCH + 12.hours): WorkedListUiState =
        assertNotNull(toFleetsUiState(now = now, since = since, timeZone = TimeZone.UTC).worked)

    private fun landing(from: GalaxyCoordinate, cargo: Resources, at: Instant): Event.FleetReturned =
        Event.FleetReturned(from = from, ships = one, cargo = cargo, at = at)

    private fun traitsOf(state: GameState, at: GalaxyCoordinate) =
        dev.fardavide.oltre.core.worldAt(state.galaxy.seed, at)!!.traits.hazards

    private companion object {
        val SEED = GalaxySeed(20_260_807L)
        val EPOCH: Instant = Instant.fromEpochMilliseconds(0)
        val one: Ships = Ships.of(ShipType.SKIFF, 1)

        // Two worlds of the seed's own home system, which genesis surveys — so both are legal
        // targets on turn one and both have a real name, a real face and a real deposit.
        val near: GalaxyCoordinate = GameState.initial(SEED).galaxy.let { galaxy ->
            galaxy.surveyed.filter { it != galaxy.home }.minBy { it.slot }
        }
        val far: GalaxyCoordinate = GameState.initial(SEED).galaxy.let { galaxy ->
            galaxy.surveyed.filter { it != galaxy.home }.maxBy { it.slot }
        }
    }
}
