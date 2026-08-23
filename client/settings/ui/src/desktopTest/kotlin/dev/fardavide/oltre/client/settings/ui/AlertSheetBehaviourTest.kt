package dev.fardavide.oltre.client.settings.ui

import androidx.compose.ui.test.ExperimentalTestApi
import dev.fardavide.oltre.client.design.text.Strings
import dev.fardavide.oltre.core.AlertCategory
import dev.fardavide.oltre.core.AlertDelivery
import dev.fardavide.oltre.core.AlertMode
import dev.fardavide.oltre.core.BuildingType
import kotlin.test.assertEquals
import org.junit.Test

// **What the sheet does when it is pressed**, which is the half a baseline cannot photograph. Every
// control here commits on tap and there is no Save — so what has to be true is that every tap reaches
// the callback that will change the colony, and that the two things which are *absent* in one state
// really are absent rather than merely invisible.
@OptIn(ExperimentalTestApi::class)
class AlertSheetBehaviourTest {

    @Test
    fun `tapping a mode chip asks for that mode`() {
        var asked: AlertMode? = null

        alertSheet(mode = AlertMode.BY_CATEGORY, onSelectMode = { asked = it }) {
            tapMode(AlertMode.PER_ITEM)
        }

        assertEquals(AlertMode.PER_ITEM, asked)
    }

    @Test
    fun `tapping the chip that is already lit still asks for it`() {
        // A ladder is not a toggle: both stops are on screen, so a tap means *this one* rather than
        // *the other one*. The state it asks for is the state it is already in, and the shell's
        // `prefer` writes it — which is a save and a re-book of exactly the same schedule, and the
        // thing a player must never see is the ladder moving under their finger.
        var asked: AlertMode? = null

        alertSheet(mode = AlertMode.BY_CATEGORY, onSelectMode = { asked = it }) {
            tapMode(AlertMode.BY_CATEGORY)
        }

        assertEquals(AlertMode.BY_CATEGORY, asked)
    }

    @Test
    fun `tapping a delivery chip asks for that stop`() {
        var asked: AlertDelivery? = null

        alertSheet(delivery = AlertDelivery.TOTAL, onSelectDelivery = { asked = it }) {
            tapDelivery(AlertDelivery.PER_CATEGORY)
        }

        assertEquals(AlertDelivery.PER_CATEGORY, asked)
    }

    @Test
    fun `every one of the seven rows answers`() {
        // Seven separate assertions in one test, because what would be wrong here is a row wired to
        // its neighbour — and a single row tapped in isolation cannot see that.
        val asked = mutableListOf<AlertCategory>()

        AlertCategory.entries.forEach { category ->
            alertSheet(onToggleCategory = { asked += it }) { tapCategory(category) }
        }

        assertEquals(AlertCategory.entries.toList(), asked.toList())
    }

    @Test
    fun `the panel is absent under per item rather than empty`() {
        // **The design's own words, and the reason the model makes it nullable**: the panel does not
        // belong to a chip, it belongs to the option — so when that option is not chosen there is
        // nothing there and the ladder above has not moved.
        alertSheet(mode = AlertMode.PER_ITEM) {
            assertNoPanel()
            assertSays(Strings.alertModeNote(AlertMode.PER_ITEM))
        }
    }

    @Test
    fun `by category draws all seven`() {
        alertSheet(mode = AlertMode.BY_CATEGORY) {
            assertPanelShowing()
            assertCategoryCount(AlertCategory.entries.size)
        }
    }

    @Test
    fun `only the price row carries a second line`() {
        // The panel would be unreadable with a sentence under every row, and six of them say what
        // they do in their own name. The seventh governs whether a control appears on rows elsewhere
        // in the app, which is the one thing a name cannot carry.
        alertSheet(mode = AlertMode.BY_CATEGORY) {
            assertSays(Strings.alertPriceWatchNote(on = true))
            assertDoesNotSay(Strings.alertPriceWatchNote(on = false))
        }
    }

    @Test
    fun `switching the price row off changes what its line claims`() {
        // Off *removes* the watch rather than muting it, so the line states the consequence rather
        // than the setting. This is where call 2 is explained, and it is explained on the row that
        // governs it rather than in a paragraph at the top of the sheet.
        alertSheet(mode = AlertMode.BY_CATEGORY, off = setOf(AlertCategory.PRICE_REACHED)) {
            assertSays(Strings.alertPriceWatchNote(on = false))
            assertDoesNotSay(Strings.alertPriceWatchNote(on = true))
        }
    }

    @Test
    fun `a bell says which category it is and which way it is set`() {
        alertSheet(mode = AlertMode.BY_CATEGORY, off = setOf(AlertCategory.PROBES)) {
            assertSpoken(AlertCategory.FACILITIES, on = true)
            assertSpoken(AlertCategory.PROBES, on = false)
        }
    }

    @Test
    fun `one each shows a string and explains no further`() {
        // The one stop with no timing line, and its absence is the assertion: the answer there is
        // *whenever anything lands*, and a sentence saying so would be a sentence explaining the word
        // `each`.
        alertSheet(delivery = AlertDelivery.EACH) {
            assertNoTiming()
            assertExample(Strings.reachedLevel(Strings.buildingFullName(BuildingType.METAL_MINE), 4))
        }
    }

    @Test
    fun `the other two stops say when the next alert is due`() {
        alertSheet(delivery = AlertDelivery.PER_CATEGORY) {
            assertSays(Strings.alertNextAt(hour = 17, minute = 42, updating = false))
        }
        alertSheet(delivery = AlertDelivery.TOTAL) {
            assertSays(Strings.alertNextAt(hour = 17, minute = 42, updating = true))
        }
    }

    @Test
    fun `every control still answers at a Slide Over's width`() {
        // The narrow window is where the Delivery ladder stacks, and a stacked ladder is a different
        // layout — so the three chips are re-measured and re-placed. What must not change is that
        // they are still the same three controls.
        var asked: AlertDelivery? = null

        alertSheet(compact = true, delivery = AlertDelivery.EACH, onSelectDelivery = { asked = it }) {
            tapDelivery(AlertDelivery.TOTAL)
        }

        assertEquals(AlertDelivery.TOTAL, asked)
    }
}
