package dev.fardavide.oltre.client.notifications.data

import dev.fardavide.oltre.client.design.text.English
import dev.fardavide.oltre.core.BuildJob
import dev.fardavide.oltre.core.BuildingLevel
import dev.fardavide.oltre.core.BuildingType
import dev.fardavide.oltre.core.FleetRun
import dev.fardavide.oltre.core.GalaxyCoordinate
import dev.fardavide.oltre.core.GalaxySeed
import dev.fardavide.oltre.core.GameState
import dev.fardavide.oltre.core.HullAlert
import dev.fardavide.oltre.core.NotificationCategory
import dev.fardavide.oltre.core.NotificationGrouping
import dev.fardavide.oltre.core.NotificationScope
import dev.fardavide.oltre.core.NotificationSettings
import dev.fardavide.oltre.core.ResourceKind
import dev.fardavide.oltre.core.Resources
import dev.fardavide.oltre.core.ShipType
import dev.fardavide.oltre.core.Ships
import dev.fardavide.oltre.core.WatchTarget
import dev.fardavide.oltre.core.YardJob
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.hours
import kotlin.time.Instant

// The second way of answering "what am I told about": say it once per category rather than once per
// job. Everything here is about the **gate** — what reaches the platform at all — and nothing about
// how it is packaged, which is `GroupedAlertsTest`'s subject.
class ByCategoryAlertsTest {

    // The whole of what the mode buys. In ad-hoc this state is silent — no square was tapped — and
    // that is the assertion the sibling test in `GameNotificationsTest` already makes.
    @Test
    fun `a facility nobody tapped a square for is announced when its category is on`() = runTest {
        // given a colony building with no subscription at all
        val scheduler = FakeNotificationScheduler()
        val state = freshState().copy(builds = builds(BuildingType.METAL_MINE to EPOCH + 1.hours))

        // when
        GameNotifications(scheduler, English).sync(state, now = EPOCH, settings = byCategory())

        // then
        val notification = scheduler.scheduled.single()
        assertEquals(EPOCH + 1.hours, notification.at)
        assertTrue("Metal Mine" in notification.title, "title was '${notification.title}'")
    }

    @Test
    fun `a facility whose category is off is not announced at all`() = runTest {
        // given the same colony with the facilities switch off
        val scheduler = FakeNotificationScheduler()
        val state = freshState().copy(builds = builds(BuildingType.METAL_MINE to EPOCH + 1.hours))

        // when
        GameNotifications(scheduler, English)
            .sync(state, now = EPOCH, settings = byCategory(off = NotificationCategory.FACILITIES))

        // then — absent rather than trimmed, exactly as an unasked build is in ad-hoc
        assertEquals(emptyList(), scheduler.scheduled)
    }

    // The per-job asks are ignored rather than obeyed while the categories are in charge, which is
    // what makes the two scopes two answers to one question instead of two filters in series.
    @Test
    fun `a subscription counts for nothing while the categories are in charge`() = runTest {
        // given a player who tapped the square and then switched the category off
        val scheduler = FakeNotificationScheduler()
        val state = freshState().copy(
            builds = builds(BuildingType.METAL_MINE to EPOCH + 1.hours),
            subscribed = setOf(WatchTarget.Facility(BuildingType.METAL_MINE)),
        )

        // when
        GameNotifications(scheduler, English)
            .sync(state, now = EPOCH, settings = byCategory(off = NotificationCategory.FACILITIES))

        // then
        assertEquals(emptyList(), scheduler.scheduled)
    }

    // The other half of the same rule, and the one that says the state is kept rather than emptied:
    // switching back to ad-hoc finds the subscription exactly where it was left.
    @Test
    fun `what the player asked for per job survives a trip through the categories`() = runTest {
        // given a colony with one subscribed build
        val scheduler = FakeNotificationScheduler()
        val state = freshState().copy(
            builds = builds(BuildingType.METAL_MINE to EPOCH + 1.hours),
            subscribed = setOf(WatchTarget.Facility(BuildingType.METAL_MINE)),
        )
        val notifications = GameNotifications(scheduler, English)

        // when the player visits by-category with facilities off and comes back
        notifications.sync(state, now = EPOCH, settings = byCategory(off = NotificationCategory.FACILITIES))
        notifications.sync(state, now = EPOCH, settings = NotificationSettings.DEFAULT)

        // then the square still speaks
        assertEquals(1, scheduler.scheduled.size)
    }

    // A hull card's control has three states; a category switch has two. The card's answer is not
    // consulted at all here — which is why the whole order arrives as three alerts rather than the
    // one "when all done" asked for.
    @Test
    fun `a hull card asking to hear only when the order is done is ignored by the categories`() = runTest {
        // given three skiffs on the slipway and a card set to one alert for the lot
        val scheduler = FakeNotificationScheduler()
        val state = freshState().copy(
            yard = yard(ShipType.SKIFF, count = 3),
            hullAlerts = mapOf(ShipType.SKIFF to HullAlert.WHEN_ALL_DONE),
        )

        // when
        GameNotifications(scheduler, English).sync(state, now = EPOCH, settings = byCategory())

        // then one per hull, because packaging is the grouping setting's job now and it says single
        assertEquals(3, scheduler.scheduled.size)
    }

    // The bell beside Dispatch is the ask a run carries with it. Under the categories a run that
    // was sent with the bell off is announced anyway — the switch is the answer for every flight,
    // including the ones already out.
    @Test
    fun `a flight sent with the bell off is announced once its category is on`() = runTest {
        // given a run nobody asked about
        val scheduler = FakeNotificationScheduler()
        val state = freshState().copy(runs = listOf(runLandingAt(EPOCH + 3.hours, announced = false)))

        // when
        GameNotifications(scheduler, English).sync(state, now = EPOCH, settings = byCategory())

        // then
        assertEquals(1, scheduler.scheduled.size)
    }

    @Test
    fun `a flight whose category is off is silent however it was sent`() = runTest {
        // given a run the player did ask about
        val scheduler = FakeNotificationScheduler()
        val state = freshState().copy(runs = listOf(runLandingAt(EPOCH + 3.hours, announced = true)))

        // when
        GameNotifications(scheduler, English)
            .sync(state, now = EPOCH, settings = byCategory(off = NotificationCategory.FLEET_RETURNS))

        // then
        assertEquals(emptyList(), scheduler.scheduled)
    }

    // Every category on is the state the first switch lands in, and it has to announce everything
    // the colony has in flight — a mode that opened on partial silence would read as broken.
    @Test
    fun `every category on announces every kind of thing in flight`() = runTest {
        // given a colony doing four different things at four instants
        val scheduler = FakeNotificationScheduler()
        val state = busyColony()

        // when
        GameNotifications(scheduler, English).sync(state, now = EPOCH, settings = byCategory())

        // then one alert each, because the grouping setting is still single
        assertEquals(4, scheduler.scheduled.size)
    }

    private companion object {

        val EPOCH: Instant = Instant.fromEpochMilliseconds(0)

        // Every switch on unless a test names one to turn off, which is the default the settings
        // themselves carry and the state the first switch into this mode lands in.
        fun byCategory(off: NotificationCategory? = null): NotificationSettings = NotificationSettings(
            scope = NotificationScope.BY_CATEGORY,
            grouping = NotificationGrouping.SINGLE,
            categories = NotificationCategory.entries.toSet() - setOfNotNull(off),
        )

        fun freshState(): GameState = GameState.initial(GalaxySeed(20_260_823))

        // Written out rather than started through `startUpgrade`, for the reason the fixtures in
        // `GameNotificationsTest` are: what these tests are about is which events reach the
        // platform, and a fixture that inherited its instants from the duration curve would be
        // asserting something different every time the balance moved.
        fun builds(vararg landing: Pair<BuildingType, Instant>): Map<BuildingType, BuildJob> =
            landing.associate { (building, at) ->
                building to BuildJob(
                    building = building,
                    toLevel = BuildingLevel(2),
                    startedAt = EPOCH,
                    completesAt = at,
                )
            }

        // A serial queue, which is the one rule `GameState` checks about a yard: each hull is laid
        // down as the one before it lands.
        fun yard(ship: ShipType, count: Int): List<YardJob> = (1..count).map {
            YardJob(
                ship = ship,
                startedAt = EPOCH + (it - 1).hours,
                completesAt = EPOCH + it.hours,
            )
        }

        fun runLandingAt(instant: Instant, announced: Boolean): FleetRun = FleetRun(
            target = GalaxyCoordinate(galaxy = 2, system = 117, slot = 9),
            ships = Ships.of(ShipType.SKIFF, 2),
            gathering = ResourceKind.METAL,
            cargo = Resources.of(metal = 500),
            dispatchedAt = instant - 1.hours,
            returnsAt = instant,
            announced = announced,
        )

        // Four kinds of news at four instants: a facility, two hulls and a fleet coming home.
        // Deliberately not five — a probe landing needs a real target on a real map, and what these
        // tests are about is the gate rather than the galaxy.
        fun busyColony(): GameState = freshState().copy(
            builds = builds(BuildingType.METAL_MINE to EPOCH + 30.hours),
            yard = yard(ShipType.SKIFF, count = 2),
            runs = listOf(runLandingAt(EPOCH + 3.hours, announced = false)),
        )
    }
}
