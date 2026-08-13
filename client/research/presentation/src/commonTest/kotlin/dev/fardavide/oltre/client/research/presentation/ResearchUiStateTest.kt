package dev.fardavide.oltre.client.research.presentation

import dev.fardavide.oltre.client.design.component.CostChipUiState
import dev.fardavide.oltre.client.design.component.SheetAction
import dev.fardavide.oltre.client.design.component.SheetFooter
import dev.fardavide.oltre.client.design.component.SheetLadderStep
import dev.fardavide.oltre.client.design.component.SheetLinePart
import dev.fardavide.oltre.client.design.component.SheetPointer
import dev.fardavide.oltre.client.design.component.VerdictUiState
import dev.fardavide.oltre.client.design.component.WatchUiState
import dev.fardavide.oltre.client.design.component.figure
import dev.fardavide.oltre.client.design.component.sheetLine
import dev.fardavide.oltre.client.design.component.words
import dev.fardavide.oltre.client.design.format.pad2
import dev.fardavide.oltre.client.design.format.toChipLabel
import dev.fardavide.oltre.client.design.format.toPaybackLabel
import dev.fardavide.oltre.core.AdaptationJob
import dev.fardavide.oltre.core.AdaptationTechnology
import dev.fardavide.oltre.core.BuildingLevel
import dev.fardavide.oltre.core.BuildingType
import dev.fardavide.oltre.core.Buildings
import dev.fardavide.oltre.core.GalaxyBalance
import dev.fardavide.oltre.core.GalaxyCoordinate
import dev.fardavide.oltre.core.GalaxySeed
import dev.fardavide.oltre.core.GameState
import dev.fardavide.oltre.core.LevelPurpose
import dev.fardavide.oltre.core.PlaceholderBalance
import dev.fardavide.oltre.core.Research
import dev.fardavide.oltre.core.ResearchBalance
import dev.fardavide.oltre.core.ResearchJob
import dev.fardavide.oltre.core.ResourceKind
import dev.fardavide.oltre.core.Resources
import dev.fardavide.oltre.core.Ships
import dev.fardavide.oltre.core.SystemAddress
import dev.fardavide.oltre.core.TechLevel
import dev.fardavide.oltre.core.Technology
import dev.fardavide.oltre.core.WatchTarget
import dev.fardavide.oltre.core.adaptationShortlist
import dev.fardavide.oltre.core.purposeOfNextLevel
import dev.fardavide.oltre.core.timeUntilAffordable
import dev.fardavide.oltre.core.worldAt
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

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

    // The subject is what the sheet's first sentence is *about* — "metal · crystal output: +17% →
    // +26%." — so it survived the verdict taking the row's line. The short form did not: it existed
    // because 320dp could not fit the trailing noun beside a watch square, and the sheet is full
    // width with no square on it.
    @Test
    fun `each technology names what it multiplies`() {
        // given
        val uiState = colony().toResearchUiState(now = EPOCH, timeZone = TimeZone.UTC)

        // then
        assertEquals("Solar Plant output", uiState.technologies[0].effect.subject)
        assertEquals("metal · crystal output", uiState.technologies[1].effect.subject)
        assertEquals("deuterium output", uiState.technologies[2].effect.subject)
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
                // Photovoltaics 1 at a tenth of the sheet's 300 / 150 / 100 — the opening
                // discount. The stock below still covers two of the three and not the deuterium,
                // which is what this test is about.
                CostChipUiState(kind = ResourceKind.METAL, amount = "30", short = false),
                CostChipUiState(kind = ResourceKind.CRYSTAL, amount = "15", short = false),
                CostChipUiState(kind = ResourceKind.DEUTERIUM, amount = "10", short = true),
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
        // given 20 metal short at 90 an hour — 13m 20s, ceiled to 14 — against a slot that frees
        // in five minutes. Photovoltaics 1 costs 30 metal since the opening discount went to a
        // tenth, so it is the *slot* that had to shrink to keep the money the further of the two,
        // which is the thing this test is about.
        val row = colony(
            buildings = gated(),
            resources = Resources.of(metal = 10, crystal = 150, deuterium = 100),
            activeResearch = project(completesAt = EPOCH + 5.minutes),
        ).rowFor(Technology.PHOTOVOLTAICS)

        // then
        assertEquals(ResearchActionUiState.AvailableIn("in 14m"), row.action)
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

        // then - level 1 is the deepest step of the opening discount, and since that discount went
        // to 10x it is 6 minutes rather than the sheet's 60, then the divisor: 6 x 25/27 at
        // Robotics 1 and 6 x 25/33 at Robotics 4, each ceiled to the minute the chip shows.
        assertEquals("6m", slow.duration)
        assertEquals("5m", quick.duration)
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
        assertEquals(EffectUiState("−30 … +45", "−44 … +59", "°C"), rows[0].effect)
        assertEquals(EffectUiState("0.65 … 1.40", "0.60 … 1.52", "g"), rows[1].effect)
        assertEquals(EffectUiState("0.5 … 2.6", "0.44 … 3.5", "atm"), rows[2].effect)
    }

    // A ladder's subject is its bare unit rather than a phrase, which is why the sheet's band
    // sentence reads "°C tolerance: −30 … +45 → −44 … +59." with nothing in it to shorten. The
    // applied branch's subject is prose and its own assertion is above.
    @Test
    fun `a ladder names the unit its band is measured in`() {
        // given
        val rows = adaptable().toResearchUiState(now = EPOCH, timeZone = TimeZone.UTC).adaptation

        // then
        assertEquals(listOf("°C", "g", "atm"), rows.map { it.effect.subject })
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
        // given a colony standing on the applied branch's Robotics 1 but not the adaptation
        // branch's 2 — the gate came down from 4 at 0.5.1, and the window this test describes is
        // one Robotics level wide now rather than three
        val uiState = colony(buildings = gated()).toResearchUiState(now = EPOCH, timeZone = TimeZone.UTC)

        // then - three gates that differ would decide the first ladder for the player
        assertEquals(
            listOf(
                ResearchActionUiState.Locked("Requires Robotics 2"),
                ResearchActionUiState.Locked("Requires Robotics 2"),
                ResearchActionUiState.Locked("Requires Robotics 2"),
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
                // A tenth of the sheet's 2,400 / 900 / 200 at level 1, and still overwhelmingly
                // metal — the discount scales all three alike, so the shape the sheet chose survives.
                CostChipUiState(kind = ResourceKind.METAL, amount = "240", short = false),
                CostChipUiState(kind = ResourceKind.CRYSTAL, amount = "90", short = false),
                CostChipUiState(kind = ResourceKind.DEUTERIUM, amount = "20", short = false),
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

        // then - level 1 carries the opening discount, so 24 base minutes rather than the sheet's
        // 240, then the divisor: 24 x 25/33 at Robotics 4 and 24 x 25/41 at Robotics 8. Both ceil
        // rather than round, because a duration that rounded down would promise a project sooner
        // than it can finish.
        assertEquals("19m", atGate.duration)
        assertEquals("15m", deeper.duration)
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
    fun `an applied technology never states a consequence on the map`() {
        // Photovoltaics multiplies a rate. It cannot make a world habitable, so a line about
        // worlds on that row would be the screen inventing a consequence — which is why the applied
        // branch's verdict is priced in units an hour and never in worlds.
        for (row in adaptable().toResearchUiState(now = EPOCH, timeZone = TimeZone.UTC).technologies) {
            assertTrue("world" !in checkNotNull(row.verdict).label, "${row.technology}")
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

    @Test
    fun `the project that finished while the app was closed is the only row that sweeps`() {
        // given
        val state = adaptable()

        // when
        val uiState = state.toResearchUiState(
            now = EPOCH,
            timeZone = TimeZone.UTC,
            finishedWhileAway = FinishedWhileAway.Project(Technology.EXTRACTION),
        )

        // then nothing on the other branch answers for a technology that finished
        assertEquals(
            listOf(Technology.EXTRACTION),
            uiState.technologies.filter { it.finishedWhileAway }.map { it.technology },
        )
        assertEquals(emptyList(), uiState.adaptation.filter { it.finishedWhileAway })
    }

    @Test
    fun `a ladder that finished while the app was closed sweeps on its own branch`() {
        // given
        val state = adaptable()

        // when
        val uiState = state.toResearchUiState(
            now = EPOCH,
            timeZone = TimeZone.UTC,
            finishedWhileAway = FinishedWhileAway.Ladder(AdaptationTechnology.GRAVITIC),
        )

        // then
        assertEquals(
            listOf(AdaptationTechnology.GRAVITIC),
            uiState.adaptation.filter { it.finishedWhileAway }.map { it.technology },
        )
        assertEquals(emptyList(), uiState.technologies.filter { it.finishedWhileAway })
    }

    @Test
    fun `a launch that found nothing finished sweeps no project and no ladder`() {
        // given
        val state = adaptable()

        // when
        val uiState = state.toResearchUiState(now = EPOCH, timeZone = TimeZone.UTC)

        // then
        assertEquals(emptyList(), uiState.technologies.filter { it.finishedWhileAway })
        assertEquals(emptyList(), uiState.adaptation.filter { it.finishedWhileAway })
    }

    @Test
    fun `a row the empire cannot pay for offers a square`() {
        // given past the gate and 20 metal short of Photovoltaics 1
        val row = colony(
            buildings = gated(),
            resources = Resources.of(metal = 10, crystal = 150, deuterium = 100),
        ).rowFor(Technology.PHOTOVOLTAICS)

        // then
        assertEquals(WatchUiState.Offered, row.watch)
    }

    @Test
    fun `a row held up only by the slot offers none`() {
        // given everything paid for and a project two hours from done. **The ghost still reads a
        // wait**, and that is the point: the square answers a narrower question than the ghost does,
        // because the resources are already in the stores and there is nothing to be told about.
        val row = colony(
            buildings = gated(),
            resources = Resources.of(metal = 300, crystal = 150, deuterium = 100),
            activeResearch = project(completesAt = EPOCH + 2.hours),
        ).rowFor(Technology.PHOTOVOLTAICS)

        // then
        assertIs<ResearchActionUiState.AvailableIn>(row.action)
        assertEquals(null, row.watch)
    }

    @Test
    fun `a row behind its requirement has no price yet and so no square`() {
        // given a colony with no Robotics Factory
        val row = colony().rowFor(Technology.PHOTOVOLTAICS)

        // then
        assertEquals(null, row.watch)
    }

    // **This reverses what 0.5 asserted here.** A running row used to offer nothing, because the
    // square was only ever about a price. Since completions went opt-in it is the one row where the
    // square is the whole of how the player hears about anything.
    @Test
    fun `a running row offers its square for the completion instead`() {
        // given the project the row itself is about — `project`'s default is deliberately another
        // technology, so it has to be named here
        val running = colony(
            buildings = gated(),
            activeResearch = project(completesAt = EPOCH + 2.hours, technology = Technology.PHOTOVOLTAICS),
        )

        // then
        assertEquals(WatchUiState.Offered, running.rowFor(Technology.PHOTOVOLTAICS).watch)
        assertEquals(
            WatchUiState.Subscribed,
            running.copy(subscribed = setOf(WatchTarget.Project(Technology.PHOTOVOLTAICS)))
                .rowFor(Technology.PHOTOVOLTAICS).watch,
        )
    }

    @Test
    fun `a running ladder offers its square the same way a running technology does`() {
        // given — the two branches share one composable, so they have to share the rule too
        val running = colony(
            buildings = gated(robotics = 4),
            activeAdaptation = ladder(completesAt = EPOCH + 2.hours),
        )

        // then — `ladder` runs Gravitic
        assertEquals(WatchUiState.Offered, running.adaptationRowFor(AdaptationTechnology.GRAVITIC).watch)
    }

    @Test
    fun `the watched technology names the instant it becomes affordable`() {
        // given the watch on a row the empire is short for
        val state = colony(
            buildings = gated(),
            resources = Resources.of(metal = 10, crystal = 150, deuterium = 100),
            watching = WatchTarget.Project(Technology.PHOTOVOLTAICS),
        )

        // when
        val row = state.rowFor(Technology.PHOTOVOLTAICS)

        // then — the same clock time the alert will be stamped with
        val wait = timeUntilAffordable(
            state.resources,
            ResearchBalance.researchCost(Technology.PHOTOVOLTAICS, TechLevel(1)),
            state.buildings,
            state.research,
        )
        val expected = (EPOCH + wait).toLocalDateTime(TimeZone.UTC)
        assertEquals(
            "→ affordable ${expected.hour.pad2()}:${expected.minute.pad2()}",
            assertIs<WatchUiState.Booked>(row.watch).affordableAt,
        )
    }

    @Test
    fun `a ladder is watched by the same rule as a technology`() {
        // given past the adaptation gate with nothing in the stores
        val state = colony(
            buildings = gated(robotics = 4),
            watching = WatchTarget.Ladder(AdaptationTechnology.THERMAL),
        )

        // then — one slot, so the other two ladders offer and only this one is booked
        assertIs<WatchUiState.Booked>(state.adaptationRowFor(AdaptationTechnology.THERMAL).watch)
        assertEquals(WatchUiState.Offered, state.adaptationRowFor(AdaptationTechnology.GRAVITIC).watch)
    }

    @Test
    fun `the heading names what the empire is watching whatever screen it is on`() {
        // given — handed in, because research cannot name a facility
        val uiState = colony().toResearchUiState(
            now = EPOCH,
            timeZone = TimeZone.UTC,
            watching = "watching Metal Mine",
        )

        // then
        assertEquals("watching Metal Mine", uiState.watching)
    }

    // ── The verdict ──────────────────────────────────────────────────────────────────────────
    //
    // One line saying what the next level is worth to *this* empire now, in the slot the effect
    // line used to have. The numbers come from `purposeOfNextLevel` rather than from a literal,
    // because what this screen owns is the sentence around them and the balance owns the figures.

    @Test
    fun `a row states what the next level adds and when it has paid for itself`() {
        // given
        val state = colony(buildings = gated())
        val purpose = assertIs<LevelPurpose.Output>(state.purposeOfNextLevel(Technology.EXTRACTION))

        // when
        val row = state.rowFor(Technology.EXTRACTION)

        // then - two clauses, and the second is the one a narrow window drops rather than truncates
        assertEquals(
            VerdictUiState(
                label = "+${purpose.perHour}/h metal · back in ${purpose.payback.toPaybackLabel()}",
                compactLabel = "+${purpose.perHour}/h metal",
            ),
            row.verdict,
        )
    }

    @Test
    fun `the resource a level is recognisably for is the one it raises most`() {
        // Extraction moves metal and crystal together; the row has one clause to say it in, so the
        // larger of the two gains is the one the level is named after.
        val state = colony(buildings = gated())

        // then
        val metal = checkNotNull(state.rowFor(Technology.EXTRACTION).verdict).compactLabel
        val deuterium = checkNotNull(state.rowFor(Technology.ENRICHMENT).verdict).compactLabel
        assertTrue(metal.endsWith(" metal"), "was '$metal'")
        assertTrue(deuterium.endsWith(" deuterium"), "was '$deuterium'")
    }

    @Test
    fun `Photovoltaics is worth nothing at all while the colony is in surplus`() {
        // given a colony making 50 energy against 40 drawn - a plant level and a technology that
        // multiplies plants both raise a supply nothing is limited by
        val row = colony(buildings = gated()).rowFor(Technology.PHOTOVOLTAICS)

        // then - it says so rather than going quiet, which is the whole point of a verdict
        assertEquals(
            VerdictUiState(label = "nothing while you are in surplus", compactLabel = "nothing while in surplus"),
            row.verdict,
        )
    }

    @Test
    fun `a row in flight carries no verdict because nobody is choosing`() {
        // given the project the row itself is about
        val row = colony(
            buildings = gated(),
            activeResearch = project(completesAt = EPOCH + 2.hours, technology = Technology.PHOTOVOLTAICS),
        ).rowFor(Technology.PHOTOVOLTAICS)

        // then - the decision was made when the player tapped the action; the slot belongs to the
        // finish line and the countdown
        assertNull(row.verdict)
    }

    @Test
    fun `a ladder takes the shortlist it already had as its verdict`() {
        // The shortlist *was* the verdict, and this design is that sentence repeated elsewhere - so
        // nothing about the copy changes on the branch that already had it.
        val row = adaptable().adaptationRowFor(AdaptationTechnology.GRAVITIC)

        // then
        assertEquals(
            VerdictUiState(label = row.shortlist.label, compactLabel = row.shortlist.compactLabel),
            row.verdict,
        )
    }

    @Test
    fun `a ladder in flight carries no verdict either`() {
        // given - the two branches share one composable, so they share the rule
        val row = adaptable(activeAdaptation = ladder(completesAt = EPOCH + 2.hours))
            .adaptationRowFor(AdaptationTechnology.GRAVITIC)

        // then
        assertNull(row.verdict)
    }

    // ── The sheet ────────────────────────────────────────────────────────────────────────────
    //
    // What the card body opens: the arithmetic behind the verdict, the ladder of what the level
    // gates, and the numbers the verdict displaced.

    @Test
    fun `the sheet names the row and repeats the sentence the player just read`() {
        // given a row past its gate and with no ladder under it - the plain case, where the sheet
        // repeats the verdict whole
        val row = colony(buildings = gated()).rowFor(Technology.PHOTOVOLTAICS)

        // then
        assertEquals("Photovoltaics", row.sheet.name)
        assertEquals(0, row.sheet.level)
        assertEquals(checkNotNull(row.verdict).label, row.sheet.verdict)
    }

    @Test
    fun `a sheet whose ladder already states the second clause does not state it twice`() {
        // given Extraction - the one applied row that gates something, so the one whose sheet
        // carries a ladder at all
        val row = colony(buildings = gated()).rowFor(Technology.EXTRACTION)

        // then
        assertEquals(checkNotNull(row.verdict).compactLabel, row.sheet.verdict)
    }

    @Test
    fun `the sheet spells out the two rates the verdict was a difference between`() {
        // given
        val state = colony(
            buildings = gated(),
            research = Research.initial().withLevel(Technology.EXTRACTION, TechLevel(2)),
        )
        val purpose = assertIs<LevelPurpose.Output>(state.purposeOfNextLevel(Technology.EXTRACTION))

        // when
        val row = state.rowFor(Technology.EXTRACTION)

        // then
        assertEquals(
            listOf(
                sheetLine(
                    words("metal · crystal output: "),
                    figure("+17%"),
                    words(" → "),
                    figure("+26%"),
                    words("."),
                ),
                sheetLine(
                    words("Your colony makes "),
                    figure("${purpose.from}/h"),
                    words(" metal and would make "),
                    figure("${purpose.to}/h"),
                    words("."),
                ),
                sheetLine(
                    words("Counted against everything the level costs, you are even after "),
                    figure(purpose.payback.toPaybackLabel()),
                    words("."),
                ),
            ),
            row.sheet.lines,
        )
    }

    @Test
    fun `a technology nobody has researched states what its first level would be worth`() {
        // given - there is no "now" to compare against, so the sentence names the level instead
        val row = colony(buildings = gated()).rowFor(Technology.ENRICHMENT)

        // then
        assertEquals(
            sheetLine(words("deuterium output: "), figure("+14%"), words(" at LV 1.")),
            row.sheet.lines.first(),
        )
    }

    @Test
    fun `an inert sheet states supply against draw and what would change it`() {
        // given a colony one mine level away from needing more power
        val row = colony(buildings = gated()).rowFor(Technology.PHOTOVOLTAICS)

        // then
        assertEquals(
            listOf(
                sheetLine(
                    words("Your plants supply "),
                    figure("50"),
                    words(" energy. The colony draws "),
                    figure("40"),
                    words("."),
                ),
                sheetLine(
                    words("Photovoltaics multiplies supply, and supply is not what is limiting you. At "),
                    figure("+10%"),
                    words(" your output does not move."),
                ),
                sheetLine(
                    words("It starts to pay when draw passes supply — about "),
                    figure("one"),
                    words(" more mine level away."),
                ),
            ),
            row.sheet.lines,
        )
    }

    @Test
    fun `the crossing sentence counts the mine levels it is away`() {
        // given a colony with a second plant - 100 supplied against 40 drawn is six mine levels of
        // headroom, in the unit the power indicator already reports it in
        val row = colony(buildings = gated().withLevel(BuildingType.SOLAR_PLANT, BuildingLevel(2)))
            .rowFor(Technology.PHOTOVOLTAICS)

        // then
        assertEquals(
            sheetLine(
                words("It starts to pay when draw passes supply — about "),
                figure("6"),
                words(" more mine levels away."),
            ),
            row.sheet.lines.last(),
        )
    }

    @Test
    fun `a colony with no headroom left is told the next mine level is the crossing`() {
        // given 50 supplied against 50 drawn - level and draw are equal so nothing is throttled yet
        val row = colony(buildings = gated().withLevel(BuildingType.METAL_MINE, BuildingLevel(2)))
            .rowFor(Technology.PHOTOVOLTAICS)

        // then - a count of zero would be arithmetic where the sentence wants a consequence
        assertEquals(
            sheetLine(words("It starts to pay with the next mine level you take.")),
            row.sheet.lines.last(),
        )
    }

    @Test
    fun `an inert row points at the shortest payback on the screen`() {
        // given
        val state = colony(buildings = gated())
        val best = Technology.entries
            .mapNotNull { technology ->
                (state.purposeOfNextLevel(technology) as? LevelPurpose.Output)?.let { technology to it }
            }
            .minBy { (_, output) -> output.payback }

        // when
        val row = state.rowFor(Technology.PHOTOVOLTAICS)

        // then - the row to buy instead, at the level the player would buy and the payback it has
        assertEquals(
            SheetPointer(
                name = best.first.displayName(),
                detail = "LV 1 · back in ${best.second.payback.toPaybackLabel()}",
            ),
            row.sheet.pointer,
        )
    }

    @Test
    fun `a row that is worth something points at nothing`() {
        // The pointer is what an inert row has instead of an argument. A row with a rate to state
        // has the argument.
        assertNull(colony(buildings = gated()).rowFor(Technology.EXTRACTION).sheet.pointer)
    }

    @Test
    fun `the sheet of a locked row keeps its first sentence and ends on what it requires`() {
        // given a colony with no Robotics Factory
        val row = colony().rowFor(Technology.PHOTOVOLTAICS)

        // then - the requirement is a figure, because it is the thing the player has to go and get
        assertEquals(
            listOf(
                sheetLine(
                    words("Your plants supply "),
                    figure("50"),
                    words(" energy. The colony draws "),
                    figure("40"),
                    words("."),
                ),
                sheetLine(words("Requires "), figure("Robotics 1"), words(".")),
            ),
            row.sheet.lines,
        )
        assertEquals("Requires Robotics 1", row.sheet.verdict)
        assertNull(row.sheet.footer)
    }

    @Test
    fun `a locked row points at the row that moves the gate`() {
        // given
        val state = colony()

        // when
        val row = state.rowFor(Technology.PHOTOVOLTAICS)

        // then - the current level to the next one it would reach, and that upgrade's own wait
        assertEquals(
            SheetPointer(
                name = "Robotics Factory",
                detail = "LV 0 → 1 · " + PlaceholderBalance.upgradeDuration(
                    building = BuildingType.ROBOTICS_FACTORY,
                    toLevel = BuildingLevel(1),
                    roboticsFactory = BuildingLevel(0),
                    naniteFactory = BuildingLevel(0),
                ).toChipLabel(),
            ),
            row.sheet.pointer,
        )
    }

    @Test
    fun `a row behind a technology rather than a facility points at nothing`() {
        // Enrichment waits on Extraction 3 - three rows up the same screen, and an arrow to
        // somewhere the thumb is already resting is noise.
        assertNull(colony(buildings = gated()).rowFor(Technology.ENRICHMENT).sheet.pointer)
    }

    @Test
    fun `the sheet of a running row says one sentence and offers nothing`() {
        // given
        val row = colony(
            buildings = gated(),
            activeResearch = project(completesAt = EPOCH + 2.hours, technology = Technology.EXTRACTION),
        ).rowFor(Technology.EXTRACTION)

        // then - mid-project the question is when rather than what
        assertEquals(1, row.sheet.lines.size)
        assertNull(row.sheet.footer)
        assertNull(row.sheet.pointer)
        assertEquals("→ LV 1 · done 02:00", row.sheet.verdict)
    }

    @Test
    fun `the sheet carries the ladder of what each level opens`() {
        // given Extraction at level 4 - past the level that opens Enrichment
        val row = colony(
            buildings = gated(),
            research = Research.initial().withLevel(Technology.EXTRACTION, TechLevel(4)),
        ).rowFor(Technology.EXTRACTION)

        // then - the level already held is on the ladder too; it is how you learn that gating is a
        // thing this technology does at all
        assertEquals(
            listOf(SheetLadderStep(level = "LV 3", opens = "Enrichment · you have this", held = true)),
            row.sheet.ladder,
        )
    }

    @Test
    fun `a level not yet reached is on the ladder without the aside`() {
        // given
        val row = colony(buildings = gated()).rowFor(Technology.EXTRACTION)

        // then
        assertEquals(
            listOf(SheetLadderStep(level = "LV 3", opens = "Enrichment", held = false)),
            row.sheet.ladder,
        )
    }

    @Test
    fun `a technology that gates nothing carries no ladder`() {
        assertEquals(emptyList(), colony(buildings = gated()).rowFor(Technology.PHOTOVOLTAICS).sheet.ladder)
    }

    @Test
    fun `a sheet whose row can start carries the row's price and its action`() {
        // given
        val row = colony(
            buildings = gated(),
            resources = Resources.of(metal = 300, crystal = 150, deuterium = 100),
        ).rowFor(Technology.PHOTOVOLTAICS)

        // then - the sheet is somewhere a decision can be made rather than somewhere you read
        // about one and then go back
        assertEquals(
            SheetFooter(costs = row.costs, duration = row.duration, action = SheetAction.Live("Research")),
            row.sheet.footer,
        )
    }

    @Test
    fun `a sheet whose row is waiting carries the wait rather than a button`() {
        // given 20 metal short
        val row = colony(
            buildings = gated(),
            resources = Resources.of(metal = 10, crystal = 150, deuterium = 100),
        ).rowFor(Technology.PHOTOVOLTAICS)

        // then - no disabled state here either: a player who wants the level they cannot afford is
        // told when, not told no
        assertEquals(
            SheetFooter(costs = row.costs, duration = row.duration, action = SheetAction.Ghost("in 14m")),
            row.sheet.footer,
        )
    }

    @Test
    fun `an adaptation sheet states the band it widens and what that reaches`() {
        // given a ladder with something to unlock
        val state = exploring()
        val expected = adaptationShortlist(state).first { it.unlocks > 0 }
        val row = state.toResearchUiState(now = EPOCH, timeZone = TimeZone.UTC)
            .adaptation.first { it.technology == expected.technology }

        // then
        assertEquals(
            listOf(
                sheetLine(
                    words("${row.effect.subject} tolerance: "),
                    figure(checkNotNull(row.effect.current)),
                    words(" → "),
                    figure(row.effect.next),
                    words("."),
                ),
                sheetLine(
                    words("Of the worlds you have surveyed this level reaches "),
                    figure("${expected.unlocks}"),
                    words(", and "),
                    figure("${expected.worthTaking}"),
                    words(" of those are worth taking."),
                ),
            ),
            row.sheet.lines,
        )
    }

    @Test
    fun `a ladder that reaches nothing says so rather than counting zero`() {
        // given a colony that has surveyed only its own home system
        val row = adaptable().adaptationRowFor(AdaptationTechnology.THERMAL)

        // then
        assertEquals(
            sheetLine(
                words(
                    "Nothing you have surveyed is blocked by this band alone, " +
                        "so this level reaches no new world.",
                ),
            ),
            row.sheet.lines.last(),
        )
    }

    @Test
    fun `a locked ladder points at the Robotics Factory that opens all three`() {
        // given a colony standing on the applied branch's gate but not the adaptation branch's
        val row = colony(buildings = gated()).adaptationRowFor(AdaptationTechnology.THERMAL)

        // then
        assertEquals("Requires Robotics 2", row.sheet.verdict)
        assertEquals("Robotics Factory", checkNotNull(row.sheet.pointer).name)
        assertNull(row.sheet.footer)
    }

    private fun GameState.rowFor(technology: Technology, now: Instant = EPOCH): TechnologyRowUiState =
        toResearchUiState(now = now, timeZone = TimeZone.UTC).technologies.first { it.technology == technology }

    private fun GameState.adaptationRowFor(
        technology: AdaptationTechnology,
        now: Instant = EPOCH,
    ): AdaptationRowUiState =
        toResearchUiState(now = now, timeZone = TimeZone.UTC).adaptation.first { it.technology == technology }

    // The first reading of "a project can be worth nothing" was that only Photovoltaics could be,
    // and only in surplus. Both halves are false: `scaleByEnergy` floors `rate x produced /
    // consumed`, so a bad enough ratio swallows a whole multiplier step and *any* of the three
    // lands there — in a deficit, which is the opposite of what the surplus copy claims.
    @Test
    fun `a project whose gain the deficit swallows says so rather than the opposite`() {
        // given a colony running its mines at a fraction of full output
        val state = colony(
            buildings = Buildings(
                metalMine = BuildingLevel(20),
                crystalMine = BuildingLevel(27),
                deuteriumSynthesizer = BuildingLevel(1),
                solarPlant = BuildingLevel(4),
                roboticsFactory = BuildingLevel(1),
                naniteFactory = BuildingLevel(0),
            ),
            research = Research.initial().withLevel(Technology.EXTRACTION, TechLevel(3)),
        )

        // when
        val row = state.rowFor(Technology.ENRICHMENT)

        // then it names the throttle rather than a surplus the colony does not have
        assertEquals(
            VerdictUiState(
                label = "nothing while your mines are throttled",
                compactLabel = "nothing while throttled",
            ),
            row.verdict,
        )

        // and the sheet names the row itself rather than the one technology this case used to
        // assume, and states the ratio that is eating the level
        val prose = row.sheet.lines.flatMap { it.parts }.joinToString("") {
            when (it) {
                is SheetLinePart.Words -> it.text
                is SheetLinePart.Figure -> it.text
            }
        }
        assertTrue(prose.contains("every mine is running at"), prose)
        assertTrue(prose.contains("Enrichment"), prose)
        assertFalse(prose.contains("Photovoltaics"), prose)
    }

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
        watching: WatchTarget? = null,
        subscribed: Set<WatchTarget> = emptySet(),
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
        // The same holds for the fleet, and one line further: an idle hull and a run in flight both
        // compete for metal, and neither can hold the slot this screen is entirely about.
        ships = Ships.NONE,
        runs = emptyList(),
        // The slipway, which this screen draws none of: a hull competes for metal and crystal and
        // never for the one research slot.
        yard = emptyList(),
        // The one slot the watch holds, which this screen shares with the colony's — a parameter,
        // because `watch` on a row is derived from it.
        watching = watching,
        subscribed = subscribed,
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
