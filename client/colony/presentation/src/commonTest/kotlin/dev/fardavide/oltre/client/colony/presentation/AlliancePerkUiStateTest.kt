package dev.fardavide.oltre.client.colony.presentation

import dev.fardavide.oltre.client.colony.ui.ColonyUiState
import dev.fardavide.oltre.client.colony.ui.FacilityRowUiState
import dev.fardavide.oltre.client.design.component.SheetLine
import dev.fardavide.oltre.client.design.component.SheetLinePart
import dev.fardavide.oltre.client.design.text.English
import dev.fardavide.oltre.client.design.text.Strings
import dev.fardavide.oltre.core.AllianceSpeedup
import dev.fardavide.oltre.core.BuildingLevel
import dev.fardavide.oltre.core.BuildingType
import dev.fardavide.oltre.core.GalaxySeed
import dev.fardavide.oltre.core.GameState
import dev.fardavide.oltre.core.PlaceholderBalance
import dev.fardavide.oltre.core.StartUpgradeResult
import dev.fardavide.oltre.core.startUpgrade
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant
import kotlinx.datetime.TimeZone

// **What the colony screen says about the alliance: one string, and nothing on any row.**
class AlliancePerkUiStateTest {

    // Nineteen colonies in twenty, and there is nothing on them for a player to ask about — they
    // have never seen a percentage in that slot, so no absence is legible.
    @Test
    fun `a colony in no alliance says nothing and its sheet is the sheet that ships`() {
        val face = colony(speedup = AllianceSpeedup.NONE, allianceLevel = null)

        assertNull(face.alliancePerk)
        assertTrue(face.facilities.all { it.baseDuration == null })
        assertTrue(face.facilities.none { it.mentionsTheAlliance() })
    }

    @Test
    fun `a colony in an alliance says so once - in the section label`() {
        val face = colony(speedup = AllianceSpeedup(14), allianceLevel = 7)

        assertEquals("−14% · your alliance", English.resolve(assertNotNull(face.alliancePerk)))
    }

    // **The row's numbers are already the helped ones**, which is the argument for the row saying
    // nothing: every row is shortened by the same percentage, so a mark on each would be one glyph
    // repeated five times.
    @Test
    fun `every row's duration is shorter and no row says why`() {
        val alone = colony(speedup = AllianceSpeedup.NONE, allianceLevel = null).mine()
        val helped = colony(speedup = AllianceSpeedup(30), allianceLevel = 15).mine()

        assertTrue(
            English.resolve(helped.duration) != English.resolve(alone.duration),
            "helped ${English.resolve(helped.duration)} should differ from ${English.resolve(alone.duration)}",
        )
    }

    // The counterfactual, drawn once per row and only behind a tap, in the slot the true number
    // occupies rather than beside it.
    @Test
    fun `the sheet carries the wait without the alliance - and the sentence that attributes it`() {
        val mine = colony(speedup = AllianceSpeedup(14), allianceLevel = 7).mine()

        assertEquals(
            English.resolve(colony(speedup = AllianceSpeedup.NONE, allianceLevel = null).mine().duration),
            English.resolve(assertNotNull(mine.baseDuration)),
            "the sheet's base should be exactly the wait with no alliance behind it",
        )
        assertTrue(mine.says(Strings.alliancePerkBuild(level = 7, percent = 14)), "no attribution")
        assertTrue(mine.says(Strings.alliancePerkBuildRunning()), "nothing about what it does not touch")
    }

    // Only at the ceiling, and it is what stops a player waiting for a number that will never move.
    @Test
    fun `an alliance at the floor says that is the most it takes off`() {
        val below = colony(speedup = AllianceSpeedup(28), allianceLevel = 14).mine()
        val atTheFloor = colony(speedup = AllianceSpeedup(30), allianceLevel = 15).mine()

        assertTrue(!below.says(Strings.alliancePerkAtTheFloor()))
        assertTrue(atTheFloor.says(Strings.alliancePerkAtTheFloor()))
    }

    // **A row in flight is not asked about the alliance.** Its wait was fixed when the job started,
    // so explaining what is taken off the *next* one would answer a question this card is not being
    // asked — which is the seam the design draws rather than hides.
    @Test
    fun `a row already building says nothing about the alliance`() {
        // A level-12 mine's next level costs more than a colony opens with, so the stores are topped
        // up to exactly its price first — this test is about a row in flight, not affordability.
        val rich = founded(AllianceSpeedup(14)).let { state ->
            state.copy(resources = PlaceholderBalance.upgradeCost(BuildingType.METAL_MINE, BuildingLevel(13)))
        }
        val started = assertIs<StartUpgradeResult.Started>(
            startUpgrade(rich, BuildingType.METAL_MINE, at = NOW),
        ).state

        val building = started.toColonyUiState(now = NOW, timeZone = TimeZone.UTC, allianceLevel = 7)
            .facilities
            .first { it.building == BuildingType.METAL_MINE }

        assertTrue(!building.mentionsTheAlliance())
    }

    private fun colony(speedup: AllianceSpeedup, allianceLevel: Int?): ColonyUiState =
        founded(speedup).toColonyUiState(now = NOW, timeZone = TimeZone.UTC, allianceLevel = allianceLevel)

    // **A mine at level 12, not a fresh one**, and the first run of this file is why: a new colony's
    // first upgrade already sits on `MINIMUM_UPGRADE_DURATION`, so the strongest alliance in the
    // game cannot make it one second shorter. That is the floor working exactly as intended — and it
    // would have made "the duration is shorter" pass for the wrong reason on any row deep enough to
    // move, so the fixture is deep enough to move.
    private fun founded(speedup: AllianceSpeedup): GameState =
        GameState.initial(GalaxySeed(1)).let { state ->
            state.copy(
                allianceSpeedup = speedup,
                buildings = state.buildings.copy(metalMine = BuildingLevel(12)),
            )
        }

    private fun ColonyUiState.mine(): FacilityRowUiState =
        facilities.first { it.building == BuildingType.METAL_MINE }

    private fun FacilityRowUiState.says(text: dev.fardavide.oltre.client.design.text.TextRes): Boolean =
        detail.lines.any { line -> English.resolve(text) in line.words() }

    private fun FacilityRowUiState.mentionsTheAlliance(): Boolean =
        detail.lines.any { "alliance" in it.words() }

    private fun SheetLine.words(): String = parts.joinToString(" ") {
        when (it) {
            is SheetLinePart.Words -> English.resolve(it.text)
            is SheetLinePart.Figure -> English.resolve(it.text)
        }
    }

    private companion object {
        val NOW: Instant = Instant.parse("2026-09-18T09:00:00Z")
    }
}
