package dev.fardavide.oltre.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Instant

// What the save carries about worlds the player has worked, and the two rules that keep it bounded:
// an absent entry is a full world, and a full entry is dropped.
class WorldDepositTest {

    private val origin = Instant.fromEpochMilliseconds(1_800_000_000_000)
    private val seed = GalaxySeed(20260813)
    private val galaxy = GalaxyState.initial(seed)
    private val home = galaxy.home

    private fun aNeighbour(): GalaxyCoordinate =
        galaxy.surveyed.first { it != home }

    // ── The record ───────────────────────────────────────────────────────────────────────────

    @Test
    fun `a deposit cannot hold a negative stock`() {
        assertFailsWith<IllegalArgumentException> {
            WorldDeposit(at = home, metalFine = -1, crystalFine = 0, asOf = origin)
        }
        assertFailsWith<IllegalArgumentException> {
            WorldDeposit(at = home, metalFine = 0, crystalFine = -1, asOf = origin)
        }
    }

    @Test
    fun `a world cannot have two deposits`() {
        val at = aNeighbour()
        assertFailsWith<IllegalArgumentException> {
            galaxy.copy(
                deposits = listOf(
                    WorldDeposit(at = at, metalFine = 0, crystalFine = 0, asOf = origin),
                    WorldDeposit(at = at, metalFine = 0, crystalFine = 0, asOf = origin),
                ),
            )
        }
    }

    // ── An absent entry is a full world ──────────────────────────────────────────────────────

    @Test
    fun `a world nobody has worked is full`() {
        val at = aNeighbour()
        val world = worldAt(seed, at)!!
        val danger = FleetBalance.danger(from = home, world = world)

        assertEquals(
            DepositBalance.cap(world, ResourceKind.METAL, danger),
            galaxy.remaining(at, ResourceKind.METAL, origin),
        )
        assertEquals(
            DepositBalance.cap(world, ResourceKind.CRYSTAL, danger),
            galaxy.remaining(at, ResourceKind.CRYSTAL, origin),
        )
    }

    @Test
    fun `a slot with no world in it holds nothing`() {
        val empty = (1..GalaxyBalance.SLOTS_PER_SYSTEM)
            .map { GalaxyCoordinate(galaxy = home.galaxy, system = home.system, slot = it) }
            .first { worldAt(seed, it) == null }

        assertNull(galaxy.depositCap(empty, ResourceKind.METAL))
        assertEquals(0, galaxy.remaining(empty, ResourceKind.METAL, origin))
    }

    // ── Debiting ─────────────────────────────────────────────────────────────────────────────

    @Test
    fun `taking from a world leaves the rest behind`() {
        val at = aNeighbour()
        val full = galaxy.remaining(at, ResourceKind.METAL, origin)

        val after = galaxy.withTaken(at, ResourceKind.METAL, taken = 300, at = origin)

        assertEquals(full - 300, after.remaining(at, ResourceKind.METAL, origin))
        // and the other deposit is untouched — the two drain independently
        assertEquals(
            galaxy.remaining(at, ResourceKind.CRYSTAL, origin),
            after.remaining(at, ResourceKind.CRYSTAL, origin),
        )
    }

    @Test
    fun `a second run in one check-in sees the first one's hole`() {
        val at = aNeighbour()
        val full = galaxy.remaining(at, ResourceKind.METAL, origin)

        val after = galaxy
            .withTaken(at, ResourceKind.METAL, taken = 300, at = origin)
            .withTaken(at, ResourceKind.METAL, taken = 200, at = origin)

        assertEquals(full - 500, after.remaining(at, ResourceKind.METAL, origin))
    }

    @Test
    fun `a world cannot be taken below empty`() {
        val at = aNeighbour()
        val full = galaxy.remaining(at, ResourceKind.METAL, origin)

        assertFailsWith<IllegalArgumentException> {
            galaxy.withTaken(at, ResourceKind.METAL, taken = full + 1, at = origin)
        }
    }

    // ── Refill, and the prune that bounds the save ───────────────────────────────────────────

    @Test
    fun `a worked world comes back at five percent a day`() {
        val at = aNeighbour()
        val full = galaxy.remaining(at, ResourceKind.METAL, origin)
        val stripped = galaxy.withTaken(at, ResourceKind.METAL, taken = full, at = origin)

        assertEquals(0, stripped.remaining(at, ResourceKind.METAL, origin))
        assertEquals(full * 5 / 100, stripped.remaining(at, ResourceKind.METAL, origin + 1.days))
        assertEquals(full, stripped.remaining(at, ResourceKind.METAL, origin + 20.days))
    }

    @Test
    fun `a world that is full again is dropped from the save`() {
        val at = aNeighbour()
        val full = galaxy.remaining(at, ResourceKind.METAL, origin)
        val stripped = galaxy.withTaken(at, ResourceKind.METAL, taken = full, at = origin)

        assertEquals(1, stripped.deposits.size)
        assertEquals(1, stripped.prunedFull(origin + 19.days).deposits.size)
        assertTrue(stripped.prunedFull(origin + 20.days).deposits.isEmpty())
    }

    @Test
    fun `pruning is monotone so it can be done at the end of any span`() {
        // Why the prune may live in `advance` at all: a world that is full at t1 is full at t2, so
        // pruning twice and pruning once land on the same state — which is the composability property
        // the whole simulation rests on.
        val at = aNeighbour()
        val full = galaxy.remaining(at, ResourceKind.METAL, origin)
        val stripped = galaxy.withTaken(at, ResourceKind.METAL, taken = full, at = origin)

        val once = stripped.prunedFull(origin + 25.days)
        val twice = stripped.prunedFull(origin + 12.days).prunedFull(origin + 25.days)

        assertEquals(once, twice)
    }

    @Test
    fun `a partly drained world is still carried`() {
        val at = aNeighbour()
        val stripped = galaxy.withTaken(at, ResourceKind.METAL, taken = 100, at = origin)

        assertEquals(1, stripped.prunedFull(origin + 1.hours).deposits.size)
    }
}
