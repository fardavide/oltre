package dev.fardavide.oltre.client.notifications.data

import dev.fardavide.oltre.core.BuildingLevel
import dev.fardavide.oltre.core.BuildingType
import dev.fardavide.oltre.core.Coordinates
import dev.fardavide.oltre.core.GalaxySeed
import dev.fardavide.oltre.core.GameState
import dev.fardavide.oltre.core.PlaceholderBalance
import dev.fardavide.oltre.core.ResearchBalance
import dev.fardavide.oltre.core.Resources
import dev.fardavide.oltre.core.ReturningFleet
import dev.fardavide.oltre.core.ShipType
import dev.fardavide.oltre.core.StartResearchResult
import dev.fardavide.oltre.core.StartUpgradeResult
import dev.fardavide.oltre.core.TechLevel
import dev.fardavide.oltre.core.Technology
import dev.fardavide.oltre.core.startResearch
import dev.fardavide.oltre.core.startUpgrade
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.hours
import kotlin.time.Instant

class GameNotificationsTest {

    @Test
    fun `a colony with nothing in flight schedules nothing`() = runTest {
        // given
        val scheduler = FakeNotificationScheduler()

        // when
        GameNotifications(scheduler).sync(freshState(), now = EPOCH)

        // then
        assertEquals(emptyList(), scheduler.scheduled)
    }

    @Test
    fun `a running build is announced at the instant it completes`() = runTest {
        // given
        val scheduler = FakeNotificationScheduler()
        val state = building(BuildingType.METAL_MINE)

        // when
        GameNotifications(scheduler).sync(state, now = EPOCH)

        // then
        val notification = scheduler.scheduled.single()
        assertEquals(state.builds.getValue(BuildingType.METAL_MINE).completesAt, notification.at)
        assertTrue("Metal Mine" in notification.title, "title was '${notification.title}'")
        assertTrue("2" in notification.title, "the level is what the player wants to read")
    }

    @Test
    fun `a returning fleet is announced at the instant it lands`() = runTest {
        // given
        val scheduler = FakeNotificationScheduler()
        val state = freshState().copy(returningFleet = fleetArrivingAt(EPOCH + 3.hours))

        // when
        GameNotifications(scheduler).sync(state, now = EPOCH)

        // then
        val notification = scheduler.scheduled.single()
        assertEquals(EPOCH + 3.hours, notification.at)
        assertTrue("2:117:9" in notification.body, "body was '${notification.body}'")
    }

    @Test
    fun `a running research is announced at the instant it completes`() = runTest {
        // given
        val scheduler = FakeNotificationScheduler()
        val state = researching(Technology.EXTRACTION)

        // when
        GameNotifications(scheduler).sync(state, now = EPOCH)

        // then
        val notification = scheduler.scheduled.single()
        assertEquals(checkNotNull(state.activeResearch).completesAt, notification.at)
        assertTrue("Extraction" in notification.title, "title was '${notification.title}'")
        assertTrue("1" in notification.title, "the level is what the player wants to read")
    }

    @Test
    fun `a colony building and researching at once gets an alert for each`() = runTest {
        // given
        val scheduler = FakeNotificationScheduler()
        val state = researching(Technology.EXTRACTION, on = building(BuildingType.METAL_MINE))

        // when
        GameNotifications(scheduler).sync(state, now = EPOCH)

        // then
        assertEquals(2, scheduler.scheduled.size)
        assertEquals(2, scheduler.scheduled.map { it.id }.toSet().size, "ids must not collide")
        assertEquals(scheduler.scheduled.map { it.at }.sorted(), scheduler.scheduled.map { it.at })
    }

    @Test
    fun `every facility building in parallel gets its own alert`() = runTest {
        // given two facilities building at once
        val scheduler = FakeNotificationScheduler()
        val state = building(BuildingType.METAL_MINE, BuildingType.SOLAR_PLANT)

        // when
        GameNotifications(scheduler).sync(state, now = EPOCH)

        // then
        assertEquals(2, scheduler.scheduled.size)
        assertEquals(2, scheduler.scheduled.map { it.id }.toSet().size, "ids must not collide")
    }

    @Test
    fun `alerts are handed over in the order they will fire`() = runTest {
        // given a fleet landing before either build finishes
        val scheduler = FakeNotificationScheduler()
        val state = building(BuildingType.METAL_MINE, BuildingType.SOLAR_PLANT)
            .copy(returningFleet = fleetArrivingAt(EPOCH + 1.hours))

        // when
        GameNotifications(scheduler).sync(state, now = EPOCH)

        // then
        assertEquals(scheduler.scheduled.map { it.at }.sorted(), scheduler.scheduled.map { it.at })
    }

    @Test
    fun `something already due is not scheduled into the past`() = runTest {
        // given a state carrying a build that finished an hour ago — a stale session resuming
        val scheduler = FakeNotificationScheduler()
        val state = building(BuildingType.METAL_MINE)

        // when the colony is synced long after everything in it was due
        GameNotifications(scheduler).sync(state, now = EPOCH + 5.hours)

        // then
        assertEquals(emptyList(), scheduler.scheduled)
    }

    @Test
    fun `an alert due at this very instant is dropped rather than fired immediately`() = runTest {
        // given
        val scheduler = FakeNotificationScheduler()
        val state = building(BuildingType.METAL_MINE)
        val completesAt = state.builds.getValue(BuildingType.METAL_MINE).completesAt

        // when
        GameNotifications(scheduler).sync(state, now = completesAt)

        // then — the player is looking at the app; the event is about to be applied by advance
        assertEquals(emptyList(), scheduler.scheduled)
    }

    @Test
    fun `syncing an empty colony clears whatever was pending`() = runTest {
        // given a scheduler holding the alerts of a colony that was building
        val scheduler = FakeNotificationScheduler()
        val notifications = GameNotifications(scheduler)
        notifications.sync(building(BuildingType.METAL_MINE), now = EPOCH)
        assertEquals(1, scheduler.scheduled.size)

        // when the build has since completed and nothing is in flight
        notifications.sync(freshState(), now = EPOCH + 1.hours)

        // then the whole set is replaced rather than amended
        assertEquals(emptyList(), scheduler.scheduled)
        assertEquals(2, scheduler.replaceCount)
    }

    @Test
    fun `the same colony always produces the same alert ids`() = runTest {
        // given
        val first = FakeNotificationScheduler()
        val second = FakeNotificationScheduler()
        val state = building(BuildingType.METAL_MINE).copy(returningFleet = fleetArrivingAt(EPOCH + 3.hours))

        // when
        GameNotifications(first).sync(state, now = EPOCH)
        GameNotifications(second).sync(state, now = EPOCH)

        // then
        assertEquals(first.scheduled, second.scheduled)
    }

    private fun building(vararg buildings: BuildingType): GameState {
        val total = buildings.fold(Resources.of()) { stock, building ->
            val cost = PlaceholderBalance.upgradeCost(building, BuildingLevel(2))
            Resources.of(
                metal = stock.metal + cost.metal,
                crystal = stock.crystal + cost.crystal,
                deuterium = stock.deuterium + cost.deuterium,
            )
        }
        return buildings.fold(freshState().copy(resources = total)) { state, building ->
            assertIs<StartUpgradeResult.Started>(startUpgrade(state, building, at = EPOCH)).state
        }
    }

    // The gate and the price, so the test reads as "a colony that is researching" rather than as
    // the four lines it takes to make one.
    private fun researching(
        technology: Technology,
        on: GameState = freshState(),
    ): GameState {
        val toLevel = TechLevel(on.research.levelOf(technology).value + 1)
        val cost = ResearchBalance.researchCost(technology, toLevel)
        val ready = on.copy(
            buildings = on.buildings.withLevel(BuildingType.ROBOTICS_FACTORY, BuildingLevel(1)),
            resources = Resources.of(metal = cost.metal, crystal = cost.crystal, deuterium = cost.deuterium),
        )
        return assertIs<StartResearchResult.Started>(startResearch(ready, technology, at = EPOCH)).state
    }

    // `GameState.initial` takes a galaxy seed rather than defaulting one, so production cannot found
    // every colony in the same galaxy. Alerts do not care which map they are scheduled over.
    private fun freshState(): GameState = GameState.initial(GalaxySeed(20_260_807))

    private fun fleetArrivingAt(instant: Instant): ReturningFleet = ReturningFleet(
        ships = mapOf(ShipType.CARGO to 14),
        cargo = Resources.of(metal = 500),
        origin = Coordinates(galaxy = 2, system = 117, position = 9),
        arrivesAt = instant,
    )

    private companion object {
        val EPOCH = Instant.fromEpochMilliseconds(0)
    }
}
