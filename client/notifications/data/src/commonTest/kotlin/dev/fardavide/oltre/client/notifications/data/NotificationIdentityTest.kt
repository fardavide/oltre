package dev.fardavide.oltre.client.notifications.data

import dev.fardavide.oltre.core.AlertDelivery
import dev.fardavide.oltre.core.AlertMode
import dev.fardavide.oltre.core.AlertSettings
import dev.fardavide.oltre.core.setAlertDelivery
import dev.fardavide.oltre.core.setAlertMode
import dev.fardavide.oltre.core.BuildingLevel
import dev.fardavide.oltre.core.BuildingType
import dev.fardavide.oltre.core.FleetRun
import dev.fardavide.oltre.core.GalaxyBalance
import dev.fardavide.oltre.core.GalaxyCoordinate
import dev.fardavide.oltre.core.GalaxySeed
import dev.fardavide.oltre.core.GameState
import dev.fardavide.oltre.core.HullAlert
import dev.fardavide.oltre.core.ResourceKind
import dev.fardavide.oltre.core.YardJob
import dev.fardavide.oltre.core.Resources
import dev.fardavide.oltre.core.ShipType
import dev.fardavide.oltre.core.Ships
import dev.fardavide.oltre.core.StartResearchResult
import dev.fardavide.oltre.core.StartSurveyResult
import dev.fardavide.oltre.core.StartUpgradeResult
import dev.fardavide.oltre.core.SystemAddress
import dev.fardavide.oltre.core.Technology
import dev.fardavide.oltre.core.WatchTarget
import dev.fardavide.oltre.core.startResearch
import dev.fardavide.oltre.core.startSurvey
import dev.fardavide.oltre.core.toggleAlert
import dev.fardavide.oltre.core.startUpgrade
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Instant

// **Two alerts that are about different things must never be one alert.** When they collide there is
// no crash and no wrong number — one of them simply never arrives, in a game whose entire way of
// telling you anything is an alert booked in advance.
//
// This is the one defect class in this module with a shipping record. `"fleet-arrival"` was a
// constant id until 0.3.0: unique by construction while a colony could hold one returning fleet, and
// silently lossy the moment runs became parallel and uncapped. **It lived in `GameNotifications`,
// which was and is at 100% line coverage.** Coverage asks whether a line ran, and that line ran in
// every test that touched a fleet — so the number that is supposed to mean "this code is looked
// after" said 100% for as long as the bug existed. Nothing automated caught it; a person thinking
// about parallel runs did.
//
// So these are the assertions the percentage cannot make. `GameNotificationsTest` covers each kind
// of alert by example, including the two-fleets-together case that regression produced; what is
// here is the general form — **every kind at once, and the pairs an example would not think to
// pick** — plus the one reduction that happens after this module hands the set over.
//
// No comma appears in a test name: this is a `commonTest` in a module with an iOS target, and
// Kotlin/Native rejects one where the JVM accepts it. See `.claude/rules/session-roles.md`.
class NotificationIdentityTest {

    @Test
    fun `every alert a crowded colony books has an id of its own`() {
        val state = crowdedColony()
        val ids = notificationsFor(state, now = EPOCH).map { it.id }

        assertEquals(ids.size, ids.toSet().size, "colliding ids among ${ids.size}: ${duplicatesIn(ids)}")
    }

    @Test
    fun `and under every pair of settings the sheet can be left in`() {
        // **This file's own lesson, applied to the thing that widened it.** 0.18 turned one packaging
        // rule into six, and the test above went on asking about one of them — the pair a colony
        // played before the sheet existed carries. That is the same shape as the `"fleet-arrival"`
        // constant: unique by construction under the state space the suite happened to walk, and
        // lossy in the one it did not.
        //
        // It found a real defect on the first run. Under `BY_CATEGORY · EACH` a hull card still
        // holding `WHEN_ALL_DONE` from before the mode was switched collapsed every queued hull onto
        // one `order-<type>` id — in the mode whose whole promise is that the card is not consulted.
        //
        // A loop rather than six tests, because what is being asserted is the same sentence six times
        // and the sixth is the one nobody would have written by hand.
        for (mode in AlertMode.entries) {
            for (delivery in AlertDelivery.entries) {
                val state = setAlertDelivery(setAlertMode(crowdedColony(), mode), delivery)
                val ids = notificationsFor(state, now = EPOCH).map { it.id }

                assertEquals(
                    ids.size,
                    ids.toSet().size,
                    "colliding ids under $mode / $delivery among ${ids.size}: ${duplicatesIn(ids)}",
                )
            }
        }
    }

    @Test
    fun `and every pair keeps one tray entry per booking except the one that must not`() {
        // The other half of identity since 0.18, and it is a *pair* of promises rather than one:
        // `TOTAL` is the only stop where several bookings share a tray entry, and it is the only stop
        // where they must. A regression in either direction is silent — an extra tray entry means the
        // player gets a second notification where they were promised one, and a shared entry anywhere
        // else means one alert quietly replacing another.
        for (mode in AlertMode.entries) {
            for (delivery in AlertDelivery.entries) {
                val state = setAlertDelivery(setAlertMode(crowdedColony(), mode), delivery)
                val booked = notificationsFor(state, now = EPOCH)
                val trays = booked.map { it.collapseId }.distinct().size

                if (delivery == AlertDelivery.TOTAL) {
                    assertEquals(1, trays, "$mode / $delivery should hold one tray entry")
                } else {
                    assertEquals(booked.size, trays, "$mode / $delivery should be its own tray entry each")
                }
            }
        }
    }

    @Test
    fun `a crowded colony is actually crowded`() {
        // The guard on the test above rather than a test of the app. A distinctness assertion over a
        // set of one passes forever and proves nothing, so the fixture has to be held to being a
        // real mix — several of each unbounded kind. If a balance change or a requirement ever makes
        // `crowdedColony` degrade to a handful, this fails here instead of quietly hollowing out
        // everything below.
        //
        // **The completions arrive as one `group-` and that is the fixture working, not failing.**
        // Since 0.5.0 anything subscribed landing inside five minutes of the one before it is one
        // alert, and a colony that started every facility it could afford at one instant is exactly
        // the case that collapses — so a crowded colony has *fewer* ids than it used to, which is the
        // property the collapse exists for. The singleton `build-`, `research-` and `adaptation-`
        // spaces are exercised by `GameNotificationsTest`, where the completions are far apart.
        val notifications = notificationsFor(crowdedColony(), now = EPOCH)
        val kinds = notifications.map { it.id.substringBefore('-') }.toSet()

        // **`order` joined the set at 0.18**, and it is the same story as the scout one version
        // earlier: the yard was the one crowding kind this fixture did not hold, so `ShipsComplete`
        // — the only id in the game derived from an *instant*, next to an order id derived from a
        // *type* — was absent from the file whose whole subject is ids meeting each other. It arrives
        // as `order-` rather than `hull-` because the card is left on `WHEN_ALL_DONE`, which is what
        // a player who tapped once carries into whatever the settings sheet is set to next.
        assertEquals(setOf("group", "survey", "order", "run"), kinds, "not every kind is represented: $kinds")
        assertTrue(notifications.size >= 20, "only ${notifications.size} alerts — too few to prove anything")
    }

    @Test
    fun `two runs to the same world tell themselves apart by when they left`() {
        // The case an example test does not reach for. `GameNotificationsTest` puts two fleets in the
        // air to *different* targets, which any id carrying a coordinate survives; sending both to
        // the same rock is what makes the dispatch instant load-bearing. A player can do this — the
        // fleet is uncapped and nothing stops two runs at one world.
        val target = GalaxyCoordinate(galaxy = 3, system = 42, slot = 7)
        val state = freshState().copy(
            runs = listOf(
                run(target = target, dispatchedAt = EPOCH, returnsAt = EPOCH + 3.hours),
                run(target = target, dispatchedAt = EPOCH + 1.hours, returnsAt = EPOCH + 5.hours),
            ),
        )

        val ids = notificationsFor(state, now = EPOCH).map { it.id }

        assertEquals(2, ids.size)
        assertEquals(2, ids.toSet().size, "two runs to one world collapsed into one alert: $ids")
    }

    @Test
    fun `two runs that left together on different windows are still two alerts`() {
        // The tightest pair the id has to separate: one world, one instant, two rungs. This shipped
        // as a **collision** — the id was `run-<galaxy>-<system>-<slot>-<dispatchedAt>` and nothing
        // in it moved when the window did, so the 7h landing replaced the 6h one and the first fleet
        // came home to silence.
        //
        // It was not reachable from a finger, which is the whole reason it is worth a test rather
        // than a shrug: nothing calls `startRun` yet, and every dispatch made inside one action
        // carries one instant — so a sheet offering a split manifest, a repeat, or a send-all makes
        // it live on the day it ships. That is precisely how `"fleet-arrival"` waited for parallel
        // runs to arrive.
        val target = GalaxyCoordinate(galaxy = 3, system = 42, slot = 7)
        val together = EPOCH + 2.hours
        val state = freshState().copy(
            runs = listOf(
                run(target = target, dispatchedAt = together, returnsAt = EPOCH + 6.hours),
                run(target = target, dispatchedAt = together, returnsAt = EPOCH + 7.hours),
            ),
        )

        val ids = notificationsFor(state, now = EPOCH).map { it.id }

        assertEquals(2, ids.size, "core stopped predicting both runs")
        assertEquals(2, ids.toSet().size, "two windows collapsed into one alert: $ids")
    }

    @Test
    fun `two runs alike in every way a player could see are one alert on purpose`() {
        // The residual, asserted as intended rather than left unsaid. Two runs to one world that left
        // together on the same rung differ only in their manifest — and the manifest reaches no
        // notification: both fire at `dispatchedAt + window`, both are titled "Your ships are home",
        // and both bodies name `target.label()` and nothing else. Two identical sentences at one
        // instant is not information the player is missing, it is the same alert twice.
        //
        // So the key deliberately stops at the window. Putting `ships` or `gathering` in it would
        // split an id that nothing downstream can tell apart, and buy a duplicate notification.
        val target = GalaxyCoordinate(galaxy = 3, system = 42, slot = 7)
        val together = EPOCH + 2.hours
        val landing = EPOCH + 6.hours
        val state = freshState().copy(
            runs = listOf(
                run(target = target, dispatchedAt = together, returnsAt = landing, ships = 4),
                run(target = target, dispatchedAt = together, returnsAt = landing, ships = 9),
            ),
        )

        val notifications = notificationsFor(state, now = EPOCH)

        assertEquals(1, notifications.map { it.id }.toSet().size)
        // And the merge is only correct because the two say the same thing. If either half of this
        // ever stops holding, the manifest has to go into the id after all.
        assertEquals(1, notifications.map { it.title to it.body }.toSet().size, "merged two different alerts")
        assertEquals(1, notifications.map { it.at }.toSet().size, "merged two different instants")
    }

    @Test
    fun `a millisecond of daylight between two dispatches is enough`() {
        // The other side of the limit above: the id's resolution is the millisecond, so one is all it
        // takes. Worth pinning because the obvious "fix" for the case above — dropping `dispatchedAt`
        // to seconds to tidy the string — would silently widen the hole from a millisecond to a
        // thousand of them.
        val target = GalaxyCoordinate(galaxy = 3, system = 42, slot = 7)
        val state = freshState().copy(
            runs = listOf(
                run(target = target, dispatchedAt = EPOCH, returnsAt = EPOCH + 6.hours),
                run(target = target, dispatchedAt = EPOCH + 1.milliseconds, returnsAt = EPOCH + 7.hours),
            ),
        )

        val ids = notificationsFor(state, now = EPOCH).map { it.id }

        assertEquals(2, ids.toSet().size, "a millisecond apart was not enough: $ids")
    }

    @Test
    fun `no two alerts collide once Android reduces an id to a hash`() {
        // **This one guards a file in another source set that no test can reach.**
        // `NotificationReceiver.onReceive` posts with `manager.notify(id.hashCode(), …)`, so on
        // Android the id space is not the string at all — it is a 32-bit hash of it. Two alerts whose
        // ids differ but whose hashes agree overwrite each other on the status bar, which is the same
        // silent loss the string ids are carefully built to avoid, one layer further down.
        //
        // The receiver is `androidMain` and is excluded from the coverage report, so nothing there
        // can be measured or executed here. What *can* be checked is the input it is given: the ids
        // this module actually produces, reduced the way Android will reduce them.
        val ids = notificationsFor(crowdedColony(), now = EPOCH).map { it.id }
        val slots = ids.map { it.hashCode() }

        assertEquals(slots.size, slots.toSet().size, "two ids share an Android slot: ${collidingHashes(ids)}")
    }

    @Test
    fun `the same colony books the same ids twice running`() {
        // Idempotence, which every id comment in `GameNotifications` claims and none of them checks.
        // It is what makes `replaceAll` safe: the set is rebuilt from state on every transition, so
        // an id that wobbled would cancel a pending alarm and book a fresh one on every tick.
        val state = crowdedColony()

        assertEquals(
            notificationsFor(state, now = EPOCH).map { it.id },
            notificationsFor(state, now = EPOCH).map { it.id },
        )
    }
}

private val EPOCH = Instant.fromEpochMilliseconds(0)

// **`CARRIED_FORWARD` rather than genesis's own settings**, for `GameNotificationsTest`'s reason:
// this file is about ids being distinct and stable, and one alert per thing is the only packaging
// that produces one id per thing to compare. `AlertDeliveryTest` covers the pair a new colony opens
// on, ids included.
private fun freshState(): GameState =
    GameState.initial(GalaxySeed(20_260_807)).copy(alerts = AlertSettings.CARRIED_FORWARD)

// Rich enough to start everything at once, and with the lab's gate already met. **The gate has to be
// a level rather than a job**: `startUpgrade` on the robotics factory books a build, and the level it
// grants does not exist until that build completes — so a colony that merely *started* the factory
// still cannot research, and the research alert vanishes from the fixture. That is not a guess; it is
// what `a crowded colony is actually crowded` caught the first time this file ran.
private fun wealthy(): GameState = freshState().copy(
    resources = Resources.of(metal = 5_000_000, crystal = 500_000, deuterium = 500_000),
    buildings = freshState().buildings.withLevel(BuildingType.ROBOTICS_FACTORY, BuildingLevel(1)),
    // **And scouts, since 0.15**, for the same reason the gate above is a level rather than a job:
    // a probe flies a hull now, so a colony with only money raises no survey alert at all and the
    // `survey` kind quietly leaves the fixture. That is the exact failure mode the comment above
    // records — `a crowded colony is actually crowded` caught it a second time, one version later.
    ships = Ships.of(ShipType.SCOUT, 8),
)

// Every kind of alert the game can raise, in flight at once, with several of each unbounded kind.
//
// Built by *asking* rather than by asserting: each action is attempted and kept only if core allows
// it, because requirements and costs move with the balance and a fixture that insists on a
// particular facility being startable is a fixture that breaks on an unrelated PR. What the test
// does insist on is the shape of the result, and that is `a crowded colony is actually crowded`.
private fun crowdedColony(): GameState {
    // The bell is lit before anything is sent, for the reason the builds are subscribed as they
    // start: since 0.15.4 a flight nobody asked about books nothing, so a fixture that dispatched
    // twelve probes with the bell dark would be crowded with nothing. It has to be set *first*,
    // because `startSurvey` copies it onto each job rather than reading it later.
    var state = wealthy().copy(announceFlights = true)

    // Facilities run one job each and in parallel, so every building the colony can afford adds one.
    // **Subscribed as they start**, since 0.5.0: a completion nobody asked about books nothing, and a
    // fixture that started six builds and booked none of them would be crowded with nothing.
    for (building in BuildingType.entries) {
        (startUpgrade(state, building, at = EPOCH) as? StartUpgradeResult.Started)?.let {
            state = toggleAlert(it.state, WatchTarget.Facility(building))
        }
    }

    // One empire-wide slot per branch, so at most one of these lands however many are attempted.
    // The adaptation branch's own slot is left empty here: what this fixture is about is ids
    // crowding each other, and a ladder would add one more bounded id to a set already at its
    // ceiling rather than test anything the applied branch does not.
    for (technology in Technology.entries) {
        (startResearch(state, technology, at = EPOCH) as? StartResearchResult.Started)?.let {
            state = toggleAlert(it.state, WatchTarget.Project(technology))
        }
    }

    // Probes are uncapped and parallel — the first of the two kinds that can crowd the set.
    var away = 1
    while (state.surveys.size < 12 && away <= GalaxyBalance.SYSTEMS_PER_GALAXY) {
        (startSurvey(state, awayFromHome(state, away), at = EPOCH) as? StartSurveyResult.Started)
            ?.let { state = it.state }
        away++
    }

    // The second, and the one with the history. Written straight into state rather than dispatched,
    // so the fixture states what it is about — twelve runs whose ids have to stay apart — instead of
    // inheriting a travel-time curve. Deliberately overlapping: two pairs share a target, two share
    // a dispatch instant, and one pair shares neither.
    val runs = (0 until 12).map { index ->
        run(
            target = GalaxyCoordinate(galaxy = 1 + index % 3, system = 20 + index % 4, slot = 1 + index % 5),
            dispatchedAt = EPOCH + (index % 6).hours,
            returnsAt = EPOCH + (30 + index).hours,
        )
    }

    // **The third crowding kind, and it was missing from a fixture whose whole claim is every kind at
    // once.** A hull is the only alert whose id is derived from a *time* rather than from a subject —
    // `hull-<instant>` — and an *order* is the only one derived from a type, so a queue is where two
    // ids can meet from two directions. Written straight into state for the runs' reason: what this
    // states is three hulls whose ids have to stay apart, not a yard curve.
    //
    // **The card is left on `WHEN_ALL_DONE`, which is the state that found the 0.18 defect.** It is
    // also the ordinary one: a player taps a hull card once and the answer stands until they tap it
    // again, so it is what a colony carries into whatever the settings sheet is switched to next.
    val yard = (0 until 3).map { nth ->
        YardJob(ship = ShipType.SKIFF, startedAt = EPOCH + nth.hours, completesAt = EPOCH + (nth + 1).hours)
    }
    return state.copy(
        runs = runs,
        yard = yard,
        hullAlerts = mapOf(ShipType.SKIFF to HullAlert.WHEN_ALL_DONE),
    )
}

private fun run(
    target: GalaxyCoordinate,
    dispatchedAt: Instant,
    returnsAt: Instant,
    ships: Int = 4,
): FleetRun = FleetRun(
    target = target,
    ships = Ships.of(ShipType.SKIFF, ships),
    gathering = ResourceKind.METAL,
    cargo = Resources.of(metal = 100),
    dispatchedAt = dispatchedAt,
    returnsAt = returnsAt,
    // Written straight into state rather than dispatched, so the ask is written with it — the twelve
    // runs are here to crowd the id space, and a silent one is a run that never reaches it.
    announced = true,
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

private fun duplicatesIn(ids: List<String>): List<String> =
    ids.groupBy { it }.filterValues { it.size > 1 }.keys.toList()

private fun collidingHashes(ids: List<String>): List<List<String>> =
    ids.groupBy { it.hashCode() }.filterValues { it.size > 1 }.values.toList()
