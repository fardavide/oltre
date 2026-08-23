package dev.fardavide.oltre.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue
import kotlin.time.Instant

// **The fourth ask, and the first one that is about the other three.** `subscribed`, `hullAlerts` and
// `announceFlights` all point at something — a job, a hull type, the next flight. This points at
// nothing at all: it is where the question is asked, and how many notifications the answer arrives in.
//
// Two settings and therefore two halves. `mode` moves the question up one level, from the job to the
// kind of job; `delivery` says how many buzzes the answers are worth. They are independent — a colony
// can ask per item and still want one notification — which is why they are two fields rather than one
// enum with six members.
//
// The design is *Ask Once*, accepted 2026-08-23.
class AlertSettingsTest {

    @Test
    fun `a new colony hears about every kind of thing in one notification`() {
        // Davide's call, 2026-08-23, and it is a change of default rather than a new option: a colony
        // opened before this shipped hears nothing it did not ask for, and a colony opened after it
        // hears about everything, once. The two halves pay for each other — all seven categories on
        // would be the loudest the app can be under any other delivery, and under this one it is a
        // single notification that keeps being brought up to date.
        val alerts = GameState.initial().alerts

        assertEquals(AlertMode.BY_CATEGORY, alerts.mode)
        assertEquals(AlertCategory.entries.toSet(), alerts.categories)
        assertEquals(AlertDelivery.TOTAL, alerts.delivery)
    }

    @Test
    fun `the mode moves and takes nothing else with it`() {
        val state = GameState.initial()

        val perItem = setAlertMode(state, AlertMode.PER_ITEM)

        assertEquals(AlertMode.PER_ITEM, perItem.alerts.mode)
        assertEquals(state.alerts.categories, perItem.alerts.categories)
        assertEquals(state.alerts.delivery, perItem.alerts.delivery)
    }

    @Test
    fun `the seven switches remember their positions across a trip through per item`() {
        // **The panel is not rebuilt when it comes back.** Choosing Per item does not clear the seven
        // — it stops consulting them — so a player who turns two off to look at the other mode finds
        // them off when they return. The sheet draws no panel at all in Per item, which is what makes
        // this invisible rather than confusing.
        val quiet = toggleAlertCategory(GameState.initial(), AlertCategory.PROBES)

        val andBack = setAlertMode(setAlertMode(quiet, AlertMode.PER_ITEM), AlertMode.BY_CATEGORY)

        assertEquals(quiet.alerts.categories, andBack.alerts.categories)
        assertFalse(AlertCategory.PROBES in andBack.alerts.categories)
    }

    @Test
    fun `a switch is its own undo`() {
        val state = GameState.initial()

        val off = toggleAlertCategory(state, AlertCategory.HULLS)
        val on = toggleAlertCategory(off, AlertCategory.HULLS)

        assertFalse(AlertCategory.HULLS in off.alerts.categories)
        assertTrue(AlertCategory.HULLS in on.alerts.categories)
        assertEquals(state.alerts, on.alerts)
    }

    @Test
    fun `turning one switch off leaves the other six alone`() {
        val state = GameState.initial()

        val off = toggleAlertCategory(state, AlertCategory.RESEARCH)

        assertEquals(
            AlertCategory.entries.toSet() - AlertCategory.RESEARCH,
            off.alerts.categories,
        )
    }

    @Test
    fun `every switch can be turned off and silence is a position the sheet allows`() {
        // Not a state to guard against. A player who wants nothing is entitled to nothing, and the
        // alternative — refusing the seventh tap — is a control that stops working for a reason
        // nothing on screen could explain.
        val silent = AlertCategory.entries.fold(GameState.initial(), ::toggleAlertCategory)

        assertEquals(emptySet(), silent.alerts.categories)
    }

    @Test
    fun `the delivery moves and takes nothing else with it`() {
        val state = GameState.initial()

        val each = setAlertDelivery(state, AlertDelivery.EACH)

        assertEquals(AlertDelivery.EACH, each.alerts.delivery)
        assertEquals(state.alerts.mode, each.alerts.mode)
        assertEquals(state.alerts.categories, each.alerts.categories)
    }

    @Test
    fun `none of the three verbs touches what the colony holds`() {
        val state = GameState.initial()

        val moved = setAlertDelivery(
            toggleAlertCategory(setAlertMode(state, AlertMode.PER_ITEM), AlertCategory.HULLS),
            AlertDelivery.PER_CATEGORY,
        )

        assertEquals(state.resources, moved.resources)
        assertEquals(state.buildings, moved.buildings)
        assertEquals(state.eventLog, moved.eventLog)
        assertEquals(state.experience, moved.experience)
    }

    @Test
    fun `nothing about the sheet is written to the log`() {
        // A preference is not something that happened to the colony. It earns no experience and it is
        // not a fact about the empire that a replay would have to reproduce — the same reasoning
        // `cycleHullAlert` and `toggleFlightAlerts` are built on.
        val state = GameState.initial()

        assertEquals(state.eventLog, setAlertMode(state, AlertMode.PER_ITEM).eventLog)
        assertEquals(state.eventLog, toggleAlertCategory(state, AlertCategory.HULLS).eventLog)
        assertEquals(state.eventLog, setAlertDelivery(state, AlertDelivery.EACH).eventLog)
    }

    @Test
    fun `two colonies alike but for the sheet are two different colonies`() {
        // The property `advance`'s own tests rest on: they compare whole `GameState`s with
        // `assertEquals`, so a field the generated `equals` ignored would let a loud colony and a
        // silent one compare equal and every span test would stop being able to see it.
        val state = GameState.initial()

        assertNotEquals(state, setAlertMode(state, AlertMode.PER_ITEM))
        assertNotEquals(state, setAlertDelivery(state, AlertDelivery.EACH))
        assertNotEquals(state, toggleAlertCategory(state, AlertCategory.PROBES))
    }

    @Test
    fun `the sheet survives a round trip`() {
        val state = setAlertDelivery(
            toggleAlertCategory(setAlertMode(GameState.initial(), AlertMode.PER_ITEM), AlertCategory.PRICE_REACHED),
            AlertDelivery.PER_CATEGORY,
        )
        val snapshot = GameSnapshot(lastUpdatedAt = EPOCH, state = state)

        val decoded = assertIs<DecodeResult.Success>(GameSave.decode(GameSave.encode(snapshot))).snapshot

        assertEquals(state.alerts, decoded.state.alerts)
    }

    @Test
    fun `a colony carried forward keeps hearing exactly what it heard before`() {
        // **The one hop in this table that must not change what the player hears.** Every earlier
        // behavioural hop — 9 for the completions, 14 for the yard, 15 for the flights — made a colony
        // quieter, and each was defensible because the thing being silenced had never been asked for.
        // This one would go the other way: migrating a played colony into `BY_CATEGORY · TOTAL` would
        // switch on seven categories nobody chose. So the new default is for new colonies only, which
        // is Davide's call of 2026-08-23, and a carried-forward save lands on the pair that describes
        // what 0.17 already did.
        val carried = AlertSettings.CARRIED_FORWARD

        assertEquals(AlertMode.PER_ITEM, carried.mode)
        assertEquals(AlertDelivery.EACH, carried.delivery)
    }

    @Test
    fun `a carried-forward colony still finds all seven switches on when it goes looking`() {
        // They are inert under `PER_ITEM` — the sheet does not draw the panel at all — so this is not
        // a promise about what it hears. It is about what it finds when it chooses the other mode:
        // seven off would be a panel that looks broken, and the honest opening position for a control
        // nobody has ever seen is the one a new colony gets.
        assertEquals(AlertCategory.entries.toSet(), AlertSettings.CARRIED_FORWARD.categories)
    }

    @Test
    fun `price reached is last because it is the odd one`() {
        // The order is the sheet's, and the sheet draws the panel by iterating this. Pinned here so
        // that adding an eighth category cannot silently land it in the middle of the list, and so
        // that the one row carrying a second line stays at the bottom where the design put it.
        assertEquals(
            listOf(
                AlertCategory.FACILITIES,
                AlertCategory.RESEARCH,
                AlertCategory.ADAPTATIONS,
                AlertCategory.HULLS,
                AlertCategory.PROBES,
                AlertCategory.FLEET_RETURNS,
                AlertCategory.PRICE_REACHED,
            ),
            AlertCategory.entries,
        )
    }

    private companion object {
        val EPOCH = Instant.fromEpochMilliseconds(0)
    }
}
