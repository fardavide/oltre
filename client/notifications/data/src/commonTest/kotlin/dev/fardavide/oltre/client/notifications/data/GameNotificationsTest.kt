package dev.fardavide.oltre.client.notifications.data

import dev.fardavide.oltre.client.design.text.English
import dev.fardavide.oltre.core.AdaptationBalance
import dev.fardavide.oltre.core.AdaptationJob
import dev.fardavide.oltre.core.AdaptationTechnology
import dev.fardavide.oltre.core.AlertSettings
import dev.fardavide.oltre.core.BuildJob
import dev.fardavide.oltre.core.BuildingLevel
import dev.fardavide.oltre.core.BuildingType
import dev.fardavide.oltre.core.FleetRun
import dev.fardavide.oltre.core.FutureEvent
import dev.fardavide.oltre.core.GalaxyBalance
import dev.fardavide.oltre.core.GalaxyCoordinate
import dev.fardavide.oltre.core.GalaxySeed
import dev.fardavide.oltre.core.GameState
import dev.fardavide.oltre.core.HullAlert
import dev.fardavide.oltre.core.PlaceholderBalance
import dev.fardavide.oltre.core.ResearchBalance
import dev.fardavide.oltre.core.ResearchJob
import dev.fardavide.oltre.core.ResourceKind
import dev.fardavide.oltre.core.Resources
import dev.fardavide.oltre.core.ShipType
import dev.fardavide.oltre.core.Ships
import dev.fardavide.oltre.core.StartAdaptationResult
import dev.fardavide.oltre.core.StartResearchResult
import dev.fardavide.oltre.core.StartSurveyResult
import dev.fardavide.oltre.core.StartUpgradeResult
import dev.fardavide.oltre.core.SystemAddress
import dev.fardavide.oltre.core.TechLevel
import dev.fardavide.oltre.core.Technology
import dev.fardavide.oltre.core.WatchTarget
import dev.fardavide.oltre.core.YardJob
import dev.fardavide.oltre.core.futureEvents
import dev.fardavide.oltre.core.timeUntilAffordable
import dev.fardavide.oltre.core.toggleAlert
import dev.fardavide.oltre.core.watchedPurchase
import dev.fardavide.oltre.core.startAdaptation
import dev.fardavide.oltre.core.startResearch
import dev.fardavide.oltre.core.startSurvey
import dev.fardavide.oltre.core.startUpgrade
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

class GameNotificationsTest {

    @Test
    fun `a colony with nothing in flight schedules nothing`() = runTest {
        // given
        val scheduler = FakeNotificationScheduler()

        // when
        GameNotifications(scheduler, English).sync(freshState(), now = EPOCH)

        // then
        assertEquals(emptyList(), scheduler.scheduled)
    }

    @Test
    fun `a running build is announced at the instant it completes`() = runTest {
        // given
        val scheduler = FakeNotificationScheduler()
        val state = building(BuildingType.METAL_MINE)

        // when
        GameNotifications(scheduler, English).sync(state, now = EPOCH)

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
        val state = freshState().copy(runs = listOf(fleetReturningAt(EPOCH + 3.hours)))

        // when
        GameNotifications(scheduler, English).sync(state, now = EPOCH)

        // then — the world the hold was filled at is what the alert is about
        val notification = scheduler.scheduled.single()
        assertEquals(EPOCH + 3.hours, notification.at)
        assertTrue("2:117:9" in notification.body, "body was '${notification.body}'")
    }

    @Test
    fun `two runs landing at the same instant each get their own alert`() = runTest {
        // The defect the run-shaped id exists to fix. A colony could once hold exactly one returning
        // fleet, so the constant `"fleet-arrival"` was unique by construction; runs are parallel and
        // uncapped, and two landing together would collapse into a single alert with one of them
        // silently gone — a check-in loop that hides half of what happened while it was closed.
        val scheduler = FakeNotificationScheduler()
        val together = EPOCH + 3.hours
        val state = freshState().copy(
            runs = listOf(
                fleetReturningAt(together),
                fleetReturningAt(together, target = GalaxyCoordinate(galaxy = 4, system = 3, slot = 2)),
            ),
        )

        // when
        GameNotifications(scheduler, English).sync(state, now = EPOCH)

        // then
        assertEquals(2, scheduler.scheduled.size)
        assertEquals(2, scheduler.scheduled.map { it.id }.toSet().size, "ids must not collide")
        // And each one speaks about its own run rather than about the fleet in general — asserted
        // as a set because which of two simultaneous returns sorts first is core's call.
        assertEquals(
            setOf("The cargo from [2:117:9] is in your stores.", "The cargo from [4:3:2] is in your stores."),
            scheduler.scheduled.map { it.body }.toSet(),
        )
    }

    // **The change this version is.** Every fixture above subscribes what it starts, because that is
    // what those tests are about; these four are about the gate itself.
    @Test
    fun `a build nobody asked about is not announced at all`() = runTest {
        // given a colony building with no subscription — the state every colony is in until a
        // square is tapped
        val scheduler = FakeNotificationScheduler()
        val state = building(BuildingType.METAL_MINE).copy(subscribed = emptySet())

        // when
        GameNotifications(scheduler, English).sync(state, now = EPOCH)

        // then — absent rather than trimmed: the player never asked, so there is nothing to weigh
        // against the platform's ceiling
        assertEquals(emptyList(), scheduler.scheduled)
    }

    @Test
    fun `asking about one build of two leaves the other silent`() = runTest {
        // given
        val scheduler = FakeNotificationScheduler()
        val state = building(BuildingType.METAL_MINE, BuildingType.SOLAR_PLANT)
            .copy(subscribed = setOf(WatchTarget.Facility(BuildingType.SOLAR_PLANT)))

        // when
        GameNotifications(scheduler, English).sync(state, now = EPOCH)

        // then
        assertEquals("Solar Plant reached level 2", scheduler.scheduled.single().title)
    }

    // **The two kinds that were still firing on their own, and the reversal is deliberate.** This
    // file used to hold `a probe still lands without being asked about`, on the argument that only
    // *completions* went opt-in and a probe is not something you wait on a row for. What was true
    // about that was the second half — a flight has no row — and what it concluded from it was
    // backwards: the absence of a control is a reason to *add one*, not a licence to announce
    // unasked. Davide's call, 2026-08-22, putting the bell beside Dispatch. From this version every
    // alert in the game is one that was asked for.
    @Test
    fun `a returning fleet nobody asked about is not announced at all`() = runTest {
        // given
        val scheduler = FakeNotificationScheduler()
        val state = freshState().copy(runs = listOf(fleetReturningAt(EPOCH + 3.hours, announced = false)))

        // when
        GameNotifications(scheduler, English).sync(state, now = EPOCH)

        // then — absent rather than trimmed, exactly as an unsubscribed build is: there is nothing to
        // weigh against the platform's ceiling, because the player never asked
        assertEquals(emptyList(), scheduler.scheduled)
    }

    @Test
    fun `a probe nobody asked about is not announced at all`() = runTest {
        val scheduler = FakeNotificationScheduler()

        GameNotifications(scheduler, English).sync(surveying(systemsAway = 20, announced = false), now = EPOCH)

        assertEquals(emptyList(), scheduler.scheduled)
    }

    @Test
    fun `asking about one run of two leaves the other silent`() = runTest {
        // The per-flight half. Both runs are in the air and only one carries the ask, so a gate that
        // read the *colony's* bell rather than the run's would announce both — which is exactly what
        // a player who lit the bell for one world and unlit it for the next did not ask for.
        val scheduler = FakeNotificationScheduler()
        val state = freshState().copy(
            announceFlights = false,
            runs = listOf(
                fleetReturningAt(EPOCH + 3.hours, announced = true),
                fleetReturningAt(
                    EPOCH + 4.hours,
                    target = GalaxyCoordinate(galaxy = 4, system = 3, slot = 2),
                    announced = false,
                ),
            ),
        )

        GameNotifications(scheduler, English).sync(state, now = EPOCH)

        assertEquals("The cargo from [2:117:9] is in your stores.", scheduler.scheduled.single().body)
    }

    @Test
    fun `several landing together arrive as one alert`() = runTest {
        // given three subscribed builds finishing inside five minutes of each other — the case
        // opt-in is for, and the reason the collapse exists: three buzzes for one check-in.
        val scheduler = FakeNotificationScheduler()
        val state = subscribedBuilds(
            BuildingType.CRYSTAL_MINE to EPOCH + 30.minutes,
            BuildingType.SOLAR_PLANT to EPOCH + 32.minutes,
            BuildingType.DEUTERIUM_SYNTHESIZER to EPOCH + 34.minutes,
        )

        // when
        GameNotifications(scheduler, English).sync(state, now = EPOCH)

        // then — one alert, at the instant the *last* of them lands, because "three upgrades are
        // done" is not true until the third one is
        val notification = scheduler.scheduled.single()
        assertEquals(EPOCH + 34.minutes, notification.at)
        assertEquals("Three upgrades are done", notification.title)
        assertEquals(
            "Crystal Mine, Solar Plant and Deuterium Synthesizer — pick what your colony builds next.",
            notification.body,
        )
    }

    @Test
    fun `a group's id comes from the instant it fires`() = runTest {
        // given — the one id in the file not derived from its subject, because a group's subject is
        // a set that changes the moment one more row is subscribed, and the instant does not
        val scheduler = FakeNotificationScheduler()
        val state = subscribedBuilds(
            BuildingType.CRYSTAL_MINE to EPOCH + 30.minutes,
            BuildingType.SOLAR_PLANT to EPOCH + 32.minutes,
        )

        // when
        GameNotifications(scheduler, English).sync(state, now = EPOCH)

        // then
        assertEquals("group-${(EPOCH + 32.minutes).toEpochMilliseconds()}", scheduler.scheduled.single().id)
    }

    @Test
    fun `a build landing well after the others keeps its own alert`() = runTest {
        // given two together and one two hours behind — the frame the design drew: the player asked
        // about three and only the near pair is one piece of news
        val scheduler = FakeNotificationScheduler()
        val state = subscribedBuilds(
            BuildingType.CRYSTAL_MINE to EPOCH + 30.minutes,
            BuildingType.SOLAR_PLANT to EPOCH + 32.minutes,
            BuildingType.METAL_MINE to EPOCH + 3.hours,
        )

        // when
        GameNotifications(scheduler, English).sync(state, now = EPOCH)

        // then
        assertEquals(2, scheduler.scheduled.size)
        assertEquals(setOf("Two upgrades are done", "Metal Mine reached level 2"), scheduler.scheduled.map { it.title }.toSet())
    }

    @Test
    fun `the window chains rather than measuring from the first`() = runTest {
        // given four builds four minutes apart each — a quarter of an hour end to end, and still
        // one alert, because by the time the last lands the player has been told nothing about any
        // of the others either
        val scheduler = FakeNotificationScheduler()
        val state = subscribedBuilds(
            BuildingType.CRYSTAL_MINE to EPOCH + 20.minutes,
            BuildingType.SOLAR_PLANT to EPOCH + 24.minutes,
            BuildingType.DEUTERIUM_SYNTHESIZER to EPOCH + 28.minutes,
            BuildingType.METAL_MINE to EPOCH + 32.minutes,
        )

        // when
        GameNotifications(scheduler, English).sync(state, now = EPOCH)

        // then
        assertEquals("Four upgrades are done", scheduler.scheduled.single().title)
    }

    // The two ends of the window, which decide what the constant *is* rather than that there is one.
    // Without them 4.minutes and 6.minutes both pass, and so does flipping the comparison.
    @Test
    fun `two landing exactly five minutes apart are one alert`() = runTest {
        // given the boundary itself — "inside five minutes" includes it
        val scheduler = FakeNotificationScheduler()
        val state = subscribedBuilds(
            BuildingType.CRYSTAL_MINE to EPOCH + 30.minutes,
            BuildingType.SOLAR_PLANT to EPOCH + 35.minutes,
        )

        // when
        GameNotifications(scheduler, English).sync(state, now = EPOCH)

        // then
        assertEquals("Two upgrades are done", scheduler.scheduled.single().title)
    }

    @Test
    fun `two landing a second past five minutes are two alerts`() = runTest {
        // given one second the other side of it
        val scheduler = FakeNotificationScheduler()
        val state = subscribedBuilds(
            BuildingType.CRYSTAL_MINE to EPOCH + 30.minutes,
            BuildingType.SOLAR_PLANT to EPOCH + 35.minutes + 1.seconds,
        )

        // when
        GameNotifications(scheduler, English).sync(state, now = EPOCH)

        // then
        assertEquals(2, scheduler.scheduled.size)
    }

    // **The ceiling, and it moved at 0.12.2**: six facilities, one applied project and one ladder is
    // every completion a colony can hold, so eight is the largest group there is and this is the
    // longest body a lock screen will ever be handed. It was seven while the two research branches
    // shared a slot — which is precisely the kind of ceiling that goes wrong quietly, because
    // `spelled()` had an `else` branch on it and an eighth member would have been announced as seven.
    @Test
    fun `the whole colony landing together is one alert of eight`() = runTest {
        // given every facility building, a project in one slot and a ladder in the other, all inside
        // one window
        val scheduler = FakeNotificationScheduler()
        val state = subscribedBuilds(
            *BuildingType.entries.mapIndexed { index, building ->
                building to EPOCH + 30.minutes + (index * 30).seconds
            }.toTypedArray(),
        ).let { colony ->
            colony.copy(
                activeResearch = ResearchJob(
                    technology = Technology.EXTRACTION,
                    toLevel = TechLevel(1),
                    startedAt = EPOCH,
                    completesAt = EPOCH + 33.minutes,
                ),
                activeAdaptation = AdaptationJob(
                    technology = AdaptationTechnology.THERMAL,
                    toLevel = TechLevel(1),
                    startedAt = EPOCH,
                    completesAt = EPOCH + 33.minutes + 30.seconds,
                ),
                subscribed = colony.subscribed +
                    WatchTarget.Project(Technology.EXTRACTION) +
                    WatchTarget.Ladder(AdaptationTechnology.THERMAL),
            )
        }

        // when
        GameNotifications(scheduler, English).sync(state, now = EPOCH)

        // then — spelled to the end of the count the model allows, and every name listed: a group
        // that said "and 4 more" would be an alert you have to open the app to understand
        // The names are in the order the things actually land, which is core's ordering and not this
        // file's: both slots free after the last facility here, applied before adaptation.
        val notification = scheduler.scheduled.single()
        assertEquals("Eight upgrades are done", notification.title)
        assertEquals(
            // "Thermal Adaptation" rather than the Research screen's "Thermal": an alert is read
            // with the app closed and has no heading above it to borrow the noun from.
            "Metal Mine, Crystal Mine, Deuterium Synthesizer, Solar Plant, Robotics Factory, " +
                "Nanite Factory, Extraction and Thermal Adaptation — pick what your colony builds next.",
            notification.body,
        )
    }

    // The seventh, which now has a branch of its own to be spelled by rather than falling through an
    // `else`. Six facilities and one project: the shape the test above had before the split.
    @Test
    fun `seven is spelled out rather than reached by falling off the end`() = runTest {
        val scheduler = FakeNotificationScheduler()
        val state = subscribedBuilds(
            *BuildingType.entries.mapIndexed { index, building ->
                building to EPOCH + 30.minutes + (index * 30).seconds
            }.toTypedArray(),
        ).let { colony ->
            colony.copy(
                activeResearch = ResearchJob(
                    technology = Technology.EXTRACTION,
                    toLevel = TechLevel(1),
                    startedAt = EPOCH,
                    completesAt = EPOCH + 33.minutes,
                ),
                subscribed = colony.subscribed + WatchTarget.Project(Technology.EXTRACTION),
            )
        }

        GameNotifications(scheduler, English).sync(state, now = EPOCH)

        assertEquals("Seven upgrades are done", scheduler.scheduled.single().title)
    }

    // The counts between the pair and the ceiling, so the whole spelled table is exercised rather
    // than its two ends. Six facilities is as far as builds alone reach; the seventh needs the
    // research slot, and has the test above to itself.
    @Test
    fun `the count is spelled out at every size builds alone can reach`() = runTest {
        val titles = (2..6).map { size ->
            val scheduler = FakeNotificationScheduler()
            val state = subscribedBuilds(
                *BuildingType.entries.take(size).mapIndexed { index, building ->
                    building to EPOCH + 30.minutes + (index * 30).seconds
                }.toTypedArray(),
            )
            GameNotifications(scheduler, English).sync(state, now = EPOCH)
            scheduler.scheduled.single().title
        }

        assertEquals(
            listOf("Two", "Three", "Four", "Five", "Six").map { "$it upgrades are done" },
            titles,
        )
    }

    @Test
    fun `a finished ladder joins the group under its full name`() = runTest {
        // given a ladder holding its branch's slot and landing beside a facility. The group's naming is
        // its own branch, so without this the ladder arm is held up by the compiler alone — which is
        // exactly the state the singleton alerts were in before their own test.
        val scheduler = FakeNotificationScheduler()
        val state = subscribedBuilds(BuildingType.METAL_MINE to EPOCH + 30.minutes).let { colony ->
            colony.copy(
                activeAdaptation = AdaptationJob(
                    technology = AdaptationTechnology.GRAVITIC,
                    toLevel = TechLevel(1),
                    startedAt = EPOCH,
                    completesAt = EPOCH + 31.minutes,
                ),
                subscribed = colony.subscribed + WatchTarget.Ladder(AdaptationTechnology.GRAVITIC),
            )
        }

        // when
        GameNotifications(scheduler, English).sync(state, now = EPOCH)

        // then — spelled out with the word the row drops, exactly as the singleton alert spells it
        assertEquals(
            "Metal Mine and Gravitic Adaptation — pick what your colony builds next.",
            scheduler.scheduled.single().body,
        )
    }

    @Test
    fun `a research completion joins the group beside the facilities`() = runTest {
        // given — the design's own card names Extraction among two facilities, so the group is not
        // a facility-only idea: what the three share is a slot they free
        val scheduler = FakeNotificationScheduler()
        val state = subscribedBuilds(BuildingType.METAL_MINE to EPOCH + 30.minutes)
            .let { colony ->
                colony.copy(
                    activeResearch = ResearchJob(
                        technology = Technology.EXTRACTION,
                        toLevel = TechLevel(1),
                        startedAt = EPOCH,
                        completesAt = EPOCH + 31.minutes,
                    ),
                    subscribed = colony.subscribed + WatchTarget.Project(Technology.EXTRACTION),
                )
            }

        // when
        GameNotifications(scheduler, English).sync(state, now = EPOCH)

        // then
        assertEquals("Two upgrades are done", scheduler.scheduled.single().title)
        assertEquals(
            "Metal Mine and Extraction — pick what your colony builds next.",
            scheduler.scheduled.single().body,
        )
    }

    @Test
    fun `a watched row is announced at the instant the colony can pay for it`() = runTest {
        // given — the only alert in the game about something that has not happened
        val scheduler = FakeNotificationScheduler()
        val state = watching(WatchTarget.Facility(BuildingType.METAL_MINE))

        // when
        GameNotifications(scheduler, English).sync(state, now = EPOCH)

        // then — the instant is the one the row itself prints, so the lock screen and the card
        // cannot disagree about when
        val notification = scheduler.scheduled.single()
        val purchase = checkNotNull(state.watchedPurchase())
        assertEquals(
            EPOCH + timeUntilAffordable(state.resources, purchase.cost, state.buildings, state.research),
            notification.at,
        )
        assertEquals("You can afford Metal Mine", notification.title)
        assertEquals("The colony has the resources for level 2.", notification.body)
    }

    @Test
    fun `a watched row the colony can already pay for books nothing`() = runTest {
        // given — the opening stocks cover the first mine level outright, so there is no instant
        val scheduler = FakeNotificationScheduler()
        val state = toggleAlert(freshState(), WatchTarget.Facility(BuildingType.METAL_MINE))

        // when
        GameNotifications(scheduler, English).sync(state, now = EPOCH)

        // then
        assertEquals(emptyList(), scheduler.scheduled)
    }

    @Test
    fun `a watched technology names the discipline and the level it would buy`() = runTest {
        // given
        val scheduler = FakeNotificationScheduler()

        // when
        GameNotifications(scheduler, English).sync(watching(WatchTarget.Project(Technology.EXTRACTION)), now = EPOCH)

        // then
        val notification = scheduler.scheduled.single()
        assertEquals("You can afford Extraction", notification.title)
        assertEquals("The colony has the resources for level 1.", notification.body)
    }

    @Test
    fun `a watched ladder is named the way the lock screen names one`() = runTest {
        // given — spelled out with the word the row drops, like every other adaptation alert
        val scheduler = FakeNotificationScheduler()

        // when
        GameNotifications(scheduler, English).sync(watching(WatchTarget.Ladder(AdaptationTechnology.GRAVITIC)), now = EPOCH)

        // then
        assertEquals("You can afford Gravitic Adaptation", scheduler.scheduled.single().title)
    }

    @Test
    fun `a watched facility is spelled out in full`() = runTest {
        // given — the row abbreviates to fit its width and a lock screen has the room
        val scheduler = FakeNotificationScheduler()

        // when
        GameNotifications(scheduler, English)
            .sync(watching(WatchTarget.Facility(BuildingType.DEUTERIUM_SYNTHESIZER)), now = EPOCH)

        // then
        assertEquals("You can afford Deuterium Synthesizer", scheduler.scheduled.single().title)
    }

    @Test
    fun `the watch never competes with probe landings for the platform's ceiling`() = runTest {
        // given a swarm of probes big enough to overflow iOS's 64, plus a watch
        val scheduler = FakeNotificationScheduler()
        val state = toggleAlert(
            swarming(probes = IOS_PENDING_REQUEST_LIMIT + 10).copy(resources = Resources.of()),
            WatchTarget.Facility(BuildingType.METAL_MINE),
        )

        // when
        GameNotifications(scheduler, English).sync(state, now = EPOCH)

        // then — one watch is bounded by the model exactly as the six facilities and the one
        // research slot are, so it is protected rather than trimmed
        assertEquals(IOS_PENDING_REQUEST_LIMIT, scheduler.scheduled.size)
        assertEquals(1, scheduler.scheduled.count { it.id.startsWith("affordable-") })
    }

    @Test
    fun `a running research is announced at the instant it completes`() = runTest {
        // given
        val scheduler = FakeNotificationScheduler()
        val state = researching(Technology.EXTRACTION)

        // when
        GameNotifications(scheduler, English).sync(state, now = EPOCH)

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
        GameNotifications(scheduler, English).sync(state, now = EPOCH)

        // then
        assertEquals(2, scheduler.scheduled.size)
        assertEquals(2, scheduler.scheduled.map { it.id }.toSet().size, "ids must not collide")
        assertEquals(scheduler.scheduled.map { it.at }.sorted(), scheduler.scheduled.map { it.at })
    }

    @Test
    fun `facilities finishing far apart each get their own alert`() = runTest {
        // given two facilities building at once and landing hours apart, so neither collapses into
        // the other — the Nanite Factory is written out rather than started, because what this test
        // needs is a gap wider than the grouping window and the curves decide the real one.
        val scheduler = FakeNotificationScheduler()
        val state = building(BuildingType.METAL_MINE, on = withNaniteGate())
            .let { it.copy(builds = it.builds + aBuildLandingAfterEveryProbe()) }
            .let { toggleAlert(it, WatchTarget.Facility(BuildingType.NANITE_FACTORY)) }

        // when
        GameNotifications(scheduler, English).sync(state, now = EPOCH)

        // then
        assertEquals(2, scheduler.scheduled.size)
        assertEquals(2, scheduler.scheduled.map { it.id }.toSet().size, "ids must not collide")
    }

    @Test
    fun `alerts are handed over in the order they will fire`() = runTest {
        // given a fleet landing before either build finishes
        val scheduler = FakeNotificationScheduler()
        val state = building(BuildingType.METAL_MINE, BuildingType.SOLAR_PLANT)
            .copy(runs = listOf(fleetReturningAt(EPOCH + 1.hours)))

        // when
        GameNotifications(scheduler, English).sync(state, now = EPOCH)

        // then
        assertEquals(scheduler.scheduled.map { it.at }.sorted(), scheduler.scheduled.map { it.at })
    }

    @Test
    fun `something already due is not scheduled into the past`() = runTest {
        // given a state carrying a build that finished an hour ago — a stale session resuming
        val scheduler = FakeNotificationScheduler()
        val state = building(BuildingType.METAL_MINE)

        // when the colony is synced long after everything in it was due
        GameNotifications(scheduler, English).sync(state, now = EPOCH + 5.hours)

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
        GameNotifications(scheduler, English).sync(state, now = completesAt)

        // then — the player is looking at the app; the event is about to be applied by advance
        assertEquals(emptyList(), scheduler.scheduled)
    }

    @Test
    fun `syncing an empty colony clears whatever was pending`() = runTest {
        // given a scheduler holding the alerts of a colony that was building
        val scheduler = FakeNotificationScheduler()
        val notifications = GameNotifications(scheduler, English)
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
        GameNotifications(scheduler, English).sync(state, now = EPOCH)

        // then
        val notification = scheduler.scheduled.single()
        assertEquals(state.surveys.single().completesAt, notification.at)
        assertTrue(
            "${target.galaxy}:${target.system}" in notification.title,
            "a landing is about somewhere; title was '${notification.title}'",
        )
    }

    @Test
    fun `a landing that charted nowhere settleable says so rather than counting worlds at it`() = runTest {
        // given the common case, by construction: round 9 measured ~14 dispatches to see one world
        // worth remarking on. An alert that only ever said "4 worlds charted" would read as a
        // payoff thirteen times out of fourteen, and the fourteenth would be indistinguishable
        // from the thirteen that were not.
        val scheduler = FakeNotificationScheduler()
        val state = surveyingSomething(settleable = false)

        // when
        GameNotifications(scheduler, English).sync(state, now = EPOCH)

        // then
        val notification = scheduler.scheduled.single()
        assertTrue("none" in notification.body, "body was '${notification.body}'")
    }

    @Test
    fun `a landing with somewhere settleable in it says so in the card's own words`() = runTest {
        // given the fourteenth dispatch — the one the other thirteen exist to make legible
        val scheduler = FakeNotificationScheduler()
        val state = surveyingSomething(settleable = true)

        // when
        GameNotifications(scheduler, English).sync(state, now = EPOCH)

        // then
        val notification = scheduler.scheduled.single()
        assertTrue("settleable" in notification.body, "body was '${notification.body}'")
        assertTrue("none" !in notification.body, "body was '${notification.body}'")
    }

    @Test
    fun `the alert and the card it is about count the same thing`() = runTest {
        // The failure this exists to stop, and one that really shipped: the body was built from a
        // yield bar that half the galaxy clears, while the Galaxy screen's landing footer counts
        // `Settleable`. The lock screen said "5 worth a look" about a landing whose card read "none
        // settleable" — a game contradicting itself between the notification and the app.
        //
        // Asserted over a whole galaxy rather than one system, because the two measures agree by
        // accident often enough that a single fixture proves nothing.
        //
        // The bell is lit on the state each probe is dispatched from, for `surveying`'s reason: the
        // sweep is about what a landing *says*, and a silent landing says nothing to compare.
        val state = wealthy().copy(announceFlights = true)
        var disagreements = 0
        var landings = 0
        for (away in 1..GalaxyBalance.SYSTEMS_PER_GALAXY) {
            val started = startSurvey(state, awayFromHome(state, away), at = EPOCH)
            if (started !is StartSurveyResult.Started) continue
            val landing = futureEvents(started.state, now = EPOCH).filterIsInstance<FutureEvent.SurveyLands>().single()
            val scheduler = FakeNotificationScheduler()
            GameNotifications(scheduler, English).sync(started.state, now = EPOCH)
            val body = scheduler.scheduled.single().body
            landings++
            val saysNone = "none settleable" in body
            if (saysNone != (landing.settleable == 0)) disagreements++
        }

        assertTrue(landings > 100, "the sweep needs to have actually landed somewhere; was $landings")
        assertEquals(0, disagreements, "of $landings landings")
    }

    @Test
    fun `probes never push a build off the end of the schedule`() = runTest {
        // given far more probes in flight than iOS will hold, all landing before a long build.
        // iOS keeps the 64 *soonest* pending requests and silently drops the rest, so the ones it
        // throws away are the furthest out — which is exactly where long builds and research
        // completions live. Left uncapped, thirty dispatches would cost the player the alert they
        // actually planned their evening around.
        val scheduler = FakeNotificationScheduler()
        val state = swarming(probes = 90)
            .copy(builds = aBuildLandingAfterEveryProbe())
            .let { toggleAlert(it, WatchTarget.Facility(BuildingType.NANITE_FACTORY)) }

        // when
        GameNotifications(scheduler, English).sync(state, now = EPOCH)

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
        GameNotifications(scheduler, English).sync(state, now = EPOCH)

        // then the soonest survive: they fire first, and every one of them re-derives the whole
        // set on the transition it causes, so a dropped far landing is re-booked long before it
        // was due. Dropping the near ones instead would lose alerts nothing would ever re-book.
        val kept = scheduler.scheduled.map { it.at }
        val dropped = futureEvents(state, now = EPOCH).map { it.at } - kept.toSet()
        assertEquals(IOS_PENDING_REQUEST_LIMIT, kept.size)
        assertTrue(dropped.isNotEmpty() && kept.max() <= dropped.min())
    }

    // ── The yard, the third unbounded kind — and opt-in since 0.16 ──────────────────────────

    @Test
    fun `a hull nobody asked about is not announced at all`() = runTest {
        // **The change this version is**, and it finishes what 0.5.0 started: a delivery used to be
        // the one thing left that fired on its own, because there was no control on a hull card to
        // ask it with. There is one now, so the rule that governs every other alert governs this one
        // — absent rather than trimmed, so it weighs nothing against the platform's ceiling.
        val scheduler = FakeNotificationScheduler()
        val state = building(hulls = 1)
        assertTrue(state.hullAlerts.isEmpty())

        GameNotifications(scheduler, English).sync(state, now = EPOCH)

        assertEquals(emptyList(), scheduler.scheduled)
    }

    @Test
    fun `one tap books a single alert at the instant the last hull of that type lands`() = runTest {
        // The first of the two questions a hull card asks. "Your five skiffs are built" is not true
        // until the fifth one is, which is the rule the upgrade group already fires by.
        val scheduler = FakeNotificationScheduler()
        val state = building(hulls = 5, ask = HullAlert.WHEN_ALL_DONE)

        GameNotifications(scheduler, English).sync(state, now = EPOCH)

        val notification = scheduler.scheduled.single()
        assertEquals(EPOCH + 5.hours, notification.at)
        assertEquals("5 hulls have left the yard", notification.title)
        assertEquals("Your Skiff order is complete — they are in your fleet and ready to send.", notification.body)
    }

    @Test
    fun `an order of one reads as the plain hull alert rather than counting to one`() = runTest {
        // The same branch the upgrade group takes at a size of one: a count is only worth saying
        // when there is more than one thing to count, and "1 hulls have left the yard" is the
        // sentence a rule stated without this test would write.
        val scheduler = FakeNotificationScheduler()
        val state = building(hulls = 1, ask = HullAlert.WHEN_ALL_DONE)

        GameNotifications(scheduler, English).sync(state, now = EPOCH)

        assertEquals("A Skiff has left the yard", scheduler.scheduled.single().title)
    }

    @Test
    fun `a second tap books an alert for every hull and every id is distinct`() = runTest {
        // The other question, and the one the `hull-` id was always built for: the id is the instant,
        // which is the only thing that separates two hulls — and it separates them because the yard
        // is serial. A queue that collided into one id would announce the last and swallow the rest.
        val scheduler = FakeNotificationScheduler()
        val state = building(hulls = 5, ask = HullAlert.EACH_HULL)

        GameNotifications(scheduler, English).sync(state, now = EPOCH)

        assertEquals(5, scheduler.scheduled.size)
        assertEquals(5, scheduler.scheduled.map { it.id }.toSet().size)
        assertTrue(scheduler.scheduled.all { "A Skiff has left the yard" == it.title })
    }

    @Test
    fun `an order's id is derived from the hull it is about`() = runTest {
        // Unlike the upgrade group's, whose subject is a *set* and so has to fall back on its
        // instant. An order's subject is the hull type, which is exactly the kind of fixed subject
        // every other id in this file is derived from — and there is at most one of these per type,
        // so it is unique by construction.
        val scheduler = FakeNotificationScheduler()
        val state = building(hulls = 3, ask = HullAlert.WHEN_ALL_DONE)

        GameNotifications(scheduler, English).sync(state, now = EPOCH)

        assertEquals("order-SKIFF", scheduler.scheduled.single().id)
    }

    @Test
    fun `asking about one hull type leaves another silent`() = runTest {
        // One control per card was Davide's call, 2026-08-22 — the queue is shared and serial, and
        // the question is per hull. A gate that read the map as a single boolean would pass every
        // other test here.
        val scheduler = FakeNotificationScheduler()
        val state = wealthy().copy(
            yard = queueOf(hulls = 2) + queueOf(hulls = 2, ship = ShipType.HAULER, from = 2.hours),
            hullAlerts = mapOf(ShipType.HAULER to HullAlert.EACH_HULL),
        )

        GameNotifications(scheduler, English).sync(state, now = EPOCH)

        assertEquals(2, scheduler.scheduled.size)
        assertTrue(scheduler.scheduled.all { "Hauler" in it.title }, "${scheduler.scheduled.map { it.title }}")
    }

    @Test
    fun `two hull types asked about different ways are booked different ways`() = runTest {
        // The map is read per type all the way through rather than collapsed to one mode, which is
        // the shape a `Map<ShipType, HullAlert>` promises and the one thing a single-type fixture
        // cannot check.
        val scheduler = FakeNotificationScheduler()
        val state = wealthy().copy(
            yard = queueOf(hulls = 2) + queueOf(hulls = 3, ship = ShipType.HAULER, from = 2.hours),
            hullAlerts = mapOf(
                ShipType.SKIFF to HullAlert.EACH_HULL,
                ShipType.HAULER to HullAlert.WHEN_ALL_DONE,
            ),
        )

        GameNotifications(scheduler, English).sync(state, now = EPOCH)

        assertEquals(
            listOf("A Skiff has left the yard", "A Skiff has left the yard", "3 hulls have left the yard"),
            scheduler.scheduled.map { it.title },
        )
        // And the order alert lands at the last hauler rather than at the first
        assertEquals(EPOCH + 5.hours, scheduler.scheduled.last().at)
    }

    @Test
    fun `a hull already delivered is not counted into the order still to come`() = runTest {
        // `notificationsFor` drops what is already due before it decides anything, so the count an
        // order announces is the count still in the air. A rule that counted the whole queue would
        // promise five hulls at an instant only three of them arrive at.
        val scheduler = FakeNotificationScheduler()
        val state = building(hulls = 5, ask = HullAlert.WHEN_ALL_DONE)

        GameNotifications(scheduler, English).sync(state, now = EPOCH + 2.hours)

        assertEquals("3 hulls have left the yard", scheduler.scheduled.single().title)
    }

    @Test
    fun `a yard longer than the platform holds is trimmed rather than overflowing it`() = runTest {
        // The third unbounded kind, and the reason the partition in `notificationsFor` had to name
        // it: `bounded.size` stops describing the protected set the moment a kind is missing from it,
        // and the trim arithmetic then under-counts against a limit the platform enforces silently.
        val scheduler = FakeNotificationScheduler()
        val state = building(hulls = IOS_PENDING_REQUEST_LIMIT + 20, ask = HullAlert.EACH_HULL)

        GameNotifications(scheduler, English).sync(state, now = EPOCH)

        assertEquals(IOS_PENDING_REQUEST_LIMIT, scheduler.scheduled.size)
    }

    @Test
    fun `a hull is what a full budget drops before a fleet coming home`() = runTest {
        // The trim order this file states: returns first, then landings, then hulls — because a
        // return carries resources a full store can void and a hull on the slipway loses nothing at
        // all by being announced late. **Unchanged by the gate**, Davide's call 2026-08-22: the
        // ladder stays as it is, and what the gate buys against the ceiling is that a hull nobody
        // asked about never enters the contest at all.
        val scheduler = FakeNotificationScheduler()
        val state = building(hulls = IOS_PENDING_REQUEST_LIMIT, ask = HullAlert.EACH_HULL)
            .copy(runs = listOf(fleetReturningAt(EPOCH + 500.hours)))

        GameNotifications(scheduler, English).sync(state, now = EPOCH)

        assertEquals(IOS_PENDING_REQUEST_LIMIT, scheduler.scheduled.size)
        assertTrue(
            scheduler.scheduled.any { "ships are home" in it.title },
            "the return was evicted by a hull: ${scheduler.scheduled.map { it.title }.toSet()}",
        )
    }

    @Test
    fun `every hull the ship set has is named rather than falling through to one word`() = runTest {
        // The four constants of `ShipType` a yard can hold beside the scout. They are reachable
        // *here* because a `YardJob` is a plain data class, and pinning them is what stops the day a
        // hull ships being the day a lock screen reads "A SETTLER has left the yard".
        for ((type, expected) in mapOf(
            ShipType.SKIFF to "Skiff",
            ShipType.HAULER to "Hauler",
            ShipType.ESCORT to "Escort",
            ShipType.SETTLER to "Settler",
        )) {
            val scheduler = FakeNotificationScheduler()
            val state = wealthy().copy(
                yard = listOf(YardJob(ship = type, startedAt = EPOCH, completesAt = EPOCH + 2.hours)),
                hullAlerts = mapOf(type to HullAlert.EACH_HULL),
            )

            GameNotifications(scheduler, English).sync(state, now = EPOCH)

            assertEquals("A $expected has left the yard", scheduler.scheduled.single().title)
        }
    }

    @Test
    fun `the order alert names the hull the card names it by`() = runTest {
        // The body carries the type, so it has to be spelled the way a lock screen spells one —
        // capitalised and in full, exactly as the singleton alert above spells it.
        for ((type, expected) in mapOf(ShipType.HAULER to "Hauler", ShipType.SETTLER to "Settler")) {
            val scheduler = FakeNotificationScheduler()
            val state = wealthy().copy(
                yard = queueOf(hulls = 2, ship = type),
                hullAlerts = mapOf(type to HullAlert.WHEN_ALL_DONE),
            )

            GameNotifications(scheduler, English).sync(state, now = EPOCH)

            assertEquals(
                "Your $expected order is complete — they are in your fleet and ready to send.",
                scheduler.scheduled.single().body,
            )
        }
    }

    // A colony with `hulls` skiffs queued and the card tapped however the test needs it. The queue
    // is written out rather than bought through `buildShips`, so the fixture states the one thing it
    // is about — how many alerts are due and when — instead of inheriting it from whatever the hull
    // curve happens to be this week.
    private fun building(hulls: Int, ask: HullAlert? = null): GameState = wealthy().copy(
        yard = queueOf(hulls = hulls),
        hullAlerts = ask?.let { mapOf(ShipType.SKIFF to it) } ?: emptyMap(),
    )

    // A serial run of `hulls` hulls an hour apart, starting at `from` — which is how a second type
    // is put *behind* the first without breaking `GameState.init`'s serial rule.
    private fun queueOf(
        hulls: Int,
        ship: ShipType = ShipType.SKIFF,
        from: Duration = Duration.ZERO,
    ): List<YardJob> = (1..hulls).map { nth ->
        YardJob(
            ship = ship,
            startedAt = EPOCH + from + (nth - 1).hours,
            completesAt = EPOCH + from + nth.hours,
        )
    }

    @Test
    fun `the same colony always produces the same alert ids`() = runTest {
        // given
        val first = FakeNotificationScheduler()
        val second = FakeNotificationScheduler()
        val state = building(BuildingType.METAL_MINE).copy(runs = listOf(fleetReturningAt(EPOCH + 3.hours)))

        // when
        GameNotifications(first, English).sync(state, now = EPOCH)
        GameNotifications(second, English).sync(state, now = EPOCH)

        // then
        assertEquals(first.scheduled, second.scheduled)
    }

    @Test
    fun `an unskipped colony books its alerts at the instants the simulation computed`() = runTest {
        // The default, and every colony until somebody shakes the phone: game time and real time
        // are the same clock, so the translation is the identity and nothing moves.
        val scheduler = FakeNotificationScheduler()
        val state = building(BuildingType.METAL_MINE)

        GameNotifications(scheduler, English).sync(state, now = EPOCH)

        assertEquals(state.builds.getValue(BuildingType.METAL_MINE).completesAt, scheduler.scheduled.single().at)
    }

    @Test
    fun `a skipped colony books its alerts at the instant the device will actually reach them`() = runTest {
        // The failure this exists to stop: the alert instants come out of the simulation in *game*
        // time, and a colony skipped four hours forward computes them four hours ahead of the clock
        // the operating system fires on. Booked untranslated, every alert is four hours late — the
        // check-in loop, which on iPhone is the entire game, broken by the debug menu.
        val scheduler = FakeNotificationScheduler()
        val skippedBy = 4.hours
        val gameNow = EPOCH + skippedBy
        val state = building(BuildingType.METAL_MINE, at = gameNow)
        val completesAt = state.builds.getValue(BuildingType.METAL_MINE).completesAt

        GameNotifications(scheduler, English).sync(state, now = gameNow, toRealTime = { it - skippedBy })

        assertEquals(completesAt - skippedBy, scheduler.scheduled.single().at)
    }

    @Test
    fun `translating moves every alert without reordering any of them`() = runTest {
        // The translation is applied after the set is chosen, so the rules that choose it — drop
        // what is already due, trim the far landings to iOS's ceiling — still run in the clock the
        // simulation computed them in. Monotone, so the set that reaches the platform is the same
        // set with a different origin.
        val plain = FakeNotificationScheduler()
        val skipped = FakeNotificationScheduler()
        val state = building(BuildingType.METAL_MINE, BuildingType.CRYSTAL_MINE)

        GameNotifications(plain, English).sync(state, now = EPOCH)
        GameNotifications(skipped, English).sync(state, now = EPOCH, toRealTime = { it - 4.hours })

        assertEquals(plain.scheduled.map { it.id }, skipped.scheduled.map { it.id })
        assertEquals(plain.scheduled.map { it.at - 4.hours }, skipped.scheduled.map { it.at })
    }

    @Test
    fun `every facility is announced by its full name`() = runTest {
        // Six names, written out rather than abbreviated the way the Colony row abbreviates them,
        // and until now only two of the six had ever been rendered by a test. They are lock-screen
        // copy: the one place the game speaks to a player who is not looking at it, and the one
        // place a wrong string cannot be noticed by whoever wrote it.
        val expected = mapOf(
            BuildingType.METAL_MINE to "Metal Mine",
            BuildingType.CRYSTAL_MINE to "Crystal Mine",
            BuildingType.DEUTERIUM_SYNTHESIZER to "Deuterium Synthesizer",
            BuildingType.SOLAR_PLANT to "Solar Plant",
            BuildingType.ROBOTICS_FACTORY to "Robotics Factory",
            BuildingType.NANITE_FACTORY to "Nanite Factory",
        )
        assertEquals(BuildingType.entries.toSet(), expected.keys, "a facility was added without a name")

        for ((building, name) in expected) {
            val scheduler = FakeNotificationScheduler()
            // The two factories start at level 0 and the four others at 1, so the level reached is
            // read off the job the colony actually started rather than restated here. The Nanite
            // Factory is also gated behind Robotics 10, which the colony is given so that all six
            // names can actually be rendered — the gate is the tech tree, not the subject here.
            val state = building(building, on = withNaniteGate())
            val toLevel = state.builds.getValue(building).toLevel.value
            GameNotifications(scheduler, English).sync(state, now = EPOCH)

            val notification = scheduler.scheduled.single()
            assertEquals("$name reached level $toLevel", notification.title)
        }
    }

    @Test
    fun `every technology is announced by its own name`() = runTest {
        val expected = mapOf(
            Technology.PHOTOVOLTAICS to "Photovoltaics",
            Technology.EXTRACTION to "Extraction",
            Technology.ENRICHMENT to "Enrichment",
            Technology.PROSPECTING to "Prospecting",
            Technology.PROPULSION to "Propulsion",
        )
        assertEquals(Technology.entries.toSet(), expected.keys, "a technology was added without a name")

        for ((technology, name) in expected) {
            val scheduler = FakeNotificationScheduler()
            // Enrichment sits behind Extraction 3, so the branch has to be climbed far enough for
            // each technology to be startable at all — the gate is the tree, not an obstacle here.
            val gated = freshState().copy(
                research = freshState().research.withLevel(Technology.EXTRACTION, TechLevel(3)),
            )
            val state = researching(technology, on = gated)
            val toLevel = checkNotNull(state.activeResearch).toLevel.value
            GameNotifications(scheduler, English).sync(state, now = EPOCH)

            assertEquals("$name reached level $toLevel", scheduler.scheduled.single().title)
        }
    }

    @Test
    fun `a finished ladder says which kind of thing climbed and points at the galaxy`() = runTest {
        // The whole adaptation branch of this file had never been rendered — no test had ever put a
        // ladder in flight — so both the name and the sentence below were held up by the compiler
        // alone. The name is spelled out in full on purpose: "Gravitic reached level 1" does not say
        // what sort of thing that is, and this is the one alert that is about somewhere else.
        val expected = mapOf(
            AdaptationTechnology.THERMAL to "Thermal Adaptation",
            AdaptationTechnology.GRAVITIC to "Gravitic Adaptation",
            AdaptationTechnology.ATMOSPHERIC to "Atmospheric Adaptation",
        )
        assertEquals(AdaptationTechnology.entries.toSet(), expected.keys, "a ladder was added without a name")

        for ((technology, name) in expected) {
            val scheduler = FakeNotificationScheduler()
            GameNotifications(scheduler, English).sync(adapting(technology), now = EPOCH)

            val notification = scheduler.scheduled.single()
            assertEquals("$name reached level 1", notification.title)
            assertEquals("adaptation-${technology.name}", notification.id)
            assertTrue("galaxy" in notification.body, "body was '${notification.body}'")
        }
    }

    @Test
    fun `a ladder and a technology never share an id`() = runTest {
        // The reason the two branches have separate id spaces: replacing the pending set is only
        // idempotent while the ids are unique, and the day a ladder and a technology are named alike
        // a shared space would silently drop one of them.
        val research = FakeNotificationScheduler()
        val adaptation = FakeNotificationScheduler()

        GameNotifications(research, English).sync(researching(Technology.EXTRACTION), now = EPOCH)
        GameNotifications(adaptation, English).sync(adapting(AdaptationTechnology.THERMAL), now = EPOCH)

        assertTrue(research.scheduled.single().id.startsWith("research-"))
        assertTrue(adaptation.scheduled.single().id.startsWith("adaptation-"))
    }

    // Robotics at the level the Nanite Factory asks for, so every facility in the game can be put
    // into flight by one loop rather than five plus a special case.
    private fun withNaniteGate(): GameState = freshState().let { state ->
        state.copy(
            buildings = state.buildings.withLevel(
                BuildingType.ROBOTICS_FACTORY,
                BuildingLevel(PlaceholderBalance.NANITE_ROBOTICS_REQUIREMENT),
            ),
        )
    }

    // The gate and the price, so a test reads as "a colony climbing a ladder" rather than as the
    // four lines it takes to make one. The applied branch's helper next door, in the other branch.
    private fun adapting(technology: AdaptationTechnology): GameState {
        val on = freshState()
        val toLevel = TechLevel(on.research.levelOf(technology).value + 1)
        val cost = AdaptationBalance.adaptationCost(technology, toLevel)
        val ready = on.copy(
            buildings = on.buildings.withLevel(BuildingType.ROBOTICS_FACTORY, AdaptationBalance.GATE),
            resources = Resources.of(metal = cost.metal, crystal = cost.crystal, deuterium = cost.deuterium),
        )
        val started = assertIs<StartAdaptationResult.Started>(startAdaptation(ready, technology, at = EPOCH)).state
        return toggleAlert(started, WatchTarget.Ladder(technology))
    }

    // `at` is the instant the upgrades are started, which for a skipped colony is not EPOCH — the
    // whole point of the translation tests is a colony whose own clock is ahead of the wall one.
    private fun building(
        vararg buildings: BuildingType,
        at: Instant = EPOCH,
        on: GameState = freshState(),
    ): GameState {
        // Priced at each facility's *own* next level rather than at a flat level 2. The two
        // factories start at 0 and a colony given the Nanite gate starts Robotics at 10, so a flat
        // figure either overfunds or — for Robotics 11 — funds nowhere near enough.
        val total = buildings.fold(Resources.of()) { stock, building ->
            val next = BuildingLevel(on.buildings.levelOf(building).value + 1)
            val cost = PlaceholderBalance.upgradeCost(building, next)
            Resources.of(
                metal = stock.metal + cost.metal,
                crystal = stock.crystal + cost.crystal,
                deuterium = stock.deuterium + cost.deuterium,
            )
        }
        // **Subscribed as it starts**, because that is what these fixtures are for: they say "a
        // colony that is building", and since 0.6 a build nobody asked about is one nobody hears.
        // The gate itself has its own tests below rather than being asserted by accident here.
        return buildings.fold(on.copy(resources = total)) { state, building ->
            val started = assertIs<StartUpgradeResult.Started>(startUpgrade(state, building, at = at)).state
            toggleAlert(started, WatchTarget.Facility(building))
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
        val started = assertIs<StartResearchResult.Started>(startResearch(ready, technology, at = EPOCH)).state
        return toggleAlert(started, WatchTarget.Project(technology))
    }

    // A colony with one probe in flight, aimed `systemsAway` from home in whichever direction the
    // map has room for — so a fixture cannot fall off the edge of the coordinate space, and does
    // not depend on where this seed put the player.
    //
    // **Asked about by default**, for `fleetReturningAt`'s reason: the tests around it are about
    // copy, ids and the platform's ceiling rather than about the gate. The bell is lit on the state
    // *before* the verb, because that is the only way an ask reaches a probe — `startSurvey` copies
    // it onto the job it creates and nothing writes it afterwards.
    private fun surveying(systemsAway: Int, announced: Boolean = true): GameState {
        val state = wealthy().copy(announceFlights = announced)
        return assertIs<StartSurveyResult.Started>(
            startSurvey(state, awayFromHome(state, systemsAway), at = EPOCH),
        ).state
    }

    // The nearest target whose landing will, or will not, chart somewhere settleable. Found by
    // asking `futureEvents` what each candidate would report rather than by hardcoding a
    // coordinate: the prediction is the thing under test's own input, so a fixture picked this way
    // cannot disagree with it, and it survives the seed's home moving.
    private fun surveyingSomething(settleable: Boolean): GameState {
        val state = wealthy().copy(announceFlights = true)
        for (away in 1..GalaxyBalance.SYSTEMS_PER_GALAXY) {
            val started = startSurvey(state, awayFromHome(state, away), at = EPOCH)
            if (started !is StartSurveyResult.Started) continue
            val landing = futureEvents(started.state, now = EPOCH).filterIsInstance<FutureEvent.SurveyLands>().single()
            if ((landing.settleable > 0) == settleable) return started.state
        }
        error("no target within a galaxy of home charts ${if (settleable) "somewhere" else "nowhere"} settleable")
    }

    // More probes in flight than the platform will hold. Dispatched outward, so their landings are
    // ordered by distance and a test can name which end a cap is expected to keep.
    private fun swarming(probes: Int): GameState {
        // **A hull per probe, stated here rather than in the shared fixture.** Since 0.15 the pool is
        // what bounds simultaneous probes — which is the whole point of the change — so a swarm has
        // to buy its own scarcity out. Keeping it local also keeps the number honest: this helper is
        // the only thing in the file that wants ninety hulls, and a shared fixture carrying ninety
        // would quietly make every other test here a test of an implausible colony.
        var state = wealthy().let {
            it.copy(ships = it.ships + Ships.of(ShipType.SCOUT, probes), announceFlights = true)
        }
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

    // A colony with empty stores and one row watched, which is the only shape a watch is ever set
    // in: the square exists on a row the colony cannot pay for, and nowhere else.
    private fun watching(target: WatchTarget): GameState =
        toggleAlert(freshState().copy(resources = Resources.of()), target)

    // Builds landing at instants the test names, all of them subscribed. Written out rather than
    // started through `startUpgrade` for the reason `aBuildLandingAfterEveryProbe` is: what these
    // tests are about is the *gaps* between completions, and a fixture that inherited them from the
    // duration curve would be asserting a different thing every time the balance moved.
    private fun subscribedBuilds(vararg landing: Pair<BuildingType, Instant>): GameState =
        freshState().copy(
            builds = landing.associate { (building, at) ->
                building to BuildJob(
                    building = building,
                    toLevel = BuildingLevel(freshState().buildings.levelOf(building).value + 1),
                    startedAt = EPOCH,
                    completesAt = at,
                )
            },
            subscribed = landing.map { (building, _) -> WatchTarget.Facility(building) }.toSet(),
        )

    // `GameState.initial` takes a galaxy seed rather than defaulting one, so production cannot found
    // every colony in the same galaxy. Alerts do not care which map they are scheduled over.
    // **Scouts in the pool, because a probe flies a hull since 0.15.** A fixture with an empty pool
    // cannot dispatch a survey at all, so every probe-landing assertion in this file would be
    // measuring a refusal. Eight, which is more than any single test here sends.
    //
    // **Pinned to `CARRIED_FORWARD` rather than taking genesis's own settings**, and the whole of
    // this file depends on it: every test above is about the per-item gates, the five-minute chain,
    // the ids or the platform's ceiling, and a new colony now opens on `BY_CATEGORY · TOTAL`, which
    // is a different gate and a different packaging. `AlertDeliveryTest` is where the new pair is
    // tested; here the pair is what 0.17 did, so these go on measuring what they were written to
    // measure.
    private fun freshState(): GameState = GameState.initial(GalaxySeed(20_260_807))
        .copy(ships = Ships.of(ShipType.SCOUT, 8), alerts = AlertSettings.CARRIED_FORWARD)

    // A run out to one world and home again. `instant` is the landing — the only end an alert is
    // about — and the dispatch is an hour before it because `FleetRun` insists a run returns after
    // it left. The target is a parameter because it is half of what keeps two ids apart.
    //
    // **Asked about by default**, which is the same choice `subscribedBuilds` makes and for the same
    // reason: every test above it is about ids, ordering, copy or the platform's ceiling, and a
    // fixture that defaulted to silent would turn all of them into tests of the gate. The three that
    // *are* about the gate pass `announced = false` and say so.
    private fun fleetReturningAt(
        instant: Instant,
        target: GalaxyCoordinate = GalaxyCoordinate(galaxy = 2, system = 117, slot = 9),
        announced: Boolean = true,
    ): FleetRun = FleetRun(
        target = target,
        ships = Ships.of(ShipType.SKIFF, 14),
        gathering = ResourceKind.METAL,
        cargo = Resources.of(metal = 500),
        dispatchedAt = instant - 1.hours,
        returnsAt = instant,
        announced = announced,
    )

    private companion object {
        val EPOCH = Instant.fromEpochMilliseconds(0)
    }
}
