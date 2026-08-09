package dev.fardavide.oltre.client.research.presentation

import dev.fardavide.oltre.client.design.component.CostChipUiState
import dev.fardavide.oltre.core.AdaptationJob
import dev.fardavide.oltre.core.AdaptationTechnology
import dev.fardavide.oltre.core.BuildingLevel
import dev.fardavide.oltre.core.BuildingType
import dev.fardavide.oltre.core.Buildings
import dev.fardavide.oltre.core.GalaxyBalance
import dev.fardavide.oltre.core.GalaxyCoordinate
import dev.fardavide.oltre.core.GalaxySeed
import dev.fardavide.oltre.core.GameState
import dev.fardavide.oltre.core.Research
import dev.fardavide.oltre.core.ResearchJob
import dev.fardavide.oltre.core.ResourceKind
import dev.fardavide.oltre.core.Resources
import dev.fardavide.oltre.core.SystemAddress
import dev.fardavide.oltre.core.TechLevel
import dev.fardavide.oltre.core.Technology
import dev.fardavide.oltre.core.adaptationShortlist
import dev.fardavide.oltre.core.worldAt
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue
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
                // Photovoltaics 1 at a third of the sheet's 300 / 150 / 100 — the opening
                // discount. The stock below still covers two of the three and not the deuterium,
                // which is what this test is about.
                CostChipUiState(kind = ResourceKind.METAL, amount = "100", short = false),
                CostChipUiState(kind = ResourceKind.CRYSTAL, amount = "50", short = false),
                CostChipUiState(kind = ResourceKind.DEUTERIUM, amount = "33", short = true),
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
        // given 90 metal short at 90 an hour, against a slot that frees in half an hour.
        // Photovoltaics 1 costs 100 metal since the opening discount reached the branch, so the
        // stock is 10 rather than 210 — the shortfall this test is about is unchanged.
        val row = colony(
            buildings = gated(),
            resources = Resources.of(metal = 10, crystal = 150, deuterium = 100),
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
    fun `a row waits on the slot when the other branch is the one holding it`() {
        // given everything paid for and an adaptation ladder two hours from done
        val row = colony(
            buildings = gated(),
            resources = Resources.of(metal = 300, crystal = 150, deuterium = 100),
            activeAdaptation = ladder(completesAt = EPOCH + 2.hours),
        ).rowFor(Technology.PHOTOVOLTAICS)

        // then - one slot, shared: a ladder holds it exactly as hard as a technology does, and a
        // row that read only `activeResearch` would offer to start a project the model refuses.
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

        // then - level 1 is the deepest step of the opening discount, so 20 minutes rather than
        // the sheet's 60, then the divisor: 20 x 25/27 at Robotics 1 and 20 x 25/33 at Robotics 4
        assertEquals("19m", slow.duration)
        assertEquals("16m", quick.duration)
    }

    // ── The adaptation branch ────────────────────────────────────────────────────────────────
    //
    // A second list on the same ui-state rather than three more entries in the first, mirroring
    // core's own split: an applied level multiplies a per-hour rate and an adaptation level widens
    // a band in °C, g or atm, and one list cannot carry both without making two kinds of thing
    // look like one.

    @Test
    fun `the adaptation branch is three ladders in a fixed order`() {
        // given
        val uiState = freshState().toResearchUiState(now = EPOCH, timeZone = TimeZone.UTC)

        // then - applied first, always, in every state: reading position is learnable only if fixed
        assertEquals(
            listOf(AdaptationTechnology.THERMAL, AdaptationTechnology.GRAVITIC, AdaptationTechnology.ATMOSPHERIC),
            uiState.adaptation.map { it.technology },
        )
        assertEquals(listOf("Thermal", "Gravitic", "Atmospheric"), uiState.adaptation.map { it.name })
    }

    @Test
    fun `a ladder's effect line is a band becoming a wider band with the unit stated once`() {
        // given
        val rows = adaptable().toResearchUiState(now = EPOCH, timeZone = TimeZone.UTC).adaptation

        // then - the unit sits where the applied row's trailing noun sits, so both read
        // [value] -> [value] [what of]
        assertEquals(EffectUiState("−30 … +45", "−44 … +59", "°C", "°C"), rows[0].effect)
        assertEquals(EffectUiState("0.65 … 1.40", "0.60 … 1.52", "g", "g"), rows[1].effect)
        assertEquals(EffectUiState("0.5 … 2.6", "0.44 … 3.5", "atm", "atm"), rows[2].effect)
    }

    // The applied line sheds "output" at 320dp because "output" is prose and prose has fat. A band
    // line is digits, units and relations — there is nothing in it that could be cut.
    @Test
    fun `a band line is the same string at both widths`() {
        // given
        val rows = adaptable().toResearchUiState(now = EPOCH, timeZone = TimeZone.UTC).adaptation

        // then
        rows.forEach { row -> assertEquals(row.effect.subject, row.effect.compactSubject) }
    }

    // Every other ladder assertion holds a level-0 empire, which is the one level at which
    // `research.levelOf(technology)` and a hard-coded zero are indistinguishable — so without this
    // the mapper could read neither the level nor anything derived from it and stay green.
    @Test
    fun `a climbed ladder is priced timed and banded from the level already held`() {
        // given an empire four levels up its gravity ladder
        val row = adaptable(
            research = Research.initial().withLevel(AdaptationTechnology.GRAVITIC, TechLevel(4)),
        ).adaptationRowFor(AdaptationTechnology.GRAVITIC)

        // then - the level, the band it bought, the band level 5 would buy, and level 5's price
        assertEquals(TechLevel(4), row.level)
        assertEquals("0.45 … 1.88", row.effect.current)
        assertEquals("0.40 … 2.00", row.effect.next)
        assertEquals("12,150", row.costs.first { it.kind == ResourceKind.METAL }.amount)
        assertEquals("15h 10m", row.duration)
    }

    @Test
    fun `a ladder at level 0 still shows the band it already tolerates`() {
        // given - the one behavioural difference from an applied row, and it falls out of the model
        val row = adaptable().adaptationRowFor(AdaptationTechnology.GRAVITIC)

        // then - a tolerance band exists at level 0 where a production bonus does not, so unlike
        // Enrichment 0 the left half is never empty
        assertEquals("0.65 … 1.40", row.effect.current)
        assertEquals(TechLevel(0), row.level)
    }

    @Test
    fun `all three ladders sit behind the one gate`() {
        // given a colony past the applied branch's Robotics 1 but not the adaptation branch's 4
        val uiState = colony(buildings = gated()).toResearchUiState(now = EPOCH, timeZone = TimeZone.UTC)

        // then - three gates that differ would decide the first ladder for the player
        assertEquals(
            listOf(
                ResearchActionUiState.Locked("Requires Robotics 4"),
                ResearchActionUiState.Locked("Requires Robotics 4"),
                ResearchActionUiState.Locked("Requires Robotics 4"),
            ),
            uiState.adaptation.map { it.action },
        )
    }

    @Test
    fun `a ladder is priced in the resource its own axis makes rich`() {
        // given
        val row = adaptable().adaptationRowFor(AdaptationTechnology.GRAVITIC)

        // then - the sheet's table: gravity makes heavy worlds and heavy worlds are rich in metal
        assertEquals(
            listOf(
                // A third of the sheet's 2,400 / 900 / 200 at level 1, and still overwhelmingly
                // metal — the discount scales all three alike, so the shape the sheet chose survives.
                CostChipUiState(kind = ResourceKind.METAL, amount = "800", short = false),
                CostChipUiState(kind = ResourceKind.CRYSTAL, amount = "300", short = false),
                CostChipUiState(kind = ResourceKind.DEUTERIUM, amount = "66", short = false),
            ),
            row.costs,
        )
    }

    @Test
    fun `an affordable ladder with a free slot offers to start`() {
        // given
        val row = adaptable().adaptationRowFor(AdaptationTechnology.GRAVITIC)

        // then
        assertEquals(ResearchActionUiState.Start, row.action)
    }

    // The whole mechanic in one assertion: the slot is shared, so a technology in flight is what
    // stops a ladder starting, and the ghost says so with the same number the countdown four rows
    // up is reading.
    @Test
    fun `a ladder waits on the slot while a technology holds it`() {
        // given
        val row = adaptable(activeResearch = project(completesAt = EPOCH + 2.hours))
            .adaptationRowFor(AdaptationTechnology.GRAVITIC)

        // then
        assertEquals(ResearchActionUiState.AvailableIn("in 2h 00m"), row.action)
    }

    @Test
    fun `the running ladder carries its target level countdown and finish time`() {
        // given a ladder half done
        val now = EPOCH + 10.hours
        val row = adaptable(
            activeAdaptation = AdaptationJob(
                technology = AdaptationTechnology.GRAVITIC,
                toLevel = TechLevel(1),
                startedAt = EPOCH + 9.hours,
                completesAt = EPOCH + 11.hours,
            ),
        ).adaptationRowFor(AdaptationTechnology.GRAVITIC, now = now)

        // then - identical to an applied row in flight, and it has to be: from three rows away this
        // is the answer to why nothing else can start
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
    fun `the duration a ladder shows is already divided by the Robotics Factory`() {
        // given the same ladder at two Robotics levels
        val atGate = adaptable().adaptationRowFor(AdaptationTechnology.THERMAL)
        val deeper = adaptable(buildings = gated(robotics = 8)).adaptationRowFor(AdaptationTechnology.THERMAL)

        // then - level 1 carries the opening discount, so 80 base minutes rather than the sheet's
        // 240, then the divisor: 80 x 25/33 at Robotics 4 and 80 x 25/41 at Robotics 8. Both ceil
        // rather than round, because a duration that rounded down would promise a project sooner
        // than it can finish.
        assertEquals("1h 01m", atGate.duration)
        assertEquals("49m", deeper.duration)
    }

    @Test
    fun `an adaptation row says what its next level would unlock on the map`() {
        // given the consumer that stops waiting from beating exploring. `verdictFor` re-derives
        // against current levels and `surveyed` is monotone, so surveying later returns strictly
        // better-labelled rows for the same price — without this line the optimal play is "not
        // yet", and a verb whose optimal play is "not yet" is not a verb.
        val row = adaptable().adaptationRowFor(AdaptationTechnology.GRAVITIC)

        // then
        val shortlist = checkNotNull(row.shortlist)
        assertEquals(shortlistFor(AdaptationTechnology.GRAVITIC).unlocks, shortlist.unlocks)
        assertEquals(shortlistFor(AdaptationTechnology.GRAVITIC).worthTaking, shortlist.worthTaking)
    }

    @Test
    fun `a ladder that would unlock nothing says so rather than going quiet`() {
        // given a colony that has surveyed only its own home system — every empire on day one
        val row = adaptable().adaptationRowFor(AdaptationTechnology.THERMAL)

        // then "Thermal 1 unlocks nothing" is the sentence that makes the other two mean
        // something, so a zero is stated rather than hidden. Pinned against `adaptationShortlist`
        // rather than against a literal, because which of the three is empty is the seed's call.
        val expected = shortlistFor(AdaptationTechnology.THERMAL)
        val shortlist = checkNotNull(row.shortlist)
        assertEquals(expected.unlocks, shortlist.unlocks)
        if (expected.unlocks == 0) assertEquals("Unlocks nothing you have surveyed", shortlist.label)
    }

    @Test
    fun `the honest half is never larger than the whole`() {
        // Most worlds a ladder unlocks are still not worth taking, by construction. A line that
        // sold the count without the qualifier would be selling the ladder on a number the player
        // cannot spend.
        for (row in adaptable().toResearchUiState(now = EPOCH, timeZone = TimeZone.UTC).adaptation) {
            val shortlist = checkNotNull(row.shortlist)
            assertTrue(shortlist.worthTaking <= shortlist.unlocks, "${row.technology}")
        }
    }

    @Test
    fun `an applied technology has no shortlist because it unlocks no world`() {
        // Photovoltaics multiplies a rate. It cannot make a world habitable, so a line about
        // worlds on that row would be the screen inventing a consequence.
        for (row in adaptable().toResearchUiState(now = EPOCH, timeZone = TimeZone.UTC).technologies) {
            assertNull(row.shortlist, "${row.technology}")
        }
    }

    @Test
    fun `the 320dp line drops a word and never a number`() {
        // The same rule the effect line follows: abbreviation may drop a word, never a figure.
        // Both counts are what the player compares across the three ladders, so both survive the
        // narrower window even though the sentence around them does not.
        for (row in adaptable().toResearchUiState(now = EPOCH, timeZone = TimeZone.UTC).adaptation) {
            val shortlist = row.shortlist
            assertTrue(
                shortlist.compactLabel.length < shortlist.label.length,
                "${row.technology} compact '${shortlist.compactLabel}' is no shorter than '${shortlist.label}'",
            )
            if (shortlist.unlocks > 0) {
                assertTrue("${shortlist.unlocks}" in shortlist.compactLabel, "was '${shortlist.compactLabel}'")
                val worth = if (shortlist.worthTaking == 0) "none" else "${shortlist.worthTaking}"
                assertTrue(worth in shortlist.compactLabel, "was '${shortlist.compactLabel}'")
            }
        }
    }

    private fun shortlistFor(technology: AdaptationTechnology) =
        adaptationShortlist(adaptable()).first { it.technology == technology }

    // A colony that has actually been exploring, and the fixture the five tests above were missing.
    // On `adaptable()` only the home system is surveyed, and all three of its blocked worlds fail
    // at least two axes — so every ladder reports 0 unlocks and a mapper hardcoding zeros passed
    // every one of them. This surveys outward until some ladder has something to say.
    private fun exploring(): GameState {
        val seed = freshState().galaxy.seed
        val home = freshState().galaxy.home
        var state = adaptable()
        for (system in 1..GalaxyBalance.SYSTEMS_PER_GALAXY) {
            val at = SystemAddress(galaxy = home.galaxy, system = system)
            state = state.copy(
                galaxy = state.galaxy.copy(
                    surveyed = state.galaxy.surveyed + (1..GalaxyBalance.SLOTS_PER_SYSTEM)
                        .map { GalaxyCoordinate(galaxy = at.galaxy, system = at.system, slot = it) }
                        .filter { worldAt(seed, it) != null },
                ),
            )
            if (adaptationShortlist(state).any { it.unlocks > 0 }) return state
        }
        error("no ladder in this galaxy unlocks anything, however much of it is surveyed")
    }

    @Test
    fun `a ladder with something to unlock states both figures`() {
        // The branch the other five could not reach. Without a colony that has surveyed more than
        // its own back garden, every shortlist is 0 and the non-zero sentence is never rendered.
        val state = exploring()
        val expected = adaptationShortlist(state).first { it.unlocks > 0 }
        val row = state.toResearchUiState(now = EPOCH, timeZone = TimeZone.UTC)
            .adaptation.first { it.technology == expected.technology }

        assertEquals(expected.unlocks, row.shortlist.unlocks)
        assertEquals(expected.worthTaking, row.shortlist.worthTaking)
        assertTrue("${expected.unlocks}" in row.shortlist.label, "was '${row.shortlist.label}'")
        assertTrue(row.shortlist.label.startsWith("Unlocks "), "was '${row.shortlist.label}'")
        // And the 320dp form keeps both figures while dropping the verb — the assertion the
        // all-zero fixture could never make.
        assertTrue("${expected.unlocks}" in row.shortlist.compactLabel, "was '${row.shortlist.compactLabel}'")
        assertTrue(!row.shortlist.compactLabel.startsWith("Unlocks"), "was '${row.shortlist.compactLabel}'")
    }

    @Test
    fun `the honest half is stated even when it is none of them`() {
        // Most worlds a ladder unlocks are still not worth taking, so this is the ordinary shape of
        // the non-zero sentence and it has to say so rather than trailing off after the count.
        val state = exploring()
        val row = state.toResearchUiState(now = EPOCH, timeZone = TimeZone.UTC)
            .adaptation.first { it.shortlist.unlocks > 0 }

        val expected = if (row.shortlist.worthTaking == 0) "none worth" else "${row.shortlist.worthTaking} worth"
        assertTrue(expected in row.shortlist.label, "was '${row.shortlist.label}'")
    }

    private fun GameState.rowFor(technology: Technology, now: Instant = EPOCH): TechnologyRowUiState =
        toResearchUiState(now = now, timeZone = TimeZone.UTC).technologies.first { it.technology == technology }

    private fun GameState.adaptationRowFor(
        technology: AdaptationTechnology,
        now: Instant = EPOCH,
    ): AdaptationRowUiState =
        toResearchUiState(now = now, timeZone = TimeZone.UTC).adaptation.first { it.technology == technology }

    // Past the adaptation gate and able to pay for any of the three at level 1 — the frame the
    // whole decision is for, where four rows can be started and starting one stops the other five.
    private fun adaptable(
        buildings: Buildings = gated(robotics = 4),
        research: Research = Research.initial(),
        activeResearch: ResearchJob? = null,
        activeAdaptation: AdaptationJob? = null,
    ): GameState = colony(
        buildings = buildings,
        research = research,
        resources = Resources.of(metal = 4_000, crystal = 3_000, deuterium = 2_000),
        activeResearch = activeResearch,
        activeAdaptation = activeAdaptation,
    )

    private fun colony(
        buildings: Buildings = Buildings.initial(),
        research: Research = Research.initial(),
        resources: Resources = Resources.of(),
        activeResearch: ResearchJob? = null,
        activeAdaptation: AdaptationJob? = null,
    ): GameState = GameState(
        resources = resources,
        buildings = buildings,
        builds = emptyMap(),
        research = research,
        activeResearch = activeResearch,
        // The other half of the same slot, and never set alongside `activeResearch` — `GameState`
        // refuses that pair. What the rows have to answer for is a slot held by either branch.
        activeAdaptation = activeAdaptation,
        galaxy = freshState().galaxy,
        // Probes hold no research slot and never will: the scarcity a ladder competes for is the
        // one slot, and the scarcity a probe competes for is metal.
        surveys = emptyList(),
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

    // The same slot, held by the branch this screen does not render.
    private fun ladder(completesAt: Instant): AdaptationJob = AdaptationJob(
        technology = AdaptationTechnology.GRAVITIC,
        toLevel = TechLevel(1),
        startedAt = EPOCH,
        completesAt = completesAt,
    )

    private companion object {
        val EPOCH = Instant.fromEpochMilliseconds(0)
    }
}
