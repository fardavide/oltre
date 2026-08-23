package dev.fardavide.oltre.client.notifications.data

import dev.fardavide.oltre.client.design.text.English
import dev.fardavide.oltre.core.AlertCategory
import dev.fardavide.oltre.core.AlertDelivery
import dev.fardavide.oltre.core.AlertMode
import dev.fardavide.oltre.core.AlertSettings
import dev.fardavide.oltre.core.BuildJob
import dev.fardavide.oltre.core.BuildingLevel
import dev.fardavide.oltre.core.BuildingType
import dev.fardavide.oltre.core.FleetRun
import dev.fardavide.oltre.core.GalaxyCoordinate
import dev.fardavide.oltre.core.GalaxySeed
import dev.fardavide.oltre.core.GameState
import dev.fardavide.oltre.core.ResourceKind
import dev.fardavide.oltre.core.Resources
import dev.fardavide.oltre.core.ShipType
import dev.fardavide.oltre.core.Ships
import dev.fardavide.oltre.core.WatchTarget
import dev.fardavide.oltre.core.YardJob
import dev.fardavide.oltre.core.setAlertDelivery
import dev.fardavide.oltre.core.setAlertMode
import dev.fardavide.oltre.core.toggleAlertCategory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

// **The settings sheet, measured at the only place it has an effect.** Everything the sheet draws is
// a preference until the schedule changes, so this is the file that says what each stop actually
// does — `GameNotificationsTest` goes on measuring the pair a colony played before 0.18 keeps.
//
// The reference colony is the design's: a mine landing at 12:04, a plant at 12:10, two hulls at 12:37
// and 13:09, and a drive at 17:42, read against 11:38. Every instant here is that colony's arithmetic.
class AlertDeliveryTest {

    // ── By category: the question moves up one level ────────────────────────────────────────

    @Test
    fun `a build nobody subscribed to is announced once the kind is`() {
        // The whole of what the mode buys. Nothing is in `subscribed` — this colony has never tapped
        // a square — and the alert is booked anyway, because the question was answered one level up.
        val state = byCategory(colony())

        val ids = notificationsFor(state, now = NOW).map { it.id }

        assertTrue("build-${BuildingType.METAL_MINE.name}" in ids, "was $ids")
    }

    @Test
    fun `the same build is silent when its kind is switched off`() {
        val state = toggleAlertCategory(byCategory(colony()), AlertCategory.FACILITIES)

        val ids = notificationsFor(state, now = NOW).map { it.id }

        assertTrue(ids.none { it.startsWith("build-") }, "was $ids")
        // and the other kinds are untouched — one switch is one kind
        assertTrue(ids.any { it.startsWith("hull-") }, "was $ids")
    }

    @Test
    fun `a flight sent with the bell unlit is announced anyway once the kind is on`() {
        // **The one place the two gates visibly disagree**, and by-category is meant to win: the ask
        // on the run says *nobody wanted to hear about this flight*, and the sheet says *tell me about
        // every fleet return*. The second is the standing answer and the first was the absence of one.
        val state = byCategory(colony()).copy(runs = listOf(runReturningAt(HOME_1300, announced = false)))

        val ids = notificationsFor(state, now = NOW).map { it.id }

        assertTrue(ids.any { it.startsWith("run-") }, "was $ids")
    }

    @Test
    fun `every hull in the queue is announced rather than the order`() {
        // The one thing call 1 loses, stated as a test. A hull card's square has three states — off,
        // each hull, whole order — and one category switch cannot carry three, so by-category is
        // `EACH_HULL` one level up. The whole-order alert comes back as `One per category`.
        val state = byCategory(colony())

        val hulls = notificationsFor(state, now = NOW).filter { it.id.startsWith("hull-") }

        assertEquals(2, hulls.size, "both queued hulls announce themselves")
    }

    @Test
    fun `the price watch survives by category because it still names a row`() {
        // Section three's exception: every other category is a *kind of thing that happens*, and this
        // one is a row the player had to point at. So the switch decides whether the watch exists at
        // all, and the square stays on the row that books it.
        val state = byCategory(watching(colony()))

        val ids = notificationsFor(state, now = NOW).map { it.id }

        assertTrue(ids.any { it.startsWith("affordable-") }, "was $ids")
    }

    @Test
    fun `switching Price reached off removes the watch rather than muting it`() {
        val state = toggleAlertCategory(byCategory(watching(colony())), AlertCategory.PRICE_REACHED)

        val ids = notificationsFor(state, now = NOW).map { it.id }

        assertTrue(ids.none { it.startsWith("affordable-") }, "was $ids")
    }

    @Test
    fun `choosing per item again consults the squares it never cleared`() {
        // The panel is not rebuilt when it comes back and neither is the colony: the three per-thing
        // asks are not consulted under `BY_CATEGORY` and not emptied either, so this colony — which
        // has subscribed to nothing — goes back to silence rather than to a different answer.
        val loud = byCategory(colony())

        val quiet = setAlertMode(loud, AlertMode.PER_ITEM)

        assertTrue(notificationsFor(loud, now = NOW).isNotEmpty())
        assertEquals(emptyList(), notificationsFor(quiet, now = NOW).map { it.id })
    }

    // ── One per category ────────────────────────────────────────────────────────────────────

    @Test
    fun `two facilities hours apart are one alert at the second one`() {
        // The five-minute chain would leave these as two — they are six minutes apart — so this is the
        // stop doing something the chain cannot.
        val state = perCategory(byCategory(colony()))

        val facilities = notificationsFor(state, now = NOW).filter { English.resolve(it.title).contains("facilities") }

        val alert = facilities.single()
        assertEquals(PLANT_1210, alert.at, "a kind is not done until the last of it is")
        assertEquals("2 facilities are done", English.resolve(alert.title))
    }

    @Test
    fun `the body names the kinds rather than repeating a count`() {
        val state = perCategory(byCategory(colony()))

        val alert = notificationsFor(state, now = NOW).single { English.resolve(it.title).contains("facilities") }

        assertEquals("Metal Mine and Solar Plant", English.resolve(alert.body))
    }

    @Test
    fun `three identical hulls are not listed three times`() {
        // Three facilities are three different rows and read as a list; three skiffs are three
        // identical objects, and "Skiff, Skiff and Skiff" is a sentence nobody should be shown. The
        // count is already in the title, so the body's job is which kinds.
        val state = perCategory(byCategory(colony()))

        val alert = notificationsFor(state, now = NOW).single { English.resolve(it.title).contains("hulls") }

        assertEquals("2 hulls have left the yard", English.resolve(alert.title))
        assertEquals("Skiff", English.resolve(alert.body))
    }

    @Test
    fun `one thing of a kind is that thing's own alert`() {
        // A group of one is simply the thing itself, which is the rule the five-minute chain and the
        // hull order are both written to — a count is only worth saying when there is more than one.
        val state = perCategory(byCategory(colony().copy(builds = mapOf(mine()))))

        val alert = notificationsFor(state, now = NOW).single { it.id.startsWith("build-") }

        assertEquals("Metal Mine reached level 14", English.resolve(alert.title))
    }

    @Test
    fun `price reached fires alone at its own instant`() {
        // **The one category that never groups**, and it is the design's call rather than a
        // convenience: every other category announces something that has happened and stays true, and
        // this announces a window that closes the moment the resources go on something else.
        //
        // Asserted against the mine rather than against a stamped instant, because the watch is the
        // one prediction `futureEvents` *computes* — projected from stocks and rates — so pinning it
        // would be pinning the balance curve in a file about packaging.
        val state = perCategory(byCategory(watching(colony())))

        val price = notificationsFor(state, now = NOW).single { it.id.startsWith("affordable-") }

        assertTrue(price.at < MINE_1204, "held back, the news would already be wrong: was ${price.at}")
    }

    @Test
    fun `each kind keeps its own alert`() {
        val state = perCategory(byCategory(colony()))

        val alerts = notificationsFor(state, now = NOW)

        assertEquals(2, alerts.size, "facilities and hulls, one each: was ${alerts.map { it.id }}")
        assertEquals(alerts.map { it.at }.sorted(), alerts.map { it.at }, "earliest first")
    }

    // ── One in total ────────────────────────────────────────────────────────────────────────

    @Test
    fun `every alert shares one tray entry`() {
        // Davide, 2026-08-23, and the whole of what the stop means: a colony holds one notification,
        // and each landing brings it up to date rather than adding a second.
        val state = total(byCategory(colony()))

        val alerts = notificationsFor(state, now = NOW)

        assertEquals(1, alerts.map { it.collapseId }.distinct().size, "one tray entry")
        assertEquals(alerts.size, alerts.map { it.id }.distinct().size, "and one booking per instant")
    }

    @Test
    fun `it fires at the soonest instant rather than waiting for the last`() {
        // **Section six's five hours and thirty-eight minutes, and this is where they do not happen.**
        // The design drew `One in total` as one alert held until the last thing lands, measured it on
        // this very colony, and said so; Davide's answer replaced the rule rather than the copy.
        val state = total(byCategory(colony()))

        val first = notificationsFor(state, now = NOW).first()

        assertEquals(MINE_1204, first.at)
    }

    @Test
    fun `the first alert names the one thing that has happened`() {
        val state = total(byCategory(colony()))

        val first = notificationsFor(state, now = NOW).first()

        assertEquals("Metal Mine reached level 14", English.resolve(first.title))
    }

    @Test
    fun `the next one counts everything since instead of announcing itself`() {
        // *"Metal Mine upgraded at 12:04, and notification shows only that, then one ship is ready at
        // 12:37, and the notification updates to show Metal Mine + 1 ship."*
        val state = total(byCategory(colony()))

        val atHull = notificationsFor(state, now = NOW).single { it.at == HULL_1237 }

        assertEquals("2 facilities · 1 hull", English.resolve(atHull.title))
        assertEquals("Metal Mine and Solar Plant · Skiff", English.resolve(atHull.body))
    }

    @Test
    fun `one kind is still the kind's own sentence`() {
        // Two facilities and nothing else yet: counts and nouns would read "2 facilities" and say less
        // than the sentence the category already has.
        val state = total(byCategory(colony()))

        val atPlant = notificationsFor(state, now = NOW).single { it.at == PLANT_1210 }

        assertEquals("2 facilities are done", English.resolve(atPlant.title))
    }

    @Test
    fun `several landing on one instant are one notification rather than two`() {
        // Two bookings on the same millisecond under one tray entry would replace each other in the
        // same frame, and the player would see whichever won.
        val state = total(byCategory(colony().copy(builds = mapOf(mine(at = PLANT_1210), plant()))))

        val alerts = notificationsFor(state, now = NOW)

        assertEquals(1, alerts.count { it.at == PLANT_1210 })
    }

    @Test
    fun `price folds into the running total rather than standing apart`() {
        // **Davide's overrule of the design's *never grouped*, 2026-08-23**: *"Price reached also
        // update the same single notification."* It is defensible here and nowhere else — under this
        // stop the news is already on the lock screen, so there is nothing to be late for.
        //
        // The price lands before anything else this colony is doing, so it is the first booking and
        // the only one that is a sentence about itself.
        val state = total(byCategory(watching(colony())))

        val price = notificationsFor(state, now = NOW).first()

        assertEquals("total", price.collapseId)
        assertEquals("You can afford Metal Mine", English.resolve(price.title))
    }

    @Test
    fun `the title carries two kinds and counts the rest`() {
        // The design's compaction rule, with a count of kinds standing in for its character budget —
        // see `TITLE_CATEGORIES`. Four kinds have landed by the drive: facilities, hulls, a fleet and
        // a price.
        val state = total(byCategory(watching(colony()).copy(runs = listOf(runReturningAt(HOME_1300)))))

        val last = notificationsFor(state, now = NOW).last()

        assertEquals("2 facilities · 2 hulls · +2", English.resolve(last.title))
    }

    @Test
    fun `a colony with nothing in flight books nothing at all`() {
        val state = total(byCategory(freshState()))

        assertEquals(emptyList(), notificationsFor(state, now = NOW))
    }

    // ── Helpers ─────────────────────────────────────────────────────────────────────────────

    private fun byCategory(state: GameState): GameState = setAlertMode(state, AlertMode.BY_CATEGORY)

    private fun perCategory(state: GameState): GameState =
        setAlertDelivery(state, AlertDelivery.PER_CATEGORY)

    private fun total(state: GameState): GameState = setAlertDelivery(state, AlertDelivery.TOTAL)

    // The design's reference colony, minus the drive — an adaptation ladder needs a research record
    // this fixture has no reason to build, and what the tests above are about is the packaging rather
    // than which kinds exist.
    private fun colony(): GameState = freshState().copy(
        builds = mapOf(mine(), plant()),
        yard = listOf(hullAt(HULL_1237), hullAt(HULL_1309, from = HULL_1237)),
    )

    private fun mine(at: Instant = MINE_1204): Pair<BuildingType, BuildJob> = BuildingType.METAL_MINE to
        BuildJob(building = BuildingType.METAL_MINE, toLevel = BuildingLevel(14), startedAt = NOW, completesAt = at)

    private fun plant(): Pair<BuildingType, BuildJob> = BuildingType.SOLAR_PLANT to
        BuildJob(
            building = BuildingType.SOLAR_PLANT,
            toLevel = BuildingLevel(12),
            startedAt = NOW,
            completesAt = PLANT_1210,
        )

    private fun hullAt(at: Instant, from: Instant = NOW): YardJob =
        YardJob(ship = ShipType.SKIFF, startedAt = from, completesAt = at)

    // A row whose price the stores have not reached, and the stocks that make the projection land at
    // a known instant. The watch is the one prediction `futureEvents` computes rather than reads.
    private fun watching(state: GameState): GameState =
        state.copy(watching = WatchTarget.Facility(BuildingType.METAL_MINE), resources = Resources.of())

    private fun runReturningAt(at: Instant, announced: Boolean = true): FleetRun = FleetRun(
        target = GalaxyCoordinate(galaxy = 2, system = 117, slot = 9),
        ships = Ships.of(ShipType.SKIFF, 1),
        gathering = ResourceKind.METAL,
        cargo = Resources.of(metal = 500),
        dispatchedAt = at - 1.hours,
        returnsAt = at,
        announced = announced,
    )

    // **Opened on `CARRIED_FORWARD` and moved one setting at a time**, rather than on the pair a new
    // colony gets. The two settings are independent and the tests above are each about one of them:
    // starting from `BY_CATEGORY · TOTAL` would make every mode test read its answer off ids that the
    // *delivery* had already collapsed, which is how the first cut of this file passed while measuring
    // nothing.
    private fun freshState(): GameState = GameState.initial(GalaxySeed(20_260_807))
        .copy(alerts = AlertSettings.CARRIED_FORWARD)

    private companion object {
        // 11:38, the instant the design's colony is read at. Every other instant below is that
        // colony's own arithmetic, offset from this one.
        val NOW: Instant = Instant.parse("2026-08-23T11:38:00Z")
        val MINE_1204: Instant = NOW + 26.minutes
        val PLANT_1210: Instant = NOW + 32.minutes
        val HULL_1237: Instant = NOW + 59.minutes
        val HOME_1300: Instant = NOW + 82.minutes
        val HULL_1309: Instant = NOW + 91.minutes
    }
}
