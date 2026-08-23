package dev.fardavide.oltre.client.settings.presentation

import dev.fardavide.oltre.client.design.text.English
import dev.fardavide.oltre.client.design.text.Strings
import dev.fardavide.oltre.core.AlertCategory
import dev.fardavide.oltre.core.AlertDelivery
import dev.fardavide.oltre.core.AlertMode
import dev.fardavide.oltre.core.AlertSettings
import dev.fardavide.oltre.core.BuildJob
import dev.fardavide.oltre.core.BuildingLevel
import dev.fardavide.oltre.core.BuildingType
import dev.fardavide.oltre.core.GalaxySeed
import dev.fardavide.oltre.core.GameState
import dev.fardavide.oltre.core.setAlertDelivery
import dev.fardavide.oltre.core.setAlertMode
import dev.fardavide.oltre.core.toggleAlertCategory
import kotlinx.datetime.TimeZone
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.hours
import kotlin.time.Instant

// The sheet read off a colony. Almost every field here is a straight rendering of `GameState.alerts`,
// and one is not — `timing` is a fold over `announcedEvents`, which is the reason this module exists
// at all rather than the mapping being three lines in the shell.
class AlertSheetUiStateTest {

    @Test
    fun `the lit chip is the mode the colony is in`() {
        val sheet = byCategory().toAlertSheetUiState(now = NOW, timeZone = TimeZone.UTC)

        assertEquals(AlertMode.BY_CATEGORY, sheet.modes.single { it.selected }.mode)
    }

    @Test
    fun `the lit stop is the delivery the colony is on`() {
        val sheet = colony().toAlertSheetUiState(now = NOW, timeZone = TimeZone.UTC)

        assertEquals(AlertDelivery.TOTAL, sheet.deliveries.single { it.selected }.delivery)
    }

    @Test
    fun `per item has no panel at all`() {
        // **Null rather than an empty list**, and the two are different things to look at: an empty
        // list would draw a card with no rows in it, where the design asks for no card.
        val sheet = perItem().toAlertSheetUiState(now = NOW, timeZone = TimeZone.UTC)

        assertNull(sheet.categories)
    }

    @Test
    fun `by category draws the seven in the sheet's own order`() {
        val sheet = byCategory().toAlertSheetUiState(now = NOW, timeZone = TimeZone.UTC)

        assertEquals(AlertCategory.entries, sheet.categories?.map { it.category })
    }

    @Test
    fun `a switch that is off is drawn off`() {
        val quiet = toggleAlertCategory(byCategory(), AlertCategory.PROBES)

        val rows = quiet.toAlertSheetUiState(now = NOW, timeZone = TimeZone.UTC).categories.orEmpty()

        assertEquals(false, rows.single { it.category == AlertCategory.PROBES }.on)
        assertTrue(rows.filter { it.category != AlertCategory.PROBES }.all { it.on })
    }

    @Test
    fun `only the price row carries a second line and it changes with the switch`() {
        val loud = byCategory().toAlertSheetUiState(now = NOW, timeZone = TimeZone.UTC).categories.orEmpty()
        val quiet = toggleAlertCategory(byCategory(), AlertCategory.PRICE_REACHED)
            .toAlertSheetUiState(now = NOW, timeZone = TimeZone.UTC).categories.orEmpty()

        assertTrue(loud.filter { it.category != AlertCategory.PRICE_REACHED }.all { it.note == null })
        assertEquals(
            Strings.alertPriceWatchNote(on = true),
            loud.single { it.category == AlertCategory.PRICE_REACHED }.note,
        )
        assertEquals(
            Strings.alertPriceWatchNote(on = false),
            quiet.single { it.category == AlertCategory.PRICE_REACHED }.note,
        )
    }

    @Test
    fun `a bell is spoken as its label and then its state`() {
        val rows = byCategory().toAlertSheetUiState(now = NOW, timeZone = TimeZone.UTC).categories.orEmpty()

        assertEquals(
            "Facilities · alerts on",
            English.resolve(rows.single { it.category == AlertCategory.FACILITIES }.spoken),
        )
    }

    @Test
    fun `one each explains no further`() {
        val each = setAlertDelivery(byCategory(), AlertDelivery.EACH)

        assertNull(each.toAlertSheetUiState(now = NOW, timeZone = TimeZone.UTC).timing)
    }

    @Test
    fun `the timing line names the instant the next alert is due`() {
        // The one live line on the sheet. The mine lands two hours after `now`, and nothing else on
        // this colony is in flight.
        val sheet = byCategory().toAlertSheetUiState(now = NOW, timeZone = TimeZone.UTC)

        assertEquals("Next alert at 13:38, and it is brought up to date rather than repeated.", English.resolve(sheet.timing!!))
    }

    @Test
    fun `total says it is updated rather than repeated and per category does not`() {
        val perCategory = setAlertDelivery(byCategory(), AlertDelivery.PER_CATEGORY)

        val line = English.resolve(perCategory.toAlertSheetUiState(now = NOW, timeZone = TimeZone.UTC).timing!!)

        assertEquals("Next alert at 13:38.", line)
    }

    @Test
    fun `the line answers under the settings the player is looking at rather than under the colony's jobs`() {
        // **The whole reason this reads `announcedEvents` rather than counting builds.** The mine is
        // still running; Facilities is off, so nothing is going to be sent about it — and a sheet that
        // promised an alert the scheduler would not book would be a preferences screen lying about
        // the only thing it does.
        val quiet = toggleAlertCategory(byCategory(), AlertCategory.FACILITIES)

        val line = English.resolve(quiet.toAlertSheetUiState(now = NOW, timeZone = TimeZone.UTC).timing!!)

        assertEquals(English.resolve(Strings.alertNothingPending()), line)
    }

    @Test
    fun `a colony with nothing in flight says so rather than leaving a gap`() {
        val idle = setAlertMode(freshState(), AlertMode.BY_CATEGORY)

        val line = English.resolve(idle.toAlertSheetUiState(now = NOW, timeZone = TimeZone.UTC).timing!!)

        assertEquals(English.resolve(Strings.alertNothingPending()), line)
    }

    @Test
    fun `the example is the string the chosen stop would actually print`() {
        val each = setAlertDelivery(byCategory(), AlertDelivery.EACH)
        val perCategory = setAlertDelivery(byCategory(), AlertDelivery.PER_CATEGORY)

        assertEquals(
            "Metal Mine reached level 4",
            English.resolve(each.toAlertSheetUiState(now = NOW, timeZone = TimeZone.UTC).example),
        )
        assertEquals(
            "3 facilities are done",
            English.resolve(perCategory.toAlertSheetUiState(now = NOW, timeZone = TimeZone.UTC).example),
        )
        assertEquals(
            "2 facilities · 1 hull",
            English.resolve(byCategory().toAlertSheetUiState(now = NOW, timeZone = TimeZone.UTC).example),
        )
    }

    // ── Helpers ─────────────────────────────────────────────────────────────────────────────

    private fun byCategory(): GameState = setAlertMode(colony(), AlertMode.BY_CATEGORY)

    private fun perItem(): GameState = setAlertMode(colony(), AlertMode.PER_ITEM)

    // A mine landing two hours from now, and nothing else. One job is enough for every assertion
    // here: what the timing line is about is *which* job the settings admit, not how many there are.
    private fun colony(): GameState = freshState().copy(
        builds = mapOf(
            BuildingType.METAL_MINE to BuildJob(
                building = BuildingType.METAL_MINE,
                toLevel = BuildingLevel(2),
                startedAt = NOW,
                completesAt = NOW + 2.hours,
            ),
        ),
    )

    private fun freshState(): GameState =
        GameState.initial(GalaxySeed(20_260_807)).copy(alerts = AlertSettings.NEW_COLONY)

    private companion object {
        val NOW: Instant = Instant.parse("2026-08-23T11:38:00Z")
    }
}
