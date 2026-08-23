package dev.fardavide.oltre.client.player.presentation

import dev.fardavide.oltre.client.design.text.English
import dev.fardavide.oltre.client.design.text.Italian
import dev.fardavide.oltre.client.design.text.Strings
import dev.fardavide.oltre.core.BuildingLevel
import dev.fardavide.oltre.core.BuildingType
import dev.fardavide.oltre.core.Event
import dev.fardavide.oltre.core.ExperienceBalance
import dev.fardavide.oltre.core.GalaxySeed
import dev.fardavide.oltre.core.GameState
import dev.fardavide.oltre.core.PlayerLevel
import dev.fardavide.oltre.core.Resources
import dev.fardavide.oltre.core.ShipType
import dev.fardavide.oltre.core.Ships
import dev.fardavide.oltre.core.SystemAddress
import dev.fardavide.oltre.core.TechLevel
import dev.fardavide.oltre.core.Technology
import dev.fardavide.oltre.core.experienceOf
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.hours
import kotlin.time.Instant

class PlayerStripFromStateTest {

    @Test
    fun `should open a new colony at level zero on an empty gauge`() {
        val uiState = genesis().toPlayerStripUiState()

        assertEquals(Strings.levelBadge(0), uiState.level)
        assertEquals(0, uiState.experiencePercent)
    }

    @Test
    fun `should read the level off the log rather than off a stored field`() {
        // The whole design in one assertion: nothing on `GameState` holds a level, so a colony that
        // has finished twenty facility levels is at whatever those twenty completions are worth.
        val played = genesis().copy(eventLog = builds(count = 20))
        val expected = ExperienceBalance.levelFor(experienceOf(played.eventLog))

        assertEquals(Strings.levelBadge(expected.value), played.toPlayerStripUiState().level)
        assertTrue(expected.value > 0, "twenty facility levels were worth no level at all")
    }

    @Test
    fun `should credit a colony played before the level system existed`() {
        // Davide, 2026-08-22: *"make it so next time I start the game it gives me experience for
        // everything I did before."* A save written by 0.16 carries this log and no experience field
        // at all, and this is what it opens on.
        val carriedForward = genesis().copy(eventLog = aWeekOfPlay())

        val uiState = carriedForward.toPlayerStripUiState()

        assertTrue(
            uiState.level != Strings.levelBadge(0),
            "a colony with a week of history opened at level 0",
        )
    }

    @Test
    fun `should fill the gauge as a share of the level being served`() {
        // Half of level 0's span, which is a reading a player can check against the badge next to it:
        // the bar is how far through *this* level you are, not how far through the game.
        val halfway = ExperienceBalance.spanOf(PlayerLevel(0)).points / 2
        val state = genesis().copy(eventLog = worthAbout(halfway))

        val percent = state.toPlayerStripUiState().experiencePercent

        assertEquals(Strings.levelBadge(0), state.toPlayerStripUiState().level)
        assertTrue(percent in 40..60, "the gauge read $percent% halfway through the first level")
    }

    @Test
    fun `should never hand the gauge a value the strip has to coerce`() {
        // `PlayerStripUiState` documents that the percent is coerced where it is drawn rather than
        // where it is built. That is the strip being defensive; this is the mapper making sure it
        // never has to be.
        for (levels in 0..40) {
            val percent = genesis().copy(eventLog = builds(count = levels)).toPlayerStripUiState().experiencePercent
            assertTrue(percent in 0..99, "$levels builds put the gauge at $percent%")
        }
    }

    @Test
    fun `should take the name from the catalogue rather than from a literal`() {
        // The assertion is `Strings.playerDefaultName()` and not the string it resolves to, on the
        // rule `TextRes` exists for: a test asserts on meaning, so this keeps passing when the
        // wording changes and fails when the *message* does.
        assertEquals(Strings.playerDefaultName(), genesis().toPlayerStripUiState().name)
    }

    @Test
    fun `should give the name words in both languages`() {
        // Italian keeps the English callsign deliberately — see `Italian.kt` — and this is what
        // makes that a decision rather than a gap somebody forgot to fill.
        val name = genesis().toPlayerStripUiState().name

        assertEquals("Dead Reckoning", English.resolve(name))
        assertEquals("Dead Reckoning", Italian.resolve(name))
    }

    @Test
    fun `should not name the player after the galaxy they were dealt`() {
        // `player-strip-sheet.md` §3 retired the seeded name: an identity the save cannot back is a
        // fact about a random number wearing a person's clothes. Two colonies in different galaxies
        // are called the same thing until somebody chooses otherwise.
        val here = GameState.initial(GalaxySeed(1)).toPlayerStripUiState()
        val elsewhere = GameState.initial(GalaxySeed(999_983)).toPlayerStripUiState()

        assertEquals(here.name, elsewhere.name)
    }

    private fun genesis(): GameState = GameState.initial(GalaxySeed(20_260_807))

    private fun builds(count: Int): List<Event> = (1..count).map { level ->
        Event.BuildCompleted(BuildingType.METAL_MINE, BuildingLevel(level), at = EPOCH + level.hours)
    }

    // Enough completions to be worth roughly `points`, built out of the cheapest award there is so
    // the arithmetic lands close rather than in steps of a facility level.
    private fun worthAbout(points: Long): List<Event> {
        val each = ExperienceBalance.HULL
        return List((points / each).toInt()) { index ->
            Event.ShipsBuilt(Ships.of(ShipType.SKIFF, 1), at = EPOCH + index.hours)
        }
    }

    private fun aWeekOfPlay(): List<Event> = buildList {
        for (level in 1..8) {
            add(Event.BuildStarted(BuildingType.METAL_MINE, BuildingLevel(level), at = EPOCH + level.hours))
            add(Event.BuildCompleted(BuildingType.METAL_MINE, BuildingLevel(level), at = EPOCH + level.hours))
        }
        add(Event.ResearchCompleted(Technology.EXTRACTION, TechLevel(1), at = EPOCH + 9.hours))
        add(Event.SurveyCompleted(SystemAddress(galaxy = 1, system = 4), worldsFound = 3, at = EPOCH + 10.hours))
        add(
            Event.FleetReturned(
                from = null,
                ships = Ships.of(ShipType.SKIFF, 1),
                cargo = Resources.of(metal = 900),
                at = EPOCH + 11.hours,
            ),
        )
    }

    private companion object {
        val EPOCH: Instant = Instant.fromEpochMilliseconds(0)
    }
}
