package dev.fardavide.oltre.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.seconds
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
        val watched = toggleAlert(state, WatchTarget.Facility(BuildingType.METAL_MINE))

        // then
        assertEquals(WatchTarget.Facility(BuildingType.METAL_MINE), watched.watching)
    }

    @Test
    fun `watching another row moves the watch rather than adding one`() {
        // given
        val watched = toggleAlert(GameState.initial(), WatchTarget.Facility(BuildingType.METAL_MINE))

        // when — the other branch of the same slot, on a screen the player is not looking at
        val moved = toggleAlert(watched, WatchTarget.Project(Technology.EXTRACTION))

        // then
        assertEquals(WatchTarget.Project(Technology.EXTRACTION), moved.watching)
    }

    @Test
    fun `watching the watched row takes the watch back`() {
        // given
        val target = WatchTarget.Ladder(AdaptationTechnology.THERMAL)
        val watched = toggleAlert(GameState.initial(), target)

        // when
        val cleared = toggleAlert(watched, target)

        // then
        assertNull(cleared.watching)
    }

    @Test
    fun `the watched purchase is the row's next level and its price`() {
        // given
        val state = toggleAlert(GameState.initial(), WatchTarget.Facility(BuildingType.METAL_MINE))

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
        val state = broke().let { toggleAlert(it, WatchTarget.Facility(BuildingType.METAL_MINE)) }
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
        val state = toggleAlert(GameState.initial(), WatchTarget.Facility(BuildingType.METAL_MINE))

        // then
        assertEquals(emptyList(), futureEvents(state, now = EPOCH))
    }

    @Test
    fun `a row whose binding resource has no income has no instant to book`() {
        // given a colony with no synthesizer at all, watching the one facility priced in deuterium
        val state = broke()
            .copy(buildings = Buildings.initial().withLevel(BuildingType.DEUTERIUM_SYNTHESIZER, BuildingLevel(0)))
            .let { toggleAlert(it, WatchTarget.Facility(BuildingType.ROBOTICS_FACTORY)) }

        // then
        assertEquals(emptyList(), futureEvents(state, now = EPOCH))
    }

    @Test
    fun `the projection moves later when the stocks are spent on something else`() {
        // given the most expensive row in the game, so a part-filled store is still a long way short
        val watched = toggleAlert(broke(), WatchTarget.Facility(BuildingType.NANITE_FACTORY))

        // when
        val booked = affordableAt(watched.copy(resources = Resources.of(metal = 1_000, crystal = 1_000, deuterium = 1_000)))
        val spent = affordableAt(watched.copy(resources = Resources.of()))

        // then
        assertEquals(true, spent > booked, "spending the stores moves the instant later")
    }

    @Test
    fun `advance clears the watch once the colony can pay for the row`() {
        // given
        val state = toggleAlert(broke(), WatchTarget.Facility(BuildingType.METAL_MINE))

        // when — long enough that the mines have earned the price several times over
        val later = advance(state, from = EPOCH, to = EPOCH + 30.days)

        // then
        assertNull(later.watching)
    }

    @Test
    fun `advance keeps a watch the colony still cannot pay for`() {
        // given
        val state = toggleAlert(broke(), WatchTarget.Facility(BuildingType.NANITE_FACTORY))

        // when
        val later = advance(state, from = EPOCH, to = EPOCH)

        // then
        assertEquals(WatchTarget.Facility(BuildingType.NANITE_FACTORY), later.watching)
    }

    @Test
    fun `a watch survives a round trip`() {
        // given
        val state = toggleAlert(GameState.initial(), WatchTarget.Project(Technology.ENRICHMENT))
        val snapshot = GameSnapshot(lastUpdatedAt = EPOCH, state = state)

        // when
        val decoded = GameSave.decode(GameSave.encode(snapshot))

        // then
        assertEquals(
            WatchTarget.Project(Technology.ENRICHMENT),
            (decoded as DecodeResult.Success).snapshot.state.watching,
        )
    }

    // ── The other half of the square: a job in flight, asked about its completion ────────────────

    @Test
    fun `the square on a running row asks about the completion rather than the price`() {
        // given a colony building a mine it has already paid for
        val building = building(BuildingType.METAL_MINE)

        // when
        val asked = toggleAlert(building, WatchTarget.Facility(BuildingType.METAL_MINE))

        // then — the subscription, not the watch: the price is paid, so there is no price to wait for
        assertEquals(setOf(WatchTarget.Facility(BuildingType.METAL_MINE)), asked.subscribed)
        assertNull(asked.watching)
    }

    @Test
    fun `a second subscription adds where a second watch would have moved`() {
        // given — the asymmetry the design chose, and the reason for it: a completion is something
        // the player started, and the model caps those at seven
        val two = building(BuildingType.METAL_MINE, BuildingType.SOLAR_PLANT)
            .let { toggleAlert(it, WatchTarget.Facility(BuildingType.METAL_MINE)) }
            .let { toggleAlert(it, WatchTarget.Facility(BuildingType.SOLAR_PLANT)) }

        // then
        assertEquals(
            setOf(WatchTarget.Facility(BuildingType.METAL_MINE), WatchTarget.Facility(BuildingType.SOLAR_PLANT)),
            two.subscribed,
        )
    }

    @Test
    fun `tapping a subscribed row's square takes the subscription back`() {
        // given
        val target = WatchTarget.Facility(BuildingType.METAL_MINE)
        val asked = toggleAlert(building(BuildingType.METAL_MINE), target)

        // when — no separate verb, because the undo is the same tap
        val cleared = toggleAlert(asked, target)

        // then
        assertEquals(emptySet(), cleared.subscribed)
    }

    @Test
    fun `a subscription is spent by the job it was about`() {
        // given — a subscription is about the job the player started, not a standing preference
        // about the row: start the same facility again and the square is unlit, because the second
        // build is a second decision
        val target = WatchTarget.Facility(BuildingType.METAL_MINE)
        val asked = toggleAlert(building(BuildingType.METAL_MINE), target)
        val completesAt = checkNotNull(asked.builds[BuildingType.METAL_MINE]).completesAt

        // when
        val later = advance(asked, from = EPOCH, to = completesAt)

        // then
        assertEquals(BuildingLevel(2), later.buildings.metalMine)
        assertEquals(emptySet(), later.subscribed)
    }

    @Test
    fun `a subscription survives while its job is still in flight`() {
        // given
        val target = WatchTarget.Facility(BuildingType.METAL_MINE)
        val asked = toggleAlert(building(BuildingType.METAL_MINE), target)
        val completesAt = checkNotNull(asked.builds[BuildingType.METAL_MINE]).completesAt

        // when — a moment short of the end
        val later = advance(asked, from = EPOCH, to = completesAt - 1.seconds)

        // then
        assertEquals(setOf(target), later.subscribed)
    }

    // **The invariant the two halves rest on, and nothing in `init` enforces it.** If a row could be
    // both watched and subscribed at once, the square on it would toggle the subscription and the
    // watch would become uncancellable while the section label went on naming it. It is unreachable
    // because starting a job requires covering the very cost the watch is waiting for, and `advance`
    // clears the watch the moment the stores do — so this is the test that says so out loud, rather
    // than a `require` paid for on every construction.
    @Test
    fun `advance never leaves a row both watched and subscribed`() {
        // given a colony watching the mine it is about to be able to afford, and building another
        val watched = toggleAlert(building(BuildingType.SOLAR_PLANT), WatchTarget.Facility(BuildingType.METAL_MINE))
            .let { toggleAlert(it, WatchTarget.Facility(BuildingType.SOLAR_PLANT)) }

        // when — walked forward an hour at a time across both completions
        var state = watched
        var now = EPOCH
        repeat(24) {
            val next = now + 1.hours
            state = advance(state, from = now, to = next)
            now = next
            assertNull(
                state.watching?.takeIf { it in state.subscribed },
                "a row was watched and subscribed at once, $it hours in",
            )
        }
    }

    @Test
    fun `subscriptions survive a round trip`() {
        // given
        val state = toggleAlert(building(BuildingType.METAL_MINE), WatchTarget.Facility(BuildingType.METAL_MINE))
        val snapshot = GameSnapshot(lastUpdatedAt = EPOCH, state = state)

        // when
        val decoded = GameSave.decode(GameSave.encode(snapshot))

        // then
        assertEquals(
            setOf(WatchTarget.Facility(BuildingType.METAL_MINE)),
            assertIs<DecodeResult.Success>(decoded).snapshot.state.subscribed,
        )
    }

    private fun broke(): GameState = GameState.initial().copy(resources = Resources.of())

    // A colony with the named facilities in flight, funded for exactly those and nothing more.
    private fun building(vararg buildings: BuildingType): GameState =
        buildings.fold(GameState.initial().fundedFor(*buildings)) { state, building ->
            state.started(building, at = EPOCH)
        }

    private fun affordableAt(state: GameState): Instant =
        futureEvents(state, now = EPOCH).filterIsInstance<FutureEvent.AffordableAt>().single().at

    private companion object {
        val EPOCH: Instant = Instant.fromEpochMilliseconds(0)
    }
}
