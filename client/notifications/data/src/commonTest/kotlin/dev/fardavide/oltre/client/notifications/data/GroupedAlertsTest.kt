package dev.fardavide.oltre.client.notifications.data

import dev.fardavide.oltre.client.design.text.English
import dev.fardavide.oltre.core.AdaptationJob
import dev.fardavide.oltre.core.AdaptationTechnology
import dev.fardavide.oltre.core.BuildJob
import dev.fardavide.oltre.core.BuildingLevel
import dev.fardavide.oltre.core.BuildingType
import dev.fardavide.oltre.core.FleetRun
import dev.fardavide.oltre.core.GalaxyCoordinate
import dev.fardavide.oltre.core.GalaxySeed
import dev.fardavide.oltre.core.GameState
import dev.fardavide.oltre.core.NotificationCategory
import dev.fardavide.oltre.core.NotificationGrouping
import dev.fardavide.oltre.core.NotificationScope
import dev.fardavide.oltre.core.NotificationSettings
import dev.fardavide.oltre.core.ResearchJob
import dev.fardavide.oltre.core.ResourceKind
import dev.fardavide.oltre.core.Resources
import dev.fardavide.oltre.core.ShipType
import dev.fardavide.oltre.core.Ships
import dev.fardavide.oltre.core.TechLevel
import dev.fardavide.oltre.core.Technology
import dev.fardavide.oltre.core.YardJob
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

// How what you are told is packaged, which is the setting that applies in both scopes. Everything
// here runs by category with every switch on, so that what is under test is the packaging and not
// the gate — `ByCategoryAlertsTest` owns the gate.
class GroupedAlertsTest {

    // **No window, and that is Davide's call of 2026-08-23 against the recommendation.** Three
    // builds landing an hour, six hours and a day apart are one alert, fired when the last of them
    // lands — the first two are not announced early, they are not announced at all.
    @Test
    fun `grouped collapses a category into one alert at the instant the last of them lands`() = runTest {
        // given three facilities landing nowhere near each other
        val scheduler = FakeNotificationScheduler()
        val state = freshState().copy(
            builds = builds(
                BuildingType.METAL_MINE to EPOCH + 1.hours,
                BuildingType.CRYSTAL_MINE to EPOCH + 6.hours,
                BuildingType.SOLAR_PLANT to EPOCH + 24.hours,
            ),
        )

        // when
        GameNotifications(scheduler, English).sync(state, now = EPOCH, settings = byCategory(NotificationGrouping.GROUPED))

        // then
        val notification = scheduler.scheduled.single()
        assertEquals(EPOCH + 24.hours, notification.at)
        assertEquals("3 facilities are done", notification.title)
    }

    @Test
    fun `grouped keeps two categories apart`() = runTest {
        // given two facilities and two hulls
        val scheduler = FakeNotificationScheduler()
        val state = freshState().copy(
            builds = builds(
                BuildingType.METAL_MINE to EPOCH + 1.hours,
                BuildingType.CRYSTAL_MINE to EPOCH + 6.hours,
            ),
            yard = yard(ShipType.SKIFF, count = 2),
        )

        // when
        GameNotifications(scheduler, English).sync(state, now = EPOCH, settings = byCategory(NotificationGrouping.GROUPED))

        // then
        assertEquals(
            setOf("2 facilities are done", "2 hulls have left the yard"),
            scheduler.scheduled.map { it.title }.toSet(),
        )
    }

    // The rule this file already keeps twice over: a count is only worth saying when there is more
    // than one thing to count.
    @Test
    fun `a category with one thing pending says the thing rather than the count`() = runTest {
        // given one facility and nothing else
        val scheduler = FakeNotificationScheduler()
        val state = freshState().copy(builds = builds(BuildingType.METAL_MINE to EPOCH + 1.hours))

        // when
        GameNotifications(scheduler, English).sync(state, now = EPOCH, settings = byCategory(NotificationGrouping.GROUPED))

        // then
        assertEquals("Metal Mine reached level 2", scheduler.scheduled.single().title)
    }

    // Every alert this game books has an id derived from its subject, which is what makes replacing
    // the whole set idempotent. A category is fixed vocabulary, so this is the most stable id in the
    // file — stronger than the five-minute group's, which has to fall back on its instant.
    @Test
    fun `the id of a grouped alert is its category and it does not move`() = runTest {
        // given a colony whose queue grows between two syncs
        val first = FakeNotificationScheduler()
        val second = FakeNotificationScheduler()
        val state = freshState().copy(builds = builds(BuildingType.METAL_MINE to EPOCH + 1.hours))
        val grown = state.copy(
            builds = builds(
                BuildingType.METAL_MINE to EPOCH + 1.hours,
                BuildingType.CRYSTAL_MINE to EPOCH + 2.hours,
                BuildingType.SOLAR_PLANT to EPOCH + 3.hours,
            ),
        )

        // when
        GameNotifications(first, English).sync(grown, now = EPOCH, settings = byCategory(NotificationGrouping.GROUPED))
        GameNotifications(second, English).sync(grown, now = EPOCH, settings = byCategory(NotificationGrouping.GROUPED))

        // then the same colony books the same id twice, and it names the category
        assertEquals("category-FACILITIES", first.scheduled.single().id)
        assertEquals(first.scheduled.map { it.id }, second.scheduled.map { it.id })
    }

    // The subjects, so an alert saying "3" also says which three.
    @Test
    fun `a grouped alert names what it is about`() = runTest {
        // given three facilities
        val scheduler = FakeNotificationScheduler()
        val state = freshState().copy(
            builds = builds(
                BuildingType.METAL_MINE to EPOCH + 1.hours,
                BuildingType.CRYSTAL_MINE to EPOCH + 2.hours,
                BuildingType.SOLAR_PLANT to EPOCH + 3.hours,
            ),
        )

        // when
        GameNotifications(scheduler, English).sync(state, now = EPOCH, settings = byCategory(NotificationGrouping.GROUPED))

        // then
        assertEquals("Metal Mine, Crystal Mine and Solar Plant.", scheduler.scheduled.single().body)
    }

    // Compaction in the body as well as in the title — a lock screen has room for three names and
    // a tally, and a queue of twelve hulls would otherwise write a paragraph.
    @Test
    fun `a grouped alert names three subjects and counts the rest`() = runTest {
        // given six hulls on the slipway
        val scheduler = FakeNotificationScheduler()
        val state = freshState().copy(yard = yard(ShipType.SKIFF, count = 6))

        // when
        GameNotifications(scheduler, English).sync(state, now = EPOCH, settings = byCategory(NotificationGrouping.GROUPED))

        // then
        assertEquals("Skiff, Skiff, Skiff and 3 more.", scheduler.scheduled.single().body)
    }

    @Test
    fun `summary collapses everything into one alert at the instant the last thing lands`() = runTest {
        // given a colony doing three different things
        val scheduler = FakeNotificationScheduler()
        val state = busyColony()

        // when
        GameNotifications(scheduler, English).sync(state, now = EPOCH, settings = byCategory(NotificationGrouping.SUMMARY))

        // then
        val notification = scheduler.scheduled.single()
        assertEquals("summary", notification.id)
        assertEquals(EPOCH + 30.hours, notification.at)
    }

    // The first rung of the ladder, in Davide's own words: *"ofc if only Metal Mine is completed,
    // for example, we show 'Metal Mine upgraded to lv x'"*.
    @Test
    fun `summary says the thing itself when only one thing is pending`() = runTest {
        // given one facility and nothing else
        val scheduler = FakeNotificationScheduler()
        val state = freshState().copy(builds = builds(BuildingType.METAL_MINE to EPOCH + 1.hours))

        // when
        GameNotifications(scheduler, English).sync(state, now = EPOCH, settings = byCategory(NotificationGrouping.SUMMARY))

        // then
        assertEquals("Metal Mine reached level 2", scheduler.scheduled.single().title)
    }

    // The second rung: several things, all of one kind, is the category's own sentence — there is
    // nothing for a summary to summarise across.
    @Test
    fun `summary of one category is that category's count`() = runTest {
        // given two facilities and nothing else
        val scheduler = FakeNotificationScheduler()
        val state = freshState().copy(
            builds = builds(
                BuildingType.METAL_MINE to EPOCH + 1.hours,
                BuildingType.CRYSTAL_MINE to EPOCH + 2.hours,
            ),
        )

        // when
        GameNotifications(scheduler, English).sync(state, now = EPOCH, settings = byCategory(NotificationGrouping.SUMMARY))

        // then
        assertEquals("2 facilities are done", scheduler.scheduled.single().title)
    }

    // The third rung, and the shape Davide wrote: *"3 cargos ready, 2 upgrade completed"*.
    @Test
    fun `summary of two categories says a clause for each`() = runTest {
        // given two facilities and two hulls
        val scheduler = FakeNotificationScheduler()
        val state = freshState().copy(
            builds = builds(
                BuildingType.METAL_MINE to EPOCH + 1.hours,
                BuildingType.CRYSTAL_MINE to EPOCH + 2.hours,
            ),
            yard = yard(ShipType.SKIFF, count = 2),
        )

        // when
        GameNotifications(scheduler, English).sync(state, now = EPOCH, settings = byCategory(NotificationGrouping.SUMMARY))

        // then
        assertEquals("2 facilities are done and 2 hulls have left the yard", scheduler.scheduled.single().title)
    }

    // The fourth rung: *"the more info we need to show, the more we compact to fit everything"*. At
    // four kinds the verbs go and the title is a tally, because four clauses with verbs is a
    // paragraph and a lock screen truncates one.
    @Test
    fun `summary of four categories drops the verbs`() = runTest {
        // given a colony doing four different kinds of thing at once
        val scheduler = FakeNotificationScheduler()
        val state = fourKindsOfNews()

        // when
        GameNotifications(scheduler, English).sync(state, now = EPOCH, settings = byCategory(NotificationGrouping.SUMMARY))

        // then
        assertEquals(
            "2 facilities, 1 project, 1 adaptation and 1 fleet",
            scheduler.scheduled.single().title,
        )
    }

    // What the setting is for, stated as the property rather than as a count of alerts: a colony
    // with a fleet's worth of hulls on the slipway interrupts its player exactly once.
    @Test
    fun `summary books one request whatever the colony has in flight`() = runTest {
        // given twelve hulls, three facilities and a fleet out
        val scheduler = FakeNotificationScheduler()
        val state = busyColony().copy(yard = yard(ShipType.SKIFF, count = 12))

        // when
        GameNotifications(scheduler, English).sync(state, now = EPOCH, settings = byCategory(NotificationGrouping.SUMMARY))

        // then
        assertEquals(1, scheduler.scheduled.size)
    }

    // Single is what ships today and this slice may not have moved it. The five-minute chain is the
    // half most easily broken by a rewrite: two builds four minutes apart are one alert, and they
    // are one alert about *upgrades* rather than about facilities.
    @Test
    fun `single still chains completions that land within five minutes of each other`() = runTest {
        // given two facilities landing four minutes apart
        val scheduler = FakeNotificationScheduler()
        val state = freshState().copy(
            builds = builds(
                BuildingType.METAL_MINE to EPOCH + 60.minutes,
                BuildingType.CRYSTAL_MINE to EPOCH + 64.minutes,
            ),
        )

        // when
        GameNotifications(scheduler, English).sync(state, now = EPOCH, settings = byCategory(NotificationGrouping.SINGLE))

        // then
        val notification = scheduler.scheduled.single()
        assertEquals(EPOCH + 64.minutes, notification.at)
        assertEquals("Two upgrades are done", notification.title)
    }

    private companion object {

        val EPOCH: Instant = Instant.fromEpochMilliseconds(0)

        fun byCategory(grouping: NotificationGrouping): NotificationSettings = NotificationSettings(
            scope = NotificationScope.BY_CATEGORY,
            grouping = grouping,
            categories = NotificationCategory.entries.toSet(),
        )

        fun freshState(): GameState = GameState.initial(GalaxySeed(20_260_823))

        fun builds(vararg landing: Pair<BuildingType, Instant>): Map<BuildingType, BuildJob> =
            landing.associate { (building, at) ->
                building to BuildJob(
                    building = building,
                    toLevel = BuildingLevel(2),
                    startedAt = EPOCH,
                    completesAt = at,
                )
            }

        fun yard(ship: ShipType, count: Int): List<YardJob> = (1..count).map {
            YardJob(ship = ship, startedAt = EPOCH + (it - 1).hours, completesAt = EPOCH + it.hours)
        }

        fun runLandingAt(instant: Instant): FleetRun = FleetRun(
            target = GalaxyCoordinate(galaxy = 2, system = 117, slot = 9),
            ships = Ships.of(ShipType.SKIFF, 2),
            gathering = ResourceKind.METAL,
            cargo = Resources.of(metal = 500),
            dispatchedAt = instant - 1.hours,
            returnsAt = instant,
            announced = true,
        )

        // A facility, two hulls and a fleet — three categories, with the facility landing last so a
        // test can name the instant a summary has to fire at.
        fun busyColony(): GameState = freshState().copy(
            builds = builds(BuildingType.METAL_MINE to EPOCH + 30.hours),
            yard = yard(ShipType.SKIFF, count = 2),
            runs = listOf(runLandingAt(EPOCH + 3.hours)),
        )

        // Four kinds at once: two facilities, the applied project, a ladder and a fleet coming home.
        fun fourKindsOfNews(): GameState = freshState().copy(
            builds = builds(
                BuildingType.METAL_MINE to EPOCH + 1.hours,
                BuildingType.CRYSTAL_MINE to EPOCH + 2.hours,
            ),
            activeResearch = ResearchJob(
                technology = Technology.PHOTOVOLTAICS,
                toLevel = TechLevel(1),
                startedAt = EPOCH,
                completesAt = EPOCH + 4.hours,
            ),
            activeAdaptation = AdaptationJob(
                technology = AdaptationTechnology.THERMAL,
                toLevel = TechLevel(1),
                startedAt = EPOCH,
                completesAt = EPOCH + 5.hours,
            ),
            runs = listOf(runLandingAt(EPOCH + 6.hours)),
        )
    }
}
