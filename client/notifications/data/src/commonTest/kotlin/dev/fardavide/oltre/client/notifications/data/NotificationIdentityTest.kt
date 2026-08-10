package dev.fardavide.oltre.client.notifications.data

import dev.fardavide.oltre.core.BuildingLevel
import dev.fardavide.oltre.core.BuildingType
import dev.fardavide.oltre.core.FleetRun
import dev.fardavide.oltre.core.GalaxyBalance
import dev.fardavide.oltre.core.GalaxyCoordinate
import dev.fardavide.oltre.core.GalaxySeed
import dev.fardavide.oltre.core.GameState
import dev.fardavide.oltre.core.ResourceKind
import dev.fardavide.oltre.core.Resources
import dev.fardavide.oltre.core.ShipType
import dev.fardavide.oltre.core.Ships
import dev.fardavide.oltre.core.StartResearchResult
import dev.fardavide.oltre.core.StartSurveyResult
import dev.fardavide.oltre.core.StartUpgradeResult
import dev.fardavide.oltre.core.SystemAddress
import dev.fardavide.oltre.core.Technology
import dev.fardavide.oltre.core.startResearch
import dev.fardavide.oltre.core.startSurvey
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
    fun `a crowded colony is actually crowded`() {
        // The guard on the test above rather than a test of the app. A distinctness assertion over a
        // set of one passes forever and proves nothing, so the fixture has to be held to being a
        // real mix — every kind of alert the game can raise, several of the two unbounded ones. If a
        // balance change or a requirement ever makes `crowdedColony` degrade to a handful, this
        // fails here instead of quietly hollowing out everything below.
        val notifications = notificationsFor(crowdedColony(), now = EPOCH)
        val kinds = notifications.map { it.id.substringBefore('-') }.toSet()

        assertEquals(setOf("build", "research", "survey", "run"), kinds, "not every kind is represented")
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

private fun freshState(): GameState = GameState.initial(GalaxySeed(20_260_807))

// Rich enough to start everything at once, and with the lab's gate already met. **The gate has to be
// a level rather than a job**: `startUpgrade` on the robotics factory books a build, and the level it
// grants does not exist until that build completes — so a colony that merely *started* the factory
// still cannot research, and the research alert vanishes from the fixture. That is not a guess; it is
// what `a crowded colony is actually crowded` caught the first time this file ran.
private fun wealthy(): GameState = freshState().copy(
    resources = Resources.of(metal = 5_000_000, crystal = 500_000, deuterium = 500_000),
    buildings = freshState().buildings.withLevel(BuildingType.ROBOTICS_FACTORY, BuildingLevel(1)),
)

// Every kind of alert the game can raise, in flight at once, with several of each unbounded kind.
//
// Built by *asking* rather than by asserting: each action is attempted and kept only if core allows
// it, because requirements and costs move with the balance and a fixture that insists on a
// particular facility being startable is a fixture that breaks on an unrelated PR. What the test
// does insist on is the shape of the result, and that is `a crowded colony is actually crowded`.
private fun crowdedColony(): GameState {
    var state = wealthy()

    // Facilities run one job each and in parallel, so every building the colony can afford adds one.
    for (building in BuildingType.entries) {
        (startUpgrade(state, building, at = EPOCH) as? StartUpgradeResult.Started)?.let { state = it.state }
    }

    // One empire-wide slot, so at most one of these lands however many are attempted.
    for (technology in Technology.entries) {
        (startResearch(state, technology, at = EPOCH) as? StartResearchResult.Started)?.let { state = it.state }
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
    return state.copy(runs = runs)
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
