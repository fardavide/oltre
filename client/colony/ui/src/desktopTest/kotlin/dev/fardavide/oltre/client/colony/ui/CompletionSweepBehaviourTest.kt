package dev.fardavide.oltre.client.colony.ui

import dev.fardavide.oltre.client.design.text.TextRes
import dev.fardavide.oltre.client.design.component.CostChipUiState
import dev.fardavide.oltre.client.design.component.VerdictUiState
import dev.fardavide.oltre.core.BuildingLevel
import dev.fardavide.oltre.core.BuildingType
import dev.fardavide.oltre.core.ResourceKind
import org.junit.Test

// The one transition in the pass with a decision inside it: a row that finished while the app was
// closed shows the level it *had* until the band of light has passed the badge, and the level it
// arrived at afterwards. The band itself is a baseline's business —
// `facility_list_finished_while_away` — and what is asserted here is the number, which is the part
// a player reads rather than sees.
class CompletionSweepBehaviourTest {

    @Test
    fun `a row that finished while away keeps the level it had until the band reaches the badge`() {
        facilityRow(finishedSolarPlant()) {
            // 795ms: 420 of delay plus half the crossing, and 135ms short of the swap
            atMillis(795)
            assertReads("LV 8")
        }
    }

    @Test
    fun `and takes the level it arrived at once the band has passed`() {
        facilityRow(finishedSolarPlant()) {
            atMillis(1_000)
            assertReads("LV 9")
        }
    }

    @Test
    fun `a row with nothing to announce shows its real level from the first frame`() {
        facilityRow(finishedSolarPlant().copy(finishedWhileAway = false)) {
            atMillis(16)
            assertReads("LV 9")
        }
    }

    // The regression this file exists for. The shell withdraws the announcement as soon as the
    // screen showing it has been composed — otherwise every return to the tab would replay a sweep
    // about a launch that already happened. Read live rather than latched, that withdrawal landed
    // mid-crossing: the band vanished wherever it had got to and the badge snapped to the new level
    // in the same frame, with nothing on screen to explain the change. It has to survive being
    // forgotten about.
    @Test
    fun `the crossing survives the announcement being withdrawn under it`() {
        facilityRow(finishedSolarPlant()) {
            atMillis(100)
            withdrawTheAnnouncement()
            // Still short of the swap, so the badge must still read the old level
            atMillis(600)
            assertReads("LV 8")
            // And it must still arrive at the new one on its own schedule
            atMillis(400)
            assertReads("LV 9")
        }
    }

    private fun finishedSolarPlant() = FacilityRowUiState(
        building = BuildingType.SOLAR_PLANT,
        name = TextRes("Solar Plant"),
        compactName = TextRes("Solar Plant"),
        level = BuildingLevel(9),
        costs = listOf(
            CostChipUiState(kind = ResourceKind.METAL, amount = TextRes("2,868"), short = false),
            CostChipUiState(kind = ResourceKind.CRYSTAL, amount = TextRes("1,135"), short = false),
        ),
        duration = TextRes("1h 48m"),
        action = FacilityActionUiState.Upgrade,
        power = null,
        fix = null,
        watch = null,
        verdict = VerdictUiState(label = TextRes("+50 supply · draw already covered"), compactLabel = TextRes("+50 supply")),
        detail = FacilityDetailUiState(lines = emptyList(), ladder = emptyList(), pointer = null),
        finishedWhileAway = true,
    )
}
