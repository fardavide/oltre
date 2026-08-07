package dev.fardavide.oltre.client.research.presentation

import dev.fardavide.oltre.client.design.component.CostChipUiState
import dev.fardavide.oltre.core.BuildingLevel
import dev.fardavide.oltre.core.BuildingType
import dev.fardavide.oltre.core.Buildings
import dev.fardavide.oltre.core.GalaxySeed
import dev.fardavide.oltre.core.GameState
import dev.fardavide.oltre.core.Research
import dev.fardavide.oltre.core.ResearchJob
import dev.fardavide.oltre.core.ResourceKind
import dev.fardavide.oltre.core.Resources
import dev.fardavide.oltre.core.TechLevel
import dev.fardavide.oltre.core.Technology
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant
import kotlinx.datetime.TimeZone

class ResearchUiStateTest {

    @Test
    fun `the branch is three rows in a fixed order`() {
        // given
        val uiState = freshState().toResearchUiState(now = EPOCH, timeZone = TimeZone.UTC)

        // then
        assertEquals(
            listOf(Technology.PHOTOVOLTAICS, Technology.EXTRACTION, Technology.ENRICHMENT),
            uiState.technologies.map { it.technology },
        )
        assertEquals(listOf("Photovoltaics", "Extraction", "Enrichment"), uiState.technologies.map { it.name })
    }

    @Test
    fun `a technology nobody has researched has no current effect to show`() {
        // given - there is no "now" to compare the next level against yet
        val row = colony().rowFor(Technology.PHOTOVOLTAICS)

        // then
        assertNull(row.effect.current)
        assertEquals("+10%", row.effect.next)
        assertEquals(TechLevel(0), row.level)
    }

    @Test
    fun `the effect line carries what you have and what the next level buys`() {
        // given
        val row = colony(research = Research.initial().withLevel(Technology.PHOTOVOLTAICS, TechLevel(2)))
            .rowFor(Technology.PHOTOVOLTAICS)

        // then - level 8 is only meaningful against level 7
        assertEquals("+21%", row.effect.current)
        assertEquals("+33%", row.effect.next)
    }

    @Test
    fun `each technology names what it multiplies and what it drops at 320dp`() {
        // given
        val uiState = colony().toResearchUiState(now = EPOCH, timeZone = TimeZone.UTC)

        // then - the percentages and the resource names are load-bearing; the noun is not
        assertEquals("Solar Plant output", uiState.technologies[0].effect.subject)
        assertEquals("Solar Plant", uiState.technologies[0].effect.compactSubject)
        assertEquals("metal · crystal output", uiState.technologies[1].effect.subject)
        assertEquals("metal · crystal", uiState.technologies[1].effect.compactSubject)
        assertEquals("deuterium output", uiState.technologies[2].effect.subject)
        assertEquals("deuterium", uiState.technologies[2].effect.compactSubject)
    }

    @Test
    fun `costs come back as three chips with the short one flagged`() {
        // given a colony that can pay for everything but the deuterium
        val row = colony(
            buildings = gated(),
            resources = Resources.of(metal = 300, crystal = 150),
        ).rowFor(Technology.PHOTOVOLTAICS)

        // then - colour is the affordability channel
        assertEquals(
            listOf(
                CostChipUiState(kind = ResourceKind.METAL, amount = "300", short = false),
                CostChipUiState(kind = ResourceKind.CRYSTAL, amount = "150", short = false),
                CostChipUiState(kind = ResourceKind.DEUTERIUM, amount = "100", short = true),
            ),
            row.costs,
        )
    }

    @Test
    fun `a technology behind its gate says what it requires`() {
        // given a colony with no Robotics Factory
        val uiState = colony().toResearchUiState(now = EPOCH, timeZone = TimeZone.UTC)

        // then
        assertEquals(
            ResearchActionUiState.Locked("Requires Robotics 1"),
            uiState.technologies.first { it.technology == Technology.PHOTOVOLTAICS }.action,
        )
        assertEquals(
            ResearchActionUiState.Locked("Requires Extraction 3"),
            uiState.technologies.first { it.technology == Technology.ENRICHMENT }.action,
        )
    }

    @Test
    fun `Enrichment unlocks once Extraction has reached its third level`() {
        // given
        val row = colony(
            buildings = gated(),
            research = Research.initial().withLevel(Technology.EXTRACTION, TechLevel(3)),
            resources = Resources.of(metal = 500, crystal = 700, deuterium = 200),
        ).rowFor(Technology.ENRICHMENT)

        // then
        assertEquals(ResearchActionUiState.Start, row.action)
    }

    @Test
    fun `an affordable technology with a free slot offers to start`() {
        // given
        val row = colony(
            buildings = gated(),
            resources = Resources.of(metal = 300, crystal = 150, deuterium = 100),
        ).rowFor(Technology.PHOTOVOLTAICS)

        // then
        assertEquals(ResearchActionUiState.Start, row.action)
    }

    @Test
    fun `a row waits on the money when the money is further away than the slot`() {
        // given 90 metal short at 90 an hour, against a slot that frees in half an hour
        val row = colony(
            buildings = gated(),
            resources = Resources.of(metal = 210, crystal = 150, deuterium = 100),
            activeResearch = project(completesAt = EPOCH + 30.minutes),
        ).rowFor(Technology.PHOTOVOLTAICS)

        // then
        assertEquals(ResearchActionUiState.AvailableIn("in 1h 00m"), row.action)
    }

    @Test
    fun `a row waits on the slot when the slot is further away than the money`() {
        // given everything paid for and a project two hours from done
        val row = colony(
            buildings = gated(),
            resources = Resources.of(metal = 300, crystal = 150, deuterium = 100),
            activeResearch = project(completesAt = EPOCH + 2.hours),
        ).rowFor(Technology.PHOTOVOLTAICS)

        // then - one number with one meaning, and the player never has to know which reason it was
        assertEquals(ResearchActionUiState.AvailableIn("in 2h 00m"), row.action)
    }

    @Test
    fun `a resource the colony cannot produce at all reads as a dash`() {
        // given no Deuterium Synthesizer, so the deuterium a technology wants never arrives
        val row = colony(
            buildings = gated().withLevel(BuildingType.DEUTERIUM_SYNTHESIZER, BuildingLevel(0)),
            resources = Resources.of(metal = 300, crystal = 150),
        ).rowFor(Technology.PHOTOVOLTAICS)

        // then - "in 2,000,000h" would be a worse lie than saying nothing
        assertEquals(ResearchActionUiState.AvailableIn("—"), row.action)
    }

    @Test
    fun `the running technology carries its target level countdown and finish time`() {
        // given a two-hour project half done
        val now = EPOCH + 10.hours
        val row = colony(
            buildings = gated(),
            activeResearch = ResearchJob(
                technology = Technology.PHOTOVOLTAICS,
                toLevel = TechLevel(1),
                startedAt = EPOCH + 9.hours,
                completesAt = EPOCH + 11.hours,
            ),
        ).rowFor(Technology.PHOTOVOLTAICS, now = now)

        // then
        assertEquals(
            ResearchActionUiState.Running(
                toLevel = TechLevel(1),
                countdown = "01:00:00",
                progressPercent = 50,
                doneAt = "done 11:00",
            ),
            row.action,
        )
    }

    @Test
    fun `only the running technology takes the running treatment`() {
        // given
        val uiState = colony(
            buildings = gated(),
            resources = Resources.of(metal = 600, crystal = 400, deuterium = 200),
            activeResearch = project(completesAt = EPOCH + 1.hours, technology = Technology.PHOTOVOLTAICS),
        ).toResearchUiState(now = EPOCH, timeZone = TimeZone.UTC)

        // then - the other two carry a wait, because the slot is what is holding them
        assertIs<ResearchActionUiState.Running>(uiState.technologies[0].action)
        assertIs<ResearchActionUiState.AvailableIn>(uiState.technologies[1].action)
    }

    @Test
    fun `the duration a row shows is already divided by the Robotics Factory`() {
        // given the same technology at two Robotics levels
        val slow = colony(buildings = gated()).rowFor(Technology.PHOTOVOLTAICS)
        val quick = colony(buildings = gated(robotics = 4)).rowFor(Technology.PHOTOVOLTAICS)

        // then - 60 minutes at Robotics 1, and 60 x 25/33 at Robotics 4
        assertEquals("56m", slow.duration)
        assertEquals("46m", quick.duration)
    }

    private fun GameState.rowFor(technology: Technology, now: Instant = EPOCH): TechnologyRowUiState =
        toResearchUiState(now = now, timeZone = TimeZone.UTC).technologies.first { it.technology == technology }

    private fun colony(
        buildings: Buildings = Buildings.initial(),
        research: Research = Research.initial(),
        resources: Resources = Resources.of(),
        activeResearch: ResearchJob? = null,
    ): GameState = GameState(
        resources = resources,
        buildings = buildings,
        builds = emptyMap(),
        research = research,
        activeResearch = activeResearch,
        galaxy = freshState().galaxy,
        returningFleet = null,
        eventLog = emptyList(),
    )

    // `GameState.initial` takes a galaxy seed rather than defaulting one, so production cannot found
    // every colony in the same galaxy. The Research screen draws none of it.
    private fun freshState(): GameState = GameState.initial(GalaxySeed(20_260_807))

    // Past the deuterium wall, which is where the branch opens.
    private fun gated(robotics: Int = 1): Buildings =
        Buildings.initial().withLevel(BuildingType.ROBOTICS_FACTORY, BuildingLevel(robotics))

    // The slot is empire-wide, so "the slot is busy" means busy with *something else* — the
    // default is deliberately not the technology these tests then ask about.
    private fun project(
        completesAt: Instant,
        technology: Technology = Technology.EXTRACTION,
    ): ResearchJob = ResearchJob(
        technology = technology,
        toLevel = TechLevel(1),
        startedAt = EPOCH,
        completesAt = completesAt,
    )

    private companion object {
        val EPOCH = Instant.fromEpochMilliseconds(0)
    }
}
