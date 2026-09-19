package dev.fardavide.oltre.client.research.presentation

import dev.fardavide.oltre.client.design.component.SheetLine
import dev.fardavide.oltre.client.design.component.SheetLinePart
import dev.fardavide.oltre.client.design.text.English
import dev.fardavide.oltre.client.design.text.Strings
import dev.fardavide.oltre.client.research.ui.ResearchUiState
import dev.fardavide.oltre.client.research.ui.TechnologyRowUiState
import dev.fardavide.oltre.core.AllianceSpeedup
import dev.fardavide.oltre.core.BuildingLevel
import dev.fardavide.oltre.core.GalaxySeed
import dev.fardavide.oltre.core.GameState
import dev.fardavide.oltre.core.Technology
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant
import kotlinx.datetime.TimeZone

// **What the research screen says about the alliance**, which is a bare figure beside the rule the
// screen already had — the attribution is what abbreviates, not the number.
class AlliancePerkResearchUiStateTest {

    @Test
    fun `a colony in no alliance says nothing`() {
        val face = research(AllianceSpeedup.NONE, allianceLevel = null)

        assertNull(face.alliancePerk)
        assertTrue(face.technologies.all { it.baseDuration == null })
        assertTrue(face.technologies.none { it.mentionsTheAlliance() })
    }

    // **The bare figure, not the attribution.** The screen joins it to `one project at a time` with
    // the app's own middle dot; `your alliance` is the clause that does not fit at 320dp and the one
    // the heading already implies.
    @Test
    fun `a colony in an alliance carries the figure alone`() {
        val face = research(AllianceSpeedup(14), allianceLevel = 7)

        assertEquals("−14%", English.resolve(assertNotNull(face.alliancePerk)))
    }

    @Test
    fun `every project starts sooner and the sheet says which level did it`() {
        val alone = research(AllianceSpeedup.NONE, allianceLevel = null).first()
        val helped = research(AllianceSpeedup(14), allianceLevel = 7).first()

        assertTrue(
            English.resolve(helped.duration) != English.resolve(alone.duration),
            "helped ${English.resolve(helped.duration)} should differ from ${English.resolve(alone.duration)}",
        )
        assertEquals(
            English.resolve(alone.duration),
            English.resolve(assertNotNull(helped.baseDuration)),
            "the sheet's base should be exactly the wait with no alliance behind it",
        )
        assertTrue(helped.says(Strings.alliancePerkResearch(level = 7, percent = 14)))
        assertTrue(helped.says(Strings.alliancePerkResearchRunning()))
    }

    @Test
    fun `an alliance at the floor says that is the most it takes off`() {
        assertTrue(!research(AllianceSpeedup(28), allianceLevel = 14).first().says(Strings.alliancePerkAtTheFloor()))
        assertTrue(research(AllianceSpeedup(30), allianceLevel = 15).first().says(Strings.alliancePerkAtTheFloor()))
    }

    // **The adaptation branch is not helped and must not claim to be.** The perk is build and
    // research speed; a ladder is neither, so nothing on those rows mentions an alliance.
    @Test
    fun `the adaptation rows say nothing about the alliance`() {
        val face = research(AllianceSpeedup(14), allianceLevel = 7)

        assertTrue(
            face.adaptation.none { row -> row.sheet.lines.any { "alliance" in it.words() } },
            "an adaptation ladder is neither a build nor a research",
        )
    }

    // **A Robotics Factory, because every technology is locked without one** — and a locked row's
    // sheet is trimmed to its requirement, which correctly drops the alliance's sentences. Testing
    // them at all means unlocking the branch first, which is what a player does before any of this
    // is on screen.
    private fun research(speedup: AllianceSpeedup, allianceLevel: Int?): ResearchUiState =
        GameState.initial(GalaxySeed(1)).let { state ->
            state.copy(
                allianceSpeedup = speedup,
                buildings = state.buildings.copy(roboticsFactory = BuildingLevel(1)),
            )
        }.toResearchUiState(now = NOW, timeZone = TimeZone.UTC, allianceLevel = allianceLevel)

    // **Extraction, and both halves of that choice matter.** It is behind Robotics 1 alone, so the
    // fixture above unlocks it — and a *locked* row's sheet is trimmed to its requirement, which
    // correctly drops the alliance's sentences, so a locked row could never have shown them. Its
    // 90-minute base is also long enough that the boon survives rounding to whole minutes, which a
    // six-minute Photovoltaics would have swallowed.
    private fun ResearchUiState.first(): TechnologyRowUiState =
        technologies.first { it.technology == Technology.EXTRACTION }

    private fun TechnologyRowUiState.says(text: dev.fardavide.oltre.client.design.text.TextRes): Boolean =
        sheet.lines.any { English.resolve(text) in it.words() }

    private fun TechnologyRowUiState.mentionsTheAlliance(): Boolean =
        sheet.lines.any { "alliance" in it.words() }

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
