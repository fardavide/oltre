package dev.fardavide.oltre.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.time.Duration.Companion.days
import kotlin.time.Instant

class WatchTest {

    @Test
    fun `a fresh colony watches nothing`() {
        assertNull(GameState.initial().watching)
    }

    @Test
    fun `watching a row points the one slot at it`() {
        // given
        val state = GameState.initial()

        // when
        val watched = toggleWatch(state, WatchTarget.Facility(BuildingType.METAL_MINE))

        // then
        assertEquals(WatchTarget.Facility(BuildingType.METAL_MINE), watched.watching)
    }

    @Test
    fun `watching another row moves the watch rather than adding one`() {
        // given
        val watched = toggleWatch(GameState.initial(), WatchTarget.Facility(BuildingType.METAL_MINE))

        // when — the other branch of the same slot, on a screen the player is not looking at
        val moved = toggleWatch(watched, WatchTarget.Project(Technology.EXTRACTION))

        // then
        assertEquals(WatchTarget.Project(Technology.EXTRACTION), moved.watching)
    }

    @Test
    fun `watching the watched row takes the watch back`() {
        // given
        val target = WatchTarget.Ladder(AdaptationTechnology.THERMAL)
        val watched = toggleWatch(GameState.initial(), target)

        // when
        val cleared = toggleWatch(watched, target)

        // then
        assertNull(cleared.watching)
    }

    @Test
    fun `the watched purchase is the row's next level and its price`() {
        // given
        val state = toggleWatch(GameState.initial(), WatchTarget.Facility(BuildingType.METAL_MINE))

        // then — level 2 because the colony opens on level 1
        assertEquals(
            WatchedPurchase.Facility(
                building = BuildingType.METAL_MINE,
                toLevel = BuildingLevel(2),
                cost = PlaceholderBalance.upgradeCost(BuildingType.METAL_MINE, BuildingLevel(2)),
            ),
            state.watchedPurchase(),
        )
    }

    @Test
    fun `futureEvents projects the instant the watched row becomes affordable`() {
        // given a colony with nothing in the stores and a mine it cannot pay for
        val state = broke().let { toggleWatch(it, WatchTarget.Facility(BuildingType.METAL_MINE)) }
        val purchase = checkNotNull(state.watchedPurchase())

        // when
        val upcoming = futureEvents(state, now = EPOCH)

        // then
        assertEquals(
            listOf(
                FutureEvent.AffordableAt(
                    purchase = purchase,
                    at = EPOCH + timeUntilAffordable(
                        state.resources,
                        purchase.cost,
                        state.buildings,
                        state.research,
                    ),
                ),
            ),
            upcoming,
        )
    }

    @Test
    fun `a watched row the colony can already pay for has no instant to book`() {
        // given — the opening stocks cover the first mine level outright
        val state = toggleWatch(GameState.initial(), WatchTarget.Facility(BuildingType.METAL_MINE))

        // then
        assertEquals(emptyList(), futureEvents(state, now = EPOCH))
    }

    @Test
    fun `a row whose binding resource has no income has no instant to book`() {
        // given a colony with no synthesizer at all, watching the one facility priced in deuterium
        val state = broke()
            .copy(buildings = Buildings.initial().withLevel(BuildingType.DEUTERIUM_SYNTHESIZER, BuildingLevel(0)))
            .let { toggleWatch(it, WatchTarget.Facility(BuildingType.ROBOTICS_FACTORY)) }

        // then
        assertEquals(emptyList(), futureEvents(state, now = EPOCH))
    }

    @Test
    fun `the projection moves later when the stocks are spent on something else`() {
        // given the most expensive row in the game, so a part-filled store is still a long way short
        val watched = toggleWatch(broke(), WatchTarget.Facility(BuildingType.NANITE_FACTORY))

        // when
        val booked = affordableAt(watched.copy(resources = Resources.of(metal = 1_000, crystal = 1_000, deuterium = 1_000)))
        val spent = affordableAt(watched.copy(resources = Resources.of()))

        // then
        assertEquals(true, spent > booked, "spending the stores moves the instant later")
    }

    @Test
    fun `advance clears the watch once the colony can pay for the row`() {
        // given
        val state = toggleWatch(broke(), WatchTarget.Facility(BuildingType.METAL_MINE))

        // when — long enough that the mines have earned the price several times over
        val later = advance(state, from = EPOCH, to = EPOCH + 30.days)

        // then
        assertNull(later.watching)
    }

    @Test
    fun `advance keeps a watch the colony still cannot pay for`() {
        // given
        val state = toggleWatch(broke(), WatchTarget.Facility(BuildingType.NANITE_FACTORY))

        // when
        val later = advance(state, from = EPOCH, to = EPOCH)

        // then
        assertEquals(WatchTarget.Facility(BuildingType.NANITE_FACTORY), later.watching)
    }

    @Test
    fun `a watch survives a round trip`() {
        // given
        val state = toggleWatch(GameState.initial(), WatchTarget.Project(Technology.ENRICHMENT))
        val snapshot = GameSnapshot(lastUpdatedAt = EPOCH, state = state)

        // when
        val decoded = GameSave.decode(GameSave.encode(snapshot))

        // then
        assertEquals(
            WatchTarget.Project(Technology.ENRICHMENT),
            (decoded as DecodeResult.Success).snapshot.state.watching,
        )
    }

    private fun broke(): GameState = GameState.initial().copy(resources = Resources.of())

    private fun affordableAt(state: GameState): Instant =
        futureEvents(state, now = EPOCH).filterIsInstance<FutureEvent.AffordableAt>().single().at

    private companion object {
        val EPOCH: Instant = Instant.fromEpochMilliseconds(0)
    }
}
