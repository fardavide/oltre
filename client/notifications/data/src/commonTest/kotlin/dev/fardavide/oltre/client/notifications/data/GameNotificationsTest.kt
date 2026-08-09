package dev.fardavide.oltre.client.notifications.data

import dev.fardavide.oltre.core.BuildJob
import dev.fardavide.oltre.core.BuildingLevel
import dev.fardavide.oltre.core.BuildingType
import dev.fardavide.oltre.core.Coordinates
import dev.fardavide.oltre.core.FutureEvent
import dev.fardavide.oltre.core.GalaxyBalance
import dev.fardavide.oltre.core.GalaxySeed
import dev.fardavide.oltre.core.GameState
import dev.fardavide.oltre.core.PlaceholderBalance
import dev.fardavide.oltre.core.ResearchBalance
import dev.fardavide.oltre.core.Resources
import dev.fardavide.oltre.core.ReturningFleet
import dev.fardavide.oltre.core.ShipType
import dev.fardavide.oltre.core.StartResearchResult
import dev.fardavide.oltre.core.StartSurveyResult
import dev.fardavide.oltre.core.StartUpgradeResult
import dev.fardavide.oltre.core.SystemAddress
import dev.fardavide.oltre.core.TechLevel
import dev.fardavide.oltre.core.Technology
import dev.fardavide.oltre.core.futureEvents
import dev.fardavide.oltre.core.startResearch
import dev.fardavide.oltre.core.startSurvey
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
    fun `a probe in flight is announced at the instant it lands`() = runTest {
        // given the second verb's only way of reaching a player who is not holding the phone —
        // which is the whole reason it exists, since a dispatch is bought to cover a gap
        val scheduler = FakeNotificationScheduler()
        val state = surveying(systemsAway = 12)
        val target = state.surveys.single().target

        // when
        GameNotifications(scheduler).sync(state, now = EPOCH)

        // then
        val notification = scheduler.scheduled.single()
        assertEquals(state.surveys.single().completesAt, notification.at)
        assertTrue(
            "${target.galaxy}:${target.system}" in notification.title,
            "a landing is about somewhere; title was '${notification.title}'",
        )
    }

    @Test
    fun `a landing that charted nothing worth a look says so rather than counting worlds at it`() = runTest {
        // given the common case, by construction: round 9 measured ~14 dispatches to see one world
        // worth remarking on. An alert that only ever said "4 worlds charted" would read as a
        // payoff thirteen times out of fourteen, and the fourteenth would be indistinguishable
        // from the thirteen that were not.
        val scheduler = FakeNotificationScheduler()
        val state = surveyingSomething(worthTaking = false)

        // when
        GameNotifications(scheduler).sync(state, now = EPOCH)

        // then
        val notification = scheduler.scheduled.single()
        assertTrue("none" in notification.body, "body was '${notification.body}'")
    }

    @Test
    fun `a landing with something in it counts what cleared the bar`() = runTest {
        // given the fourteenth dispatch — the one the other thirteen exist to make legible
        val scheduler = FakeNotificationScheduler()
        val state = surveyingSomething(worthTaking = true)

        // when
        GameNotifications(scheduler).sync(state, now = EPOCH)

        // then
        val notification = scheduler.scheduled.single()
        assertTrue("worth a look" in notification.body, "body was '${notification.body}'")
        assertTrue("none" !in notification.body, "body was '${notification.body}'")
    }

    @Test
    fun `probes never push a build off the end of the schedule`() = runTest {
        // given far more probes in flight than iOS will hold, all landing before a long build.
        // iOS keeps the 64 *soonest* pending requests and silently drops the rest, so the ones it
        // throws away are the furthest out — which is exactly where long builds and research
        // completions live. Left uncapped, thirty dispatches would cost the player the alert they
        // actually planned their evening around.
        val scheduler = FakeNotificationScheduler()
        val state = swarming(probes = 90).copy(builds = aBuildLandingAfterEveryProbe())

        // when
        GameNotifications(scheduler).sync(state, now = EPOCH)

        // then — the cap is ours to spend rather than the platform's to apply
        assertTrue(scheduler.scheduled.size <= IOS_PENDING_REQUEST_LIMIT, "was ${scheduler.scheduled.size}")
        assertTrue(
            scheduler.scheduled.any { "Nanite Factory" in it.title },
            "the one alert a player waits hours for must not be the one evicted",
        )
    }

    @Test
    fun `what a cap drops is the furthest probe and never the nearest`() = runTest {
        // given more probes than the platform holds and nothing else in flight
        val scheduler = FakeNotificationScheduler()
        val state = swarming(probes = 90)

        // when
        GameNotifications(scheduler).sync(state, now = EPOCH)

        // then the soonest survive: they fire first, and every one of them re-derives the whole
        // set on the transition it causes, so a dropped far landing is re-booked long before it
        // was due. Dropping the near ones instead would lose alerts nothing would ever re-book.
        val kept = scheduler.scheduled.map { it.at }
        val dropped = futureEvents(state).map { it.at } - kept.toSet()
        assertEquals(IOS_PENDING_REQUEST_LIMIT, kept.size)
        assertTrue(dropped.isNotEmpty() && kept.max() <= dropped.min())
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

    // A colony with one probe in flight, aimed `systemsAway` from home in whichever direction the
    // map has room for — so a fixture cannot fall off the edge of the coordinate space, and does
    // not depend on where this seed put the player.
    private fun surveying(systemsAway: Int): GameState {
        val state = wealthy()
        return assertIs<StartSurveyResult.Started>(
            startSurvey(state, awayFromHome(state, systemsAway), at = EPOCH),
        ).state
    }

    // The nearest target whose landing will, or will not, chart something over the worth-it bar.
    // Found by asking `futureEvents` what each candidate would report rather than by hardcoding a
    // coordinate: the prediction is the thing under test's own input, so a fixture picked this way
    // cannot disagree with it, and it survives the seed's home moving.
    private fun surveyingSomething(worthTaking: Boolean): GameState {
        val state = wealthy()
        for (away in 1..GalaxyBalance.SYSTEMS_PER_GALAXY) {
            val started = startSurvey(state, awayFromHome(state, away), at = EPOCH)
            if (started !is StartSurveyResult.Started) continue
            val landing = futureEvents(started.state).filterIsInstance<FutureEvent.SurveyLands>().single()
            if ((landing.worthTaking > 0) == worthTaking) return started.state
        }
        error("no target within a galaxy of home charts ${if (worthTaking) "something" else "nothing"} worth taking")
    }

    // More probes in flight than the platform will hold. Dispatched outward, so their landings are
    // ordered by distance and a test can name which end a cap is expected to keep.
    private fun swarming(probes: Int): GameState {
        var state = wealthy()
        var away = 1
        while (state.surveys.size < probes) {
            check(away <= GalaxyBalance.SYSTEMS_PER_GALAXY) { "ran out of map before reaching $probes probes" }
            (startSurvey(state, awayFromHome(state, away), at = EPOCH) as? StartSurveyResult.Started)
                ?.let { state = it.state }
            away++
        }
        return state
    }

    // Written out rather than started through `startUpgrade`, so the fixture states the one thing
    // it is about — a completion later than every probe's — instead of inheriting it from whatever
    // the duration curve happens to be this week.
    private fun aBuildLandingAfterEveryProbe(): Map<BuildingType, BuildJob> = mapOf(
        BuildingType.NANITE_FACTORY to BuildJob(
            building = BuildingType.NANITE_FACTORY,
            toLevel = BuildingLevel(1),
            startedAt = EPOCH,
            completesAt = EPOCH + 1_000.hours,
        ),
    )

    private fun awayFromHome(state: GameState, systemsAway: Int): SystemAddress {
        val home = state.galaxy.home
        val up = home.system + systemsAway
        val down = home.system - systemsAway
        return SystemAddress(
            galaxy = home.galaxy,
            system = if (up <= GalaxyBalance.SYSTEMS_PER_GALAXY) up else down.coerceAtLeast(1),
        )
    }

    private fun wealthy(): GameState =
        freshState().copy(resources = Resources.of(metal = 1_000_000, crystal = 1_000, deuterium = 1_000))

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
